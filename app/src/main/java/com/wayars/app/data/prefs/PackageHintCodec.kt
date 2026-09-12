package com.wayars.app.data.prefs

import com.wayars.app.domain.model.Currency
import com.wayars.app.domain.model.PackageHint

/**
 * [PackageHint] is small and flat enough that pulling in a JSON library
 * just to persist it isn't worth the new dependency — this project doesn't
 * use one anywhere else. A pipe-delimited line per hint, stored as a
 * `Set<String>` (same DataStore shape as customPackages), is enough.
 *
 * Format: packageName|moneyToken|moneyCurrencyCode|distanceUnitToken|timeUnitToken
 * Empty fields are literally empty (not "null") between the pipes. A
 * literal "|" inside a token (shouldn't normally happen — tokens are short
 * currency/unit words) is escaped as "\|" so it can't shift the field
 * count.
 */
object PackageHintCodec {
    private const val DELIMITER = "|"
    private const val ESCAPED_DELIMITER = "\u0001" // placeholder while splitting

    fun encode(hint: PackageHint): String {
        fun esc(s: String?) = s?.replace(DELIMITER, ESCAPED_DELIMITER) ?: ""
        return listOf(
            esc(hint.packageName),
            esc(hint.moneyToken),
            esc(hint.moneyCurrency?.name),
            esc(hint.distanceUnitToken),
            esc(hint.timeUnitToken)
        ).joinToString(DELIMITER)
    }

    fun decode(encoded: String): PackageHint? {
        val parts = encoded.split(DELIMITER)
        if (parts.size != 5) return null
        fun unesc(s: String): String? = s.replace(ESCAPED_DELIMITER, DELIMITER).ifEmpty { null }
        val packageName = unesc(parts[0]) ?: return null
        val currency = unesc(parts[2])?.let { name -> runCatching { Currency.valueOf(name) }.getOrNull() }
        return PackageHint(
            packageName = packageName,
            moneyToken = unesc(parts[1]),
            moneyCurrency = currency,
            distanceUnitToken = unesc(parts[3]),
            timeUnitToken = unesc(parts[4])
        )
    }
}
