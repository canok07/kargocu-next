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
    data class ArithmeticFailure(val operation: String) : Rejection
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
        return try {
            when (command) {
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
            }
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
            if (nextLevel != null) add(NextTargetProjection("company-level-${nextLevel.level}", state.progression.completedJobs.toLong(), nextLevel.minimumCompletedJobs.toLong()))
            if (nextVehicle != null) add(NextTargetProjection("vehicle-${nextVehicle.id.value}", state.money.cents, nextVehicle.purchasePrice.cents))
            catalog.regions.firstOrNull { it.id !in state.progression.unlockedRegionIds }?.let { add(NextTargetProjection("region-${it.id.value}", state.progression.completedJobs.toLong(), it.minimumCompletedJobs.toLong())) }
            if (owned == 0L) add(NextTargetProjection("first-owned-vehicle", 0, 1))
        }
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
        val entry = ledger(state, LedgerType.REPAIR, Money(-cost.cents), vehicleId = vehicle.id)
        val next = markProcessed(state.copy(money = state.money - cost, nextEntitySequence = state.nextEntitySequence + 1, vehicles = state.vehicles.map { if (it.id == vehicle.id) it.copy(conditionPercent = 100, totalOperatingCosts = it.totalOperatingCosts + cost) else it }, ledger = bounded(state.ledger + entry)), command.commandId)
        return GameResult.Applied(next, listOf(GameEvent.LedgerBooked(entry), GameEvent.VehicleRepaired(vehicle.id, cost)))
    }

    private fun maintainVehicle(state: GameState, command: GameCommand.MaintainVehicle): GameResult {
        val vehicle = state.vehicles.firstOrNull { it.id == command.vehicleId } ?: return GameResult.Rejected(state, Rejection.NotFound("vehicle", command.vehicleId.value))
        if (vehicle.status == VehicleStatus.BUSY || state.activeJobs.any { it.vehicleId == vehicle.id }) return GameResult.Rejected(state, Rejection.VehicleBusy(vehicle.id))
        if (vehicle.mileageMeters - vehicle.maintainedAtMeters < catalog.economy.maintenanceIntervalMeters) return GameResult.Rejected(state, Rejection.MaintenanceNotDue(vehicle.id))
        val spec = catalog.vehicles.first { it.id == vehicle.specId }
        val cost = spec.purchasePrice.percentage(catalog.economy.maintenanceCostBasisPoints)
        if (state.money < cost) return GameResult.Rejected(state, Rejection.InsufficientFunds(cost, state.money))
        val entry = ledger(state, LedgerType.MAINTENANCE, Money(-cost.cents), vehicleId = vehicle.id)
        val next = markProcessed(state.copy(money = state.money - cost, nextEntitySequence = state.nextEntitySequence + 1, vehicles = state.vehicles.map { if (it.id == vehicle.id) it.copy(conditionPercent = minOf(100, it.conditionPercent + 15), maintainedAtMeters = it.mileageMeters, totalOperatingCosts = it.totalOperatingCosts + cost) else it }, ledger = bounded(state.ledger + entry)), command.commandId)
        return GameResult.Applied(next, listOf(GameEvent.LedgerBooked(entry), GameEvent.VehicleMaintained(vehicle.id, cost)))
    }

    private fun generateOffers(state: GameState, commandId: CommandId): GameResult {
        var counter = state.randomCounter
        val unlockedLocations = catalog.locations.filter { it.regionId in state.progression.unlockedRegionIds }.map { it.id }.toSet()
        val routes = catalog.routes.filter { it.originId in unlockedLocations && it.destinationId in unlockedLocations }
        if (routes.isEmpty()) return GameResult.Rejected(state, Rejection.NotFound("route", "unlocked"))
        val offers = List(catalog.economy.offersPerDay) { index ->
            val route = routes[pick(state.randomSeed, counter++, routes.size)]
            val packageType = catalog.packageTypes[pick(state.randomSeed, counter++, catalog.packageTypes.size)]
            val risk = catalog.risks[pick(state.randomSeed, counter++, catalog.risks.size)]
            val count = 1 + pick(state.randomSeed, counter++, 6)
            val unitWeightRange = (packageType.maxWeightGrams - packageType.minWeightGrams + 1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            val unitWeight = packageType.minWeightGrams + pick(state.randomSeed, counter++, unitWeightRange)
            JobOffer(
                OfferId("offer-${state.gameDay}-${index + 1}-${stablePositive(state.randomSeed, counter++)}"),
                packageType.id,
                count,
                Math.multiplyExact(unitWeight, count.toLong()),
                route.originId,
                route.destinationId,
                listOf(route.id),
                packageType.capabilities + route.requiredCapabilities,
                risk.id,
                state.gameTime.plusMillis(Math.multiplyExact(catalog.economy.offerLifetimeGameMinutes.toLong(), MINUTE_MILLIS)),
            )
        }.sortedBy { it.id.value }
        val next = markProcessed(state.copy(offers = offers, randomCounter = counter), commandId)
        return GameResult.Applied(next, listOf(GameEvent.OffersGenerated(state.gameDay, offers.size)))
    }

    private fun acceptJob(state: GameState, command: GameCommand.AcceptJob): GameResult {
        val offer = state.offers.firstOrNull { it.id == command.offerId } ?: return GameResult.Rejected(state, Rejection.NotFound("offer", command.offerId.value))
        if (offer.expiresAt <= state.gameTime) return GameResult.Rejected(state, Rejection.OfferExpired(offer.id))
        val vehicle = state.vehicles.firstOrNull { it.id == command.vehicleId } ?: return GameResult.Rejected(state, Rejection.NotFound("vehicle", command.vehicleId.value))
        if (vehicle.status != VehicleStatus.AVAILABLE || state.activeJobs.any { it.vehicleId == vehicle.id }) return GameResult.Rejected(state, Rejection.VehicleUnavailable(vehicle.id))
        if (command.manualDriving && state.activeJobs.any { it.manualDriving }) return GameResult.Rejected(state, Rejection.ManualDriverBusy)
        val spec = catalog.vehicles.first { it.id == vehicle.specId }
        if (offer.count > spec.capacityCount || offer.totalWeightGrams > spec.capacityGrams) return GameResult.Rejected(state, Rejection.CapacityExceeded(spec.capacityCount, offer.count, spec.capacityGrams, offer.totalWeightGrams))
        val missing = offer.requiredCapabilities - spec.capabilities
        if (missing.isNotEmpty()) return GameResult.Rejected(state, Rejection.MissingCapability(missing))
        val route = catalog.routes.firstOrNull { it.id == command.routeId && it.id in offer.routeOptions } ?: return GameResult.Rejected(state, Rejection.RouteUnavailable(command.routeId))
        val destinationRegion = catalog.locations.first { it.id == route.destinationId }.regionId
        if (destinationRegion !in state.progression.unlockedRegionIds) return GameResult.Rejected(state, Rejection.RegionLocked(destinationRegion))
        val invoice = quote(offer, route.id)
        val reservedOperatingCosts = spec.dailyUpkeep
        val reservation = invoice.maximumReservation + reservedOperatingCosts
        if (state.money < reservation) return GameResult.Rejected(state, Rejection.InsufficientFunds(reservation, state.money))
        val jobId = JobId("job-${state.nextEntitySequence}")
        val riskRoll = pick(state.randomSeed, state.randomCounter, 1_000_000)
        val duration = Math.multiplyExact(route.durationGameMinutes.toLong(), MINUTE_MILLIS)
        val risk = catalog.risks.first { it.id == offer.riskId }
        val job = ActiveJob(jobId, offer.id, vehicle.id, vehicle.assignedDriverId, command.manualDriving, route.id, risk.id, risk.chancePerMillion, state.gameTime, state.gameTime.plusMillis(duration), state.gameTime.plusMillis(duration), invoice, reservation, reservedOperatingCosts, distanceMeters = route.distanceMeters, riskRollPerMillion = riskRoll)
        val entry = ledger(state, LedgerType.RESERVATION, Money(-reservation.cents), jobId = jobId, vehicleId = vehicle.id)
        val next = state.copy(
            money = state.money - reservation,
            nextEntitySequence = Math.addExact(state.nextEntitySequence, 1),
            randomCounter = Math.addExact(state.randomCounter, 1),
            vehicles = state.vehicles.map { if (it.id == vehicle.id) it.copy(status = VehicleStatus.BUSY) else it },
            offers = state.offers.filterNot { it.id == offer.id },
            activeJobs = state.activeJobs + job,
            ledger = bounded(state.ledger + entry),
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
        events += GameEvent.TimeAdvanced(from, target)
        return GameResult.Applied(markProcessed(state, commandId), events)
    }

    private fun settle(state: GameState, job: ActiveJob): Pair<GameState, List<GameEvent>> {
        val risk = catalog.risks.first { it.id == job.riskId }
        val riskOccurred = job.riskRollPerMillion < job.riskChancePerMillion
        val penalty = if (riskOccurred) job.invoice.maximumRevenue.percentage(risk.penaltyBasisPoints).let { minOf(it, job.invoice.reservedPenalty) } else Money.ZERO
        val actualFuel = Money(Math.multiplyExact(job.distanceMeters / 1_000L, catalog.vehicles.first { it.id == state.vehicles.first { vehicle -> vehicle.id == job.vehicleId }.specId }.fuelCentsPerKilometer))
        val rental = if (state.vehicles.first { it.id == job.vehicleId }.ownership == Ownership.RENTAL) job.invoice.reservedRental else Money.ZERO
        val spec = catalog.vehicles.first { it.id == state.vehicles.first { vehicle -> vehicle.id == job.vehicleId }.specId }
        val workedDays = ((job.completionAt.millis - job.startedAt.millis) / DAY_MILLIS + 1L).coerceAtLeast(1L)
        val upkeep = spec.dailyUpkeep * workedDays
        val actualCosts = actualFuel + rental + penalty + upkeep
        val refund = job.reserved
        val revenue = job.invoice.maximumRevenue
        val result = JobResult(job.id, job.completionAt, job.completionAt <= job.dueAt, riskOccurred, revenue, actualCosts, revenue - actualCosts)
        var sequence = state.nextEntitySequence
        val entries = buildList {
            add(LedgerEntry(LedgerEntryId("ledger-${sequence++}"), job.completionAt, LedgerType.RESERVATION_REFUND, job.reserved, job.id, job.vehicleId))
            if (actualFuel.cents > 0) add(LedgerEntry(LedgerEntryId("ledger-${sequence++}"), job.completionAt, LedgerType.FUEL, Money(-actualFuel.cents), job.id, job.vehicleId))
            if (rental.cents > 0) add(LedgerEntry(LedgerEntryId("ledger-${sequence++}"), job.completionAt, LedgerType.RENTAL, Money(-rental.cents), job.id, job.vehicleId))
            if (penalty.cents > 0) add(LedgerEntry(LedgerEntryId("ledger-${sequence++}"), job.completionAt, LedgerType.PENALTY, Money(-penalty.cents), job.id, job.vehicleId))
            if (upkeep.cents > 0) add(LedgerEntry(LedgerEntryId("ledger-${sequence++}"), job.completionAt, LedgerType.VEHICLE_UPKEEP, Money(-upkeep.cents), job.id, job.vehicleId))
            add(LedgerEntry(LedgerEntryId("ledger-${sequence++}"), job.completionAt, LedgerType.REVENUE, revenue, job.id, job.vehicleId))
        }
        val vehicle = state.vehicles.first { it.id == job.vehicleId }
        val next = state.copy(
            money = state.money + refund + revenue - actualCosts,
            nextEntitySequence = sequence,
            vehicles = state.vehicles.map { if (it.id == job.vehicleId) it.copy(status = VehicleStatus.AVAILABLE, conditionPercent = (it.conditionPercent - maxOf(1, Math.toIntExact(job.distanceMeters / 200_000L))).coerceAtLeast(1), mileageMeters = Math.addExact(it.mileageMeters, job.distanceMeters), totalOperatingCosts = it.totalOperatingCosts + actualCosts, totalEarned = it.totalEarned + result.net) else it },
            activeJobs = state.activeJobs.filterNot { it.id == job.id },
            completedJobs = bounded(state.completedJobs + result),
            ledger = bounded(state.ledger + entries),
            progression = state.progression.copy(completedJobs = Math.addExact(state.progression.completedJobs, 1), onTimeJobs = state.progression.onTimeJobs + if (result.onTime) 1 else 0),
            tutorial = if (state.tutorial.step == TutorialStep.ACCEPT_FIRST_JOB || state.tutorial.step == TutorialStep.COMPLETE_FIRST_JOB) state.tutorial.copy(step = TutorialStep.BUY_FIRST_VEHICLE) else state.tutorial,
        )
        return next to (entries.map { GameEvent.LedgerBooked(it) } + GameEvent.JobCompleted(job.id, result))
    }

    private fun quote(offer: JobOffer, routeId: RouteId): JobInvoice {
        val packageType = catalog.packageTypes.first { it.id == offer.packageTypeId }
        val route = catalog.routes.first { it.id == routeId }
        val risk = catalog.risks.first { it.id == offer.riskId }
        val distance = Money(Math.multiplyExact(route.distanceMeters / 1_000L, catalog.economy.distanceRewardCentsPerKilometer))
        val base = packageType.baseReward + Money(Math.multiplyExact(offer.count.toLong(), 150L))
        val riskBonus = (base + distance).percentage(risk.rewardBasisPoints)
        val fuel = Money(Math.multiplyExact(route.distanceMeters / 1_000L, catalog.vehicles.maxOf { it.fuelCentsPerKilometer }))
        val rental = Money(catalog.economy.rentalCentsPerJob)
        val penalty = (base + distance + riskBonus).percentage(catalog.economy.maximumPenaltyBasisPoints)
        return JobInvoice(base, distance, riskBonus, fuel, rental, penalty)
    }

    private fun summarizeDay(state: GameState, day: Int): DailySummary {
        val start = (day - 1L) * DAY_MILLIS
        val end = day.toLong() * DAY_MILLIS
        val entries = state.ledger.filter { it.at.millis in start until end }
        val revenue = entries.filter { it.amount.cents > 0 }.fold(Money.ZERO) { sum, it -> sum + it.amount }
        val costs = entries.filter { it.amount.cents < 0 }.fold(Money.ZERO) { sum, it -> sum + Money(-it.amount.cents) }
        return DailySummary(day, state.completedJobs.count { it.completedAt.millis in start until end }, revenue, costs)
    }

    private fun unlockRegions(state: GameState): GameState {
        val unlocked = catalog.regions.filter { state.progression.completedJobs >= it.minimumCompletedJobs && state.vehicles.count { vehicle -> vehicle.ownership == Ownership.OWNED } >= it.minimumOwnedVehicles && state.gameDay >= it.minimumGameDay }.map { it.id }.toSet()
        return state.copy(progression = state.progression.copy(unlockedRegionIds = state.progression.unlockedRegionIds + unlocked))
    }

    private fun ledger(state: GameState, type: LedgerType, amount: Money, jobId: JobId? = null, vehicleId: VehicleId? = null): LedgerEntry = LedgerEntry(LedgerEntryId("ledger-${state.nextEntitySequence}"), state.gameTime, type, amount, jobId, vehicleId)
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
