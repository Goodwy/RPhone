package dev.goodwy.rphone.modal.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SpamNumberDao {
    @Query("SELECT * FROM spam_numbers ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<SpamNumberEntity>>

    @Query("SELECT * FROM spam_numbers ORDER BY updatedAt DESC")
    suspend fun getAll(): List<SpamNumberEntity>

    @Query("SELECT * FROM spam_numbers WHERE normalizedNumber = :normalizedNumber LIMIT 1")
    suspend fun getByNormalizedNumber(normalizedNumber: String): SpamNumberEntity?

    @Query("SELECT COUNT(*) FROM spam_numbers")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SpamNumberEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<SpamNumberEntity>)

    @Query("DELETE FROM spam_numbers WHERE normalizedNumber = :normalizedNumber")
    suspend fun deleteByNormalizedNumber(normalizedNumber: String)

    @Query("DELETE FROM spam_numbers WHERE source = :source")
    suspend fun deleteBySource(source: String)

    @Query("DELETE FROM spam_numbers")
    suspend fun clearAll()
}
