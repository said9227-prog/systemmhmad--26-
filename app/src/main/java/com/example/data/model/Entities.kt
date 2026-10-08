
package com.example.data.model

import com.squareup.moshi.JsonClass

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@JsonClass(generateAdapter = true)
@Entity(
    tableName = "exchange_rates",
    indices = [
        Index(value = ["fromCurrency", "toCurrency"], unique = true)
    ]
)
data class ExchangeRate(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val fromCurrency: String,
    val toCurrency: String,
    val rate: Double,
    val lastUpdated: Long = System.currentTimeMillis()
)

@JsonClass(generateAdapter = true)
@Entity(
    tableName = "clients",
    indices = [
        Index(value = ["name"]),
        Index(value = ["phone"]),
        Index(value = ["isPinned", "name"]),
        Index(value = ["balance"])
    ]
)
data class Client(
    val customerId: String = "",
    val altPhone: String = "",
    val companyName: String = "",
    val city: String = "",
    val dealType: String = "نقدي",
    val initialBalance: Double = 0.0,
    val balanceType: String = "عليه لنا",
    val paymentPeriod: String = "عند الطلب",
    val defaultDueDateDays: Int = 0,
    val clientType: String = "فرد",
    val taxNumber: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val phone: String = "",
    val address: String = "",
    val email: String = "",
    val notes: String = "",
    val classification: String = "عادي", // VIP, عادي, إلخ
    val isPinned: Boolean = false,
    val imageUri: String? = null,
    val balance: Double = 0.0, // positive means they owe money, negative means credit
    val creditLimit: Double = 0.0, // الحد الائتماني (0.0 = بدون حد/غير مقيد)
    val creditWarningThreshold: Double = 80.0, // نسبة التنبيه المبكر (افتراضياً 80%)
    val initialBalanceCurrency: String = "الريال اليمني",
    val currency: String = "الريال اليمني",
    val initialBalanceExchangeRate: Double = 1.0,
    val initialBalanceConvertedAmount: Double = 0.0,
    val initialBalanceBaseCurrency: String = "",
    val uuid: String = "",
    val isDeleted: Int = 0
)

@JsonClass(generateAdapter = true)
@Entity(
    tableName = "items",
    indices = [
        Index(value = ["barcode"]),
        Index(value = ["name"]),
        Index(value = ["category"]),
        Index(value = ["supplierCompanyId"]),
        Index(value = ["createdAt"])
    ]
)
data class Item(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val barcode: String = "",
    val category: String = "",
    val unit: String = "قطعة",
    val supplierType: String = "شركة", // "شركة" or "مورد فردي"
    val supplierCompanyId: Int? = null,
    val supplierCompanyName: String = "",
    val individualSupplierName: String = "",
    val individualSupplierPhone: String = "",
    val individualSupplierNotes: String = "",
    val purchaseDate: Long = System.currentTimeMillis(),
    val purchasePrice: Double = 0.0,
    val sellingPrice: Double = 0.0,
    val quantity: Int = 0,
    val minQuantityAlert: Int = 5,
    val imageUri: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val purchaseCurrency: String = "الريال اليمني",
    val sellingCurrency: String = "الريال اليمني",
    val uuid: String = ""
)

@JsonClass(generateAdapter = true)
@Entity(tableName = "item_categories")
data class ItemCategory(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val isPermanent: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@JsonClass(generateAdapter = true)
@Entity(tableName = "item_units")
data class ItemUnit(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val isPermanent: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@JsonClass(generateAdapter = true)
@Entity(
    tableName = "supplier_companies",
    indices = [
        Index(value = ["name"])
    ]
)
data class SupplierCompany(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val phone: String = "",
    val address: String = "",
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@JsonClass(generateAdapter = true)
@Entity(
    tableName = "invoices",
    indices = [
        Index(value = ["clientId"]),
        Index(value = ["date"]),
        Index(value = ["invoiceNumber"]),
        Index(value = ["currency"]),
        Index(value = ["isDraft"]),
        Index(value = ["clientId", "date"])
    ],
    foreignKeys = [
        androidx.room.ForeignKey(
            entity = Client::class,
            parentColumns = ["id"],
            childColumns = ["clientId"],
            onDelete = androidx.room.ForeignKey.CASCADE
        )
    ]
)
data class Invoice(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val invoiceNumber: String,
    val date: Long = System.currentTimeMillis(),
    val clientId: Int,
    val clientName: String,
    val isQuickInvoice: Boolean = false,
    val description: String? = null, // للفاتورة السريعة
    val discount: Double = 0.0,
    val taxRate: Double = 0.0, // نسبة مئوية
    val notes: String? = null,
    val isDraft: Boolean = false,
    val totalAmount: Double = 0.0,
    val paidAmount: Double = 0.0,
    val remainingAmount: Double = 0.0,
    val currency: String = "الريال اليمني",
    val exchangeRate: Double = 1.0,
    val baseCurrency: String = "الريال اليمني",
    val convertedTotalAmount: Double = 0.0,
    val isCreditOverride: Boolean = false, // تم اعتمادها بتجاوز استثنائي للحد الائتماني
    val overrideAuthorizer: String? = null, // اسم المستخدم/المدير الذي صرح بالتجاوز
    val overrideReason: String? = null, // سبب التجاوز
    val overrideAmount: Double = 0.0, // مبلغ التجاوز عن الحد الائتماني
    val dueDate: Long = 0L,
    val uuid: String = ""
)

@JsonClass(generateAdapter = true)
@Entity(
    tableName = "invoice_items",
    indices = [
        Index(value = ["invoiceId"]),
        Index(value = ["itemId"]),
        Index(value = ["itemName"])
    ],
    foreignKeys = [
        androidx.room.ForeignKey(
            entity = Invoice::class,
            parentColumns = ["id"],
            childColumns = ["invoiceId"],
            onDelete = androidx.room.ForeignKey.CASCADE
        )
    ]
)
data class InvoiceItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val invoiceId: Int,
    val itemId: Int? = null,
    val itemName: String,
    val quantity: Int,
    val unitPrice: Double,
    val totalPrice: Double,
    val currency: String = "الريال اليمني",
    val exchangeRate: Double = 1.0,
    val convertedTotalPrice: Double = 0.0,
    val originalProductPrice: Double = 0.0,
    val originalProductCurrency: String = "الريال اليمني",
    val uuid: String = ""
)

@JsonClass(generateAdapter = true)
@Entity(
    tableName = "payments",
    indices = [
        Index(value = ["clientId"]),
        Index(value = ["invoiceId"]),
        Index(value = ["date"]),
        Index(value = ["currency"]),
        Index(value = ["clientId", "date"])
    ],
    foreignKeys = [
        androidx.room.ForeignKey(
            entity = Client::class,
            parentColumns = ["id"],
            childColumns = ["clientId"],
            onDelete = androidx.room.ForeignKey.CASCADE
        )
    ]
)
data class Payment(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val clientId: Int,
    val invoiceId: Int? = null, // اختياري
    val amount: Double,
    val date: Long = System.currentTimeMillis(),
    val paymentMethod: String = "نقدي", // نقدي، إيداع، تحويل، إلخ
    val notes: String? = null,
    val currency: String = "الريال اليمني",
    val exchangeRate: Double = 1.0,
    val baseCurrency: String = "الريال اليمني",
    val convertedAmount: Double = 0.0,
    val voucherNumber: String? = null, // رقم السند
    val collectorName: String? = null, // اسم المحصل
    val transferNumber: String? = null, // رقم الحوالة
    val receiptImageUri: String? = null, // صورة إشعار الحوالة او الإيداع
    val uuid: String = ""
)

@JsonClass(generateAdapter = true)
@Entity(
    tableName = "installments",
    indices = [
        Index(value = ["clientId"]),
        Index(value = ["dueDate"]),
        Index(value = ["isPaid"]),
        Index(value = ["invoiceId"])
    ]
)
data class Installment(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val clientId: Int,
    val clientName: String,
    val invoiceId: Int? = null,
    val amount: Double,
    val dueDate: Long,
    val isPaid: Boolean = false,
    val paidAmount: Double = 0.0,
    val notes: String = "",
    val recurrence: String = "بدون تكرار", // بدون تكرار، يومي، أسبوعي، شهري، سنوي
    val currency: String = "الريال اليمني",
    val notificationId: Int = 0
)

@JsonClass(generateAdapter = true)
@Entity(
    tableName = "audit_logs",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["tableName"]),
        Index(value = ["operationType"]),
        Index(value = ["timestamp", "tableName"])
    ]
)
data class AuditLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val operationType: String, // إضافة، تعديل، حذف
    val tableName: String, // العملاء، الفواتير، الأصناف، المدفوعات
    val details: String
)

@JsonClass(generateAdapter = true)
@Entity(tableName = "store_settings")
data class StoreSettings(
    @PrimaryKey val id: Int = 1,
    val storeName: String = "الحكيمي للأدوية والمستلزمات الطبية",
    val storePhone: String = "",
    val storeAddress: String = "",
    val storeEmail: String = "",
    val storeLogoUri: String? = null,
    val currency: String = "الريال اليمني",
    val language: String = "ar",
    val isDarkMode: Boolean = false,
    val dateFormat: String = "yyyy-MM-dd",
    val invoicePrefix: String = "INV-",
    val lastInvoiceNumber: Int = 0,
    val isAutoNumberingEnabled: Boolean = false, // الوضع الافتراضي عند أول تثبيت: يدوي
    val lastPaymentNumber: Int = 0, // عند التفعيل يبدأ من 1
    val lastSalesReturnNumber: Int = 0, // عند التفعيل يبدأ من 1
    val lastPurchaseReturnNumber: Int = 0, // عند التفعيل يبدأ من 1
    val showVatAndSubtotal: Boolean = false, // الوضع الافتراضي: إزالة ضريبة القيمة المضافة والمجموع الفرعي
    val isAutoPdfBackupEnabled: Boolean = false,
    val autoPdfBackupHour: Int = 0, // 0 = 12 AM (منتصف الليل)
    val overdueDaysThreshold: Int = 30, // عدد أيام التأخر لاعتبار العميل متأخر بالسداد
    val overdueNoticeTemplate: String = "عزيزي العميل {اسم_العميل}، نود تذكيركم بوجود مبلغ مستحق على حسابكم بقيمة {المبلغ_المستحق} {العملة}. نرجو سرعة السداد حتى يتم استمرار منحكم مشتريات جديدة. شاكرين لكم حسن تعاونكم.",
    val loyaltyAppreciationTemplate: String = "عزيزي العميل {اسم_العميل}،\nنشكركم على هذا الوفاء والثقة المتبادلة، ونقدّر استمرار تعاملاتكم معنا، ونأمل أن نستمر معًا في هذا التعاون المميز.\nدمتم بألف خير. 🌹",
    val fastPayerDaysThreshold: Int = 7, // مهلة الأيام لاعتبار السداد سريعاً
    val loyaltyMinInvoicesCount: Int = 3, // الحد الأدنى لعدد الفواتير لتأهيل العميل الوفي
    val isGeneralInstallmentReminderEnabled: Boolean = true,
    val generalReminderHour: Int = 10,
    val generalReminderMinute: Int = 0,
    val generalReminderDaysBeforeDue: Int = 1,
    val generalReminderDaysAfterOverdue: Int = 3,
    val generalReminderScope: String = "جميع ما سبق", // أقساط مستحقة اليوم، أقساط ستستحق قريبًا، أقساط متأخرة، جميع ما سبق
    val generalAlarmDate: Long = 0L, // 0 = موعد دوري، أو تاريخ محدد
    val generalAlarmRecurrence: String = "يومي", // مرة واحدة، يومي، أسبوعي، شهري
    val generalSoundUri: String = "",
    val generalSoundTitle: String = "نغمة النظام الافتراضية",
    val generalVibrationEnabled: Boolean = true,
    val isCustomerInstallmentReminderEnabled: Boolean = true,
    val customerDefaultHour: Int = 10,
    val customerDefaultMinute: Int = 0,
    val customerDefaultRecurrence: String = "مرة واحدة",
    val customerSoundUri: String = "",
    val customerSoundTitle: String = "نغمة النظام الافتراضية",
    val customerVibrationEnabled: Boolean = true,
    val allowCustomCustomerReminder: Boolean = true,
    val enableInvoiceCurrencyConverter: Boolean = true,
    val defaultInvoiceDisplayCurrency: String = "الكل", // "الكل", "YER", "SAR", "USD"
    val enableUnclutteredInvoiceMode: Boolean = true // وضوح العملات وكتابة الفاتورة غير مكتظة
)

enum class InstallmentReminderType {
    GENERAL_INSTALLMENT,
    CUSTOMER_INSTALLMENT
}

enum class InstallmentReminderStatus {
    NEW,
    DISPLAYED,
    HANDLED,
    CANCELLED,
    EXPIRED
}

@JsonClass(generateAdapter = true)
@Entity(
    tableName = "installment_reminders",
    indices = [
        Index(value = ["status"]),
        Index(value = ["reminderType"]),
        Index(value = ["customerId"]),
        Index(value = ["installmentId"]),
        Index(value = ["scheduledAt"])
    ]
)
data class InstallmentReminder(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val reminderType: InstallmentReminderType,
    val customerId: Int? = null,
    val customerName: String? = null,
    val invoiceId: Int? = null,
    val invoiceNumber: String? = null,
    val installmentId: Int? = null,
    val amount: Double = 0.0,
    val remainingAmount: Double = 0.0,
    val dueDate: Long = 0L,
    val overdueDays: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val scheduledAt: Long = System.currentTimeMillis(),
    val handledAt: Long? = null,
    val status: InstallmentReminderStatus = InstallmentReminderStatus.NEW,
    val title: String = "",
    val message: String = "",
    val dueCount: Int = 0,
    val overdueCount: Int = 0,
    val overdueAmount: Double = 0.0,
    val currency: String = "الريال اليمني",
    val generalScope: String = "جميع ما سبق",
    val notificationId: Int = 0,
    val soundUri: String = "",
    val soundTitle: String = "نغمة النظام الافتراضية",
    val vibrationEnabled: Boolean = true,
    val recurrence: String = "مرة واحدة"
)


@JsonClass(generateAdapter = true)
@Entity(
    tableName = "item_purchase_history",
    indices = [
        Index(value = ["itemId"]),
        Index(value = ["supplierCompanyId"]),
        Index(value = ["purchaseDate"])
    ]
)
data class ItemPurchaseHistory(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val itemId: Int,
    val supplierCompanyId: Int?,
    val supplierCompanyName: String,
    val supplierType: String,
    val purchaseDate: Long,
    val purchasePrice: Double,
    val quantity: Int,
    val purchaseCurrency: String = "الريال اليمني",
    val exchangeRate: Double = 1.0
)

@JsonClass(generateAdapter = true)
@Entity(
    tableName = "product_returns",
    indices = [
        Index(value = ["date"]),
        Index(value = ["type"]),
        Index(value = ["clientId"]),
        Index(value = ["invoiceId"])
    ]
)
data class ProductReturn(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val returnNumber: String,
    val type: String, // "CUSTOMER" (مرتجع عملاء) or "PURCHASE" (مرتجع مشتريات)
    val date: Long = System.currentTimeMillis(),
    val invoiceId: Int? = null,
    val invoiceNumber: String = "",
    val clientId: Int? = null,
    val clientName: String = "",
    val supplierCompanyId: Int? = null,
    val supplierType: String = "", // "شركة" or "مورد فردي"
    val supplierName: String = "",
    val totalAmount: Double = 0.0,
    val settlementType: String = "خصم من الرصيد", // "خصم من الرصيد", "استرداد نقدي", "رصيد دائن"
    val reason: String = "", // "تالف", "منتهي الصلاحية", "خطأ في الطلب", "غير مطابق للمواصفات", "طلب العميل", "أخرى"
    val notes: String = "",
    val currency: String = "الريال اليمني",
    val exchangeRate: Double = 1.0,
    val baseCurrency: String = "الريال اليمني",
    val convertedTotalAmount: Double = 0.0,
    val createdAt: Long = System.currentTimeMillis(),
    val uuid: String = ""
)

@JsonClass(generateAdapter = true)
@Entity(
    tableName = "product_return_items",
    indices = [
        Index(value = ["returnId"]),
        Index(value = ["itemId"])
    ]
)
data class ProductReturnItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val returnId: Int,
    val itemId: Int? = null,
    val itemName: String,
    val unit: String = "قطعة",
    val quantity: Int,
    val unitPrice: Double,
    val totalPrice: Double,
    val itemCondition: String = "صالح للبيع", // "صالح للبيع" (يعاد للمخزون) or "تالف" (لا يعاد للمخزون)
    val originalSoldQuantity: Int = 0,
    val currency: String = "الريال اليمني",
    val exchangeRate: Double = 1.0,
    val convertedTotalPrice: Double = 0.0,
    val originalProductPrice: Double = 0.0,
    val originalProductCurrency: String = "الريال اليمني",
    val uuid: String = ""
)

