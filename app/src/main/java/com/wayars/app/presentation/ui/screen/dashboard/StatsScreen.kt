package com.wayars.app.presentation.ui.screen.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wayars.app.R
import com.wayars.app.domain.model.Currency
import com.wayars.app.domain.model.StatsPeriod
import com.wayars.app.domain.repository.OrderRecord
import com.wayars.app.presentation.ui.component.StatCard
import com.wayars.app.presentation.ui.component.bottomNavBarClearance
import com.wayars.app.presentation.ui.component.verdictColor
import com.wayars.app.presentation.ui.component.verdictLabel
import com.wayars.app.presentation.ui.theme.WaNeonGreen
import com.wayars.app.presentation.ui.theme.WaNeonGreenDark
import com.wayars.app.presentation.ui.theme.WaSurface
import com.wayars.app.presentation.ui.theme.WaSurfaceVariant
import com.wayars.app.presentation.ui.theme.WaTextSecondary
import com.wayars.app.util.CurrencyFormatter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Earnings statistics. [orders] are every accepted order ever recorded (newest first); the screen
 * filters them by [period] (today / 7 days / month / year), shows totals and a bar chart, and lists
 * the most recent orders of that period. The chosen period is persisted by the caller.
 */
@Composable
fun StatsScreen(
    orders: List<OrderRecord>,
    currency: Currency,
    period: StatsPeriod,
    onPeriodChange: (StatsPeriod) -> Unit,
    modifier: Modifier = Modifier
) {
    val stats = remember(orders, period) { buildPeriodStats(orders, period) }
    val timeFormat = remember(period) {
        SimpleDateFormat(if (period == StatsPeriod.TODAY) "HH:mm" else "dd.MM HH:mm", Locale.getDefault())
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        // See bottomNavBarClearance's doc: device-accurate clearance for
        // the floating nav pill, instead of a fixed guess.
        contentPadding = PaddingValues(bottom = bottomNavBarClearance())
    ) {
        item { PeriodSelector(period, onPeriodChange) }

        if (stats.orders.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.stats_empty),
                    color = WaTextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 48.dp)
                )
            }
        } else {
            item { SummaryBlock(stats, currency) }
            item { EarningsChart(stats, currency) }
            items(stats.orders.take(50)) { order ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(WaSurface)
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            CurrencyFormatter.format(order.evaluation.earnings, order.evaluation.currency),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "${order.evaluation.distanceKm} km • ${timeFormat.format(Date(order.timestampMillis))}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = WaTextSecondary
                        )
                    }
                    Text(
                        verdictLabel(order.evaluation.verdict),
                        color = verdictColor(order.evaluation.verdict),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

@Composable
private fun periodLabel(period: StatsPeriod): String = stringResource(
    when (period) {
        StatsPeriod.TODAY -> R.string.stats_period_today
        StatsPeriod.WEEK -> R.string.stats_period_week
        StatsPeriod.MONTH -> R.string.stats_period_month
        StatsPeriod.YEAR -> R.string.stats_period_year
    }
)

@Composable
private fun PeriodSelector(period: StatsPeriod, onChange: (StatsPeriod) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(WaSurface)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        StatsPeriod.values().forEach { p ->
            val selected = p == period
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected) WaNeonGreen else WaSurface)
                    .clickable { onChange(p) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    periodLabel(p),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    color = if (selected) Color.Black else WaTextSecondary
                )
            }
        }
    }
}

@Composable
private fun SummaryBlock(stats: PeriodStats, currency: Currency) {
    val total = stats.orders.sumOf { it.evaluation.earnings }
    val km = stats.orders.sumOf { it.evaluation.distanceKm }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(
                label = stringResource(R.string.stats_earned),
                value = CurrencyFormatter.format(total, currency),
                modifier = Modifier.weight(1f)
            )
            StatCard(
                label = stringResource(R.string.dashboard_orders),
                value = stats.orders.size.toString(),
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(
                label = stringResource(R.string.dashboard_distance),
                value = String.format(Locale.US, "%.1f km", km),
                modifier = Modifier.weight(1f)
            )
            StatCard(
                label = stringResource(R.string.dashboard_avg_rate),
                value = CurrencyFormatter.formatRatePerKm(if (km > 0) total / km else 0.0, currency),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** Bar chart of earnings per hour (today) / day (7 days, month) / month (year). Tap a bar to read its value. */
@Composable
private fun EarningsChart(stats: PeriodStats, currency: Currency) {
    var selected by remember(stats.period) { mutableStateOf(-1) }
    val max = stats.buckets.maxOrNull() ?: 0.0
    val bestIndex = stats.buckets.indexOf(max).coerceAtLeast(0)
    val shown = if (selected in stats.buckets.indices) selected else bestIndex
    val gap = if (stats.buckets.size > 14) 2.dp else 6.dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(WaSurface)
            .padding(14.dp)
    ) {
        Text(
            "${stats.captions[shown]}: ${CurrencyFormatter.format(stats.buckets[shown], currency)}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Row(
            modifier = Modifier.fillMaxWidth().height(150.dp).padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalAlignment = Alignment.Bottom
        ) {
            stats.buckets.forEachIndexed { i, value ->
                val fraction = if (max > 0) (value / max).toFloat() else 0f
                val barColor = when {
                    value <= 0.0 -> WaSurfaceVariant
                    i == shown -> WaNeonGreen
                    else -> WaNeonGreenDark.copy(alpha = 0.7f)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { selected = i },
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (value > 0.0) Modifier.fillMaxHeight(fraction.coerceIn(0.04f, 1f))
                                else Modifier.height(2.dp)
                            )
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(barColor)
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(gap)
        ) {
            stats.labels.forEach { label ->
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        label,
                        fontSize = 10.sp,
                        color = WaTextSecondary,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.wrapContentWidth(unbounded = true)
                    )
                }
            }
        }
    }
}

private class PeriodStats(
    val period: StatsPeriod,
    val orders: List<OrderRecord>,
    val buckets: List<Double>,
    val labels: List<String>,
    val captions: List<String>
)

private fun buildPeriodStats(all: List<OrderRecord>, period: StatsPeriod): PeriodStats {
    val start = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        when (period) {
            StatsPeriod.TODAY -> Unit
            StatsPeriod.WEEK -> add(Calendar.DAY_OF_YEAR, -6)
            StatsPeriod.MONTH -> add(Calendar.DAY_OF_YEAR, -29)
            StatsPeriod.YEAR -> {
                set(Calendar.DAY_OF_MONTH, 1)
                add(Calendar.MONTH, -11)
            }
        }
    }
    val count = when (period) {
        StatsPeriod.TODAY -> 24
        StatsPeriod.WEEK -> 7
        StatsPeriod.MONTH -> 30
        StatsPeriod.YEAR -> 12
    }
    val inPeriod = all.filter { it.timestampMillis >= start.timeInMillis }

    val sums = DoubleArray(count)
    val cal = Calendar.getInstance()
    for (order in inPeriod) {
        cal.timeInMillis = order.timestampMillis
        val index = when (period) {
            StatsPeriod.TODAY -> cal.get(Calendar.HOUR_OF_DAY)
            StatsPeriod.WEEK, StatsPeriod.MONTH -> {
                val dayStart = Calendar.getInstance().apply {
                    timeInMillis = order.timestampMillis
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                Math.round((dayStart - start.timeInMillis) / 86_400_000.0).toInt()
            }
            StatsPeriod.YEAR ->
                (cal.get(Calendar.YEAR) - start.get(Calendar.YEAR)) * 12 + cal.get(Calendar.MONTH) - start.get(Calendar.MONTH)
        }
        sums[index.coerceIn(0, count - 1)] += order.evaluation.earnings
    }

    val locale = Locale.getDefault()
    val dowShort = SimpleDateFormat("EEE", locale)
    val dowCaption = SimpleDateFormat("EEE, d MMM", locale)
    val dayNumber = SimpleDateFormat("d", locale)
    val dayCaption = SimpleDateFormat("d MMM", locale)
    val monthShort = SimpleDateFormat("LLL", locale)
    val monthCaption = SimpleDateFormat("LLLL yyyy", locale)

    val labels = ArrayList<String>(count)
    val captions = ArrayList<String>(count)
    val cursor = start.clone() as Calendar
    for (i in 0 until count) {
        val date = cursor.time
        when (period) {
            StatsPeriod.TODAY -> {
                labels.add(if (i % 6 == 0) i.toString() else "")
                captions.add(String.format(Locale.US, "%02d:00", i))
                cursor.add(Calendar.HOUR_OF_DAY, 1)
            }
            StatsPeriod.WEEK -> {
                labels.add(dowShort.format(date).take(3).replaceFirstChar { it.uppercase() })
                captions.add(dowCaption.format(date))
                cursor.add(Calendar.DAY_OF_YEAR, 1)
            }
            StatsPeriod.MONTH -> {
                labels.add(if (i % 5 == 0 || i == count - 1) dayNumber.format(date) else "")
                captions.add(dayCaption.format(date))
                cursor.add(Calendar.DAY_OF_YEAR, 1)
            }
            StatsPeriod.YEAR -> {
                labels.add(monthShort.format(date).take(3).replaceFirstChar { it.uppercase() })
                captions.add(monthCaption.format(date))
                cursor.add(Calendar.MONTH, 1)
            }
        }
    }
    return PeriodStats(period, inPeriod, sums.toList(), labels, captions)
}
