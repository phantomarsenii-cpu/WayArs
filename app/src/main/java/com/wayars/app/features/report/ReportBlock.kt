package com.wayars.app.features.report

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wayars.app.R
import com.wayars.app.features.AppNames
import com.wayars.app.presentation.ui.theme.WaNeonGreen
import com.wayars.app.presentation.ui.theme.WaTextSecondary
import kotlinx.coroutines.launch

/**
 * Shown under a phone check that found nothing wrong: lets the driver send an encrypted
 * order-reading report for the app whose orders aren't read (see [ReportBuilder]). If WayArs
 * hasn't seen any supported app lately, it first asks them to get an order on screen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReportBlock(isSubscribed: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tick by remember { mutableStateOf(0) }
    var rechecked by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Boolean?>(null) }
    var sending by remember { mutableStateOf(false) }
    val recent = remember(tick) { ReportBuilder.recentApps() }
    // Nothing seen even after "Check again": still let them send one for an installed app —
    // "WayArs saw nothing at all" is itself what the developer needs to know.
    val apps = remember(tick, rechecked) {
        recent.ifEmpty { if (rechecked) ReportBuilder.installedApps(context) else emptyList() }
    }
    val small = Modifier.padding(top = 6.dp)
    // Screen text read by the accessibility service leaves the phone only after the driver has
    // seen exactly what goes into the report and confirmed it (Google Play's Accessibility API
    // policy: prominent disclosure and consent before such data is transmitted).
    var confirmPkg by remember { mutableStateOf<String?>(null) }
    confirmPkg?.let { pkg ->
        AlertDialog(
            onDismissRequest = { confirmPkg = null },
            title = { Text(stringResource(R.string.report_confirm_title)) },
            text = {
                Text(
                    stringResource(R.string.report_confirm_body, AppNames.label(context, pkg)),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                )
            },
            confirmButton = {
                Button(onClick = {
                    confirmPkg = null
                    sending = true
                    scope.launch {
                        result = runCatching { ReportBuilder.send(context, pkg, isSubscribed) }.getOrDefault(false)
                        sending = false
                    }
                }) { Text(stringResource(R.string.report_confirm_send)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmPkg = null }) { Text(stringResource(R.string.settings_cancel)) }
            }
        )
    }

    Column(modifier) {
        when {
            recent.isNotEmpty() -> {
                Text(stringResource(R.string.report_hint), color = WaTextSecondary, fontSize = 12.sp, lineHeight = 16.sp, modifier = small)
                if (recent.size > 1) Text(stringResource(R.string.report_which), color = Color.White, fontSize = 12.sp, modifier = small)
            }
            rechecked -> Text(stringResource(R.string.report_none_seen), color = WaTextSecondary, fontSize = 12.sp, lineHeight = 16.sp, modifier = small)
            else -> Text(stringResource(R.string.report_need_order), color = WaTextSecondary, fontSize = 12.sp, lineHeight = 16.sp, modifier = small)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = small) {
            apps.forEach { pkg ->
                Pill(stringResource(R.string.report_button, AppNames.label(context, pkg)), primary = true) {
                    if (!sending) confirmPkg = pkg
                }
            }
            if (recent.isEmpty()) Pill(stringResource(R.string.report_recheck), primary = false) { rechecked = true; tick++ }
        }
        result?.let {
            Text(
                stringResource(if (it) R.string.report_opened else R.string.report_no_mail),
                color = if (it) WaNeonGreen else WaTextSecondary, fontSize = 12.sp, lineHeight = 16.sp, modifier = small
            )
        }
    }
}

@Composable
private fun Pill(label: String, primary: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (primary) Color.Black else WaNeonGreen,
        fontWeight = if (primary) FontWeight.Bold else FontWeight.Normal,
        fontSize = 12.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (primary) WaNeonGreen else WaNeonGreen.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    )
}
