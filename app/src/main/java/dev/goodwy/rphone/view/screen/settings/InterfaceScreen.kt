package dev.goodwy.rphone.view.screen.settings

import android.app.Activity
import android.content.Context
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.controller.util.PreferenceManager
import android.os.Build
import android.view.Surface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.BlurOff
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FontDownload
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import dev.goodwy.rphone.cardCornerSmall
import dev.goodwy.rphone.view.components.RillAnimatedSection
import dev.goodwy.rphone.view.components.RillExpressiveCard
import dev.goodwy.rphone.view.components.RillListItem
import dev.goodwy.rphone.view.components.RillSwitchListItem
import dev.goodwy.rphone.view.theme.MyColors.cardColor
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import androidx.core.graphics.toColorInt
import com.ramcosta.composedestinations.generated.destinations.AvatarsPreferenceScreenDestination
import dev.goodwy.rphone.R
import dev.goodwy.rphone.cardCornerMedium
import dev.goodwy.rphone.controller.util.darken
import dev.goodwy.rphone.view.components.NavigationIcon
import dev.goodwy.rphone.view.components.RillIconBox
import dev.goodwy.rphone.view.components.RillSelectListItem
import dev.goodwy.rphone.view.components.SupportProjectItem
import dev.goodwy.rphone.view.components.shake
import dev.goodwy.rphone.view.theme.color_call_end
import dev.goodwy.rphone.view.theme.color_default_primary
import dev.goodwy.rphone.view.theme.customColors
import com.ramcosta.composedestinations.generated.destinations.DonateScreenDestination
import dev.goodwy.rphone.controller.PurchaseHelper
import dev.goodwy.rphone.view.components.Title
import kotlinx.coroutines.delay
import java.io.File
import kotlin.math.roundToInt

data class ThemeOption(val key: String, val label: String)

private fun triggerRestartPrompt(
    scope: CoroutineScope,
    snackbarHostState: SnackbarHostState,
    context: Context
) {
    scope.launch {
        val result = snackbarHostState.showSnackbar(
            message = "Restart required to apply theme changes fully.",
            actionLabel = "Restart",
            duration = SnackbarDuration.Short
        )
        if (result == SnackbarResult.ActionPerformed) {
            (context as? Activity)?.recreate()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun InterfaceScreen(navigator: DestinationsNavigator) {
    val prefs = koinInject<PreferenceManager>()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val keyboardController = LocalSoftwareKeyboardController.current

    val settingsState by prefs.settingsChanged.collectAsState()
    var themeMode           by remember(settingsState) { mutableStateOf(prefs.getString(PreferenceManager.KEY_THEME_MODE, "auto") ?: "auto") }
    var dynamicColors       by remember(settingsState) { mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_DYNAMIC_COLORS, true)) }
    var customPrimaryColor  by remember(settingsState) { mutableStateOf(prefs.getInt("custom_primary_color", color_default_primary.toArgb())) }
    var incomingCallUI      by remember(settingsState) { mutableStateOf(prefs.getInt(PreferenceManager.KEY_INCOMING_CALL_UI_MODE, 10)) }
    var scrollAnimation     by remember(settingsState) { mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_SCROLL_ANIMATION, false)) }
    var liquidGlass         by remember(settingsState) { mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_LIQUID_GLASS, false)) }
    var blurEffects         by remember(settingsState) { mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_BLUR_EFFECTS, false)) }

    // Call UI section checkboxes dialog
    var showCallUIDialog   by remember(settingsState) { mutableStateOf(false) }
    var callUIShowToday    by remember(settingsState) { mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_CALL_UI_SHOW_TODAY, true)) }
    var callUIShowMissed   by remember(settingsState) { mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_CALL_UI_SHOW_MISSED, true)) }
    var callUIShowOutgoing by remember(settingsState) { mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_CALL_UI_SHOW_OUTGOING, true)) }
    var callUIShowCallTime by remember(settingsState) { mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_CALL_UI_SHOW_CALL_TIME, true)) }

    var hexInput by remember(settingsState) { mutableStateOf(String.format("%06X", 0xFFFFFF and customPrimaryColor)) }
    var hexError by remember(settingsState) { mutableStateOf(false) }

    val purchaseHelper: PurchaseHelper = koinInject()
    val isPro by purchaseHelper.isPro.collectAsState()
    val proCheckDone by purchaseHelper.proCheckDone.collectAsState()
    LaunchedEffect(Unit) {
        val savedIsProIap = prefs.getBoolean(PreferenceManager.KEY_IS_PRO_IAP, false)
        val savedIsProSub = prefs.getBoolean(PreferenceManager.KEY_IS_PRO_SUB, false)
        val savedIsProFoss = prefs.getBoolean(PreferenceManager.KEY_IS_PRO_FOSS, false)
        if (savedIsProIap || savedIsProSub || savedIsProFoss) {
            purchaseHelper.setProStatusImmediate(true)
            purchaseHelper.checkProStatus()
        } else {
            purchaseHelper.checkProStatus()
        }
    }

    var enabledShake by remember { mutableStateOf(false) }
    var showSnackbar   by remember(settingsState) { mutableStateOf(false) }

    val themeOptions = listOf(
        ThemeOption("auto",    stringResource(R.string.theme_auto)),
        ThemeOption("light",   stringResource(R.string.theme_light)),
        ThemeOption("dark",    stringResource(R.string.theme_dark)),
        ThemeOption("auto_bw", stringResource(R.string.theme_auto_black_white)),
        ThemeOption("white",   stringResource(R.string.theme_white)),
        ThemeOption("black",   stringResource(R.string.theme_black))
    )

//    val presetColors2 = listOf(
//        Color(0xFF50D6FB), Color(0xFF74A6FF), Color(0xFFB18CFD), Color(0xFFD356FE),
//        Color(0xFFEE719F), Color(0xFFFF8E81), Color(0xFFFEA57C), Color(0xFFFEC679),
//        Color(0xFFFFD977), Color(0xFFFFF994), Color(0xFFEAF18F), Color(0xFFB0DC8B),
//        Color(0xFFB1C2A9), Color(0xFF9EB7BE), Color(0xFFB7A7B6), Color(0xFFB9B2A8)
//    )

    val presetColors = listOf(
        Color(0xFF78D3F7), Color(0xFF7FA5F7), Color(0xFFAB8DF6), Color(0xFFC45FF5),
        Color(0xFFDE789C), Color(0xFFEF9485), Color(0xFFF2A984), Color(0xFFF5C883),
        Color(0xFFF8DA85), Color(0xFFFDF9A0), Color(0xFFEAF19A), Color(0xFFBADB93),
        Color(0xFFB4C1AB), Color(0xFFA3B6BC), Color(0xFFB5A8B5), Color(0xFFB8B1A8)
    )

    fun applyHexColor(hex: String) {
        val cleaned = hex.trimStart('#').uppercase()
        if (cleaned.length == 6) {
            try {
                val colorInt = "#$cleaned".toColorInt()
                customPrimaryColor = colorInt
                prefs.setInt("custom_primary_color", colorInt)
                hexError = false
                triggerRestartPrompt(scope, snackbarHostState, context)
            } catch (_: Exception) { hexError = true }
        } else {
            hexError = true
        }
    }

    // ── Call UI Dialog ────────────────────────────────────────────────────────
    if (showCallUIDialog) {
        AlertDialog(
            onDismissRequest = { showCallUIDialog = false },
            icon = { Icon(Icons.Default.Dashboard, null, tint = MaterialTheme.colorScheme.customColors.colorCyan) },
            title = { Text("Call UI Elements") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Toggle which stat cards appear in the Calls home screen.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    listOf(
                        Triple("Today", callUIShowToday) { v: Boolean ->
                            callUIShowToday = v
                            prefs.setBoolean(PreferenceManager.KEY_CALL_UI_SHOW_TODAY, v)
                        },
                        Triple("Missed", callUIShowMissed) { v: Boolean ->
                            callUIShowMissed = v
                            prefs.setBoolean(PreferenceManager.KEY_CALL_UI_SHOW_MISSED, v)
                        },
                        Triple("Outgoing", callUIShowOutgoing) { v: Boolean ->
                            callUIShowOutgoing = v
                            prefs.setBoolean(PreferenceManager.KEY_CALL_UI_SHOW_OUTGOING, v)
                        },
                        Triple("Call Time", callUIShowCallTime) { v: Boolean ->
                            callUIShowCallTime = v
                            prefs.setBoolean(PreferenceManager.KEY_CALL_UI_SHOW_CALL_TIME, v)
                        }
                    ).forEach { (label, checked, onChange) ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = onChange,
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = MaterialTheme.colorScheme.primary,
                                        uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCallUIDialog = false }) { Text("Done") }
            }
        )
    }

    // Font state
    val savedFontPath = prefs.getString(PreferenceManager.KEY_CUSTOM_FONT_PATH, null)
    var hasFontSet   by remember { mutableStateOf(savedFontPath != null) }
    var fontSizeScale by remember { mutableFloatStateOf(prefs.getFloat(PreferenceManager.KEY_CUSTOM_FONT_SIZE, 1.0f)) }

    // Font picker
    val fontPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                try {
                    val fontFile = File(context.filesDir, "custom_font.ttf")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        fontFile.outputStream().use { output -> input.copyTo(output) }
                    }
                    prefs.setString(PreferenceManager.KEY_CUSTOM_FONT_PATH, fontFile.absolutePath)
                    hasFontSet = true
                    (context as? Activity)?.let { activity ->
                        val intent = activity.intent
                        activity.finish()
                        activity.startActivity(intent)
                    }
                } catch (_: Exception) {}
            }
        }
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
                title = { Title(stringResource(R.string.interface_settings)) },
                navigationIcon = {
                    NavigationIcon(onClick = { navigator.navigateUp() })
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        Box(modifier = Modifier
//            .padding(padding)
            .padding(
                top = padding.calculateTopPadding(),
                start = 0.dp,
                end = 0.dp,
                bottom = 0.dp
            )
            .fillMaxSize()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                if (!isPro && proCheckDone) {
                    item {
                        RillAnimatedSection(delayMs = 30L) {
                            SupportProjectItem(
                                modifier = Modifier.shake(enabledShake) { enabledShake = false },
                                onClick = { navigator.navigate(DonateScreenDestination) }
                            )
                        }
                    }
                }

                // ── App Theme ────────────────────────────────────────
                item {
                    RillAnimatedSection(delayMs = 0L) {
                        Column {
                            SettingsSectionLabel(stringResource(R.string.app_theme))
                            RillExpressiveCard {
                                Column(
                                    modifier = Modifier
                                        .background(
                                            color = cardColor,
                                            shape = RoundedCornerShape(cardCornerSmall)
                                        )
                                        .padding(12.dp)
                                ) {
                                    themeOptions.chunked(3).forEachIndexed { rowIndex, rowItems ->
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .then(
                                                    if (rowIndex == 0) Modifier.padding(bottom = 2.dp)
                                                    else Modifier
                                                )
                                        ) {
                                            rowItems.forEachIndexed { lineIndex, option ->
                                                val selected = themeMode == option.key
                                                val interactionSource = remember { MutableInteractionSource() }
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
                                                val shape = remember(cornerRadius, lineIndex) {
                                                    when {
                                                        rowIndex == 0 && lineIndex == 0  -> RoundedCornerShape(
                                                            topStart = outsideCornerRadius,
                                                            topEnd = cornerRadius,
                                                            bottomEnd = cornerRadius,
                                                            bottomStart = cornerRadius
                                                        )
                                                        rowIndex == 0 && lineIndex == 2 -> RoundedCornerShape(
                                                            topStart = cornerRadius,
                                                            topEnd = outsideCornerRadius,
                                                            bottomEnd = cornerRadius,
                                                            bottomStart = cornerRadius
                                                        )
                                                        rowIndex == 1 && lineIndex == 0 -> RoundedCornerShape(
                                                            topStart = cornerRadius,
                                                            topEnd = cornerRadius,
                                                            bottomEnd = cornerRadius,
                                                            bottomStart = outsideCornerRadius
                                                        )
                                                        rowIndex == 1 && lineIndex == 2 -> RoundedCornerShape(
                                                            topStart = cornerRadius,
                                                            topEnd = cornerRadius,
                                                            bottomEnd = outsideCornerRadius,
                                                            bottomStart = cornerRadius
                                                        )
                                                        else -> RoundedCornerShape(cornerRadius)
                                                    }
                                                }

                                                val borderColor =
                                                    if (rowIndex == 1 && !isPro) {
                                                        val isDark = isSystemInDarkTheme()
                                                        if (themeMode == "dark" || (themeMode == "auto" && isDark)) BorderStroke(3.dp, MaterialTheme.colorScheme.customColors.colorPurple)
                                                        else BorderStroke(3.dp, MaterialTheme.colorScheme.customColors.colorDarkPurple.copy(0.4f))
                                                    } else null
                                                Surface(
                                                    onClick = {
                                                        if (!isPro && rowIndex == 1) {
                                                            enabledShake = true
                                                            showSnackbar = true
                                                            scope.launch {
                                                                delay(3000)
                                                                showSnackbar = false
                                                            }
                                                        } else {
                                                            themeMode = option.key
                                                            prefs.setString(PreferenceManager.KEY_THEME_MODE, option.key)
                                                            triggerRestartPrompt(scope, snackbarHostState, context)
                                                        }
                                                    },
                                                    shape = shape,
                                                    color = if (selected) MaterialTheme.colorScheme.primary
                                                            else MaterialTheme.colorScheme.surfaceContainerHigh,
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .height(38.dp),
                                                    interactionSource = interactionSource,
                                                    border = borderColor,
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Text(
                                                            option.label,
                                                            style = MaterialTheme.typography.labelMedium,
                                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                                            color = if (selected) MaterialTheme.colorScheme.onPrimary
                                                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                            modifier = Modifier.padding(horizontal = 8.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                RillSwitchListItem(
                                    headline = stringResource(R.string.custom_color),
                                    supporting = stringResource(R.string.custom_color_subtitle),
                                    leadingIcon = Icons.Rounded.Palette,
                                    iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkCyan,
                                    iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorCyan,
                                    checked = !dynamicColors,
                                    onCheckedChange = {
                                        if (isPro) {
                                            dynamicColors = !it
                                            prefs.setBoolean(PreferenceManager.KEY_DYNAMIC_COLORS, !it)
                                            triggerRestartPrompt(scope, snackbarHostState, context)
                                        } else {
                                            enabledShake = true
                                            showSnackbar = true
                                            scope.launch {
                                                delay(3000)
                                                showSnackbar = false
                                            }
                                        }
                                    },
                                    modifier = Modifier.alpha(if (isPro) 1f else 0.4f)
                                )
                                if (!dynamicColors) {
                                    Column(modifier = Modifier
                                        .background(
                                            color = cardColor,
                                            shape = RoundedCornerShape(cardCornerSmall)
                                        )
                                        .padding(vertical = 12.dp)
                                    ) {
                                        LazyRow(
                                            contentPadding = PaddingValues(horizontal = 16.dp),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            items(presetColors) { color ->
                                                val currentColor = customPrimaryColor == color.toArgb()
                                                Box(
                                                    modifier = Modifier
                                                        .size(40.dp)
                                                        .clip(CircleShape)
                                                        .background(color)
                                                        .border(
                                                            width = if (currentColor) 3.dp else 2.dp,
                                                            color = if (currentColor) MaterialTheme.colorScheme.onSurface else color.darken(),
                                                            shape = CircleShape
                                                        )
                                                        .clickable {
                                                            customPrimaryColor = color.toArgb()
                                                            prefs.setInt(
                                                                "custom_primary_color",
                                                                color.toArgb()
                                                            )
                                                            hexInput = String.format(
                                                                "%06X",
                                                                0xFFFFFF and color.toArgb()
                                                            )
                                                            hexError = false
                                                            triggerRestartPrompt(
                                                                scope,
                                                                snackbarHostState,
                                                                context
                                                            )
                                                        }
                                                )
                                            }
                                        }
                                        Spacer(Modifier.height(16.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                                        ) {
                                            OutlinedTextField(
                                                value = hexInput,
                                                onValueChange = { v ->
                                                    hexInput = v.trimStart('#').uppercase().take(6)
                                                    hexError = false
                                                },
                                                label = { Text(stringResource(R.string.hex_color)) },
                                                prefix = { Text("#") },
                                                isError = hexError,
                                                singleLine = true,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .padding(bottom = 6.dp),
                                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                                keyboardActions = KeyboardActions(onDone = {
                                                    applyHexColor(hexInput)
                                                    keyboardController?.hide()
                                                }),
                                                shape = RoundedCornerShape(12.dp),
                                                supportingText = if (hexError) {{ Text(stringResource(R.string.hex_color_subtitle)) }} else null,
                                            )

                                            val interactionSource = remember { MutableInteractionSource() }
                                            val isPressed by interactionSource.collectIsPressedAsState()

                                            val cornerRadius by animateDpAsState(
                                                targetValue = if (isPressed) 12.dp else 50.dp,
                                                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                                label = "ButtonShape"
                                            )
                                            Button(
                                                modifier = Modifier.height(55.dp),
                                                onClick = {
                                                    applyHexColor(hexInput)
                                                    keyboardController?.hide()
                                                },
                                                shape = RoundedCornerShape(cornerRadius),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = try {
                                                        Color("#${hexInput.trimStart('#')}".toColorInt())
                                                    } catch (_: Exception) {
                                                        MaterialTheme.colorScheme.onSurface
                                                    }
                                                ),
                                                interactionSource = interactionSource
                                            ) {
                                                Icon(Icons.Rounded.Check, "Apply")
                                            }
                                        }
                                    }
                                }
                                Column(modifier = Modifier
                                    .clickable {
                                        if (isPro) {
                                            fontPickerLauncher.launch("font/ttf")
                                        } else {
                                            enabledShake = true
                                            showSnackbar = true
                                            scope.launch {
                                                delay(3000)
                                                showSnackbar = false
                                            }
                                        }
                                    }
                                    .alpha(if (isPro) 1f else 0.4f)
                                    .background(
                                        color = cardColor,
                                        shape = RoundedCornerShape(cardCornerSmall)
                                    )
                                    .padding(start = 12.dp, end = 6.dp, top = 12.dp, bottom = 12.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                        RillIconBox(
                                            icon = Icons.Rounded.FontDownload,
                                            iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkCyan,
                                            iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorCyan
                                        )
                                        Spacer(Modifier.width(16.dp))
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
                                            Text(
                                                stringResource(R.string.custom_font),
                                                style = MaterialTheme.typography.bodyLarge,
                                                lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
                                            )
                                            Text(
                                                if (savedFontPath != null) File(savedFontPath).name
                                                    else stringResource(R.string.custom_font_subtitle),
                                                style = MaterialTheme.typography.bodyMedium,
                                                lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (hasFontSet) {
                                            Spacer(Modifier.width(8.dp))
                                            IconButton(
                                                onClick = {
                                                    prefs.setString(PreferenceManager.KEY_CUSTOM_FONT_PATH, null)
                                                    prefs.setFloat(PreferenceManager.KEY_CUSTOM_FONT_SIZE, 1.0f)
//                                                    fontSizeScale = 1.0f
                                                    hasFontSet = false
                                                    val file = File(context.filesDir, "custom_font.ttf")
                                                    file.delete()
                                                    (context as? Activity)?.let { a ->
                                                        val intent = a.intent
                                                        a.finish()
                                                        a.startActivity(intent)
                                                    }
                                            }) { Icon(Icons.Default.Refresh, "Revert font", tint = color_call_end) }
                                        }
//                                        IconButton(onClick = { fontPickerLauncher.launch("font/ttf") }) {
//                                            Icon(Icons.Default.FolderOpen, "Pick font", tint = MaterialTheme.colorScheme.primary)
//                                        }
                                    }
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    modifier = Modifier
                                        .background(
                                            color = cardColor,
                                            shape = RoundedCornerShape(cardCornerSmall)
                                        )
                                        .padding(
                                            start = 12.dp,
                                            end = 16.dp,
                                            top = 12.dp,
                                            bottom = 12.dp
                                        )
                                ) {
                                    RillIconBox(
                                        icon = Icons.Rounded.FormatSize,
                                        iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkCyan,
                                        iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorCyan
                                    )
                                    Slider(
                                        value = fontSizeScale,
                                        onValueChange = { fontSizeScale = it },
                                        onValueChangeFinished = { prefs.setFloat(PreferenceManager.KEY_CUSTOM_FONT_SIZE, fontSizeScale) },
                                        valueRange = 0.8f..1.4f,
                                        steps = 11,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text("${(fontSizeScale * 100).roundToInt()}%",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.clickable(
                                            indication = ripple(bounded = false, radius = 32.dp),
                                            interactionSource = null,
                                        ) {
                                            fontSizeScale = 1.0f
                                            prefs.setFloat(PreferenceManager.KEY_CUSTOM_FONT_SIZE, fontSizeScale)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Liquid Glass ─────────────────────────────────────
                item {
                    RillAnimatedSection(delayMs = 80L) {
                        Column {
                            SettingsSectionLabel(stringResource(R.string.visual_effects))
                            RillExpressiveCard {
                                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                                    RillListItem(
                                        headline = stringResource(R.string.not_supported_on_this_device),
                                        supporting = stringResource(R.string.not_supported_on_this_device_subtitle),
                                        leadingIcon = Icons.Rounded.BlurOff,
                                        iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkRed,
                                        iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorRed,
                                        onClick = { }
                                    )
                                } else {
                                    RillSwitchListItem(
                                        headline = stringResource(R.string.material_liquid_you_glass),
                                        supporting = stringResource(R.string.material_liquid_you_glass_subtitle),
                                        leadingIcon = Icons.Outlined.Lens,
                                        iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkPink,
                                        iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorPink,
                                        checked = liquidGlass,
                                        onCheckedChange = {
                                            liquidGlass = it
                                            prefs.setBoolean(
                                                PreferenceManager.KEY_LIQUID_GLASS,
                                                it
                                            )
                                        }
                                    )
//                                    if (liquidGlass) {
//                                        RillListItem(
//                                            headline = "Elements to have liquid glass effect",
//                                            supporting = "Choose which UI elements use the liquid glass effect",
//                                            leadingIcon = Icons.Rounded.Layers,
//                                            iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkPink,
//                                            iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorPink,
//                                            trailingIcon = Icons.Default.ChevronRight,
//                                            onClick = {
//                                                navigator.navigate(com.ramcosta.composedestinations.generated.destinations.LiquidGlassElementsScreenDestination)
//                                            }
//                                        )
//                                    }

                                    if (!liquidGlass) {
                                        RillSwitchListItem(
                                            headline = stringResource(R.string.material_blur_effects),
                                            supporting = stringResource(R.string.material_blur_effects_subtitle),
                                            leadingIcon = Icons.Outlined.BlurCircular,
                                            iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkOrange,
                                            iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorOrange,
                                            checked = blurEffects,
                                            onCheckedChange = {
                                                blurEffects = it
                                                prefs.setBoolean(
                                                    PreferenceManager.KEY_BLUR_EFFECTS,
                                                    it
                                                )
                                            }
                                        )
//                                        if (blurEffects) {
//                                            RillListItem(
//                                                headline = "Elements to have blur effect",
//                                                supporting = "Choose which UI elements use the blur effect",
//                                                leadingIcon = Icons.Rounded.Layers,
//                                                iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkOrange,
//                                                iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorOrange,
//                                                trailingIcon = Icons.Default.ChevronRight,
//                                                onClick = {
//                                                    navigator.navigate(com.ramcosta.composedestinations.generated.destinations.BlurEffectsElementsScreenDestination)
//                                                }
//                                            )
//                                        }
                                    }
                                }
                                RillSwitchListItem(
                                    headline = stringResource(R.string.scroll_animation),
                                    supporting = stringResource(R.string.scroll_animation_device),
                                    leadingIcon = Icons.Outlined.Animation,
                                    iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkAmber,
                                    iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorAmber,
                                    checked = scrollAnimation,
                                    onCheckedChange = {
                                        scrollAnimation = it
                                        prefs.setBoolean(PreferenceManager.KEY_SCROLL_ANIMATION, it)
                                    }
                                )
                            }
                        }
                    }
                }

                // ── Call UI ───────────────────────────────────────────
                item {
                    RillAnimatedSection(delayMs = 100L) {
                        Column {
                            SettingsSectionLabel(stringResource(R.string.call_ui))
                            RillExpressiveCard {
                                RillSelectListItem(
                                    headline = stringResource(R.string.incoming_call_ui),
                                    supporting = stringResource(R.string.incoming_call_ui_subtitle),
                                    leadingIcon = Icons.Rounded.PhoneInTalk,
                                    iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkGreen,
                                    iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorGreen,
                                    options = listOf(
                                        "Default Swipe" to 10,
                                        "Horizontal Swipe" to 0,
                                        "Buttons" to 1,
                                        "Slide to Answer (iOS)" to 2,
                                        "Vertical Swipe" to 3
                                    ),
                                    selectedValue = incomingCallUI,
                                    onValueChange = {
                                        incomingCallUI = it
                                        prefs.setInt(PreferenceManager.KEY_INCOMING_CALL_UI_MODE, it)
                                    }
                                )

                                // ── Caller UI → separate page ─────────────────────────
                                RillListItem(
                                    headline = stringResource(R.string.сaller_ui),
                                    supporting = stringResource(R.string.сaller_ui_subtitle),
                                    leadingIcon = Icons.Rounded.Person,
                                    iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkGreen,
                                    iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorGreen,
                                    trailingIcon = Icons.Default.ChevronRight,
                                    onClick = { navigator.navigate(com.ramcosta.composedestinations.generated.destinations.CallerUIScreenDestination) }
                                )
//                                RillListItem(
//                                    headline = "Calls Section Elements",
//                                    supporting = "Toggle Today, Missed, Outgoing, Call Time cards",
//                                    leadingIcon = Icons.Default.Dashboard,
//                                    iconContainerColor = ColorDarkGreen,
//                                    iconBgContainerColor = ColorGreen,
//                                    trailingIcon = Icons.Default.ChevronRight,
//                                    onClick = { showCallUIDialog = true }
//                                )
                            }
                        }
                    }
                }

                // ── Avatars ──────────────────────────────────────────
                item {
                    RillAnimatedSection(delayMs = 160L) {
                        Column {
                            SettingsSectionLabel("Avatars")
                            RillExpressiveCard {
//                                RillSwitchListItem(
//                                    headline = "Show First Letter in Avatar",
//                                    supporting = "Displays letter when picture is missing",
//                                    leadingIcon = if (showFirstLetter) Icons.Rounded.Title else Icons.Rounded.AccountCircle,
//                                    iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkOliva,
//                                    iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorOliva,
//                                    checked = showFirstLetter,
//                                    onCheckedChange = { showFirstLetter = it; prefs.setBoolean(PreferenceManager.KEY_SHOW_FIRST_LETTER, it) }
//                                )
//                                RillSwitchListItem(
//                                    headline = "Use Colorful Avatars",
//                                    supporting = "Random colors based on contact name",
//                                    leadingIcon = Icons.Rounded.Palette,
//                                    iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkOliva,
//                                    iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorOliva,
//                                    checked = colorfulAvatars,
//                                    onCheckedChange = { colorfulAvatars = it; prefs.setBoolean(PreferenceManager.KEY_COLORFUL_AVATARS, it) }
//                                )
//                                RillSwitchListItem(
//                                    headline = "Avatar Frame",
//                                    supporting = "Show a border around the avatar",
//                                    leadingIcon = if (avatarFrame) ImageVector.vectorResource(id = R.drawable.ic_person_border) else ImageVector.vectorResource(id = R.drawable.ic_person_no_border),
//                                    iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkOliva,
//                                    iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorOliva,
//                                    checked = avatarFrame,
//                                    onCheckedChange = { avatarFrame = it; prefs.setBoolean(PreferenceManager.KEY_AVATAR_FRAME, it) }
//                                )
//                                RillSwitchListItem(
//                                    headline = "Show Picture in Avatar",
//                                    supporting = "Shows the contact picture if available",
//                                    leadingIcon = if (showPicture) Icons.Rounded.Portrait else Icons.Rounded.TextFields,
//                                    iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkOliva,
//                                    iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorOliva,
//                                    checked = showPicture,
//                                    onCheckedChange = { showPicture = it; prefs.setBoolean(PreferenceManager.KEY_SHOW_PICTURE, it) }
//                                )
                                RillListItem(
                                    headline = stringResource(R.string.avatars_settings),
                                    supporting = stringResource(R.string.avatars_settings_subtitle),
                                    leadingIcon = Icons.Rounded.AccountCircle,
                                    iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkBlue,
                                    iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorBlue,
                                    trailingIcon = Icons.Default.ChevronRight,
                                    onClick = { navigator.navigate(AvatarsPreferenceScreenDestination) }
                                )
                            }
                        }
                    }
                }

                // ── App Icon ─────────────────────────────────────────
//                item {
//                    Column {
//                        SettingsSectionLabel("App Icon")
//                        RillExpressiveCard {
//                            RillListItem(
//                                headline = "App Icon",
//                                supporting = "Choose the app icon displayed on your home screen",
//                                leadingIcon = Icons.Rounded.AppRegistration,
//                                iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkPurple,
//                                iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorPurple,
//                                onClick = {
//                                    navigator.navigate(com.ramcosta.composedestinations.generated.destinations.AppIconScreenDestination)
//                                }
//                            )
//                        }
//                    }
//                }

                item { Spacer(modifier = Modifier.height(20.dp).navigationBarsPadding()) }
            }

            AnimatedVisibility(
                modifier = Modifier
                    .align(Alignment.BottomCenter),
                visible = showSnackbar,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                Snackbar(
                    modifier = Modifier.navigationBarsPadding().padding(24.dp),
                    shape = RoundedCornerShape(cardCornerMedium),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    action = {
                        TextButton(
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                            onClick = {
                                showSnackbar = false
                                navigator.navigate(DonateScreenDestination)
                            }
                        ) {
                            Text(stringResource(R.string.continue_support), color = MaterialTheme.colorScheme.primary)
                        }
                    },
                ) {
                    Text(
                        stringResource(R.string.support_project_to_unlock),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
