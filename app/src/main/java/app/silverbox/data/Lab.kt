package app.silverbox.data

import android.content.Context
import android.graphics.Bitmap
import app.silverbox.develop.DateStamp
import app.silverbox.develop.Defect
import app.silverbox.develop.Exposure
import app.silverbox.develop.Film
import app.silverbox.develop.Movie
import app.silverbox.domain.Rules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The photo lab. Takes a roll in, makes the prints (or cuts the film) straight away in the background,
 * and keeps them back until the roll's [Roll.readyAt]. If the app is closed halfway, the next start
 * carries on where it stopped.
 */
class Lab(private val context: Context, private val db: LabDatabase, private val vault: Vault, private val settings: SettingsStore) {
    private val lock = Mutex()

    /** Hands a roll over the counter. */
    suspend fun send(roll: Roll, express: Boolean, now: Long = System.currentTimeMillis(), fast: Boolean = false) {
        val dao = db.dao()
        val wait = when {
            fast -> 60_000L
            express -> Rules.EXPRESS
            else -> Rules.STANDARD
        }
        dao.update(
            roll.copy(
                sentAt = now,
                readyAt = now + wait,
                express = express,
                order = Rules.nextOrder(dao.lastOrder(), now / 1000),
                expired = Rules.expired(roll.loadedAt, now),
            )
        )
    }

    /** Develops every roll that is waiting. Safe to call often. Returns how many rolls it finished. */
    suspend fun work(): Int = lock.withLock {
        withContext(Dispatchers.Default) {
            val dao = db.dao()
            dao.rolls().filter { it.sentAt != null && !it.developed }.sortedBy { it.sentAt }.count { roll ->
                runCatching { if (roll.movie) cut(roll) else print(roll) }
                    .onFailure { android.util.Log.w("Lab", "Roll ${roll.id} failed", it) }
                    .isSuccess
            }
        }
    }

    private suspend fun print(roll: Roll) {
        val dao = db.dao()
        val stamp = settings.now().dateStamp
        val frames = dao.framesOf(roll.id)
        frames.filter { it.print.isEmpty() }.forEach { f ->
            val src = Vault.decodeUpright(vault.file(f.original), Film.LONG) ?: return@forEach
            val cropped = Film.crop(src)
            if (cropped !== src) src.recycle()
            val defect = Film.defect(f.number, roll.id.toInt())
            val printed = Film.develop(
                cropped,
                Exposure(
                    stock = roll.stockOf,
                    seed = (roll.id * 1000 + f.number).toInt(),
                    defect = defect,
                    expired = roll.expired,
                    stamp = if (stamp) DateStamp.text(f.takenAt) else null,
                ),
            )
            cropped.recycle()
            val name = vault.put(printed, "p-")
            printed.recycle()
            dao.update(f.copy(print = name, defect = defect.name))
        }
        dao.update(roll.copy(developed = true))
    }

    private suspend fun cut(roll: Roll) {
        val dao = db.dao()
        val frames = dao.framesOf(roll.id).filter { it.video && vault.file(it.original).exists() }
        if (frames.isEmpty()) {
            dao.update(roll.copy(developed = true))
            return
        }
        val name = vault.newName("mp4", "f-")
        Movie.develop(
            context, roll.stockOf,
            frames.map { Movie.Clip(vault.file(it.original), it.durationMs.takeIf { d -> d > 0 } ?: vault.durationMs(vault.file(it.original)), it.takenAt) },
            roll.id.toInt(), vault.file(name),
        )
        // Each clip also gets a print of its middle frame, for the contact sheet.
        frames.filter { it.print.isEmpty() }.forEach { f ->
            val still = vault.frameOf(vault.file(f.original), f.durationMs / 2) ?: return@forEach
            val small = Vault.scaleDown(still, 960)
            val printed = Film.develop(small, Exposure(roll.stockOf, (roll.id * 1000 + f.number).toInt()))
            if (small !== still) small.recycle()
            still.recycle()
            val p = vault.put(printed, "p-", 88)
            printed.recycle()
            dao.update(f.copy(print = p, defect = Defect.NONE.name))
        }
        dao.update(roll.copy(developed = true, film = name))
    }

    companion object {
        /** A print with a white border, the way it came out of the envelope, for sharing. */
        fun bordered(print: Bitmap): Bitmap {
            val pad = (minOf(print.width, print.height) * 0.045f).toInt()
            val out = Bitmap.createBitmap(print.width + pad * 2, print.height + pad * 2, Bitmap.Config.ARGB_8888)
            val c = android.graphics.Canvas(out)
            c.drawColor(0xFFFBF8F1.toInt())
            c.drawBitmap(print, pad.toFloat(), pad.toFloat(), null)
            return out
        }
    }
}
