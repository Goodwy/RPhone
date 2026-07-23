package dev.goodwy.rphone.view.screen

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.provider.ContactsContract
import android.telecom.TelecomManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.view.WindowManager
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import dev.goodwy.rphone.controller.ContactsViewModel
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.controller.util.makeCall
import dev.goodwy.rphone.view.components.SimPickerDialog
import dev.goodwy.rphone.view.components.tiles.SingleTile
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.ContactDetailsScreenDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinActivityViewModel
import dev.goodwy.rphone.liquidglass.drawBackdrop
import dev.goodwy.rphone.liquidglass.drawPlainBackdrop
import dev.goodwy.rphone.liquidglass.effects.blur
import dev.goodwy.rphone.liquidglass.effects.lens
import dev.goodwy.rphone.liquidglass.effects.colorControls
import dev.goodwy.rphone.liquidglass.highlight.Highlight
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.core.net.toUri
import dev.goodwy.rphone.R
import dev.goodwy.rphone.bottomBarHeight
import dev.goodwy.rphone.cardCornerBig
import dev.goodwy.rphone.cardCornerMedium
import dev.goodwy.rphone.controller.CallLogViewModel
import dev.goodwy.rphone.liquidglass.LocalLiquidGlassBackdrop
import dev.goodwy.rphone.view.components.PermissionDeniedView
import dev.goodwy.rphone.view.components.PlaceholderView
import dev.goodwy.rphone.view.theme.MyColors.dialpadColor
import dev.goodwy.rphone.view.theme.MyColors.dialpadKeyColor
import dev.goodwy.rphone.view.theme.TabTransitionStyle
import dev.goodwy.rphone.view.theme.color_call_button
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState
import com.ramcosta.composedestinations.generated.destinations.ContactEditScreenDestination
import dev.goodwy.rphone.controller.UssdRepository
import dev.goodwy.rphone.controller.util.SocialUtils
import dev.goodwy.rphone.controller.util.SocialUtils.getInstalledMessenger
import dev.goodwy.rphone.controller.util.SocialUtils.messengerPackages
import dev.goodwy.rphone.modal.data.getDisplayName
import dev.goodwy.rphone.view.components.RillDialog
import dev.goodwy.rphone.view.components.RillExpressiveButton

/**
 * Keeps the in-progress dialed digits alive across the dialpad bottom sheet being dismissed
 * (e.g. by swiping down on the drag handle) and reopened. The sheet's composable is fully torn
 * down on dismiss, so a plain `remember` loses the typed number; this small in-memory holder
 * survives that as long as the process is alive, matching what users expect from a dialer.
 */
private object DialpadDraftHolder {
    var pendingNumber: String = ""
}

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Destination<RootGraph>(style = TabTransitionStyle::class)
@Composable
fun DialPadScreen(
    navController: NavController,
    navigator: DestinationsNavigator,
    initialNumber: String? = null
) {
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { true }
    )
    val prefs = koinInject<PreferenceManager>()

    // Lock the window so the keyboard never pushes the bottom sheet up.
    // WindowCompat.setDecorFitsSystemWindows(false) in MainActivity normally causes
    // the sheet to resize with the IME; overriding softInputMode here prevents that.
    val view = LocalView.current
    DisposableEffect(Unit) {
        val window = (view.context as? android.app.Activity)?.window
        val prevMode = window?.attributes?.softInputMode ?: 0
        window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        onDispose {
            window?.setSoftInputMode(prevMode)
        }
    }

    var number by remember { mutableStateOf(initialNumber?.ifBlank { DialpadDraftHolder.pendingNumber } ?: DialpadDraftHolder.pendingNumber) }
    // Keep the draft holder in sync so dismissing the sheet (including swipe-down-to-dismiss)
    // and reopening it restores whatever digits were typed, instead of clearing them.
    LaunchedEffect(number) { DialpadDraftHolder.pendingNumber = number }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
//    ModalBottomSheet(
//        onDismissRequest = { navigator.navigateUp() },
//        sheetState = sheetState,
//        shape = RoundedCornerShape(topStart = 38.dp, topEnd = 38.dp),
//        containerColor = MaterialTheme.colorScheme.surfaceContainerLow, //MaterialTheme.colorScheme.surfaceContainerHigh,
//        tonalElevation = 4.dp,
//        scrimColor = Color.Transparent,
//        contentWindowInsets = {
//            if (isLandscape) {
//                WindowInsets.systemBars.only(
//                    WindowInsetsSides.Top + WindowInsetsSides.Horizontal
//                )
//            } else BottomSheetDefaults.windowInsets
//        },
//        dragHandle = {
//            if (isLandscape) null
//            else {
//                Box(
//                    modifier = Modifier
//                        .fillMaxWidth()
//                        .padding(top = 12.dp, bottom = 12.dp),
//                    contentAlignment = Alignment.Center
//                ) {
//                    Surface(
//                        shape = RoundedCornerShape(3.dp),
//                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
//                        modifier = Modifier.size(width = 36.dp, height = 4.dp)
//                    ) {}
//                }
//            }
//        },
//        modifier = Modifier.statusBarsPadding()
//    ) {
//        DialPadContent(
//            initialNumber = initialNumber,
//            navigator = navigator,
//            onDismiss = { navigator.navigateUp() },
//            isBottomSheet = true
//        )
//    }

//    val scope = rememberCoroutineScope()
//    val favouritesEnabled = prefs.getBoolean(PreferenceManager.KEY_TAB_SHOW_FAVORITES, true)
//    val contactsEnabled = prefs.getBoolean(PreferenceManager.KEY_TAB_SHOW_CONTACTS, true)
//    val notesEnabled = prefs.getBoolean(PreferenceManager.KEY_TAB_SHOW_NOTES, false)
    Scaffold(
        modifier = Modifier
            .fillMaxSize(),
//            .pointerInput(Unit) {
//                awaitPointerEventScope {
//                    while (true) {
//                        val down = awaitPointerEvent(PointerEventPass.Final).changes.firstOrNull()
//                            ?: continue
//                        if (!down.pressed) continue
//                        val startX = down.position.x
//                        val startY = down.position.y
//                        val startTime = System.currentTimeMillis()
//                        var triggered = false
//                        while (true) {
//                            val event = awaitPointerEvent(PointerEventPass.Final)
//                            val change = event.changes.firstOrNull() ?: break
//                            val dx = change.position.x - startX
//                            val dy = change.position.y - startY
//                            val elapsed = System.currentTimeMillis() - startTime
//                            if (!triggered && elapsed >= 150L &&
//                                abs(dx) > 700f &&
//                                abs(dx) > abs(dy) * 5.5f
//                            ) {
//                                triggered = true
//                                if (dx < 0) {
//                                    val route = when {
//                                        notesEnabled -> NotesScreenDestination.route
//                                        favouritesEnabled -> FavoritesScreenDestination.route
//                                        else -> RecentScreenDestination.route
//                                    }
//                                    scope.launch {
//                                        navController.navigate(route) {
//                                            popUpTo(navController.graph.findStartDestination().id) {
//                                                saveState = true
//                                            }
//                                            launchSingleTop = true; restoreState = true
//                                        }
//                                    }
//                                } else {
//                                    val route = when {
//                                        contactsEnabled -> ContactScreenDestination.route
//                                        else -> RecentScreenDestination.route
//                                    }
//                                    scope.launch {
//                                        navController.navigate(route) {
//                                            popUpTo(navController.graph.findStartDestination().id) {
//                                                saveState = true
//                                            }
//                                            launchSingleTop = true; restoreState = true
//                                        }
//                                    }
//                                }
//                            }
//                            if (!change.pressed) break
//                        }
//                    }
//                }
//            },
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0)
    ) { innerPadding ->
        val pillNav = remember { prefs.getBoolean(PreferenceManager.KEY_PILL_NAV, false) }
        Box(modifier = Modifier
            .padding(innerPadding)
            .fillMaxSize()) {
            DialPadContent(
                initialNumber = number,
                navigator = navigator,
                onDismiss = { navigator.navigateUp() },
                modifier = Modifier
                    .padding(bottom = if (pillNav) 88.dp else bottomBarHeight - 12.dp)
                    .navigationBarsPadding()
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalPermissionsApi::class)
@Composable
fun DialPadContent(
    initialNumber: String? = null,
    navigator: DestinationsNavigator? = null,
    onDismiss: (() -> Unit)? = null,
    showHeader: Boolean = false,
    isBottomSheet: Boolean = false,
    modifier: Modifier = Modifier
) {
    val permStateLogs = rememberPermissionState(Manifest.permission.READ_CALL_LOG)
    val isGrantedLogs = permStateLogs.status == PermissionStatus.Granted
    val permStateContacts = rememberPermissionState(Manifest.permission.READ_CONTACTS)
    val isGrantedContacts = permStateContacts.status == PermissionStatus.Granted

    if (isGrantedLogs && isGrantedContacts) {
        val context = LocalContext.current
        val haptic = LocalHapticFeedback.current
        val clipboard = LocalClipboardManager.current
        val focusManager = LocalFocusManager.current
        val contactsVM: ContactsViewModel = koinActivityViewModel()
        val logsViewModel: CallLogViewModel = koinActivityViewModel()
        val prefs = koinInject<PreferenceManager>()
        val settingsState by prefs.settingsChanged.collectAsState()
        val displayOrder by remember(settingsState) {
            mutableIntStateOf(prefs.getInt(PreferenceManager.KEY_CONTACT_DISPLAY_ORDER, 0))
        }

        val allContacts by contactsVM.allContacts.collectAsState()
        val logs by logsViewModel.allCallLogs.collectAsState()
        var number by remember { mutableStateOf(initialNumber?.ifBlank { DialpadDraftHolder.pendingNumber } ?: DialpadDraftHolder.pendingNumber) }
        // Where new digits get inserted / backspace deletes from. Defaults to the end of the number
        // (normal typing behaviour), but the user can tap anywhere in the number to move it, so they
        // can fill in a missing digit in the middle without having to delete and retype everything.
        var cursorPosition by remember { mutableIntStateOf(number.length) }

        // Route every edit through these so the cursor position stays correct and consistent no
        // matter where the edit originates from (dialpad keys, backspace, paste, clipboard banner,
        // clearing on secret-code detection, etc.)
        fun insertAtCursor(text: String) {
            val at = cursorPosition.coerceIn(0, number.length)
            number = number.substring(0, at) + text + number.substring(at)
            cursorPosition = at + text.length
        }
        fun backspaceAtCursor() {
            val at = cursorPosition.coerceIn(0, number.length)
            if (at > 0) {
                number = number.removeRange(at - 1, at)
                cursorPosition = at - 1
            }
        }
        fun replaceNumber(text: String) {
            number = text
            cursorPosition = text.length
        }
        // Keep the draft holder in sync so dismissing the sheet (including swipe-down-to-dismiss)
        // and reopening it restores whatever digits were typed, instead of clearing them.
        LaunchedEffect(number) { DialpadDraftHolder.pendingNumber = number }

        // Collect USSD / MMI responses from CallService and show inline dialog
        val ussdResult by UssdRepository.response.collectAsState()
        DisposableEffect(Unit) { onDispose { UssdRepository.clear() } }

        ussdResult?.let { (request, response) ->
            AlertDialog(
                onDismissRequest = { UssdRepository.clear() },
                title = {
                    Text(
                        request,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = { Text(response, style = MaterialTheme.typography.bodyMedium) },
                confirmButton = {
                    TextButton(onClick = { UssdRepository.clear() }) {
                        Text(stringResource(R.string.ok))
                    }
                },
                shape = RoundedCornerShape(24.dp)
            )
        }

        val soundPool = remember { buildDtmfSoundPool(context) }

        var showSocialDialog by remember { mutableStateOf(false) }
        val installedWhatsApp = messengerPackages["WhatsApp"]?.let { context.getInstalledMessenger(it) }
        val installedTelegram = messengerPackages["Telegram"]?.let { context.getInstalledMessenger(it) }
        val installedSignal = messengerPackages["Signal"]?.let { context.getInstalledMessenger(it) }
        val initiateMessage = { number: String ->
            context.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    "sms:$number".toUri()
                )
            )
        }

        val t9Enabled = prefs.getBoolean(PreferenceManager.KEY_T9_DIALING, true)
        var showSimPicker by remember { mutableStateOf(false) }
        val telecomManager =
            remember { context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager }
        var pendingSearchCallNumber by remember { mutableStateOf<String?>(null) }

        // Helper: place a call respecting the default SIM preference
        fun placeCallWithSimPreference(num: String) {
            val accounts = try { telecomManager.callCapablePhoneAccounts } catch (_: SecurityException) { emptyList() }
            if (accounts.size > 1) {
                val simPref = prefs.getInt(PreferenceManager.KEY_DEFAULT_SIM, prefs.getDefaultSimIndexDefault())
                when {
                    simPref == 1 && accounts.isNotEmpty() -> {
                        replaceNumber("")
                        makeCall(context, num, accounts[0])
                    }

                    simPref == 2 && accounts.size >= 2 -> {
                        replaceNumber("")
                        makeCall(context, num, accounts[1])
                    }

                    else -> {
                        pendingSearchCallNumber = num
                        showSimPicker = true
                    }
                }
            } else {
                replaceNumber("")
                makeCall(context, num)
            }
        }

        val clipText = remember {
            clipboard.getText()?.text?.filter { it.isDigit() || it == '+' } ?: ""
        }
        var showClipboardBanner by remember { mutableStateOf(clipText.length in 7..15) }
        var showOverflowMenu by remember { mutableStateOf(false) }

        var openDialpadDefault by remember {
            mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_OPEN_DIALPAD_DEFAULT, true))
        }

        val take = 30
        // Search contacts results
        val searchResults by remember(number, allContacts, t9Enabled) {
            derivedStateOf {
                if (number.isEmpty()) emptyList()
                else {
                    val cleanQuery = number.replace(" ", "").replace("-", "")
                    allContacts.asSequence()
                        .filter { contact ->
                            val matchesNumber = contact.phoneNumbers.any {
                                it.replace(" ", "").replace("-", "").contains(cleanQuery)
                            }
                            val matchesName =
                                t9Enabled && T9Matcher.isMatch(contact.displayName, cleanQuery)
                            val matchesNickname = t9Enabled && contact.nickname.let { T9Matcher.isMatch(it, cleanQuery) } ?: false
                            matchesNumber || matchesName || matchesNickname
                        }
                        .take(take)
                        .toList()
                }
            }
        }

        // Search logs results with unique numbers (latest call per number)
        val searchLogsResults by remember(number, logs, t9Enabled) {
            derivedStateOf {
                if (number.isEmpty()) emptyList()
                else {
                    val cleanQuery = number.replace(" ", "").replace("-", "")
                    val filtered = logs.asSequence()
                        .filter { log ->
                            val matchesNumber = log.number.replace(" ", "").replace("-", "").contains(cleanQuery)
                            val matchesName =
                                t9Enabled && T9Matcher.isMatch(log.name ?: "", cleanQuery)
                            matchesNumber || matchesName
                        }
                        .take(take)
                        .toList()
                    // Then, keep only unique numbers (latest call per number)
                    filtered
                        .groupBy { it.number.replace(" ", "").replace("-", "") } // Group by normalized number
                        .map { (_, logsGroup) ->
                            logsGroup.maxByOrNull { it.date } // Take the latest call
                        }
                        .filterNotNull()
                        .sortedByDescending { it.date } // Sort by date descending
                }
            }
        }

        // Create a set of all phone numbers and names from contacts for quick searching
        val contactNumbers = remember(allContacts) {
            allContacts.flatMap { contact ->
                contact.phoneNumbers.map { it.replace(" ", "").replace("-", "") }
            }.toSet()
        }

        val contactNames = remember(allContacts) {
            allContacts.map { it.displayName.lowercase() }.toSet()
        }

        // Filtering logs
        val filteredSearchLogsResults = remember(searchLogsResults, contactNumbers, contactNames) {
            searchLogsResults.filter { log ->
                val logNumber = log.number.replace(" ", "").replace("-", "")
                val logName = log.name?.lowercase() ?: ""

                // Exclude if the number is in contacts OR the name is in contacts
                logNumber !in contactNumbers && logName !in contactNames
            }
        }

//    val scale by animateFloatAsState(
//        targetValue = if (number.isNotEmpty()) 1f else 0.95f,
//        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
//        label = "numberScale"
//    )

        val callPermissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            if (permissions[Manifest.permission.CALL_PHONE] == true) {
                val numToCall = pendingSearchCallNumber ?: number
                pendingSearchCallNumber = null
                val hasPhoneState = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_PHONE_STATE
                ) == PackageManager.PERMISSION_GRANTED
                if (hasPhoneState) {
                    placeCallWithSimPreference(numToCall)
                } else {
                    replaceNumber("")
                    makeCall(context, numToCall)
                }
            } else {
                pendingSearchCallNumber = null
            }
        }

        // Auto-process Android hidden/secret codes and MMI codes as the user types
        fun processSecretCodeIfNeeded(input: String): Boolean {
            val code = input.trim()
            if (code.length < 3) return false

            // ── Pattern 0: *#06# (IMEI) / *#07# (SAR info)  ─────────────────────────────
            // These look like MMI/USSD codes (they even used to be handled that way in this
            // app), but they are NOT network requests at all — dialing them out via
            // TelecomManager.placeCall() sends them to the SIM/carrier as if they were a real
            // number, which is exactly what was causing the SIM-picker prompt / failed "call"
            // instead of the expected system info screen. Stock dialers intercept these two
            // locally, before ever touching Telecom, and this app now does the same.
            //
            // Note: reading the real IMEI via TelephonyManager.getImei() requires
            // READ_PRIVILEGED_PHONE_STATE on Android 10+, which only privileged system apps
            // can hold — being the default dialer does not grant it. So instead of showing a
            // (permission-blocked) in-app dialog, we open Android's own "About phone → IMEI
            // information" settings screen, which is what actually has that privilege and is
            // guaranteed to exist on every device.
            if (code == "*#06#") {
                try {
                    context.startActivity(
                        Intent(android.provider.Settings.ACTION_DEVICE_INFO_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } catch (_: Exception) {}
                return true
            }
            if (code == "*#07#") {
                // "SAR information" doesn't have one stable intent action across all OEMs/OS
                // versions the way IMEI does, so try the couple of known ones first and fall
                // back to the general device-info settings screen (still a real system menu,
                // not a failed call) if none of them resolve on this device.
                val sarActions = listOf(
                    "android.settings.SAR_INFORMATION",
                    "android.settings.RF_EXPOSURE_SETTINGS",
                    android.provider.Settings.ACTION_DEVICE_INFO_SETTINGS
                )
                for (action in sarActions) {
                    try {
                        context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        break
                    } catch (_: Exception) { /* try next action */ }
                }
                return true
            }

            // ── Pattern 1: *#*#DIGITS#*#*  (Android secret activity codes, e.g. testing menu) ──
            // These end with #*#* so a plain endsWith("#") check misses them entirely
            val secretMatch = Regex("^\\*#\\*#(\\d+)#\\*#\\*$").find(code)
            if (secretMatch != null) {
                val digits = secretMatch.groupValues[1]
                // Fire every known delivery mechanism unconditionally rather than only
                // falling back to the classic broadcasts when sendDialerSpecialCode() throws.
                // sendDialerSpecialCode() is a fire-and-forget AIDL call — it reports no
                // success/failure back to us, so "didn't throw" is not proof the code was
                // actually delivered anywhere. Different codes are ultimately owned by
                // different apps (Settings' Testing menu for 4636, Calendar Storage for 225,
                // Play Services for 426, an OEM diagnostics app for the hardware-test codes,
                // etc.) and some only listen on one of these two channels, so sending both
                // maximizes the chance whichever app owns this particular code receives it.
                try {
                    val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? android.telephony.TelephonyManager
                    telephonyManager?.sendDialerSpecialCode(digits)
                } catch (_: Exception) {}
                val uri = android.net.Uri.parse("android_secret_code://$digits")
                try {
                    context.sendBroadcast(
                        Intent("android.provider.Telephony.SECRET_CODE", uri).apply {
                            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                        }
                    )
                } catch (_: Exception) {}
                try {
                    context.sendBroadcast(
                        Intent("android.telephony.action.SECRET_CODE", uri).apply {
                            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                        }
                    )
                } catch (_: Exception) {}
                return true
            }

            // ── Pattern 2: USSD / MMI codes  ──────────────────────────────────────────
            // *124#  *123#  *199#  ##002#  *21*N#  *#21#  *#62#
            // (*#06# and *#07# are intercepted above and never reach this branch.)
            val decoded = try { android.net.Uri.decode(code) } catch (_: Exception) { code }
            if (!((decoded.startsWith("*") || decoded.startsWith("#")) &&
                        decoded.endsWith("#"))) return false

            // Dial USSD/MMI codes exactly like a normal call via TelecomManager.placeCall()
            // (same approach RivoPhoneApp uses). The carrier's telephony stack recognises the
            // MMI/USSD prefix itself and drives the whole USSD session — including any
            // interactive multi-step menu — through Android's own native USSD dialog, and
            // placing a real call also lets CallService's connection-event listener (see
            // isUssdNumber() below) pick up and surface the response inline when the
            // carrier/OEM supplies one. This is far more reliable than
            // TelephonyManager.sendUssdRequest(), which only supports a single
            // non-interactive request/response and fails outright on many devices, carriers,
            // and dual-SIM setups.
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                placeCallWithSimPreference(decoded)
            } else {
                pendingSearchCallNumber = decoded
                callPermissionLauncher.launch(arrayOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_PHONE_STATE))
            }
            return true
        }

        fun initiateCall(num: String) {
            val cleanNum = num.trim()
            if (cleanNum.isEmpty() || cleanNum == "Unknown") return
            // MMI/USSD codes (*#06#, *#*#4636#*#*, *21*1234#, *124#, ##002#, etc.) are handled
            // by processSecretCodeIfNeeded which uses telecomManager.placeCall() with the raw URI
            // for proper carrier-stack routing without looping back to this app.
            if (processSecretCodeIfNeeded(cleanNum)) {
                replaceNumber("")
                return
            }
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                val hasPhoneState = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
                if (hasPhoneState) {
                    placeCallWithSimPreference(cleanNum)
                } else {
                    replaceNumber("")
                    makeCall(context, cleanNum)
                }
            } else {
                pendingSearchCallNumber = cleanNum
                callPermissionLauncher.launch(arrayOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_PHONE_STATE))
            }
        }

        if (showSimPicker) {
            SimPickerDialog(
                onDismissRequest = { showSimPicker = false },
                onSimSelected = { handle ->
                    replaceNumber("")
                    makeCall(context, pendingSearchCallNumber ?: number, handle)
                    pendingSearchCallNumber = null
                    showSimPicker = false
                }
            )
        }

        if (showSocialDialog) {
            RillDialog(
                onDismissRequest = { showSocialDialog = false },
                title = stringResource(R.string.connect_via_social),
                icon = ImageVector.vectorResource(id = R.drawable.ic_message_outline),
                confirmButton = {
                    TextButton(onClick = { showSocialDialog = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (installedWhatsApp != null) {
                        RillExpressiveButton(
                            modifier = Modifier.weight(1f),
                            icon = ImageVector.vectorResource(id = R.drawable.ic_whatsapp),
                            label = "WhatsApp",
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            iconSize = 32.dp,
                            onClick = {
                                SocialUtils.openWhatsApp(context, number)
                                showSocialDialog = false
                            }
                        )
                    }
                    if (installedTelegram != null) {
                        RillExpressiveButton(
                            modifier = Modifier.weight(1f),
                            icon = ImageVector.vectorResource(id = R.drawable.ic_telegram),
                            label = "Telegram",
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            iconSize = 32.dp,
                            onClick = {
                                SocialUtils.openTelegram(context, number)
                                showSocialDialog = false
                            }
                        )
                    }
                    if (installedSignal != null) {
                        RillExpressiveButton(
                            modifier = Modifier.weight(1f),
                            icon = ImageVector.vectorResource(id = R.drawable.ic_signal),
                            label = "Signal",
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            iconSize = 32.dp,
                            onClick = {
                                SocialUtils.openSignal(context, number)
                                showSocialDialog = false
                            }
                        )
                    }
                    RillExpressiveButton(
                        modifier = Modifier.weight(1f),
                        icon = ImageVector.vectorResource(id = R.drawable.ic_message_outline),
                        label = "SMS",
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        iconSize = 32.dp,
                        onClick = {
                            initiateMessage(number)
                            showSocialDialog = false
                        }
                    )
                }
            }
        }

        val configuration = LocalConfiguration.current
        val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        if (isLandscape) {
            // Landscape: side-by-side layout — left=search+search results, right=dialpad keys+number+actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left panel: search bar + results only
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(horizontal = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .background(if (isBottomSheet) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surface),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        val searchLogsNotEmpty = filteredSearchLogsResults.isNotEmpty()
                        val searchResultsNotEmpty = searchResults.isNotEmpty()
                        val searchResultsPadding = if (searchLogsNotEmpty) 16.dp else 4.dp
                        // Search contacts results
                        AnimatedVisibility(
                            visible = searchResultsNotEmpty,
                            enter = fadeIn(tween(380, easing = FastOutSlowInEasing)) +
                                    expandVertically(tween(420, easing = FastOutSlowInEasing)),
                            exit = fadeOut(tween(280, easing = FastOutLinearInEasing)) +
                                    shrinkVertically(tween(320, easing = FastOutLinearInEasing))
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(top = 4.dp, bottom = searchResultsPadding)
                                    .then(
                                        if (searchLogsNotEmpty) Modifier
                                        else Modifier.navigationBarsPadding()
                                    )
                                    .then(if (!isBottomSheet) Modifier.statusBarsPadding() else Modifier)
                            ) {
                                Text(
                                    text = stringResource(R.string.contacts),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 4.dp)
                                )
                                Surface(
                                    shape = RoundedCornerShape(24.dp),
                                    color = if (isBottomSheet) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surface,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .background(if (isBottomSheet) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surface),
                                        verticalArrangement = Arrangement.spacedBy(2.dp),
                                    ) {
                                        searchResults.forEach { contact ->
                                            val defaultOrFirstPhone = contact.phoneDetails.firstOrNull { it.isPrimary }?.number ?: contact.phoneNumbers.firstOrNull()
                                            SingleTile(
                                                title = getDisplayName(contact, displayOrder), //contact.displayName,
                                                subtitle = contact.phoneNumbers.firstOrNull(),
                                                photoUri = contact.photoUri,
                                                phoneNumber = defaultOrFirstPhone,
                                                onAvatarClick = {
                                                    navigator?.navigate(
                                                        ContactDetailsScreenDestination(
                                                            contactId = contact.id
                                                        )
                                                    )
                                                },
                                                onClick = {
                                                    val num =
                                                        contact.phoneNumbers.firstOrNull() ?: return@SingleTile
                                                    initiateCall(num)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Search logs results
                        AnimatedVisibility(
                            visible = searchLogsNotEmpty,
                            enter = fadeIn(tween(380, easing = FastOutSlowInEasing)) +
                                    expandVertically(tween(420, easing = FastOutSlowInEasing)),
                            exit = fadeOut(tween(280, easing = FastOutLinearInEasing)) +
                                    shrinkVertically(tween(320, easing = FastOutLinearInEasing))
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(top = 4.dp, bottom = 4.dp)
                                    .navigationBarsPadding()
                                    .then(if (!isBottomSheet && !searchResultsNotEmpty) Modifier.statusBarsPadding() else Modifier)
                            ) {
                                Text(
                                    text = stringResource(R.string.recents),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 4.dp)
                                )
                                Surface(
                                    shape = RoundedCornerShape(24.dp),
                                    color = if (isBottomSheet) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surface,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier
//                                        .padding(vertical = 8.dp)
                                            .background(if (isBottomSheet) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surface),
                                        verticalArrangement = Arrangement.spacedBy(2.dp),
                                    ) {
                                        filteredSearchLogsResults.forEach { log ->
                                            SingleTile(
                                                title = log.name ?: log.number,
                                                subtitle = if (log.name == log.number) null else log.number,
                                                phoneNumber = log.number,
                                                photoUri = log.photoUri,
                                                onAvatarClick = if (log.contactId == null) {
                                                    {
                                                        val intent = Intent(Intent.ACTION_INSERT).apply {
                                                            type = ContactsContract.RawContacts.CONTENT_TYPE
                                                            putExtra(ContactsContract.Intents.Insert.PHONE, log.number)
                                                        }
                                                        context.startActivity(intent)
                                                    }
                                                } else null,
                                                onClick = {
                                                    val num = log.number
                                                    initiateCall(num)
                                                },
                                                showAddToContact = log.contactId == null
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.weight(1f))
                    }
                }

                // Right panel: dialpad keys + action buttons below
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .navigationBarsPadding()
                        .padding(horizontal = 6.dp, vertical = 12.dp)
                        .then(if (!isBottomSheet) Modifier.statusBarsPadding() else Modifier),
                    shape = RoundedCornerShape(cardCornerBig),
//                shadowElevation = 2.dp,
                    color = dialpadColor //MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)//.SpaceEvenly
                    ) {
                        // Number display
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1.2f)
                                .clip(RoundedCornerShape(16.dp))
                                .animateContentSize(
                                    animationSpec = spring(
                                        stiffness = Spring.StiffnessLow,
                                        dampingRatio = Spring.DampingRatioMediumBouncy
                                    )
                                )
                                .padding(vertical = 8.dp, horizontal = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            val numberLength = number.length
                            DialpadNumberDisplay(
                                number = number,
                                fontSize = when {
                                    numberLength > 42 -> 8
                                    numberLength > 36 -> 10
                                    numberLength > 32 -> 12
                                    numberLength > 28 -> 14
                                    numberLength > 25 -> 16
                                    numberLength > 22 -> 18
                                    numberLength > 16 -> 20
                                    numberLength > 11 -> 24
                                    else -> 30
                                },
                                cursorPosition = cursorPosition,
                                onCursorPositionChange = { cursorPosition = it },
                                onLongPress = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    showOverflowMenu = true
                                }
                            )
                        }
                        val keys = listOf(
                            listOf("1", "2", "3"),
                            listOf("4", "5", "6"),
                            listOf("7", "8", "9"),
                            listOf("*", "0", "#")
                        )
                        val subKeys = mapOf(
                            "1" to "   ", "2" to "ABC", "3" to "DEF", "4" to "GHI", "5" to "JKL",
                            "6" to "MNO", "7" to "PQRS", "8" to "TUV", "9" to "WXYZ", "0" to "+"
                        )
                        keys.forEach { row ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                row.forEach { key ->
                                    DialPadKey(
                                        number = key,
                                        letters = subKeys[key] ?: "",
                                        soundPool = soundPool,
                                        context = context,
                                        onClick = { digit -> insertAtCursor(digit) },
                                        onLongClick = { digit -> insertAtCursor(digit) },
                                        compact = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f)
                                    )
                                }
                            }
                        }
                        // Action row — below the keys
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1.2f)
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            FadeScaleBox(modifier = Modifier.weight(1f).fillMaxHeight(), visible = number.isNotEmpty()) {
                                DialerActionExpressive(
                                    onClick = {
                                        navigator?.navigate(ContactEditScreenDestination(initialPhone = number))
                                    },
                                    icon = Icons.Default.PersonAdd,
                                    contentDescription = stringResource(R.string.create_contact),
                                    containerColor = Color.Transparent //MaterialTheme.colorScheme.surfaceContainerLow
                                )
                            }
                            DialerActionExpressive(
                                onClick = {
                                    if (number.isNotEmpty()) {
                                        initiateCall(number)
                                    }
                                },
                                onLongClick = {
                                    val pasteText = clipboard.getText()?.text
                                        ?.filter { it.isDigit() || it == '+' || it == '*' || it == '#' } ?: ""
                                    if (number.isEmpty() && pasteText.isNotEmpty()) {
                                        replaceNumber(pasteText)
                                    } else {
                                        clipboard.setText(AnnotatedString(number))
                                    }
                                },
                                icon = Icons.Default.Call,
                                contentDescription = stringResource(R.string.call),
                                containerColor = color_call_button,
                                contentColor = Color.White,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                isLarge = true
                            )
                            FadeScaleBox(modifier = Modifier.weight(1f).fillMaxHeight(), visible = number.isNotEmpty()) {
                                DialerActionExpressive(
                                    onLongClick = {
                                        replaceNumber("")
                                    },
                                    onClick = {
                                        backspaceAtCursor()
                                    },
                                    icon = Icons.AutoMirrored.Outlined.Backspace,
                                    contentDescription = stringResource(R.string.backspace),
                                    containerColor = Color.Transparent //MaterialTheme.colorScheme.surfaceContainerLow
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Prevent keyboard from auto-opening on composition
            LaunchedEffect(Unit) { focusManager.clearFocus() }

            BoxWithConstraints(
                modifier = modifier
                    .fillMaxSize()
                    .then(if (!isBottomSheet) Modifier.statusBarsPadding() else Modifier)
//            .padding(top = 16.dp)
            ) {
                val screenHeight = maxHeight
                val screenWidth = maxWidth

                // Layout: search bar fixed at top, scrollable middle (results/pills/clipboard),
                // dialpad card fixed at bottom. Nothing moves when results appear.
                Box(
                    modifier = Modifier.fillMaxSize(),
//                verticalArrangement = Arrangement.Top
                    contentAlignment = Alignment.BottomEnd
                ) {
                    // ── Middle section: results / pills / clipboard (scrollable, fills space) ──
                    var isDialpadVisible by remember { mutableStateOf(true) }
                    val listScrollState = rememberScrollState()
                    var lastScrollPosition by remember { mutableStateOf(0) }

                    LaunchedEffect(listScrollState.isScrollInProgress) {
                        snapshotFlow { listScrollState.isScrollInProgress }
                            .collect { isScrolling ->
                                if (isScrolling && isDialpadVisible && listScrollState.value > 0) {
                                    isDialpadVisible = false
                                }
                            }
                    }
                    Surface(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(cardCornerMedium),
                        color = Color.Transparent
                    ) {
                        Column(
                            modifier = Modifier
//                        .weight(1f)
                                .fillMaxSize()
                                .navigationBarsPadding()
                                .verticalScroll(listScrollState),
                            verticalArrangement = Arrangement.Top
                        ) {

                            AnimatedVisibility(
                                visible = number.isNotEmpty(),// && searchResults.isEmpty() && searchQuery.isEmpty(),
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 8.dp, start = 2.dp, end = 2.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Surface(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            navigator?.navigate(ContactEditScreenDestination(initialPhone = number))
                                        },
                                        shape = RoundedCornerShape(50.dp),
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(
                                                horizontal = 12.dp,
                                                vertical = 10.dp
                                            ),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            val create = stringResource(R.string.create_contact)
                                            Icon(
                                                Icons.Default.PersonAdd,
                                                create,
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                create,
                                                style = MaterialTheme.typography.labelLarge,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    Surface(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            val intent =
                                                Intent(Intent.ACTION_INSERT_OR_EDIT).apply {
                                                    type =
                                                        ContactsContract.Contacts.CONTENT_ITEM_TYPE
                                                    putExtra(
                                                        ContactsContract.Intents.Insert.PHONE,
                                                        number
                                                    )
                                                }
                                            context.startActivity(intent)
                                        },
                                        shape = RoundedCornerShape(50.dp),
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(
                                                horizontal = 12.dp,
                                                vertical = 10.dp
                                            ),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            val add = stringResource(R.string.add_to_contact)
                                            Icon(
                                                Icons.Default.Person,
                                                add,
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                add,
                                                style = MaterialTheme.typography.labelLarge,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    Surface(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            if (
                                                installedWhatsApp != null ||
                                                installedTelegram != null ||
                                                installedSignal != null
                                            ) showSocialDialog = true
                                            else initiateMessage(number)
                                        },
                                        shape = RoundedCornerShape(50.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(
                                                horizontal = 12.dp,
                                                vertical = 10.dp
                                            ),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            val message = stringResource(R.string.message)
                                            Icon(
                                                painter = painterResource(id = R.drawable.ic_message_outline),
                                                message,
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                message,
                                                style = MaterialTheme.typography.labelLarge,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }

                            val configuration = LocalConfiguration.current
                            val screenHeightDp = configuration.screenHeightDp.dp
                            val searchResultsBottomPadding = screenHeightDp * 0.50f
                            val searchResultsPadding =
                                if (filteredSearchLogsResults.isNotEmpty()) 16.dp else searchResultsBottomPadding
                            // Search contacts results
                            AnimatedVisibility(
                                visible = searchResults.isNotEmpty(),
                                enter = fadeIn(tween(380, easing = FastOutSlowInEasing)) +
                                        expandVertically(tween(420, easing = FastOutSlowInEasing)),
                                exit = fadeOut(tween(280, easing = FastOutLinearInEasing)) +
                                        shrinkVertically(tween(320, easing = FastOutLinearInEasing))
                            ) {
                                Column(
                                    modifier = Modifier//.padding(horizontal = 16.dp, vertical = 4.dp)
                                        .padding(
//                                        start = 16.dp,
//                                        end = 16.dp,
                                            top = 4.dp,
                                            bottom = searchResultsPadding
                                        )
                                ) {
                                    Text(
                                        text = stringResource(R.string.contacts),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 4.dp)
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(24.dp),
                                        color = if (isBottomSheet) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surface,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier
//                                        .padding(vertical = 8.dp)
                                                .background(if (isBottomSheet) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surface),
                                            verticalArrangement = Arrangement.spacedBy(2.dp),
                                        ) {
                                            searchResults.forEach { contact ->
                                                val defaultOrFirstPhone = contact.phoneDetails.firstOrNull { it.isPrimary }?.number ?: contact.phoneNumbers.firstOrNull()
                                                SingleTile(
                                                    title = getDisplayName(contact, displayOrder), //contact.displayName,
                                                    subtitle = contact.phoneNumbers.firstOrNull(),
                                                    photoUri = contact.photoUri,
                                                    phoneNumber = defaultOrFirstPhone,
                                                    onAvatarClick = {
                                                        navigator?.navigate(
                                                            ContactDetailsScreenDestination(
                                                                contactId = contact.id
                                                            )
                                                        )
                                                    },
                                                    onClick = {
                                                        val num = contact.phoneNumbers.firstOrNull() ?: return@SingleTile
                                                        initiateCall(num)
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Search logs results
                            AnimatedVisibility(
                                visible = filteredSearchLogsResults.isNotEmpty(),
                                enter = fadeIn(tween(380, easing = FastOutSlowInEasing)) +
                                        expandVertically(tween(420, easing = FastOutSlowInEasing)),
                                exit = fadeOut(tween(280, easing = FastOutLinearInEasing)) +
                                        shrinkVertically(tween(320, easing = FastOutLinearInEasing))
                            ) {
                                Column(
                                    modifier = Modifier//.padding(horizontal = 16.dp, vertical = 4.dp)
                                        .padding(
//                                        start = 16.dp,
//                                        end = 16.dp,
                                            top = 4.dp,
                                            bottom = searchResultsBottomPadding
                                        )
                                ) {
                                    Text(
                                        text = stringResource(R.string.recents),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 4.dp)
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(24.dp),
                                        color = if (isBottomSheet) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surface,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier
//                                        .padding(vertical = 8.dp)
                                                .background(if (isBottomSheet) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surface),
                                            verticalArrangement = Arrangement.spacedBy(2.dp),
                                        ) {
                                            filteredSearchLogsResults.forEach { log ->
                                                SingleTile(
                                                    title = log.name ?: log.number,
                                                    subtitle = if (log.name == log.number) null else log.number,
                                                    phoneNumber = log.number,
                                                    photoUri = log.photoUri,
                                                    onAvatarClick = if (log.contactId == null) {
                                                        {
                                                            val intent = Intent(Intent.ACTION_INSERT).apply {
                                                                type = ContactsContract.RawContacts.CONTENT_TYPE
                                                                putExtra(ContactsContract.Intents.Insert.PHONE, log.number)
                                                            }
                                                            context.startActivity(intent)
                                                        }
                                                    } else null,
                                                    onClick = {
                                                        val num = log.number
                                                        initiateCall(num)
                                                    },
                                                    showAddToContact = log.contactId == null
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Clipboard banner
                            AnimatedVisibility(
                                visible = showClipboardBanner && number.isEmpty(),
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically()
                            ) {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(
//                                        horizontal = 16.dp,
                                            vertical = 4.dp
                                        ),
                                    shape = RoundedCornerShape(cardCornerBig),
                                    color = MaterialTheme.colorScheme.secondaryContainer
                                ) {
                                    Row(
                                        modifier = Modifier.padding(
                                            start = 24.dp, end = 8.dp, top = 8.dp, bottom = 8.dp
                                        ),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            Icons.Default.ContentPaste,
                                            null,
                                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            text = clipText,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(horizontal = 20.dp)
                                        )
                                        TextButton(onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); replaceNumber(clipText); showClipboardBanner = false
                                        }) {
                                            Text(stringResource(R.string.use), color = MaterialTheme.colorScheme.primary)
                                        }
                                        IconButton(
                                            onClick = { showClipboardBanner = false },
//                                        modifier = Modifier.size(cardCornerBig)
                                        ) {
                                            Icon(Icons.Default.Close, stringResource(R.string.close))
                                        }
                                    }
                                }
                            }

                            AnimatedVisibility(
                                visible = searchResults.isEmpty() && filteredSearchLogsResults.isEmpty() && number.isNotEmpty(),
                                enter = fadeIn(tween(380, easing = FastOutSlowInEasing)) +
                                        expandVertically(tween(420, easing = FastOutSlowInEasing)),
                                exit = fadeOut(tween(280, easing = FastOutLinearInEasing)) +
                                        shrinkVertically(tween(320, easing = FastOutLinearInEasing))
                            ) {
                                PlaceholderView(
                                    icon = Icons.Rounded.SearchOff,
                                    title = stringResource(R.string.no_results),
                                )
                            }
                        } // end scrollable middle Column
                    }


                    // Show dialpad button
                    val pillNav = remember { prefs.getBoolean(PreferenceManager.KEY_PILL_NAV, false) }
                    AnimatedVisibility(
                        visible = !isDialpadVisible,
                        enter = scaleIn() + fadeIn(),
                        exit = scaleOut() + fadeOut()
                    ) {
                        val dialpadTab =
                            prefs.getBoolean(PreferenceManager.KEY_TAB_SHOW_DIALPAD, true)
                        val fabShape = RoundedCornerShape(17.dp)
                        FloatingActionButton(
                            modifier = Modifier
                                .padding(bottom = if (pillNav) 0.dp else 24.dp)
                                .then(
                                    if (dialpadTab) Modifier
                                        .padding(horizontal = 16.dp, vertical = 4.dp)
                                    else Modifier
                                        .navigationBarsPadding()
                                        .padding(horizontal = 16.dp, vertical = 16.dp)
                                ),
                            onClick = { isDialpadVisible = true },
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            shape = fabShape,
                            elevation = FloatingActionButtonDefaults.elevation(4.dp),
                        ) { Icon(Icons.Default.Dialpad, stringResource(R.string.keypad)) }
                    }

                    // ── Dialpad card — hides with animation when search is active ──
                    AnimatedVisibility(
                        visible = isDialpadVisible || number.isEmpty(),
                        enter = slideInVertically(
                            initialOffsetY = { it },
                            animationSpec = tween(
                                durationMillis = 320,
                                easing = FastOutSlowInEasing
                            )
                        ) + fadeIn(
                            animationSpec = tween(
                                durationMillis = 280,
                                easing = FastOutSlowInEasing
                            )
                        ),
                        exit = slideOutVertically(
                            targetOffsetY = { it },
                            animationSpec = tween(
                                durationMillis = 260,
                                easing = FastOutLinearInEasing
                            )
                        ) + fadeOut(
                            animationSpec = tween(
                                durationMillis = 200,
                                easing = FastOutLinearInEasing
                            )
                        )
                    ) {
                        // ── Dialpad card — always at bottom, never moves ───────────────
                        BoxWithConstraints(
                            contentAlignment = Alignment.BottomCenter,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                        ) {
                            // Scale based on the SMALLER of width-derived and height-derived factors
                            // so the dialpad always fits on screen regardless of device size.
                            val refWidth = 360f
                            val availableWidth = maxWidth.value
                            val widthScale = (availableWidth / refWidth).coerceIn(0.6f, 1.4f)

                            // Height budget: total screen height minus search bar (~64dp) minus spacing (~24dp)
                            // The dialpad card needs: header(~56dp) + 4 key rows + action row(~72dp) + padding(~40dp)
                            // Reference key height = 68dp, so 4 rows = 272dp + overhead ~168dp = ~440dp total card
                            val cardHeightBudget =
                                (screenHeight.value - 64f - 24f).coerceAtLeast(200f)
                            val refCardHeight = 440f
                            val heightScale =
                                (cardHeightBudget / refCardHeight).coerceIn(0.55f, 1.4f)

                            val scaleFactor = minOf(widthScale, heightScale)

                            val keyWidth: Dp = (108 * scaleFactor).dp
                            val keyHeight: Dp = (56 * scaleFactor).dp //58
//                            val actionSize: Dp = (64 * scaleFactor).dp
                            val callW: Dp = (108 * scaleFactor).dp
                            val callH: Dp = (58 * scaleFactor).dp
                            val spacing = if (isBottomSheet) 8 else 6
                            val keySpacing: Dp = (spacing * scaleFactor).dp //8

                            Box( // Hides the bottom part of the list
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(if (isBottomSheet) 100.dp else 200.dp)
                                    .background(if (isBottomSheet) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surface)
                            )
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = if (pillNav) 0.dp else 24.dp),
                                shape = RoundedCornerShape(32.dp),
                                color = dialpadColor, //MaterialTheme.colorScheme.surfaceContainerLow,
//                            shadowElevation = 2.dp
                            ) {
                                val topBottom = if (isBottomSheet) 16 else 8
                                Column(
                                    modifier = Modifier.padding(
                                        horizontal = 8.dp,
                                        vertical = (topBottom * scaleFactor).coerceIn(
                                            6f,
                                            16f
                                        ).dp //16
                                    ),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(keySpacing)
                                ) {
                                    // Header row
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = if (isBottomSheet) 8.dp else 0.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val pasteText = clipboard.getText()?.text
                                            ?.filter { it.isDigit() || it == '+' || it == '*' || it == '#' } ?: ""
                                        if (number.isNotEmpty() || pasteText.isNotEmpty()) {
                                            Box(modifier = Modifier.padding(12.dp)) {
                                                val optionsSource =
                                                    remember { MutableInteractionSource() }
                                                Icon(
                                                    imageVector = Icons.Default.MoreVert,
                                                    contentDescription = stringResource(R.string.more),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier
                                                        .combinedClickable(
                                                            onClick = { showOverflowMenu = true },
                                                            interactionSource = optionsSource,
                                                            indication = ripple(
                                                                bounded = false,
                                                                radius = 26.dp
                                                            )
                                                        ),
                                                )
                                            }
                                            AnimatedVisibility(
                                                visible = showOverflowMenu,
                                                enter = slideInVertically(
                                                    initialOffsetY = { -it },
                                                    animationSpec = tween(
                                                        320,
                                                        easing = FastOutSlowInEasing
                                                    )
                                                ) + fadeIn(tween(280)),
                                                exit = slideOutVertically(
                                                    targetOffsetY = { -it },
                                                    animationSpec = tween(
                                                        420,
                                                        easing = FastOutLinearInEasing
                                                    )
                                                ) + fadeOut(tween(380))
                                            ) {
                                                DropdownMenu(
                                                    shape = RoundedCornerShape(16.dp),
                                                    expanded = showOverflowMenu,
                                                    onDismissRequest = { showOverflowMenu = false },
                                                    offset = DpOffset((-24).dp, 32.dp),
                                                ) {
                                                    if (number.isNotEmpty()) {
                                                        DropdownMenuItem(
                                                            contentPadding = PaddingValues(
                                                                horizontal = 24.dp
                                                            ),
                                                            text = { Text(stringResource(R.string.add_pause)) },
//                                                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                                            onClick = {
                                                                showOverflowMenu = false
                                                                number += ","
                                                            }
                                                        )
                                                        DropdownMenuItem(
                                                            contentPadding = PaddingValues(
                                                                horizontal = 24.dp
                                                            ),
                                                            text = { Text(stringResource(R.string.add_wait)) },
//                                                        leadingIcon = { Icon(Icons.Default.Share, null) },
                                                            onClick = {
                                                                showOverflowMenu = false
                                                                number += ";"
                                                            }
                                                        )
                                                        DropdownMenuItem(
                                                            contentPadding = PaddingValues(
                                                                horizontal = 24.dp
                                                            ),
                                                            text = { Text(stringResource(R.string.copy)) },
//                                                        leadingIcon = { Icon(Icons.Default.ContentCopy, null) },
                                                            onClick = {
                                                                showOverflowMenu = false
                                                                clipboard.setText(
                                                                    AnnotatedString(
                                                                        number
                                                                    )
                                                                )
                                                            }
                                                        )
                                                    }
                                                    if (pasteText.isNotEmpty()) {
                                                        DropdownMenuItem(
                                                            contentPadding = PaddingValues(
                                                                horizontal = 24.dp
                                                            ),
                                                            text = { Text(stringResource(R.string.paste)) },
//                                                        leadingIcon = { Icon(Icons.Default.ContentPaste, null) },
                                                            onClick = {
                                                                showOverflowMenu = false
                                                                replaceNumber(pasteText)
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
//                                            .defaultMinSize(minHeight = if (number.isEmpty()) 64.dp else 0.dp)
                                                .clip(RoundedCornerShape(cardCornerMedium))
                                                .animateContentSize(
                                                    animationSpec = spring(
                                                        stiffness = Spring.StiffnessLow,
                                                        dampingRatio = Spring.DampingRatioMediumBouncy
                                                    )
                                                )
                                                .padding(vertical = 8.dp, horizontal = 12.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            val numberLength = number.length
                                            DialpadNumberDisplay(
                                                number = number,
                                                fontSize = (
                                                        (when {
                                                            numberLength > 45 -> 6
                                                            numberLength > 39 -> 8
                                                            numberLength > 35 -> 10
                                                            numberLength > 31 -> 12
                                                            numberLength > 27 -> 14
                                                            numberLength > 23 -> 16
                                                            numberLength > 20 -> 18
                                                            numberLength > 16 -> 20
                                                            numberLength > 14 -> 24
                                                            numberLength > 11 -> 28
                                                            else -> 36
                                                        }) * scaleFactor).coerceIn(8f, 40f).toInt(),
                                                cursorPosition = cursorPosition,
                                                onCursorPositionChange = { cursorPosition = it },
                                                onLongPress = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    showOverflowMenu = true
                                                }
                                            )
                                        }

                                        Box(modifier = Modifier.padding(12.dp)) {
                                            val backspaceSource =
                                                remember { MutableInteractionSource() }
//                                    FadeScaleBox(visible = number.isNotEmpty()) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Outlined.Backspace,
                                                contentDescription = stringResource(R.string.backspace),
                                                tint = if (number.isNotEmpty()) MaterialTheme.colorScheme.onSurfaceVariant
                                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                    alpha = 0.5f
                                                ),
                                                modifier = Modifier
                                                    .combinedClickable(
                                                        onClick = { backspaceAtCursor() },
                                                        onLongClick = { replaceNumber("") },
                                                        interactionSource = backspaceSource,
                                                        indication = ripple(
                                                            bounded = false,
                                                            radius = 26.dp
                                                        )
                                                    ),
                                            )
//                                    }
                                        }
                                    }

                                    // Dialpad keys
                                    val keys = listOf(
                                        listOf("1", "2", "3"),
                                        listOf("4", "5", "6"),
                                        listOf("7", "8", "9"),
                                        listOf("*", "0", "#")
                                    )
                                    val subKeys = mapOf(
                                        "1" to "   ",
                                        "2" to "ABC",
                                        "3" to "DEF",
                                        "4" to "GHI",
                                        "5" to "JKL",
                                        "6" to "MNO",
                                        "7" to "PQRS",
                                        "8" to "TUV",
                                        "9" to "WXYZ",
                                        "0" to "+"
                                    )

                                    keys.forEach { row ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceEvenly
                                        ) {
                                            row.forEach { key ->
                                                DialPadKey(
                                                    number = key,
                                                    letters = subKeys[key] ?: "",
                                                    soundPool = soundPool,
                                                    context = context,
                                                    onClick = { digit -> insertAtCursor(digit) },
                                                    onLongClick = { digit -> insertAtCursor(digit) },
                                                    overrideWidth = keyWidth,
                                                    overrideHeight = keyHeight,
                                                    scaleFactor = scaleFactor
                                                )
                                            }
                                        }
                                    }

                                    // Action row
                                    val top = if (isBottomSheet) 6.dp else 1.dp
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 16.dp, end = 16.dp, top = top),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        val lgBackdrop = LocalLiquidGlassBackdrop.current
                                        val lgDialpadEnabled = remember(settingsState) {
                                            prefs.getBoolean(PreferenceManager.KEY_LIQUID_GLASS, false) &&
                                            prefs.getBoolean(PreferenceManager.KEY_LG_DIALPAD_CALL_BUTTON, false)
                                        }
                                        val blurDialpadEnabled = remember(settingsState) {
                                            prefs.getBoolean(PreferenceManager.KEY_BLUR_EFFECTS, false) &&
                                            prefs.getBoolean(PreferenceManager.KEY_BLUR_DIALPAD_CALL_BUTTON, false) &&
                                            !lgDialpadEnabled
                                        }
                                        DialerActionExpressive(
                                            onClick = {
                                                if (number.isNotEmpty()) {
                                                    initiateCall(number)
                                                }
                                            },
                                            onLongClick = {
                                                val pasteText = clipboard.getText()?.text
                                                    ?.filter { it.isDigit() || it == '+' || it == '*' || it == '#' } ?: ""
                                                if (number.isEmpty() && pasteText.isNotEmpty()) {
                                                    replaceNumber(pasteText)
                                                } else {
                                                    clipboard.setText(AnnotatedString(number))
                                                }
                                            },
                                            icon = Icons.Default.Call,
                                            contentDescription = stringResource(R.string.call),
                                            containerColor = color_call_button,
                                            contentColor = Color.White,
                                            modifier = Modifier
                                                .width(callW)
                                                .height(callH),
                                            isLarge = true,
                                            liquidGlassBackdrop = lgBackdrop,
                                            liquidGlassEnabled = lgDialpadEnabled,
                                            blurEnabled = blurDialpadEnabled
                                        )
                                    }
                                }
                            }
                        } // end BoxWithConstraints (dialpad card)
                    } // end AnimatedVisibility (dialpad card)

                    Spacer(modifier = Modifier.height(8.dp))
                } // end outer Column
            } // end BoxWithConstraints (screen)
        }
    } else {
        if (isGrantedLogs) {
            PermissionDeniedView(
                icon = Icons.Rounded.People,
                title = stringResource(R.string.contacts_permission),
                description = stringResource(R.string.contacts_permission_description),
                onGrantClick = { permStateContacts.launchPermissionRequest() }
            )
        } else {
            PermissionDeniedView(
                icon = Icons.Default.Call,
                title = stringResource(R.string.call_history),
                description = stringResource(R.string.call_history_permission),
                onGrantClick = { permStateLogs.launchPermissionRequest() }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DialerActionExpressive(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    containerColor: Color,
    modifier: Modifier = Modifier.size(64.dp),
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    isLarge: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    liquidGlassBackdrop: dev.goodwy.rphone.liquidglass.Backdrop? = null,
    liquidGlassEnabled: Boolean = false,
    blurEnabled: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val prefs = koinInject<PreferenceManager>()
    val haptic = LocalHapticFeedback.current
    val wrappedOnClick: () -> Unit = {
        if (prefs.getBoolean(PreferenceManager.KEY_APP_HAPTICS, true)) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        onClick()
    }
    val wrappedOnLongClick: (() -> Unit)? = if (onLongClick != null) ({
        if (prefs.getBoolean(PreferenceManager.KEY_APP_HAPTICS, true)) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        onLongClick()
    }) else null
    val cornerRadius by animateDpAsState(
        targetValue = if (isPressed) (if (isLarge) 42.dp else 14.dp) else (if (isLarge) 42.dp else 24.dp),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow), label = "ButtonShape"
    )
    val scale by animateFloatAsState(
        targetValue = if (isPressed) (if (isLarge) 1.08f else 1.2f) else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow), label = "ButtonScale"
    )
    val buttonShape = RoundedCornerShape(cornerRadius)
    val useLiquidGlass =
        liquidGlassEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && liquidGlassBackdrop != null
    val useBackdropBlur =
        blurEnabled && !useLiquidGlass && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    if (useLiquidGlass) {
        Box(
            modifier = modifier
                .scale(scale)
                .drawBackdrop(
                    backdrop = liquidGlassBackdrop,
                    shape = { buttonShape },
                    effects = {
                        val d = density
                        colorControls(saturation = 1.3f)
                        blur(2f * d)
                        lens(refractionHeight = 18f * d, refractionAmount = 52f * d)
                    },
                    highlight = { Highlight.Default }
                )
                .combinedClickable(
                    onClick = wrappedOnClick,
                    onLongClick = wrappedOnLongClick,
                    interactionSource = interactionSource,
                    indication = null
                )
        ) {
            Surface(
                shape = buttonShape,
                color = containerColor.copy(alpha = 0.5f),
                contentColor = contentColor,
                modifier = Modifier.matchParentSize()
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription,
                        modifier = Modifier.size(if (isLarge) 32.dp else 24.dp)
                    )
                }
            }
        }
    } else if (useBackdropBlur && liquidGlassBackdrop != null) {
        Surface(
            modifier = modifier
                .scale(scale)
                .drawPlainBackdrop(
                    backdrop = liquidGlassBackdrop,
                    shape = { buttonShape },
                    effects = { blur(30f * density) }
                )
                .combinedClickable(
                    onClick = wrappedOnClick,
                    onLongClick = wrappedOnLongClick,
                    interactionSource = interactionSource,
                    indication = null
                ),
            shape = buttonShape,
            color = containerColor.copy(alpha = 0.72f),
            contentColor = contentColor
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription,
                    modifier = Modifier.size(if (isLarge) 32.dp else 24.dp)
                )
            }
        }
    } else {
        Surface(
            modifier = modifier
                .scale(scale)
                .combinedClickable(
                    onClick = wrappedOnClick,
                    onLongClick = wrappedOnLongClick,
                    interactionSource = interactionSource,
                    indication = null
                ),
            shape = buttonShape,
            color = containerColor,
            contentColor = contentColor
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription,
                    modifier = Modifier.size(if (isLarge) 32.dp else 24.dp)
                )
            }
        }
    }
}

@Composable
fun DialPadKey(
    number: String,
    letters: String,
    soundPool: SoundPool,
    context: Context,
    onClick: (String) -> Unit,
    onLongClick: (String) -> Unit,
    compact: Boolean = false,
    overrideWidth: Dp? = null,
    overrideHeight: Dp? = null,
    scaleFactor: Float = 1f,
    modifier: Modifier? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val prefs = koinInject<PreferenceManager>()
    val haptic = LocalHapticFeedback.current
    val cornerRadius by animateDpAsState(
        if (isPressed) 16.dp else 42.dp,
        spring(stiffness = Spring.StiffnessMediumLow),
        label = "ButtonShapeAnimation"
    )

    val scale by animateFloatAsState(
        if (isPressed) 1.04f else 1f,
        spring(stiffness = Spring.StiffnessMediumLow),
        label = "DialKeyScale"
    )

    val bgColor by animateColorAsState(
        if (isPressed) MaterialTheme.colorScheme.primaryContainer else dialpadKeyColor, //MaterialTheme.colorScheme.surfaceContainerLow,
        spring(stiffness = Spring.StiffnessMedium), "DialKeyColor"
    )

    val keyWidth = overrideWidth ?: if (compact) 82.dp else 108.dp
    val keyHeight = overrideHeight ?: if (compact) 52.dp else 58.dp
    val mainFontSize = (if (compact) 18f else 28f) * scaleFactor.coerceIn(0.6f, 1.4f)
    val subFontSize = (10f * scaleFactor.coerceIn(0.6f, 1.4f))
    Surface(
//        onClick = {
//            if (prefs.getBoolean(
//                    PreferenceManager.KEY_APP_HAPTICS,
//                    true
//                )
//            ) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
//            if (prefs.getBoolean(PreferenceManager.KEY_DTMF_TONE, false)) playDtmf(
//                context,
//                number,
//                soundPool
//            )
//            onClick(number)
//        },
        shape = RoundedCornerShape(cornerRadius),
        color = bgColor,
        modifier = Modifier
            .then(
                modifier ?: Modifier.size(width = keyWidth, height = keyHeight)
            )
//            .size(width = keyWidth, height = keyHeight)
            .scale(scale)
//        interactionSource = interactionSource
    ) {
        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .combinedClickable(
                    onClick = {
                        if (prefs.getBoolean(
                                PreferenceManager.KEY_APP_HAPTICS,
                                true
                            )
                        ) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        if (prefs.getBoolean(PreferenceManager.KEY_DTMF_TONE, false)) playDtmf(
                            context,
                            number,
                            soundPool
                        )
                        onClick(number)
                    },
                    onLongClick = {
                        if (prefs.getBoolean(
                                PreferenceManager.KEY_APP_HAPTICS,
                                true
                            )
                        ) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        if (prefs.getBoolean(PreferenceManager.KEY_DTMF_TONE, false)) playDtmf(
                            context,
                            number,
                            soundPool
                        )
                        val newNumber = when (number) {
                            "0" -> "+"
                            "*" -> ","
                            "#" -> ";"
                            else -> number
                        }
                        onLongClick(newNumber)
                    },
                    interactionSource = interactionSource,
                    indication = ripple()
                ),
        ) {
            Text(
                text = number,
                style = MaterialTheme.typography.headlineMedium.copy(fontSize = mainFontSize.sp),
                fontWeight = FontWeight.Medium
            )
            if (letters.isNotBlank()) {
                Text(
                    text = letters,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = subFontSize.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

/**
 * Renders a phone number with smooth per-character animations:
 * - New chars slide up + fade + scale in from below
 * - Deleted chars slide down + fade + scale out
 * - Existing chars animate their horizontal position smoothly when neighbours appear/disappear
 *
 * Uses a stable monotonically-increasing ID per character insertion so Compose can
 * distinguish "same char shifted left" from "new char at this slot".
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DialpadNumberDisplay(
    number: String,
    fontSize: Int,
    cursorPosition: Int = number.length,
    onCursorPositionChange: (Int) -> Unit = {},
    onLongPress: () -> Unit = {}
) {
    val easeOutExpo = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
    val textColor = MaterialTheme.colorScheme.onSurface
    val textStyle = MaterialTheme.typography.displaySmall.copy(
        fontSize = fontSize.sp,
        fontWeight = FontWeight.Light
    )

    // Stable list of (uniqueId, char) — each insertion gets a fresh monotonic id
    // so position shifts use animateItem, not a full recompose.
    val idCounter = remember { mutableStateOf(0) }
    val stableChars = remember { mutableStateListOf<Pair<Int, Char>>() }

    LaunchedEffect(number) {
        val current = stableChars.map { it.second }.joinToString("")
        if (number == current) return@LaunchedEffect

        // Diff by common prefix/suffix so an insert or delete anywhere in the middle of the
        // string (not just at the end) only touches the characters that actually changed —
        // everything else keeps its stable id and simply slides over.
        val minLen = minOf(current.length, number.length)
        var prefixLen = 0
        while (prefixLen < minLen && current[prefixLen] == number[prefixLen]) prefixLen++

        var suffixLen = 0
        val maxSuffix = minLen - prefixLen
        while (suffixLen < maxSuffix &&
            current[current.length - 1 - suffixLen] == number[number.length - 1 - suffixLen]
        ) suffixLen++

        val removeCount = current.length - prefixLen - suffixLen
        repeat(removeCount) {
            if (stableChars.size > prefixLen) stableChars.removeAt(prefixLen)
        }
        val insertText = number.substring(prefixLen, number.length - suffixLen)
        insertText.forEachIndexed { i, ch ->
            stableChars.add(prefixLen + i, Pair(idCounter.value++, ch))
        }
    }

    // Blinking caret alpha
    val cursorBlink = rememberInfiniteTransition(label = "cursorBlink")
    val cursorAlpha by cursorBlink.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1000
                1f at 0
                1f at 500
                0f at 501
                0f at 999
            }
        ),
        label = "cursorAlpha"
    )

    val clampedCursor = cursorPosition.coerceIn(0, stableChars.size)

    LazyRow(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        userScrollEnabled = false,
        modifier = Modifier.fillMaxWidth()
    ) {
        // Leading spacer matching the trailing tap zone's width below, so the digits (and the
        // cursor) are actually centered in the box instead of being pulled off-center by an
        // unbalanced zone that only exists on the trailing side.
        item(key = "leading_cursor_area") {
            Box(modifier = Modifier.width(28.dp))
        }
        itemsIndexed(
            items = stableChars,
            key = { _, pair -> pair.first }
        ) { index, pair ->
            var appeared by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { appeared = true }

            val offsetY by animateDpAsState(
                targetValue = if (appeared) 0.dp else 20.dp,
                animationSpec = spring(
                    stiffness = Spring.StiffnessLow,
                    dampingRatio = Spring.DampingRatioMediumBouncy
                ),
                label = "charOffY"
            )
            val alpha by animateFloatAsState(
                targetValue = if (appeared) 1f else 0f,
                animationSpec = tween(360, easing = easeOutExpo),
                label = "charAlpha"
            )
            val scale by animateFloatAsState(
                targetValue = if (appeared) 1f else 0.55f,
                animationSpec = spring(
                    stiffness = Spring.StiffnessLow,
                    dampingRatio = Spring.DampingRatioMediumBouncy
                ),
                label = "charScale"
            )

            Box(contentAlignment = Alignment.CenterStart) {
                // A thin blinking bar rendered just before this character when the cursor sits
                // here, so it visually sits between the two adjacent digits.
                if (clampedCursor == index) {
                    Box(
                        modifier = Modifier
                            .offset(x = (-2).dp)
                            .width(2.dp)
                            .height(with(LocalDensity.current) { textStyle.fontSize.toDp() * 0.9f })
                            .align(Alignment.CenterStart)
                            .graphicsLayer { this.alpha = cursorAlpha }
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
                Text(
                    text = pair.second.toString(),
                    style = textStyle,
                    color = textColor,
                    modifier = Modifier
                        .animateItem(
                            placementSpec = spring(
                                stiffness = Spring.StiffnessMediumLow,
                                dampingRatio = Spring.DampingRatioMediumBouncy
                            ),
                            fadeInSpec = tween(360, easing = easeOutExpo),
                            fadeOutSpec = tween(220)
                        )
                        .offset(y = offsetY)
                        .alpha(alpha)
                        .scale(scale)
                        .pointerInput(pair.first) {
                            detectTapGestures(
                                onLongPress = { onLongPress() }
                            ) { tapOffset ->
                                // Tapping the left half of a digit places the cursor before it,
                                // the right half places it after — like a normal text field.
                                val newPos = if (tapOffset.x < size.width / 2f) index else index + 1
                                onCursorPositionChange(newPos)
                            }
                        }
                )
            }
        }

        if (number.isNotEmpty()) {
            item(key = "trailing_cursor_area") {
                Box(
                    modifier = Modifier
                        .width(28.dp)
                        .height(with(LocalDensity.current) { textStyle.fontSize.toDp() * 1.4f })
                        .pointerInput(stableChars.size) {
                            detectTapGestures(
                                onLongPress = { onLongPress() }
                            ) { onCursorPositionChange(stableChars.size) }
                        },
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (clampedCursor == stableChars.size) {
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .height(with(LocalDensity.current) { textStyle.fontSize.toDp() * 0.9f })
                                .graphicsLayer { this.alpha = cursorAlpha }
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            }
        }
    }
}

object T9Matcher {
    fun isMatch(contactName: String, query: String): Boolean {
        if (query.isEmpty()) return false

        val startIndices = mutableListOf(0)
        for (i in 0 until contactName.length - 1) {
            val c = contactName[i]
            if (c == ' ' || c == '-' || c == '.' || c == '_') {
                startIndices.add(i + 1)
            }
        }

        for (startIndex in startIndices) {
            var qIdx = 0
            var nIdx = startIndex

            while (qIdx < query.length && nIdx < contactName.length) {
                val nC = contactName[nIdx]
                if (nC == ' ' || nC == '-' || nC == '.' || nC == '_') {
                    nIdx++
                    continue
                }

                if (charToT9(nC) != query[qIdx]) {
                    break
                }
                qIdx++
                nIdx++
            }

            if (qIdx == query.length) return true
        }

        return false
    }

    private fun charToT9(c: Char): Char = when (c.uppercaseChar()) {
        'A', 'B', 'C' -> '2'
        'D', 'E', 'F' -> '3'
        'G', 'H', 'I' -> '4'
        'J', 'K', 'L' -> '5'
        'M', 'N', 'O' -> '6'
        'P', 'Q', 'R', 'S' -> '7'
        'T', 'U', 'V' -> '8'
        'W', 'X', 'Y', 'Z' -> '9'
        '0' -> '0'
        '1' -> '1'
        '2' -> '2'
        '3' -> '3'
        '4' -> '4'
        '5' -> '5'
        '6' -> '6'
        '7' -> '7'
        '8' -> '8'
        '9' -> '9'
        else -> ' '
    }
}

private fun buildDtmfSoundPool(context: Context): SoundPool {
    val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()
    return SoundPool.Builder().setMaxStreams(1).setAudioAttributes(attributes).build()
}

private fun playDtmf(context: Context, key: String, soundPool: SoundPool) {
    val toneType = when (key) {
        "0" -> android.media.ToneGenerator.TONE_DTMF_0
        "1" -> android.media.ToneGenerator.TONE_DTMF_1
        "2" -> android.media.ToneGenerator.TONE_DTMF_2
        "3" -> android.media.ToneGenerator.TONE_DTMF_3
        "4" -> android.media.ToneGenerator.TONE_DTMF_4
        "5" -> android.media.ToneGenerator.TONE_DTMF_5
        "6" -> android.media.ToneGenerator.TONE_DTMF_6
        "7" -> android.media.ToneGenerator.TONE_DTMF_7
        "8" -> android.media.ToneGenerator.TONE_DTMF_8
        "9" -> android.media.ToneGenerator.TONE_DTMF_9
        "*" -> android.media.ToneGenerator.TONE_DTMF_S
        "#" -> android.media.ToneGenerator.TONE_DTMF_P
        else -> return
    }
    try {
        val toneGen = android.media.ToneGenerator(android.media.AudioManager.STREAM_DTMF, 80)
        toneGen.startTone(toneType, 150)
        android.os.Handler(android.os.Looper.getMainLooper())
            .postDelayed({ toneGen.release() }, 200)
    } catch (_: Exception) {
    }
}

@Composable
private fun FadeScaleBox(modifier: Modifier, visible: Boolean, content: @Composable () -> Unit) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut()
        ) {
            content()
        }
    }
}
