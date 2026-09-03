package com.wayars.app.domain.usecase

import com.wayars.app.domain.model.Currency
import com.wayars.app.domain.model.OrderEvaluation
import com.wayars.app.domain.model.Preset
import com.wayars.app.domain.model.Verdict

/**
 * Core scoring engine described in the spec:
 *   ratePerKm = earnings / distanceKm
 *   ratePerMinute = earnings / timeMinutes
 *   verdict = GOOD if both rates clear the preset's (currency-adjusted) thresholds, else BAD.
 */
class EvaluateOrderUseCase {

    operator fun invoke(
        earnings: Double,
        distanceKm: Double,
        timeMinutes: Double,
        currency: Currency,
        preset: Preset
    ): OrderEvaluation {
        require(distanceKm > 0) { "distanceKm must be > 0" }
        require(timeMinutes > 0) { "timeMinutes must be > 0" }

        val ratePerKm = earnings / distanceKm
        val ratePerMinute = earnings / timeMinutes

        val verdict = if (
            ratePerKm >= preset.minRatePerKm(currency) &&
            ratePerMinute >= preset.minRatePerMinute(currency)
        ) Verdict.GOOD else Verdict.BAD

        return OrderEvaluation(
            earnings = earnings,
            distanceKm = distanceKm,
            timeMinutes = timeMinutes,
            currency = currency,
            ratePerKm = ratePerKm,
            ratePerMinute = ratePerMinute,
            verdict = verdict
        )
    }
}
