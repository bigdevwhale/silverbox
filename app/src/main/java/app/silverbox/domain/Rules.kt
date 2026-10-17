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

    /** Clips on a cassette ("50 feet") and how long each one runs. */
    const val CASSETTE = 50
    const val CLIP_MS = 3_000L

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

    /** The three seconds of a clip worth keeping: from the middle, where the moment usually is. */
    fun clipStart(durationMs: Long): Long = ((durationMs - CLIP_MS) / 2).coerceAtLeast(0)

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
