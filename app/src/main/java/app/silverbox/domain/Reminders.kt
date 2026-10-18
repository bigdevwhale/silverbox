package app.silverbox.domain

import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

/**
 * When to nudge for a frame: [count] times a day between [from] and [to] (minutes after midnight). The
 * window is cut into equal slots and each reminder falls somewhere in the middle of its slot, a little
 * differently every day, so it never feels like an alarm clock.
 */
object Reminders {
    val COUNTS = listOf(0, 1, 2, 3, 4, 5)

    /** No nudge if something was shot this recently. */
    const val QUIET_AFTER_SHOT = 90 * 60_000L

    fun times(day: LocalDate, count: Int, from: Int, to: Int, zone: ZoneId): List<Long> {
        if (count <= 0 || to - from < count * 10) return emptyList()
        val slot = (to - from) / count
        val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
        return (0 until count).map { i ->
            val r = Random(day.toEpochDay() * 31 + i)
            val minute = from + i * slot + (slot * 0.2).toInt() + r.nextInt((slot * 0.6).toInt().coerceAtLeast(1))
            start + minute * 60_000L
        }
    }

    /** The next reminder after [now], today or tomorrow; null when they are off. */
    fun next(now: Long, count: Int, from: Int, to: Int, zone: ZoneId): Long? {
        val today = Rules.day(now, zone)
        return (times(today, count, from, to, zone) + times(today.plusDays(1), count, from, to, zone)).firstOrNull { it > now }
    }
}
