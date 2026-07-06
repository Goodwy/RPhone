package dev.goodwy.rphone.view.screen.onboarding

import android.content.res.Configuration
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.R
import kotlin.math.cos
import kotlin.math.sin

data class MorphingPage(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val shapeCornerPercent: Int,
    val rotation: Float,
    val scale: Float,
    val teethCount: Int
)

@Composable
fun MorphingOnboardingScreen(onFinished: () -> Unit) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    var currentPage by remember { mutableIntStateOf(0) }

    val pages = listOf(
        MorphingPage(
            icon = Icons.Default.BugReport,
            title = stringResource(R.string.page_title_warning),
            description = stringResource(R.string.page_subtitle_warning),
            shapeCornerPercent = 50,
            rotation = 35f,
            scale = 0.9f,
            teethCount = 8
        ),
        MorphingPage(
            icon = Icons.Default.PrivacyTip,
            title = stringResource(R.string.page_title_one),
            description = stringResource(R.string.page_subtitle_one),
            shapeCornerPercent = 30,
            rotation = 0f,
            scale = 1f,
            teethCount = 10
        ),
        MorphingPage(
            icon = Icons.Default.Lock,
            title = stringResource(R.string.page_title_two),
            description = stringResource(R.string.page_subtitle_two),
            shapeCornerPercent = 40,
            rotation = 45f,
            scale = 1.2f,
            teethCount = 12
        ),
        MorphingPage(
            icon = Icons.Default.Dialpad,
            title = stringResource(R.string.page_title_three),
            description = stringResource(R.string.page_subtitle_three),
            shapeCornerPercent = 50,
            rotation = 0f,
            scale = 1.1f,
            teethCount = 14
        )
    )

    val cornerPercent by animateFloatAsState(
        targetValue = pages[currentPage].shapeCornerPercent.toFloat(),
        animationSpec = tween(500),
        label = "corner"
    )
    val rotation by animateFloatAsState(
        targetValue = pages[currentPage].rotation,
        animationSpec = tween(500),
        label = "rotation"
    )
    val scale by animateFloatAsState(
        targetValue = pages[currentPage].scale,
        animationSpec = tween(500),
        label = "scale"
    )
    val shapeSize by animateDpAsState(
        targetValue = (140 * pages[currentPage].scale).dp,
        animationSpec = tween(500),
        label = "size"
    )
    val teethCount by animateIntAsState(
        targetValue = pages[currentPage].teethCount,
        animationSpec = tween(200),
        label = "size"
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.navigationBarsPadding().fillMaxSize()) {
            // Background morphing shapes
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .offset(x = (-50).dp, y = 100.dp)
                    .rotate(rotation * 2)
                    .clip(RoundedCornerShape(cornerPercent.toInt()))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
            )

            Box(
                modifier = Modifier
                    .size(150.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = 50.dp, y = 200.dp)
                    .rotate(-rotation)
                    .clip(RoundedCornerShape((50 - cornerPercent / 2).toInt()))
                    .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f))
            )

            Box(
                modifier = Modifier
                    .size(130.dp)
                    .align(Alignment.CenterStart)
                    .offset(x = 20.dp, y = (140).dp)
                    .rotate(rotation * 1.5f)
                    .scale(scale)
                    .clip(wavyCircleShape(teethCount)) //GearShape(teethCount = teethCount)
                    .background(
                        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                    )
            )

            // Skip button
            TextButton(
                onClick = onFinished,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(16.dp)
            ) {
                Text(stringResource(R.string.skip))
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.weight(1f))

                // Main morphing shape with icon
                Box(
                    modifier = Modifier
                        .size(shapeSize)
                        .rotate(rotation)
                        .clip(RoundedCornerShape(cornerPercent.toInt()))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = pages[currentPage].icon,
                        contentDescription = null,
                        modifier = Modifier
                            .size(56.dp)
                            .rotate(-rotation), // Counter-rotate to keep icon upright
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                val height = if (isLandscape) 24.dp else 48.dp
                Spacer(modifier = Modifier.height(height))

                // Title
                Text(
                    text = pages[currentPage].title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Description
                Text(
                    text = pages[currentPage].description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )

                Spacer(modifier = Modifier.weight(1f))

                // Morphing indicators
                Row(
                    horizontalArrangement = Arrangement.Center
                ) {
                    repeat(pages.size) { index ->
                        val indicatorCorner by animateFloatAsState(
                            targetValue = 50f, //if (index == currentPage) 4f else 50f,
                            animationSpec = tween(300),
                            label = "indicatorCorner"
                        )
                        val indicatorWidth by animateDpAsState(
                            targetValue = if (index == currentPage) 24.dp else 8.dp,
                            animationSpec = tween(300),
                            label = "indicatorWidth"
                        )

                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .size(indicatorWidth, 8.dp)
                                .clip(RoundedCornerShape(indicatorCorner.toInt()))
                                .background(
                                    if (index == currentPage)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.outlineVariant
                                )
                        )
                    }
                }

                if (!isLandscape) Spacer(modifier = Modifier.height(32.dp))

                // Navigation
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (currentPage > 0) {
                        TextButton(onClick = { currentPage-- }) {
//                            Text("Back")
                            Icon(
                                Icons.AutoMirrored.Rounded.ArrowBack,
                                "Back",
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    Button(
                        onClick = {
                            if (currentPage < pages.size - 1) {
                                currentPage++
                            } else {
                                onFinished()
                            }
                        },
                        shape = RoundedCornerShape(cornerPercent.toInt().coerceIn(10, 50))
                    ) {
//                        Text(
//                            text = if (currentPage == pages.size - 1) "Get Started" else "Next",
//                            modifier = Modifier.padding(horizontal = 16.dp)
//                        )
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowForward,
                            if (currentPage == pages.size - 1) "Get Started" else "Next",
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

class GearShape(
    private val teethCount: Int = 8,
    private val toothRadius: Float = 0.85f,
    private val innerRadius: Float = 0.65f
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val width = size.width
        val height = size.height
        val centerX = width / 2
        val centerY = height / 2
        val outerR = minOf(width, height) / 2
        val toothR = outerR * toothRadius
        val innerR = outerR * innerRadius

        val path = Path()
        val angleStep = 360.0 / teethCount

        for (i in 0 until teethCount) {
            val angle1 = i * angleStep
            val angle2 = angle1 + angleStep / 2
            val angle3 = angle1 + angleStep

            val rad1 = Math.toRadians(angle1)
            val rad2 = Math.toRadians(angle2)
            val rad3 = Math.toRadians(angle3)

            val x1 = centerX + innerR * cos(rad1).toFloat()
            val y1 = centerY + innerR * sin(rad1).toFloat()

            val x2 = centerX + toothR * cos(rad2).toFloat()
            val y2 = centerY + toothR * sin(rad2).toFloat()

            val x3 = centerX + innerR * cos(rad3).toFloat()
            val y3 = centerY + innerR * sin(rad3).toFloat()

            if (i == 0) path.moveTo(x1, y1)
            path.lineTo(x2, y2)
            path.lineTo(x3, y3)
        }
        path.close()

        return Outline.Generic(path)
    }
}

fun wavyCircleShape(
    waveCount: Int = 12,
    waveAmplitude: Float = 0.04f
): Shape = GenericShape { size, _ ->
    val cx = size.width / 2f
    val cy = size.height / 2f
    val maxRadius = minOf(size.width, size.height) / 2f
    val baseRadius = maxRadius * (1f - waveAmplitude)
    val steps = 720

    val startX = cx + baseRadius * (1f + waveAmplitude * sin(0f))
    val startY = cy
    moveTo(startX, startY)

    for (i in 1..steps) {
        val angle = (i.toFloat() / steps) * 2f * Math.PI.toFloat()
        val r = baseRadius * (1f + waveAmplitude * sin(waveCount * angle))
        val x = cx + r * cos(angle)
        val y = cy + r * sin(angle)
        lineTo(x, y)
    }
    close()
}
