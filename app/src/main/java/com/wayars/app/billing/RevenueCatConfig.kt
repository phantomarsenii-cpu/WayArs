package com.wayars.app.billing

/**
 * Central place for every RevenueCat-related constant used by the app.
 *
 * NOTE: the Android SDK key (the "test_..." / "goog_..." value below) is a
 * *public* client key by design — RevenueCat's own docs say it's safe to
 * ship inside the APK, unlike a secret server key (which would start with
 * "sk_"). It only ever lets a device report/query its own purchases; it
 * cannot be used to read or mutate other users' data. Nothing here needs to
 * come from a CI secret.
 */
object RevenueCatConfig {

    /** RevenueCat project "Android API key". Safe to embed client-side. */
    const val API_KEY = "test_pbcBfGxsqSzHoeTgXEIKqIWDJxh"

    /** Entitlement identifier configured in the RevenueCat dashboard. */
    const val ENTITLEMENT_PRO = "wayars_pro"

    /**
     * Package identifiers inside the "default" offering. These match
     * RevenueCat's standard duration-based package identifiers
     * ($rc_weekly / $rc_monthly / $rc_annual), so we can pick the right
     * [com.revenuecat.purchases.Package] out of [com.revenuecat.purchases.Offering.availablePackages]
     * without hardcoding underlying Play product IDs anywhere in the UI layer.
     */
    object PackageIds {
        const val WEEKLY = "\$rc_weekly"
        const val MONTHLY = "\$rc_monthly"
        const val YEARLY = "\$rc_annual"
    }

    // Underlying Play Console product IDs, kept here only for reference /
    // logging — all purchase & entitlement logic should go through
    // Packages and the entitlement identifier above, never these directly.
    const val PRODUCT_WEEKLY = "wayars_pro_weekly"
    const val PRODUCT_MONTHLY = "wayars_pro_monthly"
    const val PRODUCT_YEARLY = "wayars_pro_yearly"
}
