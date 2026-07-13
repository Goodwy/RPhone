package dev.goodwy.rphone.view.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.CallLog
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.ramcosta.composedestinations.generated.destinations.ContactDetailsScreenDestination
import com.ramcosta.composedestinations.generated.destinations.ContactDetailsScreenDestination.invoke
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.formatDate
import dev.goodwy.rphone.controller.util.formatSecondsToShortTimeString
import dev.goodwy.rphone.controller.util.getPhoneTypeText
import dev.goodwy.rphone.controller.util.toast
import dev.goodwy.rphone.modal.data.CallLogEntry
import dev.goodwy.rphone.view.theme.color_call_button
import dev.goodwy.rphone.view.theme.customColors

@Composable
fun CallLogTileSimple(
    log: CallLogEntry,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    onCallClick: () -> Unit = {},
    selected: Boolean = false,
    showNumber: Boolean,
    showSimLabel: Boolean
) {
    val icon = when (log.type) {
        CallLog.Calls.INCOMING_TYPE -> Icons.AutoMirrored.Filled.CallReceived
        CallLog.Calls.OUTGOING_TYPE -> Icons.AutoMirrored.Filled.CallMade
        CallLog.Calls.MISSED_TYPE   -> Icons.AutoMirrored.Filled.CallMissed
        CallLog.Calls.REJECTED_TYPE   -> Icons.AutoMirrored.Filled.CallMissed
        CallLog.Calls.BLOCKED_TYPE   -> Icons.Filled.Block
        else                        -> Icons.Default.Call
    }
    val context   = LocalContext.current
    val typeText = when (log.type) {
        CallLog.Calls.INCOMING_TYPE -> stringResource(R.string.incoming)
        CallLog.Calls.OUTGOING_TYPE -> stringResource(R.string.outgoing)
        CallLog.Calls.MISSED_TYPE   -> stringResource(R.string.missed)
        CallLog.Calls.REJECTED_TYPE -> stringResource(R.string.rejected)
        CallLog.Calls.BLOCKED_TYPE  -> stringResource(R.string.blocked)
        else                        -> stringResource(R.string.call)
    }
    val headlineText = if (showNumber) log.number + "  •  " + typeText else typeText
    val simLabel = if (showSimLabel && log.simLabel != null) " • " + log.simLabel else ""
    CallLogListItemSimple(
        headline = headlineText,
        supporting = context.formatDate(log.date) + simLabel,
//        trailing = if (log.duration > 0) "${android.text.format.DateUtils.formatElapsedTime(log.duration)}" else "",
        trailing = if (log.duration > 0) context.formatSecondsToShortTimeString(log.duration.toInt()) else "",
        leadingIcon = icon,
        iconContainerColor = if (log.type == CallLog.Calls.MISSED_TYPE || log.type == CallLog.Calls.REJECTED_TYPE) MaterialTheme.colorScheme.error else null,
        onClick = onClick,
        onLongClick = onLongClick,
        selected = selected
    )
}

@Composable
fun CallLogTile(
    log: CallLogEntry,
    directCall: Boolean,
    onTileClick: (CallLogEntry) -> Unit,
    onLongClick: (CallLogEntry) -> Unit,
    onCallClick: (CallLogEntry) -> Unit,
    onAvatarClick: ((CallLogEntry) -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onShowHistory: () -> Unit,
    isSelected: Boolean = false,
    selectionMode: Boolean = false,
    showSimLabel: Boolean
) {
    val context   = LocalContext.current
    var showMenu  by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        val simLabel = if (showSimLabel && log.simLabel != null) " • " + log.simLabel else ""
        CallLogListItem(
            headline = buildString {
                append(log.name ?: log.number)
                if (log.count > 1) append(" (${log.count})")
            },
            supporting = buildString {
                if (log.name != null && log.name != log.number) {
                    val typeLabel = getPhoneTypeText(context, log.phoneType, log.phoneLabel)
                    val number = typeLabel ?: log.number
                    append(number + " • " + context.formatDate(log.date, true) + simLabel)
                }
                else append(context.formatDate(log.date, true) + simLabel)
            },
            avatarName  = log.name ?: log.number,
            photoUri    = log.photoUri,
            supportingIcon = when (log.type) {
                CallLog.Calls.MISSED_TYPE   -> Icons.AutoMirrored.Filled.CallMissed
                CallLog.Calls.INCOMING_TYPE -> Icons.AutoMirrored.Filled.CallReceived
                CallLog.Calls.OUTGOING_TYPE -> Icons.AutoMirrored.Filled.CallMade
                CallLog.Calls.REJECTED_TYPE -> Icons.AutoMirrored.Filled.CallMissed
                CallLog.Calls.BLOCKED_TYPE  -> Icons.Filled.Block
                else                        -> Icons.Rounded.Call
            },
            supportingColor = when (log.type) {
                CallLog.Calls.MISSED_TYPE, CallLog.Calls.REJECTED_TYPE -> MaterialTheme.colorScheme.error
                else                        -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f)
            },
            onCallClick = { onCallClick(log) },
            onAvatarClick = { if (onAvatarClick != null) onAvatarClick(log) else null
            },
            onAvatarLongClick = {
                showMenu = true
            },
            directCall = directCall,
            onLongClick = {
                onLongClick(log)
            },
            isMenuOpen  = showMenu && !selectionMode,
            onClick     = { onTileClick(log) },
            isSelected = isSelected,
            selectionMode = selectionMode,
        )

        AnimatedVisibility(
            visible = showMenu,
            enter = slideInVertically(initialOffsetY = { -it }, animationSpec = tween(320, easing = FastOutSlowInEasing)) + fadeIn(tween(280)),
            exit  = slideOutVertically(targetOffsetY = { -it }, animationSpec = tween(420, easing = FastOutLinearInEasing)) + fadeOut(tween(380))
        ) {
            DropdownMenu(
                shape = RoundedCornerShape(16.dp),
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                offset = DpOffset(56.dp, 64.dp),
            ) {
                DropdownMenuItem(
                    contentPadding = PaddingValues(start = 20.dp, end = 26.dp),
                    text = { Text(stringResource(R.string.select)) },
                    leadingIcon = { Icon(Icons.Default.CheckBox, null) },
                    onClick = { showMenu = false; onLongClick(log) }
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
                DropdownMenuItem(
                    contentPadding = PaddingValues(start = 20.dp, end = 26.dp),
                    text = { Text("Call back") },
                    leadingIcon = { Icon(Icons.Rounded.Call, null) },
                    onClick = { showMenu = false; onCallClick(log) }
                )
                if (log.number.isNotBlank()) {
                    val phoneNumber = log.number
                    DropdownMenuItem(
                        contentPadding = PaddingValues(start = 20.dp, end = 26.dp),
                        text = { Text("Send SMS") },
                        leadingIcon = { Icon(ImageVector.vectorResource(id = R.drawable.ic_message_filled), null) },
                        onClick = {
                            showMenu = false
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                data = "sms:$phoneNumber".toUri()
                            }
                            context.startActivity(intent)
                        }
                    )
                }
                DropdownMenuItem(
                    contentPadding = PaddingValues(start = 20.dp, end = 26.dp),
                    text = { Text("Copy number") },
                    leadingIcon = { Icon(Icons.Default.ContentCopy, null) },
                    onClick = {
                        showMenu = false
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Phone number", log.number))
                        context.toast("Number copied")
                    }
                )
//                DropdownMenuItem(
//                    contentPadding = PaddingValues(start = 20.dp, end = 26.dp),
//                    text = { Text("Block number") },
//                    leadingIcon = { Icon(Icons.Default.Block, null) },
//                    onClick = {
//                        showMenu = false
//                        context.toast("Number blocked")
//                    }
//                )
                DropdownMenuItem(
                    contentPadding = PaddingValues(start = 20.dp, end = 26.dp),
                    text = { Text("Show full history") },
                    leadingIcon = { Icon(Icons.Default.AccessTime, null) },
                    onClick = {
                        showMenu = false
                        onShowHistory()
                    }
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
                DropdownMenuItem(
                    contentPadding = PaddingValues(start = 20.dp, end = 24.dp),
                    text = { Text("Delete from call log", color = MaterialTheme.colorScheme.error) },
                    leadingIcon = { Icon(ImageVector.vectorResource(id = R.drawable.ic_delete), null, tint = MaterialTheme.colorScheme.error) },
                    onClick = {
                        showMenu = false
                        try {
                            // Delete only this specific call log entry by its exact timestamp
                            context.contentResolver.delete(
                                CallLog.Calls.CONTENT_URI,
                                "${CallLog.Calls.NUMBER} = ? AND ${CallLog.Calls.DATE} = ?",
                                arrayOf(log.number, log.date.toString())
                            )
                            onDelete?.invoke()
                            context.toast("Deleted from call log")
                        } catch (_: Exception) {
                            context.toast("Could not delete")
                        }
                    }
                )
            }
        }
//        RillDropdownMenu(
//            expanded          = showMenu,
//            onDismissRequest  = { showMenu = false }
//        ) {
//            RillDropdownMenuItem(
//                text     = stringResource(R.string.select),
//                icon     = Icons.Default.CheckBox,
//                iconTint = Color(0xFF9C27B0),
//                onClick  = {
//                    showMenu = false
//                    onLongClick(log)
//                }
//            )
//            HorizontalDivider(
//                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
//                color    = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
//            )
//            RillDropdownMenuItem(
//                text     = "Call back",
//                icon     = Icons.Default.Call,
//                iconTint = color_call_button,
//                onClick  = { showMenu = false; onCallClick(log) }
//            )
//            RillDropdownMenuItem(
//                text     = "Copy number",
//                icon     = Icons.Default.ContentCopy,
//                iconTint = Color(0xFF2196F3),
//                onClick  = {
//                    showMenu = false
//                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
//                    clipboard.setPrimaryClip(ClipData.newPlainText("Phone number", log.number))
//                    Toast.makeText(context, "Number copied", Toast.LENGTH_SHORT).show()
//                }
//            )
//            RillDropdownMenuItem(
//                text     = "Block number",
//                icon     = Icons.Default.Block,
//                iconTint = Color(0xFFFF9800),
//                onClick  = {
//                    showMenu = false
//                    Toast.makeText(context, "Number blocked", Toast.LENGTH_SHORT).show()
//                }
//            )
//            RillDropdownMenuItem(
//                text     = "Show full history",
//                icon     = Icons.Default.AccessTime,
//                iconTint = Color(0xFF00BCD4),
//                onClick  = {
//                    showMenu = false
//                    onShowHistory()
//                }
//            )
//            HorizontalDivider(
//                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
//                color    = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
//            )
//            RillDropdownMenuItem(
//                text          = "Delete from call log",
//                icon          = ImageVector.vectorResource(id = R.drawable.ic_delete),
//                isDestructive = true,
//                onClick       = {
//                    showMenu = false
//                    try {
//                        // Delete only this specific call log entry by its exact timestamp
//                        context.contentResolver.delete(
//                            CallLog.Calls.CONTENT_URI,
//                            "${CallLog.Calls.NUMBER} = ? AND ${CallLog.Calls.DATE} = ?",
//                            arrayOf(log.number, log.date.toString())
//                        )
//                        onDelete?.invoke()
//                        Toast.makeText(context, "Deleted from call log", Toast.LENGTH_SHORT).show()
//                    } catch (e: Exception) {
//                        Toast.makeText(context, "Could not delete", Toast.LENGTH_SHORT).show()
//                    }
//                }
//            )
//        }
    }
}

@Composable
fun BatchCallLogActionBar(
    selectedCount: Int,
    onClearSelection: () -> Unit,
    onDelete: () -> Unit,
    onClearAll: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    onCallLogs: (() -> Unit)? = null,
    onDeselect: () -> Unit,
    onSelectAll: () -> Unit,
    isAllSelected: Boolean
) {
//    var showSelectionMenuOuter by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showClearAllConfirm by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth(),
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClearSelection) {
                Icon(Icons.Default.Close, "Clear selection")
            }
            TextButton(
                onClick = if (isAllSelected) onDeselect else onSelectAll
            ) {
                Text(
                    text = "$selectedCount selected",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            if (onCallLogs != null) {
                IconButton(onClick = onCallLogs) {
                    Icon(Icons.Rounded.AccessTime, "Show full history")
                }
            }
            if (onClearAll != null) {
                IconButton(onClick = { showClearAllConfirm = true }) {
                    Icon(ImageVector.vectorResource(id = R.drawable.ic_delete_sweep), "Clear all logs")
                }
            }
            IconButton(onClick = { showDeleteConfirm = true }) {
                Icon(ImageVector.vectorResource(id = R.drawable.ic_delete), "Delete selected")
            }
            if (onShare != null) {
                IconButton(onClick = onShare) {
                    Icon(Icons.Default.Share, stringResource(R.string.share))
                }
            }

//            Box {
//                IconButton(onClick = { showSelectionMenuOuter = true }) {
//                    Icon(Icons.Default.MoreVert, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
//                }
//                DropdownMenu(shape = RoundedCornerShape(16.dp), expanded = showSelectionMenuOuter, onDismissRequest = { showSelectionMenuOuter = false }) {
//                    DropdownMenuItem(
//                        text = { Text(stringResource(R.string.share)) },
//                        leadingIcon = { Icon(Icons.Default.Share, null) },
//                        onClick = {
//                            showSelectionMenuOuter = false
//                            onShare()
//                        }
//                    )
////                    DropdownMenuItem(
////                        text = { Text("Deselect All") },
////                        leadingIcon = { Icon(Icons.Rounded.Deselect, "Deselect All") },
////                        onClick = {
////                            showSelectionMenuOuter = false
////                            onDeselect()
////                        }
////                    )
//                    if (!isAllSelected) {
//                        DropdownMenuItem(
//                            text = { Text("Select All") },
//                            leadingIcon = { Icon(Icons.Rounded.SelectAll, "Select All") },
//                            onClick = {
//                                showSelectionMenuOuter = false
//                                onSelectAll()
//                            }
//                        )
//                    }
//                }
//            }
        }
    }

    if (showDeleteConfirm) {
        RillDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = "Delete Call Logs",
            icon = ImageVector.vectorResource(id = R.drawable.ic_delete),
            iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkRed,
            iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorRed,
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
                    showDeleteConfirm = false
                }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        ) {
            Text(
                "Are you sure you want to delete $selectedCount selected call logs?",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    if (showClearAllConfirm) {
        RillDialog(
            onDismissRequest = { showClearAllConfirm = false },
            title = "Clear all filtered logs",
            icon = ImageVector.vectorResource(id = R.drawable.ic_delete_sweep),
            iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkRed,
            iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorRed,
            confirmButton = {
                TextButton(onClick = {
                    onClearAll?.invoke()
                    showClearAllConfirm = false
                }) {
                    Text("Clear All", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        ) {
            Text(
                "This will delete all displayed call history. This action cannot be undone.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
