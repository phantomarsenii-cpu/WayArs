package com.wayars.app.service.accessibility

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Process-wide "Active" switch. The OS AccessibilityService can be enabled
 * in system Settings yet still do NOTHING unless this is true — enabling
 * Accessibility in Settings only grants the *permission*; this flag is the
 * app's own runtime on/off control (the Dashboard "Active" toggle).
 *
 * It starts false in a new process, but the last value the user (or the
 * subscription gate) set is persisted: if Android kills WayArs in the
 * background while scanning was on, [wasInterruptedWhileActive] lets
 * WayArsApplication switch it back on (subscription/trial permitting)
 * instead of leaving the driver with a silently dead scanner.
 */
object ScanningState {
    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive

    private const val PREFS = "scanning_state"
    private const val KEY_ACTIVE = "active"
    private const val KEY_SINCE = "active_since"

    /** Scanning is restored after a process death only within this long after it was switched on. */
    private const val RESTORE_WINDOW_MS = 16L * 60 * 60 * 1000

    fun setActive(active: Boolean, context: Context) {
        runCatching {
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_ACTIVE, active)
                .putLong(KEY_SINCE, if (active) System.currentTimeMillis() else 0L)
                .apply()
        }
        ActiveSessionTracker.onActiveChanged(active, context)
        if (active) com.wayars.app.pet.PetState.onScanningStarted(context)
        FreeTrial.onActiveChanged(active, context)
        _isActive.value = active
        com.wayars.app.features.EarningsWidget.requestUpdate(context)
        // Ties the file logger's lifetime exactly to one Active session,
        // regardless of which call site flips this flag.
        if (active) {
            ScanLogFile.start(context.applicationContext)
        } else {
            ScanLogFile.stop()
        }
    }

    /**
     * True when the previous process was killed while scanning was ON (the
     * user never switched it off and the subscription gate never did), and
     * that was switched on less than [RESTORE_WINDOW_MS] ago.
     */
    fun wasInterruptedWhileActive(context: Context): Boolean {
        val p = runCatching {
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        }.getOrNull() ?: return false
        if (!p.getBoolean(KEY_ACTIVE, false)) return false
        val since = p.getLong(KEY_SINCE, 0L)
        return since > 0L && System.currentTimeMillis() - since in 0..RESTORE_WINDOW_MS
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
