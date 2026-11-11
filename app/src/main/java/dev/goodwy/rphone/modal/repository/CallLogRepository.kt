package dev.goodwy.rphone.modal.repository

import android.content.ComponentName
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.os.Bundle
import android.provider.CallLog
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.controller.util.normalizePhoneNumber
import dev.goodwy.rphone.modal.`interface`.ICallLogRepository
import dev.goodwy.rphone.modal.data.CallLogEntry
import dev.goodwy.rphone.modal.data.Contact
import dev.goodwy.rphone.modal.`interface`.IContactsRepository
import androidx.core.net.toUri
import dev.goodwy.rphone.R
import dev.goodwy.rphone.modal.data.getDisplayName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class CallLogRepository(
    private val context: Context,
    private val contentResolver: ContentResolver,
    private val contactsRepo: IContactsRepository,
    private val preferenceManager: PreferenceManager
) : ICallLogRepository {

    private val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager

    private var cachedContactMap: Map<String, Contact>? = null
    private var lastContactMapUpdate = 0L
    private val CONTACT_MAP_TTL = 60_000L // 1 minute
    private val contactMapMutex = Mutex()

    private suspend fun getOrFetchContactMap(): Map<String, Contact> {
        // Check cache under the lock first (fast path)
        contactMapMutex.withLock {
            val existing = cachedContactMap
            val now = System.currentTimeMillis()
            if (existing != null && now - lastContactMapUpdate < CONTACT_MAP_TTL) {
                return existing
            }
        }

        // Fetch outside the lock so coroutines don't block each other
        val allContacts = try { contactsRepo.getContacts() } catch (e: Exception) { emptyList() }
        if (allContacts.isEmpty()) {
            return emptyMap()
        }
        val map = mutableMapOf<String, Contact>()
        allContacts.forEach { contact ->
            contact.phoneNumbers.forEach { number ->
                val normalized = normalizePhoneNumber(number)
                val key = if (normalized.length >= 10) normalized.takeLast(10) else normalized
                map[key] = contact
            }
        }

        // Write back under lock — only update if no other coroutine already wrote a fresher value
        return contactMapMutex.withLock {
            val now = System.currentTimeMillis()
            val existing = cachedContactMap
            if (existing != null && now - lastContactMapUpdate < CONTACT_MAP_TTL) {
                // Another coroutine already refreshed; discard our result and return theirs
                existing
            } else {
                cachedContactMap = map
                lastContactMapUpdate = now
                map
            }
        }
    }

    override suspend fun getCallLogs(): List<CallLogEntry> = withContext(Dispatchers.IO) {
        val callLogs = mutableListOf<CallLogEntry>()
        val contactMap = getOrFetchContactMap()

        val baseProjection = mutableListOf(
            CallLog.Calls._ID,
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.CACHED_PHOTO_URI,
            CallLog.Calls.CACHED_LOOKUP_URI,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION
        )

        baseProjection.add(CallLog.Calls.PHONE_ACCOUNT_ID)
        baseProjection.add(CallLog.Calls.PHONE_ACCOUNT_COMPONENT_NAME)

        val limit = preferenceManager.getCallLogLimit()
        val uri = if (limit > 0) {
            CallLog.Calls.CONTENT_URI.buildUpon()
                .appendQueryParameter(CallLog.Calls.LIMIT_PARAM_KEY, limit.toString())
                .build()
        } else {
            CallLog.Calls.CONTENT_URI
        }

        try {
            val cursor = contentResolver.query(
                uri,
                baseProjection.toTypedArray(),
                null,
                null,
                "${CallLog.Calls.DATE} DESC"
            )

            cursor?.use { parseCursor(it, callLogs, contactMap, limit) }
        } catch (e: Exception) {
            // If the above fails, try a safer subset
            try {
                val safeProjection = arrayOf(
                    CallLog.Calls._ID, CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME,
                    CallLog.Calls.CACHED_PHOTO_URI, CallLog.Calls.CACHED_LOOKUP_URI,
                    CallLog.Calls.TYPE, CallLog.Calls.DATE, CallLog.Calls.DURATION,
                    CallLog.Calls.PHONE_ACCOUNT_ID, CallLog.Calls.PHONE_ACCOUNT_COMPONENT_NAME
                )
                val cursor = contentResolver.query(
                    CallLog.Calls.CONTENT_URI,
                    safeProjection,
                    null,
                    null,
                    "${CallLog.Calls.DATE} DESC"
                )
                cursor?.use { parseCursor(it, callLogs, contactMap, limit) }
            } catch (e2: Exception) {
                e2.printStackTrace()
            }
        }

        callLogs
    }

    override suspend fun saveCallLog(entry: CallLogEntry) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put(CallLog.Calls.NUMBER, entry.number)
            put(CallLog.Calls.TYPE, entry.type)
            put(CallLog.Calls.DATE, entry.date)
            put(CallLog.Calls.DURATION, entry.duration)
            put(CallLog.Calls.NEW, 1)
        }
        try {
            contentResolver.insert(CallLog.Calls.CONTENT_URI, values)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        Unit
    }

    private fun contactIdFromLookupUri(lookupUri: String): String? {
        return try {
            val segments = lookupUri.toUri().pathSegments
            segments.lastOrNull { it.toLongOrNull() != null } ?: segments.lastOrNull()
        } catch (_: Exception) {
            null
        }
    }

    private fun parseCursor(cursor: Cursor, callLogs: MutableList<CallLogEntry>, contactMap: Map<String, Contact>, limit: Int = 0) {
        val idIdx = cursor.getColumnIndex(CallLog.Calls._ID)
        val numberIdx = cursor.getColumnIndex(CallLog.Calls.NUMBER)
        val cachedNameIdx = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME)
        val cachedPhotoIdx = cursor.getColumnIndex(CallLog.Calls.CACHED_PHOTO_URI)
        val cachedLookupIdx = cursor.getColumnIndex(CallLog.Calls.CACHED_LOOKUP_URI)
        val typeIdx = cursor.getColumnIndex(CallLog.Calls.TYPE)
        val dateIdx = cursor.getColumnIndex(CallLog.Calls.DATE)
        val durationIdx = cursor.getColumnIndex(CallLog.Calls.DURATION)

        val accountIdIdx = cursor.getColumnIndex(CallLog.Calls.PHONE_ACCOUNT_ID)
        val componentNameIdx = cursor.getColumnIndex(CallLog.Calls.PHONE_ACCOUNT_COMPONENT_NAME)

        val tempLogs = mutableListOf<CallLogEntry>()
        val simCache = mutableMapOf<String, String>()
        val unknownLabel = context.getString(R.string.label_unknown)

        val displayOrder = preferenceManager.getInt(PreferenceManager.KEY_CONTACT_DISPLAY_ORDER, 0)

        val zone = ZoneId.systemDefault()
        fun dayKey(dateMillis: Long): LocalDate =
            Instant.ofEpochMilli(dateMillis).atZone(zone).toLocalDate()
        var lastEntryDay: LocalDate? = null

        while (cursor.moveToNext()) {
            if (limit > 0 && tempLogs.size >= limit) {
                break
            }
            val callId = cursor.getLong(idIdx)
            val number = cursor.getString(numberIdx) ?: unknownLabel
            val type = cursor.getInt(typeIdx)
            val date = cursor.getLong(dateIdx)
            val duration = cursor.getLong(durationIdx)

            var simLabel: String? = null

            val isBlocked = type == CallLog.Calls.BLOCKED_TYPE// || type == CallLog.Calls.REJECTED_TYPE

            // If label is missing, try to resolve it from account ID
            if (accountIdIdx != -1 && componentNameIdx != -1) {
                val accountId = cursor.getString(accountIdIdx)
                val componentStr = cursor.getString(componentNameIdx)

                if (!accountId.isNullOrEmpty() && !componentStr.isNullOrEmpty()) {
                    simLabel = simCache.getOrPut("$componentStr/$accountId") {
                        try {
                            val componentName = ComponentName.unflattenFromString(componentStr)
                            if (componentName != null) {
                                val handle = PhoneAccountHandle(componentName, accountId)
                                telecomManager?.getPhoneAccount(handle)?.label?.toString() ?: ""
                            } else ""
                        } catch (e: Exception) { "" }
                    }
                }
            }

            if (simLabel?.isEmpty() == true) simLabel = null

            // Enrich with contact data
            val normalizedNum = normalizePhoneNumber(number)
            val lookupKey = if (normalizedNum.length >= 10) normalizedNum.takeLast(10) else normalizedNum
            val matchedContact = contactMap[lookupKey]

//            val displayName = matchedContact?.displayName ?: cursor.getString(cachedNameIdx)
            val photoUri = matchedContact?.photoUri ?: cursor.getString(cachedPhotoIdx)
            val contactId = matchedContact?.id ?: cursor.getString(cachedLookupIdx)?.let {
                contactIdFromLookupUri(it)
            }

            val contactName = if (matchedContact != null) getDisplayName(matchedContact, displayOrder) else null //matchedContact?.displayName
            val cachedName = cursor.getString(cachedNameIdx) // cached name from CallLog
            val phoneDetails = matchedContact?.phoneDetails?.firstOrNull { normalizePhoneNumber(it.number) == normalizedNum }
            val phoneType = phoneDetails?.type
            val phoneLabel = phoneDetails?.label

//                val isCallerIdName = contactName == null && cachedName != null
            val isCallerIdName = contactName.isNullOrEmpty() && !cachedName.isNullOrEmpty()
            val displayName = when {
                !contactName.isNullOrEmpty() -> contactName
                !cachedName.isNullOrEmpty() -> cachedName
                else -> number
            }

            val singleEntry =
                CallLogEntry(
                    id = callId,
                    number = number,
                    name = displayName,
                    type = type,
                    date = date,
                    duration = duration,
                    photoUri = photoUri,
                    contactId = contactId,
                    simLabel = simLabel,
                    isBlocked = isBlocked,
                    types = listOf(type),
                    ids = listOf(callId),
                    isCallerIdName = isCallerIdName,
                    phoneType = phoneType,
                    phoneLabel = phoneLabel
                )

            val lastEntry = tempLogs.lastOrNull()
            // Group only if the number and date are the same
            val sameDay = if (lastEntry != null) {
                val lastDay = lastEntryDay ?: dayKey(lastEntry.date).also { lastEntryDay = it }
                lastDay == dayKey(date)
            } else false

            if (lastEntry != null && lastEntry.number == number && sameDay) {
                val currentSubLogs = if (lastEntry.subLogs.isEmpty()) listOf(lastEntry) else lastEntry.subLogs
                tempLogs[tempLogs.size - 1] = lastEntry.copy(
                    types = lastEntry.types + type,
                    ids = lastEntry.ids + callId,
                    subLogs = currentSubLogs + singleEntry
                )
            } else {
                tempLogs.add(singleEntry)
                lastEntryDay = dayKey(date)
            }
        }
        callLogs.addAll(tempLogs)
    }

    override suspend fun deleteCallLog(number: String) = withContext(Dispatchers.IO) {
        try {
            contentResolver.delete(
                CallLog.Calls.CONTENT_URI,
                "${CallLog.Calls.NUMBER} = ?",
                arrayOf(number)
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
        Unit
    }

    override suspend fun deleteCallLogsByIds(ids: List<Long>) = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext
        try {
            // Build a parameterized IN clause to avoid string concatenation / injection risk.
            // ContentResolver doesn't support bind args for IN directly, so we use
            // placeholders and pass the IDs as String selection args.
            val placeholders = ids.joinToString(",") { "?" }
            val selection = "${CallLog.Calls._ID} IN ($placeholders)"
            val selectionArgs = ids.map { it.toString() }.toTypedArray()
            contentResolver.delete(CallLog.Calls.CONTENT_URI, selection, selectionArgs)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override suspend fun clearCallLogs() = withContext(Dispatchers.IO) {
        try {
            contentResolver.delete(CallLog.Calls.CONTENT_URI, null, null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        Unit
    }

    override suspend fun invalidateContactCache() {
        contactMapMutex.withLock {
            cachedContactMap = null
            lastContactMapUpdate = 0L
        }
    }
}