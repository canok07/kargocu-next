package com.canok.kargotycoon.game.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreDomainTest {
    @Test
    fun moneyUsesCheckedIntegerArithmetic() {
        assertEquals(Money(12_345), Money.euros(100) + Money(2_345))
        assertThrows(ArithmeticException::class.java) { Money(Long.MAX_VALUE) + Money(1) }
        assertThrows(ArithmeticException::class.java) { Money.euros(Long.MAX_VALUE) }
    }

    @Test
    fun bundledCatalogIsValidAndStarterJobIsFeasible() {
        val catalog = DefaultCatalog.value
        assertTrue(CatalogValidator.validate(catalog).isEmpty())
        assertEquals(7, catalog.regions.size)
        val starter = catalog.vehicles.single { it.id == catalog.starterVehicleSpecId }
        assertTrue(starter.rental)
        assertTrue(catalog.packageTypes.any { it.minWeightGrams <= starter.capacityGrams && it.capabilities.all(starter.capabilities::contains) })
    }

    @Test
    fun duplicateAndDanglingReferencesAreReported() {
        val valid = DefaultCatalog.value
        val invalid = valid.copy(
            vehicles = valid.vehicles + valid.vehicles.first(),
            locations = valid.locations + LocationSpec(LocationId("ghost"), RegionId("missing"), "Ghost"),
        )
        val issues = CatalogValidator.validate(invalid)
        assertTrue(issues.any { it is CatalogIssue.DuplicateId && it.category == "vehicle" })
        assertTrue(issues.any { it is CatalogIssue.MissingReference && it.id == "missing" })
    }

    @Test
    fun invalidRangesAndImpossibleStarterAreRejected() {
        val valid = DefaultCatalog.value
        val invalid = valid.copy(
            vehicles = valid.vehicles.map { if (it.id == valid.starterVehicleSpecId) it.copy(capacityCount = 0, capacityGrams = 1, capabilities = emptySet()) else it },
            packageTypes = valid.packageTypes.map { it.copy(capabilities = setOf(Capability.HAZARDOUS), minWeightGrams = 2) },
        )
        val issues = CatalogValidator.validate(invalid)
        assertTrue(issues.any { it is CatalogIssue.InvalidRange && it.field.startsWith("vehicle:") })
        assertTrue(issues.contains(CatalogIssue.NoFeasibleStarterJob))
    }

    @Test
    fun catalogAndCompleteInitialStateRoundTrip() {
        val catalogJson = DefaultCatalog.json.encodeToString(GameCatalog.serializer(), DefaultCatalog.value)
        assertEquals(DefaultCatalog.value, DefaultCatalog.json.decodeFromString(GameCatalog.serializer(), catalogJson))
        val state = newGame(seed = 42).copy(
            offers = listOf(JobOffer(OfferId("offer-1"), PackageTypeId("parcel"), 2, 4_000, LocationId("essen-hub"), LocationId("dortmund-market"), listOf(RouteId("ruhr-east")), emptySet(), RiskId("calm"), GameInstant(100))),
        )
        val stateJson = DefaultCatalog.json.encodeToString(GameState.serializer(), state)
        assertEquals(state, DefaultCatalog.json.decodeFromString(GameState.serializer(), stateJson))
        assertFalse(state.vehicles.single().ownership == Ownership.OWNED)
    }

    @Test
    fun companyLevelDerivesFromWorkAssetsDriversAndGameDays() {
        val base = newGame(seed = 1)
        assertEquals(1, base.companyLevel(DefaultCatalog.value))
        val progressed = base.copy(
            gameDay = 4,
            progression = ProgressionState(completedJobs = 8),
            vehicles = base.vehicles + VehicleState(VehicleId("vehicle-2"), VehicleSpecId("city-van"), Ownership.OWNED),
        )
        assertEquals(3, progressed.companyLevel(DefaultCatalog.value))
    }
}
