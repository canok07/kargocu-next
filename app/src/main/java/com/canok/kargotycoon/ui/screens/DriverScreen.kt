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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.canok.kargotycoon.R
import com.canok.kargotycoon.game.domain.DriverId
import com.canok.kargotycoon.game.domain.DriverStatus
import com.canok.kargotycoon.game.domain.GameCatalog
import com.canok.kargotycoon.game.domain.GameState
import com.canok.kargotycoon.game.engine.GameResult
import com.canok.kargotycoon.ui.TestTags
import com.canok.kargotycoon.ui.components.ConfirmDialog
import com.canok.kargotycoon.ui.components.EmptyState
import com.canok.kargotycoon.ui.components.InfoBanner
import com.canok.kargotycoon.ui.components.KeyValueRow
import com.canok.kargotycoon.ui.components.Mark
import com.canok.kargotycoon.ui.components.ScreenBody
import com.canok.kargotycoon.ui.components.SectionHeading
import com.canok.kargotycoon.ui.format.currentLocale
import com.canok.kargotycoon.ui.format.driverStatusName
import com.canok.kargotycoon.ui.format.formatInteger
import com.canok.kargotycoon.ui.format.formatMoney
import com.canok.kargotycoon.ui.format.rejectionText
import com.canok.kargotycoon.ui.format.tierName
import com.canok.kargotycoon.ui.format.vehicleName
import com.canok.kargotycoon.ui.state.Projections
import com.canok.kargotycoon.ui.state.driverSeverance
import com.canok.kargotycoon.viewmodel.GameViewModel

@Composable
fun DriverScreen(
    driverId: String,
    game: GameState,
    catalog: GameCatalog,
    viewModel: GameViewModel,
    onBack: () -> Unit,
) {
    val locale = currentLocale()
    val driver = Projections.driver(game, DriverId(driverId))
    if (driver == null) {
        ScreenBody(Modifier.testTag(TestTags.DRIVER_ROOT)) {
            EmptyState(
                mark = Mark.Team,
                title = stringResource(R.string.driver_missing_title),
                body = stringResource(R.string.driver_missing_body),
                action = { OutlinedButton(onClick = onBack) { Text(stringResource(R.string.action_back)) } },
            )
        }
        return
    }

    val tier = catalog.driverTiers.firstOrNull { it.id == driver.tierId }
    val vehicle = driver.assignedVehicleId?.let { Projections.vehicle(game, it) }
    val vehicleSpec = vehicle?.let { Projections.specOf(catalog, it) }
    val busy = driver.status != DriverStatus.AVAILABLE || game.activeJobs.any { it.driverId == driver.id }

    var confirmingFire by remember { mutableStateOf(false) }
    var firePreview by remember(confirmingFire) { mutableStateOf<GameResult?>(null) }
    val actionPending by viewModel.actionPending.collectAsStateWithLifecycle()
    LaunchedEffect(confirmingFire, game.revision) {
        firePreview = null
        firePreview = if (confirmingFire) viewModel.previewFire(driver.id) else null
    }

    ScreenBody(Modifier.testTag(TestTags.DRIVER_ROOT)) {
        SectionHeading(driver.name)
        KeyValueRow(stringResource(R.string.driver_tier), tier?.let { tierName(it) } ?: stringResource(R.string.value_none))
        KeyValueRow(stringResource(R.string.driver_status), driverStatusName(driver.status))
        KeyValueRow(stringResource(R.string.driver_experience), pluralStringResource(R.plurals.team_driver_jobs, driver.experienceJobs, formatInteger(driver.experienceJobs.toLong(), locale)))
        KeyValueRow(stringResource(R.string.driver_vehicle), vehicleSpec?.let { vehicleName(it) } ?: stringResource(R.string.value_none))

        if (busy) {
            InfoBanner(text = stringResource(R.string.driver_busy_note), mark = Mark.Clock)
        }

        SectionHeading(stringResource(R.string.vehicle_actions))
        OutlinedButton(
            onClick = { viewModel.unassignDriver(driver.id) },
            enabled = driver.assignedVehicleId != null && !busy && !actionPending,
            modifier = Modifier.fillMaxWidth().testTag(TestTags.DRIVER_UNASSIGN),
        ) { Text(stringResource(R.string.driver_unassign)) }
        Button(
            onClick = { confirmingFire = true },
            enabled = !busy && !actionPending,
            modifier = Modifier.fillMaxWidth().testTag(TestTags.DRIVER_FIRE),
        ) { Text(stringResource(R.string.driver_fire)) }
    }

    if (confirmingFire) {
        val severance = firePreview.driverSeverance()
        val costText = severance?.let { formatMoney(it.cents, locale) } ?: stringResource(R.string.value_calculating)
        ConfirmDialog(
            title = stringResource(R.string.fire_confirm_title),
            message = rejectionText((firePreview as? GameResult.Rejected)?.reason) ?: stringResource(R.string.fire_confirm_body, driver.name, costText),
            confirmLabel = stringResource(R.string.driver_fire),
            cancelLabel = stringResource(R.string.action_cancel),
            destructive = true,
            confirmEnabled = severance != null && !busy && !actionPending,
            onConfirm = { viewModel.fireDriver(driver.id, onBack); confirmingFire = false },
            onDismiss = { confirmingFire = false },
        )
    }
}
