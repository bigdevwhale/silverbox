package app.silverbox

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import app.silverbox.develop.DateStamp
import app.silverbox.develop.Defect
import app.silverbox.develop.Exposure
import app.silverbox.develop.Film
import app.silverbox.domain.Stock
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Develops a few demo photos on every stock, with the defects, to app/build/art/prints (run with `-Prender`). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class RenderArt {
    private val out = File(System.getProperty("render.out") ?: "build/art", "prints").apply { mkdirs() }
    private val photos = File(System.getProperty("render.photos") ?: "src/demo/assets/demo")

    private fun photo(name: String): Bitmap = BitmapFactory.decodeFile(File(photos, "p$name.jpg").path)

    private fun save(b: Bitmap, name: String) {
        val s = Bitmap.createScaledBitmap(b, b.width / 2, b.height / 2, true)
        File(out, "$name.jpg").outputStream().use { s.compress(Bitmap.CompressFormat.JPEG, 88, it) }
    }

    @Test
    fun stocks() {
        val stamp = DateStamp.text(System.currentTimeMillis())
        for (p in listOf("129", "64", "195", "1001")) {
            val src = Film.crop(photo(p))
            save(src, "$p-0-original")
            Stock.photo.forEach { s -> save(Film.develop(src, Exposure(s, 7, stamp = stamp)), "$p-${s.name.lowercase()}") }
        }
    }

    @Test
    fun defects() {
        val src = Film.crop(photo("102"))
        listOf(Defect.FIRST, Defect.LEAK).forEach { d -> save(Film.develop(src, Exposure(Stock.SUNNY, 3, d)), "102-${d.name.lowercase()}") }
        save(Film.develop(src, Exposure(Stock.SUNNY, 3, expired = 1f)), "102-expired")
        for (p in listOf("195", "1008")) {
            val dark = Film.crop(photo(p))
            listOf(Defect.FIRST, Defect.LEAK).forEach { d -> save(Film.develop(dark, Exposure(Stock.EVERY, 11, d, stamp = "'26 10 17")), "$p-${d.name.lowercase()}") }
        }
        val up = Film.crop(photo("64"))
        save(Film.develop(up, Exposure(Stock.EVERY, 5, Defect.LEAK, stamp = "'26 10 17")), "64-leak-upright")
    }
}
