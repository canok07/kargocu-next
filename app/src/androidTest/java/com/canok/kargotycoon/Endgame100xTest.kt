package com.canok.kargotycoon

import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.canok.kargotycoon.data.SessionController
import com.canok.kargotycoon.game.domain.*
import com.canok.kargotycoon.game.engine.*
import com.canok.kargotycoon.game.persistence.SaveCodec
import com.canok.kargotycoon.game.persistence.StoreCommandResult
import com.canok.kargotycoon.ui.TestTags
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Opt-in, visible, wall-clock-paced measurement; never changes production speed. */
@RunWith(AndroidJUnit4::class)
class Endgame100xTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun measureLegalCareerAt100xWithoutSkippingDays() = runBlocking {
        assumeTrue("Opt-in endgame measurement", InstrumentationRegistry.getArguments().getString("endgame100x") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val session = (context.applicationContext as KargoApplication).session
        val catalog = session.catalog
        val engine = GameEngine(catalog)
        val seed = 20261003L
        session.startNewGame(seed = seed, languageTag = "tr")
        session.setForeground(false)
        // Drain any lifecycle resume before starting the measured clock.
        delay(300)
        session.dispatch(GameCommand.AdvanceTime(SessionController.id(), 0))
        fun game() = requireNotNull(session.state.value.game)
        val paidJobs = mutableSetOf<String>()
        var commandCount = 0
        var maxParallel = 0
        suspend fun apply(command: GameCommand): Boolean {
            val before = game()
            if (engine.reduce(before, command) !is GameResult.Applied) return false
            val result = session.dispatch(command)
            assertTrue("Validated command did not persist: $result", result is StoreCommandResult.Applied)
            result as StoreCommandResult.Applied
            commandCount++
            result.events.filterIsInstance<GameEvent.JobCompleted>().forEach {
                assertTrue("Duplicate payout: ${it.jobId}", paidJobs.add(it.jobId.value))
            }
            maxParallel = maxOf(maxParallel, result.state.activeJobs.size)
            return true
        }
        apply(GameCommand.SetTutorialDismissed(SessionController.id(), true))
        compose.onNodeWithTag(TestTags.NAV_MORE).performClick()
        compose.onNodeWithTag(TestTags.MORE_COMPANY).performClick()
        val reportDir = File(context.filesDir, "benchmarks/endgame100x").apply { mkdirs() }
        val progress = File(reportDir, "progress.csv")
        progress.writeText("real_ms,game_day,game_ms,stage,completed_jobs,owned_vehicles,drivers,regions,balance_cents\n")
        val baseGameMillis = game().gameTime.millis
        val startedAt = SystemClock.elapsedRealtime()
        var lastDay = 0
        val milestones = mutableListOf<JSONObject>()
        val maxStage = catalog.progression.levels.maxOf { it.level }
        val requiredRegions = catalog.regions.map { it.id }.toSet()
        fun goalReached() = game().companyLevel(catalog) == maxStage && game().progression.unlockedRegionIds.containsAll(requiredRegions)
        try {
            while (!goalReached()) {
                val elapsed = SystemClock.elapsedRealtime() - startedAt
                assertTrue("Endgame not reached within 12 real minutes; day=${game().gameDay}, jobs=${game().progression.completedJobs}", elapsed < 720_000L)
                // Normal rate is 60 game ms per real ms; benchmark is exactly 100x.
                val target = baseGameMillis + elapsed * 60L * 100L
                val delta = target - game().gameTime.millis
                if (delta > 0) apply(GameCommand.AdvanceTime(SessionController.id(), delta))
                var snapshot = game()
                val owned = snapshot.vehicles.count { it.ownership == Ownership.OWNED }
                if (owned < 2) {
                    val affordable = catalog.vehicles.filter { !it.rental && it.minimumCompanyLevel <= snapshot.companyLevel(catalog) && snapshot.money >= it.purchasePrice + Money.euros(200) }
                    val choice = affordable.firstOrNull { spec -> snapshot.vehicles.none { it.specId == spec.id } }
                        ?: affordable.minByOrNull { it.purchasePrice.cents }
                    if (choice != null) apply(GameCommand.PurchaseVehicle(SessionController.id(), choice.id))
                }
                snapshot = game()
                val idleOwned = snapshot.vehicles.firstOrNull { it.ownership == Ownership.OWNED && it.assignedDriverId == null && it.status == VehicleStatus.AVAILABLE }
                var idleDriver = snapshot.drivers.firstOrNull { it.assignedVehicleId == null && it.status == DriverStatus.AVAILABLE }
                if (idleOwned != null && idleDriver == null) {
                    val tier = catalog.driverTiers.filter { it.minimumCompanyLevel <= snapshot.companyLevel(catalog) && snapshot.money >= it.hiringCost + Money.euros(200) }.minByOrNull { it.hiringCost.cents }
                    if (tier != null) {
                        apply(GameCommand.HireDriver(SessionController.id(), tier.id, "Driver ${snapshot.drivers.size + 1}"))
                        idleDriver = game().drivers.firstOrNull { it.assignedVehicleId == null && it.status == DriverStatus.AVAILABLE }
                    }
                }
                if (idleOwned != null && idleDriver != null) apply(GameCommand.AssignDriver(SessionController.id(), idleDriver.id, idleOwned.id))
                game().vehicles.filter { it.status == VehicleStatus.AVAILABLE }.forEach { vehicle ->
                    if (vehicle.conditionPercent < 95) apply(GameCommand.RepairVehicle(SessionController.id(), vehicle.id))
                    if (vehicle.mileageMeters - vehicle.maintainedAtMeters >= catalog.economy.maintenanceIntervalMeters) apply(GameCommand.MaintainVehicle(SessionController.id(), vehicle.id))
                }
                for (vehicle in game().vehicles.filter { it.status == VehicleStatus.AVAILABLE }) {
                    snapshot = game()
                    val manual = vehicle.assignedDriverId == null
                    if (manual && snapshot.activeJobs.any { it.manualDriving }) continue
                    val candidates = snapshot.offers.flatMap { offer -> offer.routeOptions.map { route ->
                        GameCommand.AcceptJob(SessionController.id(), offer.id, vehicle.id, route, manualDriving = manual)
                    } }.mapNotNull { command ->
                        val result = engine.reduce(snapshot, command)
                        if (result !is GameResult.Applied) null else command to result.state.activeJobs.last().invoice.maximumRevenue.cents
                    }.sortedByDescending { it.second }
                    candidates.firstOrNull()?.let { apply(it.first) }
                }
                snapshot = game()
                assertTrue("Invalid persisted state", GameStateValidator.validate(snapshot, catalog).isEmpty())
                assertTrue("Negative balance", snapshot.money.cents >= 0)
                if (snapshot.gameDay != lastDay) {
                    lastDay = snapshot.gameDay
                    val realMillis = SystemClock.elapsedRealtime() - startedAt
                    progress.appendText("$realMillis,${snapshot.gameDay},${snapshot.gameTime.millis},${snapshot.companyLevel(catalog)},${snapshot.progression.completedJobs},${snapshot.vehicles.count { it.ownership == Ownership.OWNED }},${snapshot.drivers.size},${snapshot.progression.unlockedRegionIds.size},${snapshot.money.cents}\n")
                    milestones += JSONObject().put("realMillis", realMillis).put("gameDay", snapshot.gameDay).put("stage", snapshot.companyLevel(catalog)).put("completedJobs", snapshot.progression.completedJobs)
                    Log.i("Endgame100x", "day=${snapshot.gameDay} stage=${snapshot.companyLevel(catalog)} jobs=${snapshot.progression.completedJobs} balance=${snapshot.money.cents} regions=${snapshot.progression.unlockedRegionIds.size} realMillis=$realMillis")
                }
                session.clearNotice()
                delay(100)
            }
            val finalState = game()
            val measuredMillis = SystemClock.elapsedRealtime() - startedAt
            val gameElapsedMillis = finalState.gameTime.millis - baseGameMillis
            val codec = SaveCodec(catalog)
            File(reportDir, "final-save.json").writeBytes(codec.encode(finalState))
            val report = JSONObject()
                .put("seed", seed).put("speedMultiplier", 100)
                .put("criterion", "highest company stage and every catalog region unlocked")
                .put("strategy", "automatic legal job selection by payout; buy up to two owned vehicles with 200 EUR buffer, hire and assign drivers, repair and maintain idle fleet; no injected funds, catalog changes or AdvanceDay")
                .put("realElapsedMillis", measuredMillis).put("gameElapsedMillis", gameElapsedMillis)
                .put("normalClockEquivalentMillis", gameElapsedMillis / 60L)
                .put("gameDay", finalState.gameDay).put("stage", finalState.companyLevel(catalog))
                .put("completedJobs", finalState.progression.completedJobs)
                .put("ownedVehicles", finalState.vehicles.count { it.ownership == Ownership.OWNED })
                .put("drivers", finalState.drivers.size).put("regions", finalState.progression.unlockedRegionIds.size)
                .put("balanceCents", finalState.money.cents).put("maxParallelJobs", maxParallel)
                .put("appliedCommands", commandCount).put("uniquePaidJobs", paidJobs.size)
                .put("milestones", org.json.JSONArray(milestones))
            File(reportDir, "summary.json").writeText(report.toString(2) + "\n")
            Log.i("Endgame100x", "FINISHED $report")
            assertEquals(finalState.progression.completedJobs, paidJobs.size)
            assertTrue(goalReached())
            assertTrue("Clock did not run at 100x", kotlin.math.abs(gameElapsedMillis / 6000L - measuredMillis) < 1500L)
        } finally {
            session.setForeground(true)
        }
    }
}
