package dev.goodwy.rphone.view.screen.settings

import android.content.Context
import android.telecom.TelecomManager
import android.view.Surface
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.NotificationsPaused
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.controller.util.getBlockedNumbers
import dev.goodwy.rphone.view.components.NavigationIcon
import dev.goodwy.rphone.view.components.RillAnimatedSection
import dev.goodwy.rphone.view.components.RillExpressiveCard
import dev.goodwy.rphone.view.components.RillListItem
import dev.goodwy.rphone.view.components.RillSelectListItem
import dev.goodwy.rphone.view.components.RillSwitchListItem
import dev.goodwy.rphone.view.theme.customColors
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun BlockedNumbersScreen(
    navigator: DestinationsNavigator
) {
    val context = LocalContext.current
    val prefs = koinInject<PreferenceManager>()
    val settingsState by prefs.settingsChanged.collectAsState()
    
    var blockMethod by remember(settingsState) { mutableStateOf(prefs.getInt(PreferenceManager.KEY_BLOCK_METHOD, 0)) }
    var logVisibility by remember(settingsState) { mutableStateOf(prefs.getInt(PreferenceManager.KEY_BLOCK_LOG_VISIBILITY, 0)) }
    var blockNotification by remember(settingsState) { mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_BLOCK_NOTIFICATION, true)) }

    var visible by remember { mutableStateOf(false) }
    val screenAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(350),
        label = "spamAlpha"
    )
    LaunchedEffect(Unit) { visible = true }

    val lifecycleOwner = LocalLifecycleOwner.current
    var blockedNumbersSize by remember { mutableStateOf(context.getBlockedNumbers().size.toString()) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                blockedNumbersSize = context.getBlockedNumbers().size.toString()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

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
                title = { Text(stringResource(R.string.manage_blocked), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    NavigationIcon(onClick = { navigator.navigateUp() })
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
//                .padding(padding)
                .padding(
                    top = padding.calculateTopPadding(),
                    start = 0.dp,
                    end = 0.dp,
                    bottom = 0.dp
                )
                .alpha(screenAlpha),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                RillAnimatedSection(delayMs = 0L) {
                    RillExpressiveCard {
                        RillSwitchListItem(
                            headline = stringResource(R.string.block_notifications),
                            supporting = stringResource(R.string.block_notifications_subtitle),
                            leadingIcon = Icons.Rounded.NotificationsPaused,
                            iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkAmber,
                            iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorAmber,
                            checked = blockNotification,
                            onCheckedChange = {
                                blockNotification = it
                                prefs.setBoolean(PreferenceManager.KEY_BLOCK_NOTIFICATION, it)
                            }
                        )
                    }
                }
            }

            item {
                RillAnimatedSection(delayMs = 80L) {
                    RillExpressiveCard {
                        RillSelectListItem(
                            headline = stringResource(R.string.block_method),
                            supporting = stringResource(R.string.block_method_subtitle),
                            leadingIcon = Icons.Rounded.Gavel,
                            iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkRed,
                            iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorRed,
                            options = listOf(
                                stringResource(R.string.decline_automatically) to 0,
                                stringResource(R.string.ring_silently) to 1
                            ),
                            selectedValue = blockMethod,
                            onValueChange = {
                                blockMethod = it
                                prefs.setInt(PreferenceManager.KEY_BLOCK_METHOD, it)
                            }
                        )
                        RillSelectListItem(
                            headline = stringResource(R.string.log_visibility),
                            supporting = stringResource(R.string.log_visibility_subtitle),
                            leadingIcon = Icons.Rounded.Visibility,
                            iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkRed,
                            iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorRed,
                            options = listOf(
                                stringResource(R.string.hide_from_logs) to 0,
                                stringResource(R.string.show_in_logs) to 1
                            ),
                            selectedValue = logVisibility,
                            onValueChange = {
                                logVisibility = it
                                prefs.setInt(PreferenceManager.KEY_BLOCK_LOG_VISIBILITY, it)
                            }
                        )
                    }
                }
            }
            
            item {
                RillAnimatedSection(delayMs = 160L) {
                    RillExpressiveCard {
                        RillListItem(
                            headline = stringResource(R.string.blocked_numbers),
                            supporting = stringResource(R.string.blocked_size, blockedNumbersSize),
                            leadingIcon = Icons.AutoMirrored.Rounded.List,
                            iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkOrange,
                            iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorOrange,
                            trailingIcon = Icons.Default.ChevronRight,
                            onClick = {
                                val telecomManager =
                                    context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
                                try {
                                    val intent = telecomManager.createManageBlockedNumbersIntent()
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    // Fallback if the intent is not supported on some older/custom versions
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
