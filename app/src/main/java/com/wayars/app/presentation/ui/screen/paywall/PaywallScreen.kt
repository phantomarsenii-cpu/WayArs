package com.wayars.app.presentation.ui.screen.paywall

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.ParentDataModifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.wayars.app.R
import com.wayars.app.presentation.PaywallUiState
import com.wayars.app.presentation.PlanOption
import com.wayars.app.presentation.PlanType
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
 * Pixel geometry measured directly off the approved reference screenshot
 * (native size 704×1510 px; the brief calls it 716×1536 — same 2x-density
 * frame, off by rounding from re-export/compression) via a grid-overlay
 * pixel analysis: the reference was cropped in 20px bands, a ruled grid
 * was drawn over each band, and every border/badge edge below was read
 * off that grid rather than guessed. Values are NOT proportions or
 * weights — they are the actual measured spans, kept in the same
 * reference-pixel unit so the ratios between them are exactly the
 * reference's ratios (e.g. Monthly is 372px wide vs. 240px for
 * Yearly/Weekly — 1.55x, not the 1x a `weight(1f)`/`weight(1f)` split
 * would produce).
 *
 * All X values below are LOCAL to the content column, i.e. already
 * offset by the reference's 32px left margin (so X=0 means "at the
 * screen's content edge", matching this screen's own 18dp padding).
 * All Y values in CARDS_* are local to the pricing-cards block, with
 * Y=0 = the Yearly card's top border (reference Y=535) — the topmost
 * edge in that block (the "Популярный" badge, reference Y=555, sits at
 * local Y=20 — i.e. it overhangs the Monthly card's OWN top border by
 * 10px, not the block's top).
 *
 * [scaleFor] turns these into dp for the device at hand: it divides the
 * width Compose actually measured for the content column by this
 * object's reference content width, and every element is scaled by that
 * single factor on both axes — true proportional scaling to the
 * reference (not a fixed dp copy, and not a weight-based guess).
 */
private object Ref {
    /** Reference content width: 672 - 32 = 640px (the span both card columns fill together). */
    const val CONTENT_W = 640f

    // --- Header: text block vs. hero illustration, same row, split at ref X=330 ---
    const val HEADER_TEXT_W = 298f   // 32..330
    const val HEADER_HERO_X = 298f   // local X where the hero block starts
    const val HEADER_HERO_W = 342f   // 330..672(screen right edge)
    // Hero bounding box measured within its own 342-wide slot (local to that slot):
    // the phone illustration + its glow sits centered, floating tags orbit it.
    const val HERO_H = 275f          // ref Y 25..300 (title top-align to divider)
    // Phone frame and floating-tag anchor points, LOCAL to the hero slot
    // (i.e. already offset by ref X=330, ref Y=25 — the slot's own
    // origin). Read off the same grid-overlay analysis as the cards.
    // Rotation angles are the tags' visible tilt in the reference.
    const val PHONE_X = 165f;       const val PHONE_Y = 15f
    const val PHONE_W = 100f;       const val PHONE_H = 210f
    const val GLOW_W = 130f;        const val GLOW_H = 130f
    const val TAG_BOLT_X = 90f;     const val TAG_BOLT_Y = 55f;  const val TAG_BOLT_ROT = -7f
    const val TAG_BOLT_W = 95f;     const val TAG_BOLT_H = 55f
    const val TAG_UBER_X = 0f;      const val TAG_UBER_Y = 115f; const val TAG_UBER_ROT = -3f
    const val TAG_UBER_W = 100f;    const val TAG_UBER_H = 55f
    const val TAG_WOLT_X = 250f;    const val TAG_WOLT_Y = 55f;  const val TAG_WOLT_ROT = 6f
    const val TAG_WOLT_W = 90f;     const val TAG_WOLT_H = 55f
    const val TAG_STUART_X = 255f;  const val TAG_STUART_Y = 165f; const val TAG_STUART_ROT = 0f
    const val TAG_STUART_W = 90f;   const val TAG_STUART_H = 70f
    const val TAG_FREENOW_X = 255f; const val TAG_FREENOW_Y = 195f; const val TAG_FREENOW_ROT = -4f
    const val TAG_FREENOW_W = 90f;  const val TAG_FREENOW_H = 70f

    // --- Pricing cards block (local origin: X=0 at ref X=32, Y=0 at ref Y=535) ---
    const val YEARLY_X = 0f;    const val YEARLY_Y = 0f
    const val YEARLY_W = 240f;  const val YEARLY_H = 363f   // ref 535..898
    const val WEEKLY_X = 0f;    const val WEEKLY_Y = 385f   // ref 920..1250 -> local 385..715
    const val WEEKLY_W = 240f;  const val WEEKLY_H = 330f
    const val MONTHLY_X = 268f; const val MONTHLY_Y = 30f   // ref 565..1218 -> local 30..683
    const val MONTHLY_W = 372f; const val MONTHLY_H = 653f
    const val BADGE_W = 150f;   const val BADGE_H = 45f
    const val BADGE_Y = 20f     // ref 555 -> local 20 (overhangs Monthly's own top by 10px)
    /** Bottom-most edge across both columns: max(Weekly bottom 715, Monthly bottom 683). */
    const val CARDS_BLOCK_H = 715f
}

/**
 * Internal geometry of one plan card, local to that card's own top-left
 * (i.e. its own [RefBox] fed straight into a per-card [RefCanvas] whose
 * refW/refH are the card's own measured width/height from [Ref]). Read
 * off the same grid-overlay analysis as the outer card boxes — these
 * inner element positions carry more manual-reading error than the
 * outer boxes (small text baselines are harder to pin to a pixel than a
 * card border is), so treat them as "measured, moderate confidence"
 * rather than exact.
 */
private data class CardGeom(
    val cardW: Float,
    val cardH: Float,
    val icon: RefBox,
    val title: RefBox,
    val subtitle: RefBox,
    val pill: RefBox?,       // discount pill background; null for Weekly (no discount)
    val trial: RefBox,       // "7 дней бесплатно" text (sits beside the pill, or alone for Weekly)
    val price: RefBox,
    val original: RefBox?,   // struck-through original price; Monthly only
    val period: RefBox,
    val features: List<RefBox>,
    val button: RefBox
)

private val YearlyGeom = CardGeom(
    cardW = Ref.YEARLY_W, cardH = Ref.YEARLY_H,
    icon = RefBox(33f, 20f, 44f, 45f),
    title = RefBox(86f, 20f, 150f, 24f),
    subtitle = RefBox(86f, 48f, 150f, 16f),
    pill = RefBox(33f, 73f, 110f, 40f),
    trial = RefBox(150f, 73f, 90f, 40f),
    price = RefBox(33f, 122f, 140f, 42f),
    original = null,
    period = RefBox(178f, 150f, 60f, 20f),
    features = listOf(
        RefBox(33f, 190f, 175f, 20f),
        RefBox(33f, 215f, 175f, 20f),
        RefBox(33f, 240f, 175f, 40f),
        RefBox(33f, 288f, 175f, 20f)
    ),
    button = RefBox(33f, 323f, 174f, 37f)
)

private val WeeklyGeom = CardGeom(
    cardW = Ref.WEEKLY_W, cardH = Ref.WEEKLY_H,
    icon = RefBox(33f, 20f, 44f, 45f),
    title = RefBox(86f, 20f, 150f, 24f),
    subtitle = RefBox(86f, 48f, 150f, 16f),
    pill = null,
    trial = RefBox(33f, 80f, 180f, 20f),
    price = RefBox(33f, 103f, 140f, 42f),
    original = null,
    period = RefBox(168f, 130f, 70f, 20f),
    features = listOf(
        RefBox(33f, 170f, 175f, 20f),
        RefBox(33f, 195f, 175f, 20f),
        RefBox(33f, 220f, 175f, 40f),
        RefBox(33f, 268f, 175f, 20f)
    ),
    button = RefBox(33f, 288f, 174f, 35f)
)

private val MonthlyGeom = CardGeom(
    cardW = Ref.MONTHLY_W, cardH = Ref.MONTHLY_H,
    icon = RefBox(55f, 45f, 55f, 55f),
    title = RefBox(125f, 45f, 200f, 30f),
    subtitle = RefBox(125f, 82f, 220f, 20f),
    pill = RefBox(55f, 140f, 135f, 55f),
    trial = RefBox(210f, 140f, 140f, 55f),
    price = RefBox(55f, 225f, 190f, 55f),
    original = RefBox(255f, 245f, 100f, 30f),
    period = RefBox(55f, 285f, 100f, 25f),
    features = listOf(
        RefBox(55f, 345f, 300f, 25f),
        RefBox(55f, 385f, 300f, 25f),
        RefBox(55f, 425f, 300f, 50f),
        RefBox(55f, 485f, 300f, 25f)
    ),
    button = RefBox(55f, 555f, 262f, 75f)
)

private fun geomFor(plan: PlanType): CardGeom = when (plan) {
    PlanType.YEARLY -> YearlyGeom
    PlanType.WEEKLY -> WeeklyGeom
    PlanType.MONTHLY -> MonthlyGeom
}

/** Scale factor: actual measured content width / [Ref.CONTENT_W]. Multiply any Ref.* value by this, then `.dp`, to place it. */
private fun scaleFor(actualContentWidthDp: Float): Float = actualContentWidthDp / Ref.CONTENT_W

/**
 * Reference-pixel coordinates for one child of [RefCanvas]: x, y, width,
 * height, all in the SAME unit as the [Ref] constants (reference-image
 * pixels), local to that canvas's own top-left.
 */
private data class RefBox(val x: Float, val y: Float, val w: Float, val h: Float)

private class RefBoundsElement(val box: RefBox) : ParentDataModifier {
    override fun Density.modifyParentData(parentData: Any?): Any = box
}

/** Attaches this child's reference-pixel box to it for [RefCanvas] to read at layout time. */
private fun Modifier.refBounds(x: Float, y: Float, w: Float, h: Float): Modifier =
    this.then(RefBoundsElement(RefBox(x, y, w, h)))

/**
 * A single coordinate canvas, addressing the "не Row/Column для позиционирования"
 * requirement directly: every child is placed by [Modifier.refBounds] alone —
 * no `Row`, no `Column`, no `weight()`, no `fillMaxHeight()` decides where
 * anything goes.
 *
 * This is Compose's own [Layout] primitive, not [Modifier.offset]/[Modifier.size]
 * with a pre-computed dp value: [Constraints.maxWidth] here is the ACTUAL
 * measured width of the container in real device pixels (post-density,
 * i.e. what the compositor will rasterize), and every child's target
 * rectangle is computed from that in the SAME pixel space — `refW`/`refH`
 * (the reference image's own pixel dimensions for this canvas) map onto
 * `constraints.maxWidth`/an implied height by one multiply, then each
 * child's [RefBox] (also in reference pixels) is scaled by that same
 * factor and handed to `placeable.place(xPx, yPx)` in that same real-pixel
 * space. There is no dp anywhere in this function — dp only exists where
 * [Ref] constants get authored as literals, and it never mixes with the
 * px math done here.
 */
@Composable
private fun RefCanvas(
    refW: Float,
    refH: Float,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val scale = constraints.maxWidth / refW
        val heightPx = (refH * scale).toInt().coerceAtLeast(0)
        val placed = measurables.map { m ->
            val box = (m.parentData as? RefBox) ?: RefBox(0f, 0f, refW, refH)
            val wPx = (box.w * scale).toInt().coerceAtLeast(0)
            val hPx = (box.h * scale).toInt().coerceAtLeast(0)
            val xPx = (box.x * scale).toInt()
            val yPx = (box.y * scale).toInt()
            Triple(m.measure(Constraints.fixed(wPx, hPx)), xPx, yPx)
        }
        layout(constraints.maxWidth, heightPx) {
            placed.forEach { (placeable, x, y) -> placeable.place(x, y) }
        }
    }
}

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
            // Measured directly off the reference at ~30 sample points away
            // from the hero/card glow zones: it is a NEAR-FLAT dark
            // teal-navy (~RGB 7,20,29 / #071420), not the near-black
            // #040608 this screen used before, and not a strong gradient
            // or decorative background layer either — direct pixel
            // sampling found none. The visual "background interest" in the
            // reference comes entirely from the hero glow and each card's
            // own border/shadow glow, both of which already exist as
            // separate layers in this screen.
            .background(Brush.verticalGradient(listOf(Color(0xFF081826), Color(0xFF071420), Color(0xFF061119))))
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

        PricingCardsBlock(
            state = state,
            onSelectPlan = onSelectPlan
        )

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

/**
 * The pricing-cards bento grid, laid out by absolute coordinate — via
 * [RefCanvas], not `Row`/`Column` weights — using the measurements in
 * [Ref]. [RefCanvas] measures the actual pixel width Compose gives the
 * content column and scales every [Ref] value by that one factor on both
 * axes, so the block reproduces the reference's exact card proportions
 * (Monthly 372:240 wide vs. Yearly/Weekly, not a 1:1 split) at whatever
 * size the device gives it. The "Популярный" badge is drawn here, as its
 * own absolutely-positioned sibling overhanging the Monthly card's top
 * edge — not inside [PlanCard] — because that's what the reference
 * actually is: one badge floating above the border line, not padding
 * baked into the card.
 */
@Composable
private fun PricingCardsBlock(
    state: PaywallUiState,
    onSelectPlan: (PlanOption) -> Unit
) {
    val yearly = state.plans.find { it.plan == PlanType.YEARLY }
    val weekly = state.plans.find { it.plan == PlanType.WEEKLY }
    val monthly = state.plans.find { it.plan == PlanType.MONTHLY }

    RefCanvas(
        refW = Ref.CONTENT_W,
        refH = Ref.CARDS_BLOCK_H,
        modifier = Modifier.fillMaxWidth()
    ) {
        yearly?.let {
            PlanCard(
                plan = it,
                isPurchasing = state.isPurchasing,
                isMockSelected = state.selectedMockPlan == it.plan,
                onSelect = onSelectPlan,
                modifier = Modifier.refBounds(Ref.YEARLY_X, Ref.YEARLY_Y, Ref.YEARLY_W, Ref.YEARLY_H)
            )
        }
        weekly?.let {
            PlanCard(
                plan = it,
                isPurchasing = state.isPurchasing,
                isMockSelected = state.selectedMockPlan == it.plan,
                onSelect = onSelectPlan,
                modifier = Modifier.refBounds(Ref.WEEKLY_X, Ref.WEEKLY_Y, Ref.WEEKLY_W, Ref.WEEKLY_H)
            )
        }
        monthly?.let {
            PlanCard(
                plan = it,
                isPurchasing = state.isPurchasing,
                isMockSelected = state.selectedMockPlan == it.plan,
                onSelect = onSelectPlan,
                modifier = Modifier.refBounds(Ref.MONTHLY_X, Ref.MONTHLY_Y, Ref.MONTHLY_W, Ref.MONTHLY_H)
            )
        }

        // "Популярный" badge — sibling of the cards, not nested inside
        // PlanCard, positioned by its own measured coordinates so it
        // overhangs the Monthly card's top border by exactly the
        // measured 10px, instead of being simulated with card-content
        // padding. zIndex keeps it above the Monthly card it overlaps.
        Box(
            modifier = Modifier
                .refBounds(
                    Ref.MONTHLY_X + Ref.MONTHLY_W / 2f - Ref.BADGE_W / 2f,
                    Ref.BADGE_Y,
                    Ref.BADGE_W,
                    Ref.BADGE_H
                )
                .zIndex(2f)
                .clip(RoundedCornerShape(50))
                .background(Brush.horizontalGradient(listOf(WaNeonGreen, Color(0xFF22D3EE)))),
            contentAlignment = Alignment.Center
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

        // Text block vs. hero illustration, split by the measured reference
        // widths (298px : 342px) — NOT `Modifier.weight(1f)` on both sides,
        // which would force an inaccurate 1:1 split. This one spot keeps
        // `BoxWithConstraints` + a dp scale rather than [RefCanvas]
        // deliberately: [RefCanvas] forces each child to an exact
        // reference-derived height via `Constraints.fixed`, which is right
        // for the hero graphic (fixed decorative content) but would clip
        // the title/subtitle text block in any language whose translation
        // runs longer than the reference's Russian copy. The width split
        // itself is still the measured ratio, not a guess.
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val scale = scaleFor(maxWidth.value)
            fun px(v: Float) = (v * scale).dp

            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.width(px(Ref.HEADER_TEXT_W))) {
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
                HeroIllustration(
                    modifier = Modifier
                        .width(px(Ref.HEADER_HERO_W))
                        .height(px(Ref.HERO_H))
                )
            }
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
 * Stylized "phone + partner platforms" hero graphic. Every element here —
 * the phone frame and each floating tag — is placed by absolute offset
 * from [Ref]'s measured coordinates (via [px]), not by `Alignment` plus a
 * guessed `.offset()`/`.rotate()` like the previous version.
 *
 * The platform tags are plain colored text labels — deliberately NOT
 * pixel copies of each brand's actual logo/wordmark artwork. Uber, Bolt,
 * Wolt, Stuart and FreeNow are real, currently-trademarked brands; baking
 * a bitmap of their stylized app-icon art into a shipping app risks
 * implying an official partnership that may not exist, independent of
 * how faithfully it's redrawn. Naming a compatible service in your own
 * UI as plain text is standard, safe practice — reproducing their logo
 * art is not something I'll do without that partnership/license
 * actually being confirmed. Position, size, and rotation of each tag
 * are matched to the reference; the glyph inside is not.
 */
@Composable
private fun HeroIllustration(modifier: Modifier = Modifier) {
    RefCanvas(refW = Ref.HEADER_HERO_W, refH = Ref.HERO_H, modifier = modifier) {
        // Ambient glow, centered on the measured phone bounding box.
        Box(
            modifier = Modifier
                .refBounds(
                    Ref.PHONE_X + Ref.PHONE_W / 2f - Ref.GLOW_W / 2f,
                    Ref.PHONE_Y + Ref.PHONE_H / 2f - Ref.GLOW_H / 2f,
                    Ref.GLOW_W,
                    Ref.GLOW_H
                )
                .background(
                    Brush.radialGradient(
                        listOf(WaNeonGreen.copy(alpha = 0.28f), Color.Transparent)
                    )
                )
        )

        // Phone frame — placed at its own measured top-left, not centered
        // in the whole slot (the reference's phone sits right-of-center).
        Box(
            modifier = Modifier
                .refBounds(Ref.PHONE_X, Ref.PHONE_Y, Ref.PHONE_W, Ref.PHONE_H)
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
                .refBounds(Ref.TAG_BOLT_X, Ref.TAG_BOLT_Y, Ref.TAG_BOLT_W, Ref.TAG_BOLT_H)
                .rotate(Ref.TAG_BOLT_ROT)
        )
        PlatformTag(
            "Uber",
            Brush.horizontalGradient(listOf(WaTagUber, WaTagUber)),
            WaTextPrimary,
            modifier = Modifier
                .refBounds(Ref.TAG_UBER_X, Ref.TAG_UBER_Y, Ref.TAG_UBER_W, Ref.TAG_UBER_H)
                .rotate(Ref.TAG_UBER_ROT)
        )
        PlatformTag(
            "Wolt",
            Brush.horizontalGradient(listOf(WaTagWoltDark, WaTagWolt)),
            WaTextPrimary,
            modifier = Modifier
                .refBounds(Ref.TAG_WOLT_X, Ref.TAG_WOLT_Y, Ref.TAG_WOLT_W, Ref.TAG_WOLT_H)
                .rotate(Ref.TAG_WOLT_ROT)
        )
        PlatformTag(
            "Stuart",
            Brush.horizontalGradient(listOf(WaTagStuart, WaTagStuart)),
            WaTextPrimary,
            icon = Icons.Filled.LocalShipping,
            modifier = Modifier
                .refBounds(Ref.TAG_STUART_X, Ref.TAG_STUART_Y, Ref.TAG_STUART_W, Ref.TAG_STUART_H)
                .rotate(Ref.TAG_STUART_ROT)
        )
        PlatformTag(
            "Free Now",
            Brush.horizontalGradient(listOf(WaTagFreeNow, WaTagFreeNow)),
            WaTextPrimary,
            modifier = Modifier
                .refBounds(Ref.TAG_FREENOW_X, Ref.TAG_FREENOW_Y, Ref.TAG_FREENOW_W, Ref.TAG_FREENOW_H)
                .rotate(Ref.TAG_FREENOW_ROT)
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

    // Sizing and position come entirely from [modifier] — the caller
    // (PricingCardsBlock) places every card by the exact reference
    // coordinates in [Ref]. This composable no longer guesses a size for
    // itself, and — for the Monthly card — no longer reserves its own
    // top padding for the "Популярный" badge: that badge is drawn as an
    // independent sibling at its own measured coordinates, since in the
    // reference it overhangs the card's border, it isn't inset from it.
    Box(
        modifier = modifier
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
        val geom = geomFor(plan.plan)
        RefCanvas(refW = geom.cardW, refH = geom.cardH, modifier = Modifier.fillMaxSize()) {
            Box(Modifier.refBounds(geom.icon.x, geom.icon.y, geom.icon.w, geom.icon.h)) {
                CalendarCrownIcon(tint = accent.iconTint, size = 24.dp)
            }
            Text(
                copy.title,
                color = WaTextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.refBounds(geom.title.x, geom.title.y, geom.title.w, geom.title.h)
            )
            Text(
                copy.daysLabel,
                color = WaTextSecondary,
                fontSize = 11.sp,
                modifier = Modifier.refBounds(geom.subtitle.x, geom.subtitle.y, geom.subtitle.w, geom.subtitle.h)
            )

            if (copy.discountLabel != null && geom.pill != null) {
                Box(
                    modifier = Modifier
                        .refBounds(geom.pill.x, geom.pill.y, geom.pill.w, geom.pill.h)
                        .clip(RoundedCornerShape(50))
                        .background(
                            Brush.horizontalGradient(listOf(copy.discountColor, copy.discountColor.copy(alpha = 0.65f)))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(copy.discountLabel, color = Color(0xFF04140C), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Box(
                    modifier = Modifier.refBounds(geom.trial.x, geom.trial.y, geom.trial.w, geom.trial.h),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(copy.trialLabel, color = WaTextSecondary, fontSize = 11.sp)
                }
            } else {
                Box(
                    modifier = Modifier.refBounds(geom.trial.x, geom.trial.y, geom.trial.w, geom.trial.h),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(copy.trialLabel, color = accent.iconTint, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }

            Box(
                modifier = Modifier.refBounds(geom.price.x, geom.price.y, geom.price.w, geom.price.h),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(plan.priceText, color = WaTextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            }
            if (geom.original != null) {
                plan.originalPriceText?.let { original ->
                    Box(
                        modifier = Modifier.refBounds(geom.original.x, geom.original.y, geom.original.w, geom.original.h),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            original,
                            color = WaTextSecondary,
                            fontSize = 15.sp,
                            textDecoration = TextDecoration.LineThrough
                        )
                    }
                }
            }
            Text(
                copy.periodSuffix,
                color = WaTextSecondary,
                fontSize = 13.sp,
                modifier = Modifier.refBounds(geom.period.x, geom.period.y, geom.period.w, geom.period.h)
            )

            val features = planFeatures()
            geom.features.forEachIndexed { index, box ->
                features.getOrNull(index)?.let { feature ->
                    Row(
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier.refBounds(box.x, box.y, box.w, box.h)
                    ) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = WaNeonGreen,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(feature, color = WaTextSecondary, fontSize = 12.sp, lineHeight = 15.sp)
                    }
                }
            }

            val purchasingThis = isPurchasing && plan.rcPackage != null
            Box(Modifier.refBounds(geom.button.x, geom.button.y, geom.button.w, geom.button.h)) {
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
