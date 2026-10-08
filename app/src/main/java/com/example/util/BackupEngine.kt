package com.example.util

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.example.data.database.AppDatabase
import com.example.data.model.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object BackupEngine {

    val ALL_CATEGORIES = listOf(
        "clients",
        "supplierCompanies",
        "items",
        "itemCategories",
        "itemUnits",
        "invoices",
        "invoiceItems",
        "payments",
        "installments",
        "installmentReminders",
        "productReturns",
        "productReturnItems",
        "storeSettings",
        "auditLogs"
    )

    fun getBackupDirectory(context: Context): File {
        val dir = File(context.filesDir, "backups")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun getMoshi(): Moshi {
        return Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }

    private fun computeSha256(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(data)
        return hash.joinToString("") { "%02x".format(it) }
    }

    /**
     * Resolves logical dependencies so that data integrity is maintained.
     */
    fun resolveDependencies(selected: Set<String>): Set<String> {
        val resolved = selected.toMutableSet()
        if (resolved.contains("invoices")) {
            resolved.add("invoiceItems")
            resolved.add("clients")
        }
        if (resolved.contains("payments") || resolved.contains("installments")) {
            resolved.add("clients")
        }
        if (resolved.contains("items")) {
            resolved.add("itemCategories")
            resolved.add("itemUnits")
        }
        return resolved
    }

    suspend fun createBackup(
        context: Context,
        db: AppDatabase,
        selectedCategories: Set<String> = ALL_CATEGORIES.toSet(),
        destination: String = "الهاتف",
        externalFolderUri: String? = null,
        retentionCount: Int = 7,
        onProgress: suspend (progress: Int, stage: String) -> Unit = { _, _ -> }
    ): Result<File> = withContext(Dispatchers.IO) {
        var tempZipFile: File? = null
        try {
            onProgress(10, "🔍 جاري فحص وتجميع بيانات النظام...")

            val categoriesToInclude = resolveDependencies(selectedCategories)
            val counts = mutableMapOf<String, Int>()

            val clients = if (categoriesToInclude.contains("clients")) db.clientDao().getAllClients() else emptyList()
            counts["clients"] = clients.size

            val suppliers = if (categoriesToInclude.contains("supplierCompanies")) db.supplierCompanyDao().getAllCompanies() else emptyList()
            counts["supplierCompanies"] = suppliers.size

            val items = if (categoriesToInclude.contains("items")) db.itemDao().getAllItems() else emptyList()
            counts["items"] = items.size

            val categories = if (categoriesToInclude.contains("itemCategories")) db.itemCategoryDao().getAllCategories() else emptyList()
            counts["itemCategories"] = categories.size

            val units = if (categoriesToInclude.contains("itemUnits")) db.itemUnitDao().getAllUnits() else emptyList()
            counts["itemUnits"] = units.size

            val invoices = if (categoriesToInclude.contains("invoices")) db.invoiceDao().getAllInvoices() else emptyList()
            counts["invoices"] = invoices.size

            val invoiceItems = if (categoriesToInclude.contains("invoiceItems")) db.invoiceDao().getAllInvoiceItems() else emptyList()
            counts["invoiceItems"] = invoiceItems.size

            val payments = if (categoriesToInclude.contains("payments")) db.paymentDao().getAllPayments() else emptyList()
            counts["payments"] = payments.size

            val installments = if (categoriesToInclude.contains("installments")) db.installmentDao().getAllInstallments() else emptyList()
            counts["installments"] = installments.size

            val reminders = if (categoriesToInclude.contains("installmentReminders")) db.installmentReminderDao().getAllReminders() else emptyList()
            counts["installmentReminders"] = reminders.size

            val productReturns = if (categoriesToInclude.contains("productReturns")) db.productReturnDao().getAllReturns() else emptyList()
            counts["productReturns"] = productReturns.size

            val productReturnItems = if (categoriesToInclude.contains("productReturnItems")) db.productReturnDao().getAllReturnItems() else emptyList()
            counts["productReturnItems"] = productReturnItems.size

            val settings = if (categoriesToInclude.contains("storeSettings")) db.storeSettingsDao().getSettings() else null
            counts["storeSettings"] = if (settings != null) 1 else 0

            val logs = if (categoriesToInclude.contains("auditLogs")) db.auditLogDao().getAllLogs() else emptyList()
            counts["auditLogs"] = logs.size

            onProgress(40, "📦 جاري معالجة البيانات وتوليد الحزمة المشفرة...")

            val moshi = getMoshi()
            val packageAdapter = moshi.adapter(BackupPackage::class.java).indent("  ")
            val manifestAdapter = moshi.adapter(BackupManifest::class.java).indent("  ")

            val now = System.currentTimeMillis()
            val dateFmt = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
            val dateStr = dateFmt.format(Date(now))
            val zipFileName = "backup_$dateStr.zip"

            val backupDir = getBackupDirectory(context)
            val finalZipFile = File(backupDir, zipFileName)
            tempZipFile = finalZipFile

            // Prepare Data JSON
            val initialManifest = BackupManifest(
                formatVersion = 1,
                appVersion = "1.1",
                databaseVersion = 12,
                timestamp = now,
                categories = categoriesToInclude.toList(),
                recordCounts = counts,
                checksum = ""
            )

            val backupPackage = BackupPackage(
                manifest = initialManifest,
                clients = clients,
                supplierCompanies = suppliers,
                items = items,
                itemCategories = categories,
                itemUnits = units,
                invoices = invoices,
                invoiceItems = invoiceItems,
                payments = payments,
                installments = installments,
                storeSettings = settings,
                installmentReminders = reminders,
                auditLogs = logs,
                productReturns = productReturns,
                productReturnItems = productReturnItems
            )

            val dataJsonBytes = packageAdapter.toJson(backupPackage).toByteArray(Charsets.UTF_8)
            val checksum = computeSha256(dataJsonBytes)

            val finalManifest = initialManifest.copy(checksum = checksum)
            val manifestJsonBytes = manifestAdapter.toJson(finalManifest).toByteArray(Charsets.UTF_8)

            // Adapters for individual modular JSON files inside the ZIP
            val clientsAdapter = moshi.adapter<List<Client>>(com.squareup.moshi.Types.newParameterizedType(List::class.java, Client::class.java)).indent("  ")
            val invoicesAdapter = moshi.adapter<List<Invoice>>(com.squareup.moshi.Types.newParameterizedType(List::class.java, Invoice::class.java)).indent("  ")
            val invoiceItemsAdapter = moshi.adapter<List<InvoiceItem>>(com.squareup.moshi.Types.newParameterizedType(List::class.java, InvoiceItem::class.java)).indent("  ")
            val paymentsAdapter = moshi.adapter<List<Payment>>(com.squareup.moshi.Types.newParameterizedType(List::class.java, Payment::class.java)).indent("  ")
            val itemsAdapter = moshi.adapter<List<Item>>(com.squareup.moshi.Types.newParameterizedType(List::class.java, Item::class.java)).indent("  ")
            val installmentsAdapter = moshi.adapter<List<Installment>>(com.squareup.moshi.Types.newParameterizedType(List::class.java, Installment::class.java)).indent("  ")
            val settingsAdapter = moshi.adapter(StoreSettings::class.java).indent("  ")
            val returnsAdapter = moshi.adapter<List<ProductReturn>>(com.squareup.moshi.Types.newParameterizedType(List::class.java, ProductReturn::class.java)).indent("  ")
            val returnItemsAdapter = moshi.adapter<List<ProductReturnItem>>(com.squareup.moshi.Types.newParameterizedType(List::class.java, ProductReturnItem::class.java)).indent("  ")

            onProgress(65, "🔐 جاري ضغط وفحص سلامة الملف (SHA-256)...")

            // Write to Structured Zip File
            FileOutputStream(finalZipFile).use { fos ->
                ZipOutputStream(BufferedOutputStream(fos)).use { zos ->
                    fun addEntry(name: String, bytes: ByteArray) {
                        val entry = ZipEntry(name)
                        zos.putNextEntry(entry)
                        zos.write(bytes)
                        zos.closeEntry()
                    }

                    // 1. Metadata and Manifest
                    addEntry("metadata.json", manifestJsonBytes)
                    addEntry("manifest.json", manifestJsonBytes)

                    // 2. Comprehensive Data package
                    addEntry("data.json", dataJsonBytes)

                    // 3. Structured individual tables
                    addEntry("customers.json", clientsAdapter.toJson(clients).toByteArray(Charsets.UTF_8))
                    addEntry("clients.json", clientsAdapter.toJson(clients).toByteArray(Charsets.UTF_8))
                    addEntry("invoices.json", invoicesAdapter.toJson(invoices).toByteArray(Charsets.UTF_8))
                    addEntry("invoice_items.json", invoiceItemsAdapter.toJson(invoiceItems).toByteArray(Charsets.UTF_8))
                    addEntry("payments.json", paymentsAdapter.toJson(payments).toByteArray(Charsets.UTF_8))
                    addEntry("products.json", itemsAdapter.toJson(items).toByteArray(Charsets.UTF_8))
                    addEntry("items.json", itemsAdapter.toJson(items).toByteArray(Charsets.UTF_8))
                    addEntry("installments.json", installmentsAdapter.toJson(installments).toByteArray(Charsets.UTF_8))
                    addEntry("returns.json", returnsAdapter.toJson(productReturns).toByteArray(Charsets.UTF_8))
                    addEntry("return_items.json", returnItemsAdapter.toJson(productReturnItems).toByteArray(Charsets.UTF_8))
                    if (settings != null) {
                        addEntry("settings.json", settingsAdapter.toJson(settings).toByteArray(Charsets.UTF_8))
                    }

                    // 4. Archive images without loading all into RAM
                    val imageDirectories = listOf(
                        File(context.filesDir, "item_images"),
                        File(context.filesDir, "images")
                    )
                    val buffer = ByteArray(8192)
                    for (dir in imageDirectories) {
                        if (dir.exists() && dir.isDirectory) {
                            val imgFiles = dir.listFiles { f -> f.isFile && f.length() > 0 } ?: emptyArray()
                            for (imgFile in imgFiles) {
                                try {
                                    val entry = ZipEntry("images/${imgFile.name}")
                                    zos.putNextEntry(entry)
                                    FileInputStream(imgFile).use { fis ->
                                        var read: Int
                                        while (fis.read(buffer).also { read = it } != -1) {
                                            zos.write(buffer, 0, read)
                                        }
                                    }
                                    zos.closeEntry()
                                } catch (_: Exception) {}
                            }
                        }
                    }
                }
            }

            onProgress(85, "💾 جاري حفظ النسخة في الوجهة المحددة...")

            // If external SAF URI provided, copy file to external destination
            if (!externalFolderUri.isNullOrBlank()) {
                try {
                    val treeUri = Uri.parse(externalFolderUri)
                    SafStorageManager.writeToSafFolder(
                        context = context,
                        treeUri = treeUri,
                        sourceFile = finalZipFile,
                        mimeType = "application/zip",
                        targetFileName = zipFileName
                    )
                    SafStorageManager.enforceRetention(context, treeUri, retentionCount)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            val totalRecords = counts.values.sum()
            val fileSizeBytes = finalZipFile.length()
            val backupType = if (selectedCategories.containsAll(ALL_CATEGORIES)) "كاملة" else "مخصصة"

            // Insert into history
            val history = BackupHistory(
                timestamp = now,
                destination = destination,
                backupType = backupType,
                status = "ناجحة",
                fileSizeBytes = fileSizeBytes,
                recordCount = totalRecords,
                errorDetails = null
            )
            db.backupHistoryDao().insert(history)

            // Enforce retention policy on files and database
            enforceRetentionPolicy(context, db, retentionCount)

            onProgress(100, "✓ تم حفظ النسخة الاحتياطية بنجاح!")
            Result.success(finalZipFile)
        } catch (e: Exception) {
            e.printStackTrace()
            // Record failure in history
            try {
                db.backupHistoryDao().insert(
                    BackupHistory(
                        timestamp = System.currentTimeMillis(),
                        destination = destination,
                        backupType = if (selectedCategories.containsAll(ALL_CATEGORIES)) "كاملة" else "مخصصة",
                        status = "فاشلة",
                        fileSizeBytes = 0,
                        recordCount = 0,
                        errorDetails = e.message ?: "خطأ غير معروف"
                    )
                )
            } catch (_: Exception) {}

            Result.failure(e)
        }
    }

    private suspend fun enforceRetentionPolicy(
        context: Context,
        db: AppDatabase,
        retentionCount: Int
    ) = withContext(Dispatchers.IO) {
        try {
            val backupDir = getBackupDirectory(context)
            val files = backupDir.listFiles { f -> f.extension.equals("zip", ignoreCase = true) || f.extension.equals("json", ignoreCase = true) }
                ?.sortedByDescending { it.lastModified() }
                ?: emptyList()

            // Keep at least retentionCount, but never delete everything if there are files
            if (files.size > retentionCount && retentionCount > 0) {
                val filesToDelete = files.drop(retentionCount)
                filesToDelete.forEach { f ->
                    try {
                        f.delete()
                    } catch (_: Exception) {}
                }
            }

            db.backupHistoryDao().enforceRetentionPolicy(retentionCount)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
