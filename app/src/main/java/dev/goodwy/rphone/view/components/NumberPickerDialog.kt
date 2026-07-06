package dev.goodwy.rphone.view.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.goodwy.rphone.cardCornerBig
import dev.goodwy.rphone.cardCornerMedium
import dev.goodwy.rphone.cardCornerSmall

@Composable
fun NumberPickerDialog(
    numbers: List<String>,
    onDismissRequest: () -> Unit,
    onNumberSelected: (String) -> Unit
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .wrapContentHeight()
                .padding(top = 100.dp),
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(
                modifier = Modifier
                    .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 8.dp)
            ) {
                Text(
                    text = "Select Number",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    itemsIndexed(
                        items = numbers,
                        contentType = { _, _ -> "number" }
                    ) { index, number ->
                        val isOnly   = numbers.size == 1
                        val isFirst  = index == 0
                        val isLast   = index == numbers.lastIndex

                        val shape = when {
                            isOnly  -> RoundedCornerShape(cardCornerBig)
                            isFirst -> RoundedCornerShape(
                                topStart = cardCornerMedium, topEnd = cardCornerMedium,
                                bottomStart = cardCornerSmall, bottomEnd = cardCornerSmall
                            )
                            isLast  -> RoundedCornerShape(
                                topStart = cardCornerSmall, topEnd = cardCornerSmall,
                                bottomStart = cardCornerMedium, bottomEnd = cardCornerMedium
                            )
                            else    -> RoundedCornerShape(cardCornerSmall)
                        }

                        Surface(
                            onClick = { onNumberSelected(number) },
                            shape = shape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    modifier = Modifier.size(48.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.Phone,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                Text(
                                    text = number,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = onDismissRequest,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}
