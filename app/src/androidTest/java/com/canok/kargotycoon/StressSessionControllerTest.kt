package com.canok.kargotycoon

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.canok.kargotycoon.data.Notice
import com.canok.kargotycoon.data.SessionController
import com.canok.kargotycoon.data.SessionMode
import com.canok.kargotycoon.game.domain.GameState
import com.canok.kargotycoon.game.domain.LedgerType
import com.canok.kargotycoon.game.domain.VehicleId
import com.canok.kargotycoon.game.domain.VehicleStatus
import com.canok.kargotycoon.game.engine.GameCommand
import com.canok.kargotycoon.game.engine.GameResult
import com.canok.kargotycoon.game.engine.GameStateValidator
import com.canok.kargotycoon.game.persistence.SaveWriteResult
import com.canok.kargotycoon.game.persistence.StoreCommandResult
import com.canok.kargotycoon.game.persistence.StoreOpenResult
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/**
 * Instrumented stress for the session/store coordination layer over the real
 * AtomicFile repository. Each test owns a temporary directory and its own
 * CoroutineScope, which is cancelled in `finally`. The app's live save is never
 * touched and no real financial write is made to the player's game.
 */
@RunWith(AndroidJUnit4::class)
class StressSessionControllerTest {

    @Test
    fun commandStreamWithReopensKeepsRevisionMonotonicAndMoneyConserved() = runBlocking {
        val directory = directory()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            var session = SessionController(directory, clock = { 7_000 }, scope = scope)
            assertEquals(SaveWriteResult.Written, session.startNewGame(seed = 9))
            var maxRevision = session.state.value.game!!.revision
            repeat(200) { index ->
                val snapshot = session.state.value.game!!
                val result = session.dispatch(commandFor(snapshot, index))
                assertTrue(result is StoreCommandResult.Applied || result is StoreCommandResult.Rejected)
                val game = session.state.value.game!!
                assertTrue("revision must not decrease", game.revision >= maxRevision)
                assertTrue("cash must stay non-negative", game.money.cents >= 0)
                maxRevision = game.revision
                if (index % 40 == 39) {
                    val before = session.state.value.game!!
                    session = SessionController(directory, clock = { 7_000 }, scope = scope)
                    assertTrue(session.reopen() is StoreOpenResult.Ready)
                    assertEquals("reopen must preserve cash", before.money, session.state.value.game!!.money)
                    assertTrue(session.state.value.game!!.revision >= maxRevision)
                    maxRevision = session.state.value.game!!.revision
                }
            }
        } finally {
            scope.cancel()
            directory.deleteRecursively()
        }
    }

    @Test
    fun inFlightDeliverySurvivesReopensAndPaysExactlyOnce() = runBlocking {
        val directory = directory()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            var session = SessionController(directory, clock = { 7_000 }, scope = scope)
            assertEquals(SaveWriteResult.Written, session.startNewGame(seed = 9))
            assertTrue(acceptFirstFeasible(session, VehicleId("vehicle-1"), manual = true))

            repeat(15) {
                val before = session.state.value.game!!
                session = SessionController(directory, clock = { 7_000 }, scope = scope)
                assertTrue(session.reopen() is StoreOpenResult.Ready)
                val after = session.state.value.game!!
                assertEquals("active jobs must survive reopen", before.activeJobs, after.activeJobs)
                assertEquals("in-flight cash must survive reopen", before.money, after.money)
            }

            val loaded = session.state.value.game!!
            val job = loaded.activeJobs.single()
            val half = (job.completionAt.millis - loaded.gameTime.millis) / 2
            assertTrue(session.dispatch(GameCommand.AdvanceTime(SessionController.id(), half)) is StoreCommandResult.Applied)

            session = SessionController(directory, clock = { 7_000 }, scope = scope)
            assertTrue(session.reopen() is StoreOpenResult.Ready)
            val resumed = session.state.value.game!!
            val remaining = resumed.activeJobs.single().completionAt.millis - resumed.gameTime.millis
            assertTrue(session.dispatch(GameCommand.AdvanceTime(SessionController.id(), remaining + 1)) is StoreCommandResult.Applied)

            val settled = session.state.value.game!!
            assertEquals(1, settled.completedJobs.size)
            assertEquals(1, settled.ledger.count { it.type == LedgerType.REVENUE })

            session = SessionController(directory, clock = { 7_000 }, scope = scope)
            assertTrue(session.reopen() is StoreOpenResult.Ready)
            assertTrue(session.dispatch(GameCommand.AdvanceTime(SessionController.id(), 0)) is StoreCommandResult.Applied)
            assertEquals("no duplicate payout after reopen", settled.money, session.state.value.game!!.money)
            assertEquals(settled.completedJobs, session.state.value.game!!.completedJobs)
        } finally {
            scope.cancel()
            directory.deleteRecursively()
        }
    }

    @Test
    fun blockedWritesPausePersistenceAndRecoverOnReopen() = runBlocking {
        val directory = directory()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val session = SessionController(directory, clock = { 7_000 }, scope = scope)
            assertEquals(SaveWriteResult.Written, session.startNewGame(seed = 9))
            val before = session.state.value.game!!
            val obstruction = File(directory, "game.json.new").also { assertTrue(it.mkdir()) }
            val result = session.dispatch(GameCommand.AdvanceDay(SessionController.id()))
            assertTrue(result is StoreCommandResult.PersistenceFailed)
            assertEquals("failed save must not advance game state", before.gameDay, session.state.value.game!!.gameDay)
            assertEquals(Notice.SAVING_FAILED, session.state.value.notice)
            assertEquals(SessionMode.PLAYING, session.state.value.mode)

            assertTrue(obstruction.deleteRecursively())
            assertTrue(session.reopen() is StoreOpenResult.Ready)
            assertNull(session.state.value.notice)
            assertTrue(session.dispatch(GameCommand.AdvanceDay(SessionController.id())) is StoreCommandResult.Applied)
        } finally {
            scope.cancel()
            directory.deleteRecursively()
        }
    }

    @Test
    fun concurrentDispatchesSerializeWithoutCorruptingState() = runBlocking {
        val directory = directory()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val session = SessionController(directory, clock = { 7_000 }, scope = scope)
            assertEquals(SaveWriteResult.Written, session.startNewGame(seed = 9))
            val results = (0 until 64).map {
                async(Dispatchers.Default) { session.dispatch(GameCommand.AdvanceTime(SessionController.id(), 60_000L)) }
            }.awaitAll()
            assertTrue(results.all { it is StoreCommandResult.Applied })
            val game = session.state.value.game!!
            assertTrue("state must remain valid", GameStateValidator.validate(game, session.catalog).isEmpty())
            assertTrue(game.money.cents >= 0)
        } finally {
            scope.cancel()
            directory.deleteRecursively()
        }
    }

    private fun commandFor(state: GameState, index: Int): GameCommand = when (index % 4) {
        0 -> GameCommand.GenerateDailyOffers(SessionController.id())
        1 -> GameCommand.AdvanceTime(SessionController.id(), 60_000L)
        2 -> GameCommand.AdvanceDay(SessionController.id())
        else -> {
            val offer = state.offers.firstOrNull()
            val vehicle = state.vehicles.firstOrNull { it.status == VehicleStatus.AVAILABLE }
            if (offer != null && vehicle != null) {
                GameCommand.AcceptJob(SessionController.id(), offer.id, vehicle.id, offer.routeOptions.first(), manualDriving = vehicle.assignedDriverId == null)
            } else {
                GameCommand.AdvanceDay(SessionController.id())
            }
        }
    }

    private suspend fun acceptFirstFeasible(session: SessionController, vehicleId: VehicleId, manual: Boolean): Boolean {
        val snapshot = session.state.value.game!!
        for (offer in snapshot.offers) {
            for (route in offer.routeOptions) {
                val command = GameCommand.AcceptJob(SessionController.id(), offer.id, vehicleId, route, manualDriving = manual)
                if (session.previewCommand(command) is GameResult.Applied) {
                    return session.dispatch(command) is StoreCommandResult.Applied
                }
            }
        }
        return false
    }

    private fun directory(): File =
        File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "stress-session-${UUID.randomUUID()}").also { check(it.mkdirs()) }
}
