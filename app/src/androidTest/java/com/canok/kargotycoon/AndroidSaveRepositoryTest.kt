package com.canok.kargotycoon

import android.util.AtomicFile
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.canok.kargotycoon.data.AndroidSaveRepository
import com.canok.kargotycoon.game.domain.newGame
import com.canok.kargotycoon.game.persistence.*
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class AndroidSaveRepositoryTest {
    private val codec = SaveCodec()
    private lateinit var directory: File
    private lateinit var repository: AndroidSaveRepository

    @Before fun prepare() {
        directory = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "save-test-${UUID.randomUUID()}")
        assertTrue(directory.mkdirs())
        repository = AndroidSaveRepository(directory)
    }

    @After fun clean() { directory.deleteRecursively() }

    @Test fun primaryAndLastGoodSurviveRepositoryRecreation() = runBlocking {
        val first = codec.encode(newGame(seed = 9).copy(revision = 1))
        val second = codec.encode(newGame(seed = 9).copy(revision = 2))
        assertEquals(SaveWriteResult.Written, repository.write(first, 1))
        assertEquals(SaveWriteResult.Written, repository.write(second, 2))
        val reopened = AndroidSaveRepository(directory).readCandidates()
        assertArrayEquals(second, reopened.primary)
        assertArrayEquals(first, reopened.lastKnownGood)
    }

    @Test fun interruptedAtomicWriteKeepsPreviousCommittedSave() = runBlocking {
        val original = codec.encode(newGame(seed = 9).copy(revision = 1))
        repository.write(original, 1)
        val file = AtomicFile(File(directory, "game.json"))
        val interrupted = file.startWrite()
        interrupted.write("partial".toByteArray())
        file.failWrite(interrupted)
        assertArrayEquals(original, AndroidSaveRepository(directory).readCandidates().primary)
    }

    @Test fun staleAndConcurrentWritesCannotRegressDiskRevision() = runBlocking {
        val writes = (1L..12L).map { revision -> async(Dispatchers.Default) {
            repository.write(codec.encode(newGame(seed = 9).copy(revision = revision)), revision)
        } }
        writes.awaitAll()
        val reopened = AndroidSaveRepository(directory)
        val primary = codec.decode(reopened.readCandidates().primary) as SaveDecodeResult.Success
        assertEquals(12L, primary.state.revision)
        assertEquals(SaveWriteResult.Stale(12), reopened.write(codec.encode(newGame(seed = 9).copy(revision = 2)), 2))
    }

    @Test fun unknownFutureSaveCannotBeOverwrittenWithoutPreservation() = runBlocking {
        val future = codec.encode(newGame(seed = 9)).decodeToString().replaceFirst("\"saveVersion\":2", "\"saveVersion\":99").encodeToByteArray()
        File(directory, "game.json").writeBytes(future)
        assertTrue(repository.write(codec.encode(newGame(seed = 9).copy(revision = 1)), 1) is SaveWriteResult.Failed)
        assertArrayEquals(future, repository.readCandidates().primary)
    }

    @Test fun corruptPrimaryIsArchivedBeforeLastGoodRecovery() = runBlocking {
        val backup = codec.encode(newGame(seed = 9).copy(revision = 1))
        repository.write(backup, 1)
        repository.write(codec.encode(newGame(seed = 9).copy(revision = 2)), 2)
        val corrupt = "broken-save".toByteArray()
        File(directory, "game.json").writeBytes(corrupt)
        assertTrue(repository.preservePrimary("recovery"))
        assertEquals(SaveWriteResult.Written, repository.write(codec.encode(newGame(seed = 9).copy(revision = 2)), 2))
        assertArrayEquals(backup, repository.readCandidates().lastKnownGood)
        assertTrue(directory.listFiles()!!.any { it.name.startsWith("archive-") && it.readBytes().contentEquals(corrupt) })
    }

    @Test fun preservationDoesNotAuthorizeOverwritingADifferentFutureFile() = runBlocking {
        File(directory, "game.json").writeText("broken-one")
        assertTrue(repository.preservePrimary("recovery"))
        File(directory, "game.json").writeText("broken-two")
        assertTrue(repository.write(codec.encode(newGame(seed = 9).copy(revision = 1)), 1) is SaveWriteResult.Failed)
        assertEquals("broken-two", File(directory, "game.json").readText())
    }

    @Test fun oversizedInputIsBoundedAndOriginalCanStillBeArchived() = runBlocking {
        val huge = ByteArray(2 * 1024 * 1024 + 1234) { 42 }
        File(directory, "game.json").writeBytes(huge)
        val candidate = repository.readCandidates().primary!!
        assertEquals(2 * 1024 * 1024 + 1, candidate.size)
        assertTrue(codec.decode(candidate) is SaveDecodeResult.Corrupt)
        assertTrue(repository.preservePrimary("recovery"))
        assertTrue(directory.listFiles()!!.any { it.name.startsWith("archive-") && it.readBytes().contentEquals(huge) })
    }

    @Test fun explicitNewGameArchivesBothPreviousCandidates() = runBlocking {
        val first = codec.encode(newGame(seed = 9).copy(revision = 1))
        val second = codec.encode(newGame(seed = 9).copy(revision = 2))
        repository.write(first, 1)
        repository.write(second, 2)
        val fresh = codec.encode(newGame(seed = 77))
        assertEquals(SaveWriteResult.Written, repository.replaceForNewGame(fresh))
        val saved = repository.readCandidates()
        assertArrayEquals(fresh, saved.primary)
        assertArrayEquals(fresh, saved.lastKnownGood)
        val archives = directory.listFiles()!!.filter { it.name.startsWith("archive-") }.map { it.readBytes() }
        assertTrue(archives.any { it.contentEquals(first) })
        assertTrue(archives.any { it.contentEquals(second) })
    }
}
