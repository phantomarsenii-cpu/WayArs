package com.wayars.app.data.repository

import com.wayars.app.data.local.dao.OrderDao
import com.wayars.app.data.local.entity.OrderEntity
import com.wayars.app.domain.model.Currency
import com.wayars.app.domain.model.OrderEvaluation
import com.wayars.app.domain.model.Verdict
import com.wayars.app.domain.repository.OrderRecord
import com.wayars.app.domain.repository.OrderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Calendar

class OrderRepositoryImpl(private val dao: OrderDao) : OrderRepository {

    override fun observeToday(): Flow<List<OrderRecord>> {
        val startOfDay = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return dao.observeSince(startOfDay).map { list -> list.map { it.toRecord() } }
    }

    override fun observeAll(): Flow<List<OrderRecord>> =
        dao.observeAll().map { list -> list.map { it.toRecord() } }

    override suspend fun record(evaluation: OrderEvaluation): Long = dao.insert(
        OrderEntity(
            timestampMillis = System.currentTimeMillis(),
            earnings = evaluation.earnings,
            currencyCode = evaluation.currency.code,
            distanceKm = evaluation.distanceKm,
            timeMinutes = evaluation.timeMinutes,
            ratePerKm = evaluation.ratePerKm,
            ratePerMinute = evaluation.ratePerMinute,
            verdict = evaluation.verdict.name
        )
    )

    override suspend fun markDecision(id: Long, accepted: Boolean) = dao.setAccepted(id, accepted)

    override suspend fun clearHistory() = dao.clearAll()

    private fun OrderEntity.toRecord() = OrderRecord(
        id = id,
        timestampMillis = timestampMillis,
        evaluation = OrderEvaluation(
            earnings = earnings,
            distanceKm = distanceKm,
            timeMinutes = timeMinutes,
            currency = Currency.fromCode(currencyCode),
            ratePerKm = ratePerKm,
            ratePerMinute = ratePerMinute,
            verdict = Verdict.valueOf(verdict)
        ),
        accepted = accepted
    )
}
