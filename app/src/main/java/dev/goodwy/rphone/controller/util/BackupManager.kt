package dev.goodwy.rphone.controller.util

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object BackupManager {

    private const val PREFS_NAME = "rill_prefs"
    private const val BACKUP_DIR = "RPhone"

    fun getBackupDir(context: Context): File {
        val dir = File(context.getExternalFilesDir(null), "Backups")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun createBackup(context: Context): File? {
        return try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val backupFile = File(getBackupDir(context), "RPhone_Backup_$timestamp.rphone")

            ZipOutputStream(FileOutputStream(backupFile)).use { zip ->
                // 1. Backup Preferences (DataStore via PreferenceManager)
                val manager = PreferenceManager(context)
                val prefsJson = prefsToJson(manager.getAllPreferences())
                zip.putNextEntry(ZipEntry("prefs.json"))
                zip.write(prefsJson.toByteArray(Charsets.UTF_8))
                zip.closeEntry()

                // 2. Backup notes
                val notesDir = NoteManager.getNotesDir(context)
                notesDir.listFiles()?.filter { it.extension == "txt" }?.forEach { noteFile ->
                    zip.putNextEntry(ZipEntry("notes/${noteFile.name}"))
                    FileInputStream(noteFile).use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
            backupFile
        } catch (_: Exception) { null }
    }

    fun restoreBackup(context: Context, backupFile: File): Boolean {
        return try {
            ZipInputStream(FileInputStream(backupFile)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    when {
                        entry.name == "prefs.json" -> {
                            val json = zip.readBytes().toString(Charsets.UTF_8)
                            restorePrefs(context, json)
                        }
                        entry.name.startsWith("notes/") -> {
                            val fileName = entry.name.removePrefix("notes/")
                            if (fileName.isNotEmpty()) {
                                val noteFile = File(NoteManager.getNotesDir(context), fileName)
                                noteFile.parentFile?.mkdirs()
                                FileOutputStream(noteFile).use { zip.copyTo(it) }
                            }
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
            true
        } catch (_: Exception) { false }
    }

    private fun prefsToJson(prefsData: Map<String, Any>): String {
        val json = JSONObject()
        val meta = JSONObject() // store type hints for ambiguous types
        prefsData.forEach { (key, value) ->
            when (value) {
                is Boolean -> json.put(key, value)
                is Int -> json.put(key, value)
                is Long -> json.put(key, value)
                is Float -> {
                    // Store float as double; mark in meta so restore knows it's a float
                    json.put(key, value.toDouble())
                    meta.put(key, "float")
                }
                is String -> json.put(key, value)
            }
        }
        val wrapper = JSONObject()
        wrapper.put("data", json)
        wrapper.put("meta", meta)
        return wrapper.toString()
    }

    private fun restorePrefs(context: Context, json: String) {
        try {
            val manager = PreferenceManager(context)
            val restoredMap = mutableMapOf<String, Any>()

            // Support both new wrapper format and legacy flat format
            val raw = JSONObject(json)
            val jsonObj = if (raw.has("data")) raw.getJSONObject("data") else raw
            val meta = if (raw.has("meta")) raw.getJSONObject("meta") else JSONObject()

            jsonObj.keys().forEach { key ->
                val value = jsonObj.get(key)
                when {
                    meta.optString(key) == "float" -> restoredMap[key] = (value as? Double)?.toFloat() ?: value
                    value is Double -> restoredMap[key] = value.toFloat() // DataStore prefers Float
                    else -> restoredMap[key] = value
                }
            }
            manager.restoreAllPreferences(restoredMap)
        } catch (_: Exception) {}
    }

    fun listBackups(context: Context): List<File> =
        getBackupDir(context).listFiles()
            ?.filter { it.extension == "rphone" }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
}
