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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.R
import dev.goodwy.rphone.view.theme.customColors
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun SwipeableCallLogContainer(
    enabled: Boolean,
    onSwipeRight: () -> Unit, // Call
    onSwipeLeft: () -> Unit,  // Message
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    if (!enabled) {
        Box(modifier = modifier) {
            content()
        }
        return
    }

    val coroutineScope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val haptic = LocalHapticFeedback.current
    val layoutDirection = LocalLayoutDirection.current
    val isRtl = layoutDirection == LayoutDirection.Rtl

    val singleThreshold = with(LocalDensity.current) { 100.dp.toPx() }
    val threshold = with(LocalDensity.current) { 150.dp.toPx() }

    val width = 48.dp
    val widthFloat = with(LocalDensity.current) { width.toPx() }
    val height = width - 12.dp
    val iconSpacing = 16.dp
    val iconSpacingFloat = with(LocalDensity.current) { iconSpacing.toPx() }

    var triggeredRight by remember { mutableStateOf(false) }
    var triggeredMessage by remember { mutableStateOf(false) }
    var triggeredDelete by remember { mutableStateOf(false) }

    // For RTL, we invert the offset for display and triggers
    val displayOffset = if (isRtl) -offsetX.value else offsetX.value

    val startFractionMsg = widthFloat / singleThreshold
    val leftProgressMsg = ((-displayOffset / singleThreshold - startFractionMsg) / (1f - startFractionMsg)).coerceIn(0f, 1f)

    val startFractionDel = (widthFloat * 2 + iconSpacingFloat) / threshold
    val leftProgressDel = (((-displayOffset / threshold) - startFractionDel) / (1f - startFractionDel)).coerceIn(0f, 1f)

    val startFractionCall = widthFloat / singleThreshold
    val rightProgress = ((displayOffset / singleThreshold - startFractionCall) / (1f - startFractionCall)).coerceIn(0f, 1f)

    val rightTriggerScale by animateFloatAsState(
        targetValue = if (triggeredRight) 1.2f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "rightTriggerScale"
    )
    val msgTriggerScale by animateFloatAsState(
        targetValue = if (triggeredMessage) 1.2f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "msgTriggerScale"
    )
    val delTriggerScale by animateFloatAsState(
        targetValue = if (triggeredDelete) 1.2f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "delTriggerScale"
    )

    Box(
        modifier = modifier
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta ->
                    coroutineScope.launch {
                        val maxRight = if (isRtl) threshold * 1.2f else singleThreshold * 1.2f
                        val maxLeft = if (isRtl) singleThreshold * 1.2f else threshold * 1.2f
                        val newOffset = (offsetX.value + delta * 0.6f).coerceIn(-maxLeft, maxRight)
                        offsetX.snapTo(newOffset)

                        val checkOffset = if (isRtl) -newOffset else newOffset

                        // To the right (Call)
                        if (checkOffset >= singleThreshold && !triggeredRight) {
                            triggeredRight = true
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        } else if (checkOffset < singleThreshold && triggeredRight) {
                            triggeredRight = false
                        }

                        // Left 1 (Message)
                        if (checkOffset <= -singleThreshold && !triggeredMessage) {
                            triggeredMessage = true
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        } else if (checkOffset > -singleThreshold && triggeredMessage) {
                            triggeredMessage = false
                        }

                        // Left 2 (Delete)
                        if (checkOffset <= -threshold && !triggeredDelete) {
                            triggeredDelete = true
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        } else if (checkOffset > -threshold && triggeredDelete) {
                            triggeredDelete = false
                        }
                    }
                },
                onDragStopped = {
                    coroutineScope.launch {
                        val finalDisplayOffset = if (isRtl) -offsetX.value else offsetX.value

                        if (finalDisplayOffset >= singleThreshold) {
                            onSwipeRight()
                        } else if (finalDisplayOffset <= -threshold) {
                            onDelete()
                        } else if (finalDisplayOffset <= -singleThreshold) {
                            onSwipeLeft()
                        }

                        offsetX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow))
                        triggeredRight = false
                        triggeredMessage = false
                        triggeredDelete = false
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
                    color = MaterialTheme.colorScheme.customColors.colorGreen
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.Call,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.customColors.colorDarkGreen,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            if (displayOffset < 0) {
                Surface(
                    modifier = Modifier
                        .padding(end = iconSpacing)
                        .size(width, height)
                        .graphicsLayer {
                            val scale = 0.4f + leftProgressDel * 0.6f * delTriggerScale
                            scaleX = scale
                            scaleY = scale
                            alpha = leftProgressDel
                        },
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.customColors.colorRed
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = ImageVector.vectorResource(id = R.drawable.ic_delete),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.customColors.colorDarkRed,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Surface(
                    modifier = Modifier
                        .padding(end = 16.dp)
                        .size(width, height)
                        .graphicsLayer {
                            val scale = 0.4f + leftProgressMsg * 0.6f * msgTriggerScale
                            scaleX = scale
                            scaleY = scale
                            alpha = leftProgressMsg
                        },
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.customColors.colorBlue
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = ImageVector.vectorResource(id = R.drawable.ic_message_outline),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.customColors.colorDarkBlue,
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