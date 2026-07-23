package dev.goodwy.rphone.view.screen

import android.accounts.Account
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.media.RingtoneManager
import android.net.Uri
import android.provider.ContactsContract
import android.telecom.TelecomManager
import android.view.Surface
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.goodwy.rphone.controller.CallLogViewModel
import dev.goodwy.rphone.controller.ContactsViewModel
import dev.goodwy.rphone.controller.util.NoteManager
import dev.goodwy.rphone.controller.util.QrCodeUtils
import dev.goodwy.rphone.controller.util.makeCall
import dev.goodwy.rphone.controller.util.placeCallWithSimPreference
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.view.components.*
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.CallSplit
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.automirrored.rounded.Help
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Share
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.navigation.NavController
import dev.goodwy.rphone.R
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinActivityViewModel
import org.koin.compose.koinInject
import androidx.core.net.toUri
import dev.goodwy.rphone.cardCornerBig
import dev.goodwy.rphone.cardCornerSmall
import dev.goodwy.rphone.controller.util.ContactUtils
import dev.goodwy.rphone.controller.util.copyToClipboard
import dev.goodwy.rphone.controller.util.getAddressTypeText
import dev.goodwy.rphone.controller.util.getEmailTypeText
import dev.goodwy.rphone.controller.util.getEventTypeText
import dev.goodwy.rphone.controller.util.getPhoneTypeText
import dev.goodwy.rphone.controller.util.isPackageInstalled
import dev.goodwy.rphone.controller.util.normalizePhoneNumber
import dev.goodwy.rphone.controller.util.toast
import dev.goodwy.rphone.modal.data.Contact
import dev.goodwy.rphone.view.theme.MyColors.cardColor
import dev.goodwy.rphone.view.theme.customColors
import com.ramcosta.composedestinations.generated.destinations.CallLogFullScreenDestination
import com.ramcosta.composedestinations.generated.destinations.ContactEditScreenDestination
import dev.goodwy.rphone.cardCornerMedium
import dev.goodwy.rphone.cardSpacedBy
import dev.goodwy.rphone.controller.util.SocialUtils
import dev.goodwy.rphone.controller.util.SocialUtils.launchSendWhatsAppIntent
import dev.goodwy.rphone.controller.util.hasDualSim
import dev.goodwy.rphone.device_only
import dev.goodwy.rphone.modal.data.getDisplayName
import dev.goodwy.rphone.modal.repository.ContactsRepository
import dev.goodwy.rphone.private_only
import java.util.Calendar

@SuppressLint("ConfigurationScreenWidthHeight")
@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun ContactDetailsScreen(
    contactId: String? = null,
    phoneNumber: String? = null,
    navController: NavController,
    navigator: DestinationsNavigator
) {
    val context = LocalContext.current
    val contactsViewModel: ContactsViewModel = koinActivityViewModel()
    val callLogViewModel: CallLogViewModel = koinActivityViewModel()

//    val contacts by contactsViewModel.allContacts.collectAsState()
    val allLogs by callLogViewModel.allCallLogs.collectAsState()
    val availableAccounts = contactsViewModel.availableAccounts.collectAsState().value
    val availableAccountsForMoving = contactsViewModel.availableAccountsForMoving.collectAsState().value

//    val contact = remember(contactId, phoneNumber, contacts) {
//        if (contactId != null && contactId != "null") contacts.find { it.id == contactId }
//        else if (phoneNumber != null) contacts.find { c -> c.phoneNumbers.any { n -> n.replace(" ", "").contains(phoneNumber.replace(" ", "")) } }
//        else null
//    }
    var contact by remember { mutableStateOf<Contact?>(null) }
    var contactAccount by remember { mutableStateOf<Account?>(null) }
    var isFullLoading by remember { mutableStateOf(true) }

    // Entrance / exit animation
    var screenVisible by remember { mutableStateOf(false) }
    var isClosing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun navigateBack() {
        isClosing = true
        scope.launch {
            kotlinx.coroutines.delay(420)
            navigator.navigateUp()
        }
    }

    val noContactsFound = stringResource(R.string.no_contacts_found)
    LaunchedEffect(contactId, phoneNumber) {
        isFullLoading = true
        contact = if (contactId != null && contactId != "null") {
            contactsViewModel.getFullContactById(contactId)
        } else if (phoneNumber != null) {
            contactsViewModel.getFullContactByNumber(phoneNumber)
        } else null
        contactAccount = if (contact != null) availableAccounts.find {
            it.name == contact!!.accountName && it.type == contact!!.accountType
        } else null

        if (contact == null && contactId != null && contactId != "null") {
            if (phoneNumber != null) {
                isFullLoading = false
                return@LaunchedEffect
            } else {
                context.toast(noContactsFound)
                isFullLoading = false
                navigateBack()
                return@LaunchedEffect
            }
        }
        isFullLoading = false
    }

    val displayPhone = phoneNumber
        ?: contact?.phoneDetails?.firstOrNull { it.isPrimary }?.number
        ?: contact?.phoneNumbers?.firstOrNull()
        ?: "Unknown"

    val companyAndJob = when {
        contact == null -> ""
        contact!!.company.isNotBlank() && contact!!.jobTitle.isNotBlank() -> contact!!.jobTitle + " • " + contact!!.company
        contact!!.company.isNotBlank() -> contact!!.company
        contact!!.jobTitle.isNotBlank() -> contact!!.jobTitle
        else -> ""
    }

    var contactSources by remember { mutableStateOf<List<ContactsRepository.ContactSource>>(emptyList()) }
    LaunchedEffect(contact) {
        if (contact != null && !contact!!.isPrivate) {
            val rawContacts = contactsViewModel.getContactSources(contact!!.id)
            contactSources = rawContacts
        } else {
            contactSources = emptyList()
        }
    }

    val defaultPhone = remember(contact) { derivedStateOf { contact?.phoneDetails?.firstOrNull { it.isPrimary } } }.value
    val videoLauncher = rememberVideoLauncher()

    fun updateDefaultPhone(phoneNumber: String, isPrimary: Boolean) {
        contactsViewModel.setDefaultPhoneNumber(
            contactId = contact!!.id,
            phoneNumber = phoneNumber,
            isPrimary = isPrimary
        )
        contact = contact!!.copy(
            phoneDetails = contact!!.phoneDetails.map { detail ->
                if (detail.number == phoneNumber) {
                    detail.copy(isPrimary = isPrimary)
                } else if (isPrimary) {
                    detail.copy(isPrimary = false)
                } else {
                    detail
                }
            }
        )
    }

    val telecomManager = remember { context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager }
    val prefs = koinInject<PreferenceManager>()
    val simPref = remember { prefs.getInt(PreferenceManager.KEY_DEFAULT_SIM, prefs.getDefaultSimIndexDefault()) }
    val displayOrder by remember {
        mutableIntStateOf(prefs.getInt(PreferenceManager.KEY_CONTACT_DISPLAY_ORDER, 0))
    }
    val displayName = contact?.let { getDisplayName(it, displayOrder) } ?: phoneNumber ?: "Unknown"

    var showSimPicker by remember { mutableStateOf(false) }
    var showNumberPicker by remember { mutableStateOf(false) }
    var showMessagePicker by remember { mutableStateOf(false) }
    var showEmailPicker by remember { mutableStateOf(false) }
    var showAddCallNotePicker by remember { mutableStateOf(false) }
    var pendingNumber by remember { mutableStateOf<String?>(null) }
    var showQrDialog by remember { mutableStateOf(false) }
    var showQrDialogPicker by remember { mutableStateOf(false) }
    var pendingQrNumber by remember { mutableStateOf<String?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showNoteEditor by remember { mutableStateOf(false) }
    var showMoveDialog by remember { mutableStateOf(false) }
    var showSharePicker by remember { mutableStateOf(false) }
    var showSourcesDialog by remember { mutableStateOf(false) }

    val contactLogs = remember(contact, phoneNumber, allLogs) {
        allLogs.filter { log ->
//            (contact != null && (log.contactId == contact!!.id ||
//                contact!!.phoneNumbers.any { n -> log.number.replace(" ", "").contains(n.replace(" ", "")) }))
//                    || (phoneNumber != null && log.number.replace(" ", "").contains(phoneNumber.replace(" ", "")))

            val normalizedLogNumber = log.number.replace(" ", "")
            val normalizedPhoneNumber = phoneNumber?.replace(" ", "")

            (contact != null && (log.contactId == contact!!.id ||
                contact!!.phoneNumbers.any { n -> normalizedLogNumber == n.replace(" ", "") }))
                    || (phoneNumber != null && normalizedLogNumber == normalizedPhoneNumber)
        }
    }

    val isFavorite = contact?.isFavorite ?: false
    val listState = rememberLazyListState()
    val showButton by remember { derivedStateOf { listState.firstVisibleItemIndex > 1 } }

    val screenAlpha by animateFloatAsState(
        targetValue = if (screenVisible && !isClosing) 1f else 0f,
        animationSpec = if (isClosing) tween(380, easing = androidx.compose.animation.core.FastOutLinearInEasing)
                        else tween(500, easing = androidx.compose.animation.core.LinearOutSlowInEasing),
        label = "screenAlpha"
    )
    val screenOffsetY by animateDpAsState(
        targetValue = if (screenVisible && !isClosing) 0.dp else if (isClosing) 80.dp else 56.dp,
        animationSpec = if (isClosing) tween(400, easing = androidx.compose.animation.core.FastOutLinearInEasing)
                        else spring(
                            stiffness = Spring.StiffnessLow,
                            dampingRatio = Spring.DampingRatioMediumBouncy
                        ),
        label = "screenOffsetY"
    )
    LaunchedEffect(Unit) { screenVisible = true }
    BackHandler { navigateBack() }

    val initiateCall = { number: String ->
        placeCallWithSimPreference(context, number, simPref) {
            pendingNumber = number; showSimPicker = true
        }
    }

    val initiateMessage = { number: String ->
        context.startActivity(
            Intent(
                Intent.ACTION_VIEW,
                "sms:$number".toUri()
            )
        )
    }

    val initiateEmail = { email: String ->
        context.startActivity(Intent(Intent.ACTION_SENDTO,
            "mailto:$email".toUri()))
    }

    val ringtonePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            if (contact != null) {
                contactsViewModel.setCustomRingtone(contact!!.id, uri?.toString())
                contact = contact!!.copy(customRingtone = uri?.toString())
            }
        }
    }

    val showSimLabel = hasDualSim(context)

    val openWhatsApp = { num: String -> SocialUtils.openWhatsApp(context, num) }
    val openTelegram = { num: String -> SocialUtils.openTelegram(context, num) }
    val openSignal = { num: String -> SocialUtils.openSignal(context, num) }

    val shareText = stringResource(R.string.share)
    val nameText = stringResource(R.string.name)
    val phoneNumberText = stringResource(R.string.phone_number)
    val shareContact = { number: String ->
        val name = if (displayName != number) "$nameText: $displayName\n" else ""
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "$name$phoneNumberText: $number")
        }
        context.startActivity(Intent.createChooser(intent, shareText))
    }

    if (showSharePicker && contact != null) {
        NumberPickerDialog(numbers = contact!!.phoneNumbers, onDismissRequest = { showSharePicker = false }, onNumberSelected = { showSharePicker = false; shareContact(it) })
    }
    if (showNumberPicker && contact != null) {
        NumberPickerDialog(numbers = contact!!.phoneNumbers, onDismissRequest = { showNumberPicker = false }, onNumberSelected = { showNumberPicker = false; initiateCall(it) })
    }
    if (showMessagePicker && contact != null) {
        NumberPickerDialog(numbers = contact!!.phoneNumbers, onDismissRequest = { showMessagePicker = false }, onNumberSelected = { showMessagePicker = false; initiateMessage(it) })
    }
    if (showEmailPicker && contact != null) {
        NumberPickerDialog(numbers = contact!!.emails.map {it.value}, onDismissRequest = { showEmailPicker = false }, onNumberSelected = { showEmailPicker = false; initiateEmail(it) })
    }
    if (showSimPicker && pendingNumber != null) {
        SimPickerDialog(onDismissRequest = { showSimPicker = false }, onSimSelected = { handle -> makeCall(context, pendingNumber!!, handle); showSimPicker = false })
    }
    if (showQrDialog) {
        QrCodeDialog(name = displayName, phone = pendingQrNumber ?: displayPhone, email = contact?.emails?.firstOrNull()?.value, onDismiss = { showQrDialog = false })
    }
    if (showQrDialogPicker) {
        NumberPickerDialog(
            numbers = contact!!.phoneNumbers,
            onDismissRequest = { showQrDialogPicker = false },
            onNumberSelected = { pendingQrNumber = it; showQrDialogPicker = false; showQrDialog = true }
        )
    }
    if (showDeleteDialog) {
        RillDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = stringResource(R.string.delete_contact),
            icon = ImageVector.vectorResource(id = R.drawable.ic_delete),
            iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkRed,
            iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorRed,
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    if (contactId != null) {
                        contactsViewModel.deleteContact(contactId)
                        navigator.navigateUp()
                    }
                }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        ) {
            Text(
                stringResource(R.string.delete_contact_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
    var editingNoteNumber by remember { mutableStateOf<String?>(null) }
    if (showNoteEditor) {
        NoteEditorDialog(
            contactName = displayName,
            phoneNumber = editingNoteNumber!!,
            onDismiss = {
                showNoteEditor = false
                editingNoteNumber = null
            }
        )
    }

    val initiateAddCallNote = { number: String ->
        if (number != "Unknown") {
            editingNoteNumber = number
            showNoteEditor = true
        }
    }

    if (showAddCallNotePicker && contact != null) {
        NumberPickerDialog(numbers = contact!!.phoneNumbers, onDismissRequest = { showAddCallNotePicker = false }, onNumberSelected = { showAddCallNotePicker = false; initiateAddCallNote(it) })
    }

    if (showSourcesDialog && contact != null) {
        SourcesDialog(
            contact = contact!!,
            sources = contactSources,
            navigator = navigator,
            navigateBack = { navigateBack() },
            onDismiss = { showSourcesDialog = false }
        )
    }

    // We track when the title should be displayed
    val showTitle by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex >= 1
        }
    }
    val titleAlpha by animateFloatAsState(
        targetValue = if (showTitle) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "titleAlpha"
    )

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
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer { alpha = titleAlpha }
                    ) {
                        Title(displayName, false)
                    }
                },
                navigationIcon = { NavigationIcon(onClick = { navigateBack() }) },
                actions = {
                    if (contact != null) {
//                        IconButton(onClick = {
//                            if (contact != null) {
//                                val dump = contactsViewModel.dumpContact(contact!!.id)
//                                context.copyToClipboard(dump)
//                            }
//                        }) {
//                            Icon(Icons.Default.BugReport, "Dump Contact")
//                        }
                        val interactionSource = remember { MutableInteractionSource() }
                        val isPressed by interactionSource.collectIsPressedAsState()
                        val cornerRadius by animateDpAsState(
                            if (isPressed) 8.dp else 40.dp,
                            spring(stiffness = Spring.StiffnessMediumLow),
                            label = "ButtonShapeAnimation"
                        )
                        IconButton(
                            onClick = {
                                contact?.let { thisContact ->
                                    val newFavorite = !thisContact.isFavorite
                                    contact = thisContact.copy(isFavorite = newFavorite)
                                    contactsViewModel.toggleFavorite(thisContact)
                                }
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
                                stringResource(if (isFavorite) R.string.remove_from_favorites else R.string.add_to_favorites))
                        }
                        Spacer(modifier = Modifier.size(8.dp))
                        IconButton(onClick = {
//                            navController.navigate(ContactEditScreenDestination(contactId = contact!!.id).route) {
//                                launchSingleTop = true
//                                restoreState = true
//                            }
                            if (contactSources.size > 1) {
                                showSourcesDialog = true
                            } else {
                                navController.navigate(ContactEditScreenDestination(contactId = contact!!.id).route) {
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }) { Icon(Icons.Rounded.Edit, stringResource(R.string.edit)) }
                    } else if (phoneNumber != null && phoneNumber != "Unknown") {
                        IconButton(onClick = {
                            navigator.navigate(ContactEditScreenDestination(initialPhone = phoneNumber))
                        }) { Icon(Icons.Rounded.PersonAdd, stringResource(R.string.add_contact)) }
                    }
                    if (contact != null) {
                        IconButton(onClick = {
                            scope.launch {
                                val lastIndex = listState.layoutInfo.totalItemsCount - 2
                                listState.animateScrollToItem(lastIndex)
                            }
                        }) { Icon(ImageVector.vectorResource(id = R.drawable.ic_settings_account_box), stringResource(R.string.contacts_settings)) }
                    }
                    Spacer(modifier = Modifier.size(4.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier
//            .padding(innerPadding)
            .padding(
                top = innerPadding.calculateTopPadding(),
                start = 0.dp,
                end = 0.dp,
                bottom = 0.dp
            )
            .fillMaxSize()
            .alpha(screenAlpha)
            .offset(y = screenOffsetY)
        ) {
            if (isFullLoading) {
                RillLoadingIndicatorView()
            } else {
                val configuration = LocalConfiguration.current
                val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                if (isLandscape) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.Top
                    ) {
                        // Left column - avatar
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .then(
                                    if (isRotation90) Modifier.navigationBarsPadding()
                                    else Modifier
                                )
                                .padding(bottom = 24.dp)
                                .fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            RillAvatar(
                                name = displayName,
                                photoUri = contact?.photoUri,
                                modifier = Modifier.size(180.dp),
                                shape = CircleShape
                            )
                        }

                        // Right column contains all other content
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .weight(1.7f),
                            contentPadding = PaddingValues(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            item {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = displayName,
                                        style = MaterialTheme.typography.headlineLarge,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center
                                    )
                                    if (contact?.nickname != "") {
                                        contact?.nickname?.let {
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Text(
                                                text = it,
                                                style = MaterialTheme.typography.bodyMedium,
                                                textAlign = TextAlign.Center,
                                            )
                                        }
                                    }
                                    if (companyAndJob != "") {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = companyAndJob,
                                            style = MaterialTheme.typography.bodyMedium,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }

                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surface)
                                        .padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    RillExpressiveButton(
                                        modifier = Modifier.weight(1f),
                                        icon = Icons.Rounded.Phone,
                                        label = stringResource(R.string.call),
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        enabled = (contact != null && contact!!.phoneNumbers.isNotEmpty()) || displayPhone != "Unknown",
                                        onClick = {
                                            if (contact != null && defaultPhone != null) initiateCall(
                                                defaultPhone.number
                                            )
                                            else if (contact != null && contact!!.phoneNumbers.size > 1) showNumberPicker =
                                                true
                                            else if (displayPhone != "Unknown") initiateCall(displayPhone)
                                        })
                                    val messageImageVector: ImageVector =
                                        ImageVector.vectorResource(id = R.drawable.ic_message_filled)
                                    RillExpressiveButton(
                                        modifier = Modifier.weight(1f),
                                        icon = messageImageVector,
                                        label = stringResource(R.string.message),
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        enabled = (contact != null && contact!!.phoneNumbers.isNotEmpty()) || displayPhone != "Unknown",
                                        onClick = {
                                            if (contact != null && defaultPhone != null) initiateMessage(
                                                defaultPhone.number
                                            )
                                            else if (contact != null && contact!!.phoneNumbers.size > 1) showMessagePicker =
                                                true
                                            else if (displayPhone != "Unknown") initiateMessage(displayPhone)
                                        })
                                    val videoImageVector: ImageVector =
                                        ImageVector.vectorResource(id = R.drawable.ic_video_camera)
                                    RillExpressiveButton(
                                        modifier = Modifier.weight(1f),
                                        icon = videoImageVector,
                                        label = stringResource(R.string.video),
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        enabled = (contact != null && contact!!.phoneNumbers.isNotEmpty()) || displayPhone != "Unknown",
                                        onClick = {
                                            videoLauncher.startVideoCall(displayPhone, contact)
                                        })
                                    RillExpressiveButton(
                                        modifier = Modifier.weight(1f),
                                        icon = Icons.Rounded.Email,
                                        label = stringResource(R.string.email),
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        enabled = contact != null && contact!!.emails.isNotEmpty(),
                                        onClick = {
                                            if (contact != null && contact!!.emails.size > 1) showEmailPicker =
                                                true
                                            else if (contact != null && contact!!.emails.isNotEmpty()) initiateEmail(
                                                contact!!.emails.first().value
                                            )
                                        })
                                }
                            }

                            // Contact Info
                            item {
                                RillExpressiveCard(title = stringResource(R.string.contact_info)) {
                                    val configuration = LocalConfiguration.current
                                    val screenWidth = configuration.screenWidthDp.dp
                                    val middleOfScreen = screenWidth / 3
                                    val offsetMenu = DpOffset(middleOfScreen, (-24).dp)
                                    if (contact != null) {
                                        val messageImageVector: ImageVector =
                                            ImageVector.vectorResource(id = R.drawable.ic_message_outline)
                                        val numberSize = contact!!.phoneDetails.size
                                        contact!!.phoneDetails.forEachIndexed { index, phoneDetail ->
                                            val recentText = stringResource(R.string.recent_number)
                                            val recent = if (normalizePhoneNumber(phoneDetail.number) == phoneNumber) " • $recentText" else ""
                                            val isDefault = defaultPhone != null && phoneDetail.number == defaultPhone.number
                                            val default = stringResource(R.string.default_number)
                                            val label =
                                                if (isDefault) getPhoneTypeText(context, phoneDetail.type, phoneDetail.label) + " • $default" + recent
                                                else getPhoneTypeText(context, phoneDetail.type, phoneDetail.label) + recent
                                            val interactionSource = remember { MutableInteractionSource() }
                                            Box {
                                                var showOverflowMenu by remember { mutableStateOf(false) }
                                                RillListItem(
                                                    headline = phoneDetail.number,
                                                    supporting = label,
                                                    leadingIcon = if (isDefault) ImageVector.vectorResource(id = R.drawable.ic_phone_star) else Icons.Rounded.Phone,
                                                    iconContainerColor =
                                                        if (isDefault) MaterialTheme.colorScheme.onSecondaryContainer else null,
                                                    iconBgContainerColor =
                                                        if (isDefault) MaterialTheme.colorScheme.primaryContainer else null,
                                                    trailingIcon = messageImageVector,
                                                    modifierTrailingIcon = Modifier
                                                        .padding(end = 8.dp)
                                                        .size(24.dp)
                                                        .combinedClickable(
                                                            interactionSource = interactionSource,
                                                            indication = ripple(
                                                                bounded = false,
                                                                radius = 24.dp
                                                            ),
                                                            onClick = { initiateMessage(phoneDetail.number) }),
                                                    onClick = { initiateCall(phoneDetail.number) },
                                                    onLongClick = { showOverflowMenu = true }
                                                )
                                                // Dropdown menu
                                                DropdownMenu(
                                                    shape = RoundedCornerShape(16.dp),
                                                    expanded = showOverflowMenu,
                                                    onDismissRequest = { showOverflowMenu = false },
                                                    offset = offsetMenu,
                                                ) {
                                                    DropdownMenuItem(
                                                        contentPadding = PaddingValues(horizontal = 24.dp),
                                                        text = {
                                                            Text(
                                                                phoneDetail.number,
                                                                style = MaterialTheme.typography.bodyLarge,
                                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                            )
                                                        },
                                                        onClick = { },
                                                        enabled = false
                                                    )
                                                    DropdownMenuItem(
                                                        contentPadding = PaddingValues(horizontal = 24.dp),
                                                        text = { Text(stringResource(R.string.add_note)) },
                                                        onClick = {
                                                            showOverflowMenu = false
                                                            editingNoteNumber = phoneDetail.number
                                                            showNoteEditor = true
                                                        }
                                                    )
                                                    DropdownMenuItem(
                                                        contentPadding = PaddingValues(horizontal = 24.dp),
                                                        text = { Text(stringResource(R.string.copy)) },
                                                        onClick = {
                                                            showOverflowMenu = false
                                                            context.copyToClipboard(phoneDetail.number)
                                                        }
                                                    )
                                                    if (numberSize > 1) {
                                                        if (phoneDetail.number == defaultPhone?.number) {
                                                            val textToast = stringResource(R.string.default_phone_number_cleared)
                                                            DropdownMenuItem(
                                                                contentPadding = PaddingValues(horizontal = 24.dp),
                                                                text = { Text(stringResource(R.string.clear_default)) },
                                                                onClick = {
                                                                    showOverflowMenu = false
                                                                    updateDefaultPhone(phoneDetail.number, false)
                                                                    context.toast(textToast)
                                                                }
                                                            )
                                                        } else {
                                                            val message = stringResource(
                                                                R.string.default_phone_set,
                                                                phoneDetail.number
                                                            )
                                                            DropdownMenuItem(
                                                                contentPadding = PaddingValues(horizontal = 24.dp),
                                                                text = { Text(stringResource(R.string.set_as_default)) },
                                                                onClick = {
                                                                    showOverflowMenu = false
                                                                    updateDefaultPhone(phoneDetail.number, true)
                                                                    context.toast(message)
                                                                }
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        contact!!.emails.forEachIndexed { index, email ->
                                            val label = getEmailTypeText(context, email.type, email.label)
                                            RillListItem(
                                                headline = email.value,
                                                supporting = label,
                                                leadingIcon = Icons.Rounded.Email,
                                                onClick = { initiateEmail(email.value) },
                                                onLongClick = { context.copyToClipboard(email.value) }
                                            )
                                        }

                                        if (contact!!.phoneDetails.isEmpty() && contact!!.emails.isEmpty()) {
                                            RillListItem(
                                                headline = stringResource(R.string.add_phone),
                                                leadingIcon = Icons.Rounded.Add,
                                                onClick = {
                                                    if (contactSources.size > 1) {
                                                        showSourcesDialog = true
                                                    } else {
                                                        navController.navigate(ContactEditScreenDestination(contactId = contact!!.id).route) {
                                                            launchSingleTop = true
                                                            restoreState = true
                                                        }
                                                    }
                                                }
                                            )
                                        }
                                    } else if (phoneNumber != null && phoneNumber != "Unknown") {
                                        Box {
                                            var showOverflowMenu by remember { mutableStateOf(false) }
                                            RillListItem(
                                                headline = phoneNumber,
                                                leadingIcon = Icons.Rounded.Phone,
                                                onClick = { initiateCall(phoneNumber) },
                                                onLongClick = { showOverflowMenu = true }
                                            )
                                            // Dropdown menu
                                            DropdownMenu(
                                                shape = RoundedCornerShape(16.dp),
                                                expanded = showOverflowMenu,
                                                onDismissRequest = { showOverflowMenu = false },
                                                offset = offsetMenu,
                                            ) {
                                                DropdownMenuItem(
                                                    contentPadding = PaddingValues(horizontal = 24.dp),
                                                    text = {
                                                        Text(
                                                            phoneNumber,
                                                            style = MaterialTheme.typography.bodyLarge,
                                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        )
                                                    },
                                                    onClick = { },
                                                    enabled = false
                                                )
                                                DropdownMenuItem(
                                                    contentPadding = PaddingValues(horizontal = 24.dp),
                                                    text = { Text(stringResource(R.string.copy)) },
                                                    onClick = {
                                                        showOverflowMenu = false
                                                        context.copyToClipboard(phoneNumber)
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Call Notes section (between Contact Info and Recent Activity)
                            item {
                                var refreshTrigger by remember { mutableIntStateOf(0) }

                                // Get a list of all the contact's phone numbers
                                val phoneNumbersList = remember(contact) {
                                    contact?.phoneDetails?.map { it.number }
                                        ?: if (phoneNumber != null && phoneNumber != "Unknown") {
                                            listOf(phoneNumber)
                                        } else {
                                            emptyList()
                                        }
                                }

                                // Get all the notes for all issues
                                val allNotes = remember(contact, refreshTrigger) {
                                    if (phoneNumbersList.isNotEmpty()) {
                                        NoteManager.getAllNotesForNumbers(context, phoneNumbersList)
                                    } else {
                                        emptyMap()
                                    }
                                }
                                // Refresh after closing the editor
                                LaunchedEffect(showNoteEditor) {
                                    if (!showNoteEditor) {
                                        refreshTrigger++
                                    }
                                }

                                var showAboutNotesDialog by remember { mutableStateOf(false) }
                                if (showAboutNotesDialog) {
                                    RillDialog(
                                        onDismissRequest = { showAboutNotesDialog = false },
                                        title = stringResource(R.string.about_call_notes),
                                        icon = Icons.AutoMirrored.Rounded.Help,
                                        iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkAmber,
                                        iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorAmber,
                                        confirmButton = {
                                            TextButton(onClick = {
                                                showAboutNotesDialog = false
                                            }) {
                                                Text(
                                                    stringResource(R.string.close),
                                                    textAlign = TextAlign.End,
                                                )
                                            }
                                        },
                                        dismissButton = {
                                            val notesDir = NoteManager.getNotesDir(context).absolutePath
                                            TextButton(onClick = {
                                                showAboutNotesDialog = false
                                                context.copyToClipboard(notesDir)
                                            }) {
                                                Text(stringResource(R.string.copy_path))
                                            }
                                        }
                                    ) {
                                        Text(stringResource(R.string.about_call_notes_subtitle))
                                    }
                                }
                                RillExpressiveCard(
                                    title = stringResource(R.string.call_notes),
                                    trailingIcon = Icons.AutoMirrored.Outlined.HelpOutline,
                                    onTrailingIconClick = { showAboutNotesDialog = true }
                                ) {
                                    if (allNotes.isEmpty()) {
                                        // No notes—display the "Add" button
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(
                                                    color = cardColor,
                                                    shape = RoundedCornerShape(
                                                        topStart = cardCornerSmall,
                                                        topEnd = cardCornerSmall,
                                                        bottomStart = cardCornerBig,
                                                        bottomEnd = cardCornerBig
                                                    )
                                                )
                                                .combinedClickable(
                                                    onClick = {
                                                        if (contact != null && contact!!.phoneNumbers.size > 1) showAddCallNotePicker =
                                                            true
                                                        else if (displayPhone != "Unknown") initiateAddCallNote(
                                                            displayPhone
                                                        )
                                                    }
                                                )
                                                .padding(vertical = 16.dp, horizontal = 24.dp)
                                        ) {
                                            Text(
                                                text = "+ " + stringResource(R.string.add_note),
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            )
                                        }
                                    } else {
                                        // Here is a list of all notes
                                        allNotes.forEach { (number, noteContent) ->
                                            RillListItem(
                                                headline = number,
                                                supporting = noteContent,
                                                leadingIcon = Icons.AutoMirrored.Outlined.StickyNote2,
                                                onClick = {
                                                    editingNoteNumber = number
                                                    showNoteEditor = true
                                                },
                                                onLongClick = {
                                                    if (noteContent.isNotBlank()) {
                                                        val clipboard =
                                                            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                        clipboard.setPrimaryClip(
                                                            ClipData.newPlainText("Phone number", noteContent)
                                                        )
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            // Events & Addresses
                            if (contact != null && (contact!!.events.isNotEmpty() || contact!!.addresses.isNotEmpty())) {
                                item {
                                    RillExpressiveCard(
                                        title = stringResource(R.string.other_information),
                                    ) {
                                        contact!!.events.forEachIndexed { index, event ->
                                            val isBirthday =
                                                event.type == ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY
                                            val label = getEventTypeText(context, event.type, event.label)
                                            RillListItem(
                                                headline = event.date,
                                                supporting = label,
                                                leadingIcon = if (isBirthday) Icons.Outlined.Cake else Icons.Outlined.Event,
                                                onClick = {
                                                    val dateParts = event.date.split("-")
                                                    if (dateParts.size == 3) {
//                                            val year = dateParts[0].toInt()
                                                        val month = dateParts[1].toInt()
                                                        val day = dateParts[2].toInt()

                                                        val currentYear =
                                                            Calendar.getInstance().get(Calendar.YEAR)
                                                        val calendar = Calendar.getInstance().apply {
                                                            set(Calendar.YEAR, currentYear)
                                                            set(
                                                                Calendar.MONTH,
                                                                month - 1
                                                            ) // Month in Calendar: 0 = January
                                                            set(Calendar.DAY_OF_MONTH, day)
                                                            set(Calendar.HOUR_OF_DAY, 12)
                                                            set(Calendar.MINUTE, 0)
                                                            set(Calendar.SECOND, 0)
                                                            set(Calendar.MILLISECOND, 0)
                                                        }
                                                        val uri =
                                                            "content://com.android.calendar/time/${calendar.timeInMillis}".toUri()
                                                        context.startActivity(
                                                            Intent(
                                                                Intent.ACTION_VIEW,
                                                                uri
                                                            )
                                                        )
                                                    }
                                                },
                                                onLongClick = { context.copyToClipboard(event.date) }
                                            )
                                        }
                                        contact!!.addresses.forEachIndexed { index, address ->
                                            val interactionSource = remember { MutableInteractionSource() }
                                            val label =
                                                getAddressTypeText(context, address.type, address.label)
                                            RillListItem(
                                                headline = address.formattedAddress,
                                                supporting = label,
                                                leadingIcon = Icons.Rounded.LocationOn,
                                                trailingIcon = Icons.Outlined.Directions,
                                                modifierTrailingIcon = Modifier
                                                    .padding(end = 8.dp)
                                                    .size(24.dp)
                                                    .combinedClickable(
                                                        interactionSource = interactionSource,
                                                        indication = ripple(
                                                            bounded = false,
                                                            radius = 24.dp
                                                        ),
                                                        onClick = {
                                                            context.startActivity(
                                                                Intent(
                                                                    Intent.ACTION_VIEW,
                                                                    "google.navigation:q=$address".toUri()
                                                                )
                                                            )
                                                        }),
                                                onClick = {
                                                    context.startActivity(
                                                        Intent(
                                                            Intent.ACTION_VIEW,
                                                            "geo:0,0?q=$address".toUri()
                                                        )
                                                    )
                                                },
                                                onLongClick = { context.copyToClipboard(address.formattedAddress) }
                                            )
                                        }
                                    }
                                }
                            }

                            // Recent Activity
                            if (contactLogs.isNotEmpty()) {
                                val hasMultipleNumbers = contactLogs.distinctBy { it.number }.size > 1 || (contact?.phoneNumbers != null && contact?.phoneNumbers?.size!! > 1)
                                item {
                                    RillExpressiveCard(
                                        title = stringResource(R.string.recent_activity),
                                    ) {
                                        Column(modifier = Modifier.animateContentSize()) {
                                            contactLogs.take(3).forEachIndexed { index, log ->
                                                CallLogTileSimple(log, showNumber = hasMultipleNumbers, showSimLabel = showSimLabel)
                                                if (index < 2 && index < contactLogs.size - 1) {
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                }
                                            }
                                            if (contactLogs.size > 3) {
                                                val finalContactId = if (contact?.id != null) contact!!.id else if (contactId != "null") contactId else null
                                                val finalPhoneNumber = phoneNumber ?: contact?.phoneNumbers?.firstOrNull() ?: contactLogs.firstOrNull()?.number
                                                TextButton(
                                                    onClick = {
//                                                        navController.navigate("call_log_detail_screen?contactId=${finalContactId ?: "null"}&phoneNumber=${finalPhoneNumber ?: "null"}")
                                                        navigator.navigate(CallLogFullScreenDestination(
                                                            contactId = finalContactId,
                                                            phoneNumber = finalPhoneNumber,
                                                            numbersList = contact?.phoneNumbers?.toTypedArray()
                                                        ))
                                                    },
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .offset(
                                                            x = 0.dp,
                                                            y = (-2).dp
                                                        ), // We'll make up for the indentation
                                                    colors = ButtonDefaults.textButtonColors()
                                                        .copy(containerColor = cardColor),
                                                    shape = RoundedCornerShape(
                                                        topStart = cardCornerSmall,
                                                        topEnd = cardCornerSmall,
                                                        bottomStart = cardCornerBig,
                                                        bottomEnd = cardCornerBig
                                                    ),
                                                ) {
                                                    Text(stringResource(R.string.show_full_history))
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Contact Settings
                            item {
                                RillExpressiveCard(title = stringResource(R.string.contacts_settings)) {
                                    if (contact != null) {
                                        val currentRingtone = contact!!.customRingtone?.let {
                                            RingtoneManager.getRingtone(context, it.toUri())
                                                ?.getTitle(context) ?: "Custom"
                                        } ?: "Default"
                                        val contactRingtone = stringResource(R.string.contact_ringtone)
                                        RillListItem(
                                            headline = contactRingtone,
                                            supporting = currentRingtone,
                                            leadingIcon = Icons.Rounded.MusicNote,
                                            onClick = {
                                                val intent =
                                                    Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                                        putExtra(
                                                            RingtoneManager.EXTRA_RINGTONE_TYPE,
                                                            RingtoneManager.TYPE_RINGTONE
                                                        )
                                                        putExtra(
                                                            RingtoneManager.EXTRA_RINGTONE_TITLE,
                                                            contactRingtone
                                                        )
                                                        putExtra(
                                                            RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                                                            contact!!.customRingtone?.toUri()
                                                        )
                                                    }
                                                ringtonePickerLauncher.launch(intent)
                                            }
                                        )
                                    }
                                    val interactionSource = remember { MutableInteractionSource() }
                                    RillListItem(
                                        headline = stringResource(R.string.share),
                                        supporting = stringResource(R.string.share_subtitle),
                                        leadingIcon = Icons.Rounded.Share,
                                        trailingIcon = Icons.Rounded.QrCode2,
                                        modifierTrailingIcon = Modifier
                                            .padding(end = 8.dp)
                                            .size(24.dp)
                                            .combinedClickable(
                                                interactionSource = interactionSource,
                                                indication = ripple(
                                                    bounded = false,
                                                    radius = 24.dp
                                                ),
                                                onClick = {
                                                    if (contact != null && contact!!.phoneNumbers.size > 1) showQrDialogPicker =
                                                        true
                                                    else {
                                                        pendingQrNumber = null; showQrDialog = true
                                                    }
                                                }
                                            ),
                                        onClick = {
                                            if (contact != null && contact!!.phoneNumbers.size > 1) showSharePicker = true
                                            else shareContact(displayPhone) }
                                    )
                                    if (contact != null && contactSources.size < 2) {
                                        RillListItem(
                                            headline = stringResource(R.string.move_contact),
                                            supporting = stringResource(R.string.move_to_another_account),
                                            leadingIcon = Icons.AutoMirrored.Rounded.DriveFileMove,
                                            onClick = { showMoveDialog = true }
                                        )
                                    }
                                    if (contact != null) {
                                        RillListItem(
                                            headline = stringResource(R.string.delete),
                                            leadingIcon = ImageVector.vectorResource(id = R.drawable.ic_delete),
                                            iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkRed,
                                            iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorRed,
                                            onClick = { showDeleteDialog = true }
                                        )
                                    }

                                    if (showMoveDialog) {
                                        val textToast = stringResource(R.string.contact_moved_successfully)
                                        MoveSingleContactDialog(
                                            contact = contact!!,
                                            availableAccounts = availableAccountsForMoving,
                                            currentAccountKey = if (contact!!.accountName == null && contact!!.accountType == null && contact!!.isPrivate) private_only
                                                                else if (contact!!.accountName == null && contact!!.accountType == null) device_only
                                                                else "${contact!!.accountName}|${contact!!.accountType}",
                                            contactsViewModel = contactsViewModel,
                                            onDismiss = { showMoveDialog = false },
                                            onSuccess = { selectedAccount, isPrivate ->
                                                context.toast(textToast)
                                                // Updating local data
                                                contact = contact!!.copy(
                                                    accountName = selectedAccount?.name,
                                                    accountType = selectedAccount?.type,
                                                    isPrivate = isPrivate
                                                )
                                                contactAccount = selectedAccount
                                            }
                                        )
                                    }
                                }
                            }

                            // Contact Sources
                            if (contact != null && contactSources.size > 1) {
                                item {
                                    RillExpressiveCard(title = stringResource(R.string.contact_sources)) {
                                        contactSources.forEachIndexed { index, source ->
                                            val account = Account(source.accountName ?: "", source.accountType ?: "")
                                            SourceItem(
                                                modifier = Modifier.combinedClickable(
                                                    onClick = { showSourcesDialog = true },
                                                ),
                                                leadingIcon = ContactUtils.getAccountIcon(account, contact!!.isPrivate),
                                                headline = ContactUtils.getAccountName(account),
                                                supporting = ContactUtils.getFriendlyAccountName(account),
                                            )
                                        }
                                    }
                                }
                            } else if (contact != null) {
                                item {
                                    RillExpressiveCard(title = stringResource(R.string.contact_sources)) {
                                        SourceItem(
                                            modifier = Modifier,
                                            leadingIcon = ContactUtils.getAccountIcon(contactAccount, contact!!.isPrivate),
                                            headline = if (contactAccount != null) ContactUtils.getAccountName(contactAccount!!)
                                            else ContactUtils.getFriendlyAccountName(null, contact!!.isPrivate),
                                            supporting = if (contactAccount != null) ContactUtils.getFriendlyAccountName(contactAccount) else null,
                                        )
                                    }
                                }
                            }

                            item { Spacer(modifier = Modifier.height(100.dp)) }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(340.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            MaterialTheme.colorScheme.surface
                                        )
                                    )
                                )
                        )
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier.wrapContentSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    RillAvatar(
                                        name = displayName,
                                        photoUri = contact?.photoUri,
                                        modifier = Modifier.size(180.dp),
                                        shape = CircleShape
                                    )
                                }
                                Spacer(modifier = Modifier.height(20.dp))
                                Text(
                                    text = displayName,
                                    style = MaterialTheme.typography.headlineLarge,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                                if (contact?.nickname != "") {
                                    contact?.nickname?.let {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = it,
                                            style = MaterialTheme.typography.bodyMedium,
                                            textAlign = TextAlign.Center,
                                        )
                                    }
                                }
                                if (companyAndJob != "") {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = companyAndJob,
                                        style = MaterialTheme.typography.bodyMedium,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }

                        stickyHeader {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                RillExpressiveButton(
                                    modifier = Modifier.weight(1f),
                                    icon = Icons.Rounded.Phone,
                                    label = stringResource(R.string.call),
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    enabled = (contact != null && contact!!.phoneNumbers.isNotEmpty()) || displayPhone != "Unknown",
                                    onClick = {
                                        if (contact != null && defaultPhone != null) initiateCall(
                                            defaultPhone.number
                                        )
                                        else if (contact != null && contact!!.phoneNumbers.size > 1) showNumberPicker =
                                            true
                                        else if (displayPhone != "Unknown") initiateCall(displayPhone)
                                    })
                                val messageImageVector: ImageVector =
                                    ImageVector.vectorResource(id = R.drawable.ic_message_filled)
                                RillExpressiveButton(
                                    modifier = Modifier.weight(1f),
                                    icon = messageImageVector,
                                    label = stringResource(R.string.message),
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    enabled = (contact != null && contact!!.phoneNumbers.isNotEmpty()) || displayPhone != "Unknown",
                                    onClick = {
                                        if (contact != null && defaultPhone != null) initiateMessage(
                                            defaultPhone.number
                                        )
                                        else if (contact != null && contact!!.phoneNumbers.size > 1) showMessagePicker =
                                            true
                                        else if (displayPhone != "Unknown") initiateMessage(displayPhone)
                                    })
                                val videoImageVector: ImageVector =
                                    ImageVector.vectorResource(id = R.drawable.ic_video_camera)
                                RillExpressiveButton(
                                    modifier = Modifier.weight(1f),
                                    icon = videoImageVector,
                                    label = stringResource(R.string.video),
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    enabled = (contact != null && contact!!.phoneNumbers.isNotEmpty()) || displayPhone != "Unknown",
                                    onClick = {
                                        videoLauncher.startVideoCall(displayPhone, contact)
                                    })
                                RillExpressiveButton(
                                    modifier = Modifier.weight(1f),
                                    icon = Icons.Rounded.Email,
                                    label = stringResource(R.string.email),
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    enabled = contact != null && contact!!.emails.isNotEmpty(),
                                    onClick = {
                                        if (contact != null && contact!!.emails.size > 1) showEmailPicker =
                                            true
                                        else if (contact != null && contact!!.emails.isNotEmpty()) initiateEmail(
                                            contact!!.emails.first().value
                                        )
                                    })
                            }
                        }

                        // Contact Info
                        item {
                            RillExpressiveCard(
                                title = stringResource(R.string.contact_info),
                            ) {
                                val configuration = LocalConfiguration.current
                                val screenWidth = configuration.screenWidthDp.dp
                                val middleOfScreen = screenWidth / 3
                                val offsetMenu = DpOffset(middleOfScreen, (-24).dp)
                                if (contact != null) {
                                    val messageImageVector: ImageVector =
                                        ImageVector.vectorResource(id = R.drawable.ic_message_outline)
                                    val whatsappImageVector: ImageVector =
                                        ImageVector.vectorResource(id = R.drawable.ic_whatsapp)
                                    val numberSize = contact!!.phoneDetails.size
                                    val whatsappInstall =
                                        context.isPackageInstalled("com.whatsapp") || context.isPackageInstalled(
                                            "com.whatsapp.w4b"
                                        )
                                    contact!!.phoneDetails.forEachIndexed { index, phoneDetail ->
                                        val recentText = stringResource(R.string.recent_number)
                                        val recent = if (normalizePhoneNumber(phoneDetail.number) == phoneNumber) " • $recentText" else ""
                                        val isDefault = defaultPhone != null && phoneDetail.number == defaultPhone.number
                                        val default = stringResource(R.string.default_number)
                                        val label =
                                            if (isDefault) getPhoneTypeText(context, phoneDetail.type, phoneDetail.label) + " • $default" + recent
                                            else getPhoneTypeText(context, phoneDetail.type, phoneDetail.label) + recent
                                        Box {
                                            var showOverflowMenu by remember {
                                                mutableStateOf(
                                                    false
                                                )
                                            }
                                            RillListItem(
                                                headline = phoneDetail.number,
                                                supporting = label,
                                                leadingIcon = if (isDefault) ImageVector.vectorResource(id = R.drawable.ic_phone_star) else Icons.Rounded.Phone,
                                                iconContainerColor =
                                                    if (isDefault) MaterialTheme.colorScheme.onSecondaryContainer else null,
                                                iconBgContainerColor =
                                                    if (isDefault) MaterialTheme.colorScheme.primaryContainer else null,
                                                preTrailingIcon = if (whatsappInstall) whatsappImageVector else null,
                                                modifierPreTrailingIcon = Modifier
                                                    .padding(end = 8.dp)
                                                    .size(24.dp)
                                                    .combinedClickable(
                                                        interactionSource = null,
                                                        indication = ripple(
                                                            bounded = false,
                                                            radius = 24.dp
                                                        ),
                                                        onClick = {
                                                            context.launchSendWhatsAppIntent(
                                                                phoneDetail.number
                                                            )
                                                        }),
                                                trailingIcon = messageImageVector,
                                                modifierTrailingIcon = Modifier
                                                    .padding(end = 8.dp)
                                                    .size(24.dp)
                                                    .combinedClickable(
                                                        interactionSource = null,
                                                        indication = ripple(
                                                            bounded = false,
                                                            radius = 24.dp
                                                        ),
                                                        onClick = {
                                                            initiateMessage(
                                                                phoneDetail.number
                                                            )
                                                        }),
                                                onClick = { initiateCall(phoneDetail.number) },
                                                onLongClick = { showOverflowMenu = true }
                                            )
                                            // Dropdown menu
                                            DropdownMenu(
                                                shape = RoundedCornerShape(16.dp),
                                                expanded = showOverflowMenu,
                                                onDismissRequest = {
                                                    showOverflowMenu = false
                                                },
                                                offset = offsetMenu,
                                            ) {
                                                DropdownMenuItem(
                                                    contentPadding = PaddingValues(
                                                        horizontal = 24.dp
                                                    ),
                                                    text = {
                                                        Text(
                                                            phoneDetail.number,
                                                            style = MaterialTheme.typography.bodyLarge,
                                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        )
                                                    },
                                                    onClick = { },
                                                    enabled = false
                                                )
                                                DropdownMenuItem(
                                                    contentPadding = PaddingValues(
                                                        horizontal = 24.dp
                                                    ),
                                                    text = { Text(stringResource(R.string.add_note)) },
                                                    onClick = {
                                                        showOverflowMenu = false
                                                        editingNoteNumber =
                                                            phoneDetail.number
                                                        showNoteEditor = true
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    contentPadding = PaddingValues(
                                                        horizontal = 24.dp
                                                    ),
                                                    text = { Text(stringResource(R.string.copy)) },
                                                    onClick = {
                                                        showOverflowMenu = false
                                                        context.copyToClipboard(phoneDetail.number)
                                                    }
                                                )
                                                if (numberSize > 1) {
                                                    if (phoneDetail.number == defaultPhone?.number) {
                                                        val textToast = stringResource(R.string.default_phone_number_cleared)
                                                        DropdownMenuItem(
                                                            contentPadding = PaddingValues(
                                                                horizontal = 24.dp
                                                            ),
                                                            text = { Text(stringResource(R.string.clear_default)) },
                                                            onClick = {
                                                                showOverflowMenu = false
                                                                updateDefaultPhone(
                                                                    phoneDetail.number,
                                                                    false
                                                                )
                                                                context.toast(textToast)
                                                            }
                                                        )
                                                    } else {
                                                        val message = stringResource(
                                                            R.string.default_phone_set,
                                                            phoneDetail.number
                                                        )
                                                        DropdownMenuItem(
                                                            contentPadding = PaddingValues(
                                                                horizontal = 24.dp
                                                            ),
                                                            text = { Text(stringResource(R.string.set_as_default)) },
                                                            onClick = {
                                                                showOverflowMenu = false
                                                                updateDefaultPhone(
                                                                    phoneDetail.number,
                                                                    true
                                                                )
                                                                context.toast(message)
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    contact!!.emails.forEachIndexed { index, email ->
                                        val label =
                                            getEmailTypeText(context, email.type, email.label)
                                        RillListItem(
                                            headline = email.value,
                                            supporting = label,
                                            leadingIcon = Icons.Rounded.Email,
                                            onClick = { initiateEmail(email.value) },
                                            onLongClick = { context.copyToClipboard(email.value) }
                                        )
                                    }

                                    if (contact!!.phoneDetails.isEmpty() && contact!!.emails.isEmpty()) {
                                        RillListItem(
                                            headline = stringResource(R.string.add_phone),
                                            leadingIcon = Icons.Rounded.Add,
                                            onClick = {
                                                if (contactSources.size > 1) {
                                                    showSourcesDialog = true
                                                } else {
                                                    navController.navigate(ContactEditScreenDestination(contactId = contact!!.id).route) {
                                                        launchSingleTop = true
                                                        restoreState = true
                                                    }
                                                }
                                            }
                                        )
                                    }
                                } else if (phoneNumber != null && phoneNumber != "Unknown") {
                                    Box {
                                        var showOverflowMenu by remember { mutableStateOf(false) }
                                        RillListItem(
                                            headline = phoneNumber,
                                            leadingIcon = Icons.Rounded.Phone,
                                            onClick = { initiateCall(phoneNumber) },
                                            onLongClick = { showOverflowMenu = true }
                                        )
                                        // Dropdown menu
                                        DropdownMenu(
                                            shape = RoundedCornerShape(16.dp),
                                            expanded = showOverflowMenu,
                                            onDismissRequest = { showOverflowMenu = false },
                                            offset = offsetMenu,
                                        ) {
                                            DropdownMenuItem(
                                                contentPadding = PaddingValues(horizontal = 24.dp),
                                                text = {
                                                    Text(
                                                        phoneNumber,
                                                        style = MaterialTheme.typography.bodyLarge,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    )
                                                },
                                                onClick = { },
                                                enabled = false
                                            )
                                            DropdownMenuItem(
                                                contentPadding = PaddingValues(horizontal = 24.dp),
                                                text = { Text(stringResource(R.string.copy)) },
                                                onClick = {
                                                    showOverflowMenu = false
                                                    context.copyToClipboard(phoneNumber)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Call Notes section (between Contact Info and Recent Activity)
                        item {
                            var refreshTrigger by remember { mutableIntStateOf(0) }

                            // Get a list of all the contact's phone numbers
                            val phoneNumbersList = remember(contact) {
                                contact?.phoneDetails?.map { it.number }
                                    ?: if (phoneNumber != null && phoneNumber != "Unknown") {
                                        listOf(phoneNumber)
                                    } else {
                                        emptyList()
                                    }
                            }

                            // Get all the notes for all issues
                            val allNotes = remember(contact, refreshTrigger) {
                                if (phoneNumbersList.isNotEmpty()) {
                                    NoteManager.getAllNotesForNumbers(context, phoneNumbersList)
                                } else {
                                    emptyMap()
                                }
                            }
                            // Refresh after closing the editor
                            LaunchedEffect(showNoteEditor) {
                                if (!showNoteEditor) {
                                    refreshTrigger++
                                }
                            }

                            var showAboutNotesDialog by remember { mutableStateOf(false) }
                            if (showAboutNotesDialog) {
                                RillDialog(
                                    onDismissRequest = { showAboutNotesDialog = false },
                                    title = stringResource(R.string.about_call_notes),
                                    icon = Icons.AutoMirrored.Rounded.Help,
                                    iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkAmber,
                                    iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorAmber,
                                    confirmButton = {
                                        TextButton(onClick = {
                                            showAboutNotesDialog = false
                                        }) {
                                            Text(
                                                stringResource(R.string.close),
                                                textAlign = TextAlign.End,
                                            )
                                        }
                                    },
                                    dismissButton = {
                                        val notesDir = NoteManager.getNotesDir(context).absolutePath
                                        TextButton(onClick = {
                                            showAboutNotesDialog = false
                                            context.copyToClipboard(notesDir)
                                        }) {
                                            Text(stringResource(R.string.copy_path))
                                        }
                                    }
                                ) {
                                    Text(stringResource(R.string.about_call_notes_subtitle))
                                }
                            }
                            RillExpressiveCard(
                                title = stringResource(R.string.call_notes),
//                        icon = Icons.AutoMirrored.Outlined.StickyNote2,
                                trailingIcon = Icons.AutoMirrored.Outlined.HelpOutline,
                                onTrailingIconClick = { showAboutNotesDialog = true }
                            ) {
                                if (allNotes.isEmpty()) {
                                    // No notes—display the "Add" button
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                color = cardColor,
                                                shape = RoundedCornerShape(
                                                    topStart = cardCornerSmall,
                                                    topEnd = cardCornerSmall,
                                                    bottomStart = cardCornerBig,
                                                    bottomEnd = cardCornerBig
                                                )
                                            )
                                            .combinedClickable(
                                                onClick = {
                                                    if (contact != null && contact!!.phoneNumbers.size > 1) showAddCallNotePicker =
                                                        true
                                                    else if (displayPhone != "Unknown") initiateAddCallNote(
                                                        displayPhone
                                                    )
                                                }
                                            )
                                            .padding(vertical = 16.dp, horizontal = 24.dp)
                                    ) {
                                        Text(
                                            text = "+ " + stringResource(R.string.add_note),
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        )
                                    }
                                } else {
                                    // Here is a list of all notes
                                    allNotes.forEach { (number, noteContent) ->
                                        RillListItem(
                                            headline = number,
                                            supporting = noteContent,
                                            leadingIcon = Icons.AutoMirrored.Outlined.StickyNote2,
                                            onClick = {
                                                editingNoteNumber = number
                                                showNoteEditor = true
                                            },
                                            onLongClick = {
                                                if (noteContent.isNotBlank()) {
                                                    val clipboard =
                                                        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                    clipboard.setPrimaryClip(
                                                        ClipData.newPlainText(
                                                            "Phone number",
                                                            noteContent
                                                        )
                                                    )
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Events & Addresses
                        if (contact != null && (contact!!.events.isNotEmpty() || contact!!.addresses.isNotEmpty())) {
                            item {
                                RillExpressiveCard(
                                    title = stringResource(R.string.other_information),
//                            icon = Icons.Default.Event
                                ) {
                                    contact!!.events.forEachIndexed { index, event ->
                                        val isBirthday =
                                            event.type == ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY
                                        val label = getEventTypeText(context, event.type, event.label)
                                        RillListItem(
                                            headline = event.date,
                                            supporting = label,
                                            leadingIcon = if (isBirthday) Icons.Outlined.Cake else Icons.Outlined.Event,
                                            onClick = {
                                                val dateParts = event.date.split("-")
                                                if (dateParts.size == 3) {
//                                            val year = dateParts[0].toInt()
                                                    val month = dateParts[1].toInt()
                                                    val day = dateParts[2].toInt()

                                                    val currentYear =
                                                        Calendar.getInstance().get(Calendar.YEAR)
                                                    val calendar = Calendar.getInstance().apply {
                                                        set(Calendar.YEAR, currentYear)
                                                        set(
                                                            Calendar.MONTH,
                                                            month - 1
                                                        ) // Month in Calendar: 0 = January
                                                        set(Calendar.DAY_OF_MONTH, day)
                                                        set(Calendar.HOUR_OF_DAY, 12)
                                                        set(Calendar.MINUTE, 0)
                                                        set(Calendar.SECOND, 0)
                                                        set(Calendar.MILLISECOND, 0)
                                                    }
                                                    val uri =
                                                        "content://com.android.calendar/time/${calendar.timeInMillis}".toUri()
                                                    context.startActivity(
                                                        Intent(
                                                            Intent.ACTION_VIEW,
                                                            uri
                                                        )
                                                    )
                                                }
                                            },
                                            onLongClick = { context.copyToClipboard(event.date) }
                                        )
                                    }
                                    contact!!.addresses.forEachIndexed { index, address ->
                                        val interactionSource = remember { MutableInteractionSource() }
                                        val label =
                                            getAddressTypeText(context, address.type, address.label)
                                        RillListItem(
                                            headline = address.formattedAddress,
                                            supporting = label,
                                            leadingIcon = Icons.Rounded.LocationOn,
                                            trailingIcon = Icons.Outlined.Directions,
                                            modifierTrailingIcon = Modifier
                                                .padding(end = 8.dp)
                                                .size(24.dp)
                                                .combinedClickable(
                                                    interactionSource = interactionSource,
                                                    indication = ripple(
                                                        bounded = false,
                                                        radius = 24.dp
                                                    ),
                                                    onClick = {
                                                        context.startActivity(
                                                            Intent(
                                                                Intent.ACTION_VIEW,
                                                                "google.navigation:q=$address".toUri()
                                                            )
                                                        )
                                                    }),
                                            onClick = {
                                                context.startActivity(
                                                    Intent(
                                                        Intent.ACTION_VIEW,
                                                        "geo:0,0?q=$address".toUri()
                                                    )
                                                )
                                            },
                                            onLongClick = { context.copyToClipboard(address.formattedAddress) }
                                        )
                                    }
                                }
                            }
                        }

                        // Recent Activity
                        if (contactLogs.isNotEmpty()) {
                            val hasMultipleNumbers = contactLogs.distinctBy { it.number }.size > 1 || (contact?.phoneNumbers != null && contact?.phoneNumbers?.size!! > 1)
                            item {
                                RillExpressiveCard(
                                    title = stringResource(R.string.recent_activity),
                                ) {
                                    Column(modifier = Modifier.animateContentSize()) {
                                        contactLogs.take(3).forEachIndexed { index, log ->
                                            CallLogTileSimple(log, showNumber = hasMultipleNumbers, showSimLabel = showSimLabel)
                                            if (index < 2 && index < contactLogs.size - 1) {
                                                Spacer(modifier = Modifier.height(2.dp))
                                            }
                                        }
                                        if (contactLogs.size > 3) {
                                            val finalContactId = if (contact?.id != null) contact!!.id else if (contactId != "null") contactId else null
                                            val finalPhoneNumber = phoneNumber ?: contact?.phoneNumbers?.firstOrNull() ?: contactLogs.firstOrNull()?.number
                                            TextButton(
                                                onClick = {
//                                                    navController.navigate("call_log_detail_screen?contactId=${finalContactId ?: "null"}&phoneNumber=${finalPhoneNumber ?: "null"}")
                                                    navigator.navigate(CallLogFullScreenDestination(
                                                        contactId = finalContactId,
                                                        phoneNumber = finalPhoneNumber,
                                                        numbersList = contact?.phoneNumbers?.toTypedArray()
                                                    ))
                                                },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .offset(
                                                        x = 0.dp,
                                                        y = (-2).dp
                                                    ), // We'll make up for the indentation
                                                colors = ButtonDefaults.textButtonColors()
                                                    .copy(containerColor = cardColor),
                                                shape = RoundedCornerShape(
                                                    topStart = cardCornerSmall,
                                                    topEnd = cardCornerSmall,
                                                    bottomStart = cardCornerBig,
                                                    bottomEnd = cardCornerBig
                                                ),
                                            ) {
                                                Text(stringResource(R.string.show_full_history))
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Contact Settings
                        item {
                            RillExpressiveCard(title = stringResource(R.string.contacts_settings)) {
                                if (contact != null) {
                                    val currentRingtone = contact!!.customRingtone?.let {
                                        RingtoneManager.getRingtone(context, it.toUri())
                                            ?.getTitle(context) ?: "Custom"
                                    } ?: "Default"
                                    val contactRingtone = stringResource(R.string.contact_ringtone)
                                    RillListItem(
                                        headline = contactRingtone,
                                        supporting = currentRingtone,
                                        leadingIcon = Icons.Rounded.MusicNote,
                                        onClick = {
                                            val intent =
                                                Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                                    putExtra(
                                                        RingtoneManager.EXTRA_RINGTONE_TYPE,
                                                        RingtoneManager.TYPE_RINGTONE
                                                    )
                                                    putExtra(
                                                        RingtoneManager.EXTRA_RINGTONE_TITLE,
                                                        contactRingtone
                                                    )
                                                    putExtra(
                                                        RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                                                        contact!!.customRingtone?.toUri()
                                                    )
                                                }
                                            ringtonePickerLauncher.launch(intent)
                                        }
                                    )
                                }
                                val interactionSource = remember { MutableInteractionSource() }
                                RillListItem(
                                    headline = stringResource(R.string.share),
                                    supporting = stringResource(R.string.share_subtitle),
                                    leadingIcon = Icons.Rounded.Share,
                                    trailingIcon = Icons.Rounded.QrCode2,
                                    modifierTrailingIcon = Modifier
                                        .padding(end = 8.dp)
                                        .size(24.dp)
                                        .combinedClickable(
                                            interactionSource = interactionSource,
                                            indication = ripple(
                                                bounded = false,
                                                radius = 24.dp
                                            ),
                                            onClick = {
                                                if (contact != null && contact!!.phoneNumbers.size > 1) showQrDialogPicker =
                                                    true
                                                else {
                                                    pendingQrNumber = null; showQrDialog = true
                                                }
                                            }
                                        ),
                                    onClick = {
                                        if (contact != null && contact!!.phoneNumbers.size > 1) showSharePicker = true
                                        else shareContact(displayPhone)
                                    }
                                )
                                if (contact != null && contactSources.size < 2) {
                                    RillListItem(
                                        headline = stringResource(R.string.move_contact),
                                        supporting = stringResource(R.string.move_to_another_account),
                                        leadingIcon = Icons.AutoMirrored.Rounded.DriveFileMove,
                                        onClick = { showMoveDialog = true }
                                    )
                                }
                                if (contact != null) {
                                    RillListItem(
                                        headline = stringResource(R.string.delete),
                                        leadingIcon = ImageVector.vectorResource(id = R.drawable.ic_delete),
                                        iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkRed,
                                        iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorRed,
                                        onClick = { showDeleteDialog = true }
                                    )
                                }

                                if (showMoveDialog) {
                                    val textToast = stringResource(R.string.contact_moved_successfully)
                                    MoveSingleContactDialog(
                                        contact = contact!!,
                                        availableAccounts = availableAccountsForMoving,
                                        currentAccountKey = if (contact!!.accountName == null && contact!!.accountType == null && contact!!.isPrivate) private_only
                                                            else if (contact!!.accountName == null && contact!!.accountType == null) device_only
                                                            else "${contact!!.accountName}|${contact!!.accountType}",
                                        contactsViewModel = contactsViewModel,
                                        onDismiss = { showMoveDialog = false },
                                        onSuccess = { selectedAccount, isPrivate ->
                                            context.toast(textToast)
                                            // Updating local data
                                            contact = contact!!.copy(
                                                accountName = selectedAccount?.name,
                                                accountType = selectedAccount?.type,
                                                isPrivate = isPrivate
                                            )
                                            contactAccount = selectedAccount
                                        }
                                    )
                                }
                            }
                        }

                        // Contact Sources
                        if (contact != null && contactSources.size > 1) {
                            item {
                                RillExpressiveCard(title = stringResource(R.string.contact_sources)) {
                                    contactSources.forEachIndexed { index, source ->
                                        val account = Account(source.accountName ?: "", source.accountType ?: "")
                                        SourceItem(
                                            modifier = Modifier.combinedClickable(
                                                onClick = { showSourcesDialog = true },
                                            ),
                                            leadingIcon = ContactUtils.getAccountIcon(account, contact!!.isPrivate),
                                            headline = ContactUtils.getAccountName(account),
                                            supporting = ContactUtils.getFriendlyAccountName(account),
                                        )
                                    }
                                }
                            }
                        } else if (contact != null) {
                            item {
                                RillExpressiveCard(title = stringResource(R.string.contact_sources)) {
                                    SourceItem(
                                        modifier = Modifier,
                                        leadingIcon = ContactUtils.getAccountIcon(contactAccount, contact!!.isPrivate),
                                        headline = if (contactAccount != null) ContactUtils.getAccountName(contactAccount!!)
                                                    else ContactUtils.getFriendlyAccountName(null, contact!!.isPrivate),
                                        supporting = if (contactAccount != null) ContactUtils.getFriendlyAccountName(contactAccount) else null,
                                    )
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
    }
}

@Composable
fun QrCodeDialog(name: String, phone: String?, email: String?, onDismiss: () -> Unit) {
    val vCard = remember(name, phone, email) { QrCodeUtils.generateVCard(name, phone, email) }
    val qrBitmap = remember(vCard) { QrCodeUtils.generateQrCode(vCard, 600) }
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.contact_qr), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                qrBitmap?.let {
                    Image(bitmap = it.asImageBitmap(), contentDescription = "QR Code", modifier = Modifier
                        .size(240.dp)
                        .background(Color.White, RoundedCornerShape(12.dp))
                        .padding(12.dp))
                }
                Spacer(Modifier.height(16.dp))
                Text(name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
                Text(phone ?: "", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(24.dp))
                Button(onClick = onDismiss, modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp), shape = RoundedCornerShape(12.dp)) { Text(stringResource(R.string.close)) }
            }
        }
    }
}

private fun buildClickableAnnotatedString(text: String): AnnotatedString {
    val urlPattern = android.util.Patterns.WEB_URL
    return buildAnnotatedString {
        var lastIdx = 0
        val matcher = urlPattern.matcher(text)
        while (matcher.find()) {
            val start = matcher.start()
            val end = matcher.end()
            append(text.substring(lastIdx, start))
            pushStringAnnotation("URL", matcher.group())
            withStyle(SpanStyle(color = Color(0xFF1E88E5), textDecoration = TextDecoration.Underline)) {
                append(text.substring(start, end))
            }
            pop()
            lastIdx = end
        }
        append(text.substring(lastIdx))
    }
}


@Composable
fun SourceItem(
    modifier: Modifier,
    leadingIcon: ImageVector,
    headline: String,
    supporting: String?,
//    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier,
        color = cardColor,
        shape = RoundedCornerShape(cardCornerSmall),
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = headline,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (supporting != null) {
                    Text(
                        text = supporting,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun SourcesDialog(
    contact: Contact,
    sources: List<ContactsRepository.ContactSource>,
    navigator: DestinationsNavigator,
    navigateBack: () -> Unit,
    onDismiss: () -> Unit
) {
    val contactsViewModel: ContactsViewModel = koinActivityViewModel()
    var sourceData by remember { mutableStateOf<Map<String, Contact>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(sources) {
        isLoading = true
        val data = mutableMapOf<String, Contact>()
        sources.forEach { source ->
            try {
                val contactData = contactsViewModel.getRawContactData(source.rawContactId)
                if (contactData != null) {
                    data[source.rawContactId] = contactData
                }
            } catch (_: Exception) {
            }
        }
        sourceData = data
        isLoading = false
    }

    RillDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.contact_sources),
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    ) {
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 600.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(cardSpacedBy)
            ) {
                Text(
                    stringResource(R.string.unmerging_contacts_description),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 16.dp)
                )

                RillExpressiveCard(shape = RoundedCornerShape(cardCornerMedium)) {
                    sources.forEachIndexed { _, source ->
                        val account = Account(source.accountName ?: "", source.accountType ?: "")
                        val contactData = sourceData[source.rawContactId]

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(cardCornerSmall),
                            colors = CardDefaults.cardColors(containerColor = cardColor)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onDismiss()
                                        navigator.navigate(
                                            ContactEditScreenDestination(
                                                contactId = contact.id,
                                                rawContactId = source.rawContactId,
                                                isEditingSource = true
                                            )
                                        )
                                    }
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (contactData != null) {
                                    RillAvatar(
                                        name = contactData.displayName,
                                        photoUri = contactData.photoUri,
                                        modifier = Modifier.size(42.dp)
                                    )
                                } else {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = ContactUtils.getAccountIcon(account, contact.isPrivate),
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                if (contactData != null) {
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(start = 14.dp),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        if (contactData.displayName.isNotBlank()) {
                                            Text(
                                                modifier = Modifier.padding(start = 2.dp),
                                                text = contactData.displayName,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }

                                        if (contactData.phoneDetails.isNotEmpty()) {
                                            Text(
                                                modifier = Modifier.padding(start = 2.dp),
                                                text = contactData.phoneDetails.joinToString(", ") { it.number },
                                                style = MaterialTheme.typography.bodyMedium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        } else if (contactData.emails.isNotEmpty()) {
                                            Text(
                                                modifier = Modifier.padding(start = 2.dp),
                                                text =  contactData.emails.joinToString(", ") { it.value },
                                                style = MaterialTheme.typography.bodyMedium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Surface(
                                                shape = CircleShape,
                                                color = MaterialTheme.colorScheme.secondaryContainer,
                                                modifier = Modifier.size(16.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        imageVector = ContactUtils.getAccountIcon(account, contact.isPrivate),
                                                        contentDescription = null,
                                                        modifier = Modifier.size(10.dp),
                                                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                                                    )
                                                }
                                            }
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                text = ContactUtils.getAccountName(account), // + "(${ContactUtils.getFriendlyAccountName(account)})",
                                                style = MaterialTheme.typography.bodySmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Button(
                        onClick = {
                            contactsViewModel.unmergeAll(contact.id)
                            onDismiss()
                            navigateBack()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset(x = 0.dp, y = (-4).dp),
                        shape = RoundedCornerShape(
                            topStart = cardCornerSmall,
                            topEnd = cardCornerSmall,
                            bottomStart = cardCornerMedium,
                            bottomEnd = cardCornerMedium
                        ),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.CallSplit, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.unlink))
                    }
                }
            }
        }
    }
}