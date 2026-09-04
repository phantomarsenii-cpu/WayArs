package com.wayars.app.service.notification

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.wayars.app.appContainer
import com.wayars.app.domain.model.CustomThresholds
import com.wayars.app.domain.model.Currency
import com.wayars.app.domain.model.Preset
import com.wayars.app.presentation.widget.OverlayState
import com.wayars.app.service.accessibility.OrderAccessibilityService
import com.wayars.app.service.accessibility.ScanningState
import com.wayars.app.util.ScreenTextParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Backstop channel for orders that arrive as a system notification while the
 * delivery/taxi app is minimized or the order is shown in a popup the
 * AccessibilityService's window-content scan doesn't catch. Reads only the
 * notification's own title/text (same read-only philosophy as the
 * accessibility service — no notification actions are ever triggered here),
 * runs it through the same [ScreenTextParser] + evaluation pipeline, and
 * publishes to the same [OverlayState] the floating widget already observes.
 *
 * Requires a SEPARATE permission from Accessibility: Settings ->
 * Notification access. Not enabled by default; there's no in-app button for
 * it yet (Settings screen only wires up Accessibility + overlay per the
 * current spec) — enable it manually once via
 * Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS if you want this backstop.
 */
class OrderNotificationListenerService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var currentCurrency: Currency = Currency.default
    private var currentPreset: Preset = Preset.BALANCE
    private var currentCustomThresholds: CustomThresholds? = null

    override fun onListenerConnected() {
        super.onListenerConnected()
        val container = applicationContext.appContainer()
        scope.launch { container.settingsRepository.currency.collect { currentCurrency = it } }
        scope.launch { container.settingsRepository.preset.collect { currentPreset = Preset.fromType(it) } }
        scope.launch { container.settingsRepository.customThresholds.collect { currentCustomThresholds = it } }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        if (!ScanningState.isActive.value) return
        if (!OrderAccessibilityService.isSupportedPackage(sbn.packageName)) return

        val extras = sbn.notification.extras
        val texts = listOfNotNull(
            extras.getCharSequence("android.title")?.toString(),
            extras.getCharSequence("android.text")?.toString(),
            extras.getCharSequence("android.bigText")?.toString()
        )
        if (texts.isEmpty()) return

        val candidate = ScreenTextParser.parse(texts)
        if (!candidate.isComplete) return

        val earnings = candidate.earnings ?: return
        val distanceKm = candidate.distanceKm ?: return
        val timeMinutes = candidate.timeMinutes ?: return
        val currency = candidate.currency ?: currentCurrency

        val container = applicationContext.appContainer()
        val evaluation = container.evaluateOrderUseCase(
            earnings = earnings,
            distanceKm = distanceKm,
            timeMinutes = timeMinutes,
            currency = currency,
            preset = currentPreset,
            customThresholds = currentCustomThresholds
        )
        Log.d(TAG, "Parsed order from notification: $evaluation")
        OverlayState.publish(evaluation, recordId = null)
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    companion object {
        private const val TAG = "WayArsNotifListener"
    }
}
