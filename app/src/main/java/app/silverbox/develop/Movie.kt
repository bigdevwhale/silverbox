package app.silverbox.develop

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import androidx.annotation.OptIn
import androidx.core.content.res.ResourcesCompat
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.FrameDropEffect
import androidx.media3.effect.MatrixTransformation
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.effect.RgbMatrix
import androidx.media3.effect.SingleColorLut
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import app.silverbox.R
import app.silverbox.domain.Look
import app.silverbox.domain.Rules
import app.silverbox.domain.Stock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.sin
import kotlin.random.Random

/**
 * The film lab for cassettes: cuts three seconds from each clip, puts them end to end after a title card,
 * and prints the lot as home movie: 4:3, 18 frames a second, the stock's colour, grain, dust, a flicker
 * and the weave of film in the gate. Silent, as home movies were; the projector supplies the sound.
 */
@OptIn(UnstableApi::class)
object Movie {
    const val W = 960
    const val H = 720
    const val FPS = 18f

    class Clip(val file: File, val startMs: Long)

    /** Writes the film to [out]. Must not be called on the main thread; it hops there for the transformer. */
    suspend fun develop(context: Context, stock: Stock, clips: List<Clip>, title: String, sub: String, end: String, seed: Int, out: File) {
        val cards = File(context.cacheDir, "cards").apply { mkdirs() }
        val opening = File(cards, "title-$seed.png").also { save(card(context, title, sub), it) }
        val closing = File(cards, "end-$seed.png").also { save(card(context, end, ""), it) }
        val fit = Presentation.createForWidthAndHeight(W, H, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP)

        fun still(f: File, ms: Long) = EditedMediaItem.Builder(MediaItem.Builder().setUri(Uri.fromFile(f)).setImageDurationMs(ms).build())
            .setFrameRate(FPS.toInt())
            .setRemoveAudio(true)
            .setEffects(Effects(listOf(), listOf<Effect>(fit)))
            .build()

        val items = buildList {
            add(still(opening, 3000))
            clips.forEach { c ->
                val item = MediaItem.Builder()
                    .setUri(Uri.fromFile(c.file))
                    .setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder().setStartPositionMs(c.startMs).setEndPositionMs(c.startMs + Rules.CLIP_MS).build()
                    )
                    .build()
                add(EditedMediaItem.Builder(item).setRemoveAudio(true).setEffects(Effects(listOf(), listOf<Effect>(fit))).build())
            }
            add(still(closing, 2000))
        }

        val look = Film.aged(stock.look, 0f)
        val rnd = Random(seed)
        val effects = listOf<Effect>(
            // Phones film at 30: keep three frames in five for the jerk of 18 fps (the default dropper trips some GPUs).
            FrameDropEffect.createSimpleFrameDropEffect(30f, FPS),
            SingleColorLut.createFromCube(cube(look)),
            weave(rnd.nextInt()),
            flicker(rnd.nextInt()),
            OverlayEffect(listOf(Dirt(look, rnd.nextInt()))),
        )
        val composition = Composition.Builder(EditedMediaItemSequence.Builder(items).build())
            .setEffects(Effects(listOf(), effects))
            .build()

        out.delete()
        try {
            export(context, composition, out)
        } catch (e: Exception) {
            // Half a film is no film.
            out.delete()
            throw e
        } finally {
            opening.delete()
            closing.delete()
        }
    }

    private suspend fun export(context: Context, composition: Composition, out: File) {
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                val transformer = Transformer.Builder(context)
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            if (cont.isActive) cont.resume(Unit)
                        }

                        override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                            if (cont.isActive) cont.resumeWithException(exportException)
                        }
                    })
                    .build()
                try {
                    transformer.start(composition, out.path)
                } catch (e: Exception) {
                    cont.resumeWithException(e)
                }
                cont.invokeOnCancellation { transformer.cancel() }
            }
        }
    }

    /** The stock's colour as a 3D lookup table, from the same curve the prints use. */
    private fun cube(look: Look): Array<Array<IntArray>> {
        val n = 33
        val curve = Film.curve(look)
        fun c(v: Float) = curve[(v.coerceIn(0f, 1.5f) / 1.5f * (curve.size - 1)).toInt()]
        return Array(n) { ri ->
            Array(n) { gi ->
                IntArray(n) { bi ->
                    var r = ri / (n - 1f) * look.wb[0]
                    var g = gi / (n - 1f) * look.wb[1]
                    var b = bi / (n - 1f) * look.wb[2]
                    look.bw?.let { m -> val l = r * m[0] + g * m[1] + b * m[2]; r = l; g = l; b = l }
                    r = c(r); g = c(g); b = c(b)
                    val l = 0.299f * r + 0.587f * g + 0.114f * b
                    if (look.bw == null) {
                        r = l + (r - l) * look.sat; g = l + (g - l) * look.sat; b = l + (b - l) * look.sat
                    }
                    val ts = (1 - l) * (1 - l)
                    val th = l * l
                    r += look.shadows[0] * ts + look.highlights[0] * th
                    g += look.shadows[1] * ts + look.highlights[1] * th
                    b += look.shadows[2] * ts + look.highlights[2] * th
                    Color.rgb(ch(r), ch(g), ch(b))
                }
            }
        }
    }

    private fun ch(v: Float) = (v * 255f + 0.5f).toInt().coerceIn(0, 255)

    /** Film never sits still in the gate: a small, wandering shift, and a little zoom to hide the edges. */
    private fun weave(seed: Int) = MatrixTransformation { t ->
        val s = t / 1_000_000.0
        val r = Random(seed)
        val a = r.nextFloat() * 6f
        val b = r.nextFloat() * 6f
        val dx = (sin(s * 7.3 + a) * 0.003 + sin(s * 19.1 + b) * 0.002).toFloat()
        val dy = (sin(s * 5.7 + b) * 0.006 + sin(s * 23.3 + a) * 0.003).toFloat()
        android.graphics.Matrix().apply {
            postScale(1.04f, 1.04f)
            postTranslate(dx, dy)
        }
    }

    /** The lamp and the shutter: brightness breathing a few percent, frame to frame. */
    private fun flicker(seed: Int) = RgbMatrix { t, _ ->
        val frame = (t * FPS / 1_000_000).toLong()
        val k = 1f + (Random(seed + frame).nextFloat() - 0.5f) * 0.07f
        floatArrayOf(
            k, 0f, 0f, 0f,
            0f, k, 0f, 0f,
            0f, 0f, k, 0f,
            0f, 0f, 0f, 1f,
        )
    }

    /** Grain, vignette, dust and the odd scratch: a few frames' worth, cycled. */
    private class Dirt(look: Look, seed: Int) : BitmapOverlay() {
        private val frames = List(6) { i -> paint(look, seed + i * 977) }

        override fun getBitmap(presentationTimeUs: Long): Bitmap {
            val n = (presentationTimeUs * FPS / 1_000_000).toLong()
            return frames[(Random(n).nextInt(frames.size))]
        }

        private fun paint(look: Look, seed: Int): Bitmap {
            val r = Random(seed)
            val b = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
            val px = IntArray(W * H)
            val a = (look.grain * 255 * 0.9f).toInt().coerceIn(6, 60)
            val gs = 2
            for (y in 0 until H step gs) for (x in 0 until W step gs) {
                val v = r.nextFloat() + r.nextFloat() - 1f
                val c = if (v > 0) Color.argb((v * a).toInt(), 255, 245, 230) else Color.argb((-v * a).toInt(), 0, 0, 0)
                for (yy in y until minOf(y + gs, H)) for (xx in x until minOf(x + gs, W)) px[yy * W + xx] = c
            }
            b.setPixels(px, 0, W, 0, 0, W, H)
            val cv = Canvas(b)
            cv.drawRect(0f, 0f, W.toFloat(), H.toFloat(), Paint().apply {
                shader = RadialGradient(
                    W / 2f, H / 2f, W * 0.72f,
                    intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, Color.argb((look.vignette * 300).toInt().coerceIn(0, 230), 0, 0, 0)),
                    floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
                )
            })
            val dust = Paint(Paint.ANTI_ALIAS_FLAG)
            repeat(r.nextInt(2, 7)) {
                dust.color = if (r.nextInt(3) == 0) Color.argb(150, 255, 250, 240) else Color.argb(170, 10, 8, 6)
                val x = r.nextFloat() * W
                val y = r.nextFloat() * H
                if (r.nextInt(4) == 0) {
                    // A hair.
                    dust.strokeWidth = 1.6f
                    dust.style = Paint.Style.STROKE
                    val p = android.graphics.Path().apply {
                        moveTo(x, y)
                        quadTo(x + r.nextFloat() * 40 - 20, y + r.nextFloat() * 40 - 20, x + r.nextFloat() * 60 - 30, y + r.nextFloat() * 60 - 30)
                    }
                    cv.drawPath(p, dust)
                } else {
                    dust.style = Paint.Style.FILL
                    cv.drawCircle(x, y, 0.8f + r.nextFloat() * 2.4f, dust)
                }
            }
            if (r.nextInt(3) == 0) {
                val x = r.nextFloat() * W
                cv.drawRect(x, 0f, x + 1.4f, H.toFloat(), Paint().apply { color = Color.argb(70, 255, 250, 235) })
            }
            return b
        }
    }

    /** An intertitle: pale letters in a thin ornamental frame on black, the way silent films said things. */
    fun card(context: Context, title: String, sub: String): Bitmap {
        val b = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.drawColor(Color.rgb(14, 12, 10))
        val ink = Color.rgb(236, 228, 210)
        val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; style = Paint.Style.STROKE; strokeWidth = 3f }
        c.drawRect(RectF(70f, 70f, W - 70f, H - 70f), frame)
        frame.strokeWidth = 1.2f
        c.drawRect(RectF(84f, 84f, W - 84f, H - 84f), frame)
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink }
        for ((x, y) in listOf(70f to 70f, W - 70f to 70f, 70f to H - 70f, W - 70f to H - 70f)) c.drawCircle(x, y, 7f, dot)

        val serif = runCatching { ResourcesCompat.getFont(context, R.font.lora_italic) }.getOrNull() ?: Typeface.SERIF
        val big = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; typeface = serif; textAlign = Paint.Align.CENTER; textSize = 64f }
        val small = Paint(big).apply { textSize = 34f; color = Color.rgb(196, 186, 166) }
        val lines = wrap(title, big, W - 240f)
        val lineH = 80f
        val block = lines.size * lineH + if (sub.isNotEmpty()) 70f else 0f
        var y = H / 2f - block / 2f + 56f
        lines.forEach { line -> c.drawText(line, W / 2f, y, big); y += lineH }
        if (sub.isNotEmpty()) c.drawText(sub, W / 2f, y + 24f, small)
        return b
    }

    private fun wrap(text: String, p: Paint, max: Float): List<String> {
        val out = mutableListOf<String>()
        var line = ""
        text.split(' ').forEach { word ->
            val next = if (line.isEmpty()) word else "$line $word"
            if (p.measureText(next) <= max || line.isEmpty()) line = next else { out += line; line = word }
        }
        if (line.isNotEmpty()) out += line
        return out.take(3)
    }

    private fun save(b: Bitmap, f: File) {
        FileOutputStream(f).use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        b.recycle()
    }
}
