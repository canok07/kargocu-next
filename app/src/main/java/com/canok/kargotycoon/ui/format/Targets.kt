package com.canok.kargotycoon.ui.format

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.canok.kargotycoon.R
import com.canok.kargotycoon.game.domain.GameCatalog
import com.canok.kargotycoon.game.domain.RegionId
import com.canok.kargotycoon.game.engine.NextTargetProjection
import com.canok.kargotycoon.ui.components.ProgressBar
import com.canok.kargotycoon.ui.theme.NumericStyle

/** Turns the engine's machine target ids into readable, localized goals. */
@Composable
fun targetLabel(target: String, catalog: GameCatalog): String = when {
    target.startsWith("company-level-") ->
        stringResource(R.string.target_company_level, target.removePrefix("company-level-").toIntOrNull() ?: 0)
    target.startsWith("vehicle-") -> {
        val specId = target.removePrefix("vehicle-")
        val name = catalog.vehicles.firstOrNull { it.id.value == specId }?.let { vehicleName(it) } ?: specId
        stringResource(R.string.target_vehicle, name)
    }
    target.startsWith("region-") -> stringResource(R.string.target_region, regionName(RegionId(target.removePrefix("region-"))))
    target == "first-owned-vehicle" -> stringResource(R.string.target_first_owned_vehicle)
    else -> target
}

@Composable
fun TargetProgress(target: NextTargetProjection, catalog: GameCatalog, modifier: Modifier = Modifier) {
    val locale = currentLocale()
    val fraction = if (target.required <= 0L) 1f else (target.current.toDouble() / target.required.toDouble()).coerceIn(0.0, 1.0).toFloat()
    Column(modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(targetLabel(target.target, catalog), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                stringResource(R.string.target_progress, formatInteger(target.current, locale), formatInteger(target.required, locale)),
                style = NumericStyle,
            )
        }
        Spacer(Modifier.height(6.dp))
        ProgressBar(fraction)
    }
}
