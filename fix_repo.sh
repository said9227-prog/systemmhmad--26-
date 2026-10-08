head -n -20 app/src/main/java/com/example/data/repository/AppRepository.kt > tmp_repo.kt
cat << 'INNER_EOF' >> tmp_repo.kt

    // --- Backup History ---
    fun getAllBackupHistoryFlow(): Flow<List<BackupHistory>> = backupHistoryDao.getAllHistory()
    
    suspend fun insertBackupHistory(history: BackupHistory): Long = withContext(Dispatchers.IO) {
        backupHistoryDao.insert(history)
    }

    suspend fun enforceBackupRetentionPolicy(retentionCount: Int) = withContext(Dispatchers.IO) {
        backupHistoryDao.enforceRetentionPolicy(retentionCount)
    }
}
INNER_EOF
mv tmp_repo.kt app/src/main/java/com/example/data/repository/AppRepository.kt
