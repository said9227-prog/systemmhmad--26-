package com.example.util.pdf.mapper

import android.graphics.Color
import com.example.data.model.Client
import com.example.data.model.StoreSettings
import com.example.ui.screens.LedgerEntry
import com.example.util.FormatUtils
import com.example.util.pdf.model.*
import java.text.SimpleDateFormat
import java.util.*

object ClientStatementPdfMapper {

    fun mapToPdfModel(
        client: Client,
        ledgerItems: List<LedgerEntry>,
        settings: StoreSettings,
        startDate: Long? = null,
        endDate: Long? = null
    ): PdfReportModel {
        val currency = client.currency.ifBlank { settings.currency.ifBlank { "الريال اليمني" } }
        val dateFmt = SimpleDateFormat("yyyy/MM/dd", Locale("ar"))
        val timeFmt = SimpleDateFormat("yyyy/MM/dd hh:mm a", Locale("ar"))

        val storeName = settings.storeName.ifBlank { "الحكيمي للأدوية والمستلزمات الطبية" }

        val periodStr = if (startDate != null && endDate != null) {
            "من ${dateFmt.format(Date(startDate))} إلى ${dateFmt.format(Date(endDate))}"
        } else {
            "جميع المعاملات التاريخية حتى اليوم"
        }

        val balStatusText = if (client.balance > 0) "مدين (مستحق للشركة)" else if (client.balance < 0) "دائن (لصالح العميل)" else "الحساب خالص"

        val metaFields = listOf(
            PdfHeaderMeta("اسم العميل", client.name),
            PdfHeaderMeta("رقم الهاتف", client.phone.ifBlank { "غير مسجل" }),
            PdfHeaderMeta("المدينة / العنوان", client.city.ifBlank { client.address.ifBlank { "غير محدد" } }),
            PdfHeaderMeta("الرصيد الافتتاحي", "${FormatUtils.formatAmount(client.initialBalance)} ${client.initialBalanceCurrency.ifBlank { currency }} (${client.balanceType})"),
            PdfHeaderMeta("العملة المعتمدة", currency),
            PdfHeaderMeta("حالة الحساب النهائية", "${FormatUtils.formatAmount(kotlin.math.abs(client.balance))} $currency ($balStatusText)")
        )

        val headerInfo = PdfHeaderInfo(
            storeName = storeName,
            storePhone = settings.storePhone,
            storeAddress = settings.storeAddress,
            reportTitle = "📋 كشف حساب عميل تفصيلي",
            reportSubtitle = "كشف حركة حساب العميل: ${client.name}",
            periodText = "الفترة: $periodStr",
            metaFields = metaFields
        )

        val columns = listOf(
            PdfTableColumn("#", "#", widthWeight = 0.5f, alignment = PdfAlignment.CENTER),
            PdfTableColumn("date", "التاريخ والوقت", widthWeight = 1.8f, alignment = PdfAlignment.CENTER),
            PdfTableColumn("type", "العملية", widthWeight = 1.2f, alignment = PdfAlignment.CENTER),
            PdfTableColumn("label", "البيان التفصيلي للعملية", widthWeight = 3.2f, alignment = PdfAlignment.RIGHT),
            PdfTableColumn("debit", "دين (+) ($currency)", widthWeight = 1.5f, alignment = PdfAlignment.LEFT),
            PdfTableColumn("credit", "سداد (-) ($currency)", widthWeight = 1.5f, alignment = PdfAlignment.LEFT),
            PdfTableColumn("balance", "الرصيد ($currency)", widthWeight = 1.8f, alignment = PdfAlignment.LEFT, isBold = true)
        )

        val rows = ledgerItems.mapIndexed { index, entry ->
            val dateStr = try { timeFmt.format(Date(entry.date)) } catch (_: Exception) { "" }
            val itemCurr = entry.currency.ifBlank { currency }

            val labelText = if (itemCurr != currency && entry.originalAmount > 0) {
                "${entry.label} (${FormatUtils.formatAmount(entry.originalAmount)} $itemCurr - صرف: ${FormatUtils.formatPlain(entry.exchangeRate)})"
            } else {
                entry.label
            }

            val debitText = if (entry.debit > 0) FormatUtils.formatAmount(entry.debit) else "-"
            val creditText = if (entry.credit > 0) FormatUtils.formatAmount(entry.credit) else "-"

            val debitColor = if (entry.debit > 0) Color.parseColor("#DC2626") else Color.BLACK
            val creditColor = if (entry.credit > 0) Color.parseColor("#059669") else Color.BLACK

            PdfTableRow(
                cells = listOf(
                    PdfCell((index + 1).toString(), alignment = PdfAlignment.CENTER),
                    PdfCell(dateStr, alignment = PdfAlignment.CENTER),
                    PdfCell(entry.type, alignment = PdfAlignment.CENTER, isBold = true),
                    PdfCell(labelText, alignment = PdfAlignment.RIGHT),
                    PdfCell(debitText, alignment = PdfAlignment.LEFT, textColor = debitColor),
                    PdfCell(creditText, alignment = PdfAlignment.LEFT, textColor = creditColor),
                    PdfCell(FormatUtils.formatAmount(entry.runningBalance), alignment = PdfAlignment.LEFT, isBold = true)
                )
            )
        }

        val totalDebit = ledgerItems.sumOf { it.debit }
        val totalCredit = ledgerItems.sumOf { it.credit }

        val summaryItems = listOf(
            PdfSummaryItem("إجمالي الديون / المبيعات (+)", "${FormatUtils.formatAmount(totalDebit)} $currency", isHighlighted = true, highlightColor = Color.parseColor("#FEF2F2")),
            PdfSummaryItem("إجمالي السداد / الدفعات (-)", "${FormatUtils.formatAmount(totalCredit)} $currency", isHighlighted = true, highlightColor = Color.parseColor("#ECFDF5")),
            PdfSummaryItem("الرصيد المتبقي النهـائي المستحق", "${FormatUtils.formatAmount(kotlin.math.abs(client.balance))} $currency ($balStatusText)", isHighlighted = true, highlightColor = Color.parseColor("#E0F2FE"))
        )

        val table = PdfTable(
            title = "سجل حركة الحساب والمعاملات",
            columns = columns,
            rows = rows
        )

        return PdfReportModel(
            title = "كشف حساب ${client.name}",
            headerInfo = headerInfo,
            tables = listOf(table),
            summaryInfo = PdfSummaryInfo(title = "ملخص حركة الحساب", items = summaryItems),
            notes = client.notes.ifBlank { null },
            orientation = PdfOrientation.PORTRAIT,
            currency = currency
        )
    }
}
