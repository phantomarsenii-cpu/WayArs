package com.wayars.app.service.accessibility

import com.wayars.app.domain.model.Currency
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.abs

/**
 * Remembers which orders the driver has already Accepted or Rejected, so
 * reopening that SAME order's screen later (e.g. via "View tasks" after
 * accepting, or dragging the order sheet back up) doesn't pop the overlay
 * back up asking for a decision that was already made.
 *
 * The scraped screen text has no real order ID to key off — only
 * earnings/distance/time/currency — so "same order" means: same package,
 * same currency, same earnings (to the cent) and a distance within
 * [DISTANCE_TOLERANCE_KM] of the decided one. timeMinutes is deliberately
 * ignored: it's a live ETA that ticks down while the order sits on screen.
 *
 * Distance is compared with a tolerance rather than rounded into a bucket:
 * some apps (Stuart confirmed on-device, 2026-09-27) show a live pickup
 * distance that drifts by hundredths of a km between scans of the same
 * order, and bucket-rounding still splits values that straddle a rounding
 * boundary (1.34 -> 1.3 but 1.35 -> 1.4) into two different "orders".
 *
 * This is a proxy, not a guarantee: two genuinely different orders with the
 * same fare and nearly the same distance from the same app within
 * [TTL_MILLIS] would be treated as one. Judged acceptable against the
 * alternative (the overlay nagging about an order already decided on).
 * Entries expire after [TTL_MILLIS] so a coincidentally identical order
 * later in the shift isn't suppressed indefinitely.
 */
object DecidedOrdersState {

    // 40 minutes: a delivery normally wraps up well within 30, this leaves
    // margin without risking masking a genuinely new matching order.
    private const val TTL_MILLIS = 40 * 60 * 1000L

    private const val DISTANCE_TOLERANCE_KM = 0.3
    private const val EARNINGS_TOLERANCE = 0.005

    private data class Entry(
        val packageName: String,
        val earnings: Double,
        val distanceKm: Double,
        val currency: Currency,
        val decidedAtMillis: Long
    )

    private val entries = CopyOnWriteArrayList<Entry>()

    fun markDecided(packageName: String, earnings: Double, distanceKm: Double, currency: Currency) {
        pruneExpired()
        entries.add(Entry(packageName, earnings, distanceKm, currency, System.currentTimeMillis()))
        ScanLogFile.append(
            "DECIDED mark pkg=$packageName earnings=$earnings km=$distanceKm cur=$currency (now ${entries.size} remembered)"
        )
    }

    fun wasRecentlyDecided(packageName: String, earnings: Double, distanceKm: Double, currency: Currency): Boolean {
        val now = System.currentTimeMillis()
        val hit = entries.any {
            it.packageName == packageName &&
                it.currency == currency &&
                now - it.decidedAtMillis < TTL_MILLIS &&
                abs(it.earnings - earnings) <= EARNINGS_TOLERANCE &&
                abs(it.distanceKm - distanceKm) <= DISTANCE_TOLERANCE_KM
        }
        // Logged on every check (hit AND miss) so a repeat popup can be
        // diagnosed from the scan log: a miss right after a "DECIDED mark"
        // line for the same order shows exactly which field differed.
        ScanLogFile.append(
            "DECIDED check pkg=$packageName earnings=$earnings km=$distanceKm cur=$currency -> " +
                (if (hit) "HIT (suppressed)" else "miss") + " [remembered=${entries.size}]"
        )
        return hit
    }

    private fun pruneExpired() {
        val now = System.currentTimeMillis()
        entries.removeAll { now - it.decidedAtMillis >= TTL_MILLIS }
    }
}
