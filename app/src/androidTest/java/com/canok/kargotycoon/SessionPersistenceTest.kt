package com.canok.kargotycoon

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.canok.kargotycoon.data.SessionController
import com.canok.kargotycoon.data.SessionMode
import com.canok.kargotycoon.game.domain.*
import com.canok.kargotycoon.game.engine.*
import com.canok.kargotycoon.game.persistence.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class SessionPersistenceTest {
    @Test fun inFlightDeliveryReopensAndCannotPayTwice() = runBlocking {
        val directory = directory()
        try {
            val first = SessionController(directory, clock = { 7_000 })
            assertEquals(SaveWriteResult.Written, first.startNewGame(seed = 9))
            val board = first.state.value.game!!
            val engine = GameEngine()
            val command = board.offers.firstNotNullOf { offer -> offer.routeOptions.firstNotNullOfOrNull { route ->
                GameCommand.AcceptJob(SessionController.id(), offer.id, board.vehicles.first().id, route).takeIf {
                    engine.reduce(board, it) is GameResult.Applied
                }
            } }
            assertTrue(first.dispatch(command) is StoreCommandResult.Applied)
            val accepted = first.state.value.game!!
            val reopened = SessionController(directory, clock = { 7_000 })
            assertTrue(reopened.reopen() is StoreOpenResult.Ready)
            val loaded = reopened.state.value.game!!
            assertEquals(accepted.activeJobs, loaded.activeJobs)
            assertEquals(accepted.money, loaded.money)
            assertEquals(accepted.randomCounter, loaded.randomCounter)
            val job = loaded.activeJobs.single()
            assertTrue(reopened.dispatch(GameCommand.AdvanceTime(SessionController.id(), job.completionAt.millis - loaded.gameTime.millis)) is StoreCommandResult.Applied)
            val settled = reopened.state.value.game!!
            assertEquals(1, settled.completedJobs.size)
            val again = SessionController(directory, clock = { 7_000 })
            again.reopen()
            assertTrue(again.dispatch(GameCommand.AdvanceTime(SessionController.id(), 0)) is StoreCommandResult.Applied)
            assertEquals(settled.money, again.state.value.game!!.money)
            assertEquals(settled.completedJobs, again.state.value.game!!.completedJobs)
        } finally { directory.deleteRecursively() }
    }

    @Test fun futureSaveRequiresAnExplicitPreservingNewGame() = runBlocking {
        val directory = directory()
        try {
            val codec = SaveCodec()
            val future = codec.encode(newGame(seed = 9)).decodeToString()
                .replace(Regex("\"saveVersion\":\\d+"), "\"saveVersion\":99").encodeToByteArray()
            File(directory, "game.json").writeBytes(future)
            val session = SessionController(directory, clock = { 7_000 })
            assertTrue(session.reopen() is StoreOpenResult.RecoveryRequired)
            assertEquals(SessionMode.RECOVERY, session.state.value.mode)
            assertTrue(session.state.value.futureSave)
            assertEquals(StoreCommandResult.RecoveryConfirmationRequired, session.dispatch(GameCommand.AdvanceDay(SessionController.id())))
            assertArrayEquals(future, File(directory, "game.json").readBytes())
            assertEquals(SaveWriteResult.Written, session.startNewGame(seed = 88))
            assertEquals(SessionMode.PLAYING, session.state.value.mode)
            assertEquals(88L, session.state.value.game!!.randomSeed)
            assertTrue(directory.listFiles()!!.any { it.name.startsWith("archive-") && it.readBytes().contentEquals(future) })
        } finally { directory.deleteRecursively() }
    }

    private fun directory() = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "session-test-${UUID.randomUUID()}").also { check(it.mkdirs()) }
}
