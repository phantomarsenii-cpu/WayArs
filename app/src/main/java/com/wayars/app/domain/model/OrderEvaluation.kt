package com.wayars.app.domain.model

/** Result of running [com.wayars.app.domain.usecase.EvaluateOrderUseCase]. */
data class OrderEvaluation(
    val earnings: Double,
    val distanceKm: Double,
    val timeMinutes: Double,
    val currency: Currency,
    val ratePerKm: Double,
    val ratePerMinute: Double,
    val verdict: Verdict
)
