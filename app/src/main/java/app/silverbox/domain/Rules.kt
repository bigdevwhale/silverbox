package app.silverbox.domain

import app.silverbox.data.Frame
import app.silverbox.data.Roll
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The lab's terms: how long things take and what film can stand. */
object Rules {
    const val HOUR = 3_600_000L
    const val DAY = 24 * HOUR

    /** Ordinary development: a day. */
    const val STANDARD = DAY
    /** Express: an hour, once a week. */
    const val EXPRESS = HOUR
    const val EXPRESS_EVERY = 7 * DAY

    /** Film keeps this long in the camera before it starts to drift. */
    const val FRESH_DAYS = 60
    /** And is as expired as it gets this much later. */
    const val STALE_DAYS = 120

    /** Clips on a cassette ("50 feet"). */
    const val CASSETTE = 50

    val SIZES = listOf(24, 36)

    /** When the last express order was placed, or null. */
    fun lastExpress(rolls: List<Roll>): Long? = rolls.filter { it.express }.mapNotNull { it.sentAt }.maxOrNull()

    /** When express is next on offer; now or earlier means it is. */
    fun expressFrom(rolls: List<Roll>): Long = (lastExpress(rolls) ?: Long.MIN_VALUE / 2) + EXPRESS_EVERY

    fun expressOpen(rolls: List<Roll>, now: Long, unlimited: Boolean = false) = unlimited || now >= expressFrom(rolls)

    /** 0 while fresh, rising to 1 as the film sits in the camera past its date. */
    fun expired(loadedAt: Long, at: Long): Float {
        val days = (at - loadedAt).toDouble() / DAY
        return ((days - FRESH_DAYS) / (STALE_DAYS - FRESH_DAYS)).toFloat().coerceIn(0f, 1f)
    }

    /** Days left before the film in the camera starts to drift; negative once it has. */
    fun freshDaysLeft(loadedAt: Long, now: Long): Int = FRESH_DAYS - ((now - loadedAt) / DAY).toInt()

    /** The next order number: four digits, counting on from the last. */
    fun nextOrder(last: Int?, seed: Long): Int = if (last == null || last == 0) 400 + (seed % 500).toInt().let { if (it < 0) -it else it } else last + 1

    /** What still fits: the first [room] of [found] by time. */
    fun <T> fit(found: List<T>, room: Int): Pair<List<T>, List<T>> = found.take(room.coerceAtLeast(0)) to found.drop(room.coerceAtLeast(0))

    /** The common shapes a phone films in, across. */
    private val SHAPES = listOf(16 to 9, 4 to 3, 3 to 2, 1 to 1, 2 to 1, 21 to 9)

    /** The common shape nearest to [w] by [h], turned the same way: 1080 by 1920 is 9 to 16. */
    fun shape(w: Int, h: Int): Pair<Int, Int> {
        val r = maxOf(w, h).toFloat() / minOf(w, h)
        val (a, b) = SHAPES.minBy { (a, b) -> kotlin.math.abs(a.toFloat() / b - r) }
        return if (w >= h) a to b else b to a
    }

    /** The pictures of one trip to the camera, in the order they were added: from [since], each no more than [gap] after the one before. */
    fun <T> trip(found: List<T>, since: Long, gap: Long, at: (T) -> Long): List<T> {
        var last = since
        return found.takeWhile { f -> (at(f) - last <= gap).also { if (it) last = maxOf(last, at(f)) } }
    }

    fun day(ms: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()

    /** Prints from this day in earlier years, newest year first. */
    fun yearsAgo(frames: List<Frame>, today: LocalDate, zone: ZoneId): List<Frame> = frames
        .filter { it.print.isNotEmpty() && !it.video }
        .filter {
            val d = day(it.takenAt, zone)
            d.year < today.year && d.monthValue == today.monthValue && d.dayOfMonth == today.dayOfMonth
        }
        .sortedByDescending { it.takenAt }
}
