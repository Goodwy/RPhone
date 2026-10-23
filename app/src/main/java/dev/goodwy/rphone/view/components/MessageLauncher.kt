package dev.goodwy.rphone.view.components

import android.Manifest
import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.core.net.toUri
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.*
import dev.goodwy.rphone.controller.util.SocialUtils.formatInternationalNumber
import dev.goodwy.rphone.modal.data.Contact
import org.koin.compose.koinInject

class MessageLauncher(
    private val onInitiate: (String, Contact?) -> Unit
) {
    fun sendMessage(number: String, contact: Contact? = null) {
        onInitiate(number, contact)
    }
}

@Composable
fun rememberMessageLauncher(): MessageLauncher {
    val context = LocalContext.current
    val prefs = koinInject<PreferenceManager>()

    val performSMS: (String) -> Unit = { number ->
        val intl = formatInternationalNumber(context, number)
        val intent = Intent(Intent.ACTION_SENDTO, "smsto:$intl".toUri()).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {}
    }

    var showAppPicker by remember { mutableStateOf(false) }
    var pendingNumber by remember { mutableStateOf("") }
    var pendingContact by remember { mutableStateOf<Contact?>(null) }

    val messageLauncher = remember {
        MessageLauncher { number, contact ->
            pendingNumber = number
            pendingContact = contact
            showAppPicker = true
        }
    }

    if (showAppPicker) {
        val apps = remember(pendingNumber) {
            val messengers = SocialUtils.getInstalledSocialApps(context)
                .filter { it.id != "meet" }
                .sortedBy { it.name }
                .toMutableList()

            messengers.add(
                SocialAppInfo(
                    id = "sms",
                    name = "SMS",
                    packageName = null,
                    iconRes = R.drawable.ic_message_outline,
                    action = { _, _ -> },
                )
            )
            messengers
        }

        if (apps.size > 1) RillSelectionDialog(
            onDismissRequest = { showAppPicker = false },
            title = stringResource(R.string.message),
            items = apps,
            itemLabel = { it.name },
            onItemSelected = { app ->
                showAppPicker = false
                if (app.id == "sms") {
                    performSMS(pendingNumber)
                } else {
                    app.action(context, pendingNumber)
                }
            },
            icon = ImageVector.vectorResource(R.drawable.ic_message_filled),
            itemIcon = { app ->
                app.iconRes.let { ImageVector.vectorResource(it) }
            },
            itemSupporting = { app -> app.packageName },
            isSettings = false
        )
        else apps.first().let { app ->
            app.action(context, pendingNumber)
            showAppPicker = false
        }
    }

    return messageLauncher
}