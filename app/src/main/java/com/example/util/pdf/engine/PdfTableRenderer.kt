package com.example.util.pdf.engine

import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.example.util.pdf.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * Core Engine for rendering structured table-based PDF documents in Android.
 * Fully supports A4 Portrait/Landscape, Arabic RTL text wrapping, custom cell borders,
 * auto page-breaking, repeating headers, and multi-currency formatting.
 */
object PdfTableRenderer {

    private const val A4_PORTRAIT_WIDTH = 595f
    private const val A4_PORTRAIT_HEIGHT = 842f

    private const val A4_LANDSCAPE_WIDTH = 842f
    private const val A4_LANDSCAPE_HEIGHT = 595f

    private const val MARGIN_X = 36f
    private const val MARGIN_Y = 36f
    private const val FOOTER_HEIGHT = 30f

    /**
     * Renders a [PdfReportModel] to a PDF file asynchronously.
     */
    suspend fun renderReportToPdf(
        reportModel: PdfReportModel,
        outputFile: File
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val isLandscape = reportModel.orientation == PdfOrientation.LANDSCAPE
            val pageWidth = if (isLandscape) A4_LANDSCAPE_WIDTH else A4_PORTRAIT_WIDTH
            val pageHeight = if (isLandscape) A4_LANDSCAPE_HEIGHT else A4_PORTRAIT_HEIGHT
            val printableWidth = pageWidth - (MARGIN_X * 2)
            val maxY = pageHeight - MARGIN_Y - FOOTER_HEIGHT

            val pdfDocument = PdfDocument()
            var pageNumber = 1
            var pageInfo = PdfDocument.PageInfo.Builder(pageWidth.toInt(), pageHeight.toInt(), pageNumber).create()
            var currentPage = pdfDocument.startPage(pageInfo)
            var canvas = currentPage.canvas

            val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = 9f
            }

            val headerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = 14f
                isFakeBoldText = true
            }

            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#CBD5E1")
                style = Paint.Style.STROKE
                strokeWidth = 0.75f
            }

            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
            }

            var currentY = MARGIN_Y

            // Helper: Draw Header on Top of Page
            fun drawDocumentHeader(): Float {
                var y = MARGIN_Y

                // Store Title
                val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#1E3A8A")
                    textSize = 16f
                    isFakeBoldText = true
                    textAlign = Paint.Align.CENTER
                }

                val storeTitle = reportModel.headerInfo.storeName.ifBlank { "الحكيمي للأدوية والمستلزمات الطبية" }
                canvas.drawText(storeTitle, pageWidth / 2f, y + 14f, titlePaint)
                y += 22f

                // Store Subtitle / Phone
                val subText = buildString {
                    reportModel.headerInfo.storeSubtitle?.let { if (it.isNotBlank()) append(it) }
                    reportModel.headerInfo.storePhone?.let { if (it.isNotBlank()) append(" | هاتف: ").append(it) }
                    reportModel.headerInfo.storeAddress?.let { if (it.isNotBlank()) append(" | العنوان: ").append(it) }
                }
                if (subText.isNotBlank()) {
                    val subPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.parseColor("#475569")
                        textSize = 9f
                        textAlign = Paint.Align.CENTER
                    }
                    canvas.drawText(subText, pageWidth / 2f, y + 9f, subPaint)
                    y += 15f
                }

                // Divider Line
                borderPaint.color = Color.parseColor("#3B82F6")
                borderPaint.strokeWidth = 1.5f
                canvas.drawLine(MARGIN_X, y + 4f, pageWidth - MARGIN_X, y + 4f, borderPaint)
                borderPaint.color = Color.parseColor("#CBD5E1")
                borderPaint.strokeWidth = 0.75f
                y += 12f

                // Report Title Card
                val reportTitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#0F172A")
                    textSize = 13f
                    isFakeBoldText = true
                    textAlign = Paint.Align.RIGHT
                }
                canvas.drawText(reportModel.headerInfo.reportTitle, pageWidth - MARGIN_X, y + 12f, reportTitlePaint)

                // Date/Period Text (Left aligned)
                val periodText = reportModel.headerInfo.periodText ?: reportModel.headerInfo.dateText ?: ""
                if (periodText.isNotBlank()) {
                    val periodPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.parseColor("#64748B")
                        textSize = 9f
                        textAlign = Paint.Align.LEFT
                    }
                    canvas.drawText(periodText, MARGIN_X, y + 12f, periodPaint)
                }
                y += 20f

                // Metadata Fields (2-Column Grid)
                if (reportModel.headerInfo.metaFields.isNotEmpty()) {
                    val metaPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.parseColor("#1E293B")
                        textSize = 9f
                        textAlign = Paint.Align.RIGHT
                    }
                    fillPaint.color = Color.parseColor("#F1F5F9")
                    val metaBoxRect = RectF(MARGIN_X, y, pageWidth - MARGIN_X, y + (reportModel.headerInfo.metaFields.size + 1) / 2 * 16f + 8f)
                    canvas.drawRoundRect(metaBoxRect, 4f, 4f, fillPaint)
                    canvas.drawRoundRect(metaBoxRect, 4f, 4f, borderPaint)

                    var metaY = y + 14f
                    val halfWidth = printableWidth / 2f
                    reportModel.headerInfo.metaFields.chunked(2).forEach { pair ->
                        // Right Column
                        val left1 = pair[0]
                        canvas.drawText("${left1.label}: ${left1.value}", pageWidth - MARGIN_X - 8f, metaY, metaPaint)

                        // Left Column
                        if (pair.size > 1) {
                            val left2 = pair[1]
                            canvas.drawText("${left2.label}: ${left2.value}", pageWidth - MARGIN_X - halfWidth - 8f, metaY, metaPaint)
                        }
                        metaY += 16f
                    }
                    y = metaBoxRect.bottom + 10f
                } else {
                    y += 6f
                }

                return y
            }

            // Helper: Draw Footer on Every Page
            fun drawFooter(pNum: Int, totalPages: Int = 0) {
                val footerY = pageHeight - MARGIN_Y + 12f
                val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#94A3B8")
                    textSize = 8f
                }

                // Top divider
                borderPaint.color = Color.parseColor("#E2E8F0")
                borderPaint.strokeWidth = 0.5f
                canvas.drawLine(MARGIN_X, pageHeight - MARGIN_Y - 4f, pageWidth - MARGIN_X, pageHeight - MARGIN_Y - 4f, borderPaint)

                // Left: Timestamp
                val nowStr = SimpleDateFormat("yyyy/MM/dd | hh:mm a", Locale("ar")).format(Date())
                footerPaint.textAlign = Paint.Align.LEFT
                canvas.drawText("تم التصدير: $nowStr", MARGIN_X, footerY, footerPaint)

                // Center: Store Branding
                footerPaint.textAlign = Paint.Align.CENTER
                val brandName = reportModel.headerInfo.storeName.ifBlank { "الحكيمي للأدوية والمستلزمات الطبية" }
                canvas.drawText("نظام $brandName المحاسبي", pageWidth / 2f, footerY, footerPaint)

                // Right: Page Number
                footerPaint.textAlign = Paint.Align.RIGHT
                val pageStr = if (totalPages > 0) "صفحة $pNum من $totalPages" else "صفحة $pNum"
                canvas.drawText(pageStr, pageWidth - MARGIN_X, footerY, footerPaint)
            }

            // Start First Page
            currentY = drawDocumentHeader()

            // Check if all tables are empty
            val hasData = reportModel.tables.any { it.rows.isNotEmpty() }

            if (!hasData) {
                // Render Empty Data Box
                fillPaint.color = Color.parseColor("#FEF2F2")
                borderPaint.color = Color.parseColor("#FCA5A5")
                val emptyRect = RectF(MARGIN_X, currentY + 10f, pageWidth - MARGIN_X, currentY + 70f)
                canvas.drawRoundRect(emptyRect, 6f, 6f, fillPaint)
                canvas.drawRoundRect(emptyRect, 6f, 6f, borderPaint)

                val emptyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#991B1B")
                    textSize = 11f
                    isFakeBoldText = true
                    textAlign = Paint.Align.CENTER
                }
                val msg = reportModel.emptyDataMessage ?: "لا توجد بيانات مسجلة ضمن الفترة المحددة."
                canvas.drawText(msg, pageWidth / 2f, currentY + 45f, emptyPaint)
                currentY += 80f
            } else {
                // Iterate over tables
                for (table in reportModel.tables) {
                    if (table.rows.isEmpty()) continue

                    // Optional Table Title
                    if (!table.title.isNullOrBlank()) {
                        if (currentY + 25f > maxY) {
                            drawFooter(pageNumber)
                            pdfDocument.finishPage(currentPage)
                            pageNumber++
                            pageInfo = PdfDocument.PageInfo.Builder(pageWidth.toInt(), pageHeight.toInt(), pageNumber).create()
                            currentPage = pdfDocument.startPage(pageInfo)
                            canvas = currentPage.canvas
                            currentY = drawDocumentHeader()
                        }

                        val tblTitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = Color.parseColor("#1E3A8A")
                            textSize = 11f
                            isFakeBoldText = true
                            textAlign = Paint.Align.RIGHT
                        }
                        canvas.drawText("■ ${table.title}", pageWidth - MARGIN_X, currentY + 14f, tblTitlePaint)
                        currentY += 22f
                    }

                    // Calculate Column Widths
                    val totalWeight = table.columns.sumOf { it.widthWeight.toDouble() }.toFloat()
                    val colWidths = table.columns.map { (it.widthWeight / totalWeight) * printableWidth }

                    // Column X Positions (RTL Layout: right to left)
                    // Col 0 is at right, Col N is at left
                    val colXPositions = FloatArray(table.columns.size)
                    var currX = pageWidth - MARGIN_X
                    for (i in table.columns.indices) {
                        colXPositions[i] = currX - colWidths[i]
                        currX -= colWidths[i]
                    }

                    // Helper: Create Cell StaticLayout for multiline Arabic text wrapping
                    fun createCellLayout(text: String, width: Float, align: PdfAlignment, isBold: Boolean, fontSize: Float, textColor: Int): StaticLayout {
                        textPaint.apply {
                            this.color = textColor
                            this.textSize = fontSize
                            this.isFakeBoldText = isBold
                        }
                        val alignment = when (align) {
                            PdfAlignment.LEFT -> Layout.Alignment.ALIGN_NORMAL
                            PdfAlignment.CENTER -> Layout.Alignment.ALIGN_CENTER
                            PdfAlignment.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
                        }
                        val innerWidth = (width - 10f).coerceAtLeast(10f).toInt()
                        return StaticLayout.Builder.obtain(text, 0, text.length, textPaint, innerWidth)
                            .setAlignment(alignment)
                            .setLineSpacing(0f, 1.05f)
                            .setIncludePad(false)
                            .build()
                    }

                    // Helper: Draw Table Header Row
                    fun drawTableHeaderRow(y: Float): Float {
                        textPaint.apply {
                            color = table.headerTextColor
                            textSize = 9.5f
                            isFakeBoldText = true
                        }

                        // Calculate Header Height
                        var headerHeight = 22f
                        val headerLayouts = mutableListOf<StaticLayout>()
                        for (i in table.columns.indices) {
                            val col = table.columns[i]
                            val layout = createCellLayout(col.title, colWidths[i], col.alignment, true, 9.5f, table.headerTextColor)
                            headerLayouts.add(layout)
                            val layoutHeight = layout.height + 10f
                            if (layoutHeight > headerHeight) {
                                headerHeight = layoutHeight
                            }
                        }

                        // Fill Header Background
                        fillPaint.color = table.headerBackgroundColor
                        canvas.drawRect(MARGIN_X, y, pageWidth - MARGIN_X, y + headerHeight, fillPaint)

                        // Draw Header Text & Vertical Lines
                        borderPaint.color = table.borderColor
                        borderPaint.strokeWidth = 0.75f

                        for (i in table.columns.indices) {
                            val layout = headerLayouts[i]
                            val cellX = colXPositions[i]
                            val cellW = colWidths[i]

                            canvas.save()
                            canvas.translate(cellX + 5f, y + (headerHeight - layout.height) / 2f)
                            layout.draw(canvas)
                            canvas.restore()

                            // Vertical Border Line
                            canvas.drawLine(cellX, y, cellX, y + headerHeight, borderPaint)
                        }
                        // Outer Right & Left Vertical Lines & Horizontal Line
                        canvas.drawLine(pageWidth - MARGIN_X, y, pageWidth - MARGIN_X, y + headerHeight, borderPaint)
                        canvas.drawLine(MARGIN_X, y + headerHeight, pageWidth - MARGIN_X, y + headerHeight, borderPaint)

                        return headerHeight
                    }

                    // Draw Initial Table Header
                    if (currentY + 30f > maxY) {
                        drawFooter(pageNumber)
                        pdfDocument.finishPage(currentPage)
                        pageNumber++
                        pageInfo = PdfDocument.PageInfo.Builder(pageWidth.toInt(), pageHeight.toInt(), pageNumber).create()
                        currentPage = pdfDocument.startPage(pageInfo)
                        canvas = currentPage.canvas
                        currentY = drawDocumentHeader()
                    }

                    var headerRowHeight = drawTableHeaderRow(currentY)
                    currentY += headerRowHeight

                    // Render Table Data Rows
                    for (rowIndex in table.rows.indices) {
                        val row = table.rows[rowIndex]

                        // Create cell layouts and measure row height
                        val cellLayouts = mutableListOf<StaticLayout>()
                        var rowHeight = row.customMinHeightDp ?: 18f

                        for (colIndex in table.columns.indices) {
                            val cell = row.cells.getOrNull(colIndex) ?: PdfCell("")
                            val col = table.columns[colIndex]
                            val fontSize = cell.textSizeSp ?: 8.5f
                            val align = if (cell.alignment != PdfAlignment.RIGHT) cell.alignment else col.alignment
                            val layout = createCellLayout(cell.text, colWidths[colIndex], align, cell.isBold, fontSize, cell.textColor)
                            cellLayouts.add(layout)

                            val cellH = layout.height + 8f
                            if (cellH > rowHeight) {
                                rowHeight = cellH
                            }
                        }

                        // Check Page Break
                        if (currentY + rowHeight > maxY) {
                            // Draw Footer and Finish Page
                            drawFooter(pageNumber)
                            pdfDocument.finishPage(currentPage)

                            // Start New Page
                            pageNumber++
                            pageInfo = PdfDocument.PageInfo.Builder(pageWidth.toInt(), pageHeight.toInt(), pageNumber).create()
                            currentPage = pdfDocument.startPage(pageInfo)
                            canvas = currentPage.canvas

                            // Draw Document Header & Repeat Table Header
                            currentY = drawDocumentHeader()
                            val newHeaderH = drawTableHeaderRow(currentY)
                            currentY += newHeaderH
                        }

                        // Determine Row Background Color
                        val rowBgColor = row.backgroundColor ?: if (rowIndex % 2 == 1) table.zebraBackgroundColor else Color.WHITE
                        fillPaint.color = rowBgColor
                        canvas.drawRect(MARGIN_X, currentY, pageWidth - MARGIN_X, currentY + rowHeight, fillPaint)

                        // Draw Cells
                        borderPaint.color = table.borderColor
                        borderPaint.strokeWidth = 0.5f

                        for (colIndex in table.columns.indices) {
                            val layout = cellLayouts[colIndex]
                            val cellX = colXPositions[colIndex]

                            canvas.save()
                            canvas.translate(cellX + 5f, currentY + (rowHeight - layout.height) / 2f)
                            layout.draw(canvas)
                            canvas.restore()

                            // Vertical Grid Line
                            canvas.drawLine(cellX, currentY, cellX, currentY + rowHeight, borderPaint)
                        }
                        // Rightmost Border Line & Horizontal Bottom Line
                        canvas.drawLine(pageWidth - MARGIN_X, currentY, pageWidth - MARGIN_X, currentY + rowHeight, borderPaint)
                        canvas.drawLine(MARGIN_X, currentY + rowHeight, pageWidth - MARGIN_X, currentY + rowHeight, borderPaint)

                        currentY += rowHeight
                    }

                    // Render Table Summary Rows (if any)
                    if (table.summaryRows.isNotEmpty()) {
                        for (sumRow in table.summaryRows) {
                            var sumRowH = 20f
                            val sumLayouts = mutableListOf<StaticLayout>()
                            for (colIndex in table.columns.indices) {
                                val cell = sumRow.cells.getOrNull(colIndex) ?: PdfCell("")
                                val col = table.columns[colIndex]
                                val layout = createCellLayout(cell.text, colWidths[colIndex], cell.alignment, true, 9f, cell.textColor)
                                sumLayouts.add(layout)
                                if (layout.height + 8f > sumRowH) {
                                    sumRowH = layout.height + 8f
                                }
                            }

                            if (currentY + sumRowH > maxY) {
                                drawFooter(pageNumber)
                                pdfDocument.finishPage(currentPage)
                                pageNumber++
                                pageInfo = PdfDocument.PageInfo.Builder(pageWidth.toInt(), pageHeight.toInt(), pageNumber).create()
                                currentPage = pdfDocument.startPage(pageInfo)
                                canvas = currentPage.canvas
                                currentY = drawDocumentHeader()
                            }

                            val sumBg = sumRow.backgroundColor ?: Color.parseColor("#E2E8F0")
                            fillPaint.color = sumBg
                            canvas.drawRect(MARGIN_X, currentY, pageWidth - MARGIN_X, currentY + sumRowH, fillPaint)

                            borderPaint.color = table.borderColor
                            borderPaint.strokeWidth = 0.75f

                            for (colIndex in table.columns.indices) {
                                val layout = sumLayouts[colIndex]
                                val cellX = colXPositions[colIndex]

                                canvas.save()
                                canvas.translate(cellX + 5f, currentY + (sumRowH - layout.height) / 2f)
                                layout.draw(canvas)
                                canvas.restore()

                                canvas.drawLine(cellX, currentY, cellX, currentY + sumRowH, borderPaint)
                            }
                            canvas.drawLine(pageWidth - MARGIN_X, currentY, pageWidth - MARGIN_X, currentY + sumRowH, borderPaint)
                            canvas.drawLine(MARGIN_X, currentY + sumRowH, pageWidth - MARGIN_X, currentY + sumRowH, borderPaint)

                            currentY += sumRowH
                        }
                    }

                    currentY += 15f
                }
            }

            // Summary Card Info at Bottom of Document
            reportModel.summaryInfo?.let { summary ->
                if (summary.items.isNotEmpty()) {
                    val summaryItemHeight = 18f
                    val totalSummaryH = 26f + (summary.items.size * summaryItemHeight)

                    if (currentY + totalSummaryH > maxY) {
                        drawFooter(pageNumber)
                        pdfDocument.finishPage(currentPage)
                        pageNumber++
                        pageInfo = PdfDocument.PageInfo.Builder(pageWidth.toInt(), pageHeight.toInt(), pageNumber).create()
                        currentPage = pdfDocument.startPage(pageInfo)
                        canvas = currentPage.canvas
                        currentY = drawDocumentHeader()
                    }

                    fillPaint.color = Color.parseColor("#F8FAFC")
                    borderPaint.color = Color.parseColor("#CBD5E1")
                    borderPaint.strokeWidth = 1f

                    val cardRect = RectF(MARGIN_X, currentY, pageWidth - MARGIN_X, currentY + totalSummaryH)
                    canvas.drawRoundRect(cardRect, 6f, 6f, fillPaint)
                    canvas.drawRoundRect(cardRect, 6f, 6f, borderPaint)

                    val sumTitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.parseColor("#1E3A8A")
                        textSize = 10.5f
                        isFakeBoldText = true
                        textAlign = Paint.Align.RIGHT
                    }
                    canvas.drawText("📊 ${summary.title}", pageWidth - MARGIN_X - 12f, currentY + 18f, sumTitlePaint)

                    var sumY = currentY + 28f
                    val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.parseColor("#334155")
                        textSize = 9.5f
                        textAlign = Paint.Align.RIGHT
                    }
                    val valuePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.parseColor("#0F172A")
                        textSize = 9.5f
                        isFakeBoldText = true
                        textAlign = Paint.Align.LEFT
                    }

                    for (item in summary.items) {
                        if (item.isHighlighted) {
                            val highlightBg = item.highlightColor ?: Color.parseColor("#E0F2FE")
                            fillPaint.color = highlightBg
                            val hRect = RectF(MARGIN_X + 6f, sumY - 12f, pageWidth - MARGIN_X - 6f, sumY + 4f)
                            canvas.drawRoundRect(hRect, 3f, 3f, fillPaint)
                        }

                        canvas.drawText("${item.label}:", pageWidth - MARGIN_X - 12f, sumY, labelPaint)
                        canvas.drawText(item.value, MARGIN_X + 12f, sumY, valuePaint)
                        sumY += summaryItemHeight
                    }

                    currentY = cardRect.bottom + 15f
                }
            }

            // Notes Block
            if (!reportModel.notes.isNullOrBlank()) {
                val notesText = "ملاحظات: ${reportModel.notes}"
                val notesLayout = StaticLayout.Builder.obtain(notesText, 0, notesText.length, textPaint.apply {
                    textSize = 8.5f
                    color = Color.parseColor("#475569")
                }, printableWidth.toInt())
                .setAlignment(Layout.Alignment.ALIGN_OPPOSITE)
                .build()

                if (currentY + notesLayout.height + 10f > maxY) {
                    drawFooter(pageNumber)
                    pdfDocument.finishPage(currentPage)
                    pageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(pageWidth.toInt(), pageHeight.toInt(), pageNumber).create()
                    currentPage = pdfDocument.startPage(pageInfo)
                    canvas = currentPage.canvas
                    currentY = drawDocumentHeader()
                }

                canvas.save()
                canvas.translate(MARGIN_X, currentY)
                notesLayout.draw(canvas)
                canvas.restore()

                currentY += notesLayout.height + 15f
            }

            // Draw Final Page Footer
            drawFooter(pageNumber)
            pdfDocument.finishPage(currentPage)

            // Save PDF File
            FileOutputStream(outputFile).use { fos ->
                pdfDocument.writeTo(fos)
            }
            pdfDocument.close()

            if (outputFile.exists() && outputFile.length() > 0) {
                Result.success(outputFile)
            } else {
                Result.failure(Exception("فشل حفظ ملف PDF."))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
}
