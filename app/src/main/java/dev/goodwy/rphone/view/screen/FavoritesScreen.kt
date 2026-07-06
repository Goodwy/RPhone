package dev.goodwy.rphone.view.screen

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.telecom.TelecomManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import dev.goodwy.rphone.controller.ContactsViewModel
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.controller.util.makeCall
import dev.goodwy.rphone.controller.util.placeCallWithSimPreference
import dev.goodwy.rphone.modal.data.Contact
import dev.goodwy.rphone.view.components.RillAvatar
import dev.goodwy.rphone.view.components.RillScrollAnimatedItem
import dev.goodwy.rphone.view.components.ScrollHapticsGridEffect
import dev.goodwy.rphone.view.components.SimPickerDialog
import dev.goodwy.rphone.view.components.TopBar
import dev.goodwy.rphone.view.theme.TabTransitionStyle
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.zIndex
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.ContactDetailsScreenDestination
import com.ramcosta.composedestinations.generated.destinations.ContactScreenDestination
import com.ramcosta.composedestinations.generated.destinations.DialPadScreenDestination
import com.ramcosta.composedestinations.generated.destinations.RecentScreenDestination
import com.ramcosta.composedestinations.generated.destinations.NotesScreenDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinActivityViewModel
import kotlin.math.abs
import androidx.core.net.toUri
import dev.goodwy.rphone.R
import dev.goodwy.rphone.view.components.PlaceholderView
import com.ramcosta.composedestinations.generated.destinations.ContactEditScreenDestination
import com.ramcosta.composedestinations.generated.destinations.ContactEditScreenDestination.invoke
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay

@Destination<RootGraph>(style = TabTransitionStyle::class)
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FavoritesScreen(navController: NavController, navigator: DestinationsNavigator) {
    val contactsVM: ContactsViewModel = koinActivityViewModel()
    val allContacts by contactsVM.allContacts.collectAsState()
    val favorites = remember(allContacts) { allContacts.filter { it.isFavorite } }
    val scope = rememberCoroutineScope()
    val prefs = koinInject<PreferenceManager>()
    val settingsVersion by prefs.settingsChanged.collectAsState()

    LaunchedEffect(settingsVersion) {
        contactsVM.fetchContacts()
    }

    // Drag-to-reorder state — declared first so LaunchedEffect can reference draggedContactId
    var draggedContactId       by remember { mutableStateOf<String?>(null) }
    var dragOffset             by remember { mutableStateOf(Offset.Zero) }
    val itemBoundsMap          = remember { mutableStateMapOf<String, Rect>() }
    var lastSwapTargetId       by remember { mutableStateOf<String?>(null) }
    var fingerAbsPos           by remember { mutableStateOf(Offset.Zero) }
    var expectedDraggedCenter  by remember { mutableStateOf(Offset.Zero) }

    // Ordered favorites — persists custom drag-to-reorder order
    val orderedFavorites = remember { mutableStateListOf<Contact>() }
    LaunchedEffect(favorites) {
        // Don't touch the list while the user is actively dragging
        if (draggedContactId != null) return@LaunchedEffect
        val savedIds = prefs.getString(PreferenceManager.KEY_FAVORITES_ORDER, null)
            ?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()
        val favMap   = favorites.associateBy { it.id }
        val ordered  = savedIds.mapNotNull { favMap[it] }
        val newList  = ordered + favorites.filter { it.id !in savedIds.toSet() }
        val toRemove = orderedFavorites.filter { o -> newList.none { it.id == o.id } }
        toRemove.forEach { orderedFavorites.remove(it) }
        newList.filter { n -> orderedFavorites.none { it.id == n.id } }
            .forEach { orderedFavorites.add(it) }
    }

    val dragNestedScroll = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
                if (draggedContactId != null) available else Offset.Zero
        }
    }

    fun saveFavoritesOrder() {
        prefs.setString(PreferenceManager.KEY_FAVORITES_ORDER, orderedFavorites.joinToString(",") { it.id })
    }
    val contactsEnabled = prefs.getBoolean(PreferenceManager.KEY_TAB_SHOW_CONTACTS, true)
    val dialpadEnabled = prefs.getBoolean(PreferenceManager.KEY_TAB_SHOW_DIALPAD, true)
    val notesEnabled = prefs.getBoolean(PreferenceManager.KEY_TAB_SHOW_NOTES, true)
    val context = LocalContext.current

    var showSimPicker by remember { mutableStateOf(false) }
    var pendingCallNumber by remember { mutableStateOf<String?>(null) }
    val simPref = remember { prefs.getInt("default_sim", 0) }

    var selectedIds by remember { mutableStateOf(setOf<String>()) }

    BackHandler(enabled = selectedIds.isNotEmpty()) {
        selectedIds = emptySet()
    }

    val callPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.CALL_PHONE] == true) {
            pendingCallNumber?.let { num ->
                placeCallWithSimPreference(context, num, simPref) {
                    showSimPicker = true
                }
            }
        }
    }

    if (showSimPicker && pendingCallNumber != null) {
        val telecomManager = remember { context.getSystemService(android.content.Context.TELECOM_SERVICE) as TelecomManager }
        SimPickerDialog(
            onDismissRequest = { showSimPicker = false },
            onSimSelected = { handle ->
                makeCall(context, pendingCallNumber!!, handle)
                showSimPicker = false
            }
        )
    }

    val pillNav = remember { prefs.getBoolean(PreferenceManager.KEY_PILL_NAV, false) }
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitPointerEvent(PointerEventPass.Final).changes.firstOrNull() ?: continue
                        if (!down.pressed) continue
                        val startX = down.position.x
                        val startY = down.position.y
                        val startTime = System.currentTimeMillis()
                        var triggered = false
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Final)
                            val change = event.changes.firstOrNull() ?: break

                            // Never swipe tabs while a drag-to-reorder is in progress
                            if (draggedContactId != null) { if (!change.pressed) break; continue }

                            val dx = change.position.x - startX
                            val dy = change.position.y - startY
                            val elapsed = System.currentTimeMillis() - startTime
                            if (!triggered && elapsed >= 150L &&
                                abs(dx) > 700f &&
                                abs(dx) > abs(dy) * 5.5f
                            ) {
                                triggered = true
                                if (dx < 0) {
                                    scope.launch {
                                        navController.navigate(RecentScreenDestination.route) {
                                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                            launchSingleTop = true; restoreState = true
                                        }
                                    }
                                } else {
                                    val route = when {
                                        notesEnabled -> NotesScreenDestination.route
                                        dialpadEnabled -> DialPadScreenDestination().route
                                        contactsEnabled -> ContactScreenDestination.route
                                        else -> RecentScreenDestination.route
                                    }
                                    scope.launch {
                                        navController.navigate(route) {
                                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                            launchSingleTop = true; restoreState = true
                                        }
                                    }
                                }
                            }
                            if (!change.pressed) break
                        }
                    }
                }
            },
        topBar = {
            AnimatedContent(
                targetState = selectedIds.isNotEmpty(),
                transitionSpec = {
                    (fadeIn() + expandVertically()) togetherWith (fadeOut() + shrinkVertically())
                },
                label = "TopBarTransition"
            ) { isSelecting ->
                if (!isSelecting) {
                    TopBar(navController, navigator)
                } else {
                    BatchActionBar(
                        selectedCount = selectedIds.size,
                        onClear = { selectedIds = emptySet() },
                        onDelete = {
                            contactsVM.deleteContacts(selectedIds.toList())
                            selectedIds = emptySet()
                        },
//                        onMove = { account ->
//                            contactsVM.moveContacts(selectedIds.toList(), account)
//                            selectedIds = emptySet()
//                        },
                        onRemoveFromFav = {
                            favorites.filter { selectedIds.contains(it.id) }.forEach { contactsVM.toggleFavorite(it) }
                            selectedIds = emptySet()
                        },
                        availableAccounts = contactsVM.availableAccountsForMoving.collectAsState().value,
                        onShare = {
                            val text = favorites.filter { selectedIds.contains(it.id) }.joinToString("\n") { "${it.name}: ${it.phoneNumbers.firstOrNull() ?: ""}" }
                            val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
                            context.startActivity(Intent.createChooser(intent, "Share contacts"))
                        },
                        onDeselect = {
                            selectedIds = emptySet()
                        },
                        onSelectAll = {
                            selectedIds = favorites.map { it.id }.toSet()
                        },
                        isAllSelected = selectedIds == favorites.map { it.id }.toSet()
                    )
                }
            }

//            AnimatedVisibility(
//                visible = selectionMode,
//                enter = slideInVertically(initialOffsetY = { -it }, animationSpec = tween(320, easing = FastOutSlowInEasing)) + fadeIn(animationSpec = tween(280, easing = FastOutSlowInEasing)),
//                exit  = slideOutVertically(targetOffsetY = { -it }, animationSpec = tween(420, easing = FastOutLinearInEasing)) + fadeOut(animationSpec = tween(380, easing = FastOutLinearInEasing)),
//                modifier = Modifier.fillMaxWidth().zIndex(10f)
//            ) {
//                Surface(color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
//                    Row(
//                        modifier = Modifier.fillMaxWidth()
//                            .statusBarsPadding()
//                            .padding(horizontal = 8.dp, vertical = 4.dp),
//                        verticalAlignment = Alignment.CenterVertically
//                    ) {
//                        IconButton(onClick = { selectionMode = false; selectedFavorites = emptySet() }) {
//                            Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
//                        }
//                        Text("${selectedFavorites.size} selected", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.weight(1f))
//                        Box {
//                            IconButton(onClick = { showFavSelectionMenu = true }) {
//                                Icon(Icons.Default.MoreVert, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
//                            }
//                            DropdownMenu(shape = RoundedCornerShape(16.dp), expanded = showFavSelectionMenu, onDismissRequest = { showFavSelectionMenu = false }) {
//                                DropdownMenuItem(text = { Text(stringResource(R.string.remove_from_favourites)) }, leadingIcon = { Icon(Icons.Default.Star, null, tint = MaterialTheme.colorScheme.error) }, onClick = { showFavSelectionMenu = false; if (selectedFavorites.isNotEmpty()) showFavDeleteConfirm = true })
//                                DropdownMenuItem(text = { Text("Select All") }, leadingIcon = { Icon(Icons.Default.SelectAll, null) }, onClick = { showFavSelectionMenu = false; selectedFavorites = favorites.map { it.id }.toSet() })
//                            }
//                        }
//                    }
//                }
//            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0)
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            if (favorites.isEmpty()) {
                PlaceholderView(
                    icon = Icons.Default.Star,
                    title = "No Favorites Yet",
                    description = "Star a contact to add them here"
                )
            } else {
                val configuration = LocalConfiguration.current
                val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                val columns = if (isLandscape) 6 else 4
                val gridState = rememberLazyGridState()
                val selectionMode = selectedIds.isNotEmpty()
                ScrollHapticsGridEffect(gridState = gridState)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    state = gridState,
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(dragNestedScroll),
                    contentPadding = PaddingValues(top = 24.dp, start = 24.dp, end = 24.dp, bottom = 168.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    items(orderedFavorites, key = { it.id }) { contact ->
                        RillScrollAnimatedItem(
                            modifier = Modifier.zIndex(
                                if (draggedContactId == contact.id) 10f else 0f
                            )
                        ) {
                            val isSelected = selectedIds.contains(contact.id)
                            FavoriteContactCard(
                                contact = contact,
                                navigator = navigator,
                                context = context,
                                isSelected = isSelected,
                                selectionMode = selectionMode,
                                isDragging = draggedContactId == contact.id,
                                dragOffset = if (draggedContactId == contact.id) dragOffset else null,
                                onBoundsChanged = { bounds -> itemBoundsMap[contact.id] = bounds },
                                onDragStart = { _ ->
                                    draggedContactId = contact.id
                                    dragOffset = Offset.Zero
                                    val center = itemBoundsMap[contact.id]?.center ?: Offset.Zero
                                    fingerAbsPos = center
                                    expectedDraggedCenter = center
                                    lastSwapTargetId = null
                                },
                                onDrag = { amt ->
                                    if (draggedContactId == contact.id) {
                                        fingerAbsPos += amt
                                        // dragOffset = finger position minus expected card center
                                        // expectedDraggedCenter is updated synchronously on swap,
                                        // eliminating the 1-frame position jump that causes flicker
                                        dragOffset = fingerAbsPos - expectedDraggedCenter

                                        val targetId = itemBoundsMap.entries
                                            .filter { it.key != draggedContactId }
                                            .firstOrNull { (_, b) -> b.contains(fingerAbsPos) }
                                            ?.key

                                        if (targetId != null && targetId != lastSwapTargetId) {
                                            // Update expectedDraggedCenter to target's current bounds BEFORE swap
                                            // so dragOffset compensates immediately in this same frame
                                            val targetCenter = itemBoundsMap[targetId]?.center
                                            if (targetCenter != null) {
                                                expectedDraggedCenter = targetCenter
                                                dragOffset = fingerAbsPos - expectedDraggedCenter
                                            }
                                            lastSwapTargetId = targetId
                                            val fromIdx = orderedFavorites.indexOfFirst { it.id == draggedContactId }
                                            val toIdx   = orderedFavorites.indexOfFirst { it.id == targetId }
                                            if (fromIdx != -1 && toIdx != -1) {
                                                orderedFavorites.add(toIdx, orderedFavorites.removeAt(fromIdx))
                                            }
                                        }
                                    }
                                },
                                onDragEnd = {
                                    draggedContactId = null
                                    dragOffset = Offset.Zero
                                    fingerAbsPos = Offset.Zero
                                    expectedDraggedCenter = Offset.Zero
                                    lastSwapTargetId = null
                                    saveFavoritesOrder()
                                },
                                onDragCancel = {
                                    draggedContactId = null
                                    dragOffset = Offset.Zero
                                    fingerAbsPos = Offset.Zero
                                    expectedDraggedCenter = Offset.Zero
                                    lastSwapTargetId = null
                                },
                                onSelectToggle = { id ->
                                    selectedIds = if (selectedIds.contains(id)) {
                                        selectedIds - id
                                    } else {
                                        selectedIds + id
                                    }
                                },
                                onClick = {
//                                    val directCall = prefs.getBoolean(PreferenceManager.KEY_DIRECT_CALL_ON_TAP, false)
                                    val phoneNumber = contact.phoneNumbers.firstOrNull()
                                    if (/*directCall &&*/ phoneNumber != null) {
                                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                                            placeCallWithSimPreference(context, phoneNumber, simPref) {
                                                pendingCallNumber = phoneNumber
                                                showSimPicker = true
                                            }
                                        } else {
                                            pendingCallNumber = phoneNumber
                                            callPermissionLauncher.launch(arrayOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_PHONE_STATE))
                                        }
                                    } else {
                                        navigator.navigate(ContactDetailsScreenDestination(contactId = contact.id))
                                    }
                                },
                                onToggleFavorite = {
                                    contactsVM.toggleFavorite(contact)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FavoriteContactCard(
    contact: Contact,
    navigator: DestinationsNavigator,
    context: android.content.Context,
    isSelected: Boolean = false,
    selectionMode: Boolean = false,
    isDragging: Boolean = false,
    dragOffset: Offset? = null,
    onBoundsChanged: ((Rect) -> Unit)? = null,
    onDragStart: ((Offset) -> Unit)? = null,
    onDrag: ((Offset) -> Unit)? = null,
    onDragEnd: (() -> Unit)? = null,
    onDragCancel: (() -> Unit)? = null,
    onSelectToggle: ((String) -> Unit)? = null,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    var visible by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(300), label = "cardAlpha")
    LaunchedEffect(Unit) { visible = true }

    var isPressed by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var isDraggingLocally by remember { mutableStateOf(false) }
    var localBounds by remember { mutableStateOf<Rect?>(null) }
    val haptic = LocalHapticFeedback.current

    val scale by animateFloatAsState(
        targetValue = when {
            isDragging -> 1.08f
            isPressed || showMenu -> 0.93f
            else -> 1f
        },
        animationSpec = spring(stiffness = Spring.StiffnessMedium, dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "cardScale"
    )
    // Selection highlight
    val cardBgColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                      else MaterialTheme.colorScheme.surface,
        animationSpec = tween(200),
        label = "cardBg"
    )

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = cardBgColor,
//        tonalElevation = 2.dp,
        modifier = modifier
            .alpha(alpha)
            .scale(scale)
//            .zIndex(if (isDragging) 10f else 0f)
            .graphicsLayer {
                if (isDragging && dragOffset != null) {
                    translationX = dragOffset.x
                    translationY = dragOffset.y
                    scaleX = 1.08f
                    scaleY = 1.08f
                    shadowElevation = 16f
                    shape = RoundedCornerShape(16.dp)
                }
            }
            .onGloballyPositioned { coords ->
                val bounds = coords.boundsInWindow()
                localBounds = bounds
                onBoundsChanged?.invoke(bounds)
            }
            .pointerInput(selectionMode, contact.id) {
                val pis = this
                coroutineScope {
                    val cs = this
                    pis.awaitPointerEventScope {
                        while (true) {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            isPressed = true
                            val startPos = down.position
                            val touchSlop = viewConfiguration.touchSlop
                            var longPressTriggered = false
                            var dragStarted = false

                            val longPressJob = cs.launch {
                                delay(viewConfiguration.longPressTimeoutMillis)
                                longPressTriggered = true
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                isPressed = false
                                if (selectionMode) onSelectToggle?.invoke(contact.id) else showMenu = true
                            }

                            loop@ while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id }

                                if (change == null || !change.pressed) {
                                    longPressJob.cancel()
                                    isPressed = false
                                    when {
                                        dragStarted -> { isDraggingLocally = false; onDragEnd?.invoke() }
                                        !longPressTriggered -> { if (selectionMode) onSelectToggle?.invoke(contact.id) else onClick() }
                                        // long press without drag: menu already shown in job
                                    }
                                    break@loop
                                }

                                val totalDist = (change.position - startPos).getDistance()
                                val delta = change.position - change.previousPosition

                                if (!dragStarted && totalDist > touchSlop) {
                                    if (longPressTriggered && !selectionMode) {
                                        showMenu = false
                                        dragStarted = true
                                        isDraggingLocally = true
                                        isPressed = false
                                        longPressJob.cancel()
                                        onDragStart?.invoke(startPos)
                                    } else if (!longPressTriggered) {
                                        longPressJob.cancel()
                                        isPressed = false
                                        break@loop
                                    }
                                }

                                if (dragStarted) {
                                    change.consume()
                                    onDrag?.invoke(delta)
                                }
                            }
                        }
                    }
                }
            }
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
                RillAvatar(
                    name = contact.name,
                    photoUri = contact.photoUri,
                    modifier = Modifier.fillMaxSize().padding(2.dp),
                    shape = CircleShape
                )
                if (isSelected) {
                    var appeared by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) { appeared = true }
                    val iconScale by animateFloatAsState(
                        targetValue = if (appeared) 1f else 0.5f,
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        label = "iconScale"
                    )
                    val iconAlpha by animateFloatAsState(
                        targetValue = if (appeared) 1f else 0f,
                        animationSpec = tween(250),
                        label = "iconAlpha"
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .clip(CircleShape)
                            .scale(iconScale)
                            .alpha(iconAlpha)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Rounded.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                }
            }
            Text(
                text = contact.name,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }

    val density = LocalDensity.current
    val menuOffsetY by remember {
        derivedStateOf {
            with(density) {
                // Move the menu down by the card height
                (localBounds?.height ?: 0f).toDp()
            }
        }
    }
    AnimatedVisibility(
        visible = showMenu,
        enter = slideInVertically(initialOffsetY = { -it }, animationSpec = tween(320, easing = FastOutSlowInEasing)) + fadeIn(tween(280)),
        exit  = slideOutVertically(targetOffsetY = { -it }, animationSpec = tween(420, easing = FastOutLinearInEasing)) + fadeOut(tween(380))
    ) {
        DropdownMenu(
            shape = RoundedCornerShape(16.dp),
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            offset = DpOffset(0.dp, menuOffsetY),
        ) {
            if (onSelectToggle != null) {
                DropdownMenuItem(
                    contentPadding = PaddingValues(start = 20.dp, end = 26.dp),
                    text = { Text(stringResource(R.string.select)) },
                    leadingIcon = { Icon(Icons.Default.CheckBox, null) },
                    onClick = {
                        showMenu = false
                        onSelectToggle(contact.id)
                    }
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
            }
            DropdownMenuItem(
                contentPadding = PaddingValues(start = 20.dp, end = 26.dp),
                text = { Text("Call") },
                leadingIcon = { Icon(Icons.Rounded.Call, null) },
                onClick = {
                    showMenu = false
                    onClick()
                }
            )
            val phoneNumber = contact.phoneNumbers.firstOrNull()
            if (!phoneNumber.isNullOrEmpty()) {
                DropdownMenuItem(
                    contentPadding = PaddingValues(start = 20.dp, end = 26.dp),
                    text = { Text("Send SMS") },
                    leadingIcon = { Icon(ImageVector.vectorResource(id = R.drawable.ic_message_filled), null) },
                    onClick = {
                        showMenu = false
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            data = "sms:$phoneNumber".toUri()
                        }
                        context.startActivity(intent)
                    }
                )
            }
            DropdownMenuItem(
                contentPadding = PaddingValues(start = 20.dp, end = 26.dp),
                text = { Text("View Details") },
                leadingIcon = { Icon(Icons.Default.Info, null) },
                onClick = {
                    showMenu = false
                    navigator.navigate(ContactDetailsScreenDestination(contactId = contact.id))
                }
            )
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
            DropdownMenuItem(
                contentPadding = PaddingValues(start = 20.dp, end = 26.dp),
                text = { Text(stringResource(R.string.remove_from_favourites)) },
                leadingIcon = { Icon(Icons.Default.Star, null, tint = MaterialTheme.colorScheme.error) },
                onClick = {
                    showMenu = false
                    onToggleFavorite()
                }
            )
        }
    }
}
