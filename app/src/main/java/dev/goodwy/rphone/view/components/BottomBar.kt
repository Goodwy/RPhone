package dev.goodwy.rphone.view.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.EaseOutQuint
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.PreferenceManager
import com.ramcosta.composedestinations.generated.destinations.ContactScreenDestination
import com.ramcosta.composedestinations.generated.destinations.FavoritesScreenDestination
import com.ramcosta.composedestinations.generated.destinations.NotesScreenDestination
import com.ramcosta.composedestinations.generated.destinations.RecentScreenDestination
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import android.os.Build
import androidx.compose.material.icons.automirrored.filled.StickyNote2
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.AccessTimeFilled
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import dev.goodwy.rphone.bottomBarHeight
import dev.goodwy.rphone.liquidglass.drawBackdrop
import dev.goodwy.rphone.liquidglass.drawPlainBackdrop
import dev.goodwy.rphone.liquidglass.effects.blur
import dev.goodwy.rphone.liquidglass.effects.lens
import dev.goodwy.rphone.liquidglass.effects.colorControls
import dev.goodwy.rphone.liquidglass.highlight.Highlight
import dev.goodwy.rphone.liquidglass.LocalLiquidGlassBackdrop
import dev.goodwy.rphone.view.theme.MyColors.bottomBarColor
import com.ramcosta.composedestinations.generated.destinations.DialPadScreenDestination

// Tab routes — only show the bar when one of these is active
private val TAB_ROUTES = setOf(
    FavoritesScreenDestination.route,
    RecentScreenDestination.route,
    ContactScreenDestination.route,
    NotesScreenDestination.route,
    DialPadScreenDestination.route,
//    SettingsScreenDestination.route,
//    SearchScreenDestination.route
)

/** Describes a single bottom-navigation tab, driving both the pill-style and standard nav bars. */
data class TabSpec(
    val key: String,
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val selected: Boolean,
    val onClick: () -> Unit
)

/** Parses the user-configured tab order preference into an ordered list of tab keys. */
fun parseTabOrder(raw: String?): List<String> {
    val fallback = PreferenceManager.DEFAULT_TAB_ORDER.split(",")
    if (raw.isNullOrBlank()) return fallback
    val parsed = raw.split(",").map { it.trim() }.filter { it.isNotBlank() }
    // Ensure any tab keys missing from a stale/older saved order are still appended,
    // so newly-added tabs (like Recordings) always show up even for existing users.
    val merged = parsed.toMutableList()
    fallback.forEach { key -> if (key !in merged) merged.add(key) }
    return merged.filter { it in fallback }
}

@Composable
fun BottomBar(navController: NavController) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    if (isLandscape) return
    val prefs         = koinInject<PreferenceManager>()
    val context       = LocalContext.current
    @Suppress("UNUSED_VARIABLE")
    val settingsState by prefs.settingsChanged.collectAsState()

    val pillNav             = remember(settingsState) { prefs.getBoolean(PreferenceManager.KEY_PILL_NAV, false) }
    val iconOnly            = remember(settingsState) { prefs.getBoolean(PreferenceManager.KEY_ICON_ONLY_NAV, false) }
    val liquidGlass         = remember(settingsState) { prefs.getBoolean(PreferenceManager.KEY_LIQUID_GLASS, false) }
    val lgBottomNav         = remember(settingsState) { prefs.getBoolean(PreferenceManager.KEY_LG_BOTTOM_NAV, true) }
    val blurEffects         = remember(settingsState) { prefs.getBoolean(PreferenceManager.KEY_BLUR_EFFECTS, false) }
    val blurBottomNav       = remember(settingsState) { prefs.getBoolean(PreferenceManager.KEY_BLUR_BOTTOM_NAV, true) }
    val showFavoritesTab    = remember(settingsState) { prefs.getBoolean(PreferenceManager.KEY_TAB_SHOW_FAVORITES, false) }
    val showCallsTab        = remember(settingsState) { prefs.getBoolean(PreferenceManager.KEY_TAB_SHOW_CALLS,     true) }
    val showContactsTab     = remember(settingsState) { prefs.getBoolean(PreferenceManager.KEY_TAB_SHOW_CONTACTS,  true) }
    val showDialpadTab      = remember(settingsState) { prefs.getBoolean(PreferenceManager.KEY_TAB_SHOW_DIALPAD, true) }
    val showNotesTab        = remember(settingsState) { prefs.getBoolean(PreferenceManager.KEY_TAB_SHOW_NOTES,     false) }
    val tabOrder            = remember(settingsState) { parseTabOrder(prefs.getString(PreferenceManager.KEY_TAB_ORDER, null)) }
    val labelStyle: TextStyle = MaterialTheme.typography.labelMedium

    val navBackStackEntry  by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentRoute       = currentDestination?.route ?: ""

    val isFavoritesSelected = currentDestination?.hierarchy?.any { it.route == FavoritesScreenDestination.route } == true
    val isRecentsSelected   = currentDestination?.hierarchy?.any { it.route == RecentScreenDestination.route } == true
    val isContactsSelected  = currentDestination?.hierarchy?.any { it.route == ContactScreenDestination.route } == true
    val isDialpadSelected  = currentDestination?.hierarchy?.any { it.route == DialPadScreenDestination.route } == true
    val isNotesSelected     = currentDestination?.hierarchy?.any { it.route == NotesScreenDestination.route } == true
//    val isSettingsSelected     =currentDestination?.hierarchy?.any { it.route?.contains("settings", ignoreCase = true) == true } == true

    // Build visible tab routes dynamically based on prefs
    val visibleTabRoutes = remember(showFavoritesTab, showCallsTab, showContactsTab, showDialpadTab, showNotesTab) {
        buildSet {
            if (showFavoritesTab) add(FavoritesScreenDestination.route)
            if (showCallsTab)     add(RecentScreenDestination.route)
            // Add contacts if ‘Favorites’ and ‘Contacts’ are hidden to make the ‘View contacts’ button work
            if (showContactsTab || !showFavoritesTab)  add(ContactScreenDestination.route)
            if (showDialpadTab)   add(DialPadScreenDestination.route)
            if (showNotesTab)     add(NotesScreenDestination.route)
        }
    }

    // Only render pill when a visible tab screen is active, and not while a tab screen
    // (e.g. Recordings) is showing its own full-screen onboarding content.
    val isOnTabScreen = visibleTabRoutes.any { currentRoute.contains(it, ignoreCase = true) } &&
            !NavBarVisibilityState.hideForOnboarding &&
            !NavBarVisibilityState.hideForSelectionMode

    // If current tab is now hidden, redirect to first visible tab. This must only fire for
    // tabs the user actually disabled in Settings > Tab Sections — not for a visible tab that's
    // just temporarily hiding the nav bar for its own onboarding content (e.g. Recordings'
    // disclaimer/permissions gate), otherwise tapping that tab would immediately get redirected
    // away again in a loop.
    val isOnHiddenTab = TAB_ROUTES.any { currentRoute.contains(it, ignoreCase = true) } &&
            visibleTabRoutes.none { currentRoute.contains(it, ignoreCase = true) }
    fun routeForTabKey(key: String): String? = when (key) {
        "favorites"  -> FavoritesScreenDestination.route
        "calls"      -> RecentScreenDestination.route
        "contacts"   -> ContactScreenDestination.route
        "dialpad"    -> DialPadScreenDestination.route
        "notes"      -> NotesScreenDestination.route
        else         -> null
    }

    LaunchedEffect(isOnHiddenTab) {
        if (isOnHiddenTab) {
            val firstVisible = tabOrder
                .asSequence()
                .mapNotNull { routeForTabKey(it) }
                .firstOrNull { it in visibleTabRoutes }
                ?: RecentScreenDestination.route
            navController.navigate(firstVisible) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = false }
                launchSingleTop = true
            }
        }
    }

    // ── Slide-in animation — slower, re-triggers every time pill re-enters ───
    var pillVisible by remember { mutableStateOf(false) }
    LaunchedEffect(isOnTabScreen) {
        if (isOnTabScreen) {
            pillVisible = false
            delay(16) // one frame — lets Compose commit the hidden state
            pillVisible = true
        } else {
            pillVisible = false
        }
    }
    val pillOffsetY by animateFloatAsState(
        targetValue   = if (pillVisible) 0f else 220f,
        animationSpec = tween(durationMillis = 750, easing = EaseOutQuint),
        label         = "pillSlideIn"
    )
    val pillAlpha by animateFloatAsState(
        targetValue   = if (pillVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label         = "pillFadeIn"
    )

    fun doHaptic() {
        if (prefs.getBoolean(PreferenceManager.KEY_APP_HAPTICS, true)) {
            performAppHaptic(
                context,
                prefs.getString(PreferenceManager.KEY_APP_HAPTICS_STRENGTH, "light") ?: "light",
                prefs.getFloat(PreferenceManager.KEY_HAPTICS_CUSTOM_INTENSITY, 0.5f)
            )
        }
    }

    fun navigate(route: String) {
        // If already on this route, do nothing (prevents double-tap freeze)
        if (currentDestination?.hierarchy?.any { it.route == route } == true) return
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState    = true
        }
    }

    val houseIcon = ImageVector.vectorResource(id = R.drawable.ic_house_fill)
    val orderedTabs: List<TabSpec> = remember(
        tabOrder, showFavoritesTab, showCallsTab, showContactsTab, showDialpadTab, showNotesTab,
        isFavoritesSelected, isRecentsSelected, isContactsSelected, isDialpadSelected, isNotesSelected
    ) {
        tabOrder.mapNotNull { key ->
            when (key) {
                "favorites" -> if (showFavoritesTab) TabSpec(
                    key             = key,
                    route           = FavoritesScreenDestination.route,
                    selected        = isFavoritesSelected,
                    selectedIcon    = Icons.Filled.Star,
                    unselectedIcon  = Icons.Outlined.StarOutline,
                    label           = "Favourites",
                    onClick         = { doHaptic(); navigate(FavoritesScreenDestination.route) }
                ) else null
                "calls" -> if (showCallsTab) TabSpec(
                    key             = key,
                    route           = RecentScreenDestination.route,
                    selected        = isRecentsSelected,
                    selectedIcon    = if (!showFavoritesTab && !showContactsTab) houseIcon else Icons.Rounded.AccessTimeFilled,
                    unselectedIcon  = if (!showFavoritesTab && !showContactsTab) houseIcon else Icons.Rounded.AccessTime,
                    label           = if (!showFavoritesTab && !showContactsTab) "Home" else "Calls",
                    onClick         = { doHaptic(); navigate(RecentScreenDestination.route) }
                ) else null
                "contacts" -> if (showContactsTab) TabSpec(
                    key             = key,
                    route           = ContactScreenDestination.route,
                    selected        = isContactsSelected,
                    selectedIcon    = Icons.Filled.AccountCircle,
                    unselectedIcon  = Icons.Outlined.AccountCircle,
                    label           = "Contacts",
                    onClick         = { doHaptic(); navigate(ContactScreenDestination.route) }
                ) else null
                "dialpad" -> if (showDialpadTab) TabSpec(
                    key = key,
                    route = DialPadScreenDestination(initialNumber = "").route,
                    selected       = isDialpadSelected,
                    selectedIcon   = Icons.Filled.Dialpad,
                    unselectedIcon = Icons.Filled.Dialpad,
                    label          = "Keypad",
                    onClick        = { doHaptic(); navigate(DialPadScreenDestination(initialNumber = "").route) }
                ) else null
                "notes" -> if (showNotesTab) TabSpec(
                    key            = key,
                    route          = NotesScreenDestination.route,
                    selected       = isNotesSelected,
                    selectedIcon   = Icons.AutoMirrored.Filled.StickyNote2,
                    unselectedIcon = Icons.AutoMirrored.Outlined.StickyNote2,
                    label          = "Notes",
                    onClick        = { doHaptic(); navigate(NotesScreenDestination.route) }
                ) else null
                else -> null
            }
        }
    }

    if (pillNav) {
        if (!isOnTabScreen && !pillVisible) return

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.dp)
                .wrapContentHeight(align = Alignment.Bottom, unbounded = true)
                .offset { IntOffset(0, pillOffsetY.toInt()) }
                .graphicsLayer { alpha = pillAlpha },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                val globalBackdrop = LocalLiquidGlassBackdrop.current
                val pillShape = RoundedCornerShape(32.dp)

                val useLgBottomNav = liquidGlass && lgBottomNav && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && globalBackdrop != null
                val useBlurBottomNav = blurEffects && blurBottomNav && !useLgBottomNav

                val pillContent: @Composable () -> Unit = {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 9.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        orderedTabs.forEach { tab ->
                            PillNavItem(
                                selected = tab.selected,
                                selectedIcon = tab.selectedIcon,
                                unselectedIcon = tab.unselectedIcon,
                                label = tab.label,
                                iconOnly = iconOnly,
                                onClick = tab.onClick
                            )
                        }
                    }
                }

                if (useLgBottomNav) {
                    Surface(
                        shape           = pillShape,
                        color           = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.35f),
                        shadowElevation = 0.dp,
                        tonalElevation  = 0.dp,
                        modifier = Modifier.drawBackdrop(
                            backdrop = globalBackdrop,
                            shape = { pillShape },
                            effects = {
                                val d = density
                                colorControls(saturation = 1.4f)
                                blur(2f * d)
                                lens(
                                    refractionHeight = 23f * d,
                                    refractionAmount = 64f * d
                                )
                            },
                            highlight = { Highlight.Default }
                        )
                    ) { pillContent() }
                } else if (useBlurBottomNav && globalBackdrop != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Surface(
                        shape           = pillShape,
                        color           = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f),
                        shadowElevation = 0.dp,
                        tonalElevation  = 0.dp,
                        modifier        = Modifier.drawPlainBackdrop(
                            backdrop = globalBackdrop,
                            shape    = { pillShape },
                            effects  = { blur(30f * density) }
                        )
                    ) { pillContent() }
                } else {
                    Surface(
                        shape           = pillShape,
                        color           = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shadowElevation = 8.dp,
                        tonalElevation  = 4.dp,
                    ) { pillContent() }
                }
            }
        }
    } else {
        val navBarAlpha by animateFloatAsState(
            targetValue   = if (isOnTabScreen) 1f else 0f,
            animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
            label         = "navBarAlpha"
        )
        if (!isOnTabScreen && navBarAlpha == 0f) return

        val globalBackdrop = LocalLiquidGlassBackdrop.current
        val navBarShape = RoundedCornerShape(0.dp)
        val useLgBottomNav =
            liquidGlass && lgBottomNav && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && globalBackdrop != null
        val useBlurBottomNav =
            blurEffects && blurBottomNav && !useLgBottomNav && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && globalBackdrop != null

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.dp)
                .wrapContentHeight(align = Alignment.Bottom, unbounded = true)
                .offset { IntOffset(0, pillOffsetY.toInt()) }
                .then(
                    if (useLgBottomNav) Modifier.drawBackdrop(
                        backdrop = globalBackdrop,
                        shape = { RoundedCornerShape(16.dp) },
                        effects = {
                            val d = density
                            colorControls(saturation = 1.4f)
                            blur(2f * d)
                            lens(
                                refractionHeight = 23f * d,
                                refractionAmount = 64f * d
                            )
                        },
                        highlight = { Highlight.Default })
                    else if (useBlurBottomNav) Modifier.drawPlainBackdrop(
                        backdrop = globalBackdrop,
                        shape = { navBarShape },
                        effects = { blur(30f * density) }
                    )
                    else Modifier.background(bottomBarColor)
                ),
//                .graphicsLayer { alpha = navBarAlpha },
            contentAlignment = Alignment.Center
        ) {
            Column {
                if (!useLgBottomNav) HorizontalDivider(thickness = 0.4.dp, color = MaterialTheme.colorScheme.surface)
                NavigationBar(
                    containerColor = Color.Transparent,
                    tonalElevation = 0.dp,
                    windowInsets = WindowInsets.navigationBars,
                    modifier = Modifier
                        .fillMaxWidth().navigationBarsPadding()
//                        .wrapContentHeight()
                        .height(bottomBarHeight)
                        .graphicsLayer { alpha = navBarAlpha }
                ) {
                    Spacer(Modifier.width(12.dp))
                    orderedTabs.forEach { tab ->
                        AnimatedNavBarItem(
                            selected       = tab.selected,
                            selectedIcon   = tab.selectedIcon,
                            unselectedIcon = tab.unselectedIcon,
                            label          = tab.label,
                            iconOnly       = iconOnly,
                            labelStyle     = labelStyle,
                            onClick        = tab.onClick
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                }
            }
        }
    }
}

// ── Animated standard nav bar item ────────────────────────────────────────────

@Composable
private fun RowScope.AnimatedNavBarItem(
    selected: Boolean,
    selectedIcon: ImageVector,
    unselectedIcon: ImageVector,
    label: String,
    iconOnly: Boolean,
    labelStyle: TextStyle,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val iconSize by animateDpAsState(
        targetValue   = 24.dp, //if (selected) 26.dp else 24.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label         = "${label}Size"
    )
    val scale by animateFloatAsState(
        targetValue   = when {
            isPressed -> 0.85f
            selected  -> 1.04f
            else      -> 1f
        },
        animationSpec = if (isPressed)
            tween(durationMillis = 80, easing = FastOutSlowInEasing)
        else if (selected)
            spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioMediumBouncy)
        else
            tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label         = "${label}Scale"
    )

    NavigationBarItem(
        modifier = Modifier.padding(top = 4.dp),
        icon = {
            Box(modifier = Modifier.scale(scale)) {
                Crossfade(
                    targetState   = selected,
                    animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
                    label         = "${label}IconCrossfade"
                ) { sel ->
                    Icon(
                        imageVector        = if (sel) selectedIcon else unselectedIcon,
                        contentDescription = label,
                        modifier           = Modifier.size(iconSize)
                    )
                }
            }
        },
        label           = if (iconOnly) null
        else ({ Text(label, style = labelStyle, maxLines = 1, overflow = TextOverflow.Ellipsis) }),
        alwaysShowLabel = !iconOnly,
        selected        = selected,
        interactionSource = interactionSource,
        colors          = NavigationBarItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
            indicatorColor    = MaterialTheme.colorScheme.primaryContainer
        ),
        onClick = onClick
    )
}

// ── Pill nav item ─────────────────────────────────────────────────────────────

@Composable
private fun PillNavItem(
    selected: Boolean,
    selectedIcon: ImageVector,
    unselectedIcon: ImageVector,
    label: String,
    iconOnly: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val bgAlpha by animateFloatAsState(
        targetValue   = if (selected) 1f else 0f,
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label         = "${label}BgAlpha"
    )
    val iconTint by animateColorAsState(
        targetValue   = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label         = "${label}IconTint"
    )
    // Tap-press squish → spring back bounce
    val scale by animateFloatAsState(
        targetValue   = when {
            isPressed -> 0.82f
//            selected  -> 1.10f
            else      -> 1f
        },
        animationSpec = if (isPressed)
            tween(durationMillis = 80, easing = FastOutSlowInEasing)
        else if (selected)
            spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioMediumBouncy)
        else
            tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label         = "${label}Scale"
    )

    Box(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(50.dp))
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = bgAlpha))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = if (iconOnly) 16.dp else 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        if (iconOnly) {
            Crossfade(
                targetState   = selected,
                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                label         = "${label}IconCrossfade"
            ) { sel ->
                Icon(
                    imageVector        = if (sel) selectedIcon else unselectedIcon,
                    contentDescription = label,
                    modifier           = Modifier.size(24.dp),
                    tint               = iconTint
                )
            }
        } else {
            Row(
                modifier = Modifier.animateContentSize(
                    animationSpec = spring(
                        stiffness    = Spring.StiffnessMediumLow,
                        dampingRatio = Spring.DampingRatioMediumBouncy
                    )
                ),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Crossfade(
                    targetState   = selected,
                    animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                    label         = "${label}IconCrossfade"
                ) { sel ->
                    Icon(
                        imageVector        = if (sel) selectedIcon else unselectedIcon,
                        contentDescription = label,
                        modifier           = Modifier.size(24.dp),
                        tint               = iconTint
                    )
                }
                AnimatedVisibility(
                    visible = selected,
                    enter = expandHorizontally(
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioMediumBouncy),
                        expandFrom = Alignment.Start
                    ) + fadeIn(tween(durationMillis = 350)),
                    exit = shrinkHorizontally(
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy),
                        shrinkTowards = Alignment.Start
                    ) + fadeOut(tween(durationMillis = 250))
                ) {
                    Text(
                        text  = label,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}
