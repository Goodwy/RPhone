package dev.goodwy.rphone.view.screen.settings

import android.content.Context
import android.view.Surface
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Swipe
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.modal.data.SwipeActionType
import dev.goodwy.rphone.view.components.*
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.goodwy.rphone.cardCornerExtraSmall
import dev.goodwy.rphone.icons.swipe_left
import dev.goodwy.rphone.icons.swipe_left_2
import dev.goodwy.rphone.icons.swipe_right
import dev.goodwy.rphone.icons.swipe_right_2
import dev.goodwy.rphone.view.theme.MyColors.cardColor
import dev.goodwy.rphone.view.theme.customColors
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun SwipeActionsScreen(
    navigator: DestinationsNavigator
) {
    val context = LocalContext.current
    val prefs = koinInject<PreferenceManager>()
    val settingsState by prefs.settingsChanged.collectAsState()

    var directCallOnTap by remember(settingsState) { mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_DIRECT_CALL_ON_TAP, false)) }
    var enabled by remember(settingsState) { mutableStateOf(prefs.isSwipeActionsEnabled()) }
    var rightActionId by remember(settingsState) { mutableIntStateOf(prefs.getSwipeRightAction()) }
    var leftActionId by remember(settingsState) { mutableIntStateOf(prefs.getSwipeLeftAction()) }
    var leftSecondActionId by remember(settingsState) { mutableIntStateOf(prefs.getSwipeLeftSecondAction()) }

    val actionOptions = remember {
        listOf(
            SwipeActionType.CALL,
            SwipeActionType.MESSAGE,
            SwipeActionType.WHATSAPP,
            SwipeActionType.TELEGRAM,
            SwipeActionType.VIDEO_CALL,
            SwipeActionType.COPY_NUMBER,
            SwipeActionType.DELETE,
            SwipeActionType.NONE
        )
    }

    var visible by remember { mutableStateOf(false) }
    val screenAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(350),
        label = "navigationAlpha"
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
                title = { Title(stringResource(R.string.settings_swipe_and_taps_title)) },
                navigationIcon = {
                    NavigationIcon(onClick = { navigator.navigateUp() })
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = padding.calculateTopPadding(),
                    start = 0.dp,
                    end = 0.dp,
                    bottom = 0.dp
                )
                .alpha(screenAlpha),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                RillAnimatedSection(delayMs = 40L) {
                    RillExpressiveCard {
                        RillSwitchListItem(
                            headline   = stringResource(R.string.direct_call_on_tap),
                            supporting = stringResource(R.string.direct_call_on_tap_subtitle),
                            leadingIcon = Icons.Rounded.TouchApp,
                            iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkIndigo,
                            iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorIndigo,
                            checked = directCallOnTap,
                            onCheckedChange = {
                                directCallOnTap = it
                                prefs.setBoolean(PreferenceManager.KEY_DIRECT_CALL_ON_TAP, it)
                            }
                        )
                        RillSwitchListItem(
                            headline = stringResource(R.string.settings_swipe_actions_enable),
                            supporting = stringResource(R.string.settings_swipe_actions_enable_supporting),
                            leadingIcon = Icons.Rounded.Swipe,
                            iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkBlue,
                            iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorBlue,
                            checked = enabled,
                            onCheckedChange = {
                                enabled = it
                                prefs.setSwipeActionsEnabled(it)
                            }
                        )
                    }
                }
            }

            // Swipe Right Configuration
            item {
                AnimatedVisibility(
                    visible = enabled,
                    enter = fadeIn(tween(250)) + expandVertically(tween(250)),
                    exit = fadeOut(tween(200)) + shrinkVertically(tween(200))
                ) {
                    RillAnimatedSection(delayMs = 80L) {
                        Column {
                            SettingsSectionLabel(stringResource(R.string.recents))
                            RillExpressiveCard {
                                val actionRight = SwipeActionType.fromId(rightActionId)
                                RillSelectListItem(
                                    headline = if (isLtr) stringResource(R.string.settings_swipe_right_action) else stringResource(R.string.settings_swipe_left_action),
                                    leadingIcon = actionRight.icon(),
                                    iconContainerColor = actionRight.contentColor(),
                                    iconBgContainerColor = actionRight.containerColor(),
                                    trailingIcon = if (isLtr) swipe_right else swipe_left,
                                    options = actionOptions.map { stringResource(it.titleRes) to it.id },
                                    selectedValue = rightActionId,
                                    onValueChange = { selected ->
                                        rightActionId = selected
                                        prefs.setSwipeRightAction(selected)
                                    }
                                )

                                val actionLeft = SwipeActionType.fromId(leftActionId)
                                RillSelectListItem(
                                    headline = if (isLtr) stringResource(R.string.settings_swipe_left_action) else stringResource(R.string.settings_swipe_right_action),
                                    leadingIcon = actionLeft.icon(),
                                    iconContainerColor = actionLeft.contentColor(),
                                    iconBgContainerColor = actionLeft.containerColor(),
                                    trailingIcon = if (isLtr) swipe_left else swipe_right,
                                    options = actionOptions.map { stringResource(it.titleRes) to it.id },
                                    selectedValue = leftActionId,
                                    onValueChange = { selected ->
                                        leftActionId = selected
                                        prefs.setSwipeLeftAction(selected)
                                    }
                                )

                                AnimatedVisibility(
                                    visible = actionLeft != SwipeActionType.NONE,
                                    enter = fadeIn(tween(250)) + expandVertically(tween(250)),
                                    exit = fadeOut(tween(200)) + shrinkVertically(tween(200))
                                ) {
                                    val actionLeftSecond = SwipeActionType.fromId(leftSecondActionId)
                                    RillSelectListItem(
                                        headline = if (isLtr) stringResource(R.string.settings_swipe_left_action_second) else stringResource(R.string.settings_swipe_right_action_second),
                                        leadingIcon = actionLeftSecond.icon(),
                                        iconContainerColor = actionLeftSecond.contentColor(),
                                        iconBgContainerColor = actionLeftSecond.containerColor(),
                                        trailingIcon = if (isLtr) swipe_left_2 else swipe_right_2,
                                        options = actionOptions.map { stringResource(it.titleRes) to it.id },
                                        selectedValue = leftSecondActionId,
                                        onValueChange = { selected ->
                                            leftSecondActionId = selected
                                            prefs.setSwipeLeftSecondAction(selected)
                                        }
                                    )
                                }

                                TextButton(
                                    onClick = {
                                        prefs.resetSwipeActions()
                                        rightActionId = prefs.getSwipeRightAction()
                                        leftActionId = prefs.getSwipeLeftAction()
                                        leftSecondActionId = prefs.getSwipeLeftSecondAction()
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .offset(
                                            x = 0.dp,
                                            y = (-4).dp
                                        ), // We'll make up for the indentation
                                    colors = ButtonDefaults.textButtonColors()
                                        .copy(containerColor = cardColor),
                                    shape = MaterialTheme.shapes.extraLarge.copy(
                                        topStart = CornerSize(cardCornerExtraSmall),
                                        topEnd = CornerSize(cardCornerExtraSmall)
                                    ),
                                ) {
                                    Text(stringResource(R.string.settings_swipe_actions_reset))
                                }
                            }
                        }
                    }
                }
            }

            item { SettingsBottomPadding() }
        }
    }
}
