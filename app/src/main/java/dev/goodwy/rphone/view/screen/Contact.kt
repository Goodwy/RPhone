package dev.goodwy.rphone.view.screen

import android.Manifest
import android.accounts.Account
import dev.goodwy.rphone.view.theme.TabTransitionStyle
import android.content.Intent
import androidx.compose.animation.core.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.People
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.scale
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.controller.ContactsViewModel
import dev.goodwy.rphone.view.components.*
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.automirrored.filled.StarHalf
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.ramcosta.composedestinations.generated.destinations.RecentScreenDestination
import com.ramcosta.composedestinations.generated.destinations.NotesScreenDestination
import androidx.navigation.NavGraph.Companion.findStartDestination
import dev.goodwy.rphone.R
import dev.goodwy.rphone.bottomBarHeight
import dev.goodwy.rphone.controller.util.ContactUtils
import dev.goodwy.rphone.device_only
import dev.goodwy.rphone.view.theme.customColors
import com.ramcosta.composedestinations.generated.destinations.ContactEditScreenDestination
import com.ramcosta.composedestinations.generated.destinations.DialPadScreenDestination
import com.ramcosta.composedestinations.generated.destinations.FavoritesScreenDestination
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinActivityViewModel

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Destination<RootGraph>(style = TabTransitionStyle::class)
@Composable
fun ContactScreen(navController: NavController, navigator: DestinationsNavigator) {
    val permState = rememberPermissionState(Manifest.permission.READ_CONTACTS)
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val showButton by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 2
        }
    }

    val prefs = koinInject<PreferenceManager>()
    val pillNav = remember { prefs.getBoolean(PreferenceManager.KEY_PILL_NAV, false) }
    val favouritesEnabled = prefs.getBoolean(PreferenceManager.KEY_TAB_SHOW_FAVORITES, true)
    val dialpadEnabled = prefs.getBoolean(PreferenceManager.KEY_TAB_SHOW_DIALPAD, true)
    val notesEnabled = prefs.getBoolean(PreferenceManager.KEY_TAB_SHOW_NOTES, true)
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    val contactsVM: ContactsViewModel = koinActivityViewModel()

    var selectedIds by remember { mutableStateOf(setOf<String>()) }

    BackHandler(enabled = selectedIds.isNotEmpty()) {
        selectedIds = emptySet()
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitPointerEvent(PointerEventPass.Final).changes.firstOrNull()
                            ?: continue
                        if (!down.pressed) continue
                        val startX = down.position.x
                        val startY = down.position.y
                        val startTime = System.currentTimeMillis()
                        var triggered = false
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Final)
                            val change = event.changes.firstOrNull() ?: break
                            val dx = change.position.x - startX
                            val dy = change.position.y - startY
                            val elapsed = System.currentTimeMillis() - startTime
                            if (!triggered && elapsed >= 150L && !change.isConsumed && kotlin.math.abs(
                                    dx
                                ) > 700f && kotlin.math.abs(dx) > kotlin.math.abs(dy) * 5.5f
                            ) {
                                triggered = true
                                if (dx > 0) {
                                    scope.launch {
                                        navController.navigate(RecentScreenDestination.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true; restoreState = true
                                        }
                                    }
                                } else {
                                    // swipe left from Contacts → Notes (wrap around)
                                    val route = when {
                                        dialpadEnabled -> DialPadScreenDestination().route
                                        notesEnabled -> NotesScreenDestination.route
                                        favouritesEnabled -> FavoritesScreenDestination.route
                                        else -> RecentScreenDestination.route
                                    }
                                    scope.launch {
                                        navController.navigate(route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
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
                    Column {
                        TopBar(navController, navigator)
                        AccountFilterBar(
                            viewModel = contactsVM,
                            onAddContact = {
                                navigator.navigate(ContactEditScreenDestination())
                            }
                        )
                    }
                } else {
                    val filteredContacts by contactsVM.filteredContacts.collectAsState()

                    // Set the currentAccountKey for the selected contacts
                    val selectedContacts = filteredContacts.filter { selectedIds.contains(it.id) }
                    val currentAccountKey = remember(selectedIds) {
                        if (selectedContacts.isNotEmpty()) {
                            val firstAccount = if (selectedContacts.first().accountName == null &&
                                selectedContacts.first().accountType == null) {
                                device_only
                            } else {
                                "${selectedContacts.first().accountName}|${selectedContacts.first().accountType}"
                            }
                            val allSameAccount = selectedContacts.all {
                                val account = if (it.accountName == null && it.accountType == null) device_only
                                else "${it.accountName}|${it.accountType}"
                                account == firstAccount
                            }
                            if (allSameAccount) firstAccount else "-1"
                        } else {
                            "-1"
                        }
                    }
                    BatchActionBar(
                        selectedCount = selectedIds.size,
                        onClear = { selectedIds = emptySet() },
                        onDelete = {
                            contactsVM.deleteContacts(selectedIds.toList())
                            selectedIds = emptySet()
                        },
                        onMove = { account ->
                            contactsVM.moveContacts(selectedIds.toList(), account)
                            selectedIds = emptySet()
                        },
                        onAddToFav = {
                            filteredContacts.filter { selectedIds.contains(it.id) }.forEach { contactsVM.toggleFavorite(it, true) }
                        },
                        availableAccounts = contactsVM.availableAccountsForMoving.collectAsState().value,
                        currentAccountKey = currentAccountKey,
                        onShare = {
                            val text = filteredContacts.filter { selectedIds.contains(it.id) }.joinToString("\n") { "${it.name}: ${it.phoneNumbers.firstOrNull() ?: ""}" }
                            val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
                            context.startActivity(Intent.createChooser(intent, "Share contacts"))
                        },
                        onDeselect = {
                            selectedIds = emptySet()
                        },
                        onSelectAll = {
                            selectedIds = filteredContacts.map { it.id }.toSet()
                        },
                        isAllSelected = selectedIds == filteredContacts.map { it.id }.toSet()
                    )
                }
            }
        },
        floatingActionButton = {
//            if (selectedIds.isEmpty()) {
//            val globalBackdrop = LocalLiquidGlassBackdrop.current
//            val settingsVer by prefs_ui.settingsChanged.collectAsState()
//            val liquidGlass = remember(settingsVer) { prefs_ui.getBoolean(PreferenceManager.KEY_LIQUID_GLASS, false) }
//            val lgContactsFab = remember(settingsVer) { prefs_ui.getBoolean(PreferenceManager.KEY_LG_CONTACTS_FAB, false) }
//            val blurEffects = remember(settingsVer) { prefs_ui.getBoolean(PreferenceManager.KEY_BLUR_EFFECTS, false) }
//            val blurContactsFab = remember(settingsVer) { prefs_ui.getBoolean(PreferenceManager.KEY_BLUR_CONTACTS_FAB, false) }
//            val fabShape = RoundedCornerShape(17.dp)
//            val useLiquidGlass = liquidGlass && lgContactsFab && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && globalBackdrop != null
//            val useBlur = blurEffects && blurContactsFab && !useLiquidGlass
//            val baseModifier = Modifier
//                .scale(fabScale)
//                .then(
//                    if (pillNav) Modifier
//                        .navigationBarsPadding()
//                        .padding(bottom = 92.dp)
//                    else Modifier
//                        .navigationBarsPadding()
//                        .padding(bottom = 82.dp)
//                )
//                .then(
//                    if (isLandscape) Modifier
//                        .navigationBarsPadding()
//                        .padding(bottom = 8.dp) else Modifier
//                )
//            val fabOnClick: () -> Unit = {
//                  navigator.navigate(ContactEditScreenDestination())
//            }
//            if (useLiquidGlass) {
//                Box(
//                    modifier = baseModifier.drawBackdrop(
//                        backdrop = globalBackdrop,
//                        shape = { fabShape },
//                        effects = {
//                            val d = density
//                            colorControls(brightness = -0.15f)
//                            lens(refractionHeight = 46f * d, refractionAmount = 64f * d)
//                        },
//                        highlight = { Highlight.Default }
//                    )
//                ) {
//                    FloatingActionButton(
//                        onClick = fabOnClick,
//                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.0f),
//                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
//                        shape = fabShape,
//                        elevation = FloatingActionButtonDefaults.elevation(0.dp),
//                    ) { Icon(Icons.Default.PersonAdd, "Add Contact") }
//                }
//            } else {
//                FloatingActionButton(
//                    onClick = fabOnClick,
//                    containerColor = if (useBlur)
//                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f)
//                    else
//                        MaterialTheme.colorScheme.primaryContainer,
//                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
//                    shape = fabShape,
//                    elevation = FloatingActionButtonDefaults.elevation(0.dp),
//                    modifier = baseModifier
//                ) { Icon(Icons.Default.PersonAdd, "Add Contact") }
//            }
//            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            ContactContent(
                navigator = navigator,
                isGranted = permState.status == PermissionStatus.Granted,
                onRequestPermission = { permState.launchPermissionRequest() },
                listState = listState,
                selectedIds = selectedIds,
                onToggleSelection = { id ->
                    selectedIds = if (selectedIds.contains(id)) {
                        selectedIds - id
                    } else {
                        selectedIds + id
                    }
                },
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
                visible = showButton && selectedIds.isEmpty(),
                onClick = {
                    scope.launch {
                        listState.animateScrollToItem(0)
                    }
                }
            )
        }
    }
}

@Composable
fun AccountFilterBar(
    viewModel: ContactsViewModel,
    onAddContact: () -> Unit,
) {
    val accounts by viewModel.availableAccounts.collectAsState()
    val selectedAccount by viewModel.selectedAccount.collectAsState()
    val showOnlyDeviceContacts by viewModel.showOnlyDeviceContacts.collectAsState()
    LaunchedEffect(Unit) { viewModel.fetchAccounts() }

//    if (accounts.isNotEmpty()) {
//        LazyRow(
//            modifier = Modifier
//                .fillMaxWidth()
//                .padding(vertical = 2.dp),
//            contentPadding = PaddingValues(horizontal = 16.dp),
//            horizontalArrangement = Arrangement.spacedBy(8.dp)
//        ) {
//            item {
//                RillFilterChip("All", selectedAccount == null, {
//                        _ ->
//                    viewModel.selectAccount(null)
//                })
//            }
//            items(accounts) { account ->
//                RillFilterChip(ContactUtils.getFriendlyAccountName(account), selectedAccount == account, {
//                        _ ->
//                    viewModel.selectAccount(account)
//                })
//            }
//        }
//    }

    // ── Contact count and add pill ────────────────────────────────
    var chipVisible by remember { mutableStateOf(false) }
    val chipAlpha by animateFloatAsState(
        targetValue = if (chipVisible) 1f else 0f,
        animationSpec = tween(500),
        label = "chipAlpha"
    )
    val chipScale by animateFloatAsState(
        targetValue = if (chipVisible) 1f else 0.8f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "chipScale"
    )
    val allContacts by viewModel.allContacts.collectAsState()
    val filteredContacts by viewModel.filteredContacts.collectAsState()
    LaunchedEffect(filteredContacts.size) { chipVisible = true }

    var showAccountSheet by remember { mutableStateOf(false) }

    val contactsCountText = when {
        showOnlyDeviceContacts -> {
            val deviceOnlyCount = allContacts.count { it.accountName == null && it.accountType == null }
            val deviceOnly = ContactUtils.getAccountType(null)
            "$deviceOnly · $deviceOnlyCount"
        }
        selectedAccount != null -> {
            val acc = accounts.find { it.name == selectedAccount!!.name }
            "${acc?.name} · ${filteredContacts.size}"
        }
        else -> pluralStringResource(R.plurals.contacts_count, filteredContacts.size, filteredContacts.size)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .alpha(chipAlpha)
            .scale(chipScale),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.combinedClickable(
                onClick = { showAccountSheet = true },
                interactionSource = null,
                indication = null,
            ),
            shape = RoundedCornerShape(20.dp),
            color = when {
                showOnlyDeviceContacts -> MaterialTheme.colorScheme.tertiaryContainer
                selectedAccount != null -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.primaryContainer
            }
        ) {
            Row(
                modifier = Modifier.padding(
                    start = 12.dp,
                    end = 8.dp,
                    top = 5.dp,
                    bottom = 5.dp
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    if (showOnlyDeviceContacts) ContactUtils.getAccountIcon(null) else Icons.Rounded.People,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = when {
                        showOnlyDeviceContacts -> MaterialTheme.colorScheme.onTertiaryContainer
                        selectedAccount != null -> MaterialTheme.colorScheme.onSecondaryContainer
                        else -> MaterialTheme.colorScheme.onPrimaryContainer
                    }
                )
                Text(
                    text = contactsCountText,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = when {
                        showOnlyDeviceContacts -> MaterialTheme.colorScheme.onTertiaryContainer
                        selectedAccount != null -> MaterialTheme.colorScheme.onSecondaryContainer
                        else -> MaterialTheme.colorScheme.onPrimaryContainer
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(
                    Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = when {
                        showOnlyDeviceContacts -> MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                        selectedAccount != null -> MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                        else -> MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    }
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.width(16.dp))

//        val context = LocalContext.current
//        val fabOnClick: () -> Unit = {
//            val intent = Intent(Intent.ACTION_INSERT, ContactsContract.Contacts.CONTENT_URI)
//            context.startActivity(intent)
//        }
        Surface(
            modifier = Modifier.combinedClickable(
                onClick = onAddContact,
                interactionSource = null,
                indication = null,
            ),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Row(
                modifier = Modifier.padding(
                    horizontal = 12.dp,
                    vertical = 5.dp
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    Icons.Rounded.PersonAdd,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "Add Contact",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (showAccountSheet) {
            val countMap = viewModel.contactCountByAccount
            AccountSwitcherSheet(
                accounts = accounts,
                selectedAccount = selectedAccount,
                showOnlyDeviceContacts = showOnlyDeviceContacts,
                totalCount = allContacts.size,
                contactCountByAccount = { accountName, accountType ->
                    if (accountName == null) {
                        allContacts.count { it.accountName == null && it.accountType == null }
                    } else {
                        countMap["$accountName|$accountType"] ?: 0
                    }
                },
                onSelectAll = {
                    showAccountSheet = false
                    viewModel.setShowOnlyDeviceContacts(false)
                    viewModel.selectAccount(null)
                },
                onSelectDeviceOnly = {
                    showAccountSheet = false
                    viewModel.setShowOnlyDeviceContacts(true)
                },
                onSelectAccount = { account ->
                    showAccountSheet = false
                    viewModel.setShowOnlyDeviceContacts(false)
                    viewModel.selectAccount(account)
                },
                onDismiss = { showAccountSheet = false }
            )
        }
    } // ── End Contact count and add pill ────────────────────────────────
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchActionBar(
    selectedCount: Int,
    onClear: () -> Unit,
    onDelete: () -> Unit,
    onMove: ((Account?) -> Unit)? = null,
    availableAccounts: List<Account>,
    currentAccountKey: String? = null,
    onShare: () -> Unit,
    onAddToFav: (() -> Unit)? = null,
    onRemoveFromFav: (() -> Unit)? = null,
    onDeselect: () -> Unit,
    onSelectAll: () -> Unit,
    isAllSelected: Boolean
) {
    var showMoveDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showFavDeleteConfirm by remember { mutableStateOf(false) }
    var showAddFavConfirm by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth(),
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClear) {
                Icon(Icons.Default.Close, "Clear selection")
            }
            TextButton(
                onClick = if (isAllSelected) onDeselect else onSelectAll
            ) {
                Text(
                    text = "$selectedCount selected",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            if (onRemoveFromFav != null) {
                IconButton(onClick = { showFavDeleteConfirm = true }) {
                    Icon(Icons.AutoMirrored.Filled.StarHalf, stringResource(R.string.remove_from_favourites))
                }
            }
            if (onAddToFav != null) {
                IconButton(onClick = { showAddFavConfirm = true }) {
                    Icon(Icons.Default.Star, stringResource(R.string.add_to_favourites))
                }
            }
            if (onMove != null) {
                IconButton(onClick = { showMoveDialog = true }) {
                    Icon(Icons.AutoMirrored.Rounded.DriveFileMove, "Move to another account")
                }
            }
            IconButton(onClick = { showDeleteConfirm = true }) {
                Icon(ImageVector.vectorResource(id = R.drawable.ic_delete), "Delete selected")
            }
            IconButton(onClick = onShare) {
                Icon(Icons.Default.Share, "Share")
            }
        }
    }

    if (showAddFavConfirm) {
        RillDialog(
            onDismissRequest = { showAddFavConfirm = false },
            title = pluralStringResource(
                R.plurals.add_to_favorites_confirmation,
                selectedCount,
                selectedCount
            ),
            icon = Icons.Default.Star,
            confirmButton = {
                TextButton(onClick = {
                    onAddToFav?.invoke()
                    showAddFavConfirm = false
                }) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddFavConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        ) { }
    }

    if (showFavDeleteConfirm) {
        RillDialog(
            onDismissRequest = { showFavDeleteConfirm = false },
            title = pluralStringResource(
                R.plurals.remove_from_favorites_confirmation,
                selectedCount,
                selectedCount
            ),
            icon = Icons.AutoMirrored.Filled.StarHalf,
            confirmButton = {
                TextButton(onClick = {
                    onRemoveFromFav?.invoke()
                    showFavDeleteConfirm = false
                }) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showFavDeleteConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        ) { }
    }

    if (showDeleteConfirm) {
        RillDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = "Delete Contacts",
            icon = ImageVector.vectorResource(id = R.drawable.ic_delete),
            iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkRed,
            iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorRed,
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
                    showDeleteConfirm = false
                }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        ) {
            Text(
                "Are you sure you want to delete $selectedCount selected contacts?",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    if (showMoveDialog) {
        MoveToAccountDialog(
            availableAccounts = availableAccounts,
            currentAccountKey = currentAccountKey,
            onDismiss = { showMoveDialog = false },
            onAccountSelected = { account ->
                onMove?.invoke(account)
                showMoveDialog = false
            }
        )
    }
}

@Composable
fun ContactContent(
    navigator: DestinationsNavigator,
    isGranted: Boolean,
    onRequestPermission: () -> Unit,
    listState: LazyListState,
    selectedIds: Set<String>,
    onToggleSelection: (String) -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(400),
        label = "contentAlpha"
    )
    LaunchedEffect(Unit) { visible = true }

    Column(modifier = Modifier
        .fillMaxSize()
        .alpha(alpha)
    ) {
        if (isGranted) {
            val contactsVM: ContactsViewModel = koinActivityViewModel()
            val isLoading by contactsVM.isLoading.collectAsState()
            val contacts by contactsVM.filteredContacts.collectAsState()
            val groupedContacts by contactsVM.groupedContacts.collectAsState()

            val prefs = koinInject<PreferenceManager>()
            val settingsVersion by prefs.settingsChanged.collectAsState()

            LaunchedEffect(settingsVersion) {
                contactsVM.fetchContacts()
            }

            val pullToRefreshState = rememberPullToRefreshState()
            PullToRefreshBox(
                isRefreshing = isLoading && contacts.isNotEmpty(),
                onRefresh = { contactsVM.fetchContacts() },
                modifier = Modifier.fillMaxSize(),
                state = pullToRefreshState,
                indicator = {
                    RillPullToRefreshIndicator(
                        state = pullToRefreshState,
                        isRefreshing = isLoading && contacts.isNotEmpty()
                    )
                }
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    if (isLoading && contacts.isEmpty()) {
                        RillLoadingIndicatorView()
                    } else {
                        if (contacts.isEmpty()) {
                            PlaceholderView(
                                icon = Icons.Rounded.People,
                                title = "No contacts found",
                            )
                        } else {
                            ScrollHapticsEffect(listState = listState)
                            AZListScroll(
                                contacts = contacts,
                                navigator = navigator,
                                listState = listState,
                                selectedIds = selectedIds,
                                onToggleSelection = onToggleSelection,
                                grouped = groupedContacts,
                            )
                        }
                    }
                }
            }
        } else {
            PermissionDeniedView(
                icon = Icons.Rounded.People,
                title = stringResource(R.string.contacts_permission),
                description = stringResource(R.string.contacts_permission_description),
                onGrantClick = onRequestPermission
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountSwitcherSheet(
    accounts: List<Account>,
    selectedAccount: Account?,
    showOnlyDeviceContacts: Boolean,
    totalCount: Int,
    contactCountByAccount: (String?, String?) -> Int,
    onSelectAll: () -> Unit,
    onSelectDeviceOnly: () -> Unit,
    onSelectAccount: (Account) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
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

            LazyColumn(contentPadding = PaddingValues(vertical = 12.dp)) {
                // "All Contacts" row
                item {
                    AccountRow(
                        icon = Icons.Rounded.People,
                        name = "All Contacts",
                        subtitle = pluralStringResource(R.plurals.contacts_count, totalCount, totalCount),
                        isSelected = !showOnlyDeviceContacts && selectedAccount == null,
                        onClick = onSelectAll
                    )
                }

                // "Device only" row - for contacts without an account (local device contacts)
                item {
                    val deviceOnlyCount = contactCountByAccount(null, null)
                    AccountRow(
                        icon = ContactUtils.getAccountIcon(null),
                        name = ContactUtils.getAccountType(null),
                        subtitle = pluralStringResource(R.plurals.contacts_count, deviceOnlyCount, deviceOnlyCount),
                        isSelected = showOnlyDeviceContacts,
                        onClick = onSelectDeviceOnly
                    )
                }

                if (accounts.isNotEmpty()) {
                    items(accounts, key = { it.name }) { account ->
                        val count = contactCountByAccount(account.name, account.type)
                        AccountRow(
                            icon = ContactUtils.getAccountIcon(account),
                            name = ContactUtils.getAccountName(account),
                            subtitle = pluralStringResource(R.plurals.contacts_count, count, count),
                            isSelected = !showOnlyDeviceContacts && selectedAccount?.name == account.name,
                            onClick = { onSelectAccount(account) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountRow(
    icon: ImageVector,
    name: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bgColor by animateColorAsState(
        /*if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        else */androidx.compose.ui.graphics.Color.Transparent,
        spring(stiffness = Spring.StiffnessMediumLow), label = "rowBg"
    )
    Surface(
        onClick = onClick,
        color = bgColor,
        modifier = androidx.compose.ui.Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = androidx.compose.ui.Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                else MaterialTheme.colorScheme.surfaceVariant,
                modifier = androidx.compose.ui.Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        modifier = androidx.compose.ui.Modifier.size(22.dp),
                        tint = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Column(modifier = androidx.compose.ui.Modifier.weight(1f)) {
                Text(
                    name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AnimatedVisibility(
                visible = isSelected,
                enter = scaleIn(spring(stiffness = Spring.StiffnessMedium)) + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = androidx.compose.ui.Modifier.size(22.dp)
                )
            }
        }
    }
}
