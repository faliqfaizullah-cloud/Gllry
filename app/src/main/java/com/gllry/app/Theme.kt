package com.gllry.app

import android.content.Context
import android.graphics.Typeface
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Pure-black OLED colour scheme (Material 3 dark roles, tonal accents). */
val OledScheme = darkColorScheme(
    primary = Color(0xFFD0BCFF), onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B), onPrimaryContainer = Color(0xFFEADDFF),
    secondary = Color(0xFFCCC2DC), onSecondary = Color(0xFF332D41),
    tertiary = Color(0xFFEFB8C8), onTertiary = Color(0xFF492532),
    background = Color.Black, onBackground = Color(0xFFE6E1E5),
    surface = Color.Black, onSurface = Color(0xFFE6E1E5),
    surfaceVariant = Color(0xFF1D1B20), onSurfaceVariant = Color(0xFFCAC4D0),
    surfaceContainer = Color(0xFF0B0B0D), surfaceContainerHigh = Color(0xFF141316),
    surfaceContainerHighest = Color(0xFF1D1B20), outline = Color(0xFF49454F)
)

/** Material 3 Expressive uses larger, rounder corner steps. */
val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(22.dp), large = RoundedCornerShape(32.dp),
    extraLarge = RoundedCornerShape(44.dp)
)

/** Material 3 Expressive motion scheme: bouncy "spatial" springs, calm "effects" springs. */
object Motion {
    fun <T> spatialFast() = spring<T>(0.6f, 800f)
    fun <T> spatial() = spring<T>(0.8f, 380f)
    fun <T> spatialSlow() = spring<T>(0.8f, 200f)
    fun <T> effects() = spring<T>(1f, 1600f)
}

/**
 * Fonts are loaded from assets/fonts (run get-fonts.sh once). If a file is missing the
 * app quietly falls back to system fonts, so it never crashes because of a font.
 *   ui.ttf     -> Instrument Sans  (titles, labels)
 *   pixel.ttf  -> Doto             (dot-matrix dates)
 *   script.ttf -> Gaegu            (handwritten captions)
 */
object AppFonts {
    private var app: Context? = null
    fun init(c: Context) { app = c.applicationContext }

    private fun valid(file: String): Boolean {
        val c = app ?: return false
        return runCatching {
            val big = c.assets.open("fonts/$file").use { it.available() > 5000 }
            big && Typeface.createFromAsset(c.assets, "fonts/$file") != null
        }.getOrDefault(false)
    }

    private fun family(file: String, weights: List<Int>): FontFamily? {
        val c = app ?: return null
        if (!valid(file)) return null
        return runCatching {
            FontFamily(weights.map { w ->
                Font("fonts/$file", c.assets, FontWeight(w), FontStyle.Normal, FontVariation.Settings(FontVariation.weight(w)))
            })
        }.getOrNull()
    }

    val ui: FontFamily by lazy { family("ui.ttf", listOf(400, 500, 600)) ?: FontFamily.SansSerif }
    val pixel: FontFamily by lazy { family("pixel.ttf", listOf(400)) ?: FontFamily.Monospace }
    val script: FontFamily by lazy { family("script.ttf", listOf(400)) ?: FontFamily.Cursive }
}
