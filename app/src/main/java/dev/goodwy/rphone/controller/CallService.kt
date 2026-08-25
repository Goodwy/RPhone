package dev.goodwy.rphone.controller

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Person
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.Icon
import android.os.Build
import android.provider.BlockedNumberContract
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.DisconnectCause
import android.telecom.InCallService
import android.telecom.TelecomManager
import android.telecom.VideoProfile
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.net.toUri
import dev.goodwy.rphone.MainActivity
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.data.manager.CallStateManager
import dev.goodwy.rphone.modal.`interface`.IContactsRepository
import dev.goodwy.rphone.view.screen.BiometricCallActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import kotlin.getValue

data class CallSession(
    val call: Call,
    val state: Int,
    val updateTime: Long = System.currentTimeMillis(),
    val connectTimeMillis: Long = 0L
)

class CallService : InCallService() {

    private val contactsRepository: IContactsRepository by inject()
    private val preferenceManager: PreferenceManager by inject()
    private val callStateManager: CallStateManager by inject()
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var redialCount = 0
    private val callStartTimes = mutableMapOf<Call, Long>()

    private fun getContactBitmap(photoUri: String?): Bitmap? {
        if (photoUri == null) return null
        return try {
            val uri = photoUri.toUri()
            val inputStream = contentResolver.openInputStream(uri)
            BitmapFactory.decodeStream(inputStream)
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val CHANNEL_ID = "call_channel"
        private const val INCOMING_CHANNEL_ID = "incoming_call_channel_v3"
        private const val FULLSCREEN_INCOMING_CHANNEL_ID = "fullscreen_incoming_call_channel_v3"
        private const val MISSED_CHANNEL_ID = "missed_call_channel_v3"
        private const val NOTIFICATION_ID = 101

        private val _currentCallSession = MutableStateFlow<CallSession?>(null)
        val currentCallSession = _currentCallSession.asStateFlow()

        private val _allCalls = MutableStateFlow<List<Call>>(emptyList())
        val allCalls = _allCalls.asStateFlow()

        private val _preferredCall = MutableStateFlow<Call?>(null)

        private val _audioState = MutableStateFlow<CallAudioState?>(null)
        val audioState = _audioState.asStateFlow()

        val isActivityVisible = MutableStateFlow(false)

        private var instance: CallService? = null

        fun setPreferredCall(call: Call) {
            _preferredCall.value = call
            instance?.updateCallState()
        }

        fun mute(muted: Boolean) {
            instance?.setMuted(muted)
        }

        fun toggleMute() {
            val currentMute = _audioState.value?.isMuted ?: false
            mute(!currentMute)
        }

        fun setAudioRoute(route: Int) {
            instance?.setAudioRoute(route)
        }

        fun cycleAudioRoute() {
            val state = _audioState.value ?: return
            val supported = state.supportedRouteMask
            val current = state.route

            val nextRoute = when (current) {
                CallAudioState.ROUTE_EARPIECE -> {
                    if ((supported and CallAudioState.ROUTE_BLUETOOTH) != 0) CallAudioState.ROUTE_BLUETOOTH
                    else if ((supported and CallAudioState.ROUTE_SPEAKER) != 0) CallAudioState.ROUTE_SPEAKER
                    else current
                }
                CallAudioState.ROUTE_WIRED_HEADSET -> {
                    if ((supported and CallAudioState.ROUTE_SPEAKER) != 0) CallAudioState.ROUTE_SPEAKER
                    else if ((supported and CallAudioState.ROUTE_BLUETOOTH) != 0) CallAudioState.ROUTE_BLUETOOTH
                    else current
                }
                CallAudioState.ROUTE_BLUETOOTH -> {
                    if ((supported and CallAudioState.ROUTE_SPEAKER) != 0) CallAudioState.ROUTE_SPEAKER
                    else if ((supported and CallAudioState.ROUTE_EARPIECE) != 0) CallAudioState.ROUTE_EARPIECE
                    else current
                }
                CallAudioState.ROUTE_SPEAKER -> {
                    if ((supported and CallAudioState.ROUTE_EARPIECE) != 0) CallAudioState.ROUTE_EARPIECE
                    else if ((supported and CallAudioState.ROUTE_WIRED_HEADSET) != 0) CallAudioState.ROUTE_WIRED_HEADSET
                    else if ((supported and CallAudioState.ROUTE_BLUETOOTH) != 0) CallAudioState.ROUTE_BLUETOOTH
                    else current
                }
                else -> if ((supported and CallAudioState.ROUTE_SPEAKER) != 0) CallAudioState.ROUTE_SPEAKER else current
            }

            if (nextRoute != current) {
                instance?.setAudioRoute(nextRoute)
            }
        }

//        fun mergeCalls() {
//            val calls = instance?.calls ?: return
//            if (calls.size >= 2) {
//                val activeCall = calls.find { it.state == Call.STATE_ACTIVE }
//                val heldCall = calls.find { it.state == Call.STATE_HOLDING }
//                if (activeCall != null && heldCall != null) {
//                    activeCall.conference(heldCall)
//                } else if (calls.size >= 2) {
//                    calls[0].conference(calls[1])
//                }
//            }
//        }

        fun answerCall() {
            _currentCallSession.value?.call?.answer(VideoProfile.STATE_AUDIO_ONLY)
        }

        fun answerRingingCall(endActive: Boolean) {
            val calls = instance?.getCalls() ?: return
            val ringing = calls.find { it.state == Call.STATE_RINGING } ?: return
            val others = calls.filter { it != ringing && it.state != Call.STATE_DISCONNECTED }

            others.forEach { other ->
                try {
                    if (endActive) other.disconnect() else if (other.state == Call.STATE_ACTIVE) other.hold()
                } catch (e: Exception) {
                }
            }

            try {
                ringing.answer(VideoProfile.STATE_AUDIO_ONLY)
            } catch (e: Exception) {
            }
        }

        fun declineCall() {
            // If the call hasn't been answered yet, we try to reject it so that it's recorded correctly in the call history
            val call = _currentCallSession.value?.call ?: return
            val isRinging = call.state == Call.STATE_RINGING
            try {
                if (isRinging && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    call.reject(Call.REJECT_REASON_DECLINED)
                } else {
                    call.disconnect()
                }
            } catch (_: Exception) {
                try { call.disconnect() } catch (_: Exception) {}
            }
        }

        fun setMuted(muted: Boolean) { instance?.setMuted(muted) }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        serviceScope.launch {
            isActivityVisible.collect {
                _currentCallSession.value?.call?.let { currentCall ->
                    updateNotification(currentCall)
                }
            }
        }
        serviceScope.launch {
            callStateManager.callerMetadata.collect {
                _currentCallSession.value?.call?.let { currentCall ->
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

                val remaining = getCalls()?.filter { it.state != Call.STATE_DISCONNECTED } ?: emptyList()
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
            if (!isNumberBlocked(number) || preferenceManager.getInt(PreferenceManager.KEY_BLOCK_LOG_VISIBILITY, 0) == 1) {
                showMissedCallNotification(call)
            }
        }
    }

    private fun isNumberBlocked(number: String): Boolean {
        if (number.isEmpty()) return false
        return try {
            BlockedNumberContract.isBlocked(this, number)
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
            showBlockedNotification(number)
        }
    }

    private fun showBlockedNotification(number: String) {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_close_clear_cancel)
            .setContentTitle(getString(R.string.notif_blocked_call_title))
            .setContentText(getString(R.string.notif_blocked_call_text, number))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)

        notificationManager.notify(number.hashCode(), builder.build())
    }

    private fun showMissedCallNotification(call: Call) {
        val details = call.details
        val handle = details.handle
        val number = handle?.schemeSpecificPart ?: ""
        val accountHandle = details.accountHandle

        serviceScope.launch {
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

            val channel = NotificationChannel(
                MISSED_CHANNEL_ID,
                getString(R.string.notif_channel_missed_calls),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                enableVibration(true)
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(channel)

            val contact = if (number.isNotEmpty()) {
                try {
                    contactsRepository.getContactByNumber(number)
                } catch (e: Exception) {
                    null
                }
            } else null

            val contactName = contact?.displayName ?: number.ifEmpty { getString(R.string.label_unknown_number) }
            val contactPhoto = getContactBitmap(contact?.photoUri)

            val telecomManager = getSystemService(TELECOM_SERVICE) as TelecomManager
            val simLabel = accountHandle?.let {
                try {
                    telecomManager.getPhoneAccount(it)?.label?.toString()
                } catch (e: SecurityException) {
                    null
                }
            }

            val intent = Intent(this@CallService, MainActivity::class.java).apply {
                action = "dev.goodwy.rphone.ACTION_VIEW_RECENTS"
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                this@CallService,
                10,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val timeString = android.text.format.DateFormat.getTimeFormat(this@CallService).format(java.util.Date())

            val missedCallText = buildString {
                append(getString(R.string.notif_missed_call_text, contactName, timeString))
                if (simLabel != null) {
                    append(" ")
                    append(getString(R.string.notif_via_sim, simLabel))
                }
            }

            val builder = NotificationCompat.Builder(this@CallService, MISSED_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.sym_call_missed)
                .setContentTitle(getString(R.string.notif_missed_call_title))
                .setContentText(missedCallText)
                .setLargeIcon(contactPhoto)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setColor(Color.RED)

            notificationManager.notify(number.hashCode(), builder.build())
        }
    }

    private fun updateCallState() {
        val calls = calls ?: emptyList()
        _allCalls.value = ArrayList(calls)

        calls.forEach { c ->
            if (c.state == Call.STATE_ACTIVE) {
                val detailsTime = c.details.connectTimeMillis
                if (detailsTime > 0) {
                    callStartTimes[c] = detailsTime
                } else if (!callStartTimes.containsKey(c)) {
                    callStartTimes[c] = System.currentTimeMillis()
                }
            }
        }
        callStartTimes.keys.retainAll(calls.toSet())

        val preferred = _preferredCall.value
        if (preferred != null && (preferred !in calls || preferred.state == Call.STATE_DISCONNECTED)) {
            _preferredCall.value = null
        }

        val activePreferred = if (preferred != null && preferred.state != Call.STATE_DISCONNECTED && preferred.state != Call.STATE_HOLDING) preferred else null

        val priorityCall = calls.find { it.state == Call.STATE_RINGING }
            ?: activePreferred
            ?: calls.find { it.state == Call.STATE_RINGING }
            ?: calls.find { it.state == Call.STATE_DIALING || it.state == Call.STATE_CONNECTING }
            ?: calls.find { it.state == Call.STATE_ACTIVE }
            ?: calls.find { it == preferred }
            ?: calls.find { it.state == Call.STATE_HOLDING }
            ?: calls.firstOrNull { it.state != Call.STATE_DISCONNECTED }

        if (priorityCall != null) {
            val connectTime = callStartTimes[priorityCall] ?: 0L
            _currentCallSession.value = CallSession(priorityCall, priorityCall.state, connectTimeMillis = connectTime)
        } else {
            _currentCallSession.value = null
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
        instance = this
        redialCount = 0
        call.registerCallback(callCallback)

        val number = call.details.handle?.schemeSpecificPart ?.let { android.net.Uri.decode(it) } ?: ""
        val cnam = if (call.details.callerDisplayNamePresentation == TelecomManager.PRESENTATION_ALLOWED) {
            call.details.callerDisplayName
        } else null

        callStateManager.onNewCallReceived(number, cnam)

        // ── USSD / MMI outgoing calls ────────────────────────────────────────
        // Do NOT launch CallActivity for codes like *124# *#06# ##002# *21*N#.
        // com.android.phone owns MMI/USSD processing at the RIL level and shows
        // its own system dialog — just return and let it handle everything.
        val isUssd = call.state != Call.STATE_RINGING && isUssdNumber(number)
        if (isUssd) return
        // ────────────────────────────────────────────────────────────────────

        if (isNumberBlocked(number)) {
            handleBlockedCall(call, number)
            return
        }

        updateCallState()
        updateNotification(call)

        val fullscreenCalls = preferenceManager.getBoolean(PreferenceManager.KEY_ALWAYS_FULLSCREEN_CALLS, false)
        if (call.state != Call.STATE_RINGING || fullscreenCalls) {
            val intent = Intent(this, CallActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            try {
                startActivity(intent)
            } catch (_: Exception) {
            }
        }
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        call.unregisterCallback(callCallback)
        updateCallState()
        if (allCalls.value.isEmpty()) {
            callStateManager.onCallEnded()
        }
        val calls = allCalls.value
        if (calls.isEmpty()) {
            removeForeground()
            cancelNotification()
        } else {
            _currentCallSession.value?.call?.let { updateNotification(it) }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        super.onCallAudioStateChanged(audioState)
        _audioState.value = audioState
        _currentCallSession.value?.call?.let { updateNotification(it) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "ANSWER_CALL" -> {
                val phoneNumber = _currentCallSession.value?.call?.details?.handle?.schemeSpecificPart
                if (preferenceManager.shouldGateCallWithBiometric(phoneNumber)) {
                    launchBiometricCallActivity("ANSWER")
                } else {
                    answerCall()
                    val intent = Intent(this, CallActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                        putExtra("ANSWERED_FROM_NOTIFICATION", true)
                    }
                    startActivity(intent)
                }
            }
            "DECLINE_CALL" -> {
                val phoneNumber = _currentCallSession.value?.call?.details?.handle?.schemeSpecificPart
                if (preferenceManager.shouldGateCallWithBiometric(phoneNumber)) {
                    launchBiometricCallActivity("DECLINE")
                } else {
                    declineCall()
                }
            }
            "TOGGLE_MUTE" -> toggleMute()
            "TOGGLE_SPEAKER" -> cycleAudioRoute()
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

    private fun updateNotification(call: Call) {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val fullscreenCalls = preferenceManager.getBoolean(PreferenceManager.KEY_ALWAYS_FULLSCREEN_CALLS, false)

        val isRinging = call.state == Call.STATE_RINGING
        val channel = if (isRinging) {
            NotificationChannel(
                if (fullscreenCalls) FULLSCREEN_INCOMING_CHANNEL_ID else INCOMING_CHANNEL_ID,
                if (fullscreenCalls) getString(R.string.notif_channel_fullscreen_incoming_calls) else getString(R.string.notif_channel_incoming_calls),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                enableVibration(true)
                setBypassDnd(true)
            }
        } else {
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_outgoing_calls),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                enableVibration(false)
            }
        }
        notificationManager.createNotificationChannel(channel)

        val handle = call.details.handle
        val number = handle?.schemeSpecificPart ?: ""
        val metadata = callStateManager.callerMetadata.value

        val contactName = if (metadata != null && metadata.number == number) {
            metadata.name
        } else {
            // Fallback if metadata isn't ready or matches another call (rare)
            number.ifEmpty { getString(R.string.label_unknown_number) }
        }

        val contactPhoto = getContactBitmap(metadata?.photoUri)

        val telecomManager = getSystemService(TELECOM_SERVICE) as TelecomManager
        val accountHandle = call.details.accountHandle
        val simLabel = accountHandle?.let {
            try {
                telecomManager.getPhoneAccount(it)?.label?.toString()
            } catch (_: SecurityException) { null }
        }

        val fullScreenIntent = Intent(this, CallActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            if (isRinging) call.hashCode() else 0,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val answerIntent = Intent(this, CallService::class.java).apply { action = "ANSWER_CALL" }
        val answerPendingIntent = PendingIntent.getService(this, 1, answerIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val declineIntent = Intent(this, CallService::class.java).apply { action = "DECLINE_CALL" }
        val declinePendingIntent = PendingIntent.getService(this, 2, declineIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val speakerIntent = Intent(this, CallService::class.java).apply { action = "TOGGLE_SPEAKER" }
        val speakerPendingIntent = PendingIntent.getService(this, 4, speakerIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val muteIntent = Intent(this, CallService::class.java).apply { action = "TOGGLE_MUTE" }
        val mutePendingIntent = PendingIntent.getService(this, 5, muteIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val isMuted   = _audioState.value?.isMuted ?: false
        val audioState = _audioState.value
        val audioRoute = audioState?.route ?: CallAudioState.ROUTE_EARPIECE
        val audioLabel = when (audioRoute) {
            CallAudioState.ROUTE_SPEAKER -> this.getString(R.string.audio_route_speaker)
            CallAudioState.ROUTE_BLUETOOTH -> {
                try {
                    audioState?.activeBluetoothDevice?.name ?: getString(R.string.audio_route_bluetooth)
                } catch (_: SecurityException) {
                    getString(R.string.audio_route_bluetooth)
                }
            }
            CallAudioState.ROUTE_WIRED_HEADSET -> getString(R.string.audio_route_headset)
            else -> getString(R.string.audio_route_handset)
        }

        val contentText = buildString {
            if (call.state == Call.STATE_RINGING) append(getString(R.string.call_status_incoming)) else append(getString(R.string.notif_active_call))
            if (!simLabel.isNullOrEmpty()) {
                append(" ")
                append(getString(R.string.notif_via_sim, simLabel))
            }
        }
        val channelId = if (isRinging) {
            if (fullscreenCalls) FULLSCREEN_INCOMING_CHANNEL_ID else INCOMING_CHANNEL_ID
        } else CHANNEL_ID

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // For Android 12 and later, we use the native CallStyle
            val personBuilder = Person.Builder().setName(contactName).setImportant(true)

            if (contactPhoto != null) {
                personBuilder.setIcon(Icon.createWithBitmap(contactPhoto))
            }
            val person = personBuilder.build()

            val notificationColor = ContextCompat.getColor(this, R.color.notification_color)
            val builder = Notification.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_phone)
                .setContentTitle(contactName)
                .setContentText(contentText)
                .setCategory(Notification.CATEGORY_CALL)
                .setContentIntent(fullScreenPendingIntent)
                .setOngoing(true)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOnlyAlertOnce(!isRinging)
                .setUsesChronometer(true)
                .setStyle(
                    if (isRinging) {
                        Notification.CallStyle.forIncomingCall(person, declinePendingIntent, answerPendingIntent)
                    } else {
                        Notification.CallStyle.forOngoingCall(person, declinePendingIntent)
                    }
                )
                .setColorized(true)
                .setColor(notificationColor)
                .addPerson(person)

            if (call.state == Call.STATE_RINGING) {
                builder.setFullScreenIntent(fullScreenPendingIntent, true)
                builder.setUsesChronometer(false)
                builder.setShowWhen(false)
            } else {
                val connectTime = call.details.connectTimeMillis
                if (connectTime > 0) {
                    builder.setWhen(connectTime)
                    builder.setUsesChronometer(true)
                    builder.setShowWhen(true)
                } else {
                    builder.setUsesChronometer(false)
                    builder.setShowWhen(false)
                }
            }

            // Add extra action buttons for ongoing calls
            if (call.state != Call.STATE_RINGING) {
                builder.addAction(
                    Notification.Action.Builder(
                        Icon.createWithResource(this, R.drawable.ic_notif_speaker),
                        audioLabel,
                        speakerPendingIntent
                    ).build()
                )
                builder.addAction(
                    Notification.Action.Builder(
                        Icon.createWithResource(this, if (isMuted) R.drawable.ic_notif_mic_off else R.drawable.ic_notif_mic_on),
                        if (isMuted) this.getString(R.string.unmute) else this.getString(R.string.mute),
                        mutePendingIntent
                    ).build()
                )
//                builder.addAction(
//                    Notification.Action.Builder(
//                        Icon.createWithResource(this, R.drawable.ic_notif_note),
//                        "Notes",
//                        notesPi
//                    ).build()
//                )
            }

            val notification = builder.build()
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL)
        } else {
            val personBuilder = androidx.core.app.Person.Builder()
                .setName(contactName)
                .setImportant(true)

            if (contactPhoto != null) {
                personBuilder.setIcon(IconCompat.createWithBitmap(contactPhoto))
            }
            val person = personBuilder.build()

            val builder = NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_phone)
                .setContentTitle(contactName)
                .setContentText(contentText)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setFullScreenIntent(fullScreenPendingIntent, true)
                .setContentIntent(fullScreenPendingIntent)
                .setOngoing(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(false)
                .setSilent(call.state != Call.STATE_RINGING)
                .setOnlyAlertOnce(!isRinging)
                .setDefaults(if (isRinging) NotificationCompat.DEFAULT_ALL else 0)
                .setStyle(
                    if (isRinging) {
                        NotificationCompat.CallStyle.forIncomingCall(
                            person,
                            declinePendingIntent,
                            answerPendingIntent
                        )
                    } else {
                        NotificationCompat.CallStyle.forOngoingCall(person, declinePendingIntent)
                    }
                )
                .setColorized(false)

            if (call.state == Call.STATE_ACTIVE) {
                val connectTime = call.details.connectTimeMillis
                if (connectTime > 0) {
                    builder.setWhen(connectTime)
                    builder.setUsesChronometer(true)
                    builder.setShowWhen(true)
                } else {
                    builder.setUsesChronometer(false)
                    builder.setShowWhen(false)
                }
            } else {
                builder.setUsesChronometer(false)
                builder.setShowWhen(false)
            }

            if (call.state != Call.STATE_RINGING) {
                builder.addAction(
                    NotificationCompat.Action.Builder(
                        R.drawable.ic_notif_speaker,
                        audioLabel,
                        speakerPendingIntent
                    ).build()
                )
                builder.addAction(
                    NotificationCompat.Action.Builder(
                        if (isMuted) R.drawable.ic_notif_mic_off else R.drawable.ic_notif_mic_on,
                        if (isMuted) "Unmute" else "Mute",
                        mutePendingIntent
                    ).build()
                )
//                builder.addAction(
//                    NotificationCompat.Action.Builder(
//                        R.drawable.ic_notif_note,
//                        "Notes",
//                        notesPi
//                    ).build()
//                )
            }

            val notification = builder.build()
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
            )
        }

        // Start/stop floating bubble based on preference
        if (call.state != Call.STATE_DISCONNECTED && call.state != Call.STATE_DISCONNECTING) {
            maybeStartFloatingCall(contactName, number, metadata?.photoUri)
        }
    }

    private fun cancelNotification() {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) instance = null
        serviceScope.cancel()
    }

    private fun maybeStartFloatingCall(contactName: String, number: String, photoUri: String?) {
        if (!preferenceManager.getBoolean(PreferenceManager.KEY_FLOATING_CALL, false)) return
        if (!android.provider.Settings.canDrawOverlays(this)) return
        FloatingCallService.start(this, contactName, number, photoUri)
    }

    /** Returns true for any MMI / USSD code like *124# *#06# ##002# *21*N# */
    private fun isUssdNumber(number: String): Boolean {
        if (number.isBlank()) return false
        val n = android.net.Uri.decode(number).trim()
        return (n.startsWith("*") || n.startsWith("#")) && n.endsWith("#")
    }
}
