package dev.goodwy.rphone.view.components

import android.content.res.Configuration
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.liquidglass.LocalLiquidGlassBackdrop
import dev.goodwy.rphone.liquidglass.drawBackdrop
import dev.goodwy.rphone.liquidglass.drawPlainBackdrop
import dev.goodwy.rphone.liquidglass.effects.blur
import dev.goodwy.rphone.liquidglass.effects.colorControls
import dev.goodwy.rphone.liquidglass.effects.lens
import dev.goodwy.rphone.liquidglass.highlight.Highlight
import dev.goodwy.rphone.view.theme.MyColors.cardColor
import dev.goodwy.rphone.view.theme.color_call_end
import org.koin.compose.koinInject

@Composable
fun RillDialog(
    onDismissRequest: () -> Unit,
    title: String? = null,
    icon: ImageVector? = null,
    modifierIcon: Modifier = Modifier,
    iconContainerColor: Color? = null,
    iconBgContainerColor: Color? = null,
    confirmButton: (@Composable () -> Unit)? = null,
    dismissButton: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val showState = remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { showState.value = true }
    val prefs = koinInject<PreferenceManager>()
    val settingsState by prefs.settingsChanged.collectAsStateWithLifecycle()
    val roundness = remember(settingsState) { prefs.getInt(PreferenceManager.KEY_CARD_ROUNDNESS, 28) }

    val scale by animateFloatAsState(
        targetValue = if (showState.value) 1f else 0.95f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "DialogScale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (showState.value) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "DialogAlpha"
    )

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                    }
                    .animateContentSize(),
                shape = RoundedCornerShape(roundness.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.padding(bottom = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header Area
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 24.dp, bottom = 16.dp)
                            .padding(horizontal = 24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (icon != null) {
                            Surface(
                                modifier = Modifier.size(44.dp),
                                shape = RoundedCornerShape(12.dp),
                                color = iconBgContainerColor
                                    ?: MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        modifier = modifierIcon.size(24.dp),
                                        tint = iconContainerColor ?: LocalContentColor.current
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(20.dp))
                        }

                        if (title != null) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.headlineSmall,
                                lineHeight = MaterialTheme.typography.titleMedium.lineHeight,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Start,
                                modifier = Modifier.weight(1f).padding(end = 8.dp)
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        content()
                    }

                    if (confirmButton != null || dismissButton != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (dismissButton != null) {
                                Box(modifier = Modifier.weight(1f)) {
                                    dismissButton()
                                }
                            }
                            if (confirmButton != null) {
                                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                                    confirmButton()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun <T> RillSelectionDialog(
    onDismissRequest: () -> Unit,
    title: String,
    items: List<T>,
    itemLabel: (T) -> String,
    onItemSelected: (T) -> Unit,
    itemSupporting: ((T) -> String)? = null,
    icon: ImageVector? = null,
    iconContainerColor: Color? = null,
    iconBgContainerColor: Color? = null,
    itemIcon: @Composable ((T) -> ImageVector)? = null,
    isSelected: (T) -> Boolean = { false },
) {
    RillDialog(
        onDismissRequest = onDismissRequest,
        title = title,
        icon = icon,
        iconContainerColor = iconContainerColor,
        iconBgContainerColor = iconBgContainerColor,
        dismissButton = {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismissRequest) { Text(stringResource(R.string.cancel)) }
            }
        }
    ) {
        items.forEach { item ->
            val selected = isSelected(item)
            val interactionSource = remember { MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()
            val cornerRadius by animateDpAsState(
                if (selected || isPressed) 40.dp else 10.dp,
                spring(stiffness = Spring.StiffnessMediumLow),
                label = "ButtonShapeAnimation"
            )
            Surface(
                onClick = {
                    onItemSelected(item)
                    onDismissRequest()
                },
                shape = RoundedCornerShape(cornerRadius),
                color = if (selected) MaterialTheme.colorScheme.primaryContainer else cardColor,
                modifier = Modifier.fillMaxWidth(),
                interactionSource = interactionSource
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = if (itemIcon != null) 8.dp else 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (itemIcon != null) {
                        Surface(
                            modifier = Modifier.size(44.dp),
                            shape = CircleShape, //RoundedCornerShape(12.dp),
                            color = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    itemIcon(item),
                                    null,
                                    tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(16.dp))
                    } else {
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            itemLabel(item),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurface
                        )
                        if (itemSupporting != null) {
                            Text(
                                itemSupporting(item),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer.copy(
                                    alpha = 0.8f
                                ) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (selected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Styled context menu that matches Rill Phone's card-based design.
 * Uses a Popup so the menu is statically positioned without jumping on finger release.
 * The shadow is rendered by Compose's draw.shadow (not the window elevation)
 * so it clips correctly to the rounded shape on all API levels.
 */
@Composable
fun RillDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val prefs = koinInject<PreferenceManager>()
    val settingsVer by prefs.settingsChanged.collectAsStateWithLifecycle()
    val liquidGlass = remember(settingsVer) { prefs.getBoolean(PreferenceManager.KEY_LIQUID_GLASS, false) }
    val lgDropdownMenu = remember(settingsVer) { prefs.getBoolean(PreferenceManager.KEY_LG_DROPDOWN_MENU, true) }
    val blurEffects = remember(settingsVer) { prefs.getBoolean(PreferenceManager.KEY_BLUR_EFFECTS, false) }
    val blurDropdownMenu = remember(settingsVer) { prefs.getBoolean(PreferenceManager.KEY_BLUR_DROPDOWN_MENU, true) }
    var showContent by remember { mutableStateOf(false) }

    LaunchedEffect(expanded) {
        if (expanded) showContent = true
    }

    if (showContent) {
        Dialog(
            onDismissRequest = onDismissRequest,
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            val dimAlpha by animateFloatAsState(
                targetValue = if (expanded) 0.45f else 0f,
                animationSpec = tween(320),
                label = "dimAlpha",
                finishedListener = { if (!expanded) showContent = false }
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = dimAlpha))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismissRequest
                    ),
                contentAlignment = Alignment.Center
            ) {
                AnimatedVisibility(
                    visible = expanded,
                    enter = scaleIn(
                        animationSpec = spring(
                            stiffness = Spring.StiffnessLow,
                            dampingRatio = Spring.DampingRatioMediumBouncy
                        ),
                        initialScale = 0.75f,
                        transformOrigin = TransformOrigin(0.5f, 0.5f)
                    ) + fadeIn(tween(280)),
                    exit = scaleOut(
                        animationSpec = tween(220, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                        targetScale = 0.85f,
                        transformOrigin = TransformOrigin(0.5f, 0.5f)
                    ) + fadeOut(tween(200))
                ) {
                    val menuShape = RoundedCornerShape(35.dp)
                    val globalBackdrop = LocalLiquidGlassBackdrop.current
                    val useLgDropdown = liquidGlass && lgDropdownMenu && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && globalBackdrop != null
                    val useBlurDropdown = blurEffects && blurDropdownMenu && !useLgDropdown

                    Box(
                        modifier = modifier
                            .width(260.dp)
                            .then(
                                if (useLgDropdown) Modifier
                                else Modifier.shadow(
                                    elevation = 16.dp,
                                    shape = RoundedCornerShape(24.dp),
                                    spotColor = Color.Black.copy(alpha = 0.28f),
                                    ambientColor = Color.Black.copy(alpha = 0.12f)
                                )
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {}
                            )
                    ) {
                        val dropdownShape = if (useLgDropdown) menuShape else RoundedCornerShape(24.dp)
                        if (useLgDropdown) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .drawBackdrop(
                                        backdrop = globalBackdrop,
                                        shape = { menuShape },
                                        effects = {
                                            val d = density
                                            colorControls(brightness = -0.13f, saturation = 1.4f)
                                            blur(6f * d)
                                            lens(
                                                refractionHeight = 40f * d,
                                                refractionAmount = 248f * d
                                            )
                                        },
                                        highlight = { Highlight.Plain }
                                    ),
                                shape = menuShape,
                                color = Color.Black.copy(alpha = 0.25f),
                                tonalElevation = 0.dp
                            ) {
                                Column(modifier = Modifier.padding(vertical = 8.dp)) { content() }
                            }
                        } else if (useBlurDropdown && globalBackdrop != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .drawPlainBackdrop(
                                        backdrop = globalBackdrop,
                                        shape = { dropdownShape },
                                        effects = { blur(30f * density) }
                                    ),
                                shape = dropdownShape,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f),
                                tonalElevation = 0.dp
                            ) {
                                Column(modifier = Modifier.padding(vertical = 8.dp)) { content() }
                            }
                        } else {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = dropdownShape,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                tonalElevation = 0.dp
                            ) {
                                Column(modifier = Modifier.padding(vertical = 8.dp)) { content() }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * A single styled item for [RillDropdownMenu].
 * Icons are rendered inside a tinted rounded box matching the app's icon containers.
 * Supports destructive (error-coloured) styling.
 */
@Composable
fun RillDropdownMenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    isDestructive: Boolean = false
) {
    val prefs2 = koinInject<PreferenceManager>()
    val settingsVer2 by prefs2.settingsChanged.collectAsStateWithLifecycle()
    val liquidGlass2 = remember(settingsVer2) { prefs2.getBoolean(PreferenceManager.KEY_LIQUID_GLASS, false) }
    val lgDropdown   = remember(settingsVer2) { prefs2.getBoolean(PreferenceManager.KEY_LG_DROPDOWN_MENU, true) }

    // Text color: white only when liquid glass dropdown is fully active
    val textColor  = when {
        isDestructive          -> MaterialTheme.colorScheme.error
        liquidGlass2 && lgDropdown -> Color.White
        else                   -> MaterialTheme.colorScheme.onSurface
    }
    val tintColor  = if (isDestructive) MaterialTheme.colorScheme.error else iconTint
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "rMenuItemScale"
    )

    Surface(
        onClick = onClick,
        color = Color.Transparent,
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        shape = RoundedCornerShape(12.dp),
        interactionSource = interactionSource
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (icon != null) {
                // solid = LG on AND dropdown toggle on → fully opaque icon bg
                // translucent = LG off OR LG on but dropdown toggle off → 0.15f alpha (same as settings icons)
                val solidMode = liquidGlass2 && lgDropdown
                val iconBgColor = when {
                    solidMode && isDestructive -> color_call_end
                    solidMode -> tintColor.copy(
                        red   = (tintColor.red   * 1.15f).coerceAtMost(1f),
                        green = (tintColor.green * 1.15f).coerceAtMost(1f),
                        blue  = (tintColor.blue  * 1.15f).coerceAtMost(1f),
                        alpha = 1f
                    )
                    else -> tintColor.copy(alpha = 0.15f)
                }
                val iconTintColor = if (solidMode) Color.White else tintColor

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = iconBgColor,
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector        = icon,
                            contentDescription = null,
                            tint               = iconTintColor,
                            modifier           = Modifier.size(18.dp)
                        )
                    }
                }
            }
            Text(
                text       = text,
                style      = MaterialTheme.typography.bodyLarge,
                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight,
                color      = textColor,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
