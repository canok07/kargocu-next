package com.canok.kargotycoon.game.career

import com.canok.kargotycoon.game.domain.*
import com.canok.kargotycoon.game.engine.*
import com.canok.kargotycoon.game.persistence.*
import kotlinx.serialization.Serializable

@Serializable
data class CareerFinding(val seed: Long, val skipIdleDays: Boolean, val endDay: Int, val completedJobs: Int,
    val firstOwnedAfterJobs: Int, val firstOwnedDay: Int, val firstDriverDay: Int, val ownedVehicles: Int,
    val distinctOwnedSpecs: Int, val drivers: Int, val regions: Int, val stage: Int, val balanceCents: Long,
    val normalClockMillis: Long, val daySkipMinimumWaitMillis: Long, val commands: Int, val paidJobs: Int,
    val maxParallel: Int, val minimumBalanceCents: Long)

/** Event-driven clock model, not a human playtime prediction. Uses only legal commands. */
fun runCareer(catalog: GameCatalog, seed: Long, skipIdleDays: Boolean): CareerFinding {
    val engine = GameEngine(catalog)
    val codec = SaveCodec(catalog)
    var state = newGame(catalog, seed)
    var sequence = 0
    var firstOwnedJobs = -1
    var firstOwnedDay = -1
    var firstDriverDay = -1
    var waitedGameMillis = 0L
    var maxParallel = 0
    var minimumBalance = state.money.cents
    val paid = mutableSetOf<JobId>()
    val maxLevel = catalog.progression.levels.maxOf { it.level }
    val targetOwned = catalog.progression.levels.maxOf { it.minimumOwnedVehicles }
    val targetTypes = catalog.progression.levels.maxOf { it.minimumDistinctVehicleSpecs }
    fun id() = CommandId("career-${sequence++}")
    fun commit(command: GameCommand): Boolean {
        when (val result = engine.reduce(state, command)) {
            is GameResult.Rejected -> { check(result.state == state); return false }
            is GameResult.Applied -> {
                result.events.filterIsInstance<GameEvent.JobCompleted>().forEach { check(paid.add(it.jobId)) { "Duplicate payout" } }
                state = result.state
                check(GameStateValidator.validate(state, catalog).isEmpty())
                check(state.money.cents >= 0)
                maxParallel = maxOf(maxParallel, state.activeJobs.size)
                minimumBalance = minOf(minimumBalance, state.money.cents)
                return true
            }
        }
    }
    commit(GameCommand.GenerateDailyOffers(id()))
    while (state.gameDay <= 180) {
        if (state.companyLevel(catalog) == maxLevel && state.progression.unlockedRegionIds.containsAll(catalog.regions.map { it.id })) break
        repeat(16) {
            val level = state.companyLevel(catalog)
            val owned = state.vehicles.filter { it.ownership == Ownership.OWNED }
            val missingSpecs = catalog.vehicles.filter { !it.rental && it.minimumCompanyLevel <= level && owned.none { v -> v.specId == it.id } }
            if (owned.size < targetOwned || state.distinctOwnedVehicleSpecs() < targetTypes) {
                val affordable = missingSpecs.filter { state.money >= it.purchasePrice + Money.euros(200) }.minByOrNull { it.purchasePrice.cents }
                if (affordable != null) {
                    if (firstOwnedJobs < 0) { firstOwnedJobs = state.progression.completedJobs; firstOwnedDay = state.gameDay }
                    check(commit(GameCommand.PurchaseVehicle(id(), affordable.id)))
                    return@repeat
                }
                if (missingSpecs.isEmpty() && targetTypes == 0) {
                    val extra = catalog.vehicles.filter { !it.rental && it.minimumCompanyLevel <= level && state.money >= it.purchasePrice + Money.euros(200) }.minByOrNull { it.purchasePrice.cents }
                    if (extra != null) { check(commit(GameCommand.PurchaseVehicle(id(), extra.id))); return@repeat }
                }
            }
            val idleVehicle = state.vehicles.firstOrNull { it.ownership == Ownership.OWNED && it.assignedDriverId == null && it.status == VehicleStatus.AVAILABLE }
            var idleDriver = state.drivers.firstOrNull { it.assignedVehicleId == null && it.status == DriverStatus.AVAILABLE }
            if (idleVehicle != null && idleDriver == null) {
                val tier = catalog.driverTiers.filter { it.minimumCompanyLevel <= level && state.money >= it.hiringCost + Money.euros(200) }.minByOrNull { it.hiringCost.cents }
                if (tier != null) {
                    if (firstDriverDay < 0) firstDriverDay = state.gameDay
                    check(commit(GameCommand.HireDriver(id(), tier.id, "Driver ${state.drivers.size + 1}")))
                    idleDriver = state.drivers.first { it.assignedVehicleId == null && it.status == DriverStatus.AVAILABLE }
                }
            }
            if (idleVehicle != null && idleDriver != null) check(commit(GameCommand.AssignDriver(id(), idleDriver.id, idleVehicle.id)))
        }
        state.vehicles.filter { it.status == VehicleStatus.AVAILABLE }.forEach { vehicle ->
            if (vehicle.conditionPercent < 95) commit(GameCommand.RepairVehicle(id(), vehicle.id))
            if (vehicle.mileageMeters - vehicle.maintainedAtMeters >= catalog.economy.maintenanceIntervalMeters) commit(GameCommand.MaintainVehicle(id(), vehicle.id))
        }
        for (vehicle in state.vehicles.filter { it.status == VehicleStatus.AVAILABLE }) {
            val manual = vehicle.assignedDriverId == null
            if (manual && state.activeJobs.any { it.manualDriving }) continue
            val candidates = state.offers.flatMap { offer -> offer.routeOptions.map { route -> GameCommand.AcceptJob(id(), offer.id, vehicle.id, route, manual) } }
            val best = candidates.mapNotNull { command ->
                val result = engine.reduce(state, command) as? GameResult.Applied ?: return@mapNotNull null
                val job = result.state.activeJobs.last()
                val margin = (job.invoice.maximumRevenue - job.reserved).cents
                if (margin <= 0) null else command to (margin.toDouble() / (job.completionAt.millis - job.startedAt.millis))
            }.maxByOrNull { it.second }
            if (best != null) check(commit(best.first))
        }
        if (state.activeJobs.isNotEmpty()) {
            val delta = state.activeJobs.minOf { it.completionAt.millis } - state.gameTime.millis
            check(delta > 0)
            waitedGameMillis += delta
            check(commit(GameCommand.AdvanceTime(id(), delta)))
        } else {
            val before = state
            val decoded = codec.decode(codec.encode(state)) as SaveDecodeResult.Success
            check(before == decoded.state)
            state = decoded.state
            if (skipIdleDays) check(commit(GameCommand.AdvanceDay(id())))
            else check(commit(GameCommand.AdvanceTime(id(), state.gameDay.toLong() * GameEngine.DAY_MILLIS - state.gameTime.millis)))
        }
    }
    check(state.companyLevel(catalog) == maxLevel && state.progression.unlockedRegionIds.containsAll(catalog.regions.map { it.id })) { "Seed $seed did not reach endgame: day=${state.gameDay} stage=${state.companyLevel(catalog)} jobs=${state.progression.completedJobs} assets=${state.vehicles.size} cash=${state.money}" }
    check(paid.size == state.progression.completedJobs)
    val owned = state.vehicles.filter { it.ownership == Ownership.OWNED }
    return CareerFinding(seed, skipIdleDays, state.gameDay, state.progression.completedJobs, firstOwnedJobs, firstOwnedDay, firstDriverDay,
        owned.size, owned.map { it.specId }.toSet().size, state.drivers.size, state.progression.unlockedRegionIds.size, state.companyLevel(catalog),
        state.money.cents, state.gameTime.millis / 60, waitedGameMillis / 60, sequence, paid.size, maxParallel, minimumBalance)
}
