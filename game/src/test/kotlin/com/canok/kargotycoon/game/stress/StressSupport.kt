package com.canok.kargotycoon.game.stress

import com.canok.kargotycoon.game.domain.*
import com.canok.kargotycoon.game.engine.*
import kotlinx.serialization.json.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.io.File
import java.security.MessageDigest

/** Marker for the opt-in `:game:stressTest` campaign; excluded from routine `:game:test`. */
interface StressTest

internal fun intProp(name: String, default: Int): Int = System.getProperty(name)?.toIntOrNull() ?: default
internal fun doubleProp(name: String, default: Double): Double = System.getProperty(name)?.toDoubleOrNull() ?: default

internal fun GameCommand.kind(): String = this::class.simpleName ?: "GameCommand"

/** Small deterministic SplitMix-style generator; never the engine's own RNG. */
internal class StressRandom(seed: Long) {
    private var state = seed * -7046029254386353131L + 0x2545F4914F6CDD1DL

    fun nextLong(): Long {
        state += -7046029254386353131L
        var z = state
        z = (z xor (z ushr 30)) * -4658895280553007687L
        z = (z xor (z ushr 27)) * -7723592293110705685L
        return z xor (z ushr 31)
    }

    fun nextInt(bound: Int): Int {
        require(bound > 0)
        return Math.floorMod(nextLong(), bound.toLong()).toInt()
    }

    fun nextDouble(): Double = (nextLong() ushr 11).toDouble() / (1L shl 53).toDouble()
    fun chance(probability: Double): Boolean = nextDouble() < probability
}

/** Emits both a stdout line and a JSON file under the stress metrics directory. */
internal class StressMetrics(private val name: String) {
    private val entries = LinkedHashMap<String, String>()

    fun number(key: String, value: Number) { entries[key] = value.toString() }
    fun boolean(key: String, value: Boolean) { entries[key] = value.toString() }
    fun text(key: String, value: String) {
        entries[key] = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
    }

    fun counters(prefix: String, values: Map<String, Long>) {
        values.toSortedMap().forEach { (key, value) -> entries["$prefix.$key"] = value.toString() }
    }

    fun emit() {
        val json = entries.entries.joinToString(",", "{", "}") { (key, value) -> "\"$key\":$value" }
        println("STRESS_METRICS $name $json")
        val directory = File(System.getProperty("stress.metrics.dir") ?: "build/stress-metrics")
        runCatching {
            directory.mkdirs()
            File(directory, "$name.json").writeText(json)
        }
    }
}

/** Counts committed transitions by command kind and by rejection reason. */
internal class Coverage {
    val appliedByCommand = LinkedHashMap<String, Long>()
    val rejectedByCommand = LinkedHashMap<String, Long>()
    val rejectedByReason = LinkedHashMap<String, Long>()
    var transitions = 0L
    var applied = 0L
    var rejected = 0L

    fun onApplied(command: String) {
        transitions++
        applied++
        appliedByCommand.merge(command, 1L) { a, b -> a + b }
    }

    fun onRejected(command: String, reason: Rejection) {
        transitions++
        rejected++
        rejectedByCommand.merge(command, 1L) { a, b -> a + b }
        rejectedByReason.merge(reason::class.simpleName ?: "Rejection", 1L) { a, b -> a + b }
    }
}

/**
 * Independent accounting oracle. It rebuilds the full ledger history by capturing
 * every entry the engine ever appends (even after the bounded ledger truncates),
 * then asserts the global identity money == initialMoney + sum(all ledger entries).
 */
internal class LedgerOracle(private val initialMoneyCents: Long) {
    private val seen = HashSet<String>()
    private var sum = 0L
    var appendedEntries = 0L
        private set

    fun record(after: GameState): List<LedgerEntry> {
        val fresh = ArrayList<LedgerEntry>()
        for (entry in after.ledger) {
            if (seen.add(entry.id.value)) {
                sum = Math.addExact(sum, entry.amount.cents)
                appendedEntries++
                fresh += entry
            }
        }
        return fresh
    }

    fun assertConserved(state: GameState) {
        assertEquals(
            "ledger conservation broken at gameTime=${state.gameTime.millis} revision=${state.revision}",
            initialMoneyCents,
            Math.subtractExact(state.money.cents, sum),
        )
    }
}

/** Asserts a worked vehicle/driver calendar day is charged exactly once, independent of the ledger list. */
internal class DayFeeOracle(private val catalog: GameCatalog) {
    fun check(before: GameState, after: GameState, fresh: List<LedgerEntry>) {
        val previousVehicles = before.vehicles.associateBy { it.id }
        for (vehicle in after.vehicles) {
            val previous = previousVehicles[vehicle.id] ?: continue
            val newlyChargedDays = vehicle.upkeepChargedGameDays - previous.upkeepChargedGameDays
            val spec = catalog.vehicles.first { it.id == vehicle.specId }
            val booked = fresh.filter { it.type == LedgerType.VEHICLE_UPKEEP && it.vehicleId == vehicle.id }.sumOf { -it.amount.cents }
            assertEquals(
                "vehicle ${vehicle.id} upkeep must equal newly worked days x daily upkeep",
                newlyChargedDays.size.toLong() * spec.dailyUpkeep.cents,
                booked,
            )
        }
        val previousDrivers = before.drivers.associateBy { it.id }
        for (driver in after.drivers) {
            val previous = previousDrivers[driver.id] ?: continue
            val newlyChargedDays = driver.wageChargedGameDays - previous.wageChargedGameDays
            val tier = catalog.driverTiers.first { it.id == driver.tierId }
            val booked = fresh.filter { it.type == LedgerType.DRIVER_WAGE && it.driverId == driver.id }.sumOf { -it.amount.cents }
            assertEquals(
                "driver ${driver.id} wage must equal newly worked days x daily wage",
                newlyChargedDays.size.toLong() * tier.dailyWage.cents,
                booked,
            )
        }
    }
}

/** Structural invariants that must hold after every committed transition. */
internal fun assertStructural(state: GameState, catalog: GameCatalog) {
    val issues = GameStateValidator.validate(state, catalog)
    assertTrue("state validator must be clean: $issues", issues.isEmpty())
    assertEquals("vehicle ids unique", state.vehicles.size, state.vehicles.map { it.id }.toSet().size)
    assertEquals("driver ids unique", state.drivers.size, state.drivers.map { it.id }.toSet().size)
    assertEquals("completed job ids unique", state.completedJobs.size, state.completedJobs.map { it.jobId }.toSet().size)
    assertTrue("one active job per vehicle", state.activeJobs.groupingBy { it.vehicleId }.eachCount().values.all { it == 1 })
    assertTrue("at most one player-driven job", state.activeJobs.count { it.manualDriving } <= 1)
    assertEquals(
        "driver assigned to at most one vehicle",
        state.drivers.count { it.assignedVehicleId != null },
        state.drivers.mapNotNull { it.assignedVehicleId }.toSet().size,
    )
    assertEquals(
        "vehicle has at most one driver",
        state.vehicles.count { it.assignedDriverId != null },
        state.vehicles.mapNotNull { it.assignedDriverId }.toSet().size,
    )
    assertTrue("cash never negative", state.money.cents >= 0)
    val limit = catalog.economy.historyLimit
    assertTrue("ledger bounded", state.ledger.size <= limit)
    assertTrue("completed jobs bounded", state.completedJobs.size <= limit)
    assertTrue("daily summaries bounded", state.dailySummaries.size <= limit)
    assertTrue("processed commands bounded", state.processedCommandIds.size <= limit)
}

/**
 * Builds save envelopes with recomputed checksums, so the decoder's *state*
 * validation can be probed independently of checksum validation.
 */
internal object StressEnvelope {
    val json = Json { encodeDefaults = true; explicitNulls = false; ignoreUnknownKeys = false; isLenient = false }

    fun payloadOf(state: GameState): JsonObject = json.encodeToJsonElement(GameState.serializer(), state).jsonObject

    fun checksum(payload: JsonObject): String {
        val bytes = json.encodeToString(JsonObject.serializer(), payload).encodeToByteArray()
        return MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }

    fun encode(
        state: GameState,
        saveVersion: Int = 2,
        payload: JsonObject = payloadOf(state),
        revision: Long = state.revision,
        checksum: String = checksum(payload),
    ): ByteArray {
        val fields = linkedMapOf<String, JsonElement>(
            "saveVersion" to JsonPrimitive(saveVersion),
            "stateVersion" to JsonPrimitive(state.stateVersion),
            "catalogVersion" to JsonPrimitive(state.catalogVersion),
        )
        if (saveVersion >= 2) fields["revision"] = JsonPrimitive(revision)
        fields["checksum"] = JsonPrimitive(checksum)
        fields["payload"] = payload
        return JsonObject(fields).toString().encodeToByteArray()
    }
}

/** Deterministic greedy career builder used to craft rich, valid save states. */
internal object StressCareers {
    fun greedyRun(engine: GameEngine, catalog: GameCatalog, seed: Long, days: Int): GameState {
        var state = newGame(catalog, seed)

        fun apply(command: GameCommand): GameState {
            val result = engine.reduce(state, command)
            if (result is GameResult.Applied) state = result.state
            return state
        }

        repeat(days) { day ->
            apply(GameCommand.GenerateDailyOffers(CommandId("g-$seed-$day")))
            var guard = 0
            while (guard++ < 10) {
                val level = state.companyLevel(catalog)
                if (state.vehicles.count { it.ownership == Ownership.OWNED } < 3) {
                    val spec = catalog.vehicles.filterNot { it.rental }
                        .filter { it.minimumCompanyLevel <= level && state.money >= it.purchasePrice + Money.euros(200) }
                        .minByOrNull { it.purchasePrice.cents }
                    if (spec != null) {
                        apply(GameCommand.PurchaseVehicle(CommandId("b-$seed-$day-$guard"), spec.id))
                        continue
                    }
                }
                val idleVehicle = state.vehicles.firstOrNull { it.ownership == Ownership.OWNED && it.assignedDriverId == null && it.status == VehicleStatus.AVAILABLE }
                val idleDriver = state.drivers.firstOrNull { it.assignedVehicleId == null && it.status == DriverStatus.AVAILABLE }
                if (idleVehicle != null && idleDriver == null && state.drivers.size < state.vehicles.size) {
                    val tier = catalog.driverTiers
                        .filter { it.minimumCompanyLevel <= level && state.money >= it.hiringCost + Money.euros(200) }
                        .minByOrNull { it.hiringCost.cents }
                    if (tier != null) {
                        apply(GameCommand.HireDriver(CommandId("h-$seed-$day-$guard"), tier.id, "G$guard"))
                        continue
                    }
                }
                if (idleVehicle != null && idleDriver != null) {
                    apply(GameCommand.AssignDriver(CommandId("a-$seed-$day-$guard"), idleDriver.id, idleVehicle.id))
                    continue
                }
                break
            }
            var progressed = true
            var round = 0
            while (progressed && round++ < 50) {
                progressed = false
                for (vehicle in state.vehicles.filter { it.status == VehicleStatus.AVAILABLE }) {
                    val manual = vehicle.assignedDriverId == null
                    if (!manual || state.activeJobs.none { it.manualDriving }) {
                        val accepted = acceptFirst(engine, state, vehicle.id, manual, seed, day, round)
                        if (accepted != null) {
                            state = accepted
                            progressed = true
                        }
                    }
                }
                if (state.activeJobs.isNotEmpty()) {
                    val delta = state.activeJobs.minOf { it.completionAt.millis } - state.gameTime.millis
                    if (delta > 0) apply(GameCommand.AdvanceTime(CommandId("f-$seed-$day-$round"), delta))
                }
            }
            state.vehicles
                .filter { it.conditionPercent < 100 && it.status == VehicleStatus.AVAILABLE }
                .forEachIndexed { index, vehicle -> apply(GameCommand.RepairVehicle(CommandId("r-$seed-$day-$index"), vehicle.id)) }
            apply(GameCommand.AdvanceDay(CommandId("d-$seed-$day")))
        }
        return state
    }

    private fun acceptFirst(engine: GameEngine, state: GameState, vehicleId: VehicleId, manual: Boolean, seed: Long, day: Int, round: Int): GameState? {
        for ((index, offer) in state.offers.withIndex()) {
            for (route in offer.routeOptions) {
                val command = GameCommand.AcceptJob(CommandId("ac-$seed-$day-$round-$index-${route.value}"), offer.id, vehicleId, route, manual)
                val result = engine.reduce(state, command)
                if (result is GameResult.Applied) return result.state
            }
        }
        return null
    }
}
