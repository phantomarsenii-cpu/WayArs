package com.wayars.app.features

import com.wayars.app.R

/**
 * Rewards for growing Wayo (smart decisions only, never order volume):
 *  - card themes for the overlay and the widget, unlocked by level;
 *  - Wayo's abilities per stage: tip of the day (5+), weekly forecast (10+),
 *    "better than X% of your orders" on the card (20+).
 */
enum class CardTheme(
    val minLevel: Int,
    val nameRes: Int,
    val bgTop: Long,
    val bgBottom: Long,
    val accent: Long,
    val frame: Long
) {
    CLASSIC(1, R.string.theme_classic, 0xFF0E141B, 0xFF0E141B, 0xFF00E676, 0xFF243040),
    NEON(5, R.string.theme_neon, 0xFF0A1024, 0xFF1C0B30, 0xFF00E5FF, 0xFF7C4DFF),
    CARBON(10, R.string.theme_carbon, 0xFF1B1E22, 0xFF0D0F12, 0xFFCFD8DC, 0xFF78909C),
    GOLD(15, R.string.theme_gold, 0xFF241A08, 0xFF120D04, 0xFFFFC94D, 0xFFB8860B),
    LEGEND(20, R.string.theme_legend, 0xFF2A0F45, 0xFF0E0618, 0xFFFFD54F, 0xFFB388FF);

    fun unlocked(level: Int) = level >= minLevel

    companion object {
        fun of(name: String?): CardTheme = values().firstOrNull { it.name == name } ?: CLASSIC
        /** The theme that unlocks exactly at [level], if any (for the level-up message). */
        fun unlockedAt(level: Int): CardTheme? = values().firstOrNull { it.minLevel == level && it != CLASSIC }
    }
}

object Abilities {
    const val TIP_OF_DAY_LEVEL = 5
    const val WEEKLY_FORECAST_LEVEL = 10
    const val PERCENTILE_LEVEL = 20
}

/**
 * Where this order stands among the driver's own recent orders (by net rate per km).
 * Kept in memory by the overlay service; filled from history and on every accepted order.
 */
object OrderPercentile {
    private val rates = ArrayList<Double>()
    private const val MIN_HISTORY = 20

    @Synchronized fun load(values: List<Double>) { rates.clear(); rates.addAll(values.takeLast(500)) }
    @Synchronized fun add(rate: Double) { rates.add(rate); if (rates.size > 500) rates.removeAt(0) }

    /** 0..99, or null when there isn't enough history yet. */
    @Synchronized fun of(rate: Double): Int? {
        if (rates.size < MIN_HISTORY) return null
        val below = rates.count { it < rate }
        return (below * 100 / rates.size).coerceIn(0, 99)
    }
}
