package com.example.data.model

import com.example.ui.viewmodel.ActivityItem

/**
 * Filter time period for financial analytics.
 */
enum class DashboardPeriod(val labelAr: String) {
    ALL("الكل"),
    TODAY("اليوم"),
    THIS_WEEK("هذا الأسبوع"),
    THIS_MONTH("هذا الشهر"),
    THIS_YEAR("هذه السنة")
}

/**
 * Metadata and standardization for currencies used across the app.
 */
data class CurrencyMeta(
    val code: String,
    val nameAr: String,
    val symbol: String,
    val flag: String
) {
    val displayName: String
        get() = "$flag $nameAr ($code)"

    val chipLabel: String
        get() = "$flag $nameAr"

    companion object {
        val YER = CurrencyMeta(code = "YER", nameAr = "الريال اليمني", symbol = "﷼.ي", flag = "🇾🇪")
        val SAR = CurrencyMeta(code = "SAR", nameAr = "الريال السعودي", symbol = "﷼.س", flag = "🇸🇦")
        val USD = CurrencyMeta(code = "USD", nameAr = "الدولار الأمريكي", symbol = "$", flag = "🇺🇸")

        val DEFAULT_LIST = listOf(YER, SAR, USD)

        /**
         * Normalizes any currency string (e.g. from invoices, payments, or settings)
         * to a standardized CurrencyMeta.
         */
        fun from(input: String?): CurrencyMeta {
            val trimmed = input?.trim().orEmpty()
            return when {
                trimmed.equals("YER", ignoreCase = true) || trimmed.contains("يمني") || trimmed.contains("اليمني") -> YER
                trimmed.equals("SAR", ignoreCase = true) || trimmed.contains("سعودي") || trimmed.contains("السعودي") -> SAR
                trimmed.equals("USD", ignoreCase = true) || trimmed.contains("دولار") || trimmed.contains("امريكي") || trimmed.contains("أمريكي") || trimmed == "$" -> USD
                trimmed.isNotBlank() -> CurrencyMeta(
                    code = trimmed.take(4).uppercase(),
                    nameAr = trimmed,
                    symbol = trimmed,
                    flag = "💱"
                )
                else -> YER
            }
        }
    }
}

/**
 * Information about a debtor client under a specific currency.
 */
data class CurrencyDebtorInfo(
    val client: Client,
    val debtAmount: Double,
    val currency: CurrencyMeta,
    val invoicesCount: Int = 0
)

/**
 * Comprehensive financial summary scoped to a single currency.
 * Absolutely NO mixing of currencies occurs here.
 */
data class CurrencyFinancialSummary(
    val currency: CurrencyMeta,
    val clientCount: Int = 0,
    val debtorsCount: Int = 0,
    val creditorsCount: Int = 0,
    val balancedCount: Int = 0,
    val netBalance: Double = 0.0, // Total net receivables (positive = clients owe us, negative = credit)
    val totalSales: Double = 0.0, // Total sales invoiced in selected period
    val totalReceipts: Double = 0.0, // Total cash/transfer receipts received in selected period
    val totalDebts: Double = 0.0, // Outstanding uncollected debts for this currency
    val collectionRate: Double = 0.0, // Collection efficiency percentage (0.0 to 100.0)
    val invoicesCount: Int = 0,
    val paidInvoicesCount: Int = 0,
    val partialInvoicesCount: Int = 0,
    val unpaidInvoicesCount: Int = 0,
    val paymentsCount: Int = 0,
    val installmentsDueTodayCount: Int = 0,
    val installmentsDueTodayAmount: Double = 0.0,
    val installmentsOverdueCount: Int = 0,
    val installmentsOverdueAmount: Double = 0.0,
    val totalInstallmentsCount: Int = 0,
    val totalInstallmentsRemainingAmount: Double = 0.0,
    val topDebtors: List<CurrencyDebtorInfo> = emptyList(),
    val recentActivities: List<ActivityItem> = emptyList()
)

/**
 * Full state for the Financial Dashboard screen.
 */
data class FinancialDashboardUiState(
    val selectedCurrencyCode: String = "YER", // "YER" by default for Yemeni Rial, or "SAR", "USD", "ALL", etc.
    val selectedPeriod: DashboardPeriod = DashboardPeriod.THIS_MONTH,
    val availableCurrencies: List<CurrencyMeta> = emptyList(),
    val summariesPerCurrency: Map<String, CurrencyFinancialSummary> = emptyMap(),
    val currentSummary: CurrencyFinancialSummary? = null,
    val totalRegisteredClients: Int = 0,
    val isLoading: Boolean = false
) {
    val isAllCurrenciesMode: Boolean
        get() = selectedCurrencyCode == "ALL" || selectedCurrencyCode.isBlank()
}
