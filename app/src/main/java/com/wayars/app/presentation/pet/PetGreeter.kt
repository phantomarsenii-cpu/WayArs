package com.wayars.app.presentation.pet

import android.content.Context
import com.wayars.app.R
import com.wayars.app.appContainer
import com.wayars.app.data.prefs.DriverPrefs
import com.wayars.app.domain.model.Currency
import com.wayars.app.features.Earnings
import com.wayars.app.service.accessibility.ActiveSessionTracker
import com.wayars.app.util.CurrencyFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * What Wayo says when the app is opened: praise from today's real numbers when there are any
 * (record pace, smart-day streak, today's take-home), "I missed you" after 2+ days away, or a
 * greeting for the time of day. At most once every 20 minutes so it doesn't get annoying.
 */
object PetGreeter {
    private const val PREFS = "pet_greeter"
    private const val KEY_AT = "last_greeting_at"
    private const val MIN_GAP_MS = 20 * 60_000L
    private const val AWAY_MS = 2L * 24 * 60 * 60 * 1000

    /** "Thursday evening — Bolt pays you best, 41 zł/h" from the last 60 days, or null without enough data. */
    suspend fun tipOfDay(context: Context, now: Long = System.currentTimeMillis()): String? = withContext(Dispatchers.IO) {
        runCatching {
            val c = context.appContainer()
            val from = now - 60L * 86_400_000L
            val slots = Earnings.slotRates(c.orderRepository.acceptedSince(from), ActiveSessionTracker.intervals(from, now))
            val dow = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
            val best = Earnings.bestSlots(slots.filter { it.dayOfWeek == dow }, 1).firstOrNull() ?: return@runCatching null
            val cur = runCatching { c.settingsRepository.currency.first() }.getOrDefault(Currency.default)
            context.getString(
                R.string.pet_tip_day,
                slotName(context, best.dayOfWeek, best.dayPart),
                com.wayars.app.features.AppNames.label(context, best.sourcePackage),
                context.getString(R.string.summary_part_hour, CurrencyFormatter.format(best.perHour, cur, 0))
            )
        }.getOrNull()
    }

    fun slotName(context: Context, dayOfWeek: Int, part: com.wayars.app.features.DayPart): String {
        val cal = Calendar.getInstance().apply { set(Calendar.DAY_OF_WEEK, dayOfWeek) }
        val day = java.text.SimpleDateFormat("EEEE", java.util.Locale.getDefault()).format(cal.time)
        val p = context.getString(
            when (part) {
                com.wayars.app.features.DayPart.MORNING -> R.string.daypart_morning
                com.wayars.app.features.DayPart.DAY -> R.string.daypart_day
                com.wayars.app.features.DayPart.EVENING -> R.string.daypart_evening
                com.wayars.app.features.DayPart.NIGHT -> R.string.daypart_night
            }
        )
        return context.getString(R.string.stats_slot, day, p)
    }

    suspend fun next(context: Context, lastActivityAt: Long): String? = withContext(Dispatchers.IO) {
        runCatching {
            val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val now = System.currentTimeMillis()
            if (now - p.getLong(KEY_AT, 0L) < MIN_GAP_MS) return@runCatching null
            p.edit().putLong(KEY_AT, now).apply()
            val r = context.resources
            fun random(id: Int) = r.getStringArray(id).let { it[(now / 1000 % it.size).toInt()] }

            val c = context.appContainer()
            val today = Earnings.startOfDay(now)
            val orders = c.orderRepository.acceptedSince(today)
            if (orders.isNotEmpty()) {
                val cur = runCatching { c.settingsRepository.currency.first() }.getOrDefault(Currency.default)
                val e = Earnings.period(orders, ActiveSessionTracker.intervals(today, now), today, now + 1, DriverPrefs.weeklyFee.value)
                val a = Earnings.achievements(c.orderRepository.acceptedSince(today - 400L * 86_400_000L), ActiveSessionTracker.intervals(0L, now))
                val ph = e.perHour
                val record = a.recordPerHour
                return@runCatching when {
                    ph != null && record != null && e.activeMillis >= 3_600_000L && ph >= record * 0.999 ->
                        context.getString(R.string.pet_praise_record, context.getString(R.string.summary_part_hour, CurrencyFormatter.format(ph, cur, 0)))
                    a.currentSmartStreak >= 3 && now / 1000 % 2 == 0L ->
                        context.getString(R.string.pet_praise_streak, a.currentSmartStreak)
                    else -> context.getString(R.string.pet_praise_today, e.orders, CurrencyFormatter.format(e.net, cur))
                }
            }
            if (lastActivityAt > 0 && now - lastActivityAt > AWAY_MS) return@runCatching random(R.array.pet_greet_back)
            // Teen and older: a tip for today from the driver's own data.
            if (com.wayars.app.pet.PetState.state.value.level >= com.wayars.app.features.Abilities.TIP_OF_DAY_LEVEL) {
                tipOfDay(context, now)?.let { return@runCatching it }
            }
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            random(
                when (hour) {
                    in 5..10 -> R.array.pet_greet_morning
                    in 11..16 -> R.array.pet_greet_day
                    in 17..22 -> R.array.pet_greet_evening
                    else -> R.array.pet_greet_night
                }
            )
        }.getOrNull()
    }
}
