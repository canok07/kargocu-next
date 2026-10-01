package com.canok.kargotycoon.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.canok.kargotycoon.R
import com.canok.kargotycoon.game.domain.GameCatalog
import com.canok.kargotycoon.game.domain.GameState
import com.canok.kargotycoon.game.domain.RegionId
import com.canok.kargotycoon.ui.TestTags
import com.canok.kargotycoon.ui.components.ChoiceChip
import com.canok.kargotycoon.ui.components.InfoBanner
import com.canok.kargotycoon.ui.components.KeyValueRow
import com.canok.kargotycoon.ui.components.Mark
import com.canok.kargotycoon.ui.components.ScreenBody
import com.canok.kargotycoon.ui.components.SectionHeading
import com.canok.kargotycoon.ui.format.currentLocale
import com.canok.kargotycoon.ui.format.formatInteger
import com.canok.kargotycoon.ui.format.locationName
import com.canok.kargotycoon.ui.format.regionName
import com.canok.kargotycoon.ui.map.MapBoard
import com.canok.kargotycoon.ui.state.Projections

@Composable
fun MapScreen(game: GameState, catalog: GameCatalog) {
    val locale = currentLocale()
    val unlocked = game.progression.unlockedRegionIds
    val activeRoutes = game.activeJobs.map { it.routeId }.toSet()
    var selectedRegion by remember { mutableStateOf<RegionId?>(unlocked.firstOrNull() ?: catalog.regions.firstOrNull()?.id) }

    val regionFill = MaterialTheme.colorScheme.surfaceVariant
    val regionStroke = MaterialTheme.colorScheme.outline
    val lockedFill = MaterialTheme.colorScheme.surface
    val routeColor = MaterialTheme.colorScheme.outlineVariant
    val activeColor = MaterialTheme.colorScheme.tertiary
    val nodeColor = MaterialTheme.colorScheme.primary
    val lockedNode = MaterialTheme.colorScheme.outline
    val labelColor = MaterialTheme.colorScheme.onSurface

    ScreenBody(Modifier.testTag(TestTags.MAP_ROOT)) {
        SectionHeading(stringResource(R.string.map_title))
        Text(stringResource(R.string.map_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        BoxWithConstraints(
            Modifier.fillMaxWidth()
                .height(320.dp)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surface),
        ) {
            val boardWidth = maxWidth
            val boardHeight = maxHeight
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                MapBoard.regions.forEach { box ->
                    val isUnlocked = RegionId(box.regionId) in unlocked
                    val topLeft = Offset(w * box.left, h * box.top)
                    val regionSize = Size(w * (box.right - box.left), h * (box.bottom - box.top))
                    drawRect(color = if (isUnlocked) regionFill else lockedFill, topLeft = topLeft, size = regionSize)
                    drawRect(color = regionStroke, topLeft = topLeft, size = regionSize, style = Stroke(width = 2f))
                }
                catalog.routes.forEach { route ->
                    val from = MapBoard.position(route.originId.value)
                    val to = MapBoard.position(route.destinationId.value)
                    if (from != null && to != null) {
                        val active = route.id in activeRoutes
                        drawLine(
                            color = if (active) activeColor else routeColor,
                            start = Offset(w * from.x, h * from.y),
                            end = Offset(w * to.x, h * to.y),
                            strokeWidth = if (active) 4f else 1.6f,
                            cap = StrokeCap.Round,
                        )
                    }
                }
            }
            catalog.locations.forEach { location ->
                val position = MapBoard.position(location.id.value) ?: return@forEach
                val isUnlocked = location.regionId in unlocked
                Box(
                    Modifier
                        .offset(x = boardWidth * position.x - 24.dp, y = boardHeight * position.y - 24.dp)
                        .size(48.dp)
                        .testTag(TestTags.mapNode(location.id.value))
                        .clickable { selectedRegion = location.regionId },
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier
                                .size(if (isUnlocked) 14.dp else 12.dp)
                                .background(if (isUnlocked) nodeColor else lockedNode, CircleShape)
                                .border(1.dp, MaterialTheme.colorScheme.surface, CircleShape),
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            locationName(location.id),
                            style = MaterialTheme.typography.labelSmall,
                            color = labelColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(activeColor, CircleShape))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.map_legend_active), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(lockedNode, CircleShape))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.map_legend_locked), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            catalog.regions.forEach { region ->
                val isUnlocked = region.id in unlocked
                ChoiceChip(
                    selected = selectedRegion == region.id,
                    label = (if (isUnlocked) "" else "🔒 ") + regionName(region.id),
                    onClick = { selectedRegion = region.id },
                )
            }
        }

        val region = catalog.regions.firstOrNull { it.id == selectedRegion }
        if (region == null) {
            InfoBanner(text = stringResource(R.string.map_select_hint), mark = Mark.Map)
        } else {
            SectionHeading(regionName(region.id))
            val isUnlocked = region.id in unlocked
            KeyValueRow(
                stringResource(R.string.map_region_status),
                stringResource(if (isUnlocked) R.string.map_region_unlocked else R.string.map_region_locked),
            )
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
                        formatInteger(Projections.ownedVehicleCount(game).toLong(), locale),
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
            catalog.locations.filter { it.regionId == region.id }.forEach { location ->
                Text(
                    "• " + locationName(location.id),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
