package dev.goodwy.rphone.modal.`interface`

import android.accounts.Account
import android.net.Uri
import dev.goodwy.rphone.modal.data.Contact
import dev.goodwy.rphone.modal.repository.ContactsRepository.RawContactInfo

interface IContactsRepository {
    suspend fun getContacts(includePrivate: Boolean = true): List<Contact>
    suspend fun getContactById(contactId: String): Contact?
    suspend fun getContactByNumber(number: String): Contact?
    suspend fun toggleFavorite(contactId: String, isFavorite: Boolean)
    suspend fun saveContact(contact: Contact)
    suspend fun deleteContact(contactId: String)
    suspend fun deleteContacts(contactIds: List<String>)
    suspend fun moveContacts(contactIds: List<String>, accountName: String?, accountType: String?)
    suspend fun getAvailableAccounts(): List<Account>
    suspend fun getAvailableAccountsForMoving(): List<Account>
    suspend fun findDuplicates(): List<List<Contact>>
    suspend fun mergeContacts(targetContactId: String, sourceContactIds: List<String>)
    suspend fun unmergeAllSources(contactId: String)
    suspend fun setCustomRingtone(contactId: String, ringtoneUri: String?)
    suspend fun formatAllPhoneNumbers(onProgress: ((current: Int, total: Int) -> Unit)? = null)
    suspend fun setDefaultPhoneNumber(contactId: String, phoneNumber: String, isPrimary: Boolean)
    suspend fun getRawContactsForContact(contactId: String): List<RawContactInfo>
    suspend fun getRawContactData(rawContactId: String): Contact?
    suspend fun updateRawContact(rawContactId: String, contact: Contact)
    suspend fun dumpContact(contactId: String): String

    // Private Contacts
    suspend fun makeContactPrivate(contactId: String)
    suspend fun makeContactPublic(contactId: String)
    suspend fun exportPrivateContacts(uri: Uri)
    suspend fun importPrivateContacts(uri: Uri)
}