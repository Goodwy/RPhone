package dev.goodwy.rphone.view.screen.settings

import android.content.Context
import android.view.Surface
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import dev.goodwy.rphone.GITHUB_URL
import dev.goodwy.rphone.PRIVACY_POLICY
import dev.goodwy.rphone.R
import dev.goodwy.rphone.SITE_URL
import dev.goodwy.rphone.controller.util.getAppVersion
import dev.goodwy.rphone.controller.util.openLink
import dev.goodwy.rphone.controller.util.toast
import dev.goodwy.rphone.view.components.NavigationIcon
import dev.goodwy.rphone.view.components.RillAnimatedSection
import dev.goodwy.rphone.view.components.RillExpressiveCard
import dev.goodwy.rphone.view.components.RillListItem
import dev.goodwy.rphone.view.theme.customColors
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.ContributorsScreenDestination
import com.ramcosta.composedestinations.generated.destinations.DonateScreenDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.goodwy.rphone.BuildConfig
import dev.goodwy.rphone.GP_DEV_URL
import dev.goodwy.rphone.GP_URL
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.controller.util.PreferenceManager.Companion.KEY_HIGH_SCORE
import dev.goodwy.rphone.controller.util.PreferenceManager.Companion.KEY_IS_PRO_FOSS
import dev.goodwy.rphone.view.components.Title
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import java.util.Calendar
import kotlin.random.Random

private const val EASTER_EGG_TIME_LIMIT = 8000L
private const val EASTER_EGG_REQUIRED_CLICKS = 7
private const val EASTER_EGG_REQUIRED_CLICKS_NEXT = 10

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun AboutAppScreen(navigator: DestinationsNavigator) {
    val context = LocalContext.current
    val appInfo = getAppVersion(context)
    val storeName = when (BuildConfig.FLAVOR) {
        "gplay" -> "GPlay"
        else -> "FOSS"
    }

    var visible by remember { mutableStateOf(false) }
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
    LaunchedEffect(Unit) { visible = true }

    var firstVersionClickTS by remember { mutableLongStateOf(0L) }
    var clicksSinceFirstClick by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    var showDialog by remember { mutableStateOf(false) }
    var showHorseGame by remember { mutableStateOf(false) }
    var showRatingDialog by remember { mutableStateOf(false) }

    fun onClickEasterEgg() {
        val currentTime = System.currentTimeMillis()

        if (firstVersionClickTS == 0L) {
            firstVersionClickTS = currentTime
            scope.launch {
                delay(EASTER_EGG_TIME_LIMIT)
                firstVersionClickTS = 0L
                clicksSinceFirstClick = 0
            }
        }

        clicksSinceFirstClick++

        if (clicksSinceFirstClick == EASTER_EGG_REQUIRED_CLICKS) {
            context.toast("Hello!")
        } else if (clicksSinceFirstClick >= EASTER_EGG_REQUIRED_CLICKS_NEXT) {
            firstVersionClickTS = 0L
            clicksSinceFirstClick = 0
            showHorseGame = true
        }
    }
    if (showDialog) {
        ThemeColorsDialog(
            onDismiss = { showDialog = false }
        )
    }

    if (showHorseGame) {
        Dialog(onDismissRequest = { showHorseGame = false }) {
            HorseGameDialog(
                onDismiss = { showHorseGame = false }
            )
        }
    }

    if (showRatingDialog) {
        RatingDialog(
            onDismiss = { showRatingDialog = false },
            onRate = { rating ->
                if (rating == 5) {
                    openLink(context, GP_URL)
                }
                showRatingDialog = false
            }
        )
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
                title = { Title(stringResource(R.string.about)) },
                navigationIcon = {
                    NavigationIcon(onClick = { navigator.navigateUp() })
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = padding.calculateTopPadding(),
                    start = 0.dp,
                    end = 0.dp,
                    bottom = 0.dp
                )
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier.size(120.dp), //.scale(scale).alpha(alpha),
                shape = RoundedCornerShape(36.dp),
                color = Color.White,//MaterialTheme.colorScheme.primaryContainer,
                shadowElevation = 6.dp,
                tonalElevation = 8.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_phone),
                        contentDescription = null,
                        modifier = Modifier.size(86.dp),
                        tint = Color(0xFF2196F3)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.alpha(alpha)
            )
            Text(
                stringResource(R.string.page_title_two),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.alpha(alpha)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Version badge - Material Expressive style
            Surface(
                onClick = { onClickEasterEgg() },
                shape = RoundedCornerShape(50.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                tonalElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Outlined.Info,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = appInfo.first + " ($storeName)",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            RillAnimatedSection(delayMs = 150L) {
                RillExpressiveCard {
                    val isGPlay = BuildConfig.FLAVOR == "gplay"
                    if (isGPlay) {
                        RillListItem(
                            headline = stringResource(R.string.rate_app),
                            leadingIcon = Icons.Rounded.StarRate,
                            iconContainerColor = Color.Black,
                            iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorOliva,
                            trailingIcon = Icons.Default.ChevronRight,
                            onClick = { showRatingDialog = true }
                        )
                    }
                    RillListItem(
                        headline = stringResource(R.string.other_apps),
                        leadingIcon = if (isGPlay) ImageVector.vectorResource(id = R.drawable.ic_google_play_vector) else ImageVector.vectorResource(id = R.drawable.ic_goodwy),
                        iconContainerColor = Color.Black,
                        iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorOliva,
                        trailingIcon = Icons.Default.ChevronRight,
                        onClick = {
                            val url = if (isGPlay) GP_DEV_URL else SITE_URL
                            openLink(context, url)
                        }
                    )
                    RillListItem(
                        headline = stringResource(R.string.source_code),
                        supporting = "GitHub Repository",
                        leadingIcon = ImageVector.vectorResource(id = R.drawable.ic_github_vector),
                        iconContainerColor = Color.Black,
                        iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorOliva,
                        trailingIcon = Icons.Default.ChevronRight,
                        onClick = { openLink(context, GITHUB_URL) }
                    )
                    RillListItem(
                        headline = stringResource(R.string.support_development),
                        leadingIcon = Icons.Rounded.VolunteerActivism,
                        iconContainerColor = Color.Black,
                        iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorOliva,
                        trailingIcon = Icons.Default.ChevronRight,
                        onClick = { navigator.navigate(DonateScreenDestination) })
                    RillListItem(
                        headline = stringResource(R.string.privacy_policy),
                        leadingIcon = Icons.Rounded.PrivacyTip,
                        iconContainerColor = Color.Black,
                        iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorOliva,
                        trailingIcon = Icons.Default.ChevronRight,
                        onClick = { openLink(context, PRIVACY_POLICY) }
                    )
                    RillListItem(
                        headline = stringResource(R.string.contributors),
                        leadingIcon = Icons.Rounded.LogoDev,
                        iconContainerColor = Color.Black,
                        iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorOliva,
                        trailingIcon = Icons.Default.ChevronRight,
                        onClick = { navigator.navigate(ContributorsScreenDestination) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(42.dp))

            RillAnimatedSection(delayMs = 300L) {
                val currentYear = Calendar.getInstance().get(Calendar.YEAR)
                Text(
                    "© Goodwy, $currentYear",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.alpha(alpha)
                )
            }

            Spacer(modifier = Modifier.height(padding.calculateBottomPadding() + 24.dp))
        }
    }
}

@Composable
fun RatingDialog(
    onDismiss: () -> Unit,
    onRate: (Int) -> Unit
) {
    var selectedRating by remember { mutableIntStateOf(0) }
    var hoveredRating by remember { mutableIntStateOf(0) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Icon
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(64.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.StarRate,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Rate This App",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Your feedback helps us improve!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Star rating
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    for (i in 1..5) {
                        val isSelected = i <= (if (hoveredRating > 0) hoveredRating else selectedRating)
                        IconButton(
                            onClick = {
                                selectedRating = i
                                if (i == 5) {
                                    onRate(5)
                                }
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                if (isSelected) Icons.Rounded.StarRate else Icons.Rounded.StarOutline,
                                contentDescription = "Star $i",
                                modifier = Modifier.size(40.dp),
                                tint = if (isSelected)
                                    Color(0xFFFFD700)
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            )
                        }
                    }
                }

                // Rating labels
                Text(
                    text = when (selectedRating) {
                        1 -> "Terrible"
                        2 -> "Bad"
                        3 -> "Okay"
                        4 -> "Good"
                        5 -> "Excellent! 🎉"
                        else -> "Tap a star to rate"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (selectedRating == 5)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (selectedRating == 5) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Rating message for non-5 stars
                if (selectedRating in 1..4) {
                    Text(
                        text = "We appreciate your feedback!",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                if (selectedRating == 5) {
                    Text(
                        text = "Thank you!",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Close button
                TextButton(
                    onClick = {
                        if (selectedRating in 1..4) {
                            onRate(selectedRating)
                        } else {
                            onDismiss()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = when {
                            selectedRating in 1..5 -> "Submit Feedback"
                            else -> "Not Now"
                        },
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun ThemeColorsDialog(
    onDismiss: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(top = 20.dp, bottom = 8.dp, start = 20.dp, end = 20.dp), /*verticalArrangement = Arrangement.spacedBy(12.dp)*/) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.customColors.colorRed,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Rounded.Palette,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.customColors.colorDarkRed,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Text("Current theme colors", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(16.dp))


                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 8.dp)
                ) {
                    // Primary colors
                    ThemeColorItem("primary", colorScheme.primary)
                    ThemeColorItem("onPrimary", colorScheme.onPrimary)
                    ThemeColorItem("primaryContainer", colorScheme.primaryContainer)
                    ThemeColorItem("onPrimaryContainer", colorScheme.onPrimaryContainer)

                    Spacer(modifier = Modifier.height(8.dp))

                    // Secondary colors
                    ThemeColorItem("secondary", colorScheme.secondary)
                    ThemeColorItem("onSecondary", colorScheme.onSecondary)
                    ThemeColorItem("secondaryContainer", colorScheme.secondaryContainer)
                    ThemeColorItem("onSecondaryContainer", colorScheme.onSecondaryContainer)

                    Spacer(modifier = Modifier.height(8.dp))

                    // Tertiary colors
                    ThemeColorItem("tertiary", colorScheme.tertiary)
                    ThemeColorItem("onTertiary", colorScheme.onTertiary)
                    ThemeColorItem("tertiaryContainer", colorScheme.tertiaryContainer)
                    ThemeColorItem("onTertiaryContainer", colorScheme.onTertiaryContainer)

                    Spacer(modifier = Modifier.height(8.dp))

                    // Error colors
                    ThemeColorItem("error", colorScheme.error)
                    ThemeColorItem("onError", colorScheme.onError)
                    ThemeColorItem("errorContainer", colorScheme.errorContainer)
                    ThemeColorItem("onErrorContainer", colorScheme.onErrorContainer)

                    Spacer(modifier = Modifier.height(8.dp))

                    // Background colors
                    ThemeColorItem("background", colorScheme.background)
                    ThemeColorItem("onBackground", colorScheme.onBackground)

                    Spacer(modifier = Modifier.height(8.dp))

                    // Surface colors
                    ThemeColorItem("onSurface", colorScheme.onSurface)
                    ThemeColorItem("onSurfaceVariant", colorScheme.onSurfaceVariant)
                    ThemeColorItem("surfaceContainerLowest", colorScheme.surfaceContainerLowest)
                    ThemeColorItem("surface", colorScheme.surface)
                    ThemeColorItem("surfaceContainerLow", colorScheme.surfaceContainerLow)
                    ThemeColorItem("surfaceContainer", colorScheme.surfaceContainer)
                    ThemeColorItem("surfaceContainerHigh", colorScheme.surfaceContainerHigh)
                    ThemeColorItem("surfaceContainerHighest", colorScheme.surfaceContainerHighest)
                    ThemeColorItem("surfaceVariant", colorScheme.surfaceVariant)
                    ThemeColorItem("surfaceBright", colorScheme.surfaceBright)
                    ThemeColorItem("surfaceDim", colorScheme.surfaceDim)
                    ThemeColorItem("surfaceTint", colorScheme.surfaceTint)

                    Spacer(modifier = Modifier.height(8.dp))

                    // Outline colors
                    ThemeColorItem("outline", colorScheme.outline)
                    ThemeColorItem("outlineVariant", colorScheme.outlineVariant)

                    Spacer(modifier = Modifier.height(8.dp))

                    // Inverse colors
                    ThemeColorItem("inverseSurface", colorScheme.inverseSurface)
                    ThemeColorItem("inverseOnSurface", colorScheme.inverseOnSurface)
                    ThemeColorItem("inversePrimary", colorScheme.inversePrimary)
                }

                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
            }
        }
    }
}

@Composable
private fun ThemeColorItem(
    colorName: String,
    color: Color
) {
    val interactionSource = remember { MutableInteractionSource() }
    val indication = LocalIndication.current
    val clipboardManager = LocalClipboardManager.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(
                onClick = {
                    clipboardManager.setText(AnnotatedString(color.toHex()))
                },
                interactionSource = interactionSource,
                indication = indication
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Color preview
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(color)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline,
                    shape = RoundedCornerShape(8.dp)
                )
        )

        Spacer(modifier = Modifier.width(16.dp))

        // Color name and hex value
        Column {
            Text(
                text = colorName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = color.toHex(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// Extension function to convert Color to hex string
fun Color.toHex(): String {
    val red = (red * 255).toInt()
    val green = (green * 255).toInt()
    val blue = (blue * 255).toInt()
    val alpha = (alpha * 255).toInt()

    return if (alpha == 255) {
        String.format("#%02X%02X%02X", red, green, blue)
    } else {
        String.format("#%02X%02X%02X%02X", red, green, blue, alpha)
    }
}

@Composable
fun HorseGameDialog(
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .fillMaxWidth()
                .height(500.dp)
        ) {
            HorseGameContent(onDismiss = onDismiss)
        }
    }
}

@Composable
fun HorseGameContent(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var gameState by remember { mutableStateOf(GameState.PLAYING) }
    var score by remember { mutableIntStateOf(0) }

    val prefs: PreferenceManager = koinInject()
    var highScore by remember { mutableIntStateOf(prefs.getInt(KEY_HIGH_SCORE, 0)) }

    var horseY by remember { mutableFloatStateOf(0f) }
    var horseVelocity by remember { mutableFloatStateOf(0f) }
    var obstacles by remember { mutableStateOf(listOf<Obstacle>()) }
    var frameCount by remember { mutableIntStateOf(0) }
    var groundY by remember { mutableFloatStateOf(0f) }
    var canvasHeight by remember { mutableFloatStateOf(0f) }
    var canvasWidth by remember { mutableFloatStateOf(0f) }
    var framesSinceLastObstacle by remember { mutableIntStateOf(0) }
    var nextObstacleFrame by remember { mutableIntStateOf(45) }
    var isInitialized by remember { mutableStateOf(false) }
    var isHighScoreUpdated by remember { mutableStateOf(false) }

    var dayNightCycle by remember { mutableIntStateOf(0) } // 0 - day, 1 - sunset, 2 - night, 3 - dawn
    var cycleProgress by remember { mutableFloatStateOf(0f) }
    var previousScoreDivider by remember { mutableIntStateOf(0) }

    val gravity = 0.6f
    val jumpStrength = -13f
    val obstacleSpeed = 7f
    val groundHeight = 80f
    val horseSize = 50f
    val horseX = 80f
    val minFramesBetweenObstacles = 45
    val maxFramesBetweenObstacles = 90
    val CYCLE_INTERVAL = 2500

    fun resetGame() {
        gameState = GameState.PLAYING
        score = 0
        horseY = 0f
        horseVelocity = 0f
        obstacles = emptyList()
        frameCount = 0
        framesSinceLastObstacle = 0
        nextObstacleFrame = Random.nextInt(minFramesBetweenObstacles, maxFramesBetweenObstacles)
        isInitialized = false
        isHighScoreUpdated = false
        dayNightCycle = 0
        cycleProgress = 0f
        previousScoreDivider = 0
    }

    fun saveHighScore(newScore: Int) {
        if (newScore > highScore) {
            highScore = newScore
            prefs.setInt(KEY_HIGH_SCORE, newScore)
        }
    }

    LaunchedEffect(gameState) {
        while (gameState == GameState.PLAYING) {
            delay(16)

            if (canvasHeight > 0) {
                val currentGroundY = canvasHeight - groundHeight
                groundY = currentGroundY

                if (!isInitialized) {
                    horseY = groundY - horseSize
                    isInitialized = true
                }

                horseVelocity += gravity
                horseY += horseVelocity

                if (horseY > groundY - horseSize) {
                    horseY = groundY - horseSize
                    horseVelocity = 0f
                }

                frameCount++
                framesSinceLastObstacle++

                if (framesSinceLastObstacle >= nextObstacleFrame) {
                    val randomHeight = Random.nextInt(35, 65).toFloat()
                    obstacles = obstacles + Obstacle(
                        x = canvasWidth + 50f,
                        height = randomHeight,
                        width = 22f
                    )
                    framesSinceLastObstacle = 0
                    nextObstacleFrame = Random.nextInt(minFramesBetweenObstacles, maxFramesBetweenObstacles)
                }

                obstacles = obstacles.map { it.copy(x = it.x - obstacleSpeed) }
                    .filter { it.x > -50f }

                obstacles.forEach { obstacle ->
                    val horseLeft = horseX + 5f
                    val horseRight = horseX + 35f
                    val horseTop = horseY + 5f
                    val horseBottom = horseY + 25f

                    val obstacleLeft = obstacle.x + 2f
                    val obstacleRight = obstacle.x + obstacle.width - 2f
                    val obstacleTop = groundY - obstacle.height + 2f
                    val obstacleBottom = groundY - 2f

                    if (horseRight > obstacleLeft &&
                        horseLeft < obstacleRight &&
                        horseBottom > obstacleTop &&
                        horseTop < obstacleBottom
                    ) {
                        gameState = GameState.GAME_OVER

                        val finalScore = score / 2
                        if (finalScore > highScore && !isHighScoreUpdated) {
                            isHighScoreUpdated = true
                            saveHighScore(finalScore)
                        }
                        if (finalScore > 9999) {
                            prefs.setBoolean(KEY_IS_PRO_FOSS, true)
                            context.toast(" App Unlocked")
                        }
                    }
                }

                if (frameCount % 2 == 0) {
                    score++
                }

                // Updating the Day and Night Cycle
                val currentScoreDivider = (score / 2) / CYCLE_INTERVAL
                if (currentScoreDivider != previousScoreDivider) {
                    previousScoreDivider = currentScoreDivider
                    dayNightCycle = (dayNightCycle + 1) % 4
                    cycleProgress = 0f
                }

                // Progress of the Transition
                if (cycleProgress < 1f) {
                    cycleProgress += 0.005f
                    if (cycleProgress > 1f) cycleProgress = 1f
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "🏇 Horse Runner",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            if (highScore > 0) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    tonalElevation = 2.dp
                ) {
                    Text(
                        text = "🏆 $highScore",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = when (dayNightCycle) {
                    0 -> Color(0xFFFFD740)
                    1 -> Color(0xFFFF8F00)
                    2 -> Color(0xFF1A237E)
                    else -> Color(0xFFFF6F00)
                }.copy(alpha = 0.3f),
                tonalElevation = 2.dp
            ) {
                Text(
                    text = when (dayNightCycle) {
                        0 -> "☀️ Day"
                        1 -> "🌅 Sunset"
                        2 -> "🌙 Night"
                        else -> "🌅 Dawn"
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                tonalElevation = 2.dp
            ) {
                Text(
                    text = "⭐ ${score / 2}",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val currentWidth = size.width
                val currentHeight = size.height
                canvasWidth = currentWidth
                canvasHeight = currentHeight

                val groundLevel = currentHeight - groundHeight


                // === COLORS BY CYCLE ===
                val skyColor: Color
                val sunColor: Color
                val sunGlowColor: Color
                val groundColor: Color
                val grassColor: Color
                val cloudColor: Color

                when (dayNightCycle) {
                    0 -> { // Day
                        val progress = cycleProgress
                        skyColor = Color(0xFF87CEEB)
                        sunColor = Color(0xFFFFD700)
                        sunGlowColor = Color(0xFFFFEB3B).copy(alpha = 0.3f)
                        groundColor = Color(0xFF8B6914)
                        grassColor = Color(0xFF4CAF50)
                        cloudColor = Color.White.copy(alpha = 0.7f)
                    }
                    1 -> { // Sunset
                        val progress = cycleProgress
                        val skyR = (135 + (255 - 135) * progress).toInt()
                        val skyG = (206 - (206 - 100) * progress).toInt()
                        val skyB = (235 - (235 - 50) * progress).toInt()
                        skyColor = Color(skyR, skyG, skyB)
                        sunColor = Color(0xFFFF6F00)
                        sunGlowColor = Color(0xFFFFAB00).copy(alpha = 0.3f)
                        groundColor = Color(0xFFA67B5B)
                        grassColor = Color(0xFF66BB6A)
                        cloudColor = Color.White.copy(alpha = 0.5f)
                    }
                    2 -> { // Night
                        val progress = cycleProgress
                        skyColor = Color(0xFF1A237E)
                        sunColor = Color(0xFFFFD700).copy(alpha = 0.2f)
                        sunGlowColor = Color(0xFFFFEB3B).copy(alpha = 0.1f)
                        groundColor = Color(0xFF3E2723)
                        grassColor = Color(0xFF1B5E20)
                        cloudColor = Color.White.copy(alpha = 0.2f)
                    }
                    else -> { // Dawn
                        val progress = cycleProgress
                        val skyR = (30 + (200 - 30) * progress).toInt()
                        val skyG = (60 + (150 - 60) * progress).toInt()
                        val skyB = (120 + (220 - 120) * progress).toInt()
                        skyColor = Color(skyR, skyG, skyB)
                        sunColor = Color(0xFFFF8F00)
                        sunGlowColor = Color(0xFFFFAB00).copy(alpha = 0.2f)
                        groundColor = Color(0xFF6D4C41)
                        grassColor = Color(0xFF388E3C)
                        cloudColor = Color.White.copy(alpha = 0.4f)
                    }
                }

                // Sky
                drawRect(
                    color = skyColor,
                    size = Size(currentWidth, groundLevel)
                )

                // Stars (only at night)
                if (dayNightCycle == 2) {
                    val starCount = 50
                    for (i in 0 until starCount) {
                        val starX = (i * 37 + 13) % currentWidth
                        val starY = (i * 23 + 7) % (groundLevel * 0.7f)
                        val starSize = 2f + (i % 3).toFloat()
                        val starAlpha = 0.5f + (i % 5).toFloat() * 0.1f
                        drawCircle(
                            color = Color.White.copy(alpha = starAlpha),
                            radius = starSize,
                            center = Offset(starX, starY)
                        )
                    }
                }

                // Clouds
                if (dayNightCycle != 2) { // No clouds at night
                    drawCircle(
                        color = cloudColor,
                        radius = 30f,
                        center = Offset(100f, 50f)
                    )
                    drawCircle(
                        color = cloudColor,
                        radius = 25f,
                        center = Offset(130f, 40f)
                    )
                    drawCircle(
                        color = cloudColor,
                        radius = 20f,
                        center = Offset(80f, 60f)
                    )

                    drawCircle(
                        color = cloudColor,
                        radius = 35f,
                        center = Offset(currentWidth - 150f, 70f)
                    )
                    drawCircle(
                        color = cloudColor,
                        radius = 28f,
                        center = Offset(currentWidth - 180f, 60f)
                    )
                }

                // Sun
                drawCircle(
                    color = sunColor,
                    radius = 40f,
                    center = Offset(currentWidth - 60f, 60f)
                )
                drawCircle(
                    color = sunGlowColor,
                    radius = 55f,
                    center = Offset(currentWidth - 60f, 60f)
                )

                // Moon (at night)
                if (dayNightCycle == 2) {
                    drawCircle(
                        color = Color(0xFFE0E0E0),
                        radius = 35f,
                        center = Offset(80f, 50f)
                    )
                    drawCircle(
                        color = skyColor,
                        radius = 30f,
                        center = Offset(95f, 40f)
                    )
                }

                // Ground
                drawRect(
                    color = groundColor,
                    topLeft = Offset(0f, groundLevel),
                    size = Size(currentWidth, groundHeight)
                )

                // Grass
                drawRect(
                    color = grassColor,
                    topLeft = Offset(0f, groundLevel - 5f),
                    size = Size(currentWidth, 10f)
                )

                // Grass blades
                for (i in 0..(currentWidth / 10).toInt()) {
                    val x = (i * 10f) + (if (frameCount % 60 < 30) 0f else 2f)
                    drawLine(
                        color = if (dayNightCycle == 2) Color(0xFF1B5E20) else Color(0xFF388E3C),
                        start = Offset(x, groundLevel - 5f),
                        end = Offset(x + 3f, groundLevel - 15f),
                        strokeWidth = 2f
                    )
                }

                // Obstacles
                obstacles.forEach { obstacle ->
                    val drawX = obstacle.x
                    val drawY = groundLevel - obstacle.height

                    if (drawX > 0 && drawX < currentWidth) {
                        drawRect(
                            color = if (dayNightCycle == 2) Color(0xFF1B5E20) else Color(0xFF2E7D32),
                            topLeft = Offset(drawX, drawY),
                            size = Size(obstacle.width, obstacle.height)
                        )

                        if (obstacle.height > 40f) {
                            drawRect(
                                color = if (dayNightCycle == 2) Color(0xFF1B5E20) else Color(0xFF2E7D32),
                                topLeft = Offset(drawX - 8f, drawY + 10f),
                                size = Size(8f, 15f)
                            )
                            drawRect(
                                color = if (dayNightCycle == 2) Color(0xFF1B5E20) else Color(0xFF2E7D32),
                                topLeft = Offset(drawX + obstacle.width, drawY + 15f),
                                size = Size(8f, 12f)
                            )
                        }

                        for (j in 0..3) {
                            val spineX = drawX + (j * obstacle.width / 3)
                            drawLine(
                                color = if (dayNightCycle == 2) Color(0xFF0D3B0E) else Color(0xFF1B5E20),
                                start = Offset(spineX, drawY + 10f),
                                end = Offset(spineX + 8f, drawY + 5f),
                                strokeWidth = 2f
                            )
                            drawLine(
                                color = if (dayNightCycle == 2) Color(0xFF0D3B0E) else Color(0xFF1B5E20),
                                start = Offset(spineX, drawY + 20f),
                                end = Offset(spineX - 8f, drawY + 15f),
                                strokeWidth = 2f
                            )
                        }
                    }
                }

                // Horse with rider
                val horseDrawX = horseX
                val horseDrawY = if (horseY > 0) horseY else groundLevel - horseSize

                if (horseDrawY > 0 && horseDrawY < currentHeight) {
                    val horseColor = if (dayNightCycle == 2) Color(0xFF6D4C41) else Color(0xFF8D6E63)
                    val horseLightColor = if (dayNightCycle == 2) Color(0xFF795548) else Color(0xFFA1887F)
                    val horseDarkColor = if (dayNightCycle == 2) Color(0xFF4E342E) else Color(0xFF5D4037)
                    val maneColor = if (dayNightCycle == 2) Color(0xFF3E2723) else Color(0xFF5D4037)

                    drawRoundRect(
                        color = horseColor,
                        topLeft = Offset(horseDrawX + 5f, horseDrawY + 5f),
                        size = Size(35f, 22f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f)
                    )

                    drawRoundRect(
                        color = horseLightColor,
                        topLeft = Offset(horseDrawX + 30f, horseDrawY + 5f),
                        size = Size(15f, 20f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
                    )

                    drawRect(
                        color = horseLightColor,
                        topLeft = Offset(horseDrawX + 38f, horseDrawY - 8f),
                        size = Size(8f, 15f)
                    )

                    drawRoundRect(
                        color = horseLightColor,
                        topLeft = Offset(horseDrawX + 42f, horseDrawY - 18f),
                        size = Size(18f, 15f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
                    )

                    drawRoundRect(
                        color = if (dayNightCycle == 2) Color(0xFF8D6E63) else Color(0xFFBCAAA4),
                        topLeft = Offset(horseDrawX + 55f, horseDrawY - 15f),
                        size = Size(10f, 10f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
                    )

                    drawCircle(
                        color = Color.Black,
                        radius = 2.5f,
                        center = Offset(horseDrawX + 50f, horseDrawY - 12f)
                    )
                    drawCircle(
                        color = if (dayNightCycle == 2) Color(0xFFBDBDBD) else Color.White,
                        radius = 1f,
                        center = Offset(horseDrawX + 51f, horseDrawY - 13f)
                    )

                    drawLine(
                        color = horseColor,
                        start = Offset(horseDrawX + 44f, horseDrawY - 18f),
                        end = Offset(horseDrawX + 46f, horseDrawY - 26f),
                        strokeWidth = 4f,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = horseColor,
                        start = Offset(horseDrawX + 48f, horseDrawY - 18f),
                        end = Offset(horseDrawX + 50f, horseDrawY - 26f),
                        strokeWidth = 4f,
                        cap = StrokeCap.Round
                    )

                    for (i in 0..6) {
                        drawLine(
                            color = maneColor,
                            start = Offset(horseDrawX + 32f + i * 4f, horseDrawY - 2f),
                            end = Offset(horseDrawX + 30f + i * 4f, horseDrawY - 16f - (i % 3) * 2f),
                            strokeWidth = 3f,
                            cap = StrokeCap.Round
                        )
                    }

                    val legOffset = if (frameCount % 20 < 10) 6f else -6f

                    drawRoundRect(
                        color = if (dayNightCycle == 2) Color(0xFF4E342E) else Color(0xFF6D4C41),
                        topLeft = Offset(horseDrawX + 8f, horseDrawY + 25f),
                        size = Size(5f, 18f + legOffset),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f, 3f)
                    )
                    drawRoundRect(
                        color = if (dayNightCycle == 2) Color(0xFF4E342E) else Color(0xFF6D4C41),
                        topLeft = Offset(horseDrawX + 16f, horseDrawY + 25f),
                        size = Size(5f, 18f - legOffset),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f, 3f)
                    )
                    drawRoundRect(
                        color = if (dayNightCycle == 2) Color(0xFF4E342E) else Color(0xFF6D4C41),
                        topLeft = Offset(horseDrawX + 28f, horseDrawY + 25f),
                        size = Size(5f, 18f - legOffset),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f, 3f)
                    )
                    drawRoundRect(
                        color = if (dayNightCycle == 2) Color(0xFF4E342E) else Color(0xFF6D4C41),
                        topLeft = Offset(horseDrawX + 36f, horseDrawY + 25f),
                        size = Size(5f, 18f + legOffset),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f, 3f)
                    )

                    drawRect(
                        color = Color(0xFF3E2723),
                        topLeft = Offset(horseDrawX + 8f, horseDrawY + 40f + legOffset),
                        size = Size(5f, 3f)
                    )
                    drawRect(
                        color = Color(0xFF3E2723),
                        topLeft = Offset(horseDrawX + 16f, horseDrawY + 40f - legOffset),
                        size = Size(5f, 3f)
                    )
                    drawRect(
                        color = Color(0xFF3E2723),
                        topLeft = Offset(horseDrawX + 28f, horseDrawY + 40f - legOffset),
                        size = Size(5f, 3f)
                    )
                    drawRect(
                        color = Color(0xFF3E2723),
                        topLeft = Offset(horseDrawX + 36f, horseDrawY + 40f + legOffset),
                        size = Size(5f, 3f)
                    )

                    drawLine(
                        color = maneColor,
                        start = Offset(horseDrawX + 5f, horseDrawY + 8f),
                        end = Offset(horseDrawX - 12f, horseDrawY + 20f),
                        strokeWidth = 4f,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = maneColor,
                        start = Offset(horseDrawX + 5f, horseDrawY + 10f),
                        end = Offset(horseDrawX - 15f, horseDrawY + 25f),
                        strokeWidth = 3f,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = maneColor,
                        start = Offset(horseDrawX + 7f, horseDrawY + 6f),
                        end = Offset(horseDrawX - 10f, horseDrawY + 15f),
                        strokeWidth = 3f,
                        cap = StrokeCap.Round
                    )

                    // Rider
                    val riderBodyColor = if (dayNightCycle == 2) Color(0xFF0D47A1) else Color(0xFF1565C0)

                    drawRoundRect(
                        color = riderBodyColor,
                        topLeft = Offset(horseDrawX + 18f, horseDrawY - 28f),
                        size = Size(20f, 25f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
                    )

                    drawRect(
                        color = if (dayNightCycle == 2) Color(0xFF3E2723) else Color(0xFF5D4037),
                        topLeft = Offset(horseDrawX + 18f, horseDrawY - 8f),
                        size = Size(20f, 4f)
                    )

                    drawRect(
                        color = if (dayNightCycle == 2) Color(0xFFFFD700).copy(alpha = 0.6f) else Color(0xFFFFD700),
                        topLeft = Offset(horseDrawX + 26f, horseDrawY - 8f),
                        size = Size(4f, 4f)
                    )

                    drawCircle(
                        color = if (dayNightCycle == 2) Color(0xFFFFCCBC).copy(alpha = 0.7f) else Color(0xFFFFCCBC),
                        radius = 10f,
                        center = Offset(horseDrawX + 28f, horseDrawY - 38f)
                    )

                    drawArc(
                        color = if (dayNightCycle == 2) Color(0xFF3E2723) else Color(0xFF5D4037),
                        startAngle = 180f,
                        sweepAngle = 180f,
                        useCenter = true,
                        topLeft = Offset(horseDrawX + 18f, horseDrawY - 48f),
                        size = Size(20f, 12f)
                    )

                    drawRect(
                        color = if (dayNightCycle == 2) Color(0xFF3E2723) else Color(0xFF4E342E),
                        topLeft = Offset(horseDrawX + 18f, horseDrawY - 54f),
                        size = Size(20f, 6f)
                    )
                    drawRect(
                        color = if (dayNightCycle == 2) Color(0xFF3E2723) else Color(0xFF4E342E),
                        topLeft = Offset(horseDrawX + 14f, horseDrawY - 50f),
                        size = Size(28f, 4f)
                    )

                    drawCircle(
                        color = Color.Black,
                        radius = 1.5f,
                        center = Offset(horseDrawX + 24f, horseDrawY - 38f)
                    )
                    drawCircle(
                        color = Color.Black,
                        radius = 1.5f,
                        center = Offset(horseDrawX + 32f, horseDrawY - 38f)
                    )

                    drawArc(
                        color = Color.Black,
                        startAngle = 180f,
                        sweepAngle = 180f,
                        useCenter = false,
                        topLeft = Offset(horseDrawX + 24f, horseDrawY - 35f),
                        size = Size(8f, 4f)
                    )

                    drawLine(
                        color = if (dayNightCycle == 2) Color(0xFFFFCCBC).copy(alpha = 0.7f) else Color(0xFFFFCCBC),
                        start = Offset(horseDrawX + 18f, horseDrawY - 28f),
                        end = Offset(horseDrawX + 8f, horseDrawY - 18f),
                        strokeWidth = 4f,
                        cap = StrokeCap.Round
                    )

                    val armAngle = if (frameCount % 30 < 15) 30f else -30f
                    drawLine(
                        color = if (dayNightCycle == 2) Color(0xFFFFCCBC).copy(alpha = 0.7f) else Color(0xFFFFCCBC),
                        start = Offset(horseDrawX + 38f, horseDrawY - 28f),
                        end = Offset(horseDrawX + 48f + armAngle * 0.2f, horseDrawY - 38f + armAngle * 0.2f),
                        strokeWidth = 4f,
                        cap = StrokeCap.Round
                    )

                    drawLine(
                        color = if (dayNightCycle == 2) Color(0xFF6D4C41) else Color(0xFF8D6E63),
                        start = Offset(horseDrawX + 8f, horseDrawY - 18f),
                        end = Offset(horseDrawX + 45f, horseDrawY - 15f),
                        strokeWidth = 2f
                    )

                    drawRect(
                        color = if (dayNightCycle == 2) Color(0xFF2C2C2C) else Color(0xFF3E2723),
                        topLeft = Offset(horseDrawX + 22f, horseDrawY + 5f),
                        size = Size(4f, 6f)
                    )
                    drawRect(
                        color = if (dayNightCycle == 2) Color(0xFF2C2C2C) else Color(0xFF3E2723),
                        topLeft = Offset(horseDrawX + 32f, horseDrawY + 5f),
                        size = Size(4f, 6f)
                    )

                    drawLine(
                        color = if (dayNightCycle == 2) Color(0xFFB71C1C) else Color(0xFFD32F2F),
                        start = Offset(horseDrawX + 20f, horseDrawY - 28f),
                        end = Offset(horseDrawX + 36f, horseDrawY - 28f),
                        strokeWidth = 3f,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = if (dayNightCycle == 2) Color(0xFFB71C1C) else Color(0xFFD32F2F),
                        start = Offset(horseDrawX + 36f, horseDrawY - 28f),
                        end = Offset(horseDrawX + 38f, horseDrawY - 22f),
                        strokeWidth = 2f,
                        cap = StrokeCap.Round
                    )
                }
            }

            // Game Over overlay
            if (gameState == GameState.GAME_OVER) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .clickable { /* Prevent click through */ },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "💀 Game Over!",
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Score: ${score / 2}",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            modifier = Modifier.padding(8.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = {
                                    resetGame()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text("🔄 Replay")
                            }
//                            Button(
//                                onClick = onDismiss,
//                                colors = ButtonDefaults.buttonColors(
//                                    containerColor = MaterialTheme.colorScheme.secondary
//                                )
//                            ) {
//                                Text("❌ Exit")
//                            }
                        }
                    }
                }
            }
        }

        // Controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = {
                    if (gameState == GameState.PLAYING && horseY >= groundY - horseSize) {
                        horseVelocity = jumpStrength
                    }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("🦘 Jump!", fontSize = 18.sp)
            }

            Button(
                onClick = onDismiss,
                modifier = Modifier.weight(0.5f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Text("Close") //"❌ Close"
            }
        }

        Text(
            text = "Tap JUMP to avoid obstacles!",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, start  = 2.dp)
        )
    }
}

enum class GameState {
    PLAYING,
    GAME_OVER
}

data class Obstacle(
    val x: Float,
    val height: Float,
    val width: Float
)
