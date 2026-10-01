package com.canok.kargotycoon.ui.state

import com.canok.kargotycoon.game.domain.ActiveJob
import com.canok.kargotycoon.game.domain.DriverId
import com.canok.kargotycoon.game.domain.DriverState
import com.canok.kargotycoon.game.domain.GameCatalog
import com.canok.kargotycoon.game.domain.GameState
import com.canok.kargotycoon.game.domain.Ownership
import com.canok.kargotycoon.game.domain.TutorialStep
import com.canok.kargotycoon.game.domain.VehicleId
import com.canok.kargotycoon.game.domain.VehicleSpec
import com.canok.kargotycoon.game.domain.VehicleSpecId
import com.canok.kargotycoon.game.domain.VehicleState
import com.canok.kargotycoon.game.domain.VehicleStatus

/**
 * Read-only projections over engine state. These never mutate state and never
 * re-implement economy rules; they only look up catalog entries and totals that
 * the engine already produced.
 */
object Projections {
    fun spec(catalog: GameCatalog, id: VehicleSpecId): VehicleSpec? = catalog.vehicles.firstOrNull { it.id == id }

    fun specOf(catalog: GameCatalog, vehicle: VehicleState): VehicleSpec? = spec(catalog, vehicle.specId)

    fun vehicle(state: GameState, id: VehicleId): VehicleState? = state.vehicles.firstOrNull { it.id == id }

    fun driver(state: GameState, id: DriverId): DriverState? = state.drivers.firstOrNull { it.id == id }

    fun driverForVehicle(state: GameState, vehicle: VehicleState): DriverState? =
        vehicle.assignedDriverId?.let { id -> driver(state, id) }

    fun activeJobForVehicle(state: GameState, vehicleId: VehicleId): ActiveJob? =
        state.activeJobs.firstOrNull { it.vehicleId == vehicleId }

    fun activeJobForDriver(state: GameState, driverId: DriverId): ActiveJob? =
        state.activeJobs.firstOrNull { it.driverId == driverId }

    /** Vehicles that can take a new job right now: available and not on an active route. */
    fun availableVehicles(state: GameState): List<VehicleState> =
        state.vehicles.filter { it.status == VehicleStatus.AVAILABLE && activeJobForVehicle(state, it.id) == null }

    fun ownedVehicleCount(state: GameState): Int = state.vehicles.count { it.ownership == Ownership.OWNED }

    fun purchasableSpecs(catalog: GameCatalog, state: GameState): List<VehicleSpec> =
        catalog.vehicles.filterNot { it.rental }.sortedBy { it.purchasePrice.cents }

    /** Vehicles a driver may be assigned to: available, not busy, and without a driver yet. */
    fun assignableVehicles(state: GameState): List<VehicleState> =
        state.vehicles.filter { it.assignedDriverId == null && it.status == VehicleStatus.AVAILABLE && activeJobForVehicle(state, it.id) == null }

    fun canSell(vehicle: VehicleState, state: GameState): Boolean =
        vehicle.ownership == Ownership.OWNED &&
            vehicle.status == VehicleStatus.AVAILABLE &&
            vehicle.assignedDriverId == null &&
            activeJobForVehicle(state, vehicle.id) == null

    fun canRepair(vehicle: VehicleState, state: GameState): Boolean =
        vehicle.conditionPercent < 100 &&
            vehicle.status != VehicleStatus.BUSY &&
            activeJobForVehicle(state, vehicle.id) == null

    fun canMaintain(vehicle: VehicleState, state: GameState, catalog: GameCatalog): Boolean =
        vehicle.status != VehicleStatus.BUSY &&
            activeJobForVehicle(state, vehicle.id) == null &&
            vehicle.mileageMeters - vehicle.maintainedAtMeters >= catalog.economy.maintenanceIntervalMeters
}

/** The bottom-bar root the tutorial step should point the player at. */
fun tutorialRouteTag(step: TutorialStep): String = when (step) {
    TutorialStep.ACCEPT_FIRST_JOB -> "jobs"
    TutorialStep.COMPLETE_FIRST_JOB -> "dashboard"
    TutorialStep.BUY_FIRST_VEHICLE -> "fleet"
    TutorialStep.HIRE_FIRST_DRIVER -> "team"
    TutorialStep.START_PARALLEL_JOBS -> "jobs"
    TutorialStep.COMPLETE -> "dashboard"
}
