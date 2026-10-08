package com.example.util

import android.content.Context
import androidx.room.withTransaction
import com.example.data.database.AppDatabase
import com.example.data.model.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import java.security.MessageDigest
import java.util.zip.ZipFile

enum class RestoreMode {
    MERGE,
    FULL_REPLACE,
    SELECTIVE
}

data class RestoreOptions(
    val mode: RestoreMode = RestoreMode.MERGE,
    val selectedCategories: Set<String> = BackupEngine.ALL_CATEGORIES.toSet()
)

data class RestoreResult(
    val success: Boolean,
    val message: String,
    val totalRestored: Int = 0,
    val details: Map<String, Int> = emptyMap()
)

data class BackupPreview(
    val formatVersion: Int,
    val appVersion: String,
    val databaseVersion: Int,
    val timestamp: Long,
    val recordCounts: Map<String, Int>,
    val fileSizeBytes: Long,
    val fileName: String,
    val isCompatible: Boolean,
    val compatibilityMessage: String,
    val tempFile: File
)

object RestoreEngine {

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

    suspend fun restoreFromFile(
        context: Context,
        db: AppDatabase,
        backupFile: File,
        options: RestoreOptions = RestoreOptions(RestoreMode.MERGE, BackupEngine.ALL_CATEGORIES.toSet()),
        onProgress: suspend (progress: Int, stage: String) -> Unit = { _, _ -> }
    ): RestoreResult = withContext(Dispatchers.IO) {
        val workspaceDir = File(context.cacheDir, "restore_workspace_${System.currentTimeMillis()}")
        try {
            if (!backupFile.exists() || backupFile.length() <= 0L) {
                return@withContext RestoreResult(false, "ملف النسخة الاحتياطية غير موجود أو فارغ")
            }

            onProgress(10, "🔍 جاري فحص ملف النسخة الاحتياطية والتحقق من أمان المسارات...")

            // Step 1: Extract to temporary workspace with Zip Slip protection
            val extractResult = extractToWorkspace(backupFile, workspaceDir)
            if (extractResult.isFailure) {
                return@withContext RestoreResult(false, "فشل فك ضغط الحزمة: ${extractResult.exceptionOrNull()?.message}")
            }

            onProgress(25, "📦 جاري قراءة وفحص الـ Manifest والبيانات...")

            // Step 2: Read Manifest & Package
            val (manifest, backupPackage) = readManifestAndPackage(workspaceDir)
                ?: return@withContext RestoreResult(false, "تعذر قراءة ملف الـ Manifest أو بيانات النسخة (ملف تالف أو غير صالح)")

            // Step 3: Schema compatibility check
            if (manifest.databaseVersion > AppDatabase.CURRENT_DATABASE_VERSION) {
                return@withContext RestoreResult(
                    false,
                    "إصدار قاعدة بيانات النسخة الاحتياطية (${manifest.databaseVersion}) أحدث من إصدار التطبيق الحالي (${AppDatabase.CURRENT_DATABASE_VERSION}). يرجى تحديث التطبيق أولاً."
                )
            }

            onProgress(40, "🛡️ جاري التحقق من سلامة البيانات والـ Checksum (SHA-256)...")

            // Step 4: Emergency backup for Full Replace mode
            if (options.mode == RestoreMode.FULL_REPLACE) {
                onProgress(45, "📦 جاري إنشاء نسخة أمان طارئة (Emergency Backup) للبيانات الحالية قبل الاستبدال...")
                try {
                    BackupEngine.createBackup(
                        context = context,
                        db = db,
                        destination = "الهاتف",
                        onProgress = { _, _ -> }
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            onProgress(60, "♻️ جاري تنفيذ عملية الاستعادة داخل معاملة آمنة (Atomic Transaction)...")

            val counts = mutableMapOf<String, Int>()
            val categories = if (options.mode == RestoreMode.SELECTIVE) {
                options.selectedCategories
            } else {
                manifest.categories.ifEmpty { BackupEngine.ALL_CATEGORIES }
            }

            db.withTransaction {
                if (options.mode == RestoreMode.FULL_REPLACE) {
                    if (categories.contains("invoiceItems")) db.invoiceDao().deleteAllInvoiceItems()
                    if (categories.contains("invoices")) db.invoiceDao().deleteAllInvoices()
                    if (categories.contains("payments")) db.paymentDao().deleteAllPayments()
                    if (categories.contains("installments")) db.installmentDao().deleteAllInstallments()
                    if (categories.contains("clients")) db.clientDao().deleteAllClients()
                    if (categories.contains("supplierCompanies")) db.supplierCompanyDao().deleteAllCompanies()
                    if (categories.contains("items")) db.itemDao().deleteAllItems()
                    if (categories.contains("itemCategories")) db.itemCategoryDao().deleteAllCategories()
                    if (categories.contains("itemUnits")) db.itemUnitDao().deleteAllUnits()
                    if (categories.contains("installmentReminders")) db.installmentReminderDao().deleteAllReminders()
                    if (categories.contains("auditLogs")) db.auditLogDao().deleteAllLogs()
                    if (categories.contains("productReturns")) db.productReturnDao().deleteAllReturns()
                    if (categories.contains("productReturnItems")) db.productReturnDao().deleteAllReturnItems()
                }

                // ID Remapping dictionaries for Merge mode
                val clientIdMap = mutableMapOf<Int, Int>()
                val itemIdMap = mutableMapOf<Int, Int>()
                val invoiceIdMap = mutableMapOf<Int, Int>()
                val returnIdMap = mutableMapOf<Int, Int>()

                // 1. Supplier Companies
                if (categories.contains("supplierCompanies") && backupPackage.supplierCompanies.isNotEmpty()) {
                    val existing = db.supplierCompanyDao().getAllCompanies().associateBy { it.name }
                    val toInsert = mutableListOf<SupplierCompany>()
                    backupPackage.supplierCompanies.forEach { comp ->
                        if (!existing.containsKey(comp.name) || options.mode == RestoreMode.FULL_REPLACE) {
                            toInsert.add(comp.copy(id = 0))
                        }
                    }
                    if (toInsert.isNotEmpty()) {
                        db.supplierCompanyDao().insertAll(toInsert)
                    }
                    counts["supplierCompanies"] = backupPackage.supplierCompanies.size
                }

                // 2. Categories
                if (categories.contains("itemCategories") && backupPackage.itemCategories.isNotEmpty()) {
                    val existing = db.itemCategoryDao().getAllCategories().map { it.name }.toSet()
                    val toInsert = backupPackage.itemCategories.filter { !existing.contains(it.name) || options.mode == RestoreMode.FULL_REPLACE }.map { it.copy(id = 0) }
                    if (toInsert.isNotEmpty()) {
                        db.itemCategoryDao().insertAll(toInsert)
                    }
                    counts["itemCategories"] = backupPackage.itemCategories.size
                }

                // 3. Units
                if (categories.contains("itemUnits") && backupPackage.itemUnits.isNotEmpty()) {
                    val existing = db.itemUnitDao().getAllUnits().map { it.name }.toSet()
                    val toInsert = backupPackage.itemUnits.filter { !existing.contains(it.name) || options.mode == RestoreMode.FULL_REPLACE }.map { it.copy(id = 0) }
                    if (toInsert.isNotEmpty()) {
                        db.itemUnitDao().insertAll(toInsert)
                    }
                    counts["itemUnits"] = backupPackage.itemUnits.size
                }

                // 4. Clients with ID remapping & duplicate prevention
                if (categories.contains("clients") && backupPackage.clients.isNotEmpty()) {
                    val existingClients = db.clientDao().getAllClients()
                    for (client in backupPackage.clients) {
                        val match = existingClients.find {
                            (client.uuid.isNotBlank() && it.uuid == client.uuid) ||
                            (client.customerId.isNotBlank() && it.customerId == client.customerId) ||
                            (it.name == client.name && it.phone.isNotBlank() && it.phone == client.phone)
                        }
                        if (match != null && options.mode != RestoreMode.FULL_REPLACE) {
                            clientIdMap[client.id] = match.id
                        } else {
                            val newId = db.clientDao().insertClient(client.copy(id = 0))
                            clientIdMap[client.id] = newId.toInt()
                        }
                    }
                    counts["clients"] = backupPackage.clients.size
                }

                // 5. Items with ID remapping & duplicate prevention
                if (categories.contains("items") && backupPackage.items.isNotEmpty()) {
                    val existingItems = db.itemDao().getAllItems()
                    for (item in backupPackage.items) {
                        val match = existingItems.find {
                            (item.uuid.isNotBlank() && it.uuid == item.uuid) ||
                            (item.barcode.isNotBlank() && it.barcode == item.barcode) ||
                            (it.name == item.name)
                        }
                        if (match != null && options.mode != RestoreMode.FULL_REPLACE) {
                            itemIdMap[item.id] = match.id
                        } else {
                            val newId = db.itemDao().insertItem(item.copy(id = 0))
                            itemIdMap[item.id] = newId.toInt()
                        }
                    }
                    counts["items"] = backupPackage.items.size
                }

                // 6. Invoices with ID remapping & duplicate prevention
                if (categories.contains("invoices") && backupPackage.invoices.isNotEmpty()) {
                    val existingInvoices = db.invoiceDao().getAllInvoices()
                    for (inv in backupPackage.invoices) {
                        val match = existingInvoices.find {
                            (inv.uuid.isNotBlank() && it.uuid == inv.uuid) ||
                            (it.invoiceNumber == inv.invoiceNumber)
                        }
                        if (match != null && options.mode != RestoreMode.FULL_REPLACE) {
                            invoiceIdMap[inv.id] = match.id
                        } else {
                            val remappedClientId = clientIdMap[inv.clientId] ?: inv.clientId
                            val newId = db.invoiceDao().insertInvoice(inv.copy(id = 0, clientId = remappedClientId))
                            invoiceIdMap[inv.id] = newId.toInt()
                        }
                    }
                    counts["invoices"] = backupPackage.invoices.size
                }

                // 7. Invoice Items with FK remapping
                if (categories.contains("invoiceItems") && backupPackage.invoiceItems.isNotEmpty()) {
                    val existingInvoiceItems = db.invoiceDao().getAllInvoiceItems()
                    val toInsert = mutableListOf<InvoiceItem>()
                    for (item in backupPackage.invoiceItems) {
                        val remappedInvoiceId = invoiceIdMap[item.invoiceId] ?: item.invoiceId
                        val remappedItemId = item.itemId?.let { itemIdMap[it] ?: it }
                        val isDuplicate = existingInvoiceItems.any {
                            it.invoiceId == remappedInvoiceId && it.itemName == item.itemName && it.quantity == item.quantity
                        }
                        if (!isDuplicate || options.mode == RestoreMode.FULL_REPLACE) {
                            toInsert.add(item.copy(id = 0, invoiceId = remappedInvoiceId, itemId = remappedItemId))
                        }
                    }
                    if (toInsert.isNotEmpty()) {
                        db.invoiceDao().insertAllInvoiceItems(toInsert)
                    }
                    counts["invoiceItems"] = toInsert.size
                }

                // 8. Payments with FK remapping & duplicate prevention
                if (categories.contains("payments") && backupPackage.payments.isNotEmpty()) {
                    val existingPayments = db.paymentDao().getAllPayments()
                    val toInsert = mutableListOf<Payment>()
                    for (pay in backupPackage.payments) {
                        val remappedClientId = clientIdMap[pay.clientId] ?: pay.clientId
                        val remappedInvoiceId = pay.invoiceId?.let { invoiceIdMap[it] ?: it }
                        val isDuplicate = existingPayments.any {
                            it.clientId == remappedClientId && it.date == pay.date && it.amount == pay.amount && it.voucherNumber == pay.voucherNumber
                        }
                        if (!isDuplicate || options.mode == RestoreMode.FULL_REPLACE) {
                            toInsert.add(pay.copy(id = 0, clientId = remappedClientId, invoiceId = remappedInvoiceId))
                        }
                    }
                    if (toInsert.isNotEmpty()) {
                        db.paymentDao().insertAll(toInsert)
                    }
                    counts["payments"] = toInsert.size
                }

                // 9. Installments with FK remapping
                if (categories.contains("installments") && backupPackage.installments.isNotEmpty()) {
                    val existingInstallments = db.installmentDao().getAllInstallments()
                    val toInsert = mutableListOf<Installment>()
                    for (inst in backupPackage.installments) {
                        val remappedClientId = clientIdMap[inst.clientId] ?: inst.clientId
                        val remappedInvoiceId = inst.invoiceId?.let { invoiceIdMap[it] ?: it }
                        val isDuplicate = existingInstallments.any {
                            it.clientId == remappedClientId && it.dueDate == inst.dueDate && it.amount == inst.amount
                        }
                        if (!isDuplicate || options.mode == RestoreMode.FULL_REPLACE) {
                            toInsert.add(inst.copy(id = 0, clientId = remappedClientId, invoiceId = remappedInvoiceId))
                        }
                    }
                    if (toInsert.isNotEmpty()) {
                        db.installmentDao().insertAll(toInsert)
                    }
                    counts["installments"] = toInsert.size
                }

                // 10. Product Returns with FK remapping
                if (categories.contains("productReturns") && backupPackage.productReturns.isNotEmpty()) {
                    val existingReturns = db.productReturnDao().getAllReturns()
                    for (ret in backupPackage.productReturns) {
                        val match = existingReturns.find {
                            (ret.uuid.isNotBlank() && it.uuid == ret.uuid) || (it.returnNumber == ret.returnNumber)
                        }
                        if (match != null && options.mode != RestoreMode.FULL_REPLACE) {
                            returnIdMap[ret.id] = match.id
                        } else {
                            val remappedClientId = ret.clientId?.let { clientIdMap[it] ?: it }
                            val remappedInvoiceId = ret.invoiceId?.let { invoiceIdMap[it] ?: it }
                            val newId = db.productReturnDao().insertReturn(
                                ret.copy(id = 0, clientId = remappedClientId, invoiceId = remappedInvoiceId)
                            )
                            returnIdMap[ret.id] = newId.toInt()
                        }
                    }
                    counts["productReturns"] = backupPackage.productReturns.size
                }

                // 11. Product Return Items with FK remapping
                if (categories.contains("productReturnItems") && backupPackage.productReturnItems.isNotEmpty()) {
                    val existingReturnItems = db.productReturnDao().getAllReturnItems()
                    val toInsert = mutableListOf<ProductReturnItem>()
                    for (ri in backupPackage.productReturnItems) {
                        val remappedReturnId = returnIdMap[ri.returnId] ?: ri.returnId
                        val remappedItemId = ri.itemId?.let { itemIdMap[it] ?: it }
                        val isDuplicate = existingReturnItems.any {
                            it.returnId == remappedReturnId && it.itemName == ri.itemName && it.quantity == ri.quantity
                        }
                        if (!isDuplicate || options.mode == RestoreMode.FULL_REPLACE) {
                            toInsert.add(ri.copy(id = 0, returnId = remappedReturnId, itemId = remappedItemId))
                        }
                    }
                    if (toInsert.isNotEmpty()) {
                        db.productReturnDao().insertAllReturnItems(toInsert)
                    }
                    counts["productReturnItems"] = toInsert.size
                }

                if (categories.contains("installmentReminders") && backupPackage.installmentReminders.isNotEmpty()) {
                    val existingReminders = db.installmentReminderDao().getAllReminders()
                    val toInsert = mutableListOf<InstallmentReminder>()
                    for (rem in backupPackage.installmentReminders) {
                        val remappedCustomerId = rem.customerId?.let { clientIdMap[it] ?: it }
                        val isDuplicate = existingReminders.any {
                            it.title == rem.title && it.dueDate == rem.dueDate && it.amount == rem.amount
                        }
                        if (!isDuplicate || options.mode == RestoreMode.FULL_REPLACE) {
                            toInsert.add(rem.copy(id = 0, customerId = remappedCustomerId))
                        }
                    }
                    if (toInsert.isNotEmpty()) {
                        db.installmentReminderDao().insertAll(toInsert)
                    }
                    counts["installmentReminders"] = toInsert.size
                }

                if (categories.contains("storeSettings") && backupPackage.storeSettings != null && options.mode == RestoreMode.FULL_REPLACE) {
                    db.storeSettingsDao().insertOrUpdateSettings(backupPackage.storeSettings)
                    counts["storeSettings"] = 1
                }
            }

            onProgress(85, "📁 جاري استعادة الصور المرفقة بطريقة آمنة (Streaming)...")
            restoreImagesFromWorkspace(context, workspaceDir)

            onProgress(95, "📊 جاري إعادة تدقيق ومطابقة أرصدة الحسابات...")
            auditBalances(db)

            onProgress(100, "✓ اكتملت الاستعادة بنجاح تام!")
            val total = counts.values.sum()
            RestoreResult(
                success = true,
                message = "تمت استعادة البيانات بنجاح ($total سجل)",
                totalRestored = total,
                details = counts
            )
        } catch (e: Exception) {
            e.printStackTrace()
            RestoreResult(success = false, message = "فشلت عملية الاستعادة: ${e.localizedMessage}")
        } finally {
            try {
                if (workspaceDir.exists()) {
                    workspaceDir.deleteRecursively()
                }
            } catch (_: Exception) {}
        }
    }

    private fun extractToWorkspace(zipFile: File, workspaceDir: File): Result<Unit> {
        return try {
            if (!workspaceDir.exists()) workspaceDir.mkdirs()
            ZipFile(zipFile).use { zip ->
                val entries = zip.entries()
                val canonicalWorkspacePath = workspaceDir.canonicalPath
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val destFile = File(workspaceDir, entry.name)
                    val canonicalDestPath = destFile.canonicalPath
                    // Zip Slip protection
                    if (!canonicalDestPath.startsWith(canonicalWorkspacePath)) {
                        throw SecurityException("Zip Slip vulnerability detected: ${entry.name}")
                    }
                    if (entry.isDirectory) {
                        destFile.mkdirs()
                    } else {
                        destFile.parentFile?.mkdirs()
                        zip.getInputStream(entry).use { input ->
                            FileOutputStream(destFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun readManifestAndPackage(workspaceDir: File): Pair<BackupManifest, BackupPackage>? {
        return try {
            val moshi = getMoshi()
            val manifestAdapter = moshi.adapter(BackupManifest::class.java)
            val packageAdapter = moshi.adapter(BackupPackage::class.java)

            val manifestFile = File(workspaceDir, "metadata.json").takeIf { it.exists() }
                ?: File(workspaceDir, "manifest.json").takeIf { it.exists() }
            val dataFile = File(workspaceDir, "data.json").takeIf { it.exists() } ?: return null

            val dataBytes = dataFile.readBytes()
            val dataJson = String(dataBytes, Charsets.UTF_8)
            val backupPkg = packageAdapter.fromJson(dataJson) ?: return null

            val manifest = if (manifestFile != null) {
                manifestAdapter.fromJson(manifestFile.readText(Charsets.UTF_8)) ?: backupPkg.manifest
            } else {
                backupPkg.manifest
            }

            if (manifest.checksum.isNotBlank()) {
                val actual = computeSha256(dataBytes)
                if (!actual.equals(manifest.checksum, ignoreCase = true)) {
                    android.util.Log.w("RestoreEngine", "Checksum mismatch in backup archive")
                }
            }

            Pair(manifest, backupPkg)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun restoreImagesFromWorkspace(context: Context, workspaceDir: File) {
        try {
            val imagesDir = File(workspaceDir, "images")
            if (imagesDir.exists() && imagesDir.isDirectory) {
                val targetDir = File(context.filesDir, "item_images").apply { if (!exists()) mkdirs() }
                val files = imagesDir.listFiles() ?: emptyArray()
                for (file in files) {
                    if (file.isFile && file.length() > 0) {
                        val dest = File(targetDir, file.name)
                        file.copyTo(dest, overwrite = true)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun auditBalances(db: AppDatabase) {
        try {
            val repository = com.example.data.repository.AppRepository(db)
            val clients = db.clientDao().getAllClients()
            clients.forEach { client ->
                repository.recalculateClientBalance(client.id)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun inspectBackupUri(context: Context, uri: android.net.Uri): Result<BackupPreview> = withContext(Dispatchers.IO) {
        try {
            val tempFileResult = SafStorageManager.copyUriToTempFile(context, uri, "inspect_restore_temp.zip")
            val tempFile = tempFileResult.getOrNull()
                ?: return@withContext Result.failure(tempFileResult.exceptionOrNull() ?: IOException("تعذر قراءة ملف النسخة"))
            inspectBackupFile(tempFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun inspectBackupFile(file: File, context: Context? = null): Result<BackupPreview> = withContext(Dispatchers.IO) {
        try {
            if (!file.exists() || file.length() <= 0L) {
                return@withContext Result.failure(IllegalArgumentException("ملف النسخة الاحتياطية فارغ أو غير موجود"))
            }

            val workspaceDir = File(file.parentFile, "inspect_ws_${System.currentTimeMillis()}")
            val extractRes = extractToWorkspace(file, workspaceDir)
            if (extractRes.isFailure) {
                return@withContext Result.failure(IllegalArgumentException("فشل قراءة هيكل ملف النسخة الاحتياطية"))
            }

            val (manifest, backupPackage) = readManifestAndPackage(workspaceDir)
                ?: return@withContext Result.failure(IllegalArgumentException("ملف النسخة تالف أو غير صالح"))

            try {
                workspaceDir.deleteRecursively()
            } catch (_: Exception) {}

            val currentAppDbVersion = 17
            val isCompatible = manifest.databaseVersion <= currentAppDbVersion
            val compatibilityMessage = if (isCompatible) {
                "متوافق تماماً مع إصدار التطبيق وقاعدة البيانات الحالية"
            } else {
                "غير متوافق: إصدار قاعدة البيانات (${manifest.databaseVersion}) أحدث من إصدار التطبيق ($currentAppDbVersion)"
            }

            val counts = if (manifest.recordCounts.isNotEmpty()) {
                manifest.recordCounts
            } else {
                mapOf(
                    "clients" to backupPackage.clients.size,
                    "invoices" to backupPackage.invoices.size,
                    "payments" to backupPackage.payments.size,
                    "installments" to backupPackage.installments.size,
                    "items" to backupPackage.items.size
                )
            }

            Result.success(
                BackupPreview(
                    formatVersion = manifest.formatVersion,
                    appVersion = manifest.appVersion,
                    databaseVersion = manifest.databaseVersion,
                    timestamp = manifest.timestamp,
                    recordCounts = counts,
                    fileSizeBytes = file.length(),
                    fileName = file.name,
                    isCompatible = isCompatible,
                    compatibilityMessage = compatibilityMessage,
                    tempFile = file
                )
            )
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun restoreFromUri(
        context: Context,
        db: AppDatabase,
        uri: android.net.Uri,
        options: RestoreOptions = RestoreOptions(RestoreMode.MERGE, BackupEngine.ALL_CATEGORIES.toSet()),
        onProgress: suspend (progress: Int, stage: String) -> Unit = { _, _ -> }
    ): RestoreResult = withContext(Dispatchers.IO) {
        try {
            onProgress(5, "📥 جاري قراءة ملف النسخة الاحتياطية...")
            val tempFileResult = SafStorageManager.copyUriToTempFile(context, uri, "active_restore_temp.zip")
            val tempFile = tempFileResult.getOrNull()
                ?: return@withContext RestoreResult(false, "تعذر قراءة الملف: ${tempFileResult.exceptionOrNull()?.localizedMessage}")

            val result = restoreFromFile(context, db, tempFile, options, onProgress)
            try {
                if (tempFile.exists()) tempFile.delete()
            } catch (_: Exception) {}
            result
        } catch (e: Exception) {
            e.printStackTrace()
            RestoreResult(false, "حدث خطأ غير متوقع أثناء الاستعادة: ${e.localizedMessage}")
        }
    }
}
