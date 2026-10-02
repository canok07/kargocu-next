package com.canok.kargotycoon.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.canok.kargotycoon.R
import com.canok.kargotycoon.game.domain.GameCatalog
import com.canok.kargotycoon.game.domain.GameState
import com.canok.kargotycoon.game.engine.GameEngine
import com.canok.kargotycoon.ui.TestTags
import com.canok.kargotycoon.ui.components.ActiveJobRow
import com.canok.kargotycoon.ui.components.ConfirmDialog
import com.canok.kargotycoon.ui.components.KargoMark
import com.canok.kargotycoon.ui.components.Mark
import com.canok.kargotycoon.ui.components.ScreenBody
import com.canok.kargotycoon.ui.components.SectionHeading
import com.canok.kargotycoon.ui.components.InfoBanner
import com.canok.kargotycoon.ui.components.KeyValueRow
import com.canok.kargotycoon.ui.components.StatTile
import com.canok.kargotycoon.ui.components.EmptyState
import com.canok.kargotycoon.ui.format.TargetProgress
import com.canok.kargotycoon.ui.format.currentLocale
import com.canok.kargotycoon.ui.format.dayOfInstant
import com.canok.kargotycoon.ui.format.formatGameMinutesOfDay
import com.canok.kargotycoon.ui.format.formatInteger
import com.canok.kargotycoon.ui.format.formatMoney
import com.canok.kargotycoon.ui.format.formatMoneySigned
import com.canok.kargotycoon.ui.theme.NumericStyle

private val dashboardEngine = GameEngine()

@Composable
fun DashboardScreen(
    game: GameState,
    catalog: GameCatalog,
    onEndDay: () -> Unit,
    onOpenJobs: () -> Unit,
    onOpenFleet: () -> Unit,
) {
    val locale = currentLocale()
    var confirmingEndDay by remember { mutableStateOf(false) }
    val targets = remember(game.revision) { dashboardEngine.nextTargets(game) }
    val lastSummary = game.dailySummaries.lastOrNull()
    val lastResult = game.completedJobs.lastOrNull()

    ScreenBody(Modifier.testTag(TestTags.DASHBOARD_ROOT)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(
                label = stringResource(R.string.dashboard_balance),
                value = formatMoney(game.money.cents, locale),
                modifier = Modifier.weight(1f).testTag(TestTags.DASHBOARD_BALANCE),
                accent = true,
            )
            StatTile(
                label = stringResource(R.string.dashboard_day),
                value = formatInteger(dayOfInstant(game.gameTime.millis).toLong(), locale),
                modifier = Modifier.weight(1f).testTag(TestTags.DASHBOARD_DAY),
                hint = formatGameMinutesOfDay(game.gameTime.millis),
            )
        }

        SectionHeading(stringResource(R.string.dashboard_stage))
        KeyValueRow(stringResource(R.string.dashboard_stage_label), stringResource(R.string.dashboard_stage_value, game.companyLevel(catalog)))

        InfoBanner(
            text = stringResource(R.string.dashboard_time_hint),
            mark = Mark.Clock,
        )

        Button(
            onClick = { confirmingEndDay = true },
            modifier = Modifier.fillMaxWidth().testTag(TestTags.DASHBOARD_END_DAY),
        ) {
            KargoMark(Mark.Advance, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onPrimary)
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.dashboard_end_day))
        }

        SectionHeading(stringResource(R.string.dashboard_active_title))
        if (game.activeJobs.isEmpty()) {
            EmptyState(
                mark = Mark.Route,
                title = stringResource(R.string.dashboard_no_active_title),
                body = stringResource(R.string.dashboard_no_active_body),
                action = { OutlinedButton(onClick = onOpenJobs) { Text(stringResource(R.string.dashboard_go_jobs)) } },
            )
        } else {
            game.activeJobs.forEach { job -> ActiveJobRow(job, game, catalog) }
        }

        SectionHeading(stringResource(R.string.dashboard_daily_title))
        if (lastSummary == null) {
            Text(stringResource(R.string.dashboard_daily_none), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text(
                pluralStringResource(
                    R.plurals.dashboard_daily_body,
                    lastSummary.completedJobs,
                    formatMoneySigned(lastSummary.revenue.cents, locale),
                    formatMoney(lastSummary.costs.cents, locale),
                    lastSummary.completedJobs,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        SectionHeading(stringResource(R.string.dashboard_last_net_title))
        if (lastResult == null) {
            Text(stringResource(R.string.dashboard_no_completed), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text(
                stringResource(R.string.dashboard_last_net_value, formatMoneySigned(lastResult.net.cents, locale)),
                style = NumericStyle,
                color = if (lastResult.net.cents >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        }

        SectionHeading(stringResource(R.string.dashboard_targets_title))
        if (targets.isEmpty()) {
            Text(stringResource(R.string.dashboard_targets_none), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            targets.forEach { target -> TargetProgress(target, catalog) }
        }

        OutlinedButton(onClick = onOpenFleet, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.dashboard_go_fleet))
        }
    }

    if (confirmingEndDay) {
        ConfirmDialog(
            title = stringResource(R.string.end_day_confirm_title),
            message = stringResource(R.string.end_day_confirm_body),
            confirmLabel = stringResource(R.string.dashboard_end_day),
            cancelLabel = stringResource(R.string.action_cancel),
            onConfirm = { confirmingEndDay = false; onEndDay() },
            onDismiss = { confirmingEndDay = false },
        )
    }
}
