package dev.goodwy.rphone.view.screen

import android.Manifest
import android.accounts.Account
import android.content.Intent
import android.content.res.Configuration
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
import androidx.compose.ui.draw.scale
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.automirrored.filled.StarHalf
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.PeopleAlt
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.goodwy.rphone.R
import dev.goodwy.rphone.bottomBarHeight
import dev.goodwy.rphone.controller.util.ContactUtils
import dev.goodwy.rphone.device_only
import dev.goodwy.rphone.view.theme.customColors
import dev.goodwy.rphone.view.theme.TabTransitionStyle
import com.ramcosta.composedestinations.generated.destinations.ContactEditScreenDestination
import com.ramcosta.composedestinations.generated.destinations.ContactVisibilityScreenDestination
import dev.goodwy.rphone.private_only
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
    val showButton by remember { derivedStateOf { listState.firstVisibleItemIndex > 2 } }

    val prefs = koinInject<PreferenceManager>()
    val pillNav = remember { prefs.getBoolean(PreferenceManager.KEY_PILL_NAV, false) }
    val searchEnabled = prefs.getBoolean(PreferenceManager.KEY_TAB_SHOW_SEARCH, false)
//    val favoritesEnabled = prefs.getBoolean(PreferenceManager.KEY_TAB_SHOW_FAVORITES, true)
//    val dialpadEnabled = prefs.getBoolean(PreferenceManager.KEY_TAB_SHOW_DIALPAD, true)
//    val notesEnabled = prefs.getBoolean(PreferenceManager.KEY_TAB_SHOW_NOTES, false)
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    val contactsVM: ContactsViewModel = koinActivityViewModel()

    var selectedIds by remember { mutableStateOf(setOf<String>()) }

    BackHandler(enabled = selectedIds.isNotEmpty()) {
        selectedIds = emptySet()
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize(),
//            .pointerInput(Unit) {
//                awaitPointerEventScope {
//                    while (true) {
//                        val down = awaitPointerEvent(PointerEventPass.Final).changes.firstOrNull()
//                            ?: continue
//                        if (!down.pressed) continue
//                        val startX = down.position.x
//                        val startY = down.position.y
//                        val startTime = System.currentTimeMillis()
//                        var triggered = false
//                        while (true) {
//                            val event = awaitPointerEvent(PointerEventPass.Final)
//                            val change = event.changes.firstOrNull() ?: break
//                            val dx = change.position.x - startX
//                            val dy = change.position.y - startY
//                            val elapsed = System.currentTimeMillis() - startTime
//                            if (!triggered && elapsed >= 150L && !change.isConsumed && kotlin.math.abs(
//                                    dx
//                                ) > 700f && kotlin.math.abs(dx) > kotlin.math.abs(dy) * 5.5f
//                            ) {
//                                triggered = true
//                                if (dx > 0) {
//                                    scope.launch {
//                                        navController.navigate(RecentScreenDestination.route) {
//                                            popUpTo(navController.graph.findStartDestination().id) {
//                                                saveState = true
//                                            }
//                                            launchSingleTop = true; restoreState = true
//                                        }
//                                    }
//                                } else {
//                                    // swipe left from Contacts → Notes (wrap around)
//                                    val route = when {
//                                        dialpadEnabled -> DialPadScreenDestination().route
//                                        notesEnabled -> NotesScreenDestination.route
//                                        favoritesEnabled -> FavoritesScreenDestination.route
//                                        else -> RecentScreenDestination.route
//                                    }
//                                    scope.launch {
//                                        navController.navigate(route) {
//                                            popUpTo(navController.graph.findStartDestination().id) {
//                                                saveState = true
//                                            }
//                                            launchSingleTop = true; restoreState = true
//                                        }
//                                    }
//                                }
//                            }
//                            if (!change.pressed) break
//                        }
//                    }
//                }
//            },
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
                        if (!searchEnabled) TopBar(navController, navigator)
                        AccountFilterBar(
                            modifier = Modifier
                                .then(
                                    if (searchEnabled) Modifier.windowInsetsPadding(WindowInsets.statusBars)
                                    else Modifier
                                ),
                            navigator = navigator,
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
                                selectedContacts.first().accountType == null && !selectedContacts.first().isPrivate) {
                                device_only
                            } else if (selectedContacts.first().accountName == null &&
                                selectedContacts.first().accountType == null && selectedContacts.first().isPrivate) {
                                private_only
                            } else {
                                "${selectedContacts.first().accountName}|${selectedContacts.first().accountType}"
                            }
                            val allSameAccount = selectedContacts.all {
                                val account = if (it.accountName == null && it.accountType == null && !it.isPrivate) device_only
                                            else if (it.accountName == null && it.accountType == null) private_only
                                            else "${it.accountName}|${it.accountType}"
                                account == firstAccount
                            }
                            if (allSameAccount) firstAccount else "-1"
                        } else {
                            "-1"
                        }
                    }
                    val shareText = stringResource(R.string.share)
                    val showMove = selectedContacts.none { it.hasMultipleSources } // You cannot move merged contacts
                    BatchActionBar(
                        selectedCount = selectedIds.size,
                        onClear = { selectedIds = emptySet() },
                        onDelete = {
                            contactsVM.deleteContacts(selectedIds.toList())
                            selectedIds = emptySet()
                        },
                        onMove = if (showMove) { account ->
                            contactsVM.moveContacts(selectedIds.toList(), account)
                            selectedIds = emptySet()
                        } else null,
                        onMoveToPrivate = {
                            selectedIds.forEach { contactsVM.makeContactPrivate(it) }
                            selectedIds = emptySet()
                        },
                        onAddToFav = {
                            filteredContacts.filter { selectedIds.contains(it.id) }.forEach { contactsVM.toggleFavorite(it, true) }
                        },
                        onRemoveFromFav = {
                            filteredContacts.filter { selectedIds.contains(it.id) }.forEach { contactsVM.toggleFavorite(it, false) }
                        },
                        availableAccounts = contactsVM.availableAccountsForMoving.collectAsState().value,
                        currentAccountKey = currentAccountKey,
                        onShare = {
                            val text = filteredContacts.filter { selectedIds.contains(it.id) }.joinToString("\n") { "${it.displayName}: ${it.phoneNumbers.joinToString(", ")}" }
                            val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
                            context.startActivity(Intent.createChooser(intent, shareText))
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
//                    ) { Icon(Icons.Default.PersonAdd, stringResource(R.string.add_contact)) }
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
//                ) { Icon(Icons.Default.PersonAdd, stringResource(R.string.add_contact)) }
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
                            .padding(bottom = 12.dp, end = 32.dp)
                        else if (pillNav) Modifier
                            .navigationBarsPadding()
                            .padding(bottom = 92.dp + 8.dp, end = 32.dp)
                        else Modifier
                            .navigationBarsPadding()
                            .padding(bottom = bottomBarHeight + 12.dp, end = 32.dp)
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
    modifier: Modifier,
    navigator: DestinationsNavigator,
    viewModel: ContactsViewModel,
    onAddContact: () -> Unit,
) {
    val accounts by viewModel.filteredAvailableAccounts.collectAsState()
    val selectedAccount by viewModel.selectedAccount.collectAsState()
    val showLocalOnly by viewModel.showLocalOnly.collectAsState()
    val showPrivateOnly by viewModel.showPrivateOnly.collectAsState()
    val visibleAccounts by viewModel.visibleAccountsFlow.collectAsState()
    val showLocalOnlyAccount = visibleAccounts?.contains("local|local") ?: true
    val showPrivateOnlyAccount = visibleAccounts?.contains("private|private") ?: true
    LaunchedEffect(Unit) { viewModel.fetchAccounts() }

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

    val totalVisibleContacts = remember(allContacts, visibleAccounts) {
        when {
            visibleAccounts != null -> allContacts.count { contact ->
                val key = if (contact.accountType == null && contact.accountName == null && !contact.isPrivate) "local|local"
                            else if (contact.accountType == null && contact.accountName == null) "private|private"
                            else "${contact.accountType}|${contact.accountName}"
                visibleAccounts!!.contains(key)
            }
            else -> allContacts.size
        }
    }

    val contactsCountText = when {
        showPrivateOnly -> {
            val privateOnlyCount = allContacts.count { it.isPrivate }
            val privateOnly = ContactUtils.getFriendlyAccountName(null, true)
            "$privateOnly · $privateOnlyCount"
        }
        showLocalOnly -> {
            val deviceOnlyCount = allContacts.count { it.accountName == null && it.accountType == null && !it.isPrivate }
            val deviceOnly = ContactUtils.getFriendlyAccountName(null)
            "$deviceOnly · $deviceOnlyCount"
        }
        selectedAccount != null -> {
            val acc = accounts.find { it.name == selectedAccount!!.name }
            "${acc?.name} · ${filteredContacts.size}"
        }
        else -> pluralStringResource(R.plurals.contacts_count, filteredContacts.size, filteredContacts.size)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .alpha(chipAlpha)
            .scale(chipScale),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val interactionSource = remember { MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()
        val cornerRadius by animateDpAsState(
            if (isPressed) 8.dp else 20.dp,
            spring(stiffness = Spring.StiffnessMediumLow),
            label = "ButtonShapeAnimation"
        )
        Surface(
            modifier = Modifier.combinedClickable(
                onClick = { showAccountSheet = true },
                onLongClick = { navigator.navigate(ContactVisibilityScreenDestination) },
                interactionSource = interactionSource,
                indication = null,
            ),
            shape = RoundedCornerShape(cornerRadius),
            color = when {
                showPrivateOnly -> MaterialTheme.colorScheme.tertiaryContainer
                showLocalOnly -> MaterialTheme.colorScheme.tertiaryContainer
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
                    imageVector = when {
                        showPrivateOnly -> ContactUtils.getAccountIcon(null, true)
                        showLocalOnly -> ContactUtils.getAccountIcon(null)
                        selectedAccount != null -> ContactUtils.getAccountIcon(selectedAccount)
                        else -> Icons.Rounded.PeopleAlt
                    },
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = when {
                        showPrivateOnly -> MaterialTheme.colorScheme.onTertiaryContainer
                        showLocalOnly -> MaterialTheme.colorScheme.onTertiaryContainer
                        selectedAccount != null -> MaterialTheme.colorScheme.onSecondaryContainer
                        else -> MaterialTheme.colorScheme.onPrimaryContainer
                    }
                )
                Text(
                    text = contactsCountText,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = when {
                        showPrivateOnly -> MaterialTheme.colorScheme.onTertiaryContainer
                        showLocalOnly -> MaterialTheme.colorScheme.onTertiaryContainer
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
                        showPrivateOnly -> MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                        showLocalOnly -> MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                        selectedAccount != null -> MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                        else -> MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    }
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.width(16.dp))

        val interactionSourceAdd = remember { MutableInteractionSource() }
        val isPressedAdd by interactionSourceAdd.collectIsPressedAsState()
        val cornerRadiusAdd by animateDpAsState(
            if (isPressedAdd) 8.dp else 20.dp,
            spring(stiffness = Spring.StiffnessMediumLow),
            label = "ButtonAddShapeAnimation"
        )
        Surface(
            modifier = Modifier.combinedClickable(
                onClick = onAddContact,
                interactionSource = interactionSourceAdd,
                indication = null,
            ),
            shape = RoundedCornerShape(cornerRadiusAdd),
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
                    text = stringResource(R.string.add_contact),
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
                showLocalOnly = showLocalOnly,
                showLocalOnlyAccount = showLocalOnlyAccount,
                showPrivateOnlyAccount = showPrivateOnlyAccount,
                showPrivateOnly = showPrivateOnly,
                totalCount = totalVisibleContacts,
                contactCountByAccount = { accountName, accountType ->
                    if (accountName == null) {
                        allContacts.count { it.accountName == null && it.accountType == null }
                    } else {
                        countMap["$accountName|$accountType"] ?: 0
                    }
                },
                contactCountByPrivate = allContacts.count { it.isPrivate },
                onSelectAll = {
                    showAccountSheet = false
                    viewModel.setShowLocalOnly(false)
                    viewModel.setShowPrivateOnly(false)
                    viewModel.selectAccount(null)
                },
                onSelectDeviceOnly = {
                    showAccountSheet = false
                    viewModel.setShowLocalOnly(true)
                    viewModel.setShowPrivateOnly(false)
                },
                onSelectPrivateOnly = {
                    showAccountSheet = false
                    viewModel.setShowLocalOnly(false)
                    viewModel.setShowPrivateOnly(true)
                },
                onSelectAccount = { account ->
                    showAccountSheet = false
                    viewModel.setShowLocalOnly(false)
                    viewModel.setShowPrivateOnly(false)
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
    onMoveToPrivate: (() -> Unit)? = null,
    availableAccounts: List<Account>,
    currentAccountKey: String? = null,
    onShare: () -> Unit,
    onAddToFav: (() -> Unit)? = null,
    onRemoveFromFav: (() -> Unit)? = null,
    onDeselect: () -> Unit,
    onSelectAll: () -> Unit,
    isAllSelected: Boolean
) {
    var showSelectionMenuOuter by remember { mutableStateOf(false) }
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
            RillIconButton(
                onClick = onClear,
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.cancel)
            )
            RillTextButton(
                onClick = if (isAllSelected) onDeselect else onSelectAll,
                text = stringResource(R.string.selected_items, selectedCount),
                toast = if (isAllSelected) stringResource(R.string.deselect_all) else stringResource(R.string.select_all)
            )
            Spacer(modifier = Modifier.weight(1f))
//            if (onRemoveFromFav != null) {
//                IconButton(onClick = { showFavDeleteConfirm = true }) {
//                    Icon(Icons.AutoMirrored.Filled.StarHalf, stringResource(R.string.remove_from_favorites))
//                }
//            }
//            if (onAddToFav != null) {
//                IconButton(onClick = { showAddFavConfirm = true }) {
//                    Icon(Icons.Default.Star, stringResource(R.string.add_to_favorites))
//                }
//            }
            if (onMove != null && onMoveToPrivate != null) {
                RillIconButton(
                    onClick = { showMoveDialog = true },
                    imageVector = Icons.AutoMirrored.Rounded.DriveFileMove,
                    contentDescription = stringResource(R.string.move_to_another_account)
                )
            }
            RillIconButton(
                onClick = { showDeleteConfirm = true },
                imageVector = ImageVector.vectorResource(id = R.drawable.ic_delete),
                contentDescription = stringResource(R.string.delete_contacts)
            )
//            IconButton(onClick = onShare) {
//                Icon(Icons.Default.Share, stringResource(R.string.share))
//            }

            Box {
                RillIconButton(
                    onClick = { showSelectionMenuOuter = true },
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.more)
                )
                DropdownMenu(shape = RoundedCornerShape(16.dp), expanded = showSelectionMenuOuter, onDismissRequest = { showSelectionMenuOuter = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.share)) },
                        leadingIcon = { Icon(Icons.Default.Share, null) },
                        onClick = {
                            showSelectionMenuOuter = false
                            onShare()
                        }
                    )
                    if (onAddToFav != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.add_to_favorites)) },
                            leadingIcon = { Icon(Icons.Default.Star, null) },
                            onClick = {
                                showSelectionMenuOuter = false
                                showAddFavConfirm = true
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.remove_from_favorites)) },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.StarHalf, null) },
                        onClick = {
                            showSelectionMenuOuter = false
                            showFavDeleteConfirm = true
                        }
                    )
                }
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
                    Text(
                        stringResource(R.string.add_to_favorites),
                        textAlign = TextAlign.End,
                    )
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
                    Text(
                        stringResource(R.string.remove_from_favorites),
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.End,
                    )
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
            title = stringResource(R.string.delete_contacts),
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
                stringResource(R.string.delete_contacts_subtitle, selectedCount),
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
            onAccountSelected = { account, isPrivate ->
                if (isPrivate) onMoveToPrivate?.invoke()
                else onMove?.invoke(account)
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
                                title = stringResource(R.string.no_contacts_found),
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
    showLocalOnly: Boolean,
    showLocalOnlyAccount: Boolean,
    showPrivateOnlyAccount: Boolean,
    showPrivateOnly: Boolean,
    totalCount: Int,
    contactCountByAccount: (String?, String?) -> Int,
    contactCountByPrivate: Int,
    onSelectAll: () -> Unit,
    onSelectDeviceOnly: () -> Unit,
    onSelectPrivateOnly: () -> Unit,
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
                        icon = Icons.Rounded.PeopleAlt,
                        name = stringResource(R.string.filter_all),
                        subtitle = pluralStringResource(R.plurals.contacts_count, totalCount, totalCount),
                        isSelected = !showLocalOnly && !showPrivateOnly && selectedAccount == null,
                        onClick = onSelectAll
                    )
                }

                // "Private only" row - for contacts without an account (app contacts)
                if (showPrivateOnlyAccount) {
                    item {
                        AccountRow(
                            icon = ContactUtils.getAccountIcon(null, true),
                            name = ContactUtils.getFriendlyAccountName(null, true),
                            subtitle = pluralStringResource(R.plurals.contacts_count, contactCountByPrivate, contactCountByPrivate),
                            isSelected = showPrivateOnly,
                            onClick = onSelectPrivateOnly
                        )
                    }
                }


                // "Device only" row - for contacts without an account (local device contacts)
                if (showLocalOnlyAccount) {
                    item {
                        val deviceOnlyCount = contactCountByAccount(null, null) - contactCountByPrivate
                        AccountRow(
                            icon = ContactUtils.getAccountIcon(null),
                            name = ContactUtils.getFriendlyAccountName(null),
                            subtitle = pluralStringResource(R.plurals.contacts_count, deviceOnlyCount, deviceOnlyCount),
                            isSelected = showLocalOnly,
                            onClick = onSelectDeviceOnly
                        )
                    }
                }

                if (accounts.isNotEmpty()) {
                    items(accounts, key = { it.name }) { account ->
                        val count = contactCountByAccount(account.name, account.type)
                        AccountRow(
                            icon = ContactUtils.getAccountIcon(account),
                            name = ContactUtils.getAccountName(account),
                            subtitle = pluralStringResource(R.plurals.contacts_count, count, count),
                            isSelected = !showLocalOnly && !showPrivateOnly && selectedAccount?.name == account.name,
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
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
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
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
