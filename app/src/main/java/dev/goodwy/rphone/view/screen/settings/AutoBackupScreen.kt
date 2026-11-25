package dev.goodwy.rphone.view.screen.settings

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.Surface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.goodwy.rphone.controller.util.AutoBackupManager
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.view.components.NavBarVisibilityState
import dev.goodwy.rphone.view.components.RillAnimatedSection
import dev.goodwy.rphone.view.components.RillExpressiveCard
import dev.goodwy.rphone.view.components.RillListItem
import dev.goodwy.rphone.view.components.RillSwitchListItem
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.DonateScreenDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.goodwy.rphone.R
import dev.goodwy.rphone.cardCornerExtraSmall
import dev.goodwy.rphone.cardSpacedBy
import dev.goodwy.rphone.controller.PurchaseHelper
import dev.goodwy.rphone.icons.cycle
import dev.goodwy.rphone.icons.home_storage_gear
import dev.goodwy.rphone.view.components.HeadlineText
import dev.goodwy.rphone.view.components.NavigationIcon
import dev.goodwy.rphone.view.components.RillDialog
import dev.goodwy.rphone.view.components.RillIconBox
import dev.goodwy.rphone.view.components.RillSettingsAnimatedVisibility
import dev.goodwy.rphone.view.components.SupportProjectItem
import dev.goodwy.rphone.view.components.SupportingText
import dev.goodwy.rphone.view.components.Title
import dev.goodwy.rphone.view.components.doHapticIfNeed
import dev.goodwy.rphone.view.components.shake
import dev.goodwy.rphone.view.theme.MyColors.cardColor
import dev.goodwy.rphone.view.theme.customColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import kotlin.time.Duration.Companion.milliseconds

@SuppressLint("LocalContextGetResourceValueCall")
@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun AutoBackupScreen(
    navigator: DestinationsNavigator
) {
    val context = LocalContext.current
    val prefs = koinInject<PreferenceManager>()
    val settingsState by prefs.settingsChanged.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        NavBarVisibilityState.hideForSettingsEntry = true
        onDispose {
            NavBarVisibilityState.hideForSettingsEntry = false
        }
    }

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

    // Auto backup master state
    var autoBackupEnabled by remember { mutableStateOf(prefs.isAutoBackupEnabled()) }
    var folderUriStr by remember { mutableStateOf(prefs.getAutoBackupFolderUri()) }

    // Frequency state
    var intervalValueText by remember { mutableStateOf(prefs.getAutoBackupIntervalValue().toString()) }
    var intervalUnit by remember { mutableStateOf(prefs.getAutoBackupIntervalUnit()) }

    // What to backup state
    var backupSettings by remember { mutableStateOf(prefs.isAutoBackupItemSettings()) }
    var backupCallingCards by remember { mutableStateOf(prefs.isAutoBackupItemCallingCards()) }
    var backupNotes by remember { mutableStateOf(prefs.isAutoBackupItemNotes()) }
    var backupContacts by remember { mutableStateOf(prefs.isAutoBackupItemContacts()) }
    var backupPrivateContacts by remember { mutableStateOf(prefs.isAutoBackupItemPrivateContacts()) }
    var backupCallLogs by remember { mutableStateOf(prefs.isAutoBackupItemCallLogs()) }

    var showItemsDialog by remember { mutableStateOf(false) }

    // Auto delete state
    var autoDeleteTimeEnabled by remember { mutableStateOf(prefs.isAutoDeleteBackupsTimeEnabled()) }
    var autoDeleteTimeValueText by remember { mutableStateOf(prefs.getAutoDeleteBackupsTimeValue().toString()) }
    var autoDeleteTimeUnit by remember { mutableStateOf(prefs.getAutoDeleteBackupsTimeUnit()) }

    var autoDeleteCountEnabled by remember { mutableStateOf(prefs.isAutoDeleteBackupsCountEnabled()) }
    var autoDeleteCountValueText by remember { mutableStateOf(prefs.getAutoDeleteBackupsCountValue().toString()) }

    // Folder picker launcher
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            folderUriStr = uri.toString()
            prefs.setAutoBackupFolderUri(uri.toString())
        }
    }

    // Derive folder display label
    val isFolderSet = !folderUriStr.isNullOrBlank()
    val folderDisplayName = remember(folderUriStr) {
        if (!folderUriStr.isNullOrBlank()) {
            try {
                val doc = DocumentFile.fromTreeUri(context, Uri.parse(folderUriStr))
                doc?.name ?: folderUriStr
            } catch (_: Exception) {
                folderUriStr
            }
        } else {
            context.getString(R.string.auto_backup_folder_none)
        }
    }

    // Summary of selected backup items
    val backupItemsSummary = remember(
        backupSettings, backupCallingCards, backupNotes,
        backupContacts, backupPrivateContacts, backupCallLogs
    ) {
        val list = mutableListOf<String>()
        if (backupSettings) list.add(context.getString(R.string.settings))
        if (backupCallingCards) list.add(context.getString(R.string.backup_call_backgrounds_title))
        if (backupContacts) list.add(context.getString(R.string.contacts))
        if (backupPrivateContacts) list.add(context.getString(R.string.private_contacts))
        if (backupCallLogs) list.add(context.getString(R.string.recents))
        if (backupNotes) list.add(context.getString(R.string.call_notes))
        if (list.isEmpty()) context.getString(R.string.auto_backup_what_summary_none) else list.joinToString(", ")
    }

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
                title = { Title(stringResource(R.string.auto_backup_settings_title)) },
                navigationIcon = {
                    NavigationIcon(onClick = { navigator.navigateUp() })
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        Box(modifier = Modifier
            .padding(top = padding.calculateTopPadding())
            .fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(16.dp)
            ) {
                if (!isPro && proCheckDone) {
                    item {
                        RillAnimatedSection(delayMs = 40L) {
                            RillExpressiveCard {
                                SupportProjectItem(
                                    modifier = Modifier.shake(enabledShake) { enabledShake = false },
                                    onClick = { navigator.navigate(DonateScreenDestination) }
                                )
                            }
                        }
                    }
                }

                // ── Main Switch  ───────────────────────────────────────
                item {
                    RillAnimatedSection(delayMs = 0L) {
                        RillExpressiveCard {
                            RillSwitchListItem(
                                headline = stringResource(R.string.backup_auto_backup_title),
                                supporting = if (autoBackupEnabled) stringResource(R.string.auto_backup_enabled_supporting) else stringResource(R.string.auto_backup_disabled_supporting),
                                leadingIcon = Icons.Rounded.CloudSync,
                                iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkPurple,
                                iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorPurple,
                                checked = autoBackupEnabled,
                                onCheckedChange = { checked ->
                                    if (autoBackupEnabled || isPro) {
                                        autoBackupEnabled = checked
                                        prefs.setAutoBackupEnabled(checked)
                                        if (checked) {
                                            AutoBackupManager.scheduleAutoBackup(context)
                                        } else {
                                            AutoBackupManager.cancelAutoBackup(context)
                                        }
                                    } else {
                                        enabledShake = true
                                        showSnackbar = true
                                        scope.launch {
                                            delay(3000.milliseconds)
                                            showSnackbar = false
                                        }
                                    }
                                },
                                modifier = Modifier.alpha(if (autoBackupEnabled || isPro) 1f else 0.6f)
                            )
                        }
                    }
                }

                // ── Features Below Main Switch ─────────────────────────────────
                item {
                    val contentAlpha = if (autoBackupEnabled) 1f else 0.38f
                    val isFolderMissing = autoBackupEnabled && !isFolderSet
                    val lowerFeaturesEnabled = autoBackupEnabled && isFolderSet
                    val lowerFeaturesAlpha = if (lowerFeaturesEnabled) 1f else 0.38f

                    RillAnimatedSection(delayMs = 30L) {
                        RillExpressiveCard {
                            RillListItem(
                                headline = stringResource(R.string.auto_backup_folder_title),
                                supporting = if (isFolderSet) folderDisplayName else stringResource(R.string.auto_backup_folder_none_hint),
                                supportingColor = if (isFolderMissing) MaterialTheme.colorScheme.error else null,
                                leadingIcon = home_storage_gear,
                                iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkBlue,
                                iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorBlue,
                                onClick = {
                                    if (autoBackupEnabled) {
                                        folderPickerLauncher.launch(null)
                                    }
                                },
                                trailingContent = {
                                    if (isFolderSet) {
                                        IconButton(
                                            onClick = {
                                                doHapticIfNeed(context, prefs)
                                                folderUriStr = null
                                                prefs.setAutoBackupFolderUri(null)
                                            }
                                        ) { Icon(Icons.Rounded.Clear, stringResource(R.string.clear)) }
                                    }
                                },
                                modifier = Modifier.alpha(contentAlpha)
                            )

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .alpha(lowerFeaturesAlpha),
                            ) {
                                RillListItem(
                                    headline = stringResource(R.string.auto_backup_what_title),
                                    supporting = backupItemsSummary,
                                    supportingColor = if (backupItemsSummary == stringResource(R.string.auto_backup_what_summary_none)) MaterialTheme.colorScheme.error else null,
                                    leadingIcon = Icons.Rounded.Checklist,
                                    iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkBlue,
                                    iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorBlue,
                                    trailingIcon = Icons.Default.ChevronRight,
                                    onClick = {
                                        if (lowerFeaturesEnabled) {
                                            showItemsDialog = true
                                        }
                                    }
                                )
                                Spacer(Modifier.height(cardSpacedBy))
                                Column(
                                    modifier = Modifier
                                        .background(cardColor, RoundedCornerShape(cardCornerExtraSmall))
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RillIconBox(
                                            icon = cycle,
                                            iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkBlue,
                                            iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorBlue,
                                            size = 32.dp,
                                            modifier = Modifier.padding(start = 2.dp)
                                        )
                                        Spacer(modifier = Modifier.width(16.dp))
                                        HeadlineText(text = stringResource(R.string.auto_backup_every_title))
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = intervalValueText,
                                            onValueChange = { input ->
                                                if (lowerFeaturesEnabled) {
                                                    val digitsOnly = input.filter { it.isDigit() }.take(4)
                                                    intervalValueText = digitsOnly
                                                    digitsOnly.toIntOrNull()?.let { num ->
                                                        if (num > 0) {
                                                            prefs.setAutoBackupIntervalValue(num)
                                                            AutoBackupManager.scheduleAutoBackup(context)
                                                        }
                                                    }
                                                }
                                            },
                                            modifier = Modifier.weight(0.25f),
                                            textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
                                            singleLine = true,
                                            enabled = lowerFeaturesEnabled,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            shape = RoundedCornerShape(50),
                                            label = { Text(stringResource(R.string.auto_backup_interval_value_label), style = MaterialTheme.typography.labelSmall) }
                                        )

                                        // Minutes, Hours, Days
                                        Row(
                                            modifier = Modifier.weight(0.75f).padding(top = 8.dp),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            val intervalCount = intervalValueText.toIntOrNull() ?: 0
                                            listOf(
                                                "minutes" to pluralStringResource(R.plurals.auto_backup_unit_minutes, intervalCount, intervalCount),
                                                "hours" to pluralStringResource(R.plurals.auto_backup_unit_hours, intervalCount, intervalCount),
                                                "days" to pluralStringResource(R.plurals.auto_backup_unit_days, intervalCount, intervalCount)
                                            ).forEach { (unitKey, unitLabel) ->
                                                val isSelected = intervalUnit.equals(unitKey, ignoreCase = true)
                                                val interactionSource = remember { MutableInteractionSource() }
                                                val isPressed by interactionSource.collectIsPressedAsState()
                                                val cornerRadius by animateDpAsState(
                                                    targetValue = if (isPressed || isSelected) 50.dp else 8.dp,
                                                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                                    label = "ButtonShape"
                                                )
                                                Surface(
                                                    onClick = {
                                                        if (lowerFeaturesEnabled) {
                                                            doHapticIfNeed(context, prefs)
                                                            intervalUnit = unitKey
                                                            prefs.setAutoBackupIntervalUnit(unitKey)
                                                            AutoBackupManager.scheduleAutoBackup(context)
                                                        }
                                                    },
                                                    interactionSource = interactionSource,
                                                    enabled = lowerFeaturesEnabled,
                                                    shape = when {
                                                        isSelected -> CircleShape
                                                        unitKey == "minutes" -> RoundedCornerShape(topStart = 50.dp, topEnd = cornerRadius, bottomEnd = cornerRadius, bottomStart = 50.dp)
                                                        unitKey == "hours" -> RoundedCornerShape(cornerRadius)
                                                        unitKey == "days" -> RoundedCornerShape(topStart = cornerRadius, topEnd = 50.dp, bottomEnd = 50.dp, bottomStart = cornerRadius)
                                                        else -> CircleShape
                                                    },
                                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                                                    modifier = Modifier.weight(1f).height(56.dp)
                                                ) {
                                                    Box(
                                                        contentAlignment = Alignment.Center,
                                                        modifier = Modifier.fillMaxSize().padding(12.dp)
                                                    ) {
                                                        Text(
                                                            text = unitLabel,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                            maxLines = 2,
                                                            overflow = TextOverflow.Ellipsis,
                                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    // Auto Delete Backups
                    RillAnimatedSection(delayMs = 120L) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .alpha(lowerFeaturesAlpha),
                        ) {
                            SettingsSectionLabel(stringResource(R.string.auto_backup_delete_section))
                            RillExpressiveCard {
                                Column(
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .background(cardColor, RoundedCornerShape(cardCornerExtraSmall)),
                                    ) {
                                        RillSwitchListItem(
                                            headline = stringResource(R.string.auto_backup_delete_by_time_title),
                                            leadingIcon = Icons.Rounded.Timer,
                                            iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkPurple,
                                            iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorPurple,
                                            checked = autoDeleteTimeEnabled,
                                            enabled = lowerFeaturesEnabled,
                                            onCheckedChange = { checked ->
                                                autoDeleteTimeEnabled = checked
                                                prefs.setAutoDeleteBackupsTimeEnabled(checked)
                                            },
                                        )

                                        RillSettingsAnimatedVisibility(visible = autoDeleteTimeEnabled) {
                                            Column(
                                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                                modifier = Modifier.padding(top = 0.dp, start = 16.dp, end = 16.dp, bottom = 16.dp)
                                            ) {
                                                SupportingText(text = stringResource(R.string.auto_backup_delete_by_time_supporting))
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    OutlinedTextField(
                                                        value = autoDeleteTimeValueText,
                                                        onValueChange = { v ->
                                                            if (lowerFeaturesEnabled) {
                                                                val d = v.filter { it.isDigit() }.take(4)
                                                                autoDeleteTimeValueText = d
                                                                d.toIntOrNull()?.let { prefs.setAutoDeleteBackupsTimeValue(it) }
                                                            }
                                                        },
                                                        modifier = Modifier.weight(0.3f),
                                                        textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
                                                        singleLine = true,
                                                        enabled = lowerFeaturesEnabled,
                                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                        shape = RoundedCornerShape(50),
                                                        label = { Text(stringResource(R.string.auto_backup_delete_amount_label), style = MaterialTheme.typography.labelSmall) }
                                                    )
                                                    Row(
                                                        modifier = Modifier.weight(0.6f).padding(top = 8.dp),
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        val deleteTimeCount = autoDeleteTimeValueText.toIntOrNull() ?: 0
                                                        listOf(
                                                            "hours" to pluralStringResource(R.plurals.auto_backup_unit_hours, deleteTimeCount, deleteTimeCount),
                                                            "days" to pluralStringResource(R.plurals.auto_backup_unit_days, deleteTimeCount, deleteTimeCount)
                                                        ).forEach { (optKey, optLabel) ->
                                                            val sel = autoDeleteTimeUnit == optKey
                                                            val interactionSource = remember { MutableInteractionSource() }
                                                            val isPressed by interactionSource.collectIsPressedAsState()
                                                            val cornerRadius by animateDpAsState(
                                                                targetValue = if (isPressed || sel) 50.dp else 8.dp,
                                                                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                                                label = "ButtonShape"
                                                            )
                                                            Surface(
                                                                onClick = {
                                                                    if (lowerFeaturesEnabled) {
                                                                        doHapticIfNeed(context, prefs)
                                                                        autoDeleteTimeUnit = optKey
                                                                        prefs.setAutoDeleteBackupsTimeUnit(optKey)
                                                                    }
                                                                },
                                                                interactionSource = interactionSource,
                                                                enabled = lowerFeaturesEnabled,
                                                                shape = when {
                                                                    sel -> CircleShape
                                                                    optKey == "hours" -> RoundedCornerShape(topStart = 50.dp, topEnd = cornerRadius, bottomEnd = cornerRadius, bottomStart = 50.dp)
                                                                    optKey == "days" -> RoundedCornerShape(topStart = cornerRadius, topEnd = 50.dp, bottomEnd = 50.dp, bottomStart = cornerRadius)
                                                                    else -> CircleShape
                                                                },
                                                                color = if (sel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                                                                modifier = Modifier.weight(1f).height(56.dp)
                                                            ) {
                                                                Box(
                                                                    Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                                                    Alignment.Center
                                                                ) {
                                                                    Text(
                                                                        optLabel,
                                                                        style = MaterialTheme.typography.labelMedium,
                                                                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                                                        color = if (sel) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    Spacer(Modifier.height(cardSpacedBy))

                                    Column(
                                        modifier = Modifier
                                            .background(cardColor, RoundedCornerShape(cardCornerExtraSmall))
                                    ) {
                                        RillSwitchListItem(
                                            headline = stringResource(R.string.auto_backup_delete_by_count_title),
                                            leadingIcon = Icons.Rounded.Storage,
                                            iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkPurple,
                                            iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorPurple,
                                            checked = autoDeleteCountEnabled,
                                            enabled = lowerFeaturesEnabled,
                                            onCheckedChange = { checked ->
                                                autoDeleteCountEnabled = checked
                                                prefs.setAutoDeleteBackupsCountEnabled(checked)
                                            },
                                        )

                                        RillSettingsAnimatedVisibility(visible = autoDeleteCountEnabled) {
                                            Column(
                                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                                modifier = Modifier.padding(top = 0.dp, start = 16.dp, end = 16.dp, bottom = 16.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    SupportingText(
                                                        text = stringResource(R.string.auto_backup_delete_by_count_supporting),
                                                        modifier = Modifier.padding(top = 6.dp).weight(0.7f)
                                                    )
                                                    OutlinedTextField(
                                                        value = autoDeleteCountValueText,
                                                        onValueChange = { v ->
                                                            if (lowerFeaturesEnabled) {
                                                                val d = v.filter { it.isDigit() }.take(3)
                                                                autoDeleteCountValueText = d
                                                                d.toIntOrNull()?.let { prefs.setAutoDeleteBackupsCountValue(it) }
                                                            }
                                                        },
                                                        modifier = Modifier.weight(0.3f),
                                                        textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
                                                        singleLine = true,
                                                        enabled = lowerFeaturesEnabled,
                                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                        shape = RoundedCornerShape(50),
                                                        label = { Text(stringResource(R.string.auto_backup_delete_count_label), style = MaterialTheme.typography.labelSmall) }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item { SettingsBottomPadding() }
            }

            AnimatedVisibility(
                modifier = Modifier
                    .align(Alignment.BottomCenter),
                visible = showSnackbar,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                Snackbar(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(24.dp),
                    shape = MaterialTheme.shapes.large,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    action = {
                        TextButton(
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                            onClick = {
                                doHapticIfNeed(context, prefs)
                                showSnackbar = false
                                navigator.navigate(DonateScreenDestination)
                            }
                        ) {
                            Text(stringResource(R.string.continue_support), color = MaterialTheme.colorScheme.primary)
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

    // ── Dialog: What to Backup ──────────────────────────────────────────
    if (showItemsDialog) {
        RillDialog(
            onDismissRequest = { showItemsDialog = false },
            title = stringResource(R.string.auto_backup_what_title),
            icon = Icons.Rounded.Checklist,
            iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkBlue,
            iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorBlue,
            confirmButton = {
                TextButton(onClick = { showItemsDialog = false }) {
                    Text(stringResource(R.string.done))
                }
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 600.dp)
                    .clip(MaterialTheme.shapes.large)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(cardSpacedBy)
            ) {
                val items = listOf(
                    Triple(stringResource(R.string.settings), stringResource(R.string.backup_settings_supporting), backupSettings) to { checked: Boolean ->
                        backupSettings = checked
                        prefs.setAutoBackupItemSettings(checked)
                    },
                    Triple(stringResource(R.string.backup_call_backgrounds_title), stringResource(R.string.backup_call_backgrounds_supporting), backupCallingCards) to { checked: Boolean ->
                        backupCallingCards = checked
                        prefs.setAutoBackupItemCallingCards(checked)
                    },
                    Triple(stringResource(R.string.contacts), stringResource(R.string.backup_contacts_supporting), backupContacts) to { checked: Boolean ->
                        backupContacts = checked
                        prefs.setAutoBackupItemContacts(checked)
                    },
                    Triple(stringResource(R.string.private_contacts), stringResource(R.string.backup_private_contacts_supporting), backupPrivateContacts) to { checked: Boolean ->
                        backupPrivateContacts = checked
                        prefs.setAutoBackupItemPrivateContacts(checked)
                    },
                    Triple(stringResource(R.string.recents), stringResource(R.string.backup_call_logs_supporting), backupCallLogs) to { checked: Boolean ->
                        backupCallLogs = checked
                        prefs.setAutoBackupItemCallLogs(checked)
                    },
                    Triple(stringResource(R.string.call_notes), stringResource(R.string.backup_call_notes_supporting), backupNotes) to { checked: Boolean ->
                        backupNotes = checked
                        prefs.setAutoBackupItemNotes(checked)
                    },
                )

                items.forEach { (item, onToggle) ->
                    val (title, subtitle, checked) = item
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(cardColor, RoundedCornerShape(cardCornerExtraSmall))
                            .clickable { onToggle(!checked) }
                            .padding(vertical = 8.dp, horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.bodyMedium,
                                lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                lineHeight = MaterialTheme.typography.bodyMedium.fontSize, //14.sp
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = checked,
                            onCheckedChange = onToggle
                        )
                    }
                }
            }
        }
    }
}
