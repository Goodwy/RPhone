package dev.goodwy.rphone.controller.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat

/** Returns true if the device currently has 2 or more call-capable SIMs (dual/multi-SIM). */
fun hasDualSim(context: Context): Boolean {
    return try {
        val hasPhoneState = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        if (!hasPhoneState) return false
        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager ?: return false
        telecomManager.callCapablePhoneAccounts.size >= 2
    } catch (_: Exception) {
        false
    }
}

fun getSystemVoicemailNumber(context: Context): String? {
    val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
        try {
            val accounts = telecomManager.callCapablePhoneAccounts
            val defaultHandle =
                telecomManager.getDefaultOutgoingPhoneAccount(Uri.fromParts("tel", "123", null).scheme)

            val handle = defaultHandle ?: accounts.firstOrNull()
            if (handle != null) {
                val num = telecomManager.getVoiceMailNumber(handle)
                if (!num.isNullOrEmpty()) return num
            }
        } catch (e: SecurityException) {
        } catch (e: Exception) {}

        try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val num = tm.voiceMailNumber
            if (!num.isNullOrEmpty()) return num
        } catch (e: SecurityException) {
        } catch (e: Exception) {}
    }
    return null
}

data class DeviceImeiInfo(
    val imei1: String? = null,
    val imei2: String? = null,
    val meid: String? = null,
    val serial: String? = null
)

@SuppressLint("HardwareIds")
@RequiresPermission("android.permission.READ_PRIVILEGED_PHONE_STATE")
fun getDeviceImeiInfo(context: Context): DeviceImeiInfo {
    val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    var imei1: String? = null
    var imei2: String? = null
    var meid: String? = null

    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
        try {
            imei1 = try { tm.getImei(0) } catch (e: Exception) { null }
            imei2 = try { tm.getImei(1) } catch (e: Exception) { null }
            meid = try { tm.getMeid() } catch (e: Exception) { null }
            if (imei1.isNullOrEmpty()) {
                @Suppress("DEPRECATION")
                imei1 = try { tm.deviceId } catch (e: Exception) { null }
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    return DeviceImeiInfo(imei1 = imei1, imei2 = imei2, meid = meid, serial = null)
}