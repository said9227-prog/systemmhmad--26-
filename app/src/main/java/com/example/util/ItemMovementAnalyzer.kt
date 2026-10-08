package com.example.util

import com.example.data.model.*

object ItemMovementAnalyzer {

    fun analyze(
        invoices: List<Invoice>,
        invoiceItems: List<InvoiceItem>,
        returns: List<ProductReturn>,
        returnItems: List<ProductReturnItem>,
        itemsCatalog: List<Item>,
        clients: List<Client>,
        period: ItemMovementPeriod,
        currencyFilter: String = "الكل",
        searchQuery: String = "",
        sortBy: ItemMovementSort = ItemMovementSort.QUANTITY
    ): Pair<List<ItemMovementSummary>, ItemMovementGlobalStats> {
        val (startTime, endTime) = period.getTimeRange()

        // 1. Index Invoices by ID (valid non-draft invoices in time range and currency)
        val filteredInvoicesMap = invoices.asSequence()
            .filter { !it.isDraft }
            .filter { it.date in startTime..endTime }
            .filter { currencyFilter == "الكل" || it.currency == currencyFilter }
            .associateBy { it.id }

        // 2. Index Customer Returns by ID (only CUSTOMER returns in time range and currency)
        val filteredReturnsMap = returns.asSequence()
            .filter { it.type == "CUSTOMER" }
            .filter { it.date in startTime..endTime }
            .filter { currencyFilter == "الكل" || it.currency == currencyFilter }
            .associateBy { it.id }

        // 3. Index items catalog by ID and by clean name
        val itemCatalogById = itemsCatalog.associateBy { it.id }
        val itemCatalogByName = itemsCatalog.associateBy { it.name.trim().lowercase() }
        val clientsById = clients.associateBy { it.id }
        val clientsByName = clients.associateBy { it.name.trim().lowercase() }

        // Temporary data structures per item
        class CustomerItemBucket(
            val clientId: Int?,
            val clientName: String
        ) {
            var grossSoldQty = 0
            var returnedQty = 0
            var grossSoldAmount = 0.0
            var returnedAmount = 0.0
            val invoicesList = mutableListOf<ItemMovementInvoiceRecord>()
            val returnsList = mutableListOf<ItemMovementReturnRecord>()
        }

        class ItemBucket(
            val itemId: Int?,
            val itemName: String
        ) {
            var grossSoldQty = 0
            var returnedQty = 0
            var grossSoldAmount = 0.0
            var returnedAmount = 0.0
            val invoiceIds = mutableSetOf<Int>()
            val returnIds = mutableSetOf<Int>()
            val customersMap = mutableMapOf<String, CustomerItemBucket>() // key: normalized client name or id
        }

        val itemBuckets = mutableMapOf<String, ItemBucket>()

        fun getItemBucket(itemId: Int?, rawName: String): ItemBucket {
            val key = itemId?.let { "id_$it" } ?: "name_${rawName.trim().lowercase()}"
            return itemBuckets.getOrPut(key) {
                // Find best display name from catalog if available
                val matchedItem = (itemId?.let { itemCatalogById[it] })
                    ?: itemCatalogByName[rawName.trim().lowercase()]
                val finalName = matchedItem?.name ?: rawName.trim().ifBlank { "صنف غير معروف" }
                ItemBucket(itemId = matchedItem?.id ?: itemId, itemName = finalName)
            }
        }

        // 4. Process all invoice items
        for (invItem in invoiceItems) {
            val invoice = filteredInvoicesMap[invItem.invoiceId] ?: continue
            val bucket = getItemBucket(invItem.itemId, invItem.itemName)

            val qty = invItem.quantity
            val amount = invItem.totalPrice

            bucket.grossSoldQty += qty
            bucket.grossSoldAmount += amount
            bucket.invoiceIds.add(invoice.id)

            // Associate with client
            val clientKey = if (invoice.clientId > 0) "id_${invoice.clientId}" else "name_${invoice.clientName.trim().lowercase()}"
            val cBucket = bucket.customersMap.getOrPut(clientKey) {
                CustomerItemBucket(
                    clientId = if (invoice.clientId > 0) invoice.clientId else null,
                    clientName = invoice.clientName.trim().ifBlank { "عميل عام (نقدي)" }
                )
            }
            cBucket.grossSoldQty += qty
            cBucket.grossSoldAmount += amount
            cBucket.invoicesList.add(
                ItemMovementInvoiceRecord(
                    invoiceId = invoice.id,
                    invoiceNumber = invoice.invoiceNumber,
                    date = invoice.date,
                    quantity = qty,
                    unitPrice = invItem.unitPrice,
                    totalPrice = amount,
                    currency = invoice.currency
                )
            )
        }

        // 5. Process customer returns (Deduct from movement)
        for (retItem in returnItems) {
            val ret = filteredReturnsMap[retItem.returnId] ?: continue
            val bucket = getItemBucket(retItem.itemId, retItem.itemName)

            val qty = retItem.quantity
            val amount = retItem.totalPrice

            bucket.returnedQty += qty
            bucket.returnedAmount += amount
            bucket.returnIds.add(ret.id)

            val clientKey = (ret.clientId?.let { if (it > 0) "id_$it" else null })
                ?: "name_${ret.clientName.trim().lowercase()}"
            val cBucket = bucket.customersMap.getOrPut(clientKey) {
                CustomerItemBucket(
                    clientId = ret.clientId,
                    clientName = ret.clientName.trim().ifBlank { "عميل عام (نقدي)" }
                )
            }
            cBucket.returnedQty += qty
            cBucket.returnedAmount += amount
            cBucket.returnsList.add(
                ItemMovementReturnRecord(
                    returnId = ret.id,
                    returnNumber = ret.returnNumber,
                    date = ret.date,
                    quantity = qty,
                    unitPrice = retItem.unitPrice,
                    totalPrice = amount,
                    reason = ret.reason.ifBlank { "مرتجع مبيعات" }
                )
            )
        }

        // 6. Convert buckets to ItemMovementSummary
        var totalUnitsSoldNet = 0
        var totalUnitsReturned = 0
        var totalNetRevenue = 0.0
        val allUniqueClients = mutableSetOf<String>()

        val summaries = itemBuckets.values.mapNotNull { bucket ->
            val netQty = bucket.grossSoldQty - bucket.returnedQty
            val netAmt = bucket.grossSoldAmount - bucket.returnedAmount

            // Only include items that had some activity (gross sold > 0 or returned > 0)
            if (bucket.grossSoldQty == 0 && bucket.returnedQty == 0) return@mapNotNull null

            // Find catalog item details
            val catalogItem = (bucket.itemId?.let { itemCatalogById[it] })
                ?: itemCatalogByName[bucket.itemName.trim().lowercase()]

            // Convert customer buckets
            val totalItemNetQty = netQty.coerceAtLeast(1)
            val sortedCustomers = bucket.customersMap.values.map { c ->
                val cNetQty = c.grossSoldQty - c.returnedQty
                val cNetAmt = c.grossSoldAmount - c.returnedAmount
                val matchedClient = (c.clientId?.let { clientsById[it] })
                    ?: clientsByName[c.clientName.trim().lowercase()]

                CustomerMovementDetail(
                    clientId = matchedClient?.id ?: c.clientId,
                    clientName = matchedClient?.name ?: c.clientName,
                    phone = matchedClient?.phone ?: "",
                    grossSoldQuantity = c.grossSoldQty,
                    returnedQuantity = c.returnedQty,
                    netQuantity = cNetQty,
                    grossSoldAmount = c.grossSoldAmount,
                    returnedAmount = c.returnedAmount,
                    netAmount = cNetAmt,
                    percentageOfTotal = if (totalItemNetQty > 0 && cNetQty > 0) {
                        (cNetQty.toDouble() / totalItemNetQty.toDouble()) * 100.0
                    } else 0.0,
                    isTopCustomer = false, // will update below
                    invoices = c.invoicesList.sortedByDescending { it.date },
                    returns = c.returnsList.sortedByDescending { it.date }
                )
            }.sortedWith(compareByDescending<CustomerMovementDetail> { it.netQuantity }.thenByDescending { it.grossSoldQuantity })

            // Mark top customer (the one who took the biggest net quantity)
            val customersWithTopFlag = if (sortedCustomers.isNotEmpty() && sortedCustomers.first().netQuantity > 0) {
                sortedCustomers.mapIndexed { index, item ->
                    if (index == 0) item.copy(isTopCustomer = true) else item
                }
            } else {
                sortedCustomers
            }

            val topCustomer = customersWithTopFlag.firstOrNull { it.isTopCustomer } ?: customersWithTopFlag.firstOrNull()

            totalUnitsSoldNet += netQty
            totalUnitsReturned += bucket.returnedQty
            totalNetRevenue += netAmt
            customersWithTopFlag.forEach { allUniqueClients.add(it.clientName) }

            ItemMovementSummary(
                itemId = catalogItem?.id ?: bucket.itemId,
                itemName = catalogItem?.name ?: bucket.itemName,
                category = catalogItem?.category ?: "",
                unit = catalogItem?.unit?.ifBlank { "قطعة" } ?: "قطعة",
                currentStock = catalogItem?.quantity ?: 0,
                grossSoldQuantity = bucket.grossSoldQty,
                returnedQuantity = bucket.returnedQty,
                netQuantity = netQty,
                grossSoldAmount = bucket.grossSoldAmount,
                returnedAmount = bucket.returnedAmount,
                netAmount = netAmt,
                defaultCurrency = if (currencyFilter != "الكل") currencyFilter else "الريال اليمني",
                invoicesCount = bucket.invoiceIds.size,
                returnsCount = bucket.returnIds.size,
                customersCount = bucket.customersMap.size,
                topCustomer = topCustomer,
                customers = customersWithTopFlag
            )
        }

        // 7. Apply search filter
        val searchFiltered = if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim().lowercase()
            summaries.filter {
                it.itemName.lowercase().contains(q) ||
                it.category.lowercase().contains(q) ||
                it.topCustomer?.clientName?.lowercase()?.contains(q) == true ||
                it.customers.any { c -> c.clientName.lowercase().contains(q) }
            }
        } else {
            summaries
        }

        // 8. Apply sort
        val sortedList = when (sortBy) {
            ItemMovementSort.QUANTITY -> searchFiltered.sortedWith(
                compareByDescending<ItemMovementSummary> { it.netQuantity }
                    .thenByDescending { it.grossSoldQuantity }
                    .thenByDescending { it.netAmount }
            )
            ItemMovementSort.AMOUNT -> searchFiltered.sortedWith(
                compareByDescending<ItemMovementSummary> { it.netAmount }
                    .thenByDescending { it.netQuantity }
            )
            ItemMovementSort.CUSTOMERS_COUNT -> searchFiltered.sortedWith(
                compareByDescending<ItemMovementSummary> { it.customersCount }
                    .thenByDescending { it.netQuantity }
            )
        }

        // Overall stats
        val mostMoved = sortedList.firstOrNull()
        val topClientOverall = sortedList.flatMap { it.customers }
            .groupBy { it.clientName }
            .maxByOrNull { entry -> entry.value.sumOf { it.netQuantity } }
            ?.key

        val globalStats = ItemMovementGlobalStats(
            totalItemsMoved = sortedList.size,
            totalUnitsSoldNet = totalUnitsSoldNet,
            totalUnitsReturned = totalUnitsReturned,
            totalNetRevenue = totalNetRevenue,
            totalUniqueClients = allUniqueClients.size,
            mostMovedItemName = mostMoved?.itemName,
            mostMovedItemQty = mostMoved?.netQuantity ?: 0,
            topCustomerOverallName = topClientOverall,
            currency = if (currencyFilter != "الكل") currencyFilter else "الريال اليمني"
        )

        return Pair(sortedList, globalStats)
    }

    /**
     * Builds a structured text report suitable for sharing via WhatsApp or copying.
     */
    fun buildShareReport(
        items: List<ItemMovementSummary>,
        stats: ItemMovementGlobalStats,
        period: ItemMovementPeriod,
        currency: String,
        storeName: String
    ): String {
        val sb = StringBuilder()
        sb.append("📊 *تقرير حركة الأصناف وسحب العملاء*\n")
        sb.append("🏬 *$storeName*\n")
        sb.append("📅 *الفترة:* ${period.labelAr}\n")
        sb.append("💰 *العملة:* $currency\n")
        sb.append("─────────────────────\n")
        sb.append("📦 *إجمالي الأصناف المتحركة:* ${stats.totalItemsMoved}\n")
        sb.append("📈 *صافي الوحدات المباعة:* ${stats.totalUnitsSoldNet}\n")
        if (stats.totalUnitsReturned > 0) {
            sb.append("🔄 *الوحدات المرتجعة:* ${stats.totalUnitsReturned}\n")
        }
        sb.append("💵 *إجمالي صافي المبيعات:* ${FormatUtils.formatAmount(stats.totalNetRevenue)} $currency\n")
        sb.append("👥 *إجمالي العملاء المتفاعلين:* ${stats.totalUniqueClients}\n")
        sb.append("═════════════════════\n\n")

        items.take(15).forEachIndexed { index, item ->
            val rankIcon = when (index) {
                0 -> "🥇"
                1 -> "🥈"
                2 -> "🥉"
                else -> "#${index + 1}"
            }
            sb.append("$rankIcon *${item.itemName}*\n")
            sb.append("   • صافي السحب: *${item.netQuantity}* ${item.unit}")
            if (item.returnedQuantity > 0) {
                sb.append(" (مباع: ${item.grossSoldQuantity} / مرتجع: ${item.returnedQuantity})")
            }
            sb.append("\n")
            sb.append("   • القيمة: ${FormatUtils.formatAmount(item.netAmount)} $currency\n")

            if (item.topCustomer != null && item.topCustomer.netQuantity > 0) {
                sb.append("   👑 *الأكثر سحباً:* ${item.topCustomer.clientName} (${item.topCustomer.netQuantity} ${item.unit} - %.0f%%)\n".format(item.topCustomer.percentageOfTotal))
            }

            if (item.customers.size > 1) {
                sb.append("   👥 بقية العملاء:\n")
                item.customers.drop(1).take(3).forEach { c ->
                    sb.append("      - ${c.clientName}: ${c.netQuantity} ${item.unit}\n")
                }
                if (item.customers.size > 4) {
                    val othersCount = item.customers.drop(4).size
                    val othersQty = item.customers.drop(4).sumOf { it.netQuantity }
                    sb.append("      - $othersCount عملاء آخرين: $othersQty ${item.unit}\n")
                }
            }
            sb.append("─────────────────────\n")
        }

        sb.append("\nتم التصدير من نظام الحسابات وإدارة المبيعات")
        return sb.toString()
    }
}
