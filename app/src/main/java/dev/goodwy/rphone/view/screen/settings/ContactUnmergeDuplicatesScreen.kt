package dev.goodwy.rphone.view.screen.settings

import android.accounts.Account
import android.content.Context
import android.view.Surface
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallSplit
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.rounded.Merge
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.controller.ContactsViewModel
import dev.goodwy.rphone.modal.data.Contact
import dev.goodwy.rphone.view.components.RillExpressiveCard
import dev.goodwy.rphone.view.components.RillListItem
import dev.goodwy.rphone.view.components.RillLoadingIndicatorView
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.ContactDetailsScreenDestination
import com.ramcosta.composedestinations.generated.destinations.ContactDetailsScreenDestination.invoke
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.goodwy.rphone.R
import dev.goodwy.rphone.cardCornerBig
import dev.goodwy.rphone.cardCornerSmall
import dev.goodwy.rphone.controller.util.ContactUtils
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.modal.data.getDisplayName
import dev.goodwy.rphone.modal.repository.ContactsRepository
import dev.goodwy.rphone.modal.repository.ContactsRepository.ContactSource
import dev.goodwy.rphone.view.components.NavigationIcon
import dev.goodwy.rphone.view.components.RillAvatar
import dev.goodwy.rphone.view.components.ScrollHapticsEffect
import dev.goodwy.rphone.view.components.Title
import dev.goodwy.rphone.view.theme.MyColors.cardColor
import dev.goodwy.rphone.view.theme.MyColors.cardColorSelected
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinActivityViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun ContactUnmergeDuplicatesScreen(
    navigator: DestinationsNavigator
) {
    val context = LocalContext.current
    val prefs = koinInject<PreferenceManager>()
    val settingsState by prefs.settingsChanged.collectAsState()
    val displayOrder = remember(settingsState) { prefs.getInt(PreferenceManager.KEY_CONTACT_DISPLAY_ORDER, 0) }
    val viewModel: ContactsViewModel = koinActivityViewModel()

    var mergedContacts by remember { mutableStateOf<List<MergedContactDisplay>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    fun loadMergedContacts() {
        val allContacts = viewModel.allContacts.value
        mergedContacts = findMergedContactsWithData(allContacts, viewModel)
        isLoading = false
    }

    LaunchedEffect(Unit) {
        loadMergedContacts()
    }

    LaunchedEffect(viewModel.allContacts) {
        if (!isLoading) {
            loadMergedContacts()
        }
    }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var visible by remember { mutableStateOf(false) }
    var isClosing by remember { mutableStateOf(false) }

    fun navigateBack() {
        isClosing = true
        scope.launch {
            delay(280)
            navigator.navigateUp()
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
        (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation
    val isRotation90 = rotation == Surface.ROTATION_90

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets.systemBars.only(
                    if (isRotation90) WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                    else WindowInsetsSides.Top
                ),
                title = { Title(stringResource(R.string.unmerging_contacts)) },
                navigationIcon = {
                    NavigationIcon(onClick = { navigateBack() })
                },
            )
        }
    ) { padding ->
        BackHandler { navigateBack() }
        ScrollHapticsEffect(listState = listState)

        if (isLoading) {
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
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val mergedToShow = mergedContacts.filter { it.isMerged }
                if (mergedToShow.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillParentMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                stringResource(R.string.no_merged_contacts_found),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    item {
                        Text(
                            stringResource(R.string.merging_contacts_description),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 16.dp)
                        )
                    }

                    items(
                        items = mergedToShow,
                        key = { it.contactId }
                    ) { merged ->
                        MergedContactCard(
                            navigator = navigator,
                            merged = merged,
                            displayOrder = displayOrder,
                            onUnmergeAll = {
                                viewModel.unmergeAll(merged.contactId)
                                mergedContacts = mergedContacts.filter { it.contactId != merged.contactId }
                            }
                        )
                    }
                }

                item { Spacer(modifier = Modifier.height(20.dp).navigationBarsPadding()) }
            }
        }
    }
}

// A class for storing data about a composite contact and its sources
data class MergedContactDisplay(
    val contactId: String,
    val displayName: String,
    val photoUri: String?,
    val sources: List<ContactSource>,
    val sourceContacts: List<Contact>, // Add a list of source contacts
    val isMerged: Boolean
)

@Composable
fun MergedContactCard(
    navigator: DestinationsNavigator,
    merged: MergedContactDisplay,
    displayOrder: Int,
    onUnmergeAll: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var isUnmerging by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isUnmerging) 0.8f else 1f,
        animationSpec = spring(
            stiffness = Spring.StiffnessMedium,
            dampingRatio = Spring.DampingRatioMediumBouncy
        ),
        label = "cardScale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (isUnmerging) 0f else 1f,
        animationSpec = tween(200),
        label = "cardAlpha"
    )

    RillExpressiveCard(
        modifier = Modifier
            .scale(scale)
            .alpha(alpha)
    ) {
        // Title - Combined Contact
        Surface(
            modifier = Modifier
                .clickable { navigator.navigate(ContactDetailsScreenDestination(contactId = merged.contactId)) },
            shape = RoundedCornerShape(cardCornerSmall),
            color = MaterialTheme.colorScheme.secondaryContainer,
            shadowElevation = 0.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val displayName = getDisplayName(
                    Contact(
                        id = merged.contactId,
                        givenName = merged.displayName,
                        photoUri = merged.photoUri
                    ),
                    displayOrder
                )
                RillAvatar(
                    name = displayName,
                    photoUri = merged.photoUri,
                    modifier = Modifier.size(42.dp)
                )

                Column(
                    modifier = Modifier.weight(1f).padding(horizontal = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.merged_from_sources, merged.sources.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Icon(
                    Icons.Default.ChevronRight, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // List of sources (the contacts that make up the combined list)
        merged.sourceContacts.forEachIndexed { index, sourceContact ->
            val account = Account(
                merged.sources[index].accountName ?: "",
                merged.sources[index].accountType ?: ""
            )

            Card(
                modifier = Modifier
//                    .clickable { }
                    .fillMaxWidth(),
                shape = RoundedCornerShape(cardCornerSmall),
                colors = CardDefaults.cardColors(containerColor = cardColor)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RillAvatar(
                        name = sourceContact.displayName,
                        photoUri = sourceContact.photoUri,
                        modifier = Modifier.size(36.dp)
                    )

                    Column(
                        modifier = Modifier.weight(1f).padding(start = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        if (sourceContact.displayName.isNotBlank()) {
                            Text(
                                text = sourceContact.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        if (sourceContact.phoneDetails.isNotEmpty()) {
                            Text(
                                text = sourceContact.phoneDetails.joinToString(", ") { it.number },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        } else if (sourceContact.emails.isNotEmpty()) {
                            Text(
                                text = sourceContact.emails.joinToString(", ") { it.value },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.size(14.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = ContactUtils.getAccountIcon(account, false),
                                        contentDescription = null,
                                        modifier = Modifier.size(9.dp),
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = ContactUtils.getAccountName(account),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }

        Button(
            onClick = {
                isUnmerging = true
                scope.launch {
                    delay(300)
                    onUnmergeAll()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .offset(x = 0.dp, y = (-4).dp),
            shape = RoundedCornerShape(
                topStart = cardCornerSmall,
                topEnd = cardCornerSmall,
                bottomStart = cardCornerBig,
                bottomEnd = cardCornerBig
            ),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ),
            enabled = !isUnmerging
        ) {
            if (isUnmerging) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(8.dp))
                Text("Unmerging...")
            } else {
                Icon(Icons.AutoMirrored.Rounded.CallSplit, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.unlink))
            }
        }
    }
}

private fun findMergedContactsWithData(
    contacts: List<Contact>,
    viewModel: ContactsViewModel
): List<MergedContactDisplay> {
    val mergedGroups = mutableListOf<MergedContactDisplay>()

    contacts.filter { it.hasMultipleSources }.forEach { contact ->
        val sources = viewModel.getContactSources(contact.id)
        if (sources.size > 1) {
            // Load the data for each source
            val sourceContacts = sources.mapNotNull { source ->
                try {
                    viewModel.getRawContactData(source.rawContactId)
                } catch (_: Exception) {
                    null
                }
            }

            mergedGroups.add(
                MergedContactDisplay(
                    contactId = contact.id,
                    displayName = contact.displayName,
                    photoUri = contact.photoUri,
                    sources = sources,
                    sourceContacts = sourceContacts,
                    isMerged = true
                )
            )
        }
    }

    return mergedGroups
}