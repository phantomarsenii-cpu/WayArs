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
    // decision gives the app's own UI time to actually move on.
    private var suppressUntilMillis: Long = 0L

    fun suppressScanningBriefly(durationMillis: Long = 8000L) {
        suppressUntilMillis = System.currentTimeMillis() + durationMillis
    }

    fun isSuppressed(): Boolean = System.currentTimeMillis() < suppressUntilMillis
}
