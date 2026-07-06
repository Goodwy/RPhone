//package dev.goodwy.rphone.view.screen
//
//import android.app.KeyguardManager
//import android.content.Context
//import android.content.Intent
//import android.os.*
//import android.telecom.Call
//import android.telecom.CallAudioState
//import android.telecom.VideoProfile
//import androidx.activity.ComponentActivity
//import androidx.activity.compose.setContent
//import androidx.activity.enableEdgeToEdge
//import androidx.compose.animation.*
//import androidx.compose.animation.core.*
//import androidx.compose.foundation.background
//import androidx.compose.foundation.gestures.detectHorizontalDragGestures
//import androidx.compose.foundation.interaction.MutableInteractionSource
//import androidx.compose.foundation.interaction.collectIsPressedAsState
//import androidx.compose.foundation.isSystemInDarkTheme
//import androidx.compose.foundation.layout.*
//import androidx.compose.foundation.lazy.LazyColumn
//import androidx.compose.foundation.lazy.items
//import androidx.compose.foundation.shape.CircleShape
//import androidx.compose.foundation.shape.RoundedCornerShape
//import androidx.compose.material.icons.Icons
//import androidx.compose.material.icons.automirrored.filled.CallMerge
//import androidx.compose.material.icons.automirrored.rounded.Backspace
//import androidx.compose.material.icons.automirrored.outlined.StickyNote2
//import androidx.compose.material.icons.automirrored.rounded.VolumeDown
//import androidx.compose.material.icons.automirrored.rounded.VolumeUp
//import androidx.compose.material.icons.filled.AccountCircle
//import androidx.compose.material.icons.filled.Check
//import androidx.compose.material.icons.filled.Dialpad
//import androidx.compose.material.icons.rounded.AccessTime
//import androidx.compose.material.icons.rounded.Bluetooth
//import androidx.compose.material.icons.rounded.BluetoothDisabled
//import androidx.compose.material.icons.rounded.Call
//import androidx.compose.material.icons.rounded.CallEnd
//import androidx.compose.material.icons.rounded.Cancel
//import androidx.compose.material.icons.rounded.Mic
//import androidx.compose.material.icons.rounded.MicOff
//import androidx.compose.material.icons.rounded.Pause
//import androidx.compose.material.icons.rounded.Person
//import androidx.compose.material.icons.rounded.PersonAdd
//import androidx.compose.material.icons.rounded.PlayArrow
//import androidx.compose.material.icons.rounded.Search
//import androidx.compose.material3.*
//import androidx.compose.runtime.*
//import androidx.compose.ui.Alignment
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.draw.alpha
//import androidx.compose.ui.draw.blur
//import androidx.compose.ui.draw.clip
//import androidx.compose.ui.draw.scale
//import androidx.compose.ui.graphics.Color
//import androidx.compose.ui.graphics.graphicsLayer
//import androidx.compose.ui.graphics.vector.ImageVector
//import androidx.compose.ui.input.pointer.pointerInput
//import androidx.compose.ui.layout.ContentScale
//import androidx.compose.ui.platform.LocalContext
//import androidx.compose.ui.platform.LocalDensity
//import androidx.compose.ui.platform.LocalView
//import androidx.compose.ui.text.font.FontWeight
//import androidx.compose.ui.text.style.TextOverflow
//import androidx.compose.ui.layout.onSizeChanged
//import androidx.compose.ui.unit.IntOffset
//import androidx.compose.ui.unit.dp
//import androidx.compose.ui.unit.sp
//import coil.compose.AsyncImage
//import coil.request.ImageRequest
//import dev.goodwy.rphone.controller.CallService
//import dev.goodwy.rphone.controller.util.NoteManager
//import dev.goodwy.rphone.controller.util.PreferenceManager
//import dev.goodwy.rphone.modal.`interface`.ICallLogRepository
//import dev.goodwy.rphone.modal.`interface`.IContactsRepository
//import dev.goodwy.rphone.modal.data.CallLogEntry
//import dev.goodwy.rphone.modal.data.Contact
//import dev.goodwy.rphone.view.components.RillAvatar
//import dev.goodwy.rphone.view.theme.Rill4Theme
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.delay
//import kotlinx.coroutines.launch
//import kotlinx.coroutines.withContext
//import org.koin.android.ext.android.inject
//import kotlin.math.roundToInt
//import android.hardware.Sensor
//import android.hardware.SensorEvent
//import android.hardware.SensorEventListener
//import android.hardware.SensorManager
//import android.view.WindowManager
//import androidx.compose.ui.platform.LocalConfiguration
//import android.content.res.Configuration
//import androidx.compose.foundation.rememberScrollState
//import androidx.compose.foundation.verticalScroll
//import android.bluetooth.BluetoothAdapter
//import android.bluetooth.BluetoothHeadset
//import android.bluetooth.BluetoothProfile
//import android.content.BroadcastReceiver
//import android.content.IntentFilter
//import androidx.compose.foundation.combinedClickable
//import androidx.compose.material.icons.rounded.ContentCopy
//import androidx.compose.material.icons.rounded.ContentPaste
//import androidx.compose.ui.res.painterResource
//import androidx.compose.ui.res.stringResource
//import androidx.compose.ui.text.style.TextAlign
//import androidx.compose.ui.util.lerp
//import dev.goodwy.rphone.view.theme.color_call_end
//import dev.goodwy.rphone.R
//import dev.goodwy.rphone.controller.util.copyToClipboard
//import dev.goodwy.rphone.controller.util.getTextFromClipboard
//import dev.goodwy.rphone.controller.util.makeCall
//import dev.goodwy.rphone.view.theme.MyColors.bottomBarColor
//import dev.goodwy.rphone.view.theme.MyColors.dialpadKeyColor
//import dev.goodwy.rphone.view.theme.color_call_button
//
//class CallActivity : ComponentActivity() {
//
//    private val contactsRepo: IContactsRepository by inject()
//    private val callLogRepo: ICallLogRepository by inject()
//    private val prefs: PreferenceManager by inject()
//    private var proximityWakeLock: PowerManager.WakeLock? = null
//
//    companion object {
//        /** FloatingCallService observes this to hide the bubble when CallActivity is visible. */
//        val isInForeground = kotlinx.coroutines.flow.MutableStateFlow(false)
//    }
//
//    // Pocket mode prevention
//    private var sensorManager: SensorManager? = null
//    private var proximitySensor: Sensor? = null
//    private var isPocketBlocked = false
//    // Auto-speaker proximity tracking
//    private var autoSpeakerActive = false
//    private val proxSensorListener = object : SensorEventListener {
//        override fun onSensorChanged(event: SensorEvent) {
//            val maxRange = event.sensor.maximumRange
//            val isNear = event.values[0] < maxRange * 0.5f
//
//            // Pocket mode prevention
//            if (prefs.getBoolean(PreferenceManager.KEY_POCKET_MODE_PREVENTION, false)) {
//                isPocketBlocked = isNear
//            }
//
//            // Auto speaker: near -> earpiece, far -> speaker
//            if (prefs.getBoolean(PreferenceManager.KEY_AUTO_SPEAKER, false)) {
//                val session = CallService.currentCallSession.value
//                if (session != null && (session.state == android.telecom.Call.STATE_ACTIVE)) {
//                    if (isNear && autoSpeakerActive) {
//                        // Near ear: switch to earpiece
//                        CallService.setAudioRoute(android.telecom.CallAudioState.ROUTE_EARPIECE)
//                        autoSpeakerActive = false
//                    } else if (!isNear && !autoSpeakerActive) {
//                        // Far from ear: switch to speaker
//                        CallService.setAudioRoute(android.telecom.CallAudioState.ROUTE_SPEAKER)
//                        autoSpeakerActive = true
//                    }
//                }
//            }
//        }
//        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
//    }
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        showWhenLockedAndTurnScreenOn()
//        setupProximitySensor()
//        // Prevent notification shade from being pulled down during a call
////        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
//        enableEdgeToEdge()
//        // Instead of FLAG_FULLSCREEN, we use WindowInsetsController to control the status bar
//        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
//            show(androidx.core.view.WindowInsetsCompat.Type.statusBars())
//            systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
//        }
//        // Register pocket mode proximity listener
//        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
//        proximitySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
//        proximitySensor?.let {
//            sensorManager?.registerListener(proxSensorListener, it, SensorManager.SENSOR_DELAY_UI)
//        }
//
//        setContent {
//            Rill4Theme {
//                val session by CallService.currentCallSession.collectAsState()
//                val heldSession by CallService.heldCallSession.collectAsState()
//                val audioState by CallService.audioState.collectAsState()
//                val settingsVersion by prefs.settingsChanged.collectAsState()
//
//                val call = session?.call
//                val callState = session?.state
//
//                val proximityBgEnabled = remember(settingsVersion) {
//                    prefs.getBoolean(PreferenceManager.KEY_PROXIMITY_BG, true)
//                }
//                val isSpeakerOn = audioState?.route == CallAudioState.ROUTE_SPEAKER
//
//                LaunchedEffect(callState, isSpeakerOn, proximityBgEnabled) {
//                    when (callState) {
//                        Call.STATE_ACTIVE, Call.STATE_DIALING -> {
//                            if (proximityBgEnabled && !isSpeakerOn) {
//                                acquireProximityLock()
//                            } else {
//                                releaseProximityLock()
//                            }
//                        }
//                        else -> releaseProximityLock()
//                    }
//                    if (session == null || callState == Call.STATE_DISCONNECTED) {
//                        delay(800)
//                        finishAndRemoveTask()
//                    }
//                }
//
//                if (call != null && session != null) {
//                    val number = call.details?.handle?.schemeSpecificPart ?: ""
//                    // Stable initial values — number shown immediately, replaced by
//                    // contact name in-place once async lookup completes (no layout shift
//                    // because the composable tree is already present and sized).
//                    // Start empty so the layout is stable from the first frame.
//                    // contactName is filled by the async lookup; until then we
//                    // show the number as a subtitle-style fallback (see the status
//                    // text below the name), so there is no visible content gap.
//                    var contactName by remember { mutableStateOf("") }
//                    var photoUri by remember { mutableStateOf<String?>(null) }
//
//                    val heldCall = heldSession?.call
//                    val heldNumber = heldCall?.details?.handle?.schemeSpecificPart ?: ""
//                    var heldContactName by remember(heldNumber) { mutableStateOf(heldNumber.ifEmpty { "Unknown" }) }
//
//                    LaunchedEffect(number) {
//                        if (number.isNotEmpty()) {
//                            val contact = contactsRepo.getContactByNumber(number)
//                            if (contact != null) {
//                                contactName = contact.name
//                                photoUri = contact.photoUri
//                            } else {
//                                contactName = number
//                            }
//                        } else {
//                            contactName = "Unknown"
//                        }
//                    }
//
//                    LaunchedEffect(heldNumber) {
//                        if (heldNumber.isNotEmpty()) {
//                            contactsRepo.getContactByNumber(heldNumber)?.let {
//                                heldContactName = it.name
//                            }
//                        }
//                    }
//
//                    val answeredFromNotification = intent?.getBooleanExtra("ANSWERED_FROM_NOTIFICATION", false) ?: false
//                    ExpressiveCallScreen(
//                        call = call,
//                        callState = session?.state ?: Call.STATE_ACTIVE,
//                        contactName = contactName,
//                        phoneNumber = number,
//                        photoUri = photoUri,
//                        audioState = audioState,
//                        hasHeldCall = heldSession != null && heldSession?.state != Call.STATE_DISCONNECTED && heldSession?.state != Call.STATE_DISCONNECTING,
//                        heldCallName = heldContactName,
//                        contactsRepo = contactsRepo,
//                        callLogRepo = callLogRepo,
//                        prefs = prefs,
//                        isPocketBlocked = { isPocketBlocked },
//                        skipIncomingScreen = answeredFromNotification
//                    )
//                }
//            }
//        }
//    }
//
//    override fun onResume() {
//        super.onResume()
//        isInForeground.value = true
//        val session = CallService.currentCallSession.value
//        val audioState = CallService.audioState.value
//        val proximityBgEnabled = prefs.getBoolean(PreferenceManager.KEY_PROXIMITY_BG, true)
//        val isSpeakerOn = audioState?.route == CallAudioState.ROUTE_SPEAKER
//        val callState = session?.state
//        if (proximityBgEnabled && !isSpeakerOn &&
//            (callState == Call.STATE_ACTIVE || callState == Call.STATE_DIALING)) {
//            acquireProximityLock()
//        } else if (!proximityBgEnabled || isSpeakerOn) {
//            releaseProximityLock()
//        }
//    }
//
//    private fun setupProximitySensor() {
//        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
//        proximityWakeLock = powerManager.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "Rill::Prox")
//    }
//
//    private fun showWhenLockedAndTurnScreenOn() {
//        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
//            setShowWhenLocked(true)
//            setTurnScreenOn(true)
//        } else {
//            @Suppress("DEPRECATION")
//            window.addFlags(
//                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
//                android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
//                android.view.WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
//            )
//        }
//        (getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager)?.requestDismissKeyguard(this, null)
//    }
//
//    override fun onDestroy() { super.onDestroy(); releaseProximityLock(); sensorManager?.unregisterListener(proxSensorListener) }
//    override fun onPause() {
//        super.onPause()
//        isInForeground.value = false
//    }
//    private fun acquireProximityLock() { if (proximityWakeLock?.isHeld == false) proximityWakeLock?.acquire(20*60*1000L /*20 minutes*/) }
//    private fun releaseProximityLock() { if (proximityWakeLock?.isHeld == true) proximityWakeLock?.release() }
//}
//
//@OptIn(ExperimentalMaterial3Api::class)
//@Composable
//fun ExpressiveCallScreen(
//    call: Call,
//    callState: Int,
//    contactName: String,
//    phoneNumber: String = "",
//    photoUri: String?,
//    audioState: CallAudioState?,
//    hasHeldCall: Boolean = false,
//    heldCallName: String = "",
//    contactsRepo: IContactsRepository? = null,
//    callLogRepo: ICallLogRepository? = null,
//    prefs: PreferenceManager? = null,
//    isPocketBlocked: () -> Boolean = { false },
//    skipIncomingScreen: Boolean = false
//) {
//    val context = LocalView.current.context
//    val isMuted = audioState?.isMuted ?: false
//    val isSpeakerOn = audioState?.route == CallAudioState.ROUTE_SPEAKER
//    val isBluetoothActive = audioState?.route == CallAudioState.ROUTE_BLUETOOTH
//
//    // Bluetooth availability detection
//    var isBluetoothConnected by remember { mutableStateOf(false) }
//    DisposableEffect(context) {
//        val btAdapter = BluetoothAdapter.getDefaultAdapter()
//        // Initial check via supported routes in audioState or via BluetoothProfile proxy
//        fun checkBtConnected(): Boolean {
//            val supportedMask = audioState?.supportedRouteMask ?: 0
//            return (supportedMask and CallAudioState.ROUTE_BLUETOOTH) != 0
//        }
//        isBluetoothConnected = checkBtConnected()
//
//        val receiver = object : BroadcastReceiver() {
//            override fun onReceive(ctx: android.content.Context, intent: Intent) {
//                when (intent.action) {
//                    BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED -> {
//                        val state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, BluetoothProfile.STATE_DISCONNECTED)
//                        isBluetoothConnected = state == BluetoothProfile.STATE_CONNECTED
//                    }
//                    BluetoothAdapter.ACTION_STATE_CHANGED -> {
//                        val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_OFF)
//                        if (state == BluetoothAdapter.STATE_OFF) isBluetoothConnected = false
//                    }
//                }
//            }
//        }
//        val filter = IntentFilter().apply {
//            addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
//            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
//        }
//        context.registerReceiver(receiver, filter)
//        onDispose { context.unregisterReceiver(receiver) }
//    }
//
//    // Also update bluetooth connected from audioState changes
//    LaunchedEffect(audioState) {
//        val supportedMask = audioState?.supportedRouteMask ?: 0
//        if ((supportedMask and CallAudioState.ROUTE_BLUETOOTH) != 0) {
//            isBluetoothConnected = true
//        }
//    }
//    // When answered from notification, skip the incoming screen by treating
//    // a brief STATE_RINGING flash as already-active for UI purposes
//    val effectiveCallState = if (skipIncomingScreen && callState == Call.STATE_RINGING) Call.STATE_ACTIVE else callState
//    var isOnHold by remember { mutableStateOf(false) }
//    var showNoteWindow by remember { mutableStateOf(false) }
//
//    // ── Call-lock biometric ────────────────────────────────────────────────
//    val callLockEnabled = remember {
//        prefs?.getBoolean(PreferenceManager.KEY_BIOMETRICS_CALL_LOCK, false) == true &&
//                (prefs.getString(PreferenceManager.KEY_BIOMETRICS_TYPE, "") ?: "").isNotEmpty()
//    }
//    var callBiometricUnlocked by remember { mutableStateOf(!callLockEnabled) }
//    var showCallBiometricUnlock by remember { mutableStateOf(false) }
//    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
//
//    var showMergeConfirm by remember { mutableStateOf(false) }
//    var showAddPersonSheet by remember { mutableStateOf(false) }
//    var showDialpad by remember { mutableStateOf(false) }
//    var dtmfInput by remember { mutableStateOf("") }
//
//    // Track isAddingToCall from service so Merge button only shows when 3rd party answered
//    var isAddingToCallState by remember { mutableStateOf(CallService.isAddingToCall) }
//    LaunchedEffect(Unit) {
//        while (true) {
//            isAddingToCallState = CallService.isAddingToCall
//            kotlinx.coroutines.delay(200)
//        }
//    }
//
//    // Merge is only available when held call exists AND we are NOT still dialing the 3rd party
//    val canShowMerge = hasHeldCall && !isAddingToCallState
//
//    // Auto-dismiss merge confirm dialog if the held call disappears (3rd person hung up)
//    LaunchedEffect(hasHeldCall) {
//        if (!hasHeldCall) {
//            showMergeConfirm = false
//            showAddPersonSheet = false
//            isAddingToCallState = false
//            if (callState == Call.STATE_ACTIVE) {
//                isOnHold = false
//            }
//        }
//    }
//
//    var callDuration by remember { mutableLongStateOf(0L) }
//    val isDark = isSystemInDarkTheme()
//
//    // Hangup button width from prefs (0.1f .. 1.0f)
//    val settingsVersion by (prefs?.settingsChanged ?: kotlinx.coroutines.flow.MutableStateFlow(0)).collectAsState()
//    val hangupWidthFraction = remember(settingsVersion) {
//        prefs?.getFloat(PreferenceManager.KEY_HANGUP_WIDTH, 0.5f) ?: 0.5f
//    }
//    var noteText by remember { mutableStateOf("") }
//
//    LaunchedEffect(phoneNumber) {
//        if (phoneNumber.isNotEmpty() && noteText.isBlank()) {
//            val existing = NoteManager.readNoteByPhone(context, phoneNumber)
//            if (existing.isNotBlank()) noteText = existing
//        }
//    }
//
//    LaunchedEffect(contactName) {
//        if (phoneNumber.isNotEmpty() && noteText.isBlank()) {
//            val existing = NoteManager.readNote(context, contactName, phoneNumber)
//            if (existing.isNotBlank()) noteText = existing
//        }
//    }
//    val scope = rememberCoroutineScope()
//
//    LaunchedEffect(noteText) {
//        if (phoneNumber.isNotEmpty() && noteText.isNotBlank()) {
//            NoteManager.writeNote(context, contactName, phoneNumber, noteText)
//        }
//    }
//
//    LaunchedEffect(callState) {
//        if ((callState == Call.STATE_DISCONNECTED || callState == Call.STATE_DISCONNECTING) && noteText.isNotBlank() && phoneNumber.isNotEmpty()) {
//            NoteManager.writeNote(context, contactName, phoneNumber, noteText)
//        }
//    }
//
//    var isDisconnecting by remember { mutableStateOf(false) }
//    val disconnectOffset by animateDpAsState(
//        if (isDisconnecting) 120.dp else 0.dp,
//        tween(600),
//        label = "disconnectSlide"
//    )
//    val disconnectAlpha by animateFloatAsState(
//        if (isDisconnecting) 0f else 1f,
//        tween(600),
//        label = "disconnectAlpha"
//    )
//
//    var wasRinging by remember { mutableStateOf(callState == Call.STATE_RINGING) }
//    var screenEntered by remember { mutableStateOf(true) }
//
//    // Smooth answer transition: when ringing → active, gently scale + fade the UI in.
//    var callAnswered by remember { mutableStateOf(false) }
//    LaunchedEffect(callState) {
//        if (callState == Call.STATE_ACTIVE && wasRinging && !callAnswered) {
//            callAnswered = true
//        }
//    }
//    val answerProgress by animateFloatAsState(
//        targetValue = if (wasRinging && !callAnswered) 0f else 1f,
//        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
//        label = "answerProgress"
//    )
//    val acceptScale = if (wasRinging && callAnswered) lerp(0.97f, 1f, answerProgress) else 1f
//    val acceptAlpha = if (wasRinging && callAnswered) lerp(0.88f, 1f, answerProgress) else 1f
//
//    LaunchedEffect(callState) {
//        if (callState == Call.STATE_DISCONNECTED || callState == Call.STATE_DISCONNECTING) isDisconnecting = true
//        if (callState == Call.STATE_RINGING) wasRinging = true
//        // If call returns to active from holding (e.g. held call restored), sync isOnHold
//        if (callState == Call.STATE_ACTIVE && isOnHold) isOnHold = false
//    }
//
//    LaunchedEffect(Unit) {
//        while (true) {
//            val connectTime = call.details?.connectTimeMillis ?: 0L
//            callDuration = if (connectTime > 0L) (System.currentTimeMillis() - connectTime) / 1000L else 0L
//            delay(500)
//        }
//    }
//
//    val bgColor = MaterialTheme.colorScheme.surface
//    val onBgColor = MaterialTheme.colorScheme.onSurface
//    val subtleColor = MaterialTheme.colorScheme.onSurfaceVariant
//    val overlayColor = if (isDark) Color.White.copy(0.2f) else Color.Black.copy(0.2f)
//    val controlBtnColor = dialpadKeyColor //if (isDark) Color.White.copy(0.12f) else Color.Black.copy(0.08f)
//    val controlBtnActiveColor = if (isDark) Color.White else Color.Black
//    val controlBtnActiveFg = if (isDark) Color.Black else Color.White
//    val controlBtnFg = onBgColor
//
//    val infiniteTransition = rememberInfiniteTransition(label = "bg")
//    val driftX by infiniteTransition.animateFloat(-35f, 35f, infiniteRepeatable(tween(20000, easing = LinearEasing), RepeatMode.Reverse), label = "x")
//    val driftY by infiniteTransition.animateFloat(-25f, 25f, infiniteRepeatable(tween(25000, easing = LinearEasing), RepeatMode.Reverse), label = "y")
//
//    if (showMergeConfirm) {
//        AlertDialog(
//            onDismissRequest = { showMergeConfirm = false },
//            icon = { Icon(Icons.AutoMirrored.Filled.CallMerge, null, tint = color_call_button) },
//            title = { Text("Merge Calls") },
//            text = {
//                Text(
//                    "This will merge your current call with ${heldCallName.ifBlank { "the held call" }} into a conference call.",
//                    style = MaterialTheme.typography.bodyMedium
//                )
//            },
//            confirmButton = {
//                Button(onClick = {
//                    showMergeConfirm = false
//                    CallService.mergeCalls()
//                }) { Text("Merge") }
//            },
//            dismissButton = {
//                TextButton(onClick = { showMergeConfirm = false }) { Text("Cancel") }
//            }
//        )
//    }
//
//    if (showAddPersonSheet) {
//        AddPersonSheet(
//            context = context,
//            contactsRepo = contactsRepo,
//            callLogRepo = callLogRepo,
//            onDismiss = { showAddPersonSheet = false },
//            onPersonSelected = { number ->
//                showAddPersonSheet = false
//                scope.launch(kotlinx.coroutines.Dispatchers.Main) {
//                    // Signal CallService FIRST so it knows the next outgoing call is an "add to call"
//                    CallService.isAddingToCall = true
//                    // Hold the current call and reflect that in UI
//                    try {
//                        call.hold()
//                        isOnHold = true
//                    } catch (_: Exception) {}
//                    delay(300)
//                    try {
//                        makeCall(context, number)
//                    } catch (_: Exception) {
//                        CallService.isAddingToCall = false
//                        isOnHold = false
//                        try { call.unhold() } catch (_: Exception) {}
//                    }
//                }
//            }
//        )
//    }
//
//    val configuration = LocalConfiguration.current
//    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
//
//    val dialpadOffsetY by animateFloatAsState(
//        targetValue = if (showDialpad) 0f else 1f,
//        animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy),
//        label = "dialpadSlide"
//    )
//    val dialpadAlpha by animateFloatAsState(
//        targetValue = if (showDialpad) 1f else 0f,
//        animationSpec = tween(220),
//        label = "dialpadAlpha"
//    )
//
//    Box(modifier = Modifier
//        .fillMaxSize()
//        .background(bgColor)) {
//        Box(
//            modifier = Modifier
//                .fillMaxSize()
//                .offset(y = disconnectOffset)
//                .alpha(disconnectAlpha)
//        ) {
//            // Blurred background photo
//            if (!photoUri.isNullOrEmpty()) {
//                Box(modifier = Modifier
//                    .fillMaxSize()
//                    .graphicsLayer {
//                        translationX = driftX; translationY = driftY; scaleX = 1.4f; scaleY = 1.4f
//                    }) {
//                    AsyncImage(model = photoUri, contentDescription = null, modifier = Modifier
//                        .fillMaxSize()
//                        .blur(80.dp)
//                        .alpha(if (isDark) 0.35f else 0.2f), contentScale = ContentScale.Crop)
//                }
//            }
//
//            if (isLandscape) {
//                // ── LANDSCAPE: two-panel layout ─────────────────────────────
////                val lsStatusBarHeight = with(LocalDensity.current) { WindowInsets.statusBars.getTop(this).toDp() }
////                val lsNavBarHeight = with(LocalDensity.current) { WindowInsets.navigationBars.getBottom(this).toDp() }
////                val frozenLsTop = remember { lsStatusBarHeight }
////                val frozenLsBottom = remember { lsNavBarHeight }
//                Row(
//                    modifier = Modifier
//                        .fillMaxSize()
////                        .padding(
////                            top = frozenLsTop/*, bottom = frozenLsBottom*/)
//                        .scale(acceptScale)
//                        .alpha(acceptAlpha)
//                ) {
//                    // Left panel: avatar + caller info
//                    Column(
//                        modifier = Modifier
//                            .weight(1f)
//                            .fillMaxHeight()
//                            .padding(24.dp),
//                        horizontalAlignment = Alignment.CenterHorizontally,
//                        verticalArrangement = Arrangement.Center
//                    ) {
//                        Text(
//                            text = when {
//                                isOnHold -> "On Hold"
//                                callState == Call.STATE_ACTIVE -> formatDuration(callDuration)
//                                callState == Call.STATE_DIALING -> "Calling"
//                                callState == Call.STATE_RINGING -> "Incoming"
//                                callState == Call.STATE_CONNECTING -> "Calling"
//                                callState == Call.STATE_DISCONNECTING || isDisconnecting -> "Hanging up..."
//                                else -> "Connecting..."
//                            },
//                            color = if (isOnHold) Color(0xFFFFB74D) else subtleColor,
//                            style = MaterialTheme.typography.titleMedium,
//                            modifier = Modifier.padding(top = 6.dp)
//                        )
//                        if (hasHeldCall) {
//                            Spacer(modifier = Modifier.height(10.dp))
//                            Surface(shape = RoundedCornerShape(20.dp), color = color_call_button.copy(alpha = 0.15f)) {
//                                Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
//                                    Icon(Icons.AutoMirrored.Filled.CallMerge, null, tint = color_call_button, modifier = Modifier.size(14.dp))
//                                    Text(text = if (heldCallName.isBlank()) "1 call on hold" else "$heldCallName on hold", style = MaterialTheme.typography.labelSmall, color = color_call_button)
//                                }
//                            }
//                        }
//                        Spacer(modifier = Modifier.height(12.dp))
//                        Box(modifier = Modifier
//                            .fillMaxWidth()
//                            .height(44.dp), contentAlignment = Alignment.Center) {
//                            Text(
//                                text = contactName.ifEmpty { "" },
//                                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Medium),
//                                color = onBgColor.copy(alpha = if (contactName.isEmpty()) 0f else 1f),
//                                maxLines = 2,
//                                overflow = TextOverflow.Ellipsis,
//                                textAlign = TextAlign.Center
//                            )
//                        }
//                        if (contactName != phoneNumber && phoneNumber.isNotEmpty()) {
//                            Spacer(modifier = Modifier.height(12.dp))
//                            Text(
//                                text = phoneNumber,
//                                color = subtleColor,
//                                style = MaterialTheme.typography.titleMedium,
//                            )
//                        }
//                        if (!photoUri.isNullOrEmpty()) {
//                            Spacer(modifier = Modifier.height(20.dp))
//                            Box(
//                                modifier = Modifier
//                                    .size(140.dp)
//                                    .clip(CircleShape)
//                                    .background(controlBtnColor)
//                            ) {
//                                Icon(
//                                    Icons.Rounded.Person, null, modifier = Modifier
//                                        .align(Alignment.Center)
//                                        .size(48.dp), tint = subtleColor
//                                )
//                                if (photoUri.isNotEmpty()) {
//                                    AsyncImage(
//                                        model = ImageRequest.Builder(context).data(photoUri)
//                                            .crossfade(300).build(),
//                                        contentDescription = null,
//                                        modifier = Modifier.fillMaxSize(),
//                                        contentScale = ContentScale.Crop
//                                    )
//                                }
//                            }
//                        }
//                    }
//
//                    // Right panel: controls
//                    if (effectiveCallState != Call.STATE_RINGING) {
//                        Surface(
//                            modifier = Modifier
//                                .weight(1f)
//                                .fillMaxHeight(),
//                            color = overlayColor
//                        ) {
//                            Column(
//                                modifier = Modifier
//                                    .fillMaxSize()
//                                    .verticalScroll(rememberScrollState())
//                                    .padding(
//                                        start = 16.dp,
//                                        end = 16.dp,
//                                        top = 24.dp,
//                                        bottom = 12.dp
//                                    ),
//                                horizontalAlignment = Alignment.CenterHorizontally,
//                                verticalArrangement = Arrangement.Center
//                            ) {
//                                // Top row: Dialpad, Mute, Speaker
//                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
//                                    AnimatedCallButton(icon = Icons.Default.Dialpad, label = "Keypad", isActive = showDialpad, btnColor = controlBtnColor, activeBtnColor = controlBtnActiveColor, fgColor = controlBtnFg, activeFgColor = controlBtnActiveFg) { showDialpad = !showDialpad }
//                                    AnimatedCallButton(icon = if (isMuted) Icons.Rounded.MicOff else Icons.Rounded.Mic, label = "Mute", isActive = isMuted, btnColor = controlBtnColor, activeBtnColor = controlBtnActiveColor, fgColor = controlBtnFg, activeFgColor = controlBtnActiveFg) { CallService.setMuted(!isMuted) }
//                                    AnimatedCallButton(icon = if (isSpeakerOn) Icons.AutoMirrored.Rounded.VolumeUp else Icons.AutoMirrored.Rounded.VolumeDown, label = "Speaker", isActive = isSpeakerOn, btnColor = controlBtnColor, activeBtnColor = controlBtnActiveColor, fgColor = controlBtnFg, activeFgColor = controlBtnActiveFg) {
//                                        CallService.setAudioRoute(if (isSpeakerOn) CallAudioState.ROUTE_EARPIECE else CallAudioState.ROUTE_SPEAKER)
//                                    }
//                                    if (isBluetoothConnected) {
//                                        AnimatedCallButton(
//                                            icon = if (isBluetoothActive) Icons.Rounded.Bluetooth else Icons.Rounded.BluetoothDisabled,
//                                            label = "Bluetooth",
//                                            isActive = isBluetoothActive,
//                                            btnColor = controlBtnColor,
//                                            activeBtnColor = controlBtnActiveColor,
//                                            fgColor = controlBtnFg,
//                                            activeFgColor = controlBtnActiveFg
//                                        ) {
//                                            if (isBluetoothActive) CallService.setAudioRoute(CallAudioState.ROUTE_EARPIECE)
//                                            else CallService.setAudioRoute(CallAudioState.ROUTE_BLUETOOTH)
//                                        }
//                                    }
//                                }
//                                Spacer(modifier = Modifier.height(16.dp))
//                                // Bottom row: Hold, Add Person, Note
//                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
//                                    AnimatedCallButton(icon = if (isOnHold) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, label = "Hold", isActive = isOnHold, btnColor = controlBtnColor, activeBtnColor = controlBtnActiveColor, fgColor = controlBtnFg, activeFgColor = controlBtnActiveFg) {
//                                        isOnHold = !isOnHold
//                                        if (isOnHold) call.hold() else call.unhold()
//                                    }
//                                    if (canShowMerge) {
//                                        AnimatedCallButton(icon = Icons.AutoMirrored.Filled.CallMerge, label = "Merge", isActive = true, btnColor = controlBtnColor, activeBtnColor = color_call_button, fgColor = controlBtnFg, activeFgColor = Color.White, onClick = { showMergeConfirm = true })
//                                    } else {
//                                        AnimatedCallButton(icon = Icons.Rounded.PersonAdd, label = "Add", isActive = false, btnColor = controlBtnColor, activeBtnColor = controlBtnActiveColor, fgColor = controlBtnFg, activeFgColor = controlBtnActiveFg, onClick = { showAddPersonSheet = true })
//                                    }
//                                    AnimatedCallButton(icon = Icons.AutoMirrored.Outlined.StickyNote2, label = stringResource(R.string.call_notes), isActive = showNoteWindow, btnColor = controlBtnColor, activeBtnColor = controlBtnActiveColor, fgColor = controlBtnFg, activeFgColor = controlBtnActiveFg) { showNoteWindow = !showNoteWindow }
//                                }
//                                AnimatedVisibility(visible = showNoteWindow, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
//                                    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f), modifier = Modifier
//                                        .fillMaxWidth()
//                                        .padding(top = 16.dp)) {
//                                        Column(modifier = Modifier.padding(16.dp)) {
//                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
//                                                Text("Note — $contactName", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
//                                                IconButton(onClick = { if (phoneNumber.isNotEmpty()) NoteManager.writeNote(context, contactName, phoneNumber, noteText); showNoteWindow = false }, modifier = Modifier.size(32.dp)) {
//                                                    Icon(Icons.Default.Check, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
//                                                }
//                                            }
//                                            Spacer(Modifier.height(8.dp))
//                                            OutlinedTextField(value = noteText, onValueChange = { noteText = it }, modifier = Modifier
//                                                .fillMaxWidth()
//                                                .heightIn(min = 80.dp, max = 140.dp), placeholder = { Text("Type your note...") }, shape = RoundedCornerShape(12.dp), minLines = 3, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant))
//                                        }
//                                    }
//                                }
//                                Spacer(modifier = Modifier.height(20.dp))
//                                val endInteraction2 = remember { MutableInteractionSource() }
//                                val endPressed2 by endInteraction2.collectIsPressedAsState()
//                                val endRadius2 by animateDpAsState(if (endPressed2) 20.dp else 42.dp, spring(stiffness = Spring.StiffnessMedium), label = "endRadius2")
//                                val isCircleHangup = hangupWidthFraction <= 0.1f
//                                Surface(
//                                    onClick = { if (noteText.isNotBlank() && phoneNumber.isNotEmpty()) NoteManager.writeNote(context, contactName, phoneNumber, noteText); try { call.disconnect() } catch (_: Exception) {} },
//                                    modifier = if (isCircleHangup) Modifier
//                                        .size(76.dp)
//                                        //.scale(if (endPressed2) 0.96f else 1f),
//                                    else Modifier
//                                        .fillMaxWidth(hangupWidthFraction.coerceIn(0.1f, 1.0f))
//                                        .height(68.dp),
//                                        //.scale(if (endPressed2) 0.96f else 1f),
//                                    shape = RoundedCornerShape(endRadius2), color = color_call_end, interactionSource = endInteraction2
//                                ) {
//                                    Box(contentAlignment = Alignment.Center) {
//                                        Row(
//                                            verticalAlignment = Alignment.CenterVertically,
//                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
//                                        ) {
//                                            val showText = hangupWidthFraction > 0.5f
//                                            Icon(
//                                                Icons.Rounded.CallEnd,
//                                                null,
//                                                tint = Color.White,
//                                                modifier = Modifier.size(if (showText) 26.dp else 32.dp)
//                                            )
//                                            if (showText) {
//                                                Text(
//                                                    "End Call",
//                                                    color = Color.White,
//                                                    style = MaterialTheme.typography.labelLarge,
//                                                    fontWeight = FontWeight.SemiBold
//                                                )
//                                            }
//                                        }
//                                    }
//                                }
//                            }
//                        }
//                    } else {
//                        Column(
//                            modifier = Modifier.weight(1f).fillMaxHeight(),
//                            horizontalAlignment = Alignment.CenterHorizontally,
//                            verticalArrangement = Arrangement.Center
//                        ) {
//                            NewSwipeToAnswer(
//                                onAnswer = {
//                                    if (!isPocketBlocked()) {
//                                        if (callBiometricUnlocked) {
//                                            try { call.answer(VideoProfile.STATE_AUDIO_ONLY) } catch (_: Exception) {}
//                                        } else {
//                                            pendingAction = { try { call.answer(VideoProfile.STATE_AUDIO_ONLY) } catch (_: Exception) {} }
//                                            showCallBiometricUnlock = true
//                                        }
//                                    }
//                                },
//                                onDecline = {
//                                    if (!isPocketBlocked()) {
//                                        if (callBiometricUnlocked) {
//                                            try { call.disconnect() } catch (_: Exception) {}
//                                        } else {
//                                            pendingAction = { try { call.disconnect() } catch (_: Exception) {} }
//                                            showCallBiometricUnlock = true
//                                        }
//                                    }
//                                },
//                                labelColor = subtleColor,
//                                bgColor = overlayColor,
//                                isPocketBlocked = isPocketBlocked
//                            )
//                        }
//                    }
//                }
//            } else {
//                // ── PORTRAIT: original layout ────────────────────────────────
//                // Snapshot inset sizes once — never reread them — so window-flag
//                // changes during the call (lock-screen → active) can't shift the layout.
//                val statusBarHeight = with(LocalDensity.current) {
//                    WindowInsets.statusBars.getTop(this).toDp()
//                }
////                val navBarHeight = with(LocalDensity.current) {
////                    WindowInsets.navigationBars.getBottom(this).toDp()
////                }
//                val frozenStatusBarHeight = remember { statusBarHeight }
////                val frozenNavBarHeight = remember { navBarHeight }
//                Box(
//                    modifier = Modifier
//                        .fillMaxSize()
//                        .padding(top = frozenStatusBarHeight/*, bottom = frozenNavBarHeight*/)
//                        .scale(acceptScale)
//                        .alpha(acceptAlpha)
//                ) {
//                    // ── Top: caller info — absolutely top-anchored, never affected by bottom content ──
//                    Column(
//                        modifier = Modifier
//                            .fillMaxWidth()
//                            .wrapContentHeight()
//                            .align(Alignment.TopCenter)
//                            .padding(top = 130.dp),
//                        horizontalAlignment = Alignment.CenterHorizontally,
//                        verticalArrangement = Arrangement.Top
//                    ) {
//                        Text(
//                            text = when {
//                                isOnHold -> "On Hold"
//                                callState == Call.STATE_ACTIVE -> formatDuration(callDuration)
//                                callState == Call.STATE_DIALING -> "Calling"
//                                callState == Call.STATE_RINGING -> "Incoming"
//                                callState == Call.STATE_CONNECTING -> "Calling"
//                                callState == Call.STATE_DISCONNECTING || isDisconnecting -> "Hanging up..."
//                                else -> "Connecting..."
//                            },
//                            color = if (isOnHold) Color(0xFFFFB74D) else subtleColor,
//                            style = MaterialTheme.typography.titleMedium,
//                            modifier = Modifier.padding(top = 8.dp)
//                        )
//
//                        if (hasHeldCall) {
//                            Spacer(modifier = Modifier.height(12.dp))
//                            Surface(
//                                shape = RoundedCornerShape(20.dp),
//                                color = color_call_button.copy(alpha = 0.15f),
//                                modifier = Modifier.padding(horizontal = 32.dp)
//                            ) {
//                                Row(
//                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
//                                    verticalAlignment = Alignment.CenterVertically,
//                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
//                                ) {
//                                    Icon(Icons.AutoMirrored.Filled.CallMerge, null, tint = color_call_button, modifier = Modifier.size(16.dp))
//                                    Text(
//                                        text = if (heldCallName.isBlank()) "1 call on hold" else "$heldCallName on hold",
//                                        style = MaterialTheme.typography.labelMedium,
//                                        color = color_call_button
//                                    )
//                                }
//                            }
//                        }
//                        Spacer(modifier = Modifier.height(12.dp))
//                        // Fixed height box so layout never shifts when name loads
//                        Box(modifier = Modifier
//                            .fillMaxWidth()
//                            .height(52.dp), contentAlignment = Alignment.Center) {
//                            Text(
//                                text = contactName.ifEmpty { "" },
//                                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Medium),
//                                color = onBgColor.copy(alpha = if (contactName.isEmpty()) 0f else 1f),
//                                maxLines = 1,
//                                overflow = TextOverflow.Ellipsis
//                            )
//                        }
//                        if (contactName != phoneNumber && phoneNumber.isNotEmpty()) {
//                            Spacer(modifier = Modifier.height(12.dp))
//                            Text(
//                                text = phoneNumber,
//                                color = subtleColor,
//                                style = MaterialTheme.typography.titleMedium,
//                            )
//                        }
//
//                        if (!photoUri.isNullOrEmpty()) {
//                            Spacer(modifier = Modifier.height(38.dp))
//                            Box(modifier = Modifier
//                                .size(160.dp)
//                                .clip(CircleShape)
//                                .background(controlBtnColor)) {
//                                // Always render Icon as base layer so layout never shifts
//                                Icon(
//                                    Icons.Rounded.Person, null, modifier = Modifier
//                                        .align(Alignment.Center)
//                                        .size(56.dp), tint = subtleColor
//                                )
//                                if (photoUri.isNotEmpty()) {
//                                    AsyncImage(
//                                        model = ImageRequest.Builder(context)
//                                            .data(photoUri)
//                                            .crossfade(300)
//                                            .build(),
//                                        contentDescription = null,
//                                        modifier = Modifier.fillMaxSize(),
//                                        contentScale = ContentScale.Crop
//                                    )
//                                }
//                            }
//                        }
//                    }
//
//                    // ── Bottom: controls — anchored to bottom ─────────────────
//                    if (effectiveCallState != Call.STATE_RINGING) {
//                        Surface(
//                            modifier = Modifier
//                                .fillMaxWidth()
//                                .align(Alignment.BottomCenter)
//                                .clip(RoundedCornerShape(topStart = 48.dp, topEnd = 48.dp)),
//                            color = bottomBarColor//overlayColor
//                        ) {
//                            Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
//
//                                // ── Dialpad overlay
//                                AnimatedVisibility(visible = showDialpad, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
//                                    Column(modifier = Modifier.fillMaxWidth()) {
////                                        Row(
////                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
////                                            verticalAlignment = Alignment.CenterVertically,
////                                            horizontalArrangement = Arrangement.SpaceBetween
////                                        ) {
////                                            Text("Dialpad", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
////                                            IconButton(onClick = { showDialpad = false }) { Icon(Icons.Rounded.Cancel, null) }
////                                        }
////                                        if (dtmfInput.isNotEmpty()) {
//                                            Text(
//                                                text = dtmfInput,
//                                                style = MaterialTheme.typography.headlineLarge,
//                                                fontWeight = FontWeight.SemiBold,
//                                                color = MaterialTheme.colorScheme.onSurface,
//                                                modifier = Modifier
//                                                    .fillMaxWidth()
//                                                    .padding(horizontal = 24.dp),
//                                                textAlign = TextAlign.Center,
//                                                maxLines = 1,
//                                                overflow = TextOverflow.StartEllipsis
//                                            )
////                                        }
//                                        Spacer(modifier = Modifier.height(16.dp))
//                                        InCallDialPad(
//                                            onDigit = { digit ->
//                                                dtmfInput += digit
//                                                try { call.playDtmfTone(digit[0]); call.stopDtmfTone() } catch (_: Exception) {}
//                                            },
//                                            onBackspace = { if (dtmfInput.isNotEmpty()) dtmfInput = dtmfInput.dropLast(1) }
//                                        )
//                                    }
//                                }
//
//                                Spacer(modifier = Modifier.height(20.dp))
//
//                                // Top row: Dialpad, Mute, Speaker
//                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
//                                    AnimatedCallButton(
//                                        icon = Icons.Default.Dialpad,
//                                        label = "Keypad",
//                                        isActive = showDialpad,
//                                        btnColor = controlBtnColor,
//                                        activeBtnColor = controlBtnActiveColor,
//                                        fgColor = controlBtnFg,
//                                        activeFgColor = controlBtnActiveFg
//                                    ) { showDialpad = !showDialpad }
//                                    AnimatedCallButton(icon = if (isMuted) Icons.Rounded.MicOff else Icons.Rounded.Mic, label = "Mute", isActive = isMuted, btnColor = controlBtnColor, activeBtnColor = controlBtnActiveColor, fgColor = controlBtnFg, activeFgColor = controlBtnActiveFg) { CallService.setMuted(!isMuted) }
//                                    AnimatedCallButton(icon = if (isSpeakerOn) Icons.AutoMirrored.Rounded.VolumeUp else Icons.AutoMirrored.Rounded.VolumeDown, label = "Speaker", isActive = isSpeakerOn, btnColor = controlBtnColor, activeBtnColor = controlBtnActiveColor, fgColor = controlBtnFg, activeFgColor = controlBtnActiveFg) {
//                                        CallService.setAudioRoute(if (isSpeakerOn) CallAudioState.ROUTE_EARPIECE else CallAudioState.ROUTE_SPEAKER)
//                                    }
//                                }
//
//                                Spacer(modifier = Modifier.height(20.dp))
//
//                                // Bottom row: Hold, Add Person, Note
//                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
//                                    AnimatedCallButton(icon = if (isOnHold) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, label = "Hold", isActive = isOnHold, btnColor = controlBtnColor, activeBtnColor = controlBtnActiveColor, fgColor = controlBtnFg, activeFgColor = controlBtnActiveFg) {
//                                        isOnHold = !isOnHold
//                                        if (isOnHold) call.hold() else call.unhold()
//                                    }
//                                    if (canShowMerge) {
//                                        AnimatedCallButton(
//                                            icon = Icons.AutoMirrored.Filled.CallMerge,
//                                            label = "Merge",
//                                            isActive = true,
//                                            btnColor = controlBtnColor,
//                                            activeBtnColor = color_call_button,
//                                            fgColor = controlBtnFg,
//                                            activeFgColor = Color.White,
//                                            onClick = { showMergeConfirm = true }
//                                        )
//                                    } else {
//                                        AnimatedCallButton(
//                                            icon = Icons.Rounded.PersonAdd,
//                                            label = "Add Person",
//                                            isActive = false,
//                                            btnColor = controlBtnColor,
//                                            activeBtnColor = controlBtnActiveColor,
//                                            fgColor = controlBtnFg,
//                                            activeFgColor = controlBtnActiveFg,
//                                            onClick = { showAddPersonSheet = true }
//                                        )
//                                    }
//                                    AnimatedCallButton(
//                                        icon = Icons.AutoMirrored.Outlined.StickyNote2,
//                                        label = stringResource(R.string.call_notes),
//                                        isActive = showNoteWindow,
//                                        btnColor = controlBtnColor,
//                                        activeBtnColor = controlBtnActiveColor,
//                                        fgColor = controlBtnFg,
//                                        activeFgColor = controlBtnActiveFg
//                                    ) { showNoteWindow = !showNoteWindow }
//                                }
//
//                                // Bluetooth row — only visible when a BT device is connected
//                                AnimatedVisibility(
//                                    visible = isBluetoothConnected,
//                                    enter = fadeIn() + expandVertically(),
//                                    exit = fadeOut() + shrinkVertically()
//                                ) {
//                                    Row(
//                                        modifier = Modifier
//                                            .fillMaxWidth()
//                                            .padding(top = 8.dp),
//                                        horizontalArrangement = Arrangement.Center
//                                    ) {
//                                        AnimatedCallButton(
//                                            icon = if (isBluetoothActive) Icons.Rounded.Bluetooth else Icons.Rounded.BluetoothDisabled,
//                                            label = "Bluetooth",
//                                            isActive = isBluetoothActive,
//                                            btnColor = controlBtnColor,
//                                            activeBtnColor = controlBtnActiveColor,
//                                            fgColor = controlBtnFg,
//                                            activeFgColor = controlBtnActiveFg
//                                        ) {
//                                            if (isBluetoothActive) {
//                                                CallService.setAudioRoute(CallAudioState.ROUTE_EARPIECE)
//                                            } else {
//                                                CallService.setAudioRoute(CallAudioState.ROUTE_BLUETOOTH)
//                                            }
//                                        }
//                                    }
//                                }
//
//                                AnimatedVisibility(visible = showNoteWindow, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
//                                    Column(modifier = Modifier
//                                        .fillMaxWidth()
//                                        .padding(top = 20.dp)) {
//                                        Surface(
//                                            shape = RoundedCornerShape(20.dp),
//                                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
//                                            modifier = Modifier.fillMaxWidth()
//                                        ) {
//                                            Column(modifier = Modifier.padding(16.dp)) {
//                                                Row(
//                                                    modifier = Modifier.fillMaxWidth(),
//                                                    horizontalArrangement = Arrangement.SpaceBetween,
//                                                    verticalAlignment = Alignment.CenterVertically
//                                                ) {
//                                                    Text(
//                                                        "Note — $contactName",
//                                                        style = MaterialTheme.typography.labelMedium,
//                                                        fontWeight = FontWeight.Bold,
//                                                        color = MaterialTheme.colorScheme.onSurface
//                                                    )
//                                                    Row {
//                                                        IconButton(onClick = {
//                                                            if (phoneNumber.isNotEmpty()) {
//                                                                NoteManager.writeNote(context, contactName, phoneNumber, noteText)
//                                                            }
//                                                            showNoteWindow = false
//                                                        }, modifier = Modifier.size(32.dp)) {
//                                                            Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
//                                                        }
//                                                    }
//                                                }
//                                                Spacer(Modifier.height(8.dp))
//                                                OutlinedTextField(
//                                                    value = noteText,
//                                                    onValueChange = { noteText = it },
//                                                    modifier = Modifier
//                                                        .fillMaxWidth()
//                                                        .heightIn(min = 100.dp, max = 200.dp),
//                                                    placeholder = { Text("Type your note...") },
//                                                    shape = RoundedCornerShape(12.dp),
//                                                    minLines = 4,
//                                                    colors = OutlinedTextFieldDefaults.colors(
//                                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
//                                                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
//                                                    )
//                                                )
//                                                if (noteText.isNotBlank()) {
//                                                    Text(
//                                                        "Syncing...",
//                                                        style = MaterialTheme.typography.labelSmall,
//                                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
//                                                        modifier = Modifier.padding(top = 4.dp)
//                                                    )
//                                                }
//                                            }
//                                        }
//                                    }
//                                }
//
//                                Spacer(modifier = Modifier.height(28.dp))
//
//                                // ── Hangup Button with configurable width ──────────────
//                                val endInteraction = remember { MutableInteractionSource() }
//                                val endPressed by endInteraction.collectIsPressedAsState()
//                                val endRadius by animateDpAsState(if (endPressed) 20.dp else 42.dp, spring(stiffness = Spring.StiffnessMedium), label = "endRadius")
//
//                                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
//                                    val isCircleHangup = hangupWidthFraction <= 0.1f
//                                    Surface(
//                                        onClick = {
//                                            if (noteText.isNotBlank() && phoneNumber.isNotEmpty()) {
//                                                NoteManager.writeNote(context, contactName, phoneNumber, noteText)
//                                            }
//                                            try { call.disconnect() } catch (e: Exception) {}
//                                        },
//                                        modifier = if (isCircleHangup) Modifier
//                                            .size(76.dp)
////                                            .scale(if (endPressed) 1.04f else 1f)
//                                        else Modifier
//                                            .fillMaxWidth(hangupWidthFraction.coerceIn(0.1f, 1.0f))
//                                            .height(68.dp),
////                                            .scale(if (endPressed) 1.04f else 1f),
//                                        shape = if (isCircleHangup) CircleShape else RoundedCornerShape(endRadius),
//                                        color = color_call_end,
//                                        interactionSource = endInteraction
//                                    ) {
//                                        Box(contentAlignment = Alignment.Center) {
//                                            Row(
//                                                verticalAlignment = Alignment.CenterVertically,
//                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
//                                            ) {
//                                                val showText = hangupWidthFraction > 0.5f
//                                                Icon(
//                                                    Icons.Rounded.CallEnd,
//                                                    null,
//                                                    tint = Color.White,
//                                                    modifier = Modifier.size(if (showText) 26.dp else 32.dp)
//                                                )
//                                                if (showText) {
//                                                    Text(
//                                                        "End Call",
//                                                        color = Color.White,
//                                                        style = MaterialTheme.typography.labelLarge,
//                                                        fontWeight = FontWeight.SemiBold
//                                                    )
//                                                }
//                                            }
//                                        }
//                                    }
//                                }
//                            }
//                        }
//                    } else {
//                        // Ringing state — swipe to answer, also anchored to bottom
//                        Box(
//                            modifier = Modifier
//                                .fillMaxWidth()
//                                .align(Alignment.BottomCenter)
//                        ) {
//                            NewSwipeToAnswer(
//                                onAnswer = {
//                                    if (!isPocketBlocked()) {
//                                        if (callBiometricUnlocked) {
//                                            try { call.answer(VideoProfile.STATE_AUDIO_ONLY) } catch (_: Exception) {}
//                                        } else {
//                                            pendingAction = { try { call.answer(VideoProfile.STATE_AUDIO_ONLY) } catch (_: Exception) {} }
//                                            showCallBiometricUnlock = true
//                                        }
//                                    }
//                                },
//                                onDecline = {
//                                    if (!isPocketBlocked()) {
//                                        if (callBiometricUnlocked) {
//                                            try { call.disconnect() } catch (_: Exception) {}
//                                        } else {
//                                            pendingAction = { try { call.disconnect() } catch (_: Exception) {} }
//                                            showCallBiometricUnlock = true
//                                        }
//                                    }
//                                },
//                                labelColor = subtleColor,
//                                bgColor = overlayColor,
//                                isPocketBlocked = isPocketBlocked
//                            )
//                        }
//                    }
//                } // end portrait Box
//            } // end portrait
//        }
//
//        // ── Call biometric — direct prompt, no overlay ────────────────────
//        if (showCallBiometricUnlock && prefs != null) {
//            val biometricType = prefs.getString(PreferenceManager.KEY_BIOMETRICS_TYPE, "") ?: ""
//            val context = LocalContext.current
//            when (biometricType) {
//                "system" -> {
//                    LaunchedEffect(showCallBiometricUnlock) {
//                        val activity = context as? androidx.fragment.app.FragmentActivity ?: run {
//                            showCallBiometricUnlock = false; pendingAction = null; return@LaunchedEffect
//                        }
//                        val executor = androidx.core.content.ContextCompat.getMainExecutor(activity)
//                        val prompt = androidx.biometric.BiometricPrompt(
//                            activity, executor,
//                            object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
//                                override fun onAuthenticationSucceeded(result: androidx.biometric.BiometricPrompt.AuthenticationResult) {
//                                    callBiometricUnlocked = true
//                                    showCallBiometricUnlock = false
//                                    pendingAction?.invoke()
//                                    pendingAction = null
//                                }
//                                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
//                                    showCallBiometricUnlock = false
//                                    pendingAction = null
//                                }
//                                override fun onAuthenticationFailed() {
//                                    showCallBiometricUnlock = false
//                                    pendingAction = null
//                                }
//                            }
//                        )
//                        val info = androidx.biometric.BiometricPrompt.PromptInfo.Builder()
//                            .setTitle("Authenticate")
//                            .setSubtitle("Verify your identity to perform this action")
//                            .setNegativeButtonText("Cancel")
//                            .setAllowedAuthenticators(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK)
//                            .build()
//                        prompt.authenticate(info)
//                    }
//                }
//                "pin" -> {
//                    dev.goodwy.rphone.view.screen.settings.PinSetupDialog(
//                        title = "Enter PIN",
//                        isVerify = true,
//                        expectedPin = prefs.getString(PreferenceManager.KEY_BIOMETRICS_PIN, "") ?: "",
//                        onConfirm = {
//                            callBiometricUnlocked = true
//                            showCallBiometricUnlock = false
//                            pendingAction?.invoke()
//                            pendingAction = null
//                        },
//                        onDismiss = {
//                            showCallBiometricUnlock = false
//                            pendingAction = null
//                        }
//                    )
//                }
//                "password" -> {
//                    dev.goodwy.rphone.view.screen.settings.PasswordSetupDialog(
//                        title = "Enter Password",
//                        isVerify = true,
//                        expectedPassword = prefs.getString(PreferenceManager.KEY_BIOMETRICS_PASSWORD, "") ?: "",
//                        onConfirm = {
//                            callBiometricUnlocked = true
//                            showCallBiometricUnlock = false
//                            pendingAction?.invoke()
//                            pendingAction = null
//                        },
//                        onDismiss = {
//                            showCallBiometricUnlock = false
//                            pendingAction = null
//                        }
//                    )
//                }
//            }
//        }
//
//        // ── Dialpad overlay — last child of main Box, never triggers layout shift ──
//        if ((showDialpad || dialpadAlpha > 0f) && isLandscape) {
//            Box(
//                modifier = Modifier
//                    .fillMaxSize()
//                    .graphicsLayer { alpha = dialpadAlpha }
//                    .background(Color.Black.copy(alpha = 0.55f * dialpadAlpha))
//                    .padding(horizontal = 120.dp),
//                contentAlignment = Alignment.BottomCenter
//            ) {
//                Surface(
//                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
//                    color = MaterialTheme.colorScheme.surface,
//                    modifier = Modifier
//                        .fillMaxWidth()
//                        .graphicsLayer { translationY = dialpadOffsetY * size.height }
//                ) {
//                    Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
////                        Box(modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp), contentAlignment = Alignment.Center) {
////                            Surface(shape = RoundedCornerShape(3.dp), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f), modifier = Modifier.size(width = 36.dp, height = 4.dp)) {}
////                        }
//                        Row(
//                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, end = 8.dp),
//                            verticalAlignment = Alignment.CenterVertically,
//                            horizontalArrangement = Arrangement.End //SpaceBetween
//                        ) {
////                            Text("Dialpad", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
//                            IconButton(onClick = { showDialpad = false }) { Icon(Icons.Rounded.Cancel, null) }
//                        }
////                        if (dtmfInput.isNotEmpty()) {
//                            Text(
//                                text = dtmfInput,
//                                style = MaterialTheme.typography.headlineMedium,
//                                fontWeight = FontWeight.Light,
//                                color = MaterialTheme.colorScheme.onSurface,
//                                modifier = Modifier.fillMaxWidth().padding(horizontal = 36.dp),
//                                textAlign = TextAlign.Center,
//                                maxLines = 1,
//                                overflow = TextOverflow.StartEllipsis
//                            )
////                        }
//                        Spacer(modifier = Modifier.height(16.dp))
//                        InCallDialPad(
//                            onDigit = { digit ->
//                                dtmfInput += digit
//                                try { call.playDtmfTone(digit[0]); call.stopDtmfTone() } catch (_: Exception) {}
//                            },
//                            onBackspace = { if (dtmfInput.isNotEmpty()) dtmfInput = dtmfInput.dropLast(1) }
//                        )
//                    }
//                }
//            }
//        }
//    }
//}
//
//// ─── In-Call Dial Pad ──────────────────────────────────────────────────────────
//
//@Composable
//private fun InCallDialPad(
//    onDigit: (String) -> Unit,
//    onBackspace: () -> Unit
//) {
//    val keys = listOf(
//        listOf("1" to "", "2" to "ABC", "3" to "DEF"),
//        listOf("4" to "GHI", "5" to "JKL", "6" to "MNO"),
//        listOf("7" to "PQRS", "8" to "TUV", "9" to "WXYZ"),
//        listOf("*" to "", "0" to "+", "#" to "")
//    )
//
//    Column(
//        modifier = Modifier
//            .fillMaxWidth(),
////            .padding(horizontal = 8.dp, vertical = 8.dp),
//        horizontalAlignment = Alignment.CenterHorizontally,
//        verticalArrangement = Arrangement.spacedBy(10.dp)
//    ) {
//        keys.forEach { row ->
//            Row(
//                modifier = Modifier.fillMaxWidth(),
////                horizontalArrangement = Arrangement.spacedBy(14.dp)
//                horizontalArrangement = Arrangement.SpaceEvenly
//            ) {
//                row.forEach { (digit, letters) ->
//                    val interaction = remember { MutableInteractionSource() }
//                    val isPressed by interaction.collectIsPressedAsState()
//                    val keyRadius by animateDpAsState(if (isPressed) 14.dp else 26.dp, spring(stiffness = Spring.StiffnessMedium), label = "keyR")
//                    Surface(
//                        onClick = { onDigit(digit) },
//                        shape = RoundedCornerShape(keyRadius),
//                        color = dialpadKeyColor,
//                        modifier = Modifier
////                            .weight(1f)
//                            .width(108.dp)
//                            .height(52.dp), //.scale(if (isPressed) 0.92f else 1f),
//                        interactionSource = interaction
//                    ) {
//                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
//                            Text(digit, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
//                            if (letters.isNotEmpty()) {
//                                Text(letters, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp)
//                            }
//                        }
//                    }
//                }
//            }
//        }
//
//        // Backspace row
////        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.Center) {
////            Surface(
////                onClick = onBackspace,
////                shape = RoundedCornerShape(22.dp),
////                color = MaterialTheme.colorScheme.surfaceContainerLow,
////                modifier = Modifier.fillMaxWidth(0.5f).height(56.dp)
////            ) {
////                Box(contentAlignment = Alignment.Center) {
////                    Icon(Icons.AutoMirrored.Rounded.Backspace, null, modifier = Modifier.size(22.dp))
////                }
////            }
////        }
//
//        Spacer(Modifier.height(8.dp))
//    }
//}
//
//// ─── Add Person Bottom Sheet ───────────────────────────────────────────────────
//
//@OptIn(ExperimentalMaterial3Api::class)
//@Composable
//private fun AddPersonSheet(
//    context: android.content.Context,
//    contactsRepo: IContactsRepository?,
//    callLogRepo: ICallLogRepository?,
//    onDismiss: () -> Unit,
//    onPersonSelected: (String) -> Unit
//) {
//    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
//    var selectedTab by remember { mutableIntStateOf(0) }
//    var searchQuery by remember { mutableStateOf("") }
//
//    var contacts by remember { mutableStateOf<List<Contact>>(emptyList()) }
//    var callLogs by remember { mutableStateOf<List<CallLogEntry>>(emptyList()) }
//    var dialNumber by remember { mutableStateOf("") }
//
//    val scope = rememberCoroutineScope()
//
//    LaunchedEffect(Unit) {
//        withContext(Dispatchers.IO) {
//            contacts = contactsRepo?.getContacts() ?: emptyList()
//            callLogs = callLogRepo?.getCallLogs()?.distinctBy { it.number } ?: emptyList()
//        }
//    }
//
//    ModalBottomSheet(
//        onDismissRequest = onDismiss,
//        sheetState = sheetState,
//        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
//        containerColor = MaterialTheme.colorScheme.surface,
//        dragHandle = {
//            Box(modifier = Modifier
//                .fillMaxWidth()
//                .padding(top = 12.dp, bottom = 4.dp), contentAlignment = Alignment.Center) {
//                Surface(shape = RoundedCornerShape(3.dp), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f), modifier = Modifier.size(width = 36.dp, height = 4.dp)) {}
//            }
//        }
//    ) {
//        Column(modifier = Modifier
//            .fillMaxWidth()
//            .navigationBarsPadding()) {
//            Row(
//                modifier = Modifier
//                    .fillMaxWidth()
//                    .padding(horizontal = 20.dp, vertical = 8.dp),
//                verticalAlignment = Alignment.CenterVertically,
//                horizontalArrangement = Arrangement.SpaceBetween
//            ) {
//                Text("Add Person", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
//                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Cancel, null) }
//            }
//
//            if (selectedTab != 2) {
//                OutlinedTextField(
//                    value = searchQuery,
//                    onValueChange = { searchQuery = it },
//                    modifier = Modifier
//                        .fillMaxWidth()
//                        .padding(horizontal = 16.dp, vertical = 4.dp),
//                    placeholder = { Text(if (selectedTab == 0) "Search call logs..." else "Search contacts...") },
//                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
//                    shape = RoundedCornerShape(16.dp),
//                    singleLine = true,
//                    colors = OutlinedTextFieldDefaults.colors(
//                        focusedBorderColor = MaterialTheme.colorScheme.primary,
//                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
//                    )
//                )
//            }
//
//            Row(
//                modifier = Modifier
//                    .fillMaxWidth()
//                    .padding(horizontal = 16.dp, vertical = 10.dp),
//                horizontalArrangement = Arrangement.spacedBy(8.dp)
//            ) {
//                val tabs = listOf("Call Logs" to Icons.Rounded.AccessTime, "Contacts" to Icons.Filled.AccountCircle, "Dial Pad" to Icons.Default.Dialpad)
//                tabs.forEachIndexed { index, (label, icon) ->
//                    val selected = selectedTab == index
//                    val tabColor by animateColorAsState(
//                        if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
//                        spring(stiffness = Spring.StiffnessMediumLow), label = "tabColor"
//                    )
//                    Surface(
//                        onClick = { selectedTab = index; searchQuery = "" },
//                        shape = RoundedCornerShape(50.dp),
//                        color = tabColor,
//                        modifier = Modifier.weight(1f)
//                    ) {
//                        Row(
//                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
//                            horizontalArrangement = Arrangement.Center,
//                            verticalAlignment = Alignment.CenterVertically
//                        ) {
//                            Icon(icon, null, modifier = Modifier.size(16.dp),
//                                tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
//                            Spacer(Modifier.width(4.dp))
//                            Text(label, style = MaterialTheme.typography.labelMedium, fontSize = 11.sp,
//                                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
//                                maxLines = 1, overflow = TextOverflow.Ellipsis)
//                        }
//                    }
//                }
//            }
//
//            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
//
//            Box(modifier = Modifier
//                .fillMaxWidth()
//                .heightIn(min = 200.dp, max = 420.dp)) {
//                when (selectedTab) {
//                    0 -> {
//                        val filtered = remember(callLogs, searchQuery) {
//                            if (searchQuery.isBlank()) callLogs.take(50)
//                            else callLogs.filter {
//                                val name = it.name ?: ""
//                                name.contains(searchQuery, ignoreCase = true) || it.number.contains(searchQuery)
//                            }.take(50)
//                        }
//                        LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
//                            items(filtered, key = { it.number }) { log ->
//                                AddPersonRow(
//                                    name = log.name?.takeIf { it != log.number } ?: log.number,
//                                    subtitle = if (log.name != null && log.name != log.number) log.number else null,
//                                    photoUri = log.photoUri,
//                                    onClick = { onPersonSelected(log.number) }
//                                )
//                            }
//                        }
//                    }
//                    1 -> {
//                        val filtered = remember(contacts, searchQuery) {
//                            if (searchQuery.isBlank()) contacts.take(100)
//                            else contacts.filter { it.name.contains(searchQuery, ignoreCase = true) || it.phoneNumbers.any { n -> n.contains(searchQuery) } }.take(100)
//                        }
//                        LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
//                            items(filtered, key = { it.id }) { contact ->
//                                AddPersonRow(
//                                    name = contact.name,
//                                    subtitle = contact.phoneNumbers.firstOrNull(),
//                                    photoUri = contact.photoUri,
//                                    onClick = { contact.phoneNumbers.firstOrNull()?.let { onPersonSelected(it) } }
//                                )
//                            }
//                        }
//                    }
//                    2 -> {
//                        CompactDialPad(
//                            number = dialNumber,
//                            onNumberChange = { dialNumber = it },
//                            onCall = { if (dialNumber.isNotEmpty()) onPersonSelected(dialNumber) }
//                        )
//                    }
//                }
//            }
//        }
//    }
//}
//
//@Composable
//private fun AddPersonRow(
//    name: String,
//    subtitle: String?,
//    photoUri: String?,
//    onClick: () -> Unit
//) {
//    var isPressed by remember { mutableStateOf(false) }
//    val scale by animateFloatAsState(if (isPressed) 0.96f else 1f, spring(stiffness = Spring.StiffnessMedium), label = "rowScale")
//
//    Surface(
//        onClick = { isPressed = false; onClick() },
//        color = Color.Transparent,
//        modifier = Modifier
//            .fillMaxWidth()
//            .scale(scale)
//    ) {
//        Row(
//            modifier = Modifier
//                .fillMaxWidth()
//                .padding(horizontal = 16.dp, vertical = 10.dp),
//            verticalAlignment = Alignment.CenterVertically
//        ) {
//            RillAvatar(name = name, photoUri = photoUri, modifier = Modifier.size(44.dp))
//            Spacer(Modifier.width(14.dp))
//            Column(modifier = Modifier.weight(1f)) {
//                Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
//                if (subtitle != null) {
//                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
//                }
//            }
//            Icon(Icons.Rounded.Call, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
//        }
//    }
//}
//
//@Composable
//private fun CompactDialPad(
//    number: String,
//    onNumberChange: (String) -> Unit,
//    onCall: () -> Unit
//) {
//    val keys = listOf(
//        listOf("1" to "", "2" to "ABC", "3" to "DEF"),
//        listOf("4" to "GHI", "5" to "JKL", "6" to "MNO"),
//        listOf("7" to "PQRS", "8" to "TUV", "9" to "WXYZ"),
//        listOf("*" to "", "0" to "+", "#" to "")
//    )
//
//    Column(
//        modifier = Modifier
//            .fillMaxWidth()
//            .padding(horizontal = 24.dp, vertical = 8.dp),
//        horizontalAlignment = Alignment.CenterHorizontally,
//        verticalArrangement = Arrangement.spacedBy(8.dp)
//    ) {
//        Text(
//            text = number.ifEmpty { "Enter number" },
//            style = MaterialTheme.typography.headlineMedium,
//            fontWeight = FontWeight.Light,
//            color = if (number.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f) else MaterialTheme.colorScheme.onSurface,
//            modifier = Modifier.padding(bottom = 4.dp)
//        )
//
//        keys.forEach { row ->
//            Row(
//                modifier = Modifier.fillMaxWidth(),
//                horizontalArrangement = Arrangement.spacedBy(12.dp)
//            ) {
//                row.forEach { (digit, letters) ->
//                    val interaction = remember { MutableInteractionSource() }
//                    val isPressed by interaction.collectIsPressedAsState()
//                    val keyRadius by animateDpAsState(if (isPressed) 14.dp else 22.dp, spring(stiffness = Spring.StiffnessMedium), label = "keyR")
//                    Surface(
////                        onClick = { onNumberChange(number + digit) },
//                        shape = RoundedCornerShape(keyRadius),
//                        color = MaterialTheme.colorScheme.surfaceContainerLow,
//                        modifier = Modifier
//                            .weight(1f)
//                            .height(52.dp), //.scale(if (isPressed) 0.92f else 1f),
////                        interactionSource = interaction
//                    ) {
//                        Column(
//                            horizontalAlignment = Alignment.CenterHorizontally,
//                            verticalArrangement = Arrangement.Center,
//                            modifier = Modifier
//                                .fillMaxSize()
//                                .combinedClickable(
//                                    onClick = { onNumberChange(number + digit) },
//                                    onLongClick = {
//                                        val newDigit = when (digit) {
//                                            "0" -> "+"
//                                            "*" -> ","
//                                            "#" -> ";"
//                                            else -> digit
//                                        }
//                                        onNumberChange(number + newDigit)
//                                    },
//                                    interactionSource = interaction,
//                                    indication = ripple()
//                                ),
//                        ) {
//                            Text(digit, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
//                            if (letters.isNotEmpty()) {
//                                Text(letters, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp)
//                            }
//                        }
//                    }
//                }
//            }
//        }
//
//        Row(
//            modifier = Modifier
//                .fillMaxWidth(),
////                .padding(top = 4.dp),
//            horizontalArrangement = Arrangement.spacedBy(12.dp)
//        ) {
//            val context = LocalContext.current
//            Surface(
//                onClick = {
//                    if (number.isNotEmpty()) context.copyToClipboard(number)
//                    else onNumberChange(context.getTextFromClipboard().toString())
//                },
//                shape = RoundedCornerShape(22.dp),
//                color = MaterialTheme.colorScheme.surfaceContainerLow,
//                modifier = Modifier
//                    .weight(1f)
//                    .height(52.dp)
//            ) {
//                Box(contentAlignment = Alignment.Center) {
//                    Icon(
//                        if (number.isNotEmpty()) Icons.Rounded.ContentCopy else Icons.Rounded.ContentPaste,
//                        if (number.isNotEmpty()) "Copy" else "Paste",
//                        modifier = Modifier.size(22.dp))
//                }
//            }
//            Surface(
//                onClick = onCall,
//                shape = RoundedCornerShape(22.dp),
//                color = if (number.isNotEmpty()) color_call_button else MaterialTheme.colorScheme.surfaceContainerLow,
//                modifier = Modifier
//                    .weight(1f)
//                    .height(52.dp)
//            ) {
//                Box(contentAlignment = Alignment.Center) {
//                    Icon(Icons.Rounded.Call, null, tint = if (number.isNotEmpty()) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
//                }
//            }
//            Surface(
////                onClick = { if (number.isNotEmpty()) onNumberChange(number.dropLast(1)) },
//                shape = RoundedCornerShape(22.dp),
//                color = MaterialTheme.colorScheme.surfaceContainerLow,
//                modifier = Modifier
//                    .weight(1f)
//                    .height(52.dp)
//            ) {
//                Box(
//                    contentAlignment = Alignment.Center,
//                    modifier = Modifier
//                        .fillMaxSize()
//                        .combinedClickable(
//                            onClick = { if (number.isNotEmpty()) onNumberChange(number.dropLast(1)) },
//                            onLongClick = { onNumberChange("") },
//                            interactionSource = null,
//                            indication = ripple()
//                        ),
//                ) {
//                    Icon(Icons.AutoMirrored.Rounded.Backspace, null, modifier = Modifier.size(22.dp))
//                }
//            }
//        }
//    }
//}
//
//// ─── Animated Call Button ───────────────────────────────────────────────────────
//
//@Composable
//fun AnimatedCallButton(
//    icon: ImageVector,
//    label: String,
//    isActive: Boolean = false,
//    btnColor: Color = Color.White.copy(0.12f),
//    activeBtnColor: Color = Color.White,
//    fgColor: Color = Color.White,
//    activeFgColor: Color = Color.Black,
//    onClick: () -> Unit
//) {
//    val interaction = remember { MutableInteractionSource() }
//    val isPressed by interaction.collectIsPressedAsState()
//    val radius by animateDpAsState(if (isActive || isPressed) 20.dp else 42.dp, spring(stiffness = Spring.StiffnessMedium), label = "btnRadius")
//    Column(horizontalAlignment = Alignment.CenterHorizontally) {
//        Surface(onClick = onClick,
//            modifier = Modifier.size(height = 68.dp, width = 108.dp),//.scale(if (isPressed) 0.9f else 1f),
//            shape = RoundedCornerShape(radius),
//            color = if (isActive) activeBtnColor else btnColor,
//            interactionSource = interaction
//        ) {
//            Box(contentAlignment = Alignment.Center) {
//                Icon(
//                    imageVector = icon,
//                    contentDescription = label,
//                    tint = if (isActive) activeFgColor else fgColor,
//                    modifier = Modifier.size(32.dp)
//                )
//            }
//        }
//        Text(
//            text = label,
//            style = MaterialTheme.typography.labelMedium,
//            fontFamily = MaterialTheme.typography.labelMedium.fontFamily,
//            color = fgColor.copy(0.7f),
//            modifier = Modifier.padding(top = 8.dp),
//            maxLines = 1,
//            overflow = TextOverflow.Ellipsis
//        )
//    }
//}
//
//@Composable
//fun NewSwipeToAnswer(onAnswer: () -> Unit, onDecline: () -> Unit, labelColor: Color = Color.White.copy(0.6f), bgColor: Color = Color.White.copy(0.08f), isPocketBlocked: () -> Boolean = { false }) {
//    val coroutineScope = rememberCoroutineScope()
//    val offsetX = remember { Animatable(0f) }
//    val density = LocalDensity.current
////    val isDark = isSystemInDarkTheme()
//    val handleColor = Color.White //if (isDark) Color.White else Color.Black.copy(0.85f)
//    val handleFg = Color.Black //if (isDark) Color.Black else Color.White
//
//    // pillWidth tracks the actual measured pill width so we can compute real edge travel
//    var pillWidthPx by remember { mutableFloatStateOf(0f) }
//    val handleSizePx = with(density) { 72.dp.toPx() }
//    val paddingHandle = with(density) { (38).dp.toPx() }
//    // maxDrag = half of (pillWidth - handleSize) → handle reaches the pill edge
//    val maxDrag = ((pillWidthPx - handleSizePx - paddingHandle) / 2f).coerceAtLeast(with(density) { 120.dp.toPx() })
//
//    // Icon rotation animation
//    val targetRotation = when {
//        offsetX.value > maxDrag * 0.5f -> 0f      // right zone (response) → 0°
//        offsetX.value < -maxDrag * 0.5f -> 135f   // left zone (deviation) → +67.5°
//        else -> 0f
//    }
//    val rotation by animateFloatAsState(
//        targetValue = targetRotation,
//        animationSpec = spring(
//            stiffness = Spring.StiffnessMediumLow,
//            dampingRatio = Spring.DampingRatioMediumBouncy
//        ),
//        label = "handleRotation"
//    )
//
//    Column(modifier = Modifier
//        .fillMaxWidth()
//        .padding(bottom = 60.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
//        Surface(
//            onClick = {},
//            shape = CircleShape,
//            color = bgColor,
//            modifier = Modifier
//                .height(45.dp)
//                .wrapContentWidth() //width(140.dp)
//        ) {
//            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
//                Spacer(modifier = Modifier.width(18.dp))
//                Icon(painter = painterResource(id = R.drawable.ic_message_outline), "Message", tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(22.dp))
//                Spacer(modifier = Modifier.width(12.dp))
//                Text("Message", color = labelColor, style = MaterialTheme.typography.labelLarge)
//                Spacer(modifier = Modifier.width(18.dp))
//            }
//        }
//        Spacer(modifier = Modifier.size(24.dp))
//        Box(
//            modifier = Modifier
//                .height(90.dp)
//                .fillMaxWidth(0.85f)
//                .clip(CircleShape)
//                .background(bgColor)
//                .onSizeChanged { pillWidthPx = it.width.toFloat() },
//            contentAlignment = Alignment.Center
//        ) {
//            Row(modifier = Modifier
//                .fillMaxWidth()
//                .padding(horizontal = 40.dp), horizontalArrangement = Arrangement.SpaceBetween) {
//                Text("Decline", color = labelColor, style = MaterialTheme.typography.bodyLarge)
//                Text("Answer", color = labelColor, style = MaterialTheme.typography.bodyLarge)
//            }
//            Box(
//                modifier = Modifier
//                    .offset { IntOffset(offsetX.value.roundToInt(), 0) }
////                    .size(72.dp)
//                    .height(72.dp)
//                    .width(92.dp)
//                    .clip(CircleShape)
//                    .background(handleColor)
//                    .pointerInput(Unit) {
//                        detectHorizontalDragGestures(
//                            onDragEnd = {
//                                coroutineScope.launch {
//                                    when {
//                                        offsetX.value >= maxDrag * 0.90f -> onAnswer()
//                                        offsetX.value <= -maxDrag * 0.90f -> onDecline()
//                                        else -> offsetX.animateTo(0f, spring(dampingRatio = 0.8f))
//                                    }
//                                }
//                            },
//                            onHorizontalDrag = { change, dragAmount ->
//                                if (!isPocketBlocked()) {
//                                    change.consume()
//                                    coroutineScope.launch {
//                                        offsetX.snapTo(
//                                            (offsetX.value + dragAmount).coerceIn(
//                                                -maxDrag,
//                                                maxDrag
//                                            )
//                                        )
//                                    }
//                                } else {
//                                    // In pocket: snap back to center and consume event to prevent accidental action
//                                    change.consume()
//                                    coroutineScope.launch {
//                                        offsetX.animateTo(
//                                            0f,
//                                            spring(dampingRatio = 0.8f)
//                                        )
//                                    }
//                                }
//                            }
//                        )
//                    }
//                    .graphicsLayer {
//                        rotationZ = rotation  // Применяем поворот
//                    },
//                contentAlignment = Alignment.Center
//            ) {
//                Icon(
//                    imageVector = Icons.Rounded.Call,
//                    contentDescription = null,
//                    tint = when {
//                        offsetX.value > maxDrag * 0.5f -> color_call_button
//                        offsetX.value < -maxDrag * 0.5f -> color_call_end
//                        else -> color_call_button }, //handleFg
//                    modifier = Modifier.size(30.dp)
//                )
//            }
//        }
//    }
//}
//
//private fun formatDuration(seconds: Long): String {
//    val h = seconds / 3600; val m = (seconds % 3600) / 60; val s = seconds % 60
//    return if (h > 0) String.format("%02d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
//}
