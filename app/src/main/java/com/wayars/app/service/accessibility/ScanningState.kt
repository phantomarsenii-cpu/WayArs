package com.wayars.app.service.accessibility

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Process-wide "Active" switch. The OS AccessibilityService can be enabled
 * in system Settings yet still do NOTHING unless this is true — enabling
 * Accessibility in Settings only grants the *permission*; this flag is the
 * app's own runtime on/off control (the Dashboard "Active" toggle), and it
 * always starts false on a cold app launch, per spec.
 */
object ScanningState {
    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive

    fun setActive(active: Boolean, context: Context) {
        _isActive.value = active
        // Ties the file logger's lifetime exactly to one Active session,
        // regardless of which call site flips this flag.
        if (active) {
            ScanLogFile.start(context.applicationContext)
        } else {
            ScanLogFile.stop()
        }
    }

    // Cooldown after Accept/Reject: the same still-visible order screen often
    // keeps updating live text (a ticking ETA, "17 min" -> "16 min", a moving
    // pickup-distance counter) which used to look like a "new" order to our
    // equality-based dedup and pop the card straight back up right after the
    // driver dismissed it. Suppressing scanning for a few seconds after any
    // decision gives that ONE app's own UI time to actually move on.
    //
    // 3s (was 8s) — a driver working through orders back-to-back experienced
    // 8s as a felt "lag" on every single decision, since NO scanning at all
    // happens for that app during the window. 3s is short enough to not
    // read as a stall, still long enough to clear the immediate re-trigger
    // this exists for on every app tested so far; revisit if a specific app
    // is confirmed to need longer.
    //
    // Scoped PER PACKAGE, not globally — confirmed on-device, 2026-09-11:
    // a courier running Wolt/Bolt/Stuart side by side (a common real setup)
    // would accept/reject an order in ONE of them and this used to black out
    // scanning for ALL THREE for the next 8 seconds, silently missing any
    // order that arrived in a DIFFERENT app during that window. The screen
    // that actually needs a moment to settle is only the one the driver just
    // acted on.
    private val suppressUntilMillisByPackage = java.util.concurrent.ConcurrentHashMap<String, Long>()

    fun suppressScanningBriefly(packageName: String, durationMillis: Long = 3000L) {
        suppressUntilMillisByPackage[packageName] = System.currentTimeMillis() + durationMillis
    }

    fun isSuppressed(packageName: String): Boolean {
        val until = suppressUntilMillisByPackage[packageName] ?: return false
        return System.currentTimeMillis() < until
    }
}
