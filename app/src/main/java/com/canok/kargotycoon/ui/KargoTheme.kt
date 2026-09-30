package com.canok.kargotycoon.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val KargoColors = lightColorScheme(
    primary = Color(0xFF4338CA),
    onPrimary = Color.White,
    secondary = Color(0xFF0E7490),
    background = Color(0xFFEEF2FA),
    onBackground = Color(0xFF172033),
    surface = Color.White,
    onSurface = Color(0xFF172033),
    surfaceVariant = Color(0xFFE1E7F3),
    onSurfaceVariant = Color(0xFF526079),
)

@Composable
fun KargoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = KargoColors, content = content)
}
