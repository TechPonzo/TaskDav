package app.taskdav.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.max
import kotlin.math.min

/** Default brand seed (Forest green). */
val DEFAULT_SEED_ARGB: Int = (0xFF shl 24) or (0x1F shl 16) or (0x6B shl 8) or 0x4A

/**
 * Builds light + dark Material color schemes from a single seed RGB.
 * Lighter/darker tonal roles are derived from the seed's hue.
 */
fun schemesFromSeed(seed: Color): Pair<ColorScheme, ColorScheme> {
    val hsl = seed.toHsl()
    val h = hsl.h
    val s = hsl.s.coerceIn(0.28f, 0.78f)
    val tertiaryH = (h + 38f) % 360f
    val secondaryS = (s * 0.45f).coerceIn(0.12f, 0.42f)

    val light = lightColorScheme(
        primary = tone(h, s, 0.34f),
        onPrimary = Color.White,
        primaryContainer = tone(h, (s * 0.55f).coerceAtMost(0.5f), 0.88f),
        onPrimaryContainer = tone(h, s, 0.16f),
        secondary = tone(h, secondaryS, 0.38f),
        onSecondary = Color.White,
        secondaryContainer = tone(h, secondaryS * 0.7f, 0.90f),
        onSecondaryContainer = tone(h, secondaryS, 0.14f),
        tertiary = tone(tertiaryH, s * 0.7f, 0.38f),
        onTertiary = Color.White,
        tertiaryContainer = tone(tertiaryH, s * 0.45f, 0.90f),
        onTertiaryContainer = tone(tertiaryH, s * 0.7f, 0.14f),
        error = Color(0xFFBA1A1A),
        onError = Color.White,
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        background = tone(h, 0.06f, 0.97f),
        onBackground = tone(h, 0.10f, 0.12f),
        surface = tone(h, 0.06f, 0.97f),
        onSurface = tone(h, 0.10f, 0.12f),
        surfaceVariant = tone(h, 0.10f, 0.90f),
        onSurfaceVariant = tone(h, 0.12f, 0.32f),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = tone(h, 0.05f, 0.95f),
        surfaceContainer = tone(h, 0.06f, 0.93f),
        surfaceContainerHigh = tone(h, 0.07f, 0.91f),
        surfaceContainerHighest = tone(h, 0.08f, 0.88f),
        outline = tone(h, 0.08f, 0.48f),
        outlineVariant = tone(h, 0.08f, 0.78f),
        inverseSurface = tone(h, 0.08f, 0.20f),
        inverseOnSurface = tone(h, 0.06f, 0.94f),
        inversePrimary = tone(h, s * 0.7f, 0.72f),
        scrim = Color.Black,
    )

    val dark = darkColorScheme(
        primary = tone(h, s * 0.75f, 0.72f),
        onPrimary = tone(h, s, 0.14f),
        primaryContainer = tone(h, s * 0.7f, 0.24f),
        onPrimaryContainer = tone(h, s * 0.5f, 0.88f),
        secondary = tone(h, secondaryS, 0.72f),
        onSecondary = tone(h, secondaryS, 0.16f),
        secondaryContainer = tone(h, secondaryS, 0.26f),
        onSecondaryContainer = tone(h, secondaryS * 0.8f, 0.90f),
        tertiary = tone(tertiaryH, s * 0.6f, 0.74f),
        onTertiary = tone(tertiaryH, s * 0.6f, 0.16f),
        tertiaryContainer = tone(tertiaryH, s * 0.55f, 0.26f),
        onTertiaryContainer = tone(tertiaryH, s * 0.45f, 0.90f),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        background = tone(h, 0.08f, 0.08f),
        onBackground = tone(h, 0.06f, 0.90f),
        surface = tone(h, 0.08f, 0.08f),
        onSurface = tone(h, 0.06f, 0.90f),
        surfaceVariant = tone(h, 0.10f, 0.28f),
        onSurfaceVariant = tone(h, 0.08f, 0.78f),
        surfaceContainerLowest = tone(h, 0.08f, 0.05f),
        surfaceContainerLow = tone(h, 0.08f, 0.12f),
        surfaceContainer = tone(h, 0.08f, 0.14f),
        surfaceContainerHigh = tone(h, 0.08f, 0.18f),
        surfaceContainerHighest = tone(h, 0.08f, 0.22f),
        outline = tone(h, 0.08f, 0.58f),
        outlineVariant = tone(h, 0.10f, 0.28f),
        inverseSurface = tone(h, 0.06f, 0.90f),
        inverseOnSurface = tone(h, 0.08f, 0.18f),
        inversePrimary = tone(h, s, 0.34f),
        scrim = Color.Black,
    )

    return light to dark
}

fun Color.contrastingOnColor(): Color =
    if (luminance() > 0.45f) Color(0xFF161D19) else Color.White

private fun tone(h: Float, s: Float, l: Float): Color =
    hslToColor(h, s.coerceIn(0f, 1f), l.coerceIn(0f, 1f))

private data class Hsl(val h: Float, val s: Float, val l: Float)

private fun Color.toHsl(): Hsl {
    val r = red
    val g = green
    val b = blue
    val max = max(r, max(g, b))
    val min = min(r, min(g, b))
    val l = (max + min) / 2f
    if (max == min) return Hsl(0f, 0f, l)
    val d = max - min
    val s = if (l > 0.5f) d / (2f - max - min) else d / (max + min)
    val h = when (max) {
        r -> ((g - b) / d + if (g < b) 6f else 0f) / 6f
        g -> ((b - r) / d + 2f) / 6f
        else -> ((r - g) / d + 4f) / 6f
    } * 360f
    return Hsl(h, s, l)
}

private fun hslToColor(h: Float, s: Float, l: Float): Color {
    if (s <= 0.0001f) return Color(l, l, l)
    val c = (1f - kotlin.math.abs(2f * l - 1f)) * s
    val hp = h / 60f
    val x = c * (1f - kotlin.math.abs(hp % 2f - 1f))
    val (r1, g1, b1) = when {
        hp < 1f -> Triple(c, x, 0f)
        hp < 2f -> Triple(x, c, 0f)
        hp < 3f -> Triple(0f, c, x)
        hp < 4f -> Triple(0f, x, c)
        hp < 5f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    val m = l - c / 2f
    return Color(
        red = (r1 + m).coerceIn(0f, 1f),
        green = (g1 + m).coerceIn(0f, 1f),
        blue = (b1 + m).coerceIn(0f, 1f),
    )
}
