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
    /** How long after opening the camera its pictures still count as this session's. */
    const val SESSION = 30 * 60_000L

    class Found(val uri: Uri, val video: Boolean, val takenAt: Long)

    /** What came back from one look at the gallery. */
    class Loaded(val uris: List<Uri>, val photos: Int, val clips: Int, val leftover: Int, val noRoll: Int)

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

    /** New camera pictures and clips since [since], oldest first, skipping ones already on film. */
    fun find(context: Context, since: Long, known: Set<String>): List<Found> {
        val out = mutableListOf<Found>()
        for (video in listOf(false, true)) {
            val collection = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val cols = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DATE_ADDED, MediaStore.MediaColumns.DATE_TAKEN)
            // Only the camera's folder: no screenshots, downloads or other apps' pictures.
            val (where, args) = if (Build.VERSION.SDK_INT >= 29) {
                "${MediaStore.MediaColumns.DATE_ADDED} >= ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} LIKE 'DCIM/%' AND ${MediaStore.MediaColumns.IS_PENDING} = 0" to
                    arrayOf(((since / 1000) - 2).toString())
            } else {
                @Suppress("DEPRECATION")
                "${MediaStore.MediaColumns.DATE_ADDED} >= ? AND ${MediaStore.MediaColumns.DATA} LIKE '%/DCIM/%'" to arrayOf(((since / 1000) - 2).toString())
            }
            runCatching {
                context.contentResolver.query(collection, cols, where, args, "${MediaStore.MediaColumns.DATE_ADDED} ASC")?.use { c ->
                    while (c.moveToNext()) {
                        val uri = ContentUris.withAppendedId(collection, c.getLong(0))
                        if (uri.toString() in known) continue
                        val taken = c.getLong(2).takeIf { it > 0 } ?: (c.getLong(1) * 1000)
                        out += Found(uri, video, taken)
                    }
                }
            }
        }
        return out.sortedBy { it.takenAt }
    }

    /** Looks at the gallery and loads anything new onto the film. */
    suspend fun load(context: Context, db: LabDatabase, vault: Vault, since: Long, now: Long = System.currentTimeMillis()): Loaded {
        val dao = db.dao()
        val found = find(context, since, dao.sources().toSet())
        if (found.isEmpty()) return Loaded(emptyList(), 0, 0, 0, 0)
        val taken = mutableListOf<Uri>()
        var leftover = 0
        var noRoll = 0
        var photos = 0
        var clips = 0

        val shots = found.filter { !it.video }
        val roll = dao.loaded(Format.PHOTO.name)
        if (roll == null) noRoll += shots.size
        else {
            val count = dao.framesOf(roll.id).size
            val (fit, rest) = Rules.fit(shots, roll.capacity - count)
            leftover += rest.size
            fit.forEachIndexed { i, f ->
                val name = copy(context, vault, f) ?: return@forEachIndexed
                dao.insert(Frame(rollId = roll.id, number = count + i + 1, original = name, takenAt = f.takenAt, source = f.uri.toString()))
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
            leftover += rest.size
            fit.forEachIndexed { i, f ->
                val name = copy(context, vault, f) ?: return@forEachIndexed
                val duration = vault.durationMs(vault.file(name))
                dao.insert(
                    Frame(
                        rollId = cassette.id, number = count + i + 1, original = name, video = true, durationMs = duration,
                        clipStartMs = Rules.clipStart(duration), takenAt = f.takenAt, source = f.uri.toString(),
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
    }.getOrNull()

    /**
     * Asks the system to move these pictures to the gallery's bin (one dialog for all). Android 11 and
     * newer; on older phones the pictures simply stay.
     */
    fun trashRequest(context: Context, uris: List<Uri>): PendingIntent? =
        if (Build.VERSION.SDK_INT >= 30 && uris.isNotEmpty()) runCatching { MediaStore.createTrashRequest(context.contentResolver, uris, true) }.getOrNull() else null
}
