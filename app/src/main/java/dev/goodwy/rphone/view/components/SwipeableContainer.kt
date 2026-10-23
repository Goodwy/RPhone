package dev.goodwy.rphone.view.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.R
import dev.goodwy.rphone.modal.data.SwipeActionType
import dev.goodwy.rphone.view.theme.customColors
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun SwipeableContainer(
    enabled: Boolean,
    haptics: Boolean,
    hapticsStrength: String,
    hapticsIntensity: Float,
    swipeRightAction: SwipeActionType = SwipeActionType.CALL,
    swipeLeftAction: SwipeActionType = SwipeActionType.MESSAGE,
    swipeLeftSecondAction: SwipeActionType = SwipeActionType.DELETE,
    onSwipeRight: () -> Unit, // Call
    onSwipeLeft: () -> Unit,  // Message
    onSwipeLeftSecond: () -> Unit, // Delete
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    if (!enabled || (swipeRightAction == SwipeActionType.NONE && swipeLeftAction == SwipeActionType.NONE)) {
        Box(modifier = modifier) {
            content()
        }
        return
    }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val layoutDirection = LocalLayoutDirection.current
    val isRtl = layoutDirection == LayoutDirection.Rtl

    val singleThreshold = with(LocalDensity.current) { 100.dp.toPx() }
    val effectiveLeftSecond = if (swipeLeftAction == SwipeActionType.NONE) {
        SwipeActionType.NONE
    } else {
        swipeLeftSecondAction
    }
    val threshold = if (effectiveLeftSecond != SwipeActionType.NONE) {
        with(LocalDensity.current) { 150.dp.toPx() }
    } else {
        singleThreshold
    }

    val width = 48.dp
    val widthFloat = with(LocalDensity.current) { width.toPx() }
    val height = width - 12.dp
    val iconSpacing = 16.dp
    val iconSpacingFloat = with(LocalDensity.current) { iconSpacing.toPx() }

    var triggeredRight by remember { mutableStateOf(false) }
    var triggeredLeft by remember { mutableStateOf(false) }
    var triggeredLeftSecond by remember { mutableStateOf(false) }

    // For RTL, we invert the offset for display and triggers
    val displayOffset = if (isRtl) -offsetX.value else offsetX.value

    val startFractionLeft = widthFloat / singleThreshold
    val leftProgress = ((-displayOffset / singleThreshold - startFractionLeft) / (1f - startFractionLeft)).coerceIn(0f, 1f)

    val startFractionLeftSecond = (widthFloat * 2 + iconSpacingFloat) / threshold
    val leftSecondProgress = (((-displayOffset / threshold) - startFractionLeftSecond) / (1f - startFractionLeftSecond)).coerceIn(0f, 1f)

    val startFractionCall = widthFloat / singleThreshold
    val rightProgress = ((displayOffset / singleThreshold - startFractionCall) / (1f - startFractionCall)).coerceIn(0f, 1f)

    val rightTriggerScale by animateFloatAsState(
        targetValue = if (triggeredRight) 1.2f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "rightTriggerScale"
    )
    val leftTriggerScale by animateFloatAsState(
        targetValue = if (triggeredLeft) 1.2f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "leftTriggerScale"
    )
    val leftSecondTriggerScale by animateFloatAsState(
        targetValue = if (triggeredLeftSecond) 1.2f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "leftSecondTriggerScale"
    )

    Box(
        modifier = modifier
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta ->
                    coroutineScope.launch {
                        val maxRight = when {
                            swipeRightAction == SwipeActionType.NONE -> 0f
                            isRtl -> threshold * 1.2f
                            else -> singleThreshold * 1.2f
                        }
                        val maxLeft = when {
                            swipeLeftAction == SwipeActionType.NONE -> 0f
                            isRtl -> singleThreshold * 1.2f
                            else -> threshold * 1.2f
                        }
                        val newOffset = (offsetX.value + delta * 0.6f).coerceIn(-maxLeft, maxRight)
                        offsetX.snapTo(newOffset)

                        val checkOffset = if (isRtl) -newOffset else newOffset

                        // To the right (Call)
                        if (swipeRightAction != SwipeActionType.NONE) {
                            if (checkOffset >= singleThreshold && !triggeredRight) {
                                triggeredRight = true
                                if (haptics) {
                                    performAppHaptic(context, hapticsStrength, hapticsIntensity)
                                }
                            } else if (checkOffset < singleThreshold && triggeredRight) {
                                triggeredRight = false
                            }
                        }

                        // Left 1 (Message)
                        if (swipeLeftAction != SwipeActionType.NONE) {
                            if (checkOffset <= -singleThreshold && !triggeredLeft) {
                                triggeredLeft = true
                                if (haptics) {
                                    performAppHaptic(context, hapticsStrength, hapticsIntensity)
                                }
                            } else if (checkOffset > -singleThreshold && triggeredLeft) {
                                triggeredLeft = false
                            }
                        }

                        // Left 2 (Delete)
                        if (effectiveLeftSecond != SwipeActionType.NONE) {
                            if (checkOffset <= -threshold && !triggeredLeftSecond) {
                                triggeredLeftSecond = true
                                if (haptics) {
                                    performAppHaptic(context, hapticsStrength, hapticsIntensity)
                                }
                            } else if (checkOffset > -threshold && triggeredLeftSecond) {
                                triggeredLeftSecond = false
                            }
                        }
                    }
                },
                onDragStopped = {
                    coroutineScope.launch {
                        val finalDisplayOffset = if (isRtl) -offsetX.value else offsetX.value

                        if (swipeRightAction != SwipeActionType.NONE && finalDisplayOffset >= singleThreshold) {
                            onSwipeRight()
                        } else if (effectiveLeftSecond != SwipeActionType.NONE && finalDisplayOffset <= -threshold) {
                            onSwipeLeftSecond()
                        } else if (swipeLeftAction != SwipeActionType.NONE && finalDisplayOffset <= -singleThreshold) {
                            onSwipeLeft()
                        }

                        offsetX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow))
                        triggeredRight = false
                        triggeredLeft = false
                        triggeredLeftSecond = false
                    }
                }
            )
    ) {
        // Background Actions
        Row(
            modifier = Modifier
                .matchParentSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (displayOffset > 0) {
                Surface(
                    modifier = Modifier
                        .padding(start = 16.dp)
                        .size(width, height)
                        .graphicsLayer {
                            val scale = 0.4f + rightProgress * 0.6f * rightTriggerScale
                            scaleX = scale
                            scaleY = scale
                            alpha = rightProgress
                        },
                    shape = RoundedCornerShape(50),
                    color = swipeRightAction.containerColor()
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = swipeRightAction.icon(),
                            contentDescription = null,
                            tint = swipeRightAction.contentColor(),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            if (displayOffset < 0) {
                if (effectiveLeftSecond != SwipeActionType.NONE) {
                    Surface(
                        modifier = Modifier
                            .padding(end = iconSpacing)
                            .size(width, height)
                            .graphicsLayer {
                                val scale = 0.4f + leftSecondProgress * 0.6f * leftSecondTriggerScale
                                scaleX = scale
                                scaleY = scale
                                alpha = leftSecondProgress
                            },
                        shape = RoundedCornerShape(50),
                        color = effectiveLeftSecond.containerColor()
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = effectiveLeftSecond.icon(),
                                contentDescription = null,
                                tint = effectiveLeftSecond.contentColor(),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                Surface(
                    modifier = Modifier
                        .padding(end = 16.dp)
                        .size(width, height)
                        .graphicsLayer {
                            val scale = 0.4f + leftProgress * 0.6f * leftTriggerScale
                            scaleX = scale
                            scaleY = scale
                            alpha = leftProgress
                        },
                    shape = RoundedCornerShape(50),
                    color = swipeLeftAction.containerColor()
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = swipeLeftAction.icon(),
                            contentDescription = null,
                            tint = swipeLeftAction.contentColor(),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }

        // Foreground Content
        Box(
            modifier = Modifier
                .offset { IntOffset(displayOffset.roundToInt(), 0) }
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            content()
        }
    }
}