package com.wayars.app.domain.model

enum class VehicleCategory { CAR, SCOOTER, BICYCLE }

/**
 * Meaningful for [VehicleCategory.CAR] (all four values) and
 * [VehicleCategory.SCOOTER] (PETROL or ELECTRIC only). Never set for
 * [VehicleCategory.BICYCLE], which has no fuel/energy cost.
 */
enum class FuelType { PETROL, DIESEL, LPG, ELECTRIC }

/**
 * Driver's own vehicle running costs. CAR and SCOOTER both carry a real
 * fuel/energy cost (petrol scooters burn fuel just like cars; electric
 * scooters draw charging cost same as an electric car). Only BICYCLE
 * (human-powered / e-scooter with negligible charge cost) always has
 * consumption and price at 0 — there is nothing to enter for it.
 */
data class VehicleProfile(
    val category: VehicleCategory,
    val fuelType: FuelType?,
    val consumptionPer100Km: Double,
    val fuelPricePerUnit: Double
) {
    /** Fuel/energy cost for covering [distanceKm], in the driver's currency. */
    fun fuelCostForDistance(distanceKm: Double): Double {
        if (category == VehicleCategory.BICYCLE) return 0.0
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
