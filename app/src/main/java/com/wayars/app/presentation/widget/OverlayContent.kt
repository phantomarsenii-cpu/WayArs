package com.wayars.app.presentation.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wayars.app.R
import com.wayars.app.domain.model.OrderEvaluation
import com.wayars.app.presentation.ui.component.verdictColor
import com.wayars.app.presentation.ui.component.verdictLabel
import com.wayars.app.presentation.ui.theme.WaNeonGreen
import com.wayars.app.presentation.ui.theme.WaRed
import com.wayars.app.presentation.ui.theme.WaSurface
import com.wayars.app.presentation.ui.theme.WaSurfaceVariant
import com.wayars.app.presentation.ui.theme.WaTextSecondary
import com.wayars.app.util.CurrencyFormatter

/**
 * The floating card shown over Bolt/Uber/Wolt/FreeNow.
 *
 * Dragging lives ONLY on the header row via [onDragBy] + Compose's own
 * detectDragGestures — the Accept/Reject/Settings/Close buttons below are
 * never touched by any drag-handling code, which is what actually fixes taps
 * "not registering" (a raw View.OnTouchListener spanning the whole card used
 * to intercept every touch, including button taps, before Compose's click
 * detector ever got a clean look at it).
 */
@Composable
fun OverlayContent(
    evaluation: OrderEvaluation?,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onSettings: () -> Unit,
    onClose: () -> Unit,
    onDragBy: (dx: Float, dy: Float) -> Unit
) {
    Column(
        modifier = Modifier
            .width(260.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(WaSurface)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onDragBy(dragAmount.x, dragAmount.y)
                    }
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("WayArs", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.width(6.dp))
                Box(Modifier.size(8.dp).clip(CircleShape).background(WaNeonGreen))
            }
            Row {
                IconButton(onClick = onSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = null, tint = WaTextSecondary)
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = null, tint = WaTextSecondary)
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        if (evaluation == null) {
            Text(
                stringResource(R.string.dashboard_no_order),
                style = MaterialTheme.typography.bodyMedium,
                color = WaTextSecondary
            )
        } else {
            val verdictColor = verdictColor(evaluation.verdict)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(verdictColor.copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        verdictLabel(evaluation.verdict),
                        color = verdictColor,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            Spacer(Modifier.height(6.dp))
            Text(
                CurrencyFormatter.format(evaluation.earnings, evaluation.currency),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "${fmt(evaluation.distanceKm)} km • ${fmt(evaluation.timeMinutes)} min",
                style = MaterialTheme.typography.bodyMedium,
                color = WaTextSecondary
            )

            Spacer(Modifier.height(6.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(WaSurfaceVariant)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    CurrencyFormatter.formatRatePerKm(evaluation.ratePerKm, evaluation.currency),
                    style = MaterialTheme.typography.labelSmall,
                    color = WaNeonGreen
                )
            }

            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onAccept,
                    colors = ButtonDefaults.buttonColors(containerColor = WaNeonGreen, contentColor = Color.Black),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null)
                }
                Button(
                    onClick = onReject,
                    colors = ButtonDefaults.buttonColors(containerColor = WaRed, contentColor = Color.White),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Close, contentDescription = null)
                }
            }
        }
    }
}

private fun fmt(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else String.format("%.1f", value)
