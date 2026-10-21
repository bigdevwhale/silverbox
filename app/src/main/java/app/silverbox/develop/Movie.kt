package app.silverbox.develop

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.opengl.GLES20
import androidx.annotation.OptIn
import androidx.core.content.res.ResourcesCompat
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.FrameDropEffect
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
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
import java.nio.ByteBuffer
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.tanh
import kotlin.random.Random

/**
 * The lab for cassettes: puts the clips end to end, whole and with their sound, in the shape they were
 * filmed in, and prints the lot as one of two things. Home movie: 18 frames a second, the stock's colour,
 * grain, dust, a flicker, the weave of film in the gate and a flash where each clip is spliced on, the sound
 * thin as from a projector's speaker. Or a VHS tape: soft, colour bleeding sideways, lines that wobble, a
 * band of tracking noise, the camcorder's date in the corner and a tear in the picture at every cut.
 */
@OptIn(UnstableApi::class)
object Movie {
    /** The long side of the frame; the short one follows the clips. A tape holds less. */
    const val LONG = 960
    const val TAPE_LONG = 720
    const val FPS = 18f

    class Clip(val file: File, val durationMs: Long, val takenAt: Long = 0)

    /**
     * The frame's width and height: the shape most of the clips were filmed in (9:16 upright, 16:9 across),
     * so they fill it with nothing cut off and no bars; only a clip of another shape sits inside it.
     */
    fun gate(clips: List<Clip>, long: Int = LONG): Pair<Int, Int> {
        val shape = clips.mapNotNull { shape(it.file) }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: (4 to 3)
        val (w, h) = shape
        fun even(v: Float) = (v / 2).toInt() * 2
        return if (w >= h) long to even(long * h / w.toFloat()) else even(long * w / h.toFloat()) to long
    }

    /** A clip's width and height as it is watched, reduced to a common shape like 16:9. */
    private fun shape(f: File): Pair<Int, Int>? = runCatching {
        MediaMetadataRetriever().run {
            try {
                setDataSource(f.path)
                val w = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                val h = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                val turned = (extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0) % 180 != 0
                if (w <= 0 || h <= 0) null else Rules.shape(if (turned) h else w, if (turned) w else h)
            } finally { release() }
        }
    }.getOrNull()

    /** Writes the film to [out]. Must not be called on the main thread; it hops there for the transformer. */
    suspend fun develop(context: Context, stock: Stock, clips: List<Clip>, seed: Int, out: File) {
        val (w, h) = gate(clips, if (stock.tape) TAPE_LONG else LONG)
        val fit = Presentation.createForWidthAndHeight(w, h, Presentation.LAYOUT_SCALE_TO_FIT)
        val items = clips.map { c -> EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(c.file))).setEffects(Effects(listOf(), listOf<Effect>(fit))).build() }
        // Where one clip is joined to the next, in the film's own time.
        val cuts = clips.runningFold(0L) { at, c -> at + c.durationMs }

        val look = Film.aged(stock.look, 0f)
        val rnd = Random(seed)
        val effects = if (stock.tape) listOf<Effect>(
            SingleColorLut.createFromCube(cube(look)),
            Tape(rnd.nextInt(), cuts),
            OverlayEffect(listOf(Camcorder(context, clips, cuts, w, h))),
        ) else listOf<Effect>(
            // Phones film at 30: keep three frames in five for the jerk of 18 fps (the default dropper trips some GPUs).
            FrameDropEffect.createSimpleFrameDropEffect(30f, FPS),
            SingleColorLut.createFromCube(cube(look)),
            weave(rnd.nextInt()),
            flicker(rnd.nextInt(), cuts.drop(1).dropLast(1)),
            OverlayEffect(listOf(Dirt(look, rnd.nextInt(), w, h))),
        )
        val speaker = if (stock.tape) Speaker(rnd.nextInt(), low = 90f, high = 7000f, drive = 1.2f, crackle = false)
        else Speaker(rnd.nextInt(), low = 300f, high = 3500f, drive = 1.6f, crackle = true)
        val composition = Composition.Builder(
            // A clip filmed without sound gets silence, so the next one's sound stays in step.
            EditedMediaItemSequence.Builder(items).experimentalSetForceAudioTrack(true).build()
        )
            .setEffects(Effects(listOf<AudioProcessor>(speaker), effects))
            // Phones film in HDR more and more; home movie is not.
            .setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL)
            .build()

        out.delete()
        try {
            export(context, composition, out)
        } catch (e: Exception) {
            // Half a film is no film.
            out.delete()
            throw e
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

    /** The lamp and the shutter: brightness breathing a few percent, frame to frame, and a flash at each splice. */
    private fun flicker(seed: Int, splices: List<Long>) = RgbMatrix { t, _ ->
        val frame = (t * FPS / 1_000_000).toLong()
        val ms = t / 1000
        val splice = splices.any { ms - it in 0..SPLICE_MS }
        val k = (1f + (Random(seed + frame).nextFloat() - 0.5f) * 0.07f) * if (splice) 1.7f else 1f
        floatArrayOf(
            k, 0f, 0f, 0f,
            0f, k, 0f, 0f,
            0f, 0f, k, 0f,
            0f, 0f, 0f, 1f,
        )
    }

    /** Two frames of light where the tape of a splice went through the gate. */
    private const val SPLICE_MS = 110L

    /**
     * The speaker of a projector or an old television: only the band from [low] to [high] Hz, slightly
     * driven, a hiss under it, and on film the odd crackle of dust on the sound stripe.
     */
    private class Speaker(seed: Int, private val low: Float, private val high: Float, private val drive: Float, private val crackle: Boolean) : BaseAudioProcessor() {
        private val rnd = Random(seed)
        private var channels = 1
        private var hpA = 0f
        private var lpA = 0f
        private var hpIn = FloatArray(1)
        private var hpOut = FloatArray(1)
        private var lp1 = FloatArray(1)
        private var lp2 = FloatArray(1)
        private var pop = 0f

        override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
            if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) return AudioProcessor.AudioFormat.NOT_SET
            channels = inputAudioFormat.channelCount
            val dt = 1f / inputAudioFormat.sampleRate
            val hpRc = 1f / (2f * PI.toFloat() * low)
            val lpRc = 1f / (2f * PI.toFloat() * high)
            hpA = hpRc / (hpRc + dt)
            lpA = dt / (lpRc + dt)
            return inputAudioFormat
        }

        override fun onFlush() {
            hpIn = FloatArray(channels); hpOut = FloatArray(channels); lp1 = FloatArray(channels); lp2 = FloatArray(channels)
            pop = 0f
        }

        override fun queueInput(inputBuffer: ByteBuffer) {
            val out = replaceOutputBuffer(inputBuffer.remaining())
            val frame = 2 * channels
            while (inputBuffer.remaining() >= frame) {
                if (crackle && rnd.nextInt(24_000) == 0) pop = (rnd.nextFloat() - 0.5f) * 0.25f
                val hiss = (rnd.nextFloat() - 0.5f) * 0.012f
                for (ch in 0 until channels) {
                    val x = inputBuffer.short / 32768f
                    hpOut[ch] = hpA * (hpOut[ch] + x - hpIn[ch]); hpIn[ch] = x
                    lp1[ch] += lpA * (hpOut[ch] - lp1[ch])
                    lp2[ch] += lpA * (lp1[ch] - lp2[ch])
                    val y = tanh(lp2[ch] * drive) / tanh(drive) * 0.85f + hiss + pop
                    out.putShort((y.coerceIn(-1f, 1f) * 32767f).toInt().toShort())
                }
                pop *= 0.96f
            }
            inputBuffer.position(inputBuffer.limit())
            out.flip()
        }
    }

    /** Grain, vignette, dust and the odd scratch: a few frames' worth, cycled. */
    private class Dirt(look: Look, seed: Int, private val W: Int, private val H: Int) : BitmapOverlay() {
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

    /**
     * The look of a worn tape, in one pass on the GPU: the picture slightly soft, colour smeared to the right
     * of what it belongs to, every line shaking a little, scanlines and snow, a band of tracking noise
     * drifting at the bottom, and the picture torn sideways for a moment at every cut.
     */
    private class Tape(private val seed: Int, private val cuts: List<Long>) : GlEffect {
        override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram = TapeProgram(useHdr, seed, cuts)
    }

    private class TapeProgram(useHdr: Boolean, seed: Int, private val cuts: List<Long>) : BaseGlShaderProgram(useHdr, 1) {
        private val program = GlProgram(VERTEX, FRAGMENT).apply {
            setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE)
        }
        private val seed = (seed and 0xffff) / 65535f
        private var size = floatArrayOf(1f, 1f)

        override fun configure(inputWidth: Int, inputHeight: Int): Size {
            size = floatArrayOf(inputWidth.toFloat(), inputHeight.toFloat())
            return Size(inputWidth, inputHeight)
        }

        override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
            val ms = presentationTimeUs / 1000
            // Just after a cut the heads lose the track for a few frames.
            val since = cuts.filter { it in 1..ms }.maxOfOrNull { ms - it }
            val tear = if (since != null && since < 260) 1f - since / 260f else 0f
            try {
                program.use()
                program.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
                program.setFloatUniform("uTime", ms / 1000f + seed * 100f)
                program.setFloatUniform("uTear", tear)
                program.setFloatsUniform("uSize", size)
                program.bindAttributesAndUniforms()
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            } catch (e: GlUtil.GlException) {
                throw VideoFrameProcessingException(e, presentationTimeUs)
            }
        }

        override fun release() {
            super.release()
            try { program.delete() } catch (e: GlUtil.GlException) { throw VideoFrameProcessingException(e) }
        }

        companion object {
            const val VERTEX = """
                attribute vec4 aFramePosition;
                varying vec2 vUv;
                void main() {
                    gl_Position = aFramePosition;
                    vUv = aFramePosition.xy * 0.5 + 0.5;
                }
            """

            const val FRAGMENT = """
                precision highp float;
                uniform sampler2D uTexSampler;
                uniform float uTime;
                uniform float uTear;
                uniform vec2 uSize;
                varying vec2 vUv;

                float rand(vec2 p) { return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453); }

                vec3 toYiq(vec3 c) {
                    return vec3(dot(c, vec3(0.299, 0.587, 0.114)), dot(c, vec3(0.596, -0.274, -0.322)), dot(c, vec3(0.211, -0.523, 0.312)));
                }
                vec3 toRgb(vec3 c) {
                    return vec3(c.x + 0.956 * c.y + 0.621 * c.z, c.x - 0.272 * c.y - 0.647 * c.z, c.x - 1.106 * c.y + 1.703 * c.z);
                }

                void main() {
                    vec2 uv = vUv;
                    float line = floor((1.0 - uv.y) * uSize.y / 2.0);
                    float frame = floor(uTime * 30.0);
                    // The tracking band: a strip near the bottom that drifts up and down.
                    float bandY = 0.025 + 0.02 * sin(uTime * 0.6);
                    float band = smoothstep(0.022, 0.0, abs(uv.y - bandY));
                    // A tear after a cut: the top of the picture slides sideways.
                    float tear = uTear * smoothstep(0.35, 1.0, uv.y);
                    float shake = (rand(vec2(line, frame)) - 0.5) * (0.0016 + band * 0.04) + sin(uv.y * 30.0 + uTime * 3.0) * 0.0007;
                    uv.x += shake + tear * 0.06 * (0.6 + 0.4 * sin(uv.y * 20.0 + uTime * 40.0));
                    float px = 1.0 / uSize.x;
                    // Brightness is a little soft; colour is soft and lags to the right.
                    vec3 l = toYiq(texture2D(uTexSampler, uv).rgb * 0.5
                        + texture2D(uTexSampler, uv - vec2(px, 0.0)).rgb * 0.25
                        + texture2D(uTexSampler, uv + vec2(px, 0.0)).rgb * 0.25);
                    vec3 c = vec3(0.0);
                    for (int i = 0; i < 8; i++) c += toYiq(texture2D(uTexSampler, uv - vec2(float(i + 2) * 1.6 * px, 0.0)).rgb);
                    c /= 8.0;
                    vec3 col = toRgb(vec3(l.x, c.y * 1.1, c.z * 1.1));
                    col *= 0.93 + 0.07 * sin((1.0 - vUv.y) * uSize.y * 3.14159);
                    col += (rand(vUv * uSize + frame) - 0.5) * 0.07;
                    float snow = step(0.7, rand(vec2(floor(vUv.x * uSize.x / 3.0), line + frame)));
                    col = mix(col, vec3(0.85), band * snow * 0.45);
                    col = mix(col, vec3(rand(vec2(line, frame))), tear * 0.25 * step(0.7, rand(vec2(line * 3.0, frame))));
                    gl_FragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
                }
            """
        }
    }

    /**
     * What a camcorder wrote over the picture: "PLAY" for the first moments, and the date and time each clip
     * was filmed, in the corner, in its square letters.
     */
    private class Camcorder(context: Context, clips: List<Clip>, private val cuts: List<Long>, private val w: Int, private val h: Int) : BitmapOverlay() {
        private val font = runCatching { ResourcesCompat.getFont(context, R.font.pt_mono) }.getOrNull() ?: Typeface.MONOSPACE
        private val stamps = clips.map { paint(if (it.takenAt > 0) date(it.takenAt) else null, null) }
        private val play = paint(null, "PLAY ▶")
        private val stampsWithPlay = clips.map { paint(if (it.takenAt > 0) date(it.takenAt) else null, "PLAY ▶") }

        override fun getBitmap(presentationTimeUs: Long): Bitmap {
            val ms = presentationTimeUs / 1000
            val i = (cuts.indexOfLast { it <= ms }).coerceIn(0, stamps.lastIndex.coerceAtLeast(0))
            val playing = ms < 2500
            return if (stamps.isEmpty()) play else if (playing) stampsWithPlay[i] else stamps[i]
        }

        private fun date(ms: Long): Pair<String, String> {
            val t = java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault())
            val am = if (t.hour < 12) "AM" else "PM"
            val hour = (t.hour + 11) % 12 + 1
            val time = "%s %d:%02d".format(am, hour, t.minute)
            val day = "%s.%2d %d".format(t.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH).uppercase(), t.dayOfMonth, t.year)
            return time to day
        }

        private fun paint(date: Pair<String, String>?, top: String?): Bitmap {
            val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(b)
            val size = minOf(w, h) * 0.055f
            val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = font; textSize = size; color = Color.rgb(240, 240, 232)
                setShadowLayer(size * 0.12f, size * 0.06f, size * 0.06f, Color.argb(200, 0, 0, 0))
            }
            val pad = minOf(w, h) * 0.06f
            top?.let { c.drawText(it, pad, pad + size, text) }
            date?.let { (time, day) ->
                c.drawText(time, pad, h - pad - size * 1.25f, text)
                c.drawText(day, pad, h - pad, text)
            }
            return b
        }
    }
}
