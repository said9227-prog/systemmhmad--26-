package com.example.util.pdf.model

import android.graphics.Color
import android.graphics.Paint

enum class PdfOrientation {
    PORTRAIT,
    LANDSCAPE
}

/**
 * Alignment for cells inside table
 */
enum class PdfAlignment {
    LEFT,
    CENTER,
    RIGHT
}

/**
 * Represents a single cell inside a PDF table
 */
data class PdfCell(
    val text: String,
    val alignment: PdfAlignment = PdfAlignment.RIGHT,
    val textColor: Int = Color.BLACK,
    val backgroundColor: Int? = null,
    val isBold: Boolean = false,
    val textSizeSp: Float? = null,
    val maxLines: Int = 10
)

/**
 * Column definition for a PDF table
 */
data class PdfTableColumn(
    val key: String,
    val title: String,
    val widthWeight: Float = 1.0f,
    val alignment: PdfAlignment = PdfAlignment.RIGHT,
    val isBold: Boolean = true
)

/**
 * A single row inside a PDF table
 */
data class PdfTableRow(
    val cells: List<PdfCell>,
    val backgroundColor: Int? = null,
    val isHeader: Boolean = false,
    val customMinHeightDp: Float? = null
)

/**
 * Represents a complete structured table in a PDF report
 */
data class PdfTable(
    val title: String? = null,
    val columns: List<PdfTableColumn>,
    val rows: List<PdfTableRow>,
    val headerBackgroundColor: Int = Color.parseColor("#1E3A8A"), // Deep Navy
    val headerTextColor: Int = Color.WHITE,
    val zebraBackgroundColor: Int = Color.parseColor("#F8FAFC"), // Subtle slate
    val borderColor: Int = Color.parseColor("#CBD5E1"), // Light border
    val summaryRows: List<PdfTableRow> = emptyList()
)

/**
 * Metadata key-value pair for report header block
 */
data class PdfHeaderMeta(
    val label: String,
    val value: String
)

/**
 * Header block at the top of every PDF document
 */
data class PdfHeaderInfo(
    val storeName: String,
    val storeSubtitle: String? = null,
    val storePhone: String? = null,
    val storeAddress: String? = null,
    val reportTitle: String,
    val reportSubtitle: String? = null,
    val dateText: String? = null,
    val periodText: String? = null,
    val metaFields: List<PdfHeaderMeta> = emptyList()
)

/**
 * Summary card/rows at the end of the report
 */
data class PdfSummaryItem(
    val label: String,
    val value: String,
    val isHighlighted: Boolean = false,
    val highlightColor: Int? = null
)

data class PdfSummaryInfo(
    val title: String = "ملخص التقرير",
    val items: List<PdfSummaryItem>
)

/**
 * Unified Root Model representing a complete PDF document
 */
data class PdfReportModel(
    val title: String,
    val headerInfo: PdfHeaderInfo,
    val tables: List<PdfTable>,
    val summaryInfo: PdfSummaryInfo? = null,
    val notes: String? = null,
    val footerText: String? = null,
    val orientation: PdfOrientation = PdfOrientation.PORTRAIT,
    val currency: String = "الريال اليمني",
    val emptyDataMessage: String? = "لا توجد بيانات مسجلة ضمن الفترة المحددة."
)
