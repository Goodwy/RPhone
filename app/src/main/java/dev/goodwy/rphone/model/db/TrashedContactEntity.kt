package dev.goodwy.rphone.model.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import dev.goodwy.rphone.model.data.Contact
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Entity(tableName = "trashed_contacts")
data class TrashedContactEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val originalId: String,
    val name: String,
    val phoneNumbersJson: String,
    val contactJson: String,
    val trashedAt: Long = System.currentTimeMillis()
) {
    fun toContact(): Contact? {
        return runCatching { json.decodeFromString<Contact>(contactJson) }.getOrNull()
    }

    companion object {
        private val json = Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
        }

        fun fromContact(contact: Contact): TrashedContactEntity {
            return TrashedContactEntity(
                originalId = contact.id,
                name = contact.displayName,
                phoneNumbersJson = json.encodeToString(contact.phoneNumbers),
                contactJson = json.encodeToString(contact),
                trashedAt = System.currentTimeMillis()
            )
        }
    }
}