package com.wayars.app.util

import com.wayars.app.domain.model.PackageHint
import com.wayars.app.domain.model.RawOrderCandidate

/**
 * Picks the parser for one app's screen text.
 *
 *  - Bolt Driver / FreeNow Driver (taxi-only packages): [TaxiOrderParser]
 *    first, generic [ScreenTextParser] as a fallback.
 *  - Uber Driver: [TaxiOrderParser] ONLY when the screen is clearly a ride
 *    offer (pickup leg + trip leg); every Uber Eats delivery offer goes to
 *    [ScreenTextParser] exactly as before.
 *  - Every courier app (Bolt Courier, Wolt, Stuart, user-added apps):
 *    [ScreenTextParser] only — unchanged behavior.
 */
object OrderTextParser {

    data class Result(val candidate: RawOrderCandidate, val parserName: String)

    fun parse(packageName: String, texts: List<String>, hint: PackageHint?): Result {
        TaxiOrderParser.parseFor(packageName, texts, hint)?.let { return Result(it, "taxi") }
        return Result(ScreenTextParser.parse(texts, hint), "generic")
    }
}
