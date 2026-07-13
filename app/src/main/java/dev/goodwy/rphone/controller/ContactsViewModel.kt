package dev.goodwy.rphone.controller

import android.accounts.Account
import android.app.Application
import android.net.Uri
import dev.goodwy.rphone.modal.`interface`.IContactsRepository
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.device_only
import dev.goodwy.rphone.modal.data.Contact
import dev.goodwy.rphone.private_only
import dev.goodwy.rphone.view.screen.settings.NumberChangeExample
import dev.goodwy.rphone.view.screen.settings.StandardizeStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ContactsViewModel(
    application: Application,
    private val contactsRepo: IContactsRepository,
    private val preferenceManager: PreferenceManager
) : AndroidViewModel(application) {

    private val _allContacts = MutableStateFlow<List<Contact>>(emptyList())
    val allContacts: StateFlow<List<Contact>> = _allContacts.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _standardizeProgress = MutableStateFlow<Float?>(null)
    val standardizeProgress: StateFlow<Float?> = _standardizeProgress.asStateFlow()

    private val _availableAccounts = MutableStateFlow<List<Account>>(emptyList())
    val availableAccounts = _availableAccounts.asStateFlow()

    private val _availableAccountsForMoving = MutableStateFlow<List<Account>>(emptyList())
    val availableAccountsForMoving = _availableAccountsForMoving.asStateFlow()

    private val _selectedAccount = MutableStateFlow<Account?>(null)
    val selectedAccount = _selectedAccount.asStateFlow()

    private val _showPrivateOnly = MutableStateFlow(false)
    val showPrivateOnly = _showPrivateOnly.asStateFlow()

    private val _showLocalOnly = MutableStateFlow(false)
    val showLocalOnly = _showLocalOnly.asStateFlow()

    private val _visibleAccounts = MutableStateFlow<Set<String>?>(preferenceManager.getVisibleAccounts())
    val visibleAccountsFlow = _visibleAccounts.asStateFlow()

    private val _sortOrder = MutableStateFlow(preferenceManager.getInt(PreferenceManager.KEY_CONTACT_SORT_ORDER, 0))
    val sortOrder = _sortOrder.asStateFlow()

    private val _displayOrder = MutableStateFlow(preferenceManager.getInt(PreferenceManager.KEY_CONTACT_DISPLAY_ORDER, 0))
    val displayOrder = _displayOrder.asStateFlow()

    val filteredAvailableAccounts: StateFlow<List<Account>> = combine(
        _availableAccounts,
        _visibleAccounts
    ) { accounts, visibleAccounts ->
        if (visibleAccounts == null) {
            accounts
        } else {
            accounts.filter { account ->
                val key = "${account.type}|${account.name}"
                visibleAccounts.contains(key)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredContacts = combine(
        _allContacts,
        _selectedAccount,
        _showPrivateOnly,
        _showLocalOnly,
        _visibleAccounts,
        _sortOrder
    ) { args ->
        val contacts = args[0] as List<Contact>
        val account = args[1] as Account?
        val privateOnly = args[2] as Boolean
        val localOnly = args[3] as Boolean
        val visibleAccounts = args[4] as Set<String>?
        val sortOrder = args[5] as Int

        val baseFiltered = when {
            privateOnly -> contacts.filter { it.isPrivate }
            localOnly -> contacts.filter { it.accountName == null && it.accountType == null && !it.isPrivate }
            account == null -> {
                if (visibleAccounts == null) contacts
                else contacts.filter { contact ->
                    val key = if (contact.accountType == null && contact.accountName == null) "local|local" else "${contact.accountType}|${contact.accountName}"
                    visibleAccounts.contains(key) || contact.isPrivate
                }
            }
            else -> contacts.filter { it.accountName == account.name && it.accountType == account.type }
        }

        if (sortOrder == 1) {
            baseFiltered.sortedBy {
                it.familyName.lowercase().ifBlank { it.displayName.lowercase() }
            }
        } else {
            baseFiltered.sortedBy {
                it.givenName.lowercase().ifBlank { it.displayName.lowercase() }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val groupedContacts = combine(filteredContacts, _sortOrder) { contacts, sortOrder ->
        val mainGroups = contacts.groupBy {
            val nameToUse = if (sortOrder == 1) {
                it.familyName.ifBlank { it.displayName }
            } else {
                it.givenName.ifBlank { it.displayName }
            }
            val firstChar = nameToUse.firstOrNull()?.uppercaseChar() ?: '#'
            if (firstChar.isLetter()) firstChar else '#'
        }.toMutableMap()

        val finalMap = linkedMapOf<Char, List<Contact>>()

        mainGroups.keys.filter { it.isLetter() }.sorted().forEach { char ->
            finalMap[char] = mainGroups[char]!!
        }

        val hashGroup = mainGroups['#']
        if (hashGroup != null) finalMap['#'] = hashGroup

        finalMap
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    init {
        fetchAccounts()
    }

    fun fetchContacts() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            val result = contactsRepo.getContacts()
            _allContacts.value = result
            _isLoading.value = false
        }
    }

    suspend fun getFullContactById(contactId: String): Contact? {
        return withContext(Dispatchers.IO) {
            contactsRepo.getContactById(contactId)
        }
    }

    suspend fun getFullContactByNumber(number: String): Contact? {
        return withContext(Dispatchers.IO) {
            contactsRepo.getContactByNumber(number)
        }
    }

    fun fetchAccounts() {
        viewModelScope.launch(Dispatchers.IO) {
            _availableAccounts.value = contactsRepo.getAvailableAccounts()
            _availableAccountsForMoving.value = contactsRepo.getAvailableAccountsForMoving()
        }
    }

    fun selectAccount(account: Account?) {
        _selectedAccount.value = account
        if (account != null) {
            _showPrivateOnly.value = false
            _showLocalOnly.value = false
        }
    }
    fun setShowPrivateOnly(show: Boolean) {
        _showPrivateOnly.value = show
        if (show) {
            _selectedAccount.value = null
            _showLocalOnly.value = false
        }
    }

    fun setShowLocalOnly(show: Boolean) {
        _showLocalOnly.value = show
        if (show) {
            _selectedAccount.value = null
            _showPrivateOnly.value = false
        }
    }

    fun setVisibleAccounts(accounts: Set<String>?) {
        if (accounts == null) {
            preferenceManager.setString(PreferenceManager.KEY_VISIBLE_ACCOUNTS, null)
        } else {
            preferenceManager.setVisibleAccounts(accounts)
        }
        _visibleAccounts.value = accounts
    }

    fun setSortOrder(order: Int) {
        preferenceManager.setInt(PreferenceManager.KEY_CONTACT_SORT_ORDER, order)
        _sortOrder.value = order
    }

    fun setDisplayOrder(order: Int) {
        preferenceManager.setInt(PreferenceManager.KEY_CONTACT_DISPLAY_ORDER, order)
        _displayOrder.value = order
    }

    fun toggleFavorite(contact: Contact, add: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            val newFavStatus = !contact.isFavorite
            contactsRepo.toggleFavorite(contact.id, if (add) true else newFavStatus)

            val currentOrder = preferenceManager.getFavoritesOrder().toMutableList()
            if (newFavStatus) {
                if (!currentOrder.contains(contact.id)) {
                    currentOrder.add(contact.id)
                    preferenceManager.setFavoritesOrder(currentOrder)
                }
            } else {
                if (currentOrder.contains(contact.id)) {
                    currentOrder.remove(contact.id)
                    preferenceManager.setFavoritesOrder(currentOrder)
                }
            }

            fetchContacts()
        }
    }

    fun setFavorite(contact: Contact, isFavorite: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            contactsRepo.toggleFavorite(contact.id, isFavorite)
            fetchContacts()
        }
    }

    suspend fun saveContact(contact: Contact) {
        withContext(Dispatchers.IO) {
            contactsRepo.saveContact(contact)

            if (contact.isPrivate) {
                preferenceManager.setString(PreferenceManager.KEY_LAST_USED_ACCOUNT_NAME, contact.accountName ?: private_only)
                preferenceManager.setString(PreferenceManager.KEY_LAST_USED_ACCOUNT_TYPE, contact.accountType ?: private_only)
            } else {
                preferenceManager.setString(PreferenceManager.KEY_LAST_USED_ACCOUNT_NAME, contact.accountName ?: device_only)
                preferenceManager.setString(PreferenceManager.KEY_LAST_USED_ACCOUNT_TYPE, contact.accountType ?: device_only)
            }


            fetchContacts()
        }
    }

    fun getLastUsedAccount(): Account? {
        val lastUsedName = preferenceManager.getString(PreferenceManager.KEY_LAST_USED_ACCOUNT_NAME, null)
        val lastUsedType = preferenceManager.getString(PreferenceManager.KEY_LAST_USED_ACCOUNT_TYPE, null)

        if (lastUsedName == private_only && lastUsedType == private_only) {
            return Account(private_only, private_only)
        }

        if (lastUsedName == device_only && lastUsedType == device_only) {
            return null
        }

        if (lastUsedName != null && lastUsedType != null) {
            val lastUsed = Account(lastUsedName, lastUsedType)
            if (_availableAccountsForMoving.value.contains(lastUsed)) {
                return lastUsed
            }
        }

        val mostPopular = getAccountWithMostContacts()
        if (mostPopular != null) {
            return mostPopular
        }

        return null
    }

    private fun getAccountWithMostContacts(): Account? {
        val accounts = _availableAccountsForMoving.value
        if (accounts.isEmpty()) return null

        val accountCounts = mutableMapOf<String, Account>()
        val counts = mutableMapOf<String, Int>()

        _allContacts.value.forEach { contact ->
            if (contact.accountName != null && contact.accountType != null) {
                val key = "${contact.accountName}|${contact.accountType}"
                counts[key] = counts.getOrDefault(key, 0) + 1
                accountCounts[key] = Account(contact.accountName, contact.accountType)
            }
        }

        val maxEntry = counts.maxByOrNull { it.value }
        return maxEntry?.let { accountCounts[it.key] }
    }

    fun deleteContact(contactId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            contactsRepo.deleteContact(contactId)

            val currentOrder = preferenceManager.getFavoritesOrder().toMutableList()
            if (currentOrder.contains(contactId)) {
                currentOrder.remove(contactId)
                preferenceManager.setFavoritesOrder(currentOrder)
            }

            fetchContacts()
        }
    }

    fun deleteContacts(contactIds: List<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            contactsRepo.deleteContacts(contactIds)

            val currentOrder = preferenceManager.getFavoritesOrder().toMutableList()
            var changed = false
            contactIds.forEach { id ->
                if (currentOrder.contains(id)) {
                    currentOrder.remove(id)
                    changed = true
                }
            }
            if (changed) {
                preferenceManager.setFavoritesOrder(currentOrder)
            }

            fetchContacts()
        }
    }

    fun moveContacts(contactIds: List<String>, account: Account?) {
        viewModelScope.launch(Dispatchers.IO) {
            contactsRepo.moveContacts(contactIds, account?.name, account?.type)
            fetchContacts()
        }
    }

    fun findDuplicates(onResult: (List<List<Contact>>) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val duplicates = contactsRepo.findDuplicates()
            withContext(Dispatchers.Main) {
                onResult(duplicates)
            }
        }
    }

    fun mergeContacts(targetId: String, sourceIds: List<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            contactsRepo.mergeContacts(targetId, sourceIds)
            fetchContacts()
        }
    }

    fun setCustomRingtone(contactId: String, ringtoneUri: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            contactsRepo.setCustomRingtone(contactId, ringtoneUri)
        }
    }

    fun formatAllPhoneNumbers() {
        viewModelScope.launch(Dispatchers.IO) {
            _standardizeProgress.value = 0f
            contactsRepo.formatAllPhoneNumbers { current, total ->
                _standardizeProgress.value = if (total > 0) current.toFloat() / total else 1f
            }
            fetchContacts()
            _standardizeProgress.value = null
        }
    }

    fun previewStandardize(onResult: (StandardizeStats) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val allContacts = contactsRepo.getContacts()
            var contactsWithChanges = 0
            var totalNumbersChanged = 0
            val examples = mutableListOf<NumberChangeExample>()

            allContacts.forEach { contact ->
                val formattedPhoneNumbers = contact.phoneNumbers.map { it.replace(" ", "") }
                val changed = contact.phoneNumbers.zip(formattedPhoneNumbers)
                    .filter { it.first != it.second }

                if (changed.isNotEmpty()) {
                    contactsWithChanges++
                    totalNumbersChanged += changed.size

                    if (examples.size < 3) {
                        changed.take(3).forEach { (oldNum, newNum) ->
                            examples.add(
                                NumberChangeExample(
                                    contactName = contact.displayName,
                                    oldNumber = oldNum,
                                    newNumber = newNum
                                )
                            )
                        }
                    }
                }
            }

            val stats = StandardizeStats(
                totalContacts = allContacts.size,
                contactsWithChanges = contactsWithChanges,
                totalNumbersChanged = totalNumbersChanged,
                examples = examples
            )

            withContext(Dispatchers.Main) {
                onResult(stats)
            }
        }
    }

    fun makeContactPrivate(contactId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            contactsRepo.makeContactPrivate(contactId)
            fetchContacts()
        }
    }

    fun makeContactPublic(contactId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            contactsRepo.makeContactPublic(contactId)
            fetchContacts()
        }
    }

    fun exportPrivateContacts(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            contactsRepo.exportPrivateContacts(uri)
        }
    }

    fun importPrivateContacts(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            contactsRepo.importPrivateContacts(uri)
            fetchContacts()
        }
    }

    // Goodwy
    fun setDefaultPhoneNumber(contactId: String, phoneNumber: String, isPrimary: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            contactsRepo.setDefaultPhoneNumber(contactId, phoneNumber, isPrimary)
            refreshContacts()
        }
    }

    private fun refreshContacts() {
        viewModelScope.launch(Dispatchers.IO) {
            val freshContacts = contactsRepo.getContacts()
            _allContacts.value = freshContacts
        }
    }

    val contactCountByAccount: Map<String, Int>
        get() {
            val map = mutableMapOf<String, Int>()
            _allContacts.value.forEach { contact ->
                val key = "${contact.accountName}|${contact.accountType}"
                if (contact.accountName != null) {
                    map[key] = map.getOrDefault(key, 0) + 1
                }
            }
            return map
        }
}
