package dev.goodwy.rphone.controller

import android.content.Intent
import android.os.Build
import android.provider.BlockedNumberContract
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.DisconnectCause
import android.telecom.InCallService
import android.telecom.TelecomManager
import androidx.core.net.toUri
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.data.manager.CallStateManager
import dev.goodwy.rphone.modal.`interface`.CallSession
import dev.goodwy.rphone.modal.`interface`.ICallRepository
import dev.goodwy.rphone.modal.repository.CallRepositoryImpl
import dev.goodwy.rphone.view.screen.BiometricCallActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import kotlin.getValue

class CallService : InCallService() {

    private val preferenceManager: PreferenceManager by inject()
    private val callStateManager: CallStateManager by inject()
    private val notificationManager: CallNotificationManager by inject()
    private val callRepository: ICallRepository by inject()

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var redialCount = 0
    private val callStartTimes = mutableMapOf<Call, Long>()
    private var lastFloatingCallMetadata: Triple<String, String, String?>? = null

    override fun onCreate() {
        super.onCreate()
        (callRepository as? CallRepositoryImpl)?.bindService(this)

        serviceScope.launch {
            callRepository.isActivityVisible.collect {
                callRepository.currentCallSession.value?.call?.let { currentCall ->
                    updateNotification(currentCall)
                }
            }
        }
        serviceScope.launch {
            callStateManager.callerMetadataMap.collect {
                callRepository.currentCallSession.value?.call?.let { currentCall ->
                    updateNotification(currentCall)
                }
            }
        }
    }

    private val callCallback = object : Call.Callback() {
        override fun onConnectionEvent(call: Call, event: String, extras: android.os.Bundle?) {
            super.onConnectionEvent(call, event, extras)
            val number = call.details?.handle?.schemeSpecificPart?.let { android.net.Uri.decode(it) } ?: ""
            if (isUssdNumber(number)) {
                val resp = extras?.let { b ->
                    b.getString("ussdResult") ?: b.getString("android.telecom.extra.ussd_message")
                    ?: b.getString("android.telephony.extra.USSD_RESPONSE")
                    ?: b.getString("response") ?: b.getString("result") ?: b.getString("data") ?: b.getString("message")
                }
                if (!resp.isNullOrBlank()) UssdRepository.post(number, resp)
            }
        }

        override fun onStateChanged(call: Call, state: Int) {
            super.onStateChanged(call, state)
            updateCallState()

            if (state == Call.STATE_ACTIVE) {
                redialCount = 0
            }

            if (state == Call.STATE_DISCONNECTED) {
                val cause = call.details.disconnectCause
                handleDisconnect(call, cause)

                val remaining = calls?.filter { it.state != Call.STATE_DISCONNECTED } ?: emptyList()
                if (remaining.isEmpty()) {
                    removeForeground()
                    cancelNotification()
                }
            } else {
                updateNotification(call)
            }
        }

        override fun onDetailsChanged(call: Call, details: Call.Details) {
            super.onDetailsChanged(call, details)
            val number = details.handle?.schemeSpecificPart?.let { android.net.Uri.decode(it) } ?: ""
            val cnam = if (details.callerDisplayNamePresentation == TelecomManager.PRESENTATION_ALLOWED) {
                details.callerDisplayName
            } else null
            callStateManager.onNewCallReceived(number, cnam)
            updateCallState()
            updateNotification(call)
        }
    }

    private fun handleDisconnect(call: Call, cause: DisconnectCause?) {
        val number = call.details.handle?.schemeSpecificPart ?: ""

        // Auto Redial on Busy
        if (cause?.code == DisconnectCause.BUSY &&
            preferenceManager.getBoolean(PreferenceManager.KEY_AUTO_REDIAL_BUSY, false)) {

            val maxAttempts = preferenceManager.getInt(PreferenceManager.KEY_REDIAL_ATTEMPTS, 3)
            val delayMs = preferenceManager.getInt(PreferenceManager.KEY_REDIAL_DELAY, 3000).toLong()

            if (redialCount < maxAttempts) {
                redialCount++
                serviceScope.launch {
                    delay(delayMs)
                    val intent = Intent(Intent.ACTION_CALL, "tel:$number".toUri()).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                }
            }
        }

        // Need to create a Receiver (android.telecom.action.SHOW_MISSED_CALLS_NOTIFICATION) to prevent the system notification from being duplicated
        val wasNeverConnected = call.details.connectTimeMillis == 0L
        val isIncoming = call.details.callDirection == Call.Details.DIRECTION_INCOMING

        if (isIncoming && wasNeverConnected && (cause?.code == DisconnectCause.MISSED || cause?.code == DisconnectCause.REMOTE || cause?.code == DisconnectCause.REJECTED)) {
            serviceScope.launch {
                if (!isNumberBlocked(number) || preferenceManager.getInt(PreferenceManager.KEY_BLOCK_LOG_VISIBILITY, 0) == 1) {
                    val contactName = getContactNameFromCache(number)
                    val photoUri = getContactPhotoFromCache(number)
                    notificationManager.showMissedCallNotification(call, contactName, photoUri)
                }
            }
        }
    }

    private suspend fun isNumberBlocked(number: String): Boolean = withContext(Dispatchers.IO) {
        if (number.isBlank()) return@withContext false
        return@withContext try {
            BlockedNumberContract.isBlocked(this@CallService, number)
        } catch (_: Exception) {
            false
        }
    }

    private fun handleBlockedCall(call: Call, number: String) {
        val method = preferenceManager.getInt(PreferenceManager.KEY_BLOCK_METHOD, 0) // 0: Decline, 1: Silent

        if (method == 0) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                call.reject(Call.REJECT_REASON_DECLINED)
            } else {
                call.disconnect()
            }
        }

        if (preferenceManager.getBoolean(PreferenceManager.KEY_BLOCK_NOTIFICATION, true)) {
            notificationManager.showBlockedNotification(number)
        }
    }

    private fun getContactNameFromCache(number: String): String {
        if (number.isEmpty()) return getString(R.string.label_unknown_number)
        val metadata = callStateManager.callerMetadataMap.value[number]
        return if (metadata != null && metadata.name.isNotEmpty()) {
            metadata.name
        } else {
            number
        }
    }

    private fun getContactPhotoFromCache(number: String): String? {
        val metadata = callStateManager.callerMetadataMap.value[number]
        return metadata?.photoUri
    }

    private fun updateCallState() {
        val callsList = calls ?: emptyList()
        callRepository.updateAllCalls(callsList)

        callsList.forEach { c ->
            if (c.state == Call.STATE_ACTIVE) {
                val detailsTime = c.details.connectTimeMillis
                if (detailsTime > 0) {
                    callStartTimes[c] = detailsTime
                } else if (!callStartTimes.containsKey(c)) {
                    callStartTimes[c] = System.currentTimeMillis()
                }
            }
        }
        callStartTimes.keys.retainAll(callsList.toSet())

        val preferred = callRepository.getPreferredCall()
        if (preferred != null && (preferred !in callsList || preferred.state == Call.STATE_DISCONNECTED)) {
            callRepository.setPreferredCall(null)
        }

        val currentPreferred = callRepository.getPreferredCall()
        val activePreferred = if (currentPreferred != null && currentPreferred.state != Call.STATE_DISCONNECTED && currentPreferred.state != Call.STATE_HOLDING) currentPreferred else null

        val priorityCall = callsList.find { it.state == Call.STATE_RINGING }
            ?: activePreferred
            ?: callsList.find { it.state == Call.STATE_DIALING || it.state == Call.STATE_CONNECTING }
            ?: callsList.find { it.state == Call.STATE_ACTIVE }
            ?: callsList.find { it == preferred }
            ?: callsList.find { it.state == Call.STATE_HOLDING }
            ?: callsList.firstOrNull { it.state != Call.STATE_DISCONNECTED }

        if (priorityCall != null) {
            val connectTime = callStartTimes[priorityCall] ?: 0L
            callRepository.updateCurrentCallSession(CallSession(priorityCall, priorityCall.state, connectTimeMillis = connectTime))
        } else {
            callRepository.updateCurrentCallSession(null)
        }
    }

    private fun updateNotification(call: Call) {
        serviceScope.launch {
            val handle = call.details.handle
            val number = handle?.schemeSpecificPart ?: ""
            val contactName = getContactNameFromCache(number)
            val photoUri = getContactPhotoFromCache(number)
            val contactPhoto = notificationManager.getContactBitmap(photoUri)

            val notification = notificationManager.buildCallNotification(
                call,
                contactName,
                contactPhoto,
                callRepository.audioState.value
            )
            startForeground(
                CallNotificationManager.NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
            )

            // Start/stop floating bubble based on preference
            if (call.state != Call.STATE_DISCONNECTED && call.state != Call.STATE_DISCONNECTING) {
                maybeStartFloatingCall(contactName, number, photoUri)
            }
        }
    }

    private fun removeForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private fun launchBiometricCallActivity(action: String) {
        val intent = Intent(this, BiometricCallActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("NOTIFICATION_PENDING_ACTION", action)
        }
        startActivity(intent)
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        redialCount = 0
        call.registerCallback(callCallback)

        val number = call.details.handle?.schemeSpecificPart ?.let { android.net.Uri.decode(it) } ?: ""
        val cnam = if (call.details.callerDisplayNamePresentation == TelecomManager.PRESENTATION_ALLOWED) {
            call.details.callerDisplayName
        } else null

        callStateManager.onNewCallReceived(number, cnam)

        // ── USSD / MMI outgoing calls ────────────────────────────────────────
        val isUssd = call.state != Call.STATE_RINGING && isUssdNumber(number)
        if (isUssd) return
        // ────────────────────────────────────────────────────────────────────

        serviceScope.launch {
            if (isNumberBlocked(number)) {
                handleBlockedCall(call, number)
                return@launch
            }

            updateCallState()
            updateNotification(call)

            val fullscreenCalls = preferenceManager.getBoolean(PreferenceManager.KEY_ALWAYS_FULLSCREEN_CALLS, false)
            if (call.state != Call.STATE_RINGING || fullscreenCalls) {
                val intent = Intent(this@CallService, CallActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                try {
                    startActivity(intent)
                } catch (_: Exception) {
                }
            }
        }
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        call.unregisterCallback(callCallback)

        // If the call being deleted is the same one for which a floating window was launched,
        // we must clear the cache to allow it to be launched for the next call.
        val number = call.details?.handle?.schemeSpecificPart ?: ""
        val currentMetadata = lastFloatingCallMetadata
        if (currentMetadata != null && currentMetadata.second == number) {
            lastFloatingCallMetadata = null
        }

        updateCallState()
        if (callRepository.allCalls.value.isEmpty()) {
            callStateManager.onCallEnded(number)
        }
        val callsList = callRepository.allCalls.value
        if (callsList.isEmpty()) {
            removeForeground()
            cancelNotification()
        } else {
            callRepository.currentCallSession.value?.call?.let { updateNotification(it) }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        super.onCallAudioStateChanged(audioState)
        callRepository.updateAudioState(audioState)
        callRepository.currentCallSession.value?.call?.let { updateNotification(it) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "ANSWER_CALL" -> {
                val phoneNumber = callRepository.currentCallSession.value?.call?.details?.handle?.schemeSpecificPart
                if (preferenceManager.shouldGateCallWithBiometric(phoneNumber)) {
                    launchBiometricCallActivity("ANSWER")
                } else {
                    callRepository.answerCall()
                    val intent = Intent(this, CallActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                        putExtra("ANSWERED_FROM_NOTIFICATION", true)
                    }
                    startActivity(intent)
                }
            }
            "DECLINE_CALL" -> {
                val phoneNumber = callRepository.currentCallSession.value?.call?.details?.handle?.schemeSpecificPart
                if (preferenceManager.shouldGateCallWithBiometric(phoneNumber)) {
                    launchBiometricCallActivity("DECLINE")
                } else {
                    callRepository.declineCall()
                }
            }
            "TOGGLE_MUTE" -> callRepository.toggleMute()
            "TOGGLE_SPEAKER" -> callRepository.cycleAudioRoute()
            "NOTES_CALL"   -> {
                val name   = intent.getStringExtra("contact_name") ?: "Unknown"
                val number = intent.getStringExtra("phone_number") ?: ""
                if (android.provider.Settings.canDrawOverlays(this)) {
                    FloatingNotesService.start(this, name, number)
                }
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun cancelNotification() {
        notificationManager.cancelNotification()
    }

    override fun onDestroy() {
        super.onDestroy()
        (callRepository as? CallRepositoryImpl)?.unbindService()
        serviceScope.cancel()
    }

    private fun maybeStartFloatingCall(contactName: String, number: String, photoUri: String?) {
        if (!preferenceManager.getBoolean(PreferenceManager.KEY_FLOATING_CALL, false)) return
        if (!android.provider.Settings.canDrawOverlays(this)) return

        val metadata = Triple(contactName, number, photoUri)
        if (lastFloatingCallMetadata == metadata) return
        lastFloatingCallMetadata = metadata

        FloatingCallService.start(this, contactName, number, photoUri)
    }

    /** Returns true for any MMI / USSD code like *124# *#06# ##002# *21*N# */
    private fun isUssdNumber(number: String): Boolean {
        if (number.isBlank()) return false
        val n = android.net.Uri.decode(number).trim()
        return (n.startsWith("*") || n.startsWith("#")) && n.endsWith("#")
    }
}
