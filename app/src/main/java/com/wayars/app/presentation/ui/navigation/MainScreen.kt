package com.wayars.app.presentation.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.wayars.app.domain.model.Currency
import com.wayars.app.domain.model.CustomThresholds
import com.wayars.app.domain.model.PresetType
import com.wayars.app.domain.model.VehicleProfile
import com.wayars.app.domain.repository.OrderRecord
import com.wayars.app.domain.model.OrderEvaluation
import com.wayars.app.presentation.TodaySummary
import com.wayars.app.presentation.ui.component.BottomNavBar
import com.wayars.app.presentation.ui.component.MainTab
import com.wayars.app.presentation.ui.component.UpdatePromptCard
import com.wayars.app.presentation.ui.component.bottomNavBarClearance
import com.wayars.app.presentation.ui.screen.dashboard.DashboardScreen
import com.wayars.app.presentation.ui.screen.dashboard.StatsScreen
import com.wayars.app.presentation.ui.screen.onboarding.PresetSelectionScreen
import com.wayars.app.presentation.ui.screen.settings.SettingsScreen
import com.wayars.app.presentation.ui.tour.LocalTourController
import com.wayars.app.presentation.ui.tour.TourController
import com.wayars.app.util.PlayUpdateChecker
import kotlinx.coroutines.delay

/**
 * Post-onboarding home: bottom-nav host for Home / Stats / Presets / Settings.
 *
 * Uses a plain Box instead of Scaffold(bottomBar = ...) on purpose: Scaffold
 * docks the bar full-width flush against the screen edge and applies its own
 * automatic inset handling, which is exactly what was producing the
 * mismatched dark strip near the system navigation bar. Layering the
 * floating pill nav bar over the content directly gives full control over
 * its own margin/inset handling instead (see BottomNavBar).
 *
 * This screen is only ever reached for a subscribed user (see WayArsNavHost), so it is also
 * where the first-run tour and the "update available" card are driven from.
 */
@Composable
fun MainScreen(
    summary: TodaySummary,
    latestEvaluation: OrderEvaluation?,
    todayOrders: List<OrderRecord>,
    languageCode: String,
    currency: Currency,
    preset: PresetType,
    termsAcceptedAt: Long?,
    customThresholds: CustomThresholds?,
    vehicleProfile: VehicleProfile,
    onLanguageSelected: (String) -> Unit,
    onCurrencySelected: (Currency) -> Unit,
    onPresetSelected: (PresetType) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onSaveCustomThresholds: (bad: Double, average: Double, good: Double) -> Unit,
    onClearCustomThresholds: () -> Unit,
    onSaveVehicleProfile: (VehicleProfile) -> Unit,
    customPackages: Set<String>,
    onAddCustomPackage: (String) -> Unit,
    onRemoveCustomPackage: (String) -> Unit,
    packageHints: Map<String, com.wayars.app.domain.model.PackageHint>,
    onSavePackageHint: (com.wayars.app.domain.model.PackageHint) -> Unit,
    onClearPackageHint: (String) -> Unit,
    isSubscribed: Boolean,
    onSubscriptionRequired: () -> Unit,
    tour: TourController,
    tourDone: Boolean?,
    onTourFinished: () -> Unit,
    updateSnoozeUntil: Long?,
    onUpdateLater: () -> Unit
) {
    var tab by remember { mutableStateOf(MainTab.HOME) }
    val settingsListState = rememberLazyListState()
    val context = LocalContext.current

    // ---------------------------------------------------------------- first-run tour
    SideEffect { tour.onFinished = onTourFinished }

    // Leaving this screen (e.g. the subscription lapsed -> paywall) must never leave the
    // overlay hanging over another screen. Not marked as done -- it was not completed.
    DisposableEffect(tour) { onDispose { tour.stop() } }

    // Starts once, on the Home screen, for a subscribed user who has not seen it yet.
    // tourDone is null until DataStore has loaded, so a returning user never sees a flash.
    LaunchedEffect(tourDone, isSubscribed) {
        if (tourDone == false && isSubscribed && !tour.running) {
            delay(900)
            tour.start()
        }
    }

    // Per step: show the right tab, scroll to the target, wait for it to be laid out, then
    // "settle" (the overlay reads the final position of the target from here).
    LaunchedEffect(tour.running, tour.index) {
        if (!tour.running) return@LaunchedEffect
        val step = tour.current
        tour.beginStep()
        val switching = tab != step.tab
        tab = step.tab
        delay(if (switching) 180L else 40L)

        val target = step.target
        if (step.settingsItem != null) {
            runCatching { settingsListState.animateScrollToItem(step.settingsItem) }
        } else if (target != null) {
            runCatching { tour.bringIntoView(target) }
        }
        var tries = 0
        while (target != null && !tour.isTargetReady(target) && tries < 10) {
            delay(100L)
            tries++
        }
        delay(120L)
        tour.settle()
    }

    // When the tour ends, land on the Home tab again.
    var tourWasRunning by remember { mutableStateOf(false) }
    LaunchedEffect(tour.running) {
        if (tourWasRunning && !tour.running) tab = MainTab.HOME
        tourWasRunning = tour.running
    }

    // ------------------------------------------------------- "update available" card
    // Checked once per visit to this screen: only for a subscribed user, only after the tour
    // is done, and not while the user has pressed "Later" (3-day snooze, see MainViewModel).
    var updateAvailable by remember { mutableStateOf(false) }
    var updateChecked by remember { mutableStateOf(false) }
    LaunchedEffect(isSubscribed, tourDone, updateSnoozeUntil) {
        if (updateChecked || !isSubscribed || tourDone != true || updateSnoozeUntil == null) {
            return@LaunchedEffect
        }
        if (System.currentTimeMillis() < updateSnoozeUntil) return@LaunchedEffect
        delay(2500)
        updateChecked = true
        updateAvailable = PlayUpdateChecker.isUpdateAvailable(context)
    }

    // No outer padding reserved for the pill anymore -- that reserved gap was
    // exactly what read as a separate-colored strip above the nav bar
    // (WaBackground behind it vs WaSurface cards above it). Content now
    // fills the full screen and scrolls BEHIND the floating pill instead;
    // each screen adds its own bottom inset as part of its scrollable
    // content (see their contentPadding/trailing spacer) so the last item
    // isn't permanently stuck under the opaque pill, without introducing a
    // separately-colored dead zone.
    CompositionLocalProvider(LocalTourController provides tour) {
        Box(modifier = Modifier.fillMaxSize()) {
            when (tab) {
                MainTab.HOME -> DashboardScreen(
                    summary = summary,
                    latestEvaluation = latestEvaluation,
                    isSubscribed = isSubscribed,
                    onSubscriptionRequired = onSubscriptionRequired
                )
                MainTab.STATS -> StatsScreen(orders = todayOrders)
                MainTab.PRESETS -> PresetSelectionScreen(
                    selected = preset,
                    onSelect = onPresetSelected,
                    onContinue = { tab = MainTab.HOME }
                )
                MainTab.SETTINGS -> SettingsScreen(
                    languageCode = languageCode,
                    currency = currency,
                    customThresholds = customThresholds,
                    vehicleProfile = vehicleProfile,
                    onLanguageSelected = onLanguageSelected,
                    onCurrencySelected = onCurrencySelected,
                    onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                    onOpenOverlaySettings = onOpenOverlaySettings,
                    onOpenNotificationSettings = onOpenNotificationSettings,
                    onSaveCustomThresholds = onSaveCustomThresholds,
                    onClearCustomThresholds = onClearCustomThresholds,
                    onSaveVehicleProfile = onSaveVehicleProfile,
                    customPackages = customPackages,
                    onAddCustomPackage = onAddCustomPackage,
                    onRemoveCustomPackage = onRemoveCustomPackage,
                    packageHints = packageHints,
                    onSavePackageHint = onSavePackageHint,
                    onClearPackageHint = onClearPackageHint,
                    termsAcceptedAt = termsAcceptedAt,
                    listState = settingsListState,
                    onReplayTour = { tour.start() }
                )
            }

            BottomNavBar(
                current = tab,
                onSelect = { tab = it },
                modifier = Modifier.align(Alignment.BottomCenter)
            )

            UpdatePromptCard(
                visible = updateAvailable && !tour.running,
                onUpdate = {
                    PlayUpdateChecker.openStorePage(context)
                    updateAvailable = false
                    onUpdateLater()
                },
                onLater = {
                    updateAvailable = false
                    onUpdateLater()
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 20.dp)
                    .padding(bottom = bottomNavBarClearance() + 8.dp)
            )
        }
    }
}
