package com.wayars.app.presentation.widget

import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

private const val OVERLAY_ENTER_DURATION_MS = 200
private const val OVERLAY_EXIT_DURATION_MS = 140
private const val OVERLAY_HIDDEN_SCALE = 0.95f

/**
 * Wraps the verdict card in a fade + scale(0.95→1) transition instead of the
 * hard cut the raw WindowManager add/removeView used to produce. This is the
 * one moment in the whole app a courier actually looks at mid-drive, so it's
 * the one place a plain instant pop-in/pop-out was worst felt.
 *
 * [onHidden] fires once the exit animation actually reaches alpha 0 — that's
 * OverlayService's cue to do the real `windowManager.removeView(...)`, so the
 * view is only ever torn down AFTER it's finished animating away, never
 * mid-fade. Reduced-motion users skip straight to the end state (duration 0)
 * instead of getting the transition forced on them.
 */
@Composable
fun AnimatedOverlayCard(
    visible: Boolean,
    reducedMotion: Boolean,
    onHidden: () -> Unit = {},
    content: @Composable () -> Unit
) {
    val durationMs = if (reducedMotion) 0 else if (visible) OVERLAY_ENTER_DURATION_MS else OVERLAY_EXIT_DURATION_MS
    val easing = if (visible) LinearOutSlowInEasing else FastOutLinearInEasing
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = durationMs, easing = easing),
        label = "overlay_card_alpha",
        finishedListener = { finalValue -> if (!visible && finalValue <= 0f) onHidden() }
    )
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else OVERLAY_HIDDEN_SCALE,
        animationSpec = tween(durationMillis = durationMs, easing = easing),
        label = "overlay_card_scale"
    )
    Box(
        modifier = Modifier.graphicsLayer {
            this.alpha = alpha
            this.scaleX = scale
            this.scaleY = scale
        }
    ) {
        content()
    }
}


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
            .width(212.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(WaSurface)
            .padding(12.dp)
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
                Text(
                    "WayArs",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.width(5.dp))
                Box(Modifier.size(6.dp).clip(CircleShape).background(WaNeonGreen))
            }
            Row {
                IconButton(onClick = onSettings, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Filled.Settings, contentDescription = null, tint = WaTextSecondary, modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = onClose, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = null, tint = WaTextSecondary, modifier = Modifier.size(16.dp))
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        if (evaluation == null) {
            Text(
                stringResource(R.string.dashboard_no_order),
                fontSize = 12.sp,
                color = WaTextSecondary
            )
        } else {
            val verdictColor = verdictColor(evaluation.verdict)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(verdictColor.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        verdictLabel(evaluation.verdict),
                        color = verdictColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(Modifier.height(5.dp))
            Text(
                CurrencyFormatter.format(evaluation.earnings, evaluation.currency),
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "${fmt(evaluation.distanceKm)} km • ${fmt(evaluation.timeMinutes)} min",
                fontSize = 12.sp,
                color = WaTextSecondary
            )

            Spacer(Modifier.height(5.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(WaSurfaceVariant)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                // Under 1 km a €/km figure is technically correct but reads
                // as nonsense (it can exceed the order's own total) — show
                // net profit instead so short orders don't look broken.
                val badgeText = if (evaluation.distanceKm < OrderEvaluation.SHORT_TRIP_THRESHOLD_KM) {
                    stringResource(R.string.verdict_short_trip_net, CurrencyFormatter.format(evaluation.netProfit, evaluation.currency))
                } else {
                    CurrencyFormatter.formatRatePerKm(evaluation.ratePerKm, evaluation.currency)
                }
                Text(
                    badgeText,
                    fontSize = 10.sp,
                    color = WaNeonGreen
                )
            }

            Spacer(Modifier.height(11.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onAccept,
                    colors = ButtonDefaults.buttonColors(containerColor = WaNeonGreen, contentColor = Color.Black),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    modifier = Modifier.weight(1f).height(38.dp)
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Button(
                    onClick = onReject,
                    colors = ButtonDefaults.buttonColors(containerColor = WaRed, contentColor = Color.White),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    modifier = Modifier.weight(1f).height(38.dp)
                ) {
                    Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

private fun fmt(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else String.format("%.1f", value)
