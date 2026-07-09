package dev.goodwy.rphone.view.components

import android.annotation.SuppressLint
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import android.os.VibratorManager
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.ripple
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.repeatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.rounded.Check
import androidx.compose.runtime.getValue
import androidx.compose.ui.composed
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.controller.util.PreferenceManager
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.debugInspectorInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import dev.goodwy.rphone.BuildConfig
import dev.goodwy.rphone.R
import dev.goodwy.rphone.cardCornerBig
import dev.goodwy.rphone.cardCornerSmall
import dev.goodwy.rphone.cardSpacedBy
import dev.goodwy.rphone.liquidglass.drawBackdrop
import dev.goodwy.rphone.liquidglass.drawPlainBackdrop
import dev.goodwy.rphone.liquidglass.effects.blur
import dev.goodwy.rphone.liquidglass.effects.lens
import dev.goodwy.rphone.liquidglass.effects.colorControls
import dev.goodwy.rphone.liquidglass.highlight.Highlight
import dev.goodwy.rphone.liquidglass.LocalLiquidGlassBackdrop
import dev.goodwy.rphone.view.theme.MyColors.cardColor
import dev.goodwy.rphone.view.theme.MyColors.cardColorSelected
import dev.goodwy.rphone.view.theme.color_call_end
import dev.goodwy.rphone.view.theme.customColors
import java.util.Locale

// ─── App Haptics Helper ────────────────────────────────────────────────────────

/**
 * strength: "light" | "strong" | "custom"
 * customIntensity: 0f..1f, only used when strength == "custom"
 */
fun performAppHaptic(
    context: android.content.Context,
    strength: String,
    customIntensity: Float = 0.5f
) {
    try {
        val durationMs: Long
        val amplitude: Int
        when (strength) {
            "strong" -> { durationMs = 40; amplitude = VibrationEffect.DEFAULT_AMPLITUDE }
            "custom" -> {
                durationMs = (10 + customIntensity * 70).toLong().coerceIn(10, 80)
                amplitude  = (40  + (customIntensity * 215)).toInt().coerceIn(40, 255)
            }
            else -> { durationMs = 20; amplitude = 80 } // light
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(VibratorManager::class.java)
            vm?.defaultVibrator?.vibrate(VibrationEffect.createOneShot(durationMs, amplitude))
        } else {
            val vibrator = context.getSystemService(Vibrator::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(durationMs, amplitude))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(durationMs)
            }
        }
    } catch (_: Exception) {}
}

fun performScrollHaptic(context: android.content.Context, amplitude: Int = 60) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(VibratorManager::class.java)
            val vibrator = vm?.defaultVibrator
            val effect = VibrationEffect.createOneShot(10, amplitude.coerceIn(1, 255))
            vibrator?.vibrate(effect)
        } else {
            val vibrator = context.getSystemService(Vibrator::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = VibrationEffect.createOneShot(10, amplitude.coerceIn(1, 255))
                vibrator?.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(10L)
            }
        }
    } catch (_: Exception) {}
}

/**
 * A composable effect that triggers scroll haptics based on physical scroll distance.
 * Uses snapshotFlow to reliably track scroll position changes in real time.
 */
@Composable
fun ScrollHapticsEffect(listState: LazyListState) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val prefs = koinInject<PreferenceManager>()
    val settingsVersion by prefs.settingsChanged.collectAsState()

    val scrollHapticsEnabled = remember(settingsVersion) { prefs.getBoolean(PreferenceManager.KEY_SCROLL_HAPTICS, false) }
    val cmPerHaptic = remember(settingsVersion) { prefs.getFloat(PreferenceManager.KEY_SCROLL_CM_PER_HAPTIC, 1.5f) }
    val hapticAmplitude = remember(settingsVersion) { prefs.getInt(PreferenceManager.KEY_SCROLL_HAPTIC_STRENGTH, 60) }

    // Physical pixels per cm on this screen
    val pxPerCm = with(density) { (160f / 2.54f).dp.toPx() }
    val pxThreshold = (cmPerHaptic * pxPerCm).coerceAtLeast(8f)

    LaunchedEffect(scrollHapticsEnabled, pxThreshold, hapticAmplitude) {
        if (!scrollHapticsEnabled) return@LaunchedEffect

        var lastAbsolutePx = 0f
        var hapticBucket = 0f
        var initialized = false

        snapshotFlow {
            // Use layoutInfo so we always get the real item size, not just index+offset
            val info = listState.layoutInfo
            val firstItem = info.visibleItemsInfo.firstOrNull()
            val itemSize = firstItem?.size?.toFloat()?.takeIf { it > 0f }
                ?: info.viewportSize.height.toFloat().takeIf { it > 0f }
                ?: 1f
            val index = listState.firstVisibleItemIndex
            val offset = listState.firstVisibleItemScrollOffset
            // Absolute scroll position in pixels from top of content
            index * itemSize + offset
        }.collect { absolutePx ->
            if (!initialized) {
                lastAbsolutePx = absolutePx
                initialized = true
                return@collect
            }
            val delta = kotlin.math.abs(absolutePx - lastAbsolutePx)
            lastAbsolutePx = absolutePx
            hapticBucket += delta
            if (hapticBucket >= pxThreshold) {
                val count = (hapticBucket / pxThreshold).toInt()
                hapticBucket -= count * pxThreshold
                performScrollHaptic(context, hapticAmplitude)
            }
        }
    }
}

/**
 * Scroll haptics for LazyVerticalGrid / LazyHorizontalGrid.
 */
@Composable
fun ScrollHapticsGridEffect(gridState: androidx.compose.foundation.lazy.grid.LazyGridState) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val prefs = koinInject<PreferenceManager>()
    val settingsVersion by prefs.settingsChanged.collectAsState()

    val scrollHapticsEnabled = remember(settingsVersion) { prefs.getBoolean(PreferenceManager.KEY_SCROLL_HAPTICS, false) }
    val cmPerHaptic = remember(settingsVersion) { prefs.getFloat(PreferenceManager.KEY_SCROLL_CM_PER_HAPTIC, 1.5f) }
    val hapticAmplitude = remember(settingsVersion) { prefs.getInt(PreferenceManager.KEY_SCROLL_HAPTIC_STRENGTH, 60) }

    val pxPerCm = with(density) { (160f / 2.54f).dp.toPx() }
    val pxThreshold = (cmPerHaptic * pxPerCm).coerceAtLeast(8f)

    LaunchedEffect(scrollHapticsEnabled, pxThreshold, hapticAmplitude) {
        if (!scrollHapticsEnabled) return@LaunchedEffect

        var lastAbsolutePx = 0f
        var hapticBucket = 0f
        var initialized = false

        snapshotFlow {
            val info = gridState.layoutInfo
            val firstItem = info.visibleItemsInfo.firstOrNull()
            val itemSize = firstItem?.size?.height?.toFloat()?.takeIf { it > 0f }
                ?: info.viewportSize.height.toFloat().takeIf { it > 0f }
                ?: 1f
            val index = gridState.firstVisibleItemIndex
            val offset = gridState.firstVisibleItemScrollOffset
            index * itemSize + offset
        }.collect { absolutePx ->
            if (!initialized) {
                lastAbsolutePx = absolutePx
                initialized = true
                return@collect
            }
            val delta = kotlin.math.abs(absolutePx - lastAbsolutePx)
            lastAbsolutePx = absolutePx
            hapticBucket += delta
            if (hapticBucket >= pxThreshold) {
                val count = (hapticBucket / pxThreshold).toInt()
                hapticBucket -= count * pxThreshold
                performScrollHaptic(context, hapticAmplitude)
            }
        }
    }
}

// ─── Animated Section ──────────────────────────────────────────────────────────
/**
 * Wraps content in a staggered fade+slide-up entrance animation.
 * delayMs controls when the animation fires relative to screen entry.
 */
@Composable
fun RillAnimatedSection(
    delayMs: Long = 0L,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val prefs = koinInject<PreferenceManager>()
    val scrollAnimEnabled = remember { prefs.getBoolean(PreferenceManager.KEY_SCROLL_ANIMATION, false) }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (delayMs > 0L) delay(delayMs)
        visible = true
    }
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(280),
        label = "sectionProgress"
    )
    Box(
        modifier = modifier.then(
            if (scrollAnimEnabled) {
                modifier.graphicsLayer {
                    alpha = progress
                    translationY = (1f - progress) * 18.dp.toPx()
                }
            } else {
                modifier
            }
        )
    ) {
        content()
    }
}

// ─── Card ──────────────────────────────────────────────────────────────────────

@Composable
fun RillExpressiveCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    icon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    onTrailingIconClick: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(cardCornerBig),
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant, //cardColor,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent), // To make the dividers visible
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
//            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(cardSpacedBy)
        ) {
            if (title != null || icon != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            color = containerColor,
                            shape = RoundedCornerShape(cardCornerSmall)
                        )
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    if (icon != null) {
                        Icon(
                            icon, null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    if (title != null) {
                        Text(
                            modifier = Modifier.weight(1f),
                            text = title,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (trailingIcon != null) {
                        Spacer(Modifier.width(16.dp))
                        Icon(
                            trailingIcon, null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(18.dp)
                                .then(
                                    if (onTrailingIconClick != null) {
                                        val interactionSource = remember { MutableInteractionSource() }
                                        Modifier.combinedClickable(
                                            interactionSource = interactionSource,
                                            indication = ripple(bounded = false, radius = 18.dp),
                                            onClick = onTrailingIconClick
                                        )
                                    } else Modifier
                                )
                        )
                    }
                }
            }
            content()
        }
    }
}

// ─── Section Header ────────────────────────────────────────────────────────────

@Composable
fun RillSectionHeader(
    title: String,
    modifier: Modifier = Modifier.fillMaxWidth().padding(horizontal = 36.dp, vertical = 8.dp)
) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = modifier
    )
}

// ─── Expressive Button ─────────────────────────────────────────────────────────

@Composable
fun RillExpressiveButton(
    onClick: () -> Unit,
    icon: ImageVector,
    label: String? = null,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    size: Dp = 56.dp,
    iconSize: Dp = 28.dp,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val cornerRadius by animateDpAsState(
        targetValue = if (isPressed && enabled) (size / 4) else (size / 2f),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "ButtonShape"
    )
//    val scale by animateFloatAsState(
//        targetValue = if (isPressed && enabled) 0.91f else 1f,
//        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
//        label = "ButtonScale"
//    )

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Surface(
            onClick = if (enabled) onClick else ({}),
            modifier = Modifier
                .height(size)
                .fillMaxWidth(), //.scale(scale),
            shape = RoundedCornerShape(cornerRadius),
            color = if (enabled) containerColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
            contentColor = if (enabled) contentColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            interactionSource = interactionSource,
            shadowElevation = 0.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = label, modifier = Modifier.size(iconSize))
            }
        }
        if (label != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// ─── Stat Card ────────────────────────────────────────────────────────────────

@Composable
fun RillStatCard(
    label: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    containerColor: Color = cardColor,
    iconTint: Color = MaterialTheme.colorScheme.primary
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = containerColor,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Colored icon background
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = iconTint.copy(alpha = 0.15f),
                modifier = Modifier.size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon, null,
                        tint = iconTint,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ─── Icon Container Helper ────────────────────────────────────────────────────
/**
 * Renders a colored square icon box with translucent tinted background.
 * iconContainerColor = null → falls back to secondaryContainer theming.
 */
@Composable
internal fun RillIconBox(
    icon: ImageVector,
    iconContainerColor: Color?,
    iconBgContainerColor: Color? = null,
    modifier: Modifier = Modifier
) {
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val iconScale by animateFloatAsState(
        targetValue = if (appeared) 1f else 0.5f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "iconScale"
    )
    val iconAlpha by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(250),
        label = "iconAlpha"
    )

    val bgColor = iconBgContainerColor ?: iconContainerColor?.copy(alpha = 0.15f)
        ?: MaterialTheme.colorScheme.secondaryContainer
    val fgColor = iconContainerColor
        ?: MaterialTheme.colorScheme.onSecondaryContainer

    Surface(
        modifier = modifier
            .size(44.dp)
            .scale(iconScale)
            .alpha(iconAlpha),
        shape = CircleShape, //RoundedCornerShape(14.dp),
        color = bgColor,
        shadowElevation = 0.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon, null,
                tint = fgColor,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

// ─── List Item ────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CallLogListItem(
    headline: String,
    supporting: String? = null,
    leadingIcon: ImageVector? = null,
    iconContainerColor: Color? = null,
    iconBgContainerColor: Color? = null,
    supportingIcon: ImageVector? = null,
    supportingColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f),
    avatarName: String? = null,
    photoUri: String? = null,
    onClick: () -> Unit,
    onCallClick: () -> Unit,
    onAvatarClick: (() -> Unit)? = null,
    onAvatarLongClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    directCall: Boolean,
    isMenuOpen: Boolean = false,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    selectionMode: Boolean = false,
) {
    val context = LocalContext.current
    val prefs = koinInject<PreferenceManager>()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isMenuOpen) 0.97f else if (isPressed) 0.95f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "ListItemScale"
    )

    val interactionSourceCall = remember { MutableInteractionSource() }
    val isPressedCall by interactionSourceCall.collectIsPressedAsState()
    val scaleCall by animateFloatAsState(
        targetValue = if (isPressedCall) 1.2f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "ListItemScale"
    )

    Surface(
        color = if (isSelected) cardColorSelected else cardColor,
        modifier = modifier
            .fillMaxWidth()
            .scale(scale),
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = ripple(),
                    onClick = {
                        if (prefs.getBoolean(PreferenceManager.KEY_APP_HAPTICS, true)) {
                            performAppHaptic(
                                context,
                                prefs.getString(PreferenceManager.KEY_APP_HAPTICS_STRENGTH, "light")
                                    ?: "light",
                                prefs.getFloat(PreferenceManager.KEY_HAPTICS_CUSTOM_INTENSITY, 0.5f)
                            )
                        }
                        if (directCall) onCallClick() else onClick()
                    },
                    onLongClick = onLongClick
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectionMode && isSelected) {
                var appeared by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { appeared = true }
                val iconScale by animateFloatAsState(
                    targetValue = if (appeared) 1f else 0.5f,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label = "iconScale"
                )
                val iconAlpha by animateFloatAsState(
                    targetValue = if (appeared) 1f else 0f,
                    animationSpec = tween(250),
                    label = "iconAlpha"
                )
                RillAvatar(
                    name = "",
                    modifier = Modifier.size(48.dp)
                        .scale(iconScale)
                        .alpha(iconAlpha),
                    icon = Icons.Rounded.Check,
                    iconContainerColor = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(16.dp))
            } else if (avatarName != null || photoUri != null) {
                RillAvatar(
                    name = avatarName ?: "",
                    photoUri = photoUri,
                    modifier = Modifier.size(48.dp)
                        .then(
                            if (onAvatarClick != null)
                                Modifier.combinedClickable(
                                    interactionSource = null,
                                    indication = ripple(bounded = false, radius = 32.dp),
                                    onClick = onAvatarClick,
                                    onLongClick = onAvatarLongClick
                                )
                            else Modifier
                        )
                )
                Spacer(modifier = Modifier.width(16.dp))
            } else if (leadingIcon != null) {
                RillIconBox(
                    icon = leadingIcon,
                    iconContainerColor = iconContainerColor,
                    iconBgContainerColor = iconBgContainerColor
                )
                Spacer(modifier = Modifier.width(16.dp))
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = headline,
                    style = MaterialTheme.typography.bodyLarge,
//                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (supportingIcon != null || supporting != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (supportingIcon != null) {
                            Icon(
                                supportingIcon, null,
                                tint = supportingColor,
                                modifier = Modifier.size(MaterialTheme.typography.bodyLarge.fontSize.value.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }

                        if (supporting != null) {
                            Text(
                                text = supporting,
                                style = MaterialTheme.typography.bodyMedium,
                                color = supportingColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Icon(
                imageVector = if (directCall) Icons.Outlined.Info else Icons.Outlined.Phone, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(28.dp)
                    .scale(scaleCall)
                    .combinedClickable(
                        interactionSource = interactionSourceCall,
                        indication = ripple(bounded = false, radius = 28.dp),
                        onClick = {
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
                            if (directCall) onClick() else onCallClick()
                        },
                        onLongClick = if (!selectionMode) onAvatarLongClick else onLongClick
                    )
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CallLogListItemSimple(
    headline: String,
    supporting: String? = null,
    trailing: String? = null,
    leadingIcon: ImageVector? = null,
    iconContainerColor: Color? = null,
    iconBgContainerColor: Color? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    isMenuOpen: Boolean = false,
    selected: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = koinInject<PreferenceManager>()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isMenuOpen) 0.97f else if (isPressed) 0.95f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "ListItemScale"
    )

    Surface(
        color = if (selected) cardColorSelected else cardColor,
        shape = RoundedCornerShape(cardCornerSmall),
        modifier = modifier
            .fillMaxWidth()
            .scale(scale),
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = ripple(),
                    onClick = {
                        if (prefs.getBoolean(PreferenceManager.KEY_APP_HAPTICS, true)) {
                            performAppHaptic(
                                context,
                                prefs.getString(PreferenceManager.KEY_APP_HAPTICS_STRENGTH, "light")
                                    ?: "light",
                                prefs.getFloat(PreferenceManager.KEY_HAPTICS_CUSTOM_INTENSITY, 0.5f)
                            )
                        }
                        onClick()
                    },
                    onLongClick = onLongClick
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selected) {
                RillIconBox(
                    icon = Icons.Rounded.Check,
                    iconContainerColor = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(16.dp))
            } else if (leadingIcon != null) {
                RillIconBox(
                    icon = leadingIcon,
                    iconContainerColor = iconContainerColor,
                    iconBgContainerColor = iconBgContainerColor
                )
                Spacer(modifier = Modifier.width(16.dp))
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = headline,
                    style = MaterialTheme.typography.bodyLarge,
//                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (supporting != null) {
                    Text(
                        text = supporting,
                        style = MaterialTheme.typography.bodyMedium,
                        color = iconContainerColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (trailing != null) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = trailing,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RillListItem(
    headline: String,
    supporting: String? = null,
    leadingIcon: ImageVector? = null,
    iconContainerColor: Color? = null,
    iconBgContainerColor: Color? = null,
    trailingIcon: ImageVector? = null,
    preTrailingIcon: ImageVector? = null,
    avatarName: String? = null,
    photoUri: String? = null,
    onClick: () -> Unit,
    onAvatarClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    isMenuOpen: Boolean = false,
    modifier: Modifier = Modifier,
    modifierLeadingIcon: Modifier = Modifier,
    modifierTrailingIcon: Modifier = Modifier.size(20.dp),
    modifierPreTrailingIcon: Modifier = Modifier.size(20.dp)
) {
    val context = LocalContext.current
    val prefs = koinInject<PreferenceManager>()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isMenuOpen) 0.97f else if (isPressed) 0.95f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "ListItemScale"
    )

    Surface(
        color = cardColor,
        shape = RoundedCornerShape(cardCornerSmall),
        modifier = modifier
            .fillMaxWidth()
            .scale(scale),
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = ripple(),
                    onClick = {
                        if (prefs.getBoolean(PreferenceManager.KEY_APP_HAPTICS, true)) {
                            performAppHaptic(
                                context,
                                prefs.getString(PreferenceManager.KEY_APP_HAPTICS_STRENGTH, "light")
                                    ?: "light",
                                prefs.getFloat(PreferenceManager.KEY_HAPTICS_CUSTOM_INTENSITY, 0.5f)
                            )
                        }
                        onClick()
                    },
                    onLongClick = onLongClick
                )
                .padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (avatarName != null || photoUri != null) {
                RillAvatar(
                    name = avatarName ?: "",
                    photoUri = photoUri,
                    modifier = Modifier
                        .size(48.dp)
                        .then(
                            if (onAvatarClick != null)
                                Modifier.combinedClickable(onClick = onAvatarClick)
                            else Modifier
                        )
                )
                Spacer(modifier = Modifier.width(16.dp))
            } else if (leadingIcon != null) {
                RillIconBox(
                    icon = leadingIcon,
                    iconContainerColor = iconContainerColor,
                    iconBgContainerColor = iconBgContainerColor,
                    modifier = modifierLeadingIcon
                )
                Spacer(modifier = Modifier.width(16.dp))
            } else {
                Spacer(modifier = Modifier.width(8.dp))
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = headline,
                    style = MaterialTheme.typography.bodyLarge,
//                    fontWeight = FontWeight.SemiBold,
                    lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (supporting != null) {
                    Text(
                        text = supporting,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
//                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (preTrailingIcon != null) {
                Spacer(modifier = Modifier.width(12.dp))
                Icon(
                    preTrailingIcon, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = modifierPreTrailingIcon
                )
            }

            if (trailingIcon != null) {
                Spacer(modifier = Modifier.width(12.dp))
                Icon(
                    trailingIcon, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = modifierTrailingIcon
                )
            }
        }
    }
}

// ─── Switch List Item ─────────────────────────────────────────────────────────

@Composable
fun RillSwitchListItem(
    headline: String,
    supporting: String? = null,
    leadingIcon: ImageVector? = null,
    iconContainerColor: Color? = null,
    iconBgContainerColor: Color? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val prefs = koinInject<PreferenceManager>()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "SwitchItemScale"
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
            onCheckedChange(!checked)
        },
        shape = RoundedCornerShape(cardCornerSmall),
        color = cardColor, //Color.Transparent,
        modifier = modifier
            .fillMaxWidth()
            .scale(scale),
        shadowElevation = 0.dp,
        interactionSource = interactionSource
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingIcon != null) {
                RillIconBox(
                    icon = leadingIcon,
                    iconContainerColor = iconContainerColor,
                    iconBgContainerColor = iconBgContainerColor
                )
                Spacer(modifier = Modifier.width(16.dp))
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = headline,
                    style = MaterialTheme.typography.bodyLarge,
                    lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
//                    fontWeight = FontWeight.SemiBold
                )
                if (supporting != null) {
                    Text(
                        text = supporting,
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
            )
        }
    }
}

@Composable
fun RillSelectListItem(
    headline: String,
    supporting: String? = null,
    leadingIcon: ImageVector? = null,
    iconContainerColor: Color? = null,
    iconBgContainerColor: Color? = null,
    options: List<Pair<String, Int>>,
    selectedValue: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = koinInject<PreferenceManager>()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "SelectItemScale"
    )
    var showSelectionScreen by remember { mutableStateOf(false) }

    Surface(
        onClick = {
            if (prefs.getBoolean(PreferenceManager.KEY_APP_HAPTICS, true)) {
                performAppHaptic(
                    context,
                    prefs.getString(PreferenceManager.KEY_APP_HAPTICS_STRENGTH, "light") ?: "light",
                    prefs.getFloat(PreferenceManager.KEY_HAPTICS_CUSTOM_INTENSITY, 0.5f)
                )
            }
            showSelectionScreen = true
        },
        shape = RoundedCornerShape(cardCornerSmall),
        color = cardColor,
        modifier = modifier
            .fillMaxWidth()
            .scale(scale),
        shadowElevation = 0.dp,
        interactionSource = interactionSource
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingIcon != null) {
                RillIconBox(
                    icon = leadingIcon,
                    iconContainerColor = iconContainerColor,
                    iconBgContainerColor = iconBgContainerColor
                )
                Spacer(modifier = Modifier.width(16.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = headline,
                    style = MaterialTheme.typography.bodyLarge,
                    lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
//                    fontWeight = FontWeight.SemiBold
                )
                if (supporting != null) {
                    Text(
                        text = supporting,
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Select option",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp)
            )
        }
    }

    if (showSelectionScreen) {
        RillSelectionDialog(
            onDismissRequest = { showSelectionScreen = false },
            title = headline,
            icon = leadingIcon,
            iconContainerColor = iconContainerColor,
            iconBgContainerColor = iconBgContainerColor,
            items = options,
            itemLabel = { it.first },
            onItemSelected = { onValueChange(it.second) },
            isSelected = { it.second == selectedValue }
        )
    }
}

@Composable
fun RillDialog(
    onDismissRequest: () -> Unit,
    title: String? = null,
    icon: ImageVector? = null,
    iconContainerColor: Color? = null,
    iconBgContainerColor: Color? = null,
    confirmButton: (@Composable () -> Unit)? = null,
    dismissButton: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val showState = remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { showState.value = true }
    val prefs = koinInject<PreferenceManager>()
    val settingsState by prefs.settingsChanged.collectAsState()
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
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 32.dp, bottom = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (icon != null) {
                            Surface(
                                modifier = Modifier.size(64.dp),
                                shape = RoundedCornerShape(20.dp),
                                color = iconBgContainerColor ?: MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(32.dp),
                                        tint = iconContainerColor ?: LocalContentColor.current
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        if (title != null) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp, vertical = 24.dp),
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
fun RillFilterChip(
    label: String,
    selected: Boolean,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: @Composable (() -> Unit)? = null
) {
    val containerColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surface,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "chipColor"
    )
    val labelColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurface,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "chipLabelColor"
    )
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.04f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium, dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "chipScale"
    )
    FilterChip(
        modifier = modifier.scale(scale),
        selected = selected,
        onClick = { onClick(label) },
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
        },
        shape = RoundedCornerShape(12.dp),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = containerColor,
            labelColor = labelColor,
            selectedContainerColor = containerColor,
            selectedLabelColor = labelColor
        ),
        elevation = FilterChipDefaults.filterChipElevation(elevation = 0.dp)
    )
}

@Composable
fun RillConfirmationDialog(
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    title: String,
    message: String,
    confirmLabel: String = "Confirm",
    dismissLabel: String = stringResource(R.string.cancel),
    icon: ImageVector? = null,
    isDestructive: Boolean = false
) {
    RillDialog(
        onDismissRequest = onDismissRequest,
        title = title,
        icon = icon,
        confirmButton = {
            Button(
                onClick = {
                    onConfirm()
                    onDismissRequest()
                },
                colors = if (isDestructive) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) else ButtonDefaults.buttonColors(),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text(
                    confirmLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            FilledTonalButton(
                onClick = onDismissRequest,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text(
                    dismissLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    ) {
        Text(
            message,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = TextUnit.Unspecified
        )
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
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer.copy(
                                alpha = 0.5f
                            )
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    itemIcon(item),
                                    null,
                                    tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
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
                            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
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

// ─── Scroll Animated Item ─────────────────────────────────────────────────────

/**
 * Wraps a list item with a scroll-in fade+slide animation, controlled by the
 * scroll animation preference. Uses a unique composition key so that each time
 * the item is composed (or re-composed after filter changes), the entrance
 * animation replays correctly.
 */
@Composable
fun RillScrollAnimatedItem(
    delayMs: Long = 0L,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val prefs = koinInject<PreferenceManager>()
    val scrollAnimEnabled = remember { prefs.getBoolean(PreferenceManager.KEY_SCROLL_ANIMATION, false) }

    if (scrollAnimEnabled) {
        // Use a key that changes each time this composable enters composition,
        // ensuring LaunchedEffect(Unit) inside RillAnimatedSection always fires
        // fresh — both on first load and when the item scrolls back into view.
        val animKey = remember { Any() }
        key(animKey) {
            RillAnimatedSection(delayMs = delayMs, modifier = modifier, content = content)
        }
    } else {
        Box(modifier = modifier) { content() }
    }
}

// ─── Rill Dropdown Menu ───────────────────────────────────────────────────────

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
    val settingsVer by prefs.settingsChanged.collectAsState()
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
    val settingsVer2 by prefs2.settingsChanged.collectAsState()
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

/**
 * A FAB that optionally shows a background-only blur effect (frosted glass).
 * When [useBlur] is true and API >= 31, a blurred background layer is drawn
 * behind the content so the icon remains sharp and fully readable.
 */
@Composable
fun RillBlurFab(
    onClick: () -> Unit,
    shape: RoundedCornerShape,
    useBlur: Boolean,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    if (useBlur) {
        FloatingActionButton(
            onClick = onClick,
            containerColor = containerColor,
            contentColor = contentColor,
            shape = shape,
            elevation = FloatingActionButtonDefaults.elevation(0.dp),
            modifier = modifier
        ) { content() }
    } else {
        FloatingActionButton(
            onClick = onClick,
            containerColor = containerColor,
            contentColor = contentColor,
            shape = shape,
            elevation = FloatingActionButtonDefaults.elevation(0.dp),
            modifier = modifier
        ) { content() }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SupportProjectItem(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = koinInject<PreferenceManager>()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "ListItemScale"
    )

    Surface(
        color = MaterialTheme.colorScheme.customColors.colorPurple.copy(0.9f), //cardColor,
        shape = RoundedCornerShape(cardCornerBig),
        modifier = modifier
            .fillMaxWidth()
            .scale(scale),
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = ripple(),
                    onClick = {
                        if (prefs.getBoolean(PreferenceManager.KEY_APP_HAPTICS, true)) {
                            performAppHaptic(
                                context,
                                prefs.getString(PreferenceManager.KEY_APP_HAPTICS_STRENGTH, "light") ?: "light",
                                prefs.getFloat(PreferenceManager.KEY_HAPTICS_CUSTOM_INTENSITY, 0.5f)
                            )
                        }
                        onClick()
                    },
                )
                .padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.Top
        ) {
            var appeared by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { appeared = true }
            val iconScale by animateFloatAsState(
                targetValue = if (appeared) 1f else 0.5f,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "iconScale"
            )
            val iconAlpha by animateFloatAsState(
                targetValue = if (appeared) 1f else 0f,
                animationSpec = tween(250),
                label = "iconAlpha"
            )

            Surface(
                modifier = Modifier
                    .size(44.dp)
                    .scale(iconScale)
                    .alpha(iconAlpha),
                shape = RoundedCornerShape(14.dp),
                color = Color.White,
                shadowElevation = 0.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        ImageVector.vectorResource(id = R.drawable.ic_plus_support), null,
                        tint = MaterialTheme.colorScheme.customColors.colorDarkPurple,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val isGPlay = BuildConfig.FLAVOR == "gplay"
                Text(
                    text = if (isGPlay) stringResource(R.string.project_support)
                            else stringResource(R.string.support_development),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.customColors.colorDarkPurple,
                    lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (isGPlay) stringResource(R.string.project_support_summary)
                            else stringResource(R.string.support_development_description3),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.customColors.colorDarkPurple,
                    lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
//                        maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@SuppressLint("SuspiciousModifierThen")
@Composable
fun Modifier.shake(enabled: Boolean, onAnimationFinish: () -> Unit): Modifier = then(
    composed(
        factory = {
            val distance by animateFloatAsState(
                targetValue = if (enabled) 12f else 0f,
                animationSpec = repeatable(
                    iterations = 3,
                    animation = tween(durationMillis = 70, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                finishedListener = { onAnimationFinish.invoke() }, label = ""
            )

            Modifier.graphicsLayer {
                translationX = if (enabled) distance else 0f
            }
        },
        inspectorInfo = debugInspectorInfo {
            name = "shake"
            properties["enabled"] = enabled
        }
    )
)
