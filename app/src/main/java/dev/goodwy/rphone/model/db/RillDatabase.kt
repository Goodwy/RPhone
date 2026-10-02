package dev.goodwy.rphone.model.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        PrivateContactEntity::class,
        TrashedContactEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class RillDatabase : RoomDatabase() {
    abstract fun privateContactDao(): PrivateContactDao
    abstract fun trashedContactDao(): TrashedContactDao

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
    }
}
