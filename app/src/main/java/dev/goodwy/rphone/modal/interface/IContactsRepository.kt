package dev.goodwy.rphone.modal.`interface`

import android.accounts.Account
import android.net.Uri
import dev.goodwy.rphone.modal.data.Contact
import dev.goodwy.rphone.modal.repository.ContactsRepository.RawContactInfo

interface IContactsRepository {
    fun getContacts(includePrivate: Boolean = true): List<Contact>
    fun getContactById(contactId: String): Contact?
    fun getContactByNumber(number: String): Contact?
    fun toggleFavorite(contactId: String, isFavorite: Boolean)
    fun saveContact(contact: Contact)
    fun deleteContact(contactId: String)
    fun deleteContacts(contactIds: List<String>)
    fun moveContacts(contactIds: List<String>, accountName: String?, accountType: String?)
    fun getAvailableAccounts(): List<Account>
    fun getAvailableAccountsForMoving(): List<Account>
    fun findDuplicates(): List<List<Contact>>
    fun mergeContacts(targetContactId: String, sourceContactIds: List<String>)
    fun unmergeAllSources(contactId: String)
    fun setCustomRingtone(contactId: String, ringtoneUri: String?)
    fun formatAllPhoneNumbers(onProgress: ((current: Int, total: Int) -> Unit)? = null)
    fun setDefaultPhoneNumber(contactId: String, phoneNumber: String, isPrimary: Boolean)
    fun getRawContactsForContact(contactId: String): List<RawContactInfo>
    fun getRawContactData(rawContactId: String): Contact?
    fun updateRawContact(rawContactId: String, contact: Contact)
    fun dumpContact(contactId: String): String

    // Private Contacts
    fun makeContactPrivate(contactId: String)
    fun makeContactPublic(contactId: String)
    fun exportPrivateContacts(uri: Uri)
    fun importPrivateContacts(uri: Uri)
}