package com.canok.kargotycoon.game.engine

import com.canok.kargotycoon.game.domain.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FleetSystemTest {
    private val engine = GameEngine()

    @Test
    fun purchaseRequiresLevelAndMoneyThenCreatesUniqueOwnedVehicle() {
        val base = newGame(seed = 1)
        val gated = engine.reduce(base, GameCommand.PurchaseVehicle(CommandId("gated"), VehicleSpecId("city-van"))) as GameResult.Rejected
        assertTrue(gated.reason is Rejection.CompanyLevelRequired)
        assertEquals(base, gated.state)

        val eligible = eligibleState().copy(money = Money(89_999))
        val poor = engine.reduce(eligible, GameCommand.PurchaseVehicle(CommandId("poor"), VehicleSpecId("city-van"))) as GameResult.Rejected
        assertTrue(poor.reason is Rejection.InsufficientFunds)

        val funded = eligible.copy(money = Money(200_000))
        val first = applied(engine.reduce(funded, GameCommand.PurchaseVehicle(CommandId("buy-1"), VehicleSpecId("city-van")))).state
        val second = applied(engine.reduce(first, GameCommand.PurchaseVehicle(CommandId("buy-2"), VehicleSpecId("city-van")))).state
        val owned = second.vehicles.filter { it.ownership == Ownership.OWNED }
        assertEquals(2, owned.size)
        assertEquals(2, owned.map { it.id }.toSet().size)
        assertEquals(Money(20_000), second.money)
    }

    @Test
    fun rentalAndBusyVehiclesCannotBeSoldOrStranded() {
        val base = eligibleState().copy(money = Money(200_000))
        val rental = engine.reduce(base, GameCommand.SellVehicle(CommandId("sell-rental"), VehicleId("vehicle-1"))) as GameResult.Rejected
        assertTrue(rental.reason is Rejection.RentalVehicleProtected)

        val bought = applied(engine.reduce(base, GameCommand.PurchaseVehicle(CommandId("buy"), VehicleSpecId("city-van")))).state
        val owned = bought.vehicles.last()
        val active = syntheticJob(owned.id)
        val busy = bought.copy(vehicles = bought.vehicles.map { if (it.id == owned.id) it.copy(status = VehicleStatus.BUSY) else it }, activeJobs = listOf(active))
        val rejected = engine.reduce(busy, GameCommand.SellVehicle(CommandId("sell-busy"), owned.id)) as GameResult.Rejected
        assertTrue(rejected.reason is Rejection.VehicleBusy)
        assertEquals(busy, rejected.state)
        assertTrue(rejected.state.activeJobs.any { it.vehicleId == owned.id })
    }

    @Test
    fun saleUsesConditionAdjustedCatalogResaleAndDoesNotReuseId() {
        val bought = applied(engine.reduce(eligibleState().copy(money = Money(200_000)), GameCommand.PurchaseVehicle(CommandId("buy"), VehicleSpecId("city-van")))).state
        val vehicle = bought.vehicles.last()
        val worn = bought.copy(vehicles = bought.vehicles.map { if (it.id == vehicle.id) it.copy(conditionPercent = 50) else it })
        val sold = applied(engine.reduce(worn, GameCommand.SellVehicle(CommandId("sell"), vehicle.id))).state
        assertFalse(sold.vehicles.any { it.id == vehicle.id })
        assertEquals(Money(139_250), sold.money)
        val replacement = applied(engine.reduce(sold.copy(money = Money(200_000)), GameCommand.PurchaseVehicle(CommandId("replace"), VehicleSpecId("city-van")))).state.vehicles.last()
        assertFalse(replacement.id == vehicle.id)
    }

    @Test
    fun repairAndMaintenanceAreExplicitAffordableRoutes() {
        val owned = VehicleState(VehicleId("vehicle-9"), VehicleSpecId("city-van"), Ownership.OWNED, conditionPercent = 70, mileageMeters = 300_000)
        val state = eligibleState().copy(money = Money(100_000), vehicles = listOf(newGame(seed = 1).vehicles.single(), owned), nextEntitySequence = 20)
        val repaired = applied(engine.reduce(state, GameCommand.RepairVehicle(CommandId("repair"), owned.id))).state
        assertEquals(100, repaired.vehicles.first { it.id == owned.id }.conditionPercent)
        assertEquals(Money(98_350), repaired.money)
        assertEquals(LedgerType.REPAIR, repaired.ledger.last().type)

        val wornAgain = repaired.copy(vehicles = repaired.vehicles.map { if (it.id == owned.id) it.copy(conditionPercent = 80) else it })
        val maintained = applied(engine.reduce(wornAgain, GameCommand.MaintainVehicle(CommandId("maintain"), owned.id))).state
        val result = maintained.vehicles.first { it.id == owned.id }
        assertEquals(95, result.conditionPercent)
        assertEquals(result.mileageMeters, result.maintainedAtMeters)
        assertEquals(LedgerType.MAINTENANCE, maintained.ledger.last().type)
    }

    @Test
    fun maintenanceNotDueAndHealthyRepairLeaveStateUnchanged() {
        val state = eligibleState().copy(money = Money(100_000))
        val healthy = engine.reduce(state, GameCommand.RepairVehicle(CommandId("healthy"), VehicleId("vehicle-1"))) as GameResult.Rejected
        assertTrue(healthy.reason is Rejection.VehicleAlreadyHealthy)
        val early = engine.reduce(state, GameCommand.MaintainVehicle(CommandId("early"), VehicleId("vehicle-1"))) as GameResult.Rejected
        assertTrue(early.reason is Rejection.MaintenanceNotDue)
        assertEquals(state, early.state)
    }

    private fun eligibleState(): GameState = newGame(seed = 2).copy(gameDay = 2, progression = ProgressionState(completedJobs = 4))

    private fun syntheticJob(vehicleId: VehicleId) = ActiveJob(
        JobId("active"), OfferId("accepted"), vehicleId, null, true, RouteId("ruhr-east"), RiskId("calm"), 0,
        GameInstant(0), GameInstant(1), GameInstant(1), JobInvoice(Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO), Money.ZERO, distanceMeters = 1, riskRollPerMillion = 1,
    )

    private fun applied(result: GameResult): GameResult.Applied = result as GameResult.Applied
}
