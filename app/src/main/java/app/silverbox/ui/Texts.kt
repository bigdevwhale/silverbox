package app.silverbox.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import app.silverbox.R
import app.silverbox.data.Frame
import app.silverbox.data.Roll
import app.silverbox.domain.Stock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Words and dates, in the app's language. Plain functions over a [Context] so receivers can use them too. */
object T {
    private fun locale(c: Context): Locale = c.resources.configuration.locales[0]

    fun stockDesc(c: Context, s: Stock): String = c.getString(
        when (s) {
            Stock.SUNNY -> R.string.stock_sunny
            Stock.EVERY -> R.string.stock_every
            Stock.NIGHT -> R.string.stock_night
            Stock.SILVER -> R.string.stock_silver
            Stock.SLIDE -> R.string.stock_slide
            Stock.HOME8 -> R.string.stock_home8
            Stock.SILVER8 -> R.string.stock_silver8
            Stock.VHS -> R.string.stock_vhs
        }
    )

    fun local(ms: Long, zone: ZoneId = ZoneId.systemDefault()) = Instant.ofEpochMilli(ms).atZone(zone)

    /** "17 Oct 2026". */
    fun date(c: Context, ms: Long): String = local(ms).format(DateTimeFormatter.ofPattern("d MMM yyyy", locale(c)))

    /** "17 Oct". */
    fun day(c: Context, ms: Long): String = local(ms).format(DateTimeFormatter.ofPattern("d MMM", locale(c)))

    /** "17.10.26", the way the lab stamps it. */
    fun stampDate(ms: Long): String = local(ms).format(DateTimeFormatter.ofPattern("dd.MM.yy"))

    fun time(c: Context, ms: Long): String = local(ms).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale(c)))

    /** "today at 14:20", "tomorrow at 9:00", "on 21 Oct at 9:00". */
    fun whenAt(c: Context, ms: Long, today: LocalDate = LocalDate.now()): String {
        val d = local(ms).toLocalDate()
        val t = time(c, ms)
        return when (d) {
            today -> c.getString(R.string.today_at, t)
            today.plusDays(1) -> c.getString(R.string.tomorrow_at, t)
            else -> c.getString(R.string.date_at, day(c, ms), t)
        }
    }

    /** "12 Sep – 3 Oct 2026", the span a roll was shot over. */
    fun span(c: Context, frames: List<Frame>): String {
        if (frames.isEmpty()) return ""
        val a = frames.minOf { it.takenAt }
        val b = frames.maxOf { it.takenAt }
        val da = local(a).toLocalDate()
        val db = local(b).toLocalDate()
        return when {
            da == db -> date(c, a)
            da.year == db.year -> day(c, a) + " – " + date(c, b)
            else -> date(c, a) + " – " + date(c, b)
        }
    }

    /** The name on a roll's label: its own, or the month it was loaded. */
    fun rollName(c: Context, r: Roll): String = r.name.ifBlank {
        local(r.loadedAt).format(DateTimeFormatter.ofPattern("LLLL yyyy", locale(c))).replaceFirstChar { it.titlecase(locale(c)) }
    }

    fun order(n: Int): String = "%04d".format(n)

    fun plural(c: Context, id: Int, n: Int): String = c.resources.getQuantityString(id, n, n)
}

@Composable
fun ctx(): Context = LocalContext.current
