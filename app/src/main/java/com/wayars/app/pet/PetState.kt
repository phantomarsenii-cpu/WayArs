package com.wayars.app.pet

import android.content.Context
import com.wayars.app.domain.model.Verdict
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Calendar
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
    enum class Reaction { NONE, GOOD_ACCEPTED, SMART_REJECT, ENERGY, LEVEL_UP }

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
    private const val KEY_DAILY = "daily_bonus_day"

    const val XP_GOOD_ACCEPTED = 10
    const val XP_AVERAGE_ACCEPTED = 5
    const val XP_BAD_ACCEPTED = 2
    const val XP_BAD_REJECTED = 10
    const val XP_AVERAGE_REJECTED = 3
    const val XP_DAILY_ACTIVE = 5

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
        val gain = when {
            accepted && verdict == Verdict.GOOD -> XP_GOOD_ACCEPTED
            accepted && verdict == Verdict.AVERAGE -> XP_AVERAGE_ACCEPTED
            accepted -> XP_BAD_ACCEPTED
            verdict == Verdict.BAD -> XP_BAD_REJECTED
            verdict == Verdict.AVERAGE -> XP_AVERAGE_REJECTED
            else -> 0
        }
        val reaction = when {
            accepted && verdict == Verdict.GOOD -> Reaction.GOOD_ACCEPTED
            !accepted && verdict == Verdict.BAD -> Reaction.SMART_REJECT
            gain > 0 -> Reaction.ENERGY
            else -> Reaction.NONE
        }
        addXp(context, gain, reaction)
    }

    /** Once per calendar day, switching scanning on feeds the pet a little. */
    @Synchronized
    fun onScanningStarted(context: Context) {
        val p = prefs(context) ?: return
        val today = dayKey()
        if (p.getInt(KEY_DAILY, 0) == today) return
        p.edit().putInt(KEY_DAILY, today).apply()
        addXp(context, XP_DAILY_ACTIVE, Reaction.ENERGY)
    }

    private fun addXp(context: Context, gain: Int, reaction: Reaction) {
        val p = prefs(context) ?: return
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

    /** Level n starts at 10·(n−1)² XP: 2 → 10, 5 → 160, 10 → 810, 20 → 3610. */
    fun xpForLevel(level: Int): Int = 10 * (level - 1) * (level - 1)

    fun levelFor(xp: Int): Int = floor(sqrt(xp.coerceAtLeast(0) / 10.0)).toInt() + 1

    fun stageFor(level: Int): Stage = when {
        level <= 1 -> Stage.EGG
        level <= 4 -> Stage.BABY
        level <= 9 -> Stage.TEEN
        level <= 19 -> Stage.PRO
        else -> Stage.LEGEND
    }

    private fun dayKey(): Int {
        val c = Calendar.getInstance()
        return c.get(Calendar.YEAR) * 1000 + c.get(Calendar.DAY_OF_YEAR)
    }

    private fun prefs(context: Context? = null) =
        (context?.applicationContext ?: appContext)?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
