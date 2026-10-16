package dev.goodwy.rphone.controller.util

import android.app.Activity
import android.content.Context
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter

object ContactNfcHelper {

    fun isNfcAvailable(context: Context): Boolean {
        return try {
            val adapter = NfcAdapter.getDefaultAdapter(context)
            adapter != null
        } catch (_: Exception) {
            false
        }
    }

    fun isNfcEnabled(context: Context): Boolean {
        return try {
            val adapter = NfcAdapter.getDefaultAdapter(context)
            adapter != null && adapter.isEnabled
        } catch (_: Exception) {
            false
        }
    }

    fun createVCardNdefMessage(vCardString: String): NdefMessage? {
        return try {
            val mimeType = "text/x-vcard"
            val record = NdefRecord.createMime(mimeType, vCardString.toByteArray(Charsets.UTF_8))
            NdefMessage(arrayOf(record))
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun pushNdefMessage(activity: Activity, vCardString: String): Boolean {
        return try {
            val adapter = NfcAdapter.getDefaultAdapter(activity)
            if (adapter != null && adapter.isEnabled) {
                val message = createVCardNdefMessage(vCardString)
                if (message != null) {
                    val method = adapter.javaClass.getMethod(
                        "setNdefPushMessage",
                        NdefMessage::class.java,
                        Activity::class.java
                    )
                    method.invoke(adapter, message, activity)
                    return true
                }
            }
            false
        } catch (_: Exception) {
            false
        }
    }

    fun clearNdefMessage(activity: Activity) {
        try {
            val adapter = NfcAdapter.getDefaultAdapter(activity)
            if (adapter != null && adapter.isEnabled) {
                val method = adapter.javaClass.getMethod(
                    "setNdefPushMessage",
                    NdefMessage::class.java,
                    Activity::class.java
                )
                method.invoke(adapter, null, activity)
            }
        } catch (_: Exception) {
        }
    }
}
