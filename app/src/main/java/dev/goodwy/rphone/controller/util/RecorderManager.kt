package dev.goodwy.rphone.controller.util

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object RecorderManager {
    private var recorder: MediaRecorder? = null
    private val _isRecording = MutableStateFlow(false)
    val isRecording = _isRecording.asStateFlow()
    private var currentFilePath: String? = null

    fun startRecording(context: Context, phoneNumber: String): Boolean {
        if (_isRecording.value) return false

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "CALL_${phoneNumber}_${timeStamp}.m4a"
        val storageDir = File(context.getExternalFilesDir(null), "recordings")
        if (!storageDir.exists()) {
            storageDir.mkdirs()
        }
        val file = File(storageDir, fileName)
        currentFilePath = file.absolutePath

        recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(currentFilePath)
            try {
                prepare()
                start()
                _isRecording.value = true
            } catch (e: Exception) {
                Log.e("RecorderManager", "Recording failed", e)
                return false
            }
        }
        return true
    }

    fun stopRecording() {
        if (!_isRecording.value) return
        try {
            recorder?.stop()
            recorder?.release()
        } catch (e: Exception) {
            Log.e("RecorderManager", "Stop recording failed", e)
        } finally {
            recorder = null
            _isRecording.value = false
        }
    }

    fun isRecordingActive(): Boolean = _isRecording.value
    
    fun getCurrentRecordingPath(): String? = currentFilePath
}
