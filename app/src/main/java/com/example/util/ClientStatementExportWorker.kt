package com.example.util

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.database.AppDatabase
import com.example.data.model.AuditLog
import com.example.data.model.StoreSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ClientStatementExportWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getDatabase(context)
            val settings = db.storeSettingsDao().getSettings() ?: StoreSettings()

            val prefs = context.getSharedPreferences("pdf_backup_prefs", Context.MODE_PRIVATE)
            val isEnabled = prefs.getBoolean("is_auto_pdf_backup_enabled", settings.isAutoPdfBackupEnabled)
            if (!isEnabled) {
                return@withContext Result.success()
            }

            val clients = db.clientDao().getAllClients()
            val invoices = db.invoiceDao().getAllInvoices()
            val payments = db.paymentDao().getAllPayments()

            val file = exportAllClientsStatementToPdf(
                context = context,
                clientsList = clients,
                invoicesList = invoices,
                paymentsList = payments,
                settings = settings
            )

            if (file != null && file.exists()) {
                db.auditLogDao().insertLog(
                    AuditLog(
                        operationType = "تصدير تلقائي مجدول",
                        tableName = "كشوفات العملاء (PDF)",
                        details = "تم حفظ كشوفات العملاء في مجلد Downloads: ${file.name}"
                    )
                )
                BackupNotificationManager.showSuccess(
                    context = context,
                    title = "تم حفظ كشوفات العملاء تلقائياً 📄",
                    details = "تم تصدير كشوفات جميع العملاء (PDF) بنجاح وحفظها في مجلد Downloads: ${file.name}"
                )
                Result.success()
            } else {
                if (runAttemptCount < 3) Result.retry() else Result.failure()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
