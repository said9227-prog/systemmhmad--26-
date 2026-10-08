package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.database.AppDatabase
import com.example.data.model.*
import com.example.ui.viewmodel.ActivityItem
import com.example.ui.viewmodel.ActivityType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext

class AppRepository(private val db: AppDatabase) {

    private val clientDao = db.clientDao()
    private val itemDao = db.itemDao()
    private val invoiceDao = db.invoiceDao()
    private val paymentDao = db.paymentDao()
    private val auditLogDao = db.auditLogDao()
    private val storeSettingsDao = db.storeSettingsDao()
    private val itemCategoryDao = db.itemCategoryDao()
    private val itemUnitDao = db.itemUnitDao()
    private val supplierCompanyDao = db.supplierCompanyDao()
    private val backupHistoryDao = db.backupHistoryDao()
    private val archivedAuditLogDao = db.archivedAuditLogDao()
    private val auditOperationDao = db.auditOperationDao()
    private val productReturnDao = db.productReturnDao()
    private val exchangeRateDao = db.exchangeRateDao()

    fun getDatabase(): AppDatabase = db

    // --- Exchange Rates ---
    fun getAllRatesFlow(): Flow<List<ExchangeRate>> = exchangeRateDao.getAllRatesFlow()
    suspend fun getAllRates(): List<ExchangeRate> = withContext(Dispatchers.IO) { exchangeRateDao.getAllRates() }
    suspend fun getRateBetween(fromCurr: String, toCurr: String): ExchangeRate? = withContext(Dispatchers.IO) { exchangeRateDao.getRateBetween(fromCurr, toCurr) }
    suspend fun insertRate(rate: ExchangeRate): Long = withContext(Dispatchers.IO) {
        val id = exchangeRateDao.insertRate(rate)
        insertLog("إضافة", "أسعار الصرف", "تم إضافة سعر صرف: 1 ${rate.fromCurrency} = ${rate.rate} ${rate.toCurrency}")
        id
    }
    suspend fun updateRate(rate: ExchangeRate) = withContext(Dispatchers.IO) {
        exchangeRateDao.updateRate(rate)
        insertLog("تعديل", "أسعار الصرف", "تم تعديل سعر صرف: 1 ${rate.fromCurrency} = ${rate.rate} ${rate.toCurrency}")
    }
    suspend fun deleteRate(rate: ExchangeRate) = withContext(Dispatchers.IO) {
        exchangeRateDao.deleteRate(rate)
        insertLog("حذف", "أسعار الصرف", "تم حذف سعر الصرف لـ ${rate.fromCurrency} -> ${rate.toCurrency}")
    }

    // --- Audit Log Helper ---
    suspend fun insertLog(operationType: String, tableName: String, details: String) {
        withContext(Dispatchers.IO) {
            auditLogDao.insertLog(
                AuditLog(
                    operationType = operationType,
                    tableName = tableName,
                    details = details
                )
            )
        }
    }

    // --- Audit Log Advanced Queries & Management ---
    fun getActiveLogsCountFlow(): Flow<Int> = auditLogDao.getActiveLogsCountFlow()
    suspend fun getActiveLogsCount(): Int = withContext(Dispatchers.IO) { auditLogDao.getActiveLogsCount() }
    suspend fun getOldestLogTimestamp(): Long? = withContext(Dispatchers.IO) { auditLogDao.getOldestLogTimestamp() }
    suspend fun getNewestLogTimestamp(): Long? = withContext(Dispatchers.IO) { auditLogDao.getNewestLogTimestamp() }
    suspend fun getMonthlyAuditSummaries(): List<MonthAuditSummary> = withContext(Dispatchers.IO) { auditLogDao.getMonthlyAuditSummaries() }
    suspend fun getLogsPaged(limit: Int, offset: Int): List<AuditLog> = withContext(Dispatchers.IO) { auditLogDao.getLogsPaged(limit, offset) }
    suspend fun searchActiveLogs(query: String, limit: Int, offset: Int): List<AuditLog> = withContext(Dispatchers.IO) { auditLogDao.searchLogs(query, limit, offset) }

    // --- Archived Audit Logs ---
    fun getArchivedLogsCountFlow(): Flow<Int> = archivedAuditLogDao.getArchivedCountFlow()
    suspend fun getArchivedLogsCount(): Int = withContext(Dispatchers.IO) { archivedAuditLogDao.getArchivedCount() }
    suspend fun getArchivedLogsPaged(limit: Int, offset: Int): List<ArchivedAuditLog> = withContext(Dispatchers.IO) { archivedAuditLogDao.getArchivedLogsPaged(limit, offset) }
    suspend fun searchArchivedLogs(query: String, limit: Int, offset: Int): List<ArchivedAuditLog> = withContext(Dispatchers.IO) { archivedAuditLogDao.searchArchivedLogs(query, limit, offset) }

    // --- Audit Operations History (Audit the Audit System) ---
    fun getAllAuditOperationsFlow(): Flow<List<AuditManagementOperation>> = auditOperationDao.getAllOperationsFlow()
    suspend fun insertAuditOperation(op: AuditManagementOperation): Long = withContext(Dispatchers.IO) { auditOperationDao.insertOperation(op) }
    suspend fun updateAuditOperation(op: AuditManagementOperation) = withContext(Dispatchers.IO) { auditOperationDao.updateOperation(op) }

    // --- Paging Support ---
    fun getAllClientsPaged(pageSize: Int = 50): Flow<androidx.paging.PagingData<Client>> {
        return androidx.paging.Pager(
            config = androidx.paging.PagingConfig(pageSize = pageSize, enablePlaceholders = false),
            pagingSourceFactory = { clientDao.getAllClientsPagingSource() }
        ).flow
    }

    fun searchClientsPaged(query: String, pageSize: Int = 50): Flow<androidx.paging.PagingData<Client>> {
        return androidx.paging.Pager(
            config = androidx.paging.PagingConfig(pageSize = pageSize, enablePlaceholders = false),
            pagingSourceFactory = { clientDao.searchClientsPagingSource("%$query%") }
        ).flow
    }

    fun getAllItemsPaged(pageSize: Int = 50): Flow<androidx.paging.PagingData<Item>> {
        return androidx.paging.Pager(
            config = androidx.paging.PagingConfig(pageSize = pageSize, enablePlaceholders = false),
            pagingSourceFactory = { itemDao.getAllItemsPagingSource() }
        ).flow
    }

    fun searchItemsPaged(query: String, pageSize: Int = 50): Flow<androidx.paging.PagingData<Item>> {
        return androidx.paging.Pager(
            config = androidx.paging.PagingConfig(pageSize = pageSize, enablePlaceholders = false),
            pagingSourceFactory = { itemDao.searchItemsPagingSource("%$query%") }
        ).flow
    }

    fun getAllInvoicesPaged(pageSize: Int = 50): Flow<androidx.paging.PagingData<Invoice>> {
        return androidx.paging.Pager(
            config = androidx.paging.PagingConfig(pageSize = pageSize, enablePlaceholders = false),
            pagingSourceFactory = { invoiceDao.getAllInvoicesPagingSource() }
        ).flow
    }

    fun getInvoicesByClientPaged(clientId: Int, pageSize: Int = 50): Flow<androidx.paging.PagingData<Invoice>> {
        return androidx.paging.Pager(
            config = androidx.paging.PagingConfig(pageSize = pageSize, enablePlaceholders = false),
            pagingSourceFactory = { invoiceDao.getInvoicesByClientPagingSource(clientId) }
        ).flow
    }

    // --- Dashboard Aggregations (Optimized) ---
    suspend fun getDashboardTotals(currency: String, start: Long, end: Long): Triple<Double, Double, Int> = withContext(Dispatchers.IO) {
        val sales = invoiceDao.getTotalSales(currency, start, end)
        val receipts = paymentDao.getTotalReceipts(currency, start, end)
        val count = invoiceDao.getInvoicesCount(currency, start, end)
        Triple(sales, receipts, count)
    }

    suspend fun getRecentActivities(limit: Int = 20): List<ActivityItem> = withContext(Dispatchers.IO) {
        val invs = invoiceDao.getRecentInvoices(limit)
        val pays = paymentDao.getRecentPayments(limit)
        
        val activities = mutableListOf<ActivityItem>()
        invs.forEach { inv ->
            activities.add(ActivityItem(
                id = inv.id,
                type = ActivityType.INVOICE,
                title = if (inv.isQuickInvoice) "فاتورة سريعة" else "فاتورة مفصلة",
                subtitle = "رقم: ${inv.invoiceNumber} | العميل: ${inv.clientName}",
                amount = inv.totalAmount,
                date = inv.date,
                isDraft = inv.isDraft,
                referenceId = inv.id
            ))
        }
        pays.forEach { pay ->
            val clientName = clientDao.getClientById(pay.clientId)?.name ?: "عميل غير معروف"
            activities.add(ActivityItem(
                id = pay.id,
                type = ActivityType.PAYMENT,
                title = "دفعة مستلمة (${pay.currency})",
                subtitle = "العميل: $clientName | طريقة الدفع: ${pay.paymentMethod}",
                amount = pay.amount,
                date = pay.date,
                isDraft = false,
                referenceId = pay.id
            ))
        }
        activities.sortedByDescending { it.date }.take(limit)
    }

    suspend fun getPaymentsCount(currency: String, start: Long, end: Long): Int = withContext(Dispatchers.IO) {
        paymentDao.getPaymentsCount(currency, start, end)
    }

    suspend fun getClientsCount(): Int = withContext(Dispatchers.IO) {
        clientDao.getClientsCount()
    }

    suspend fun getItemsCount(): Int = withContext(Dispatchers.IO) {
        itemDao.getItemsCount()
    }

    suspend fun getTopDebtors(limit: Int): List<Client> = withContext(Dispatchers.IO) {
        clientDao.getTopDebtors(limit)
    }

    suspend fun searchInvoices(query: String): List<Invoice> = withContext(Dispatchers.IO) {
        invoiceDao.searchInvoices("%$query%")
    }

    // --- Clients ---
    fun getAllClientsFlow(): Flow<List<Client>> = clientDao.getAllClientsFlow()
    suspend fun getAllClients(): List<Client> = withContext(Dispatchers.IO) { clientDao.getAllClients() }
    fun getClientByIdFlow(id: Int): Flow<Client?> = clientDao.getClientByIdFlow(id)
    suspend fun getClientById(id: Int): Client? = withContext(Dispatchers.IO) { clientDao.getClientById(id) }

    fun searchClients(query: String): Flow<List<Client>> = clientDao.searchClients(query)
    
    suspend fun insertClient(client: Client): Long = withContext(Dispatchers.IO) {
        val id = clientDao.insertClient(client)
        insertLog("إضافة", "العملاء", "تم إضافة العميل: ${client.name}")
        recalculateClientBalance(id.toInt())
        id
    }

    suspend fun updateClient(client: Client) = withContext(Dispatchers.IO) {
        clientDao.updateClient(client)
        insertLog("تعديل", "العملاء", "تم تعديل بيانات العميل: ${client.name}")
        recalculateClientBalance(client.id)
    }

    suspend fun deleteClient(client: Client) = withContext(Dispatchers.IO) {
        clientDao.updateClient(client.copy(isDeleted = 1))
        insertLog("حذف", "العملاء", "تم حذف العميل (حذف مؤقت): ${client.name}")
    }

    suspend fun deleteClientPermanently(client: Client) = withContext(Dispatchers.IO) {
        val invoices = invoiceDao.getInvoicesByClient(client.id)
        val payments = paymentDao.getPaymentsByClient(client.id)
        val installments = installmentDao.getInstallmentsByClient(client.id)
        if (invoices.isNotEmpty() || payments.isNotEmpty() || installments.isNotEmpty()) {
            throw IllegalArgumentException("لا يمكن حذف العميل نهائياً لوجود معاملات مرتبطة به")
        }
        clientDao.deleteClient(client)
        insertLog("حذف نهائي", "العملاء", "تم حذف العميل نهائياً: ${client.name}")
    }

    suspend fun recalculateClientBalance(clientId: Int) = withContext(Dispatchers.IO) {
        val client = clientDao.getClientById(clientId) ?: return@withContext
        val clientBaseCurrency = com.example.domain.currency.Currency.fromCodeOrName(client.currency)

        val ratesList = exchangeRateDao.getAllRates()
        val rateMap = ratesList.associate {
            (com.example.domain.currency.Currency.fromCodeOrName(it.fromCurrency) to com.example.domain.currency.Currency.fromCodeOrName(it.toCurrency)) to java.math.BigDecimal.valueOf(it.rate)
        }

        // 1. Opening Balance Equivalent in Client Base Currency (LOCKED HISTORICALLY)
        val initialCurrency = com.example.domain.currency.Currency.fromCodeOrName(client.initialBalanceCurrency)
        val openingBalanceAmountInBase: java.math.BigDecimal = if (initialCurrency == clientBaseCurrency) {
            java.math.BigDecimal.valueOf(client.initialBalance)
        } else if (client.initialBalanceConvertedAmount != 0.0) {
            // Use permanently locked historical conversion
            java.math.BigDecimal.valueOf(client.initialBalanceConvertedAmount)
        } else if (client.initialBalance != 0.0) {
            // First time computing cross-currency opening balance: convert and lock forever into client record
            val initialMoney = com.example.domain.currency.Money(client.initialBalance, initialCurrency)
            val conv = com.example.domain.currency.CurrencyConversionService.convert(initialMoney, clientBaseCurrency, rateMap)
            val convertedVal = conv.convertedMoney.toDouble()
            val rateVal = conv.exchangeRate.toDouble()
            clientDao.updateClient(
                client.copy(
                    initialBalanceConvertedAmount = convertedVal,
                    initialBalanceExchangeRate = rateVal,
                    initialBalanceBaseCurrency = client.currency
                )
            )
            conv.convertedMoney.amount
        } else {
            java.math.BigDecimal.ZERO
        }

        val isDebit = client.balanceType == "مدين" || client.balanceType == "عليه لنا"
        val openingBalanceInBase = if (isDebit) openingBalanceAmountInBase else openingBalanceAmountInBase.negate()

        // 2. Total Invoices for Client
        val invoices = invoiceDao.getInvoicesByClient(clientId).filter { !it.isDraft }
        var totalInvoicedInBase = java.math.BigDecimal.ZERO
        for (inv in invoices) {
            val invCurrency = com.example.domain.currency.Currency.fromCodeOrName(inv.currency)
            if (invCurrency == clientBaseCurrency) {
                totalInvoicedInBase = totalInvoicedInBase.add(java.math.BigDecimal.valueOf(inv.totalAmount))
            } else {
                val convertedAmount = if (inv.convertedTotalAmount > 0.0) {
                    java.math.BigDecimal.valueOf(inv.convertedTotalAmount)
                } else if (inv.exchangeRate > 0.0 && inv.exchangeRate != 1.0) {
                    java.math.BigDecimal.valueOf(inv.totalAmount).multiply(java.math.BigDecimal.valueOf(inv.exchangeRate))
                } else {
                    com.example.domain.currency.CurrencyConversionService.convert(
                        com.example.domain.currency.Money(inv.totalAmount, invCurrency),
                        clientBaseCurrency,
                        rateMap
                    ).convertedMoney.amount
                }
                totalInvoicedInBase = totalInvoicedInBase.add(convertedAmount)
            }
        }

        // 3. Total Payments for Client
        val payments = paymentDao.getPaymentsByClient(clientId)
        var totalPaidInBase = java.math.BigDecimal.ZERO
        for (pay in payments) {
            val payCurrency = com.example.domain.currency.Currency.fromCodeOrName(pay.currency)
            if (payCurrency == clientBaseCurrency) {
                totalPaidInBase = totalPaidInBase.add(java.math.BigDecimal.valueOf(pay.amount))
            } else {
                val convertedAmount = if (pay.convertedAmount > 0.0) {
                    java.math.BigDecimal.valueOf(pay.convertedAmount)
                } else if (pay.exchangeRate > 0.0 && pay.exchangeRate != 1.0) {
                    java.math.BigDecimal.valueOf(pay.amount).multiply(java.math.BigDecimal.valueOf(pay.exchangeRate))
                } else {
                    com.example.domain.currency.CurrencyConversionService.convert(
                        com.example.domain.currency.Money(pay.amount, payCurrency),
                        clientBaseCurrency,
                        rateMap
                    ).convertedMoney.amount
                }
                totalPaidInBase = totalPaidInBase.add(convertedAmount)
            }
        }

        // 4. Total Returns Credited for Client
        val returns = productReturnDao.getReturnsByClient(clientId).filter { it.type == "CUSTOMER" && it.settlementType != "استرداد نقدي" }
        var totalReturnsInBase = java.math.BigDecimal.ZERO
        for (ret in returns) {
            val retCurrency = com.example.domain.currency.Currency.fromCodeOrName(ret.currency)
            if (retCurrency == clientBaseCurrency) {
                totalReturnsInBase = totalReturnsInBase.add(java.math.BigDecimal.valueOf(ret.totalAmount))
            } else {
                val convertedAmount = if (ret.convertedTotalAmount > 0.0) {
                    java.math.BigDecimal.valueOf(ret.convertedTotalAmount)
                } else if (ret.exchangeRate > 0.0 && ret.exchangeRate != 1.0) {
                    java.math.BigDecimal.valueOf(ret.totalAmount).multiply(java.math.BigDecimal.valueOf(ret.exchangeRate))
                } else {
                    com.example.domain.currency.CurrencyConversionService.convert(
                        com.example.domain.currency.Money(ret.totalAmount, retCurrency),
                        clientBaseCurrency,
                        rateMap
                    ).convertedMoney.amount
                }
                totalReturnsInBase = totalReturnsInBase.add(convertedAmount)
            }
        }

        val netBalanceBigDecimal = openingBalanceInBase
            .add(totalInvoicedInBase)
            .subtract(totalPaidInBase)
            .subtract(totalReturnsInBase)
            .setScale(2, java.math.RoundingMode.HALF_UP)

        val calculatedBalance = netBalanceBigDecimal.toDouble()

        if (client.balance != calculatedBalance) {
            val updatedClient = client.copy(balance = calculatedBalance)
            clientDao.updateClient(updatedClient)
        }
    }

    // --- Items ---
    fun getAllItemsFlow(): Flow<List<Item>> = itemDao.getAllItemsFlow()
    suspend fun getAllItems(): List<Item> = withContext(Dispatchers.IO) { itemDao.getAllItems() }
    suspend fun getItemById(id: Int): Item? = withContext(Dispatchers.IO) { itemDao.getItemById(id) }
    suspend fun getItemByBarcode(barcode: String): Item? = withContext(Dispatchers.IO) { itemDao.getItemByBarcode(barcode) }

    suspend fun insertItem(item: Item): Long = withContext(Dispatchers.IO) {
        val id = itemDao.insertItem(item)
        insertLog("إضافة", "الأصناف", "تم إضافة الصنف: ${item.name}")
        id
    }

    suspend fun updateItem(item: Item) = withContext(Dispatchers.IO) {
        itemDao.updateItem(item)
        insertLog("تعديل", "الأصناف", "تم تعديل الصنف: ${item.name}")
    }

    suspend fun deleteItem(item: Item) = withContext(Dispatchers.IO) {
        itemDao.deleteItem(item)
        insertLog("حذف", "الأصناف", "تم حذف الصنف: ${item.name}")
    }

    fun searchItems(query: String): Flow<List<Item>> = itemDao.searchItems("%$query%")
    suspend fun autocompleteItems(query: String): List<Item> = withContext(Dispatchers.IO) { itemDao.autocompleteItems("%$query%") }
    suspend fun getItemByName(name: String): Item? = withContext(Dispatchers.IO) { itemDao.getItemByName(name) }
    suspend fun getItemByNameAndCompany(name: String, companyName: String): Item? = withContext(Dispatchers.IO) { itemDao.getItemByNameAndCompany(name, companyName) }

    // --- Invoices ---
    fun getAllInvoicesFlow(): Flow<List<Invoice>> = invoiceDao.getAllInvoicesFlow()
    fun getInvoiceByIdFlow(id: Int): Flow<Invoice?> = invoiceDao.getInvoiceByIdFlow(id)
    suspend fun getInvoiceById(id: Int): Invoice? = withContext(Dispatchers.IO) { invoiceDao.getInvoiceById(id) }
    fun getInvoicesByClientFlow(clientId: Int): Flow<List<Invoice>> = invoiceDao.getInvoicesByClientFlow(clientId)

    suspend fun insertInvoice(invoice: Invoice, items: List<InvoiceItem>): Long = withContext(Dispatchers.IO) {
        db.withTransaction {
            val invoiceId = invoiceDao.insertInvoice(invoice).toInt()
            
            // Delete old items if updating (in case id is specified, though Room update is usually separate)
            invoiceDao.deleteInvoiceItemsByInvoiceId(invoiceId)
            
            // Insert invoice items with strict stock validation
            items.forEach { item ->
                invoiceDao.insertInvoiceItem(item.copy(invoiceId = invoiceId))
                
                // Update stock quantities if item is stored in inventory
                item.itemId?.let { storedItemId ->
                    itemDao.getItemById(storedItemId)?.let { storedItem ->
                        if (storedItem.quantity < item.quantity) {
                            throw IllegalArgumentException("الكمية المطلوبة من الصنف '${storedItem.name}' (${item.quantity}) غير متوفرة في المخزون (المتوفر: ${storedItem.quantity})")
                        }
                        val newQty = storedItem.quantity - item.quantity
                        itemDao.updateItem(storedItem.copy(quantity = newQty))
                    }
                }
            }

            // Recalculate client's balance
            recalculateClientBalance(invoice.clientId)
            
            val typeStr = if (invoice.isQuickInvoice) "سريعة" else "مفصلة"
            insertLog("إضافة", "الفواتير", "تم إنشاء فاتورة $typeStr رقم: ${invoice.invoiceNumber} للعميل: ${invoice.clientName}")
            if (invoice.isCreditOverride) {
                insertLog(
                    "تجاوز ائتماني",
                    "الائتمان",
                    "⚠️ تم منح تجاوز استثنائي للحد الائتماني بمبلغ ${invoice.overrideAmount} للفاتورة ${invoice.invoiceNumber} للعميل: ${invoice.clientName} بواسطة: ${invoice.overrideAuthorizer ?: "الإدارة"}. السبب: ${invoice.overrideReason ?: "غير محدد"}"
                )
            }
            invoiceId.toLong()
        }
    }

    suspend fun insertDetailedInvoiceWithPaymentAndSettings(
        invoice: Invoice, 
        items: List<InvoiceItem>, 
        payment: Payment?, 
        settingsToUpdate: StoreSettings?
    ): Long = withContext(Dispatchers.IO) {
        db.withTransaction {
            val invoiceId = invoiceDao.insertInvoice(invoice).toInt()
            
            // Delete old items if updating
            invoiceDao.deleteInvoiceItemsByInvoiceId(invoiceId)
            
            // Insert invoice items with strict stock validation
            items.forEach { item ->
                invoiceDao.insertInvoiceItem(item.copy(invoiceId = invoiceId))
                
                // Update stock quantities if item is stored in inventory
                item.itemId?.let { storedItemId ->
                    itemDao.getItemById(storedItemId)?.let { storedItem ->
                        if (storedItem.quantity < item.quantity) {
                            throw java.lang.IllegalArgumentException("الكمية المطلوبة من الصنف '${storedItem.name}' (${item.quantity}) غير متوفرة في المخزون (المتوفر: ${storedItem.quantity})")
                        }
                        val newQty = storedItem.quantity - item.quantity
                        itemDao.updateItem(storedItem.copy(quantity = newQty))
                    }
                }
            }

            if (payment != null) {
                paymentDao.insertPayment(payment.copy(invoiceId = invoiceId))
                updateInvoicePaymentAllocation(invoiceId)
            }

            if (settingsToUpdate != null) {
                storeSettingsDao.insertOrUpdateSettings(settingsToUpdate)
            }

            // Recalculate client's balance
            recalculateClientBalance(invoice.clientId)
            
            val typeStr = if (invoice.isQuickInvoice) "سريعة" else "مفصلة"
            insertLog("إضافة", "الفواتير", "تم إنشاء فاتورة $typeStr رقم: ${invoice.invoiceNumber} للعميل: ${invoice.clientName}")
            if (invoice.isCreditOverride) {
                insertLog(
                    "تجاوز ائتماني",
                    "الائتمان",
                    "⚠️ تم منح تجاوز استثنائي للحد الائتماني بمبلغ ${invoice.overrideAmount} للفاتورة ${invoice.invoiceNumber} للعميل: ${invoice.clientName} بواسطة: ${invoice.overrideAuthorizer ?: "الإدارة"}. السبب: ${invoice.overrideReason ?: "غير محدد"}"
                )
            }
            invoiceId.toLong()
        }
    }

    suspend fun insertInvoiceWithSettings(invoice: Invoice, settings: StoreSettings) = withContext(Dispatchers.IO) {
        db.withTransaction {
            invoiceDao.insertInvoice(invoice)
            storeSettingsDao.insertOrUpdateSettings(settings)
            recalculateClientBalance(invoice.clientId)
            val typeStr = if (invoice.isQuickInvoice) "سريعة" else "مفصلة"
            insertLog("إضافة", "الفواتير", "تم إنشاء فاتورة $typeStr رقم: ${invoice.invoiceNumber} للعميل: ${invoice.clientName}")
        }
    }

    suspend fun deleteInvoice(invoice: Invoice) = withContext(Dispatchers.IO) {
        db.withTransaction {
            // Restore inventory stock before deletion
            val items = invoiceDao.getInvoiceItems(invoice.id)
            items.forEach { item ->
                item.itemId?.let { storedItemId ->
                    itemDao.getItemById(storedItemId)?.let { storedItem ->
                        val newQty = storedItem.quantity + item.quantity
                        itemDao.updateItem(storedItem.copy(quantity = newQty))
                    }
                }
            }

            invoiceDao.deleteInvoiceItemsByInvoiceId(invoice.id)
            
            // Delete related payments to maintain consistency
            val invoicePayments = paymentDao.getPaymentsByInvoice(invoice.id)
            invoicePayments.forEach { payment ->
                paymentDao.deletePayment(payment)
            }

            invoiceDao.deleteInvoice(invoice)
            recalculateClientBalance(invoice.clientId)
            insertLog("حذف", "الفواتير", "تم حذف الفاتورة رقم: ${invoice.invoiceNumber} للعميل: ${invoice.clientName}")
        }
    }

    fun getAllInvoiceItemsFlow(): Flow<List<InvoiceItem>> = invoiceDao.getAllInvoiceItemsFlow()
    fun getInvoiceItemsFlow(invoiceId: Int): Flow<List<InvoiceItem>> = invoiceDao.getInvoiceItemsFlow(invoiceId)
    suspend fun getInvoiceItems(invoiceId: Int): List<InvoiceItem> = withContext(Dispatchers.IO) { invoiceDao.getInvoiceItems(invoiceId) }
    suspend fun getAllInvoiceItems(): List<InvoiceItem> = withContext(Dispatchers.IO) { invoiceDao.getAllInvoiceItems() }
    suspend fun getInvoiceItemsForInvoices(invoiceIds: List<Int>): List<InvoiceItem> = withContext(Dispatchers.IO) {
        if (invoiceIds.isEmpty()) emptyList()
        else {
            invoiceIds.chunked(500).flatMap { chunk ->
                invoiceDao.getInvoiceItemsForInvoices(chunk)
            }
        }
    }

    // --- Payments ---
    fun getAllPaymentsFlow(): Flow<List<Payment>> = paymentDao.getAllPaymentsFlow()
    fun getPaymentsByClientFlow(clientId: Int): Flow<List<Payment>> = paymentDao.getPaymentsByClientFlow(clientId)
    suspend fun getPaymentsByClient(clientId: Int): List<Payment> = withContext(Dispatchers.IO) { paymentDao.getPaymentsByClient(clientId) }

    // --- Invoice Payment Allocation Helper ---
    suspend fun updateInvoicePaymentAllocation(invoiceId: Int) {
        val invoice = invoiceDao.getInvoiceById(invoiceId) ?: return
        val payments = paymentDao.getPaymentsByInvoice(invoiceId)
        val ratesList = exchangeRateDao.getAllRates()
        val rateMap = ratesList.associate {
            (com.example.domain.currency.Currency.fromCodeOrName(it.fromCurrency) to com.example.domain.currency.Currency.fromCodeOrName(it.toCurrency)) to java.math.BigDecimal.valueOf(it.rate)
        }
        val invCurrency = com.example.domain.currency.Currency.fromCodeOrName(invoice.currency)

        var totalPaidInInvoiceCurrency = java.math.BigDecimal.ZERO
        for (pay in payments) {
            val payCurrency = com.example.domain.currency.Currency.fromCodeOrName(pay.currency)
            val payAmt = if (payCurrency == invCurrency) {
                java.math.BigDecimal.valueOf(pay.amount)
            } else if (pay.baseCurrency == invoice.currency && pay.convertedAmount > 0.0) {
                java.math.BigDecimal.valueOf(pay.convertedAmount)
            } else {
                com.example.domain.currency.CurrencyConversionService.convert(
                    com.example.domain.currency.Money(pay.amount, payCurrency),
                    invCurrency,
                    rateMap
                ).convertedMoney.amount
            }
            totalPaidInInvoiceCurrency = totalPaidInInvoiceCurrency.add(payAmt)
        }

        val totalPaidDouble = totalPaidInInvoiceCurrency.setScale(2, java.math.RoundingMode.HALF_UP).toDouble()
        val invTotalAmtBigDecimal = java.math.BigDecimal.valueOf(invoice.totalAmount)
        val remainingBigDecimal = invTotalAmtBigDecimal.subtract(totalPaidInInvoiceCurrency).max(java.math.BigDecimal.ZERO).setScale(2, java.math.RoundingMode.HALF_UP)
        val remainingDouble = remainingBigDecimal.toDouble()

        val updatedInvoice = invoice.copy(
            paidAmount = totalPaidDouble,
            remainingAmount = remainingDouble
        )
        invoiceDao.updateInvoice(updatedInvoice)

        // Sync linked installments if any
        val installments = installmentDao.getInstallmentsByClient(invoice.clientId).filter { it.invoiceId == invoiceId }
        for (inst in installments) {
            if (remainingDouble <= 0.0) {
                installmentDao.updateInstallment(inst.copy(isPaid = true, paidAmount = inst.amount))
            } else {
                val instPaidPortion = totalPaidDouble.coerceAtMost(inst.amount)
                installmentDao.updateInstallment(inst.copy(isPaid = false, paidAmount = instPaidPortion))
            }
        }
    }

    suspend fun insertPayment(payment: Payment): Long = withContext(Dispatchers.IO) {
        db.withTransaction {
            val id = paymentDao.insertPayment(payment)
            if (payment.invoiceId != null && payment.invoiceId > 0) {
                updateInvoicePaymentAllocation(payment.invoiceId)
            }
            recalculateClientBalance(payment.clientId)
            val client = clientDao.getClientById(payment.clientId)
            insertLog("إضافة", "المدفوعات", "تم تسجيل دفعة بقيمة: ${payment.amount} ${payment.currency} للعميل: ${client?.name ?: "غير معروف"}")
            id
        }
    }

    suspend fun insertPaymentWithSettings(payment: Payment, settings: StoreSettings?) = withContext(Dispatchers.IO) {
        db.withTransaction {
            val id = paymentDao.insertPayment(payment)
            if (payment.invoiceId != null && payment.invoiceId > 0) {
                updateInvoicePaymentAllocation(payment.invoiceId)
            }
            recalculateClientBalance(payment.clientId)
            val client = clientDao.getClientById(payment.clientId)
            insertLog("إضافة", "المدفوعات", "تم تسجيل دفعة بقيمة: ${payment.amount} ${payment.currency} للعميل: ${client?.name ?: "غير معروف"}")
            if (settings != null) {
                db.storeSettingsDao().insertOrUpdateSettings(settings)
            }
            id
        }
    }

    suspend fun deletePayment(payment: Payment) = withContext(Dispatchers.IO) {
        db.withTransaction {
            paymentDao.deletePayment(payment)
            if (payment.invoiceId != null && payment.invoiceId > 0) {
                updateInvoicePaymentAllocation(payment.invoiceId)
            }
            recalculateClientBalance(payment.clientId)
            val client = clientDao.getClientById(payment.clientId)
            insertLog("حذف", "المدفوعات", "تم حذف دفعة بقيمة: ${payment.amount} ${payment.currency} للعميل: ${client?.name ?: "غير معروف"}")
        }
    }

    suspend fun updatePayment(payment: Payment) = withContext(Dispatchers.IO) {
        db.withTransaction {
            paymentDao.updatePayment(payment)
            if (payment.invoiceId != null && payment.invoiceId > 0) {
                updateInvoicePaymentAllocation(payment.invoiceId)
            }
            recalculateClientBalance(payment.clientId)
            val client = clientDao.getClientById(payment.clientId)
            insertLog("تعديل", "المدفوعات", "تم تعديل دفعة للعميل: ${client?.name ?: "غير معروف"}")
        }
    }

    // --- Categories ---
    fun getAllCategoriesFlow(): Flow<List<ItemCategory>> = itemCategoryDao.getAllCategoriesFlow()
    suspend fun getAllCategories(): List<ItemCategory> = withContext(Dispatchers.IO) { itemCategoryDao.getAllCategories() }
    suspend fun insertCategory(category: ItemCategory): Long = withContext(Dispatchers.IO) { itemCategoryDao.insertCategory(category) }
    suspend fun deleteCategory(category: ItemCategory) = withContext(Dispatchers.IO) { itemCategoryDao.deleteCategory(category) }

    // --- Units ---
    fun getAllUnitsFlow(): Flow<List<ItemUnit>> = itemUnitDao.getAllUnitsFlow()
    suspend fun getAllUnits(): List<ItemUnit> = withContext(Dispatchers.IO) { itemUnitDao.getAllUnits() }
    suspend fun insertUnit(unit: ItemUnit): Long = withContext(Dispatchers.IO) { itemUnitDao.insertUnit(unit) }
    suspend fun deleteUnit(unit: ItemUnit) = withContext(Dispatchers.IO) { itemUnitDao.deleteUnit(unit) }

    // --- Supplier Companies ---
    fun getAllCompaniesFlow(): Flow<List<SupplierCompany>> = supplierCompanyDao.getAllCompaniesFlow()
    suspend fun getAllCompanies(): List<SupplierCompany> = withContext(Dispatchers.IO) { supplierCompanyDao.getAllCompanies() }
    suspend fun insertCompany(company: SupplierCompany): Long = withContext(Dispatchers.IO) {
        val id = supplierCompanyDao.insertCompany(company)
        insertLog("إضافة", "الموردين", "تم إضافة شركة التوريد: ${company.name}")
        id
    }
    suspend fun updateCompany(company: SupplierCompany) = withContext(Dispatchers.IO) {
        supplierCompanyDao.updateCompany(company)
        insertLog("تعديل", "الموردين", "تم تعديل شركة التوريد: ${company.name}")
    }
    suspend fun deleteCompany(company: SupplierCompany) = withContext(Dispatchers.IO) {
        supplierCompanyDao.deleteCompany(company)
        insertLog("حذف", "الموردين", "تم حذف شركة التوريد: ${company.name}")
    }
    fun searchCompanies(query: String): Flow<List<SupplierCompany>> = supplierCompanyDao.searchCompanies("%$query%")


    // --- Item Purchase History ---
    suspend fun insertItemPurchaseHistory(history: ItemPurchaseHistory): Long = db.itemPurchaseHistoryDao().insertPurchaseHistory(history)
    fun getPurchaseHistoryForItem(itemId: Int) = db.itemPurchaseHistoryDao().getPurchaseHistoryForItem(itemId)
    suspend fun getPurchaseHistoryForItemSync(itemId: Int) = db.itemPurchaseHistoryDao().getPurchaseHistoryForItemSync(itemId)
    fun getAllPurchaseHistoryFlow(): Flow<List<ItemPurchaseHistory>> = db.itemPurchaseHistoryDao().getAllPurchaseHistoryFlow()
    suspend fun getAllPurchaseHistory(): List<ItemPurchaseHistory> = withContext(Dispatchers.IO) { db.itemPurchaseHistoryDao().getAllPurchaseHistory() }
    suspend fun getPurchaseHistoryInPeriod(start: Long, end: Long): List<ItemPurchaseHistory> = withContext(Dispatchers.IO) { db.itemPurchaseHistoryDao().getPurchaseHistoryInPeriod(start, end) }
    suspend fun deletePurchaseHistoryForItem(itemId: Int) = db.itemPurchaseHistoryDao().deletePurchaseHistoryForItem(itemId)

    // --- Audit Logs ---
    fun getAllLogsFlow(): Flow<List<AuditLog>> = auditLogDao.getAllLogsFlow()

    // --- Store Settings ---
    fun getSettingsFlow(): Flow<StoreSettings?> = storeSettingsDao.getSettingsFlow()
    suspend fun getSettings(): StoreSettings? = withContext(Dispatchers.IO) { storeSettingsDao.getSettings() }
    
    suspend fun saveSettings(settings: StoreSettings) = withContext(Dispatchers.IO) {
        storeSettingsDao.insertOrUpdateSettings(settings)
        insertLog("تعديل", "الإعدادات", "تم تحديث إعدادات التطبيق والمتجر")
    }

    // --- Installments ---
    private val installmentDao = db.installmentDao()

    fun getAllInstallmentsFlow(): Flow<List<Installment>> = installmentDao.getAllInstallmentsFlow()
    fun getInstallmentsByClientFlow(clientId: Int): Flow<List<Installment>> = installmentDao.getInstallmentsByClientFlow(clientId)
    suspend fun getAllInstallments(): List<Installment> = withContext(Dispatchers.IO) { installmentDao.getAllInstallments() }

    suspend fun insertInstallment(installment: Installment): Long = withContext(Dispatchers.IO) {
        val id = installmentDao.insertInstallment(installment)
        insertLog("إضافة", "الأقساط", "تم إضافة قسط للعميل: ${installment.clientName} بمبلغ: ${installment.amount}")
        id
    }

    suspend fun updateInstallment(installment: Installment) = withContext(Dispatchers.IO) {
        installmentDao.updateInstallment(installment)
        insertLog("تعديل", "الأقساط", "تم تعديل قسط للعميل: ${installment.clientName}")
    }

    suspend fun deleteInstallment(installment: Installment) = withContext(Dispatchers.IO) {
        installmentDao.deleteInstallment(installment)
        insertLog("حذف", "الأقساط", "تم حذف قسط للعميل: ${installment.clientName}")
    }

    // --- Installment Reminders ---
    private val installmentReminderDao = db.installmentReminderDao()

    fun getAllRemindersFlow(): Flow<List<InstallmentReminder>> = installmentReminderDao.getAllRemindersFlow()
    fun getUnhandledRemindersFlow(): Flow<List<InstallmentReminder>> = installmentReminderDao.getUnhandledRemindersFlow()
    suspend fun getUnhandledReminders(): List<InstallmentReminder> = withContext(Dispatchers.IO) {
        installmentReminderDao.getUnhandledReminders()
    }
    suspend fun getReminderById(id: Int): InstallmentReminder? = withContext(Dispatchers.IO) {
        installmentReminderDao.getReminderById(id)
    }
    suspend fun insertReminder(reminder: InstallmentReminder): Long = withContext(Dispatchers.IO) {
        val id = installmentReminderDao.insertReminder(reminder)
        val typeLabel = if (reminder.reminderType == InstallmentReminderType.GENERAL_INSTALLMENT) "تنبيه أقساط عام" else "تنبيه قسط للعميل: ${reminder.customerName}"
        insertLog("إضافة", "تنبيهات الأقساط", "تم إنشاء $typeLabel")
        id
    }
    suspend fun updateReminder(reminder: InstallmentReminder) = withContext(Dispatchers.IO) {
        installmentReminderDao.updateReminder(reminder)
    }
    suspend fun deleteReminder(reminder: InstallmentReminder) = withContext(Dispatchers.IO) {
        installmentReminderDao.deleteReminder(reminder)
        insertLog("حذف", "تنبيهات الأقساط", "تم حذف التنبيه رقم: ${reminder.id}")
    }
    suspend fun markReminderHandled(id: Int, handledAt: Long = System.currentTimeMillis()) = withContext(Dispatchers.IO) {
        installmentReminderDao.markReminderHandled(id, handledAt)
    }
    suspend fun markReminderDisplayed(id: Int) = withContext(Dispatchers.IO) {
        installmentReminderDao.markReminderDisplayed(id)
    }
    suspend fun cancelRemindersForInstallment(installmentId: Int) = withContext(Dispatchers.IO) {
        installmentReminderDao.cancelRemindersForInstallment(installmentId)
    }

    // --- Backup History ---
    fun getAllBackupHistoryFlow(): Flow<List<BackupHistory>> = backupHistoryDao.getAllHistory()
    
    suspend fun insertBackupHistory(history: BackupHistory): Long = withContext(Dispatchers.IO) {
        backupHistoryDao.insert(history)
    }

    suspend fun enforceBackupRetentionPolicy(retentionCount: Int) = withContext(Dispatchers.IO) {
        backupHistoryDao.enforceRetentionPolicy(retentionCount)
    }

    // --- Product Returns (نظام المرتجعات) ---
    fun getAllReturnsFlow(): Flow<List<ProductReturn>> = productReturnDao.getAllReturnsFlow()
    suspend fun getAllReturns(): List<ProductReturn> = withContext(Dispatchers.IO) { productReturnDao.getAllReturns() }
    fun getReturnsByTypeFlow(type: String): Flow<List<ProductReturn>> = productReturnDao.getReturnsByTypeFlow(type)
    fun getReturnsByClientFlow(clientId: Int): Flow<List<ProductReturn>> = productReturnDao.getReturnsByClientFlow(clientId)
    suspend fun getReturnsByClient(clientId: Int): List<ProductReturn> = withContext(Dispatchers.IO) { productReturnDao.getReturnsByClient(clientId) }
    fun getReturnsByInvoiceIdFlow(invoiceId: Int): Flow<List<ProductReturn>> = productReturnDao.getReturnsByInvoiceIdFlow(invoiceId)
    suspend fun getReturnsByInvoiceId(invoiceId: Int): List<ProductReturn> = withContext(Dispatchers.IO) { productReturnDao.getReturnsByInvoiceId(invoiceId) }
    fun getReturnByIdFlow(id: Int): Flow<ProductReturn?> = productReturnDao.getReturnByIdFlow(id)
    suspend fun getReturnById(id: Int): ProductReturn? = withContext(Dispatchers.IO) { productReturnDao.getReturnById(id) }

    fun getAllReturnItemsFlow(): Flow<List<ProductReturnItem>> = productReturnDao.getAllReturnItemsFlow()
    fun getReturnItemsFlow(returnId: Int): Flow<List<ProductReturnItem>> = productReturnDao.getReturnItemsFlow(returnId)
    suspend fun getReturnItems(returnId: Int): List<ProductReturnItem> = withContext(Dispatchers.IO) { productReturnDao.getReturnItems(returnId) }
    suspend fun getAllReturnItems(): List<ProductReturnItem> = withContext(Dispatchers.IO) { productReturnDao.getAllReturnItems() }

    suspend fun saveCustomerReturn(
        productReturn: ProductReturn,
        items: List<ProductReturnItem>
    ): Long = withContext(Dispatchers.IO) {
        db.withTransaction {
            val returnId = productReturnDao.insertReturn(productReturn).toInt()

            items.forEach { item ->
                productReturnDao.insertReturnItem(item.copy(returnId = returnId))

                // If condition is "صالح للبيع", return item back to resellable stock
                if (item.itemCondition == "صالح للبيع" && item.itemId != null) {
                    itemDao.getItemById(item.itemId)?.let { storedItem ->
                        val newQty = storedItem.quantity + item.quantity
                        itemDao.updateItem(storedItem.copy(quantity = newQty))
                    }
                }
            }

            // Recalculate client's balance if client is attached
            productReturn.clientId?.let { clientId ->
                recalculateClientBalance(clientId)
            }

            insertLog(
                "إضافة",
                "المرتجعات",
                "تم تسجيل مرتجع مبيعات رقم ${productReturn.returnNumber} للعميل: ${productReturn.clientName} بقيمة: ${productReturn.totalAmount} ${productReturn.currency}"
            )

            returnId.toLong()
        }
    }

    suspend fun savePurchaseReturn(
        productReturn: ProductReturn,
        items: List<ProductReturnItem>
    ): Long = withContext(Dispatchers.IO) {
        db.withTransaction {
            val returnId = productReturnDao.insertReturn(productReturn).toInt()

            items.forEach { item ->
                productReturnDao.insertReturnItem(item.copy(returnId = returnId))

                // For purchase return, deduct stock from inventory with strict validation
                if (item.itemId != null) {
                    itemDao.getItemById(item.itemId)?.let { storedItem ->
                        if (storedItem.quantity < item.quantity) {
                            throw IllegalArgumentException("الكمية المرتجعة للمورد من الصنف '${storedItem.name}' (${item.quantity}) تتجاوز الكمية المتوفرة في المخزون (${storedItem.quantity})")
                        }
                        val newQty = storedItem.quantity - item.quantity
                        itemDao.updateItem(storedItem.copy(quantity = newQty))
                    }
                }
            }

            insertLog(
                "إضافة",
                "المرتجعات",
                "تم تسجيل مرتجع مشتريات رقم ${productReturn.returnNumber} للمورد: ${productReturn.supplierName} بقيمة: ${productReturn.totalAmount} ${productReturn.currency}"
            )

            returnId.toLong()
        }
    }

    suspend fun deleteReturn(productReturn: ProductReturn) = withContext(Dispatchers.IO) {
        db.withTransaction {
            val items = productReturnDao.getReturnItems(productReturn.id)

            // Revert inventory changes safely
            items.forEach { item ->
                if (item.itemId != null) {
                    itemDao.getItemById(item.itemId)?.let { storedItem ->
                        val newQty = if (productReturn.type == "CUSTOMER") {
                            // If it was customer return and was resellable, reverse addition by deducting
                            if (item.itemCondition == "صالح للبيع") {
                                (storedItem.quantity - item.quantity).coerceAtLeast(0)
                            } else storedItem.quantity
                        } else {
                            // If it was purchase return, reverse deduction by adding back to stock
                            storedItem.quantity + item.quantity
                        }
                        itemDao.updateItem(storedItem.copy(quantity = newQty))
                    }
                }
            }

            productReturnDao.deleteReturnItemsByReturnId(productReturn.id)
            productReturnDao.deleteReturn(productReturn)

            // Recalculate client balance if it was a customer return
            if (productReturn.type == "CUSTOMER" && productReturn.clientId != null) {
                recalculateClientBalance(productReturn.clientId)
            }

            insertLog(
                "حذف",
                "المرتجعات",
                "تم حذف سند المرتجع رقم: ${productReturn.returnNumber} بقيمة: ${productReturn.totalAmount}"
            )
        }
    }
}
