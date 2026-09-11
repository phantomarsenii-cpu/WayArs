package com.wayars.app.service.accessibility

/**
 * Process-wide holder for the user-added custom package ids (Settings ->
 * "Поддерживаемые приложения"), on top of the hardcoded
 * [OrderAccessibilityService.SUPPORTED_PACKAGES] list.
 *
 * Exists as its own tiny object (same pattern as [ScanningState]) rather
 * than a private field on one service, because TWO independent services
 * need the same up-to-date set: [OrderAccessibilityService] itself and
 * [com.wayars.app.service.notification.OrderNotificationListenerService].
 * Both run in the SAME app process, so a plain `@Volatile` field here is
 * visible to both without needing a shared repository lookup on every
 * single event — each service's onServiceConnected/onListenerConnected
 * collects the settings Flow once and writes here; every accessibility/
 * notification event reads the cheap volatile field instead of touching
 * DataStore synchronously (which isn't possible from a non-suspend
 * function anyway).
 */
object CustomPackagesState {
    @Volatile
    var packages: Set<String> = emptySet()
        private set

    fun update(newPackages: Set<String>) {
        packages = newPackages
    }
}
