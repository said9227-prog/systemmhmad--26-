package com.example.util.pdf.mapper

import android.graphics.Color
import com.example.data.model.Client
import com.example.data.model.ExchangeRate
import com.example.data.model.Invoice
import com.example.data.model.InvoiceItem
import com.example.data.model.Item
import com.example.data.model.StoreSettings
import com.example.util.FormatUtils
import com.example.util.MonetaryUtils
import com.example.util.pdf.model.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

object InvoicePdfMapper {

    fun mapToPdfModel(
        invoice: Invoice,
        items: List<InvoiceItem>,
        client: Client?,
        settings: StoreSettings,
        rates: List<ExchangeRate> = emptyList(),
        itemsMap: Map<Int, Item> = emptyMap(),
        targetCurrency: String? = null
    ): PdfReportModel {
        val invoiceCurr = MonetaryUtils.normalizeCurrency(invoice.currency.ifBlank { settings.currency.ifBlank { MonetaryUtils.CURRENCY_YER } })
        
        // Resolve chosen currency intelligently based on user selection in details screen
        val chosenCurrency = when {
            targetCurrency == "SAR" || targetCurrency?.contains("سعودي") == true -> MonetaryUtils.CURRENCY_SAR
            targetCurrency == "USD" || targetCurrency?.contains("دولار") == true -> MonetaryUtils.CURRENCY_USD
            targetCurrency == "YER" || targetCurrency?.contains("يمني") == true -> MonetaryUtils.CURRENCY_YER
            !targetCurrency.isNullOrBlank() && targetCurrency != "الكل" -> MonetaryUtils.normalizeCurrency(targetCurrency)
            else -> invoiceCurr
        }

        val (currSymbol, currFullName) = when (chosenCurrency) {
            MonetaryUtils.CURRENCY_SAR -> "ر.س" to "الريال السعودي (SAR)"
            MonetaryUtils.CURRENCY_USD -> "$" to "الدولار الأمريكي (USD)"
            else -> "ر.ي" to "الريال اليمني (YER)"
        }

        val dateFmt = SimpleDateFormat("yyyy/MM/dd", Locale("ar"))
        val formattedDate = dateFmt.format(Date(invoice.date))

        val storeName = settings.storeName.ifBlank { "الحكيمي للأدوية والمستلزمات الطبية" }

        val metaFields = mutableListOf(
            PdfHeaderMeta("رقم الفاتورة", "#${invoice.invoiceNumber}"),
            PdfHeaderMeta("تاريخ الفاتورة", formattedDate),
            PdfHeaderMeta("اسم العميل", invoice.clientName.ifBlank { "عميل نقدي" }),
            PdfHeaderMeta("نوع الفاتورة", if (invoice.isQuickInvoice) "فاتورة سريعة" else "فاتورة مبيعات رسمية"),
            PdfHeaderMeta("العملة المعتمدة", currFullName)
        )

        // Show exchange rate ONLY if uncluttered mode is OFF and user explicitly chose "الكل"
        if (!settings.enableUnclutteredInvoiceMode && targetCurrency == "الكل") {
            val sarToYer = MonetaryUtils.convert(1.0, MonetaryUtils.CURRENCY_SAR, MonetaryUtils.CURRENCY_YER, rates).convertedAmount
            val usdToYer = MonetaryUtils.convert(1.0, MonetaryUtils.CURRENCY_USD, MonetaryUtils.CURRENCY_YER, rates).convertedAmount
            val usdToSar = MonetaryUtils.convert(1.0, MonetaryUtils.CURRENCY_USD, MonetaryUtils.CURRENCY_SAR, rates).convertedAmount

            when (chosenCurrency) {
                MonetaryUtils.CURRENCY_SAR -> {
                    metaFields.add(PdfHeaderMeta("سعر الصرف", "1 ر.س = ${FormatUtils.formatAmount(sarToYer)} ر.ي"))
                }
                MonetaryUtils.CURRENCY_USD -> {
                    metaFields.add(PdfHeaderMeta("سعر الصرف", "1 $ = ${FormatUtils.formatAmount(usdToYer)} ر.ي (1 $ = ${FormatUtils.formatAmount(usdToSar)} ر.س)"))
                }
                MonetaryUtils.CURRENCY_YER -> {
                    metaFields.add(PdfHeaderMeta("سعر الصرف", "1 ر.س = ${FormatUtils.formatAmount(sarToYer)} ر.ي | 1 $ = ${FormatUtils.formatAmount(usdToYer)} ر.ي"))
                }
            }
        }

        if (client != null && client.phone.isNotBlank()) {
            metaFields.add(PdfHeaderMeta("رقم هاتف العميل", client.phone))
        }

        val headerInfo = PdfHeaderInfo(
            storeName = storeName,
            storePhone = settings.storePhone,
            storeAddress = settings.storeAddress,
            reportTitle = if (invoice.isQuickInvoice) "🧾 فاتورة مبيعات سريعة" else "🧾 فاتورة مبيعات رسمية",
            reportSubtitle = "فاتورة مبيعات رقم #${invoice.invoiceNumber} | بالعملة: $currFullName",
            metaFields = metaFields
        )

        // Clean, elegant 5-column layout with clear single-currency headers
        val columns = listOf(
            PdfTableColumn("#", "#", widthWeight = 0.4f, alignment = PdfAlignment.CENTER),
            PdfTableColumn("name", "اسم الصنف والبيان", widthWeight = 4.2f, alignment = PdfAlignment.RIGHT),
            PdfTableColumn("qty", "الكمية", widthWeight = 0.9f, alignment = PdfAlignment.CENTER),
            PdfTableColumn("price", "سعر الوحدة ($currSymbol)", widthWeight = 2.0f, alignment = PdfAlignment.RIGHT),
            PdfTableColumn("total", "الإجمالي ($currSymbol)", widthWeight = 2.2f, alignment = PdfAlignment.RIGHT, isBold = true)
        )

        val rows = mutableListOf<PdfTableRow>()
        var grandTotalChosen = 0.0

        if (invoice.isQuickInvoice) {
            val desc = invoice.description?.ifBlank { "مبيعات عامة سريعة" } ?: "مبيعات عامة سريعة"
            val totalChosen = MonetaryUtils.convert(invoice.totalAmount, invoiceCurr, chosenCurrency, rates).convertedAmount
            grandTotalChosen = totalChosen

            rows.add(
                PdfTableRow(
                    cells = listOf(
                        PdfCell("1", alignment = PdfAlignment.CENTER, isBold = true, textSizeSp = 9.5f),
                        PdfCell(desc, alignment = PdfAlignment.RIGHT, isBold = true, textSizeSp = 9.5f),
                        PdfCell("1", alignment = PdfAlignment.CENTER, isBold = true, textSizeSp = 10f),
                        PdfCell("${FormatUtils.formatAmount(totalChosen)} $currSymbol", alignment = PdfAlignment.RIGHT, isBold = true, textColor = Color.parseColor("#047857"), textSizeSp = 10f),
                        PdfCell("${FormatUtils.formatAmount(totalChosen)} $currSymbol", alignment = PdfAlignment.RIGHT, isBold = true, textColor = Color.parseColor("#1E3A8A"), textSizeSp = 10f)
                    )
                )
            )
        } else {
            items.forEachIndexed { index, item ->
                val storedItem = item.itemId?.let { itemsMap[it] }
                val rawCurr = item.currency.ifBlank { invoice.currency.ifBlank { settings.currency } }
                val itemCurr = MonetaryUtils.normalizeCurrency(rawCurr)
                val qty = item.quantity
                val unitPrice = item.unitPrice
                val itemTotalPrice = if (item.totalPrice > 0.001) item.totalPrice else unitPrice * qty

                val unitPriceChosen = if (itemCurr == chosenCurrency) unitPrice else {
                    MonetaryUtils.convert(unitPrice, itemCurr, chosenCurrency, rates).convertedAmount
                }
                val totalPriceChosen = if (itemCurr == chosenCurrency) itemTotalPrice else {
                    MonetaryUtils.convert(itemTotalPrice, itemCurr, chosenCurrency, rates).convertedAmount
                }

                grandTotalChosen += totalPriceChosen

                // Clean name cell without debug or internal purchase spam
                val nameBuilder = StringBuilder()
                nameBuilder.append(item.itemName)
                if (storedItem?.unit?.isNotBlank() == true && storedItem.unit != "قطعة") {
                    nameBuilder.append(" [${storedItem.unit}]")
                }

                // Total cell with line discount if any
                val totalBuilder = StringBuilder()
                totalBuilder.append("${FormatUtils.formatAmount(totalPriceChosen)} $currSymbol")
                val lineDiscount = (unitPrice * qty) - itemTotalPrice
                if (lineDiscount > 0.001) {
                    val discChosen = MonetaryUtils.convert(lineDiscount, itemCurr, chosenCurrency, rates).convertedAmount
                    totalBuilder.append("\n(خصم: -${FormatUtils.formatAmount(discChosen)} $currSymbol)")
                }

                rows.add(
                    PdfTableRow(
                        cells = listOf(
                            PdfCell((index + 1).toString(), alignment = PdfAlignment.CENTER, isBold = true, textSizeSp = 9.5f),
                            PdfCell(nameBuilder.toString(), alignment = PdfAlignment.RIGHT, textColor = Color.parseColor("#0F172A"), textSizeSp = 9.5f),
                            PdfCell("$qty", alignment = PdfAlignment.CENTER, isBold = true, textSizeSp = 10f, textColor = Color.parseColor("#1E3A8A")),
                            PdfCell("${FormatUtils.formatAmount(unitPriceChosen)} $currSymbol", alignment = PdfAlignment.RIGHT, isBold = true, textSizeSp = 9.5f, textColor = Color.parseColor("#047857")),
                            PdfCell(totalBuilder.toString(), alignment = PdfAlignment.RIGHT, isBold = true, textSizeSp = 10f, textColor = Color.parseColor("#1E3A8A"))
                        )
                    )
                )
            }
        }

        // Summary row for table
        val summaryRows = if (!invoice.isQuickInvoice && items.isNotEmpty()) {
            val totalQty = items.sumOf { it.quantity }
            listOf(
                PdfTableRow(
                    cells = listOf(
                        PdfCell("∑", alignment = PdfAlignment.CENTER, isBold = true, textSizeSp = 10f),
                        PdfCell("إجمالي الأصناف (${items.size} بنود)", alignment = PdfAlignment.RIGHT, isBold = true, textSizeSp = 10f),
                        PdfCell("$totalQty", alignment = PdfAlignment.CENTER, isBold = true, textSizeSp = 10f, textColor = Color.parseColor("#1E3A8A")),
                        PdfCell("-", alignment = PdfAlignment.CENTER),
                        PdfCell(
                            "${FormatUtils.formatAmount(grandTotalChosen)} $currSymbol",
                            alignment = PdfAlignment.RIGHT,
                            isBold = true,
                            textSizeSp = 10.5f,
                            textColor = Color.parseColor("#1E3A8A")
                        )
                    ),
                    backgroundColor = Color.parseColor("#F1F5F9")
                )
            )
        } else emptyList()

        val table = PdfTable(
            title = "جدول الأصناف والبنود المبيعة",
            columns = columns,
            rows = rows,
            summaryRows = summaryRows
        )

        // Financial totals in the chosen currency
        val discountChosen = MonetaryUtils.convert(invoice.discount, invoiceCurr, chosenCurrency, rates).convertedAmount
        val invoiceTotalChosen = MonetaryUtils.convert(invoice.totalAmount, invoiceCurr, chosenCurrency, rates).convertedAmount
        val paidChosen = MonetaryUtils.convert(invoice.paidAmount, invoiceCurr, chosenCurrency, rates).convertedAmount
        val remainingChosen = (invoiceTotalChosen - paidChosen).coerceAtLeast(0.0)
        val subtotalChosen = if (invoice.isQuickInvoice) invoiceTotalChosen else grandTotalChosen

        val summaryItems = mutableListOf<PdfSummaryItem>()

        // 1. Subtotal
        summaryItems.add(
            PdfSummaryItem(
                label = "المجموع الفرعي للأصناف",
                value = "${FormatUtils.formatAmount(subtotalChosen)} $currSymbol"
            )
        )

        // 2. Discount
        if (invoice.discount > 0) {
            summaryItems.add(
                PdfSummaryItem(
                    label = "الخصم الممنوح على الفاتورة",
                    value = "-${FormatUtils.formatAmount(discountChosen)} $currSymbol",
                    isHighlighted = true,
                    highlightColor = Color.parseColor("#FEF2F2")
                )
            )
        }

        // 3. Tax if any
        if (settings.showVatAndSubtotal && invoice.taxRate > 0) {
            val taxChosen = (subtotalChosen - discountChosen) * (invoice.taxRate / 100.0)
            summaryItems.add(
                PdfSummaryItem(
                    label = "ضريبة القيمة المضافة (${invoice.taxRate}%)",
                    value = "${FormatUtils.formatAmount(taxChosen)} $currSymbol"
                )
            )
        }

        // 4. Final Total (Highlighted)
        summaryItems.add(
            PdfSummaryItem(
                label = "⭐ الإجمالي النهائي للفاتورة",
                value = "${FormatUtils.formatAmount(invoiceTotalChosen)} $currSymbol",
                isHighlighted = true,
                highlightColor = Color.parseColor("#E0F2FE")
            )
        )

        // 5. Paid
        summaryItems.add(
            PdfSummaryItem(
                label = "المبلغ المدفوع (نقداً/سند)",
                value = "${FormatUtils.formatAmount(paidChosen)} $currSymbol"
            )
        )

        // 6. Remaining
        val isPending = invoice.remainingAmount > 0 || remainingChosen > 0.001
        summaryItems.add(
            PdfSummaryItem(
                label = if (isPending) "⚠️ المبلغ المتبقي الآجل المطلوب" else "✅ المبلغ المتبقي (مسدد خالص)",
                value = if (isPending) "${FormatUtils.formatAmount(remainingChosen)} $currSymbol" else "0.00 $currSymbol (مسدد بالكامل)",
                isHighlighted = isPending,
                highlightColor = if (isPending) Color.parseColor("#FEF2F2") else Color.parseColor("#F0FDF4")
            )
        )

        // 7. Equivalent in other currencies for reference (Only if uncluttered mode is OFF and user explicitly chose "الكل")
        if (!settings.enableUnclutteredInvoiceMode && targetCurrency == "الكل") {
            val eqList = mutableListOf<String>()
            if (chosenCurrency != MonetaryUtils.CURRENCY_YER) {
                val eqYer = MonetaryUtils.convert(invoiceTotalChosen, chosenCurrency, MonetaryUtils.CURRENCY_YER, rates).convertedAmount
                eqList.add("${FormatUtils.formatAmount(eqYer)} ر.ي")
            }
            if (chosenCurrency != MonetaryUtils.CURRENCY_SAR) {
                val eqSar = MonetaryUtils.convert(invoiceTotalChosen, chosenCurrency, MonetaryUtils.CURRENCY_SAR, rates).convertedAmount
                eqList.add("${FormatUtils.formatAmount(eqSar)} ر.س")
            }
            if (chosenCurrency != MonetaryUtils.CURRENCY_USD) {
                val eqUsd = MonetaryUtils.convert(invoiceTotalChosen, chosenCurrency, MonetaryUtils.CURRENCY_USD, rates).convertedAmount
                eqList.add("$${FormatUtils.formatAmount(eqUsd)}")
            }
            if (eqList.isNotEmpty()) {
                summaryItems.add(
                    PdfSummaryItem(
                        label = "المعادل التقريبي بالعملات الأخرى",
                        value = eqList.joinToString("  |  "),
                        isHighlighted = false,
                        highlightColor = Color.parseColor("#F8FAFC")
                    )
                )
            }
        }

        // 8. Client current balance in chosen currency
        if (client != null) {
            val clientCurr = MonetaryUtils.normalizeCurrency(client.currency.ifBlank { settings.currency })
            val clientBal = client.balance
            val clientBalChosen = MonetaryUtils.convert(abs(clientBal), clientCurr, chosenCurrency, rates).convertedAmount

            val statusText = when {
                clientBal > 0.001 -> "مدين / مستحق عليه"
                clientBal < -0.001 -> "دائن / لصالح العميل"
                else -> "الحساب متعادل وخالص"
            }

            val balValue = if (abs(clientBal) > 0.001) {
                "${FormatUtils.formatAmount(clientBalChosen)} $currSymbol ($statusText)"
            } else {
                "0.00 $currSymbol (خالص)"
            }

            summaryItems.add(
                PdfSummaryItem(
                    label = "👤 إجمالي رصيد حساب العميل",
                    value = balValue,
                    isHighlighted = clientBal > 0.001,
                    highlightColor = if (clientBal > 0.001) Color.parseColor("#FEE2E2") else Color.parseColor("#F0FDF4")
                )
            )
        }

        return PdfReportModel(
            title = "فاتورة رقم #${invoice.invoiceNumber}",
            headerInfo = headerInfo,
            tables = listOf(table),
            summaryInfo = PdfSummaryInfo(title = "الملخص المالي للفاتورة", items = summaryItems),
            notes = invoice.notes,
            orientation = PdfOrientation.PORTRAIT,
            currency = currFullName
        )
    }
}

