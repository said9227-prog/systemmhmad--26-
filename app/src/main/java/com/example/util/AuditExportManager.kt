package com.example.util

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.example.data.database.AppDatabase
import com.example.data.model.AuditLog
import com.example.util.pdf.engine.PdfTableRenderer
import com.example.util.pdf.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object AuditExportManager {

    private fun getExportsDirectory(context: Context): File {
        val dir = File(context.cacheDir, "audit_exports")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun escapeCsv(text: String): String {
        return if (text.contains(",") || text.contains("\"") || text.contains("\n")) {
            "\"" + text.replace("\"", "\"\"") + "\""
        } else {
            text
        }
    }

    /**
     * Exports audit logs to CSV with UTF-8 BOM for Arabic support in Excel.
     */
    suspend fun exportToCsv(
        context: Context,
        db: AppDatabase,
        startTime: Long? = null,
        endTime: Long? = null,
        storeName: String,
        periodDescription: String
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val auditLogDao = db.auditLogDao()
            val logs = if (startTime != null && endTime != null) {
                auditLogDao.getLogsBetween(startTime, endTime)
            } else {
                auditLogDao.getAllLogs()
            }

            if (logs.isEmpty()) {
                return@withContext Result.failure(Exception("لا توجد سجلات لتصديرها."))
            }

            val exportDir = getExportsDirectory(context)
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val file = File(exportDir, "سجل_العمليات_$timeStamp.csv")

            val dateTimeFmt = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault())

            val sb = StringBuilder()
            // UTF-8 BOM
            sb.append("\uFEFF")
            sb.append("نظام $storeName - تقرير سجل عمليات النظام (Audit Log)\n")
            sb.append("تاريخ التصدير,${dateTimeFmt.format(Date())}\n")
            sb.append("الفترة,$periodDescription\n")
            sb.append("إجمالي السجلات,${logs.size}\n\n")

            // Headers
            sb.append("م,المعرف,التاريخ والوقت,نوع العملية,القسم / الجدول,التفاصيل\n")

            logs.forEachIndexed { index, log ->
                sb.append("${index + 1},")
                sb.append("${log.id},")
                sb.append("${dateTimeFmt.format(Date(log.timestamp))},")
                sb.append("${escapeCsv(log.operationType)},")
                sb.append("${escapeCsv(log.tableName)},")
                sb.append("${escapeCsv(log.details)}\n")
            }

            FileOutputStream(file).use { fos ->
                fos.write(sb.toString().toByteArray(Charsets.UTF_8))
            }

            if (file.exists() && file.length() > 0) {
                Result.success(file)
            } else {
                Result.failure(Exception("فشل إنشاء ملف التصدير CSV."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Exports audit logs to structured plain text report.
     */
    suspend fun exportToTxt(
        context: Context,
        db: AppDatabase,
        startTime: Long? = null,
        endTime: Long? = null,
        storeName: String,
        periodDescription: String
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val auditLogDao = db.auditLogDao()
            val logs = if (startTime != null && endTime != null) {
                auditLogDao.getLogsBetween(startTime, endTime)
            } else {
                auditLogDao.getAllLogs()
            }

            if (logs.isEmpty()) {
                return@withContext Result.failure(Exception("لا توجد سجلات لتصديرها."))
            }

            val exportDir = getExportsDirectory(context)
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val file = File(exportDir, "سجل_العمليات_$timeStamp.txt")

            val dateTimeFmt = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault())

            val sb = StringBuilder()
            sb.append("=================================================================\n")
            sb.append("             نظام $storeName             \n")
            sb.append("             تقرير سجل عمليات النظام (Audit Log)             \n")
            sb.append("=================================================================\n")
            sb.append("تاريخ التصدير: ${dateTimeFmt.format(Date())}\n")
            sb.append("الفترة: $periodDescription\n")
            sb.append("إجمالي السجلات: ${logs.size}\n")
            sb.append("=================================================================\n\n")

            logs.forEachIndexed { index, log ->
                sb.append("[${index + 1}] المعرف: #${log.id} | ${dateTimeFmt.format(Date(log.timestamp))}\n")
                sb.append("العملية: ${log.operationType} | القسم: ${log.tableName}\n")
                sb.append("التفاصيل: ${log.details}\n")
                sb.append("-----------------------------------------------------------------\n")
            }

            FileOutputStream(file).use { fos ->
                fos.write(sb.toString().toByteArray(Charsets.UTF_8))
            }

            if (file.exists() && file.length() > 0) {
                Result.success(file)
            } else {
                Result.failure(Exception("فشل إنشاء ملف التصدير TXT."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Exports audit logs to multi-page PDF document.
     */
    suspend fun exportToPdf(
        context: Context,
        db: AppDatabase,
        startTime: Long? = null,
        endTime: Long? = null,
        storeName: String,
        periodDescription: String
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val auditLogDao = db.auditLogDao()
            val logs = if (startTime != null && endTime != null) {
                auditLogDao.getLogsBetween(startTime, endTime)
            } else {
                auditLogDao.getAllLogs()
            }

            if (logs.isEmpty()) {
                return@withContext Result.failure(Exception("لا توجد سجلات لتصديرها."))
            }

            val exportDir = getExportsDirectory(context)
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val file = File(exportDir, "سجل_العمليات_$timeStamp.pdf")

            val dateTimeFmt = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())

            val metaFields = listOf(
                PdfHeaderMeta("الفترة التفصيلية", periodDescription),
                PdfHeaderMeta("إجمالي عدد السجلات", "${logs.size} عملية"),
                PdfHeaderMeta("تاريخ التصدير", dateTimeFmt.format(Date()))
            )

            val headerInfo = PdfHeaderInfo(
                storeName = storeName.ifBlank { "الحكيمي للأدوية والمستلزمات الطبية" },
                reportTitle = "🛡️ تقرير سجل عمليات ونشاط النظام (Audit Log)",
                reportSubtitle = "تتبع عمليات النظام والأمان والحركات المنفذة",
                metaFields = metaFields
            )

            val columns = listOf(
                PdfTableColumn("#", "#", widthWeight = 0.6f, alignment = PdfAlignment.CENTER),
                PdfTableColumn("date", "التاريخ والوقت", widthWeight = 1.8f, alignment = PdfAlignment.CENTER),
                PdfTableColumn("type", "نوع العملية", widthWeight = 1.5f, alignment = PdfAlignment.CENTER, isBold = true),
                PdfTableColumn("table", "الجدول / القسم", widthWeight = 1.5f, alignment = PdfAlignment.CENTER),
                PdfTableColumn("details", "تفاصيل العملية والبيانات", widthWeight = 4.0f, alignment = PdfAlignment.RIGHT)
            )

            val rows = logs.mapIndexed { index, log ->
                PdfTableRow(
                    cells = listOf(
                        PdfCell("#${log.id}", alignment = PdfAlignment.CENTER),
                        PdfCell(dateTimeFmt.format(Date(log.timestamp)), alignment = PdfAlignment.CENTER),
                        PdfCell(log.operationType, alignment = PdfAlignment.CENTER, isBold = true),
                        PdfCell(log.tableName, alignment = PdfAlignment.CENTER),
                        PdfCell(log.details, alignment = PdfAlignment.RIGHT)
                    )
                )
            }

            val table = PdfTable(
                title = "سجل التدقيق والأمان الحسابي",
                columns = columns,
                rows = rows
            )

            val reportModel = PdfReportModel(
                title = "سجل عمليات النظام",
                headerInfo = headerInfo,
                tables = listOf(table),
                summaryInfo = PdfSummaryInfo("إحصائيات التدقيق", listOf(PdfSummaryItem("إجمالي العمليات الموثقة", "${logs.size} سجل")))
            )

            PdfTableRenderer.renderReportToPdf(reportModel, file)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
