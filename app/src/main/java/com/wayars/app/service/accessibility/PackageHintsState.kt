package com.wayars.app.service.accessibility

import com.wayars.app.domain.model.PackageHint

/**
 * Process-wide holder for calibration hints (see [PackageHint]), same
 * pattern and same reason as [CustomPackagesState]: both
 * [OrderAccessibilityService] and
 * [com.wayars.app.service.notification.OrderNotificationListenerService]
 * need the current map without a suspend/DataStore round-trip on every
 * single event.
 */
object PackageHintsState {
    @Volatile
    var hints: Map<String, PackageHint> = emptyMap()
        private set

    fun update(newHints: Map<String, PackageHint>) {
        hints = newHints
    }
}
