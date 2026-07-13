package dev.goodwy.rphone.view.screen.settings

import android.app.Activity
import android.app.DownloadManager

import android.content.Context
import android.net.Uri
import android.view.Surface
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.MoveUp
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PeopleAlt
import androidx.compose.material.icons.rounded.VolunteerActivism
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.BackupManager
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.controller.util.enqueueApkDownload
import dev.goodwy.rphone.controller.util.getApkDestinationFile
import dev.goodwy.rphone.controller.util.getAppVersion
import dev.goodwy.rphone.controller.util.installApkAndScheduleDelete
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
import com.ramcosta.composedestinations.generated.destinations.CallSettingsScreenDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.goodwy.rphone.BuildConfig
import dev.goodwy.rphone.controller.PurchaseHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import java.io.File
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun SettingsScreen(navigator: DestinationsNavigator) {
    val context = LocalContext.current
    val appInfo = getAppVersion(context)
    val appVersion = appInfo.first
    val storeName = when (BuildConfig.FLAVOR) {
        "gplay" -> "GPlay"
        else -> "FOSS"
    }

    val listState = rememberLazyListState()
    val prefs: PreferenceManager = koinInject()
    val scope = rememberCoroutineScope()

    var proximityBg by remember { mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_PROXIMITY_BG, true)) }
    val purchaseHelper: PurchaseHelper = koinInject()
    val isPro by purchaseHelper.isPro.collectAsState()
    val proCheckDone by purchaseHelper.proCheckDone.collectAsState()
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

    var updateDialogState by remember { mutableStateOf<UpdateDialogState>(UpdateDialogState.Idle) }
    var backupState       by remember { mutableStateOf<BackupDialogState>(BackupDialogState.Idle) }

    var visible by remember { mutableStateOf(false) }
    var isClosing by remember { mutableStateOf(false) }

    fun navigateBack() {
        isClosing = true
        scope.launch {
            delay(280)
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

    // Call recorder install state (top-level so it re-checks on resume)
//    val recorderPkgTopLevel = "com.coolappstore.evercallrecorder.by.svhp"
//    fun isRecorderInstalled(): Boolean = try { context.packageManager.getPackageInfo(recorderPkgTopLevel, 0); true } catch (_: Exception) { false }
//    var recorderInstalled by remember { mutableStateOf(isRecorderInstalled()) }
//    DisposableEffect(activity) {
//        val recorderLifecycleOwner = activity as? androidx.lifecycle.LifecycleOwner
//        val recorderObserver = LifecycleEventObserver { _, event ->
//            if (event == Lifecycle.Event.ON_RESUME)
//                recorderInstalled = isRecorderInstalled()
//        }
//        recorderLifecycleOwner?.lifecycle?.addObserver(recorderObserver)
//        onDispose { recorderLifecycleOwner?.lifecycle?.removeObserver(recorderObserver) }
//    }
//    var showRecordingDialog by remember { mutableStateOf(false) }

    // ── Update Dialogs ────────────────────────────────────────────────────────
    when (val state = updateDialogState) {

        is UpdateDialogState.Checking -> Dialog(onDismissRequest = {}) {
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    CircularProgressIndicator()
                    Text("Checking for updates…", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }

        is UpdateDialogState.UpToDate -> AlertDialog(
            onDismissRequest = { updateDialogState = UpdateDialogState.Idle },
            icon = {
                Icon(
                    Icons.Default.CheckCircle,
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(72.dp)
                )
            },
            title = { Text("Up to date") },
            text = { Text("The app is running the latest version (v$appVersion).") },
            confirmButton = { TextButton(onClick = { updateDialogState = UpdateDialogState.Idle }) { Text("OK") } }
        )

        // ── Confirmation popup before downloading ──
        is UpdateDialogState.ConfirmUpdate -> AlertDialog(
            onDismissRequest = { updateDialogState = UpdateDialogState.Idle },
            icon = { Icon(Icons.Default.SystemUpdate, null, tint = MaterialTheme.colorScheme.customColors.colorBlue) },
            title = { Text("Update Available") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Version v${state.latestVersion} is available.")
                    Text("Would you like to download and install it now?", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                Button(onClick = {
                    val url = state.apkUrl
                    if (url != null) {
                        val downloadId = enqueueApkDownload(context, url)
                        if (downloadId != null) {
                            updateDialogState = UpdateDialogState.Downloading(state.latestVersion, url, downloadId, 0f)
                        } else {
                            updateDialogState = UpdateDialogState.Error
                        }
                    } else {
                        updateDialogState = UpdateDialogState.Error
                    }
                }) { Text("Download") }
            },
            dismissButton = {
                TextButton(onClick = { updateDialogState = UpdateDialogState.Idle }) { Text("Not Now") }
            }
        )

        // ── Accurate download progress ──
        is UpdateDialogState.Downloading -> {
            // Poll DownloadManager for real progress
            LaunchedEffect(state.downloadId) {
                val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                while (true) {
                    delay(300)
                    val query = DownloadManager.Query().setFilterById(state.downloadId)
                    val cursor = dm.query(query)
                    if (!cursor.moveToFirst()) { cursor.close(); break }

                    val dmStatus = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    val downloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    cursor.close()

                    when (dmStatus) {
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            updateDialogState = UpdateDialogState.Idle
                            val file = getApkDestinationFile()
                            installApkAndScheduleDelete(context, file)
                            break
                        }
                        DownloadManager.STATUS_FAILED -> {
                            updateDialogState = UpdateDialogState.Error
                            break
                        }
                        else -> {
                            val progress = if (total > 0L) (downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
                            updateDialogState = state.copy(progress = progress)
                        }
                    }
                }
            }

            Dialog(onDismissRequest = {}) {
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Icon(Icons.Default.SystemUpdate, null, tint = MaterialTheme.colorScheme.customColors.colorBlue, modifier = Modifier.size(36.dp))
                        Text("Downloading Update", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("v${state.latestVersion}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                            LinearProgressIndicator(
                                progress = { state.progress },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${(state.progress * 100).roundToInt()}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Please wait…", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }

        is UpdateDialogState.Error -> AlertDialog(
            onDismissRequest = { updateDialogState = UpdateDialogState.Idle },
            icon = { Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.customColors.colorRed) },
            title = { Text("Check failed") },
            text = { Text("Could not check for updates. Please try again later.") },
            confirmButton = { TextButton(onClick = { updateDialogState = UpdateDialogState.Idle }) { Text("OK") } }
        )

        else -> {}
    }

    // ── Backup Dialogs ────────────────────────────────────────────────────────
    when (val state = backupState) {
        is BackupDialogState.Restoring -> Dialog(onDismissRequest = {}) {
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    CircularProgressIndicator()
                    Text("Restoring backup…", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        is BackupDialogState.BackupSuccess -> AlertDialog(onDismissRequest = { backupState = BackupDialogState.Idle }, icon = { Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.customColors.colorGreen) }, title = { Text("Backup created") }, text = { Text("Backup saved to:\n${state.path}") }, confirmButton = { TextButton(onClick = { backupState = BackupDialogState.Idle }) { Text("OK") } })
        is BackupDialogState.RestoreSuccess -> AlertDialog(onDismissRequest = { backupState = BackupDialogState.Idle }, icon = { Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.customColors.colorGreen) }, title = { Text("Restore complete") }, text = { Text("Your data has been restored successfully. Please restart the app.") }, confirmButton = { TextButton(onClick = { backupState = BackupDialogState.Idle }) { Text("OK") } })
        is BackupDialogState.Error -> AlertDialog(onDismissRequest = { backupState = BackupDialogState.Idle }, icon = { Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.customColors.colorRed) }, title = { Text("Operation failed") }, text = { Text(state.message) }, confirmButton = { TextButton(onClick = { backupState = BackupDialogState.Idle }) { Text("OK") } })
        else -> {}
    }

    // ── Screen ────────────────────────────────────────────────────────────────
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
                title = { Text("Settings", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = {
                    NavigationIcon(onClick = { navigateBack() })
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        BackHandler { navigateBack() }
        ScrollHapticsEffect(listState = listState)
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
//                .padding(padding)
                .padding(
                    top = padding.calculateTopPadding(),
                    start = 0.dp,
                    end = 0.dp,
                    bottom = 0.dp
                )
                .alpha(alpha)
                .offset(y = offsetY),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (!isPro && proCheckDone) {
                item {
                    RillAnimatedSection(delayMs = 30L) {
                        RillExpressiveCard {
                            SupportProjectItem(
                                onClick = { navigator.navigate(DonateScreenDestination) }
                            )
                        }
                    }
                }
            }

            // ── Updates ──────────────────────────────────────────────────────

//            if (!isDefaultDialer) {
//                item {
//                    RillAnimatedSection(delayMs = 0L) {
//                        Column {
////                        SectionLabel("Updates")
//                            RillExpressiveCard {
//                            RillListItem(
//                                headline  = "Check For Updates",
//                                supporting = "Current version: v$appVersion",
//                                leadingIcon = Icons.Default.SystemUpdate,
//                                iconContainerColor = MaterialTheme.colorScheme.customColors.colorAmber,
//                                trailingIcon = Icons.Default.ChevronRight,
//                                onClick = {
//                                    scope.launch {
//                                        updateDialogState = UpdateDialogState.Checking
//                                        val release = fetchLatestRelease(GITHUB_API_RELEASES)
//                                        updateDialogState = when {
//                                            release == null -> UpdateDialogState.Error
//                                            isNewerVersion(release.tagName, appVersion) ->
//                                                UpdateDialogState.ConfirmUpdate(release.tagName, release.apkUrl)
//                                            else -> UpdateDialogState.UpToDate
//                                        }
//                                    }
//                                }
//                            )
//                            RillListItem(
//                                headline = "Rate and Review",
//                                supporting = "Share your feedback about UC Dialer",
//                                leadingIcon = Icons.Default.Star,
//                                iconContainerColor = MaterialTheme.colorScheme.customColors.colorCyan,
//                                trailingIcon = Icons.Default.ChevronRight,
//                                onClick = {
//                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://docs.google.com/forms/d/e/1FAIpQLSdY2WYWDFfvLScsBBxfCWzozyA_4sHUCzfR1JycfzJKASvbfQ/viewform?usp=header"))
//                                    context.startActivity(intent)
//                                }
//                            )
//                                RillListItem(
//                                    headline = if (isDefaultDialer) "Default Dialer" else "Set as Default Dialer",
//                                    supporting = if (isDefaultDialer) "UC Dialer is your default phone app" else "Required for calls and call log access",
//                                    leadingIcon = Icons.Rounded.PhoneEnabled,
//                                    iconContainerColor = if (isDefaultDialer) MaterialTheme.colorScheme.customColors.colorDarkGreen else MaterialTheme.colorScheme.customColors.colorDarkRed,
//                                    iconBgContainerColor = if (isDefaultDialer) MaterialTheme.colorScheme.customColors.colorGreen else MaterialTheme.colorScheme.customColors.colorRed,
//                                    trailingIcon = if (isDefaultDialer) Icons.Default.CheckCircle else Icons.Default.ChevronRight,
//                                    onClick = {
//                                        if (!isDefaultDialer) {
//                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
//                                                val roleManager =
//                                                    context.getSystemService(android.app.role.RoleManager::class.java)
//                                                val intent =
//                                                    roleManager.createRequestRoleIntent(android.app.role.RoleManager.ROLE_DIALER)
//                                                defaultDialerLauncher.launch(intent)
//                                            } else {
//                                                val intent =
//                                                    Intent(android.telecom.TelecomManager.ACTION_CHANGE_DEFAULT_DIALER)
//                                                        .putExtra(
//                                                            android.telecom.TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME,
//                                                            context.packageName
//                                                        )
//                                                defaultDialerLauncher.launch(intent)
//                                            }
//                                        }
//                                    }
//                                )
//                            }
//                        }
//                    }
//                }
//            }

            // ── Appearance ───────────────────────────────────────────────────
            item {
                RillAnimatedSection(delayMs = 60L) {
                    Column {
                        SettingsSectionLabel("Appearance")
                        RillExpressiveCard {
                            RillListItem(
                                headline = "Interface",
                                supporting = "Themes, colors, and layout",
                                leadingIcon = Icons.Rounded.Palette,
                                iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkCyan,
                                iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorCyan,
                                trailingIcon = Icons.Default.ChevronRight,
                                onClick = { navigator.navigate(InterfaceScreenDestination) })
                            RillListItem(
                                headline = "Navigations",
                                supporting = "Tab visibility, tab style, and default tab",
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
                        SettingsSectionLabel("Calls & System")
                        RillExpressiveCard {
                            RillListItem(
                                headline = "Call Settings",
                                supporting = "Accounts, sensor, and pocket mode",
                                leadingIcon = Icons.Rounded.Call,
                                iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkGreen,
                                iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorGreen,
                                trailingIcon = Icons.Default.ChevronRight,
                                onClick = { navigator.navigate(CallSettingsScreenDestination) }
                            )
                            RillListItem(
                                headline = "Sound & Vibration",
                                supporting = "Ringtones, dialpad tones, and haptics across app",
                                leadingIcon = Icons.AutoMirrored.Rounded.VolumeUp,
                                iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkAmber,
                                iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorAmber,
                                trailingIcon = Icons.Default.ChevronRight,
                                onClick = { navigator.navigate(SoundVibrationScreenDestination) }
                            )
                            RillListItem(
                                headline = stringResource(R.string.manage_blocked),
                                supporting = stringResource(R.string.manage_blocked_subtitle),
                                leadingIcon = Icons.Outlined.Block,
                                iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkRed,
                                iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorRed,
                                trailingIcon = Icons.Default.ChevronRight,
                                onClick = { navigator.navigate(BlockedNumbersScreenDestination) }
                            )
                            val biometricsType = remember(prefs.settingsChanged.collectAsState().value) {
                                prefs.getString(PreferenceManager.KEY_BIOMETRICS_TYPE, "") ?: ""
                            }
                            val biometricsLabel = when (biometricsType) {
                                "system"   -> "System Biometrics"
                                "pin"      -> "Custom PIN"
                                "password" -> "Custom Password"
                                else       -> "Not configured"
                            }
                            RillListItem(
                                headline   = "Authentication",
                                supporting = biometricsLabel,
                                leadingIcon = Icons.Rounded.Fingerprint,
                                iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkRed,
                                iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorRed,
                                trailingIcon = Icons.Default.ChevronRight,
                                onClick = { navigator.navigate(BiometricScreenDestination) }
                            )
                        }

//                        Spacer(modifier = Modifier.height(8.dp))
//
//                        // ── Call Recording (separate card) ────────────────────
//                        val recorderPkg = recorderPkgTopLevel
//                        RillExpressiveCard {
//                            RillListItem(
//                                headline = "Call Recording",
//                                supporting = if (recorderInstalled) "Open UC Call Recorder" else "Download the UC Call Recorder companion app",
//                                leadingIcon = Icons.Default.FiberManualRecord,
//                                iconContainerColor = Color(0xFFE53935),
//                                trailingIcon = Icons.Default.ChevronRight,
//                                onClick = {
//                                    val isActuallyInstalled = try { context.packageManager.getPackageInfo(recorderPkg, 0); true } catch (_: Exception) { false }
//                                    if (isActuallyInstalled) {
//                                        val launch = Intent().apply {
//                                            setClassName(recorderPkg, "com.coolappstore.evercallrecorder.by.svhp.ui.MainActivity")
//                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
//                                        }
//                                        try { context.startActivity(launch) } catch (_: Exception) {}
//                                    } else {
//                                        showRecordingDialog = true
//                                    }
//                                }
//                            )
//                        }
//                        if (showRecordingDialog) {
//                            val isInstalledNow = try { context.packageManager.getPackageInfo(recorderPkg, 0); true } catch (_: Exception) { false }
//                            if (isInstalledNow) {
//                                showRecordingDialog = false
//                                val launch = Intent().apply {
//                                    setClassName(recorderPkg, "com.coolappstore.evercallrecorder.by.svhp.ui.MainActivity")
//                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
//                                }
//                                try { context.startActivity(launch) } catch (_: Exception) {}
//                            } else {
//                                CallRecordingDialog(onDismiss = { showRecordingDialog = false })
//                            }
//                        }
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
                                headline = "Manage Contacts",
                                supporting = "Private contacts and clean up your list",
                                leadingIcon = Icons.Rounded.PeopleAlt,
                                iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkBlue,
                                iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorBlue,
                                trailingIcon = Icons.Default.ChevronRight,
                                onClick = { navigator.navigate(ContactManagementScreenDestination) })
                        }
                    }
                }
            }

            // ── Auto Check For Updates ────────────────────────────────────────
//            item {
//                RillAnimatedSection(delayMs = 240L) {
//                    Column {
//                        SectionLabel("Auto Check For Updates")
//                        RillExpressiveCard {
//                            RillSwitchListItem(
//                                headline   = "Auto Check For Updates",
//                                supporting = "Automatically check for updates when the app opens",
//                                leadingIcon = Icons.Default.Autorenew,
//                                iconContainerColor = ColorAmber,
//                                checked = autoUpdateEnabled,
//                                onCheckedChange = {
//                                    autoUpdateEnabled = it
//                                    prefs.setBoolean(PreferenceManager.KEY_AUTO_UPDATE_CHECK, it)
//                                }
//                            )
//                        }
//                    }
//                }
//            }

            // ── Backup & Restore ─────────────────────────────────────────────
//            item {
//                RillAnimatedSection(delayMs = 260L) {
//                    Column {
//                        SettingsSectionLabel("Backup & Restore")
//                        RillExpressiveCard {
//                            RillListItem(
//                                headline   = "Create Backup",
//                                supporting = "Save app configuration and notes",
//                                leadingIcon = Icons.Default.Backup,
//                                iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkPurple,
//                                iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorPurple,
//                                trailingIcon = Icons.Default.ChevronRight,
//                                onClick = {
//                                    scope.launch {
//                                        val file = BackupManager.createBackup(context)
//                                        backupState = if (file != null) {
//                                            val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
//                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
//                                                type = "application/octet-stream"
//                                                putExtra(Intent.EXTRA_STREAM, uri)
//                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
//                                            }
//                                            context.startActivity(Intent.createChooser(shareIntent, "Save Backup"))
//                                            BackupDialogState.BackupSuccess(file.absolutePath)
//                                        } else {
//                                            BackupDialogState.Error("Failed to create backup")
//                                        }
//                                    }
//                                }
//                            )
//                            RillListItem(headline = "Restore Backup", supporting = "Restore app configuration and notes", leadingIcon = Icons.Default.Restore, iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkPurple, iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorPurple, trailingIcon = Icons.Default.ChevronRight, onClick = { restoreLauncher.launch("*/*") })
//                        }
//                    }
//                }
//            }

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
                                    iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkOliva,
                                    iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorOliva,
                                    trailingIcon = Icons.Default.ChevronRight,
                                    onClick = { navigator.navigate(DonateScreenDestination) })
                            }
                            RillListItem(headline = stringResource(R.string.about), supporting = "Version $appVersion ($storeName)", leadingIcon = Icons.Outlined.Info, iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkOliva, iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorOliva, trailingIcon = Icons.Default.ChevronRight, onClick = { navigator.navigate(AboutAppScreenDestination) })
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

private sealed class UpdateDialogState {
    object Idle : UpdateDialogState()
    object Checking : UpdateDialogState()
    object UpToDate : UpdateDialogState()
    data class ConfirmUpdate(val latestVersion: String, val apkUrl: String?) : UpdateDialogState()
    data class Downloading(val latestVersion: String, val apkUrl: String?, val downloadId: Long, val progress: Float) : UpdateDialogState()
    object Error : UpdateDialogState()
}

private sealed class BackupDialogState {
    object Idle : BackupDialogState()
    object Restoring : BackupDialogState()
    data class BackupSuccess(val path: String) : BackupDialogState()
    object RestoreSuccess : BackupDialogState()
    data class Error(val message: String) : BackupDialogState()
}

@Composable
fun SettingsSectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(start = 20.dp, bottom = 8.dp),
        color = MaterialTheme.colorScheme.primary
    )
}
