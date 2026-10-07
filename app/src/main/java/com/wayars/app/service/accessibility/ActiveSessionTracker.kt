package com.wayars.app.service.accessibility

import android.content.Context
import java.util.Calendar

/**
 * Counts how long the driver has had scanning switched ON (the Dashboard
 * "Active" toggle) today - independent of whether any order was seen or
 * accepted. This is the "Active Time" tile; the old order-based figure is
 * now the separate "Order Time" tile.
 *
 * Persisted in SharedPreferences so it survives process death: while a
 * session runs, a heartbeat is written (by the app's polling loop); if the
 * process dies mid-session, the next cold start credits the session up to
 * the last heartbeat. Only today's total is kept - it resets at midnight,
 * and a session running across midnight only counts its post-midnight part.
 */
object ActiveSessionTracker {

    private const val PREFS = "active_session_tracker"
    private const val KEY_DAY = "day"
    private const val KEY_TODAY_MS = "today_ms"
    private const val KEY_SESSION_START = "session_start"
    private const val KEY_LAST_BEAT = "last_beat"

    @Volatile
    private var appContext: Context? = null

    private fun prefs() = appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun startOfDay(millis: Long): Long = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /** Call once from Application.onCreate. Recovers a session cut short by process death. */
    @Synchronized
    fun init(context: Context) {
        appContext = context.applicationContext
        val p = prefs() ?: return
        val start = p.getLong(KEY_SESSION_START, 0L)
        if (start > 0L) {
            val beat = maxOf(p.getLong(KEY_LAST_BEAT, start), start)
            credit(start, beat)
            p.edit().putLong(KEY_SESSION_START, 0L).apply()
        }
    }

    @Synchronized
    fun onActiveChanged(active: Boolean, context: Context) {
        if (appContext == null) appContext = context.applicationContext
        val p = prefs() ?: return
        val now = System.currentTimeMillis()
        val start = p.getLong(KEY_SESSION_START, 0L)
        if (active) {
            if (start == 0L) {
                p.edit().putLong(KEY_SESSION_START, now).putLong(KEY_LAST_BEAT, now).apply()
            }
        } else if (start > 0L) {
            credit(start, now)
            p.edit().putLong(KEY_SESSION_START, 0L).apply()
        }
    }

    /** Called periodically while scanning runs, so a killed process loses at most one interval. */
    @Synchronized
    fun heartbeat() {
        val p = prefs() ?: return
        if (p.getLong(KEY_SESSION_START, 0L) > 0L) {
            p.edit().putLong(KEY_LAST_BEAT, System.currentTimeMillis()).apply()
        }
    }

    /** Today's active milliseconds, including the currently running session. */
    @Synchronized
    fun todayMillis(now: Long = System.currentTimeMillis()): Long {
        val p = prefs() ?: return 0L
        val dayStart = startOfDay(now)
        var total = if (p.getLong(KEY_DAY, 0L) == dayStart) p.getLong(KEY_TODAY_MS, 0L) else 0L
        val start = p.getLong(KEY_SESSION_START, 0L)
        if (start > 0L) total += maxOf(0L, now - maxOf(start, dayStart))
        return total
    }

    private fun credit(start: Long, end: Long) {
        val p = prefs() ?: return
        val dayStart = startOfDay(end)
        val base = if (p.getLong(KEY_DAY, 0L) == dayStart) p.getLong(KEY_TODAY_MS, 0L) else 0L
        val add = maxOf(0L, end - maxOf(start, dayStart))
        p.edit().putLong(KEY_DAY, dayStart).putLong(KEY_TODAY_MS, base + add).apply()
    }
}
