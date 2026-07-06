package dev.goodwy.rphone.view.components

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.provider.CallLog
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import dev.goodwy.rphone.R
import dev.goodwy.rphone.bottomBarHeight
import dev.goodwy.rphone.cardCornerBig
import dev.goodwy.rphone.cardCornerSmall
import dev.goodwy.rphone.cardSpacedBy
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.controller.ContactsViewModel
import dev.goodwy.rphone.modal.data.Contact
import dev.goodwy.rphone.view.theme.MyColors.cardColor
import dev.goodwy.rphone.view.theme.MyColors.cardColorSelected
import dev.goodwy.rphone.view.theme.customColors
import com.ramcosta.composedestinations.generated.destinations.ContactDetailsScreenDestination
import com.ramcosta.composedestinations.generated.destinations.ContactEditScreenDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.goodwy.rphone.controller.util.toast
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinActivityViewModel

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun AZListScroll(
    contacts: List<Contact>,
    navigator: DestinationsNavigator,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    selectedIds: Set<String> = emptySet(),
    onToggleSelection: (String) -> Unit = {},
    grouped: Map<Char, List<Contact>>? = null,
) {
    AZListContent(
        contacts = contacts,
        navigator = navigator,
        modifier = modifier,
        listState = listState,
        selectedIds = selectedIds,
        onToggleSelection = onToggleSelection,
        grouped = grouped,
    )
}

@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
@OptIn(ExperimentalFoundationApi::class)
fun AZListContent(
    contacts: List<Contact>,
    navigator: DestinationsNavigator,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    selectedIds: Set<String> = emptySet(),
    onToggleSelection: (String) -> Unit = {},
    grouped: Map<Char, List<Contact>>? = null,
) {
    val prefs = koinInject<PreferenceManager>()
    val settingsState by prefs.settingsChanged.collectAsState()
    val showFavorites = !prefs.getBoolean(PreferenceManager.KEY_TAB_SHOW_FAVORITES, true)

//    val haptic = LocalHapticFeedback.current
//    val hapticScrollEnabled = prefs.getBoolean(PreferenceManager.KEY_HAPTIC_LIST_SCROLL, false)
//    if (hapticScrollEnabled) {
//        LaunchedEffect(listState.firstVisibleItemIndex) {
//            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
//        }
//    }

    val finalGrouped = remember(contacts, grouped) {
        if (grouped != null && !showFavorites) return@remember grouped

        val favorites = if (showFavorites) contacts.filter { it.isFavorite } else emptyList()
        val nonFavs = if (showFavorites) contacts.filter { !it.isFavorite } else contacts
        val mainGroups = nonFavs.groupBy {
            val firstChar = it.displayName.firstOrNull()?.uppercaseChar() ?: '#'
            if (firstChar.isLetter()) firstChar else '#'
        }.toMutableMap()

        val finalMap = linkedMapOf<Char, List<Contact>>()
        if (favorites.isNotEmpty()) finalMap['❤'] = favorites
        mainGroups.keys.filter { it.isLetter() }.sorted().forEach { char ->
            finalMap[char] = mainGroups[char]!!
        }
        val hashGroup = mainGroups['#']
        if (hashGroup != null) finalMap['#'] = hashGroup
        finalMap
    }

    // Map each letter to its first LazyColumn item index for sidebar jump
    val alphabetIndices = remember(finalGrouped) {
        val map = mutableMapOf<Char, Int>()
        var currentIndex = 0
        finalGrouped.forEach { (char, group) ->
            map[char] = currentIndex          // stickyHeader index
            currentIndex += 1 + group.size   // header + N contact items
        }
        map
    }

    val scope = rememberCoroutineScope()
    var draggingChar by remember { mutableStateOf<Char?>(null) }

    val scrollingChar by remember {
        derivedStateOf {
            val firstVisible = listState.firstVisibleItemIndex
            alphabetIndices.entries
                .filter { it.value <= firstVisible }
                .maxByOrNull { it.value }
                ?.key ?: alphabetIndices.keys.firstOrNull()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        val configuration = LocalConfiguration.current
        val screenHeightDp = configuration.screenHeightDp.dp
        val bottomPadding = screenHeightDp - 340.dp
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 8.dp, bottom = bottomPadding)
        ) {
            finalGrouped.forEach { (initial, contactsForChar) ->
                // ── Letter header ──────────────────────────────────────────
                stickyHeader(key = "header_$initial", contentType = "letterHeader") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 28.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (initial == '❤') "Favorites" else initial.toString(),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }

                // ── One lazy item per contact for smooth scrolling ─────────
                val selectionMode = selectedIds.isNotEmpty()
                itemsIndexed(
                    items = contactsForChar,
                    key = { _, contact -> "${initial}_${contact.id}" },
                    contentType = { _, _ -> "contact" }
                ) { index, contact ->
                    val isOnly   = contactsForChar.size == 1
                    val isFirst  = index == 0
                    val isLast   = index == contactsForChar.lastIndex
                    val isSelected = selectedIds.contains(contact.id)

                    val shape = when {
                        isOnly || isSelected -> RoundedCornerShape(cardCornerBig)
                        isFirst -> RoundedCornerShape(
                            topStart = cardCornerBig, topEnd = cardCornerBig,
                            bottomStart = cardCornerSmall, bottomEnd = cardCornerSmall
                        )
                        isLast  -> RoundedCornerShape(
                            topStart = cardCornerSmall, topEnd = cardCornerSmall,
                            bottomStart = cardCornerBig, bottomEnd = cardCornerBig
                        )
                        else    -> RoundedCornerShape(cardCornerSmall)
                    }
                    val bottomPadding = if (!isLast) cardSpacedBy else 0.dp

                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        RillScrollAnimatedItem(delayMs = (index * 25L).coerceAtMost(250L)) {
                            Surface(
                                shape = shape,
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = bottomPadding)
                            ) {
                                ContactListItem(
                                    contact = contact,
                                    navigator = navigator,
                                    selectionMode = selectionMode,
                                    isSelected = isSelected,
                                    onSelectToggle = { onToggleSelection(contact.id) }
                                )
                            }
                        }
                    }

                    // Gap between letter groups
                    if (isLast) {
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
        }

        val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
        if (!isLandscape) {
            val prefs_ui = koinInject<PreferenceManager>()
            val pillNav = remember { prefs_ui.getBoolean(PreferenceManager.KEY_PILL_NAV, false) }
            val paddingBottom = if (pillNav) 0.dp else bottomBarHeight
            AlphabetSideBar(
                alphabet = alphabetIndices.keys.toList(),
                selectedChar = draggingChar ?: scrollingChar,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .navigationBarsPadding()
                    .padding(end = 4.dp, bottom = paddingBottom),
                onLetterSelected = { char ->
                    draggingChar = char
                    val index = alphabetIndices[char] ?: return@AlphabetSideBar
                    scope.launch { listState.scrollToItem(index) }
                },
                onDragEnd = { draggingChar = null }
            )
        }

        if (draggingChar != null) {
            Surface(
                modifier = Modifier
                    .size(100.dp)
                    .align(Alignment.Center),
                shape = RoundedCornerShape(36.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                shadowElevation = 8.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = draggingChar.toString(),
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContactListItem(
    contact: Contact,
    navigator: DestinationsNavigator,
    selectionMode: Boolean = false,
    isSelected: Boolean = false,
    onSelectToggle: () -> Unit = {}
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val prefs = koinInject<PreferenceManager>()
    val contactsVM: ContactsViewModel = koinActivityViewModel()
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var isPressed by remember { mutableStateOf(false) }
    var horizontalDragDetected by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (showMenu) 0.97f else if (isPressed) 0.97f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "contactItemScale"
    )

    val headline = contact.displayName

    // Delete confirmation dialog
    if (showDeleteConfirm) {
        RillDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = "Delete Contact",
            icon = ImageVector.vectorResource(id = R.drawable.ic_delete),
            iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkRed,
            iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorRed,
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    contactsVM.deleteContact(contact.id)
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
                "Are you sure you want to permanently delete \"$headline\"? This action cannot be undone.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    val onClick: () -> Unit = {
        if (selectionMode) {
            onSelectToggle()
        } else {
            if (prefs.getBoolean(PreferenceManager.KEY_APP_HAPTICS, true)) {
                performAppHaptic(
                    context,
                    prefs.getString(
                        PreferenceManager.KEY_APP_HAPTICS_STRENGTH,
                        "light"
                    ) ?: "light",
                    prefs.getFloat(
                        PreferenceManager.KEY_HAPTICS_CUSTOM_INTENSITY,
                        0.5f
                    )
                )
            }
            navigator.navigate(ContactDetailsScreenDestination(contactId = contact.id))
        }

    }
    Surface(
        color = if (isSelected) cardColorSelected else cardColor,
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale),
        shadowElevation = 0.dp
    ) {
        val defaultOrFirstPhone = contact.phoneDetails.firstOrNull { it.isPrimary }?.number ?: contact.phoneNumbers.firstOrNull()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {
                        if (horizontalDragDetected) return@combinedClickable
                        onClick()
                    },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSelectToggle()
                    }
                )
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        isPressed = true
                        horizontalDragDetected = false
                        val downPos = down.position
                        do {
                            val event = awaitPointerEvent()
                            val current = event.changes.firstOrNull() ?: break
                            val dx = kotlin.math.abs(current.position.x - downPos.x)
                            val dy = kotlin.math.abs(current.position.y - downPos.y)
                            if (dx > 28.dp.toPx() && dx > dy * 1.3f) horizontalDragDetected = true
                            if (!current.pressed) break
                        } while (true)
                        isPressed = false
                    }
                }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
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
                RillAvatar(
                    name = "",
                    modifier = Modifier.size(42.dp)
                        .scale(iconScale)
                        .alpha(iconAlpha),
                    icon = Icons.Rounded.Check,
                    iconContainerColor = MaterialTheme.colorScheme.primary
                )
            } else {
                RillAvatar(
                    name = headline,
                    photoUri = contact.photoUri,
                    modifier = Modifier
                        .size(42.dp)
                        .combinedClickable(
                            interactionSource = null,
                            indication = ripple(bounded = false, radius = 32.dp),
                            onClick = {
                                if (horizontalDragDetected) return@combinedClickable
                                onClick()
                            },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                showMenu = true
                            }
                        )
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = headline,
                    style = MaterialTheme.typography.bodyLarge,
//                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                if (!defaultOrFirstPhone.isNullOrBlank()) {
                    Text(
                        text = defaultOrFirstPhone,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
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
                offset = DpOffset(42.dp, 48.dp),
            ) {
                DropdownMenuItem(
                    contentPadding = PaddingValues(start = 20.dp, end = 26.dp),
                    text = { Text(stringResource(R.string.select)) },
                    leadingIcon = { Icon(Icons.Default.CheckBox, null) },
                    onClick = {
                        showMenu = false
                        onSelectToggle()
                    }
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
                DropdownMenuItem(
                    contentPadding = PaddingValues(start = 20.dp, end = 26.dp),
                    text = { Text("View contact") },
                    leadingIcon = { Icon(Icons.Rounded.AccountCircle, null) },
                    onClick = {
                        showMenu = false
                        navigator.navigate(ContactDetailsScreenDestination(contactId = contact.id))
                    }
                )
                DropdownMenuItem(
                    contentPadding = PaddingValues(start = 20.dp, end = 26.dp),
                    text = { Text("Edit contact") },
                    leadingIcon = { Icon(Icons.Default.Edit, null) },
                    onClick = {
                        showMenu = false
                        navigator.navigate(ContactEditScreenDestination(contactId = contact.id))
                    }
                )
                DropdownMenuItem(
                    contentPadding = PaddingValues(start = 20.dp, end = 26.dp),
                    text = { Text("Share contact") },
                    leadingIcon = { Icon(Icons.Default.Share, null) },
                    onClick = {
                        showMenu = false
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "${contact.name}\n${contact.phoneNumbers.joinToString(", ")}")
                        }
                        context.startActivity(Intent.createChooser(intent, "Share contact"))
                    }
                )
                if (!defaultOrFirstPhone.isNullOrBlank()) {
                    DropdownMenuItem(
                        contentPadding = PaddingValues(start = 20.dp, end = 26.dp),
                        text = { Text("Copy number") },
                        leadingIcon = { Icon(Icons.Default.ContentCopy, null) },
                        onClick = {
                            showMenu = false
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Phone number", defaultOrFirstPhone))
                            Toast.makeText(context, "Number copied", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
                DropdownMenuItem(
                    contentPadding = PaddingValues(start = 20.dp, end = 26.dp),
                    text = { Text(if (contact.isFavorite) "Remove from Favourites" else "Add to Favourites") },
                    leadingIcon = { Icon(Icons.Default.Star, null, tint = if (contact.isFavorite) MaterialTheme.colorScheme.error else LocalContentColor.current) },
                    onClick = {
                        showMenu = false
                        contactsVM.toggleFavorite(contact)
                    }
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
                DropdownMenuItem(
                    contentPadding = PaddingValues(start = 20.dp, end = 26.dp),
                    text = { Text("Delete contact", color = MaterialTheme.colorScheme.error) },
                    leadingIcon = { Icon(ImageVector.vectorResource(id = R.drawable.ic_delete), null, tint = MaterialTheme.colorScheme.error) },
                    onClick = {
                        showMenu = false
                        showDeleteConfirm = true
                    }
                )
            }
        }
//        RillDropdownMenu(
//            expanded         = showMenu && !selectionMode,
//            onDismissRequest = { showMenu = false }
//        ) {
//            RillDropdownMenuItem(
//                text     = stringResource(R.string.select),
//                icon     = Icons.Default.CheckBox,
//                iconTint = Color(0xFF9C27B0),
//                onClick  = {
//                    showMenu = false
//                    onSelectToggle()
//                }
//            )
//            HorizontalDivider(
//                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
//                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
//            )
//            RillDropdownMenuItem(
//                text     = "View contact",
//                icon     = Icons.Filled.AccountCircle,
//                iconTint = Color(0xFF2196F3),
//                onClick  = {
//                    showMenu = false
//                    navigator.navigate(ContactDetailsScreenDestination(contactId = contact.id))
//                }
//            )
//            RillDropdownMenuItem(
//                text     = "Edit contact",
//                icon     = Icons.Default.Edit,
//                iconTint = Color(0xFF00BCD4),
//                onClick  = {
//                    showMenu = false
//                    navigator.navigate(ContactEditScreenDestination(contactId = contact.id))
//                }
//            )
//            if (!defaultOrFirstPhone.isNullOrBlank()) {
//                RillDropdownMenuItem(
//                    text     = "Copy number",
//                    icon     = Icons.Default.ContentCopy,
//                    iconTint = Color(0xFF009688),
//                    onClick  = {
//                        showMenu = false
//                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
//                        clipboard.setPrimaryClip(ClipData.newPlainText("Phone number", defaultOrFirstPhone))
//                        Toast.makeText(context, "Number copied", Toast.LENGTH_SHORT).show()
//                    }
//                )
//            }
//            RillDropdownMenuItem(
//                text     = "Share contact",
//                icon     = Icons.Default.Share,
//                iconTint = Color(0xFFFF9800),
//                onClick  = {
//                    showMenu = false
//                    val intent = Intent(Intent.ACTION_SEND).apply {
//                        type = "text/plain"
//                        putExtra(Intent.EXTRA_TEXT, "${contact.name}\n${contact.phoneNumbers.joinToString(", ")}")
//                    }
//                    context.startActivity(Intent.createChooser(intent, "Share contact"))
//                }
//            )
//            HorizontalDivider(
//                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
//                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
//            )
//            RillDropdownMenuItem(
//                text     = if (contact.isFavorite) "Remove from Favourites" else "Add to Favourites",
//                icon     = Icons.Default.Star,
//                iconTint = if (contact.isFavorite) Color(0xFFF44336) else Color(0xFFE91E63),
////                isDestructive = contact.isFavorite,
//                onClick  = {
//                    showMenu = false
//                    contactsVM.toggleFavorite(contact)
//                }
//            )
//            HorizontalDivider(
//                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
//                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
//            )
//            RillDropdownMenuItem(
//                text     = "Delete contact",
//                icon     = ImageVector.vectorResource(id = R.drawable.ic_delete),
//                iconTint = Color(0xFFF44336),
//                isDestructive = true,
//                onClick  = {
//                    showMenu = false
//                    showDeleteConfirm = true
//                }
//            )
//        }
    }
} // end AZListContent

@Composable
fun AlphabetSideBar(
    alphabet: List<Char>,
    selectedChar: Char?,
    modifier: Modifier = Modifier,
    onLetterSelected: (Char) -> Unit,
    onDragEnd: () -> Unit
) {
    var columnHeight by remember { mutableStateOf(0) }

    Surface(
        modifier = modifier
            .width(24.dp)
            .wrapContentHeight()
            .onGloballyPositioned { columnHeight = it.size.height }
            .pointerInput(alphabet) {
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        if (columnHeight > 0) {
                            val itemHeight = columnHeight.toFloat() / alphabet.size
                            val index = (offset.y / itemHeight).toInt()
                            val char = alphabet.getOrNull(index.coerceIn(0, alphabet.lastIndex))
                            if (char != null) onLetterSelected(char)
                        }
                    },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() }
                ) { change, _ ->
                    if (columnHeight > 0) {
                        val itemHeight = columnHeight.toFloat() / alphabet.size
                        val index = (change.position.y / itemHeight).toInt()
                        val char = alphabet.getOrNull(index.coerceIn(0, alphabet.lastIndex))
                        if (char != null) onLetterSelected(char)
                    }
                }
            },
        color = Color.Transparent,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            alphabet.forEach { char ->
                val isSelected = char == selectedChar
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = char.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}
