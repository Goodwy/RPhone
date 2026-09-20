package dev.goodwy.rphone.view.screen.settings

import android.content.Context
import android.view.Surface
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.StarHalf
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.view.components.RillConfirmationDialog
import dev.goodwy.rphone.view.components.RillDialog
import dev.goodwy.rphone.view.components.RillDialogAction
import dev.goodwy.rphone.view.components.RillExpressiveCard
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.goodwy.rphone.cardCornerExtraSmall
import dev.goodwy.rphone.view.components.NavigationIcon
import dev.goodwy.rphone.view.components.RillAnimatedSection
import dev.goodwy.rphone.view.components.Title
import dev.goodwy.rphone.view.components.performAppHaptic
import dev.goodwy.rphone.view.theme.MyColors.cardColor
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun QuickResponsesScreen(
    navigator: DestinationsNavigator
) {
    val context = LocalContext.current
    val prefs = koinInject<PreferenceManager>()
    var responses by remember { mutableStateOf(prefs.getQuickResponses().toMutableList()) }
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var editingText by remember { mutableStateOf("") }
    var isAddingNew by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }

    fun save(newResponses: List<String>) {
        responses = newResponses.toMutableList()
        prefs.setQuickResponses(newResponses)
    }

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
                title = { Title(stringResource(R.string.quick_responses)) },
                navigationIcon = {
                    NavigationIcon(onClick = { navigator.navigateUp() })
                },
                actions = {
                    IconButton(onClick = { showResetConfirm = true }) {
                        Icon(Icons.Outlined.RestartAlt, contentDescription = stringResource(R.string.reset_to_defaults))
                    }
                    Spacer(modifier = Modifier.size(6.dp))
                }
            )
        },
        floatingActionButton = {
            val fabShape = RoundedCornerShape(17.dp)
            FloatingActionButton(
                onClick = {
                    editingText = ""
                    isAddingNew = true
                },
                shape = fabShape
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_response))
            }
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                RillAnimatedSection(delayMs = 30L) {
                    Text(
                        text = stringResource(R.string.quick_responses_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
                    )
                }
            }

            item {
                RillAnimatedSection(delayMs = 60L) {
                    Column {
                        if (responses.isNotEmpty()) {
                            SettingsSectionLabel(stringResource(R.string.canned_responses, responses.size))
                        }
                        RillExpressiveCard {
                            if (responses.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = stringResource(R.string.no_quick_responses_configured),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                responses.forEachIndexed { index, responseText ->
                                    val interactionSource = remember { MutableInteractionSource() }
                                    val isPressed by interactionSource.collectIsPressedAsState()
                                    val scale by animateFloatAsState(
                                        targetValue = if (isPressed) 0.96f else 1f,
                                        animationSpec = spring(stiffness = Spring.StiffnessMedium),
                                        label = "SelectItemScale_$index"
                                    )
                                    Surface(
                                        onClick = {
                                            if (prefs.getBoolean(PreferenceManager.KEY_APP_HAPTICS, true)) {
                                                performAppHaptic(
                                                    context,
                                                    prefs.getString(PreferenceManager.KEY_APP_HAPTICS_STRENGTH, "light") ?: "light",
                                                    prefs.getFloat(PreferenceManager.KEY_HAPTICS_CUSTOM_INTENSITY, 0.5f)
                                                )
                                            }
                                            editingIndex = index
                                            editingText = responseText
                                        },
                                        shape = RoundedCornerShape(cardCornerExtraSmall),
                                        color = cardColor,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .scale(scale),
                                        shadowElevation = 0.dp,
                                        interactionSource = interactionSource
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 12.dp)
                                                .padding(start = 24.dp, end = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = responseText,
                                                style = MaterialTheme.typography.bodyMedium,
                                                modifier = Modifier.weight(1f),
                                                maxLines = 5,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            IconButton(
                                                onClick = {
                                                    val updated = responses.toMutableList()
                                                    updated.removeAt(index)
                                                    save(updated)
                                                }
                                            ) {
                                                Icon(
                                                    ImageVector.vectorResource(id = R.drawable.ic_delete),
                                                    contentDescription = stringResource(R.string.delete),
                                                    tint = MaterialTheme.colorScheme.error
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

            item { SettingsBottomPadding(120.dp) }
        }
    }

    if (isAddingNew) {
        RillDialog(
            onDismissRequest = { isAddingNew = false },
            title = stringResource(R.string.add_response),
            icon = ImageVector.vectorResource(id = R.drawable.ic_message_outline),
            confirmButton = {
                TextButton(onClick = {
                    val trimmed = editingText.trim()
                    if (trimmed.isNotEmpty()) {
                        val updated = responses.toMutableList()
                        updated.add(trimmed)
                        save(updated)
                    }
                    isAddingNew = false
                }) {
                    Text(
                        stringResource(R.string.save),
                        textAlign = TextAlign.End,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { isAddingNew = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        ) {
            OutlinedTextField(
                value = editingText,
                onValueChange = { editingText = it },
                label = { Text(stringResource(R.string.type_message)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                maxLines = 5
            )
        }
//        RillDialog(
//            onDismissRequest = { isAddingNew = false },
//            title = stringResource(R.string.add_response),
//            icon = Icons.Outlined.AddComment,
//            confirmAction = RillDialogAction(
//                label = stringResource(R.string.save),
//                enabled = editingText.isNotBlank(),
//                onClick = {
//                    val trimmed = editingText.trim()
//                    if (trimmed.isNotEmpty()) {
//                        val updated = responses.toMutableList()
//                        updated.add(trimmed)
//                        save(updated)
//                    }
//                    isAddingNew = false
//                }
//            )
//        ) {
//            OutlinedTextField(
//                value = editingText,
//                onValueChange = { editingText = it },
//                label = { Text("Message text") },
//                modifier = Modifier.fillMaxWidth(),
//                shape = RoundedCornerShape(14.dp),
//                maxLines = 3
//            )
//        }
    }

    if (editingIndex != null) {
        val index = editingIndex!!
        RillDialog(
            onDismissRequest = { editingIndex = null },
            title = stringResource(R.string.edit_response),
            icon = Icons.Outlined.Edit,
            confirmButton = {
                TextButton(onClick = {
                    val trimmed = editingText.trim()
                    if (trimmed.isNotEmpty()) {
                        val updated = responses.toMutableList()
                        updated[index] = trimmed
                        save(updated)
                    }
                    editingIndex = null
                }) {
                    Text(
                        stringResource(R.string.save),
                        textAlign = TextAlign.End,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { editingIndex = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        ) {
            OutlinedTextField(
                value = editingText,
                onValueChange = { editingText = it },
                label = { Text(stringResource(R.string.quick_response)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                maxLines = 5
            )
        }
//        RillDialog(
//            onDismissRequest = { editingIndex = null },
//            title = "Edit Quick Response",
//            icon = Icons.Outlined.Edit,
//            confirmAction = RillDialogAction(
//                label = "Save",
//                enabled = editingText.isNotBlank(),
//                onClick = {
//                    val trimmed = editingText.trim()
//                    if (trimmed.isNotEmpty()) {
//                        val updated = responses.toMutableList()
//                        updated[index] = trimmed
//                        save(updated)
//                    }
//                    editingIndex = null
//                }
//            )
//        ) {
//            OutlinedTextField(
//                value = editingText,
//                onValueChange = { editingText = it },
//                label = { Text("Message text") },
//                modifier = Modifier.fillMaxWidth(),
//                shape = RoundedCornerShape(14.dp),
//                maxLines = 3
//            )
//        }
    }

    if (showResetConfirm) {
        RillDialog(
            onDismissRequest = { showResetConfirm = false },
            title = stringResource(R.string.reset_to_defaults),
            icon = Icons.Rounded.RestartAlt,
            confirmButton = {
                TextButton(onClick = {
                    save(prefs.defaultQuickResponses())
                    showResetConfirm = false
                }) {
                    Text(
                        stringResource(R.string.reset),
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.End,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        ) {
            Text(stringResource(R.string.reset_to_defaults_description))
        }
//        RillConfirmationDialog(
//            onDismissRequest = { showResetConfirm = false },
//            onConfirm = {
//                save(prefs.defaultQuickResponses())
//                showResetConfirm = false
//            },
//            title = "Reset to Defaults",
//            message = "Restore original preset quick responses?",
//            confirmLabel = "Reset",
//            dismissLabel = stringResource(R.string.cancel),
//            icon = Icons.Outlined.RestartAlt,
//            isDestructive = true
//        )
    }
}
