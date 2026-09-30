package dev.goodwy.rphone.controller

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import dev.goodwy.rphone.R
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class FakeCallReceiver : BroadcastReceiver(), KoinComponent {

    companion object {
        const val ACTION_TRIGGER_FAKE_CALL = "dev.goodwy.rphone.ACTION_TRIGGER_FAKE_CALL"
        const val EXTRA_CALLER_NAME = "extra_caller_name"
        const val EXTRA_CALLER_NUMBER = "extra_caller_number"
        const val EXTRA_LOG_TO_HISTORY = "extra_log_to_history"

        const val CHANNEL_ID = "fake_call_channel"
        const val NOTIFICATION_ID = 8842
    }

    private val fakeCallManager: FakeCallManager by inject()

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_TRIGGER_FAKE_CALL) {
            fakeCallManager.clearScheduledState()

            val callerName = intent.getStringExtra(EXTRA_CALLER_NAME) ?: "John Doe"
            val callerNumber = intent.getStringExtra(EXTRA_CALLER_NUMBER) ?: "+1 555-019-2834"
            val logToHistory = intent.getBooleanExtra(EXTRA_LOG_TO_HISTORY, true)

            // Prepare activity intent
            val fullScreenIntent = Intent(context, FakeCallActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
                putExtra(FakeCallActivity.EXTRA_CALLER_NAME, callerName)
                putExtra(FakeCallActivity.EXTRA_CALLER_NUMBER, callerNumber)
                putExtra(FakeCallActivity.EXTRA_LOG_TO_HISTORY, logToHistory)
            }

            val fullScreenPendingIntent = PendingIntent.getActivity(
                context,
                0,
                fullScreenIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Post heads-up / full screen notification
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.fake_call_incoming),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifications for incoming fake calls"
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
                notificationManager.createNotificationChannel(channel)
            }

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(callerName)
                .setContentText(callerNumber)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setFullScreenIntent(fullScreenPendingIntent, true)
                .setContentIntent(fullScreenPendingIntent)
                .setAutoCancel(true)
                .setOngoing(true)
                .build()

            try {
                notificationManager.notify(NOTIFICATION_ID, notification)
            } catch (_: Exception) {}

            // Directly launch FakeCallActivity
            try {
                context.startActivity(fullScreenIntent)
            } catch (_: Exception) {}
        }
    }
}
