package com.wayars.app.domain.model

/**
 * User-defined €/km (in their selected currency) boundaries that override the
 * built-in presets when set. Anything at or above [goodRatePerKm] is GOOD,
 * at or above [averageRatePerKm] (but below good) is AVERAGE, otherwise BAD.
 * [badRatePerKm] is kept for the user's own reference / display and does not
 * itself change the classification (below-average already means BAD).
 */
data class CustomThresholds(
    val badRatePerKm: Double,
    val averageRatePerKm: Double,
    val goodRatePerKm: Double
)
