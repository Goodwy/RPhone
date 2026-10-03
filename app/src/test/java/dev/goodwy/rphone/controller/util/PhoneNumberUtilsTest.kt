package dev.goodwy.rphone.controller.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneNumberUtilsTest {

    @Test
    fun testNormalizeNumberDigits() {
        assertEquals("+15551234567", normalizeNumberDigits("+1 (555) 123-4567"))
        assertEquals("5551234567", normalizeNumberDigits("555-123-4567"))
        assertEquals("", normalizeNumberDigits("abc-xyz"))
    }

    @Test
    fun testNumbersLikelyMatch() {
        assertTrue(numbersLikelyMatch("+1 (555) 123-4567", "5551234567"))
        assertTrue(numbersLikelyMatch("+447911123456", "07911123456"))
        assertFalse(numbersLikelyMatch("123456", "654321"))
        assertFalse(numbersLikelyMatch("", ""))
    }

    @Test
    fun testIsPhoneNumber() {
        assertTrue("+15551234567".isPhoneNumber())
        assertTrue("555-123-4567".isPhoneNumber())
        assertTrue("*#06#".isPhoneNumber())
        assertFalse("hello@world.com".isPhoneNumber())
        assertFalse("invalid_number_123!".isPhoneNumber())
    }
}
