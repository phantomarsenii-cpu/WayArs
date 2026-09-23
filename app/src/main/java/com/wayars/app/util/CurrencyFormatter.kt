package com.wayars.app.util

import com.wayars.app.domain.model.Currency
import java.util.Locale

object CurrencyFormatter {

    fun format(amount: Double, currency: Currency, decimals: Int = 2): String {
        val value = String.format(Locale.US, "%.${decimals}f", amount)
        return when (currency) {
            // Symbol/code goes BEFORE the amount for these — matches how
            // each is conventionally written locally ("$120", "₽120",
            // "₾120"), same as EUR/USD/GBP above them.
            Currency.EUR, Currency.USD, Currency.GBP,
            Currency.RUB, Currency.GEL, Currency.AMD, Currency.AZN -> "${currency.symbol}$value"
            // CHF's "symbol" is the full 3-letter code (no single glyph
            // convention like $/€), so it needs a space to stay readable.
            Currency.CHF -> "${currency.symbol} $value"
            else -> "$value ${currency.symbol}"
        }
    }

    fun formatRatePerKm(amount: Double, currency: Currency): String = "${format(amount, currency)}/km"
}
