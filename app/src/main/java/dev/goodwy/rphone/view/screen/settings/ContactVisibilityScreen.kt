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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.controller.ContactsViewModel
import dev.goodwy.rphone.controller.util.ContactUtils
import dev.goodwy.rphone.view.components.RillExpressiveCard
import dev.goodwy.rphone.view.components.RillSwitchListItem
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.goodwy.rphone.R
import dev.goodwy.rphone.view.components.NavigationIcon
import dev.goodwy.rphone.view.components.RillAnimatedSection
import dev.goodwy.rphone.view.components.ScrollHapticsEffect
import dev.goodwy.rphone.view.components.Title
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinActivityViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun ContactVisibilityScreen(
    navigator: DestinationsNavigator
) {
    val context = LocalContext.current
    val viewModel: ContactsViewModel = koinActivityViewModel()
    val accounts by viewModel.availableAccounts.collectAsState()
    val visibleAccounts by viewModel.visibleAccountsFlow.collectAsState()

    val currentVisible = remember(visibleAccounts, accounts) {
        visibleAccounts ?: (accounts.map { "${it.type}|${it.name}" } + "local|local" + "private|private").toSet()
    }

    fun toggleAccount(key: String, enabled: Boolean) {
        val newSet = if (enabled) currentVisible + key else currentVisible - key
        viewModel.setVisibleAccounts(newSet)
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
                title = { Title(stringResource(R.string.managing_contact_sources)) },
                navigationIcon = {
                    NavigationIcon(onClick = { navigateBack() })
                }
            )
        }
    ) { padding ->
        BackHandler { navigateBack() }
        ScrollHapticsEffect(listState = listState)
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
                    Text(
                        text = stringResource(R.string.managing_contact_sources_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
                    )
                }
            }

            item {
                RillAnimatedSection(delayMs = 60L) {
                    Column {
                        SettingsSectionLabel(stringResource(R.string.contact_sources))
                        RillExpressiveCard {
                            RillSwitchListItem(
                                headline = ContactUtils.getFriendlyAccountName(null, true),
                                supporting = stringResource(R.string.contacts_stored_on_app),
                                leadingIcon = ContactUtils.getAccountIcon(null, true),
                                iconContainerColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                iconBgContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                checked = currentVisible.contains("private|private"),
                                onCheckedChange = { isChecked: Boolean ->
                                    toggleAccount(
                                        "private|private",
                                        isChecked
                                    )
                                }
                            )
                            RillSwitchListItem(
                                headline = ContactUtils.getFriendlyAccountName(null),
                                supporting = stringResource(R.string.contacts_stored_on_device),
                                leadingIcon = ContactUtils.getAccountIcon(null),
                                iconContainerColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                iconBgContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                checked = currentVisible.contains("local|local"),
                                onCheckedChange = { isChecked: Boolean ->
                                    toggleAccount(
                                        "local|local",
                                        isChecked
                                    )
                                }
                            )

                            accounts.forEach { account ->
                                val key = "${account.type}|${account.name}"
                                RillSwitchListItem(
                                    headline = ContactUtils.getFriendlyAccountName(account),
                                    supporting = account.name,
                                    leadingIcon = ContactUtils.getAccountIcon(account),
                                    checked = currentVisible.contains(key),
                                    onCheckedChange = { isChecked: Boolean ->
                                        toggleAccount(
                                            key,
                                            isChecked
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
            
//            item {
//                RillAnimatedSection(delayMs = 90L) {
//                    TextButton(
//                        onClick = { viewModel.setVisibleAccounts(null) },
//                        modifier = Modifier.fillMaxWidth()
//                    ) {
//                        Text("Reset to Show All")
//                    }
//                }
//            }

            item { Spacer(modifier = Modifier.height(20.dp).navigationBarsPadding()) }
        }
    }
}
