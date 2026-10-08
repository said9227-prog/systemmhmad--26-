package com.example.util

import android.content.Context
import android.graphics.Color
import com.example.data.model.Client
import com.example.data.model.Invoice
import com.example.data.model.Payment
import com.example.data.model.ProductReturn
import com.example.data.model.StoreSettings
import com.example.domain.report.ReportData
import com.example.util.pdf.engine.PdfTableRenderer
import com.example.util.pdf.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object ReportPdfManager {

    suspend fun exportFinancialReportToPdf(
        context: Context,
        reportData: ReportData,
        settings: StoreSettings
    ): File? = withContext(Dispatchers.IO) {
        try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val dateRangeStr = "الفترة: من ${sdf.format(Date(reportData.dateRange.start))} إلى ${sdf.format(Date(reportData.dateRange.end))}"
            val storeName = settings.storeName.ifBlank { "الحكيمي للأدوية والمستلزمات الطبية" }

            val metaFields = listOf(
                PdfHeaderMeta("العملة الرئيسية للمتجر", settings.currency.ifBlank { "الريال اليمني" }),
                PdfHeaderMeta("العملة المحددة للتقرير", reportData.selectedCurrency),
                PdfHeaderMeta("نطاق الفترة الزمنية", dateRangeStr),
                PdfHeaderMeta("عدد أجزاء التقارير حسب العملات", "${reportData.currencyReports.size} عملة")
            )

            val headerInfo = PdfHeaderInfo(
                storeName = storeName,
                storePhone = settings.storePhone,
                storeAddress = settings.storeAddress,
                reportTitle = "📊 التقرير المالي والإحصائي الشامل",
                reportSubtitle = "تحليل الأداء المالي، المبيعات، المشتريات والمخزون",
                metaFields = metaFields
            )

            val tables = mutableListOf<PdfTable>()

            // Financial summary by currency
            val currencyColumns = listOf(
                PdfTableColumn("#", "#", widthWeight = 0.5f, alignment = PdfAlignment.CENTER),
                PdfTableColumn("currency", "العملة", widthWeight = 1.2f, alignment = PdfAlignment.CENTER, isBold = true),
                PdfTableColumn("sales", "إجمالي المبيعات", widthWeight = 1.8f, alignment = PdfAlignment.LEFT),
                PdfTableColumn("salesReturns", "مرتجع المبيعات", widthWeight = 1.5f, alignment = PdfAlignment.LEFT),
                PdfTableColumn("netSales", "صافي المبيعات", widthWeight = 1.8f, alignment = PdfAlignment.LEFT, isBold = true),
                PdfTableColumn("purchases", "إجمالي المشتريات", widthWeight = 1.8f, alignment = PdfAlignment.LEFT),
                PdfTableColumn("netPurchases", "صافي المشتريات", widthWeight = 1.8f, alignment = PdfAlignment.LEFT)
            )

            val currencyRows = reportData.currencyReports.mapIndexed { index, cr ->
                PdfTableRow(
                    cells = listOf(
                        PdfCell((index + 1).toString(), alignment = PdfAlignment.CENTER),
                        PdfCell(cr.currency, alignment = PdfAlignment.CENTER, isBold = true),
                        PdfCell("${FormatUtils.formatAmount(cr.salesReport.totalSales)} ${cr.currency}", alignment = PdfAlignment.LEFT),
                        PdfCell("${FormatUtils.formatAmount(cr.salesReport.totalReturns)} ${cr.currency}", alignment = PdfAlignment.LEFT),
                        PdfCell("${FormatUtils.formatAmount(cr.salesReport.netSales)} ${cr.currency}", alignment = PdfAlignment.LEFT, isBold = true),
                        PdfCell("${FormatUtils.formatAmount(cr.purchaseReport.totalPurchases)} ${cr.currency}", alignment = PdfAlignment.LEFT),
                        PdfCell("${FormatUtils.formatAmount(cr.purchaseReport.netPurchases)} ${cr.currency}", alignment = PdfAlignment.LEFT)
                    )
                )
            }

            tables.add(
                PdfTable(
                    title = "جدول الأداء المالي والمبيعات والمشتريات حسب العملات",
                    columns = currencyColumns,
                    rows = currencyRows
                )
            )

            // Customers and Debts report by currency
            val debtColumns = listOf(
                PdfTableColumn("#", "#", widthWeight = 0.5f, alignment = PdfAlignment.CENTER),
                PdfTableColumn("currency", "العملة", widthWeight = 1.2f, alignment = PdfAlignment.CENTER, isBold = true),
                PdfTableColumn("activeCust", "العملاء النشطين", widthWeight = 1.5f, alignment = PdfAlignment.CENTER),
                PdfTableColumn("unpaidInvs", "فواتير الديون", widthWeight = 1.5f, alignment = PdfAlignment.CENTER),
                PdfTableColumn("dueToday", "مستحق القيمة", widthWeight = 2.0f, alignment = PdfAlignment.LEFT),
                PdfTableColumn("overdue60", "متأخر +60 يوم", widthWeight = 2.0f, alignment = PdfAlignment.LEFT)
            )

            val debtRows = reportData.currencyReports.mapIndexed { index, cr ->
                PdfTableRow(
                    cells = listOf(
                        PdfCell((index + 1).toString(), alignment = PdfAlignment.CENTER),
                        PdfCell(cr.currency, alignment = PdfAlignment.CENTER, isBold = true),
                        PdfCell("${cr.customerReport.activeCustomersCount} عميل", alignment = PdfAlignment.CENTER),
                        PdfCell("${cr.debtAgingReport.totalInvoices} فاتورة", alignment = PdfAlignment.CENTER),
                        PdfCell("${FormatUtils.formatAmount(cr.debtAgingReport.currentDue)} ${cr.currency}", alignment = PdfAlignment.LEFT),
                        PdfCell("${FormatUtils.formatAmount(cr.debtAgingReport.overdue60Plus)} ${cr.currency}", alignment = PdfAlignment.LEFT, textColor = Color.parseColor("#DC2626"), isBold = true)
                    )
                )
            }

            tables.add(
                PdfTable(
                    title = "جدول تحليل الديون والعملاء وأعمار الديون",
                    columns = debtColumns,
                    rows = debtRows
                )
            )

            // Inventory Summary Table
            if (reportData.currencyReports.isNotEmpty()) {
                val inv = reportData.currencyReports.first().inventoryReport
                val invColumns = listOf(
                    PdfTableColumn("totalItems", "إجمالي الأصناف بالمخزون", widthWeight = 2.0f, alignment = PdfAlignment.CENTER),
                    PdfTableColumn("outOfStock", "الأصناف النافدة", widthWeight = 2.0f, alignment = PdfAlignment.CENTER),
                    PdfTableColumn("totalQty", "إجمالي الكمية والقطع المتوفرة", widthWeight = 2.0f, alignment = PdfAlignment.CENTER, isBold = true)
                )
                val invRows = listOf(
                    PdfTableRow(
                        cells = listOf(
                            PdfCell("${inv.totalItemsCount} صنف", alignment = PdfAlignment.CENTER),
                            PdfCell("${inv.outOfStockItemsCount} صنف", alignment = PdfAlignment.CENTER, textColor = Color.parseColor("#DC2626"), isBold = true),
                            PdfCell("${inv.totalQuantity} قطعة", alignment = PdfAlignment.CENTER, isBold = true)
                        )
                    )
                )
                tables.add(
                    PdfTable(
                        title = "جدول حصر وإحصائيات المخزون العام",
                        columns = invColumns,
                        rows = invRows
                    )
                )
            }

            val summaryItems = mutableListOf<PdfSummaryItem>()
            reportData.currencyReports.forEach { cr ->
                summaryItems.add(PdfSummaryItem("صافي المبيعات (${cr.currency})", "${FormatUtils.formatAmount(cr.salesReport.netSales)} ${cr.currency}", isHighlighted = true, highlightColor = Color.parseColor("#ECFDF5")))
                summaryItems.add(PdfSummaryItem("صافي المشتريات (${cr.currency})", "${FormatUtils.formatAmount(cr.purchaseReport.netPurchases)} ${cr.currency}"))
            }

            val reportModel = PdfReportModel(
                title = "التقرير المالي والإحصائي",
                headerInfo = headerInfo,
                tables = tables,
                summaryInfo = PdfSummaryInfo(title = "الملخص المالي العام للتقرير", items = summaryItems)
            )

            val reportsDir = File(context.cacheDir, "reports")
            if (!reportsDir.exists()) reportsDir.mkdirs()

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val file = File(reportsDir, "تقرير_مالي_$timestamp.pdf")

            val result = PdfTableRenderer.renderReportToPdf(reportModel, file)

            result.getOrNull()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun exportMultiClientReportToPdf(
        context: Context,
        selectedClients: List<Client>,
        invoices: List<Invoice>,
        payments: List<Payment>,
        returns: List<ProductReturn>,
        settings: StoreSettings,
        startDate: Long? = null,
        endDate: Long? = null
    ): File? = withContext(Dispatchers.IO) {
        try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val timeSdf = SimpleDateFormat("yyyy-MM-dd hh:mm a", Locale.getDefault())
            val storeName = settings.storeName.ifBlank { "الحكيمي للأدوية والمستلزمات الطبية" }

            val periodStr = if (startDate != null && endDate != null) {
                "من ${sdf.format(Date(startDate))} إلى ${sdf.format(Date(endDate))}"
            } else {
                "جميع المعاملات التاريخية"
            }

            val metaFields = listOf(
                PdfHeaderMeta("عدد العملاء المحددين", "${selectedClients.size} عميل"),
                PdfHeaderMeta("الفترة الزمنية", periodStr),
                PdfHeaderMeta("تاريخ استخراج التقرير", sdf.format(Date()))
            )

            val headerInfo = PdfHeaderInfo(
                storeName = storeName,
                storePhone = settings.storePhone,
                storeAddress = settings.storeAddress,
                reportTitle = "📋 تقرير تفصيلي شامل للعملاء المحددين",
                reportSubtitle = "حصر المعاملات، الفواتير، الدفعات والمرتجعات للعملاء المحددين",
                metaFields = metaFields
            )

            val tables = mutableListOf<PdfTable>()

            // Summary Table per Client
            val clientColumns = listOf(
                PdfTableColumn("#", "#", widthWeight = 0.5f, alignment = PdfAlignment.CENTER),
                PdfTableColumn("name", "اسم العميل", widthWeight = 2.5f, alignment = PdfAlignment.RIGHT),
                PdfTableColumn("phone", "الهاتف", widthWeight = 1.3f, alignment = PdfAlignment.CENTER),
                PdfTableColumn("currency", "العملة", widthWeight = 1.0f, alignment = PdfAlignment.CENTER),
                PdfTableColumn("invoicesCount", "الفواتير", widthWeight = 1.0f, alignment = PdfAlignment.CENTER),
                PdfTableColumn("paymentsCount", "الدفعات", widthWeight = 1.0f, alignment = PdfAlignment.CENTER),
                PdfTableColumn("balance", "الرصيد المتبقي", widthWeight = 1.8f, alignment = PdfAlignment.LEFT, isBold = true)
            )

            val clientRows = selectedClients.mapIndexed { index, client ->
                val cInvoices = invoices.filter { it.clientId == client.id && !it.isDraft }
                val cPayments = payments.filter { it.clientId == client.id }
                val clientMainCurr = client.currency.ifBlank { settings.currency.ifBlank { "الريال اليمني" } }

                PdfTableRow(
                    cells = listOf(
                        PdfCell((index + 1).toString(), alignment = PdfAlignment.CENTER),
                        PdfCell(client.name, alignment = PdfAlignment.RIGHT, isBold = true),
                        PdfCell(client.phone.ifBlank { "-" }, alignment = PdfAlignment.CENTER),
                        PdfCell(clientMainCurr, alignment = PdfAlignment.CENTER),
                        PdfCell("${cInvoices.size} فاتورة", alignment = PdfAlignment.CENTER),
                        PdfCell("${cPayments.size} دفعة", alignment = PdfAlignment.CENTER),
                        PdfCell("${FormatUtils.formatAmount(client.balance)} $clientMainCurr", alignment = PdfAlignment.LEFT, isBold = true)
                    )
                )
            }

            tables.add(
                PdfTable(
                    title = "جدول ملخص أرصدة ومعاملات العملاء المحددين",
                    columns = clientColumns,
                    rows = clientRows
                )
            )

            // Detailed Ledger Table for each client
            selectedClients.forEach { client ->
                val clientMainCurr = client.currency.ifBlank { settings.currency.ifBlank { "الريال اليمني" } }
                val clientInvs = invoices.filter { it.clientId == client.id && !it.isDraft }
                val clientPays = payments.filter { it.clientId == client.id }
                val clientRets = returns.filter { it.clientId == client.id }

                data class TempEntry(
                    val date: Long,
                    val type: String,
                    val label: String,
                    val debit: Double,
                    val credit: Double,
                    val currency: String
                )

                val rawEntries = mutableListOf<TempEntry>()

                clientInvs.forEach { inv ->
                    val invCurr = inv.currency.ifBlank { clientMainCurr }
                    rawEntries.add(
                        TempEntry(
                            date = inv.date,
                            type = "فاتورة بيع",
                            label = "فاتورة #${inv.invoiceNumber}${if (!inv.notes.isNullOrBlank()) " (${inv.notes})" else ""}",
                            debit = inv.totalAmount,
                            credit = 0.0,
                            currency = invCurr
                        )
                    )
                }

                clientPays.forEach { pay ->
                    val payCurr = pay.currency.ifBlank { clientMainCurr }
                    rawEntries.add(
                        TempEntry(
                            date = pay.date,
                            type = "دفعة سداد",
                            label = "سداد (${pay.paymentMethod})${if (!pay.voucherNumber.isNullOrBlank()) " سند #${pay.voucherNumber}" else ""}",
                            debit = 0.0,
                            credit = pay.amount,
                            currency = payCurr
                        )
                    )
                }

                clientRets.forEach { ret ->
                    val isAccountCredit = ret.settlementType != "استرداد نقدي"
                    val retCurr = ret.currency.ifBlank { clientMainCurr }
                    rawEntries.add(
                        TempEntry(
                            date = ret.date,
                            type = "مرتجع",
                            label = "مرتجع #${ret.returnNumber} (${ret.settlementType})",
                            debit = 0.0,
                            credit = if (isAccountCredit) ret.totalAmount else 0.0,
                            currency = retCurr
                        )
                    )
                }

                var sortedEntries = rawEntries.sortedBy { it.date }
                if (startDate != null && endDate != null) {
                    sortedEntries = sortedEntries.filter { it.date in startDate..endDate }
                }

                if (sortedEntries.isNotEmpty()) {
                    val detailColumns = listOf(
                        PdfTableColumn("#", "#", widthWeight = 0.5f, alignment = PdfAlignment.CENTER),
                        PdfTableColumn("date", "التاريخ والوقت", widthWeight = 1.8f, alignment = PdfAlignment.CENTER),
                        PdfTableColumn("type", "نوع العملية", widthWeight = 1.2f, alignment = PdfAlignment.CENTER, isBold = true),
                        PdfTableColumn("label", "البيان التفصيلي للعملية", widthWeight = 3.0f, alignment = PdfAlignment.RIGHT),
                        PdfTableColumn("debit", "مدين (+)", widthWeight = 1.5f, alignment = PdfAlignment.LEFT),
                        PdfTableColumn("credit", "دائن (-)", widthWeight = 1.5f, alignment = PdfAlignment.LEFT),
                        PdfTableColumn("balance", "الرصيد الجاري", widthWeight = 1.8f, alignment = PdfAlignment.LEFT, isBold = true)
                    )

                    var runningBal = 0.0
                    val detailRows = sortedEntries.mapIndexed { index, entry ->
                        runningBal += (entry.debit - entry.credit)
                        val timeStr = try { timeSdf.format(Date(entry.date)) } catch (_: Exception) { "" }

                        PdfTableRow(
                            cells = listOf(
                                PdfCell((index + 1).toString(), alignment = PdfAlignment.CENTER),
                                PdfCell(timeStr, alignment = PdfAlignment.CENTER),
                                PdfCell(entry.type, alignment = PdfAlignment.CENTER, isBold = true),
                                PdfCell(entry.label, alignment = PdfAlignment.RIGHT),
                                PdfCell(if (entry.debit > 0) "${FormatUtils.formatAmount(entry.debit)} ${entry.currency}" else "-", alignment = PdfAlignment.LEFT, textColor = if (entry.debit > 0) Color.parseColor("#DC2626") else Color.BLACK),
                                PdfCell(if (entry.credit > 0) "${FormatUtils.formatAmount(entry.credit)} ${entry.currency}" else "-", alignment = PdfAlignment.LEFT, textColor = if (entry.credit > 0) Color.parseColor("#059669") else Color.BLACK),
                                PdfCell("${FormatUtils.formatAmount(runningBal)} $clientMainCurr", alignment = PdfAlignment.LEFT, isBold = true)
                            )
                        )
                    }

                    tables.add(
                        PdfTable(
                            title = "كشف حركة حساب العميل: ${client.name} (${client.phone.ifBlank { "بدون هاتف" }})",
                            columns = detailColumns,
                            rows = detailRows
                        )
                    )
                }
            }

            val summaryItems = listOf(
                PdfSummaryItem("عدد العملاء في التقرير", "${selectedClients.size} عميل"),
                PdfSummaryItem("إجمالي الديون القائمة للعملاء المحددين", "${FormatUtils.formatAmount(selectedClients.filter { it.balance > 0 }.sumOf { it.balance })} ${settings.currency}", isHighlighted = true, highlightColor = Color.parseColor("#FEF2F2"))
            )

            val reportModel = PdfReportModel(
                title = "تقرير العملاء المخصص",
                headerInfo = headerInfo,
                tables = tables,
                summaryInfo = PdfSummaryInfo(title = "الملخص الإجمالي", items = summaryItems)
            )

            val reportsDir = File(context.cacheDir, "reports")
            if (!reportsDir.exists()) reportsDir.mkdirs()

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val file = File(reportsDir, "تقرير_عملاء_مخصص_$timestamp.pdf")

            val result = PdfTableRenderer.renderReportToPdf(reportModel, file)

            result.getOrNull()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
