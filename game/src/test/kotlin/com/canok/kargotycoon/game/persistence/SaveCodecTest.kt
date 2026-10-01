package com.canok.kargotycoon.game.persistence

import com.canok.kargotycoon.game.domain.*
import com.canok.kargotycoon.game.engine.StateIssue
import kotlinx.serialization.json.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

class SaveCodecTest {
    private val codec = SaveCodec()
    private val json = Json { encodeDefaults = true; explicitNulls = false }

    @Test
    fun roundTripPreservesAllRelevantStateIncludingActiveDelivery() {
        val state = completeState()
        val decoded = codec.decode(codec.encode(state)) as SaveDecodeResult.Success
        assertEquals(state, decoded.state)
        assertEquals(null, decoded.migratedFromVersion)
    }

    @Test
    fun checksumAndTruncationAreRejected() {
        val bytes = codec.encode(completeState())
        val root = json.parseToJsonElement(bytes.decodeToString()).jsonObject
        val payload = root["payload"]!!.jsonObject
        val changed = JsonObject(payload + ("money" to JsonPrimitive(999_999L)))
        val tampered = JsonObject(root + ("payload" to changed)).toString().encodeToByteArray()
        assertTrue((codec.decode(tampered) as SaveDecodeResult.Corrupt).reason is CorruptionReason.ChecksumMismatch)
        assertTrue((codec.decode(bytes.copyOf(bytes.size / 2)) as SaveDecodeResult.Corrupt).reason is CorruptionReason.InvalidJson)
    }

    @Test
    fun missingAndInvalidTypesAreRejected() {
        val root = json.parseToJsonElement(codec.encode(completeState()).decodeToString()).jsonObject
        val missing = JsonObject(root - "checksum").toString().encodeToByteArray()
        assertEquals(CorruptionReason.MissingField("checksum"), (codec.decode(missing) as SaveDecodeResult.Corrupt).reason)
        val wrongType = JsonObject(root + ("revision" to JsonPrimitive("12"))).toString().encodeToByteArray()
        assertEquals(CorruptionReason.InvalidType("revision"), (codec.decode(wrongType) as SaveDecodeResult.Corrupt).reason)
        val unknownField = JsonObject(root + ("extra" to JsonPrimitive(true))).toString().encodeToByteArray()
        assertEquals(CorruptionReason.InvalidJson, (codec.decode(unknownField) as SaveDecodeResult.Corrupt).reason)
        assertEquals(CorruptionReason.InvalidUtf8, (codec.decode(byteArrayOf(0xC3.toByte(), 0x28)) as SaveDecodeResult.Corrupt).reason)
    }

    @Test
    fun danglingReferencesAndDuplicateJobsAreRejectedAfterValidChecksum() {
        val original = completeState()
        val dangling = original.copy(activeJobs = original.activeJobs.map { it.copy(vehicleId = VehicleId("missing")) })
        val danglingResult = codec.decode(encodeUnchecked(dangling)) as SaveDecodeResult.Corrupt
        assertTrue((danglingResult.reason as CorruptionReason.InvalidState).issues.any { it is StateIssue.MissingReference })

        val duplicate = original.copy(activeJobs = original.activeJobs + original.activeJobs.single())
        val duplicateResult = codec.decode(encodeUnchecked(duplicate)) as SaveDecodeResult.Corrupt
        assertTrue((duplicateResult.reason as CorruptionReason.InvalidState).issues.any { it is StateIssue.DuplicateId && it.kind == "activeJob" })

        val invalidOffer = original.copy(offers = original.offers.map { it.copy(packageTypeId = PackageTypeId("missing"), count = 0) })
        val invalidOfferResult = codec.decode(encodeUnchecked(invalidOffer)) as SaveDecodeResult.Corrupt
        val issues = (invalidOfferResult.reason as CorruptionReason.InvalidState).issues
        assertTrue(issues.any { it is StateIssue.MissingReference && it.kind == "offer.package" })
        assertTrue(issues.any { it is StateIssue.InvalidValue && it.field.startsWith("offer:") })
    }

    @Test
    fun metadataMustMatchSupportedStateCatalogAndPayloadRevision() {
        val original = completeState()
        val root = json.parseToJsonElement(codec.encode(original).decodeToString()).jsonObject
        fun changed(field: String, value: Int): ByteArray = JsonObject(root + (field to JsonPrimitive(value))).toString().encodeToByteArray()
        assertEquals(CorruptionReason.MetadataMismatch("stateVersion"), (codec.decode(changed("stateVersion", 2)) as SaveDecodeResult.Corrupt).reason)
        assertEquals(CorruptionReason.MetadataMismatch("catalogVersion"), (codec.decode(changed("catalogVersion", 2)) as SaveDecodeResult.Corrupt).reason)
        assertEquals(CorruptionReason.MetadataMismatch("revision"), (codec.decode(changed("revision", 13)) as SaveDecodeResult.Corrupt).reason)
    }

    @Test
    fun unknownFutureVersionReturnsProtectedRawBytes() {
        val bytes = codec.encode(completeState())
        val root = json.parseToJsonElement(bytes.decodeToString()).jsonObject
        val futureBytes = JsonObject(root + ("saveVersion" to JsonPrimitive(99))).toString().encodeToByteArray()
        val result = codec.decode(futureBytes) as SaveDecodeResult.FutureVersion
        assertEquals(99, result.version)
        assertTrue(futureBytes.contentEquals(result.raw))
    }

    @Test
    fun oversizedAndMissingSavesHaveTypedResults() {
        assertEquals(SaveDecodeResult.Missing, codec.decode(null))
        val smallCodec = SaveCodec(maximumBytes = 10)
        assertEquals(CorruptionReason.TooLarge, (smallCodec.decode(ByteArray(11)) as SaveDecodeResult.Corrupt).reason)
    }

    @Test
    fun goldenVersionOneEnvelopeMigratesToCurrentVersion() {
        val fixture = checkNotNull(javaClass.getResourceAsStream("save-v1.json")).use { it.readBytes() }
        val result = codec.decode(fixture) as SaveDecodeResult.Success
        assertEquals(1, result.migratedFromVersion)
        assertEquals(completeState().copy(revision = 0), result.state)
    }

    @Test
    fun legacyChecksumIsValidatedBeforeMigration() {
        val fixture = checkNotNull(javaClass.getResourceAsStream("save-v1.json")).use { it.readBytes() }
        val tampered = fixture.decodeToString().replaceFirst("\"money\":22222", "\"money\":22223").encodeToByteArray()
        assertEquals(CorruptionReason.ChecksumMismatch, (codec.decode(tampered) as SaveDecodeResult.Corrupt).reason)
    }

    private fun completeState(): GameState {
        val vehicle = VehicleState(VehicleId("vehicle-1"), VehicleSpecId("rental-panelvan"), Ownership.RENTAL, VehicleStatus.BUSY, conditionPercent = 91, mileageMeters = 44_000, maintainedAtMeters = 10_000, totalOperatingCosts = Money(500), totalEarned = Money(5_000))
        val invoice = JobInvoice(Money(8_000), Money(1_000), Money(500), Money(600), Money(500), Money(2_000))
        val active = ActiveJob(JobId("job-7"), OfferId("offer-7"), vehicle.id, null, true, RouteId("ruhr-east"), RiskId("calm"), 20_000, GameInstant(1_000), GameInstant(800_000), GameInstant(700_000), invoice, Money(3_100), Money.ZERO, setOf(1), 34_000, 999_999)
        return GameState(
            catalogVersion = 1,
            revision = 12,
            gameTime = GameInstant(100_000),
            lastRealtimeMillis = 123_456,
            gameDay = 3,
            money = Money(22_222),
            nextEntitySequence = 20,
            randomSeed = 42,
            randomCounter = 8,
            vehicles = listOf(vehicle),
            offers = listOf(JobOffer(OfferId("offer-8"), PackageTypeId("parcel"), 2, 4_000, LocationId("essen-hub"), LocationId("dortmund-market"), listOf(RouteId("ruhr-east")), emptySet(), RiskId("calm"), GameInstant(900_000))),
            activeJobs = listOf(active),
            completedJobs = listOf(JobResult(JobId("job-6"), GameInstant(900), true, false, Money(9_000), Money(1_000), Money(8_000))),
            ledger = listOf(LedgerEntry(LedgerEntryId("ledger-1"), GameInstant(1_000), LedgerType.RESERVATION, Money(-3_100), active.id, vehicle.id)),
            dailySummaries = listOf(DailySummary(1, 1, Money(9_000), Money(1_000))),
            progression = ProgressionState(5, 4, setOf(RegionId("ruhr")), setOf("level-2")),
            tutorial = TutorialState(TutorialStep.COMPLETE_FIRST_JOB),
            settings = GameSettings("en", soundEnabled = false, hapticsEnabled = true),
            processedCommandIds = listOf(CommandId("command-1")),
        )
    }

    private fun encodeUnchecked(state: GameState): ByteArray {
        val payload = json.encodeToJsonElement(GameState.serializer(), state).jsonObject
        return JsonObject(mapOf(
            "saveVersion" to JsonPrimitive(2), "stateVersion" to JsonPrimitive(state.stateVersion), "catalogVersion" to JsonPrimitive(state.catalogVersion),
            "revision" to JsonPrimitive(state.revision), "checksum" to JsonPrimitive(checksum(payload)), "payload" to payload,
        )).toString().encodeToByteArray()
    }

    private fun checksum(payload: JsonObject): String {
        val bytes = json.encodeToString(JsonObject.serializer(), payload).encodeToByteArray()
        return MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
