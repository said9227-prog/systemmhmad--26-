package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.auditRetentionDataStore: DataStore<Preferences> by preferencesDataStore(name = "audit_retention_preferences")

data class AuditRetentionSettings(
    val retentionPeriodKey: String = "FOREVER", // FOREVER, FIVE_YEARS, TWO_YEARS, ONE_YEAR, SIX_MONTHS, THREE_MONTHS
    val safetyLockDays: Int = 30, // 30, 60, 90
    val autoExportBeforeDelete: Boolean = true,
    val lastCleanupTimestamp: Long = 0L,
    val lastArchivedTimestamp: Long = 0L
) {
    val retentionPeriodLabelArabic: String get() {
        return when (retentionPeriodKey) {
            "THREE_MONTHS" -> "3 أشهر"
            "SIX_MONTHS" -> "6 أشهر"
            "ONE_YEAR" -> "سنة واحدة"
            "TWO_YEARS" -> "سنتان"
            "FIVE_YEARS" -> "5 سنوات"
            else -> "الاحتفاظ الدائم (دائمًا)"
        }
    }

    val cutoffTimestampMillis: Long? get() {
        val now = System.currentTimeMillis()
        val oneDayMs = 86400000L
        return when (retentionPeriodKey) {
            "THREE_MONTHS" -> now - (90L * oneDayMs)
            "SIX_MONTHS" -> now - (180L * oneDayMs)
            "ONE_YEAR" -> now - (365L * oneDayMs)
            "TWO_YEARS" -> now - (730L * oneDayMs)
            "FIVE_YEARS" -> now - (1825L * oneDayMs)
            else -> null
        }
    }
}

class AuditRetentionPreferences(private val context: Context) {

    private object PreferencesKeys {
        val RETENTION_PERIOD = stringPreferencesKey("retention_period")
        val SAFETY_LOCK_DAYS = intPreferencesKey("safety_lock_days")
        val AUTO_EXPORT_BEFORE_DELETE = booleanPreferencesKey("auto_export_before_delete")
        val LAST_CLEANUP_TIMESTAMP = longPreferencesKey("last_cleanup_timestamp")
        val LAST_ARCHIVED_TIMESTAMP = longPreferencesKey("last_archived_timestamp")
    }

    val retentionSettingsFlow: Flow<AuditRetentionSettings> = context.auditRetentionDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            AuditRetentionSettings(
                retentionPeriodKey = preferences[PreferencesKeys.RETENTION_PERIOD] ?: "FOREVER",
                safetyLockDays = preferences[PreferencesKeys.SAFETY_LOCK_DAYS] ?: 30,
                autoExportBeforeDelete = preferences[PreferencesKeys.AUTO_EXPORT_BEFORE_DELETE] ?: true,
                lastCleanupTimestamp = preferences[PreferencesKeys.LAST_CLEANUP_TIMESTAMP] ?: 0L,
                lastArchivedTimestamp = preferences[PreferencesKeys.LAST_ARCHIVED_TIMESTAMP] ?: 0L
            )
        }

    suspend fun updateRetentionPeriod(periodKey: String) {
        context.auditRetentionDataStore.edit { preferences ->
            preferences[PreferencesKeys.RETENTION_PERIOD] = periodKey
        }
    }

    suspend fun updateSafetyLockDays(days: Int) {
        context.auditRetentionDataStore.edit { preferences ->
            preferences[PreferencesKeys.SAFETY_LOCK_DAYS] = days
        }
    }

    suspend fun setAutoExportBeforeDelete(enabled: Boolean) {
        context.auditRetentionDataStore.edit { preferences ->
            preferences[PreferencesKeys.AUTO_EXPORT_BEFORE_DELETE] = enabled
        }
    }

    suspend fun recordCleanupTimestamp() {
        context.auditRetentionDataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_CLEANUP_TIMESTAMP] = System.currentTimeMillis()
        }
    }

    suspend fun recordArchiveTimestamp() {
        context.auditRetentionDataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_ARCHIVED_TIMESTAMP] = System.currentTimeMillis()
        }
    }
}
