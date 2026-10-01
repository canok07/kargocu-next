package com.canok.kargotycoon.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.canok.kargotycoon.game.domain.VehicleSpec
import com.canok.kargotycoon.ui.TestTags
import com.canok.kargotycoon.ui.components.ConfirmDialog
import com.canok.kargotycoon.ui.components.EmptyState
import com.canok.kargotycoon.ui.components.LedgerRow
import com.canok.kargotycoon.ui.components.Mark
import com.canok.kargotycoon.ui.format.capabilityName
import com.canok.kargotycoon.ui.format.currentLocale
import com.canok.kargotycoon.ui.format.formatInteger
import com.canok.kargotycoon.ui.format.formatMoney
import com.canok.kargotycoon.ui.format.vehicleName
import com.canok.kargotycoon.ui.format.weightText
import com.canok.kargotycoon.ui.state.Projections
import com.canok.kargotycoon.viewmodel.GameViewModel

@Composable
fun PurchaseScreen(game: GameState, catalog: GameCatalog, viewModel: GameViewModel, onBack: () -> Unit) {
    val locale = currentLocale()
    val level = game.companyLevel(catalog)
    val specs = remember(game.revision) { Projections.purchasableSpecs(catalog, game) }
    var confirming by remember { mutableStateOf<VehicleSpec?>(null) }

    LazyColumn(Modifier.fillMaxWidth().testTag(TestTags.PURCHASE_ROOT).padding(horizontal = 16.dp)) {
        if (specs.isEmpty()) {
            item { EmptyState(mark = Mark.Fleet, title = stringResource(R.string.purchase_empty_title), body = stringResource(R.string.purchase_empty_body)) }
        } else {
            items(specs, key = { it.id.value }) { spec ->
                val levelOk = level >= spec.minimumCompanyLevel
                val affordable = game.money >= spec.purchasePrice
                val note = when {
                    !levelOk -> stringResource(R.string.purchase_requires_level, spec.minimumCompanyLevel)
                    !affordable -> stringResource(R.string.purchase_need_funds)
                    else -> stringResource(R.string.purchase_ready)
                }
                val capabilitiesLabel = spec.capabilities.takeIf { it.isNotEmpty() }?.map { capabilityName(it) }?.joinToString(", ")
                    ?: stringResource(R.string.value_none)
                LedgerRow(
                    title = vehicleName(spec),
                    subtitle = stringResource(
                        R.string.vehicle_capacity_value,
                        formatInteger(spec.capacityCount.toLong(), locale),
                        weightText(spec.capacityGrams),
                    ) + " · " + capabilitiesLabel + " · " + note,
                    value = formatMoney(spec.purchasePrice.cents, locale),
                    mark = Mark.Fleet,
                    enabled = levelOk,
                    onClick = if (levelOk) ({ confirming = spec }) else null,
                    testTag = TestTags.buySpec(spec.id.value),
                )
            }
        }
        item {
            Text(
                stringResource(R.string.purchase_footer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp),
            )
        }
    }

    val target = confirming
    if (target != null) {
        ConfirmDialog(
            title = stringResource(R.string.purchase_confirm_title),
            message = stringResource(R.string.purchase_confirm_body, vehicleName(target), formatMoney(target.purchasePrice.cents, locale)),
            confirmLabel = stringResource(R.string.purchase_action),
            cancelLabel = stringResource(R.string.action_cancel),
            onConfirm = { viewModel.purchaseVehicle(target.id); confirming = null; onBack() },
            onDismiss = { confirming = null },
        )
    }
}
