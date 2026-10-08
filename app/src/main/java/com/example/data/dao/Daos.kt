package com.example.data.dao

import androidx.room.*
import com.example.data.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ClientDao {
    @Query("SELECT * FROM clients WHERE isDeleted = 0 ORDER BY isPinned DESC, name ASC")
    fun getAllClientsFlow(): Flow<List<Client>>

    @Query("SELECT * FROM clients WHERE isDeleted = 0 ORDER BY isPinned DESC, name ASC")
    suspend fun getAllClients(): List<Client>

    @Query("SELECT * FROM clients WHERE id = :id AND isDeleted = 0 LIMIT 1")
    fun getClientByIdFlow(id: Int): Flow<Client?>

    @Query("SELECT * FROM clients WHERE id = :id AND isDeleted = 0 LIMIT 1")
    suspend fun getClientById(id: Int): Client?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClient(client: Client): Long

    @Update
    suspend fun updateClient(client: Client)

    @Delete
    suspend fun deleteClient(client: Client)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(clients: List<Client>)

    @Query("SELECT COUNT(*) FROM clients WHERE isDeleted = 0")
    suspend fun getClientsCount(): Int

    @Query("DELETE FROM clients")
    suspend fun deleteAllClients()

    @Query("SELECT * FROM clients WHERE isDeleted = 0 ORDER BY isPinned DESC, name ASC")
    fun getAllClientsPagingSource(): androidx.paging.PagingSource<Int, Client>

    @Query("SELECT * FROM clients WHERE balance > 0 AND isDeleted = 0 ORDER BY balance DESC LIMIT :limit")
    suspend fun getTopDebtors(limit: Int): List<Client>

    @Query("SELECT * FROM clients WHERE (name LIKE :query OR phone LIKE :query) AND isDeleted = 0 ORDER BY isPinned DESC, name ASC")
    fun searchClientsPagingSource(query: String): androidx.paging.PagingSource<Int, Client>

    @Query("SELECT * FROM clients WHERE (name LIKE :query OR phone LIKE :query) AND isDeleted = 0 LIMIT 50")
    fun searchClients(query: String): Flow<List<Client>>
}

@Dao
interface ItemDao {
    @Query("SELECT COUNT(*) FROM items")
    suspend fun getItemsCount(): Int

    @Query("SELECT * FROM items ORDER BY name ASC")
    fun getAllItemsFlow(): Flow<List<Item>>

    @Query("SELECT * FROM items ORDER BY name ASC")
    suspend fun getAllItems(): List<Item>

    @Query("SELECT * FROM items WHERE id = :id LIMIT 1")
    suspend fun getItemById(id: Int): Item?

    @Query("SELECT * FROM items WHERE barcode = :barcode LIMIT 1")
    suspend fun getItemByBarcode(barcode: String): Item?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: Item): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<Item>)

    @Update
    suspend fun updateItem(item: Item)

    @Delete
    suspend fun deleteItem(item: Item)

    @Query("DELETE FROM items")
    suspend fun deleteAllItems()

    @Query("SELECT * FROM items ORDER BY name ASC")
    fun getAllItemsPagingSource(): androidx.paging.PagingSource<Int, Item>

    @Query("SELECT * FROM items WHERE name LIKE :query OR barcode LIKE :query OR category LIKE :query ORDER BY name ASC")
    fun searchItemsPagingSource(query: String): androidx.paging.PagingSource<Int, Item>

    @Query("SELECT * FROM items WHERE name LIKE :query OR barcode LIKE :query OR category LIKE :query LIMIT 50")
    fun searchItems(query: String): Flow<List<Item>>

    @Query("SELECT * FROM items WHERE name LIKE :query LIMIT 50")
    suspend fun autocompleteItems(query: String): List<Item>

    @Query("SELECT * FROM items WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun getItemByName(name: String): Item?

    @Query("SELECT * FROM items WHERE name = :name COLLATE NOCASE AND supplierCompanyName = :companyName COLLATE NOCASE LIMIT 1")
    suspend fun getItemByNameAndCompany(name: String, companyName: String): Item?

}

@Dao
interface ItemCategoryDao {
    @Query("SELECT * FROM item_categories ORDER BY name ASC")
    fun getAllCategoriesFlow(): Flow<List<ItemCategory>>

    @Query("SELECT * FROM item_categories ORDER BY name ASC")
    suspend fun getAllCategories(): List<ItemCategory>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: ItemCategory): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(categories: List<ItemCategory>)

    @Delete
    suspend fun deleteCategory(category: ItemCategory)

    @Query("DELETE FROM item_categories")
    suspend fun deleteAllCategories()
}

@Dao
interface ItemUnitDao {
    @Query("SELECT * FROM item_units ORDER BY name ASC")
    fun getAllUnitsFlow(): Flow<List<ItemUnit>>

    @Query("SELECT * FROM item_units ORDER BY name ASC")
    suspend fun getAllUnits(): List<ItemUnit>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUnit(unit: ItemUnit): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(units: List<ItemUnit>)

    @Delete
    suspend fun deleteUnit(unit: ItemUnit)

    @Query("DELETE FROM item_units")
    suspend fun deleteAllUnits()
}

@Dao
interface SupplierCompanyDao {
    @Query("SELECT * FROM supplier_companies ORDER BY name ASC")
    fun getAllCompaniesFlow(): Flow<List<SupplierCompany>>

    @Query("SELECT * FROM supplier_companies ORDER BY name ASC")
    suspend fun getAllCompanies(): List<SupplierCompany>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCompany(company: SupplierCompany): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(companies: List<SupplierCompany>)

    @Update
    suspend fun updateCompany(company: SupplierCompany)

    @Delete
    suspend fun deleteCompany(company: SupplierCompany)

    @Query("DELETE FROM supplier_companies")
    suspend fun deleteAllCompanies()

    @Query("SELECT * FROM supplier_companies WHERE name LIKE :query")
    fun searchCompanies(query: String): Flow<List<SupplierCompany>>
}

@Dao
interface InvoiceDao {
    @Query("SELECT * FROM invoices ORDER BY date DESC")
    fun getAllInvoicesFlow(): Flow<List<Invoice>>

    @Query("SELECT * FROM invoices ORDER BY date DESC")
    suspend fun getAllInvoices(): List<Invoice>

    @Query("SELECT * FROM invoices WHERE invoiceNumber LIKE :query OR clientName LIKE :query ORDER BY date DESC LIMIT 20")
    suspend fun searchInvoices(query: String): List<Invoice>

    @Query("SELECT COALESCE(SUM(totalAmount), 0.0) FROM invoices WHERE isDraft = 0 AND currency = :currency AND date BETWEEN :start AND :end")
    suspend fun getTotalSales(currency: String, start: Long, end: Long): Double

    @Query("SELECT COUNT(*) FROM invoices WHERE isDraft = 0 AND currency = :currency AND date BETWEEN :start AND :end")
    suspend fun getInvoicesCount(currency: String, start: Long, end: Long): Int

    @Query("SELECT * FROM invoices ORDER BY date DESC LIMIT :limit")
    suspend fun getRecentInvoices(limit: Int): List<Invoice>

    @Query("SELECT * FROM invoices WHERE id = :id LIMIT 1")
    suspend fun getInvoiceById(id: Int): Invoice?

    @Query("SELECT * FROM invoices WHERE id = :id LIMIT 1")
    fun getInvoiceByIdFlow(id: Int): Flow<Invoice?>

    @Query("SELECT * FROM invoices ORDER BY date DESC")
    fun getAllInvoicesPagingSource(): androidx.paging.PagingSource<Int, Invoice>

    @Query("SELECT * FROM invoices WHERE clientId = :clientId ORDER BY date DESC")
    fun getInvoicesByClientPagingSource(clientId: Int): androidx.paging.PagingSource<Int, Invoice>

    @Query("SELECT * FROM invoices WHERE clientId = :clientId ORDER BY date DESC")
    fun getInvoicesByClientFlow(clientId: Int): Flow<List<Invoice>>

    @Query("SELECT * FROM invoices WHERE clientId = :clientId ORDER BY date DESC")
    suspend fun getInvoicesByClient(clientId: Int): List<Invoice>

    @Query("SELECT COALESCE(SUM(CASE WHEN convertedTotalAmount > 0 THEN convertedTotalAmount ELSE totalAmount END), 0.0) FROM invoices WHERE clientId = :clientId AND isDraft = 0")
    suspend fun getClientTotalInvoiced(clientId: Int): Double

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInvoice(invoice: Invoice): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllInvoices(invoices: List<Invoice>)

    @Update
    suspend fun updateInvoice(invoice: Invoice)

    @Delete
    suspend fun deleteInvoice(invoice: Invoice)

    @Query("DELETE FROM invoices")
    suspend fun deleteAllInvoices()

    @Query("SELECT * FROM invoice_items WHERE invoiceId = :invoiceId")
    fun getInvoiceItemsFlow(invoiceId: Int): Flow<List<InvoiceItem>>

    @Query("SELECT * FROM invoice_items WHERE invoiceId = :invoiceId")
    suspend fun getInvoiceItems(invoiceId: Int): List<InvoiceItem>

    @Query("SELECT * FROM invoice_items")
    fun getAllInvoiceItemsFlow(): Flow<List<InvoiceItem>>

    @Query("SELECT * FROM invoice_items")
    suspend fun getAllInvoiceItems(): List<InvoiceItem>

    @Query("SELECT * FROM invoice_items WHERE invoiceId IN (:invoiceIds)")
    suspend fun getInvoiceItemsForInvoices(invoiceIds: List<Int>): List<InvoiceItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInvoiceItem(item: InvoiceItem): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllInvoiceItems(items: List<InvoiceItem>)

    @Query("DELETE FROM invoice_items WHERE invoiceId = :invoiceId")
    suspend fun deleteInvoiceItemsByInvoiceId(invoiceId: Int)

    @Query("DELETE FROM invoice_items")
    suspend fun deleteAllInvoiceItems()
}

@Dao
interface PaymentDao {
    @Query("SELECT * FROM payments ORDER BY date DESC")
    fun getAllPaymentsFlow(): Flow<List<Payment>>

    @Query("SELECT * FROM payments ORDER BY date DESC")
    suspend fun getAllPayments(): List<Payment>

    @Query("SELECT * FROM payments WHERE clientId = :clientId ORDER BY date DESC")
    fun getPaymentsByClientFlow(clientId: Int): Flow<List<Payment>>

    @Query("SELECT * FROM payments WHERE clientId = :clientId ORDER BY date DESC")
    suspend fun getPaymentsByClient(clientId: Int): List<Payment>

    @Query("SELECT * FROM payments WHERE invoiceId = :invoiceId ORDER BY date ASC")
    suspend fun getPaymentsByInvoice(invoiceId: Int): List<Payment>

    @Query("SELECT COALESCE(SUM(CASE WHEN convertedAmount > 0 THEN convertedAmount ELSE amount END), 0.0) FROM payments WHERE clientId = :clientId")
    suspend fun getClientTotalPaid(clientId: Int): Double

    @Query("SELECT COALESCE(SUM(amount), 0.0) FROM payments WHERE currency = :currency AND date BETWEEN :start AND :end")
    suspend fun getTotalReceipts(currency: String, start: Long, end: Long): Double

    @Query("SELECT COUNT(*) FROM payments WHERE currency = :currency AND date BETWEEN :start AND :end")
    suspend fun getPaymentsCount(currency: String, start: Long, end: Long): Int

    @Query("SELECT * FROM payments ORDER BY date DESC LIMIT :limit")
    suspend fun getRecentPayments(limit: Int): List<Payment>

    @Query("SELECT * FROM payments WHERE id = :id LIMIT 1")
    suspend fun getPaymentById(id: Int): Payment?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayment(payment: Payment): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(payments: List<Payment>)

    @Update
    suspend fun updatePayment(payment: Payment)

    @Delete
    suspend fun deletePayment(payment: Payment)

    @Query("DELETE FROM payments")
    suspend fun deleteAllPayments()
}

@Dao
interface AuditLogDao {
    @Query("SELECT * FROM audit_logs ORDER BY timestamp DESC LIMIT 200")
    fun getAllLogsFlow(): Flow<List<AuditLog>>

    @Query("SELECT * FROM audit_logs ORDER BY timestamp DESC")
    suspend fun getAllLogs(): List<AuditLog>

    @Query("SELECT * FROM audit_logs ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    suspend fun getLogsPaged(limit: Int, offset: Int): List<AuditLog>

    @Query("SELECT COUNT(*) FROM audit_logs")
    fun getActiveLogsCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM audit_logs")
    suspend fun getActiveLogsCount(): Int

    @Query("SELECT MIN(timestamp) FROM audit_logs")
    suspend fun getOldestLogTimestamp(): Long?

    @Query("SELECT MAX(timestamp) FROM audit_logs")
    suspend fun getNewestLogTimestamp(): Long?

    @Query("""
        SELECT 
            CAST(strftime('%Y', timestamp / 1000, 'unixepoch') AS INTEGER) AS year,
            CAST(strftime('%m', timestamp / 1000, 'unixepoch') AS INTEGER) AS month,
            COUNT(*) AS count,
            MIN(timestamp) AS minTimestamp,
            MAX(timestamp) AS maxTimestamp
        FROM audit_logs
        GROUP BY year, month
        ORDER BY year DESC, month DESC
    """)
    suspend fun getMonthlyAuditSummaries(): List<MonthAuditSummary>

    @Query("SELECT COUNT(*) FROM audit_logs WHERE timestamp >= :startTime AND timestamp <= :endTime")
    suspend fun getCountBetween(startTime: Long, endTime: Long): Int

    @Query("SELECT * FROM audit_logs WHERE timestamp >= :startTime AND timestamp <= :endTime ORDER BY timestamp ASC")
    suspend fun getLogsBetween(startTime: Long, endTime: Long): List<AuditLog>

    @Query("SELECT * FROM audit_logs WHERE timestamp >= :startTime AND timestamp <= :endTime ORDER BY timestamp ASC LIMIT :limit OFFSET :offset")
    suspend fun getLogsBetweenPaged(startTime: Long, endTime: Long, limit: Int, offset: Int): List<AuditLog>

    @Query("SELECT COUNT(*) FROM audit_logs WHERE timestamp < :cutoffTime")
    suspend fun getCountOlderThan(cutoffTime: Long): Int

    @Query("SELECT * FROM audit_logs WHERE timestamp < :cutoffTime ORDER BY timestamp ASC")
    suspend fun getLogsOlderThan(cutoffTime: Long): List<AuditLog>

    @Query("DELETE FROM audit_logs WHERE id IN (SELECT id FROM audit_logs WHERE timestamp >= :startTime AND timestamp <= :endTime LIMIT :batchSize)")
    suspend fun deleteBatchBetween(startTime: Long, endTime: Long, batchSize: Int): Int

    @Query("DELETE FROM audit_logs WHERE id IN (SELECT id FROM audit_logs WHERE timestamp < :cutoffTime LIMIT :batchSize)")
    suspend fun deleteBatchOlderThan(cutoffTime: Long, batchSize: Int): Int

    @Query("DELETE FROM audit_logs WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Int>): Int

    @Query("""
        SELECT * FROM audit_logs 
        WHERE (:query = '' OR details LIKE '%' || :query || '%' OR tableName LIKE '%' || :query || '%' OR operationType LIKE '%' || :query || '%')
        ORDER BY timestamp DESC 
        LIMIT :limit OFFSET :offset
    """)
    suspend fun searchLogs(query: String, limit: Int, offset: Int): List<AuditLog>

    @Query("SELECT COUNT(*) FROM audit_logs WHERE (:query = '' OR details LIKE '%' || :query || '%' OR tableName LIKE '%' || :query || '%' OR operationType LIKE '%' || :query || '%')")
    suspend fun countSearchLogs(query: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: AuditLog): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(logs: List<AuditLog>)

    @Query("DELETE FROM audit_logs")
    suspend fun deleteAllLogs()
}

@Dao
interface StoreSettingsDao {
    @Query("SELECT * FROM store_settings WHERE id = 1 LIMIT 1")
    fun getSettingsFlow(): Flow<StoreSettings?>

    @Query("SELECT * FROM store_settings WHERE id = 1 LIMIT 1")
    suspend fun getSettings(): StoreSettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateSettings(settings: StoreSettings): Long
}

@Dao
interface InstallmentDao {
    @Query("SELECT * FROM installments ORDER BY dueDate ASC")
    fun getAllInstallmentsFlow(): Flow<List<Installment>>

    @Query("SELECT * FROM installments ORDER BY dueDate ASC")
    suspend fun getAllInstallments(): List<Installment>

    @Query("SELECT * FROM installments WHERE clientId = :clientId ORDER BY dueDate ASC")
    fun getInstallmentsByClientFlow(clientId: Int): Flow<List<Installment>>

    @Query("SELECT * FROM installments WHERE clientId = :clientId ORDER BY dueDate ASC")
    suspend fun getInstallmentsByClient(clientId: Int): List<Installment>

    @Query("SELECT * FROM installments WHERE id = :id LIMIT 1")
    suspend fun getInstallmentById(id: Int): Installment?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInstallment(installment: Installment): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(installments: List<Installment>)

    @Update
    suspend fun updateInstallment(installment: Installment)

    @Delete
    suspend fun deleteInstallment(installment: Installment)

    @Query("DELETE FROM installments WHERE id = :id")
    suspend fun deleteInstallmentById(id: Int)

    @Query("DELETE FROM installments")
    suspend fun deleteAllInstallments()
}

@Dao
interface InstallmentReminderDao {
    @Query("SELECT * FROM installment_reminders ORDER BY scheduledAt DESC, id DESC")
    fun getAllRemindersFlow(): Flow<List<InstallmentReminder>>

    @Query("SELECT * FROM installment_reminders ORDER BY scheduledAt DESC, id DESC")
    suspend fun getAllReminders(): List<InstallmentReminder>

    @Query("SELECT * FROM installment_reminders WHERE status IN ('NEW', 'DISPLAYED') ORDER BY id DESC")
    fun getUnhandledRemindersFlow(): Flow<List<InstallmentReminder>>

    @Query("SELECT * FROM installment_reminders WHERE status IN ('NEW', 'DISPLAYED') ORDER BY id DESC")
    suspend fun getUnhandledReminders(): List<InstallmentReminder>

    @Query("SELECT * FROM installment_reminders WHERE id = :id LIMIT 1")
    suspend fun getReminderById(id: Int): InstallmentReminder?

    @Query("SELECT * FROM installment_reminders WHERE reminderType = :type ORDER BY id DESC")
    suspend fun getRemindersByType(type: InstallmentReminderType): List<InstallmentReminder>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(reminder: InstallmentReminder): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(reminders: List<InstallmentReminder>)

    @Update
    suspend fun updateReminder(reminder: InstallmentReminder)

    @Delete
    suspend fun deleteReminder(reminder: InstallmentReminder)

    @Query("DELETE FROM installment_reminders")
    suspend fun deleteAllReminders()

    @Query("UPDATE installment_reminders SET status = 'HANDLED', handledAt = :handledAt WHERE id = :id")
    suspend fun markReminderHandled(id: Int, handledAt: Long = System.currentTimeMillis())

    @Query("UPDATE installment_reminders SET status = 'DISPLAYED' WHERE id = :id AND status = 'NEW'")
    suspend fun markReminderDisplayed(id: Int)

    @Query("UPDATE installment_reminders SET status = 'CANCELLED' WHERE installmentId = :installmentId AND status IN ('NEW', 'DISPLAYED')")
    suspend fun cancelRemindersForInstallment(installmentId: Int)

    @Query("UPDATE installment_reminders SET status = 'CANCELLED' WHERE invoiceId = :invoiceId AND status IN ('NEW', 'DISPLAYED')")
    suspend fun cancelRemindersForInvoice(invoiceId: Int)

    @Query("UPDATE installment_reminders SET status = 'CANCELLED' WHERE customerId = :customerId AND status IN ('NEW', 'DISPLAYED')")
    suspend fun cancelRemindersForCustomer(customerId: Int)
}

@Dao
interface ExchangeRateDao {
    @Query("SELECT * FROM exchange_rates ORDER BY lastUpdated DESC")
    fun getAllRatesFlow(): Flow<List<ExchangeRate>>

    @Query("SELECT * FROM exchange_rates ORDER BY lastUpdated DESC")
    suspend fun getAllRates(): List<ExchangeRate>

    @Query("SELECT * FROM exchange_rates WHERE (fromCurrency = :fromCurr AND toCurrency = :toCurr) OR (fromCurrency = :toCurr AND toCurrency = :fromCurr) LIMIT 1")
    suspend fun getRateBetween(fromCurr: String, toCurr: String): ExchangeRate?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRate(exchangeRate: ExchangeRate): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rates: List<ExchangeRate>)

    @Update
    suspend fun updateRate(exchangeRate: ExchangeRate)

    @Delete
    suspend fun deleteRate(exchangeRate: ExchangeRate)

    @Query("DELETE FROM exchange_rates WHERE id = :id")
    suspend fun deleteRateById(id: Int)

    @Query("DELETE FROM exchange_rates")
    suspend fun deleteAllRates()
}

