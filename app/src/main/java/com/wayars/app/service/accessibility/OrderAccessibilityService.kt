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
 *    AccessibilityNodeInfo tree (rootInActiveWindow).
 *  - Does not inject into or modify the target app's process in any way.
 *
 * Package filtering is enforced TWICE, deliberately: once via the
 * android:packageNames allow-list in res/xml/accessibility_service_config.xml
 * (so the OS doesn't even deliver events for other apps), and again here in
 * code via [isSupportedPackage] as a hard backstop. Real-world testing showed
 * the overlay misfiring on Google Maps navigation UI ("17 min · 4.2 km" from
 * Maps' own ETA bar was mistaken for an order) — this second check exists
 * specifically so that can never happen again even if the XML allow-list
 * config is ever loosened or misconfigured.
 */
class OrderAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var currentCurrency: Currency = Currency.default
    private var currentPreset: Preset = Preset.BALANCE
    private var currentCustomThresholds: CustomThresholds? = null
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
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        // Hard gate #1: the app-wide "Active" switch. The service stays bound
        // to the OS whenever Accessibility is enabled in Settings, but it must
        // do nothing at all unless the user has flipped Active ON.
        if (!ScanningState.isActive.value) return

        // Hard gate #1b: brief cooldown right after Accept/Reject — see
        // ScanningState.suppressScanningBriefly() for why this exists.
        if (ScanningState.isSuppressed()) return

        // Hard gate #2: package allow-list, enforced in code regardless of
        // what the XML config says.
        val eventPackage = event.packageName?.toString()
        if (eventPackage == null || !isSupportedPackage(eventPackage)) return

        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) return

        val now = System.currentTimeMillis()
        if (now - lastProcessedAt < 400) return // simple debounce, screens fire many events per second
        lastProcessedAt = now

        val root = rootInActiveWindow ?: return
        // Double-check the root itself belongs to a supported app — rootInActiveWindow
        // can momentarily lag behind the event's own package during window transitions.
        val rootPackage = root.packageName?.toString()
        if (rootPackage == null || !isSupportedPackage(rootPackage)) return

        val texts = ArrayList<String>()
        collectText(root, texts, maxDepth = 40)
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
            customThresholds = currentCustomThresholds
        )

        Log.d(TAG, "Parsed order: $evaluation")

        // NOTE: nothing is written to Room here. A row is only ever inserted
        // when the driver taps Accept in the overlay (see OverlayService) —
        // this is what stops every re-scan of the same still-visible order
        // from spamming duplicate rows into the stats history.
        OverlayState.publish(evaluation, recordId = null)

        if (Settings.canDrawOverlays(applicationContext)) {
            startForegroundService(Intent(applicationContext, OverlayService::class.java))
        }
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
         * res/xml/accessibility_service_config.xml. Verify real package IDs
         * on-device via `adb shell dumpsys window | grep mCurrentFocus`
         * while each app is open; these are best-effort names.
         */
        private val SUPPORTED_PACKAGES = setOf(
            "ee.mtakso.driver",           // Bolt driver app (rides + Bolt Food share this app)
            "com.ubercab.driver",         // Uber driver
            "com.wolt.courier.app",       // Wolt courier
            "com.freenow.driver"          // FreeNow driver — verify on-device, name unconfirmed
        )

        fun isSupportedPackage(packageName: String): Boolean = packageName in SUPPORTED_PACKAGES
    }
}
