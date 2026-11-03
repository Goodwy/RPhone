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
import dev.goodwy.rphone.modal.data.LOCAL_SOURCE_KEY
import dev.goodwy.rphone.modal.data.PRIVATE_SOURCE_KEY
import dev.goodwy.rphone.modal.data.accountSourceKey
import kotlinx.coroutines.launch

@Composable
fun MoveToAccountDialog(
    title: String = stringResource(R.string.move_to_another_account),
    icon: ImageVector? = Icons.AutoMirrored.Rounded.DriveFileMove,
    availableAccounts: List<Account>,
    localAllowed: Boolean,
    currentAccountKey: String? = "-1",
    onDismiss: () -> Unit,
    onAccountSelected: (Account?, isPrivate: Boolean) -> Unit
) {
    RillDialog(
        onDismissRequest = onDismiss,
        title = title,
        icon = icon,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    ) {
        Surface(
            onClick = {
                if (currentAccountKey != PRIVATE_SOURCE_KEY) {
                    onAccountSelected(null, true)
                }
            },
            shape = RoundedCornerShape(16.dp),
            color = if (currentAccountKey == PRIVATE_SOURCE_KEY) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
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
                            ContactUtils.getAccountIcon(null, true),
                            ContactUtils.getFriendlyAccountName(null, true),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        ContactUtils.getFriendlyAccountName(null, true),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        if (localAllowed) Surface(
            onClick = {
                if (currentAccountKey != LOCAL_SOURCE_KEY) {
                    onAccountSelected(null, false)
                }
            },
            shape = RoundedCornerShape(16.dp),
            color = if (currentAccountKey == LOCAL_SOURCE_KEY) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
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
                            ContactUtils.getFriendlyAccountName(null),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        ContactUtils.getFriendlyAccountName(null),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        availableAccounts.forEachIndexed { index, account ->
            val isCurrentAccount = currentAccountKey == accountSourceKey(account.type, account.name)

            Surface(
                onClick = {
                    if (!isCurrentAccount) {
                        onAccountSelected(account, false)
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
                            ContactUtils.getFriendlyAccountName(account),
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
    onSuccess: (Account?, isPrivate: Boolean, newContactId: String) -> Unit = {_, _, _ -> }
) {
    val scope = rememberCoroutineScope()
    MoveToAccountDialog(
        availableAccounts = availableAccounts,
        localAllowed = contactsViewModel.canCreateLocalContacts(),
        currentAccountKey = currentAccountKey,
        onDismiss = onDismiss,
        onAccountSelected = { account, isPrivate ->
            scope.launch {
                if (isPrivate) {
                    contactsViewModel.makeContactPrivate(contact.id)
                    val newId = contact.phoneNumbers.firstNotNullOfOrNull { number ->
                        contactsViewModel.getFullContactByNumber(number)?.id
                            ?.takeIf { it.startsWith("p") }
                    } ?: contact.id
                    onSuccess(null, true, newId)
                } else {
                    val newIdsMap = contactsViewModel.moveContacts(listOf(contact.id), account)
                    val finalId = newIdsMap[contact.id] ?: contact.id
                    onSuccess(account, false, finalId)
                }
                onDismiss()
            }
        }
    )
}

@Composable
fun MoveMultipleContactsDialog(
    contactIds: List<String>,
    availableAccounts: List<Account>,
    localAllowed: Boolean,
    currentAccountKey: String? = null,
    contactsViewModel: ContactsViewModel,
    onDismiss: () -> Unit,
    onSuccess: (Account?, isPrivate: Boolean) -> Unit = {_, _ -> }
) {
    val scope = rememberCoroutineScope()
    MoveToAccountDialog(
        availableAccounts = availableAccounts,
        localAllowed = localAllowed,
        currentAccountKey = currentAccountKey,
        onDismiss = onDismiss,
        onAccountSelected = { account, isPrivate ->
            scope.launch {
                if (isPrivate) {
                    contactIds.forEach { contactsViewModel.makeContactPrivate(it) }
                } else {
                    contactsViewModel.moveContacts(contactIds, account)
                }
                onSuccess(account, isPrivate)
                onDismiss()
            }
        }
    )
}