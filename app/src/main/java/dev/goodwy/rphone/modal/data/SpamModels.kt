package dev.goodwy.rphone.modal.data

import androidx.compose.runtime.Immutable

enum class SpamCategory(val storageKey: String) {
    NONE("NONE"),
    SPAM("SPAM"),
    TELEMARKETER("TELEMARKETER"),
    SCAM("SCAM"),
    ROBOCALL("ROBOCALL"),
    SPOOFED("SPOOFED"),
    PRIVATE_NUMBER("PRIVATE_NUMBER"),
    UNKNOWN_CALLER("UNKNOWN_CALLER"),
    USER_REPORTED("USER_REPORTED");

    companion object {
        fun fromStorageKey(value: String?): SpamCategory {
            if (value.isNullOrBlank()) return SPAM
            return entries.firstOrNull { it.storageKey.equals(value.trim(), ignoreCase = true) } ?: SPAM
        }
    }
}

enum class SpamVerdict {
    ALLOW,
    WARN,
    SILENCE,
    DECLINE
}

enum class SpamActionMode(val value: Int) {
    WARN(0),
    SILENCE(1),
    DECLINE(2);

    companion object {
        fun fromValue(value: Int): SpamActionMode =
            entries.firstOrNull { it.value == value } ?: SILENCE
    }
}

enum class StirShakenStatus {
    UNKNOWN,
    PASSED,
    FAILED,
    NOT_VERIFIED
}

enum class CallerPresentation {
    ALLOWED,
    RESTRICTED,
    UNKNOWN,
    PAYPHONE
}

enum class SpamReason {
    SAVED_CONTACT,
    EMERGENCY_NUMBER,
    EXPLICITLY_BLOCKED,
    LOCAL_SPAM_DB,
    BLOCKLIST_IMPORTED,
    STIR_SHAKEN_FAILED,
    PRIVATE_OR_HIDDEN_NUMBER,
    UNKNOWN_NOT_IN_CONTACTS,
    RAPID_REPEAT_CALLS,
    NEIGHBOR_SPOOFING
}

data class SpamProtectionConfig(
    val enabled: Boolean = true,
    val actionMode: SpamActionMode = SpamActionMode.SILENCE,
    val silenceUnknownCallers: Boolean = false,
    val blockPrivateNumbers: Boolean = false,
    val stirShakenCheckEnabled: Boolean = true,
    val burstProtectionEnabled: Boolean = true,
    val neighborSpoofingEnabled: Boolean = false,
    val showNotifications: Boolean = true,
    val ownPhoneNumber: String? = null
)

data class SpamDbMatch(
    val normalizedNumber: String,
    val category: SpamCategory,
    val source: String,
    val label: String? = null,
    val confidence: Int = 100
)

data class SpamSignals(
    val rawNumber: String,
    val normalizedNumber: String,
    val presentation: CallerPresentation = CallerPresentation.ALLOWED,
    val stirShakenStatus: StirShakenStatus = StirShakenStatus.UNKNOWN,
    val isSavedContact: Boolean = false,
    val isExplicitlyBlocked: Boolean = false,
    val isEmergencyNumber: Boolean = false,
    val dbMatch: SpamDbMatch? = null,
    val recentUnknownCallCountInWindow: Int = 0
)

@Immutable
data class SpamEvaluationResult(
    val verdict: SpamVerdict = SpamVerdict.ALLOW,
    val category: SpamCategory = SpamCategory.NONE,
    val riskScore: Int = 0,
    val reasons: List<SpamReason> = emptyList(),
    val stirShakenStatus: StirShakenStatus = StirShakenStatus.UNKNOWN,
    val matchedLabel: String? = null,
    val isExplicitlyBlocked: Boolean = false
) {
    val isSuspectedSpam: Boolean
        get() = verdict != SpamVerdict.ALLOW || riskScore >= 35
}

data class SpamBlocklistImportEntry(
    val normalizedNumber: String,
    val rawNumber: String,
    val category: SpamCategory = SpamCategory.SPAM,
    val label: String? = null
)

data class SpamBlocklistImportResult(
    val importedCount: Int,
    val skippedInvalidCount: Int
)
