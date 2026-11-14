package com.wayars.app.presentation.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.wayars.app.R
import com.wayars.app.domain.model.OrderEvaluation
import com.wayars.app.util.CurrencyFormatter

/**
 * What was subtracted from the order amount to get the take-home figure, e.g. "fuel -3.20 · tax -4.00".
 * Null when nothing was subtracted (no fuel data entered and no tax set).
 */
@Composable
fun deductionsLine(evaluation: OrderEvaluation): String? {
    val parts = ArrayList<String>(3)
    if (evaluation.commissionAmount > 0.0) {
        parts.add(stringResource(R.string.overlay_deduction_partner, CurrencyFormatter.format(evaluation.commissionAmount, evaluation.currency)))
    }
    if (evaluation.fuelDataEntered && evaluation.fuelCost > 0.0) {
        parts.add(stringResource(R.string.overlay_deduction_fuel, CurrencyFormatter.format(evaluation.fuelCost, evaluation.currency)))
    }
    if (evaluation.taxAmount > 0.0) {
        parts.add(stringResource(R.string.overlay_deduction_tax, CurrencyFormatter.format(evaluation.taxAmount, evaluation.currency)))
    }
    return if (parts.isEmpty()) null else parts.joinToString(" \u00B7 ")
}
