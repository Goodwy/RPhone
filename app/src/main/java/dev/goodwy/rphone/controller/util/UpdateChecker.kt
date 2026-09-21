package dev.goodwy.rphone.controller.util

import android.app.DownloadManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import dev.goodwy.rphone.GITHUB_API_RELEASES
import dev.goodwy.rphone.R
import dev.goodwy.rphone.view.theme.customColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import androidx.core.net.toUri

sealed class UpdateDialogState {
    object Idle : UpdateDialogState()
    object Checking : UpdateDialogState()
    object UpToDate : UpdateDialogState()
    data class ConfirmUpdate(val latestVersion: String, val apkUrl: String?) : UpdateDialogState()
    data class Downloading(val latestVersion: String, val apkUrl: String?, val downloadId: Long, val progress: Float = 0f) : UpdateDialogState()
    data class DownloadComplete(val latestVersion: String, val fileUri: Uri) : UpdateDialogState()
    object Error : UpdateDialogState()
}

data class ReleaseInfo(
    val tagName: String,
    val apkUrl: String?
)

suspend fun fetchLatestRelease(apiUrl: String): ReleaseInfo? = withContext(Dispatchers.IO) {
    try {
        val connection = URL(apiUrl).openConnection() as HttpURLConnection
        connection.apply {
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.github+json")
            connectTimeout = 10_000
            readTimeout = 10_000
        }
        if (connection.responseCode != 200) return@withContext null
        val body = connection.inputStream.bufferedReader().readText()
        val json = JSONObject(body)
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
        ReleaseInfo(tagName = tag.trimStart('v', 'V'), apkUrl = apkUrl)
    } catch (_: Exception) { null }
}

suspend fun performUpdateCheck(appVersion: String, onStateChange: (UpdateDialogState) -> Unit) {
    onStateChange(UpdateDialogState.Checking)
    val release = fetchLatestRelease(GITHUB_API_RELEASES)
    if (release == null) {
        onStateChange(UpdateDialogState.Error)
    } else {
        if (isNewerVersion(release.tagName, appVersion)) {
            onStateChange(UpdateDialogState.ConfirmUpdate(release.tagName, release.apkUrl))
        } else {
            onStateChange(UpdateDialogState.UpToDate)
        }
    }
}

fun isNewerVersion(latest: String, current: String): Boolean {
    fun parts(v: String) = v.split(".").mapNotNull { it.toIntOrNull() }
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
    return try {
        val file = getApkDestinationFile()
        if (file.exists()) file.delete()

        val request = DownloadManager.Request(apkUrl.toUri()).apply {
            setTitle("Rill Phone Update")
            setDescription("Downloading latest version…")
            // Show during download only — no "completed" notification (we launch installer directly)
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, APK_FILE_NAME)
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
        }

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        dm.enqueue(request)
    } catch (_: Exception) { null }
}

/**
 * Trigger the PackageInstaller UI immediately and delete the APK only on a successful install.
 */
fun installApkAndScheduleDelete(context: Context, file: File) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val packageInstaller = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            val sessionId = packageInstaller.createSession(params)
            val session = packageInstaller.openSession(sessionId)

            FileInputStream(file).use { fis ->
                session.openWrite("package", 0, file.length()).use { os ->
                    fis.copyTo(os)
                    session.fsync(os)
                }
            }

            val installResultAction = "${context.packageName}.INSTALL_RESULT"

            val handler = Handler(Looper.getMainLooper())
            var receiverUnregistered = false

            // Delete APK only on STATUS_SUCCESS and ensure receiver is always unregistered
            val resultReceiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    val status = intent?.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)
                    if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                        val confirmIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(Intent.EXTRA_INTENT)
                        }
                        if (confirmIntent != null) {
                            confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            try { context.startActivity(confirmIntent) } catch (_: Exception) {}
                        }
                        return
                    }

                    if (!receiverUnregistered) {
                        receiverUnregistered = true
                        try { context.unregisterReceiver(this) } catch (_: Exception) {}
                    }
                    if (status == PackageInstaller.STATUS_SUCCESS) {
                        try { file.delete() } catch (_: Exception) {}
                    }
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(resultReceiver, IntentFilter(installResultAction), Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                context.registerReceiver(resultReceiver, IntentFilter(installResultAction))
            }

            // Fallback unregister timeout after 5 minutes to prevent memory leaks if user abandons installation
            handler.postDelayed({
                if (!receiverUnregistered) {
                    receiverUnregistered = true
                    try { context.unregisterReceiver(resultReceiver) } catch (_: Exception) {}
                }
            }, 5 * 60 * 1000L)

            val intent = Intent(installResultAction)
            val pi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.getBroadcast(context, sessionId, intent, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            } else {
                PendingIntent.getBroadcast(context, sessionId, intent, PendingIntent.FLAG_UPDATE_CURRENT)
            }

            session.commit(pi.intentSender)
        } else {
            installApkLegacy(context, file)
        }
    } catch (e: Exception) {
        try { installApkLegacy(context, file) } catch (_: Exception) {
            context.toast("Install failed: ${e.message}", Toast.LENGTH_LONG)
        }
    }
}

private fun installApkLegacy(context: Context, file: File) {
    val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    } else {
        Uri.fromFile(file)
    }
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
    }
    context.startActivity(intent)
    // Best-effort delete after 90s for legacy path
    Thread { Thread.sleep(90_000); try { file.delete() } catch (_: Exception) {} }.start()
}

// Keep old name for call-site compatibility
fun downloadAndInstallApk(context: Context, apkUrl: String) {
    enqueueApkDownload(context, apkUrl)
}

fun installApk(context: Context, file: File) = installApkAndScheduleDelete(context, file)

@Composable
fun UpdateDialogs(
    updateDialogState: UpdateDialogState,
    onStateChange: (UpdateDialogState) -> Unit,
    appVersion: String
) {
    val context = LocalContext.current
    when (val state = updateDialogState) {
        is UpdateDialogState.Checking -> Dialog(onDismissRequest = {}) {
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.checking_for_updates), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }

        is UpdateDialogState.UpToDate -> AlertDialog(
            onDismissRequest = { onStateChange(UpdateDialogState.Idle) },
            icon = {
                Icon(
                    Icons.Rounded.CheckCircle,
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(72.dp)
                )
            },
            title = { Text(stringResource(R.string.up_to_date), modifier = Modifier.wrapContentWidth(), textAlign = TextAlign.Center) },
            text = { Text(stringResource(R.string.up_to_date_subtitle, appVersion)) },
            confirmButton = { TextButton(onClick = { onStateChange(UpdateDialogState.Idle) }) { Text(stringResource(R.string.ok)) } }
        )

        is UpdateDialogState.ConfirmUpdate -> AlertDialog(
            onDismissRequest = { onStateChange(UpdateDialogState.Idle) },
            icon = { Icon(Icons.Rounded.SystemUpdate, null, tint = MaterialTheme.colorScheme.customColors.colorDarkBlue) },
            title = { Text(stringResource(R.string.update_available)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.update_available_subtitle, state.latestVersion))
                    Text(stringResource(R.string.update_available_description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                Button(onClick = {
                    val url = state.apkUrl
                    if (url != null) {
                        val downloadId = enqueueApkDownload(context, url)
                        if (downloadId != null) {
                            onStateChange(UpdateDialogState.Downloading(state.latestVersion, url, downloadId, 0f))
                        } else {
                            onStateChange(UpdateDialogState.Error)
                        }
                    } else {
                        onStateChange(UpdateDialogState.Error)
                    }
                }) { Text(stringResource(R.string.download)) }
            },
            dismissButton = {
                TextButton(onClick = { onStateChange(UpdateDialogState.Idle) }) { Text(stringResource(R.string.not_now)) }
            }
        )

        is UpdateDialogState.Downloading -> {
            LaunchedEffect(state.downloadId) {
                val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                while (true) {
                    delay(300.milliseconds)
                    val query = DownloadManager.Query().setFilterById(state.downloadId)
                    val cursor = dm.query(query)
                    if (!cursor.moveToFirst()) { cursor.close(); break }

                    val dmStatus = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    val downloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    val localUriString = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
                    cursor.close()

                    when (dmStatus) {
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            val fileUri = localUriString?.toUri() ?: Uri.fromFile(getApkDestinationFile())
                            onStateChange(UpdateDialogState.DownloadComplete(state.latestVersion, fileUri))
                            break
                        }
                        DownloadManager.STATUS_FAILED -> {
                            onStateChange(UpdateDialogState.Error)
                            break
                        }
                        else -> {
                            val progress = if (total > 0L) (downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
                            onStateChange(state.copy(progress = progress))
                        }
                    }
                }
            }

            Dialog(onDismissRequest = {}) {
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Icon(Icons.Default.SystemUpdate, null, tint = MaterialTheme.colorScheme.customColors.colorDarkBlue, modifier = Modifier.size(36.dp))
                        Text(stringResource(R.string.downloading_update), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("v${state.latestVersion}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                            LinearProgressIndicator(
                                progress = { state.progress },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${(state.progress * 100).roundToInt()}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(stringResource(R.string.please_wait), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }

        is UpdateDialogState.Error -> AlertDialog(
            onDismissRequest = { onStateChange(UpdateDialogState.Idle) },
            icon = { Icon(Icons.Rounded.Error, null, tint = MaterialTheme.colorScheme.customColors.colorRed) },
            title = { Text(stringResource(R.string.check_failed)) },
            text = { Text(stringResource(R.string.check_failed_subtitle)) },
            confirmButton = { TextButton(onClick = { onStateChange(UpdateDialogState.Idle) }) { Text(stringResource(R.string.ok)) } }
        )

        is UpdateDialogState.DownloadComplete -> {
            AlertDialog(
                onDismissRequest = { onStateChange(UpdateDialogState.Idle) },
                icon = {
                    Icon(
                        Icons.Rounded.CheckCircle,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(72.dp)
                    )
                },
                title = { Text(stringResource(R.string.download_complete)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.download_complete_subtitle, state.latestVersion))
                        Text(
                            text = stringResource(R.string.file_saved_to_downloads_folder),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    val failedText = stringResource(R.string.open_downloads_folder_manually)
                    Button(onClick = {
                        // We're trying to open the file (this will launch the system installer)
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(state.fileUri, "application/vnd.android.package-archive")
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                        }
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            // If you can't open the file, open the "Downloads" folder
                            val downloadsIntent = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            try {
                                context.startActivity(downloadsIntent)
                            } catch (_: Exception) {
                                context.toast(failedText)
                            }
                        }
                        onStateChange(UpdateDialogState.Idle)
                    }) {
                        Text(stringResource(R.string.open))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { onStateChange(UpdateDialogState.Idle) }) {
                        Text(stringResource(R.string.close))
                    }
                }
            )
        }

        else -> {}
    }
}
