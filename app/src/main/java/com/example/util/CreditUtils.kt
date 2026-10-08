package com.example.util

import androidx.compose.ui.graphics.Color
import com.example.data.model.Client

enum class CreditStatusLevel {
    NO_LIMIT,          // غير محدد
    NORMAL,            // 🟢 طبيعي (< 80%)
    EARLY_WARNING,     // 🟡 تنبيه مبكر (80% - 89.9%)
    CRITICAL_WARNING,  // 🟠 تحذير قوي (90% - 99.9%)
    LIMIT_REACHED      // 🔴 تجاوز الحد الائتماني (>= 100%)
}

data class CreditStatusInfo(
    val level: CreditStatusLevel,
    val limit: Double,
    val currentBalance: Double,
    val availableCredit: Double,
    val overageAmount: Double,
    val usagePercentage: Double,
    val title: String,
    val description: String,
    val statusColor: Color,
    val containerColor: Color,
    val isBlocked: Boolean
)

data class InvoiceCreditCheckResult(
    val isExceeded: Boolean,
    val limit: Double,
    val currentBalance: Double,
    val invoiceAmount: Double,
    val prospectiveBalance: Double,
    val availableCreditBefore: Double,
    val overageAmount: Double,
    val prospectiveUsagePercentage: Double,
    val prospectiveLevel: CreditStatusLevel,
    val clientNotificationMessage: String
)

object CreditUtils {

    /**
     * Calculates current credit status for a client.
     */
    fun getCreditStatusInfo(client: Client): CreditStatusInfo {
        val limit = client.creditLimit
        val balance = client.balance

        if (limit <= 0.0) {
            return CreditStatusInfo(
                level = CreditStatusLevel.NO_LIMIT,
                limit = 0.0,
                currentBalance = balance,
                availableCredit = Double.MAX_VALUE,
                overageAmount = 0.0,
                usagePercentage = 0.0,
                title = "بدون سقف ائتماني",
                description = "العميل غير مقيد بحد ائتماني محدد",
                statusColor = Color(0xFF6B7280),
                containerColor = Color(0xFFF3F4F6),
                isBlocked = false
            )
        }

        val usageRatio = if (balance > 0) (balance / limit) else 0.0
        val percentage = (usageRatio * 100).coerceAtLeast(0.0)
        val available = (limit - balance).coerceAtLeast(0.0)
        val overage = (balance - limit).coerceAtLeast(0.0)

        val warningThreshold = if (client.creditWarningThreshold > 0) client.creditWarningThreshold else 80.0

        return when {
            balance >= limit -> {
                CreditStatusInfo(
                    level = CreditStatusLevel.LIMIT_REACHED,
                    limit = limit,
                    currentBalance = balance,
                    availableCredit = 0.0,
                    overageAmount = overage,
                    usagePercentage = percentage,
                    title = if (overage > 0) "تجاوز السقف الائتماني" else "بلوغ الحد الائتماني الأقصى",
                    description = "ممنوع الشراء الآجل حتى يتم سداد المديونية أو منح استثناء إداري",
                    statusColor = Color(0xFFDC2626),
                    containerColor = Color(0xFFFEE2E2),
                    isBlocked = true
                )
            }
            percentage >= 90.0 -> {
                CreditStatusInfo(
                    level = CreditStatusLevel.CRITICAL_WARNING,
                    limit = limit,
                    currentBalance = balance,
                    availableCredit = available,
                    overageAmount = 0.0,
                    usagePercentage = percentage,
                    title = "تحذير ائتماني حرج (90%+)",
                    description = "اقترب العميل جداً من استنفاد الحد الائتماني المتاح",
                    statusColor = Color(0xFFEA580C),
                    containerColor = Color(0xFFFFEDD5),
                    isBlocked = false
                )
            }
            percentage >= warningThreshold -> {
                CreditStatusInfo(
                    level = CreditStatusLevel.EARLY_WARNING,
                    limit = limit,
                    currentBalance = balance,
                    availableCredit = available,
                    overageAmount = 0.0,
                    usagePercentage = percentage,
                    title = "تنبيه مبكر (${warningThreshold.toInt()}%)",
                    description = "تم استهلاك أكثر من ${warningThreshold.toInt()}% من السقف الائتماني",
                    statusColor = Color(0xFFD97706),
                    containerColor = Color(0xFFFEF3C7),
                    isBlocked = false
                )
            }
            else -> {
                CreditStatusInfo(
                    level = CreditStatusLevel.NORMAL,
                    limit = limit,
                    currentBalance = balance,
                    availableCredit = available,
                    overageAmount = 0.0,
                    usagePercentage = percentage,
                    title = "ائتمان طبيعي وآمن",
                    description = "حالة الحساب طبيعية ورصيد الائتمان متاح ومستقر",
                    statusColor = Color(0xFF059669),
                    containerColor = Color(0xFFD1FAE5),
                    isBlocked = false
                )
            }
        }
    }

    /**
     * Checks whether an invoice will exceed the client's credit limit.
     */
    fun checkInvoiceCredit(client: Client, invoiceAmount: Double, storeName: String = "المتجر"): InvoiceCreditCheckResult {
        val limit = client.creditLimit
        val currentBalance = client.balance
        val prospectiveBalance = currentBalance + invoiceAmount
        val availableBefore = (limit - currentBalance).coerceAtLeast(0.0)

        if (limit <= 0.0) {
            return InvoiceCreditCheckResult(
                isExceeded = false,
                limit = 0.0,
                currentBalance = currentBalance,
                invoiceAmount = invoiceAmount,
                prospectiveBalance = prospectiveBalance,
                availableCreditBefore = Double.MAX_VALUE,
                overageAmount = 0.0,
                prospectiveUsagePercentage = 0.0,
                prospectiveLevel = CreditStatusLevel.NO_LIMIT,
                clientNotificationMessage = ""
            )
        }

        val prospectiveUsage = if (prospectiveBalance > 0) (prospectiveBalance / limit) * 100.0 else 0.0
        val isExceeded = prospectiveBalance > limit
        val overage = (prospectiveBalance - limit).coerceAtLeast(0.0)

        val prospectiveLevel = when {
            prospectiveBalance >= limit -> CreditStatusLevel.LIMIT_REACHED
            prospectiveUsage >= 90.0 -> CreditStatusLevel.CRITICAL_WARNING
            prospectiveUsage >= (if (client.creditWarningThreshold > 0) client.creditWarningThreshold else 80.0) -> CreditStatusLevel.EARLY_WARNING
            else -> CreditStatusLevel.NORMAL
        }

        val notificationMsg = "تنبيه: عميلنا العزيز ${client.name}، نود إحاطتكم بأن حسابكم لدى $storeName قد بلغ الحد الائتماني المسموح به (${FormatUtils.formatAmount(limit)}). مديونيتكم الحالية: ${FormatUtils.formatAmount(prospectiveBalance)}. يرجى سرعة السداد حتى يتم منحكم مشتريات جديدة."

        return InvoiceCreditCheckResult(
            isExceeded = isExceeded,
            limit = limit,
            currentBalance = currentBalance,
            invoiceAmount = invoiceAmount,
            prospectiveBalance = prospectiveBalance,
            availableCreditBefore = availableBefore,
            overageAmount = overage,
            prospectiveUsagePercentage = prospectiveUsage,
            prospectiveLevel = prospectiveLevel,
            clientNotificationMessage = notificationMsg
        )
    }

    /**
     * Format a direct WhatsApp/SMS message for Credit Limit warning/alert.
     */
    fun formatCreditAlertMessage(
        client: Client,
        storeName: String,
        currency: String
    ): String {
        val info = getCreditStatusInfo(client)
        val sb = java.lang.StringBuilder()
        sb.append("📋 *إشعار الحد الائتماني والحساب - $storeName*\n\n")
        sb.append("عميلنا العزيز: *${client.name}*\n")
        
        when (info.level) {
            CreditStatusLevel.LIMIT_REACHED -> {
                sb.append("⛔ *تنبيه هام:* حسابكم بلغ الحد الائتماني المسموح به.\n\n")
            }
            CreditStatusLevel.CRITICAL_WARNING -> {
                sb.append("🟠 *تحذير ائتماني:* لقد اقتربتم من بلوغ الحد الائتماني المسموح به.\n\n")
            }
            CreditStatusLevel.EARLY_WARNING -> {
                sb.append("🟡 *تذكير ائتماني:* استهلكتم أكثر من ${client.creditWarningThreshold.toInt()}% من السقف الائتماني.\n\n")
            }
            else -> {
                sb.append("🟢 *بيان الائتمان المتاح لحسابكم:*\n\n")
            }
        }

        sb.append("▫️ *الحد الائتماني المعتمد:* ${FormatUtils.formatAmount(info.limit)} $currency\n")
        sb.append("▫️ *المديونية الحالية:* ${FormatUtils.formatAmount(info.currentBalance)} $currency\n")
        sb.append("▫️ *الرصيد المتبقي المتاح للشراء:* ${FormatUtils.formatAmount(info.availableCredit)} $currency\n")
        sb.append("▫️ *نسبة الاستهلاك:* ${String.format(java.util.Locale.US, "%.1f", info.usagePercentage)}%\n")

        if (info.overageAmount > 0) {
            sb.append("⚠️ *مبلغ التجاوز الحالي:* ${FormatUtils.formatAmount(info.overageAmount)} $currency\n")
        }

        sb.append("\nيرجى التكرم بسرعة السداد لمواصلة خدمة الشراء الآجل دون انقطاع.\n")
        sb.append("شاكرين ومقدرين حسن تعاونكم معنا.\n")
        sb.append("🏢 *$storeName*")

        return sb.toString()
    }
}
