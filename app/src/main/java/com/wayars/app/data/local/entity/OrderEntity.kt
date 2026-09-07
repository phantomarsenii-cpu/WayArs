package com.wayars.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "orders")
data class OrderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampMillis: Long,
    val earnings: Double,
    val currencyCode: String,
    val distanceKm: Double,
    val timeMinutes: Double,
    val ratePerKm: Double,
    val ratePerMinute: Double,
    val fuelCost: Double = 0.0,
    val netProfit: Double = 0.0,
    val verdict: String, // "GOOD" | "AVERAGE" | "BAD"
    val accepted: Boolean? = null // null = no manual decision recorded yet
)
