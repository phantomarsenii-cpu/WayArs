package com.wayars.app.data.prefs

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Small driver settings that background components (accessibility service, alarm receivers,
 * the home-screen widget) need synchronously: partner (fleet) commission and weekly fee,
 * evening/weekly summaries, verdict on the watch. Kept in SharedPreferences and mirrored
 * into StateFlows so Compose can observe them too.
 */
object DriverPrefs {
    private const val PREFS = "driver_prefs"
    private const val KEY_COMMISSION = "partner_commission_percent"
    private const val KEY_WEEKLY_FEE = "partner_weekly_fee"
    private const val KEY_SUMMARIES = "summaries_enabled"
    private const val KEY_WATCH = "watch_verdict_enabled"
    private const val KEY_INVITE_CODE = "invite_code"
    private const val KEY_THEME = "card_theme"

    private val _commissionPercent = MutableStateFlow(0.0)
    val commissionPercent: StateFlow<Double> = _commissionPercent
    private val _weeklyFee = MutableStateFlow(0.0)
    val weeklyFee: StateFlow<Double> = _weeklyFee
    private val _summariesEnabled = MutableStateFlow(true)
    val summariesEnabled: StateFlow<Boolean> = _summariesEnabled
    private val _watchEnabled = MutableStateFlow(false)
    val watchEnabled: StateFlow<Boolean> = _watchEnabled

    private val _cardTheme = MutableStateFlow("CLASSIC")
    /** Name of the chosen [com.wayars.app.features.CardTheme] for the overlay card and widget. */
    val cardTheme: StateFlow<String> = _cardTheme

    @Volatile private var loaded = false

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun init(context: Context) {
        if (loaded) return
        val p = prefs(context)
        _commissionPercent.value = p.getFloat(KEY_COMMISSION, 0f).toDouble()
        _weeklyFee.value = p.getFloat(KEY_WEEKLY_FEE, 0f).toDouble()
        _summariesEnabled.value = p.getBoolean(KEY_SUMMARIES, true)
        _watchEnabled.value = p.getBoolean(KEY_WATCH, false)
        _cardTheme.value = p.getString(KEY_THEME, "CLASSIC") ?: "CLASSIC"
        loaded = true
    }

    fun setPartner(context: Context, commissionPercent: Double, weeklyFee: Double) {
        val c = commissionPercent.coerceIn(0.0, 90.0)
        val f = weeklyFee.coerceIn(0.0, 1_000_000.0)
        prefs(context).edit().putFloat(KEY_COMMISSION, c.toFloat()).putFloat(KEY_WEEKLY_FEE, f.toFloat()).apply()
        _commissionPercent.value = c
        _weeklyFee.value = f
    }

    fun setSummariesEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_SUMMARIES, enabled).apply()
        _summariesEnabled.value = enabled
    }

    fun setWatchEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_WATCH, enabled).apply()
        _watchEnabled.value = enabled
    }

    fun setCardTheme(context: Context, name: String) {
        prefs(context).edit().putString(KEY_THEME, name).apply()
        _cardTheme.value = name
    }

    /** A short personal code put into invite links (no account needed, never leaves the phone otherwise). */
    fun inviteCode(context: Context): String {
        val p = prefs(context)
        p.getString(KEY_INVITE_CODE, null)?.let { return it }
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val rnd = java.security.SecureRandom()
        val code = (1..6).map { alphabet[rnd.nextInt(alphabet.length)] }.joinToString("")
        p.edit().putString(KEY_INVITE_CODE, code).apply()
        return code
    }
}
