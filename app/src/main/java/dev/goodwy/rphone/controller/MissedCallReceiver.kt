package dev.goodwy.rphone.controller

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telecom.TelecomManager

/**
 * Receiver for missed call notifications from the system.
 * Registering this receiver for [TelecomManager.ACTION_SHOW_MISSED_CALLS_NOTIFICATION]
 * tells the system that the default dialer will handle showing the missed call notification,
 * preventing duplicate notifications from the system.
 */
class MissedCallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == TelecomManager.ACTION_SHOW_MISSED_CALLS_NOTIFICATION) {
            // The notification is currently handled in CallService.handleDisconnect 
            // to leverage existing Call object context (like account handles/SIM labels).
            // This receiver acts as a signal to the system to suppress its own notification.
        }
    }
}
