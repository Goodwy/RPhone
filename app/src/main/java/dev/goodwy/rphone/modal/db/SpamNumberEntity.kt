package dev.goodwy.rphone.modal.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "spam_numbers")
data class SpamNumberEntity(
    @PrimaryKey
    val normalizedNumber: String,
    val rawNumber: String,
    val category: String,
    val source: String,
    val label: String? = null,
    val confidence: Int = 100,
    val reportCount: Int = 1,
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val SOURCE_USER_REPORT = "USER_REPORT"
        const val SOURCE_IMPORTED_LIST = "IMPORTED_LIST"
        const val SOURCE_LEGACY_MIGRATION = "LEGACY_MIGRATION"
    }
}
