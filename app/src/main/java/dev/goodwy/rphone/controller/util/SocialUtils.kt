package dev.goodwy.rphone.controller.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri

object SocialUtils {
    fun openWhatsApp(context: Context, number: String) {
        val url = "https://api.whatsapp.com/send?phone=$number"
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        } catch (_: Exception) {}
    }

    fun openTelegram(context: Context, number: String) {
        val url = "tg://msg?text=&to=$number"
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        } catch (_: Exception) {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, "https://t.me/$number".toUri()))
            } catch (_: Exception) {}
        }
    }

    fun openSignal(context: Context, number: String) {
        val url = "sgnl://signal.me/#p/$number"
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        } catch (_: Exception) {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, "https://signal.me/#p/$number".toUri()))
            } catch (_: Exception) {}
        }
    }

    fun Context.launchSendWhatsAppIntent(phoneNumber: String) {
        val digits = phoneNumber.filter { it.isDigit() }
        if (digits.isEmpty()) {
            toast("No valid app found")
            return
        }
        val pkg = messengerPackages["WhatsApp"]?.let { getInstalledMessenger(it) }
        val intent = Intent(Intent.ACTION_VIEW, "https://wa.me/$digits".toUri())
        if (pkg != null) intent.setPackage(pkg)
        try {
            startActivity(intent)
        } catch (_: android.content.ActivityNotFoundException) {
            toast("No valid app found")
        }
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
        "WeChat" to listOf("com.tencent.mm"),
        "Line" to listOf("jp.naver.line.android"),
        "Skype" to listOf("com.skype.raider"),
        "Discord" to listOf("com.discord"),
        "Slack" to listOf("com.Slack"),
        "Messenger" to listOf("com.facebook.orca"),
        "Snapchat" to listOf("com.snapchat.android"),
        "KakaoTalk" to listOf("com.kakao.talk"),
        "Threema" to listOf("ch.threema.app"),
        "Wire" to listOf("com.wire"),
        "Tox" to listOf("im.tox.tox"),
        "Element" to listOf("im.vector.app")
    )
}
