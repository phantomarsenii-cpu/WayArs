package com.wayars.app.presentation.ui.screen.dashboard

import com.wayars.app.service.accessibility.FreeTrial
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.wayars.app.R
import com.wayars.app.presentation.ui.tour.TourTarget
import com.wayars.app.presentation.ui.tour.tourTarget
import com.wayars.app.domain.model.OrderEvaluation
import com.wayars.app.presentation.TodaySummary
import com.wayars.app.presentation.ui.component.VerdictCard
import com.wayars.app.presentation.ui.theme.WaNeonGreen
import com.wayars.app.presentation.ui.theme.WaSurface
import com.wayars.app.presentation.ui.theme.WaSurfaceVariant
import com.wayars.app.presentation.ui.theme.WaTextSecondary
import com.wayars.app.service.accessibility.ScanLogFile
import com.wayars.app.service.accessibility.ActiveSessionTracker
import com.wayars.app.service.accessibility.ScanningState
import com.wayars.app.service.overlay.OverlayService
import com.wayars.app.util.AccessibilityUtils
import com.wayars.app.util.CurrencyFormatter
import kotlinx.coroutines.delay

@Composable
fun DashboardScreen(
    summary: TodaySummary,
    latestEvaluation: OrderEvaluation?,
    isSubscribed: Boolean,
    onSubscriptionRequired: () -> Unit,
    onPermissionsRequired: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isActive by ScanningState.isActive.collectAsState()
    // Time the scanner has been switched ON today (not order-based).
    // Holds WHOLE MINUTES, so the card only recomposes when the shown value
    // really changes; while scanning runs it sleeps exactly until the next
    // minute boundary, so the tile ticks up the moment the minute flips.
    val scanningNow = isActive
    val lifecycleOwner = LocalLifecycleOwner.current
    // Whole minutes. Only runs while the screen is visible (STARTED); when scanning
    // is OFF it reads the value once and stops, when scanning is ON it sleeps
    // exactly until the next minute boundary. Nothing runs in the background.
    val activeTodayMinutes by produceState(initialValue = ActiveSessionTracker.todayMillis() / 60_000L, scanningNow) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val ms = ActiveSessionTracker.todayMillis()
                value = ms / 60_000L
                if (!scanningNow) break
                delay(60_000L - (ms % 60_000L) + 50L)
            }
        }
    }
    val requiresSubscriptionToast = stringResource(R.string.active_requires_subscription)
    // Minutes of the one-time free trial left (only shown to a user without a subscription).
    val trialLeftMinutes by produceState(initialValue = FreeTrial.minutesLeft(), isActive, isSubscribed) {
        while (true) {
            value = FreeTrial.minutesLeft()
            delay(15_000L)
        }
    }
    // Only a FINISHED session's file ever lands here (see ScanLogFile), so
    // this is never offered while a scan is still being written to.
    val lastLogFile by ScanLogFile.lastCompletedLogFile.collectAsState()

    // Android 13+ requires POST_NOTIFICATIONS to be granted at RUNTIME, not
    // just declared in the manifest — without it, the foreground service's
    // notification (and its status-bar icon) can be silently suppressed even
    // though the service itself is running fine, which looked like "Active
    // sometimes needs to be toggled twice" but was really just a missing
    // permission prompt that never happened.
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* proceed regardless of the result — see activateScanning below */ }

    fun activateScanning() {
        // Gate #1, right here at the source of truth for "Active": this is
        // the ONE place the switch actually flips ScanningState on, so it's
        // the one place that must refuse to do so without a live, server-
        // verified subscription — the LaunchedEffect(gateState) redirect to
        // the paywall in WayArsNavHost is a *reaction* to an already-lapsed
        // subscription, not a gate on this action, and it only runs while
        // MainScreen is composed. Without this check, a lapsed subscriber
        // sitting on the Dashboard (e.g. in the brief window before that
        // redirect fires, or if it's ever missed) could just flip Active
        // back on with nothing to stop them. isSubscribed is derived the
        // same way (from SubscriptionViewModel.gateState, server-verified
        // via RevenueCat) so this can't be spoofed by anything on-device.
        if (!isSubscribed && !FreeTrial.hasTimeLeft()) {
            Toast.makeText(context, requiresSubscriptionToast, Toast.LENGTH_LONG).show()
            onSubscriptionRequired()
            return
        }
        val overlayOk = Settings.canDrawOverlays(context)
        val accessibilityOk = AccessibilityUtils.isServiceEnabled(context)
        if (overlayOk && accessibilityOk) {
            ScanningState.setActive(true, context)
            context.startForegroundService(Intent(context, OverlayService::class.java))
        } else {
            Toast.makeText(
                context,
                context.getString(R.string.active_requires_permissions),
                Toast.LENGTH_LONG
            ).show()
            // Take the user straight to the permissions (and the screen-reading consent dialog).
            onPermissionsRequired()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Header: small app glyph + wordmark on the left, "Active" switch on the right.
        // The switch is wired to the real global scanning gate (ScanningState) — when
        // OFF, the accessibility service does nothing at all and the overlay service
        // (with its status-bar icon) is fully stopped, not just visually hidden.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.wayars_icon_header),
                    contentDescription = null,
                    modifier = Modifier.size(28.dp).clip(RoundedCornerShape(8.dp))
                )
                Text(
                    "WayArs",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            Row(
                modifier = Modifier.tourTarget(TourTarget.ACTIVE_SWITCH),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Active",
                    color = WaTextSecondary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = 6.dp)
                )
                Switch(
                    checked = isActive,
                    onCheckedChange = { checked ->
                        if (checked) {
                            // Check the subscription BEFORE asking for the
                            // notification permission — no point prompting a
                            // lapsed subscriber for a system permission on
                            // the way to a paywall redirect. activateScanning()
                            // re-checks isSubscribed itself either way (it's
                            // the real gate); this just skips the pointless
                            // permission prompt when we already know it'll refuse.
                            val needsNotificationPermission = (isSubscribed || FreeTrial.hasTimeLeft()) &&
                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                                PackageManager.PERMISSION_GRANTED
                            if (needsNotificationPermission) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            activateScanning()
                        } else {
                            ScanningState.setActive(false, context)
                            context.stopService(Intent(context, OverlayService::class.java))
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = WaNeonGreen,
                        checkedThumbColor = Color.White,
                        uncheckedTrackColor = WaSurfaceVariant
                    )
                )
            }
        }

        if (!isSubscribed && trialLeftMinutes > 0) {
            Text(
                stringResource(R.string.dashboard_trial_left, trialLeftMinutes),
                color = WaNeonGreen,
                fontSize = 13.sp
            )
        }

        // "Share log" row hidden from the dashboard UI per product decision —
        // the file itself is still written/tracked by ScanLogFile as before,
        // it's just no longer surfaced as a button here. Flip this back to
        // `lastLogFile != null && !isActive` to restore the button.
        if (false) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    stringResource(R.string.dashboard_share_log),
                    color = WaNeonGreen,
                    fontSize = 13.sp,
                    modifier = Modifier.clickable {
                        val file = lastLogFile ?: return@clickable
                        val uri = androidx.core.content.FileProvider.getUriForFile(
                            context, "${context.packageName}.fileprovider", file
                        )
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(
                            Intent.createChooser(sendIntent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                )
            }
        }

        // Combined "Today's Summary" card: orders + earnings headline, divider,
        // then a 3-column stat row (Active Time / Distance / Avg Rate).
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .tourTarget(TourTarget.SUMMARY_CARD)
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
                    value = fmtDuration(activeTodayMinutes.toDouble()),
                    label = stringResource(R.string.dashboard_active_time),
                    modifier = Modifier.weight(1f)
                )
                SummaryStat(
                    value = fmtDuration(summary.totalTimeMinutes),
                    label = stringResource(R.string.dashboard_order_time),
                    modifier = Modifier.weight(1f)
                )
            }
            androidx.compose.foundation.layout.Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
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
            emptyLabel = stringResource(R.string.dashboard_no_order),
            modifier = Modifier.tourTarget(TourTarget.VERDICT_CARD)
        )

        // Bottom inset so content scrolls fully BEHIND the floating nav pill
        // instead of stopping short with a separately-colored reserved gap
        // above it. Computed from the device's actual system nav-bar inset
        // (see bottomNavBarClearance) rather than a fixed guess, so it
        // stays correct on 3-button nav devices too, not just gesture nav.
        androidx.compose.foundation.layout.Spacer(Modifier.height(com.wayars.app.presentation.ui.component.bottomNavBarClearance()))
    }
}

@Composable
private fun SummaryStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, color = WaTextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
