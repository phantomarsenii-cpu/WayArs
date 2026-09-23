package com.wayars.app.presentation.ui.screen.paywall

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wayars.app.R
import com.wayars.app.presentation.PaywallUiState
import com.wayars.app.presentation.PlanOption
import com.wayars.app.presentation.PlanType
import com.wayars.app.presentation.ui.theme.WaProAuroraGreen
import com.wayars.app.presentation.ui.theme.WaProBackground
import com.wayars.app.presentation.ui.theme.WaProCardBg
import com.wayars.app.presentation.ui.theme.WaProCardBgSelected
import com.wayars.app.presentation.ui.theme.WaProCardBorder
import com.wayars.app.presentation.ui.theme.WaProCyan
import com.wayars.app.presentation.ui.theme.WaProGreen
import com.wayars.app.presentation.ui.theme.WaProGreenBadge
import com.wayars.app.presentation.ui.theme.WaProPurpleBadge
import com.wayars.app.presentation.ui.theme.WaProTextFaint
import com.wayars.app.presentation.ui.theme.WaProTextMuted
import com.wayars.app.presentation.ui.theme.WaProTextMuted2
import com.wayars.app.presentation.ui.theme.WaProTextMuted3
import com.wayars.app.util.PriceFormatter
import java.util.Locale

/*
 * WayArs Pro paywall — full rewrite, per the approved HTML reference
 * (wayars_pro_original_logo.html): a dark "aurora" theme replacing the
 * previous light-hero paywall design entirely. Every visual on this
 * screen is new; none of the old layout/geometry code was reused.
 *
 * Billing/business logic is untouched: this screen still reads
 * [PaywallUiState] (real-or-mock [PlanOption]s, loading/purchasing/error
 * flags) and calls the same [onLoadOfferings]/[onSelectPlan]/[onRestore]/
 * [onDismissError]/[onRetryGateCheck] callbacks the nav layer already
 * wires up — nothing in WayArsNavHost or SubscriptionViewModel needed to
 * change.
 *
 * The one behavioral difference from before: the HTML reference decouples
 * "which plan is highlighted" (a radio a user can tap to preview) from
 * "start the purchase" (a single CTA button below the cards). So plan
 * selection is now local UI state ([selectedPlan]), and the CTA is what
 * actually invokes [onSelectPlan] for whichever plan is currently
 * highlighted — the real purchase/mock-selection routing in
 * WayArsNavHost.onSelectPlan is unchanged and still decides whether that
 * turns into a real RevenueCat purchase or the offline mock-selection
 * toast.
 */

@Composable
fun PaywallScreen(
    state: PaywallUiState,
    onLoadOfferings: () -> Unit,
    onSelectPlan: (PlanOption) -> Unit,
    onRestore: () -> Unit,
    onDismissError: () -> Unit,
    gateErrorMessage: String? = null,
    onRetryGateCheck: () -> Unit = {}
) {
    LaunchedEffect(Unit) { onLoadOfferings() }

    var selectedPlan by rememberSaveable { mutableStateOf(PlanType.YEARLY) }

    // This outer Box is intentionally left WITHOUT any statusBarsPadding/
    // navigationBarsPadding of its own: it needs to fill the true screen
    // bounds edge-to-edge so WaProBackground and AuroraGlow paint all the
    // way behind the system status bar, instead of stopping short and
    // leaving a visible seam where a different background color (or none)
    // showed through above it. Only the inner, scrollable Column below is
    // inset — that's the actual text/buttons content, which does need to
    // clear both the status bar (top) and the system navigation bar
    // (bottom, e.g. gesture-nav phones) so nothing sits underneath it.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(WaProBackground)
    ) {
        AuroraGlow()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(top = 12.dp, bottom = 24.dp)
        ) {
            gateErrorMessage?.let { message ->
                ProGateErrorBanner(message = message, onRetry = onRetryGateCheck)
                Spacer(Modifier.height(16.dp))
            }

            Text(
                stringResource(R.string.paywall_pro_header),
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))
            LogoWithGlow()
            Spacer(Modifier.height(12.dp))

            HighlightedHeadline(stringResource(R.string.paywall_title))
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.paywall_subtitle),
                color = WaProTextMuted,
                fontSize = 14.sp,
                lineHeight = 19.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(24.dp))
            FeatureList()
            Spacer(Modifier.height(26.dp))

            val yearly = state.plans.find { it.plan == PlanType.YEARLY }
            val monthly = state.plans.find { it.plan == PlanType.MONTHLY }
            val weekly = state.plans.find { it.plan == PlanType.WEEKLY }

            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                yearly?.let { plan ->
                    val perMonth = estimateMonthlyFromYearly(plan.priceText)
                    PlanCard(
                        title = stringResource(R.string.paywall_plan_yearly_title),
                        priceLine = "${plan.priceText} ${stringResource(R.string.paywall_period_year)}",
                        originalPriceText = null,
                        trialText = stringResource(R.string.paywall_badge_trial7),
                        tagText = perMonth?.let { stringResource(R.string.paywall_per_month_hint, it) },
                        badge = BadgeSpec(stringResource(R.string.paywall_badge_best_value), WaProPurpleBadge),
                        selected = selectedPlan == PlanType.YEARLY,
                        onClick = { selectedPlan = PlanType.YEARLY }
                    )
                }
                monthly?.let { plan ->
                    PlanCard(
                        title = stringResource(R.string.paywall_plan_monthly_title),
                        priceLine = "${plan.priceText} ${stringResource(R.string.paywall_period_month)}",
                        originalPriceText = plan.originalPriceText,
                        trialText = stringResource(R.string.paywall_badge_trial7),
                        tagText = null,
                        badge = BadgeSpec(stringResource(R.string.paywall_badge_popular_discount), WaProGreenBadge),
                        selected = selectedPlan == PlanType.MONTHLY,
                        onClick = { selectedPlan = PlanType.MONTHLY }
                    )
                }
                weekly?.let { plan ->
                    PlanCard(
                        title = stringResource(R.string.paywall_plan_weekly_title),
                        priceLine = "${plan.priceText} ${stringResource(R.string.paywall_period_week)}",
                        originalPriceText = null,
                        trialText = null,
                        tagText = null,
                        badge = null,
                        selected = selectedPlan == PlanType.WEEKLY,
                        onClick = { selectedPlan = PlanType.WEEKLY }
                    )
                }
            }

            state.errorMessage?.let { message ->
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        message,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onDismissError) {
                        Text(stringResource(R.string.paywall_button_hide), color = WaProTextMuted, fontSize = 12.sp)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // "0" in whichever currency the highlighted plan actually
            // charges in (e.g. "0 zł" for a PLN purchase), not a hardcoded
            // "$0" — falls back to "$0" while only mock pricing has loaded.
            val selectedCurrencyCode = state.plans.find { it.plan == selectedPlan }?.priceCurrencyCode
            val zeroPriceText = PriceFormatter.zero(selectedCurrencyCode, fallback = "$0")

            AuroraCtaButton(
                text = stringResource(R.string.paywall_cta_trial, zeroPriceText),
                isLoading = state.isPurchasing,
                enabled = !state.isPurchasing,
                onClick = {
                    state.plans.find { it.plan == selectedPlan }?.let(onSelectPlan)
                }
            )

            Spacer(Modifier.height(16.dp))

            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TextButton(onClick = onRestore, enabled = !state.isPurchasing) {
                    Text(
                        stringResource(R.string.paywall_button_restore),
                        color = WaProTextFaint,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        textDecoration = TextDecoration.Underline
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            FooterLegal()
        }
    }
}

/** A soft radial glow behind the paywall header.
 *
 *  Previously this was a `Box` with a fixed `.height(340.dp)`, filled with a
 *  radial gradient whose color stops (0.0 -> 0.35 -> 0.7 -> 1.0 transparent)
 *  are fractions of its `radius`. Because that radius (900px) covered more
 *  vertical distance than the box was tall, the gradient hadn't reached
 *  its fully-transparent stop yet by the time it hit the box's bottom edge
 *  — so instead of fading out, it was hard-clipped there, leaving a visible
 *  straight seam across the screen where the box ended and the flat
 *  [WaProBackground] began.
 *
 *  Fixed by drawing across the ENTIRE screen instead of a short fixed-height
 *  box: the gradient itself still fades out (and reaches full transparency)
 *  within the same short distance near the top as before, but because
 *  there's no rectangular box edge nearby to cut it off, it blends into
 *  [WaProBackground] smoothly with nothing left to draw a hard line. The
 *  gradient's center is pinned to a fixed distance from the top (not the
 *  center of the draw area) so the glow still sits behind the header/logo,
 *  exactly like before. */
@Composable
private fun AuroraGlow() {
    val density = LocalDensity.current
    val auroraGreen = WaProAuroraGreen
    val auroraCyan = WaProCyan
    val auroraPurple = WaProPurpleBadge

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                val radiusPx = with(density) { 300.dp.toPx() }
                val centerYPx = with(density) { 150.dp.toPx() }
                drawRect(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(
                            0.0f to auroraGreen.copy(alpha = 0.20f),
                            0.35f to auroraCyan.copy(alpha = 0.12f),
                            0.7f to auroraPurple.copy(alpha = 0.08f),
                            1.0f to Color.Transparent
                        ),
                        center = Offset(size.width / 2f, centerYPx),
                        radius = radiusPx
                    )
                )
            }
    )
}

@Composable
private fun LogoWithGlow() {
    val infinite = rememberInfiniteTransition(label = "logo_glow")
    val glowAlpha by infinite.animateFloat(
        initialValue = 0.85f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Reverse),
        label = "logo_glow_alpha"
    )
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .size(180.dp)
                .background(
                    Brush.radialGradient(
                        listOf(WaProGreen.copy(alpha = 0.32f * glowAlpha), Color.Transparent)
                    )
                )
        )
        Image(
            painter = painterResource(R.drawable.wayars_icon_header),
            contentDescription = null,
            modifier = Modifier.size(120.dp)
        )
    }
}

@Composable
private fun HighlightedHeadline(rawTitle: String) {
    val brandWord = "WayArs"
    val idx = rawTitle.lastIndexOf(brandWord)
    val annotated = buildAnnotatedString {
        if (idx >= 0) {
            append(rawTitle.substring(0, idx))
            withStyle(SpanStyle(color = WaProGreen)) { append(brandWord) }
            append(rawTitle.substring(idx + brandWord.length))
        } else {
            append(rawTitle)
        }
    }
    Text(
        annotated,
        color = Color.White,
        fontSize = 26.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 30.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun FeatureList() {
    val features = listOf(
        stringResource(R.string.paywall_feature_all),
        stringResource(R.string.paywall_feature_presets),
        stringResource(R.string.paywall_feature_realtime),
        stringResource(R.string.paywall_feature_languages)
    )
    Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
        features.forEach { feature ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(WaProGreen),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = WaProBackground,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(feature, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

private data class BadgeSpec(val text: String, val color: Color)

@Composable
private fun PlanCard(
    title: String,
    priceLine: String,
    originalPriceText: String?,
    trialText: String?,
    tagText: String?,
    badge: BadgeSpec?,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        val shape = RoundedCornerShape(16.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (selected) {
                        Modifier.shadow(10.dp, shape, ambientColor = WaProGreen, spotColor = WaProGreen)
                    } else Modifier
                )
                .clip(shape)
                .background(if (selected) WaProCardBgSelected else WaProCardBg)
                .border(
                    width = if (selected) 2.dp else 1.5.dp,
                    color = if (selected) WaProGreen else WaProCardBorder,
                    shape = shape
                )
                .clickable(onClick = onClick)
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(priceLine, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    originalPriceText?.let {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            it,
                            color = WaProTextMuted2,
                            fontSize = 12.sp,
                            textDecoration = TextDecoration.LineThrough
                        )
                    }
                }
                trialText?.let {
                    Spacer(Modifier.height(1.dp))
                    Text(
                        it,
                        color = if (selected) WaProGreen else WaProGreen.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                tagText?.let {
                    Spacer(Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Sell, contentDescription = null, tint = WaProTextMuted2, modifier = Modifier.size(11.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(it, color = WaProTextMuted2, fontSize = 11.sp)
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            SelectionRadio(selected = selected)
        }

        badge?.let {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = 16.dp, y = (-10).dp)
                    .clip(RoundedCornerShape(50))
                    .background(it.color)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    it.text,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.3.sp
                )
            }
        }
    }
}

@Composable
private fun SelectionRadio(selected: Boolean) {
    if (selected) {
        val infinite = rememberInfiniteTransition(label = "radio_ping")
        val pingScale by infinite.animateFloat(
            initialValue = 1f,
            targetValue = 1.9f,
            animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart),
            label = "ping_scale"
        )
        val pingAlpha by infinite.animateFloat(
            initialValue = 0.35f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart),
            label = "ping_alpha"
        )
        Box(modifier = Modifier.size(22.dp), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .scale(pingScale)
                    .clip(CircleShape)
                    .background(WaProGreen.copy(alpha = pingAlpha))
            )
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(WaProGreen),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                )
            }
        }
    } else {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .border(1.5.dp, WaProCardBorder, CircleShape)
        )
    }
}

/** The animated tri-color "aurora" CTA: a shifting green→cyan→purple
 *  gradient fill plus a diagonal shimmer sweep, matching the reference's
 *  `.cta-aurora-btn` (gradientShift + shimmerSweep keyframes). */
@Composable
private fun AuroraCtaButton(
    text: String,
    isLoading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val infinite = rememberInfiniteTransition(label = "cta_shimmer")
    val shimmer by infinite.animateFloat(
        initialValue = -0.4f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(tween(2500, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmer_x"
    )
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale = if (pressed) 0.98f else 1f

    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .scale(pressScale)
            .shadow(12.dp, shape, ambientColor = WaProGreen, spotColor = WaProCyan)
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(WaProAuroraGreen, WaProCyan, WaProPurpleBadge)))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        // Diagonal shimmer highlight sweeping left to right, clipped to the button.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.35f), Color.Transparent),
                            start = androidx.compose.ui.geometry.Offset(shimmer * 800f - 200f, 0f),
                            end = androidx.compose.ui.geometry.Offset(shimmer * 800f + 100f, 200f)
                        )
                    )
            )
        }

        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Color.White)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.WorkspacePremium,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun FooterLegal() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            stringResource(R.string.paywall_no_charge_notice),
            color = WaProTextMuted,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Shield, contentDescription = null, tint = WaProTextMuted2, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.paywall_data_safe), color = WaProTextMuted2, fontSize = 11.sp)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.paywall_trial_notice),
            color = WaProTextMuted3,
            fontSize = 10.sp,
            lineHeight = 13.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ProGateErrorBanner(message: String, onRetry: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(WaProCardBg)
            .border(1.dp, WaProCardBorder, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(R.string.paywall_gate_error_title),
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(message, color = WaProTextMuted, fontSize = 12.sp)
        }
        Spacer(Modifier.width(8.dp))
        TextButton(onClick = onRetry) {
            Text(
                stringResource(R.string.paywall_gate_error_retry),
                color = WaProGreen,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * Best-effort "$X.XX / month" breakdown for the Yearly card, computed
 * from the plan's OWN real price text (whether that's a live RevenueCat
 * formatted price or the [MockPlans] fallback) — never a separately
 * hardcoded number. Returns null (hiding the row entirely) if the price
 * text can't be parsed as a leading-currency-symbol + amount, e.g. an
 * unusual locale formatting this doesn't recognize, rather than showing
 * a guessed value.
 */
private fun estimateMonthlyFromYearly(priceText: String): String? {
    val match = Regex("""^([^\d]*)([\d]+(?:[.,]\d+)?)(.*)$""").find(priceText.trim()) ?: return null
    val prefix = match.groupValues[1]
    val amount = match.groupValues[2].replace(',', '.').toFloatOrNull() ?: return null
    val perMonth = amount / 12f
    return prefix + String.format(Locale.US, "%.2f", perMonth)
}
