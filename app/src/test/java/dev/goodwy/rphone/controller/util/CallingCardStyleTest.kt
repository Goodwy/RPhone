package dev.goodwy.rphone.controller.util

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CallingCardStyleTest {

    @Test
    fun everyFontRoundTripsWithoutColor() {
        CallingCardFont.entries.forEach { font ->
            val style = CallingCardStyle(font = font)
            val decoded = CallingCardStyleCodec.decode(CallingCardStyleCodec.encode(style))
            assertEquals(style, decoded)
        }
    }

    @Test
    fun everyFontRoundTripsWithColor() {
        val colors = listOf(
            0xFFFFFFFF.toInt(),
            0xFFFFC857.toInt(),
            0xFF101418.toInt(),
            0x00000000
        )
        CallingCardFont.entries.forEach { font ->
            colors.forEach { color ->
                val style = CallingCardStyle(font = font, textColorArgb = color)
                val decoded = CallingCardStyleCodec.decode(CallingCardStyleCodec.encode(style))
                assertEquals(style, decoded)
            }
        }
    }

    @Test
    fun themeDefaultIsDetected() {
        assertEquals(true, CallingCardStyle.ThemeDefault.isThemeDefault)
        assertEquals(false, CallingCardStyle(font = CallingCardFont.Lora).isThemeDefault)
        assertEquals(false, CallingCardStyle(textColorArgb = 0xFFFFFFFF.toInt()).isThemeDefault)
    }

    @Test
    fun decodingBlankOrGarbageReturnsNull() {
        assertNull(CallingCardStyleCodec.decode(null))
        assertNull(CallingCardStyleCodec.decode(""))
        assertNull(CallingCardStyleCodec.decode("   "))
    }

    @Test
    fun unknownFontFallsBackToSystem() {
        val decoded = CallingCardStyleCodec.decode("not-a-font|")
        assertNotNull(decoded)
        assertEquals(CallingCardFont.System, decoded?.font)
        assertNull(decoded?.textColorArgb)
    }

    @Test
    fun invalidColorIsIgnored() {
        val decoded = CallingCardStyleCodec.decode("lora|zzzz")
        assertEquals(CallingCardFont.Lora, decoded?.font)
        assertNull(decoded?.textColorArgb)
    }

    @Test
    fun colorConvertsToComposeColor() {
        val style = CallingCardStyle(textColorArgb = 0xFF8ECAE6.toInt())
        assertEquals(Color(0xFF8ECAE6.toInt()), style.textColor)
        assertNull(CallingCardStyle.ThemeDefault.textColor)
    }

    @Test
    fun shadowOnlyAppearsOnBackgrounds() {
        assertNull(callingCardNameShadow(Color.White, hasBackground = false))
        assertNull(callingCardNameShadow(Color.Black, hasBackground = false))
        assertNotNull(callingCardNameShadow(Color.White, hasBackground = true))
        assertNotNull(callingCardNameShadow(Color.Black, hasBackground = true))
    }

    @Test
    fun wallpaperTransformRoundTrips() {
        val style = CallingCardStyle(
            font = CallingCardFont.Playfair,
            fontWeight = 600,
            fontSizeSp = 32,
            textColorArgb = 0xFFFFC857.toInt(),
            blurRadiusDp = 6,
            bgScale = 2.35f,
            bgOffsetX = -0.425f,
            bgOffsetY = 0.3125f
        )
        val encoded = CallingCardStyleCodec.encode(style)
        val decoded = CallingCardStyleCodec.decode(encoded)
        assertNotNull(decoded)
        assertEquals(style.font, decoded!!.font)
        assertEquals(style.fontWeight, decoded.fontWeight)
        assertEquals(style.fontSizeSp, decoded.fontSizeSp)
        assertEquals(style.textColorArgb, decoded.textColorArgb)
        assertEquals(style.blurRadiusDp, decoded.blurRadiusDp)
        assertEquals(2.35f, decoded.bgScale, 0.0001f)
        assertEquals(-0.425f, decoded.bgOffsetX, 0.0001f)
        assertEquals(0.3125f, decoded.bgOffsetY, 0.0001f)
    }

    @Test
    fun legacyFiveFieldFormatDecodesToDefaultTransform() {
        val decoded = CallingCardStyleCodec.decode("inter|600|28|FFFFFFFF|8")
        assertNotNull(decoded)
        assertEquals(CallingCardFont.Inter, decoded!!.font)
        assertEquals(600, decoded.fontWeight)
        assertEquals(28, decoded.fontSizeSp)
        assertEquals(0xFFFFFFFF.toInt(), decoded.textColorArgb)
        assertEquals(8, decoded.blurRadiusDp)
        assertEquals(1f, decoded.bgScale, 0.0001f)
        assertEquals(0f, decoded.bgOffsetX, 0.0001f)
        assertEquals(0f, decoded.bgOffsetY, 0.0001f)
        assertEquals(false, decoded.hasCustomBackgroundTransform)
    }

    @Test
    fun outOfRangeAndMalformedTransformsAreClampedSafely() {
        val high = CallingCardStyleCodec.decode("system|500|24||0|9.5|NaN|Infinity")
        assertNotNull(high)
        assertEquals(CallingCardStyle.MAX_BG_SCALE, high!!.bgScale, 0.0001f)
        assertEquals(0f, high.bgOffsetX, 0.0001f)
        assertEquals(0f, high.bgOffsetY, 0.0001f)

        val low = CallingCardStyleCodec.decode("system|500|24||0|0.2|-999|999")
        assertNotNull(low)
        assertEquals(CallingCardStyle.MIN_BG_SCALE, low!!.bgScale, 0.0001f)
        assertEquals(-CallingCardStyle.MAX_BG_OFFSET, low.bgOffsetX, 0.0001f)
        assertEquals(CallingCardStyle.MAX_BG_OFFSET, low.bgOffsetY, 0.0001f)
    }

    @Test
    fun transformOnlyCustomizationIsNotThemeDefault() {
        val zoomed = CallingCardStyle(bgScale = 1.5f)
        assertEquals(true, zoomed.hasCustomBackgroundTransform)
        assertEquals(false, zoomed.isThemeDefault)

        val reset = zoomed.resetBackgroundTransform()
        assertEquals(false, reset.hasCustomBackgroundTransform)
        assertEquals(true, reset.isThemeDefault)
    }

    @Test
    fun encodingUsesDotDecimalsRegardlessOfDefaultLocale() {
        val previous = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.FRANCE)
            val style = CallingCardStyle(bgScale = 1.75f, bgOffsetX = 0.25f, bgOffsetY = -0.5f)
            val encoded = CallingCardStyleCodec.encode(style)
            val decoded = CallingCardStyleCodec.decode(encoded)
            assertNotNull(decoded)
            assertEquals(1.75f, decoded!!.bgScale, 0.0001f)
            assertEquals(0.25f, decoded.bgOffsetX, 0.0001f)
            assertEquals(-0.5f, decoded.bgOffsetY, 0.0001f)
        } finally {
            java.util.Locale.setDefault(previous)
        }
    }
}
