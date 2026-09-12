package com.wayars.app.domain.repository

import android.app.Activity
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.Offerings
import com.revenuecat.purchases.Package
import com.wayars.app.domain.model.SubscriptionState
import kotlinx.coroutines.flow.StateFlow

/**
 * Single seam between the rest of the app and RevenueCat. Everything that
 * decides whether the user is "pro" reads [subscriptionState] — nobody else
 * touches `Purchases.sharedInstance` directly.
 */
interface SubscriptionRepository {

    /** Latest known, server-verified gate state. Never trust anything else. */
    val subscriptionState: StateFlow<SubscriptionState>

    /** Re-fetches CustomerInfo from RevenueCat and updates [subscriptionState]. */
    suspend fun refreshCustomerInfo()

    /** Fetches the current offerings (weekly / monthly / yearly packages) for the paywall. */
    suspend fun getOfferings(): Result<Offerings>

    /** Launches the Play Billing purchase flow for [packageToPurchase]. */
    suspend fun purchase(activity: Activity, packageToPurchase: Package): Result<CustomerInfo>

    /** Re-syncs the Play Store purchase history for the current user (Restore Purchases). */
    suspend fun restore(): Result<CustomerInfo>
}
