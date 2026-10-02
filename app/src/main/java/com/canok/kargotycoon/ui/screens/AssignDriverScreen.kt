package com.canok.kargotycoon.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.canok.kargotycoon.R
import com.canok.kargotycoon.game.domain.DriverId
import com.canok.kargotycoon.game.domain.DriverStatus
import com.canok.kargotycoon.game.domain.GameState
import com.canok.kargotycoon.ui.TestTags
import com.canok.kargotycoon.ui.components.EmptyState
import com.canok.kargotycoon.ui.components.LedgerRow
import com.canok.kargotycoon.ui.components.Mark
import com.canok.kargotycoon.ui.format.currentLocale
import com.canok.kargotycoon.ui.format.formatInteger
import com.canok.kargotycoon.ui.state.Projections

@Composable
fun AssignDriverScreen(vehicleId: String, game: GameState, onAssign: (DriverId) -> Unit, onBack: () -> Unit) {
    val locale = currentLocale()
    val candidates = game.drivers.filter { it.status == DriverStatus.AVAILABLE && it.assignedVehicleId == null }
    LazyColumn(Modifier.padding(horizontal = 16.dp).testTag(TestTags.TEAM_ROOT)) {
        if (candidates.isEmpty()) {
            item {
                EmptyState(
                    mark = Mark.Team,
                    title = stringResource(R.string.assign_empty_title),
                    body = stringResource(R.string.assign_empty_body),
                )
            }
        } else {
            items(candidates, key = { it.id.value }) { driver ->
                LedgerRow(
                    title = driver.name,
                    subtitle = pluralStringResource(R.plurals.team_driver_jobs, driver.experienceJobs, formatInteger(driver.experienceJobs.toLong(), locale)),
                    mark = Mark.Team,
                    onClick = { onAssign(driver.id) },
                    testTag = TestTags.driver(driver.id.value),
                )
            }
        }
        item {
            Text(
                stringResource(R.string.assign_note),
                modifier = Modifier.padding(vertical = 16.dp),
            )
        }
    }
}
