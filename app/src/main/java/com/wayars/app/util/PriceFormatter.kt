package com.wayars.app.util

import java.text.NumberFormat
import java.util.Locale

/**
 * Formats subscription prices using each currency's own conventional
 * symbol/placement (e.g. PLN -> "zł" after the number), instead of relying
 * on Play Billing's [com.revenuecat.purchases.models.Price.formatted].
 *
 * Play Billing formats the price using the *device's UI locale*, not the
 * currency's home locale. Java's [java.util.Currency.getSymbol] falls back
 * to the raw ISO code (e.g. "PLN") whenever the running locale has no
 * localized symbol for that currency — which is exactly what happened for
 * a Polish (PLN) purchase on a device set to Russian: Play showed
 * "139,99 PLN" instead of "139,99 zł". Picking the currency's own home
 * locale for formatting fixes this regardless of the device's language,
 * while still formatting correctly for every other supported currency.
 */
object PriceFormatter {

    /** currencyCode -> a locale where that currency is the native one,
     *  used only to resolve its conventional symbol/grouping/placement. */
    private val homeLocales: Map<String, Locale> = mapOf(
        "PLN" to Locale("pl", "PL"),
        "EUR" to Locale("de", "DE"),
        "USD" to Locale.US,
        "GBP" to Locale.UK,
        "UAH" to Locale("uk", "UA"),
        "MDL" to Locale("ro", "MD"),
        "BRL" to Locale("pt", "BR"),
        "INR" to Locale("en", "IN"),
        "TRY" to Locale("tr", "TR"),
        "JPY" to Locale.JAPAN,
        "CZK" to Locale("cs", "CZ"),
        "HUF" to Locale("hu", "HU"),
        "RON" to Locale("ro", "RO"),
        "CHF" to Locale("de", "CH"),
        "CAD" to Locale.CANADA,
        "AUD" to Locale("en", "AU"),
        "SEK" to Locale("sv", "SE"),
        "NOK" to Locale("nb", "NO"),
        "DKK" to Locale("da", "DK"),
        "ILS" to Locale("iw", "IL"),
        "RUB" to Locale("ru", "RU"),
        "KZT" to Locale("kk", "KZ"),
        "RSD" to Locale("sr", "RS"),
        "BGN" to Locale("bg", "BG")
    )

    private fun currencyFormatterFor(currencyCode: String): NumberFormat? {
        val code = currencyCode.uppercase(Locale.ROOT)
        val locale = homeLocales[code] ?: return null
        return try {
            val currency = java.util.Currency.getInstance(code)
            NumberFormat.getCurrencyInstance(locale).apply { this.currency = currency }
        } catch (e: IllegalArgumentException) {
            // Unknown/invalid ISO 4217 code.
            null
        }
    }

    /**
     * Formats a real price. [amountMicros] and [currencyCode] come straight
     * from RevenueCat's [com.revenuecat.purchases.models.Price]. Falls back
     * to [fallback] (normally Play's own `.formatted` string) for any
     * currency we don't have a home locale for, so nothing breaks for
     * currencies outside the curated list above.
     */
    fun format(amountMicros: Long, currencyCode: String, fallback: String): String {
        val formatter = currencyFormatterFor(currencyCode) ?: return fallback
        return formatter.format(amountMicros / 1_000_000.0)
    }

    /**
     * Formats a zero amount in [currencyCode] with no decimals (e.g. "0 zł",
     * "$0"), for copy like "Try 7 days for 0 zł". Returns [fallback]
     * (normally "$0") when [currencyCode] is null/unknown — e.g. the
     * paywall is still showing mock pricing and hasn't loaded a real
     * currency yet.
     */
    fun zero(currencyCode: String?, fallback: String): String {
        val formatter = currencyCode?.let { currencyFormatterFor(it) } ?: return fallback
        formatter.maximumFractionDigits = 0
        formatter.minimumFractionDigits = 0
        return formatter.format(0)
    }
}
