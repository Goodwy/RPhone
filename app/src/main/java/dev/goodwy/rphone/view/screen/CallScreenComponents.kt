package dev.goodwy.rphone.view.screen

import android.os.Build
import android.telecom.Call
import android.view.HapticFeedbackConstants
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowRight
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import dev.goodwy.rphone.R
import dev.goodwy.rphone.cardCornerSmall
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.view.components.RillIconBox
import dev.goodwy.rphone.view.screen.onboarding.wavyCircleShape
import dev.goodwy.rphone.view.theme.MyColors.cardColor
import dev.goodwy.rphone.view.theme.MyColors.dialpadKeyColor
import dev.goodwy.rphone.view.theme.color_call_button
import dev.goodwy.rphone.view.theme.color_call_end
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun KeypadButton(
    modifier: Modifier,
    key: Char,
    style: Int = 0,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val cornerRadius by animateDpAsState(
        targetValue = when (style) {
            1 -> 50.dp // Circular
            2 -> 0.dp  // Minimal
            else -> if (isPressed) 16.dp else 32.dp // Modern
        },
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "ButtonShape"
    )

    val containerColor = when (style) {
        2 -> if (isPressed) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else dialpadKeyColor
        else -> dialpadKeyColor
    }

    Surface(
        onClick = onClick,
        modifier = modifier.height(64.dp),
        shape = if (style == 1) CircleShape else RoundedCornerShape(cornerRadius),
        color = containerColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = if (style == 2 && !isPressed) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)) else null,
        interactionSource = interactionSource
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = key.toString(),
                style = if (style == 1) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun PulsingAvatar(photoUri: String?) {
    val prefs = koinInject<PreferenceManager>()
    val settingsState by prefs.settingsChanged.collectAsStateWithLifecycle()
    val avatarShape = remember(settingsState) {
        val shapeVal = prefs.getInt(PreferenceManager.KEY_AVATAR_SHAPE, 1)
        when (shapeVal) {
            0 -> RoundedCornerShape(20.dp)
            1 -> wavyCircleShape(waveAmplitude = 0.024f)
            2 -> RoundedCornerShape(0.dp)
            else -> CircleShape
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "alpha"
    )

    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(230.dp)
                .scale(scale)
                .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = alpha), avatarShape)
        )
        Box(
            modifier = Modifier
                .size(260.dp)
                .scale(scale * 1.1f)
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = alpha * 0.5f),
                    avatarShape
                )
        )

        HeroAvatar(photoUri, avatarSize = 200.dp, wavy = true)
    }
}

@Composable
fun HeroAvatar(photoUri: String?, avatarSize: Dp = 160.dp, wavy: Boolean = false) {
    val prefs = koinInject<PreferenceManager>()
    val settingsState by prefs.settingsChanged.collectAsStateWithLifecycle()
    val avatarShape = remember(settingsState) {
        if (wavy) wavyCircleShape(waveAmplitude = 0.024f)
        else {
            val shapeVal = prefs.getInt(PreferenceManager.KEY_AVATAR_SHAPE, 1)
            when (shapeVal) {
                0 -> RoundedCornerShape(20.dp)
                1 -> CircleShape
                2 -> RoundedCornerShape(0.dp)
                else -> CircleShape
            }
        }
    }
    val avatarFrame = prefs.getBoolean(PreferenceManager.KEY_AVATAR_FRAME, false)
    val borderColor =  MaterialTheme.colorScheme.onSurface

    Box(
        modifier = Modifier
            .then(
                if (avatarFrame) Modifier
                    .drawBehind {
                        val borderWidth = size.width * 0.08f // 8% of the width
                        drawOutline(
                            outline = avatarShape.createOutline(size, layoutDirection, this),
                            color = borderColor,
                            style = Stroke(width = borderWidth)
                        )
                    }
                else Modifier
            )
            .size(avatarSize)
            .clip(avatarShape)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center
    ) {
        if (!photoUri.isNullOrEmpty()) {
            AsyncImage(
                model = photoUri,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(avatarShape),
                contentScale = ContentScale.Crop
            )
        }
    }
}

@Composable
fun AnimatedCallButton(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    isActive: Boolean = false,
    btnColor: Color = Color.White.copy(0.12f),
    activeBtnColor: Color = Color.White,
    fgColor: Color = Color.White,
    activeFgColor: Color = Color.Black,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()
    val radius by animateDpAsState(if (isActive || isPressed) 20.dp else 42.dp, spring(stiffness = Spring.StiffnessMedium), label = "btnRadius")
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(onClick = onClick,
            modifier = Modifier.height(68.dp).fillMaxWidth(),
            shape = RoundedCornerShape(radius),
            color = if (isActive) activeBtnColor else btnColor,
            interactionSource = interaction
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (isActive) activeFgColor else fgColor,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = MaterialTheme.typography.labelMedium.fontFamily,
            color = fgColor.copy(0.7f),
            modifier = Modifier.padding(top = 8.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun HorizontalSwipeToAnswer(onAnswer: () -> Unit, onDecline: () -> Unit) {
    val coroutineScope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val density = LocalDensity.current
    val view = LocalView.current

    val trackHeight = 96.dp
    val handleWidth = 110.dp
    val handleHeight = 72.dp
    val handleWidthPx = with(density) { handleWidth.toPx() }
    val paddingHandle = with(density) { (trackHeight - handleHeight).toPx() }
    var trackWidthPx by remember { mutableFloatStateOf(0f) }

    val maxDrag by remember(trackWidthPx, handleWidthPx, paddingHandle) {
        derivedStateOf {
            if (trackWidthPx > 0f) (trackWidthPx / 2f) - (handleWidthPx / 2f) - (paddingHandle) + with(density) { 1.dp.toPx() }
            else 0f
        }
    }
    val triggerThreshold = maxDrag * 0.85f

    val dragProgress = remember { derivedStateOf { if (maxDrag > 0f) offsetX.value / maxDrag else 0f } }
    val dragNormal = remember { derivedStateOf { abs(dragProgress.value) } }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")

    val handlePulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "handlePulse"
    )

    val hintAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "hintAlpha"
    )

    val answerGreen = color_call_button
    val declineRed = color_call_end

    val handleBgColor by animateColorAsState(
        targetValue = when {
            dragProgress.value > 0.1f -> answerGreen
            dragProgress.value < -0.1f -> declineRed
            else -> Color.White
        },
        label = "handleColor"
    )

    val iconTint by animateColorAsState(
        targetValue = if (dragNormal.value > 0.1f) Color.White
        else Color.Black,
        label = "iconTint"
    )

    val iconRotation by remember { derivedStateOf {
        dragProgress.value * 135f
    } }

    Box(
        modifier = Modifier
            .padding(bottom = 36.dp)
            .fillMaxWidth()
            .height(trackHeight)
            .padding(horizontal = 16.dp)
            .onSizeChanged { trackWidthPx = it.width.toFloat() }
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Text(
            stringResource(R.string.decline),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 32.dp)
                .alpha((1f - (dragProgress.value * -2f).coerceIn(0f, 1f)) * hintAlpha),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = declineRed.copy(alpha = 0.8f)
        )

        Text(
            stringResource(R.string.answer),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 32.dp)
                .alpha((1f - (dragProgress.value * 2f).coerceIn(0f, 1f)) * hintAlpha),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = answerGreen.copy(alpha = 0.8f)
        )

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .graphicsLayer {
                    val idleFactor = (1f - dragNormal.value * 5f).coerceIn(0f, 1f)
                    scaleX = 1f + (handlePulseScale - 1f) * idleFactor
                    scaleY = 1f + (handlePulseScale - 1f) * idleFactor
                }
                .width(handleWidth)
                .height(handleHeight)
                .clip(CircleShape)
                .background(handleBgColor)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            coroutineScope.launch {
                                when {
                                    offsetX.value > triggerThreshold -> {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                        } else {
                                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                        }
                                        onAnswer()
                                    }

                                    offsetX.value < -triggerThreshold -> {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                            view.performHapticFeedback(HapticFeedbackConstants.REJECT)
                                        } else {
                                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                        }
                                        onDecline()
                                    }

                                    else -> offsetX.animateTo(
                                        0f,
                                        spring(
                                            dampingRatio = 0.75f,
                                            stiffness = Spring.StiffnessMedium
                                        )
                                    )
                                }
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            coroutineScope.launch {
                                val newOffset = (offsetX.value + dragAmount).coerceIn(
                                    -maxDrag * 1.1f,
                                    maxDrag * 1.1f
                                )
                                offsetX.snapTo(newOffset)
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            val icon = Icons.Rounded.Call

            Crossfade(targetState = icon, animationSpec = tween(150), label = "icon") { targetIcon ->
                Icon(
                    targetIcon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier
                        .size(32.dp)
                        .graphicsLayer { rotationZ = iconRotation }
                )
            }
        }
    }
}

@Composable
fun VerticalSwipeToAnswer(onAnswer: () -> Unit, onDecline: () -> Unit) {
    val coroutineScope = rememberCoroutineScope()
    val offsetY = remember { Animatable(0f) }
    val density = LocalDensity.current
    val view = LocalView.current

    val handleSize = 80.dp
    val maxDrag = with(density) { 100.dp.toPx() }
    val triggerThreshold = maxDrag * 0.7f

    val dragProgress = remember { derivedStateOf { offsetY.value / maxDrag } }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseAlpha"
    )

    val textColor = MaterialTheme.colorScheme.onSurface

    val arrowOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 12f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "arrowBounce"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (-110).dp)
                .graphicsLayer {
                    alpha = (0.4f + (dragProgress.value * -1.8f)).coerceIn(0f, 1f)
                    translationY = -arrowOffset
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Rounded.KeyboardArrowUp, null, tint = textColor, modifier = Modifier.size(36.dp))
            Text(
                stringResource(R.string.swipe_up_to_answer),
                style = MaterialTheme.typography.titleMedium,
                color = textColor.copy(alpha = 0.9f),
                fontWeight = FontWeight.Light,
                fontStyle = FontStyle.Italic
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = 110.dp)
                .graphicsLayer {
                    alpha = (0.4f + (dragProgress.value * 1.8f)).coerceIn(0f, 1f)
                    translationY = arrowOffset
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                stringResource(R.string.swipe_down_to_reject),
                style = MaterialTheme.typography.titleMedium,
                color = textColor.copy(alpha = 0.9f),
                fontWeight = FontWeight.Light,
                fontStyle = FontStyle.Italic
            )
            Icon(Icons.Rounded.KeyboardArrowDown, null, tint = textColor, modifier = Modifier.size(36.dp))
        }

        Box(contentAlignment = Alignment.Center) {
            if (abs(offsetY.value) < 5f) {
                Box(
                    modifier = Modifier
                        .size(handleSize)
                        .scale(pulseScale)
                        .background(textColor.copy(alpha = pulseAlpha * 0.4f), CircleShape)
                )
                Box(
                    modifier = Modifier
                        .size(handleSize)
                        .scale(pulseScale * 1.4f)
                        .border(1.dp, textColor.copy(alpha = pulseAlpha * 0.2f), CircleShape)
                )
            }

            val handleBgColor by animateColorAsState(
                targetValue = when {
                    offsetY.value < -15f -> color_call_button
                    offsetY.value > 15f -> color_call_end
                    else -> Color.White
                },
                label = "bgColor"
            )

            val iconTint by animateColorAsState(
                targetValue = if (abs(offsetY.value) > 15f) Color.White else color_call_button,
                label = "iconTint"
            )

            Box(
                modifier = Modifier
                    .offset { IntOffset(0, offsetY.value.roundToInt()) }
                    .size(handleSize)
                    .shadow(if (abs(offsetY.value) > 5f) 12.dp else 4.dp, CircleShape)
                    .background(handleBgColor, CircleShape)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = {
                                coroutineScope.launch {
                                    when {
                                        offsetY.value < -triggerThreshold -> {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                            } else {
                                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                            }
                                            onAnswer()
                                        }

                                        offsetY.value > triggerThreshold -> {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                                view.performHapticFeedback(HapticFeedbackConstants.REJECT)
                                            } else {
                                                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                            }
                                            onDecline()
                                        }

                                        else -> offsetY.animateTo(
                                            0f,
                                            spring(
                                                dampingRatio = 0.7f,
                                                stiffness = Spring.StiffnessMedium
                                            )
                                        )
                                    }
                                }
                            },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                coroutineScope.launch {
                                    val newOffset =
                                        (offsetY.value + dragAmount).coerceIn(-maxDrag, maxDrag)
                                    offsetY.snapTo(newOffset)
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                val icon = if (offsetY.value > 5f) Icons.Rounded.CallEnd else Icons.Rounded.Call

                Crossfade(targetState = icon, label = "icon") { targetIcon ->
                    Icon(
                        targetIcon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun IPhoneSwipeToAnswer(onAnswer: () -> Unit, onDecline: () -> Unit, onMessage: () -> Unit) {
    val coroutineScope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val density = LocalDensity.current
    val view = LocalView.current
    val isDark = isSystemInDarkTheme()

    val trackWidth = 320.dp
    val trackHeight = 94.dp
    val handleSize = 78.dp
    val handlePadding = 8.dp

    val trackWidthPx = with(density) { trackWidth.toPx() }
    val handleSizePx = with(density) { handleSize.toPx() }
    val handlePaddingPx = with(density) { handlePadding.toPx() }

    val maxDrag = trackWidthPx - handleSizePx - (handlePaddingPx * 2)

    val trackBgColor = MaterialTheme.colorScheme.onSurface.copy(alpha = if (isDark) 0.15f else 0.1f)
    val buttonContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
    val handleBgColor = Color.White

    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "offset"
    )

    val dragProgress = remember { derivedStateOf { if (maxDrag > 0f) offsetX.value / maxDrag else 0f } }
    val iconRotation by remember { derivedStateOf {
        dragProgress.value * 135f
    } }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(42.dp),
        modifier = Modifier.padding(bottom = 42.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(0.75f),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = onDecline,
                    modifier = Modifier
                        .size(60.dp)
                        .background(
                            trackBgColor,
                            CircleShape
                        )
                ) {
                    Icon(
                        Icons.Rounded.CallEnd,
                        contentDescription = stringResource(R.string.decline),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    stringResource(R.string.decline),
                    style = MaterialTheme.typography.labelMedium,
                    color = buttonContentColor,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = onMessage,
                    modifier = Modifier
                        .size(60.dp)
                        .background(
                            trackBgColor,
                            CircleShape
                        )
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_message_outline),
                        contentDescription = stringResource(R.string.message),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    stringResource(R.string.message),
                    style = MaterialTheme.typography.labelMedium,
                    color = buttonContentColor,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .width(trackWidth)
                .height(trackHeight)
                .clip(CircleShape),
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .height(trackHeight)
                    .align(Alignment.CenterEnd)
                    .width(
                        with(density) {
                            val width = trackWidthPx - offsetX.value
                            width.coerceAtLeast(0f).toDp()
                        }
                    )
                    .clip(CircleShape)
                    .background(trackBgColor)
            )

            val baseTextColor = MaterialTheme.colorScheme.onSurface
            val shimmerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)

            val brush = Brush.linearGradient(
                colors = listOf(shimmerColor, baseTextColor, shimmerColor),
                start = Offset(trackWidthPx * shimmerOffset - 150f, 0f),
                end = Offset(trackWidthPx * shimmerOffset + 150f, 0f)
            )

            Text(
                text = stringResource(R.string.slide_to_answer),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = handleSize)
                    .graphicsLayer {
                        alpha = (1f - (offsetX.value / maxDrag) * 2f).coerceIn(0f, 1f)
                    },
                style = MaterialTheme.typography.titleMedium.copy(
                    brush = brush,
                    fontWeight = FontWeight.Medium
                ),
                textAlign = TextAlign.Center
            )

            Box(
                modifier = Modifier
                    .padding(start = handlePadding)
                    .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                    .size(handleSize)
                    .clip(CircleShape)
                    .background(handleBgColor)
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                coroutineScope.launch {
                                    if (offsetX.value > maxDrag * 0.85f) {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                        } else {
                                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                        }
                                        onAnswer()
                                    } else {
                                        offsetX.animateTo(0f, spring(dampingRatio = 0.8f))
                                    }
                                }
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                coroutineScope.launch {
                                    offsetX.snapTo(
                                        (offsetX.value + dragAmount).coerceIn(
                                            0f,
                                            maxDrag
                                        )
                                    )
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Call,
                    contentDescription = null,
                    tint = color_call_button,
                    modifier = Modifier
                        .size(36.dp)
                        .graphicsLayer { rotationZ = iconRotation }
                )
            }
        }
    }
}

@Composable
fun IncomingCallButtons(
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    onAnswerAndDecline: (() -> Unit)?
) {
    val declineColor = color_call_end
    val answerColor = color_call_button

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp)
            .padding(bottom = 32.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                val interaction = remember { MutableInteractionSource() }
                val isPressed by interaction.collectIsPressedAsState()
                val radius by animateDpAsState(
                    if (isPressed) 28.dp else 42.dp,
                    spring(stiffness = Spring.StiffnessMedium),
                    label = "btnDeclineRadius"
                )
                Box(
                    modifier = Modifier
                        .size(height = 68.dp, width = 80.dp)
                        .scale(scale * 1.06f)
                        .background(declineColor.copy(alpha = 0.2f), RoundedCornerShape(radius))
                )
                Surface(
                    onClick = onDecline,
                    modifier = Modifier.size(height = 68.dp, width = 82.dp),
                    shape = RoundedCornerShape(radius),
                    color = declineColor,
                    interactionSource = interaction
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.CallEnd,
                            contentDescription = stringResource(R.string.decline),
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.decline),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 96.dp)
            )
        }

        if (onAnswerAndDecline != null) Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                val interaction = remember { MutableInteractionSource() }
                val isPressed by interaction.collectIsPressedAsState()
                val radius by animateDpAsState(
                    if (isPressed) 28.dp else 42.dp,
                    spring(stiffness = Spring.StiffnessMedium),
                    label = "btnRadius"
                )
                Box(
                    modifier = Modifier
                        .size(height = 68.dp, width = 80.dp)
                        .scale(scale * 1.06f)
                        .background(declineColor.copy(alpha = 0.1f), RoundedCornerShape(radius))
                )
                Surface(onClick = onAnswerAndDecline,
                    modifier = Modifier.size(height = 68.dp, width = 82.dp),
                    shape = RoundedCornerShape(radius),
                    color = answerColor,
                    interactionSource = interaction
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.CallEnd,
                            contentDescription = stringResource(R.string.answer_and_decline),
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.answer_and_decline),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelLarge,
                lineHeight = MaterialTheme.typography.labelMedium.lineHeight,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 96.dp)
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                val interaction = remember { MutableInteractionSource() }
                val isPressed by interaction.collectIsPressedAsState()
                val radius by animateDpAsState(
                    if (isPressed) 28.dp else 42.dp,
                    spring(stiffness = Spring.StiffnessMedium),
                    label = "btnAnswerRadius"
                )
                Box(
                    modifier = Modifier
                        .size(height = 68.dp, width = 80.dp)
                        .scale(scale * 1.06f)
                        .background(answerColor.copy(alpha = 0.2f), RoundedCornerShape(radius))
                )
                Surface(onClick = onAnswer,
                    modifier = Modifier.size(height = 68.dp, width = 82.dp),
                    shape = RoundedCornerShape(radius),
                    color = answerColor,
                    interactionSource = interaction
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Call,
                            contentDescription = stringResource(R.string.answer),
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.answer),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 96.dp)
            )
        }
    }
}

@Composable
fun DefaultSwipeToAnswer(onAnswer: () -> Unit, onDecline: () -> Unit, onMessage: () -> Unit) {
    val coroutineScope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val density = LocalDensity.current
    val view = LocalView.current
    val isDark = isSystemInDarkTheme()

    val trackWidth = 320.dp
    val trackHeight = 80.dp
    val handleHeight = 68.dp
    val handleWidth = 96.dp
    val handlePadding = 6.dp

    val trackWidthPx = with(density) { trackWidth.toPx() }
    val handleWidthPx = with(density) { handleWidth.toPx() }
    val handlePaddingPx = with(density) { handlePadding.toPx() }

    val maxDrag = trackWidthPx - handleWidthPx - (handlePaddingPx * 2)

    val buttonBgColor = MaterialTheme.colorScheme.onSurface.copy(alpha = if (isDark) 0.15f else 0.1f)
    val buttonContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
    val buttonIconColor = MaterialTheme.colorScheme.onSurface
    val handleBgColor = Color.White

    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "offset"
    )

    val dragProgress = remember { derivedStateOf { if (maxDrag > 0f) offsetX.value / maxDrag else 0f } }
    val iconRotation by remember { derivedStateOf {
        dragProgress.value * 135f
    } }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(42.dp),
        modifier = Modifier.padding(bottom = 42.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(0.75f),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = onDecline,
                    modifier = Modifier
                        .size(60.dp)
                        .background(
                            buttonBgColor,
                            CircleShape
                        )
                ) {
                    Icon(
                        Icons.Rounded.CallEnd,
                        contentDescription = stringResource(R.string.decline),
                        tint = buttonIconColor
                    )
                }
                Text(
                    stringResource(R.string.decline),
                    style = MaterialTheme.typography.labelMedium,
                    color = buttonContentColor,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = onMessage,
                    modifier = Modifier
                        .size(60.dp)
                        .background(
                            buttonBgColor,
                            CircleShape
                        )
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_message_outline),
                        contentDescription = stringResource(R.string.message),
                        tint = buttonIconColor
                    )
                }
                Text(
                    stringResource(R.string.message),
                    style = MaterialTheme.typography.labelMedium,
                    color = buttonContentColor,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .width(trackWidth)
                .height(trackHeight)
                .clip(CircleShape),
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .height(trackHeight)
                    .align(Alignment.CenterEnd)
                    .width(
                        with(density) {
                            val width = trackWidthPx - offsetX.value
                            width.coerceAtLeast(0f).toDp()
                        }
                    )
                    .clip(CircleShape)
                    .background(buttonBgColor)
            )

            val baseTextColor = buttonIconColor
            val shimmerColor = buttonIconColor.copy(alpha = 0.4f)

            val brush = Brush.linearGradient(
                colors = listOf(shimmerColor, baseTextColor, shimmerColor),
                start = Offset(trackWidthPx * shimmerOffset - 150f, 0f),
                end = Offset(trackWidthPx * shimmerOffset + 150f, 0f)
            )

            Text(
                text = stringResource(R.string.slide_to_answer),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = handleWidth, end = 56.dp)
                    .graphicsLayer {
                        alpha = (1f - (offsetX.value / maxDrag) * 2f).coerceIn(0f, 1f)
                    },
                style = MaterialTheme.typography.titleMedium.copy(
                    brush = brush,
                    fontWeight = FontWeight.Medium
                ),
                textAlign = TextAlign.Center
            )
            Icon(
                Icons.AutoMirrored.Rounded.ArrowRight,
                contentDescription = null,
                tint = handleBgColor.copy(0.3f),
                modifier = Modifier.padding(end = 28.dp).size(46.dp).align(Alignment.CenterEnd)
            )
            Icon(
                Icons.AutoMirrored.Rounded.ArrowRight,
                contentDescription = null,
                tint = handleBgColor.copy(0.6f),
                modifier = Modifier.padding(end = 8.dp).size(46.dp).align(Alignment.CenterEnd)
            )

            Box(
                modifier = Modifier
                    .padding(start = handlePadding)
                    .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                    .size(handleWidth, handleHeight)
                    .clip(CircleShape)
                    .background(handleBgColor)
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                coroutineScope.launch {
                                    if (offsetX.value > maxDrag * 0.85f) {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                        } else {
                                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                        }
                                        onAnswer()
                                    } else {
                                        offsetX.animateTo(0f, spring(dampingRatio = 0.8f))
                                    }
                                }
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                coroutineScope.launch {
                                    offsetX.snapTo(
                                        (offsetX.value + dragAmount).coerceIn(
                                            0f,
                                            maxDrag
                                        )
                                    )
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Call,
                    contentDescription = null,
                    tint = Color.Black.copy(alpha = 0.9f),
                    modifier = Modifier
                        .size(36.dp)
                        .graphicsLayer { rotationZ = iconRotation }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MoreItem(
    headline: String,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "ListItemScale"
    )

    Surface(
        color = cardColor,
        shape = RoundedCornerShape(cardCornerSmall),
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale),
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (enabled) {
                        Modifier
                            .alpha(1f)
                            .combinedClickable(
                                interactionSource = interactionSource,
                                indication = ripple(),
                                onClick = onClick,
                            )
                    } else {
                        Modifier.alpha(0.5f)
                    }
                )
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingIcon != null) {
                RillIconBox(
                    icon = leadingIcon,
                    iconContainerColor = MaterialTheme.colorScheme.onSurface,
                    iconBgContainerColor = MaterialTheme.colorScheme.surface,
                )
                Spacer(modifier = Modifier.width(16.dp))
            }
            Text(
                modifier = Modifier.weight(1f),
                text = headline,
                style = MaterialTheme.typography.bodyLarge,
                lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (trailingIcon != null) {
                Spacer(modifier = Modifier.width(12.dp))
                Icon(
                    trailingIcon, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.width(2.dp))
            }
        }
    }
}

@Composable
fun InCallKeypad(
    call: Call,
    typedDigits: String,
    onDigitClick: (Char) -> Unit
) {
    val prefs = koinInject<PreferenceManager>()
    val settingsState by prefs.settingsChanged.collectAsStateWithLifecycle()
    val toneGenerator = remember { ToneGenerator(AudioManager.STREAM_DTMF, 80) }
    val dialpadStyle by remember(settingsState) {
        mutableIntStateOf(prefs.getInt(PreferenceManager.KEY_DIALPAD_STYLE, 3))
    }

    DisposableEffect(Unit) {
        onDispose {
            toneGenerator.release()
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val typedDigitsLength = typedDigits.length
        val fontSize = (
                (when {
                    typedDigitsLength > 42 -> 12
                    typedDigitsLength > 38 -> 14
                    typedDigitsLength > 34 -> 16
                    typedDigitsLength > 30 -> 18
                    typedDigitsLength > 25 -> 20
                    typedDigitsLength > 20 -> 24
                    typedDigitsLength > 16 -> 28
                    else -> 36
                }))
        val textStyle = MaterialTheme.typography.displaySmall.copy(
            fontSize = fontSize.sp,
            fontWeight = FontWeight.Light
        )
        Text(
            text = typedDigits,
            style = textStyle,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .animateContentSize()
        )

        val keys = listOf(
            listOf('1', '2', '3'),
            listOf('4', '5', '6'),
            listOf('7', '8', '9'),
            listOf('*', '0', '#')
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            keys.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    row.forEach { key ->
                        KeypadButton(
                            modifier = Modifier.weight(1f),
                            key = key,
                            style = dialpadStyle,
                            onClick = {
                                if (prefs.getBoolean(PreferenceManager.KEY_DTMF_TONE, true)) {
                                    val toneType = when (key) {
                                        '1' -> ToneGenerator.TONE_DTMF_1
                                        '2' -> ToneGenerator.TONE_DTMF_2
                                        '3' -> ToneGenerator.TONE_DTMF_3
                                        '4' -> ToneGenerator.TONE_DTMF_4
                                        '5' -> ToneGenerator.TONE_DTMF_5
                                        '6' -> ToneGenerator.TONE_DTMF_6
                                        '7' -> ToneGenerator.TONE_DTMF_7
                                        '8' -> ToneGenerator.TONE_DTMF_8
                                        '9' -> ToneGenerator.TONE_DTMF_9
                                        '0' -> ToneGenerator.TONE_DTMF_0
                                        '*' -> ToneGenerator.TONE_DTMF_S
                                        '#' -> ToneGenerator.TONE_DTMF_P
                                        else -> -1
                                    }
                                    if (toneType != -1) {
                                        toneGenerator.startTone(toneType, 120)
                                    }
                                }
                                call.playDtmfTone(key)
                                call.stopDtmfTone()
                                onDigitClick(key)
                            }
                        )
                    }
                }
            }
        }
    }
}