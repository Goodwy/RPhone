package dev.goodwy.rphone.view.screen.settings

import android.content.Context
import android.content.res.Configuration
import android.view.Surface
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.rounded.Voicemail
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.goodwy.rphone.R
import dev.goodwy.rphone.cardSpacedBy
import dev.goodwy.rphone.controller.ContactsViewModel
import dev.goodwy.rphone.controller.util.ContactUtils.getPhoneNumber
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.controller.util.formatPhoneNumber
import dev.goodwy.rphone.controller.util.getSystemVoicemailNumbers
import dev.goodwy.rphone.controller.util.toast
import dev.goodwy.rphone.modal.data.Contact
import dev.goodwy.rphone.modal.data.ContactPhoneDetail
import dev.goodwy.rphone.view.components.*
import dev.goodwy.rphone.view.theme.MyColors.cardColor
import dev.goodwy.rphone.view.theme.RillShapeDefaults
import dev.goodwy.rphone.view.theme.customColors
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinActivityViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun SpeedDialScreen(
    navigator: DestinationsNavigator
) {
    val context = LocalContext.current
    val prefs = koinInject<PreferenceManager>()
    val contactsVM: ContactsViewModel = koinActivityViewModel()
    val allContacts by contactsVM.allContacts.collectAsState()
    val settingsState by prefs.settingsChanged.collectAsState()

    var speedDialEnabled by remember(settingsState) { 
        mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_SPEED_DIAL, true)) 
    }

    var showNumberPicker by remember { mutableStateOf(false) }
    var showContactPicker by remember { mutableStateOf<Int?>(null) }

    val voicemailNumbers = getSystemVoicemailNumbers(context)

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
                title = { Title(stringResource(R.string.settings_speed_dial_title)) },
                navigationIcon = {
                    NavigationIcon(onClick = { navigator.navigateUp() })
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(
                    top = padding.calculateTopPadding(),
                    start = 0.dp,
                    end = 0.dp,
                    bottom = 0.dp
                )
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(cardSpacedBy)
        ) {
            item {
                RillAnimatedSection(delayMs = 30L) {
                    RillExpressiveCard {
                        RillSwitchListItem(
                            headline = stringResource(R.string.settings_speed_dial_enable),
                            supporting = stringResource(R.string.settings_speed_dial_enable_supporting),
                            leadingIcon = Icons.Default.Dialpad,
                            iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkIndigo,
                            iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorIndigo,
                            checked = speedDialEnabled,
                            onCheckedChange = {
                                speedDialEnabled = it
                                prefs.setBoolean(PreferenceManager.KEY_SPEED_DIAL, it)
                            }
                        )
                    }
                }
            }

            item {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    SettingsSectionLabel(
                        stringResource(R.string.settings_speed_dial_assignments_header),
                        modifier = Modifier.padding(start = 20.dp, bottom = 6.dp)
                    )
                }
            }

            items(9) { index ->
                RillAnimatedSection(delayMs = index * 100L) {
                    val key = index + 1
                    val mapping = prefs.getString("speed_dial_$key", null)
                    val parts = mapping?.split("|")
                    val name = parts?.getOrNull(0)
                    val number = parts?.getOrNull(1)

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

                    val interactionSource = remember { MutableInteractionSource() }
                    val isPressed by interactionSource.collectIsPressedAsState()
                    val scale by animateFloatAsState(
                        targetValue = if (isPressed) 0.96f else 1f,
                        animationSpec = spring(stiffness = Spring.StiffnessMedium),
                        label = "SwitchItemScale"
                    )

                    RillGroupedCardContainer(
                        shape = rillGroupedItemShape(index, 9, prefs),
                        modifier = Modifier.scale(scale)
                    ) {
                        Row(
                            modifier = Modifier
                                .clickable(
                                    onClick = { showContactPicker = key },
                                    interactionSource = interactionSource
                                )
                                .fillMaxWidth()
                                .padding(vertical = 10.dp)
                                .padding(start = 16.dp, end = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier
                                    .size(62.dp, 44.dp)
                                    .scale(iconScale)
                                    .alpha(iconAlpha),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = key.toString(),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = name ?: stringResource(R.string.settings_speed_dial_not_assigned),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (name == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                                )
                                if (number != null) {
                                    Text(
                                        text = number,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (mapping != null) {
                                IconButton(onClick = {
                                    prefs.setString("speed_dial_$key", null)
                                }) {
                                    Icon(ImageVector.vectorResource(id = R.drawable.ic_delete), contentDescription = stringResource(R.string.clear), tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }

            item { SettingsBottomPadding(120.dp) }
        }
    }

    var pendingContact by remember { mutableStateOf<Contact?>(null) }
    if (showContactPicker != null) {
        val noNumberText = stringResource(R.string.settings_speed_dial_no_number)
        ContactPickerDialog(
            voicemailNumbers = voicemailNumbers,
            contacts = allContacts,
            onDismissRequest = { showContactPicker = null },
            onContactSelected = { contact ->
                val numbers = contact.phoneNumbers
                if (numbers.size == 1) {
                    val number = contact.phoneNumbers.firstOrNull()
                    if (number != null) {
                        prefs.setString("speed_dial_${showContactPicker!!}", "${contact.displayName}|$number")
                    }
                    showContactPicker = null
                } else if (numbers.size > 1) {
                    pendingContact = contact
                    showNumberPicker = true
                } else {
                    context.toast(noNumberText)
                    showContactPicker = null
                }
            }
        )
    }
    if (showNumberPicker && pendingContact != null) {
        NumberPickerDialog(
            items = pendingContact!!.phoneDetails.map { NumberPickerItem(it.number, it.isPrimary) },
            onDismissRequest = { showNumberPicker = false },
            onNumberSelected = {
                prefs.setString("speed_dial_${showContactPicker!!}", "${pendingContact!!.displayName}|$it")
                showNumberPicker = false
                showContactPicker = null
                pendingContact = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactPickerDialog(
    voicemailNumbers: Set<String>,
    contacts: List<Contact>,
    onDismissRequest: () -> Unit,
    onContactSelected: (Contact) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredContacts = remember(searchQuery, contacts) {
        if (searchQuery.isBlank()) contacts.sortedBy { it.displayName.lowercase() }
        else contacts.filter {
            it.displayName.contains(searchQuery, ignoreCase = true) ||
                    it.nickname.contains(searchQuery, ignoreCase = true) ||
                    it.company.contains(searchQuery, ignoreCase = true) ||
                    it.jobTitle.contains(searchQuery, ignoreCase = true) ||
                    it.phoneNumbers.any { number -> number.replace(" ", "").replace("-", "").contains(searchQuery.replace(" ", "").replace("-", "")) } ||
                    it.emails.any { email -> email.value.replace(" ", "").contains(searchQuery.replace(" ", "")) } ||
                    it.addresses.any { address -> address.formattedAddress.replace(" ", "").contains(searchQuery.replace(" ", "")) } ||
                    it.events.any { event -> event.date.replace(" ", "").replace("-", "").replace(".", "")
                        .contains(searchQuery.replace(" ", "").replace("-", "").replace(".", "")) }
        }.sortedBy { it.displayName.lowercase() }
    }

    val prefs = koinInject<PreferenceManager>()
    val cardCorner  = remember { prefs.getInt(PreferenceManager.KEY_CARD_ROUNDNESS, RillShapeDefaults.DefaultRoundness) }
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = MaterialTheme.shapes.extraLarge.copy(bottomStart = CornerSize(0.dp), bottomEnd = CornerSize(0.dp)),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 4.dp,
        scrimColor = Color.Transparent,
        contentWindowInsets = {
            if (isLandscape) {
                WindowInsets.systemBars.only(
                    WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                )
            } else BottomSheetDefaults.windowInsets
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
                                stringResource(R.string.settings_speed_dial_assignments_header),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        },
                    )
                    // Search bar
                    val shape = if (cardCorner > 12) CircleShape else MaterialTheme.shapes.extraExtraLarge
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text(stringResource(R.string.search_contacts)) },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null,
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
                                IconButton(onClick = { searchQuery = "" }, modifier = Modifier.padding(end = 4.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.clear))
                                }
                            }
                        },
                        singleLine = true,
                        shape = shape,
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
            Box(Modifier
                .fillMaxSize()
                .padding(
                    top = padding.calculateTopPadding(),
                    start = 0.dp,
                    end = 0.dp,
                    bottom = 0.dp
                )
            ) {
                if (contacts.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (filteredContacts.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.SearchOff, contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                            Text(stringResource(R.string.no_contacts_found), color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                } else {
                    Surface(
                        color = Color.Transparent,
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .padding(top = 8.dp)
                            .fillMaxSize(),
                        shape = MaterialTheme.shapes.extraLarge,
                    ) {
                        LazyColumn(
                            contentPadding = PaddingValues(bottom = 96.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            if (voicemailNumbers.isNotEmpty()) itemsIndexed(
                                items = voicemailNumbers.toList(),
                                key = { _, voicemailNumber -> voicemailNumber },
                                contentType = { _, _ -> "voicemailNumber" }
                            ) { index, voicemailNumber ->
                                val contact = Contact(
                                    id = index.toString(),
                                    middleName = stringResource(R.string.settings_voicemail_title),
                                    phoneNumbers = listOf(voicemailNumber),
                                    phoneDetails = listOf(ContactPhoneDetail(number = voicemailNumber))
                                )
                                RillGroupedCardContainer(
                                    shape = rillGroupedItemShape(index, voicemailNumbers.size, prefs)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable(
                                                onClick = {
                                                    onContactSelected(contact)
                                                    onDismissRequest()
                                                }
                                            )
                                            .padding(horizontal = 16.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                                    ) {
                                        RillAvatar(
                                            name = contact.phoneNumbers.firstOrNull() ?: contact.displayName,
                                            modifier = Modifier.size(46.dp),
                                            icon = Icons.Rounded.Voicemail,
//                                            iconContainerColor = MaterialTheme.colorScheme.primary
                                        )
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = contact.displayName.ifBlank { "Unknown" },
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.Normal,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            val primaryNumber = getPhoneNumber(contact) ?: ""
                                            Text(
                                                text = formatPhoneNumber(primaryNumber),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                                if (index == voicemailNumbers.size - 1) Spacer(modifier = Modifier.height(14.dp))
                            }

                            itemsIndexed(
                                items = filteredContacts,
                                key = { _, contact -> contact.id },
                                contentType = { _, _ -> "contact" }
                            ) { index, contact ->
                                RillGroupedCardContainer(
                                    shape = rillGroupedItemShape(index, filteredContacts.size, prefs)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable(
                                                onClick = {
                                                    onContactSelected(contact)
                                                    if (contact.phoneNumbers.size < 2) onDismissRequest()
                                                }
                                            )
                                            .padding(horizontal = 16.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                                    ) {
                                        RillAvatar(
                                            name = contact.displayName,
                                            photoUri = contact.photoUri,
                                            modifier = Modifier.size(46.dp)
                                        )
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = contact.displayName.ifBlank { "Unknown" },
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.Normal,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            val primaryNumber = getPhoneNumber(contact) ?: ""
                                            Text(
                                                text = if (contact.phoneNumbers.isEmpty()) stringResource(
                                                    R.string.settings_speed_dial_no_number
                                                )
                                                else if (contact.phoneNumbers.size > 1) stringResource(
                                                    R.string.plus_more,
                                                    formatPhoneNumber(primaryNumber),
                                                    contact.phoneNumbers.size - 1
                                                )
                                                else formatPhoneNumber(primaryNumber),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1
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
}
