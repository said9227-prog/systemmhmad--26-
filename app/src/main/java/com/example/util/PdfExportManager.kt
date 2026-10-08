package com.example.util

import android.content.Context
import android.graphics.Color
import com.example.data.model.Client
import com.example.data.model.Invoice
import com.example.data.model.Payment
import com.example.data.model.ProductReturn
import com.example.data.model.ProductReturnItem
import com.example.data.model.StoreSettings
import com.example.util.pdf.engine.PdfTableRenderer
import com.example.util.pdf.mapper.InvoicePdfMapper
import com.example.util.pdf.mapper.ReturnPdfMapper
import com.example.util.pdf.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Utility to format Arabic timestamp with 12h time (ص/م)
 */
fun formatPdfTimestamp(timeMillis: Long): String {
    val cal = Calendar.getInstance().apply { this.timeInMillis = timeMillis }
    val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
    val arabicDayName = when (dayOfWeek) {
        Calendar.SUNDAY -> "الأحد"
        Calendar.MONDAY -> "الإثنين"
        Calendar.TUESDAY -> "الثلاثاء"
        Calendar.WEDNESDAY -> "الأربعاء"
        Calendar.THURSDAY -> "الخميس"
        Calendar.FRIDAY -> "الجمعة"
        Calendar.SATURDAY -> "السبت"
        else -> ""
    }
    val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val hour12 = cal.get(Calendar.HOUR).let { if (it == 0) 12 else it }
    val minuteStr = String.format("%02d", cal.get(Calendar.MINUTE))
    val amPmStr = if (cal.get(Calendar.AM_PM) == Calendar.AM) "ص" else "م"

    return "$arabicDayName ${dateFmt.format(cal.time)} | $hour12:$minuteStr $amPmStr"
}

/**
 * Exports a comprehensive PDF report containing all clients, their debt balances,
 * invoice counts, and payment counts using the unified PDF Table Renderer.
 */
suspend fun exportAllClientsStatementToPdf(
    context: Context,
    clientsList: List<Client>,
    invoicesList: List<Invoice>,
    paymentsList: List<Payment>,
    settings: StoreSettings
): File? = withContext(Dispatchers.IO) {
    try {
        val currency = settings.currency.ifBlank { "الريال اليمني" }
        val storeName = settings.storeName.ifBlank { "الحكيمي للأدوية والمستلزمات الطبية" }
        val formattedTime = formatPdfTimestamp(System.currentTimeMillis())

        val totalDebts = clientsList.filter { it.balance > 0 }.sumOf { it.balance }

        val metaFields = listOf(
            PdfHeaderMeta("إجمالي عدد العملاء", "${clientsList.size} عميل"),
            PdfHeaderMeta("إجمالي الفواتير", "${invoicesList.size} فاتورة"),
            PdfHeaderMeta("إجمالي الدفعات المسجلة", "${paymentsList.size} دفعة"),
            PdfHeaderMeta("تاريخ استخراج التقرير", formattedTime),
            PdfHeaderMeta("العملة المعتمدة", currency)
        )

        val headerInfo = PdfHeaderInfo(
            storeName = storeName,
            storePhone = settings.storePhone,
            storeAddress = settings.storeAddress,
            reportTitle = "👥 تقرير شـامل لجميع كشوفات العملاء والديون",
            reportSubtitle = "نسخة احتياطية رسمية وحصر الأرصدة والديون المستحقة",
            metaFields = metaFields
        )

        val columns = listOf(
            PdfTableColumn("#", "#", widthWeight = 0.5f, alignment = PdfAlignment.CENTER),
            PdfTableColumn("name", "اسم العميل", widthWeight = 3.0f, alignment = PdfAlignment.RIGHT),
            PdfTableColumn("phone", "الهاتف", widthWeight = 1.5f, alignment = PdfAlignment.CENTER),
            PdfTableColumn("invoices", "عدد الفواتير", widthWeight = 1.2f, alignment = PdfAlignment.CENTER),
            PdfTableColumn("payments", "عدد الدفعات", widthWeight = 1.2f, alignment = PdfAlignment.CENTER),
            PdfTableColumn("balance", "الرصيد المتبقي ($currency)", widthWeight = 2.0f, alignment = PdfAlignment.LEFT, isBold = true)
        )

        val sortedClients = clientsList.sortedByDescending { it.balance }
        val rows = sortedClients.mapIndexed { index, client ->
            val cInvoices = invoicesList.count { it.clientId == client.id }
            val cPayments = paymentsList.count { it.clientId == client.id }
            val balColor = when {
                client.balance > 0 -> Color.parseColor("#DC2626")
                client.balance < 0 -> Color.parseColor("#059669")
                else -> Color.BLACK
            }

            PdfTableRow(
                cells = listOf(
                    PdfCell((index + 1).toString(), alignment = PdfAlignment.CENTER),
                    PdfCell(client.name, alignment = PdfAlignment.RIGHT, isBold = true),
                    PdfCell(client.phone.ifBlank { "-" }, alignment = PdfAlignment.CENTER),
                    PdfCell("$cInvoices فاتورة", alignment = PdfAlignment.CENTER),
                    PdfCell("$cPayments دفعة", alignment = PdfAlignment.CENTER),
                    PdfCell(FormatUtils.formatAmount(client.balance), alignment = PdfAlignment.LEFT, textColor = balColor, isBold = true)
                )
            )
        }

        val summaryItems = listOf(
            PdfSummaryItem("إجمالي عدد العملاء المسجلين", "${clientsList.size} عميل"),
            PdfSummaryItem("عدد العملاء المديونين (عليهم مستحقات)", "${clientsList.count { it.balance > 0 }} عميل"),
            PdfSummaryItem("إجمالي الديون القائمة المستحقة على العملاء", "${FormatUtils.formatAmount(totalDebts)} $currency", isHighlighted = true, highlightColor = Color.parseColor("#FEF2F2"))
        )

        val table = PdfTable(
            title = "جدول تفاصيل أرصدة وكشوفات العملاء",
            columns = columns,
            rows = rows
        )

        val reportModel = PdfReportModel(
            title = "تقرير جميع كشوفات العملاء",
            headerInfo = headerInfo,
            tables = listOf(table),
            summaryInfo = PdfSummaryInfo(title = "الملخص العام للديون", items = summaryItems)
        )

        val reportsDir = File(context.cacheDir, "shared_statements")
        if (!reportsDir.exists()) reportsDir.mkdirs()
        val destinationFile = File(reportsDir, "نسخة_كشوفات_العملاء.pdf")

        val result = PdfTableRenderer.renderReportToPdf(reportModel, destinationFile)

        result.getOrNull()
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

suspend fun exportInvoiceToPdf(
    context: Context,
    invoice: Invoice,
    items: List<com.example.data.model.InvoiceItem>,
    storeName: String = "",
    client: Client? = null,
    storeSettings: StoreSettings? = null,
    exchangeRates: List<com.example.data.model.ExchangeRate> = emptyList(),
    targetCurrency: String? = null
): String? = withContext(Dispatchers.IO) {
    try {
        val db = com.example.data.database.AppDatabase.getDatabase(context)
        val settings = storeSettings ?: try {
            db.storeSettingsDao().getSettings()
        } catch (e: Exception) {
            null
        } ?: StoreSettings(storeName = storeName.ifBlank { "الحكيمي للأدوية والمستلزمات الطبية" })

        val rates = if (exchangeRates.isNotEmpty()) exchangeRates else try {
            db.exchangeRateDao().getAllRates()
        } catch (e: Exception) {
            emptyList()
        }

        val itemsMap = try {
            val ids = items.mapNotNull { it.itemId }.distinct()
            ids.mapNotNull { id -> db.itemDao().getItemById(id) }.associateBy { it.id }
        } catch (e: Exception) {
            emptyMap()
        }

        val pdfModel = InvoicePdfMapper.mapToPdfModel(
            invoice = invoice,
            items = items,
            client = client,
            settings = settings,
            rates = rates,
            itemsMap = itemsMap,
            targetCurrency = targetCurrency
        )
        val dir = File(context.cacheDir, "shared_invoices")
        if (!dir.exists()) dir.mkdirs()
        val currSuffix = if (!targetCurrency.isNullOrBlank() && targetCurrency != "الكل") "_$targetCurrency" else ""
        val file = File(dir, "Invoice_${invoice.invoiceNumber}${currSuffix}.pdf")

        val result = PdfTableRenderer.renderReportToPdf(pdfModel, file)

        result.getOrNull()?.absolutePath
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

suspend fun exportReturnToPdf(
    context: Context,
    productReturn: ProductReturn,
    items: List<ProductReturnItem>,
    storeName: String
): String? = withContext(Dispatchers.IO) {
    try {
        val settings = StoreSettings(storeName = storeName)
        val pdfModel = ReturnPdfMapper.mapToPdfModel(productReturn, items, settings)
        val returnsDir = File(context.cacheDir, "shared_returns")
        if (!returnsDir.exists()) returnsDir.mkdirs()
        val file = File(returnsDir, "Return_${productReturn.returnNumber}.pdf")

        val result = PdfTableRenderer.renderReportToPdf(pdfModel, file)

        result.getOrNull()?.absolutePath
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

/**
 * Exports one or multiple selected payment receipts to a structured PDF document.
 */
suspend fun exportSelectedPaymentsToPdf(
    context: Context,
    client: Client,
    payments: List<Payment>,
    settings: StoreSettings
): String? = withContext(Dispatchers.IO) {
    if (payments.isEmpty()) return@withContext null
    try {
        val totalAmount = payments.sumOf { it.amount }
        val currency = settings.currency.ifBlank { "الريال اليمني" }
        val formattedTime = formatPdfTimestamp(System.currentTimeMillis())

        val headerMeta = listOf(
            PdfHeaderMeta("العميل", client.name),
            PdfHeaderMeta("الهاتف", client.phone.ifBlank { "غير مسجل" }),
            PdfHeaderMeta("عدد الدفعات المحددة", "${payments.size} دفعة"),
            PdfHeaderMeta("إجمالي المبلغ المسدد", "${FormatUtils.formatAmount(totalAmount)} $currency"),
            PdfHeaderMeta("تاريخ الإصدار", formattedTime)
        )

        val headerInfo = PdfHeaderInfo(
            storeName = settings.storeName.ifBlank { "المتجر المحاسبي" },
            storeSubtitle = "نظام إدارة المبيعات والحسابات",
            storePhone = settings.storePhone,
            storeAddress = settings.storeAddress,
            reportTitle = "سند استلام / إيصال دفعات مسددة",
            reportSubtitle = "إيصال سداد رسمي للمستحقات المالية",
            dateText = formattedTime,
            metaFields = headerMeta
        )

        val columns = listOf(
            PdfTableColumn("#", "#", widthWeight = 0.5f, alignment = PdfAlignment.CENTER),
            PdfTableColumn("date", "تاريخ الدفعة", widthWeight = 1.8f, alignment = PdfAlignment.CENTER),
            PdfTableColumn("voucher", "رقم السند / الحوالة", widthWeight = 1.8f, alignment = PdfAlignment.CENTER),
            PdfTableColumn("method", "طريقة الدفع", widthWeight = 1.2f, alignment = PdfAlignment.CENTER),
            PdfTableColumn("collector", "المُحصل", widthWeight = 1.5f, alignment = PdfAlignment.CENTER),
            PdfTableColumn("amount", "المبلغ المسدد", widthWeight = 2.0f, alignment = PdfAlignment.LEFT, isBold = true)
        )

        val rows = payments.mapIndexed { index, p ->
            val pDate = formatPdfTimestamp(p.date)
            val refStr = when {
                !p.voucherNumber.isNullOrBlank() -> "سند #${p.voucherNumber}"
                !p.transferNumber.isNullOrBlank() -> "حوالة #${p.transferNumber}"
                else -> "-"
            }
            PdfTableRow(
                cells = listOf(
                    PdfCell((index + 1).toString(), alignment = PdfAlignment.CENTER),
                    PdfCell(pDate, alignment = PdfAlignment.CENTER),
                    PdfCell(refStr, alignment = PdfAlignment.CENTER),
                    PdfCell(p.paymentMethod.ifBlank { "نقدي" }, alignment = PdfAlignment.CENTER),
                    PdfCell(p.collectorName ?: "-", alignment = PdfAlignment.CENTER),
                    PdfCell("${FormatUtils.formatAmount(p.amount)} ${p.currency.ifBlank { currency }}", alignment = PdfAlignment.LEFT, textColor = Color.parseColor("#059669"), isBold = true)
                )
            )
        }

        val table = PdfTable(columns = columns, rows = rows)

        val summaryItems = listOf(
            PdfSummaryItem("إجمالي عدد الدفعات المحددة", "${payments.size} دفعة"),
            PdfSummaryItem("المبلغ الإجمالي المسدد", "${FormatUtils.formatAmount(totalAmount)} $currency", isHighlighted = true, highlightColor = Color.parseColor("#ECFDF5")),
            PdfSummaryItem("الرصيد المتبقي المستحق على العميل", "${FormatUtils.formatAmount(client.balance)} $currency", isHighlighted = client.balance > 0, highlightColor = Color.parseColor("#FEF2F2"))
        )

        val reportModel = PdfReportModel(
            title = "سند استلام دفعات - ${client.name}",
            headerInfo = headerInfo,
            tables = listOf(table),
            summaryInfo = PdfSummaryInfo(title = "ملخص إيصال السداد", items = summaryItems),
            currency = currency
        )

        val exportsDir = File(context.cacheDir, "pdf_exports").apply { mkdirs() }
        val file = File(exportsDir, "Payment_Receipt_${client.name.replace(" ", "_")}_${System.currentTimeMillis()}.pdf")

        val result = PdfTableRenderer.renderReportToPdf(reportModel, file)
        result.getOrNull()?.absolutePath
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
