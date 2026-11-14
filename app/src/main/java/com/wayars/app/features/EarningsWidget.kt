package com.wayars.app.features

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.RemoteViews
import com.wayars.app.MainActivity
import com.wayars.app.R
import com.wayars.app.appContainer
import com.wayars.app.data.prefs.DriverPrefs
import com.wayars.app.domain.model.Currency
import com.wayars.app.service.accessibility.ActiveSessionTracker
import com.wayars.app.service.accessibility.FreeTrial
import com.wayars.app.service.accessibility.ScanningState
import com.wayars.app.service.overlay.OverlayService
import com.wayars.app.util.AccessibilityUtils
import com.wayars.app.util.CurrencyFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Home-screen widget: today's take-home, orders and per-hour, plus the "Active" switch. */
object EarningsWidget {
    const val ACTION_TOGGLE = "com.wayars.app.action.WIDGET_TOGGLE"

    fun requestUpdate(context: Context) {
        runCatching {
            val app = context.applicationContext
            val mgr = AppWidgetManager.getInstance(app)
            val ids = mgr.getAppWidgetIds(ComponentName(app, EarningsWidgetProvider::class.java))
            if (ids.isEmpty()) return
            app.sendBroadcast(
                Intent(app, EarningsWidgetProvider::class.java)
                    .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            )
        }
    }

    /** Asks the launcher to pin the widget (Android 8+ launchers that support it). */
    fun requestPin(context: Context): Boolean = runCatching {
        val mgr = AppWidgetManager.getInstance(context)
        if (!mgr.isRequestPinAppWidgetSupported) return false
        mgr.requestPinAppWidget(ComponentName(context, EarningsWidgetProvider::class.java), null, null)
    }.getOrDefault(false)

    internal suspend fun render(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        val container = context.appContainer()
        val currency: Currency = runCatching { container.settingsRepository.currency.first() }.getOrDefault(Currency.default)
        val now = System.currentTimeMillis()
        val from = Earnings.startOfDay(now)
        val orders = runCatching { container.orderRepository.acceptedSince(from) }.getOrDefault(emptyList())
        val e = Earnings.period(orders, ActiveSessionTracker.intervals(from, now), from, now, DriverPrefs.weeklyFee.value)
        val active = ScanningState.isActive.value
        val sub = buildString {
            append(context.getString(R.string.summary_part_orders, e.orders))
            e.perHour?.let { append(" · ").append(context.getString(R.string.summary_part_hour, CurrencyFormatter.format(it, currency, 0))) }
        }
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val toggle = PendingIntent.getBroadcast(
            context, 1, Intent(context, EarningsWidgetProvider::class.java).setAction(ACTION_TOGGLE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        for (id in ids) {
            val v = RemoteViews(context.packageName, R.layout.widget_earnings)
            v.setTextViewText(R.id.widget_title, context.getString(R.string.widget_today))
            v.setTextViewText(R.id.widget_amount, CurrencyFormatter.format(e.net, currency))
            v.setTextViewText(R.id.widget_sub, sub)
            v.setTextViewText(R.id.widget_toggle, context.getString(if (active) R.string.widget_active else R.string.widget_off))
            v.setInt(R.id.widget_toggle, "setBackgroundResource", if (active) R.drawable.widget_toggle_on else R.drawable.widget_toggle_off)
            v.setTextColor(R.id.widget_toggle, if (active) 0xFF000000.toInt() else 0xFF9CA3AF.toInt())
            v.setOnClickPendingIntent(R.id.widget_root, open)
            v.setOnClickPendingIntent(R.id.widget_toggle, toggle)
            runCatching { mgr.updateAppWidget(id, v) }
        }
    }

    internal fun toggle(context: Context) {
        if (ScanningState.isActive.value) {
            ScanningState.setActive(false, context)
            return
        }
        val entitled = FreeTrial.isSubscribedCached() || FreeTrial.hasTimeLeft()
        val permitted = AccessibilityUtils.isServiceEnabled(context) && Settings.canDrawOverlays(context)
        if (entitled && permitted) {
            ScanningState.setActive(true, context)
            runCatching { context.startForegroundService(Intent(context, OverlayService::class.java)) }
        } else {
            // Subscription / permissions need the app itself.
            runCatching { context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }
}

class EarningsWidgetProvider : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == EarningsWidget.ACTION_TOGGLE) {
            EarningsWidget.toggle(context.applicationContext)
            return
        }
        super.onReceive(context, intent)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val app = context.applicationContext
        val result = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try { runCatching { EarningsWidget.render(app, appWidgetManager, appWidgetIds) } } finally { result.finish() }
        }
    }
}
