package com.example.util.pdf.mapper

import android.graphics.Color
import com.example.data.model.ProductReturn
import com.example.data.model.ProductReturnItem
import com.example.data.model.StoreSettings
import com.example.util.FormatUtils
import com.example.util.pdf.model.*
import java.text.SimpleDateFormat
import java.util.*

object ReturnPdfMapper {

    fun mapToPdfModel(
        productReturn: ProductReturn,
        items: List<ProductReturnItem>,
        settings: StoreSettings
    ): PdfReportModel {
        val currency = productReturn.currency.ifBlank { settings.currency.ifBlank { "الريال اليمني" } }
        val dateFmt = SimpleDateFormat("yyyy/MM/dd | hh:mm a", Locale("ar"))
        val formattedDate = dateFmt.format(Date(productReturn.date))

        val storeName = settings.storeName.ifBlank { "الحكيمي للأدوية والمستلزمات الطبية" }
        val isCustomer = productReturn.type == "CUSTOMER"

        val partyLabel = if (isCustomer) "اسم العميل" else "اسم المورد"
        val partyName = if (isCustomer) productReturn.clientName else productReturn.supplierName
        val returnTitle = if (isCustomer) "سند مرتجع مبيعات" else "سند مرتجع مشتريات"

        val metaFields = mutableListOf(
            PdfHeaderMeta("رقم المرتجع", "#${productReturn.returnNumber}"),
            PdfHeaderMeta("التاريخ والوقت", formattedDate),
            PdfHeaderMeta(partyLabel, partyName.ifBlank { "غير مسمى" }),
            PdfHeaderMeta("طريقة التسوية", productReturn.settlementType),
            PdfHeaderMeta("العملة المعتمدة", currency)
        )

        if (productReturn.invoiceNumber.isNotBlank()) {
            metaFields.add(PdfHeaderMeta("الفاتورة الأصلية", "#${productReturn.invoiceNumber}"))
        }

        val headerInfo = PdfHeaderInfo(
            storeName = storeName,
            storePhone = settings.storePhone,
            storeAddress = settings.storeAddress,
            reportTitle = "🔄 $returnTitle",
            reportSubtitle = "مرتجع رقم #${productReturn.returnNumber}",
            metaFields = metaFields
        )

        val columns = listOf(
            PdfTableColumn("#", "#", widthWeight = 0.5f, alignment = PdfAlignment.CENTER),
            PdfTableColumn("name", "اسم الصنف المرتجع", widthWeight = 3.5f, alignment = PdfAlignment.RIGHT),
            PdfTableColumn("unit", "الوحدة", widthWeight = 1.0f, alignment = PdfAlignment.CENTER),
            PdfTableColumn("qty", "الكمية المرتجعة", widthWeight = 1.2f, alignment = PdfAlignment.CENTER),
            PdfTableColumn("price", "سعر الوحدة ($currency)", widthWeight = 1.5f, alignment = PdfAlignment.LEFT),
            PdfTableColumn("condition", "حالة الصنف", widthWeight = 1.3f, alignment = PdfAlignment.CENTER),
            PdfTableColumn("total", "القيمة الإجمالية ($currency)", widthWeight = 1.8f, alignment = PdfAlignment.LEFT, isBold = true)
        )

        val rows = items.mapIndexed { index, item ->
            PdfTableRow(
                cells = listOf(
                    PdfCell((index + 1).toString(), alignment = PdfAlignment.CENTER),
                    PdfCell(item.itemName, alignment = PdfAlignment.RIGHT),
                    PdfCell(item.unit.ifBlank { "قطعة" }, alignment = PdfAlignment.CENTER),
                    PdfCell(item.quantity.toString(), alignment = PdfAlignment.CENTER),
                    PdfCell(FormatUtils.formatAmount(item.unitPrice), alignment = PdfAlignment.LEFT),
                    PdfCell(item.itemCondition.ifBlank { "صالح" }, alignment = PdfAlignment.CENTER),
                    PdfCell(FormatUtils.formatAmount(item.totalPrice), alignment = PdfAlignment.LEFT, isBold = true)
                )
            )
        }

        val summaryItems = listOf(
            PdfSummaryItem("عدد الأصناف المرتجعة", "${items.size} صنف"),
            PdfSummaryItem("إجمالي عدد القطع المرتجعة", "${items.sumOf { it.quantity }} قطعة"),
            PdfSummaryItem("إجمالي قيمة المرتجع النهائية", "${FormatUtils.formatAmount(productReturn.totalAmount)} $currency", isHighlighted = true, highlightColor = Color.parseColor("#E0F2FE"))
        )

        val table = PdfTable(
            title = "جدول الأصناف المرتجعة",
            columns = columns,
            rows = rows
        )

        val fullNotes = buildString {
            if (productReturn.reason.isNotBlank()) append("السبب: ").append(productReturn.reason)
            if (productReturn.notes.isNotBlank()) {
                if (isNotEmpty()) append(" | ")
                append("ملاحظات: ").append(productReturn.notes)
            }
        }

        return PdfReportModel(
            title = "مرتجع رقم #${productReturn.returnNumber}",
            headerInfo = headerInfo,
            tables = listOf(table),
            summaryInfo = PdfSummaryInfo(title = "الملخص المالي للمرتجع", items = summaryItems),
            notes = fullNotes.ifBlank { null },
            orientation = PdfOrientation.PORTRAIT,
            currency = currency
        )
    }
}
