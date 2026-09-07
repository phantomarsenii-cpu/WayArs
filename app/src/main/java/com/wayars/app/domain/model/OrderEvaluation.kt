package com.wayars.app.domain.model

/**
 * Result of running [com.wayars.app.domain.usecase.EvaluateOrderUseCase].
 *
 * [ratePerKm] and [ratePerMinute] are NET figures — after subtracting
 * [fuelCost] and after padding the order's own reported time with the
 * hidden-time constant (parking, lights, waiting inside the restaurant) —
 * because that is what the verdict is actually based on. [earnings] stays
 * the raw, gross amount the app reported, shown alongside so the driver
 * sees both numbers.
 */
data class OrderEvaluation(
    val earnings: Double,
    val distanceKm: Double,
    val timeMinutes: Double,
    val currency: Currency,
    val fuelCost: Double,
    val netProfit: Double,
    val ratePerKm: Double,
    val ratePerMinute: Double,
    val verdict: Verdict
)
