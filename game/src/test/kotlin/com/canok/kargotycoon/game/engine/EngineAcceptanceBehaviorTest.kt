package com.canok.kargotycoon.game.engine

import com.canok.kargotycoon.game.domain.*
import com.canok.kargotycoon.game.persistence.SaveCodec
import com.canok.kargotycoon.game.persistence.SaveDecodeResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineAcceptanceBehaviorTest {
    private val catalog = DefaultCatalog.value
    private val engine = GameEngine(catalog)

    @Test
    fun exhaustedDailyBoardDoesNotRerollOrConsumeRandomness() {
        val generated = applied(engine.reduce(newGame(seed = 151), GameCommand.GenerateDailyOffers(CommandId("generate"))))
        val exhausted = generated.copy(offers = emptyList())
        val repeated = applied(engine.reduce(exhausted, GameCommand.GenerateDailyOffers(CommandId("repeat"))))
        assertTrue(repeated.offers.isEmpty())
        assertEquals(exhausted.randomCounter, repeated.randomCounter)
        assertEquals(exhausted.offersGeneratedGameDay, repeated.offersGeneratedGameDay)
    }

    @Test
    fun advanceDayCreatesNextBoardExactlyOnce() {
        val first = applied(engine.reduce(newGame(seed = 300), GameCommand.GenerateDailyOffers(CommandId("first"))))
        val nextDay = applied(engine.reduce(first, GameCommand.AdvanceDay(CommandId("next-day"))))
        assertEquals(2, nextDay.gameDay)
        assertEquals(2, nextDay.offersGeneratedGameDay)
        assertTrue(nextDay.offers.isNotEmpty())
        val repeated = applied(engine.reduce(nextDay, GameCommand.GenerateDailyOffers(CommandId("same-day"))))
        assertEquals(nextDay.offers, repeated.offers)
        assertEquals(nextDay.randomCounter, repeated.randomCounter)
    }

    @Test
    fun failedReservationDoesNotConsumeOfferOrRandomness() {
        val board = applied(engine.reduce(newGame(seed = 99), GameCommand.GenerateDailyOffers(CommandId("generate"))))
        val poor = board.copy(money = Money.ZERO)
        val result = poor.offers.asSequence().flatMap { offer -> offer.routeOptions.asSequence().map { route ->
            engine.reduce(poor, GameCommand.AcceptJob(CommandId("try-${offer.id.value}-${route.value}"), offer.id, VehicleId("vehicle-1"), route))
        } }.first { it is GameResult.Rejected && it.reason is Rejection.InsufficientFunds }
        assertEquals(poor, result.state)
        assertEquals(board.randomCounter, result.state.randomCounter)
        assertEquals(board.offers, result.state.offers)
    }

    @Test
    fun zeroCashCanAdvanceAndSettleReservedJob() {
        val board = applied(engine.reduce(newGame(seed = 99), GameCommand.GenerateDailyOffers(CommandId("generate"))))
        val accepted = firstAccepted(board, VehicleId("vehicle-1"), manual = true)
        val zeroCash = accepted.copy(money = Money.ZERO)
        val job = zeroCash.activeJobs.single()
        val finished = applied(engine.reduce(zeroCash, GameCommand.AdvanceTime(CommandId("finish"), job.completionAt.millis - zeroCash.gameTime.millis)))
        val result = finished.completedJobs.single()
        assertEquals(job.reserved + result.revenue - result.costs, finished.money)
        assertTrue(finished.activeJobs.isEmpty())
    }

    @Test
    fun idleDayDoesNotChargeAssignedVehicleOrDriver() {
        val driverId = DriverId("driver-2")
        val vehicle = VehicleState(VehicleId("vehicle-2"), VehicleSpecId("city-van"), Ownership.OWNED, assignedDriverId = driverId)
        val driver = DriverState(driverId, DriverTierId("junior"), "Mina", assignedVehicleId = vehicle.id)
        val state = newGame(seed = 1).copy(vehicles = newGame(seed = 1).vehicles + vehicle, drivers = listOf(driver), nextEntitySequence = 3)
        val advanced = applied(engine.reduce(state, GameCommand.AdvanceDay(CommandId("idle"))))
        assertTrue(advanced.ledger.none { it.type == LedgerType.VEHICLE_UPKEEP || it.type == LedgerType.DRIVER_WAGE })
        assertTrue(advanced.vehicles.first { it.id == vehicle.id }.upkeepChargedGameDays.isEmpty())
        assertTrue(advanced.drivers.single().wageChargedGameDays.isEmpty())
    }

    @Test
    fun unknownPersistedReferencesAreTypedRejections() {
        val offer = JobOffer(OfferId("offer"), PackageTypeId("parcel"), 1, 2_000, LocationId("essen-hub"), LocationId("dortmund-market"), listOf(RouteId("ruhr-east")), emptySet(), RiskId("calm"), GameInstant(GameEngine.DAY_MILLIS))
        val base = newGame(seed = 1)
        val invalidStates = listOf(
            base.copy(offers = listOf(offer.copy(packageTypeId = PackageTypeId("missing")))),
            base.copy(offers = listOf(offer.copy(originId = LocationId("missing")))),
            base.copy(offers = listOf(offer.copy(routeOptions = listOf(RouteId("missing"))))),
            base.copy(offers = listOf(offer.copy(riskId = RiskId("missing")))),
            base.copy(vehicles = listOf(base.vehicles.single().copy(specId = VehicleSpecId("missing")))),
            base.copy(drivers = listOf(DriverState(DriverId("driver"), DriverTierId("missing"), "Mina"))),
        )
        invalidStates.forEachIndexed { index, invalid ->
            val result = engine.reduce(invalid, GameCommand.GenerateDailyOffers(CommandId("invalid-$index")))
            assertTrue("Expected typed rejection for $invalid", result is GameResult.Rejected)
            assertEquals(invalid, result.state)
        }
    }

    @Test
    fun inFlightSnapshotsSurviveSaveLoadAndCatalogRepricing() {
        val accepted = firstAccepted(applied(engine.reduce(newGame(seed = 99), GameCommand.GenerateDailyOffers(CommandId("generate")))), VehicleId("vehicle-1"), manual = true)
        val loaded = (SaveCodec(catalog).decode(SaveCodec(catalog).encode(accepted)) as SaveDecodeResult.Success).state
        assertEquals(accepted, loaded)
        val job = loaded.activeJobs.single()
        val command = GameCommand.AdvanceTime(CommandId("finish"), job.completionAt.millis - loaded.gameTime.millis)
        val expected = applied(engine.reduce(loaded, command))
        val repriced = catalog.copy(
            vehicles = catalog.vehicles.map { it.copy(fuelCentsPerKilometer = it.fuelCentsPerKilometer + 100, dailyUpkeep = it.dailyUpkeep + Money(10_000)) },
            driverTiers = catalog.driverTiers.map { it.copy(dailyWage = it.dailyWage + Money(10_000)) },
            risks = catalog.risks.map { it.copy(penaltyBasisPoints = 5_000) },
            economy = catalog.economy.copy(rentalCentsPerJob = 20_000),
        )
        val actual = applied(GameEngine(repriced).reduce(loaded, command))
        assertEquals(expected.completedJobs.single(), actual.completedJobs.single())
        assertEquals(expected.money, actual.money)
    }

    private fun firstAccepted(state: GameState, vehicleId: VehicleId, manual: Boolean): GameState {
        state.offers.forEachIndexed { index, offer ->
            offer.routeOptions.forEach { route ->
                val result = engine.reduce(state, GameCommand.AcceptJob(CommandId("accept-$index-${route.value}"), offer.id, vehicleId, route, manual))
                if (result is GameResult.Applied) return result.state
            }
        }
        error("No acceptable offer for $state")
    }

    private fun applied(result: GameResult): GameState = (result as? GameResult.Applied)?.state ?: error("Unexpected rejection: $result")
}
