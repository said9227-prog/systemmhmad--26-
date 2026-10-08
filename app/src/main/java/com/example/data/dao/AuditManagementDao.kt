package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.ArchivedAuditLog
import com.example.data.model.AuditManagementOperation
import kotlinx.coroutines.flow.Flow

@Dao
interface ArchivedAuditLogDao {
    @Query("SELECT COUNT(*) FROM archived_audit_logs")
    fun getArchivedCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM archived_audit_logs")
    suspend fun getArchivedCount(): Int

    @Query("SELECT * FROM archived_audit_logs ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    suspend fun getArchivedLogsPaged(limit: Int, offset: Int): List<ArchivedAuditLog>

    @Query("SELECT * FROM archived_audit_logs WHERE archivePackageName = :packageName ORDER BY timestamp ASC")
    suspend fun getLogsByPackage(packageName: String): List<ArchivedAuditLog>

    @Query("""
        SELECT * FROM archived_audit_logs 
        WHERE (:query = '' OR details LIKE '%' || :query || '%' OR tableName LIKE '%' || :query || '%' OR operationType LIKE '%' || :query || '%')
        ORDER BY timestamp DESC 
        LIMIT :limit OFFSET :offset
    """)
    suspend fun searchArchivedLogs(query: String, limit: Int, offset: Int): List<ArchivedAuditLog>

    @Query("SELECT COUNT(*) FROM archived_audit_logs WHERE (:query = '' OR details LIKE '%' || :query || '%' OR tableName LIKE '%' || :query || '%' OR operationType LIKE '%' || :query || '%')")
    suspend fun countSearchArchivedLogs(query: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArchivedLogs(logs: List<ArchivedAuditLog>)

    @Query("DELETE FROM archived_audit_logs WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Int>): Int

    @Query("DELETE FROM archived_audit_logs WHERE archivePackageName = :packageName")
    suspend fun deleteByPackage(packageName: String): Int

    @Query("DELETE FROM archived_audit_logs")
    suspend fun deleteAllArchivedLogs()
}

@Dao
interface AuditOperationDao {
    @Query("SELECT * FROM audit_management_operations ORDER BY timestamp DESC")
    fun getAllOperationsFlow(): Flow<List<AuditManagementOperation>>

    @Query("SELECT * FROM audit_management_operations ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentOperations(limit: Int): List<AuditManagementOperation>

    @Query("SELECT * FROM audit_management_operations WHERE correlationId = :correlationId LIMIT 1")
    suspend fun getOperationByCorrelationId(correlationId: String): AuditManagementOperation?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOperation(op: AuditManagementOperation): Long

    @Update
    suspend fun updateOperation(op: AuditManagementOperation)

    @Query("DELETE FROM audit_management_operations")
    suspend fun deleteAllOperations()
}
