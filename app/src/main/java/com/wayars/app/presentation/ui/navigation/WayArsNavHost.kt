package com.wayars.app.presentation.ui.navigation

import android.widget.Toast
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
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.wayars.app.R
import com.wayars.app.domain.model.SubscriptionState
import com.wayars.app.presentation.MainViewModel
import com.wayars.app.presentation.PlanType
import com.wayars.app.presentation.SubscriptionViewModel
import com.wayars.app.presentation.ui.screen.onboarding.PresetSelectionScreen
import com.wayars.app.presentation.ui.screen.paywall.PaywallScreen
import com.wayars.app.presentation.ui.screen.splash.SplashScreen
import com.wayars.app.presentation.ui.screen.terms.TermsGateScreen
import com.wayars.app.presentation.ui.theme.WaBackground
import com.wayars.app.service.accessibility.ScanningState
import com.wayars.app.util.findActivity

private object Routes {
    const val SPLASH = "splash"
    const val TERMS = "terms"
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
    val termsAcceptedAt by viewModel.termsAcceptedAt.collectAsState()
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

    // Shared by both the Splash and Terms screens: once whichever of them
    // is currently showing has nothing left to wait on, this is what
    // decides where to go next — the paywall, or straight into the app —
    // based on the server-verified subscription state. Kept in one place
    // so the two screens can't drift into different gating behavior.
    fun navigateFromGate(fromRoute: String) {
        when (gateState) {
            is SubscriptionState.Loading -> Unit // keep waiting
            is SubscriptionState.Subscribed -> navController.navigate(destinationAfterGate()) {
                popUpTo(fromRoute) { inclusive = true }
            }
            is SubscriptionState.NotSubscribed, is SubscriptionState.Error -> {
                navController.navigate(Routes.PAYWALL) {
                    popUpTo(fromRoute) { inclusive = true }
                }
            }
        }
    }

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
                // its own. It waits for the splash animation, then — the
                // very first thing checked, before subscription status —
                // whether the Terms of Use have ever been accepted on this
                // install. Unaccepted terms always win: the user is sent to
                // the Terms gate regardless of subscription state, and only
                // reaches the paywall/app afterward (see Routes.TERMS
                // below). Once terms are accepted, this falls through to
                // the same subscription check as before: a resolved
                // (non-Loading) check, then routes to either the paywall or
                // the app based on server-verified entitlement — never a
                // locally cached flag.
                var splashAnimationDone by remember { mutableStateOf(false) }

                SplashScreen(onFinished = { splashAnimationDone = true })

                LaunchedEffect(splashAnimationDone, termsAcceptedAt, gateState) {
                    if (!splashAnimationDone) return@LaunchedEffect
                    if (termsAcceptedAt == null) {
                        navController.navigate(Routes.TERMS) {
                            popUpTo(Routes.SPLASH) { inclusive = true }
                        }
                        return@LaunchedEffect
                    }
                    navigateFromGate(Routes.SPLASH)
                }
            }
            composable(Routes.TERMS) {
                // No back button, no dismiss, no "decide later" — this
                // route is only ever left via TermsGateScreen's own Accept
                // button, which is itself disabled until the checkbox is
                // ticked (see TermsGateScreen). Accepting persists a
                // one-way timestamp (MainViewModel.acceptTerms ->
                // SettingsDataStore.setTermsAccepted) that this app never
                // offers a way to clear, so this screen only ever shows
                // once per install.
                TermsGateScreen(onAccept = { viewModel.acceptTerms() })

                // Mirrors the Splash screen's own gate logic (see above),
                // just entered from here: once acceptance has actually
                // persisted (termsAcceptedAt flips non-null), proceed to
                // the paywall or the app the same way Splash would have.
                LaunchedEffect(termsAcceptedAt, gateState) {
                    if (termsAcceptedAt == null) return@LaunchedEffect
                    navigateFromGate(Routes.TERMS)
                }
            }
            composable(Routes.PAYWALL) {
                val paywallState by subscriptionViewModel.paywallState.collectAsState()
                val context = LocalContext.current

                // Captured here (composable scope) rather than inside the
                // click lambda below, since stringResource() can only be
                // called from composable code.
                val planTitles = mapOf(
                    PlanType.YEARLY to stringResource(R.string.paywall_plan_yearly_title),
                    PlanType.MONTHLY to stringResource(R.string.paywall_plan_monthly_title),
                    PlanType.WEEKLY to stringResource(R.string.paywall_plan_weekly_title)
                )
                val mockSelectedToast = stringResource(R.string.paywall_mock_selected_toast)

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
                    onSelectPlan = { plan ->
                        val rcPackage = plan.rcPackage
                        if (rcPackage != null) {
                            context.findActivity()?.let { activity ->
                                subscriptionViewModel.purchase(activity, rcPackage)
                            }
                        } else {
                            // Offline / offerings not loaded / product not
                            // yet created in Play Console: no live Package
                            // to purchase. Fall back to a local selection +
                            // toast so the tap still visibly does something
                            // instead of the button looking unresponsive.
                            subscriptionViewModel.selectMockPlan(plan.plan)
                            val planName = planTitles[plan.plan].orEmpty()
                            Toast.makeText(
                                context,
                                String.format(mockSelectedToast, planName),
                                Toast.LENGTH_SHORT
                            ).show()
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
                //
                // Getting bounced to the paywall isn't enough on its own,
                // though: scanning (ScanningState) is a separate switch that
                // keeps running until something explicitly turns it off, so
                // without this the accessibility service just kept scanning
                // behind the paywall for anyone who didn't also flip the
                // Dashboard "Active" toggle by hand. Turn it off here, same
                // as tapping that toggle off, the moment the paywall shows
                // for a lapsed subscription.
                val mainContext = LocalContext.current
                LaunchedEffect(gateState) {
                    if (gateState is SubscriptionState.NotSubscribed) {
                        ScanningState.setActive(false, mainContext)
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
                    termsAcceptedAt = termsAcceptedAt,
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
