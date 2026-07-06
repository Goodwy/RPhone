package dev.goodwy.rphone.controller

import android.accounts.Account
import android.app.Application
import dev.goodwy.rphone.modal.`interface`.IContactsRepository
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.device_only
import dev.goodwy.rphone.modal.data.Contact
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

    private val _showOnlyDeviceContacts = MutableStateFlow(false)
    val showOnlyDeviceContacts = _showOnlyDeviceContacts.asStateFlow()

//    val filteredContacts = combine(_allContacts, _selectedAccount) { contacts, account ->
//        if (account == null) {
//            contacts
//        } else if (account.name == "device_only") {
//            contacts.filter { it.accountName == null && it.accountType == null }
//        } else {
//            contacts.filter { it.accountName == account.name && it.accountType == account.type }
//        }
//    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredContacts = combine(_allContacts, _selectedAccount, _showOnlyDeviceContacts) {
            contacts, account, showDeviceOnly ->
        when {
            showDeviceOnly -> contacts.filter { it.accountName == null && it.accountType == null }
            account == null -> contacts
            else -> contacts.filter { it.accountName == account.name && it.accountType == account.type }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val groupedContacts = filteredContacts.combine(MutableStateFlow(Unit)) { contacts, _ ->
        val mainGroups = contacts.groupBy {
            val firstChar = it.name.firstOrNull()?.uppercaseChar() ?: '#'
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

//    fun selectAccount(account: Account?) {
//        _selectedAccount.value = account
//    }
    fun selectAccount(account: Account?) {
        _selectedAccount.value = account
        if (account != null) {
            _showOnlyDeviceContacts.value = false
        }
    }

    fun setShowOnlyDeviceContacts(show: Boolean) {
        _showOnlyDeviceContacts.value = show
        if (show) {
            _selectedAccount.value = null
        }
    }

    fun toggleFavorite(contact: Contact, add: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            contactsRepo.toggleFavorite(contact.id, if (add) true else !contact.isFavorite)
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

            preferenceManager.setString(PreferenceManager.KEY_LAST_USED_ACCOUNT_NAME, contact.accountName ?: device_only)
            preferenceManager.setString(PreferenceManager.KEY_LAST_USED_ACCOUNT_TYPE, contact.accountType ?: device_only)

            fetchContacts()
        }
    }

    fun getLastUsedAccount(): Account? {
//        val name = preferenceManager.getString(PreferenceManager.KEY_LAST_USED_ACCOUNT_NAME, "-1")
//        val type = preferenceManager.getString(PreferenceManager.KEY_LAST_USED_ACCOUNT_TYPE, "-1")
//        return if (name != null && type != null) {
//            Account(name, type)
//        } else {
//            null
//        }
        val lastUsedName = preferenceManager.getString(PreferenceManager.KEY_LAST_USED_ACCOUNT_NAME, null)
        val lastUsedType = preferenceManager.getString(PreferenceManager.KEY_LAST_USED_ACCOUNT_TYPE, null)

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
            fetchContacts()
        }
    }

    fun deleteContacts(contactIds: List<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            contactsRepo.deleteContacts(contactIds)
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
