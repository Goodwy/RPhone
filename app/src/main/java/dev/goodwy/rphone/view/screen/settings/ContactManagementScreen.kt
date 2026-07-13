package dev.goodwy.rphone.view.screen.settings

import android.content.Context
import android.view.Surface
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.Merge
import androidx.compose.material.icons.rounded.PeopleAlt
import androidx.compose.material.icons.rounded.SortByAlpha
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.controller.ContactsViewModel
import dev.goodwy.rphone.modal.data.Contact
import dev.goodwy.rphone.view.components.RillDialog
import dev.goodwy.rphone.view.components.RillExpressiveCard
import dev.goodwy.rphone.view.components.RillListItem
import dev.goodwy.rphone.view.components.RillLoadingIndicatorView
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.ContactVisibilityScreenDestination
import com.ramcosta.composedestinations.generated.destinations.PrivateContactsScreenDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.ContactUtils.getAccountIcon
import dev.goodwy.rphone.view.components.NavigationIcon
import dev.goodwy.rphone.view.components.RillAnimatedSection
import dev.goodwy.rphone.view.components.RillSelectListItem
import dev.goodwy.rphone.view.components.ScrollHapticsEffect
import dev.goodwy.rphone.view.theme.customColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinActivityViewModel

data class StandardizeStats(
    val totalContacts: Int,
    val contactsWithChanges: Int,
    val totalNumbersChanged: Int,
    val examples: List<NumberChangeExample>
)

data class NumberChangeExample(
    val contactName: String,
    val oldNumber: String,
    val newNumber: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun ContactManagementScreen(
    navigator: DestinationsNavigator
) {
    val context = LocalContext.current
    val viewModel: ContactsViewModel = koinActivityViewModel()
    var duplicateGroups by remember { mutableStateOf<List<List<Contact>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val standardizeProgress by viewModel.standardizeProgress.collectAsState()

    if (standardizeProgress != null) {
        RillDialog(
            onDismissRequest = {},
            title = "Standardizing numbers",
            icon = Icons.Rounded.FormatListNumbered
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            val progress = standardizeProgress ?: 0f
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "${(progress * 100).toInt()}% completed",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.End)
            )
        }
    }

    var showStandardizeDialog by remember { mutableStateOf(false) }
    var standardizeStats by remember { mutableStateOf<StandardizeStats?>(null) }

    if (showStandardizeDialog && standardizeStats != null) {
        StandardizeConfirmationDialog(
            stats = standardizeStats!!,
            onConfirm = {
                viewModel.formatAllPhoneNumbers()
                showStandardizeDialog = false
            },
            onDismiss = {
                showStandardizeDialog = false
                standardizeStats = null
            }
        )
    }

    LaunchedEffect(Unit) {
        viewModel.findDuplicates { groups ->
            duplicateGroups = groups
            isLoading = false
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
        (context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager).defaultDisplay.rotation
    val isRotation90 = rotation == Surface.ROTATION_90
    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets.systemBars.only(
                    if (isRotation90) WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                    else WindowInsetsSides.Top
                ),
                title = { Text("Manage Contacts", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = {
                    NavigationIcon(onClick = { navigateBack() })
                }
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
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    RillAnimatedSection(delayMs = 30L) {
                        RillExpressiveCard {
                            RillListItem(
                                headline = "Contact Visibility",
                                supporting = "Choose which accounts to show in your list",
                                leadingIcon = Icons.Rounded.PeopleAlt,
                                iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkBlue,
                                iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorBlue,
                                trailingIcon = Icons.Default.ChevronRight,
                                onClick = { navigator.navigate(ContactVisibilityScreenDestination) }
                            )
                            RillListItem(
                                headline = "Private Contacts",
                                supporting = "Manage contacts stored only in this app",
                                leadingIcon = getAccountIcon(null, true),
                                iconContainerColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                iconBgContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                trailingIcon = Icons.Default.ChevronRight,
                                onClick = { navigator.navigate(PrivateContactsScreenDestination) }
                            )
                        }
                    }
                }

                item {
                    val sortOrder by viewModel.sortOrder.collectAsState()
                    val displayOrder by viewModel.displayOrder.collectAsState()
                    RillAnimatedSection(delayMs = 60L) {
                        Column {
                            SettingsSectionLabel("Display")
                            RillExpressiveCard {
                                RillSelectListItem(
                                    headline = "Sort by",
//                                    supporting = "Choose how contacts are ordered",
                                    leadingIcon = Icons.Rounded.SortByAlpha,
                                    iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkBlue,
                                    iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorBlue,
                                    options = listOf(
                                        "First Name" to 0,
                                        "Last Name" to 1
                                    ),
                                    selectedValue = sortOrder,
                                    onValueChange = { newValue: Int -> viewModel.setSortOrder(newValue) }
                                )
                                RillSelectListItem(
                                    headline = "Name format",
//                                    supporting = "Choose how names are displayed",
                                    leadingIcon = Icons.Rounded.Badge,
                                    iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkBlue,
                                    iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorBlue,
                                    options = listOf(
                                        "First name first" to 0,
                                        "Last name first" to 1
                                    ),
                                    selectedValue = displayOrder,
                                    onValueChange = { newValue: Int -> viewModel.setDisplayOrder(newValue) }
                                )
                            }
                        }
                    }
                }

                item {
                    RillAnimatedSection(delayMs = 90L) {
                        Column {
                            SettingsSectionLabel("Quick Fixes")
                            RillExpressiveCard {
                                RillListItem(
                                    headline = "Standardize phone numbers",
                                    supporting = "Remove spaces and special characters from all numbers",
                                    leadingIcon = Icons.Rounded.FormatListNumbered,
                                    iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkOrange,
                                    iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorOrange,
                                    trailingIcon = Icons.Rounded.AutoFixHigh,
                                    onClick = {
                                        viewModel.previewStandardize { stats ->
                                            standardizeStats = stats
                                            showStandardizeDialog = true
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

//                if (duplicateGroups.isEmpty()) {
//                    item {
//                        Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
//                            Text("No duplicates found.", style = MaterialTheme.typography.bodyLarge)
//                        }
//                    }
//                } else {
//                    item {
//                        Text(
//                            "Found ${duplicateGroups.size} groups of duplicates",
//                            style = MaterialTheme.typography.titleMedium,
//                            fontWeight = FontWeight.Bold,
//                            modifier = Modifier.padding(start = 8.dp)
//                        )
//                    }
//
//                    items(duplicateGroups) { group ->
//                        DuplicateGroupCard(group) { target, sources ->
//                            viewModel.mergeContacts(target.id, sources.map { it.id })
//                            // Refresh list locally for better UX
//                            duplicateGroups = duplicateGroups.filter { it != group }
//                        }
//                    }
//                }
            }
        }
    }
}

@Composable
fun DuplicateGroupCard(
    group: List<Contact>,
    onMerge: (Contact, List<Contact>) -> Unit
) {
    RillExpressiveCard {
        group.forEachIndexed { index, contact ->
            RillListItem(
                headline = contact.name,
                supporting = contact.phoneNumbers.joinToString(", "),
                avatarName = contact.name,
                photoUri = contact.photoUri,
                onClick = { }
            )
            if (index < group.size - 1) {
                HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Button(
            onClick = { onMerge(group.first(), group.drop(1)) },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium
        ) {
            Icon(Icons.Rounded.Merge, null)
            Spacer(Modifier.width(8.dp))
            Text("Merge duplicates")
        }
    }
}

@Composable
fun StandardizeConfirmationDialog(
    stats: StandardizeStats,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Standardize Phone Numbers",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "This will remove all spaces and special characters from phone numbers.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Divider()

                // Статистика
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    StatRow(
                        label = "Total contacts",
                        value = stats.totalContacts.toString()
                    )
                    StatRow(
                        label = "Contacts with changes",
                        value = stats.contactsWithChanges.toString()
                    )
                    StatRow(
                        label = "Numbers to standardize",
                        value = stats.totalNumbersChanged.toString()
                    )
                }

                if (stats.contactsWithChanges == 0) {
                    Text(
                        "✅ All numbers are already standardized!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (stats.examples.isNotEmpty()) {
                    Divider()

                    Text(
                        "Examples of changes:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium
                    )

                    stats.examples.forEach { example ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = example.contactName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = example.oldNumber,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "→",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = example.newNumber,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                    if (stats.totalNumbersChanged > stats.examples.size) {
                        Text(
                            "And ${stats.totalNumbersChanged - stats.examples.size} more...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (stats.contactsWithChanges > 0) {
                    Text(
                        "⚠️ This action cannot be undone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = stats.contactsWithChanges > 0,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Standardize")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun StatRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}