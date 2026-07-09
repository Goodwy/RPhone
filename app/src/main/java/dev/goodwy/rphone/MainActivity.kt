package dev.goodwy.rphone

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.ContactsContract
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.navigation.compose.rememberNavController
import dev.goodwy.rphone.controller.CallService
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.controller.util.enqueueApkDownload
import dev.goodwy.rphone.controller.util.fetchLatestRelease
import dev.goodwy.rphone.controller.util.getApkDestinationFile
import dev.goodwy.rphone.controller.util.installApkAndScheduleDelete
import dev.goodwy.rphone.controller.util.isNewerVersion
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
import kotlinx.coroutines.delay
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import android.view.Surface
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.automirrored.filled.StickyNote2
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.AccessTimeFilled
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.Dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import dev.goodwy.rphone.controller.util.copyToClipboard
import dev.goodwy.rphone.controller.util.getAppVersion
import dev.goodwy.rphone.controller.util.isAlreadyDefaultDialer
import dev.goodwy.rphone.view.screen.onboarding.MorphingOnboardingScreen
import dev.goodwy.rphone.view.theme.MyColors.bottomBarColor
import com.ramcosta.composedestinations.generated.destinations.AboutAppScreenDestination
import com.ramcosta.composedestinations.generated.destinations.AppIconScreenDestination
import com.ramcosta.composedestinations.generated.destinations.BiometricScreenDestination
import com.ramcosta.composedestinations.generated.destinations.BlockedNumbersScreenDestination
import com.ramcosta.composedestinations.generated.destinations.BlurEffectsElementsScreenDestination
import com.ramcosta.composedestinations.generated.destinations.CallAccountsScreenDestination
import com.ramcosta.composedestinations.generated.destinations.CallSettingsScreenDestination
import com.ramcosta.composedestinations.generated.destinations.CallerUIScreenDestination
import com.ramcosta.composedestinations.generated.destinations.ContactManagementScreenDestination
import com.ramcosta.composedestinations.generated.destinations.ContactScreenDestination
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
import org.koin.core.context.GlobalContext

class MainActivity : FragmentActivity() {

    private val requestRoleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ -> }

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> /* permissions result; dialer popup now shown after welcome */ }
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

                val favouritesEnabled = prefs.getBoolean(PreferenceManager.KEY_TAB_SHOW_FAVORITES, true)
                val contactsEnabled = prefs.getBoolean(PreferenceManager.KEY_TAB_SHOW_CONTACTS, true)
                val notesEnabled = prefs.getBoolean(PreferenceManager.KEY_TAB_SHOW_NOTES, true)
                val dialpadEnabled = prefs.getBoolean(PreferenceManager.KEY_TAB_SHOW_DIALPAD, true)
                val defaultTab = prefs.getString(PreferenceManager.KEY_DEFAULT_TAB, "calls") ?: "calls"

                val defBar = prefs.getInt(PreferenceManager.KEY_DEFAULT_BOTTOM_NAV, 0)
                val transitionStyle = prefs.getInt(PreferenceManager.KEY_TRANSITION_STYLE, 0)
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

                    var autoUpdateVersion by remember { mutableStateOf<String?>(null) }
                    var autoUpdateApkUrl by remember { mutableStateOf<String?>(null) }
                    var showAutoUpdateDialog by remember { mutableStateOf(false) }
                    var autoDownloadId by remember { mutableStateOf<Long?>(null) }
                    var autoDownloadProgress by remember { mutableFloatStateOf(0f) }
                    var showAutoDownloadProgress by remember { mutableStateOf(false) }

                    LaunchedEffect(Unit) {
                        val autoCheck =
                            prefs.getBoolean(PreferenceManager.KEY_AUTO_UPDATE_CHECK, false)
                        if (autoCheck) {
                            val release = fetchLatestRelease(GITHUB_API_RELEASES)
                            if (release != null && isNewerVersion(release.tagName, appInfo.first)) {
                                autoUpdateVersion = release.tagName
                                autoUpdateApkUrl = release.apkUrl
                                showAutoUpdateDialog = true
                            }
                        }
                    }

                    if (showAutoDownloadProgress) {
                        val dlId = autoDownloadId
                        if (dlId != null) {
                            LaunchedEffect(dlId) {
                                val dm =
                                    getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                                while (true) {
                                    delay(300)
                                    val query = DownloadManager.Query().setFilterById(dlId)
                                    val cursor = dm.query(query)
                                    if (!cursor.moveToFirst()) {
                                        cursor.close(); break
                                    }
                                    val dmStatus =
                                        cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                                    val downloaded =
                                        cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                                    val total =
                                        cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                                    cursor.close()
                                    when (dmStatus) {
                                        DownloadManager.STATUS_SUCCESSFUL -> {
                                            showAutoDownloadProgress = false
                                            autoDownloadId = null
                                            val file = getApkDestinationFile()
                                            installApkAndScheduleDelete(this@MainActivity, file)
                                            break
                                        }

                                        DownloadManager.STATUS_FAILED -> {
                                            showAutoDownloadProgress = false
                                            autoDownloadId = null
                                            break
                                        }

                                        else -> {
                                            autoDownloadProgress = if (total > 0L)
                                                (downloaded.toFloat() / total.toFloat()).coerceIn(
                                                    0f,
                                                    1f
                                                ) else 0f
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (showAutoUpdateDialog) {
                        AlertDialog(
                            onDismissRequest = { showAutoUpdateDialog = false },
                            icon = {
                                Icon(
                                    Icons.Default.SystemUpdate,
                                    null,
                                    tint = Color(0xFF2196F3)
                                )
                            },
                            title = { Text("Update Available") },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Version v${autoUpdateVersion} is available.")
                                    Text(
                                        "Would you like to download and install it now?",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            confirmButton = {
                                Button(onClick = {
                                    showAutoUpdateDialog = false
                                    val url = autoUpdateApkUrl
                                    if (url != null) {
                                        val id = enqueueApkDownload(this@MainActivity, url)
                                        if (id != null) {
                                            autoDownloadId = id
                                            autoDownloadProgress = 0f
                                            showAutoDownloadProgress = true
                                        }
                                    }
                                }) { Text("Download") }
                            },
                            dismissButton = {
                                TextButton(onClick = {
                                    showAutoUpdateDialog = false
                                }) { Text("Not Now") }
                            }
                        )
                    }

                    if (showAutoDownloadProgress) {
                        Dialog(onDismissRequest = {}) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Icon(
                                        Icons.Default.SystemUpdate,
                                        null,
                                        tint = Color(0xFF2196F3),
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Text(
                                        "Downloading Update",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        "v${autoUpdateVersion ?: ""}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        LinearProgressIndicator(
                                            progress = { autoDownloadProgress },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                "${(autoDownloadProgress * 100).toInt()}%",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                "Please wait…",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ── Ongoing Call Banner + Main nav host ───────────────────────
                    val callSession by CallService.currentCallSession.collectAsState()
                    val hasOngoingCall =
                        callSession != null && callSession?.state != android.telecom.Call.STATE_RINGING

                    Column(modifier = Modifier.fillMaxSize()) {
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
                                        text = "Call is Ongoing — Tap to return",
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
                        val favouritesEnabled =
                            prefs2.getBoolean(PreferenceManager.KEY_TAB_SHOW_FAVORITES, true)
                        val contactsEnabled =
                            prefs2.getBoolean(PreferenceManager.KEY_TAB_SHOW_CONTACTS, true)
                        val dialpadEnabled =
                            prefs2.getBoolean(PreferenceManager.KEY_TAB_SHOW_DIALPAD, true)
                        val showNotesRail =
                            prefs2.getBoolean(PreferenceManager.KEY_TAB_SHOW_NOTES, true)

                        fun saveCurrentTab(route: String) {
                            when {
                                route.contains(FavoritesScreenDestination.route) ->
                                    prefs.setString(PreferenceManager.KEY_LAST_OPENED_TAB, FavoritesScreenDestination.route)
                                route.contains(ContactScreenDestination.route) ->
                                    prefs.setString(PreferenceManager.KEY_LAST_OPENED_TAB, ContactScreenDestination.route)
                                route.contains(NotesScreenDestination.route) ->
                                    prefs.setString(PreferenceManager.KEY_LAST_OPENED_TAB, NotesScreenDestination.route)
                                route.contains(DialPadScreenDestination.route) ->
                                    prefs.setString(PreferenceManager.KEY_LAST_OPENED_TAB, DialPadScreenDestination.route)
                                route.contains(RecentScreenDestination.route) ->
                                    prefs.setString(PreferenceManager.KEY_LAST_OPENED_TAB, RecentScreenDestination.route)
                            }
                        }

                        LaunchedEffect(currentDest) {
                            currentDest?.route?.let { route ->
                                if (isLandscape && isAlreadyDefaultDialer(this@MainActivity)) {
                                    saveCurrentTab(route)
                                }
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

                        if (isLandscape && isAlreadyDefaultDialer(this@MainActivity)) {
                            val ctx = LocalContext.current

                            @Suppress("DEPRECATION")
                            val rotation =
                                (ctx.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager).defaultDisplay.rotation
                            val isRotation90 = rotation == Surface.ROTATION_90
                            val isRotation270 = rotation == Surface.ROTATION_270
//                        val railPaddingStart = if (isRotation270) 10.dp else 0.dp
//                        val railPaddingEnd   = if (isRotation90)  10.dp else 0.dp

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
                                                        Modifier.windowInsetsPadding(WindowInsets.systemBars)
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
                                                // Nav items — perfectly centered
                                                if (favouritesEnabled) {
                                                    RailItem(
                                                        selected = currentDest?.hierarchy?.any { it.route == FavoritesScreenDestination.route } == true,
                                                        icon = { sel ->
                                                            Icon(
                                                                if (sel) Icons.Filled.Star else Icons.Outlined.StarOutline,
                                                                "Favourites",
                                                                modifier = Modifier.size(24.dp)
                                                            )
                                                        },
//                                                label = "Favourites",
//                                                paddingStart = railPaddingStart,
//                                                paddingEnd = railPaddingEnd,
                                                        onClick = { navTo(FavoritesScreenDestination.route) }
                                                    )
                                                }
                                                RailItem(
                                                    selected = currentDest?.hierarchy?.any { it.route == RecentScreenDestination.route } == true,
                                                    icon = { sel ->
                                                        Icon(
                                                            if (!favouritesEnabled && !contactsEnabled) ImageVector.vectorResource(
                                                                id = R.drawable.ic_house_fill
                                                            ) else if (sel) Icons.Rounded.AccessTimeFilled else Icons.Rounded.AccessTime,
                                                            "Calls",
                                                            modifier = Modifier.size(24.dp)
                                                        )
                                                    },
//                                                label = if (!favouritesEnabled && !contactsEnabled) "Home" else "Calls",
//                                                paddingStart = railPaddingStart,
//                                                paddingEnd = railPaddingEnd,
                                                    onClick = { navTo(RecentScreenDestination.route) }
                                                )
                                                if (contactsEnabled) {
                                                    RailItem(
                                                        selected = currentDest?.hierarchy?.any { it.route == ContactScreenDestination.route } == true,
                                                        icon = { sel ->
                                                            Icon(
                                                                if (sel) Icons.Filled.AccountCircle else Icons.Outlined.AccountCircle,
                                                                "Contacts",
                                                                modifier = Modifier.size(24.dp)
                                                            )
                                                        },
//                                                label = "Contacts",
//                                                paddingStart = railPaddingStart,
//                                                paddingEnd = railPaddingEnd,
                                                        onClick = { navTo(ContactScreenDestination.route) }
                                                    )
                                                }
                                                if (dialpadEnabled) {
                                                    RailItem(
                                                        selected = currentDest?.hierarchy?.any { it.route == DialPadScreenDestination.route } == true,
                                                        icon = { sel ->
                                                            Icon(
                                                                if (sel) Icons.Filled.Dialpad else Icons.Filled.Dialpad,
                                                                "Keypad",
                                                                modifier = Modifier.size(24.dp)
                                                            )
                                                        },
//                                                label = "Keypad",
//                                                paddingStart = railPaddingStart,
//                                                paddingEnd = railPaddingEnd,
                                                        onClick = {
                                                            navTo(
                                                                DialPadScreenDestination(
                                                                    initialNumber = ""
                                                                ).route
                                                            )
                                                        }
                                                    )
                                                }
                                                if (showNotesRail) {
                                                    RailItem(
                                                        selected = currentDest?.hierarchy?.any { it.route == NotesScreenDestination.route } == true,
                                                        icon = { sel ->
                                                            Icon(
                                                                if (sel) Icons.AutoMirrored.Filled.StickyNote2 else Icons.AutoMirrored.Outlined.StickyNote2,
                                                                "Notes",
                                                                modifier = Modifier.size(24.dp)
                                                            )
                                                        },
//                                                    label = "Notes",
//                                                    paddingStart = railPaddingStart,
//                                                    paddingEnd = railPaddingEnd,
                                                        onClick = { navTo(NotesScreenDestination.route) }
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
                                                        it.route?.contains("search", ignoreCase = true) == true
                                                    } == true,
                                                    icon = { _ ->
                                                        Icon(
                                                            Icons.Default.Search,
                                                            "Search",
                                                            modifier = Modifier.size(24.dp)
                                                        )
                                                    },
//                                                label = "Search",
//                                                paddingStart = railPaddingStart,
//                                                paddingEnd = railPaddingEnd,
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
                                                                it.route == ContactManagementScreenDestination.route || it.route == PrivateContactsScreenDestination.route
                                                    } == true,
                                                    icon = { sel ->
                                                        Icon(
                                                            if (sel) Icons.Default.Settings else ImageVector.vectorResource(
                                                                id = R.drawable.ic_settings
                                                            ),
                                                            "Settings",
                                                            modifier = Modifier.size(24.dp)
                                                        )
                                                    },
//                                                label = "Settings",
//                                                paddingStart = railPaddingStart,
//                                                paddingEnd = railPaddingEnd,
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
                                favouritesEnabled || contactsEnabled || dialpadEnabled || notesEnabled
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

                    // ── Biometric lock — direct prompt, no overlay ─────────────
                    if (!isUnlocked) {
                        val activity = this@MainActivity
                        LaunchedEffect(biometricType) {
                            if (biometricType.isEmpty() || !appLockEnabled) {
                                isUnlocked = true
                                return@LaunchedEffect
                            }
                            when (biometricType) {
                                "system" -> {
                                    val executor =
                                        androidx.core.content.ContextCompat.getMainExecutor(activity)
                                    val prompt = androidx.biometric.BiometricPrompt(
                                        activity, executor,
                                        object :
                                            androidx.biometric.BiometricPrompt.AuthenticationCallback() {
                                            override fun onAuthenticationSucceeded(result: androidx.biometric.BiometricPrompt.AuthenticationResult) {
                                                isUnlocked = true
                                            }

                                            override fun onAuthenticationError(
                                                errorCode: Int,
                                                errString: CharSequence
                                            ) {
                                                finish()
                                            }

                                            override fun onAuthenticationFailed() {
                                                finish()
                                            }
                                        }
                                    )
                                    val info =
                                        androidx.biometric.BiometricPrompt.PromptInfo.Builder()
                                            .setTitle(getString(R.string.app_name))
                                            .setSubtitle("Verify your identity to continue")
                                            .setNegativeButtonText("Cancel")
                                            .setAllowedAuthenticators(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK)
                                            .build()
                                    prompt.authenticate(info)
                                }

                                "pin", "password" -> {
                                    // PIN/password shown inline below — handled by showCustomUnlock
                                }
                            }
                        }

                        if (biometricType == "pin" || biometricType == "password") {
                            if (biometricType == "pin") {
                                dev.goodwy.rphone.view.screen.settings.PinSetupDialog(
                                    title = "Enter PIN",
                                    isVerify = true,
                                    expectedPin = prefs.getString(
                                        PreferenceManager.KEY_BIOMETRICS_PIN,
                                        ""
                                    ) ?: "",
                                    onConfirm = { isUnlocked = true },
                                    onDismiss = { finish() }
                                )
                            } else {
                                dev.goodwy.rphone.view.screen.settings.PasswordSetupDialog(
                                    title = "Enter Password",
                                    isVerify = true,
                                    expectedPassword = prefs.getString(
                                        PreferenceManager.KEY_BIOMETRICS_PASSWORD,
                                        ""
                                    ) ?: "",
                                    onConfirm = { isUnlocked = true },
                                    onDismiss = { finish() }
                                )
                            }
                        }
                    }

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
            "com.grinch.rill4.ACTION_VIEW_RECENTS" -> {
                navController.navigate(RecentScreenDestination.route) {
                    popUpTo(navController.graph.startDestinationId)
                    launchSingleTop = true
                }
            }
            Intent.ACTION_DIAL, Intent.ACTION_VIEW -> {
                if (data?.scheme == "tel") {
                    val number = data.schemeSpecificPart
                    navController.navigate(DialPadScreenDestination(initialNumber = number).route)
                } else if (data?.toString()?.contains("contacts") == true ||
                    data?.toString()?.contains("com.android.contacts") == true ||
                    intent.hasExtra("contact_id")) {
                    val id = data?.lastPathSegment ?: intent.getStringExtra("contact_id")
                    if (id != null) {
                        navController.navigate(ContactDetailsScreenDestination(contactId = id).route) {
                            launchSingleTop = true
                            popUpTo(navController.graph.startDestinationId) {
                                saveState = true
                            }
                        }
                    }
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
