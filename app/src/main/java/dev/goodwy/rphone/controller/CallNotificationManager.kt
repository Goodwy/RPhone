package dev.goodwy.rphone.controller

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Person
import android.content.Context
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
import androidx.core.graphics.drawable.IconCompat
import androidx.core.net.toUri
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.domain.model.CallerMetadata

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

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun buildCallNotification(
        call: Call,
        metadata: CallerMetadata?,
        audioState: CallAudioState?
    ): Notification {
        val fullscreenCalls = preferenceManager.getBoolean(PreferenceManager.KEY_ALWAYS_FULLSCREEN_CALLS, false)
        val isRinging = call.state == Call.STATE_RINGING

        val channel = if (isRinging) {
            NotificationChannel(
                if (fullscreenCalls) FULLSCREEN_INCOMING_CHANNEL_ID else INCOMING_CHANNEL_ID,
                if (fullscreenCalls) context.getString(R.string.notif_channel_fullscreen_incoming_calls) else context.getString(R.string.notif_channel_incoming_calls),
                NotificationManager.IMPORTANCE_HIGH
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

        val handle = call.details.handle
        val number = handle?.schemeSpecificPart ?: ""
        
        val contactName = if (metadata != null && metadata.number == number) {
            metadata.name
        } else {
            number.ifEmpty { context.getString(R.string.label_unknown_number) }
        }

        val contactPhoto = getContactBitmap(metadata?.photoUri)

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
        val channelId = if (isRinging) {
            if (fullscreenCalls) FULLSCREEN_INCOMING_CHANNEL_ID else INCOMING_CHANNEL_ID
        } else CHANNEL_ID

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
                    if (isMuted) "Unmute" else "Mute",
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
            .setSmallIcon(android.R.drawable.ic_menu_close_clear_cancel)
            .setContentTitle(context.getString(R.string.notif_blocked_call_title))
            .setContentText(context.getString(R.string.notif_blocked_call_text, number))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)

        notificationManager.notify(number.hashCode(), builder.build())
    }

    private fun getContactBitmap(photoUri: String?): Bitmap? {
        if (photoUri == null) return null
        return try {
            val uri = photoUri.toUri()
            val inputStream = context.contentResolver.openInputStream(uri)
            BitmapFactory.decodeStream(inputStream)
        } catch (_: Exception) {
            null
        }
    }
}
