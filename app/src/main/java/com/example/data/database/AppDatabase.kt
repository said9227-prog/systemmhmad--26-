package com.example.data.database

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
    entities = [BackupHistory::class, 
        Client::class,
        Item::class,
        Invoice::class,
        InvoiceItem::class,
        Payment::class,
        AuditLog::class,
        StoreSettings::class,
        Installment::class,
        ItemCategory::class,
        ItemUnit::class,
        ItemPurchaseHistory::class,
        SupplierCompany::class,
        InstallmentReminder::class,
        ArchivedAuditLog::class,
        AuditManagementOperation::class,
        ProductReturn::class,
        ProductReturnItem::class,
        ExchangeRate::class
    ],
    version = 24,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun backupHistoryDao(): BackupHistoryDao
    abstract fun clientDao(): ClientDao
    abstract fun itemDao(): ItemDao
    abstract fun invoiceDao(): InvoiceDao
    abstract fun paymentDao(): PaymentDao
    abstract fun auditLogDao(): AuditLogDao
    abstract fun storeSettingsDao(): StoreSettingsDao
    abstract fun installmentDao(): InstallmentDao
    abstract fun itemCategoryDao(): ItemCategoryDao
    abstract fun itemUnitDao(): ItemUnitDao
    abstract fun itemPurchaseHistoryDao(): ItemPurchaseHistoryDao
    abstract fun supplierCompanyDao(): SupplierCompanyDao
    abstract fun installmentReminderDao(): InstallmentReminderDao
    abstract fun archivedAuditLogDao(): ArchivedAuditLogDao
    abstract fun auditOperationDao(): AuditOperationDao
    abstract fun productReturnDao(): ProductReturnDao
    abstract fun exchangeRateDao(): ExchangeRateDao

    companion object {
        const val CURRENT_DATABASE_VERSION = 24
        val MIGRATION_1_2 = object : Migration(1, 2) { override fun migrate(database: SupportSQLiteDatabase) {} }
        val MIGRATION_2_3 = object : Migration(2, 3) { override fun migrate(database: SupportSQLiteDatabase) {} }
        val MIGRATION_3_4 = object : Migration(3, 4) { override fun migrate(database: SupportSQLiteDatabase) {} }
        val MIGRATION_4_5 = object : Migration(4, 5) { override fun migrate(database: SupportSQLiteDatabase) {} }
        val MIGRATION_5_6 = object : Migration(5, 6) { override fun migrate(database: SupportSQLiteDatabase) {} }
        val MIGRATION_6_7 = object : Migration(6, 7) { override fun migrate(database: SupportSQLiteDatabase) {} }

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

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add new columns to items table
                database.execSQL("ALTER TABLE items ADD COLUMN unit TEXT NOT NULL DEFAULT 'قطعة'")
                database.execSQL("ALTER TABLE items ADD COLUMN supplierType TEXT NOT NULL DEFAULT 'شركة'")
                database.execSQL("ALTER TABLE items ADD COLUMN supplierCompanyId INTEGER")
                database.execSQL("ALTER TABLE items ADD COLUMN supplierCompanyName TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE items ADD COLUMN individualSupplierName TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE items ADD COLUMN individualSupplierPhone TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE items ADD COLUMN individualSupplierNotes TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE items ADD COLUMN purchaseDate INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE items ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")

                // Create item_categories table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS item_categories (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        isPermanent INTEGER NOT NULL DEFAULT 1,
                        createdAt INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())

                // Create item_units table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS item_units (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        isPermanent INTEGER NOT NULL DEFAULT 1,
                        createdAt INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())

                // Create supplier_companies table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS supplier_companies (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        phone TEXT NOT NULL DEFAULT '',
                        address TEXT NOT NULL DEFAULT '',
                        notes TEXT NOT NULL DEFAULT '',
                        createdAt INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())

                val now = System.currentTimeMillis()
                // Seed default permanent categories
                val defaultCategories = listOf("كواشف", "أدوية", "مستلزمات طبية", "أدوات", "أخرى")
                defaultCategories.forEach { cat ->
                    database.execSQL("INSERT OR IGNORE INTO item_categories (name, isPermanent, createdAt) VALUES ('$cat', 1, $now)")
                }

                // Seed default permanent units
                val defaultUnits = listOf("قطعة", "علبة", "كرتون", "زجاجة", "شريط", "كيس", "صندوق")
                defaultUnits.forEach { u ->
                    database.execSQL("INSERT OR IGNORE INTO item_units (name, isPermanent, createdAt) VALUES ('$u', 1, $now)")
                }

                // Seed default companies
                val defaultCompanies = listOf(
                    Triple("شركة الجبل", "770000001", "صنعاء"),
                    Triple("شركة العابد", "770000002", "تعز")
                )
                defaultCompanies.forEach { (compName, compPhone, compAddr) ->
                    database.execSQL("INSERT OR IGNORE INTO supplier_companies (name, phone, address, notes, createdAt) VALUES ('$compName', '$compPhone', '$compAddr', '', $now)")
                }
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Alter store_settings with installment reminder configuration
                database.execSQL("ALTER TABLE store_settings ADD COLUMN isGeneralInstallmentReminderEnabled INTEGER NOT NULL DEFAULT 1")
                database.execSQL("ALTER TABLE store_settings ADD COLUMN generalReminderHour INTEGER NOT NULL DEFAULT 10")
                database.execSQL("ALTER TABLE store_settings ADD COLUMN generalReminderMinute INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE store_settings ADD COLUMN generalReminderDaysBeforeDue INTEGER NOT NULL DEFAULT 1")
                database.execSQL("ALTER TABLE store_settings ADD COLUMN generalReminderDaysAfterOverdue INTEGER NOT NULL DEFAULT 3")
                database.execSQL("ALTER TABLE store_settings ADD COLUMN generalReminderScope TEXT NOT NULL DEFAULT 'جميع ما سبق'")
                database.execSQL("ALTER TABLE store_settings ADD COLUMN isCustomerInstallmentReminderEnabled INTEGER NOT NULL DEFAULT 1")
                database.execSQL("ALTER TABLE store_settings ADD COLUMN allowCustomCustomerReminder INTEGER NOT NULL DEFAULT 1")

                // Create installment_reminders table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS installment_reminders (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        reminderType TEXT NOT NULL,
                        customerId INTEGER,
                        customerName TEXT,
                        invoiceId INTEGER,
                        invoiceNumber TEXT,
                        installmentId INTEGER,
                        amount REAL NOT NULL DEFAULT 0.0,
                        remainingAmount REAL NOT NULL DEFAULT 0.0,
                        dueDate INTEGER NOT NULL DEFAULT 0,
                        overdueDays INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL DEFAULT 0,
                        scheduledAt INTEGER NOT NULL DEFAULT 0,
                        handledAt INTEGER,
                        status TEXT NOT NULL DEFAULT 'NEW',
                        title TEXT NOT NULL DEFAULT '',
                        message TEXT NOT NULL DEFAULT '',
                        dueCount INTEGER NOT NULL DEFAULT 0,
                        overdueCount INTEGER NOT NULL DEFAULT 0,
                        overdueAmount REAL NOT NULL DEFAULT 0.0,
                        currency TEXT NOT NULL DEFAULT 'الريال اليمني',
                        generalScope TEXT NOT NULL DEFAULT 'جميع ما سبق',
                        notificationId INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `backup_history` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `destination` TEXT NOT NULL,
                        `backupType` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `fileSizeBytes` INTEGER NOT NULL,
                        `recordCount` INTEGER NOT NULL,
                        `errorDetails` TEXT
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add Alarm Profile fields to store_settings
                database.execSQL("ALTER TABLE store_settings ADD COLUMN generalAlarmDate INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE store_settings ADD COLUMN generalAlarmRecurrence TEXT NOT NULL DEFAULT 'يومي'")
                database.execSQL("ALTER TABLE store_settings ADD COLUMN generalSoundUri TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE store_settings ADD COLUMN generalSoundTitle TEXT NOT NULL DEFAULT 'نغمة النظام الافتراضية'")
                database.execSQL("ALTER TABLE store_settings ADD COLUMN generalVibrationEnabled INTEGER NOT NULL DEFAULT 1")
                database.execSQL("ALTER TABLE store_settings ADD COLUMN customerDefaultHour INTEGER NOT NULL DEFAULT 10")
                database.execSQL("ALTER TABLE store_settings ADD COLUMN customerDefaultMinute INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE store_settings ADD COLUMN customerDefaultRecurrence TEXT NOT NULL DEFAULT 'مرة واحدة'")
                database.execSQL("ALTER TABLE store_settings ADD COLUMN customerSoundUri TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE store_settings ADD COLUMN customerSoundTitle TEXT NOT NULL DEFAULT 'نغمة النظام الافتراضية'")
                database.execSQL("ALTER TABLE store_settings ADD COLUMN customerVibrationEnabled INTEGER NOT NULL DEFAULT 1")

                // Add sound and vibration to installment_reminders
                database.execSQL("ALTER TABLE installment_reminders ADD COLUMN soundUri TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE installment_reminders ADD COLUMN soundTitle TEXT NOT NULL DEFAULT 'نغمة النظام الافتراضية'")
                database.execSQL("ALTER TABLE installment_reminders ADD COLUMN vibrationEnabled INTEGER NOT NULL DEFAULT 1")
                database.execSQL("ALTER TABLE installment_reminders ADD COLUMN recurrence TEXT NOT NULL DEFAULT 'مرة واحدة'")
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 1. Create archived_audit_logs table and indexes
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `archived_audit_logs` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `originalId` INTEGER NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `operationType` TEXT NOT NULL,
                        `tableName` TEXT NOT NULL,
                        `details` TEXT NOT NULL,
                        `archivePackageName` TEXT NOT NULL DEFAULT '',
                        `archivedAt` INTEGER NOT NULL
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_archived_audit_logs_timestamp` ON `archived_audit_logs` (`timestamp`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_archived_audit_logs_originalId` ON `archived_audit_logs` (`originalId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_archived_audit_logs_tableName` ON `archived_audit_logs` (`tableName`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_archived_audit_logs_operationType` ON `archived_audit_logs` (`operationType`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_archived_audit_logs_archivePackageName` ON `archived_audit_logs` (`archivePackageName`)")

                // 2. Create audit_management_operations table and indexes
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `audit_management_operations` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `operationType` TEXT NOT NULL,
                        `stage` TEXT NOT NULL,
                        `correlationId` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `userName` TEXT NOT NULL,
                        `periodDescription` TEXT NOT NULL,
                        `recordCount` INTEGER NOT NULL,
                        `resultStatus` TEXT NOT NULL,
                        `durationMs` INTEGER NOT NULL,
                        `notes` TEXT,
                        `detailsJson` TEXT
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_audit_management_operations_timestamp` ON `audit_management_operations` (`timestamp`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_audit_management_operations_correlationId` ON `audit_management_operations` (`correlationId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_audit_management_operations_operationType` ON `audit_management_operations` (`operationType`)")

                // 3. Create performance indexes on audit_logs table
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_audit_logs_timestamp` ON `audit_logs` (`timestamp`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_audit_logs_tableName` ON `audit_logs` (`tableName`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_audit_logs_operationType` ON `audit_logs` (`operationType`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_audit_logs_timestamp_tableName` ON `audit_logs` (`timestamp`, `tableName`)")
            }
        }

                val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `item_purchase_history` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `itemId` INTEGER NOT NULL,
                        `supplierCompanyId` INTEGER,
                        `supplierCompanyName` TEXT NOT NULL,
                        `supplierType` TEXT NOT NULL,
                        `purchaseDate` INTEGER NOT NULL,
                        `purchasePrice` REAL NOT NULL,
                        `quantity` INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `product_returns` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `returnNumber` TEXT NOT NULL,
                        `type` TEXT NOT NULL,
                        `date` INTEGER NOT NULL,
                        `invoiceId` INTEGER,
                        `invoiceNumber` TEXT NOT NULL DEFAULT '',
                        `clientId` INTEGER,
                        `clientName` TEXT NOT NULL DEFAULT '',
                        `supplierCompanyId` INTEGER,
                        `supplierType` TEXT NOT NULL DEFAULT '',
                        `supplierName` TEXT NOT NULL DEFAULT '',
                        `totalAmount` REAL NOT NULL DEFAULT 0.0,
                        `settlementType` TEXT NOT NULL DEFAULT 'خصم من الرصيد',
                        `reason` TEXT NOT NULL DEFAULT '',
                        `notes` TEXT NOT NULL DEFAULT '',
                        `currency` TEXT NOT NULL DEFAULT 'الريال اليمني',
                        `createdAt` INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_product_returns_date` ON `product_returns` (`date`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_product_returns_type` ON `product_returns` (`type`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_product_returns_clientId` ON `product_returns` (`clientId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_product_returns_invoiceId` ON `product_returns` (`invoiceId`)")

                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `product_return_items` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `returnId` INTEGER NOT NULL,
                        `itemId` INTEGER,
                        `itemName` TEXT NOT NULL,
                        `unit` TEXT NOT NULL DEFAULT 'قطعة',
                        `quantity` INTEGER NOT NULL DEFAULT 1,
                        `unitPrice` REAL NOT NULL DEFAULT 0.0,
                        `totalPrice` REAL NOT NULL DEFAULT 0.0,
                        `itemCondition` TEXT NOT NULL DEFAULT 'صالح للبيع',
                        `originalSoldQuantity` INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_product_return_items_returnId` ON `product_return_items` (`returnId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_product_return_items_itemId` ON `product_return_items` (`itemId`)")
            }
        }

        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE store_settings ADD COLUMN isAutoNumberingEnabled INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE store_settings ADD COLUMN lastPaymentNumber INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE store_settings ADD COLUMN lastSalesReturnNumber INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE store_settings ADD COLUMN lastPurchaseReturnNumber INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE store_settings ADD COLUMN showVatAndSubtotal INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 1. Clients performance indexes
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_clients_name` ON `clients` (`name`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_clients_phone` ON `clients` (`phone`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_clients_isPinned_name` ON `clients` (`isPinned`, `name`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_clients_balance` ON `clients` (`balance`)")

                // 2. Items performance indexes
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_items_barcode` ON `items` (`barcode`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_items_name` ON `items` (`name`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_items_category` ON `items` (`category`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_items_supplierCompanyId` ON `items` (`supplierCompanyId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_items_createdAt` ON `items` (`createdAt`)")

                // 3. Supplier Companies index
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_supplier_companies_name` ON `supplier_companies` (`name`)")

                // 4. Invoices performance indexes
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_invoices_clientId` ON `invoices` (`clientId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_invoices_date` ON `invoices` (`date`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_invoices_invoiceNumber` ON `invoices` (`invoiceNumber`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_invoices_currency` ON `invoices` (`currency`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_invoices_isDraft` ON `invoices` (`isDraft`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_invoices_clientId_date` ON `invoices` (`clientId`, `date`)")

                // 5. Invoice Items performance indexes
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_invoice_items_invoiceId` ON `invoice_items` (`invoiceId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_invoice_items_itemId` ON `invoice_items` (`itemId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_invoice_items_itemName` ON `invoice_items` (`itemName`)")

                // 6. Payments performance indexes
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_payments_clientId` ON `payments` (`clientId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_payments_invoiceId` ON `payments` (`invoiceId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_payments_date` ON `payments` (`date`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_payments_currency` ON `payments` (`currency`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_payments_clientId_date` ON `payments` (`clientId`, `date`)")

                // 7. Installments performance indexes
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_installments_clientId` ON `installments` (`clientId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_installments_dueDate` ON `installments` (`dueDate`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_installments_isPaid` ON `installments` (`isPaid`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_installments_invoiceId` ON `installments` (`invoiceId`)")

                // 8. Installment Reminders performance indexes
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_installment_reminders_status` ON `installment_reminders` (`status`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_installment_reminders_reminderType` ON `installment_reminders` (`reminderType`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_installment_reminders_customerId` ON `installment_reminders` (`customerId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_installment_reminders_installmentId` ON `installment_reminders` (`installmentId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_installment_reminders_scheduledAt` ON `installment_reminders` (`scheduledAt`)")

                // 9. Item Purchase History performance indexes
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_item_purchase_history_itemId` ON `item_purchase_history` (`itemId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_item_purchase_history_supplierCompanyId` ON `item_purchase_history` (`supplierCompanyId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_item_purchase_history_purchaseDate` ON `item_purchase_history` (`purchaseDate`)")
            }
        }

        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE items ADD COLUMN purchaseCurrency TEXT NOT NULL DEFAULT 'الريال اليمني'")
                database.execSQL("ALTER TABLE items ADD COLUMN sellingCurrency TEXT NOT NULL DEFAULT 'الريال اليمني'")
                database.execSQL("ALTER TABLE clients ADD COLUMN initialBalanceCurrency TEXT NOT NULL DEFAULT 'الريال اليمني'")
                database.execSQL("ALTER TABLE clients ADD COLUMN currency TEXT NOT NULL DEFAULT 'الريال اليمني'")
            }
        }

        val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `exchange_rates` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `fromCurrency` TEXT NOT NULL,
                        `toCurrency` TEXT NOT NULL,
                        `rate` REAL NOT NULL,
                        `lastUpdated` INTEGER NOT NULL
                    )
                """.trimIndent())
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_exchange_rates_fromCurrency_toCurrency` ON `exchange_rates` (`fromCurrency`, `toCurrency`)")

                database.execSQL("ALTER TABLE invoices ADD COLUMN exchangeRate REAL NOT NULL DEFAULT 1.0")
                database.execSQL("ALTER TABLE invoices ADD COLUMN baseCurrency TEXT NOT NULL DEFAULT 'الريال اليمني'")
                database.execSQL("ALTER TABLE invoices ADD COLUMN convertedTotalAmount REAL NOT NULL DEFAULT 0.0")

                database.execSQL("ALTER TABLE payments ADD COLUMN exchangeRate REAL NOT NULL DEFAULT 1.0")
                database.execSQL("ALTER TABLE payments ADD COLUMN baseCurrency TEXT NOT NULL DEFAULT 'الريال اليمني'")
                database.execSQL("ALTER TABLE payments ADD COLUMN convertedAmount REAL NOT NULL DEFAULT 0.0")

                database.execSQL("ALTER TABLE product_returns ADD COLUMN exchangeRate REAL NOT NULL DEFAULT 1.0")
                database.execSQL("ALTER TABLE product_returns ADD COLUMN baseCurrency TEXT NOT NULL DEFAULT 'الريال اليمني'")
                database.execSQL("ALTER TABLE product_returns ADD COLUMN convertedTotalAmount REAL NOT NULL DEFAULT 0.0")

                // Seed default initial exchange rates
                val now = System.currentTimeMillis()
                database.execSQL("INSERT OR IGNORE INTO exchange_rates (fromCurrency, toCurrency, rate, lastUpdated) VALUES ('USD', 'YER', 1500.0, $now)")
                database.execSQL("INSERT OR IGNORE INTO exchange_rates (fromCurrency, toCurrency, rate, lastUpdated) VALUES ('SAR', 'YER', 400.0, $now)")
                database.execSQL("INSERT OR IGNORE INTO exchange_rates (fromCurrency, toCurrency, rate, lastUpdated) VALUES ('USD', 'SAR', 3.75, $now)")
                database.execSQL("INSERT OR IGNORE INTO exchange_rates (fromCurrency, toCurrency, rate, lastUpdated) VALUES ('EUR', 'USD', 1.08, $now)")
            }
        }

        val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE invoice_items ADD COLUMN currency TEXT NOT NULL DEFAULT 'الريال اليمني'")
                database.execSQL("ALTER TABLE invoice_items ADD COLUMN exchangeRate REAL NOT NULL DEFAULT 1.0")
                database.execSQL("ALTER TABLE invoice_items ADD COLUMN convertedTotalPrice REAL NOT NULL DEFAULT 0.0")
                database.execSQL("ALTER TABLE invoice_items ADD COLUMN originalProductPrice REAL NOT NULL DEFAULT 0.0")
                database.execSQL("ALTER TABLE invoice_items ADD COLUMN originalProductCurrency TEXT NOT NULL DEFAULT 'الريال اليمني'")
            }
        }

        val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE store_settings ADD COLUMN enableInvoiceCurrencyConverter INTEGER NOT NULL DEFAULT 1")
                database.execSQL("ALTER TABLE store_settings ADD COLUMN defaultInvoiceDisplayCurrency TEXT NOT NULL DEFAULT 'الكل'")
            }
        }

        val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE store_settings ADD COLUMN enableUnclutteredInvoiceMode INTEGER NOT NULL DEFAULT 1")
            }
        }

        val MIGRATION_22_23 = object : Migration(22, 23) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE clients ADD COLUMN initialBalanceExchangeRate REAL NOT NULL DEFAULT 1.0")
                database.execSQL("ALTER TABLE clients ADD COLUMN initialBalanceConvertedAmount REAL NOT NULL DEFAULT 0.0")
                database.execSQL("ALTER TABLE clients ADD COLUMN initialBalanceBaseCurrency TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE clients ADD COLUMN uuid TEXT NOT NULL DEFAULT ''")

                database.execSQL("ALTER TABLE items ADD COLUMN uuid TEXT NOT NULL DEFAULT ''")

                database.execSQL("ALTER TABLE invoices ADD COLUMN dueDate INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE invoices ADD COLUMN uuid TEXT NOT NULL DEFAULT ''")

                database.execSQL("ALTER TABLE invoice_items ADD COLUMN uuid TEXT NOT NULL DEFAULT ''")

                database.execSQL("ALTER TABLE payments ADD COLUMN uuid TEXT NOT NULL DEFAULT ''")

                database.execSQL("ALTER TABLE product_returns ADD COLUMN uuid TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE product_return_items ADD COLUMN uuid TEXT NOT NULL DEFAULT ''")

                // Clean up any stray EUR records
                database.execSQL("DELETE FROM exchange_rates WHERE fromCurrency = 'EUR' OR toCurrency = 'EUR'")
            }
        }

        val MIGRATION_23_24 = object : Migration(23, 24) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE clients ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
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
                                val now = System.currentTimeMillis()
                                val defaultCategories = listOf("كواشف", "أدوية", "مستلزمات طبية", "أدوات", "أخرى")
                                defaultCategories.forEach { cat ->
                                    database.itemCategoryDao().insertCategory(ItemCategory(name = cat, isPermanent = true, createdAt = now))
                                }
                                val defaultUnits = listOf("قطعة", "علبة", "كرتون", "زجاجة", "شريط", "كيس", "صندوق")
                                defaultUnits.forEach { u ->
                                    database.itemUnitDao().insertUnit(ItemUnit(name = u, isPermanent = true, createdAt = now))
                                }
                                val defaultCompanies = listOf(
                                    Triple("شركة الجبل", "770000001", "صنعاء"),
                                    Triple("شركة العابد", "770000002", "تعز")
                                )
                                defaultCompanies.forEach { (compName, compPhone, compAddr) ->
                                    database.supplierCompanyDao().insertCompany(SupplierCompany(name = compName, phone = compPhone, address = compAddr, createdAt = now))
                                }
                                 // Seed default exchange rates
                                database.exchangeRateDao().insertAll(
                                    listOf(
                                        ExchangeRate(fromCurrency = "USD", toCurrency = "YER", rate = 1500.0, lastUpdated = now),
                                        ExchangeRate(fromCurrency = "SAR", toCurrency = "YER", rate = 400.0, lastUpdated = now),
                                        ExchangeRate(fromCurrency = "USD", toCurrency = "SAR", rate = 3.75, lastUpdated = now)
                                    )
                                )
                            }
                        }
                    }
                })
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21, MIGRATION_21_22, MIGRATION_22_23, MIGRATION_23_24)
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
                
                INSTANCE = instance
                instance
            }
        }
    }
}
