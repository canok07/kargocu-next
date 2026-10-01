package com.canok.kargotycoon.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.canok.kargotycoon.R
import com.canok.kargotycoon.game.domain.GameCatalog
import com.canok.kargotycoon.game.domain.GameState
import com.canok.kargotycoon.game.domain.Money
import com.canok.kargotycoon.game.domain.VehicleId
import com.canok.kargotycoon.ui.TestTags
import com.canok.kargotycoon.ui.components.ConfirmDialog
import com.canok.kargotycoon.ui.components.EmptyState
import com.canok.kargotycoon.ui.components.InfoBanner
import com.canok.kargotycoon.ui.components.KeyValueRow
import com.canok.kargotycoon.ui.components.Mark
import com.canok.kargotycoon.ui.components.ScreenBody
import com.canok.kargotycoon.ui.components.SectionHeading
import com.canok.kargotycoon.ui.format.capabilityName
import com.canok.kargotycoon.ui.format.currentLocale
import com.canok.kargotycoon.ui.format.distanceText
import com.canok.kargotycoon.ui.format.formatInteger
import com.canok.kargotycoon.ui.format.formatMoney
import com.canok.kargotycoon.ui.format.formatPercent
import com.canok.kargotycoon.ui.format.ownershipName
import com.canok.kargotycoon.ui.format.vehicleName
import com.canok.kargotycoon.ui.format.vehicleStatusName
import com.canok.kargotycoon.ui.format.weightText
import com.canok.kargotycoon.ui.state.Projections
import com.canok.kargotycoon.ui.state.vehicleMaintenanceCost
import com.canok.kargotycoon.ui.state.vehicleRepairCost
import com.canok.kargotycoon.ui.state.vehicleSaleProceeds
import com.canok.kargotycoon.viewmodel.GameViewModel

private enum class VehicleAction { Repair, Maintain, Sell }

@Composable
fun VehicleScreen(
    vehicleId: String,
    game: GameState,
    catalog: GameCatalog,
    viewModel: GameViewModel,
    onOpenAssign: () -> Unit,
    onBack: () -> Unit,
) {
    val locale = currentLocale()
    val vehicle = Projections.vehicle(game, VehicleId(vehicleId))
    if (vehicle == null) {
        ScreenBody(Modifier.testTag(TestTags.VEHICLE_ROOT)) {
            EmptyState(
                mark = Mark.Fleet,
                title = stringResource(R.string.vehicle_missing_title),
                body = stringResource(R.string.vehicle_missing_body),
                action = { OutlinedButton(onClick = onBack) { Text(stringResource(R.string.action_back)) } },
            )
        }
        return
    }

    val spec = Projections.specOf(catalog, vehicle)
    val driver = Projections.driverForVehicle(game, vehicle)
    var action by remember { mutableStateOf<VehicleAction?>(null) }
    var price by remember { mutableStateOf<Money?>(null) }

    LaunchedEffect(action, game.revision) {
        price = when (action) {
            VehicleAction.Repair -> viewModel.previewRepair(vehicle.id).vehicleRepairCost()
            VehicleAction.Maintain -> viewModel.previewMaintain(vehicle.id).vehicleMaintenanceCost()
            VehicleAction.Sell -> viewModel.previewSell(vehicle.id).vehicleSaleProceeds()
            null -> null
        }
    }

    val canRepair = Projections.canRepair(vehicle, game)
    val canMaintain = Projections.canMaintain(vehicle, game, catalog)
    val canSell = Projections.canSell(vehicle, game)
    val busy = vehicle.status != com.canok.kargotycoon.game.domain.VehicleStatus.AVAILABLE

    ScreenBody(Modifier.testTag(TestTags.VEHICLE_ROOT)) {
        SectionHeading(spec?.let { vehicleName(it) } ?: stringResource(R.string.unknown_vehicle))
        KeyValueRow(stringResource(R.string.vehicle_ownership), ownershipName(vehicle.ownership))
        KeyValueRow(stringResource(R.string.vehicle_status), vehicleStatusName(vehicle.status))
        KeyValueRow(
            stringResource(R.string.vehicle_capacity),
            stringResource(
                R.string.vehicle_capacity_value,
                formatInteger((spec?.capacityCount ?: 0).toLong(), locale),
                weightText(spec?.capacityGrams ?: 0L),
            ),
        )
        KeyValueRow(
            stringResource(R.string.vehicle_capabilities),
            spec?.capabilities?.takeIf { it.isNotEmpty() }?.map { capabilityName(it) }?.joinToString(", ") ?: stringResource(R.string.value_none),
        )
        KeyValueRow(stringResource(R.string.vehicle_condition), formatPercent(vehicle.conditionPercent, locale))
        KeyValueRow(stringResource(R.string.vehicle_mileage), distanceText(vehicle.mileageMeters))
        KeyValueRow(stringResource(R.string.vehicle_earned), formatMoney(vehicle.totalEarned.cents, locale))
        KeyValueRow(stringResource(R.string.vehicle_costs), formatMoney(vehicle.totalOperatingCosts.cents, locale))
        KeyValueRow(stringResource(R.string.vehicle_driver), driver?.name ?: stringResource(R.string.value_none))

        if (busy) {
            InfoBanner(
                text = stringResource(R.string.vehicle_busy_note),
                mark = Mark.Clock,
            )
        }
        if (vehicle.ownership == com.canok.kargotycoon.game.domain.Ownership.RENTAL) {
            InfoBanner(text = stringResource(R.string.vehicle_rental_note), mark = Mark.Info)
        }

        SectionHeading(stringResource(R.string.vehicle_actions))
        Button(
            onClick = { action = VehicleAction.Repair },
            enabled = canRepair,
            modifier = Modifier.fillMaxWidth().testTag(TestTags.VEHICLE_REPAIR),
        ) { Text(stringResource(R.string.vehicle_repair)) }
        OutlinedButton(
            onClick = { action = VehicleAction.Maintain },
            enabled = canMaintain,
            modifier = Modifier.fillMaxWidth().testTag(TestTags.VEHICLE_MAINTAIN),
        ) { Text(stringResource(R.string.vehicle_maintain)) }
        OutlinedButton(
            onClick = { action = VehicleAction.Sell },
            enabled = canSell,
            modifier = Modifier.fillMaxWidth().testTag(TestTags.VEHICLE_SELL),
        ) { Text(stringResource(R.string.vehicle_sell)) }
        if (driver == null) {
            OutlinedButton(
                onClick = onOpenAssign,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().testTag(TestTags.VEHICLE_ASSIGN_DRIVER),
            ) { Text(stringResource(R.string.vehicle_assign_driver)) }
        }
    }

    val currentAction = action
    if (currentAction != null) {
        val costText = price?.let { formatMoney(it.cents, locale) } ?: stringResource(R.string.value_calculating)
        val titleRes = when (currentAction) {
            VehicleAction.Repair -> R.string.repair_confirm_title
            VehicleAction.Maintain -> R.string.maintain_confirm_title
            VehicleAction.Sell -> R.string.sell_confirm_title
        }
        val bodyRes = when (currentAction) {
            VehicleAction.Repair -> R.string.repair_confirm_body
            VehicleAction.Maintain -> R.string.maintain_confirm_body
            VehicleAction.Sell -> R.string.sell_confirm_body
        }
        ConfirmDialog(
            title = stringResource(titleRes),
            message = stringResource(bodyRes, costText),
            confirmLabel = when (currentAction) {
                VehicleAction.Repair -> stringResource(R.string.vehicle_repair)
                VehicleAction.Maintain -> stringResource(R.string.vehicle_maintain)
                VehicleAction.Sell -> stringResource(R.string.vehicle_sell)
            },
            cancelLabel = stringResource(R.string.action_cancel),
            destructive = currentAction == VehicleAction.Sell,
            onConfirm = {
                when (currentAction) {
                    VehicleAction.Repair -> viewModel.repairVehicle(vehicle.id)
                    VehicleAction.Maintain -> viewModel.maintainVehicle(vehicle.id)
                    VehicleAction.Sell -> { viewModel.sellVehicle(vehicle.id); onBack() }
                }
                action = null
            },
            onDismiss = { action = null },
        )
    }
}
