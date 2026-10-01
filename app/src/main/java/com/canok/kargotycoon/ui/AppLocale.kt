package com.canok.kargotycoon.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/**
 * Applies the game language to resource lookup and number formatting without
 * touching the device locale. Strings resolve through the wrapped context.
 */
@Composable
fun AppLocale(languageTag: String, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val configuration = remember(languageTag) {
        val locale = Locale.forLanguageTag(languageTag)
        Configuration(context.resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
    }
    val localizedContext = remember(configuration, context) { context.createConfigurationContext(configuration) }
    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalConfiguration provides configuration,
        content = content,
    )
}
