package dev.goodwy.rphone.view.components

import android.content.res.Configuration
import androidx.compose.animation.core.Spring
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.MicNone
import androidx.compose.material3.*
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import dev.goodwy.rphone.R
import dev.goodwy.rphone.view.theme.MyColors.cardColor
import dev.goodwy.rphone.controller.util.PreferenceManager
import com.ramcosta.composedestinations.generated.destinations.SearchScreenDestination
import com.ramcosta.composedestinations.generated.destinations.SettingsScreenDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import org.koin.compose.koinInject

@Composable
fun TopBar(
    navController: NavController,
    navigator: DestinationsNavigator
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    // In landscape, search/settings are in the NavigationRail — just provide status bar inset
    if (isLandscape) {
        Spacer(modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars))
        return
    }
    val prefs = koinInject<PreferenceManager>()
    var animatedVisible by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (animatedVisible) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "topBarAlpha"
    )
    val offsetY by animateDpAsState(
        targetValue = if (animatedVisible) 0.dp else (-16).dp,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "topBarOffset"
    )
    LaunchedEffect(Unit) { animatedVisible = true }

    // Settings button press animation
    val settingsSource = remember { MutableInteractionSource() }
    val micSource = remember { MutableInteractionSource() }

    // Search bar press animation
    val searchSource = remember { MutableInteractionSource() }
    val searchPressed by searchSource.collectIsPressedAsState()
    val searchScale by animateFloatAsState(
        targetValue = if (searchPressed) 0.96f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "searchScale"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .alpha(alpha)
            .offset(y = offsetY),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Search bar
            Surface(
                onClick = {
                    if (prefs.getBoolean(PreferenceManager.KEY_APP_HAPTICS, true)) {
                        performAppHaptic(context, prefs.getString(PreferenceManager.KEY_APP_HAPTICS_STRENGTH, "light") ?: "light", prefs.getFloat(PreferenceManager.KEY_HAPTICS_CUSTOM_INTENSITY, 0.5f))
                    }
                    navigator.navigate(SearchScreenDestination())
                },
                modifier = Modifier.weight(1f).height(52.dp).scale(searchScale),
                shape = CircleShape,
                color = cardColor, //MaterialTheme.colorScheme.surfaceContainerHigh,
                interactionSource = searchSource
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = stringResource(R.string.search_contacts),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.search_contacts),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )

                    Icon(
                        imageVector = Icons.Rounded.MicNone,
                        contentDescription = stringResource(R.string.voice_input),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .combinedClickable(
                                onClick = {
                                    if (prefs.getBoolean(PreferenceManager.KEY_APP_HAPTICS, true)) {
                                        performAppHaptic(context, prefs.getString(PreferenceManager.KEY_APP_HAPTICS_STRENGTH, "light") ?: "light", prefs.getFloat(PreferenceManager.KEY_HAPTICS_CUSTOM_INTENSITY, 0.5f))
                                    }
                                    navigator.navigate(SearchScreenDestination(true))
                                },
                                interactionSource = micSource,
                                indication = ripple(bounded = false, radius = 22.dp)
                            ),
                    )
                    if (!prefs.getBoolean(PreferenceManager.KEY_TAB_SHOW_SETTINGS, true)) {
                        Spacer(modifier = Modifier.size(0.dp))
                        Icon(
                            painter = painterResource(id = R.drawable.ic_settings),
                            contentDescription = stringResource(R.string.settings),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .combinedClickable(
                                    onClick = {
                                        if (prefs.getBoolean(PreferenceManager.KEY_APP_HAPTICS, true)) {
                                            performAppHaptic(context, prefs.getString(PreferenceManager.KEY_APP_HAPTICS_STRENGTH, "light") ?: "light", prefs.getFloat(PreferenceManager.KEY_HAPTICS_CUSTOM_INTENSITY, 0.5f))
                                        }
                                        navigator.navigate(SettingsScreenDestination)
                                    },
                                    interactionSource = settingsSource,
                                    indication = ripple(bounded = false, radius = 22.dp)
                                ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NavigationIcon(
    onClick: () -> Unit,
    modifier: Modifier = Modifier.padding(start = 16.dp, end = 14.dp)
) {
    IconButton(
        modifier = modifier,
        onClick = onClick,
        colors = IconButtonDefaults.iconButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.96f),
            containerColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.08f)
        )
    ) {
        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
    }
}

@Composable
fun Title(
    text: String,
    bold: Boolean = true
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        lineHeight = MaterialTheme.typography.titleMedium.lineHeight,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
}
