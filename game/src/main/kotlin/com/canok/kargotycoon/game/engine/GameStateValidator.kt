package com.canok.kargotycoon.game.engine

import com.canok.kargotycoon.game.domain.*

sealed interface StateIssue {
    data class DuplicateId(val kind: String, val id: String) : StateIssue
    data class MissingReference(val kind: String, val id: String) : StateIssue
    data class ConflictingAssignment(val kind: String, val id: String) : StateIssue
    data class InvalidStatus(val kind: String, val id: String) : StateIssue
    data class InvalidValue(val field: String) : StateIssue
}

object GameStateValidator {
    fun validate(state: GameState, catalog: GameCatalog): List<StateIssue> = buildList {
        duplicates("vehicle", state.vehicles.map { it.id.value }, this)
        duplicates("driver", state.drivers.map { it.id.value }, this)
        duplicates("offer", state.offers.map { it.id.value }, this)
        duplicates("activeJob", state.activeJobs.map { it.id.value }, this)
        duplicates("completedJob", state.completedJobs.map { it.jobId.value }, this)
        duplicates("ledger", state.ledger.map { it.id.value }, this)
        if (state.stateVersion <= 0 || state.catalogVersion != catalog.version || state.revision < 0 || state.gameDay <= 0 || state.nextEntitySequence <= 0 || state.randomCounter < 0) add(StateIssue.InvalidValue("metadata"))
        val specs = catalog.vehicles.map { it.id }.toSet()
        val tiers = catalog.driverTiers.map { it.id }.toSet()
        val vehicleIds = state.vehicles.map { it.id }.toSet()
        val driverIds = state.drivers.map { it.id }.toSet()
        state.vehicles.forEach { vehicle ->
            if (vehicle.specId !in specs) add(StateIssue.MissingReference("vehicle.spec", vehicle.specId.value))
            if (vehicle.conditionPercent !in 1..100 || vehicle.mileageMeters < 0 || vehicle.maintainedAtMeters !in 0..vehicle.mileageMeters) add(StateIssue.InvalidValue("vehicle:${vehicle.id.value}"))
            vehicle.assignedDriverId?.let { if (it !in driverIds) add(StateIssue.MissingReference("vehicle.driver", it.value)) }
        }
        state.drivers.forEach { driver ->
            if (driver.tierId !in tiers) add(StateIssue.MissingReference("driver.tier", driver.tierId.value))
            driver.assignedVehicleId?.let { if (it !in vehicleIds) add(StateIssue.MissingReference("driver.vehicle", it.value)) }
            if (driver.experienceJobs < 0 || driver.wageChargedGameDays.any { it <= 0 }) add(StateIssue.InvalidValue("driver:${driver.id.value}"))
        }
        state.vehicles.mapNotNull { it.assignedDriverId }.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.forEach { add(StateIssue.ConflictingAssignment("driver", it.value)) }
        state.drivers.mapNotNull { it.assignedVehicleId }.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.forEach { add(StateIssue.ConflictingAssignment("vehicle", it.value)) }
        state.vehicles.forEach { vehicle -> vehicle.assignedDriverId?.let { driverId -> if (state.drivers.firstOrNull { it.id == driverId }?.assignedVehicleId != vehicle.id) add(StateIssue.ConflictingAssignment("vehicle-driver", vehicle.id.value)) } }
        state.activeJobs.map { it.vehicleId }.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.forEach { add(StateIssue.ConflictingAssignment("activeVehicle", it.value)) }
        state.activeJobs.mapNotNull { it.driverId }.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.forEach { add(StateIssue.ConflictingAssignment("activeDriver", it.value)) }
        if (state.activeJobs.count { it.manualDriving } > 1) add(StateIssue.ConflictingAssignment("manualDriver", "player"))
        state.activeJobs.forEach { job ->
            val vehicle = state.vehicles.firstOrNull { it.id == job.vehicleId }
            if (vehicle == null) add(StateIssue.MissingReference("job.vehicle", job.vehicleId.value)) else if (vehicle.status != VehicleStatus.BUSY) add(StateIssue.InvalidStatus("vehicle", vehicle.id.value))
            job.driverId?.let { driverId ->
                val driver = state.drivers.firstOrNull { it.id == driverId }
                if (driver == null) add(StateIssue.MissingReference("job.driver", driverId.value)) else if (driver.status != DriverStatus.DRIVING || driver.assignedVehicleId != job.vehicleId) add(StateIssue.InvalidStatus("driver", driverId.value))
            }
            if (!job.manualDriving && job.driverId == null) add(StateIssue.MissingReference("job.driver", job.id.value))
            if (job.countedInvalid()) add(StateIssue.InvalidValue("job:${job.id.value}"))
        }
    }

    private fun ActiveJob.countedInvalid(): Boolean = dueAt < startedAt || completionAt < startedAt || reserved.cents < 0 || distanceMeters <= 0 || riskChancePerMillion !in 0..250_000 || riskRollPerMillion !in 0 until 1_000_000

    private fun duplicates(kind: String, ids: List<String>, issues: MutableList<StateIssue>) {
        ids.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.forEach { issues += StateIssue.DuplicateId(kind, it) }
    }
}
