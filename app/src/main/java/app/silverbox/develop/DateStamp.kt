package app.silverbox.develop

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import java.time.Instant
import java.time.ZoneId
import kotlin.math.min

/**
 * The orange date a point-and-shoot burned into the corner of the frame: seven-segment digits, a little
 * slanted, glowing. It sits in the bottom-right corner of the film frame, so on an upright picture it runs
 * up the right-hand edge.
 */
object DateStamp {
    /** "'26 10 17", as the cameras printed it. */
    fun text(ms: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val d = Instant.ofEpochMilli(ms).atZone(zone)
        return "'%02d %02d %02d".format(d.year % 100, d.monthValue, d.dayOfMonth)
    }

    // Segments a..g, clockwise from the top, then the middle.
    private val digits = intArrayOf(
        0b0111111, 0b0000110, 0b1011011, 0b1001111, 0b1100110,
        0b1101101, 0b1111101, 0b0000111, 0b1111111, 0b1101111,
    )

    fun draw(canvas: Canvas, w: Int, h: Int, text: String) {
        val upright = h > w
        val short = min(w, h).toFloat()
        val digitH = short * 0.05f
        val digitW = digitH * 0.56f
        val gap = digitH * 0.24f
        val total = text.sumOf { c -> (if (c == '\'') digitW * 0.35f else if (c == ' ') digitW * 0.6f else digitW).toDouble() }.toFloat() + gap * (text.length - 1)
        val margin = short * 0.055f

        canvas.save()
        if (upright) {
            // The film frame is turned: its bottom-right corner is now at the right edge, reading upwards.
            canvas.translate(w - margin, h - margin * 1.4f)
            canvas.rotate(-90f)
            canvas.translate(0f, -digitH)
        } else {
            canvas.translate(w - margin * 1.4f - total, h - margin - digitH)
        }
        canvas.skew(-0.1f, 0f)

        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xB0FF5A12.toInt()
            maskFilter = BlurMaskFilter(digitH * 0.32f, BlurMaskFilter.Blur.NORMAL)
        }
        val core = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xF2FFA040.toInt()
            maskFilter = BlurMaskFilter(digitH * 0.035f, BlurMaskFilter.Blur.NORMAL)
        }
        for (p in listOf(glow, core)) {
            var x = 0f
            text.forEach { c ->
                when {
                    c == '\'' -> {
                        val t = digitH * 0.13f
                        canvas.drawRoundRect(RectF(x + digitW * 0.05f, 0f, x + digitW * 0.05f + t, digitH * 0.3f), t / 3, t / 3, p)
                        x += digitW * 0.35f + gap
                    }
                    c == ' ' -> x += digitW * 0.6f + gap
                    c.isDigit() -> {
                        segments(canvas, x, digitW, digitH, digits[c - '0'], p)
                        x += digitW + gap
                    }
                }
            }
        }
        canvas.restore()
    }

    private fun segments(canvas: Canvas, x: Float, w: Float, h: Float, mask: Int, p: Paint) {
        val t = h * 0.13f
        val s = t * 0.35f
        val half = h / 2
        val r = t / 2.5f
        fun seg(i: Int, l: Float, top: Float, rr: Float, b: Float) {
            if (mask and (1 shl i) != 0) canvas.drawRoundRect(RectF(x + l, top, x + rr, b), r, r, p)
        }
        seg(0, t * 0.6f + s, 0f, w - t * 0.6f - s, t) // a
        seg(1, w - t, t * 0.5f + s, w, half - s) // b
        seg(2, w - t, half + s, w, h - t * 0.5f - s) // c
        seg(3, t * 0.6f + s, h - t, w - t * 0.6f - s, h) // d
        seg(4, 0f, half + s, t, h - t * 0.5f - s) // e
        seg(5, 0f, t * 0.5f + s, t, half - s) // f
        seg(6, t * 0.6f + s, half - t / 2, w - t * 0.6f - s, half + t / 2) // g
    }
}
