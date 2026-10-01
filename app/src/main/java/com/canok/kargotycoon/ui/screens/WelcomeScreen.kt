package com.canok.kargotycoon.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.canok.kargotycoon.ui.TestTags
import com.canok.kargotycoon.ui.components.ChoiceChip
import com.canok.kargotycoon.ui.components.ConfirmDialog
import com.canok.kargotycoon.ui.components.KargoMark
import com.canok.kargotycoon.ui.components.Mark
import com.canok.kargotycoon.ui.components.Rule

@Composable
private fun LaunchFrame(content: @Composable ColumnScope.() -> Unit) {
    Box(
        Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding(),
    ) {
        Column(
            Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 28.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                KargoMark(Mark.Parcel, Modifier.size(26.dp), tint = MaterialTheme.colorScheme.tertiary)
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.company_label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.testTag(TestTags.APP_TITLE),
            )
            Spacer(Modifier.height(10.dp))
            Rule(color = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(22.dp))
            content()
        }
    }
}

@Composable
private fun LanguageChooser(languageTag: String, onLanguageChange: (String) -> Unit) {
    Text(stringResource(R.string.welcome_language), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ChoiceChip(languageTag == "tr", stringResource(R.string.language_tr), { onLanguageChange("tr") }, testTag = TestTags.WELCOME_LANG_TR)
        ChoiceChip(languageTag == "en", stringResource(R.string.language_en), { onLanguageChange("en") }, testTag = TestTags.WELCOME_LANG_EN)
    }
}

@Composable
fun LaunchScreen() {
    LaunchFrame {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.tertiary)
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.launch_opening), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun WelcomeScreen(languageTag: String, onLanguageChange: (String) -> Unit, onNewGame: () -> Unit) {
    LaunchFrame {
        Text(stringResource(R.string.welcome_label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary)
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.welcome_title), style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.welcome_description), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(28.dp))
        LanguageChooser(languageTag, onLanguageChange)
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = onNewGame,
            modifier = Modifier.fillMaxWidth().testTag(TestTags.WELCOME_NEW_GAME),
        ) {
            KargoMark(Mark.Advance, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onPrimary)
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.welcome_new_game))
        }
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.welcome_new_game_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun RecoveryScreen(
    futureSave: Boolean,
    canRecover: Boolean,
    languageTag: String,
    onLanguageChange: (String) -> Unit,
    onRecover: () -> Unit,
    onNewGame: () -> Unit,
) {
    var confirmingNewGame by remember { mutableStateOf(false) }
    LaunchFrame {
        KargoMark(Mark.Alert, Modifier.size(30.dp), tint = MaterialTheme.colorScheme.tertiary)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.recovery_title), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(if (futureSave) R.string.recovery_future_body else R.string.recovery_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        LanguageChooser(languageTag, onLanguageChange)
        Spacer(Modifier.height(24.dp))
        if (canRecover) {
            Button(onClick = onRecover, modifier = Modifier.fillMaxWidth().testTag(TestTags.WELCOME_RECOVER)) {
                Text(stringResource(R.string.recovery_restore))
            }
            Spacer(Modifier.height(8.dp))
        }
        TextButton(onClick = { confirmingNewGame = true }, modifier = Modifier.fillMaxWidth().testTag(TestTags.WELCOME_NEW_GAME)) {
            Text(stringResource(R.string.recovery_new_game))
        }
    }
    if (confirmingNewGame) {
        ConfirmDialog(
            title = stringResource(R.string.reset_confirm_title),
            message = stringResource(R.string.reset_confirm_body),
            confirmLabel = stringResource(R.string.reset_confirm_action),
            cancelLabel = stringResource(R.string.action_cancel),
            destructive = true,
            onConfirm = { confirmingNewGame = false; onNewGame() },
            onDismiss = { confirmingNewGame = false },
        )
    }
}

@Composable
fun ReadErrorScreen(onRetry: () -> Unit) {
    LaunchFrame {
        KargoMark(Mark.Alert, Modifier.size(30.dp), tint = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.read_error_title), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.read_error_body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRetry, modifier = Modifier.fillMaxWidth().testTag(TestTags.WELCOME_RETRY)) {
            Text(stringResource(R.string.read_error_retry))
        }
    }
}
