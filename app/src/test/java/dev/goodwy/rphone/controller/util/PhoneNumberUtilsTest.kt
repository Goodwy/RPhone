package dev.goodwy.rphone.controller.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneNumberUtilsTest {

    @Test
    fun normalizeNumberDigits_stripsNonDigitsExceptLeadingPlus() {
        assertEquals("+15551234567", normalizeNumberDigits("+1 (555) 123-4567"))
        assertEquals("5551234567", normalizeNumberDigits("555-123-4567"))
        assertEquals("", normalizeNumberDigits(""))
    }

    @Test
    fun numbersLikelyMatch_matchesSameNumbersWithDifferentFormatting() {
        assertTrue(numbersLikelyMatch("+1 (555) 123-4567", "5551234567"))
        assertTrue(numbersLikelyMatch("+44 7911 123456", "07911 123456"))
        assertFalse(numbersLikelyMatch("12345", "67890"))
        assertFalse(numbersLikelyMatch("", "5551234567"))
    }

    @Test
    fun isPhoneNumber_identifiesValidPhoneNumberStrings() {
        assertTrue("+1 (555) 123-4567".isPhoneNumber())
        assertTrue("555-123-4567".isPhoneNumber())
        assertTrue("*#06#".isPhoneNumber())
        assertFalse("Hello World".isPhoneNumber())
        assertFalse("john@example.com".isPhoneNumber())
    }
}
