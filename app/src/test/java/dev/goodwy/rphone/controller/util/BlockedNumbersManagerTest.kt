package dev.goodwy.rphone.controller.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockedNumbersManagerTest {

    @Test
    fun testIsBlockedNumberPattern() {
        assertTrue(BlockedNumbersManager.isBlockedNumberPattern("+1800*"))
        assertTrue(BlockedNumbersManager.isBlockedNumberPattern("*1919"))
        assertTrue(BlockedNumbersManager.isBlockedNumberPattern("*800*"))
        assertTrue(BlockedNumbersManager.isBlockedNumberPattern("*"))
        assertFalse(BlockedNumbersManager.isBlockedNumberPattern("+15551234567"))
        assertFalse(BlockedNumbersManager.isBlockedNumberPattern("12345"))
        assertFalse(BlockedNumbersManager.isBlockedNumberPattern(""))
    }

    @Test
    fun testMatchesPatternPrefix() {
        assertTrue(BlockedNumbersManager.matchesPattern("+18005551234", "+1800*"))
        assertTrue(BlockedNumbersManager.matchesPattern("+1 (800) 555-1234", "+1800*"))
        assertFalse(BlockedNumbersManager.matchesPattern("+18885551234", "+1800*"))
        assertFalse(BlockedNumbersManager.matchesPattern("18005551234", "+1800*"))
    }

    @Test
    fun testMatchesPatternSuffix() {
        assertTrue(BlockedNumbersManager.matchesPattern("+15551231919", "*1919"))
        assertTrue(BlockedNumbersManager.matchesPattern("1919", "*1919"))
        assertFalse(BlockedNumbersManager.matchesPattern("+15551919000", "*1919"))
    }

    @Test
    fun testMatchesPatternContainsAndMiddle() {
        assertTrue(BlockedNumbersManager.matchesPattern("+18005551234", "*800*"))
        assertTrue(BlockedNumbersManager.matchesPattern("800", "*800*"))
        assertFalse(BlockedNumbersManager.matchesPattern("+18885551234", "*800*"))

        assertTrue(BlockedNumbersManager.matchesPattern("1239", "1*9"))
        assertFalse(BlockedNumbersManager.matchesPattern("1238", "1*9"))
    }

    @Test
    fun testMatchesPatternEscapesRegexMetacharacters() {
        // Parentheses and dots should be treated literally (or stripped as separators), not as regex syntax
        assertTrue(BlockedNumbersManager.matchesPattern("(800) 555-1234", "(800)*"))
        assertTrue(BlockedNumbersManager.matchesPattern("8005551234", "(800)*"))
        assertTrue(BlockedNumbersManager.matchesPattern("+1 (800) 555-1234", "*(800)*"))
        assertTrue(BlockedNumbersManager.matchesPattern("+1.800.555.1234", "+1.800*"))
        assertFalse(BlockedNumbersManager.matchesPattern("+19995551234", "(800)*"))
    }

    @Test
    fun testMatchesAnyPattern() {
        val patterns = listOf("+44*", "*1919", "*999*")
        assertTrue(BlockedNumbersManager.matchesAnyPattern("+447911123456", patterns))
        assertTrue(BlockedNumbersManager.matchesAnyPattern("5551919", patterns))
        assertTrue(BlockedNumbersManager.matchesAnyPattern("1299934", patterns))
        assertFalse(BlockedNumbersManager.matchesAnyPattern("+15551234567", patterns))
        assertFalse(BlockedNumbersManager.matchesAnyPattern("", patterns))
    }
}
