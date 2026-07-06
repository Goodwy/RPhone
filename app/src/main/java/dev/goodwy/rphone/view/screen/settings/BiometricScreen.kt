package dev.goodwy.rphone.view.screen.settings

import android.content.Context
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
import androidx.compose.ui.Modifier
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.PhonePaused
import androidx.compose.material.icons.rounded.Pin
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.ui.graphics.vector.ImageVector
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun BiometricScreen(navigator: DestinationsNavigator) {
    val prefs: PreferenceManager = koinInject()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var biometricsType by remember { mutableStateOf(prefs.getString(PreferenceManager.KEY_BIOMETRICS_TYPE, "") ?: "") }
    var appLockEnabled by remember { mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_BIOMETRICS_APP_LOCK, false)) }
    var callLockEnabled by remember { mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_BIOMETRICS_CALL_LOCK, false)) }

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
                title = { Text("Biometrics", fontWeight = FontWeight.ExtraBold) },
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
                SettingsSectionLabel("Authentication Method")
                RillExpressiveCard {
                    RillListItem(
                        headline = "Biometric Method",
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
                        SettingsSectionLabel("Biometrics")
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
fun PinSetupDialog(
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

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Header
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        modifier = Modifier.padding(start = 4.dp),
                        text = when {
                            isVerify -> title
                            phase == 0 -> "Enter PIN"
                            else -> "Confirm PIN"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
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

                if (!isVerify) {
                    Text(
                        text = if (errorMessage != null) errorMessage ?: ""
                               else if (phase == 0) "Enter a 4-digit PIN"
                               else "Re-enter your PIN to confirm",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (errorMessage != null) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant,
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
                                .size(14.dp)
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
    maxLength: Int = 4
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
                                            .height(56.dp)
                                            .scale(scale),
                                        interactionSource = interaction
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.AutoMirrored.Rounded.Backspace, null, modifier = Modifier.size(20.dp))
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
                                    .height(56.dp)
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
fun PasswordSetupDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    title: String = "Set Password",
    isVerify: Boolean = false,
    expectedPassword: String = ""
) {
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var showConfirm by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
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
    }
}

