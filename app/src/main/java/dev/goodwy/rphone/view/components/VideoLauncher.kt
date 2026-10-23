package dev.goodwy.rphone.view.components

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import dev.goodwy.rphone.controller.util.*
import dev.goodwy.rphone.modal.data.Contact
import dev.goodwy.rphone.R

class VideoLauncher(
    private val onInitiate: (String, Contact?) -> Unit
) {
    fun startVideoCall(number: String, contact: Contact? = null) {
        onInitiate(number, contact)
    }
}

@Composable
fun rememberVideoLauncher(): VideoLauncher {
    val context = LocalContext.current

    var showAppPicker by remember { mutableStateOf(false) }
    var pendingNumber by remember { mutableStateOf("") }
    var pendingContact by remember { mutableStateOf<Contact?>(null) }

    val videoLauncher = remember {
        VideoLauncher { number, contact ->
            pendingNumber = number
            pendingContact = contact
            showAppPicker = true
        }
    }

    if (showAppPicker) {
        val videoImage = ImageVector.vectorResource(id = R.drawable.ic_video_camera)
        val apps = SocialUtils.getInstalledSocialApps(context)
            .filter { it.videoCallAction != null }
            .sortedBy { it.name }

        if (apps.size > 1) RillSelectionDialog(
            onDismissRequest = { showAppPicker = false },
            title = stringResource(R.string.video_call),
            items = apps,
            itemLabel = { it.name },
            onItemSelected = { app ->
                val ok = app.videoCallAction?.invoke(context, pendingNumber) ?: false
                if (!ok) app.action(context, pendingNumber)
                showAppPicker = false
            },
            icon = videoImage,
            itemIcon = { app ->
                ImageVector.vectorResource(id = app.iconRes)
            },
            itemSupporting = { app -> app.packageName },
            isSettings = false
        )
        else if (apps.size == 1) apps.first().let { app ->
            app.action(context, pendingNumber)
            showAppPicker = false
        }
        else {
            context.toast(stringResource(R.string.no_valid_app_found))
        }
    }

    return videoLauncher
}