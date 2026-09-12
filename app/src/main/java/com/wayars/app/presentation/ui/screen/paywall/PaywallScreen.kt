package com.wayars.app.presentation.ui.screen.paywall

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.revenuecat.purchases.Package
import com.wayars.app.R
import com.wayars.app.presentation.PaywallUiState
import com.wayars.app.presentation.PlanOption
import com.wayars.app.presentation.PlanType
import com.wayars.app.presentation.ui.theme.WaBackground
import com.wayars.app.presentation.ui.theme.WaNeonGreen
import com.wayars.app.presentation.ui.theme.WaNeonGreenDark
import com.wayars.app.presentation.ui.theme.WaPurple
import com.wayars.app.presentation.ui.theme.WaSurface
import com.wayars.app.presentation.ui.theme.WaSurfaceVariant
import com.wayars.app.presentation.ui.theme.WaTextPrimary
import com.wayars.app.presentation.ui.theme.WaTextSecondary

/**
 * Static per-plan copy (title, "days of access" subtitle, badges, period
 * suffix). This is marketing/layout content, not pricing — pricing always
 * comes from [PlanOption.priceText], which is either the live RevenueCat
 * [Package.product.price.formatted] or the hardcoded fallback in
 * [com.wayars.app.presentation.MockPlans] (see [SubscriptionViewModel]).
 * That keeps what's shown always consistent with what Google Play will
 * actually charge once real offerings load, while guaranteeing the layout
 * never has a missing/blank card.
 */
private data class PlanCopy(
    val title: String,
    val daysLabel: String,
    val badges: List<Pair<String, Color>>,
    val periodSuffix: String
)

@Composable
private fun planCopyFor(plan: PlanType): PlanCopy = when (plan) {
    PlanType.YEARLY -> PlanCopy(
        title = stringResource(R.string.paywall_plan_yearly_title),
        daysLabel = stringResource(R.string.paywall_plan_yearly_days),
        badges = listOf(
            "-30%" to WaPurple,
            stringResource(R.string.paywall_badge_trial7) to WaTextSecondary
        ),
        periodSuffix = stringResource(R.string.paywall_period_year)
    )
    PlanType.MONTHLY -> PlanCopy(
        title = stringResource(R.string.paywall_plan_monthly_title),
        daysLabel = stringResource(R.string.paywall_plan_monthly_days),
        badges = listOf(
            "-15%" to WaNeonGreen,
            stringResource(R.string.paywall_badge_trial7) to WaTextSecondary
        ),
        periodSuffix = stringResource(R.string.paywall_period_month)
    )
    PlanType.WEEKLY -> PlanCopy(
        title = stringResource(R.string.paywall_plan_weekly_title),
        daysLabel = stringResource(R.string.paywall_plan_weekly_days),
        badges = listOf(
            stringResource(R.string.paywall_badge_trial7) to WaTextSecondary
        ),
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
    onSelectPlan: (Package) -> Unit,
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
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        gateErrorMessage?.let { message ->
            GateErrorBanner(message = message, onRetry = onRetryGateCheck)
            Spacer(Modifier.height(16.dp))
        }

        Header()

        Spacer(Modifier.height(28.dp))

        Text(
            stringResource(R.string.paywall_section_eyebrow),
            color = WaNeonGreen,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.paywall_section_title),
            color = WaTextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 30.sp
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.paywall_section_subtitle),
            color = WaTextSecondary,
            fontSize = 14.sp
        )

        Spacer(Modifier.height(20.dp))

        // Two-column "bento" grid matching the design: Yearly + Weekly
        // stacked on the left, the featured Monthly card on the right.
        // Columns share width via weight(1f); each card sizes to its own
        // content rather than being forced to an exact matching height,
        // since translated copy varies in length across the 7 supported
        // languages and a hard height match would risk clipped text.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            val yearly = state.plans.find { it.plan == PlanType.YEARLY }
            val monthly = state.plans.find { it.plan == PlanType.MONTHLY }
            val weekly = state.plans.find { it.plan == PlanType.WEEKLY }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                yearly?.let {
                    PlanCard(
                        plan = it,
                        isPurchasing = state.isPurchasing,
                        onSelect = { pkg -> onSelectPlan(pkg) }
                    )
                }
                weekly?.let {
                    PlanCard(
                        plan = it,
                        isPurchasing = state.isPurchasing,
                        onSelect = { pkg -> onSelectPlan(pkg) }
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                monthly?.let {
                    PlanCard(
                        plan = it,
                        isPurchasing = state.isPurchasing,
                        onSelect = { pkg -> onSelectPlan(pkg) },
                        modifier = Modifier.fillMaxWidth()
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

@Composable
private fun Header() {
    Column(horizontalAlignment = Alignment.Start) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(WaSurfaceVariant)
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Text(stringResource(R.string.paywall_badge_full_access), color = WaTextSecondary, fontSize = 12.sp)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.paywall_title),
            color = WaTextPrimary,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 34.sp
        )
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.paywall_subtitle),
            color = WaTextSecondary,
            fontSize = 14.sp,
            lineHeight = 20.sp
        )
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            FeatureChip(Icons.Filled.Bolt, stringResource(R.string.paywall_chip_analysis))
            FeatureChip(Icons.Filled.Shield, stringResource(R.string.paywall_chip_filters))
            FeatureChip(Icons.Filled.ShowChart, stringResource(R.string.paywall_chip_income))
            FeatureChip(Icons.Filled.Public, stringResource(R.string.paywall_chip_languages))
        }
    }
}

@Composable
private fun FeatureChip(icon: ImageVector, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(70.dp)
    ) {
        Icon(icon, contentDescription = null, tint = WaNeonGreen, modifier = Modifier.size(18.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, color = WaTextSecondary, fontSize = 10.sp, lineHeight = 12.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun PlanCard(
    plan: PlanOption,
    isPurchasing: Boolean,
    onSelect: (Package) -> Unit,
    modifier: Modifier = Modifier
) {
    val copy = planCopyFor(plan.plan)
    val isFeatured = plan.plan == PlanType.MONTHLY

    val borderColor = if (isFeatured) WaNeonGreen else WaSurfaceVariant
    val backgroundBrush = if (isFeatured) {
        Brush.verticalGradient(listOf(WaSurface, WaNeonGreenDark.copy(alpha = 0.12f)))
    } else {
        Brush.verticalGradient(listOf(WaSurface, WaSurface))
    }

    Box(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (isFeatured) Modifier.padding(top = 14.dp) else Modifier)
                .clip(RoundedCornerShape(20.dp))
                .background(backgroundBrush)
                .border(BorderStroke(1.dp, borderColor), RoundedCornerShape(20.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                Icon(
                    Icons.Filled.CalendarMonth,
                    contentDescription = null,
                    tint = if (isFeatured) WaNeonGreen else WaTextSecondary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text(copy.title, color = WaTextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text(copy.daysLabel, color = WaTextSecondary, fontSize = 11.sp)

                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    copy.badges.forEach { (label, color) ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(color.copy(alpha = 0.18f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(label, color = color, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
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

                Spacer(Modifier.height(16.dp))

                val enabled = !isPurchasing && plan.isLive
                if (isFeatured) {
                    Button(
                        onClick = { plan.rcPackage?.let(onSelect) },
                        enabled = enabled,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = WaNeonGreen,
                            contentColor = Color(0xFF04140C),
                            disabledContainerColor = WaNeonGreen.copy(alpha = if (isPurchasing) 1f else 0.5f)
                        )
                    ) {
                        if (isPurchasing) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color(0xFF04140C))
                        } else {
                            Text(stringResource(R.string.paywall_button_select), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = { plan.rcPackage?.let(onSelect) },
                        enabled = enabled,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, WaSurfaceVariant)
                    ) {
                        Text(stringResource(R.string.paywall_button_select), color = WaTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    }
                }
            }
        }

        if (isFeatured) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .zIndex(1f)
                    .clip(RoundedCornerShape(50))
                    .background(WaNeonGreen)
                    .padding(horizontal = 14.dp, vertical = 5.dp)
            ) {
                Text(
                    stringResource(R.string.paywall_badge_popular),
                    color = Color(0xFF04140C),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun TrialNotice() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(WaSurface)
            .padding(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(Icons.Filled.Shield, contentDescription = null, tint = WaNeonGreen, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            stringResource(R.string.paywall_trial_notice),
            color = WaTextSecondary,
            fontSize = 12.sp,
            lineHeight = 17.sp
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
