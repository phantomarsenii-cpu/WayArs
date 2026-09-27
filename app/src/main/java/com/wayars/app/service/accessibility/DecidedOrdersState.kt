package com.wayars.app.service.accessibility

import com.wayars.app.domain.model.Currency
import java.util.concurrent.ConcurrentHashMap

/**
 * Remembers which orders the driver has already Accepted or Rejected, so
 * reopening that SAME order's full-screen view later (e.g. via "View tasks"
 * after accepting, or just scrolling back to it) doesn't pop the overlay
 * back up asking for a decision that was already made.
 *
 * The scraped screen text has no real order ID to key off — [RawOrderCandidate]
 * only ever carries earnings/distance/time/currency (see its own doc) — so
 * "same order" here means "same package + same earnings + same distance +
 * same currency". timeMinutes is deliberately excluded: it's a live ETA that
 * ticks down while the order sits on screen, so including it would make the
 * SAME order fail to match itself a minute later.
 *
 * This is a proxy, not a guarantee: two genuinely different orders from the
 * same app that happen to pay the exact same amount for the exact same
 * distance would be treated as "the same order" if the second one shows up
 * within [TTL_MILLIS]. That's judged an acceptable trade-off against the
 * alternative (the overlay nagging about an order already decided on) —
 * revisit if it turns out to cause real misses.
 *
 * Entries expire after [TTL_MILLIS] rather than being kept forever, so a
 * genuinely new order later in the same shift that happens to match old
 * numbers isn't silently suppressed indefinitely.
 */
object DecidedOrdersState {

    // 40 minutes — long enough to cover a driver bouncing between the task
    // list, navigation, and the order's own detail screen for as long as
    // that specific order is still open in the courier app, short enough
    // that it doesn't suppress a coincidentally-identical order later in
    // the same long shift. A completed delivery normally wraps up well
    // within 30 minutes, so 40 leaves some margin past that without
    // stretching so far it risks masking a genuinely new matching order.
    private const val TTL_MILLIS = 40 * 60 * 1000L

    private val decidedAtMillisByKey = ConcurrentHashMap<String, Long>()

    fun markDecided(packageName: String, earnings: Double, distanceKm: Double, currency: Currency) {
        pruneExpired()
        decidedAtMillisByKey[key(packageName, earnings, distanceKm, currency)] = System.currentTimeMillis()
    }

    fun wasRecentlyDecided(packageName: String, earnings: Double, distanceKm: Double, currency: Currency): Boolean {
        val decidedAt = decidedAtMillisByKey[key(packageName, earnings, distanceKm, currency)] ?: return false
        return System.currentTimeMillis() - decidedAt < TTL_MILLIS
    }

    private fun key(packageName: String, earnings: Double, distanceKm: Double, currency: Currency): String =
        "$packageName|$earnings|$distanceKm|$currency"

    private fun pruneExpired() {
        val now = System.currentTimeMillis()
        decidedAtMillisByKey.entries.removeIf { now - it.value >= TTL_MILLIS }
    }
}
