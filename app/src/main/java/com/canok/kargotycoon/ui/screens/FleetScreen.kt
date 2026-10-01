package com.canok.kargotycoon.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.canok.kargotycoon.R
import com.canok.kargotycoon.game.domain.GameCatalog
import com.canok.kargotycoon.game.domain.GameState
import com.canok.kargotycoon.ui.TestTags
import com.canok.kargotycoon.ui.components.EmptyState
import com.canok.kargotycoon.ui.components.LedgerRow
import com.canok.kargotycoon.ui.components.Mark
import com.canok.kargotycoon.ui.format.currentLocale
import com.canok.kargotycoon.ui.format.formatPercent
import com.canok.kargotycoon.ui.format.ownershipName
import com.canok.kargotycoon.ui.format.vehicleName
import com.canok.kargotycoon.ui.format.vehicleStatusName
import com.canok.kargotycoon.ui.state.Projections

@Composable
fun FleetScreen(game: GameState, catalog: GameCatalog, onOpenVehicle: (String) -> Unit, onOpenPurchase: () -> Unit) {
    val locale = currentLocale()
    LazyColumn(Modifier.fillMaxWidth().testTag(TestTags.FLEET_ROOT).padding(horizontal = 16.dp)) {
        item {
            Button(
                onClick = onOpenPurchase,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp).testTag(TestTags.FLEET_PURCHASE),
            ) {
                Text(stringResource(R.string.fleet_purchase_button))
            }
        }
        if (game.vehicles.isEmpty()) {
            item {
                EmptyState(mark = Mark.Fleet, title = stringResource(R.string.fleet_empty_title), body = stringResource(R.string.fleet_empty_body))
            }
        } else {
            items(game.vehicles, key = { it.id.value }) { vehicle ->
                val spec = Projections.specOf(catalog, vehicle)
                val driver = Projections.driverForVehicle(game, vehicle)
                val ownershipLabel = ownershipName(vehicle.ownership)
                val statusLabel = vehicleStatusName(vehicle.status)
                LedgerRow(
                    title = spec?.let { vehicleName(it) } ?: stringResource(R.string.unknown_vehicle),
                    subtitle = buildString {
                        append(ownershipLabel)
                        append(" · ")
                        append(statusLabel)
                        if (driver != null) {
                            append(" · ")
                            append(driver.name)
                        }
                    },
                    value = formatPercent(vehicle.conditionPercent, locale),
                    mark = Mark.Fleet,
                    onClick = { onOpenVehicle(vehicle.id.value) },
                    testTag = TestTags.vehicle(vehicle.id.value),
                )
            }
        }
    }
}
