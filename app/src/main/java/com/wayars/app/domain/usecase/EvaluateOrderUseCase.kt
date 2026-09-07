package com.wayars.app.domain.usecase

import com.wayars.app.domain.model.CustomThresholds
import com.wayars.app.domain.model.Currency
import com.wayars.app.domain.model.OrderEvaluation
import com.wayars.app.domain.model.Preset
import com.wayars.app.domain.model.Verdict
import com.wayars.app.domain.model.VehicleProfile

/**
 * Core scoring engine.
 *
 * Real profitability accounting, not just the raw payout:
 *  1. Fuel/energy cost = (distanceKm / 100) * consumption * pricePerUnit
 *     (always 0 for scooters/bicycles — see VehicleProfile).
 *  2. Net profit = earnings - fuel cost.
 *  3. Real time = the order's own reported time + [HIDDEN_TIME_MINUTES] —
 *     apps report only active drive/prep time, never the parking search, the
 *     red lights, or the wait at the restaurant counter. Padding it by a
 *     fixed amount gives an honest €/hour instead of an inflated one.
 *
 * The verdict itself is computed on NET €/km and NET €/(real)minute, not the
 * gross numbers — a order can look great on paper and still lose money once
 * fuel is subtracted.
 *
 * If the user has set [customThresholds] (Settings screen), those take
 * priority over the selected preset and produce a 3-tier verdict
 * (GOOD / AVERAGE / BAD) based on net €/km. Otherwise falls back to the
 * preset-based logic, with one hard rule that always wins regardless of
 * preset: net €/km clearing [ABSOLUTE_GOOD_RATE_PER_KM_PLN] is ALWAYS GOOD.
 */
class EvaluateOrderUseCase {

    operator fun invoke(
        earnings: Double,
        distanceKm: Double,
        timeMinutes: Double,
        currency: Currency,
        preset: Preset,
        vehicleProfile: VehicleProfile = VehicleProfile.DEFAULT,
        customThresholds: CustomThresholds? = null
    ): OrderEvaluation {
        require(distanceKm > 0) { "distanceKm must be > 0" }
        require(timeMinutes > 0) { "timeMinutes must be > 0" }

        val fuelCost = vehicleProfile.fuelCostForDistance(distanceKm)
        val netProfit = earnings - fuelCost
        val realTimeMinutes = timeMinutes + HIDDEN_TIME_MINUTES

        val ratePerKm = netProfit / distanceKm
        val ratePerMinute = netProfit / realTimeMinutes

        val verdict = if (customThresholds != null) {
            when {
                ratePerKm >= customThresholds.goodRatePerKm -> Verdict.GOOD
                ratePerKm >= customThresholds.averageRatePerKm -> Verdict.AVERAGE
                else -> Verdict.BAD
            }
        } else {
            val absoluteGoodRate = ABSOLUTE_GOOD_RATE_PER_KM_PLN / currency.rateToPln
            if (ratePerKm >= absoluteGoodRate) {
                Verdict.GOOD
            } else if (ratePerKm >= preset.minRatePerKm(currency) && ratePerMinute >= preset.minRatePerMinute(currency)) {
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
            fuelCost = fuelCost,
            netProfit = netProfit,
            ratePerKm = ratePerKm,
            ratePerMinute = ratePerMinute,
            verdict = verdict
        )
    }

    companion object {
        /** Any order clearing this NET €/km bar (converted to the user's currency) is always GOOD. */
        const val ABSOLUTE_GOOD_RATE_PER_KM_PLN = 4.0

        /**
         * Default hidden time added to every order's own reported duration —
         * parking, traffic lights, waiting at the pickup counter. This is a
         * flat constant, not per-app tuned; adjust here if you want a
         * different baseline.
         */
        const val HIDDEN_TIME_MINUTES = 10.0
    }
}
