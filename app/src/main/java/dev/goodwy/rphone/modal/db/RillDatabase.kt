// Based on RivoPhoneApp by user-grinch (© 2025-2026 RivoPhone Project / user-grinch), GPL-3.0.
// Modified by Goodwy on 2026-11-12.
package dev.goodwy.rphone.modal.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        PrivateContactEntity::class,
        TrashedContactEntity::class,
        SpamNumberEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class RillDatabase : RoomDatabase() {
    abstract fun privateContactDao(): PrivateContactDao
    abstract fun trashedContactDao(): TrashedContactDao
    abstract fun spamNumberDao(): SpamNumberDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE private_contacts ADD COLUMN notes TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE private_contacts ADD COLUMN isHidden INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
            CREATE TABLE IF NOT EXISTS `trashed_contacts` (
                `localId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `originalId` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `phoneNumbersJson` TEXT NOT NULL,
                `contactJson` TEXT NOT NULL,
                `trashedAt` INTEGER NOT NULL
            )
            """.trimIndent()
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
            CREATE TABLE IF NOT EXISTS `spam_numbers` (
                `normalizedNumber` TEXT NOT NULL,
                `rawNumber` TEXT NOT NULL,
                `category` TEXT NOT NULL,
                `source` TEXT NOT NULL,
                `label` TEXT DEFAULT NULL,
                `confidence` INTEGER NOT NULL DEFAULT 100,
                `reportCount` INTEGER NOT NULL DEFAULT 1,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`normalizedNumber`)
            )
            """.trimIndent()
                )
            }
        }
    }
}
