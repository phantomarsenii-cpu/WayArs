package com.wayars.app.features.report

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.wayars.app.R
import com.wayars.app.appContainer
import com.wayars.app.features.AppNames
import com.wayars.app.service.accessibility.CustomPackagesState
import com.wayars.app.service.accessibility.FreeTrial
import com.wayars.app.service.accessibility.OrderAccessibilityService
import com.wayars.app.service.accessibility.PackageHintsState
import com.wayars.app.service.accessibility.ScanDiagnostics
import com.wayars.app.service.accessibility.ScanningState
import com.wayars.app.util.AccessibilityUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * "Orders aren't read" report, sent only when the driver taps the button in Wayo's phone check.
 *
 * What WayArs last read from the screen of ONE chosen app (plus phone/app/permission state) is
 * put into a text report, encrypted on the phone with [ReportCrypto] (the driver can't open it,
 * only the developer can), and handed to the driver's mail app with our address, a subject and a
 * file name that all name the app in question — the driver only has to tap "Send".
 */
object ReportBuilder {
    const val EMAIL = "apstudiomobile@gmail.com"

    /** Scans within this window count as "you just had an order on screen". */
    private const val RECENT_MS = 30L * 60 * 1000

    /** Supported apps WayArs read something from in the last half hour, most recent first. */
    fun recentApps(): List<String> = ScanDiagnostics.recentSupportedApps(RECENT_MS).map { it.first }

    /** Supported apps installed on this phone (for sending a report even when nothing was seen). */
    fun installedApps(context: Context): List<String> {
        val pm = context.packageManager
        return (OrderAccessibilityService.builtInPackages + CustomPackagesState.packages)
            .filter { pm.getLaunchIntentForPackage(it) != null }
            .sortedBy { AppNames.label(context, it).lowercase() }
    }

    /** Builds, encrypts and opens the email. False when no mail app could take it. */
    suspend fun send(context: Context, pkg: String, isSubscribed: Boolean): Boolean {
        val app = AppNames.label(context, pkg)
        val file = withContext(Dispatchers.IO) {
            val text = buildText(context, pkg, app, isSubscribed)
            val dir = File(context.cacheDir, "share").apply { mkdirs() }
            dir.listFiles { f -> f.name.endsWith(".wayars") }?.forEach { it.delete() }
            val stamp = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date())
            val safe = app.replace(Regex("[^A-Za-z0-9]+"), "")
                .ifEmpty { pkg.substringAfterLast('.').replace(Regex("[^A-Za-z0-9]+"), "") }
            File(dir, "WayArs_report_${safe}_$stamp.wayars").apply {
                writeBytes(ReportCrypto.encrypt(text.toByteArray(Charsets.UTF_8)))
            }
        }
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_EMAIL, arrayOf(EMAIL))
            putExtra(Intent.EXTRA_SUBJECT, "WayArs report: $app — orders not read")
            putExtra(Intent.EXTRA_TEXT, context.getString(R.string.report_email_body, app))
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        // Mail apps only (address and attachment already filled in); any app that can send a
        // file if the phone has no mail app that accepts the "mailto:" selector.
        val toMail = Intent(send).apply { selector = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")) }
        if (runCatching { context.startActivity(toMail) }.isSuccess) return true
        return runCatching {
            context.startActivity(
                Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            )
        }.isSuccess
    }

    private suspend fun buildText(context: Context, pkg: String, app: String, isSubscribed: Boolean): String {
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val now = System.currentTimeMillis()
        val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
        val appVersion = runCatching { context.packageManager.getPackageInfo(pkg, 0).versionName }.getOrNull()
        val currency = runCatching { context.appContainer().settingsRepository.currency.first().code }.getOrNull()
        val power = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        val scans = ScanDiagnostics.historyFor(pkg)
        val others = ScanDiagnostics.recentSupportedApps(2L * 60 * 60 * 1000).filter { it.first != pkg }
        fun yes(b: Boolean) = if (b) "yes" else "NO"
        return buildString {
            appendLine("PROBLEM APP: $app ($pkg)${appVersion?.let { " v$it" } ?: ""}")
            appendLine("Report time: ${time.format(Date(now))} (${java.util.TimeZone.getDefault().id})")
            appendLine("WayArs: ${version?.versionName} (${version?.let { androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(it) }})")
            appendLine("Phone: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Language: ${Locale.getDefault().toLanguageTag()}")
            appendLine()
            appendLine("== State ==")
            appendLine("Active: ${yes(ScanningState.isActive.value)}")
            appendLine("Accessibility enabled: ${yes(AccessibilityUtils.isServiceEnabled(context))}, service running: ${yes(OrderAccessibilityService.isConnected)}")
            appendLine("Overlay permission: ${yes(Settings.canDrawOverlays(context))}")
            appendLine("Notification access: ${yes(AccessibilityUtils.isNotificationListenerEnabled(context))}")
            appendLine("Battery unrestricted: ${yes(power.isIgnoringBatteryOptimizations(context.packageName))}")
            appendLine("Screen interactive: ${yes(power.isInteractive)}")
            appendLine("Plan: subscribed=${yes(isSubscribed)}, trial left=${yes(FreeTrial.hasTimeLeft())}")
            appendLine("Currency: $currency")
            appendLine("Calibration for $pkg: ${PackageHintsState.hints[pkg]?.let { if (it.isEmpty) "none" else it.toString() } ?: "none"}")
            appendLine("Custom apps: ${CustomPackagesState.packages.sorted().joinToString().ifEmpty { "none" }}")
            appendLine()
            appendLine("== Last scans of $app: ${scans.size} (newest last) ==")
            if (scans.isEmpty()) appendLine("Nothing was read from this app in the last 2 hours.")
            scans.forEachIndexed { i, e ->
                appendLine()
                appendLine("#${i + 1} ${time.format(Date(e.timestampMillis))} window=${e.windowFound} texts=${e.textsCollected}")
                e.parsedSummary?.let { appendLine("  parsed: $it") }
                if (e.rawTexts.isNotEmpty()) {
                    appendLine("  raw:")
                    e.rawTexts.forEach { appendLine("   | $it") }
                }
            }
            appendLine()
            appendLine("== Other supported apps seen (2 h) ==")
            if (others.isEmpty()) appendLine("none")
            others.forEach { (p, at) -> appendLine("${AppNames.label(context, p)} ($p), last ${time.format(Date(at))}") }
        }
    }
}
