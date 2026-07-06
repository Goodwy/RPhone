package dev.goodwy.rphone.view.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun RillLoadingIndicatorView() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(36.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 3.dp
            )
            // Pulsing dots row
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(3) { index ->
                    PulsingDot(delayMs = index * 150L)
                }
            }
        }
    }
}

@Composable
private fun PulsingDot(delayMs: Long) {
    var active by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(delayMs)
        active = true
    }
    val infiniteTransition = rememberInfiniteTransition(label = "dot$delayMs")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.6f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600, easing = EaseInOut), RepeatMode.Reverse),
        label = "dotScale$delayMs"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600, easing = EaseInOut), RepeatMode.Reverse),
        label = "dotAlpha$delayMs"
    )
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(8.dp).scale(scale).alpha(if (active) alpha else 0f)
    ) {}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RillPullToRefreshIndicator(
    state: PullToRefreshState,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier
) {
    val progress = state.distanceFraction.coerceIn(0f, 1f)

    // Only show the indicator if we are refreshing OR the user is pulling
    if (isRefreshing || progress > 0f) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
                .graphicsLayer {
                    // Modern expressive entrance: scale and fade in
                    val alpha = if (isRefreshing) 1f else progress
                    val scale = if (isRefreshing) 1f else 0.8f + (0.2f * progress)
                    this.alpha = alpha
                    this.scaleX = scale
                    this.scaleY = scale
                },
            contentAlignment = Alignment.TopCenter
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shadowElevation = 6.dp,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 3.dp,
                            strokeCap = StrokeCap.Round,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        CircularProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 3.dp,
                            strokeCap = StrokeCap.Round,
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = Color.Transparent
                        )
                    }
                }
            }
        }
    }
}
