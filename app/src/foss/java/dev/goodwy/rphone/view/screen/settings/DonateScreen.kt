package dev.goodwy.rphone.view.screen.settings

import android.content.Context
import android.view.Surface
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Animation
import androidx.compose.material3.*
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.view.components.NavigationIcon
import dev.goodwy.rphone.view.components.RillAnimatedSection
import dev.goodwy.rphone.view.components.performAppHaptic
import dev.goodwy.rphone.view.screen.FloatingParticles
import dev.goodwy.rphone.view.theme.customColors
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.goodwy.rphone.COFFEE_URL
import dev.goodwy.rphone.DONATE_URL
import dev.goodwy.rphone.cardCornerMedium
import dev.goodwy.rphone.controller.util.HtmlTextView
import dev.goodwy.rphone.controller.util.openLink
import dev.goodwy.rphone.controller.util.toast
import dev.goodwy.rphone.view.components.RillSwitchListItem
import org.koin.compose.koinInject
import kotlin.math.cos
import kotlin.math.sin

data class Donate(
    val headline: String,
    val supporting: String? = null,
    val trailing: String? = null,
    val label: String? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun DonateScreen(navigator: DestinationsNavigator) {
    val context = LocalContext.current

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.65f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "logoScale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(500),
        label = "logoAlpha"
    )

    val prefs = koinInject<PreferenceManager>()
    val settingsState by prefs.settingsChanged.collectAsState()
    var isPro by remember(settingsState) { mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_IS_PRO_FOSS, false)) }
    val themeMode      = prefs.getString(PreferenceManager.KEY_THEME_MODE, "auto") ?: "auto"
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        "light", "white"  -> false
        "dark",  "black"  -> true
        "auto_bw"         -> systemDark
        else              -> systemDark
    }

    val rotation =
        (context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager).defaultDisplay.rotation
    val isRotation90 = rotation == Surface.ROTATION_90
    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets.systemBars.only(
                    if (isRotation90) WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                    else WindowInsetsSides.Top
                ),
                title = {  },
                navigationIcon = {
                    NavigationIcon(onClick = { navigator.navigateUp() })
                },
                actions = {
                    TextButton(onClick = { openLink(context, DONATE_URL) }) { Text(stringResource(R.string.support_all_options)) }
                    Spacer(modifier = Modifier.size(4.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        FloatingStars()
        FloatingParticles()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = padding.calculateTopPadding(),
                    start = 0.dp,
                    end = 0.dp,
                    bottom = 0.dp
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 100.dp), // Indent for the button
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    modifier = Modifier
                        .size(120.dp)
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFFF49154),
                                    Color(0xFFED6D93)
                                )
                            ),
                            shape = RoundedCornerShape(36.dp)
                        )
                        .scale(scale)
                        .alpha(alpha),
                    shape = RoundedCornerShape(36.dp),
                    color = Color.Transparent,
                    shadowElevation = 6.dp,
                    tonalElevation = 8.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_plus_support),
                            contentDescription = null,
                            modifier = Modifier.size(86.dp),
                            tint = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = stringResource(R.string.support_development),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(cardCornerMedium),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.support_development_description1),
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = stringResource(R.string.support_development_description2),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = stringResource(R.string.support_development_description3),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = stringResource(R.string.unlock_all_features),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
//                            Text(
//                                text = "Support the project and get access to everything Rill Phone has to offer.",
//                                style = MaterialTheme.typography.bodySmall,
//                                color = MaterialTheme.colorScheme.onSurfaceVariant
//                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))

                        val thankText = stringResource(R.string.thank_you_for_your_support)
                        val lockedText = stringResource(R.string.features_locked)
                        Switch(
                            checked = isPro,
                            onCheckedChange = {
                                isPro = it
                                prefs.setBoolean(PreferenceManager.KEY_IS_PRO_FOSS, it)
                                context.toast(if (it) thankText else lockedText)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            )
                        )
                    }
                }

                if (isPro) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.all_features_unlocked),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(cardCornerMedium),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.your_donation_ensures),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )

                        listOf(
                            stringResource(R.string.your_donation_ensures_1),
                            stringResource(R.string.your_donation_ensures_2),
                            stringResource(R.string.your_donation_ensures_3),
                            stringResource(R.string.your_donation_ensures_4)
                        ).forEach { item ->
                            Text(
                                text = item,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(padding.calculateBottomPadding() + 24.dp))
            }

            RillAnimatedSection(delayMs = 120L) {
                val interactionSource = remember { MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()

                val cornerRadius by animateDpAsState(
                    targetValue = if (isPressed) 24.dp else 60.dp,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label = "ButtonShape"
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = padding.calculateBottomPadding() + 24.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Button(
                        onClick = { openLink(context, COFFEE_URL) },
                        interactionSource = interactionSource,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp),
                        shape = RoundedCornerShape(cornerRadius),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.onSurface,
                            contentColor = MaterialTheme.colorScheme.surface
                        ),
                        contentPadding = PaddingValues(24.dp)
                    ) {
                        Text(
                            "☕ Buy Me a Coffee",
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FloatingStars(count: Int = 12) {
    val infiniteTransition = rememberInfiniteTransition(label = "stars")

    val positions = remember(count) {
        List(count) {
            val random = java.util.Random()
            Pair(
                random.nextFloat() * 1000f,
                random.nextFloat() * 1500f
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        repeat(count) { index ->
            val (startX, startY) = positions[index]

            val animX by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 80f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 9000 + index * 800, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "x_$index"
            )

            val animY by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = -120f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 11000 + index * 1000, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "y_$index"
            )

            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.2f,
                targetValue = 0.7f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 4000 + index * 400, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "alpha_$index"
            )

            val rotation by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 6000 + index * 400, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "rotation_$index"
            )

            val size = (10 + index * 2).dp

            FourPointStar(
                modifier = Modifier
                    .offset(x = (startX + animX).dp, y = (startY + animY).dp)
                    .size(size)
                    .graphicsLayer {
                        rotationZ = rotation
                        this.alpha = alpha
                    },
                color = MaterialTheme.colorScheme.primary,
                innerRadiusRatio = 0.3f
            )
        }
    }
}

@Composable
fun FourPointStar(
    modifier: Modifier,
    color: Color,
    innerRadiusRatio: Float = 0.35f
) {
    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val outerRadius = size.minDimension / 2f
        val innerRadius = outerRadius * innerRadiusRatio

        val starPath = Path().apply {
            for (i in 0 until 4) {
                val outerAngle = Math.toRadians((i * 90.0) - 90.0)
                val innerAngle = Math.toRadians((i * 90.0) - 45.0)

                val outerX = center.x + outerRadius * cos(outerAngle).toFloat()
                val outerY = center.y + outerRadius * sin(outerAngle).toFloat()
                val innerX = center.x + innerRadius * cos(innerAngle).toFloat()
                val innerY = center.y + innerRadius * sin(innerAngle).toFloat()

                if (i == 0) {
                    moveTo(outerX, outerY)
                } else {
                    lineTo(outerX, outerY)
                }
                lineTo(innerX, innerY)
            }
            close()
        }

        drawPath(
            path = starPath,
            color = color.copy(alpha = 0.3f),
            style = Fill
        )
        drawPath(
            path = starPath,
            color = color.copy(alpha = 0.8f),
            style = Stroke(width = 1.5f)
        )
    }
}