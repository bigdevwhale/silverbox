package app.silverbox.develop

import android.graphics.Bitmap
import android.graphics.Canvas
import app.silverbox.domain.Look
import app.silverbox.domain.Stock
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** What can go a little wrong on a roll, the way it did on real film. */
enum class Defect {
    NONE,
    /** The first frame, half fogged by the light that got in while loading. */
    FIRST,
    /** A stray leak of light from the edge of the frame. */
    LEAK,
}

/** Everything the lab needs to know to print one frame. */
class Exposure(
    val stock: Stock,
    val seed: Int,
    val defect: Defect = Defect.NONE,
    /** 0 for fresh film, up to 1 for film long past its date. */
    val expired: Float = 0f,
    /** The date printed in the corner, as "'26 10 17"; null for none. */
    val stamp: String? = null,
)

/**
 * The darkroom: turns a phone photo into a print from the chosen film. Pure pixel work on the CPU, so it
 * behaves the same on every phone and in tests. A 2400 px print takes well under a second or two.
 */
object Film {
    /** The long side of a print, in pixels. */
    const val LONG = 2400

    /** Chooses where light leaks for frame [number] of a roll. */
    fun defect(number: Int, seed: Int): Defect = when {
        number == 1 -> Defect.FIRST
        Random(seed * 31 + number).nextInt(11) == 0 -> Defect.LEAK
        else -> Defect.NONE
    }

    /** Crops to the 2:3 of a 10×15 print, the way a minilab did, and scales to print size. */
    fun crop(src: Bitmap, long: Int = LONG): Bitmap {
        val landscape = src.width >= src.height
        val (lw, sw) = if (landscape) src.width to src.height else src.height to src.width
        // Keep the whole of the short side when the picture is wider than 3:2, else trim the short side.
        val (cl, cs) = if (lw * 2 >= sw * 3) (sw * 3 / 2) to sw else lw to (lw * 2 / 3)
        val (cw, ch) = if (landscape) cl to cs else cs to cl
        val x = (src.width - cw) / 2
        val y = (src.height - ch) / 2
        val k = min(1f, long.toFloat() / cl)
        val ow = max(1, (cw * k).toInt())
        val oh = max(1, (ch * k).toInt())
        val cut = Bitmap.createBitmap(src, x, y, cw, ch)
        if (ow == cw && oh == ch) return if (cut === src) src.copy(Bitmap.Config.ARGB_8888, true) else cut
        val out = Bitmap.createScaledBitmap(cut, ow, oh, true)
        if (cut !== src) cut.recycle()
        return out
    }

    /** The look of [stock] after it has sat in a camera too long. */
    fun aged(l: Look, a: Float): Look = if (a <= 0f) l else Look(
        wb = l.wb,
        contrast = l.contrast * (1 - 0.22f * a),
        lift = l.lift + 0.08f * a,
        gain = l.gain - 0.05f * a,
        sat = l.sat * (1 - 0.4f * a),
        shadows = floatArrayOf(l.shadows[0] + 0.05f * a, l.shadows[1] - 0.012f * a, l.shadows[2] + 0.045f * a),
        highlights = floatArrayOf(l.highlights[0] + 0.01f * a, l.highlights[1] + 0.02f * a, l.highlights[2] + 0.03f * a),
        grain = l.grain * (1 + 0.7f * a),
        grainSize = l.grainSize * (1 + 0.15f * a),
        halation = l.halation,
        vignette = l.vignette + 0.08f * a,
        bw = l.bw,
    )

    /** Prints [src] (already cropped, see [crop]) as [e] says. Returns a new bitmap. */
    fun develop(src: Bitmap, e: Exposure): Bitmap {
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val w = out.width
        val h = out.height
        val look = aged(e.stock.look, e.expired)
        val rnd = Random(e.seed)
        val px = IntArray(w * h)
        out.getPixels(px, 0, w, 0, 0, w, h)

        tone(px, look, rnd)
        out.setPixels(px, 0, w, 0, 0, w, h)

        // The date is light on the film, so it glows and gets grain like everything else.
        e.stamp?.let { DateStamp.draw(Canvas(out), w, h, it) }
        out.getPixels(px, 0, w, 0, 0, w, h)

        val glow = if (look.halation > 0f) halation(px, w, h) else null
        finish(px, w, h, look, e, glow, rnd)
        out.setPixels(px, 0, w, 0, 0, w, h)
        return out
    }

    /** White balance, the film's curve, its colour and a print-to-print drift in exposure and colour. */
    private fun tone(px: IntArray, look: Look, rnd: Random) {
        val ev = 1f + (rnd.nextFloat() - 0.5f) * 0.12f
        val wr = look.wb[0] * ev * (1f + (rnd.nextFloat() - 0.5f) * 0.03f)
        val wg = look.wb[1] * ev
        val wb = look.wb[2] * ev * (1f + (rnd.nextFloat() - 0.5f) * 0.03f)
        val curve = curve(look)
        val bw = look.bw
        val sat = look.sat
        val sh = look.shadows
        val hi = look.highlights
        for (i in px.indices) {
            val p = px[i]
            var r = ((p shr 16) and 0xFF) / 255f * wr
            var g = ((p shr 8) and 0xFF) / 255f * wg
            var b = (p and 0xFF) / 255f * wb
            if (bw != null) {
                val l = r * bw[0] + g * bw[1] + b * bw[2]
                r = l; g = l; b = l
            }
            r = curve[idx(r)]; g = curve[idx(g)]; b = curve[idx(b)]
            val l = 0.299f * r + 0.587f * g + 0.114f * b
            if (bw == null) {
                r = l + (r - l) * sat
                g = l + (g - l) * sat
                b = l + (b - l) * sat
            }
            val ts = (1 - l) * (1 - l)
            val th = l * l
            r += sh[0] * ts + hi[0] * th
            g += sh[1] * ts + hi[1] * th
            b += sh[2] * ts + hi[2] * th
            px[i] = rgb(r, g, b)
        }
    }

    private const val CURVE_MAX = 1.5f
    private const val CURVE_N = 1024

    private fun idx(v: Float): Int = (v.coerceIn(0f, CURVE_MAX) / CURVE_MAX * (CURVE_N - 1)).toInt()

    /** The film's characteristic curve: a soft shoulder for highlights, contrast in the middle, a lifted toe. */
    fun curve(look: Look): FloatArray = FloatArray(CURVE_N) { i ->
        var x = i.toFloat() / (CURVE_N - 1) * CURVE_MAX
        if (x > 0.8f) x = 0.8f + 0.2f * (1 - exp(-(x - 0.8f) / 0.2f))
        x = x.coerceIn(0f, 1f)
        val s = x * x * (3 - 2 * x)
        val a = ((look.contrast - 1f) * 2.6f).coerceIn(-0.6f, 1f)
        val y = x + (s - x) * a
        look.lift + (look.gain - look.lift) * y.coerceIn(0f, 1f)
    }

    /** The red glow film gets around bright lights, as a blurred map at an eighth of the size. */
    private fun halation(px: IntArray, w: Int, h: Int): Glow {
        val f = 8
        val sw = (w + f - 1) / f
        val shh = (h + f - 1) / f
        val m = FloatArray(sw * shh)
        val n = FloatArray(sw * shh)
        for (y in 0 until h) {
            val row = (y / f) * sw
            for (x in 0 until w) {
                val p = px[y * w + x]
                val l = (0.299f * ((p shr 16) and 0xFF) + 0.587f * ((p shr 8) and 0xFF) + 0.114f * (p and 0xFF)) / 255f
                val k = ((l - 0.7f) / 0.3f).coerceIn(0f, 1f)
                m[row + x / f] += k * k
                n[row + x / f] += 1f
            }
        }
        for (i in m.indices) if (n[i] > 0) m[i] /= n[i]
        repeat(3) { boxBlur(m, sw, shh, 3) }
        return Glow(m, sw, shh, f)
    }

    private class Glow(val m: FloatArray, val w: Int, val h: Int, val f: Int) {
        fun at(x: Int, y: Int): Float {
            val fx = ((x + 0.5f) / f - 0.5f).coerceIn(0f, (w - 1).toFloat())
            val fy = ((y + 0.5f) / f - 0.5f).coerceIn(0f, (h - 1).toFloat())
            val x0 = fx.toInt(); val y0 = fy.toInt()
            val x1 = min(x0 + 1, w - 1); val y1 = min(y0 + 1, h - 1)
            val tx = fx - x0; val ty = fy - y0
            val a = m[y0 * w + x0] + (m[y0 * w + x1] - m[y0 * w + x0]) * tx
            val b = m[y1 * w + x0] + (m[y1 * w + x1] - m[y1 * w + x0]) * tx
            return a + (b - a) * ty
        }
    }

    private fun boxBlur(m: FloatArray, w: Int, h: Int, r: Int) {
        val tmp = FloatArray(m.size)
        for (y in 0 until h) {
            var acc = 0f
            for (x in -r..r) acc += m[y * w + x.coerceIn(0, w - 1)]
            for (x in 0 until w) {
                tmp[y * w + x] = acc / (2 * r + 1)
                acc += m[y * w + (x + r + 1).coerceAtMost(w - 1)] - m[y * w + (x - r).coerceAtLeast(0)]
            }
        }
        for (x in 0 until w) {
            var acc = 0f
            for (y in -r..r) acc += tmp[y.coerceIn(0, h - 1) * w + x]
            for (y in 0 until h) {
                m[y * w + x] = acc / (2 * r + 1)
                acc += tmp[(y + r + 1).coerceAtMost(h - 1) * w + x] - tmp[(y - r).coerceAtLeast(0) * w + x]
            }
        }
    }

    /** Softness, glow, vignette, light leaks and grain, in one pass. */
    private fun finish(px: IntArray, w: Int, h: Int, look: Look, e: Exposure, glow: Glow?, rnd: Random) {
        val src = px.copyOf()
        val grain = Noise(w, h, look.grainSize, rnd.nextInt())
        val chroma = if (look.bw == null) Noise(w, h, look.grainSize * 1.6f, rnd.nextInt()) else null
        val cx = w / 2f
        val cy = h / 2f
        val diag = sqrt(cx * cx + cy * cy)
        // Film runs along the long side, so leaks come in from its ends.
        val along = w >= h
        val len = if (along) w else h
        val leakFrom = when (e.defect) {
            Defect.FIRST -> 0
            Defect.LEAK -> if (rnd.nextBoolean()) 0 else 1
            else -> -1
        }
        val leakWidth = len * if (e.defect == Defect.FIRST) 0.42f else 0.2f
        val leakPower = if (e.defect == Defect.FIRST) 1f else 0.62f
        val leakPhase = rnd.nextFloat() * 6.28f
        val hal = look.halation
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = y * w + x
                val p = src[i]
                // A touch of softness: phone pictures are sharper than any print.
                val q1 = src[if (x > 0) i - 1 else i]
                val q2 = src[if (x < w - 1) i + 1 else i]
                val q3 = src[if (y > 0) i - w else i]
                val q4 = src[if (y < h - 1) i + w else i]
                var r = soft(p, q1, q2, q3, q4, 16)
                var g = soft(p, q1, q2, q3, q4, 8)
                var b = soft(p, q1, q2, q3, q4, 0)

                if (glow != null) {
                    val gl = glow.at(x, y) * hal
                    r += gl * 0.95f; g += gl * 0.3f; b += gl * 0.12f
                }

                val dx = x - cx
                val dy = y - cy
                val d = sqrt(dx * dx + dy * dy) / diag
                val v = 1f - look.vignette * d.toDouble().pow(2.4).toFloat()
                r *= v; g *= v; b *= v

                val t = if (along) x.toFloat() else y.toFloat()
                val across = if (along) y.toFloat() / h else x.toFloat() / w
                if (leakFrom >= 0) {
                    val from = if (leakFrom == 0) t else len - 1 - t
                    val k = exp(-(from / leakWidth).toDouble().pow(1.6)).toFloat() * leakPower * (0.82f + 0.18f * sin(across * 4.1f + leakPhase))
                    if (k > 0.002f) {
                        // Hot white-yellow at the edge, through orange, to a deep red.
                        val lr = 1f
                        val lg = 0.18f + 0.72f * k
                        val lb = 0.06f + 0.45f * k * k
                        r = 1 - (1 - r) * (1 - lr * k)
                        g = 1 - (1 - g) * (1 - lg * k)
                        b = 1 - (1 - b) * (1 - lb * k)
                    }
                }
                val l = (0.299f * r + 0.587f * g + 0.114f * b).coerceIn(0f, 1f)
                // Grain shows most in the mid-tones and hardly at all in the deepest shadows.
                val amt = look.grain * (0.3f + 2.8f * l * (1 - l))
                val n = grain.at(x, y) * amt
                r += n; g += n; b += n
                if (chroma != null) {
                    val c = chroma.at(x, y) * amt * 0.35f
                    r += c; b -= c
                }
                px[i] = rgb(r, g, b)
            }
        }
    }

    private fun soft(p: Int, a: Int, b: Int, c: Int, d: Int, s: Int): Float {
        val me = (p shr s) and 0xFF
        val around = ((a shr s) and 0xFF) + ((b shr s) and 0xFF) + ((c shr s) and 0xFF) + ((d shr s) and 0xFF)
        return (me * 0.62f + around * 0.095f) / 255f
    }

    /** Smooth random field, roughly normal, sampled bilinearly from a grid [size] pixels apart. */
    private class Noise(w: Int, h: Int, size: Float, seed: Int) {
        private val step = max(1f, size)
        private val gw = (w / step).toInt() + 2
        private val gh = (h / step).toInt() + 2
        private val v = FloatArray(gw * gh).also {
            val r = Random(seed)
            for (i in it.indices) it[i] = (r.nextFloat() + r.nextFloat() + r.nextFloat() - 1.5f) * 1.2f
        }

        fun at(x: Int, y: Int): Float {
            val fx = x / step
            val fy = y / step
            val x0 = fx.toInt(); val y0 = fy.toInt()
            val tx = fx - x0; val ty = fy - y0
            val a = v[y0 * gw + x0] + (v[y0 * gw + x0 + 1] - v[y0 * gw + x0]) * tx
            val b = v[(y0 + 1) * gw + x0] + (v[(y0 + 1) * gw + x0 + 1] - v[(y0 + 1) * gw + x0]) * tx
            return a + (b - a) * ty
        }
    }

    private fun rgb(r: Float, g: Float, b: Float): Int =
        (0xFF shl 24) or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)

    private fun ch(v: Float): Int = (v * 255f + 0.5f).toInt().coerceIn(0, 255)
}
