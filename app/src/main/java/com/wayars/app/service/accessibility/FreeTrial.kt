package com.wayars.app.service.accessibility

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * One-time free trial of scanning: [TRIAL_MS] of scanning-ON time per install, in total, ever.
 * Only time spent scanning WITHOUT a subscription is counted. Persisted in SharedPreferences
 * (same approach as [ActiveSessionTracker]); a session cut short by process death is credited
 * up to the last heartbeat. There is deliberately no way to reset it in the app.
 */
object FreeTrial {

    const val TRIAL_MS = 30L * 60_000L

    private const val PREFS = "free_trial"
    private const val KEY_USED = "used_ms"
    private const val KEY_START = "session_start"
    private const val KEY_BEAT = "last_beat"

    @Volatile
    private var appContext: Context? = null

    /** Mirrors the verified subscription state; set from WayArsApplication. */
    @Volatile
    private var subscribed = false

    private val _expired = MutableStateFlow(false)
    /** True after the trial ran out while scanning; the UI shows the paywall and calls [consumeExpired]. */
    val expired: StateFlow<Boolean> = _expired

    private fun prefs() = appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    fun init(context: Context) {
        appContext = context.applicationContext
        val p = prefs() ?: return
        val start = p.getLong(KEY_START, 0L)
        if (start > 0L) {
            val beat = maxOf(p.getLong(KEY_BEAT, start), start)
            credit(start, beat)
        }
    }

    @Synchronized
    fun setSubscribed(value: Boolean) {
        subscribed = value
        if (value) closeSession(System.currentTimeMillis())
    }

    @Synchronized
    fun onActiveChanged(active: Boolean, context: Context) {
        if (appContext == null) appContext = context.applicationContext
        val p = prefs() ?: return
        val now = System.currentTimeMillis()
        if (active) {
            if (!subscribed && p.getLong(KEY_START, 0L) == 0L) {
                p.edit().putLong(KEY_START, now).putLong(KEY_BEAT, now).apply()
            }
        } else {
            closeSession(now)
        }
    }

    /** Scanning is running without a subscription but no trial session is open yet: open one. */
    @Synchronized
    fun ensureCounting(context: Context) {
        if (appContext == null) appContext = context.applicationContext
        val p = prefs() ?: return
        if (!subscribed && p.getLong(KEY_START, 0L) == 0L) {
            val now = System.currentTimeMillis()
            p.edit().putLong(KEY_START, now).putLong(KEY_BEAT, now).apply()
        }
    }

    @Synchronized
    fun heartbeat() {
        val p = prefs() ?: return
        if (p.getLong(KEY_START, 0L) > 0L) {
            p.edit().putLong(KEY_BEAT, System.currentTimeMillis()).apply()
        }
    }

    @Synchronized
    fun usedMillis(now: Long = System.currentTimeMillis()): Long {
        val p = prefs() ?: return 0L
        val start = p.getLong(KEY_START, 0L)
        val running = if (start > 0L) maxOf(0L, now - start) else 0L
        return minOf(TRIAL_MS, p.getLong(KEY_USED, 0L) + running)
    }

    fun remainingMillis(): Long = TRIAL_MS - usedMillis()

    fun hasTimeLeft(): Boolean = remainingMillis() > 0L

    /** Whole minutes left, rounded up (0 only when the trial is over). */
    fun minutesLeft(): Int = ((remainingMillis() + 59_999L) / 60_000L).toInt()

    fun signalExpired() { _expired.value = true }

    fun consumeExpired() { _expired.value = false }

    private fun closeSession(now: Long) {
        val p = prefs() ?: return
        val start = p.getLong(KEY_START, 0L)
        if (start > 0L) credit(start, now)
    }

    private fun credit(start: Long, end: Long) {
        val p = prefs() ?: return
        val used = minOf(TRIAL_MS, p.getLong(KEY_USED, 0L) + maxOf(0L, end - start))
        p.edit().putLong(KEY_USED, used).putLong(KEY_START, 0L).apply()
    }
}
