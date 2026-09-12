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
        // The log file must exist the moment this service is alive, not only
        // when the in-app "Active" switch happens to flip it on — the UI
        // path that used to guarantee this (ScanningState.setActive ->
        // ScanLogFile.start(context)) depends on a toggle that may be
        // hidden or skipped, which left ScanDiagnostics.record() writing to
        // a null currentFile. Starting it here, unconditionally, with the
        // service's own context, removes that dependency entirely.
        //
        // Note: this does NOT change what gets scanned or recorded — event
        // processing is still fully gated behind ScanningState.isActive in
        // onAccessibilityEvent() below ("Hard gate #1"). This only
        // guarantees the FILE is ready to receive lines whenever that gate
        // does let a diagnostic through.
        ScanLogFile.start(this)
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
        scope.launch {
            container.settingsRepository.customPackages.collect { CustomPackagesState.update(it) }
        }
        scope.launch {
            container.settingsRepository.packageHints.collect { PackageHintsState.update(it) }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        // Hard gate #1: the app-wide "Active" switch.
        if (!ScanningState.isActive.value) return

        // Hard gate #1b: brief cooldown right after Accept/Reject — scoped
        // to the SPECIFIC app that was just decided on, not global (see
        // ScanningState.suppressScanningBriefly's comment). Uses
        // event.packageName directly rather than the resolved
        // windowsChangedRoot below, so a TYPE_WINDOWS_CHANGED event (whose
        // packageName is often null/unreliable per the comment further
        // down) simply won't match a real suppressed key here and passes
        // this gate — an acceptable gap for that one event type, since the
        // debounce window and the lastCandidate equality check downstream
        // still catch an exact-duplicate re-trigger of the same order.
        if (ScanningState.isSuppressed(event.packageName?.toString().orEmpty())) return

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
        // deliberately does NOT get the same bypass: unlike WINDOW_STATE_
        // CHANGED (which only fires on a genuine window change), WINDOWS_
        // CHANGED fires for any change to the system's visible-window list
        // — including ones that have nothing to do with a new order (minor
        // layout/animation churn while the same popup is still on screen).
        // Bypassing the debounce for every one of those flooded ScanDiagnostics
        // with near-duplicate entries every few hundred ms, which pushed the
        // one entry actually worth reading off the capped list before it
        // could be opened. The normal per-package debounce below is enough:
        // the first WINDOWS_CHANGED for a package is virtually always >500ms
        // after that package's last event (it wasn't in the foreground a
        // moment ago), so Uber's popup still gets caught on first sight.
        val isNewWindowAppearing = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        if (!isNewWindowAppearing && now - lastForPackage < 500) return
        lastProcessedAtByPackage[eventPackage] = now

        val root = windowsChangedRoot ?: findSupportedWindowRoot(eventPackage)
        if (root == null) {
            ScanDiagnostics.record(eventPackage, matchedSupportedApp = true, windowFound = false)
            return
        }

        val texts = ArrayList<String>()
        texts.addAll(collectTextsFromAllSupportedWindows(eventPackage))

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
            //
            // Diagnostic-only addition: describeEmptyTree() records WHY the
            // tree was empty (zero children at all vs. children present but
            // textless) instead of just the fact that it was. Field logs
            // show Uber's window is found (window=true) but yields texts=0
            // for several REAL seconds in a row (not a single missed frame)
            // before it suddenly populates — that's too long to be a normal
            // "not laid out yet" gap, so the next step is seeing which of
            // the two shapes it actually is on-device, not guessing.
            ScanDiagnostics.record(
                eventPackage, matchedSupportedApp = true, windowFound = true, textsCollected = 0,
                parsedSummary = describeEmptyTree(root)
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
     * common case) is caught almost immediately. Stops as soon as text is
     * found and handed to handleCollectedTexts, or the window disappears.
     * Any previous poll for the same package is cancelled first so a new
     * event for that app always restarts from scratch rather than racing a
     * stale poll.
     *
     * Ceiling raised from an original 1.5s (20 * 75ms) to ~12s. Field logs
     * (Uber, 2026-09-10) showed root has 0 children for a SINGLE sustained
     * popup for 20-30+ real seconds before Uber's own tree finally
     * populated — this is not a "missed frame", it's how slow that specific
     * overlay's content genuinely loads. The old 1.5s cap gave up long
     * before that and depended on the NEXT external TYPE_WINDOWS_CHANGED
     * event to restart a fresh 1.5s poll; when the popup went quiet for a
     * stretch with no further system window-list churn (also seen in the
     * same logs — 90+ second gaps with zero Uber events while the banner
     * was presumably still up), no poll was running at all during that gap
     * and a populate-then-disappear cycle could be missed entirely. A
     * single ~12s poll covers the slow case without needing to rely on
     * lucky re-triggering. 150ms interval keeps the total attempt count
     * (~80) cheap — each attempt is a shallow window-root fetch, not a
     * repeated full tree walk unless text is actually found.
     */
    private fun pollForTexts(
        eventPackage: String,
        intervalMs: Long = 150L,
        maxAttempts: Int = 80 // 80 * 150ms = 12s ceiling
    ) {
        pollJobsByPackage[eventPackage]?.cancel()
        pollJobsByPackage[eventPackage] = scope.launch {
            repeat(maxAttempts) {
                kotlinx.coroutines.delay(intervalMs)
                // Existence check only — still tells us the window is gone
                // and we should stop polling. The actual text comes from
                // collectTextsFromAllSupportedWindows() below so a
                // same-package drawer/overlay window can't hide the real
                // content here either.
                findSupportedWindowRoot(eventPackage) ?: return@launch
                val retryTexts = collectTextsFromAllSupportedWindows(eventPackage)
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
        val candidate = ScreenTextParser.parse(texts, hint = PackageHintsState.hints[eventPackage])
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
        OverlayState.publish(evaluation, recordId = null, sourcePackage = eventPackage)

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

        var anySupportedMatch: AccessibilityNodeInfo? = null
        for (window in visibleWindows) {
            val root = window.root ?: continue
            val pkg = root.packageName?.toString() ?: continue
            if (!isSupportedPackage(pkg)) continue
            if (pkg == preferredPackage) return root
            // Falling back to a DIFFERENT supported app's window is only
            // correct when preferredPackage is null, i.e. the
            // TYPE_WINDOWS_CHANGED case above, which doesn't carry a
            // package name at all and is deliberately asking "is ANY
            // supported app's window on screen right now". When a SPECIFIC
            // package was asked for, handing back a different one instead
            // is actively wrong, not just imprecise: with two courier apps
            // open at once (confirmed on-device, 2026-09-11 — Wolt, Bolt
            // and Stuart all running side by side), this used to make an
            // event for one app report the OTHER app's window as if it
            // were its own, rather than correctly reporting "not found yet"
            // for the one that legitimately had nothing.
            if (preferredPackage == null && anySupportedMatch == null) anySupportedMatch = root
        }
        return anySupportedMatch
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
     *
     * Skips text/contentDescription from a node whose [AccessibilityNodeInfo.isVisibleToUser]
     * is false. Wolt (confirmed on-device) keeps its navigation-drawer
     * content permanently attached to the semantics tree — just translated
     * off-screen while "closed" — rather than removing it, so a blind walk
     * picks up drawer menu text even when the user is on the main screen
     * looking at an order card. Only the text-adding step is skipped, not
     * the subtree walk itself: visibility flags on some OEM/Compose builds
     * are unreliable at a container level even when accurate on leaves, so
     * still descending avoids silently losing genuinely visible children
     * of a container that (for whatever reason) reports itself as hidden.
     */
    private fun collectText(root: AccessibilityNodeInfo, out: MutableList<String>, maxDepth: Int) {
        val stack = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        stack.addLast(root to 0)
        while (stack.isNotEmpty()) {
            val (node, depth) = stack.removeLast()
            if (node.isVisibleToUser) {
                node.text?.let { if (it.isNotBlank()) out.add(it.toString()) }
                node.contentDescription?.let { if (it.isNotBlank()) out.add(it.toString()) }
            }
            if (depth >= maxDepth) continue
            for (i in node.childCount - 1 downTo 0) {
                node.getChild(i)?.let { stack.addLast(it to depth + 1) }
            }
        }
    }

    /**
     * Collects text from EVERY currently-visible window belonging to
     * [preferredPackage] SPECIFICALLY — not one, and NOT any other
     * supported package. [findSupportedWindowRoot] returns on the FIRST
     * same-package window it finds, which field logs (Wolt, 2026-09-10)
     * showed can be the wrong one: Wolt exposed a second window for its own
     * package whose entire reachable tree was just the navigation-drawer
     * chrome ("Close drawer", then its menu items), and the scanner stayed
     * latched onto THAT window for 28+ minutes while the user was on the
     * main screen the whole time with a real order showing — because
     * nothing ever made it try any other window for the SAME package.
     *
     * An earlier version of this fix filtered by `isSupportedPackage(pkg)`
     * instead of `pkg == preferredPackage` for the "other windows" pass,
     * intending to only broaden the search within one app. That was a real
     * bug, not just imprecise: field logs (2026-09-11) showed a
     * Wolt-triggered scan and a Bolt-triggered scan producing
     * byte-for-byte IDENTICAL merged text, because couriers commonly run
     * TWO delivery apps side by side (confirmed on-device — Wolt, Bolt,
     * AND Stuart were all open at once in that session) and every single
     * scan was pulling in every OTHER open courier app's window too,
     * corrupting all of them at once. Restricting to `pkg == preferredPackage`
     * keeps the original drawer-window fix (still gathers every window
     * that particular app owns) without reaching into a different app's
     * screen.
     */
    private fun collectTextsFromAllSupportedWindows(preferredPackage: String?): List<String> {
        val texts = ArrayList<String>()
        val visibleWindows = windows
        if (visibleWindows.isNullOrEmpty()) {
            val fallbackRoot = rootInActiveWindow ?: return texts
            val fallbackPackage = fallbackRoot.packageName?.toString()
            if (fallbackPackage != null && isSupportedPackage(fallbackPackage) &&
                (preferredPackage == null || fallbackPackage == preferredPackage)
            ) {
                collectText(fallbackRoot, texts, maxDepth = 40)
            }
            return texts
        }
        if (preferredPackage == null) return texts

        for (window in visibleWindows) {
            val root = window.root ?: continue
            val pkg = root.packageName?.toString() ?: continue
            if (pkg != preferredPackage) continue
            collectText(root, texts, maxDepth = 40)
        }
        return texts
    }

    /**
     * Inspects why [root]'s tree yielded no text — for diagnostics only,
     * never used to change scan behavior. Distinguishes two very different
     * situations that both currently look identical as "texts=0":
     *  - genuinely no children yet (root.childCount == 0): the window
     *    exists but the app hasn't attached any content to it — consistent
     *    with a staged/animated reveal.
     *  - children exist but none carry text/contentDescription: the layout
     *    is already there, only the text itself is missing — consistent
     *    with fields being populated asynchronously (e.g. after a route/ETA
     *    lookup) rather than the whole card being deferred.
     * Only looks one level deep and only at className, so this stays cheap
     * enough to run on every empty-tree event without adding real cost.
     */
    private fun describeEmptyTree(root: AccessibilityNodeInfo): String {
        val childCount = root.childCount
        if (childCount == 0) return "emptyTree: root has 0 children"
        val classes = (0 until childCount).mapNotNull { root.getChild(it)?.className?.toString() }
        return "emptyTree: root has $childCount children, classes=$classes"
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
         *
         * This is the DEFAULT list only. The user can add any other
         * delivery/taxi app's package id at runtime from Settings ->
         * "Поддерживаемые приложения" — see [CustomPackagesState] and
         * [isSupportedPackage] below, which checks both. Nothing here needs
         * to change to support a new country/app; that's the whole point of
         * the user-editable list — the scanner and ScreenTextParser have no
         * per-app branches, they just need the package name allow-listed.
         *
         * "Driver" (ride-hailing/taxi) and "courier" (delivery) apps from
         * the same company are separate packages with separate order-screen
         * layouts, so both are listed explicitly per company rather than
         * assumed to be the same app. ee.mtakso.driver (Bolt Driver) and
         * taxi.android.driver (FreeNow Driver) confirmed by the user
         * directly, replacing an earlier unconfirmed guess for FreeNow
         * (com.freenow.driver, which was never right — that's why FreeNow
         * never worked at all before). com.wolt.courier.app kept alongside
         * the verified com.wolt.courierapp in case a regional build uses
         * the other id — harmless to list both, an exact match is still
         * required either way.
         */
        private val SUPPORTED_PACKAGES = setOf(
            "com.bolt.deliverycourier",   // Bolt courier — verified on-device
            "ee.mtakso.driver",           // Bolt driver (taxi/rides) — confirmed by user
            "com.ubercab.driver",         // Uber driver — verified on-device
            "com.wolt.courierapp",        // Wolt courier — verified on-device
            "com.wolt.courier.app",       // Wolt courier — older/alt package id
            "taxi.android.driver",        // FreeNow driver (taxi) — confirmed by user
            "com.stuart.courier"          // Stuart courier
        )

        fun isSupportedPackage(packageName: String): Boolean =
            packageName in SUPPORTED_PACKAGES || packageName in CustomPackagesState.packages
    }
}
