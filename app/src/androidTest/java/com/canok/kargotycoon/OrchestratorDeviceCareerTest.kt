package com.canok.kargotycoon

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.canok.kargotycoon.data.SessionController
import com.canok.kargotycoon.game.domain.*
import com.canok.kargotycoon.game.engine.*
import com.canok.kargotycoon.game.persistence.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Real Android storage seam: progression is earned only through public commands. */
@RunWith(AndroidJUnit4::class)
class OrchestratorDeviceCareerTest {
    @Test fun careerAndParallelContractsSurviveDeviceSessionRecreation() = runBlocking {
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "career-test-${UUID.randomUUID()}")
        check(directory.mkdirs())
        try {
            var session = SessionController(directory, clock = { 7_000 })
            assertEquals(SaveWriteResult.Written, session.startNewGame(seed = 99))
            val catalog = session.catalog
            val van = catalog.vehicles.first { it.id == VehicleSpecId("city-van") }
            val junior = catalog.driverTiers.first { it.id == DriverTierId("junior") }
            val operatingBuffer = Money.euros(100)
            var parallelRestored = false

            suspend fun apply(command: GameCommand) {
                val result = session.dispatch(command)
                assertTrue("Command rejected: $command -> $result", result is StoreCommandResult.Applied)
            }
            suspend fun acceptFirst(vehicleId: VehicleId, manual: Boolean): Boolean {
                val snapshot = session.state.value.game!!
                for (offer in snapshot.offers) for (route in offer.routeOptions) {
                    val command = GameCommand.AcceptJob(SessionController.id(), offer.id, vehicleId, route, manual)
                    if (session.previewCommand(command) is GameResult.Applied) {
                        apply(command)
                        return true
                    }
                }
                return false
            }

            repeat(14) {
                while (true) {
                    var state = session.state.value.game!!
                    if (state.vehicles.none { it.ownership == Ownership.OWNED } && state.companyLevel(catalog) >= van.minimumCompanyLevel && state.money >= van.purchasePrice + operatingBuffer) {
                        apply(GameCommand.PurchaseVehicle(SessionController.id(), van.id))
                    }
                    state = session.state.value.game!!
                    if (state.vehicles.any { it.ownership == Ownership.OWNED } && state.drivers.isEmpty() && state.companyLevel(catalog) >= junior.minimumCompanyLevel && state.money >= junior.hiringCost + operatingBuffer) {
                        apply(GameCommand.HireDriver(SessionController.id(), junior.id, "Mina"))
                        val hired = session.state.value.game!!
                        apply(GameCommand.AssignDriver(SessionController.id(), hired.drivers.single().id, hired.vehicles.first { it.ownership == Ownership.OWNED }.id))
                    }
                    if (!acceptFirst(VehicleId("vehicle-1"), manual = true)) break
                    state = session.state.value.game!!
                    if (state.drivers.isNotEmpty()) acceptFirst(state.vehicles.first { it.ownership == Ownership.OWNED }.id, manual = false)
                    state = session.state.value.game!!
                    if (state.activeJobs.size == 2 && !parallelRestored) {
                        val original = state
                        session = SessionController(directory, clock = { 7_000 })
                        assertTrue(session.reopen() is StoreOpenResult.Ready)
                        state = session.state.value.game!!
                        assertEquals(original.activeJobs, state.activeJobs)
                        assertEquals(original.money, state.money)
                        assertEquals(original.offers, state.offers)
                        assertEquals(original.randomCounter, state.randomCounter)
                        parallelRestored = true
                    }
                    apply(GameCommand.AdvanceTime(SessionController.id(), state.activeJobs.maxOf { it.completionAt.millis } - state.gameTime.millis))
                }
                apply(GameCommand.AdvanceDay(SessionController.id()))
            }

            val earned = session.state.value.game!!
            assertTrue(earned.vehicles.any { it.ownership == Ownership.OWNED })
            assertTrue(earned.drivers.isNotEmpty())
            assertTrue(parallelRestored)
            assertTrue(earned.progression.completedJobs >= 14)
            assertTrue(earned.money.cents >= 0)
            assertEquals(TutorialStep.COMPLETE, earned.tutorial.step)
            apply(GameCommand.ChangeSettings(SessionController.id(), earned.settings.copy(languageTag = "en")))
            val settled = session.state.value.game!!
            session = SessionController(directory, clock = { 7_000 })
            session.reopen()
            apply(GameCommand.AdvanceTime(SessionController.id(), 0))
            assertEquals(settled.money, session.state.value.game!!.money)
            assertEquals(settled.completedJobs, session.state.value.game!!.completedJobs)
            assertEquals("en", session.state.value.game!!.settings.languageTag)
        } finally { directory.deleteRecursively() }
    }
}
