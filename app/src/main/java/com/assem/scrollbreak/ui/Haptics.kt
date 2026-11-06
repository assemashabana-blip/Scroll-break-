package com.assem.scrollbreak.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.assem.scrollbreak.AppSettings

/** How strong a buzz is: a dial step, a toggle, or the main blocking switch. Only dials and toggles buzz. */
enum class Buzz { TICK, CLICK, HEAVY }

/**
 * Drives the vibration motor directly, so feedback works even when the phone's own "touch
 * feedback" is off. Does nothing when the user turned Vibration off in Settings.
 */
object Haptics {
    fun buzz(context: Context, kind: Buzz) {
        if (!AppSettings.hapticsEnabled) return
        val vibrator = if (Build.VERSION.SDK_INT >= 31) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
        if (vibrator?.hasVibrator() != true) return
        val effect = if (Build.VERSION.SDK_INT >= 29) {
            VibrationEffect.createPredefined(
                when (kind) {
                    Buzz.TICK -> VibrationEffect.EFFECT_TICK
                    Buzz.CLICK -> VibrationEffect.EFFECT_CLICK
                    Buzz.HEAVY -> VibrationEffect.EFFECT_HEAVY_CLICK
                },
            )
        } else {
            val ms = when (kind) {
                Buzz.TICK -> 8L
                Buzz.CLICK -> 15L
                Buzz.HEAVY -> 30L
            }
            VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE)
        }
        vibrator.vibrate(effect)
    }
}

/** A buzz function bound to the current context, for use in composables. */
@Composable
fun rememberBuzz(): (Buzz) -> Unit {
    val context = LocalContext.current.applicationContext
    return remember(context) { { kind -> Haptics.buzz(context, kind) } }
}
