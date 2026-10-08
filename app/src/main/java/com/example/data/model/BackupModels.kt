package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class BackupManifest(
    val formatVersion: Int = 1,
    val appVersion: String = "1.1",
    val databaseVersion: Int = 12,
    val timestamp: Long = System.currentTimeMillis(),
    val categories: List<String> = emptyList(),
    val recordCounts: Map<String, Int> = emptyMap(),
    val checksum: String = "" // Optional basic integrity check
)

@JsonClass(generateAdapter = true)
data class BackupPackage(
    val manifest: BackupManifest,
    val clients: List<Client> = emptyList(),
    val supplierCompanies: List<SupplierCompany> = emptyList(),
    val items: List<Item> = emptyList(),
    val itemCategories: List<ItemCategory> = emptyList(),
    val itemUnits: List<ItemUnit> = emptyList(),
    val invoices: List<Invoice> = emptyList(),
    val invoiceItems: List<InvoiceItem> = emptyList(),
    val payments: List<Payment> = emptyList(),
    val installments: List<Installment> = emptyList(),
    val storeSettings: StoreSettings? = null,
    val installmentReminders: List<InstallmentReminder> = emptyList(),
    val auditLogs: List<AuditLog> = emptyList(),
    val productReturns: List<ProductReturn> = emptyList(),
    val productReturnItems: List<ProductReturnItem> = emptyList()
)

@Entity(tableName = "backup_history")
data class BackupHistory(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val destination: String, // الهاتف, مجلد خارجي, Google Drive
    val backupType: String, // تلقائي, كامل, مخصص
    val status: String, // ناجحة, فاشلة, قيد التنفيذ, ملغاة
    val fileSizeBytes: Long = 0,
    val recordCount: Int = 0,
    val errorDetails: String? = null
)
