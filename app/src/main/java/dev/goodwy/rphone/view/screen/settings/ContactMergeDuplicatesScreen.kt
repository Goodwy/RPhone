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
import androidx.compose.material.icons.automirrored.rounded.CallSplit
import androidx.compose.material.icons.rounded.Merge
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.controller.ContactsViewModel
import dev.goodwy.rphone.modal.data.Contact
import dev.goodwy.rphone.view.components.RillExpressiveCard
import dev.goodwy.rphone.view.components.RillLoadingIndicatorView
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.goodwy.rphone.R
import dev.goodwy.rphone.cardCornerBig
import dev.goodwy.rphone.cardCornerSmall
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.view.components.ContactListItem
import dev.goodwy.rphone.view.components.NavigationIcon
import dev.goodwy.rphone.view.components.ScrollHapticsEffect
import dev.goodwy.rphone.view.components.Title
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinActivityViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun ContactMergeDuplicatesScreen(
    navigator: DestinationsNavigator
) {
    val context = LocalContext.current
    val prefs = koinInject<PreferenceManager>()
    val settingsState by prefs.settingsChanged.collectAsState()
    val displayOrder = remember(settingsState) { prefs.getInt(PreferenceManager.KEY_CONTACT_DISPLAY_ORDER, 0) }
    val viewModel: ContactsViewModel = koinActivityViewModel()
    var duplicateGroups by remember { mutableStateOf<List<List<Contact>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

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
                title = { Title(stringResource(R.string.merging_contacts)) },
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
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (duplicateGroups.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.no_duplicates_found), style = MaterialTheme.typography.bodyLarge)
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
                        items = duplicateGroups,
                        key = { it.first().id }
                    ) { group ->
                        DuplicateGroupCard(
                            group = group,
                            navigator = navigator,
                            displayOrder = displayOrder,
                            onMerge = { target, sources ->
                                viewModel.mergeContacts(target.id, sources.map { it.id })
                                duplicateGroups = duplicateGroups.filter { it != group }
                            }
                        )
                    }
                }

                item { Spacer(modifier = Modifier.height(20.dp).navigationBarsPadding()) }
            }
        }
    }
}

@Composable
fun DuplicateGroupCard(
    group: List<Contact>,
    navigator: DestinationsNavigator,
    displayOrder: Int,
    onMerge: (Contact, List<Contact>) -> Unit
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
        group.forEachIndexed { index, contact ->
//            val displayName = getDisplayName(contact, displayOrder)
//            RillListItem(
//                headline = displayName,
//                supporting = if (contact.phoneNumbers.isNotEmpty()) contact.phoneNumbers.joinToString(", ") else null,
//                avatarName = displayName,
//                photoUri = contact.photoUri,
//                onClick = { }
//            )
            Surface(
                shape = RoundedCornerShape(cardCornerSmall),
                color = Color.Transparent,
            ) {
                ContactListItem(
                    contact = contact,
                    navigator = navigator,
                    displayOrder = displayOrder,
                )
            }
        }

        Button(
            onClick = {
                // Target contact—first in the group
//                onMerge(group.first(), group.drop(1))

                isUnmerging = true
                scope.launch {
                    delay(300)
                    onMerge(group.first(), group.drop(1))
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
                Icon(Icons.Rounded.Merge, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.merge_duplicates, group.size))
            }
        }
    }
}