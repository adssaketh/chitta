package com.mansaketh.chitta.core.design.theme

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * The person's own "Animations" switch in Settings (separate from the system setting, master section 7). Provided once at the top of
 * the app; true when nothing provides it, so snapshots and the gallery behave as before.
 */
val LocalAnimationsOn = compositionLocalOf { true }

/**
 * False when the phone's "Remove animations" (or animator duration scale 0) is on, or the person turned Animations off in Settings. Every animation in the app asks this first and
 * jumps straight to its end state when it is false (master section 3, Motion). Follows the setting live: the observer updates
 * the value when the person changes it while the app is open.
 */
@Composable
fun rememberMotionEnabled(): Boolean {
    val resolver = LocalContext.current.contentResolver
    val state: State<Boolean> = remember(resolver) { mutableStateOf(readMotion(resolver)) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { (state as androidx.compose.runtime.MutableState<Boolean>).value = readMotion(resolver) }
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return state.value && LocalAnimationsOn.current
}

private fun readMotion(resolver: android.content.ContentResolver): Boolean =
    Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
