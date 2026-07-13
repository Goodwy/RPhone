package dev.goodwy.rphone.view.screen

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.telecom.Call
import android.telecom.TelecomManager
import android.telecom.CallAudioState
import android.telecom.VideoProfile
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.automirrored.rounded.ArrowRight
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.rounded.AddIcCall
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.Headset
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SwapCalls
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.fragment.app.FragmentActivity
import coil.compose.AsyncImage
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.CallService
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.modal.`interface`.IContactsRepository
import dev.goodwy.rphone.cardCornerSmall
import dev.goodwy.rphone.controller.util.NoteManager
import dev.goodwy.rphone.modal.data.getDisplayName
import dev.goodwy.rphone.view.components.RillExpressiveCard
import dev.goodwy.rphone.view.components.RillIconBox
import dev.goodwy.rphone.view.screen.onboarding.wavyCircleShape
import dev.goodwy.rphone.view.screen.settings.PasswordSetupDialog
import dev.goodwy.rphone.view.screen.settings.PinSetupDialog
import dev.goodwy.rphone.view.theme.MyColors.bottomBarColor
import dev.goodwy.rphone.view.theme.MyColors.cardColor
import dev.goodwy.rphone.view.theme.MyColors.dialpadKeyColor
import dev.goodwy.rphone.view.theme.color_call_button
import dev.goodwy.rphone.view.theme.color_call_end
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import java.util.*
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds

@Composable
fun ExpressiveCallScreen(
    call: Call,
    callState: Int,
    contactName: String,
    phoneNumber: String,
    photoUri: String?,
    audioState: CallAudioState?,
    skipIncomingScreen: Boolean = false
) {
    val view = LocalView.current
    val context = LocalContext.current
    val preferenceManager = koinInject<PreferenceManager>()
    val contactsRepo = koinInject<IContactsRepository>()
    val telecomManager = remember { context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager }
    
    val allCalls by CallService.allCalls.collectAsState()
    val otherCall = remember(allCalls, call) {
        @Suppress("DEPRECATION")
        allCalls.find { it != call && it.state != Call.STATE_DISCONNECTED }
    }

    val simLabel = remember(call.details.accountHandle) {
        val handle = call.details.accountHandle
        if (handle != null) {
            val account = try {
                telecomManager.getPhoneAccount(handle)
            } catch (_: Exception) {
                null
            }

            val label = account?.label?.toString()
            if (!label.isNullOrEmpty()) {
                label
            } else {
                "SIM ${handle.id}"
            }
        } else {
            null
        }
    }
    val isMuted = audioState?.isMuted ?: false

    var callDuration by remember { mutableLongStateOf(0L) }
    var showKeypad by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }
    var typedDigits by remember { mutableStateOf("") }

    val settingsState by preferenceManager.settingsChanged.collectAsState()
    val showCallScreenAvatar = remember(settingsState) {
        preferenceManager.getBoolean(PreferenceManager.KEY_SHOW_CALL_SCREEN_AVATAR, true)
    }

    val connectTime = remember(call) { call.details.connectTimeMillis }
//    LaunchedEffect(callState, call.details.connectTimeMillis) {
//        if (callState == Call.STATE_ACTIVE) {
//            val connectTime = if (call.details.connectTimeMillis > 0) call.details.connectTimeMillis else System.currentTimeMillis()
//            while (true) {
//                callDuration = (System.currentTimeMillis() - connectTime) / 1000
//                delay(1.seconds)
//            }
//        }
//    }
    LaunchedEffect(callState, connectTime) {
        if (callState == Call.STATE_ACTIVE && connectTime > 0) {
            while (true) {
                callDuration = (System.currentTimeMillis() - connectTime) / 1000
                delay(1000)
            }
        } else if (callState == Call.STATE_ACTIVE && connectTime == 0L) {
            // If connectTime == 0, use the current time
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

    // Gate the incoming call screen behind biometric when call arrives ringing
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
        if (!photoUri.isNullOrEmpty()) ExpressiveBackground(photoUri)
        FloatingParticles()

        Column(
            modifier = Modifier
                .fillMaxSize(),
//                .statusBarsPadding()
//                .navigationBarsPadding()
//                .padding(horizontal = 24.dp, vertical = 20.dp),
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
                            if (contact != null) ocName = getDisplayName(contact, displayOrder) //contact.displayName
                        }
                    }
                    
                    Surface(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            // Swap calls reliably
                            try {
                                CallService.setPreferredCall(oc)
                                if (call.state != Call.STATE_HOLDING) {
                                    call.hold()
                                }
                                oc.unhold()
                            } catch (_: Exception) {
                                // Fallback: just try to unhold the other one
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
                                        text = stringResource(R.string.on_hold),
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
                            Call.STATE_DISCONNECTED -> stringResource(R.string.call_ended)
                            Call.STATE_HOLDING -> stringResource(R.string.on_hold)
                            Call.STATE_ACTIVE -> formatDuration(callDuration)
                            Call.STATE_DIALING -> stringResource(R.string.calling)
                            Call.STATE_RINGING -> stringResource(R.string.incoming_call)
                            else -> stringResource(R.string.connecting)
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
            if (callState != Call.STATE_RINGING) {
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
                        // More
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
                                    // Header
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

                                    // Call notes
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

                                    // --- KEYPAD ---
                                    if (showKeypad) {
                                        InCallKeypad(
                                            call = call,
                                            typedDigits = typedDigits,
                                            onDigitClick = { digit -> typedDigits += digit }
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(24.dp))
                                }
                            }
                        }

                        // Row 1 (3 centered)
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
                                CallService.mute(!isMuted)
                            }

                            val audioRoute = audioState?.route ?: CallAudioState.ROUTE_EARPIECE
                            val audioIcon = when (audioRoute) {
                                CallAudioState.ROUTE_SPEAKER -> Icons.AutoMirrored.Rounded.VolumeUp
                                CallAudioState.ROUTE_BLUETOOTH -> Icons.Rounded.Bluetooth
                                CallAudioState.ROUTE_WIRED_HEADSET -> Icons.Rounded.Headset
                                else -> Icons.AutoMirrored.Rounded.VolumeDown //Icons.Rounded.Phone
                            }

                            val audioLabel = when (audioRoute) {
                                CallAudioState.ROUTE_SPEAKER -> stringResource(R.string.speaker)
                                CallAudioState.ROUTE_BLUETOOTH -> {
                                    try {
                                        audioState?.activeBluetoothDevice?.name ?: "Bluetooth"
                                    } catch (e: SecurityException) {
                                        "Bluetooth"
                                    }
                                }
                                CallAudioState.ROUTE_WIRED_HEADSET -> "Earpiece"
                                else -> "Handset"
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
                                CallService.cycleAudioRoute()
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

                        // Row 2 (3 centered)
//                    Row(
//                        modifier = Modifier.fillMaxWidth(),
//                        horizontalArrangement = Arrangement.SpaceEvenly,
//                        verticalAlignment = Alignment.CenterVertically
//                    ) {
//                        AnimatedCallButton(
//                            icon = Icons.Rounded.Add,
//                            isActive = false,
//                            label = stringResource(R.string.add_call),
//                            btnColor = controlBtnColor,
//                            activeBtnColor = controlBtnActiveColor,
//                            fgColor = controlBtnFg,
//                            activeFgColor = controlBtnActiveFg
//                        ) {
//                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
//                            if (callState != Call.STATE_HOLDING) {
//                                try { call.hold() } catch (e: Exception) {}
//                            }
//                            val intent = Intent(Intent.ACTION_DIAL)
//                            context.startActivity(intent)
//                        }
//
//                        AnimatedCallButton(
//                            icon = if (otherCall != null) Icons.Rounded.SwapCalls
//                                    else if (callState == Call.STATE_HOLDING) Icons.Rounded.PlayArrow
//                                    else Icons.Default.Pause,
//                            isActive = callState == Call.STATE_HOLDING,
//                            label = if (otherCall != null) stringResource(R.string.swap)
//                                    else if (callState == Call.STATE_HOLDING) stringResource(R.string.resume)
//                                    else stringResource(R.string.hold),
//                            btnColor = controlBtnColor,
//                            activeBtnColor = controlBtnActiveColor,
//                            fgColor = controlBtnFg,
//                            activeFgColor = controlBtnActiveFg
//                        ) {
//                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
//                            if (callState == Call.STATE_HOLDING) call.unhold() else call.hold()
//                        }
//
//                        AnimatedCallButton(
//                            icon = ImageVector.vectorResource(id = R.drawable.ic_message_outline),
//                            isActive = false,
//                            label = stringResource(R.string.message),
//                            btnColor = controlBtnColor,
//                            activeBtnColor = controlBtnActiveColor,
//                            fgColor = controlBtnFg,
//                            activeFgColor = controlBtnActiveFg
//                        ) {
//                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
//                            val intent = Intent(Intent.ACTION_SENDTO).apply {
//                                data = "smsto:$phoneNumber".toUri()
//                            }
//                            context.startActivity(intent)
//                        }
//                    }

                        // End Call Button
                        // ── Hangup Button with configurable width ──────────────
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
                                        NoteManager.writeNote(
                                            context,
                                            contactName,
                                            phoneNumber,
                                            noteText
                                        )
                                    }
                                    try {
                                        call.disconnect()
                                    } catch (_: Exception) {
                                    }
                                },
                                modifier = if (isCircleHangup) Modifier
                                    .size(76.dp)
//                                            .scale(if (endPressed) 1.04f else 1f)
                                else Modifier
                                    .fillMaxWidth(hangupWidthFraction.coerceIn(0.1f, 1.0f))
                                    .height(68.dp),
//                                            .scale(if (endPressed) 1.04f else 1f),
                                shape = if (isCircleHangup) CircleShape else RoundedCornerShape(
                                    endRadius
                                ),
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
                // Incoming call
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
//                    "Horizontal Swipe" to 0,
//                    "Buttons" to 1,
//                    "Slide to Answer (iOS)" to 2,
//                    "Vertical Swipe" to 3
                    if ((useCustomUI != 2 && useCustomUI != 3 && useCustomUI != 10) || otherCall != null) {
                        Surface(
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                try { call.disconnect() } catch (_: Exception) {}
                                val intent = Intent(Intent.ACTION_SENDTO).apply {
                                    data = "smsto:$phoneNumber".toUri()
                                }
                                context.startActivity(intent)
                            },
                            shape = CircleShape,
                            color = Color.Transparent,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                            modifier = Modifier
                                .height(45.dp)
                                .wrapContentWidth() //width(140.dp)
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
                            onDecline = { try { call.disconnect() } catch (_: Exception) {} },
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
                            onDecline = { try { call.disconnect() } catch (_: Exception) {} },
                            onMessage = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                try { call.disconnect() } catch (_: Exception) {}
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
                                    try { call.disconnect() } catch (_: Exception) {}
                                } else {
                                    pendingAction = { try { call.disconnect() } catch (_: Exception) {} }
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
                                    try { call.disconnect() } catch (_: Exception) {}
                                } else {
                                    pendingAction = { try { call.disconnect() } catch (_: Exception) {} }
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
                                    try { call.disconnect() } catch (_: Exception) {}
                                } else {
                                    pendingAction = { try { call.disconnect() } catch (_: Exception) {} }
                                    showCallBiometricUnlock = true
                                }
                            },
                            onMessage = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                try { call.disconnect() } catch (_: Exception) {}
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

    // ── Call biometric — direct prompt, no overlay ────────────────────
    if (showCallBiometricUnlock) {
        val biometricType = preferenceManager.getString(PreferenceManager.KEY_BIOMETRICS_TYPE, "") ?: ""
        val callActivity   = LocalContext.current as? FragmentActivity
        fun onBiometricFail() {
            showCallBiometricUnlock = false
            pendingAction = null
            // Don't disconnect — let the call keep ringing so the user can retry
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
                            override fun onAuthenticationFailed() { /* keep prompt open */ }
                        }
                    )
                    prompt.authenticate(
                        androidx.biometric.BiometricPrompt.PromptInfo.Builder()
                            .setTitle("Ever Dialer")
                            .setSubtitle("Verify your identity to access this call")
                            .setNegativeButtonText("Cancel")
                            .setAllowedAuthenticators(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK)
                            .build()
                    )
                }
            }
            "pin" -> {
                PinSetupDialog(
                    title = "Enter PIN", isVerify = true,
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
                    title = "Enter Password", isVerify = true,
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

@Composable
fun InCallKeypad(
    call: Call,
    typedDigits: String,
    onDigitClick: (Char) -> Unit
) {
    val prefs = koinInject<PreferenceManager>()
    val settingsState by prefs.settingsChanged.collectAsState()
    val toneGenerator = remember { ToneGenerator(AudioManager.STREAM_DTMF, 80) }
    val dialpadStyle by remember(settingsState) {
        mutableIntStateOf(prefs.getInt(PreferenceManager.KEY_DIALPAD_STYLE, 3))
    }

    DisposableEffect(Unit) {
        onDispose {
            toneGenerator.release()
        }
    }

    Column(
//        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val typedDigitsLength = typedDigits.length
        val fontSize = (
                (when {
                    typedDigitsLength > 42 -> 12
                    typedDigitsLength > 38 -> 14
                    typedDigitsLength > 34 -> 16
                    typedDigitsLength > 30 -> 18
                    typedDigitsLength > 25 -> 20
                    typedDigitsLength > 20 -> 24
                    typedDigitsLength > 16 -> 28
                    else -> 36
                }))
        val textStyle = MaterialTheme.typography.displaySmall.copy(
            fontSize = fontSize.sp,
            fontWeight = FontWeight.Light
        )
        Text(
            text = typedDigits,
            style = textStyle, //MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .animateContentSize()
        )

        val keys = listOf(
            listOf('1', '2', '3'),
            listOf('4', '5', '6'),
            listOf('7', '8', '9'),
            listOf('*', '0', '#')
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            keys.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    row.forEach { key ->
                        KeypadButton(
                            modifier = Modifier.weight(1f),
                            key = key,
                            style = dialpadStyle,
                            onClick = {
                                if (prefs.getBoolean(PreferenceManager.KEY_DTMF_TONE, true)) {
                                    val toneType = when (key) {
                                        '1' -> ToneGenerator.TONE_DTMF_1
                                        '2' -> ToneGenerator.TONE_DTMF_2
                                        '3' -> ToneGenerator.TONE_DTMF_3
                                        '4' -> ToneGenerator.TONE_DTMF_4
                                        '5' -> ToneGenerator.TONE_DTMF_5
                                        '6' -> ToneGenerator.TONE_DTMF_6
                                        '7' -> ToneGenerator.TONE_DTMF_7
                                        '8' -> ToneGenerator.TONE_DTMF_8
                                        '9' -> ToneGenerator.TONE_DTMF_9
                                        '0' -> ToneGenerator.TONE_DTMF_0
                                        '*' -> ToneGenerator.TONE_DTMF_S
                                        '#' -> ToneGenerator.TONE_DTMF_P
                                        else -> -1
                                    }
                                    if (toneType != -1) {
                                        toneGenerator.startTone(toneType, 120)
                                    }
                                }
                                call.playDtmfTone(key)
                                call.stopDtmfTone()
                                onDigitClick(key)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun KeypadButton(
    modifier: Modifier,
    key: Char,
    style: Int = 0,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val cornerRadius by animateDpAsState(
        targetValue = when (style) {
            1 -> 50.dp // Circular
            2 -> 0.dp  // Minimal
            else -> if (isPressed) 16.dp else 32.dp // Modern
        },
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "ButtonShape"
    )

    val containerColor = when (style) {
        2 -> if (isPressed) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else dialpadKeyColor
        else -> dialpadKeyColor
    }

    Surface(
        onClick = onClick,
        modifier = modifier.height(64.dp),
        shape = if (style == 1) CircleShape else RoundedCornerShape(cornerRadius),
        color = containerColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = if (style == 2 && !isPressed) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)) else null,
        interactionSource = interactionSource
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = key.toString(),
                style = if (style == 1) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun ExpressiveBackground(photoUri: String?) {
    val infiniteTransition = rememberInfiniteTransition(label = "bg")
    val driftX by infiniteTransition.animateFloat(
        initialValue = -30f, targetValue = 30f,
        animationSpec = infiniteRepeatable(tween(20000, easing = LinearEasing), RepeatMode.Reverse), label = "x"
    )
    val driftY by infiniteTransition.animateFloat(
        initialValue = -20f, targetValue = 20f,
        animationSpec = infiniteRepeatable(tween(25000, easing = LinearEasing), RepeatMode.Reverse), label = "y"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        if (!photoUri.isNullOrEmpty()) {
            AsyncImage(
                model = photoUri,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = driftX
                        translationY = driftY
                        scaleX = 1.4f
                        scaleY = 1.4f
                    }
                    .blur(80.dp)
                    .alpha(0.35f),
                contentScale = ContentScale.Crop
            )
        } else {
            val color1 = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            val color2 = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
            val color3 = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.2f)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(listOf(color1, color2, color3)))
                    .blur(40.dp)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.2f),
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                        )
                    )
                )
        )
    }
}

@Composable
fun PulsingAvatar(photoUri: String?) {
    val prefs = koinInject<PreferenceManager>()
    val settingsState by prefs.settingsChanged.collectAsState()
    val avatarShape = remember(settingsState) {
        val shapeVal = prefs.getInt(PreferenceManager.KEY_AVATAR_SHAPE, 1)
        when (shapeVal) {
            0 -> RoundedCornerShape(20.dp)
            1 -> wavyCircleShape(waveAmplitude = 0.024f)
            2 -> RoundedCornerShape(0.dp)
            else -> CircleShape
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "alpha"
    )

    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(230.dp)
                .scale(scale)
                .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = alpha), avatarShape)
        )
        Box(
            modifier = Modifier
                .size(260.dp)
                .scale(scale * 1.1f)
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = alpha * 0.5f),
                    avatarShape
                )
        )

        HeroAvatar(photoUri, avatarSize = 200.dp, wavy = true)
    }
}

@Composable
fun FloatingParticles() {
    val infiniteTransition = rememberInfiniteTransition(label = "particles")

    Box(modifier = Modifier.fillMaxSize()) {
        repeat(10) { index ->
            val startX = (index * 100f) % 1000f
            val startY = (index * 150f) % 1500f

            val animX by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 100f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 10000 + index * 1000, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "x_$index"
            )

            val animY by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = -150f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 12000 + index * 1200, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "y_$index"
            )

            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.1f,
                targetValue = 0.4f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 5000 + index * 500, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "alpha_$index"
            )

            Box(
                modifier = Modifier
                    .offset(x = (startX + animX).dp, y = (startY + animY).dp)
                    .size((10 + index % 20).dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha))
                    .blur(2.dp)
            )
        }
    }
}

@Composable
fun HeroAvatar(photoUri: String?, avatarSize: Dp = 160.dp, wavy: Boolean = false) {
    val prefs = koinInject<PreferenceManager>()
    val settingsState by prefs.settingsChanged.collectAsState()
    val avatarShape = remember(settingsState) {
        if (wavy) wavyCircleShape(waveAmplitude = 0.024f)
        else {
            val shapeVal = prefs.getInt(PreferenceManager.KEY_AVATAR_SHAPE, 1)
            when (shapeVal) {
                0 -> RoundedCornerShape(20.dp)
                1 -> CircleShape
                2 -> RoundedCornerShape(0.dp)
                else -> CircleShape
            }
        }
    }
    val avatarFrame = prefs.getBoolean(PreferenceManager.KEY_AVATAR_FRAME, false)
    val borderColor =  MaterialTheme.colorScheme.onSurface

    Box(
        modifier = Modifier
            .then(
                if (avatarFrame) Modifier
                    .drawBehind {
                        val borderWidth = size.width * 0.08f // 8% of the width
                        drawOutline(
                            outline = avatarShape.createOutline(size, layoutDirection, this),
                            color = borderColor,
                            style = Stroke(width = borderWidth)
                        )
                    }
                else Modifier
            )
            .size(avatarSize)
            .clip(avatarShape)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center
    ) {
        if (!photoUri.isNullOrEmpty()) {
            AsyncImage(
                model = photoUri,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(avatarShape),
                contentScale = ContentScale.Crop
            )
        } /*else {
            Icon(
                Icons.Rounded.Person,
                contentDescription = null,
                modifier = Modifier.size(120.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }*/
    }
}

// ─── Animated Call Button ───────────────────────────────────────────────────────
@Composable
fun AnimatedCallButton(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    isActive: Boolean = false,
    btnColor: Color = Color.White.copy(0.12f),
    activeBtnColor: Color = Color.White,
    fgColor: Color = Color.White,
    activeFgColor: Color = Color.Black,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()
    val radius by animateDpAsState(if (isActive || isPressed) 20.dp else 42.dp, spring(stiffness = Spring.StiffnessMedium), label = "btnRadius")
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(onClick = onClick,
            modifier = Modifier.height(68.dp).fillMaxWidth(),
            shape = RoundedCornerShape(radius),
            color = if (isActive) activeBtnColor else btnColor,
            interactionSource = interaction
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (isActive) activeFgColor else fgColor,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = MaterialTheme.typography.labelMedium.fontFamily,
            color = fgColor.copy(0.7f),
            modifier = Modifier.padding(top = 8.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

//@Composable
//fun CallActionButton(
//    icon: ImageVector,
//    isActive: Boolean,
//    label: String,
//    enabled: Boolean = true,
//    onClick: () -> Unit
//) {
//    val scale by animateFloatAsState(if (isActive) 1.1f else 1f, label = "scale")
//
//    Column(
//        horizontalAlignment = Alignment.CenterHorizontally,
//        modifier = Modifier
//            .width(90.dp)
//            .alpha(if (enabled) 1f else 0.5f)
//    ) {
//        val containerColor by animateColorAsState(
//            if (isActive) MaterialTheme.colorScheme.secondaryContainer
//            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
//            label = "color"
//        )
//        val contentColor = if (isActive) MaterialTheme.colorScheme.onSecondaryContainer
//                          else MaterialTheme.colorScheme.onSurfaceVariant
//
//        IconButton(
//            onClick = onClick,
//            enabled = enabled,
//            modifier = Modifier
//                .size(64.dp)
//                .scale(scale)
//                .background(containerColor, CircleShape)
//        ) {
//            Icon(
//                icon,
//                contentDescription = label,
//                tint = contentColor,
//                modifier = Modifier.size(28.dp)
//            )
//        }
//        Spacer(modifier = Modifier.height(8.dp))
//        Text(
//            text = label,
//            style = MaterialTheme.typography.labelLarge,
//            color = MaterialTheme.colorScheme.onSurface,
//            fontWeight = FontWeight.Medium
//        )
//    }
//}

fun formatDuration(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", m, s)
}

@Composable
fun HorizontalSwipeToAnswer(onAnswer: () -> Unit, onDecline: () -> Unit) {
    val coroutineScope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val density = LocalDensity.current
    val view = LocalView.current

    val trackHeight = 96.dp // Increased from 88.dp
    val handleWidth = 110.dp
    val handleHeight = 72.dp // Increased from 64.dp
    val handleWidthPx = with(density) { handleWidth.toPx() }
    val paddingHandle = with(density) { (trackHeight - handleHeight).toPx() }
    var trackWidthPx by remember { mutableFloatStateOf(0f) }
    
    val maxDrag by remember(trackWidthPx, handleWidthPx, paddingHandle) {
        derivedStateOf {
            if (trackWidthPx > 0f) (trackWidthPx / 2f) - (handleWidthPx / 2f) - (paddingHandle) + with(density) { 1.dp.toPx() }
            else 0f
        }
    }
    val triggerThreshold = maxDrag * 0.85f

    val dragProgress = remember { derivedStateOf { if (maxDrag > 0f) offsetX.value / maxDrag else 0f } }
    val dragNormal = remember { derivedStateOf { abs(dragProgress.value) } }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    
    val handlePulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "handlePulse"
    )

    val hintAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "hintAlpha"
    )

    val answerGreen = color_call_button
    val declineRed = color_call_end
//    val idleColor = if (isDark) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
//                   else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)

    val handleBgColor by animateColorAsState(
        targetValue = when {
            dragProgress.value > 0.1f -> answerGreen
            dragProgress.value < -0.1f -> declineRed
            else -> Color.White //if (isDark) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
        },
        label = "handleColor"
    )

    val iconTint by animateColorAsState(
        targetValue = if (dragNormal.value > 0.1f) Color.White 
                     else Color.Black, //if (isDark) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
        label = "iconTint"
    )
    
    val iconRotation by remember { derivedStateOf {
        dragProgress.value * 135f
    } }

    Box(
        modifier = Modifier
            .padding(bottom = 36.dp)
            .fillMaxWidth()
            .height(trackHeight)
            .padding(horizontal = 16.dp)
            .onSizeChanged { trackWidthPx = it.width.toFloat() }
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant/*.copy(alpha = 0.2f)*/)
            //.border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f), CircleShape)
    ) {
        Text(
            stringResource(R.string.decline),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 32.dp)
                .alpha((1f - (dragProgress.value * -2f).coerceIn(0f, 1f)) * hintAlpha),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = declineRed.copy(alpha = 0.8f)
        )

        Text(
            stringResource(R.string.answer),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 32.dp)
                .alpha((1f - (dragProgress.value * 2f).coerceIn(0f, 1f)) * hintAlpha),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = answerGreen.copy(alpha = 0.8f)
        )

        // drag handle
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .graphicsLayer {
                    val idleFactor = (1f - dragNormal.value * 5f).coerceIn(0f, 1f)
                    scaleX = 1f + (handlePulseScale - 1f) * idleFactor
                    scaleY = 1f + (handlePulseScale - 1f) * idleFactor
                }
                .width(handleWidth)
                .height(handleHeight)
                .clip(CircleShape)
                .background(handleBgColor)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            coroutineScope.launch {
                                when {
                                    offsetX.value > triggerThreshold -> {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                        } else {
                                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                        }
                                        onAnswer()
                                    }

                                    offsetX.value < -triggerThreshold -> {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                            view.performHapticFeedback(HapticFeedbackConstants.REJECT)
                                        } else {
                                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                        }
                                        onDecline()
                                    }

                                    else -> offsetX.animateTo(
                                        0f,
                                        spring(
                                            dampingRatio = 0.75f,
                                            stiffness = Spring.StiffnessMedium
                                        )
                                    )
                                }
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            coroutineScope.launch {
                                val newOffset = (offsetX.value + dragAmount).coerceIn(
                                    -maxDrag * 1.1f,
                                    maxDrag * 1.1f
                                )
                                offsetX.snapTo(newOffset)
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            val icon = Icons.Rounded.Call //if (dragProgress.value < -0.2f) Icons.Rounded.CallEnd else Icons.Rounded.Call
            
            Crossfade(targetState = icon, animationSpec = tween(150), label = "icon") { targetIcon ->
                Icon(
                    targetIcon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier
                        .size(32.dp)
                        .graphicsLayer { rotationZ = iconRotation }
                )
            }
        }
    }
}

@Composable
fun VerticalSwipeToAnswer(onAnswer: () -> Unit, onDecline: () -> Unit) {
    val coroutineScope = rememberCoroutineScope()
    val offsetY = remember { Animatable(0f) }
    val density = LocalDensity.current
    val view = LocalView.current

    val handleSize = 80.dp
    val maxDrag = with(density) { 100.dp.toPx() } // Smaller movement region
    val triggerThreshold = maxDrag * 0.7f

    val dragProgress = remember { derivedStateOf { offsetY.value / maxDrag } }
    
    // Pulse animation for the button
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseAlpha"
    )

    val textColor = MaterialTheme.colorScheme.onSurface

    // Arrow bounce animation
    val arrowOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 12f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "arrowBounce"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp) //360.dp
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        // --- UP SECTION (Answer) ---
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (-110).dp)
                .graphicsLayer {
                    alpha = (0.4f + (dragProgress.value * -1.8f)).coerceIn(0f, 1f)
                    translationY = -arrowOffset
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Rounded.KeyboardArrowUp, null, tint = textColor, modifier = Modifier.size(36.dp))
            Text(
                stringResource(R.string.swipe_up_to_answer),
                style = MaterialTheme.typography.titleMedium,
                color = textColor.copy(alpha = 0.9f),
                fontWeight = FontWeight.Light,
                fontStyle = FontStyle.Italic
            )
        }

        // --- DOWN SECTION (Reject) ---
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = 110.dp)
                .graphicsLayer {
                    alpha = (0.4f + (dragProgress.value * 1.8f)).coerceIn(0f, 1f)
                    translationY = arrowOffset
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                stringResource(R.string.swipe_down_to_reject),
                style = MaterialTheme.typography.titleMedium,
                color = textColor.copy(alpha = 0.9f),
                fontWeight = FontWeight.Light,
                fontStyle = FontStyle.Italic
            )
            Icon(Icons.Rounded.KeyboardArrowDown, null, tint = textColor, modifier = Modifier.size(36.dp))
        }

        // --- CENTER BUTTON ---
        Box(contentAlignment = Alignment.Center) {
            // Pulsing rings
            if (abs(offsetY.value) < 5f) {
                Box(
                    modifier = Modifier
                        .size(handleSize)
                        .scale(pulseScale)
                        .background(textColor.copy(alpha = pulseAlpha * 0.4f), CircleShape)
                )
                Box(
                    modifier = Modifier
                        .size(handleSize)
                        .scale(pulseScale * 1.4f)
                        .border(1.dp, textColor.copy(alpha = pulseAlpha * 0.2f), CircleShape)
                )
            }

            val handleBgColor by animateColorAsState(
                targetValue = when {
                    offsetY.value < -15f -> color_call_button
                    offsetY.value > 15f -> color_call_end
                    else -> Color.White
                },
                label = "bgColor"
            )
            
            val iconTint by animateColorAsState(
                targetValue = if (abs(offsetY.value) > 15f) Color.White else color_call_button,
                label = "iconTint"
            )

            Box(
                modifier = Modifier
                    .offset { IntOffset(0, offsetY.value.roundToInt()) }
                    .size(handleSize)
                    .shadow(if (abs(offsetY.value) > 5f) 12.dp else 4.dp, CircleShape)
                    .background(handleBgColor, CircleShape)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = {
                                coroutineScope.launch {
                                    when {
                                        offsetY.value < -triggerThreshold -> {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                            } else {
                                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                            }
                                            onAnswer()
                                        }

                                        offsetY.value > triggerThreshold -> {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                                view.performHapticFeedback(HapticFeedbackConstants.REJECT)
                                            } else {
                                                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                            }
                                            onDecline()
                                        }

                                        else -> offsetY.animateTo(
                                            0f,
                                            spring(
                                                dampingRatio = 0.7f,
                                                stiffness = Spring.StiffnessMedium
                                            )
                                        )
                                    }
                                }
                            },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                coroutineScope.launch {
                                    val newOffset =
                                        (offsetY.value + dragAmount).coerceIn(-maxDrag, maxDrag)
                                    offsetY.snapTo(newOffset)
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                val icon = if (offsetY.value > 5f) Icons.Rounded.CallEnd else Icons.Rounded.Call
                
                Crossfade(targetState = icon, label = "icon") { targetIcon ->
                    Icon(
                        targetIcon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun IPhoneSwipeToAnswer(onAnswer: () -> Unit, onDecline: () -> Unit, onMessage: () -> Unit) {
    val coroutineScope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val density = LocalDensity.current
    val view = LocalView.current
    val isDark = isSystemInDarkTheme()
    
    val trackWidth = 320.dp
    val trackHeight = 94.dp
    val handleSize = 78.dp
    val handlePadding = 8.dp
    
    val trackWidthPx = with(density) { trackWidth.toPx() }
    val handleSizePx = with(density) { handleSize.toPx() }
    val handlePaddingPx = with(density) { handlePadding.toPx() }
    
    val maxDrag = trackWidthPx - handleSizePx - (handlePaddingPx * 2)

    val trackBgColor = MaterialTheme.colorScheme.onSurface.copy(alpha = if (isDark) 0.15f else 0.1f)
    val buttonContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
    val handleBgColor = Color.White

    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "offset"
    )

    val dragProgress = remember { derivedStateOf { if (maxDrag > 0f) offsetX.value / maxDrag else 0f } }
    val iconRotation by remember { derivedStateOf {
        dragProgress.value * 135f
    } }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(42.dp),
        modifier = Modifier.padding(bottom = 42.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(0.75f),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = onDecline,
                    modifier = Modifier
                        .size(60.dp)
                        .background(
                            trackBgColor,
                            CircleShape
                        )
                ) {
                    Icon(
                        Icons.Rounded.CallEnd,
                        contentDescription = stringResource(R.string.decline),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    stringResource(R.string.decline),
                    style = MaterialTheme.typography.labelMedium, 
                    color = buttonContentColor,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = onMessage,
                    modifier = Modifier
                        .size(60.dp)
                        .background(
                            trackBgColor,
                            CircleShape
                        )
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_message_outline),
                        contentDescription = stringResource(R.string.message),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    stringResource(R.string.message),
                    style = MaterialTheme.typography.labelMedium, 
                    color = buttonContentColor,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .width(trackWidth)
                .height(trackHeight)
                .clip(CircleShape),
//                .background(trackBgColor),
//                .border(
//                    1.dp,
//                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
//                    CircleShape
//                ),
            contentAlignment = Alignment.CenterStart
        ) {
            // Shrinking box
            Box(
                modifier = Modifier
                    .height(trackHeight)
                    .align(Alignment.CenterEnd)
                    .width(
                        with(density) {
                            val width = trackWidthPx - offsetX.value
                            width.coerceAtLeast(0f).toDp()
                        }
                    )
                    .clip(CircleShape)
                    .background(trackBgColor)
            )

            val baseTextColor = MaterialTheme.colorScheme.onSurface
            val shimmerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            
            val brush = Brush.linearGradient(
                colors = listOf(shimmerColor, baseTextColor, shimmerColor),
                start = Offset(trackWidthPx * shimmerOffset - 150f, 0f),
                end = Offset(trackWidthPx * shimmerOffset + 150f, 0f)
            )

            Text(
                text = stringResource(R.string.slide_to_answer),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = handleSize)
                    .graphicsLayer {
                        alpha = (1f - (offsetX.value / maxDrag) * 2f).coerceIn(0f, 1f)
                    },
                style = MaterialTheme.typography.titleMedium.copy(
                    brush = brush,
                    fontWeight = FontWeight.Medium
                ),
                textAlign = TextAlign.Center
            )

            Box(
                modifier = Modifier
                    .padding(start = handlePadding)
                    .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                    .size(handleSize)
                    .clip(CircleShape)
                    .background(handleBgColor)
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                coroutineScope.launch {
                                    if (offsetX.value > maxDrag * 0.85f) {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                        } else {
                                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                        }
                                        onAnswer()
                                    } else {
                                        offsetX.animateTo(0f, spring(dampingRatio = 0.8f))
                                    }
                                }
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                coroutineScope.launch {
                                    offsetX.snapTo(
                                        (offsetX.value + dragAmount).coerceIn(
                                            0f,
                                            maxDrag
                                        )
                                    )
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Call,
                    contentDescription = null,
                    tint = color_call_button,
                    modifier = Modifier
                        .size(36.dp)
                        .graphicsLayer { rotationZ = iconRotation }
                )
            }
        }
    }
}

@Composable
fun IncomingCallButtons(
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    onAnswerAndDecline: (() -> Unit)?
) {
    val declineColor = color_call_end
    val answerColor = color_call_button

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp)
            .padding(bottom = 32.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                val interaction = remember { MutableInteractionSource() }
                val isPressed by interaction.collectIsPressedAsState()
                val radius by animateDpAsState(
                    if (isPressed) 28.dp else 42.dp,
                    spring(stiffness = Spring.StiffnessMedium),
                    label = "btnDeclineRadius"
                )
                Box(
                    modifier = Modifier
                        .size(height = 68.dp, width = 80.dp)
                        .scale(scale * 1.06f)
                        .background(declineColor.copy(alpha = 0.2f), RoundedCornerShape(radius))
                )
                Surface(
                    onClick = onDecline,
                    modifier = Modifier.size(height = 68.dp, width = 82.dp),
                    shape = RoundedCornerShape(radius),
                    color = declineColor,
                    interactionSource = interaction
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.CallEnd,
                            contentDescription = stringResource(R.string.decline),
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.decline),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 96.dp)
            )
        }

        if (onAnswerAndDecline != null) Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                val interaction = remember { MutableInteractionSource() }
                val isPressed by interaction.collectIsPressedAsState()
                val radius by animateDpAsState(
                    if (isPressed) 28.dp else 42.dp,
                    spring(stiffness = Spring.StiffnessMedium),
                    label = "btnRadius"
                )
                Box(
                    modifier = Modifier
                        .size(height = 68.dp, width = 80.dp)
                        .scale(scale * 1.06f)
                        .background(declineColor.copy(alpha = 0.1f), RoundedCornerShape(radius))
                )
                Surface(onClick = onAnswerAndDecline,
                    modifier = Modifier.size(height = 68.dp, width = 82.dp),
                    shape = RoundedCornerShape(radius),
                    color = answerColor,
                    interactionSource = interaction
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.CallEnd,
                            contentDescription = stringResource(R.string.answer_and_decline),
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.answer_and_decline),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelLarge,
                lineHeight = MaterialTheme.typography.labelMedium.lineHeight,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 96.dp)
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                val interaction = remember { MutableInteractionSource() }
                val isPressed by interaction.collectIsPressedAsState()
                val radius by animateDpAsState(
                    if (isPressed) 28.dp else 42.dp,
                    spring(stiffness = Spring.StiffnessMedium),
                    label = "btnAnswerRadius"
                )
                Box(
                    modifier = Modifier
                        .size(height = 68.dp, width = 80.dp)
                        .scale(scale * 1.06f)
                        .background(answerColor.copy(alpha = 0.2f), RoundedCornerShape(radius))
                )
                Surface(onClick = onAnswer,
                    modifier = Modifier.size(height = 68.dp, width = 82.dp),
                    shape = RoundedCornerShape(radius),
                    color = answerColor,
                    interactionSource = interaction
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Call,
                            contentDescription = stringResource(R.string.answer),
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.answer),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 96.dp)
            )
        }
    }
}

@Composable
fun DefaultSwipeToAnswer(onAnswer: () -> Unit, onDecline: () -> Unit, onMessage: () -> Unit) {
    val coroutineScope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val density = LocalDensity.current
    val view = LocalView.current
    val isDark = isSystemInDarkTheme()

    val trackWidth = 320.dp
    val trackHeight = 80.dp
    val handleHeight = 68.dp
    val handleWidth = 96.dp
    val handlePadding = 6.dp

    val trackWidthPx = with(density) { trackWidth.toPx() }
    val handleWidthPx = with(density) { handleWidth.toPx() }
    val handlePaddingPx = with(density) { handlePadding.toPx() }

    val maxDrag = trackWidthPx - handleWidthPx - (handlePaddingPx * 2)

    val buttonBgColor = MaterialTheme.colorScheme.onSurface.copy(alpha = if (isDark) 0.15f else 0.1f)
//    val trackBgColor = MaterialTheme.colorScheme.primary
    val buttonContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
    val buttonIconColor = MaterialTheme.colorScheme.onSurface
    val handleBgColor = Color.White

    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "offset"
    )

    val dragProgress = remember { derivedStateOf { if (maxDrag > 0f) offsetX.value / maxDrag else 0f } }
    val iconRotation by remember { derivedStateOf {
        dragProgress.value * 135f
    } }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(42.dp),
        modifier = Modifier.padding(bottom = 42.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(0.75f),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = onDecline,
                    modifier = Modifier
                        .size(60.dp)
                        .background(
                            buttonBgColor,
                            CircleShape
                        )
                ) {
                    Icon(
                        Icons.Rounded.CallEnd,
                        contentDescription = stringResource(R.string.decline),
                        tint = buttonIconColor
                    )
                }
                Text(
                    stringResource(R.string.decline),
                    style = MaterialTheme.typography.labelMedium,
                    color = buttonContentColor,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = onMessage,
                    modifier = Modifier
                        .size(60.dp)
                        .background(
                            buttonBgColor,
                            CircleShape
                        )
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_message_outline),
                        contentDescription = stringResource(R.string.message),
                        tint = buttonIconColor
                    )
                }
                Text(
                    stringResource(R.string.message),
                    style = MaterialTheme.typography.labelMedium,
                    color = buttonContentColor,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .width(trackWidth)
                .height(trackHeight)
                .clip(CircleShape),
//                .background(trackBgColor),
//                .border(
//                    1.dp,
//                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
//                    CircleShape
//                ),
            contentAlignment = Alignment.CenterStart
        ) {
            // Shrinking box
            Box(
                modifier = Modifier
                    .height(trackHeight)
                    .align(Alignment.CenterEnd)
                    .width(
                        with(density) {
                            val width = trackWidthPx - offsetX.value
                            width.coerceAtLeast(0f).toDp()
                        }
                    )
                    .clip(CircleShape)
                    .background(buttonBgColor /*Brush.verticalGradient(
                        colors = listOf(
                            trackBgColor.copy(alpha = 0.7f),
                            trackBgColor.copy(alpha = 0.9f),
                            trackBgColor
                        )
                    )*/)
            )

            val baseTextColor = buttonIconColor
            val shimmerColor = buttonIconColor.copy(alpha = 0.4f)

            val brush = Brush.linearGradient(
                colors = listOf(shimmerColor, baseTextColor, shimmerColor),
                start = Offset(trackWidthPx * shimmerOffset - 150f, 0f),
                end = Offset(trackWidthPx * shimmerOffset + 150f, 0f)
            )

            Text(
                text = stringResource(R.string.slide_to_answer),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = handleWidth, end = 56.dp)
                    .graphicsLayer {
                        alpha = (1f - (offsetX.value / maxDrag) * 2f).coerceIn(0f, 1f)
                    },
                style = MaterialTheme.typography.titleMedium.copy(
                    brush = brush,
                    fontWeight = FontWeight.Medium
                ),
                textAlign = TextAlign.Center
            )
            Icon(
                Icons.AutoMirrored.Rounded.ArrowRight,
                contentDescription = null,
                tint = handleBgColor.copy(0.3f),
                modifier = Modifier.padding(end = 28.dp).size(46.dp).align(Alignment.CenterEnd)
            )
            Icon(
                Icons.AutoMirrored.Rounded.ArrowRight,
                contentDescription = null,
                tint = handleBgColor.copy(0.6f),
                modifier = Modifier.padding(end = 8.dp).size(46.dp).align(Alignment.CenterEnd)
            )

            Box(
                modifier = Modifier
                    .padding(start = handlePadding)
                    .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                    .size(handleWidth, handleHeight)
                    .clip(CircleShape)
                    .background(handleBgColor)
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                coroutineScope.launch {
                                    if (offsetX.value > maxDrag * 0.85f) {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                        } else {
                                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                        }
                                        onAnswer()
                                    } else {
                                        offsetX.animateTo(0f, spring(dampingRatio = 0.8f))
                                    }
                                }
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                coroutineScope.launch {
                                    offsetX.snapTo(
                                        (offsetX.value + dragAmount).coerceIn(
                                            0f,
                                            maxDrag
                                        )
                                    )
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Call,
                    contentDescription = null,
                    tint = Color.Black.copy(alpha = 0.9f), //trackBgColor.copy(alpha = 0.9f),
                    modifier = Modifier
                        .size(36.dp)
                        .graphicsLayer { rotationZ = iconRotation }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MoreItem(
    headline: String,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "ListItemScale"
    )

    Surface(
        color = cardColor,
        shape = RoundedCornerShape(cardCornerSmall),
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale),
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (enabled) {
                        Modifier
                            .alpha(1f)
                            .combinedClickable(
                                interactionSource = interactionSource,
                                indication = ripple(),
                                onClick = onClick,
                            )
                    } else {
                        Modifier.alpha(0.5f)
                    }
                )
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingIcon != null) {
                RillIconBox(
                    icon = leadingIcon,
                    iconContainerColor = MaterialTheme.colorScheme.onSurface,
                    iconBgContainerColor = MaterialTheme.colorScheme.surface,
                )
                Spacer(modifier = Modifier.width(16.dp))
            }
            Text(
                modifier = Modifier.weight(1f),
                text = headline,
                style = MaterialTheme.typography.bodyLarge,
                lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (trailingIcon != null) {
                Spacer(modifier = Modifier.width(12.dp))
                Icon(
                    trailingIcon, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.width(2.dp))
            }
        }
    }
}

