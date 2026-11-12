package dev.goodwy.rphone.view.screen.settings

import android.content.Context
import android.view.Surface
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.RememberMe
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.view.components.NavigationIcon
import dev.goodwy.rphone.view.components.RillAnimatedSection
import dev.goodwy.rphone.view.components.RillExpressiveCard
import dev.goodwy.rphone.view.theme.MyColors.cardColor
import dev.goodwy.rphone.view.theme.color_call_end
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.CallingCardOverridesScreenDestination
import com.ramcosta.composedestinations.generated.destinations.DonateScreenDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.goodwy.rphone.controller.PurchaseHelper
import dev.goodwy.rphone.controller.util.CallBackgroundStore
import dev.goodwy.rphone.controller.util.CallingCardStore
import dev.goodwy.rphone.controller.util.CallingCardStyle
import dev.goodwy.rphone.icons.background_replace
import dev.goodwy.rphone.view.components.HeadlineText
import dev.goodwy.rphone.view.components.RillIconBox
import dev.goodwy.rphone.view.components.RillListItem
import dev.goodwy.rphone.view.components.RillSwitchListItem
import dev.goodwy.rphone.view.components.SupportProjectItem
import dev.goodwy.rphone.view.components.SupportingText
import dev.goodwy.rphone.view.components.Title
import dev.goodwy.rphone.view.components.shake
import dev.goodwy.rphone.view.screen.CallScreenPreview
import dev.goodwy.rphone.view.screen.CallScreenPreviewStyle
import dev.goodwy.rphone.view.screen.CallingCardEditorDialog
import dev.goodwy.rphone.view.theme.customColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun CallerUIScreen(navigator: DestinationsNavigator) {
    val context = LocalContext.current
    val prefs = koinInject<PreferenceManager>()
    val scope = rememberCoroutineScope()
    val settingsState by prefs.settingsChanged.collectAsStateWithLifecycle()


    val purchaseHelper: PurchaseHelper = koinInject()
    val isPro by purchaseHelper.isPro.collectAsStateWithLifecycle()
    val proCheckDone by purchaseHelper.proCheckDone.collectAsStateWithLifecycle()
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

    var themeMode   by remember(settingsState) { mutableStateOf(prefs.getString(PreferenceManager.KEY_THEME_MODE, "auto") ?: "auto") }
    var hangupWidth by remember(settingsState) { mutableFloatStateOf(prefs.getFloat(PreferenceManager.KEY_HANGUP_WIDTH, 0.5f).coerceIn(0.1f, 1.0f)) }
    var hideAvatarWithBg          by remember(settingsState) { mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_HIDE_AVATAR_WITH_BACKGROUND, false)) }

    var defaultCallBg by remember { mutableStateOf<String?>(null) }
    var defaultCardStyle by remember { mutableStateOf(CallingCardStyle.ThemeDefault) }
    var hasDefaultCardStyle by remember { mutableStateOf(false) }
    var showCallingCardEditor by remember { mutableStateOf(false) }
    var loadingBg by remember { mutableStateOf(true) }
    LaunchedEffect(settingsState) {
        loadingBg = true
        defaultCallBg = CallBackgroundStore.defaultModelAsync(context)
        val storedStyle = CallingCardStore.peekDefault(context)
        hasDefaultCardStyle = storedStyle != null
        defaultCardStyle = storedStyle ?: CallingCardStyle.ThemeDefault
        loadingBg = false
    }
    var savingCallBg by remember { mutableStateOf(false) }

    if (showCallingCardEditor) {
        CallingCardEditorDialog(
            contactName = stringResource(R.string.label_unknown),
            phoneNumber = "",
            contactPhotoUri = null,
            initialBackgroundUri = defaultCallBg,
            initialStyle = defaultCardStyle,
            fallbackStyle = CallingCardStyle.ThemeDefault,
            onDismiss = { showCallingCardEditor = false },
            onDone = { result ->
                showCallingCardEditor = false
                scope.launch {
                    savingCallBg = true
                    if (result.backgroundUri != defaultCallBg) {
                        if (result.backgroundUri == null) {
                            CallBackgroundStore.clearDefault(context)
                            defaultCallBg = null
                        } else {
                            val ok = CallBackgroundStore.saveDefault(
                                context,
                                result.backgroundUri.toUri()
                            )
                            if (ok) {
                                defaultCallBg = CallBackgroundStore.defaultModelAsync(context)
                            }
                        }
                    }

                    val nextStyle = result.style
                    when {
                        nextStyle == null -> {
                            CallingCardStore.clearDefault(context)
                            defaultCardStyle = CallingCardStyle.ThemeDefault
                            hasDefaultCardStyle = false
                        }
                        // Done without customizing must not create a global style.
                        !hasDefaultCardStyle && nextStyle == CallingCardStyle.ThemeDefault -> Unit
                        nextStyle == defaultCardStyle -> Unit
                        else -> {
                            CallingCardStore.saveDefault(context, nextStyle)
                            defaultCardStyle = nextStyle
                            hasDefaultCardStyle = true
                        }
                    }
                    savingCallBg = false
                }
            }
        )
    }

    val rotation =
        (context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager).defaultDisplay.rotation
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val isRotation90 = rotation == if (isLtr) Surface.ROTATION_90 else Surface.ROTATION_270
    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets.systemBars.only(
                    if (isRotation90) WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                    else WindowInsetsSides.Top
                ),
                title = { Title(stringResource(R.string.сaller_ui)) },
                navigationIcon = {
                    NavigationIcon(onClick = { navigator.navigateUp() })
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        Box(modifier = Modifier
            .padding(
                top = padding.calculateTopPadding(),
                start = 0.dp,
                end = 0.dp,
                bottom = 0.dp
            )
            .fillMaxSize()
        ) {
            LazyColumn(
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

                item {
                    RillAnimatedSection(delayMs = 30L) {
                        Column {
                            SettingsSectionLabel(stringResource(R.string.contact_call_background))
                            RillExpressiveCard {
                                Column(
                                    modifier = Modifier
                                        .background(color = cardColor)
                                        .padding(16.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        RillIconBox(
                                            icon = Icons.Rounded.Wallpaper,
                                            iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkBlue,
                                            iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorBlue,
                                            shape = CircleShape,
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            HeadlineText(text = stringResource(R.string.settings_call_default_background))
                                            Spacer(Modifier.height(4.dp))
                                            SupportingText(
                                                text = if (defaultCallBg != null) {
                                                    stringResource(R.string.settings_call_default_background_set)
                                                } else {
                                                    stringResource(R.string.settings_call_default_background_none)
                                                }
                                            )
                                            if (hasDefaultCardStyle) {
                                                Spacer(Modifier.height(2.dp))
                                                SupportingText(text = stringResource(R.string.calling_card_style_customized))
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(16.dp))
                                    Box(
                                        modifier = Modifier
                                            .padding(horizontal = 2.dp)
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(20.dp))
                                            .height(220.dp)
                                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (loadingBg) {
                                            CircularProgressIndicator()
                                        } else if (defaultCallBg != null || hasDefaultCardStyle) {
                                            CallScreenPreview(
                                                contactName = stringResource(R.string.label_unknown),
                                                backgroundUri = defaultCallBg,
                                                style = CallScreenPreviewStyle.Default,
                                                callingCardStyle = defaultCardStyle,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Rounded.Wallpaper,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(40.dp)
                                            )
                                        }
                                        if (savingCallBg) {
                                            CircularProgressIndicator(color = MaterialTheme.colorScheme.onSurface)
                                        }
                                    }
                                    Spacer(Modifier.height(12.dp))
                                    Row(
                                        modifier = Modifier
                                            .padding(horizontal = 2.dp)
                                            .fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        val isDark = isSystemInDarkTheme()
                                        val borderColor =
                                            if (!isPro) {
                                                if (themeMode == "dark" || (themeMode == "auto" && isDark)) BorderStroke(
                                                    3.dp,
                                                    MaterialTheme.colorScheme.customColors.colorPurple
                                                )
                                                else BorderStroke(
                                                    3.dp,
                                                    MaterialTheme.colorScheme.customColors.colorDarkPurple.copy(
                                                        0.4f
                                                    )
                                                )
                                            } else null
                                        FilledTonalButton(
                                            onClick = {
                                                if (isPro) {
                                                    showCallingCardEditor = true
                                                } else {
                                                    enabledShake = true
                                                    showSnackbar = true
                                                    scope.launch {
                                                        delay(3000.milliseconds)
                                                        showSnackbar = false
                                                    }
                                                }
                                            },
                                            border = borderColor,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(
                                                Icons.Outlined.Edit,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                if (defaultCallBg != null) {
                                                    stringResource(R.string.contact_call_background_change)
                                                } else {
                                                    stringResource(R.string.contact_call_background_choose)
                                                },
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        val shouldShowClear = defaultCallBg != null || hasDefaultCardStyle
                                        if (shouldShowClear) {
                                            val clearLabel = if (defaultCallBg != null) {
                                                stringResource(R.string.contact_call_background_remove)
                                            } else {
                                                stringResource(R.string.clear_default)
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    scope.launch {
                                                        CallBackgroundStore.clearDefault(context)
                                                        CallingCardStore.clearDefault(context)
                                                        defaultCallBg = null
                                                        defaultCardStyle = CallingCardStyle.ThemeDefault
                                                        hasDefaultCardStyle = false
                                                    }
                                                }
                                            ) {
                                                Icon(
                                                    ImageVector.vectorResource(id = R.drawable.ic_delete),
                                                    contentDescription = clearLabel,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Text(
                                                    clearLabel,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                        }
                                    }
                                }
                                RillListItem(
                                    headline = stringResource(R.string.calling_card_overrides_title),
                                    supporting = stringResource(R.string.calling_card_overrides_subtitle),
                                    leadingIcon = background_replace,
                                    iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkPurple,
                                    iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorPurple,
                                    trailingIcon = Icons.Default.ChevronRight,
                                    onClick = { navigator.navigate(CallingCardOverridesScreenDestination) }
                                )
                                RillSwitchListItem(
                                    headline = stringResource(R.string.settings_interface_hide_avatar_with_bg),
                                    supporting = stringResource(R.string.settings_interface_hide_avatar_with_bg_supporting),
                                    leadingIcon = if (hideAvatarWithBg) Icons.Rounded.Wallpaper else Icons.Rounded.RememberMe,
                                    iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkAmber,
                                    iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorAmber,
                                    checked = hideAvatarWithBg,
                                    onCheckedChange = { hideAvatarWithBg = it; prefs.setBoolean(PreferenceManager.KEY_HIDE_AVATAR_WITH_BACKGROUND, it) }
                                )
                            }
                        }
                    }
                }

                // ── Hang Up Button ────────────────────────────────────────
                item {
                    RillAnimatedSection(delayMs = 60L) {
                        Column {
                            SettingsSectionLabel(stringResource(R.string.end_call_button))
                            RillExpressiveCard {
                                Column(
                                    modifier = Modifier
                                        .background(color = cardColor)
                                        .padding(16.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        RillIconBox(
                                            icon = Icons.Rounded.CallEnd,
                                            iconContainerColor = color_call_end,
                                            iconBgContainerColor = color_call_end.copy(alpha = 0.15f),
                                            shape = CircleShape,
                                        )
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight(),
                                            verticalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
                                            HeadlineText(text = stringResource(R.string.customize_width))
                                            SupportingText(text = stringResource(R.string.customize_width_subtitle))
                                        }
                                    }

                                    Spacer(Modifier.height(24.dp))

                                    // Live preview
                                    Box(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        val isCircle = hangupWidth <= 0.1f
                                        Surface(
                                            shape = if (isCircle) CircleShape else RoundedCornerShape(
                                                42.dp
                                            ),
                                            color = color_call_end,
                                            modifier = if (isCircle) Modifier.size(64.dp)
                                            else Modifier
                                                .fillMaxWidth(
                                                    hangupWidth.coerceIn(
                                                        0.1f,
                                                        1.0f
                                                    )
                                                )
                                                .height(64.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    val showText = hangupWidth > 0.5f
                                                    Icon(
                                                        Icons.Rounded.CallEnd,
                                                        null,
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

                                    Spacer(Modifier.height(16.dp))

                                    // Slider
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Slider(
                                            value = hangupWidth,
                                            onValueChange = { hangupWidth = it },
                                            onValueChangeFinished = {
                                                prefs.setFloat(
                                                    PreferenceManager.KEY_HANGUP_WIDTH,
                                                    hangupWidth
                                                )
                                            },
                                            valueRange = 0.1f..1.0f,
                                            steps = 8,
                                            modifier = Modifier.weight(1f),
                                            colors = SliderDefaults.colors(
                                                thumbColor = color_call_end,
                                                activeTrackColor = color_call_end,
                                                inactiveTrackColor = color_call_end.copy(alpha = 0.3f)
                                            )
                                        )
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            "10%",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            "${(hangupWidth * 100).toInt()}%",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.clickable(
                                                indication = ripple(
                                                    bounded = false,
                                                    radius = 24.dp
                                                ),
                                                interactionSource = null,
                                            ) {
                                                hangupWidth = 0.5f
                                            }
                                        )
                                        Text(
                                            "100%",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item { SettingsBottomPadding() }
            }

            AnimatedVisibility(
                modifier = Modifier
                    .align(Alignment.BottomCenter),
                visible = showSnackbar,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                Snackbar(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(24.dp),
                    shape = MaterialTheme.shapes.large,
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
