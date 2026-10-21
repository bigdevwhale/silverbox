package app.silverbox.data

import android.Manifest
import android.app.PendingIntent
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import app.silverbox.domain.Format
import app.silverbox.domain.Rules
import app.silverbox.domain.Stock

/**
 * The phone's own camera does the shooting. When the keeper comes back, this finds what the camera put
 * in the gallery since the shutter was handed over, and loads it onto the film: photos onto the roll,
 * clips onto the cassette.
 */
object Pickup {
    /**
     * The longest pause between two pictures of one trip. A trip ends when the keeper is back in the app;
     * this only keeps out what the camera took on its own later, when the app was gone for hours.
     */
    const val GAP = 45 * 60_000L

    /** After coming back, the gallery is looked at again at these moments: cameras finish pictures late. */
    val LOOKS = listOf(0L, 1_500L, 4_000L, 8_000L, 15_000L, 30_000L, 60_000L, 120_000L)

    class Found(val uri: Uri, val video: Boolean, val takenAt: Long, val addedAt: Long)

    /** What came back from the gallery: from one look, or summed over a trip. */
    class Loaded(
        val uris: List<Uri> = emptyList(),
        val photos: Int = 0,
        val clips: Int = 0,
        /** What didn't fit, and what had no film to go on: still in the gallery, so seen again on every look. */
        val leftover: Set<Uri> = emptySet(),
        val noRoll: Set<Uri> = emptySet(),
    ) {
        val empty get() = photos + clips + leftover.size + noRoll.size == 0
        operator fun plus(o: Loaded) = Loaded(uris + o.uris, photos + o.photos, clips + o.clips, leftover + o.leftover, noRoll + o.noRoll)
    }

    fun permissions(): Array<String> = if (Build.VERSION.SDK_INT >= 33) {
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
    } else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

    /** Full access to photos and videos; "selected photos only" can't see new pictures. */
    fun canRead(context: Context): Boolean = permissions().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    /** The camera app in photo mode (it can switch to video itself). */
    fun cameraIntent(): Intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun hasCamera(context: Context): Boolean = cameraIntent().resolveActivity(context.packageManager) != null

    /**
     * New camera pictures and clips from one trip, oldest first, skipping ones already on film. The trip ran
     * from [since] to [back]; a long one (a whole party) counts as long as the pictures keep coming, no more
     * than [GAP] apart.
     */
    fun find(context: Context, since: Long, back: Long, known: Set<String>): List<Found> {
        val all = mutableListOf<Found>()
        for (video in listOf(false, true)) {
            val collection = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val cols = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DATE_ADDED, MediaStore.MediaColumns.DATE_TAKEN)
            val range = arrayOf(((since / 1000) - 2).toString(), (back / 1000 + 2).toString())
            // Only the camera's folder: no screenshots, downloads or other apps' pictures.
            val where = if (Build.VERSION.SDK_INT >= 29) {
                "${MediaStore.MediaColumns.DATE_ADDED} >= ? AND ${MediaStore.MediaColumns.DATE_ADDED} <= ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} LIKE 'DCIM/%' AND ${MediaStore.MediaColumns.IS_PENDING} = 0"
            } else {
                @Suppress("DEPRECATION")
                "${MediaStore.MediaColumns.DATE_ADDED} >= ? AND ${MediaStore.MediaColumns.DATE_ADDED} <= ? AND ${MediaStore.MediaColumns.DATA} LIKE '%/DCIM/%'"
            }
            runCatching {
                context.contentResolver.query(collection, cols, where, range, "${MediaStore.MediaColumns.DATE_ADDED} ASC")?.use { c ->
                    while (c.moveToNext()) {
                        val uri = ContentUris.withAppendedId(collection, c.getLong(0))
                        val added = c.getLong(1) * 1000
                        all += Found(uri, video, c.getLong(2).takeIf { it > 0 } ?: added, added)
                    }
                }
            }
        }
        return Rules.trip(all.sortedBy { it.addedAt }, since, GAP) { it.addedAt }
            .filter { it.uri.toString() !in known }
            .sortedBy { it.takenAt }
    }

    /** Looks at the gallery and loads anything new from the trip onto the film. */
    suspend fun load(context: Context, db: LabDatabase, vault: Vault, since: Long, back: Long, now: Long = System.currentTimeMillis()): Loaded {
        val dao = db.dao()
        val found = find(context, since, back, dao.sources().toSet())
        if (found.isEmpty()) return Loaded()
        val taken = mutableListOf<Uri>()
        val leftover = mutableSetOf<Uri>()
        val noRoll = mutableSetOf<Uri>()
        var photos = 0
        var clips = 0

        val shots = found.filter { !it.video }
        val roll = dao.loaded(Format.PHOTO.name)
        if (roll == null) noRoll += shots.map { it.uri }
        else {
            val count = dao.framesOf(roll.id).size
            val (fit, rest) = Rules.fit(shots, roll.capacity - count)
            leftover += rest.map { it.uri }
            fit.forEach { f ->
                // One that can't be read yet stays out of the known ones and is tried again on the next look.
                val name = copy(context, vault, f) ?: return@forEach
                dao.insert(Frame(rollId = roll.id, number = count + photos + 1, original = name, takenAt = f.takenAt, source = f.uri.toString()))
                taken += f.uri
                photos++
            }
        }

        val moving = found.filter { it.video }
        if (moving.isNotEmpty()) {
            // Clips need no ceremony: a fresh cassette goes in by itself.
            val cassette = dao.loaded(Format.MOVIE.name) ?: Roll(format = Format.MOVIE.name, stock = Stock.HOME8.name, capacity = Rules.CASSETTE, loadedAt = now)
                .let { it.copy(id = dao.insert(it)) }
            val count = dao.framesOf(cassette.id).size
            val (fit, rest) = Rules.fit(moving, cassette.capacity - count)
            leftover += rest.map { it.uri }
            fit.forEach { f ->
                val name = copy(context, vault, f) ?: return@forEach
                val duration = vault.durationMs(vault.file(name))
                dao.insert(
                    Frame(
                        rollId = cassette.id, number = count + clips + 1, original = name, video = true, durationMs = duration,
                        takenAt = f.takenAt, source = f.uri.toString(),
                    )
                )
                taken += f.uri
                clips++
            }
        }
        return Loaded(taken, photos, clips, leftover, noRoll)
    }

    private fun copy(context: Context, vault: Vault, f: Found): String? = runCatching {
        context.contentResolver.openInputStream(f.uri)?.use { vault.take(it, if (f.video) "mp4" else "jpg") }
    }.onFailure { android.util.Log.w("Pickup", "Can't read ${f.uri}", it) }.getOrNull()

    /**
     * Asks the system to move these pictures to the gallery's bin (one dialog for all). Android 11 and
     * newer; on older phones the pictures simply stay.
     */
    fun trashRequest(context: Context, uris: List<Uri>): PendingIntent? =
        if (Build.VERSION.SDK_INT >= 30 && uris.isNotEmpty()) runCatching { MediaStore.createTrashRequest(context.contentResolver, uris, true) }.getOrNull() else null
}
