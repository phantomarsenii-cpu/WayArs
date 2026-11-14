package com.wayars.app.features

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.wayars.app.MainActivity
import com.wayars.app.R
import com.wayars.app.data.prefs.DriverPrefs
import com.wayars.app.domain.model.Currency
import com.wayars.app.service.accessibility.ActiveSessionTracker
import com.wayars.app.service.accessibility.ScanningState
import com.wayars.app.util.CurrencyFormatter
import com.wayars.app.appContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Evening "your day" and Monday-morning "your week" notifications. Only sent when there were
 * accepted orders. If scanning is still on at 21:00 the day summary waits (hourly) until it's
 * switched off, but no later than 23:30.
 */
object Summaries {
    const val ACTION_DAILY = "com.wayars.app.action.SUMMARY_DAILY"
    const val ACTION_WEEKLY = "com.wayars.app.action.SUMMARY_WEEKLY"
    private const val CHANNEL = "summaries"
    private const val ID_DAILY = 5_101
    private const val ID_WEEKLY = 5_102
    private const val DAILY_HOUR = 21
    private const val WEEKLY_HOUR = 9

    private fun pending(context: Context, action: String): PendingIntent =
        PendingIntent.getBroadcast(
            context, action.hashCode(),
            Intent(context, SummaryReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun at(millis: Long): Calendar = Calendar.getInstance().apply { timeInMillis = millis }

    fun nextDaily(now: Long = System.currentTimeMillis()): Long = at(now).apply {
        set(Calendar.HOUR_OF_DAY, DAILY_HOUR); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        if (timeInMillis <= now) add(Calendar.DAY_OF_YEAR, 1)
    }.timeInMillis

    fun nextWeekly(now: Long = System.currentTimeMillis()): Long = at(now).apply {
        set(Calendar.HOUR_OF_DAY, WEEKLY_HOUR); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        while (get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY || timeInMillis <= now) add(Calendar.DAY_OF_YEAR, 1)
    }.timeInMillis

    private fun arm(context: Context, action: String, triggerAt: Long) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending(context, action)) }
    }

    /** (Re)arms both alarms; or cancels them when the driver switched summaries off. */
    fun schedule(context: Context) {
        DriverPrefs.init(context)
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        if (!DriverPrefs.summariesEnabled.value) {
            am.cancel(pending(context, ACTION_DAILY)); am.cancel(pending(context, ACTION_WEEKLY))
            return
        }
        arm(context, ACTION_DAILY, nextDaily())
        arm(context, ACTION_WEEKLY, nextWeekly())
    }

    private fun canNotify(context: Context) = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, "android.permission.POST_NOTIFICATIONS") == PackageManager.PERMISSION_GRANTED

    internal suspend fun handle(context: Context, action: String) {
        DriverPrefs.init(context)
        if (!DriverPrefs.summariesEnabled.value) return
        val now = System.currentTimeMillis()
        if (action == ACTION_DAILY) {
            val hour = at(now).get(Calendar.HOUR_OF_DAY)
            val minute = at(now).get(Calendar.MINUTE)
            if (ScanningState.isActive.value && (hour < 23 || (hour == 23 && minute < 30))) {
                // Still working: try again in an hour (or at 23:30 at the latest).
                val retry = minOf(now + 60 * 60_000L, at(now).apply { set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 30) }.timeInMillis)
                arm(context, ACTION_DAILY, maxOf(retry, now + 60_000L))
                return
            }
            arm(context, ACTION_DAILY, nextDaily(now))
            val from = Earnings.startOfDay(now)
            post(context, ID_DAILY, buildText(context, from, now, weekly = false) ?: return)
        } else {
            arm(context, ACTION_WEEKLY, nextWeekly(now))
            val to = Earnings.startOfDay(now)
            val from = at(to).apply { add(Calendar.DAY_OF_YEAR, -7) }.timeInMillis
            post(context, ID_WEEKLY, buildText(context, from, to, weekly = true) ?: return)
        }
    }

    /** Title + text, or null when there were no accepted orders in the period. */
    suspend fun buildText(context: Context, from: Long, to: Long, weekly: Boolean): Pair<String, String>? {
        val container = context.appContainer()
        val currency: Currency = runCatching { container.settingsRepository.currency.first() }.getOrDefault(Currency.default)
        val orders = container.orderRepository.acceptedSince(from).filter { it.timestampMillis < to }
        if (orders.isEmpty()) return null
        val e = Earnings.period(orders, ActiveSessionTracker.intervals(from, to), from, to, DriverPrefs.weeklyFee.value)
        val fmt = { v: Double -> CurrencyFormatter.format(v, currency) }
        val parts = mutableListOf(context.getString(R.string.summary_part_orders, e.orders))
        e.perHour?.let { parts.add(context.getString(R.string.summary_part_hour, fmt(it))) }
        if (weekly && com.wayars.app.pet.PetState.state.value.level >= Abilities.WEEKLY_FORECAST_LEVEL) {
            // Pro ability: the driver's best times (app + weekday + part of the day) from 60 days.
            val since = to - 60L * 86_400_000L
            val best = Earnings.bestSlots(
                Earnings.slotRates(container.orderRepository.acceptedSince(since).filter { it.timestampMillis < to }, ActiveSessionTracker.intervals(since, to)),
                2
            )
            if (best.isNotEmpty()) {
                parts.add(context.getString(R.string.summary_forecast, best.joinToString(", ") {
                    com.wayars.app.presentation.pet.PetGreeter.slotName(context, it.dayOfWeek, it.dayPart) + " (" + AppNames.label(context, it.sourcePackage) + ")"
                }))
            }
        }
        if (weekly) {
            val bestDay = orders.groupBy { Earnings.startOfDay(it.timestampMillis) }
                .maxByOrNull { (_, l) -> l.sumOf { it.evaluation.takeHome } }?.key
            if (bestDay != null) {
                val name = SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(bestDay))
                parts.add(context.getString(R.string.summary_part_best_day, name))
            }
        } else {
            val best = orders.maxByOrNull { it.evaluation.takeHome }
            if (best != null) {
                val app = best.sourcePackage?.let { AppNames.label(context, it) }
                parts.add(
                    if (app != null) context.getString(R.string.summary_part_best, fmt(best.evaluation.takeHome), app)
                    else context.getString(R.string.summary_part_best_plain, fmt(best.evaluation.takeHome))
                )
            }
        }
        val title = context.getString(if (weekly) R.string.summary_weekly_title else R.string.summary_daily_title, fmt(e.net))
        return title to parts.joinToString(" · ")
    }

    private fun post(context: Context, id: Int, content: Pair<String, String>) {
        if (!canNotify(context)) return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
                    NotificationChannel(CHANNEL, context.getString(R.string.summary_channel), NotificationManager.IMPORTANCE_DEFAULT)
                )
            }
            val open = PendingIntent.getActivity(
                context, id, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val n = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_wayars)
                .setContentTitle(content.first)
                .setContentText(content.second)
                .setStyle(NotificationCompat.BigTextStyle().bigText(content.second))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(context).notify(id, n)
        }
    }
}

class SummaryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        when (val action = intent.action) {
            Summaries.ACTION_DAILY, Summaries.ACTION_WEEKLY -> {
                val result = goAsync()
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    try { runCatching { Summaries.handle(app, action) } } finally { result.finish() }
                }
            }
            // Boot / app update: alarms are lost, arm them again, and redraw the widget with the
            // new version's numbers (the launcher keeps the old picture until it's updated).
            else -> {
                Summaries.schedule(app)
                EarningsWidget.requestUpdate(app)
            }
        }
    }
}
