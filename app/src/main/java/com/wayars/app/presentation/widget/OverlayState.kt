package com.wayars.app.presentation.widget

import com.wayars.app.domain.model.OrderEvaluation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Process-wide bridge between OrderAccessibilityService (producer) and the
 * floating overlay / Dashboard screen (consumers). A simple in-memory
 * StateFlow is enough here since everything runs in one app process.
 */
object OverlayState {
    private val _latestEvaluation = MutableStateFlow<OrderEvaluation?>(null)
    val latestEvaluation: StateFlow<OrderEvaluation?> = _latestEvaluation

    private val _pendingRecordId = MutableStateFlow<Long?>(null)
    val pendingRecordId: StateFlow<Long?> = _pendingRecordId

    fun publish(evaluation: OrderEvaluation, recordId: Long?) {
        _latestEvaluation.value = evaluation
        _pendingRecordId.value = recordId
    }

    fun clear() {
        _latestEvaluation.value = null
        _pendingRecordId.value = null
    }
}
