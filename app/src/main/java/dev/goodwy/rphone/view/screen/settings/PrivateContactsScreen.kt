package dev.goodwy.rphone.view.screen.settings

import android.content.Context
import android.view.Surface
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.ContactsViewModel
import dev.goodwy.rphone.modal.data.Contact
import dev.goodwy.rphone.view.components.RillAvatar
import dev.goodwy.rphone.view.components.RillExpressiveCard
import dev.goodwy.rphone.view.components.RillLoadingIndicatorView
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.goodwy.rphone.controller.util.ContactUtils.getAccountIcon
import dev.goodwy.rphone.controller.util.toast
import dev.goodwy.rphone.view.components.NavigationIcon
import dev.goodwy.rphone.view.components.RillIconButton
import dev.goodwy.rphone.view.components.ScrollHapticsEffect
import dev.goodwy.rphone.view.components.Title
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinActivityViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun PrivateContactsScreen(
    navigator: DestinationsNavigator
) {
    val context = LocalContext.current
    val viewModel: ContactsViewModel = koinActivityViewModel()
    val allContacts by viewModel.allContacts.collectAsState()
    val privateContacts = remember(allContacts) { allContacts.filter { it.isPrivate } }
    val isLoading by viewModel.isLoading.collectAsState()

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/vcard"),
        onResult = { uri ->
            uri?.let {
                viewModel.exportPrivateContacts(it)
            }
        }
    )

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            uri?.let {
                viewModel.importPrivateContacts(it)
            }
        }
    )

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
                title = { Title(stringResource(R.string.private_contacts)) },
                navigationIcon = {
                    NavigationIcon(onClick = { navigateBack() })
                },
                actions = {
//                    IconButton(onClick = { importLauncher.launch("text/vcard") }) {
//                        Icon(Icons.Default.FileDownload, stringResource(R.string.import_text))
//                    }
//                    IconButton(onClick = { exportLauncher.launch("private_contacts.vcf") }) {
//                        Icon(Icons.Default.FileUpload, stringResource(R.string.export_text))
//                    }
                    val importText = stringResource(R.string.import_text)
                    RillIconButton(
                        onClick = { importLauncher.launch("text/vcard") },
                        imageVector = Icons.Default.FileDownload,
                        contentDescription = importText
                    )
                    val exportText = stringResource(R.string.export_text)
                    RillIconButton(
                        onClick = { exportLauncher.launch("private_contacts.vcf") },
                        imageVector = Icons.Default.FileUpload,
                        contentDescription = exportText
                    )
                }
            )
        }
    ) { padding ->
        BackHandler { navigateBack() }
        ScrollHapticsEffect(listState = listState)
        if (isLoading) {
            RillLoadingIndicatorView()
        } else if (privateContacts.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(getAccountIcon(null, true), null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.no_contacts_found), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
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
                    Text(
                        stringResource(R.string.private_contacts_subtitle),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
                    )
                }

                items(privateContacts) { contact ->
                    val textToast = stringResource(R.string.contact_moved_successfully)
                    PrivateContactCard(
                        contact = contact,
                        onMoveToPublic = {
                            viewModel.makeContactPublic(contact.id)
                            context.toast(textToast)
                        },
                        onDelete = { viewModel.deleteContact(contact.id) }
                    )
                }

                item { Spacer(modifier = Modifier.height(20.dp).navigationBarsPadding()) }
            }
        }
    }
}

@Composable
fun PrivateContactCard(
    contact: Contact,
    onMoveToPublic: () -> Unit,
    onDelete: () -> Unit
) {
    RillExpressiveCard {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).padding(start = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RillAvatar(name = contact.name, photoUri = contact.photoUri, modifier = Modifier.size(48.dp))
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(contact.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (contact.phoneNumbers.isNotEmpty()) {
                    Text(contact.phoneNumbers.first(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            
            var showMenu by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, null)
                }
                DropdownMenu(
                    shape = RoundedCornerShape(16.dp),
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        contentPadding = PaddingValues(start = 16.dp, end = 24.dp),
                        text = { Text(stringResource(R.string.move_to_device_storage)) },
                        onClick = {
                            showMenu = false
                            onMoveToPublic()
                        },
                        leadingIcon = { Icon(Icons.Default.LockOpen, null) }
                    )
                    DropdownMenuItem(
                        contentPadding = PaddingValues(start = 16.dp, end = 24.dp),
                        text = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            showMenu = false
                            onDelete()
                        },
                        leadingIcon = { Icon(ImageVector.vectorResource(id = R.drawable.ic_delete), null, tint = MaterialTheme.colorScheme.error) }
                    )
                }
            }
        }
    }
}
