package com.canok.kargotycoon.game.stress

import com.canok.kargotycoon.game.domain.*
import com.canok.kargotycoon.game.engine.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.experimental.categories.Category

/**
 * Stateful, deterministic fuzz campaign. Every committed `engine.reduce` is a
 * counted transition; rejected commands must leave the exact input state intact.
 * Independent oracles (ledger conservation, per-day fee accounting, structural
 * invariants) run after every applied transition.
 */
@Category(StressTest::class)
class EngineStressCampaignTest {
    private val catalog = DefaultCatalog.value
    private val engine = GameEngine(catalog)

    @Test
    fun campaignConservesMoneyAndCoversAppliedAndRejectedTransitions() {
        val seeds = intProp("stress.seeds", 120)
        val iterations = intProp("stress.iterations", 2000)
        val chaosRate = doubleProp("stress.chaos", 0.22)
        val startedAt = System.nanoTime()

        val coverage = Coverage()
        val requiredApplied = setOf(
            "GenerateDailyOffers", "AcceptJob", "AdvanceTime", "AdvanceDay", "PurchaseVehicle",
            "HireDriver", "AssignDriver", "RepairVehicle", "MaintainVehicle", "SellVehicle", "UnassignDriver",
        )
        val requiredRejections = setOf(
            "NotFound", "InsufficientFunds", "InvalidAdvance", "DuplicateCommand", "CompanyLevelRequired",
            "CapacityExceeded", "MissingCapability", "ManualDriverBusy", "InvalidSettings",
        )
        var replayChecks = 0
        var completedJobsTotal = 0L

        for (index in 0 until seeds) {
            val seed = 20_261_002L + index * 7_919L
            var state = newGame(catalog, seed)
            val oracle = LedgerOracle(state.money.cents)
            val feeOracle = DayFeeOracle(catalog)
            val director = CareerDirector(catalog, StressRandom(seed), seed, chaosRate)
            val commands = ArrayList<GameCommand>(iterations + 1)
            var lastAppliedId: CommandId? = null

            fun commit(command: GameCommand) {
                when (val result = engine.reduce(state, command)) {
                    is GameResult.Applied -> {
                        val fresh = oracle.record(result.state)
                        feeOracle.check(state, result.state, fresh)
                        assertStructural(result.state, catalog)
                        oracle.assertConserved(result.state)
                        state = result.state
                        coverage.onApplied(command.kind())
                        lastAppliedId = command.commandId
                    }
                    is GameResult.Rejected -> {
                        assertEquals("rejected command must not mutate state", state, result.state)
                        coverage.onRejected(command.kind(), result.reason)
                    }
                }
                commands += command
            }

            commit(GameCommand.GenerateDailyOffers(CommandId("seed-$seed-gen")))
            repeat(iterations) { commit(director.next(state, lastAppliedId)) }
            completedJobsTotal += state.progression.completedJobs

            // Deterministic replay: the exact command list from a fresh seed must reproduce the state.
            var replayState = newGame(catalog, seed)
            for (command in commands) {
                when (val result = engine.reduce(replayState, command)) {
                    is GameResult.Applied -> replayState = result.state
                    is GameResult.Rejected -> Unit
                }
            }
            replayChecks++
            assertEquals("deterministic replay diverged for seed $seed", state, replayState)
        }

        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
        assertTrue("campaign produced >= 100k transitions, got ${coverage.transitions}", coverage.transitions >= 100_000)
        assertTrue("campaign exercised both accepted and rejected transitions", coverage.applied > 0 && coverage.rejected > 0)
        assertTrue("campaign actually progressed jobs, got $completedJobsTotal", completedJobsTotal >= seeds.toLong())

        val missingApplied = requiredApplied - coverage.appliedByCommand.keys
        assertTrue("campaign never applied: $missingApplied", missingApplied.isEmpty())
        val missingRejections = requiredRejections - coverage.rejectedByReason.keys
        assertTrue("campaign never hit rejection reasons: $missingRejections", missingRejections.isEmpty())

        StressMetrics("engine-stress").apply {
            number("seeds", seeds)
            number("iterationsPerSeed", iterations)
            number("aggregateCommittedTransitions", coverage.transitions)
            number("appliedTransitions", coverage.applied)
            number("rejectedTransitions", coverage.rejected)
            number("replayChecks", replayChecks)
            number("completedJobsTotal", completedJobsTotal)
            number("elapsedMs", elapsedMs)
            counters("appliedByCommand", coverage.appliedByCommand)
            counters("rejectedByCommand", coverage.rejectedByCommand)
            counters("rejectedByReason", coverage.rejectedByReason)
            emit()
        }
    }

    /** Deterministic command director: weighted valid play plus deliberate invalid commands. */
    private class CareerDirector(
        private val catalog: GameCatalog,
        private val rng: StressRandom,
        private val seed: Long,
        private val chaosRate: Double,
    ) {
        private var sequence = 0L

        private fun id(): CommandId = CommandId("c-$seed-${sequence++}")

        fun next(state: GameState, lastAppliedId: CommandId?): GameCommand =
            if (rng.chance(chaosRate)) chaos(state, lastAppliedId) else planned(state)

        private fun planned(state: GameState): GameCommand {
            val options = ArrayList<Pair<Int, () -> GameCommand>>()
            if (state.offers.isEmpty() && state.offersGeneratedGameDay != state.gameDay) {
                options += 40 to { GameCommand.GenerateDailyOffers(id()) }
            }
            val available = state.vehicles.filter { it.status == VehicleStatus.AVAILABLE }
            if (state.offers.isNotEmpty() && available.isNotEmpty()) {
                options += 70 to { smartAccept(state, available) }
            }
            affordablePurchase(state)?.let { spec -> options += 30 to { GameCommand.PurchaseVehicle(id(), spec.id) } }
            affordableTier(state)?.let { tier ->
                if (state.drivers.size < 6) options += 20 to { GameCommand.HireDriver(id(), tier.id, "D${state.nextEntitySequence}") }
            }
            idleAssignment(state)?.let { (driverId, vehicleId) -> options += 25 to { GameCommand.AssignDriver(id(), driverId, vehicleId) } }
            state.vehicles.firstOrNull { it.conditionPercent < 100 && it.status == VehicleStatus.AVAILABLE }?.let { damaged ->
                options += 25 to { GameCommand.RepairVehicle(id(), damaged.id) }
            }
            state.vehicles.firstOrNull { it.status == VehicleStatus.AVAILABLE && it.mileageMeters - it.maintainedAtMeters >= catalog.economy.maintenanceIntervalMeters }?.let { due ->
                options += 15 to { GameCommand.MaintainVehicle(id(), due.id) }
            }
            state.vehicles.firstOrNull { it.ownership == Ownership.OWNED && it.status == VehicleStatus.AVAILABLE && it.assignedDriverId == null }?.let { sellable ->
                if (state.vehicles.count { it.ownership == Ownership.OWNED } > 1) options += 8 to { GameCommand.SellVehicle(id(), sellable.id) }
            }
            state.drivers.firstOrNull { it.assignedVehicleId != null && it.status == DriverStatus.AVAILABLE }?.let { assignedIdle ->
                options += 8 to { GameCommand.UnassignDriver(id(), assignedIdle.id) }
            }
            if (state.activeJobs.isNotEmpty()) {
                val delta = (state.activeJobs.minOf { it.completionAt.millis } - state.gameTime.millis).coerceAtLeast(0L)
                options += 60 to { GameCommand.AdvanceTime(id(), delta) }
            }
            options += 50 to { GameCommand.AdvanceDay(id()) }

            val total = options.sumOf { it.first }
            var pick = rng.nextInt(total)
            for ((weight, factory) in options) {
                pick -= weight
                if (pick < 0) return factory()
            }
            return GameCommand.AdvanceDay(id())
        }

        private fun smartAccept(state: GameState, available: List<VehicleState>): GameCommand {
            val offer = state.offers.minByOrNull { it.totalWeightGrams }!!
            val vehicle = available.maxByOrNull { catalog.vehicles.first { spec -> spec.id == it.specId }.capacityGrams }!!
            val manual = vehicle.assignedDriverId == null || rng.chance(0.4)
            return GameCommand.AcceptJob(id(), offer.id, vehicle.id, offer.routeOptions.first(), manualDriving = manual)
        }

        private fun affordablePurchase(state: GameState): VehicleSpec? {
            if (state.vehicles.size >= 10) return null
            val level = state.companyLevel(catalog)
            return catalog.vehicles
                .filterNot { it.rental }
                .filter { it.minimumCompanyLevel <= level && state.money >= it.purchasePrice + Money.euros(100) }
                .minByOrNull { it.purchasePrice.cents }
        }

        private fun affordableTier(state: GameState): DriverTier? {
            val level = state.companyLevel(catalog)
            return catalog.driverTiers
                .filter { it.minimumCompanyLevel <= level && state.money >= it.hiringCost + Money.euros(100) }
                .minByOrNull { it.hiringCost.cents }
        }

        private fun idleAssignment(state: GameState): Pair<DriverId, VehicleId>? {
            val vehicle = state.vehicles.firstOrNull {
                it.ownership == Ownership.OWNED && it.assignedDriverId == null && it.status == VehicleStatus.AVAILABLE &&
                    state.activeJobs.none { job -> job.vehicleId == it.id }
            } ?: return null
            val driver = state.drivers.firstOrNull { it.assignedVehicleId == null && it.status == DriverStatus.AVAILABLE } ?: return null
            return driver.id to vehicle.id
        }

        private fun chaos(state: GameState, lastAppliedId: CommandId?): GameCommand {
            val offer = state.offers.firstOrNull()
            val vehicle = state.vehicles.firstOrNull()
            val vehicleId = vehicle?.id ?: VehicleId("vehicle-1")
            return when (rng.nextInt(16)) {
                0 -> GameCommand.AcceptJob(id(), OfferId("missing-offer"), vehicleId, RouteId("ruhr-east"))
                1 -> GameCommand.AcceptJob(id(), offer?.id ?: OfferId("missing-offer"), VehicleId("missing-vehicle"), RouteId("ruhr-east"))
                2 -> GameCommand.AcceptJob(id(), offer?.id ?: OfferId("missing-offer"), vehicleId, RouteId("missing-route"))
                3 -> GameCommand.AcceptJob(id(), offer?.id ?: OfferId("missing-offer"), vehicleId, offer?.routeOptions?.firstOrNull() ?: RouteId("ruhr-east"), manualDriving = true)
                4 -> GameCommand.PurchaseVehicle(id(), VehicleSpecId("missing-spec"))
                5 -> GameCommand.PurchaseVehicle(id(), VehicleSpecId("city-van"))
                6 -> GameCommand.SellVehicle(id(), vehicleId)
                7 -> GameCommand.RepairVehicle(id(), vehicleId)
                8 -> GameCommand.AdvanceTime(id(), -1L)
                9 -> GameCommand.AdvanceTime(id(), 3_000_000_000L)
                10 -> GameCommand.FireDriver(id(), DriverId("missing-driver"))
                11 -> GameCommand.AssignDriver(id(), DriverId("missing-driver"), VehicleId("vehicle-1"))
                12 -> GameCommand.ChangeSettings(id(), state.settings.copy(languageTag = "xx"))
                13 -> GameCommand.PurchaseVehicle(id(), VehicleSpecId("cool-box"))
                14 -> GameCommand.HireDriver(id(), DriverTierId("junior"), "")
                else -> GameCommand.GenerateDailyOffers(lastAppliedId ?: id())
            }
        }
    }
}
