package dev.goodwy.rphone.view.screen

import android.content.Context
import android.content.Intent
import android.provider.CallLog
import android.telecom.TelecomManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.CallLogViewModel
import dev.goodwy.rphone.controller.util.formatDateHeader
import dev.goodwy.rphone.controller.util.makeCall
import dev.goodwy.rphone.controller.util.placeCallWithSimPreference
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.controller.util.formatPhoneNumber
import dev.goodwy.rphone.modal.data.CallLogEntry
import dev.goodwy.rphone.modal.data.CallLogFilter
import dev.goodwy.rphone.view.components.*
import dev.goodwy.rphone.view.theme.MyColors.bottomBarColor
import dev.goodwy.rphone.view.theme.MyColors.cardColor
import dev.goodwy.rphone.view.theme.color_call_button
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.goodwy.rphone.controller.util.hasDualSim
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinActivityViewModel
import org.koin.compose.koinInject

//@Destination<RootGraph>(route = "call_log_detail_screen")
@Destination<RootGraph>
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallLogFullScreen(
    navigator: DestinationsNavigator,
    contactId: String? = null,
    phoneNumber: String? = null,
    numbersList: Array<String>? = null
) {
    val viewModel: CallLogViewModel = koinActivityViewModel()
    val allLogs by viewModel.allCallLogs.collectAsState()
    val selectedFilter by viewModel.selectedFilter.collectAsState()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    var selectedEntries by remember { mutableStateOf(setOf<CallLogEntry>()) }

    BackHandler(enabled = selectedEntries.isNotEmpty()) {
        selectedEntries = emptySet()
    }

    val showButton by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 2 }
    }
    val telecomManager = remember { context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager }
    val prefs = koinInject<PreferenceManager>()
    val simPref = remember { prefs.getInt("default_sim", 0) }

    var showSimPicker by remember { mutableStateOf(false) }
    var showNumberPicker by remember { mutableStateOf(false) }
    var showMessagePicker by remember { mutableStateOf(false) }
    var pendingNumber by remember { mutableStateOf<String?>(null) }

    // Entrance animation
    var screenVisible by remember { mutableStateOf(false) }
    val screenAlpha by animateFloatAsState(
        targetValue = if (screenVisible) 1f else 0f,
        animationSpec = tween(300),
        label = "logScreenAlpha"
    )
    val screenScale by animateFloatAsState(
        targetValue = if (screenVisible) 1f else 0.96f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "logScreenScale"
    )
    LaunchedEffect(Unit) { screenVisible = true }

    val filteredLogsByContact = remember(allLogs, contactId, phoneNumber) {
        if (contactId == null && phoneNumber == null && numbersList == null) allLogs
        else allLogs.filter { log ->
            (contactId != null && contactId != "null" && log.contactId == contactId) ||
            (phoneNumber != null && log.number.replace(" ", "").contains(phoneNumber.replace(" ", ""))) ||
            (numbersList != null && numbersList.contains(log.number))
        }
    }

    val contactName = remember(filteredLogsByContact) {
        filteredLogsByContact.firstOrNull { it.name != null && it.name != it.number }?.name ?: phoneNumber
        filteredLogsByContact.firstOrNull { it.name != null && it.name != it.number }?.name ?: (if (phoneNumber != null) formatPhoneNumber(phoneNumber) else null)
    }

    if (showSimPicker && pendingNumber != null) {
        SimPickerDialog(
            onDismissRequest = { showSimPicker = false },
            onSimSelected = { handle ->
                makeCall(context, pendingNumber!!, handle)
                showSimPicker = false
            }
        )
    }

    Scaffold(
        topBar = {
            AnimatedContent(
                targetState = selectedEntries.isNotEmpty(),
                transitionSpec = {
                    (fadeIn() + expandVertically()) togetherWith (fadeOut() + shrinkVertically())
                },
                label = "TopBarTransition"
            ) { isSelecting ->
                if (!isSelecting) {
                    Column {
                        TopAppBar(
                            title = {
                                val name =
                                    if ((contactId == null || contactId == "null") && numbersList != null) numbersList.joinToString()
                                    else contactName
                                Text(
                                    text = if (name != null) stringResource(R.string.history_with, name)
                                            else stringResource(R.string.call_history),
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            navigationIcon = {
                                NavigationIcon(onClick = { navigator.navigateUp() })
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                            )
                        )
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val filters = CallLogFilter.entries - CallLogFilter.Contacts
                            items(filters) { filter ->
                                RillFilterChip(stringResource(filter.stringRes), selectedFilter == filter, { _ ->
                                    viewModel.setFilter(filter)
                                })
                            }
                        }
                    }
                } else {
                    val finalLogs = remember(filteredLogsByContact, selectedFilter) {
                        when (selectedFilter) {
                            CallLogFilter.All -> filteredLogsByContact
                            CallLogFilter.Missed -> filteredLogsByContact.filter { it.type == CallLog.Calls.MISSED_TYPE }
                            CallLogFilter.Incoming -> filteredLogsByContact.filter { it.type == CallLog.Calls.INCOMING_TYPE }
                            CallLogFilter.Outgoing -> filteredLogsByContact.filter { it.type == CallLog.Calls.OUTGOING_TYPE }
                            CallLogFilter.Rejected -> filteredLogsByContact.filter { it.type == CallLog.Calls.REJECTED_TYPE }
                            CallLogFilter.Contacts -> filteredLogsByContact.filter { it.contactId != null }
                        }
                    }
                    BatchCallLogActionBar(
                        selectedCount = selectedEntries.size,
                        onClearSelection = { selectedEntries = emptySet() },
                        onDelete = {
                            val allIdsToDelete = selectedEntries.flatMap { it.ids }
                            viewModel.deleteCallLogsByIds(allIdsToDelete)
                            selectedEntries = emptySet()
                        },
                        onClearAll = {
                            val filteredIdsToDelete = finalLogs.flatMap { it.ids }
                            viewModel.deleteCallLogsByIds(filteredIdsToDelete)
                            selectedEntries = emptySet()
                        },
                        onDeselect = {
                            selectedEntries = emptySet()
                        },
                        onSelectAll = {
                            selectedEntries = finalLogs.toSet()
                        },
                        isAllSelected = selectedEntries == finalLogs.toSet()
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { innerPadding ->
        Box(modifier = Modifier
            .fillMaxSize()
            .alpha(screenAlpha)
            .scale(screenScale)) {
            val filteredNumbersList = filteredLogsByContact.distinctBy { it.number }
            val hasMultipleNumbers = filteredNumbersList.size > 1 || (numbersList != null && numbersList.size > 1)
            Box(
                modifier = Modifier
//                    .padding(innerPadding)
                    .padding(
                        top = innerPadding.calculateTopPadding(),
                        start = 0.dp,
                        end = 0.dp,
                        bottom = 0.dp
                    )
                    .fillMaxSize()) {
                if (filteredLogsByContact.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.no_call_history_found), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    val finalLogs = remember(filteredLogsByContact, selectedFilter) {
                        when (selectedFilter) {
                            CallLogFilter.All -> filteredLogsByContact
                            CallLogFilter.Missed -> filteredLogsByContact.filter { it.type == CallLog.Calls.MISSED_TYPE }
                            CallLogFilter.Incoming -> filteredLogsByContact.filter { it.type == CallLog.Calls.INCOMING_TYPE }
                            CallLogFilter.Outgoing -> filteredLogsByContact.filter { it.type == CallLog.Calls.OUTGOING_TYPE }
                            CallLogFilter.Rejected -> filteredLogsByContact.filter { it.type == CallLog.Calls.REJECTED_TYPE }
                            CallLogFilter.Contacts -> filteredLogsByContact.filter { it.contactId != null }
                        }
                    }

                    val showSimLabel = hasDualSim(context)
                    val groupedLogs = remember(finalLogs) { finalLogs.groupBy { context.formatDateHeader(it.date) } }

                    ScrollHapticsEffect(listState = listState)
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 168.dp, start = 16.dp, end = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        groupedLogs.forEach { (header, logsInGroup) ->
                            item(key = "group_$header", contentType = "logGroup") {
                                RillSectionHeader(
                                    title = header,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp, vertical = 4.dp))
                                RillExpressiveCard {
                                    logsInGroup.forEachIndexed { index, lg ->
                                        CallLogTileSimple(
                                            log = lg,
                                            onClick = {
                                                if (selectedEntries.isNotEmpty()) {
                                                    selectedEntries = if (selectedEntries.any { it.id == lg.id }) {
                                                        selectedEntries.filter { it.id != lg.id }.toSet()
                                                    } else {
                                                        selectedEntries + lg
                                                    }
                                                }
                                            },
                                            onLongClick = {
                                                if (selectedEntries.none { it.id == lg.id }) {
                                                    selectedEntries = selectedEntries + lg
                                                }
                                            },
                                            onCallClick = {
//                                                val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
//                                                    context,
//                                                    android.Manifest.permission.READ_PHONE_STATE
//                                                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
//
//                                                val targetContactId = lg.contactId ?: contactId
//
//                                                if (hasPermission) {
//                                                    val accounts = telecomManager.callCapablePhoneAccounts
//                                                    if (accounts.size > 1) {
//                                                        pendingNumber = lg.number
//                                                        pendingContactId = targetContactId
//                                                        showSimPicker = true
//                                                    } else {
//                                                        makeCall(context, lg.number, contactId = targetContactId)
//                                                    }
//                                                } else {
//                                                    makeCall(context, lg.number, contactId = targetContactId)
//                                                }
                                            },
                                            selected = selectedEntries.any { it.id == lg.id },
                                            showNumber = hasMultipleNumbers,
                                            showSimLabel = showSimLabel,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Bottom bar
            val hideBottomBar = contactId == null && phoneNumber == null
            if (!hideBottomBar) {
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
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .fillMaxWidth()
                        .wrapContentHeight()
//                    .offset { IntOffset(0, pillOffsetY.toInt()) }
                        .background(bottomBarColor),
//                .graphicsLayer { alpha = navBarAlpha },
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Row(
                        modifier = Modifier
                            .navigationBarsPadding()
                            .padding(16.dp)
                            .wrapContentSize(),
//                        .graphicsLayer { alpha = navBarAlpha }
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val callButtonSource = remember { MutableInteractionSource() }
                        val callButtonPressed by callButtonSource.collectIsPressedAsState()
                        val callButtonScale by animateFloatAsState(
                            targetValue = if (callButtonPressed) 1.04f else 1f,
                            animationSpec = spring(stiffness = Spring.StiffnessMedium),
                            label = "callButtonScale"
                        )
                        Surface(
                            onClick = {
                                if (phoneNumber != null) {
                                    if (prefs.getBoolean(PreferenceManager.KEY_APP_HAPTICS, true)) {
                                        performAppHaptic(
                                            context,
                                            prefs.getString(
                                                PreferenceManager.KEY_APP_HAPTICS_STRENGTH,
                                                "light"
                                            ) ?: "light",
                                            prefs.getFloat(
                                                PreferenceManager.KEY_HAPTICS_CUSTOM_INTENSITY,
                                                0.5f
                                            )
                                        )
                                    }
                                    if (hasMultipleNumbers) {
                                        showNumberPicker = true
                                    } else {
                                        initiateCall(phoneNumber)
                                    }
                                }
                            },
                            modifier = Modifier
                                .size(height = 46.dp, width = 82.dp)
                                .scale(callButtonScale),
                            shape = RoundedCornerShape(24.dp),
                            color = color_call_button, //MaterialTheme.colorScheme.primaryContainer
                            interactionSource = callButtonSource
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Phone,
                                    contentDescription = stringResource(R.string.call),
                                    tint = Color.White, //MaterialTheme.colorScheme.onPrimaryContainer
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        val messageButtonSource = remember { MutableInteractionSource() }
                        val messageButtonPressed by messageButtonSource.collectIsPressedAsState()
                        val messageButtonScale by animateFloatAsState(
                            targetValue = if (messageButtonPressed) 1.04f else 1f,
                            animationSpec = spring(stiffness = Spring.StiffnessMedium),
                            label = "messageButtonScale"
                        )
                        Surface(
                            onClick = {
                                if (phoneNumber != null) {
                                    if (prefs.getBoolean(PreferenceManager.KEY_APP_HAPTICS, true)) {
                                        performAppHaptic(
                                            context,
                                            prefs.getString(
                                                PreferenceManager.KEY_APP_HAPTICS_STRENGTH,
                                                "light"
                                            ) ?: "light",
                                            prefs.getFloat(
                                                PreferenceManager.KEY_HAPTICS_CUSTOM_INTENSITY,
                                                0.5f
                                            )
                                        )
                                    }
                                    if (hasMultipleNumbers) {
                                        showMessagePicker = true
                                    } else {
                                        initiateMessage(phoneNumber)
                                    }
                                }
                            },
                            modifier = Modifier
                                .size(height = 46.dp, width = 68.dp)
                                .scale(messageButtonScale),
                            shape = RoundedCornerShape(24.dp),
                            color = cardColor,
                            interactionSource = messageButtonSource
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_message_outline),
                                    contentDescription = stringResource(R.string.message),
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }

                    if (showNumberPicker) {
                        NumberPickerDialog(
                            numbers = numbersList?.toList()
                                ?: filteredNumbersList.map { it.number },
                            onDismissRequest = { showNumberPicker = false },
                            onNumberSelected = {
                                showNumberPicker = false
                                initiateCall(it)
                            }
                        )
                    }
                    if (showMessagePicker) {
                        NumberPickerDialog(
                            numbers = numbersList?.toList()
                                ?: filteredNumbersList.map { it.number },
                            onDismissRequest = { showMessagePicker = false },
                            onNumberSelected = {
                                showMessagePicker = false
                                initiateMessage(it)
                            }
                        )
                    }
                }
            }

            ScrollToTopButton(
                visible = showButton,
                onClick = { scope.launch { listState.animateScrollToItem(0) } },
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = if (!hideBottomBar) 102.dp else 24.dp, end = 24.dp)
            )
        }
    }
}
