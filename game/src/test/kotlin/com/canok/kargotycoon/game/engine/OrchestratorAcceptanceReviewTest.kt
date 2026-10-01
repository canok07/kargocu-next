package com.canok.kargotycoon.game.engine

import com.canok.kargotycoon.game.domain.*
import org.junit.Assert.*
import org.junit.Test

/** Independent acceptance checks. Production code is intentionally unchanged. */
class OrchestratorAcceptanceReviewTest {
    private val catalog = DefaultCatalog.value
    private val engine = GameEngine(catalog)

    @Test fun invalidPackageValuesRejectWithoutMutation() {
        val ready = ready()
        val invalid = ready.copy(offers = listOf(ready.offers.first().copy(count = 0, totalWeightGrams = -1)))
        val result = engine.reduce(invalid, accept(invalid.offers.first(), "invalid"))
        assertTrue("Invalid count/weight accepted: $result", result is GameResult.Rejected)
        assertEquals(invalid, result.state)
    }

    @Test fun unknownRiskReturnsTypedRejection() {
        val ready = ready()
        val invalid = ready.copy(offers = listOf(ready.offers.first().copy(riskId = RiskId("missing-risk"))))
        val result = engine.reduce(invalid, accept(invalid.offers.first(), "risk"))
        assertTrue(result is GameResult.Rejected)
        assertEquals(invalid, result.state)
    }

    @Test fun validatorRejectsUnreciprocatedDriverAssignment() {
        val invalid = newGame(seed = 3).copy(drivers = listOf(
            DriverState(DriverId("driver-10"), DriverTierId("junior"), "Mina", assignedVehicleId = VehicleId("vehicle-1"))
        ))
        assertTrue("One-sided assignment is not a valid save", GameStateValidator.validate(invalid, catalog).isNotEmpty())
    }

    @Test fun validatorRejectsNegativeCash() {
        assertTrue(GameStateValidator.validate(newGame(seed = 3).copy(money = Money(-1)), catalog).isNotEmpty())
    }

    @Test fun vehicleUpkeepIsChargedOnceForTwoJobsOnSameWorkedDay() {
        var state = ready()
        repeat(2) { index ->
            state = applied(engine.reduce(state, accept(state.offers.first(), "accept-$index")))
            state = finish(state, "finish-$index")
        }
        val charged = state.ledger.filter { it.type == LedgerType.VEHICLE_UPKEEP }.sumOf { -it.amount.cents }
        assertEquals("Daily upkeep must not scale with jobs on the same day", 180L, charged)
    }

    @Test fun overnightJobChargesBothWorkedCalendarDays() {
        val beforeMidnight = ready(hired = true).copy(gameTime = GameInstant(GameEngine.DAY_MILLIS - 5 * GameEngine.MINUTE_MILLIS))
        val accepted = applied(engine.reduce(beforeMidnight, accept(beforeMidnight.offers.first(), "overnight", manual = false)))
        val finished = finish(accepted, "finish-overnight")
        assertEquals(setOf(1, 2), finished.drivers.single().wageChargedGameDays)
        assertEquals(1_900L, finished.ledger.filter { it.type == LedgerType.DRIVER_WAGE }.sumOf { -it.amount.cents })
    }

    @Test fun dailyRevenueExcludesReturnedReservations() {
        val ready = ready()
        val accepted = applied(engine.reduce(ready, accept(ready.offers.first(), "summary")))
        val finished = finish(accepted, "summary-finish")
        val closed = applied(engine.reduce(finished, GameCommand.AdvanceDay(CommandId("close-day"))))
        assertEquals("Refunded reservations are not business revenue", finished.completedJobs.single().revenue, closed.dailySummaries.single().revenue)
    }

    @Test fun acceptedJobCostsRemainStableWhenInjectedPricesChange() {
        val ready = ready()
        val accepted = applied(engine.reduce(ready, accept(ready.offers.first(), "snapshot")))
        val command = GameCommand.AdvanceTime(CommandId("settle-snapshot"), accepted.activeJobs.single().completionAt.millis)
        val expected = applied(engine.reduce(accepted, command))
        val repriced = catalog.copy(vehicles = catalog.vehicles.map { it.copy(fuelCentsPerKilometer = it.fuelCentsPerKilometer + 5) })
        val actual = applied(GameEngine(repriced).reduce(accepted, command))
        assertEquals("In-flight invoice costs must be snapshotted", expected.completedJobs.single().costs, actual.completedJobs.single().costs)
        assertEquals(expected.money, actual.money)
    }

    @Test fun repeatedDailyRefreshCannotReplaceRemainingOffers() {
        val initial = applied(engine.reduce(newGame(seed = 99), GameCommand.GenerateDailyOffers(CommandId("first-refresh"))))
        val repeated = engine.reduce(initial, GameCommand.GenerateDailyOffers(CommandId("second-refresh")))
        assertEquals("Daily board must not be freely rerolled with a new command id", initial.offers, repeated.state.offers)
        assertEquals(initial.randomCounter, repeated.state.randomCounter)
    }

    @Test fun initialBoardAlwaysIncludesAffordableCompatibleWork() {
        val impossible = (0L until 1_000L).filter { seed ->
            val board = applied(engine.reduce(newGame(seed = seed), GameCommand.GenerateDailyOffers(CommandId("initial-$seed"))))
            board.offers.none { offer -> offer.routeOptions.any { route ->
                engine.reduce(board, GameCommand.AcceptJob(CommandId("probe-${offer.id.value}"), offer.id, VehicleId("vehicle-1"), route)) is GameResult.Applied
            } }
        }
        assertTrue("No starter job for ${impossible.size}/1000 seeds; examples=${impossible.take(12)}", impossible.isEmpty())
    }

    private fun ready(hired: Boolean = false): GameState {
        val driverId = DriverId("driver-10")
        val vehicle = VehicleState(VehicleId("vehicle-1"), VehicleSpecId("city-van"), Ownership.OWNED,
            assignedDriverId = if (hired) driverId else null)
        // Fixed boundary fixtures, not used as evidence of generated-offer balance.
        fun offer(index: Int) = JobOffer(OfferId("fixture-$index"), PackageTypeId("parcel"), 1, 2_000,
            LocationId("essen-hub"), LocationId("dortmund-market"), listOf(RouteId("ruhr-east")),
            emptySet(), RiskId("calm"), GameInstant(2 * GameEngine.DAY_MILLIS))
        return newGame(seed = 99).copy(money = Money.euros(1_000), nextEntitySequence = 20,
            vehicles = listOf(vehicle), drivers = if (hired) listOf(DriverState(driverId, DriverTierId("junior"), "Mina", assignedVehicleId = vehicle.id)) else emptyList(),
            offers = listOf(offer(1), offer(2)))
    }

    private fun accept(offer: JobOffer, id: String, manual: Boolean = true) =
        GameCommand.AcceptJob(CommandId(id), offer.id, VehicleId("vehicle-1"), offer.routeOptions.first(), manual)

    private fun finish(state: GameState, id: String) = applied(engine.reduce(state,
        GameCommand.AdvanceTime(CommandId(id), state.activeJobs.single().completionAt.millis - state.gameTime.millis)))

    private fun applied(result: GameResult): GameState {
        assertTrue("Unexpected rejection: $result", result is GameResult.Applied)
        return result.state
    }
}
