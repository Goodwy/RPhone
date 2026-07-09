package dev.goodwy.rphone.view.screen

import android.Manifest
import android.content.res.Configuration
import dev.goodwy.rphone.view.theme.TabTransitionStyle
import android.content.Context
import android.content.Intent
import android.provider.CallLog
import android.telecom.TelecomManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState
import dev.goodwy.rphone.controller.CallLogViewModel
import dev.goodwy.rphone.controller.util.formatDateHeader
import dev.goodwy.rphone.controller.util.makeCall
import dev.goodwy.rphone.controller.util.placeCallWithSimPreference
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.modal.data.CallLogFilter
import dev.goodwy.rphone.view.components.*
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.ContactDetailsScreenDestination
import com.ramcosta.composedestinations.generated.destinations.ContactEditScreenDestination
import com.ramcosta.composedestinations.generated.destinations.ContactScreenDestination
import com.ramcosta.composedestinations.generated.destinations.DialPadScreenDestination
import com.ramcosta.composedestinations.generated.destinations.FavoritesScreenDestination
import com.ramcosta.composedestinations.generated.destinations.NotesScreenDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import android.os.Build
import androidx.compose.foundation.combinedClickable
import dev.goodwy.rphone.bottomBarHeight
import dev.goodwy.rphone.cardCornerBig
import dev.goodwy.rphone.cardCornerSmall
import dev.goodwy.rphone.cardSpacedBy
import dev.goodwy.rphone.liquidglass.drawBackdrop
import dev.goodwy.rphone.liquidglass.effects.lens
import dev.goodwy.rphone.liquidglass.effects.colorControls
import dev.goodwy.rphone.liquidglass.highlight.Highlight
import dev.goodwy.rphone.liquidglass.LocalLiquidGlassBackdrop
import org.koin.compose.viewmodel.koinActivityViewModel
import java.util.Calendar
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.ContactsViewModel
import dev.goodwy.rphone.modal.data.CallLogEntry
import dev.goodwy.rphone.modal.data.Contact
import com.ramcosta.composedestinations.generated.destinations.CallLogFullScreenDestination
import kotlin.collections.component1
import kotlin.collections.component2

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Destination<RootGraph>(start = true, style = TabTransitionStyle::class)
@Composable
fun RecentScreen(navController: NavController, navigator: DestinationsNavigator) {
    val context = LocalContext.current
    val permState = rememberPermissionState(Manifest.permission.READ_CALL_LOG)
    val isGranted = permState.status == PermissionStatus.Granted
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val showButton by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 2
        }
    }

    val prefs = koinInject<PreferenceManager>()
    val pillNav = remember { prefs.getBoolean(PreferenceManager.KEY_PILL_NAV, false) }
    val favoritesEnabled = prefs.getBoolean(PreferenceManager.KEY_TAB_SHOW_FAVORITES, true)
    val contactsEnabled = prefs.getBoolean(PreferenceManager.KEY_TAB_SHOW_CONTACTS, true)
    val dialpadEnabled = prefs.getBoolean(PreferenceManager.KEY_TAB_SHOW_DIALPAD, true)
    val notesEnabled = prefs.getBoolean(PreferenceManager.KEY_TAB_SHOW_NOTES, true)

    var showDialpad by remember { mutableStateOf(false) }
    var fabVisible by remember { mutableStateOf(false) }
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    var isDraggingFavorite by remember { mutableStateOf(false) }
    val currentIsDraggingFavorite by rememberUpdatedState(isDraggingFavorite)

    var selectedEntries by remember { mutableStateOf(setOf<CallLogEntry>()) }

    BackHandler(enabled = selectedEntries.isNotEmpty()) {
        selectedEntries = emptySet()
    }

    LaunchedEffect(Unit) {
        fabVisible = true
        if (prefs.getBoolean(PreferenceManager.KEY_OPEN_DIALPAD_DEFAULT, false)) {
            showDialpad = true
        }
    }

    if (showDialpad) {
        ModalBottomSheet(
            onDismissRequest = { showDialpad = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = RoundedCornerShape(topStart = 38.dp, topEnd = 38.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow, //MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            scrimColor = Color.Transparent,
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
            DialPadContent(
                navigator = navigator,
                onDismiss = { showDialpad = false },
                isBottomSheet = true
            )
        }
    }

    var childHScrolling by remember { mutableStateOf(false) }
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // A child LazyRow is consuming horizontal scroll – block our swipe nav
                if (kotlin.math.abs(available.x) > kotlin.math.abs(available.y)) {
                    childHScrolling = true
                }
                return Offset.Zero
            }
        }
    }

    val showBottomBar = favoritesEnabled || contactsEnabled || dialpadEnabled ||notesEnabled
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection)
            .pointerInput(Unit) {
                // Use PointerEventPass.Final so children (LazyColumn) get events first.
                // Only trigger navigation when the horizontal movement clearly dominates
                // vertical movement, preventing accidental swipes during scrolling.
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitPointerEvent(PointerEventPass.Final).changes.firstOrNull()
                            ?: continue
                        if (!down.pressed) continue
                        val startX = down.position.x
                        val startY = down.position.y
                        val startTime = System.currentTimeMillis()
                        var triggered = false
                        childHScrolling = false // reset at start of each gesture
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Final)
                            val change = event.changes.firstOrNull() ?: break

                            // If dragging begins DURING the swipe gesture, we cancel it
                            if (currentIsDraggingFavorite) {
                                triggered = false // We're resetting it so the navigation definitely won't work
                                break // We exit the swipe loop; the outer loop will take over and wait for the button to be released
                            }

                            val dx = change.position.x - startX
                            val dy = change.position.y - startY
                            val elapsed = System.currentTimeMillis() - startTime
                            if (!triggered &&
                                !childHScrolling &&
                                elapsed >= 150L &&
                                kotlin.math.abs(dx) > 700f &&
                                kotlin.math.abs(dx) > kotlin.math.abs(dy) * 5.5f
                            ) {
                                triggered = true
                                if (showBottomBar) {
                                    if (dx < 0) {
                                        val route = when {
                                            contactsEnabled -> ContactScreenDestination.route
                                            dialpadEnabled -> DialPadScreenDestination().route
                                            notesEnabled -> NotesScreenDestination.route
                                            else -> FavoritesScreenDestination.route
                                        }
                                        scope.launch {
                                            navController.navigate(route) {
                                                popUpTo(navController.graph.findStartDestination().id) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        }
                                    } else {
                                        val route = when {
                                            favoritesEnabled -> FavoritesScreenDestination.route
                                            notesEnabled -> NotesScreenDestination.route
                                            dialpadEnabled -> DialPadScreenDestination().route
                                            else -> ContactScreenDestination.route
                                        }
                                        scope.launch {
                                            navController.navigate(route) {
                                                popUpTo(navController.graph.findStartDestination().id) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        }
                                    }
                                }
                            }
                            if (!change.pressed) {
                                childHScrolling = false
                                break
                            }
                        }
                    }
                }
            },
        topBar = {
            AnimatedContent(
                targetState = selectedEntries.isNotEmpty(),
                transitionSpec = {
                    (fadeIn() + expandVertically()) togetherWith (fadeOut() + shrinkVertically())
                },
                label = "TopBarTransition"
            ) { isSelecting ->
                if (isGranted) {
                    val viewModel: CallLogViewModel = koinActivityViewModel()
                    val selectedFilter by viewModel.selectedFilter.collectAsState()

                    if (!isSelecting) {
                        Column {
                            TopBar(navController, navigator)
                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(CallLogFilter.entries) { filter ->
                                    RillFilterChip(filter.name, selectedFilter == filter, { _ ->
                                        viewModel.setFilter(filter)
                                    })
                                }
                            }
                        }
                    } else {
                        val logs by viewModel.allCallLogs.collectAsState()
                        val blockLogVisibility = prefs.getInt(PreferenceManager.KEY_BLOCK_LOG_VISIBILITY, 0)
                        val filteredLogs = remember(logs, selectedFilter, blockLogVisibility) {
                            val baseLogs = if (blockLogVisibility == 0) logs.filter { !it.isBlocked } else logs

                            when (selectedFilter) {
                                CallLogFilter.All -> baseLogs
                                CallLogFilter.Missed -> baseLogs.filter { it.type == CallLog.Calls.MISSED_TYPE }
                                CallLogFilter.Incoming -> baseLogs.filter { it.type == CallLog.Calls.INCOMING_TYPE }
                                CallLogFilter.Outgoing -> baseLogs.filter { it.type == CallLog.Calls.OUTGOING_TYPE }
                                CallLogFilter.Rejected -> baseLogs.filter { it.type == CallLog.Calls.REJECTED_TYPE }
                                CallLogFilter.Contacts -> baseLogs.filter { it.name != null && it.name != it.number }
                            }
                        }
                        BatchCallLogActionBar(
                            selectedCount = selectedEntries.size,
                            onClearSelection = { selectedEntries = emptySet() },
                            onDelete = {
                                val allIdsToDelete = selectedEntries.flatMap { it.ids }
                                viewModel.deleteCallLogsByIds(allIdsToDelete)
                                selectedEntries = emptySet()
                            },
                            onClearAll = {
//                            viewModel.clearCallLogs()
                                // We delete only the filtered recent ones
                                val filteredIdsToDelete = filteredLogs.flatMap { it.ids }
                                viewModel.deleteCallLogsByIds(filteredIdsToDelete)
                                selectedEntries = emptySet()
                            },
                            onShare = {
                                val text = selectedEntries.map { it.number }
                                    .joinToString("\n") { it.split("|").firstOrNull() ?: it }
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text)
                                }
                                context.startActivity(
                                    Intent.createChooser(
                                        intent,
                                        "Share call logs"
                                    )
                                )
                            },
                            onCallLogs = {
                                navigator.navigate(CallLogFullScreenDestination(
                                    numbersList = selectedEntries.map {it.number}.distinct().toTypedArray()
                                ))
                            },
                            onDeselect = {
                                selectedEntries = emptySet()
                            },
                            onSelectAll = {
                                selectedEntries = filteredLogs.toSet()
                            },
                            isAllSelected = selectedEntries == filteredLogs.toSet()
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (!dialpadEnabled) {
                val globalBackdrop = LocalLiquidGlassBackdrop.current
                val settingsVer by prefs.settingsChanged.collectAsState()
                val liquidGlass = remember(settingsVer) {
                    prefs.getBoolean(
                        PreferenceManager.KEY_LIQUID_GLASS,
                        false
                    )
                }
                val lgRecentsFab = remember(settingsVer) {
                    prefs.getBoolean(
                        PreferenceManager.KEY_LG_RECENTS_FAB,
                        false
                    )
                }
                val blurEffects = remember(settingsVer) {
                    prefs.getBoolean(
                        PreferenceManager.KEY_BLUR_EFFECTS,
                        false
                    )
                }
                val blurRecentsFab = remember(settingsVer) {
                    prefs.getBoolean(
                        PreferenceManager.KEY_BLUR_RECENTS_FAB,
                        false
                    )
                }
                val fabShape = RoundedCornerShape(17.dp)
                val useLiquidGlass =
                    liquidGlass && lgRecentsFab && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && globalBackdrop != null
                val useBlur = blurEffects && blurRecentsFab && !useLiquidGlass

                val fabScale by animateFloatAsState(
                    targetValue = if (fabVisible) 1f else 0f,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label = "fabScale"
                )
                val baseModifier = Modifier
                    .scale(fabScale)
                    .then(
                        if (isLandscape) Modifier
                            .navigationBarsPadding()
                            .padding(bottom = 0.dp)
                        else if (pillNav) Modifier
                            .navigationBarsPadding()
                            .padding(bottom = if (showBottomBar) 90.dp else 0.dp)
                        else Modifier
                            .padding(bottom = if (showBottomBar) bottomBarHeight else 0.dp)
                            .then( if (showBottomBar) Modifier else Modifier.navigationBarsPadding())
                    )
                if (useLiquidGlass) {
                    Box(
                        modifier = baseModifier.drawBackdrop(
                            backdrop = globalBackdrop,
                            shape = { fabShape },
                            effects = {
                                val d = density
                                colorControls(brightness = -0.15f)
                                lens(refractionHeight = 46f * d, refractionAmount = 64f * d)
                            },
                            highlight = { Highlight.Default }
                        )
                    ) {
                        FloatingActionButton(
                            onClick = { showDialpad = true },
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.0f),
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            shape = fabShape,
//                            elevation = FloatingActionButtonDefaults.elevation(0.dp),
                        ) { Icon(Icons.Default.Dialpad, "Dialpad") }
                    }
                } else {
                    FloatingActionButton(
                        onClick = { showDialpad = true },
                        containerColor = if (useBlur)
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f)
                        else
                            MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        shape = fabShape,
//                        elevation = FloatingActionButtonDefaults.elevation(0.dp),
                        modifier = baseModifier
                    ) { Icon(Icons.Default.Dialpad, "Dialpad") }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0)
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            CallLogFullContent(
                navController = navController,
                navigator = navigator,
                isGranted = isGranted,
                onRequestPermission = { permState.launchPermissionRequest() },
                listState = listState,
                selectedEntries = selectedEntries,
                onToggleSelection = { entry ->
                    selectedEntries = if (selectedEntries.any { it.id == entry.id }) {
                        selectedEntries.filter { it.id != entry.id }.toSet()
                    } else {
                        selectedEntries + entry
                    }
                },
                isDraggingFavorite = currentIsDraggingFavorite,
                onDraggingFavoriteChange = { isDraggingFavorite = it },
            )

            ScrollToTopButton(
                modifier = Modifier
                    .then(
                        if (isLandscape) Modifier
                            .navigationBarsPadding()
                            .padding(bottom = 16.dp, end = 32.dp)
                        else if (pillNav) Modifier
                            .navigationBarsPadding()
                            .padding(bottom = 92.dp + 8.dp, end = 32.dp)
                        else Modifier
                            .navigationBarsPadding()
                            .padding(bottom = bottomBarHeight + 8.dp, end = 32.dp)
                    ),
                visible = showButton,
                onClick = {
                    scope.launch {
                        listState.animateScrollToItem(0)
                    }
                }
            )
        }
    }
}

//private fun formatDuration(totalSeconds: Long): String {
//    val hours = totalSeconds / 3600
//    val minutes = (totalSeconds % 3600) / 60
//    val seconds = totalSeconds % 60
//    return when {
//        hours > 0 -> "${hours}h ${minutes}m"
//        minutes > 0 -> "${minutes}m ${seconds}s"
//        else -> "${seconds}s"
//    }
//}

//private fun todayStartMillis(): Long {
//    val cal = Calendar.getInstance()
//    cal.set(Calendar.HOUR_OF_DAY, 0)
//    cal.set(Calendar.MINUTE, 0)
//    cal.set(Calendar.SECOND, 0)
//    cal.set(Calendar.MILLISECOND, 0)
//    return cal.timeInMillis
//}

@Composable
fun CallLogFullContent(
    navController: NavController,
    navigator: DestinationsNavigator,
    isGranted: Boolean,
    onRequestPermission: () -> Unit,
    listState: LazyListState,
    selectedEntries: Set<CallLogEntry>,
    onToggleSelection: (CallLogEntry) -> Unit,
    isDraggingFavorite: Boolean = false,
    onDraggingFavoriteChange: (Boolean) -> Unit = {},
) {
    val prefs = koinInject<PreferenceManager>()
    val favouritesEnabled = prefs.getBoolean(PreferenceManager.KEY_TAB_SHOW_FAVORITES, true)
    val contactsEnabled = prefs.getBoolean(PreferenceManager.KEY_TAB_SHOW_CONTACTS, true)

    if (isGranted) {
        val viewModel: CallLogViewModel = koinActivityViewModel()
        val logs by viewModel.allCallLogs.collectAsState()
        val selectedFilter by viewModel.selectedFilter.collectAsState()
        val context = LocalContext.current
        val telecomManager = remember { context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager }

        val contactsVM: ContactsViewModel = koinActivityViewModel()
        val settingsState by prefs.settingsChanged.collectAsState()
        LaunchedEffect(settingsState) {
            contactsVM.fetchContacts()
        }

        val allContacts by contactsVM.allContacts.collectAsState()
//        val favorites = remember(allContacts) { allContacts.filter { it.isFavorite } }
        val favorites = remember(allContacts, settingsState) {
            val favContacts = allContacts.filter { it.isFavorite }
            val order = prefs.getFavoritesOrder()
            favContacts.sortedWith(compareBy<Contact> { contact ->
                val index = order.indexOf(contact.id)
                if (index != -1) index else Int.MAX_VALUE
            }.thenBy { it.name })
        }
        var isEditingFavorites by remember { mutableStateOf(false) }

        LaunchedEffect(selectedFilter) {
            isEditingFavorites = false
        }

        // Drag-to-reorder state — declared first so LaunchedEffect can reference draggedContactId
        var draggedContactId       by remember { mutableStateOf<String?>(null) }
        var dragOffset             by remember { mutableStateOf(Offset.Zero) }
        val itemBoundsMap          = remember { mutableStateMapOf<String, Rect>() }
        var lastSwapTargetId       by remember { mutableStateOf<String?>(null) }
        var fingerAbsPos           by remember { mutableStateOf(Offset.Zero) }
        var expectedDraggedCenter  by remember { mutableStateOf(Offset.Zero) }

        // Ordered favorites — persists custom drag-to-reorder order
//        val orderedFavorites = remember { mutableStateListOf<Contact>() }
//        LaunchedEffect(favorites) {
//            // Don't touch the list while the user is actively dragging
//            if (draggedContactId != null) return@LaunchedEffect
//            val savedIds = prefs.getString(PreferenceManager.KEY_FAVORITES_ORDER, null)
//                ?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()
//            val favMap   = favorites.associateBy { it.id }
//            val ordered  = savedIds.mapNotNull { favMap[it] }
//            val newList  = ordered + favorites.filter { it.id !in savedIds.toSet() }
//            val toRemove = orderedFavorites.filter { o -> newList.none { it.id == o.id } }
//            toRemove.forEach { orderedFavorites.remove(it) }
//            newList.filter { n -> orderedFavorites.none { it.id == n.id } }
//                .forEach { orderedFavorites.add(it) }
//        }
//        fun saveFavoritesOrder() {
//            prefs.setString(PreferenceManager.KEY_FAVORITES_ORDER, orderedFavorites.joinToString(",") { it.id })
//        }

        val lazyRowState = rememberLazyListState()

        var lazyRowLayoutCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
        val density = LocalDensity.current
        val lazyRowBounds by remember(lazyRowLayoutCoords) {
            derivedStateOf {
                lazyRowLayoutCoords?.let { coords ->
                    with(density) {
                        Rect(
                            left = coords.localToWindow(Offset.Zero).x,
                            top = coords.localToWindow(Offset.Zero).y,
                            right = coords.localToWindow(Offset(coords.size.width.toFloat(), coords.size.height.toFloat())).x,
                            bottom = coords.localToWindow(Offset(coords.size.width.toFloat(), coords.size.height.toFloat())).y
                        )
                    }
                }
            }
        }

        var showSimPicker by remember { mutableStateOf(false) }
        var pendingNumber by remember { mutableStateOf<String?>(null) }
        val simPref = remember(settingsState) { prefs.getInt("default_sim", 0) }

        // Track previous filter index for slide direction
        val filterEntries = CallLogFilter.entries
//        var previousFilterIndex by remember { mutableIntStateOf(filterEntries.indexOf(selectedFilter)) }
        val blockLogVisibility = prefs.getInt(PreferenceManager.KEY_BLOCK_LOG_VISIBILITY, 0)

        val filteredLogs = remember(logs, selectedFilter, blockLogVisibility) {
            val baseLogs = if (blockLogVisibility == 0) logs.filter { !it.isBlocked } else logs

            when (selectedFilter) {
                CallLogFilter.All -> baseLogs
                CallLogFilter.Missed -> baseLogs.filter { it.type == CallLog.Calls.MISSED_TYPE }
                CallLogFilter.Incoming -> baseLogs.filter { it.type == CallLog.Calls.INCOMING_TYPE }
                CallLogFilter.Outgoing -> baseLogs.filter { it.type == CallLog.Calls.OUTGOING_TYPE }
                CallLogFilter.Rejected -> baseLogs.filter { it.type == CallLog.Calls.REJECTED_TYPE }
                CallLogFilter.Contacts -> baseLogs.filter { it.name != null && it.name != it.number }
            }
        }
        val groupedLogs = remember(filteredLogs) { filteredLogs.groupBy { context.formatDateHeader(it.date) } }

        if (showSimPicker && pendingNumber != null) {
            SimPickerDialog(
                onDismissRequest = { showSimPicker = false },
                onSimSelected = { handle ->
                    makeCall(context, pendingNumber!!, handle)
                    showSimPicker = false
                }
            )
        }

        if (logs.isEmpty()) {
            // Only show a spinner on the very first launch when no disk cache exists.
            // On subsequent opens the disk cache fills instantly so this won't be seen.
            var showSpinner by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                // Give the disk cache ~200ms to arrive; only show spinner if still empty
                kotlinx.coroutines.delay(200)
                showSpinner = true
            }
            if (showSpinner) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(36.dp), strokeWidth = 3.dp)
                }
            }
        } else {
            // Use start-of-day (midnight) for "today" so all four stat cards
            // consistently reflect the current calendar day.
//            val todayStart = remember { todayStartMillis() }
//            val todayLogs  = remember(logs) { logs.filter { it.date >= todayStart } }
//
//            val totalToday        = remember(todayLogs) { todayLogs.size }
//            val missedToday       = remember(todayLogs) { todayLogs.count { it.type == CallLog.Calls.MISSED_TYPE } }
//            val outgoingToday     = remember(todayLogs) { todayLogs.count { it.type == CallLog.Calls.OUTGOING_TYPE } }
//            val totalDurationToday = remember(todayLogs) {
//                todayLogs.filter { it.duration > 0 }.sumOf { it.duration }
//            }

            Column(modifier = Modifier.fillMaxSize()) {

                // Stat cards – visibility controlled by Call UI settings
//                val showToday    = remember(settingsState) { prefs.getBoolean(PreferenceManager.KEY_CALL_UI_SHOW_TODAY, true) }
//                val showMissed   = remember(settingsState) { prefs.getBoolean(PreferenceManager.KEY_CALL_UI_SHOW_MISSED, true) }
//                val showOutgoing = remember(settingsState) { prefs.getBoolean(PreferenceManager.KEY_CALL_UI_SHOW_OUTGOING, true) }
//                val showCallTime = remember(settingsState) { prefs.getBoolean(PreferenceManager.KEY_CALL_UI_SHOW_CALL_TIME, true) }

                // In portrait, render stat cards and pills above the list (sticky)
                // In landscape, they go inside the LazyColumn so they scroll with content
//                if (!isLandscape) {
//                    if (showToday || showMissed || showOutgoing || showCallTime) {
//                        LazyRow(
//                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
//                            horizontalArrangement = Arrangement.spacedBy(10.dp)
//                        ) {
//                            if (showToday) item { AnimatedStatCard(0L, "Today", totalToday.toString(), Icons.AutoMirrored.Filled.CallReceived, ColorBlue, Modifier.width(110.dp)) { viewModel.setFilter(CallLogFilter.All) } }
//                            if (showMissed) item { AnimatedStatCard(60L, "Missed", missedToday.toString(), Icons.AutoMirrored.Filled.CallMissed, ColorRed, Modifier.width(110.dp),
//                                if (missedToday > 0) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surfaceContainerLow
//                            ) { viewModel.setFilter(CallLogFilter.Missed) } }
//                            if (showOutgoing) item { AnimatedStatCard(120L, "Outgoing", outgoingToday.toString(), Icons.AutoMirrored.Filled.CallMade, ColorGreen, Modifier.width(110.dp)) { viewModel.setFilter(CallLogFilter.Outgoing) } }
//                            if (showCallTime) {
//                                item { AnimatedStatCard(180L, "Call Time", if (totalDurationToday > 0) formatDuration(totalDurationToday) else "0s", Icons.Default.Timer, ColorOrange, Modifier.width(110.dp)) { viewModel.setFilter(CallLogFilter.Incoming) } }
//                            }
//                        }
//                    }
//                }

                // ── Animated content: slides left/right on filter change ──────
                // On the very first data load (startup) we use a slow fade-in so the
                // list appears gracefully instead of jumping. Once the user starts
                // changing filters the normal slide transition takes over.
                var hasLoadedOnce by remember { mutableStateOf(false) }
                AnimatedContent(
                    targetState = Pair(selectedFilter, groupedLogs),
                    transitionSpec = {
                        val filterChanged = initialState.first != targetState.first
                        if (!hasLoadedOnce || !filterChanged) {
                            // Startup / data-only refresh: slow gentle fade, no slide
                            fadeIn(animationSpec = tween(600, easing = LinearOutSlowInEasing)) togetherWith
                                fadeOut(animationSpec = tween(0))
                        } else {
                            val currentIdx = filterEntries.indexOf(targetState.first)
                            val prevIdx = filterEntries.indexOf(initialState.first)
                            val goingRight = currentIdx > prevIdx
                            if (goingRight) {
                                slideInHorizontally(
                                    initialOffsetX = { it },
                                    animationSpec = tween(300, easing = FastOutSlowInEasing)
                                ) togetherWith slideOutHorizontally(
                                    targetOffsetX = { -it },
                                    animationSpec = tween(300, easing = FastOutSlowInEasing)
                                )
                            } else {
                                slideInHorizontally(
                                    initialOffsetX = { -it },
                                    animationSpec = tween(300, easing = FastOutSlowInEasing)
                                ) togetherWith slideOutHorizontally(
                                    targetOffsetX = { it },
                                    animationSpec = tween(300, easing = FastOutSlowInEasing)
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    label = "filterSlide"
                ) { (currentFilter, currentGroupedLogs) ->
                    LaunchedEffect(Unit) { hasLoadedOnce = true }
                    LaunchedEffect(selectedFilter) {
                        // Scroll to the top whenever the filter changes
                        listState.scrollToItem(0)
                    }
                    ScrollHapticsEffect(listState = listState)
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 168.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        // In landscape, stat cards and filter pills scroll with the list
//                        if (isLandscape) {
//                            if (showToday || showMissed || showOutgoing || showCallTime) {
//                                item(key = "stat_cards", contentType = "statCards") {
//                                    LazyRow(
//                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
//                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
//                                    ) {
//                                        if (showToday) item { AnimatedStatCard(0L, "Today", totalToday.toString(), Icons.AutoMirrored.Filled.CallReceived, ColorBlue, Modifier.width(110.dp)) { viewModel.setFilter(CallLogFilter.All) } }
//                                        if (showMissed) item { AnimatedStatCard(60L, "Missed", missedToday.toString(), Icons.AutoMirrored.Filled.CallMissed, ColorRed, Modifier.width(110.dp),
//                                            if (missedToday > 0) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surfaceContainerLow
//                                        ) { viewModel.setFilter(CallLogFilter.Missed) } }
//                                        if (showOutgoing) item { AnimatedStatCard(120L, "Outgoing", outgoingToday.toString(), Icons.AutoMirrored.Filled.CallMade, ColorGreen, Modifier.width(110.dp)) { viewModel.setFilter(CallLogFilter.Outgoing) } }
//                                        if (showCallTime) {
//                                            item { AnimatedStatCard(180L, "Call Time", if (totalDurationToday > 0) formatDuration(totalDurationToday) else "0s", Icons.Default.Timer, ColorOrange, Modifier.width(110.dp)) { viewModel.setFilter(CallLogFilter.Incoming) } }
//                                        }
//                                    }
//                                }
//                            }
//                        }
                        if (!favouritesEnabled && favorites.isNotEmpty() && selectedFilter == CallLogFilter.All) {
                            item {
//                                RivoSectionHeader(
//                                    title = stringResource(R.string.favorites),
//                                    trailingContent = {
//                                        TextButton(
//                                            onClick = { isEditingFavorites = !isEditingFavorites },
//                                            modifier = Modifier.padding(end = 8.dp)
//                                        ) {
//                                            Text(
//                                                text = if (isEditingFavorites) "Done" else "Edit",
//                                                style = MaterialTheme.typography.labelLarge,
//                                                fontWeight = FontWeight.Bold,
//                                                color = MaterialTheme.colorScheme.primary
//                                            )
//                                        }
//                                    }
//                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth().padding(end = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
//                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = stringResource(R.string.favorites),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 36.dp, vertical = 4.dp)
                                    )
                                    Spacer(Modifier.weight(1f))
                                    if (!contactsEnabled) {
                                        Surface(
                                            modifier = Modifier.combinedClickable(
                                                onClick = {
                                                    navController.navigate(ContactScreenDestination.route) {
                                                        popUpTo(navController.graph.findStartDestination().id) {
                                                            saveState = true
                                                        }
                                                        launchSingleTop = true
                                                        restoreState = true
                                                    }
                                                },
                                                interactionSource = null,
                                                indication = null,
                                            ),
                                            shape = RoundedCornerShape(20.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                                        ) {
                                            Text(
                                                modifier = Modifier.padding(
                                                    horizontal = 16.dp,
                                                    vertical = 5.dp
                                                ),
                                                text = stringResource(R.string.view_contacts),
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                        Spacer(Modifier.width(12.dp))
                                    }
                                    Surface(
                                        modifier = Modifier.combinedClickable(
                                            onClick = { isEditingFavorites = !isEditingFavorites },
                                            interactionSource = null,
                                            indication = null,
                                        ),
                                        shape = RoundedCornerShape(20.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                                    ) {
                                        Text(
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp),
                                            text = if (isEditingFavorites) stringResource(R.string.done)
                                                    else stringResource(R.string.edit),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                                IPhoneFavoritesRow(
                                    favorites = favorites,
                                    isEditing = isEditingFavorites,
                                    onUnfavorite = { contact ->
                                        contactsVM.toggleFavorite(contact)
                                    },
                                    onSaveOrder = { newOrder ->
                                        prefs.setFavoritesOrder(newOrder)
                                    },
                                    onClick = { contact ->
//                                        callLauncher.dial(contact.phoneNumbers.firstOrNull() ?: "", contact)
                                        val phoneNumber =
                                            contact.phoneNumbers.firstOrNull()
                                        if (phoneNumber != null) {
                                            placeCallWithSimPreference(
                                                context,
                                                phoneNumber,
                                                simPref
                                            ) {
                                                pendingNumber =
                                                    phoneNumber; showSimPicker = true
                                            }
                                        } else {
                                            navigator.navigate(
                                                ContactDetailsScreenDestination(
                                                    contactId = contact.id
                                                )
                                            )
                                        }
                                    },
                                    isDragging = isDraggingFavorite,
                                    onDraggingChange = onDraggingFavoriteChange
                                )
                            }
                        }

//                        if (showFavorite && currentFilter == CallLogFilter.All && favorites.isNotEmpty()) {
//                            item(key = "favorites_pills", contentType = "favoritesPills") {
//                                Column(
//                                    modifier = Modifier
//                                        .fillMaxSize()
//                                        .padding(bottom = 2.dp)
//                                ) {
//                                    Row(
//                                        modifier = Modifier
//                                            .fillMaxWidth().padding(end = 16.dp),
//                                        verticalAlignment = Alignment.CenterVertically,
//                                        horizontalArrangement = Arrangement.SpaceBetween
//                                    ) {
//                                        Text(
//                                            text = stringResource(R.string.favorites),
//                                            style = MaterialTheme.typography.labelLarge,
//                                            color = MaterialTheme.colorScheme.primary,
//                                            fontWeight = FontWeight.Bold,
//                                            modifier = Modifier.padding(horizontal = 36.dp, vertical = 4.dp)
//                                        )
//                                        Surface(
//                                            modifier = Modifier.combinedClickable(
//                                                onClick = {
//                                                    navController.navigate(ContactScreenDestination.route) {
//                                                        popUpTo(navController.graph.findStartDestination().id) {
//                                                            saveState = true
//                                                        }
//                                                        launchSingleTop = true
//                                                        restoreState = true
//                                                    }
//                                                },
//                                                interactionSource = null,
//                                                indication = null,
//                                            ),
//                                            shape = RoundedCornerShape(20.dp),
//                                            color = MaterialTheme.colorScheme.surfaceContainerHigh
//                                        ) {
//                                            Text(
//                                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp),
//                                                text = stringResource(R.string.view_contacts),
//                                                style = MaterialTheme.typography.labelMedium,
//                                                fontWeight = FontWeight.SemiBold,
//                                                color = MaterialTheme.colorScheme.onPrimaryContainer
//                                            )
//                                        }
//                                    }
//                                    LazyRow(
//                                        state = lazyRowState,
//                                        userScrollEnabled = draggedFavoriteId == null,
//                                        modifier = Modifier
//                                            .fillMaxWidth()
//                                            .height(108.dp)
//                                            .onGloballyPositioned { coords ->
//                                                lazyRowLayoutCoords = coords
//                                            },
//                                        contentPadding = PaddingValues(
//                                            horizontal = 24.dp,
//                                            vertical = 12.dp
//                                        ),
//                                        horizontalArrangement = Arrangement.spacedBy(20.dp)
//                                    ) {
//                                        items(orderedFavorites, key = { it.id }) { contact ->
//                                            RillScrollAnimatedItem(
//                                                modifier = Modifier.zIndex(
//                                                    if (currentDraggedFavoriteId == contact.id) 10f else 0f
//                                                )
//                                            ) {
//                                                FavoriteContactCard(
//                                                    modifier = Modifier
//                                                        .width(60.dp)
//                                                        .wrapContentHeight(),
//                                                    contact = contact,
//                                                    navigator = navigator,
//                                                    context = context,
//                                                    isDragging = currentDraggedFavoriteId == contact.id,
//                                                    dragOffset = if (currentDraggedFavoriteId == contact.id) dragOffset else null,
//                                                    onBoundsChanged = { bounds -> itemBoundsMap[contact.id] = bounds },
//                                                    onDragStart = { _ ->
//                                                        onDraggedFavoriteIdChange(contact.id)
//                                                        dragOffset = Offset.Zero
//                                                        val center = itemBoundsMap[contact.id]?.center ?: Offset.Zero
//                                                        fingerAbsPos = center
//                                                        expectedDraggedCenter = center
//                                                        lastSwapTargetId = null
//                                                    },
//                                                    onDrag = { amt ->
//                                                        if (currentDraggedFavoriteId == contact.id) {
//                                                            fingerAbsPos += amt
//                                                            // dragOffset = finger position minus expected card center
//                                                            // expectedDraggedCenter is updated synchronously on swap,
//                                                            // eliminating the 1-frame position jump that causes flicker
//                                                            dragOffset = fingerAbsPos - expectedDraggedCenter
//
//                                                            val targetId = itemBoundsMap.entries
//                                                                .filter { it.key != currentDraggedFavoriteId }
//                                                                .firstOrNull { (_, b) -> b.contains(fingerAbsPos) }
//                                                                ?.key
//
//                                                            if (targetId != null && targetId != lastSwapTargetId) {
//                                                                // Update expectedDraggedCenter to target's current bounds BEFORE swap
//                                                                // so dragOffset compensates immediately in this same frame
//                                                                val targetCenter = itemBoundsMap[targetId]?.center
//                                                                if (targetCenter != null) {
//                                                                    expectedDraggedCenter = targetCenter
//                                                                    dragOffset = fingerAbsPos - expectedDraggedCenter
//                                                                }
//                                                                lastSwapTargetId = targetId
//                                                                val fromIdx = orderedFavorites.indexOfFirst { it.id == currentDraggedFavoriteId }
//                                                                val toIdx   = orderedFavorites.indexOfFirst { it.id == targetId }
//                                                                if (fromIdx != -1 && toIdx != -1) {
//                                                                    orderedFavorites.add(toIdx, orderedFavorites.removeAt(fromIdx))
//                                                                }
//                                                            }
//                                                        }
//                                                    },
//                                                    onDragEnd = {
//                                                        onDraggedFavoriteIdChange(null)
//                                                        dragOffset = Offset.Zero
//                                                        fingerAbsPos = Offset.Zero
//                                                        expectedDraggedCenter = Offset.Zero
//                                                        lastSwapTargetId = null
//                                                        saveFavoritesOrder()
//                                                    },
//                                                    onDragCancel = {
//                                                        onDraggedFavoriteIdChange(null)
//                                                        dragOffset = Offset.Zero
//                                                        fingerAbsPos = Offset.Zero
//                                                        expectedDraggedCenter = Offset.Zero
//                                                        lastSwapTargetId = null
//                                                    },
//                                                    onClick = {
//                                                        val phoneNumber =
//                                                            contact.phoneNumbers.firstOrNull()
//                                                        if (phoneNumber != null) {
//                                                            placeCallWithSimPreference(
//                                                                context,
//                                                                phoneNumber,
//                                                                simPref
//                                                            ) {
//                                                                pendingNumber =
//                                                                    phoneNumber; showSimPicker = true
//                                                            }
//                                                        } else {
//                                                            navigator.navigate(
//                                                                ContactDetailsScreenDestination(
//                                                                    contactId = contact.id
//                                                                )
//                                                            )
//                                                        }
//                                                    },
//                                                    onToggleFavorite = {
//                                                        contactsVM.toggleFavorite(contact)
//                                                    }
//                                                )
//                                            }
//                                        }
//                                    }
//                                }
//                            }
//                        }

                        val directCall = prefs.getBoolean(PreferenceManager.KEY_DIRECT_CALL_ON_TAP, false)
                        currentGroupedLogs.forEach { (header, logsInGroup) ->
                            // Section header as its own item
                            item(key = "header_$header", contentType = "sectionHeader") {
                                RillScrollAnimatedItem {
                                    RillSectionHeader(title = header)
                                }
                            }
                            // Individual items per log entry with per-item rounded corners
                            logsInGroup.forEachIndexed { index, lg ->
                                val isFirst = index == 0
                                val isLast = index == logsInGroup.size - 1
                                val topStart = if (isFirst) cardCornerBig else cardCornerSmall
                                val topEnd = if (isFirst) cardCornerBig else cardCornerSmall
                                val bottomStart = if (isLast) cardCornerBig else cardCornerSmall
                                val bottomEnd = if (isLast) cardCornerBig else cardCornerSmall
                                val bottomPadding = if (!isLast) cardSpacedBy else 0.dp
                                item(
                                    key = "log_${lg.number}_${lg.date}_${index}",
                                    contentType = "callLogEntry"
                                ) {
                                    val isSelected = selectedEntries.any { it.id == lg.id }
                                    val selectionMode = selectedEntries.isNotEmpty()
                                    RillScrollAnimatedItem(delayMs = (index.coerceAtMost(5) * 30).toLong()) {
                                        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                            Surface(
                                                shape = RoundedCornerShape(
                                                    topStart = if (isSelected) cardCornerBig else topStart,
                                                    topEnd = if (isSelected) cardCornerBig else topEnd,
                                                    bottomStart = if (isSelected) cardCornerBig else bottomStart,
                                                    bottomEnd = if (isSelected) cardCornerBig else bottomEnd
                                                ),
                                                color = MaterialTheme.colorScheme.surface,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(bottom = bottomPadding)
                                            ) {
                                                CallLogTile(
                                                    log = lg,
                                                    isSelected = isSelected,
                                                    selectionMode = selectionMode,
                                                    directCall = directCall,
                                                    onTileClick = { log ->
                                                        if (selectionMode) {
                                                            onToggleSelection(log)
                                                        } else {
                                                            navigator.navigate(ContactDetailsScreenDestination(contactId = log.contactId ?: "null", phoneNumber = log.number))
                                                        }
                                                    },
                                                    onLongClick = { log ->
                                                        onToggleSelection(log)
                                                    },
                                                    onAvatarClick = { log ->
                                                        if (log.contactId != null) {
                                                            navigator.navigate(ContactDetailsScreenDestination(contactId = log.contactId, phoneNumber = log.number))
                                                        } else {
                                                            navigator.navigate(ContactEditScreenDestination(initialPhone = log.number))
                                                        }
                                                    },
                                                    onCallClick = { log ->
                                                        if (selectionMode) {
                                                            onToggleSelection(log)
                                                        } else {
                                                            placeCallWithSimPreference(context, log.number, simPref) {
                                                                pendingNumber = log.number; showSimPicker = true
                                                            }
                                                        }
                                                    },
                                                    onDelete = { viewModel.refreshLogs() },
                                                    onShowHistory = {
                                                        val contactId = lg.contactId
                                                        val phoneNumber = lg.number
//                                                        navController.navigate("call_log_detail_screen?contactId=${contactId ?: "null"}&phoneNumber=${phoneNumber}")
                                                        navigator.navigate(CallLogFullScreenDestination(
                                                            contactId = contactId,
                                                            phoneNumber = phoneNumber
                                                        ))
                                                    },
                                                )
                                            }
                                        }
                                    }
                                    if (isLast) Spacer(modifier = Modifier.height(12.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    } else {
        PermissionDeniedView(
            icon = Icons.Default.Call,
            title = stringResource(R.string.call_history),
            description = stringResource(R.string.call_history_permission),
            onGrantClick = onRequestPermission
        )
    }
}

//@Composable
//private fun AnimatedStatCard(
//    delayMs: Long,
//    label: String,
//    value: String,
//    icon: ImageVector,
//    iconTint: Color,
//    modifier: Modifier = Modifier,
//    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
//    onClick: () -> Unit = {}
//) {
//    var visible by remember { mutableStateOf(false) }
//    LaunchedEffect(Unit) { delay(delayMs); visible = true }
//    val cardAlpha by animateFloatAsState(if (visible) 1f else 0f, tween(350), label = "statAlpha")
//    val cardOffset by animateDpAsState(if (visible) 0.dp else 16.dp, spring(stiffness = Spring.StiffnessMediumLow), label = "statOffset")
//    Box(modifier = Modifier
//        .alpha(cardAlpha)
//        .offset(y = cardOffset)) {
//        Surface(onClick = onClick, shape = RoundedCornerShape(20.dp), color = containerColor, modifier = modifier) {
//            RillStatCard(label = label, value = value, icon = icon, iconTint = iconTint, containerColor = Color.Transparent, modifier = Modifier.fillMaxWidth())
//        }
//    }
//}
