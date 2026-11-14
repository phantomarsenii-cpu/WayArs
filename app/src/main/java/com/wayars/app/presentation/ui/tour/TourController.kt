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
    SETTINGS_CURRENCY,
    SETTINGS_THRESHOLDS,
    SETTINGS_VEHICLE,
    SETTINGS_TAX,
    SETTINGS_PERMISSIONS,
    SETTINGS_APPS,
    SETTINGS_SUBSCRIPTION,
    SETTINGS_INFO,
    SETTINGS_PARTNER,
    SETTINGS_PET,
    SETTINGS_EXTRAS,
    STATS_HOURLY,
    PET_CARD
}

/**
 * One step of the tour.
 *  - [target] null = just a centered card over the dimmed screen.
 *  - [tab] the main tab that must be showing for this step.
 *  - [settingsItem] index of the LazyColumn item in SettingsScreen to scroll to first
 *    (0 title, 1 subscription, 2 general/currency, 3 thresholds, 4 vehicle, 5 tax, 6 permissions,
 *    7 supported apps, 8 info).
 */
class TourStep(
    val target: TourTarget?,
    val tab: MainTab,
    val settingsItem: Int?,
    @StringRes val title: Int,
    @StringRes val text: Int
)

/** Item positions in the Settings LazyColumn (used to scroll the tour / helper to a card). */
object SettingsItems {
    const val SUBSCRIPTION = 1
    const val GENERAL = 2
    const val THRESHOLDS = 3
    const val VEHICLE = 4
    const val TAX = 5
    const val PARTNER = 6
    const val PERMISSIONS = 7
    const val APPS = 8
    const val PET = 9
    const val EXTRAS = 10
    const val INFO = 11
}

object TourSteps {
    val all: List<TourStep> = listOf(
        TourStep(null, MainTab.HOME, null, R.string.tour_welcome_title, R.string.tour_welcome_text),
        TourStep(TourTarget.ACTIVE_SWITCH, MainTab.HOME, null, R.string.tour_active_title, R.string.tour_active_text),
        TourStep(TourTarget.SUMMARY_CARD, MainTab.HOME, null, R.string.tour_summary_title, R.string.tour_summary_text),
        TourStep(TourTarget.VERDICT_CARD, MainTab.HOME, null, R.string.tour_verdict_title, R.string.tour_verdict_text),
        TourStep(TourTarget.PET_CARD, MainTab.HOME, null, R.string.tour_pet_title, R.string.tour_pet_text),
        TourStep(TourTarget.NAV_STATS, MainTab.STATS, null, R.string.tour_stats_title, R.string.tour_stats_text),
        TourStep(TourTarget.NAV_PRESETS, MainTab.PRESETS, null, R.string.tour_presets_title, R.string.tour_presets_text),
        TourStep(TourTarget.SETTINGS_CURRENCY, MainTab.SETTINGS, 2, R.string.tour_currency_title, R.string.tour_currency_text),
        TourStep(TourTarget.SETTINGS_THRESHOLDS, MainTab.SETTINGS, 3, R.string.tour_thresholds_title, R.string.tour_thresholds_text),
        TourStep(TourTarget.SETTINGS_VEHICLE, MainTab.SETTINGS, 4, R.string.tour_vehicle_title, R.string.tour_vehicle_text),
        TourStep(TourTarget.SETTINGS_TAX, MainTab.SETTINGS, SettingsItems.TAX, R.string.tour_tax_title, R.string.tour_tax_text),
        TourStep(TourTarget.SETTINGS_PARTNER, MainTab.SETTINGS, SettingsItems.PARTNER, R.string.tour_partner_title, R.string.tour_partner_text),
        TourStep(TourTarget.SETTINGS_PERMISSIONS, MainTab.SETTINGS, SettingsItems.PERMISSIONS, R.string.tour_permissions_title, R.string.tour_permissions_text),
        TourStep(TourTarget.SETTINGS_APPS, MainTab.SETTINGS, SettingsItems.APPS, R.string.tour_apps_title, R.string.tour_apps_text),
        TourStep(TourTarget.SETTINGS_PET, MainTab.SETTINGS, SettingsItems.PET, R.string.settings_pet_title, R.string.tour_petset_text),
        TourStep(TourTarget.SETTINGS_EXTRAS, MainTab.SETTINGS, SettingsItems.EXTRAS, R.string.tour_extras_title, R.string.tour_extras_text),
        TourStep(null, MainTab.HOME, null, R.string.tour_final_title, R.string.tour_final_text)
    )

    /** Shown once to drivers who finished the tour before these features existed. */
    val whatsNew: List<TourStep> = listOf(
        TourStep(null, MainTab.HOME, null, R.string.tour_new_title, R.string.tour_new_text),
        TourStep(TourTarget.PET_CARD, MainTab.HOME, null, R.string.tour_pet_title, R.string.tour_pet_text),
        TourStep(TourTarget.NAV_STATS, MainTab.STATS, null, R.string.tour_stats_title, R.string.tour_stats_text),
        TourStep(TourTarget.SETTINGS_PARTNER, MainTab.SETTINGS, SettingsItems.PARTNER, R.string.tour_partner_title, R.string.tour_partner_text),
        TourStep(TourTarget.SETTINGS_PET, MainTab.SETTINGS, SettingsItems.PET, R.string.settings_pet_title, R.string.tour_petset_text),
        TourStep(TourTarget.SETTINGS_EXTRAS, MainTab.SETTINGS, SettingsItems.EXTRAS, R.string.tour_extras_title, R.string.tour_extras_text)
    )

    /** For drivers who already saw [whatsNew]: only Wayo's rewards. */
    val whatsNewRewards: List<TourStep> = listOf(
        TourStep(null, MainTab.HOME, null, R.string.tour_new_title, R.string.tour_new2_text),
        TourStep(TourTarget.SETTINGS_PET, MainTab.SETTINGS, SettingsItems.PET, R.string.settings_pet_title, R.string.tour_petset_text)
    )
}

/** Remembers whether the current "what's new" tour was already shown (bump [VERSION] for the next one). */
object WhatsNewTour {
    private const val PREFS = "tour_prefs"
    private const val VERSION = 2
    private const val KEY = "whats_new_seen"

    fun isSeen(context: android.content.Context): Boolean =
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).getInt(KEY, 0) >= VERSION

    /** What to show: everything new for drivers who never saw it, otherwise only what came after. */
    fun steps(context: android.content.Context): List<TourStep> =
        if (context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).getInt(KEY, 0) >= 1)
            TourSteps.whatsNewRewards else TourSteps.whatsNew

    fun markSeen(context: android.content.Context) {
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).edit().putInt(KEY, VERSION).apply()
    }
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
    /** The full tour, or a single step when the pet helper points at one setting. */
    var steps: List<TourStep> by mutableStateOf(TourSteps.all)
        private set
    private var singleStep = false

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
        steps = TourSteps.all
        singleStep = false
        index = 0
        settled = false
        targetRect = null
        running = true
    }

    /** A short tour of a few steps (e.g. "what's new"); doesn't count as finishing the full tour. */
    fun startSteps(list: List<TourStep>) {
        if (list.isEmpty()) return
        steps = list
        singleStep = true
        index = 0
        settled = false
        targetRect = null
        running = true
    }

    /** Spotlights ONE element (used by the pet helper's "Show me"); doesn't count as finishing the tour. */
    fun startSingle(step: TourStep) {
        steps = listOf(step)
        singleStep = true
        index = 0
        settled = false
        targetRect = null
        running = true
    }

    fun next() { if (index >= steps.lastIndex) finish() else index++ }

    fun back() { if (index <= 0) finish() else index-- }

    fun finish() {
        if (!running) return
        val wasSingle = singleStep
        stop()
        singleStep = false
        if (!wasSingle) onFinished?.invoke()
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
