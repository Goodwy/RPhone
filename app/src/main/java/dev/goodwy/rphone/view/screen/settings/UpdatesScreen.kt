package dev.goodwy.rphone.view.screen.settings

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.view.Surface
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.Downloading
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import dev.goodwy.rphone.APP_VERSION
import dev.goodwy.rphone.GITHUB_API_RELEASES
import dev.goodwy.rphone.GITHUB_API_RELEASES_LIST
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.controller.util.ReleaseInfo
import dev.goodwy.rphone.controller.util.enqueueApkDownload
import dev.goodwy.rphone.controller.util.fetchLatestRelease
import dev.goodwy.rphone.controller.util.fetchReleaseForVersion
import dev.goodwy.rphone.controller.util.getApkDestinationFile
import dev.goodwy.rphone.controller.util.isNewerVersion
import dev.goodwy.rphone.view.components.RillAnimatedSection
import dev.goodwy.rphone.view.components.RillExpressiveCard
import dev.goodwy.rphone.view.components.RillSwitchListItem
import dev.goodwy.rphone.view.components.performAppHaptic
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.goodwy.rphone.R
import dev.goodwy.rphone.cardCornerExtraSmall
import dev.goodwy.rphone.controller.util.toast
import dev.goodwy.rphone.view.components.NavigationIcon
import dev.goodwy.rphone.view.components.RillPullToRefreshIndicator
import dev.goodwy.rphone.view.components.Title
import dev.goodwy.rphone.view.theme.MyColors.cardColor
import dev.goodwy.rphone.view.theme.customColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import kotlin.time.Duration.Companion.milliseconds

// ─── State machines ────────────────────────────────────────────────────────

private sealed class CheckState {
    object Idle : CheckState()
    object Checking : CheckState()
    data class Done(val latest: ReleaseInfo?, val isNewer: Boolean) : CheckState()
    object Failed : CheckState()
}

private sealed class DownloadState {
    object Idle : DownloadState()
    data class Confirm(val release: ReleaseInfo, val readyToInstall: Boolean) : DownloadState()
    data class Downloading(val release: ReleaseInfo, val downloadId: Long, val progress: Float) : DownloadState()
    data class DownloadComplete(val release: ReleaseInfo) : DownloadState()
    object Failed : DownloadState()
}

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun UpdatesScreen(navigator: DestinationsNavigator) {
    val context = LocalContext.current
    val prefs = koinInject<PreferenceManager>()
    val scope = rememberCoroutineScope()

    var checkState by remember { mutableStateOf<CheckState>(CheckState.Idle) }
    var downloadState by remember { mutableStateOf<DownloadState>(DownloadState.Idle) }
    var installedNotes by remember { mutableStateOf<String?>(null) }
    var installedNotesLoaded by remember { mutableStateOf(false) }
    var pullToRefreshActive by remember { mutableStateOf(false) }
    var showCompareSheet by remember { mutableStateOf(false) }

    val settingsVersion by prefs.settingsChanged.collectAsState()
    var autoUpdateEnabled by remember(settingsVersion) {
        mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_AUTO_UPDATE_CHECK, true))
    }

    fun triggerHaptic() {
        if (prefs.getBoolean(PreferenceManager.KEY_APP_HAPTICS, true)) {
            performAppHaptic(
                context,
                prefs.getString(PreferenceManager.KEY_APP_HAPTICS_STRENGTH, "light") ?: "light",
                prefs.getFloat(PreferenceManager.KEY_HAPTICS_CUSTOM_INTENSITY, 0.5f)
            )
        }
    }

    fun runCheck() {
        triggerHaptic()
        scope.launch {
            checkState = CheckState.Checking
            val release = fetchLatestRelease(GITHUB_API_RELEASES)
            checkState = when {
                release == null -> CheckState.Failed
                isNewerVersion(release.tagName, APP_VERSION) -> CheckState.Done(release, true)
                else -> CheckState.Done(release, false)
            }
            pullToRefreshActive = false
        }
    }

    LaunchedEffect(Unit) {
        runCheck()
    }
    LaunchedEffect(Unit) {
        installedNotes = fetchReleaseForVersion(GITHUB_API_RELEASES_LIST, APP_VERSION)?.releaseNotes
        installedNotesLoaded = true
    }

    // ── Download confirmation + progress flow ──────────────────────────────
    when (val ds = downloadState) {
        is DownloadState.Confirm -> {
            val failedText = stringResource(R.string.open_downloads_folder_manually)
            PermissionToDownloadDialog(
                currentVersion = APP_VERSION,
                latestVersion = ds.release.tagName,
                readyToInstall = ds.readyToInstall,
                onConfirm = {
                    triggerHaptic()
                    if (ds.readyToInstall) {
//                        downloadState = DownloadState.Idle
//                        installApkAndScheduleDelete(context, getApkDestinationFile())
                        try {
                            val intent = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            context.toast(failedText)
                        }
                        downloadState = DownloadState.Idle
                    } else {
                        val url = ds.release.apkUrl
                        if (url != null) {
                            val id = enqueueApkDownload(context, url)
                            downloadState = if (id != null) DownloadState.Downloading(ds.release, id, 0f) else DownloadState.Failed
                        } else {
                            downloadState = DownloadState.Failed
                        }
                    }
                },
                onDismiss = { downloadState = DownloadState.Idle }
            )
        }
        is DownloadState.Downloading -> {
            val onCancelDownload = {
                val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                try {
                    dm.remove(ds.downloadId)
                } catch (_: Exception) {}
                downloadState = DownloadState.Idle
            }

            LaunchedEffect(ds.downloadId) {
                val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                while (true) {
                    delay(300.milliseconds)
                    val query = DownloadManager.Query().setFilterById(ds.downloadId)
                    val cursor = dm.query(query)
                    if (!cursor.moveToFirst()) { cursor.close(); break }
                    val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    val downloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    cursor.close()
                    when (status) {
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            prefs.setString(PreferenceManager.KEY_DOWNLOADED_UPDATE_VERSION, ds.release.tagName)
                            downloadState = DownloadState.DownloadComplete(ds.release)
                            break
                        }
                        DownloadManager.STATUS_FAILED -> {
                            downloadState = DownloadState.Failed
                            break
                        }
                        else -> {
                            val p = if (total > 0L) (downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
                            downloadState = ds.copy(progress = p)
                        }
                    }
                }
            }
            DownloadingDialog(
                latestVersion = ds.release.tagName,
                progress = ds.progress,
                onCancel = onCancelDownload
            )
        }
        is DownloadState.DownloadComplete -> {
            val failedText = stringResource(R.string.open_downloads_folder_manually)
            DownloadCompleteDialog(
                latestVersion = ds.release.tagName,
                onOpenDownloads = {
                    triggerHaptic()
                    try {
                        val intent = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        context.toast(failedText)
                    }
                    downloadState = DownloadState.Idle
                },
                onDismiss = { downloadState = DownloadState.Idle }
            )
        }
        is DownloadState.Failed -> {
            AlertDialog(
                onDismissRequest = { downloadState = DownloadState.Idle },
                shape = RoundedCornerShape(28.dp),
                icon = {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.customColors.colorRed.copy(alpha = 0.16f),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.customColors.colorDarkRed,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                },
                title = { Text("Download Failed", fontWeight = FontWeight.Bold) },
                text = { Text("Something went wrong while downloading the update. Please check your internet connection and try again.") },
                confirmButton = {
                    Button(
                        onClick = { downloadState = DownloadState.Idle },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("OK")
                    }
                }
            )
        }
        else -> {}
    }

    if (showCompareSheet) {
        val latestForCompare = (checkState as? CheckState.Done)?.latest
        CompareReleaseNotesSheet(
            installedVersion = APP_VERSION,
            installedNotes = installedNotes,
            latestVersion = latestForCompare?.tagName,
            latestNotes = latestForCompare?.releaseNotes,
            onDismiss = { showCompareSheet = false }
        )
    }

    val rotation =
        (context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager).defaultDisplay.rotation
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val isRotation90 = rotation == if (isLtr) Surface.ROTATION_90 else Surface.ROTATION_270
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets.systemBars.only(
                    if (isRotation90) WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                    else WindowInsetsSides.Top
                ),
                title = { Title(stringResource(R.string.updates)) },
                navigationIcon = {
                    NavigationIcon(onClick = { navigator.navigateUp() })
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = padding.calculateTopPadding(),
                    start = 0.dp,
                    end = 0.dp,
                    bottom = 0.dp
                )
                .padding(
                    start = 16.dp,
                    top = 12.dp,
                    end = 16.dp,
                    bottom = 12.dp + navBarBottom
                ),
        ) {
            val pullToRefreshState = rememberPullToRefreshState()
            PullToRefreshBox(
                isRefreshing = checkState == CheckState.Checking && pullToRefreshActive,
                onRefresh = {
                    pullToRefreshActive = true
                    runCheck()
                },
                modifier = Modifier.fillMaxSize(),
                state = pullToRefreshState,
                indicator = {
                    RillPullToRefreshIndicator(
                        state = pullToRefreshState,
                        isRefreshing = checkState == CheckState.Checking && pullToRefreshActive
                    )
                }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // ── Unified Top Card with Background Container ───────────
                    RillAnimatedSection(delayMs = 0L) {
                        RillExpressiveCard {
                            // ── Top Hero Banner (Dual Pane) ──
                            ExpressiveUpdateHeroCard(checkState = checkState)

                            // ── Version Stat Cards ──
//                            VersionStatsRow(checkState = checkState)

                            // ── Release Notes Section ────────────────────────────────
                            if (checkState != CheckState.Failed) ExpressiveReleaseNotesCard(
                                checkState = checkState,
                                installedNotes = installedNotes
                            )

                            // ── Action Buttons ──
                            ExpressiveActionArea(
                                checkState = checkState,
                                onCheckAgain = { runCheck() },
                                onUpdateClick = { latest ->
                                    triggerHaptic()
                                    val apkFile = getApkDestinationFile()
                                    val downloadedVersion = prefs.getString(
                                        PreferenceManager.KEY_DOWNLOADED_UPDATE_VERSION,
                                        null
                                    )
                                    val readyToInstall =
                                        apkFile.exists() && apkFile.length() > 0L && downloadedVersion == latest.tagName
                                    downloadState =
                                        DownloadState.Confirm(latest, readyToInstall)
                                }
                            )
                        }
                    }

                    // ── Update Preferences Card ──────────────────────────────
                    RillAnimatedSection(delayMs = 240L) {
                        Column {
//                            SettingsSectionLabel("Options")
                            RillExpressiveCard {
                                RillSwitchListItem(
                                    headline = stringResource(R.string.auto_check_for_updates),
                                    supporting = stringResource(R.string.auto_check_for_updates_subtitle),
                                    leadingIcon = Icons.Rounded.Autorenew,
                                    checked = autoUpdateEnabled,
                                    onCheckedChange = { enabled ->
                                        autoUpdateEnabled = enabled
                                        prefs.setBoolean(PreferenceManager.KEY_AUTO_UPDATE_CHECK, enabled)
                                    }
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

// ─── Expressive Hero Status Card (Dual Pane Layout) ───────────────────────

@Composable
private fun ExpressiveUpdateHeroCard(checkState: CheckState) {
    val containerColor = when (checkState) {
        is CheckState.Done -> if (checkState.isNewer) MaterialTheme.colorScheme.primaryContainer
        else cardColor

        is CheckState.Failed -> MaterialTheme.colorScheme.errorContainer
        else -> cardColor
    }

    val (titleColor, subtitleColor, tagColor) = when (checkState) {
        is CheckState.Done -> if (checkState.isNewer) {
            Triple(
                MaterialTheme.colorScheme.onPrimaryContainer,
                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                MaterialTheme.colorScheme.primary
            )
        } else {
            Triple(
                MaterialTheme.colorScheme.onSecondaryContainer,
                MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f),
                MaterialTheme.colorScheme.secondary
            )
        }

        is CheckState.Failed -> Triple(
            MaterialTheme.colorScheme.onErrorContainer,
            MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f),
            MaterialTheme.colorScheme.error
        )

        else -> Triple(
            MaterialTheme.colorScheme.onPrimaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
            MaterialTheme.colorScheme.primary
        )
    }

    val animatedContainerColor by animateColorAsState(
        targetValue = containerColor,
        animationSpec = tween(400),
        label = "heroContainerColor"
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(cardCornerExtraSmall),
        color = animatedContainerColor,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Left Pane: Hero Status Icon & Badge ──
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HeroStatusIcon(checkState = checkState)
            }

            // ── Right Pane: App Name, Headline & Description ──
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
//                Text(
//                    text = "Rill Phone",
//                    style = MaterialTheme.typography.labelSmall,
//                    color = tagColor,
//                    fontWeight = FontWeight.Bold,
//                    letterSpacing = 1.2.sp
//                )

                AnimatedContent(
                    targetState = checkState,
                    transitionSpec = {
                        (fadeIn(tween(250)) + slideInVertically { it / 2 }) togetherWith
                            (fadeOut(tween(150)) + slideOutVertically { -it / 2 })
                    },
                    label = "heroTitle"
                ) { state ->
                    val (title, subtitle) = when (state) {
                        is CheckState.Checking -> stringResource(R.string.checking_for_updates) to null //"Connecting to release repository"
                        is CheckState.Done -> if (state.isNewer && state.latest != null) {
                            stringResource(R.string.update_available) to stringResource(R.string.update_available_subtitle, state.latest.tagName)
                        } else {
                            stringResource(R.string.up_to_date) to stringResource(R.string.up_to_date_subtitle, APP_VERSION)
                        }
                        is CheckState.Failed -> stringResource(R.string.check_failed) to stringResource(R.string.check_failed_subtitle)
                        else -> "Rill Phone Updates" to "Current installed build v$APP_VERSION"
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            lineHeight = MaterialTheme.typography.titleMedium.lineHeight,
                            color = titleColor
                        )
                        if (subtitle != null) Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = subtitleColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroStatusIcon(checkState: CheckState) {
    val infinite = rememberInfiniteTransition(label = "heroIconAnim")
    val spin by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
        label = "heroSpin"
    )
    val pulse by infinite.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "heroPulse"
    )

    val (icon, iconBgColor, iconTintColor) = when {
        checkState is CheckState.Checking -> Triple(
            Icons.Rounded.Refresh,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
            MaterialTheme.colorScheme.primary
        )
        checkState is CheckState.Done -> if (checkState.isNewer) {
            Triple(
                Icons.Default.NewReleases,
                MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                MaterialTheme.colorScheme.primary
            )
        } else {
            Triple(
                Icons.Default.Verified,
                MaterialTheme.colorScheme.secondary.copy(alpha = 0.20f),
                MaterialTheme.colorScheme.secondary
            )
        }
        checkState is CheckState.Failed -> Triple(
            Icons.Default.CloudOff,
            MaterialTheme.colorScheme.error.copy(alpha = 0.18f),
            MaterialTheme.colorScheme.error
        )
        else -> Triple(
            Icons.Rounded.SystemUpdate,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
            MaterialTheme.colorScheme.primary
        )
    }

    Surface(
        shape = CircleShape,
        color = iconBgColor,
        modifier = Modifier
            .size(64.dp)
            .scale(if (checkState is CheckState.Done && checkState.isNewer) pulse else 1f),
        shadowElevation = 0.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            AnimatedContent(
                targetState = checkState,
                transitionSpec = {
                    (fadeIn(tween(250)) + scaleIn(initialScale = 0.6f)) togetherWith
                        (fadeOut(tween(150)) + scaleOut(targetScale = 0.6f))
                },
                label = "heroIconGlyph"
            ) { state ->
                val modifier = if (state is CheckState.Checking) Modifier.rotate(spin) else Modifier
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTintColor,
                    modifier = modifier.size(30.dp)
                )
            }
        }
    }
}

// ─── Expressive Action Button Area ─────────────────────────────────────────

@Composable
private fun ExpressiveActionArea(
    checkState: CheckState,
    onCheckAgain: () -> Unit,
    onUpdateClick: (ReleaseInfo) -> Unit
) {
    AnimatedContent(
        targetState = checkState,
        transitionSpec = {
            (fadeIn(tween(250)) + slideInVertically { it / 3 }) togetherWith
                (fadeOut(tween(140)) + slideOutVertically { -it / 3 })
        },
        label = "actionArea"
    ) { state ->
        when {
            state is CheckState.Done && state.isNewer && state.latest != null -> {
                Button(
                    onClick = { onUpdateClick(state.latest) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(cardCornerExtraSmall),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        stringResource(R.string.download) + " v${state.latest.tagName}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            state is CheckState.Failed -> {
                FilledTonalButton(
                    onClick = onCheckAgain,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(cardCornerExtraSmall),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.try_again), fontWeight = FontWeight.Bold)
                }
            }

            state is CheckState.Checking || state == CheckState.Idle -> {
                FilledTonalButton(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(cardCornerExtraSmall),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.5.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.checking_for_updates), fontWeight = FontWeight.Medium)
                }
            }

            else -> {
                FilledTonalButton(
                    onClick = onCheckAgain,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(cardCornerExtraSmall),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.check_for_updates), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ─── Expressive Release Notes Card (Dual Pane Layout) ─────────────────────

@Composable
private fun ExpressiveReleaseNotesCard(
    checkState: CheckState,
    installedNotes: String?
) {
    Surface(
        shape = RoundedCornerShape(cardCornerExtraSmall),
        color = cardColor,
        modifier = Modifier.fillMaxWidth(),
        shadowElevation = 0.dp,
    ) {
        AnimatedContent(
            targetState = checkState,
            transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) },
            label = "notesAnimatedCard"
        ) { state ->
            when (state) {
                CheckState.Idle, is CheckState.Checking -> {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        NotesShimmerPlaceholder()
                    }
                }

                is CheckState.Done -> {
                    if (state.isNewer && state.latest != null) {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // ── Dual Pane Header ──
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Left Pane: Icon Badge + Version Title + Tag
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Default.AutoAwesome,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                    Column {
                                        Text(
                                            "v${state.latest.tagName}",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            stringResource(R.string.latest),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }

                                // Right Pane: Date Badge
                                if (state.latest.publishedAt != null) {
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = MaterialTheme.colorScheme.surfaceContainerHighest
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(
                                                horizontal = 10.dp,
                                                vertical = 5.dp
                                            ),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                Icons.Outlined.CalendarToday,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Text(
                                                text = state.latest.publishedAt.substringBefore(
                                                    "T"
                                                ),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }

                            // Inset content container
                            Surface(
                                shape = MaterialTheme.shapes.large,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(modifier = Modifier.padding(14.dp)) {
                                    SelectionContainer {
                                        ReleaseNotesText(state.latest.releaseNotes)
                                    }
                                }
                            }
                        }
                    } else {
                        // Up to date (Dual Pane Layout)
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // ── Dual Pane Header ──
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Left Pane: Icon Badge + Version Title + Tag
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Outlined.Info,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                    Column {
                                        Text(
                                            "v$APP_VERSION",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            stringResource(R.string.installed),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }

                                // Right Pane: Date Badge
                                if (installedNotes != null && state.latest?.publishedAt != null) {
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = MaterialTheme.colorScheme.surfaceContainerHighest
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(
                                                horizontal = 10.dp,
                                                vertical = 5.dp
                                            ),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                Icons.Outlined.CalendarToday,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Text(
                                                text = state.latest.publishedAt.substringBefore(
                                                    "T"
                                                ),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }

                            // Inset content container
                            Surface(
                                shape = MaterialTheme.shapes.large,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(modifier = Modifier.padding(14.dp)) {
                                    SelectionContainer {
                                        ReleaseNotesText(installedNotes)
                                    }
                                }
                            }
                        }
                    }
                }

                is CheckState.Failed -> {
                    // Failed (Dual Pane Layout)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Left Pane: Error Icon Surface
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.18f),
                            modifier = Modifier.size(56.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Rounded.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                        }

                        // Right Pane: Error Info
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                stringResource(R.string.couldnt_check_for_updates),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                lineHeight = MaterialTheme.typography.titleSmall.lineHeight,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                stringResource(R.string.couldnt_check_for_updates_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotesShimmerPlaceholder() {
    val infinite = rememberInfiniteTransition(label = "shimmer")
    val shimmerX by infinite.animateFloat(
        initialValue = -1f, targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)),
        label = "shimmerX"
    )
    val base = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    val highlight = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(0.7f, 1f, 0.88f, 0.65f).forEach { widthFraction ->
            Box(
                modifier = Modifier
                    .fillMaxWidth(widthFraction)
                    .height(14.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(base, highlight, base),
                            start = Offset(shimmerX * 300f, 0f),
                            end = Offset(shimmerX * 300f + 300f, 0f)
                        )
                    )
            )
        }
    }
}

/** Rich GitHub Markdown renderer supporting code blocks, headers, quotes, lists, bold, italic, code, links. */
@Composable
private fun ReleaseNotesText(rawNotes: String?) {
    if (rawNotes.isNullOrBlank()) {
        Text(
            stringResource(R.string.no_release_notes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
    val codeBgColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)

    val lines = rawNotes.lines()
    var inCodeBlock = false
    val codeBlockLines = mutableListOf<String>()

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (rawLine in lines) {
            val trimmed = rawLine.trim()

            // Handle code block fences
            if (trimmed.startsWith("```")) {
                if (inCodeBlock) {
                    val codeContent = codeBlockLines.joinToString("\n")
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = codeContent,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = onSurfaceColor,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                    codeBlockLines.clear()
                    inCodeBlock = false
                } else {
                    inCodeBlock = true
                }
                continue
            }

            if (inCodeBlock) {
                codeBlockLines.add(rawLine)
                continue
            }

            when {
                trimmed.isBlank() -> Spacer(Modifier.height(4.dp))

                trimmed.startsWith("#### ") -> {
                    Text(
                        text = parseMarkdownInline(trimmed.removePrefix("#### ").trim(), primaryColor, onSurfaceColor, codeBgColor),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = onSurfaceVariantColor,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                trimmed.startsWith("### ") -> {
                    Text(
                        text = parseMarkdownInline(trimmed.removePrefix("### ").trim(), primaryColor, onSurfaceColor, codeBgColor),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = onSurfaceColor,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                trimmed.startsWith("## ") -> {
                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        Text(
                            text = parseMarkdownInline(trimmed.removePrefix("## ").trim(), primaryColor, onSurfaceColor, codeBgColor),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = primaryColor
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(top = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                        )
                    }
                }
                trimmed.startsWith("# ") -> {
                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        Text(
                            text = parseMarkdownInline(trimmed.removePrefix("# ").trim(), primaryColor, onSurfaceColor, codeBgColor),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = primaryColor
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(top = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                        )
                    }
                }

                trimmed.startsWith("> ") || trimmed.startsWith(">") -> {
                    val quoteText = trimmed.removePrefix(">").trim()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(2.dp),
                            color = primaryColor.copy(alpha = 0.7f),
                            modifier = Modifier
                                .width(3.5.dp)
                                .height(22.dp)
                        ) {}
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = parseMarkdownInline(quoteText, primaryColor, onSurfaceVariantColor, codeBgColor),
                            style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                            color = onSurfaceVariantColor
                        )
                    }
                }

                trimmed == "---" || trimmed == "***" || trimmed == "___" -> {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )
                }

                trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ") -> {
                    val content = trimmed.substring(2).trim()
                    val leadingSpaces = rawLine.takeWhile { it == ' ' }.length
                    val indent = (leadingSpaces / 2 * 12).dp
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = indent),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = primaryColor,
                            modifier = Modifier
                                .padding(top = 7.dp)
                                .size(5.dp)
                        ) {}
                        Text(
                            text = parseMarkdownInline(content, primaryColor, onSurfaceColor, codeBgColor),
                            style = MaterialTheme.typography.bodyMedium,
                            color = onSurfaceColor
                        )
                    }
                }

                trimmed.matches(Regex("""^\d+\.\s+.*""")) -> {
                    val numPrefix = trimmed.substringBefore(".") + "."
                    val content = trimmed.substringAfter(".").trim()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = numPrefix,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = primaryColor
                        )
                        Text(
                            text = parseMarkdownInline(content, primaryColor, onSurfaceColor, codeBgColor),
                            style = MaterialTheme.typography.bodyMedium,
                            color = onSurfaceColor
                        )
                    }
                }

                else -> {
                    Text(
                        text = parseMarkdownInline(trimmed, primaryColor, onSurfaceColor, codeBgColor),
                        style = MaterialTheme.typography.bodyMedium,
                        color = onSurfaceColor
                    )
                }
            }
        }

        if (inCodeBlock && codeBlockLines.isNotEmpty()) {
            val codeContent = codeBlockLines.joinToString("\n")
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = codeContent,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = onSurfaceColor,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}

/** Parses GitHub inline markdown into an AnnotatedString with formatting spans. */
private fun parseMarkdownInline(
    text: String,
    primaryColor: Color,
    onSurfaceColor: Color,
    codeBgColor: Color
): AnnotatedString {
    return buildAnnotatedString {
        val pattern = Regex("""(\[(.*?)\]\((.*?)\)|`([^`]+)`|\*\*\*([^*]+)\*\*\*|\*\*([^*]+)\*\*|__([^_]+)__|(?<!\*)\*([^*]+)\*(?!\*)|(?<!_)_([^_]+)_(?!_)|~~([^~]+)~~|(#[0-9]+|@[a-zA-Z0-9_/-]+))""")
        var currentIndex = 0
        val matches = pattern.findAll(text)

        for (match in matches) {
            val start = match.range.first
            val end = match.range.last + 1

            if (start > currentIndex) {
                append(text.substring(currentIndex, start))
            }

            val fullMatch = match.value
            when {
                fullMatch.startsWith("[") && fullMatch.contains("](") -> {
                    val label = match.groupValues[2]
                    pushStyle(SpanStyle(color = primaryColor, textDecoration = TextDecoration.Underline, fontWeight = FontWeight.Medium))
                    append(label)
                    pop()
                }
                fullMatch.startsWith("`") && fullMatch.endsWith("`") -> {
                    val codeText = match.groupValues[4]
                    pushStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = codeBgColor,
                            color = primaryColor,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    append(" $codeText ")
                    pop()
                }
                fullMatch.startsWith("***") && fullMatch.endsWith("***") -> {
                    val content = match.groupValues[5]
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic))
                    append(content)
                    pop()
                }
                (fullMatch.startsWith("**") && fullMatch.endsWith("**")) || (fullMatch.startsWith("__") && fullMatch.endsWith("__")) -> {
                    val content = match.groupValues[6].ifEmpty { match.groupValues[7] }
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    append(content)
                    pop()
                }
                (fullMatch.startsWith("*") && fullMatch.endsWith("*")) || (fullMatch.startsWith("_") && fullMatch.endsWith("_")) -> {
                    val content = match.groupValues[8].ifEmpty { match.groupValues[9] }
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                    append(content)
                    pop()
                }
                fullMatch.startsWith("~~") && fullMatch.endsWith("~~") -> {
                    val content = match.groupValues[10]
                    pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
                    append(content)
                    pop()
                }
                fullMatch.startsWith("#") || fullMatch.startsWith("@") -> {
                    pushStyle(SpanStyle(color = primaryColor, fontWeight = FontWeight.SemiBold))
                    append(fullMatch)
                    pop()
                }
                else -> {
                    append(fullMatch)
                }
            }
            currentIndex = end
        }

        if (currentIndex < text.length) {
            append(text.substring(currentIndex))
        }
    }
}

// ─── Permission-to-download confirmation dialog ────────────────────────────

@Composable
private fun PermissionToDownloadDialog(
    currentVersion: String,
    latestVersion: String,
    readyToInstall: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        icon = {
            val iconColor = if (readyToInstall) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = iconColor.copy(alpha = 0.16f),
                modifier = Modifier.size(52.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (readyToInstall) Icons.Default.InstallMobile else Icons.Default.Download,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        },
        title = {
            Text(
                if (readyToInstall) stringResource(R.string.install_update) else stringResource(R.string.download_update),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.wrapContentWidth(), textAlign = TextAlign.Center
            )
        },
        text = {
            Text(
                if (readyToInstall)
                    stringResource(R.string.install_update_subtitle, latestVersion)
                else
                    stringResource(R.string.download_update_subtitle, latestVersion, currentVersion),
                modifier = Modifier.wrapContentWidth(), textAlign = TextAlign.Center
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(if (readyToInstall) stringResource(R.string.open) else stringResource(R.string.download))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(stringResource(R.string.not_now))
            }
        }
    )
}

// ─── Download complete dialog ───────────────────────────────────────────────

@Composable
private fun DownloadCompleteDialog(
    latestVersion: String,
    onOpenDownloads: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        icon = {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                modifier = Modifier.size(52.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        },
        title = {
            Text(
                stringResource(R.string.download_complete),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(stringResource(R.string.download_complete_subtitle, latestVersion))
        },
        confirmButton = {
            Button(
                onClick = onOpenDownloads,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(stringResource(R.string.open))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(stringResource(R.string.not_now))
            }
        }
    )
}

@Composable
private fun DownloadingDialog(
    latestVersion: String,
    progress: Float,
    onCancel: () -> Unit
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(250),
        label = "dlProgress"
    )

    AlertDialog(
        onDismissRequest = {},
        properties = androidx.compose.ui.window.DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        ),
        shape = RoundedCornerShape(28.dp),
        icon = {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.customColors.colorIndigo,
                modifier = Modifier.size(52.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.Downloading,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.customColors.colorDarkIndigo,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        },
        title = { Text(stringResource(R.string.downloading_update, latestVersion), fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(50)),
                    strokeCap = StrokeCap.Round,
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "${(animatedProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        stringResource(R.string.please_wait),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onCancel,
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(stringResource(R.string.cancel))
            }
        },
        confirmButton = {}
    )
}

// ─── Compare release notes bottom sheet ─────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompareReleaseNotesSheet(
    installedVersion: String,
    installedNotes: String?,
    latestVersion: String?,
    latestNotes: String?,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Installed, 1 = Latest

    fun closeWithAnimation() {
        scope.launch {
            sheetState.hide()
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Compare Release Notes",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                IconButton(
                    onClick = { closeWithAnimation() },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            val hasLatest = latestVersion != null
            if (hasLatest) {
                ExpressiveSegmentedTab(
                    options = listOf("Installed · v$installedVersion", "Latest · v$latestVersion"),
                    selectedIndex = selectedTab,
                    onSelect = { selectedTab = it }
                )
            } else {
                Text(
                    "Showing release notes for your installed version.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedContent(
                targetState = if (hasLatest) selectedTab else 0,
                transitionSpec = {
                    if (targetState > initialState) {
                        (slideInVertically { it / 4 } + fadeIn(tween(200))) togetherWith
                            (slideOutVertically { -it / 4 } + fadeOut(tween(150)))
                    } else {
                        (slideInVertically { -it / 4 } + fadeIn(tween(200))) togetherWith
                            (slideOutVertically { it / 4 } + fadeOut(tween(150)))
                    }
                },
                label = "compareContent"
            ) { tab ->
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .heightIn(min = 160.dp, max = 380.dp)
                            .padding(18.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        SelectionContainer {
                            ReleaseNotesText(if (tab == 0) installedNotes else latestNotes)
                        }
                    }
                }
            }

            Button(
                onClick = { closeWithAnimation() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Close", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ExpressiveSegmentedTab(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
            .padding(4.dp)
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            val bg by animateColorAsState(
                targetValue = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                animationSpec = tween(220),
                label = "tabBg"
            )
            val fg by animateColorAsState(
                targetValue = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                animationSpec = tween(220),
                label = "tabFg"
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(bg)
                    .clickable { onSelect(index) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = fg,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }
    }
}
