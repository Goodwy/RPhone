package dev.goodwy.rphone.view.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.IntOffset
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
    shape: RoundedCornerShape,
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
    val threshold = with(LocalDensity.current) { 80.dp.toPx() }
    
    var triggeredRight by remember { mutableStateOf(false) }
    var triggeredLeft by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .clip(shape)
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta ->
                    coroutineScope.launch {
                        val newOffset = (offsetX.value + delta * 0.6f).coerceIn(-threshold * 1.5f, threshold * 1.5f)
                        offsetX.snapTo(newOffset)
                        
                        if (newOffset >= threshold && !triggeredRight) {
                            triggeredRight = true
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        } else if (newOffset < threshold && triggeredRight) {
                            triggeredRight = false
                        }
                        
                        if (newOffset <= -threshold && !triggeredLeft) {
                            triggeredLeft = true
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        } else if (newOffset > -threshold && triggeredLeft) {
                            triggeredLeft = false
                        }
                    }
                },
                onDragStopped = {
                    coroutineScope.launch {
                        if (triggeredRight) {
                            onSwipeRight()
                        } else if (triggeredLeft) {
                            onSwipeLeft()
                        }
                        offsetX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow))
                        triggeredRight = false
                        triggeredLeft = false
                    }
                }
            )
    ) {
        // Background Actions
        if (offsetX.value != 0f) {
            Row(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        if (offsetX.value > 0) MaterialTheme.colorScheme.customColors.colorDarkGreen
                        else MaterialTheme.colorScheme.primary
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (offsetX.value > 0) {
                    Icon(
                        imageVector = Icons.Rounded.Call,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier
                            .padding(start = 24.dp)
                            .size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                if (offsetX.value < 0) {
                    Icon(
                        imageVector = ImageVector.vectorResource(id = R.drawable.ic_message_filled),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier
                            .padding(end = 24.dp)
                            .size(24.dp)
                    )
                }
            }
        }

        // Foreground Content
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            content()
        }
    }
}
