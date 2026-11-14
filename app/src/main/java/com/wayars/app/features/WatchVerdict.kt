package com.wayars.app.features

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.wayars.app.R
import com.wayars.app.data.prefs.DriverPrefs
import com.wayars.app.domain.model.OrderEvaluation
import com.wayars.app.domain.model.Verdict
import com.wayars.app.util.CurrencyFormatter
import java.util.Locale

/**
 * Verdict on a Wear OS watch (Galaxy Watch, Pixel Watch…). Android mirrors phone
 * notifications to a paired watch, so each new order posts a short notification on a channel
 * with its own vibration: one short buzz = profitable, two short = not, one long = average.
 * Importance DEFAULT, so on the phone it never pops over the order screen (no heads-up).
 */
object WatchVerdict {
    private const val NOTIFICATION_ID = 4_242
    private const val TIMEOUT_MS = 30_000L
    private const val SAME_ORDER_MS = 3 * 60_000L

    private val patterns = mapOf(
        Verdict.GOOD to longArrayOf(0, 160),
        Verdict.AVERAGE to longArrayOf(0, 600),
        Verdict.BAD to longArrayOf(0, 160, 140, 160)
    )

    @Volatile private var lastKey: String? = null
    @Volatile private var lastAt = 0L

    private fun channelId(v: Verdict) = "watch_verdict_" + v.name.lowercase(Locale.ROOT)

    private fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        for (v in Verdict.values()) {
            if (nm.getNotificationChannel(channelId(v)) != null) continue
            val name = context.getString(R.string.watch_channel, context.getString(verdictRes(v)))
            nm.createNotificationChannel(NotificationChannel(channelId(v), name, NotificationManager.IMPORTANCE_DEFAULT).apply {
                setSound(null, null)
                enableVibration(true)
                vibrationPattern = patterns.getValue(v)
                setShowBadge(false)
            })
        }
    }

    private fun verdictRes(v: Verdict) = when (v) {
        Verdict.GOOD -> R.string.verdict_good
        Verdict.AVERAGE -> R.string.verdict_average
        Verdict.BAD -> R.string.verdict_bad
    }

    fun onOrderShown(context: Context, e: OrderEvaluation, sourcePackage: String) {
        DriverPrefs.init(context)
        if (!DriverPrefs.watchEnabled.value) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, "android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED
        ) return
        // Live re-reads of the same order must not buzz again.
        val key = "$sourcePackage|${e.earnings}|${e.distanceKm}"
        val now = System.currentTimeMillis()
        if (key == lastKey && now - lastAt < SAME_ORDER_MS) return
        lastKey = key
        lastAt = now
        runCatching {
            ensureChannels(context)
            val mark = when (e.verdict) { Verdict.GOOD -> "✅"; Verdict.AVERAGE -> "🟡"; Verdict.BAD -> "❌" }
            val title = "$mark ${context.getString(verdictRes(e.verdict))} · ${CurrencyFormatter.format(e.takeHome, e.currency)}"
            val parts = mutableListOf(String.format(Locale.US, "%.1f km", e.distanceKm))
            if (e.timeMinutes > 0) parts.add(String.format(Locale.US, "%.0f min", e.timeMinutes))
            parts.add(AppNames.label(context, sourcePackage))
            val n = NotificationCompat.Builder(context, channelId(e.verdict))
                .setSmallIcon(R.drawable.ic_stat_wayars)
                .setContentTitle(title)
                .setContentText(parts.joinToString(" · "))
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setVibrate(patterns.getValue(e.verdict))
                .setTimeoutAfter(TIMEOUT_MS)
                .setAutoCancel(true)
                .setOnlyAlertOnce(false)
                .build()
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, n)
        }
    }

    /** The driver decided on the card: the watch note is no longer needed. */
    fun cancel(context: Context) {
        runCatching { NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID) }
    }
}
