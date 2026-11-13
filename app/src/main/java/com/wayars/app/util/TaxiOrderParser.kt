package com.wayars.app.util

import com.wayars.app.domain.model.Currency
import com.wayars.app.domain.model.PackageHint
import com.wayars.app.domain.model.RawOrderCandidate

/**
 * Parser for TAXI ride offers only (Bolt Driver, FreeNow Driver, and the ride
 * offer card of Uber Driver). Courier apps never reach this parser: they keep
 * going through [ScreenTextParser] exactly as before — see [OrderTextParser]
 * for the routing rules.
 *
 * Why taxi needs its own parser (layouts taken from real offer screenshots,
 * 2026-11):
 *
 *  - Uber ride offer:
 *      "UberX" "Tylko dla Ciebie" "141,85 zł" "Płatność gotówką" "★ 5,00"
 *      "4 min (1.8 km) od miejsca odbioru"   <- leg 1: driver -> pickup
 *      "51 min (37.8 km) przejazd"           <- leg 2: the trip itself
 *    Long trips use hours: "1 godz. 14 min (71.7 km) przejazd".
 *    Russian UI: "В 1 мин. (0.1 км) от вас", "Поездка: 12 мин. (3.5 км)".
 *
 *  - Bolt Driver:
 *      "Green" "109.20 ZŁ (netto)" "Iwona · 5.0 ★"
 *      "2 min · 1.6 km" <address>            <- leg 1: driver -> pickup
 *      "37 min · 33.4 km" <address>          <- leg 2: the trip itself
 *
 *  - FreeNow Driver:
 *      "Повышение тарифа 2.5x" "29,80 PLN"
 *      "Расстояние: 4,4 km (12 мин.)" <pickup address>   <- leg 1 (km first!)
 *      "1,9 km до" <destination>                         <- trip, distance only
 *
 * The generic parser takes the FIRST km/min figure after the price, which on
 * every one of these layouts is the short driver->pickup leg (1.8 km for a
 * 39.6 km job), and it cannot read "1 godz. 14 min" at all. Here every leg is
 * found and summed, so km/min cover the whole job (pickup + trip), the same
 * meaning "total" already has for courier orders.
 *
 * It is also more tolerant of how the text reaches the accessibility tree:
 * amount and currency split into two nodes ("109.20" + "ZŁ"), "2 min" / "·" /
 * "1.6 km" as separate nodes, and narrow/no-break spaces (U+202F, U+2009…)
 * between number and unit, which the generic patterns do not accept.
 */
object TaxiOrderParser {

    const val UBER_DRIVER_PACKAGE = "com.ubercab.driver"

    /** Packages that only ever show taxi rides — no courier traffic goes through them. */
    private val TAXI_ONLY_PACKAGES = setOf(
        "ee.mtakso.driver",     // Bolt Driver
        "taxi.android.driver"   // FreeNow Driver
    )

    fun isTaxiOnlyPackage(packageName: String): Boolean = packageName in TAXI_ONLY_PACKAGES

    /**
     * Returns a COMPLETE candidate when [texts] is a taxi offer this parser
     * understands, or null so the caller falls back to [ScreenTextParser].
     *
     * Uber Driver is shared by couriers (Uber Eats) and taxi drivers, so for
     * that package a result is returned ONLY when the screen clearly is a
     * ride offer (a pickup leg AND a trip leg with their own wording). Any
     * delivery offer fails that check and is handled by the generic parser
     * exactly as before.
     */
    fun parseFor(packageName: String, texts: List<String>, hint: PackageHint?): RawOrderCandidate? {
        val requireRideMarkers = when {
            packageName in TAXI_ONLY_PACKAGES -> false
            packageName == UBER_DRIVER_PACKAGE -> true
            else -> return null
        }
        return parse(texts, hint, requireRideMarkers)?.takeIf { it.isComplete }
    }

    // ---------------------------------------------------------------- text prep

    private val spaceLikeRegex = Regex("[\\u00A0\\u2002-\\u200A\\u202F\\u205F\\u3000]")
    private val invisibleRegex = Regex("[\\u200B-\\u200F\\u2060\\uFEFF\\u00AD]")

    private val bareAmountRegex = Regex("""^\d+(?:[.,]\d{1,2})?$""")
    private val currencyOnlyRegex = Regex(
        """^(?:zł|zl|pln|€|eur|\$|usd|£|gbp|₴|грн\.?|uah|lei|ron|mdl|kč|czk|ft|huf|₽|руб\.?|rub|₸|тг|kzt|kr\.?|sek|nok|dkk|₾|gel|֏|amd|₼|azn|so'?m|сум|uzs|лв\.?|bgn|chf|₺|tl|try)$""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Splits multi-line nodes into lines, normalizes exotic spaces, drops
     * exact duplicates (a node's text and its identical contentDescription)
     * and glues a bare amount to a currency that landed in its own node.
     */
    internal fun prepareLines(texts: List<String>): List<String> {
        val raw = texts.asSequence()
            .map { it.replace(invisibleRegex, "").replace(spaceLikeRegex, " ") }
            .flatMap { it.split('\n').asSequence() }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()

        val merged = ArrayList<String>(raw.size)
        var i = 0
        while (i < raw.size) {
            val line = raw[i]
            val next = raw.getOrNull(i + 1)
            if (next != null &&
                ((bareAmountRegex.matches(line) && currencyOnlyRegex.matches(next)) ||
                    (currencyOnlyRegex.matches(line) && bareAmountRegex.matches(next)))
            ) {
                merged.add("$line $next")
                i += 2
                continue
            }
            merged.add(line)
            i++
        }
        return merged.distinct()
    }

    // --------------------------------------------------------------------- legs

    // Unit words. (?!\p{L}) instead of \b so Cyrillic and "godz." behave the
    // same everywhere and "min" never matches inside "minimum".
    // Hour / minute words for every app language (PL, EN, RU, UK, DE, CS,
    // RO, FR, DA/NB/SV, HU "óra"/"perc", AZ "saat"/"dəq", UZ "soat"/"daq",
    // TR "saat"/"dk", HY "ժամ"/"րոպե", KA "სთ"/"წთ").
    private const val HOUR = """(?:godz(?:in[ayę]?)?|h|hr|hrs|hours?|ч|час(?:а|ов)?|год(?:ин[аи]?)?|std|óra|saat|soat|ժամ|სთ|tim(?:me|mar)?)(?!\p{L})\.?"""
    private const val MIN = """(?:min(?:ut[aeyę]?|utes?|s)?|мин(?:ут[аы]?)?|хв(?:илин[аи]?)?|mín|perc|dəq(?:iqə)?|daq(?:iqa)?|dk|dakika|րոպե|წთ|წუთი)(?!\p{L})\.?"""
    private const val DUR = """(?:\d+\s*$HOUR(?:\s*\d+\s*$MIN)?|\d+\s*$MIN)"""
    // Group 1 = number, group 2 = unit (miles are converted to km).
    private const val DIST = """(\d+(?:[.,]\d+)?)\s*(km|км|კმ|կմ|mi(?:les?)?|m|м)(?![\p{L}\d])"""
    private const val SEP = """(?:[•·∙・|,–—\-]\s*|\(\s*)"""

    /** "4 min (1.8 km)", "1 godz. 14 min (71.7 km)", "2 min · 1.6 km" */
    private val legTimeThenDistRegex = Regex("""($DUR)\s*$SEP?$DIST\s*\)?""", RegexOption.IGNORE_CASE)

    /** "4,4 km (12 мин.)", "1.6 km · 2 min" — a separator is mandatory here. */
    private val legDistThenTimeRegex = Regex("""$DIST\s*$SEP($DUR)\s*\)?""", RegexOption.IGNORE_CASE)

    private val distOnlyRegex = Regex(DIST, RegexOption.IGNORE_CASE)
    private val hoursRegex = Regex("""(\d+)\s*$HOUR""", RegexOption.IGNORE_CASE)
    private val minutesRegex = Regex("""(\d+)\s*$MIN""", RegexOption.IGNORE_CASE)

    internal data class Leg(val range: IntRange, val minutes: Double, val km: Double)

    private fun parseDurationMinutes(text: String): Double? {
        val hours = hoursRegex.find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val minutes = minutesRegex.find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val total = hours * 60 + minutes
        return if (total > 0 || minutesRegex.containsMatchIn(text)) total.toDouble() else null
    }

    private fun toNumber(raw: String): Double? = raw.replace(',', '.').toDoubleOrNull()

    private fun toKm(number: String, unit: String): Double? {
        val v = toNumber(number) ?: return null
        return when {
            unit.startsWith("mi", ignoreCase = true) -> v * 1.609344
            unit.equals("m", ignoreCase = true) || unit == "м" -> v / 1000.0   // "1 min (400 m)"
            else -> v
        }
    }

    internal fun findLegs(full: String): List<Leg> {
        val found = ArrayList<Leg>()
        legTimeThenDistRegex.findAll(full).forEach { m ->
            val minutes = parseDurationMinutes(m.groupValues[1]) ?: return@forEach
            val km = toKm(m.groupValues[2], m.groupValues[3]) ?: return@forEach
            found.add(Leg(m.range, minutes, km))
        }
        legDistThenTimeRegex.findAll(full).forEach { m ->
            val km = toKm(m.groupValues[1], m.groupValues[2]) ?: return@forEach
            val minutes = parseDurationMinutes(m.groupValues[3]) ?: return@forEach
            found.add(Leg(m.range, minutes, km))
        }
        // Earliest first, overlapping matches dropped (keep the earlier one).
        val ordered = ArrayList<Leg>()
        for (leg in found.sortedBy { it.range.first }) {
            val overlaps = ordered.any { it.range.first <= leg.range.last && leg.range.first <= it.range.last }
            if (!overlaps && leg.km > 0) ordered.add(leg)
        }
        // A parent node whose contentDescription repeats its children's text
        // would list every leg twice — keep each (minutes, km) pair once.
        return ordered.distinctBy { it.minutes to it.km }
    }

    // -------------------------------------------------------------------- money

    private data class MoneyHit(val position: Int, val amount: Double, val currency: Currency?, val priceLike: Boolean)

    private val parenthesizedRegex = Regex("""\([^)]*\)""")
    private val perKmAfterRegex = Regex("""^\s*/\s?km|^\s*per\s?km""", RegexOption.IGNORE_CASE)

    private fun findMoney(lines: List<String>, lineStarts: IntArray, hint: PackageHint?): List<MoneyHit> {
        val patterns = ScreenTextParser.moneyPatternsFor(hint)
        val hits = ArrayList<MoneyHit>()
        lines.forEachIndexed { index, line ->
            // "+5,00 zł" style bonus/boost chips are never the fare itself.
            if (line.startsWith("+")) return@forEachIndexed
            var best: Pair<MatchResult, Currency?>? = null
            for ((regex, currency) in patterns) {
                for (match in regex.findAll(line)) {
                    if (line.substring(0, match.range.first).trimEnd().endsWith("★")) continue
                    if (perKmAfterRegex.containsMatchIn(line.substring(match.range.last + 1))) continue
                    val amount = ScreenTextParser.parseAmount(match.groupValues[1]) ?: continue
                    if (amount <= 0) continue
                    if (best == null || match.range.first < best.first.range.first) best = match to currency
                    break
                }
            }
            val (match, currency) = best ?: return@forEachIndexed
            val amount = ScreenTextParser.parseAmount(match.groupValues[1]) ?: return@forEachIndexed
            // "Price-like": the line is basically just the amount, maybe with
            // a parenthesized note ("(netto)") or an icon glyph after it — as
            // opposed to a sentence that merely mentions an amount.
            val rest = line.removeRange(match.range).replace(parenthesizedRegex, "")
            val priceLike = rest.none { it.isLetterOrDigit() }
            hits.add(MoneyHit(lineStarts[index] + match.range.first, amount, currency, priceLike))
        }
        return hits
    }

    // ------------------------------------------------------------- ride markers

    private val pickupMarkerRegex = Regex(
        """odbior|odbiór|away|от вас|від вас|entfernt|de distancia|di distanza|de distância|pickup|pick-up""",
        RegexOption.IGNORE_CASE
    )
    private val tripMarkerRegex = Regex(
        """przejazd|trip|поездк|поїздк|fahrt|viaje|viagem|corsa|trajet|jízd|cursă|cursa""",
        RegexOption.IGNORE_CASE
    )

    private fun hasMarkerNear(full: String, range: IntRange, marker: Regex): Boolean {
        val from = (range.first - MARKER_WINDOW).coerceAtLeast(0)
        val to = (range.last + 1 + MARKER_WINDOW).coerceAtMost(full.length)
        return marker.containsMatchIn(full.substring(from, to))
    }

    private const val MARKER_WINDOW = 30

    // -------------------------------------------------------------------- parse

    internal fun parse(texts: List<String>, hint: PackageHint?, requireRideMarkers: Boolean): RawOrderCandidate? {
        val lines = prepareLines(texts)
        if (lines.isEmpty()) return null

        val lineStarts = IntArray(lines.size)
        val full = buildString {
            lines.forEachIndexed { index, line ->
                if (index > 0) append('\n')
                lineStarts[index] = length
                append(line)
            }
        }

        val legs = findLegs(full)
        if (legs.isEmpty()) return null

        if (requireRideMarkers) {
            val pickupOk = hasMarkerNear(full, legs.first().range, pickupMarkerRegex)
            val tripOk = legs.drop(1).any { hasMarkerNear(full, it.range, tripMarkerRegex) }
            if (legs.size < 2 || !pickupOk || !tripOk) return null
        }

        // Fare: the price sits above the legs on every taxi layout. Prefer the
        // LAST "price-like" amount before the first leg (tags/bonus chips and
        // any earnings counter sit above the fare, sentences after it are not
        // price-like); otherwise the first amount before the first leg;
        // otherwise the first amount anywhere.
        val firstLegStart = legs.first().range.first
        val money = findMoney(lines, lineStarts, hint)
        val beforeLegs = money.filter { it.position < firstLegStart }
        val fare = beforeLegs.lastOrNull { it.priceLike } ?: beforeLegs.firstOrNull() ?: money.firstOrNull()

        val distanceKm: Double
        val timeMinutes: Double?
        if (legs.size >= 2) {
            distanceKm = legs.sumOf { it.km }
            timeMinutes = legs.sumOf { it.minutes }
        } else {
            val leg = legs.first()
            // FreeNow: the trip after the pickup leg is shown as distance only
            // ("1,9 km до"). Its duration is unknown, so total time is left
            // unknown too (verdict then uses per-km only) rather than showing
            // the pickup minutes as if they were the whole job.
            val tripKm = distOnlyRegex.findAll(full)
                .filter { it.range.first > leg.range.last }
                .firstNotNullOfOrNull { toKm(it.groupValues[1], it.groupValues[2])?.takeIf { km -> km > 0 } }
            if (tripKm != null) {
                distanceKm = leg.km + tripKm
                timeMinutes = null
            } else {
                distanceKm = leg.km
                timeMinutes = leg.minutes
            }
        }

        return RawOrderCandidate(
            earnings = fare?.amount,
            distanceKm = roundTo2(distanceKm),
            timeMinutes = timeMinutes,
            currency = fare?.currency
        )
    }

    private fun roundTo2(value: Double): Double = Math.round(value * 100.0) / 100.0
}
