package dev.goodwy.rphone.controller

import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.CallLog
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import dev.goodwy.rphone.modal.`interface`.ICallLogRepository
import dev.goodwy.rphone.modal.data.CallLogEntry
import dev.goodwy.rphone.view.screen.FakeCallInCallScreen
import dev.goodwy.rphone.view.theme.Rill4Theme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class FakeCallActivity : ComponentActivity() {

    companion object {
        const val EXTRA_CALLER_NAME = "extra_caller_name"
        const val EXTRA_CALLER_NUMBER = "extra_caller_number"
        const val EXTRA_LOG_TO_HISTORY = "extra_log_to_history"
    }

    private val callLogRepo: ICallLogRepository by inject()

    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null

    private var callerName: String = "John Doe"
    private var callerNumber: String = "+1 555-019-2834"
    private var logToHistory: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        setupLockScreenFlags()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        callerName = intent.getStringExtra(EXTRA_CALLER_NAME) ?: "John Doe"
        callerNumber = intent.getStringExtra(EXTRA_CALLER_NUMBER) ?: "+1 555-019-2834"
        logToHistory = intent.getBooleanExtra(EXTRA_LOG_TO_HISTORY, true)

        startRingtoneAndVibration()

        setContent {
            Rill4Theme {
                var callState by remember { mutableIntStateOf(0) } // 0 = RINGING, 1 = ACTIVE, 2 = DISCONNECTED
                var durationSeconds by remember { mutableLongStateOf(0L) }
                var isMuted by remember { mutableStateOf(false) }
                var isSpeakerOn by remember { mutableStateOf(false) }
                var isHold by remember { mutableStateOf(false) }

                LaunchedEffect(callState) {
                    if (callState == 1) {
                        stopRingtoneAndVibration()
                        while (true) {
                            delay(1000)
                            if (!isHold) {
                                durationSeconds++
                            }
                        }
                    }
                }

                FakeCallInCallScreen(
                    callerName = callerName,
                    callerNumber = callerNumber,
                    callState = callState,
                    durationSeconds = durationSeconds,
                    isMuted = isMuted,
                    isSpeakerOn = isSpeakerOn,
                    isHold = isHold,
                    onAnswer = {
                        stopRingtoneAndVibration()
                        callState = 1
                    },
                    onDecline = {
                        val wasActive = callState == 1
                        callState = 2
                        stopRingtoneAndVibration()
                        dismissNotification()

                        if (logToHistory) {
                            val callType = if (wasActive) CallLog.Calls.INCOMING_TYPE else CallLog.Calls.MISSED_TYPE
                            lifecycleScope.launch(Dispatchers.IO) {
                                callLogRepo.saveCallLog(
                                    CallLogEntry(
                                        id = 0L,
                                        number = callerNumber,
                                        name = callerName,
                                        type = callType,
                                        date = System.currentTimeMillis(),
                                        duration = if (wasActive) durationSeconds else 0L,
                                        photoUri = null,
                                        contactId = null
                                    )
                                )
                            }
                        }

                        lifecycleScope.launch {
                            delay(500)
                            finishAndRemoveTask()
                        }
                    },
                    onToggleMute = { isMuted = !isMuted },
                    onToggleSpeaker = { isSpeakerOn = !isSpeakerOn },
                    onToggleHold = { isHold = !isHold }
                )
            }
        }
    }

    private fun setupLockScreenFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun startRingtoneAndVibration() {
        try {
            val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ringtone = RingtoneManager.getRingtone(applicationContext, ringtoneUri)
            ringtone?.play()
        } catch (_: Exception) {}

        try {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(VIBRATOR_SERVICE) as Vibrator
            }
            val pattern = longArrayOf(0, 1000, 1000)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(
                    VibrationEffect.createWaveform(pattern, 0),
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .build()
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        } catch (_: Exception) {}
    }

    private fun stopRingtoneAndVibration() {
        try {
            ringtone?.stop()
            ringtone = null
        } catch (_: Exception) {}

        try {
            vibrator?.cancel()
            vibrator = null
        } catch (_: Exception) {}
    }

    private fun dismissNotification() {
        try {
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(FakeCallReceiver.NOTIFICATION_ID)
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        stopRingtoneAndVibration()
        dismissNotification()
        super.onDestroy()
    }
}
