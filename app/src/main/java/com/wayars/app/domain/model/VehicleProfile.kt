package com.wayars.app.domain.model

enum class VehicleCategory { CAR, SCOOTER, BICYCLE }

/** Only meaningful when [VehicleCategory.CAR] is selected. */
enum class FuelType { PETROL, DIESEL, LPG, ELECTRIC }

/**
 * Driver's own vehicle running costs. For SCOOTER/BICYCLE, consumption and
 * price are always 0 — those simply have no fuel cost in this model
 * (electric scooter charging cost is negligible enough to ignore, matching
 * the spec's "for two-wheelers, costs are 0").
 */
data class VehicleProfile(
    val category: VehicleCategory,
    val fuelType: FuelType?,
    val consumptionPer100Km: Double,
    val fuelPricePerUnit: Double
) {
    /** Fuel/energy cost for covering [distanceKm], in the driver's currency. */
    fun fuelCostForDistance(distanceKm: Double): Double {
        if (category != VehicleCategory.CAR) return 0.0
        return (distanceKm / 100.0) * consumptionPer100Km * fuelPricePerUnit
    }

    companion object {
        val DEFAULT = VehicleProfile(
            category = VehicleCategory.CAR,
            fuelType = FuelType.PETROL,
            consumptionPer100Km = 7.0,
            fuelPricePerUnit = 6.5
        )
    }
}
