package com.canok.kargotycoon.game.persistence

import com.canok.kargotycoon.game.domain.*
import com.canok.kargotycoon.game.engine.GameStateValidator
import com.canok.kargotycoon.game.engine.StateIssue
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.*
import java.nio.charset.CharacterCodingException
import java.security.MessageDigest

@Serializable
data class SaveEnvelope(
    val saveVersion: Int,
    val stateVersion: Int,
    val catalogVersion: Int,
    val revision: Long,
    val checksum: String,
    val payload: JsonObject,
)

sealed interface SaveDecodeResult {
    data class Success(val state: GameState, val migratedFromVersion: Int? = null) : SaveDecodeResult
    data object Missing : SaveDecodeResult
    data class Corrupt(val reason: CorruptionReason) : SaveDecodeResult
    data class FutureVersion(val version: Int, val raw: ByteArray) : SaveDecodeResult
}

sealed interface CorruptionReason {
    data object TooLarge : CorruptionReason
    data object InvalidUtf8 : CorruptionReason
    data object InvalidJson : CorruptionReason
    data class MissingField(val field: String) : CorruptionReason
    data class InvalidType(val field: String) : CorruptionReason
    data class UnsupportedOldVersion(val version: Int) : CorruptionReason
    data object ChecksumMismatch : CorruptionReason
    data class MetadataMismatch(val field: String) : CorruptionReason
    data class InvalidState(val issues: List<StateIssue>) : CorruptionReason
}

class SaveCodec(
    private val catalog: GameCatalog = DefaultCatalog.value,
    private val migrations: SaveMigrations = SaveMigrations(),
    private val maximumBytes: Int = 2 * 1024 * 1024,
) {
    private val json = Json { encodeDefaults = true; explicitNulls = false; ignoreUnknownKeys = false; isLenient = false }

    fun encode(state: GameState): ByteArray {
        require(state.stateVersion == CURRENT_STATE_VERSION)
        require(validateState(state).isEmpty())
        val payload = json.encodeToJsonElement(GameState.serializer(), state).jsonObject
        val envelope = SaveEnvelope(CURRENT_SAVE_VERSION, state.stateVersion, state.catalogVersion, state.revision, checksum(payload), payload)
        return json.encodeToString(SaveEnvelope.serializer(), envelope).encodeToByteArray().also { require(it.size <= maximumBytes) }
    }

    fun decode(bytes: ByteArray?): SaveDecodeResult {
        if (bytes == null) return SaveDecodeResult.Missing
        if (bytes.size > maximumBytes) return SaveDecodeResult.Corrupt(CorruptionReason.TooLarge)
        val text = try { bytes.decodeToString(throwOnInvalidSequence = true) } catch (_: CharacterCodingException) { return SaveDecodeResult.Corrupt(CorruptionReason.InvalidUtf8) }
        val root = try { json.parseToJsonElement(text).jsonObject } catch (_: Exception) { return SaveDecodeResult.Corrupt(CorruptionReason.InvalidJson) } catch (_: StackOverflowError) { return SaveDecodeResult.Corrupt(CorruptionReason.InvalidJson) }
        val saveVersion = root.requiredInt("saveVersion") ?: return fieldFailure(root, "saveVersion")
        if (saveVersion > CURRENT_SAVE_VERSION) return SaveDecodeResult.FutureVersion(saveVersion, bytes.copyOf())
        if (saveVersion < MINIMUM_SAVE_VERSION) return SaveDecodeResult.Corrupt(CorruptionReason.UnsupportedOldVersion(saveVersion))
        validateEnvelope(root, saveVersion)?.let { return SaveDecodeResult.Corrupt(it) }
        val payload = root["payload"] as JsonObject
        val sourceChecksum = (root["checksum"] as JsonPrimitive).content
        if (!sourceChecksum.equals(checksum(payload), ignoreCase = true)) return SaveDecodeResult.Corrupt(CorruptionReason.ChecksumMismatch)
        val migration = migrations.migrate(root, saveVersion, CURRENT_SAVE_VERSION)
        val migrated = when (migration) {
            is MigrationResult.Success -> migration.envelope
            is MigrationResult.Unsupported -> return SaveDecodeResult.Corrupt(CorruptionReason.UnsupportedOldVersion(migration.version))
            is MigrationResult.Invalid -> return SaveDecodeResult.Corrupt(CorruptionReason.InvalidJson)
        }
        return decodeCurrent(migrated, if (saveVersion == CURRENT_SAVE_VERSION) null else saveVersion)
    }

    private fun decodeCurrent(root: JsonObject, migratedFrom: Int?): SaveDecodeResult {
        validateEnvelope(root, CURRENT_SAVE_VERSION)?.let { return SaveDecodeResult.Corrupt(it) }
        val stateVersion = root.requiredInt("stateVersion")!!
        val catalogVersion = root.requiredInt("catalogVersion")!!
        val revision = root.requiredLong("revision")!!
        val checksum = (root["checksum"] as JsonPrimitive).content
        val payload = root["payload"] as JsonObject
        if (!checksum.equals(checksum(payload), ignoreCase = true)) return SaveDecodeResult.Corrupt(CorruptionReason.ChecksumMismatch)
        val state = try { json.decodeFromJsonElement(GameState.serializer(), payload) } catch (_: SerializationException) { return SaveDecodeResult.Corrupt(CorruptionReason.InvalidJson) } catch (_: IllegalArgumentException) { return SaveDecodeResult.Corrupt(CorruptionReason.InvalidJson) } catch (_: StackOverflowError) { return SaveDecodeResult.Corrupt(CorruptionReason.InvalidJson) }
        if (stateVersion != CURRENT_STATE_VERSION || stateVersion != state.stateVersion) return SaveDecodeResult.Corrupt(CorruptionReason.MetadataMismatch("stateVersion"))
        if (catalogVersion != state.catalogVersion || catalogVersion != catalog.version) return SaveDecodeResult.Corrupt(CorruptionReason.MetadataMismatch("catalogVersion"))
        if (revision != state.revision || revision < 0) return SaveDecodeResult.Corrupt(CorruptionReason.MetadataMismatch("revision"))
        val issues = validateState(state)
        if (issues.isNotEmpty()) return SaveDecodeResult.Corrupt(CorruptionReason.InvalidState(issues))
        return SaveDecodeResult.Success(state, migratedFrom)
    }

    private fun validateEnvelope(root: JsonObject, version: Int): CorruptionReason? {
        val fields = if (version == 1) LEGACY_FIELDS else CURRENT_FIELDS
        fields.firstOrNull { it !in root }?.let { return CorruptionReason.MissingField(it) }
        if (root.keys != fields) return CorruptionReason.InvalidJson
        if (root.requiredInt("saveVersion") != version) return CorruptionReason.InvalidType("saveVersion")
        if (root.requiredInt("stateVersion") == null) return CorruptionReason.InvalidType("stateVersion")
        if (root.requiredInt("catalogVersion") == null) return CorruptionReason.InvalidType("catalogVersion")
        if (version == CURRENT_SAVE_VERSION && root.requiredLong("revision") == null) return CorruptionReason.InvalidType("revision")
        val checksum = root["checksum"] as? JsonPrimitive
        if (checksum == null || !checksum.isString || !CHECKSUM.matches(checksum.content)) return CorruptionReason.InvalidType("checksum")
        if (root["payload"] !is JsonObject) return CorruptionReason.InvalidType("payload")
        return null
    }

    private fun validateState(state: GameState): List<StateIssue> = buildList {
        addAll(GameStateValidator.validate(state, catalog))
        val packageIds = catalog.packageTypes.map { it.id }.toSet()
        val locationIds = catalog.locations.map { it.id }.toSet()
        val routeIds = catalog.routes.map { it.id }.toSet()
        val riskIds = catalog.risks.map { it.id }.toSet()
        val regionIds = catalog.regions.map { it.id }.toSet()
        if (state.gameTime.millis < 0 || state.lastRealtimeMillis < 0 || state.money.cents < 0) add(StateIssue.InvalidValue("timeOrMoney"))
        state.offers.forEach { offer ->
            if (offer.packageTypeId !in packageIds) add(StateIssue.MissingReference("offer.package", offer.packageTypeId.value))
            if (offer.originId !in locationIds) add(StateIssue.MissingReference("offer.origin", offer.originId.value))
            if (offer.destinationId !in locationIds) add(StateIssue.MissingReference("offer.destination", offer.destinationId.value))
            offer.routeOptions.filterNot(routeIds::contains).forEach { add(StateIssue.MissingReference("offer.route", it.value)) }
            if (offer.riskId !in riskIds) add(StateIssue.MissingReference("offer.risk", offer.riskId.value))
            if (offer.count <= 0 || offer.totalWeightGrams <= 0 || offer.routeOptions.isEmpty() || offer.expiresAt.millis < 0) add(StateIssue.InvalidValue("offer:${offer.id.value}"))
        }
        state.activeJobs.forEach { job ->
            if (job.routeId !in routeIds) add(StateIssue.MissingReference("job.route", job.routeId.value))
            if (job.riskId !in riskIds) add(StateIssue.MissingReference("job.risk", job.riskId.value))
            if (listOf(job.invoice.baseReward, job.invoice.distanceReward, job.invoice.riskBonus, job.invoice.reservedFuel, job.invoice.reservedRental, job.invoice.reservedPenalty, job.reserved, job.reservedOperatingCosts).any { it.cents < 0 }) add(StateIssue.InvalidValue("jobMoney:${job.id.value}"))
        }
        state.progression.unlockedRegionIds.filterNot(regionIds::contains).forEach { add(StateIssue.MissingReference("progression.region", it.value)) }
        if (state.progression.completedJobs < 0 || state.progression.onTimeJobs !in 0..state.progression.completedJobs) add(StateIssue.InvalidValue("progression"))
        state.completedJobs.forEach { if (it.completedAt.millis < 0 || it.revenue.cents < 0 || it.costs.cents < 0 || it.net != it.revenue - it.costs) add(StateIssue.InvalidValue("completedJob:${it.jobId.value}")) }
        state.dailySummaries.forEach { if (it.gameDay <= 0 || it.completedJobs < 0 || it.revenue.cents < 0 || it.costs.cents < 0) add(StateIssue.InvalidValue("dailySummary:${it.gameDay}")) }
    }

    private fun checksum(payload: JsonObject): String {
        val bytes = json.encodeToString(JsonObject.serializer(), payload).encodeToByteArray()
        return MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }

    private fun fieldFailure(root: JsonObject, field: String): SaveDecodeResult = if (field !in root) SaveDecodeResult.Corrupt(CorruptionReason.MissingField(field)) else SaveDecodeResult.Corrupt(CorruptionReason.InvalidType(field))

    companion object {
        const val CURRENT_SAVE_VERSION = 2
        const val MINIMUM_SAVE_VERSION = 1
        const val CURRENT_STATE_VERSION = 1
        private val CURRENT_FIELDS = setOf("saveVersion", "stateVersion", "catalogVersion", "revision", "checksum", "payload")
        private val LEGACY_FIELDS = CURRENT_FIELDS - "revision"
        private val CHECKSUM = Regex("[0-9a-fA-F]{64}")
    }
}

private fun JsonObject.requiredInt(name: String): Int? = (this[name] as? JsonPrimitive)?.takeUnless(JsonPrimitive::isString)?.intOrNull
private fun JsonObject.requiredLong(name: String): Long? = (this[name] as? JsonPrimitive)?.takeUnless(JsonPrimitive::isString)?.longOrNull
