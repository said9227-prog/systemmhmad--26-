package com.example.data.model

import java.util.Calendar

enum class ItemMovementPeriod(val labelAr: String) {
    TODAY("اليوم"),
    THIS_WEEK("الأسبوع"),
    THIS_MONTH("الشهر"),
    THIS_YEAR("السنة"),
    ALL_TIME("الكل");

    fun getTimeRange(): Pair<Long, Long> {
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance()
        return when (this) {
            TODAY -> {
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val start = cal.timeInMillis
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                cal.set(Calendar.SECOND, 59)
                cal.set(Calendar.MILLISECOND, 999)
                Pair(start, cal.timeInMillis)
            }
            THIS_WEEK -> {
                cal.firstDayOfWeek = Calendar.SATURDAY
                cal.set(Calendar.DAY_OF_WEEK, Calendar.SATURDAY)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val start = cal.timeInMillis
                Pair(start, now + 86400000L)
            }
            THIS_MONTH -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val start = cal.timeInMillis
                Pair(start, now + 86400000L)
            }
            THIS_YEAR -> {
                cal.set(Calendar.DAY_OF_YEAR, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val start = cal.timeInMillis
                Pair(start, now + 86400000L)
            }
            ALL_TIME -> {
                Pair(0L, Long.MAX_VALUE)
            }
        }
    }
}

enum class ItemMovementSort(val labelAr: String) {
    QUANTITY("الأكثر كمية"),
    AMOUNT("الأعلى قيمة"),
    CUSTOMERS_COUNT("الأكثر عملاء")
}

data class ItemMovementInvoiceRecord(
    val invoiceId: Int,
    val invoiceNumber: String,
    val date: Long,
    val quantity: Int,
    val unitPrice: Double,
    val totalPrice: Double,
    val currency: String
)

data class ItemMovementReturnRecord(
    val returnId: Int,
    val returnNumber: String,
    val date: Long,
    val quantity: Int,
    val unitPrice: Double,
    val totalPrice: Double,
    val reason: String
)

data class CustomerMovementDetail(
    val clientId: Int?,
    val clientName: String,
    val phone: String = "",
    val grossSoldQuantity: Int,
    val returnedQuantity: Int,
    val netQuantity: Int,
    val grossSoldAmount: Double,
    val returnedAmount: Double,
    val netAmount: Double,
    val percentageOfTotal: Double,
    val isTopCustomer: Boolean = false,
    val invoices: List<ItemMovementInvoiceRecord> = emptyList(),
    val returns: List<ItemMovementReturnRecord> = emptyList()
)

data class ItemMovementSummary(
    val itemId: Int?,
    val itemName: String,
    val category: String = "",
    val unit: String = "قطعة",
    val currentStock: Int = 0,
    val grossSoldQuantity: Int = 0,
    val returnedQuantity: Int = 0,
    val netQuantity: Int = 0,
    val grossSoldAmount: Double = 0.0,
    val returnedAmount: Double = 0.0,
    val netAmount: Double = 0.0,
    val defaultCurrency: String = "الريال اليمني",
    val invoicesCount: Int = 0,
    val returnsCount: Int = 0,
    val customersCount: Int = 0,
    val topCustomer: CustomerMovementDetail? = null,
    val customers: List<CustomerMovementDetail> = emptyList()
)

data class ItemMovementGlobalStats(
    val totalItemsMoved: Int = 0,
    val totalUnitsSoldNet: Int = 0,
    val totalUnitsReturned: Int = 0,
    val totalNetRevenue: Double = 0.0,
    val totalUniqueClients: Int = 0,
    val mostMovedItemName: String? = null,
    val mostMovedItemQty: Int = 0,
    val topCustomerOverallName: String? = null,
    val currency: String = "الريال اليمني"
)
