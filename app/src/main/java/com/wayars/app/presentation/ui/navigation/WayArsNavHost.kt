package com.wayars.app.presentation.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.wayars.app.domain.model.SubscriptionState
import com.wayars.app.presentation.MainViewModel
import com.wayars.app.presentation.SubscriptionViewModel
import com.wayars.app.presentation.ui.screen.onboarding.PresetSelectionScreen
import com.wayars.app.presentation.ui.screen.paywall.PaywallScreen
import com.wayars.app.presentation.ui.screen.splash.SplashScreen
import com.wayars.app.presentation.ui.theme.WaBackground
import com.wayars.app.util.findActivity

private object Routes {
    const val SPLASH = "splash"
    const val PAYWALL = "paywall"
    const val ONBOARDING = "onboarding"
    const val MAIN = "main"
}

@Composable
fun WayArsNavHost(
    viewModel: MainViewModel,
    subscriptionViewModel: SubscriptionViewModel,
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

    val gateState by subscriptionViewModel.gateState.collectAsState()

    fun destinationAfterGate(): String = if (onboardingDone) Routes.MAIN else Routes.ONBOARDING

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
    // explicitly insets it. Only the TOP inset is applied here — the bottom
    // nav bar's own floating pill spacing already clears the system nav bar
    // correctly on its own, so padding for that stays where it already was
    // rather than doubling up.
    Box(modifier = Modifier.fillMaxSize().background(WaBackground)) {
        NavHost(
            navController = navController,
            startDestination = Routes.SPLASH,
            modifier = Modifier.fillMaxSize().statusBarsPadding()
        ) {
            composable(Routes.SPLASH) {
                // Strict gate: splash never hands off to onboarding/main on
                // its own. It waits for the splash animation AND a
                // resolved (non-Loading) subscription check, then routes to
                // either the paywall or the app based on server-verified
                // entitlement — never a locally cached flag.
                var splashAnimationDone by remember { mutableStateOf(false) }

                SplashScreen(onFinished = { splashAnimationDone = true })

                LaunchedEffect(splashAnimationDone, gateState) {
                    if (!splashAnimationDone) return@LaunchedEffect
                    when (gateState) {
                        is SubscriptionState.Loading -> Unit // keep waiting on splash
                        is SubscriptionState.Subscribed -> navController.navigate(destinationAfterGate()) {
                            popUpTo(Routes.SPLASH) { inclusive = true }
                        }
                        is SubscriptionState.NotSubscribed, is SubscriptionState.Error -> {
                            navController.navigate(Routes.PAYWALL) {
                                popUpTo(Routes.SPLASH) { inclusive = true }
                            }
                        }
                    }
                }
            }
            composable(Routes.PAYWALL) {
                val paywallState by subscriptionViewModel.paywallState.collectAsState()
                val context = LocalContext.current

                // If a purchase/restore succeeds (or, e.g., the user's
                // subscription was actually active all along and a retry
                // just confirmed it), gateState flips to Subscribed on its
                // own — leave the paywall automatically the moment it does.
                LaunchedEffect(gateState) {
                    if (gateState is SubscriptionState.Subscribed) {
                        navController.navigate(destinationAfterGate()) {
                            popUpTo(Routes.PAYWALL) { inclusive = true }
                        }
                    }
                }

                PaywallScreen(
                    state = paywallState,
                    onLoadOfferings = { subscriptionViewModel.loadOfferings() },
                    onSelectPlan = { pkg ->
                        context.findActivity()?.let { activity ->
                            subscriptionViewModel.purchase(activity, pkg)
                        }
                    },
                    onRestore = { subscriptionViewModel.restorePurchases() },
                    onDismissError = { subscriptionViewModel.clearError() },
                    gateErrorMessage = (gateState as? SubscriptionState.Error)?.message,
                    onRetryGateCheck = { subscriptionViewModel.retryGateCheck() }
                )
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
                // Belt-and-suspenders: if the entitlement lapses while the
                // user is already inside the app (checked on every
                // foreground, per WayArsApplication's ProcessLifecycleOwner
                // observer), drop them back to the paywall immediately
                // instead of waiting for their next app restart.
                LaunchedEffect(gateState) {
                    if (gateState is SubscriptionState.NotSubscribed) {
                        navController.navigate(Routes.PAYWALL) {
                            popUpTo(Routes.MAIN) { inclusive = true }
                        }
                    }
                }

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
