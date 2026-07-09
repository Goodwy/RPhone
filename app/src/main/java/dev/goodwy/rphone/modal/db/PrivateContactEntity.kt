package dev.goodwy.rphone.modal.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.goodwy.rphone.modal.data.Contact
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Entity(
    tableName = "private_contacts",
    indices = [Index(value = ["display_name"])]
)
data class PrivateContactEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val namePrefix: String = "",
    val givenName: String = "",
    val middleName: String = "",
    val familyName: String = "",
    val nameSuffix: String = "",
    val nickname: String = "",
    val company: String = "",
    val jobTitle: String = "",
    val phoneNumbersJson: String,
    val phoneDetailsJson: String = "[]",
    val emailsJson: String,
    val addressesJson: String,
    val eventsJson: String,
    val photoUri: String? = null,
    val isFavorite: Boolean = false,
    val customRingtone: String? = null,
    @ColumnInfo(name = "display_name")
    val displayName: String = ""
) {
    fun toContact(): Contact {
        return Contact(
            id = "p$localId",
            namePrefix = namePrefix,
            givenName = givenName,
            middleName = middleName,
            familyName = familyName,
            nameSuffix = nameSuffix,
            nickname = nickname,
            company = company,
            jobTitle = jobTitle,
            phoneNumbers = Json.decodeFromString(phoneNumbersJson),
            phoneDetails = Json.decodeFromString(phoneDetailsJson),
            emails = Json.decodeFromString(emailsJson),
            addresses = Json.decodeFromString(addressesJson),
            events = Json.decodeFromString(eventsJson),
            photoUri = photoUri,
            isFavorite = isFavorite,
            customRingtone = customRingtone,
            isPrivate = true
        )
    }

    companion object {
        fun fromContact(contact: Contact): PrivateContactEntity {
            val entity = PrivateContactEntity(
                localId = if (contact.id.startsWith("p")) contact.id.substring(1).toLong() else 0,
                namePrefix = contact.namePrefix,
                givenName = contact.givenName,
                middleName = contact.middleName,
                familyName = contact.familyName,
                nameSuffix = contact.nameSuffix,
                nickname = contact.nickname,
                company = contact.company,
                jobTitle = contact.jobTitle,
                phoneNumbersJson = Json.encodeToString(contact.phoneNumbers),
                phoneDetailsJson = Json.encodeToString(contact.phoneDetails),
                emailsJson = Json.encodeToString(contact.emails),
                addressesJson = Json.encodeToString(contact.addresses),
                eventsJson = Json.encodeToString(contact.events),
                photoUri = contact.photoUri,
                isFavorite = contact.isFavorite,
                customRingtone = contact.customRingtone,
                displayName = ""
            )
            return entity.copy(displayName = entity.calculateDisplayName())
        }
    }

    fun calculateDisplayName(): String {
        return buildString {
            // Prefix (Mr., Mrs., Dr., etc.)
            if (namePrefix.isNotBlank()) {
                append(namePrefix)
            }
            // Given name (First name)
            if (givenName.isNotBlank()) {
                if (isNotEmpty()) append(" ")
                append(givenName)
            }
            // Middle name
            if (middleName.isNotBlank()) {
                if (isNotEmpty()) append(" ")
                append(middleName)
            }
            // Family name (Last name)
            if (familyName.isNotBlank()) {
                if (isNotEmpty()) append(" ")
                append(familyName)
            }
            // Suffix (Jr., Sr., III, etc.) - with comma before it like Google Contacts
            if (nameSuffix.isNotBlank()) {
                if (isNotEmpty()) append(", ")
                append(nameSuffix)
            }
            // If there is no name, use "nickname", "company" or "phone"
            if (isEmpty() && nickname.isNotBlank()) {
                append(nickname)
            }
            if (isEmpty() && company.isNotBlank()) {
                append(company)
            }
            if (isEmpty() && jobTitle.isNotBlank()) {
                append(jobTitle)
            }
            if (isEmpty() && phoneNumbersJson.isNotEmpty()) {
                try {
                    val phones = Json.decodeFromString<List<String>>(phoneNumbersJson)
                    if (phones.isNotEmpty()) {
                        append(phones.first())
                    }
                } catch (_: Exception) {
                }
            }
            if (isEmpty()) {
                append("Unnamed")
            }
        }
    }
}
