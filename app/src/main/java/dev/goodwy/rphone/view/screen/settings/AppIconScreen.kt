package dev.goodwy.rphone.view.screen.settings

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.view.Surface
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.view.components.NavigationIcon
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.DonateScreenDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.PurchaseHelper
import dev.goodwy.rphone.view.components.SupportProjectItem
import dev.goodwy.rphone.view.components.Title
import dev.goodwy.rphone.view.components.shake
import dev.goodwy.rphone.view.theme.MyColors.cardColor
import dev.goodwy.rphone.view.theme.RillShapeDefaults
import dev.goodwy.rphone.view.theme.color_call_end
import dev.goodwy.rphone.view.theme.customColors
import dev.goodwy.rphone.view.theme.rillCornerDp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import kotlin.time.Duration.Companion.milliseconds
import androidx.core.graphics.createBitmap

internal const val KEY_SELECTED_APP_ICON = "selected_app_icon"

data class AppIconEntry(
    val key: String,
    val label: String,
    val aliasName: String?,
    @DrawableRes val previewRes: Int
)

@SuppressLint("DiscouragedApi")
internal fun buildIcons(context: Context) = listOf(
    AppIconEntry("default",  "Default", "MainActivityDefaultIcon",      context.resources.getIdentifier("ic_launcher",              "mipmap", context.packageName)),
    AppIconEntry("one",      "One",     "MainActivityOneIcon",          context.resources.getIdentifier("ic_launcher_one",          "mipmap", context.packageName)),
    AppIconEntry("two",      "Two",     "MainActivityTwoIcon",          context.resources.getIdentifier("ic_launcher_two",          "mipmap", context.packageName)),
    AppIconEntry("three",    "Three",   "MainActivityThreeDialerIcon",  context.resources.getIdentifier("ic_launcher_three",        "mipmap", context.packageName)),
    AppIconEntry("four",     "Four",    "MainActivityFourIcon",         context.resources.getIdentifier("ic_launcher_four",         "mipmap", context.packageName)),
    AppIconEntry("five",     "Five",    "MainActivityFiveIcon",         context.resources.getIdentifier("ic_launcher_five",         "mipmap", context.packageName)),
    AppIconEntry("greenWhite", "Green White", "MainActivityGreenWhiteIcon", context.resources.getIdentifier("ic_launcher_green_white", "mipmap", context.packageName)),
    AppIconEntry("green",      "Green",       "MainActivityGreenIcon",      context.resources.getIdentifier("ic_launcher_green",       "mipmap", context.packageName)),
    AppIconEntry("greenDark",  "Green Dark",  "MainActivityGreenDarkIcon",  context.resources.getIdentifier("ic_launcher_green_dark",  "mipmap", context.packageName)),
    AppIconEntry("blueWhite", "Blue White", "MainActivityBlueWhiteIcon", context.resources.getIdentifier("ic_launcher_blue_white", "mipmap", context.packageName)),
    AppIconEntry("blue",      "Blue",       "MainActivityBlueIcon",      context.resources.getIdentifier("ic_launcher_blue",       "mipmap", context.packageName)),
    AppIconEntry("blueDark",  "Blue Dark",  "MainActivityBlueDarkIcon",  context.resources.getIdentifier("ic_launcher_blue_dark",  "mipmap", context.packageName)),
)

// Curated App Name presets. "default" reuses the same alias as the Default app icon in
// combination with whatever icon is currently selected — see aliasNameFor() below, which
// computes a combined icon+name alias so changing one never resets the other.
@SuppressLint("DiscouragedApi")
internal fun buildAppNamePresets(context: Context) = listOf(
    AppIconEntry("default",  context.getString(R.string.app_launcher_name), null, context.resources.getIdentifier("ic_launcher", "mipmap", context.packageName)),
    AppIconEntry("app_name", context.getString(R.string.app_name), null, context.resources.getIdentifier("ic_launcher", "mipmap", context.packageName)),
)

private fun iconSuffix(iconKey: String): String = when (iconKey) {
    "default"            -> "Default"
    "one"                -> "One"
    "two"                -> "Two"
    "three"              -> "Three"
    "four"               -> "Four"
    "five"               -> "Five"
    "greenWhite"         -> "GreenWhite"
    "green"              -> "Green"
    "greenDark"          -> "GreenDark"
    "blueWhite"          -> "BlueWhite"
    "blue"               -> "Blue"
    "blueDark"           -> "BlueDark"
    else                 -> "Default"
}

private fun plainIconAliasName(iconKey: String): String = when (iconKey) {
    "default"            -> "MainActivityDefaultIcon"
    "one"                -> "MainActivityOneIcon"
    "two"                -> "MainActivityTwoIcon"
    "three"              -> "MainActivityThreeDialerIcon"
    "four"               -> "MainActivityFourIcon"
    "five"               -> "MainActivityFiveIcon"
    "greenWhite"         -> "MainActivityGreenWhiteIcon"
    "green"              -> "MainActivityGreenIcon"
    "greenDark"          -> "MainActivityGreenDarkIcon"
    "blueWhite"          -> "MainActivityBlueWhiteIcon"
    "blue"               -> "MainActivityBlueIcon"
    "blueDark"           -> "MainActivityBlueDarkIcon"
    else                 -> "MainActivityDefaultIcon"
}

private fun nameSuffix(nameKey: String): String? = when (nameKey) {
    "app_name"       -> "App"
    else             -> null // "default" — no separate name alias needed
}

/** Resolves the exact activity-alias short name for a given (icon, name) combination. */
internal fun aliasNameFor(iconKey: String, nameKey: String): String {
    val nSuffix = nameSuffix(nameKey) ?: return plainIconAliasName(iconKey)
    return "MainActivityName$nSuffix${iconSuffix(iconKey)}"
}

private val ALL_ICON_KEYS = listOf("default", "one", "two", "three", "four", "five", "greenWhite", "green", "greenDark", "blueWhite", "blue", "blueDark")
private val ALL_NAME_KEYS = listOf("default", "app_name")

private fun allLauncherAliasNames(): List<String> =
    ALL_ICON_KEYS.flatMap { icon -> ALL_NAME_KEYS.map { name -> aliasNameFor(icon, name) } }.distinct()

/**
 * Only one launcher activity-alias may ever be enabled at a time — enabling more than one
 * would show multiple separate launcher icons for this app. This disables every possible
 * icon×name combo alias, then enables only [targetAliasName].
 */
internal fun applyLauncherAlias(context: Context, targetAliasName: String) {
    val pm  = context.packageManager
    val pkg = context.packageName

    val allAliasComponents = allLauncherAliasNames().map { ComponentName(pkg, "$pkg.$it") }
    val target = ComponentName(pkg, "$pkg.$targetAliasName")

    allAliasComponents.forEach { component ->
        val state = if (component == target)
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        else
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        pm.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
    }
}

internal fun applyIcon(context: Context, prefs: PreferenceManager, entry: AppIconEntry) {
    val currentNameKey = prefs.getString(PreferenceManager.KEY_APP_NAME_PRESET, "default") ?: "default"
    applyLauncherAlias(context, aliasNameFor(entry.key, currentNameKey))
}

internal fun applyAppNamePreset(context: Context, prefs: PreferenceManager, entry: AppIconEntry) {
    val currentIconKey = prefs.getString(KEY_SELECTED_APP_ICON, "default") ?: "default"
    applyLauncherAlias(context, aliasNameFor(currentIconKey, entry.key))
}

@SuppressLint("UseCompatLoadingForDrawables")
fun loadBitmapFromRes(context: Context, @DrawableRes resId: Int): Bitmap? {
    if (resId == 0) return null
    return try {
        val drawable = context.resources.getDrawable(resId, context.theme)
        when (drawable) {
            is BitmapDrawable         -> drawable.bitmap
            is AdaptiveIconDrawable   -> {
                val bmp = createBitmap(192, 192)
                val canvas = Canvas(bmp)
                drawable.setBounds(0, 0, 192, 192)
                drawable.draw(canvas)
                bmp
            }
            else -> drawable.toBitmap(192, 192)
        }
    } catch (e: Exception) { null }
}

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun AppIconScreen(navigator: DestinationsNavigator) {
    val context = LocalContext.current
    val prefs   = koinInject<PreferenceManager>()
    val settingsState by prefs.settingsChanged.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    val roundness = remember(settingsState) { prefs.getInt(PreferenceManager.KEY_CARD_ROUNDNESS, RillShapeDefaults.DefaultRoundness) }
    val smallCornerDp = rillCornerDp(RillShapeDefaults.BaseSmall, roundness)
    val extraLargeCornerDp = rillCornerDp(RillShapeDefaults.BaseExtraLarge, roundness)
    val extraExtraLargeCornerDp = rillCornerDp(RillShapeDefaults.BaseExtraExtraLarge, roundness)

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

    val icons = remember { buildIcons(context) }

    var selectedKey by remember {
        mutableStateOf(prefs.getString(KEY_SELECTED_APP_ICON, "default") ?: "default")
    }

    val iconBitmaps = remember {
        icons.associate { entry ->
            entry.key to loadBitmapFromRes(context, entry.previewRes)?.asImageBitmap()
        }
    }

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
        scope.launch { delay(260.milliseconds); navigator.navigateUp() }
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
                title = { Title(stringResource(R.string.app_icon)) },
                navigationIcon = {
                    NavigationIcon(onClick = { navigateBack() })
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier
            .padding(
                top = padding.calculateTopPadding(),
                start = 16.dp,
                end = 16.dp,
                bottom = 0.dp
            )
            .fillMaxSize()
            .alpha(alpha)
            .offset(y = offsetY)
        ) {
            Column {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxWidth(),
//                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (!isPro && proCheckDone) {
                        item(span = { GridItemSpan(3) }) {
                            SupportProjectItem(
                                modifier = Modifier.padding(top = 8.dp, bottom = 14.dp).shake(enabledShake) { enabledShake = false },
                                onClick = { navigator.navigate(DonateScreenDestination) }
                            )
                        }
                    } else {
                        item(span = { GridItemSpan(3) }) {
                            Spacer(Modifier.height(16.dp))
                        }
                    }

                    itemsIndexed(icons) { index, entry ->
                        val isSelected = selectedKey == entry.key
                        val bitmap = iconBitmaps[entry.key]

                        val interactionSource = remember { MutableInteractionSource() }
                        val isPressed by interactionSource.collectIsPressedAsState()
                        val cornerRadius by animateDpAsState(
                            targetValue = if (isPressed) extraExtraLargeCornerDp else smallCornerDp,
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            label = "ButtonShape"
                        )
                        val outsideCornerRadius by animateDpAsState(
                            targetValue = if (isPressed) extraExtraLargeCornerDp else extraLargeCornerDp,
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            label = "ButtonShape"
                        )
                        val shape = remember(cornerRadius) {
                            when (index) {
                                0 -> RoundedCornerShape(
                                    topStart = outsideCornerRadius,
                                    topEnd = cornerRadius,
                                    bottomEnd = cornerRadius,
                                    bottomStart = cornerRadius
                                )
                                2 -> RoundedCornerShape(
                                    topStart = cornerRadius,
                                    topEnd = outsideCornerRadius,
                                    bottomEnd = cornerRadius,
                                    bottomStart = cornerRadius
                                )
                                9 -> RoundedCornerShape(
                                    topStart = cornerRadius,
                                    topEnd = cornerRadius,
                                    bottomEnd = cornerRadius,
                                    bottomStart = outsideCornerRadius
                                )
                                11 -> RoundedCornerShape(
                                    topStart = cornerRadius,
                                    topEnd = cornerRadius,
                                    bottomEnd = outsideCornerRadius,
                                    bottomStart = cornerRadius
                                )
                                else -> RoundedCornerShape(cornerRadius)
                            }
                        }
                        Surface(
                            onClick = {
                                if (!isPro && index > 2) {
                                    enabledShake = true
                                    showSnackbar = true
                                    scope.launch {
                                        delay(3000.milliseconds)
                                        showSnackbar = false
                                    }
                                } else {
                                    selectedKey = entry.key
                                    prefs.setString(KEY_SELECTED_APP_ICON, entry.key)
                                    applyIcon(context, prefs, entry)
                                }
                            },
                            shape = shape,
                            color = cardColor,
                            interactionSource = interactionSource,
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .padding(8.dp)
                                    .size(80.dp)
                            ) {
                                if (bitmap != null) {
                                    Image(
                                        bitmap = bitmap,
                                        contentDescription = entry.label,
                                        modifier = Modifier
                                            .size(68.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                    )
                                }

                                if (isSelected) {
                                    Surface(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .size(24.dp),
                                        shape = CircleShape,
                                        color = color_call_end,
                                        shadowElevation = 2.dp,
                                        border = BorderStroke(1.dp, Color.White)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.padding(4.dp)
                                        )
                                    }
                                } else if (!isPro && index > 2) {
                                    Surface(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .size(24.dp),
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.customColors.colorDarkPurple,
                                        shadowElevation = 2.dp,
                                        border = BorderStroke(1.dp, Color.White)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.padding(5.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
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