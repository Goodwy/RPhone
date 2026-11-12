// Based on RivoPhoneApp by user-grinch (© 2025-2026 RivoPhone Project / user-grinch), GPL-3.0.
// Modified by Goodwy on 2026-11-12.
package dev.goodwy.rphone.modal.db

import androidx.room.*

@Dao
interface PrivateContactDao {
    @Query("SELECT * FROM private_contacts ORDER BY display_name ASC")
    fun getAll(): List<PrivateContactEntity>

    @Query("SELECT * FROM private_contacts WHERE localId = :id")
    fun getById(id: Long): PrivateContactEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(contact: PrivateContactEntity): Long

    @Update
    fun update(contact: PrivateContactEntity)

    @Delete
    fun delete(contact: PrivateContactEntity)

    @Query("DELETE FROM private_contacts WHERE localId = :id")
    fun deleteById(id: Long)

    @Query("UPDATE private_contacts SET isHidden = :isHidden WHERE localId = :id")
    fun setHidden(id: Long, isHidden: Boolean)
}
