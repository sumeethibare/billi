package com.example.ui.components

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.SoundEffectConstants
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView

/**
 * Tactile haptic micro-interactions and optional subtle audio feedback for Billi.
 * Respects user preferences for haptics and sound effects cleanly.
 */
class BilliFeedbackController(
    private val context: Context,
    private val composeHaptic: HapticFeedback,
    private val view: android.view.View,
    val hapticsEnabled: Boolean,
    val soundEffectsEnabled: Boolean
) {
    private val vibrator: Vibrator? by lazy {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Crisp tick for split sliders, filter chips, and tab switches. */
    fun tick() {
        if (hapticsEnabled) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
                } else {
                    composeHaptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            } catch (_: Exception) {
                composeHaptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
        if (soundEffectsEnabled) {
            playSystemClick()
        }
    }

    /** Soft thud when Google avatar docks into the top-right corner or a sheet settles. */
    fun softThud() {
        if (hapticsEnabled) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(24L, 120))
                } else {
                    composeHaptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            } catch (_: Exception) {
                composeHaptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        }
        if (soundEffectsEnabled) {
            playSubtleTone(ToneGenerator.TONE_PROP_BEEP, 45)
        }
    }

    /** Celebratory double-pulse when settling a debt, morphing graph reduction, or revealing Trip Wrapped cards. */
    fun celebrateDoublePulse() {
        if (hapticsEnabled) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val timings = longArrayOf(0L, 22L, 65L, 38L)
                    val amps = intArrayOf(0, 140, 0, 220)
                    vibrator?.vibrate(VibrationEffect.createWaveform(timings, amps, -1))
                } else {
                    composeHaptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            } catch (_: Exception) {
                composeHaptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        }
        if (soundEffectsEnabled) {
            playSubtleTone(ToneGenerator.TONE_PROP_ACK, 80)
        }
    }

    /** Distinct subtle nudge when Duplicate Expense Shield spots a conflict. */
    fun warningNudge() {
        if (hapticsEnabled) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK))
                } else {
                    composeHaptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            } catch (_: Exception) {
                composeHaptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        }
        if (soundEffectsEnabled) {
            playSubtleTone(ToneGenerator.TONE_PROP_NACK, 65)
        }
    }

    private fun playSystemClick() {
        try {
            view.playSoundEffect(SoundEffectConstants.CLICK)
        } catch (_: Exception) {
        }
    }

    private fun playSubtleTone(toneType: Int, durationMs: Int) {
        try {
            val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 24)
            tg.startTone(toneType, durationMs)
            view.postDelayed({
                try {
                    tg.release()
                } catch (_: Exception) {
                }
            }, (durationMs + 120).toLong())
        } catch (_: Exception) {
        }
    }
}

val LocalBilliFeedback = staticCompositionLocalOf<BilliFeedbackController?> { null }

@Composable
fun ProvideBilliFeedback(
    hapticsEnabled: Boolean,
    soundEffectsEnabled: Boolean,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current
    val controller = remember(context, haptic, view, hapticsEnabled, soundEffectsEnabled) {
        BilliFeedbackController(
            context = context,
            composeHaptic = haptic,
            view = view,
            hapticsEnabled = hapticsEnabled,
            soundEffectsEnabled = soundEffectsEnabled
        )
    }
    CompositionLocalProvider(LocalBilliFeedback provides controller, content = content)
}
