package com.canok.kargotycoon.game.persistence

import kotlinx.serialization.json.*
import java.security.MessageDigest

sealed interface MigrationResult {
    data class Success(val envelope: JsonObject) : MigrationResult
    data class Unsupported(val version: Int) : MigrationResult
    data object Invalid : MigrationResult
}

class SaveMigrations {
    private val json = Json { encodeDefaults = true; explicitNulls = false }

    fun migrate(envelope: JsonObject, fromVersion: Int, toVersion: Int): MigrationResult {
        if (fromVersion > toVersion || fromVersion < SaveCodec.MINIMUM_SAVE_VERSION) return MigrationResult.Unsupported(fromVersion)
        var current = envelope
        var version = fromVersion
        while (version < toVersion) {
            current = when (version) {
                1 -> migrateOneToTwo(current) ?: return MigrationResult.Invalid
                else -> return MigrationResult.Unsupported(version)
            }
            version++
        }
        return MigrationResult.Success(current)
    }

    private fun migrateOneToTwo(root: JsonObject): JsonObject? {
        val payload = root["payload"] as? JsonObject ?: return null
        val migratedPayload = JsonObject(payload + ("revision" to JsonPrimitive(0L)))
        return JsonObject(root + mapOf(
            "saveVersion" to JsonPrimitive(2),
            "revision" to JsonPrimitive(0L),
            "payload" to migratedPayload,
            "checksum" to JsonPrimitive(checksum(migratedPayload)),
        ))
    }

    private fun checksum(payload: JsonObject): String {
        val bytes = json.encodeToString(JsonObject.serializer(), payload).encodeToByteArray()
        return MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
