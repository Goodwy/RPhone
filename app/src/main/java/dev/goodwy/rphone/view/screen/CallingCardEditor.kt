package dev.goodwy.rphone.view.screen

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import dev.goodwy.rphone.liquidglass.backdrops.rememberLayerBackdrop
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.CallingCardFont
import dev.goodwy.rphone.controller.util.CallingCardPhotoSupport
import dev.goodwy.rphone.controller.util.CallingCardStyle
import dev.goodwy.rphone.controller.util.CallingCardStyleCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    var cloudProblem by remember { mutableStateOf<CloudPhotoProblem?>(null) }

    // The inherited global background is shown for context, but it is not a
    // draft value: Done still returns only what the user picked here.
    val previewBackgroundUri = backgroundUri ?: inheritedBackgroundUri

    fun showMessage(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

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
                showMessage(context.getString(R.string.calling_card_image_unreadable))
            }
        }
    }

    val galleryPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> acceptPickedUri(uri) }

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
                showMessage(context.getString(R.string.calling_card_camera_failed))
            }
        }
    }

    fun launchGallery() {
        galleryPicker.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    fun launchCamera() {
        if (!CallingCardPhotoSupport.isCameraAvailable(context)) {
            showMessage(context.getString(R.string.calling_card_camera_unavailable))
            return
        }
        val target = CallingCardPhotoSupport.createCaptureTarget(context)
        if (target == null) {
            showMessage(context.getString(R.string.calling_card_camera_failed))
            return
        }
        pendingCameraUri = target.uri.toString()
        try {
            cameraLauncher.launch(target.uri)
        } catch (e: Exception) {
            pendingCameraUri = null
            CallingCardPhotoSupport.discard(target)
            showMessage(context.getString(R.string.calling_card_camera_unavailable))
        }
    }

    fun launchGooglePhotos() {
        scope.launch {
            val status = withContext(Dispatchers.IO) {
                CallingCardPhotoSupport.cloudPhotoStatus(context)
            }
            when (CallingCardPhotoSupport.cloudPhotoAction(status)) {
                CallingCardPhotoSupport.CloudPhotoAction.OPEN_PICKER -> launchGallery()
                CallingCardPhotoSupport.CloudPhotoAction.OPEN_SETTINGS ->
                    cloudProblem = CloudPhotoProblem.NOT_SELECTED
                CallingCardPhotoSupport.CloudPhotoAction.FALLBACK ->
                    cloudProblem = CloudPhotoProblem.UNAVAILABLE
            }
        }
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

    cloudProblem?.let { problem ->
        CloudPhotoProblemDialog(
            problem = problem,
            onDismiss = { cloudProblem = null },
            onUseGallery = {
                cloudProblem = null
                launchGallery()
            },
            onOpenSettings = {
                cloudProblem = null
                if (!CallingCardPhotoSupport.openPhotoPickerSettings(context)) {
                    showMessage(
                        context.getString(R.string.calling_card_google_photos_settings_failed)
                    )
                }
            }
        )
    }
}

private enum class CloudPhotoProblem {
    UNAVAILABLE,
    NOT_SELECTED
}

@Composable
private fun CloudPhotoProblemDialog(
    problem: CloudPhotoProblem,
    onDismiss: () -> Unit,
    onUseGallery: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val title = when (problem) {
        CloudPhotoProblem.NOT_SELECTED ->
            stringResource(R.string.calling_card_google_photos_not_selected_title)
        CloudPhotoProblem.UNAVAILABLE ->
            stringResource(R.string.calling_card_google_photos_unavailable_title)
    }
    val body = when (problem) {
        CloudPhotoProblem.NOT_SELECTED ->
            stringResource(R.string.calling_card_google_photos_not_selected_body)
        CloudPhotoProblem.UNAVAILABLE ->
            stringResource(R.string.calling_card_google_photos_unavailable_body)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            if (problem == CloudPhotoProblem.NOT_SELECTED) {
                TextButton(onClick = onOpenSettings) {
                    Text(stringResource(R.string.calling_card_google_photos_open_settings))
                }
            } else {
                TextButton(onClick = onUseGallery) {
                    Text(stringResource(R.string.calling_card_google_photos_use_gallery))
                }
            }
        },
        dismissButton = {
            if (problem == CloudPhotoProblem.NOT_SELECTED) {
                TextButton(onClick = onUseGallery) {
                    Text(stringResource(R.string.calling_card_google_photos_use_gallery))
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.calling_card_google_photos_cancel))
                }
            }
        }
    )
}

@Composable
private fun EditorHeader(
    title: String,
    onBack: () -> Unit,
    backLabel: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = backLabel
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        TextButton(onClick = onAction) {
            Text(actionLabel)
        }
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        EditorHeader(
            title = stringResource(R.string.calling_card_photo_title),
            onBack = onBack,
            backLabel = stringResource(R.string.calling_card_back),
            actionLabel = stringResource(R.string.calling_card_next),
            onAction = onNext
        )

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
                            contentDescription = stringResource(R.string.calling_card_photo_preview)
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

        if (!compactHeight) {
            Text(
                text = stringResource(R.string.calling_card_photo_supporting),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp, vertical = 4.dp)
            )
        }

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

@Composable
private fun CallingCardPhotoActionBar(
    canRemovePhoto: Boolean,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onGooglePhotos: () -> Unit,
    onRemovePhoto: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.Top
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
                        imageVector = Icons.Rounded.DeleteOutline,
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

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {
                if (!enabled) disabled()
            }
            .padding(vertical = 6.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
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
        Spacer(Modifier.height(4.dp))
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        EditorHeader(
            title = stringResource(R.string.calling_card_customize_title),
            onBack = onBack,
            backLabel = stringResource(R.string.calling_card_back),
            actionLabel = stringResource(R.string.calling_card_done),
            onAction = onDone
        )

        if (isLandscape) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                PortraitPreviewFrame(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                ) {
                    LivePreview(
                        contactName = contactName,
                        phoneNumber = phoneNumber,
                        photoUri = photoUri,
                        backgroundUri = backgroundUri,
                        style = style
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    AppearanceControls(
                        style = style,
                        usingDefault = usingDefault,
                        onStyleChange = onStyleChange,
                        onUseDefault = onUseDefault
                    )
                }
            }
        } else {
            PortraitPreviewFrame(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
            ) {
                LivePreview(
                    contactName = contactName,
                    phoneNumber = phoneNumber,
                    photoUri = photoUri,
                    backgroundUri = backgroundUri,
                    style = style
                )
            }
            AppearanceControls(
                style = style,
                usingDefault = usingDefault,
                onStyleChange = onStyleChange,
                onUseDefault = onUseDefault
            )
        }

        Spacer(Modifier.height(12.dp))
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
    style: CallingCardStyle,
    usingDefault: Boolean,
    onStyleChange: (CallingCardStyle) -> Unit,
    onUseDefault: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        Text(
            text = stringResource(R.string.calling_card_font_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(CallingCardFont.entries, key = { it.id }) { font ->
                FontPreset(
                    font = font,
                    selected = !usingDefault && style.font == font,
                    onClick = { onStyleChange(style.copy(font = font)) }
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.calling_card_text_color_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        LazyRow(
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
                    label = stringResource(preset.labelRes),
                    selected = !usingDefault && style.textColorArgb == preset.argb,
                    onClick = { onStyleChange(style.copy(textColorArgb = preset.argb)) }
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        TextButton(
            onClick = onUseDefault,
            enabled = !usingDefault
        ) {
            Text(stringResource(R.string.calling_card_use_default))
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
            .clip(RoundedCornerShape(18.dp))
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
private fun ColorSwatch(
    color: Color?,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(color ?: MaterialTheme.colorScheme.surfaceContainerHighest)
            .border(if (selected) 3.dp else 1.dp, borderColor, CircleShape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (color == null) {
            Text(
                text = "A",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else if (selected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = label,
                tint = if (color.luminance() > 0.5f) Color.Black else Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun PortraitPreviewFrame(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val frameWidth: Dp = minOf(maxWidth, maxHeight * 9f / 16f)
        val frameHeight: Dp = frameWidth * 16f / 9f
        Surface(
            modifier = Modifier
                .size(frameWidth, frameHeight)
                .clip(RoundedCornerShape(28.dp)),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            tonalElevation = 2.dp
        ) {
            Box(Modifier.fillMaxSize()) {
                content()
            }
        }
    }
}

private data class ColorPreset(val argb: Int, val labelRes: Int)

private val callingCardColors = listOf(
    ColorPreset(0xFFFFFFFF.toInt(), R.string.calling_card_color_white),
    ColorPreset(0xFFFFF1CC.toInt(), R.string.calling_card_color_cream),
    ColorPreset(0xFFFFC857.toInt(), R.string.calling_card_color_gold),
    ColorPreset(0xFFFF8A80.toInt(), R.string.calling_card_color_coral),
    ColorPreset(0xFF8ECAE6.toInt(), R.string.calling_card_color_sky),
    ColorPreset(0xFFA8E6CF.toInt(), R.string.calling_card_color_mint),
    ColorPreset(0xFFCDB4DB.toInt(), R.string.calling_card_color_lavender),
    ColorPreset(0xFF101418.toInt(), R.string.calling_card_color_ink)
)

private val CallingCardStyleSaver = Saver<CallingCardStyle, String>(
    save = { CallingCardStyleCodec.encode(it) },
    restore = { CallingCardStyleCodec.decode(it) ?: CallingCardStyle.ThemeDefault }
)
