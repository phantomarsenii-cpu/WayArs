package com.wayars.app.domain.model

/**
 * Supported working currencies. `rateToPln` is a rough approximate conversion
 * rate used only to auto-scale preset thresholds when the user switches currency.
 * BRL/INR/TRY/JPY/RUB/KZT/BYN/UZS/GEL/AMD/AZN/KGS/RON/CZK/HUF/BGN/CHF/SEK/NOK/DKK
 * rates are ballpark figures checked against market data as of September 2026 —
 * several of these (TRY, RUB, UZS especially) swing a lot with inflation, so
 * treat all of these as "close enough to scale a threshold," not anything to
 * build financial logic on.
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
    JPY(code = "JPY", symbol = "¥", rateToPln = 0.024),

    // --- CIS ---
    RUB(code = "RUB", symbol = "₽", rateToPln = 0.044),
    KZT(code = "KZT", symbol = "₸", rateToPln = 0.0083),
    BYN(code = "BYN", symbol = "Br", rateToPln = 1.2),
    UZS(code = "UZS", symbol = "so'm", rateToPln = 0.00031),
    GEL(code = "GEL", symbol = "₾", rateToPln = 1.48),
    AMD(code = "AMD", symbol = "֏", rateToPln = 0.0101),
    AZN(code = "AZN", symbol = "₼", rateToPln = 2.35),
    KGS(code = "KGS", symbol = "KGS", rateToPln = 0.046),

    // --- Europe (non-eurozone / additional) ---
    RON(code = "RON", symbol = "lei", rateToPln = 0.86),
    CZK(code = "CZK", symbol = "Kč", rateToPln = 0.172),
    HUF(code = "HUF", symbol = "Ft", rateToPln = 0.0109),
    BGN(code = "BGN", symbol = "лв", rateToPln = 2.2),
    CHF(code = "CHF", symbol = "CHF", rateToPln = 4.6),
    SEK(code = "SEK", symbol = "kr", rateToPln = 0.384),
    NOK(code = "NOK", symbol = "kr", rateToPln = 0.368),
    DKK(code = "DKK", symbol = "kr", rateToPln = 0.577);

    companion object {
        val default = PLN
        fun fromCode(code: String?): Currency = entries.firstOrNull { it.code == code } ?: default
    }
}
