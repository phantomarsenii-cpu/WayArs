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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ShowChart
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.revenuecat.purchases.Package
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
 * Marketing copy (discount badges, trial length) lives here as static UI
 * content — RevenueCat's Offering/Package objects only carry real store
 * price + duration, not promotional copy. Prices themselves (below) always
 * come from [Package.product.price.formatted], never hardcoded, so what's
 * shown always matches what Google Play will actually charge.
 */
private data class PlanCopy(
    val title: String,
    val badge: String?,
    val badgeColor: Color,
    val periodSuffix: String
)

private val planCopy = mapOf(
    PlanType.YEARLY to PlanCopy("Годовая", "-30% • 7 дней бесплатно", WaPurple, "/ год"),
    PlanType.MONTHLY to PlanCopy("Месячная", "-15% • 7 дней бесплатно", WaNeonGreen, "/ месяц"),
    PlanType.WEEKLY to PlanCopy("Недельная", "7 дней бесплатно", WaTextSecondary, "/ неделя")
)

private val planFeatures = listOf(
    "Все функции приложения",
    "Умные пресеты",
    "Анализ заказов в реальном времени",
    "Поддержка 7 языков"
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
            "ВЫБЕРИТЕ СВОЙ ПЛАН",
            color = WaNeonGreen,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Гибкие тарифы для ваших целей",
            color = WaTextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Чем дольше подписка — тем выгоднее!",
            color = WaTextSecondary,
            fontSize = 14.sp
        )

        Spacer(Modifier.height(20.dp))

        when {
            state.isLoadingOfferings -> Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(color = WaNeonGreen) }

            else -> Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                state.plans.forEach { plan ->
                    PlanCard(
                        plan = plan,
                        isPurchasing = state.isPurchasing,
                        onSelect = { onSelectPlan(plan.rcPackage) }
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
                    Text("Скрыть", color = WaTextSecondary, fontSize = 12.sp)
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
            Text("Восстановить покупки", color = WaTextSecondary, fontSize = 14.sp)
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
            Text("Не удалось проверить подписку", color = WaTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(message, color = WaTextSecondary, fontSize = 12.sp)
        }
        Spacer(Modifier.width(8.dp))
        TextButton(onClick = onRetry) {
            Text("Повторить", color = WaNeonGreen, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
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
            Text("полный доступ", color = WaTextSecondary, fontSize = 12.sp)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            buildString { append("Откройте полный\nпотенциал WayArs") },
            color = WaTextPrimary,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 34.sp
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "WayArs — это не просто помощник, а ваш личный инструмент для стабильного и максимального дохода.",
            color = WaTextSecondary,
            fontSize = 14.sp,
            lineHeight = 20.sp
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            FeatureChip(Icons.Filled.Bolt, "Быстрый анализ")
            FeatureChip(Icons.Filled.Shield, "Умные фильтры")
            FeatureChip(Icons.Filled.ShowChart, "Максимальный доход")
            FeatureChip(Icons.Filled.Public, "7 языков")
        }
    }
}

@Composable
private fun FeatureChip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = WaNeonGreen, modifier = Modifier.size(18.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, color = WaTextSecondary, fontSize = 10.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun PlanCard(plan: PlanOption, isPurchasing: Boolean, onSelect: () -> Unit) {
    val copy = planCopy.getValue(plan.plan)
    val isFeatured = plan.plan == PlanType.MONTHLY
    val storeProduct = plan.rcPackage.product
    val priceFormatted = storeProduct.price.formatted

    val borderColor = if (isFeatured) WaNeonGreen else WaSurfaceVariant
    val backgroundBrush = if (isFeatured) {
        Brush.verticalGradient(listOf(WaSurface, WaNeonGreenDark.copy(alpha = 0.12f)))
    } else {
        Brush.verticalGradient(listOf(WaSurface, WaSurface))
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(backgroundBrush)
            .border(BorderStroke(1.dp, borderColor), RoundedCornerShape(20.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            if (isFeatured) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(WaNeonGreen)
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text("Популярный", color = Color(0xFF04140C), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(10.dp))
            }

            Text(copy.title, color = WaTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)

            copy.badge?.let { badge ->
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(copy.badgeColor.copy(alpha = 0.18f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(badge, color = copy.badgeColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(priceFormatted, color = WaTextPrimary, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(6.dp))
                Text(
                    copy.periodSuffix,
                    color = WaTextSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 5.dp)
                )
            }

            Spacer(Modifier.height(12.dp))
            planFeatures.forEach { feature ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 3.dp)
                ) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = WaNeonGreen,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(feature, color = WaTextSecondary, fontSize = 13.sp)
                }
            }

            Spacer(Modifier.height(16.dp))

            if (isFeatured) {
                Button(
                    onClick = onSelect,
                    enabled = !isPurchasing,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = WaNeonGreen, contentColor = Color(0xFF04140C))
                ) {
                    if (isPurchasing) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color(0xFF04140C))
                    } else {
                        Text("Выбрать", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            } else {
                OutlinedButton(
                    onClick = onSelect,
                    enabled = !isPurchasing,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, WaSurfaceVariant)
                ) {
                    Text("Выбрать", color = WaTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                }
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
            "Ваша подписка начнётся с 7-дневного пробного периода. Автоматическое продление выбранного плана. Вы можете отменить подписку в любой момент.",
            color = WaTextSecondary,
            fontSize = 12.sp,
            lineHeight = 17.sp
        )
    }
}
