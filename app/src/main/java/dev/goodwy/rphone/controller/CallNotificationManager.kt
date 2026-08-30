package dev.goodwy.rphone.controller

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Person
import android.content.Context
import android.content.Context.NOTIFICATION_SERVICE
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import android.os.Build
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.TelecomManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.graphics.drawable.IconCompat
import androidx.core.net.toUri
import kotlinx.coroutines.withContext
import dev.goodwy.rphone.MainActivity
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.PreferenceManager

class CallNotificationManager(
    private val context: Context,
    private val preferenceManager: PreferenceManager
) {
    companion object {
        const val CHANNEL_ID = "call_channel"
        const val INCOMING_CHANNEL_ID = "incoming_call_channel_v3"
        const val FULLSCREEN_INCOMING_CHANNEL_ID = "fullscreen_incoming_call_channel_v3"
        const val MISSED_CHANNEL_ID = "missed_call_channel_v3"
        const val NOTIFICATION_ID = 101
    }

    // Repository of active missed call notifications
    private val activeMissedCallIds = mutableSetOf<Int>()
    private val createdChannels = mutableSetOf<String>()

    fun clearAllMissedCallNotifications(context: Context) {
        val notificationManager = context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        // We delete all the IDs that we have saved
        activeMissedCallIds.forEach { id ->
            notificationManager.cancel(id)
        }
        // We’re clearing the list so we don’t delete them again
        activeMissedCallIds.clear()
    }

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun buildCallNotification(
        call: Call,
        contactName: String,
        contactPhoto: Bitmap?,
        audioState: CallAudioState?
    ): Notification {
        val fullscreenCalls = preferenceManager.getBoolean(PreferenceManager.KEY_ALWAYS_FULLSCREEN_CALLS, false)
        val isRinging = call.state == Call.STATE_RINGING
        val channelId = if (isRinging) {
            if (fullscreenCalls) FULLSCREEN_INCOMING_CHANNEL_ID else INCOMING_CHANNEL_ID
        } else CHANNEL_ID

        if (!createdChannels.contains(channelId)) {
            val channel = if (isRinging) {
                NotificationChannel(
                    channelId,
                    if (fullscreenCalls) context.getString(R.string.notif_channel_fullscreen_incoming_calls) else context.getString(R.string.notif_channel_incoming_calls),
                    if (fullscreenCalls) NotificationManager.IMPORTANCE_LOW else NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    enableVibration(true)
                    setBypassDnd(true)
                }
            } else {
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.notif_channel_outgoing_calls),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    enableVibration(false)
                }
            }
            notificationManager.createNotificationChannel(channel)
            createdChannels.add(channelId)
        }

        // Removed contactPhoto = getContactBitmap(photoUri) - handled via parameter

        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        val accountHandle = call.details.accountHandle
        val simLabel = accountHandle?.let {
            try {
                telecomManager.getPhoneAccount(it)?.label?.toString()
            } catch (_: SecurityException) { null }
        }

        val fullScreenIntent = Intent(context, CallActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            if (isRinging) call.hashCode() else 0,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val answerIntent = Intent(context, CallService::class.java).apply { action = "ANSWER_CALL" }
        val answerPendingIntent = PendingIntent.getService(context, 1, answerIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val declineIntent = Intent(context, CallService::class.java).apply { action = "DECLINE_CALL" }
        val declinePendingIntent = PendingIntent.getService(context, 2, declineIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val speakerIntent = Intent(context, CallService::class.java).apply { action = "TOGGLE_SPEAKER" }
        val speakerPendingIntent = PendingIntent.getService(context, 4, speakerIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val muteIntent = Intent(context, CallService::class.java).apply { action = "TOGGLE_MUTE" }
        val mutePendingIntent = PendingIntent.getService(context, 5, muteIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val isMuted = audioState?.isMuted ?: false
        val audioRoute = audioState?.route ?: CallAudioState.ROUTE_EARPIECE
        val audioLabel = when (audioRoute) {
            CallAudioState.ROUTE_SPEAKER -> context.getString(R.string.audio_route_speaker)
            CallAudioState.ROUTE_BLUETOOTH -> {
                try {
                    audioState?.activeBluetoothDevice?.name ?: context.getString(R.string.audio_route_bluetooth)
                } catch (_: SecurityException) {
                    context.getString(R.string.audio_route_bluetooth)
                }
            }
            CallAudioState.ROUTE_WIRED_HEADSET -> context.getString(R.string.audio_route_headset)
            else -> context.getString(R.string.audio_route_handset)
        }

        val contentText = buildString {
            if (call.state == Call.STATE_RINGING) append(context.getString(R.string.call_status_incoming)) else append(context.getString(R.string.notif_active_call))
            if (!simLabel.isNullOrEmpty()) {
                append(" ")
                append(context.getString(R.string.notif_via_sim, simLabel))
            }
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val personBuilder = Person.Builder().setName(contactName).setImportant(true)
            if (contactPhoto != null) {
                personBuilder.setIcon(Icon.createWithBitmap(contactPhoto))
            }
            val person = personBuilder.build()

            val notificationColor = ContextCompat.getColor(context, R.color.notification_color)
            val builder = Notification.Builder(context, channelId)
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

            if (call.state != Call.STATE_RINGING) {
                builder.addAction(
                    Notification.Action.Builder(
                        Icon.createWithResource(context, R.drawable.ic_notif_speaker),
                        audioLabel,
                        speakerPendingIntent
                    ).build()
                )
                builder.addAction(
                    Notification.Action.Builder(
                        Icon.createWithResource(context, if (isMuted) R.drawable.ic_notif_mic_off else R.drawable.ic_notif_mic_on),
                        if (isMuted) context.getString(R.string.unmute) else context.getString(R.string.mute),
                        mutePendingIntent
                    ).build()
                )
            }
            builder.build()
        } else {
            val personBuilder = androidx.core.app.Person.Builder()
                .setName(contactName)
                .setImportant(true)
            if (contactPhoto != null) {
                personBuilder.setIcon(IconCompat.createWithBitmap(contactPhoto))
            }
            val person = personBuilder.build()

            val builder = NotificationCompat.Builder(context, channelId)
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
                    R.drawable.ic_notif_speaker,
                    audioLabel,
                    speakerPendingIntent
                )
                builder.addAction(
                    if (isMuted) R.drawable.ic_notif_mic_off else R.drawable.ic_notif_mic_on,
                    if (isMuted) context.getString(R.string.unmute) else context.getString(R.string.mute),
                    mutePendingIntent
                )
            }
            builder.build()
        }
    }

    fun cancelNotification() {
        notificationManager.cancel(NOTIFICATION_ID)
    }

    fun showBlockedNotification(number: String) {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_close)
            .setContentTitle(context.getString(R.string.notif_blocked_call_title))
            .setContentText(context.getString(R.string.notif_blocked_call_text, number))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)

        notificationManager.notify(number.hashCode(), builder.build())
    }

    suspend fun getContactBitmap(photoUri: String?): Bitmap? = withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (photoUri == null) return@withContext null
        try {
            val uri = photoUri.toUri()
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun showMissedCallNotification(
        call: Call,
        contactName: String,
        photoUri: String?
    ) {
        val channel = NotificationChannel(
            MISSED_CHANNEL_ID,
            context.getString(R.string.notif_channel_missed_calls),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            enableVibration(true)
            setShowBadge(true)
        }
        notificationManager.createNotificationChannel(channel)

        val handle = call.details.handle
        val number = handle?.schemeSpecificPart ?: ""

        val contactPhoto = getContactBitmap(photoUri)
        val notificationId = number.hashCode()
        activeMissedCallIds.add(notificationId)

        val intent = Intent(context, MainActivity::class.java).apply {
            action = "dev.goodwy.rphone.ACTION_VIEW_RECENTS"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(context, 10, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)


//        val telecomManager = getSystemService(TELECOM_SERVICE) as TelecomManager
//        val simLabel = call.details.accountHandle?.let {
//            try { telecomManager.getPhoneAccount(it)?.label?.toString() } catch (e: SecurityException) { null }
//        }
//
//        val timeString = android.text.format.DateFormat.getTimeFormat(this).format(java.util.Date())
//
//        val missedCallText = buildString {
//            append(getString(R.string.notif_missed_call_text, contactName, timeString))
//            if (simLabel != null) {
//                append(" ")
//                append(getString(R.string.notif_via_sim, simLabel))
//            }
//        }

//        val missedCallText = buildString {
//            append(contactName)
//            if (simLabel != null) {
//                append(" ")
//                val sim = getString(R.string.notif_via_sim, simLabel)
//                append("($sim)")
//            }
//        }

        val callActionIntent = Intent(context, NotificationActivity::class.java).apply {
            action = NotificationActivity.ACTION_CALL
            putExtra(NotificationActivity.EXTRA_NUMBER, number)
        }
        val callPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            callActionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val smsActionIntent = Intent(context, NotificationActivity::class.java).apply {
            action = NotificationActivity.ACTION_SMS
            putExtra(NotificationActivity.EXTRA_NUMBER, number)
        }
        val smsPendingIntent = PendingIntent.getActivity(
            context,
            notificationId + 1,
            smsActionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val personBuilder = androidx.core.app.Person.Builder()
            .setName(contactName)
            .setImportant(true)

        if (contactPhoto != null) {
            personBuilder.setIcon(IconCompat.createWithBitmap(contactPhoto))
        }
        val person = personBuilder.build()

        val builder = NotificationCompat.Builder(context,
            MISSED_CHANNEL_ID
        )
            .setSmallIcon(R.drawable.ic_phone_missed)
//            .setContentTitle(getString(R.string.notif_missed_call_title))
//            .setContentText(missedCallText)
//            .setLargeIcon(contactPhoto)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setColor(ContextCompat.getColor(context, R.color.notification_color))
            .setStyle(
                NotificationCompat.MessagingStyle(person)
                    .addMessage(
                        NotificationCompat.MessagingStyle.Message(
                            context.getString(R.string.notif_missed_call_title),
                            System.currentTimeMillis(),
                            person
                        )
                    )
                    .setGroupConversation(false)
            )
            .setColorized(false)
            .addAction(
                NotificationCompat.Action.Builder(
                    R.drawable.ic_widget_call,
                    context.getString(R.string.notif_call_back),
                    callPendingIntent
                ).build()
            )
            .addAction(
                NotificationCompat.Action.Builder(
                    R.drawable.ic_message_filled,
                    context.getString(R.string.message),
                    smsPendingIntent
                ).build()
            )

        notificationManager.notify(notificationId, builder.build())
    }
}