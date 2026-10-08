package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.backupDataStore: DataStore<Preferences> by preferencesDataStore(name = "backup_preferences")

data class BackupSettingsState(
    val isAutoBackupEnabled: Boolean = false,
    val backupType: String = "FULL_BACKUP", // FULL_BACKUP, CSV, PDF
    val frequency: String = "DAILY", // DAILY, WEEKLY
    val backupExecutionHour: Int = 2, // 0..23 (افتراضياً الساعة 02:00 فجراً)
    val backupExecutionMinute: Int = 0,
    val retentionCount: Int = 7,
    val backupFolderUri: String? = null,
    val backupFolderName: String = "لم يتم تحديد مجلد",
    val syncStatus: String = "Not Enabled", // Not Enabled, Folder Required, Ready, Backup In Progress, Backup Successful, Backup Failed, Retry Scheduled, Folder Unavailable, No Network, Permission Lost
    val lastSuccessfulTimestamp: Long = 0L,
    val lastAttemptTimestamp: Long = 0L,
    val lastFailureTimestamp: Long = 0L,
    val lastErrorMessage: String? = null,
    val lastBackupType: String? = null,
    val lastFileName: String? = null,
    val lastFileSizeBytes: Long = 0L
)

class BackupPreferences(private val context: Context) {

    private object PreferencesKeys {
        val IS_AUTO_BACKUP_ENABLED = booleanPreferencesKey("is_auto_backup_enabled")
        val BACKUP_TYPE = stringPreferencesKey("backup_type")
        val FREQUENCY = stringPreferencesKey("frequency")
        val BACKUP_EXECUTION_HOUR = intPreferencesKey("backup_execution_hour")
        val BACKUP_EXECUTION_MINUTE = intPreferencesKey("backup_execution_minute")
        val RETENTION_COUNT = intPreferencesKey("retention_count")
        val BACKUP_FOLDER_URI = stringPreferencesKey("backup_folder_uri")
        val BACKUP_FOLDER_NAME = stringPreferencesKey("backup_folder_name")
        val SYNC_STATUS = stringPreferencesKey("sync_status")
        val LAST_SUCCESSFUL_TIMESTAMP = longPreferencesKey("last_successful_timestamp")
        val LAST_ATTEMPT_TIMESTAMP = longPreferencesKey("last_attempt_timestamp")
        val LAST_FAILURE_TIMESTAMP = longPreferencesKey("last_failure_timestamp")
        val LAST_ERROR_MESSAGE = stringPreferencesKey("last_error_message")
        val LAST_BACKUP_TYPE = stringPreferencesKey("last_backup_type")
        val LAST_FILE_NAME = stringPreferencesKey("last_file_name")
        val LAST_FILE_SIZE_BYTES = longPreferencesKey("last_file_size_bytes")
    }

    val backupSettingsFlow: Flow<BackupSettingsState> = context.backupDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val folderUri = preferences[PreferencesKeys.BACKUP_FOLDER_URI]
            val isEnabled = preferences[PreferencesKeys.IS_AUTO_BACKUP_ENABLED] ?: false
            val storedStatus = preferences[PreferencesKeys.SYNC_STATUS]

            // Calculate current effective sync status
            val resolvedStatus = when {
                !isEnabled -> "Not Enabled"
                folderUri.isNullOrBlank() -> "Folder Required"
                storedStatus != null -> storedStatus
                else -> "Ready"
            }

            BackupSettingsState(
                isAutoBackupEnabled = isEnabled,
                backupType = preferences[PreferencesKeys.BACKUP_TYPE] ?: "FULL_BACKUP",
                frequency = preferences[PreferencesKeys.FREQUENCY] ?: "DAILY",
                backupExecutionHour = preferences[PreferencesKeys.BACKUP_EXECUTION_HOUR] ?: 2,
                backupExecutionMinute = preferences[PreferencesKeys.BACKUP_EXECUTION_MINUTE] ?: 0,
                retentionCount = preferences[PreferencesKeys.RETENTION_COUNT] ?: 7,
                backupFolderUri = folderUri,
                backupFolderName = preferences[PreferencesKeys.BACKUP_FOLDER_NAME] ?: "لم يتم تحديد مجلد",
                syncStatus = resolvedStatus,
                lastSuccessfulTimestamp = preferences[PreferencesKeys.LAST_SUCCESSFUL_TIMESTAMP] ?: 0L,
                lastAttemptTimestamp = preferences[PreferencesKeys.LAST_ATTEMPT_TIMESTAMP] ?: 0L,
                lastFailureTimestamp = preferences[PreferencesKeys.LAST_FAILURE_TIMESTAMP] ?: 0L,
                lastErrorMessage = preferences[PreferencesKeys.LAST_ERROR_MESSAGE],
                lastBackupType = preferences[PreferencesKeys.LAST_BACKUP_TYPE],
                lastFileName = preferences[PreferencesKeys.LAST_FILE_NAME],
                lastFileSizeBytes = preferences[PreferencesKeys.LAST_FILE_SIZE_BYTES] ?: 0L
            )
        }

    suspend fun updateAutoBackupEnabled(enabled: Boolean) {
        context.backupDataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_AUTO_BACKUP_ENABLED] = enabled
            val folderUri = preferences[PreferencesKeys.BACKUP_FOLDER_URI]
            preferences[PreferencesKeys.SYNC_STATUS] = when {
                !enabled -> "Not Enabled"
                folderUri.isNullOrBlank() -> "Folder Required"
                else -> "Ready"
            }
        }
    }

    suspend fun updateBackupType(type: String) {
        context.backupDataStore.edit { preferences ->
            preferences[PreferencesKeys.BACKUP_TYPE] = type
        }
    }

    suspend fun updateFrequency(frequency: String) {
        context.backupDataStore.edit { preferences ->
            preferences[PreferencesKeys.FREQUENCY] = frequency
        }
    }

    suspend fun updateBackupExecutionTime(hour: Int, minute: Int) {
        context.backupDataStore.edit { preferences ->
            preferences[PreferencesKeys.BACKUP_EXECUTION_HOUR] = hour.coerceIn(0, 23)
            preferences[PreferencesKeys.BACKUP_EXECUTION_MINUTE] = minute.coerceIn(0, 59)
        }
    }

    suspend fun updateRetentionCount(count: Int) {
        context.backupDataStore.edit { preferences ->
            preferences[PreferencesKeys.RETENTION_COUNT] = count.coerceIn(1, 50)
        }
    }

    suspend fun saveBackupFolder(uriString: String, folderName: String) {
        context.backupDataStore.edit { preferences ->
            preferences[PreferencesKeys.BACKUP_FOLDER_URI] = uriString
            preferences[PreferencesKeys.BACKUP_FOLDER_NAME] = folderName
            val isEnabled = preferences[PreferencesKeys.IS_AUTO_BACKUP_ENABLED] ?: false
            preferences[PreferencesKeys.SYNC_STATUS] = if (isEnabled) "Ready" else "Not Enabled"
        }
    }

    suspend fun updateSyncStatus(status: String, errorMessage: String? = null) {
        context.backupDataStore.edit { preferences ->
            preferences[PreferencesKeys.SYNC_STATUS] = status
            if (errorMessage != null) {
                preferences[PreferencesKeys.LAST_ERROR_MESSAGE] = errorMessage
            }
        }
    }

    suspend fun recordBackupAttempt() {
        context.backupDataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_ATTEMPT_TIMESTAMP] = System.currentTimeMillis()
            preferences[PreferencesKeys.SYNC_STATUS] = "Backup In Progress"
        }
    }

    suspend fun recordBackupSuccess(
        type: String,
        fileName: String,
        fileSizeBytes: Long
    ) {
        val now = System.currentTimeMillis()
        context.backupDataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_SUCCESSFUL_TIMESTAMP] = now
            preferences[PreferencesKeys.LAST_ATTEMPT_TIMESTAMP] = now
            preferences[PreferencesKeys.LAST_BACKUP_TYPE] = type
            preferences[PreferencesKeys.LAST_FILE_NAME] = fileName
            preferences[PreferencesKeys.LAST_FILE_SIZE_BYTES] = fileSizeBytes
            preferences[PreferencesKeys.LAST_ERROR_MESSAGE] = ""
            preferences[PreferencesKeys.SYNC_STATUS] = "Backup Successful"
        }
    }

    suspend fun recordBackupFailure(errorMessage: String, isRetryScheduled: Boolean = false) {
        val now = System.currentTimeMillis()
        context.backupDataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_FAILURE_TIMESTAMP] = now
            preferences[PreferencesKeys.LAST_ATTEMPT_TIMESTAMP] = now
            preferences[PreferencesKeys.LAST_ERROR_MESSAGE] = errorMessage
            preferences[PreferencesKeys.SYNC_STATUS] = if (isRetryScheduled) "Retry Scheduled" else "Backup Failed"
        }
    }
}
