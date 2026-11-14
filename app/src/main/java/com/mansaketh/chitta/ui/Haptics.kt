package com.mansaketh.chitta.ui

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

/** Vibration strengths offered in Settings. [key] is what is stored. */
enum class VibrationLevel(val key: String, val label: String) {
    Off("off", "Off"), Light("light", "Light"), Medium("medium", "Medium"), Strong("strong", "Strong");
    companion object { fun of(key: String?) = entries.firstOrNull { it.key == key } ?: Medium }
}

/**
 * The app's vibration handler. Compose's own haptics have one fixed strength, so with a strength other than Off this drives the phone's
 * vibrator directly (a short pulse with a set strength). A phone whose motor cannot vary its strength falls back to the system haptic.
 * Three kinds of pulse: a firm one for long-presses and confirmations, a small one for taps and keys, a tiny one for scrolling ticks.
 * Direct pulses ignore the phone's "touch feedback" switch; the Off choice here is how to silence the app.
 */
class ChittaHaptics(context: Context, private val level: VibrationLevel, private val fallback: HapticFeedback) : HapticFeedback {
    private val vibrator: Vibrator? = context.getSystemService(Vibrator::class.java)

    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
        if (level == VibrationLevel.Off) return
        val v = vibrator
        if (v == null || !v.hasVibrator() || !v.hasAmplitudeControl()) { fallback.performHapticFeedback(hapticFeedbackType); return }
        val press = hapticFeedbackType == HapticFeedbackType.LongPress || hapticFeedbackType == HapticFeedbackType.Confirm
        val scroll = hapticFeedbackType == HapticFeedbackType.SegmentFrequentTick
        // (milliseconds, strength 1 to 255)
        val (ms, amp) = when (level) {
            VibrationLevel.Light -> if (press) 14 to 90 else if (scroll) 4 to 30 else 6 to 45
            VibrationLevel.Strong -> if (press) 40 to 255 else if (scroll) 14 to 180 else 20 to 230
            else -> if (press) 24 to 170 else if (scroll) 8 to 80 else 12 to 120
        }
        runCatching { v.vibrate(VibrationEffect.createOneShot(ms.toLong(), amp)) }
    }
}
