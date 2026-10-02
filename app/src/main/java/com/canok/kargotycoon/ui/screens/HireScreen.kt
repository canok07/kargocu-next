package com.canok.kargotycoon.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.canok.kargotycoon.R
import com.canok.kargotycoon.game.domain.GameCatalog
import com.canok.kargotycoon.game.domain.GameState
import com.canok.kargotycoon.ui.TestTags
import com.canok.kargotycoon.ui.components.LedgerRow
import com.canok.kargotycoon.ui.components.Mark
import com.canok.kargotycoon.ui.components.ScreenBody
import com.canok.kargotycoon.ui.components.SectionHeading
import com.canok.kargotycoon.ui.format.currentLocale
import com.canok.kargotycoon.ui.format.formatMoney
import com.canok.kargotycoon.ui.format.tierName
import com.canok.kargotycoon.viewmodel.GameViewModel

@Composable
fun HireScreen(game: GameState, catalog: GameCatalog, viewModel: GameViewModel, onBack: () -> Unit) {
    val locale = currentLocale()
    val level = game.companyLevel(catalog)
    var selectedTier by rememberSaveable { mutableStateOf<String?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    val tier = catalog.driverTiers.firstOrNull { it.id.value == selectedTier }
    val levelOk = tier == null || level >= tier.minimumCompanyLevel

    ScreenBody {
        SectionHeading(stringResource(R.string.hire_tier_title))
        catalog.driverTiers.forEach { candidate ->
            val available = level >= candidate.minimumCompanyLevel
            LedgerRow(
                title = tierName(candidate),
                subtitle = stringResource(
                    R.string.hire_tier_subtitle,
                    formatMoney(candidate.dailyWage.cents, locale),
                    formatMoney(candidate.hiringCost.cents, locale),
                ) + if (!available) " · " + stringResource(R.string.hire_requires_level, candidate.minimumCompanyLevel) else "",
                value = if (candidate.id.value == selectedTier) stringResource(R.string.choice_selected) else null,
                mark = Mark.Team,
                enabled = available,
                onClick = if (available) ({ selectedTier = candidate.id.value }) else null,
                testTag = TestTags.hireTier(candidate.id.value),
            )
        }

        SectionHeading(stringResource(R.string.hire_name_title))
        OutlinedTextField(
            value = name,
            onValueChange = { if (it.length <= 40) name = it },
            label = { Text(stringResource(R.string.hire_name_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag(TestTags.HIRE_NAME),
        )
        if (name.isBlank()) {
            Text(stringResource(R.string.hire_name_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        TextButton(
            onClick = {
                val chosen = tier ?: return@TextButton
                viewModel.hireDriver(chosen.id, name.trim())
                onBack()
            },
            enabled = tier != null && name.isNotBlank() && levelOk,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp).testTag(TestTags.HIRE_CONFIRM),
        ) {
            Text(stringResource(R.string.hire_action))
        }
    }
}
