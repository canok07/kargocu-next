package com.canok.kargotycoon.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.canok.kargotycoon.R
import com.canok.kargotycoon.game.domain.TutorialStep
import com.canok.kargotycoon.ui.TestTags

/**
 * One instruction at a time, driven purely by the engine's tutorial step. It
 * never grants progress; it only points at the screen where the real command
 * lives.
 */
@Composable
fun TutorialBanner(step: TutorialStep, onAction: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val copy = when (step) {
        TutorialStep.ACCEPT_FIRST_JOB -> Triple(R.string.tutorial_accept_title, R.string.tutorial_accept_body, R.string.tutorial_accept_action)
        TutorialStep.COMPLETE_FIRST_JOB -> Triple(R.string.tutorial_complete_title, R.string.tutorial_complete_body, R.string.tutorial_complete_action)
        TutorialStep.BUY_FIRST_VEHICLE -> Triple(R.string.tutorial_buy_title, R.string.tutorial_buy_body, R.string.tutorial_buy_action)
        TutorialStep.HIRE_FIRST_DRIVER -> Triple(R.string.tutorial_hire_title, R.string.tutorial_hire_body, R.string.tutorial_hire_action)
        TutorialStep.START_PARALLEL_JOBS -> Triple(R.string.tutorial_parallel_title, R.string.tutorial_parallel_body, R.string.tutorial_parallel_action)
        TutorialStep.COMPLETE -> return
    }
    Row(
        modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag(TestTags.TUTORIAL_BANNER),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KargoMark(Mark.Info, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.tertiary)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(copy.first), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(copy.second), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onAction, modifier = Modifier.testTag(TestTags.TUTORIAL_ACTION)) {
                Text(stringResource(copy.third))
            }
        }
        TextButton(onClick = onDismiss, modifier = Modifier.testTag(TestTags.TUTORIAL_DISMISS)) {
            Text(stringResource(R.string.tutorial_dismiss))
        }
    }
}
