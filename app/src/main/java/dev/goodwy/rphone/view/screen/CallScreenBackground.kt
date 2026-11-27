// Based on RivoPhoneApp by user-grinch (© 2025-2026 RivoPhone Project / user-grinch), GPL-3.0.
// Modified by Goodwy on 2026-11-12.
package dev.goodwy.rphone.view.screen

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Scale
import dev.goodwy.rphone.controller.util.CallingCardStyle
import dev.goodwy.rphone.controller.util.CallingCardTransformMath
import dev.goodwy.rphone.liquidglass.backdrops.LayerBackdrop
import dev.goodwy.rphone.liquidglass.backdrops.layerBackdrop
import dev.goodwy.rphone.view.theme.RillDurations

private const val TopScrimFraction = 0.36f
private const val BottomScrimFraction = 0.54f
private const val MAX_WALLPAPER_DECODE_PX = 2048

/**
 * Renders a calling card wallpaper image with non-destructive zoom (`[scale]`) and
 * viewport-normalized pan (`[offsetX]`, `[offsetY]`) clamped cleanly to the image edges.
 *
 * Drawing the Coil painter directly into a clipped canvas preserves the full cover-fit
 * overflow so wide or tall photos can be repositioned even at `1.0x`, and keeps full
 * bitmap resolution when zoomed in up to `4.0x`.
 */
@Composable
fun CallingCardWallpaperImage(
    model: Any?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    scale: Float = CallingCardStyle.DEFAULT_BG_SCALE,
    offsetX: Float = 0f,
    offsetY: Float = 0f,
    blurDp: Int = 0,
    crossfade: Boolean = true,
    onSuccess: (() -> Unit)? = null,
    onError: (() -> Unit)? = null,
    onIntrinsicSize: ((Size) -> Unit)? = null
) {
    val context = LocalContext.current
    val request = remember(model, context, crossfade) {
        when (model) {
            null -> null
            is ImageRequest -> model
            else -> ImageRequest.Builder(context)
                .data(model)
                .size(MAX_WALLPAPER_DECODE_PX, MAX_WALLPAPER_DECODE_PX)
                .scale(Scale.FIT)
                .precision(Precision.INEXACT)
                .apply {
                    if (crossfade) crossfade(RillDurations.Long1)
                }
                .build()
        }
    }

    if (request == null) return

    val painter = rememberAsyncImagePainter(
        model = request,
        onState = { state ->
            when (state) {
                is AsyncImagePainter.State.Success -> {
                    onSuccess?.invoke()
                    val intrinsic = state.painter.intrinsicSize
                    if (intrinsic.isSpecified && intrinsic.width > 0f && intrinsic.height > 0f) {
                        onIntrinsicSize?.invoke(intrinsic)
                    }
                }
                is AsyncImagePainter.State.Error -> {
                    onError?.invoke()
                }
                else -> Unit
            }
        }
    )

    val intrinsicSize = painter.intrinsicSize
    LaunchedEffect(intrinsicSize) {
        if (intrinsicSize.isSpecified && intrinsicSize.width > 0f && intrinsicSize.height > 0f) {
            onIntrinsicSize?.invoke(intrinsicSize)
        }
    }

    Box(
        modifier = modifier
            .clipToBounds()
            .then(
                if (contentDescription != null) {
                    Modifier.semantics {
                        this.contentDescription = contentDescription
                        this.role = Role.Image
                    }
                } else {
                    Modifier
                }
            )
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (blurDp > 0) Modifier.blur(blurDp.dp)
                    else Modifier
                )
        ) {
            val intrinsic = painter.intrinsicSize
            val intrinsicW = if (intrinsic.isSpecified && intrinsic.width > 0f) intrinsic.width else 0f
            val intrinsicH = if (intrinsic.isSpecified && intrinsic.height > 0f) intrinsic.height else 0f
            val metrics = CallingCardTransformMath.computeDrawMetrics(
                containerWidth = size.width,
                containerHeight = size.height,
                intrinsicWidth = intrinsicW,
                intrinsicHeight = intrinsicH,
                scale = scale,
                offsetX = offsetX,
                offsetY = offsetY
            )
            if (metrics.drawWidth > 0f && metrics.drawHeight > 0f) {
                clipRect {
                    translate(left = metrics.leftPx, top = metrics.topPx) {
                        with(painter) {
                            draw(size = Size(metrics.drawWidth, metrics.drawHeight))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ExpressiveBackground(
    photoUri: String?,
    backgroundUri: String? = null,
    liquidGlassBackdrop: LayerBackdrop,
    hideGradient: Boolean,
    enableParticles: Boolean = true,
    backgroundBlurDp: Int = 0,
    backgroundScale: Float = CallingCardStyle.DEFAULT_BG_SCALE,
    backgroundOffsetX: Float = 0f,
    backgroundOffsetY: Float = 0f,
    onBackgroundIntrinsicSize: ((Size) -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme
    val surface = scheme.surface

    val hasCustomBackground = !backgroundUri.isNullOrEmpty()
    var customFailed by remember(backgroundUri) { mutableStateOf(false) }

    val customVisible = hasCustomBackground && !customFailed
    val photoVisible = !photoUri.isNullOrEmpty() && !customVisible

    Box(modifier = Modifier.fillMaxSize().layerBackdrop(liquidGlassBackdrop).background(surface)) {
        if (!customVisible && enableParticles) {
            FloatingParticles()
        }

        if (photoVisible) {
            AsyncImage(
                model = photoUri,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().blur(56.dp),
                contentScale = ContentScale.Crop,
                alpha = 0.42f
            )
        }

        if (hasCustomBackground) {
            CallingCardWallpaperImage(
                model = backgroundUri,
                modifier = Modifier.fillMaxSize(),
                scale = backgroundScale,
                offsetX = backgroundOffsetX,
                offsetY = backgroundOffsetY,
                blurDp = backgroundBlurDp,
                onSuccess = { customFailed = false },
                onError = { customFailed = true },
                onIntrinsicSize = onBackgroundIntrinsicSize
            )
        }

        if (customVisible || photoVisible) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(TopScrimFraction)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            0f to surface.copy(alpha = 0.88f),
                            0.5f to surface.copy(alpha = 0.60f),
                            1f to surface.copy(alpha = 0f)
                        )
                    )
            )

            if (!hideGradient) Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(BottomScrimFraction)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            0f to surface.copy(alpha = 0f),
                            0.34f to surface.copy(alpha = 0.42f),
                            0.72f to surface.copy(alpha = 0.88f),
                            1f to surface.copy(alpha = 0.88f)
                        )
                    )
            )
        }
    }
}

@Composable
fun FloatingParticles() {
    val infiniteTransition = rememberInfiniteTransition(label = "particles")

    Box(modifier = Modifier.fillMaxSize()) {
        repeat(10) { index ->
            val startX = (index * 100f) % 1000f
            val startY = (index * 150f) % 1500f

            val animX by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 100f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 10000 + index * 1000, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "x_$index"
            )

            val animY by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = -150f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 12000 + index * 1200, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "y_$index"
            )

            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.1f,
                targetValue = 0.4f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 5000 + index * 500, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "alpha_$index"
            )

            Box(
                modifier = Modifier
                    .offset(x = (startX + animX).dp, y = (startY + animY).dp)
                    .size((10 + index % 20).dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha))
                    .blur(2.dp)
            )
        }
    }
}
