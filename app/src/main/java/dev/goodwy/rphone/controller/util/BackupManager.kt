package dev.goodwy.rphone.controller.util

import android.content.Context
import androidx.core.net.toUri
import dev.goodwy.rphone.modal.`interface`.IContactsRepository
import org.json.JSONArray
import org.json.JSONObject
import org.koin.core.context.GlobalContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import dev.goodwy.rphone.modal.data.Contact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

object BackupManager {

    const val BACKUP_VERSION = 1

    private val backupJson = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
    }

    private fun isCallingCardKey(key: String): Boolean =
        key.startsWith(PreferenceManager.CONTACT_BACKGROUND_PREFIX) ||
                key.startsWith(PreferenceManager.CALLING_CARD_STYLE_PREFIX)

    data class BackupContents(
        val version: Int = 0,
        val createdAt: Long = 0L,
        val hasSettings: Boolean = false,
        val hasCallBackgrounds: Boolean = false,
        val hasNotes: Boolean = false,
        val hasContacts: Boolean = false,
        val hasPrivateContacts: Boolean = false,
        val hasCallLogs: Boolean = false,
    )

    fun getBackupDir(context: Context): File {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        val dir = File(base, "Backups")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    suspend fun writeBackup(
        context: Context,
        outputStream: OutputStream,
        backupSettings: Boolean = true,
        backupCallBackgrounds: Boolean = true,
        backupNotes: Boolean = true,
        backupContacts: Boolean = false,
        backupPrivateContacts: Boolean = false,
        backupCallLogs: Boolean = false,
    ): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            ZipOutputStream(outputStream).use { zip ->
                // 0. Version
                val versionJson = JSONObject().apply {
                    put("version", BACKUP_VERSION)
                    put("createdAt", System.currentTimeMillis())
                }.toString()
                zip.putNextEntry(ZipEntry("backup_version.json"))
                zip.write(versionJson.toByteArray(Charsets.UTF_8))
                zip.closeEntry()

                if (backupSettings) {
                    // 1. Backup Preferences
                    val manager = PreferenceManager(context)
                    val allPrefs = manager.getAllPreferences()
                    val filtered = allPrefs.filterKeys { !isCallingCardKey(it) }
                    val prefsJson = prefsToJson(filtered)
                    zip.putNextEntry(ZipEntry("prefs.json"))
                    zip.write(prefsJson.toByteArray(Charsets.UTF_8))
                    zip.closeEntry()

                    // 2. Backup custom font if set
                    try {
                        val fontFile = File(context.filesDir, "custom_font.ttf")
                        if (fontFile.exists() && fontFile.isFile && fontFile.length() > 0) {
                            zip.putNextEntry(ZipEntry("custom_font.ttf"))
                            FileInputStream(fontFile).use { it.copyTo(zip) }
                            zip.closeEntry()
                        }
                    } catch (_: Exception) {}
                }

                // 3. Backup call backgrounds
                if (backupCallBackgrounds) {
                    val manager = PreferenceManager(context)
                    val allPrefs = manager.getAllPreferences()
                    val filtered = allPrefs.filterKeys { isCallingCardKey(it) }
                    if (filtered.isNotEmpty()) {
                        val json = prefsToJson(filtered)
                        zip.putNextEntry(ZipEntry("calling_cards.json"))
                        zip.write(json.toByteArray(Charsets.UTF_8))
                        zip.closeEntry()
                    }
                    CallBackgroundStore.exportBackgroundsToZip(context, zip)
                }

                // 4. Backup call notes
                if (backupNotes) {
                    val notesDir = NoteManager.getNotesDir(context)
                    notesDir.listFiles()?.filter { it.extension == "txt" }?.forEach { noteFile ->
                        zip.putNextEntry(ZipEntry("notes/${noteFile.name}"))
                        FileInputStream(noteFile).use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }

                // 5. Backup contacts
                if (backupContacts || backupPrivateContacts) {
                    val contactsRepo = runCatching { GlobalContext.get().get<IContactsRepository>() }.getOrNull()
                    if (contactsRepo == null) {
                        android.util.Log.e("BackupManager", "IContactsRepository not registered")
                    } else {
                        if (backupContacts) {
                            val publicContacts = contactsRepo.getContactsFull(includePrivate = false, includeHidden = false)
                            if (publicContacts.isNotEmpty()) {
                                writeContactsToZip(context, zip, "contacts/contacts.json", publicContacts, "contacts/photos/")
                            }
                        }
                        if (backupPrivateContacts) {
                            val privateContacts = contactsRepo.getPrivateContacts()
                            if (privateContacts.isNotEmpty()) {
                                writeContactsToZip(context, zip, "contacts/private_contacts.json", privateContacts, "contacts/private_photos/")
                            }
                        }
                    }
                }

                // 5. Backup call logs
                if (backupCallLogs) {
                    try {
                        val cursor = context.contentResolver.query(
                            android.provider.CallLog.Calls.CONTENT_URI,
                            arrayOf(
                                android.provider.CallLog.Calls.NUMBER,
                                android.provider.CallLog.Calls.CACHED_NAME,
                                android.provider.CallLog.Calls.TYPE,
                                android.provider.CallLog.Calls.DATE,
                                android.provider.CallLog.Calls.DURATION,
                                android.provider.CallLog.Calls.PHONE_ACCOUNT_ID,
                                android.provider.CallLog.Calls.IS_READ,
                                android.provider.CallLog.Calls.NEW
                            ),
                            null,
                            null,
                            "${android.provider.CallLog.Calls.DATE} DESC"
                        )
                        cursor?.use { c ->
                            val numIdx = c.getColumnIndex(android.provider.CallLog.Calls.NUMBER)
                            val nameIdx = c.getColumnIndex(android.provider.CallLog.Calls.CACHED_NAME)
                            val typeIdx = c.getColumnIndex(android.provider.CallLog.Calls.TYPE)
                            val dateIdx = c.getColumnIndex(android.provider.CallLog.Calls.DATE)
                            val durIdx = c.getColumnIndex(android.provider.CallLog.Calls.DURATION)
                            val accIdx = c.getColumnIndex(android.provider.CallLog.Calls.PHONE_ACCOUNT_ID)
                            val isReadIdx = c.getColumnIndex(android.provider.CallLog.Calls.IS_READ)
                            val newIdx = c.getColumnIndex(android.provider.CallLog.Calls.NEW)

                            val jsonArray = JSONArray()
                            while (c.moveToNext()) {
                                val obj = JSONObject()
                                if (numIdx >= 0) obj.put("number", c.getString(numIdx) ?: "")
                                if (nameIdx >= 0 && !c.isNull(nameIdx)) obj.put("cachedName", c.getString(nameIdx))
                                if (typeIdx >= 0) obj.put("type", c.getInt(typeIdx))
                                if (dateIdx >= 0) obj.put("date", c.getLong(dateIdx))
                                if (durIdx >= 0) obj.put("duration", c.getLong(durIdx))
                                if (accIdx >= 0 && !c.isNull(accIdx)) obj.put("phoneAccountId", c.getString(accIdx))
                                if (isReadIdx >= 0) obj.put("isRead", c.getInt(isReadIdx))
                                if (newIdx >= 0) obj.put("isNew", c.getInt(newIdx))
                                jsonArray.put(obj)
                            }
                            if (jsonArray.length() > 0) {
                                zip.putNextEntry(ZipEntry("call_logs/call_logs.json"))
                                zip.write(jsonArray.toString().toByteArray(Charsets.UTF_8))
                                zip.closeEntry()
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("BackupManager", "Failed to backup call logs", e)
                    }
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun writeContactsToZip(
        context: Context,
        zip: ZipOutputStream,
        jsonEntryName: String,
        contacts: List<Contact>,
        photosDir: String,
    ) {
        val photoByIndex = mutableMapOf<Int, Pair<String, ByteArray>>()
        contacts.forEachIndexed { index, c ->
            val uri = c.photoUri ?: return@forEachIndexed
            val bytes = readPhotoBytes(context, uri) ?: return@forEachIndexed
            val fileName = "photo_${index}_${UUID.randomUUID()}.jpg"
            photoByIndex[index] = fileName to bytes
        }
        // Save the photo
        photoByIndex.forEach { (_, pair) ->
            val (name, bytes) = pair
            zip.putNextEntry(ZipEntry("$photosDir$name"))
            zip.write(bytes)
            zip.closeEntry()
        }
        // We serialize it with photoUri = "photo:<name>" (an indicator that this is a local photo in the ZIP file)
        val sanitized = contacts.mapIndexed { index, c ->
            val marker = photoByIndex[index]?.first?.let { "photo:$it" }
            c.copy(photoUri = marker)
        }
        val json = backupJson.encodeToString(sanitized)
        zip.putNextEntry(ZipEntry(jsonEntryName))
        zip.write(json.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun readPhotoBytes(context: Context, uriString: String): ByteArray? {
        if (uriString.startsWith("photo:")) return null
        return try {
            val uri = uriString.toUri()
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (_: Exception) { null }
    }

    suspend fun createBackup(
        context: Context,
        backupSettings: Boolean = true,
        backupCallBackgrounds: Boolean = true,
        backupNotes: Boolean = true,
        backupContacts: Boolean = false,
        backupPrivateContacts: Boolean = false,
        backupCallLogs: Boolean = false,
    ): File? = withContext(Dispatchers.IO) {
        return@withContext try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val backupFile = File(
                getBackupDir(
                    context
                ), "RPhone_Backup_$timestamp.rphone")
            val ok = FileOutputStream(backupFile).use { outputStream ->
                writeBackup(context, outputStream, backupSettings, backupCallBackgrounds, backupNotes, backupContacts, backupPrivateContacts, backupCallLogs)
            }
            if (ok) backupFile else null
        } catch (_: Exception) {
            null
        }
    }

    fun inspectBackup(backupFile: File): BackupContents {
        var version = 0
        var createdAt = 0L
        var hasSettings = false
        var hasCallBackgrounds = false
        var hasNotes = false
        var hasContacts = false
        var hasPrivateContacts = false
        var hasCallLogs = false

        try {
            ZipInputStream(FileInputStream(backupFile)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val name = entry.name
                    when {
                        name == "backup_version.json" -> {
                            val json = runCatching { JSONObject(zip.readBytes().toString(Charsets.UTF_8)) }.getOrNull()
                            version = json?.optInt("version", 0) ?: 0
                            createdAt = json?.optLong("createdAt", 0L) ?: 0L
                        }
                        name == "prefs.json" -> {
                            hasSettings = true
                        }
                        name == "calling_cards.json" -> {
                            hasCallBackgrounds = true
                        }
                        name.startsWith("call_backgrounds/") -> {
                            hasCallBackgrounds = true
                        }
                        name.startsWith("notes/") -> {
                            hasNotes = true
                        }
                        name == "contacts/contacts.json" -> {
                            hasContacts = isNonEmptyJsonArray(zip)
                        }
                        name == "contacts/private_contacts.json" -> {
                            hasPrivateContacts = isNonEmptyJsonArray(zip)
                        }
                        name == "call_logs/call_logs.json" -> {
                            hasCallLogs = isNonEmptyJsonArray(zip)
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        } catch (_: Exception) {}

        return BackupContents(
            version = version,
            createdAt = createdAt,
            hasSettings = hasSettings,
            hasCallBackgrounds = hasCallBackgrounds,
            hasNotes = hasNotes,
            hasContacts = hasContacts,
            hasPrivateContacts = hasPrivateContacts,
            hasCallLogs = hasCallLogs,
        )
    }

    private fun isNonEmptyJsonArray(zip: ZipInputStream): Boolean {
        val reader = zip.bufferedReader(Charsets.UTF_8)
        val first = reader.read()
        if (first != '['.code) return false
        var c: Int
        while (reader.read().also { c = it } != -1) {
            if (c == ']'.code) return false
            if (!c.toChar().isWhitespace()) return true
        }
        return false
    }

    suspend fun restoreBackup(
        context: Context,
        backupFile: File,
        restoreSettings: Boolean = true,
        restoreCallBackgrounds: Boolean = true,
        restoreNotes: Boolean = true,
        restoreContacts: Boolean = true,
        restorePrivateContacts: Boolean = true,
        restoreCallLogs: Boolean = true,
    ): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            var restoredAny = false
            var backgroundsRestored = false

            // 1: Photos for keepsakes (map name -> bytes)
            val photos = mutableMapOf<String, ByteArray>()
            ZipInputStream(FileInputStream(backupFile)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name.startsWith("contacts/photos/") ||
                        entry.name.startsWith("contacts/private_photos/")) {
                        val name = entry.name.substringAfterLast('/')
                        photos[name] = zip.readBytes()
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }

            // 2: Everything else
            ZipInputStream(FileInputStream(backupFile)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val name = entry.name
                    when {
                        name == "prefs.json" -> {
                            if (restoreSettings) {
                                val json = zip.readBytes().toString(Charsets.UTF_8)
                                restorePrefs(context, json)
                                restoredAny = true
                            }
                        }
                        name == "custom_font.ttf" -> {
                            if (restoreSettings) {
                                val fontFile = File(context.filesDir, "custom_font.ttf")
                                fontFile.parentFile?.mkdirs()
                                FileOutputStream(fontFile).use { zip.copyTo(it) }
                                PreferenceManager(context)
                                    .setString(PreferenceManager.KEY_CUSTOM_FONT_PATH, fontFile.absolutePath)
                                restoredAny = true
                            }
                        }

                        name == "calling_cards.json" -> {
                            if (restoreCallBackgrounds) {
                                val json = zip.readBytes().toString(Charsets.UTF_8)
                                restorePrefs(context, json)
                                restoredAny = true
                            }
                        }
                        name.startsWith("call_backgrounds/") -> {
                            if (restoreCallBackgrounds) {
                                val fileName = entry.name.removePrefix("call_backgrounds/")
                                if (fileName.isNotEmpty()) {
                                    val ok = CallBackgroundStore.importBackgroundEntry(context, fileName, zip)
                                    if (ok) {
                                        backgroundsRestored = true
                                        restoredAny = true
                                    }
                                }
                            }
                        }

                        name.startsWith("notes/") -> {
                            if (restoreNotes) {
                                val fileName = name.removePrefix("notes/")
                                if (fileName.isNotEmpty()) {
                                    val notesDir = NoteManager.getNotesDir(context)
                                    val noteFile = File(notesDir, fileName)
                                    if (noteFile.canonicalPath.startsWith(notesDir.canonicalPath + File.separator)) {
                                        noteFile.parentFile?.mkdirs()
                                        FileOutputStream(noteFile).use { zip.copyTo(it) }
                                        restoredAny = true
                                    }
                                }
                            }
                        }

                        name == "contacts/contacts.json" -> {
                            if (restoreContacts) {
                                val json = zip.readBytes().toString(Charsets.UTF_8)
                                val ok = runCatching { restoreContactsFromJson(context, json, photos, isPrivate = false) }
                                    .onFailure { android.util.Log.e("BackupManager", "Failed to restore contacts", it) }
                                    .isSuccess
                                if (ok) restoredAny = true
                            }
                        }
                        name == "contacts/private_contacts.json" -> {
                            if (restorePrivateContacts) {
                                val json = zip.readBytes().toString(Charsets.UTF_8)
                                val ok = runCatching { restoreContactsFromJson(context, json, photos, isPrivate = true) }
                                    .onFailure { android.util.Log.e("BackupManager", "Failed to restore contacts", it) }
                                    .isSuccess
                                if (ok) restoredAny = true
                            }
                        }

                        name == "call_logs/call_logs.json" -> {
                            if (restoreCallLogs) {
                                try {
                                    val jsonStr = zip.readBytes().toString(Charsets.UTF_8)
                                    val jsonArray = JSONArray(jsonStr)

                                    val existingKeys = mutableSetOf<String>()
                                    try {
                                        val cursor = context.contentResolver.query(
                                            android.provider.CallLog.Calls.CONTENT_URI,
                                            arrayOf(
                                                android.provider.CallLog.Calls.NUMBER,
                                                android.provider.CallLog.Calls.DATE,
                                                android.provider.CallLog.Calls.TYPE
                                            ),
                                            null,
                                            null,
                                            null
                                        )
                                        cursor?.use { c ->
                                            val numIdx = c.getColumnIndex(android.provider.CallLog.Calls.NUMBER)
                                            val dateIdx = c.getColumnIndex(android.provider.CallLog.Calls.DATE)
                                            val typeIdx = c.getColumnIndex(android.provider.CallLog.Calls.TYPE)
                                            while (c.moveToNext()) {
                                                val num = if (numIdx >= 0) c.getString(numIdx) ?: "" else ""
                                                val date = if (dateIdx >= 0) c.getLong(dateIdx) else 0L
                                                val type = if (typeIdx >= 0) c.getInt(typeIdx) else 0
                                                existingKeys.add("$num|$date|$type")
                                            }
                                        }
                                    } catch (_: Exception) {}

                                    for (i in 0 until jsonArray.length()) {
                                        val obj = jsonArray.getJSONObject(i)
                                        val number = obj.optString("number", "")
                                        val date = obj.optLong("date", 0L)
                                        val type = obj.optInt("type", 1)
                                        val key = "$number|$date|$type"
                                        if (!existingKeys.contains(key)) {
                                            val values = android.content.ContentValues().apply {
                                                put(android.provider.CallLog.Calls.NUMBER, number)
                                                if (obj.has("cachedName")) {
                                                    put(android.provider.CallLog.Calls.CACHED_NAME, obj.getString("cachedName"))
                                                }
                                                put(android.provider.CallLog.Calls.TYPE, type)
                                                put(android.provider.CallLog.Calls.DATE, date)
                                                put(android.provider.CallLog.Calls.DURATION, obj.optLong("duration", 0L))
                                                if (obj.has("phoneAccountId")) {
                                                    put(android.provider.CallLog.Calls.PHONE_ACCOUNT_ID, obj.getString("phoneAccountId"))
                                                }
                                                if (obj.has("isRead")) {
                                                    put(android.provider.CallLog.Calls.IS_READ, obj.getInt("isRead"))
                                                }
                                                if (obj.has("isNew")) {
                                                    put(android.provider.CallLog.Calls.NEW, obj.getInt("isNew"))
                                                }
                                            }
                                            try {
                                                context.contentResolver.insert(android.provider.CallLog.Calls.CONTENT_URI, values)
                                                existingKeys.add(key)
                                            } catch (e: Exception) {
                                                android.util.Log.e("BackupManager", "Failed to insert call log entry", e)
                                            }
                                        }
                                    }
                                    restoredAny = true
                                } catch (e: Exception) {
                                    android.util.Log.e("BackupManager", "Failed to restore call logs", e)
                                }
                            }
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }

            if (backgroundsRestored) remapBackgroundPaths(context)
            restoredAny
        } catch (_: Exception) { false }
    }

    private suspend fun restoreContactsFromJson(
        context: Context,
        json: String,
        photos: Map<String, ByteArray>,
        isPrivate: Boolean,
    ) {
        val contactsList: List<Contact> = backupJson.decodeFromString(json)
        val contactsRepo = runCatching { GlobalContext.get().get<IContactsRepository>() }.getOrNull()
            ?: return

        val existing = contactsRepo.getContactsFull(includePrivate = true, includeHidden = true).toMutableList()

        contactsList.forEach { c ->
            // 1. Restoring Photos: photoUri = "photo:<name>" -> filesDir/<name>
            val localPhotoUri = c.photoUri?.takeIf { it.startsWith("photo:") }?.let { marker ->
                val fileName = marker.removePrefix("photo:")
                val bytes = photos[fileName] ?: return@let null
                val target = File(context.filesDir, "restored_contacts/$fileName")
                target.parentFile?.mkdirs()
                target.writeBytes(bytes)
                target.toUri().toString()
            } ?: c.photoUri

            val contactToSave = c.copy(id = "", photoUri = localPhotoUri, isPrivate = isPrivate)

            // 2. Checking for duplicates (for private accounts—by number)
            val alreadyExists = existing.any { ec ->
                ec.displayName.equals(c.displayName, ignoreCase = true) &&
                        (c.phoneNumbers.isEmpty() ||
                                ec.phoneNumbers.any { ep -> c.phoneNumbers.any { numbersLikelyMatch(ep, it) } })
            }
            if (alreadyExists || (c.displayName.isBlank() && c.phoneNumbers.isEmpty())) return@forEach

            // 3. Save
            contactsRepo.saveContact(contactToSave)
            val newId = c.phoneNumbers.firstNotNullOfOrNull { number ->
                contactsRepo.getContactByNumber(number)?.id
            }
            if (newId != null) {
                contactsRepo.toggleFavorite(newId, c.isFavorite)
                if (c.customRingtone != null) contactsRepo.setCustomRingtone(newId, c.customRingtone)
                contactsRepo.getContactById(newId)?.let { existing.add(it) }
            } else {
                // Contact without a phone number—search by displayName
                contactsRepo.getContactsFull(includePrivate = true, includeHidden = true)
                    .firstOrNull { it.displayName == c.displayName }?.let { existing.add(it) }
            }

            // 4. Transferring backgrounds/styles from the old ID to the new one
            if (isPrivate && c.id.startsWith("p")) {
                val newId = c.phoneNumbers.firstNotNullOfOrNull { number ->
                    contactsRepo.getContactByNumber(number)?.id?.takeIf { it.startsWith("p") }
                }
                if (newId != null && newId != c.id) {
                    CallBackgroundStore.carryBlocking(context, c.id, newId, c.phoneNumbers)
                    CallingCardStore.carryBlocking(context, c.id, newId, c.phoneNumbers)
                }
            }
        }
    }

    private fun prefsToJson(prefsData: Map<String, Any>): String {
        val json = JSONObject()
        val meta = JSONObject() // store type hints for ambiguous types
        prefsData.forEach { (key, value) ->
            when (value) {
                is Boolean -> {
                    json.put(key, value)
                    meta.put(key, "boolean")
                }
                is Int -> {
                    json.put(key, value)
                    meta.put(key, "int")
                }
                is Long -> {
                    json.put(key, value)
                    meta.put(key, "long")
                }
                is Float -> {
                    json.put(key, value.toDouble())
                    meta.put(key, "float")
                }
                is String -> {
                    json.put(key, value)
                    meta.put(key, "string")
                }
                is Set<*> -> {
                    val arr = JSONArray()
                    value.forEach { if (it != null) arr.put(it.toString()) }
                    json.put(key, arr)
                    meta.put(key, "string_set")
                }
            }
        }
        val wrapper = JSONObject()
        wrapper.put("data", json)
        wrapper.put("meta", meta)
        return wrapper.toString()
    }

    suspend fun restorePrefs(context: Context, json: String) {
        try {
            val manager = PreferenceManager(context)
            val restoredMap = mutableMapOf<String, Any>()

            // Support both new wrapper format and legacy flat format
            val raw = JSONObject(json)
            val jsonObj = if (raw.has("data")) raw.getJSONObject("data") else raw
            val meta = if (raw.has("meta")) raw.getJSONObject("meta") else JSONObject()

            jsonObj.keys().forEach { key ->
                val value = jsonObj.get(key)
                when (meta.optString(key, "")) {
                    "boolean" -> (value as? Boolean)?.let { restoredMap[key] = it }
                    "int" -> (value as? Number)?.let { restoredMap[key] = it.toInt() }
                    "long" -> (value as? Number)?.let { restoredMap[key] = it.toLong() }
                    "float" -> (value as? Number)?.let { restoredMap[key] = it.toFloat() }
                    "string" -> restoredMap[key] = value.toString()
                    "string_set" -> {
                        val arr = jsonObj.optJSONArray(key)
                        if (arr != null) {
                            val set = mutableSetOf<String>()
                            for (i in 0 until arr.length()) set.add(arr.getString(i))
                            restoredMap[key] = set
                        }
                    }
                    else -> {
                        // Fallback for old backups without metadata
                        when (value) {
                            is Boolean -> restoredMap[key] = value
                            is Int -> restoredMap[key] = value
                            is Long -> restoredMap[key] = value
                            is Double -> restoredMap[key] = value.toFloat()
                            is String -> restoredMap[key] = value
                            is JSONArray -> {
                                val arr = jsonObj.optJSONArray(key)
                                if (arr != null) {
                                    val set = mutableSetOf<String>()
                                    for (i in 0 until arr.length()) set.add(arr.getString(i))
                                    restoredMap[key] = set
                                }
                            }
                            else -> restoredMap[key] = value
                        }
                    }
                }
            }
            manager.restoreAllPreferences(restoredMap)
        } catch (e: Exception) {
            android.util.Log.e("BackupManager", "Failed to restore prefs", e)
        }
    }

    private suspend fun remapBackgroundPaths(context: Context) {
        val manager = PreferenceManager(context)
        val dir = CallBackgroundStore.storageDirForRestore(context) ?: return
        val entries = manager.getAllPreferencesSuspend()
            .filterKeys { it.startsWith(PreferenceManager.CONTACT_BACKGROUND_PREFIX) }
            .mapNotNull { (k, v) -> (v as? String)?.let { k to it } }
        val updates = mutableMapOf<String, String?>()
        entries.forEach { (key, stored) ->
            if (!stored.startsWith("/")) return@forEach
            val file = File(stored)
            if (file.exists()) return@forEach
            val candidate = File(dir, file.name)
            updates[key] = if (candidate.exists() && candidate.length() > 0) candidate.absolutePath else null
        }
        if (updates.isNotEmpty()) manager.updateContactBackgroundEntriesSuspend(updates)
    }
}
