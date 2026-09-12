package com.wayars.app.presentation.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.wayars.app.domain.model.Currency
import com.wayars.app.domain.model.CustomThresholds
import com.wayars.app.domain.model.PresetType
import com.wayars.app.domain.model.VehicleProfile
import com.wayars.app.presentation.MainViewModel
import com.wayars.app.presentation.ui.screen.onboarding.PresetSelectionScreen
import com.wayars.app.presentation.ui.screen.splash.SplashScreen
import com.wayars.app.presentation.ui.theme.WaBackground

private object Routes {
    const val SPLASH = "splash"
    const val ONBOARDING = "onboarding"
    const val MAIN = "main"
}

@Composable
fun WayArsNavHost(
    viewModel: MainViewModel,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onOpenNotificationSettings: () -> Unit
) {
    val navController: NavHostController = rememberNavController()

    val onboardingDone by viewModel.onboardingDone.collectAsState()
    val preset by viewModel.preset.collectAsState()
    val currency by viewModel.currency.collectAsState()
    val languageCode by viewModel.languageCode.collectAsState()
    val summary by viewModel.todaySummary.collectAsState()
    val latestEvaluation by viewModel.latestEvaluation.collectAsState()
    val todayOrders by viewModel.todayOrders.collectAsState()
    val customThresholds by viewModel.customThresholds.collectAsState()
    val vehicleProfile by viewModel.vehicleProfile.collectAsState()
    val customPackages by viewModel.customPackages.collectAsState()
    val packageHints by viewModel.packageHints.collectAsState()

    // Fills the ENTIRE screen, including the area behind the (now
    // transparent, edge-to-edge) system status/navigation bars, with the
    // app's own background color. This is what actually fixes the
    // mismatched dark strip that kept showing near the nav bar — trying to
    // tint the system bar itself via Window APIs was fighting edge-to-edge
    // and behaving inconsistently across devices; painting our own
    // background behind everything sidesteps that entirely.
    //
    // The NavHost itself gets `.statusBarsPadding()` on top of that — edge-
    // to-edge means content draws UNDER the status bar unless something
    // explicitly insets it, which is exactly what screenshots showed
    // (2026-09-12): screen titles ("Wybierz swój profil", "Ustawienia")
    // rendering flush against/under the status bar icons. Only the TOP
    // inset is applied here — the bottom nav bar's own floating pill
    // spacing already clears the system nav bar correctly on its own, so
    // padding for that stays where it already was rather than doubling up.
    Box(modifier = Modifier.fillMaxSize().background(WaBackground)) {
        NavHost(
            navController = navController,
            startDestination = Routes.SPLASH,
            modifier = Modifier.fillMaxSize().statusBarsPadding()
        ) {
            composable(Routes.SPLASH) {
                SplashScreen(onFinished = {
                    val dest = if (onboardingDone) Routes.MAIN else Routes.ONBOARDING
                    navController.navigate(dest) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                })
            }
            composable(Routes.ONBOARDING) {
                PresetSelectionScreen(
                    selected = preset,
                    onSelect = { viewModel.setPreset(it) },
                    onContinue = {
                        viewModel.completeOnboarding()
                        navController.navigate(Routes.MAIN) {
                            popUpTo(Routes.ONBOARDING) { inclusive = true }
                        }
                    }
                )
            }
            composable(Routes.MAIN) {
                MainScreen(
                    summary = summary,
                    latestEvaluation = latestEvaluation,
                    todayOrders = todayOrders,
                    languageCode = languageCode ?: "en",
                    currency = currency,
                    preset = preset,
                    onLanguageSelected = { viewModel.setLanguage(it) },
                    onCurrencySelected = { viewModel.setCurrency(it) },
                    onPresetSelected = { viewModel.setPreset(it) },
                    onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                    onOpenOverlaySettings = onOpenOverlaySettings,
                    onOpenNotificationSettings = onOpenNotificationSettings,
                    customThresholds = customThresholds,
                    vehicleProfile = vehicleProfile,
                    onSaveCustomThresholds = { bad, average, good -> viewModel.setCustomThresholds(bad, average, good) },
                    onClearCustomThresholds = { viewModel.clearCustomThresholds() },
                    onSaveVehicleProfile = { viewModel.setVehicleProfile(it) },
                    customPackages = customPackages,
                    onAddCustomPackage = { viewModel.addCustomPackage(it) },
                    onRemoveCustomPackage = { viewModel.removeCustomPackage(it) },
                    packageHints = packageHints,
                    onSavePackageHint = { viewModel.savePackageHint(it) },
                    onClearPackageHint = { viewModel.clearPackageHint(it) }
                )
            }
        }
    }
}
