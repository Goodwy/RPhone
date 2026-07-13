package dev.goodwy.rphone.modal.data

import kotlinx.serialization.Serializable

@Serializable
data class Contact(
    val id: String,
    val namePrefix: String = "",
    val givenName: String = "",
    val middleName: String = "",
    val familyName: String = "",
    val nameSuffix: String = "",
    val nickname: String = "",
    val company: String = "",
    val jobTitle: String = "",
    val phoneNumbers: List<String> = emptyList(),
    val phoneDetails: List<ContactPhoneDetail> = emptyList(),
    val emails: List<ContactEmail> = emptyList(),
    val addresses: List<ContactAddress> = emptyList(),
    val events: List<ContactEvent> = emptyList(),
    val photoUri: String? = null,
    val isFavorite: Boolean = false,
    val customRingtone: String? = null,
    val accountName: String? = null,
    val accountType: String? = null,
    val isPrivate: Boolean = false
) {
    val displayName: String
        get() = getDisplayName(this)
    val isCompany: Boolean
        get() = isCompany(this)

    // For backward compatibility
    @Deprecated("Use displayName instead", ReplaceWith("displayName"))
    val name: String get() = displayName
}

@Serializable
data class ContactEvent(
    val type: Int,
    val label: String?,
    val date: String
)

@Serializable
data class ContactPhoneDetail(
    val type: Int,
    val label: String?,
    val number: String,
    val isPrimary: Boolean = false
)

@Serializable
data class ContactEmail(
    val type: Int,
    val label: String?,
    val value: String
)

@Serializable
data class ContactAddress(
    val type: Int,
    val label: String?,
    val formattedAddress: String
)

fun getDisplayName(contact: Contact, order: Int = 0): String {
    return buildString {
        when (order) {
            0 -> {
                // Prefix (Mr., Mrs., Dr., etc.)
                if (contact.namePrefix.isNotBlank()) {
                    append(contact.namePrefix)
                }
                // Given name (First name)
                if (contact.givenName.isNotBlank()) {
                    if (isNotEmpty()) append(" ")
                    append(contact.givenName)
                }
                // Middle name
                if (contact.middleName.isNotBlank()) {
                    if (isNotEmpty()) append(" ")
                    append(contact.middleName)
                }
                // Family name (Last name)
                if (contact.familyName.isNotBlank()) {
                    if (isNotEmpty()) append(" ")
                    append(contact.familyName)
                }
                // Suffix (Jr., Sr., III, etc.) - with comma before it like Google Contacts
                if (contact.nameSuffix.isNotBlank()) {
                    if (isNotEmpty()) append(", ")
                    append(contact.nameSuffix)
                }
            }

            1 -> {
                // Family name (Last name)
                if (contact.familyName.isNotBlank()) {
                    append(contact.familyName)
                }
                // Suffix (Jr., Sr., III, etc.) - with comma before suffix
                if (contact.nameSuffix.isNotBlank()) {
                    if (isNotEmpty()) append(", ")
                    append(contact.nameSuffix)
                }
                // Separator (comma) between last name + suffix and first name
                if (contact.givenName.isNotBlank() || contact.middleName.isNotBlank() || contact.namePrefix.isNotBlank()) {
                    if (isNotEmpty()) append(", ")
                }
                // Prefix (Mr., Mrs., Dr., etc.)
                if (contact.namePrefix.isNotBlank()) {
                    append(contact.namePrefix)
                }
                // Given name (First name)
                if (contact.givenName.isNotBlank()) {
                    if (isNotEmpty()) append(" ")
                    append(contact.givenName)
                }
                // Middle name
                if (contact.middleName.isNotBlank()) {
                    if (isNotEmpty()) append(" ")
                    append(contact.middleName)
                }
            }
        }

        // If there is no name, use "nickname", "company" or "phone"
        if (isEmpty() && contact.nickname.isNotBlank()) {
            append(contact.nickname)
        }
        if (isEmpty() && contact.company.isNotBlank()) {
            append(contact.company)
        }
        if (isEmpty() && contact.jobTitle.isNotBlank()) {
            append(contact.jobTitle)
        }
        if (isEmpty() && contact.phoneNumbers.isNotEmpty()) {
            append(contact.phoneNumbers.first())
        }
        if (isEmpty()) {
            append("")
        }
    }
}

fun isCompany(contact: Contact): Boolean {
    return contact.namePrefix.isEmpty() && contact.givenName.isEmpty() && contact.middleName.isEmpty()
            && contact.givenName.isEmpty() && contact.nameSuffix.isEmpty()
            && (contact.company.isNotEmpty() || contact.jobTitle.isNotEmpty())
}