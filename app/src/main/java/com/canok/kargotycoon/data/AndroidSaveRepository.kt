package com.canok.kargotycoon.data

import android.util.AtomicFile
import com.canok.kargotycoon.game.persistence.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID

/** One application-owned instance. All disk access is serialized off the main thread. */
class AndroidSaveRepository(
    private val directory: File,
    private val codec: SaveCodec = SaveCodec(),
) : SaveRepository {
    private val mutex = Mutex()
    private val primary = AtomicFile(File(directory, "game.json"))
    private val backup = AtomicFile(File(directory, "game.last-good.json"))
    private var preservedPrimary: ByteArray? = null

    override suspend fun readCandidates(): SaveCandidates = withContext(Dispatchers.IO) {
        mutex.withLock { SaveCandidates(readBounded(primary), readBounded(backup)) }
    }

    override suspend fun write(bytes: ByteArray, revision: Long): SaveWriteResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            val candidate = codec.decode(bytes) as? SaveDecodeResult.Success
                ?: return@withLock SaveWriteResult.Failed("Invalid save data")
            if (candidate.state.revision != revision) return@withLock SaveWriteResult.Failed("Save revision mismatch")
            try {
                val oldBytes = readBounded(primary)
                val old = codec.decode(oldBytes)
                val lastGood = codec.decode(readBounded(backup))
                val diskRevision = listOfNotNull(
                    (old as? SaveDecodeResult.Success)?.state?.revision,
                    (lastGood as? SaveDecodeResult.Success)?.state?.revision,
                ).maxOrNull()
                if (diskRevision != null && revision <= diskRevision) return@withLock SaveWriteResult.Stale(diskRevision)
                if (old is SaveDecodeResult.Corrupt || old is SaveDecodeResult.FutureVersion) {
                    if (preservedPrimary == null || !preservedPrimary!!.contentEquals(digest(primary))) {
                        return@withLock SaveWriteResult.Failed("Existing save requires explicit recovery")
                    }
                }
                if (lastGood is SaveDecodeResult.FutureVersion) return@withLock SaveWriteResult.Failed("Newer backup must be preserved first")
                ensureDirectory()
                if (old is SaveDecodeResult.Success) writeAtomic(backup, oldBytes!!)
                writeAtomic(primary, bytes)
                preservedPrimary = null
                SaveWriteResult.Written
            } catch (_: IOException) {
                SaveWriteResult.Failed("Unable to write saved game")
            }
        }
    }

    override suspend fun preservePrimary(reason: String): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                ensureDirectory()
                preservedPrimary = archive(primary, reason)
                true
            } catch (_: IOException) {
                false
            }
        }
    }

    /** Explicit new-game action only. Archives both candidates before replacing either. */
    suspend fun replaceForNewGame(bytes: ByteArray): SaveWriteResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (codec.decode(bytes) !is SaveDecodeResult.Success) return@withLock SaveWriteResult.Failed("Invalid new game")
            try {
                ensureDirectory()
                archive(primary, "new-game")
                archive(backup, "new-game-backup")
                writeAtomic(backup, bytes)
                writeAtomic(primary, bytes)
                preservedPrimary = null
                SaveWriteResult.Written
            } catch (_: IOException) {
                SaveWriteResult.Failed("Unable to preserve and replace saved game")
            }
        }
    }

    private fun ensureDirectory() {
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Save directory unavailable")
    }

    private fun openIfPresent(file: AtomicFile) = try {
        file.openRead()
    } catch (exception: FileNotFoundException) {
        if (file.baseFile.exists() || File(file.baseFile.path + ".bak").exists()) throw exception
        null
    }

    private fun readBounded(file: AtomicFile): ByteArray? = openIfPresent(file)?.use { input ->
        val bytes = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (bytes.size() <= MAXIMUM_SAVE_BYTES) {
            val remaining = MAXIMUM_SAVE_BYTES + 1 - bytes.size()
            val count = input.read(buffer, 0, minOf(buffer.size, remaining))
            if (count < 0) break
            bytes.write(buffer, 0, count)
        }
        bytes.toByteArray()
    }

    private fun writeAtomic(file: AtomicFile, bytes: ByteArray) {
        val output = file.startWrite()
        try {
            output.write(bytes)
            output.fd.sync()
            file.finishWrite(output)
            if (readBounded(file)?.contentEquals(bytes) != true) throw IOException("Atomic commit verification failed")
        } catch (exception: Exception) {
            file.failWrite(output)
            throw exception
        }
    }

    /** Stream the entire original, including oversized corrupt files, without allocating it. */
    private fun archive(file: AtomicFile, reason: String): ByteArray? {
        val input = openIfPresent(file) ?: return null
        val safeReason = reason.filter { it.isLetterOrDigit() || it == '-' }.take(32).ifBlank { "recovery" }
        val copy = AtomicFile(File(directory, "archive-$safeReason-${UUID.randomUUID()}.json"))
        val fingerprint = MessageDigest.getInstance("SHA-256")
        input.use { original ->
            val output = copy.startWrite()
            try {
                val buffer = ByteArray(8192)
                while (true) {
                    val count = original.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                    fingerprint.update(buffer, 0, count)
                }
                output.fd.sync()
                copy.finishWrite(output)
            } catch (exception: Exception) {
                copy.failWrite(output)
                throw exception
            }
        }
        return fingerprint.digest().also { if (!it.contentEquals(digest(copy))) throw IOException("Archive verification failed") }
    }

    private fun digest(file: AtomicFile): ByteArray? = openIfPresent(file)?.use { input ->
        val fingerprint = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            fingerprint.update(buffer, 0, count)
        }
        fingerprint.digest()
    }

    private companion object { const val MAXIMUM_SAVE_BYTES = 2 * 1024 * 1024 }
}
