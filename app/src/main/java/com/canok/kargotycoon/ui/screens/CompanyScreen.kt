package com.canok.kargotycoon.ui.screens

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.canok.kargotycoon.R
import com.canok.kargotycoon.game.domain.GameCatalog
import com.canok.kargotycoon.game.domain.GameState
import com.canok.kargotycoon.game.engine.GameEngine
import com.canok.kargotycoon.ui.TestTags
import com.canok.kargotycoon.ui.components.KeyValueRow
import com.canok.kargotycoon.ui.components.ScreenBody
import com.canok.kargotycoon.ui.components.SectionHeading
import com.canok.kargotycoon.ui.components.TagChip
import com.canok.kargotycoon.ui.format.TargetProgress
import com.canok.kargotycoon.ui.format.currentLocale
import com.canok.kargotycoon.ui.format.formatInteger
import com.canok.kargotycoon.ui.format.regionName
import com.canok.kargotycoon.ui.state.Projections

private val companyEngine = GameEngine()

@Composable
fun CompanyScreen(game: GameState, catalog: GameCatalog) {
    val locale = currentLocale()
    val level = game.companyLevel(catalog)
    val owned = Projections.ownedVehicleCount(game)
    val unlocked = game.progression.unlockedRegionIds
    val nextLevel = catalog.progression.levels.firstOrNull { it.level > level }
    val targets = remember(game.revision) { companyEngine.nextTargets(game) }

    ScreenBody(Modifier.testTag(TestTags.COMPANY_ROOT)) {
        SectionHeading(stringResource(R.string.company_title))
        KeyValueRow(stringResource(R.string.dashboard_stage_label), stringResource(R.string.dashboard_stage_value, level))
        KeyValueRow(stringResource(R.string.company_completed), formatInteger(game.progression.completedJobs.toLong(), locale))
        KeyValueRow(stringResource(R.string.company_on_time), formatInteger(game.progression.onTimeJobs.toLong(), locale))
        KeyValueRow(stringResource(R.string.company_owned), formatInteger(owned.toLong(), locale))
        KeyValueRow(stringResource(R.string.company_employees), formatInteger(game.drivers.size.toLong(), locale))
        KeyValueRow(
            stringResource(R.string.company_regions),
            stringResource(R.string.company_regions_value, unlocked.size, catalog.regions.size),
        )

        SectionHeading(stringResource(R.string.company_next_stage))
        if (nextLevel == null) {
            Text(stringResource(R.string.company_max_stage), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text(stringResource(R.string.company_next_stage_name, nextLevel.level), style = MaterialTheme.typography.bodyMedium)
            KeyValueRow(
                stringResource(R.string.company_requirement_jobs),
                stringResource(
                    R.string.target_progress,
                    formatInteger(game.progression.completedJobs.toLong(), locale),
                    formatInteger(nextLevel.minimumCompletedJobs.toLong(), locale),
                ),
            )
            KeyValueRow(
                stringResource(R.string.company_requirement_vehicles),
                stringResource(
                    R.string.target_progress,
                    formatInteger(owned.toLong(), locale),
                    formatInteger(nextLevel.minimumOwnedVehicles.toLong(), locale),
                ),
            )
            KeyValueRow(
                stringResource(R.string.company_requirement_drivers),
                stringResource(
                    R.string.target_progress,
                    formatInteger(game.drivers.size.toLong(), locale),
                    formatInteger(nextLevel.minimumDrivers.toLong(), locale),
                ),
            )
            KeyValueRow(
                stringResource(R.string.company_requirement_day),
                stringResource(
                    R.string.target_progress,
                    formatInteger(game.gameDay.toLong(), locale),
                    formatInteger(nextLevel.minimumGameDay.toLong(), locale),
                ),
            )
        }

        SectionHeading(stringResource(R.string.company_regions_title))
        catalog.regions.forEach { region ->
            val isUnlocked = region.id in unlocked
            Row(Modifier, verticalAlignment = Alignment.CenterVertically) {
                Text(regionName(region.id), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                TagChip(stringResource(if (isUnlocked) R.string.map_region_unlocked else R.string.map_region_locked))
            }
            if (!isUnlocked) {
                KeyValueRow(
                    stringResource(R.string.map_condition_jobs),
                    stringResource(
                        R.string.target_progress,
                        formatInteger(game.progression.completedJobs.toLong(), locale),
                        formatInteger(region.minimumCompletedJobs.toLong(), locale),
                    ),
                )
                KeyValueRow(
                    stringResource(R.string.map_condition_vehicles),
                    stringResource(
                        R.string.target_progress,
                        formatInteger(owned.toLong(), locale),
                        formatInteger(region.minimumOwnedVehicles.toLong(), locale),
                    ),
                )
                KeyValueRow(
                    stringResource(R.string.map_condition_day),
                    stringResource(
                        R.string.target_progress,
                        formatInteger(game.gameDay.toLong(), locale),
                        formatInteger(region.minimumGameDay.toLong(), locale),
                    ),
                )
            }
        }

        SectionHeading(stringResource(R.string.dashboard_targets_title))
        if (targets.isEmpty()) {
            Text(stringResource(R.string.dashboard_targets_none), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            targets.forEach { target -> TargetProgress(target, catalog) }
        }
    }
}
