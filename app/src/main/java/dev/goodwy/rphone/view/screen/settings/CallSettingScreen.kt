package dev.goodwy.rphone.view.screen.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.telecom.PhoneAccount
import android.telecom.TelecomManager
import android.view.Surface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.outlined.DoNotDisturbOn
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.rounded.CropFree
import androidx.compose.material.icons.rounded.PictureInPicture
import androidx.compose.material.icons.rounded.ScreenLockPortrait
import androidx.compose.material.icons.rounded.SettingsPhone
import androidx.compose.material.icons.rounded.SimCard
import androidx.compose.material.icons.rounded.SpatialTracking
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.view.components.NavigationIcon
import dev.goodwy.rphone.view.components.RillAnimatedSection
import dev.goodwy.rphone.view.components.RillDialog
import dev.goodwy.rphone.view.components.RillExpressiveCard
import dev.goodwy.rphone.view.components.RillListItem
import dev.goodwy.rphone.view.components.RillSwitchListItem
import dev.goodwy.rphone.view.theme.customColors
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import org.koin.compose.koinInject
import androidx.core.net.toUri
import com.ramcosta.composedestinations.generated.destinations.*
import com.ramcosta.composedestinations.generated.destinations.SpeedDialScreenDestination
import com.ramcosta.composedestinations.generated.destinations.QuickResponsesScreenDestination
import dev.goodwy.rphone.view.components.RillSelectListItem
import dev.goodwy.rphone.view.components.Title

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun CallSettingScreen(navigator: DestinationsNavigator) {
    val prefs = koinInject<PreferenceManager>()
    val context = LocalContext.current

    val settingsState by prefs.settingsChanged.collectAsState()
    var proximityBg by remember(settingsState) { mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_PROXIMITY_SENSOR, true)) }
    var pocketModePrevention by remember(settingsState) { mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_POCKET_MODE, false)) }
    var floatingCall by remember(settingsState) { mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_FLOATING_CALL, false)) }
    var directCallOnTap by remember(settingsState) { mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_DIRECT_CALL_ON_TAP, false)) }
    var autoSpeaker by remember(settingsState) { mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_AUTO_SPEAKER, false)) }
    var defaultSim by remember(settingsState) { mutableStateOf(prefs.getInt(PreferenceManager.KEY_DEFAULT_SIM, prefs.getDefaultSimIndexDefault())) }
    var fullscreenCalls by remember(settingsState) { mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_ALWAYS_FULLSCREEN_CALLS, false)) }
    var speedDial by remember(settingsState) { mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_SPEED_DIAL, true)) }

    val defaultBusyMsg = stringResource(R.string.busy_mode_default_message)
    var busyMode by remember(settingsState) { mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_BUSY_MODE_ENABLED, false)) }
    var busyMessage by remember(settingsState) { mutableStateOf(prefs.getString(PreferenceManager.KEY_BUSY_MODE_MESSAGE, defaultBusyMsg) ?: defaultBusyMsg) }
    var showBusyMessageDialog by remember { mutableStateOf(false) }
    var editingBusyMsgText by remember { mutableStateOf("") }

    val smsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            busyMode = true
            prefs.setBoolean(PreferenceManager.KEY_BUSY_MODE_ENABLED, true)
        } else {
            busyMode = false
            prefs.setBoolean(PreferenceManager.KEY_BUSY_MODE_ENABLED, false)
        }
    }

    var visible by remember { mutableStateOf(false) }
    val screenAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(350),
        label = "callSettingsAlpha"
    )
    LaunchedEffect(Unit) { visible = true }

    val rotation =
        (context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager).defaultDisplay.rotation
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val isRotation90 = rotation == if (isLtr) Surface.ROTATION_90 else Surface.ROTATION_270
    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets.systemBars.only(
                    if (isRotation90) WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                    else WindowInsetsSides.Top
                ),
                title = { Title(stringResource(R.string.call_settings)) },
                navigationIcon = {
                    NavigationIcon(onClick = { navigator.navigateUp() })
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
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

            item {
                RillAnimatedSection(delayMs = 30L) {
                    RillExpressiveCard {
                        RillListItem(
                            headline = stringResource(R.string.settings_speed_dial_title),
                            supporting = if (speedDial) stringResource(R.string.on) else stringResource(
                                R.string.off
                            ),
                            leadingIcon = Icons.Default.Dialpad,
                            iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkIndigo,
                            iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorIndigo,
                            trailingIcon = Icons.Default.ChevronRight,
                            onClick = { navigator.navigate(SpeedDialScreenDestination) }
                        )
                    }
                }
            }

            // ── Caller Accounts ───────────────────────────────────────────────
            item {
                RillAnimatedSection(delayMs = 0L) {
                    Column {
                        SettingsSectionLabel("SIM")
                        RillExpressiveCard {
                            val simList = getSimSelectionList(R.string.ask_first)
                            RillSelectListItem(
                                headline = stringResource(R.string.default_sim),
                                leadingIcon = Icons.Rounded.SimCard,
                                iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkGreen,
                                iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorGreen,
                                options = simList,
                                selectedValue = defaultSim,
                                onValueChange = { newValue: Int ->
                                    defaultSim = newValue
                                    prefs.setInt(PreferenceManager.KEY_DEFAULT_SIM, newValue)
                                }
                            )
                            RillListItem(
                                headline = stringResource(R.string.calling_accounts),
                                supporting = stringResource(R.string.system_settings),
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
                                    } catch (_: Exception) {
                                        try {
                                            val intent = Intent("android.telecom.action.SHOW_CALL_SETTINGS").apply {
                                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                            }
                                            context.startActivity(intent)
                                        } catch (_: Exception) {
                                            try {
                                                val intent = Intent(android.provider.Settings.ACTION_SETTINGS).apply {
                                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                                }
                                                context.startActivity(intent)
                                            } catch (_: Exception) {}
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // ── Call Behavior ─────────────────────────────────────────────────
            item {
                RillAnimatedSection(delayMs = 60L) {
                    Column {
                        SettingsSectionLabel(stringResource(R.string.call_behavior))
                        RillExpressiveCard {
                            RillListItem(
                                headline = stringResource(R.string.quick_responses),
                                supporting = stringResource(R.string.quick_responses_subtitle),
                                leadingIcon = ImageVector.vectorResource(id = R.drawable.ic_message_filled),
                                iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkAmber,
                                iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorAmber,
                                trailingIcon = Icons.Default.ChevronRight,
                                onClick = { navigator.navigate(QuickResponsesScreenDestination) }
                            )
                            RillSwitchListItem(
                                headline   = stringResource(R.string.busy_mode),
                                supporting = stringResource(R.string.busy_mode_subtitle),
                                leadingIcon = Icons.Outlined.DoNotDisturbOn,
                                iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkRed,
                                iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorRed,
                                checked = busyMode,
                                onCheckedChange = { enable ->
                                    if (enable) {
                                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
                                            smsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
                                        } else {
                                            busyMode = true
                                            prefs.setBoolean(PreferenceManager.KEY_BUSY_MODE_ENABLED, true)
                                        }
                                    } else {
                                        busyMode = false
                                        prefs.setBoolean(PreferenceManager.KEY_BUSY_MODE_ENABLED, false)
                                    }
                                }
                            )
                            if (busyMode) {
                                RillListItem(
                                    headline = stringResource(R.string.busy_mode_message),
                                    supporting = busyMessage.ifEmpty { defaultBusyMsg },
                                    leadingIcon = Icons.Outlined.Edit,
                                    iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkAmber,
                                    iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorAmber,
                                    trailingIcon = Icons.Default.ChevronRight,
                                    onClick = {
                                        editingBusyMsgText = busyMessage
                                        showBusyMessageDialog = true
                                    }
                                )
                            }
                            RillSwitchListItem(
                                headline   = stringResource(R.string.proximity_sensor),
                                supporting = stringResource(R.string.proximity_sensor_subtitle),
                                leadingIcon = Icons.Rounded.SpatialTracking,
                                iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkOrange,
                                iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorOrange,
                                checked = proximityBg,
                                onCheckedChange = {
                                    proximityBg = it
                                    prefs.setBoolean(PreferenceManager.KEY_PROXIMITY_SENSOR, it)
                                }
                            )
                            RillSwitchListItem(
                                headline   = stringResource(R.string.pocket_mode_prevention),
                                supporting = stringResource(R.string.pocket_mode_prevention_subtitle),
                                leadingIcon = Icons.Rounded.ScreenLockPortrait,
                                iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkOrange,
                                iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorOrange,
                                checked = pocketModePrevention,
                                onCheckedChange = {
                                    pocketModePrevention = it
                                    prefs.setBoolean(PreferenceManager.KEY_POCKET_MODE, it)
                                }
                            )
                            RillSwitchListItem(
                                headline   = stringResource(R.string.floating_ongoing_call),
                                supporting = stringResource(R.string.floating_ongoing_call_subtitle),
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
                                headline   = stringResource(R.string.fullscreen_calls),
                                supporting = stringResource(R.string.fullscreen_calls_subtitle),
                                leadingIcon = Icons.Rounded.CropFree,
                                iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkGreen,
                                iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorGreen,
                                checked = fullscreenCalls,
                                onCheckedChange = {
                                    fullscreenCalls = it
                                    prefs.setBoolean(PreferenceManager.KEY_ALWAYS_FULLSCREEN_CALLS, it)
                                }
                            )
                            RillSwitchListItem(
                                headline   = stringResource(R.string.direct_call_on_tap),
                                supporting = stringResource(R.string.direct_call_on_tap_subtitle),
                                leadingIcon = Icons.Rounded.TouchApp,
                                iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkGreen,
                                iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorGreen,
                                checked = directCallOnTap,
                                onCheckedChange = {
                                    directCallOnTap = it
                                    prefs.setBoolean(PreferenceManager.KEY_DIRECT_CALL_ON_TAP, it)
                                }
                            )
//                            RillSwitchListItem(
//                                headline   = stringResource(R.string.auto_speaker),
//                                supporting = stringResource(R.string.auto_speaker_subtitle),
//                                leadingIcon = Icons.AutoMirrored.Rounded.VolumeUp,
//                                iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkAmber,
//                                iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorAmber,
//                                checked = autoSpeaker,
//                                onCheckedChange = {
//                                    autoSpeaker = it
//                                    prefs.setBoolean(PreferenceManager.KEY_AUTO_SPEAKER, it)
//                                }
//                            )
                        }
                    }
                }
            }

            item { SettingsBottomPadding() }
        }

        if (showBusyMessageDialog) {
            RillDialog(
                onDismissRequest = { showBusyMessageDialog = false },
                title = stringResource(R.string.edit_busy_message),
                icon = Icons.Outlined.Edit,
                confirmButton = {
                    TextButton(onClick = {
                        val trimmed = editingBusyMsgText.trim()
                        val finalMsg = if (trimmed.isNotEmpty()) trimmed else defaultBusyMsg
                        busyMessage = finalMsg
                        prefs.setString(PreferenceManager.KEY_BUSY_MODE_MESSAGE, finalMsg)
                        showBusyMessageDialog = false
                    }) {
                        Text(
                            stringResource(R.string.save),
                            textAlign = TextAlign.End,
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showBusyMessageDialog = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            ) {
                OutlinedTextField(
                    value = editingBusyMsgText,
                    onValueChange = { editingBusyMsgText = it },
                    label = { Text(stringResource(R.string.busy_mode_message)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    maxLines = 5
                )
            }
        }
    }
}

@Composable
fun getSimSelectionList(askFirstResId: Int): List<Pair<String, Int>> {
    val context = LocalContext.current
    val askFirstLabel = stringResource(askFirstResId)

    val finalList = mutableListOf<Pair<String, Int>>()
    finalList.add(askFirstLabel to 0)

    val hasReadPhoneState = ContextCompat.checkSelfPermission(
        context, Manifest.permission.READ_PHONE_STATE
    ) == PackageManager.PERMISSION_GRANTED

    if (!hasReadPhoneState) {
        return remember { finalList }
    }

    return remember {
        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
        if (telecomManager != null) {
            try {
                val rawAccounts = telecomManager.callCapablePhoneAccounts
                val seen = HashSet<String>()

                val validAccounts = rawAccounts.filter { handle ->
                    val info = try {
                        telecomManager.getPhoneAccount(handle)
                    } catch (e: Exception) {
                        null
                    }

                    val isSimAccount = info != null &&
                            info.isEnabled &&
                            info.hasCapabilities(PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION)

                    if (!isSimAccount) return@filter false

                    val key = info!!.label?.toString().orEmpty() + "|" + info.address?.toString().orEmpty()
                    seen.add(key)
                }

                validAccounts.forEachIndexed { index, _ ->
                    val info = try {
                        telecomManager.getPhoneAccount(validAccounts[index])
                    } catch (e: Exception) {
                        null
                    }

                    val simNumber = index + 1
                    val operatorName = info?.label?.toString()

                    val label = if (!operatorName.isNullOrEmpty()) {
                        "SIM $simNumber: $operatorName"
                    } else {
                        "SIM $simNumber"
                    }

                    finalList.add(label to simNumber)
                }
            } catch (e: SecurityException) {
            }
        }

        finalList
    }
}
