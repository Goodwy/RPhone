package dev.goodwy.rphone.controller.util

import android.accounts.Account
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.SimCard
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import dev.goodwy.rphone.R

object ContactUtils {
    fun getAccountName(account: Account): String {
        return when {
            account.type.contains("telegram", ignoreCase = true) -> "Telegram (${account.name})"
            account.type.contains("xiaomi", ignoreCase = true) -> "Mi Account (${account.name})"
            else -> account.name
        }
    }

    fun getAccountType(account: Account?): String {
        return when {
            account == null -> "Local (Device Only)"
            account.type == "com.google" -> "Google"
            account.type == "com.whatsapp" -> "WhatsApp"
            account.type.contains("telegram", ignoreCase = true) -> "Telegram"
            account.type.contains("xiaomi", ignoreCase = true) -> "Mi Account"
            account.type.contains("sim", ignoreCase = true) -> "SIM Card"
            account.name.contains("@") -> account.name.substringBefore("@")
            else -> account.name
        }
    }

    @Composable
    fun getAccountIcon(account: Account?): ImageVector {
        return when {
            account == null -> Icons.Rounded.CloudOff
            account.type == "com.google" -> ImageVector.vectorResource(id = R.drawable.ic_google)
            account.type == "com.whatsapp" -> ImageVector.vectorResource(id = R.drawable.ic_whatsapp)
            account.type.contains("telegram", ignoreCase = true) -> ImageVector.vectorResource(id = R.drawable.ic_telegram)
            account.type.contains("xiaomi", ignoreCase = true) -> ImageVector.vectorResource(id = R.drawable.ic_xiaomi)
            account.type.contains("sim", ignoreCase = true) -> Icons.Rounded.SimCard
            else -> Icons.Rounded.Android
        }
    }

    fun getAccountKey(account: Account?): String? {
        return when {
            account == null -> null
            else -> "${account.name}|${account.type}"
        }
    }
}
