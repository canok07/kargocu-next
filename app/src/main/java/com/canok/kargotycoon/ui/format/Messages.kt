package com.canok.kargotycoon.ui.format

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.canok.kargotycoon.R
import com.canok.kargotycoon.data.Notice
import com.canok.kargotycoon.game.engine.Rejection

/** Readable, non-technical feedback. Internal checksum/revision details are never surfaced. */

@Composable
fun noticeText(notice: Notice): String = when (notice) {
    Notice.SAVING_FAILED -> stringResource(R.string.notice_saving_failed)
    Notice.NOT_ENOUGH_MONEY -> stringResource(R.string.notice_not_enough_money)
    Notice.UNAVAILABLE -> stringResource(R.string.notice_unavailable)
    Notice.CAPACITY -> stringResource(R.string.notice_capacity)
    Notice.REQUIREMENT -> stringResource(R.string.notice_requirement)
    Notice.EXPIRED -> stringResource(R.string.notice_expired)
    Notice.INVALID_ACTION -> stringResource(R.string.notice_invalid_action)
    Notice.DELIVERY_FINISHED -> stringResource(R.string.notice_delivery_finished)
}

@Composable
fun rejectionText(reason: Rejection?): String? = when (reason) {
    null -> null
    is Rejection.InsufficientFunds -> stringResource(
        R.string.reject_insufficient_funds,
        formatMoney(reason.required.cents, currentLocale()),
        formatMoney(reason.available.cents, currentLocale()),
    )
    is Rejection.CapacityExceeded -> stringResource(
        R.string.reject_capacity,
        formatInteger(reason.requestedCount.toLong(), currentLocale()),
        formatInteger(reason.availableCount.toLong(), currentLocale()),
        formatKilograms(reason.requestedGrams, currentLocale()),
        formatKilograms(reason.availableGrams, currentLocale()),
    )
    is Rejection.MissingCapability -> stringResource(
        R.string.reject_missing_capability,
        reason.capabilities.map { capabilityName(it) }.joinToString(", "),
    )
    is Rejection.CompanyLevelRequired -> stringResource(R.string.reject_company_level, reason.required, reason.actual)
    is Rejection.RegionLocked -> stringResource(R.string.reject_region_locked, regionName(reason.regionId))
    is Rejection.OfferExpired -> stringResource(R.string.notice_expired)
    is Rejection.VehicleUnavailable -> stringResource(R.string.reject_vehicle_unavailable)
    is Rejection.VehicleBusy -> stringResource(R.string.reject_vehicle_busy)
    is Rejection.ManualDriverBusy -> stringResource(R.string.reject_manual_driver_busy)
    is Rejection.AssignedDriverRequired -> stringResource(R.string.reject_assigned_driver_required)
    is Rejection.DriverUnavailable -> stringResource(R.string.reject_driver_unavailable)
    is Rejection.DriverBusy -> stringResource(R.string.reject_driver_busy)
    is Rejection.DriverAlreadyAssigned -> stringResource(R.string.reject_driver_already_assigned)
    is Rejection.VehicleAlreadyAssigned -> stringResource(R.string.reject_vehicle_already_assigned)
    is Rejection.RentalVehicleProtected -> stringResource(R.string.reject_rental_protected)
    is Rejection.VehicleAlreadyHealthy -> stringResource(R.string.reject_already_healthy)
    is Rejection.MaintenanceNotDue -> stringResource(R.string.reject_maintenance_not_due)
    is Rejection.InvalidDriverName -> stringResource(R.string.reject_invalid_driver_name)
    is Rejection.RouteUnavailable -> stringResource(R.string.reject_route_unavailable)
    else -> stringResource(R.string.notice_invalid_action)
}
