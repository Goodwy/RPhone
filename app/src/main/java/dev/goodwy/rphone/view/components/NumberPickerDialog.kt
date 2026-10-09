package dev.goodwy.rphone.view.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.R
import dev.goodwy.rphone.cardCornerExtraSmall
import dev.goodwy.rphone.view.theme.MyColors.cardColor

data class NumberPickerItem(
    val number: String,
    val primary: Boolean = false
)

@Composable
fun NumberPickerDialog(
    numbers: List<String>,
    onDismissRequest: () -> Unit,
    onNumberSelected: (String) -> Unit,
    icon: ImageVector = Icons.Rounded.Phone
) {
    NumberPickerDialog(
        items = numbers.map { NumberPickerItem(it) },
        onDismissRequest = onDismissRequest,
        onNumberSelected = onNumberSelected,
        icon = icon
    )
}

@Composable
@JvmName("NumberPickerDialogItems")
fun NumberPickerDialog(
    items: List<NumberPickerItem>,
    onDismissRequest: () -> Unit,
    onNumberSelected: (String) -> Unit,
    icon: ImageVector = Icons.Rounded.Phone
) {
    RillDialog(
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.select),
        icon = Icons.Rounded.Check,
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.cancel))
            }
        }
    ) {
        RillExpressiveCard(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 600.dp),
            shape = MaterialTheme.shapes.large
        ) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                itemsIndexed(
                    items = items,
                    contentType = { _, _ -> "number" }
                ) { _, item ->
                    Surface(
                        onClick = { onNumberSelected(item.number) },
                        shape = RoundedCornerShape(cardCornerExtraSmall),
                        color = cardColor,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier.size(42.dp),
                                shape = RoundedCornerShape(50),
                                color = if (item.primary) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        if (item.primary) ImageVector.vectorResource(id = R.drawable.ic_phone_star) else icon,
                                        contentDescription = null,
                                        tint = if (item.primary) MaterialTheme.colorScheme.onPrimaryContainer
                                            else MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Text(
                                text = item.number,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}
