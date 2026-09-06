package com.wayars.app.presentation.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wayars.app.domain.model.Currency
import com.wayars.app.domain.model.CustomThresholds
import com.wayars.app.domain.model.PresetType
import com.wayars.app.domain.repository.OrderRecord
import com.wayars.app.domain.model.OrderEvaluation
import com.wayars.app.presentation.TodaySummary
import com.wayars.app.presentation.ui.component.BottomNavBar
import com.wayars.app.presentation.ui.component.MainTab
import com.wayars.app.presentation.ui.screen.dashboard.DashboardScreen
import com.wayars.app.presentation.ui.screen.dashboard.StatsScreen
import com.wayars.app.presentation.ui.screen.onboarding.PresetSelectionScreen
import com.wayars.app.presentation.ui.screen.settings.SettingsScreen

/**
 * Post-onboarding home: bottom-nav host for Home / Stats / Presets / Settings.
 *
 * Uses a plain Box instead of Scaffold(bottomBar = ...) on purpose: Scaffold
 * docks the bar full-width flush against the screen edge and applies its own
 * automatic inset handling, which is exactly what was producing the
 * mismatched dark strip near the system navigation bar. Layering the
 * floating pill nav bar over the content directly gives full control over
 * its own margin/inset handling instead (see BottomNavBar).
 */
@Composable
fun MainScreen(
    summary: TodaySummary,
    latestEvaluation: OrderEvaluation?,
    todayOrders: List<OrderRecord>,
    languageCode: String,
    currency: Currency,
    preset: PresetType,
    customThresholds: CustomThresholds?,
    onLanguageSelected: (String) -> Unit,
    onCurrencySelected: (Currency) -> Unit,
    onPresetSelected: (PresetType) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onSaveCustomThresholds: (bad: Double, average: Double, good: Double) -> Unit,
    onClearCustomThresholds: () -> Unit
) {
    var tab by remember { mutableStateOf(MainTab.HOME) }

    // Extra bottom padding so scrollable content never sits underneath the
    // floating pill bar.
    val content = Modifier.padding(PaddingValues(bottom = 90.dp))

    Box(modifier = Modifier.fillMaxSize()) {
        when (tab) {
            MainTab.HOME -> DashboardScreen(summary = summary, latestEvaluation = latestEvaluation, modifier = content)
            MainTab.STATS -> StatsScreen(orders = todayOrders, modifier = content)
            MainTab.PRESETS -> PresetSelectionScreen(
                selected = preset,
                onSelect = onPresetSelected,
                onContinue = { tab = MainTab.HOME },
                modifier = content
            )
            MainTab.SETTINGS -> SettingsScreen(
                languageCode = languageCode,
                currency = currency,
                customThresholds = customThresholds,
                onLanguageSelected = onLanguageSelected,
                onCurrencySelected = onCurrencySelected,
                onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                onOpenOverlaySettings = onOpenOverlaySettings,
                onOpenNotificationSettings = onOpenNotificationSettings,
                onSaveCustomThresholds = onSaveCustomThresholds,
                onClearCustomThresholds = onClearCustomThresholds,
                modifier = content
            )
        }

        BottomNavBar(
            current = tab,
            onSelect = { tab = it },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
