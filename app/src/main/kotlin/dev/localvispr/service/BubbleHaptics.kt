package dev.localvispr.service

import android.content.Context
import android.os.Build
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

// ==============================================================================
// Bubble Haptics Feedback
// ==============================================================================
//
// Provides tactile feedback during floating bubble recording and transcription
// lifecycles using VibratorManager / VibrationEffect.createPredefined.

class BubbleHaptics(context: Context) {

    private val vibratorManager: VibratorManager? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        } else {
            null
        }

    private val legacyVibrator: Vibrator? =
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        } else {
            null
        }

    /** Tactile feedback when audio recording starts (sharp, decisive click). */
    fun onStartRecording() {
        playPredefined(VibrationEffect.EFFECT_CLICK)
    }

    /** Tactile feedback when recording stops (subtle tick). */
    fun onStopRecording() {
        playPredefined(VibrationEffect.EFFECT_TICK)
    }

    /**
     * Tactile feedback when text is successfully inserted / transcribed (heavy click /
     * confirmation).
     */
    fun onInserted() {
        playPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
    }

    /**
     * Tactile feedback when an action or recording is cancelled (double click / error indication).
     */
    fun onCancelled() {
        playPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK)
    }

    private fun playPredefined(effectId: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = vibratorManager ?: return
            val effect = VibrationEffect.createPredefined(effectId)
            val combined = CombinedVibration.createParallel(effect)
            vm.vibrate(combined)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val effect = VibrationEffect.createPredefined(effectId)
            legacyVibrator?.vibrate(effect)
        } else {
            @Suppress("DEPRECATION") legacyVibrator?.vibrate(50L)
        }
    }
}
