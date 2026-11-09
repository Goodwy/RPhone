package dev.goodwy.rphone.controller.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import android.provider.ContactsContract
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Persists calling card appearance (font + name colour).
 *
 * It follows the same pointer model as [CallBackgroundStore]: a style is
 * written for the contact id and for every known number key, so a style still
 * resolves when a contact is merged, made private, or only a number is known.
 */
object CallingCardStore {

    private const val DEFAULT_ID = "default"
    private const val PRUNE_INTERVAL_MS = 15L * 60L * 1000L

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var lastPruneAt = 0L

    @Volatile
    private var cachedPrefs: PreferenceManager? = null

    fun attach(manager: PreferenceManager) {
        if (cachedPrefs != null) return
        synchronized(this) {
            if (cachedPrefs == null) cachedPrefs = manager
        }
    }

    private fun prefs(context: Context): PreferenceManager {
        val existing = cachedPrefs
        if (existing != null) return existing
        return synchronized(this) {
            cachedPrefs ?: PreferenceManager(context.applicationContext).also { cachedPrefs = it }
        }
    }

    fun peek(context: Context, contactId: String?, numbers: List<String>): CallingCardStyle? {
        val manager = prefs(context.applicationContext)
        val candidates = mutableListOf<String>()
        if (!contactId.isNullOrBlank()) {
            manager.getCallingCardStyle(contactId)?.let { candidates.add(it) }
        }
        CallBackgroundStore.numberKeys(numbers).forEach { key ->
            manager.getCallingCardStyleForNumber(key)?.let { candidates.add(it) }
        }
        return candidates.firstNotNullOfOrNull { CallingCardStyleCodec.decode(it) }
    }

    fun peekDefault(context: Context): CallingCardStyle? {
        val stored = prefs(context.applicationContext).getCallingCardStyle(DEFAULT_ID)
        return CallingCardStyleCodec.decode(stored)
    }

    /**
     * Effective style for a contact: per-contact first, then the global
     * default, then the plain app theme.
     */
    fun effectiveStyle(context: Context, contactId: String?, numbers: List<String>): CallingCardStyle {
        val app = context.applicationContext
        schedulePrune(app)
        return peek(app, contactId, numbers)
            ?: peekDefault(app)
            ?: CallingCardStyle.ThemeDefault
    }

    suspend fun save(
        context: Context,
        contactId: String?,
        numbers: List<String>,
        style: CallingCardStyle
    ) = withContext(Dispatchers.IO) {
        val manager = prefs(context.applicationContext)
        val keys = CallBackgroundStore.numberKeys(numbers)
        if (contactId.isNullOrBlank() && keys.isEmpty()) return@withContext
        writePointers(manager, contactId, keys, CallingCardStyleCodec.encode(style))
    }

    suspend fun saveDefault(context: Context, style: CallingCardStyle) =
        withContext(Dispatchers.IO) {
            prefs(context.applicationContext).updateCallingCardStyleEntriesSuspend(
                mapOf(
                    PreferenceManager.callingCardStyleIdKey(DEFAULT_ID) to
                        CallingCardStyleCodec.encode(style)
                )
            )
        }

    suspend fun clear(context: Context, contactId: String?, numbers: List<String>) =
        withContext(Dispatchers.IO) { clearBlocking(context, contactId, numbers) }

    suspend fun clearBlocking(context: Context, contactId: String?, numbers: List<String>) {
        val manager = prefs(context.applicationContext)
        val updates = mutableMapOf<String, String?>()
        if (!contactId.isNullOrBlank() && manager.getCallingCardStyle(contactId) != null) {
            updates[PreferenceManager.callingCardStyleIdKey(contactId)] = null
        }
        CallBackgroundStore.numberKeys(numbers).forEach { key ->
            if (manager.getCallingCardStyleForNumber(key) != null) {
                updates[PreferenceManager.callingCardStyleNumberKey(key)] = null
            }
        }
        if (updates.isEmpty()) return
        manager.updateCallingCardStyleEntriesSuspend(updates)
    }

    suspend fun clearDefault(context: Context) {
        val manager = prefs(context.applicationContext)
        if (manager.getCallingCardStyle(DEFAULT_ID) != null) {
            manager.updateCallingCardStyleEntriesSuspend(
                mapOf(PreferenceManager.callingCardStyleIdKey(DEFAULT_ID) to null)
            )
        }
    }

    /**
     * Moves a style when a contact id changes (private/public switch, merge).
     */
    suspend fun carryBlocking(
        context: Context,
        fromContactId: String?,
        toContactId: String?,
        numbers: List<String>
    ) {
        val manager = prefs(context.applicationContext)
        val keys = CallBackgroundStore.numberKeys(numbers)
        val stored = storedCandidates(manager, fromContactId, keys).firstOrNull()
        if (stored != null) {
            writePointers(manager, toContactId, keys, stored)
        }
        if (!fromContactId.isNullOrBlank() && fromContactId != toContactId &&
            manager.getCallingCardStyle(fromContactId) != null
        ) {
            manager.updateCallingCardStyleEntriesSuspend(
                mapOf(PreferenceManager.callingCardStyleIdKey(fromContactId) to null)
            )
        }
    }

    private fun storedCandidates(
        manager: PreferenceManager,
        contactId: String?,
        keys: List<String>
    ): List<String> {
        val candidates = mutableListOf<String>()
        if (!contactId.isNullOrBlank()) {
            manager.getCallingCardStyle(contactId)?.let { candidates.add(it) }
        }
        keys.forEach { key ->
            manager.getCallingCardStyleForNumber(key)?.let { candidates.add(it) }
        }
        return candidates.distinct()
    }

    private suspend fun writePointers(
        manager: PreferenceManager,
        contactId: String?,
        keys: List<String>,
        value: String
    ) {
        val updates = mutableMapOf<String, String?>()
        if (!contactId.isNullOrBlank() && manager.getCallingCardStyle(contactId) != value) {
            updates[PreferenceManager.callingCardStyleIdKey(contactId)] = value
        }
        keys.forEach { key ->
            if (manager.getCallingCardStyleForNumber(key) != value) {
                updates[PreferenceManager.callingCardStyleNumberKey(key)] = value
            }
        }
        if (updates.isEmpty()) return
        manager.updateCallingCardStyleEntriesSuspend(updates)
    }

    private fun schedulePrune(context: Context) {
        val now = SystemClock.elapsedRealtime()
        if (lastPruneAt != 0L && now - lastPruneAt < PRUNE_INTERVAL_MS) return
        lastPruneAt = now
        ioScope.launch { pruneStaleIdKeys(context) }
    }

    /**
     * Mirrors the background store: drop id-keyed styles for contacts that no
     * longer exist. Number-keyed styles are kept because they can still match
     * a caller even when the address book no longer has the contact.
     */
    private suspend fun pruneStaleIdKeys(context: Context) {
        if (!hasContactsPermission(context)) return
        val existing = mutableSetOf<String>()
        try {
            context.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(ContactsContract.Contacts._ID),
                null,
                null,
                null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    cursor.getString(0)?.let { existing.add(it) }
                }
            } ?: return
        } catch (e: Exception) {
            return
        }
        if (existing.isEmpty()) return

        val prefix = PreferenceManager.CALLING_CARD_STYLE_PREFIX
        val numberPrefix = PreferenceManager.CALLING_CARD_STYLE_NUMBER_PREFIX
        val updates = mutableMapOf<String, String?>()
        prefs(context.applicationContext).getCallingCardStyleEntries().keys.forEach { key ->
            if (!key.startsWith(numberPrefix) && key.startsWith(prefix)) {
                val id = key.removePrefix(prefix)
                if (id == DEFAULT_ID) return@forEach
                if (id.toLongOrNull() != null && !existing.contains(id)) {
                    updates[key] = null
                }
            }
        }
        if (updates.isEmpty()) return
        prefs(context.applicationContext).updateCallingCardStyleEntriesSuspend(updates)
    }

    private fun hasContactsPermission(context: Context): Boolean {
        return try {
            context.checkSelfPermission(Manifest.permission.READ_CONTACTS) ==
                    PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }
    }
}
