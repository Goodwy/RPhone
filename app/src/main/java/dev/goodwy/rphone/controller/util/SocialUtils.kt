package dev.goodwy.rphone.controller.util

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.net.toUri
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import dev.goodwy.rphone.R
import java.util.Locale

// ── WhatsApp ─────────────────────────────────────────────────────────────
private const val MIME_WA_VOICE_CALL          = "vnd.android.cursor.item/vnd.com.whatsapp.voip.call"
private const val MIME_WA_VIDEO_CALL          = "vnd.android.cursor.item/vnd.com.whatsapp.video.call"
private const val MIME_WA_BIZ_VOICE_CALL      = "vnd.android.cursor.item/vnd.com.whatsapp.w4b.voip.call"
private const val MIME_WA_BIZ_VIDEO_CALL      = "vnd.android.cursor.item/vnd.com.whatsapp.w4b.video.call"

// ── Google Meet / Duo (Tachyon) ──────────────────────────────────────────
private const val TACHYON_PKG                 = "com.google.android.apps.tachyon"
private const val TACHYON_CALL_ACTION         = "com.google.android.apps.tachyon.action.CALL"
private const val TACHYON_VIDEO_ACTIVITY      = "com.google.android.apps.tachyon.ContactsVideoActionActivity"
private const val TACHYON_AUDIO_ACTIVITY      = "com.google.android.apps.tachyon.ContactsAudioActionActivity"
private const val MIME_MEET_VIDEO_CALL        = "vnd.android.cursor.item/com.google.android.apps.tachyon.phone"
private const val MIME_MEET_AUDIO_CALL        = "vnd.android.cursor.item/com.google.android.apps.tachyon.phone.audio"

data class SocialAppInfo(
    val id: String,
    val name: String,
    val packageName: String?,
    val iconRes: Int,
    val iconDrawable: Drawable? = null,
    val assetIcon: String? = null,
    val action: (Context, String) -> Unit,
    val videoCallAction: ((Context, String) -> Boolean)? = null,
    val voiceCallAction: ((Context, String) -> Boolean)? = null
)

object SocialUtils {

    /**
     * Formats a raw phone number to international E.164 format (+15551234567).
     * If formatting fails, cleans formatting symbols and preserves leading '+'.
     */
    fun formatInternationalNumber(context: Context, rawNumber: String): String {
        val trimmed = rawNumber.trim()
        if (trimmed.isEmpty()) return ""

        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val countryIso = tm?.simCountryIso?.uppercase()?.ifBlank { null }
            ?: tm?.networkCountryIso?.uppercase()?.ifBlank { null }
            ?: Locale.getDefault().country.uppercase().ifBlank { "US" }

        val e164 = try {
            PhoneNumberUtils.formatNumberToE164(trimmed, countryIso)
        } catch (_: Exception) {
            null
        }

        if (!e164.isNullOrBlank()) {
            return e164
        }

        // If it starts with '+', keep '+' and strip any spaces/dashes/parentheses
        return if (trimmed.startsWith("+")) {
            "+" + trimmed.drop(1).replace(Regex("[^0-9]"), "")
        } else {
            trimmed.replace(Regex("[^0-9]"), "")
        }
    }

    /**
     * Extracts only digits without '+' or punctuation for APIs like WhatsApp / Viber.
     */
    fun cleanDigitsOnly(context: Context, rawNumber: String): String {
        val intl = formatInternationalNumber(context, rawNumber)
        return intl.replace(Regex("[^0-9]"), "")
    }

    fun openWhatsAppChat(context: Context, number: String) {
        val digits = cleanDigitsOnly(context, number)
        if (digits.isBlank()) {
            context.toast(context.getString(R.string.no_valid_app_found))
            return
        }
        val url = "https://api.whatsapp.com/send?phone=$digits"
        val intent = Intent(Intent.ACTION_VIEW, url.toUri()).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            when {
                context.isPackageInstalled("com.whatsapp") -> setPackage("com.whatsapp")
                context.isPackageInstalled("com.whatsapp.w4b") -> setPackage("com.whatsapp.w4b")
            }
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                intent.setPackage(null)
                context.startActivity(intent)
            } catch (_: Exception) {
                context.toast(context.getString(R.string.no_valid_app_found))
            }
        }
    }

    fun openTelegramChat(context: Context, number: String) {
        val digits = cleanDigitsOnly(context, number)
        if (digits.isBlank()) {
            context.toast(context.getString(R.string.no_valid_app_found))
            return
        }
        val url = "https://t.me/+$digits"
        val intent = Intent(Intent.ACTION_VIEW, url.toUri()).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            when {
                context.isPackageInstalled("org.telegram.messenger") -> setPackage("org.telegram.messenger")
                context.isPackageInstalled("org.thunderdog.challegram") -> setPackage("org.thunderdog.challegram")
                context.isPackageInstalled("org.telegram.plus") -> setPackage("org.telegram.plus")
            }
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                intent.setPackage(null)
                context.startActivity(intent)
            } catch (_: Exception) {
                context.toast(context.getString(R.string.no_valid_app_found))
            }
        }
    }

    fun openSignalChat(context: Context, number: String) {
        val digits = cleanDigitsOnly(context, number)
        if (digits.isBlank()) {
            context.toast(context.getString(R.string.no_valid_app_found))
            return
        }
        val url = "sgnl://signal.me/#p/+$digits"
        val intent = Intent(Intent.ACTION_VIEW, url.toUri()).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            if (context.isPackageInstalled("org.thoughtcrime.securesms")) {
                setPackage("org.thoughtcrime.securesms")
            }
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val fallbackIntent = Intent(Intent.ACTION_VIEW,
                    "https://signal.me/#p/+$digits".toUri()).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(fallbackIntent)
            } catch (_: Exception) {
                context.toast(context.getString(R.string.no_valid_app_found))
            }
        }
    }

    fun openViberChat(context: Context, number: String) {
        val digits = cleanDigitsOnly(context, number)
        if (digits.isBlank()) {
            context.toast(context.getString(R.string.no_valid_app_found))
            return
        }
        val url = "viber://chat?number=%2B$digits"
        val intent = Intent(Intent.ACTION_VIEW, url.toUri()).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            if (context.isPackageInstalled("com.viber.voip")) {
                setPackage("com.viber.voip")
            }
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                intent.setPackage(null)
                context.startActivity(intent)
            } catch (_: Exception) {
                context.toast(context.getString(R.string.no_valid_app_found))
            }
        }
    }

    fun openMeet(context: Context, number: String) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage("com.google.android.apps.tachyon")
        if (launchIntent != null) {
            launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            try {
                context.startActivity(launchIntent)
            } catch (_: Exception) {
                context.toast(context.getString(R.string.no_valid_app_found))
            }
        }
    }

    /**
     * Queries and returns ONLY the supported social/messaging apps that are
     * actually installed on the user's device.
     */
    fun getInstalledSocialApps(context: Context): List<SocialAppInfo> {
        val pm = context.packageManager
        val list = mutableListOf<SocialAppInfo>()

        // 1. WhatsApp or WhatsApp Business
        val waPkg = when {
            context.isPackageInstalled("com.whatsapp") -> "com.whatsapp"
            context.isPackageInstalled("com.whatsapp.w4b") -> "com.whatsapp.w4b"
            else -> null
        }
        if (waPkg != null) {
            val appInfo = runCatching { pm.getApplicationInfo(waPkg, 0) }.getOrNull()
            val label = appInfo?.let { pm.getApplicationLabel(it).toString() } ?: "WhatsApp"
            val icon = appInfo?.let { pm.getApplicationIcon(it) }
            list.add(
                SocialAppInfo(
                    id = "whatsapp",
                    name = label,
                    packageName = waPkg,
                    iconRes = R.drawable.ic_whatsapp,
                    iconDrawable = icon,
                    assetIcon = "file:///android_asset/icons/whatsapp.png",
                    action = { ctx, num -> openWhatsAppChat(ctx, num) },
                    videoCallAction = { ctx, num -> startWhatsAppVideoCall(ctx, num) },
                    voiceCallAction = { ctx, num -> startWhatsAppVoiceCall(ctx, num) }
                )
            )
        }

        // 2. Telegram or Telegram X or Plus Messenger
        val tgPkg = when {
            context.isPackageInstalled("org.telegram.messenger") -> "org.telegram.messenger"
            context.isPackageInstalled("org.thunderdog.challegram") -> "org.thunderdog.challegram"
            context.isPackageInstalled("org.telegram.plus") -> "org.telegram.plus"
            else -> null
        }
        if (tgPkg != null) {
            val appInfo = runCatching { pm.getApplicationInfo(tgPkg, 0) }.getOrNull()
            val label = appInfo?.let { pm.getApplicationLabel(it).toString() } ?: "Telegram"
            val icon = appInfo?.let { pm.getApplicationIcon(it) }
            list.add(
                SocialAppInfo(
                    id = "telegram",
                    name = label,
                    packageName = tgPkg,
                    iconRes = R.drawable.ic_telegram,
                    iconDrawable = icon,
                    assetIcon = "file:///android_asset/icons/telegram.png",
                    action = { ctx, num -> openTelegramChat(ctx, num) },
                    videoCallAction = { ctx, num -> startTelegramVideoCall(ctx, num) },
                    voiceCallAction = { ctx, num -> startTelegramVoiceCall(ctx, num) }
                )
            )
        }

        // 3. Signal
        if (context.isPackageInstalled("org.thoughtcrime.securesms")) {
            val appInfo = runCatching { pm.getApplicationInfo("org.thoughtcrime.securesms", 0) }.getOrNull()
            val label = appInfo?.let { pm.getApplicationLabel(it).toString() } ?: "Signal"
            val icon = appInfo?.let { pm.getApplicationIcon(it) }
            list.add(
                SocialAppInfo(
                    id = "signal",
                    name = label,
                    packageName = "org.thoughtcrime.securesms",
                    iconRes = R.drawable.ic_signal,
                    iconDrawable = icon,
                    assetIcon = "file:///android_asset/icons/signal.png",
                    action = { ctx, num -> openSignalChat(ctx, num) }
                )
            )
        }

        // 4. Viber
        if (context.isPackageInstalled("com.viber.voip")) {
            val appInfo = runCatching { pm.getApplicationInfo("com.viber.voip", 0) }.getOrNull()
            val label = appInfo?.let { pm.getApplicationLabel(it).toString() } ?: "Viber"
            val icon = appInfo?.let { pm.getApplicationIcon(it) }
            list.add(
                SocialAppInfo(
                    id = "viber",
                    name = label,
                    packageName = "com.viber.voip",
                    iconRes = R.drawable.ic_viber,
                    iconDrawable = icon,
                    action = { ctx, num -> openViberChat(ctx, num) }
                )
            )
        }

        // 5. Meet
        if (context.isPackageInstalled("com.google.android.apps.tachyon")) {
            val appInfo = runCatching { pm.getApplicationInfo("com.google.android.apps.tachyon", 0) }.getOrNull()
            val label = appInfo?.let { pm.getApplicationLabel(it).toString() } ?: "Meet"
            val icon = appInfo?.let { pm.getApplicationIcon(it) }
            list.add(
                SocialAppInfo(
                    id = "meet",
                    name = label,
                    packageName = "com.google.android.apps.tachyon",
                    iconRes = R.drawable.ic_messenger,
                    iconDrawable = icon,
                    action = { ctx, num -> openMeet(ctx, num) },
                    videoCallAction = { ctx, num -> startMeetVideoCall(ctx, num) },
                    voiceCallAction = { ctx, num -> startMeetVoiceCall(ctx, num) }
                )
            )
        }

        return list
    }

    fun Context.getInstalledMessenger(packageNames: List<String>): String? {
        return packageNames.firstOrNull { isPackageInstalled(it) }
    }

    val messengerPackages = mapOf(
        "WhatsApp" to listOf("com.whatsapp", "com.whatsapp.w4b"),
        "Telegram" to listOf(
            "org.telegram.messenger",
            "org.telegram.plus",
            "org.thunderdog.challegram",
            "com.ghost.telegram",
            "nekox.messenger",
            "it.feed.android"
        ),
        "Signal" to listOf("org.thoughtcrime.securesms"),
        "Viber" to listOf("com.viber.voip"),
        "Meet" to listOf("com.google.android.apps.tachyon"),

    )

    private fun contactHasMatchingNumber(cr: ContentResolver, contactId: Long, digits: String): Boolean {
        if (digits.isEmpty()) return false
        return try {
            cr.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                arrayOf(contactId.toString()),
                null
            )?.use { c ->
                while (c.moveToNext()) {
                    val num = c.getString(0) ?: continue
                    val norm = num.filter { it.isDigit() }
                    if (norm.endsWith(digits) || digits.endsWith(norm)) return true
                }
                false
            } ?: false
        } catch (_: Exception) { false }
    }

    private fun findWhatsAppCallDataUri(ctx: Context, number: String, mime: String): Uri? {
        if (!hasReadContacts(ctx)) return null
        val digits = cleanDigitsOnly(ctx, number)
        if (digits.isEmpty()) return null
        return try {
            val cr = ctx.contentResolver
            cr.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(ContactsContract.Data._ID, ContactsContract.Data.CONTACT_ID),
                "${ContactsContract.Data.MIMETYPE} = ?",
                arrayOf(mime),
                null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val dataId = cursor.getLong(0)
                    val contactId = cursor.getLong(1)
                    if (contactHasMatchingNumber(cr, contactId, digits)) {
                        return ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI, dataId)
                    }
                }
                null
            }
        } catch (_: Exception) { null }
    }

    private fun findTelegramCallDataUri(ctx: Context, number: String, wantVideo: Boolean): Uri? {
        if (!hasReadContacts(ctx)) return null
        val digits = cleanDigitsOnly(ctx, number)
        if (digits.isEmpty()) return null
        return try {
            val cr = ctx.contentResolver
            cr.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(
                    ContactsContract.Data._ID,
                    ContactsContract.Data.CONTACT_ID,
                    ContactsContract.Data.MIMETYPE
                ),
                null, null, null
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(ContactsContract.Data._ID)
                val cidIdx = cursor.getColumnIndexOrThrow(ContactsContract.Data.CONTACT_ID)
                val mimeIdx = cursor.getColumnIndexOrThrow(ContactsContract.Data.MIMETYPE)
                var fallback: Uri? = null
                while (cursor.moveToNext()) {
                    val mime = cursor.getString(mimeIdx)?.lowercase() ?: continue
                    if (!mime.contains("telegram") || !mime.contains("call")) continue
                    val contactId = cursor.getLong(cidIdx)
                    if (!contactHasMatchingNumber(cr, contactId, digits)) continue
                    val uri = ContentUris.withAppendedId(
                        ContactsContract.Data.CONTENT_URI, cursor.getLong(idIdx)
                    )
                    if (mime.contains("video") == wantVideo) return uri
                    if (fallback == null) fallback = uri
                }
                fallback
            }
        } catch (_: Exception) { null }
    }

    private fun findMeetCallDataUri(ctx: Context, number: String, mime: String): Uri? {
        if (!hasReadContacts(ctx)) return null
        val digits = cleanDigitsOnly(ctx, number)
        if (digits.isEmpty()) return null
        return try {
            val cr = ctx.contentResolver
            cr.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(ContactsContract.Data._ID, ContactsContract.Data.CONTACT_ID),
                "${ContactsContract.Data.MIMETYPE} = ?",
                arrayOf(mime),
                null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val dataId = cursor.getLong(0)
                    val contactId = cursor.getLong(1)
                    if (contactHasMatchingNumber(cr, contactId, digits)) {
                        return ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI, dataId)
                    }
                }
                null
            }
        } catch (_: Exception) { null }
    }

    private fun startTachyonCallActivity(ctx: Context, number: String, activityClass: String): Boolean {
        if (!ctx.isPackageInstalled(TACHYON_PKG)) return false
        if (!hasCallPhone(ctx)) return false
        val clean = cleanDigitsOnly(ctx, number)
        if (clean.isEmpty()) return false
        return try {
            val intent = Intent(TACHYON_CALL_ACTION, "tel:$clean".toUri()).apply {
                setClassName(TACHYON_PKG, activityClass)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(intent)
            true
        } catch (_: Exception) { false }
    }

    // ── WhatsApp ─────────────────────────────────────────────────────────────
    fun startWhatsAppVoiceCall(ctx: Context, number: String): Boolean {
        val wa = when {
            ctx.isPackageInstalled("com.whatsapp") -> "com.whatsapp"
            ctx.isPackageInstalled("com.whatsapp.w4b") -> "com.whatsapp.w4b"
            else -> return false
        }
        val uri = findWhatsAppCallDataUri(ctx, number, MIME_WA_VOICE_CALL)
            ?: findWhatsAppCallDataUri(ctx, number, MIME_WA_BIZ_VOICE_CALL)
        if (uri != null) {
            return try {
                ctx.startActivity(
                    Intent(Intent.ACTION_VIEW, uri)
                        .setPackage(wa)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                true
            } catch (_: Exception) { openWhatsAppChat(ctx, number); true }
        }
        openWhatsAppChat(ctx, number)
        return true
    }

    fun startWhatsAppVideoCall(ctx: Context, number: String): Boolean {
        val wa = when {
            ctx.isPackageInstalled("com.whatsapp") -> "com.whatsapp"
            ctx.isPackageInstalled("com.whatsapp.w4b") -> "com.whatsapp.w4b"
            else -> return false
        }
        val uri = findWhatsAppCallDataUri(ctx, number, MIME_WA_VIDEO_CALL)
            ?: findWhatsAppCallDataUri(ctx, number, MIME_WA_BIZ_VIDEO_CALL)
        if (uri != null) {
            return try {
                ctx.startActivity(
                    Intent(Intent.ACTION_VIEW, uri)
                        .setPackage(wa)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                true
            } catch (_: Exception) { openWhatsAppChat(ctx, number); true }
        }
        openWhatsAppChat(ctx, number)
        return true
    }

    // ── Telegram ─────────────────────────────────────────────────────────────
    fun startTelegramVoiceCall(ctx: Context, number: String): Boolean {
        val pkg = ctx.getInstalledMessenger(messengerPackages["Telegram"]!!) ?: return false
        val uri = findTelegramCallDataUri(ctx, number, wantVideo = false)
        if (uri != null) {
            return try {
                ctx.startActivity(
                    Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                true
            } catch (_: Exception) { openTelegramChat(ctx, number); true }
        }
        openTelegramChat(ctx, number)
        return true
    }

    fun startTelegramVideoCall(ctx: Context, number: String): Boolean {
        val pkg = ctx.getInstalledMessenger(messengerPackages["Telegram"]!!) ?: return false
        val uri = findTelegramCallDataUri(ctx, number, wantVideo = true)
        if (uri != null) {
            return try {
                ctx.startActivity(
                    Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                true
            } catch (_: Exception) { openTelegramChat(ctx, number); true }
        }
        openTelegramChat(ctx, number)
        return true
    }

    // ── Google Meet ──────────────────────────────────────────────────────────
    fun startMeetVideoCall(ctx: Context, number: String): Boolean {
        if (!ctx.isPackageInstalled(TACHYON_PKG)) return false
        if (startTachyonCallActivity(ctx, number, TACHYON_VIDEO_ACTIVITY)) return true
        val uri = findMeetCallDataUri(ctx, number, MIME_MEET_VIDEO_CALL)
        if (uri != null) {
            return try {
                ctx.startActivity(
                    Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                true
            } catch (_: Exception) { false }
        }
        openMeet(ctx, number)
        return true
    }

    fun startMeetVoiceCall(ctx: Context, number: String): Boolean {
        if (!ctx.isPackageInstalled(TACHYON_PKG)) return false
        if (startTachyonCallActivity(ctx, number, TACHYON_AUDIO_ACTIVITY)) return true
        val uri = findMeetCallDataUri(ctx, number, MIME_MEET_AUDIO_CALL)
        if (uri != null) {
            return try {
                ctx.startActivity(
                    Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                true
            } catch (_: Exception) { false }
        }
        openMeet(ctx, number)
        return true
    }
}
