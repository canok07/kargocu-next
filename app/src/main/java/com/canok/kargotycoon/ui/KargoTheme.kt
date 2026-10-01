package com.canok.kargotycoon.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.canok.kargotycoon.ui.theme.KargoDarkScheme
import com.canok.kargotycoon.ui.theme.KargoLightScheme
import com.canok.kargotycoon.ui.theme.KargoShapes
import com.canok.kargotycoon.ui.theme.KargoTypography

@Composable
fun KargoTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) KargoDarkScheme else KargoLightScheme,
        typography = KargoTypography,
        shapes = KargoShapes,
        content = content,
    )
}
