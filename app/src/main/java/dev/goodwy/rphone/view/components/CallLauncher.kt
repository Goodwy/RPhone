package dev.goodwy.rphone.view.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.core.content.ContextCompat
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.*
import dev.goodwy.rphone.modal.data.Contact
import org.koin.compose.koinInject

class CallLauncher(
    private val onInitiate: (String, Contact?) -> Unit
) {
    fun dial(number: String, contact: Contact? = null) {
        onInitiate(number, contact)
    }
}

@Composable
fun rememberCallLauncher(): CallLauncher {
    val context = LocalContext.current
    val prefs = koinInject<PreferenceManager>()
    val simPref = remember {
        prefs.getInt(PreferenceManager.KEY_DEFAULT_SIM, prefs.getDefaultSimIndexDefault())
    }

    var showAppPicker by remember { mutableStateOf(false) }
    var pendingNumber by remember { mutableStateOf("") }
    var pendingContact by remember { mutableStateOf<Contact?>(null) }

    var showSimPicker by remember { mutableStateOf(false) }
    var simPickerNumber by remember { mutableStateOf("") }

    var hasCallPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.CALL_PHONE
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCallPermission = granted }

    val performSystemCall: (String) -> Unit = { number ->
        if (hasCallPermission) {
            placeCallWithSimPreference(context, number, simPref) {
                simPickerNumber = number
                showSimPicker = true
            }
        } else {
            permissionLauncher.launch(Manifest.permission.CALL_PHONE)
        }
    }

    val callLauncher = remember {
        CallLauncher { number, contact ->
            pendingNumber = number
            pendingContact = contact
            showAppPicker = true
        }
    }

    if (showAppPicker) {
        val callText = stringResource(R.string.call)
        val apps = remember(pendingNumber) {
            val messengers = SocialUtils.getInstalledSocialApps(context)
                .filter { it.voiceCallAction != null }
                .sortedBy { it.name }
                .toMutableList()

            messengers.add(
                SocialAppInfo(
                    id = "system_call",
                    name = callText,
                    packageName = null,
                    iconRes = R.drawable.ic_phone,
                    action = { _, _ -> },
                    voiceCallAction = { _, _ -> true }
                )
            )
            messengers
        }

        RillSelectionDialog(
            onDismissRequest = { showAppPicker = false },
            title = stringResource(R.string.voice_call),
            items = apps,
            itemLabel = { it.name },
            onItemSelected = { app ->
                showAppPicker = false
                if (app.id == "system_call") {
                    performSystemCall(pendingNumber)
                } else {
                    val ok = app.voiceCallAction?.invoke(context, pendingNumber) ?: false
                    if (!ok) app.action(context, pendingNumber)
                }
            },
            icon = Icons.Rounded.Phone,
            itemIcon = { app ->
                if (app.id == "system_call") Icons.Outlined.Phone
                else ImageVector.vectorResource(id = app.iconRes)
            },
            itemSupporting = { app -> app.packageName },
            isSettings = false
        )
    }

    // ── SimPicker ──────────────────────────────────────
    if (showSimPicker && simPickerNumber.isNotEmpty()) {
        SimPickerDialog(
            onDismissRequest = { showSimPicker = false },
            onSimSelected = { handle ->
                makeCall(context, simPickerNumber, handle)
                showSimPicker = false
            }
        )
    }

    return callLauncher
}