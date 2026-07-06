package dev.goodwy.rphone.view.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun PlaceholderView(
    icon: ImageVector,
    title: String,
    description: String? = null
) {
    var visible by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.8f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "permScale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(400),
        label = "permAlpha"
    )
    LaunchedEffect(Unit) { visible = true }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(40.dp)
            .alpha(alpha),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, modifier = Modifier.size(64.dp).scale(scale), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
        Spacer(modifier = Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (description != null) Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), modifier = Modifier.padding(top = 4.dp), textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(60.dp))
    }
}
