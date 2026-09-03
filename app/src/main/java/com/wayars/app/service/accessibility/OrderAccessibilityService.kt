package com.wayars.app.service.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.wayars.app.appContainer
import com.wayars.app.domain.model.Currency
import com.wayars.app.domain.model.Preset
import com.wayars.app.domain.model.PresetType
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
 * Safety properties (see project README for the full rationale):
 *  - Never calls performAction/dispatchGesture -> no auto-clicking, ever.
 *  - Only reads text already rendered on screen via the official
 *    AccessibilityNodeInfo tree (rootInActiveWindow), scoped to the
 *    package allow-list in res/xml/accessibility_service_config.xml.
 *  - Does not inject into or modify the target app's process in any way.
 */
class OrderAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var currentCurrency: Currency = Currency.default
    private var currentPreset: Preset = Preset.BALANCE
    private var lastProcessedAt = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        val container = applicationContext.appContainer()
        scope.launch {
            container.settingsRepository.currency.collect { currentCurrency = it }
        }
        scope.launch {
            container.settingsRepository.preset.collect { currentPreset = Preset.fromType(it) }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) return

        val now = System.currentTimeMillis()
        if (now - lastProcessedAt < 400) return // simple debounce, screens fire many events per second
        lastProcessedAt = now

        val root = rootInActiveWindow ?: return
        val texts = ArrayList<String>()
        collectText(root, texts, maxDepth = 40)

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
            preset = currentPreset
        )

        Log.d(TAG, "Parsed order: $evaluation")

        scope.launch {
            val id = runCatching { container.orderRepository.record(evaluation) }.getOrNull()
            OverlayState.publish(evaluation, id)
        }

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
    }
}
