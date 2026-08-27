package dev.goodwy.rphone.controller

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.core.net.toUri

//Empty activation to remove missed call notifications when you press to call or send a message
//https://stackoverflow.com/questions/18261969/clicking-android-notification-actions-does-not-close-notification-drawer?noredirect=1&lq=1
class NotificationActivity : Activity() {

    companion object {
        const val EXTRA_NUMBER = "extra_number"
        const val ACTION_CALL = "action_call"
        const val ACTION_SMS = "action_sms"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val number = intent.getStringExtra(EXTRA_NUMBER) ?: run { finish(); return }

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.cancel(number.hashCode())

        when (intent.action) {
            ACTION_CALL -> {
                Intent(Intent.ACTION_CALL, "tel:$number".toUri()).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(this)
                }
            }
            ACTION_SMS -> {
                Intent(Intent.ACTION_VIEW, "sms:$number".toUri()).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(this)
                }
            }
        }

        finish()
    }
}