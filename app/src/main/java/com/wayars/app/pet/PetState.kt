package com.wayars.app.pet

import android.content.Context
import com.wayars.app.domain.model.Verdict
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * The WayArs pet (a chameleon). It grows from SMART decisions, not from the
 * number of orders: accepting a good order and rejecting a bad one both feed
 * it, so the pet never nudges a driver into taking unprofitable work.
 *
 * Stored locally in SharedPreferences (same approach as FreeTrial /
 * ActiveSessionTracker); nothing leaves the phone.
 */
object PetState {

    enum class Stage { EGG, BABY, TEEN, PRO, LEGEND }

    /** A short-lived reaction to something that just happened. */
    enum class Reaction { NONE, GOOD_ACCEPTED, SMART_REJECT, ENERGY, LEVEL_UP, FULL, BAD_ACCEPTED }

    data class Snapshot(
        val xp: Int = 0,
        val level: Int = 1,
        val stage: Stage = Stage.EGG,
        val levelStartXp: Int = 0,
        val nextLevelXp: Int = xpForLevel(2),
        val lastActivityAt: Long = 0L,
        val reaction: Reaction = Reaction.NONE,
        val reactionAt: Long = 0L,
        val lastGain: Int = 0
    ) {
        /** 0..1 progress towards the next level. */
        val progress: Float
            get() = if (nextLevelXp <= levelStartXp) 1f
            else ((xp - levelStartXp).toFloat() / (nextLevelXp - levelStartXp)).coerceIn(0f, 1f)
    }

    private const val PREFS = "pet_state"
    private const val KEY_XP = "xp"
    private const val KEY_LAST = "last_activity"
    private const val KEY_DAILY = "daily_bonus_epoch_day"
    private const val KEY_STREAK = "streak_days"
    private const val KEY_DAY = "decision_epoch_day"
    private const val KEY_DAY_XP = "decision_xp_today"
    private const val KEY_TEASE_DAY = "tease_day"
    private const val KEY_BAD_ACCEPTED = "bad_accepted_today"
    private const val KEY_REJECT_STREAK = "reject_streak"

    // Balance: growth must take MONTHS, not a week. An active driver earns at most
    // DAILY_DECISION_CAP from decisions plus the daily bonus (5, up to 10 with a streak),
    // i.e. ~60–70 a day. With level n starting at 25·(n−1)² that gives roughly:
    //   hatch (lv 2) on day 1 · teen (lv 5) after ~1 week · pro (lv 10) after ~1 month
    //   · legend (lv 20) after ~4–6 months of regular work. Levels keep going after 20.
    const val XP_GOOD_ACCEPTED = 3
    const val XP_AVERAGE_ACCEPTED = 2
    const val XP_BAD_ACCEPTED = 1
    const val XP_BAD_REJECTED = 3
    const val XP_AVERAGE_REJECTED = 1
    const val DAILY_DECISION_CAP = 60
    const val XP_DAILY_ACTIVE = 5
    const val MAX_STREAK_BONUS = 5

    private val _state = MutableStateFlow(Snapshot())
    val state: StateFlow<Snapshot> = _state

    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        val p = prefs() ?: return
        _state.value = build(p.getInt(KEY_XP, 0), p.getLong(KEY_LAST, 0L), Reaction.NONE, 0L, 0)
    }

    /** Accept / Reject / Close on the overlay. */
    @Synchronized
    fun onDecision(context: Context, accepted: Boolean, verdict: Verdict) {
        val p = prefs(context) ?: return
        val raw = when {
            accepted && verdict == Verdict.GOOD -> XP_GOOD_ACCEPTED
            accepted && verdict == Verdict.AVERAGE -> XP_AVERAGE_ACCEPTED
            accepted -> XP_BAD_ACCEPTED
            verdict == Verdict.BAD -> XP_BAD_REJECTED
            verdict == Verdict.AVERAGE -> XP_AVERAGE_REJECTED
            else -> 0
        }
        // Daily cap: a 14-hour marathon doesn't grow the pet faster than a normal day.
        val today = epochDay()
        val usedToday = if (p.getLong(KEY_DAY, -1L) == today) p.getInt(KEY_DAY_XP, 0) else 0
        val gain = raw.coerceAtMost((DAILY_DECISION_CAP - usedToday).coerceAtLeast(0))
        p.edit().putLong(KEY_DAY, today).putInt(KEY_DAY_XP, usedToday + gain).apply()
        // For Wayo's teasing: red orders taken today and rejections in a row.
        val badToday = (if (p.getLong(KEY_TEASE_DAY, -1L) == today) p.getInt(KEY_BAD_ACCEPTED, 0) else 0) +
            if (accepted && verdict == Verdict.BAD) 1 else 0
        val streak = if (accepted) 0 else p.getInt(KEY_REJECT_STREAK, 0) + 1
        p.edit().putLong(KEY_TEASE_DAY, today).putInt(KEY_BAD_ACCEPTED, badToday).putInt(KEY_REJECT_STREAK, streak).apply()
        val reaction = when {
            raw > 0 && gain == 0 -> Reaction.FULL
            accepted && verdict == Verdict.GOOD -> Reaction.GOOD_ACCEPTED
            !accepted && verdict == Verdict.BAD -> Reaction.SMART_REJECT
            accepted && verdict == Verdict.BAD -> Reaction.BAD_ACCEPTED
            gain > 0 -> Reaction.ENERGY
            else -> Reaction.NONE
        }
        addXp(context, gain, reaction)
    }

    /** Red (unprofitable) orders accepted today. */
    fun badAcceptedToday(context: Context): Int {
        val p = prefs(context) ?: return 0
        return if (p.getLong(KEY_TEASE_DAY, -1L) == epochDay()) p.getInt(KEY_BAD_ACCEPTED, 0) else 0
    }

    /** Orders rejected in a row (any verdict) since the last accepted one. */
    fun rejectStreak(context: Context): Int = prefs(context)?.getInt(KEY_REJECT_STREAK, 0) ?: 0

    /**
     * Once per calendar day, switching scanning on feeds the pet a little: 5, plus 1 for
     * every consecutive working day before it (up to +5). Missing a day resets the streak.
     */
    @Synchronized
    fun onScanningStarted(context: Context) {
        val p = prefs(context) ?: return
        val today = epochDay()
        val last = p.getLong(KEY_DAILY, -1L)
        if (last == today) return
        val streak = if (last == today - 1) p.getInt(KEY_STREAK, 0) + 1 else 1
        p.edit().putLong(KEY_DAILY, today).putInt(KEY_STREAK, streak).apply()
        addXp(context, XP_DAILY_ACTIVE + (streak - 1).coerceAtMost(MAX_STREAK_BONUS), Reaction.ENERGY)
    }

    private fun addXp(context: Context, gain: Int, reaction: Reaction) {
        val p = prefs(context) ?: return
        if (gain <= 0 && reaction == Reaction.NONE) return
        val before = _state.value
        val xp = p.getInt(KEY_XP, 0) + gain
        val now = System.currentTimeMillis()
        p.edit().putInt(KEY_XP, xp).putLong(KEY_LAST, now).apply()
        val next = build(xp, now, reaction, now, gain)
        _state.value = if (next.level > before.level) next.copy(reaction = Reaction.LEVEL_UP) else next
    }

    private fun build(xp: Int, last: Long, reaction: Reaction, reactionAt: Long, gain: Int): Snapshot {
        val level = levelFor(xp)
        return Snapshot(
            xp = xp,
            level = level,
            stage = stageFor(level),
            levelStartXp = xpForLevel(level),
            nextLevelXp = xpForLevel(level + 1),
            lastActivityAt = last,
            reaction = reaction,
            reactionAt = reactionAt,
            lastGain = gain
        )
    }

    /** Level n starts at 25·(n−1)² XP: 2 → 25, 5 → 400, 10 → 2 025, 20 → 9 025. */
    fun xpForLevel(level: Int): Int = 25 * (level - 1) * (level - 1)

    fun levelFor(xp: Int): Int = floor(sqrt(xp.coerceAtLeast(0) / 25.0)).toInt() + 1

    fun stageFor(level: Int): Stage = when {
        level <= 1 -> Stage.EGG
        level <= 4 -> Stage.BABY
        level <= 9 -> Stage.TEEN
        level <= 19 -> Stage.PRO
        else -> Stage.LEGEND
    }

    /** Days since 1970 in the phone's own time zone, so "a day" ends at local midnight. */
    private fun epochDay(): Long {
        val now = System.currentTimeMillis()
        val offset = java.util.TimeZone.getDefault().getOffset(now)
        return Math.floorDiv(now + offset, 86_400_000L)
    }

    private fun prefs(context: Context? = null) =
        (context?.applicationContext ?: appContext)?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
