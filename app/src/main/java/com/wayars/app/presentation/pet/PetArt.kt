package com.wayars.app.presentation.pet

import android.annotation.SuppressLint
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.wayars.app.pet.PetState

/** What the pet is doing right now; decides which picture is shown. */
enum class PetMood { NEUTRAL, WORKING, HAPPY, COOL, EATING, LEVEL_UP, SLEEPING, BREAK, SAD, HELPER, HELLO }

/**
 * Draws the pet for [stage] + [mood].
 *
 * Real artwork can be dropped into res/drawable without touching code:
 *   pet_<stage>_<mood>.png   e.g. pet_teen_happy.png, pet_baby_sleeping.png, pet_egg_neutral.png
 * Lookup order: exact stage+mood → pet_<stage>_neutral → pet_teen_<mood> → built-in drawing.
 * Stage and mood names are the enum names in lower case (egg, baby, teen, pro, legend /
 * neutral, working, happy, cool, eating, level_up, sleeping, break, sad, helper, hello).
 */
@SuppressLint("DiscouragedApi")
@Composable
fun PetArt(stage: PetState.Stage, mood: PetMood, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val resId = remember(stage, mood) {
        val res = context.resources
        val pkg = context.packageName
        val s = stage.name.lowercase()
        val m = mood.name.lowercase()
        listOf("pet_${s}_$m", "pet_${s}_neutral", "pet_teen_$m")
            .map { res.getIdentifier(it, "drawable", pkg) }
            .firstOrNull { it != 0 } ?: 0
    }

    val transition = rememberInfiniteTransition(label = "pet")
    val bob by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Reverse),
        label = "bob"
    )
    val clock by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing)),
        label = "clock"
    )
    val jump = when (mood) {
        PetMood.LEVEL_UP, PetMood.HAPPY -> 10f
        PetMood.SLEEPING -> 1.5f
        else -> 4f
    }

    Box(modifier = modifier.graphicsLayer { translationY = -bob * jump * density }) {
        if (resId != 0) {
            // The artwork is normalised to one size; smaller stages are shown smaller so
            // growing up is visible.
            val stageScale = when (stage) {
                PetState.Stage.EGG -> 0.74f
                PetState.Stage.BABY -> 0.80f
                PetState.Stage.TEEN -> 0.92f
                else -> 1f
            }
            Image(
                painter = painterResource(resId),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = stageScale
                    scaleY = stageScale
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.97f)
                }
            )
        } else {
            Canvas(Modifier.fillMaxSize()) { drawChameleon(stage, mood, blink = clock in 0.0f..0.04f, t = clock) }
        }
    }
}

// --------------------------------------------------------------------------------------------
// Built-in placeholder drawing (used until real artwork is added).

private val Green = Color(0xFF39E07A)
private val GreenDark = Color(0xFF1FA85A)
private val Crest = Color(0xFF3FA9F5)
private val Hoodie = Color(0xFF14161F)
private val Neon = Color(0xFF00E676)
private val Ink = Color(0xFF0B0E14)

private fun DrawScope.drawChameleon(stage: PetState.Stage, mood: PetMood, blink: Boolean, t: Float) {
    val w = size.width
    val h = size.height
    if (stage == PetState.Stage.EGG) {
        drawEgg(mood, t)
        return
    }
    val k = when (stage) {
        PetState.Stage.BABY -> 0.72f
        PetState.Stage.TEEN -> 0.86f
        PetState.Stage.PRO -> 0.94f
        else -> 1f
    }
    scale(k, pivot = Offset(w / 2, h * 0.95f)) {
        // tail spiral
        val tail = Path().apply {
            moveTo(w * 0.40f, h * 0.80f)
            cubicTo(w * 0.18f, h * 0.84f, w * 0.10f, h * 0.70f, w * 0.18f, h * 0.62f)
            cubicTo(w * 0.24f, h * 0.56f, w * 0.32f, h * 0.62f, w * 0.27f, h * 0.68f)
        }
        drawPath(tail, GreenDark, style = Stroke(width = w * 0.06f, cap = StrokeCap.Round))
        // body / hoodie (teen and up wear the W hoodie)
        val dressed = stage != PetState.Stage.BABY
        drawRoundRect(
            if (dressed) Hoodie else Green,
            topLeft = Offset(w * 0.34f, h * 0.52f), size = Size(w * 0.34f, h * 0.36f),
            cornerRadius = CornerRadius(w * 0.12f)
        )
        if (dressed) {
            drawW(Offset(w * 0.51f, h * 0.68f), w * 0.07f)
        }
        // feet
        drawRoundRect(Ink, Offset(w * 0.35f, h * 0.86f), Size(w * 0.13f, h * 0.06f), CornerRadius(w * 0.03f))
        drawRoundRect(Ink, Offset(w * 0.54f, h * 0.86f), Size(w * 0.13f, h * 0.06f), CornerRadius(w * 0.03f))
        // head + crest
        val headC = Offset(w * 0.52f, h * 0.38f)
        val headR = w * 0.21f
        drawCircle(Crest, radius = headR * 0.9f, center = headC + Offset(0f, -headR * 0.35f))
        drawCircle(Green, radius = headR, center = headC)
        // eye
        val eyeC = headC + Offset(headR * 0.25f, -headR * 0.05f)
        val eyeR = headR * 0.42f
        val closed = blink || mood == PetMood.SLEEPING || mood == PetMood.EATING || mood == PetMood.BREAK
        if (closed) {
            drawArc(Ink, 0f, 180f, false, topLeft = eyeC - Offset(eyeR * 0.7f, eyeR * 0.3f),
                size = Size(eyeR * 1.4f, eyeR * 0.8f), style = Stroke(w * 0.022f, cap = StrokeCap.Round))
        } else {
            drawCircle(Color.White, eyeR, eyeC)
            drawCircle(Ink, eyeR * 0.55f, eyeC + Offset(eyeR * 0.12f, 0f))
            drawCircle(Color.White, eyeR * 0.15f, eyeC + Offset(eyeR * 0.3f, -eyeR * 0.22f))
        }
        // mouth
        val mouthTop = headC + Offset(-headR * 0.55f, headR * 0.25f)
        when (mood) {
            PetMood.SAD -> drawArc(Ink, 200f, 140f, false, topLeft = mouthTop + Offset(0f, headR * 0.2f),
                size = Size(headR * 0.9f, headR * 0.5f), style = Stroke(w * 0.018f, cap = StrokeCap.Round))
            PetMood.HAPPY, PetMood.LEVEL_UP, PetMood.HELLO -> drawArc(Color(0xFFE5486B), 10f, 160f, true,
                topLeft = mouthTop, size = Size(headR * 0.9f, headR * 0.6f))
            else -> drawArc(Ink, 20f, 140f, false, topLeft = mouthTop,
                size = Size(headR * 0.9f, headR * 0.45f), style = Stroke(w * 0.018f, cap = StrokeCap.Round))
        }
        // mood props
        when (mood) {
            PetMood.COOL -> drawRoundRect(Ink, eyeC - Offset(eyeR * 1.1f, eyeR * 0.6f), Size(eyeR * 2.3f, eyeR * 1.1f), CornerRadius(eyeR * 0.4f))
            PetMood.WORKING -> {
                drawArc(Ink, 180f, 180f, true, topLeft = headC - Offset(headR * 1.05f, headR * 1.15f), size = Size(headR * 2.1f, headR * 1.4f))
                drawRoundRect(Ink, Offset(w * 0.68f, h * 0.50f), Size(w * 0.22f, h * 0.22f), CornerRadius(w * 0.03f))
                drawW(Offset(w * 0.79f, h * 0.61f), w * 0.06f)
            }
            PetMood.SLEEPING -> drawZzz(Offset(w * 0.78f, h * 0.18f), w, t)
            PetMood.SAD -> drawCloud(Offset(w * 0.78f, h * 0.14f), w)
            PetMood.BREAK -> {
                drawRoundRect(Color(0xFF2A2F3E), Offset(w * 0.66f, h * 0.60f), Size(w * 0.14f, h * 0.15f), CornerRadius(w * 0.02f))
                drawCircle(Color(0xFF2A2F3E), w * 0.035f, Offset(w * 0.82f, h * 0.67f), style = Stroke(w * 0.015f))
            }
            PetMood.LEVEL_UP, PetMood.HAPPY -> drawSparkles(w, h, t)
            PetMood.EATING -> drawBolt(Offset(w * 0.80f, h * 0.30f), w)
            PetMood.HELPER -> drawCircle(Neon, w * 0.04f, Offset(w * 0.86f, h * 0.30f))
            else -> Unit
        }
        if (stage == PetState.Stage.LEGEND) drawCrown(headC - Offset(0f, headR * 1.25f), headR)
    }
}

private fun DrawScope.drawEgg(mood: PetMood, t: Float) {
    val w = size.width
    val h = size.height
    rotate(degrees = (t - 0.5f) * 8f, pivot = Offset(w / 2, h * 0.9f)) {
        drawOval(Green, topLeft = Offset(w * 0.28f, h * 0.22f), size = Size(w * 0.44f, h * 0.66f))
        drawCircle(GreenDark, w * 0.04f, Offset(w * 0.42f, h * 0.45f))
        drawCircle(Crest, w * 0.03f, Offset(w * 0.58f, h * 0.60f))
        drawCircle(GreenDark, w * 0.025f, Offset(w * 0.47f, h * 0.72f))
        val crack = Path().apply {
            moveTo(w * 0.33f, h * 0.48f); lineTo(w * 0.40f, h * 0.52f); lineTo(w * 0.46f, h * 0.47f)
            lineTo(w * 0.53f, h * 0.53f); lineTo(w * 0.60f, h * 0.48f); lineTo(w * 0.67f, h * 0.52f)
        }
        drawPath(crack, Ink, style = Stroke(w * 0.012f, cap = StrokeCap.Round))
    }
    if (mood == PetMood.SLEEPING) drawZzz(Offset(w * 0.74f, h * 0.18f), w, t)
}

private fun DrawScope.drawW(center: Offset, s: Float) {
    val p = Path().apply {
        moveTo(center.x - s, center.y - s * 0.6f)
        lineTo(center.x - s * 0.5f, center.y + s * 0.6f)
        lineTo(center.x, center.y - s * 0.1f)
        lineTo(center.x + s * 0.5f, center.y + s * 0.6f)
        lineTo(center.x + s, center.y - s * 0.6f)
    }
    drawPath(p, Neon, style = Stroke(s * 0.35f, cap = StrokeCap.Round))
}

private fun DrawScope.drawZzz(at: Offset, w: Float, t: Float) {
    val s = w * 0.05f
    for (i in 0 until 3) {
        val o = at + Offset(i * s * 0.9f, -i * s * 1.1f - t * s)
        val p = Path().apply {
            moveTo(o.x, o.y); lineTo(o.x + s, o.y); lineTo(o.x, o.y + s); lineTo(o.x + s, o.y + s)
        }
        drawPath(p, Crest.copy(alpha = 1f - i * 0.25f), style = Stroke(w * 0.012f, cap = StrokeCap.Round))
    }
}

private fun DrawScope.drawCloud(at: Offset, w: Float) {
    val c = Color(0xFF3B4A7A)
    drawCircle(c, w * 0.05f, at)
    drawCircle(c, w * 0.06f, at + Offset(w * 0.06f, -w * 0.02f))
    drawCircle(c, w * 0.045f, at + Offset(w * 0.12f, w * 0.005f))
    for (i in 0 until 3) drawLine(Crest, at + Offset(i * w * 0.05f, w * 0.06f), at + Offset(i * w * 0.05f - w * 0.01f, w * 0.10f), w * 0.01f, StrokeCap.Round)
}

private fun DrawScope.drawSparkles(w: Float, h: Float, t: Float) {
    val pts = listOf(Offset(w * 0.2f, h * 0.2f), Offset(w * 0.85f, h * 0.25f), Offset(w * 0.15f, h * 0.55f), Offset(w * 0.88f, h * 0.55f))
    pts.forEachIndexed { i, p ->
        val a = 0.4f + 0.6f * kotlin.math.abs(kotlin.math.sin((t + i * 0.25f) * 6.28f))
        drawCircle(Neon.copy(alpha = a), w * 0.018f, p)
    }
}

private fun DrawScope.drawBolt(at: Offset, w: Float) {
    val s = w * 0.07f
    val p = Path().apply {
        moveTo(at.x, at.y - s); lineTo(at.x - s * 0.5f, at.y + s * 0.1f); lineTo(at.x, at.y + s * 0.1f)
        lineTo(at.x - s * 0.2f, at.y + s); lineTo(at.x + s * 0.5f, at.y - s * 0.1f); lineTo(at.x, at.y - s * 0.1f); close()
    }
    drawPath(p, Color(0xFFFFD23F))
}

private fun DrawScope.drawCrown(at: Offset, r: Float) {
    translate(at.x - r * 0.5f, at.y) {
        val p = Path().apply {
            moveTo(0f, r * 0.4f); lineTo(0f, 0f); lineTo(r * 0.25f, r * 0.2f); lineTo(r * 0.5f, -r * 0.05f)
            lineTo(r * 0.75f, r * 0.2f); lineTo(r, 0f); lineTo(r, r * 0.4f); close()
        }
        drawPath(p, Color(0xFFFFC83D))
    }
}
