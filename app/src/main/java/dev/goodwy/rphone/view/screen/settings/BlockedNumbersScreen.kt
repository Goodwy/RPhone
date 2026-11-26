// Based on RivoPhoneApp by user-grinch (© 2025-2026 RivoPhone Project / user-grinch), GPL-3.0.
// Modified by Goodwy on 2026-11-12.
package dev.goodwy.rphone.view.screen.settings

import android.content.Context
import android.telecom.TelecomManager
import android.view.Surface
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Help
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DisabledVisible
import androidx.compose.material.icons.rounded.MusicOff
import androidx.compose.material.icons.rounded.NotificationsPaused
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.BlockedNumber
import dev.goodwy.rphone.controller.util.BlockedNumbersManager
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.controller.util.toast
import dev.goodwy.rphone.view.components.NavigationIcon
import dev.goodwy.rphone.view.components.RillAnimatedSection
import dev.goodwy.rphone.view.components.RillDialog
import dev.goodwy.rphone.view.components.RillExpressiveCard
import dev.goodwy.rphone.view.components.RillListItem
import dev.goodwy.rphone.view.components.RillSelectListItem
import dev.goodwy.rphone.view.components.RillSwitchListItem
import dev.goodwy.rphone.view.components.Title
import dev.goodwy.rphone.view.theme.customColors
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun BlockedNumbersScreen(
    navigator: DestinationsNavigator
) {
    val context = LocalContext.current
    val prefs = koinInject<PreferenceManager>()
    val settingsState by prefs.settingsChanged.collectAsStateWithLifecycle()

    var blockMethod by remember(settingsState) { mutableIntStateOf(prefs.getInt(PreferenceManager.KEY_BLOCK_METHOD, 0)) }
    var logVisibility by remember(settingsState) { mutableIntStateOf(prefs.getInt(PreferenceManager.KEY_BLOCK_LOG_VISIBILITY, 0)) }
    var blockNotification by remember(settingsState) { mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_BLOCK_NOTIFICATION, true)) }

    var visible by remember { mutableStateOf(false) }
    val screenAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(350),
        label = "spamAlpha"
    )
    LaunchedEffect(Unit) { visible = true }

    val lifecycleOwner = LocalLifecycleOwner.current

    // In-app blocked numbers list (includes patterns)
    var blockedNumbersList by remember { mutableStateOf(BlockedNumbersManager.getAll(context)) }

    // Add dialog state
    var showAddDialog by remember { mutableStateOf(false) }
    var addNumberText by remember { mutableStateOf("") }

    // Delete confirmation dialog state
    var numberToDelete by remember { mutableStateOf<BlockedNumber?>(null) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                blockedNumbersList = BlockedNumbersManager.getAll(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
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
                title = { Title(stringResource(R.string.manage_blocked)) },
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
            // ── In-app blocked numbers & patterns ────────────────────────
            item {
                RillAnimatedSection(delayMs = 60L) {
                    Column {
                        val sectionTitle = if (blockedNumbersList.isEmpty()) {
                            stringResource(R.string.blocked_numbers)
                        } else {
                            "${stringResource(R.string.blocked_numbers)} (${blockedNumbersList.size})"
                        }
                        SettingsSectionLabel(sectionTitle)
                        RillExpressiveCard {
                            // Add number / pattern button
                            RillListItem(
                                headline = stringResource(R.string.add_blocked_number),
                                supporting = stringResource(R.string.add_blocked_number_subtitle),
                                leadingIcon = Icons.Outlined.Add,
                                iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkOrange,
                                iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorOrange,
                                onClick = {
                                    addNumberText = ""
                                    showAddDialog = true
                                }
                            )
                        }
                    }
                }
            }

            // List of blocked numbers / patterns
            if (blockedNumbersList.isNotEmpty()) {
                items(
                    items = blockedNumbersList,
                    key = { it.id }
                ) { blocked ->
                    RillAnimatedSection(delayMs = 0L) {
                        RillExpressiveCard {
                            RillListItem(
                                headline = blocked.originalNumber,
                                supporting = if (BlockedNumbersManager.isBlockedNumberPattern(blocked.originalNumber)) {
                                    stringResource(R.string.blocked_pattern_label)
                                } else {
                                    blocked.strippedNumber?.takeIf { it != blocked.originalNumber }
                                },
                                leadingIcon = Icons.Rounded.Block,
                                iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkRed,
                                iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorRed,
                                trailingIcon = Icons.Rounded.Close,
                                modifierTrailingIcon = Modifier.size(20.dp),
                                onClick = {
                                    numberToDelete = blocked
                                }
                            )
                        }
                    }
                }
            }

            // ── System blocked numbers link ──────────────────────────────
            item {
                RillAnimatedSection(delayMs = 120L) {
                    var showAboutNotesDialog by remember { mutableStateOf(false) }
                    Column {
                        SettingsSectionLabel(stringResource(R.string.system_settings))
                        RillExpressiveCard {
                            RillListItem(
                                headline = stringResource(R.string.system_blocked_numbers),
                                supporting = stringResource(R.string.system_blocked_numbers_subtitle),
                                leadingIcon = Icons.AutoMirrored.Rounded.List,
                                iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkOrange,
                                iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorOrange,
                                preTrailingIcon = Icons.AutoMirrored.Rounded.Help,
                                modifierPreTrailingIcon = Modifier
                                    .padding(end = 8.dp)
                                    .size(24.dp)
                                    .combinedClickable(
                                        interactionSource = null,
                                        indication = ripple(
                                            bounded = false,
                                            radius = 24.dp
                                        ),
                                        onClick = { showAboutNotesDialog = true }),
                                trailingIcon = Icons.Default.ChevronRight,
                                onClick = {
                                    val telecomManager =
                                        context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
                                    try {
                                        val intent =
                                            telecomManager.createManageBlockedNumbersIntent()
                                        context.startActivity(intent)
                                    } catch (_: Exception) {
                                        // Fallback if the intent is not supported on some older/custom versions
                                    }
                                }
                            )
                        }

                        if (showAboutNotesDialog) {
                            RillDialog(
                                onDismissRequest = { showAboutNotesDialog = false },
                                title = stringResource(R.string.system_blocked_numbers),
                                icon = Icons.AutoMirrored.Rounded.Help,
                                iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkAmber,
                                iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorAmber,
                                confirmButton = {
                                    TextButton(onClick = {
                                        showAboutNotesDialog = false
                                    }) {
                                        Text(
                                            stringResource(R.string.close),
                                            textAlign = TextAlign.End,
                                        )
                                    }
                                },
                            ) {
                                Text(stringResource(R.string.blocked_numbers_system_info))
                            }
                        }
                    }
                }
            }

            // ── Settings ─────────────────────────────────────────────────
            item {
                RillAnimatedSection(delayMs = 150L) {
                    RillExpressiveCard {
                        RillSwitchListItem(
                            headline = stringResource(R.string.blocking_notifications),
                            supporting = stringResource(R.string.blocking_notifications_subtitle),
                            leadingIcon = Icons.Rounded.NotificationsPaused,
                            iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkAmber,
                            iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorAmber,
                            checked = blockNotification,
                            onCheckedChange = {
                                blockNotification = it
                                prefs.setBoolean(PreferenceManager.KEY_BLOCK_NOTIFICATION, it)
                            }
                        )
                    }
                }
            }

            item {
                RillAnimatedSection(delayMs = 180L) {
                    RillExpressiveCard {
                        RillSelectListItem(
                            headline = stringResource(R.string.block_method),
                            supporting = stringResource(R.string.block_method_subtitle),
                            leadingIcon = if (blockMethod == 0) Icons.Rounded.CallEnd else Icons.Rounded.MusicOff,
                            iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkRed,
                            iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorRed,
                            options = listOf(
                                stringResource(R.string.decline_automatically) to 0,
                                stringResource(R.string.ring_silently) to 1
                            ),
                            selectedValue = blockMethod,
                            onValueChange = {
                                blockMethod = it
                                prefs.setInt(PreferenceManager.KEY_BLOCK_METHOD, it)
                            }
                        )
                        RillSelectListItem(
                            headline = stringResource(R.string.log_visibility),
                            supporting = stringResource(R.string.log_visibility_subtitle),
                            leadingIcon = if (logVisibility == 0) Icons.Rounded.DisabledVisible else Icons.Rounded.Visibility,
                            iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkRed,
                            iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorRed,
                            options = listOf(
                                stringResource(R.string.hide_from_logs) to 0,
                                stringResource(R.string.show_in_logs) to 1
                            ),
                            selectedValue = logVisibility,
                            onValueChange = {
                                logVisibility = it
                                prefs.setInt(PreferenceManager.KEY_BLOCK_LOG_VISIBILITY, it)
                            }
                        )
                    }
                }
            }

            item { SettingsBottomPadding() }
        }
    }

    // ── Add number / pattern dialog ──────────────────────────────────────
    if (showAddDialog) {
        val blockFailedMsg = stringResource(R.string.block_number_failed)
        RillDialog(
            onDismissRequest = { showAddDialog = false },
            title = stringResource(R.string.add_blocked_number),
            icon = Icons.Rounded.Block,
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = addNumberText.trim()
                        if (trimmed.isNotEmpty()) {
                            val success = BlockedNumbersManager.block(context, trimmed)
                            if (success) {
                                blockedNumbersList = BlockedNumbersManager.getAll(context)
                            } else {
                                context.toast(blockFailedMsg)
                            }
                        }
                        showAddDialog = false
                    },
                    enabled = addNumberText.isNotBlank()
                ) {
                    Text(
                        stringResource(R.string.block),
                        textAlign = TextAlign.End,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        ) {
            OutlinedTextField(
                value = addNumberText,
                onValueChange = { addNumberText = it },
                label = { Text(stringResource(R.string.number_or_pattern)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.pattern_help_text),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    // ── Delete confirmation dialog ───────────────────────────────────────
    numberToDelete?.let { blocked ->
        RillDialog(
            onDismissRequest = { numberToDelete = null },
            title = stringResource(R.string.unblock_number),
            icon = Icons.Rounded.Block,
            confirmButton = {
                TextButton(onClick = {
                    BlockedNumbersManager.unblockById(context, blocked.id)
                    blockedNumbersList = BlockedNumbersManager.getAll(context)
                    numberToDelete = null
                }) {
                    Text(
                        stringResource(R.string.unblock),
                        textAlign = TextAlign.End,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { numberToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        ) {
            Text(stringResource(R.string.unblock_number_confirm, blocked.originalNumber))
        }
    }
}
