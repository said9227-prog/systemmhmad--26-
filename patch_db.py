import sys
content = """package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.migration.Migration
import com.example.data.dao.*
import com.example.data.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        Client::class,
        Item::class,
        Invoice::class,
        InvoiceItem::class,
        Payment::class,
        AuditLog::class,
        StoreSettings::class,
        Installment::class
    ],
    version = 8,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun clientDao(): ClientDao
    abstract fun itemDao(): ItemDao
    abstract fun invoiceDao(): InvoiceDao
    abstract fun paymentDao(): PaymentDao
    abstract fun auditLogDao(): AuditLogDao
    abstract fun storeSettingsDao(): StoreSettingsDao
    abstract fun installmentDao(): InstallmentDao

    companion object {
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE clients ADD COLUMN customerId TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE clients ADD COLUMN altPhone TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE clients ADD COLUMN companyName TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE clients ADD COLUMN city TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE clients ADD COLUMN dealType TEXT NOT NULL DEFAULT 'نقدي'")
                database.execSQL("ALTER TABLE clients ADD COLUMN initialBalance REAL NOT NULL DEFAULT 0.0")
                database.execSQL("ALTER TABLE clients ADD COLUMN balanceType TEXT NOT NULL DEFAULT 'عليه لنا'")
                database.execSQL("ALTER TABLE clients ADD COLUMN paymentPeriod TEXT NOT NULL DEFAULT 'عند الطلب'")
                database.execSQL("ALTER TABLE clients ADD COLUMN defaultDueDateDays INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE clients ADD COLUMN clientType TEXT NOT NULL DEFAULT 'فرد'")
                database.execSQL("ALTER TABLE clients ADD COLUMN taxNumber TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE clients ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "client_accounts_pro_db"
                )
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        INSTANCE?.let { database ->
                            CoroutineScope(Dispatchers.IO).launch {
                                database.storeSettingsDao().insertOrUpdateSettings(StoreSettings())
                            }
                        }
                    }
                })
                .addMigrations(MIGRATION_7_8)
                .fallbackToDestructiveMigration()
                .build()
                
                INSTANCE = instance
                instance
            }
        }
    }
}
"""

with open('app/src/main/java/com/example/data/database/AppDatabase.kt', 'w', encoding='utf-8') as f:
    f.write(content)
