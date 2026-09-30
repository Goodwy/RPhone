package dev.goodwy.rphone.controller

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import dev.goodwy.rphone.controller.util.PreferenceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeCallManager(
    private val context: Context,
    private val preferenceManager: PreferenceManager
) {
    companion object {
        const val REQUEST_CODE_FAKE_CALL = 7392
    }

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    private val _scheduledTimeMillis = MutableStateFlow<Long?>(null)
    val scheduledTimeMillis: StateFlow<Long?> = _scheduledTimeMillis.asStateFlow()

    fun scheduleFakeCall(
        callerName: String = preferenceManager.getFakeCallName(),
        callerNumber: String = preferenceManager.getFakeCallNumber(),
        delaySeconds: Int = preferenceManager.getFakeCallDelay(),
        logToHistory: Boolean = preferenceManager.getLogFakeCalls()
    ) {
        if (delaySeconds <= 0) {
            triggerFakeCallNow(callerName, callerNumber, logToHistory)
            return
        }

        val triggerTime = System.currentTimeMillis() + (delaySeconds * 1000L)
        _scheduledTimeMillis.value = triggerTime

        val intent = Intent(context, FakeCallReceiver::class.java).apply {
            action = FakeCallReceiver.ACTION_TRIGGER_FAKE_CALL
            putExtra(FakeCallReceiver.EXTRA_CALLER_NAME, callerName)
            putExtra(FakeCallReceiver.EXTRA_CALLER_NUMBER, callerNumber)
            putExtra(FakeCallReceiver.EXTRA_LOG_TO_HISTORY, logToHistory)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_FAKE_CALL,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager?.let { alarm ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            } else {
                alarm.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            }
        }
    }

    fun triggerFakeCallNow(
        callerName: String = preferenceManager.getFakeCallName(),
        callerNumber: String = preferenceManager.getFakeCallNumber(),
        logToHistory: Boolean = preferenceManager.getLogFakeCalls()
    ) {
        _scheduledTimeMillis.value = null
        val intent = Intent(context, FakeCallReceiver::class.java).apply {
            action = FakeCallReceiver.ACTION_TRIGGER_FAKE_CALL
            putExtra(FakeCallReceiver.EXTRA_CALLER_NAME, callerName)
            putExtra(FakeCallReceiver.EXTRA_CALLER_NUMBER, callerNumber)
            putExtra(FakeCallReceiver.EXTRA_LOG_TO_HISTORY, logToHistory)
        }
        context.sendBroadcast(intent)
    }

    fun cancelScheduledFakeCall() {
        _scheduledTimeMillis.value = null
        val intent = Intent(context, FakeCallReceiver::class.java).apply {
            action = FakeCallReceiver.ACTION_TRIGGER_FAKE_CALL
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_FAKE_CALL,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager?.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    fun clearScheduledState() {
        _scheduledTimeMillis.value = null
    }
}
