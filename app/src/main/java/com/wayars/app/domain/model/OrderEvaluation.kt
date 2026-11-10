package com.wayars.app.domain.model

/**
 * Result of running [com.wayars.app.domain.usecase.EvaluateOrderUseCase].
 *
 * [ratePerKm] and [ratePerMinute] are NET figures — after subtracting
 * [fuelCost] — because that is what the verdict is actually based on.
 * [earnings] stays the raw, gross amount the app reported, shown alongside
 * so the driver sees both numbers.
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
) {
    companion object {
        /**
         * Below this distance, a €/km figure is arithmetically correct but
         * misleading to display on its own: dividing by a small fraction of
         * a kilometer inflates the number well past the order's actual
         * payout (a real, if small, order can show a €/km bigger than its
         * own total). UI surfaces showing a single per-order rate badge
         * should show net profit instead once distanceKm is under this bar,
         * rather than the "stretched to a full km" rate.
         */
        const val SHORT_TRIP_THRESHOLD_KM = 1.0
    }
}
