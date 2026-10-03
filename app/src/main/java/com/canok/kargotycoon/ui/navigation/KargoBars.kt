package com.canok.kargotycoon.ui.navigation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.canok.kargotycoon.R
import com.canok.kargotycoon.ui.TestTags
import com.canok.kargotycoon.ui.components.KargoMark
import com.canok.kargotycoon.ui.components.Mark
import com.canok.kargotycoon.ui.components.Rule

private data class NavItem(val route: Route, val labelRes: Int, val mark: Mark, val tag: String)

private val navItems = listOf(
    NavItem(Route.Dashboard, R.string.nav_dashboard, Mark.Ledger, TestTags.NAV_DASHBOARD),
    NavItem(Route.Jobs, R.string.nav_jobs, Mark.Dispatch, TestTags.NAV_JOBS),
    NavItem(Route.Fleet, R.string.nav_fleet, Mark.Fleet, TestTags.NAV_FLEET),
    NavItem(Route.Team, R.string.nav_team, Mark.Team, TestTags.NAV_TEAM),
    NavItem(Route.More, R.string.nav_more, Mark.More, TestTags.NAV_MORE),
)

@Composable
fun KargoBottomBar(current: Route, onSelect: (Route) -> Unit) {
    NavigationBar(
        modifier = Modifier.fillMaxWidth().navigationBarsPadding().testTag(TestTags.BOTTOM_BAR),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        navItems.forEach { item ->
            NavigationBarItem(
                selected = current.tab == item.route,
                onClick = { onSelect(item.route) },
                icon = { KargoMark(item.mark, Modifier.size(22.dp)) },
                label = { Text(stringResource(item.labelRes), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                alwaysShowLabel = true,
                modifier = Modifier.testTag(item.tag),
            )
        }
    }
}

@Composable
fun KargoTopBar(title: String, canGoBack: Boolean, onBack: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (canGoBack) {
                IconButton(onClick = onBack, modifier = Modifier.testTag(TestTags.BACK)) {
                    KargoMark(Mark.Advance, Modifier.size(22.dp).rotate(180f), tint = MaterialTheme.colorScheme.onSurface)
                }
            } else {
                Spacer(Modifier.width(12.dp))
                Image(painterResource(R.drawable.ic_launcher), contentDescription = null, modifier = Modifier.size(34.dp))
                Spacer(Modifier.width(10.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    maxLines = 1,
                    modifier = Modifier.testTag(TestTags.APP_TITLE),
                )
                Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(12.dp))
        }
        Rule()
    }
}
