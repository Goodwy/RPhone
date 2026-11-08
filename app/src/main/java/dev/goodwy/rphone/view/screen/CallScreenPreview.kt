package dev.goodwy.rphone.view.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.CallBackgroundStore
import dev.goodwy.rphone.controller.util.CallingCardStyle
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.controller.util.callingCardNameShadow
import dev.goodwy.rphone.liquidglass.backdrops.rememberLayerBackdrop
import org.koin.compose.koinInject

/**
 * Typography and avatar sizing for [CallScreenPreview].
 *
 * Kept as a small data class so per-contact font customization can extend it later
 * without changing the preview composable signature.
 */
data class CallScreenPreviewStyle(
    val statusFontSize: TextUnit = 13.sp,
    val nameFontSize: TextUnit = 22.sp,
    val nameFontWeight: FontWeight = FontWeight.Medium,
    val numberFontSize: TextUnit = 14.sp,
    val avatarSize: Dp = 96.dp
) {
    companion object {
        val Default = CallScreenPreviewStyle()
        val Large = CallScreenPreviewStyle(
            statusFontSize = 15.sp,
            nameFontSize = 28.sp,
            numberFontSize = 16.sp,
            avatarSize = 132.dp
        )
    }
}

/**
 * Outgoing-style call screen preview used while a calling-card background is being edited.
 *
 * This renders the real background treatment and the hero block only. It has no Telecom
 * dependency, no call controls, and no incoming-call variant. The avatar visibility rule
 * is shared with the live call screen through [CallBackgroundStore.shouldShowAvatar].
 */
@Composable
fun CallScreenPreview(
    contactName: String,
    phoneNumber: String = "",
    photoUri: String? = null,
    backgroundUri: String? = null,
    style: CallScreenPreviewStyle = CallScreenPreviewStyle.Default,
    callingCardStyle: CallingCardStyle = CallingCardStyle.ThemeDefault,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val preferenceManager = koinInject<PreferenceManager>()
    val settingsState by preferenceManager.settingsChanged.collectAsStateWithLifecycle()
    val showCallScreenAvatar = remember(settingsState) {
        preferenceManager.getBoolean(PreferenceManager.KEY_SHOW_CALL_SCREEN_AVATAR, true)
    }
    val hideAvatarWithBg = remember(settingsState) {
        preferenceManager.getBoolean(PreferenceManager.KEY_HIDE_AVATAR_WITH_BACKGROUND, false)
    }
    val defaultBackgroundUri = remember(settingsState) {
        CallBackgroundStore.defaultModel(context)
    }
    val shouldShowAvatar = CallBackgroundStore.shouldShowAvatar(
        showCallScreenAvatar = showCallScreenAvatar,
        hideAvatarWithBg = hideAvatarWithBg,
        backgroundUri = backgroundUri,
        defaultBackgroundUri = defaultBackgroundUri
    )
    val showAvatar = shouldShowAvatar && !photoUri.isNullOrEmpty()
    val unknownName = stringResource(R.string.label_unknown)
    val displayName = if (contactName.isBlank()) unknownName else contactName
    // Callers may pass a forceLtr()-marked number as the display name. Compare the
    // visible text so the number is not rendered twice.
    val showNumber = phoneNumber.isNotBlank() &&
        phoneNumber.withoutBidiMarks() != contactName.withoutBidiMarks()
    // Match the real call screen: when the contact has no own background, the
    // global default is what the user actually sees.
    val effectiveBackgroundUri = backgroundUri ?: defaultBackgroundUri
    val nameColor = callingCardStyle.textColor ?: MaterialTheme.colorScheme.onSurface
    val nameShadow = callingCardNameShadow(
        color = nameColor,
        hasBackground = !effectiveBackgroundUri.isNullOrEmpty()
    )
    val backdrop = rememberLayerBackdrop()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        ExpressiveBackground(
            photoUri = photoUri,
            backgroundUri = effectiveBackgroundUri,
            liquidGlassBackdrop = backdrop,
            hideGradient = false,
            enableParticles = false
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.call_status_calling),
                style = MaterialTheme.typography.titleMedium.copy(fontSize = style.statusFontSize),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(10.dp))

            Text(
                text = displayName,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = style.nameFontSize,
                    fontFamily = callingCardStyle.font.family,
                    shadow = nameShadow
                ),
                fontWeight = style.nameFontWeight,
                color = nameColor,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (showNumber) {
                Spacer(Modifier.height(6.dp))

                Text(
                    text = phoneNumber,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = style.numberFontSize),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.height(20.dp))

            if (showAvatar) {
                HeroAvatar(photoUri = photoUri, avatarSize = style.avatarSize)
            } else {
                Spacer(Modifier.height(style.avatarSize))
            }
        }
    }
}

/** Invisible bidi controls, including the left-to-right mark added by forceLtr(). */
private val bidiMarks = setOf(
    '\u200E',
    '\u200F',
    '\u202A',
    '\u202B',
    '\u202C',
    '\u202D',
    '\u202E',
    '\u2066',
    '\u2067',
    '\u2068',
    '\u2069'
)

private fun String.withoutBidiMarks(): String = filterNot { it in bidiMarks }
