package com.wayars.app.domain.usecase

import com.wayars.app.domain.model.CustomThresholds
import com.wayars.app.domain.model.Currency
import com.wayars.app.domain.model.OrderEvaluation
import com.wayars.app.domain.model.Preset
import com.wayars.app.domain.model.Verdict

/**
 * Core scoring engine.
 *
 * If the user has set [customThresholds] (Settings screen), those take
 * priority over the selected preset and produce a 3-tier verdict
 * (GOOD / AVERAGE / BAD) based purely on €/km. Otherwise falls back to the
 * original preset-based 2-tier logic (GOOD if both €/km and €/min clear the
 * preset's thresholds, else BAD).
 */
class EvaluateOrderUseCase {

    operator fun invoke(
        earnings: Double,
        distanceKm: Double,
        timeMinutes: Double,
        currency: Currency,
        preset: Preset,
        customThresholds: CustomThresholds? = null
    ): OrderEvaluation {
        require(distanceKm > 0) { "distanceKm must be > 0" }
        require(timeMinutes > 0) { "timeMinutes must be > 0" }

        val ratePerKm = earnings / distanceKm
        val ratePerMinute = earnings / timeMinutes

        val verdict = if (customThresholds != null) {
            when {
                ratePerKm >= customThresholds.goodRatePerKm -> Verdict.GOOD
                ratePerKm >= customThresholds.averageRatePerKm -> Verdict.AVERAGE
                else -> Verdict.BAD
            }
        } else {
            if (ratePerKm >= preset.minRatePerKm(currency) && ratePerMinute >= preset.minRatePerMinute(currency)) {
                Verdict.GOOD
            } else {
                Verdict.BAD
            }
        }

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
