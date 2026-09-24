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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowRight
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.IconButtonDefaults
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
import dev.goodwy.rphone.cardCornerExtraSmall
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.liquidglass.LocalLiquidGlassBackdrop
import dev.goodwy.rphone.liquidglass.drawBackdrop
import dev.goodwy.rphone.liquidglass.drawPlainBackdrop
import dev.goodwy.rphone.liquidglass.effects.blur
import dev.goodwy.rphone.liquidglass.effects.colorControls
import dev.goodwy.rphone.liquidglass.effects.lens
import dev.goodwy.rphone.liquidglass.highlight.Highlight
import dev.goodwy.rphone.liquidglass.shadow.Shadow
import dev.goodwy.rphone.view.components.RillIconBox
import dev.goodwy.rphone.view.screen.onboarding.wavyCircleShape
import dev.goodwy.rphone.view.theme.MyColors.bottomBarColor
import dev.goodwy.rphone.view.theme.MyColors.cardColor
import dev.goodwy.rphone.view.theme.MyColors.dialpadKeyColor
import dev.goodwy.rphone.view.theme.color_call_button
import dev.goodwy.rphone.view.theme.color_call_end
import dev.goodwy.rphone.view.theme.customColors
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
fun PocketModeOverlay(
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.95f))
            .clickable(enabled = false) {},
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            Surface(
                modifier = Modifier.size(80.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.customColors.colorIndigo,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.ScreenLockPortrait,
                        contentDescription = null,
                        modifier = Modifier.size(42.dp),
                        tint = MaterialTheme.colorScheme.customColors.colorDarkIndigo
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.pocket_mode_active),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.pocket_mode_active_description),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(32.dp))
            FilledTonalButton(
                onClick = onDismiss,
                shape = CircleShape
            ) {
                Icon(Icons.Rounded.LockOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.pocket_mode_dismiss))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickResponsesBottomSheet(
    phoneNumber: String,
    contactName: String,
    onDismiss: () -> Unit,
    onSend: (String) -> Unit,
    onOpenSmsApp: () -> Unit,
//    onScheduleReminder: (Long, String) -> Unit
) {
    val prefs = koinInject<PreferenceManager>()
    val responses = remember { prefs.getQuickResponses() }
    var customText by remember { mutableStateOf("") }
    var isCustomVisible by remember { mutableStateOf(false) }

//    val tomorrowMorningMinutes = remember {
//        val now = Calendar.getInstance()
//        val tomorrow = Calendar.getInstance().apply {
//            add(Calendar.DAY_OF_YEAR, 1)
//            set(Calendar.HOUR_OF_DAY, 9)
//            set(Calendar.MINUTE, 0)
//            set(Calendar.SECOND, 0)
//            set(Calendar.MILLISECOND, 0)
//        }
//        val diff = tomorrow.timeInMillis - now.timeInMillis
//        (diff / (1000 * 60)).coerceAtLeast(15)
//    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Drag handle
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(3.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                    modifier = Modifier.size(width = 36.dp, height = 4.dp)
                ) {}
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp).padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.quick_response),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.decline_call_and_reply_to, contactName.ifBlank { phoneNumber }),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
//                IconButton(onClick = onOpenSmsApp) {
//                    Icon(
//                        Icons.AutoMirrored.Rounded.OpenInNew,
//                        contentDescription = "Open SMS app",
//                        tint = MaterialTheme.colorScheme.primary
//                    )
//                }
            }

            Spacer(Modifier.height(16.dp))

            responses.forEach { responseText ->
                Surface(
                    onClick = { onSend(responseText) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLowest
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = responseText,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(12.dp))
                        Icon(
                            Icons.AutoMirrored.Rounded.Send,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            if (!isCustomVisible) {
                Button(
                    onClick = { isCustomVisible = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    shape = CircleShape
                ) {
                    Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.write_your_own))
                }
            } else {
                TextField(
                    value = customText,
                    onValueChange = { customText = it },
                    placeholder = { Text(stringResource(R.string.type_message)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = CircleShape,
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent
                    ),
                    trailingIcon = {
                        IconButton(
                            modifier = Modifier.width(72.dp).padding(horizontal = 8.dp),
                            onClick = {
                                if (customText.isNotBlank()) {
                                    onSend(customText.trim())
                                }
                            },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = if (customText.isNotBlank()) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
                            ),
                            shape = CircleShape,
                            enabled = customText.isNotBlank()
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.Send,
                                modifier = Modifier.padding(start = 2.dp),
                                contentDescription = stringResource(R.string.send),
                            )
                        }
                    },
                )
            }

//            Spacer(Modifier.height(16.dp))
//            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
//            Spacer(Modifier.height(12.dp))
//
//            Text(
//                text = "Remind Me to Call Back",
//                style = MaterialTheme.typography.titleMedium,
//                fontWeight = FontWeight.SemiBold
//            )
//            Spacer(Modifier.height(8.dp))
//
//            Row(
//                modifier = Modifier.fillMaxWidth(),
//                horizontalArrangement = Arrangement.spacedBy(8.dp)
//            ) {
//                listOf(
//                    "In 15m" to 15L,
//                    "In 1h" to 60L,
//                    "Tomorrow 9 AM" to tomorrowMorningMinutes
//                ).forEach { (label, minutes) ->
//                    FilledTonalButton(
//                        onClick = { onScheduleReminder(minutes, label) },
//                        modifier = Modifier.weight(1f),
//                        shape = RoundedCornerShape(12.dp),
//                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp)
//                    ) {
//                        Icon(Icons.Rounded.Alarm, contentDescription = null, modifier = Modifier.size(14.dp))
//                        Spacer(Modifier.width(4.dp))
//                        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
//                    }
//                }
//            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
fun HorizontalSwipeToAnswer(
    useLg: Boolean,
    useBlur: Boolean,
    blurIntensity: Float,
    onAnswer: () -> Unit,
    onDecline: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val localDensity = LocalDensity.current
    val view = LocalView.current
    val globalBackdrop = LocalLiquidGlassBackdrop.current

    val trackHeight = 96.dp
    val handleWidth = 110.dp
    val handleHeight = 72.dp
    val handleWidthPx = with(localDensity) { handleWidth.toPx() }
    val paddingHandle = with(localDensity) { (trackHeight - handleHeight).toPx() }
    var trackWidthPx by remember { mutableFloatStateOf(0f) }

    val maxDrag by remember(trackWidthPx, handleWidthPx, paddingHandle) {
        derivedStateOf {
            if (trackWidthPx > 0f) (trackWidthPx / 2f) - (handleWidthPx / 2f) - (paddingHandle) + with(localDensity) { 1.dp.toPx() }
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

    val iconRotation by remember { derivedStateOf { dragProgress.value * 135f } }
    val buttonBgColor = if (useLg || useBlur) bottomBarColor.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceVariant

    Surface(
        shape           = CircleShape,
        color           = buttonBgColor,
        shadowElevation = 0.dp,
        tonalElevation  = 0.dp,
        modifier = Modifier
            .padding(bottom = 36.dp)
            .fillMaxWidth()
            .height(trackHeight)
            .padding(horizontal = 16.dp)
            .onSizeChanged { trackWidthPx = it.width.toFloat() }
            .then(
                if (useLg && globalBackdrop != null) Modifier.drawBackdrop(
                    backdrop = globalBackdrop,
                    shape = { CircleShape },
                    shadow = { Shadow(radius = 8.dp) },
                    effects = {
                        val d = density
                        colorControls(saturation = 1.3f)
                        blur(blurIntensity * d)
                        lens(refractionHeight = 18f * d, refractionAmount = 52f * d)
                    },
                    highlight = { Highlight.Default }
                )
                else if (useBlur && globalBackdrop != null) Modifier.drawPlainBackdrop(
                    backdrop = globalBackdrop,
                    shape    = { CircleShape },
                    shadow = { Shadow(radius = 8.dp) },
                    effects  = { blur(blurIntensity * density) }
                )
                else Modifier
            )
    ) {
        Box {
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

                Crossfade(
                    targetState = icon,
                    animationSpec = tween(150),
                    label = "icon"
                ) { targetIcon ->
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
}

@Composable
fun VerticalSwipeToAnswer(
    useLg: Boolean,
    useBlur: Boolean,
    blurIntensity: Float,
    onAnswer: () -> Unit,
    onDecline: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val offsetY = remember { Animatable(0f) }
    val localDensity = LocalDensity.current
    val view = LocalView.current
    val globalBackdrop = LocalLiquidGlassBackdrop.current

    val handleSize = 80.dp
    val maxDrag = with(localDensity) { 100.dp.toPx() }
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
            Surface(
                shape           = CircleShape,
                color           = if (useLg || useBlur) handleBgColor.copy(0.6f) else handleBgColor,
                shadowElevation = 0.dp,
                tonalElevation  = 0.dp,
                modifier = Modifier
                    .offset { IntOffset(0, offsetY.value.roundToInt()) }
                    .size(handleSize)
                    .shadow(if (abs(offsetY.value) > 5f) 12.dp else 4.dp, CircleShape)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = {
                                coroutineScope.launch {
                                    when {
                                        offsetY.value < -triggerThreshold -> {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                                view.performHapticFeedback(
                                                    HapticFeedbackConstants.CONFIRM
                                                )
                                            } else {
                                                view.performHapticFeedback(
                                                    HapticFeedbackConstants.VIRTUAL_KEY
                                                )
                                            }
                                            onAnswer()
                                        }

                                        offsetY.value > triggerThreshold -> {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                                view.performHapticFeedback(
                                                    HapticFeedbackConstants.REJECT
                                                )
                                            } else {
                                                view.performHapticFeedback(
                                                    HapticFeedbackConstants.LONG_PRESS
                                                )
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
                    }
                    .then(
                        if (useLg && globalBackdrop != null) Modifier.drawBackdrop(
                            backdrop = globalBackdrop,
                            shape = { CircleShape },
                            shadow = { Shadow(radius = 8.dp) },
                            effects = {
                                val d = density
                                colorControls(saturation = 1.3f)
                                blur(blurIntensity * d)
                                lens(refractionHeight = 18f * d, refractionAmount = 52f * d)
                            },
                            highlight = { Highlight.Default }
                        )
                        else if (useBlur && globalBackdrop != null) Modifier.drawPlainBackdrop(
                            backdrop = globalBackdrop,
                            shape    = { CircleShape },
                            shadow = { Shadow(radius = 8.dp) },
                            effects  = { blur(blurIntensity * density) }
                        )
                        else Modifier
                    )
            ) {
                Box(
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
}

@Composable
fun IPhoneSwipeToAnswer(
    useLg: Boolean,
    useBlur: Boolean,
    blurIntensity: Float,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    onMessage: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val localDensity = LocalDensity.current
    val view = LocalView.current
    val isDark = isSystemInDarkTheme()
    val globalBackdrop = LocalLiquidGlassBackdrop.current

    val trackWidth = 320.dp
    val trackHeight = 94.dp
    val handleSize = 78.dp
    val handlePadding = 8.dp

    val trackWidthPx = with(localDensity) { trackWidth.toPx() }
    val handleSizePx = with(localDensity) { handleSize.toPx() }
    val handlePaddingPx = with(localDensity) { handlePadding.toPx() }

    val maxDrag = trackWidthPx - handleSizePx - (handlePaddingPx * 2)

    val trackBgColor =
        if (useLg || useBlur) bottomBarColor else MaterialTheme.colorScheme.onSurface.copy(alpha = if (isDark) 0.15f else 0.1f)
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
                    colors = IconButtonDefaults.iconButtonColors(containerColor = trackBgColor.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .size(60.dp)
                        .then(
                            if (useLg && globalBackdrop != null) Modifier.drawBackdrop(
                                backdrop = globalBackdrop,
                                shape = { CircleShape },
                                shadow = { Shadow(radius = 8.dp) },
                                effects = {
                                    val d = density
                                    colorControls(saturation = 1.3f)
                                    blur(blurIntensity * d)
                                    lens(refractionHeight = 18f * d, refractionAmount = 52f * d)
                                },
                                highlight = { Highlight.Default }
                            )
                            else if (useBlur && globalBackdrop != null) Modifier.drawPlainBackdrop(
                                backdrop = globalBackdrop,
                                shape    = { CircleShape },
                                shadow = { Shadow(radius = 8.dp) },
                                effects  = { blur(blurIntensity * density) }
                            )
                            else Modifier.background(
                                trackBgColor,
                                CircleShape
                            )
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
                    colors = IconButtonDefaults.iconButtonColors(containerColor = trackBgColor.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .size(60.dp)
                        .then(
                            if (useLg && globalBackdrop != null) Modifier.drawBackdrop(
                                backdrop = globalBackdrop,
                                shape = { CircleShape },
                                shadow = { Shadow(radius = 8.dp) },
                                effects = {
                                    val d = density
                                    colorControls(saturation = 1.3f)
                                    blur(blurIntensity * d)
                                    lens(refractionHeight = 18f * d, refractionAmount = 52f * d)
                                },
                                highlight = { Highlight.Default }
                            )
                            else if (useBlur && globalBackdrop != null) Modifier.drawPlainBackdrop(
                                backdrop = globalBackdrop,
                                shape    = { CircleShape },
                                shadow = { Shadow(radius = 8.dp) },
                                effects  = { blur(blurIntensity * density) }
                            )
                            else Modifier.background(
                                trackBgColor,
                                CircleShape
                            )
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
                .width(trackWidth + 16.dp)
                .height(trackHeight + 16.dp)
                .clip(CircleShape)
                .padding(8.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Surface(
                shape           = CircleShape,
                color           = trackBgColor.copy(alpha = 0.35f),
                shadowElevation = 0.dp,
                tonalElevation  = 0.dp,
                modifier = Modifier
                    .height(trackHeight)
                    .align(Alignment.CenterEnd)
                    .width(
                        with(localDensity) {
                            val width = trackWidthPx - offsetX.value
                            width.coerceAtLeast(0f).toDp()
                        }
                    )
                    .then(
                        if (useLg && globalBackdrop != null) Modifier.drawBackdrop(
                            backdrop = globalBackdrop,
                            shape = { CircleShape },
                            shadow = { Shadow(radius = 8.dp) },
                            effects = {
                                val d = density
                                colorControls(saturation = 1.3f)
                                blur(blurIntensity * d)
                                lens(refractionHeight = 18f * d, refractionAmount = 52f * d)
                            },
                            highlight = { Highlight.Default }
                        )
                        else if (useBlur && globalBackdrop != null) Modifier.drawPlainBackdrop(
                            backdrop = globalBackdrop,
                            shape    = { CircleShape },
                            shadow = { Shadow(radius = 8.dp) },
                            effects  = { blur(blurIntensity * density) }
                        )
                        else Modifier
                    )
            ) {}

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
    useLg: Boolean,
    useBlur: Boolean,
    blurIntensity: Float,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    onAnswerAndDecline: (() -> Unit)?
) {
    val declineColor = if (useLg || useBlur) color_call_end.copy(alpha = 0.65f) else color_call_end
    val answerColor = if (useLg || useBlur) color_call_button.copy(alpha = 0.65f) else color_call_button
    val globalBackdrop = LocalLiquidGlassBackdrop.current

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
                    if (isPressed) 24.dp else 42.dp,
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
                    modifier = Modifier.size(height = 68.dp, width = 82.dp)
                        .then(
                            if (useLg && globalBackdrop != null) Modifier.drawBackdrop(
                                backdrop = globalBackdrop,
                                shape = { RoundedCornerShape(radius) },
                                shadow = { Shadow(radius = 8.dp) },
                                effects = {
                                    val d = density
                                    colorControls(saturation = 1.3f)
                                    blur(blurIntensity * d)
                                    lens(refractionHeight = 18f * d, refractionAmount = 52f * d)
                                },
                                highlight = { Highlight.Default }
                            )
                            else if (useBlur && globalBackdrop != null) Modifier.drawPlainBackdrop(
                                backdrop = globalBackdrop,
                                shape    = { RoundedCornerShape(radius) },
                                shadow = { Shadow(radius = 8.dp) },
                                effects  = { blur(blurIntensity * density) }
                            )
                            else Modifier
                        ),
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
                    if (isPressed) 24.dp else 42.dp,
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
                    modifier = Modifier.size(height = 68.dp, width = 82.dp)
                        .then(
                            if (useLg && globalBackdrop != null) Modifier.drawBackdrop(
                                backdrop = globalBackdrop,
                                shape = { RoundedCornerShape(radius) },
                                shadow = { Shadow(radius = 8.dp) },
                                effects = {
                                    val d = density
                                    colorControls(saturation = 1.3f)
                                    blur(blurIntensity * d)
                                    lens(refractionHeight = 18f * d, refractionAmount = 52f * d)
                                },
                                highlight = { Highlight.Default }
                            )
                            else if (useBlur && globalBackdrop != null) Modifier.drawPlainBackdrop(
                                backdrop = globalBackdrop,
                                shape    = { RoundedCornerShape(radius) },
                                shadow = { Shadow(radius = 8.dp) },
                                effects  = { blur(blurIntensity * density) }
                            )
                            else Modifier
                        ),
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
                    if (isPressed) 24.dp else 42.dp,
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
                    modifier = Modifier.size(height = 68.dp, width = 82.dp)
                        .then(
                            if (useLg && globalBackdrop != null) Modifier.drawBackdrop(
                                backdrop = globalBackdrop,
                                shape = { RoundedCornerShape(radius) },
                                shadow = { Shadow(radius = 8.dp) },
                                effects = {
                                    val d = density
                                    colorControls(saturation = 1.3f)
                                    blur(blurIntensity * d)
                                    lens(refractionHeight = 18f * d, refractionAmount = 52f * d)
                                },
                                highlight = { Highlight.Default }
                            )
                            else if (useBlur && globalBackdrop != null) Modifier.drawPlainBackdrop(
                                backdrop = globalBackdrop,
                                shape    = { RoundedCornerShape(radius) },
                                shadow = { Shadow(radius = 8.dp) },
                                effects  = { blur(blurIntensity * density) }
                            )
                            else Modifier
                        ),
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
fun DefaultSwipeToAnswer(
    useLg: Boolean,
    useBlur: Boolean,
    blurIntensity: Float,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    onMessage: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val localDensity = LocalDensity.current
    val view = LocalView.current
    val isDark = isSystemInDarkTheme()
    val globalBackdrop = LocalLiquidGlassBackdrop.current

    val trackWidth = 320.dp
    val trackHeight = 80.dp
    val handleHeight = 68.dp
    val handleWidth = 96.dp
    val handlePadding = 6.dp

    val trackWidthPx = with(localDensity) { trackWidth.toPx() }
    val handleWidthPx = with(localDensity) { handleWidth.toPx() }
    val handlePaddingPx = with(localDensity) { handlePadding.toPx() }

    val maxDrag = trackWidthPx - handleWidthPx - (handlePaddingPx * 2)

    val buttonBgColor =
        if (useLg || useBlur) bottomBarColor else MaterialTheme.colorScheme.onSurface.copy(alpha = if (isDark) 0.15f else 0.1f)
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
                    colors = IconButtonDefaults.iconButtonColors(containerColor = buttonBgColor.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .size(60.dp)
                        .then(
                            if (useLg && globalBackdrop != null) Modifier.drawBackdrop(
                                backdrop = globalBackdrop,
                                shape = { CircleShape },
                                shadow = { Shadow(radius = 8.dp) },
                                effects = {
                                    val d = density
                                    colorControls(saturation = 1.3f)
                                    blur(blurIntensity * d)
                                    lens(refractionHeight = 18f * d, refractionAmount = 52f * d)
                                },
                                highlight = { Highlight.Default }
                            )
                            else if (useBlur && globalBackdrop != null) Modifier.drawPlainBackdrop(
                                backdrop = globalBackdrop,
                                shape    = { CircleShape },
                                shadow = { Shadow(radius = 8.dp) },
                                effects  = { blur(blurIntensity * density) }
                            )
                            else Modifier.background(
                                buttonBgColor,
                                CircleShape
                            )
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
                    colors = IconButtonDefaults.iconButtonColors(containerColor = buttonBgColor.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .size(60.dp)
                        .then(
                            if (useLg && globalBackdrop != null) Modifier.drawBackdrop(
                                backdrop = globalBackdrop,
                                shape = { CircleShape },
                                shadow = { Shadow(radius = 8.dp) },
                                effects = {
                                    val d = density
                                    colorControls(saturation = 1.3f)
                                    blur(blurIntensity * d)
                                    lens(refractionHeight = 18f * d, refractionAmount = 52f * d)
                                },
                                highlight = { Highlight.Default }
                            )
                            else if (useBlur && globalBackdrop != null) Modifier.drawPlainBackdrop(
                                backdrop = globalBackdrop,
                                shape    = { CircleShape },
                                shadow = { Shadow(radius = 8.dp) },
                                effects  = { blur(blurIntensity * density) }
                            )
                            else Modifier.background(
                                buttonBgColor,
                                CircleShape
                            )
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
                .width(trackWidth + 16.dp)
                .height(trackHeight + 16.dp)
                .clip(CircleShape)
                .padding(8.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Surface(
                shape           = CircleShape,
                color           = buttonBgColor.copy(alpha = 0.35f),
                shadowElevation = 0.dp,
                tonalElevation  = 0.dp,
                modifier = Modifier
                    .height(trackHeight)
                    .align(Alignment.CenterEnd)
                    .width(
                        with(localDensity) {
                            val width = trackWidthPx - offsetX.value
                            width.coerceAtLeast(0f).toDp()
                        }
                    )
                    .then(
                        if (useLg && globalBackdrop != null) Modifier.drawBackdrop(
                            backdrop = globalBackdrop,
                            shape = { CircleShape },
                            shadow = { Shadow(radius = 8.dp) },
                            effects = {
                                val d = density
                                colorControls(saturation = 1.3f)
                                blur(blurIntensity * d)
                                lens(refractionHeight = 18f * d, refractionAmount = 52f * d)
                            },
                            highlight = { Highlight.Default }
                        )
                        else if (useBlur && globalBackdrop != null) Modifier.drawPlainBackdrop(
                            backdrop = globalBackdrop,
                            shape    = { CircleShape },
                            shadow = { Shadow(radius = 8.dp) },
                            effects  = { blur(blurIntensity * density) }
                        )
                        else Modifier
                    )
            ) {}

            val shimmerColor = buttonIconColor.copy(alpha = 0.4f)

            val brush = Brush.linearGradient(
                colors = listOf(shimmerColor, buttonIconColor, shimmerColor),
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
    isSelected: Boolean = false,
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
        shape = RoundedCornerShape(cardCornerExtraSmall),
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
                if (isSelected) RillIconBox(
                    icon = leadingIcon,
                    iconContainerColor = MaterialTheme.colorScheme.surface,
                    iconBgContainerColor = MaterialTheme.colorScheme.onSurface,
                ) else RillIconBox(
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