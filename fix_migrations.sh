sed -i '/val MIGRATION_10_11/i \
        val MIGRATION_11_12 = object : Migration(11, 12) {\
            override fun migrate(database: SupportSQLiteDatabase) {\
                database.execSQL("""\
                    CREATE TABLE IF NOT EXISTS `backup_history` (\
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,\
                        `timestamp` INTEGER NOT NULL,\
                        `destination` TEXT NOT NULL,\
                        `backupType` TEXT NOT NULL,\
                        `status` TEXT NOT NULL,\
                        `fileSizeBytes` INTEGER NOT NULL,\
                        `recordCount` INTEGER NOT NULL,\
                        `errorDetails` TEXT\
                    )\
                """.trimIndent())\
            }\
        }\
' app/src/main/java/com/example/data/database/AppDatabase.kt

sed -i 's/addMigrations(MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)/addMigrations(MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12)/' app/src/main/java/com/example/data/database/AppDatabase.kt
