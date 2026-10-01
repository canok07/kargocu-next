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
import com.canok.kargotycoon.ui.format.driverStatusName
import com.canok.kargotycoon.ui.format.formatInteger
import com.canok.kargotycoon.ui.format.tierName
import com.canok.kargotycoon.ui.format.vehicleName
import com.canok.kargotycoon.ui.state.Projections

@Composable
fun TeamScreen(game: GameState, catalog: GameCatalog, onOpenDriver: (String) -> Unit, onOpenHire: () -> Unit) {
    val locale = currentLocale()
    LazyColumn(Modifier.fillMaxWidth().testTag(TestTags.TEAM_ROOT).padding(horizontal = 16.dp)) {
        item {
            Button(onClick = onOpenHire, modifier = Modifier.fillMaxWidth().padding(top = 12.dp).testTag(TestTags.TEAM_HIRE)) {
                Text(stringResource(R.string.team_hire_button))
            }
        }
        if (game.drivers.isEmpty()) {
            item {
                EmptyState(mark = Mark.Team, title = stringResource(R.string.team_empty_title), body = stringResource(R.string.team_empty_body))
            }
        } else {
            items(game.drivers, key = { it.id.value }) { driver ->
                val tier = catalog.driverTiers.firstOrNull { it.id == driver.tierId }
                val vehicle = driver.assignedVehicleId?.let { Projections.vehicle(game, it) }
                val vehicleSpec = vehicle?.let { Projections.specOf(catalog, it) }
                val tierLabel = tier?.let { tierName(it) } ?: stringResource(R.string.value_none)
                val statusLabel = driverStatusName(driver.status)
                val vehicleLabel = vehicleSpec?.let { vehicleName(it) }
                LedgerRow(
                    title = driver.name,
                    subtitle = buildString {
                        append(tierLabel)
                        append(" · ")
                        append(statusLabel)
                        if (vehicleLabel != null) {
                            append(" · ")
                            append(vehicleLabel)
                        }
                    },
                    value = stringResource(R.string.team_driver_jobs, formatInteger(driver.experienceJobs.toLong(), locale)),
                    mark = Mark.Team,
                    onClick = { onOpenDriver(driver.id.value) },
                    testTag = TestTags.driver(driver.id.value),
                )
            }
        }
    }
}
