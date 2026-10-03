package com.canok.kargotycoon.game.engine

import com.canok.kargotycoon.game.domain.*
import org.junit.Assert.*
import org.junit.Test

class ExpandedCareerTest {
    private val catalog = DefaultCatalog.value
    private val engine = GameEngine(catalog)
    private fun rich() = newGame(seed = 20261003).copy(money = Money.euros(100_000), gameDay = 32,
        progression = ProgressionState(completedJobs = 300), nextEntitySequence = 100,
        vehicles = listOf(VehicleState(VehicleId("vehicle-1"), VehicleSpecId("rental-panelvan"), Ownership.RENTAL)) +
            catalog.vehicles.filterNot { it.rental }.take(6).mapIndexed { index,spec -> VehicleState(VehicleId("owned-$index"), spec.id, Ownership.OWNED) },
        drivers = (0 until 5).map { DriverState(DriverId("driver-$it"), DriverTierId("junior"), "Driver $it") })

    @Test
    fun repeatingOneCheapVehicleCannotOpenTheAdvancedCareer() {
        val base = rich()
        val repeated = base.copy(vehicles = base.vehicles.map { if (it.ownership == Ownership.OWNED) it.copy(specId = VehicleSpecId("city-van")) else it })
        assertEquals(4, repeated.companyLevel(catalog))
        assertEquals(8, base.companyLevel(catalog))
        val refreshed = (engine.reduce(repeated, GameCommand.RefreshProgression(CommandId("refresh"))) as GameResult.Applied).state
        assertEquals(setOf(RegionId("ruhr"),RegionId("rhein-main"),RegionId("elbe")), refreshed.progression.unlockedRegionIds)
    }

    @Test
    fun starterMarketsDoNotAdvertiseUnavailableSpecialistCargo() {
        repeat(30) { seed ->
            val board = (engine.reduce(newGame(seed = seed.toLong()), GameCommand.GenerateDailyOffers(CommandId("offers"))) as GameResult.Applied).state
            assertTrue(board.offers.all { catalog.packageTypes.first { type -> type.id == it.packageTypeId }.minimumCompanyLevel == 1 })
            assertTrue(board.offers.any { offer -> offer.routeOptions.any { route -> engine.reduce(board, GameCommand.AcceptJob(CommandId("preview"),offer.id,VehicleId("vehicle-1"),route)) is GameResult.Applied } })
        }
    }

    @Test
    fun matureMarketsScaleWithinTheBoundAndCannotBeRegeneratedForFree() {
        val eligible = (engine.reduce(rich(), GameCommand.RefreshProgression(CommandId("regions"))) as GameResult.Applied).state
        assertEquals(7, eligible.progression.unlockedRegionIds.size)
        val board = (engine.reduce(eligible, GameCommand.GenerateDailyOffers(CommandId("board"))) as GameResult.Applied).state
        assertEquals(24, board.offers.size)
        val repeated = (engine.reduce(board, GameCommand.GenerateDailyOffers(CommandId("repeat-board"))) as GameResult.Applied).state
        assertEquals(board.offers, repeated.offers)
        assertEquals(board.randomCounter, repeated.randomCounter)
    }

    @Test
    fun alternativeRoutesKeepEndpointsAndActuallyChangeCostAndDuration() {
        val board = (engine.reduce(newGame(seed = 1), GameCommand.GenerateDailyOffers(CommandId("board"))) as GameResult.Applied).state
        val offer = board.offers.first { it.routeOptions.size > 1 }.copy(packageTypeId = PackageTypeId("parcel"),requiredCapabilities = emptySet(),count = 1,totalWeightGrams = 2_000,riskId = RiskId("calm"))
        val jobs = offer.routeOptions.map { route ->
            val result = engine.reduce(board.copy(offers=listOf(offer)),GameCommand.AcceptJob(CommandId("route-${route.value}"),offer.id,VehicleId("vehicle-1"),route)) as GameResult.Applied
            result.state.activeJobs.single()
        }
        assertTrue(jobs.map { it.invoice.reservedFuel }.toSet().size > 1)
        assertTrue(jobs.map { it.completionAt }.toSet().size > 1)
        assertTrue(offer.routeOptions.all { id -> catalog.routes.first { it.id == id }.let { it.originId == offer.originId && it.destinationId == offer.destinationId } })
    }

    @Test
    fun impossibleVehicleDiversityGatesAreRejectedAsCatalogErrors() {
        val impossible = catalog.copy(progression = ProgressionConfig(catalog.progression.levels.map { if(it.level==2) it.copy(minimumOwnedVehicles=6,minimumDistinctVehicleSpecs=6) else it }))
        assertTrue(CatalogValidator.validate(impossible).any { it is CatalogIssue.InvalidRange && it.field.startsWith("unreachableVehicleTypes") })
    }
}
