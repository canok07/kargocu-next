package com.canok.kargotycoon.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.canok.kargotycoon.R
import com.canok.kargotycoon.game.domain.ActiveJob
import com.canok.kargotycoon.game.domain.GameCatalog
import com.canok.kargotycoon.game.domain.GameState
import com.canok.kargotycoon.game.domain.JobResult
import com.canok.kargotycoon.ui.format.currentLocale
import com.canok.kargotycoon.ui.format.dayOfInstant
import com.canok.kargotycoon.ui.format.durationText
import com.canok.kargotycoon.ui.format.formatGameMinutesOfDay
import com.canok.kargotycoon.ui.format.formatMoney
import com.canok.kargotycoon.ui.format.formatMoneySigned
import com.canok.kargotycoon.ui.format.formatInteger
import com.canok.kargotycoon.ui.format.locationName
import com.canok.kargotycoon.ui.format.progressFraction
import com.canok.kargotycoon.ui.format.riskName
import com.canok.kargotycoon.ui.format.vehicleName
import com.canok.kargotycoon.ui.state.Projections
import com.canok.kargotycoon.ui.theme.NumericStyle

@Composable
fun ActiveJobRow(job: ActiveJob, game: GameState, catalog: GameCatalog, modifier: Modifier = Modifier) {
    val locale = currentLocale()
    val route = catalog.routes.firstOrNull { it.id == job.routeId }
    val vehicle = Projections.vehicle(game, job.vehicleId)
    val spec = vehicle?.let { Projections.specOf(catalog, it) }
    val driver = job.driverId?.let { Projections.driver(game, it) }
    val fraction = progressFraction(game.gameTime.millis, job.startedAt.millis, job.completionAt.millis)
    val remainingMinutes = ((job.completionAt.millis - game.gameTime.millis) / 60_000L).coerceAtLeast(0L)
    Column(modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(
                        R.string.route_line,
                        route?.originId?.let { locationName(it) } ?: "?",
                        route?.destinationId?.let { locationName(it) } ?: "?",
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(
                        R.string.active_job_subtitle,
                        spec?.let { vehicleName(it) } ?: stringResource(R.string.unknown_vehicle),
                        driver?.name ?: stringResource(R.string.job_manual_driver),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(formatMoney(job.invoice.maximumRevenue.cents, locale), style = NumericStyle, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(8.dp))
        ProgressBar(fraction)
        Spacer(Modifier.height(4.dp))
        Row {
            Text(riskName(job.riskId), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text(
                stringResource(R.string.job_remaining, durationText(remainingMinutes)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Rule()
}

@Composable
fun HistoryRow(result: JobResult, modifier: Modifier = Modifier) {
    val locale = currentLocale()
    Column(modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(
                        R.string.history_title,
                        formatInteger(dayOfInstant(result.completedAt.millis).toLong(), locale),
                        formatGameMinutesOfDay(result.completedAt.millis),
                    ),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    stringResource(
                        R.string.history_breakdown,
                        formatMoney(result.revenue.cents, locale),
                        formatMoney(result.costs.cents, locale),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                formatMoneySigned(result.net.cents, locale),
                style = NumericStyle,
                color = if (result.net.cents >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        }
        Spacer(Modifier.height(6.dp))
        Row {
            TagChip(stringResource(if (result.onTime) R.string.history_on_time else R.string.history_late))
            Spacer(Modifier.width(6.dp))
            TagChip(stringResource(if (result.riskOccurred) R.string.history_risk else R.string.history_no_risk))
        }
    }
    Rule()
}
