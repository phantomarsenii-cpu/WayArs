package com.wayars.app.domain.model

/**
 * A driving preset. Thresholds are defined in PLN and automatically scaled
 * to the user's selected currency via [Currency.rateToPln].
 */
enum class PresetType { ECONOMY, BALANCE, PROFITABLE_ONLY }

data class Preset(
    val type: PresetType,
    val minRatePerKmPln: Double,
    val minRatePerMinutePln: Double
) {
    fun minRatePerKm(currency: Currency): Double = minRatePerKmPln / currency.rateToPln
    fun minRatePerMinute(currency: Currency): Double = minRatePerMinutePln / currency.rateToPln

    companion object {
        val ECONOMY = Preset(PresetType.ECONOMY, minRatePerKmPln = 1.5, minRatePerMinutePln = 0.5)
        val BALANCE = Preset(PresetType.BALANCE, minRatePerKmPln = 2.2, minRatePerMinutePln = 0.8)
        val PROFITABLE_ONLY = Preset(PresetType.PROFITABLE_ONLY, minRatePerKmPln = 3.0, minRatePerMinutePln = 1.1)

        val all = listOf(ECONOMY, BALANCE, PROFITABLE_ONLY)

        fun fromType(type: PresetType): Preset = all.first { it.type == type }
    }
}
