package com.canok.kargotycoon.game.persistence

import com.canok.kargotycoon.game.domain.newGame
import com.canok.kargotycoon.game.domain.CommandId
import com.canok.kargotycoon.game.engine.GameCommand
import com.canok.kargotycoon.game.engine.GameEngine
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class GameStoreOpenSafetyTest {
    private val codec = SaveCodec()

    @Test fun commandsBeforeOpenCannotReplaceAnUnreadFutureSave() = runTest {
        val initial = newGame(seed = 11)
        val future = codec.encode(initial).decodeToString().replaceFirst("\"saveVersion\":2", "\"saveVersion\":99").encodeToByteArray()
        val repository = Repository(primary = future)
        val store = GameStore(GameEngine(), codec, repository, initial)
        assertEquals(StoreCommandResult.RecoveryConfirmationRequired, store.dispatch(GameCommand.GenerateDailyOffers(CommandId("early"))))
        assertEquals(0, repository.writes)
        assertArrayEquals(future, repository.primary)
        assertEquals(initial, store.state.value)
    }

    @Test fun failedReadBlocksWritesUntilAValidReopen() = runTest {
        val initial = newGame(seed = 12)
        val repository = Repository(primary = codec.encode(initial), failReads = true)
        val store = GameStore(GameEngine(), codec, repository, initial)
        val result = store.open()
        assertTrue(result is StoreOpenResult.PersistenceFailed)
        assertEquals(initial, store.state.value)
        assertEquals(StoreCommandResult.RecoveryConfirmationRequired, store.dispatch(GameCommand.GenerateDailyOffers(CommandId("after-error"))))
        assertEquals(0, repository.writes)
        repository.failReads = false
        assertTrue(store.open() is StoreOpenResult.Ready)
        assertTrue(store.dispatch(GameCommand.GenerateDailyOffers(CommandId("after-reopen"))) is StoreCommandResult.Applied)
        assertEquals(1, repository.writes)
    }

    @Test fun aFutureBackupWithoutPrimaryIsProtected() = runTest {
        val initial = newGame(seed = 13)
        val future = codec.encode(initial).decodeToString().replaceFirst("\"saveVersion\":2", "\"saveVersion\":99").encodeToByteArray()
        val repository = Repository(backup = future)
        val store = GameStore(GameEngine(), codec, repository, initial)
        assertTrue(store.open() is StoreOpenResult.RecoveryRequired)
        assertEquals(StoreCommandResult.RecoveryConfirmationRequired, store.dispatch(GameCommand.GenerateDailyOffers(CommandId("no-primary"))))
        assertEquals(0, repository.writes)
        assertArrayEquals(future, repository.backup)
    }

    private class Repository(var primary: ByteArray? = null, var backup: ByteArray? = null, var failReads: Boolean = false) : SaveRepository {
        var writes = 0
        override suspend fun readCandidates(): SaveCandidates {
            if (failReads) throw IOException("cannot read saved game")
            return SaveCandidates(primary, backup)
        }
        override suspend fun write(bytes: ByteArray, revision: Long): SaveWriteResult {
            writes++
            backup = primary
            primary = bytes
            return SaveWriteResult.Written
        }
        override suspend fun preservePrimary(reason: String) = false
    }
}
