package app.silverbox

import app.silverbox.data.Backup
import app.silverbox.data.Frame
import app.silverbox.data.Roll
import app.silverbox.develop.DateStamp
import app.silverbox.develop.Defect
import app.silverbox.develop.Film
import app.silverbox.domain.Reminders
import app.silverbox.domain.Rules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SilverboxTest {
    private val day = Rules.DAY

    private fun roll(sent: Long? = null, ready: Long? = null, developed: Boolean = false, opened: Boolean = false, express: Boolean = false) =
        Roll(id = 1, format = "PHOTO", stock = "SUNNY", capacity = 24, loadedAt = 0, sentAt = sent, readyAt = ready, developed = developed, opened = opened, express = express)

    @Test
    fun rollGoesRound() {
        val r = roll()
        assertTrue(r.inCamera())
        val sent = roll(sent = 1000, ready = 1000 + day)
        assertTrue(sent.atLab(2000))
        assertFalse(sent.ready(2000 + day))
        val done = sent.copy(developed = true)
        assertTrue(done.atLab(2000))
        assertTrue(done.waiting(1000 + day))
        assertFalse(done.copy(opened = true).waiting(1000 + day))
    }

    @Test
    fun expressOncePerWeek() {
        val now = 100 * day
        assertTrue(Rules.expressOpen(emptyList(), now))
        val used = listOf(roll(sent = now - 2 * day, express = true))
        assertFalse(Rules.expressOpen(used, now))
        assertTrue(Rules.expressOpen(used, now + 5 * day))
        assertTrue(Rules.expressOpen(used, now, unlimited = true))
    }

    @Test
    fun filmAgesAfterTwoMonths() {
        assertEquals(0f, Rules.expired(0, 59 * day), 0f)
        assertEquals(0.5f, Rules.expired(0, 90 * day), 0.001f)
        assertEquals(1f, Rules.expired(0, 400 * day), 0f)
        assertEquals(60, Rules.freshDaysLeft(0, 0))
    }

    @Test
    fun clipsAreCutFromTheMiddle() {
        assertEquals(0L, Rules.clipStart(2000))
        assertEquals(1000L, Rules.clipStart(5000))
    }

    @Test
    fun whatDoesNotFitStaysOut() {
        val (fit, rest) = Rules.fit(listOf(1, 2, 3, 4), 3)
        assertEquals(listOf(1, 2, 3), fit)
        assertEquals(listOf(4), rest)
        assertEquals(0, Rules.fit(listOf(1), -2).first.size)
    }

    @Test
    fun ordersCountOn() {
        assertEquals(418, Rules.nextOrder(417, 9))
        assertTrue(Rules.nextOrder(null, 123_456) in 400..899)
    }

    @Test
    fun defectsFallWhereFilmHasThem() {
        assertEquals(Defect.FIRST, Film.defect(1, 24, true, 1))
        assertEquals(Defect.CUT, Film.defect(24, 24, true, 1))
        assertTrue(Film.defect(24, 24, false, 1) != Defect.CUT)
    }

    @Test
    fun stampReadsLikeACamera() {
        val ms = LocalDate.of(2026, 10, 17).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        assertEquals("'26 10 17", DateStamp.text(ms, ZoneOffset.UTC))
    }

    @Test
    fun yearsAgoFindsThisDay() {
        val zone = ZoneOffset.UTC
        val today = LocalDate.of(2026, 10, 17)
        fun f(id: Long, d: LocalDate) = Frame(id = id, rollId = 1, number = 1, original = "", print = "p", takenAt = d.atStartOfDay(zone).toInstant().toEpochMilli())
        val list = listOf(f(1, LocalDate.of(2025, 10, 17)), f(2, LocalDate.of(2025, 10, 18)), f(3, today), f(4, LocalDate.of(2023, 10, 17)))
        assertEquals(listOf(1L, 4L), Rules.yearsAgo(list, today, zone).map { it.id })
    }

    @Test
    fun remindersFallInsideTheWindow() {
        val zone = ZoneOffset.UTC
        val day = LocalDate.of(2026, 10, 18)
        val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val t = Reminders.times(day, 3, 10 * 60, 20 * 60, zone)
        assertEquals(3, t.size)
        assertTrue(t.zipWithNext().all { (a, b) -> a < b })
        assertTrue(t.all { it >= start + 10 * 60 * 60_000L && it < start + 20 * 60 * 60_000L })
        assertTrue(Reminders.times(day, 0, 600, 1200, zone).isEmpty())
        // After the last one today, the next is tomorrow's first.
        assertEquals(Reminders.times(day.plusDays(1), 3, 600, 1200, zone).first(), Reminders.next(t.last() + 1, 3, 600, 1200, zone))
    }

    @Test
    fun backupRoundTrips() {
        val rolls = listOf(roll(sent = 5, ready = 9, developed = true, opened = true).copy(name = "Лето", order = 417, expired = 0.25f, film = "f.mp4"))
        val frames = listOf(Frame(id = 3, rollId = 1, number = 1, original = "a.jpg", print = "p.jpg", takenAt = 7, note = "Бабушка", by = "Аня", album = true))
        val (r, f) = Backup.decode(Backup.encode(rolls, frames))
        assertEquals(rolls, r)
        assertEquals(frames, f)
    }
}
