package dev.goodwy.rphone.view.screen

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.telecom.Call
import android.telecom.TelecomManager
import android.telecom.CallAudioState
import android.telecom.VideoProfile
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.CallViewModel
import dev.goodwy.rphone.modal.`interface`.IContactsRepository
import dev.goodwy.rphone.cardCornerSmall
import dev.goodwy.rphone.controller.util.NoteManager
import dev.goodwy.rphone.modal.data.getDisplayName
import dev.goodwy.rphone.view.components.RillExpressiveCard
import dev.goodwy.rphone.view.components.RillSelectionDialog
import dev.goodwy.rphone.view.theme.MyColors.bottomBarColor
import dev.goodwy.rphone.view.theme.MyColors.cardColor
import dev.goodwy.rphone.view.theme.MyColors.dialpadKeyColor
import dev.goodwy.rphone.view.theme.color_call_button
import dev.goodwy.rphone.view.theme.color_call_end
import dev.goodwy.rphone.controller.util.formatDuration
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.view.screen.settings.PasswordSetupDialog
import dev.goodwy.rphone.view.screen.settings.PinSetupDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun ExpressiveCallScreen(
    call: Call,
    callState: Int,
    contactName: String,
    phoneNumber: String,
    photoUri: String?,
    audioState: CallAudioState?,
    initialConnectTime: Long = 0L,
    backgroundUri: String? = null,
    skipIncomingScreen: Boolean = false
) {
    val view = LocalView.current
    val context = LocalContext.current
    val preferenceManager = koinInject<PreferenceManager>()
    val contactsRepo = koinInject<IContactsRepository>()
    val callViewModel = koinInject<CallViewModel>()
    val telecomManager = remember { context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager }

    val allCalls by callViewModel.allCalls.collectAsStateWithLifecycle()
    val otherCall = remember(allCalls, call) {
        @Suppress("DEPRECATION")
        allCalls.find { it != call && it.state != Call.STATE_DISCONNECTED }
    }

    val accountHandle = call.details.accountHandle
    val simLabelFallback = accountHandle?.let { stringResource(R.string.call_screen_sim_label, it.id) }
    val simLabel = remember(accountHandle, simLabelFallback) {
        if (accountHandle != null) {
            val account = try {
                telecomManager.getPhoneAccount(accountHandle)
            } catch (_: Exception) {
                null
            }

            val label = account?.label?.toString()
            if (!label.isNullOrEmpty()) {
                label
            } else {
                simLabelFallback
            }
        } else {
            null
        }
    }
    val isMuted = audioState?.isMuted ?: false

    var callDuration by remember(initialConnectTime) {
        mutableLongStateOf(
            if (initialConnectTime > 0) (System.currentTimeMillis() - initialConnectTime) / 1000 else 0L
        )
    }
    var showKeypad by remember { mutableStateOf(false) }
    var showAudioPicker by remember { mutableStateOf(false) }
    var typedDigits by remember { mutableStateOf("") }
    var showMore by remember { mutableStateOf(false) }
    var isEnding by remember { mutableStateOf(false) }

    fun callDisconnect(isIncoming: Boolean = false) {
        if (isIncoming) isEnding = true
        try { call.disconnect() } catch (_: Exception) { }
    }

    val settingsState by preferenceManager.settingsChanged.collectAsStateWithLifecycle()
    val showCallScreenAvatar = remember(settingsState) {
        preferenceManager.getBoolean(PreferenceManager.KEY_SHOW_CALL_SCREEN_AVATAR, true)
    }

    val connectTime = remember(call) { call.details.connectTimeMillis }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    LaunchedEffect(callState, connectTime) {
        if (callState == Call.STATE_ACTIVE && connectTime > 0) {
            while (true) {
                callDuration = (System.currentTimeMillis() - connectTime) / 1000
                delay(1000)
            }
        } else if (callState == Call.STATE_ACTIVE && connectTime == 0L) {
            val startTime = System.currentTimeMillis()
            while (true) {
                callDuration = (System.currentTimeMillis() - startTime) / 1000
                delay(1000)
            }
        }
    }

    BackHandler(showKeypad) {
        showKeypad = false
    }

    if (showAudioPicker) {
        val supported = audioState?.supportedRouteMask ?: 0
        val handsetLabel = stringResource(R.string.audio_route_handset)
        val speakerLabel = stringResource(R.string.audio_route_speaker)
        val headsetLabel = stringResource(R.string.audio_route_headset)
        val bluetoothLabel = stringResource(R.string.audio_route_bluetooth)
        val options = remember(supported, handsetLabel, speakerLabel, headsetLabel, bluetoothLabel) {
            mutableListOf<Pair<String, Int>>().apply {
                if ((supported and CallAudioState.ROUTE_EARPIECE) != 0) add(handsetLabel to CallAudioState.ROUTE_EARPIECE)
                if ((supported and CallAudioState.ROUTE_SPEAKER) != 0) add(speakerLabel to CallAudioState.ROUTE_SPEAKER)
                if ((supported and CallAudioState.ROUTE_WIRED_HEADSET) != 0) add(headsetLabel to CallAudioState.ROUTE_WIRED_HEADSET)
                if ((supported and CallAudioState.ROUTE_BLUETOOTH) != 0) {
                    val deviceName = try {
                        audioState?.activeBluetoothDevice?.name
                    } catch (e: SecurityException) {
                        null
                    }
                    add((deviceName ?: bluetoothLabel) to CallAudioState.ROUTE_BLUETOOTH)
                }
            }
        }

        RillSelectionDialog<Pair<String, Int>>(
            onDismissRequest = { showAudioPicker = false },
            title = stringResource(R.string.audio_output_title),
            items = options,
            itemLabel = { option -> option.first },
            onItemSelected = { option ->
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                callViewModel.setAudioRoute(option.second)
            },
            isSelected = { option -> option.second == audioState?.route },
            icon = Icons.AutoMirrored.Rounded.VolumeUp,
            itemIcon = { option ->
                when (option.second) {
                    CallAudioState.ROUTE_SPEAKER -> Icons.AutoMirrored.Rounded.VolumeUp
                    CallAudioState.ROUTE_BLUETOOTH -> Icons.Rounded.Bluetooth
                    CallAudioState.ROUTE_WIRED_HEADSET -> Icons.Rounded.Headset
                    else -> Icons.AutoMirrored.Rounded.VolumeDown
                }
            }
        )
    }

    // Call notes --->
    var showNoteWindow by remember { mutableStateOf(false) }
    var noteText by remember { mutableStateOf("") }

    // ── Call-lock biometric ────────────────────────────────────────────────
    val callLockEnabled = remember {
        preferenceManager.shouldGateCallWithBiometric(phoneNumber)
    }
    var callBiometricUnlocked by remember { mutableStateOf(!callLockEnabled || skipIncomingScreen) }

    var showCallBiometricUnlock by remember { mutableStateOf(false) }
    var biometricGatesScreen by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    LaunchedEffect(callState) {
        if (callLockEnabled && !callBiometricUnlocked && !showCallBiometricUnlock) {
            if (callState == Call.STATE_RINGING) {
                biometricGatesScreen = true
                showCallBiometricUnlock = true
            }
        }
    }

    LaunchedEffect(phoneNumber) {
        if (phoneNumber.isNotEmpty() && noteText.isBlank()) {
            val existing = NoteManager.readNoteByPhone(context, phoneNumber)
            if (existing.isNotBlank()) noteText = existing
        }
    }

    LaunchedEffect(contactName) {
        if (phoneNumber.isNotEmpty() && noteText.isBlank()) {
            val existing = NoteManager.readNote(context, contactName, phoneNumber)
            if (existing.isNotBlank()) noteText = existing
        }
    }

    LaunchedEffect(noteText) {
        if (phoneNumber.isNotEmpty() && noteText.isNotBlank()) {
            NoteManager.writeNote(context, contactName, phoneNumber, noteText)
        }
    }

    LaunchedEffect(callState) {
        if ((callState == Call.STATE_DISCONNECTED || callState == Call.STATE_DISCONNECTING) && noteText.isNotBlank() && phoneNumber.isNotEmpty()) {
            NoteManager.writeNote(context, contactName, phoneNumber, noteText)
        }
    }
    // <--- Call notes

    Box(modifier = Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.surface)
    ) {
        ExpressiveBackground(photoUri, backgroundUri)

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Other Call Card
            AnimatedVisibility(
                visible = otherCall != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                otherCall?.let { oc ->
                    var ocName by remember(oc) { mutableStateOf(oc.details.handle?.schemeSpecificPart ?: "Unknown") }
                    val displayOrder = preferenceManager.getInt(PreferenceManager.KEY_CONTACT_DISPLAY_ORDER, 0)
                    LaunchedEffect(oc) {
                        val number = oc.details.handle?.schemeSpecificPart ?: ""
                        if (number.isNotEmpty()) {
                            val contact = try { contactsRepo.getContactByNumber(number) } catch (_: Exception) { null }
                            if (contact != null) ocName = getDisplayName(contact, displayOrder)
                        }
                    }

                    Surface(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            try {
                                callViewModel.setPreferredCall(oc)
                                if (call.state != Call.STATE_HOLDING) {
                                    call.hold()
                                }
                                oc.unhold()
                            } catch (_: Exception) {
                                try { oc.unhold() } catch (_: Exception) {}
                            }
                        },
                        modifier = Modifier
                            .statusBarsPadding()
                            .fillMaxWidth()
                            .padding(vertical = 16.dp, horizontal = 24.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = bottomBarColor,
                        tonalElevation = 4.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    Icons.Default.PauseCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text(
                                        text = ocName,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = stringResource(R.string.call_status_on_hold),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            IconButton(onClick = { oc.disconnect() }) {
                                Icon(Icons.Rounded.CallEnd, contentDescription = stringResource(R.string.end_call), tint = color_call_end)
                            }
                        }
                    }
                }
            }

            // --- HERO SECTION ---
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top,
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(horizontal = 24.dp)
                    .weight(if (showKeypad || showNoteWindow || showMore) 0.7f else 1f)
            ) {
                Spacer(modifier = Modifier.weight(0.4f))
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(tween(1000)) + expandVertically(tween(800))
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val statusText = when (callState) {
                            Call.STATE_DISCONNECTED -> stringResource(R.string.call_status_ended)
                            Call.STATE_HOLDING -> stringResource(R.string.call_status_on_hold)
                            Call.STATE_ACTIVE -> formatDuration(callDuration)
                            Call.STATE_DIALING -> stringResource(R.string.call_status_calling)
                            Call.STATE_RINGING -> stringResource(R.string.call_status_incoming)
                            else -> stringResource(R.string.call_status_connecting)
                        }

                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (callState == Call.STATE_HOLDING) MaterialTheme.colorScheme.tertiary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = contactName,
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (contactName != phoneNumber) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = phoneNumber,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (simLabel != null) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                Text(
                                    text = simLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                if (!showKeypad && !showNoteWindow && !showMore) {
                    AnimatedVisibility(
                        visible = showCallScreenAvatar && photoUri != null,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column {
                            Spacer(modifier = Modifier.height(24.dp))
                            if (callState == Call.STATE_RINGING) {
                                PulsingAvatar(photoUri)
                            } else {
                                HeroAvatar(photoUri)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.weight(0.6f))
            }

            // --- UI CONTROLS ---
            if (callState != Call.STATE_RINGING && !isEnding) {
                val isDark = isSystemInDarkTheme()
                val controlBtnColor = dialpadKeyColor
                val controlBtnActiveColor = if (isDark) Color.White else Color.Black
                val controlBtnActiveFg = if (isDark) Color.Black else Color.White
                val controlBtnFg = MaterialTheme.colorScheme.onSurface

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)),
                    color = bottomBarColor
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(
                                start = 20.dp,
                                end = 20.dp,
                                top = if (showKeypad || showNoteWindow || showMore) 20.dp else 22.dp,
                                bottom = 20.dp
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        AnimatedContent(
                            targetState = showKeypad || showNoteWindow || showMore,
                            transitionSpec = {
                                (fadeIn() + expandVertically(
                                    animationSpec = tween(300, easing = FastOutSlowInEasing)
                                )) togetherWith (fadeOut() + shrinkVertically(
                                    animationSpec = tween(300, easing = FastOutSlowInEasing)
                                ))
                            },
                            label = "moreContent"
                        ) { visible ->
                            if (visible) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            if (showKeypad) stringResource(R.string.keypad)
                                            else if (showNoteWindow) stringResource(R.string.add_note)
                                            else stringResource(R.string.more),
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        IconButton(onClick = {
                                            showKeypad = false
                                            showNoteWindow = false
                                            showMore = false
                                        }) { Icon(Icons.Rounded.Cancel, null) }
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))

                                    if (showMore) {
                                        RillExpressiveCard {
                                            MoreItem(
                                                headline = stringResource(R.string.add_note),
                                                leadingIcon = Icons.AutoMirrored.Outlined.StickyNote2,
                                                onClick = {
                                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                    showNoteWindow = true
                                                    showMore = false
                                                    showKeypad = false
                                                }
                                            )
                                            MoreItem(
                                                headline = stringResource(R.string.message),
                                                leadingIcon = ImageVector.vectorResource(id = R.drawable.ic_message_outline),
                                                onClick = {
                                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                                                        data = "smsto:$phoneNumber".toUri()
                                                    }
                                                    context.startActivity(intent)
                                                }
                                            )
                                            MoreItem(
                                                headline = stringResource(R.string.add_call),
                                                leadingIcon = Icons.Rounded.AddIcCall,
                                                enabled = otherCall == null && callState != Call.STATE_DIALING,
                                                onClick = {
                                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                    if (callState != Call.STATE_HOLDING) {
                                                        try {
                                                            call.hold()
                                                        } catch (_: Exception) {
                                                        }
                                                    }
                                                    val intent = Intent(Intent.ACTION_DIAL)
                                                    context.startActivity(intent)
                                                }
                                            )
                                            MoreItem(
                                                headline = if (otherCall != null) stringResource(R.string.swap)
                                                else if (callState == Call.STATE_HOLDING) stringResource(R.string.resume)
                                                else stringResource(R.string.hold),
                                                leadingIcon = if (otherCall != null) Icons.Rounded.SwapCalls
                                                else if (callState == Call.STATE_HOLDING) Icons.Rounded.PlayArrow
                                                else Icons.Default.Pause,
                                                enabled = callState != Call.STATE_DIALING,
                                                onClick = {
                                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                    if (callState == Call.STATE_HOLDING) call.unhold() else call.hold()
                                                }
                                            )
                                        }
                                    }

                                    if (showNoteWindow) {
                                        RillExpressiveCard {
                                            MoreItem(
                                                headline = contactName,
                                                leadingIcon = Icons.AutoMirrored.Outlined.StickyNote2,
                                                trailingIcon = Icons.Default.Check,
                                                enabled = callState != Call.STATE_DIALING,
                                                onClick = {
                                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                    if (phoneNumber.isNotEmpty()) NoteManager.writeNote(context, contactName, phoneNumber, noteText)
                                                    showNoteWindow = false
                                                    showKeypad = false
                                                    showMore = true
                                                }
                                            )
                                            Surface(
                                                color = cardColor,
                                                shape = RoundedCornerShape(cardCornerSmall),
                                                modifier = Modifier.fillMaxWidth(),
                                                shadowElevation = 0.dp
                                            ) {
                                                OutlinedTextField(
                                                    value = noteText,
                                                    onValueChange = { noteText = it },
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .heightIn(min = 184.dp, max = 184.dp),
                                                    placeholder = { Text(stringResource(R.string.type_your_note)) },
                                                    shape = RoundedCornerShape(12.dp),
                                                    minLines = 3,
                                                    colors = OutlinedTextFieldDefaults.colors(
                                                        focusedBorderColor = Color.Transparent,
                                                        unfocusedBorderColor = Color.Transparent)
                                                )
                                            }
                                        }
                                    }

                                    if (showKeypad) {
                                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                            InCallKeypad(
                                                call = call,
                                                typedDigits = typedDigits,
                                                onDigitClick = { digit -> typedDigits += digit }
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(24.dp))
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AnimatedCallButton(
                                modifier = Modifier.weight(1f),
                                icon = Icons.Rounded.Dialpad,
                                isActive = showKeypad,
                                label = stringResource(R.string.keypad),
                                btnColor = controlBtnColor,
                                activeBtnColor = controlBtnActiveColor,
                                fgColor = controlBtnFg,
                                activeFgColor = controlBtnActiveFg
                            ) {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                showKeypad = !showKeypad
                                showNoteWindow = false
                                showMore = false
                            }

                            AnimatedCallButton(
                                modifier = Modifier.weight(1f),
                                icon = if (isMuted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                                isActive = isMuted,
                                label = stringResource(R.string.mute),
                                btnColor = controlBtnColor,
                                activeBtnColor = controlBtnActiveColor,
                                fgColor = controlBtnFg,
                                activeFgColor = controlBtnActiveFg
                            ) {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                callViewModel.toggleMute()
                            }

                            val audioRoute = audioState?.route ?: CallAudioState.ROUTE_EARPIECE
                            val audioIcon = when (audioRoute) {
                                CallAudioState.ROUTE_SPEAKER -> Icons.AutoMirrored.Rounded.VolumeUp
                                CallAudioState.ROUTE_BLUETOOTH -> Icons.Rounded.Bluetooth
                                CallAudioState.ROUTE_WIRED_HEADSET -> Icons.Rounded.Headset
                                else -> Icons.AutoMirrored.Rounded.VolumeDown
                            }

                            val bluetoothLabel = stringResource(R.string.audio_route_bluetooth)
                            val audioLabel = when (audioRoute) {
                                CallAudioState.ROUTE_SPEAKER -> stringResource(R.string.audio_route_speaker)
                                CallAudioState.ROUTE_BLUETOOTH -> {
                                    try {
                                        audioState?.activeBluetoothDevice?.name ?: bluetoothLabel
                                    } catch (e: SecurityException) {
                                        bluetoothLabel
                                    }
                                }
                                CallAudioState.ROUTE_WIRED_HEADSET -> stringResource(R.string.audio_route_headset)
                                else -> stringResource(R.string.audio_route_handset)
                            }
                            AnimatedCallButton(
                                modifier = Modifier.weight(1f),
                                icon = audioIcon,
                                isActive = audioRoute == CallAudioState.ROUTE_SPEAKER || audioRoute == CallAudioState.ROUTE_BLUETOOTH,
                                label = audioLabel,
                                btnColor = controlBtnColor,
                                activeBtnColor = controlBtnActiveColor,
                                fgColor = controlBtnFg,
                                activeFgColor = controlBtnActiveFg
                            ) {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                callViewModel.cycleAudioRoute()
                            }

                            AnimatedCallButton(
                                modifier = Modifier.weight(1f),
                                icon = Icons.Default.MoreVert,
                                isActive = showMore,
                                label = stringResource(R.string.more),
                                btnColor = controlBtnColor,
                                activeBtnColor = controlBtnActiveColor,
                                fgColor = controlBtnFg,
                                activeFgColor = controlBtnActiveFg
                            ) {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                showMore = !showMore
                                showNoteWindow = false
                                showKeypad = false
                            }
                        }

                        val hangupWidthFraction =
                            preferenceManager.getFloat(PreferenceManager.KEY_HANGUP_WIDTH, 0.5f)
                        val endInteraction = remember { MutableInteractionSource() }
                        val endPressed by endInteraction.collectIsPressedAsState()
                        val endRadius by animateDpAsState(
                            if (endPressed) 20.dp else 42.dp,
                            spring(stiffness = Spring.StiffnessMedium),
                            label = "endRadius"
                        )

                        Spacer(modifier = Modifier.height(24.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            val isCircleHangup = hangupWidthFraction <= 0.1f
                            Surface(
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.REJECT)
                                    if (noteText.isNotBlank() && phoneNumber.isNotEmpty()) {
                                        NoteManager.writeNote(context, contactName, phoneNumber, noteText)
                                    }
                                    callDisconnect()
                                },
                                modifier = if (isCircleHangup) Modifier.size(76.dp)
                                else Modifier.fillMaxWidth(hangupWidthFraction.coerceIn(0.1f, 1.0f)).height(68.dp),
                                shape = if (isCircleHangup) CircleShape else RoundedCornerShape(endRadius),
                                color = color_call_end,
                                interactionSource = endInteraction
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        val showText = hangupWidthFraction > 0.5f
                                        Icon(
                                            Icons.Rounded.CallEnd,
                                            stringResource(R.string.end_call),
                                            tint = Color.White,
                                            modifier = Modifier.size(if (showText) 26.dp else 32.dp)
                                        )
                                        if (showText) {
                                            Text(
                                                stringResource(R.string.end_call),
                                                color = Color.White,
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                val useCustomUI = preferenceManager.getInt(PreferenceManager.KEY_INCOMING_CALL_UI_MODE, 10)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(start = 24.dp, end = 24.dp, bottom = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    if ((useCustomUI != 2 && useCustomUI != 3 && useCustomUI != 10) || otherCall != null) {
                        Surface(
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                callDisconnect(true)
                                val intent = Intent(Intent.ACTION_SENDTO).apply {
                                    data = "smsto:$phoneNumber".toUri()
                                }
                                context.startActivity(intent)
                            },
                            shape = CircleShape,
                            color = Color.Transparent,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                            modifier = Modifier.height(45.dp).wrapContentWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                                Spacer(modifier = Modifier.width(18.dp))
                                Icon(painter = painterResource(id = R.drawable.ic_message_outline), stringResource(R.string.message), tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(22.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(stringResource(R.string.message), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
                                Spacer(modifier = Modifier.width(18.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(24.dp))
                    }

                    when {
                        useCustomUI == 1 || otherCall != null -> IncomingCallButtons(
                            onAnswer = { try { call.answer(VideoProfile.STATE_AUDIO_ONLY) } catch (_: Exception) {} },
                            onDecline = { callDisconnect(true) },
                            onAnswerAndDecline = if (otherCall != null) {
                                {
                                    try {
                                        otherCall.disconnect()
                                        call.answer(VideoProfile.STATE_AUDIO_ONLY)
                                    } catch (_: Exception) {}
                                }
                            } else null
                        )
                        useCustomUI == 2 -> IPhoneSwipeToAnswer(
                            onAnswer = { try { call.answer(VideoProfile.STATE_AUDIO_ONLY) } catch (_: Exception) {} },
                            onDecline = { callDisconnect(true) },
                            onMessage = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                callDisconnect(true)
                                val intent = Intent(Intent.ACTION_SENDTO).apply {
                                    data = "smsto:$phoneNumber".toUri()
                                }
                                context.startActivity(intent)
                            }
                        )
                        useCustomUI == 3 -> VerticalSwipeToAnswer(
                            onAnswer = {
                                if (callBiometricUnlocked) {
                                    try { call.answer(VideoProfile.STATE_AUDIO_ONLY) } catch (_: Exception) {}
                                } else {
                                    pendingAction = { try { call.answer(VideoProfile.STATE_AUDIO_ONLY) } catch (_: Exception) {} }
                                    showCallBiometricUnlock = true
                                }
                            },
                            onDecline = {
                                if (callBiometricUnlocked) {
                                    callDisconnect(true)
                                } else {
                                    pendingAction = { callDisconnect(true) }
                                    showCallBiometricUnlock = true
                                }
                            }
                        )
                        useCustomUI == 0 -> HorizontalSwipeToAnswer(
                            onAnswer = {
                                if (callBiometricUnlocked) {
                                    try { call.answer(VideoProfile.STATE_AUDIO_ONLY) } catch (_: Exception) {}
                                } else {
                                    pendingAction = { try { call.answer(VideoProfile.STATE_AUDIO_ONLY) } catch (_: Exception) {} }
                                    showCallBiometricUnlock = true
                                }
                            },
                            onDecline = {
                                if (callBiometricUnlocked) {
                                    callDisconnect(true)
                                } else {
                                    pendingAction = { callDisconnect(true) }
                                    showCallBiometricUnlock = true
                                }
                            }
                        )
                        else -> DefaultSwipeToAnswer(
                            onAnswer = {
                                if (callBiometricUnlocked) {
                                    try { call.answer(VideoProfile.STATE_AUDIO_ONLY) } catch (_: Exception) {}
                                } else {
                                    pendingAction = { try { call.answer(VideoProfile.STATE_AUDIO_ONLY) } catch (_: Exception) {} }
                                    showCallBiometricUnlock = true
                                }
                            },
                            onDecline = {
                                if (callBiometricUnlocked) {
                                    callDisconnect(true)
                                } else {
                                    pendingAction = { callDisconnect(true) }
                                    showCallBiometricUnlock = true
                                }
                            },
                            onMessage = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                callDisconnect(true)
                                val intent = Intent(Intent.ACTION_SENDTO).apply {
                                    data = "smsto:$phoneNumber".toUri()
                                }
                                context.startActivity(intent)
                            }
                        )
                    }
                }
            }
        }
    }

    if (showCallBiometricUnlock) {
        val biometricType = preferenceManager.getString(PreferenceManager.KEY_BIOMETRICS_TYPE, "") ?: ""
        val callActivity = LocalContext.current as? FragmentActivity
        fun onBiometricFail() {
            showCallBiometricUnlock = false
            pendingAction = null
        }
        when (biometricType) {
            "system" -> {
                LaunchedEffect(showCallBiometricUnlock) {
                    val activity = callActivity ?: run { onBiometricFail(); return@LaunchedEffect }
                    val executor = androidx.core.content.ContextCompat.getMainExecutor(activity)
                    val prompt = androidx.biometric.BiometricPrompt(
                        activity, executor,
                        object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
                            override fun onAuthenticationSucceeded(result: androidx.biometric.BiometricPrompt.AuthenticationResult) {
                                callBiometricUnlocked = true
                                biometricGatesScreen = false
                                showCallBiometricUnlock = false
                                pendingAction?.invoke(); pendingAction = null
                            }
                            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { onBiometricFail() }
                            override fun onAuthenticationFailed() { }
                        }
                    )
                    prompt.authenticate(
                        androidx.biometric.BiometricPrompt.PromptInfo.Builder()
                            .setTitle(activity.getString(R.string.enter_pin))
                            .setSubtitle("Verify your identity to access this call")
                            .setNegativeButtonText(activity.getString(R.string.cancel))
                            .setAllowedAuthenticators(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK)
                            .build()
                    )
                }
            }
            "pin" -> {
                PinSetupDialog(
                    title = stringResource(R.string.enter_pin), isVerify = true,
                    expectedPin = preferenceManager.getString(PreferenceManager.KEY_BIOMETRICS_PIN, "") ?: "",
                    showCloseButton = !biometricGatesScreen,
                    onConfirm = {
                        callBiometricUnlocked = true; biometricGatesScreen = false
                        showCallBiometricUnlock = false
                        pendingAction?.invoke(); pendingAction = null
                    },
                    onDismiss = { onBiometricFail() }
                )
            }
            "password" -> {
                PasswordSetupDialog(
                    title = stringResource(R.string.enter_password), isVerify = true,
                    expectedPassword = preferenceManager.getString(PreferenceManager.KEY_BIOMETRICS_PASSWORD, "") ?: "",
                    showCloseButton = !biometricGatesScreen,
                    onConfirm = {
                        callBiometricUnlocked = true; biometricGatesScreen = false
                        showCallBiometricUnlock = false
                        pendingAction?.invoke(); pendingAction = null
                    },
                    onDismiss = { onBiometricFail() }
                )
            }
        }
    }
}