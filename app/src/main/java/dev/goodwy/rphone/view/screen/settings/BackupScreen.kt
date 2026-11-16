package dev.goodwy.rphone.view.screen.settings

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.Surface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.StickyNote2
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.AccessTimeFilled
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.PeopleAlt
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import dev.goodwy.rphone.controller.util.BackupManager
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.view.components.RillAnimatedSection
import dev.goodwy.rphone.view.components.RillExpressiveCard
import dev.goodwy.rphone.view.components.RillSwitchListItem
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.goodwy.rphone.R
import dev.goodwy.rphone.cardCornerExtraSmall
import dev.goodwy.rphone.controller.util.ContactUtils.getAccountIcon
import dev.goodwy.rphone.icons.background_replace
import dev.goodwy.rphone.view.components.NavigationIcon
import dev.goodwy.rphone.view.components.Title
import dev.goodwy.rphone.view.theme.MyColors.cardColor
import dev.goodwy.rphone.view.theme.customColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

private val ColorGreen = Color(0xFF4CAF50)
private val ColorRed = Color(0xFFE53935)

private sealed class BackupScreenState {
    object Idle : BackupScreenState()
    object Creating : BackupScreenState()
    object Restoring : BackupScreenState()
    data class BackupSuccess(val path: String) : BackupScreenState()
    object RestoreSuccess : BackupScreenState()
    data class Error(val message: String) : BackupScreenState()
}

@SuppressLint("LocalContextGetResourceValueCall")
@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun BackupScreen(
    navigator: DestinationsNavigator,
    initialTab: Int = 0
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = koinInject<PreferenceManager>()

    var selectedTab by remember { mutableIntStateOf(initialTab) }
    var screenState by remember { mutableStateOf<BackupScreenState>(BackupScreenState.Idle) }

    // ── Backup options state ──────────────────────────────────────────────────
    var backupSettings by remember { mutableStateOf(true) }
    var backupCallBackgrounds by remember { mutableStateOf(true) }
    var backupNotes by remember { mutableStateOf(true) }
    var backupContacts by remember { mutableStateOf(false) }
    var backupPrivateContacts by remember { mutableStateOf(false) }
    var backupCallLogs by remember { mutableStateOf(false) }

    val hasAnyBackupSelected = backupSettings || backupCallBackgrounds || backupNotes ||
            backupContacts || backupPrivateContacts || backupCallLogs

    // ── Restore state ─────────────────────────────────────────────────────────
    var selectedRestoreFile by remember { mutableStateOf<File?>(null) }
    var restoreContents by remember { mutableStateOf<BackupManager.BackupContents?>(null) }
    var restoreSettings by remember { mutableStateOf(true) }
    var restoreCallBackgrounds by remember { mutableStateOf(true) }
    var restoreNotes by remember { mutableStateOf(true) }
    var restoreContacts by remember { mutableStateOf(false) }
    var restorePrivateContacts by remember { mutableStateOf(false) }
    var restoreCallLogs by remember { mutableStateOf(false) }

    val hasAnyRestoreSelected = restoreContents != null && (
            (restoreSettings && restoreContents!!.hasSettings) ||
            (restoreCallBackgrounds && restoreContents!!.hasCallBackgrounds) ||
            (restoreNotes && restoreContents!!.hasNotes) ||
            (restoreContacts && restoreContents!!.hasContacts) ||
            (restorePrivateContacts && restoreContents!!.hasPrivateContacts) ||
            (restoreCallLogs && restoreContents!!.hasCallLogs)
    )

    // ── File Launchers ────────────────────────────────────────────────────────
    val saveBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        if (uri != null) {
            screenState = BackupScreenState.Creating
            scope.launch(Dispatchers.IO) {
                try {
                    val ok = context.contentResolver.openOutputStream(uri)?.use { output ->
                        BackupManager.writeBackup(
                            context = context,
                            outputStream = output,
                            backupSettings = backupSettings,
                            backupCallBackgrounds = backupCallBackgrounds,
                            backupNotes = backupNotes,
                            backupContacts = backupContacts,
                            backupPrivateContacts = backupPrivateContacts,
                            backupCallLogs = backupCallLogs,
                        )
                    } ?: false
                    withContext(Dispatchers.Main) {
                        screenState = if (ok) {
                            BackupScreenState.BackupSuccess("Backup saved successfully")
                        } else {
                            BackupScreenState.Error(context.getString(R.string.backup_error_save))
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        screenState = BackupScreenState.Error(e.message ?: context.getString(R.string.backup_error_save))
                    }
                }
            }
        }
    }

    val restoreFilePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val tmpFile = File(context.cacheDir, "restore_page_tmp.rphone")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        tmpFile.outputStream().use { output -> input.copyTo(output) }
                    }
                    val contents = BackupManager.inspectBackup(tmpFile)
                    withContext(Dispatchers.Main) {
                        selectedRestoreFile = tmpFile
                        restoreContents = contents
                        restoreSettings = contents.hasSettings
                        restoreCallBackgrounds = contents.hasCallBackgrounds
                        restoreNotes = contents.hasNotes
                        restoreContacts = contents.hasContacts
                        restorePrivateContacts = contents.hasPrivateContacts
                        restoreCallLogs = contents.hasCallLogs
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        screenState = BackupScreenState.Error(e.message ?: context.getString(R.string.backup_error_read_file))
                    }
                }
            }
        }
    }

    fun executeShare() {
        screenState = BackupScreenState.Creating
        scope.launch(Dispatchers.IO) {
            val file = BackupManager.createBackup(
                context = context,
                backupSettings = backupSettings,
                backupCallBackgrounds = backupCallBackgrounds,
                backupNotes = backupNotes,
                backupContacts = backupContacts,
                backupPrivateContacts = backupPrivateContacts,
                backupCallLogs = backupCallLogs,
            )
            withContext(Dispatchers.Main) {
                if (file != null) {
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.provider",
                        file
                    )
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/octet-stream"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.backup_share_button)))
                    screenState = BackupScreenState.BackupSuccess(file.absolutePath)
                } else {
                    screenState = BackupScreenState.Error(context.getString(R.string.backup_error_create))
                }
            }
        }
    }

    fun executeRestore() {
        val file = selectedRestoreFile ?: return
        screenState = BackupScreenState.Restoring
        scope.launch(Dispatchers.IO) {
            try {
                val ok = BackupManager.restoreBackup(
                    context = context,
                    backupFile = file,
                    restoreSettings = restoreSettings,
                    restoreCallBackgrounds = restoreCallBackgrounds,
                    restoreNotes = restoreNotes,
                    restoreContacts = restoreContacts,
                    restorePrivateContacts = restorePrivateContacts,
                    restoreCallLogs = restoreCallLogs,
                )
                withContext(Dispatchers.Main) {
                    screenState = if (ok) BackupScreenState.RestoreSuccess else BackupScreenState.Error(context.getString(R.string.backup_error_restore))
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    screenState = BackupScreenState.Error(e.message ?: context.getString(R.string.backup_error_unknown))
                }
            }
        }
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
                title = { Title(stringResource(R.string.backup_and_restore)) },
                navigationIcon = {
                    NavigationIcon(onClick = { navigator.navigateUp() })
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding()),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            contentPadding = PaddingValues(16.dp)
        ) {
            // ── Pill Tab Selector ─────────────────────────────────────────────
            item {
                RillAnimatedSection(delayMs = 0L) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val tabs = listOf(
                            stringResource(R.string.create_backup) to Icons.Rounded.Backup,
                            stringResource(R.string.restore_backup) to Icons.Rounded.Restore
                        )
                        tabs.forEachIndexed { index, (label, icon) ->
                            val isSelected = selectedTab == index
                            val bgAnim by animateColorAsState(
                                targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceDim,
                                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                label = "tabBg_$index"
                            )
                            val fgAnim by animateColorAsState(
                                targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                label = "tabFg_$index"
                            )

                            val interactionSource =
                                remember { MutableInteractionSource() }
                            val isPressed by interactionSource.collectIsPressedAsState()
                            val cornerRadius by animateDpAsState(
                                targetValue = if (isPressed || isSelected) 50.dp else 8.dp,
                                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                label = "ButtonShape"
                            )
                            val shape = remember(cornerRadius, index) {
                                when (index) {
                                    0 -> RoundedCornerShape(
                                        topStart = 50.dp,
                                        topEnd = cornerRadius,
                                        bottomEnd = cornerRadius,
                                        bottomStart = 50.dp
                                    )

                                    else -> RoundedCornerShape(
                                        topStart = cornerRadius,
                                        topEnd = 50.dp,
                                        bottomEnd = 50.dp,
                                        bottomStart = cornerRadius
                                    )
                                }
                            }
                            Surface(
                                onClick = { selectedTab = index },
                                shape = shape,
                                color = bgAnim,
                                modifier = Modifier.weight(1f),
                                interactionSource = interactionSource,
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = fgAnim,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelLarge,
                                        lineHeight = MaterialTheme.typography.labelMedium.lineHeight,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                        fontWeight = FontWeight.SemiBold,
                                        color = fgAnim
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── Tab Create Backup ──────────────────────────────────────────
            if (selectedTab == 0) {
                item {
                    RillAnimatedSection(delayMs = 60L) {
                        Column {
                            Text(
                                text = stringResource(R.string.select_data_to_backup),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp)
                            )
                            RillExpressiveCard {
                                RillSwitchListItem(
                                    headline = stringResource(R.string.settings),
                                    supporting = stringResource(R.string.backup_settings_supporting),
                                    leadingIcon = ImageVector.vectorResource(id = R.drawable.ic_settings),
                                    iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkOliva,
                                    iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorOliva,
                                    checked = backupSettings,
                                    onCheckedChange = { backupSettings = it }
                                )
                                RillSwitchListItem(
                                    headline = stringResource(R.string.backup_call_backgrounds_title),
                                    supporting = stringResource(R.string.backup_call_backgrounds_supporting),
                                    leadingIcon = background_replace,
                                    iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkPurple,
                                    iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorPurple,
                                    checked = backupCallBackgrounds,
                                    onCheckedChange = { backupCallBackgrounds = it }
                                )
                                RillSwitchListItem(
                                    headline = stringResource(R.string.contacts),
                                    supporting = stringResource(R.string.backup_contacts_supporting),
                                    leadingIcon = Icons.Rounded.PeopleAlt,
                                    iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkBlue,
                                    iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorBlue,
                                    checked = backupContacts,
                                    onCheckedChange = { backupContacts = it }
                                )
                                RillSwitchListItem(
                                    headline = stringResource(R.string.private_contacts),
                                    supporting = stringResource(R.string.backup_private_contacts_supporting),
                                    leadingIcon = getAccountIcon(null, true),
                                    iconContainerColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                    iconBgContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                    checked = backupPrivateContacts,
                                    onCheckedChange = { backupPrivateContacts = it }
                                )
                                RillSwitchListItem(
                                    headline = stringResource(R.string.recents),
                                    supporting = stringResource(R.string.backup_call_logs_supporting),
                                    leadingIcon = Icons.Rounded.AccessTimeFilled,
                                    iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkGreen,
                                    iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorGreen,
                                    checked = backupCallLogs,
                                    onCheckedChange = { backupCallLogs = it }
                                )
                                RillSwitchListItem(
                                    headline = stringResource(R.string.call_notes),
                                    supporting = stringResource(R.string.backup_call_notes_supporting),
                                    leadingIcon = Icons.AutoMirrored.Filled.StickyNote2,
                                    iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkAmber,
                                    iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorAmber,
                                    checked = backupNotes,
                                    onCheckedChange = { backupNotes = it }
                                )
                            }
                        }
                    }
                }

                item {
                    RillAnimatedSection(delayMs = 60L) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
//                            Button(
//                                onClick = { executeShare() },
//                                enabled = hasAnyBackupSelected,
//                                shape = CircleShape,
//                                colors = ButtonDefaults.filledTonalButtonColors(
//                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
//                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
//                                ),
//                                modifier = Modifier
//                                    .weight(0.9f)
//                                    .height(52.dp),
//                                contentPadding = PaddingValues(horizontal = 16.dp)
//                            ) {
//                                Icon(Icons.Default.Share, contentDescription = stringResource(R.string.backup_share_button), modifier = Modifier.size(20.dp))
//                                Spacer(Modifier.width(8.dp))
//                                Text(stringResource(R.string.backup_share_button), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
//                            }

                            Button(
                                onClick = {
                                    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                                    saveBackupLauncher.launch("RPhone_Backup_$timestamp.rphone")
                                },
                                enabled = hasAnyBackupSelected,
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                modifier = Modifier
                                    .weight(1.1f)
                                    .height(52.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp)
                            ) {
                                Icon(Icons.Default.Save, contentDescription = stringResource(R.string.backup_save_button), modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.backup_save_button), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }

            // ── Tab Restore Backup ─────────────────────────────────────────
            if (selectedTab == 1) {
                item {
                    RillAnimatedSection(delayMs = 50L) {
                        Column {
                            Text(
                                text = stringResource(R.string.restore_select_file_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp)
                            )
                            RillExpressiveCard {
                                Column(
                                    modifier = Modifier
                                        .background(cardColor)
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        Icons.Outlined.FolderOpen,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(40.dp)
                                    )
                                    Spacer(Modifier.height(10.dp))
                                    Text(
                                        text = if (selectedRestoreFile != null) {
                                            selectedRestoreFile!!.name
                                        } else {
                                            stringResource(R.string.restore_no_file_selected)
                                        },
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = if (selectedRestoreFile != null) {
                                            stringResource(R.string.restore_file_size, selectedRestoreFile!!.length() / 1024)
                                        } else {
                                            stringResource(R.string.restore_tap_to_choose)
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.height(14.dp))
                                    FilledTonalButton(
                                        onClick = { restoreFilePickerLauncher.launch("*/*") },
                                        shape = CircleShape
                                    ) {
                                        Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text(if (selectedRestoreFile != null) stringResource(R.string.restore_choose_another_button) else stringResource(R.string.restore_browse_button))
                                    }
                                }
                            }
                        }
                    }
                }

                if (restoreContents != null) {
                    val contents = restoreContents!!
                    item {
                        RillAnimatedSection(delayMs = 100L) {
                            Column {
                                Text(
                                    text = stringResource(R.string.restore_select_items_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp)
                                )
                                RillExpressiveCard {
                                    if (contents.version > 0 || contents.createdAt > 0L) Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(cardColor, RoundedCornerShape(cardCornerExtraSmall))
                                            .padding(16.dp)
                                    ) {
                                        // Version
                                        if (contents.version > 0) {
                                            Text(
                                                modifier = Modifier.padding(horizontal = 4.dp),
                                                text = stringResource(R.string.restore_backup_version, contents.version),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        // Date
                                        if (contents.createdAt > 0L) {
                                            val formatted = remember(contents.createdAt) { formatBackupDate(contents.createdAt) }
                                            if (formatted != null) {
                                                Spacer(Modifier.height(4.dp))
                                                Text(
                                                    modifier = Modifier.padding(horizontal = 4.dp),
                                                    text = stringResource(R.string.restore_backup_created_at, formatted),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        // Warning if the version is newer
                                        if (contents.version > BackupManager.BACKUP_VERSION) {
                                            Spacer(Modifier.height(10.dp))
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = MaterialTheme.colorScheme.errorContainer
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(8.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        Icons.Default.Warning,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onErrorContainer,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(Modifier.width(6.dp))
                                                    Text(
                                                        text = stringResource(
                                                            R.string.restore_backup_newer_version_warning,
                                                            contents.version,
                                                            BackupManager.BACKUP_VERSION
                                                        ),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        lineHeight = MaterialTheme.typography.bodyMedium.fontSize, //14.sp
                                                        color = MaterialTheme.colorScheme.onErrorContainer
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    RillSwitchListItem(
                                        headline = stringResource(R.string.settings),
                                        supporting = if (contents.hasSettings) stringResource(R.string.backup_settings_supporting) else stringResource(R.string.restore_not_in_backup),
                                        leadingIcon = ImageVector.vectorResource(id = R.drawable.ic_settings),
                                        iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkOliva,
                                        iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorOliva,
                                        checked = restoreSettings,
                                        onCheckedChange = { restoreSettings = it },
                                        enabled = contents.hasSettings
                                    )
                                    RillSwitchListItem(
                                        headline = stringResource(R.string.backup_call_backgrounds_title),
                                        supporting = if (contents.hasCallBackgrounds) stringResource(R.string.backup_call_backgrounds_supporting) else stringResource(R.string.restore_not_in_backup),
                                        leadingIcon = background_replace,
                                        iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkPurple,
                                        iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorPurple,
                                        checked = restoreCallBackgrounds,
                                        onCheckedChange = { restoreCallBackgrounds = it },
                                        enabled = contents.hasCallBackgrounds
                                    )
                                    RillSwitchListItem(
                                        headline = stringResource(R.string.contacts),
                                        supporting = if (contents.hasContacts) stringResource(R.string.backup_contacts_supporting) else stringResource(R.string.restore_not_in_backup),
                                        leadingIcon = Icons.Rounded.PeopleAlt,
                                        iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkBlue,
                                        iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorBlue,
                                        checked = restoreContacts,
                                        onCheckedChange = { restoreContacts = it },
                                        enabled = contents.hasContacts
                                    )
                                    RillSwitchListItem(
                                        headline = stringResource(R.string.private_contacts),
                                        supporting = if (contents.hasPrivateContacts) stringResource(R.string.backup_private_contacts_supporting) else stringResource(R.string.restore_not_in_backup),
                                        leadingIcon = getAccountIcon(null, true),
                                        iconContainerColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                        iconBgContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                        checked = restorePrivateContacts,
                                        onCheckedChange = { restorePrivateContacts = it },
                                        enabled = contents.hasPrivateContacts
                                    )
                                    RillSwitchListItem(
                                        headline = stringResource(R.string.recents),
                                        supporting = if (contents.hasCallLogs) stringResource(R.string.backup_call_logs_supporting) else stringResource(R.string.restore_not_in_backup),
                                        leadingIcon = Icons.Rounded.AccessTimeFilled,
                                        iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkGreen,
                                        iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorGreen,
                                        checked = restoreCallLogs,
                                        onCheckedChange = { restoreCallLogs = it },
                                        enabled = contents.hasCallLogs
                                    )
                                    RillSwitchListItem(
                                        headline = stringResource(R.string.call_notes),
                                        supporting = if (contents.hasNotes) stringResource(R.string.backup_call_notes_supporting) else stringResource(R.string.restore_not_in_backup),
                                        leadingIcon = Icons.AutoMirrored.Filled.StickyNote2,
                                        iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkAmber,
                                        iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorAmber,
                                        checked = restoreNotes,
                                        onCheckedChange = { restoreNotes = it },
                                        enabled = contents.hasNotes
                                    )
                                }
                            }
                        }
                    }

                    item {
                        RillAnimatedSection(delayMs = 150L) {
                            Button(
                                onClick = { executeRestore() },
                                enabled = hasAnyRestoreSelected,
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp)
                            ) {
                                Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.restore_selected_button), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }

            item { SettingsBottomPadding() }
        }
    }

    // ── Dialogs for States ────────────────────────────────────────────────────
    when (val state = screenState) {
        is BackupScreenState.Creating -> {
            AlertDialog(
                onDismissRequest = {},
                confirmButton = {},
                icon = { CircularProgressIndicator(modifier = Modifier.size(36.dp)) },
                title = { Text(stringResource(R.string.backup_creating_title), fontWeight = FontWeight.Bold) },
                text = { Text(stringResource(R.string.backup_creating_message)) }
            )
        }
        is BackupScreenState.Restoring -> {
            AlertDialog(
                onDismissRequest = {},
                confirmButton = {},
                icon = { CircularProgressIndicator(modifier = Modifier.size(36.dp)) },
                title = { Text(stringResource(R.string.backup_restoring_title), fontWeight = FontWeight.Bold) },
                text = { Text(stringResource(R.string.backup_restoring_message)) }
            )
        }
        is BackupScreenState.BackupSuccess -> {
            AlertDialog(
                onDismissRequest = { screenState = BackupScreenState.Idle },
                icon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = ColorGreen, modifier = Modifier.size(36.dp)) },
                title = { Text(stringResource(R.string.backup_success_title), fontWeight = FontWeight.Bold) },
                text = { Text(stringResource(R.string.backup_success_message, state.path)) },
                confirmButton = {
                    TextButton(onClick = { screenState = BackupScreenState.Idle }) {
                        Text(stringResource(R.string.ok))
                    }
                }
            )
        }
        is BackupScreenState.RestoreSuccess -> {
            AlertDialog(
                onDismissRequest = { screenState = BackupScreenState.Idle },
                icon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = ColorGreen, modifier = Modifier.size(36.dp)) },
                title = { Text(stringResource(R.string.restore_success_title), fontWeight = FontWeight.Bold) },
                text = { Text(stringResource(R.string.restore_success_message)) },
                confirmButton = {
                    TextButton(onClick = { screenState = BackupScreenState.Idle }) {
                        Text(stringResource(R.string.ok))
                    }
                }
            )
        }
        is BackupScreenState.Error -> {
            AlertDialog(
                onDismissRequest = { screenState = BackupScreenState.Idle },
                icon = { Icon(Icons.Default.Error, contentDescription = null, tint = ColorRed, modifier = Modifier.size(36.dp)) },
                title = { Text(stringResource(R.string.backup_error_title), fontWeight = FontWeight.Bold) },
                text = { Text(state.message) },
                confirmButton = {
                    TextButton(onClick = { screenState = BackupScreenState.Idle }) {
                        Text(stringResource(R.string.ok))
                    }
                }
            )
        }
        BackupScreenState.Idle -> {}
    }
}

private fun formatBackupDate(epochMillis: Long): String? {
    if (epochMillis <= 0L) return null
    return runCatching {
        SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault())
            .format(Date(epochMillis))
    }.getOrNull()
}
