// Based on RivoPhoneApp by user-grinch (© 2025-2026 RivoPhone Project / user-grinch), GPL-3.0.
// Modified by Goodwy on 2026-11-12.
package dev.goodwy.rphone.view.screen.settings

import android.content.Context
import android.os.Build
import android.view.Surface
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.BubbleChart
import androidx.compose.material.icons.rounded.Leaderboard
import androidx.compose.material.icons.rounded.PhoneDisabled
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.AnalyticsTimeRange
import dev.goodwy.rphone.controller.CallAnalyticsSummary
import dev.goodwy.rphone.controller.CallAnalyticsViewModel
import dev.goodwy.rphone.controller.TopContactStat
import dev.goodwy.rphone.view.components.RillAvatar
import dev.goodwy.rphone.view.components.RillExpressiveCard
import dev.goodwy.rphone.view.components.RillLoadingIndicatorView
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.ContactDetailsScreenDestination
import com.ramcosta.composedestinations.generated.destinations.DonateScreenDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.goodwy.rphone.cardCornerExtraSmall
import dev.goodwy.rphone.controller.ChartDataPoint
import dev.goodwy.rphone.controller.PurchaseHelper
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.controller.util.forceLtr
import dev.goodwy.rphone.view.components.NavigationIcon
import dev.goodwy.rphone.view.components.PlaceholderView
import dev.goodwy.rphone.view.components.RillPullToRefreshIndicator
import dev.goodwy.rphone.view.components.ScrollHapticsEffect
import dev.goodwy.rphone.view.components.Title
import dev.goodwy.rphone.view.components.performAppHaptic
import dev.goodwy.rphone.view.theme.MyColors.cardColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun CallAnalyticsScreen(
    navigator: DestinationsNavigator
) {
    val context = LocalContext.current
    val prefs = koinInject<PreferenceManager>()
    val viewModel: CallAnalyticsViewModel = koinViewModel()
    val isTrackingEnabled by viewModel.isTrackingEnabled.collectAsState()
    val analytics by viewModel.analytics.collectAsState()
    val selectedRange by viewModel.selectedRange.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var pullToRefreshActive by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }
    var isClosing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun navigateBack() {
        isClosing = true
        scope.launch {
            delay(280.milliseconds)
            navigator.navigateUp()
        }
    }

    fun triggerHaptic() {
        if (prefs.getBoolean(PreferenceManager.KEY_APP_HAPTICS, true)) {
            performAppHaptic(
                context,
                prefs.getString(PreferenceManager.KEY_APP_HAPTICS_STRENGTH, "light") ?: "light",
                prefs.getFloat(PreferenceManager.KEY_HAPTICS_CUSTOM_INTENSITY, 0.5f)
            )
        }
    }

    fun runRefresh() {
        triggerHaptic()
        viewModel.loadAnalytics(forceRefresh = true)
        pullToRefreshActive = false
    }

    val purchaseHelper: PurchaseHelper = koinInject()
    val isPro by purchaseHelper.isPro.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        val savedIsProIap = prefs.getBoolean(PreferenceManager.KEY_IS_PRO_IAP, false)
        val savedIsProSub = prefs.getBoolean(PreferenceManager.KEY_IS_PRO_SUB, false)
        val savedIsProFoss = prefs.getBoolean(PreferenceManager.KEY_IS_PRO_FOSS, false)
        if (savedIsProIap || savedIsProSub || savedIsProFoss) {
            purchaseHelper.setProStatusImmediate(true)
            purchaseHelper.checkProStatus()
        } else {
            purchaseHelper.checkProStatus()
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

    val listState = rememberLazyListState()
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
                title = { Title(stringResource(R.string.settings_call_analytics_title)) },
                navigationIcon = {
                    NavigationIcon(onClick = { navigateBack() })
                },
//                actions = {
//                    if (isTrackingEnabled) {
//                        IconButton(onClick = { viewModel.loadAnalytics(forceRefresh = true) }) {
//                            Icon(Icons.Outlined.Refresh, contentDescription = "Refresh")
//                        }
//                    }
//                }
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        BackHandler { navigateBack() }
        ScrollHapticsEffect(listState = listState)

        val isBlurSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        val blurRadius by animateDpAsState(
            targetValue = if (!isPro) 16.dp else 0.dp,
            animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
            label = "biometricBlur"
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = padding.calculateTopPadding(),
                    start = 0.dp,
                    end = 0.dp,
                    bottom = 0.dp
                ),
        ) {
            val pullToRefreshState = rememberPullToRefreshState()
            PullToRefreshBox(
                isRefreshing = isLoading && analytics.totalCalls == 0 && pullToRefreshActive,
                onRefresh = {
                    pullToRefreshActive = true
                    runRefresh()
                },
                modifier = Modifier.fillMaxSize()
                    .then(
                        if (isBlurSupported && blurRadius > 0.dp)
                            Modifier.blur(blurRadius, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                        else
                            Modifier
                    ),
                state = pullToRefreshState,
                indicator = {
                    RillPullToRefreshIndicator(
                        state = pullToRefreshState,
                        isRefreshing = isLoading && analytics.totalCalls == 0 && pullToRefreshActive
                    )
                }
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(alpha)
                        .offset(y = offsetY),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = 16.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (!isTrackingEnabled) {
                        item {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.large,
                                color = MaterialTheme.colorScheme.surfaceContainerLow
                            ) {
                                Column(
                                    modifier = Modifier.padding(32.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.BubbleChart,
                                        contentDescription = null,
                                        modifier = Modifier.size(56.dp),
                                        tint = MaterialTheme.colorScheme.outline
                                    )
                                    Spacer(Modifier.height(16.dp))
                                    Text(
                                        text = stringResource(R.string.call_analytics_disabled_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = stringResource(R.string.call_analytics_disabled_desc),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(Modifier.height(20.dp))
                                    Button(
                                        onClick = { viewModel.setAnalyticsTrackingEnabled(true) },
                                        shape = CircleShape
                                    ) {
                                        Icon(
                                            Icons.Outlined.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(stringResource(R.string.call_analytics_enable_action))
                                    }
                                }
                            }
                        }
                    } else if (isLoading && analytics.totalCalls == 0) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(250.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                RillLoadingIndicatorView()
                            }
                        }
                    } else {
                        // Time Range Segmented Control
                        stickyHeader {
                            SingleChoiceSegmentedButtonRow(
                                modifier = Modifier.fillMaxWidth(),
                                space = (-2).dp
                            ) {
                                AnalyticsTimeRange.entries.forEachIndexed { index, range ->
                                    val selected = selectedRange == range
                                    val interactionSource = remember { MutableInteractionSource() }
                                    val isPressed by interactionSource.collectIsPressedAsState()
                                    val cornerRadius by animateDpAsState(
                                        targetValue = if (isPressed || selected) 20.dp else 8.dp,
                                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                        label = "ButtonShape"
                                    )
                                    SegmentedButton(
                                        selected = selected,
                                        interactionSource = interactionSource,
                                        onClick = { viewModel.setTimeRange(range) },
                                        shape = when (index) {
                                            0 -> RoundedCornerShape(
                                                topStart = 20.dp,
                                                topEnd = cornerRadius,
                                                bottomEnd = cornerRadius,
                                                bottomStart = 20.dp
                                            )

                                            3 -> RoundedCornerShape(
                                                topStart = cornerRadius,
                                                topEnd = 20.dp,
                                                bottomEnd = 20.dp,
                                                bottomStart = cornerRadius
                                            )

                                            else -> RoundedCornerShape(cornerRadius)
                                        },
                                        colors = SegmentedButtonDefaults.colors(
                                            inactiveContainerColor = MaterialTheme.colorScheme.surfaceDim,
                                            activeContainerColor = MaterialTheme.colorScheme.primary
                                        ),
                                        icon = {},
                                        border = BorderStroke(0.dp, Color.Transparent)
                                    ) {
                                        Text(
                                            stringResource(range.stringRes),
                                            maxLines = 1,
                                            overflow = TextOverflow.MiddleEllipsis,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }

                        val totalCalls = analytics.totalCalls
                        if (totalCalls == 0) {
                            item {
                                PlaceholderView(
                                    icon = Icons.Rounded.PhoneDisabled,
                                    title = stringResource(R.string.no_call_records_this_period),
                                )
                            }
                        } else {
                            // Hero Talk Time Banner
                            item {
                                HeroTalkTimeCard(analytics = analytics)
                            }

                            item {
                                RillExpressiveCard(
                                    title = stringResource(R.string.call_volume),
                                    icon = Icons.Rounded.QueryStats
                                ) {
                                    AnalyticsBarChart(data = analytics.chartData)
                                }
                            }

                            // Call Distribution Summary Grid
                            item {
                                CallDistributionCard(analytics = analytics)
                            }

                            // SIM Card Usage (if multiple SIMs found)
                            if (analytics.simUsage.size > 1) {
                                item {
                                    RillExpressiveCard(
                                        title = stringResource(R.string.sim_usage_breakdown),
                                        icon = Icons.Outlined.SimCard
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .background(
                                                    color = cardColor,
                                                    shape = RoundedCornerShape(cardCornerExtraSmall)
                                                )
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 14.dp),
                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            analytics.simUsage.forEach { (sim, durationSec) ->
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        sim,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                    Text(
                                                        formatAnalyticsDuration(durationSec),
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Most Talked Person
                            if (analytics.topContacts.isEmpty()) {
                                item {
                                    RillExpressiveCard(
                                        title = stringResource(R.string.most_talked_persons),
                                        icon = Icons.Rounded.Leaderboard
                                    ) {
                                        Surface(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(cardCornerExtraSmall),
                                            color = cardColor
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(28.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Icon(
                                                    Icons.Rounded.PhoneDisabled,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(36.dp),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Spacer(Modifier.height(8.dp))
                                                Text(
                                                    stringResource(R.string.no_call_records_this_period),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                item {
                                    RillExpressiveCard(
                                        title = stringResource(R.string.most_talked_persons),
                                        icon = Icons.Rounded.Leaderboard
                                    ) {
                                        analytics.topContacts.forEachIndexed { index, contact ->
                                            TopContactLeaderboardItem(
                                                rank = index + 1,
                                                contact = contact,
                                                onClick = {
                                                    if (isPro) {
                                                        triggerHaptic()
                                                        navigator.navigate(
                                                            ContactDetailsScreenDestination(
                                                                phoneNumber = contact.number
                                                            )
                                                        )
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            item { SettingsBottomPadding() }
                        }
                    }
                }
            }

            AnimatedVisibility(
                modifier = Modifier
                    .align(Alignment.BottomCenter),
                visible = !isPro,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                Snackbar(
                    modifier = Modifier.navigationBarsPadding().padding(24.dp),
                    shape = MaterialTheme.shapes.large,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    action = {
                        TextButton(
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                            onClick = {
                                navigator.navigate(DonateScreenDestination)
                            }
                        ) {
                            Text(
                                stringResource(R.string.continue_support),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                ) {
                    Text(
                        stringResource(R.string.support_project_to_unlock),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroTalkTimeCard(analytics: CallAnalyticsSummary) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Surface(
            modifier = Modifier.weight(1f),
            shape = MaterialTheme.shapes.largeIncreased,
            color = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ) {
            HeroMetricItem(label = stringResource(R.string.total_talk_time), value = formatAnalyticsDuration(analytics.totalTalkTimeSeconds))
        }
        Surface(
            modifier = Modifier.weight(1f),
            shape = MaterialTheme.shapes.largeIncreased,
            color = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ) {
            HeroMetricItem(label = stringResource(R.string.total_calls), value = "${analytics.totalCalls}")
        }
        Surface(
            modifier = Modifier.weight(1f),
            shape = MaterialTheme.shapes.largeIncreased,
            color = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ) {
            HeroMetricItem(label = stringResource(R.string.avg_duration), value = formatShortDuration(analytics.avgDurationSeconds))
        }
    }
}

@Composable
private fun HeroMetricItem(label: String, value: String) {
    Column(
        modifier = Modifier.padding(vertical = 24.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CallDistributionCard(analytics: CallAnalyticsSummary) {
    RillExpressiveCard(
        title = stringResource(R.string.call_breakdown),
        icon = Icons.Outlined.PieChart
    ) {
        Column(
            modifier = Modifier
                .background(
                    color = cardColor,
                    shape = RoundedCornerShape(cardCornerExtraSmall)
                )
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val total = analytics.totalCalls.coerceAtLeast(1).toFloat()
            val inRatio = analytics.incomingCalls / total
            val outRatio = analytics.outgoingCalls / total
            val missRatio = analytics.missedCalls / total
            val rejectRatio = analytics.rejectedCalls / total
            val inColor = Color(0xFF68D27C) //Color(0xFF34C759)
            val outColor = Color(0xFF4CA0FF) //Color(0xFF0088FF)
            val missColor = Color(0xFFFF6666) //Color(0xFFFF383C)
            val rejectColor = Color(0xFFFFA45B) //Color(0xFFFF8D28)

            // Stacked progress bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            ) {
                if (inRatio > 0f) {
                    Box(
                        modifier = Modifier
                            .weight(inRatio)
                            .fillMaxHeight()
                            .background(inColor)
                    )
                }
                if (outRatio > 0f) {
                    Box(
                        modifier = Modifier
                            .weight(outRatio)
                            .fillMaxHeight()
                            .background(outColor)
                    )
                }
                if (missRatio > 0f) {
                    Box(
                        modifier = Modifier
                            .weight(missRatio)
                            .fillMaxHeight()
                            .background(missColor)
                    )
                }
                if (rejectRatio > 0f) {
                    Box(
                        modifier = Modifier
                            .weight(rejectRatio)
                            .fillMaxHeight()
                            .background(rejectColor)
                    )
                }
            }

            // Legend Row
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (inRatio > 0f) BreakdownLegend(
                    color = inColor,
                    label = stringResource(R.string.filter_incoming),
                    count = analytics.incomingCalls
                )
                if (outRatio > 0f) BreakdownLegend(
                    color = outColor,
                    label = stringResource(R.string.filter_outgoing),
                    count = analytics.outgoingCalls
                )
                if (missRatio > 0f) BreakdownLegend(
                    color = missColor,
                    label = stringResource(R.string.filter_missed),
                    count = analytics.missedCalls
                )
                if (rejectRatio > 0f) BreakdownLegend(
                    color = rejectColor,
                    label = stringResource(R.string.filter_rejected),
                    count = analytics.rejectedCalls
                )
            }
        }
    }
}

@Composable
private fun BreakdownLegend(color: Color, label: String, count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "$label ($count)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TopContactLeaderboardItem(
    rank: Int,
    contact: TopContactStat,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(cardCornerExtraSmall),
        color = cardColor
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Rank Badge
            Surface(
                shape = CircleShape,
                color = when (rank) {
                    1 -> Color(0xFFFFD700).copy(alpha = 0.25f) // Gold
                    2 -> Color(0xFFC0C0C0).copy(alpha = 0.25f) // Silver
                    3 -> Color(0xFFCD7F32).copy(alpha = 0.25f) // Bronze
                    else -> MaterialTheme.colorScheme.surfaceContainerHighest
                },
                modifier = Modifier.size(28.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "$rank",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = when (rank) {
                            1 -> Color(0xFFB8860B)
                            2 -> Color(0xFF708090)
                            3 -> Color(0xFF8B4513)
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            RillAvatar(
                name = if (contact.name == contact.number) contact.name.forceLtr() else contact.name,
                photoUri = contact.photoUri,
                modifier = Modifier.size(44.dp)
            )

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (contact.name != contact.number) contact.name + " (${contact.number})" else contact.number,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(R.string.total_calls_in_number, contact.totalCalls, contact.incomingCount, contact.outgoingCount, contact.missedCount),
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = MaterialTheme.typography.bodyMedium.fontSize,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.width(10.dp))

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatAnalyticsDuration(contact.totalDurationSeconds),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
//                Text(
//                    text = "talked",
//                    style = MaterialTheme.typography.labelSmall,
//                    color = MaterialTheme.colorScheme.onSurfaceVariant
//                )
            }
        }
    }
}

fun formatAnalyticsDuration(seconds: Long): String {
    if (seconds <= 0) return "0s"
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60

    return when {
        hours > 0 -> "${hours}h ${minutes}m ${secs}s"
        minutes > 0 -> "${minutes}m ${secs}s"
        else -> "${secs}s"
    }
}

fun formatShortDuration(seconds: Long): String {
    if (seconds <= 0) return "0s"
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60

    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m ${secs}s"
        else -> "${secs}s"
    }
}

@Composable
private fun AnalyticsBarChart(data: List<ChartDataPoint>) {
    val maxVal = (data.maxOfOrNull { it.value } ?: 0).coerceAtLeast(1)
    val maxChartHeight = 120.dp
    val density = LocalDensity.current

    Row(
        modifier = Modifier
            .background(
                color = cardColor,
                shape = RoundedCornerShape(cardCornerExtraSmall)
            )
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 16.dp)
            .height(maxChartHeight + 40.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        data.forEach { point ->
            val heightFraction = if (point.value > 0) point.value.toFloat() / maxVal else 0f
            val barHeightDp = with(density) { (maxChartHeight.toPx() * heightFraction).toDp() }

            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (point.value > 0) {
                    // Value above the column
                    Text(
                        text = "${point.value}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                } else {
                    // A blank space to align the columns at the bottom
                    Spacer(Modifier.height(16.dp))
                }

                // Column
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(barHeightDp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.primary)
                )

                Spacer(Modifier.height(8.dp))

                if (point.value == 0) {
                    Box(
                        modifier = Modifier
                            .padding(bottom = 4.dp)
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    )
                } else {
                    Text(
                        text = point.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
