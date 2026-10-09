package dev.goodwy.rphone.view.screen

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.view.HapticFeedbackConstants
import android.view.Surface
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.MicNone
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.ContactsViewModel
import dev.goodwy.rphone.controller.util.formatPhoneNumber
import dev.goodwy.rphone.modal.data.Contact
import dev.goodwy.rphone.view.components.*
import dev.goodwy.rphone.view.theme.*
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.ContactEditScreenDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.ramcosta.composedestinations.result.ResultBackNavigator
import dev.goodwy.rphone.controller.util.ContactUtils.getPhoneNumber
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.controller.util.VoiceSearchContract
import dev.goodwy.rphone.modal.data.getDisplayName
import dev.goodwy.rphone.view.theme.MyColors.cardColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinActivityViewModel
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Destination<RootGraph>
@Composable
fun ContactSelectionScreen(
    navigator: DestinationsNavigator,
    resultNavigator: ResultBackNavigator<String>,
    title: String = "",
    isMultiSelect: Boolean = false,
    actionButtonText: String = "",
    returnContactId: Boolean = false,
    initialPhoneToAssign: String? = null,
    isExternalPick: Boolean = false
) {
    val context = LocalContext.current
    val view = LocalView.current
    val viewModel: ContactsViewModel = koinActivityViewModel()
    val allContacts by viewModel.allContacts.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    val listState = rememberLazyListState()
    val prefs = koinInject<PreferenceManager>()
    val scope = rememberCoroutineScope()

    var queryFieldValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }
    val searchQuery = queryFieldValue.text
    var selectedFilterTab by remember { mutableIntStateOf(0) }
    var selectedContactIds by remember { mutableStateOf(setOf<String>()) }
    var selectedPhoneNumbers by remember { mutableStateOf(setOf<String>()) }
    var pendingMultiNumberContact by remember { mutableStateOf<Contact?>(null) }

    val hideVoiceSearch = remember { prefs.getBoolean(PreferenceManager.KEY_HIDE_VOICE_SEARCH, false) }
    val displayOrder = remember { prefs.getInt(PreferenceManager.KEY_CONTACT_DISPLAY_ORDER, 0) }
    val sortOrder = remember { prefs.getInt(PreferenceManager.KEY_CONTACT_SORT_ORDER, 0) }
    val cardCorner  = remember { prefs.getInt(PreferenceManager.KEY_CARD_ROUNDNESS, RillShapeDefaults.DefaultRoundness) }

    // Voice search
    val keyboardController = LocalSoftwareKeyboardController.current
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

    // ─── Checking Permissions ───────────────────────────────────
    var hasContactsPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { permissions ->
        hasContactsPermission = permissions
        if (permissions) {
            viewModel.fetchContacts()
        }
    }

    LaunchedEffect(Unit) {
        if (hasContactsPermission) {
            viewModel.fetchContacts()
        } else {
            permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        }
    }
    // ────────────────────────────────────────────────────────────

    val filteredContacts = remember(allContacts, searchQuery, selectedFilterTab) {
        var list = when (selectedFilterTab) {
            1 -> allContacts.filter { it.isFavorite }
            2 -> allContacts.filter { it.isPrivate }
            else -> allContacts
        }
        if (searchQuery.isNotBlank()) {
            val cleanSearch = searchQuery.trim()
            list = list.filter {
                it.displayName.contains(cleanSearch, ignoreCase = true) ||
                it.phoneNumbers.any { num -> num.contains(cleanSearch) }
            }
        }
        list.sortedBy { it.displayName.lowercase() }
    }

//    val groupedContacts = remember(filteredContacts) {
//        filteredContacts.groupBy { contact ->
//            val first = contact.name.trim().firstOrNull()?.uppercaseChar() ?: '#'
//            if (first in 'A'..'Z') first.toString() else "#"
//        }
//    }
    val groupedContacts = remember(filteredContacts) {
        val mainGroups = filteredContacts.groupBy {
            val firstChar =
                if (sortOrder == 1) it.familyName.ifBlank { it.displayName }.firstOrNull()?.uppercaseChar() ?: '#'
                else it.givenName.ifBlank { it.displayName }.firstOrNull()?.uppercaseChar() ?: '#'
            if (firstChar.isLetter()) firstChar else '#'
        }.toMutableMap()

        val finalMap = linkedMapOf<Char, List<Contact>>()

        mainGroups.keys.filter { it.isLetter() }.sorted().forEach { char ->
            finalMap[char] = mainGroups[char]!!
        }

        val hashGroup = mainGroups['#']
        if (hashGroup != null) finalMap['#'] = hashGroup

        finalMap
    }

    val totalSelectedCount = maxOf(selectedContactIds.size, selectedPhoneNumbers.size)


    var visible by remember { mutableStateOf(false) }
    var isClosing by remember { mutableStateOf(false) }

    fun navigateBack() {
        if (isClosing) return
        isClosing = true
        scope.launch {
            delay(280.milliseconds)

            if (isExternalPick) {
                val activity = context as? Activity
                activity?.setResult(Activity.RESULT_CANCELED)
                activity?.finish()
            } else {
                navigator.navigateUp()
            }
        }
    }

    val alpha by animateFloatAsState(
        targetValue = if (visible && !isClosing) 1f else 0f,
        animationSpec = if (isClosing) tween(280, easing = FastOutLinearInEasing) else tween(350),
        label = "settingsAlpha"
    )
    val offsetY by animateDpAsState(
        targetValue = if (visible && !isClosing) 0.dp else if (isClosing) 60.dp else 30.dp,
        animationSpec = if (isClosing) tween(300, easing = FastOutLinearInEasing)
        else spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioLowBouncy),
        label = "settingsOffsetY"
    )
    LaunchedEffect(Unit) { visible = true }

    val rotation =
        (context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager).defaultDisplay.rotation
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val isRotation90 = rotation == if (isLtr) Surface.ROTATION_90 else Surface.ROTATION_270

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets.systemBars.only(
                    if (isRotation90) WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                    else WindowInsetsSides.Top
                ),
                title = {
                    Column {
                        Title(
                            if (isMultiSelect && totalSelectedCount > 0) {
                                stringResource(R.string.selected_items, totalSelectedCount)
                            } else {
                                title.ifBlank { stringResource(R.string.select_contacts) }
                            }
                        )
                        if (initialPhoneToAssign != null) Title(formatPhoneNumber(initialPhoneToAssign), false)
                    }
                },
                navigationIcon = {
                    NavigationIcon(onClick = { navigateBack() })
                },
                actions = {
                    if (hasContactsPermission) IconButton(onClick = { navigator.navigate(ContactEditScreenDestination(initialPhone = initialPhoneToAssign)) }) {
                        Icon(Icons.Rounded.PersonAdd, contentDescription = stringResource(R.string.create_contact))
                    }
                    if (isMultiSelect && totalSelectedCount > 0) {
                        IconButton(onClick = {
                            selectedContactIds = emptySet()
                            selectedPhoneNumbers = emptySet()
                        }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel))
                        }
                    }
                    Spacer(modifier = Modifier.size(6.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            if (isMultiSelect && totalSelectedCount > 0) {
                ExtendedFloatingActionButton(
                    onClick = {
                        val csvResult = if (returnContactId) {
                            selectedContactIds.joinToString(",")
                        } else {
                            selectedPhoneNumbers.joinToString(",")
                        }
                        resultNavigator.navigateBack(result = csvResult)
                    },
                    icon = { Icon(Icons.Default.Check, contentDescription = null) },
                    text = { Text(actionButtonText.ifBlank { stringResource(R.string.select) + " ($totalSelectedCount)" }, fontWeight = FontWeight.Bold) },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        BackHandler(enabled = !isClosing) { navigateBack() }
        if (!hasContactsPermission) {
            PermissionDeniedView(
                icon = Icons.Rounded.People,
                title = stringResource(R.string.contacts_permission),
                description = stringResource(R.string.contacts_permission_description),
                onGrantClick = { permissionLauncher.launch(Manifest.permission.READ_CONTACTS) }
            )
        } else {
            ScrollHapticsEffect(listState = listState)
            if (isLoading && allContacts.isEmpty()) {
                RillLoadingIndicatorView()
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            top = padding.calculateTopPadding(),
                            start = 0.dp,
                            end = 0.dp,
                            bottom = 0.dp
                        )
                        .alpha(alpha)
                        .offset(y = offsetY),
                    contentPadding = PaddingValues(/*start = 16.dp, end = 16.dp,*/ top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    // Search Bar
                    item {
                        val shape = if (cardCorner > 12) CircleShape else MaterialTheme.shapes.extraExtraLarge
                        Surface(
                            modifier = Modifier
                                .padding(horizontal = 16.dp).padding(bottom = 12.dp)
                                .fillMaxWidth().height(52.dp),
                            shape = shape,
                            color = cardColor
                        ) {
                            TextField(
                                value = queryFieldValue,
                                onValueChange = { queryFieldValue = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text(stringResource(R.string.search_contacts)) },
                                leadingIcon = {
                                    Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search_contacts),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .padding(start = 12.dp)
                                    )
                                },
                                trailingIcon = {
                                    Row {
                                        AnimatedVisibility(
                                            visible = searchQuery.isNotEmpty(),
                                            enter = fadeIn() + scaleIn(),
                                            exit = fadeOut() + scaleOut()
                                        ) {
                                            IconButton(
                                                onClick = { queryFieldValue = TextFieldValue("", selection = TextRange(0)) }
                                            ) {
                                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.clear))
                                            }
                                        }

                                        AnimatedVisibility(
                                            visible = micPermissionState.status == PermissionStatus.Granted && !hideVoiceSearch,
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

                                        Spacer(modifier = Modifier.size(4.dp))
                                    }
                                },
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    disabledContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                ),
                                singleLine = true
                            )
                        }
                    }

                    // Filter Chips
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp).padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            RillFilterChip(
                                label = stringResource(R.string.filter_all) + " (${allContacts.size})",
                                selected = selectedFilterTab == 0,
                                onClick = { selectedFilterTab = 0 },
//                            leadingIcon = { Icon(Icons.Rounded.People, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            )
                            val favCount = remember(allContacts) { allContacts.count { it.isFavorite } }
                            RillFilterChip(
                                label = stringResource(R.string.favorites) + " ($favCount)",
                                selected = selectedFilterTab == 1,
                                onClick = { selectedFilterTab = 1 },
//                            leadingIcon = { Icon(Icons.Outlined.Star, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            )
                            val privCount = remember(allContacts) { allContacts.count { it.isPrivate } }
                            if (privCount > 0) {
                                RillFilterChip(
                                    label = "Private ($privCount)",
                                    selected = selectedFilterTab == 2,
                                    onClick = { selectedFilterTab = 2 },
//                                leadingIcon = { Icon(ContactUtils.getAccountIcon(null, true), contentDescription = null, modifier = Modifier.size(16.dp)) }
                                )
                            }
                        }
                    }

                    val cleanQuery = searchQuery.trim()
                    val isPhoneQuery = cleanQuery.any { it.isDigit() } || cleanQuery.startsWith("+")

                    if (isPhoneQuery) {
                        item {
                            val isCustomSelected = selectedPhoneNumbers.contains(cleanQuery)
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 12.dp)
                                    .clip(MaterialTheme.shapes.large)
                                    .clickable {
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                        if (isMultiSelect) {
                                            selectedPhoneNumbers = if (isCustomSelected) {
                                                selectedPhoneNumbers - cleanQuery
                                            } else {
                                                selectedPhoneNumbers + cleanQuery
                                            }
                                        } else {
                                            resultNavigator.navigateBack(result = cleanQuery)
                                        }
                                    },
                                shape = MaterialTheme.shapes.large,
                                color = if (isCustomSelected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerLow
                                },
                                border = if (isCustomSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (isMultiSelect) {
                                        Checkbox(
                                            checked = isCustomSelected,
                                            onCheckedChange = { checked ->
                                                selectedPhoneNumbers = if (checked) {
                                                    selectedPhoneNumbers + cleanQuery
                                                } else {
                                                    selectedPhoneNumbers - cleanQuery
                                                }
                                            },
                                            modifier = Modifier.padding(end = 8.dp)
                                        )
                                    }
                                    Surface(
                                        modifier = Modifier.size(44.dp),
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.Outlined.Dialpad, contentDescription = null, modifier = Modifier.size(22.dp))
                                        }
                                    }
                                    Spacer(Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stringResource(R.string.use_number, cleanQuery),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
//                                    Spacer(Modifier.height(2.dp))
//                                    Text(
//                                        text = "Tap to select this custom number",
//                                        style = MaterialTheme.typography.bodySmall,
//                                        color = MaterialTheme.colorScheme.onSurfaceVariant
//                                    )
                                    }
                                    Icon(
                                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (filteredContacts.isEmpty() && !isPhoneQuery) {
                        item {
                            Box(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                                PlaceholderView(
                                    icon = Icons.Rounded.People,
                                    title = stringResource(R.string.no_contacts_found),
                                )
                            }
                        }
                    } else {
                        groupedContacts.forEach { (initial, contactsInGroup) ->
                            // ── Letter header ──────────────────────────────────────────
                            stickyHeader(key = "header_$initial", contentType = "letterHeader") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surface)
                                        .padding(horizontal = 28.dp, vertical = 4.dp)
                                        .padding(top = 8.dp)
                                ) {
                                    Text(
                                        text = if (initial == '❤') stringResource(R.string.favorites) else initial.toString(),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                }
                            }

                            item(key = "group_$initial") {
                                RillExpressiveCard(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                ) {
                                    contactsInGroup.forEachIndexed { index, contact ->
                                        val primaryNumber = getPhoneNumber(contact) ?: ""
                                        val isSelected = if (isMultiSelect) {
                                            selectedContactIds.contains(contact.id) || selectedPhoneNumbers.contains(primaryNumber)
                                        } else false

                                        RillScrollAnimatedItem(delayMs = (index * 25L).coerceAtMost(250L)) {
                                            RillListItem(
                                                paddingVertical = 8.dp,
                                                headline = getDisplayName(contact, displayOrder),
                                                supporting = if (contact.phoneNumbers.isNotEmpty()) {
                                                    if (contact.phoneNumbers.size > 1) {
                                                        stringResource(R.string.plus_more, formatPhoneNumber(primaryNumber), contact.phoneNumbers.size - 1)
                                                    } else {
                                                        formatPhoneNumber(primaryNumber)
                                                    }
                                                } else null,
                                                avatarName = contact.displayName,
                                                photoUri = contact.photoUri,
                                                onClick = {
                                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                    if (isMultiSelect) {
                                                        if (isSelected) {
                                                            selectedContactIds =
                                                                selectedContactIds - contact.id
                                                            selectedPhoneNumbers =
                                                                selectedPhoneNumbers - contact.phoneNumbers.toSet()
                                                        } else {
                                                            selectedContactIds =
                                                                selectedContactIds + contact.id
                                                            if (primaryNumber.isNotBlank()) {
                                                                selectedPhoneNumbers =
                                                                    selectedPhoneNumbers + primaryNumber
                                                            }
                                                        }
                                                    } else {
                                                        if (initialPhoneToAssign != null) {
                                                            navigator.navigate(
                                                                ContactEditScreenDestination(
                                                                    contactId = contact.id,
                                                                    initialPhone = initialPhoneToAssign
                                                                )
                                                            )
                                                        } else if (isExternalPick) {
                                                            val resultIntent = Intent().apply {
                                                                data = Uri.withAppendedPath(
                                                                    ContactsContract.Contacts.CONTENT_URI, contact.id)
                                                            }
                                                            val activity = context as? Activity
                                                            activity?.setResult(Activity.RESULT_OK, resultIntent)
                                                            activity?.finish()
                                                        } else if (returnContactId) {
                                                            resultNavigator.navigateBack(result = contact.id)
                                                        } else {
                                                            if (contact.phoneNumbers.size > 1) {
                                                                pendingMultiNumberContact = contact
                                                            } else if (primaryNumber.isNotBlank()) {
                                                                resultNavigator.navigateBack(result = primaryNumber)
                                                            }
                                                        }
                                                    }
                                                },
                                                trailingContent = {
                                                    if (isMultiSelect) {
                                                        Checkbox(
                                                            checked = isSelected,
                                                            onCheckedChange = { checked ->
                                                                if (checked) {
                                                                    selectedContactIds =
                                                                        selectedContactIds + contact.id
                                                                    if (primaryNumber.isNotBlank()) {
                                                                        selectedPhoneNumbers =
                                                                            selectedPhoneNumbers + primaryNumber
                                                                    }
                                                                } else {
                                                                    selectedContactIds =
                                                                        selectedContactIds - contact.id
                                                                    selectedPhoneNumbers =
                                                                        selectedPhoneNumbers - contact.phoneNumbers.toSet()
                                                                }
                                                            }
                                                        )
                                                    } else if (initialPhoneToAssign != null) {
                                                        Surface(
                                                            shape = CircleShape,
                                                            color = MaterialTheme.colorScheme.primaryContainer,
                                                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                                        ) {
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                modifier = Modifier.padding(
                                                                    horizontal = 8.dp,
                                                                    vertical = 4.dp
                                                                )
                                                            ) {
//                                                            Icon(
//                                                                imageVector = Icons.Outlined.PersonAdd,
//                                                                contentDescription = null,
//                                                                modifier = Modifier.size(13.dp)
//                                                            )
//                                                            Spacer(Modifier.width(4.dp))
                                                                Text(
                                                                    text = stringResource(R.string.add),
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    fontWeight = FontWeight.Bold
                                                                )
                                                            }
                                                        }
                                                    } else {
                                                        Icon(
                                                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                                alpha = 0.6f
                                                            ),
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    pendingMultiNumberContact?.let { contact ->
        NumberPickerDialog(
            numbers = contact.phoneNumbers,
            onDismissRequest = { pendingMultiNumberContact = null },
            onNumberSelected = { selectedNumber ->
                pendingMultiNumberContact = null
                resultNavigator.navigateBack(result = selectedNumber)
            }
        )
    }
}
