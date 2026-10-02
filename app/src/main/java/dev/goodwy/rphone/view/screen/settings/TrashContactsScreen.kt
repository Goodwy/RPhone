package dev.goodwy.rphone.view.screen.settings

import android.content.Context
import android.view.Surface
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.ContactsViewModel
import dev.goodwy.rphone.model.db.TrashedContactEntity
import dev.goodwy.rphone.view.components.RillAvatar
import dev.goodwy.rphone.view.components.RillConfirmationDialog
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.goodwy.rphone.cardCornerExtraSmall
import dev.goodwy.rphone.controller.util.toast
import dev.goodwy.rphone.view.components.NavigationIcon
import dev.goodwy.rphone.view.components.PlaceholderView
import dev.goodwy.rphone.view.components.RillDialog
import dev.goodwy.rphone.view.components.RillExpressiveCard
import dev.goodwy.rphone.view.components.Title
import dev.goodwy.rphone.view.theme.MyColors.cardColor
import dev.goodwy.rphone.view.theme.customColors
import org.koin.compose.viewmodel.koinActivityViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun TrashContactsScreen(
    navigator: DestinationsNavigator
) {
    val context = LocalContext.current
    val viewModel: ContactsViewModel = koinActivityViewModel()
    val trashedContacts by viewModel.trashedContacts.collectAsState()

    var showEmptyTrashDialog by remember { mutableStateOf(false) }
    var contactToDeletePermanently by remember { mutableStateOf<TrashedContactEntity?>(null) }

    LaunchedEffect(Unit) {
        viewModel.fetchTrashedContacts()
    }

    var visible by remember { mutableStateOf(false) }
    val screenAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(350),
        label = "spamAlpha"
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
                title = { Title(stringResource(R.string.contacts_trash_title)) },
                navigationIcon = {
                    NavigationIcon(onClick = { navigator.navigateUp() })
                },
                actions = {
                    if (trashedContacts.isNotEmpty()) {
                        IconButton(onClick = { showEmptyTrashDialog = true }) {
                            Icon(
                                imageVector = ImageVector.vectorResource(id = R.drawable.ic_delete_sweep),
                                contentDescription = stringResource(R.string.contacts_trash_empty_all),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }

                        Spacer(modifier = Modifier.size(6.dp))
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        if (trashedContacts.isEmpty()) {
            Box(
                modifier = Modifier
                    .padding(
                        top = padding.calculateTopPadding(),
                        start = 0.dp,
                        end = 0.dp,
                        bottom = 0.dp
                    )
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                PlaceholderView(
                    icon = ImageVector.vectorResource(id = R.drawable.ic_delete),
                    title = stringResource(R.string.contacts_trash_empty_title),
                    description = stringResource(R.string.contacts_trash_empty_desc)
                )
            }
        } else {
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
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = cardColor
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = stringResource(R.string.contacts_trash_header_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                item {
                    RillExpressiveCard {
                        trashedContacts.forEach { item ->
                            val contact = remember(item.contactJson) { item.toContact() }
                            val daysRemaining = remember(item.trashedAt) {
                                val thirtyDaysMs = 30L * 24 * 60 * 60 * 1000L
                                val diff = (item.trashedAt + thirtyDaysMs) - System.currentTimeMillis()
                                (diff / (24 * 60 * 60 * 1000L)).coerceAtLeast(0)
                            }
                            val deletedDateStr = remember(item.trashedAt) {
                                SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(item.trashedAt))
                            }

                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(cardCornerExtraSmall),
                                color = cardColor
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RillAvatar(
                                        name = item.name,
                                        photoUri = contact?.photoUri,
                                        modifier = Modifier.size(48.dp)
                                    )

                                    Spacer(modifier = Modifier.width(14.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.name.ifBlank { stringResource(R.string.label_unknown) },
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        val subtitle = contact?.phoneNumbers?.firstOrNull() ?: deletedDateStr
                                        Text(
                                            text = subtitle,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = stringResource(R.string.contacts_trash_days_left, daysRemaining),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.secondary,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                                        val toast = stringResource(R.string.contacts_trash_restored_toast, item.name)
                                        IconButton(
                                            onClick = {
                                                viewModel.restoreTrashedContact(item.localId) {
                                                    context.toast(toast)
                                                }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = ImageVector.vectorResource(id = R.drawable.ic_restore_from_trash),
                                                contentDescription = stringResource(R.string.contacts_trash_restore_action),
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(26.dp)
                                            )
                                        }

                                        IconButton(
                                            onClick = {
                                                contactToDeletePermanently = item
                                            }
                                        ) {
                                            Icon(
                                                imageVector = ImageVector.vectorResource(id = R.drawable.ic_delete_forever),
                                                contentDescription = stringResource(R.string.contacts_trash_delete_action),
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(26.dp)
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

    if (showEmptyTrashDialog) {
//        RillConfirmationDialog(
//            onDismissRequest = { showEmptyTrashDialog = false },
//            onConfirm = {
//                viewModel.emptyTrash()
//                showEmptyTrashDialog = false
//            },
//            title = stringResource(R.string.contacts_trash_empty_all_confirm_title),
//            message = stringResource(R.string.contacts_trash_empty_all_confirm_msg, trashedContacts.size),
//            confirmLabel = stringResource(R.string.contacts_trash_empty_all),
//            icon = Icons.Default.DeleteForever,
//            isDestructive = true
//        )
        RillDialog(
            onDismissRequest = { showEmptyTrashDialog = false },
            title = stringResource(R.string.contacts_trash_empty_all_confirm_title),
            icon = ImageVector.vectorResource(id = R.drawable.ic_delete_sweep),
            iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkRed,
            iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorRed,
            confirmButton = {
                TextButton(onClick = {
                    viewModel.emptyTrash()
                    showEmptyTrashDialog = false
                }) {
                    Text(stringResource(R.string.contacts_trash_empty_all), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEmptyTrashDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        ) {
            Text(
                stringResource(R.string.contacts_trash_empty_all_confirm_msg, trashedContacts.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    contactToDeletePermanently?.let { item ->
        RillDialog(
            onDismissRequest = { contactToDeletePermanently = null },
            title = stringResource(R.string.contacts_trash_delete_action),
            icon = ImageVector.vectorResource(id = R.drawable.ic_delete_forever),
            iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkRed,
            iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorRed,
            confirmButton = {
                TextButton(onClick = {
                    viewModel.permanentlyDeleteTrashedContact(item.localId)
                    contactToDeletePermanently = null
                }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { contactToDeletePermanently = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        ) {
            Text(
                stringResource(R.string.contacts_trash_delete_action_subtitle, item.name),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}