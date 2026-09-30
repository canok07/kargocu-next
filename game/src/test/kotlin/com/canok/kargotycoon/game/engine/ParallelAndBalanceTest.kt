package com.canok.kargotycoon.game.engine

import com.canok.kargotycoon.game.domain.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ParallelAndBalanceTest {
    private val engine = GameEngine()

    @Test
    fun threeDueJobsSettleByDueTimeThenIdWithoutCrossTalk() {
        val state = threeActiveJobs()
        val result = applied(engine.reduce(state, GameCommand.AdvanceTime(CommandId("settle-all"), 20)))
        val completedOrder = result.events.filterIsInstance<GameEvent.JobCompleted>().map { it.jobId.value }
        assertEquals(listOf("job-a", "job-b", "job-c"), completedOrder)
        assertEquals(3, result.state.completedJobs.size)
        assertTrue(result.state.activeJobs.isEmpty())
        assertTrue(result.state.vehicles.all { it.status == VehicleStatus.AVAILABLE })
        assertTrue(result.state.drivers.all { it.status == DriverStatus.AVAILABLE })
        assertEquals(3, result.state.ledger.count { it.type == LedgerType.REVENUE })
    }

    @Test
    fun repeatedAndStalledAdvancesCannotDuplicateCompletionOrPay() {
        val state = threeActiveJobs()
        val stalled = applied(engine.reduce(state, GameCommand.AdvanceTime(CommandId("stall"), 4))).state
        assertEquals(3, stalled.activeJobs.size)
        assertTrue(stalled.completedJobs.isEmpty())
        val settled = applied(engine.reduce(stalled, GameCommand.AdvanceTime(CommandId("settle"), 20))).state
        val repeated = applied(engine.reduce(settled, GameCommand.AdvanceTime(CommandId("after"), 0))).state
        assertEquals(settled.completedJobs, repeated.completedJobs)
        assertEquals(settled.money, repeated.money)
    }

    @Test
    fun validatorFindsDuplicateAndDanglingParallelState() {
        val valid = threeActiveJobs()
        assertTrue(GameStateValidator.validate(valid, DefaultCatalog.value).isEmpty())
        val invalid = valid.copy(
            vehicles = valid.vehicles + valid.vehicles.first(),
            activeJobs = valid.activeJobs + valid.activeJobs.first().copy(id = JobId("job-z"), vehicleId = VehicleId("missing")),
        )
        val issues = GameStateValidator.validate(invalid, DefaultCatalog.value)
        assertTrue(issues.any { it is StateIssue.DuplicateId && it.kind == "vehicle" })
        assertTrue(issues.any { it is StateIssue.MissingReference && it.kind == "job.vehicle" })
    }

    @Test
    fun progressionRefreshAndTutorialEventsAreIdempotent() {
        val eligible = newGame(seed = 1).copy(gameDay = 4, progression = ProgressionState(completedJobs = 8), vehicles = newGame(seed = 1).vehicles + VehicleState(VehicleId("owned"), VehicleSpecId("city-van"), Ownership.OWNED))
        val first = applied(engine.reduce(eligible, GameCommand.RefreshProgression(CommandId("refresh-1")))).state
        val second = applied(engine.reduce(first, GameCommand.RefreshProgression(CommandId("refresh-2")))).state
        assertTrue(RegionId("rhein-main") in first.progression.unlockedRegionIds)
        assertEquals(first.progression, second.progression)

        val generated = applied(engine.reduce(newGame(seed = 9), GameCommand.GenerateDailyOffers(CommandId("offers")))).state
        val offer = feasible(generated.offers.first())
        val accepted = applied(engine.reduce(generated.copy(offers = listOf(offer)), GameCommand.AcceptJob(CommandId("accept"), offer.id, VehicleId("vehicle-1"), offer.routeOptions.first()))).state
        assertEquals(TutorialStep.COMPLETE_FIRST_JOB, accepted.tutorial.step)
        val completed = applied(engine.reduce(accepted, GameCommand.AdvanceTime(CommandId("finish"), accepted.activeJobs.single().completionAt.millis))).state
        assertEquals(TutorialStep.BUY_FIRST_VEHICLE, completed.tutorial.step)
    }

    @Test
    fun onlineAndCatchupProduceIdenticalParallelOutcome() {
        val base = threeActiveJobs().copy(lastRealtimeMillis = 1_000)
        val online = applied(engine.reduce(base, GameCommand.AdvanceTime(CommandId("online"), 60))).state
        val catchup = applied(engine.reduce(base, GameCommand.Resume(CommandId("catchup"), 1_001))).state
        assertEquals(online.copy(lastRealtimeMillis = catchup.lastRealtimeMillis, processedCommandIds = catchup.processedCommandIds), catchup)
    }

    @Test
    fun seededFourteenAndThirtyDayBalanceReachesPlayableLoop() {
        val day14 = simulate(days = 14, seed = 20260930)
        val day30 = simulate(days = 30, seed = 20260930)
        assertTrue("day14=$day14 day30=$day30", day14.firstOwnedAfterJobs in 8..15)
        assertTrue("day14=$day14", day14.ownedVehicles >= 1)
        assertTrue("day14=$day14", day14.drivers >= 1)
        assertTrue("day14=$day14", day14.parallelStarted)
        assertTrue(day30.completedJobs > day14.completedJobs)
        assertTrue(day30.money.cents >= 0)
        assertFalse(day30.softLocked)
    }

    private fun simulate(days: Int, seed: Long): BalanceFinding {
        var state = newGame(seed = seed)
        var command = 0
        var firstOwnedAfterJobs: Int? = null
        var parallelStarted = false
        repeat(days) { dayIndex ->
            repeat(2) {
                state = applied(engine.reduce(state, GameCommand.GenerateDailyOffers(CommandId("offers-${command++}")))).state
                val offer = state.offers.map(::feasible).first()
                val accepted = engine.reduce(state.copy(offers = listOf(offer)), GameCommand.AcceptJob(CommandId("accept-${command++}"), offer.id, VehicleId("vehicle-1"), offer.routeOptions.first(), true))
                if (accepted is GameResult.Applied) {
                    state = accepted.state
                    val job = state.activeJobs.first { it.manualDriving }
                    state = applied(engine.reduce(state, GameCommand.AdvanceTime(CommandId("finish-${command++}"), job.completionAt.millis - state.gameTime.millis))).state
                }
            }
            val firstVehicle = DefaultCatalog.value.vehicles.first { it.id == VehicleSpecId("city-van") }
            val purchaseBuffer = Money.euros(100)
            if (state.vehicles.none { it.ownership == Ownership.OWNED } && state.companyLevel(DefaultCatalog.value) >= 2 && state.money >= firstVehicle.purchasePrice + purchaseBuffer) {
                firstOwnedAfterJobs = state.progression.completedJobs
                state = applied(engine.reduce(state, GameCommand.PurchaseVehicle(CommandId("buy-${command++}"), firstVehicle.id))).state
            }
            if (state.vehicles.any { it.ownership == Ownership.OWNED } && state.drivers.isEmpty() && state.money >= Money(22_000)) {
                state = applied(engine.reduce(state, GameCommand.HireDriver(CommandId("hire-${command++}"), DriverTierId("junior"), "Mina"))).state
                val owned = state.vehicles.first { it.ownership == Ownership.OWNED }
                state = applied(engine.reduce(state, GameCommand.AssignDriver(CommandId("assign-${command++}"), state.drivers.single().id, owned.id))).state
            }
            if (!parallelStarted && state.drivers.isNotEmpty()) {
                state = applied(engine.reduce(state, GameCommand.GenerateDailyOffers(CommandId("parallel-offers-${command++}")))).state
                val offers = state.offers.take(2).map(::feasible).mapIndexed { index, offer -> offer.copy(id = OfferId("parallel-$dayIndex-$index")) }
                state = applied(engine.reduce(state.copy(offers = offers), GameCommand.AcceptJob(CommandId("manual-${command++}"), offers[0].id, VehicleId("vehicle-1"), offers[0].routeOptions.first(), true))).state
                val owned = state.vehicles.first { it.ownership == Ownership.OWNED }
                val second = engine.reduce(state, GameCommand.AcceptJob(CommandId("hired-${command++}"), offers[1].id, owned.id, offers[1].routeOptions.first(), false))
                if (second is GameResult.Applied) {
                    state = second.state
                    parallelStarted = true
                    val delta = state.activeJobs.maxOf { it.completionAt.millis } - state.gameTime.millis
                    state = applied(engine.reduce(state, GameCommand.AdvanceTime(CommandId("parallel-finish-${command++}"), delta))).state
                }
            }
            state = applied(engine.reduce(state, GameCommand.AdvanceDay(CommandId("day-${command++}")))).state
        }
        return BalanceFinding(state.progression.completedJobs, state.money, firstOwnedAfterJobs ?: Int.MAX_VALUE, state.vehicles.count { it.ownership == Ownership.OWNED }, state.drivers.size, parallelStarted, state.money.cents < 0 && state.activeJobs.isEmpty())
    }

    private fun threeActiveJobs(): GameState {
        val vehicles = listOf(
            VehicleState(VehicleId("v1"), VehicleSpecId("rental-panelvan"), Ownership.RENTAL, VehicleStatus.BUSY),
            VehicleState(VehicleId("v2"), VehicleSpecId("city-van"), Ownership.OWNED, VehicleStatus.BUSY, assignedDriverId = DriverId("d1")),
            VehicleState(VehicleId("v3"), VehicleSpecId("city-van"), Ownership.OWNED, VehicleStatus.BUSY, assignedDriverId = DriverId("d2")),
        )
        val drivers = listOf(
            DriverState(DriverId("d1"), DriverTierId("junior"), "A", status = DriverStatus.DRIVING, assignedVehicleId = VehicleId("v2")),
            DriverState(DriverId("d2"), DriverTierId("junior"), "B", status = DriverStatus.DRIVING, assignedVehicleId = VehicleId("v3")),
        )
        fun job(id: String, vehicle: VehicleId, driver: DriverId?, due: Long, manual: Boolean) = ActiveJob(JobId(id), OfferId("offer-$id"), vehicle, driver, manual, RouteId("ruhr-east"), RiskId("calm"), 0, GameInstant(0), GameInstant(due), GameInstant(due), JobInvoice(Money(1_000), Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO), Money.ZERO, distanceMeters = 1_000, riskRollPerMillion = 999_999)
        return newGame(seed = 4).copy(money = Money(50_000), nextEntitySequence = 100, vehicles = vehicles, drivers = drivers, activeJobs = listOf(job("job-c", VehicleId("v3"), DriverId("d2"), 10, false), job("job-b", VehicleId("v2"), DriverId("d1"), 5, false), job("job-a", VehicleId("v1"), null, 5, true)))
    }

    private fun feasible(offer: JobOffer): JobOffer = offer.copy(packageTypeId = PackageTypeId("parcel"), count = 1, totalWeightGrams = 2_000, requiredCapabilities = emptySet(), riskId = RiskId("calm"))
    private fun applied(result: GameResult): GameResult.Applied = result as GameResult.Applied
    private data class BalanceFinding(val completedJobs: Int, val money: Money, val firstOwnedAfterJobs: Int, val ownedVehicles: Int, val drivers: Int, val parallelStarted: Boolean, val softLocked: Boolean)
}
