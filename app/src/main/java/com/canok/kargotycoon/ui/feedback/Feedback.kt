package com.canok.kargotycoon.ui.feedback

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Small real UI feedback layer honouring the persisted settings. Haptics use the
 * platform haptic feedback service; sound is a short procedural platform tone
 * (no bundled audio assets). Both are best-effort and never crash the UI.
 */
class KargoFeedback internal constructor(
    private val haptics: HapticFeedback?,
    private val tone: ToneGenerator?,
) {
    fun tick() {
        haptics?.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    fun confirm() {
        try {
            tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 60)
        } catch (_: RuntimeException) {
            // Tone generation is optional feedback only.
        }
    }

    companion object {
        val Silent = KargoFeedback(null, null)
    }
}

val LocalKargoFeedback = compositionLocalOf { KargoFeedback.Silent }

@Composable
fun rememberKargoFeedback(soundEnabled: Boolean, hapticsEnabled: Boolean): KargoFeedback {
    val haptic = LocalHapticFeedback.current
    val tone = remember(soundEnabled) {
        if (!soundEnabled) {
            null
        } else {
            try {
                ToneGenerator(AudioManager.STREAM_NOTIFICATION, 40)
            } catch (_: RuntimeException) {
                null
            }
        }
    }
    DisposableEffect(tone) {
        onDispose { tone?.release() }
    }
    return remember(haptic, tone, hapticsEnabled) {
        KargoFeedback(if (hapticsEnabled) haptic else null, tone)
    }
}
