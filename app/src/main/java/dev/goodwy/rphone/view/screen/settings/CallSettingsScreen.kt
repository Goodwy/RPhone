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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.R
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
import dev.goodwy.rphone.view.components.RillSelectListItem
import dev.goodwy.rphone.view.components.Title

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
    var defaultSim by remember { mutableStateOf(prefs.getInt(PreferenceManager.KEY_DEFAULT_SIM, prefs.getDefaultSimIndexDefault())) }

    var visible by remember { mutableStateOf(false) }
    val screenAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(350),
        label = "callSettingsAlpha"
    )
    LaunchedEffect(Unit) { visible = true }

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
            // ── Caller Accounts ───────────────────────────────────────────────
            item {
                RillAnimatedSection(delayMs = 0L) {
                    Column {
                        SettingsSectionLabel("SIM")
                        RillExpressiveCard {
                            RillSelectListItem(
                                headline = stringResource(R.string.default_sim),
                                leadingIcon = Icons.Rounded.SimCard,
                                iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkGreen,
                                iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorGreen,
                                options = listOf(
                                    stringResource(R.string.ask_first) to 0,
                                    "SIM 1" to 1,
                                    "SIM 2" to 2
                                ),
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
                            RillSwitchListItem(
                                headline   = stringResource(R.string.proximity_sensor),
                                supporting = stringResource(R.string.proximity_sensor_subtitle),
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
                                headline   = stringResource(R.string.pocket_mode_prevention),
                                supporting = stringResource(R.string.pocket_mode_prevention_subtitle),
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

            item { Spacer(modifier = Modifier.height(80.dp).navigationBarsPadding()) }
        }
    }
}
