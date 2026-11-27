package dev.goodwy.rphone.modal.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import dev.goodwy.rphone.controller.spam.SpamBlocklistParser
import dev.goodwy.rphone.controller.spam.SpamDetectionEngine
import dev.goodwy.rphone.controller.util.BlockedNumbersManager
import dev.goodwy.rphone.controller.util.BusyModeExceptionsHelper
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.modal.data.CallerPresentation
import dev.goodwy.rphone.modal.data.SpamActionMode
import dev.goodwy.rphone.modal.data.SpamBlocklistImportEntry
import dev.goodwy.rphone.modal.data.SpamBlocklistImportResult
import dev.goodwy.rphone.modal.data.SpamCategory
import dev.goodwy.rphone.modal.data.SpamDbMatch
import dev.goodwy.rphone.modal.data.SpamEvaluationResult
import dev.goodwy.rphone.modal.data.SpamProtectionConfig
import dev.goodwy.rphone.modal.data.SpamSignals
import dev.goodwy.rphone.modal.data.StirShakenStatus
import dev.goodwy.rphone.modal.`interface`.IContactsRepository
import dev.goodwy.rphone.modal.db.SpamNumberDao
import dev.goodwy.rphone.modal.db.SpamNumberEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap

/**
 * Privacy-first repository for local spam detection, offline blocklist management,
 * and on-device user spam reporting. No network calls are ever made.
 */
class SpamRepository(
    private val context: Context,
    private val spamNumberDao: SpamNumberDao,
    private val contactsRepository: IContactsRepository,
    private val preferenceManager: PreferenceManager
) {
    private val cacheMutex = Mutex()
    @Volatile
    private var cachedEntriesByNormalized: Map<String, SpamNumberEntity>? = null

    private val recentUnknownCalls = ConcurrentHashMap<String, ArrayDeque<Long>>()

    fun observeSpamNumbers(): Flow<List<SpamNumberEntity>> = spamNumberDao.observeAll()

    fun getConfig(): SpamProtectionConfig {
        return SpamProtectionConfig(
            enabled = preferenceManager.getBoolean(PreferenceManager.KEY_SPAM_PROTECTION_ENABLED, true),
            actionMode = SpamActionMode.fromValue(
                preferenceManager.getInt(PreferenceManager.KEY_SPAM_ACTION, SpamActionMode.SILENCE.value)
            ),
            silenceUnknownCallers = preferenceManager.getBoolean(PreferenceManager.KEY_SILENCE_UNKNOWN, false),
            blockPrivateNumbers = preferenceManager.getBoolean(PreferenceManager.KEY_SPAM_BLOCK_PRIVATE, false),
            stirShakenCheckEnabled = preferenceManager.getBoolean(PreferenceManager.KEY_SPAM_STIR_SHAKEN_ENABLED, true),
            burstProtectionEnabled = preferenceManager.getBoolean(PreferenceManager.KEY_SPAM_BURST_PROTECTION, true),
            neighborSpoofingEnabled = preferenceManager.getBoolean(PreferenceManager.KEY_SPAM_NEIGHBOR_SPOOFING, false),
            showNotifications = preferenceManager.getBoolean(PreferenceManager.KEY_SPAM_NOTIFICATION, true),
            ownPhoneNumber = readOwnPhoneNumberSafely()
        )
    }

    suspend fun migrateLegacyBlockedContactsIfNeeded() = withContext(Dispatchers.IO) {
        if (preferenceManager.getBoolean(PreferenceManager.KEY_SPAM_LEGACY_MIGRATED, false)) return@withContext
        val legacyRaw = preferenceManager.getString(PreferenceManager.KEY_BLOCKED_CONTACTS, null)
        val numbers = legacyRaw?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }.orEmpty()
        if (numbers.isNotEmpty()) {
            val now = System.currentTimeMillis()
            val entities = numbers.mapNotNull { raw ->
                val normalized = BusyModeExceptionsHelper.normalizeManualNumber(raw)
                if (normalized.isEmpty()) null
                else SpamNumberEntity(
                    normalizedNumber = normalized,
                    rawNumber = raw,
                    category = SpamCategory.USER_REPORTED.storageKey,
                    source = SpamNumberEntity.SOURCE_LEGACY_MIGRATION,
                    confidence = 100,
                    reportCount = 1,
                    updatedAt = now
                )
            }
            if (entities.isNotEmpty()) {
                spamNumberDao.upsertAll(entities)
                invalidateCache()
            }
        }
        preferenceManager.setBoolean(PreferenceManager.KEY_SPAM_LEGACY_MIGRATED, true)
    }

    suspend fun evaluateIncomingCall(
        rawNumber: String,
        presentation: CallerPresentation = CallerPresentation.ALLOWED,
        stirShakenStatus: StirShakenStatus = StirShakenStatus.UNKNOWN,
        isExplicitlyBlocked: Boolean? = null,
        recordBurstAttempt: Boolean = true
    ): SpamEvaluationResult = withContext(Dispatchers.IO) {
        migrateLegacyBlockedContactsIfNeeded()

        val normalized = BusyModeExceptionsHelper.normalizeManualNumber(rawNumber)
        val isEmergency = rawNumber.isNotBlank() && (
            SpamDetectionEngine.isLikelyEmergencyNumber(rawNumber) ||
                runCatching {
                    val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                    tm?.isEmergencyNumber(rawNumber) == true
                }.getOrDefault(false)
            )

        val blocked = isExplicitlyBlocked ?: runCatching {
            rawNumber.isNotBlank() && BlockedNumbersManager.isBlocked(context, rawNumber)
        }.getOrDefault(false)

        val isSavedContact = if (rawNumber.isNotBlank() && !isEmergency) {
            runCatching { contactsRepository.getContactByNumber(rawNumber) != null }.getOrDefault(false)
        } else {
            false
        }

        val dbMatch = if (normalized.isNotEmpty() && !isSavedContact && !isEmergency) {
            findSpamMatch(rawNumber, normalized)
        } else {
            null
        }

        val burstCount = if (!isSavedContact && !isEmergency && normalized.isNotEmpty() && recordBurstAttempt) {
            recordAndCountBurst(normalized)
        } else if (!isSavedContact && !isEmergency && normalized.isNotEmpty()) {
            peekBurstCount(normalized)
        } else {
            0
        }

        val signals = SpamSignals(
            rawNumber = rawNumber,
            normalizedNumber = normalized,
            presentation = presentation,
            stirShakenStatus = stirShakenStatus,
            isSavedContact = isSavedContact,
            isExplicitlyBlocked = blocked,
            isEmergencyNumber = isEmergency,
            dbMatch = dbMatch,
            recentUnknownCallCountInWindow = burstCount
        )

        SpamDetectionEngine.evaluate(signals, getConfig())
    }

    suspend fun isInSpamDatabase(number: String): Boolean = withContext(Dispatchers.IO) {
        val normalized = BusyModeExceptionsHelper.normalizeManualNumber(number)
        if (normalized.isEmpty()) return@withContext false
        findSpamMatch(number, normalized) != null
    }

    suspend fun reportSpam(
        number: String,
        category: SpamCategory = SpamCategory.USER_REPORTED,
        label: String? = null,
        alsoBlock: Boolean = false
    ): Boolean = withContext(Dispatchers.IO) {
        val trimmed = number.trim()
        val normalized = BusyModeExceptionsHelper.normalizeManualNumber(trimmed)
        if (normalized.isEmpty() || SpamDetectionEngine.isLikelyEmergencyNumber(normalized)) {
            return@withContext false
        }

        val existing = spamNumberDao.getByNormalizedNumber(normalized)
        val entity = SpamNumberEntity(
            normalizedNumber = normalized,
            rawNumber = trimmed,
            category = category.storageKey,
            source = SpamNumberEntity.SOURCE_USER_REPORT,
            label = label?.trim()?.takeIf { it.isNotEmpty() } ?: existing?.label,
            confidence = 100,
            reportCount = (existing?.reportCount ?: 0) + 1,
            updatedAt = System.currentTimeMillis()
        )
        spamNumberDao.upsert(entity)
        invalidateCache()

        if (alsoBlock) {
            BlockedNumbersManager.block(context, trimmed)
        }
        true
    }

    suspend fun removeSpamNumber(normalizedNumber: String) = withContext(Dispatchers.IO) {
        spamNumberDao.deleteByNormalizedNumber(normalizedNumber)
        invalidateCache()
    }

    suspend fun importBlocklist(rawText: String): SpamBlocklistImportResult = withContext(Dispatchers.IO) {
        val (entries, skipped) = SpamBlocklistParser.parse(rawText)
        if (entries.isEmpty()) {
            return@withContext SpamBlocklistImportResult(importedCount = 0, skippedInvalidCount = skipped)
        }
        val now = System.currentTimeMillis()
        val entities = entries.map { entry ->
            SpamNumberEntity(
                normalizedNumber = entry.normalizedNumber,
                rawNumber = entry.rawNumber,
                category = entry.category.storageKey,
                source = SpamNumberEntity.SOURCE_IMPORTED_LIST,
                label = entry.label,
                confidence = 95,
                reportCount = 1,
                updatedAt = now
            )
        }
        spamNumberDao.upsertAll(entities)
        invalidateCache()
        SpamBlocklistImportResult(importedCount = entities.size, skippedInvalidCount = skipped)
    }

    suspend fun exportBlocklistCsv(): String = withContext(Dispatchers.IO) {
        val entries = spamNumberDao.getAll().map { entity ->
            SpamBlocklistImportEntry(
                normalizedNumber = entity.normalizedNumber,
                rawNumber = entity.rawNumber,
                category = SpamCategory.fromStorageKey(entity.category),
                label = entity.label
            )
        }
        SpamBlocklistParser.toCsv(entries)
    }

    suspend fun clearImportedBlocklist() = withContext(Dispatchers.IO) {
        spamNumberDao.deleteBySource(SpamNumberEntity.SOURCE_IMPORTED_LIST)
        invalidateCache()
    }

    suspend fun clearAllSpamNumbers() = withContext(Dispatchers.IO) {
        spamNumberDao.clearAll()
        invalidateCache()
    }

    private suspend fun getOrLoadCache(): Map<String, SpamNumberEntity> {
        cachedEntriesByNormalized?.let { return it }
        return cacheMutex.withLock {
            cachedEntriesByNormalized?.let { return@withLock it }
            val loaded = spamNumberDao.getAll().associateBy { it.normalizedNumber }
            cachedEntriesByNormalized = loaded
            loaded
        }
    }

    private fun invalidateCache() {
        cachedEntriesByNormalized = null
    }

    private suspend fun findSpamMatch(rawNumber: String, normalized: String): SpamDbMatch? {
        val cache = getOrLoadCache()
        if (cache.isEmpty()) return null

        val direct = cache[normalized]
        val matched = direct ?: cache.values.firstOrNull { entity ->
            BusyModeExceptionsHelper.comparePhoneNumbers(rawNumber, entity.normalizedNumber)
        } ?: return null

        return SpamDbMatch(
            normalizedNumber = matched.normalizedNumber,
            category = SpamCategory.fromStorageKey(matched.category),
            source = matched.source,
            label = matched.label,
            confidence = matched.confidence
        )
    }

    private fun recordAndCountBurst(normalized: String): Int {
        val now = System.currentTimeMillis()
        if (recentUnknownCalls.size > 200 && !recentUnknownCalls.containsKey(normalized)) {
            pruneOldBurstEntries(now)
        }
        val queue = recentUnknownCalls.computeIfAbsent(normalized) { ArrayDeque() }
        synchronized(queue) {
            val cutoff = now - SpamDetectionEngine.BURST_WINDOW_MILLIS
            while (queue.isNotEmpty() && queue.first() < cutoff) {
                queue.removeFirst()
            }
            queue.addLast(now)
            return queue.size
        }
    }

    private fun peekBurstCount(normalized: String): Int {
        val queue = recentUnknownCalls[normalized] ?: return 0
        val cutoff = System.currentTimeMillis() - SpamDetectionEngine.BURST_WINDOW_MILLIS
        synchronized(queue) {
            while (queue.isNotEmpty() && queue.first() < cutoff) {
                queue.removeFirst()
            }
            return queue.size
        }
    }

    private fun pruneOldBurstEntries(now: Long) {
        val cutoff = now - SpamDetectionEngine.BURST_WINDOW_MILLIS
        val iterator = recentUnknownCalls.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            synchronized(entry.value) {
                while (entry.value.isNotEmpty() && entry.value.first() < cutoff) {
                    entry.value.removeFirst()
                }
                if (entry.value.isEmpty()) {
                    iterator.remove()
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun readOwnPhoneNumberSafely(): String? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }
        return try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            tm?.line1Number?.takeIf { it.isNotBlank() }?.let {
                BusyModeExceptionsHelper.normalizeManualNumber(it).ifEmpty { null }
            }
        } catch (_: SecurityException) {
            null
        } catch (_: Exception) {
            null
        }
    }
}
