package com.wayars.app.data.repository

import android.app.Activity
import android.util.Log
import com.revenuecat.purchases.CacheFetchPolicy
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.Offerings
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesException
import com.revenuecat.purchases.awaitCustomerInfo
import com.revenuecat.purchases.awaitOfferings
import com.revenuecat.purchases.awaitPurchase
import com.revenuecat.purchases.awaitRestore
import com.wayars.app.billing.RevenueCatConfig
import com.wayars.app.domain.model.SubscriptionState
import com.wayars.app.domain.repository.SubscriptionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "SubscriptionRepo"

class SubscriptionRepositoryImpl : SubscriptionRepository {

    private val _subscriptionState = MutableStateFlow<SubscriptionState>(SubscriptionState.Loading)
    override val subscriptionState: StateFlow<SubscriptionState> = _subscriptionState.asStateFlow()

    override suspend fun refreshCustomerInfo() {
        try {
            // IMPORTANT: the default fetch policy (cachedOrFetched) is allowed
            // to return the on-device cache even when it's stale, with no
            // network call at all. Right after a fresh purchase that stale
            // cache can still be the pre-purchase snapshot (e.g. process was
            // killed before the post-purchase cache write settled), which
            // made the gate check wrongly report NotSubscribed and show the
            // paywall — even though the entitlement was active on the server
            // (confirmed by Restore, which always forces a real network
            // sync). FETCH_CURRENT forces this specific check to always hit
            // the network so it reflects true server state, same as Restore.
            val info = Purchases.sharedInstance.awaitCustomerInfo(CacheFetchPolicy.FETCH_CURRENT)
            applyCustomerInfo(info)
        } catch (e: PurchasesException) {
            Log.w(TAG, "Failed to fetch CustomerInfo", e)
            // Don't blindly fall back to "not subscribed" on a network hiccup —
            // that would boot an already-paying user back to the paywall just
            // because they opened the app offline. Surface it distinctly so
            // the UI can retry instead of silently re-locking the app.
            _subscriptionState.value = SubscriptionState.Error(e.error.message)
        }
    }

    override suspend fun getOfferings(): Result<Offerings> = runCatching {
        Purchases.sharedInstance.awaitOfferings()
    }

    override suspend fun purchase(activity: Activity, packageToPurchase: Package): Result<CustomerInfo> =
        runCatching {
            val params = PurchaseParams.Builder(activity, packageToPurchase).build()
            val result = Purchases.sharedInstance.awaitPurchase(params)
            applyCustomerInfo(result.customerInfo)
            result.customerInfo
        }

    override suspend fun restore(): Result<CustomerInfo> = runCatching {
        val info = Purchases.sharedInstance.awaitRestore()
        applyCustomerInfo(info)
        info
    }

    /**
     * The ONLY place that decides "is this user pro". Always derived from
     * RevenueCat's server-verified CustomerInfo — never from a raw local
     * flag — so there's nothing on-device to spoof.
     */
    private fun applyCustomerInfo(info: CustomerInfo) {
        val isActive = info.entitlements[RevenueCatConfig.ENTITLEMENT_PRO]?.isActive == true
        _subscriptionState.value =
            if (isActive) SubscriptionState.Subscribed else SubscriptionState.NotSubscribed
    }
}
