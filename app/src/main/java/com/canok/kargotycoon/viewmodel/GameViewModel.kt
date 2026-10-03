package com.canok.kargotycoon.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.canok.kargotycoon.KargoApplication
import com.canok.kargotycoon.data.OfferPreview
import com.canok.kargotycoon.data.SessionController
import com.canok.kargotycoon.data.SessionState
import com.canok.kargotycoon.game.domain.GameCatalog
import com.canok.kargotycoon.game.domain.GameSettings
import com.canok.kargotycoon.game.domain.OfferId
import com.canok.kargotycoon.game.domain.RouteId
import com.canok.kargotycoon.game.domain.VehicleId
import com.canok.kargotycoon.game.engine.GameCommand
import com.canok.kargotycoon.game.engine.GameResult
import com.canok.kargotycoon.game.persistence.SaveWriteResult
import com.canok.kargotycoon.game.persistence.StoreCommandResult
import com.canok.kargotycoon.game.persistence.StoreOpenResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Small Android facade over the application-scoped [SessionController]. It only
 * forwards user intents as engine commands; no economy or progression rules are
 * computed here.
 */
class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val session: SessionController = (application as KargoApplication).session
    private val mutableActionPending = MutableStateFlow(false)
    val actionPending: StateFlow<Boolean> = mutableActionPending.asStateFlow()

    val catalog: GameCatalog get() = session.catalog
    val state: StateFlow<SessionState> = session.state

    fun startNewGame(languageTag: String) = performAction { session.createNewGame(languageTag) == SaveWriteResult.Written }
    fun recover() = performAction { session.confirmRecovery() is StoreCommandResult.Applied }
    fun retryOpen() = performAction { session.reopen() is StoreOpenResult.Ready }
    fun clearNotice(sequence: Long? = null) = session.clearNotice(sequence)

    /** A screen leaves an action only after the store has committed it. */
    private fun performAction(onApplied: () -> Unit = {}, action: suspend () -> Boolean) {
        if (mutableActionPending.value) return
        mutableActionPending.value = true
        viewModelScope.launch {
            try {
                if (withContext(Dispatchers.Default) { action() }) onApplied()
            } finally {
                mutableActionPending.value = false
            }
        }
    }

    fun send(command: GameCommand, onApplied: () -> Unit = {}) =
        performAction(onApplied) { session.dispatch(command) is StoreCommandResult.Applied }
    fun endDay() = send(GameCommand.AdvanceDay(SessionController.id()))
    fun setTutorialDismissed(dismissed: Boolean) =
        send(GameCommand.SetTutorialDismissed(SessionController.id(), dismissed))
    fun changeSettings(settings: GameSettings) =
        send(GameCommand.ChangeSettings(SessionController.id(), settings))

    fun setLanguage(language: String) = patchSettings { it.copy(languageTag = language) }
    fun setSound(enabled: Boolean) = patchSettings { it.copy(soundEnabled = enabled) }
    fun setHaptics(enabled: Boolean) = patchSettings { it.copy(hapticsEnabled = enabled) }

    private fun patchSettings(transform: (GameSettings) -> GameSettings) {
        viewModelScope.launch(Dispatchers.Default) { session.updateSettings(transform) }
    }

    fun acceptJob(offerId: OfferId, vehicleId: VehicleId, routeId: RouteId, manual: Boolean, onApplied: () -> Unit = {}) =
        send(GameCommand.AcceptJob(SessionController.id(), offerId, vehicleId, routeId, manual), onApplied)

    fun purchaseVehicle(specId: com.canok.kargotycoon.game.domain.VehicleSpecId, onApplied: () -> Unit = {}) =
        send(GameCommand.PurchaseVehicle(SessionController.id(), specId), onApplied)

    fun sellVehicle(vehicleId: VehicleId, onApplied: () -> Unit = {}) = send(GameCommand.SellVehicle(SessionController.id(), vehicleId), onApplied)
    fun repairVehicle(vehicleId: VehicleId) = send(GameCommand.RepairVehicle(SessionController.id(), vehicleId))
    fun maintainVehicle(vehicleId: VehicleId) = send(GameCommand.MaintainVehicle(SessionController.id(), vehicleId))

    fun hireDriver(tierId: com.canok.kargotycoon.game.domain.DriverTierId, name: String, onApplied: () -> Unit = {}) =
        send(GameCommand.HireDriver(SessionController.id(), tierId, name), onApplied)

    fun fireDriver(driverId: com.canok.kargotycoon.game.domain.DriverId, onApplied: () -> Unit = {}) =
        send(GameCommand.FireDriver(SessionController.id(), driverId), onApplied)

    fun assignDriver(driverId: com.canok.kargotycoon.game.domain.DriverId, vehicleId: VehicleId, onApplied: () -> Unit = {}) =
        send(GameCommand.AssignDriver(SessionController.id(), driverId, vehicleId), onApplied)

    fun unassignDriver(driverId: com.canok.kargotycoon.game.domain.DriverId) =
        send(GameCommand.UnassignDriver(SessionController.id(), driverId))

    suspend fun previewOffer(offerId: OfferId, vehicleId: VehicleId, routeId: RouteId, manual: Boolean): OfferPreview =
        session.preview(offerId, vehicleId, routeId, manual)

    suspend fun previewCommand(command: GameCommand): GameResult? = session.previewCommand(command)

    suspend fun previewRepair(vehicleId: VehicleId): GameResult? =
        session.previewCommand(GameCommand.RepairVehicle(SessionController.id(), vehicleId))

    suspend fun previewMaintain(vehicleId: VehicleId): GameResult? =
        session.previewCommand(GameCommand.MaintainVehicle(SessionController.id(), vehicleId))

    suspend fun previewSell(vehicleId: VehicleId): GameResult? =
        session.previewCommand(GameCommand.SellVehicle(SessionController.id(), vehicleId))

    suspend fun previewFire(driverId: com.canok.kargotycoon.game.domain.DriverId): GameResult? =
        session.previewCommand(GameCommand.FireDriver(SessionController.id(), driverId))
}
