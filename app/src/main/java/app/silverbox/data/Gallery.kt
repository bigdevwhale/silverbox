package app.silverbox.data

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import java.io.File

/**
 * Gives the camera's originals back to the phone's gallery, once their roll is developed: byte for byte,
 * into Pictures/Silverbox, outside the camera's folder so they never go on a film again.
 */
object Gallery {
    private const val FOLDER = "Silverbox"
    private val PATH = Environment.DIRECTORY_PICTURES + "/" + FOLDER + "/"

    /** Android 9 and older need leave to write to shared storage. */
    fun needsPermission(context: Context) = Build.VERSION.SDK_INT < 29 &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED

    /** "Silverbox_0042_07.jpg": the order number and the place on the roll, so a second run skips what is there. */
    fun name(roll: Roll, f: Frame) = "Silverbox_%04d_%02d.%s".format(roll.order, f.number, f.original.substringAfterLast('.', "jpg"))

    /** Returns how many went back; the ones already in the gallery are skipped. */
    fun restore(context: Context, rolls: List<Roll>, frames: List<Frame>, vault: Vault, now: Long = System.currentTimeMillis()): Int {
        val ready = rolls.filter { it.ready(now) }.associateBy { it.id }
        val there = existing(context)
        var n = 0
        frames.forEach { f ->
            val roll = ready[f.rollId] ?: return@forEach
            val src = vault.file(f.original)
            val name = name(roll, f)
            if (!src.exists() || name in there) return@forEach
            if (runCatching { put(context, src, name, f) }.isSuccess) n++
        }
        return n
    }

    private fun existing(context: Context): Set<String> {
        if (Build.VERSION.SDK_INT < 29) {
            @Suppress("DEPRECATION")
            return File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), FOLDER).list()?.toSet().orEmpty()
        }
        val out = mutableSetOf<String>()
        for (collection in listOf(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)) {
            runCatching {
                context.contentResolver.query(
                    collection, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), "${MediaStore.MediaColumns.RELATIVE_PATH} = ?", arrayOf(PATH), null,
                )?.use { c -> while (c.moveToNext()) out += c.getString(0) }
            }
        }
        return out
    }

    private fun put(context: Context, src: File, name: String, f: Frame) {
        val mime = if (f.video) "video/mp4" else "image/jpeg"
        if (Build.VERSION.SDK_INT < 29) {
            @Suppress("DEPRECATION")
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), FOLDER).apply { mkdirs() }
            val out = File(dir, name)
            src.copyTo(out, overwrite = true)
            out.setLastModified(f.takenAt)
            MediaScannerConnection.scanFile(context, arrayOf(out.path), arrayOf(mime), null)
            return
        }
        val resolver = context.contentResolver
        val collection = if (f.video) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        else MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, PATH)
            // So the gallery puts it on the day it was taken, not the day it came back.
            put(MediaStore.MediaColumns.DATE_TAKEN, f.takenAt)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: error("No room in the gallery")
        try {
            resolver.openOutputStream(uri)?.use { out -> src.inputStream().use { it.copyTo(out) } } ?: error("Can't write")
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
    }
}
