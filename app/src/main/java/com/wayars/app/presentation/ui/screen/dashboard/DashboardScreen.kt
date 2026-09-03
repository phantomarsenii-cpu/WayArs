package com.wayars.app.presentation.ui.screen.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wayars.app.R
import com.wayars.app.domain.model.OrderEvaluation
import com.wayars.app.presentation.TodaySummary
import com.wayars.app.presentation.ui.component.StatCard
import com.wayars.app.presentation.ui.component.VerdictCard
import com.wayars.app.util.CurrencyFormatter

@Composable
fun DashboardScreen(
    summary: TodaySummary,
    latestEvaluation: OrderEvaluation?
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            stringResource(R.string.dashboard_today_summary),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            "${summary.ordersCount} ${stringResource(R.string.dashboard_orders)} • ${CurrencyFormatter.format(summary.totalEarnings, summary.currency)}",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatCard(
                label = stringResource(R.string.dashboard_distance),
                value = "${fmt(summary.totalDistanceKm)} km",
                modifier = Modifier.weight(1f)
            )
            StatCard(
                label = stringResource(R.string.dashboard_avg_rate),
                value = CurrencyFormatter.formatRatePerKm(summary.avgRatePerKm, summary.currency),
                modifier = Modifier.weight(1f)
            )
        }

        Text(
            stringResource(R.string.dashboard_order_evaluation),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        VerdictCard(
            evaluation = latestEvaluation,
            emptyLabel = stringResource(R.string.dashboard_no_order)
        )
    }
}

private fun fmt(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else String.format("%.1f", value)
