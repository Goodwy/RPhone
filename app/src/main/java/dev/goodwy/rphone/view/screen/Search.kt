package dev.goodwy.rphone.view.screen

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.rounded.MicNone
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import dev.goodwy.rphone.R
import dev.goodwy.rphone.cardCornerBig
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState
import dev.goodwy.rphone.controller.ContactsViewModel
import dev.goodwy.rphone.controller.util.VoiceSearchContract
import dev.goodwy.rphone.view.components.*
import dev.goodwy.rphone.view.theme.MyColors.cardColor
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.DialPadScreenDestination
import com.ramcosta.composedestinations.generated.destinations.NotesScreenDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.goodwy.rphone.cardCornerSmall
import dev.goodwy.rphone.controller.CallLogViewModel
import dev.goodwy.rphone.controller.util.NoteManager
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.controller.util.normalizeNumberDigits
import dev.goodwy.rphone.modal.data.CallLogEntry
import dev.goodwy.rphone.view.components.tiles.SingleTile
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinActivityViewModel

@OptIn(ExperimentalPermissionsApi::class)
@Destination<RootGraph>
@Composable
fun SearchScreen(navController: NavController, navigator: DestinationsNavigator, autoStartVoiceSearch: Boolean = false) {
    val permState = rememberPermissionState(Manifest.permission.READ_CONTACTS)
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val showButton by remember { derivedStateOf { listState.firstVisibleItemIndex > 2 } }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = cardColor //MaterialTheme.colorScheme.surface
    ) { innerPadding ->
        Box(modifier = Modifier
//            .padding(innerPadding)
            .padding(
                top = innerPadding.calculateTopPadding() + 8.dp,
                start = 0.dp, //innerPadding.calculateStartPadding(LayoutDirection.Ltr),
                end = 0.dp, //innerPadding.calculateEndPadding(LayoutDirection.Ltr),
                bottom = 0.dp
            )
            .background(MaterialTheme.colorScheme.surface)
            .fillMaxSize()
        ) {
            ContactSearchContent(
                navigator = navigator,
                isGranted = permState.status == PermissionStatus.Granted,
                onRequestPermission = { permState.launchPermissionRequest() },
                listState = listState,
                autoStartVoiceSearch = autoStartVoiceSearch
            )
            ScrollToTopButton(
                visible = showButton,
                onClick = { scope.launch { listState.animateScrollToItem(0) } }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun ContactSearchContent(
    navigator: DestinationsNavigator,
    isGranted: Boolean,
    onRequestPermission: () -> Unit,
    listState: LazyListState,
    autoStartVoiceSearch: Boolean
) {
    if (!isGranted) {
        PermissionDeniedView(
            icon = Icons.Rounded.People,
            title = stringResource(R.string.contacts_permission),
            description = stringResource(R.string.search_contacts_permission_description),
            onGrantClick = onRequestPermission
        )
        return
    }

    val context = LocalContext.current
    val prefs = koinInject<PreferenceManager>()
    val contactsVM: ContactsViewModel = koinActivityViewModel()
    val callLogVM: CallLogViewModel = koinActivityViewModel()

    val contacts by contactsVM.allContacts.collectAsState()
    val callLogs by callLogVM.allCallLogs.collectAsState()

    val settingsVer by prefs.settingsChanged.collectAsState()
    val filterState = remember(settingsVer) { prefs.getSearchFilterState() }
    val displayOrder = remember(settingsVer) { prefs.getInt(PreferenceManager.KEY_CONTACT_DISPLAY_ORDER, 0) }

    var queryFieldValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }
    val query = queryFieldValue.text
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    var isInitialized by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!isInitialized) {
            focusRequester.requestFocus()
            queryFieldValue =
                queryFieldValue.copy(selection = TextRange(queryFieldValue.text.length))
            keyboardController?.show()
            isInitialized = true
        }
    }

    val filteredContacts = remember(query, contacts, filterState.contacts) {
        if (!filterState.contacts || query.isBlank()) emptyList()
        else contacts.filter {
            it.displayName.contains(query, ignoreCase = true) ||
                    it.nickname.contains(query, ignoreCase = true) ||
                    it.company.contains(query, ignoreCase = true) ||
                    it.jobTitle.contains(query, ignoreCase = true) ||
                    it.phoneNumbers.any { number -> number.replace(" ", "").replace("-", "").contains(query.replace(" ", "").replace("-", "")) } ||
                    it.emails.any { email -> email.value.replace(" ", "").contains(query.replace(" ", "")) } ||
                    it.addresses.any { address -> address.formattedAddress.replace(" ", "").contains(query.replace(" ", "")) } ||
                    it.events.any { event -> event.date.replace(" ", "").replace("-", "").replace(".", "")
                        .contains(query.replace(" ", "").replace("-", "").replace(".", "")) }
        }
    }

    // Numbers that show up in the call log but aren't saved as a contact — i.e. what "Non
    // contacts" in the Filter menu refers to. Deduplicated by normalized number, keeping the
    // most recent entry (callLogs is already date-descending).
    val nonContactResults = remember(query, callLogs, filterState.nonContacts) {
        if (!filterState.nonContacts || query.isBlank()) emptyList()
        else {
            val seen = LinkedHashMap<String, CallLogEntry>()
            callLogs.asSequence()
                .filter { it.contactId.isNullOrBlank() }
                .forEach { entry ->
                    val key = normalizeNumberDigits(entry.number).filter { it.isDigit() }.takeLast(9)
                        .ifBlank { entry.number }
                    seen.putIfAbsent(key, entry)
                }
            seen.values.filter { entry ->
                entry.number.replace(" ", "").contains(query.replace(" ", "")) ||
                        (entry.isCallerIdName && (entry.name?.contains(query, ignoreCase = true) == true))
            }
        }
    }

    var refreshNoteTrigger by remember { mutableIntStateOf(0) }
    // Notes attached to a contact/number (from the call screen or contact info screen).
    val contactNoteResults = remember(query, filterState.contactNotes, refreshNoteTrigger) {
        if (!filterState.contactNotes || query.isBlank()) emptyList()
        else NoteManager.getAllNotes(context).filter { note ->
            note.contactName.contains(query, ignoreCase = true) ||
                    note.phoneNumber.contains(query.filter { c -> c.isDigit() || c == '+' }.ifEmpty { query }, ignoreCase = true) ||
                    note.content.contains(query, ignoreCase = true)
        }
    }

    val totalResults = filteredContacts.size + nonContactResults.size + contactNoteResults.size
    val hasAnyResults = totalResults > 0

    // Voice search
    var isVoiceSearchTriggered by rememberSaveable { mutableStateOf(false) }
    var voiceSearchResult by remember { mutableStateOf<String?>(null) }
    // Synchronizing the query with textFieldValue.text
    LaunchedEffect(voiceSearchResult) {
        voiceSearchResult?.let { result ->
            queryFieldValue = TextFieldValue(result, selection = TextRange(result.length))
            keyboardController?.hide()
            voiceSearchResult = null
        }
    }
    val micPermissionState = rememberPermissionState(Manifest.permission.RECORD_AUDIO)
    val voiceSearchLauncher = rememberLauncherForActivityResult(VoiceSearchContract()) { result ->
        result?.let {
            voiceSearchResult = result
            keyboardController?.hide()
        }
    }
    // Automatic activation of voice search
    LaunchedEffect(Unit) {
        if (autoStartVoiceSearch && !isVoiceSearchTriggered) {
            // Allow time for the UI to fully initialize
            delay(300)

            if (micPermissionState.status == PermissionStatus.Granted) {
                isVoiceSearchTriggered = true
                voiceSearchLauncher.launch(Unit)
            } else {
                // Allow time for the UI to fully initialize
                micPermissionState.launchPermissionRequest()
                // You can wait for the resolution result or handle it in a separate LaunchedEffect
            }
        }
    }
    // If permission has been requested and granted, we'll start voice search
    LaunchedEffect(micPermissionState.status) {
        if (autoStartVoiceSearch && !isVoiceSearchTriggered && micPermissionState.status == PermissionStatus.Granted) {
            isVoiceSearchTriggered = true
            voiceSearchLauncher.launch(Unit)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Search bar
        Surface(
            modifier = Modifier.fillMaxWidth(),
//            shape = RoundedCornerShape(28.dp),
            color = cardColor, //MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 0.dp
        ) {
            TextField(
                value = queryFieldValue,
                onValueChange = { queryFieldValue = it },
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                placeholder = { Text(stringResource(R.string.search)) },
                leadingIcon = {
                    IconButton(onClick = { navigator.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                trailingIcon = {
                    Row {
                        AnimatedVisibility(
                            visible = query.isNotEmpty(),
                            enter = fadeIn() + scaleIn(),
                            exit = fadeOut() + scaleOut()
                        ) {
                            IconButton(onClick = {
                                queryFieldValue = TextFieldValue("", selection = TextRange(0))
                            }) {
                                Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.clear))
                            }
                        }

                        AnimatedVisibility(
                            visible = micPermissionState.status == PermissionStatus.Granted,
                            enter = fadeIn() + scaleIn(),
                            exit = fadeOut() + scaleOut()
                        ) {
                            IconButton(
                                onClick = {
                                    if (micPermissionState.status == PermissionStatus.Granted) {
                                        voiceSearchLauncher.launch(Unit)
                                    } else {
                                        micPermissionState.launchPermissionRequest()
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.MicNone,
                                    contentDescription = stringResource(R.string.voice_input),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        SearchFilterButton()
                        Spacer(modifier = Modifier.size(4.dp))
                    }
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                singleLine = true
            )
        }
        HorizontalDivider()

        // Call this number chip
        AnimatedVisibility(
            visible = query.isNotEmpty() && query.all { it.isDigit() || it == '+' || it == '-' || it == ' ' },
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                shape = RoundedCornerShape(cardCornerBig),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Row(
                    modifier = Modifier.padding(start = 24.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(Icons.Default.Call, null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        text = stringResource(R.string.call_query, query),
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        navigator.navigate(DialPadScreenDestination(initialNumber = query))
                    }) {
                        Icon(Icons.Default.Dialpad, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        when {
            query.isBlank() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Search,
                            null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Text(
                            stringResource(R.string.search),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            !hasAnyResults -> {
                Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Rounded.SearchOff,
                            null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Text(
//                            "No results for \"$query\"",
                            stringResource(R.string.no_results),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            else -> {
                ScrollHapticsEffect(listState = listState)
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (filteredContacts.isNotEmpty()) {
                        item {
                            RillSectionHeader(
                                title = stringResource(R.string.contacts),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 8.dp)
                            )
                            RillExpressiveCard {
                                filteredContacts.forEach { contact ->
                                    ContactListItem(
                                        contact = contact,
                                        navigator = navigator,
                                        displayOrder = displayOrder
                                    )
                                }
                            }
                        }
                    }

                    if (nonContactResults.isNotEmpty()) {
                        item {
                            RillSectionHeader(
                                title = stringResource(R.string.calls),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 8.dp)
                            )
                            RillExpressiveCard {
                                nonContactResults.forEach { entry ->
                                    SingleTile(
                                        title = entry.name?.ifEmpty { entry.number } ?: entry.number,
                                        subtitle = if (entry.name.isNullOrEmpty() || entry.name == entry.number) null else entry.number,
                                        icon = Icons.Default.Person,
                                        phoneNumber = entry.number,
                                        trailingContent = {
                                            IconButton(onClick = {
                                                navigator.navigate(DialPadScreenDestination(initialNumber = entry.number))
                                            }) {
                                                Icon(Icons.Default.Call, contentDescription = "Call", tint = MaterialTheme.colorScheme.primary)
                                            }
                                        },
                                        onClick = {
                                            navigator.navigate(DialPadScreenDestination(initialNumber = entry.number))
                                        }
                                    )
                                }
                            }
                        }
                    }

                    if (contactNoteResults.isNotEmpty()) {
                        item {
                            RillSectionHeader(
                                title = stringResource(R.string.call_notes),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 8.dp)
                            )
                            RillExpressiveCard {
                                contactNoteResults.forEach { note ->
                                    var showNoteEditor by remember { mutableStateOf(false) }
                                    SingleTile(
                                        title = note.contactName.ifBlank { note.phoneNumber.ifBlank { "Unknown" } },
                                        subtitle = note.content,
                                        icon = Icons.AutoMirrored.Outlined.StickyNote2,
                                        phoneNumber = note.phoneNumber,
                                        supportingContent = {
                                            Text(
                                                note.content,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.padding(top = 4.dp)
                                            )
                                        },
                                        onClick = {
                                            showNoteEditor = true
                                        },
                                        useLongClick = false
                                    )
                                    if (showNoteEditor) {
                                        NoteEditorDialog(
                                            contactName = note.contactName,
                                            phoneNumber = note.phoneNumber,
                                            onDismiss = {
                                                showNoteEditor = false
                                                refreshNoteTrigger++
                                            }
                                        )
                                    }
                                }
                                TextButton(
                                    onClick = {
                                        NavBarVisibilityState.hideForSearchResult = true
                                        navigator.navigate(NotesScreenDestination(highlightQuery = query))
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .offset(
                                            x = 0.dp,
                                            y = (-4).dp
                                        ), // We'll make up for the indentation
                                    colors = ButtonDefaults.textButtonColors().copy(containerColor = cardColor),
                                    shape = RoundedCornerShape(
                                        topStart = cardCornerSmall,
                                        topEnd = cardCornerSmall,
                                        bottomStart = cardCornerBig,
                                        bottomEnd = cardCornerBig
                                    ),
                                ) {
                                    Text(stringResource(R.string.filter_all))
                                }
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(300.dp)) }
                }
            }
        }
    }
}
