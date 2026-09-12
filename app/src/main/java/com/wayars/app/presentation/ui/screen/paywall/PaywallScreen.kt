package com.wayars.app.presentation.ui.screen.paywall

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.wayars.app.R
import com.wayars.app.presentation.PaywallUiState
import com.wayars.app.presentation.PlanOption
import com.wayars.app.presentation.PlanType
import com.wayars.app.presentation.ui.theme.WaBackground
import com.wayars.app.presentation.ui.theme.WaHeroPhoneBottom
import com.wayars.app.presentation.ui.theme.WaHeroPhoneTop
import com.wayars.app.presentation.ui.theme.WaNeonGreen
import com.wayars.app.presentation.ui.theme.WaNeonGreenDark
import com.wayars.app.presentation.ui.theme.WaPurple
import com.wayars.app.presentation.ui.theme.WaPurpleLight
import com.wayars.app.presentation.ui.theme.WaSurface
import com.wayars.app.presentation.ui.theme.WaSurfaceVariant
import com.wayars.app.presentation.ui.theme.WaTagBolt
import com.wayars.app.presentation.ui.theme.WaTagBoltDark
import com.wayars.app.presentation.ui.theme.WaTagFreeNow
import com.wayars.app.presentation.ui.theme.WaTagStuart
import com.wayars.app.presentation.ui.theme.WaTagUber
import com.wayars.app.presentation.ui.theme.WaTagWolt
import com.wayars.app.presentation.ui.theme.WaTagWoltDark
import com.wayars.app.presentation.ui.theme.WaTextPrimary
import com.wayars.app.presentation.ui.theme.WaTextSecondary
import com.wayars.app.presentation.ui.theme.WaWeeklyBorder
import com.wayars.app.presentation.ui.theme.WaWeeklyCard

/**
 * Static per-plan copy (title, "days of access" subtitle, discount/trial
 * badge, period suffix). This is marketing/layout content, not pricing —
 * pricing always comes from [PlanOption.priceText], which is either the
 * live RevenueCat [Package.product.price.formatted] or the hardcoded
 * fallback in [com.wayars.app.presentation.MockPlans] (see
 * [SubscriptionViewModel]). That keeps what's shown always consistent
 * with what Google Play will actually charge once real offerings load,
 * while guaranteeing the layout never has a missing/blank card.
 */
private data class PlanCopy(
    val title: String,
    val daysLabel: String,
    val discountLabel: String?,
    val discountColor: Color,
    val trialLabel: String,
    val periodSuffix: String
)

@Composable
private fun planCopyFor(plan: PlanType): PlanCopy = when (plan) {
    PlanType.YEARLY -> PlanCopy(
        title = stringResource(R.string.paywall_plan_yearly_title),
        daysLabel = stringResource(R.string.paywall_plan_yearly_days),
        discountLabel = "-30%",
        discountColor = WaPurpleLight,
        trialLabel = stringResource(R.string.paywall_badge_trial7),
        periodSuffix = stringResource(R.string.paywall_period_year)
    )
    PlanType.MONTHLY -> PlanCopy(
        title = stringResource(R.string.paywall_plan_monthly_title),
        daysLabel = stringResource(R.string.paywall_plan_monthly_days),
        discountLabel = "-15%",
        discountColor = WaNeonGreen,
        trialLabel = stringResource(R.string.paywall_badge_trial7),
        periodSuffix = stringResource(R.string.paywall_period_month)
    )
    PlanType.WEEKLY -> PlanCopy(
        title = stringResource(R.string.paywall_plan_weekly_title),
        daysLabel = stringResource(R.string.paywall_plan_weekly_days),
        discountLabel = null,
        discountColor = Color.Transparent,
        trialLabel = stringResource(R.string.paywall_badge_trial7),
        periodSuffix = stringResource(R.string.paywall_period_week)
    )
}

@Composable
private fun planFeatures(): List<String> = listOf(
    stringResource(R.string.paywall_feature_all),
    stringResource(R.string.paywall_feature_presets),
    stringResource(R.string.paywall_feature_realtime),
    stringResource(R.string.paywall_feature_languages)
)

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WaBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 22.dp)
    ) {
        gateErrorMessage?.let { message ->
            GateErrorBanner(message = message, onRetry = onRetryGateCheck)
            Spacer(Modifier.height(16.dp))
        }

        Header()

        Spacer(Modifier.height(22.dp))

        Text(
            stringResource(R.string.paywall_section_eyebrow),
            color = WaNeonGreen,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.paywall_section_title),
            color = WaTextPrimary,
            fontSize = 23.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 28.sp
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.paywall_section_subtitle),
            color = WaTextSecondary,
            fontSize = 14.sp
        )

        Spacer(Modifier.height(20.dp))

        // Two-column "bento" grid matching the reference: Yearly + Weekly
        // stacked on the left, the featured Monthly card on the right.
        // IntrinsicSize.Min on the Row lets the right (Monthly) column stretch
        // to match the combined height of the left column's two stacked cards
        // (Yearly + gap + Weekly) — the reference's
        // [ YEARLY ][ MONTHLY, tall ]
        // [ WEEKLY ][           ]
        // bento composition, not three independently-sized cards.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            val yearly = state.plans.find { it.plan == PlanType.YEARLY }
            val monthly = state.plans.find { it.plan == PlanType.MONTHLY }
            val weekly = state.plans.find { it.plan == PlanType.WEEKLY }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                yearly?.let {
                    PlanCard(
                        plan = it,
                        isPurchasing = state.isPurchasing,
                        isMockSelected = state.selectedMockPlan == it.plan,
                        onSelect = onSelectPlan,
                        modifier = Modifier.weight(1f)
                    )
                }
                weekly?.let {
                    PlanCard(
                        plan = it,
                        isPurchasing = state.isPurchasing,
                        isMockSelected = state.selectedMockPlan == it.plan,
                        onSelect = onSelectPlan,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                monthly?.let {
                    PlanCard(
                        plan = it,
                        isPurchasing = state.isPurchasing,
                        isMockSelected = state.selectedMockPlan == it.plan,
                        onSelect = onSelectPlan,
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight()
                    )
                }
            }
        }

        state.errorMessage?.let { message ->
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    message,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onDismissError) {
                    Text(stringResource(R.string.paywall_button_hide), color = WaTextSecondary, fontSize = 12.sp)
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        TrialNotice()

        Spacer(Modifier.height(16.dp))

        TextButton(
            onClick = onRestore,
            enabled = !state.isPurchasing,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.paywall_button_restore), color = WaTextSecondary, fontSize = 14.sp)
        }

        Spacer(Modifier.height(20.dp))

        TrustFooter()
    }
}

@Composable
private fun GateErrorBanner(message: String, onRetry: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(WaSurface)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(R.string.paywall_gate_error_title),
                color = WaTextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(message, color = WaTextSecondary, fontSize = 12.sp)
        }
        Spacer(Modifier.width(8.dp))
        TextButton(onClick = onRetry) {
            Text(stringResource(R.string.paywall_gate_error_retry), color = WaNeonGreen, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/**
 * Brand header: icon + "WayArs" wordmark + tagline, the "full access" pill,
 * the two-tone headline and subtitle sitting alongside the hero
 * illustration (phone mock-up + floating partner-platform tags), the 4
 * feature chips row, and a hairline divider that closes the hero block —
 * matching the approved design.
 */
@Composable
private fun Header() {
    Column(horizontalAlignment = Alignment.Start) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.wayars_icon_header),
                contentDescription = null,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(11.dp))
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Row {
                    Text("Way", color = WaTextPrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Ars", color = WaNeonGreen, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                }
                Text(
                    stringResource(R.string.paywall_tagline),
                    color = WaTextSecondary,
                    fontSize = 9.sp,
                    letterSpacing = 1.2.sp
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .border(BorderStroke(1.dp, WaNeonGreen.copy(alpha = 0.5f)), RoundedCornerShape(50))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(stringResource(R.string.paywall_badge_full_access), color = WaNeonGreen, fontSize = 11.sp)
                }
                Spacer(Modifier.height(12.dp))
                Column {
                    val titleLines = stringResource(R.string.paywall_title).split("\n")
                    Text(
                        titleLines.getOrElse(0) { "" },
                        color = WaTextPrimary,
                        fontSize = 23.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 28.sp
                    )
                    if (titleLines.size > 1) {
                        Text(
                            titleLines[1],
                            style = TextStyle(
                                brush = Brush.horizontalGradient(listOf(WaNeonGreen, Color(0xFF22D3EE))),
                                fontSize = 23.sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 28.sp
                            )
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.paywall_subtitle),
                    color = WaTextSecondary,
                    fontSize = 12.5.sp,
                    lineHeight = 17.sp
                )
            }
            Spacer(Modifier.width(6.dp))
            HeroIllustration(modifier = Modifier.width(126.dp).height(178.dp))
        }

        Spacer(Modifier.height(18.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FeatureChip(Icons.Filled.Bolt, stringResource(R.string.paywall_chip_analysis), Modifier.weight(1f))
            FeatureChip(Icons.Filled.Shield, stringResource(R.string.paywall_chip_filters), Modifier.weight(1f))
            FeatureChip(Icons.Filled.ShowChart, stringResource(R.string.paywall_chip_income), Modifier.weight(1f))
            FeatureChip(Icons.Filled.Public, stringResource(R.string.paywall_chip_languages), Modifier.weight(1f))
        }

        Spacer(Modifier.height(20.dp))
        Divider(color = WaSurfaceVariant.copy(alpha = 0.7f), thickness = 1.dp)
    }
}

/**
 * Stylized "phone + partner platforms" hero graphic, matching the
 * approved design's composition: the app icon centered on a phone-shaped
 * card with a glowing route line, surrounded by floating tags naming the
 * delivery/taxi platforms this app's data sources are compatible with.
 *
 * The platform tags are plain colored text labels — deliberately NOT
 * recreations of each brand's logo/wordmark artwork, to avoid
 * impersonating third-party trademarks. Naming a compatible service in
 * your own app's UI as plain text is standard practice; redrawing their
 * logo artwork is not.
 */
@Composable
private fun HeroIllustration(modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        // Ambient glow behind the phone.
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(110.dp)
                .background(
                    Brush.radialGradient(
                        listOf(WaNeonGreen.copy(alpha = 0.28f), Color.Transparent)
                    )
                )
        )

        // Phone frame.
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .width(72.dp)
                .height(150.dp)
                .shadow(
                    elevation = 14.dp,
                    shape = RoundedCornerShape(20.dp),
                    ambientColor = WaNeonGreen.copy(alpha = 0.5f),
                    spotColor = WaNeonGreen.copy(alpha = 0.5f)
                )
                .clip(RoundedCornerShape(20.dp))
                .background(Brush.verticalGradient(listOf(WaHeroPhoneTop, WaHeroPhoneBottom)))
                .border(
                    BorderStroke(1.5.dp, Brush.verticalGradient(listOf(WaNeonGreen, WaPurple))),
                    RoundedCornerShape(20.dp)
                )
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 6.dp)
                    .width(22.dp)
                    .height(5.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black)
            )
            Image(
                painter = painterResource(R.drawable.wayars_icon_header),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = (-8).dp)
                    .size(34.dp)
            )
            RouteGlyph(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 14.dp)
                    .size(width = 46.dp, height = 26.dp)
            )
        }

        PlatformTag(
            "Bolt",
            Brush.horizontalGradient(listOf(WaTagBoltDark, WaTagBolt)),
            Color(0xFF04240F),
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = (-4).dp, y = 8.dp)
                .rotate(-7f)
        )
        PlatformTag(
            "Uber",
            Brush.horizontalGradient(listOf(WaTagUber, WaTagUber)),
            WaTextPrimary,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = (-10).dp, y = 4.dp)
                .rotate(-3f)
        )
        PlatformTag(
            "Wolt",
            Brush.horizontalGradient(listOf(WaTagWoltDark, WaTagWolt)),
            WaTextPrimary,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 6.dp, y = 14.dp)
                .rotate(6f)
        )
        PlatformTag(
            "Stuart",
            Brush.horizontalGradient(listOf(WaTagStuart, WaTagStuart)),
            WaTextPrimary,
            icon = Icons.Filled.LocalShipping,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = 10.dp, y = 30.dp)
        )
        PlatformTag(
            "Free Now",
            Brush.horizontalGradient(listOf(WaTagFreeNow, WaTagFreeNow)),
            WaTextPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 4.dp, y = (-2).dp)
                .rotate(-4f)
        )
    }
}

/** Small glowing dashed route line with a pin, drawn inside the hero phone. */
@Composable
private fun RouteGlyph(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val path = Path().apply {
            moveTo(2f, size.height * 0.85f)
            cubicTo(
                size.width * 0.25f, size.height * 0.9f,
                size.width * 0.3f, size.height * 0.15f,
                size.width * 0.55f, size.height * 0.35f
            )
            cubicTo(
                size.width * 0.75f, size.height * 0.5f,
                size.width * 0.8f, size.height * 0.1f,
                size.width - 2f, size.height * 0.2f
            )
        }
        drawPath(
            path,
            color = WaNeonGreen,
            style = Stroke(width = size.height * 0.09f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(size.height * 0.22f, size.height * 0.16f)))
        )
        drawCircle(color = WaNeonGreen, radius = size.height * 0.11f, center = Offset(size.width - 2f, size.height * 0.2f))
    }
}

/** A floating "compatible platform" tag used in [HeroIllustration]. Plain text, no logo artwork. */
@Composable
private fun PlatformTag(
    name: String,
    brush: Brush,
    textColor: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    Row(
        modifier = modifier
            .shadow(elevation = 6.dp, shape = RoundedCornerShape(9.dp))
            .clip(RoundedCornerShape(9.dp))
            .background(brush)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon?.let {
            Icon(it, contentDescription = null, tint = textColor, modifier = Modifier.size(11.dp))
            Spacer(Modifier.width(3.dp))
        }
        Text(name, color = textColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun FeatureChip(icon: ImageVector, label: String, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(9.dp))
                .border(BorderStroke(1.dp, WaNeonGreen.copy(alpha = 0.55f)), RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = WaNeonGreen, modifier = Modifier.size(15.dp))
        }
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            color = WaTextSecondary,
            fontSize = 9.5.sp,
            lineHeight = 11.sp,
            maxLines = 2
        )
    }
}

/**
 * Colored "neon" elevation behind a plan card. Uses [shadow]'s ambient/spot
 * color parameters (API 28+); on API 26-27 devices (this app's minSdk is
 * 26) the color is ignored by the platform and a plain neutral shadow is
 * drawn instead — a harmless, expected degrade, not a crash risk.
 */
private fun Modifier.androidxShadowGlow(color: Color, shape: RoundedCornerShape): Modifier = this.shadow(
    elevation = 20.dp,
    shape = shape,
    ambientColor = color.copy(alpha = 0.6f),
    spotColor = color.copy(alpha = 0.6f)
)

/** Visual accent per plan: border/glow color, card background and button style. */
private data class PlanAccent(
    val borderColor: Color,
    val glowColor: Color,
    val cardBrush: Brush,
    val iconTint: Color
)

@Composable
private fun accentFor(plan: PlanType): PlanAccent = when (plan) {
    PlanType.YEARLY -> PlanAccent(
        borderColor = WaPurpleLight,
        glowColor = WaPurple,
        cardBrush = Brush.verticalGradient(listOf(WaPurple.copy(alpha = 0.22f), WaSurface)),
        iconTint = WaPurpleLight
    )
    PlanType.MONTHLY -> PlanAccent(
        borderColor = WaNeonGreen,
        glowColor = WaNeonGreen,
        cardBrush = Brush.verticalGradient(listOf(WaSurface, WaNeonGreenDark.copy(alpha = 0.18f))),
        iconTint = WaNeonGreen
    )
    PlanType.WEEKLY -> PlanAccent(
        borderColor = WaWeeklyBorder,
        glowColor = Color.Transparent,
        cardBrush = Brush.verticalGradient(listOf(WaWeeklyCard, WaWeeklyCard)),
        iconTint = WaTextSecondary
    )
}

@Composable
private fun PlanCard(
    plan: PlanOption,
    isPurchasing: Boolean,
    isMockSelected: Boolean,
    onSelect: (PlanOption) -> Unit,
    modifier: Modifier = Modifier
) {
    val copy = planCopyFor(plan.plan)
    val isFeatured = plan.plan == PlanType.MONTHLY
    val accent = accentFor(plan.plan)
    val cardShape = RoundedCornerShape(24.dp)

    // Purchases stay disabled mid-flight, but the card is ALWAYS clickable
    // otherwise — even before real RevenueCat offerings have loaded (or
    // when they never will, e.g. offline / pre-Play-Store-release). See
    // PaywallScreen's onSelectPlan wiring: a plan with no live [Package]
    // yet falls back to a local mock selection instead of a no-op tap, so
    // the UI never feels "dead".
    val clickEnabled = !isPurchasing

    // A selected card always lights up with the same vivid green frame,
    // regardless of its own accent color (purple/green/gray) — matching
    // the approved design's selection state.
    val glowColor = if (isMockSelected) WaNeonGreen else accent.glowColor
    val borderColor = if (isMockSelected) WaNeonGreen else accent.borderColor
    val borderWidth = if (isMockSelected) 2.5.dp else if (isFeatured) 1.5.dp else 1.dp

    Box(modifier = modifier.fillMaxWidth().fillMaxHeight()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .then(if (isFeatured) Modifier.padding(top = 16.dp) else Modifier)
                // Shadow MUST come before clip/background: elevation shadows
                // are drawn outside the layout bounds, so clipping first
                // would cut the glow off entirely.
                .then(
                    if (glowColor != Color.Transparent) {
                        Modifier.androidxShadowGlow(glowColor, cardShape)
                    } else Modifier
                )
                .clip(cardShape)
                .background(accent.cardBrush)
                .border(BorderStroke(borderWidth, borderColor), cardShape)
                .clickable(enabled = clickEnabled) { onSelect(plan) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .padding(18.dp)
            ) {
                CalendarCrownIcon(tint = accent.iconTint, size = 24.dp)
                Spacer(Modifier.height(8.dp))
                Text(copy.title, color = WaTextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text(copy.daysLabel, color = WaTextSecondary, fontSize = 11.sp)

                Spacer(Modifier.height(12.dp))
                if (copy.discountLabel != null) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .border(BorderStroke(1.dp, accent.borderColor.copy(alpha = 0.5f)), RoundedCornerShape(50))
                            .padding(3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(copy.discountColor, copy.discountColor.copy(alpha = 0.65f))
                                    )
                                )
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(copy.discountLabel, color = Color(0xFF04140C), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            copy.trialLabel,
                            color = WaTextSecondary,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 10.dp)
                        )
                    }
                } else {
                    Text(copy.trialLabel, color = accent.iconTint, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }

                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(plan.priceText, color = WaTextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    plan.originalPriceText?.let { original ->
                        Spacer(Modifier.width(6.dp))
                        Text(
                            original,
                            color = WaTextSecondary,
                            fontSize = 15.sp,
                            textDecoration = TextDecoration.LineThrough,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                }
                Text(
                    copy.periodSuffix,
                    color = WaTextSecondary,
                    fontSize = 13.sp
                )

                Spacer(Modifier.height(12.dp))
                planFeatures().forEach { feature ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 3.dp)
                    ) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = WaNeonGreen,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(feature, color = WaTextSecondary, fontSize = 12.sp)
                    }
                }

                // Flexible: absorbs whatever extra height this card was
                // stretched to (see the height-matching Row in
                // PaywallScreen), anchoring the CTA to the card's bottom
                // edge in every column instead of leaving a fixed gap.
                Spacer(Modifier.weight(1f).heightIn(min = 16.dp))

                val purchasingThis = isPurchasing && plan.rcPackage != null
                when (plan.plan) {
                    PlanType.MONTHLY -> GradientSelectButton(
                        text = stringResource(R.string.paywall_button_select),
                        brush = Brush.horizontalGradient(listOf(WaNeonGreen, Color(0xFF22D3EE))),
                        contentColor = Color(0xFF04140C),
                        showArrow = true,
                        isLoading = purchasingThis,
                        enabled = clickEnabled,
                        height = 52.dp,
                        onClick = { onSelect(plan) }
                    )
                    PlanType.YEARLY -> GradientSelectButton(
                        text = stringResource(R.string.paywall_button_select),
                        brush = Brush.horizontalGradient(listOf(WaPurple, WaPurpleLight)),
                        contentColor = Color.White,
                        showArrow = false,
                        isLoading = purchasingThis,
                        enabled = clickEnabled,
                        height = 48.dp,
                        onClick = { onSelect(plan) }
                    )
                    PlanType.WEEKLY -> OutlinePlainButton(
                        text = stringResource(R.string.paywall_button_select),
                        isLoading = purchasingThis,
                        enabled = clickEnabled,
                        onClick = { onSelect(plan) }
                    )
                }
            }
        }

        if (isFeatured) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .zIndex(1f)
                    .clip(RoundedCornerShape(50))
                    .background(Brush.horizontalGradient(listOf(WaNeonGreen, Color(0xFF22D3EE))))
                    .padding(horizontal = 20.dp, vertical = 9.dp)
            ) {
                Text(
                    stringResource(R.string.paywall_badge_popular),
                    color = Color(0xFF04140C),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Custom "calendar with a crown" glyph matching the approved design's plan
 * icon (a plain calendar icon reads as generic; the crown communicates
 * "premium access"). Drawn with [Canvas] rather than a bitmap asset so it
 * scales cleanly and re-tints per plan accent color.
 */
@Composable
private fun CalendarCrownIcon(tint: Color, size: androidx.compose.ui.unit.Dp) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = w * 0.09f

        // Calendar body.
        drawRoundRect(
            color = tint,
            topLeft = Offset(0f, h * 0.22f),
            size = Size(w, h * 0.74f),
            cornerRadius = CornerRadius(w * 0.16f, w * 0.16f),
            style = Stroke(width = stroke)
        )
        // Binder rings.
        val ringXs = listOf(w * 0.28f, w * 0.5f, w * 0.72f)
        ringXs.forEach { x ->
            drawLine(
                color = tint,
                start = Offset(x, 0f),
                end = Offset(x, h * 0.32f),
                strokeWidth = stroke,
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        }
        // Crown glyph inside the body.
        val crown = Path().apply {
            moveTo(w * 0.30f, h * 0.72f)
            lineTo(w * 0.34f, h * 0.50f)
            lineTo(w * 0.44f, h * 0.60f)
            lineTo(w * 0.50f, h * 0.46f)
            lineTo(w * 0.56f, h * 0.60f)
            lineTo(w * 0.66f, h * 0.50f)
            lineTo(w * 0.70f, h * 0.72f)
            close()
        }
        drawPath(crown, color = tint)
    }
}

/** Filled, gradient-background pill button used for the Yearly and Monthly "Select" CTAs. */
@Composable
private fun GradientSelectButton(
    text: String,
    brush: Brush,
    contentColor: Color,
    showArrow: Boolean,
    isLoading: Boolean,
    enabled: Boolean,
    height: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(50))
            .background(if (enabled) brush else Brush.horizontalGradient(listOf(WaSurfaceVariant, WaSurfaceVariant)))
            .clickable(enabled = enabled && !isLoading, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = contentColor)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text, color = contentColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                if (showArrow) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Filled.ArrowForward, contentDescription = null, tint = contentColor, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

/** Outlined "Select" button used for the Weekly plan. */
@Composable
private fun OutlinePlainButton(
    text: String,
    isLoading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(50))
            .border(BorderStroke(1.dp, WaSurfaceVariant), RoundedCornerShape(50))
            .clickable(enabled = enabled && !isLoading, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = WaTextPrimary)
        } else {
            Text(text, color = WaTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        }
    }
}

@Composable
private fun TrialNotice() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(WaSurface)
            .border(BorderStroke(1.dp, WaSurfaceVariant), RoundedCornerShape(18.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(50))
                .background(
                    Brush.radialGradient(listOf(WaNeonGreen.copy(alpha = 0.28f), Color.Transparent))
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Shield, contentDescription = null, tint = WaNeonGreen, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(
            stringResource(R.string.paywall_trial_notice),
            color = WaTextSecondary,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun TrustFooter() {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TrustItem(
                icon = Icons.Filled.Lock,
                title = stringResource(R.string.paywall_footer_secured_title),
                subtitle = stringResource(R.string.paywall_footer_secured_subtitle),
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(12.dp))
            TrustItem(
                icon = Icons.Filled.Storefront,
                title = stringResource(R.string.paywall_footer_google_title),
                subtitle = stringResource(R.string.paywall_footer_google_subtitle),
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            stringResource(R.string.paywall_footer_tagline),
            color = WaTextSecondary,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun TrustItem(icon: ImageVector, title: String, subtitle: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = WaTextSecondary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(title, color = WaTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = WaTextSecondary, fontSize = 10.sp, lineHeight = 12.sp)
        }
    }
}
