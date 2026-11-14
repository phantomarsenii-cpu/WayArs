package com.wayars.app.domain.repository

import com.wayars.app.domain.model.OrderEvaluation
import kotlinx.coroutines.flow.Flow

data class OrderRecord(
    val id: Long,
    val timestampMillis: Long,
    val evaluation: OrderEvaluation,
    val accepted: Boolean?,
    val sourcePackage: String? = null
)

data class TodayStats(
    val ordersCount: Int,
    val totalEarnings: Double,
    val totalDistanceKm: Double,
    val totalTimeMinutes: Double,
    val avgRatePerKm: Double
)

interface OrderRepository {
    fun observeToday(): Flow<List<OrderRecord>>
    fun observeAll(): Flow<List<OrderRecord>>
    suspend fun record(evaluation: OrderEvaluation, sourcePackage: String? = null): Long
    /** Accepted orders since [sinceMillis], oldest first (for summaries / widget). */
    suspend fun acceptedSince(sinceMillis: Long): List<OrderRecord>
    suspend fun markDecision(id: Long, accepted: Boolean)
    suspend fun clearHistory()
}
