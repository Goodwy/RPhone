package dev.goodwy.rphone.view.screen.settings

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.Surface
import androidx.biometric.BiometricManager
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.controller.util.PreferenceManager
import androidx.compose.ui.window.Dialog
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Deselect
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PersonOff
import androidx.compose.material.icons.rounded.PhonePaused
import androidx.compose.material.icons.rounded.Pin
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.DialogProperties
import dev.goodwy.rphone.cardCornerMedium
import dev.goodwy.rphone.view.components.NavigationIcon
import dev.goodwy.rphone.view.components.RillExpressiveCard
import dev.goodwy.rphone.view.components.RillListItem
import dev.goodwy.rphone.view.components.RillSwitchListItem
import dev.goodwy.rphone.view.theme.MyColors.cardColor
import dev.goodwy.rphone.view.theme.customColors
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.goodwy.rphone.R
import dev.goodwy.rphone.cardCornerBig
import dev.goodwy.rphone.cardCornerSmall
import dev.goodwy.rphone.modal.data.Contact
import dev.goodwy.rphone.modal.`interface`.IContactsRepository
import dev.goodwy.rphone.view.components.RillAvatar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun BiometricScreen(navigator: DestinationsNavigator) {
    val prefs: PreferenceManager = koinInject()
    val contactsRepo: IContactsRepository = koinInject()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var biometricsType by remember { mutableStateOf(prefs.getString(PreferenceManager.KEY_BIOMETRICS_TYPE, "") ?: "") }
    var appLockEnabled by remember { mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_BIOMETRICS_APP_LOCK, false)) }
    var callLockEnabled by remember { mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_BIOMETRICS_CALL_LOCK, false)) }
    var callLockMode by remember { mutableStateOf(prefs.getString(PreferenceManager.KEY_BIOMETRICS_CALL_LOCK_MODE, "all") ?: "all") }
    var callLockNumbers by remember { mutableStateOf(prefs.getString(PreferenceManager.KEY_BIOMETRICS_CALL_LOCK_NUMBERS, "") ?: "") }
    var showContactPicker by remember { mutableStateOf(false) }
    var allContacts by remember { mutableStateOf(emptyList<Contact>()) }

    LaunchedEffect(Unit) {
        allContacts = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            contactsRepo.getContacts()
        }
    }

    val selectedNumbers = remember(callLockNumbers) {
        if (callLockNumbers.isBlank()) emptySet()
        else callLockNumbers.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }

    val selectedContactCount = remember(selectedNumbers, allContacts) {
        allContacts.count { c -> c.phoneNumbers.any { n -> selectedNumbers.any { s -> n.filter(Char::isDigit).takeLast(10) == s.filter(Char::isDigit).takeLast(10) } } }
    }

    var showTypeSheet by remember { mutableStateOf(false) }
    var showPinSetup by remember { mutableStateOf(false) }
    var showPasswordSetup by remember { mutableStateOf(false) }
    var isClosing by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }

    val alpha by animateFloatAsState(
        targetValue = if (visible && !isClosing) 1f else 0f,
        animationSpec = if (isClosing) tween(260) else tween(320),
        label = "alpha"
    )
    val offsetY by animateDpAsState(
        targetValue = if (visible && !isClosing) 0.dp else if (isClosing) 40.dp else 24.dp,
        animationSpec = if (isClosing) tween(270) else spring(stiffness = Spring.StiffnessMediumLow),
        label = "offsetY"
    )
    LaunchedEffect(Unit) { visible = true }

    fun navigateBack() {
        isClosing = true
        scope.launch { delay(260); navigator.navigateUp() }
    }

    val systemBiometricsAvailable = remember {
        val bm = BiometricManager.from(context)
        bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
                BiometricManager.BIOMETRIC_SUCCESS
    }

    val typeLabel = when (biometricsType) {
        "system" -> "System Biometrics"
        "pin"    -> "Custom PIN"
        "password" -> "Custom Password"
        else     -> "Not Set"
    }

    val rotation =
        (context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager).defaultDisplay.rotation
    val isRotation90 = rotation == Surface.ROTATION_90
    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets.systemBars.only(
                    if (isRotation90) WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                    else WindowInsetsSides.Top
                ),
                title = { Text("Authentication", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = {
                    NavigationIcon(onClick = { navigateBack() })
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
//                .padding(padding)
                .padding(
                    top = innerPadding.calculateTopPadding(),
                    start = 0.dp,
                    end = 0.dp,
                    bottom = 0.dp
                )
                .alpha(alpha)
                .offset(y = offsetY),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Authentication Method card ─────────────────────────────────
            item {
//                SettingsSectionLabel("Authentication Method")
                RillExpressiveCard {
                    RillListItem(
                        headline = "Authentication Method",
                        supporting = typeLabel,
                        leadingIcon = Icons.Rounded.Fingerprint,
                        iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkRed,
                        iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorRed,
                        trailingIcon = Icons.Default.ChevronRight,
                        onClick = { showTypeSheet = true }
                    )
                }
            }

            // ── Toggles — only visible when a method is configured ─────────
            item {
                AnimatedVisibility(
                    visible = biometricsType.isNotEmpty(),
                    enter = fadeIn(tween(300)) + expandVertically(tween(300)),
                    exit = fadeOut(tween(200)) + shrinkVertically(tween(200))
                ) {
                    Column {
                        SettingsSectionLabel("Authentication")
                        RillExpressiveCard {
                            RillSwitchListItem(
                                headline = "Lock App on Open",
                                supporting = "Require authentication when opening Rill Phone",
                                leadingIcon = Icons.Rounded.LockOpen,
                                iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkPurple,
                                iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorPurple,
                                checked = appLockEnabled,
                                onCheckedChange = {
                                    appLockEnabled = it
                                    prefs.setBoolean(PreferenceManager.KEY_BIOMETRICS_APP_LOCK, it)
                                }
                            )
                            RillSwitchListItem(
                                headline = "Lock Call Actions",
                                supporting = "Require authentication to answer or reject incoming calls",
                                leadingIcon = Icons.Rounded.PhonePaused,
                                iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkGreen,
                                iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorGreen,
                                checked = callLockEnabled,
                                onCheckedChange = {
                                    callLockEnabled = it
                                    prefs.setBoolean(PreferenceManager.KEY_BIOMETRICS_CALL_LOCK, it)

                                }
                            )
                        }
                    }
                }
            }

            item {
                AnimatedVisibility(
                    visible = biometricsType.isNotEmpty() && callLockEnabled,
                    enter = fadeIn(tween(250)) + expandVertically(tween(250)),
                    exit  = fadeOut(tween(200)) + shrinkVertically(tween(200))
                ) {
                    Column {
                        SettingsSectionLabel("Lock Scope")
                        RillExpressiveCard {
                            // Modern segmented control
//                            SingleChoiceSegmentedButtonRow(
//                                modifier = Modifier
//                                    .background(
//                                        color = cardColor,
//                                        shape = RoundedCornerShape(cardCornerSmall)
//                                    )
//                                    .padding(12.dp)
//                                    .fillMaxWidth()
//                            ) {
//                                SegmentedButton(
//                                    selected = callLockMode == "all",
//                                    onClick = {
//                                        callLockMode = "all"
//                                        prefs.setString(PreferenceManager.KEY_BIOMETRICS_CALL_LOCK_MODE, "all")
//                                    },
//                                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
//                                    icon = { SegmentedButtonDefaults.ActiveIcon() }
//                                ) { Text("All Calls", maxLines = 1) }
//                                SegmentedButton(
//                                    selected = callLockMode == "specified",
//                                    onClick = {
//                                        callLockMode = "specified"
//                                        prefs.setString(PreferenceManager.KEY_BIOMETRICS_CALL_LOCK_MODE, "specified")
//                                        showContactPicker = true
//                                    },
//                                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
//                                    icon = { SegmentedButtonDefaults.ActiveIcon() }
//                                ) { Text("Specified", maxLines = 1) }
//                                SegmentedButton(
//                                    selected = callLockMode == "skip_specified",
//                                    onClick = {
//                                        callLockMode = "skip_specified"
//                                        prefs.setString(PreferenceManager.KEY_BIOMETRICS_CALL_LOCK_MODE, "skip_specified")
//                                        showContactPicker = true
//                                    },
//                                    shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
//                                    icon = { SegmentedButtonDefaults.ActiveIcon() }
//                                ) { Text("Exclude", maxLines = 1) }
//                            }

                            data class CallLockMode(val key: String, val label: String)
                            val callLockModes = listOf(
                                CallLockMode("all",            "All Calls"),
                                CallLockMode("specified",      "Specified"),
                                CallLockMode("skip_specified", "Exclude")
                            )
                            Box(
                                modifier = Modifier
                                    .background(
                                        color = cardColor,
                                        shape = RoundedCornerShape(cardCornerSmall)
                                    )
                                    .padding(12.dp)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    callLockModes.forEachIndexed { index, mode ->
                                        val selected = callLockMode == mode.key
                                        val interactionSource =
                                            remember { MutableInteractionSource() }
                                        val isPressed by interactionSource.collectIsPressedAsState()
                                        val cornerRadius by animateDpAsState(
                                            targetValue = if (isPressed || selected) 20.dp else 8.dp,
                                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                            label = "ButtonShape"
                                        )
                                        val outsideCornerRadius by animateDpAsState(
                                            targetValue = if (isPressed || selected) 20.dp else 16.dp,
                                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                            label = "ButtonShape"
                                        )
                                        val shape = remember(cornerRadius, index) {
                                            when (index) {
                                                0 -> RoundedCornerShape(
                                                    topStart = outsideCornerRadius,
                                                    topEnd = cornerRadius,
                                                    bottomEnd = cornerRadius,
                                                    bottomStart = outsideCornerRadius
                                                )

                                                2 -> RoundedCornerShape(
                                                    topStart = cornerRadius,
                                                    topEnd = outsideCornerRadius,
                                                    bottomEnd = outsideCornerRadius,
                                                    bottomStart = cornerRadius
                                                )

                                                else -> RoundedCornerShape(cornerRadius)
                                            }
                                        }

                                        Surface(
                                            onClick = {
                                                callLockMode = mode.key
                                                prefs.setString(PreferenceManager.KEY_BIOMETRICS_CALL_LOCK_MODE, mode.key)
                                                if (mode.key != "all") showContactPicker = true
                                            },
                                            shape = shape,
                                            color = if (selected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.surfaceContainerHigh,
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(38.dp),
                                            interactionSource = interactionSource,
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    mode.label,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (selected) MaterialTheme.colorScheme.onPrimary
                                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Description card
                            AnimatedContent(targetState = callLockMode, label = "lockModeDesc") { mode ->
                                Row(
                                    modifier = Modifier
                                        .combinedClickable(
                                            enabled = callLockMode != "all",
                                            onClick = { showContactPicker = true },
                                        )
                                        .background(
                                            color = cardColor,
                                            shape = RoundedCornerShape(cardCornerSmall)
                                        )
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
//                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = when (mode) {
                                            "all" -> MaterialTheme.colorScheme.customColors.colorRed
                                            "specified" -> MaterialTheme.colorScheme.customColors.colorOrange
                                            else -> MaterialTheme.colorScheme.customColors.colorGreen
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = when (mode) {
                                                    "all" -> Icons.Rounded.Lock
                                                    "specified" -> Icons.Rounded.Person
                                                    else -> Icons.Rounded.PersonOff
                                                },
                                                contentDescription = null,
                                                tint = when (mode) {
                                                    "all" -> MaterialTheme.colorScheme.customColors.colorDarkRed
                                                    "specified" -> MaterialTheme.colorScheme.customColors.colorDarkOrange
                                                    else -> MaterialTheme.colorScheme.customColors.colorDarkGreen
                                                },
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Text(
                                        modifier = Modifier.weight(1f),
                                        text = when (mode) {
                                            "all" -> "Biometric required for every incoming call"
                                            "specified" -> if (selectedContactCount > 0)
                                                "$selectedContactCount contact${if (selectedContactCount != 1) "s" else ""} will require biometric to answer"
                                            else "Choose contacts that require biometric to answer"
                                            else -> if (selectedContactCount > 0)
                                                "$selectedContactCount contact${if (selectedContactCount != 1) "s" else ""} excluded from biometric lock"
                                            else "Choose contacts to skip the biometric lock"
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (mode != "all") {
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Icon(
                                            Icons.Rounded.Edit, null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                }
                            }

                            // Edit selection button
//                            AnimatedVisibility(
//                                visible = callLockMode != "all",
//                                enter = fadeIn(tween(200)) + expandVertically(tween(200)),
//                                exit  = fadeOut(tween(150)) + shrinkVertically(tween(150))
//                            ) {
//                                FilledTonalButton(
//                                    onClick = { showContactPicker = true },
//                                    modifier = Modifier.fillMaxWidth(),
//                                    shape = RoundedCornerShape(cardCornerSmall)
//                                ) {
//                                    Icon(Icons.Rounded.Edit, null, modifier = Modifier.size(16.dp))
//                                    Spacer(Modifier.width(8.dp))
//                                    Text(
//                                        if (selectedContactCount > 0)
//                                            "Edit Selection  ·  $selectedContactCount selected"
//                                        else "Select Contacts"
//                                    )
//                                }
//                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }

    // ── Type Chooser Bottom Sheet ──────────────────────────────────────────
    if (showTypeSheet) {
        BiometricTypeSheet(
            systemAvailable = systemBiometricsAvailable,
            currentType = biometricsType,
            onSelect = { type ->
                showTypeSheet = false
                when (type) {
                    "system" -> {
                        biometricsType = "system"
                        prefs.setString(PreferenceManager.KEY_BIOMETRICS_TYPE, "system")
                        
                    }
                    "pin"      -> showPinSetup = true
                    "password" -> showPasswordSetup = true
                    ""         -> {
                        biometricsType = ""
                        prefs.setString(PreferenceManager.KEY_BIOMETRICS_TYPE, "")
                        prefs.setString(PreferenceManager.KEY_BIOMETRICS_PIN, "")
                        prefs.setString(PreferenceManager.KEY_BIOMETRICS_PASSWORD, "")
                        prefs.setBoolean(PreferenceManager.KEY_BIOMETRICS_APP_LOCK, false)
                        prefs.setBoolean(PreferenceManager.KEY_BIOMETRICS_CALL_LOCK, false)
                        appLockEnabled = false; callLockEnabled = false
                        
                    }
                }
            },
            onDismiss = { showTypeSheet = false }
        )
    }

    if (showPinSetup) {
        PinSetupDialog(
            onConfirm = { pin ->
                biometricsType = "pin"
                prefs.setString(PreferenceManager.KEY_BIOMETRICS_TYPE, "pin")
                prefs.setString(PreferenceManager.KEY_BIOMETRICS_PIN, pin)
                
                showPinSetup = false
            },
            onDismiss = { showPinSetup = false }
        )
    }

    if (showPasswordSetup) {
        PasswordSetupDialog(
            onConfirm = { password ->
                biometricsType = "password"
                prefs.setString(PreferenceManager.KEY_BIOMETRICS_TYPE, "password")
                prefs.setString(PreferenceManager.KEY_BIOMETRICS_PASSWORD, password)
                
                showPasswordSetup = false
            },
            onDismiss = { showPasswordSetup = false }
        )
    }

    // ── Contact Picker ─────────────────────────────────────────────────────
    if (showContactPicker) {
        ContactPickerDialog(
            contacts = allContacts,
            initialSelectedNumbers = selectedNumbers,
            onDone = { newNumbers ->
                val joined = newNumbers.joinToString(",")
                callLockNumbers = joined
                prefs.setString(PreferenceManager.KEY_BIOMETRICS_CALL_LOCK_NUMBERS, joined)
                showContactPicker = false
            },
            onDismiss = { showContactPicker = false }
        )
    }
}

// ─── Contact Picker Dialog ────────────────────────────────────────────────────

@Composable
private fun ContactPickerDialog(
    contacts: List<Contact>,
    initialSelectedNumbers: Set<String>,
    onDone: (Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    // working copy — normalised to last-10-digit strings for matching
    var selectedNumbers by remember {
        mutableStateOf(initialSelectedNumbers.map { it.filter(Char::isDigit).takeLast(10) }.toSet())
    }
    var searchQuery by remember { mutableStateOf("") }

    fun normalise(n: String) = n.filter(Char::isDigit).takeLast(10)

    fun isContactSelected(contact: Contact) =
        contact.phoneNumbers.any { normalise(it) in selectedNumbers }

    fun toggleContact(contact: Contact) {
        val nums = contact.phoneNumbers.map(::normalise).filter { it.isNotEmpty() }.toSet()
        selectedNumbers = if (isContactSelected(contact)) selectedNumbers - nums
        else selectedNumbers + nums
    }

    fun selectAll() {
        selectedNumbers = contacts.flatMap { c -> c.phoneNumbers.map(::normalise) }.filter { it.isNotEmpty() }.toSet()
    }

    fun unselectAll() {
        selectedNumbers = emptySet()
    }

    val filteredContacts = remember(contacts, searchQuery) {
//        if (searchQuery.isBlank()) contacts.sortedBy { it.displayName.lowercase() }
//        else contacts.filter { c ->
//            c.displayName.contains(searchQuery, ignoreCase = true) ||
//                    c.phoneNumbers.any { it.contains(searchQuery) }
//        }.sortedBy { it.displayName.lowercase() }
        if (searchQuery.isBlank()) contacts.sortedBy { it.displayName.lowercase() }
        else contacts.filter {
            it.displayName.contains(searchQuery, ignoreCase = true) ||
                    it.nickname.contains(searchQuery, ignoreCase = true) ||
                    it.company.contains(searchQuery, ignoreCase = true) ||
                    it.jobTitle.contains(searchQuery, ignoreCase = true) ||
                    it.phoneNumbers.any { number -> number.replace(" ", "").replace("-", "").contains(searchQuery.replace(" ", "").replace("-", "")) } ||
                    it.emails.any { email -> email.value.replace(" ", "").contains(searchQuery.replace(" ", "")) } ||
                    it.addresses.any { address -> address.formattedAddress.replace(" ", "").contains(searchQuery.replace(" ", "")) } ||
                    it.events.any { event -> event.date.replace(" ", "").replace("-", "").replace(".", "")
                        .contains(searchQuery.replace(" ", "").replace("-", "").replace(".", "")) }
        }.sortedBy { it.displayName.lowercase() }
    }

    val selectedContactCount = contacts.count(::isContactSelected)

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 38.dp, topEnd = 38.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
//        scrimColor = Color.Transparent,
        contentWindowInsets = {
            if (isLandscape) {
                WindowInsets.systemBars.only(
                    WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                )
            } else BottomSheetDefaults.windowInsets
        },
        dragHandle = {
            if (isLandscape) null
            else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(3.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        modifier = Modifier.size(width = 36.dp, height = 4.dp)
                    ) {}
                }
            }
        },
        modifier = Modifier.statusBarsPadding()
    ) {
        Surface(
//            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxSize(),
            color = Color.Transparent
        ) {
            Scaffold(
                topBar = {
                    Column {
                        TopAppBar(
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                            title = { Text("Select Contacts", fontWeight = FontWeight.Bold) },
//                            navigationIcon = {
//                                NavigationIcon(onClick = onDismiss)
//                            },
                            actions = {
                                IconButton(onClick = ::selectAll) {
                                    Icon(Icons.Rounded.SelectAll, "Select All")
                                }
                                IconButton(onClick = ::unselectAll) {
                                    Icon(Icons.Rounded.Deselect, "Unselect All")
                                }
                                Spacer(modifier = Modifier.size(6.dp))
//                                TextButton(onClick = ::selectAll) {
//                                    Text("Select All", fontWeight = FontWeight.SemiBold)
//                                }
//                                TextButton(onClick = ::unselectAll) {
//                                    Text("Unselect All", fontWeight = FontWeight.SemiBold)
//                                }
                            }
                        )
                        // Search bar
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text(stringResource(R.string.search_contacts)) },
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .padding(start = 12.dp)
                                )
                            },
                            trailingIcon = {
                                AnimatedVisibility(visible = searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" },
                                        modifier = Modifier
                                            .padding(end = 4.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear",
                                            modifier = Modifier.size(18.dp))
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(28.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = cardColor,
                                unfocusedContainerColor = cardColor,
                                focusedBorderColor = cardColor,
                                unfocusedBorderColor = cardColor
                            )
                        )
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface
            ) { padding ->
                Box(Modifier.fillMaxSize()
                    .padding(
                        top = padding.calculateTopPadding(),
                        start = 0.dp,
                        end = 0.dp,
                        bottom = 0.dp
                    )
                ) {
                    if (contacts.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else if (filteredContacts.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.SearchOff, contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                                Text("No contacts found", color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    } else {
                        Surface(
                            color = Color.Transparent,
                            modifier = Modifier.padding(horizontal = 16.dp).fillMaxSize(),
                            shape = RoundedCornerShape(cardCornerBig),
                        ) {
                            LazyColumn(
//                                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                                contentPadding = PaddingValues(bottom = 96.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                items(filteredContacts, key = { it.id }) { contact ->
                                    val checked = isContactSelected(contact)
                                    val rowBg by animateColorAsState(
                                        if (checked) MaterialTheme.colorScheme.primaryContainer
                                        else cardColor,
                                        tween(180), label = "cb${contact.id}"
                                    )
                                    val interactionSource = remember { MutableInteractionSource() }
                                    val isPressed by interactionSource.collectIsPressedAsState()
                                    val cornerRadius by animateDpAsState(
                                        if (checked || isPressed) 50.dp else cardCornerSmall,
                                        spring(stiffness = Spring.StiffnessMediumLow),
                                        label = "cr${contact.id}"
                                    )
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                rowBg,
                                                shape = RoundedCornerShape(cornerRadius)
                                            )
                                            .clickable(
                                                indication = null,
                                                interactionSource = interactionSource,
                                                onClick = { toggleContact(contact) }
                                            )
                                            .padding(horizontal = 16.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                                    ) {
                                        RillAvatar(
                                            name = contact.displayName,
                                            photoUri = contact.photoUri,
                                            modifier = Modifier.size(46.dp)
                                        )
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                text = contact.displayName.ifBlank { "Unknown" },
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Normal,
                                                maxLines = 1,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                            )
                                            if (contact.phoneNumbers.isNotEmpty()) {
                                                Text(
                                                    text = contact.phoneNumbers.first(),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                        Checkbox(
                                            checked = checked,
                                            onCheckedChange = { toggleContact(contact) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ── Floating Done pill ─────────────────────────────────
//                    AnimatedVisibility(
//                        visible = selectedContactCount > 0,
//                        enter = slideInVertically { it } + fadeIn(tween(200)),
//                        exit  = slideOutVertically { it } + fadeOut(tween(150)),
//                        modifier = Modifier
//                            .align(Alignment.BottomCenter)
//                            .padding(bottom = 28.dp)
//                    ) {
                    val donePillAlpha by animateFloatAsState(
                        targetValue   = if (selectedContactCount > 0) 1f else 0f,
                        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
                        label         = "donePillFadeIn"
                    )
                        Button(
                            onClick = {
                                val finalNumbers = contacts
                                    .filter(::isContactSelected)
                                    .flatMap { c -> c.phoneNumbers.map { it.filter(Char::isDigit).takeLast(10) } }
                                    .filter { it.isNotEmpty() }
                                    .toSet()
                                onDone(finalNumbers)
                            },
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .graphicsLayer { alpha = donePillAlpha }
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 28.dp).height(52.dp),
                            elevation = ButtonDefaults.buttonElevation(
                                defaultElevation = 0.dp,
                                pressedElevation = 0.dp,
                                hoveredElevation = 0.dp
                            )
                        ) {
                            Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = "Done  ·  $selectedContactCount selected",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
//                    }
                }
            }
        }
    }
}

// ─── Biometric Type Selector Sheet ──────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BiometricTypeSheet(
    systemAvailable: Boolean,
    currentType: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            // Drag handle
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(3.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                    modifier = Modifier.size(width = 36.dp, height = 4.dp)
                ) {}
            }
            Spacer(modifier = Modifier.height(8.dp))

//            Text("Choose Method", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

            // System Biometrics option
            RillExpressiveCard(shape = RoundedCornerShape(cardCornerMedium)) {
                BiometricOptionRow(
                    icon = Icons.Rounded.Fingerprint,
                    iconTint = MaterialTheme.colorScheme.customColors.colorDarkPurple,
                    iconBgTint = MaterialTheme.colorScheme.customColors.colorPurple,
                    title = "System Biometrics",
                    subtitle = if (systemAvailable) "Fingerprint, face unlock, or device credentials"
                                else "Not available on this device",
                    isSelected = currentType == "system",
                    enabled = systemAvailable,
                    onClick = { if (systemAvailable) onSelect("system") }
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            SettingsSectionLabel("Custom Biometrics")
            RillExpressiveCard(shape = RoundedCornerShape(cardCornerMedium)) {
                BiometricOptionRow(
                    icon = Icons.Rounded.Pin,
                    iconTint = MaterialTheme.colorScheme.customColors.colorDarkBlue,
                    iconBgTint = MaterialTheme.colorScheme.customColors.colorBlue,
                    title = "PIN",
                    subtitle = "Set a numeric PIN of any length",
                    isSelected = currentType == "pin",
                    onClick = { onSelect("pin") }
                )

                BiometricOptionRow(
                    icon = Icons.Rounded.Key,
                    iconTint = MaterialTheme.colorScheme.customColors.colorDarkGreen,
                    iconBgTint = MaterialTheme.colorScheme.customColors.colorGreen,
                    title = "Password",
                    subtitle = "Set a custom alphanumeric password",
                    isSelected = currentType == "password",
                    onClick = { onSelect("password") }
                )
            }

            if (currentType.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    onClick = { onSelect("") },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Rounded.LockOpen, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Remove Biometric Lock", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun BiometricOptionRow(
    icon: ImageVector,
    iconTint: Color,
    iconBgTint: Color,
    title: String,
    subtitle: String,
    isSelected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val bg by animateColorAsState(
        if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        else cardColor,
        spring(stiffness = Spring.StiffnessMediumLow), label = "optBg"
    )
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(4.dp),
        color = bg,
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.4f)
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = iconBgTint,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = iconTint, modifier = Modifier.size(22.dp))
                }
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AnimatedVisibility(
                visible = isSelected,
                enter = scaleIn(spring(stiffness = Spring.StiffnessMedium)) + fadeIn(),
                exit  = scaleOut() + fadeOut()
            ) {
                Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
        }
    }
}

// ─── PIN Setup Dialog ────────────────────────────────────────────────────────

@Composable
fun PinDialogContent(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    title: String = "Set PIN",
    isVerify: Boolean = false,
    expectedPin: String = ""
) {
    var phase by remember { mutableIntStateOf(if (isVerify) 2 else 0) } // 0=enter, 1=confirm, 2=done
    var pin by remember { mutableStateOf("") }
    var firstPin by remember { mutableStateOf("") }
    var shakeState by remember { mutableIntStateOf(0) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    val shake by animateDpAsState(
        targetValue = 0.dp,
        animationSpec = keyframes {
            durationMillis = 400
            0.dp at 0; (-12).dp at 60; 12.dp at 120; (-8).dp at 200; 8.dp at 280; 0.dp at 400
        },
        label = "shake",
        finishedListener = { shakeState = 0 }
    )

    fun vibError() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator.vibrate(VibrationEffect.createOneShot(80, 180))
            } else {
                @Suppress("DEPRECATION")
                (context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator)
                    .vibrate(VibrationEffect.createOneShot(80, 180))
            }
        } catch (_: Exception) {}
    }

    fun vibSuccess() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator.vibrate(VibrationEffect.createOneShot(40, 120))
            } else {
                @Suppress("DEPRECATION")
                (context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator)
                    .vibrate(VibrationEffect.createOneShot(40, 120))
            }
        } catch (_: Exception) {}
    }

    fun onDigit(d: String) {
        if (pin.length >= 4) return
        pin += d
        errorMessage = null
    }

    fun onBackspace() {
        if (pin.isNotEmpty()) {
            pin = pin.dropLast(1)
            errorMessage = null
        }
    }

    fun onSubmit() {
        when {
            pin.length < 4 -> { shakeState++; vibError() }
            isVerify -> {
                if (pin == expectedPin) { vibSuccess(); onConfirm(pin) }
                else {
                    pin = ""
                    shakeState++
                    vibError()
                    errorMessage = "Incorrect PIN. Please try again."
                }
            }
            phase == 0 -> {
                firstPin = pin
                pin = ""
                phase = 1
                errorMessage = null
            }
            phase == 1 -> {
                if (pin == firstPin) {
                    vibSuccess()
                    onConfirm(pin)
                } else {
                    pin = ""
                    firstPin = ""
                    phase = 0
                    shakeState++
                    vibError()
                    errorMessage = "PINs don't match. Please start over."
                }
            }
        }
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    if (isLandscape) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                Modifier.weight(1f)
                    .padding(horizontal = 24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(32.dp)
            ) {
                // Header
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Surface(
                        modifier = Modifier.size(34.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = when {
                            isVerify -> title
                            phase == 0 -> "Set PIN"
                            else -> "Confirm PIN"
                        },
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (!isVerify) {
                    Text(
                        text = if (errorMessage != null) errorMessage ?: ""
                        else if (phase == 0) "Enter a 4-digit PIN"
                        else "Re-enter your PIN to confirm",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (errorMessage != null) MaterialTheme.colorScheme.error
                        else Color.White.copy(0.7f),
                        textAlign = TextAlign.Center
                    )
                }

                // PIN dots indicator
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.offset(x = if (shakeState > 0) shake else 0.dp)
                ) {
                    repeat(4) { i ->
                        val filled = i < pin.length
                        val scale by animateFloatAsState(
                            targetValue = if (filled) 1f else 0.6f,
                            animationSpec = spring(stiffness = Spring.StiffnessMedium),
                            label = "dot$i"
                        )
                        Box(
                            Modifier
                                .size(16.dp)
                                .scale(scale)
                                .clip(CircleShape)
                                .background(
                                    if (filled) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant
                                )
                        )
                    }
                }

                // Error message
                AnimatedVisibility(
                    visible = errorMessage != null && isVerify,
                    enter = fadeIn(animationSpec = tween(200)) + slideInVertically(
                        initialOffsetY = { -it / 2 },
                        animationSpec = tween(200)
                    ),
                    exit = fadeOut(animationSpec = tween(150)) + slideOutVertically(
                        targetOffsetY = { -it / 2 },
                        animationSpec = tween(150)
                    )
                ) {
                    Text(
                        text = errorMessage ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            Column(
                Modifier.weight(1f)
                    .padding(horizontal = 24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Numpad
                PinNumpad(
                    currentPin = pin,
                    onDigit = ::onDigit,
                    onBackspace = ::onBackspace,
                    onSubmit = ::onSubmit,
                    maxLength = 4,
                    buttonHeight = 56.dp
                )
                Spacer(modifier = Modifier.weight(1f))
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    TextButton(onClick = onDismiss) {
                        Text(
                            stringResource(R.string.cancel),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        }
    } else {
        Column(
            Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            Surface(
                modifier = Modifier.size(64.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
            Text(
                text = when {
                    isVerify -> title
                    phase == 0 -> "Set PIN"
                    else -> "Confirm PIN"
                },
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            if (!isVerify) {
                Text(
                    text = if (errorMessage != null) errorMessage ?: ""
                    else if (phase == 0) "Enter a 4-digit PIN"
                    else "Re-enter your PIN to confirm",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (errorMessage != null) MaterialTheme.colorScheme.error
                    else Color.White.copy(0.7f),
                    textAlign = TextAlign.Center
                )
            }

            // PIN dots indicator
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(vertical = 6.dp).offset(x = if (shakeState > 0) shake else 0.dp)
            ) {
                repeat(4) { i ->
                    val filled = i < pin.length
                    val scale by animateFloatAsState(
                        targetValue = if (filled) 1f else 0.6f,
                        animationSpec = spring(stiffness = Spring.StiffnessMedium),
                        label = "dot$i"
                    )
                    Box(
                        Modifier
                            .size(20.dp)
                            .scale(scale)
                            .clip(CircleShape)
                            .background(
                                if (filled) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant
                            )
                    )
                }
            }

            // Error message
            AnimatedVisibility(
                visible = errorMessage != null && isVerify,
                enter = fadeIn(animationSpec = tween(200)) + slideInVertically(
                    initialOffsetY = { -it / 2 },
                    animationSpec = tween(200)
                ),
                exit = fadeOut(animationSpec = tween(150)) + slideOutVertically(
                    targetOffsetY = { -it / 2 },
                    animationSpec = tween(150)
                )
            ) {
                Text(
                    text = errorMessage ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            // Numpad
            PinNumpad(
                currentPin = pin,
                onDigit = ::onDigit,
                onBackspace = ::onBackspace,
                onSubmit = ::onSubmit,
                maxLength = 4
            )

            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    }
}

@Composable
fun PinSetupDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    title: String = "Set PIN",
    isVerify: Boolean = false,
    expectedPin: String = "",
    showCloseButton: Boolean = true
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = showCloseButton,
            dismissOnClickOutside = showCloseButton
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize().systemBarsPadding().padding(16.dp),
            shape = RoundedCornerShape(28.dp),
            color = Color.Transparent,//MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Box(
                contentAlignment = Alignment.BottomCenter
            ) {
                PinDialogContent(
                    onConfirm = onConfirm,
                    onDismiss = onDismiss,
                    title = title,
                    isVerify = isVerify,
                    expectedPin = expectedPin
                )
            }
        }
    }
}

@Composable
private fun PinNumpad(
    currentPin: String,
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    onSubmit: () -> Unit,
    maxLength: Int = 4,
    buttonHeight: Dp = 62.dp
) {
    val keys = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("", "0", "⌫")
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        keys.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { key ->
                    val interaction = remember { MutableInteractionSource() }
                    val isPressed by interaction.collectIsPressedAsState()
                    val scale by animateFloatAsState(if (isPressed) 0.88f else 1f, spring(stiffness = Spring.StiffnessMedium), label = "ks")
                    val radius by animateDpAsState(if (isPressed) 14.dp else 22.dp, spring(stiffness = Spring.StiffnessMedium), label = "kr")

                    Box(Modifier.weight(1f)) {
                        when (key) {
                            "" -> {} // empty cell
                            "⌫" -> {
                                androidx.compose.animation.AnimatedVisibility(
                                    visible = currentPin.isNotEmpty(),
                                    enter = fadeIn() + scaleIn(),
                                    exit = fadeOut() + scaleOut()
                                ) {
                                    Surface(
                                        onClick = onBackspace,
                                        shape = RoundedCornerShape(radius),
                                        color = Color.Transparent,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(buttonHeight)
                                            .scale(scale),
                                        interactionSource = interaction
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.AutoMirrored.Rounded.Backspace, null,
                                                tint = Color.White.copy(0.7f),
                                                modifier = Modifier.size(24.dp))
                                        }
                                    }
                                }
                            }
                            else -> Surface(
                                onClick = {
                                    onDigit(key)
                                    // Automatic validation after entering a number of digits up to `maxLength`
                                    if (currentPin.length + 1 >= maxLength) {
                                        onSubmit()
                                    }
                                },
                                shape = RoundedCornerShape(radius),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(buttonHeight)
                                    .scale(scale),
                                interactionSource = interaction
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(key, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Password Setup Dialog ───────────────────────────────────────────────────

@Composable
fun PasswordDialogContent(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    title: String = "Set Password",
    isVerify: Boolean = false,
    expectedPassword: String = "",
    showCloseButton: Boolean = true
) {
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var showConfirm by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf("") }

    Column(
        Modifier
            .padding(24.dp)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(
                modifier = Modifier.padding(start = 4.dp),
                text = if (isVerify) title else "Set Password",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            if (showCloseButton) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            MaterialTheme.colorScheme.onSurface.copy(0.3f),
                            RoundedCornerShape(50)
                        )
                        .padding(2.dp)
                ) {
                    Icon(Icons.Rounded.Close, null)
                }
            }
        }

        if (!isVerify) {
            Text(
                "Enter any password. Supports letters, numbers and special characters.",
                modifier = Modifier.padding(horizontal = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        OutlinedTextField(
            value = password,
            onValueChange = { password = it; errorText = "" },
            label = { Text(if (isVerify) "Password" else "Enter Password") },
            singleLine = true,
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, null)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            isError = errorText.isNotEmpty()
        )

        if (!isVerify) {
            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it; errorText = "" },
                label = { Text("Confirm Password") },
                singleLine = true,
                visualTransformation = if (showConfirm) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { showConfirm = !showConfirm }) {
                        Icon(if (showConfirm) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, null)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                isError = errorText.isNotEmpty()
            )
        }

        AnimatedVisibility(visible = errorText.isNotEmpty()) {
            Text(errorText, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Row(Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, end = 1.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
            Button(
                onClick = {
                    when {
                        isVerify -> {
                            if (password == expectedPassword) onConfirm(password)
                            else errorText = "Incorrect password"
                        }
                        password.length < 4 -> errorText = "Password must be at least 4 characters"
                        password != confirmPassword -> errorText = "Passwords don't match"
                        else -> onConfirm(password)
                    }
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(50)
            ) { Text("Confirm") }
        }
    }
}

@Composable
fun PasswordSetupDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    title: String = "Set Password",
    isVerify: Boolean = false,
    expectedPassword: String = "",
    showCloseButton: Boolean = true
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = showCloseButton,
            dismissOnClickOutside = showCloseButton
        )
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            PasswordDialogContent(
                onConfirm = onConfirm,
                onDismiss = onDismiss,
                title = title,
                isVerify = isVerify,
                expectedPassword = expectedPassword,
                showCloseButton = showCloseButton
            )
        }
    }
}

