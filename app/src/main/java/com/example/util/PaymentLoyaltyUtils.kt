package com.example.util

import androidx.compose.ui.graphics.Color
import com.example.data.model.Client
import com.example.data.model.Installment
import com.example.data.model.Invoice
import com.example.data.model.Payment
import com.example.data.model.StoreSettings
import java.util.concurrent.TimeUnit
import kotlin.math.abs

enum class LoyaltyBadgeType(
    val title: String,
    val iconEmoji: String,
    val colorHex: Long,
    val bgHex: Long,
    val description: String
) {
    LOYAL("عميل وفي", "⭐", 0xFFD97706, 0xFFFEF3C7, "عميل وفي ذو سجل شراء وسداد ممتاز ومستمر"),
    FAST_PAYER("سريع السداد", "⚡", 0xFF2563EB, 0xFFDBEAFE, "يسدد مستحقاته فوراً وبسرعة قياسية"),
    REGULAR_PAYER("منتظم بالسداد", "✓", 0xFF059669, 0xFFD1FAE5, "يسدد دفعاته بانتظام والتزام مستمر"),
    FIXED_PATTERN("سداد ثابت", "🔄", 0xFF7C3AED, 0xFFEDE9FE, "يمتلك نمط سداد متكرر بمواعيد أو مبالغ ثابتة"),
    OVERDUE("متأخر بالسداد", "⚠️", 0xFFDC2626, 0xFFFEE2E2, "تجاوزت مديونيته مهلة السداد المحددة"),
    NORMAL("عميل عادي", "👤", 0xFF4B5563, 0xFFF3F4F6, "عميل بحالة حساب طبيعية")
}

data class ClientLoyaltyProfile(
    val client: Client,
    val primaryBadge: LoyaltyBadgeType,
    val allBadges: List<LoyaltyBadgeType>,
    val isOverdue: Boolean,
    val overdueDays: Int,
    val isFastPayer: Boolean,
    val isLoyal: Boolean,
    val isRegularPayer: Boolean,
    val hasFixedPattern: Boolean,
    val totalInvoices: Int,
    val paidInvoices: Int,
    val totalPayments: Int,
    val totalPaidAmount: Double,
    val totalInvoicedAmount: Double,
    val paymentCommitmentRate: Double,
    val averageSettlementDays: Double,
    val fixedRecurringAmount: Double?,
    val loyaltyScore: Int, // 0 to 100
    val overdueNoticeMessage: String,
    val loyaltyAppreciationMessage: String
) {
    val primaryColor: Color get() = Color(primaryBadge.colorHex)
    val primaryContainerColor: Color get() = Color(primaryBadge.bgHex)
}

object PaymentLoyaltyUtils {

    /**
     * Comprehensive evaluation of a client's historical payment, invoicing, and installment behavior.
     */
    fun analyzeClientBehavior(
        client: Client,
        allInvoices: List<Invoice>,
        allPayments: List<Payment>,
        allInstallments: List<Installment>,
        settings: StoreSettings
    ): ClientLoyaltyProfile {
        val clientInvoices = allInvoices.filter { it.clientId == client.id }
        val clientPayments = allPayments.filter { it.clientId == client.id }
        val clientInstallments = allInstallments.filter { it.clientId == client.id }
        return analyzeClientBehaviorPreFiltered(client, clientInvoices, clientPayments, clientInstallments, settings)
    }

    /**
     * Highly optimized pre-grouped/pre-filtered evaluation that prevents O(N*M) full list scans.
     */
    fun analyzeClientBehaviorPreFiltered(
        client: Client,
        clientInvoices: List<Invoice>,
        clientPayments: List<Payment>,
        clientInstallments: List<Installment>,
        settings: StoreSettings
    ): ClientLoyaltyProfile {
        val sortedInvoices = clientInvoices.filter { !it.isDraft }.sortedBy { it.date }
        val sortedPayments = clientPayments.sortedBy { it.date }

        val totalInvoices = sortedInvoices.size
        val totalPayments = sortedPayments.size
        val totalInvoicedAmount = sortedInvoices.sumOf { it.totalAmount }
        val totalPaidAmount = sortedPayments.sumOf { it.amount }

        val now = System.currentTimeMillis()

        // 1. Check for Overdue Status
        var isOverdue = false
        var overdueDays = 0

        // 1.a Check overdue installments
        val overdueInstallments = clientInstallments.filter { !it.isPaid && it.dueDate < now }
        if (overdueInstallments.isNotEmpty()) {
            val oldestOverdueDueDate = overdueInstallments.minOf { it.dueDate }
            val diffMs = (now - oldestOverdueDueDate).coerceAtLeast(0L)
            overdueDays = TimeUnit.MILLISECONDS.toDays(diffMs).toInt()
            isOverdue = overdueDays > 0
        }

        // 1.b Check general balance & oldest invoice timestamp if balance is positive
        if (client.balance > 0) {
            // Find when the outstanding debt started accumulating
            val lastPaymentDate = clientPayments.maxOfOrNull { it.date }
            val thresholdDays = settings.overdueDaysThreshold.coerceAtLeast(1)
            
            // Reference time: if client has payments, check time since last payment vs invoices; otherwise check oldest unpaid invoice
            val referenceDate = if (lastPaymentDate != null && clientInvoices.isNotEmpty()) {
                val invoicesAfterLastPayment = clientInvoices.filter { it.date > lastPaymentDate }
                if (invoicesAfterLastPayment.isNotEmpty()) invoicesAfterLastPayment.minOf { it.date } else lastPaymentDate
            } else {
                clientInvoices.minOfOrNull { it.date } ?: (now - TimeUnit.DAYS.toMillis(thresholdDays.toLong()))
            }

            val elapsedDays = TimeUnit.MILLISECONDS.toDays((now - referenceDate).coerceAtLeast(0L)).toInt()
            if (elapsedDays >= thresholdDays) {
                isOverdue = true
                overdueDays = maxOf(overdueDays, elapsedDays)
            }
        }

        // 2. Calculate Average Settlement Speed
        var averageSettlementDays = 0.0
        if (clientInvoices.isNotEmpty() && clientPayments.isNotEmpty()) {
            var totalDurationDays = 0.0
            var matchedCount = 0
            
            // Measure elapsed days between each invoice and the nearest subsequent payment
            for (inv in clientInvoices) {
                val nextPayments = clientPayments.filter { it.date >= inv.date }
                if (nextPayments.isNotEmpty()) {
                    val firstSubsequentPay = nextPayments.minByOrNull { it.date }!!
                    val days = TimeUnit.MILLISECONDS.toDays(firstSubsequentPay.date - inv.date).toDouble()
                    totalDurationDays += days
                    matchedCount++
                }
            }
            if (matchedCount > 0) {
                averageSettlementDays = totalDurationDays / matchedCount
            }
        }

        // 3. Fast Payer Detection (⚡)
        val fastDaysThreshold = settings.fastPayerDaysThreshold.coerceAtLeast(1)
        val isFastPayer = (totalPayments >= 2 || (totalInvoices >= 1 && client.balance <= 0)) &&
                !isOverdue &&
                (averageSettlementDays <= fastDaysThreshold || (totalPaidAmount >= totalInvoicedAmount && client.balance <= 0))

        // 4. Fixed / Recurring Pattern Detection (🔄)
        var hasFixedPattern = false
        var fixedRecurringAmount: Double? = null
        if (clientPayments.size >= 3) {
            // Group payments by rounded amounts (within 5% threshold)
            val paymentAmounts = clientPayments.map { it.amount }
            val frequencyMap = mutableMapOf<Double, Int>()
            for (amt in paymentAmounts) {
                // Check if matches an existing cluster
                val clusterKey = frequencyMap.keys.find { abs(it - amt) <= (it * 0.05).coerceAtLeast(1.0) }
                if (clusterKey != null) {
                    frequencyMap[clusterKey] = (frequencyMap[clusterKey] ?: 0) + 1
                } else {
                    frequencyMap[amt] = 1
                }
            }
            val maxRecurring = frequencyMap.maxByOrNull { it.value }
            if (maxRecurring != null && maxRecurring.value >= 3) {
                hasFixedPattern = true
                fixedRecurringAmount = maxRecurring.key
            }
        }

        // 5. Regular Payer Detection (✓)
        val paymentCommitmentRate = if (totalInvoicedAmount > 0) {
            ((totalPaidAmount / totalInvoicedAmount) * 100.0).coerceIn(0.0, 100.0)
        } else if (client.balance <= 0) 100.0 else 50.0

        val isRegularPayer = (totalPayments >= 2 && !isOverdue && paymentCommitmentRate >= 60.0)

        // 6. Loyal Client Detection (⭐)
        val minInvoices = settings.loyaltyMinInvoicesCount.coerceAtLeast(1)
        val isLoyal = (totalInvoices >= minInvoices || totalPayments >= minInvoices) &&
                !isOverdue &&
                (paymentCommitmentRate >= 70.0 || client.balance <= 0) &&
                (isFastPayer || isRegularPayer || hasFixedPattern || client.classification == "VIP" || client.classification == "مميز")

        // 7. Calculate Badges & Primary Badge Hierarchy
        val badges = mutableListOf<LoyaltyBadgeType>()
        if (isOverdue) {
            badges.add(LoyaltyBadgeType.OVERDUE)
        }
        if (isLoyal) {
            badges.add(LoyaltyBadgeType.LOYAL)
        }
        if (isFastPayer) {
            badges.add(LoyaltyBadgeType.FAST_PAYER)
        }
        if (hasFixedPattern) {
            badges.add(LoyaltyBadgeType.FIXED_PATTERN)
        }
        if (isRegularPayer && !badges.contains(LoyaltyBadgeType.LOYAL) && !badges.contains(LoyaltyBadgeType.FAST_PAYER)) {
            badges.add(LoyaltyBadgeType.REGULAR_PAYER)
        }
        if (badges.isEmpty()) {
            badges.add(LoyaltyBadgeType.NORMAL)
        }

        val primaryBadge = when {
            isOverdue -> LoyaltyBadgeType.OVERDUE
            isLoyal -> LoyaltyBadgeType.LOYAL
            isFastPayer -> LoyaltyBadgeType.FAST_PAYER
            hasFixedPattern -> LoyaltyBadgeType.FIXED_PATTERN
            isRegularPayer -> LoyaltyBadgeType.REGULAR_PAYER
            else -> LoyaltyBadgeType.NORMAL
        }

        // 8. Loyalty Score (0 - 100)
        var score = 50
        if (isLoyal) score += 30
        if (isFastPayer) score += 15
        if (isRegularPayer) score += 10
        if (hasFixedPattern) score += 10
        if (client.balance <= 0) score += 10
        if (isOverdue) score -= (20 + (overdueDays.coerceAtMost(30)))
        val loyaltyScore = score.coerceIn(0, 100)

        // 9. Format Notification Messages
        val formattedDueAmount = FormatUtils.formatAmount(if (client.balance > 0) client.balance else 0.0)
        val overdueNotice = formatMessageTemplate(
            template = settings.overdueNoticeTemplate,
            clientName = client.name,
            dueAmount = formattedDueAmount,
            currency = settings.currency,
            storeName = settings.storeName
        )

        val loyaltyAppreciation = formatMessageTemplate(
            template = settings.loyaltyAppreciationTemplate,
            clientName = client.name,
            dueAmount = formattedDueAmount,
            currency = settings.currency,
            storeName = settings.storeName
        )

        return ClientLoyaltyProfile(
            client = client,
            primaryBadge = primaryBadge,
            allBadges = badges,
            isOverdue = isOverdue,
            overdueDays = overdueDays,
            isFastPayer = isFastPayer,
            isLoyal = isLoyal,
            isRegularPayer = isRegularPayer,
            hasFixedPattern = hasFixedPattern,
            totalInvoices = totalInvoices,
            paidInvoices = clientInvoices.count { it.remainingAmount <= 0.01 },
            totalPayments = totalPayments,
            totalPaidAmount = totalPaidAmount,
            totalInvoicedAmount = totalInvoicedAmount,
            paymentCommitmentRate = paymentCommitmentRate,
            averageSettlementDays = averageSettlementDays,
            fixedRecurringAmount = fixedRecurringAmount,
            loyaltyScore = loyaltyScore,
            overdueNoticeMessage = overdueNotice,
            loyaltyAppreciationMessage = loyaltyAppreciation
        )
    }

    /**
     * Interpolates dynamic variables in template strings.
     */
    fun formatMessageTemplate(
        template: String,
        clientName: String,
        dueAmount: String,
        currency: String,
        storeName: String
    ): String {
        return template
            .replace("{اسم_العميل}", clientName)
            .replace("{اسم العميل}", clientName)
            .replace("{المبلغ_المستحق}", dueAmount)
            .replace("{المبلغ المستحق}", dueAmount)
            .replace("{العملة}", currency)
            .replace("{اسم_المتجر}", storeName)
            .replace("{اسم المتجر}", storeName)
    }
}
