package com.canok.kargotycoon.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.canok.kargotycoon.R
import com.canok.kargotycoon.game.domain.GameCatalog
import com.canok.kargotycoon.game.domain.GameState
import com.canok.kargotycoon.ui.TestTags
import com.canok.kargotycoon.ui.components.ActiveJobRow
import com.canok.kargotycoon.ui.components.ChoiceChip
import com.canok.kargotycoon.ui.components.EmptyState
import com.canok.kargotycoon.ui.components.HistoryRow
import com.canok.kargotycoon.ui.components.LedgerRow
import com.canok.kargotycoon.ui.components.Mark
import com.canok.kargotycoon.ui.components.Rule
import com.canok.kargotycoon.ui.components.SectionHeading
import com.canok.kargotycoon.ui.format.distanceText
import com.canok.kargotycoon.ui.format.locationName
import com.canok.kargotycoon.ui.format.packageName
import com.canok.kargotycoon.ui.format.riskName
import com.canok.kargotycoon.ui.format.weightText

@Composable
fun JobsScreen(game: GameState, catalog: GameCatalog, onOpenOffer: (String) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().testTag(TestTags.JOBS_ROOT)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChoiceChip(tab == 0, stringResource(R.string.jobs_tab_offers), { tab = 0 }, testTag = TestTags.JOBS_TAB_OFFERS)
            ChoiceChip(tab == 1, stringResource(R.string.jobs_tab_deliveries), { tab = 1 }, testTag = TestTags.JOBS_TAB_DELIVERIES)
            Spacer(Modifier.weight(1f))
            Text(
                pluralStringResource(R.plurals.jobs_offer_count, game.offers.size, game.offers.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Rule()
        if (tab == 0) {
            LazyColumn(Modifier.fillMaxSize().testTag(TestTags.JOBS_OFFERS_LIST).padding(horizontal = 16.dp)) {
                if (game.offers.isEmpty()) {
                    item {
                        EmptyState(
                            mark = Mark.Parcel,
                            title = stringResource(R.string.jobs_empty_title),
                            body = stringResource(R.string.jobs_empty_body),
                            modifier = Modifier.testTag(TestTags.JOBS_EMPTY),
                        )
                    }
                } else {
                    items(game.offers, key = { it.id.value }) { offer ->
                        val packageType = catalog.packageTypes.firstOrNull { it.id == offer.packageTypeId }
                        val route = catalog.routes.firstOrNull { it.id == offer.routeOptions.firstOrNull() }
                        LedgerRow(
                            title = stringResource(
                                R.string.route_line,
                                locationName(offer.originId),
                                locationName(offer.destinationId),
                            ),
                            subtitle = stringResource(
                                R.string.jobs_offer_subtitle,
                                packageType?.let { packageName(it) } ?: "-",
                                offer.count,
                                weightText(offer.totalWeightGrams),
                                riskName(offer.riskId),
                            ),
                            value = route?.let { distanceText(it.distanceMeters) },
                            mark = Mark.Parcel,
                            onClick = { onOpenOffer(offer.id.value) },
                            testTag = TestTags.offer(offer.id.value),
                        )
                    }
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                item { SectionHeading(stringResource(R.string.jobs_active_title), Modifier.padding(top = 12.dp)) }
                if (game.activeJobs.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.jobs_active_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                } else {
                    items(game.activeJobs, key = { it.id.value }) { job -> ActiveJobRow(job, game, catalog) }
                }
                item { SectionHeading(stringResource(R.string.jobs_history_title), Modifier.padding(top = 16.dp)) }
                if (game.completedJobs.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.jobs_history_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                } else {
                    items(game.completedJobs.reversed(), key = { it.jobId.value }) { result -> HistoryRow(result) }
                }
            }
        }
    }
}
