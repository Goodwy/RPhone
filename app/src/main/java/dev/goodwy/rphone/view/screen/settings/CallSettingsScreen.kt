package dev.goodwy.rphone.view.screen.settings

import android.accounts.Account
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.telecom.TelecomManager
import android.view.Surface
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.rounded.Backpack
import androidx.compose.material.icons.rounded.PictureInPicture
import androidx.compose.material.icons.rounded.SettingsPhone
import androidx.compose.material.icons.rounded.SimCard
import androidx.compose.material.icons.rounded.SpatialTracking
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.ContactsViewModel
import dev.goodwy.rphone.controller.util.ContactUtils
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.view.components.NavigationIcon
import dev.goodwy.rphone.view.components.RillAnimatedSection
import dev.goodwy.rphone.view.components.RillExpressiveCard
import dev.goodwy.rphone.view.components.RillListItem
import dev.goodwy.rphone.view.components.RillSwitchListItem
import dev.goodwy.rphone.view.theme.customColors
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import org.koin.compose.koinInject
import androidx.core.net.toUri
import org.koin.compose.viewmodel.koinActivityViewModel

// ─── Contacts to Display Dialog ───────────────────────────────────────────────

data class ContactSourceItem(
    val key: String,
    val label: String,
    val subLabel: String? = null
)

@Composable
fun ContactsToDisplayDialog(
    onDismiss: () -> Unit,
    prefs: PreferenceManager
) {
    val context = LocalContext.current

    val contactsVM: ContactsViewModel = koinActivityViewModel()
    val sources = contactsVM.availableAccounts.collectAsState().value

    // Load saved enabled keys
    val savedKeys = remember {
        val raw = prefs.getString(PreferenceManager.KEY_CONTACTS_DISPLAY_ACCOUNTS, null)
        if (raw.isNullOrBlank()) sources.map { getAccountKey(it) }.toSet()
        else raw.split(",").toSet()
    }
    val checkedKeys = remember { mutableStateOf(savedKeys) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Contacts to display") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                sources.forEach { source ->
                    val isChecked = getAccountKey(source) in checkedKeys.value
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = { checked ->
                                checkedKeys.value = if (checked) {
                                    checkedKeys.value + getAccountKey(source)
                                } else {
                                    checkedKeys.value - getAccountKey(source)
                                }
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = ContactUtils.getAccountName(source),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = ContactUtils.getFriendlyAccountName(source),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                prefs.setString(
                    PreferenceManager.KEY_CONTACTS_DISPLAY_ACCOUNTS,
                    checkedKeys.value.joinToString(",")
                )
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun CallSettingsScreen(navigator: DestinationsNavigator) {
    val prefs = koinInject<PreferenceManager>()
    val context = LocalContext.current

    var proximityBg by remember { mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_PROXIMITY_BG, true)) }
    var pocketModePrevention by remember { mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_POCKET_MODE_PREVENTION, false)) }
    var floatingCall by remember { mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_FLOATING_CALL, false)) }
    var directCallOnTap by remember { mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_DIRECT_CALL_ON_TAP, false)) }
    var autoSpeaker by remember { mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_AUTO_SPEAKER, false)) }
    var showContactsToDisplayDialog by remember { mutableStateOf(false) }
    var defaultSim by remember { mutableStateOf(prefs.getInt("default_sim", 0)) }
    var showSimDialog by remember { mutableStateOf(false) }

    var visible by remember { mutableStateOf(false) }
    val screenAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(350),
        label = "callSettingsAlpha"
    )
    LaunchedEffect(Unit) { visible = true }

    if (showContactsToDisplayDialog) {
        ContactsToDisplayDialog(
            onDismiss = { showContactsToDisplayDialog = false },
            prefs = prefs
        )
    }

    if (showSimDialog) {
        AlertDialog(
            onDismissRequest = { showSimDialog = false },
            title = { Text("Default SIM") },
            text = {
                Column {
                    listOf("Ask every time", "SIM 1", "SIM 2").forEachIndexed { index, label ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = defaultSim == index,
                                onClick = {
                                    defaultSim = index
                                    prefs.setInt("default_sim", index)
                                    showSimDialog = false
                                }
                            )
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSimDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    val rotation =
        (context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager).defaultDisplay.rotation
    val isRotation90 = rotation == Surface.ROTATION_90
    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets.systemBars.only(
                    if (isRotation90) WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                    else WindowInsetsSides.Top
                ),
                title = { Text("Call Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    NavigationIcon(onClick = { navigator.navigateUp() })
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
//                .padding(padding)
                .padding(
                    top = padding.calculateTopPadding(),
                    start = 0.dp,
                    end = 0.dp,
                    bottom = 0.dp
                )
                .alpha(screenAlpha),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Caller Accounts ───────────────────────────────────────────────
            item {
                RillAnimatedSection(delayMs = 0L) {
                    Column {
                        CallSettingsSectionLabel("Accounts")
                        RillExpressiveCard {
                            RillListItem(
                                headline = "Default SIM",
                                supporting = when(defaultSim) {
                                    0 -> "Ask every time"
                                    1 -> "SIM 1"
                                    2 -> "SIM 2"
                                    else -> "Ask every time"
                                },
                                leadingIcon = Icons.Rounded.SimCard,
                                iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkGreen,
                                iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorGreen,
                                trailingIcon = Icons.Default.ChevronRight,
                                onClick = { showSimDialog = true }
                            )
                            RillListItem(
                                headline = stringResource(R.string.calling_accounts),
//                                supporting = "Configure carrier settings (Call Waiting, Forwarding, etc.)",
                                leadingIcon = Icons.Rounded.SettingsPhone,
                                iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkGreen,
                                iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorGreen,
                                trailingIcon = Icons.Default.ChevronRight,
                                onClick = {
                                    try {
                                        val intent = Intent(TelecomManager.ACTION_CHANGE_PHONE_ACCOUNTS).apply {
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        }
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        try {
                                            val intent = Intent("android.telecom.action.SHOW_CALL_SETTINGS").apply {
                                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                            }
                                            context.startActivity(intent)
                                        } catch (e2: Exception) {
                                            try {
                                                val intent = Intent(android.provider.Settings.ACTION_SETTINGS).apply {
                                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                                }
                                                context.startActivity(intent)
                                            } catch (e3: Exception) {}
                                        }
                                    }
                                }
                            )
//                            RillListItem(
//                                headline = "Contacts to display",
//                                supporting = "Choose which accounts' contacts are shown",
//                                leadingIcon = Icons.Rounded.Contacts,
//                                iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkBlue,
//                                iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorBlue,
//                                trailingIcon = Icons.Default.ChevronRight,
//                                onClick = { showContactsToDisplayDialog = true }
//                            )
                        }
                    }
                }
            }

            // ── Call Behavior ─────────────────────────────────────────────────
            item {
                RillAnimatedSection(delayMs = 60L) {
                    Column {
                        CallSettingsSectionLabel("Call Behavior")
                        RillExpressiveCard {
                            RillSwitchListItem(
                                headline   = "Proximity Sensor on in background",
                                supporting = "Turn off screen when phone is near ear during a call",
                                leadingIcon = Icons.Rounded.SpatialTracking,
                                iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkOrange,
                                iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorOrange,
                                checked = proximityBg,
                                onCheckedChange = {
                                    proximityBg = it
                                    prefs.setBoolean(PreferenceManager.KEY_PROXIMITY_BG, it)
                                }
                            )
                            RillSwitchListItem(
                                headline   = "Pocket Mode Prevention",
                                supporting = "Block accidental answer/decline when phone is in pocket",
                                leadingIcon = Icons.Rounded.Backpack,
                                iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkOrange,
                                iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorOrange,
                                checked = pocketModePrevention,
                                onCheckedChange = {
                                    pocketModePrevention = it
                                    prefs.setBoolean(PreferenceManager.KEY_POCKET_MODE_PREVENTION, it)
                                }
                            )
                            RillSwitchListItem(
                                headline   = "Floating Ongoing Call",
                                supporting = "Show a draggable floating bubble during calls. Requires 'Display over other apps' permission.",
                                leadingIcon = Icons.Rounded.PictureInPicture,
                                iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkGreen,
                                iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorGreen,
                                checked = floatingCall,
                                onCheckedChange = { newValue ->
                                    if (newValue && !Settings.canDrawOverlays(context)) {
                                        context.startActivity(
                                            Intent(
                                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                "package:${context.packageName}".toUri()
                                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        )
                                    } else {
                                        floatingCall = newValue
                                        prefs.setBoolean(PreferenceManager.KEY_FLOATING_CALL, newValue)
                                    }
                                }
                            )
                            RillSwitchListItem(
                                headline   = "Direct Call on Tap",
                                supporting = "Tap a call log entry to call directly instead of viewing contact info",
                                leadingIcon = Icons.Rounded.TouchApp,
                                iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkGreen,
                                iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorGreen,
                                checked = directCallOnTap,
                                onCheckedChange = {
                                    directCallOnTap = it
                                    prefs.setBoolean(PreferenceManager.KEY_DIRECT_CALL_ON_TAP, it)
                                }
                            )
                            RillSwitchListItem(
                                headline   = "Auto Speaker",
                                supporting = "Automatically switch to loudspeaker when phone is away from ear, and back to earpiece when near",
                                leadingIcon = Icons.AutoMirrored.Rounded.VolumeUp,
                                iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkAmber,
                                iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorAmber,
                                checked = autoSpeaker,
                                onCheckedChange = {
                                    autoSpeaker = it
                                    prefs.setBoolean(PreferenceManager.KEY_AUTO_SPEAKER, it)
                                }
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun CallSettingsSectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(start = 12.dp, bottom = 8.dp),
        color = MaterialTheme.colorScheme.primary
    )
}

fun getAccountKey(account: Account?): String? {
    return ContactUtils.getAccountKey(account)
}
