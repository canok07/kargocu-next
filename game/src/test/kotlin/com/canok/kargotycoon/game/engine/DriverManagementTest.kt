package com.canok.kargotycoon.game.engine

import com.canok.kargotycoon.game.domain.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DriverManagementTest {
    private val engine = GameEngine()

    @Test
    fun hiringUsesTierGateCostAndUniqueIds() {
        val base = eligible().copy(money = Money(100_000))
        val first = applied(engine.reduce(base, GameCommand.HireDriver(CommandId("hire-1"), DriverTierId("junior"), "  Mina  "))).state
        val second = applied(engine.reduce(first, GameCommand.HireDriver(CommandId("hire-2"), DriverTierId("junior"), "Leon"))).state
        assertEquals(listOf("Mina", "Leon"), second.drivers.map { it.name })
        assertEquals(2, second.drivers.map { it.id }.toSet().size)
        assertEquals(Money(56_000), second.money)
        assertEquals(2, second.ledger.count { it.type == LedgerType.DRIVER_HIRE })
    }

    @Test
    fun hiringAndSeveranceRequireFundsAndReconcileLedger() {
        val poor = eligible().copy(money = Money(21_999))
        assertTrue((engine.reduce(poor, GameCommand.HireDriver(CommandId("hire"), DriverTierId("junior"), "Mina")) as GameResult.Rejected).reason is Rejection.InsufficientFunds)
        val hired = hiredAssigned().copy(money = Money(950))
        val driver = hired.drivers.single()
        val fired = applied(engine.reduce(hired, GameCommand.FireDriver(CommandId("fire"), driver.id))).state
        assertTrue(fired.drivers.isEmpty())
        assertNull(fired.vehicles.first { it.id == VehicleId("vehicle-2") }.assignedDriverId)
        assertEquals(Money.ZERO, fired.money)
        assertEquals(LedgerType.DRIVER_SEVERANCE, fired.ledger.last().type)
    }

    @Test
    fun assignmentIsOneToOneAndBusyVehiclesRejectChanges() {
        val state = eligible().copy(
            drivers = listOf(DriverState(DriverId("d1"), DriverTierId("junior"), "A"), DriverState(DriverId("d2"), DriverTierId("junior"), "B")),
            vehicles = eligible().vehicles + VehicleState(VehicleId("vehicle-2"), VehicleSpecId("city-van"), Ownership.OWNED),
        )
        val assigned = applied(engine.reduce(state, GameCommand.AssignDriver(CommandId("assign"), DriverId("d1"), VehicleId("vehicle-2")))).state
        assertEquals(VehicleId("vehicle-2"), assigned.drivers.first().assignedVehicleId)
        assertEquals(DriverId("d1"), assigned.vehicles.last().assignedDriverId)
        assertTrue((engine.reduce(assigned, GameCommand.AssignDriver(CommandId("same-driver"), DriverId("d1"), VehicleId("vehicle-1"))) as GameResult.Rejected).reason is Rejection.DriverAlreadyAssigned)
        assertTrue((engine.reduce(assigned, GameCommand.AssignDriver(CommandId("same-vehicle"), DriverId("d2"), VehicleId("vehicle-2"))) as GameResult.Rejected).reason is Rejection.VehicleAlreadyAssigned)
    }

    @Test
    fun hiredDriverJobBlocksFireUnassignAndReassignment() {
        val ready = hiredAssigned()
        val offer = feasibleOffer(ready)
        val accepted = applied(engine.reduce(ready.copy(offers = listOf(offer)), GameCommand.AcceptJob(CommandId("accept"), offer.id, VehicleId("vehicle-2"), offer.routeOptions.first(), manualDriving = false))).state
        val driver = accepted.drivers.single()
        assertEquals(DriverStatus.DRIVING, driver.status)
        assertTrue((engine.reduce(accepted, GameCommand.FireDriver(CommandId("fire"), driver.id)) as GameResult.Rejected).reason is Rejection.DriverBusy)
        assertTrue((engine.reduce(accepted, GameCommand.UnassignDriver(CommandId("unassign"), driver.id)) as GameResult.Rejected).reason is Rejection.DriverBusy)
        assertEquals(1, accepted.activeJobs.size)
    }

    @Test
    fun nonManualJobRequiresAssignedAvailableDriver() {
        val state = generated(eligible())
        val offer = feasibleOffer(state)
        val result = engine.reduce(state.copy(offers = listOf(offer)), GameCommand.AcceptJob(CommandId("accept"), offer.id, VehicleId("vehicle-1"), offer.routeOptions.first(), manualDriving = false)) as GameResult.Rejected
        assertTrue(result.reason is Rejection.AssignedDriverRequired)
        assertEquals(state.copy(offers = listOf(offer)), result.state)
    }

    @Test
    fun wageAndExperienceAreBookedExactlyOncePerWorkedDay() {
        val ready = hiredAssigned()
        val offer = feasibleOffer(ready)
        val accepted = applied(engine.reduce(ready.copy(offers = listOf(offer)), GameCommand.AcceptJob(CommandId("accept"), offer.id, VehicleId("vehicle-2"), offer.routeOptions.first(), manualDriving = false))).state
        val job = accepted.activeJobs.single()
        val completed = applied(engine.reduce(accepted, GameCommand.AdvanceTime(CommandId("finish"), job.completionAt.millis - accepted.gameTime.millis))).state
        val driver = completed.drivers.single()
        assertEquals(1, driver.experienceJobs)
        assertEquals(setOf(4), driver.wageChargedGameDays)
        assertEquals(1, completed.ledger.count { it.type == LedgerType.DRIVER_WAGE })
        val later = applied(engine.reduce(completed, GameCommand.AdvanceTime(CommandId("later"), 1))).state
        assertEquals(1, later.ledger.count { it.type == LedgerType.DRIVER_WAGE })
    }

    @Test
    fun oneManualJobCanRunAlongsideOneHiredDriverJob() {
        val ready = hiredAssigned()
        val offers = twoFeasibleOffers(ready)
        val manual = applied(engine.reduce(ready.copy(offers = offers), GameCommand.AcceptJob(CommandId("manual"), offers[0].id, VehicleId("vehicle-1"), offers[0].routeOptions.first(), true))).state
        val parallel = applied(engine.reduce(manual, GameCommand.AcceptJob(CommandId("driver"), offers[1].id, VehicleId("vehicle-2"), offers[1].routeOptions.first(), false))).state
        assertEquals(2, parallel.activeJobs.size)
        assertEquals(1, parallel.activeJobs.count { it.manualDriving })
        assertEquals(1, parallel.activeJobs.count { it.driverId != null })
    }

    private fun eligible(): GameState = newGame(seed = 20).copy(gameTime = GameInstant(3 * GameEngine.DAY_MILLIS), gameDay = 4, progression = ProgressionState(completedJobs = 8))

    private fun hiredAssigned(): GameState {
        val owned = VehicleState(VehicleId("vehicle-2"), VehicleSpecId("city-van"), Ownership.OWNED, assignedDriverId = DriverId("driver-3"))
        val driver = DriverState(DriverId("driver-3"), DriverTierId("junior"), "Mina", assignedVehicleId = owned.id)
        return generated(eligible().copy(money = Money(100_000), nextEntitySequence = 10, vehicles = eligible().vehicles + owned, drivers = listOf(driver)))
    }

    private fun generated(state: GameState): GameState = applied(engine.reduce(state, GameCommand.GenerateDailyOffers(CommandId("generate")))).state

    private fun feasibleOffer(state: GameState): JobOffer = state.offers.first().copy(packageTypeId = PackageTypeId("parcel"), count = 1, totalWeightGrams = 2_000, requiredCapabilities = emptySet(), riskId = RiskId("calm"))

    private fun twoFeasibleOffers(state: GameState): List<JobOffer> {
        val first = feasibleOffer(state)
        return listOf(first, first.copy(id = OfferId("offer-second")))
    }

    private fun applied(result: GameResult): GameResult.Applied = result as? GameResult.Applied ?: error("Unexpected rejection: $result")
}
