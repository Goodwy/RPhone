package dev.goodwy.rphone.controller.util

import android.app.DownloadManager
import android.content.Context
import android.os.Environment
import dev.goodwy.rphone.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import androidx.core.net.toUri

data class ReleaseInfo(
    val tagName: String,
    val apkUrl: String?,
    val releaseNotes: String? = null,
    val publishedAt: String? = null
)

private fun parseReleaseJson(json: JSONObject): ReleaseInfo {
    val tag = json.optString("tag_name", "")
    val assets = json.optJSONArray("assets")
    var apkUrl: String? = null
    if (assets != null) {
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            if (asset.optString("name", "").endsWith(".apk", ignoreCase = true)) {
                apkUrl = asset.optString("browser_download_url")
                break
            }
        }
    }
    val notes = json.optString("body", "").trim().ifBlank { null }
    val publishedAt = json.optString("published_at", "").ifBlank { null }
    return ReleaseInfo(
        tagName = tag.trimStart('v', 'V'),
        apkUrl = apkUrl,
        releaseNotes = notes,
        publishedAt = publishedAt
    )
}

suspend fun fetchLatestRelease(apiUrl: String): ReleaseInfo? = withContext(Dispatchers.IO) {
    var connection: HttpURLConnection? = null
    try {
        connection = URL(apiUrl).openConnection() as HttpURLConnection
        connection.apply {
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.github+json")
            connectTimeout = 10_000
            readTimeout = 10_000
        }
        if (connection.responseCode != 200) return@withContext null
        val body = connection.inputStream.bufferedReader().readText()
        parseReleaseJson(JSONObject(body))
    } catch (_: Exception) { null } finally {
        connection?.disconnect()
    }
}

/**
 * Fetches the release whose tag matches [version] (with or without a leading "v")
 * from the repo's full releases list. Used to show release notes for the
 * currently-installed version, so the user can compare it against the latest.
 */
suspend fun fetchReleaseForVersion(apiListUrl: String, version: String): ReleaseInfo? = withContext(Dispatchers.IO) {
    var connection: HttpURLConnection? = null
    try {
        connection = URL(apiListUrl).openConnection() as HttpURLConnection
        connection.apply {
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.github+json")
            connectTimeout = 10_000
            readTimeout = 10_000
        }
        if (connection.responseCode != 200) return@withContext null
        val body = connection.inputStream.bufferedReader().readText()
        val array = org.json.JSONArray(body)
        val target = version.trimStart('v', 'V')
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val tag = obj.optString("tag_name", "").trimStart('v', 'V')
            if (tag == target) return@withContext parseReleaseJson(obj)
        }
        null
    } catch (_: Exception) { null } finally {
        connection?.disconnect()
    }
}

fun isNewerVersion(latest: String, current: String): Boolean {
    fun parts(v: String) = v.split(".").mapNotNull { part ->
        val match = Regex("^\\d+").find(part)
        match?.value?.toIntOrNull()
    }
    val l = parts(latest); val c = parts(current)
    val len = maxOf(l.size, c.size)
    for (i in 0 until len) {
        val lp = l.getOrElse(i) { 0 }; val cp = c.getOrElse(i) { 0 }
        if (lp > cp) return true; if (lp < cp) return false
    }
    return false
}

private const val APK_FILE_NAME = "RPhone_update.apk"

/** Public Downloads folder — visible in Files/Downloads app. */
fun getApkDestinationFile(): File =
    File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), APK_FILE_NAME)

/**
 * Enqueue an APK download to the public Downloads folder.
 * Returns the DownloadManager download ID, or null on failure.
 * Progress should be polled via DownloadManager.Query from the caller.
 */
fun enqueueApkDownload(context: Context, apkUrl: String): Long? {
    val appContext = context.applicationContext
    return try {
        val file = getApkDestinationFile()
        if (file.exists()) file.delete()

        val request = DownloadManager.Request(apkUrl.toUri()).apply {
            setTitle(APK_FILE_NAME)
            setDescription(appContext.getString(R.string.downloading_update))
            // Show during download only — no "completed" notification (we launch installer directly)
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, APK_FILE_NAME)
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
        }

        val dm = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        dm.enqueue(request)
    } catch (_: Exception) { null }
}
