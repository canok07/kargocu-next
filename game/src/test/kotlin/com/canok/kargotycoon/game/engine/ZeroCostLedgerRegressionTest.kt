package com.canok.kargotycoon.game.engine

import com.canok.kargotycoon.game.domain.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression for a real defect: the starter rental panelvan has zero repair and
 * maintenance cost in the shipped catalog. The engine still booked a zero-amount
 * ledger entry for a free operation, which `GameStateValidator` rejects, so
 * RepairVehicle/MaintainVehicle on a damaged or due rental failed with an
 * internal `StateInvariantViolation` even though the UI offers the action.
 */
class ZeroCostLedgerRegressionTest {
    private val catalog = DefaultCatalog.value
    private val engine = GameEngine(catalog)

    @Test
    fun damagedRentalCanBeRepairedForFreeWithoutABookedZeroLedgerEntry() {
        val damaged = newGame(catalog, seed = 1).copy(
            vehicles = listOf(VehicleState(VehicleId("vehicle-1"), catalog.starterVehicleSpecId, Ownership.RENTAL, conditionPercent = 99)),
        )
        val result = engine.reduce(damaged, GameCommand.RepairVehicle(CommandId("repair-rental"), VehicleId("vehicle-1")))
        assertTrue("free repair must be applied, got $result", result is GameResult.Applied)
        val repaired = result.state
        assertEquals(100, repaired.vehicles.single().conditionPercent)
        assertEquals(damaged.money, repaired.money)
        assertEquals(damaged.ledger, repaired.ledger)
    }

    @Test
    fun rentalMaintenanceAtZeroCostSucceedsAndResetsTheMileageAnchor() {
        val due = newGame(catalog, seed = 2).copy(
            vehicles = listOf(VehicleState(VehicleId("vehicle-1"), catalog.starterVehicleSpecId, Ownership.RENTAL, mileageMeters = 300_000, maintainedAtMeters = 0)),
        )
        val result = engine.reduce(due, GameCommand.MaintainVehicle(CommandId("maintain-rental"), VehicleId("vehicle-1")))
        assertTrue("free maintenance must be applied, got $result", result is GameResult.Applied)
        val maintained = result.state.vehicles.single()
        assertEquals(300_000, maintained.maintainedAtMeters)
        assertEquals(due.money, result.state.money)
        assertEquals(due.ledger, result.state.ledger)
    }

    @Test
    fun paidRepairStillBooksExactlyOneLedgerEntry() {
        val owned = VehicleState(VehicleId("vehicle-9"), VehicleSpecId("city-van"), Ownership.OWNED, conditionPercent = 70)
        val state = newGame(catalog, seed = 3).copy(
            money = Money(100_000),
            vehicles = listOf(newGame(catalog, seed = 3).vehicles.single(), owned),
            nextEntitySequence = 20,
        )
        val result = engine.reduce(state, GameCommand.RepairVehicle(CommandId("repair-owned"), owned.id))
        assertTrue(result is GameResult.Applied)
        assertEquals(1, result.state.ledger.size - state.ledger.size)
        assertEquals(LedgerType.REPAIR, result.state.ledger.last().type)
        assertEquals(Money(98_350), result.state.money)
    }
}
