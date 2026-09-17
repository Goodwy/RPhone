package dev.goodwy.rphone.view.screen

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.telecom.Call
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
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PauseCircle
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
import androidx.core.net.toUri
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.CallViewModel
import dev.goodwy.rphone.modal.`interface`.IContactsRepository
import dev.goodwy.rphone.cardCornerExtraSmall
import dev.goodwy.rphone.controller.lock.AppLockManager
import dev.goodwy.rphone.controller.sensor.PocketModeManager
import dev.goodwy.rphone.controller.util.NoteManager
import dev.goodwy.rphone.modal.data.getDisplayName
import dev.goodwy.rphone.view.components.RillExpressiveCard
import dev.goodwy.rphone.view.components.RillSelectionDialog
import dev.goodwy.rphone.view.theme.MyColors.bottomBarColor
import dev.goodwy.rphone.view.theme.MyColors.cardColor
import dev.goodwy.rphone.view.theme.MyColors.dialpadKeyColor
import dev.goodwy.rphone.view.theme.color_call_end
import dev.goodwy.rphone.controller.util.formatDuration
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.liquidglass.LocalLiquidGlassBackdrop
import dev.goodwy.rphone.liquidglass.backdrops.LayerBackdrop
import dev.goodwy.rphone.liquidglass.drawBackdrop
import dev.goodwy.rphone.liquidglass.drawPlainBackdrop
import dev.goodwy.rphone.liquidglass.effects.blur
import dev.goodwy.rphone.liquidglass.effects.colorControls
import dev.goodwy.rphone.liquidglass.effects.lens
import dev.goodwy.rphone.liquidglass.highlight.Highlight
import dev.goodwy.rphone.liquidglass.shadow.Shadow
import dev.goodwy.rphone.view.screen.settings.PasswordSetupDialog
import dev.goodwy.rphone.view.screen.settings.PinSetupDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import kotlin.time.Duration.Companion.milliseconds

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
    skipIncomingScreen: Boolean = false,
    liquidGlassBackdrop: LayerBackdrop
) {
    val view = LocalView.current
    val context = LocalContext.current
    val preferenceManager = koinInject<PreferenceManager>()
    val contactsRepo = koinInject<IContactsRepository>()
    val callViewModel = koinInject<CallViewModel>()
    // telecomManager removed as it's now handled by ViewModel

    val allCalls by callViewModel.allCalls.collectAsStateWithLifecycle()
    val otherCall = remember(allCalls, call) {
        @Suppress("DEPRECATION")
        allCalls.find { it != call && it.state != Call.STATE_DISCONNECTED }
    }

    val simLabel by callViewModel.simLabel.collectAsStateWithLifecycle()
    val isMuted = audioState?.isMuted ?: false

    val callDuration by callViewModel.callDuration.collectAsStateWithLifecycle()
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
    val hideAvatarWithBg = remember(settingsState) {
        preferenceManager.getBoolean(PreferenceManager.KEY_HIDE_AVATAR_WITH_BACKGROUND, false)
    }
    val hasBackground = !backgroundUri.isNullOrEmpty()
    val shouldShowAvatar = showCallScreenAvatar && !(hideAvatarWithBg && hasBackground)

    val globalBackdrop = LocalLiquidGlassBackdrop.current
    val liquidGlass = remember(settingsState) { preferenceManager.getBoolean(PreferenceManager.KEY_LIQUID_GLASS, false) }
    val lgCallScreen = remember(settingsState) { preferenceManager.getBoolean(PreferenceManager.KEY_LG_CALL_SCREEN, true) }
    val blurEffects = remember(settingsState) { preferenceManager.getBoolean(PreferenceManager.KEY_BLUR_EFFECTS, false) }
    val blurCallScreen = remember(settingsState) { preferenceManager.getBoolean(PreferenceManager.KEY_BLUR_CALL_SCREEN, true) }
    val blurIntensity = remember(settingsState) { preferenceManager.getInt(PreferenceManager.KEY_BLUR_INTENSITY, 20).toFloat() }
    val useLgCallScreen = liquidGlass && lgCallScreen && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && globalBackdrop != null
    val useBlurCallScreen = blurEffects && blurCallScreen && !useLgCallScreen

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // LaunchedEffect for duration removed (handled by ViewModel)

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

    var showQuickResponsesSheet by remember { mutableStateOf(false) }
    var showCallNotesSheet by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val pocketModeEnabled = remember(settingsState) {
        preferenceManager.getBoolean(PreferenceManager.KEY_POCKET_MODE, false)
    }
    var isPocketModeCovered by remember { mutableStateOf(false) }
    var pocketModeDismissedManually by remember { mutableStateOf(false) }

    DisposableEffect(callState, pocketModeEnabled) {
        if (pocketModeEnabled && callState == Call.STATE_RINGING) {
            val manager = PocketModeManager(context)
            manager.startListening { isNear ->
                isPocketModeCovered = isNear
            }
            onDispose {
                manager.stopListening()
            }
        } else {
            isPocketModeCovered = false
            onDispose { }
        }
    }

    val onSendQuickResponse: (String) -> Unit = { message ->
        try {
            call.reject(true, message)
        } catch (e: Exception) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    call.reject(Call.REJECT_REASON_DECLINED)
                } else {
                    call.disconnect()
                }
            } catch (_: Exception) {}
            try {
                val intent = Intent(Intent.ACTION_SENDTO, "smsto:$phoneNumber".toUri()).apply {
                    putExtra("sms_body", message)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {}
        }
        showQuickResponsesSheet = false
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
            delay(1000.milliseconds)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                NoteManager.writeNote(context, contactName, phoneNumber, noteText)
            }
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
        ExpressiveBackground(photoUri, backgroundUri, liquidGlassBackdrop, useLgCallScreen || useBlurCallScreen)

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
                            style = MaterialTheme.typography.headlineSmall,
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

                        val currentSimLabel = simLabel
                        if (currentSimLabel != null) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                Text(
                                    text = currentSimLabel,
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
                        visible = shouldShowAvatar && photoUri != null,
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

                val controlContent: @Composable () -> Unit = {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 9.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment     = Alignment.CenterVertically
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
                                                style = MaterialTheme.typography.titleMedium,
                                                color = controlBtnFg
                                            )
                                            IconButton(onClick = {
                                                showKeypad = false
                                                showNoteWindow = false
                                                showMore = false
                                            }) { Icon(Icons.Rounded.Cancel, stringResource(R.string.cancel), tint = controlBtnFg) }
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
                                                    shape = RoundedCornerShape(cardCornerExtraSmall),
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
                                    CallAudioState.ROUTE_BLUETOOTH -> try {
                                        audioState?.activeBluetoothDevice?.name ?: bluetoothLabel
                                    } catch (e: SecurityException) {
                                        bluetoothLabel
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
                                    else Modifier
                                        .fillMaxWidth(hangupWidthFraction.coerceIn(0.1f, 1.0f))
                                        .height(68.dp),
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
                }

                val controlShape = MaterialTheme.shapes.extraExtraLarge.copy(bottomStart = CornerSize(0.dp), bottomEnd = CornerSize(0.dp))

                if (useLgCallScreen) {
                    Surface(
                        shape           = controlShape,
                        color           = bottomBarColor.copy(alpha = 0.35f),
                        shadowElevation = 0.dp,
                        tonalElevation  = 0.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .drawBackdrop(
                                backdrop = globalBackdrop,
                                shape = { controlShape },
                                effects = {
                                    val d = density
                                    colorControls(saturation = 1.4f)
                                    blur(blurIntensity * d)
                                    lens(
                                        refractionHeight = 23f * d,
                                        refractionAmount = 64f * d
                                    )
                                },
                                highlight = { Highlight.Default }
                            )
                    ) { controlContent() }
                } else if (useBlurCallScreen && globalBackdrop != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Surface(
                        shape           = controlShape,
                        color           = bottomBarColor.copy(alpha = 0.72f),
                        shadowElevation = 0.dp,
                        tonalElevation  = 0.dp,
                        modifier        = Modifier
                            .fillMaxWidth()
                            .drawPlainBackdrop(
                                backdrop = globalBackdrop,
                                shape = { controlShape },
                                effects = { blur(blurIntensity * density) }
                            )
                    ) { controlContent() }
                } else {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(controlShape),
                        color = bottomBarColor
                    ) {
                        controlContent()
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
                        val buttonBgColor =
                            if (useLgCallScreen || useBlurCallScreen) bottomBarColor.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceVariant
                        val interaction = remember { MutableInteractionSource() }
                        val isPressed by interaction.collectIsPressedAsState()
                        val radius by animateDpAsState(
                            if (isPressed) 16.dp else 40.dp,
                            spring(stiffness = Spring.StiffnessMedium),
                            label = "btnMessageRadius"
                        )
                        Surface(
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                callDisconnect(true)
                                val intent = Intent(Intent.ACTION_SENDTO).apply {
                                    data = "smsto:$phoneNumber".toUri()
                                }
                                context.startActivity(intent)
                            },
                            shape = RoundedCornerShape(radius),
                            color = buttonBgColor,
                            interactionSource = interaction,
//                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                            modifier = Modifier
                                .height(45.dp)
                                .wrapContentWidth()
                                .then(
                                    if (useLgCallScreen) Modifier.drawBackdrop(
                                        backdrop = globalBackdrop,
                                        shape = { RoundedCornerShape(radius) },
                                        shadow = { Shadow(radius = 8.dp) },
                                        effects = {
                                            val d = density
                                            colorControls(saturation = 1.3f)
                                            blur(blurIntensity * d)
                                            lens(
                                                refractionHeight = 18f * d,
                                                refractionAmount = 52f * d
                                            )
                                        },
                                        highlight = { Highlight.Default }
                                    )
                                    else if (useBlurCallScreen && globalBackdrop != null) Modifier.drawPlainBackdrop(
                                        backdrop = globalBackdrop,
                                        shape = { RoundedCornerShape(radius) },
                                        shadow = { Shadow(radius = 8.dp) },
                                        effects = { blur(blurIntensity * density) }
                                    )
                                    else Modifier
                                )
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
                            useLg = useLgCallScreen,
                            useBlur = useBlurCallScreen,
                            blurIntensity = blurIntensity,
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
                            useLg = useLgCallScreen,
                            useBlur = useBlurCallScreen,
                            blurIntensity = blurIntensity,
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
                            useLg = useLgCallScreen,
                            useBlur = useBlurCallScreen,
                            blurIntensity = blurIntensity,
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
                            useLg = useLgCallScreen,
                            useBlur = useBlurCallScreen,
                            blurIntensity = blurIntensity,
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
                            useLg = useLgCallScreen,
                            useBlur = useBlurCallScreen,
                            blurIntensity = blurIntensity,
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

        if (isPocketModeCovered && !pocketModeDismissedManually && callState == Call.STATE_RINGING) {
            PocketModeOverlay(
                onDismiss = { pocketModeDismissedManually = true }
            )
        }

        if (showQuickResponsesSheet) {
            QuickResponsesBottomSheet(
                phoneNumber = phoneNumber,
                contactName = contactName,
                onDismiss = { showQuickResponsesSheet = false },
                onSend = onSendQuickResponse,
                onOpenSmsApp = {
                    try {
                        if (call.state == Call.STATE_RINGING) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                call.reject(Call.REJECT_REASON_DECLINED)
                            } else {
                                call.disconnect()
                            }
                        } else {
                            call.disconnect()
                        }
                    } catch (_: Exception) {}
                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                        data = "smsto:$phoneNumber".toUri()
                    }
                    context.startActivity(intent)
                    showQuickResponsesSheet = false
                },
//                onScheduleReminder = { delayMinutes, label ->
//                    scope.launch {
//                        reminderManager.scheduleReminder(
//                            phoneNumber = phoneNumber,
//                            contactName = contactName.ifBlank { null },
//                            delayMinutes = delayMinutes,
//                            note = "Callback reminder from incoming call"
//                        )
//                        Toast.makeText(context, "Reminder set for $label", Toast.LENGTH_SHORT).show()
//                    }
//                    try {
//                        if (call.state == Call.STATE_RINGING) {
//                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
//                                call.reject(Call.REJECT_REASON_DECLINED)
//                            } else {
//                                call.disconnect()
//                            }
//                        } else {
//                            call.disconnect()
//                        }
//                    } catch (_: Exception) {}
//                    showQuickResponsesSheet = false
//                }
            )
        }
    }

    if (showCallBiometricUnlock) {
        val biometricType = preferenceManager.getString(PreferenceManager.KEY_BIOMETRICS_TYPE, "") ?: ""
        @SuppressLint("ContextCastToActivity")
        val callActivity = LocalContext.current as? FragmentActivity
        fun onBiometricFail() {
            showCallBiometricUnlock = false
            pendingAction = null
        }
        when (biometricType) {
            "system" -> {
                val activity = callActivity ?: run { onBiometricFail(); return }
                AppLockManager.authenticate(
                    activity = activity,
                    title = stringResource(R.string.verify_your_identity_to_access_call),
                    onSuccess = {
                        callBiometricUnlocked = true
                        biometricGatesScreen = false
                        showCallBiometricUnlock = false
                        pendingAction?.invoke(); pendingAction = null
                    },
                    onError = { _, _ -> onBiometricFail()}
                )
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