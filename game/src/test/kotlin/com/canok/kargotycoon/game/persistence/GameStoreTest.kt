package com.canok.kargotycoon.game.persistence

import com.canok.kargotycoon.game.domain.*
import com.canok.kargotycoon.game.engine.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class GameStoreTest {
    private val codec = SaveCodec()
    private val engine = GameEngine()

    @Test
    fun failedSaveCannotPublishUnpersistedState() = runTest {
        val initial = newGame(seed = 1)
        val repository = FakeRepository(codec.encode(initial), failWrites = true)
        val store = GameStore(engine, codec, repository, initial)
        store.open()
        val result = store.dispatch(GameCommand.GenerateDailyOffers(CommandId("offers")))
        assertTrue(result is StoreCommandResult.PersistenceFailed)
        assertEquals(initial, store.state.value)
        assertEquals(initial, (codec.decode(repository.primary) as SaveDecodeResult.Success).state)
    }

    @Test
    fun staleSaveCannotOverwriteNewerRevision() = runTest {
        val initial = newGame(seed = 2).copy(revision = 2)
        val repository = FakeRepository(codec.encode(initial), forcedPersistedRevision = 10)
        val store = GameStore(engine, codec, repository, initial)
        store.open()
        val result = store.dispatch(GameCommand.GenerateDailyOffers(CommandId("offers")))
        assertEquals(StoreCommandResult.StaleWrite(initial, 3, 10), result)
        assertEquals(initial, store.state.value)
        assertEquals(emptyList<Long>(), repository.successfulWrites)
    }

    @Test
    fun concurrentCommandsSerializeAndPublishMonotonicPersistedRevisions() = runTest {
        val initial = newGame(seed = 3)
        val repository = FakeRepository(codec.encode(initial))
        val store = GameStore(engine, codec, repository, initial)
        store.open()
        val results = listOf(
            async { store.dispatch(GameCommand.GenerateDailyOffers(CommandId("offers"))) },
            async { store.dispatch(GameCommand.RefreshProgression(CommandId("progress"))) },
        ).awaitAll()
        assertTrue(results.all { it is StoreCommandResult.Applied })
        assertEquals(listOf(1L, 2L), repository.successfulWrites)
        assertEquals(2L, store.state.value.revision)
        assertEquals(2L, (codec.decode(repository.primary) as SaveDecodeResult.Success).state.revision)
    }

    @Test
    fun corruptPrimaryRequiresExplicitRecoveryAndPreservesDamage() = runTest {
        val backupState = newGame(seed = 4).copy(revision = 7)
        val damaged = "{truncated".encodeToByteArray()
        val repository = FakeRepository(damaged, backup = codec.encode(backupState))
        val store = GameStore(engine, codec, repository, newGame(seed = 99))
        val opened = store.open() as StoreOpenResult.RecoveryRequired
        assertEquals(backupState, opened.recoveredState)
        assertTrue(opened.primaryResult is SaveDecodeResult.Corrupt)
        assertEquals(StoreCommandResult.RecoveryConfirmationRequired, store.dispatch(GameCommand.RefreshProgression(CommandId("blocked"))))
        assertTrue(repository.preserved == null)

        val recovered = store.confirmRecovery() as StoreCommandResult.Applied
        assertEquals(8L, recovered.state.revision)
        assertTrue(damaged.contentEquals(repository.preserved))
        assertEquals(backupState.copy(revision = 8), store.state.value)
    }

    @Test
    fun futurePrimaryIsNeverSilentlyReplaced() = runTest {
        val initial = newGame(seed = 5)
        val current = codec.encode(initial)
        val future = current.decodeToString().replaceFirst("\"saveVersion\":2", "\"saveVersion\":99").encodeToByteArray()
        val repository = FakeRepository(future)
        val store = GameStore(engine, codec, repository, initial)
        val opened = store.open() as StoreOpenResult.RecoveryRequired
        assertTrue(opened.primaryResult is SaveDecodeResult.FutureVersion)
        assertEquals(StoreCommandResult.RecoveryConfirmationRequired, store.confirmRecovery())
        assertTrue(future.contentEquals(repository.primary))
    }

    @Test
    fun throwingSaveCannotPublishUnpersistedState() = runTest {
        val initial = newGame(seed = 6)
        val repository = FakeRepository(codec.encode(initial), throwWrites = true)
        val store = GameStore(engine, codec, repository, initial)
        store.open()
        val result = store.dispatch(GameCommand.GenerateDailyOffers(CommandId("offers")))
        assertTrue(result is StoreCommandResult.PersistenceFailed)
        assertEquals(initial, store.state.value)
        assertEquals(initial, (codec.decode(repository.primary) as SaveDecodeResult.Success).state)
    }

    @Test
    fun cancellationFromRepositoryWritePropagates() = runTest {
        val initial = newGame(seed = 7)
        val store = GameStore(engine, codec, FakeRepository(codec.encode(initial), cancelWrites = true), initial)
        store.open()
        try {
            store.dispatch(GameCommand.GenerateDailyOffers(CommandId("offers")))
            fail("Expected cancellation")
        } catch (_: CancellationException) {
        }
        assertEquals(initial, store.state.value)
    }

    @Test
    fun successfulReopenClearsObsoleteRecoveryProtection() = runTest {
        val initial = newGame(seed = 8)
        val repository = FakeRepository("{broken".encodeToByteArray(), codec.encode(initial))
        val store = GameStore(engine, codec, repository, initial)
        assertTrue(store.open() is StoreOpenResult.RecoveryRequired)
        repository.primary = codec.encode(initial)
        assertTrue(store.open() is StoreOpenResult.Ready)
        assertTrue(store.dispatch(GameCommand.GenerateDailyOffers(CommandId("offers"))) is StoreCommandResult.Applied)
    }

    @Test
    fun storeValidationUsesInjectedCatalog() = runTest {
        val catalog = DefaultCatalog.value.copy(version = 2)
        val customCodec = SaveCodec(catalog)
        val customEngine = GameEngine(catalog)
        val initial = newGame(catalog, seed = 9)
        val repository = FakeRepository(customCodec.encode(initial))
        val store = GameStore(customEngine, customCodec, repository, initial, catalog)
        store.open()
        assertTrue(store.dispatch(GameCommand.GenerateDailyOffers(CommandId("offers"))) is StoreCommandResult.Applied)
    }

    private class FakeRepository(
        var primary: ByteArray?,
        private var backup: ByteArray? = null,
        private val failWrites: Boolean = false,
        private val forcedPersistedRevision: Long? = null,
        private val throwWrites: Boolean = false,
        private val cancelWrites: Boolean = false,
    ) : SaveRepository {
        val successfulWrites = mutableListOf<Long>()
        var preserved: ByteArray? = null

        override suspend fun readCandidates(): SaveCandidates = SaveCandidates(primary, backup)

        override suspend fun write(bytes: ByteArray, revision: Long): SaveWriteResult {
            if (cancelWrites) throw CancellationException("cancelled")
            if (throwWrites) throw IllegalStateException("disk unavailable")
            if (failWrites) return SaveWriteResult.Failed("disk full")
            forcedPersistedRevision?.let { return SaveWriteResult.Stale(it) }
            val currentRevision = primary?.let { (SaveCodec().decode(it) as? SaveDecodeResult.Success)?.state?.revision }
            if (currentRevision != null && currentRevision >= revision) return SaveWriteResult.Stale(currentRevision)
            backup = primary
            primary = bytes
            successfulWrites += revision
            return SaveWriteResult.Written
        }

        override suspend fun preservePrimary(reason: String): Boolean {
            preserved = primary?.copyOf()
            primary = null
            return true
        }
    }
}
