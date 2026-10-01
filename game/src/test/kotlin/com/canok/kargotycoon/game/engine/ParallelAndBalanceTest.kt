package com.canok.kargotycoon.game.engine

import com.canok.kargotycoon.game.domain.*
import com.canok.kargotycoon.game.persistence.SaveCodec
import com.canok.kargotycoon.game.persistence.SaveDecodeResult
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
    fun publicCommandFourteenAndThirtyDayLoopsReachFleetAndParallelPlay() {
        val seeds = listOf(7L, 99L, 20260930L)
        val day14 = seeds.map { simulate(days = 14, seed = it) }
        val day30 = seeds.map { simulate(days = 30, seed = it) }
        assertTrue("day14=$day14", day14.all { it.ownedVehicles >= 1 && it.drivers >= 1 && it.parallelStarted && it.saveLoaded && !it.softLocked })
        assertTrue("day30=$day30", day30.all { it.ownedVehicles >= 1 && it.drivers >= 1 && it.parallelStarted && it.saveLoaded && !it.softLocked })
        assertTrue("day14=$day14 day30=$day30", day30.zip(day14).all { (long, short) -> long.completedJobs > short.completedJobs })
    }

    private fun simulate(days: Int, seed: Long): BalanceFinding {
        var state = newGame(seed = seed)
        var command = 0
        var firstOwnedAfterJobs: Int? = null
        var parallelStarted = false
        var saveLoaded = false
        val cityVan = DefaultCatalog.value.vehicles.first { it.id == VehicleSpecId("city-van") }
        val junior = DefaultCatalog.value.driverTiers.first { it.id == DriverTierId("junior") }
        val operatingBuffer = Money.euros(100)
        repeat(days) { dayIndex ->
            state = applied(engine.reduce(state, GameCommand.GenerateDailyOffers(CommandId("offers-${command++}")))).state
            while (true) {
                if (state.vehicles.none { it.ownership == Ownership.OWNED } && state.companyLevel(DefaultCatalog.value) >= cityVan.minimumCompanyLevel && state.money >= cityVan.purchasePrice + operatingBuffer) {
                    firstOwnedAfterJobs = state.progression.completedJobs
                    state = applied(engine.reduce(state, GameCommand.PurchaseVehicle(CommandId("buy-${command++}"), cityVan.id))).state
                }
                if (state.vehicles.any { it.ownership == Ownership.OWNED } && state.drivers.isEmpty() && state.companyLevel(DefaultCatalog.value) >= junior.minimumCompanyLevel && state.money >= junior.hiringCost + operatingBuffer) {
                    state = applied(engine.reduce(state, GameCommand.HireDriver(CommandId("hire-${command++}"), junior.id, "Mina"))).state
                    val owned = state.vehicles.first { it.ownership == Ownership.OWNED }
                    state = applied(engine.reduce(state, GameCommand.AssignDriver(CommandId("assign-${command++}"), state.drivers.single().id, owned.id))).state
                }
                val manual = acceptFirstAvailable(state, VehicleId("vehicle-1"), manual = true, command = command++) ?: break
                state = manual
                if (!parallelStarted && state.drivers.isNotEmpty()) {
                    val owned = state.vehicles.first { it.ownership == Ownership.OWNED }
                    val hired = acceptFirstAvailable(state, owned.id, manual = false, command = command++)
                    if (hired != null) {
                        state = hired
                        parallelStarted = state.activeJobs.size == 2
                    }
                }
                val delta = state.activeJobs.maxOf { it.completionAt.millis } - state.gameTime.millis
                state = applied(engine.reduce(state, GameCommand.AdvanceTime(CommandId("finish-${command++}"), delta))).state
            }
            if (!saveLoaded && dayIndex >= 6) {
                state = (SaveCodec().decode(SaveCodec().encode(state)) as SaveDecodeResult.Success).state
                saveLoaded = true
            }
            state = applied(engine.reduce(state, GameCommand.AdvanceDay(CommandId("day-${command++}")))).state
        }
        return BalanceFinding(state.progression.completedJobs, state.money, firstOwnedAfterJobs ?: Int.MAX_VALUE, state.vehicles.count { it.ownership == Ownership.OWNED }, state.drivers.size, parallelStarted, saveLoaded, state.money.cents < 0 && state.activeJobs.isEmpty())
    }

    private fun acceptFirstAvailable(state: GameState, vehicleId: VehicleId, manual: Boolean, command: Int): GameState? {
        state.offers.forEachIndexed { index, offer ->
            offer.routeOptions.forEach { routeId ->
                val result = engine.reduce(state, GameCommand.AcceptJob(CommandId("accept-$command-$index-${routeId.value}"), offer.id, vehicleId, routeId, manual))
                if (result is GameResult.Applied) return result.state
            }
        }
        return null
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
    private fun applied(result: GameResult): GameResult.Applied = result as? GameResult.Applied ?: error("Unexpected rejection: $result")
    private data class BalanceFinding(val completedJobs: Int, val money: Money, val firstOwnedAfterJobs: Int, val ownedVehicles: Int, val drivers: Int, val parallelStarted: Boolean, val saveLoaded: Boolean, val softLocked: Boolean)
}
