package com.canok.kargotycoon.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Dispatch-ledger palette: warm paper, near-black forest ink and a single
 * restrained burnt-orange accent. Deliberately unrelated to the temporary
 * blue/indigo welcome of M1 and to the old game's visuals.
 */

// Paper / ink / accent tokens shared by both schemes.
val Paper = Color(0xFFF4EFE5)
val PaperRaised = Color(0xFFFBF7EF)
val PaperSunken = Color(0xFFE8E0D1)
val Ink = Color(0xFF17211B)
val InkSoft = Color(0xFF4C574E)
val Rule = Color(0xFFC9BFA9)
val Burnt = Color(0xFFB4561F)
val BurntSoft = Color(0xFFE7B588)
val Forest = Color(0xFF1F3D30)
val ForestSoft = Color(0xFF3F5F4E)
val Brick = Color(0xFF8C3220)

val NightPaper = Color(0xFF12160F)
val NightRaised = Color(0xFF1B2119)
val NightSunken = Color(0xFF262E25)
val NightInk = Color(0xFFF0E9DA)
val NightInkSoft = Color(0xFFB7BFAF)
val NightRule = Color(0xFF48513F)
val NightBurnt = Color(0xFFE09159)
val NightForest = Color(0xFF9BC6A6)

val KargoLightScheme = lightColorScheme(
    primary = Forest,
    onPrimary = Color(0xFFF6F2E7),
    primaryContainer = Color(0xFFD7E4D6),
    onPrimaryContainer = Color(0xFF10241A),
    secondary = ForestSoft,
    onSecondary = Color(0xFFF6F2E7),
    secondaryContainer = PaperSunken,
    onSecondaryContainer = Ink,
    tertiary = Burnt,
    onTertiary = Color(0xFFFFF3EA),
    tertiaryContainer = Color(0xFFF3D6BC),
    onTertiaryContainer = Color(0xFF3A1B08),
    background = Paper,
    onBackground = Ink,
    surface = PaperRaised,
    onSurface = Ink,
    surfaceVariant = PaperSunken,
    onSurfaceVariant = InkSoft,
    outline = Rule,
    outlineVariant = Color(0xFFDDD4C2),
    error = Brick,
    onError = Color(0xFFFFF3EE),
    errorContainer = Color(0xFFF3D3C9),
    onErrorContainer = Color(0xFF3A0F06),
    inverseSurface = Ink,
    inverseOnSurface = Paper,
    surfaceTint = Forest,
)

val KargoDarkScheme = darkColorScheme(
    primary = NightForest,
    onPrimary = Color(0xFF0E2116),
    primaryContainer = Color(0xFF2C4A39),
    onPrimaryContainer = Color(0xFFD7E4D6),
    secondary = Color(0xFFA9BCA9),
    onSecondary = Color(0xFF12200F),
    secondaryContainer = NightSunken,
    onSecondaryContainer = NightInk,
    tertiary = NightBurnt,
    onTertiary = Color(0xFF2C1403),
    tertiaryContainer = Color(0xFF5A2C10),
    onTertiaryContainer = Color(0xFFF4D9C2),
    background = NightPaper,
    onBackground = NightInk,
    surface = NightRaised,
    onSurface = NightInk,
    surfaceVariant = NightSunken,
    onSurfaceVariant = NightInkSoft,
    outline = NightRule,
    outlineVariant = Color(0xFF333B2E),
    error = Color(0xFFE6A091),
    onError = Color(0xFF3A0F06),
    errorContainer = Color(0xFF5A2419),
    onErrorContainer = Color(0xFFF3D3C9),
    inverseSurface = NightInk,
    inverseOnSurface = NightPaper,
    surfaceTint = NightForest,
)
