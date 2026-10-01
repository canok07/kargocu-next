package com.canok.kargotycoon.ui.state

import com.canok.kargotycoon.game.domain.LedgerType
import com.canok.kargotycoon.game.domain.Money
import com.canok.kargotycoon.game.engine.GameEvent
import com.canok.kargotycoon.game.engine.GameResult

/**
 * Reads confirmation prices out of a read-only reducer result. The engine
 * computes the amounts; the UI only surfaces them from the events. These
 * commands never consume RNG and never touch the real store.
 */
private fun GameResult?.events(): List<GameEvent> = (this as? GameResult.Applied)?.events.orEmpty()

fun GameResult?.vehicleRepairCost(): Money? =
    events().filterIsInstance<GameEvent.VehicleRepaired>().firstOrNull()?.cost

fun GameResult?.vehicleMaintenanceCost(): Money? =
    events().filterIsInstance<GameEvent.VehicleMaintained>().firstOrNull()?.cost

fun GameResult?.vehicleSaleProceeds(): Money? =
    events().filterIsInstance<GameEvent.VehicleSold>().firstOrNull()?.proceeds

fun GameResult?.driverSeverance(): Money? =
    events().filterIsInstance<GameEvent.DriverFired>().firstOrNull()?.severance

fun GameResult?.ledgerAmount(type: LedgerType): Money? =
    events().filterIsInstance<GameEvent.LedgerBooked>().firstOrNull { it.entry.type == type }?.entry?.amount

/** Hiring cost is booked as a negative ledger amount, so flip it back. */
fun GameResult?.driverHireCost(): Money? = ledgerAmount(LedgerType.DRIVER_HIRE)?.let { Money(-it.cents) }
