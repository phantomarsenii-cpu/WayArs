package com.wayars.app.util

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Android has no user-facing "reduce motion" toggle the way iOS does. The
 * closest equivalent apps can actually read is the animator duration scale
 * in Developer Options / Accessibility ("Remove animations" on many OEM
 * skins, "Animator duration scale" in stock AOSP) — when a user has turned
 * that to 0x, every system transition on the device is already instant, and
 * an app that keeps its own decorative loops (shimmer, glow, ping) spinning
 * regardless is the odd one out. This does NOT catch every motion-sensitive
 * user (most never touch this setting), but it's the one signal actually
 * available without a custom in-app setting, so we honor it for anything
 * that isn't essential to understanding the UI.
 */
object MotionPrefs {

    fun isReducedMotionPreferred(context: Context): Boolean {
        val scale = runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }.getOrDefault(1f)
        return scale == 0f
    }
}

@Composable
fun rememberReducedMotionPreferred(): Boolean {
    val context = LocalContext.current
    // Deliberately not observed reactively — this is a system dev/accessibility
    // setting that essentially never changes mid-session, and re-reading it on
    // every recomposition would be wasted work.
    return remember { MotionPrefs.isReducedMotionPreferred(context) }
}
