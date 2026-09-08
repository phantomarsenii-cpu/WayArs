package com.wayars.app.service.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.wayars.app.appContainer
import com.wayars.app.domain.model.CustomThresholds
import com.wayars.app.domain.model.Currency
import com.wayars.app.domain.model.Preset
import com.wayars.app.domain.model.PresetType
import com.wayars.app.domain.model.RawOrderCandidate
import com.wayars.app.domain.model.VehicleProfile
import com.wayars.app.presentation.widget.OverlayState
import com.wayars.app.service.overlay.OverlayService
import com.wayars.app.util.ScreenTextParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Read-only order scanner.
 *
 * Safety properties:
 *  - Never calls performAction/dispatchGesture -> no auto-clicking, ever.
 *  - Only reads text already rendered on screen via the official
 *    AccessibilityNodeInfo tree.
 *  - Does not inject into or modify the target app's process in any way.
 *
 * IMPORTANT: this does NOT rely solely on [rootInActiveWindow]. That API only
 * ever returns the currently *focused* window — but Bolt/Uber/Wolt/FreeNow
 * often show an incoming order as their OWN non-focusable overlay popup
 * (drawn with SYSTEM_ALERT_WINDOW, the same technique WayArs itself uses for
 * its verdict card) while the app is minimized, e.g. right on top of the
 * home screen launcher. A non-focusable window is never "the active window",
 * so rootInActiveWindow silently returns the LAUNCHER instead and the order
 * popup is invisible to a scan that only looks there. [windows] (enabled by
 * flagRetrieveInteractiveWindows in the service config) returns EVERY
 * currently visible window regardless of focus, which is what actually finds
 * that popup.
 *
 * Package filtering is enforced TWICE: once via the android:packageNames
 * allow-list in res/xml/accessibility_service_config.xml, and again here via
 * [isSupportedPackage] as a hard backstop against misfiring on unrelated
 * apps (this caught the service reading Google Maps' own ETA bar as an order
 * during testing).
 */
class OrderAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var currentCurrency: Currency = Currency.default
    private var currentPreset: Preset = Preset.BALANCE
    private var currentCustomThresholds: CustomThresholds? = null
    private var currentVehicleProfile: VehicleProfile = VehicleProfile.DEFAULT
    private var lastProcessedAt = 0L
    private var lastCandidate: RawOrderCandidate? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        val container = applicationContext.appContainer()
        scope.launch {
            container.settingsRepository.currency.collect { currentCurrency = it }
        }
        scope.launch {
            container.settingsRepository.preset.collect { currentPreset = Preset.fromType(it) }
        }
        scope.launch {
            container.settingsRepository.customThresholds.collect { currentCustomThresholds = it }
        }
        scope.launch {
            container.settingsRepository.vehicleProfile.collect { currentVehicleProfile = it }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        // Hard gate #1: the app-wide "Active" switch.
        if (!ScanningState.isActive.value) return

        // Hard gate #1b: brief cooldown right after Accept/Reject.
        if (ScanningState.isSuppressed()) return

        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) return

        val now = System.currentTimeMillis()
        if (now - lastProcessedAt < 500) return // debounce — now runs for events from ANY app,
        lastProcessedAt = now                    // not just supported ones, so a bit more headroom here

        // Diagnostic recording happens for EVERY app (see ScanDiagnostics /
        // Settings -> Диагностика) so the real Bolt/Wolt package name can
        // finally be confirmed on-device. Anything not on our supported list
        // bails out immediately, right here — before touching `windows` or
        // walking any node tree — so the cost of the temporarily-removed
        // XML package filter stays as close to zero as possible for the
        // flood of unrelated apps' events.
        val eventPackage = event.packageName?.toString() ?: return
        val isSupported = isSupportedPackage(eventPackage)
        ScanDiagnostics.record(eventPackage, isSupported, textsCollected = 0)
        if (!isSupported) return

        val root = findSupportedWindowRoot(eventPackage) ?: return

        val texts = ArrayList<String>()
        collectText(root, texts, maxDepth = 40)
        ScanDiagnostics.record(eventPackage, isSupported, textsCollected = texts.size)
        if (texts.isEmpty()) return

        val candidate = ScreenTextParser.parse(texts)
        if (!candidate.isComplete) return
        if (candidate == lastCandidate) return // identical to what's already on screen — nothing changed
        lastCandidate = candidate

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
            vehicleProfile = currentVehicleProfile,
            customThresholds = currentCustomThresholds
        )

        Log.d(TAG, "Parsed order: $evaluation")

        // NOTE: nothing is written to Room here. A row is only ever inserted
        // when the driver taps Accept in the overlay (see OverlayService).
        OverlayState.publish(evaluation, recordId = null)

        if (Settings.canDrawOverlays(applicationContext)) {
            startForegroundService(Intent(applicationContext, OverlayService::class.java))
        }
    }

    /**
     * Looks across ALL currently visible windows (not just the focused one)
     * for one belonging to a supported app, preferring [preferredPackage]
     * (the package that actually generated this event) if it has a window,
     * otherwise any other supported-app window that happens to be visible.
     * Falls back to rootInActiveWindow only if the windows list is empty for
     * some reason (e.g. capability not yet granted).
     */
    private fun findSupportedWindowRoot(preferredPackage: String?): AccessibilityNodeInfo? {
        val visibleWindows = windows
        if (visibleWindows.isNullOrEmpty()) {
            val fallbackRoot = rootInActiveWindow ?: return null
            val fallbackPackage = fallbackRoot.packageName?.toString()
            return if (fallbackPackage != null && isSupportedPackage(fallbackPackage)) fallbackRoot else null
        }

        var fallbackMatch: AccessibilityNodeInfo? = null
        for (window in visibleWindows) {
            val root = window.root ?: continue
            val pkg = root.packageName?.toString() ?: continue
            if (!isSupportedPackage(pkg)) continue
            if (pkg == preferredPackage) return root
            if (fallbackMatch == null) fallbackMatch = root
        }
        return fallbackMatch
    }

    /** Iterative (non-recursive) tree walk to avoid stack overflows on deep trees. */
    private fun collectText(root: AccessibilityNodeInfo, out: MutableList<String>, maxDepth: Int) {
        val stack = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        stack.addLast(root to 0)
        while (stack.isNotEmpty()) {
            val (node, depth) = stack.removeLast()
            node.text?.let { if (it.isNotBlank()) out.add(it.toString()) }
            node.contentDescription?.let { if (it.isNotBlank()) out.add(it.toString()) }
            if (depth >= maxDepth) continue
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { stack.addLast(it to depth + 1) }
            }
        }
    }

    override fun onInterrupt() {
        // Read-only service — nothing to tear down mid-scan.
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    companion object {
        private const val TAG = "WayArsAccessibility"

        /**
         * Runtime backstop allow-list — keep in sync with
         * res/xml/accessibility_service_config.xml.
         * ee.mtakso.driver / com.wolt.courier.app kept alongside the
         * user-verified names below in case either regional build uses the
         * other package id — harmless to list both, an exact match is still
         * required either way.
         */
        private val SUPPORTED_PACKAGES = setOf(
            "com.bolt.deliverycourier",   // Bolt courier — verified on-device
            "ee.mtakso.driver",           // Bolt driver (rides) — older/alt package id
            "com.ubercab.driver",         // Uber driver
            "com.wolt.courierapp",        // Wolt courier — verified on-device
            "com.wolt.courier.app",       // Wolt courier — older/alt package id
            "com.freenow.driver"          // FreeNow driver — still unconfirmed
        )

        fun isSupportedPackage(packageName: String): Boolean = packageName in SUPPORTED_PACKAGES
    }
}
