package dev.goodwy.rphone.view.screen.settings

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.goodwy.rphone.R
import dev.goodwy.rphone.cardSpacedBy
import dev.goodwy.rphone.controller.util.CallBackgroundStore
import dev.goodwy.rphone.controller.util.CallingCardStore
import dev.goodwy.rphone.controller.util.CallingCardStyle
import dev.goodwy.rphone.controller.util.CallingCardStyleCodec
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.icons.background_replace
import dev.goodwy.rphone.modal.`interface`.IContactsRepository
import dev.goodwy.rphone.view.components.NavigationIcon
import dev.goodwy.rphone.view.components.PlaceholderView
import dev.goodwy.rphone.view.components.RillAvatar
import dev.goodwy.rphone.view.components.RillGroupedCardContainer
import dev.goodwy.rphone.view.components.Title
import dev.goodwy.rphone.view.components.rillGroupedItemShape
import dev.goodwy.rphone.view.screen.CallingCardEditorDialog
import dev.goodwy.rphone.view.theme.MyColors.cardColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

// ─── Model ─────────────────────────────────────────────────────────────────

private data class CallingCardOverrideEntry(
    val contactId: String? = null,
    val numberKey: String? = null,          // Only for "orphan" numbers
    val displayName: String,
    val phoneNumber: String? = null,        // What to display under the name
    val numbers: List<String> = emptyList(),// All contact numbers — for save/clear
    val photoUri: String? = null,
    val backgroundUri: String? = null,
    val style: CallingCardStyle? = null
) {
    val key: String
        get() = when {
            contactId != null -> "id:$contactId"
            numberKey != null -> "num:$numberKey"
            else -> error("entry without id or number")
        }
    val hasBackground: Boolean get() = backgroundUri != null
    val hasStyle: Boolean get() = style != null && !style.isThemeDefault
}

// ─── Screen ────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun CallingCardOverridesScreen(navigator: DestinationsNavigator) {
    val context = LocalContext.current
    val prefs = koinInject<PreferenceManager>()
    val contactsRepo = koinInject<IContactsRepository>()
    val scope = rememberCoroutineScope()
    val snackbarHost = remember { SnackbarHostState() }
    val settingsState by prefs.settingsChanged.collectAsStateWithLifecycle()

    var entries by remember { mutableStateOf<List<CallingCardOverrideEntry>?>(null) }
    var editing by remember { mutableStateOf<CallingCardOverrideEntry?>(null) }
    var deletingKey by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(settingsState) {
        entries = withContext(Dispatchers.IO) {
            loadOverrides(context, prefs, contactsRepo)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Top),
                title = { Title(stringResource(R.string.calling_card_overrides_title)) },
                navigationIcon = { NavigationIcon(onClick = { navigator.navigateUp() }) }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        Box(
            Modifier
                .padding(top = padding.calculateTopPadding())
                .fillMaxSize()
        ) {
            val list = entries
            when {
                list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                list.isEmpty() -> EmptyState()
                else -> {
                    val contacts = list.filter { it.contactId != null }
                    val numbers = list.filter { it.numberKey != null }
                    val deleted = stringResource(R.string.calling_card_override_deleted)
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(cardSpacedBy)
                    ) {
                        if (contacts.isNotEmpty()) {
                            item {
                                SettingsSectionLabel(
                                    stringResource(R.string.contacts),
                                    modifier = Modifier.padding(start = 20.dp, bottom = 6.dp)
                                )
                            }
                            itemsIndexed(contacts, key = { _, entry -> entry.key }) { index, entry ->
                                RillGroupedCardContainer(
                                    shape = rillGroupedItemShape(index, contacts.size, prefs)
                                ) {
                                    OverrideRow(
                                        entry = entry,
                                        isDeleting = deletingKey == entry.key,
                                        onClick = { editing = entry },
                                        onDelete = {
                                            scope.launch {
                                                deletingKey = entry.key
                                                deleteOverride(context, entry)
                                                deletingKey = null
                                                snackbarHost.showSnackbar(deleted)
                                            }
                                        }
                                    )
                                }
                                if (index == contacts.size - 1) Spacer(modifier = Modifier.height(14.dp))
                            }
                        }
                        if (numbers.isNotEmpty()) {
                            item {
                                SettingsSectionLabel(
                                    stringResource(R.string.calling_card_overrides_numbers),
                                    modifier = Modifier.padding(start = 20.dp, bottom = 6.dp)
                                )
                            }
                            itemsIndexed(numbers, key = { _, entry -> entry.key }) { index, entry ->
                                RillGroupedCardContainer(
                                    shape = rillGroupedItemShape(index, numbers.size, prefs)
                                ) {
                                    OverrideRow(
                                        entry = entry,
                                        isDeleting = deletingKey == entry.key,
                                        onClick = { editing = entry },
                                        onDelete = {
                                            scope.launch {
                                                deletingKey = entry.key
                                                deleteOverride(context, entry)
                                                deletingKey = null
                                                snackbarHost.showSnackbar(deleted)
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        item { SettingsBottomPadding() }
                    }
                }
            }
        }
    }

    val saved = stringResource(R.string.calling_card_override_saved)
    val failed = stringResource(R.string.calling_card_override_save_failed)
    editing?.let { entry ->
        CallingCardEditorDialog(
            contactName = entry.displayName,
            phoneNumber = entry.phoneNumber ?: "",
            contactPhotoUri = entry.photoUri,
            initialBackgroundUri = entry.backgroundUri,
            inheritedBackgroundUri = null,
            initialStyle = entry.style ?: CallingCardStyle.ThemeDefault,
            fallbackStyle = CallingCardStyle.ThemeDefault,
            onDismiss = { editing = null },
            onDone = { result ->
                editing = null
                scope.launch {
                    val ok = saveOverride(context, entry, result.backgroundUri, result.style)
                    snackbarHost.showSnackbar(if (ok) saved else failed)
                }
            }
        )
    }
}

// ─── UI parts ──────────────────────────────────────────────────────────────

@Composable
private fun EmptyState() {
    PlaceholderView(
        icon = background_replace,
        title = stringResource(R.string.calling_card_overrides_empty),
    )
}

@Composable
private fun OverrideRow(
    entry: CallingCardOverrideEntry,
    isDeleting: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .background(color = cardColor)
            .fillMaxWidth()
            .clickable(enabled = !isDeleting, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(MaterialTheme.shapes.large)
                .background(cardColor),
            contentAlignment = Alignment.Center
        ) {
            when {
                entry.backgroundUri != null -> AsyncImage(
                    model = entry.backgroundUri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                entry.photoUri != null -> AsyncImage(
                    model = entry.photoUri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                else -> RillAvatar(
                    name = entry.displayName,
                    photoUri = null,
                    modifier = Modifier.size(56.dp),
                    shape = CircleShape
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = entry.displayName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!entry.phoneNumber.isNullOrBlank() &&
                entry.phoneNumber != entry.displayName
            ) {
                Text(
                    text = entry.phoneNumber,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (entry.hasBackground) Badge(
                    icon = Icons.Rounded.Image,
                    text = stringResource(R.string.calling_card_override_badge_background)
                )
                if (entry.hasStyle) Badge(
                    icon = Icons.Rounded.Palette,
                    text = stringResource(R.string.calling_card_override_badge_style)
                )
            }
        }

        if (isDeleting) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp))
        } else {
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = ImageVector.vectorResource(id = R.drawable.ic_delete),
                    contentDescription = stringResource(R.string.calling_card_action_remove),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun Badge(icon: ImageVector, text: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(4.dp))
            Text(text, style = MaterialTheme.typography.labelSmall)
        }
    }
}

// ─── Data loading ──────────────────────────────────────────────────────────

private suspend fun loadOverrides(
    context: Context,
    prefs: PreferenceManager,
    contactsRepo: IContactsRepository
): List<CallingCardOverrideEntry> = withContext(Dispatchers.IO) {
    // ── id-ключи ──
    val bgById = prefs.getContactBackgroundEntries()
        .filterKeys {
            !it.startsWith(PreferenceManager.CONTACT_BACKGROUND_NUMBER_PREFIX) &&
                    !it.endsWith("_default")
        }
        .mapKeys { it.key.removePrefix(PreferenceManager.CONTACT_BACKGROUND_PREFIX) }
    val styleById = prefs.getCallingCardStyleEntries()
        .filterKeys {
            !it.startsWith(PreferenceManager.CALLING_CARD_STYLE_NUMBER_PREFIX) &&
                    !it.endsWith("_default")
        }
        .mapKeys { it.key.removePrefix(PreferenceManager.CALLING_CARD_STYLE_PREFIX) }

    // ── ID keys + numbers associated with contacts that have that ID key ──
    val idKeys = (bgById.keys + styleById.keys).toSet()
    val numberKeysOwnedByIdKeyContacts = mutableSetOf<String>()
    idKeys.forEach { id ->
        val contact = runCatching { contactsRepo.getContactById(id) }.getOrNull() ?: return@forEach
        numberKeysOwnedByIdKeyContacts += CallBackgroundStore.numberKeys(contact.phoneNumbers)
    }

    val result = mutableListOf<CallingCardOverrideEntry>()

    // ── Contacts (ID only) ──
    idKeys.forEach { id ->
        val contact = runCatching { contactsRepo.getContactById(id) }.getOrNull()
        val numbers = contact?.phoneNumbers.orEmpty()
        val background = bgById[id]
            ?: CallBackgroundStore.peekStored(context, id, numbers)
        val style = styleById[id]?.let { CallingCardStyleCodec.decode(it) }
            ?: CallingCardStore.peek(context, id, numbers)
        result += CallingCardOverrideEntry(
            contactId = id,
            displayName = contact?.displayName ?: context.getString(R.string.calling_card_override_unknown_contact, id),
            phoneNumber = numbers.firstOrNull(),
            numbers = numbers,
            photoUri = contact?.photoUri,
            backgroundUri = background,
            style = style?.takeIf { !it.isThemeDefault }
        )
    }

    // ── Numbered Keys ──
    val bgByNumber = prefs.getContactBackgroundNumberEntries()
    val styleByNumber = prefs.getCallingCardStyleNumberEntries()

    // ── Phone numbers (excluding those belonging to contacts with an ID key) ──
    val numberKeys = (bgByNumber.keys + styleByNumber.keys).toSet()
    numberKeys.forEach { numberKey ->
        if (numberKey in numberKeysOwnedByIdKeyContacts) return@forEach

        val contact = runCatching { contactsRepo.getContactByNumber(numberKey) }.getOrNull()
        val background = bgByNumber[numberKey]
            ?: CallBackgroundStore.peekStored(context, contact?.id, listOf(numberKey))
        val style = styleByNumber[numberKey]?.let { CallingCardStyleCodec.decode(it) }
            ?: CallingCardStore.peek(context, contact?.id, listOf(numberKey))
        result += CallingCardOverrideEntry(
            numberKey = numberKey,
            displayName = contact?.displayName ?: numberKey,
            phoneNumber = numberKey,
            numbers = listOf(numberKey),
            photoUri = contact?.photoUri,
            backgroundUri = background,
            style = style?.takeIf { !it.isThemeDefault }
        )
    }

    result
        .filter { it.hasBackground || it.hasStyle }
        .distinctBy { it.key }
        .sortedBy { it.displayName.lowercase() }
}

// ─── Save / delete ─────────────────────────────────────────────────────────

private suspend fun deleteOverride(context: Context, entry: CallingCardOverrideEntry) {
    CallBackgroundStore.clearBlocking(context, entry.contactId, entry.numbers)
    CallingCardStore.clearBlocking(context, entry.contactId, entry.numbers)
}

private suspend fun saveOverride(
    context: Context,
    entry: CallingCardOverrideEntry,
    newBackgroundUri: String?,
    newStyle: CallingCardStyle?
): Boolean = withContext(Dispatchers.IO) {
    var ok = true

    if (newBackgroundUri != entry.backgroundUri) {
        ok = if (newBackgroundUri == null) {
            CallBackgroundStore.clearBlocking(context, entry.contactId, entry.numbers)
            true
        } else {
            CallBackgroundStore.save(
                context, entry.contactId, entry.numbers, newBackgroundUri.toUri()
            )
        }
    }

    if (newStyle == null || newStyle.isThemeDefault) {
        CallingCardStore.clearBlocking(context, entry.contactId, entry.numbers)
    } else if (newStyle != entry.style) {
        CallingCardStore.save(context, entry.contactId, entry.numbers, newStyle)
    }

    ok
}