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
        if (state.gameTime.millis < 0 || state.lastRealtimeMillis < 0 || state.money.cents < 0) add(StateIssue.InvalidValue("timeOrMoney"))
        if (state.offersGeneratedGameDay != null && state.offersGeneratedGameDay !in 1..state.gameDay) add(StateIssue.InvalidValue("offersGeneratedGameDay"))

        val specs = catalog.vehicles.associateBy { it.id }
        val tiers = catalog.driverTiers.associateBy { it.id }
        val packages = catalog.packageTypes.associateBy { it.id }
        val risks = catalog.risks.associateBy { it.id }
        val locations = catalog.locations.associateBy { it.id }
        val routes = catalog.routes.associateBy { it.id }
        val regionIds = catalog.regions.map { it.id }.toSet()
        val vehicleIds = state.vehicles.map { it.id }.toSet()
        val driverIds = state.drivers.map { it.id }.toSet()

        state.vehicles.forEach { vehicle ->
            if (vehicle.specId !in specs) add(StateIssue.MissingReference("vehicle.spec", vehicle.specId.value))
            if (vehicle.conditionPercent !in 1..100 || vehicle.mileageMeters < 0 || vehicle.maintainedAtMeters !in 0..vehicle.mileageMeters || vehicle.totalOperatingCosts.cents < 0 || vehicle.upkeepChargedGameDays.any { it <= 0 }) add(StateIssue.InvalidValue("vehicle:${vehicle.id.value}"))
            vehicle.assignedDriverId?.let { driverId ->
                val driver = state.drivers.firstOrNull { it.id == driverId }
                if (driver == null) add(StateIssue.MissingReference("vehicle.driver", driverId.value))
                else if (driver.assignedVehicleId != vehicle.id) add(StateIssue.ConflictingAssignment("vehicle-driver", vehicle.id.value))
            }
        }
        state.drivers.forEach { driver ->
            if (driver.tierId !in tiers) add(StateIssue.MissingReference("driver.tier", driver.tierId.value))
            if (driver.experienceJobs < 0 || driver.wageChargedGameDays.any { it <= 0 }) add(StateIssue.InvalidValue("driver:${driver.id.value}"))
            driver.assignedVehicleId?.let { vehicleId ->
                val vehicle = state.vehicles.firstOrNull { it.id == vehicleId }
                if (vehicle == null) add(StateIssue.MissingReference("driver.vehicle", vehicleId.value))
                else if (vehicle.assignedDriverId != driver.id) add(StateIssue.ConflictingAssignment("driver-vehicle", driver.id.value))
            }
        }
        state.vehicles.mapNotNull { it.assignedDriverId }.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.forEach { add(StateIssue.ConflictingAssignment("driver", it.value)) }
        state.drivers.mapNotNull { it.assignedVehicleId }.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.forEach { add(StateIssue.ConflictingAssignment("vehicle", it.value)) }

        state.offers.forEach { offer ->
            val packageType = packages[offer.packageTypeId]
            if (packageType == null) add(StateIssue.MissingReference("offer.package", offer.packageTypeId.value))
            if (offer.originId !in locations) add(StateIssue.MissingReference("offer.origin", offer.originId.value))
            if (offer.destinationId !in locations) add(StateIssue.MissingReference("offer.destination", offer.destinationId.value))
            if (offer.riskId !in risks) add(StateIssue.MissingReference("offer.risk", offer.riskId.value))
            offer.routeOptions.forEach { routeId ->
                val route = routes[routeId]
                if (route == null) add(StateIssue.MissingReference("offer.route", routeId.value))
                else if (route.originId != offer.originId || route.destinationId != offer.destinationId) add(StateIssue.InvalidValue("offerRoute:${offer.id.value}"))
            }
            val invalidPackageRange = packageType != null && offer.count > 0 && runCatching {
                offer.totalWeightGrams < Math.multiplyExact(packageType.minWeightGrams, offer.count.toLong()) || offer.totalWeightGrams > Math.multiplyExact(packageType.maxWeightGrams, offer.count.toLong())
            }.getOrDefault(true)
            if (offer.count <= 0 || offer.totalWeightGrams <= 0 || offer.routeOptions.isEmpty() || offer.expiresAt.millis < 0 || invalidPackageRange) add(StateIssue.InvalidValue("offer:${offer.id.value}"))
        }

        state.activeJobs.map { it.vehicleId }.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.forEach { add(StateIssue.ConflictingAssignment("activeVehicle", it.value)) }
        state.activeJobs.mapNotNull { it.driverId }.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.forEach { add(StateIssue.ConflictingAssignment("activeDriver", it.value)) }
        if (state.activeJobs.count { it.manualDriving } > 1) add(StateIssue.ConflictingAssignment("manualDriver", "player"))
        state.activeJobs.forEach { job ->
            val vehicle = state.vehicles.firstOrNull { it.id == job.vehicleId }
            if (vehicle == null) add(StateIssue.MissingReference("job.vehicle", job.vehicleId.value)) else if (vehicle.status != VehicleStatus.BUSY) add(StateIssue.InvalidStatus("vehicle", vehicle.id.value))
            if (job.routeId !in routes) add(StateIssue.MissingReference("job.route", job.routeId.value))
            if (job.riskId !in risks) add(StateIssue.MissingReference("job.risk", job.riskId.value))
            job.driverId?.let { driverId ->
                val driver = state.drivers.firstOrNull { it.id == driverId }
                if (driver == null) add(StateIssue.MissingReference("job.driver", driverId.value)) else if (driver.status != DriverStatus.DRIVING || driver.assignedVehicleId != job.vehicleId || vehicle?.assignedDriverId != driverId) add(StateIssue.InvalidStatus("driver", driverId.value))
            }
            if (!job.manualDriving && job.driverId == null) add(StateIssue.MissingReference("job.driver", job.id.value))
            if (job.countedInvalid()) add(StateIssue.InvalidValue("job:${job.id.value}"))
        }

        state.progression.unlockedRegionIds.filterNot(regionIds::contains).forEach { add(StateIssue.MissingReference("progression.region", it.value)) }
        if (state.progression.completedJobs < 0 || state.progression.onTimeJobs !in 0..state.progression.completedJobs) add(StateIssue.InvalidValue("progression"))
        state.completedJobs.forEach { if (it.completedAt.millis < 0 || it.revenue.cents < 0 || it.costs.cents < 0 || it.net != it.revenue - it.costs) add(StateIssue.InvalidValue("completedJob:${it.jobId.value}")) }
        state.dailySummaries.forEach { if (it.gameDay <= 0 || it.completedJobs < 0 || it.revenue.cents < 0 || it.costs.cents < 0) add(StateIssue.InvalidValue("dailySummary:${it.gameDay}")) }
        state.ledger.forEach { entry ->
            if (entry.at.millis < 0 || entry.amount.cents == 0L) add(StateIssue.InvalidValue("ledger:${entry.id.value}"))
        }
    }

    private fun ActiveJob.countedInvalid(): Boolean {
        val money = listOf(invoice.baseReward, invoice.distanceReward, invoice.riskBonus, invoice.reservedFuel, invoice.reservedRental, invoice.reservedPenalty, invoice.reservedUpkeep, invoice.reservedDriverWage, reserved, reservedOperatingCosts)
        return dueAt < startedAt || completionAt < startedAt || distanceMeters <= 0 || riskChancePerMillion !in 0..250_000 || riskRollPerMillion !in 0 until 1_000_000 || invoice.penaltyBasisPoints !in 0..10_000 || money.any { it.cents < 0 } || reserved != invoice.maximumReservation || reservedOperatingCosts != invoice.reservedUpkeep + invoice.reservedDriverWage || chargedOperatingGameDays.any { it <= 0 }
    }

    private fun duplicates(kind: String, ids: List<String>, issues: MutableList<StateIssue>) {
        ids.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.forEach { issues += StateIssue.DuplicateId(kind, it) }
    }
}
