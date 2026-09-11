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
 * accuracy WORSE, not better — it went back to a simpler, independent
 * per-field scan. Lesson: don't add speculative "smarter" matching
 * heuristics without field evidence they help.
 *
 * That per-field independence is still how earnings is found (first valid
 * money match, full stop). Distance/time are different: they're searched
 * in a pass that STARTS from wherever the money match was found, only
 * falling back to an earlier match if nothing shows up from there on. This
 * isn't the same "smarter" heuristic reverted above (that one required all
 * three fields in ONE node before it would even try); this one still
 * accepts them from separate nodes same as ever, it just picks which
 * candidate to trust when a screen shows MORE THAN ONE distance/time pair
 * (Bolt confirmed on-device: a pickup-leg figure before the money, then the
 * matching trip-total figure at/after it — see the comment in parse()).
 */
object ScreenTextParser {

    private val moneyPatterns: List<Pair<Regex, Currency>> = listOf(
        Regex("""€\s?(\d+[.,]\d{1,2})""") to Currency.EUR,
        Regex("""(\d+[.,]\d{1,2})\s?€""") to Currency.EUR,
        // (?<!R) guards against matching the "$" inside Brazil's "R$" as a
        // bare USD sign — without it, "R$ 25,00" would be misread as USD
        // 25.00 by THIS pattern before ever reaching the BRL pattern below.
        Regex("""(?<!R)\$\s?(\d+[.,]\d{1,2})""") to Currency.USD,
        Regex("""(\d+[.,]\d{1,2})\s?\$""") to Currency.USD,
        Regex("""£\s?(\d+[.,]\d{1,2})""") to Currency.GBP,
        Regex("""(\d+[.,]\d{1,2})\s?£""") to Currency.GBP,
        // PLN/UAH/MDL: accept the currency marker BEFORE or AFTER the number,
        // since Bolt's combined-line format puts it before ("PLN 16.37").
        //
        // The "currency BEFORE number" variants are deliberately restricted
        // to a single same-line space/tab (`[ \t]?`, not `\s?`) and forbid a
        // digit immediately before the marker (`(?<!\d)`). Stuart was
        // observed rendering the real total and an unrelated bare per-km
        // rate as ONE multi-line node: "35.46zł\n2.18\n14.2 km total". With
        // a bare `\s?` (which matches "\n" too) and no lookbehind, this
        // pattern happily read the "zł" that's actually the SUFFIX of
        // "35.46zł" as a PREFIX for the next line's "2.18", extracting the
        // wrong figure (earnings=2.18 instead of 35.46). A currency marker
        // can only be a genuine prefix if it isn't glued to a preceding
        // number and doesn't need to reach across a line break to find its
        // number.
        Regex("""(?<!\d)(?:zł|PLN|zl)[ \t]?(\d+[.,]\d{1,2})""", RegexOption.IGNORE_CASE) to Currency.PLN,
        Regex("""(\d+[.,]\d{1,2})\s?(?:zł|PLN|zl)""", RegexOption.IGNORE_CASE) to Currency.PLN,
        Regex("""(?<!\d)(?:₴|UAH|грн)[ \t]?(\d+[.,]\d{1,2})""", RegexOption.IGNORE_CASE) to Currency.UAH,
        Regex("""(\d+[.,]\d{1,2})\s?(?:₴|UAH|грн)""", RegexOption.IGNORE_CASE) to Currency.UAH,
        Regex("""(?<!\d)(?:MDL|lei)[ \t]?(\d+[.,]\d{1,2})""", RegexOption.IGNORE_CASE) to Currency.MDL,
        Regex("""(\d+[.,]\d{1,2})\s?(?:MDL|lei|L\b)""", RegexOption.IGNORE_CASE) to Currency.MDL,
        // "R$" is always a prefix in practice (Brazilian apps never write
        // "25,00 R$") so only one direction is needed here.
        Regex("""(?<!\d)R\$[ \t]?(\d+[.,]\d{1,2})""", RegexOption.IGNORE_CASE) to Currency.BRL,
        Regex("""(?<!\d)₹[ \t]?(\d+[.,]\d{1,2})""") to Currency.INR,
        Regex("""(\d+[.,]\d{1,2})\s?₹""") to Currency.INR,
        Regex("""(?<!\d)(?:₺|TRY|TL\b)[ \t]?(\d+[.,]\d{1,2})""", RegexOption.IGNORE_CASE) to Currency.TRY,
        Regex("""(\d+[.,]\d{1,2})\s?(?:₺|TRY|TL\b)""", RegexOption.IGNORE_CASE) to Currency.TRY,
        // Yen has no minor unit in normal display ("¥850", not "¥850.00"),
        // so unlike every other currency here the amount group has NO
        // required decimal part.
        Regex("""(?<!\d)¥[ \t]?(\d+(?:[.,]\d{1,2})?)""") to Currency.JPY,
        Regex("""(\d+(?:[.,]\d{1,2})?)\s?¥""") to Currency.JPY,
        Regex("""(?<!\d)(?:JPY)[ \t]?(\d+(?:[.,]\d{1,2})?)""", RegexOption.IGNORE_CASE) to Currency.JPY,
        Regex("""(\d+(?:[.,]\d{1,2})?)\s?(?:JPY)""", RegexOption.IGNORE_CASE) to Currency.JPY
    )

    /**
     * A counter/rating badge glued to the Accept button ("4, Accept", "63,
     * Accept") — never the order total or distance. Seen on Stuart sharing a
     * multi-line node with the real amount; skipped as a whole line before
     * any money/distance/time regex gets a chance at it, rather than trying
     * to carve the badge out of a shared string.
     */
    private val acceptCounterLineRegex = Regex("""^\d+\s*,\s*Accept$""", RegexOption.IGNORE_CASE)

    // "км" (Cyrillic) added alongside Latin "km" — Bolt's Russian-locale UI
    // renders distance as "5.4 км", not "5.4 km". Before this, distanceKm
    // stayed null forever on that locale even though earnings/minutes
    // parsed fine, which silently failed candidate.isComplete and meant the
    // overlay never showed for a real, fully-visible Bolt order (confirmed
    // on-device, 2026-09-10 logs: earnings=17.28 min=17.0 km=null on every
    // single scan of an order that was on-screen for 45+ seconds).
    private val distanceRegex = Regex("""(\d+[.,]\d+|\d+)\s?(?:km|км)\b""", RegexOption.IGNORE_CASE)

    // Widened beyond the original "min|mín|хв|мин" to also accept the
    // Polish full-word forms ("minut", "minuty", "minuta") and "min." with a
    // trailing dot — apps that spell out the word instead of abbreviating it
    // (Stuart included, per on-device raw-text diagnostics) were silently
    // falling through to timeMinutes=null with the old word-boundary-only
    // "min" pattern.
    private val timeRegex = Regex(
        """(\d+)\s?(?:min\.?|mins?|mín|хв|мин|minut[ay]?)\b""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Some apps (Stuart among them) show a per-km/per-order rate ("1,70
     * zł/km") alongside the actual total earnings. That rate matches the
     * money regex just as well as a real total and, depending on node
     * traversal order, can get picked first — leaving the real total
     * ignored. Treat a match immediately followed by "/km" (or a localized
     * "per km") as a rate, not the total, and keep scanning instead.
     */
    private val perUnitSuffixRegex = Regex("""^\s*/\s?km|^\s*per\s?km""", RegexOption.IGNORE_CASE)

    fun parse(texts: List<String>): RawOrderCandidate {
        var earnings: Double? = null
        var currency: Currency? = null
        var moneyIndex = -1

        for (index in texts.indices) {
            val text = texts[index].trim()
            if (text.isEmpty() || acceptCounterLineRegex.matches(text)) continue

            for ((regex, cur) in moneyPatterns) {
                val match = regex.find(text) ?: continue

                // Guard: a rating badge (e.g. "★ 1.67") is never the
                // order total, even on the rare layout where the badge
                // text itself happens to sit next to a currency marker.
                // Checked in the SAME string only — a star glyph in an
                // unrelated earlier sibling node can't reach this match.
                val precedingText = text.substring(0, match.range.first)
                if (precedingText.trimEnd().endsWith("★")) continue

                // Guard: a per-km/per-order rate suffix ("/km", "per km").
                // Checked in two places: right after the amount in THIS
                // string (original case), and also at the START of the
                // NEXT collected text (Stuart observed splitting a rate
                // like "1.67 zł" and its "/km" suffix across two sibling
                // nodes — collectText's sibling order guarantees the
                // suffix, if present, is the very next entry).
                val sameNodeSuffix = text.substring(match.range.last + 1)
                val nextNodeText = texts.getOrNull(index + 1)?.trim().orEmpty()
                if (perUnitSuffixRegex.containsMatchIn(sameNodeSuffix) ||
                    perUnitSuffixRegex.containsMatchIn(nextNodeText)
                ) continue

                val amount = match.groupValues[1].replace(',', '.').toDoubleOrNull()
                if (amount != null && amount > 0) {
                    earnings = amount
                    currency = cur
                    moneyIndex = index
                    break
                }
            }
            if (earnings != null) break
        }

        // Distance/time are searched in a SEPARATE pass, preferring a match
        // at or after moneyIndex, only falling back to an earlier one if
        // nothing turns up from there on. Bolt (confirmed on-device,
        // 2026-09-10 logs) shows THREE distance+time figures on one order
        // screen: "~1.6 км, ~17 мин" (distance/ETA to the pickup point),
        // "~3.8 км, ~14 мин" (pickup to drop-off), and "5.4 км, 31 мин, PLN
        // 17.28" (the trip TOTAL, glued to the actual earnings — 1.6+3.8 =
        // 5.4, 17+14 = 31). The old single unified pass took the very FIRST
        // distance/time match regardless of what it belonged to, so it
        // silently paired the real 17.28 PLN total with the 1.6 km/17 min
        // pickup leg instead of the matching 5.4 km/31 min total — a
        // candidate that looked "complete" but had a wildly wrong (too
        // generous) zł/km figure. Searching from moneyIndex onward first
        // finds the figure most likely to actually belong to that total
        // (often the very same node, as here and in Wolt's "Dostawa | 26,61
        // zł | Łącznie 34 min (14.7 km)"); every currently-working layout
        // already has distance/time at or after the money node, so this is
        // a no-op for them.
        val distanceKm = findValue(texts, distanceRegex, moneyIndex)
        val timeMinutes = findValue(texts, timeRegex, moneyIndex)

        return RawOrderCandidate(
            earnings = earnings,
            distanceKm = distanceKm,
            timeMinutes = timeMinutes,
            currency = currency
        )
    }

    /**
     * Finds the first [regex] match (group 1, comma-as-decimal-point
     * tolerant) at or after [preferredFromIndex], falling back to the
     * first match anywhere earlier in [texts] if none is found from there
     * on. A negative [preferredFromIndex] (earnings never matched) simply
     * searches the whole list once, same as the old single-pass behavior.
     */
    private fun findValue(texts: List<String>, regex: Regex, preferredFromIndex: Int): Double? {
        fun search(range: IntRange): Double? {
            for (index in range) {
                val text = texts.getOrNull(index)?.trim().orEmpty()
                if (text.isEmpty() || acceptCounterLineRegex.matches(text)) continue
                val match = regex.find(text) ?: continue
                val value = match.groupValues[1].replace(',', '.').toDoubleOrNull()
                if (value != null && value > 0) return value
            }
            return null
        }

        val startIndex = if (preferredFromIndex >= 0) preferredFromIndex else 0
        search(startIndex until texts.size)?.let { return it }
        return if (startIndex > 0) search(0 until startIndex) else null
    }
}
