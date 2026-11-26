package dev.goodwy.rphone.controller.util

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.BlockedNumberContract
import android.telephony.PhoneNumberUtils

data class BlockedNumber(
    val id: Long,
    val originalNumber: String,
    val strippedNumber: String?
)

object BlockedNumbersManager {

    @Volatile
    private var cachedPrefs: PreferenceManager? = null

    private fun getPrefs(context: Context): PreferenceManager {
        return cachedPrefs ?: synchronized(this) {
            cachedPrefs ?: PreferenceManager(context.applicationContext).also { cachedPrefs = it }
        }
    }

    fun canBlockNumbers(context: Context): Boolean {
        return try {
            BlockedNumberContract.canCurrentUserBlockNumbers(context)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Checks if a number is blocked, either by exact match via the system
     * [BlockedNumberContract] or by a wildcard pattern stored in the block list.
     *
     * Pattern syntax (uses `*` as wildcard):
     * - Prefix / starts-with: `+1800*`
     * - Suffix / ends-with:   `*1919`
     * - Contains:             `*800*`
     */
    fun isBlocked(context: Context, number: String?): Boolean {
        if (number.isNullOrBlank()) return false
        val systemBlocked = try {
            BlockedNumberContract.isBlocked(context, number)
        } catch (_: Exception) {
            false
        }
        if (systemBlocked) return true
        return isNumberBlockedByPattern(context, number)
    }

    /**
     * Returns `true` if the string contains a `*` wildcard, making it
     * a pattern rather than an exact number.
     */
    fun isBlockedNumberPattern(number: String): Boolean {
        return number.contains('*')
    }

    private fun stripSeparatorsSafe(value: String): String {
        val stripped = try {
            PhoneNumberUtils.stripSeparators(value)
        } catch (_: Throwable) {
            null
        }
        return if (!stripped.isNullOrEmpty()) {
            stripped
        } else {
            value.filterNot { it == ' ' || it == '-' || it == '(' || it == ')' || it == '.' }
        }
    }

    private fun patternToRegex(pattern: String): Regex {
        val regexBody = pattern
            .split('*')
            .joinToString(".*") { Regex.escape(it) }
        return regexBody.toRegex()
    }

    /**
     * Pure helper that checks whether [number] matches a single wildcard [pattern].
     */
    fun matchesPattern(
        number: String,
        pattern: String,
        strippedNumber: String? = null
    ): Boolean {
        val trimmedPattern = pattern.trim()
        if (number.isBlank() || trimmedPattern.isEmpty() || !isBlockedNumberPattern(trimmedPattern)) {
            return false
        }
        return try {
            val regex = patternToRegex(trimmedPattern)
            val cleanNumber = strippedNumber ?: stripSeparatorsSafe(number)
            if (number.matches(regex) || cleanNumber.matches(regex)) {
                return true
            }
            val cleanPattern = trimmedPattern.filterNot {
                it == ' ' || it == '-' || it == '(' || it == ')' || it == '.'
            }
            if (cleanPattern != trimmedPattern && cleanPattern.any { it != '*' }) {
                val cleanRegex = patternToRegex(cleanPattern)
                if (cleanNumber.matches(cleanRegex)) {
                    return true
                }
            }
            false
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Pure helper that checks whether [number] matches any pattern in [patterns].
     */
    fun matchesAnyPattern(
        number: String,
        patterns: Iterable<String>,
        strippedNumber: String? = null
    ): Boolean {
        if (number.isBlank()) return false
        val cleanNumber = strippedNumber ?: stripSeparatorsSafe(number)
        return patterns.any { pattern -> matchesPattern(number, pattern, cleanNumber) }
    }

    /**
     * Checks whether [number] matches any wildcard pattern entry
     * stored in the system block list or app fallback preferences.
     */
    fun isNumberBlockedByPattern(context: Context, number: String): Boolean {
        if (number.isBlank()) return false
        val patterns = getBlockedPatterns(context)
        if (patterns.isEmpty()) return false
        return matchesAnyPattern(number, patterns)
    }

    fun getBlockedPatterns(context: Context): List<String> {
        return getAll(context)
            .map { it.originalNumber }
            .filter { isBlockedNumberPattern(it) }
            .distinct()
    }

    fun block(context: Context, number: String?): Boolean {
        val trimmed = number?.trim() ?: return false
        if (trimmed.isEmpty()) return false

        val isPattern = isBlockedNumberPattern(trimmed)
        if (isPattern) {
            if (getAll(context).any { it.originalNumber == trimmed }) return true
        } else {
            val alreadySystemBlocked = try {
                BlockedNumberContract.isBlocked(context, trimmed)
            } catch (_: Exception) {
                false
            }
            if (alreadySystemBlocked) return true
        }

        val insertedInSystem = try {
            val values = ContentValues().apply {
                put(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER, trimmed)
                // Only set E164 for real phone numbers, not patterns
                if (!isPattern && trimmed.isPhoneNumber()) {
                    val normalized = PhoneNumberUtils.normalizeNumber(trimmed)
                    if (!normalized.isNullOrBlank()) {
                        put(BlockedNumberContract.BlockedNumbers.COLUMN_E164_NUMBER, normalized)
                    }
                }
            }
            context.contentResolver.insert(
                BlockedNumberContract.BlockedNumbers.CONTENT_URI,
                values
            ) != null
        } catch (_: Exception) {
            false
        }

        if (insertedInSystem) return true

        // Fallback for wildcard patterns when system provider rejects '*' or app is not default dialer
        if (isPattern) {
            val prefs = getPrefs(context)
            val current = prefs.getBlockedPatterns()
            if (!current.contains(trimmed)) {
                prefs.setBlockedPatterns(current + trimmed)
            }
            return true
        }

        return false
    }

    fun unblock(context: Context, number: String?): Boolean {
        val trimmed = number?.trim() ?: return false
        if (trimmed.isEmpty()) return false

        var removedFromPrefs = false
        if (isBlockedNumberPattern(trimmed)) {
            val prefs = getPrefs(context)
            val current = prefs.getBlockedPatterns()
            if (current.contains(trimmed)) {
                prefs.setBlockedPatterns(current.filterNot { it == trimmed })
                removedFromPrefs = true
            }
        }

        val removedFromSystem = try {
            if (isBlockedNumberPattern(trimmed)) {
                val selection = "${BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER} = ?"
                val selectionArgs = arrayOf(trimmed)
                context.contentResolver.delete(
                    BlockedNumberContract.BlockedNumbers.CONTENT_URI,
                    selection,
                    selectionArgs
                ) > 0
            } else {
                BlockedNumberContract.unblock(context, trimmed)
                true
            }
        } catch (_: Exception) {
            false
        }

        return removedFromSystem || removedFromPrefs
    }

    fun unblockById(context: Context, id: Long): Boolean {
        if (id < 0) {
            val target = getAll(context).firstOrNull { it.id == id } ?: return false
            return unblock(context, target.originalNumber)
        }
        return try {
            val uri = ContentUris.withAppendedId(
                BlockedNumberContract.BlockedNumbers.CONTENT_URI,
                id
            )
            context.contentResolver.delete(uri, null, null) > 0
        } catch (_: Exception) {
            false
        }
    }

    fun getAll(context: Context): List<BlockedNumber> {
        val results = mutableListOf<BlockedNumber>()
        val seenNumbers = mutableSetOf<String>()
        try {
            context.contentResolver.query(
                BlockedNumberContract.BlockedNumbers.CONTENT_URI,
                arrayOf(
                    BlockedNumberContract.BlockedNumbers.COLUMN_ID,
                    BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER,
                    BlockedNumberContract.BlockedNumbers.COLUMN_E164_NUMBER
                ),
                null,
                null,
                null
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(BlockedNumberContract.BlockedNumbers.COLUMN_ID)
                val originalIndex = cursor.getColumnIndexOrThrow(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER)
                val e164Index = cursor.getColumnIndexOrThrow(BlockedNumberContract.BlockedNumbers.COLUMN_E164_NUMBER)
                while (cursor.moveToNext()) {
                    val original = cursor.getString(originalIndex) ?: continue
                    seenNumbers.add(original)
                    results.add(
                        BlockedNumber(
                            id = cursor.getLong(idIndex),
                            originalNumber = original,
                            strippedNumber = cursor.getString(e164Index)
                        )
                    )
                }
            }
        } catch (_: Exception) {
        }

        val fallbackPatterns = try {
            getPrefs(context).getBlockedPatterns()
        } catch (_: Exception) {
            emptyList()
        }
        fallbackPatterns.forEachIndexed { index, pattern ->
            if (seenNumbers.add(pattern)) {
                results.add(
                    BlockedNumber(
                        id = -(index + 1L),
                        originalNumber = pattern,
                        strippedNumber = null
                    )
                )
            }
        }

        return results
    }
}
