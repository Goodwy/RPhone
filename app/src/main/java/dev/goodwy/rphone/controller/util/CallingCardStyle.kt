package dev.goodwy.rphone.controller.util

import androidx.annotation.FontRes
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import dev.goodwy.rphone.R
import java.util.Locale

/**
 * Font presets available for the contact name on a calling card.
 *
 * Every bundled font is licensed under the SIL Open Font License 1.1. The
 * licence texts ship in `assets/calling_card_fonts/OFL.txt`.
 */
enum class CallingCardFont(
    val id: String,
    @StringRes val labelRes: Int,
    @FontRes val fontRes: Int? = null
) {
    System("system", R.string.calling_card_font_system),
    Inter("inter", R.string.calling_card_font_inter, R.font.calling_card_inter),
    Lora("lora", R.string.calling_card_font_lora, R.font.calling_card_lora),
    Playfair("playfair", R.string.calling_card_font_playfair, R.font.calling_card_playfair),
    Mono("mono", R.string.calling_card_font_mono, R.font.calling_card_mono),
    Caveat("caveat", R.string.calling_card_font_caveat, R.font.calling_card_caveat);

    val family: FontFamily by lazy {
        fontRes?.let { FontFamily(Font(it, weight = FontWeight.Normal)) } ?: FontFamily.Default
    }

    companion object {
        fun fromId(id: String?): CallingCardFont =
            entries.firstOrNull { it.id == id } ?: System
    }
}

/**
 * Per-contact or global calling card appearance.
 *
 * [textColorArgb] is null when the name should use the app theme colour, which
 * keeps every screen readable without any customization.
 */
data class CallingCardStyle(
    val font: CallingCardFont = CallingCardFont.System,
    val textColorArgb: Int? = null
) {
    val isThemeDefault: Boolean
        get() = font == CallingCardFont.System && textColorArgb == null

    val textColor: Color?
        get() = textColorArgb?.let { Color(it) }

    companion object {
        val ThemeDefault = CallingCardStyle()
    }
}

/**
 * Stable string form used in DataStore: `fontId|AARRGGBB` (colour may be empty).
 */
object CallingCardStyleCodec {

    private const val SEPARATOR = "|"

    fun encode(style: CallingCardStyle): String {
        val color = style.textColorArgb
            ?.let { String.format(Locale.US, "%08X", it) }
            .orEmpty()
        return style.font.id + SEPARATOR + color
    }

    fun decode(raw: String?): CallingCardStyle? {
        if (raw.isNullOrBlank()) return null
        val parts = raw.split(SEPARATOR, limit = 2)
        val font = CallingCardFont.fromId(parts.getOrNull(0))
        val color = parts.getOrNull(1)
            ?.takeIf { it.isNotBlank() }
            ?.let { hex -> runCatching { hex.toLong(16).toInt() }.getOrNull() }
        return CallingCardStyle(font = font, textColorArgb = color)
    }
}

/**
 * A soft shadow keeps the contact name readable on busy photos without
 * changing the theme colour of the phone number.
 */
fun callingCardNameShadow(color: Color, hasBackground: Boolean): Shadow? {
    if (!hasBackground) return null
    val shadowColor = if (color.luminance() > 0.5f) {
        Color.Black.copy(alpha = 0.55f)
    } else {
        Color.White.copy(alpha = 0.55f)
    }
    return Shadow(
        color = shadowColor,
        offset = Offset(0f, 2f),
        blurRadius = 12f
    )
}
