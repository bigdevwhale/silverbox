package app.silverbox.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.silverbox.R

/**
 * A photo shop counter in the nineties: cream paper, mustard film boxes, a deep teal sign, kraft
 * envelopes and the orange of a date stamp.
 */
@Immutable
data class Palette(
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val line: Color,
    val ink: Color,
    val muted: Color,
    val faint: Color,
    val mustard: Color,
    val mustardSoft: Color,
    val onMustard: Color,
    val teal: Color,
    val tealSoft: Color,
    val brick: Color,
    val brickSoft: Color,
    val stamp: Color,
    val kraft: Color,
    val kraftDark: Color,
    val paper: Color,
    val film: Color,
    val danger: Color,
)

val ShopPalette = Palette(
    bg = Color(0xFFF2EADB),
    surface = Color(0xFFFBF6EC),
    surface2 = Color(0xFFEAE0CC),
    line = Color(0xFFDDD0B8),
    ink = Color(0xFF2A231D),
    muted = Color(0xFF6E6255),
    faint = Color(0xFFA3968A),
    mustard = Color(0xFFD9A132),
    mustardSoft = Color(0xFFF3E1B4),
    onMustard = Color(0xFF2A1E08),
    teal = Color(0xFF1E5B5C),
    tealSoft = Color(0xFFD3E2DC),
    brick = Color(0xFFB4492F),
    brickSoft = Color(0xFFF2D6CB),
    stamp = Color(0xFFFF8F2A),
    kraft = Color(0xFFC9A577),
    kraftDark = Color(0xFFA9844F),
    paper = Color(0xFFFCFAF4),
    film = Color(0xFF1B1714),
    danger = Color(0xFFB4492F),
)

val LocalPalette = staticCompositionLocalOf { ShopPalette }

val Nunito = FontFamily(
    Font(R.font.nunito_regular, FontWeight.Normal),
    Font(R.font.nunito_semibold, FontWeight.SemiBold),
    Font(R.font.nunito_bold, FontWeight.Bold),
)

/** A bookish serif for names and titles. */
val Lora = FontFamily(
    Font(R.font.lora_regular, FontWeight.Normal),
    Font(R.font.lora_italic, FontWeight.Normal, FontStyle.Italic),
)

/** Typewriter letters for receipts, counters and labels. */
val Mono = FontFamily(Font(R.font.pt_mono, FontWeight.Normal))

/** Handwriting, for what is written on the backs of prints and the labels of rolls. */
val Hand = FontFamily(Font(R.font.caveat, FontWeight.Normal))

object U {
    val c: Palette @Composable get() = LocalPalette.current

    val RXl = RoundedCornerShape(26.dp)
    val RLg = RoundedCornerShape(20.dp)
    val RMd = RoundedCornerShape(14.dp)
    val RSm = RoundedCornerShape(10.dp)
}

object UType {
    val Display: TextStyle @Composable get() = TextStyle(fontFamily = Lora, fontSize = 30.sp, lineHeight = 36.sp, color = U.c.ink)
    val Title: TextStyle @Composable get() = TextStyle(fontFamily = Lora, fontSize = 23.sp, lineHeight = 29.sp, color = U.c.ink)
    val Name: TextStyle @Composable get() = TextStyle(fontFamily = Lora, fontSize = 18.sp, lineHeight = 23.sp, color = U.c.ink)
    val Sub: TextStyle @Composable get() = TextStyle(fontFamily = Nunito, fontSize = 14.5.sp, lineHeight = 21.sp, color = U.c.muted)
    val Eyebrow: TextStyle @Composable get() = TextStyle(fontFamily = app.silverbox.ui.theme.Mono, fontSize = 11.5.sp, letterSpacing = 0.12.em, color = U.c.muted)
    val Strong: TextStyle @Composable get() = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = U.c.ink)
    val Body: TextStyle @Composable get() = TextStyle(fontFamily = Nunito, fontSize = 15.sp, lineHeight = 22.sp, color = U.c.ink)
    val Muted: TextStyle @Composable get() = TextStyle(fontFamily = Nunito, fontSize = 13.sp, lineHeight = 18.sp, color = U.c.muted)
    val Small: TextStyle @Composable get() = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = U.c.faint)
    val Mono: TextStyle @Composable get() = TextStyle(fontFamily = app.silverbox.ui.theme.Mono, fontSize = 13.sp, lineHeight = 18.sp, color = U.c.ink)
    val Counter: TextStyle @Composable get() = TextStyle(fontFamily = app.silverbox.ui.theme.Mono, fontSize = 30.sp, color = U.c.ink)
    val Hand: TextStyle @Composable get() = TextStyle(fontFamily = app.silverbox.ui.theme.Hand, fontSize = 22.sp, lineHeight = 25.sp, color = Color(0xFF2B3A67))
    val Number: TextStyle @Composable get() = TextStyle(fontFamily = app.silverbox.ui.theme.Mono, fontSize = 24.sp, color = U.c.ink)
    val Button: TextStyle @Composable get() = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 15.sp)
}

@Composable
fun SilverboxTheme(content: @Composable () -> Unit) {
    val p = ShopPalette
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(
            colorScheme = lightColorScheme(
                primary = p.teal,
                onPrimary = p.paper,
                primaryContainer = p.tealSoft,
                onPrimaryContainer = p.ink,
                secondary = p.mustard,
                secondaryContainer = p.mustardSoft,
                onSecondaryContainer = p.ink,
                background = p.bg,
                surface = p.surface,
                surfaceContainer = p.surface,
                surfaceContainerHigh = p.surface,
                surfaceContainerHighest = p.surface2,
                surfaceContainerLow = p.surface2,
                onBackground = p.ink,
                onSurface = p.ink,
                onSurfaceVariant = p.muted,
                outline = p.line,
                outlineVariant = p.line,
                error = p.danger,
            ),
            typography = MaterialTheme.typography.copy(
                bodyLarge = UType.Body,
                bodyMedium = UType.Body.copy(fontSize = 14.sp),
                labelLarge = UType.Button,
            ),
            content = content,
        )
    }
}
