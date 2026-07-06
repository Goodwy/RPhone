package dev.goodwy.rphone.view.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.controller.util.isEmoji
import dev.goodwy.rphone.controller.util.isLetter
import org.koin.compose.koinInject
import kotlin.math.abs

//private val avatarColors3 = listOf(
//    Color(0xFFEF5350), Color(0xFFEC407A), Color(0xFFAB47BC), Color(0xFF7E57C2),
//    Color(0xFF5C6BC0), Color(0xFF42A5F5), Color(0xFF29B6F6), Color(0xFF26C6DA),
//    Color(0xFF26A69A), Color(0xFF66BB6A), Color(0xFF9CCC65), Color(0xFFD4E157),
//    Color(0xFFFFEE58), Color(0xFFFFCA28), Color(0xFFFFA726), Color(0xFFFF7043)
//)
//
//private val avatarColors2 = listOf(
//    Color(0xFFFF8C42), Color(0xFFFFB7C5), Color(0xFF1E5631), Color(0xFF4A148C),
//    Color(0xFF90CAF9), Color(0xFFC0CA33), Color(0xFFE1BEE7), Color(0xFFCE93D8),
//    Color(0xFF1A237E), Color(0xFFFFB74D), Color(0xFFF48FB1), Color(0xFFE53935),
//    Color(0xFFE64A19), Color(0xFF3949AB), Color(0xFFA5D6A7), Color(0xFF00BCD4)
//)

// Google Message
private val avatarColors = listOf(
    Color(0xFF00D0EA), Color(0xFFFF5B55), Color(0xFFFFC600), Color(0xFFBC56FF),
    Color(0xFFFF891D), Color(0xFF26BC6D), Color(0xFFFF54BB)
)

@Composable
fun RillAvatar(
    name: String,
    photoUri: String? = null,
    icon: ImageVector? = null,
    /** Optional explicit tint colour for vector icon tiles. */
    iconContainerColor: Color? = null,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape
) {
    val prefs = koinInject<PreferenceManager>()
    // Collect settingsChanged once so prefs reads below are stable across recompositions.
    // Using 'by' delegation avoids an extra object allocation on every frame.
    @Suppress("UNUSED_VARIABLE")
    val settingsState by prefs.settingsChanged.collectAsState()

    val showPicture     = remember(settingsState) { prefs.getBoolean(PreferenceManager.KEY_SHOW_PICTURE, true) }
    val showFirstLetter = remember(settingsState) { prefs.getBoolean(PreferenceManager.KEY_SHOW_FIRST_LETTER, true) }
    val colorfulAvatars = remember(settingsState) { prefs.getBoolean(PreferenceManager.KEY_COLORFUL_AVATARS, true) }
    val avatarFrame = remember(settingsState) { prefs.getBoolean(PreferenceManager.KEY_AVATAR_FRAME, false) }

    val hasName  = name.trim().isNotEmpty()
    val isLetter  = name.trim().take(1).isLetter() || name.take(2).isEmoji()
    val colorKey = if (hasName) name else "unknown_caller"

    val (backgroundColor, contentColor) = when {
        iconContainerColor != null -> iconContainerColor.copy(alpha = 0.18f) to iconContainerColor
        colorfulAvatars -> avatarColors[abs(colorKey.hashCode()) % avatarColors.size] to if (avatarFrame) MaterialTheme.colorScheme.onSurface else Color.White
//        googleContacts -> Color(0xFFFFAFAB) to Color(0xFF690A08)
        else -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    }

//    var appeared by remember { mutableStateOf(false) }
//    LaunchedEffect(Unit) { appeared = true }
//    val iconScale by animateFloatAsState(
//        targetValue = if (appeared) 1f else 0.5f,
//        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
//        label = "iconScale"
//    )
//    val iconAlpha by animateFloatAsState(
//        targetValue = if (appeared) 1f else 0f,
//        animationSpec = tween(250),
//        label = "iconAlpha"
//    )

    BoxWithConstraints(
        modifier = modifier
            .then(
                if (avatarFrame) modifier
                    .drawBehind {
                        val borderWidth = size.width * 0.09f // 9% of the width
                        drawOutline(
                            outline = shape.createOutline(size, layoutDirection, this),
                            color = contentColor,
                            style = Stroke(width = borderWidth)
                        )
                    }
                else modifier
            )
            .background(backgroundColor, shape)
//            .scale(iconScale)
//            .alpha(iconAlpha)
            .clip(shape),
        contentAlignment = Alignment.Center
    ) {
        val letterFontSize  = (maxWidth.value * 0.60f).coerceIn(14f, 72f).sp
        val iconSize        = (maxWidth.value * 0.65f).coerceIn(16f, 92f).dp
        val placeholderSize = (maxWidth.value * 0.85f).dp

        when {
            showPicture && !photoUri.isNullOrEmpty() -> {
                AsyncImage(
                    model = photoUri,
                    contentDescription = name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            icon != null -> {
                Icon(imageVector = icon, contentDescription = name, tint = contentColor, modifier = Modifier.size(iconSize))
            }
            showFirstLetter && hasName && isLetter -> {
                val emoji = name.take(2)
                val letter = if (emoji.isEmoji()) emoji else name.trim().take(1).uppercase()
                Text(
                    text = letter,
                    fontSize = letterFontSize,
//                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                    lineHeight = letterFontSize
                )
            }
            else -> {
                Icon(
                    painter = painterResource(id = R.drawable.ic_person),
                    contentDescription = name, tint = contentColor,
                    modifier = Modifier.size(placeholderSize)
                )
            }
        }
    }
}
