package dev.goodwy.rphone.view.screen.settings

import android.app.Activity
import android.content.Context
import android.text.Html
import android.view.Gravity
import android.view.Surface
import android.widget.TextView
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.controller.util.toast
import dev.goodwy.rphone.view.components.NavigationIcon
import dev.goodwy.rphone.view.components.RillAnimatedSection
import dev.goodwy.rphone.view.components.performAppHaptic
import dev.goodwy.rphone.view.screen.FloatingParticles
import dev.goodwy.rphone.view.theme.customColors
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.goodwy.rphone.BuildConfig
import dev.goodwy.rphone.controller.PlayStoreViewModel
import dev.goodwy.rphone.controller.PurchaseHelper
import dev.goodwy.rphone.controller.Tipping
import dev.goodwy.rphone.controller.util.HtmlTextView
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinActivityViewModel
import kotlin.math.cos
import kotlin.math.sin

data class Donate(
    val headline: String,
    val supporting: String? = null,
    val trailing: String? = null,
    val label: String? = null,
    val productId: String,
    val isSubscription: Boolean,
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
    val themeMode      = prefs.getString(PreferenceManager.KEY_THEME_MODE, "auto") ?: "auto"
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        "light", "white"  -> false
        "dark",  "black"  -> true
        "auto_bw"         -> systemDark
        else              -> systemDark
    }

    val purchaseHelper: PurchaseHelper = koinInject()

    val purchaseSuccess by purchaseHelper.purchaseSuccess.collectAsState()
    val isLoading by purchaseHelper.isLoading.collectAsState()
    val errorMessage by purchaseHelper.errorMessage.collectAsState()
    val isBillingReady by purchaseHelper.isBillingReady.collectAsState()
    val iapSkuDetailsInitialized by purchaseHelper.iapSkuDetailsInitialized.collectAsState()
    val subSkuDetailsInitialized by purchaseHelper.subSkuDetailsInitialized.collectAsState()
//    val isPro by playStoreViewModel.isPro.collectAsState()

    // Initialization
    LaunchedEffect(Unit) {
        purchaseHelper.initBilling()
        (purchaseHelper as? PlayStoreViewModel)?.retrieveDonation(
            iaps = listOf(
                BuildConfig.PRODUCT_ID_X1
            ),
            subs = listOf(
                BuildConfig.SUBSCRIPTION_ID_X1,
                BuildConfig.SUBSCRIPTION_YEAR_ID_X1,
            )
        )
    }

    // We display a Toast notification only when the user has JUST made a purchase
    val thankText = stringResource(R.string.thank_you_for_your_support)
    LaunchedEffect(purchaseSuccess) {
        if (purchaseSuccess) {
            context.toast(thankText)
            purchaseHelper.checkProStatus()
            // Clear the flag so that the Toast doesn't appear again
            purchaseHelper.clearPurchaseSuccess()
        }
    }

    // Error Handling
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            context.toast(it)
            purchaseHelper.clearErrors()
        }
    }

    var currentDonate by remember { mutableIntStateOf(1) }

    // We do NOT use `derivedStateOf`; we simply create the list directly
    // But we use the key to force recomposition when loading data
    val updateKey by remember {
        derivedStateOf {
            Pair(iapSkuDetailsInitialized, subSkuDetailsInitialized)
        }
    }

    // We force an update of `currentDonate` when data is loaded
    LaunchedEffect(updateKey) {
        // We simply update `currentDonate` to trigger a recompose
        currentDonate = currentDonate
    }

    val donates = listOf(
        Donate(
            headline = stringResource(R.string.monthly),
            supporting = stringResource(R.string.monthly_description),
            trailing = if (subSkuDetailsInitialized) {
                purchaseHelper.getPriceSubscription(BuildConfig.SUBSCRIPTION_ID_X1)
            } else {
                stringResource(R.string.loading_price)
            },
            label = stringResource(R.string.most_flexible),
            productId = BuildConfig.SUBSCRIPTION_ID_X1,
            isSubscription = true
        ),
        Donate(
            headline = stringResource(R.string.yearly),
            supporting = stringResource(R.string.yearly_description),
            trailing = if (subSkuDetailsInitialized) {
                purchaseHelper.getPriceSubscription(BuildConfig.SUBSCRIPTION_YEAR_ID_X1)
            } else {
                stringResource(R.string.loading_price)
            },
            label = stringResource(R.string.bast_value),
            productId = BuildConfig.SUBSCRIPTION_YEAR_ID_X1,
            isSubscription = true
        ),
        Donate(
            headline = stringResource(R.string.lifetime),
            supporting = stringResource(R.string.lifetime_description),
            trailing = if (iapSkuDetailsInitialized) {
                purchaseHelper.getPriceDonation(BuildConfig.PRODUCT_ID_X1)
            } else {
                stringResource(R.string.loading_price)
            },
            label = stringResource(R.string.pay_once),
            productId = BuildConfig.PRODUCT_ID_X1,
            isSubscription = false
        )
    )

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
                    TextButton(
                        onClick = {
                            purchaseHelper.refreshAllData()
                        },
                        enabled = !isLoading
                    ) { Text(stringResource(R.string.restore_purchase)) }
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
            // Scrollable content
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
                    stringResource(R.string.project_support),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.alpha(alpha)
                )
                Spacer(modifier = Modifier.height(8.dp))
//            Text(
//                stringResource(R.string.project_support_summary),
//                color = MaterialTheme.colorScheme.onSurfaceVariant,
//                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight,
//                textAlign = TextAlign.Center,
//                modifier = Modifier.alpha(alpha)
//            )
                HtmlTextView(
                    html = stringResource(R.string.project_support_description),
                    modifier = Modifier.alpha(alpha)
                )

                Spacer(modifier = Modifier.height(24.dp))

                RillAnimatedSection(delayMs = 60L) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        donates.forEachIndexed { index, donate ->
                            DonateItem(
                                headline = donate.headline,
                                supporting = donate.supporting,
                                trailing = donate.trailing,
                                label = donate.label,
                                selected = index == currentDonate,
                                darkTheme = darkTheme,
                                isPurchased = if (donate.isSubscription) {
                                    purchaseHelper.isSubPurchased(donate.productId)
                                } else {
                                    purchaseHelper.isIapPurchased(donate.productId)
                                },
                                onClick = { currentDonate = index },
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
                        onClick = {
                            if (isBillingReady) {
                                val selectedDonate = donates[currentDonate]
                                if (selectedDonate.isSubscription) {
                                    purchaseHelper.purchaseSubscription(
                                        selectedDonate.productId,
                                        context as Activity
                                    )
                                } else {
                                    purchaseHelper.purchaseDonation(
                                        selectedDonate.productId,
                                        context as Activity
                                    )
                                }
                            } else {
                                context.toast("Billing service is not ready")
                            }
                        },
                        interactionSource = interactionSource,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        shape = RoundedCornerShape(cornerRadius),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.onSurface,
                            contentColor = MaterialTheme.colorScheme.surface
                        ),
                        contentPadding = PaddingValues(24.dp),
                        enabled = !isLoading && isBillingReady
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.surface
                            )
                        } else {
                            Text(
                                stringResource(R.string.continue_support),
                                style = MaterialTheme.typography.titleLarge,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DonateItem(
    headline: String,
    supporting: String? = null,
    trailing: String? = null,
    label: String? = null,
    selected: Boolean = false,
    darkTheme: Boolean,
    isPurchased: Boolean = false,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = koinInject<PreferenceManager>()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "ListItemScale"
    )

    val border = when {
        isPurchased -> BorderStroke(2.2.dp, MaterialTheme.colorScheme.customColors.colorDarkGreen.copy(alpha = 0.4f))
        selected -> if (darkTheme) {
            BorderStroke(2.2.dp, MaterialTheme.colorScheme.customColors.colorPurple)
        } else {
            BorderStroke(2.2.dp, MaterialTheme.colorScheme.customColors.colorDarkPurple.copy(0.4f))
        }
        else -> BorderStroke(2.2.dp, MaterialTheme.colorScheme.onSurface.copy(0.2f))
    }
    val color = when {
        isPurchased -> MaterialTheme.colorScheme.customColors.colorGreen.copy(alpha = 0.1f)
        selected -> MaterialTheme.colorScheme.customColors.colorPurple.copy(if (darkTheme) 0.1f else 0.2f)
        else -> Color.Transparent
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = color,
            shape = RoundedCornerShape(26.dp),
            modifier = Modifier
                .fillMaxWidth()
                .scale(scale),
            shadowElevation = 0.dp,
            border = border
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        interactionSource = interactionSource,
                        indication = ripple(),
                        onClick = {
                            if (!isPurchased) {
                                if (prefs.getBoolean(PreferenceManager.KEY_APP_HAPTICS, true)) {
                                    performAppHaptic(
                                        context,
                                        prefs.getString(
                                            PreferenceManager.KEY_APP_HAPTICS_STRENGTH,
                                            "light"
                                        ) ?: "light",
                                        prefs.getFloat(
                                            PreferenceManager.KEY_HAPTICS_CUSTOM_INTENSITY,
                                            0.5f
                                        )
                                    )
                                }
                                onClick()
                            }
                        },
                        enabled = !isPurchased
                    )
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = headline,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (supporting != null) {
                        Text(
                            text = supporting,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
//                        maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (trailing != null) {
                    Spacer(modifier = Modifier.width(24.dp))
                    Text(
                        text = if (isPurchased) "✓ Purchased" else trailing,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isPurchased) MaterialTheme.colorScheme.customColors.colorDarkGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        if (label != null && selected) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = 20.dp, y = (-10).dp)
                    .background(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFFF49154),
                                Color(0xFFED6D93)
                            )
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            )
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