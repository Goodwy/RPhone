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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.rounded.MicNone
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
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
import com.ramcosta.composedestinations.generated.destinations.ContactDetailsScreenDestination
import com.ramcosta.composedestinations.generated.destinations.DialPadScreenDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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

    val contactsVM: ContactsViewModel = koinActivityViewModel()
    val contacts by contactsVM.allContacts.collectAsState()
    var query by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    val filteredContacts = remember(query, contacts) {
        if (query.isBlank()) emptyList()
        else contacts.filter {
            it.displayName.contains(query, ignoreCase = true) ||
                    it.nickname.contains(query, ignoreCase = true) ||
                    it.company.contains(query, ignoreCase = true) ||
                    it.jobTitle.contains(query, ignoreCase = true) ||
                    it.phoneNumbers.any { number -> number.replace(" ", "").contains(query.replace(" ", "")) } ||
                    it.emails.any { email -> email.value.replace(" ", "").contains(query.replace(" ", "")) } ||
                    it.addresses.any { address -> address.formattedAddress.replace(" ", "").contains(query.replace(" ", "")) } ||
                    it.events.any { event -> event.date.replace(" ", "").replace("-", "").replace(".", "")
                        .contains(query.replace(" ", "").replace("-", "").replace(".", "")) }
        }
    }

    // Voice search
    var textFieldValue by remember { mutableStateOf(TextFieldValue("")) }
    var isVoiceSearchTriggered by remember { mutableStateOf(false) }
    // Synchronizing the query with textFieldValue.text
    LaunchedEffect(textFieldValue.text) {
        query = textFieldValue.text
    }
    // When the query from external sources changes, we update textFieldValue
    LaunchedEffect(query) {
        if (textFieldValue.text != query) {
            textFieldValue = TextFieldValue(
                text = query,
                selection = TextRange(query.length) // cursor to the end
            )
        }
    }
    val micPermissionState = rememberPermissionState(Manifest.permission.RECORD_AUDIO)
    val voiceSearchLauncher = rememberLauncherForActivityResult(VoiceSearchContract()) { result ->
        result?.let {
            query = it
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
                value = textFieldValue,
                onValueChange = { textFieldValue = it },
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                placeholder = { Text("Search contacts or numbers") },
                leadingIcon = {
                    IconButton(onClick = { navigator.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
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
                                query = ""
                                textFieldValue = TextFieldValue("", selection = TextRange(0))
                            }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
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
                                    contentDescription = "Voice search",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
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
                    modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(Icons.Default.Call, null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        text = "Call $query",
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = {
                        navigator.navigate(DialPadScreenDestination(initialNumber = query))
                    }) {
                        Text("Open Dialpad", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        when {
            contacts.isEmpty() -> {
                PlaceholderView(
                    icon = Icons.Rounded.People,
                    title = "No contacts found",
                )
            }
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
                            "Search contacts or numbers",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            filteredContacts.isEmpty() -> {
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
                            "No results for \"$query\"",
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
                    item {
                        RillSectionHeader(title = "${filteredContacts.size} Result${if (filteredContacts.size != 1) "s" else ""}")
                        Spacer(modifier = Modifier.height(8.dp))
                        RillExpressiveCard {
                            filteredContacts.forEach { contact ->
                                RillListItem(
                                    headline = contact.name,
                                    supporting = contact.phoneNumbers.firstOrNull(),
                                    avatarName = contact.name,
                                    photoUri = contact.photoUri,
                                    onClick = {
                                        navigator.navigate(ContactDetailsScreenDestination(contactId = contact.id))
                                    }
                                )
                            }
                        }
                    }
                    item { Spacer(modifier = Modifier.height(100.dp)) }
                }
            }
        }
    }
}
