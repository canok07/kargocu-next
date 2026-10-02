package com.canok.kargotycoon.game.stress

import com.canok.kargotycoon.game.domain.*
import com.canok.kargotycoon.game.engine.*
import com.canok.kargotycoon.game.persistence.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.experimental.categories.Category

/**
 * Adversarial persistence campaign: byte-level fuzzing, semantic JSON mutations,
 * checksum-valid but state-invalid payloads, numeric boundaries, oversize and
 * deeply nested inputs, version boundaries, and valid round-trip/deterministic
 * continuation. The decoder must always return a typed result and never throw.
 */
@Category(StressTest::class)
class PersistenceAdversarialStressTest {
    private val catalog = DefaultCatalog.value
    private val engine = GameEngine(catalog)
    private val codec = SaveCodec(catalog)
    private val json = StressEnvelope.json

    private val activeState: GameState by lazy { buildActiveState() }
    private val richState: GameState by lazy { StressCareers.greedyRun(engine, catalog, 31_337L, 20) }
    private val longState: GameState by lazy { StressCareers.greedyRun(engine, catalog, 777_777L, 120) }
    private val bases: List<ByteArray> by lazy { listOf(codec.encode(activeState), codec.encode(richState), codec.encode(longState)) }

    @Test
    fun byteLevelFuzzingIsTotalTypedAndNeverAcceptsTamperedPayloads() {
        val variants = intProp("stress.persist.byteVariants", 4096)
        val rng = StressRandom(424242L)
        val distribution = LinkedHashMap<String, Long>()
        var accepted = 0L
        for (index in 0 until variants) {
            val original = bases[index % bases.size]
            val mutated = mutateBytes(original, rng, index)
            val result = try {
                codec.decode(mutated)
            } catch (throwable: Throwable) {
                fail("decode threw ${throwable::class.simpleName} for byte variant $index")
                return
            }
            distribution.merge(result::class.simpleName ?: "?", 1L) { a, b -> a + b }
            if (result is SaveDecodeResult.Success) {
                accepted++
                // A mutation may only be accepted when it cannot change meaning: the stored checksum
                // is compared case-insensitively, so flipping hex letter case is a harmless no-op.
                assertEquals("an accepted mutation must decode to the unchanged save", codec.decode(original), result)
            }
        }
        assertTrue("accepted mutations must stay a tiny fraction, got $accepted", accepted <= variants / 50L + 8L)
        assertTrue("majority must be rejected", (distribution["Corrupt"] ?: 0L) + (distribution["FutureVersion"] ?: 0L) >= variants * 9L / 10L)
        StressMetrics("persistence-byte-fuzz").apply {
            number("variants", variants)
            number("acceptedTamperedSaves", accepted)
            counters("result", distribution)
            emit()
        }
    }

    @Test
    fun semanticJsonAndEnvelopeMutationsAreRejectedWithTypedReasons() {
        val root = json.parseToJsonElement(codec.encode(richState).decodeToString()).jsonObject
        fun raw(element: JsonElement): ByteArray = element.toString().encodeToByteArray()

        assertEquals(CorruptionReason.MissingField("checksum"), corruptReason(raw(JsonObject(root - "checksum"))))
        assertEquals(CorruptionReason.MissingField("payload"), corruptReason(raw(JsonObject(root - "payload"))))
        assertEquals(CorruptionReason.MissingField("saveVersion"), corruptReason(raw(JsonObject(root - "saveVersion"))))
        assertEquals(CorruptionReason.InvalidType("saveVersion"), corruptReason(raw(JsonObject(root + ("saveVersion" to JsonPrimitive("2"))))))
        assertEquals(CorruptionReason.InvalidType("revision"), corruptReason(raw(JsonObject(root + ("revision" to JsonPrimitive("12"))))))
        assertEquals(CorruptionReason.InvalidType("checksum"), corruptReason(raw(JsonObject(root + ("checksum" to JsonPrimitive("not-a-hash"))))))
        assertEquals(CorruptionReason.InvalidType("payload"), corruptReason(raw(JsonObject(root + ("payload" to JsonArray(emptyList()))))))
        assertEquals(CorruptionReason.InvalidJson, corruptReason(raw(JsonObject(root + ("unknown" to JsonPrimitive(true))))))
        assertEquals(CorruptionReason.UnsupportedOldVersion(0), corruptReason(raw(JsonObject(root + ("saveVersion" to JsonPrimitive(0))))))
        assertTrue(codec.decode(raw(JsonObject(root + ("saveVersion" to JsonPrimitive(99))))) is SaveDecodeResult.FutureVersion)

        val payload = root["payload"]!!.jsonObject
        val tamperedPayload = JsonObject(payload + ("money" to JsonPrimitive((payload["money"] as JsonPrimitive).long + 1)))
        assertEquals(CorruptionReason.ChecksumMismatch, corruptReason(raw(JsonObject(root + ("payload" to tamperedPayload)))))

        assertEquals(CorruptionReason.MetadataMismatch("stateVersion"), corruptReason(raw(JsonObject(root + ("stateVersion" to JsonPrimitive(2))))))
        assertEquals(CorruptionReason.MetadataMismatch("catalogVersion"), corruptReason(raw(JsonObject(root + ("catalogVersion" to JsonPrimitive(99))))))
        assertEquals(CorruptionReason.MetadataMismatch("revision"), corruptReason(raw(JsonObject(root + ("revision" to JsonPrimitive(-1L))))))
        assertEquals(CorruptionReason.InvalidUtf8, corruptReason(byteArrayOf(0xC3.toByte(), 0x28)))
    }

    @Test
    fun checksumValidButStateInvalidVariantsAreRejected() {
        val variants = intProp("stress.persist.stateVariants", 2048)
        val rng = StressRandom(909090L)
        val kinds = LinkedHashMap<Int, Long>()
        var rejected = 0
        for (index in 0 until variants) {
            val base = listOf(activeState, richState, longState)[index % 3]
            val kind = rng.nextInt(CORRUPTION_KINDS)
            kinds.merge(kind, 1L) { a, b -> a + b }
            val payload = StressEnvelope.payloadOf(corrupt(base, kind, rng))
            val bytes = StressEnvelope.encode(base.copy(money = base.money), payload = payload)
            val result = try {
                codec.decode(bytes)
            } catch (throwable: Throwable) {
                fail("decode threw ${throwable::class.simpleName} for state variant $index kind $kind")
                return
            }
            if (result is SaveDecodeResult.Corrupt) rejected++ else fail("variant kind $kind must be rejected, got $result")
        }
        assertEquals(variants, rejected)
        StressMetrics("persistence-state-variants").apply {
            number("variants", variants)
            number("rejected", rejected)
            counters("kind", kinds.mapKeys { it.key.toString() })
            emit()
        }
    }

    @Test
    fun numericBoundariesAndOverflowPayloadsAreHandledBoundedly() {
        // Negative money is a boundary that must be rejected by state validation.
        val negativeMoney = corruptReason(StressEnvelope.encode(richState.copy(money = Money(-1))))
        assertTrue("negative money must be InvalidState, got $negativeMoney", negativeMoney is CorruptionReason.InvalidState)

        // Huge numeric fields must decode to a typed result (never throw), even if accepted.
        val hugeMoney = try { codec.decode(StressEnvelope.encode(richState.copy(money = Money(Long.MAX_VALUE)))) } catch (t: Throwable) { fail("huge money threw ${t::class}"); return }
        assertTrue(hugeMoney is SaveDecodeResult.Success || hugeMoney is SaveDecodeResult.Corrupt)
        val hugeTime = try { codec.decode(StressEnvelope.encode(richState.copy(gameTime = GameInstant(Long.MAX_VALUE)))) } catch (t: Throwable) { fail("huge time threw ${t::class}"); return }
        assertTrue(hugeTime is SaveDecodeResult.Success || hugeTime is SaveDecodeResult.Corrupt)
        val hugeSequence = try { codec.decode(StressEnvelope.encode(richState.copy(nextEntitySequence = Long.MAX_VALUE))) } catch (t: Throwable) { fail("huge sequence threw ${t::class}"); return }
        assertTrue(hugeSequence is SaveDecodeResult.Success || hugeSequence is SaveDecodeResult.Corrupt)

        // A pathological in-flight job must be rejected by the state validator, not crash.
        if (activeState.activeJobs.isNotEmpty()) {
            val badRisk = activeState.copy(activeJobs = activeState.activeJobs.map { it.copy(riskRollPerMillion = 1_000_000) })
            assertTrue(corruptReason(StressEnvelope.encode(badRisk)) is CorruptionReason.InvalidState)
        }
    }

    @Test
    fun deeplyNestedAndOversizedInputsAreRejectedBoundedly() {
        val nested = ByteArray(200_000) { '['.code.toByte() }
        val nestedResult = try {
            codec.decode(nested)
        } catch (throwable: Throwable) {
            fail("decode must reject deeply nested JSON without throwing, threw ${throwable::class.simpleName}")
            return
        }
        assertTrue("deeply nested input must not decode as a save", nestedResult !is SaveDecodeResult.Success)

        val oversized = ByteArray(2 * 1024 * 1024 + 16) { '0'.code.toByte() }
        assertEquals(CorruptionReason.TooLarge, corruptReason(oversized))
        assertEquals(CorruptionReason.InvalidJson, corruptReason(codec.encode(richState).copyOf(codec.encode(richState).size / 2)))
        assertEquals(SaveDecodeResult.Missing, codec.decode(null))
    }

    @Test
    fun legitimateSavesRoundTripAndContinueDeterministically() {
        for (state in listOf(activeState, richState, longState)) {
            val bytes = codec.encode(state)
            val decoded = (codec.decode(bytes) as SaveDecodeResult.Success).state
            assertEquals(state, decoded)
            assertEquals(bytes.toList(), codec.encode(decoded).toList())

            val commands = listOf(
                GameCommand.GenerateDailyOffers(CommandId("cont-offers")),
                GameCommand.AdvanceTime(CommandId("cont-advance"), 600_000L),
                GameCommand.AdvanceDay(CommandId("cont-day")),
                GameCommand.Resume(CommandId("cont-resume"), state.lastRealtimeMillis + 3_600_000L),
            )
            for (command in commands) {
                val original = engine.reduce(state, command)
                val replayed = engine.reduce(decoded, command)
                assertEquals("continuation diverged for $command", original.state, replayed.state)
                assertEquals(original::class, replayed::class)
            }
        }
    }

    @Test
    fun legacyAndFutureVersionsAreBounded() {
        val legacy = StressEnvelope.encode(
            state = richState,
            saveVersion = 1,
            payload = StressEnvelope.payloadOf(richState).let { JsonObject(it - "revision") },
        )
        val migrated = codec.decode(legacy) as SaveDecodeResult.Success
        assertEquals(1, migrated.migratedFromVersion)

        val future = codec.encode(richState).decodeToString().replaceFirst("\"saveVersion\":2", "\"saveVersion\":42").encodeToByteArray()
        val futureResult = codec.decode(future) as SaveDecodeResult.FutureVersion
        assertEquals(42, futureResult.version)
        assertArrayEquals(future, futureResult.raw)
    }

    @Test
    fun storeOpenNeverPublishesUnverifiedStateAcrossRandomCandidates() = runTest {
        val initial = richState.copy(revision = 7)
        val variants = intProp("stress.persist.storeVariants", 256)
        val rng = StressRandom(5150L)
        for (index in 0 until variants) {
            val repository = CandidateRepository(candidate(rng), candidate(rng))
            val store = GameStore(engine, codec, repository, initial, catalog)
            val result = try {
                store.open()
            } catch (throwable: Throwable) {
                fail("store.open threw ${throwable::class.simpleName} for candidate set $index")
                return@runTest
            }
            assertTrue(
                "unexpected open result $result",
                result is StoreOpenResult.Ready || result is StoreOpenResult.RecoveryRequired ||
                    result is StoreOpenResult.PersistenceFailed || result == StoreOpenResult.Missing,
            )
            assertTrue("store state must always validate: ${GameStateValidator.validate(store.state.value, catalog)}", GameStateValidator.validate(store.state.value, catalog).isEmpty())
        }
    }

    private fun candidate(rng: StressRandom): ByteArray? = when (rng.nextInt(3)) {
        0 -> null
        1 -> bases[rng.nextInt(bases.size)]
        else -> mutateBytes(bases[rng.nextInt(bases.size)], rng, rng.nextInt(1_000_000))
    }

    private fun buildActiveState(): GameState {
        var state = newGame(catalog, 555L)
        state = (engine.reduce(state, GameCommand.GenerateDailyOffers(CommandId("gen"))) as GameResult.Applied).state
        for ((index, offer) in state.offers.withIndex()) {
            for (route in offer.routeOptions) {
                val result = engine.reduce(state, GameCommand.AcceptJob(CommandId("acc-$index-${route.value}"), offer.id, VehicleId("vehicle-1"), route))
                if (result is GameResult.Applied) return result.state
            }
        }
        error("no acceptable offer")
    }

    private fun corruptReason(bytes: ByteArray): CorruptionReason {
        val result = codec.decode(bytes)
        assertTrue("expected Corrupt, got $result", result is SaveDecodeResult.Corrupt)
        return (result as SaveDecodeResult.Corrupt).reason
    }

    private fun mutateBytes(original: ByteArray, rng: StressRandom, index: Int): ByteArray {
        if (original.isEmpty()) return original
        val copy = original.copyOf()
        return when (index % 6) {
            0 -> copy.copyOfRange(0, rng.nextInt(copy.size))
            1 -> { copy[rng.nextInt(copy.size)] = rng.nextInt(256).toByte(); copy }
            2 -> {
                val at = rng.nextInt(copy.size)
                copy.copyOfRange(0, at) + copy.copyOfRange(at + 1, copy.size)
            }
            3 -> {
                val at = rng.nextInt(copy.size)
                copy.copyOfRange(0, at) + byteArrayOf(rng.nextInt(256).toByte()) + copy.copyOfRange(at, copy.size)
            }
            4 -> {
                val at = rng.nextInt(copy.size)
                val length = 1 + rng.nextInt(minOf(8, copy.size - at))
                for (k in at until at + length) copy[k] = 0
                copy
            }
            else -> {
                val at = rng.nextInt(copy.size)
                copy[at] = (copy[at].toInt() xor (1 shl rng.nextInt(8))).toByte()
                copy
            }
        }
    }

    private fun corrupt(state: GameState, kind: Int, rng: StressRandom): GameState = when (kind) {
        0 -> state.copy(money = Money(-1))
        1 -> if (state.vehicles.isNotEmpty()) state.copy(vehicles = state.vehicles.mapIndexed { i, v -> if (i == 0) v.copy(conditionPercent = 0) else v }) else state.copy(money = Money(-1))
        2 -> if (state.vehicles.isNotEmpty()) state.copy(vehicles = state.vehicles.mapIndexed { i, v -> if (i == 0) v.copy(conditionPercent = 101) else v }) else state.copy(money = Money(-1))
        3 -> state.copy(gameDay = 0)
        4 -> state.copy(randomCounter = -1)
        5 -> state.copy(nextEntitySequence = 0)
        6 -> if (state.vehicles.isNotEmpty()) state.copy(vehicles = state.vehicles + state.vehicles.first()) else state.copy(money = Money(-1))
        7 -> if (state.drivers.isNotEmpty()) state.copy(drivers = state.drivers + state.drivers.first()) else state.copy(money = Money(-1))
        8 -> if (state.offers.isNotEmpty()) state.copy(offers = state.offers + state.offers.first()) else state.copy(money = Money(-1))
        9 -> if (state.activeJobs.isNotEmpty()) state.copy(activeJobs = state.activeJobs + state.activeJobs.first()) else state.copy(money = Money(-1))
        10 -> if (state.ledger.isNotEmpty()) state.copy(ledger = state.ledger + state.ledger.first()) else state.copy(money = Money(-1))
        11 -> if (state.activeJobs.isNotEmpty()) state.copy(activeJobs = state.activeJobs.mapIndexed { i, j -> if (i == 0) j.copy(vehicleId = VehicleId("missing")) else j }) else state.copy(money = Money(-1))
        12 -> if (state.activeJobs.isNotEmpty()) state.copy(activeJobs = state.activeJobs.mapIndexed { i, j -> if (i == 0) j.copy(routeId = RouteId("missing")) else j }) else state.copy(money = Money(-1))
        13 -> if (state.offers.isNotEmpty()) state.copy(offers = state.offers.mapIndexed { i, o -> if (i == 0) o.copy(packageTypeId = PackageTypeId("missing")) else o }) else state.copy(money = Money(-1))
        14 -> if (state.offers.isNotEmpty()) state.copy(offers = state.offers.mapIndexed { i, o -> if (i == 0) o.copy(routeOptions = listOf(RouteId("missing"))) else o }) else state.copy(money = Money(-1))
        15 -> state.copy(progression = state.progression.copy(unlockedRegionIds = state.progression.unlockedRegionIds + RegionId("missing")))
        16 -> if (state.completedJobs.isNotEmpty()) state.copy(completedJobs = state.completedJobs + state.completedJobs.first()) else state.copy(money = Money(-1))
        17 -> state.copy(dailySummaries = state.dailySummaries + DailySummary(0, 0, Money.ZERO, Money.ZERO))
        18 -> if (state.vehicles.isNotEmpty()) state.copy(vehicles = state.vehicles.mapIndexed { i, v -> if (i == 0) v.copy(specId = VehicleSpecId("missing")) else v }) else state.copy(money = Money(-1))
        19 -> if (state.drivers.isNotEmpty()) state.copy(drivers = state.drivers.mapIndexed { i, d -> if (i == 0) d.copy(tierId = DriverTierId("missing")) else d }) else state.copy(money = Money(-1))
        20 -> if (state.ledger.isNotEmpty()) state.copy(ledger = state.ledger.mapIndexed { i, e -> if (i == 0) e.copy(amount = Money.ZERO) else e }) else state.copy(money = Money(-1))
        21 -> if (state.completedJobs.isNotEmpty()) state.copy(completedJobs = state.completedJobs.mapIndexed { i, j -> if (i == 0) j.copy(net = j.net + Money(1)) else j }) else state.copy(money = Money(-1))
        22 -> if (state.activeJobs.isNotEmpty()) state.copy(activeJobs = state.activeJobs.mapIndexed { i, j -> if (i == 0) j.copy(reserved = j.reserved + Money(1)) else j }) else state.copy(money = Money(-1))
        23 -> if (state.drivers.isNotEmpty()) state.copy(drivers = state.drivers.mapIndexed { i, d -> if (i == 0) d.copy(assignedVehicleId = VehicleId("missing")) else d }) else state.copy(money = Money(-1))
        24 -> if (state.vehicles.isNotEmpty()) state.copy(vehicles = state.vehicles.mapIndexed { i, v -> if (i == 0) v.copy(assignedDriverId = DriverId("missing")) else v }) else state.copy(money = Money(-1))
        25 -> state.copy(stateVersion = 2)
        26 -> state.copy(catalogVersion = 99)
        else -> state.copy(gameTime = GameInstant(-1))
    }

    private class CandidateRepository(private val primary: ByteArray?, private val backup: ByteArray?) : SaveRepository {
        override suspend fun readCandidates(): SaveCandidates = SaveCandidates(primary, backup)
        override suspend fun write(bytes: ByteArray, revision: Long): SaveWriteResult = SaveWriteResult.Failed("read-only")
        override suspend fun preservePrimary(reason: String): Boolean = false
    }

    private companion object {
        const val CORRUPTION_KINDS = 28
    }
}
