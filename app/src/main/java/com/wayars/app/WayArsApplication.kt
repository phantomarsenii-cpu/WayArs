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
import com.wayars.app.util.LocaleManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

// How often the subscription entitlement is re-checked while the app stays
// in the foreground. Short enough that a lapsed trial/subscription is
// caught within one interval of it actually expiring, long enough not to
// hammer RevenueCat/Play Billing for an app that's just sitting open.
private const val SUBSCRIPTION_POLL_INTERVAL_MS = 60_000L

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
     * app was backgrounded). ProcessLifecycleOwner fires ON_START exactly
     * once per app-wide foreground transition — not per Activity — so this
     * doesn't double-fire on every screen rotation or Activity recreation.
     *
     * ON_START alone isn't enough on its own, though: a driver who leaves
     * the app open and in the foreground the whole time (the normal case —
     * they're actively working with it running) never triggers another
     * ON_START, so a subscription/trial that expires mid-session was never
     * being re-checked at all until the user backgrounded and reopened the
     * app. Keep polling on an interval for as long as the app stays
     * foregrounded, and stop the moment it backgrounds (ON_STOP) so this
     * never runs, and never burns battery/network, while the app isn't
     * actually in use.
     */
    private var foregroundPollingJob: Job? = null

    private fun observeAppForeground() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                foregroundPollingJob?.cancel()
                foregroundPollingJob = applicationScope.launch {
                    while (isActive) {
                        container.subscriptionRepository.refreshCustomerInfo()
                        delay(SUBSCRIPTION_POLL_INTERVAL_MS)
                    }
                }
            }

            override fun onStop(owner: LifecycleOwner) {
                foregroundPollingJob?.cancel()
                foregroundPollingJob = null
            }
        })
    }

    override fun attachBaseContext(base: Context) {
        val languageCode = LanguagePrefs.read(base) ?: LocaleManager.resolveInitialLanguage()
        super.attachBaseContext(LocaleManager.wrap(base, languageCode))
    }
}
