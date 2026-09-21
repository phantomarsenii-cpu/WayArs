package com.wayars.app

import android.app.Application
import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.revenuecat.purchases.LogLevel
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import com.wayars.app.billing.RevenueCatConfig
import com.wayars.app.data.prefs.LanguagePrefs
import com.wayars.app.domain.model.SubscriptionState
import com.wayars.app.service.accessibility.ScanningState
import com.wayars.app.util.LocaleManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

// How often the subscription entitlement is re-checked. Short enough that a
// lapsed trial/subscription is caught reasonably quickly after it actually
// expires, long enough not to hammer RevenueCat/Play Billing for an app
// that's just sitting open or scanning in the background.
private const val SUBSCRIPTION_POLL_INTERVAL_MS = 30_000L

class WayArsApplication : Application() {

    lateinit var container: AppContainer
        private set

    // App-scoped: outlives any single Activity/ViewModel, which matters
    // here specifically because the foreground-refresh observer below is
    // registered on the process lifecycle, not an Activity's.
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        configureRevenueCat()
        observeAppForeground()
        persistResolvedLanguageIfMissing()
    }

    /**
     * attachBaseContext() below resolves and *applies* a language on every
     * cold start (device locale, if supported, else English), but until now
     * that resolved value was never written back to LanguagePrefs/DataStore
     * unless the user explicitly opened Settings and picked a language.
     * Result: the UI could be running in Russian (correctly auto-detected)
     * while MainViewModel.languageCode stayed null and the Settings screen
     * fell back to its "en" default — a real mismatch between what's
     * displayed and what's shown as selected. Persist the resolved value
     * once, right after first launch, so both stay in sync from then on.
     */
    private fun persistResolvedLanguageIfMissing() {
        if (LanguagePrefs.read(this) != null) return
        val resolved = LocaleManager.resolveInitialLanguage()
        applicationScope.launch {
            container.settingsRepository.setLanguage(resolved)
        }
    }

    private fun configureRevenueCat() {
        // Verbose while the integration beds in; safe to leave on for a
        // beta build, but drop to LogLevel.WARN (or DEBUG only in a debug
        // build type) before a wide production release.
        Purchases.logLevel = LogLevel.DEBUG
        Purchases.configure(
            PurchasesConfiguration.Builder(this, RevenueCatConfig.API_KEY).build()
        )
    }

    /**
     * Requirement: subscription status must be re-checked whenever the app
     * returns to the foreground (e.g. a trial/subscription lapsed while the
     * app was backgrounded), AND for as long as background order-scanning
     * keeps running — that's the whole point of the accessibility service:
     * it's designed to keep monitoring other apps (Uber/Bolt/Wolt/...)
     * while WayArs itself is minimized. Only stopping the poll on ON_STOP
     * (as before) meant a subscription that expired while the app was
     * minimized was never re-checked at all until the user manually
     * reopened it — scanning kept running the whole time regardless of
     * entitlement. So this now keeps polling in the background too, as
     * long as [ScanningState.isActive] is true, and only pauses once
     * neither condition holds (app backgrounded AND scanning off).
     *
     * It also gates scanning off DIRECTLY from here the moment a lapsed
     * entitlement is confirmed, instead of only relying on the UI layer's
     * own LaunchedEffect(gateState) in WayArsNavHost. That UI-side gate
     * still runs (and still handles navigating to the paywall) whenever the
     * app is actually visible, but Compose recomposition is paused while
     * the window is backgrounded, so that effect alone could sit un-fired
     * for as long as the app stayed minimized. Turning ScanningState off
     * here doesn't depend on recomposition at all, so scanning stops the
     * moment expiration is detected, foreground or not.
     */
    private var pollingJob: Job? = null
    private var isAppForegrounded = false

    private fun observeAppForeground() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                isAppForegrounded = true
                startPollingIfNeeded()
            }

            override fun onStop(owner: LifecycleOwner) {
                isAppForegrounded = false
                // Keep the loop alive if scanning is still running in the
                // background — see the class doc above. If scanning is off,
                // there's nothing left to check for until the app is opened
                // again, so let the loop stop itself on its next iteration.
            }
        })

        // Scanning can be switched on from the Dashboard toggle while the
        // app is in the foreground (the normal case) — already covered by
        // onStart above since the app is foregrounded at that point too.
        // This extra observer only matters for the (rarer) case where
        // scanning state changes while the polling loop had already
        // stopped — e.g. a manual retry — so a fresh loop always exists to
        // pick it back up.
        applicationScope.launch {
            ScanningState.isActive.collect { active ->
                if (active) startPollingIfNeeded()
            }
        }
    }

    private fun startPollingIfNeeded() {
        if (pollingJob?.isActive == true) return
        pollingJob = applicationScope.launch {
            while (isActive) {
                container.subscriptionRepository.refreshCustomerInfo()

                val stillSubscribed =
                    container.subscriptionRepository.subscriptionState.value is SubscriptionState.Subscribed
                if (!stillSubscribed && ScanningState.isActive.value) {
                    ScanningState.setActive(false, this@WayArsApplication)
                }

                // Nothing left to watch for: the app isn't visible and
                // scanning isn't running — stop until either comes back.
                if (!isAppForegrounded && !ScanningState.isActive.value) break

                delay(SUBSCRIPTION_POLL_INTERVAL_MS)
            }
            pollingJob = null
        }
    }

    override fun attachBaseContext(base: Context) {
        val languageCode = LanguagePrefs.read(base) ?: LocaleManager.resolveInitialLanguage()
        super.attachBaseContext(LocaleManager.wrap(base, languageCode))
    }
}
