package com.wayars.app.presentation.ui.screen.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wayars.app.R
import com.wayars.app.domain.model.Currency
import com.wayars.app.features.Achievements
import com.wayars.app.features.AppNames
import com.wayars.app.features.DayPart
import com.wayars.app.features.ServiceRate
import com.wayars.app.features.ShiftEarnings
import com.wayars.app.features.SlotRate
import com.wayars.app.presentation.ui.theme.WaNeonGreen
import com.wayars.app.presentation.ui.theme.WaSurface
import com.wayars.app.presentation.ui.theme.WaSurfaceVariant
import com.wayars.app.presentation.ui.theme.WaTextSecondary
import com.wayars.app.util.CurrencyFormatter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(WaSurface)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) { content() }
}

@Composable
private fun perHour(value: Double, currency: Currency) =
    stringResource(R.string.summary_part_hour, CurrencyFormatter.format(value, currency, 0))

@Composable
fun formatDuration(millis: Long): String {
    val minutes = millis / 60_000L
    return stringResource(R.string.stats_duration, (minutes / 60).toInt(), (minutes % 60).toInt())
}

/** "Real per hour": take-home over the whole time "Active" was on, waiting between orders included. */
@Composable
fun RealHourlyCard(e: ShiftEarnings, currency: Currency) = Card {
    Text(stringResource(R.string.stats_real_hour_title), color = WaTextSecondary, fontSize = 13.sp)
    Text(
        e.perHour?.let { perHour(it, currency) } ?: "—",
        color = WaNeonGreen, fontSize = 30.sp, fontWeight = FontWeight.Bold
    )
    Text(
        stringResource(R.string.stats_real_hour_detail, CurrencyFormatter.format(e.net, currency), formatDuration(e.activeMillis)),
        color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp
    )
    // Partner (fleet) costs: informational, only when the driver entered them in Settings.
    if (e.partnerCommission > 0.0 || e.partnerFee > 0.0) {
        val pct = com.wayars.app.data.prefs.DriverPrefs.commissionPercent.collectAsState().value
        if (e.partnerCommission > 0.0) {
            Text(
                stringResource(R.string.stats_partner_commission_line, formatPercent(pct), CurrencyFormatter.format(e.partnerCommission, currency)),
                color = WaTextSecondary, fontSize = 12.sp
            )
        }
        if (e.partnerFee > 0.0) {
            Text(
                stringResource(R.string.stats_partner_fee_line, CurrencyFormatter.format(e.partnerFee, currency)),
                color = WaTextSecondary, fontSize = 12.sp
            )
        }
        Text(
            stringResource(R.string.stats_after_partner, CurrencyFormatter.format(e.afterPartner, currency)),
            color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
        )
    }
    Text(stringResource(R.string.stats_real_hour_hint), color = WaTextSecondary, fontSize = 12.sp, lineHeight = 16.sp)
}

@Composable
private fun slotName(dayOfWeek: Int, part: DayPart): String {
    val day = remember(dayOfWeek) {
        val cal = Calendar.getInstance().apply { set(Calendar.DAY_OF_WEEK, dayOfWeek) }
        SimpleDateFormat("EEEE", Locale.getDefault()).format(cal.time)
    }
    val partName = stringResource(
        when (part) {
            DayPart.MORNING -> R.string.daypart_morning
            DayPart.DAY -> R.string.daypart_day
            DayPart.EVENING -> R.string.daypart_evening
            DayPart.NIGHT -> R.string.daypart_night
        }
    )
    return stringResource(R.string.stats_slot, day, partName)
}

/** Which app pays more per real hour, and when (last 60 days). */
@Composable
fun ServicesCard(services: List<ServiceRate>, comparisons: List<List<SlotRate>>, best: List<SlotRate>, currency: Currency) = Card {
    val context = LocalContext.current
    Text(stringResource(R.string.stats_services_title), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium)
    if (services.isEmpty()) {
        Text(stringResource(R.string.stats_services_empty), color = WaTextSecondary, fontSize = 13.sp, lineHeight = 18.sp)
        return@Card
    }
    services.forEachIndexed { i, s ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                AppNames.label(context, s.sourcePackage),
                color = if (i == 0 && services.size > 1) WaNeonGreen else MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.weight(1f)
            )
            Text(perHour(s.perHour, currency), color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(
                "  " + stringResource(R.string.summary_part_orders, s.orders),
                color = WaTextSecondary, fontSize = 12.sp
            )
        }
    }
    comparisons.take(3).forEach { slot ->
        val first = slot.first()
        val line = slot.joinToString(" · ") {
            "${AppNames.label(context, it.sourcePackage)} " +
                context.getString(R.string.summary_part_hour, CurrencyFormatter.format(it.perHour, currency, 0))
        }
        Text(
            "${slotName(first.dayOfWeek, first.dayPart)}: $line",
            color = WaTextSecondary, fontSize = 13.sp, lineHeight = 18.sp
        )
    }
    best.firstOrNull()?.let { b ->
        Text(
            stringResource(R.string.stats_best_time, AppNames.label(context, b.sourcePackage), slotName(b.dayOfWeek, b.dayPart), perHour(b.perHour, currency)),
            color = WaNeonGreen, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
        )
    }
}

private class Badge(val icon: String, val label: Int, val progress: Double, val target: Double)

/** Records, the "smart days" streak and badges. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AchievementsCard(a: Achievements, currency: Currency) = Card {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.stats_achievements_title),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f)
        )
        Text(
            "📤 " + stringResource(R.string.share_button),
            color = WaNeonGreen, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable { scope.launch { com.wayars.app.features.ShareCard.share(context) } }
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
    Text(
        stringResource(R.string.ach_streak, a.currentSmartStreak, a.bestSmartStreak),
        color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, lineHeight = 18.sp
    )
    a.recordPerHour?.let { Text(stringResource(R.string.ach_record_hour, perHour(it, currency)), color = WaTextSecondary, fontSize = 13.sp) }
    a.bestDay?.let { Text(stringResource(R.string.ach_best_day, CurrencyFormatter.format(it, currency)), color = WaTextSecondary, fontSize = 13.sp) }
    a.bestOrder?.let { Text(stringResource(R.string.ach_best_order, CurrencyFormatter.format(it, currency)), color = WaTextSecondary, fontSize = 13.sp) }
    val badges = listOf(
        Badge("🔥", R.string.badge_smart3, a.bestSmartStreak.toDouble(), 3.0),
        Badge("⭐", R.string.badge_smart7, a.bestSmartStreak.toDouble(), 7.0),
        Badge("🏆", R.string.badge_smart30, a.bestSmartStreak.toDouble(), 30.0),
        Badge("💯", R.string.badge_orders100, a.totalOrders.toDouble(), 100.0),
        Badge("🚀", R.string.badge_orders1000, a.totalOrders.toDouble(), 1000.0),
        Badge("⏱", R.string.badge_hours100, a.totalActiveHours, 100.0)
    )
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        badges.forEach { b ->
            val done = b.progress >= b.target
            Text(
                if (done) "${b.icon} ${stringResource(b.label)}"
                else "${b.icon} ${stringResource(b.label)} ${b.progress.toInt().coerceAtMost(b.target.toInt())}/${b.target.toInt()}",
                color = if (done) Color.Black else WaTextSecondary,
                fontSize = 12.sp,
                fontWeight = if (done) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (done) WaNeonGreen else WaSurfaceVariant)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
}

private fun formatPercent(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else String.format(java.util.Locale.US, "%.1f", v)
