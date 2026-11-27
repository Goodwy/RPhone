package dev.goodwy.rphone.view.screen.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.view.Surface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Deselect
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.goodwy.rphone.R
import dev.goodwy.rphone.cardCornerExtraSmall
import dev.goodwy.rphone.controller.util.AddManualNumberStatus
import dev.goodwy.rphone.controller.util.BusyModeExceptionsHelper
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.controller.util.forceLtr
import dev.goodwy.rphone.controller.util.toast
import dev.goodwy.rphone.modal.data.Contact
import dev.goodwy.rphone.modal.data.getDisplayName
import dev.goodwy.rphone.modal.`interface`.IContactsRepository
import dev.goodwy.rphone.view.components.NavigationIcon
import dev.goodwy.rphone.view.components.PlaceholderView
import dev.goodwy.rphone.view.components.RillAnimatedSection
import dev.goodwy.rphone.view.components.RillAvatar
import dev.goodwy.rphone.view.components.RillDialog
import dev.goodwy.rphone.view.components.RillExpressiveCard
import dev.goodwy.rphone.view.components.RillListItem
import dev.goodwy.rphone.view.components.Title
import dev.goodwy.rphone.view.theme.MyColors.cardColor
import dev.goodwy.rphone.view.theme.RillShapeDefaults
import dev.goodwy.rphone.view.theme.customColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun BusyModeExceptionsScreen(navigator: DestinationsNavigator) {
    val context = LocalContext.current
    val prefs = koinInject<PreferenceManager>()
    val contactsRepo = koinInject<IContactsRepository>()

    val settingsState by prefs.settingsChanged.collectAsState()
    var exceptionContactIds by remember(settingsState) {
        mutableStateOf(prefs.getBusyModeExceptionContactIds())
    }
    var exceptionNumbers by remember(settingsState) {
        mutableStateOf(prefs.getBusyModeExceptionNumbers())
    }
    val displayOrder = remember(settingsState) {
        prefs.getInt(PreferenceManager.KEY_CONTACT_DISPLAY_ORDER, 0)
    }

    var contacts by remember { mutableStateOf<List<Contact>>(emptyList()) }
    var isLoadingContacts by remember { mutableStateOf(true) }
    var reloadTrigger by remember { mutableStateOf(0) }

    var showContactPicker by remember { mutableStateOf(false) }
    var showAddNumberDialog by remember { mutableStateOf(false) }
    var manualNumberInput by remember { mutableStateOf("") }
    var manualNumberError by remember { mutableStateOf<String?>(null) }

    val permissionDeniedMessage = stringResource(R.string.busy_mode_exceptions_contacts_permission_required)
    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            reloadTrigger += 1
            showContactPicker = true
        } else {
            context.toast(permissionDeniedMessage)
        }
    }

    LaunchedEffect(reloadTrigger) {
        isLoadingContacts = true
        val hasContactsPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

        val loadedResult = withContext(Dispatchers.IO) {
            if (hasContactsPermission) {
                runCatching { contactsRepo.getContacts(includePrivate = true, includeHidden = true) }
            } else {
                null
            }
        }

        if (loadedResult != null && loadedResult.isSuccess) {
            val loadedContacts = loadedResult.getOrDefault(emptyList())
            contacts = loadedContacts

            val existingIds = loadedContacts.mapTo(HashSet()) { it.id }
            val currentSaved = prefs.getBusyModeExceptionContactIds()
            val pruned = BusyModeExceptionsHelper.pruneStaleContactIds(currentSaved, existingIds)
            if (pruned != currentSaved) {
                exceptionContactIds = pruned
                prefs.setBusyModeExceptionContactIds(pruned)
            }
        }
        isLoadingContacts = false
    }

    val contactsById = remember(contacts) { contacts.associateBy { it.id } }
    val resolvedExceptionContacts = remember(exceptionContactIds, contactsById, displayOrder) {
        exceptionContactIds
            .mapNotNull { id -> contactsById[id] }
            .sortedBy { getDisplayName(it, displayOrder).lowercase() }
    }
    val unresolvedExceptionContactIds = remember(exceptionContactIds, contactsById, isLoadingContacts) {
        if (isLoadingContacts) emptyList() else exceptionContactIds.filter { it !in contactsById }
    }

    var visible by remember { mutableStateOf(false) }
    val screenAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(350),
        label = "busyExceptionsAlpha"
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
                title = { Title(stringResource(R.string.busy_mode_exceptions)) },
                navigationIcon = {
                    NavigationIcon(onClick = { navigator.navigateUp() })
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = padding.calculateTopPadding(),
                    start = 0.dp,
                    end = 0.dp,
                    bottom = 0.dp
                )
                .alpha(screenAlpha),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                RillAnimatedSection(delayMs = 30L) {
                    RillExpressiveCard {
                        RillListItem(
                            headline = stringResource(R.string.busy_mode_exceptions_select_contacts),
                            supporting = stringResource(R.string.busy_mode_exceptions_select_contacts_subtitle),
                            leadingIcon = Icons.Rounded.PersonAdd,
                            iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkGreen,
                            iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorGreen,
                            onClick = {
                                val granted = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.READ_CONTACTS
                                ) == PackageManager.PERMISSION_GRANTED
                                if (granted) {
                                    showContactPicker = true
                                } else {
                                    contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                                }
                            }
                        )
                        RillListItem(
                            headline = stringResource(R.string.busy_mode_exceptions_add_number),
                            supporting = stringResource(R.string.busy_mode_exceptions_add_number_subtitle),
                            leadingIcon = Icons.Outlined.Add,
                            iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkBlue,
                            iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorBlue,
                            onClick = {
                                manualNumberInput = ""
                                manualNumberError = null
                                showAddNumberDialog = true
                            }
                        )
                    }
                }
            }

            if (resolvedExceptionContacts.isNotEmpty() || unresolvedExceptionContactIds.isNotEmpty()) {
                item {
                    RillAnimatedSection(delayMs = 60L) {
                        Column {
                            val totalContacts = resolvedExceptionContacts.size + unresolvedExceptionContactIds.size
                            SettingsSectionLabel(
                                "${stringResource(R.string.busy_mode_exceptions_saved_contacts_section)} ($totalContacts)"
                            )
                            RillExpressiveCard {
                                resolvedExceptionContacts.forEach { contact ->
                                    val displayName = getDisplayName(contact, displayOrder)
                                        .ifBlank { stringResource(R.string.label_unknown) }
                                    val numbersSummary = contact.phoneNumbers
                                        .joinToString(", ")
                                        .takeIf { it.isNotBlank() }
                                        ?.forceLtr()
                                    RillListItem(
                                        headline = displayName,
                                        supporting = numbersSummary,
                                        avatarName = displayName,
                                        photoUri = contact.photoUri,
                                        onClick = {},
                                        trailingContent = {
                                            IconButton(
                                                onClick = {
                                                    val updated = exceptionContactIds.filterNot { it == contact.id }
                                                    exceptionContactIds = updated
                                                    prefs.setBusyModeExceptionContactIds(updated)
                                                }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Close,
                                                    contentDescription = stringResource(R.string.busy_mode_exceptions_remove_action),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    )
                                }
                                unresolvedExceptionContactIds.forEach { contactId ->
                                    RillListItem(
                                        headline = stringResource(R.string.label_unknown),
                                        supporting = contactId,
                                        leadingIcon = Icons.Rounded.PersonAdd,
                                        iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkGreen,
                                        iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorGreen,
                                        onClick = {},
                                        trailingContent = {
                                            IconButton(
                                                onClick = {
                                                    val updated = exceptionContactIds.filterNot { it == contactId }
                                                    exceptionContactIds = updated
                                                    prefs.setBusyModeExceptionContactIds(updated)
                                                }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Close,
                                                    contentDescription = stringResource(R.string.busy_mode_exceptions_remove_action),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
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

            if (exceptionNumbers.isNotEmpty()) {
                item {
                    RillAnimatedSection(delayMs = 90L) {
                        Column {
                            SettingsSectionLabel(
                                "${stringResource(R.string.busy_mode_exceptions_manual_numbers_section)} (${exceptionNumbers.size})"
                            )
                            RillExpressiveCard {
                                exceptionNumbers.forEach { number ->
                                    RillListItem(
                                        headline = number.forceLtr(),
                                        leadingIcon = Icons.Rounded.Phone,
                                        iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkBlue,
                                        iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorBlue,
                                        onClick = {},
                                        trailingContent = {
                                            IconButton(
                                                onClick = {
                                                    val updated = BusyModeExceptionsHelper.removeManualNumber(
                                                        exceptionNumbers,
                                                        number
                                                    )
                                                    exceptionNumbers = updated
                                                    prefs.setBusyModeExceptionNumbers(updated)
                                                }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Close,
                                                    contentDescription = stringResource(R.string.busy_mode_exceptions_remove_action),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
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

            if (exceptionContactIds.isEmpty() && exceptionNumbers.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 24.dp)
                    ) {
                        PlaceholderView(
                            icon = Icons.Rounded.PhoneInTalk,
                            title = stringResource(R.string.busy_mode_exceptions_empty_title),
                            description = stringResource(R.string.busy_mode_exceptions_empty_description)
                        )
                    }
                }
            }

            item { SettingsBottomPadding() }
        }
    }

    if (showAddNumberDialog) {
        val invalidError = stringResource(R.string.busy_mode_exceptions_invalid_number)
        val duplicateError = stringResource(R.string.busy_mode_exceptions_duplicate_number)

        RillDialog(
            onDismissRequest = { showAddNumberDialog = false },
            title = stringResource(R.string.busy_mode_exceptions_add_number),
            icon = Icons.Rounded.Phone,
            iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkBlue,
            iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorBlue,
            confirmButton = {
                TextButton(
                    onClick = {
                        val result = BusyModeExceptionsHelper.addManualNumber(
                            existing = exceptionNumbers,
                            rawNumber = manualNumberInput
                        )
                        when (result.status) {
                            AddManualNumberStatus.ADDED -> {
                                exceptionNumbers = result.numbers
                                prefs.setBusyModeExceptionNumbers(result.numbers)
                                showAddNumberDialog = false
                            }
                            AddManualNumberStatus.INVALID -> {
                                manualNumberError = invalidError
                            }
                            AddManualNumberStatus.DUPLICATE -> {
                                manualNumberError = duplicateError
                            }
                        }
                    },
                    enabled = manualNumberInput.isNotBlank()
                ) {
                    Text(
                        text = stringResource(R.string.add),
                        textAlign = TextAlign.End
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddNumberDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        ) {
            OutlinedTextField(
                value = manualNumberInput,
                onValueChange = {
                    manualNumberInput = it
                    manualNumberError = null
                },
                label = { Text(stringResource(R.string.phone_number)) },
                isError = manualNumberError != null,
                supportingText = manualNumberError?.let { err -> { Text(err) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                singleLine = true
            )
        }
    }

    if (showContactPicker) {
        BusyModeContactPickerSheet(
            contacts = contacts,
            contactsLoaded = !isLoadingContacts,
            initialSelectedIds = exceptionContactIds.toSet(),
            displayOrder = displayOrder,
            prefs = prefs,
            onDismiss = { showContactPicker = false },
            onDone = { selectedIds ->
                val updated = selectedIds.toList()
                exceptionContactIds = updated
                prefs.setBusyModeExceptionContactIds(updated)
                showContactPicker = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BusyModeContactPickerSheet(
    contacts: List<Contact>,
    contactsLoaded: Boolean,
    initialSelectedIds: Set<String>,
    displayOrder: Int,
    prefs: PreferenceManager,
    onDismiss: () -> Unit,
    onDone: (Set<String>) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedIds by remember(initialSelectedIds) { mutableStateOf(initialSelectedIds) }

    val cardCorner = remember {
        prefs.getInt(PreferenceManager.KEY_CARD_ROUNDNESS, RillShapeDefaults.DefaultRoundness)
    }

    val selectableContacts = remember(contacts, displayOrder) {
        contacts
            .filter { it.phoneNumbers.isNotEmpty() }
            .sortedBy { getDisplayName(it, displayOrder).lowercase() }
    }

    val filteredContacts = remember(selectableContacts, searchQuery, displayOrder) {
        if (searchQuery.isBlank()) {
            selectableContacts
        } else {
            val normalizedQuery = searchQuery.replace(" ", "").replace("-", "")
            selectableContacts.filter { contact ->
                getDisplayName(contact, displayOrder).contains(searchQuery, ignoreCase = true) ||
                    contact.nickname.contains(searchQuery, ignoreCase = true) ||
                    contact.company.contains(searchQuery, ignoreCase = true) ||
                    contact.phoneNumbers.any { number ->
                        number.replace(" ", "").replace("-", "").contains(normalizedQuery)
                    }
            }
        }
    }

    fun toggleContact(contactId: String) {
        selectedIds = if (contactId in selectedIds) {
            selectedIds - contactId
        } else {
            selectedIds + contactId
        }
    }

    val selectedCount = remember(selectableContacts, selectedIds) {
        selectableContacts.count { it.id in selectedIds }
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = MaterialTheme.shapes.extraLarge.copy(
            bottomStart = CornerSize(0.dp),
            bottomEnd = CornerSize(0.dp)
        ),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 4.dp,
        scrimColor = Color.Transparent,
        contentWindowInsets = {
            if (isLandscape) {
                WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
            } else {
                BottomSheetDefaults.windowInsets
            }
        },
        dragHandle = null,
        modifier = Modifier.statusBarsPadding()
    ) {
        Scaffold(
            topBar = {
                Column {
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
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                        title = {
                            Text(
                                text = stringResource(R.string.select_contacts),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        },
                        actions = {
                            IconButton(onClick = { selectedIds = selectableContacts.mapTo(HashSet()) { it.id } }) {
                                Icon(Icons.Rounded.SelectAll, stringResource(R.string.select_all))
                            }
                            IconButton(onClick = { selectedIds = emptySet() }) {
                                Icon(Icons.Rounded.Deselect, stringResource(R.string.deselect_all))
                            }
                            Spacer(modifier = Modifier.size(6.dp))
                        }
                    )
                    val searchShape = if (cardCorner > 12) CircleShape else MaterialTheme.shapes.extraExtraLarge
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text(stringResource(R.string.search_contacts)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 12.dp)
                            )
                        },
                        trailingIcon = {
                            AnimatedVisibility(
                                visible = searchQuery.isNotEmpty(),
                                enter = fadeIn() + scaleIn(),
                                exit = fadeOut() + scaleOut()
                            ) {
                                IconButton(
                                    onClick = { searchQuery = "" },
                                    modifier = Modifier.padding(end = 4.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.clear))
                                }
                            }
                        },
                        singleLine = true,
                        shape = searchShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = cardColor,
                            unfocusedContainerColor = cardColor,
                            focusedBorderColor = cardColor,
                            unfocusedBorderColor = cardColor
                        )
                    )
                }
            },
            containerColor = Color.Transparent
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        top = padding.calculateTopPadding(),
                        start = 0.dp,
                        end = 0.dp,
                        bottom = 0.dp
                    )
            ) {
                if (!contactsLoaded) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (filteredContacts.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SearchOff,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Text(
                                text = stringResource(R.string.no_contacts_found),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                } else {
                    Surface(
                        color = Color.Transparent,
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .padding(top = 8.dp)
                            .fillMaxSize(),
                        shape = MaterialTheme.shapes.extraLarge
                    ) {
                        LazyColumn(
                            contentPadding = PaddingValues(bottom = 96.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            items(filteredContacts, key = { it.id }) { contact ->
                                val checked = contact.id in selectedIds
                                val displayName = getDisplayName(contact, displayOrder)
                                    .ifBlank { stringResource(R.string.label_unknown) }
                                val rowBg by animateColorAsState(
                                    targetValue = if (checked) MaterialTheme.colorScheme.primaryContainer else cardColor,
                                    animationSpec = tween(180),
                                    label = "busyCb${contact.id}"
                                )
                                val interactionSource = remember { MutableInteractionSource() }
                                val isPressed by interactionSource.collectIsPressedAsState()
                                val cornerRadius by animateDpAsState(
                                    targetValue = if (checked || isPressed) 50.dp else cardCornerExtraSmall,
                                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                    label = "busyCr${contact.id}"
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(rowBg, shape = RoundedCornerShape(cornerRadius))
                                        .clickable(
                                            indication = null,
                                            interactionSource = interactionSource,
                                            onClick = { toggleContact(contact.id) }
                                        )
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    RillAvatar(
                                        name = displayName,
                                        photoUri = contact.photoUri,
                                        modifier = Modifier.size(46.dp)
                                    )
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = displayName,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Normal,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = contact.phoneNumbers.joinToString(", ").forceLtr(),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Checkbox(
                                        checked = checked,
                                        onCheckedChange = { toggleContact(contact.id) }
                                    )
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = { onDone(selectedIds) },
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 28.dp)
                        .height(52.dp),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 0.dp,
                        pressedElevation = 0.dp,
                        hoveredElevation = 0.dp
                    )
                ) {
                    Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.done) + "  ·  " + stringResource(R.string.selected_items, selectedCount),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
