package com.example.util

import com.example.data.model.*
import com.example.ui.viewmodel.ActivityItem
import com.example.ui.viewmodel.ActivityType
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.round

object FinancialCalculator {

    /**
     * Rounds a double to 2 decimal places to eliminate floating point inaccuracies.
     */
    fun round2(value: Double): Double {
        if (value.isNaN() || value.isInfinite()) return 0.0
        return round(value * 100.0) / 100.0
    }

    /**
     * Checks if a timestamp falls within a specific DashboardPeriod.
     */
    fun isInPeriod(timestamp: Long, period: DashboardPeriod, now: Long = System.currentTimeMillis()): Boolean {
        if (period == DashboardPeriod.ALL) return true
        if (timestamp <= 0) return false

        val cal = Calendar.getInstance()
        cal.timeInMillis = now

        return when (period) {
            DashboardPeriod.TODAY -> {
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val startOfDay = cal.timeInMillis
                timestamp >= startOfDay
            }
            DashboardPeriod.THIS_WEEK -> {
                cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val startOfWeek = cal.timeInMillis
                timestamp >= startOfWeek
            }
            DashboardPeriod.THIS_MONTH -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val startOfMonth = cal.timeInMillis
                timestamp >= startOfMonth
            }
            DashboardPeriod.THIS_YEAR -> {
                cal.set(Calendar.DAY_OF_YEAR, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val startOfYear = cal.timeInMillis
                timestamp >= startOfYear
            }
            DashboardPeriod.ALL -> true
        }
    }

    /**
     * Computes the complete multi-currency financial state.
     * Guarantees strict currency isolation: no values from different currencies are ever combined.
     */
    fun computeDashboardState(
        clientsList: List<Client>,
        invoicesList: List<Invoice>,
        paymentsList: List<Payment>,
        installmentsList: List<Installment>,
        storeSettings: StoreSettings,
        selectedCurrencyCode: String,
        selectedPeriod: DashboardPeriod
    ): FinancialDashboardUiState {
        val now = System.currentTimeMillis()

        // Calendar today bounds
        val calToday = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfToday = calToday.timeInMillis
        calToday.set(Calendar.HOUR_OF_DAY, 23)
        calToday.set(Calendar.MINUTE, 59)
        calToday.set(Calendar.SECOND, 59)
        calToday.set(Calendar.MILLISECOND, 999)
        val endOfToday = calToday.timeInMillis

        // Determine default currency from settings
        val defaultCurrency = CurrencyMeta.from(storeSettings.currency)

        // Find all unique currencies present across transactions, settings, and defaults
        val currencySet = mutableSetOf<CurrencyMeta>()
        currencySet.add(CurrencyMeta.YER)
        currencySet.add(CurrencyMeta.SAR)
        currencySet.add(CurrencyMeta.USD)
        currencySet.add(defaultCurrency)

        invoicesList.forEach { currencySet.add(CurrencyMeta.from(it.currency)) }
        paymentsList.forEach { currencySet.add(CurrencyMeta.from(it.currency)) }
        installmentsList.forEach { currencySet.add(CurrencyMeta.from(it.currency)) }

        val availableCurrencies = currencySet.toList()

        // Fast lookup maps
        val clientMap = clientsList.associateBy { it.id }

        // Find which clients have transactions in which currencies
        val clientCurrenciesMap = mutableMapOf<Int, MutableSet<String>>()
        invoicesList.forEach { inv ->
            val code = CurrencyMeta.from(inv.currency).code
            clientCurrenciesMap.getOrPut(inv.clientId) { mutableSetOf() }.add(code)
        }
        paymentsList.forEach { pay ->
            val code = CurrencyMeta.from(pay.currency).code
            clientCurrenciesMap.getOrPut(pay.clientId) { mutableSetOf() }.add(code)
        }
        installmentsList.forEach { inst ->
            val code = CurrencyMeta.from(inst.currency).code
            clientCurrenciesMap.getOrPut(inst.clientId) { mutableSetOf() }.add(code)
        }

        // Clients with an initial balance belong to the store's default currency
        clientsList.forEach { cl ->
            if (abs(cl.initialBalance) > 0.001) {
                clientCurrenciesMap.getOrPut(cl.id) { mutableSetOf() }.add(defaultCurrency.code)
            }
        }

        // Clients with zero transactions in any currency are assigned to default store currency
        val zeroActivityClientIds = clientsList.filter { cl ->
            val set = clientCurrenciesMap[cl.id]
            set == null || set.isEmpty()
        }.map { it.id }.toSet()

        // Pre-indexing transactions by currency in a single pass
        val invoicesByCurrency = invoicesList.groupBy { CurrencyMeta.from(it.currency).code }
        val paymentsByCurrency = paymentsList.groupBy { CurrencyMeta.from(it.currency).code }
        val installmentsByCurrency = installmentsList.groupBy { CurrencyMeta.from(it.currency).code }

        // Map of summaries per currency
        val summariesPerCurrency = mutableMapOf<String, CurrencyFinancialSummary>()

        for (curr in availableCurrencies) {
            val currCode = curr.code

            // Filter transactions for this currency
            val allInvoicesForCurr = (invoicesByCurrency[currCode] ?: emptyList()).filter { !it.isDraft }
            val allPaymentsForCurr = paymentsByCurrency[currCode] ?: emptyList()
            val allInstallmentsForCurr = installmentsByCurrency[currCode] ?: emptyList()

            // Calculate per-client balance in this specific currency
            val clientInvoicesGroup = allInvoicesForCurr.groupBy { it.clientId }
            val clientPaymentsGroup = allPaymentsForCurr.groupBy { it.clientId }

            // Active clients under this currency
            val activeClientsInCurr = mutableListOf<Client>()
            val clientBalancesInCurr = mutableMapOf<Int, Double>()

            clientsList.forEach { cl ->
                val hasTransactions = clientCurrenciesMap[cl.id]?.contains(currCode) == true
                val isZeroActivityAssignedHere = (currCode == defaultCurrency.code && zeroActivityClientIds.contains(cl.id))

                if (hasTransactions || isZeroActivityAssignedHere) {
                    activeClientsInCurr.add(cl)

                    // Compute balance in this currency
                    val totalInv = clientInvoicesGroup[cl.id]?.sumOf { it.totalAmount } ?: 0.0
                    val totalPay = clientPaymentsGroup[cl.id]?.sumOf { it.amount } ?: 0.0

                    val initialBal = if (currCode == defaultCurrency.code) {
                        val isDebit = cl.balanceType == "مدين" || cl.balanceType == "عليه لنا"
                        if (isDebit) cl.initialBalance else -cl.initialBalance
                    } else 0.0

                    val netBal = round2(initialBal + totalInv - totalPay)
                    clientBalancesInCurr[cl.id] = netBal
                }
            }

            val clientCount = activeClientsInCurr.size
            val debtors = activeClientsInCurr.filter { (clientBalancesInCurr[it.id] ?: 0.0) > 0.001 }
            val creditors = activeClientsInCurr.filter { (clientBalancesInCurr[it.id] ?: 0.0) < -0.001 }
            val balancedCount = activeClientsInCurr.count { abs(clientBalancesInCurr[it.id] ?: 0.0) <= 0.001 }

            val debtorsCount = debtors.size
            val creditorsCount = creditors.size

            // Net balance for this currency = total receivables minus payables
            val netBalance = round2(clientBalancesInCurr.values.sum())

            // Total outstanding uncollected debt from debtors
            val totalDebts = round2(debtors.sumOf { clientBalancesInCurr[it.id] ?: 0.0 })

            // Period filtered transactions
            val periodInvoices = allInvoicesForCurr.filter { isInPeriod(it.date, selectedPeriod, now) }
            val periodPayments = allPaymentsForCurr.filter { isInPeriod(it.date, selectedPeriod, now) }

            val totalSales = round2(periodInvoices.sumOf { it.totalAmount })
            val totalReceipts = round2(periodPayments.sumOf { it.amount })

            val invoicesCount = periodInvoices.size
            val paidInvoicesCount = periodInvoices.count { it.remainingAmount <= 0.01 }
            val partialInvoicesCount = periodInvoices.count { it.paidAmount > 0.01 && it.remainingAmount > 0.01 }
            val unpaidInvoicesCount = periodInvoices.count { it.paidAmount <= 0.01 }
            val paymentsCount = periodPayments.size

            // Collection rate calculation:
            // Receipts divided by total invoiced sales in this period.
            val collectionRate = when {
                totalSales > 0.01 -> round2(((totalReceipts / totalSales) * 100.0).coerceIn(0.0, 100.0))
                totalReceipts > 0.01 -> 100.0
                else -> 0.0
            }

            // Installments in this currency
            val installmentsDueToday = allInstallmentsForCurr.filter {
                !it.isPaid && it.dueDate in startOfToday..endOfToday
            }
            val installmentsOverdue = allInstallmentsForCurr.filter {
                !it.isPaid && it.dueDate < startOfToday
            }
            val totalRemainingInstallments = round2(
                allInstallmentsForCurr.filter { !it.isPaid }.sumOf { (it.amount - it.paidAmount).coerceAtLeast(0.0) }
            )

            // Top Debtors in this currency
            val topDebtors = debtors
                .map { client ->
                    CurrencyDebtorInfo(
                        client = client,
                        debtAmount = clientBalancesInCurr[client.id] ?: 0.0,
                        currency = curr,
                        invoicesCount = clientInvoicesGroup[client.id]?.size ?: 0
                    )
                }
                .sortedByDescending { it.debtAmount }
                .take(5)

            // Recent Activities in this currency (filtered by period or recent)
            val activities = mutableListOf<ActivityItem>()
            periodInvoices.forEach { inv ->
                activities.add(
                    ActivityItem(
                        id = inv.id,
                        type = ActivityType.INVOICE,
                        title = if (inv.isQuickInvoice) "فاتورة سريعة" else "فاتورة مفصلة",
                        subtitle = "رقم: ${inv.invoiceNumber} | العميل: ${inv.clientName}",
                        amount = inv.totalAmount,
                        date = inv.date,
                        isDraft = inv.isDraft,
                        referenceId = inv.id
                    )
                )
            }
            periodPayments.forEach { pay ->
                val cName = clientMap[pay.clientId]?.name ?: "عميل غير معروف"
                activities.add(
                    ActivityItem(
                        id = pay.id,
                        type = ActivityType.PAYMENT,
                        title = "دفعة مستلمة (${curr.symbol})",
                        subtitle = "العميل: $cName | طريقة الدفع: ${pay.paymentMethod}",
                        amount = pay.amount,
                        date = pay.date,
                        isDraft = false,
                        referenceId = pay.id
                    )
                )
            }
            val sortedActivities = activities.sortedByDescending { it.date }.take(15)

            summariesPerCurrency[currCode] = CurrencyFinancialSummary(
                currency = curr,
                clientCount = clientCount,
                debtorsCount = debtorsCount,
                creditorsCount = creditorsCount,
                balancedCount = balancedCount,
                netBalance = netBalance,
                totalSales = totalSales,
                totalReceipts = totalReceipts,
                totalDebts = totalDebts,
                collectionRate = collectionRate,
                invoicesCount = invoicesCount,
                paidInvoicesCount = paidInvoicesCount,
                partialInvoicesCount = partialInvoicesCount,
                unpaidInvoicesCount = unpaidInvoicesCount,
                paymentsCount = paymentsCount,
                installmentsDueTodayCount = installmentsDueToday.size,
                installmentsDueTodayAmount = round2(installmentsDueToday.sumOf { (it.amount - it.paidAmount).coerceAtLeast(0.0) }),
                installmentsOverdueCount = installmentsOverdue.size,
                installmentsOverdueAmount = round2(installmentsOverdue.sumOf { (it.amount - it.paidAmount).coerceAtLeast(0.0) }),
                totalInstallmentsCount = allInstallmentsForCurr.size,
                totalInstallmentsRemainingAmount = totalRemainingInstallments,
                topDebtors = topDebtors,
                recentActivities = sortedActivities
            )
        }

        val currentSummary = if (selectedCurrencyCode != "ALL" && selectedCurrencyCode.isNotBlank()) {
            summariesPerCurrency[selectedCurrencyCode]
                ?: summariesPerCurrency[CurrencyMeta.from(selectedCurrencyCode).code]
        } else null

        return FinancialDashboardUiState(
            selectedCurrencyCode = selectedCurrencyCode,
            selectedPeriod = selectedPeriod,
            availableCurrencies = availableCurrencies,
            summariesPerCurrency = summariesPerCurrency,
            currentSummary = currentSummary,
            totalRegisteredClients = clientsList.size,
            isLoading = false
        )
    }
}
