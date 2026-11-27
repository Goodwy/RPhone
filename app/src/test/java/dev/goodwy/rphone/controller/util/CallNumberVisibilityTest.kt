package dev.goodwy.rphone.controller.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallNumberVisibilityTest {

    @Test
    fun toggleOff_showsNumberForSavedContactWithName() {
        assertTrue(
            shouldShowCallPhoneNumber(
                contactName = "Alice Smith",
                phoneNumber = "+15551234567",
                isSavedContact = true,
                hasUsableContactName = true,
                hideSavedContactNumber = false
            )
        )
    }

    @Test
    fun toggleOn_hidesNumberForSavedContactWithUsableName() {
        assertFalse(
            shouldShowCallPhoneNumber(
                contactName = "Alice Smith",
                phoneNumber = "+15551234567",
                isSavedContact = true,
                hasUsableContactName = true,
                hideSavedContactNumber = true
            )
        )
    }

    @Test
    fun toggleOn_stillShowsNumberForUnknownCallerWithCnam() {
        // Must NOT infer saved contact status from contactName != phoneNumber
        assertTrue(
            shouldShowCallPhoneNumber(
                contactName = "Acme Delivery",
                phoneNumber = "+15550002222",
                isSavedContact = false,
                hasUsableContactName = false,
                hideSavedContactNumber = true
            )
        )
    }

    @Test
    fun toggleOn_preservesFallbackForSavedContactWithoutUsableName() {
        assertTrue(
            shouldShowCallPhoneNumber(
                contactName = "Delivery Service",
                phoneNumber = "+15550005555",
                isSavedContact = true,
                hasUsableContactName = false,
                hideSavedContactNumber = true
            )
        )
    }

    @Test
    fun doesNotDuplicateNumberWhenPrimaryDisplayAlreadyShowsNumber() {
        assertFalse(
            shouldShowCallPhoneNumber(
                contactName = "+15550003333",
                phoneNumber = "+15550003333",
                isSavedContact = false,
                hasUsableContactName = false,
                hideSavedContactNumber = false
            )
        )
        assertFalse(
            shouldShowCallPhoneNumber(
                contactName = "\u202A+15550003333\u202C",
                phoneNumber = "+15550003333",
                isSavedContact = true,
                hasUsableContactName = false,
                hideSavedContactNumber = true
            )
        )
    }

    @Test
    fun blankPhoneNumberIsNeverShown() {
        assertFalse(
            shouldShowCallPhoneNumber(
                contactName = "Unknown",
                phoneNumber = "",
                isSavedContact = false,
                hasUsableContactName = false,
                hideSavedContactNumber = false
            )
        )
    }
}
