package dev.goodwy.rphone.view.components

import android.accounts.Account
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.ContactsViewModel
import dev.goodwy.rphone.controller.util.ContactUtils
import dev.goodwy.rphone.modal.data.Contact

@Composable
fun MoveToAccountDialog(
    title: String = stringResource(R.string.move_to_another_account),
    icon: ImageVector? = Icons.AutoMirrored.Rounded.DriveFileMove,
    availableAccounts: List<Account>,
    currentAccountKey: String? = "-1", // null is a local account
    onDismiss: () -> Unit,
    onAccountSelected: (Account?) -> Unit
) {
    RillDialog(
        onDismissRequest = onDismiss,
        title = title,
        icon = icon,
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    ) {
        Surface(
            onClick = {
                if (currentAccountKey != null) {
                    onAccountSelected(null)
                }
            },
            shape = RoundedCornerShape(16.dp),
            color = if (currentAccountKey == null) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp, horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            ContactUtils.getAccountIcon(null),
                            ContactUtils.getAccountType(null),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        ContactUtils.getAccountType(null),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        availableAccounts.forEachIndexed { index, account ->
            val isCurrentAccount = currentAccountKey == "${account.name}|${account.type}"

            Surface(
                onClick = {
                    if (!isCurrentAccount) {
                        onAccountSelected(account)
                    }
                },
                shape = RoundedCornerShape(16.dp),
                color = if (isCurrentAccount) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                ContactUtils.getAccountIcon(account),
                                null,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            ContactUtils.getAccountName(account),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            ContactUtils.getAccountType(account),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MoveSingleContactDialog(
    contact: Contact,
    availableAccounts: List<Account>,
    currentAccountKey: String? = null,
    contactsViewModel: ContactsViewModel,
    onDismiss: () -> Unit,
    onSuccess: (Account?) -> Unit = {}
) {
    MoveToAccountDialog(
        availableAccounts = availableAccounts,
        currentAccountKey = currentAccountKey,
        onDismiss = onDismiss,
        onAccountSelected = { account ->
            val list = listOf(contact.id)
            contactsViewModel.moveContacts(list, account)
            onSuccess(account)
            onDismiss()
        }
    )
}

@Composable
fun MoveMultipleContactsDialog(
    contactIds: List<String>,
    availableAccounts: List<Account>,
    currentAccountKey: String? = null,
    contactsViewModel: ContactsViewModel,
    onDismiss: () -> Unit,
    onSuccess: (Account?) -> Unit = {}
) {
    MoveToAccountDialog(
        availableAccounts = availableAccounts,
        currentAccountKey = currentAccountKey,
        onDismiss = onDismiss,
        onAccountSelected = { account ->
            contactsViewModel.moveContacts(contactIds, account)
            onSuccess(account)
            onDismiss()
        }
    )
}