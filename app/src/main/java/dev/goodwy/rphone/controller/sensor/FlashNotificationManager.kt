package dev.goodwy.rphone.controller.sensor

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import dev.goodwy.rphone.controller.util.PreferenceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class FlashNotificationManager(
    private val context: Context,
    private val preferenceManager: PreferenceManager
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var flashJob: Job? = null

    private val cameraManager: CameraManager? by lazy {
        context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    }

    private val cameraIdWithFlash: String? by lazy {
        try {
            cameraManager?.cameraIdList?.firstOrNull { id ->
                val characteristics = cameraManager?.getCameraCharacteristics(id)
                characteristics?.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        } catch (_: Exception) {
            null
        }
    }

    @Synchronized
    fun startFlashing() {
        if (flashJob?.isActive == true) return

        val enabled = preferenceManager.getBoolean(PreferenceManager.KEY_FLASH_ON_CALL_ENABLED, false)
        if (!enabled) return

        val mode = preferenceManager.getInt(PreferenceManager.KEY_FLASH_ON_CALL_MODE, 0)
        if (mode == 1) { // Silent or Vibrate only
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val ringerMode = audioManager?.ringerMode ?: AudioManager.RINGER_MODE_NORMAL
            if (ringerMode == AudioManager.RINGER_MODE_NORMAL) {
                return
            }
        }

        val cameraId = cameraIdWithFlash ?: return

        flashJob = scope.launch {
            try {
                while (isActive) {
                    setTorch(cameraId, true)
                    delay(250.toLong())
                    if (!isActive) break
                    setTorch(cameraId, false)
                    delay(250.toLong())
                }
            } finally {
                setTorch(cameraId, false)
            }
        }
    }

    @Synchronized
    fun stopFlashing() {
        flashJob?.cancel()
        flashJob = null
        val cameraId = cameraIdWithFlash ?: return
        scope.launch {
            setTorch(cameraId, false)
        }
    }

    private fun setTorch(cameraId: String, enabled: Boolean) {
        try {
            cameraManager?.setTorchMode(cameraId, enabled)
        } catch (_: Exception) {
            // Camera may be unavailable or in use
        }
    }
}
