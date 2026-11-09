package dev.goodwy.rphone.controller.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File

/**
 * Small helpers for the four calling-card photo actions.
 *
 * Camera capture uses the documented system camera intent with a FileProvider
 * URI inside the app cache. No CAMERA permission is declared, which is the
 * Android-recommended way to take a photo through another app.
 *
 * Google Photos support uses the Android photo picker. Android 14 added cloud
 * media providers to that picker, so the app only has to check whether Google
 * Photos is installed and selected as the current provider. No private intents
 * and no account access are involved.
 */
object CallingCardPhotoSupport {

    private const val CAPTURE_DIRECTORY = "calling_card_captures"
    private const val STALE_CAPTURE_MS = 24L * 60L * 60L * 1000L
    private const val CLOUD_PROVIDER_ACTION = "android.content.action.CLOUD_MEDIA_PROVIDER"

    private val GOOGLE_PHOTOS_PACKAGES = setOf(
        "com.google.android.apps.photos",
        "com.google.android.apps.photosgo"
    )

    data class CaptureTarget(val uri: Uri, val file: File)

    data class CloudPhotoStatus(
        val cloudPickerSupported: Boolean,
        val googlePhotosInstalled: Boolean,
        val googlePhotosEnabled: Boolean
    )

    enum class CloudPhotoAction {
        OPEN_PICKER,
        OPEN_SETTINGS,
        FALLBACK
    }

    /**
     * Pure decision helper so the fallback rules can be unit tested on the JVM.
     */
    fun cloudPhotoAction(status: CloudPhotoStatus): CloudPhotoAction = when {
        !status.cloudPickerSupported -> CloudPhotoAction.FALLBACK
        status.googlePhotosEnabled -> CloudPhotoAction.OPEN_PICKER
        status.googlePhotosInstalled -> CloudPhotoAction.OPEN_SETTINGS
        else -> CloudPhotoAction.FALLBACK
    }

    fun isCameraAvailable(context: Context): Boolean {
        return try {
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            intent.resolveActivity(context.packageManager) != null
        } catch (e: Exception) {
            false
        }
    }

    fun createCaptureTarget(context: Context): CaptureTarget? {
        return try {
            val directory = File(context.cacheDir, CAPTURE_DIRECTORY)
            if (!directory.exists() && !directory.mkdirs()) return null
            pruneOldCaptures(directory)
            val file = File.createTempFile("capture_", ".jpg", directory)
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )
            CaptureTarget(uri, file)
        } catch (e: Exception) {
            null
        }
    }

    fun discard(target: CaptureTarget?) {
        if (target == null) return
        runCatching { target.file.delete() }
    }

    /**
     * Deletes a pending capture after the camera app reported cancellation or
     * failure. Only files inside the app capture directory are touched.
     */
    fun deleteCaptureUri(context: Context, uriString: String?) {
        if (uriString.isNullOrBlank()) return
        deleteIfCapture(context, uriString)
    }

    /**
     * Removes a draft capture when it is replaced or discarded, so cache files
     * do not pile up. Saved app-owned images are never affected.
     */
    fun deleteIfCapture(context: Context, uriString: String?) {
        if (uriString.isNullOrBlank()) return
        val uri = try {
            Uri.parse(uriString)
        } catch (e: Exception) {
            return
        }
        val file = captureFile(context, uri) ?: return
        runCatching { file.delete() }
    }

    fun isReadable(context: Context, uri: Uri): Boolean {
        return try {
            val descriptor = context.contentResolver.openAssetFileDescriptor(uri, "r")
            if (descriptor != null) {
                descriptor.use { }
                true
            } else {
                context.contentResolver.openInputStream(uri)?.use { true } ?: false
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Camera output is only usable when the FileProvider still points at a
     * non-empty file. This catches a camera app that returned success without
     * writing anything.
     */
    fun isCaptureReadable(context: Context, uriString: String?): Boolean {
        if (uriString.isNullOrBlank()) return false
        val uri = try {
            Uri.parse(uriString)
        } catch (e: Exception) {
            return false
        }
        val file = captureFile(context, uri) ?: return false
        return file.exists() && file.length() > 0L && isReadable(context, uri)
    }

    fun cloudPhotoStatus(context: Context): CloudPhotoStatus {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return CloudPhotoStatus(
                cloudPickerSupported = false,
                googlePhotosInstalled = isPackageInstalled(context),
                googlePhotosEnabled = false
            )
        }

        var googleInstalled = isPackageInstalled(context)
        var googleEnabled = false

        try {
            val providers = context.packageManager.queryIntentContentProviders(
                Intent(CLOUD_PROVIDER_ACTION),
                0
            )
            providers.forEach { resolved ->
                val info = resolved.providerInfo ?: return@forEach
                val authority = info.authority ?: return@forEach
                val enabled = runCatching {
                    MediaStore.isCurrentCloudMediaProviderAuthority(
                        context.contentResolver,
                        authority
                    )
                }.getOrDefault(false)
                if (info.packageName in GOOGLE_PHOTOS_PACKAGES) {
                    googleInstalled = true
                    if (enabled) googleEnabled = true
                }
            }
        } catch (e: Exception) {
            // Package visibility or provider lookup failed; fall back to the
            // installed check above.
        }

        return CloudPhotoStatus(
            cloudPickerSupported = true,
            googlePhotosInstalled = googleInstalled,
            googlePhotosEnabled = googleEnabled
        )
    }

    fun openPhotoPickerSettings(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return false
        return try {
            context.startActivity(Intent(MediaStore.ACTION_PICK_IMAGES_SETTINGS))
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun isPackageInstalled(context: Context): Boolean {
        return GOOGLE_PHOTOS_PACKAGES.any { packageName ->
            try {
                context.packageManager.getPackageInfo(packageName, 0)
                true
            } catch (e: PackageManager.NameNotFoundException) {
                false
            } catch (e: Exception) {
                false
            }
        }
    }

    private fun captureFile(context: Context, uri: Uri): File? {
        if (uri.authority != "${context.packageName}.provider") return null
        val path = uri.path ?: return null
        val prefix = "/cache/$CAPTURE_DIRECTORY/"
        if (!path.startsWith(prefix)) return null
        val name = path.removePrefix(prefix)
        if (name.isEmpty() || name.contains('/')) return null
        return File(File(context.cacheDir, CAPTURE_DIRECTORY), name)
    }

    private fun pruneOldCaptures(directory: File) {
        val cutoff = System.currentTimeMillis() - STALE_CAPTURE_MS
        runCatching {
            directory.listFiles()?.forEach { file ->
                if (file.isFile && file.lastModified() < cutoff) file.delete()
            }
        }
    }
}
