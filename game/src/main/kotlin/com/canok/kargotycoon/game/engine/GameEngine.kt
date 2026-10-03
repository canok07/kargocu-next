package com.canok.kargotycoon.game.engine

import com.canok.kargotycoon.game.domain.*
import com.canok.kargotycoon.game.time.GameTimePolicy

sealed interface GameCommand {
    val commandId: CommandId
    data class GenerateDailyOffers(override val commandId: CommandId) : GameCommand
    data class AcceptJob(override val commandId: CommandId, val offerId: OfferId, val vehicleId: VehicleId, val routeId: RouteId, val manualDriving: Boolean = true) : GameCommand
    data class AdvanceTime(override val commandId: CommandId, val gameMillis: Long) : GameCommand
    data class Resume(override val commandId: CommandId, val realtimeNowMillis: Long) : GameCommand
    data class AdvanceDay(override val commandId: CommandId) : GameCommand
    data class PurchaseVehicle(override val commandId: CommandId, val specId: VehicleSpecId) : GameCommand
    data class SellVehicle(override val commandId: CommandId, val vehicleId: VehicleId) : GameCommand
    data class RepairVehicle(override val commandId: CommandId, val vehicleId: VehicleId) : GameCommand
    data class MaintainVehicle(override val commandId: CommandId, val vehicleId: VehicleId) : GameCommand
    data class HireDriver(override val commandId: CommandId, val tierId: DriverTierId, val name: String) : GameCommand
    data class FireDriver(override val commandId: CommandId, val driverId: DriverId) : GameCommand
    data class AssignDriver(override val commandId: CommandId, val driverId: DriverId, val vehicleId: VehicleId) : GameCommand
    data class UnassignDriver(override val commandId: CommandId, val driverId: DriverId) : GameCommand
    data class RefreshProgression(override val commandId: CommandId) : GameCommand
    data class ChangeSettings(override val commandId: CommandId, val settings: GameSettings) : GameCommand
    data class SetTutorialDismissed(override val commandId: CommandId, val dismissed: Boolean) : GameCommand
}

sealed interface Rejection {
    data class DuplicateCommand(val commandId: CommandId) : Rejection
    data class NotFound(val kind: String, val id: String) : Rejection
    data class OfferExpired(val offerId: OfferId) : Rejection
    data class VehicleUnavailable(val vehicleId: VehicleId) : Rejection
    data class CapacityExceeded(val availableCount: Int, val requestedCount: Int, val availableGrams: Long, val requestedGrams: Long) : Rejection
    data class MissingCapability(val capabilities: Set<Capability>) : Rejection
    data class RouteUnavailable(val routeId: RouteId) : Rejection
    data class RegionLocked(val regionId: RegionId) : Rejection
    data class InsufficientFunds(val required: Money, val available: Money) : Rejection
    data object ManualDriverBusy : Rejection
    data class InvalidAdvance(val gameMillis: Long) : Rejection
    data class CompanyLevelRequired(val required: Int, val actual: Int) : Rejection
    data class RentalVehicleProtected(val vehicleId: VehicleId) : Rejection
    data class VehicleBusy(val vehicleId: VehicleId) : Rejection
    data class VehicleAlreadyHealthy(val vehicleId: VehicleId) : Rejection
    data class MaintenanceNotDue(val vehicleId: VehicleId) : Rejection
    data class InvalidDriverName(val name: String) : Rejection
    data class DriverBusy(val driverId: DriverId) : Rejection
    data class DriverAlreadyAssigned(val driverId: DriverId) : Rejection
    data class VehicleAlreadyAssigned(val vehicleId: VehicleId) : Rejection
    data class AssignedDriverRequired(val vehicleId: VehicleId) : Rejection
    data class DriverUnavailable(val driverId: DriverId) : Rejection
    data class StateInvariantViolation(val issues: List<StateIssue>) : Rejection
    data class ArithmeticFailure(val operation: String) : Rejection
    data object InvalidSettings : Rejection
}

sealed interface GameEvent {
    data class OffersGenerated(val gameDay: Int, val count: Int) : GameEvent
    data class JobAccepted(val jobId: JobId, val reserved: Money) : GameEvent
    data class JobCompleted(val jobId: JobId, val result: JobResult) : GameEvent
    data class LedgerBooked(val entry: LedgerEntry) : GameEvent
    data class DayClosed(val summary: DailySummary) : GameEvent
    data class TimeAdvanced(val from: GameInstant, val to: GameInstant) : GameEvent
    data class VehiclePurchased(val vehicleId: VehicleId, val specId: VehicleSpecId) : GameEvent
    data class VehicleSold(val vehicleId: VehicleId, val proceeds: Money) : GameEvent
    data class VehicleRepaired(val vehicleId: VehicleId, val cost: Money) : GameEvent
    data class VehicleMaintained(val vehicleId: VehicleId, val cost: Money) : GameEvent
    data class DriverHired(val driverId: DriverId, val tierId: DriverTierId) : GameEvent
    data class DriverFired(val driverId: DriverId, val severance: Money) : GameEvent
    data class DriverAssigned(val driverId: DriverId, val vehicleId: VehicleId) : GameEvent
    data class DriverUnassigned(val driverId: DriverId, val vehicleId: VehicleId) : GameEvent
}

sealed interface GameResult {
    val state: GameState
    data class Applied(override val state: GameState, val events: List<GameEvent>) : GameResult
    data class Rejected(override val state: GameState, val reason: Rejection) : GameResult
}

data class NextTargetProjection(val target: String, val current: Long, val required: Long)

data class DailyOfferSummary(val gameDay: Int, val availableOffers: Int, val activeJobs: Int, val potentialGross: Money)

fun interface GameClock { fun nowMillis(): Long }

class GameEngine(
    private val catalog: GameCatalog = DefaultCatalog.value,
    private val timePolicy: GameTimePolicy = GameTimePolicy(),
) {
    fun reduce(state: GameState, command: GameCommand): GameResult {
        if (command.commandId in state.processedCommandIds) return GameResult.Rejected(state, Rejection.DuplicateCommand(command.commandId))
        val inputIssues = GameStateValidator.validate(state, catalog)
        if (inputIssues.isNotEmpty()) return GameResult.Rejected(state, Rejection.StateInvariantViolation(inputIssues))
        return try {
            val result = when (command) {
                is GameCommand.GenerateDailyOffers -> generateOffers(state, command.commandId)
                is GameCommand.AcceptJob -> acceptJob(state, command)
                is GameCommand.AdvanceTime -> if (command.gameMillis < 0) GameResult.Rejected(state, Rejection.InvalidAdvance(command.gameMillis)) else advance(state, command.commandId, state.gameTime.plusMillis(command.gameMillis))
                is GameCommand.Resume -> {
                    val elapsed = Math.subtractExact(command.realtimeNowMillis, state.lastRealtimeMillis).coerceAtLeast(0)
                    val target = state.gameTime.plusMillis(timePolicy.simulatedElapsedMillis(elapsed))
                    advance(state.copy(lastRealtimeMillis = command.realtimeNowMillis), command.commandId, target)
                }
                is GameCommand.AdvanceDay -> {
                    val targetDay = Math.addExact(state.gameDay, 1)
                    advance(state, command.commandId, GameInstant(Math.multiplyExact(targetDay.toLong() - 1L, DAY_MILLIS)))
                }
                is GameCommand.PurchaseVehicle -> purchaseVehicle(state, command)
                is GameCommand.SellVehicle -> sellVehicle(state, command)
                is GameCommand.RepairVehicle -> repairVehicle(state, command)
                is GameCommand.MaintainVehicle -> maintainVehicle(state, command)
                is GameCommand.HireDriver -> hireDriver(state, command)
                is GameCommand.FireDriver -> fireDriver(state, command)
                is GameCommand.AssignDriver -> assignDriver(state, command)
                is GameCommand.UnassignDriver -> unassignDriver(state, command)
                is GameCommand.RefreshProgression -> GameResult.Applied(markProcessed(unlockRegions(state), command.commandId), emptyList())
                is GameCommand.ChangeSettings -> if (command.settings.languageTag !in setOf("tr", "en")) {
                    GameResult.Rejected(state, Rejection.InvalidSettings)
                } else GameResult.Applied(markProcessed(state.copy(settings = command.settings), command.commandId), emptyList())
                is GameCommand.SetTutorialDismissed -> GameResult.Applied(
                    markProcessed(state.copy(tutorial = state.tutorial.copy(dismissed = command.dismissed)), command.commandId), emptyList(),
                )
            }
            if (result is GameResult.Applied) {
                val issues = GameStateValidator.validate(result.state, catalog)
                if (issues.isEmpty()) result else GameResult.Rejected(state, Rejection.StateInvariantViolation(issues))
            } else result
        } catch (_: ArithmeticException) {
            GameResult.Rejected(state, Rejection.ArithmeticFailure(command::class.simpleName ?: "command"))
        }
    }

    fun dailySummary(state: GameState): DailyOfferSummary = DailyOfferSummary(
        state.gameDay,
        state.offers.count { it.expiresAt > state.gameTime },
        state.activeJobs.size,
        state.offers.fold(Money.ZERO) { sum, offer -> sum + quote(offer, offer.routeOptions.first()).maximumRevenue },
    )

    fun nextTargets(state: GameState): List<NextTargetProjection> {
        val owned = state.vehicles.count { it.ownership == Ownership.OWNED }.toLong()
        val level = state.companyLevel(catalog)
        val nextLevel = catalog.progression.levels.firstOrNull { it.level > level }
        val nextVehicle = catalog.vehicles.filterNot { it.rental }.filter { it.minimumCompanyLevel <= level }.minByOrNull { it.purchasePrice.cents }
        return buildList {
            if (nextLevel != null && nextLevel.minimumDistinctVehicleSpecs > 0) add(NextTargetProjection("vehicle-types", state.distinctOwnedVehicleSpecs().toLong(), nextLevel.minimumDistinctVehicleSpecs.toLong()))
            if (nextLevel != null) add(NextTargetProjection("company-level-${nextLevel.level}", state.progression.completedJobs.toLong(), nextLevel.minimumCompletedJobs.toLong()))
            if (nextVehicle != null) add(NextTargetProjection("vehicle-${nextVehicle.id.value}", state.money.cents, nextVehicle.purchasePrice.cents))
            catalog.regions.firstOrNull { it.id !in state.progression.unlockedRegionIds }?.let { add(NextTargetProjection("region-${it.id.value}", state.progression.completedJobs.toLong(), it.minimumCompletedJobs.toLong())) }
            if (owned == 0L) add(NextTargetProjection("first-owned-vehicle", 0, 1))
        }
    }

    private fun hireDriver(state: GameState, command: GameCommand.HireDriver): GameResult {
        val tier = catalog.driverTiers.firstOrNull { it.id == command.tierId } ?: return GameResult.Rejected(state, Rejection.NotFound("driverTier", command.tierId.value))
        if (command.name.isBlank() || command.name.length > 40) return GameResult.Rejected(state, Rejection.InvalidDriverName(command.name))
        val level = state.companyLevel(catalog)
        if (level < tier.minimumCompanyLevel) return GameResult.Rejected(state, Rejection.CompanyLevelRequired(tier.minimumCompanyLevel, level))
        if (state.money < tier.hiringCost) return GameResult.Rejected(state, Rejection.InsufficientFunds(tier.hiringCost, state.money))
        val driverId = DriverId("driver-${state.nextEntitySequence}")
        val entry = ledger(state, LedgerType.DRIVER_HIRE, Money(-tier.hiringCost.cents), driverId = driverId)
        val next = markProcessed(state.copy(
            money = state.money - tier.hiringCost,
            nextEntitySequence = state.nextEntitySequence + 1,
            drivers = state.drivers + DriverState(driverId, tier.id, command.name.trim()),
            ledger = bounded(state.ledger + entry),
            tutorial = if (state.tutorial.step == TutorialStep.HIRE_FIRST_DRIVER) state.tutorial.copy(step = TutorialStep.START_PARALLEL_JOBS) else state.tutorial,
        ), command.commandId)
        return GameResult.Applied(next, listOf(GameEvent.LedgerBooked(entry), GameEvent.DriverHired(driverId, tier.id)))
    }

    private fun fireDriver(state: GameState, command: GameCommand.FireDriver): GameResult {
        val driver = state.drivers.firstOrNull { it.id == command.driverId } ?: return GameResult.Rejected(state, Rejection.NotFound("driver", command.driverId.value))
        if (driver.status == DriverStatus.DRIVING || state.activeJobs.any { it.driverId == driver.id }) return GameResult.Rejected(state, Rejection.DriverBusy(driver.id))
        val tier = catalog.driverTiers.first { it.id == driver.tierId }
        val severance = tier.dailyWage * catalog.economy.severanceWageDays.toLong()
        if (state.money < severance) return GameResult.Rejected(state, Rejection.InsufficientFunds(severance, state.money))
        val entry = ledger(state, LedgerType.DRIVER_SEVERANCE, Money(-severance.cents), driverId = driver.id)
        val next = markProcessed(state.copy(
            money = state.money - severance,
            nextEntitySequence = state.nextEntitySequence + 1,
            drivers = state.drivers.filterNot { it.id == driver.id },
            vehicles = state.vehicles.map { if (it.assignedDriverId == driver.id) it.copy(assignedDriverId = null) else it },
            ledger = bounded(state.ledger + entry),
        ), command.commandId)
        return GameResult.Applied(next, listOf(GameEvent.LedgerBooked(entry), GameEvent.DriverFired(driver.id, severance)))
    }

    private fun assignDriver(state: GameState, command: GameCommand.AssignDriver): GameResult {
        val driver = state.drivers.firstOrNull { it.id == command.driverId } ?: return GameResult.Rejected(state, Rejection.NotFound("driver", command.driverId.value))
        val vehicle = state.vehicles.firstOrNull { it.id == command.vehicleId } ?: return GameResult.Rejected(state, Rejection.NotFound("vehicle", command.vehicleId.value))
        if (driver.status != DriverStatus.AVAILABLE) return GameResult.Rejected(state, Rejection.DriverUnavailable(driver.id))
        if (driver.assignedVehicleId != null) return GameResult.Rejected(state, Rejection.DriverAlreadyAssigned(driver.id))
        if (vehicle.assignedDriverId != null) return GameResult.Rejected(state, Rejection.VehicleAlreadyAssigned(vehicle.id))
        if (vehicle.status != VehicleStatus.AVAILABLE || state.activeJobs.any { it.vehicleId == vehicle.id }) return GameResult.Rejected(state, Rejection.VehicleBusy(vehicle.id))
        val next = markProcessed(state.copy(
            drivers = state.drivers.map { if (it.id == driver.id) it.copy(assignedVehicleId = vehicle.id) else it },
            vehicles = state.vehicles.map { if (it.id == vehicle.id) it.copy(assignedDriverId = driver.id) else it },
        ), command.commandId)
        return GameResult.Applied(next, listOf(GameEvent.DriverAssigned(driver.id, vehicle.id)))
    }

    private fun unassignDriver(state: GameState, command: GameCommand.UnassignDriver): GameResult {
        val driver = state.drivers.firstOrNull { it.id == command.driverId } ?: return GameResult.Rejected(state, Rejection.NotFound("driver", command.driverId.value))
        val vehicleId = driver.assignedVehicleId ?: return GameResult.Rejected(state, Rejection.NotFound("assignment", driver.id.value))
        if (driver.status == DriverStatus.DRIVING || state.activeJobs.any { it.driverId == driver.id }) return GameResult.Rejected(state, Rejection.DriverBusy(driver.id))
        if (state.vehicles.first { it.id == vehicleId }.status == VehicleStatus.BUSY) return GameResult.Rejected(state, Rejection.VehicleBusy(vehicleId))
        val next = markProcessed(state.copy(
            drivers = state.drivers.map { if (it.id == driver.id) it.copy(assignedVehicleId = null) else it },
            vehicles = state.vehicles.map { if (it.id == vehicleId) it.copy(assignedDriverId = null) else it },
        ), command.commandId)
        return GameResult.Applied(next, listOf(GameEvent.DriverUnassigned(driver.id, vehicleId)))
    }

    private fun purchaseVehicle(state: GameState, command: GameCommand.PurchaseVehicle): GameResult {
        val spec = catalog.vehicles.firstOrNull { it.id == command.specId && !it.rental } ?: return GameResult.Rejected(state, Rejection.NotFound("vehicleSpec", command.specId.value))
        val level = state.companyLevel(catalog)
        if (level < spec.minimumCompanyLevel) return GameResult.Rejected(state, Rejection.CompanyLevelRequired(spec.minimumCompanyLevel, level))
        if (state.money < spec.purchasePrice) return GameResult.Rejected(state, Rejection.InsufficientFunds(spec.purchasePrice, state.money))
        val vehicleId = VehicleId("vehicle-${state.nextEntitySequence}")
        val entry = ledger(state, LedgerType.VEHICLE_PURCHASE, Money(-spec.purchasePrice.cents), vehicleId = vehicleId)
        val next = markProcessed(state.copy(
            money = state.money - spec.purchasePrice,
            nextEntitySequence = Math.addExact(state.nextEntitySequence, 1),
            vehicles = state.vehicles + VehicleState(vehicleId, spec.id, Ownership.OWNED),
            ledger = bounded(state.ledger + entry),
            tutorial = if (state.tutorial.step == TutorialStep.BUY_FIRST_VEHICLE) state.tutorial.copy(step = TutorialStep.HIRE_FIRST_DRIVER) else state.tutorial,
        ), command.commandId)
        return GameResult.Applied(next, listOf(GameEvent.LedgerBooked(entry), GameEvent.VehiclePurchased(vehicleId, spec.id)))
    }

    private fun sellVehicle(state: GameState, command: GameCommand.SellVehicle): GameResult {
        val vehicle = state.vehicles.firstOrNull { it.id == command.vehicleId } ?: return GameResult.Rejected(state, Rejection.NotFound("vehicle", command.vehicleId.value))
        if (vehicle.ownership == Ownership.RENTAL) return GameResult.Rejected(state, Rejection.RentalVehicleProtected(vehicle.id))
        if (vehicle.status == VehicleStatus.BUSY || state.activeJobs.any { it.vehicleId == vehicle.id }) return GameResult.Rejected(state, Rejection.VehicleBusy(vehicle.id))
        if (vehicle.assignedDriverId != null) return GameResult.Rejected(state, Rejection.VehicleAlreadyAssigned(vehicle.id))
        val spec = catalog.vehicles.first { it.id == vehicle.specId }
        val proceeds = spec.purchasePrice.percentage(spec.resaleBasisPoints).percentage(vehicle.conditionPercent * 100)
        val entry = ledger(state, LedgerType.VEHICLE_SALE, proceeds, vehicleId = vehicle.id)
        val next = markProcessed(state.copy(
            money = state.money + proceeds,
            nextEntitySequence = Math.addExact(state.nextEntitySequence, 1),
            vehicles = state.vehicles.filterNot { it.id == vehicle.id },
            ledger = bounded(state.ledger + entry),
        ), command.commandId)
        return GameResult.Applied(next, listOf(GameEvent.LedgerBooked(entry), GameEvent.VehicleSold(vehicle.id, proceeds)))
    }

    private fun repairVehicle(state: GameState, command: GameCommand.RepairVehicle): GameResult {
        val vehicle = state.vehicles.firstOrNull { it.id == command.vehicleId } ?: return GameResult.Rejected(state, Rejection.NotFound("vehicle", command.vehicleId.value))
        if (vehicle.status == VehicleStatus.BUSY || state.activeJobs.any { it.vehicleId == vehicle.id }) return GameResult.Rejected(state, Rejection.VehicleBusy(vehicle.id))
        if (vehicle.conditionPercent >= 100) return GameResult.Rejected(state, Rejection.VehicleAlreadyHealthy(vehicle.id))
        val spec = catalog.vehicles.first { it.id == vehicle.specId }
        val cost = Money(Math.multiplyExact((100 - vehicle.conditionPercent).toLong(), spec.repairCentsPerConditionPoint))
        if (state.money < cost) return GameResult.Rejected(state, Rejection.InsufficientFunds(cost, state.money))
        // A zero-cost repair (e.g. the free starter rental) must not book a zero ledger entry.
        val entries = if (cost.cents > 0) listOf(ledger(state, LedgerType.REPAIR, Money(-cost.cents), vehicleId = vehicle.id)) else emptyList()
        val next = markProcessed(state.copy(money = state.money - cost, nextEntitySequence = state.nextEntitySequence + 1, vehicles = state.vehicles.map { if (it.id == vehicle.id) it.copy(conditionPercent = 100, totalOperatingCosts = it.totalOperatingCosts + cost) else it }, ledger = bounded(state.ledger + entries)), command.commandId)
        return GameResult.Applied(next, entries.map { GameEvent.LedgerBooked(it) } + GameEvent.VehicleRepaired(vehicle.id, cost))
    }

    private fun maintainVehicle(state: GameState, command: GameCommand.MaintainVehicle): GameResult {
        val vehicle = state.vehicles.firstOrNull { it.id == command.vehicleId } ?: return GameResult.Rejected(state, Rejection.NotFound("vehicle", command.vehicleId.value))
        if (vehicle.status == VehicleStatus.BUSY || state.activeJobs.any { it.vehicleId == vehicle.id }) return GameResult.Rejected(state, Rejection.VehicleBusy(vehicle.id))
        if (vehicle.mileageMeters - vehicle.maintainedAtMeters < catalog.economy.maintenanceIntervalMeters) return GameResult.Rejected(state, Rejection.MaintenanceNotDue(vehicle.id))
        val spec = catalog.vehicles.first { it.id == vehicle.specId }
        val cost = spec.purchasePrice.percentage(catalog.economy.maintenanceCostBasisPoints)
        if (state.money < cost) return GameResult.Rejected(state, Rejection.InsufficientFunds(cost, state.money))
        // A zero-cost maintenance (e.g. the free starter rental) must not book a zero ledger entry.
        val entries = if (cost.cents > 0) listOf(ledger(state, LedgerType.MAINTENANCE, Money(-cost.cents), vehicleId = vehicle.id)) else emptyList()
        val next = markProcessed(state.copy(money = state.money - cost, nextEntitySequence = state.nextEntitySequence + 1, vehicles = state.vehicles.map { if (it.id == vehicle.id) it.copy(conditionPercent = minOf(100, it.conditionPercent + 15), maintainedAtMeters = it.mileageMeters, totalOperatingCosts = it.totalOperatingCosts + cost) else it }, ledger = bounded(state.ledger + entries)), command.commandId)
        return GameResult.Applied(next, entries.map { GameEvent.LedgerBooked(it) } + GameEvent.VehicleMaintained(vehicle.id, cost))
    }

    private fun generateOffers(state: GameState, commandId: CommandId): GameResult {
        if (state.offersGeneratedGameDay == state.gameDay) return GameResult.Applied(markProcessed(state, commandId), emptyList())
        if (state.offersGeneratedGameDay == null && state.offers.isNotEmpty()) return GameResult.Applied(markProcessed(state.copy(offersGeneratedGameDay = state.gameDay), commandId), emptyList())
        val generated = createDailyOffers(state) ?: return GameResult.Rejected(state, Rejection.NotFound("route", "unlocked"))
        val next = markProcessed(generated, commandId)
        return GameResult.Applied(next, listOf(GameEvent.OffersGenerated(state.gameDay, generated.offers.size)))
    }

    private fun createDailyOffers(state: GameState): GameState? {
        var counter = state.randomCounter
        val unlockedLocations = catalog.locations.filter { it.regionId in state.progression.unlockedRegionIds }.map { it.id }.toSet()
        val routes = catalog.routes.filter { it.originId in unlockedLocations && it.destinationId in unlockedLocations }
        if (routes.isEmpty()) return null
        val level = state.companyLevel(catalog)
        val packages = catalog.packageTypes.filter { it.minimumCompanyLevel <= level }
        if (packages.isEmpty()) return null
        val offerCount = (catalog.economy.offersPerDay + (state.progression.unlockedRegionIds.size - 1).coerceAtLeast(0) * catalog.economy.offersPerUnlockedRegion + (level - 1) * catalog.economy.offersPerCompanyLevel).coerceAtMost(catalog.economy.maximumOffersPerDay)
        val parcelCount = 6 + (level - 1) * catalog.economy.parcelsPerCompanyLevel
        var offers = List(offerCount) { index ->
            val route = routes[pick(state.randomSeed, counter++, routes.size)]
            val packageType = packages[pick(state.randomSeed, counter++, packages.size)]
            val risk = catalog.risks[pick(state.randomSeed, counter++, catalog.risks.size)]
            val count = 1 + pick(state.randomSeed, counter++, parcelCount)
            val unitWeightRange = (packageType.maxWeightGrams - packageType.minWeightGrams + 1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            val unitWeight = packageType.minWeightGrams + pick(state.randomSeed, counter++, unitWeightRange)
            JobOffer(
                OfferId("offer-${state.gameDay}-${index + 1}-${stablePositive(state.randomSeed, counter++)}"),
                packageType.id,
                count,
                Math.multiplyExact(unitWeight, count.toLong()),
                route.originId,
                route.destinationId,
                routes.filter { it.originId == route.originId && it.destinationId == route.destinationId && it.requiredCapabilities == route.requiredCapabilities }.map { it.id },
                packageType.capabilities + route.requiredCapabilities,
                risk.id,
                state.gameTime.plusMillis(Math.multiplyExact(catalog.economy.offerLifetimeGameMinutes.toLong(), MINUTE_MILLIS)),
            )
        }
        val starter = catalog.vehicles.first { it.id == catalog.starterVehicleSpecId }
        if (offers.none { isStarterAffordable(it, starter, state.gameTime, state.money) }) {
            val route = routes.first { it.requiredCapabilities.all(starter.capabilities::contains) }
            val packageType = catalog.packageTypes.first { it.capabilities.all(starter.capabilities::contains) && it.minWeightGrams <= starter.capacityGrams }
            val risk = catalog.risks.minBy { it.penaltyBasisPoints }
            val original = offers.first()
            offers = offers.toMutableList().also { generated ->
                generated[0] = original.copy(
                    packageTypeId = packageType.id,
                    count = 1,
                    totalWeightGrams = packageType.minWeightGrams,
                    originId = route.originId,
                    destinationId = route.destinationId,
                    routeOptions = listOf(route.id),
                    requiredCapabilities = packageType.capabilities + route.requiredCapabilities,
                    riskId = risk.id,
                )
            }
        }
        return state.copy(offers = offers.sortedBy { it.id.value }, randomCounter = counter, offersGeneratedGameDay = state.gameDay)
    }

    private fun isStarterAffordable(offer: JobOffer, starter: VehicleSpec, gameTime: GameInstant, money: Money): Boolean {
        if (offer.count > starter.capacityCount || offer.totalWeightGrams > starter.capacityGrams || !offer.requiredCapabilities.all(starter.capabilities::contains)) return false
        return offer.routeOptions.any { routeId ->
            val route = catalog.routes.first { it.id == routeId }
            val risk = catalog.risks.first { it.id == offer.riskId }
            val completionAt = gameTime.plusMillis(Math.multiplyExact(route.durationGameMinutes.toLong(), MINUTE_MILLIS))
            acceptanceInvoice(offer, route, starter, Ownership.RENTAL, risk, gameTime, completionAt, emptySet(), emptySet(), null).maximumReservation <= money
        }
    }

    private fun acceptJob(state: GameState, command: GameCommand.AcceptJob): GameResult {
        val offer = state.offers.firstOrNull { it.id == command.offerId } ?: return GameResult.Rejected(state, Rejection.NotFound("offer", command.offerId.value))
        if (offer.expiresAt <= state.gameTime) return GameResult.Rejected(state, Rejection.OfferExpired(offer.id))
        val packageType = catalog.packageTypes.firstOrNull { it.id == offer.packageTypeId } ?: return GameResult.Rejected(state, Rejection.NotFound("packageType", offer.packageTypeId.value))
        val risk = catalog.risks.firstOrNull { it.id == offer.riskId } ?: return GameResult.Rejected(state, Rejection.NotFound("risk", offer.riskId.value))
        val origin = catalog.locations.firstOrNull { it.id == offer.originId } ?: return GameResult.Rejected(state, Rejection.NotFound("location", offer.originId.value))
        val destination = catalog.locations.firstOrNull { it.id == offer.destinationId } ?: return GameResult.Rejected(state, Rejection.NotFound("location", offer.destinationId.value))
        if (offer.count <= 0 || offer.totalWeightGrams <= 0) return GameResult.Rejected(state, Rejection.StateInvariantViolation(listOf(StateIssue.InvalidValue("offer:${offer.id.value}"))))
        val vehicle = state.vehicles.firstOrNull { it.id == command.vehicleId } ?: return GameResult.Rejected(state, Rejection.NotFound("vehicle", command.vehicleId.value))
        val spec = catalog.vehicles.firstOrNull { it.id == vehicle.specId } ?: return GameResult.Rejected(state, Rejection.NotFound("vehicleSpec", vehicle.specId.value))
        if (vehicle.status != VehicleStatus.AVAILABLE || state.activeJobs.any { it.vehicleId == vehicle.id }) return GameResult.Rejected(state, Rejection.VehicleUnavailable(vehicle.id))
        if (command.manualDriving && state.activeJobs.any { it.manualDriving }) return GameResult.Rejected(state, Rejection.ManualDriverBusy)
        val assignedDriver = vehicle.assignedDriverId?.let { id -> state.drivers.firstOrNull { it.id == id } }
        if (!command.manualDriving && assignedDriver == null) return GameResult.Rejected(state, Rejection.AssignedDriverRequired(vehicle.id))
        if (!command.manualDriving && assignedDriver?.status != DriverStatus.AVAILABLE) return GameResult.Rejected(state, Rejection.DriverUnavailable(checkNotNull(assignedDriver).id))
        val driverTier = assignedDriver?.let { driver -> catalog.driverTiers.firstOrNull { it.id == driver.tierId } }
        if (assignedDriver != null && driverTier == null) return GameResult.Rejected(state, Rejection.NotFound("driverTier", assignedDriver.tierId.value))
        if (offer.count > spec.capacityCount || offer.totalWeightGrams > spec.capacityGrams) return GameResult.Rejected(state, Rejection.CapacityExceeded(spec.capacityCount, offer.count, spec.capacityGrams, offer.totalWeightGrams))
        val missing = offer.requiredCapabilities - spec.capabilities
        if (missing.isNotEmpty()) return GameResult.Rejected(state, Rejection.MissingCapability(missing))
        val route = catalog.routes.firstOrNull { it.id == command.routeId } ?: return GameResult.Rejected(state, Rejection.NotFound("route", command.routeId.value))
        if (route.id !in offer.routeOptions || route.originId != origin.id || route.destinationId != destination.id) return GameResult.Rejected(state, Rejection.RouteUnavailable(command.routeId))
        if (destination.regionId !in state.progression.unlockedRegionIds) return GameResult.Rejected(state, Rejection.RegionLocked(destination.regionId))
        val duration = Math.multiplyExact(route.durationGameMinutes.toLong(), MINUTE_MILLIS)
        val completionAt = state.gameTime.plusMillis(duration)
        val workedDays = workedGameDays(state.gameTime, completionAt)
        val invoice = acceptanceInvoice(offer, route, spec, vehicle.ownership, risk, state.gameTime, completionAt, vehicle.upkeepChargedGameDays, assignedDriver?.wageChargedGameDays.orEmpty(), driverTier)
        val reservation = invoice.maximumReservation
        if (state.money < reservation) return GameResult.Rejected(state, Rejection.InsufficientFunds(reservation, state.money))
        val jobId = JobId("job-${state.nextEntitySequence}")
        val riskRoll = pick(state.randomSeed, state.randomCounter, 1_000_000)
        val driverErrorChance = driverTier?.errorChancePerMillion ?: 0
        val snapshottedRiskChance = Math.addExact(risk.chancePerMillion, driverErrorChance).coerceAtMost(250_000)
        val operatingReservation = invoice.reservedUpkeep + invoice.reservedDriverWage
        val job = ActiveJob(jobId, offer.id, vehicle.id, if (command.manualDriving) null else assignedDriver?.id, command.manualDriving, route.id, risk.id, snapshottedRiskChance, state.gameTime, completionAt, completionAt, invoice, reservation, operatingReservation, workedDays, route.distanceMeters, riskRoll)
        val entry = ledger(state, LedgerType.RESERVATION, Money(-reservation.cents), jobId = jobId, vehicleId = vehicle.id)
        val activeAfterAcceptance = state.activeJobs.size + 1
        val tutorial = when {
            activeAfterAcceptance >= 2 && state.tutorial.step == TutorialStep.START_PARALLEL_JOBS -> state.tutorial.copy(step = TutorialStep.COMPLETE)
            state.tutorial.step == TutorialStep.ACCEPT_FIRST_JOB -> state.tutorial.copy(step = TutorialStep.COMPLETE_FIRST_JOB)
            else -> state.tutorial
        }
        val next = state.copy(
            money = state.money - reservation,
            nextEntitySequence = Math.addExact(state.nextEntitySequence, 1),
            randomCounter = Math.addExact(state.randomCounter, 1),
            vehicles = state.vehicles.map { if (it.id == vehicle.id) it.copy(status = VehicleStatus.BUSY) else it },
            drivers = state.drivers.map { if (it.id == job.driverId) it.copy(status = DriverStatus.DRIVING) else it },
            offers = state.offers.filterNot { it.id == offer.id },
            activeJobs = state.activeJobs + job,
            ledger = bounded(state.ledger + entry),
            tutorial = tutorial,
        )
        return GameResult.Applied(markProcessed(next, command.commandId), listOf(GameEvent.LedgerBooked(entry), GameEvent.JobAccepted(jobId, reservation)))
    }

    private fun advance(original: GameState, commandId: CommandId, target: GameInstant): GameResult {
        var state = original
        val events = mutableListOf<GameEvent>()
        val due = state.activeJobs.filter { it.completionAt <= target }.sortedWith(compareBy<ActiveJob> { it.completionAt }.thenBy { it.id.value })
        due.forEach { job ->
            val completed = settle(state, job)
            state = completed.first
            events += completed.second
        }
        val oldDay = state.gameDay
        val targetDay = Math.toIntExact(target.millis / DAY_MILLIS + 1L)
        for (day in oldDay until targetDay) {
            val summary = summarizeDay(state, day)
            state = state.copy(dailySummaries = bounded(state.dailySummaries + summary))
            events += GameEvent.DayClosed(summary)
        }
        val from = original.gameTime
        state = state.copy(gameTime = target, gameDay = targetDay, offers = state.offers.filter { it.expiresAt > target })
        state = unlockRegions(state)
        if (targetDay > oldDay && state.offersGeneratedGameDay != targetDay) {
            state = checkNotNull(createDailyOffers(state))
            events += GameEvent.OffersGenerated(targetDay, state.offers.size)
        }
        events += GameEvent.TimeAdvanced(from, target)
        return GameResult.Applied(markProcessed(state, commandId), events)
    }

    private fun settle(state: GameState, job: ActiveJob): Pair<GameState, List<GameEvent>> {
        val riskOccurred = job.riskRollPerMillion < job.riskChancePerMillion
        val penalty = if (riskOccurred) job.invoice.maximumRevenue.percentage(job.invoice.penaltyBasisPoints).let { minOf(it, job.invoice.reservedPenalty) } else Money.ZERO
        val fuel = job.invoice.reservedFuel
        val rental = job.invoice.reservedRental
        val vehicle = state.vehicles.first { it.id == job.vehicleId }
        val newlyChargedUpkeepDays = job.chargedOperatingGameDays - vehicle.upkeepChargedGameDays
        val upkeep = if (newlyChargedUpkeepDays.isEmpty()) Money.ZERO else job.invoice.reservedUpkeep
        val driver = job.driverId?.let { id -> state.drivers.first { it.id == id } }
        val newlyChargedWageDays = driver?.let { job.chargedOperatingGameDays - it.wageChargedGameDays }.orEmpty()
        val wage = if (newlyChargedWageDays.isEmpty()) Money.ZERO else job.invoice.reservedDriverWage
        val actualCosts = fuel + rental + penalty + upkeep + wage
        check(actualCosts <= job.reserved)
        val revenue = job.invoice.maximumRevenue
        val result = JobResult(job.id, job.completionAt, job.completionAt <= job.dueAt, riskOccurred, revenue, actualCosts, revenue - actualCosts)
        var sequence = state.nextEntitySequence
        val entries = buildList {
            if (job.reserved.cents > 0) add(LedgerEntry(LedgerEntryId("ledger-${sequence++}"), job.completionAt, LedgerType.RESERVATION_REFUND, job.reserved, job.id, job.vehicleId))
            if (fuel.cents > 0) add(LedgerEntry(LedgerEntryId("ledger-${sequence++}"), job.completionAt, LedgerType.FUEL, Money(-fuel.cents), job.id, job.vehicleId))
            if (rental.cents > 0) add(LedgerEntry(LedgerEntryId("ledger-${sequence++}"), job.completionAt, LedgerType.RENTAL, Money(-rental.cents), job.id, job.vehicleId))
            if (penalty.cents > 0) add(LedgerEntry(LedgerEntryId("ledger-${sequence++}"), job.completionAt, LedgerType.PENALTY, Money(-penalty.cents), job.id, job.vehicleId))
            if (upkeep.cents > 0) add(LedgerEntry(LedgerEntryId("ledger-${sequence++}"), job.completionAt, LedgerType.VEHICLE_UPKEEP, Money(-upkeep.cents), job.id, job.vehicleId))
            if (wage.cents > 0) add(LedgerEntry(LedgerEntryId("ledger-${sequence++}"), job.completionAt, LedgerType.DRIVER_WAGE, Money(-wage.cents), job.id, job.vehicleId, job.driverId))
            add(LedgerEntry(LedgerEntryId("ledger-${sequence++}"), job.completionAt, LedgerType.REVENUE, revenue, job.id, job.vehicleId))
        }
        val next = state.copy(
            money = state.money + job.reserved + revenue - actualCosts,
            nextEntitySequence = sequence,
            vehicles = state.vehicles.map { if (it.id == job.vehicleId) it.copy(status = VehicleStatus.AVAILABLE, conditionPercent = (it.conditionPercent - maxOf(1, Math.toIntExact(job.distanceMeters / 200_000L))).coerceAtLeast(1), mileageMeters = Math.addExact(it.mileageMeters, job.distanceMeters), totalOperatingCosts = it.totalOperatingCosts + actualCosts - wage, totalEarned = it.totalEarned + result.net, upkeepChargedGameDays = it.upkeepChargedGameDays + newlyChargedUpkeepDays) else it },
            drivers = state.drivers.map { if (it.id == job.driverId) it.copy(status = DriverStatus.AVAILABLE, experienceJobs = Math.addExact(it.experienceJobs, 1), wageChargedGameDays = it.wageChargedGameDays + newlyChargedWageDays) else it },
            activeJobs = state.activeJobs.filterNot { it.id == job.id },
            completedJobs = bounded(state.completedJobs + result),
            ledger = bounded(state.ledger + entries),
            progression = state.progression.copy(completedJobs = Math.addExact(state.progression.completedJobs, 1), onTimeJobs = state.progression.onTimeJobs + if (result.onTime) 1 else 0),
            tutorial = if (state.tutorial.step == TutorialStep.ACCEPT_FIRST_JOB || state.tutorial.step == TutorialStep.COMPLETE_FIRST_JOB) state.tutorial.copy(step = TutorialStep.BUY_FIRST_VEHICLE) else state.tutorial,
        )
        return next to (entries.map { GameEvent.LedgerBooked(it) } + GameEvent.JobCompleted(job.id, result))
    }

    private fun quote(offer: JobOffer, routeId: RouteId): JobInvoice {
        val route = catalog.routes.first { it.id == routeId }
        val risk = catalog.risks.first { it.id == offer.riskId }
        val vehicle = catalog.vehicles.maxBy { it.fuelCentsPerKilometer }
        val startAt = GameInstant(offer.expiresAt.millis - Math.multiplyExact(catalog.economy.offerLifetimeGameMinutes.toLong(), MINUTE_MILLIS))
        val completionAt = startAt.plusMillis(Math.multiplyExact(route.durationGameMinutes.toLong(), MINUTE_MILLIS))
        return acceptanceInvoice(offer, route, vehicle, Ownership.RENTAL, risk, startAt, completionAt, emptySet(), emptySet(), null)
    }

    private fun acceptanceInvoice(
        offer: JobOffer,
        route: RouteSpec,
        vehicle: VehicleSpec,
        ownership: Ownership,
        risk: RiskSpec,
        startAt: GameInstant,
        completionAt: GameInstant,
        chargedUpkeepDays: Set<Int>,
        chargedWageDays: Set<Int>,
        driverTier: DriverTier?,
    ): JobInvoice {
        val packageType = catalog.packageTypes.first { it.id == offer.packageTypeId }
        val distance = Money(Math.multiplyExact(route.distanceMeters / 1_000L, catalog.economy.distanceRewardCentsPerKilometer))
        val parcelReward = Money(Math.multiplyExact(offer.count.toLong(), catalog.economy.parcelRewardCents))
        val weightReward = Money(Math.multiplyExact(offer.totalWeightGrams / 1_000L, catalog.economy.weightRewardCentsPerKilogram))
        val base = packageType.baseReward + parcelReward + weightReward
        val riskBonus = (base + distance).percentage(risk.rewardBasisPoints)
        val fuel = Money(Math.multiplyExact(route.distanceMeters / 1_000L, vehicle.fuelCentsPerKilometer))
        val rental = if (ownership == Ownership.RENTAL) Money(catalog.economy.rentalCentsPerJob) else Money.ZERO
        val penaltyBasisPoints = minOf(risk.penaltyBasisPoints, catalog.economy.maximumPenaltyBasisPoints)
        val penalty = (base + distance + riskBonus).percentage(penaltyBasisPoints)
        val workedDays = workedGameDays(startAt, completionAt)
        val upkeep = vehicle.dailyUpkeep * (workedDays - chargedUpkeepDays).size.toLong()
        val wage = driverTier?.dailyWage?.times((workedDays - chargedWageDays).size.toLong()) ?: Money.ZERO
        return JobInvoice(base, distance, riskBonus, fuel, rental, penalty, upkeep, wage, penaltyBasisPoints)
    }

    private fun workedGameDays(start: GameInstant, completion: GameInstant): Set<Int> {
        val first = Math.toIntExact(start.millis / DAY_MILLIS + 1L)
        val lastInstant = maxOf(start.millis, completion.millis - 1L)
        val last = Math.toIntExact(lastInstant / DAY_MILLIS + 1L)
        return (first..last).toSet()
    }

    private fun summarizeDay(state: GameState, day: Int): DailySummary {
        val start = (day - 1L) * DAY_MILLIS
        val end = day.toLong() * DAY_MILLIS
        val entries = state.ledger.filter { it.at.millis in start until end }
        val revenue = entries.filter { it.type == LedgerType.REVENUE }.fold(Money.ZERO) { sum, it -> sum + it.amount }
        val costTypes = setOf(LedgerType.PENALTY, LedgerType.FUEL, LedgerType.RENTAL, LedgerType.VEHICLE_UPKEEP, LedgerType.REPAIR, LedgerType.MAINTENANCE, LedgerType.DRIVER_WAGE, LedgerType.DRIVER_SEVERANCE)
        val costs = entries.filter { it.type in costTypes }.fold(Money.ZERO) { sum, it -> sum + Money(-it.amount.cents) }
        return DailySummary(day, state.completedJobs.count { it.completedAt.millis in start until end }, revenue, costs)
    }

    private fun unlockRegions(state: GameState): GameState {
        val unlocked = catalog.regions.filter { state.progression.completedJobs >= it.minimumCompletedJobs && state.vehicles.count { vehicle -> vehicle.ownership == Ownership.OWNED } >= it.minimumOwnedVehicles && state.gameDay >= it.minimumGameDay && state.distinctOwnedVehicleSpecs() >= it.minimumDistinctVehicleSpecs }.map { it.id }.toSet()
        return state.copy(progression = state.progression.copy(unlockedRegionIds = state.progression.unlockedRegionIds + unlocked))
    }

    private fun ledger(state: GameState, type: LedgerType, amount: Money, jobId: JobId? = null, vehicleId: VehicleId? = null, driverId: DriverId? = null): LedgerEntry = LedgerEntry(LedgerEntryId("ledger-${state.nextEntitySequence}"), state.gameTime, type, amount, jobId, vehicleId, driverId)
    private fun markProcessed(state: GameState, id: CommandId): GameState = state.copy(processedCommandIds = bounded(state.processedCommandIds + id))
    private fun <T> bounded(values: List<T>): List<T> = if (values.size <= catalog.economy.historyLimit) values else values.takeLast(catalog.economy.historyLimit)

    private fun pick(seed: Long, counter: Long, bound: Int): Int = Math.floorMod(mix(seed + counter), bound.toLong()).toInt()
    private fun stablePositive(seed: Long, counter: Long): Long = mix(seed + counter) and Long.MAX_VALUE
    private fun mix(value: Long): Long {
        var x = value
        x = (x xor (x ushr 30)) * -4658895280553007687L
        x = (x xor (x ushr 27)) * -7723592293110705685L
        return x xor (x ushr 31)
    }

    companion object {
        const val MINUTE_MILLIS = 60_000L
        const val DAY_MILLIS = 24L * 60L * MINUTE_MILLIS
    }
}
