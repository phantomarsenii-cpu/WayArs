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
 *
 * The verdict itself is computed on NET €/km and NET €/minute, not the
 * gross numbers — a order can look great on paper and still lose money once
 * fuel is subtracted.
 *
 * If the user has set [customThresholds] (Settings screen), those take
 * priority over the selected preset and produce a 3-tier verdict
 * (GOOD / AVERAGE / BAD) based on net €/km. Otherwise falls back to the
 * preset-based logic, with one rule that can still mark an order GOOD even
 * if it misses the preset's own €/km bar: net €/km clearing
 * [ABSOLUTE_GOOD_RATE_PER_KM_PLN]. That override ALSO requires the order to
 * clear the preset's minimum €/minute — a €/km figure alone is misleading on
 * very short trips (a 400m order can show a huge €/km purely because the
 * distance is tiny, while still paying badly for the time it took), so a
 * short, poorly-paid-per-minute order is no longer waved through as GOOD
 * just because its distance was small.
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
        // >= 0, not > 0: apps like Stuart don't always expose a parseable
        // minutes figure, and the candidate then reaches here with
        // timeMinutes defaulted to 0 rather than being dropped entirely.
        require(timeMinutes >= 0) { "timeMinutes must be >= 0" }

        val fuelCost = vehicleProfile.fuelCostForDistance(distanceKm)
        val netProfit = earnings - fuelCost

        // No hidden padding here anymore — this used to add a flat, fake
        // "parking/red lights/waiting" constant on top of the order's own
        // reported minutes before computing €/minute. That number never came
        // from anything the app actually reported, so it silently skewed
        // every order's real time (worst on short/quick ones, where a fixed
        // 10 minutes is a huge fraction of the real duration) — removed so
        // €/minute reflects only the time the source app actually reported.
        val ratePerKm = netProfit / distanceKm
        val ratePerMinute = if (timeMinutes > 0) netProfit / timeMinutes else 0.0

        val verdict = if (customThresholds != null) {
            when {
                ratePerKm >= customThresholds.goodRatePerKm -> Verdict.GOOD
                ratePerKm >= customThresholds.averageRatePerKm -> Verdict.AVERAGE
                else -> Verdict.BAD
            }
        } else {
            val absoluteGoodRate = ABSOLUTE_GOOD_RATE_PER_KM_PLN / currency.rateToPln
            val clearsPresetBar = ratePerKm >= preset.minRatePerKm(currency) && ratePerMinute >= preset.minRatePerMinute(currency)
            // The absolute-rate override used to fire on ratePerKm ALONE.
            // On a short trip (e.g. 0.4 km) €/km can look huge purely because
            // the distance is tiny, even while €/minute is terrible — that
            // let a genuinely bad, barely-paid order get marked GOOD. Now the
            // override also requires clearing the preset's own €/minute bar,
            // same as the normal path below, so a big €/km number can no
            // longer paper over a bad €/minute one.
            if (ratePerKm >= absoluteGoodRate && ratePerMinute >= preset.minRatePerMinute(currency)) {
                Verdict.GOOD
            } else if (clearsPresetBar) {
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
        /**
         * Any order clearing this NET €/km bar (converted to the user's
         * currency) is GOOD provided it also clears the preset's own
         * €/minute bar — see the override comment above.
         */
        const val ABSOLUTE_GOOD_RATE_PER_KM_PLN = 4.0
    }
}
