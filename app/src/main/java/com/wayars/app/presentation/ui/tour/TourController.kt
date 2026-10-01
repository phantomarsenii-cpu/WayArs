package com.wayars.app.presentation.ui.tour

import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import com.wayars.app.R
import com.wayars.app.presentation.ui.component.MainTab

/** Elements of the UI that the tour can spotlight. */
enum class TourTarget {
    ACTIVE_SWITCH,
    SUMMARY_CARD,
    VERDICT_CARD,
    NAV_STATS,
    NAV_PRESETS,
    SETTINGS_THRESHOLDS,
    SETTINGS_VEHICLE,
    SETTINGS_PERMISSIONS,
    SETTINGS_APPS
}

/**
 * One step of the tour.
 *  - [target] null = just a centered card over the dimmed screen.
 *  - [tab] the main tab that must be showing for this step.
 *  - [settingsItem] index of the LazyColumn item in SettingsScreen to scroll to first
 *    (0 title, 1 general, 2 thresholds, 3 vehicle, 4 permissions, 5 supported apps, 6 info).
 */
class TourStep(
    val target: TourTarget?,
    val tab: MainTab,
    val settingsItem: Int?,
    @StringRes val title: Int,
    @StringRes val text: Int
)

object TourSteps {
    val all: List<TourStep> = listOf(
        TourStep(null, MainTab.HOME, null, R.string.tour_welcome_title, R.string.tour_welcome_text),
        TourStep(TourTarget.ACTIVE_SWITCH, MainTab.HOME, null, R.string.tour_active_title, R.string.tour_active_text),
        TourStep(TourTarget.SUMMARY_CARD, MainTab.HOME, null, R.string.tour_summary_title, R.string.tour_summary_text),
        TourStep(TourTarget.VERDICT_CARD, MainTab.HOME, null, R.string.tour_verdict_title, R.string.tour_verdict_text),
        TourStep(TourTarget.NAV_STATS, MainTab.STATS, null, R.string.tour_stats_title, R.string.tour_stats_text),
        TourStep(TourTarget.NAV_PRESETS, MainTab.PRESETS, null, R.string.tour_presets_title, R.string.tour_presets_text),
        TourStep(TourTarget.SETTINGS_THRESHOLDS, MainTab.SETTINGS, 2, R.string.tour_thresholds_title, R.string.tour_thresholds_text),
        TourStep(TourTarget.SETTINGS_VEHICLE, MainTab.SETTINGS, 3, R.string.tour_vehicle_title, R.string.tour_vehicle_text),
        TourStep(TourTarget.SETTINGS_PERMISSIONS, MainTab.SETTINGS, 4, R.string.tour_permissions_title, R.string.tour_permissions_text),
        TourStep(TourTarget.SETTINGS_APPS, MainTab.SETTINGS, 5, R.string.tour_apps_title, R.string.tour_apps_text),
        TourStep(null, MainTab.HOME, null, R.string.tour_final_title, R.string.tour_final_text)
    )
}

/**
 * State of the first-run tour. Created once in the nav host and handed to MainScreen (which
 * switches tabs/scrolls per step) and to [TourHost] (which draws the overlay).
 *
 * Target positions are NOT kept in observable state (that would recompose on every layout
 * pass); they are read once from the stored LayoutCoordinates when a step has settled.
 */
@OptIn(ExperimentalFoundationApi::class)
@Stable
class TourController {
    val steps: List<TourStep> = TourSteps.all

    var running by mutableStateOf(false)
        private set
    var index by mutableIntStateOf(0)
        private set
    /** true once the tab switch / scroll of the current step is done and [targetRect] is final. */
    var settled by mutableStateOf(false)
        private set
    var targetRect by mutableStateOf<Rect?>(null)
        private set

    /** Called when the user finishes or skips the tour (not when it is stopped by leaving the screen). */
    var onFinished: (() -> Unit)? = null

    private val coords = HashMap<TourTarget, LayoutCoordinates>()
    private val requesters = HashMap<TourTarget, BringIntoViewRequester>()

    val current: TourStep get() = steps[index]

    fun start() {
        index = 0
        settled = false
        targetRect = null
        running = true
    }

    fun next() { if (index >= steps.lastIndex) finish() else index++ }

    fun back() { if (index <= 0) finish() else index-- }

    fun finish() {
        if (!running) return
        stop()
        onFinished?.invoke()
    }

    /** Hides the tour without marking it as completed (e.g. the screen was left). */
    fun stop() {
        running = false
        settled = false
        targetRect = null
    }

    fun beginStep() {
        settled = false
        targetRect = null
    }

    fun settle() {
        val t = current.target
        targetRect = if (t == null) null
        else coords[t]?.takeIf { it.isAttached }?.boundsInWindow()
        settled = true
    }

    fun isTargetReady(target: TourTarget): Boolean = coords[target]?.isAttached == true

    suspend fun bringIntoView(target: TourTarget) {
        requesters[target]?.bringIntoView()
    }

    internal fun register(target: TourTarget, c: LayoutCoordinates) { coords[target] = c }
    internal fun register(target: TourTarget, r: BringIntoViewRequester) { requesters[target] = r }
    internal fun unregister(target: TourTarget) { coords.remove(target); requesters.remove(target) }
}

val LocalTourController = staticCompositionLocalOf<TourController?> { null }

/**
 * Marks a composable as a tour target. Costs nothing when no tour controller is provided and
 * only stores a reference (no state writes) otherwise.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.tourTarget(target: TourTarget?): Modifier {
    if (target == null) return this
    val controller = LocalTourController.current ?: return this
    val requester = remember(target) { BringIntoViewRequester() }
    DisposableEffect(controller, target) {
        controller.register(target, requester)
        onDispose { controller.unregister(target) }
    }
    return this
        .bringIntoViewRequester(requester)
        .onGloballyPositioned { controller.register(target, it) }
}
