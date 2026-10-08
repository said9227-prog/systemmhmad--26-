package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.util.scrollToTopOnFocus
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Client
import com.example.ui.viewmodel.AppViewModel
import com.example.util.FormatUtils
import com.example.util.PaymentLoyaltyUtils
import com.example.util.ShareManager
import com.example.util.WhatsAppHelper
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerProfileScreen(
    viewModel: AppViewModel,
    clientId: Int,
    onNavigateBack: () -> Unit,
    onNavigateToCreateInvoice: (Int) -> Unit,
    onNavigateToStatement: (Int) -> Unit
) {
    val context = LocalContext.current
    val clientsList by viewModel.clients.collectAsState()
    val invoicesList by viewModel.invoices.collectAsState()
    val paymentsList by viewModel.payments.collectAsState()
    val installmentsList by viewModel.installments.collectAsState()
    val settings by viewModel.storeSettings.collectAsState()
    val exchangeRates by viewModel.exchangeRates.collectAsState()

    val client = clientsList.find { it.id == clientId }
    val clientInvoices = remember(invoicesList, clientId) {
        invoicesList.filter { it.clientId == clientId && !it.isDraft }
    }
    val clientPayments = remember(paymentsList, clientId) {
        paymentsList.filter { it.clientId == clientId }
    }

    var showPaymentDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showCustomerReminderDialog by remember { mutableStateOf(false) }

    if (client == null) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("ملف العميل") },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                        }
                    }
                )
            }
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("لم يتم العثور على العميل", style = MaterialTheme.typography.bodyLarge)
            }
        }
        return
    }

    val loyaltyProfile = remember(client, invoicesList, paymentsList, installmentsList, settings) {
        PaymentLoyaltyUtils.analyzeClientBehavior(
            client = client,
            allInvoices = invoicesList,
            allPayments = paymentsList,
            allInstallments = installmentsList,
            settings = settings
        )
    }

    // Financial Metrics Calculation
    val totalSales = clientInvoices.sumOf { it.totalAmount }
    val totalPayments = clientPayments.sumOf { it.amount }
    val balance = client.balance
    val isDebit = balance > 0.001
    val isCredit = balance < -0.001
    val isZero = !isDebit && !isCredit
    val clientCurrency = client.currency.ifBlank { settings.currency.ifBlank { "الريال اليمني" } }

    val lastActivityTimestamp = remember(clientInvoices, clientPayments) {
        val lastInvoiceDate = clientInvoices.maxOfOrNull { it.date } ?: 0L
        val lastPaymentDate = clientPayments.maxOfOrNull { it.date } ?: 0L
        maxOf(lastInvoiceDate, lastPaymentDate)
    }

    val dateFormatter = SimpleDateFormat("dd/MM/yyyy", Locale("ar"))
    val lastActivityText = if (lastActivityTimestamp > 0) dateFormatter.format(Date(lastActivityTimestamp)) else "لا يوجد"

    val customerIdDisplay = if (client.customerId.isNotBlank()) client.customerId else "CUS-${client.id.toString().padStart(5, '0')}"

    val dueDateText = remember(client) {
        if (client.defaultDueDateDays > 0) {
            val cal = Calendar.getInstance()
            cal.timeInMillis = if (client.createdAt > 0) client.createdAt else System.currentTimeMillis()
            cal.add(Calendar.DAY_OF_YEAR, client.defaultDueDateDays)
            SimpleDateFormat("dd MMMM yyyy", Locale("ar")).format(cal.time)
        } else {
            "عند الطلب"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = client.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            maxLines = 1
                        )
                        Text(
                            text = customerIdDisplay,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        ShareManager.shareCustomerProfile(
                            context = context,
                            client = client,
                            balance = balance,
                            invoiceCount = clientInvoices.size,
                            totalSales = totalSales,
                            totalPayments = totalPayments,
                            settings = settings
                        )
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "مشاركة بيانات العميل")
                    }
                    if (client.phone.isNotBlank()) {
                        IconButton(onClick = {
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${client.phone}"))
                            context.startActivity(intent)
                        }) {
                            Icon(Icons.Default.Phone, contentDescription = "اتصال")
                        }
                    }
                    IconButton(onClick = { showEditDialog = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "تعديل")
                    }
                    Box {
                        IconButton(onClick = { showMoreMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "المزيد")
                        }
                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("📤 مشاركة بيانات العميل") },
                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                onClick = {
                                    showMoreMenu = false
                                    ShareManager.shareCustomerProfile(
                                        context = context,
                                        client = client,
                                        balance = balance,
                                        invoiceCount = clientInvoices.size,
                                        totalSales = totalSales,
                                        totalPayments = totalPayments,
                                        settings = settings
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("نسخ رقم العميل") },
                                leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                                onClick = {
                                    showMoreMenu = false
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Customer ID", customerIdDisplay))
                                    Toast.makeText(context, "تم نسخ رقم العميل: $customerIdDisplay", Toast.LENGTH_SHORT).show()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (client.isPinned) "إلغاء التثبيت" else "تثبيت في الأعلى") },
                                leadingIcon = { Icon(if (client.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin, contentDescription = null) },
                                onClick = {
                                    showMoreMenu = false
                                    viewModel.toggleClientPin(client)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("تنبيه قسط للعميل") },
                                leadingIcon = { Icon(Icons.Default.Alarm, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                onClick = {
                                    showMoreMenu = false
                                    showCustomerReminderDialog = true
                                }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("حذف العميل", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showMoreMenu = false
                                    showDeleteConfirmDialog = true
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 💰 1. FINANCIAL SUMMARY CARD (Main Header)
            FinancialStatusCard(
                balance = balance,
                isDebit = isDebit,
                isCredit = isCredit,
                isZero = isZero,
                currency = clientCurrency
            )

            // 📊 2. FINANCIAL METRICS GRID
            FinancialMetricsGrid(
                initialBalance = client.initialBalance,
                initialBalanceType = client.balanceType,
                totalSales = totalSales,
                totalPayments = totalPayments,
                balance = balance,
                invoiceCount = clientInvoices.size,
                lastActivityDate = lastActivityText,
                currency = clientCurrency
            )

            // ⭐ LOYALTY & PAYMENT BEHAVIOR CARD
            LoyaltyBehaviorCard(
                loyaltyProfile = loyaltyProfile,
                onSendReminder = {
                    if (loyaltyProfile.isOverdue) {
                        WhatsAppHelper.sendWhatsAppMessage(
                            context = context,
                            phone = client.phone,
                            messageText = loyaltyProfile.overdueNoticeMessage
                        )
                    } else if (loyaltyProfile.isLoyal) {
                        WhatsAppHelper.sendWhatsAppMessage(
                            context = context,
                            phone = client.phone,
                            messageText = loyaltyProfile.loyaltyAppreciationMessage
                        )
                    }
                }
            )

            // ⚡ 3. QUICK ACTIONS BAR
            QuickActionsBar(
                onRecordPayment = { showPaymentDialog = true },
                onOpenStatement = { onNavigateToStatement(client.id) },
                onNewInvoice = { onNavigateToCreateInvoice(client.id) },
                onOpenTransactions = { onNavigateToStatement(client.id) },
                onEditClient = { showEditDialog = true }
            )

            // 👤 4. SECTION: BASIC INFORMATION (بيانات العميل)
            ProfileSectionCard(title = "👤 بيانات العميل") {
                ProfileInfoRow(icon = Icons.Default.Person, label = "اسم العميل", value = client.name)
                ProfileInfoRow(icon = Icons.Default.Badge, label = "رقم العميل", value = customerIdDisplay)
                
                if (client.phone.isNotBlank()) {
                    ProfileInfoRow(
                        icon = Icons.Default.Phone,
                        label = "رقم الهاتف",
                        value = client.phone,
                        actionIcon = Icons.Default.Call,
                        onAction = {
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${client.phone}"))
                            context.startActivity(intent)
                        }
                    )
                }

                if (client.altPhone.isNotBlank()) {
                    ProfileInfoRow(
                        icon = Icons.Default.PhoneIphone,
                        label = "رقم هاتف إضافي",
                        value = client.altPhone,
                        actionIcon = Icons.Default.Call,
                        onAction = {
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${client.altPhone}"))
                            context.startActivity(intent)
                        }
                    )
                }

                if (client.companyName.isNotBlank()) {
                    ProfileInfoRow(icon = Icons.Default.Business, label = "الشركة / المؤسسة", value = client.companyName)
                }

                if (client.city.isNotBlank() || client.address.isNotBlank()) {
                    val locationText = listOf(client.city, client.address).filter { it.isNotBlank() }.joinToString(" - ")
                    ProfileInfoRow(icon = Icons.Default.LocationOn, label = "العنوان والمنطقة", value = locationText)
                }

                if (client.email.isNotBlank()) {
                    ProfileInfoRow(icon = Icons.Default.Email, label = "البريد الإلكتروني", value = client.email)
                }

                if (client.taxNumber.isNotBlank()) {
                    ProfileInfoRow(icon = Icons.Default.ReceiptLong, label = "الرقم الضريبي", value = client.taxNumber)
                }

                ProfileInfoRow(icon = Icons.Default.Category, label = "نوع العميل", value = client.clientType.ifBlank { "فرد" })
                ProfileInfoRow(icon = Icons.Default.Star, label = "تصنيف العميل", value = client.classification.ifBlank { "جديد" })

                if (client.notes.isNotBlank()) {
                    ProfileInfoRow(icon = Icons.Default.Notes, label = "الملاحظات", value = client.notes)
                }
            }

            // 📈 5. SECTION: ACCOUNT INFORMATION (بيانات الحساب)
            ProfileSectionCard(title = "📊 بيانات الحساب والشروط") {
                ProfileInfoRow(
                    icon = Icons.Default.CreditCard,
                    label = "نوع التعامل",
                    value = client.dealType.ifBlank { "نقدي" }
                )

                val initBalCurr = client.initialBalanceCurrency.ifBlank { clientCurrency }
                val initialBalText = "${FormatUtils.formatWithCurrency(client.initialBalance, initBalCurr)} (${if (client.balanceType == "دائن") "دائن" else "مدين"})"
                ProfileInfoRow(
                    icon = Icons.Default.AccountBalance,
                    label = "الرصيد الافتتاحي",
                    value = initialBalText
                )

                val currentBalStatusText = if (isDebit) "مدين" else if (isCredit) "دائن" else "متعادل"
                ProfileInfoRow(
                    icon = Icons.Default.AccountBalanceWallet,
                    label = "الرصيد الحالي",
                    value = "${FormatUtils.formatWithCurrency(abs(balance), clientCurrency)} ($currentBalStatusText)"
                )

                if (client.dealType == "آجل" || client.creditLimit > 0) {
                    val limitText = if (client.creditLimit > 0) FormatUtils.formatWithCurrency(client.creditLimit, clientCurrency) else "غير مقيد"
                    ProfileInfoRow(
                        icon = Icons.Default.Security,
                        label = "حد الائتمان",
                        value = limitText
                    )

                    ProfileInfoRow(
                        icon = Icons.Default.Timer,
                        label = "مدة السداد",
                        value = client.paymentPeriod.ifBlank { "عند الطلب" }
                    )

                    if (client.defaultDueDateDays > 0) {
                        ProfileInfoRow(
                            icon = Icons.Default.CalendarToday,
                            label = "تاريخ الاستحقاق",
                            value = dueDateText
                        )
                    }

                    // Credit Limit Consumption Bar
                    if (client.creditLimit > 0) {
                        val usageRatio = (balance / client.creditLimit).coerceIn(0.0, 1.0).toFloat()
                        val usagePercent = ((balance / client.creditLimit) * 100).toInt()
                        
                        Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("نسبة استخدام الحد الائتماني:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("$usagePercent%", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { usageRatio },
                                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                                color = if (usagePercent > 90) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Quick Payment Dialog
    if (showPaymentDialog) {
        QuickPaymentDialog(
            client = client,
            currency = clientCurrency,
            rates = exchangeRates,
            isAutoNumberingEnabled = settings.isAutoNumberingEnabled,
            defaultVoucherNumber = if (settings.lastPaymentNumber == 0) "1" else "${settings.lastPaymentNumber + 1}",
            onDismiss = { showPaymentDialog = false },
            onConfirm = { amount, method, note, receiptNum, payCurrency, customRate ->
                viewModel.addPayment(
                    clientId = client.id,
                    amount = amount,
                    paymentMethod = method,
                    notes = note,
                    voucherNumber = receiptNum,
                    currency = payCurrency,
                    customRate = customRate
                )
                showPaymentDialog = false
                Toast.makeText(context, "تم تسجيل سند القبض بنجاح", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Edit Client Dialog
    if (showEditDialog) {
        EditClientDialog(
            client = client,
            onDismiss = { showEditDialog = false },
            onConfirm = { updatedClient ->
                viewModel.updateClient(updatedClient)
                showEditDialog = false
                Toast.makeText(context, "تم تحديث بيانات العميل بنجاح", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Customer Installment Reminder Dialog
    if (showCustomerReminderDialog) {
        val clientInstList = remember(installmentsList, client.id) {
            installmentsList.filter { it.clientId == client.id }
        }
        com.example.ui.components.CreateCustomerReminderDialog(
            customer = client,
            customerInvoices = clientInvoices,
            customerInstallments = clientInstList,
            onDismiss = { showCustomerReminderDialog = false },
            onSave = { customerId, customerName, invoiceId, invoiceNumber, installmentId, amount, remainingAmount, dueDate, scheduledAt, currency ->
                showCustomerReminderDialog = false
                viewModel.createCustomerReminder(
                    customerId = customerId,
                    customerName = customerName,
                    invoiceId = invoiceId,
                    invoiceNumber = invoiceNumber,
                    installmentId = installmentId,
                    amount = amount,
                    remainingAmount = remainingAmount,
                    dueDate = dueDate,
                    scheduledAt = scheduledAt,
                    currency = currency
                )
                Toast.makeText(context, "تم حفظ وجدولة تنبيه القسط للعميل بنجاح", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Delete Client Confirmation Dialog
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("حذف العميل", fontWeight = FontWeight.Bold) },
            text = { Text("هل أنت متأكد من رغبتك في حذف العميل '${client.name}'؟ سيتم حذف جميع بياناته نهائياً.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteClient(client)
                        showDeleteConfirmDialog = false
                        Toast.makeText(context, "تم حذف العميل بنجاح", Toast.LENGTH_SHORT).show()
                        onNavigateBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("حذف", fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("إلغاء", maxLines = 1, softWrap = false)
                }
            }
        )
    }
}

// -------------------------------------------------------------
// COMPONENT: FINANCIAL STATUS CARD
// -------------------------------------------------------------
@Composable
fun FinancialStatusCard(
    balance: Double,
    isDebit: Boolean,
    isCredit: Boolean,
    isZero: Boolean,
    currency: String
) {
    val statusLabel = when {
        isDebit -> "الرصيد المدين"
        isCredit -> "الرصيد الدائن"
        else -> "الرصيد المتعادل"
    }

    val statusBadgeText = when {
        isDebit -> "مدين"
        isCredit -> "دائن"
        else -> "الرصيد متعادل"
    }

    val badgeColor = when {
        isDebit -> MaterialTheme.colorScheme.errorContainer
        isCredit -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    val badgeTextColor = when {
        isDebit -> MaterialTheme.colorScheme.onErrorContainer
        isCredit -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AccountBalanceWallet,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "الرصيد الحالي",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = badgeColor
                ) {
                    Text(
                        text = statusBadgeText,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = badgeTextColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = FormatUtils.formatWithCurrency(abs(balance), currency),
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = statusLabel,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// -------------------------------------------------------------
// COMPONENT: FINANCIAL METRICS GRID
// -------------------------------------------------------------
@Composable
fun FinancialMetricsGrid(
    initialBalance: Double,
    initialBalanceType: String,
    totalSales: Double,
    totalPayments: Double,
    balance: Double,
    invoiceCount: Int,
    lastActivityDate: String,
    currency: String
) {
    val initialLabel = if (initialBalanceType == "دائن") "دائن" else "مدين"
    val outstandingAmount = if (balance > 0) balance else 0.0

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetricCard(
                title = "الرصيد الافتتاحي",
                value = "${FormatUtils.formatAmount(initialBalance)} $currency",
                subtitle = initialLabel,
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                title = "إجمالي المبيعات",
                value = "${FormatUtils.formatAmount(totalSales)} $currency",
                subtitle = "المشتريات",
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetricCard(
                title = "إجمالي المدفوعات",
                value = "${FormatUtils.formatAmount(totalPayments)} $currency",
                subtitle = "المسدد",
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                title = "إجمالي المستحق",
                value = "${FormatUtils.formatAmount(outstandingAmount)} $currency",
                subtitle = if (balance > 0) "مطلوب سداده" else "مسدد بالكامل",
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetricCard(
                title = "عدد الفواتير",
                value = "$invoiceCount",
                subtitle = "فاتورة",
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                title = "آخر حركة مالية",
                value = lastActivityDate,
                subtitle = "تاريخ آخر عملية",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun MetricCard(
    title: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 11.sp
            )
        }
    }
}

// -------------------------------------------------------------
// COMPONENT: QUICK ACTIONS BAR
// -------------------------------------------------------------
@Composable
fun QuickActionsBar(
    onRecordPayment: () -> Unit,
    onOpenStatement: () -> Unit,
    onNewInvoice: () -> Unit,
    onOpenTransactions: () -> Unit,
    onEditClient: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                text = "الإجراءات السريعة",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickActionButton(
                    icon = Icons.Default.CreditCard,
                    label = "سداد",
                    onClick = onRecordPayment,
                    modifier = Modifier.weight(1f),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
                QuickActionButton(
                    icon = Icons.Default.Description,
                    label = "كشف الحساب",
                    onClick = onOpenStatement,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                QuickActionButton(
                    icon = Icons.Default.Receipt,
                    label = "فاتورة جديدة",
                    onClick = onNewInvoice,
                    modifier = Modifier.weight(1.15f)
                )
                QuickActionButton(
                    icon = Icons.Default.Paid,
                    label = "الحركات المالية",
                    onClick = onOpenTransactions,
                    modifier = Modifier.weight(1.15f)
                )
                QuickActionButton(
                    icon = Icons.Default.Edit,
                    label = "تعديل",
                    onClick = onEditClient,
                    modifier = Modifier.weight(0.7f)
                )
            }
        }
    }
}

@Composable
fun QuickActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(46.dp),
        shape = RoundedCornerShape(10.dp),
        color = containerColor,
        contentColor = contentColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// -------------------------------------------------------------
// COMPONENT: PROFILE SECTION CARD & ROW
// -------------------------------------------------------------
@Composable
fun ProfileSectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            content()
        }
    }
}

@Composable
fun ProfileInfoRow(
    icon: ImageVector,
    label: String,
    value: String,
    actionIcon: ImageVector? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.End
            )
            if (actionIcon != null && onAction != null) {
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = onAction,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        actionIcon,
                        contentDescription = label,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// DIALOG: QUICK PAYMENT DIALOG
// -------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickPaymentDialog(
    client: Client,
    currency: String,
    rates: List<com.example.data.model.ExchangeRate> = emptyList(),
    isAutoNumberingEnabled: Boolean = false,
    defaultVoucherNumber: String = "",
    onDismiss: () -> Unit,
    onConfirm: (amount: Double, paymentMethod: String, notes: String, receiptNumber: String, payCurrency: String, customRate: Double?) -> Unit
) {
    var selectedPayCurrency by remember { mutableStateOf(currency.ifBlank { "الريال اليمني" }) }
    var amountStr by remember { mutableStateOf(if (client.balance > 0) FormatUtils.formatPlain(client.balance) else "") }
    var customRateStr by remember { mutableStateOf("") }
    var paymentMethod by remember { mutableStateOf("نقدي") }
    var notes by remember { mutableStateOf("") }
    var receiptNumber by remember(isAutoNumberingEnabled) {
        mutableStateOf(if (isAutoNumberingEnabled) defaultVoucherNumber else "")
    }
    var amountError by remember { mutableStateOf(false) }

    val methods = listOf("نقدي", "تحويل بنكي", "شيك")
    val currenciesList = listOf("الريال اليمني", "الريال السعودي", "الدولار الأمريكي")

    val customRateDouble = customRateStr.toDoubleOrNull()
    val isMultiCurrency = selectedPayCurrency != currency
    val conversionResult = remember(amountStr, selectedPayCurrency, currency, rates, customRateDouble) {
        val amt = amountStr.toDoubleOrNull() ?: 0.0
        if (amt > 0.0 && isMultiCurrency) {
            try {
                com.example.util.MonetaryUtils.convert(
                    amount = amt,
                    fromCurrency = selectedPayCurrency,
                    toCurrency = currency,
                    rates = rates,
                    customOverrideRate = customRateDouble
                )
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(0.94f).widthIn(max = 520.dp),
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        title = {
            Column {
                Text("سند قبض جديد (سداد)", fontWeight = FontWeight.Bold)
                Text(
                    text = "للعميل: ${client.name} (عملة الحساب: $currency)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Current Balance Indicator
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("الرصيد الحالي للعميل:", style = MaterialTheme.typography.bodySmall)
                        Text(
                            text = "${FormatUtils.formatWithCurrency(abs(client.balance), currency)} (${if (client.balance > 0) "مدين" else if (client.balance < 0) "دائن" else "متعادل"})",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                // Currency and Amount Selector Row
                var expandedPayCurr by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = amountStr,
                        onValueChange = { amountStr = it; amountError = false },
                        label = { Text("المبلغ المدفوع *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1.3f).scrollToTopOnFocus(),
                        isError = amountError,
                        singleLine = true,
                        maxLines = 1,
                        shape = RoundedCornerShape(10.dp)
                    )

                    ExposedDropdownMenuBox(
                        expanded = expandedPayCurr,
                        onExpandedChange = { expandedPayCurr = !expandedPayCurr },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = selectedPayCurrency,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("عملة الدفع") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedPayCurr) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = expandedPayCurr,
                            onDismissRequest = { expandedPayCurr = false }
                        ) {
                            currenciesList.forEach { currOption ->
                                DropdownMenuItem(
                                    text = { Text(currOption) },
                                    onClick = {
                                        selectedPayCurrency = currOption
                                        expandedPayCurr = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Currency Exchange info when paying in a different currency
                if (isMultiCurrency) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.CurrencyExchange, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(16.dp))
                                Text("مصارفة العملة إلى عملة الحساب ($currency)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.tertiary)
                            }

                            if (conversionResult != null) {
                                val rateUsed = conversionResult.exchangeRate
                                Text(
                                    text = "سعر الصرف المعتمد: 1 $selectedPayCurrency = ${FormatUtils.formatPlain(rateUsed)} $currency",
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "المعادل المقيد في حساب العميل: ${FormatUtils.formatPlain(conversionResult.convertedAmount)} $currency",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF10B981)
                                )
                            } else {
                                Text(
                                    text = "يرجى إدخال سعر الصرف اليدوي لتحويل المبلغ من $selectedPayCurrency إلى $currency:",
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }

                            OutlinedTextField(
                                value = customRateStr,
                                onValueChange = { customRateStr = it },
                                label = { Text("سعر صرف مخصص (اختياري / يدوي)") },
                                placeholder = { Text("مثال: 400 أو 1500") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.fillMaxWidth().scrollToTopOnFocus(),
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }
                }

                // Quick Amount Chips
                if (client.balance > 0 && selectedPayCurrency == currency) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AssistChip(
                            onClick = { amountStr = FormatUtils.formatPlain(client.balance) },
                            label = { Text("كامل الرصيد") },
                            modifier = Modifier.weight(1f)
                        )
                        AssistChip(
                            onClick = { amountStr = FormatUtils.formatPlain(client.balance / 2) },
                            label = { Text("نصف الرصيد") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Text("طريقة الدفع", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    methods.forEach { method ->
                        FilterChip(
                            selected = paymentMethod == method,
                            onClick = { paymentMethod = method },
                            label = { Text(method) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                OutlinedTextField(
                    value = receiptNumber,
                    onValueChange = { receiptNumber = it },
                    label = { Text("رقم السند" + if (!isAutoNumberingEnabled) " (تسجيل يدوي)" else "") },
                    placeholder = { Text("أدخل رقم السند يدوياً...") },
                    modifier = Modifier.scrollToTopOnFocus().fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp)
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("ملاحظات") },
                    modifier = Modifier.scrollToTopOnFocus().fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
            }
        },
        confirmButton = {
            val context = LocalContext.current
            Button(
                onClick = {
                    val amount = amountStr.toDoubleOrNull()
                    if (amount == null || amount <= 0) {
                        amountError = true
                        Toast.makeText(context, "يجب تعبئة البيانات الإجبارية (المبلغ المدفوع)", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (isMultiCurrency && conversionResult == null && (customRateDouble == null || customRateDouble <= 0.0)) {
                        Toast.makeText(context, "يرجى تحديد سعر صرف صحيح للتحويل بين العملات", Toast.LENGTH_LONG).show()
                        return@Button
                    }
                    onConfirm(amount, paymentMethod, notes, receiptNumber, selectedPayCurrency, customRateDouble)
                }
            ) {
                Text("حفظ السند", fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("إلغاء", maxLines = 1, softWrap = false)
            }
        }
    )
}

// -------------------------------------------------------------
// DIALOG: EDIT CLIENT DIALOG
// -------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditClientDialog(
    client: Client,
    onDismiss: () -> Unit,
    onConfirm: (Client) -> Unit
) {
    var name by remember { mutableStateOf(client.name) }
    var phone by remember { mutableStateOf(client.phone) }
    var altPhone by remember { mutableStateOf(client.altPhone) }
    var companyName by remember { mutableStateOf(client.companyName) }
    var city by remember { mutableStateOf(client.city) }
    var address by remember { mutableStateOf(client.address) }
    var email by remember { mutableStateOf(client.email) }
    var taxNumber by remember { mutableStateOf(client.taxNumber) }
    var dealType by remember { mutableStateOf(client.dealType) }
    var initialBalanceStr by remember { mutableStateOf(FormatUtils.formatPlain(client.initialBalance)) }
    var balanceType by remember { mutableStateOf(if (client.balanceType == "دائن") "دائن" else "مدين") }
    var creditLimitStr by remember { mutableStateOf(if (client.creditLimit > 0) FormatUtils.formatPlain(client.creditLimit) else "") }
    var paymentPeriod by remember { mutableStateOf(client.paymentPeriod) }
    var clientType by remember { mutableStateOf(client.clientType) }
    var classification by remember { mutableStateOf(client.classification) }
    var notes by remember { mutableStateOf(client.notes) }
    var nameError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تعديل بيانات العميل", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; nameError = false },
                    label = { Text("اسم العميل *") },
                    isError = nameError,
                    modifier = Modifier.scrollToTopOnFocus().scrollToTopOnFocus().fillMaxWidth(),
                    singleLine = true
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("الهاتف") },
                        modifier = Modifier.scrollToTopOnFocus().scrollToTopOnFocus().weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = altPhone,
                        onValueChange = { altPhone = it },
                        label = { Text("هاتف إضافي") },
                        modifier = Modifier.scrollToTopOnFocus().scrollToTopOnFocus().weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true
                    )
                }

                OutlinedTextField(
                    value = companyName,
                    onValueChange = { companyName = it },
                    label = { Text("الشركة / المؤسسة") },
                    modifier = Modifier.scrollToTopOnFocus().scrollToTopOnFocus().fillMaxWidth(),
                    singleLine = true
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = city,
                        onValueChange = { city = it },
                        label = { Text("المدينة") },
                        modifier = Modifier.scrollToTopOnFocus().scrollToTopOnFocus().weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text("العنوان") },
                        modifier = Modifier.scrollToTopOnFocus().scrollToTopOnFocus().weight(1f),
                        singleLine = true
                    )
                }

                Text("نوع التعامل", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = dealType == "نقدي",
                        onClick = { dealType = "نقدي" },
                        label = { Text("نقدي") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = dealType == "آجل",
                        onClick = { dealType = "آجل" },
                        label = { Text("آجل") },
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = initialBalanceStr,
                    onValueChange = { initialBalanceStr = it },
                    label = { Text("الرصيد الافتتاحي (﷼)") },
                    modifier = Modifier.scrollToTopOnFocus().scrollToTopOnFocus().fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    maxLines = 1,
                    supportingText = {
                        val amt = initialBalanceStr.toDoubleOrNull() ?: 0.0
                        if (amt > 0) {
                            Text(
                                text = "المبلغ: ${FormatUtils.formatAmount(amt)}\u00A0﷼",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                )

                if ((initialBalanceStr.toDoubleOrNull() ?: 0.0) > 0) {
                    Text("نوع الرصيد الافتتاحي", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = balanceType == "مدين",
                            onClick = { balanceType = "مدين" },
                            label = { Text("مدين") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = balanceType == "دائن",
                            onClick = { balanceType = "دائن" },
                            label = { Text("دائن") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                if (dealType == "آجل") {
                    OutlinedTextField(
                        value = creditLimitStr,
                        onValueChange = { creditLimitStr = it },
                        label = { Text("حد الائتمان (﷼)") },
                        modifier = Modifier.scrollToTopOnFocus().scrollToTopOnFocus().fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                }

                OutlinedTextField(
                    value = taxNumber,
                    onValueChange = { taxNumber = it },
                    label = { Text("الرقم الضريبي") },
                    modifier = Modifier.scrollToTopOnFocus().scrollToTopOnFocus().fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("البريد الإلكتروني") },
                    modifier = Modifier.scrollToTopOnFocus().scrollToTopOnFocus().fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("الملاحظات") },
                    modifier = Modifier.scrollToTopOnFocus().scrollToTopOnFocus().fillMaxWidth()
                )
            }
        },
        confirmButton = {
            val context = LocalContext.current
            Button(
                onClick = {
                    if (name.isBlank()) {
                        nameError = true
                        Toast.makeText(context, "يجب تعبئة البيانات الإجبارية (اسم العميل)", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    val initBal = initialBalanceStr.toDoubleOrNull() ?: 0.0
                    val limit = creditLimitStr.toDoubleOrNull() ?: 0.0
                    val updated = client.copy(
                        name = name.trim(),
                        phone = phone.trim(),
                        altPhone = altPhone.trim(),
                        companyName = companyName.trim(),
                        city = city.trim(),
                        address = address.trim(),
                        email = email.trim(),
                        taxNumber = taxNumber.trim(),
                        dealType = dealType,
                        initialBalance = initBal,
                        balanceType = balanceType,
                        creditLimit = limit,
                        paymentPeriod = paymentPeriod,
                        clientType = clientType,
                        classification = classification,
                        notes = notes.trim()
                    )
                    onConfirm(updated)
                }
            ) {
                Text("حفظ التعديلات", fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("إلغاء", maxLines = 1, softWrap = false)
            }
        }
    )
}

// -------------------------------------------------------------
// COMPONENT: LOYALTY & PAYMENT BEHAVIOR CARD
// -------------------------------------------------------------
@Composable
fun LoyaltyBehaviorCard(
    loyaltyProfile: com.example.util.ClientLoyaltyProfile,
    onSendReminder: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = loyaltyProfile.primaryContainerColor.copy(alpha = 0.35f)
        ),
        border = BorderStroke(1.dp, loyaltyProfile.primaryColor.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = loyaltyProfile.primaryBadge.iconEmoji,
                        fontSize = 22.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = loyaltyProfile.primaryBadge.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = loyaltyProfile.primaryColor
                        )
                        Text(
                            text = loyaltyProfile.primaryBadge.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = loyaltyProfile.primaryColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "نقاط التقييم: ${loyaltyProfile.loyaltyScore}%",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = loyaltyProfile.primaryColor
                    )
                }
            }

            HorizontalDivider(color = loyaltyProfile.primaryColor.copy(alpha = 0.2f))

            // Behavioral metrics row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("نسبة الالتزام بالسداد", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = "${(loyaltyProfile.paymentCommitmentRate * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (loyaltyProfile.paymentCommitmentRate >= 0.75) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("متوسط سرعة السداد", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = if (loyaltyProfile.averageSettlementDays > 0) "${loyaltyProfile.averageSettlementDays.toInt()} يوم" else "فوري",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text("حالة السداد", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = if (loyaltyProfile.isOverdue) "متأخر (${loyaltyProfile.overdueDays} يوم)" else "ملتزم ومحدث",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (loyaltyProfile.isOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                }
            }

            // If Overdue or Loyal appreciation button
            if (loyaltyProfile.isOverdue) {
                OutlinedButton(
                    onClick = onSendReminder,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f))
                ) {
                    Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "إرسال إشعار تذكير بالسداد عبر الواتساب",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else if (loyaltyProfile.isLoyal) {
                OutlinedButton(
                    onClick = onSendReminder,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "إرسال رسالة شكر وتقدير عبر الواتساب",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
