package dev.goodwy.rphone.controller.spam

import dev.goodwy.rphone.modal.data.CallerPresentation
import dev.goodwy.rphone.modal.data.SpamActionMode
import dev.goodwy.rphone.modal.data.SpamCategory
import dev.goodwy.rphone.modal.data.SpamDbMatch
import dev.goodwy.rphone.modal.data.SpamProtectionConfig
import dev.goodwy.rphone.modal.data.SpamReason
import dev.goodwy.rphone.modal.data.SpamSignals
import dev.goodwy.rphone.modal.data.SpamVerdict
import dev.goodwy.rphone.modal.data.StirShakenStatus
import dev.goodwy.rphone.modal.db.SpamNumberEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpamDetectionEngineTest {

    @Test
    fun emergencyNumbers_areNeverBlockedOrSilenced() {
        val config = SpamProtectionConfig(
            enabled = true,
            actionMode = SpamActionMode.DECLINE,
            silenceUnknownCallers = true,
            blockPrivateNumbers = true
        )
        val result = SpamDetectionEngine.evaluate(
            signals = SpamSignals(
                rawNumber = "911",
                normalizedNumber = "911",
                isExplicitlyBlocked = true,
                isEmergencyNumber = true
            ),
            config = config
        )
        assertEquals(SpamVerdict.ALLOW, result.verdict)
        assertEquals(listOf(SpamReason.EMERGENCY_NUMBER), result.reasons)
    }

    @Test
    fun explicitlyBlockedNumber_alwaysDeclinesOverSavedContact() {
        val result = SpamDetectionEngine.evaluate(
            signals = SpamSignals(
                rawNumber = "+15551234567",
                normalizedNumber = "+15551234567",
                isSavedContact = true,
                isExplicitlyBlocked = true
            ),
            config = SpamProtectionConfig(enabled = true)
        )
        assertEquals(SpamVerdict.DECLINE, result.verdict)
        assertTrue(result.isExplicitlyBlocked)
        assertEquals(listOf(SpamReason.EXPLICITLY_BLOCKED), result.reasons)
    }

    @Test
    fun savedContact_alwaysAllowsEvenWithFailedStirShakenOrDbMatch() {
        val result = SpamDetectionEngine.evaluate(
            signals = SpamSignals(
                rawNumber = "+15551234567",
                normalizedNumber = "+15551234567",
                isSavedContact = true,
                stirShakenStatus = StirShakenStatus.FAILED,
                dbMatch = SpamDbMatch("+15551234567", SpamCategory.SCAM, SpamNumberEntity.SOURCE_USER_REPORT),
                recentUnknownCallCountInWindow = 5
            ),
            config = SpamProtectionConfig(
                enabled = true,
                actionMode = SpamActionMode.DECLINE,
                silenceUnknownCallers = true,
                stirShakenCheckEnabled = true
            )
        )
        assertEquals(SpamVerdict.ALLOW, result.verdict)
        assertFalse(result.isSuspectedSpam)
        assertEquals(listOf(SpamReason.SAVED_CONTACT), result.reasons)
    }

    @Test
    fun localSpamDbMatch_respectsConfiguredActionMode() {
        val dbMatch = SpamDbMatch(
            normalizedNumber = "+18005550199",
            category = SpamCategory.TELEMARKETER,
            source = SpamNumberEntity.SOURCE_IMPORTED_LIST,
            label = "Promo Bot",
            confidence = 95
        )
        val signals = SpamSignals(
            rawNumber = "+18005550199",
            normalizedNumber = "+18005550199",
            dbMatch = dbMatch
        )

        val warnResult = SpamDetectionEngine.evaluate(
            signals,
            SpamProtectionConfig(enabled = true, actionMode = SpamActionMode.WARN)
        )
        assertEquals(SpamVerdict.WARN, warnResult.verdict)
        assertEquals(SpamCategory.TELEMARKETER, warnResult.category)
        assertEquals("Promo Bot", warnResult.matchedLabel)
        assertEquals(listOf(SpamReason.BLOCKLIST_IMPORTED), warnResult.reasons)

        val silenceResult = SpamDetectionEngine.evaluate(
            signals,
            SpamProtectionConfig(enabled = true, actionMode = SpamActionMode.SILENCE)
        )
        assertEquals(SpamVerdict.SILENCE, silenceResult.verdict)

        val declineResult = SpamDetectionEngine.evaluate(
            signals,
            SpamProtectionConfig(enabled = true, actionMode = SpamActionMode.DECLINE)
        )
        assertEquals(SpamVerdict.DECLINE, declineResult.verdict)
    }

    @Test
    fun stirShakenFailed_flagsSpoofedCallerWhenEnabled() {
        val result = SpamDetectionEngine.evaluate(
            signals = SpamSignals(
                rawNumber = "+15559876543",
                normalizedNumber = "+15559876543",
                stirShakenStatus = StirShakenStatus.FAILED
            ),
            config = SpamProtectionConfig(
                enabled = true,
                actionMode = SpamActionMode.WARN,
                stirShakenCheckEnabled = true
            )
        )
        assertEquals(SpamVerdict.WARN, result.verdict)
        assertEquals(SpamCategory.SPOOFED, result.category)
        assertTrue(result.riskScore >= 60)
        assertTrue(SpamReason.STIR_SHAKEN_FAILED in result.reasons)
    }

    @Test
    fun stirShakenPassed_allowsUnknownCallerWhenNoOtherRulesMatch() {
        val result = SpamDetectionEngine.evaluate(
            signals = SpamSignals(
                rawNumber = "+15559876543",
                normalizedNumber = "+15559876543",
                stirShakenStatus = StirShakenStatus.PASSED
            ),
            config = SpamProtectionConfig(
                enabled = true,
                actionMode = SpamActionMode.DECLINE,
                stirShakenCheckEnabled = true
            )
        )
        assertEquals(SpamVerdict.ALLOW, result.verdict)
        assertEquals(StirShakenStatus.PASSED, result.stirShakenStatus)
        assertFalse(result.isSuspectedSpam)
    }

    @Test
    fun privateHiddenNumber_filteredWhenBlockPrivateEnabled() {
        val result = SpamDetectionEngine.evaluate(
            signals = SpamSignals(
                rawNumber = "",
                normalizedNumber = "",
                presentation = CallerPresentation.RESTRICTED
            ),
            config = SpamProtectionConfig(
                enabled = true,
                actionMode = SpamActionMode.SILENCE,
                blockPrivateNumbers = true
            )
        )
        assertEquals(SpamVerdict.SILENCE, result.verdict)
        assertEquals(SpamCategory.PRIVATE_NUMBER, result.category)
        assertEquals(listOf(SpamReason.PRIVATE_OR_HIDDEN_NUMBER), result.reasons)
    }

    @Test
    fun rapidRepeatBurst_warnsAtThreshold() {
        val result = SpamDetectionEngine.evaluate(
            signals = SpamSignals(
                rawNumber = "+15554443333",
                normalizedNumber = "+15554443333",
                recentUnknownCallCountInWindow = SpamDetectionEngine.BURST_CALL_THRESHOLD
            ),
            config = SpamProtectionConfig(
                enabled = true,
                actionMode = SpamActionMode.DECLINE,
                burstProtectionEnabled = true
            )
        )
        assertEquals(SpamVerdict.WARN, result.verdict)
        assertEquals(SpamCategory.ROBOCALL, result.category)
        assertTrue(SpamReason.RAPID_REPEAT_CALLS in result.reasons)
    }

    @Test
    fun neighborSpoofing_detectsMatchingPrefixButNotOwnNumber() {
        assertTrue(SpamDetectionEngine.isNeighborSpoof("+14155550199", "+14155550100"))
        assertFalse(SpamDetectionEngine.isNeighborSpoof("+14155550100", "+14155550100"))
        assertFalse(SpamDetectionEngine.isNeighborSpoof("+12125550199", "+14155550100"))

        val result = SpamDetectionEngine.evaluate(
            signals = SpamSignals(
                rawNumber = "+14155550199",
                normalizedNumber = "+14155550199"
            ),
            config = SpamProtectionConfig(
                enabled = true,
                neighborSpoofingEnabled = true,
                ownPhoneNumber = "+14155550100"
            )
        )
        assertEquals(SpamVerdict.WARN, result.verdict)
        assertTrue(SpamReason.NEIGHBOR_SPOOFING in result.reasons)
    }

    @Test
    fun silenceUnknownCallers_stillWorksWhenSpamEngineDisabled() {
        val result = SpamDetectionEngine.evaluate(
            signals = SpamSignals(
                rawNumber = "+15557778888",
                normalizedNumber = "+15557778888",
                isSavedContact = false
            ),
            config = SpamProtectionConfig(
                enabled = false,
                silenceUnknownCallers = true
            )
        )
        assertEquals(SpamVerdict.SILENCE, result.verdict)
        assertEquals(SpamCategory.UNKNOWN_CALLER, result.category)
    }

    @Test
    fun blocklistParser_parsesCsvAndPlainTextAndExportsRoundtrip() {
        val raw = """
            # Sample community blocklist
            number,category,label
            +1 (800) 555-0101,TELEMARKETER,Extended Warranty
            +15559990000,SCAM,"Fake Bank, Inc."
            555-222-3333
            not_a_number
            911
        """.trimIndent()

        val (entries, skipped) = SpamBlocklistParser.parse(raw)
        assertEquals(3, entries.size)
        assertEquals(2, skipped)

        assertEquals("+18005550101", entries[0].normalizedNumber)
        assertEquals(SpamCategory.TELEMARKETER, entries[0].category)
        assertEquals("Extended Warranty", entries[0].label)

        assertEquals("+15559990000", entries[1].normalizedNumber)
        assertEquals(SpamCategory.SCAM, entries[1].category)
        assertEquals("Fake Bank, Inc.", entries[1].label)

        assertEquals("5552223333", entries[2].normalizedNumber)
        assertEquals(SpamCategory.SPAM, entries[2].category)

        val csv = SpamBlocklistParser.toCsv(entries)
        val (roundtrip, roundtripSkipped) = SpamBlocklistParser.parse(csv)
        assertEquals(0, roundtripSkipped)
        assertEquals(entries.map { it.normalizedNumber to it.category }, roundtrip.map { it.normalizedNumber to it.category })
    }
}
