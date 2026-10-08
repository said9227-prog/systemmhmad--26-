package com.example.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.util.scrollToTopOnFocus
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.example.data.model.StoreSettings
import com.example.util.FormatUtils
import com.example.ui.viewmodel.AppViewModel
import com.example.ui.viewmodel.SecurityProtectionMode
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: AppViewModel,
    onNavigateToAlarmSettings: () -> Unit = {},
    onNavigateToBackupRestore: () -> Unit = {},
    onNavigateToAuditManagement: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by viewModel.storeSettings.collectAsState()
    val auditLogs by viewModel.auditLogs.collectAsState()
    val securityPin by viewModel.securityPin.collectAsState()

    val isDriveSyncEnabled by viewModel.isDriveSyncEnabled.collectAsState()
    val driveAccountEmail by viewModel.driveAccountEmail.collectAsState()
    val lastBackupTimeStr by viewModel.lastBackupTimeStr.collectAsState()
    val driveBackupStatus by viewModel.driveBackupStatus.collectAsState()
    val availableBackupFiles by viewModel.availableBackupFiles.collectAsState()

    val isBiometricEnabled by viewModel.isBiometricEnabled.collectAsState()
    val biometricUserName by viewModel.biometricUserName.collectAsState()
    val exchangeRates by viewModel.exchangeRates.collectAsState()

    var showBiometricSetupDialog by remember { mutableStateOf(false) }
    var showEditBiometricUserDialog by remember { mutableStateOf(false) }
    var isSecuritySectionExpanded by remember { mutableStateOf(false) }
    var isExchangeRatesExpanded by remember { mutableStateOf(false) }
    var showAddEditRateDialog by remember { mutableStateOf(false) }
    var fromCurrencyInput by remember { mutableStateOf("USD") }
    var toCurrencyInput by remember { mutableStateOf("YER") }
    var rateValueInput by remember { mutableStateOf("") }

    var storeName by remember(settings) { mutableStateOf(settings.storeName) }
    var storePhone by remember(settings) { mutableStateOf(settings.storePhone) }
    var storeAddress by remember(settings) { mutableStateOf(settings.storeAddress) }
    var storeEmail by remember(settings) { mutableStateOf(settings.storeEmail) }
    var currency by remember(settings) { mutableStateOf(settings.currency) }
    var invoicePrefix by remember(settings) { mutableStateOf(settings.invoicePrefix) }
    var lastInvoiceNumber by remember(settings) { mutableStateOf(settings.lastInvoiceNumber.toString()) }
    var isAutoNumberingEnabled by remember(settings) { mutableStateOf(settings.isAutoNumberingEnabled) }
    var lastPaymentNumber by remember(settings) { mutableStateOf(settings.lastPaymentNumber.toString()) }
    var lastSalesReturnNumber by remember(settings) { mutableStateOf(settings.lastSalesReturnNumber.toString()) }
    var lastPurchaseReturnNumber by remember(settings) { mutableStateOf(settings.lastPurchaseReturnNumber.toString()) }
    var showVatAndSubtotal by remember(settings) { mutableStateOf(settings.showVatAndSubtotal) }
    var enableInvoiceCurrencyConverter by remember(settings) { mutableStateOf(settings.enableInvoiceCurrencyConverter) }
    var defaultInvoiceDisplayCurrency by remember(settings) { mutableStateOf(settings.defaultInvoiceDisplayCurrency) }
    var enableUnclutteredInvoiceMode by remember(settings) { mutableStateOf(settings.enableUnclutteredInvoiceMode) }
    var isAutoPdfBackupEnabled by remember(settings) { mutableStateOf(settings.isAutoPdfBackupEnabled) }
    var autoPdfBackupHour by remember(settings) { mutableStateOf(settings.autoPdfBackupHour) }

    var overdueDaysThresholdText by remember(settings) { mutableStateOf(settings.overdueDaysThreshold.toString()) }
    var fastPayerDaysThresholdText by remember(settings) { mutableStateOf(settings.fastPayerDaysThreshold.toString()) }
    var loyaltyMinInvoicesCountText by remember(settings) { mutableStateOf(settings.loyaltyMinInvoicesCount.toString()) }
    var overdueNoticeTemplate by remember(settings) { mutableStateOf(settings.overdueNoticeTemplate) }
    var loyaltyAppreciationTemplate by remember(settings) { mutableStateOf(settings.loyaltyAppreciationTemplate) }

    var generalReminderEnabled by remember(settings) { mutableStateOf(settings.isGeneralInstallmentReminderEnabled) }
    var generalReminderHour by remember(settings) { mutableStateOf(settings.generalReminderHour) }
    var generalReminderDaysBefore by remember(settings) { mutableStateOf(settings.generalReminderDaysBeforeDue.toString()) }
    var generalReminderOverdueDays by remember(settings) { mutableStateOf(settings.generalReminderDaysAfterOverdue.toString()) }
    var generalReminderScope by remember(settings) { mutableStateOf(settings.generalReminderScope) }
    var customerRemindersEnabled by remember(settings) { mutableStateOf(settings.isCustomerInstallmentReminderEnabled) }

    var showAuditLogsDialog by remember { mutableStateOf(false) }
    var showPinSetupDialog by remember { mutableStateOf(false) }
    var showGoogleSignInDialog by remember { mutableStateOf(false) }
    var showRestoreConfirmDialog by remember { mutableStateOf(false) }
    var showBackupFilesListDialog by remember { mutableStateOf(false) }
    var isRestoringData by remember { mutableStateOf(false) }
    var userDriveEmailInput by remember { mutableStateOf(driveAccountEmail ?: "") }

    var isStoreIdentityExpanded by remember { mutableStateOf(false) }
    var isFinancialSettingsExpanded by remember { mutableStateOf(false) }
    var isLoyaltyExpanded by remember { mutableStateOf(false) }
    var isInstallmentRemindersExpanded by remember { mutableStateOf(false) }

    // PDF Export Scheduling state
    val pdfPrefs = remember { context.getSharedPreferences("pdf_backup_prefs", Context.MODE_PRIVATE) }
    var pdfScheduleFrequency by remember { mutableStateOf(pdfPrefs.getString("pdf_schedule_frequency", "DAILY") ?: "DAILY") }
    var pdfScheduleHour by remember(settings) { mutableIntStateOf(pdfPrefs.getInt("pdf_schedule_hour", settings.autoPdfBackupHour)) }
    var pdfScheduleMinute by remember { mutableIntStateOf(pdfPrefs.getInt("pdf_schedule_minute", 0)) }
    var pdfScheduleSpecificDate by remember { mutableStateOf(pdfPrefs.getString("pdf_schedule_specific_date", "") ?: "") }
    var pdfScheduleDayOfWeek by remember { mutableIntStateOf(pdfPrefs.getInt("pdf_schedule_day_of_week", Calendar.THURSDAY)) }
    var pdfScheduleDayOfMonth by remember { mutableIntStateOf(pdfPrefs.getInt("pdf_schedule_day_of_month", 1)) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 90.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 1. Store Identity Section (مخفية افتراضياً)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isStoreIdentityExpanded = !isStoreIdentityExpanded }
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Storefront, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("معلومات وهوية المتجر", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        }
                        Icon(
                            imageVector = if (isStoreIdentityExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (isStoreIdentityExpanded) "إخفاء" else "عرض",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    AnimatedVisibility(visible = isStoreIdentityExpanded) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Divider()

                            OutlinedTextField(
                                value = storeName,
                                onValueChange = { storeName = it },
                                label = { Text("اسم المتجر / العلامة التجارية *") },
                                modifier = Modifier.scrollToTopOnFocus().scrollToTopOnFocus().fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = storePhone,
                                onValueChange = { storePhone = it },
                                label = { Text("رقم هاتف المتجر") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                modifier = Modifier.scrollToTopOnFocus().scrollToTopOnFocus().fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = storeAddress,
                                onValueChange = { storeAddress = it },
                                label = { Text("عنوان المقر") },
                                modifier = Modifier.scrollToTopOnFocus().scrollToTopOnFocus().fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = storeEmail,
                                onValueChange = { storeEmail = it },
                                label = { Text("البريد الإلكتروني التجاري") },
                                modifier = Modifier.scrollToTopOnFocus().scrollToTopOnFocus().fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }

        // 1.5. أزرار التحويل للشاشة الرقمية (بطاقة مخصصة وظاهرة في صفحة الإعدادات)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (enableInvoiceCurrencyConverter) 
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                    else 
                        MaterialTheme.colorScheme.surface
                ),
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.5.dp,
                    color = if (enableInvoiceCurrencyConverter) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.CurrencyExchange,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "أزرار التحويل للشاشة الرقمية",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (enableInvoiceCurrencyConverter) "الميزة مفعلة للشاشة الرقمية" else "معطلة حالياً",
                                    fontSize = 11.sp,
                                    color = if (enableInvoiceCurrencyConverter) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        Switch(
                            checked = enableInvoiceCurrencyConverter,
                            onCheckedChange = { checked ->
                                enableInvoiceCurrencyConverter = checked
                                viewModel.updateStoreSettings(settings.copy(
                                    enableInvoiceCurrencyConverter = checked,
                                    defaultInvoiceDisplayCurrency = defaultInvoiceDisplayCurrency
                                ))
                                Toast.makeText(
                                    context,
                                    if (checked) "تم تفعيل أزرار التحويل للشاشة الرقمية بنجاح" else "تم تعطيل أزرار التحويل للشاشة الرقمية",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        )
                    }

                    Text(
                        text = "يتيح هذا الزر إظهار أزرار التحويل السريع بين العملات (شامل / يمني / سعودي / دولار) على الشاشة الرقمية للفاتورة لاحتساب الإجمالي وكافة الأصناف بالعملة المختارة فورياً حتى لو كان الشراء أو البيع بعملة أخرى.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )

                    if (enableInvoiceCurrencyConverter) {
                        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Text(
                            text = "العملة الافتراضية للشاشة الرقمية عند الفتح:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        val displayCurrencyOptions = listOf(
                            "الكل" to "🌐 شامل",
                            "YER" to "🇾🇪 يمني",
                            "SAR" to "🇸🇦 سعودي",
                            "USD" to "🇺🇸 دولار"
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            displayCurrencyOptions.forEach { (key, title) ->
                                val isCurSelected = defaultInvoiceDisplayCurrency == key
                                FilterChip(
                                    selected = isCurSelected,
                                    onClick = {
                                        defaultInvoiceDisplayCurrency = key
                                        viewModel.updateStoreSettings(settings.copy(
                                            enableInvoiceCurrencyConverter = true,
                                            defaultInvoiceDisplayCurrency = key
                                        ))
                                        Toast.makeText(context, "العملة الافتراضية للشاشة: $title", Toast.LENGTH_SHORT).show()
                                    },
                                    label = {
                                        Text(
                                            title,
                                            fontSize = 11.sp,
                                            fontWeight = if (isCurSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 1.6. وضوح العملات وكتابة الفاتورة غير مكتظة (ميزة مخصصة مطلوبة في الإعدادات)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (enableUnclutteredInvoiceMode) 
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.25f)
                    else 
                        MaterialTheme.colorScheme.surface
                ),
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.5.dp,
                    color = if (enableUnclutteredInvoiceMode) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.ReceiptLong,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "وضوح العملات وكتابة الفاتورة غير مكتظة",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (enableUnclutteredInvoiceMode) "مفعل: مسموح بظهور الوضوح بين العملات وفاتورة غير مكتظة" else "معطل: عرض تقليدي مكدّس للعملات",
                                    fontSize = 11.sp,
                                    color = if (enableUnclutteredInvoiceMode) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        Switch(
                            checked = enableUnclutteredInvoiceMode,
                            onCheckedChange = { checked ->
                                enableUnclutteredInvoiceMode = checked
                                viewModel.updateStoreSettings(settings.copy(
                                    enableUnclutteredInvoiceMode = checked
                                ))
                                Toast.makeText(
                                    context,
                                    if (checked) "تم تفعيل نمط وضوح العملات وكتابة الفاتورة غير مكتظة" else "تم تعطيل نمط الفاتورة غير المكتظة",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        )
                    }

                    Text(
                        text = "يتيح هذا الزر تفعيل ميزة الوضوح بين العملات؛ حيث تُكتب وتُعرض الفاتورة وملف الـ PDF ونصوص المشاركة بتنسيق راقٍ وواضح وغير مكتظ، مع إبراز العملة المختارة بدقة دون تشتيت أو ازدحام عشوائي.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        // 2. Financial & Invoicing Settings (مخفية افتراضياً)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isFinancialSettingsExpanded = !isFinancialSettingsExpanded }
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.PriceChange, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("الإعدادات المالية والترقيم", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        }
                        Icon(
                            imageVector = if (isFinancialSettingsExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (isFinancialSettingsExpanded) "إخفاء" else "عرض",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    AnimatedVisibility(visible = isFinancialSettingsExpanded) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Divider()

                            // Currency chooser row
                            Text("العملات المستخدمة:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    val currencyOptions = listOf(
                        "الريال اليمني" to "ر.ي",
                        "الريال السعودي" to "ر.س",
                        "الدولار الأمريكي" to "$"
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        currencyOptions.forEach { (name, code) ->
                            val selected = currency == name || currency == code
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { currency = name },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                                ),
                                border = if (selected) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp).fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(code, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(name, fontSize = 10.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = invoicePrefix,
                            onValueChange = { invoicePrefix = it },
                            label = { Text("بادئة الفواتير") },
                            modifier = Modifier.scrollToTopOnFocus().scrollToTopOnFocus().weight(1f)
                        )
                        OutlinedTextField(
                            value = lastInvoiceNumber,
                            onValueChange = { lastInvoiceNumber = it },
                            label = { Text("رقم آخر فاتورة") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.scrollToTopOnFocus().scrollToTopOnFocus().weight(1f)
                        )
                    }

                    Divider()

                    // Auto-numbering toggle for payments and returns
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "توليد أرقام السندات والمرتجعات تلقائياً",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = if (isAutoNumberingEnabled) 
                                    "مفعل: ترقيم آلي لسندات السداد ومرتجع المبيعات ومرتجع المشتريات (تبدأ من 1)"
                                else 
                                    "يدوي (الافتراضي): تسجيل رقم السند ورقم المرتجع يدوياً بمربع فارغ",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isAutoNumberingEnabled,
                            onCheckedChange = { checked ->
                                isAutoNumberingEnabled = checked
                                if (checked) {
                                    if ((lastPaymentNumber.toIntOrNull() ?: 0) == 0) lastPaymentNumber = "0"
                                    if ((lastSalesReturnNumber.toIntOrNull() ?: 0) == 0) lastSalesReturnNumber = "0"
                                    if ((lastPurchaseReturnNumber.toIntOrNull() ?: 0) == 0) lastPurchaseReturnNumber = "0"
                                }
                            }
                        )
                    }

                    if (isAutoNumberingEnabled) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "أرقام البداية للتسلسل الآلي (الرقم التالي سيكون +1):",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = lastPaymentNumber,
                                    onValueChange = { lastPaymentNumber = it },
                                    label = { Text("آخر رقم سداد") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.scrollToTopOnFocus().weight(1f),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = lastSalesReturnNumber,
                                    onValueChange = { lastSalesReturnNumber = it },
                                    label = { Text("آخر مرتجع مبيعات") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.scrollToTopOnFocus().weight(1f),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = lastPurchaseReturnNumber,
                                    onValueChange = { lastPurchaseReturnNumber = it },
                                    label = { Text("آخر مرتجع مشتريات") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.scrollToTopOnFocus().weight(1f),
                                    singleLine = true
                                )
                            }
                        }
                    }

                    Divider()

                    // VAT & Subtotal toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "إظهار ضريبة القيمة المضافة والمجموع الفرعي",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = if (showVatAndSubtotal)
                                    "معروض في تفاصيل الفواتير والطباعة ومشاركتها"
                                else
                                    "مخفي (الافتراضي): إخفاء سطري المجموع الفرعي والضريبة لتبسيط الفاتورة",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = showVatAndSubtotal,
                            onCheckedChange = { showVatAndSubtotal = it }
                        )
                    }

                    Divider()

                    // Multi-currency Invoice & Items Calculation Feature (ميزة احتساب الفاتورة والأصناف بأي عملة)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.CurrencyExchange, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                Text(
                                    text = "احتساب الفاتورة والأصناف بالعملة المختارة",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            Text(
                                text = if (enableInvoiceCurrencyConverter)
                                    "مفعل: احتساب الفاتورة الإجمالي ومع جميع الأصناف بأي عملة يتم اختيارها (يمني / سعودي / دولار) فورياً حتى لو كان الشراء أو البيع بعملة أخرى"
                                else
                                    "معطل (الافتراضي): يقتصر العرض على عملة الفاتورة الافتراضية فقط",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = enableInvoiceCurrencyConverter,
                            onCheckedChange = { enableInvoiceCurrencyConverter = it }
                        )
                    }

                    if (enableInvoiceCurrencyConverter) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "العرض الافتراضي المفضل عند فتح الفاتورة:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            val displayCurrencyOptions = listOf(
                                "الكل" to "🌐 عرض ثلاثي شامل (سعودي + يمني + دولار)",
                                "YER" to "🇾🇪 ريال يمني (YER)",
                                "SAR" to "🇸🇦 ريال سعودي (SAR)",
                                "USD" to "🇺🇸 دولار أمريكي (USD)"
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                displayCurrencyOptions.forEach { (key, title) ->
                                    val isCurSelected = defaultInvoiceDisplayCurrency == key
                                    FilterChip(
                                        selected = isCurSelected,
                                        onClick = { defaultInvoiceDisplayCurrency = key },
                                        label = { Text(if (key == "الكل") "🌐 شامل" else key, fontSize = 11.sp, fontWeight = if (isCurSelected) FontWeight.Bold else FontWeight.Normal) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

        // 2.5. Exchange Rates & Currency Conversion Settings (إدارة أسعار الصرف والمصارفة بين العملات)
        item {
            if (showAddEditRateDialog) {
                AlertDialog(
                    onDismissRequest = { showAddEditRateDialog = false },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.CurrencyExchange, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("تحديد سعر الصرف والمصارفة", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                "حدد كم يساوي 1 من العملة الأولى بالعملة المقابلة (وسيقوم النظام بحساب التحويل المباشر والعكسي تلقائياً):",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // From Currency
                            val supportedCurrencies = listOf(
                                "USD" to "دولار أمريكي (USD)",
                                "SAR" to "ريال سعودي (SAR)",
                                "YER" to "ريال يمني (YER)",
                                "EUR" to "يورو (EUR)"
                            )

                            Text("من عملة (1 وحدة):", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                supportedCurrencies.forEach { (code, label) ->
                                    FilterChip(
                                        selected = fromCurrencyInput == code,
                                        onClick = { fromCurrencyInput = code },
                                        label = { Text(code, fontSize = 11.sp) },
                                        modifier = Modifier.weight(1f),
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                        )
                                    )
                                }
                            }

                            Text("إلى عملة (المقابل):", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                supportedCurrencies.forEach { (code, label) ->
                                    FilterChip(
                                        selected = toCurrencyInput == code,
                                        onClick = { toCurrencyInput = code },
                                        label = { Text(code, fontSize = 11.sp) },
                                        modifier = Modifier.weight(1f),
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.secondary,
                                            selectedLabelColor = MaterialTheme.colorScheme.onSecondary
                                        )
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = rateValueInput,
                                onValueChange = { rateValueInput = it },
                                label = { Text("سعر الصرف (كم يساوي 1 $fromCurrencyInput بالـ $toCurrencyInput)") },
                                placeholder = { Text("مثال: 1500 أو 400 أو 3.75") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            val ratePreview = rateValueInput.toDoubleOrNull()
                            if (ratePreview != null && ratePreview > 0) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("المعادلة المباشرة: 1 $fromCurrencyInput = $ratePreview $toCurrencyInput", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                        val inv = 1.0 / ratePreview
                                        Text("المعادلة العكسية: 1 $toCurrencyInput ≈ ${String.format(Locale.US, "%.5f", inv)} $fromCurrencyInput", fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(onClick = {
                            val rateVal = rateValueInput.toDoubleOrNull()
                            if (rateVal == null || rateVal <= 0) {
                                Toast.makeText(context, "يرجى إدخال سعر صرف صحيح أكبر من الصفر", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (fromCurrencyInput == toCurrencyInput) {
                                Toast.makeText(context, "يرجى اختيار عملتين مختلفتين", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            viewModel.saveExchangeRate(fromCurrencyInput, toCurrencyInput, rateVal)
                            Toast.makeText(context, "تم حفظ وتحديث سعر الصرف بنجاح", Toast.LENGTH_SHORT).show()
                            showAddEditRateDialog = false
                            rateValueInput = ""
                        }) {
                            Text("حفظ سعر الصرف", fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showAddEditRateDialog = false }) {
                            Text("إلغاء")
                        }
                    }
                )
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isExchangeRatesExpanded = !isExchangeRatesExpanded }
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.CurrencyExchange, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column {
                                Text("إدارة أسعار الصرف والمصارفة بين العملات", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "تحديد أسعار التحويل المباشر والعكسي بين العملات بدقة عالية",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Icon(
                            imageVector = if (isExchangeRatesExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (isExchangeRatesExpanded) "إخفاء" else "عرض",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    AnimatedVisibility(visible = isExchangeRatesExpanded) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Divider()

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "أسعار الصرف المعتمدة بالنظام (${exchangeRates.size}):",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Button(
                                    onClick = {
                                        rateValueInput = ""
                                        showAddEditRateDialog = true
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("إضافة / تعديل سعر", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            if (exchangeRates.isEmpty()) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        "لا توجد أسعار صرف مسجلة. اضغط على 'إضافة / تعديل سعر' لإضافة أسعار الصرف.",
                                        modifier = Modifier.padding(12.dp),
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                exchangeRates.forEach { rate ->
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    Text(
                                                        "1 ${rate.fromCurrency} = ${FormatUtils.formatAmount(rate.rate)} ${rate.toCurrency}",
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 14.sp,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                                val inverse = if (rate.rate > 0) 1.0 / rate.rate else 0.0
                                                Text(
                                                    "المقابل العكسي: 1 ${rate.toCurrency} ≈ ${String.format(Locale.US, "%.5f", inverse)} ${rate.fromCurrency}",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }

                                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                IconButton(
                                                    onClick = {
                                                        fromCurrencyInput = rate.fromCurrency
                                                        toCurrencyInput = rate.toCurrency
                                                        rateValueInput = rate.rate.toString()
                                                        showAddEditRateDialog = true
                                                    },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(Icons.Default.Edit, contentDescription = "تعديل", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                                }
                                                IconButton(
                                                    onClick = {
                                                        viewModel.deleteExchangeRate(rate)
                                                        Toast.makeText(context, "تم حذف سعر الصرف", Toast.LENGTH_SHORT).show()
                                                    },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(Icons.Default.DeleteOutline, contentDescription = "حذف", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                    Text(
                                        "💡 يقوم التطبيق بسحب أسعار الصرف هذه رياضياً واستخدامها في تحويل قيم الأصناف تلقائياً وخصمها من حساب العميل بالعملة الأساسية المسجلة له بدقة 100%.",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. App Security Settings (أمان وحماية التطبيق - البصمة فقط، بصمة ورقم سري، رقم سري فقط)
        item {
            val protectionMode by viewModel.securityProtectionMode.collectAsState()

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Header row (Clickable to show/hide options)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isSecuritySectionExpanded = !isSecuritySectionExpanded }
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column {
                                Text("أمان وحماية التطبيق", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    if (isSecuritySectionExpanded) "تخصيص نمط القفل والدخول السريع" else "انقر هنا لعرض إعدادات الخصوصية والقفل",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Current status badge
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = when (protectionMode) {
                                    SecurityProtectionMode.NONE -> Color.Gray.copy(alpha = 0.15f)
                                    SecurityProtectionMode.BIOMETRIC_ONLY -> Color(0xFF0284C7).copy(alpha = 0.15f)
                                    SecurityProtectionMode.PIN_ONLY -> Color(0xFF10B981).copy(alpha = 0.15f)
                                    SecurityProtectionMode.BIOMETRIC_AND_PIN -> Color(0xFF8B5CF6).copy(alpha = 0.15f)
                                }
                            ) {
                                Text(
                                    text = protectionMode.label,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = when (protectionMode) {
                                        SecurityProtectionMode.NONE -> Color.Gray
                                        SecurityProtectionMode.BIOMETRIC_ONLY -> Color(0xFF0284C7)
                                        SecurityProtectionMode.PIN_ONLY -> Color(0xFF059669)
                                        SecurityProtectionMode.BIOMETRIC_AND_PIN -> Color(0xFF7C3AED)
                                    },
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }

                            Icon(
                                imageVector = if (isSecuritySectionExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = if (isSecuritySectionExpanded) "طوي" else "إظهار",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Hidden content expanded on click
                    AnimatedVisibility(visible = isSecuritySectionExpanded) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            HorizontalDivider()

                            Text("اختر نمط الحماية المطلوب للتطبيق:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)

                            // Protection Modes list
                            val modes = listOf(
                                Triple(SecurityProtectionMode.BIOMETRIC_ONLY, "البصمة فقط", "فتح التطبيق بالبصمة فوراً وتلقائياً عند الدخول دون كتابة أرقام"),
                                Triple(SecurityProtectionMode.BIOMETRIC_AND_PIN, "بصمة ورقم سري", "استشعار البصمة المباشر مع توفر لوحة PIN كخيار بديل"),
                                Triple(SecurityProtectionMode.PIN_ONLY, "رقم سري فقط", "قفل التطبيق برمز سري PIN مكوّن من 4 أرقام"),
                                Triple(SecurityProtectionMode.NONE, "بدون قفل (معطل)", "إيقاف القفل وفتح التطبيق مباشرة بدون أمان")
                            )

                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                modes.forEach { (mode, title, desc) ->
                                    val isSelected = protectionMode == mode
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                        border = androidx.compose.foundation.BorderStroke(
                                            if (isSelected) 1.5.dp else 1.dp,
                                            if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                when (mode) {
                                                    SecurityProtectionMode.NONE -> {
                                                        viewModel.setSecurityProtectionMode(SecurityProtectionMode.NONE)
                                                        Toast.makeText(context, "تم إيقاف قفل التطبيق", Toast.LENGTH_SHORT).show()
                                                    }
                                                    SecurityProtectionMode.BIOMETRIC_ONLY -> {
                                                        if (biometricUserName.isBlank()) {
                                                            showBiometricSetupDialog = true
                                                        } else {
                                                            viewModel.setSecurityProtectionMode(SecurityProtectionMode.BIOMETRIC_ONLY, null, biometricUserName)
                                                            Toast.makeText(context, "تم ضبط الحماية: البصمة فقط (تظهر فوراً عند الدخول)", Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                    SecurityProtectionMode.PIN_ONLY -> {
                                                        if (securityPin.isNullOrBlank()) {
                                                            showPinSetupDialog = true
                                                        } else {
                                                            viewModel.setSecurityProtectionMode(SecurityProtectionMode.PIN_ONLY, securityPin, "")
                                                            Toast.makeText(context, "تم ضبط الحماية: رقم سري فقط", Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                    SecurityProtectionMode.BIOMETRIC_AND_PIN -> {
                                                        if (securityPin.isNullOrBlank()) {
                                                            showPinSetupDialog = true
                                                        } else {
                                                            viewModel.setSecurityProtectionMode(SecurityProtectionMode.BIOMETRIC_AND_PIN, securityPin, biometricUserName)
                                                            Toast.makeText(context, "تم ضبط الحماية: بصمة ورقم سري معاً", Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                }
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            RadioButton(
                                                selected = isSelected,
                                                onClick = null // handled by outer surface clickable
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                Text(desc, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            Icon(
                                                imageVector = when (mode) {
                                                    SecurityProtectionMode.BIOMETRIC_ONLY -> Icons.Default.Fingerprint
                                                    SecurityProtectionMode.BIOMETRIC_AND_PIN -> Icons.Default.VerifiedUser
                                                    SecurityProtectionMode.PIN_ONLY -> Icons.Default.Pin
                                                    SecurityProtectionMode.NONE -> Icons.Default.LockOpen
                                                },
                                                contentDescription = null,
                                                tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // Configuration buttons for PIN and Biometric user
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { showPinSetupDialog = true },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.Password, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(if (securityPin.isNullOrBlank()) "تعيين PIN" else "تعديل PIN", fontSize = 11.sp)
                                }

                                OutlinedButton(
                                    onClick = {
                                        if (biometricUserName.isBlank()) showBiometricSetupDialog = true
                                        else showEditBiometricUserDialog = true
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.Fingerprint, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(if (biometricUserName.isBlank()) "تسجيل البصمة" else "مستخدم البصمة", fontSize = 11.sp)
                                }
                            }

                            if (protectionMode == SecurityProtectionMode.BIOMETRIC_ONLY || protectionMode == SecurityProtectionMode.BIOMETRIC_AND_PIN) {
                                Surface(
                                    color = Color(0xFF0284C7).copy(alpha = 0.08f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF0284C7), modifier = Modifier.size(16.dp))
                                        Text(
                                            "ملاحظة: عند تفعيل البصمة، سيظهر طلب المصادقة فوراً وتلقائياً بمجرد فتح التطبيق.",
                                            fontSize = 10.sp,
                                            color = Color(0xFF0284C7)
                                        )
                                    }
                                }
                            }

                            HorizontalDivider()

                            // Operations Audit Log shortcut
                            Button(
                                onClick = { showAuditLogsDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.HistoryToggleOff, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("عرض سجل عمليات النظام (Audit Log)")
                            }
                        }
                    }
                }
            }
        }

        // 4. Advanced Backup, Restore & Export Center
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToBackupRestore() },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                ),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(46.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Backup,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "مركز النسخ الاحتياطي والاستعادة والتصدير",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "النسخ التلقائي المجدول، استعادة البيانات الآمنة، وتصدير التقارير (Excel/PDF)",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.Default.ChevronLeft,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // 4a2. Audit Log Retention & Management (أرشفة وتطهير سجل العمليات)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToAuditManagement() }
                    .testTag("card_audit_log_management"),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                ),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.secondary)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(46.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Inventory2,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "إدارة سجل العمليات والأرشفة والتنظيف الشهري",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "أرشفة شهرية، تطهير آمن، سياسات الاحتفاظ، وفحص واستعادة حزم ZIP",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.Default.ChevronLeft,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }


        // 5. Automatic Scheduled PDF Statement Export Section for All Clients (تصدير كشوفات العملاء التلقائي بالخلفية)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isAutoPdfBackupEnabled) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isAutoPdfBackupEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = Color(0xFFDC2626))
                            Column {
                                Text("التصدير التلقائي لكشوفات العملاء (PDF)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                Text("تصدير تقرير شامل في الخلفية وحفظه بمجلد التنزيلات", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Switch(
                            checked = isAutoPdfBackupEnabled,
                            onCheckedChange = { enabled ->
                                isAutoPdfBackupEnabled = enabled
                                viewModel.scheduleClientStatementExport(
                                    enabled = enabled,
                                    frequency = pdfScheduleFrequency,
                                    hour = pdfScheduleHour,
                                    minute = pdfScheduleMinute,
                                    specificDate = pdfScheduleSpecificDate,
                                    dayOfWeek = pdfScheduleDayOfWeek,
                                    dayOfMonth = pdfScheduleDayOfMonth
                                )
                                if (enabled) {
                                    Toast.makeText(context, "تم تفعيل التصدير التلقائي لكشوفات العملاء في الخلفية", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "تم إيقاف التصدير التلقائي لكشوفات العملاء", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }

                    HorizontalDivider()

                    if (isAutoPdfBackupEnabled) {
                        // 1. Frequency selection (يومياً، أسبوعياً، شهرياً، تاريخ محدد)
                        Text("تكرار وجدولة التصدير التلقائي:", fontWeight = FontWeight.Bold, fontSize = 12.sp)

                        val freqOptions = listOf(
                            "DAILY" to "يومياً",
                            "WEEKLY" to "أسبوعياً",
                            "MONTHLY" to "شهرياً",
                            "SPECIFIC_DATE" to "تاريخ محدد"
                        )

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            freqOptions.forEach { (freqKey, freqLabel) ->
                                val isSelected = pdfScheduleFrequency == freqKey
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        pdfScheduleFrequency = freqKey
                                        viewModel.scheduleClientStatementExport(
                                            enabled = true,
                                            frequency = freqKey,
                                            hour = pdfScheduleHour,
                                            minute = pdfScheduleMinute,
                                            specificDate = pdfScheduleSpecificDate,
                                            dayOfWeek = pdfScheduleDayOfWeek,
                                            dayOfMonth = pdfScheduleDayOfMonth
                                        )
                                    },
                                    label = { Text(freqLabel, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        // Specific Day configuration depending on frequency
                        when (pdfScheduleFrequency) {
                            "WEEKLY" -> {
                                Text("اختر يوم التصدير الأسبوعي:", fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                                val daysOfWeekList = listOf(
                                    Calendar.SATURDAY to "السبت",
                                    Calendar.SUNDAY to "الأحد",
                                    Calendar.MONDAY to "الإثنين",
                                    Calendar.TUESDAY to "الثلاثاء",
                                    Calendar.WEDNESDAY to "الأربعاء",
                                    Calendar.THURSDAY to "الخميس",
                                    Calendar.FRIDAY to "الجمعة"
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    daysOfWeekList.take(4).forEach { (dayCode, dayName) ->
                                        FilterChip(
                                            selected = pdfScheduleDayOfWeek == dayCode,
                                            onClick = {
                                                pdfScheduleDayOfWeek = dayCode
                                                viewModel.scheduleClientStatementExport(
                                                    enabled = true,
                                                    frequency = pdfScheduleFrequency,
                                                    hour = pdfScheduleHour,
                                                    minute = pdfScheduleMinute,
                                                    specificDate = pdfScheduleSpecificDate,
                                                    dayOfWeek = dayCode,
                                                    dayOfMonth = pdfScheduleDayOfMonth
                                                )
                                            },
                                            label = { Text(dayName, fontSize = 10.sp) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    daysOfWeekList.drop(4).forEach { (dayCode, dayName) ->
                                        FilterChip(
                                            selected = pdfScheduleDayOfWeek == dayCode,
                                            onClick = {
                                                pdfScheduleDayOfWeek = dayCode
                                                viewModel.scheduleClientStatementExport(
                                                    enabled = true,
                                                    frequency = pdfScheduleFrequency,
                                                    hour = pdfScheduleHour,
                                                    minute = pdfScheduleMinute,
                                                    specificDate = pdfScheduleSpecificDate,
                                                    dayOfWeek = dayCode,
                                                    dayOfMonth = pdfScheduleDayOfMonth
                                                )
                                            },
                                            label = { Text(dayName, fontSize = 10.sp) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                            "MONTHLY" -> {
                                Text("اختر يوم التصدير من كل شهر:", fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                                val monthDayOptions = listOf(1, 5, 10, 15, 20, 25, 28)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    monthDayOptions.forEach { d ->
                                        FilterChip(
                                            selected = pdfScheduleDayOfMonth == d,
                                            onClick = {
                                                pdfScheduleDayOfMonth = d
                                                viewModel.scheduleClientStatementExport(
                                                    enabled = true,
                                                    frequency = pdfScheduleFrequency,
                                                    hour = pdfScheduleHour,
                                                    minute = pdfScheduleMinute,
                                                    specificDate = pdfScheduleSpecificDate,
                                                    dayOfWeek = pdfScheduleDayOfWeek,
                                                    dayOfMonth = d
                                                )
                                            },
                                            label = { Text("يوم $d", fontSize = 10.sp) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                            "SPECIFIC_DATE" -> {
                                Text("اختر التاريخ المحدد للتصدير بالخلفية:", fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                                OutlinedButton(
                                    onClick = {
                                        val cal = Calendar.getInstance()
                                        val dp = DatePickerDialog(
                                            context,
                                            { _, y, m, d ->
                                                val formatted = String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d)
                                                pdfScheduleSpecificDate = formatted
                                                viewModel.scheduleClientStatementExport(
                                                    enabled = true,
                                                    frequency = pdfScheduleFrequency,
                                                    hour = pdfScheduleHour,
                                                    minute = pdfScheduleMinute,
                                                    specificDate = formatted,
                                                    dayOfWeek = pdfScheduleDayOfWeek,
                                                    dayOfMonth = pdfScheduleDayOfMonth
                                                )
                                            },
                                            cal.get(Calendar.YEAR),
                                            cal.get(Calendar.MONTH),
                                            cal.get(Calendar.DAY_OF_MONTH)
                                        )
                                        dp.datePicker.minDate = System.currentTimeMillis()
                                        dp.show()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (pdfScheduleSpecificDate.isBlank()) "اضغط هنا لاختيار التاريخ المحدد" else "التاريخ المحدد: $pdfScheduleSpecificDate",
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Time Picker Selection
                        Text("وقت عمل الإجراء التلقائي بالخلفية:", fontWeight = FontWeight.Bold, fontSize = 12.sp)

                        OutlinedButton(
                            onClick = {
                                val tpd = TimePickerDialog(
                                    context,
                                    { _, selectedHour, selectedMinute ->
                                        pdfScheduleHour = selectedHour
                                        pdfScheduleMinute = selectedMinute
                                        autoPdfBackupHour = selectedHour
                                        viewModel.scheduleClientStatementExport(
                                            enabled = true,
                                            frequency = pdfScheduleFrequency,
                                            hour = selectedHour,
                                            minute = selectedMinute,
                                            specificDate = pdfScheduleSpecificDate,
                                            dayOfWeek = pdfScheduleDayOfWeek,
                                            dayOfMonth = pdfScheduleDayOfMonth
                                        )
                                    },
                                    pdfScheduleHour,
                                    pdfScheduleMinute,
                                    false
                                )
                                tpd.show()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            val amPm = if (pdfScheduleHour < 12) "صباحاً" else "مساءً"
                            val displayHour = if (pdfScheduleHour % 12 == 0) 12 else pdfScheduleHour % 12
                            Text(
                                text = "الوقت المجدول: %02d:%02d %s (اضغط لتغيير الساعة والدقيقة)".format(Locale.US, displayHour, pdfScheduleMinute, amPm),
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                Text(
                                    "يعمل العامل بالخلفية تلقائياً ويصدّر كشوفات حسابات العملاء المحدثة (PDF) في الموعد المجدول بدقة إلى مجلد Downloads دون الحاجة لفتح التطبيق.",
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    // Immediate Manual Save Button
                    Button(
                        onClick = {
                            scope.launch {
                                val savedFile = viewModel.generateAndSaveAllClientsPdfNow()
                                if (savedFile != null) {
                                    Toast.makeText(
                                        context,
                                        "تم حفظ التقرير الشامل بنجاح!\nاسم الملف: نسخة كشوفات العملاء.pdf\nالمسار: Downloads",
                                        Toast.LENGTH_LONG
                                    ).show()
                                } else {
                                    Toast.makeText(context, "فشل حفظ ملف PDF", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("حفظ وتصدير ملف PDF لجميع العملاء الآن يدوياً", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        // 6. Loyalty and Payment Management Settings Section (ميزة إدارة السداد والعملاء الأوفياء - مخفية افتراضياً)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isLoyaltyExpanded = !isLoyaltyExpanded }
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stars,
                                contentDescription = null,
                                tint = Color(0xFFD97706)
                            )
                            Text(
                                text = "إدارة السداد والعملاء الأوفياء ⭐",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Icon(
                            imageVector = if (isLoyaltyExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (isLoyaltyExpanded) "إخفاء" else "عرض",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    AnimatedVisibility(visible = isLoyaltyExpanded) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "نظام ذكي لمتابعة وتصنيف سلوك السداد تلقائياً، إرسال تذكيرات التأخر، ورسائل الشكر والتقدير للعملاء الأوفياء.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Divider()

                            // 1. Overdue Threshold
                            OutlinedTextField(
                                value = overdueDaysThresholdText,
                                onValueChange = { overdueDaysThresholdText = it },
                                label = { Text("مهلة تأخر السداد (بالأيام)") },
                                supportingText = { Text("عدد الأيام بعد تاريخ الاستحقاق لتصنيف العميل '⚠️ متأخر بالسداد'") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.scrollToTopOnFocus().scrollToTopOnFocus().fillMaxWidth()
                            )

                            // 2. Fast Payer Threshold
                            OutlinedTextField(
                                value = fastPayerDaysThresholdText,
                                onValueChange = { fastPayerDaysThresholdText = it },
                                label = { Text("مهلة السداد السريع (بالأيام)") },
                                supportingText = { Text("السداد خلال هذه المدة يمنح العميل شارة '⚡ سريع السداد'") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.scrollToTopOnFocus().scrollToTopOnFocus().fillMaxWidth()
                            )

                            // 3. Loyalty Min Invoices Count
                            OutlinedTextField(
                                value = loyaltyMinInvoicesCountText,
                                onValueChange = { loyaltyMinInvoicesCountText = it },
                                label = { Text("الحد الأدنى لعدد الفواتير للوفاء") },
                                supportingText = { Text("عدد الفواتير المنتظمة المطلوبة لنيل تصنيف '⭐ عميل وفي'") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.scrollToTopOnFocus().scrollToTopOnFocus().fillMaxWidth()
                            )

                            // 4. Overdue Notice WhatsApp Template
                            OutlinedTextField(
                                value = overdueNoticeTemplate,
                                onValueChange = { overdueNoticeTemplate = it },
                                label = { Text("قالب رسالة تذكير المتأخرين بالسداد (واتساب)") },
                                supportingText = { Text("المتغيرات: {اسم_العميل}، {اسم_المتجر}، {المبلغ}، {العملة}، {أيام_التأخير}") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(130.dp)
                            )

                            // 5. Loyalty Appreciation WhatsApp Template
                            OutlinedTextField(
                                value = loyaltyAppreciationTemplate,
                                onValueChange = { loyaltyAppreciationTemplate = it },
                                label = { Text("قالب رسالة شكر وتقدير للعميل الوفي (واتساب)") },
                                supportingText = { Text("المتغيرات: {اسم_العميل}، {اسم_المتجر}، {عدد_الفواتير}") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(130.dp)
                            )
                        }
                    }
                }
            }
        }

        // 7. Installment Reminder Configuration (إعدادات تنبيهات الأقساط - مخفية افتراضياً)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isInstallmentRemindersExpanded = !isInstallmentRemindersExpanded },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Alarm, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column {
                                Text("إعدادات تنبيهات الأقساط", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = if (isInstallmentRemindersExpanded) "اضغط لإخفاء التفاصيل" else "مخفية افتراضياً (اضغط هنا لعرض تفاصيل ضبط التنبيهات)",
                                    fontSize = 11.sp,
                                    color = if (isInstallmentRemindersExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Icon(
                            imageVector = if (isInstallmentRemindersExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (isInstallmentRemindersExpanded) "إخفاء" else "عرض",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    AnimatedVisibility(visible = isInstallmentRemindersExpanded) {
                        Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            HorizontalDivider()

                            // Toggle General Reminders
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("تفعيل التنبيه العام للأقساط", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("إشعار ملخص يومي بالأقساط المستحقة والمتأخرة", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(
                                    checked = generalReminderEnabled,
                                    onCheckedChange = { generalReminderEnabled = it }
                                )
                            }

                            if (generalReminderEnabled) {
                                // Hour Picker
                                Text("وقت التنبيه العام اليومي:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                val reminderHourOptions = listOf(
                                    7 to "الساعة 07:00 صباحاً",
                                    8 to "الساعة 08:00 صباحاً",
                                    9 to "الساعة 09:00 صباحاً",
                                    10 to "الساعة 10:00 صباحاً",
                                    12 to "الساعة 12:00 ظهراً",
                                    14 to "الساعة 02:00 ظهراً",
                                    17 to "الساعة 05:00 مساءً",
                                    20 to "الساعة 08:00 مساءً"
                                )
                                var expandedReminderHour by remember { mutableStateOf(false) }
                                val reminderHourLabel = reminderHourOptions.find { it.first == generalReminderHour }?.second ?: "الساعة ${generalReminderHour}:00"

                                Box(modifier = Modifier.fillMaxWidth()) {
                                    OutlinedButton(
                                        onClick = { expandedReminderHour = true },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(reminderHourLabel, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.weight(1f))
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                    }

                                    DropdownMenu(
                                        expanded = expandedReminderHour,
                                        onDismissRequest = { expandedReminderHour = false },
                                        modifier = Modifier.fillMaxWidth(0.85f)
                                    ) {
                                        reminderHourOptions.forEach { (hr, label) ->
                                            DropdownMenuItem(
                                                text = { Text(label, fontWeight = if (generalReminderHour == hr) FontWeight.Bold else FontWeight.Normal) },
                                                onClick = {
                                                    generalReminderHour = hr
                                                    expandedReminderHour = false
                                                }
                                            )
                                        }
                                    }
                                }

                                // Scope selection: اليوم / القادمة / المتأخرة / الكل
                                Text("نطاق التنبيه:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                val scopes = listOf("الكل", "اليوم", "القادمة", "المتأخرة")
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    scopes.forEach { sc ->
                                        FilterChip(
                                            selected = generalReminderScope == sc,
                                            onClick = { generalReminderScope = sc },
                                            label = { Text(sc, fontSize = 12.sp) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }

                                // Days Before & Overdue Thresholds
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = generalReminderDaysBefore,
                                        onValueChange = { generalReminderDaysBefore = it },
                                        label = { Text("أيام قبل الاستحقاق") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.scrollToTopOnFocus().scrollToTopOnFocus().weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = generalReminderOverdueDays,
                                        onValueChange = { generalReminderOverdueDays = it },
                                        label = { Text("أيام التأخر للتنبيه") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.scrollToTopOnFocus().scrollToTopOnFocus().weight(1f)
                                    )
                                }
                            }

                            HorizontalDivider()

                            // Direct access to dedicated Alarm Profile settings page
                            OutlinedButton(
                                onClick = onNavigateToAlarmSettings,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("open_alarm_profile_from_settings_btn")
                            ) {
                                Icon(Icons.Default.Alarm, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("فتح صفحة إعدادات منبّه الأقساط (Alarm Profile)", fontWeight = FontWeight.Bold)
                            }

                            // Toggle Customer Specific Reminders
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("تفعيل التنبيهات المخصصة للعملاء", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("السماح بإنشاء ومتابعة تنبيهات مرتبطة بعميل وقسط محدد", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(
                                    checked = customerRemindersEnabled,
                                    onCheckedChange = { customerRemindersEnabled = it }
                                )
                            }
                        }
                    }
                }
            }
        }

        // 8. Submit Changes Action
        item {
            Button(
                onClick = {
                    if (storeName.isBlank()) {
                        Toast.makeText(context, "اسم المتجر مطلوب!", Toast.LENGTH_SHORT).show()
                    } else {
                        viewModel.updateStoreSettings(
                            settings.copy(
                                storeName = storeName,
                                storePhone = storePhone,
                                storeAddress = storeAddress,
                                storeEmail = storeEmail,
                                currency = currency,
                                invoicePrefix = invoicePrefix,
                                lastInvoiceNumber = lastInvoiceNumber.toIntOrNull() ?: settings.lastInvoiceNumber,
                                isAutoNumberingEnabled = isAutoNumberingEnabled,
                                lastPaymentNumber = lastPaymentNumber.toIntOrNull() ?: settings.lastPaymentNumber,
                                lastSalesReturnNumber = lastSalesReturnNumber.toIntOrNull() ?: settings.lastSalesReturnNumber,
                                lastPurchaseReturnNumber = lastPurchaseReturnNumber.toIntOrNull() ?: settings.lastPurchaseReturnNumber,
                                showVatAndSubtotal = showVatAndSubtotal,
                                enableInvoiceCurrencyConverter = enableInvoiceCurrencyConverter,
                                defaultInvoiceDisplayCurrency = defaultInvoiceDisplayCurrency,
                                enableUnclutteredInvoiceMode = enableUnclutteredInvoiceMode,
                                isAutoPdfBackupEnabled = isAutoPdfBackupEnabled,
                                autoPdfBackupHour = autoPdfBackupHour,
                                overdueDaysThreshold = overdueDaysThresholdText.toIntOrNull() ?: settings.overdueDaysThreshold,
                                fastPayerDaysThreshold = fastPayerDaysThresholdText.toIntOrNull() ?: settings.fastPayerDaysThreshold,
                                loyaltyMinInvoicesCount = loyaltyMinInvoicesCountText.toIntOrNull() ?: settings.loyaltyMinInvoicesCount,
                                overdueNoticeTemplate = overdueNoticeTemplate.ifBlank { settings.overdueNoticeTemplate },
                                loyaltyAppreciationTemplate = loyaltyAppreciationTemplate.ifBlank { settings.loyaltyAppreciationTemplate },
                                isGeneralInstallmentReminderEnabled = generalReminderEnabled,
                                generalReminderHour = generalReminderHour,
                                generalReminderDaysBeforeDue = generalReminderDaysBefore.toIntOrNull() ?: settings.generalReminderDaysBeforeDue,
                                generalReminderDaysAfterOverdue = generalReminderOverdueDays.toIntOrNull() ?: settings.generalReminderDaysAfterOverdue,
                                generalReminderScope = generalReminderScope,
                                isCustomerInstallmentReminderEnabled = customerRemindersEnabled
                            )
                        )
                        Toast.makeText(context, "تم حفظ وتحديث جميع الإعدادات بنجاح!", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("حفظ جميع الإعدادات", fontWeight = FontWeight.Bold)
            }
        }
    }

    // Passcode/PIN Dialog Setup
    if (showPinSetupDialog) {
        var pinCode by remember { mutableStateOf("") }
        var pinError by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showPinSetupDialog = false },
            title = { Text("إعداد رقم PIN سري للحماية") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("أدخل رمزاً مكوناً من 4 أرقام لتأمين تطبيق حسابات العملاء برو:", fontSize = 12.sp)
                    OutlinedTextField(
                        value = pinCode,
                        onValueChange = {
                            if (it.length <= 4 && it.all { char -> char.isDigit() }) {
                                pinCode = it
                                pinError = false
                            }
                        },
                        label = { Text("رمز القفل PIN") },
                        isError = pinError,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.scrollToTopOnFocus().scrollToTopOnFocus().fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (pinCode.length == 4) {
                            viewModel.setSecurityPin(pinCode)
                            showPinSetupDialog = false
                            Toast.makeText(context, "تم تفعيل القفل برمز PIN بنجاح!", Toast.LENGTH_SHORT).show()
                        } else {
                            pinError = true
                        }
                    }
                ) {
                    Text("تفعيل الحماية")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPinSetupDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // Security Audit Logs Dialog Display
    if (showAuditLogsDialog) {
        AlertDialog(
            onDismissRequest = { showAuditLogsDialog = false },
            title = { Text("سجل العمليات وحماية النظام (Audit Log)", fontWeight = FontWeight.Bold) },
            text = {
                Box(modifier = Modifier.sizeIn(maxHeight = 400.dp)) {
                    if (auditLogs.isEmpty()) {
                        Text("لا يوجد سجل نشاطات حالياً.")
                    } else {
                        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(auditLogs) { log ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color.Gray.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                                        .padding(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(log.operationType, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
                                        Text(dateFormat.format(Date(log.timestamp)), fontSize = 9.sp, color = Color.Gray)
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text("الجدول: ${log.tableName}", fontWeight = FontWeight.Medium, fontSize = 10.sp, color = Color.DarkGray)
                                    Text(log.details, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showAuditLogsDialog = false }) {
                    Text("إغلاق")
                }
            }
        )
    }

    // Google Sign In & Drive Permissions Dialog
    if (showGoogleSignInDialog) {
        AlertDialog(
            onDismissRequest = { showGoogleSignInDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.CloudSync, contentDescription = null, tint = Color(0xFF4285F4))
                    Text("تسجيل الدخول وإذونات Google Drive", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "لتفعيل المزامنة التلقائية بملفات JSON الحية، يرجى تسجيل الدخول إلى حساب Google الخاص بك ومنح التطبيق إذن قراءة وحفظ النسخة الاحتياطية بـ Google Drive:",
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                    OutlinedTextField(
                        value = userDriveEmailInput,
                        onValueChange = { userDriveEmailInput = it },
                        label = { Text("البريد الإلكتروني لحساب Google") },
                        modifier = Modifier.scrollToTopOnFocus().scrollToTopOnFocus().fillMaxWidth(),
                        singleLine = true
                    )
                    Surface(
                        color = Color(0xFF4285F4).copy(alpha = 0.1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4285F4), modifier = Modifier.size(16.dp))
                            Text("سيتم إنشاء وتحديث ملف JSON باسم 'debt_app_clients_backup.json' تلقائياً.", fontSize = 10.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (userDriveEmailInput.isNotBlank()) {
                            viewModel.toggleDriveSync(true, userDriveEmailInput.trim())
                            showGoogleSignInDialog = false
                            Toast.makeText(context, "تم تسجيل الدخول وتفعيل المزامنة مع Google Drive بنجاح!", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, "يرجى إدخال بريد إلكتروني صالح", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4285F4))
                ) {
                    Icon(Icons.Default.Login, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("موافقة وتسجيل الدخول")
                }
            },
            dismissButton = {
                TextButton(onClick = { showGoogleSignInDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // Google Drive Restore Confirmation Dialog
    if (showRestoreConfirmDialog) {
        AlertDialog(
            onDismissRequest = { if (!isRestoringData) showRestoreConfirmDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null, tint = Color(0xFF059669))
                    Text("استعادة بيانات العملاء من Google Drive", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "هل ترغب في استعادة جميع بيانات العملاء، الفواتير، المقبوضات والأقساط من ملف JSON المحفوظ في Google Drive؟\n\nسيتم قراءة جميع الملفات واستخراج البيانات وتنسيقها داخل التطبيق لاستعادة حساباتك بالكامل.",
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                    if (isRestoringData) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color(0xFF059669))
                            Text("جاري استعادة وقراءة البيانات من ملفات JSON...", fontSize = 11.sp, color = Color(0xFF059669), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isRestoringData = true
                        viewModel.restoreGoogleDriveBackup(
                            onSuccess = { msg ->
                                isRestoringData = false
                                showRestoreConfirmDialog = false
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            },
                            onError = { err ->
                                isRestoringData = false
                                Toast.makeText(context, "خطأ في الاستعادة: $err", Toast.LENGTH_LONG).show()
                            }
                        )
                    },
                    enabled = !isRestoringData,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669))
                ) {
                    Icon(Icons.Default.Restore, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("استعادة البيانات الآن")
                }
            },
            dismissButton = {
                if (!isRestoringData) {
                    TextButton(onClick = { showRestoreConfirmDialog = false }) {
                        Text("إلغاء")
                    }
                }
            }
        )
    }

    // Modal List of JSON Backup Files in Accountant Backup Folder
    if (showBackupFilesListDialog) {
        AlertDialog(
            onDismissRequest = { showBackupFilesListDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Folder, contentDescription = null, tint = Color(0xFF4285F4))
                    Text("ملفات النسخ بـ Accountant Backup", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Box(modifier = Modifier.sizeIn(maxHeight = 380.dp)) {
                    if (availableBackupFiles.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.CloudOff, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(36.dp))
                            Text("لم يتم العثور على أية ملفات نسخ احتياطية في مجلد Accountant Backup حالياً.", fontSize = 12.sp, color = Color.Gray)
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(availableBackupFiles) { fileInfo ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Icon(Icons.Default.DataObject, contentDescription = null, tint = Color(0xFF059669), modifier = Modifier.size(16.dp))
                                                Text(fileInfo.fileName, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            }
                                            Text(fileInfo.fileSizeFormatted, fontSize = 10.sp, color = Color.Gray)
                                        }

                                        Text("التاريخ: ${fileInfo.backupDate}", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                                        Text(
                                            "المحتوى: ${fileInfo.clientCount} عميل | ${fileInfo.invoiceCount} فاتورة | ${fileInfo.paymentCount} دفعة",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium
                                        )

                                        Button(
                                            onClick = {
                                                viewModel.restoreBackupFromFile(
                                                    fileInfo,
                                                    onSuccess = { msg ->
                                                        showBackupFilesListDialog = false
                                                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                                    },
                                                    onError = { err ->
                                                        Toast.makeText(context, "خطأ: $err", Toast.LENGTH_LONG).show()
                                                    }
                                                )
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("استعادة البيانات من هذا الملف", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showBackupFilesListDialog = false }) {
                    Text("إغلاق")
                }
            }
        )
    }

    if (showBiometricSetupDialog) {
        var newUserName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showBiometricSetupDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Fingerprint, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("تفعيل البصمة وتسجيل المستخدم", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "لإتمام تفعيل البصمة لأول مرة، الرجاء إدخال اسمك لربطه بالمصادقة (سيظهر عند قفل التطبيق):",
                        fontSize = 13.sp
                    )
                    OutlinedTextField(
                        value = newUserName,
                        onValueChange = { newUserName = it },
                        label = { Text("اسم المستخدم (مثال: محمد، المدير)") },
                        modifier = Modifier.scrollToTopOnFocus().scrollToTopOnFocus().fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newUserName.isNotBlank()) {
                            viewModel.setBiometricSettings(true, newUserName)
                            Toast.makeText(context, "تم تفعيل قفل البصمة بنجاح!", Toast.LENGTH_SHORT).show()
                            showBiometricSetupDialog = false
                        } else {
                            Toast.makeText(context, "الرجاء إدخال الاسم أولاً", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("حفظ وتفعيل البصمة")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBiometricSetupDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    if (showEditBiometricUserDialog) {
        var editUserName by remember { mutableStateOf(biometricUserName) }
        AlertDialog(
            onDismissRequest = { showEditBiometricUserDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("تعديل اسم مستخدم البصمة", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("قم بتحديث اسم المستخدم المرتبط ببصمة الأمان:", fontSize = 13.sp)
                    OutlinedTextField(
                        value = editUserName,
                        onValueChange = { editUserName = it },
                        label = { Text("الاسم الجديد") },
                        modifier = Modifier.scrollToTopOnFocus().scrollToTopOnFocus().fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editUserName.isNotBlank()) {
                            viewModel.updateBiometricUserName(editUserName)
                            Toast.makeText(context, "تم تحديث الاسم بنجاح", Toast.LENGTH_SHORT).show()
                            showEditBiometricUserDialog = false
                        }
                    }
                ) {
                    Text("تحديث")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditBiometricUserDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }
}
