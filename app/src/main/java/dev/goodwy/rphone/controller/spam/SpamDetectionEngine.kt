package dev.goodwy.rphone.controller.spam

import dev.goodwy.rphone.controller.util.BusyModeExceptionsHelper
import dev.goodwy.rphone.modal.data.CallerPresentation
import dev.goodwy.rphone.modal.data.SpamActionMode
import dev.goodwy.rphone.modal.data.SpamBlocklistImportEntry
import dev.goodwy.rphone.modal.data.SpamCategory
import dev.goodwy.rphone.modal.data.SpamEvaluationResult
import dev.goodwy.rphone.modal.data.SpamProtectionConfig
import dev.goodwy.rphone.modal.data.SpamReason
import dev.goodwy.rphone.modal.data.SpamSignals
import dev.goodwy.rphone.modal.data.SpamVerdict
import dev.goodwy.rphone.modal.data.StirShakenStatus
import dev.goodwy.rphone.modal.db.SpamNumberEntity

/**
 * Pure, offline, deterministic spam detection engine.
 *
 * All evaluation happens strictly on-device with zero network calls.
 */
object SpamDetectionEngine {

    const val BURST_CALL_THRESHOLD = 3
    const val BURST_WINDOW_MILLIS = 120_000L

    private val COMMON_EMERGENCY_NUMBERS = setOf(
        "911", "112", "999", "000", "110", "111", "118", "119", "100", "101", "102", "108"
    )

    fun isLikelyEmergencyNumber(number: String): Boolean {
        val digits = number.filter { it.isDigit() }
        return digits.isNotEmpty() && digits in COMMON_EMERGENCY_NUMBERS
    }

    /**
     * Checks whether [incomingNormalized] shares the same area/prefix as [ownNormalized]
     * without being the same number (classic "neighbor spoofing" robocall pattern).
     */
    fun isNeighborSpoof(incomingNormalized: String, ownNormalized: String?): Boolean {
        if (ownNormalized.isNullOrBlank()) return false
        val incomingDigits = incomingNormalized.filter { it.isDigit() }
        val ownDigits = ownNormalized.filter { it.isDigit() }
        if (incomingDigits.length < 10 || ownDigits.length < 10) return false
        if (incomingDigits == ownDigits) return false

        val incomingTail10 = incomingDigits.takeLast(10)
        val ownTail10 = ownDigits.takeLast(10)
        return incomingTail10.take(6) == ownTail10.take(6)
    }

    fun evaluate(signals: SpamSignals, config: SpamProtectionConfig): SpamEvaluationResult {
        // 1. Emergency calls are never silenced, blocked, or flagged.
        if (signals.isEmergencyNumber || isLikelyEmergencyNumber(signals.rawNumber)) {
            return SpamEvaluationResult(
                verdict = SpamVerdict.ALLOW,
                category = SpamCategory.NONE,
                riskScore = 0,
                reasons = listOf(SpamReason.EMERGENCY_NUMBER),
                stirShakenStatus = signals.stirShakenStatus
            )
        }

        // 2. Explicit user blocklist always takes precedence.
        if (signals.isExplicitlyBlocked) {
            return SpamEvaluationResult(
                verdict = SpamVerdict.DECLINE,
                category = SpamCategory.SPAM,
                riskScore = 100,
                reasons = listOf(SpamReason.EXPLICITLY_BLOCKED),
                stirShakenStatus = signals.stirShakenStatus,
                isExplicitlyBlocked = true
            )
        }

        // 3. Saved / private contacts are trusted and never flagged as spam.
        if (signals.isSavedContact) {
            return SpamEvaluationResult(
                verdict = SpamVerdict.ALLOW,
                category = SpamCategory.NONE,
                riskScore = 0,
                reasons = listOf(SpamReason.SAVED_CONTACT),
                stirShakenStatus = signals.stirShakenStatus
            )
        }

        // If spam protection is disabled globally, only honor Silence Unknown Callers if toggled.
        if (!config.enabled) {
            return if (config.silenceUnknownCallers) {
                SpamEvaluationResult(
                    verdict = SpamVerdict.SILENCE,
                    category = SpamCategory.UNKNOWN_CALLER,
                    riskScore = 0,
                    reasons = listOf(SpamReason.UNKNOWN_NOT_IN_CONTACTS),
                    stirShakenStatus = signals.stirShakenStatus
                )
            } else {
                SpamEvaluationResult(
                    verdict = SpamVerdict.ALLOW,
                    category = SpamCategory.NONE,
                    riskScore = 0,
                    reasons = emptyList(),
                    stirShakenStatus = signals.stirShakenStatus
                )
            }
        }

        val configuredActionVerdict = when (config.actionMode) {
            SpamActionMode.WARN -> SpamVerdict.WARN
            SpamActionMode.SILENCE -> SpamVerdict.SILENCE
            SpamActionMode.DECLINE -> SpamVerdict.DECLINE
        }

        // 4. Local Spam Database / Imported Offline Blocklist match.
        val dbMatch = signals.dbMatch
        if (dbMatch != null) {
            val reason = if (dbMatch.source == SpamNumberEntity.SOURCE_IMPORTED_LIST) {
                SpamReason.BLOCKLIST_IMPORTED
            } else {
                SpamReason.LOCAL_SPAM_DB
            }
            return SpamEvaluationResult(
                verdict = configuredActionVerdict,
                category = dbMatch.category,
                riskScore = dbMatch.confidence.coerceIn(75, 100),
                reasons = listOf(reason),
                stirShakenStatus = signals.stirShakenStatus,
                matchedLabel = dbMatch.label
            )
        }

        // 5. Hidden / Private / Restricted Caller ID rule.
        if (config.blockPrivateNumbers && signals.presentation != CallerPresentation.ALLOWED) {
            val verdict = if (config.actionMode == SpamActionMode.DECLINE) {
                SpamVerdict.DECLINE
            } else {
                SpamVerdict.SILENCE
            }
            return SpamEvaluationResult(
                verdict = verdict,
                category = SpamCategory.PRIVATE_NUMBER,
                riskScore = 70,
                reasons = listOf(SpamReason.PRIVATE_OR_HIDDEN_NUMBER),
                stirShakenStatus = signals.stirShakenStatus
            )
        }

        // 6. Additive local heuristics (STIR/SHAKEN, rapid repeat bursts, neighbor spoofing).
        var score = 0
        val reasons = mutableListOf<SpamReason>()
        var primaryCategory = SpamCategory.NONE

        if (config.stirShakenCheckEnabled && signals.stirShakenStatus == StirShakenStatus.FAILED) {
            score += 65
            reasons += SpamReason.STIR_SHAKEN_FAILED
            primaryCategory = SpamCategory.SPOOFED
        }

        if (config.burstProtectionEnabled && signals.recentUnknownCallCountInWindow >= BURST_CALL_THRESHOLD) {
            score += 55
            reasons += SpamReason.RAPID_REPEAT_CALLS
            if (primaryCategory == SpamCategory.NONE) {
                primaryCategory = SpamCategory.ROBOCALL
            }
        }

        if (config.neighborSpoofingEnabled &&
            isNeighborSpoof(signals.normalizedNumber, config.ownPhoneNumber)
        ) {
            score += 35
            reasons += SpamReason.NEIGHBOR_SPOOFING
            if (primaryCategory == SpamCategory.NONE) {
                primaryCategory = SpamCategory.SPOOFED
            }
        }

        val clampedScore = score.coerceIn(0, 100)
        if (clampedScore >= 60) {
            return SpamEvaluationResult(
                verdict = configuredActionVerdict,
                category = primaryCategory,
                riskScore = clampedScore,
                reasons = reasons,
                stirShakenStatus = signals.stirShakenStatus
            )
        }

        if (clampedScore >= 35) {
            // Medium risk: silence if "Silence Unknown Callers" is also enabled, otherwise warn.
            val verdict = if (config.silenceUnknownCallers) {
                SpamVerdict.SILENCE
            } else {
                SpamVerdict.WARN
            }
            if (config.silenceUnknownCallers) {
                reasons += SpamReason.UNKNOWN_NOT_IN_CONTACTS
            }
            return SpamEvaluationResult(
                verdict = verdict,
                category = primaryCategory,
                riskScore = clampedScore,
                reasons = reasons,
                stirShakenStatus = signals.stirShakenStatus
            )
        }

        // 7. Unknown / non-contact caller silence rule.
        if (config.silenceUnknownCallers) {
            return SpamEvaluationResult(
                verdict = SpamVerdict.SILENCE,
                category = SpamCategory.UNKNOWN_CALLER,
                riskScore = 15,
                reasons = listOf(SpamReason.UNKNOWN_NOT_IN_CONTACTS),
                stirShakenStatus = signals.stirShakenStatus
            )
        }

        return SpamEvaluationResult(
            verdict = SpamVerdict.ALLOW,
            category = SpamCategory.NONE,
            riskScore = clampedScore,
            reasons = reasons,
            stirShakenStatus = signals.stirShakenStatus
        )
    }
}

object SpamBlocklistParser {

    /**
     * Parses CSV (`number,category,label`) or plain-text (one number per line) blocklists.
     * Comments starting with `#` and header rows are skipped safely.
     */
    fun parse(rawText: String): Pair<List<SpamBlocklistImportEntry>, Int> {
        val entries = LinkedHashMap<String, SpamBlocklistImportEntry>()
        var skipped = 0

        rawText.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) {
                return@forEach
            }

            val parts = splitCsvLine(line)
            val rawNumber = parts.getOrNull(0)?.trim().orEmpty()

            // Skip header rows like "number,category,label" even after leading comments
            if (entries.isEmpty() && (
                    rawNumber.equals("number", ignoreCase = true) ||
                        rawNumber.equals("phone", ignoreCase = true) ||
                        rawNumber.equals("phone_number", ignoreCase = true)
                    )
            ) {
                return@forEach
            }

            val normalized = BusyModeExceptionsHelper.normalizeManualNumber(rawNumber)
            if (normalized.isEmpty() || SpamDetectionEngine.isLikelyEmergencyNumber(normalized)) {
                skipped++
                return@forEach
            }

            val category = SpamCategory.fromStorageKey(parts.getOrNull(1))
            val label = parts.getOrNull(2)?.trim()?.takeIf { it.isNotEmpty() }

            entries[normalized] = SpamBlocklistImportEntry(
                normalizedNumber = normalized,
                rawNumber = rawNumber,
                category = category,
                label = label
            )
        }

        return entries.values.toList() to skipped
    }

    fun toCsv(entries: List<SpamBlocklistImportEntry>): String {
        return buildString {
            appendLine("number,category,label")
            entries.forEach { entry ->
                val escapedNumber = escapeCsv(entry.rawNumber.ifBlank { entry.normalizedNumber })
                val escapedCategory = escapeCsv(entry.category.storageKey)
                val escapedLabel = escapeCsv(entry.label.orEmpty())
                appendLine("$escapedNumber,$escapedCategory,$escapedLabel")
            }
        }
    }

    private fun splitCsvLine(line: String): List<String> {
        val separator = if (line.contains(',') || !line.contains(';')) ',' else ';'
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        for (ch in line) {
            when {
                ch == '"' -> inQuotes = !inQuotes
                ch == separator && !inQuotes -> {
                    result += current.toString()
                    current.clear()
                }
                else -> current.append(ch)
            }
        }
        result += current.toString()
        return result
    }

    private fun escapeCsv(value: String): String {
        return if (value.contains(',') || value.contains('"') || value.contains('\n')) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }
}
