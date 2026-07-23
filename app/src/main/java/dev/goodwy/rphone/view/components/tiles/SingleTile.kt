package dev.goodwy.rphone.view.components.tiles

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.ContactsContract
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.view.components.RillAvatar
import dev.goodwy.rphone.view.components.performAppHaptic
import dev.goodwy.rphone.view.theme.MyColors.cardColor
import org.koin.compose.koinInject
import androidx.core.net.toUri

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SingleTile(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    photoUri: String? = null,
    icon: ImageVector? = null,
    iconContainerColor: Color? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    supportingContent: (@Composable () -> Unit)? = null,
    titleTrailing: (@Composable () -> Unit)? = null,
    isMissedCall: Boolean = false,
    phoneNumber: String? = null,
    onAvatarClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    useLongClick: Boolean = true,
    showAddToContact: Boolean = false,
    onSelectMode: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val haptic  = LocalHapticFeedback.current
    val prefs   = koinInject<PreferenceManager>()
    var showMenu              by remember { mutableStateOf(false) }
    var isPressed             by remember { mutableStateOf(false) }
    var horizontalDragDetected by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue    = if (showMenu) 0.97f else if (isPressed) 0.95f else 1f,
        animationSpec  = spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioMediumBouncy),
        label          = "TileScale"
    )

    val numberForMenu = phoneNumber ?: subtitle?.filter { it.isDigit() || it == '+' }
        ?.takeIf { it.length >= 5 } ?: subtitle

    Surface(
        shape = RoundedCornerShape(4.dp),
        color = cardColor,
        modifier = Modifier.fillMaxWidth().scale(scale)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {
                        if (horizontalDragDetected) return@combinedClickable
                        isPressed = false
                        if (prefs.getBoolean(PreferenceManager.KEY_APP_HAPTICS, true)) {
                            performAppHaptic(
                                context,
                                prefs.getString(PreferenceManager.KEY_APP_HAPTICS_STRENGTH, "light") ?: "light",
                                prefs.getFloat(PreferenceManager.KEY_HAPTICS_CUSTOM_INTENSITY, 0.5f)
                            )
                        }
                        onClick()
                    },
                    onLongClick = if (useLongClick) {
                        {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (onLongClick != null) onLongClick()
                            else showMenu = true
                        }
                    } else null
                )
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        isPressed = true
                        horizontalDragDetected = false
                        val downPos = down.position
                        do {
                            val event   = awaitPointerEvent()
                            val current = event.changes.firstOrNull() ?: break
                            val dx = kotlin.math.abs(current.position.x - downPos.x)
                            val dy = kotlin.math.abs(current.position.y - downPos.y)
                            if (dx > 28.dp.toPx() && dx > dy * 1.3f) horizontalDragDetected = true
                            if (!current.pressed) break
                        } while (true)
                        isPressed = false
                    }
                }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RillAvatar(
                name               = title,
                photoUri           = photoUri,
                icon               = icon,
                iconContainerColor = iconContainerColor,
                modifier           = Modifier
                    .size(42.dp)
                    .then(
                        if (onAvatarClick != null)
                            Modifier.combinedClickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    if (prefs.getBoolean(PreferenceManager.KEY_APP_HAPTICS, true)) {
                                        performAppHaptic(
                                            context,
                                            prefs.getString(PreferenceManager.KEY_APP_HAPTICS_STRENGTH, "light") ?: "light",
                                            prefs.getFloat(PreferenceManager.KEY_HAPTICS_CUSTOM_INTENSITY, 0.5f)
                                        )
                                    }
                                    onAvatarClick()
                                }
                            )
                        else Modifier
                    ),
                shape              = RoundedCornerShape(50)
            )

            Column(
                modifier            = Modifier.weight(1f).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text     = title,
                        style    = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color    = if (isMissedCall) MaterialTheme.colorScheme.error
                                   else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (titleTrailing != null) {
                        titleTrailing()
                    }
                }
                if (supportingContent != null) {
                    supportingContent()
                } else if (subtitle != null) {
                    Text(
                        text     = subtitle,
                        style    = MaterialTheme.typography.bodyMedium,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (trailingContent != null) {
                Box(modifier = Modifier.padding(start = 8.dp)) {
                    trailingContent()
                }
            }
        }

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
                if (onSelectMode != null) {
                    DropdownMenuItem(
                        contentPadding = PaddingValues(start = 20.dp, end = 26.dp),
                        text = { Text(stringResource(R.string.select)) },
                        leadingIcon = { Icon(Icons.Default.CheckBox, null) },
                        onClick = { showMenu = false; onSelectMode() }
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }
                DropdownMenuItem(
                    contentPadding = PaddingValues(start = 20.dp, end = 26.dp),
                    text = { Text(stringResource(R.string.call)) },
                    leadingIcon = { Icon(Icons.Rounded.Call, null) },
                    onClick = { showMenu = false; onClick() }
                )
                if (!numberForMenu.isNullOrEmpty()) {
                    DropdownMenuItem(
                        contentPadding = PaddingValues(start = 20.dp, end = 26.dp),
                        text = { Text(stringResource(R.string.message)) },
                        leadingIcon = { Icon(ImageVector.vectorResource(id = R.drawable.ic_message_filled), null) },
                        onClick = {
                            showMenu = false
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                data = "sms:$numberForMenu".toUri()
                            }
                            context.startActivity(intent)
                        }
                    )
                    DropdownMenuItem(
                        contentPadding = PaddingValues(start = 20.dp, end = 26.dp),
                        text = { Text(stringResource(R.string.copy)) },
                        leadingIcon = { Icon(Icons.Default.ContentCopy, null) },
                        onClick = {
                            showMenu = false
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Phone number", numberForMenu))
                        }
                    )
                    if (showAddToContact) {
                        DropdownMenuItem(
                            contentPadding = PaddingValues(start = 20.dp, end = 26.dp),
                            text = { Text(stringResource(R.string.add_to_contact)) },
                            leadingIcon = { Icon(Icons.Default.PersonAdd, null) },
                            onClick = {
                                showMenu = false
                                val intent = Intent(Intent.ACTION_INSERT).apply {
                                    type = ContactsContract.RawContacts.CONTENT_TYPE
                                    putExtra(ContactsContract.Intents.Insert.PHONE, numberForMenu)
                                }
                                context.startActivity(intent)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TileGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        color    = MaterialTheme.colorScheme.surfaceContainerLow,
        shape    = RoundedCornerShape(28.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp), content = content)
    }
}
