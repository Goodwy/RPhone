package dev.goodwy.rphone.controller.util

import android.telephony.PhoneNumberUtils
import java.util.Locale

fun formatPhoneNumber(number: String): String {
    return PhoneNumberUtils.formatNumber(number, Locale.getDefault().country) ?: number
}

fun normalizePhoneNumber(number: String): String {
    return PhoneNumberUtils.normalizeNumber(number)
}

fun areNumbersEqual(num1: String?, num2: String?): Boolean {
    if (num1 == null || num2 == null) return false
    return PhoneNumberUtils.compare(num1, num2)
}

fun deduplicateNumbers(numbers: List<String>): List<String> {
    val unique = mutableListOf<String>()
    numbers.forEach { number ->
        val existingIndex = unique.indexOfFirst { areNumbersEqual(it, number) }
        if (existingIndex == -1) {
            unique.add(number)
        } else {
            // Prefer the number with a '+' or the longer one (usually more complete)
            val existing = unique[existingIndex]
            if (number.contains("+") && !existing.contains("+")) {
                unique[existingIndex] = number
            } else if (number.length > existing.length && (number.contains("+") == existing.contains("+"))) {
                unique[existingIndex] = number
            }
        }
    }
    return unique
}

/**
 * Strips everything except digits and a leading '+' so two differently-formatted
 * representations of the same number ("+1 (555) 123-4567" vs "5551234567") can be compared.
 */
fun normalizeNumberDigits(number: String): String =
    number.filter { it.isDigit() || it == '+' }

/**
 * Loose equality check for two phone numbers: compares the last 9 digits (enough to avoid
 * false positives while still matching across differing country-code / leading-zero / spacing
 * conventions). Used to decide whether a call-log number belongs to a saved contact.
 */
fun numbersLikelyMatch(a: String, b: String): Boolean {
    val da = normalizeNumberDigits(a).filter { it.isDigit() }
    val db = normalizeNumberDigits(b).filter { it.isDigit() }
    if (da.isEmpty() || db.isEmpty()) return false
    val tailLen = minOf(9, da.length, db.length)
    if (tailLen <= 0) return false
    return da.takeLast(tailLen) == db.takeLast(tailLen)
}

// checks if string is a phone number
fun String.isPhoneNumber(): Boolean {
    return this.matches("^[0-9+\\-\\)\\( *#]+\$".toRegex())
}

// if we are comparing phone numbers, compare just the last 9 digits
fun String.trimToComparableNumber(): String {
    // don't trim if it's not a phone number
    if (!this.isPhoneNumber()) {
        return this
    }
    val normalizedNumber = normalizePhoneNumber(this)
    val startIndex = 0.coerceAtLeast(normalizedNumber.length - 9)
    return normalizedNumber.substring(startIndex)
}