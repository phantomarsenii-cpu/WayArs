package com.wayars.app.domain.model

/**
 * Current, server-verified subscription status for the [RevenueCatConfig.ENTITLEMENT_PRO]
 * entitlement. This is intentionally derived ONLY from
 * `CustomerInfo.entitlements[...].isActive` (RevenueCat's own server-side
 * verified truth) — never from a locally cached boolean flag — so there is
 * nothing on-device for a user to flip to fake access.
 */
sealed interface SubscriptionState {
    /** Initial state while the first CustomerInfo fetch is in flight. */
    data object Loading : SubscriptionState

    /** Entitlement is active — user may access the app's main content. */
    data object Subscribed : SubscriptionState

    /** No active entitlement — user must be shown the paywall. */
    data object NotSubscribed : SubscriptionState

    /** CustomerInfo could not be fetched (e.g. offline on first launch). */
    data class Error(val message: String) : SubscriptionState
}
