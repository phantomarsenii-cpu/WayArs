package com.wayars.app.util

import com.wayars.app.domain.model.Currency
import java.util.Locale

object CurrencyFormatter {

    fun format(amount: Double, currency: Currency, decimals: Int = 2): String {
        val value = String.format(Locale.US, "%.${decimals}f", amount)
        return when (currency) {
            Currency.EUR, Currency.USD, Currency.GBP -> "${currency.symbol}$value"
            else -> "$value ${currency.symbol}"
        }
    }

    fun formatRatePerKm(amount: Double, currency: Currency): String = "${format(amount, currency)}/km"
}
