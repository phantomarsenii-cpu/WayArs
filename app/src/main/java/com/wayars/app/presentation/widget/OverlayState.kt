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

    // Which app's screen the currently-shown evaluation came from — needed
    // so a decision (Accept/Reject) can suppress re-scanning of THAT
    // specific app only, not every supported app (see
    // ScanningState.suppressScanningBriefly).
    private val _sourcePackage = MutableStateFlow<String?>(null)
    val sourcePackage: StateFlow<String?> = _sourcePackage

    fun publish(evaluation: OrderEvaluation, recordId: Long?, sourcePackage: String) {
        _latestEvaluation.value = evaluation
        _pendingRecordId.value = recordId
        _sourcePackage.value = sourcePackage
    }

    fun clear() {
        _latestEvaluation.value = null
        _pendingRecordId.value = null
        _sourcePackage.value = null
    }
}
