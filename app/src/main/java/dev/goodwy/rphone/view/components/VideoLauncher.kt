package dev.goodwy.rphone.view.components

import android.content.Intent
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import dev.goodwy.rphone.controller.util.*
import dev.goodwy.rphone.modal.data.Contact
import org.koin.compose.koinInject
import androidx.core.net.toUri
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
    val prefs = koinInject<PreferenceManager>()

    var showAppPicker by remember { mutableStateOf(false) }
    var pendingNumber by remember { mutableStateOf("") }
    var pendingContact by remember { mutableStateOf<Contact?>(null) }

    val launchApp = { pkg: String, number: String ->
        try {
            val intent = context.packageManager.getLaunchIntentForPackage(pkg)
            if (intent != null) {
                when (pkg) {
                    "com.google.android.apps.meetings" -> {
                        intent.data = "https://meet.google.com/".toUri()
                        intent.action = Intent.ACTION_VIEW
                    }
                    "com.microsoft.teams" -> {
                        intent.data = "https://teams.microsoft.com/l/chat/0/0?users=$number".toUri()
                        intent.action = Intent.ACTION_VIEW
                    }
                    "us.zoom.videomeetings" -> {
                        intent.data = "zoommtg://zoom.us/join".toUri()
                        intent.action = Intent.ACTION_VIEW
                    }
                    else -> {
                        intent.data = "tel:$number".toUri()
                        intent.action = Intent.ACTION_VIEW
                    }
                }
                context.startActivity(intent)
            } else {
                val chooser = Intent.createChooser(
                    Intent(Intent.ACTION_VIEW, "tel:$number".toUri()),
                    "Video Call with"
                )
                context.startActivity(chooser)
            }
        } catch (e: Exception) {
            val chooser = Intent.createChooser(
                Intent(Intent.ACTION_VIEW, "tel:$number".toUri()),
                "Video Call with"
            )
            context.startActivity(chooser)
        }
    }

    val videoLauncher = remember {
        VideoLauncher { number, contact ->
            pendingNumber = number
            pendingContact = contact
            showAppPicker = true
        }
    }

    if (showAppPicker) {
        data class VideoAppItem(val name: String, val packageName: String?, val iconRes: Int)

        val availableApps = mutableListOf<VideoAppItem>()

        // WhatsApp
        listOf("com.whatsapp", "com.whatsapp.w4b").firstOrNull { context.isPackageInstalled(it) }?.let {
            availableApps.add(VideoAppItem("WhatsApp", it, R.drawable.ic_whatsapp))
        }

        // Google Meet
        if (context.isPackageInstalled("com.google.android.apps.meetings")) {
            availableApps.add(VideoAppItem("Google Meet", "com.google.android.apps.meetings", R.drawable.ic_google_meet))
        }

        // Zoom
        if (context.isPackageInstalled("us.zoom.videomeetings")) {
            availableApps.add(VideoAppItem("Zoom", "us.zoom.videomeetings", R.drawable.ic_zoom))
        }

        // Telegram
        listOf(
            "org.telegram.messenger",
            "org.telegram.plus",
            "org.thunderdog.challegram",
            "com.ghost.telegram",
            "nekox.messenger",
            "it.feed.android"
        ).firstOrNull { context.isPackageInstalled(it) }?.let {
            availableApps.add(VideoAppItem("Telegram", it, R.drawable.ic_telegram))
        }

        // Signal
        if (context.isPackageInstalled("org.thoughtcrime.securesms")) {
            availableApps.add(VideoAppItem("Signal", "org.thoughtcrime.securesms", R.drawable.ic_signal))
        }

        // Viber
        if (context.isPackageInstalled("com.viber.voip")) {
            availableApps.add(VideoAppItem("Viber", "com.viber.voip", R.drawable.ic_viber))
        }

        // Microsoft Teams
        if (context.isPackageInstalled("com.microsoft.teams")) {
            availableApps.add(VideoAppItem("Microsoft Teams", "com.microsoft.teams", R.drawable.ic_teams))
        }

        // Discord
        if (context.isPackageInstalled("com.discord")) {
            availableApps.add(VideoAppItem("Discord", "com.discord", R.drawable.ic_discord))
        }

        // Messenger
        if (context.isPackageInstalled("com.facebook.orca")) {
            availableApps.add(VideoAppItem("Messenger", "com.facebook.orca", R.drawable.ic_messenger))
        }

        availableApps.sortBy { it.name }
        val systemVideoCall = VideoAppItem("System Video Call", "system", R.drawable.ic_video_camera)
        availableApps.add(systemVideoCall)

        val videoImage = ImageVector.vectorResource(id = R.drawable.ic_video_camera)

        RillSelectionDialog(
            onDismissRequest = { showAppPicker = false },
            title = "Video Call",
            items = availableApps,
            itemLabel = { it.name },
            onItemSelected = { app ->
                if (app.packageName == "system") {
                    // System-wide video call
                    val uri = "tel:${pendingNumber}".toUri()
                    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                        setDataAndType(uri, "vnd.android.cursor.item/video-chat-address")
                    }
                    try {
                        context.startActivity(Intent.createChooser(intent, "Video Call"))
                    } catch (e: Exception) {
                        // Fallback—just a phone call
                        val fallbackIntent = Intent(Intent.ACTION_VIEW, "tel:${pendingNumber}".toUri())
                        context.startActivity(fallbackIntent)
                    }
                } else {
                    launchApp(app.packageName!!, pendingNumber)
                }
                showAppPicker = false
            },
            icon = videoImage,
            itemIcon = { app ->
                ImageVector.vectorResource(id = app.iconRes)
            },
            itemSupporting = { app ->
                (if (app.packageName == "system") {
                    "Use system video call capability"
                } else {
                    app.packageName
                }).toString()
            }
        )
    }

    return videoLauncher
}