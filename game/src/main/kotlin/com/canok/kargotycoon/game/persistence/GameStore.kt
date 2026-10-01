package com.canok.kargotycoon.game.persistence

import com.canok.kargotycoon.game.domain.DefaultCatalog
import com.canok.kargotycoon.game.domain.GameCatalog
import com.canok.kargotycoon.game.domain.GameState
import com.canok.kargotycoon.game.engine.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface SaveRepository {
    suspend fun readCandidates(): SaveCandidates
    suspend fun write(bytes: ByteArray, revision: Long): SaveWriteResult
    suspend fun preservePrimary(reason: String): Boolean
}

data class SaveCandidates(val primary: ByteArray?, val lastKnownGood: ByteArray?)

sealed interface SaveWriteResult {
    data object Written : SaveWriteResult
    data class Stale(val persistedRevision: Long) : SaveWriteResult
    data class Failed(val message: String) : SaveWriteResult
}

sealed interface StoreEvent {
    data class RecoveryRequired(val primary: SaveDecodeResult, val backupRevision: Long?) : StoreEvent
    data class Recovered(val revision: Long) : StoreEvent
    data class PersistenceFailed(val message: String) : StoreEvent
}

sealed interface StoreOpenResult {
    data class Ready(val state: GameState) : StoreOpenResult
    data class RecoveryRequired(val recoveredState: GameState?, val primaryResult: SaveDecodeResult) : StoreOpenResult
    data class PersistenceFailed(val message: String) : StoreOpenResult
    data object Missing : StoreOpenResult
}

sealed interface StoreCommandResult {
    data class Applied(val state: GameState, val events: List<GameEvent>) : StoreCommandResult
    data class Rejected(val state: GameState, val reason: Rejection) : StoreCommandResult
    data class PersistenceFailed(val persistedState: GameState, val attemptedRevision: Long, val message: String) : StoreCommandResult
    data class StaleWrite(val persistedState: GameState, val attemptedRevision: Long, val repositoryRevision: Long) : StoreCommandResult
    data object RecoveryConfirmationRequired : StoreCommandResult
}

class GameStore(
    private val engine: GameEngine,
    private val codec: SaveCodec,
    private val repository: SaveRepository,
    initialState: GameState,
    private val catalog: GameCatalog = DefaultCatalog.value,
) {
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(initialState)
    private val mutableEvents = MutableSharedFlow<StoreEvent>(extraBufferCapacity = 16)
    private var recoveryCandidate: GameState? = null
    private var protectedPrimary = true

    val state: StateFlow<GameState> = mutableState.asStateFlow()
    val events: SharedFlow<StoreEvent> = mutableEvents.asSharedFlow()

    suspend fun open(): StoreOpenResult = mutex.withLock {
        recoveryCandidate = null
        protectedPrimary = true
        val candidates = try {
            repository.readCandidates()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            val message = exception.message ?: "Unable to read saved game"
            mutableEvents.tryEmit(StoreEvent.PersistenceFailed(message))
            return@withLock StoreOpenResult.PersistenceFailed(message)
        }
        val primary = codec.decode(candidates.primary)
        when (primary) {
            is SaveDecodeResult.Success -> {
                protectedPrimary = false
                mutableState.value = primary.state
                StoreOpenResult.Ready(primary.state)
            }
            SaveDecodeResult.Missing -> {
                val backup = codec.decode(candidates.lastKnownGood)
                when (backup) {
                    is SaveDecodeResult.Success -> requireRecovery(primary, backup.state)
                    SaveDecodeResult.Missing -> {
                        protectedPrimary = false
                        StoreOpenResult.Missing
                    }
                    is SaveDecodeResult.Corrupt, is SaveDecodeResult.FutureVersion -> requireRecovery(backup, null)
                }
            }
            is SaveDecodeResult.Corrupt, is SaveDecodeResult.FutureVersion -> {
                val backup = codec.decode(candidates.lastKnownGood)
                requireRecovery(primary, (backup as? SaveDecodeResult.Success)?.state)
            }
        }
    }

    suspend fun dispatch(command: GameCommand): StoreCommandResult = mutex.withLock {
        if (protectedPrimary) return@withLock StoreCommandResult.RecoveryConfirmationRequired
        val persisted = mutableState.value
        when (val reduced = engine.reduce(persisted, command)) {
            is GameResult.Rejected -> StoreCommandResult.Rejected(persisted, reduced.reason)
            is GameResult.Applied -> {
                val candidate = reduced.state.copy(revision = Math.addExact(persisted.revision, 1L))
                val issues = GameStateValidator.validate(candidate, catalog)
                if (issues.isNotEmpty()) return@withLock StoreCommandResult.Rejected(persisted, Rejection.StateInvariantViolation(issues))
                val write = try {
                    repository.write(codec.encode(candidate), candidate.revision)
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    SaveWriteResult.Failed(exception.message ?: exception::class.simpleName ?: "Repository write failed")
                }
                when (write) {
                    SaveWriteResult.Written -> {
                        mutableState.value = candidate
                        StoreCommandResult.Applied(candidate, reduced.events)
                    }
                    is SaveWriteResult.Failed -> {
                        mutableEvents.tryEmit(StoreEvent.PersistenceFailed(write.message))
                        StoreCommandResult.PersistenceFailed(persisted, candidate.revision, write.message)
                    }
                    is SaveWriteResult.Stale -> StoreCommandResult.StaleWrite(persisted, candidate.revision, write.persistedRevision)
                }
            }
        }
    }

    suspend fun confirmRecovery(): StoreCommandResult = mutex.withLock {
        val recovered = recoveryCandidate ?: return@withLock StoreCommandResult.RecoveryConfirmationRequired
        val preserved = try {
            repository.preservePrimary("recovery")
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            false
        }
        if (!preserved) return@withLock StoreCommandResult.PersistenceFailed(mutableState.value, recovered.revision, "Unable to preserve primary save")
        val candidate = recovered.copy(revision = Math.addExact(recovered.revision, 1L))
        val write = try {
            repository.write(codec.encode(candidate), candidate.revision)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            SaveWriteResult.Failed(exception.message ?: exception::class.simpleName ?: "Repository write failed")
        }
        when (write) {
            SaveWriteResult.Written -> {
                recoveryCandidate = null
                protectedPrimary = false
                mutableState.value = candidate
                mutableEvents.tryEmit(StoreEvent.Recovered(candidate.revision))
                StoreCommandResult.Applied(candidate, emptyList())
            }
            is SaveWriteResult.Failed -> StoreCommandResult.PersistenceFailed(mutableState.value, candidate.revision, write.message)
            is SaveWriteResult.Stale -> StoreCommandResult.StaleWrite(mutableState.value, candidate.revision, write.persistedRevision)
        }
    }

    private fun requireRecovery(primary: SaveDecodeResult, backup: GameState?): StoreOpenResult {
        protectedPrimary = true
        recoveryCandidate = backup
        if (backup != null) mutableState.value = backup
        mutableEvents.tryEmit(StoreEvent.RecoveryRequired(primary, backup?.revision))
        return StoreOpenResult.RecoveryRequired(backup, primary)
    }
}
