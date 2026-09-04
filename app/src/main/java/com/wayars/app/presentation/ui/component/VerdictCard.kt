package com.wayars.app.presentation.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wayars.app.domain.model.OrderEvaluation
import com.wayars.app.domain.model.Verdict
import com.wayars.app.presentation.ui.theme.WaNeonGreen
import com.wayars.app.presentation.ui.theme.WaRed
import com.wayars.app.presentation.ui.theme.WaSurface
import com.wayars.app.presentation.ui.theme.WaSurfaceVariant
import com.wayars.app.presentation.ui.theme.WaTextSecondary
import com.wayars.app.util.CurrencyFormatter

// Gauge geometry matches the design template: an open arc with a gap at the
// bottom (like a speedometer), not a full ring.
private const val GAUGE_START_ANGLE = 150f
private const val GAUGE_SWEEP_ANGLE = 240f

@Composable
fun VerdictCard(evaluation: OrderEvaluation?, emptyLabel: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(WaSurface)
            .padding(vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (evaluation == null) {
            EmptyGauge()
            Text(emptyLabel, color = WaTextSecondary, style = MaterialTheme.typography.bodyLarge)
            return@Column
        }

        val good = evaluation.verdict == Verdict.GOOD
        val gaugeColor = if (good) WaNeonGreen else WaRed

        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(170.dp)) {
            Canvas(modifier = Modifier.size(170.dp)) {
                val stroke = 12.dp.toPx()
                drawArc(
                    color = gaugeColor,
                    startAngle = GAUGE_START_ANGLE,
                    sweepAngle = GAUGE_SWEEP_ANGLE,
                    useCenter = false,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                    size = Size(size.width - stroke, size.height - stroke),
                    topLeft = Offset(stroke / 2, stroke / 2)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (good) "GOOD" else "BAD",
                    color = gaugeColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Text(
                    CurrencyFormatter.format(evaluation.earnings, evaluation.currency),
                    color = androidx.compose.ui.graphics.Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 26.sp
                )
            }
        }

        Text(
            "${fmt(evaluation.distanceKm)} km • ${fmt(evaluation.timeMinutes)} min",
            color = WaTextSecondary,
            style = MaterialTheme.typography.bodyMedium
        )
        Box(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(gaugeColor.copy(alpha = 0.16f))
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Text(
                CurrencyFormatter.formatRatePerKm(evaluation.ratePerKm, evaluation.currency),
                style = MaterialTheme.typography.labelSmall,
                color = gaugeColor
            )
        }
    }
}

@Composable
private fun EmptyGauge() {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(140.dp)) {
        Canvas(modifier = Modifier.size(140.dp)) {
            val stroke = 10.dp.toPx()
            drawArc(
                color = WaSurfaceVariant,
                startAngle = GAUGE_START_ANGLE,
                sweepAngle = GAUGE_SWEEP_ANGLE,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
                size = Size(size.width - stroke, size.height - stroke),
                topLeft = Offset(stroke / 2, stroke / 2)
            )
        }
    }
}

private fun fmt(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else String.format("%.1f", value)
