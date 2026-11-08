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
}
