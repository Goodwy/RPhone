package dev.goodwy.rphone.model.db

import androidx.room.*

@Dao
interface TrashedContactDao {
    @Query("SELECT * FROM trashed_contacts ORDER BY trashedAt DESC")
    suspend fun getAll(): List<TrashedContactEntity>

    @Query("SELECT * FROM trashed_contacts WHERE localId = :id")
    suspend fun getById(id: Long): TrashedContactEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: TrashedContactEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<TrashedContactEntity>)

    @Query("DELETE FROM trashed_contacts WHERE localId = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM trashed_contacts")
    suspend fun deleteAll()

    @Query("DELETE FROM trashed_contacts WHERE trashedAt < :olderThanTimestamp")
    suspend fun pruneOlderThan(olderThanTimestamp: Long): Int
}