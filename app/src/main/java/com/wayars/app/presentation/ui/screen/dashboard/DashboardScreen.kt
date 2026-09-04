package com.wayars.app.presentation.ui.screen.dashboard

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wayars.app.R
import com.wayars.app.domain.model.OrderEvaluation
import com.wayars.app.presentation.TodaySummary
import com.wayars.app.presentation.ui.component.VerdictCard
import com.wayars.app.presentation.ui.theme.WaNeonGreen
import com.wayars.app.presentation.ui.theme.WaSurface
import com.wayars.app.presentation.ui.theme.WaSurfaceVariant
import com.wayars.app.presentation.ui.theme.WaTextSecondary
import com.wayars.app.util.CurrencyFormatter

@Composable
fun DashboardScreen(
    summary: TodaySummary,
    latestEvaluation: OrderEvaluation?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Header: small app glyph + wordmark on the left, "Active" switch on the right —
        // matches the design template's top bar. The switch is a visual on/off indicator;
        // it doesn't yet drive the scanning service directly.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.wayars_icon_master),
                    contentDescription = null,
                    modifier = Modifier.size(28.dp).clip(RoundedCornerShape(8.dp))
                )
                Text(
                    "WayArs",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            var active by remember { mutableStateOf(true) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Active", color = WaTextSecondary, fontSize = 13.sp, modifier = Modifier.padding(end = 6.dp))
                Switch(
                    checked = active,
                    onCheckedChange = { active = it },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = WaNeonGreen,
                        checkedThumbColor = Color.White,
                        uncheckedTrackColor = WaSurfaceVariant
                    )
                )
            }
        }

        // Combined "Today's Summary" card: orders + earnings headline, divider,
        // then a 3-column stat row (Active Time / Distance / Avg Rate).
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(WaSurface)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                stringResource(R.string.dashboard_today_summary),
                color = WaTextSecondary,
                fontSize = 14.sp
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "${summary.ordersCount}",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp
                )
                Text(
                    " ${stringResource(R.string.dashboard_orders)}",
                    color = WaTextSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 3.dp)
                )
            }
            Text(
                CurrencyFormatter.format(summary.totalEarnings, summary.currency),
                color = WaNeonGreen,
                fontWeight = FontWeight.Bold,
                fontSize = 30.sp
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp), color = WaSurfaceVariant)

            Row(modifier = Modifier.fillMaxWidth()) {
                SummaryStat(
                    value = fmtDuration(summary.totalTimeMinutes),
                    label = stringResource(R.string.dashboard_active_time),
                    modifier = Modifier.weight(1f)
                )
                SummaryStat(
                    value = "${fmt(summary.totalDistanceKm)} km",
                    label = stringResource(R.string.dashboard_distance),
                    modifier = Modifier.weight(1f)
                )
                SummaryStat(
                    value = CurrencyFormatter.formatRatePerKm(summary.avgRatePerKm, summary.currency),
                    label = stringResource(R.string.dashboard_avg_rate),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Text(
            stringResource(R.string.dashboard_order_evaluation),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White
        )
        VerdictCard(
            evaluation = latestEvaluation,
            emptyLabel = stringResource(R.string.dashboard_no_order)
        )
    }
}

@Composable
private fun SummaryStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Text(label, color = WaTextSecondary, fontSize = 12.sp)
    }
}

private fun fmt(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else String.format("%.1f", value)

private fun fmtDuration(totalMinutes: Double): String {
    val mins = totalMinutes.toLong()
    val h = mins / 60
    val m = mins % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}
