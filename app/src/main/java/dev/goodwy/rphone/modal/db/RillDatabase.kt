package dev.goodwy.rphone.modal.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [PrivateContactEntity::class], version = 3, exportSchema = false)
abstract class RillDatabase : RoomDatabase() {
    abstract fun privateContactDao(): PrivateContactDao

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
    }
}
