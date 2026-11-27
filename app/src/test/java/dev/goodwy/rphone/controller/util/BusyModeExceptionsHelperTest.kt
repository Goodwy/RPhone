package dev.goodwy.rphone.controller.util

import dev.goodwy.rphone.modal.data.Contact
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BusyModeExceptionsHelperTest {

    @Test
    fun normalizeManualNumber_stripsFormattingAndPreservesLeadingPlus() {
        assertEquals("+15551234567", BusyModeExceptionsHelper.normalizeManualNumber("+1 (555) 123-4567"))
        assertEquals("5551234567", BusyModeExceptionsHelper.normalizeManualNumber("  555-123-4567 "))
        assertEquals("+442079460958", BusyModeExceptionsHelper.normalizeManualNumber("+44 20 7946 0958"))
    }

    @Test
    fun normalizeManualNumber_rejectsBlankAndNonPhoneInputs() {
        assertEquals("", BusyModeExceptionsHelper.normalizeManualNumber(""))
        assertEquals("", BusyModeExceptionsHelper.normalizeManualNumber("   "))
        assertEquals("", BusyModeExceptionsHelper.normalizeManualNumber("abc"))
        assertEquals("", BusyModeExceptionsHelper.normalizeManualNumber("call 555"))
        assertEquals("", BusyModeExceptionsHelper.normalizeManualNumber("---"))
    }

    @Test
    fun comparePhoneNumbers_matchesFormattedAndNationalNumbersWithoutLooseNineDigitFalsePositives() {
        assertTrue(BusyModeExceptionsHelper.comparePhoneNumbers("+1 (555) 123-4567", "+15551234567"))
        assertTrue(BusyModeExceptionsHelper.comparePhoneNumbers("+15551234567", "555-123-4567"))

        // Same last 9 digits ("125551234") but different area codes: must NOT match
        assertFalse(BusyModeExceptionsHelper.comparePhoneNumbers("2125551234", "3125551234"))
        // Different country codes: must NOT match
        assertFalse(BusyModeExceptionsHelper.comparePhoneNumbers("+15551234567", "+445551234567"))
        assertFalse(BusyModeExceptionsHelper.comparePhoneNumbers("", "+15551234567"))
    }

    @Test
    fun addAndRemoveManualNumber_normalizesDeduplicatesAndRemoves() {
        val added = BusyModeExceptionsHelper.addManualNumber(emptyList(), "+1 (555) 123-4567")
        assertEquals(AddManualNumberStatus.ADDED, added.status)
        assertEquals(listOf("+15551234567"), added.numbers)

        val duplicate = BusyModeExceptionsHelper.addManualNumber(added.numbers, "555-123-4567")
        assertEquals(AddManualNumberStatus.DUPLICATE, duplicate.status)
        assertEquals(listOf("+15551234567"), duplicate.numbers)

        val invalid = BusyModeExceptionsHelper.addManualNumber(added.numbers, "not-a-number")
        assertEquals(AddManualNumberStatus.INVALID, invalid.status)
        assertEquals(listOf("+15551234567"), invalid.numbers)

        val removed = BusyModeExceptionsHelper.removeManualNumber(added.numbers, "+15551234567")
        assertTrue(removed.isEmpty())
    }

    @Test
    fun pruneStaleContactIds_removesDeletedContactsAndDeduplicates() {
        val pruned = BusyModeExceptionsHelper.pruneStaleContactIds(
            savedIds = listOf("1", "deleted_99", "2", "1", ""),
            existingContactIds = setOf("1", "2", "3")
        )
        assertEquals(listOf("1", "2"), pruned)
    }

    @Test
    fun evaluateIncomingCall_blockedNumberAlwaysTakesPriorityOverManualException() = runBlocking {
        val decision = BusyModeExceptionsHelper.evaluateIncomingCall(
            incomingNumber = "+15551234567",
            isBusyModeEnabled = true,
            exceptionContactIds = emptyList(),
            exceptionNumbers = listOf("+15551234567"),
            isNumberBlocked = { true },
            findContactByNumber = { null },
            findContactById = { null }
        )
        assertEquals(IncomingCallDecision.BLOCK, decision)
    }

    @Test
    fun evaluateIncomingCall_blockedContactAlwaysTakesPriorityOverSavedContactException() = runBlocking {
        val alice = Contact(id = "42", givenName = "Alice", phoneNumbers = listOf("+15551234567"))
        val decision = BusyModeExceptionsHelper.evaluateIncomingCall(
            incomingNumber = "+15551234567",
            isBusyModeEnabled = true,
            exceptionContactIds = listOf("42"),
            exceptionNumbers = emptyList(),
            isNumberBlocked = { true },
            findContactByNumber = { alice },
            findContactById = { alice }
        )
        assertEquals(IncomingCallDecision.BLOCK, decision)
    }

    @Test
    fun evaluateIncomingCall_allowsSelectedSavedContactWhenBusyModeEnabled() = runBlocking {
        val alice = Contact(id = "42", givenName = "Alice", phoneNumbers = listOf("+15551234567"))
        val decision = BusyModeExceptionsHelper.evaluateIncomingCall(
            incomingNumber = "+15551234567",
            isBusyModeEnabled = true,
            exceptionContactIds = listOf("42"),
            exceptionNumbers = emptyList(),
            isNumberBlocked = { false },
            findContactByNumber = { alice },
            findContactById = { id -> if (id == "42") alice else null }
        )
        assertEquals(IncomingCallDecision.ALLOW_NORMAL, decision)
    }

    @Test
    fun evaluateIncomingCall_allowsDuplicateSavedContactSharingNumber() = runBlocking {
        val primaryLookup = Contact(id = "10", givenName = "Alice Work", phoneNumbers = listOf("+15551234567"))
        val exceptedContact = Contact(id = "42", givenName = "Alice Personal", phoneNumbers = listOf("555-123-4567"))
        val decision = BusyModeExceptionsHelper.evaluateIncomingCall(
            incomingNumber = "+15551234567",
            isBusyModeEnabled = true,
            exceptionContactIds = listOf("42"),
            exceptionNumbers = emptyList(),
            isNumberBlocked = { false },
            findContactByNumber = { primaryLookup },
            findContactById = { id -> if (id == "42") exceptedContact else null }
        )
        assertEquals(IncomingCallDecision.ALLOW_NORMAL, decision)
    }

    @Test
    fun evaluateIncomingCall_allowsManuallyAddedNumberWhenBusyModeEnabled() = runBlocking {
        val decision = BusyModeExceptionsHelper.evaluateIncomingCall(
            incomingNumber = "+15551234567",
            isBusyModeEnabled = true,
            exceptionContactIds = emptyList(),
            exceptionNumbers = listOf("5551234567"),
            isNumberBlocked = { false },
            findContactByNumber = { null },
            findContactById = { null }
        )
        assertEquals(IncomingCallDecision.ALLOW_NORMAL, decision)
    }

    @Test
    fun evaluateIncomingCall_rejectsNonExceptedCallerAndHandlesStaleContactIdsSafely() = runBlocking {
        val decision = BusyModeExceptionsHelper.evaluateIncomingCall(
            incomingNumber = "+15559998888",
            isBusyModeEnabled = true,
            exceptionContactIds = listOf("deleted_contact_id"),
            exceptionNumbers = listOf("+15551234567"),
            isNumberBlocked = { false },
            findContactByNumber = { null },
            findContactById = { null }
        )
        assertEquals(IncomingCallDecision.REJECT_BUSY_MODE, decision)
    }

    @Test
    fun evaluateIncomingCall_allowsNormalCallWhenBusyModeDisabled() = runBlocking {
        val decision = BusyModeExceptionsHelper.evaluateIncomingCall(
            incomingNumber = "+15559998888",
            isBusyModeEnabled = false,
            exceptionContactIds = emptyList(),
            exceptionNumbers = emptyList(),
            isNumberBlocked = { false },
            findContactByNumber = { null },
            findContactById = { null }
        )
        assertEquals(IncomingCallDecision.ALLOW_NORMAL, decision)
    }

    @Test
    fun evaluateIncomingCall_rejectsBlankNumberWhenBusyModeEnabled() = runBlocking {
        val decision = BusyModeExceptionsHelper.evaluateIncomingCall(
            incomingNumber = "",
            isBusyModeEnabled = true,
            exceptionContactIds = listOf("42"),
            exceptionNumbers = listOf("+15551234567"),
            isNumberBlocked = { false },
            findContactByNumber = { null },
            findContactById = { null }
        )
        assertEquals(IncomingCallDecision.REJECT_BUSY_MODE, decision)
    }

    @Test
    fun evaluateIncomingCall_manualExceptionStillWorksWhenContactLookupThrows() = runBlocking {
        val decision = BusyModeExceptionsHelper.evaluateIncomingCall(
            incomingNumber = "+15551234567",
            isBusyModeEnabled = true,
            exceptionContactIds = listOf("42"),
            exceptionNumbers = listOf("+15551234567"),
            isNumberBlocked = { false },
            findContactByNumber = { throw SecurityException("Permission revoked") },
            findContactById = { throw SecurityException("Permission revoked") }
        )
        assertEquals(IncomingCallDecision.ALLOW_NORMAL, decision)
    }
}
