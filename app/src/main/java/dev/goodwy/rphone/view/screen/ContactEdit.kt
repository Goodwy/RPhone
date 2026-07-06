package dev.goodwy.rphone.view.screen

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.BaseTypes
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Event
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import android.view.Surface
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.PostAdd
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.SwitchAccount
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.ContactsViewModel
import dev.goodwy.rphone.controller.util.ContactUtils
import dev.goodwy.rphone.controller.util.getAddressTypeText
import dev.goodwy.rphone.controller.util.getEmailTypeText
import dev.goodwy.rphone.controller.util.getEventTypeText
import dev.goodwy.rphone.controller.util.getPhoneTypeText
import dev.goodwy.rphone.controller.util.millisToString
import dev.goodwy.rphone.controller.util.stringToMillis
import dev.goodwy.rphone.modal.data.Contact
import dev.goodwy.rphone.modal.data.ContactAddress
import dev.goodwy.rphone.modal.data.ContactEmail
import dev.goodwy.rphone.modal.data.ContactEvent
import dev.goodwy.rphone.modal.data.ContactPhoneDetail
import dev.goodwy.rphone.modal.data.getDisplayName
import dev.goodwy.rphone.view.components.MoveToAccountDialog
import dev.goodwy.rphone.view.components.NavigationIcon
import dev.goodwy.rphone.view.components.RillAvatar
import dev.goodwy.rphone.view.components.ScrollToTopButton
import dev.goodwy.rphone.view.theme.customColors
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinActivityViewModel

private val blankPhoneDetail = ContactPhoneDetail(Phone.TYPE_MOBILE, null, "")
private val blankEvent = ContactEvent(Event.TYPE_BIRTHDAY, null, "")
private val blankAddress = ContactAddress(StructuredPostal.TYPE_HOME, null, "")
private val blankEmail = ContactEmail(Email.TYPE_HOME, null, "")
private val paddingHorizontal = 48.dp

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun ContactEditScreen(
    contactId: String? = null,
    initialName: String? = null,
    initialPhone: String? = null,
    navigator: DestinationsNavigator
) {
    val context = LocalContext.current
    val contactsVM: ContactsViewModel = koinActivityViewModel()
    val allContacts by contactsVM.allContacts.collectAsState()
    val availableAccounts by contactsVM.availableAccounts.collectAsState()
    val availableAccountsForMoving by contactsVM.availableAccountsForMoving.collectAsState()

    val listState = rememberLazyListState()
    val showButton by remember { derivedStateOf { listState.firstVisibleItemIndex > 2 } }
    
    val existingContact = remember(contactId, allContacts) {
        if (contactId != null && contactId != "0" && contactId != "null") {
            allContacts.find { it.id == contactId }
        } else null
    }

    var namePrefix by remember(existingContact) { mutableStateOf(existingContact?.namePrefix ?: "") }
    var givenName by remember(existingContact) { mutableStateOf(existingContact?.givenName ?: initialName ?: "") }
    var middleName by remember(existingContact) { mutableStateOf(existingContact?.middleName ?: "") }
    var familyName by remember(existingContact) { mutableStateOf(existingContact?.familyName ?: "") }
    var nameSuffix by remember(existingContact) { mutableStateOf(existingContact?.nameSuffix ?: "") }
    var company by remember(existingContact) { mutableStateOf(existingContact?.company ?: "") }
    var jobTitle by remember(existingContact) { mutableStateOf(existingContact?.jobTitle ?: "") }
    var photoUri by remember(existingContact) { mutableStateOf<String?>(existingContact?.photoUri) }
    var isFavorite by remember(existingContact) { mutableStateOf<Boolean>(existingContact?.isFavorite ?: false) }

    var selectedAccount by remember(existingContact, availableAccounts) {
        val lastUsed = contactsVM.getLastUsedAccount()
        mutableStateOf(
            if (contactId == null && lastUsed != null) {
                availableAccounts.find {
                    it.name == lastUsed.name && it.type == lastUsed.type
                }
            } else {
                availableAccounts.find {
                    it.name == existingContact?.accountName && it.type == existingContact.accountType
                }
            }
        )
    }

    val phoneNumbers = remember(existingContact) {
        mutableStateListOf<String>().apply { 
            if (existingContact != null && existingContact.phoneNumbers.isNotEmpty()) {
                addAll(existingContact.phoneNumbers)
            } else if (!initialPhone.isNullOrBlank()) {
                add(initialPhone)
            }
            if (isEmpty()) add("") 
        } 
    }

    val phoneDetails = remember(existingContact) {
        mutableStateListOf<ContactPhoneDetail>().apply {
            if (existingContact != null && existingContact.phoneDetails.isNotEmpty()) {
                addAll(existingContact.phoneDetails)
            } else if (!initialPhone.isNullOrBlank()) {
                add(ContactPhoneDetail(Phone.TYPE_MOBILE, null, initialPhone))
            }
            if (isEmpty()) add(blankPhoneDetail)
        }
    }
    
    val emails = remember(existingContact) { 
        mutableStateListOf<ContactEmail>().apply {
            if (existingContact != null && existingContact.emails.isNotEmpty()) {
                addAll(existingContact.emails)
            }
            if (isEmpty()) add(blankEmail)
        } 
    }

    val events = remember(existingContact) {
        mutableStateListOf<ContactEvent>().apply {
            if (existingContact != null && existingContact.events.isNotEmpty()) {
                addAll(existingContact.events)
            }
            if (isEmpty()) add(blankEvent)
        }
    }
    
    val addresses = remember(existingContact) { 
        mutableStateListOf<ContactAddress>().apply {
            if (existingContact != null && existingContact.addresses.isNotEmpty()) {
                addAll(existingContact.addresses)
            }
            if (isEmpty()) add(blankAddress)
        } 
    }

    val scope = rememberCoroutineScope()

    val currentContactForPreview = remember(
        namePrefix, givenName, middleName, familyName, nameSuffix, company
    ) {
        Contact(
            id = "",
            namePrefix = namePrefix,
            givenName = givenName,
            middleName = middleName,
            familyName = familyName,
            nameSuffix = nameSuffix,
            company = company,
            jobTitle = jobTitle,
            phoneNumbers = phoneNumbers.filter { it.isNotBlank() }
        )
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> if (uri != null) photoUri = uri.toString() }
    )
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // Track Changes
    fun hasChanges(): Boolean {
        val currentContact = Contact(
            id = contactId ?: "0",
            namePrefix = namePrefix,
            givenName = givenName,
            middleName = middleName,
            familyName = familyName,
            nameSuffix = nameSuffix,
            company = company,
            jobTitle = jobTitle,
            phoneNumbers = phoneNumbers.filter { it.isNotBlank() },
            phoneDetails = phoneDetails.filter { it.number.isNotBlank() },
            emails = emails.filter { it.value.isNotBlank() },
            addresses = addresses.filter { it.formattedAddress.isNotBlank() },
            events = events.filter { it.date.isNotBlank() },
            photoUri = photoUri,
            isFavorite = isFavorite,
//            customRingtone=null,
            accountName = selectedAccount?.name,
            accountType = selectedAccount?.type
        )

        val originalContact = existingContact ?: Contact(
            id = "0",
            namePrefix = "",
            givenName = initialName ?: "",
            middleName = "",
            familyName = "",
            nameSuffix = "",
            company = "",
            jobTitle = "",
            phoneNumbers = if (!initialPhone.isNullOrBlank()) listOf(initialPhone) else emptyList(),
            phoneDetails = if (!initialPhone.isNullOrBlank()) listOf(ContactPhoneDetail(Phone.TYPE_MOBILE, null, initialPhone)) else emptyList(),
            emails = emptyList(),
            addresses = emptyList(),
            events = emptyList(),
            photoUri = null,
            isFavorite = false
        )

//        context.copyToClipboard(currentContact.toString() +"\n" + originalContact.toString())

        return currentContact != originalContact
    }

    // Status for the exit confirmation dialog
    var showExitDialog by remember { mutableStateOf(false) }
    BackHandler(enabled = hasChanges()) {
        showExitDialog = true
    }

    fun exitWithoutSaving() {
        navigator.navigateUp()
    }

    fun saveAndExit() {
        val contactToSave = Contact(
            id = contactId ?: "0",
            namePrefix = namePrefix,
            givenName = givenName,
            middleName = middleName,
            familyName = familyName,
            nameSuffix = nameSuffix,
            company = company,
            jobTitle = jobTitle,
            phoneNumbers = phoneNumbers.filter { it.isNotBlank() },
            phoneDetails = phoneDetails.filter { it.number.isNotBlank() },
            emails = emails.filter { it.value.isNotBlank() },
            addresses = addresses.filter { it.formattedAddress.isNotBlank() },
            events = events.filter { it.date.isNotBlank() },
            photoUri = photoUri,
            isFavorite = isFavorite,
//            customRingtone=null,
            accountName = selectedAccount?.name,
            accountType = selectedAccount?.type
        )
        scope.launch {
            contactsVM.saveContact(contactToSave)
            navigator.navigateUp()
        }
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Unsaved Changes") },
            text = { Text("Do you want to save your changes before leaving?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExitDialog = false

                        scope.launch {
                            saveAndExit()
                        }
                    }
                ) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showExitDialog = false
                        exitWithoutSaving()
                    }
                ) {
                    Text("Discard")
                }
            },
            icon = {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        )
    }

    var showFieldsDialog by remember { mutableStateOf(false) }
    var showNamePrefix by remember { mutableStateOf(false) }
    var showMiddleName by remember { mutableStateOf(false) }
    var showNameSuffix by remember { mutableStateOf(false) }
    var showJobTitle by remember { mutableStateOf(false) }

    val hasNamePrefix = namePrefix.isNotBlank()
    val hasMiddleName = middleName.isNotBlank()
    val hasNameSuffix = nameSuffix.isNotBlank()
    val hasJobTitle = jobTitle.isNotBlank()

    LaunchedEffect(hasNamePrefix) { if (hasNamePrefix) showNamePrefix = true }
    LaunchedEffect(hasMiddleName) { if (hasMiddleName) showMiddleName = true }
    LaunchedEffect(hasNameSuffix) { if (hasNameSuffix) showNameSuffix = true }
    LaunchedEffect(hasJobTitle) { if (hasJobTitle) showJobTitle = true }

    if (showFieldsDialog) {
        val sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = false,
            confirmValueChange = { true }
        )

        ModalBottomSheet(
            onDismissRequest = { showFieldsDialog = false },
            sheetState = sheetState,
            dragHandle = null
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
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

                Text(
                    stringResource(R.string.add_field),
                    modifier = Modifier.padding(start = 24.dp, top = 8.dp),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(),
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp)
                ) {
                    item {
                        FieldOption(
                            title = stringResource(R.string.prefix),
                            description = stringResource(R.string.prefix_description),
                            icon = Icons.Rounded.Person,
                            isSelected = showNamePrefix,
                            onToggle = { showNamePrefix = !showNamePrefix }
                        )
                        FieldOption(
                            title = stringResource(R.string.middle_name),
                            description = stringResource(R.string.middle_name_description),
                            icon = Icons.Rounded.Person,
                            isSelected = showMiddleName,
                            onToggle = { showMiddleName = !showMiddleName }
                        )
                        FieldOption(
                            title = stringResource(R.string.suffix),
                            description = stringResource(R.string.suffix_description),
                            icon = Icons.Rounded.Person,
                            isSelected = showNameSuffix,
                            onToggle = { showNameSuffix = !showNameSuffix }
                        )
                        FieldOption(
                            title = stringResource(R.string.job_title),
                            description = stringResource(R.string.job_title_description),
                            icon = Icons.Rounded.Work,
                            isSelected = showJobTitle,
                            onToggle = { showJobTitle = !showJobTitle }
                        )
                    }
                }

//                Button(
//                    onClick = { showFieldsDialog = false },
//                    modifier = Modifier
//                        .fillMaxWidth()
//                        .padding(20.dp),
//                    shape = RoundedCornerShape(16.dp)
//                ) {
//                    Text("Done")
//                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    var showPicker by remember { mutableStateOf(false) }
    if (showPicker) {
        MoveToAccountDialog(
            title = stringResource(R.string.save_to_account),
            icon = Icons.Rounded.SwitchAccount,
            availableAccounts = availableAccountsForMoving,
            currentAccountKey =
                if (selectedAccount == null || (selectedAccount?.name == null && selectedAccount?.type == null)) null
                else "${selectedAccount!!.name}|${selectedAccount!!.type}",
            onDismiss = { showPicker = false },
            onAccountSelected = { account ->
                selectedAccount = account
                showPicker = false
            }
        )
    }

    val rotation =
        (context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager).defaultDisplay.rotation
    val isRotation90 = rotation == Surface.ROTATION_90
    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets.systemBars.only(
                    if (isRotation90) WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                    else WindowInsetsSides.Top
                ),
                title = { 
                    Text(
                        if (contactId == null || contactId == "0") stringResource(R.string.create_contact) else stringResource(R.string.edit_contact),
                        lineHeight = MaterialTheme.typography.titleMedium.lineHeight,
                        fontWeight = FontWeight.Normal,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    ) 
                },
                navigationIcon = {
                    NavigationIcon(onClick = {
                        if (hasChanges()) {
                            showExitDialog = true
                        } else {
                            navigator.navigateUp()
                        }
                    })
                },
                actions = {
                    Spacer(modifier = Modifier.size(8.dp))
                    if (existingContact != null) {
                        val interactionSource = remember { MutableInteractionSource() }
                        val isPressed by interactionSource.collectIsPressedAsState()
                        val cornerRadius by animateDpAsState(
                            if (isPressed) 8.dp else 40.dp,
                            spring(stiffness = Spring.StiffnessMediumLow),
                            label = "ButtonShapeAnimation"
                        )
                        IconButton(
                            onClick = {
                                isFavorite = !isFavorite
                            },
                            interactionSource = interactionSource,
                            modifier = Modifier.width(32.dp),
                            shape = RoundedCornerShape(cornerRadius),
                            colors = if (isFavorite) IconButtonDefaults.iconButtonColors(
                                contentColor = MaterialTheme.colorScheme.primary,
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            ) else IconButtonDefaults.iconButtonColors()
                        ) {
                            Icon(
                                if (isFavorite) Icons.Default.Star else Icons.Default.StarOutline,
                                stringResource(R.string.favorites)
                            )
                        }
                        Spacer(modifier = Modifier.size(8.dp))
                    }
                    Button(
                        onClick = {
                            scope.launch {
                                saveAndExit()
                            }
                        },
                        enabled = (namePrefix.isNotBlank() || givenName.isNotBlank() || middleName.isNotBlank() ||
                                familyName.isNotBlank() || nameSuffix.isNotBlank() || company.isNotBlank() ||
                                jobTitle.isNotBlank()) && phoneNumbers.any { it.isNotBlank() },
                        modifier = Modifier.then(if (contactId == null) Modifier.padding(end = 12.dp) else Modifier),
                        shape = RoundedCornerShape(24.dp),
                        elevation = ButtonDefaults.buttonElevation(0.dp)
                    ) {
//                        Icon(Icons.Default.Check, null)
//                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.save))
                    }
                    if (contactId != null) {
                        var showSelectionMenuOuter by remember { mutableStateOf(false) }
                        val headline = getDisplayName(currentContactForPreview)
                        // Delete confirmation dialog
                        if (showDeleteConfirm) {
                            AlertDialog(
                                onDismissRequest = { showDeleteConfirm = false },
                                icon = { Icon(Icons.Default.DeleteForever, null, tint = Color(0xFFF44336)) },
                                title = { Text("Delete Contact") },
                                text = {
                                    Text(
                                        "Are you sure you want to permanently delete \"$headline\"? This action cannot be undone.",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            showDeleteConfirm = false
                                            contactsVM.deleteContact(contactId)
                                            navigator.navigateUp()
                                        }
                                    ) {
                                        Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.cancel)) }
                                }
                            )
                        }
                        Box {
                            IconButton(onClick = { showSelectionMenuOuter = true }) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            DropdownMenu(
                                shape = RoundedCornerShape(16.dp),
                                expanded = showSelectionMenuOuter,
                                onDismissRequest = { showSelectionMenuOuter = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.open)) },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, stringResource(R.string.open)) },
                                    onClick = {
                                        showSelectionMenuOuter = false
                                        val intent = Intent(Intent.ACTION_EDIT).apply {
                                            data = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId.toLong())
                                        }
                                        context.startActivity(intent)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.delete)) },
                                    leadingIcon = {
                                        Icon(
                                            ImageVector.vectorResource(id = R.drawable.ic_delete),
                                            stringResource(R.string.delete),
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    onClick = {
                                        showSelectionMenuOuter = false
                                        showDeleteConfirm = true
                                    }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.size(4.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        }
    ) { innerPadding ->
        val configuration = LocalConfiguration.current
        val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        if (isLandscape) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.Top
            ) {
                // Left column - avatar
                Box(
                    modifier = Modifier.weight(1f)
                        .then(
                            if (isRotation90) Modifier.navigationBarsPadding()
                            else Modifier
                        )
                        .padding(
                            top = innerPadding.calculateTopPadding(),
                            start = 0.dp,
                            end = 0.dp,
                            bottom = 24.dp
                        )
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        RillAvatar(
                            name = getDisplayName(currentContactForPreview),
                            photoUri = photoUri,
                            modifier = Modifier.size(180.dp),
                            shape = CircleShape
                        )

                        if (photoUri != null) {
                            SmallFloatingActionButton(
                                onClick = { photoUri = null },
                                containerColor = MaterialTheme.colorScheme.customColors.colorRed,
                                contentColor = MaterialTheme.colorScheme.customColors.colorDarkRed,
                                shape = CircleShape,
                                modifier = Modifier
                                    .size(40.dp)
                                    .align(Alignment.BottomStart)
                                    .offset(x = (-16).dp, y = (-16).dp)
                            ) {
                                Icon(
                                    ImageVector.vectorResource(id = R.drawable.ic_delete),
                                    null,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        SmallFloatingActionButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            shape = CircleShape,
                            modifier = Modifier
                                .size(40.dp)
                                .align(Alignment.BottomEnd)
                                .offset(x = 16.dp, y = (-16).dp)
                        ) {
                            Icon(Icons.Default.AddAPhoto, null, modifier = Modifier.size(20.dp))
                        }
                    }
                }

                // Right column contains all other content
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1.7f)
                        .padding(
                            top = innerPadding.calculateTopPadding(),
                            start = 0.dp,
                            end = 0.dp,
                            bottom = 0.dp
                        )
                        .fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    item {
                        var appeared by remember { mutableStateOf(false) }
                        LaunchedEffect(Unit) { appeared = true }
                        val rowScale by animateFloatAsState(
                            targetValue = if (appeared) 1f else 0.5f,
                            animationSpec = spring(stiffness = Spring.StiffnessMedium),
                            label = "rowScale"
                        )
                        val rowAlpha by animateFloatAsState(
                            targetValue = if (appeared) 1f else 0f,
                            animationSpec = tween(150),
                            label = "rowAlpha"
                        )
                        Column(
                            modifier = Modifier
                                .animateContentSize(
                                    animationSpec = spring(
                                        stiffness = Spring.StiffnessMediumLow,
                                        dampingRatio = Spring.DampingRatioMediumBouncy
                                    )
                                )
                                .scale(rowScale)
                                .alpha(rowAlpha),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
//                    OutlinedTextField(
//                        value = name,
//                        onValueChange = { name = it },
//                        label = { Text("Full Name") },
//                        modifier = Modifier
//                            .fillMaxWidth()
//                            .padding(horizontal = paddingHorizontal),
//                        shape = RoundedCornerShape(12.dp),
//                        leadingIcon = { Icon(Icons.Default.Person, null) },
//                        colors = OutlinedTextFieldDefaults.colors(
//                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
//                            focusedBorderColor = MaterialTheme.colorScheme.primary
//                        )
//                    )
                            AnimatedVisibility(
                                visible = showNamePrefix,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    OutlinedTextField(
                                        value = namePrefix,
                                        onValueChange = { namePrefix = it },
                                        label = { Text(stringResource(R.string.prefix)) },
                                        modifier = Modifier.weight(1f)
                                            .fillMaxWidth()
                                            .padding(start = paddingHorizontal),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                            focusedBorderColor = MaterialTheme.colorScheme.primary
                                        )
                                    )
                                    IconButton(
                                        modifier = Modifier.padding(top = 8.dp),
                                        onClick = {
                                            namePrefix = ""
                                            showNamePrefix = false
                                        }
                                    ) {
                                        Icon(
                                            Icons.Rounded.RemoveCircleOutline,
                                            null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                            OutlinedTextField(
                                value = givenName,
                                onValueChange = { givenName = it },
                                label = { Text(stringResource(R.string.first_name)) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = paddingHorizontal),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                    focusedBorderColor = MaterialTheme.colorScheme.primary
                                )
                            )
                            AnimatedVisibility(
                                visible = showMiddleName,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    OutlinedTextField(
                                        value = middleName,
                                        onValueChange = { middleName = it },
                                        label = { Text(stringResource(R.string.middle_name)) },
                                        modifier = Modifier.weight(1f)
                                            .fillMaxWidth()
                                            .padding(start = paddingHorizontal),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                            focusedBorderColor = MaterialTheme.colorScheme.primary
                                        )
                                    )
                                    IconButton(
                                        modifier = Modifier.padding(top = 8.dp),
                                        onClick = {
                                            middleName = ""
                                            showMiddleName = false
                                        }
                                    ) {
                                        Icon(
                                            Icons.Rounded.RemoveCircleOutline,
                                            null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                            OutlinedTextField(
                                value = familyName,
                                onValueChange = { familyName = it },
                                label = { Text(stringResource(R.string.last_name)) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = paddingHorizontal),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                    focusedBorderColor = MaterialTheme.colorScheme.primary
                                )
                            )
                            AnimatedVisibility(
                                visible = showNameSuffix,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    OutlinedTextField(
                                        value = nameSuffix,
                                        onValueChange = { nameSuffix = it },
                                        label = { Text(stringResource(R.string.suffix)) },
                                        modifier = Modifier.weight(1f)
                                            .fillMaxWidth()
                                            .padding(start = paddingHorizontal),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                            focusedBorderColor = MaterialTheme.colorScheme.primary
                                        )
                                    )
                                    IconButton(
                                        modifier = Modifier.padding(top = 8.dp),
                                        onClick = {
                                            nameSuffix = ""
                                            showNameSuffix = false
                                        }
                                    ) {
                                        Icon(
                                            Icons.Rounded.RemoveCircleOutline,
                                            null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Organization
                    item {
                        var appeared by remember { mutableStateOf(false) }
                        LaunchedEffect(Unit) { appeared = true }
                        val rowScale by animateFloatAsState(
                            targetValue = if (appeared) 1f else 0.5f,
                            animationSpec = spring(stiffness = Spring.StiffnessMedium),
                            label = "rowScale"
                        )
                        val rowAlpha by animateFloatAsState(
                            targetValue = if (appeared) 1f else 0f,
                            animationSpec = tween(150),
                            label = "rowAlpha"
                        )
                        Column(
                            modifier = Modifier
                                .animateContentSize(
                                    animationSpec = spring(
                                        stiffness = Spring.StiffnessMediumLow,
                                        dampingRatio = Spring.DampingRatioMediumBouncy
                                    )
                                )
                                .scale(rowScale)
                                .alpha(rowAlpha),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = company,
                                onValueChange = { company = it },
                                label = { Text(stringResource(R.string.company)) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = paddingHorizontal),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                    focusedBorderColor = MaterialTheme.colorScheme.primary
                                )
                            )
                            AnimatedVisibility(
                                visible = showJobTitle,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    OutlinedTextField(
                                        value = jobTitle,
                                        onValueChange = { jobTitle = it },
                                        label = { Text(stringResource(R.string.job_title)) },
                                        modifier = Modifier.weight(1f)
                                            .fillMaxWidth()
                                            .padding(start = paddingHorizontal),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                            focusedBorderColor = MaterialTheme.colorScheme.primary
                                        )
                                    )
                                    IconButton(
                                        modifier = Modifier.padding(top = 8.dp),
                                        onClick = {
                                            jobTitle = ""
                                            showJobTitle = false
                                        }
                                    ) {
                                        Icon(
                                            Icons.Rounded.RemoveCircleOutline,
                                            null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }


                    item {
                        Column(
                            modifier = Modifier.animateContentSize(
                                animationSpec = spring(
                                    stiffness = Spring.StiffnessMediumLow,
                                    dampingRatio = Spring.DampingRatioMediumBouncy
                                )
                            ),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
//                    phoneNumbers.forEachIndexed { index, phone ->
//                        EditField(
//                            value = phone,
//                            onValueChange = { phoneNumbers[index] = it },
//                            label = "Phone",
//                            icon = Icons.Default.Phone,
//                            onDelete = if (phoneNumbers.size > 1) { { phoneNumbers.removeAt(index) } } else null
//                        )
//                    }
//                    TextButton(
//                        onClick = { phoneNumbers.add("") },
//                        modifier = Modifier.align(Alignment.Start)
//                    ) {
//                        Icon(Icons.Default.Add, null)
//                        Spacer(Modifier.width(8.dp))
//                        Text("Add Phone")
//                    }
                            phoneDetails.forEachIndexed { index, phoneDetail ->
//                        EditField(
//                            value = phoneDetail.number,
//                            onValueChange = {
//                                phoneDetails[index] = ContactPhoneDetail(phoneDetail.type, phoneDetail.label, it)
//                                phoneNumbers[index] = it
//                            },
//                            label = getPhoneTypeText(context, phoneDetail.type, phoneDetail.label),
//                            icon = Icons.Default.Phone,
//                            onDelete = if (phoneDetails.size > 1) {
//                                { phoneDetails.removeAt(index)
//                                    phoneNumbers.removeAt(index) }
//                            } else null
//                        )
                                EditPhoneField(
                                    value = phoneDetail.number,
                                    onValueChange = {
                                        phoneDetails[index] = ContactPhoneDetail(phoneDetail.type, phoneDetail.label, it)
                                        phoneNumbers[index] = it
                                    },
                                    label = getPhoneTypeText(context, phoneDetail.type, phoneDetail.label),
                                    onDelete = if (phoneDetails.size > 1) {
                                        {
                                            phoneDetails.removeAt(index)
                                            phoneNumbers.removeAt(index)
                                        }
                                    } else null,
                                    onLabelChange = { newLabel, newType ->
                                        val newTypeValue = newType ?: Phone.TYPE_CUSTOM
                                        phoneDetails[index] = ContactPhoneDetail(
                                            type = newTypeValue,
                                            label = if (newType == null) newLabel else null,
                                            number = phoneDetail.number
                                        )
                                        phoneNumbers[index] = phoneDetail.number
                                    }
                                )
                            }
                            TextButton(
                                onClick = {
                                    phoneDetails.add(blankPhoneDetail)
                                    phoneNumbers.add("")
                                },
                                modifier = Modifier
                                    .align(Alignment.Start)
                                    .padding(horizontal = paddingHorizontal)
                            ) {
                                Icon(Icons.Default.Add, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Add Phone")
                            }
                        }
                    }


                    item {
                        Column(
                            modifier = Modifier.animateContentSize(
                                animationSpec = spring(
                                    stiffness = Spring.StiffnessMediumLow,
                                    dampingRatio = Spring.DampingRatioMediumBouncy
                                )
                            ),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            emails.forEachIndexed { index, email ->
                                EditEmailField(
                                    value = email.value,
                                    onValueChange = { emails[index] = ContactEmail(email.type, email.label, it) },
                                    label = getEmailTypeText(context, email.type, email.label),
                                    onDelete = if (emails.size > 1) { { emails.removeAt(index) } } else null,
                                    onLabelChange = { newLabel, newType ->
                                        val newTypeValue = newType ?: Email.TYPE_CUSTOM
                                        emails[index] = ContactEmail(
                                            type = newTypeValue,
                                            label = if (newType == null) newLabel else null,
                                            value = email.value
                                        )
                                    }
                                )
                            }
                            TextButton(
                                onClick = { emails.add(blankEmail) },
                                modifier = Modifier
                                    .align(Alignment.Start)
                                    .padding(horizontal = paddingHorizontal)
                            ) {
                                Icon(Icons.Default.Add, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Add Email")
                            }
                        }
                    }


                    item {
                        Column(
                            modifier = Modifier.animateContentSize(
                                animationSpec = spring(
                                    stiffness = Spring.StiffnessMediumLow,
                                    dampingRatio = Spring.DampingRatioMediumBouncy
                                )
                            ),verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            events.forEachIndexed { index, event ->
//                        EditFieldDate(
//                            value = event.date,
//                            onValueChange = { events[index] = ContactEvent(event.type, event.label, it) },
//                            label = getEventTypeText(context, event.type, event.label),
//                            icon = Icons.Default.Event,
//                            onDelete = if (events.size > 1) { { events.removeAt(index) } } else null
//                        )
                                EditEventField(
                                    value = event.date,
                                    onValueChange = { events[index] = ContactEvent(event.type, event.label, it) },
                                    label = getEventTypeText(context, event.type, event.label),
                                    onDelete = if (events.size > 1) { { events.removeAt(index) } } else null,
                                    onLabelChange = { newLabel, newType ->
                                        val newTypeValue = newType ?: Event.TYPE_CUSTOM
                                        events[index] = ContactEvent(
                                            type = newTypeValue,
                                            label = if (newType == null) newLabel else null,
                                            date = event.date
                                        )
                                    }
                                )
                            }
                            TextButton(
                                onClick = { events.add(blankEvent) },
                                modifier = Modifier
                                    .align(Alignment.Start)
                                    .padding(horizontal = paddingHorizontal)
                            ) {
                                Icon(Icons.Default.Add, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Add Event")
                            }
                        }
                    }


                    item {
                        Column(
                            modifier = Modifier.animateContentSize(
                                animationSpec = spring(
                                    stiffness = Spring.StiffnessMediumLow,
                                    dampingRatio = Spring.DampingRatioMediumBouncy
                                )
                            ),verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            addresses.forEachIndexed { index, address ->
//                        EditField(
//                            value = address.formattedAddress,
//                            onValueChange = { addresses[index] = ContactAddress(address.type, address.label, it) },
//                            label = getAddressTypeText(context, address.type, address.label),
//                            icon = Icons.Default.LocationOn,
//                            onDelete = if (addresses.size > 1) { { addresses.removeAt(index) } } else null
//                        )
                                EditAddressField(
                                    value = address.formattedAddress,
                                    onValueChange = { addresses[index] = ContactAddress(address.type, address.label, it) },
                                    label = getAddressTypeText(context, address.type, address.label),
                                    onDelete = if (addresses.size > 1) { { addresses.removeAt(index) } } else null,
                                    onLabelChange = { newLabel, newType ->
                                        val newTypeValue = newType ?: StructuredPostal.TYPE_CUSTOM
                                        addresses[index] = ContactAddress(
                                            type = newTypeValue,
                                            label = if (newType == null) newLabel else null,
                                            formattedAddress = address.formattedAddress
                                        )
                                    }
                                )
                            }
                            TextButton(
                                onClick = { addresses.add(blankAddress) },
                                modifier = Modifier
                                    .align(Alignment.Start)
                                    .padding(horizontal = paddingHorizontal)
                            ) {
                                Icon(Icons.Default.Add, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Add Address")
                            }
                        }
                    }

                    item {
                        val interactionSource = remember { MutableInteractionSource() }
                        val isPressed by interactionSource.collectIsPressedAsState()

                        val cornerRadius by animateDpAsState(
                            targetValue = if (isPressed) 12.dp else 50.dp,
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            label = "ButtonShape"
                        )
                        Button(
                            onClick = { showFieldsDialog = true },
                            interactionSource = interactionSource,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = paddingHorizontal),
                            shape = RoundedCornerShape(cornerRadius),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            contentPadding = PaddingValues(16.dp)
                        ) {
                            Icon(Icons.Rounded.PostAdd, null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.add_field))
                        }
                    }

                    item {
                        Surface(
                            modifier = Modifier.padding(horizontal = paddingHorizontal),
                            onClick = { showPicker = true },
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = ContactUtils.getAccountIcon(selectedAccount),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.save_to_account),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = if (selectedAccount != null) ContactUtils.getAccountName(selectedAccount!!) else ContactUtils.getAccountType(null),
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Icon(Icons.Default.ArrowDropDown, null)
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(100.dp)) }
                }
            }
        } else {
            // Portrait
            LazyColumn(
                state = listState,
                modifier = Modifier
//                .padding(innerPadding)
                    .padding(
                        top = innerPadding.calculateTopPadding(),
                        start = 0.dp,
                        end = 0.dp,
                        bottom = 0.dp
                    )
                    .fillMaxSize(),
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(contentAlignment = Alignment.BottomEnd) {
                            RillAvatar(
                                name = getDisplayName(currentContactForPreview),
                                photoUri = photoUri,
                                modifier = Modifier.size(120.dp),
                                shape = CircleShape
                            )

                            if (photoUri != null) {
                                SmallFloatingActionButton(
                                    onClick = { photoUri = null },
                                    containerColor = MaterialTheme.colorScheme.customColors.colorRed,
                                    contentColor = MaterialTheme.colorScheme.customColors.colorDarkRed,
                                    shape = CircleShape,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .align(Alignment.BottomStart)
                                        .offset(x = (-16).dp, y = (-16).dp)
                                ) {
                                    Icon(
                                        ImageVector.vectorResource(id = R.drawable.ic_delete),
                                        null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            SmallFloatingActionButton(
                                onClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                shape = CircleShape,
                                modifier = Modifier
                                    .size(40.dp)
                                    .align(Alignment.BottomEnd)
                                    .offset(x = 16.dp, y = (-16).dp)
                            ) {
                                Icon(Icons.Default.AddAPhoto, null, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }


                item {
                    var appeared by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) { appeared = true }
                    val rowScale by animateFloatAsState(
                        targetValue = if (appeared) 1f else 0.5f,
                        animationSpec = spring(stiffness = Spring.StiffnessMedium),
                        label = "rowScale"
                    )
                    val rowAlpha by animateFloatAsState(
                        targetValue = if (appeared) 1f else 0f,
                        animationSpec = tween(150),
                        label = "rowAlpha"
                    )
                    Column(
                        modifier = Modifier
                            .animateContentSize(
                                animationSpec = spring(
                                    stiffness = Spring.StiffnessMediumLow,
                                    dampingRatio = Spring.DampingRatioMediumBouncy
                                )
                            )
                            .scale(rowScale)
                            .alpha(rowAlpha),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
//                    OutlinedTextField(
//                        value = name,
//                        onValueChange = { name = it },
//                        label = { Text("Full Name") },
//                        modifier = Modifier
//                            .fillMaxWidth()
//                            .padding(horizontal = paddingHorizontal),
//                        shape = RoundedCornerShape(12.dp),
//                        leadingIcon = { Icon(Icons.Default.Person, null) },
//                        colors = OutlinedTextFieldDefaults.colors(
//                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
//                            focusedBorderColor = MaterialTheme.colorScheme.primary
//                        )
//                    )
                        AnimatedVisibility(
                            visible = showNamePrefix,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = namePrefix,
                                    onValueChange = { namePrefix = it },
                                    label = { Text(stringResource(R.string.prefix)) },
                                    modifier = Modifier.weight(1f)
                                        .fillMaxWidth()
                                        .padding(start = paddingHorizontal),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                        focusedBorderColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                                IconButton(
                                    modifier = Modifier.padding(top = 8.dp),
                                    onClick = {
                                        namePrefix = ""
                                        showNamePrefix = false
                                    }
                                ) {
                                    Icon(
                                        Icons.Rounded.RemoveCircleOutline,
                                        null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                        OutlinedTextField(
                            value = givenName,
                            onValueChange = { givenName = it },
                            label = { Text(stringResource(R.string.first_name)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = paddingHorizontal),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                focusedBorderColor = MaterialTheme.colorScheme.primary
                            )
                        )
                        AnimatedVisibility(
                            visible = showMiddleName,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = middleName,
                                    onValueChange = { middleName = it },
                                    label = { Text(stringResource(R.string.middle_name)) },
                                    modifier = Modifier.weight(1f)
                                        .fillMaxWidth()
                                        .padding(start = paddingHorizontal),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                        focusedBorderColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                                IconButton(
                                    modifier = Modifier.padding(top = 8.dp),
                                    onClick = {
                                        middleName = ""
                                        showMiddleName = false
                                    }
                                ) {
                                    Icon(
                                        Icons.Rounded.RemoveCircleOutline,
                                        null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                        OutlinedTextField(
                            value = familyName,
                            onValueChange = { familyName = it },
                            label = { Text(stringResource(R.string.last_name)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = paddingHorizontal),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                focusedBorderColor = MaterialTheme.colorScheme.primary
                            )
                        )
                        AnimatedVisibility(
                            visible = showNameSuffix,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = nameSuffix,
                                    onValueChange = { nameSuffix = it },
                                    label = { Text(stringResource(R.string.suffix)) },
                                    modifier = Modifier.weight(1f)
                                        .fillMaxWidth()
                                        .padding(start = paddingHorizontal),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                        focusedBorderColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                                IconButton(
                                    modifier = Modifier.padding(top = 8.dp),
                                    onClick = {
                                        nameSuffix = ""
                                        showNameSuffix = false
                                    }
                                ) {
                                    Icon(
                                        Icons.Rounded.RemoveCircleOutline,
                                        null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }

                // Organization
                item {
                    var appeared by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) { appeared = true }
                    val rowScale by animateFloatAsState(
                        targetValue = if (appeared) 1f else 0.5f,
                        animationSpec = spring(stiffness = Spring.StiffnessMedium),
                        label = "rowScale"
                    )
                    val rowAlpha by animateFloatAsState(
                        targetValue = if (appeared) 1f else 0f,
                        animationSpec = tween(150),
                        label = "rowAlpha"
                    )
                    Column(
                        modifier = Modifier
                            .animateContentSize(
                                animationSpec = spring(
                                    stiffness = Spring.StiffnessMediumLow,
                                    dampingRatio = Spring.DampingRatioMediumBouncy
                                )
                            )
                            .scale(rowScale)
                            .alpha(rowAlpha),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = company,
                            onValueChange = { company = it },
                            label = { Text(stringResource(R.string.company)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = paddingHorizontal),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                focusedBorderColor = MaterialTheme.colorScheme.primary
                            )
                        )
                        AnimatedVisibility(
                            visible = showJobTitle,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = jobTitle,
                                    onValueChange = { jobTitle = it },
                                    label = { Text(stringResource(R.string.job_title)) },
                                    modifier = Modifier.weight(1f)
                                        .fillMaxWidth()
                                        .padding(start = paddingHorizontal),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                        focusedBorderColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                                IconButton(
                                    modifier = Modifier.padding(top = 8.dp),
                                    onClick = {
                                        jobTitle = ""
                                        showJobTitle = false
                                    }
                                ) {
                                    Icon(
                                        Icons.Rounded.RemoveCircleOutline,
                                        null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }


                item {
                    Column(
                        modifier = Modifier.animateContentSize(
                            animationSpec = spring(
                                stiffness = Spring.StiffnessMediumLow,
                                dampingRatio = Spring.DampingRatioMediumBouncy
                            )
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
//                    phoneNumbers.forEachIndexed { index, phone ->
//                        EditField(
//                            value = phone,
//                            onValueChange = { phoneNumbers[index] = it },
//                            label = "Phone",
//                            icon = Icons.Default.Phone,
//                            onDelete = if (phoneNumbers.size > 1) { { phoneNumbers.removeAt(index) } } else null
//                        )
//                    }
//                    TextButton(
//                        onClick = { phoneNumbers.add("") },
//                        modifier = Modifier.align(Alignment.Start)
//                    ) {
//                        Icon(Icons.Default.Add, null)
//                        Spacer(Modifier.width(8.dp))
//                        Text("Add Phone")
//                    }
                        phoneDetails.forEachIndexed { index, phoneDetail ->
//                        EditField(
//                            value = phoneDetail.number,
//                            onValueChange = {
//                                phoneDetails[index] = ContactPhoneDetail(phoneDetail.type, phoneDetail.label, it)
//                                phoneNumbers[index] = it
//                            },
//                            label = getPhoneTypeText(context, phoneDetail.type, phoneDetail.label),
//                            icon = Icons.Default.Phone,
//                            onDelete = if (phoneDetails.size > 1) {
//                                { phoneDetails.removeAt(index)
//                                    phoneNumbers.removeAt(index) }
//                            } else null
//                        )
                            EditPhoneField(
                                value = phoneDetail.number,
                                onValueChange = {
                                    phoneDetails[index] = ContactPhoneDetail(phoneDetail.type, phoneDetail.label, it)
                                    phoneNumbers[index] = it
                                },
                                label = getPhoneTypeText(context, phoneDetail.type, phoneDetail.label),
                                onDelete = if (phoneDetails.size > 1) {
                                    {
                                        phoneDetails.removeAt(index)
                                        phoneNumbers.removeAt(index)
                                    }
                                } else null,
                                onLabelChange = { newLabel, newType ->
                                    val newTypeValue = newType ?: Phone.TYPE_CUSTOM
                                    phoneDetails[index] = ContactPhoneDetail(
                                        type = newTypeValue,
                                        label = if (newType == null) newLabel else null,
                                        number = phoneDetail.number
                                    )
                                    phoneNumbers[index] = phoneDetail.number
                                }
                            )
                        }
                        TextButton(
                            onClick = {
                                phoneDetails.add(blankPhoneDetail)
                                phoneNumbers.add("")
                            },
                            modifier = Modifier
                                .align(Alignment.Start)
                                .padding(horizontal = paddingHorizontal)
                        ) {
                            Icon(Icons.Default.Add, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Add Phone")
                        }
                    }
                }


                item {
                    Column(
                        modifier = Modifier.animateContentSize(
                            animationSpec = spring(
                                stiffness = Spring.StiffnessMediumLow,
                                dampingRatio = Spring.DampingRatioMediumBouncy
                            )
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        emails.forEachIndexed { index, email ->
                            EditEmailField(
                                value = email.value,
                                onValueChange = { emails[index] = ContactEmail(email.type, email.label, it) },
                                label = getEmailTypeText(context, email.type, email.label),
                                onDelete = if (emails.size > 1) { { emails.removeAt(index) } } else null,
                                onLabelChange = { newLabel, newType ->
                                    val newTypeValue = newType ?: Email.TYPE_CUSTOM
                                    emails[index] = ContactEmail(
                                        type = newTypeValue,
                                        label = if (newType == null) newLabel else null,
                                        value = email.value
                                    )
                                }
                            )
                        }
                        TextButton(
                            onClick = { emails.add(blankEmail) },
                            modifier = Modifier
                                .align(Alignment.Start)
                                .padding(horizontal = paddingHorizontal)
                        ) {
                            Icon(Icons.Default.Add, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Add Email")
                        }
                    }
                }


                item {
                    Column(
                        modifier = Modifier.animateContentSize(
                            animationSpec = spring(
                                stiffness = Spring.StiffnessMediumLow,
                                dampingRatio = Spring.DampingRatioMediumBouncy
                            )
                        ),verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        events.forEachIndexed { index, event ->
//                        EditFieldDate(
//                            value = event.date,
//                            onValueChange = { events[index] = ContactEvent(event.type, event.label, it) },
//                            label = getEventTypeText(context, event.type, event.label),
//                            icon = Icons.Default.Event,
//                            onDelete = if (events.size > 1) { { events.removeAt(index) } } else null
//                        )
                            EditEventField(
                                value = event.date,
                                onValueChange = { events[index] = ContactEvent(event.type, event.label, it) },
                                label = getEventTypeText(context, event.type, event.label),
                                onDelete = if (events.size > 1) { { events.removeAt(index) } } else null,
                                onLabelChange = { newLabel, newType ->
                                    val newTypeValue = newType ?: Event.TYPE_CUSTOM
                                    events[index] = ContactEvent(
                                        type = newTypeValue,
                                        label = if (newType == null) newLabel else null,
                                        date = event.date
                                    )
                                }
                            )
                        }
                        TextButton(
                            onClick = { events.add(blankEvent) },
                            modifier = Modifier
                                .align(Alignment.Start)
                                .padding(horizontal = paddingHorizontal)
                        ) {
                            Icon(Icons.Default.Add, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Add Event")
                        }
                    }
                }


                item {
                    Column(
                        modifier = Modifier.animateContentSize(
                            animationSpec = spring(
                                stiffness = Spring.StiffnessMediumLow,
                                dampingRatio = Spring.DampingRatioMediumBouncy
                            )
                        ),verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        addresses.forEachIndexed { index, address ->
//                        EditField(
//                            value = address.formattedAddress,
//                            onValueChange = { addresses[index] = ContactAddress(address.type, address.label, it) },
//                            label = getAddressTypeText(context, address.type, address.label),
//                            icon = Icons.Default.LocationOn,
//                            onDelete = if (addresses.size > 1) { { addresses.removeAt(index) } } else null
//                        )
                            EditAddressField(
                                value = address.formattedAddress,
                                onValueChange = { addresses[index] = ContactAddress(address.type, address.label, it) },
                                label = getAddressTypeText(context, address.type, address.label),
                                onDelete = if (addresses.size > 1) { { addresses.removeAt(index) } } else null,
                                onLabelChange = { newLabel, newType ->
                                    val newTypeValue = newType ?: StructuredPostal.TYPE_CUSTOM
                                    addresses[index] = ContactAddress(
                                        type = newTypeValue,
                                        label = if (newType == null) newLabel else null,
                                        formattedAddress = address.formattedAddress
                                    )
                                }
                            )
                        }
                        TextButton(
                            onClick = { addresses.add(blankAddress) },
                            modifier = Modifier
                                .align(Alignment.Start)
                                .padding(horizontal = paddingHorizontal)
                        ) {
                            Icon(Icons.Default.Add, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Add Address")
                        }
                    }
                }

                item {
                    val interactionSource = remember { MutableInteractionSource() }
                    val isPressed by interactionSource.collectIsPressedAsState()

                    val cornerRadius by animateDpAsState(
                        targetValue = if (isPressed) 12.dp else 50.dp,
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        label = "ButtonShape"
                    )
                    Button(
                        onClick = { showFieldsDialog = true },
                        interactionSource = interactionSource,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = paddingHorizontal),
                        shape = RoundedCornerShape(cornerRadius),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        Icon(Icons.Rounded.PostAdd, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.add_field))
                    }
                }

                item {
                    Surface(
                        modifier = Modifier.padding(horizontal = paddingHorizontal),
                        onClick = { showPicker = true },
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = ContactUtils.getAccountIcon(selectedAccount),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.save_to_account),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = if (selectedAccount != null) ContactUtils.getAccountName(selectedAccount!!) else ContactUtils.getAccountType(null),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Icon(Icons.Default.ArrowDropDown, null)
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(100.dp)) }
            }
        }

        ScrollToTopButton(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(bottom = 24.dp, end = 24.dp),
            visible = showButton,
            onClick = { scope.launch { listState.animateScrollToItem(0) } }
        )
    }
}

@Composable
fun EditField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    onDelete: (() -> Unit)? = null,
    onLabelChange: ((String, Int?) -> Unit)? = null, // label, type
    availableLabels: List<Pair<Int, String>> = emptyList() // (type, displayName)
) {
    var appeared by remember { mutableStateOf(false) }
    var showLabelEditor by remember { mutableStateOf(false) }
    var currentLabel by remember(label) { mutableStateOf(label) }
    var customLabelText by remember { mutableStateOf("") }
    var showCustomLabelInput by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { appeared = true }

    val rowScale by animateFloatAsState(
        targetValue = if (appeared) 1f else 0.5f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "rowScale"
    )
    val rowAlpha by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(150),
        label = "rowAlpha"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .scale(rowScale)
            .alpha(rowAlpha)
            .animateContentSize()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Edit label button
            IconButton(
                onClick = { showLabelEditor = !showLabelEditor },
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Icon(
                    Icons.Default.ArrowDropDown,
                    stringResource(R.string.change_label),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(icon, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(currentLabel)
                    }
                },
                modifier = Modifier
                    .weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedBorderColor = MaterialTheme.colorScheme.primary
                )
            )

            if (onDelete != null) {
                IconButton(
                    modifier = Modifier.padding(top = 8.dp),
                    onClick = onDelete,
                ) {
                    Icon(
                        Icons.Rounded.RemoveCircleOutline,
                        null,
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            } else {
                Spacer(modifier = Modifier.size(paddingHorizontal))
            }
        }

        // Drop-down list for selecting a label
        if (showLabelEditor && availableLabels.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = paddingHorizontal, end = paddingHorizontal, top = 6.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
            ) {
                Column(
                    modifier = Modifier.animateContentSize()
                ) {
                    availableLabels.forEach { (type, labelText) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    currentLabel = labelText
                                    onLabelChange?.invoke(labelText, type)
                                    showLabelEditor = false
                                    showCustomLabelInput = false
                                }
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = labelText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (currentLabel == labelText)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurface
                            )
                            if (currentLabel == labelText) {
                                Icon(
                                    Icons.Default.Check,
                                    null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // Option for a custom label
                    if (showCustomLabelInput) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = customLabelText,
                                onValueChange = { customLabelText = it },
                                placeholder = { Text(stringResource(R.string.custom_label)) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )
                            IconButton(
                                onClick = {
                                    if (customLabelText.isNotBlank()) {
                                        currentLabel = customLabelText
                                        onLabelChange?.invoke(customLabelText, null) // null means custom type
                                        showLabelEditor = false
                                        showCustomLabelInput = false
                                        customLabelText = ""
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Check, stringResource(R.string.save))
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showCustomLabelInput = true
                                }
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.custom_label),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Icon(
                                Icons.Default.Add,
                                null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EditPhoneField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    onDelete: (() -> Unit)? = null,
    onLabelChange: ((String, Int?) -> Unit)? = null
) {
    val phoneTypes = listOf(
        Phone.TYPE_MOBILE to stringResource(R.string.mobile),
        Phone.TYPE_WORK to stringResource(R.string.work),
        Phone.TYPE_HOME to stringResource(R.string.home),
        Phone.TYPE_MAIN to stringResource(R.string.main_number),
//        Phone.TYPE_FAX_WORK to stringResource(R.string.work_fax),
//        Phone.TYPE_FAX_HOME to stringResource(R.string.home_fax),
//        Phone.TYPE_PAGER to stringResource(R.string.pager),
        Phone.TYPE_OTHER to stringResource(R.string.other)
    ).distinctBy { it.first }

    EditField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        icon = Icons.Rounded.Phone,
        onDelete = onDelete,
        onLabelChange = onLabelChange,
        availableLabels = phoneTypes
    )
}

@Composable
fun EditEmailField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    onDelete: (() -> Unit)? = null,
    onLabelChange: ((String, Int?) -> Unit)? = null
) {
    val emailTypes = listOf(
        Email.TYPE_HOME to stringResource(R.string.home),
        Email.TYPE_WORK to stringResource(R.string.work),
        Email.TYPE_MOBILE to stringResource(R.string.mobile),
        Email.TYPE_OTHER to stringResource(R.string.other)
    )

    EditField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        icon = Icons.Default.Email,
        onDelete = onDelete,
        onLabelChange = onLabelChange,
        availableLabels = emailTypes
    )
}

@Composable
fun EditAddressField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    onDelete: (() -> Unit)? = null,
    onLabelChange: ((String, Int?) -> Unit)? = null
) {
    val addressTypes = listOf(
        StructuredPostal.TYPE_HOME to stringResource(R.string.home),
        StructuredPostal.TYPE_WORK to stringResource(R.string.work),
        StructuredPostal.TYPE_OTHER to stringResource(R.string.other)
    )

    EditField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        icon = Icons.Default.LocationOn,
        onDelete = onDelete,
        onLabelChange = onLabelChange,
        availableLabels = addressTypes
    )
}

@Composable
fun EditFieldDate(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    onDelete: (() -> Unit)? = null,
    onLabelChange: ((String, Int?) -> Unit)? = null,
    availableLabels: List<Pair<Int, String>> = emptyList()
) {
    var appeared by remember { mutableStateOf(false) }
    var showLabelEditor by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var currentLabel by remember(label) { mutableStateOf(label) }
    var customLabelText by remember { mutableStateOf("") }
    var showCustomLabelInput by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { appeared = true }

    val rowScale by animateFloatAsState(
        targetValue = if (appeared) 1f else 0.5f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "rowScale"
    )
    val rowAlpha by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(150),
        label = "rowAlpha"
    )

    // Convert the YYYY-MM-DD string to milliseconds for the DatePicker
    val initialDateMillis = remember(value) { stringToMillis(value) }
    var selectedDateMillis by remember(initialDateMillis) { mutableStateOf(initialDateMillis) }

    // Update the external status in the YYYY-MM-DD format
    LaunchedEffect(selectedDateMillis) {
        val dateString = millisToString(selectedDateMillis, value)
        onValueChange(dateString)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .scale(rowScale)
            .alpha(rowAlpha)
            .animateContentSize()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Edit label button
            IconButton(
                onClick = { showLabelEditor = !showLabelEditor },
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Icon(
                    Icons.Default.ArrowDropDown,
                    "Change event type",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            OutlinedTextField(
//                leadingIcon = {
//                    Icon(
//                        icon,
//                        contentDescription = null,
//                        modifier = Modifier
//                            .padding(start = 8.dp)
//                            .clickable(
//                                indication = ripple(bounded = false, radius = 24.dp),
//                                interactionSource = null,
//                            ) { showDatePicker = true }
//                    )
//                },
                trailingIcon = {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = "Select a date",
                        modifier = Modifier.padding(end = 8.dp)
                    )
                },
                value = millisToString(selectedDateMillis, value),
                onValueChange = {},
                label = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(icon, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(currentLabel)
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { showDatePicker = true },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    // Active status
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    // We're overriding the disabled colors so the field looks like a regular one
                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                readOnly = true,
                enabled = false,
            )

            if (onDelete != null) {
                IconButton(
                    modifier = Modifier.padding(top = 8.dp),
                    onClick = onDelete,
                ) {
                    Icon(
                        Icons.Rounded.RemoveCircleOutline,
                        null,
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            } else {
                Spacer(modifier = Modifier.size(paddingHorizontal))
            }
        }

        // Drop-down list for selecting a label
        if (showLabelEditor && availableLabels.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = paddingHorizontal, end = paddingHorizontal, top = 6.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
            ) {
                Column(
                    modifier = Modifier.animateContentSize()
                ) {
                    availableLabels.forEach { (type, labelText) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    currentLabel = labelText
                                    onLabelChange?.invoke(labelText, type)
                                    showLabelEditor = false
                                    showCustomLabelInput = false
                                }
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = labelText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (currentLabel == labelText)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurface
                            )
                            if (currentLabel == labelText) {
                                Icon(
                                    Icons.Default.Check,
                                    null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // Option for a custom label
                    if (showCustomLabelInput) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = customLabelText,
                                onValueChange = { customLabelText = it },
                                placeholder = { Text(stringResource(R.string.custom_label)) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )
                            IconButton(
                                onClick = {
                                    if (customLabelText.isNotBlank()) {
                                        currentLabel = customLabelText
                                        onLabelChange?.invoke(customLabelText, null)
                                        showLabelEditor = false
                                        showCustomLabelInput = false
                                        customLabelText = ""
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Check, stringResource(R.string.save))
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showCustomLabelInput = true
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.custom_label),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Icon(
                                Icons.Default.Add,
                                null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // Date Picker Dialog
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDateMillis ?: System.currentTimeMillis()
        )

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { dateMillis ->
                            selectedDateMillis = dateMillis
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
fun EditEventField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    onDelete: (() -> Unit)? = null,
    onLabelChange: ((String, Int?) -> Unit)? = null
) {
    val eventTypes = listOf(
        Event.TYPE_BIRTHDAY to stringResource(R.string.birthday),
        Event.TYPE_ANNIVERSARY to stringResource(R.string.anniversary),
        Event.TYPE_OTHER to stringResource(R.string.other),
    )

    EditFieldDate(
        value = value,
        onValueChange = onValueChange,
        label = label,
        icon = Icons.Default.Event,
        onDelete = onDelete,
        onLabelChange = onLabelChange,
        availableLabels = eventTypes
    )
}

@Composable
fun FieldOption(
    title: String,
    description: String,
    icon: ImageVector,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Checkbox(
            checked = isSelected,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(
                checkedColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}