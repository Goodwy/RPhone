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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallSplit
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.Handyman
import androidx.compose.material.icons.rounded.Merge
import androidx.compose.material.icons.rounded.PeopleAlt
import androidx.compose.material.icons.rounded.SortByAlpha
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
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
import com.ramcosta.composedestinations.generated.destinations.ContactMergeDuplicatesScreenDestination
import com.ramcosta.composedestinations.generated.destinations.ContactUnmergeDuplicatesScreenDestination
import com.ramcosta.composedestinations.generated.destinations.ContactVisibilityScreenDestination
import com.ramcosta.composedestinations.generated.destinations.PrivateContactsScreenDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.goodwy.rphone.R
import dev.goodwy.rphone.cardCornerBig
import dev.goodwy.rphone.cardCornerSmall
import dev.goodwy.rphone.controller.util.ContactUtils.getAccountIcon
import dev.goodwy.rphone.view.components.NavigationIcon
import dev.goodwy.rphone.view.components.RillAnimatedSection
import dev.goodwy.rphone.view.components.RillSelectListItem
import dev.goodwy.rphone.view.components.ScrollHapticsEffect
import dev.goodwy.rphone.view.components.Title
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
                title = { Title(stringResource(R.string.manage_contacts)) },
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
                                headline = stringResource(R.string.managing_contact_sources),
                                supporting = stringResource(R.string.managing_contact_sources_subtitle),
                                leadingIcon = Icons.Rounded.PeopleAlt,
                                iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkBlue,
                                iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorBlue,
                                trailingIcon = Icons.Default.ChevronRight,
                                onClick = { navigator.navigate(ContactVisibilityScreenDestination) }
                            )
                            RillListItem(
                                headline = stringResource(R.string.private_contacts),
                                supporting = stringResource(R.string.private_contacts_subtitle),
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
                            SettingsSectionLabel(stringResource(R.string.display))
                            RillExpressiveCard {
                                RillSelectListItem(
                                    headline = stringResource(R.string.sort_by),
                                    leadingIcon = Icons.Rounded.SortByAlpha,
                                    iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkIndigo,
                                    iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorIndigo,
                                    options = listOf(
                                        stringResource(R.string.first_name) to 0,
                                        stringResource(R.string.last_name) to 1
                                    ),
                                    selectedValue = sortOrder,
                                    onValueChange = { newValue: Int -> viewModel.setSortOrder(newValue) }
                                )
                                RillSelectListItem(
                                    headline = stringResource(R.string.name_format),
                                    leadingIcon = Icons.Rounded.Badge,
                                    iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkIndigo,
                                    iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorIndigo,
                                    options = listOf(
                                        stringResource(R.string.first_name_first) to 0,
                                        stringResource(R.string.last_name_first) to 1
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
                            SettingsSectionLabel(stringResource(R.string.merge_and_fix))
                            RillExpressiveCard {
                                RillListItem(
                                    headline = stringResource(R.string.standardize_phone_numbers),
                                    supporting = stringResource(R.string.standardize_phone_numbers_subtitle),
                                    leadingIcon = Icons.Rounded.Handyman,
                                    iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkPink,
                                    iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorPink,
                                    trailingIcon = Icons.Rounded.AutoFixHigh,
                                    onClick = {
                                        viewModel.previewStandardize { stats ->
                                            standardizeStats = stats
                                            showStandardizeDialog = true
                                        }
                                    }
                                )
                                RillListItem(
                                    headline = stringResource(R.string.merging_contacts),
                                    supporting = stringResource(R.string.merging_contacts_subtitle),
                                    leadingIcon = Icons.Rounded.Merge,
                                    iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkOrange,
                                    iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorOrange,
                                    trailingIcon = Icons.Default.ChevronRight,
                                    onClick = { navigator.navigate(ContactMergeDuplicatesScreenDestination) }
                                )
                                RillListItem(
                                    headline = stringResource(R.string.unmerging_contacts),
                                    supporting = stringResource(R.string.unmerging_contacts_subtitle),
                                    leadingIcon = Icons.AutoMirrored.Rounded.CallSplit,
                                    iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkOrange,
                                    iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorOrange,
                                    trailingIcon = Icons.Default.ChevronRight,
                                    onClick = { navigator.navigate(ContactUnmergeDuplicatesScreenDestination) }
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
//                            modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
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

                item { Spacer(modifier = Modifier.height(20.dp).navigationBarsPadding()) }
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
                headline = contact.displayName,
                supporting = contact.phoneNumbers.joinToString(", "),
                avatarName = contact.displayName,
                photoUri = contact.photoUri,
                onClick = { }
            )
        }

        Button(
            onClick = { onMerge(group.first(), group.drop(1)) },
            modifier = Modifier
                .fillMaxWidth()
                .offset(x = 0.dp, y = (-4).dp),
            shape = RoundedCornerShape(
                topStart = cardCornerSmall,
                topEnd = cardCornerSmall,
                bottomStart = cardCornerBig,
                bottomEnd = cardCornerBig
            )
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
                stringResource(R.string.standardize_phone_numbers),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    stringResource(R.string.standardize_phone_numbers_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatRow(
                        label = stringResource(R.string.total_contacts),
                        value = stats.totalContacts.toString()
                    )
                    StatRow(
                        label = stringResource(R.string.contacts_with_changes),
                        value = stats.contactsWithChanges.toString()
                    )
                    StatRow(
                        label = stringResource(R.string.numbers_to_standardize),
                        value = stats.totalNumbersChanged.toString()
                    )
                }

                if (stats.contactsWithChanges == 0) {
                    Text(
                        stringResource(R.string.all_numbers_are_already_standardized),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }

                if (stats.examples.isNotEmpty()) {
                    HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)

                    Text(
                        stringResource(R.string.examples_of_changes),
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
                                        text = "=>",
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
                            stringResource(R.string.and_more, "${stats.totalNumbersChanged - stats.examples.size}"),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if (stats.contactsWithChanges > 0) {
                    Text(
                        stringResource(R.string.warning_cannot_be_undone),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 6.dp)
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
                Text(stringResource(R.string.standardize))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
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
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
            fontWeight = FontWeight.Medium
        )
    }
}