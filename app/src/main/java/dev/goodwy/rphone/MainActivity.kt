package dev.goodwy.rphone

import android.content.Intent
import android.os.Bundle
import android.provider.ContactsContract
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.navigation.compose.rememberNavController
import dev.goodwy.rphone.controller.CallService
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.controller.CallActivity
import dev.goodwy.rphone.view.components.BottomBar
import dev.goodwy.rphone.liquidglass.LocalLiquidGlassBackdrop
import dev.goodwy.rphone.liquidglass.backdrops.rememberLayerBackdrop
import dev.goodwy.rphone.liquidglass.backdrops.layerBackdrop
import dev.goodwy.rphone.view.theme.Rill4Theme
import dev.goodwy.rphone.view.theme.TabTransitionStyle
import com.ramcosta.composedestinations.DestinationsNavHost
import com.ramcosta.composedestinations.generated.NavGraphs
import com.ramcosta.composedestinations.generated.destinations.ContactDetailsScreenDestination
import com.ramcosta.composedestinations.generated.destinations.DialPadScreenDestination
import com.ramcosta.composedestinations.generated.destinations.ContactEditScreenDestination
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import android.view.Surface
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.automirrored.filled.StickyNote2
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Assistant
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Assistant
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.AccessTimeFilled
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.Dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import dev.goodwy.rphone.controller.util.getAppVersion
import dev.goodwy.rphone.controller.util.isAlreadyDefaultDialer
import dev.goodwy.rphone.view.screen.onboarding.MorphingOnboardingScreen
import dev.goodwy.rphone.view.theme.MyColors.bottomBarColor
import com.ramcosta.composedestinations.generated.destinations.AboutAppScreenDestination
import com.ramcosta.composedestinations.generated.destinations.AppIconScreenDestination
import com.ramcosta.composedestinations.generated.destinations.AvatarsPreferenceScreenDestination
import com.ramcosta.composedestinations.generated.destinations.BiometricScreenDestination
import com.ramcosta.composedestinations.generated.destinations.BlockedNumbersScreenDestination
import com.ramcosta.composedestinations.generated.destinations.BlurEffectsElementsScreenDestination
import com.ramcosta.composedestinations.generated.destinations.CallAccountsScreenDestination
import com.ramcosta.composedestinations.generated.destinations.CallSettingsScreenDestination
import com.ramcosta.composedestinations.generated.destinations.CallerUIScreenDestination
import com.ramcosta.composedestinations.generated.destinations.ContactManagementScreenDestination
import com.ramcosta.composedestinations.generated.destinations.ContactMergeDuplicatesScreenDestination
import com.ramcosta.composedestinations.generated.destinations.ContactScreenDestination
import com.ramcosta.composedestinations.generated.destinations.ContactUnmergeDuplicatesScreenDestination
import com.ramcosta.composedestinations.generated.destinations.ContactVisibilityScreenDestination
import com.ramcosta.composedestinations.generated.destinations.ContributorsScreenDestination
import com.ramcosta.composedestinations.generated.destinations.DefaultDialerScreenDestination
import com.ramcosta.composedestinations.generated.destinations.DonateScreenDestination
import com.ramcosta.composedestinations.generated.destinations.FavoritesScreenDestination
import com.ramcosta.composedestinations.generated.destinations.InterfaceScreenDestination
import com.ramcosta.composedestinations.generated.destinations.LiquidGlassElementsScreenDestination
import com.ramcosta.composedestinations.generated.destinations.NavigationScreenDestination
import com.ramcosta.composedestinations.generated.destinations.NotesScreenDestination
import com.ramcosta.composedestinations.generated.destinations.PrivateContactsScreenDestination
import com.ramcosta.composedestinations.generated.destinations.RecentScreenDestination
import com.ramcosta.composedestinations.generated.destinations.SoundVibrationScreenDestination
import com.ramcosta.composedestinations.generated.destinations.SpamScreenDestination
import dev.goodwy.rphone.view.components.TabSpec
import dev.goodwy.rphone.view.components.parseTabOrder
import dev.goodwy.rphone.view.components.performAppHaptic
import org.koin.core.context.GlobalContext

class MainActivity : FragmentActivity() {

    //    private val requestRoleLauncher = registerForActivityResult(
//        ActivityResultContracts.StartActivityForResult()
//    ) { _ -> }
//
//    private val requestPermissionsLauncher = registerForActivityResult(
//        ActivityResultContracts.RequestMultiplePermissions()
//    ) { _ -> /* permissions result; dialer popup now shown after welcome */ }
    private var intentState by mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        intentState = intent
        // enableEdgeToEdge() triggers Adreno GPU driver SIGSEGV on first RenderThread draw.
        // Edge-to-edge is set via theme XML instead (windowDrawsSystemBarBackgrounds etc).
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            Rill4Theme {
                val context = LocalContext.current
                val appInfo = getAppVersion(context)
                val navController = rememberNavController()

                val prefs = remember {
                    GlobalContext.get().get<PreferenceManager>()
                }

                val favouritesEnabled = prefs.getBoolean(PreferenceManager.KEY_TAB_SHOW_FAVORITES, false)
                val contactsEnabled = prefs.getBoolean(PreferenceManager.KEY_TAB_SHOW_CONTACTS, true)
                val notesEnabled = prefs.getBoolean(PreferenceManager.KEY_TAB_SHOW_NOTES, false)
//                val dialpadEnabled = prefs.getBoolean(PreferenceManager.KEY_TAB_SHOW_DIALPAD, true)
                val defaultTab = prefs.getString(PreferenceManager.KEY_DEFAULT_TAB, "calls") ?: "calls"

//                val defBar = prefs.getInt(PreferenceManager.KEY_DEFAULT_BOTTOM_NAV, 0)
//                val transitionStyle = prefs.getInt(PreferenceManager.KEY_TRANSITION_STYLE, 0)
                val onboardingShown = remember { prefs.getBoolean(PreferenceManager.KEY_ONBOARDING_SHOWN, false) }

                var showOnboarding by remember { mutableStateOf(!onboardingShown) }

                if (showOnboarding) {
                    MorphingOnboardingScreen(
                        onFinished = {
                            prefs.setBoolean(PreferenceManager.KEY_ONBOARDING_SHOWN, true)
                            showOnboarding = false
                        }
                    )
                } else {
                    // ── Biometric app-lock ──────────────────────────────────────
                    val settingsVer by prefs.settingsChanged.collectAsState()
                    val biometricType = remember(settingsVer) {
                        prefs.getString(PreferenceManager.KEY_BIOMETRICS_TYPE, "") ?: ""
                    }
                    val appLockEnabled = remember(settingsVer) {
                        prefs.getBoolean(PreferenceManager.KEY_BIOMETRICS_APP_LOCK, false)
                    }
                    var isUnlocked by remember {
                        mutableStateOf(!(biometricType.isNotEmpty() && appLockEnabled))
                    }

                    val lastOpenedTab = remember {
                        prefs.getString(PreferenceManager.KEY_LAST_OPENED_TAB, null)
                    }
                    // Compute start destination from prefs — done once so no flash
                    val startDestination = remember {
                        when {
                            !isAlreadyDefaultDialer(this@MainActivity) -> DefaultDialerScreenDestination
                            lastOpenedTab != null -> {
                                when {
                                    lastOpenedTab.contains(FavoritesScreenDestination.route) && favouritesEnabled -> FavoritesScreenDestination
                                    lastOpenedTab.contains(ContactScreenDestination.route) && contactsEnabled -> ContactScreenDestination
                                    lastOpenedTab.contains(NotesScreenDestination.route) && notesEnabled -> NotesScreenDestination
//                                    lastOpenedTab.contains(DialPadScreenDestination.route) && dialpadEnabled -> DialPadScreenDestination
                                    else -> RecentScreenDestination
                                }
                            }
                            defaultTab == "favorites" && favouritesEnabled -> FavoritesScreenDestination
                            defaultTab == "contacts" && contactsEnabled -> ContactScreenDestination
                            defaultTab == "notes" && notesEnabled -> NotesScreenDestination
                            else -> RecentScreenDestination
                        }
                    }

                    // ── Biometric blur + lock ─────────────────────────────────
                    val blurRadius by animateDpAsState(
                        targetValue = if (!isUnlocked) 22.dp else 0.dp,
                        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
                        label = "biometricBlur"
                    )

                    // ── Ongoing Call Banner + Main nav host ───────────────────────
                    val callSession by CallService.currentCallSession.collectAsState()
                    val hasOngoingCall =
                        callSession != null && callSession?.state != android.telecom.Call.STATE_RINGING

                    Box(modifier = Modifier.fillMaxSize()) {
                        // Main content — blurred when locked
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .then(
                                    if (blurRadius > 0.dp)
                                        Modifier.blur(
                                            blurRadius,
                                            edgeTreatment = BlurredEdgeTreatment.Unbounded
                                        )
                                    else
                                        Modifier
                                )
                        ) {
                            // ── Ongoing Call Banner (above all content) ────────────
                            AnimatedVisibility(
                                visible = hasOngoingCall,
                                enter = slideInVertically { -it } + fadeIn(),
                                exit = slideOutVertically { -it } + fadeOut()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF1B5E20))
                                        .statusBarsPadding()
                                        .clickable {
                                            startActivity(
                                                Intent(
                                                    this@MainActivity,
                                                    CallActivity::class.java
                                                ).apply {
                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                                                }
                                            )
                                        }
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Call,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = stringResource(R.string.call_is_ongoing),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = Color.White,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }

                            // ── Main nav host + adaptive nav (bottom bar / rail) ───
                            val configuration = LocalConfiguration.current
                            val isLandscape =
                                configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                            dev.goodwy.rphone.view.theme.isLandscapeMode = isLandscape
                            val navBackStack by navController.currentBackStackEntryAsState()
                            val currentDest = navBackStack?.destination
                            val prefs2 = remember { GlobalContext.get().get<PreferenceManager>() }
                            val showFavoritesRail = prefs2.getBoolean(PreferenceManager.KEY_TAB_SHOW_FAVORITES, false)
                            val showCallsRail = prefs2.getBoolean(PreferenceManager.KEY_TAB_SHOW_CALLS,     true)
                            val showContactsRail = prefs2.getBoolean(PreferenceManager.KEY_TAB_SHOW_CONTACTS, true)
                            val showDialpadRail = prefs2.getBoolean(PreferenceManager.KEY_TAB_SHOW_DIALPAD, true)
                            val showNotesRail = prefs2.getBoolean(PreferenceManager.KEY_TAB_SHOW_NOTES, false)
                            val tabOrder = parseTabOrder(prefs2.getString(PreferenceManager.KEY_TAB_ORDER, null))

                            val isFavoritesSelected = currentDest?.hierarchy?.any { it.route == FavoritesScreenDestination.route } == true
                            val isRecentsSelected   = currentDest?.hierarchy?.any { it.route == RecentScreenDestination.route } == true
                            val isContactsSelected  = currentDest?.hierarchy?.any { it.route == ContactScreenDestination.route } == true
                            val isDialpadSelected   = currentDest?.hierarchy?.any { it.route == DialPadScreenDestination.route } == true
                            val isNotesSelected     = currentDest?.hierarchy?.any { it.route == NotesScreenDestination.route } == true

                            fun saveCurrentTab(route: String) {
                                when {
                                    route.contains(FavoritesScreenDestination.route) ->
                                        prefs.setString(
                                            PreferenceManager.KEY_LAST_OPENED_TAB,
                                            FavoritesScreenDestination.route
                                        )

                                    route.contains(ContactScreenDestination.route) ->
                                        prefs.setString(
                                            PreferenceManager.KEY_LAST_OPENED_TAB,
                                            ContactScreenDestination.route
                                        )

                                    route.contains(NotesScreenDestination.route) ->
                                        prefs.setString(
                                            PreferenceManager.KEY_LAST_OPENED_TAB,
                                            NotesScreenDestination.route
                                        )

                                    route.contains(DialPadScreenDestination.route) ->
                                        prefs.setString(
                                            PreferenceManager.KEY_LAST_OPENED_TAB,
                                            DialPadScreenDestination.route
                                        )

                                    route.contains(RecentScreenDestination.route) ->
                                        prefs.setString(
                                            PreferenceManager.KEY_LAST_OPENED_TAB,
                                            RecentScreenDestination.route
                                        )
                                }
                            }

                            LaunchedEffect(currentDest) {
                                currentDest?.route?.let { route ->
                                    saveCurrentTab(route)
                                }
                            }

                            fun navTo(route: String) {
                                saveCurrentTab(route)
                                navController.navigate(route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }

                            fun doHaptic() {
                                if (prefs.getBoolean(PreferenceManager.KEY_APP_HAPTICS, true)) {
                                    performAppHaptic(
                                        context,
                                        prefs.getString(PreferenceManager.KEY_APP_HAPTICS_STRENGTH, "light") ?: "light",
                                        prefs.getFloat(PreferenceManager.KEY_HAPTICS_CUSTOM_INTENSITY, 0.5f)
                                    )
                                }
                            }

                            if (isLandscape && isAlreadyDefaultDialer(this@MainActivity)) {
                                val ctx = LocalContext.current

                                @Suppress("DEPRECATION")
                                val rotation =
                                    (ctx.getSystemService(WINDOW_SERVICE) as android.view.WindowManager).defaultDisplay.rotation
                                val isRotation90 = rotation == Surface.ROTATION_90
                                val isRotation270 = rotation == Surface.ROTATION_270
//                        val railPaddingStart = if (isRotation270) 10.dp else 0.dp
//                        val railPaddingEnd   = if (isRotation90)  10.dp else 0.dp



                                val houseIcon = ImageVector.vectorResource(id = R.drawable.ic_house_fill)
                                val labelFavorites = stringResource(R.string.favorites)
                                val labelRecents =
                                    if (!showFavoritesRail && !showContactsRail) stringResource(R.string.home_tab)
                                    else stringResource(R.string.recents)
                                val labelContacts = stringResource(R.string.contacts)
                                val labelKeypad = stringResource(R.string.keypad)
                                val labelNotes = stringResource(R.string.notes)
                                val orderedTabs: List<TabSpec> = remember(
                                    tabOrder, showFavoritesRail, showCallsRail, showContactsRail, showDialpadRail, showNotesRail,
                                    isFavoritesSelected, isRecentsSelected, isContactsSelected, isDialpadSelected, isNotesSelected
                                ) {
                                    tabOrder.mapNotNull { key ->
                                        when (key) {
                                            "favorites" -> if (showFavoritesRail) TabSpec(
                                                key             = key,
                                                route           = FavoritesScreenDestination.route,
                                                selected        = isFavoritesSelected,
                                                selectedIcon    = Icons.Filled.Assistant,
                                                unselectedIcon  = Icons.Outlined.Assistant,
                                                label           = labelFavorites,
                                                onClick         = { doHaptic(); navTo(FavoritesScreenDestination.route) }
                                            ) else null
                                            "calls" -> if (showCallsRail) TabSpec(
                                                key             = key,
                                                route           = RecentScreenDestination.route,
                                                selected        = isRecentsSelected,
                                                selectedIcon    = if (!showFavoritesRail && !showContactsRail) houseIcon else Icons.Rounded.AccessTimeFilled,
                                                unselectedIcon  = if (!showFavoritesRail && !showContactsRail) houseIcon else Icons.Rounded.AccessTime,
                                                label           = labelRecents,
                                                onClick         = { doHaptic(); navTo(RecentScreenDestination.route) }
                                            ) else null
                                            "contacts" -> if (showContactsRail) TabSpec(
                                                key             = key,
                                                route           = ContactScreenDestination.route,
                                                selected        = isContactsSelected,
                                                selectedIcon    = Icons.Filled.AccountCircle,
                                                unselectedIcon  = Icons.Outlined.AccountCircle,
                                                label           = labelContacts,
                                                onClick         = { doHaptic(); navTo(ContactScreenDestination.route) }
                                            ) else null
                                            "dialpad" -> if (showDialpadRail) TabSpec(
                                                key = key,
                                                route = DialPadScreenDestination(initialNumber = "").route,
                                                selected       = isDialpadSelected,
                                                selectedIcon   = Icons.Filled.Dialpad,
                                                unselectedIcon = Icons.Filled.Dialpad,
                                                label          = labelKeypad,
                                                onClick        = { doHaptic(); navTo(DialPadScreenDestination(initialNumber = "").route) }
                                            ) else null
                                            "notes" -> if (showNotesRail) TabSpec(
                                                key            = key,
                                                route          = NotesScreenDestination.route,
                                                selected       = isNotesSelected,
                                                selectedIcon   = Icons.AutoMirrored.Filled.StickyNote2,
                                                unselectedIcon = Icons.AutoMirrored.Outlined.StickyNote2,
                                                label          = labelNotes,
                                                onClick        = { doHaptic(); navTo(NotesScreenDestination.route) }
                                            ) else null
                                            else -> null
                                        }
                                    }
                                }

                                val liquidGlassBackdropLandscape = rememberLayerBackdrop()
                                CompositionLocalProvider(LocalLiquidGlassBackdrop provides liquidGlassBackdropLandscape) {
                                    Row(modifier = Modifier.fillMaxSize()) {
                                        Surface(
                                            color = bottomBarColor,
                                            modifier = Modifier.fillMaxHeight()
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxHeight()
                                                    .then(
                                                        if (isRotation90) {
                                                            Modifier.windowInsetsPadding(
                                                                WindowInsets.displayCutout/*.union(
                                                                WindowInsets.systemBars
                                                            )*/
                                                            )
                                                        } else {
                                                            Modifier.windowInsetsPadding(
                                                                WindowInsets.systemBars
                                                            )
                                                        }
                                                    )
                                                    .width(82.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Column(
                                                    modifier = Modifier.fillMaxSize(),
                                                    verticalArrangement = Arrangement.Center,
                                                    horizontalAlignment = Alignment.CenterHorizontally
                                                ) {
                                                    orderedTabs.forEach { tab ->
                                                        RailItem(
                                                            selected = tab.selected,
                                                            icon = { sel ->
                                                                Icon(
                                                                    if (sel) tab.selectedIcon else tab.unselectedIcon,
                                                                    "Calls",
                                                                    modifier = Modifier.size(24.dp)
                                                                )
                                                            },
                                                            onClick = tab.onClick
                                                        )
                                                    }

                                                    Spacer(Modifier.height(8.dp))
                                                    HorizontalDivider(
                                                        modifier = Modifier.width(32.dp),
                                                        color = MaterialTheme.colorScheme.outlineVariant.copy(
                                                            alpha = 0.6f
                                                        )
                                                    )
                                                    Spacer(Modifier.height(8.dp))

                                                    RailItem(
                                                        selected = currentDest?.hierarchy?.any {
                                                            it.route?.contains(
                                                                "search",
                                                                ignoreCase = true
                                                            ) == true
                                                        } == true,
                                                        icon = { _ ->
                                                            Icon(
                                                                Icons.Default.Search,
                                                                "Search",
                                                                modifier = Modifier.size(24.dp)
                                                            )
                                                        },
                                                        onClick = { navTo(com.ramcosta.composedestinations.generated.destinations.SearchScreenDestination.route) }
                                                    )
                                                    RailItem(
                                                        selected = currentDest?.hierarchy?.any {
                                                            it.route?.contains("settings", ignoreCase = true) == true || it.route == DonateScreenDestination.route ||
                                                                    it.route == AboutAppScreenDestination.route || it.route == AppIconScreenDestination.route ||
                                                                    it.route == BiometricScreenDestination.route || it.route == BlockedNumbersScreenDestination.route ||
                                                                    it.route == BlurEffectsElementsScreenDestination.route || it.route == CallAccountsScreenDestination.route ||
                                                                    it.route == CallerUIScreenDestination.route || it.route == CallSettingsScreenDestination.route ||
                                                                    it.route == ContributorsScreenDestination.route || it.route == InterfaceScreenDestination.route ||
                                                                    it.route == LiquidGlassElementsScreenDestination.route || it.route == NavigationScreenDestination.route ||
                                                                    it.route == SoundVibrationScreenDestination.route || it.route == SpamScreenDestination.route ||
                                                                    it.route == ContactManagementScreenDestination.route || it.route == PrivateContactsScreenDestination.route ||
                                                                    it.route == ContactVisibilityScreenDestination.route || it.route == ContactMergeDuplicatesScreenDestination.route ||
                                                                    it.route == ContactUnmergeDuplicatesScreenDestination.route || it.route == AvatarsPreferenceScreenDestination.route
                                                        } == true,
                                                        icon = { sel ->
                                                            Icon(
                                                                if (sel) Icons.Default.Settings else ImageVector.vectorResource(id = R.drawable.ic_settings),
                                                                stringResource(R.string.settings),
                                                                modifier = Modifier.size(24.dp)
                                                            )
                                                        },
                                                        onClick = { navTo(com.ramcosta.composedestinations.generated.destinations.SettingsScreenDestination.route) }
                                                    )
                                                }
                                            }
                                        }
                                        // ── Main content fills the rest, edge-to-edge ──────────────────────
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .background(MaterialTheme.colorScheme.background)
                                                .fillMaxHeight().then(
                                                    if (isRotation270) {
                                                        Modifier.windowInsetsPadding(
                                                            WindowInsets.displayCutout
                                                        )
                                                    } else {
                                                        Modifier.windowInsetsPadding(WindowInsets.systemBars)
                                                    }
                                                )
                                        ) {
                                            DestinationsNavHost(
                                                navGraph = NavGraphs.root,
                                                navController = navController,
                                                start = startDestination,
                                                defaultTransitions = TabTransitionStyle
                                            )
                                        }
                                    }
                                } // end CompositionLocalProvider landscape
                            } else {
                                val liquidGlassBackdrop = rememberLayerBackdrop()
                                val showBottomBar =
                                    showFavoritesRail || showContactsRail || showDialpadRail || notesEnabled
                                CompositionLocalProvider(LocalLiquidGlassBackdrop provides liquidGlassBackdrop) {
                                    Scaffold(
                                        bottomBar = { if (showBottomBar) BottomBar(navController) },
                                        containerColor = MaterialTheme.colorScheme.surface,
                                        contentWindowInsets = WindowInsets(0)
                                    ) { scaffoldPadding ->
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(scaffoldPadding)
                                                .layerBackdrop(liquidGlassBackdrop)
                                                .then(
                                                    if (hasOngoingCall)
                                                        Modifier.consumeWindowInsets(WindowInsets.statusBars)
                                                    else
                                                        Modifier
                                                )
                                        ) {
                                            DestinationsNavHost(
                                                navGraph = NavGraphs.root,
                                                navController = navController,
                                                start = startDestination,
                                                defaultTransitions = TabTransitionStyle
                                            )
                                        }
                                    }
                                }
                            }

                            LaunchedEffect(Unit) {
                                if (!isAlreadyDefaultDialer(this@MainActivity)) {
                                    navController.navigate(DefaultDialerScreenDestination.route) {
                                        popUpTo(ContactScreenDestination.route) {
                                            inclusive = true
                                        }
                                    }
                                }
                            }

                            LaunchedEffect(intentState) {
                                handleIntent(intentState, navController)
                            }
                        }
                    } // end blurred Column

                    // ── Biometric overlay (above blur, inside Box) ─────────
                    if (!isUnlocked) {
                        val activity = this@MainActivity
                        LaunchedEffect(biometricType) {
                            if (biometricType.isEmpty() || !appLockEnabled) {
                                isUnlocked = true; return@LaunchedEffect
                            }
                            if (biometricType == "system") {
                                val executor = androidx.core.content.ContextCompat.getMainExecutor(activity)
                                val prompt = androidx.biometric.BiometricPrompt(
                                    activity, executor,
                                    object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
                                        override fun onAuthenticationSucceeded(r: androidx.biometric.BiometricPrompt.AuthenticationResult) { isUnlocked = true }
                                        override fun onAuthenticationError(code: Int, msg: CharSequence) { finish() }
                                        override fun onAuthenticationFailed() { finish() }
                                    }
                                )
                                prompt.authenticate(
                                    androidx.biometric.BiometricPrompt.PromptInfo.Builder()
                                        .setTitle(getString(R.string.app_name))
                                        .setSubtitle("Verify your identity to continue")
                                        .setNegativeButtonText(getString(R.string.cancel))
                                        .setAllowedAuthenticators(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK)
                                        .build()
                                )
                            }
                        }
                        if (biometricType == "pin") {
                            dev.goodwy.rphone.view.screen.settings.PinSetupDialog(
                                title = stringResource(R.string.enter_pin), isVerify = true,
                                expectedPin = prefs.getString(PreferenceManager.KEY_BIOMETRICS_PIN, "") ?: "",
                                onConfirm = { isUnlocked = true }, onDismiss = { finish() }
                            )
                        } else if (biometricType == "password") {

                            dev.goodwy.rphone.view.screen.settings.PasswordSetupDialog(
                                title = stringResource(R.string.enter_password), isVerify = true,
                                expectedPassword = prefs.getString(PreferenceManager.KEY_BIOMETRICS_PASSWORD, "") ?: "",
                                onConfirm = { isUnlocked = true }, onDismiss = { finish() }
                            )
                        }
                    } // end outer Box

                    LaunchedEffect(intent) {
                        handleIntent(intent, navController)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intentState = intent
    }

    private fun handleIntent(intent: Intent?, navController: androidx.navigation.NavController) {
        intent ?: return
        val data = intent.data
        val action = intent.action

        when (action) {
            "dev.goodwy.rphone.ACTION_VIEW_RECENTS" -> {
                navController.navigate(RecentScreenDestination.route) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                }
            }
            Intent.ACTION_VIEW -> {
                val mimeType = intent.type
                if (mimeType == "vnd.android.cursor.dir/calls" ||
                    data?.toString()?.contains("call_log") == true ||
                    data?.toString()?.contains("calls") == true) {
                    navController.navigate(RecentScreenDestination.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                    }
                } else if (data?.scheme == "tel") {
                    val number = data.schemeSpecificPart
                    navController.navigate(DialPadScreenDestination(initialNumber = number).route)
                } else if (data?.toString()?.contains("contacts") == true ||
                    data?.toString()?.contains("com.android.contacts") == true ||
                    intent.hasExtra("contact_id")) {
                    val id = data?.lastPathSegment ?: intent.getStringExtra("contact_id")
                    if (id != null) {
                        navController.navigate(ContactDetailsScreenDestination(contactId = id).route)
                    }
                }
            }
            Intent.ACTION_DIAL -> {
                if (data?.scheme == "tel") {
                    val number = data.schemeSpecificPart
                    navController.navigate(DialPadScreenDestination(initialNumber = number).route)
                }
            }
            Intent.ACTION_INSERT -> {
                val name = intent.getStringExtra(ContactsContract.Intents.Insert.NAME)
                val phone = intent.getStringExtra(ContactsContract.Intents.Insert.PHONE)
                navController.navigate(ContactEditScreenDestination(initialName = name, initialPhone = phone).route) {
                    launchSingleTop = true
                    popUpTo(navController.graph.startDestinationId) {
                        saveState = true
                    }
                }
            }
            Intent.ACTION_EDIT -> {
                val id = data?.lastPathSegment
                if (id != null) {
                    navController.navigate(ContactEditScreenDestination(contactId = id).route) {
                        launchSingleTop = true
                        popUpTo(navController.graph.startDestinationId) {
                            saveState = true
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RailItem(
    selected: Boolean,
    icon: @Composable (selected: Boolean) -> Unit,
    label: String? = null,
    paddingStart: Dp = 0.dp,
    paddingEnd: Dp = 0.dp,
    onClick: () -> Unit
) {
    val bgColor = if (selected)
        MaterialTheme.colorScheme.primaryContainer
    else
        Color.Transparent
    val contentColor = if (selected)
        MaterialTheme.colorScheme.onPrimaryContainer
    else
        MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(start = paddingStart, end = paddingEnd, top = 4.dp, bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(width = 56.dp, height = 36.dp)
                .clip(RoundedCornerShape(50))
                .background(bgColor),
            contentAlignment = Alignment.Center
        ) {
            CompositionLocalProvider(
                LocalContentColor provides contentColor
            ) {
                icon(selected)
            }
        }
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor,
                maxLines = 1
            )
        }
    }
}
