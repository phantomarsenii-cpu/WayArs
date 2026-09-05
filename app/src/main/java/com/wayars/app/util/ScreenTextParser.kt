package com.wayars.app.util

import com.wayars.app.domain.model.Currency
import com.wayars.app.domain.model.RawOrderCandidate

/**
 * Turns the flat list of text strings scraped from an order app's screen
 * (via AccessibilityNodeInfo) into a [RawOrderCandidate].
 *
 * TWO-PASS STRATEGY (this is the important part, and the reason wrong prices
 * were getting picked up on real orders that had a delivery fee, a menu
 * item price, and a tip suggestion all visible on the same screen at once):
 *
 *  Pass 1 — look for a SINGLE text node that contains distance AND time AND
 *  a money amount all together, e.g. Bolt's own accept-button text
 *  "5.2 km, 34 min, PLN 16.37". If such a node exists, trust it completely
 *  and ignore every other number on screen — a combined node like that is
 *  the app's own authoritative summary of THIS order, so it can't
 *  accidentally match some unrelated price elsewhere on the same screen.
 *
 *  Pass 2 — fallback for apps that show the three values in separate nodes:
 *  scan every node independently and take the first plausible match per
 *  field, same as before.
 */
object ScreenTextParser {

    private val moneyPatterns: List<Pair<Regex, Currency>> = listOf(
        Regex("""€\s?(\d+[.,]\d{1,2})""") to Currency.EUR,
        Regex("""(\d+[.,]\d{1,2})\s?€""") to Currency.EUR,
        Regex("""\$\s?(\d+[.,]\d{1,2})""") to Currency.USD,
        Regex("""(\d+[.,]\d{1,2})\s?\$""") to Currency.USD,
        Regex("""£\s?(\d+[.,]\d{1,2})""") to Currency.GBP,
        Regex("""(\d+[.,]\d{1,2})\s?£""") to Currency.GBP,
        Regex("""(?:zł|PLN|zl)\s?(\d+[.,]\d{1,2})""", RegexOption.IGNORE_CASE) to Currency.PLN,
        Regex("""(\d+[.,]\d{1,2})\s?(?:zł|PLN|zl)""", RegexOption.IGNORE_CASE) to Currency.PLN,
        Regex("""(?:₴|UAH|грн)\s?(\d+[.,]\d{1,2})""", RegexOption.IGNORE_CASE) to Currency.UAH,
        Regex("""(\d+[.,]\d{1,2})\s?(?:₴|UAH|грн)""", RegexOption.IGNORE_CASE) to Currency.UAH,
        Regex("""(?:MDL|lei)\s?(\d+[.,]\d{1,2})""", RegexOption.IGNORE_CASE) to Currency.MDL,
        Regex("""(\d+[.,]\d{1,2})\s?(?:MDL|lei|L\b)""", RegexOption.IGNORE_CASE) to Currency.MDL
    )

    private val distanceRegex = Regex("""(\d+[.,]\d+|\d+)\s?km\b""", RegexOption.IGNORE_CASE)
    private val timeRegex = Regex("""(\d+)\s?(?:min|mín|хв|мин)\b""", RegexOption.IGNORE_CASE)

    fun parse(texts: List<String>): RawOrderCandidate {
        findCombinedNodeMatch(texts)?.let { return it }
        return parseAcrossNodes(texts)
    }

    /** Pass 1: a single node with distance + time + money together. */
    private fun findCombinedNodeMatch(texts: List<String>): RawOrderCandidate? {
        for (raw in texts) {
            val text = raw.trim()
            if (text.isEmpty()) continue

            val distanceMatch = distanceRegex.find(text) ?: continue
            val timeMatch = timeRegex.find(text) ?: continue

            var earnings: Double? = null
            var currency: Currency? = null
            for ((regex, cur) in moneyPatterns) {
                val match = regex.find(text) ?: continue
                val amount = match.groupValues[1].replace(',', '.').toDoubleOrNull()
                if (amount != null && amount > 0) {
                    earnings = amount
                    currency = cur
                    break
                }
            }
            val distanceKm = distanceMatch.groupValues[1].replace(',', '.').toDoubleOrNull()
            val timeMinutes = timeMatch.groupValues[1].toDoubleOrNull()

            if (earnings != null && distanceKm != null && distanceKm > 0 && timeMinutes != null && timeMinutes > 0) {
                return RawOrderCandidate(earnings, distanceKm, timeMinutes, currency)
            }
        }
        return null
    }

    /** Pass 2: fallback — scan every node independently, first plausible match per field. */
    private fun parseAcrossNodes(texts: List<String>): RawOrderCandidate {
        var earnings: Double? = null
        var currency: Currency? = null
        var distanceKm: Double? = null
        var timeMinutes: Double? = null

        for (raw in texts) {
            val text = raw.trim()
            if (text.isEmpty()) continue

            if (earnings == null) {
                for ((regex, cur) in moneyPatterns) {
                    val match = regex.find(text) ?: continue
                    val amount = match.groupValues[1].replace(',', '.').toDoubleOrNull()
                    if (amount != null && amount > 0) {
                        earnings = amount
                        currency = cur
                        break
                    }
                }
            }

            if (distanceKm == null) {
                distanceRegex.find(text)?.let { match ->
                    val km = match.groupValues[1].replace(',', '.').toDoubleOrNull()
                    if (km != null && km > 0) distanceKm = km
                }
            }

            if (timeMinutes == null) {
                timeRegex.find(text)?.let { match ->
                    val mins = match.groupValues[1].toDoubleOrNull()
                    if (mins != null && mins > 0) timeMinutes = mins
                }
            }

            if (earnings != null && distanceKm != null && timeMinutes != null) break
        }

        return RawOrderCandidate(earnings, distanceKm, timeMinutes, currency)
    }
}
