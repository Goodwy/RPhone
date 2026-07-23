package dev.goodwy.rphone.view.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ManageSearch
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.PreferenceManager
import org.koin.compose.koinInject

/**
 * Snapshot of the persisted "Filter" checkboxes shown beside the search bar on the Dialpad,
 * Calls, Contacts, and Favourites screens. Every field defaults to true (ticked) — matching
 * [PreferenceManager.getBoolean]'s default-value semantics — so a fresh install searches
 * everything until the user deliberately narrows it down. The choice is stored in
 * [PreferenceManager] (SharedPreferences), so it survives the app being closed and reopened.
 */
data class SearchFilterState(
    val contacts: Boolean = true,
    val nonContacts: Boolean = true,
    val recordings: Boolean = true,
    val contactNotes: Boolean = true,
    val recordingNotes: Boolean = true,
) {
    val isDefault: Boolean get() = contacts && nonContacts && recordings && contactNotes && recordingNotes
}

fun PreferenceManager.getSearchFilterState(): SearchFilterState = SearchFilterState(
    contacts = getBoolean(PreferenceManager.KEY_SEARCH_FILTER_CONTACTS, true),
    nonContacts = getBoolean(PreferenceManager.KEY_SEARCH_FILTER_NON_CONTACTS, true),
    recordings = getBoolean(PreferenceManager.KEY_SEARCH_FILTER_RECORDINGS, true),
    contactNotes = getBoolean(PreferenceManager.KEY_SEARCH_FILTER_CONTACT_NOTES, true),
    recordingNotes = getBoolean(PreferenceManager.KEY_SEARCH_FILTER_RECORDING_NOTES, true),
)

/**
 * The round "Filter" button that sits to the right of a search bar. Tapping it opens a
 * checklist (Contacts / Non contacts / Contact notes / Recording notes); every toggle is
 * written straight to [PreferenceManager] so it's remembered even after the app is fully
 * closed and reopened. Callers read the current selection with
 * `koinInject<PreferenceManager>().getSearchFilterState()`, recomputed off
 * `prefs.settingsChanged` so results update live as boxes are (un)checked.
 */
@Composable
fun SearchFilterButton(modifier: Modifier = Modifier, size: Dp = 52.dp) {
    val prefs = koinInject<PreferenceManager>()
    var expanded by remember { mutableStateOf(false) }
    val settingsVer by prefs.settingsChanged.collectAsState()
    val state = remember(settingsVer) { prefs.getSearchFilterState() }

    Box(modifier = modifier) {
        IconButton(
            onClick = { expanded = true }
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ManageSearch,
                contentDescription = stringResource(R.string.filter_results),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(20.dp)
        ) {
            Text(
                stringResource(R.string.filter_results),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )
            SearchFilterCheckRow(
                label = stringResource(R.string.contacts),
                checked = state.contacts,
                onCheckedChange = { prefs.setBoolean(PreferenceManager.KEY_SEARCH_FILTER_CONTACTS, it) }
            )
            SearchFilterCheckRow(
                label = stringResource(R.string.calls),
                checked = state.nonContacts,
                onCheckedChange = { prefs.setBoolean(PreferenceManager.KEY_SEARCH_FILTER_NON_CONTACTS, it) }
            )
            SearchFilterCheckRow(
                label = stringResource(R.string.call_notes),
                checked = state.contactNotes,
                onCheckedChange = { prefs.setBoolean(PreferenceManager.KEY_SEARCH_FILTER_CONTACT_NOTES, it) }
            )
        }
    }
}

@Composable
private fun SearchFilterCheckRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    DropdownMenuItem(
        text = {
            Row(
                modifier = Modifier.padding(start = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(label, modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.width(12.dp))
                Checkbox(checked = checked, onCheckedChange = onCheckedChange)
            }
        },
        onClick = { onCheckedChange(!checked) }
    )
}
