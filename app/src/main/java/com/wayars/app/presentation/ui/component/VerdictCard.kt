package com.wayars.app.presentation.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.wayars.app.domain.model.OrderEvaluation
import com.wayars.app.domain.model.Verdict
import com.wayars.app.presentation.ui.theme.WaNeonGreen
import com.wayars.app.presentation.ui.theme.WaRed
import com.wayars.app.presentation.ui.theme.WaSurface
import com.wayars.app.presentation.ui.theme.WaTextSecondary
import com.wayars.app.util.CurrencyFormatter

@Composable
fun VerdictCard(evaluation: OrderEvaluation?, emptyLabel: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(WaSurface)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (evaluation == null) {
            Text(emptyLabel, color = WaTextSecondary, style = MaterialTheme.typography.bodyLarge)
            return@Column
        }
        val good = evaluation.verdict == Verdict.GOOD
        val color = if (good) WaNeonGreen else WaRed
        Text(
            if (good) "GOOD" else "BAD",
            color = color,
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            CurrencyFormatter.format(evaluation.earnings, evaluation.currency),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            "${fmt(evaluation.distanceKm)} km • ${fmt(evaluation.timeMinutes)} min",
            color = WaTextSecondary,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            CurrencyFormatter.formatRatePerKm(evaluation.ratePerKm, evaluation.currency),
            color = WaNeonGreen,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

private fun fmt(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else String.format("%.1f", value)
