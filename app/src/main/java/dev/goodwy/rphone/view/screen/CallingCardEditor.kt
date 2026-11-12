package dev.goodwy.rphone.view.screen

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.ViewCompat
import coil.compose.AsyncImage
import dev.goodwy.rphone.liquidglass.backdrops.rememberLayerBackdrop
import dev.goodwy.rphone.R
import dev.goodwy.rphone.cardCornerExtraSmall
import dev.goodwy.rphone.cardSpacedBy
import dev.goodwy.rphone.controller.util.CallingCardFont
import dev.goodwy.rphone.controller.util.CallingCardPhotoSupport
import dev.goodwy.rphone.controller.util.CallingCardStyle
import dev.goodwy.rphone.controller.util.CallingCardStyleCodec
import dev.goodwy.rphone.controller.util.darken
import dev.goodwy.rphone.view.components.ColorSwatch
import dev.goodwy.rphone.view.components.NavigationIcon
import dev.goodwy.rphone.view.components.Title
import dev.goodwy.rphone.view.theme.LocalIsDarkTheme
import dev.goodwy.rphone.view.theme.MyColors.dialpadKeyColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Draft produced by the two-step calling card editor.
 *
 * A null [style] means "remove this customization and fall back to the app
 * default". The caller decides when that draft is persisted.
 */
data class CallingCardEditorResult(
    val backgroundUri: String?,
    val style: CallingCardStyle?
)

/**
 * Full-screen, two-step editor.
 *
 * Step 1 picks the portrait card photo. Step 2 previews the real call screen
 * while the user changes the font and name colour. Nothing is written to
 * storage here: [onDone] returns a draft.
 */
@Composable
fun CallingCardEditorDialog(
    contactName: String,
    phoneNumber: String,
    contactPhotoUri: String?,
    initialBackgroundUri: String?,
    inheritedBackgroundUri: String? = null,
    initialStyle: CallingCardStyle,
    fallbackStyle: CallingCardStyle,
    onDismiss: () -> Unit,
    onDone: (CallingCardEditorResult) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var stage by rememberSaveable { mutableIntStateOf(0) }
    var backgroundUri by rememberSaveable(initialBackgroundUri) {
        mutableStateOf(initialBackgroundUri)
    }
    var style by rememberSaveable(stateSaver = CallingCardStyleSaver) {
        mutableStateOf(initialStyle)
    }
    var usingDefault by rememberSaveable { mutableStateOf(false) }
    var pendingCameraUri by rememberSaveable { mutableStateOf<String?>(null) }
    var googlePhotosNotInstalled by remember { mutableStateOf(false) }

    // The inherited global background is shown for context, but it is not a
    // draft value: Done still returns only what the user picked here.
    val previewBackgroundUri = backgroundUri ?: inheritedBackgroundUri

    fun showMessage(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    val callingCardImageUnreadable = stringResource(R.string.calling_card_image_unreadable)
    fun acceptPickedUri(uri: Uri?) {
        if (uri == null) return
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        scope.launch {
            val readable = withContext(Dispatchers.IO) {
                CallingCardPhotoSupport.isReadable(context, uri)
            }
            if (readable) {
                CallingCardPhotoSupport.deleteIfCapture(context, backgroundUri)
                backgroundUri = uri.toString()
            } else {
                showMessage(callingCardImageUnreadable)
            }
        }
    }

    val galleryPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> acceptPickedUri(uri) }

    val googlePhotosLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result -> acceptPickedUri(result.data?.data) }

    val callingCardCameraFailed = stringResource(R.string.calling_card_camera_failed)
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val target = pendingCameraUri
        pendingCameraUri = null
        if (!success || target == null) {
            CallingCardPhotoSupport.deleteCaptureUri(context, target)
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val readable = withContext(Dispatchers.IO) {
                CallingCardPhotoSupport.isCaptureReadable(context, target)
            }
            if (readable) {
                CallingCardPhotoSupport.deleteIfCapture(context, backgroundUri)
                backgroundUri = target
            } else {
                CallingCardPhotoSupport.deleteCaptureUri(context, target)
                showMessage(callingCardCameraFailed)
            }
        }
    }

    fun launchGallery() {
        galleryPicker.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    val callingCardCameraUnavailable = stringResource(R.string.calling_card_camera_unavailable)
    fun launchCamera() {
        if (!CallingCardPhotoSupport.isCameraAvailable(context)) {
            showMessage(callingCardCameraUnavailable)
            return
        }
        val target = CallingCardPhotoSupport.createCaptureTarget(context)
        if (target == null) {
            showMessage(callingCardCameraFailed)
            return
        }
        pendingCameraUri = target.uri.toString()
        try {
            cameraLauncher.launch(target.uri)
        } catch (e: Exception) {
            pendingCameraUri = null
            CallingCardPhotoSupport.discard(target)
            showMessage(callingCardCameraUnavailable)
        }
    }

    fun launchGooglePhotos() {
        val googlePhotosIntent = Intent(Intent.ACTION_PICK).apply {
            type = "image/*"
            setPackage("com.google.android.apps.photos")
        }
        if (googlePhotosIntent.resolveActivity(context.packageManager) != null) {
            googlePhotosLauncher.launch(googlePhotosIntent)
        } else {
            googlePhotosNotInstalled = true
        }
    }

    fun launchGalleryFallback() {
        googlePhotosLauncher.launch(
            Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "image/*"
            }
        )
    }

    fun discardDrafts() {
        if (backgroundUri != initialBackgroundUri) {
            CallingCardPhotoSupport.deleteIfCapture(context, backgroundUri)
        }
        CallingCardPhotoSupport.deleteCaptureUri(context, pendingCameraUri)
        pendingCameraUri = null
    }

    BackHandler {
        if (stage == 1) stage = 0 else {
            discardDrafts()
            onDismiss()
        }
    }

    Dialog(
        onDismissRequest = {
            discardDrafts()
            onDismiss()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        val isDark = LocalIsDarkTheme.current
        val view = LocalView.current
        SideEffect {
            ViewCompat.getWindowInsetsController(view)?.apply {
                isAppearanceLightStatusBars = !isDark
                isAppearanceLightNavigationBars = !isDark
            }
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface
        ) {
            Box(Modifier.fillMaxSize()) {
                when (stage) {
                    0 -> CallingCardPhotoStep(
                        contactName = contactName,
                        photoUri = contactPhotoUri,
                        backgroundUri = previewBackgroundUri,
                        canRemovePhoto = backgroundUri != null,
                        onBack = {
                            discardDrafts()
                            onDismiss()
                        },
                        onNext = { stage = 1 },
                        onCamera = { launchCamera() },
                        onGallery = { launchGallery() },
                        onGooglePhotos = { launchGooglePhotos() },
                        onRemovePhoto = {
                            CallingCardPhotoSupport.deleteIfCapture(context, backgroundUri)
                            backgroundUri = null
                        }
                    )

                    else -> CallingCardCustomizeStep(
                        contactName = contactName,
                        phoneNumber = phoneNumber,
                        photoUri = contactPhotoUri,
                        backgroundUri = previewBackgroundUri,
                        style = style,
                        onBack = { stage = 0 },
                        onStyleChange = {
                            style = it
                            usingDefault = false
                        },
                        onUseDefault = {
                            style = fallbackStyle
                            usingDefault = true
                        },
                        usingDefault = usingDefault,
                        onDone = {
                            onDone(
                                CallingCardEditorResult(
                                    backgroundUri = backgroundUri,
                                    style = if (usingDefault) null else style
                                )
                            )
                        }
                    )
                }

                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 96.dp)
                )
            }
        }
    }

    if (googlePhotosNotInstalled) {
        AlertDialog(
            onDismissRequest = { googlePhotosNotInstalled = false },
            text = { Text(stringResource(R.string.calling_card_google_photos_not_installed)) },
            confirmButton = {
                TextButton(onClick = {
                    googlePhotosNotInstalled = false
                    launchGalleryFallback()
                }) {
                    Text(stringResource(R.string.calling_card_action_gallery))
                }
            },
            dismissButton = {
                TextButton(onClick = { googlePhotosNotInstalled = false }) {
                    Text(stringResource(R.string.not_now))
                }
            }
        )
    }
}

@Composable
private fun EditorHeader(
    title: String,
    onBack: () -> Unit,
    actionLabel: String,
    onAction: () -> Unit,
    usingDefault: Boolean = false,
    onUseDefault: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NavigationIcon(onClick = onBack)
        Box(modifier = Modifier.weight(1f).padding(horizontal = 6.dp)) {
            Title(title)
        }


        if (onUseDefault != null) {
            Button(
                onClick = onUseDefault,
                enabled = !usingDefault,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
            ) {
                Text(stringResource(R.string.reset))
            }
            Spacer(Modifier.width(6.dp))
        }
        Button(onClick = onAction) {
            Text(actionLabel)
        }
        Spacer(Modifier.width(16.dp))
    }
}

@Composable
private fun CallingCardPhotoStep(
    contactName: String,
    photoUri: String?,
    backgroundUri: String?,
    canRemovePhoto: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onGooglePhotos: () -> Unit,
    onRemovePhoto: () -> Unit
) {
    val compactHeight = LocalConfiguration.current.screenHeightDp < 560
    val previewDescription = stringResource(R.string.calling_card_photo_preview)
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        EditorHeader(
            title = stringResource(R.string.contact_call_background_choose),
            onBack = onBack,
            actionLabel = stringResource(R.string.calling_card_next),
            onAction = onNext
        )


        if (isLandscape) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {

                val callScreenBackdrop = rememberLayerBackdrop()
                PortraitPreviewFrame(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(
                            horizontal = if (compactHeight) 16.dp else 24.dp,
                            vertical = if (compactHeight) 4.dp else 8.dp
                        ),
                    shape = MaterialTheme.shapes.large
                ) {
                    if (backgroundUri != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .semantics {
                                    contentDescription = previewDescription
                                }
                        ) {
                            // Same rendering the live call screen uses: Crop for the
                            // custom background and the same scrims, so the preview shows
                            // exactly what will be saved.
                            ExpressiveBackground(
                                photoUri = photoUri,
                                backgroundUri = backgroundUri,
                                liquidGlassBackdrop = callScreenBackdrop,
                                hideGradient = false,
                                enableParticles = false
                            )
                        }
                    } else {
                        NoPhotoPlaceholder(contactName = contactName, photoUri = photoUri)
                    }
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
//                        .verticalScroll(rememberScrollState())
                ) {
                    CallingCardPhotoActionBar(
                        canRemovePhoto = canRemovePhoto,
                        onCamera = onCamera,
                        onGallery = onGallery,
                        onGooglePhotos = onGooglePhotos,
                        onRemovePhoto = onRemovePhoto,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        } else {
            val callScreenBackdrop = rememberLayerBackdrop()
            PortraitPreviewFrame(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(
                        horizontal = if (compactHeight) 16.dp else 24.dp,
                        vertical = if (compactHeight) 4.dp else 8.dp
                    )
            ) {
                if (backgroundUri != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .semantics {
                                contentDescription = previewDescription
                            }
                    ) {
                        // Same rendering the live call screen uses: Crop for the
                        // custom background and the same scrims, so the preview shows
                        // exactly what will be saved.
                        ExpressiveBackground(
                            photoUri = photoUri,
                            backgroundUri = backgroundUri,
                            liquidGlassBackdrop = callScreenBackdrop,
                            hideGradient = false,
                            enableParticles = false
                        )
                    }
                } else {
                    NoPhotoPlaceholder(contactName = contactName, photoUri = photoUri)
                }
            }

            Spacer(Modifier.height(8.dp))

            CallingCardPhotoActionBar(
                canRemovePhoto = canRemovePhoto,
                onCamera = onCamera,
                onGallery = onGallery,
                onGooglePhotos = onGooglePhotos,
                onRemovePhoto = onRemovePhoto
            )

            Spacer(Modifier.height(if (compactHeight) 2.dp else 8.dp))
        }
    }
}

@Composable
private fun CallingCardPhotoActionBar(
    canRemovePhoto: Boolean,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onGooglePhotos: () -> Unit,
    onRemovePhoto: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    Surface(
        modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp).fillMaxWidth(),
        color = dialpadKeyColor,
        shape = MaterialTheme.shapes.extraLarge,
        tonalElevation = 3.dp
    ) {
        Row(
            modifier = modifier
                .padding(horizontal = 16.dp, vertical = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PhotoActionButton(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.calling_card_action_camera),
                onClick = onCamera,
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.AddAPhoto,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                }
            )
            PhotoActionButton(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.calling_card_action_gallery),
                onClick = onGallery,
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.AddPhotoAlternate,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                }
            )
            PhotoActionButton(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.calling_card_action_google_photos),
                onClick = onGooglePhotos,
                icon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_google_photos),
                        contentDescription = null,
                        tint = Color.Unspecified,
                        modifier = Modifier.size(24.dp)
                    )
                }
            )
            PhotoActionButton(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.calling_card_action_remove),
                enabled = canRemovePhoto,
                onClick = onRemovePhoto,
                icon = {
                    Icon(
                        imageVector = ImageVector.vectorResource(id = R.drawable.ic_delete),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                }
            )
        }
    }
}

@Composable
private fun PhotoActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: @Composable () -> Unit
) {
    val containerColor = if (enabled) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val contentColor = if (enabled) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }
    val labelColor = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }

    val interaction = remember { MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()
    val radius by animateDpAsState(
        if (isPressed) 16.dp else 42.dp,
        spring(stiffness = Spring.StiffnessLow),
        label = "btnRadius"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.height(56.dp).fillMaxWidth(),
            shape = RoundedCornerShape(radius),
            color = containerColor,
            interactionSource = interaction
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(containerColor),
                contentAlignment = Alignment.Center
            ) {
                CompositionLocalProvider(LocalContentColor provides contentColor) {
                    icon()
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = labelColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun NoPhotoPlaceholder(contactName: String, photoUri: String?) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (photoUri != null) {
            AsyncImage(
                model = photoUri,
                contentDescription = null,
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.height(16.dp))
        } else {
            Icon(
                imageVector = Icons.Rounded.Wallpaper,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp)
            )
            Spacer(Modifier.height(16.dp))
        }
        if (contactName.isNotBlank()) {
            Text(
                text = contactName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
        }
        Text(
            text = stringResource(R.string.calling_card_no_photo),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CallingCardCustomizeStep(
    contactName: String,
    phoneNumber: String,
    photoUri: String?,
    backgroundUri: String?,
    style: CallingCardStyle,
    onBack: () -> Unit,
    onStyleChange: (CallingCardStyle) -> Unit,
    onUseDefault: () -> Unit,
    usingDefault: Boolean,
    onDone: () -> Unit
) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        if (!isLandscape) LivePreview(
            contactName = contactName,
            phoneNumber = phoneNumber,
            photoUri = photoUri,
            backgroundUri = backgroundUri,
            style = style
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.End
        ) {
            EditorHeader(
                title = stringResource(R.string.calling_card_customize_title),
                onBack = onBack,
                actionLabel = stringResource(R.string.done),
                onAction = onDone,
                usingDefault = usingDefault,
                onUseDefault = if (isLandscape) onUseDefault else null
            )

            if (isLandscape) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
//                    PortraitPreviewFrame(
//                        modifier = Modifier
//                            .weight(1f)
//                            .fillMaxSize()
//                    ) {
//                        LivePreview(
//                            contactName = contactName,
//                            phoneNumber = phoneNumber,
//                            photoUri = photoUri,
//                            backgroundUri = backgroundUri,
//                            style = style
//                        )
//                    }
                    ScaledCallScreenPreview(
                        contactName = contactName,
                        phoneNumber = phoneNumber,
                        photoUri = photoUri,
                        backgroundUri = backgroundUri,
                        style = style,
                        modifier = Modifier.weight(1f).fillMaxSize()
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        AppearanceControls(
                            modifier = Modifier.fillMaxHeight(),
                            style = style,
                            showBlur = backgroundUri != null,
                            usingDefault = usingDefault,
                            onStyleChange = onStyleChange,
                            onUseDefault = onUseDefault
                        )

                        if (backgroundUri != null) {
                            Spacer(Modifier.height(cardSpacedBy))
                            Surface(
                                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp).fillMaxWidth(),
                                color = dialpadKeyColor,
                                shape = MaterialTheme.shapes.extraLarge.copy(
                                    topStart = CornerSize(cardCornerExtraSmall), topEnd = CornerSize(cardCornerExtraSmall)
                                ),
                                tonalElevation = 3.dp
                            ) {
                                Slider(
                                    value = style.blurRadiusDp.toFloat(),
                                    onValueChange = { v ->
                                        onStyleChange(style.copy(blurRadiusDp = v.roundToInt()))
                                    },
                                    valueRange = 0f..CallingCardStyle.MAX_BLUR_DP.toFloat(),
                                    steps = CallingCardStyle.MAX_BLUR_DP - 1,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    thumb = {
                                        SliderDefaults.Thumb(
                                            interactionSource = remember { MutableInteractionSource() },
                                            modifier = Modifier.scale(scaleX = 0.8f, scaleY = 0.8f)
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            } else {
                Spacer(Modifier.weight(1f))

//            PortraitPreviewFrame(
//                modifier = Modifier
//                    .weight(1f)
//                    .fillMaxWidth()
//                    .padding(horizontal = 24.dp, vertical = 8.dp)
//            ) {
//                LivePreview(
//                    contactName = contactName,
//                    phoneNumber = phoneNumber,
//                    photoUri = photoUri,
//                    backgroundUri = backgroundUri,
//                    style = style
//                )
//            }

                Button(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    onClick = onUseDefault,
                    enabled = !usingDefault,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                ) {
                    Text(stringResource(R.string.reset))
                }

                Spacer(Modifier.height(8.dp))

                AppearanceControls(
                    modifier = Modifier,
                    style = style,
                    showBlur = backgroundUri != null,
                    usingDefault = usingDefault,
                    onStyleChange = onStyleChange,
                    onUseDefault = onUseDefault
                )

                if (backgroundUri != null) {
                    Spacer(Modifier.height(cardSpacedBy))
                    Surface(
                        modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp).fillMaxWidth(),
                        color = dialpadKeyColor,
                        shape = MaterialTheme.shapes.extraLarge.copy(
                            topStart = CornerSize(cardCornerExtraSmall), topEnd = CornerSize(cardCornerExtraSmall)
                        ),
                        tonalElevation = 3.dp
                    ) {
                        Slider(
                            value = style.blurRadiusDp.toFloat(),
                            onValueChange = { v ->
                                onStyleChange(style.copy(blurRadiusDp = v.roundToInt()))
                            },
                            valueRange = 0f..CallingCardStyle.MAX_BLUR_DP.toFloat(),
                            steps = CallingCardStyle.MAX_BLUR_DP - 1,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            thumb = {
                                SliderDefaults.Thumb(
                                    interactionSource = remember { MutableInteractionSource() },
                                    modifier = Modifier.scale(scaleX = 0.8f, scaleY = 0.8f)
                                )
                            }
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun LivePreview(
    contactName: String,
    phoneNumber: String,
    photoUri: String?,
    backgroundUri: String?,
    style: CallingCardStyle
) {
    CallScreenPreview(
        contactName = contactName,
        phoneNumber = phoneNumber,
        photoUri = photoUri,
        backgroundUri = backgroundUri,
        style = CallScreenPreviewStyle.Default,
        callingCardStyle = style,
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun AppearanceControls(
    modifier: Modifier,
    style: CallingCardStyle,
    showBlur: Boolean,
    usingDefault: Boolean,
    onStyleChange: (CallingCardStyle) -> Unit,
    onUseDefault: () -> Unit
) {
    Surface(
        modifier = modifier.padding(horizontal = 16.dp).fillMaxWidth()
            .then(
                if (showBlur) Modifier
                else Modifier.padding(bottom = 16.dp)
            ),
        color = dialpadKeyColor,
        shape = if (showBlur) MaterialTheme.shapes.extraLarge.copy(
            bottomEnd = CornerSize(cardCornerExtraSmall), bottomStart = CornerSize(cardCornerExtraSmall)
        ) else MaterialTheme.shapes.extraLarge,
        tonalElevation = 3.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(CallingCardFont.entries, key = { it.id }) { font ->
                    FontPreset(
                        font = font,
                        selected = !usingDefault && style.font == font,
                        onClick = {
                            val clampedWeight = style.fontWeight.coerceIn(font.minWeight, font.maxWeight)
                            onStyleChange(style.copy(font = font, fontWeight = clampedWeight))
                        }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Slider(
                value = style.fontSizeSp.toFloat(),
                onValueChange = { v ->
                    onStyleChange(style.copy(fontSizeSp = v.roundToInt().coerceIn(
                        CallingCardStyle.MIN_FONT_SIZE_SP, CallingCardStyle.MAX_FONT_SIZE_SP
                    )))
                },
                valueRange = CallingCardStyle.MIN_FONT_SIZE_SP.toFloat()..CallingCardStyle.MAX_FONT_SIZE_SP.toFloat(),
                steps = CallingCardStyle.MAX_FONT_SIZE_SP - CallingCardStyle.MIN_FONT_SIZE_SP - 1,
                modifier = Modifier.padding(horizontal = 16.dp),
                thumb = {
                    SliderDefaults.Thumb(
                        interactionSource = remember { MutableInteractionSource() },
                        modifier = Modifier.scale(scaleX = 0.8f, scaleY = 0.8f)
                    )
                }
            )
            Spacer(Modifier.height(12.dp))

            val weightMin = style.font.minWeight
            val weightMax = style.font.maxWeight
            Slider(
                value = style.fontWeight.toFloat().coerceIn(
                    weightMin.toFloat(), weightMax.toFloat()
                ),
                onValueChange = { v ->
                    val stepped = (v / CallingCardStyle.WEIGHT_STEP).roundToInt() * CallingCardStyle.WEIGHT_STEP
                    onStyleChange(style.copy(fontWeight = stepped.coerceIn(weightMin, weightMax)))
                },
                valueRange = weightMin.toFloat()..weightMax.toFloat(),
                steps = ((weightMax - weightMin) / CallingCardStyle.WEIGHT_STEP - 1).coerceAtLeast(0),
                modifier = Modifier.padding(horizontal = 16.dp),
                thumb = {
                    SliderDefaults.Thumb(
                        interactionSource = remember { MutableInteractionSource() },
                        modifier = Modifier.scale(scaleX = 0.8f, scaleY = 0.8f)
                    )
                }
            )

            Spacer(Modifier.height(16.dp))

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item(key = "theme") {
                    ColorSwatch(
                        color = null,
                        label = stringResource(R.string.calling_card_color_default),
                        selected = !usingDefault && style.textColorArgb == null,
                        onClick = { onStyleChange(style.copy(textColorArgb = null)) }
                    )
                }
                items(callingCardColors, key = { it.argb }) { preset ->
                    ColorSwatch(
                        color = Color(preset.argb),
                        label = if (preset.labelRes != null) stringResource(preset.labelRes) else null,
                        selected = !usingDefault && style.textColorArgb == preset.argb,
                        onClick = { onStyleChange(style.copy(textColorArgb = preset.argb)) }
                    )
                }
            }

//            Spacer(Modifier.height(8.dp))
//            TextButton(
//                onClick = onUseDefault,
//                enabled = !usingDefault
//            ) {
//                Text(stringResource(R.string.calling_card_use_default))
//            }
        }
    }
}

@Composable
private fun FontPreset(
    font: CallingCardFont,
    selected: Boolean,
    onClick: () -> Unit
) {
    val container = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val content = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Column(
        modifier = Modifier
            .width(78.dp)
            .clip(MaterialTheme.shapes.large)
            .background(container)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Aa",
            fontFamily = font.family,
            fontSize = 22.sp,
            color = content,
            maxLines = 1
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(font.labelRes),
            style = MaterialTheme.typography.labelSmall,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun rememberScreenAspectRatio(): Float {
    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current

    val widthDp = with(density) { windowInfo.containerSize.width.toDp() }
    val heightDp = with(density) { windowInfo.containerSize.height.toDp() }

    // Always portrait orientation: shorter side / longer side
    val (w, h) = if (widthDp < heightDp) widthDp to heightDp else heightDp to widthDp
    return (w / h)
}

@Composable
private fun PortraitPreviewFrame(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.extraExtraLarge,
    content: @Composable () -> Unit
) {
    val aspectRatio = rememberScreenAspectRatio()

    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val frameWidth: Dp
        val frameHeight: Dp
        if (maxWidth / maxHeight > aspectRatio) {
            frameHeight = maxHeight
            frameWidth = maxHeight * aspectRatio
        } else {
            frameWidth = maxWidth
            frameHeight = maxWidth / aspectRatio
        }

        Surface(
            modifier = Modifier
                .size(frameWidth, frameHeight)
                .clip(shape)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = shape
                ),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            tonalElevation = 2.dp
        ) {
            Box(Modifier.fillMaxSize()) {
                content()
            }
        }
    }
}

@Composable
private fun ScaledCallScreenPreview(
    contactName: String,
    phoneNumber: String,
    photoUri: String?,
    backgroundUri: String?,
    style: CallingCardStyle,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val shortSide = minOf(configuration.screenWidthDp, configuration.screenHeightDp).dp
    val longSide = maxOf(configuration.screenWidthDp, configuration.screenHeightDp).dp

    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val scale = minOf(maxWidth / shortSide, maxHeight / longSide)
        val scaledWidth = shortSide * scale
        val scaledHeight = longSide * scale

        val baseDensity = LocalDensity.current
        val scaledDensity = Density(
            density = baseDensity.density * scale,
            fontScale = baseDensity.fontScale
        )

        Box(
            modifier = Modifier
                .size(scaledWidth, scaledHeight)
                .clip(MaterialTheme.shapes.large)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large)
        ) {
            CompositionLocalProvider(LocalDensity provides scaledDensity) {
                CallScreenPreview(
                    contactName = contactName,
                    phoneNumber = phoneNumber,
                    photoUri = photoUri,
                    backgroundUri = backgroundUri,
                    style = CallScreenPreviewStyle.Default,
                    callingCardStyle = style,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

private data class ColorPreset(val argb: Int, val labelRes: Int? = null)

private val callingCardColors = listOf(
    ColorPreset(0xFFFFFFFF.toInt()),
    ColorPreset(0xFF000000.toInt()),
    ColorPreset(0xFFFF98CF.toInt()),
    ColorPreset(0xFFFF6796.toInt()),
    ColorPreset(0xFFEC1F27.toInt()),
    ColorPreset(0xFFFF8535.toInt()),
    ColorPreset(0xFFE75D00.toInt()),
    ColorPreset(0xFFFEEEB8.toInt()),
    ColorPreset(0xFFFFE056.toInt()),
    ColorPreset(0xFFC6EFF3.toInt()),
    ColorPreset(0xFF63BE33.toInt()),
    ColorPreset(0xFF2D5F13.toInt()),
    ColorPreset(0xFF72D8C8.toInt()),
    ColorPreset(0xFF0072DA.toInt()),
    ColorPreset(0xFF4DE1F5.toInt()),
    ColorPreset(0xFFB0D4FE.toInt()),
    ColorPreset(0xFF94A8FE.toInt()),
    ColorPreset(0xFF201C75.toInt()),
    ColorPreset(0xFFC498F8.toInt()),
    ColorPreset(0xFF7A43BF.toInt())
)
//private val callingCardColors = listOf(
//    ColorPreset(0xFF78D3F7.toInt()),
//    ColorPreset(0xFF7FA5F7.toInt()),
//    ColorPreset(0xFFAB8DF6.toInt()),
//    ColorPreset(0xFFC45FF5.toInt()),
//    ColorPreset(0xFFDE789C.toInt()),
//    ColorPreset(0xFFEF9485.toInt()),
//    ColorPreset(0xFFF2A984.toInt()),
//    ColorPreset(0xFFF5C883.toInt()),
//    ColorPreset(0xFFF8DA85.toInt()),
//    ColorPreset(0xFFFDF9A0.toInt()),
//    ColorPreset(0xFFEAF19A.toInt()),
//    ColorPreset(0xFFBADB93.toInt()),
//    ColorPreset(0xFFB4C1AB.toInt()),
//    ColorPreset(0xFFA3B6BC.toInt()),
//    ColorPreset(0xFFB5A8B5.toInt()),
//    ColorPreset(0xFFB8B1A8.toInt())
//)

private val CallingCardStyleSaver = Saver<CallingCardStyle, String>(
    save = { CallingCardStyleCodec.encode(it) },
    restore = { CallingCardStyleCodec.decode(it) ?: CallingCardStyle.ThemeDefault }
)
