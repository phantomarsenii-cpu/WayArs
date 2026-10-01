package com.wayars.app.presentation.ui.tour

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateRectAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wayars.app.R
import com.wayars.app.presentation.ui.theme.WaNeonGreen
import com.wayars.app.presentation.ui.theme.WaSurface
import com.wayars.app.presentation.ui.theme.WaSurfaceVariant
import com.wayars.app.presentation.ui.theme.WaTextSecondary
import com.wayars.app.util.rememberReducedMotionPreferred
import kotlin.math.max
import kotlin.math.roundToInt

private val DimColor = Color(0xD2040810)

/** Draws the tour on top of everything, but only while the tour is running. */
@Composable
fun TourHost(controller: TourController) {
    if (controller.running) TourOverlay(controller)
}

@Composable
private fun TourOverlay(c: TourController) {
    val density = LocalDensity.current
    val reduced = rememberReducedMotionPreferred()

    var origin by remember { mutableStateOf(Offset.Zero) }
    var area by remember { mutableStateOf(IntSize.Zero) }
    var cardHeight by remember { mutableIntStateOf(0) }

    val index = c.index
    val total = c.steps.size
    val isLast = index >= total - 1

    // System Back = previous step (first step: leave the tour).
    BackHandler { c.back() }

    // ---- spotlight hole (animates from step to step) ----
    val padPx = with(density) { 6.dp.toPx() }
    val rect: Rect? = c.targetRect?.translate(-origin)?.inflate(padPx)
    val center = Offset(area.width / 2f, area.height / 2f)
    val holeTarget = rect ?: Rect(center, 0f)
    val holeState = animateRectAsState(
        targetValue = holeTarget,
        animationSpec = if (reduced) snap<Rect>() else tween<Rect>(340, easing = FastOutSlowInEasing),
        label = "tourHole"
    )
    val pulseState: State<Float> = if (reduced) {
        remember { mutableFloatStateOf(0.9f) }
    } else {
        rememberInfiniteTransition(label = "tourPulse").animateFloat(
            initialValue = 0.45f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
            label = "tourPulseValue"
        )
    }

    // ---- card position ----
    val statusTop = WindowInsets.statusBars.getTop(density)
    val navBottom = WindowInsets.navigationBars.getBottom(density)
    val gap = with(density) { 16.dp.toPx() }
    val edge = with(density) { 12.dp.toPx() }
    val minY = statusTop + edge
    val maxY = max(minY, area.height - navBottom - edge - cardHeight)
    val targetY = (
        if (rect == null) {
            (area.height - cardHeight) / 2f
        } else {
            val below = area.height - navBottom - rect.bottom
            val above = rect.top - statusTop
            if (below >= cardHeight + gap || below >= above) rect.bottom + gap
            else rect.top - gap - cardHeight
        }
        ).coerceIn(minY, maxY)
    val cardY by animateFloatAsState(
        targetValue = targetY,
        animationSpec = if (reduced) snap<Float>() else tween<Float>(320, easing = FastOutSlowInEasing),
        label = "tourCardY"
    )
    val cardAlpha by animateFloatAsState(
        targetValue = if (c.settled) 1f else 0f,
        animationSpec = if (reduced) snap<Float>() else tween<Float>(220),
        label = "tourCardAlpha"
    )
    val progress by animateFloatAsState(
        targetValue = (index + 1).toFloat() / total,
        animationSpec = if (reduced) snap<Float>() else tween<Float>(300),
        label = "tourProgress"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned {
                origin = it.positionInWindow()
                area = it.size
            }
            // Swallow every touch so the app underneath can't be used during the tour.
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val hole = holeState.value
            val hasHole = hole.width > 2f && hole.height > 2f
            val radius = if (hasHole) minOf(with(density) { 16.dp.toPx() }, minOf(hole.width, hole.height) / 2f) else 0f
            val path = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(0f, 0f, size.width, size.height))
                if (hasHole) addRoundRect(RoundRect(hole, CornerRadius(radius, radius)))
            }
            drawPath(path, DimColor)
            if (hasHole) {
                drawRoundRect(
                    color = WaNeonGreen.copy(alpha = pulseState.value),
                    topLeft = hole.topLeft,
                    size = hole.size,
                    cornerRadius = CornerRadius(radius, radius),
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }

        val shape = RoundedCornerShape(22.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .offset { IntOffset(0, cardY.roundToInt()) }
                .graphicsLayer { alpha = cardAlpha }
                .onSizeChanged { cardHeight = it.height }
                .clip(shape)
                .background(WaSurface)
                .border(1.dp, WaSurfaceVariant, shape)
                .padding(horizontal = 20.dp, vertical = 18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f))
                Text(
                    stringResource(R.string.tour_counter, index + 1, total),
                    color = WaTextSecondary,
                    fontSize = 13.sp
                )
            }
            // thin progress line
            Box(
                modifier = Modifier
                    .padding(top = 6.dp, bottom = 12.dp)
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(WaSurfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .fillMaxHeight()
                        .background(WaNeonGreen)
                )
            }

            AnimatedContent(
                targetState = index,
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
                label = "tourText"
            ) { i ->
                val step = c.steps[i]
                Column {
                    Text(
                        stringResource(step.title),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp
                    )
                    Text(
                        stringResource(step.text),
                        color = WaTextSecondary,
                        fontSize = 15.sp,
                        lineHeight = 21.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isLast) {
                    TextButton(onClick = { c.finish() }) {
                        Text(stringResource(R.string.tour_skip), color = WaTextSecondary, fontSize = 15.sp)
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                if (index > 0) {
                    TextButton(onClick = { c.back() }) {
                        Text(stringResource(R.string.tour_back), color = Color.White, fontSize = 15.sp)
                    }
                }
                Button(
                    onClick = { c.next() },
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = WaNeonGreen, contentColor = Color.Black)
                ) {
                    Text(
                        stringResource(if (isLast) R.string.tour_done else R.string.tour_next),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}
