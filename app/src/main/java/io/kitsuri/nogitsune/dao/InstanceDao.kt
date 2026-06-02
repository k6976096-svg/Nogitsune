package io.kitsuri.nogitsune.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface InstanceDao {
    @Query("SELECT * FROM instances ORDER BY createdAt ASC")
    suspend fun all(): List<InstanceEntity>

    @Query("SELECT * FROM instances WHERE id = :id LIMIT 1")
    suspend fun get(id: String): InstanceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: InstanceEntity)

    @Query("DELETE FROM instances WHERE id = :id")
    suspend fun delete(id: String)
}
