package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.BackupHistory
import kotlinx.coroutines.flow.Flow

@Dao
interface BackupHistoryDao {
    @Query("SELECT * FROM backup_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<BackupHistory>>

    @Query("SELECT * FROM backup_history ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentHistory(limit: Int): List<BackupHistory>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(history: BackupHistory): Long

    @Update
    suspend fun update(history: BackupHistory)

    @Query("DELETE FROM backup_history WHERE id NOT IN (SELECT id FROM backup_history ORDER BY timestamp DESC LIMIT :retentionCount)")
    suspend fun enforceRetentionPolicy(retentionCount: Int)

    @Query("DELETE FROM backup_history")
    suspend fun clearHistory()
}
