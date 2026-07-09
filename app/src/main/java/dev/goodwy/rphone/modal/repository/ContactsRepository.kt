package dev.goodwy.rphone.modal.repository

import android.Manifest
import android.accounts.Account
import android.accounts.AccountManager
import android.content.ContentProviderOperation
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.modal.data.Contact
import dev.goodwy.rphone.modal.data.ContactAddress
import dev.goodwy.rphone.modal.data.ContactEmail
import dev.goodwy.rphone.modal.data.ContactEvent
import dev.goodwy.rphone.modal.data.ContactPhoneDetail
import dev.goodwy.rphone.modal.db.PrivateContactDao
import dev.goodwy.rphone.modal.`interface`.IContactsRepository
import androidx.core.net.toUri
import androidx.core.graphics.scale
import dev.goodwy.rphone.controller.util.areNumbersEqual
import dev.goodwy.rphone.controller.util.copyToClipboard
import dev.goodwy.rphone.controller.util.deduplicateNumbers
import dev.goodwy.rphone.modal.db.PrivateContactEntity
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ContactsRepository(
    private val context: Context,
    private val privateContactDao: PrivateContactDao
) : IContactsRepository {

    private val contentResolver: ContentResolver = context.contentResolver
    private val preferenceManager = PreferenceManager(context)

    private fun formatName(rawName: String): String {
        return rawName
    }

    override fun getContacts(includePrivate: Boolean): List<Contact> {
        val contactsMap = mutableMapOf<String, Contact>()

        if (includePrivate) {
            privateContactDao.getAll().forEach {
                val contact = it.toContact()
                contactsMap[contact.id] = contact
            }
        }

        val projection = arrayOf(
            ContactsContract.Data.CONTACT_ID,
            ContactsContract.Data.DISPLAY_NAME_PRIMARY,
            ContactsContract.Data.PHOTO_URI,
            ContactsContract.Data.MIMETYPE,
            ContactsContract.Data.DATA1,
            ContactsContract.Data.DATA2,
            ContactsContract.Data.DATA3,
            ContactsContract.Data.DATA4,
            ContactsContract.Data.IS_PRIMARY,
            ContactsContract.Data.STARRED,
            ContactsContract.Data.RAW_CONTACT_ID,
            CommonDataKinds.StructuredName.PREFIX,
            CommonDataKinds.StructuredName.GIVEN_NAME,
            CommonDataKinds.StructuredName.MIDDLE_NAME,
            CommonDataKinds.StructuredName.FAMILY_NAME,
            CommonDataKinds.StructuredName.SUFFIX,
            CommonDataKinds.Organization.COMPANY,
            CommonDataKinds.Organization.TITLE,
            ContactsContract.RawContacts.ACCOUNT_NAME,
            ContactsContract.RawContacts.ACCOUNT_TYPE
        )

        try {
            contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                projection,
                null,
                null,
                "${ContactsContract.Data.DISPLAY_NAME_PRIMARY} ASC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(ContactsContract.Data.CONTACT_ID)
                val photoIdx = cursor.getColumnIndex(ContactsContract.Data.PHOTO_URI)
                val mimeIdx = cursor.getColumnIndex(ContactsContract.Data.MIMETYPE)
                val data1Idx = cursor.getColumnIndex(ContactsContract.Data.DATA1)
                val data2Idx = cursor.getColumnIndex(ContactsContract.Data.DATA2)
                val data3Idx = cursor.getColumnIndex(ContactsContract.Data.DATA3)
                val isPrimaryIdx = cursor.getColumnIndex(ContactsContract.Data.IS_PRIMARY)
                val starredIdx = cursor.getColumnIndex(ContactsContract.Data.STARRED)
                val rawIdIdx = cursor.getColumnIndex(ContactsContract.Data.RAW_CONTACT_ID)
                val accountNameIdx = cursor.getColumnIndex(ContactsContract.RawContacts.ACCOUNT_NAME)
                val accountTypeIdx = cursor.getColumnIndex(ContactsContract.RawContacts.ACCOUNT_TYPE)

                while (cursor.moveToNext()) {
                    val id = cursor.getString(idIdx) ?: continue
                    val mimeType = cursor.getString(mimeIdx)
                    val data1 = cursor.getString(data1Idx) ?: continue
                    val isStarred = cursor.getInt(starredIdx) == 1
                    val accountName = cursor.getString(accountNameIdx)
                    val accountType = cursor.getString(accountTypeIdx)

                    val contact = contactsMap.getOrPut(id) {
                        Contact(
                            id = id,
                            photoUri = cursor.getString(photoIdx),
                            isFavorite = isStarred,
                            accountName = accountName,
                            accountType = accountType
                        )
                    }

                    when (mimeType) {
                        CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE -> {
                            val prefixIndex = cursor.getColumnIndex(CommonDataKinds.StructuredName.PREFIX)
                            val givenNameIndex = cursor.getColumnIndex(CommonDataKinds.StructuredName.GIVEN_NAME)
                            val middleNameIndex = cursor.getColumnIndex(CommonDataKinds.StructuredName.MIDDLE_NAME)
                            val familyNameIndex = cursor.getColumnIndex(CommonDataKinds.StructuredName.FAMILY_NAME)
                            val suffixIndex = cursor.getColumnIndex(CommonDataKinds.StructuredName.SUFFIX)

                            val prefix = if (prefixIndex >= 0) cursor.getString(prefixIndex) ?: "" else ""
                            val givenName = if (givenNameIndex >= 0) cursor.getString(givenNameIndex) ?: "" else ""
                            val middleName = if (middleNameIndex >= 0) cursor.getString(middleNameIndex) ?: "" else ""
                            val familyName = if (familyNameIndex >= 0) cursor.getString(familyNameIndex) ?: "" else ""
                            val suffix = if (suffixIndex >= 0) cursor.getString(suffixIndex) ?: "" else ""

                            contactsMap[id] = contact.copy(
                                namePrefix = prefix,
                                givenName = givenName,
                                middleName = middleName,
                                familyName = familyName,
                                nameSuffix = suffix
                            )
                        }
                        CommonDataKinds.Nickname.CONTENT_ITEM_TYPE -> {
                            contactsMap[id] = contact.copy(
                                nickname = data1
                            )
                        }
                        CommonDataKinds.Phone.CONTENT_ITEM_TYPE -> {
                            val type = cursor.getInt(data2Idx)
                            val label = cursor.getString(data3Idx)
                            val isPrimary = cursor.getInt(isPrimaryIdx) == 1

                            val phoneDetail = ContactPhoneDetail(
                                number = data1,
                                type = type,
                                label = label,
                                isPrimary = isPrimary
                            )

                            contactsMap[id] = contact.copy(
                                phoneNumbers = deduplicateNumbers(contact.phoneNumbers + data1),
                                phoneDetails = (contact.phoneDetails + phoneDetail).distinctBy { it.number }
                            )
                        }
                        CommonDataKinds.Email.CONTENT_ITEM_TYPE -> {
                            val type = cursor.getInt(data2Idx)
                            val label = cursor.getString(data3Idx)
                            val email = ContactEmail(type, label, data1)
                            contactsMap[id] = contact.copy(emails = (contact.emails + email).distinct())
                        }
                        CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE -> {
                            val type = cursor.getInt(data2Idx)
                            val label = cursor.getString(data3Idx)
                            val address = ContactAddress(type, label, data1)
                            contactsMap[id] = contact.copy(addresses = (contact.addresses + address).distinct())
                        }
                        CommonDataKinds.Event.CONTENT_ITEM_TYPE -> {
                            val type = cursor.getInt(data2Idx)
                            val label = cursor.getString(data3Idx)
                            val event = ContactEvent(type, label, data1)
                            contactsMap[id] = contact.copy(events = (contact.events + event).distinct())
                        }
                        CommonDataKinds.Organization.CONTENT_ITEM_TYPE -> {
                            val companyIndex = cursor.getColumnIndex(CommonDataKinds.Organization.COMPANY)
                            val titleIndex = cursor.getColumnIndex(CommonDataKinds.Organization.TITLE)

                            val company = if (companyIndex >= 0) cursor.getString(companyIndex) ?: "" else ""
                            val jobTitle = if (titleIndex >= 0) cursor.getString(titleIndex) ?: "" else ""

                            contactsMap[id] = contact.copy(
                                company = company,
                                jobTitle = jobTitle
                            )
                        }
                    }
                }
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }

        return contactsMap.values.toList()
            .filter { it.phoneNumbers.isNotEmpty() || it.emails.isNotEmpty() }
            .sortedBy { it.displayName.lowercase() }
    }

    override fun getContactById(contactId: String): Contact? {
        if (contactId.startsWith("p")) {
            val id = contactId.substring(1).toLongOrNull() ?: return null
            return privateContactDao.getById(id)?.toContact()
        }
        val projection = arrayOf(
            ContactsContract.Data.CONTACT_ID,
            ContactsContract.Data.DISPLAY_NAME_PRIMARY,
            ContactsContract.Data.PHOTO_URI,
            ContactsContract.Data.MIMETYPE,
            ContactsContract.Data.DATA1,
            ContactsContract.Data.DATA2,
            ContactsContract.Data.DATA3,
            ContactsContract.Data.DATA4,
            ContactsContract.Data.IS_PRIMARY,
            ContactsContract.Data.STARRED,
            ContactsContract.Data.CUSTOM_RINGTONE,
            ContactsContract.Data.RAW_CONTACT_ID,
            CommonDataKinds.StructuredName.PREFIX,
            CommonDataKinds.StructuredName.GIVEN_NAME,
            CommonDataKinds.StructuredName.MIDDLE_NAME,
            CommonDataKinds.StructuredName.FAMILY_NAME,
            CommonDataKinds.StructuredName.SUFFIX,
            CommonDataKinds.Organization.COMPANY,
            CommonDataKinds.Organization.TITLE,
            ContactsContract.RawContacts.ACCOUNT_NAME,
            ContactsContract.RawContacts.ACCOUNT_TYPE
        )

        var contact: Contact? = null

        try {
            contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                projection,
                "${ContactsContract.Data.CONTACT_ID} = ?",
                arrayOf(contactId),
                null
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(ContactsContract.Data.CONTACT_ID)
                val photoIdx = cursor.getColumnIndex(ContactsContract.Data.PHOTO_URI)
                val mimeIdx = cursor.getColumnIndex(ContactsContract.Data.MIMETYPE)
                val data1Idx = cursor.getColumnIndex(ContactsContract.Data.DATA1)
                val data2Idx = cursor.getColumnIndex(ContactsContract.Data.DATA2)
                val data3Idx = cursor.getColumnIndex(ContactsContract.Data.DATA3)
                val isPrimaryIdx = cursor.getColumnIndex(ContactsContract.Data.IS_PRIMARY)
                val starredIdx = cursor.getColumnIndex(ContactsContract.Data.STARRED)
                val ringtoneIdx = cursor.getColumnIndex(ContactsContract.Data.CUSTOM_RINGTONE)
                val accountNameIdx = cursor.getColumnIndex(ContactsContract.RawContacts.ACCOUNT_NAME)
                val accountTypeIdx = cursor.getColumnIndex(ContactsContract.RawContacts.ACCOUNT_TYPE)

                while (cursor.moveToNext()) {
                    val id = cursor.getString(idIdx) ?: continue
                    val mimeType = cursor.getString(mimeIdx)
                    val data1 = cursor.getString(data1Idx) ?: continue
                    val isStarred = cursor.getInt(starredIdx) == 1
                    val ringtone = cursor.getString(ringtoneIdx)
                    val accountName = cursor.getString(accountNameIdx)
                    val accountType = cursor.getString(accountTypeIdx)

                    val currentContact = contact ?: Contact(
                        id = id,
                        photoUri = cursor.getString(photoIdx),
                        isFavorite = isStarred,
                        customRingtone = ringtone,
                        accountName = accountName,
                        accountType = accountType
                    )

                    contact = when (mimeType) {
                        CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE -> {
                            val prefixIndex = cursor.getColumnIndex(CommonDataKinds.StructuredName.PREFIX)
                            val givenNameIndex = cursor.getColumnIndex(CommonDataKinds.StructuredName.GIVEN_NAME)
                            val middleNameIndex = cursor.getColumnIndex(CommonDataKinds.StructuredName.MIDDLE_NAME)
                            val familyNameIndex = cursor.getColumnIndex(CommonDataKinds.StructuredName.FAMILY_NAME)
                            val suffixIndex = cursor.getColumnIndex(CommonDataKinds.StructuredName.SUFFIX)

                            val prefix = if (prefixIndex >= 0) cursor.getString(prefixIndex) ?: "" else ""
                            val givenName = if (givenNameIndex >= 0) cursor.getString(givenNameIndex) ?: "" else ""
                            val middleName = if (middleNameIndex >= 0) cursor.getString(middleNameIndex) ?: "" else ""
                            val familyName = if (familyNameIndex >= 0) cursor.getString(familyNameIndex) ?: "" else ""
                            val suffix = if (suffixIndex >= 0) cursor.getString(suffixIndex) ?: "" else ""

                            currentContact.copy(
                                namePrefix = prefix,
                                givenName = givenName,
                                middleName = middleName,
                                familyName = familyName,
                                nameSuffix = suffix
                            )
                        }
                        CommonDataKinds.Nickname.CONTENT_ITEM_TYPE -> {
                            currentContact.copy(nickname = data1)
                        }
                        CommonDataKinds.Phone.CONTENT_ITEM_TYPE -> {
                            val type = cursor.getInt(data2Idx)
                            val label = cursor.getString(data3Idx)
                            val isPrimary = cursor.getInt(isPrimaryIdx) == 1

                            val phoneDetail = ContactPhoneDetail(
                                number = data1,
                                type = type,
                                label = label,
                                isPrimary = isPrimary
                            )

                            currentContact.copy(
                                phoneNumbers = deduplicateNumbers(currentContact.phoneNumbers + data1),
                                phoneDetails = (currentContact.phoneDetails + phoneDetail).distinctBy { it.number }
                            )
                        }
                        CommonDataKinds.Email.CONTENT_ITEM_TYPE -> {
                            val type = cursor.getInt(data2Idx)
                            val label = cursor.getString(data3Idx)
                            val email = ContactEmail(type, label, data1)
                            currentContact.copy(emails = (currentContact.emails + email).distinct())
                        }
                        CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE -> {
                            val type = cursor.getInt(data2Idx)
                            val label = cursor.getString(data3Idx)
                            val address = ContactAddress(type, label, data1)
                            currentContact.copy(addresses = (currentContact.addresses + address).distinct())
                        }
                        CommonDataKinds.Event.CONTENT_ITEM_TYPE -> {
                            val type = cursor.getInt(data2Idx)
                            val label = cursor.getString(data3Idx)
                            val event = ContactEvent(type, label, data1)
                            currentContact.copy(events = (currentContact.events + event).distinct())
                        }
                        CommonDataKinds.Organization.CONTENT_ITEM_TYPE -> {
                            val companyIndex = cursor.getColumnIndex(CommonDataKinds.Organization.COMPANY)
                            val titleIndex = cursor.getColumnIndex(CommonDataKinds.Organization.TITLE)

                            val company = if (companyIndex >= 0) cursor.getString(companyIndex) ?: "" else ""
                            val jobTitle = if (titleIndex >= 0) cursor.getString(titleIndex) ?: "" else ""

                            currentContact.copy(
                                company = company,
                                jobTitle = jobTitle
                            )
                        }
                        else -> currentContact
                    }
                }
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
        return contact
    }

    override fun toggleFavorite(contactId: String, isFavorite: Boolean) {
        if (contactId.startsWith("p")) {
            val id = contactId.substring(1).toLongOrNull() ?: return
            privateContactDao.getById(id)?.let {
                privateContactDao.update(it.copy(isFavorite = isFavorite))
            }
            return
        }
        val contentValue = ContentValues().apply {
            put(ContactsContract.Contacts.STARRED, if (isFavorite) 1 else 0)
        }
        val updateUri = ContactsContract.Contacts.CONTENT_URI.buildUpon()
            .appendPath(contactId)
            .build()
        contentResolver.update(updateUri, contentValue, null, null)
    }

    override fun setDefaultPhoneNumber(contactId: String, phoneNumber: String, isPrimary: Boolean) {
        val rawContactId = getRawContactId(contactId) ?: return

        val ops = ArrayList<ContentProviderOperation>()

        // Reset all phone numbers for this contact
        ops.add(
            ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
                .withSelection(
                    "${ContactsContract.Data.RAW_CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
                    arrayOf(rawContactId, CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                )
                .withValue(CommonDataKinds.Phone.IS_PRIMARY, 0)
                .build()
        )

        // Set primary for selected number
        if (isPrimary) {
            ops.add(
                ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
                    .withSelection(
                        "${ContactsContract.Data.RAW_CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ? AND ${CommonDataKinds.Phone.NUMBER} = ?",
                        arrayOf(rawContactId, CommonDataKinds.Phone.CONTENT_ITEM_TYPE, phoneNumber)
                    )
                    .withValue(CommonDataKinds.Phone.IS_PRIMARY, 1)
                    .build()
            )
        }

        try {
            contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getPhotoBytes(uriString: String): ByteArray? {
        return try {
            val uri = uriString.toUri()
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            if (bitmap == null) return null

            val maxSize = 720
            val width = bitmap.width
            val height = bitmap.height

            val finalBitmap = if (width > maxSize || height > maxSize) {
                val scale = maxSize.toFloat() / width.coerceAtLeast(height)
                bitmap.scale((width * scale).toInt(), (height * scale).toInt())
            } else {
                bitmap
            }

            val outputStream = java.io.ByteArrayOutputStream()
            finalBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, outputStream)
            val bytes = outputStream.toByteArray()

            if (finalBitmap != bitmap) {
                finalBitmap.recycle()
            }
            bitmap.recycle()

            bytes
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun getRawContactIds(contactId: String): List<String> {
        val ids = mutableListOf<String>()
        try {
            contentResolver.query(
                ContactsContract.RawContacts.CONTENT_URI,
                arrayOf(ContactsContract.RawContacts._ID),
                "${ContactsContract.RawContacts.CONTACT_ID} = ?",
                arrayOf(contactId),
                null
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(ContactsContract.RawContacts._ID)
                while (cursor.moveToNext()) {
                    ids.add(cursor.getString(idIdx))
                }
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
        return ids
    }

    private fun getRawContactId(contactId: String): String? {
        return getRawContactIds(contactId).firstOrNull()
    }

    override fun saveContact(contact: Contact) {
//        if (contact.isPrivate) {
//            val entity = PrivateContactEntity.fromContact(contact)
//            if (entity.localId == 0L) {
//                privateContactDao.insert(entity)
//            } else {
//                privateContactDao.update(entity)
//            }
//            return
//        }
        if (contact.isPrivate) {
            // Check whether such a private contact already exists
            val existingPrivate = privateContactDao.getAll().find {
                it.phoneNumbersJson.contains(contact.phoneNumbers.firstOrNull() ?: "")
            }

            val entity = PrivateContactEntity.fromContact(contact)

            if (existingPrivate != null) {
                // Updating an existing one
                privateContactDao.update(entity.copy(localId = existingPrivate.localId))
            } else if (entity.localId == 0L) {
                // Let's create a new one
                privateContactDao.insert(entity)
            } else {
                // Update by ID
                privateContactDao.update(entity)
            }
            return
        }

        val ops = ArrayList<ContentProviderOperation>()
        val photoBytes = contact.photoUri?.let { getPhotoBytes(it) }

        if (contact.id.isEmpty() || contact.id == "0") {
            // New Contact
            val rawContactIndex = ops.size
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, contact.accountType)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, contact.accountName)
                    .build()
            )

            // Name with all components
            val nameBuilder = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactIndex)
                .withValue(ContactsContract.Data.MIMETYPE, CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                .withValue(CommonDataKinds.StructuredName.DISPLAY_NAME, contact.displayName)

            if (contact.namePrefix.isNotBlank()) {
                nameBuilder.withValue(CommonDataKinds.StructuredName.PREFIX, contact.namePrefix)
            }
            if (contact.givenName.isNotBlank()) {
                nameBuilder.withValue(CommonDataKinds.StructuredName.GIVEN_NAME, contact.givenName)
            }
            if (contact.middleName.isNotBlank()) {
                nameBuilder.withValue(CommonDataKinds.StructuredName.MIDDLE_NAME, contact.middleName)
            }
            if (contact.familyName.isNotBlank()) {
                nameBuilder.withValue(CommonDataKinds.StructuredName.FAMILY_NAME, contact.familyName)
            }
            if (contact.nameSuffix.isNotBlank()) {
                nameBuilder.withValue(CommonDataKinds.StructuredName.SUFFIX, contact.nameSuffix)
            }
            ops.add(nameBuilder.build())

            // Nickname
            if (contact.nickname.isNotBlank()) {
                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactIndex)
                        .withValue(ContactsContract.Data.MIMETYPE, CommonDataKinds.Nickname.CONTENT_ITEM_TYPE)
                        .withValue(CommonDataKinds.Nickname.NAME, contact.nickname)
                        .withValue(CommonDataKinds.Nickname.TYPE, CommonDataKinds.Nickname.TYPE_DEFAULT)
                        .build()
                )
            }

            // Organization
            if (contact.company.isNotBlank() || contact.jobTitle.isNotBlank()) {
                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactIndex)
                        .withValue(ContactsContract.Data.MIMETYPE, CommonDataKinds.Organization.CONTENT_ITEM_TYPE)
                        .withValue(CommonDataKinds.Organization.COMPANY, contact.company)
                        .withValue(CommonDataKinds.Organization.TITLE, contact.jobTitle)
                        .build()
                )
            }

            // Photo
            if (photoBytes != null) {
                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactIndex)
                        .withValue(ContactsContract.Data.MIMETYPE, CommonDataKinds.Photo.CONTENT_ITEM_TYPE)
                        .withValue(CommonDataKinds.Photo.PHOTO, photoBytes)
                        .build()
                )
            }

            // Phone numbers with details
            if (contact.phoneDetails.isNotEmpty()) {
                contact.phoneDetails.forEach { phoneDetail ->
                    ops.add(
                        ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                            .withValueBackReference(
                                ContactsContract.Data.RAW_CONTACT_ID,
                                rawContactIndex
                            )
                            .withValue(
                                ContactsContract.Data.MIMETYPE,
                                CommonDataKinds.Phone.CONTENT_ITEM_TYPE
                            )
                            .withValue(CommonDataKinds.Phone.NUMBER, phoneDetail.number)
                            .withValue(CommonDataKinds.Phone.TYPE, phoneDetail.type)
                            .withValue(CommonDataKinds.Phone.LABEL, phoneDetail.label)
                            .withValue(
                                CommonDataKinds.Phone.IS_PRIMARY,
                                if (phoneDetail.isPrimary) 1 else 0
                            )
                            .build()
                    )
                }
            } else {
                // Private users don't have phoneDetails
                contact.phoneNumbers.forEach { number ->
                    ops.add(
                        ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactIndex)
                            .withValue(
                                ContactsContract.Data.MIMETYPE,
                                CommonDataKinds.Phone.CONTENT_ITEM_TYPE
                            )
                            .withValue(CommonDataKinds.Phone.NUMBER, number)
                            .withValue(
                                CommonDataKinds.Phone.TYPE,
                                CommonDataKinds.Phone.TYPE_MOBILE
                            )
                            .build()
                    )
                }
            }

            // Emails
            contact.emails.forEach { email ->
                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactIndex)
                        .withValue(ContactsContract.Data.MIMETYPE, CommonDataKinds.Email.CONTENT_ITEM_TYPE)
                        .withValue(CommonDataKinds.Email.ADDRESS, email.value)
                        .withValue(CommonDataKinds.Email.TYPE, email.type)
                        .withValue(CommonDataKinds.Email.LABEL, email.label)
                        .build()
                )
            }

            // Addresses
            contact.addresses.forEach { address ->
                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactIndex)
                        .withValue(ContactsContract.Data.MIMETYPE, CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE)
                        .withValue(CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS, address.formattedAddress)
                        .withValue(CommonDataKinds.StructuredPostal.TYPE, address.type)
                        .withValue(CommonDataKinds.StructuredPostal.LABEL, address.label)
                        .build()
                )
            }

            // Events
            contact.events.forEach { event ->
                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactIndex)
                        .withValue(ContactsContract.Data.MIMETYPE, CommonDataKinds.Event.CONTENT_ITEM_TYPE)
                        .withValue(CommonDataKinds.Event.START_DATE, event.date)
                        .withValue(CommonDataKinds.Event.TYPE, event.type)
                        .withValue(CommonDataKinds.Event.LABEL, event.label)
                        .build()
                )
            }
        } else {
            // Update existing contact
            val rawContactIds = getRawContactIds(contact.id)
            if (rawContactIds.isEmpty()) return

            rawContactIds.forEach { rawContactId ->
                // Update Account (Move contact)
                ops.add(
                    ContentProviderOperation.newUpdate(ContactsContract.RawContacts.CONTENT_URI)
                        .withSelection("${ContactsContract.RawContacts._ID}=?", arrayOf(rawContactId))
                        .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, contact.accountType)
                        .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, contact.accountName)
                        .build()
                )

                // Delete all existing data
                ops.add(
                    ContentProviderOperation.newDelete(ContactsContract.Data.CONTENT_URI)
                        .withSelection(
                            "${ContactsContract.Data.RAW_CONTACT_ID}=?",
                            arrayOf(rawContactId)
                        )
                        .build()
                )

                // Add new name
                val nameBuilder = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                    .withValue(ContactsContract.Data.MIMETYPE, CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                    .withValue(CommonDataKinds.StructuredName.DISPLAY_NAME, contact.displayName)

                if (contact.namePrefix.isNotBlank()) {
                    nameBuilder.withValue(CommonDataKinds.StructuredName.PREFIX, contact.namePrefix)
                }
                if (contact.givenName.isNotBlank()) {
                    nameBuilder.withValue(CommonDataKinds.StructuredName.GIVEN_NAME, contact.givenName)
                }
                if (contact.middleName.isNotBlank()) {
                    nameBuilder.withValue(CommonDataKinds.StructuredName.MIDDLE_NAME, contact.middleName)
                }
                if (contact.familyName.isNotBlank()) {
                    nameBuilder.withValue(CommonDataKinds.StructuredName.FAMILY_NAME, contact.familyName)
                }
                if (contact.nameSuffix.isNotBlank()) {
                    nameBuilder.withValue(CommonDataKinds.StructuredName.SUFFIX, contact.nameSuffix)
                }
                ops.add(nameBuilder.build())

                // Add nickname
                if (contact.nickname.isNotBlank()) {
                    ops.add(
                        ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                            .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                            .withValue(ContactsContract.Data.MIMETYPE, CommonDataKinds.Nickname.CONTENT_ITEM_TYPE)
                            .withValue(CommonDataKinds.Nickname.NAME, contact.nickname)
                            .withValue(CommonDataKinds.Nickname.TYPE, CommonDataKinds.Nickname.TYPE_DEFAULT)
                            .build()
                    )
                }

                // Add organization
                if (contact.company.isNotBlank() || contact.jobTitle.isNotBlank()) {
                    ops.add(
                        ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                            .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                            .withValue(ContactsContract.Data.MIMETYPE, CommonDataKinds.Organization.CONTENT_ITEM_TYPE)
                            .withValue(CommonDataKinds.Organization.COMPANY, contact.company)
                            .withValue(CommonDataKinds.Organization.TITLE, contact.jobTitle)
                            .build()
                    )
                }

                // Add photo
                if (photoBytes != null) {
                    ops.add(
                        ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                            .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                            .withValue(ContactsContract.Data.MIMETYPE, CommonDataKinds.Photo.CONTENT_ITEM_TYPE)
                            .withValue(CommonDataKinds.Photo.PHOTO, photoBytes)
                            .build()
                    )
                }

                // Add phone numbers
                if (contact.phoneDetails.isNotEmpty()) {
                contact.phoneDetails.forEach { phoneDetail ->
                    ops.add(
                        ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                            .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                            .withValue(ContactsContract.Data.MIMETYPE, CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                            .withValue(CommonDataKinds.Phone.NUMBER, phoneDetail.number)
                            .withValue(CommonDataKinds.Phone.TYPE, phoneDetail.type)
                            .withValue(CommonDataKinds.Phone.LABEL, phoneDetail.label)
                            .withValue(CommonDataKinds.Phone.IS_PRIMARY, if (phoneDetail.isPrimary) 1 else 0)
                            .build()
                    )
                }
                } else {
                    // Private users don't have phoneDetails
                    contact.phoneNumbers.forEach { number ->
                        ops.add(
                            ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                                .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                                .withValue(
                                    ContactsContract.Data.MIMETYPE,
                                    CommonDataKinds.Phone.CONTENT_ITEM_TYPE
                                )
                                .withValue(CommonDataKinds.Phone.NUMBER, number)
                                .withValue(
                                    CommonDataKinds.Phone.TYPE,
                                    CommonDataKinds.Phone.TYPE_MOBILE
                                )
                                .build()
                        )
                    }
                }

                // Add emails
                contact.emails.forEach { email ->
                    ops.add(
                        ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                            .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                            .withValue(ContactsContract.Data.MIMETYPE, CommonDataKinds.Email.CONTENT_ITEM_TYPE)
                            .withValue(CommonDataKinds.Email.ADDRESS, email.value)
                            .withValue(CommonDataKinds.Email.TYPE, email.type)
                            .withValue(CommonDataKinds.Email.LABEL, email.label)
                            .build()
                    )
                }

                // Add addresses
                contact.addresses.forEach { address ->
                    ops.add(
                        ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                            .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                            .withValue(ContactsContract.Data.MIMETYPE, CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE)
                            .withValue(CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS, address.formattedAddress)
                            .withValue(CommonDataKinds.StructuredPostal.TYPE, address.type)
                            .withValue(CommonDataKinds.StructuredPostal.LABEL, address.label)
                            .build()
                    )
                }

                // Add events
                contact.events.forEach { event ->
                    ops.add(
                        ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                            .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                            .withValue(ContactsContract.Data.MIMETYPE, CommonDataKinds.Event.CONTENT_ITEM_TYPE)
                            .withValue(CommonDataKinds.Event.START_DATE, event.date)
                            .withValue(CommonDataKinds.Event.TYPE, event.type)
                            .withValue(CommonDataKinds.Event.LABEL, event.label)
                            .build()
                    )
                }
            }
        }

        try {
            contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)

            // Update favorite status
            if (contact.id.isNotEmpty() && contact.id != "0") {
                toggleFavorite(contact.id, contact.isFavorite)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun deleteContact(contactId: String) {
        if (contactId.startsWith("p")) {
            val id = contactId.substring(1).toLongOrNull() ?: return
            privateContactDao.deleteById(id)
            return
        }
        val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, contactId)
        contentResolver.delete(uri, null, null)
    }

    override fun deleteContacts(contactIds: List<String>) {
        val ops = ArrayList<ContentProviderOperation>()
        contactIds.forEach { id ->
            if (id.startsWith("p")) {
                val lid = id.substring(1).toLongOrNull()
                if (lid != null) privateContactDao.deleteById(lid)
                return@forEach
            }
            val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, id)
            ops.add(ContentProviderOperation.newDelete(uri).build())
        }
        try {
            contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun moveContacts(contactIds: List<String>, accountName: String?, accountType: String?) {
//        val ops = ArrayList<ContentProviderOperation>()
//        contactIds.forEach { id ->
//            val rawContactId = getRawContactId(id)
//            if (rawContactId != null) {
//                ops.add(
//                    ContentProviderOperation.newUpdate(ContactsContract.RawContacts.CONTENT_URI)
//                        .withSelection("${ContactsContract.RawContacts._ID}=?", arrayOf(rawContactId))
//                        .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, accountType)
//                        .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, accountName)
//                        .build()
//                )
//            }
//        }
//        try {
//            contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
//        } catch (e: Exception) {
//            e.printStackTrace()
//        }

        if (contactIds.isEmpty()) return

        // We separate private and public contacts
        val privateIds = contactIds.filter { it.startsWith("p") }
        val publicIds = contactIds.filter { !it.startsWith("p") }

        // 1. Processing private contacts—making them regular (non-private)
        if (privateIds.isNotEmpty()) {
            privateIds.forEach { id ->
                val contact = getContactById(id)
                if (contact != null) {
                    val publicContact = contact.copy(
                        id = "0",
                        isPrivate = false,
                        accountName = accountName,
                        accountType = accountType
                    )
                    saveContact(publicContact)
                    // Deleting the private version
                    deleteContact(id)
                }
            }
        }

        // 2. Transferring regular contacts between accounts
        if (publicIds.isNotEmpty()) {
            val ops = ArrayList<ContentProviderOperation>()

            publicIds.forEach { id ->
                val rawContactIds = getRawContactIds(id)
                rawContactIds.forEach { rawId ->
                    ops.add(
                        ContentProviderOperation.newUpdate(ContactsContract.RawContacts.CONTENT_URI)
                            .withSelection("${ContactsContract.RawContacts._ID}=?", arrayOf(rawId))
                            .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, accountType)
                            .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, accountName)
                            .build()
                    )
                }
            }

            try {
                if (ops.isNotEmpty()) {
                    contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun getAvailableAccountsForMoving(): List<Account> {
        val sources = LinkedHashSet<Account>()
        // Accounts
        try {
            sources.addAll(AccountManager.get(context).accounts.toList())
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
        // SIM accounts
        try {
            val hasPhoneState =
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
            if (hasPhoneState) {
                val subManager = context.getSystemService(SubscriptionManager::class.java)
                val subs = subManager?.activeSubscriptionInfoList
                if (!subs.isNullOrEmpty()) {
                    subs.forEach { sub ->
                        val simName = sub.displayName?.toString()?.takeIf { it.isNotBlank() } ?: "SIM ${sub.simSlotIndex + 1}"
                        sources.add(Account(simName, "SIM_${sub.subscriptionId}"))
                    }
                } else {
                    sources.add(Account("sim_1", "SIM"))
                }
            } else {
                sources.add(Account("sim_1", "SIM"))
            }
        } catch (_: Exception) {
            sources.add(Account("sim_1", "SIM"))
        }

        return sources.toList()
    }

    override fun getAvailableAccounts(): List<Account> {
        val sources = LinkedHashSet<Account>()
        try {
            // We retrieve all raw contacts along with account information
            val projection = arrayOf(
                ContactsContract.RawContacts.ACCOUNT_NAME,
                ContactsContract.RawContacts.ACCOUNT_TYPE
            )

            contentResolver.query(
                ContactsContract.RawContacts.CONTENT_URI,
                projection,
                null,
                null,
                null
            )?.use { cursor ->
                val accountNameIdx = cursor.getColumnIndex(ContactsContract.RawContacts.ACCOUNT_NAME)
                val accountTypeIdx = cursor.getColumnIndex(ContactsContract.RawContacts.ACCOUNT_TYPE)

                while (cursor.moveToNext()) {
                    val accountName = cursor.getString(accountNameIdx)
                    val accountType = cursor.getString(accountTypeIdx)

                    // We exclude null accounts (they will be processed separately as "Device only")
                    if (accountName != null && accountType != null) {
                        sources.add(Account(accountName, accountType))
                    }
                }
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }

        // SIM accounts
        try {
            val hasPhoneState =
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
            if (hasPhoneState) {
                val subManager = context.getSystemService(SubscriptionManager::class.java)
                val subs = subManager?.activeSubscriptionInfoList
                if (!subs.isNullOrEmpty()) {
                    subs.forEach { sub ->
                        val simName = sub.displayName?.toString()?.takeIf { it.isNotBlank() } ?: "SIM ${sub.simSlotIndex + 1}"
                        sources.add(Account(simName, "SIM_${sub.subscriptionId}"))
                    }
                } else {
                    sources.add(Account("sim_1", "SIM"))
                }
            } else {
                sources.add(Account("sim_1", "SIM"))
            }
        } catch (_: Exception) {
            sources.add(Account("sim_1", "SIM"))
        }

        return sources.toList()
    }

    override fun getContactByNumber(number: String): Contact? {
        // Check private contacts first
        privateContactDao.getAll().forEach {
            val contact = it.toContact()
            if (contact.phoneNumbers.any { num -> areNumbersEqual(num, number) }) {
                return contact
            }
        }

        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))

        val projection = arrayOf(ContactsContract.PhoneLookup._ID)
        var contactId: String? = null

        try {
            contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idIdx = cursor.getColumnIndex(ContactsContract.PhoneLookup._ID)
                    contactId = if (idIdx != -1) cursor.getString(idIdx) else null
                }
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }

        if (contactId == null) return null

        // We retrieve the full contact information, including all fields
        val fullContact = getContactById(contactId)

        // Add the number you were looking for if it's not on the list
        return fullContact?.let {
            if (it.phoneNumbers.contains(number)) {
                it
            } else {
                it.copy(
                    phoneNumbers = it.phoneNumbers + number,
                    phoneDetails = it.phoneDetails + ContactPhoneDetail(
                        type = CommonDataKinds.Phone.TYPE_MOBILE,
                        label = null,
                        number = number,
                        isPrimary = false
                    )
                )
            }
        }
    }

    override fun findDuplicates(): List<List<Contact>> {
        val allContacts = getContacts()
        val duplicates = mutableListOf<List<Contact>>()

        val byName = allContacts.groupBy { it.displayName.lowercase().trim() }
            .filter { it.value.size > 1 }

        val byNumber = mutableMapOf<String, MutableSet<Contact>>()
        allContacts.forEach { contact ->
            contact.phoneNumbers.forEach { number ->
                val normalized = number.replace(Regex("[^0-9+]"), "")
                if (normalized.length >= 7) {
                    byNumber.getOrPut(normalized) { mutableSetOf() }.add(contact)
                }
            }
        }
        val byNumberFiltered = byNumber.filter { it.value.size > 1 }

        val processedIds = mutableSetOf<String>()

        byName.values.forEach { group ->
            duplicates.add(group)
            processedIds.addAll(group.map { it.id })
        }

        byNumberFiltered.values.forEach { group ->
            val uniqueGroup = group.filter { it.id !in processedIds }
            if (uniqueGroup.size > 1) {
                duplicates.add(uniqueGroup)
            }
        }

        return duplicates
    }

    override fun mergeContacts(targetContactId: String, sourceContactIds: List<String>) {
        val targetContact = getContactById(targetContactId) ?: return
        val ops = ArrayList<ContentProviderOperation>()

        sourceContactIds.forEach { sourceId ->
            if (sourceId == targetContactId) return@forEach
            val sourceContact = getContactById(sourceId) ?: return@forEach

            sourceContact.phoneNumbers.forEach { number ->
                if (!targetContact.phoneNumbers.contains(number)) {
                    ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValue(ContactsContract.Data.RAW_CONTACT_ID, getRawContactId(targetContactId))
                        .withValue(ContactsContract.Data.MIMETYPE, CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                        .withValue(CommonDataKinds.Phone.NUMBER, number)
                        .withValue(CommonDataKinds.Phone.TYPE, CommonDataKinds.Phone.TYPE_MOBILE)
                        .build())
                }
            }

            ops.add(ContentProviderOperation.newDelete(Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, sourceId))
                .build())
        }

        try {
            contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun setCustomRingtone(contactId: String, ringtoneUri: String?) {
        if (contactId.startsWith("p")) {
            val id = contactId.substring(1).toLongOrNull() ?: return
            privateContactDao.getById(id)?.let {
                privateContactDao.update(it.copy(customRingtone = ringtoneUri))
            }
            return
        }
        val contentValue = ContentValues().apply {
            put(ContactsContract.Contacts.CUSTOM_RINGTONE, ringtoneUri)
        }
        val updateUri = ContactsContract.Contacts.CONTENT_URI.buildUpon()
            .appendPath(contactId)
            .build()
        contentResolver.update(updateUri, contentValue, null, null)
    }

    override fun formatAllPhoneNumbers(onProgress: ((current: Int, total: Int) -> Unit)?) {
        val allContacts = getContacts(true)
        val ops = ArrayList<ContentProviderOperation>()
        val total = allContacts.size

        allContacts.forEachIndexed { index, contact ->
            onProgress?.invoke(index + 1, total)

            // Formatting numbers: Remove all spaces
            val formattedPhoneNumbers = contact.phoneNumbers.map { it.replace(" ", "") }
            val formattedPhoneDetails = contact.phoneDetails.map {
                it.copy(number = it.number.replace(" ", ""))
            }

            // Let's check to see if the numbers have changed
            val phoneNumbersChanged = formattedPhoneNumbers != contact.phoneNumbers
            val phoneDetailsChanged = formattedPhoneDetails != contact.phoneDetails

            if (phoneNumbersChanged || phoneDetailsChanged) {
                if (contact.isPrivate) {
                    val id = contact.id.substring(1).toLongOrNull()
                    if (id != null) {
                        privateContactDao.getById(id)?.let { entity ->
                            val updatedEntity = entity.copy(
                                phoneNumbersJson = Json.encodeToString(formattedPhoneNumbers),
                                phoneDetailsJson = Json.encodeToString(formattedPhoneDetails)
                            )
                            privateContactDao.update(updatedEntity)
                        }
                    }
                } else {
                    contact.phoneDetails.forEachIndexed { i, phoneDetail ->
                        val newNum = formattedPhoneDetails[i].number
                        val oldNum = phoneDetail.number
                        if (newNum != oldNum) {
                            val rawId = getRawContactId(contact.id)
                            if (rawId != null) {
                                ops.add(
                                    ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
                                        .withSelection(
                                            "${ContactsContract.Data.RAW_CONTACT_ID}=? AND ${ContactsContract.Data.MIMETYPE}=? AND ${ContactsContract.CommonDataKinds.Phone.NUMBER}=?",
                                            arrayOf(rawId, CommonDataKinds.Phone.CONTENT_ITEM_TYPE, oldNum)
                                        )
                                        .withValue(CommonDataKinds.Phone.NUMBER, newNum)
                                        .build()
                                )
                            }
                        }
                    }

                    if (contact.phoneNumbers.isNotEmpty() && contact.phoneDetails.isEmpty()) {
                        contact.phoneNumbers.forEachIndexed { i, oldNum ->
                            val newNum = formattedPhoneNumbers[i]
                            if (newNum != oldNum) {
                                val rawId = getRawContactId(contact.id)
                                if (rawId != null) {
                                    ops.add(
                                        ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
                                            .withSelection(
                                                "${ContactsContract.Data.RAW_CONTACT_ID}=? AND ${ContactsContract.Data.MIMETYPE}=? AND ${ContactsContract.CommonDataKinds.Phone.NUMBER}=?",
                                                arrayOf(rawId, CommonDataKinds.Phone.CONTENT_ITEM_TYPE, oldNum)
                                            )
                                            .withValue(CommonDataKinds.Phone.NUMBER, newNum)
                                            .build()
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        try {
            if (ops.isNotEmpty()) {
                contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun makeContactPrivate(contactId: String) {
        val contact = getContactById(contactId) ?: return
        if (contact.isPrivate) return

        // 1. Save to local DB
        val privateContact = contact.copy(isPrivate = true)
        privateContactDao.insert(PrivateContactEntity.fromContact(privateContact))

        // 2. Delete from system contacts
        deleteContact(contactId)
    }

    override fun makeContactPublic(contactId: String) {
        val contact = getContactById(contactId) ?: return
        if (!contact.isPrivate) return

        // 1. Save to system contacts
        saveContact(contact.copy(id = "", isPrivate = false))

        // 2. Delete from local DB
        deleteContact(contactId)
    }

    override fun exportPrivateContacts(uri: Uri) {
        val privateContacts = privateContactDao.getAll().map { it.toContact() }
        val vcfContent = buildString {
            privateContacts.forEach { contact ->
                append("BEGIN:VCARD\n")
                append("VERSION:3.0\n")
                append("FN:${contact.displayName}\n")

                val n = "${contact.familyName};${contact.givenName};${contact.middleName};${contact.namePrefix};${contact.nameSuffix}"
                append("N:$n\n")

                if (contact.nickname.isNotBlank()) {
                    append("NICKNAME:${contact.nickname}\n")
                }

                if (contact.company.isNotBlank()) {
                    append("ORG:${contact.company}\n")
                }
                if (contact.jobTitle.isNotBlank()) {
                    append("TITLE:${contact.jobTitle}\n")
                }

                contact.phoneDetails.forEach { phone ->
                    val types = mutableListOf<String>()
                    when (phone.type) {
                        CommonDataKinds.Phone.TYPE_HOME -> types.add("HOME")
                        CommonDataKinds.Phone.TYPE_WORK -> types.add("WORK")
                        CommonDataKinds.Phone.TYPE_MOBILE -> types.add("CELL")
                        CommonDataKinds.Phone.TYPE_FAX_WORK -> {
                            types.add("WORK")
                            types.add("FAX")
                        }
                        CommonDataKinds.Phone.TYPE_FAX_HOME -> {
                            types.add("HOME")
                            types.add("FAX")
                        }
                        CommonDataKinds.Phone.TYPE_PAGER -> types.add("PAGER")
                        CommonDataKinds.Phone.TYPE_OTHER -> types.add("OTHER")
                        CommonDataKinds.Phone.TYPE_CUSTOM -> {
                            val label = phone.label?.takeIf { it.isNotBlank() } ?: "OTHER"
                            types.add("X-$label")
                        }
                        else -> types.add("OTHER")
                    }

                    if (phone.isPrimary) {
                        types.add("PREF")
                    }

                    val typeStr = types.joinToString(";")
                    append("TEL;TYPE=$typeStr:${phone.number}\n")
                }

                contact.emails.forEach { email ->
                    val types = mutableListOf<String>()
                    when (email.type) {
                        CommonDataKinds.Email.TYPE_HOME -> types.add("HOME")
                        CommonDataKinds.Email.TYPE_WORK -> types.add("WORK")
                        CommonDataKinds.Email.TYPE_OTHER -> types.add("OTHER")
                        CommonDataKinds.Email.TYPE_CUSTOM -> {
                            val label = email.label?.takeIf { it.isNotBlank() } ?: "OTHER"
                            types.add("X-$label")
                        }
                        else -> types.add("OTHER")
                    }
                    val typeStr = types.joinToString(";")
                    append("EMAIL;TYPE=$typeStr:${email.value}\n")
                }

                contact.addresses.forEach { address ->
                    val types = mutableListOf<String>()
                    when (address.type) {
                        CommonDataKinds.StructuredPostal.TYPE_HOME -> types.add("HOME")
                        CommonDataKinds.StructuredPostal.TYPE_WORK -> types.add("WORK")
                        CommonDataKinds.StructuredPostal.TYPE_OTHER -> types.add("OTHER")
                        CommonDataKinds.StructuredPostal.TYPE_CUSTOM -> {
                            val label = address.label?.takeIf { it.isNotBlank() } ?: "OTHER"
                            types.add("X-$label")
                        }
                        else -> types.add("OTHER")
                    }
                    val typeStr = types.joinToString(";")
                    // ADR format: P.O. Box; street; city; region; ZIP code; country
                    append("ADR;TYPE=$typeStr:;;${address.formattedAddress};;;;\n")
                }

                contact.events.forEach { event ->
                    when (event.type) {
                        CommonDataKinds.Event.TYPE_BIRTHDAY -> {
                            val date = event.date.replace("-", "") // YYYYMMDD format
                            if (date.length == 8) {
                                append("BDAY:$date\n")
                            } else {
                                append("BDAY:${event.date}\n")
                            }
                        }
                        CommonDataKinds.Event.TYPE_ANNIVERSARY -> {
                            append("X-ANNIVERSARY:${event.date}\n")
                        }
                        else -> {
                            val label = event.label?.takeIf { it.isNotBlank() } ?: "OTHER"
                            append("X-EVENT-$label:${event.date}\n")
                        }
                    }
                }

                if (contact.isFavorite) {
                    append("X-FAVORITE:TRUE\n")
                }

                if (contact.photoUri != null) {
                    try {
                        val bytes = getPhotoBytes(contact.photoUri)
                        if (bytes != null) {
                            val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                            append("PHOTO;ENCODING=B;TYPE=JPEG:$base64\n")
                        }
                    } catch (e: Exception) {
                    }
                }

                append("END:VCARD\n")
            }
        }
        try {
            context.contentResolver.openOutputStream(uri)?.use {
                it.write(vcfContent.toByteArray())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun importPrivateContacts(uri: Uri) {
        try {
            val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: return
            val vCards = content.split("BEGIN:VCARD")
            vCards.forEach { vCard ->
                if (vCard.isBlank()) return@forEach

                var namePrefix = ""
                var givenName = ""
                var middleName = ""
                var familyName = ""
                var nameSuffix = ""
                var nickname = ""
                var company = ""
                var jobTitle = ""
                val phoneDetails = mutableListOf<ContactPhoneDetail>()
                val emails = mutableListOf<ContactEmail>()
                val addresses = mutableListOf<ContactAddress>()
                val events = mutableListOf<ContactEvent>()
                var isFavorite = false
                var photoUri: String? = null

                vCard.lines().forEach { line ->
                    when {
                        line.startsWith("N:") -> {
                            val parts = line.substring(2).split(";")
                            if (parts.size >= 5) {
                                familyName = parts[0]
                                givenName = parts[1]
                                middleName = parts[2]
                                namePrefix = parts[3]
                                nameSuffix = parts[4]
                            }
                        }
                        line.startsWith("FN:") -> {
                            val fullName = line.substring(3)
                            if (familyName.isBlank() && givenName.isBlank()) {
                                val nameParts = fullName.trim().split(" ")
                                if (nameParts.isNotEmpty()) {
                                    givenName = nameParts.first()
                                    if (nameParts.size > 1) {
                                        familyName = nameParts.drop(1).joinToString(" ")
                                    }
                                }
                            }
                        }
                        line.startsWith("NICKNAME:") -> { nickname = line.substring(9) }
                        line.startsWith("ORG:") -> { company = line.substring(4) }
                        line.startsWith("TITLE:") -> { jobTitle = line.substring(6) }
                        line.startsWith("TEL") && line.contains(":") -> {
                            val colonIndex = line.indexOf(":")
                            if (colonIndex > 0) {
                                val number = line.substring(colonIndex + 1)
                                val paramsPart = line.substring(3, colonIndex).uppercase()

                                var type = CommonDataKinds.Phone.TYPE_OTHER
                                var label: String? = null
                                var isPrimary = false

                                if (paramsPart.contains("TYPE=")) {
                                    val typeStr = paramsPart.substringAfter("TYPE=")
                                    val typeParams = typeStr.split(";")

                                    typeParams.forEach { param ->
                                        when {
                                            param.contains("HOME") && !param.contains("FAX") ->
                                                type = CommonDataKinds.Phone.TYPE_HOME
                                            param.contains("WORK") && !param.contains("FAX") ->
                                                type = CommonDataKinds.Phone.TYPE_WORK
                                            param.contains("CELL") || param.contains("MOBILE") ->
                                                type = CommonDataKinds.Phone.TYPE_MOBILE
                                            param.contains("FAX") && param.contains("WORK") ->
                                                type = CommonDataKinds.Phone.TYPE_FAX_WORK
                                            param.contains("FAX") && param.contains("HOME") ->
                                                type = CommonDataKinds.Phone.TYPE_FAX_HOME
                                            param.contains("FAX") ->
                                                type = CommonDataKinds.Phone.TYPE_FAX_WORK
                                            param.contains("PAGER") ->
                                                type = CommonDataKinds.Phone.TYPE_PAGER
                                            param.startsWith("X-") -> {
                                                type = CommonDataKinds.Phone.TYPE_CUSTOM
                                                label = param.substring(2)
                                            }
                                            param.contains("PREF") -> isPrimary = true
                                        }
                                    }
                                } else {
                                    // The old format without TYPE=
                                    val typeParams = paramsPart.split(";")
                                    typeParams.forEach { param ->
                                        when {
                                            param.contains("HOME") -> type = CommonDataKinds.Phone.TYPE_HOME
                                            param.contains("WORK") -> type = CommonDataKinds.Phone.TYPE_WORK
                                            param.contains("CELL") -> type = CommonDataKinds.Phone.TYPE_MOBILE
                                            param.contains("FAX") -> type = CommonDataKinds.Phone.TYPE_FAX_WORK
                                            param.contains("PAGER") -> type = CommonDataKinds.Phone.TYPE_PAGER
                                            param.startsWith("X-") -> {
                                                type = CommonDataKinds.Phone.TYPE_CUSTOM
                                                label = param.substring(2)
                                            }
                                            param.contains("PREF") -> isPrimary = true
                                        }
                                    }
                                }

                                phoneDetails.add(ContactPhoneDetail(
                                    type = type,
                                    label = label,
                                    number = number,
                                    isPrimary = isPrimary
                                ))
                            }
                        }
                        line.startsWith("EMAIL") && line.contains(":") -> {
                            val colonIndex = line.indexOf(":")
                            if (colonIndex > 0) {
                                val emailValue = line.substring(colonIndex + 1)
                                val paramsPart = line.substring(5, colonIndex).uppercase()

                                var type = CommonDataKinds.Email.TYPE_OTHER
                                var label: String? = null

                                if (paramsPart.contains("TYPE=")) {
                                    val typeStr = paramsPart.substringAfter("TYPE=")
                                    val typeParams = typeStr.split(";")

                                    typeParams.forEach { param ->
                                        when {
                                            param.contains("HOME") -> type = CommonDataKinds.Email.TYPE_HOME
                                            param.contains("WORK") -> type = CommonDataKinds.Email.TYPE_WORK
                                            param.startsWith("X-") -> {
                                                type = CommonDataKinds.Email.TYPE_CUSTOM
                                                label = param.substring(2)
                                            }
                                        }
                                    }
                                } else {
                                    val typeParams = paramsPart.split(";")
                                    typeParams.forEach { param ->
                                        when {
                                            param.contains("HOME") -> type = CommonDataKinds.Email.TYPE_HOME
                                            param.contains("WORK") -> type = CommonDataKinds.Email.TYPE_WORK
                                            param.startsWith("X-") -> {
                                                type = CommonDataKinds.Email.TYPE_CUSTOM
                                                label = param.substring(2)
                                            }
                                        }
                                    }
                                }

                                emails.add(ContactEmail(
                                    type = type,
                                    label = label,
                                    value = emailValue
                                ))
                            }
                        }
                        line.startsWith("ADR") && line.contains(":") -> {
                            val colonIndex = line.indexOf(":")
                            if (colonIndex > 0) {
                                val addressParts = line.substring(colonIndex + 1).split(";")
                                // ADR format: P.O. Box; street; city; region; ZIP code; country
                                val formattedAddress = buildString {
                                    if (addressParts.size > 1 && addressParts[1].isNotBlank()) {
                                        append(addressParts[1]) // Street
                                    }
                                    if (addressParts.size > 2 && addressParts[2].isNotBlank()) {
                                        if (isNotEmpty()) append(", ")
                                        append(addressParts[2]) // City
                                    }
                                    if (addressParts.size > 3 && addressParts[3].isNotBlank()) {
                                        if (isNotEmpty()) append(", ")
                                        append(addressParts[3]) // Region
                                    }
                                    if (addressParts.size > 4 && addressParts[4].isNotBlank()) {
                                        if (isNotEmpty()) append(" ")
                                        append(addressParts[4]) // Index
                                    }
                                    if (addressParts.size > 5 && addressParts[5].isNotBlank()) {
                                        if (isNotEmpty()) append(", ")
                                        append(addressParts[5]) // Country
                                    }
                                }

                                val paramsPart = line.substring(3, colonIndex).uppercase()
                                var type = CommonDataKinds.StructuredPostal.TYPE_OTHER
                                var label: String? = null

                                if (paramsPart.contains("TYPE=")) {
                                    val typeStr = paramsPart.substringAfter("TYPE=")
                                    val typeParams = typeStr.split(";")

                                    typeParams.forEach { param ->
                                        when {
                                            param.contains("HOME") -> type = CommonDataKinds.StructuredPostal.TYPE_HOME
                                            param.contains("WORK") -> type = CommonDataKinds.StructuredPostal.TYPE_WORK
                                            param.startsWith("X-") -> {
                                                type = CommonDataKinds.StructuredPostal.TYPE_CUSTOM
                                                label = param.substring(2)
                                            }
                                        }
                                    }
                                } else {
                                    val typeParams = paramsPart.split(";")
                                    typeParams.forEach { param ->
                                        when {
                                            param.contains("HOME") -> type = CommonDataKinds.StructuredPostal.TYPE_HOME
                                            param.contains("WORK") -> type = CommonDataKinds.StructuredPostal.TYPE_WORK
                                            param.startsWith("X-") -> {
                                                type = CommonDataKinds.StructuredPostal.TYPE_CUSTOM
                                                label = param.substring(2)
                                            }
                                        }
                                    }
                                }

                                if (formattedAddress.isNotBlank()) {
                                    addresses.add(ContactAddress(
                                        type = type,
                                        label = label,
                                        formattedAddress = formattedAddress
                                    ))
                                }
                            }
                        }
                        line.startsWith("BDAY:") -> {
                            val date = line.substring(5)
                            if (date.isNotBlank()) {
                                var formattedDate = date
                                // If the date is in the YYYYMMDD format, add separators
                                if (formattedDate.matches(Regex("\\d{8}"))) {
                                    formattedDate = "${formattedDate.substring(0, 4)}-${formattedDate.substring(4, 6)}-${formattedDate.substring(6, 8)}"
                                }
                                events.add(ContactEvent(
                                    type = CommonDataKinds.Event.TYPE_BIRTHDAY,
                                    label = null,
                                    date = formattedDate
                                ))
                            }
                        }
                        line.startsWith("X-ANNIVERSARY:") -> {
                            val date = line.substring(14)
                            if (date.isNotBlank()) {
                                events.add(ContactEvent(
                                    type = CommonDataKinds.Event.TYPE_ANNIVERSARY,
                                    label = null,
                                    date = date
                                ))
                            }
                        }
                        line.startsWith("X-EVENT-") && line.contains(":") -> {
                            val colonIndex = line.indexOf(":")
                            if (colonIndex > 0) {
                                val date = line.substring(colonIndex + 1)
                                val label = line.substring(9, colonIndex)
                                if (date.isNotBlank()) {
                                    events.add(ContactEvent(
                                        type = CommonDataKinds.Event.TYPE_CUSTOM,
                                        label = label,
                                        date = date
                                    ))
                                }
                            }
                        }
                        line.startsWith("PHOTO;") && line.contains(":") -> {
                            val colonIndex = line.indexOf(":")
                            if (colonIndex > 0 && line.contains("ENCODING=B")) {
                                val base64Data = line.substring(colonIndex + 1)
                                try {
                                    val bytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
                                    val fileName = "temp_contact_${System.currentTimeMillis()}.jpg"
                                    val file = java.io.File(context.filesDir, fileName)
                                    file.writeBytes(bytes)
                                    photoUri = file.toUri().toString()
                                } catch (e: Exception) {
                                }
                            }
                        }
                        line.startsWith("X-FAVORITE:TRUE") -> {
                            isFavorite = true
                        }
                    }
                }

                val displayName = buildString {
                    if (namePrefix.isNotBlank()) append("$namePrefix ")
                    if (givenName.isNotBlank()) append("$givenName ")
                    if (middleName.isNotBlank()) append("$middleName ")
                    if (familyName.isNotBlank()) append(familyName)
                    if (nameSuffix.isNotBlank()) append(", $nameSuffix")
                }.trim()

                val hasName = displayName.isNotBlank() || company.isNotBlank() || jobTitle.isNotBlank()
                val hasPhone = phoneDetails.isNotEmpty()

                if (hasName || hasPhone) {
                    saveContact(Contact(
                        id = "0",
                        namePrefix = namePrefix,
                        givenName = givenName,
                        middleName = middleName,
                        familyName = familyName,
                        nameSuffix = nameSuffix,
                        nickname = nickname,
                        company = company,
                        jobTitle = jobTitle,
                        phoneNumbers = phoneDetails.map { it.number },
                        phoneDetails = phoneDetails,
                        emails = emails,
                        addresses = addresses,
                        events = events,
                        photoUri = photoUri,
                        isFavorite = isFavorite,
                        isPrivate = true
                    ))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}