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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

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
     */
    private fun observeAppForeground() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                applicationScope.launch {
                    container.subscriptionRepository.refreshCustomerInfo()
                }
            }
        })
    }

    override fun attachBaseContext(base: Context) {
        val languageCode = LanguagePrefs.read(base) ?: LocaleManager.resolveInitialLanguage()
        super.attachBaseContext(LocaleManager.wrap(base, languageCode))
    }
}
