package com.wayars.app.domain.model

/**
 * Supported working currencies. `rateToPln` is a rough approximate conversion
 * rate used only to auto-scale preset thresholds when the user switches currency.
 * TODO: replace hardcoded rates with a live exchange-rate source if needed.
 */
enum class Currency(val code: String, val symbol: String, val rateToPln: Double) {
    PLN(code = "PLN", symbol = "zł", rateToPln = 1.0),
    EUR(code = "EUR", symbol = "€", rateToPln = 4.3),
    MDL(code = "MDL", symbol = "L", rateToPln = 0.22),
    UAH(code = "UAH", symbol = "₴", rateToPln = 0.097),
    USD(code = "USD", symbol = "$", rateToPln = 4.0),
    GBP(code = "GBP", symbol = "£", rateToPln = 5.1);

    companion object {
        val default = PLN
        fun fromCode(code: String?): Currency = entries.firstOrNull { it.code == code } ?: default
    }
}
