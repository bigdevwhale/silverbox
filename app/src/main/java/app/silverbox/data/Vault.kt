package app.silverbox.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.util.LruCache
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.max

/**
 * Where the pictures live: the camera's originals, the lab's prints and films, all in the app's own
 * files. Each picture has a small copy beside it for lists and contact sheets.
 */
class Vault(private val context: Context) {
    private val dir = File(context.filesDir, "vault").apply { mkdirs() }
    private val smalls = File(dir, "small").apply { mkdirs() }
    private val cache = object : LruCache<String, Bitmap>(32 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun file(name: String) = File(dir, name)
    private fun smallFile(name: String) = File(smalls, name.substringBeforeLast('.') + ".jpg")

    fun newName(ext: String, prefix: String = "") = prefix + UUID.randomUUID().toString() + "." + ext

    /** Copies a stream in as a new file; returns its name. */
    fun take(input: java.io.InputStream, ext: String): String {
        val name = newName(ext)
        FileOutputStream(file(name)).use { input.copyTo(it) }
        return name
    }

    /** Saves a picture as a JPEG and makes its small copy. */
    fun put(bmp: Bitmap, prefix: String, quality: Int = 92): String {
        val name = newName("jpg", prefix)
        FileOutputStream(file(name)).use { bmp.compress(Bitmap.CompressFormat.JPEG, quality, it) }
        val s = scaleDown(bmp, SMALL)
        FileOutputStream(smallFile(name)).use { s.compress(Bitmap.CompressFormat.JPEG, 86, it) }
        if (s !== bmp) s.recycle()
        cache.remove(name)
        return name
    }

    /** The small picture for lists, from the cache when it can. */
    fun small(name: String): Bitmap? {
        if (name.isEmpty()) return null
        cache.get(name)?.let { return it }
        val f = smallFile(name)
        if (!f.exists()) {
            val src = file(name)
            if (!src.exists()) return null
            val b = (if (name.endsWith(".mp4")) frameOf(src, 0) else decodeUpright(src, SMALL)) ?: return null
            val s = scaleDown(b, SMALL)
            FileOutputStream(f).use { s.compress(Bitmap.CompressFormat.JPEG, 86, it) }
            if (s !== b) b.recycle()
        }
        val b = BitmapFactory.decodeFile(f.path) ?: return null
        cache.put(name, b)
        return b
    }

    /** The whole picture, for looking at. */
    fun full(name: String, max: Int = 2400): Bitmap? = decodeUpright(file(name), max)

    fun durationMs(f: File): Long = runCatching {
        MediaMetadataRetriever().run {
            try {
                setDataSource(f.path)
                extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            } finally { release() }
        }
    }.getOrDefault(0L)

    /** A still from a clip at [atMs]. */
    fun frameOf(f: File, atMs: Long): Bitmap? = runCatching {
        MediaMetadataRetriever().run {
            try {
                setDataSource(f.path)
                getFrameAtTime(atMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: frameAtTime
            } finally { release() }
        }
    }.getOrNull()

    fun delete(name: String) {
        if (name.isEmpty()) return
        file(name).delete()
        smallFile(name).delete()
        cache.remove(name)
    }

    fun wipe() {
        dir.listFiles()?.forEach { if (it.isFile) it.delete() }
        smalls.listFiles()?.forEach { it.delete() }
        cache.evictAll()
    }

    /** Takes in the files of a restored backup; their small copies are made again when first needed. */
    fun adopt(from: File) {
        from.listFiles()?.forEach { f ->
            if (!f.isFile) return@forEach
            if (!f.renameTo(file(f.name))) { f.copyTo(file(f.name), overwrite = true); f.delete() }
        }
        from.deleteRecursively()
    }

    companion object {
        const val SMALL = 480

        fun scaleDown(b: Bitmap, max: Int): Bitmap {
            val m = max(b.width, b.height)
            if (m <= max) return b
            val k = max.toFloat() / m
            return Bitmap.createScaledBitmap(b, (b.width * k).toInt().coerceAtLeast(1), (b.height * k).toInt().coerceAtLeast(1), true)
        }

        /** Decodes with subsampling and turns it the way EXIF says. */
        fun decodeUpright(f: File, max: Int): Bitmap? {
            if (!f.exists()) return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.path, bounds)
            if (bounds.outWidth <= 0) return null
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= max) sample *= 2
            val raw = BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
            val rot = runCatching { ExifInterface(f.path).rotationDegrees }.getOrDefault(0)
            val scaled = scaleDown(raw, max)
            if (scaled !== raw) raw.recycle()
            if (rot == 0) return scaled
            val m = Matrix().apply { postRotate(rot.toFloat()) }
            val r = Bitmap.createBitmap(scaled, 0, 0, scaled.width, scaled.height, m, true)
            if (r !== scaled) scaled.recycle()
            return r
        }
    }
}
