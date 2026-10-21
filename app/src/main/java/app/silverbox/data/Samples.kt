package app.silverbox.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import app.silverbox.develop.Exposure
import app.silverbox.develop.Film
import app.silverbox.domain.Stock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The prints pinned up beside each film on the shop's shelf: the same three pictures (faces, the sea, an
 * evening with lights) developed on the phone by the real darkroom, so what they show is what the film does.
 * Developed once, then kept in the cache.
 */
object Samples {
    val NAMES = listOf("people", "sea", "evening")
    private const val LONG = 1200
    private val memory = LruCache<String, Bitmap>(15)
    private val lock = Mutex()

    suspend fun print(context: Context, stock: Stock, name: String): Bitmap? = withContext(Dispatchers.Default) {
        val key = "${stock.name}-$name"
        memory.get(key)?.let { return@withContext it }
        // One at a time: the darkroom is heavy work and the shelf asks for several at once.
        lock.withLock {
            memory.get(key)?.let { return@withLock it }
            val dir = File(context.cacheDir, "samples").apply { mkdirs() }
            val file = File(dir, "$key-$VERSION.jpg")
            val b = BitmapFactory.decodeFile(file.path) ?: runCatching {
                val src = context.assets.open("samples/$name.jpg").use { BitmapFactory.decodeStream(it) } ?: return@withLock null
                val cropped = Film.crop(src, LONG)
                if (cropped !== src) src.recycle()
                val printed = Film.develop(cropped, Exposure(stock, seed = 11 + NAMES.indexOf(name)))
                cropped.recycle()
                file.outputStream().use { printed.compress(Bitmap.CompressFormat.JPEG, 88, it) }
                printed
            }.getOrNull() ?: return@withLock null
            memory.put(key, b)
            b
        }
    }

    /** Bumped when the darkroom changes, so the shelf shows the new look and not an old cached one. */
    private const val VERSION = 1
}
