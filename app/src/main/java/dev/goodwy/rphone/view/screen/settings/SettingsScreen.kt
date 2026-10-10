package dev.goodwy.rphone.view.screen.settings

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.os.Build
import android.view.Surface
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallMerge
import androidx.compose.material.icons.automirrored.rounded.CallSplit
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.BubbleChart
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LogoDev
import androidx.compose.material.icons.rounded.MoveUp
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PeopleAlt
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.StarRate
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.VolunteerActivism
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.BackupManager
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.controller.util.getAppVersion
import dev.goodwy.rphone.view.components.NavigationIcon
import dev.goodwy.rphone.view.components.RillAnimatedSection
import dev.goodwy.rphone.view.components.RillExpressiveCard
import dev.goodwy.rphone.view.components.RillListItem
import dev.goodwy.rphone.view.components.ScrollHapticsEffect
import dev.goodwy.rphone.view.components.SupportProjectItem
import dev.goodwy.rphone.view.theme.customColors
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.*
import com.ramcosta.composedestinations.generated.destinations.CallSettingScreenDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.goodwy.rphone.BuildConfig
import dev.goodwy.rphone.GITHUB_URL
import dev.goodwy.rphone.GP_DEV_URL
import dev.goodwy.rphone.PRIVACY_POLICY
import dev.goodwy.rphone.SITE_URL
import dev.goodwy.rphone.bottomBarHeight
import dev.goodwy.rphone.controller.ContactsViewModel
import dev.goodwy.rphone.controller.PurchaseHelper
import dev.goodwy.rphone.controller.util.ContactUtils.getAccountIcon
import dev.goodwy.rphone.controller.util.openLink
import dev.goodwy.rphone.view.components.Title
import dev.goodwy.rphone.view.components.shake
import dev.goodwy.rphone.view.theme.MyColors.cardColor
import dev.goodwy.rphone.view.theme.RillShapeDefaults
import dev.goodwy.rphone.view.theme.TabTransitionStyle
import dev.goodwy.rphone.view.theme.color_primary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinActivityViewModel
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>(style = TabTransitionStyle::class)
@Composable
fun SettingsScreen(navigator: DestinationsNavigator) {
    val context = LocalContext.current
    val appInfo = getAppVersion(context)
    val appVersion = appInfo.first
    val storeName = when (BuildConfig.FLAVOR) {
        "gplay" -> "GPlay"
        "rustore" -> "RuStore"
        else -> "FOSS"
    }

    val listState = rememberLazyListState()
    val prefs: PreferenceManager = koinInject()
    val settingsState by prefs.settingsChanged.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    val cardCorner  = remember(settingsState) { prefs.getInt(PreferenceManager.KEY_CARD_ROUNDNESS, RillShapeDefaults.DefaultRoundness) }
    val favoritesEnabled = prefs.getBoolean(PreferenceManager.KEY_TAB_SHOW_FAVORITES, false)
    val contactsEnabled = prefs.getBoolean(PreferenceManager.KEY_TAB_SHOW_CONTACTS, true)
    val dialpadEnabled = prefs.getBoolean(PreferenceManager.KEY_TAB_SHOW_DIALPAD, true)
    val notesEnabled = prefs.getBoolean(PreferenceManager.KEY_TAB_SHOW_NOTES, false)
    val searchEnabled = prefs.getBoolean(PreferenceManager.KEY_TAB_SHOW_SEARCH, false)
    val settingsEnabled = prefs.getBoolean(PreferenceManager.KEY_TAB_SHOW_SETTINGS, true)
    val showBottomBar = favoritesEnabled || contactsEnabled || dialpadEnabled ||notesEnabled || searchEnabled || settingsEnabled

    val purchaseHelper: PurchaseHelper = koinInject()
    val isPro by purchaseHelper.isPro.collectAsStateWithLifecycle()
    val proCheckDone by purchaseHelper.proCheckDone.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        val savedIsProIap = prefs.getBoolean(PreferenceManager.KEY_IS_PRO_IAP, false)
        val savedIsProSub = prefs.getBoolean(PreferenceManager.KEY_IS_PRO_SUB, false)
        val savedIsProFoss = prefs.getBoolean(PreferenceManager.KEY_IS_PRO_FOSS, false)
        if (savedIsProIap || savedIsProSub || savedIsProFoss) {
            purchaseHelper.setProStatusImmediate(true)
            purchaseHelper.checkProStatus()
        } else {
            purchaseHelper.checkProStatus()
        }
    }
    var enabledShake by remember { mutableStateOf(false) }
    var showSnackbar   by remember(settingsState) { mutableStateOf(false) }

    var backupState       by remember { mutableStateOf<BackupDialogState>(BackupDialogState.Idle) }

    var visible by remember { mutableStateOf(false) }
    var isClosing by remember { mutableStateOf(false) }

    fun navigateBack() {
        isClosing = true
        scope.launch {
            delay(280.milliseconds)
            navigator.navigateUp()
        }
    }

    val alpha by animateFloatAsState(
        targetValue = if (visible && !isClosing) 1f else 0f,
        animationSpec = if (isClosing) tween(280, easing = FastOutLinearInEasing) else tween(350),
        label = "settingsAlpha"
    )
    val offsetY by animateDpAsState(
        targetValue = if (visible && !isClosing) 0.dp else if (isClosing) 60.dp else 30.dp,
        animationSpec = if (isClosing) tween(300, easing = FastOutLinearInEasing)
                        else spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioLowBouncy),
        label = "settingsOffsetY"
    )
    LaunchedEffect(Unit) { visible = true }

    // Restore file picker
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                backupState = BackupDialogState.Restoring
                try {
                    val tmpFile = File(context.cacheDir, "restore_tmp.rphone")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        tmpFile.outputStream().use { output -> input.copyTo(output) }
                    }
                    val ok = BackupManager.restoreBackup(context, tmpFile)
                    tmpFile.delete()
                    backupState = if (ok) BackupDialogState.RestoreSuccess else BackupDialogState.Error("Restore failed")
                } catch (e: Exception) {
                    backupState = BackupDialogState.Error(e.message ?: "Unknown error")
                }
            }
        }
    }

    // Default dialer
    val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as android.telecom.TelecomManager
    var isDefaultDialer by remember { mutableStateOf(telecomManager.defaultDialerPackage == context.packageName) }
    val defaultDialerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        isDefaultDialer = telecomManager.defaultDialerPackage == context.packageName
    }
    val activity = context as? Activity
    DisposableEffect(activity) {
        val lifecycleOwner = activity as? androidx.lifecycle.LifecycleOwner
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME)
                isDefaultDialer = telecomManager.defaultDialerPackage == context.packageName
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose { lifecycleOwner?.lifecycle?.removeObserver(observer) }
    }

    // ── Backup Dialogs ────────────────────────────────────────────────────────
    when (val state = backupState) {
        is BackupDialogState.Restoring -> Dialog(onDismissRequest = {}) {
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.restoring_backup), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        is BackupDialogState.BackupSuccess -> AlertDialog(onDismissRequest = { backupState = BackupDialogState.Idle }, icon = { Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.customColors.colorDarkGreen) }, title = { Text(stringResource(R.string.backup_created)) }, text = { Text(stringResource(R.string.backup_created_description, state.path)) }, confirmButton = { TextButton(onClick = { backupState = BackupDialogState.Idle }) { Text(stringResource(R.string.ok)) } })
        is BackupDialogState.RestoreSuccess -> AlertDialog(onDismissRequest = { backupState = BackupDialogState.Idle }, icon = { Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.customColors.colorDarkGreen) }, title = { Text(stringResource(R.string.restore_complete)) }, text = { Text(stringResource(R.string.restore_complete_description)) }, confirmButton = { TextButton(onClick = { backupState = BackupDialogState.Idle }) { Text(stringResource(R.string.ok)) } })
        is BackupDialogState.Error -> AlertDialog(onDismissRequest = { backupState = BackupDialogState.Idle }, icon = { Icon(Icons.Rounded.Error, null, tint = MaterialTheme.colorScheme.error) }, title = { Text(stringResource(R.string.operation_failed)) }, text = { Text(state.message) }, confirmButton = { TextButton(onClick = { backupState = BackupDialogState.Idle }) { Text(stringResource(R.string.ok)) } })
        else -> {}
    }

    // Create backup file picker
    val failedToCreateBackup = stringResource(R.string.failed_to_create_backup)
    val createBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                backupState = BackupDialogState.Restoring
                try {
                    // 1. Creating a backup in the app cache
                    val backupFile = BackupManager.createBackup(context)

                    if (backupFile != null) {
                        // 2. Copy the created file to the user-selected location
                        context.contentResolver.openOutputStream(uri)?.use { output ->
                            backupFile.inputStream().use { input -> input.copyTo(output) }
                        }
                        backupState = BackupDialogState.BackupSuccess(uri.toString())
                    } else {
                        backupState = BackupDialogState.Error(failedToCreateBackup)
                    }

                    // 3. Delete a temporary file from the cache
                    backupFile?.delete()
                } catch (e: Exception) {
                    backupState = BackupDialogState.Error(e.message ?: "Unknown error")
                }
            }
        }
    }
    fun createBackup() {
        val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
        val defaultFileName = "RPhone_Backup_$timestamp.rphone"
        createBackupLauncher.launch(defaultFileName)
    }

    fun restoreBackup() {
        restoreLauncher.launch("*/*")
    }


    val viewModel: ContactsViewModel = koinActivityViewModel()
    val trashedContacts by viewModel.trashedContacts.collectAsState()
    LaunchedEffect(Unit) {
        viewModel.fetchTrashedContacts()
    }
    var trashEnabled by remember(settingsState) { mutableStateOf(prefs.isContactsTrashEnabled()) }

    // ── Search in Settings ─────────────────────────────────────────────────
    var settingsSearchQuery by remember { mutableStateOf("") }
    val isGPlay = BuildConfig.FLAVOR == "gplay"
    val isFoss = BuildConfig.FLAVOR == "foss"
    val settingsSearchEntries = listOf(
        SettingsSearchEntry(
            headline = stringResource(R.string.interface_settings),
            supporting = stringResource(R.string.interface_settings_subtitle),
            leadingIcon = Icons.Rounded.Palette,
            iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkCyan,
            iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorCyan,
            options = listOf(
                stringResource(R.string.appearance),
                stringResource(R.string.app_theme),
                stringResource(R.string.theme_auto),
                stringResource(R.string.theme_light),
                stringResource(R.string.theme_dark),
                stringResource(R.string.theme_auto_black_white),
                stringResource(R.string.theme_white),
                stringResource(R.string.theme_black),
                stringResource(R.string.custom_color),
                stringResource(R.string.custom_color_subtitle),
                stringResource(R.string.hex_color),
                stringResource(R.string.hex_color_subtitle),
                stringResource(R.string.custom_font),
                stringResource(R.string.custom_font_subtitle),
                stringResource(R.string.font_size),
                stringResource(R.string.rounding_radius),
                stringResource(R.string.app_name_change),
                stringResource(R.string.visual_effects),
                stringResource(R.string.not_supported_on_this_device),
                stringResource(R.string.not_supported_on_this_device_subtitle),
                stringResource(R.string.material_liquid_you_glass),
                stringResource(R.string.material_liquid_you_glass_subtitle),
                stringResource(R.string.material_blur_effects),
                stringResource(R.string.material_blur_effects_subtitle),
                stringResource(R.string.blur_intensity),
                stringResource(R.string.liquid_glass_elements),
                stringResource(R.string.blur_effect_elements),
                stringResource(R.string.bottom_navigation_bar),
                stringResource(R.string.call_screen),
                stringResource(R.string.scroll_animation),
                stringResource(R.string.scroll_animation_device),
                stringResource(R.string.dialpad_animations),
                stringResource(R.string.dialpad_animations_subtitle),
                stringResource(R.string.call_ui),
                stringResource(R.string.incoming_call_ui),
                stringResource(R.string.incoming_call_ui_subtitle),
                stringResource(R.string.incoming_call_ui_default_swipe),
                stringResource(R.string.incoming_call_ui_horizontal_swipe),
                stringResource(R.string.incoming_call_ui_buttons),
                stringResource(R.string.incoming_call_ui_slide_to_answer),
                stringResource(R.string.incoming_call_ui_vertical_swipe),
                stringResource(R.string.hide_voice_search),
                stringResource(R.string.hide_voice_search_subtitle),
                stringResource(R.string.swipe_actions),
                stringResource(R.string.swipe_actions_subtitle),
            )
        ) { navigator.navigate(InterfaceScreenDestination) },
        SettingsSearchEntry(
            headline = stringResource(R.string.app_icon),
            supporting = stringResource(R.string.app_icon_subtitle),
            leadingIcon = ImageVector.vectorResource(id = R.drawable.ic_phone),
            iconContainerColor = color_primary,
            iconBgContainerColor = Color.White,
        ) { navigator.navigate(AppIconScreenDestination) },
        SettingsSearchEntry(
            headline = stringResource(R.string.liquid_glass_elements),
            supporting = stringResource(R.string.liquid_glass_elements_subtitle),
            leadingIcon = Icons.Rounded.Layers,
            iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkPink,
            iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorPink,
            options = listOf(
                stringResource(R.string.bottom_navigation_bar),
                stringResource(R.string.call_screen),
                stringResource(R.string.keypad),
            )
        ) { navigator.navigate(LiquidGlassElementsScreenDestination) },
        SettingsSearchEntry(
            headline = stringResource(R.string.blur_effect_elements),
            supporting = stringResource(R.string.blur_effect_elements_subtitle),
            leadingIcon = Icons.Rounded.Layers,
            iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkOrange,
            iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorOrange,
            options = listOf(
                stringResource(R.string.bottom_navigation_bar),
                stringResource(R.string.call_screen),
                stringResource(R.string.keypad),
            )
        ) { navigator.navigate(BlurEffectsElementsScreenDestination) },
        SettingsSearchEntry(
            headline = stringResource(R.string.сaller_ui),
            supporting = stringResource(R.string.сaller_ui_subtitle),
            leadingIcon = Icons.Rounded.Person,
            iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkGreen,
            iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorGreen,
            options = listOf(
                stringResource(R.string.settings_call_default_background),
                stringResource(R.string.end_call_button),
                stringResource(R.string.customize_width),
                stringResource(R.string.customize_width_subtitle),
            )
        ) { navigator.navigate(CallerUIScreenDestination) },
        SettingsSearchEntry(
            headline = stringResource(R.string.avatars_settings),
            supporting = stringResource(R.string.avatars_settings_subtitle),
            leadingIcon = Icons.Rounded.AccountCircle,
            iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkBlue,
            iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorBlue,
            options = listOf(
                stringResource(R.string.avatar_colors),
                stringResource(R.string.colorful),
                stringResource(R.string.primary_color),
                stringResource(R.string.secondary_color),
                stringResource(R.string.google_contacts_color),
                stringResource(R.string.show_first_letter_in_avatar),
                stringResource(R.string.show_first_letter_in_avatar_subtitle),
                stringResource(R.string.avatar_frame),
                stringResource(R.string.avatar_frame_subtitle),
                stringResource(R.string.show_picture_in_avatar),
                stringResource(R.string.show_picture_in_avatar_subtitle),
                stringResource(R.string.settings_interface_hide_avatar_with_bg),
                stringResource(R.string.settings_interface_hide_avatar_with_bg_supporting),
            )
        ) { navigator.navigate(AvatarsPreferenceScreenDestination) },
        SettingsSearchEntry(
            headline = stringResource(R.string.navigations),
            supporting = stringResource(R.string.navigations_subtitle),
            leadingIcon = Icons.Rounded.MoveUp,
            modifierLeadingIcon = Modifier.rotate(90f),
            iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkCyan,
            iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorCyan,
            options = listOf(
                stringResource(R.string.tab_sections),
                stringResource(R.string.tab_sections_subtitle),
                stringResource(R.string.navigation_style),
                stringResource(R.string.pill_style_navigation),
                stringResource(R.string.pill_style_navigation_subtitle),
                stringResource(R.string.icon_only_bottom_bar),
                stringResource(R.string.icon_only_bottom_bar_subtitle),
            )
        ) { navigator.navigate(NavigationScreenDestination) },
        SettingsSearchEntry(
            headline = stringResource(R.string.call_settings),
            supporting = stringResource(R.string.call_settings_subtitle),
            leadingIcon = Icons.Rounded.Call,
            iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkGreen,
            iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorGreen,
            options = listOf(
                stringResource(R.string.calls_and_system),
                stringResource(R.string.default_sim),
                stringResource(R.string.ask_first),
                stringResource(R.string.calling_accounts),
                stringResource(R.string.call_behavior),
                stringResource(R.string.busy_mode),
                stringResource(R.string.busy_mode_subtitle),
                stringResource(R.string.proximity_sensor),
                stringResource(R.string.proximity_sensor_subtitle),
                stringResource(R.string.pocket_mode_prevention),
                stringResource(R.string.pocket_mode_prevention_subtitle),
                stringResource(R.string.floating_ongoing_call),
                stringResource(R.string.floating_ongoing_call_subtitle),
                stringResource(R.string.direct_call_on_tap),
                stringResource(R.string.direct_call_on_tap_subtitle),
                stringResource(R.string.fullscreen_calls),
                stringResource(R.string.fullscreen_calls_subtitle),
            )
        ) { navigator.navigate(CallSettingScreenDestination) },
        SettingsSearchEntry(
            headline = stringResource(R.string.settings_speed_dial_title),
            supporting = stringResource(R.string.settings_speed_dial_enable_supporting),
            leadingIcon = Icons.Default.Dialpad,
            iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkIndigo,
            iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorIndigo,
            options = listOf(
                stringResource(R.string.settings_speed_dial_enable),
                stringResource(R.string.settings_speed_dial_assignments_header),
                stringResource(R.string.settings_speed_dial_not_assigned),
            )
        ) { navigator.navigate(SpeedDialScreenDestination) },
        SettingsSearchEntry(
            headline = stringResource(R.string.quick_responses),
            supporting = stringResource(R.string.quick_responses_subtitle),
            leadingIcon = ImageVector.vectorResource(id = R.drawable.ic_message_filled),
            iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkAmber,
            iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorAmber,
            options = listOf(
                stringResource(R.string.quick_responses_description),
                stringResource(R.string.add_response),
                stringResource(R.string.edit_response),
                stringResource(R.string.default_quick_responses_1),
                stringResource(R.string.default_quick_responses_2),
                stringResource(R.string.default_quick_responses_3),
                stringResource(R.string.default_quick_responses_4),
            )
        ) { navigator.navigate(QuickResponsesScreenDestination) },
        SettingsSearchEntry(
            headline = stringResource(R.string.sound_and_vibration),
            supporting = stringResource(R.string.sound_and_vibration_subtitle),
            leadingIcon = Icons.AutoMirrored.Rounded.VolumeUp,
            iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkAmber,
            iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorAmber,
            options = listOf(
                stringResource(R.string.tap_haptics),
                stringResource(R.string.enable_tap_haptics),
                stringResource(R.string.haptics_intensity),
                stringResource(R.string.haptics_soft),
                stringResource(R.string.haptics_strong),
                stringResource(R.string.haptics_custom),
                stringResource(R.string.preview_haptic),
                stringResource(R.string.ringtone_settings),
                stringResource(R.string.dialpad_tones),
                stringResource(R.string.dialpad_tones_subtitle),
                stringResource(R.string.vibrate_on_answer),
                stringResource(R.string.vibrate_on_answer_subtitle),
                stringResource(R.string.vibrate_on_hang_up),
                stringResource(R.string.vibrate_on_hang_up_subtitle),
                stringResource(R.string.haptics_across_app),
                stringResource(R.string.scroll_haptics),
                stringResource(R.string.scroll_haptics_subtitle),
                stringResource(R.string.haptic_interval),
                stringResource(R.string.haptic_interval_value),
                stringResource(R.string.haptic_strength),
            )
        ) { navigator.navigate(SoundVibrationScreenDestination) },
        SettingsSearchEntry(
            headline = stringResource(R.string.manage_blocked),
            supporting = stringResource(R.string.manage_blocked_subtitle),
            leadingIcon = Icons.Outlined.DoDisturb,
            iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkRed,
            iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorRed,
            options = listOf(
                stringResource(R.string.blocked_numbers),
                stringResource(R.string.blocked_numbers_system_info),
                stringResource(R.string.log_visibility),
                stringResource(R.string.log_visibility_subtitle),
                stringResource(R.string.hide_from_logs),
                stringResource(R.string.show_in_logs),
                stringResource(R.string.blocked_size),
            )
        ) { navigator.navigate(BlockedNumbersScreenDestination) },
        SettingsSearchEntry(
            headline = stringResource(R.string.authentication),
            leadingIcon = Icons.Rounded.Fingerprint,
            iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkRed,
            iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorRed,
            options = listOf(
                stringResource(R.string.authentication_method),
                stringResource(R.string.system_biometrics),
                stringResource(R.string.system_biometrics_subtitle),
                stringResource(R.string.custom_biometrics),
                stringResource(R.string.pin),
                stringResource(R.string.pin_subtitle),
                stringResource(R.string.password),
                stringResource(R.string.password_subtitle),
                stringResource(R.string.remove_biometric),
                stringResource(R.string.lock_app_on_open),
                stringResource(R.string.lock_app_on_open_subtitle),
                stringResource(R.string.lock_call_actions),
                stringResource(R.string.lock_call_actions_subtitle),
                stringResource(R.string.lock_scope),
                stringResource(R.string.all_calls),
                stringResource(R.string.all_calls_subtitle),
                stringResource(R.string.blacklist),
                stringResource(R.string.blacklist_subtitle),
                stringResource(R.string.whitelist),
                stringResource(R.string.whitelist_subtitle),
                stringResource(R.string.lock_timeout),
                stringResource(R.string.immediately_on_exit),
                stringResource(R.string.after_1_minute),
            )
        ) { navigator.navigate(BiometricScreenDestination) },

        SettingsSearchEntry(
            headline = stringResource(R.string.settings_call_analytics_title),
            supporting = stringResource(R.string.settings_call_analytics_supporting),
            leadingIcon = Icons.Rounded.BubbleChart,
            iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkPurple,
            iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorPurple,
            options = listOf(
                stringResource(R.string.this_week),
                stringResource(R.string.this_month),
                stringResource(R.string.all_time),
                stringResource(R.string.total_talk_time),
                stringResource(R.string.avg_duration),
                stringResource(R.string.call_volume),
                stringResource(R.string.call_breakdown),
                stringResource(R.string.sim_usage_breakdown),
                stringResource(R.string.most_talked_persons),
            )
        ) {
            val isBlurSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            if (isPro || isBlurSupported) {
                navigator.navigate(CallAnalyticsScreenDestination)
            } else {
                enabledShake = true
                showSnackbar = true
                scope.launch {
                    delay(3000.milliseconds)
                    showSnackbar = false
                }
            }
        },
        SettingsSearchEntry(
            headline = stringResource(R.string.manage_contacts),
            supporting = stringResource(R.string.manage_contacts_subtitle),
            leadingIcon = Icons.Rounded.PeopleAlt,
            iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkBlue,
            iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorBlue,
            options = listOf(
                stringResource(R.string.display),
                stringResource(R.string.sort_by),
                stringResource(R.string.name_format),
                stringResource(R.string.first_name_first),
                stringResource(R.string.last_name_first),
                stringResource(R.string.merge_and_fix),
                stringResource(R.string.standardize_phone_numbers),
                stringResource(R.string.standardize_phone_numbers_subtitle),
                stringResource(R.string.settings_contacts_trash_title),
                stringResource(R.string.settings_contacts_trash_supporting),
            )
        ) { navigator.navigate(ContactManagementScreenDestination) },
        SettingsSearchEntry(
            headline = stringResource(R.string.contacts_trash_title),
            supporting = stringResource(R.string.settings_contacts_trash_view_supporting, trashedContacts.size),
            leadingIcon = ImageVector.vectorResource(id = R.drawable.ic_delete),
            iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkRed,
            iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorRed,
            options = listOf(
                stringResource(R.string.contacts_trash_restore_action),
                stringResource(R.string.contacts_trash_delete_action),
            )
        ) { navigator.navigate(TrashContactsScreenDestination) },
        SettingsSearchEntry(
            headline = stringResource(R.string.merging_contacts),
            supporting = stringResource(R.string.merging_contacts_subtitle),
            leadingIcon = Icons.AutoMirrored.Rounded.CallMerge,
            iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkOrange,
            iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorOrange,
        ) { navigator.navigate(ContactMergeDuplicatesScreenDestination) },
        SettingsSearchEntry(
            headline = stringResource(R.string.unmerging_contacts),
            supporting = stringResource(R.string.unmerging_contacts_subtitle),
            leadingIcon = Icons.AutoMirrored.Rounded.CallSplit,
            iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkOrange,
            iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorOrange,
        ) { navigator.navigate(ContactUnmergeDuplicatesScreenDestination) },
        SettingsSearchEntry(
            headline = stringResource(R.string.managing_contact_sources),
            supporting = stringResource(R.string.managing_contact_sources_subtitle),
            leadingIcon = Icons.Rounded.PeopleAlt,
            iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkBlue,
            iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorBlue,
            options = listOf(
                stringResource(R.string.managing_contact_sources_description),
                stringResource(R.string.contacts_stored_on_device),
            )
        ) { navigator.navigate(ContactVisibilityScreenDestination) },
        SettingsSearchEntry(
            headline = stringResource(R.string.private_contacts),
            supporting = stringResource(R.string.private_contacts_subtitle),
            leadingIcon = getAccountIcon(null, true),
            iconContainerColor = MaterialTheme.colorScheme.onTertiaryContainer,
            iconBgContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
            options = listOf(
                stringResource(R.string.import_text),
                stringResource(R.string.export_text),
            )
        ) { navigator.navigate(PrivateContactsScreenDestination) },
        SettingsSearchEntry(
            headline = stringResource(R.string.support_development),
            supporting = stringResource(R.string.support_development_description3),
            leadingIcon = Icons.Rounded.VolunteerActivism,
            iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkPurple,
            iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorPurple,
            options = listOf(
                stringResource(R.string.unlock_all_features),
                stringResource(R.string.support_project_to_unlock),
                stringResource(R.string.your_donation_ensures),
            )
        ) { navigator.navigate(DonateScreenDestination) },
        SettingsSearchEntry(
            headline = stringResource(R.string.create_backup),
            supporting = stringResource(R.string.create_backup_subtitle),
            leadingIcon = Icons.Rounded.Backup,
            iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkPink,
            iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorPink,
        ) { createBackup() },
        SettingsSearchEntry(
            headline = stringResource(R.string.restore_backup),
            supporting = stringResource(R.string.restore_backup_subtitle),
            leadingIcon = Icons.Rounded.Restore,
            iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkPurple,
            iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorPurple,
        ) { restoreBackup() },
        SettingsSearchEntry(
            headline = stringResource(R.string.about),
            supporting = "Version $appVersion ($storeName)",
            leadingIcon = Icons.Outlined.Info,
            iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkOliva,
            iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorOliva,
        ) { navigator.navigate(AboutAppScreenDestination) },
        SettingsSearchEntry(
            headline = stringResource(R.string.other_apps),
            leadingIcon = if (isGPlay) ImageVector.vectorResource(id = R.drawable.ic_google_play_vector) else ImageVector.vectorResource(id = R.drawable.ic_goodwy),
            iconContainerColor = Color.Black,
            iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorOliva,
        ) { openLink(context, if (isGPlay) GP_DEV_URL else SITE_URL) },
        SettingsSearchEntry(
            headline = stringResource(R.string.source_code),
            supporting = "GitHub Repository",
            leadingIcon = ImageVector.vectorResource(id = R.drawable.ic_github_vector),
            iconContainerColor = Color.Black,
            iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorOliva,
        ) { openLink(context, GITHUB_URL) },
        SettingsSearchEntry(
            headline = stringResource(R.string.privacy_policy),
            leadingIcon = Icons.Rounded.PrivacyTip,
            iconContainerColor = Color.Black,
            iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorOliva,
        ) { openLink(context, PRIVACY_POLICY) },
        SettingsSearchEntry(
            headline = stringResource(R.string.contributors),
            leadingIcon = Icons.Rounded.LogoDev,
            iconContainerColor = Color.Black,
            iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorOliva,
            options = listOf(
                stringResource(R.string.development_team),
            )
        ) { navigator.navigate(ContributorsScreenDestination) },
    )

    val settingsSearchEntriesFinal = if (isGPlay) {
        settingsSearchEntries + SettingsSearchEntry(
            headline = stringResource(R.string.rate_app),
            leadingIcon = Icons.Rounded.StarRate,
            iconContainerColor = Color.Black,
            iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorOliva,
        ) { navigator.navigate(AboutAppScreenDestination) }
    } else if (isFoss) {
        settingsSearchEntries + SettingsSearchEntry(
            headline = stringResource(R.string.updates),
            supporting = stringResource(R.string.check_for_updates_subtitle),
            leadingIcon = Icons.Rounded.SystemUpdate,
            iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkBlue,
            iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorBlue,
            options = listOf(
                stringResource(R.string.download_update),
            )
        ) { navigator.navigate(UpdatesScreenDestination) }
    } else {
        settingsSearchEntries
    }
    val filteredSettingsResults = if (settingsSearchQuery.isBlank()) emptyList()
    else settingsSearchEntriesFinal.filter {
        it.headline.contains(settingsSearchQuery, ignoreCase = true) ||
                (it.supporting ?: "").contains(settingsSearchQuery, ignoreCase = true) ||
                it.options.any { option ->
                    option.contains(settingsSearchQuery, ignoreCase = true)
                }
    }

    // ── Screen ────────────────────────────────────────────────────────────────
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
                title = { Title(stringResource(R.string.settings)) },
                navigationIcon = {
                    NavigationIcon(onClick = { navigateBack() })
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        BackHandler { navigateBack() }
        ScrollHapticsEffect(listState = listState)
        Box(modifier = Modifier
//            .padding(padding)
            .padding(
                top = padding.calculateTopPadding(),
                start = 0.dp,
                end = 0.dp,
                bottom = 0.dp
            )
            .fillMaxSize()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(alpha)
                    .offset(y = offsetY),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    // Search bar
                    val shape =
                        if (cardCorner > 12) CircleShape else MaterialTheme.shapes.extraExtraLarge
                    Surface(
                        shape = shape,
                        color = cardColor,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextField(
                            value = settingsSearchQuery,
                            onValueChange = { settingsSearchQuery = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text(stringResource(R.string.search)) },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = stringResource(R.string.search),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .padding(start = 12.dp)
                                )
                            },
                            trailingIcon = {
                                AnimatedVisibility(
                                    visible = settingsSearchQuery.isNotEmpty(),
                                    enter = fadeIn() + scaleIn(),
                                    exit = fadeOut() + scaleOut()
                                ) {
                                    IconButton(
                                        onClick = { settingsSearchQuery = "" },
                                        modifier = Modifier.padding(end = 4.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = stringResource(R.string.clear)
                                        )
                                    }
                                }
                            },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            singleLine = true
                        )
                    }
                }

                if (settingsSearchQuery.isNotBlank()) {
                    item {
                        if (filteredSettingsResults.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "No settings found for \"$settingsSearchQuery\"",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            RillExpressiveCard {
                                filteredSettingsResults.forEach { entry ->
                                    RillListItem(
                                        headline = entry.headline,
                                        supporting = entry.supporting,
                                        leadingIcon = entry.leadingIcon,
                                        modifierLeadingIcon = entry.modifierLeadingIcon,
                                        iconContainerColor = entry.iconContainerColor,
                                        iconBgContainerColor = entry.iconBgContainerColor,
                                        trailingIcon = Icons.Default.ChevronRight,
                                        onClick = {
                                            settingsSearchQuery = ""
                                            entry.onClick()
                                        }
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Spacer(
                            modifier = Modifier
                                .height(80.dp)
                                .navigationBarsPadding()
                        )
                    }
                } else {
                    if (!isPro && proCheckDone) {
                        item {
                            RillAnimatedSection(delayMs = 30L) {
                                RillExpressiveCard {
                                    SupportProjectItem(
                                        modifier = Modifier.shake(enabledShake) { enabledShake = false },
                                        onClick = { navigator.navigate(DonateScreenDestination) }
                                    )
                                }
                            }
                        }
                    }

                    // ── Appearance ───────────────────────────────────────────────────
                    item {
                        RillAnimatedSection(delayMs = 60L) {
                            Column {
                                SettingsSectionLabel(stringResource(R.string.appearance))
                                RillExpressiveCard {
                                    RillListItem(
                                        headline = stringResource(R.string.interface_settings),
                                        supporting = stringResource(R.string.interface_settings_subtitle),
                                        leadingIcon = Icons.Rounded.Palette,
                                        iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkCyan,
                                        iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorCyan,
                                        trailingIcon = Icons.Default.ChevronRight,
                                        onClick = { navigator.navigate(InterfaceScreenDestination) })
                                    RillListItem(
                                        headline = stringResource(R.string.navigations),
                                        supporting = stringResource(R.string.navigations_subtitle),
                                        leadingIcon = Icons.Rounded.MoveUp,
                                        modifierLeadingIcon = Modifier.rotate(90f),
                                        iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkCyan,
                                        iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorCyan,
                                        trailingIcon = Icons.Default.ChevronRight,
                                        onClick = { navigator.navigate(NavigationScreenDestination) })
                                }
                            }
                        }
                    }

                    // ── Calls & System ───────────────────────────────────────────────
                    item {
                        RillAnimatedSection(delayMs = 140L) {
                            Column {
                                SettingsSectionLabel(stringResource(R.string.calls_and_system))
                                RillExpressiveCard {
                                    RillListItem(
                                        headline = stringResource(R.string.call_settings),
                                        supporting = stringResource(R.string.call_settings_subtitle),
                                        leadingIcon = Icons.Rounded.Call,
                                        iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkGreen,
                                        iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorGreen,
                                        trailingIcon = Icons.Default.ChevronRight,
                                        onClick = { navigator.navigate(CallSettingScreenDestination) }
                                    )
                                    RillListItem(
                                        headline = stringResource(R.string.sound_and_vibration),
                                        supporting = stringResource(R.string.sound_and_vibration_subtitle),
                                        leadingIcon = Icons.AutoMirrored.Rounded.VolumeUp,
                                        iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkAmber,
                                        iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorAmber,
                                        trailingIcon = Icons.Default.ChevronRight,
                                        onClick = {
                                            navigator.navigate(
                                                SoundVibrationScreenDestination
                                            )
                                        }
                                    )
                                    RillListItem(
                                        headline = stringResource(R.string.manage_blocked),
                                        supporting = stringResource(R.string.manage_blocked_subtitle),
                                        leadingIcon = Icons.Outlined.DoDisturb,
                                        iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkRed,
                                        iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorRed,
                                        trailingIcon = Icons.Default.ChevronRight,
                                        onClick = {
                                            navigator.navigate(
                                                BlockedNumbersScreenDestination
                                            )
                                        }
                                    )
                                    val biometricsType =
                                        remember(prefs.settingsChanged.collectAsStateWithLifecycle().value) {
                                            prefs.getString(
                                                PreferenceManager.KEY_BIOMETRICS_TYPE,
                                                ""
                                            )
                                                ?: ""
                                        }
                                    val biometricsLabel = when (biometricsType) {
                                        "system" -> stringResource(R.string.system_biometrics)
                                        "pin" -> stringResource(R.string.custom_pin)
                                        "password" -> stringResource(R.string.custom_password)
                                        else -> stringResource(R.string.not_configured)
                                    }
                                    RillListItem(
                                        headline = stringResource(R.string.authentication),
                                        supporting = biometricsLabel,
                                        leadingIcon = Icons.Rounded.Fingerprint,
                                        iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkRed,
                                        iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorRed,
                                        trailingIcon = Icons.Default.ChevronRight,
                                        onClick = { navigator.navigate(BiometricScreenDestination) }
                                    )
                                    RillListItem(
                                        modifier = Modifier.shake(enabledShake) { enabledShake = false },
                                        headline = stringResource(R.string.settings_call_analytics_title),
                                        supporting = stringResource(R.string.settings_call_analytics_supporting),
                                        leadingIcon = Icons.Rounded.BubbleChart,
                                        iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkPurple,
                                        iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorPurple,
                                        trailingIcon = if (isPro) Icons.Default.ChevronRight else Icons.Rounded.Lock,
                                        onClick = {
                                            val isBlurSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                                            if (isPro || isBlurSupported) {
                                                navigator.navigate(CallAnalyticsScreenDestination)
                                            } else {
                                                enabledShake = true
                                                showSnackbar = true
                                                scope.launch {
                                                    delay(3000.milliseconds)
                                                    showSnackbar = false
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // ── Contacts ────────────────────────────────────────────────────────
                    item {
                        RillAnimatedSection(delayMs = 300L) {
                            Column {
                                SettingsSectionLabel(stringResource(R.string.contacts))
                                RillExpressiveCard {
                                    RillListItem(
                                        headline = stringResource(R.string.manage_contacts),
                                        supporting = stringResource(R.string.manage_contacts_subtitle),
                                        leadingIcon = Icons.Rounded.PeopleAlt,
                                        iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkBlue,
                                        iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorBlue,
                                        trailingIcon = Icons.Default.ChevronRight,
                                        onClick = {
                                            navigator.navigate(
                                                ContactManagementScreenDestination
                                            )
                                        })
                                }
                            }
                        }
                    }

                    // ── Backup & Restore ─────────────────────────────────────────────
                    item {
                        RillAnimatedSection(delayMs = 260L) {
                            Column {
                                SettingsSectionLabel(stringResource(R.string.backup_and_restore))
                                RillExpressiveCard {
                                    RillListItem(
                                        headline = stringResource(R.string.create_backup),
                                        supporting = stringResource(R.string.create_backup_subtitle),
                                        leadingIcon = Icons.Rounded.Backup,
                                        iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkPink,
                                        iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorPink,
                                        trailingIcon = Icons.Default.ChevronRight,
                                        onClick = { createBackup() }
                                    )
                                    RillListItem(
                                        headline = stringResource(R.string.restore_backup),
                                        supporting = stringResource(R.string.restore_backup_subtitle),
                                        leadingIcon = Icons.Rounded.Restore,
                                        iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkPink,
                                        iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorPink,
                                        trailingIcon = Icons.Default.ChevronRight,
                                        onClick = { restoreBackup() })
                                }
                            }
                        }
                    }

                    // ── Other ────────────────────────────────────────────────────────
                    item {
                        RillAnimatedSection(delayMs = 300L) {
                            Column {
                                SettingsSectionLabel(stringResource(R.string.other))
                                RillExpressiveCard {
                                    if (isPro) {
                                        RillListItem(
                                            headline = stringResource(R.string.support_development),
                                            supporting = stringResource(R.string.support_development_description3),
                                            leadingIcon = Icons.Rounded.VolunteerActivism,
                                            iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkPurple,
                                            iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorPurple,
                                            trailingIcon = Icons.Default.ChevronRight,
                                            onClick = { navigator.navigate(DonateScreenDestination) })
                                    }
                                    val isFoss = BuildConfig.FLAVOR == "foss"
                                    if (isFoss) RillListItem(
                                        headline = stringResource(R.string.updates),
                                        supporting = stringResource(R.string.check_for_updates_subtitle),
                                        leadingIcon = Icons.Rounded.SystemUpdate,
                                        iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkOliva,
                                        iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorOliva,
                                        trailingIcon = Icons.Default.ChevronRight,
                                        onClick = {
                                            navigator.navigate(UpdatesScreenDestination)
                                        }
                                    )
                                    RillListItem(
                                        headline = stringResource(R.string.about),
                                        supporting = "Version $appVersion ($storeName)",
                                        leadingIcon = Icons.Outlined.Info,
                                        iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkOliva,
                                        iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorOliva,
                                        trailingIcon = Icons.Default.ChevronRight,
                                        onClick = { navigator.navigate(AboutAppScreenDestination) })
                                }
                            }
                        }
                    }

                    item { SettingsBottomPadding(120.dp) }
                }
            }

            AnimatedVisibility(
                modifier = Modifier
                    .align(Alignment.BottomCenter),
                visible = showSnackbar,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                Snackbar(
                    modifier = Modifier.navigationBarsPadding().padding(24.dp).padding(bottom = if (showBottomBar) bottomBarHeight else 0.dp),
                    shape = MaterialTheme.shapes.large,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    action = {
                        TextButton(
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                            onClick = {
                                showSnackbar = false
                                navigator.navigate(DonateScreenDestination)
                            }
                        ) {
                            Text(
                                stringResource(R.string.continue_support),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                ) {
                    Text(
                        stringResource(R.string.support_project_to_unlock),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

private data class SettingsSearchEntry(
    val headline: String,
    val supporting: String? = null,
    val leadingIcon: ImageVector? = null,
    val modifierLeadingIcon: Modifier = Modifier,
    val iconContainerColor: Color? = null,
    val iconBgContainerColor: Color? = null,
    val options: List<String> = emptyList(),
    val onClick: () -> Unit
)

private sealed class BackupDialogState {
    object Idle : BackupDialogState()
    object Restoring : BackupDialogState()
    data class BackupSuccess(val path: String) : BackupDialogState()
    object RestoreSuccess : BackupDialogState()
    data class Error(val message: String) : BackupDialogState()
}

@Composable
fun SettingsSectionLabel(
    text: String,
    modifier: Modifier = Modifier.padding(start = 20.dp, bottom = 8.dp)
) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        modifier = modifier,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
fun SettingsBottomPadding(padding: Dp = 36.dp) {
    Spacer(modifier = Modifier.height(padding).navigationBarsPadding())
}
