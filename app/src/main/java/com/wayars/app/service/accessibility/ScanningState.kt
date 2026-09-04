package com.wayars.app.service.accessibility

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

    fun setActive(active: Boolean) {
        _isActive.value = active
    }
}
