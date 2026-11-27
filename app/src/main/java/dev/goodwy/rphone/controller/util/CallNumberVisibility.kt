package dev.goodwy.rphone.controller.util

private val BIDI_CONTROL_CHARS = setOf(
    '\u202A', '\u202B', '\u202C', '\u202D', '\u202E',
    '\u2066', '\u2067', '\u2068', '\u2069',
    '\u200E', '\u200F'
)

internal fun stripBidiControls(value: String): String =
    value.filterNot { it in BIDI_CONTROL_CHARS }.trim()

/**
 * Determines whether the secondary phone number should be displayed on call surfaces
 * (incoming/outgoing call screen, conference participants, floating call sheet).
 *
 * Hides the visual phone number only when [hideSavedContactNumber] is enabled AND the caller
 * is explicitly identified as a saved contact with a usable contact name. Unknown callers,
 * CNAM-only non-saved callers, and saved contacts without a usable name retain their existing display.
 */
fun shouldShowCallPhoneNumber(
    contactName: String,
    phoneNumber: String,
    isSavedContact: Boolean,
    hasUsableContactName: Boolean,
    hideSavedContactNumber: Boolean
): Boolean {
    if (phoneNumber.isBlank()) return false
    if (stripBidiControls(contactName) == stripBidiControls(phoneNumber)) return false
    if (hideSavedContactNumber && isSavedContact && hasUsableContactName) return false
    return true
}
