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
    @FontRes val fontRes: Int? = null,
    val minWeight: Int = 100,
    val maxWeight: Int = 900
) {
    System("system", R.string.calling_card_font_system),
    Inter("inter", R.string.calling_card_font_inter, R.font.calling_card_inter_medium, 300, 800),
    Lora("lora", R.string.calling_card_font_lora, R.font.calling_card_lora_medium, 400, 700),
    Playfair("playfair", R.string.calling_card_font_playfair, R.font.calling_card_playfair_medium, 300, 800),
    Mono("mono", R.string.calling_card_font_mono, R.font.calling_card_mono_medium, 300, 800),
    Caveat("caveat", R.string.calling_card_font_caveat, R.font.calling_card_caveat_medium, 400, 700);

    val family: FontFamily by lazy {
//        fontRes?.let { FontFamily(Font(it, weight = FontWeight.Normal)) } ?: FontFamily.Default

        when (this) {
            System -> FontFamily.Default

            Inter -> FontFamily(
                Font(R.font.calling_card_inter_light,  FontWeight.Light),   // 300
                Font(R.font.calling_card_inter_regular,  FontWeight.Normal),   // 400
                Font(R.font.calling_card_inter_medium,   FontWeight.Medium),   // 500
                Font(R.font.calling_card_inter_semibold, FontWeight.SemiBold), // 600
                Font(R.font.calling_card_inter_bold,     FontWeight.Bold),      // 700
                Font(R.font.calling_card_inter_extrabold,     FontWeight.ExtraBold)      // 800
            )

            Lora -> FontFamily(
                Font(R.font.calling_card_lora_regular,  FontWeight.Normal),    // 400
                Font(R.font.calling_card_lora_medium,   FontWeight.Medium),    // 500
                Font(R.font.calling_card_lora_semibold, FontWeight.SemiBold),  // 600
                Font(R.font.calling_card_lora_bold,     FontWeight.Bold),       // 700
            )

            Playfair -> FontFamily(
                Font(R.font.calling_card_playfair_light,  FontWeight.Light),   // 300
                Font(R.font.calling_card_playfair_regular,   FontWeight.Normal),   // 400
                Font(R.font.calling_card_playfair_medium,    FontWeight.Medium),   // 500
                Font(R.font.calling_card_playfair_semibold,  FontWeight.SemiBold), // 600
                Font(R.font.calling_card_playfair_bold,      FontWeight.Bold),      // 700
                Font(R.font.calling_card_playfair_extrabold,     FontWeight.ExtraBold)      // 800
            )

            Mono -> FontFamily(
                Font(R.font.calling_card_mono_light,  FontWeight.Light),   // 300
                Font(R.font.calling_card_mono_regular,  FontWeight.Normal),   // 400
                Font(R.font.calling_card_mono_medium,   FontWeight.Medium),   // 500
                Font(R.font.calling_card_mono_semibold,  FontWeight.SemiBold), // 600
                Font(R.font.calling_card_mono_bold,     FontWeight.Bold),      // 700
                Font(R.font.calling_card_mono_extrabold,     FontWeight.ExtraBold)      // 800
            )

            Caveat -> FontFamily(
                Font(R.font.calling_card_caveat_regular, FontWeight.Normal),  // 400
                Font(R.font.calling_card_caveat_medium,  FontWeight.Medium),  // 500
                Font(R.font.calling_card_caveat_semibold, FontWeight.SemiBold), // 600
                Font(R.font.calling_card_caveat_bold,    FontWeight.Bold)     // 700
            )
        }
    }

    companion object {
        fun fromId(id: String?): CallingCardFont =
            entries.firstOrNull { it.id == id } ?: System
    }
}

/**
 * Per-contact or global calling card appearance. (font|weight|fontSize|color|blur)
 *
 * [textColorArgb] is null when the name should use the app theme colour, which
 * keeps every screen readable without any customization.
 */
data class CallingCardStyle(
    val font: CallingCardFont = CallingCardFont.System,
    val fontWeight: Int = DEFAULT_WEIGHT,
    val fontSizeSp: Int = DEFAULT_FONT_SIZE_SP,
    val textColorArgb: Int? = null,
    val blurRadiusDp: Int = 0
) {
    val isThemeDefault: Boolean
        get() = font == CallingCardFont.System &&
                fontWeight == DEFAULT_WEIGHT &&
                fontSizeSp == DEFAULT_FONT_SIZE_SP &&
                textColorArgb == null &&
                blurRadiusDp == 0

    val textColor: Color?
        get() = textColorArgb?.let { Color(it) }
    val weight: FontWeight
        get() = FontWeight(fontWeight.coerceIn(100, 900))

    companion object {
        val ThemeDefault = CallingCardStyle()
        const val MAX_BLUR_DP = 25
        const val DEFAULT_WEIGHT = 500
        const val WEIGHT_STEP = 100
        const val DEFAULT_FONT_SIZE_SP = 24
        const val MIN_FONT_SIZE_SP = 14
        const val MAX_FONT_SIZE_SP = 56
    }
}

/**
 * Stable string form used in DataStore: `fontId|AARRGGBB` (colour may be empty).
 */
object CallingCardStyleCodec {

    private const val SEPARATOR = "|"

    fun encode(style: CallingCardStyle): String = buildString {
        append(style.font.id).append(SEPARATOR)
        append(style.fontWeight).append(SEPARATOR)
        append(style.fontSizeSp).append(SEPARATOR)
        append(style.textColorArgb?.let { String.format(Locale.US, "%08X", it) }.orEmpty())
            .append(SEPARATOR)
        append(style.blurRadiusDp)
    }

    fun decode(raw: String?): CallingCardStyle? {
        if (raw.isNullOrBlank()) return null
        val parts = raw.split(SEPARATOR)
        val font = CallingCardFont.fromId(parts.getOrNull(0))
        val weight = parts.getOrNull(1)
            ?.toIntOrNull()
            ?.coerceIn(100, 900)
            ?: CallingCardStyle.DEFAULT_WEIGHT
        val size = parts.getOrNull(2)
            ?.toIntOrNull()
            ?.coerceIn(CallingCardStyle.MIN_FONT_SIZE_SP, CallingCardStyle.MAX_FONT_SIZE_SP)
            ?: CallingCardStyle.DEFAULT_FONT_SIZE_SP
        val color = parts.getOrNull(3)
            ?.takeIf { it.length == 8 }
            ?.let { hex -> runCatching { hex.toLong(16).toInt() }.getOrNull() }
        val blur = parts.getOrNull(4)
            ?.toIntOrNull()
            ?.coerceIn(0, CallingCardStyle.MAX_BLUR_DP)
            ?: 0
        return CallingCardStyle(
            font = font,
            fontWeight = weight,
            fontSizeSp = size,
            textColorArgb = color,
            blurRadiusDp = blur
        )
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
