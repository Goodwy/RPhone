package dev.goodwy.rphone.controller

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telecom.TelecomManager

class MissedCallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            TelecomManager.ACTION_SHOW_MISSED_CALLS_NOTIFICATION -> {
                // Simply disable the system notification
                // Our notification has already been displayed via CallService
                abortBroadcast()
            }
        }
    }
}