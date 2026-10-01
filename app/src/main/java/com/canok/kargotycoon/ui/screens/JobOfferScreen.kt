package com.canok.kargotycoon.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.canok.kargotycoon.R
import com.canok.kargotycoon.data.OfferPreview
import com.canok.kargotycoon.game.domain.GameCatalog
import com.canok.kargotycoon.game.domain.GameState
import com.canok.kargotycoon.game.domain.OfferId
import com.canok.kargotycoon.game.domain.RouteId
import com.canok.kargotycoon.game.domain.VehicleId
import com.canok.kargotycoon.ui.TestTags
import com.canok.kargotycoon.ui.components.ChoiceChip
import com.canok.kargotycoon.ui.components.EmptyState
import com.canok.kargotycoon.ui.components.InfoBanner
import com.canok.kargotycoon.ui.components.KeyValueRow
import com.canok.kargotycoon.ui.components.LedgerRow
import com.canok.kargotycoon.ui.components.Mark
import com.canok.kargotycoon.ui.components.ScreenBody
import com.canok.kargotycoon.ui.components.SectionHeading
import com.canok.kargotycoon.ui.format.currentLocale
import com.canok.kargotycoon.ui.format.distanceText
import com.canok.kargotycoon.ui.format.durationText
import com.canok.kargotycoon.ui.format.formatInteger
import com.canok.kargotycoon.ui.format.formatMoney
import com.canok.kargotycoon.ui.format.locationName
import com.canok.kargotycoon.ui.format.ownershipName
import com.canok.kargotycoon.ui.format.packageName
import com.canok.kargotycoon.ui.format.rejectionText
import com.canok.kargotycoon.ui.format.riskName
import com.canok.kargotycoon.ui.format.vehicleName
import com.canok.kargotycoon.ui.format.weightText
import com.canok.kargotycoon.ui.state.Projections
import com.canok.kargotycoon.viewmodel.GameViewModel

@Composable
fun JobOfferScreen(
    offerId: String,
    game: GameState,
    catalog: GameCatalog,
    viewModel: GameViewModel,
    onBack: () -> Unit,
) {
    val locale = currentLocale()
    val offer = game.offers.firstOrNull { it.id.value == offerId }
    if (offer == null) {
        ScreenBody(Modifier.testTag(TestTags.JOB_DETAIL_ROOT)) {
            EmptyState(
                mark = Mark.Parcel,
                title = stringResource(R.string.job_offer_missing_title),
                body = stringResource(R.string.job_offer_missing_body),
                action = { OutlinedButton(onClick = onBack) { Text(stringResource(R.string.action_back)) } },
            )
        }
        return
    }

    val vehicles = Projections.availableVehicles(game)
    var selectedVehicle by rememberSaveable(offerId) { mutableStateOf(vehicles.firstOrNull()?.id?.value) }
    var selectedRoute by rememberSaveable(offerId) { mutableStateOf(offer.routeOptions.firstOrNull()?.value) }
    var manual by rememberSaveable(offerId) { mutableStateOf(true) }
    var preview by remember { mutableStateOf<OfferPreview?>(null) }

    val vehicle = vehicles.firstOrNull { it.id.value == selectedVehicle }
    val assignedDriver = vehicle?.let { Projections.driverForVehicle(game, it) }
    val assignedAvailable = assignedDriver != null
    val effectiveManual = manual || !assignedAvailable

    LaunchedEffect(selectedVehicle, selectedRoute, effectiveManual, game.revision) {
        if (selectedVehicle == null || selectedRoute == null) {
            preview = null
            return@LaunchedEffect
        }
        preview = viewModel.previewOffer(OfferId(offerId), VehicleId(selectedVehicle!!), RouteId(selectedRoute!!), effectiveManual)
    }

    val packageType = catalog.packageTypes.firstOrNull { it.id == offer.packageTypeId }
    val route = catalog.routes.firstOrNull { it.id == offer.routeOptions.firstOrNull() }

    ScreenBody(Modifier.testTag(TestTags.JOB_DETAIL_ROOT)) {
        SectionHeading(stringResource(R.string.job_offer_facts))
        KeyValueRow(stringResource(R.string.offer_route), stringResource(R.string.route_line, locationName(offer.originId), locationName(offer.destinationId)))
        KeyValueRow(stringResource(R.string.offer_package), packageType?.let { packageName(it) } ?: "-")
        KeyValueRow(
            stringResource(R.string.offer_load),
            stringResource(R.string.offer_count_weight, formatInteger(offer.count.toLong(), locale), weightText(offer.totalWeightGrams)),
        )
        KeyValueRow(stringResource(R.string.offer_risk), riskName(offer.riskId))
        if (route != null) KeyValueRow(stringResource(R.string.offer_distance), distanceText(route.distanceMeters))

        SectionHeading(stringResource(R.string.job_vehicle_title))
        if (vehicles.isEmpty()) {
            EmptyState(mark = Mark.Fleet, title = stringResource(R.string.job_no_vehicle_title), body = stringResource(R.string.job_no_vehicle_body))
        } else {
            vehicles.forEach { candidate ->
                val spec = Projections.specOf(catalog, candidate)
                val driver = Projections.driverForVehicle(game, candidate)
                LedgerRow(
                    title = spec?.let { vehicleName(it) } ?: stringResource(R.string.unknown_vehicle),
                    subtitle = spec?.let {
                        stringResource(
                            R.string.job_vehicle_capacity,
                            formatInteger(it.capacityCount.toLong(), locale),
                            weightText(it.capacityGrams),
                        ) + " · " + ownershipName(candidate.ownership) + (driver?.let { " · " + it.name } ?: "")
                    },
                    value = if (candidate.id.value == selectedVehicle) stringResource(R.string.selected) else null,
                    mark = Mark.Fleet,
                    onClick = { selectedVehicle = candidate.id.value },
                    testTag = TestTags.jobVehicle(candidate.id.value),
                )
            }
        }

        SectionHeading(stringResource(R.string.job_route_title))
        offer.routeOptions.forEach { routeId ->
            val spec = catalog.routes.firstOrNull { it.id == routeId }
            LedgerRow(
                title = spec?.let { stringResource(R.string.route_line, locationName(it.originId), locationName(it.destinationId)) } ?: routeId.value,
                subtitle = spec?.let { distanceText(it.distanceMeters) + " · " + durationText(it.durationGameMinutes.toLong()) },
                value = if (routeId.value == selectedRoute) stringResource(R.string.selected) else null,
                mark = Mark.Route,
                onClick = { selectedRoute = routeId.value },
                testTag = TestTags.jobRoute(routeId.value),
            )
        }

        SectionHeading(stringResource(R.string.job_mode_title))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceChip(effectiveManual, stringResource(R.string.job_mode_manual), { manual = true }, testTag = TestTags.JOB_MODE_MANUAL)
            if (assignedAvailable) {
                ChoiceChip(!effectiveManual, stringResource(R.string.job_mode_assigned), { manual = false }, testTag = TestTags.JOB_MODE_ASSIGNED)
            }
        }
        if (!assignedAvailable) {
            Text(stringResource(R.string.job_assigned_unavailable), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        SectionHeading(stringResource(R.string.job_preview_title))
        val current = preview
        if (current == null) {
            Text(stringResource(R.string.job_preview_none), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            KeyValueRow(stringResource(R.string.job_preview_payout), current.invoice?.let { formatMoney(it.maximumRevenue.cents, locale) } ?: "-")
            KeyValueRow(stringResource(R.string.job_preview_reservation), current.reservation?.let { formatMoney(it.cents, locale) } ?: "-")
            KeyValueRow(stringResource(R.string.job_preview_duration), current.durationMinutes?.let { durationText(it) } ?: "-")
            val reason = rejectionText(current.rejection)
            if (reason != null) {
                InfoBanner(text = reason, mark = Mark.Alert, container = MaterialTheme.colorScheme.errorContainer, content = MaterialTheme.colorScheme.onErrorContainer)
            }
        }

        Button(
            onClick = {
                val vehicleId = selectedVehicle
                val routeId = selectedRoute
                if (vehicleId != null && routeId != null) {
                    viewModel.acceptJob(OfferId(offerId), VehicleId(vehicleId), RouteId(routeId), effectiveManual)
                }
                onBack()
            },
            enabled = preview?.rejection == null && preview != null,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp).testTag(TestTags.JOB_ACCEPT),
        ) {
            Text(stringResource(R.string.job_accept))
        }
    }
}
