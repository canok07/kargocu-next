package com.canok.kargotycoon.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.canok.kargotycoon.R
import com.canok.kargotycoon.game.domain.GameState
import com.canok.kargotycoon.ui.TestTags
import com.canok.kargotycoon.ui.components.ChoiceChip
import com.canok.kargotycoon.ui.components.ConfirmDialog
import com.canok.kargotycoon.ui.components.InfoBanner
import com.canok.kargotycoon.ui.components.Mark
import com.canok.kargotycoon.ui.components.ScreenBody
import com.canok.kargotycoon.ui.components.SectionHeading
import com.canok.kargotycoon.ui.feedback.LocalKargoFeedback

@Composable
private fun ToggleRow(
    title: String,
    hint: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String,
) {
    val feedback = LocalKargoFeedback.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = { feedback.tick(); onCheckedChange(it) },
            modifier = Modifier.testTag(testTag),
        )
    }
}

@Composable
fun SettingsScreen(
    game: GameState,
    onLanguage: (String) -> Unit,
    onSound: (Boolean) -> Unit,
    onHaptics: (Boolean) -> Unit,
    onTutorial: (Boolean) -> Unit,
    onReset: () -> Unit,
) {
    var confirmingReset by remember { mutableStateOf(false) }
    val settings = game.settings

    ScreenBody(Modifier.testTag(TestTags.SETTINGS_ROOT)) {
        SectionHeading(stringResource(R.string.settings_language))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceChip(settings.languageTag == "tr", stringResource(R.string.language_tr), { onLanguage("tr") }, testTag = TestTags.SETTINGS_LANG_TR)
            ChoiceChip(settings.languageTag == "en", stringResource(R.string.language_en), { onLanguage("en") }, testTag = TestTags.SETTINGS_LANG_EN)
        }

        SectionHeading(stringResource(R.string.settings_feedback))
        ToggleRow(
            title = stringResource(R.string.settings_sound),
            hint = stringResource(R.string.settings_sound_hint),
            checked = settings.soundEnabled,
            onCheckedChange = onSound,
            testTag = TestTags.SETTINGS_SOUND,
        )
        ToggleRow(
            title = stringResource(R.string.settings_haptics),
            hint = stringResource(R.string.settings_haptics_hint),
            checked = settings.hapticsEnabled,
            onCheckedChange = onHaptics,
            testTag = TestTags.SETTINGS_HAPTICS,
        )

        SectionHeading(stringResource(R.string.settings_tutorial_section))
        ToggleRow(
            title = stringResource(R.string.settings_tutorial_show),
            hint = stringResource(R.string.settings_tutorial_hint),
            checked = !game.tutorial.dismissed,
            onCheckedChange = onTutorial,
            testTag = TestTags.SETTINGS_TUTORIAL,
        )

        SectionHeading(stringResource(R.string.settings_data))
        InfoBanner(text = stringResource(R.string.settings_storage_note), mark = Mark.Info)
        Button(
            onClick = { confirmingReset = true },
            modifier = Modifier.fillMaxWidth().testTag(TestTags.SETTINGS_RESET),
        ) {
            Text(stringResource(R.string.settings_reset))
        }
    }

    if (confirmingReset) {
        ConfirmDialog(
            title = stringResource(R.string.reset_confirm_title),
            message = stringResource(R.string.reset_confirm_body),
            confirmLabel = stringResource(R.string.reset_confirm_action),
            cancelLabel = stringResource(R.string.action_cancel),
            destructive = true,
            onConfirm = { confirmingReset = false; onReset() },
            onDismiss = { confirmingReset = false },
        )
    }
}
