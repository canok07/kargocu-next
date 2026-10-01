package com.canok.kargotycoon.data

import com.canok.kargotycoon.game.domain.*
import com.canok.kargotycoon.game.engine.*
import com.canok.kargotycoon.game.persistence.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.UUID

enum class SessionMode { LOADING, WELCOME, PLAYING, RECOVERY, READ_ERROR }
enum class Notice { SAVING_FAILED, NOT_ENOUGH_MONEY, UNAVAILABLE, CAPACITY, REQUIREMENT, EXPIRED, INVALID_ACTION, DELIVERY_FINISHED }
data class SessionState(
    val mode: SessionMode = SessionMode.LOADING,
    val game: GameState? = null,
    val canRecover: Boolean = false,
    val futureSave: Boolean = false,
    val notice: Notice? = null,
    val noticeSequence: Long = 0,
)
data class OfferPreview(val invoice: JobInvoice?, val reservation: Money?, val durationMinutes: Long?, val rejection: Rejection?)

/** Android session coordination only; time, money and progression remain engine commands. */
class SessionController(
    directory: File,
    private val clock: () -> Long = System::currentTimeMillis,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    val catalog: GameCatalog = DefaultCatalog.value
    private val engine = GameEngine(catalog)
    private val codec = SaveCodec(catalog)
    private val repository = AndroidSaveRepository(directory, codec)
    private val operations = Mutex()
    private val mutableState = MutableStateFlow(SessionState())
    private var store: GameStore? = null
    private var started = false
    @Volatile private var foreground = false
    val state = mutableState.asStateFlow()

    fun start() {
        if (started) return
        started = true
        scope.launch { reopen() }
        scope.launch {
            while (isActive) {
                delay(1_000)
                if (foreground && state.value.mode == SessionMode.PLAYING) dispatch(GameCommand.Resume(id(), clock()))
            }
        }
    }

    fun setForeground(value: Boolean) {
        foreground = value
        if (state.value.mode == SessionMode.PLAYING) enqueue(GameCommand.Resume(id(), clock()))
    }

    fun enqueue(command: GameCommand) { scope.launch { dispatch(command) } }
    fun retryOpen() { scope.launch { reopen() } }
    fun newGame() { scope.launch { startNewGame(clock()) } }
    fun recover() { scope.launch { confirmRecovery() } }
    fun clearNotice() { mutableState.update { it.copy(notice = null) } }

    suspend fun reopen(): StoreOpenResult = operations.withLock {
        mutableState.update { it.copy(mode = SessionMode.LOADING) }
        val current = GameStore(engine, codec, repository, newGame(seed = clock()), catalog)
        store = current
        val result = current.open()
        when (result) {
            is StoreOpenResult.Ready -> {
                mutableState.value = SessionState(SessionMode.PLAYING, result.state)
                publish(current.dispatch(GameCommand.Resume(id(), clock())))
                publish(current.dispatch(GameCommand.GenerateDailyOffers(id())))
            }
            StoreOpenResult.Missing -> mutableState.value = SessionState(SessionMode.WELCOME)
            is StoreOpenResult.RecoveryRequired -> mutableState.value = SessionState(
                SessionMode.RECOVERY, result.recoveredState, result.recoveredState != null,
                result.primaryResult is SaveDecodeResult.FutureVersion,
            )
            is StoreOpenResult.PersistenceFailed -> mutableState.value = SessionState(SessionMode.READ_ERROR)
        }
        result
    }

    suspend fun startNewGame(seed: Long): SaveWriteResult = operations.withLock {
        val initial = newGame(catalog, seed).copy(lastRealtimeMillis = clock())
        val written = repository.replaceForNewGame(codec.encode(initial))
        if (written == SaveWriteResult.Written) {
            val current = GameStore(engine, codec, repository, initial, catalog)
            store = current
            when (current.open()) {
                is StoreOpenResult.Ready -> {
                    mutableState.value = SessionState(SessionMode.PLAYING, current.state.value)
                    publish(current.dispatch(GameCommand.GenerateDailyOffers(id())))
                }
                else -> mutableState.value = SessionState(SessionMode.READ_ERROR)
            }
        } else notify(Notice.SAVING_FAILED)
        written
    }

    suspend fun confirmRecovery(): StoreCommandResult = operations.withLock {
        val result = store?.confirmRecovery() ?: StoreCommandResult.RecoveryConfirmationRequired
        if (result is StoreCommandResult.Applied) {
            mutableState.value = SessionState(SessionMode.PLAYING, result.state)
            publish(store!!.dispatch(GameCommand.Resume(id(), clock())))
            publish(store!!.dispatch(GameCommand.GenerateDailyOffers(id())))
        } else notify(Notice.SAVING_FAILED)
        result
    }

    suspend fun dispatch(command: GameCommand): StoreCommandResult = operations.withLock {
        if (state.value.mode != SessionMode.PLAYING) return@withLock StoreCommandResult.RecoveryConfirmationRequired
        val result = store!!.dispatch(command)
        publish(result)
        result
    }

    suspend fun preview(offerId: OfferId, vehicleId: VehicleId, routeId: RouteId, manual: Boolean): OfferPreview = withContext(Dispatchers.Default) {
        val snapshot = state.value.game ?: return@withContext OfferPreview(null, null, null, null)
        when (val result = engine.reduce(snapshot, GameCommand.AcceptJob(id(), offerId, vehicleId, routeId, manual))) {
            is GameResult.Rejected -> OfferPreview(null, null, null, result.reason)
            is GameResult.Applied -> result.state.activeJobs.last().let { job ->
                OfferPreview(job.invoice, job.reserved, (job.completionAt.millis - job.startedAt.millis) / GameEngine.MINUTE_MILLIS, null)
            }
        }
    }

    suspend fun previewCommand(command: GameCommand): GameResult? = withContext(Dispatchers.Default) {
        state.value.game?.let { engine.reduce(it, command) }
    }

    private fun publish(result: StoreCommandResult) {
        when (result) {
            is StoreCommandResult.Applied -> {
                mutableState.update { it.copy(game = result.state) }
                if (result.events.any { it is GameEvent.JobCompleted }) notify(Notice.DELIVERY_FINISHED)
            }
            is StoreCommandResult.Rejected -> notify(noticeFor(result.reason))
            is StoreCommandResult.PersistenceFailed, is StoreCommandResult.StaleWrite -> notify(Notice.SAVING_FAILED)
            StoreCommandResult.RecoveryConfirmationRequired -> notify(Notice.INVALID_ACTION)
        }
    }

    private fun notify(notice: Notice) {
        mutableState.update { it.copy(notice = notice, noticeSequence = it.noticeSequence + 1) }
    }

    companion object {
        fun id() = CommandId(UUID.randomUUID().toString())
        fun noticeFor(reason: Rejection): Notice = when (reason) {
            is Rejection.InsufficientFunds -> Notice.NOT_ENOUGH_MONEY
            is Rejection.CapacityExceeded -> Notice.CAPACITY
            is Rejection.MissingCapability, is Rejection.CompanyLevelRequired, is Rejection.RegionLocked -> Notice.REQUIREMENT
            is Rejection.OfferExpired -> Notice.EXPIRED
            is Rejection.VehicleUnavailable, is Rejection.VehicleBusy, is Rejection.DriverBusy,
            is Rejection.DriverUnavailable, is Rejection.DriverAlreadyAssigned, is Rejection.VehicleAlreadyAssigned,
            is Rejection.AssignedDriverRequired, Rejection.ManualDriverBusy -> Notice.UNAVAILABLE
            else -> Notice.INVALID_ACTION
        }
    }
}
