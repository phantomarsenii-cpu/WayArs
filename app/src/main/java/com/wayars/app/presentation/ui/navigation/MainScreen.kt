package com.wayars.app.presentation.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.wayars.app.domain.model.Currency
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
 */
@Composable
fun MainScreen(
    summary: TodaySummary,
    latestEvaluation: OrderEvaluation?,
    todayOrders: List<OrderRecord>,
    languageCode: String,
    currency: Currency,
    preset: PresetType,
    onLanguageSelected: (String) -> Unit,
    onCurrencySelected: (Currency) -> Unit,
    onPresetSelected: (PresetType) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenOverlaySettings: () -> Unit
) {
    var tab by remember { mutableStateOf(MainTab.HOME) }

    Scaffold(
        bottomBar = { BottomNavBar(current = tab, onSelect = { tab = it }) }
    ) { padding ->
        val content = Modifier.padding(padding)
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
                preset = preset,
                onLanguageSelected = onLanguageSelected,
                onCurrencySelected = onCurrencySelected,
                onPresetSelected = onPresetSelected,
                onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                onOpenOverlaySettings = onOpenOverlaySettings,
                modifier = content
            )
        }
    }
}
