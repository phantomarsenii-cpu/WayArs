package com.wayars.app.features

import com.wayars.app.domain.model.Verdict
import com.wayars.app.domain.repository.OrderRecord
import java.util.Calendar

/** Part of the day used to compare services ("Thursday evening"). */
enum class DayPart(val fromHour: Int) { MORNING(6), DAY(11), EVENING(16), NIGHT(22);
    companion object {
        fun of(hour: Int): DayPart = when (hour) {
            in 6..10 -> MORNING
            in 11..15 -> DAY
            in 16..21 -> EVENING
            else -> NIGHT
        }
    }
}

/** Earnings of a period measured against the whole time "Active" was on (waiting included). */
data class ShiftEarnings(
    val gross: Double,
    val takeHome: Double,
    val orders: Int,
    val activeMillis: Long,
    /** Pro-rated share of the partner's weekly fee for the period. */
    val partnerFee: Double
) {
    val net: Double get() = takeHome - partnerFee
    /** Null when there's too little active time (< 15 min) for a meaningful hourly figure. */
    val perHour: Double? get() = if (activeMillis < 15 * 60_000L) null else net / (activeMillis / 3_600_000.0)
}

/** What one service paid per hour in one weekday + part of the day. */
data class SlotRate(
    val sourcePackage: String,
    val dayOfWeek: Int, // Calendar.MONDAY..SUNDAY
    val dayPart: DayPart,
    val net: Double,
    val millis: Long,
    val orders: Int
) {
    val perHour: Double get() = if (millis <= 0) 0.0 else net / (millis / 3_600_000.0)
}

/** One service over the whole analysed window. */
data class ServiceRate(val sourcePackage: String, val net: Double, val millis: Long, val orders: Int) {
    val perHour: Double get() = if (millis <= 0) 0.0 else net / (millis / 3_600_000.0)
}

/** Records, streaks and badges. */
data class Achievements(
    val currentSmartStreak: Int,
    val bestSmartStreak: Int,
    val recordPerHour: Double?,
    val bestDay: Double?,
    val bestOrder: Double?,
    val totalOrders: Int,
    val totalActiveHours: Double
)

object Earnings {

    private const val HOUR = 3_600_000L
    private const val DAY = 86_400_000L

    fun startOfDay(millis: Long): Long = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun overlap(intervals: List<LongRange>, from: Long, to: Long): Long =
        if (to <= from) 0L else intervals.sumOf { maxOf(0L, minOf(it.last, to) - maxOf(it.first, from)) }

    /** Orders in [from, to) and active time in it; the weekly fee is pro-rated by calendar days. */
    fun period(
        orders: List<OrderRecord>,
        intervals: List<LongRange>,
        from: Long,
        to: Long,
        weeklyFee: Double
    ): ShiftEarnings {
        val inRange = orders.filter { it.timestampMillis in from until to && it.accepted != false }
        val days = maxOf(1L, Math.ceil((to - from).toDouble() / DAY).toLong())
        return ShiftEarnings(
            gross = inRange.sumOf { it.evaluation.earnings },
            takeHome = inRange.sumOf { it.evaluation.takeHome },
            orders = inRange.size,
            activeMillis = overlap(intervals, from, to),
            partnerFee = if (weeklyFee > 0) weeklyFee * days / 7.0 else 0.0
        )
    }

    /** Time an order "cost": its own trip time plus the active waiting since the previous order ended. */
    private fun driveMillis(o: OrderRecord): Long {
        val minutes = if (o.evaluation.timeMinutes > 0) o.evaluation.timeMinutes
        else (o.evaluation.distanceKm * 2.5).coerceIn(3.0, 120.0)
        return (minutes * 60_000).toLong()
    }

    /**
     * Splits the active time between orders: each order gets its trip time plus the active
     * waiting before it (since the previous order ended, at most 2 h back), so services can be
     * compared per real hour even when the driver runs several apps at once.
     */
    fun slotRates(orders: List<OrderRecord>, intervals: List<LongRange>): List<SlotRate> {
        val sorted = orders.filter { it.accepted != false }.sortedBy { it.timestampMillis }
        val acc = LinkedHashMap<Triple<String, Int, DayPart>, DoubleArray>() // net, millis, count
        val cal = Calendar.getInstance()
        var prevEnd = Long.MIN_VALUE
        for (o in sorted) {
            val drive = driveMillis(o)
            val idleFrom = maxOf(prevEnd, o.timestampMillis - 2 * HOUR)
            val idle = overlap(intervals, idleFrom, o.timestampMillis)
            prevEnd = maxOf(prevEnd, o.timestampMillis + drive)
            val pkg = o.sourcePackage ?: continue
            cal.timeInMillis = o.timestampMillis
            val key = Triple(pkg, cal.get(Calendar.DAY_OF_WEEK), DayPart.of(cal.get(Calendar.HOUR_OF_DAY)))
            val a = acc.getOrPut(key) { DoubleArray(3) }
            a[0] += o.evaluation.takeHome; a[1] += (idle + drive).toDouble(); a[2] += 1.0
        }
        return acc.map { (k, a) -> SlotRate(k.first, k.second, k.third, a[0], a[1].toLong(), a[2].toInt()) }
    }

    fun serviceRates(slots: List<SlotRate>): List<ServiceRate> =
        slots.groupBy { it.sourcePackage }.map { (pkg, list) ->
            ServiceRate(pkg, list.sumOf { it.net }, list.sumOf { it.millis }, list.sumOf { it.orders })
        }.filter { it.orders >= 2 && it.millis >= 20 * 60_000L }.sortedByDescending { it.perHour }

    /**
     * Weekday + part-of-day slots where at least two services have enough data to compare,
     * busiest first. Each entry lists the services best first.
     */
    fun comparisons(slots: List<SlotRate>): List<List<SlotRate>> =
        slots.filter { it.orders >= 2 && it.millis >= 20 * 60_000L }
            .groupBy { it.dayOfWeek to it.dayPart }
            .values
            .filter { it.size >= 2 }
            .sortedByDescending { list -> list.sumOf { it.millis } }
            .map { list -> list.sortedByDescending { it.perHour } }

    /** Best single slots (service + weekday + part of the day) by real per-hour earnings. */
    fun bestSlots(slots: List<SlotRate>, limit: Int = 3): List<SlotRate> =
        slots.filter { it.orders >= 3 && it.millis >= 45 * 60_000L }.sortedByDescending { it.perHour }.take(limit)

    fun achievements(orders: List<OrderRecord>, intervals: List<LongRange>, now: Long = System.currentTimeMillis()): Achievements {
        val accepted = orders.filter { it.accepted != false }
        val byDay = accepted.groupBy { startOfDay(it.timestampMillis) }.toSortedMap()
        // Working days in a row (days off don't break it) without a single accepted unprofitable order.
        var current = 0
        var best = 0
        for ((_, list) in byDay) {
            if (list.any { it.evaluation.verdict == Verdict.BAD }) current = 0 else current++
            best = maxOf(best, current)
        }
        var recordPerHour: Double? = null
        var bestDay: Double? = null
        for ((day, list) in byDay) {
            val net = list.sumOf { it.evaluation.takeHome }
            bestDay = maxOf(bestDay ?: net, net)
            val active = overlap(intervals, day, day + DAY)
            if (active >= HOUR) {
                val rate = net / (active / HOUR.toDouble())
                recordPerHour = maxOf(recordPerHour ?: rate, rate)
            }
        }
        return Achievements(
            currentSmartStreak = current,
            bestSmartStreak = best,
            recordPerHour = recordPerHour,
            bestDay = bestDay,
            bestOrder = accepted.maxOfOrNull { it.evaluation.takeHome },
            totalOrders = accepted.size,
            totalActiveHours = intervals.sumOf { it.last - it.first } / HOUR.toDouble()
        )
    }
}
