package com.canok.kargotycoon.game.stress

import com.canok.kargotycoon.game.domain.*
import com.canok.kargotycoon.game.engine.*
import com.canok.kargotycoon.game.persistence.SaveCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.experimental.categories.Category

/**
 * Multi-year careers driven only through public commands and the shipped catalog.
 * Verifies cash/reserve conservation, ledger reconciliation, one fee per worked
 * vehicle/driver calendar day, no duplicate payouts, deterministic replay,
 * 1:1 assignment, one active job per vehicle, bounded histories/save size, and
 * eventual fleet / parallel / region access.
 */
@Category(StressTest::class)
class LongCareerStressTest {
    private val catalog = DefaultCatalog.value
    private val engine = GameEngine(catalog)
    private val codec = SaveCodec(catalog)

    @Test
    fun multiYearCareersStayConservedAndReachFleetParallelAndRegions() {
        val days = intProp("stress.career.days", 365)
        val seedCount = intProp("stress.career.seeds", 8)
        val startedAt = System.nanoTime()
        val findings = (0 until seedCount).map { index -> runCareer(700_001L + index * 104_729L, days) }
        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000

        for (finding in findings) {
            assertTrue("seed ${finding.seed}: cash non-negative", finding.moneyCents >= 0)
            assertTrue("seed ${finding.seed}: no duplicate payouts (${finding.duplicatePayouts})", finding.duplicatePayouts == 0L)
            assertTrue("seed ${finding.seed}: >=2 owned vehicles (${finding.ownedVehicles})", finding.ownedVehicles >= 2)
            assertTrue("seed ${finding.seed}: driver hired (${finding.drivers})", finding.drivers >= 1)
            assertTrue("seed ${finding.seed}: parallel work reached (${finding.maxParallelJobs})", finding.maxParallelJobs >= 2)
            assertTrue("seed ${finding.seed}: fleet exercised (${finding.ownedSpecs})", finding.ownedSpecs.size >= 3)
            assertTrue("seed ${finding.seed}: elbe region unlocked (${finding.unlockedRegions})", "elbe" in finding.unlockedRegions)
            assertTrue("seed ${finding.seed}: ledger bounded (${finding.maxLedgerSize})", finding.maxLedgerSize <= catalog.economy.historyLimit)
            assertTrue("seed ${finding.seed}: completed jobs >= 18 (${finding.completedJobs})", finding.completedJobs >= 18)
            assertTrue("seed ${finding.seed}: save size bounded (${finding.maxSaveBytes} bytes)", finding.maxSaveBytes <= 1_000_000)
        }
        // Deterministic replay of one full multi-year career.
        assertEquals(findings.first(), runCareer(findings.first().seed, days))

        StressMetrics("long-career-stress").apply {
            number("seeds", seedCount)
            number("daysPerSeed", days)
            number("elapsedMs", elapsedMs)
            number("totalCompletedJobs", findings.sumOf { it.completedJobs.toLong() })
            number("maxParallelJobs", findings.maxOf { it.maxParallelJobs })
            number("maxLedgerSize", findings.maxOf { it.maxLedgerSize })
            number("maxSaveBytes", findings.maxOf { it.maxSaveBytes })
            number("maxOwnedVehicles", findings.maxOf { it.ownedVehicles })
            counters("completedJobsPerSeed", findings.associate { "seed${it.seed}" to it.completedJobs.toLong() })
            emit()
        }
    }

    private fun runCareer(seed: Long, days: Int): CareerFinding {
        var state = newGame(catalog, seed)
        val oracle = LedgerOracle(state.money.cents)
        val feeOracle = DayFeeOracle(catalog)
        var maxParallelJobs = 0
        var maxLedgerSize = 0
        var maxSaveBytes = 0
        var duplicatePayouts = 0L
        val seenCompletedIds = HashSet<String>()
        val ownedSpecs = sortedSetOf<String>()
        val purchasedSpecs = sortedSetOf<String>()
        var commandSequence = 0L

        fun commit(command: GameCommand): Boolean {
            val before = state
            return when (val result = engine.reduce(state, command)) {
                is GameResult.Applied -> {
                    val fresh = oracle.record(result.state)
                    feeOracle.check(before, result.state, fresh)
                    assertStructural(result.state, catalog)
                    oracle.assertConserved(result.state)
                    val beforeIds = before.completedJobs.map { it.jobId.value }.toSet()
                    for (job in result.state.completedJobs) {
                        if (job.jobId.value !in beforeIds && !seenCompletedIds.add(job.jobId.value)) duplicatePayouts++
                    }
                    state = result.state
                    maxParallelJobs = maxOf(maxParallelJobs, state.activeJobs.size)
                    maxLedgerSize = maxOf(maxLedgerSize, state.ledger.size)
                    true
                }
                is GameResult.Rejected -> {
                    assertEquals("rejected command must not mutate state", before, result.state)
                    false
                }
            }
        }

        fun manage(day: Int) {
            var guard = 0
            while (guard++ < 12) {
                val level = state.companyLevel(catalog)
                if (state.vehicles.count { it.ownership == Ownership.OWNED } < 4) {
                    val affordable = catalog.vehicles.filterNot { it.rental }
                        .filter { it.minimumCompanyLevel <= level && state.money >= it.purchasePrice + Money.euros(300) }
                    // Only add a catalog spec once, so every purchasable vehicle type is exercised
                    // instead of filling the fleet with the cheapest one before gates unlock.
                    val spec = affordable.firstOrNull { it.id.value !in purchasedSpecs }
                    if (spec != null && commit(GameCommand.PurchaseVehicle(CommandId("career-$seed-buy-$day-$guard"), spec.id))) {
                        purchasedSpecs += spec.id.value
                        continue
                    }
                }
                val idleVehicle = state.vehicles.firstOrNull { it.ownership == Ownership.OWNED && it.assignedDriverId == null && it.status == VehicleStatus.AVAILABLE }
                val idleDriver = state.drivers.firstOrNull { it.assignedVehicleId == null && it.status == DriverStatus.AVAILABLE }
                if (idleVehicle != null && idleDriver == null && state.drivers.size < state.vehicles.size) {
                    val tier = catalog.driverTiers
                        .filter { it.minimumCompanyLevel <= level && state.money >= it.hiringCost + Money.euros(300) }
                        .minByOrNull { it.hiringCost.cents }
                    if (tier != null && commit(GameCommand.HireDriver(CommandId("career-$seed-hire-$day-$guard"), tier.id, "S$guard"))) continue
                }
                if (idleVehicle != null && idleDriver != null) {
                    commit(GameCommand.AssignDriver(CommandId("career-$seed-assign-$day-$guard"), idleDriver.id, idleVehicle.id))
                    continue
                }
                break
            }
            state.vehicles
                .filter { it.conditionPercent < 100 && it.status == VehicleStatus.AVAILABLE && state.activeJobs.none { job -> job.vehicleId == it.id } }
                .forEachIndexed { index, vehicle -> commit(GameCommand.RepairVehicle(CommandId("career-$seed-repair-$day-$index"), vehicle.id)) }
            state.vehicles
                .filter { it.status == VehicleStatus.AVAILABLE && it.mileageMeters - it.maintainedAtMeters >= catalog.economy.maintenanceIntervalMeters }
                .forEachIndexed { index, vehicle -> commit(GameCommand.MaintainVehicle(CommandId("career-$seed-maint-$day-$index"), vehicle.id)) }
        }

        fun tryAccept(vehicleId: VehicleId, manual: Boolean, tag: String): Boolean {
            val snapshot = state
            for ((index, offer) in snapshot.offers.withIndex()) {
                for (route in offer.routeOptions) {
                    val command = GameCommand.AcceptJob(CommandId("career-$seed-accept-$tag-$index-${route.value}"), offer.id, vehicleId, route, manualDriving = manual)
                    if (engine.reduce(snapshot, command) is GameResult.Applied) return commit(command)
                }
            }
            return false
        }

        repeat(days) { day ->
            commit(GameCommand.GenerateDailyOffers(CommandId("career-$seed-offers-$day")))
            manage(day)
            var progressed = true
            var round = 0
            while (progressed && round++ < 40) {
                progressed = false
                for (vehicle in state.vehicles.filter { it.status == VehicleStatus.AVAILABLE && state.activeJobs.none { job -> job.vehicleId == it.id } }) {
                    val manual = vehicle.assignedDriverId == null
                    if (!manual || state.activeJobs.none { it.manualDriving }) {
                        if (tryAccept(vehicle.id, manual, "d$day-r$round-$commandSequence")) {
                            progressed = true
                            commandSequence++
                        }
                    }
                }
                maxParallelJobs = maxOf(maxParallelJobs, state.activeJobs.size)
                if (state.activeJobs.isNotEmpty()) {
                    val delta = state.activeJobs.minOf { it.completionAt.millis } - state.gameTime.millis
                    if (delta > 0) commit(GameCommand.AdvanceTime(CommandId("career-$seed-finish-$day-$round"), delta))
                }
            }
            state.vehicles.filter { it.ownership == Ownership.OWNED }.forEach { ownedSpecs += it.specId.value }
            maxSaveBytes = maxOf(maxSaveBytes, codec.encode(state).size)
            commit(GameCommand.AdvanceDay(CommandId("career-$seed-day-$day")))
        }

        return CareerFinding(
            seed = seed,
            completedJobs = state.progression.completedJobs,
            moneyCents = state.money.cents,
            ownedVehicles = state.vehicles.count { it.ownership == Ownership.OWNED },
            drivers = state.drivers.size,
            maxParallelJobs = maxParallelJobs,
            ownedSpecs = ownedSpecs.toSortedSet(),
            unlockedRegions = state.progression.unlockedRegionIds.map { it.value }.toSortedSet(),
            maxLedgerSize = maxLedgerSize,
            maxSaveBytes = maxSaveBytes,
            duplicatePayouts = duplicatePayouts,
        )
    }

    private data class CareerFinding(
        val seed: Long,
        val completedJobs: Int,
        val moneyCents: Long,
        val ownedVehicles: Int,
        val drivers: Int,
        val maxParallelJobs: Int,
        val ownedSpecs: Set<String>,
        val unlockedRegions: Set<String>,
        val maxLedgerSize: Int,
        val maxSaveBytes: Int,
        val duplicatePayouts: Long,
    )
}
