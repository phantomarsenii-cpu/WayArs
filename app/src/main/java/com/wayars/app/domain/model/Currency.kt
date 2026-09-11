package com.wayars.app.domain.model

/**
 * Supported working currencies. `rateToPln` is a rough approximate conversion
 * rate used only to auto-scale preset thresholds when the user switches currency.
 * BRL/INR/TRY/JPY rates are ballpark figures checked against market data as of
 * September 2026 — TRY especially swings a lot with inflation, so treat all of
 * these as "close enough to scale a threshold," not anything to build financial
 * logic on.
 * TODO: replace hardcoded rates with a live exchange-rate source if needed.
 */
enum class Currency(val code: String, val symbol: String, val rateToPln: Double) {
    PLN(code = "PLN", symbol = "zł", rateToPln = 1.0),
    EUR(code = "EUR", symbol = "€", rateToPln = 4.3),
    MDL(code = "MDL", symbol = "L", rateToPln = 0.22),
    UAH(code = "UAH", symbol = "₴", rateToPln = 0.097),
    USD(code = "USD", symbol = "$", rateToPln = 4.0),
    GBP(code = "GBP", symbol = "£", rateToPln = 5.1),
    BRL(code = "BRL", symbol = "R$", rateToPln = 0.73),
    INR(code = "INR", symbol = "₹", rateToPln = 0.039),
    TRY(code = "TRY", symbol = "₺", rateToPln = 0.08),
    JPY(code = "JPY", symbol = "¥", rateToPln = 0.024);

    companion object {
        val default = PLN
        fun fromCode(code: String?): Currency = entries.firstOrNull { it.code == code } ?: default
    }
}
