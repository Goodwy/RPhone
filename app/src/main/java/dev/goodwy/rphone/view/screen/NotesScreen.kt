package dev.goodwy.rphone.view.screen

import android.content.Intent
import dev.goodwy.rphone.view.theme.TabTransitionStyle
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Help
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.*
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.ContactsViewModel
import dev.goodwy.rphone.controller.util.NoteEntry
import dev.goodwy.rphone.controller.util.NoteManager
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.view.components.InfoDialog
import dev.goodwy.rphone.view.components.PlaceholderView
import dev.goodwy.rphone.view.components.RillAvatar
import dev.goodwy.rphone.view.components.RillDialog
import dev.goodwy.rphone.view.components.RillScrollAnimatedItem
import dev.goodwy.rphone.view.components.ScrollHapticsEffect
import dev.goodwy.rphone.view.theme.MyColors.cardColor
import dev.goodwy.rphone.view.theme.MyColors.cardColorSelected
import dev.goodwy.rphone.view.theme.customColors
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.ContactScreenDestination
import com.ramcosta.composedestinations.generated.destinations.DialPadScreenDestination
import com.ramcosta.composedestinations.generated.destinations.FavoritesScreenDestination
import com.ramcosta.composedestinations.generated.destinations.RecentScreenDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinActivityViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Destination<RootGraph>(style = TabTransitionStyle::class)
@Composable
fun NotesScreen(navController: NavController, navigator: DestinationsNavigator) {
    val context = LocalContext.current
    val prefs = koinInject<PreferenceManager>()
    val haptic = LocalHapticFeedback.current
    val contactsVM: ContactsViewModel = koinActivityViewModel()
    val allContacts by contactsVM.allContacts.collectAsState()

    // Build phone→photoUri lookup map
    val phoneToPhotoUri = remember(allContacts) {
        buildMap {
            allContacts.forEach { c ->
                c.phoneNumbers.forEach { num ->
                    put(num.filter { it.isDigit() || it == '+' }, c.photoUri)
                }
            }
        }
    }

    var notes by remember { mutableStateOf(NoteManager.getAllNotes(context)) }
    var showOverflow by remember { mutableStateOf(false) }

    var selectedNotes by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showNotesSelectionMenu by remember { mutableStateOf(false) }
    var showNotesDeleteConfirm by remember { mutableStateOf(false) }

    BackHandler(enabled = selectedNotes.isNotEmpty()) {
        selectedNotes = emptySet()
    }

    if (showNotesDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showNotesDeleteConfirm = false },
            title = { Text("Delete ${selectedNotes.size} notes?") },
            confirmButton = {
                Button(
                    onClick = {
                        showNotesDeleteConfirm = false
                        notes.filter { selectedNotes.contains(it.file.absolutePath) }.forEach { it.file.delete() }
                        selectedNotes = emptySet()
                        notes = NoteManager.getAllNotes(context)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { showNotesDeleteConfirm = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
    var selectedNote by remember { mutableStateOf<NoteEntry?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var editorNote by remember { mutableStateOf<NoteEntry?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var noteToDelete by remember { mutableStateOf<NoteEntry?>(null) }

    fun refreshNotes() { notes = NoteManager.getAllNotes(context) }

    if (showEditor && editorNote != null) {
        NoteEditorDialog(
            contactName = editorNote!!.contactName,
            phoneNumber = editorNote!!.phoneNumber,
            onDismiss = { showEditor = false; editorNote = null; refreshNotes() }
        )
    }

    if (showDeleteConfirm && noteToDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Note") },
            text = { Text("Delete note for ${noteToDelete!!.contactName}?") },
            confirmButton = {
                TextButton(onClick = {
                    NoteManager.deleteNoteFile(noteToDelete!!.file)
                    showDeleteConfirm = false
                    refreshNotes()
                }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    val coroutineScope = rememberCoroutineScope()

    val pillNav = remember { prefs.getBoolean(PreferenceManager.KEY_PILL_NAV, false) }
    val favouritesEnabled = prefs.getBoolean(PreferenceManager.KEY_TAB_SHOW_FAVORITES, true)
    val contactsEnabled = prefs.getBoolean(PreferenceManager.KEY_TAB_SHOW_CONTACTS, true)
    val dialpadEnabled = prefs.getBoolean(PreferenceManager.KEY_TAB_SHOW_DIALPAD, true)

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
                            val dx = change.position.x - startX
                            val dy = change.position.y - startY
                            val elapsed = System.currentTimeMillis() - startTime
                            if (!triggered && elapsed >= 150L && !change.isConsumed && abs(dx) > 700f && abs(dx) > abs(dy) * 5.5f) {
                                triggered = true
                                if (dx > 0) {
                                    val route = when {
                                        dialpadEnabled -> DialPadScreenDestination().route
                                        contactsEnabled -> ContactScreenDestination.route
                                        else -> RecentScreenDestination.route
                                    }
                                    // swipe right from Notes → Contacts
                                    coroutineScope.launch {
                                        navController.navigate(route) {
                                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                            launchSingleTop = true; restoreState = true
                                        }
                                    }
                                } else {
                                    val route = when {
                                        favouritesEnabled -> FavoritesScreenDestination.route
                                        else -> RecentScreenDestination.route
                                    }
                                    // swipe left from Notes → Favorites (wrap)
                                    coroutineScope.launch {
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
                targetState = selectedNotes.isNotEmpty(),
                transitionSpec = {
                    (fadeIn() + expandVertically()) togetherWith (fadeOut() + shrinkVertically())
                },
                label = "TopBarTransition"
            ) { isSelecting ->
                if (!isSelecting) {
                    TopAppBar(
                        windowInsets = WindowInsets.systemBars.only(
                            WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                        ),
                        modifier = Modifier.padding(start = 8.dp, end = 4.dp),
                        title = { Text(stringResource(R.string.call_notes), fontWeight = FontWeight.Bold) },
                        actions = {
                            var showAboutNotesDialog by remember { mutableStateOf(false) }
                            if (showAboutNotesDialog) {
                                InfoDialog(
                                    title = stringResource(R.string.about_call_notes),
                                    subtitle = stringResource(R.string.about_call_notes_subtitle),
                                    onDismiss = { showAboutNotesDialog = false }
                                )
                            }
                            Box {
                                IconButton(onClick = { showOverflow = true }) {
                                    Icon(Icons.Default.MoreVert, "More")
                                }

//                                RillDropdownMenu(
//                                    expanded = showOverflow,
//                                    onDismissRequest = { showOverflow = false }
//                                ) {
//                                    RillDropdownMenuItem(
//                                        text     = stringResource(R.string.about_call_notes),
//                                        icon     = Icons.AutoMirrored.Outlined.Help,
//                                        iconTint = androidx.compose.ui.graphics.Color(0xFF607D8B),
//                                        onClick  = {
//                                            showOverflow = false
//                                            showAboutNotesDialog = true
//                                        }
//                                    )
//                                    RillDropdownMenuItem(
//                                        text     = "Hide call notes",
//                                        icon     = Icons.Default.VisibilityOff,
//                                        iconTint = androidx.compose.ui.graphics.Color(0xFF607D8B),
//                                        onClick  = {
//                                            showOverflow = false
//                                            prefs.setBoolean(PreferenceManager.KEY_TAB_SHOW_NOTES, false)
//                                            navigator.navigateUp()
//                                        }
//                                    )
//                                }
                            }

                            AnimatedVisibility(
                                visible = showOverflow,
                                enter = slideInVertically(initialOffsetY = { -it }, animationSpec = tween(320, easing = FastOutSlowInEasing)) + fadeIn(tween(280)),
                                exit  = slideOutVertically(targetOffsetY = { -it }, animationSpec = tween(420, easing = FastOutLinearInEasing)) + fadeOut(tween(380))
                            ) {
                                DropdownMenu(
                                    shape = RoundedCornerShape(16.dp),
                                    expanded = showOverflow,
                                    onDismissRequest = { showOverflow = false },
                                        offset = DpOffset(0.dp, 24.dp),
                                ) {
                                    DropdownMenuItem(
                                        contentPadding = PaddingValues(start = 20.dp, end = 26.dp),
                                        text = { Text(stringResource(R.string.about_call_notes)) },
                                        leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Help, null) },
                                        onClick = {
                                            showOverflow = false
                                            showAboutNotesDialog = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        contentPadding = PaddingValues(start = 20.dp, end = 26.dp),
                                        text = { Text("Hide call notes") },
                                        leadingIcon = { Icon(Icons.Default.VisibilityOff, null) },
                                        onClick = {
                                            showOverflow = false
                                            prefs.setBoolean(PreferenceManager.KEY_TAB_SHOW_NOTES, false)
                                            navigator.navigateUp()
                                        }
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                    )
                } else {
                    val shareText = stringResource(R.string.share)
                    BatchNotesActionBar(
                        selectedCount = selectedNotes.size,
                        onClear = { selectedNotes = emptySet() },
                        onDelete = {
                            notes.filter { selectedNotes.contains(it.file.absolutePath) }.forEach { it.file.delete() }
                            selectedNotes = emptySet()
                            notes = NoteManager.getAllNotes(context)
                        },
                        onShare = {
                            val text = notes.filter { selectedNotes.contains(it.file.absolutePath) }.joinToString("\n\n") { "${it.contactName}: ${it.content}" }
                            val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
                            context.startActivity(Intent.createChooser(intent, shareText))
                        },
                        onDeselect = {
                            selectedNotes = emptySet()
                        },
                        onSelectAll = {
                            selectedNotes = notes.map { it.file.absolutePath }.toSet()
                        },
                        isAllSelected = selectedNotes == notes.map { it.file.absolutePath }.toSet()
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0)
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (notes.isEmpty()) {
                    PlaceholderView(
                        icon = Icons.AutoMirrored.Outlined.StickyNote2,
                        title = "No Notes Yet",
                        description = "Notes taken during calls appear here"
                    )
                } else {
                    val listState = rememberLazyListState()
                    val selectionMode = selectedNotes.isNotEmpty()
                    ScrollHapticsEffect(listState = listState)
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(notes, key = { it.file.absolutePath }) { note ->
                            val safePhone = note.phoneNumber.filter { it.isDigit() || it == '+' }
                            val photoUri = phoneToPhotoUri[safePhone]
                            RillScrollAnimatedItem {
                                NoteCard(
                                    note = note,
                                    photoUri = photoUri,
                                    isSelected = selectedNotes.contains(note.file.absolutePath),
                                    onClick = {
                                        if (selectionMode) {
                                            val key = note.file.absolutePath
                                            selectedNotes = if (selectedNotes.contains(key)) selectedNotes - key else selectedNotes + key
                                        } else {
                                            editorNote = note
                                            showEditor = true
                                        }
                                    },
                                    onLongClick = {
                                        if (selectionMode) {
                                            val key = note.file.absolutePath
                                            selectedNotes = if (selectedNotes.contains(key)) selectedNotes - key else selectedNotes + key
                                        } else {
                                            val key = note.file.absolutePath
                                            selectedNotes =
                                                if (selectedNotes.contains(key)) selectedNotes - key else selectedNotes + key
                                        }
                                    },
                                    onAvatarLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        selectedNote = note
                                    },
                                )
                            }
                        }
                        item { Spacer(Modifier.height(80.dp)) }
                    }
                }
            } // end Column

            // Long-press context menu
            if (selectedNote != null) {
//                RillDropdownMenu(
//                    expanded         = true,
//                    onDismissRequest = { selectedNote = null }
//                ) {
//                    RillDropdownMenuItem(
//                        text     = stringResource(R.string.select),
//                        icon     = Icons.Default.CheckBox,
//                        iconTint = androidx.compose.ui.graphics.Color(0xFF9C27B0),
//                        onClick  = {
//                            selectedNote?.let { selectedNotes = setOf(it.file.absolutePath) }
//                            selectedNote = null
//                        }
//                    )
//                    HorizontalDivider(
//                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
//                        color    = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
//                    )
//                    RillDropdownMenuItem(
//                        text     = stringResource(R.string.share),
//                        icon     = Icons.Default.Share,
//                        iconTint = androidx.compose.ui.graphics.Color(0xFF2196F3),
//                        onClick  = {
//                            val note = selectedNote!!
//                            val intent = Intent(Intent.ACTION_SEND).apply {
//                                type = "text/plain"
//                                putExtra(Intent.EXTRA_SUBJECT, "Note: ${note.contactName}")
//                                putExtra(Intent.EXTRA_TEXT, note.content)
//                            }
//                            context.startActivity(Intent.createChooser(intent, "Share Note"))
//                            selectedNote = null
//                        }
//                    )
//                    RillDropdownMenuItem(
//                        text          = stringResource(R.string.delete),
//                        icon          = ImageVector.vectorResource(id = R.drawable.ic_delete),
//                        isDestructive = true,
//                        onClick       = {
//                            noteToDelete = selectedNote
//                            selectedNote = null
//                            showDeleteConfirm = true
//                        }
//                    )
//                }
                AnimatedVisibility(
                    visible = true,
                    enter = slideInVertically(initialOffsetY = { -it }, animationSpec = tween(320, easing = FastOutSlowInEasing)) + fadeIn(tween(280)),
                    exit  = slideOutVertically(targetOffsetY = { -it }, animationSpec = tween(420, easing = FastOutLinearInEasing)) + fadeOut(tween(380))
                ) {
                    DropdownMenu(
                        shape = RoundedCornerShape(16.dp),
                        expanded = selectedNote != null,
                        onDismissRequest = { selectedNote = null },
                        offset = DpOffset(56.dp, 76.dp),
                    ) {
                        DropdownMenuItem(
                            contentPadding = PaddingValues(start = 20.dp, end = 26.dp),
                            text = { Text(stringResource(R.string.select)) },
                            leadingIcon = { Icon(Icons.Default.CheckBox, null) },
                            onClick = {
                                selectedNote?.let { selectedNotes = setOf(it.file.absolutePath) }
                                selectedNote = null
                            }
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        val shareText = stringResource(R.string.share)
                        DropdownMenuItem(
                            contentPadding = PaddingValues(start = 20.dp, end = 26.dp),
                            text = { Text(shareText) },
                            leadingIcon = { Icon(Icons.Default.Share, null) },
                            onClick = {
                                val note = selectedNote!!
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "Note: ${note.contactName}")
                                    putExtra(Intent.EXTRA_TEXT, note.content)
                                }
                                context.startActivity(Intent.createChooser(intent, shareText))
                                selectedNote = null
                            }
                        )
                        DropdownMenuItem(
                            contentPadding = PaddingValues(start = 20.dp, end = 26.dp),
                            text = { Text(stringResource(R.string.delete)) },
                            leadingIcon = { Icon(ImageVector.vectorResource(id = R.drawable.ic_delete), null) },
                            onClick = {
                                noteToDelete = selectedNote
                                selectedNote = null
                                showDeleteConfirm = true
                            }
                        )
                    }
                }
            }
        } // end inner Box
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteCard(
    note: NoteEntry,
    photoUri: String? = null,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onAvatarLongClick: (() -> Unit)? = null,
) {
    val dateStr = remember(note.lastModified) {
        SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault()).format(Date(note.lastModified))
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val cornerRadius by animateDpAsState(
        if (isPressed) 32.dp else 20.dp,
        spring(stiffness = Spring.StiffnessMediumLow),
        label = "ButtonShapeAnimation"
    )

    val cardBgColor by animateColorAsState(
        targetValue = if (isSelected) cardColorSelected else cardColor,
        animationSpec = tween(200), label = "noteBg"
    )
    Box(modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                    interactionSource = interactionSource,
                    indication = null,
                ),
            shape = RoundedCornerShape(cornerRadius),
            color = cardBgColor
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Top
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
                        modifier = Modifier.size(44.dp)
                            .scale(iconScale)
                            .alpha(iconAlpha),
                        icon = Icons.Rounded.Check,
                        iconContainerColor = MaterialTheme.colorScheme.primary
                    )
                } else {
                    RillAvatar(
                        name = note.contactName,
                        photoUri = photoUri,
                        modifier = Modifier.size(44.dp)
                            .then(
                                if (onAvatarLongClick != null)
                                    Modifier.combinedClickable(
                                        interactionSource = null,
                                        indication = ripple(bounded = false, radius = 32.dp),
                                        onClick = onClick,
                                        onLongClick = onAvatarLongClick
                                    )
                                else Modifier
                            ),
                        shape = CircleShape
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            note.contactName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(
                            dateStr,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(2.dp))
                    }
                    if (note.phoneNumber.isNotEmpty()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            note.phoneNumber,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = note.content,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorDialog(
    contactName: String,
    phoneNumber: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var text by remember {
        mutableStateOf(NoteManager.readNote(context, contactName, phoneNumber))
    }

    ModalBottomSheet(
        onDismissRequest = {
            NoteManager.writeNote(context, contactName, phoneNumber, text)
            onDismiss()
        },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
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
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        contactName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Bold
                    )
                    if (phoneNumber.isNotEmpty()) {
                        Text(
                            phoneNumber,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                val interactionDeleteSource = remember { MutableInteractionSource() }
                val isDeletePressed by interactionDeleteSource.collectIsPressedAsState()
                val cornerRadiusDelete by animateDpAsState(
                    if (isDeletePressed) 12.dp else 40.dp,
                    spring(stiffness = Spring.StiffnessMediumLow),
                    label = "ButtonShapeAnimation"
                )
                Button(
                    interactionSource = interactionDeleteSource,
                    onClick = {
                        NoteManager.writeNote(context, contactName, phoneNumber, "")
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    shape = RoundedCornerShape(cornerRadiusDelete)
                ) { Icon(ImageVector.vectorResource(id = R.drawable.ic_delete), stringResource(R.string.delete)) }

                val interactionSaveSource = remember { MutableInteractionSource() }
                val isSavePressed by interactionSaveSource.collectIsPressedAsState()
                val cornerRadiusSave by animateDpAsState(
                    if (isSavePressed) 12.dp else 40.dp,
                    spring(stiffness = Spring.StiffnessMediumLow),
                    label = "ButtonShapeAnimation"
                )
                Button(
                    interactionSource = interactionSaveSource,
                    onClick = {
                        NoteManager.writeNote(context, contactName, phoneNumber, text)
                        onDismiss()
                    },
                    shape = RoundedCornerShape(cornerRadiusSave)
                ) { Text("Save") }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp),
                placeholder = { Text(stringResource(R.string.type_your_note)) },
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = cardColor,
                    unfocusedContainerColor = cardColor
                ),
                minLines = 8
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchNotesActionBar(
    selectedCount: Int,
    onClear: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onDeselect: () -> Unit,
    onSelectAll: () -> Unit,
    isAllSelected: Boolean
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth(),
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
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
            IconButton(onClick = { showDeleteConfirm = true }) {
                Icon(ImageVector.vectorResource(id = R.drawable.ic_delete), "Delete selected")
            }
            IconButton(onClick = onShare) {
                Icon(Icons.Default.Share, stringResource(R.string.share))
            }
        }
    }

    if (showDeleteConfirm) {
        RillDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = "Delete Notes",
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
                "Are you sure you want to delete $selectedCount selected notes?",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
