package dev.goodwy.rphone.view.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.Role.Companion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.toast

@Composable
fun RillIconButton(
    onClick: () -> Unit,
    imageVector: ImageVector,
    contentDescription: String,
) {
    val context = LocalContext.current
    Box(
        modifier =
            Modifier
                .minimumInteractiveComponentSize()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { context.toast(contentDescription) },
                    role = Role.Button,
                    interactionSource = null,
                    indication = ripple(bounded = false, radius = 20.dp),
                ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector, contentDescription)
    }
}

@Composable
fun RillTextButton(
    onClick: () -> Unit,
    text: String,
    toast: String = text,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    horizontalPadding: Dp = 12.dp,
    verticalPadding: Dp = 4.dp,
    height: Dp = 36.dp,
) {
    val context = LocalContext.current
    val contentColor = if (enabled) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .combinedClickable(
                enabled = enabled,
                indication = ripple(bounded = true),
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
                onLongClick = { if (enabled) context.toast(toast) },
                role = Role.Button
            )
            .height(height)
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}