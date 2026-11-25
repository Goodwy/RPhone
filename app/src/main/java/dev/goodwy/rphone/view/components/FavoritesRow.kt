package dev.goodwy.rphone.view.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LocalPinnableContainer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.goodwy.rphone.R
import dev.goodwy.rphone.modal.data.Contact
import dev.goodwy.rphone.modal.data.getDisplayName
import dev.goodwy.rphone.view.theme.color_call_end

@Composable
fun FavoritesRow(
    favorites: List<Contact>,
    isEditing: Boolean,
    onUnfavorite: (Contact) -> Unit,
    onSaveOrder: (List<String>) -> Unit,
    onClick: (Contact) -> Unit,
    displayOrder: Int
) {
    val favoritesList = remember {
        mutableStateListOf<Contact>().apply { addAll(favorites) }
    }
    LaunchedEffect(favorites) {
        if (favoritesList.map { it.id } != favorites.map { it.id }) {
            favoritesList.clear()
            favoritesList.addAll(favorites)
        }
    }

    // We track using a stable ID—`isDragged` does not depend on the layout index
    var draggedContactId by remember { mutableStateOf<String?>(null) }
    // The single source of truth for visual displacement.
    // Includes finger displacement, auto-scroll compensation, and swipe correction.
    var dragOffsetX by remember { mutableFloatStateOf(0f) }

    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val lazyListState = rememberLazyListState()

    val itemWidth = 68.dp
    val spacing = 12.dp
    val itemWidthPx = with(density) { itemWidth.toPx() }
    val spacingPx = with(density) { spacing.toPx() }
    val itemTotalWidthPx = itemWidthPx + spacingPx

    val infiniteTransition = rememberInfiniteTransition(label = "wiggle")
    val rotation by infiniteTransition.animateFloat(
        initialValue = -1.2f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(160, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wiggleRotation"
    )
    val translationY by infiniteTransition.animateFloat(
        initialValue = -0.8f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(140, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wiggleTranslation"
    )

    val isDragging = draggedContactId != null

    LaunchedEffect(isDragging) {
        if (!isDragging) return@LaunchedEffect

        val edgeZonePx = with(density) { 60.dp.toPx() }
        val maxStepPx  = with(density) { 4.dp.toPx() }
        val minStepPx  = with(density) { 2.dp.toPx() }
        val contentPaddingPx = with(density) { 16.dp.toPx() }

        while (true) {
            withFrameNanos { }
            val currentId = draggedContactId ?: break

            val currentIndex = favoritesList.indexOfFirst { it.id == currentId }
            if (currentIndex == -1) break

            val slotLeft = lazyListState.layoutInfo.visibleItemsInfo
                .find { it.index == currentIndex }?.offset?.toFloat()
                ?: (contentPaddingPx -
                        lazyListState.firstVisibleItemScrollOffset +
                        (currentIndex - lazyListState.firstVisibleItemIndex) * itemTotalWidthPx)

            val visualLeft  = slotLeft + dragOffsetX
            val visualRight = visualLeft + itemWidthPx
            val viewportEnd = lazyListState.layoutInfo.viewportEndOffset.toFloat()

            val step = when {
                visualLeft < edgeZonePx -> {
                    val t = ((edgeZonePx - visualLeft) / edgeZonePx).coerceIn(0f, 1f)
                    -(minStepPx + (maxStepPx - minStepPx) * t)
                }
                visualRight > viewportEnd - edgeZonePx -> {
                    val t = ((visualRight - (viewportEnd - edgeZonePx)) / edgeZonePx).coerceIn(0f, 1f)
                    minStepPx + (maxStepPx - minStepPx) * t
                }
                else -> 0f
            }

            if (step != 0f) {
                dragOffsetX += lazyListState.dispatchRawDelta(step)
            }

            val swapThreshold = itemTotalWidthPx * 0.55f

            if (dragOffsetX > swapThreshold && currentIndex < favoritesList.lastIndex) {
                val targetIndex = currentIndex + 1
                val item = favoritesList[currentIndex]
                favoritesList.removeAt(currentIndex)
                favoritesList.add(targetIndex, item)
                dragOffsetX -= itemTotalWidthPx
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            } else if (dragOffsetX < -swapThreshold && currentIndex > 0) {
                val targetIndex = currentIndex - 1
                val item = favoritesList[currentIndex]
                favoritesList.removeAt(currentIndex)
                favoritesList.add(targetIndex, item)
                dragOffsetX += itemTotalWidthPx
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
    }

    LazyRow(
        state = lazyListState,
        flingBehavior = rememberSnapFlingBehavior(lazyListState),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(spacing)
    ) {
        itemsIndexed(
            items = favoritesList,
            key = { _, contact -> contact.id }
        ) { index, contact ->
            val isDragged = contact.id == draggedContactId
            val pinnableContainer = LocalPinnableContainer.current
            DisposableEffect(isDragged) {
                val handle = if (isDragged) pinnableContainer?.pin() else null
                onDispose { handle?.release() }
            }

            val animatedScale by animateFloatAsState(
                targetValue = if (isDragged) 1.08f else 1.0f,
                animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow),
                label = "scale"
            )

            val animatedElevation by animateDpAsState(
                targetValue = if (isDragged) 16.dp else 0.dp,
                animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow),
                label = "elevation"
            )

            Box(
                modifier = Modifier
                    .width(itemWidth)
                    .wrapContentHeight()
                    .then(if (!isDragged) Modifier.animateItem(
                        placementSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
                    ) else Modifier)
                    .zIndex(if (isDragged) 100f else 0f)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            if (isDragged) {
                                translationX = dragOffsetX
                                scaleX = animatedScale
                                scaleY = animatedScale
                            }
                        }
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(2.dp)
                            .graphicsLayer {
                                if (isEditing && !isDragged) {
                                    rotationZ = rotation
                                    this.translationY = translationY * density.density
                                }
                            }
                            .shadow(
                                elevation = animatedElevation,
                                shape = RoundedCornerShape(12.dp),
                                ambientColor = Color.Black.copy(alpha = 0.25f),
                                spotColor = Color.Black.copy(alpha = 0.4f)
                            )
                            .pointerInput(contact.id) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        draggedContactId = contact.id
                                        dragOffsetX = 0f
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        dragOffsetX += dragAmount.x
                                    },
                                    onDragEnd = {
                                        onSaveOrder(favoritesList.map { it.id })
                                        draggedContactId = null
                                        dragOffsetX = 0f
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    },
                                    onDragCancel = {
                                        draggedContactId = null
                                        dragOffsetX = 0f
                                    }
                                )
                            }
                            .clickable(
                                enabled = !isEditing,
                                onClick = { onClick(contact) }
                            ),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = animatedElevation / 3
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
                                RillAvatar(
                                    name = contact.displayName,
                                    photoUri = contact.photoUri,
                                    modifier = Modifier.fillMaxSize().padding(2.dp),
                                    shape = CircleShape
                                )
                            }
                            val displayName = getDisplayName(contact, displayOrder)
                            Text(
                                text = displayName,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.bodySmall,
                                lineHeight = MaterialTheme.typography.bodySmall.fontSize,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    if (isEditing) {
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
                            onClick = { onUnfavorite(contact) },
                            modifier = Modifier
                                .size(22.dp)
                                .offset(x = (-2).dp, y = (-2).dp)
                                .scale(iconScale)
                                .alpha(iconAlpha)
                                .graphicsLayer {
                                    if (isEditing && !isDragged) {
                                        rotationZ = rotation
                                        this.translationY = translationY * density.density
                                    }
                                },
                            shape = CircleShape,
                            color = color_call_end,
                            shadowElevation = 4.dp,
                            border = BorderStroke(1.dp, Color.White)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Remove,
                                contentDescription = stringResource(R.string.remove_from_favorites),
                                tint = Color.White,
                                modifier = Modifier.padding(4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}