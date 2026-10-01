package com.canok.kargotycoon.ui.format

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.canok.kargotycoon.R
import com.canok.kargotycoon.game.domain.Capability
import com.canok.kargotycoon.game.domain.DriverStatus
import com.canok.kargotycoon.game.domain.DriverTier
import com.canok.kargotycoon.game.domain.LedgerType
import com.canok.kargotycoon.game.domain.LocationId
import com.canok.kargotycoon.game.domain.Ownership
import com.canok.kargotycoon.game.domain.PackageType
import com.canok.kargotycoon.game.domain.RegionId
import com.canok.kargotycoon.game.domain.RiskId
import com.canok.kargotycoon.game.domain.VehicleSpec
import com.canok.kargotycoon.game.domain.VehicleStatus

/**
 * Player-facing labels. Catalog identifiers are mapped to localized copy so the
 * UI never shows raw configuration names (with proper geographic names kept).
 */

private fun res(id: String): Int = when (id) {
    "rental-panelvan" -> R.string.vehicle_rental_panelvan
    "city-van" -> R.string.vehicle_city_van
    "cool-box" -> R.string.vehicle_cool_box
    "cargo-truck" -> R.string.vehicle_cargo_truck
    else -> 0
}

@Composable
fun vehicleName(spec: VehicleSpec): String = res(spec.id.value).takeIf { it != 0 }?.let { stringResource(it) } ?: spec.name

@Composable
fun tierName(tier: DriverTier): String = when (tier.id.value) {
    "junior" -> stringResource(R.string.tier_junior)
    "profi" -> stringResource(R.string.tier_profi)
    else -> tier.name
}

@Composable
fun packageName(type: PackageType): String = when (type.id.value) {
    "parcel" -> stringResource(R.string.package_parcel)
    "glass" -> stringResource(R.string.package_glass)
    "fresh" -> stringResource(R.string.package_fresh)
    "chemical" -> stringResource(R.string.package_chemical)
    else -> type.name
}

@Composable
fun riskName(id: RiskId): String = when (id.value) {
    "calm" -> stringResource(R.string.risk_calm)
    "rush" -> stringResource(R.string.risk_rush)
    else -> id.value
}

@Composable
fun capabilityName(capability: Capability): String = when (capability) {
    Capability.REFRIGERATED -> stringResource(R.string.capability_refrigerated)
    Capability.FRAGILE -> stringResource(R.string.capability_fragile)
    Capability.HAZARDOUS -> stringResource(R.string.capability_hazardous)
}

@Composable
fun regionName(id: RegionId): String = when (id.value) {
    "ruhr" -> stringResource(R.string.region_ruhr)
    "rhein-main" -> stringResource(R.string.region_rhein_main)
    "elbe" -> stringResource(R.string.region_elbe)
    else -> id.value
}

@Composable
fun locationName(id: LocationId): String = when (id.value) {
    "essen-hub" -> stringResource(R.string.location_essen_hub)
    "dortmund-market" -> stringResource(R.string.location_dortmund_market)
    "frankfurt-terminal" -> stringResource(R.string.location_frankfurt_terminal)
    "mainz-quay" -> stringResource(R.string.location_mainz_quay)
    "hamburg-port" -> stringResource(R.string.location_hamburg_port)
    "leipzig-yard" -> stringResource(R.string.location_leipzig_yard)
    else -> id.value
}

@Composable
fun ownershipName(ownership: Ownership): String =
    if (ownership == Ownership.OWNED) stringResource(R.string.ownership_owned) else stringResource(R.string.ownership_rental)

@Composable
fun vehicleStatusName(status: VehicleStatus): String = when (status) {
    VehicleStatus.AVAILABLE -> stringResource(R.string.vehicle_status_available)
    VehicleStatus.BUSY -> stringResource(R.string.vehicle_status_busy)
    VehicleStatus.MAINTENANCE -> stringResource(R.string.vehicle_status_maintenance)
}

@Composable
fun driverStatusName(status: DriverStatus): String = when (status) {
    DriverStatus.AVAILABLE -> stringResource(R.string.driver_status_available)
    DriverStatus.DRIVING -> stringResource(R.string.driver_status_driving)
    DriverStatus.UNAVAILABLE -> stringResource(R.string.driver_status_unavailable)
}

@Composable
fun ledgerTypeName(type: LedgerType): String = when (type) {
    LedgerType.REVENUE -> stringResource(R.string.ledger_revenue)
    LedgerType.FUEL -> stringResource(R.string.ledger_fuel)
    LedgerType.RENTAL -> stringResource(R.string.ledger_rental)
    LedgerType.PENALTY -> stringResource(R.string.ledger_penalty)
    LedgerType.VEHICLE_UPKEEP -> stringResource(R.string.ledger_upkeep)
    LedgerType.VEHICLE_PURCHASE -> stringResource(R.string.ledger_purchase)
    LedgerType.VEHICLE_SALE -> stringResource(R.string.ledger_sale)
    LedgerType.REPAIR -> stringResource(R.string.ledger_repair)
    LedgerType.MAINTENANCE -> stringResource(R.string.ledger_maintenance)
    LedgerType.DRIVER_HIRE -> stringResource(R.string.ledger_hire)
    LedgerType.DRIVER_WAGE -> stringResource(R.string.ledger_wage)
    LedgerType.DRIVER_SEVERANCE -> stringResource(R.string.ledger_severance)
    LedgerType.RESERVATION -> stringResource(R.string.ledger_reservation)
    LedgerType.RESERVATION_REFUND -> stringResource(R.string.ledger_reservation_refund)
}

@Composable
fun moneyText(cents: Long): String = formatMoney(cents, currentLocale())

@Composable
fun weightText(grams: Long): String =
    stringResource(R.string.unit_kg, formatKilograms(grams, currentLocale()))

@Composable
fun distanceText(meters: Long): String =
    stringResource(R.string.unit_km, formatKilometers(meters, currentLocale()))

@Composable
fun durationText(minutes: Long): String {
    val locale = currentLocale()
    return when {
        minutes < 60L -> stringResource(R.string.duration_minutes, formatInteger(minutes, locale))
        minutes % 60L == 0L -> stringResource(R.string.duration_hours, formatInteger(minutes / 60L, locale))
        else -> stringResource(
            R.string.duration_hours_minutes,
            formatInteger(minutes / 60L, locale),
            formatInteger(minutes % 60L, locale),
        )
    }
}
