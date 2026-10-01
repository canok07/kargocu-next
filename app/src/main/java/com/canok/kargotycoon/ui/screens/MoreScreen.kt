package com.canok.kargotycoon.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.canok.kargotycoon.R
import com.canok.kargotycoon.ui.TestTags
import com.canok.kargotycoon.ui.components.LedgerRow
import com.canok.kargotycoon.ui.components.Mark
import com.canok.kargotycoon.ui.components.ScreenBody
import com.canok.kargotycoon.ui.components.SectionHeading

@Composable
fun MoreScreen(onOpenMap: () -> Unit, onOpenCompany: () -> Unit, onOpenSettings: () -> Unit) {
    ScreenBody(Modifier.testTag(TestTags.MORE_ROOT)) {
        SectionHeading(stringResource(R.string.more_title))
        LedgerRow(
            title = stringResource(R.string.map_title),
            subtitle = stringResource(R.string.more_map_subtitle),
            mark = Mark.Map,
            onClick = onOpenMap,
            testTag = TestTags.MORE_MAP,
        )
        LedgerRow(
            title = stringResource(R.string.company_title),
            subtitle = stringResource(R.string.more_company_subtitle),
            mark = Mark.Gauge,
            onClick = onOpenCompany,
            testTag = TestTags.MORE_COMPANY,
        )
        LedgerRow(
            title = stringResource(R.string.settings_title),
            subtitle = stringResource(R.string.more_settings_subtitle),
            mark = Mark.More,
            onClick = onOpenSettings,
            testTag = TestTags.MORE_SETTINGS,
        )
    }
}
