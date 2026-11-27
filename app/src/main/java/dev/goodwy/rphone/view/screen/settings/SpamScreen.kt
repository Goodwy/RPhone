package dev.goodwy.rphone.view.screen.settings

import android.content.Context
import android.os.Build
import android.view.Surface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PhoneDisabled
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.BlockedNumbersScreenDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.controller.util.getCallScreeningRoleIntent
import dev.goodwy.rphone.controller.util.isCallScreeningRoleAvailable
import dev.goodwy.rphone.controller.util.isCallScreeningRoleHeld
import dev.goodwy.rphone.controller.util.toast
import dev.goodwy.rphone.modal.data.SpamActionMode
import dev.goodwy.rphone.modal.data.SpamCategory
import dev.goodwy.rphone.modal.db.SpamNumberEntity
import dev.goodwy.rphone.modal.repository.SpamRepository
import dev.goodwy.rphone.view.components.NavigationIcon
import dev.goodwy.rphone.view.components.RillAnimatedSection
import dev.goodwy.rphone.view.components.RillDialog
import dev.goodwy.rphone.view.components.RillExpressiveCard
import dev.goodwy.rphone.view.components.RillListItem
import dev.goodwy.rphone.view.components.RillSelectListItem
import dev.goodwy.rphone.view.components.RillSwitchListItem
import dev.goodwy.rphone.view.components.Title
import dev.goodwy.rphone.view.theme.customColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun SpamScreen(navigator: DestinationsNavigator) {
    val prefs = koinInject<PreferenceManager>()
    val spamRepository = koinInject<SpamRepository>()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsState by prefs.settingsChanged.collectAsStateWithLifecycle()

    val spamNumbers by spamRepository.observeSpamNumbers().collectAsStateWithLifecycle(initialValue = emptyList())

    var protectionEnabled by remember(settingsState) {
        mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_SPAM_PROTECTION_ENABLED, true))
    }
    var spamActionMode by remember(settingsState) {
        mutableIntStateOf(prefs.getInt(PreferenceManager.KEY_SPAM_ACTION, SpamActionMode.SILENCE.value))
    }
    var silenceUnknown by remember(settingsState) {
        mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_SILENCE_UNKNOWN, false))
    }
    var blockPrivate by remember(settingsState) {
        mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_SPAM_BLOCK_PRIVATE, false))
    }
    var stirShakenEnabled by remember(settingsState) {
        mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_SPAM_STIR_SHAKEN_ENABLED, true))
    }
    var burstProtectionEnabled by remember(settingsState) {
        mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_SPAM_BURST_PROTECTION, true))
    }
    var neighborSpoofingEnabled by remember(settingsState) {
        mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_SPAM_NEIGHBOR_SPOOFING, false))
    }
    var spamNotifications by remember(settingsState) {
        mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_SPAM_NOTIFICATION, true))
    }

    var isRoleAvailable by remember { mutableStateOf(isCallScreeningRoleAvailable(context)) }
    var isRoleHeld by remember { mutableStateOf(isCallScreeningRoleHeld(context)) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isRoleAvailable = isCallScreeningRoleAvailable(context)
                isRoleHeld = isCallScreeningRoleHeld(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        spamRepository.migrateLegacyBlockedContactsIfNeeded()
    }

    val roleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        isRoleHeld = isCallScreeningRoleHeld(context)
    }

    val resources = LocalResources.current
    val importEmptyMsg = stringResource(R.string.spam_import_empty)
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val rawText = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                }.getOrNull()
            }
            if (rawText.isNullOrBlank()) {
                context.toast(importEmptyMsg)
                return@launch
            }
            val result = spamRepository.importBlocklist(rawText)
            if (result.importedCount > 0) {
                context.toast(
                    resources.getString(
                        R.string.spam_import_success,
                        result.importedCount,
                        result.skippedInvalidCount
                    )
                )
            } else {
                context.toast(importEmptyMsg)
            }
        }
    }

    val exportSuccessMsg = stringResource(R.string.spam_export_success)
    val exportEmptyMsg = stringResource(R.string.spam_export_empty)
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val csv = spamRepository.exportBlocklistCsv()
            withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(csv) }
                }
            }
            context.toast(exportSuccessMsg)
        }
    }

    var showReportDialog by remember { mutableStateOf(false) }
    var reportNumberInput by remember { mutableStateOf("") }
    var reportCategory by remember { mutableStateOf(SpamCategory.SPAM) }
    var reportAlsoBlock by remember { mutableStateOf(false) }

    var showSpamListDialog by remember { mutableStateOf(false) }
    var entryToRemove by remember { mutableStateOf<SpamNumberEntity?>(null) }
    var showClearImportedConfirm by remember { mutableStateOf(false) }

    var visible by remember { mutableStateOf(false) }
    val screenAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(350),
        label = "spamAlpha"
    )
    LaunchedEffect(Unit) { visible = true }

    @Suppress("DEPRECATION")
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
                title = { Title(stringResource(R.string.spam_protection)) },
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
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ── On-device privacy & system role card ─────────────────────
            item {
                RillAnimatedSection(delayMs = 0L) {
                    RillExpressiveCard {
                        RillListItem(
                            headline = stringResource(R.string.spam_privacy_badge_title),
                            supporting = stringResource(R.string.spam_privacy_badge_subtitle),
                            leadingIcon = Icons.Rounded.PrivacyTip,
                            iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkGreen,
                            iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorGreen,
                            onClick = {}
                        )
                        if (isRoleAvailable) {
                            RillListItem(
                                headline = stringResource(
                                    if (isRoleHeld) R.string.spam_screening_role_active
                                    else R.string.spam_screening_role_title
                                ),
                                supporting = stringResource(
                                    if (isRoleHeld) R.string.spam_screening_role_active_subtitle
                                    else R.string.spam_screening_role_subtitle
                                ),
                                leadingIcon = if (isRoleHeld) Icons.Rounded.VerifiedUser else Icons.Rounded.Shield,
                                iconContainerColor = if (isRoleHeld) {
                                    MaterialTheme.colorScheme.customColors.colorDarkGreen
                                } else {
                                    MaterialTheme.colorScheme.customColors.colorDarkAmber
                                },
                                iconBgContainerColor = if (isRoleHeld) {
                                    MaterialTheme.colorScheme.customColors.colorGreen
                                } else {
                                    MaterialTheme.colorScheme.customColors.colorAmber
                                },
                                trailingIcon = if (isRoleHeld) null else Icons.Default.ChevronRight,
                                onClick = {
                                    if (!isRoleHeld) {
                                        runCatching {
                                            roleLauncher.launch(getCallScreeningRoleIntent(context))
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // ── Master toggle & action mode ──────────────────────────────
            item {
                RillAnimatedSection(delayMs = 50L) {
                    RillExpressiveCard {
                        RillSwitchListItem(
                            headline = stringResource(R.string.spam_enable_protection),
                            supporting = stringResource(R.string.spam_enable_protection_subtitle),
                            leadingIcon = Icons.Rounded.Security,
                            iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkPurple,
                            iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorPurple,
                            checked = protectionEnabled,
                            onCheckedChange = {
                                protectionEnabled = it
                                prefs.setBoolean(PreferenceManager.KEY_SPAM_PROTECTION_ENABLED, it)
                            }
                        )
                        RillSelectListItem(
                            headline = stringResource(R.string.spam_action_mode),
                            supporting = stringResource(R.string.spam_action_mode_subtitle),
                            leadingIcon = when (spamActionMode) {
                                SpamActionMode.WARN.value -> Icons.Rounded.Warning
                                SpamActionMode.DECLINE.value -> Icons.Rounded.CallEnd
                                else -> Icons.Rounded.PhoneDisabled
                            },
                            iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkRed,
                            iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorRed,
                            options = listOf(
                                stringResource(R.string.spam_action_warn) to SpamActionMode.WARN.value,
                                stringResource(R.string.spam_action_silence) to SpamActionMode.SILENCE.value,
                                stringResource(R.string.spam_action_decline) to SpamActionMode.DECLINE.value
                            ),
                            selectedValue = spamActionMode,
                            onValueChange = {
                                spamActionMode = it
                                prefs.setInt(PreferenceManager.KEY_SPAM_ACTION, it)
                            }
                        )
                    }
                }
            }

            // ── Local detection rules ────────────────────────────────────
            item {
                RillAnimatedSection(delayMs = 100L) {
                    Column {
                        SettingsSectionLabel(stringResource(R.string.spam_detection_rules))
                        RillExpressiveCard {
                            RillSwitchListItem(
                                headline = stringResource(R.string.spam_silence_unknown),
                                supporting = stringResource(R.string.spam_silence_unknown_subtitle),
                                leadingIcon = Icons.Rounded.PhoneDisabled,
                                iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkPurple,
                                iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorPurple,
                                checked = silenceUnknown,
                                onCheckedChange = {
                                    silenceUnknown = it
                                    prefs.setBoolean(PreferenceManager.KEY_SILENCE_UNKNOWN, it)
                                }
                            )
                            RillSwitchListItem(
                                headline = stringResource(R.string.spam_block_private),
                                supporting = stringResource(R.string.spam_block_private_subtitle),
                                leadingIcon = Icons.Rounded.VisibilityOff,
                                iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkOrange,
                                iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorOrange,
                                checked = blockPrivate,
                                enabled = protectionEnabled,
                                onCheckedChange = {
                                    blockPrivate = it
                                    prefs.setBoolean(PreferenceManager.KEY_SPAM_BLOCK_PRIVATE, it)
                                }
                            )
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                RillSwitchListItem(
                                    headline = stringResource(R.string.spam_stir_shaken),
                                    supporting = stringResource(R.string.spam_stir_shaken_subtitle),
                                    leadingIcon = Icons.Rounded.VerifiedUser,
                                    iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkBlue,
                                    iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorBlue,
                                    checked = stirShakenEnabled,
                                    enabled = protectionEnabled,
                                    onCheckedChange = {
                                        stirShakenEnabled = it
                                        prefs.setBoolean(PreferenceManager.KEY_SPAM_STIR_SHAKEN_ENABLED, it)
                                    }
                                )
                            }
                            RillSwitchListItem(
                                headline = stringResource(R.string.spam_burst_protection),
                                supporting = stringResource(R.string.spam_burst_protection_subtitle),
                                leadingIcon = Icons.Rounded.Repeat,
                                iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkAmber,
                                iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorAmber,
                                checked = burstProtectionEnabled,
                                enabled = protectionEnabled,
                                onCheckedChange = {
                                    burstProtectionEnabled = it
                                    prefs.setBoolean(PreferenceManager.KEY_SPAM_BURST_PROTECTION, it)
                                }
                            )
                            RillSwitchListItem(
                                headline = stringResource(R.string.spam_neighbor_spoofing),
                                supporting = stringResource(R.string.spam_neighbor_spoofing_subtitle),
                                leadingIcon = Icons.Rounded.Radar,
                                iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkRed,
                                iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorRed,
                                checked = neighborSpoofingEnabled,
                                enabled = protectionEnabled,
                                onCheckedChange = {
                                    neighborSpoofingEnabled = it
                                    prefs.setBoolean(PreferenceManager.KEY_SPAM_NEIGHBOR_SPOOFING, it)
                                }
                            )
                            RillSwitchListItem(
                                headline = stringResource(R.string.spam_notifications),
                                supporting = stringResource(R.string.spam_notifications_subtitle),
                                leadingIcon = Icons.Rounded.NotificationsActive,
                                iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkAmber,
                                iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorAmber,
                                checked = spamNotifications,
                                enabled = protectionEnabled,
                                onCheckedChange = {
                                    spamNotifications = it
                                    prefs.setBoolean(PreferenceManager.KEY_SPAM_NOTIFICATION, it)
                                }
                            )
                        }
                    }
                }
            }

            // ── Local spam database & offline blocklists ─────────────────
            item {
                RillAnimatedSection(delayMs = 150L) {
                    Column {
                        SettingsSectionLabel(stringResource(R.string.spam_database_section))
                        RillExpressiveCard {
                            RillListItem(
                                headline = stringResource(R.string.spam_add_number),
                                supporting = stringResource(R.string.spam_add_number_subtitle),
                                leadingIcon = Icons.Outlined.Add,
                                iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkOrange,
                                iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorOrange,
                                onClick = {
                                    reportNumberInput = ""
                                    reportCategory = SpamCategory.SPAM
                                    reportAlsoBlock = false
                                    showReportDialog = true
                                }
                            )
                            RillListItem(
                                headline = if (spamNumbers.isEmpty()) {
                                    stringResource(R.string.spam_view_list_empty)
                                } else {
                                    stringResource(R.string.spam_view_list, spamNumbers.size)
                                },
                                supporting = stringResource(R.string.spam_view_list_subtitle),
                                leadingIcon = Icons.Rounded.Block,
                                iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkRed,
                                iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorRed,
                                trailingIcon = Icons.Default.ChevronRight,
                                onClick = { showSpamListDialog = true }
                            )
                            RillListItem(
                                headline = stringResource(R.string.spam_import_blocklist),
                                supporting = stringResource(R.string.spam_import_blocklist_subtitle),
                                leadingIcon = Icons.Rounded.FileUpload,
                                iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkBlue,
                                iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorBlue,
                                onClick = {
                                    importLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "*/*"))
                                }
                            )
                            RillListItem(
                                headline = stringResource(R.string.spam_export_blocklist),
                                supporting = stringResource(R.string.spam_export_blocklist_subtitle),
                                leadingIcon = Icons.Rounded.FileDownload,
                                iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkGreen,
                                iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorGreen,
                                onClick = {
                                    if (spamNumbers.isEmpty()) {
                                        context.toast(exportEmptyMsg)
                                    } else {
                                        exportLauncher.launch("rill_spam_list.csv")
                                    }
                                }
                            )
                            if (spamNumbers.any { it.source == SpamNumberEntity.SOURCE_IMPORTED_LIST }) {
                                RillListItem(
                                    headline = stringResource(R.string.spam_clear_imported),
                                    leadingIcon = Icons.Rounded.DeleteSweep,
                                    iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkRed,
                                    iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorRed,
                                    onClick = { showClearImportedConfirm = true }
                                )
                            }
                            RillListItem(
                                headline = stringResource(R.string.spam_manage_blocked_link),
                                supporting = stringResource(R.string.spam_manage_blocked_link_subtitle),
                                leadingIcon = Icons.Rounded.Block,
                                iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkRed,
                                iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorRed,
                                trailingIcon = Icons.Default.ChevronRight,
                                onClick = { navigator.navigate(BlockedNumbersScreenDestination) }
                            )
                        }
                    }
                }
            }

            item { SettingsBottomPadding() }
        }
    }

    // ── Report Spam Number Dialog ────────────────────────────────────────
    if (showReportDialog) {
        val invalidMsg = stringResource(R.string.spam_invalid_number_toast)
        RillDialog(
            onDismissRequest = { showReportDialog = false },
            title = stringResource(R.string.spam_add_number),
            icon = Icons.Rounded.Warning,
            confirmButton = {
                TextButton(
                    onClick = {
                        val raw = reportNumberInput.trim()
                        scope.launch {
                            val ok = spamRepository.reportSpam(
                                number = raw,
                                category = reportCategory,
                                alsoBlock = reportAlsoBlock
                            )
                            if (!ok) {
                                context.toast(invalidMsg)
                            }
                            showReportDialog = false
                        }
                    },
                    enabled = reportNumberInput.isNotBlank()
                ) {
                    Text(stringResource(R.string.spam_report_action), textAlign = TextAlign.End)
                }
            },
            dismissButton = {
                TextButton(onClick = { showReportDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = reportNumberInput,
                    onValueChange = { reportNumberInput = it },
                    label = { Text(stringResource(R.string.number_or_pattern)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = stringResource(R.string.spam_category_label),
                    style = MaterialTheme.typography.labelLarge
                )
                val selectableCategories = listOf(
                    SpamCategory.SPAM to R.string.spam_category_spam,
                    SpamCategory.TELEMARKETER to R.string.spam_category_telemarketer,
                    SpamCategory.SCAM to R.string.spam_category_scam,
                    SpamCategory.ROBOCALL to R.string.spam_category_robocall
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    selectableCategories.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            pair.forEach { (cat, labelRes) ->
                                FilterChip(
                                    selected = reportCategory == cat,
                                    onClick = { reportCategory = cat },
                                    label = { Text(stringResource(labelRes)) }
                                )
                            }
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = reportAlsoBlock,
                        onCheckedChange = { reportAlsoBlock = it }
                    )
                    Text(
                        text = stringResource(R.string.spam_also_block_system),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }

    // ── Local Spam List Dialog ───────────────────────────────────────────
    if (showSpamListDialog) {
        RillDialog(
            onDismissRequest = { showSpamListDialog = false },
            title = stringResource(R.string.spam_database_section),
            icon = Icons.Rounded.Security,
            confirmButton = {
                TextButton(onClick = { showSpamListDialog = false }) {
                    Text(stringResource(R.string.close))
                }
            }
        ) {
            if (spamNumbers.isEmpty()) {
                Text(
                    text = stringResource(R.string.spam_view_list_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(spamNumbers, key = { it.normalizedNumber }) { entry ->
                        RillExpressiveCard {
                            RillListItem(
                                headline = entry.rawNumber,
                                supporting = spamCategoryLabel(SpamCategory.fromStorageKey(entry.category)) +
                                    (entry.label?.let { " • $it" } ?: ""),
                                leadingIcon = Icons.Rounded.Warning,
                                iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkRed,
                                iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorRed,
                                trailingIcon = Icons.Rounded.Close,
                                modifierTrailingIcon = Modifier.size(20.dp),
                                onClick = { entryToRemove = entry }
                            )
                        }
                    }
                }
            }
        }
    }

    // ── Remove Spam Entry Confirmation ───────────────────────────────────
    entryToRemove?.let { entry ->
        RillDialog(
            onDismissRequest = { entryToRemove = null },
            title = stringResource(R.string.spam_database_section),
            icon = Icons.Rounded.Block,
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        spamRepository.removeSpamNumber(entry.normalizedNumber)
                        entryToRemove = null
                    }
                }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { entryToRemove = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        ) {
            Text(stringResource(R.string.spam_remove_confirm, entry.rawNumber))
        }
    }

    // ── Clear Imported Blocklist Confirmation ────────────────────────────
    if (showClearImportedConfirm) {
        RillDialog(
            onDismissRequest = { showClearImportedConfirm = false },
            title = stringResource(R.string.spam_clear_imported),
            icon = Icons.Rounded.DeleteSweep,
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        spamRepository.clearImportedBlocklist()
                        showClearImportedConfirm = false
                    }
                }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearImportedConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        ) {
            Text(stringResource(R.string.spam_clear_imported_confirm))
        }
    }
}

@Composable
private fun spamCategoryLabel(category: SpamCategory): String {
    val resId = when (category) {
        SpamCategory.TELEMARKETER -> R.string.spam_category_telemarketer
        SpamCategory.SCAM -> R.string.spam_category_scam
        SpamCategory.ROBOCALL -> R.string.spam_category_robocall
        SpamCategory.SPOOFED -> R.string.spam_category_spoofed
        SpamCategory.PRIVATE_NUMBER -> R.string.spam_category_private
        SpamCategory.UNKNOWN_CALLER -> R.string.spam_category_unknown
        SpamCategory.USER_REPORTED -> R.string.spam_category_reported
        SpamCategory.SPAM, SpamCategory.NONE -> R.string.spam_category_spam
    }
    return stringResource(resId)
}
