package com.canok.kargotycoon

import android.util.AtomicFile
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.canok.kargotycoon.data.AndroidSaveRepository
import com.canok.kargotycoon.game.domain.newGame
import com.canok.kargotycoon.game.persistence.SaveCodec
import com.canok.kargotycoon.game.persistence.SaveDecodeResult
import com.canok.kargotycoon.game.persistence.SaveWriteResult
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/**
 * Instrumented stress for the real AtomicFile-backed repository. Every test uses
 * its own temporary subdirectory and never touches the live default game save.
 * Run on a device/emulator with:
 *   ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.canok.kargotycoon.StressAndroidSaveRepositoryTest
 */
@RunWith(AndroidJUnit4::class)
class StressAndroidSaveRepositoryTest {
    private val codec = SaveCodec()
    private lateinit var directory: File

    @Before
    fun prepare() {
        directory = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "stress-save-${UUID.randomUUID()}")
        assertTrue(directory.mkdirs())
    }

    @After
    fun clean() {
        directory.deleteRecursively()
    }

    private fun repository() = AndroidSaveRepository(directory)

    @Test
    fun manySerialSavesKeepExactRevisionAndRollingBackup() = runBlocking {
        val repository = repository()
        val count = 512
        for (revision in 1L..count) {
            assertEquals(SaveWriteResult.Written, repository.write(codec.encode(newGame(seed = 7).copy(revision = revision)), revision))
        }
        val reopened = repository()
        val primary = codec.decode(reopened.readCandidates().primary) as SaveDecodeResult.Success
        val backup = codec.decode(reopened.readCandidates().lastKnownGood) as SaveDecodeResult.Success
        assertEquals(count.toLong(), primary.state.revision)
        assertEquals(count - 1L, backup.state.revision)
        assertEquals(SaveWriteResult.Stale(count.toLong()), reopened.write(codec.encode(newGame(seed = 7).copy(revision = 1)), 1))
    }

    @Test
    fun competingWritesNeverRegressDiskRevision() = runBlocking {
        val repository = repository()
        val revisions = (1L..128L).shuffled(java.util.Random(4242))
        val results = revisions.map { revision ->
            async(Dispatchers.Default) { repository.write(codec.encode(newGame(seed = 5).copy(revision = revision)), revision) }
        }.awaitAll()
        assertTrue(results.none { it is SaveWriteResult.Failed })
        assertTrue(results.any { it == SaveWriteResult.Written })
        val reopened = repository()
        val primary = codec.decode(reopened.readCandidates().primary) as SaveDecodeResult.Success
        assertEquals(128L, primary.state.revision)
    }

    @Test
    fun blockedThenInterruptedWritesRecoverToLastCommittedSave() = runBlocking {
        val repository = repository()
        val first = codec.encode(newGame(seed = 9).copy(revision = 1))
        val second = codec.encode(newGame(seed = 9).copy(revision = 2))
        assertEquals(SaveWriteResult.Written, repository.write(first, 1))

        val obstruction = File(directory, "game.json.new").also { assertTrue(it.mkdir()) }
        // AtomicFile.openRead removes an empty .new directory. A nonempty one
        // really obstructs the next write, as it would on a blocked filesystem.
        File(obstruction, "obstruction").writeText("test-only")
        assertTrue(repository.write(second, 2) is SaveWriteResult.Failed)
        assertArrayEquals(first, repository.readCandidates().primary)
        assertTrue(obstruction.deleteRecursively())
        assertEquals(SaveWriteResult.Written, repository.write(second, 2))

        // Abandoned writer: process death leaves a partial .new without commit.
        val abandoned = AtomicFile(File(directory, "game.json")).startWrite()
        abandoned.write("partial-uncommitted".toByteArray())
        abandoned.fd.sync()
        abandoned.close()

        val reopened = repository()
        val primary = codec.decode(reopened.readCandidates().primary) as SaveDecodeResult.Success
        assertEquals(2L, primary.state.revision)
    }

    @Test
    fun reopenLoopsAreStableAndCheap() = runBlocking {
        val seed = codec.encode(newGame(seed = 11).copy(revision = 3))
        repository().write(seed, 3)
        repeat(300) {
            val candidates = repository().readCandidates()
            assertArrayEquals(seed, candidates.primary)
        }
    }

    @Test
    fun corruptPrimaryKeepsLastGoodAndPreservationEnablesRecovery() = runBlocking {
        val repository = repository()
        val first = codec.encode(newGame(seed = 12).copy(revision = 1))
        val second = codec.encode(newGame(seed = 12).copy(revision = 2))
        repository.write(first, 1)
        repository.write(second, 2)
        File(directory, "game.json").writeBytes("not-json".toByteArray())

        val corrupted = repository.readCandidates()
        assertTrue(codec.decode(corrupted.primary) is SaveDecodeResult.Corrupt)
        assertEquals(1L, (codec.decode(corrupted.lastKnownGood) as SaveDecodeResult.Success).state.revision)
        assertTrue(repository.write(codec.encode(newGame(seed = 12).copy(revision = 2)), 2) is SaveWriteResult.Failed)

        assertTrue(repository.preservePrimary("recovery"))
        assertEquals(SaveWriteResult.Written, repository.write(codec.encode(newGame(seed = 12).copy(revision = 2)), 2))
        assertTrue(directory.listFiles()!!.any { it.name.startsWith("archive-") })
    }
}
