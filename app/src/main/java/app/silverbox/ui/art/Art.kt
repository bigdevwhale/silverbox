package app.silverbox.ui.art

import app.silverbox.ui.theme.Paper
import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.silverbox.domain.Stock
import app.silverbox.ui.theme.Mono
import app.silverbox.ui.theme.Nunito
import app.silverbox.ui.theme.U

/** Light letters on a dark box, dark on a light one. */
fun onBox(c: Long): Color {
    val col = Color(c)
    val l = 0.299f * col.red + 0.587f * col.green + 0.114f * col.blue
    return if (l > 0.6f) Color(0xFF1D1A17) else Color(0xFFFBF6EC)
}

/** The stock's name as it is printed on its box. */
fun stockName(s: Stock): String = when (s) {
    Stock.SUNNY -> "Sunny"
    Stock.EVERY -> "Every"
    Stock.NIGHT -> "Night"
    Stock.SILVER -> "Silver"
    Stock.SLIDE -> "Slide"
    Stock.HOME8 -> "Home 8"
    Stock.SILVER8 -> "Silver 8"
}

/** "Sunny 200", "Home 8". */
fun stockLabel(s: Stock): String = if (s.format == app.silverbox.domain.Format.MOVIE) stockName(s) else stockName(s) + " " + s.iso

/**
 * A box of film, front on: the stock's colour, a bold diagonal band, the name, the speed and the
 * number of exposures. Every stock has its own; none of them is anyone's real trade dress.
 */
@Composable
fun FilmBox(stock: Stock, exposures: Int, modifier: Modifier = Modifier) {
    val box = Color(stock.box)
    val stripe = Color(stock.stripe)
    val ink = onBox(stock.box)
    BoxWithConstraints(
        modifier
            .aspectRatio(0.72f)
            .shadow(6.dp, RoundedCornerShape(6.dp))
            .clip(RoundedCornerShape(6.dp))
            .background(box),
    ) {
        val w = maxWidth
        Canvas(Modifier.fillMaxSize()) {
            // The band across the box and a thin rule beside it.
            val p = Path().apply {
                moveTo(0f, size.height * 0.58f)
                lineTo(size.width, size.height * 0.36f)
                lineTo(size.width, size.height * 0.52f)
                lineTo(0f, size.height * 0.74f)
                close()
            }
            drawPath(p, stripe)
            val q = Path().apply {
                moveTo(0f, size.height * 0.78f)
                lineTo(size.width, size.height * 0.56f)
                lineTo(size.width, size.height * 0.585f)
                lineTo(0f, size.height * 0.805f)
                close()
            }
            drawPath(q, stripe.copy(alpha = 0.75f))
            // Top flap edge and a little shine.
            drawRect(Color.Black.copy(alpha = 0.12f), size = Size(size.width, size.height * 0.035f))
            drawRect(Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.16f), Color.Transparent, Color.Black.copy(alpha = 0.1f))))
            if (stock.bw) {
                // Silver: a fine half-tone of dots along the bottom.
                val step = size.width / 14f
                for (i in 0 until 14) for (j in 0 until 3) drawCircle(ink.copy(alpha = 0.35f - j * 0.1f), step * 0.18f, Offset(step * (i + 0.5f), size.height * (0.9f + j * 0.035f)))
            }
        }
        Column(Modifier.fillMaxSize().padding(horizontal = w * 0.09f, vertical = w * 0.1f)) {
            Text(
                stockName(stock).uppercase(), color = ink, fontFamily = Nunito, fontWeight = FontWeight.Bold,
                fontSize = (w.value * 0.19f).sp, lineHeight = (w.value * 0.2f).sp, letterSpacing = (w.value * 0.004f).sp, maxLines = 1,
            )
            Text(
                if (stock.format == app.silverbox.domain.Format.MOVIE) "SUPER 8" else "COLOR FILM".takeIf { !stock.bw } ?: "B&W FILM",
                color = ink.copy(alpha = 0.8f), fontFamily = Mono, fontSize = (w.value * 0.075f).sp, maxLines = 1,
            )
        }
        Box(Modifier.fillMaxSize().padding(w * 0.08f), contentAlignment = Alignment.BottomStart) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Text(
                    if (stock.format == app.silverbox.domain.Format.MOVIE) "50ft" else stock.iso.toString(),
                    color = ink, fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = (w.value * 0.24f).sp, lineHeight = (w.value * 0.24f).sp,
                )
                Text(
                    if (exposures > 0) "$exposures\nEXP" else "", color = ink, fontFamily = Mono, fontSize = (w.value * 0.08f).sp,
                    lineHeight = (w.value * 0.085f).sp, textAlign = TextAlign.End,
                )
            }
        }
    }
}

/** A 35 mm cartridge lying on its side: the stock's label round a metal can, the film's tongue out of the lip. */
@Composable
fun Canister(stock: Stock, modifier: Modifier = Modifier) {
    Canvas(modifier.aspectRatio(1.5f)) { canister(stock) }
}

fun DrawScope.canister(stock: Stock) {
    val w = size.width
    val h = size.height
    val bodyL = w * 0.12f
    val bodyR = w * 0.68f
    val top = h * 0.12f
    val bot = h * 0.88f
    // The leader, curling out to the right.
    val leader = Path().apply {
        moveTo(bodyR - w * 0.02f, top + h * 0.18f)
        lineTo(w * 0.96f, top + h * 0.2f)
        cubicTo(w * 0.99f, top + h * 0.3f, w * 0.99f, bot - h * 0.32f, w * 0.94f, bot - h * 0.22f)
        lineTo(bodyR - w * 0.02f, bot - h * 0.12f)
        close()
    }
    // The amber-brown of a film base, light enough to read on the dark theme too.
    drawPath(leader, Brush.verticalGradient(listOf(Color(0xFF9A6236), Color(0xFF7A4724), Color(0xFF8C5530)), top, bot))
    drawPath(leader, Color(0xFFC08A55), style = Stroke(w * 0.006f))
    // Perforations along the leader, kept inside its curled edge.
    clipPath(leader) {
        for (i in 0 until 4) {
            val x = bodyR + w * 0.03f + i * w * 0.055f
            drawRoundRect(Color(0xFF2A1509), Offset(x, top + h * 0.235f), Size(w * 0.03f, h * 0.045f), CornerRadius(2f))
            drawRoundRect(Color(0xFF2A1509), Offset(x, bot - h * 0.235f), Size(w * 0.03f, h * 0.045f), CornerRadius(2f))
        }
    }
    // Spool ends.
    val metal = Brush.verticalGradient(listOf(Color(0xFFE6E2DA), Color(0xFF9C978E), Color(0xFFD6D1C8), Color(0xFF7E796F)), top, bot)
    drawRoundRect(metal, Offset(bodyL - w * 0.06f, top + h * 0.06f), Size(w * 0.08f, bot - top - h * 0.12f), CornerRadius(w * 0.02f))
    drawRoundRect(Color(0xFF55514A), Offset(bodyL - w * 0.1f, h * 0.42f), Size(w * 0.06f, h * 0.16f), CornerRadius(w * 0.012f))
    drawRoundRect(metal, Offset(bodyR - w * 0.02f, top + h * 0.06f), Size(w * 0.06f, bot - top - h * 0.12f), CornerRadius(w * 0.02f))
    // The body with its label.
    val box = Color(stock.box)
    val shade = Brush.verticalGradient(
        listOf(Color.White.copy(alpha = 0.28f), Color.Transparent, Color.Black.copy(alpha = 0.05f), Color.Black.copy(alpha = 0.32f)), top, bot,
    )
    drawRoundRect(box, Offset(bodyL, top), Size(bodyR - bodyL, bot - top), CornerRadius(w * 0.03f))
    val band = Path().apply {
        moveTo(bodyL, top + (bot - top) * 0.62f)
        lineTo(bodyR, top + (bot - top) * 0.3f)
        lineTo(bodyR, top + (bot - top) * 0.5f)
        lineTo(bodyL, top + (bot - top) * 0.82f)
        close()
    }
    drawPath(band, Color(stock.stripe))
    drawRoundRect(shade, Offset(bodyL, top), Size(bodyR - bodyL, bot - top), CornerRadius(w * 0.03f))
    // DX code squares.
    for (i in 0 until 6) {
        val c = if ((stock.iso / (i + 1)) % 2 == 0) Color(0xFF2B2B2B) else Color(0xFFCFCAC0)
        drawRect(c, Offset(bodyL + w * 0.03f + i * w * 0.04f, bot - h * 0.11f), Size(w * 0.035f, h * 0.06f))
    }
}

/** A Super 8 cartridge, face on: a square of plastic with the label window and the gate notch. */
@Composable
fun Cassette(stock: Stock, modifier: Modifier = Modifier) {
    Canvas(modifier.aspectRatio(1.1f)) {
        val w = size.width
        val h = size.height
        val body = Color(0xFF2B2826)
        drawRoundRect(Color.Black.copy(alpha = 0.18f), Offset(w * 0.04f, h * 0.06f), Size(w * 0.94f, h * 0.92f), CornerRadius(w * 0.06f))
        drawRoundRect(body, Offset(0f, 0f), Size(w * 0.94f, h * 0.92f), CornerRadius(w * 0.06f))
        drawRoundRect(
            Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.12f), Color.Transparent)), Offset(0f, 0f), Size(w * 0.94f, h * 0.5f),
            CornerRadius(w * 0.06f),
        )
        // The label.
        val lx = w * 0.08f
        val ly = h * 0.1f
        val lw = w * 0.78f
        val lh = h * 0.46f
        drawRoundRect(Color(stock.box), Offset(lx, ly), Size(lw, lh), CornerRadius(w * 0.02f))
        val band = Path().apply {
            moveTo(lx, ly + lh * 0.7f)
            lineTo(lx + lw, ly + lh * 0.25f)
            lineTo(lx + lw, ly + lh * 0.48f)
            lineTo(lx, ly + lh * 0.93f)
            close()
        }
        drawPath(band, Color(stock.stripe))
        // Spool windows and the gate notch.
        drawCircle(Color(0xFF141210), w * 0.11f, Offset(w * 0.27f, h * 0.73f))
        drawCircle(Color(0xFF141210), w * 0.11f, Offset(w * 0.67f, h * 0.73f))
        for (c in listOf(Offset(w * 0.27f, h * 0.73f), Offset(w * 0.67f, h * 0.73f))) {
            for (k in 0 until 6) {
                rotate(k * 60f, c) { drawRect(Color(0xFF3B3733), Offset(c.x - w * 0.012f, c.y - w * 0.1f), Size(w * 0.024f, w * 0.06f)) }
            }
            drawCircle(Color(0xFF55504A), w * 0.035f, c)
        }
        drawRect(Color(0xFF141210), Offset(w * 0.4f, h * 0.84f), Size(w * 0.14f, h * 0.08f))
    }
}

/** A kraft paper envelope from the lab, flap shut or open. */
@Composable
fun Envelope(open: Float, modifier: Modifier = Modifier, content: @Composable () -> Unit = {}) {
    val kraft = U.c.kraft
    val dark = U.c.kraftDark
    Box(modifier.aspectRatio(1.42f)) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawRoundRect(dark, Offset(0f, h * 0.02f), Size(w, h * 0.98f), CornerRadius(w * 0.02f))
            // The inside of the back, seen when the flap is up.
            drawRect(Color.Black.copy(alpha = 0.18f * open), Offset(w * 0.02f, h * 0.04f), Size(w * 0.96f, h * 0.2f))
        }
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            // The front pocket.
            val front = Path().apply {
                moveTo(0f, h * 0.22f)
                lineTo(w * 0.5f, h * 0.5f)
                lineTo(w, h * 0.22f)
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
            drawPath(front, kraft)
            drawPath(front, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.1f), Color.Black.copy(alpha = 0.08f))))
            // Paper fibres.
            val r = java.util.Random(7)
            repeat(70) {
                val x = r.nextFloat() * w
                val y = h * 0.3f + r.nextFloat() * h * 0.7f
                drawLine(dark.copy(alpha = 0.22f), Offset(x, y), Offset(x + r.nextFloat() * w * 0.03f, y + (r.nextFloat() - 0.5f) * h * 0.01f), 1.2f)
            }
            // The flap: folded down over the front when shut, up behind when open.
            val tip = h * (0.62f - 0.82f * open)
            val flap = Path().apply {
                moveTo(0f, h * 0.06f)
                lineTo(w * 0.5f, tip)
                lineTo(w, h * 0.06f)
                close()
            }
            if (open < 0.5f) {
                drawPath(flap, kraft)
                drawPath(flap, Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.06f), Color.White.copy(alpha = 0.08f)), 0f, tip))
                drawPath(flap, dark, style = Stroke(1.5f))
            }
        }
        // The lab's label goes on top of the pocket.
        content()
    }
}

/** The frame counter window of a camera: dark glass, a light number. */
@Composable
fun Counter(n: Int, total: Int, modifier: Modifier = Modifier, size: Dp = 30.dp) {
    Row(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1B1714))
            .border(2.dp, Color(0xFF6E665C), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text("%02d".format(n), fontFamily = Mono, fontSize = size.value.sp, color = Color(0xFFFFB25A))
        Text(" / $total", fontFamily = Mono, fontSize = (size.value * 0.5f).sp, color = Color(0xFFB8AC9C), modifier = Modifier.padding(bottom = (size.value * 0.12f).dp))
    }
}

/** A print with its white border and a soft shadow. [negative] shows it as the film saw it. */
@Composable
fun Print(bitmap: Bitmap?, modifier: Modifier = Modifier, landscape: Boolean = bitmap?.let { it.width >= it.height } ?: true, border: Dp = 7.dp, negative: Boolean = false) {
    Box(
        modifier
            .aspectRatio(if (landscape) 1.5f else 1 / 1.5f)
            .shadow(5.dp, RoundedCornerShape(2.dp))
            .background(Paper.white)
            .padding(border),
    ) {
        if (bitmap != null) {
            Image(
                bitmap.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                colorFilter = if (negative) ColorFilter.colorMatrix(NEGATIVE) else null,
            )
        } else Box(Modifier.fillMaxSize().background(U.c.surface2))
    }
}

/** Inverted and laid on the orange of a colour negative's base. */
val NEGATIVE = ColorMatrix(
    floatArrayOf(
        -0.9f, 0f, 0f, 0f, 245f,
        0f, -0.75f, 0f, 0f, 168f,
        0f, 0f, -0.6f, 0f, 108f,
        0f, 0f, 0f, 1f, 0f,
    )
)

/** A black-and-white negative: inverted, on the grey of its base. */
val NEGATIVE_BW = ColorMatrix(
    floatArrayOf(
        -0.8f, 0f, 0f, 0f, 225f,
        0f, -0.8f, 0f, 0f, 222f,
        0f, 0f, -0.8f, 0f, 215f,
        0f, 0f, 0f, 1f, 0f,
    )
)

/** A strip of film: black base, sprocket holes, edge numbers in orange; [cells] sit in the frames. */
@Composable
fun FilmStrip(count: Int, modifier: Modifier = Modifier, first: Int = 1, cell: @Composable (Int) -> Unit) {
    Column(modifier.fillMaxWidth().background(U.c.film).padding(vertical = 4.dp)) {
        Holes()
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (i in 0 until count) Box(Modifier.weight(1f).aspectRatio(1.5f)) { cell(i) }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            for (i in 0 until count) {
                Text(
                    "${first + i}  ▸ ${first + i}A", Modifier.weight(1f), fontFamily = Mono, fontSize = 8.sp, color = Color(0xFFE88A3A),
                    maxLines = 1, textAlign = TextAlign.Center,
                )
            }
        }
        Holes()
    }
}

@Composable
private fun Holes() {
    Canvas(Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 2.dp).aspectRatio(40f)) {
        val n = 28
        val step = size.width / n
        for (i in 0 until n) drawRoundRect(Color(0xFFEDE5D6), Offset(i * step + step * 0.25f, 0f), Size(step * 0.5f, size.height), CornerRadius(2f))
    }
}
