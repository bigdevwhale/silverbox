package app.silverbox.domain

/** A photo roll or a home-movie cassette. */
enum class Format { PHOTO, MOVIE }

/**
 * How a film stock renders: white balance, tone curve, colour, grain and glow. Numbers are in linear-ish
 * 0..1 display space; tints are added in the shadows and highlights.
 */
class Look(
    val wb: FloatArray = floatArrayOf(1f, 1f, 1f),
    val contrast: Float = 1f,
    /** The black point: film never prints a true black. */
    val lift: Float = 0.03f,
    /** The white point. */
    val gain: Float = 0.97f,
    val sat: Float = 1f,
    val shadows: FloatArray = floatArrayOf(0f, 0f, 0f),
    val highlights: FloatArray = floatArrayOf(0f, 0f, 0f),
    /** Grain strength and size in pixels of a 2400 px print. */
    val grain: Float = 0.05f,
    val grainSize: Float = 1.3f,
    /** Red glow around bright lights. */
    val halation: Float = 0.15f,
    val vignette: Float = 0.22f,
    /** Black and white, mixed from the channels with these weights. */
    val bw: FloatArray? = null,
)

/** The film stocks on the shelf. Names are the app's own. */
enum class Stock(val format: Format, val iso: Int, val box: Long, val stripe: Long, val look: Look) {
    SUNNY(
        Format.PHOTO, 200, 0xFFE3A72F, 0xFFC4462C,
        Look(
            wb = floatArrayOf(1.07f, 1.0f, 0.86f), contrast = 1.12f, lift = 0.04f, gain = 0.97f, sat = 1.12f,
            shadows = floatArrayOf(0.025f, 0.008f, -0.012f), highlights = floatArrayOf(0.03f, 0.018f, -0.03f),
            grain = 0.05f, grainSize = 1.25f, halation = 0.18f, vignette = 0.26f,
        ),
    ),
    EVERY(
        Format.PHOTO, 400, 0xFF2F7A6B, 0xFFE8C766,
        Look(
            wb = floatArrayOf(1.02f, 1.0f, 0.95f), contrast = 1.05f, lift = 0.05f, gain = 0.96f, sat = 0.95f,
            shadows = floatArrayOf(-0.012f, 0.016f, 0.01f), highlights = floatArrayOf(0.022f, 0.012f, -0.012f),
            grain = 0.07f, grainSize = 1.45f, halation = 0.12f, vignette = 0.2f,
        ),
    ),
    NIGHT(
        Format.PHOTO, 800, 0xFF1F2A44, 0xFFE0573A,
        Look(
            wb = floatArrayOf(0.97f, 1.0f, 1.05f), contrast = 1.1f, lift = 0.06f, gain = 0.97f, sat = 1.06f,
            shadows = floatArrayOf(-0.02f, 0.012f, 0.032f), highlights = floatArrayOf(0.045f, 0.004f, -0.02f),
            grain = 0.12f, grainSize = 1.85f, halation = 0.6f, vignette = 0.3f,
        ),
    ),
    SILVER(
        Format.PHOTO, 400, 0xFFE9E4D8, 0xFF1D1D1D,
        Look(
            contrast = 1.26f, lift = 0.05f, gain = 0.96f, grain = 0.11f, grainSize = 1.6f, halation = 0f, vignette = 0.32f,
            bw = floatArrayOf(0.42f, 0.44f, 0.14f),
        ),
    ),
    SLIDE(
        Format.PHOTO, 100, 0xFF2A62B8, 0xFFF2D04A,
        Look(
            wb = floatArrayOf(1.0f, 1.0f, 1.01f), contrast = 1.28f, lift = 0.012f, gain = 0.99f, sat = 1.3f,
            shadows = floatArrayOf(-0.01f, 0f, 0.03f), highlights = floatArrayOf(0.012f, 0.01f, 0f),
            grain = 0.035f, grainSize = 1.0f, halation = 0.1f, vignette = 0.36f,
        ),
    ),
    HOME8(
        Format.MOVIE, 40, 0xFFD8482E, 0xFFF0C24B,
        Look(
            wb = floatArrayOf(1.06f, 1.0f, 0.88f), contrast = 1.18f, lift = 0.05f, gain = 0.95f, sat = 1.22f,
            shadows = floatArrayOf(0.02f, 0.005f, -0.01f), highlights = floatArrayOf(0.035f, 0.02f, -0.035f),
            grain = 0.09f, grainSize = 1.6f, halation = 0.2f, vignette = 0.4f,
        ),
    ),
    SILVER8(
        Format.MOVIE, 100, 0xFF2B2B2B, 0xFFE9E4D8,
        Look(contrast = 1.3f, lift = 0.06f, gain = 0.94f, grain = 0.12f, grainSize = 1.7f, halation = 0f, vignette = 0.45f, bw = floatArrayOf(0.4f, 0.45f, 0.15f)),
    ),
    /** A video tape from the camcorder years: the lab copies the clips onto it, not onto film. */
    VHS(
        Format.MOVIE, 180, 0xFF1C1C1E, 0xFFE8473C,
        Look(
            wb = floatArrayOf(1.0f, 0.99f, 1.04f), contrast = 0.94f, lift = 0.07f, gain = 0.93f, sat = 1.28f,
            shadows = floatArrayOf(-0.01f, 0.01f, 0.035f), highlights = floatArrayOf(0.03f, 0.01f, -0.02f),
            grain = 0f, halation = 0f, vignette = 0.12f,
        ),
    ),
    ;

    val bw: Boolean get() = look.bw != null
    /** Video tape rather than film. */
    val tape: Boolean get() = this == VHS

    companion object {
        val photo = entries.filter { it.format == Format.PHOTO }
        val movie = entries.filter { it.format == Format.MOVIE }
    }
}
