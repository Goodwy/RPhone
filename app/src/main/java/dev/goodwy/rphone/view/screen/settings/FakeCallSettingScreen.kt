package dev.goodwy.rphone.view.screen.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.FakeCallManager
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.view.components.NavigationIcon
import dev.goodwy.rphone.view.components.RillAnimatedSection
import dev.goodwy.rphone.view.components.RillExpressiveCard
import dev.goodwy.rphone.view.components.RillSwitchListItem
import dev.goodwy.rphone.view.components.Title
import dev.goodwy.rphone.view.theme.customColors
import kotlinx.coroutines.delay
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun FakeCallSettingScreen(navigator: DestinationsNavigator) {
    val prefs = koinInject<PreferenceManager>()
    val fakeCallManager = koinInject<FakeCallManager>()

    var callerName by remember { mutableStateOf(prefs.getFakeCallName()) }
    var callerNumber by remember { mutableStateOf(prefs.getFakeCallNumber()) }
    var selectedDelay by remember { mutableIntStateOf(prefs.getFakeCallDelay()) }
    var logToHistory by remember { mutableStateOf(prefs.getLogFakeCalls()) }

    val scheduledTimeMillis by fakeCallManager.scheduledTimeMillis.collectAsStateWithLifecycle()
    var timeRemainingSeconds by remember { mutableLongStateOf(0L) }

    LaunchedEffect(scheduledTimeMillis) {
        val target = scheduledTimeMillis
        if (target != null) {
            while (true) {
                val now = System.currentTimeMillis()
                val diff = (target - now) / 1000L
                if (diff <= 0) {
                    timeRemainingSeconds = 0L
                    fakeCallManager.clearScheduledState()
                    break
                }
                timeRemainingSeconds = diff
                delay(1000)
            }
        } else {
            timeRemainingSeconds = 0L
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    NavigationIcon(onClick = { navigator.navigateUp() })
                },
                title = { Title(stringResource(R.string.fake_call)) }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Scheduled Call Banner if active
            if (scheduledTimeMillis != null && timeRemainingSeconds > 0) {
                item {
                    RillAnimatedSection(delayMs = 0L) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.customColors.colorGreen.copy(alpha = 0.2f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.fake_call_scheduled, "${timeRemainingSeconds}s"),
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "$callerName ($callerNumber)",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                TextButton(
                                    onClick = { fakeCallManager.cancelScheduledFakeCall() },
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                    )
                                ) {
                                    Text(stringResource(R.string.fake_call_cancel))
                                }
                            }
                        }
                    }
                }
            }

            // Caller Information Section
            item {
                RillAnimatedSection(delayMs = 20L) {
                    RillExpressiveCard {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Caller Details",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )

                            OutlinedTextField(
                                value = callerName,
                                onValueChange = {
                                    callerName = it
                                    prefs.setFakeCallName(it)
                                },
                                label = { Text(stringResource(R.string.fake_call_caller_name)) },
                                leadingIcon = {
                                    Icon(Icons.Rounded.Person, contentDescription = null)
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = callerNumber,
                                onValueChange = {
                                    callerNumber = it
                                    prefs.setFakeCallNumber(it)
                                },
                                label = { Text(stringResource(R.string.fake_call_caller_number)) },
                                leadingIcon = {
                                    Icon(Icons.Rounded.Phone, contentDescription = null)
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // Delay & Options Section
            item {
                RillAnimatedSection(delayMs = 40L) {
                    RillExpressiveCard {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.fake_call_delay),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )

                            val delayOptions = listOf(
                                0 to stringResource(R.string.fake_call_delay_immediately),
                                5 to stringResource(R.string.fake_call_delay_5s),
                                10 to stringResource(R.string.fake_call_delay_10s),
                                30 to stringResource(R.string.fake_call_delay_30s),
                                60 to stringResource(R.string.fake_call_delay_1m),
                                300 to stringResource(R.string.fake_call_delay_5m),
                                900 to stringResource(R.string.fake_call_delay_15m)
                            )

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                delayOptions.chunked(3).forEach { rowChunk ->
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        rowChunk.forEach { (seconds, label) ->
                                            FilterChip(
                                                selected = selectedDelay == seconds,
                                                onClick = {
                                                    selectedDelay = seconds
                                                    prefs.setFakeCallDelay(seconds)
                                                },
                                                label = { Text(label) },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                        if (rowChunk.size < 3) {
                                            repeat(3 - rowChunk.size) {
                                                Spacer(modifier = Modifier.weight(1f))
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            RillSwitchListItem(
                                headline = stringResource(R.string.fake_call_log_history),
                                supporting = stringResource(R.string.fake_call_log_history_subtitle),
                                checked = logToHistory,
                                onCheckedChange = {
                                    logToHistory = it
                                    prefs.setLogFakeCalls(it)
                                }
                            )
                        }
                    }
                }
            }

            // Actions Section
            item {
                RillAnimatedSection(delayMs = 60L) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 32.dp)
                    ) {
                        Button(
                            onClick = {
                                fakeCallManager.scheduleFakeCall(
                                    callerName = callerName,
                                    callerNumber = callerNumber,
                                    delaySeconds = selectedDelay,
                                    logToHistory = logToHistory
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                        ) {
                            Icon(Icons.Rounded.Schedule, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (selectedDelay == 0) stringResource(R.string.fake_call_trigger_now)
                                else stringResource(R.string.fake_call_schedule),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }

                        if (selectedDelay > 0) {
                            OutlinedButton(
                                onClick = {
                                    fakeCallManager.triggerFakeCallNow(
                                        callerName = callerName,
                                        callerNumber = callerNumber,
                                        logToHistory = logToHistory
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                            ) {
                                Icon(Icons.Rounded.FlashOn, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.fake_call_trigger_now),
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
