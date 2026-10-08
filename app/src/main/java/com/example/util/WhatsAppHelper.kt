package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.example.data.model.ExchangeRate
import com.example.data.model.Invoice
import com.example.data.model.InvoiceItem
import kotlin.math.abs

object WhatsAppHelper {

    /**
     * Builds a formatted Arabic WhatsApp message with Markdown emphasis and emojis,
     * supporting multi-currency (Saudi SAR, Yemeni YER, and US Dollar USD) for items and totals.
     */
    fun formatWhatsAppInvoiceMessage(
        invoice: Invoice,
        items: List<InvoiceItem>,
        storeName: String,
        storePhone: String = "",
        storeAddress: String = "",
        currency: String = "الريال اليمني",
        clientBalance: Double? = null,
        clientCurrency: String? = null,
        showVatAndSubtotal: Boolean = false,
        rates: List<ExchangeRate> = emptyList(),
        targetCurrency: String? = null
    ): String {
        val invoiceCurr = MonetaryUtils.normalizeCurrency(currency.ifBlank { invoice.currency })
        val isSpecificCurrency = !targetCurrency.isNullOrBlank() && targetCurrency != "الكل"
        
        val chosenCurrency = when {
            targetCurrency == "SAR" || targetCurrency?.contains("سعودي") == true -> MonetaryUtils.CURRENCY_SAR
            targetCurrency == "USD" || targetCurrency?.contains("دولار") == true -> MonetaryUtils.CURRENCY_USD
            targetCurrency == "YER" || targetCurrency?.contains("يمني") == true -> MonetaryUtils.CURRENCY_YER
            isSpecificCurrency -> MonetaryUtils.normalizeCurrency(targetCurrency!!)
            else -> invoiceCurr
        }

        val (currSymbol, currFullName) = when (chosenCurrency) {
            MonetaryUtils.CURRENCY_SAR -> "ر.س" to "الريال السعودي (SAR)"
            MonetaryUtils.CURRENCY_USD -> "$" to "الدولار الأمريكي (USD)"
            else -> "ر.ي" to "الريال اليمني (YER)"
        }

        val sarToYer = MonetaryUtils.convert(1.0, MonetaryUtils.CURRENCY_SAR, MonetaryUtils.CURRENCY_YER, rates).convertedAmount
        val usdToYer = MonetaryUtils.convert(1.0, MonetaryUtils.CURRENCY_USD, MonetaryUtils.CURRENCY_YER, rates).convertedAmount

        val sb = StringBuilder()
        sb.append("🏥 *").append(storeName.ifBlank { "الحكيمي للأدوية والمستلزمات الطبية" }).append("*\n")
        if (storePhone.isNotBlank()) {
            sb.append("📞 هاتف: ").append(storePhone).append("\n")
        }
        if (storeAddress.isNotBlank()) {
            sb.append("📍 العنوان: ").append(storeAddress).append("\n")
        }
        sb.append("━━━━━━━━━━━━━━━━━━━━\n")
        sb.append("🧾 *تفاصيل الفاتورة*\n")
        sb.append("• *رقم الفاتورة:* #").append(invoice.invoiceNumber).append("\n")
        sb.append("• *العميل:* ").append(invoice.clientName).append("\n")
        sb.append("• *التاريخ:* ").append(DateTimeUtils.formatDateTime12h(invoice.date)).append("\n")
        sb.append("• *النوع:* ").append(if (invoice.isQuickInvoice) "فاتورة سريعة ⚡" else "فاتورة مفصلة بالأصناف 📦").append("\n")
        sb.append("• *الحالة:* ").append(if (invoice.isDraft) "مسودة" else "نهائية مكتملة ✅").append("\n")
        sb.append("• *العملة المعتمدة:* ").append(currFullName).append("\n")
        sb.append("━━━━━━━━━━━━━━━━━━━━\n")

        var grandTotalChosen = 0.0

        if (invoice.isQuickInvoice) {
            val desc = invoice.description?.ifBlank { "شراء مستلزمات طبية وأدوية" } ?: "شراء مستلزمات طبية وأدوية"
            val totalChosen = MonetaryUtils.convert(invoice.totalAmount, invoiceCurr, chosenCurrency, rates).convertedAmount
            grandTotalChosen = totalChosen
            sb.append("📝 *البيان / الوصف:*\n").append(desc).append("\n")
            sb.append("   • الإجمالي: *${FormatUtils.formatAmount(totalChosen)} $currSymbol*\n")
            sb.append("━━━━━━━━━━━━━━━━━━━━\n")
        } else if (items.isNotEmpty()) {
            sb.append("📦 *الأصناف المبيعة:*\n")
            items.forEachIndexed { index, item ->
                val rawCurr = item.currency.ifBlank { invoiceCurr }
                val itemCurr = MonetaryUtils.normalizeCurrency(rawCurr)
                val qty = item.quantity
                val unitPrice = item.unitPrice
                val itemTotalPrice = if (item.totalPrice > 0.001) item.totalPrice else unitPrice * qty

                val pChosen = if (itemCurr == chosenCurrency) unitPrice else {
                    MonetaryUtils.convert(unitPrice, itemCurr, chosenCurrency, rates).convertedAmount
                }
                val totChosen = if (itemCurr == chosenCurrency) itemTotalPrice else {
                    MonetaryUtils.convert(itemTotalPrice, itemCurr, chosenCurrency, rates).convertedAmount
                }

                grandTotalChosen += totChosen

                sb.append("${index + 1}. *${item.itemName}* (الكمية: $qty)\n")
                sb.append("   ▫️ الوحدة: ${FormatUtils.formatAmount(pChosen)} $currSymbol  |  الإجمالي: *${FormatUtils.formatAmount(totChosen)} $currSymbol*\n")
            }
            sb.append("━━━━━━━━━━━━━━━━━━━━\n")
            if (showVatAndSubtotal) {
                sb.append("💵 *المجموع الفرعي:* ")
                    .append(FormatUtils.formatAmount(grandTotalChosen)).append(" $currSymbol\n")
            }
            if (invoice.discount > 0) {
                val discChosen = MonetaryUtils.convert(invoice.discount, invoiceCurr, chosenCurrency, rates).convertedAmount
                sb.append("🏷️ *الخصم الممنوح:* -")
                    .append(FormatUtils.formatAmount(discChosen)).append(" $currSymbol\n")
            }
        }

        val totalChosen = MonetaryUtils.convert(invoice.totalAmount, invoiceCurr, chosenCurrency, rates).convertedAmount
        val paidChosen = MonetaryUtils.convert(invoice.paidAmount, invoiceCurr, chosenCurrency, rates).convertedAmount
        val remChosen = (totalChosen - paidChosen).coerceAtLeast(0.0)

        sb.append("💰 *المبلغ الإجمالي للفاتورة:* *${FormatUtils.formatAmount(totalChosen)} $currSymbol*\n")
        sb.append("🟢 *المدفوع:* ${FormatUtils.formatAmount(paidChosen)} $currSymbol\n")
        sb.append("🔴 *المتبقي:* ").append(if (remChosen > 0.001) "*${FormatUtils.formatAmount(remChosen)} $currSymbol*" else "0.00 $currSymbol (مسدد خالص)").append("\n")

        // Equivalent currencies only when targetCurrency is explicitly "الكل"
        if (targetCurrency == "الكل") {
            val eqList = mutableListOf<String>()
            if (chosenCurrency != MonetaryUtils.CURRENCY_YER) {
                val eqYer = MonetaryUtils.convert(totalChosen, chosenCurrency, MonetaryUtils.CURRENCY_YER, rates).convertedAmount
                eqList.add("${FormatUtils.formatAmount(eqYer)} ر.ي")
            }
            if (chosenCurrency != MonetaryUtils.CURRENCY_SAR) {
                val eqSar = MonetaryUtils.convert(totalChosen, chosenCurrency, MonetaryUtils.CURRENCY_SAR, rates).convertedAmount
                eqList.add("${FormatUtils.formatAmount(eqSar)} ر.س")
            }
            if (chosenCurrency != MonetaryUtils.CURRENCY_USD) {
                val eqUsd = MonetaryUtils.convert(totalChosen, chosenCurrency, MonetaryUtils.CURRENCY_USD, rates).convertedAmount
                eqList.add("$${FormatUtils.formatAmount(eqUsd)}")
            }
            if (eqList.isNotEmpty()) {
                sb.append("💱 *ما يعادل بالعملات الأخرى:* ").append(eqList.joinToString("  |  ")).append("\n")
            }
        }

        if (clientBalance != null) {
            sb.append("━━━━━━━━━━━━━━━━━━━━\n")
            val cCurr = MonetaryUtils.normalizeCurrency(clientCurrency ?: invoiceCurr)
            val balChosen = MonetaryUtils.convert(abs(clientBalance), cCurr, chosenCurrency, rates).convertedAmount

            if (clientBalance > 0.001) {
                sb.append("⚠️ *إجمالي الرصيد المتبقي المستحق على العميل:* *${FormatUtils.formatAmount(balChosen)} $currSymbol* (مدين)\n")
            } else if (abs(clientBalance) <= 0.001) {
                sb.append("✅ *حساب العميل خالص بالكامل بدون مديونية*\n")
            } else {
                sb.append("🟢 *رصيد العميل الدائن المتبقي لصالحه:* *${FormatUtils.formatAmount(balChosen)} $currSymbol* (دائن)\n")
            }
        }

        if (!invoice.notes.isNullOrBlank()) {
            sb.append("━━━━━━━━━━━━━━━━━━━━\n")
            sb.append("📌 *ملاحظات:* ").append(invoice.notes).append("\n")
        }

        sb.append("━━━━━━━━━━━━━━━━━━━━\n")
        sb.append("✨ *شكراً لتعاملكم معنا ونرحب بكم دائماً!* ✨")
        return sb.toString()
    }

    /**
     * Cleans up telephone numbers and opens WhatsApp directly to chat with the recipient.
     */
    fun sendWhatsAppMessage(context: Context, phone: String?, messageText: String) {
        try {
            var cleanPhone = phone?.filter { it.isDigit() || it == '+' } ?: ""
            if (cleanPhone.startsWith("+")) {
                cleanPhone = cleanPhone.substring(1)
            } else if (cleanPhone.startsWith("00")) {
                cleanPhone = cleanPhone.substring(2)
            }

            // If Yemeni number without international code (e.g. 7xxxxxxxx or 07xxxxxxxx)
            if (cleanPhone.startsWith("07") && cleanPhone.length == 10) {
                cleanPhone = "967" + cleanPhone.substring(1)
            } else if (cleanPhone.startsWith("7") && cleanPhone.length == 9) {
                cleanPhone = "967$cleanPhone"
            } else if (cleanPhone.startsWith("05") && cleanPhone.length == 10) { // Saudi local
                cleanPhone = "966" + cleanPhone.substring(1)
            }

            val encodedMsg = Uri.encode(messageText)
            val uri = if (cleanPhone.isNotBlank()) {
                Uri.parse("https://api.whatsapp.com/send?phone=$cleanPhone&text=$encodedMsg")
            } else {
                Uri.parse("https://api.whatsapp.com/send?text=$encodedMsg")
            }

            // Attempt standard WhatsApp package first
            val whatsappIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.whatsapp")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            try {
                context.startActivity(whatsappIntent)
            } catch (e: Exception) {
                // Try WhatsApp Business package
                try {
                    val businessIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                        setPackage("com.whatsapp.w4b")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(businessIntent)
                } catch (e2: Exception) {
                    // Fallback to browser or generic handler
                    try {
                        val genericIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(genericIntent)
                    } catch (e3: Exception) {
                        // Ultimate fallback: ACTION_SEND intent chooser
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, messageText)
                            type = "text/plain"
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "إرسال الفاتورة عبر:"))
                    }
                }
            }
        } catch (e: Exception) {
            Toast.makeText(context, "تعذر فتح تطبيق الواتس آب: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }
}
