package com.canok.kargotycoon.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
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
import kotlinx.coroutines.flow.StateFlow

/**
 * Small Android facade over the application-scoped [SessionController]. It only
 * forwards user intents as engine commands; no economy or progression rules are
 * computed here.
 */
class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val session: SessionController = (application as KargoApplication).session

    val catalog: GameCatalog get() = session.catalog
    val state: StateFlow<SessionState> = session.state

    fun startNewGame(languageTag: String) = session.newGame(languageTag)
    fun recover() = session.recover()
    fun retryOpen() = session.retryOpen()
    fun clearNotice() = session.clearNotice()

    fun send(command: GameCommand) = session.enqueue(command)
    fun endDay() = session.enqueue(GameCommand.AdvanceDay(SessionController.id()))
    fun setTutorialDismissed(dismissed: Boolean) =
        session.enqueue(GameCommand.SetTutorialDismissed(SessionController.id(), dismissed))
    fun changeSettings(settings: GameSettings) =
        session.enqueue(GameCommand.ChangeSettings(SessionController.id(), settings))

    fun acceptJob(offerId: OfferId, vehicleId: VehicleId, routeId: RouteId, manual: Boolean) =
        session.enqueue(GameCommand.AcceptJob(SessionController.id(), offerId, vehicleId, routeId, manual))

    fun purchaseVehicle(specId: com.canok.kargotycoon.game.domain.VehicleSpecId) =
        session.enqueue(GameCommand.PurchaseVehicle(SessionController.id(), specId))

    fun sellVehicle(vehicleId: VehicleId) = session.enqueue(GameCommand.SellVehicle(SessionController.id(), vehicleId))
    fun repairVehicle(vehicleId: VehicleId) = session.enqueue(GameCommand.RepairVehicle(SessionController.id(), vehicleId))
    fun maintainVehicle(vehicleId: VehicleId) = session.enqueue(GameCommand.MaintainVehicle(SessionController.id(), vehicleId))

    fun hireDriver(tierId: com.canok.kargotycoon.game.domain.DriverTierId, name: String) =
        session.enqueue(GameCommand.HireDriver(SessionController.id(), tierId, name))

    fun fireDriver(driverId: com.canok.kargotycoon.game.domain.DriverId) =
        session.enqueue(GameCommand.FireDriver(SessionController.id(), driverId))

    fun assignDriver(driverId: com.canok.kargotycoon.game.domain.DriverId, vehicleId: VehicleId) =
        session.enqueue(GameCommand.AssignDriver(SessionController.id(), driverId, vehicleId))

    fun unassignDriver(driverId: com.canok.kargotycoon.game.domain.DriverId) =
        session.enqueue(GameCommand.UnassignDriver(SessionController.id(), driverId))

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
