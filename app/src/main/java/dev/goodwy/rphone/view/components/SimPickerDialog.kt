package dev.goodwy.rphone.view.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.SimCard
import androidx.compose.material.icons.rounded.SimCardAlert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import dev.goodwy.rphone.cardCornerExtraSmall
import dev.goodwy.rphone.R
import dev.goodwy.rphone.view.theme.MyColors.cardColor

@Composable
fun SimPickerDialog(
    onDismissRequest: () -> Unit,
    onSimSelected: (PhoneAccountHandle) -> Unit
) {
    val context = LocalContext.current
    val telecomManager = remember { context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager }
    
    val phoneAccounts = remember {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            try {
                val raw = telecomManager.callCapablePhoneAccounts
                raw.filter { handle ->
                    try {
                        telecomManager.getPhoneAccount(handle) != null
                    } catch (_: Exception) {
                        false
                    }

                }.distinct()
            } catch (e: SecurityException) {
                emptyList()
            }
        } else emptyList()
    }

    if (phoneAccounts.isNotEmpty()) {
        RillDialog(
            onDismissRequest = onDismissRequest,
            title = stringResource(R.string.sim_picker_title),
            icon = Icons.Rounded.SimCardAlert,
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
                    itemsIndexed(phoneAccounts) { index, accountHandle ->
                        val info = try {
                            telecomManager.getPhoneAccount(accountHandle)
                        } catch (_: Exception) {
                            null
                        }

                        val simNumber = index + 1
                        val operatorName = info?.label?.toString()
                        val displayLabel = if (!operatorName.isNullOrEmpty()) {
                            if (operatorName.startsWith("SIM", ignoreCase = true)) {
                                operatorName
                            } else {
                                "SIM $simNumber: $operatorName"
                            }
                        } else {
                            "SIM $simNumber"
                        }

                        Surface(
                            onClick = { onSimSelected(accountHandle) },
                            shape = RoundedCornerShape(cardCornerExtraSmall),
                            color = cardColor,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    modifier = Modifier.size(48.dp),
                                    shape = RoundedCornerShape(50),
                                    color = MaterialTheme.colorScheme.secondaryContainer
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Rounded.SimCard,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                Column {
                                    Text(
                                        text = displayLabel,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    val description = info?.shortDescription?.toString()
                                    if (!description.isNullOrEmpty() && description != operatorName) {
                                        Text(
                                            text = description,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    } else {
        SideEffect {
            onDismissRequest()
        }
    }
}
