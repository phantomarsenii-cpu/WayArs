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
    // Per-package, not global — see the debounce comment in
    // onAccessibilityEvent for why a single shared timer let noise from
    // unrelated apps starve out real order-popup events (Uber especially).
    private val lastProcessedAtByPackage = HashMap<String, Long>()
    private var lastCandidate: RawOrderCandidate? = null
    // One in-flight poll job per package. Uber (and occasionally others)
    // draws its incoming-order popup as a non-focusable SYSTEM_ALERT_WINDOW
    // whose Accessibility node tree isn't populated yet on the very first
    // WINDOW_STATE_CHANGED — see pollForTexts() below. Keyed per package so
    // a fresh event for the same app cancels and restarts its own poll
    // instead of racing a stale one; unrelated packages are untouched.
    private val pollJobsByPackage = HashMap<String, kotlinx.coroutines.Job>()

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
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED
        ) return

        // TYPE_WINDOWS_CHANGED fires the instant the system's set of visible
        // windows changes anywhere on the device, and is the only event a
        // non-focusable TYPE_APPLICATION_OVERLAY popup reliably triggers —
        // which is how Uber draws its incoming-order toast. Unlike the other
        // two event types it does NOT reliably carry event.packageName, so
        // the package can't be read off the event itself here. Resolve it by
        // scanning the actually-visible windows instead (findSupportedWindowRoot
        // already does that scan) and hang onto the root it finds so the
        // normal path below doesn't have to scan `windows` a second time.
        val windowsChangedRoot: AccessibilityNodeInfo? =
            if (event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
                findSupportedWindowRoot(null)
            } else {
                null
            }

        // Package filter MUST run before the debounce check, not after.
        // The old order checked/updated a single global lastProcessedAt
        // against events from EVERY app (launcher, system UI, keyboard,
        // whatever else is generating accessibility events at that moment)
        // before ever looking at which package the event came from. On a
        // busy device that global timer is almost never idle, so the one
        // event that actually matters — Uber's order popup WINDOW_STATE_
        // CHANGED the instant it appears — regularly landed inside someone
        // else's 500ms window and got silently dropped. By the time a
        // Uber event finally survived the debounce, the popup itself had
        // often already been dismissed/expired, so the overlay showed
        // late or not at all. Filtering by package first means only
        // Uber's (or another supported app's) own event cadence can debounce
        // Uber, so the very first appearance is processed immediately.
        val eventPackage = if (event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            windowsChangedRoot?.packageName?.toString() ?: return
        } else {
            event.packageName?.toString() ?: return
        }
        val isSupported = isSupportedPackage(eventPackage)
        if (!isSupported) {
            // Still worth a diagnostic line (see Settings -> Диагностика) so
            // the real Bolt/Wolt/Uber package name can be confirmed
            // on-device — but nothing more expensive than that for apps we
            // don't care about.
            ScanDiagnostics.record(eventPackage, matchedSupportedApp = false)
            return
        }

        // Per-package debounce, not a single global one — see comment
        // above. A burst of content-changed events from the SAME popup
        // (e.g. a ticking ETA) is still collapsed, but that no longer
        // costs other apps' events any of the window's budget.
        val now = System.currentTimeMillis()
        val lastForPackage = lastProcessedAtByPackage[eventPackage] ?: 0L
        // Never debounce a brand-new window appearing — only repeat
        // content-changed spam on an already-seen window. This is what
        // guarantees the FIRST sighting of an order popup (Uber's included)
        // is always parsed instantly instead of possibly being the one
        // event that gets swallowed by the debounce. TYPE_WINDOWS_CHANGED
        // counts as a new-window signal too — it's fired for exactly that
        // reason for Uber's overlay popup.
        val isNewWindowAppearing = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED
        if (!isNewWindowAppearing && now - lastForPackage < 500) return
        lastProcessedAtByPackage[eventPackage] = now

        val root = windowsChangedRoot ?: findSupportedWindowRoot(eventPackage)
        if (root == null) {
            ScanDiagnostics.record(eventPackage, matchedSupportedApp = true, windowFound = false)
            return
        }

        val texts = ArrayList<String>()
        collectText(root, texts, maxDepth = 40)

        // Fallback source #1: some overlay popups (Uber's incoming-order
        // toast in particular) expose a root that's found but whose node
        // tree yields zero text — the window itself carries text on the
        // triggering AccessibilityEvent even when its node tree doesn't.
        if (texts.isEmpty()) {
            event.text?.forEach { if (it.isNotBlank()) texts.add(it.toString()) }
            event.source?.let { collectText(it, texts, maxDepth = 40) }
        }

        if (texts.isEmpty()) {
            // Fallback source #2: the window's semantics tree may simply not
            // be built yet (Compose/Flutter overlays build it a few frames
            // after the popup appears). Uber's incoming-order popup is the
            // main offender — poll for it instead of a single fixed-delay
            // retry, since one retry at a fixed 200ms either fires too
            // early (tree still empty, no second attempt left) or wastes
            // 200ms when the tree was actually ready sooner.
            ScanDiagnostics.record(
                eventPackage, matchedSupportedApp = true, windowFound = true, textsCollected = 0
            )
            pollForTexts(eventPackage)
            return
        }

        handleCollectedTexts(eventPackage, texts)
    }

    /**
     * Bounded poll for a package's window text, used only when the initial
     * scan in onAccessibilityEvent found the window but its node tree was
     * still empty (see the comment there). Polls every [intervalMs] up to
     * [maxAttempts] times — short interval so a fast-appearing tree (the
     * common case) is caught almost immediately, hard cap so a window that
     * never populates (or never should have matched) can't spin forever.
     * Stops as soon as text is found and handed to handleCollectedTexts, or
     * the window disappears. Any previous poll for the same package is
     * cancelled first so a new event for that app always restarts from
     * scratch rather than racing a stale poll.
     */
    private fun pollForTexts(
        eventPackage: String,
        intervalMs: Long = 75L,
        maxAttempts: Int = 20 // 20 * 75ms = 1.5s ceiling
    ) {
        pollJobsByPackage[eventPackage]?.cancel()
        pollJobsByPackage[eventPackage] = scope.launch {
            repeat(maxAttempts) {
                kotlinx.coroutines.delay(intervalMs)
                val retryRoot = findSupportedWindowRoot(eventPackage) ?: return@launch
                val retryTexts = ArrayList<String>()
                collectText(retryRoot, retryTexts, maxDepth = 40)
                if (retryTexts.isNotEmpty()) {
                    handleCollectedTexts(eventPackage, retryTexts)
                    return@launch
                }
            }
        }
    }

    /**
     * Parses the scraped [texts], updates diagnostics (always, with the raw
     * texts attached so misparsed layouts like Stuart's can be inspected
     * on-device instead of guessed at from logs), and — if the candidate is
     * complete and new — publishes it to the overlay.
     */
    private fun handleCollectedTexts(eventPackage: String, texts: List<String>) {
        val candidate = ScreenTextParser.parse(texts)
        val candidateSummary = "earnings=${candidate.earnings} km=${candidate.distanceKm} min=${candidate.timeMinutes} cur=${candidate.currency}"

        if (!candidate.isComplete) {
            ScanDiagnostics.record(
                eventPackage, matchedSupportedApp = true, windowFound = true,
                textsCollected = texts.size, parsedSummary = candidateSummary, rawTexts = texts
            )
            return
        }
        if (candidate == lastCandidate) return // identical to what's already on screen — nothing changed
        lastCandidate = candidate

        val earnings = candidate.earnings ?: return
        val distanceKm = candidate.distanceKm ?: return
        // Not required for isComplete — default to 0 rather than drop the
        // order (Stuart routinely has no parseable minutes figure).
        val timeMinutes = candidate.timeMinutes ?: 0.0
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
        ScanDiagnostics.record(
            eventPackage, matchedSupportedApp = true, windowFound = true,
            textsCollected = texts.size, parsedSummary = "OK: $candidateSummary -> ${evaluation.verdict}",
            rawTexts = texts
        )

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

    /**
     * Iterative (non-recursive) tree walk to avoid stack overflows on deep
     * trees.
     *
     * MUST preserve document/visual sibling order in [out]. ScreenTextParser
     * relies on the FIRST money match in the list being the real total
     * (Stuart, and every other supported app, always renders the headline
     * price before secondary figures like a star rating). A plain stack
     * walk that pushes children left-to-right and pops with removeLast()
     * visits them LIFO — i.e. RIGHTMOST/LAST child first — which silently
     * reverses sibling order at every level. That reversal was the actual
     * cause of Stuart mis-parses that looked like a regex problem (e.g. a
     * "★ 1.87" rating node landing in [out] before the real "16.81zł"
     * total, because the rating happened to be a later sibling in the
     * tree). Pushing children in REVERSE (childCount-1 downTo 0) makes the
     * LIFO pop order come out correct again: child 0 first, child 1 next,
     * etc. — matching both document order and what parse() assumes.
     */
    private fun collectText(root: AccessibilityNodeInfo, out: MutableList<String>, maxDepth: Int) {
        val stack = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        stack.addLast(root to 0)
        while (stack.isNotEmpty()) {
            val (node, depth) = stack.removeLast()
            node.text?.let { if (it.isNotBlank()) out.add(it.toString()) }
            node.contentDescription?.let { if (it.isNotBlank()) out.add(it.toString()) }
            if (depth >= maxDepth) continue
            for (i in node.childCount - 1 downTo 0) {
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
            "com.ubercab.driver",         // Uber driver — verified on-device
            "com.wolt.courierapp",        // Wolt courier — verified on-device
            "com.wolt.courier.app",       // Wolt courier — older/alt package id
            "com.freenow.driver",         // FreeNow driver — still unconfirmed
            "com.stuart.courier"          // Stuart courier
        )

        fun isSupportedPackage(packageName: String): Boolean = packageName in SUPPORTED_PACKAGES
    }
}
