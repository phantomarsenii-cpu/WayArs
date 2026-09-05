package com.wayars.app.util

import com.wayars.app.domain.model.Currency
import com.wayars.app.domain.model.RawOrderCandidate

/**
 * Turns the flat list of text strings scraped from an order app's screen
 * (via AccessibilityNodeInfo) into a [RawOrderCandidate]. Pure regex, no
 * network, no per-app hardcoding beyond common currency/unit notations.
 *
 * NOTE: an earlier version of this file tried a "prefer a single node that
 * contains distance+time+money all together" pass before falling back to
 * independent per-node scanning. Real-world testing showed that made
 * accuracy WORSE, not better — it went back to this simpler, independent
 * per-field scan, which matched the actual first-version behavior that
 * tested more reliably. Lesson: don't add speculative "smarter" matching
 * heuristics without field evidence they help.
 */
object ScreenTextParser {

    private val moneyPatterns: List<Pair<Regex, Currency>> = listOf(
        Regex("""€\s?(\d+[.,]\d{1,2})""") to Currency.EUR,
        Regex("""(\d+[.,]\d{1,2})\s?€""") to Currency.EUR,
        Regex("""\$\s?(\d+[.,]\d{1,2})""") to Currency.USD,
        Regex("""(\d+[.,]\d{1,2})\s?\$""") to Currency.USD,
        Regex("""£\s?(\d+[.,]\d{1,2})""") to Currency.GBP,
        Regex("""(\d+[.,]\d{1,2})\s?£""") to Currency.GBP,
        // PLN/UAH/MDL: accept the currency marker BEFORE or AFTER the number,
        // since Bolt's combined-line format puts it before ("PLN 16.37").
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

        return RawOrderCandidate(
            earnings = earnings,
            distanceKm = distanceKm,
            timeMinutes = timeMinutes,
            currency = currency
        )
    }
}
