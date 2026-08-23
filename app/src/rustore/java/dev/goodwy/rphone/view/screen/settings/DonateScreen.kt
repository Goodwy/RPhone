package dev.goodwy.rphone.view.screen.settings

import android.app.Activity
import android.content.Context
import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.goodwy.rphone.BuildConfig
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.PurchaseHelper
import dev.goodwy.rphone.controller.RuStoreViewModel
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
import dev.goodwy.rphone.controller.util.HtmlTextView
import org.koin.compose.koinInject

data class RuStoreDonate(
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
    val activity = context as? ComponentActivity

    val purchaseHelper: PurchaseHelper = koinInject()
    val viewModel = purchaseHelper as? RuStoreViewModel

    val purchaseSuccess by purchaseHelper.purchaseSuccess.collectAsStateWithLifecycle()
    val isLoading by purchaseHelper.isLoading.collectAsStateWithLifecycle()
    val errorMessage by purchaseHelper.errorMessage.collectAsStateWithLifecycle()
    val isBillingReady by purchaseHelper.isBillingReady.collectAsStateWithLifecycle()
    val iapSkuDetailsInitialized by purchaseHelper.iapSkuDetailsInitialized.collectAsStateWithLifecycle()
    val subSkuDetailsInitialized by purchaseHelper.subSkuDetailsInitialized.collectAsStateWithLifecycle()

    // Initialisation
    LaunchedEffect(activity) {
        activity?.let {
            viewModel?.setCurrentActivity(it) // This will trigger the entire chain within the ViewModel
            purchaseHelper.checkProStatus()
        }
    }

    // Resets `isLoading` if the user has returned from the RuStore login screen
    DisposableEffect(Unit) {
        val listener = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                if (isLoading) {
                    purchaseHelper.clearErrors()
                }
            }
        }
        val lifecycleOwner = activity as? androidx.lifecycle.LifecycleOwner
        lifecycleOwner?.lifecycle?.addObserver(listener)
        onDispose { lifecycleOwner?.lifecycle?.removeObserver(listener) }
    }

    val thankText = stringResource(R.string.thank_you_for_your_support)
    LaunchedEffect(purchaseSuccess) {
        if (purchaseSuccess) {
            context.toast(thankText)
            purchaseHelper.checkProStatus()
            purchaseHelper.clearPurchaseSuccess()
        }
    }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            context.toast(it)
            purchaseHelper.clearErrors()
        }
    }

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
    val themeMode = prefs.getString(PreferenceManager.KEY_THEME_MODE, "auto") ?: "auto"
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        "light", "white" -> false
        "dark", "black" -> true
        "auto_bw" -> systemDark
        else -> systemDark
    }

    var currentDonate by remember { mutableIntStateOf(1) }

    val donates = listOf(
        RuStoreDonate(
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
        RuStoreDonate(
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
        RuStoreDonate(
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

    val rotation = (context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager)
        .defaultDisplay.rotation
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val isRotation90 = rotation == if (isLtr) Surface.ROTATION_90 else Surface.ROTATION_270

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets.systemBars.only(
                    if (isRotation90) WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                    else WindowInsetsSides.Top
                ),
                title = { },
                navigationIcon = {
                    NavigationIcon(onClick = { navigator.navigateUp() })
                },
                actions = {
                    TextButton(
                        onClick = {
                            purchaseHelper.restorePurchases()
                        },
                        enabled = !isLoading
                    ) { Text(stringResource(R.string.restore_purchase)) }
                    Spacer(modifier = Modifier.size(4.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
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
                    .padding(bottom = 100.dp),
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

//                Text(
//                    text = stringResource(R.string.project_support_description),
//                    color = MaterialTheme.colorScheme.onSurfaceVariant,
//                    lineHeight = MaterialTheme.typography.bodyMedium.lineHeight,
//                    textAlign = TextAlign.Center,
//                    modifier = Modifier.alpha(alpha)
//                )
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
                            RuStoreDonateItem(
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
fun RuStoreDonateItem(
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