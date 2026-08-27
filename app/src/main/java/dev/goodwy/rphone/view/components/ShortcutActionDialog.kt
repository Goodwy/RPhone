package dev.goodwy.rphone.view.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.R
import dev.goodwy.rphone.cardCornerMedium
import dev.goodwy.rphone.view.theme.MyColors.cardColor

@Composable
fun ShortcutActionDialog(
    onOpenContactInfo: () -> Unit,
    onCallDirectly: () -> Unit,
    onDismiss: () -> Unit
) {
    RillDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.add_to_home_screen),
        icon = Icons.Rounded.Widgets,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    ) {
        RillExpressiveCard(shape = RoundedCornerShape(cardCornerMedium)) {
            ShortcutActionRow(
                icon = Icons.Rounded.AccountCircle,
                label = stringResource(R.string.view_contact),
                subLabel = stringResource(R.string.shortcut_view_contact_subtitle),
                onClick = onOpenContactInfo
            )
            ShortcutActionRow(
                icon = Icons.Rounded.Call,
                label = stringResource(R.string.call),
                subLabel = stringResource(R.string.shortcut_call_subtitle),
                onClick = onCallDirectly
            )
        }
    }
}

@Composable
private fun ShortcutActionRow(
    icon: ImageVector,
    label: String,
    subLabel: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(cardColor)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Column {
            Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(2.dp))
            Text(subLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}