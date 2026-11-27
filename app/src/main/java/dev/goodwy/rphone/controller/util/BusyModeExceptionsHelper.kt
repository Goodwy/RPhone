package dev.goodwy.rphone.controller.util

import android.telephony.PhoneNumberUtils
import com.google.i18n.phonenumbers.PhoneNumberUtil
import dev.goodwy.rphone.modal.data.Contact

enum class IncomingCallDecision {
    BLOCK,
    ALLOW_NORMAL,
    REJECT_BUSY_MODE
}

enum class AddManualNumberStatus {
    ADDED,
    INVALID,
    DUPLICATE
}

data class AddManualNumberResult(
    val numbers: List<String>,
    val normalizedNumber: String,
    val status: AddManualNumberStatus
)

object BusyModeExceptionsHelper {

    private val phoneNumberUtil: PhoneNumberUtil by lazy { PhoneNumberUtil.getInstance() }

    /**
     * Normalizes a manually entered phone number into a canonical dialable string
     * (optional leading '+' followed by digits). Rejects blank or alphabetic inputs.
     */
    fun normalizeManualNumber(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        if (trimmed.any { it.isLetter() }) return ""
        if (!trimmed.any { it.isDigit() }) return ""

        val platformNormalized = try {
            PhoneNumberUtils.normalizeNumber(trimmed)
        } catch (_: Throwable) {
            null
        }

        val candidate = platformNormalized ?: trimmed
        val normalized = buildString {
            var leadingPlusAdded = false
            for (ch in candidate) {
                when {
                    ch == '+' && isEmpty() && !leadingPlusAdded -> {
                        append('+')
                        leadingPlusAdded = true
                    }
                    ch.isDigit() -> append(ch)
                }
            }
        }

        return if (normalized.any { it.isDigit() }) normalized else ""
    }

    /**
     * Compares two phone numbers using Android's [PhoneNumberUtils.compare] on device,
     * falling back to libphonenumber exact/NSN matching on JVM unit tests where
     * Android framework stubs are unavailable. Deliberately avoids loose last-9-digit matching.
     */
    fun comparePhoneNumbers(a: String?, b: String?): Boolean {
        if (a.isNullOrBlank() || b.isNullOrBlank()) return false

        val normalizedA = normalizeManualNumber(a)
        val normalizedB = normalizeManualNumber(b)
        if (normalizedA.isEmpty() || normalizedB.isEmpty()) return false
        if (normalizedA == normalizedB) return true

        val androidMatch = try {
            PhoneNumberUtils.compare(normalizedA, normalizedB)
        } catch (_: Throwable) {
            null
        }
        if (androidMatch != null) return androidMatch

        return try {
            val matchType = phoneNumberUtil.isNumberMatch(normalizedA, normalizedB)
            matchType == PhoneNumberUtil.MatchType.EXACT_MATCH ||
                matchType == PhoneNumberUtil.MatchType.NSN_MATCH
        } catch (_: Throwable) {
            false
        }
    }

    fun addManualNumber(
        existing: List<String>,
        rawNumber: String,
        comparator: (String, String) -> Boolean = ::comparePhoneNumbers
    ): AddManualNumberResult {
        val normalized = normalizeManualNumber(rawNumber)
        if (normalized.isEmpty()) {
            return AddManualNumberResult(existing, "", AddManualNumberStatus.INVALID)
        }
        if (existing.any { comparator(it, normalized) }) {
            return AddManualNumberResult(existing, normalized, AddManualNumberStatus.DUPLICATE)
        }
        return AddManualNumberResult(existing + normalized, normalized, AddManualNumberStatus.ADDED)
    }

    fun removeManualNumber(existing: List<String>, target: String): List<String> {
        val normalizedTarget = normalizeManualNumber(target)
        return existing.filterNot { current ->
            current == target || (normalizedTarget.isNotEmpty() && normalizeManualNumber(current) == normalizedTarget)
        }
    }

    fun matchesManualNumber(
        incomingNumber: String,
        manualNumbers: Iterable<String>,
        comparator: (String, String) -> Boolean = ::comparePhoneNumbers
    ): Boolean {
        if (incomingNumber.isBlank()) return false
        return manualNumbers.any { saved -> saved.isNotBlank() && comparator(incomingNumber, saved) }
    }

    fun pruneStaleContactIds(savedIds: List<String>, existingContactIds: Set<String>): List<String> {
        return savedIds.filter { it.isNotBlank() && it in existingContactIds }.distinct()
    }

    /**
     * Evaluates an incoming call with strict precedence:
     * 1. Blocked numbers always win and return [IncomingCallDecision.BLOCK].
     * 2. If Busy Mode is disabled, return [IncomingCallDecision.ALLOW_NORMAL].
     * 3. If the caller matches a manual exception number or a saved contact exception ID
     *    (safely ignoring stale/deleted IDs), return [IncomingCallDecision.ALLOW_NORMAL].
     * 4. Otherwise, return [IncomingCallDecision.REJECT_BUSY_MODE].
     */
    suspend fun evaluateIncomingCall(
        incomingNumber: String,
        isBusyModeEnabled: Boolean,
        exceptionContactIds: Collection<String>,
        exceptionNumbers: Collection<String>,
        isNumberBlocked: suspend (String) -> Boolean,
        findContactByNumber: suspend (String) -> Contact?,
        findContactById: suspend (String) -> Contact?,
        numberComparator: (String, String) -> Boolean = ::comparePhoneNumbers
    ): IncomingCallDecision {
        if (incomingNumber.isNotBlank() && runCatching { isNumberBlocked(incomingNumber) }.getOrDefault(false)) {
            return IncomingCallDecision.BLOCK
        }

        if (!isBusyModeEnabled) {
            return IncomingCallDecision.ALLOW_NORMAL
        }

        if (incomingNumber.isBlank()) {
            return IncomingCallDecision.REJECT_BUSY_MODE
        }

        if (matchesManualNumber(incomingNumber, exceptionNumbers, numberComparator)) {
            return IncomingCallDecision.ALLOW_NORMAL
        }

        val validExceptionIds = exceptionContactIds.filter { it.isNotBlank() }.toSet()
        if (validExceptionIds.isNotEmpty()) {
            val matchedContact = runCatching { findContactByNumber(incomingNumber) }.getOrNull()
            if (matchedContact != null && matchedContact.id in validExceptionIds) {
                return IncomingCallDecision.ALLOW_NORMAL
            }

            for (contactId in validExceptionIds) {
                if (contactId == matchedContact?.id) continue
                val contact = runCatching { findContactById(contactId) }.getOrNull() ?: continue
                if (contact.phoneNumbers.any { numberComparator(incomingNumber, it) }) {
                    return IncomingCallDecision.ALLOW_NORMAL
                }
            }
        }

        return IncomingCallDecision.REJECT_BUSY_MODE
    }
}
