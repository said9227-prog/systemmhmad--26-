#!/bin/bash
sed -i 's/version = 7,/version = 8,/g' app/src/main/java/com/example/data/database/AppDatabase.kt

sed -i '/import androidx.sqlite.db.SupportSQLiteDatabase/a import androidx.room.migration.Migration' app/src/main/java/com/example/data/database/AppDatabase.kt

sed -i '/companion object {/a \
        val MIGRATION_7_8 = object : Migration(7, 8) {\
            override fun migrate(database: SupportSQLiteDatabase) {\
                database.execSQL("ALTER TABLE clients ADD COLUMN customerId TEXT NOT NULL DEFAULT \\'\\'")\
                database.execSQL("ALTER TABLE clients ADD COLUMN altPhone TEXT NOT NULL DEFAULT \\'\\'")\
                database.execSQL("ALTER TABLE clients ADD COLUMN companyName TEXT NOT NULL DEFAULT \\'\\'")\
                database.execSQL("ALTER TABLE clients ADD COLUMN city TEXT NOT NULL DEFAULT \\'\\'")\
                database.execSQL("ALTER TABLE clients ADD COLUMN dealType TEXT NOT NULL DEFAULT \\'نقدي\\'")\
                database.execSQL("ALTER TABLE clients ADD COLUMN initialBalance REAL NOT NULL DEFAULT 0.0")\
                database.execSQL("ALTER TABLE clients ADD COLUMN balanceType TEXT NOT NULL DEFAULT \\'عليه لنا\\'")\
                database.execSQL("ALTER TABLE clients ADD COLUMN paymentPeriod TEXT NOT NULL DEFAULT \\'عند الطلب\\'")\
                database.execSQL("ALTER TABLE clients ADD COLUMN defaultDueDateDays INTEGER NOT NULL DEFAULT 0")\
                database.execSQL("ALTER TABLE clients ADD COLUMN clientType TEXT NOT NULL DEFAULT \\'فرد\\'")\
                database.execSQL("ALTER TABLE clients ADD COLUMN taxNumber TEXT NOT NULL DEFAULT \\'\\'")\
                database.execSQL("ALTER TABLE clients ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")\
            }\
        }' app/src/main/java/com/example/data/database/AppDatabase.kt

sed -i 's/\.fallbackToDestructiveMigration()/.fallbackToDestructiveMigration()\n                .addMigrations(MIGRATION_7_8)/g' app/src/main/java/com/example/data/database/AppDatabase.kt
