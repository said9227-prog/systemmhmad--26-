package com.example.domain.report

data class ReportData(
    val selectedCurrency: String,
    val dateRange: DateRange,
    val currencyReports: List<CurrencyReport>
)

data class CurrencyReport(
    val currency: String,
    val salesReport: SalesReport,
    val purchaseReport: PurchaseReport,
    val returnsReport: ReturnsReport,
    val customerReport: CustomerReport,
    val supplierReport: SupplierReport,
    val inventoryReport: InventoryReport,
    val collectionReport: CollectionReport,
    val debtAgingReport: DebtAgingReport,
    val financialAnalysis: FinancialAnalysis
)

data class SalesReport(
    val totalSales: Double,
    val totalReturns: Double,
    val netSales: Double,
    val totalPayments: Double,
    val totalRemaining: Double,
    val invoiceCount: Int,
    val averageInvoiceValue: Double,
    val fullyPaidCount: Int,
    val partiallyPaidCount: Int,
    val unpaidCount: Int,
    val draftCount: Int,
    val activeCustomersCount: Int,
    val topCustomerName: String,
    val topCustomerAmount: Double,
    val topItemName: String,
    val topItemAmount: Double
)

data class PurchaseReport(
    val isSupported: Boolean = false,
    val totalPurchases: Double = 0.0,
    val netPurchases: Double = 0.0,
    val totalPayments: Double = 0.0,
    val totalRemaining: Double = 0.0,
    val invoiceCount: Int = 0,
    val supplierCount: Int = 0
)

data class ReturnsReport(
    val isSupported: Boolean = false,
    val totalSalesReturns: Double = 0.0,
    val totalPurchaseReturns: Double = 0.0
)

data class CustomerReport(
    val activeCustomersCount: Int,
    val totalReceivables: Double, // إجمالي المستحقات (الفواتير) في الفترة
    val totalReceipts: Double, // إجمالي المقبوضات (المدفوعات) في الفترة
    val totalRemaining: Double, // إجمالي المتبقي (من فواتير الفترة)
    val totalDueInstallments: Double,
    val totalOverdueInstallments: Double,
    val debtorsCount: Int, // Currently in debt
    val creditorsCount: Int, // Currently in credit
    val neutralCount: Int // Currently 0 balance
)

data class SupplierReport(
    val isSupported: Boolean = false,
    val supplierCount: Int = 0
)

data class InventoryReport(
    val totalItemsCount: Int,
    val availableItemsCount: Int,
    val lowStockItemsCount: Int,
    val outOfStockItemsCount: Int,
    val totalQuantity: Int,
    val inventoryValue: Double,
    val soldQuantityPeriod: Int
)

data class CollectionReport(
    val totalDue: Double,
    val totalCollected: Double,
    val totalRemaining: Double,
    val collectionRatio: Double,
    val dueInstallments: Double,
    val overdueInstallments: Double
)

data class DebtAgingReport(
    val currentDue: Double,
    val overdue1_7: Double,
    val overdue8_30: Double,
    val overdue31_60: Double,
    val overdue60Plus: Double,
    val totalInvoices: Int,
    val totalCustomers: Int
)

data class FinancialAnalysis(
    val totalSales: Double,
    val netSales: Double,
    val totalReceipts: Double,
    val accountsReceivable: Double,
    val cashFlow: Double
)
