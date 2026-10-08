package com.example.ui.screens

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Client
import com.example.data.model.Installment
import com.example.data.model.StoreSettings
import com.example.ui.components.CreateCustomerReminderDialog
import com.example.ui.components.CreateGeneralReminderDialog
import com.example.ui.viewmodel.AppViewModel
import com.example.util.DateTimeUtils
import com.example.util.FormatUtils
import com.example.util.ShareManager
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstallmentsScreen(
    viewModel: AppViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToClient: (clientId: Int) -> Unit,
    onNavigateToAlarmSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    val installmentsData by viewModel.installmentsScreenData.collectAsState()
    val installments = installmentsData.installments
    val clients = installmentsData.clients
    val invoices = installmentsData.invoices
    val settings by viewModel.storeSettings.collectAsState()

    var filterState by remember { mutableStateOf("الكل") }
    var searchQuery by remember { mutableStateOf("") }

    // Dialogs state
    var showGeneralReminderDialog by remember { mutableStateOf(false) }
    var showCustomerReminderDialog by remember { mutableStateOf(false) }
    var selectedClientForReminder by remember { mutableStateOf<Client?>(null) }
    var preselectedInstallmentForReminder by remember { mutableStateOf<Installment?>(null) }
    var showAddInstallmentDialog by remember { mutableStateOf(false) }

    val now = System.currentTimeMillis()
    val startOfToday = remember(now) { DateTimeUtils.getStartOfDay(now) }
    val endOfToday = remember(now) { DateTimeUtils.getEndOfDay(now) }

    // Financial summaries
    val dueTodayList = remember(installments, startOfToday, endOfToday) {
        installments.filter { !it.isPaid && it.dueDate in startOfToday..endOfToday }
    }
    val overdueList = remember(installments, startOfToday) {
        installments.filter { !it.isPaid && it.dueDate < startOfToday }
    }
    val activeList = remember(installments) {
        installments.filter { !it.isPaid }
    }

    val totalDueToday = remember(dueTodayList) {
        dueTodayList.sumOf { (it.amount - it.paidAmount).coerceAtLeast(0.0) }
    }
    val totalOverdue = remember(overdueList) {
        overdueList.sumOf { (it.amount - it.paidAmount).coerceAtLeast(0.0) }
    }

    // Filtered list
    val displayedInstallments = remember(installments, filterState, searchQuery, startOfToday, endOfToday) {
        installments.filter { inst ->
            val matchesFilter = when (filterState) {
                "مستحقة اليوم" -> !inst.isPaid && inst.dueDate in startOfToday..endOfToday
                "متأخرة" -> !inst.isPaid && inst.dueDate < startOfToday
                "قادمة" -> !inst.isPaid && inst.dueDate > endOfToday
                "مدفوعة" -> inst.isPaid
                else -> true
            }
            val matchesSearch = searchQuery.isBlank() ||
                    inst.clientName.contains(searchQuery, ignoreCase = true) ||
                    inst.notes.contains(searchQuery, ignoreCase = true)

            matchesFilter && matchesSearch
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "إدارة الأقساط والتنبيهات",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                actions = {
                    // Quick Action: Alarm Profile Settings
                    IconButton(
                        onClick = onNavigateToAlarmSettings,
                        modifier = Modifier.testTag("open_alarm_settings_topbar_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Alarm,
                            contentDescription = "إعدادات منبّه الأقساط",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    // Quick Action: General Installment Reminder
                    IconButton(
                        onClick = { showGeneralReminderDialog = true },
                        modifier = Modifier.testTag("open_general_reminder_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = "تنبيه أقساط عام",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddInstallmentDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("قسط جديد") },
                modifier = Modifier.testTag("add_installment_fab")
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header: Action Banners for General & Customer Reminders
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // General Reminder Button
                    Card(
                        onClick = { showGeneralReminderDialog = true },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("card_general_reminder"),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.NotificationsActive,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "تنبيه أقساط عام",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "تذكير إداري شامل",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // Customer Reminder Button
                    Card(
                        onClick = {
                            if (clients.isNotEmpty()) {
                                selectedClientForReminder = clients.first()
                                preselectedInstallmentForReminder = null
                                showCustomerReminderDialog = true
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("card_customer_reminder"),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.secondary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "تنبيه قسط للعميل",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "مخصص لعميل محدد",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Dedicated Alarm Profile Card Banner
                Card(
                    onClick = onNavigateToAlarmSettings,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("card_alarm_profile_settings"),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Alarm,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "🔔 إعدادات منبّه الأقساط (Alarm Profile)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "تخصيص التوقيت، النغمات الأصلية، الاهتزاز، والتكرار",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronLeft,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Financial Summary Cards
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Due today
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text("مستحقة اليوم", style = MaterialTheme.typography.labelMedium)
                            Text(
                                text = "${dueTodayList.size} أقساط",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${FormatUtils.formatAmount(totalDueToday)} ${settings.currency}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Overdue
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text("أقساط متأخرة", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                            Text(
                                text = "${overdueList.size} أقساط",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = "${FormatUtils.formatAmount(totalOverdue)} ${settings.currency}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Search Bar
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("بحث باسم العميل أو الملاحظات...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "مسح")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("installment_search_field")
                )
            }

            // Filter Chips
            item {
                val filters = listOf("الكل", "مستحقة اليوم", "متأخرة", "قادمة", "مدفوعة")
                Row(
                    modifier = Modifier.scrollToTopOnFocus().scrollToTopOnFocus().fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    filters.forEach { f ->
                        FilterChip(
                            selected = filterState == f,
                            onClick = { filterState = f },
                            label = { Text(f) }
                        )
                    }
                }
            }

            // List of Installments
            if (displayedInstallments.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Outlined.EventBusy,
                                contentDescription = null,
                                modifier = Modifier.size(54.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "لا توجد أقساط مطابقة",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            } else {
                items(displayedInstallments, key = { it.id }) { inst ->
                    val remaining = (inst.amount - inst.paidAmount).coerceAtLeast(0.0)
                    val isOverdue = !inst.isPaid && inst.dueDate < startOfToday
                    val isDueToday = !inst.isPaid && inst.dueDate in startOfToday..endOfToday
                    val overdueDays = if (isOverdue) ((now - inst.dueDate) / (1000 * 60 * 60 * 24)).toInt() else 0

                    InstallmentCardItem(
                        installment = inst,
                        remainingAmount = remaining,
                        isOverdue = isOverdue,
                        isDueToday = isDueToday,
                        overdueDays = overdueDays,
                        currency = inst.currency,
                        onReminderClick = {
                            val client = clients.firstOrNull { it.id == inst.clientId }
                            if (client != null) {
                                selectedClientForReminder = client
                                preselectedInstallmentForReminder = inst
                                showCustomerReminderDialog = true
                            }
                        },
                        onShareClick = {
                            ShareManager.shareCustomerInstallmentReminder(
                                context = context,
                                customerName = inst.clientName,
                                invoiceNumber = if (inst.invoiceId != null && inst.invoiceId > 0) "INV-${inst.invoiceId}" else null,
                                remainingAmount = remaining,
                                currency = inst.currency,
                                dueDate = inst.dueDate,
                                overdueDays = overdueDays,
                                storeName = settings.storeName
                            )
                        },
                        onPayClick = {
                            viewModel.markInstallmentPaid(inst)
                        },
                        onDeleteClick = {
                            viewModel.deleteInstallment(inst)
                        },
                        onClientClick = {
                            onNavigateToClient(inst.clientId)
                        }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(72.dp))
            }
        }
    }

    // --- Dialog: General Reminder ---
    if (showGeneralReminderDialog) {
        CreateGeneralReminderDialog(
            initialScope = settings.generalReminderScope,
            initialHour = settings.generalReminderHour,
            initialMinute = settings.generalReminderMinute,
            daysBeforeDue = settings.generalReminderDaysBeforeDue,
            daysAfterOverdue = settings.generalReminderDaysAfterOverdue,
            onDismiss = { showGeneralReminderDialog = false },
            onSave = { scope, hour, minute, daysBefore, daysAfter ->
                showGeneralReminderDialog = false
                viewModel.createGeneralReminder(scope = scope)
                viewModel.updateInstallmentReminderSettings(
                    isGeneralEnabled = true,
                    generalHour = hour,
                    generalMinute = minute,
                    daysBefore = daysBefore,
                    daysAfter = daysAfter,
                    scope = scope,
                    isCustomerEnabled = settings.isCustomerInstallmentReminderEnabled,
                    allowCustomCustomerReminder = settings.allowCustomCustomerReminder
                )
            }
        )
    }

    // --- Dialog: Customer Reminder ---
    if (showCustomerReminderDialog && selectedClientForReminder != null) {
        val client = selectedClientForReminder!!
        val clientInvoices = remember(invoices, client.id) {
            invoices.filter { it.clientId == client.id }
        }
        val clientInstList = remember(installments, client.id) {
            installments.filter { it.clientId == client.id }
        }

        CreateCustomerReminderDialog(
            customer = client,
            customerInvoices = clientInvoices,
            customerInstallments = clientInstList,
            preselectedInstallment = preselectedInstallmentForReminder,
            onDismiss = {
                showCustomerReminderDialog = false
                selectedClientForReminder = null
                preselectedInstallmentForReminder = null
            },
            onSave = { customerId, customerName, invoiceId, invoiceNumber, installmentId, amount, remainingAmount, dueDate, scheduledAt, currency ->
                showCustomerReminderDialog = false
                selectedClientForReminder = null
                preselectedInstallmentForReminder = null

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
            }
        )
    }

    // --- Dialog: Add New Installment ---
    if (showAddInstallmentDialog) {
        AddInstallmentDialog(
            clients = clients,
            defaultCurrency = settings.currency,
            onDismiss = { showAddInstallmentDialog = false },
            onSave = { clientId, clientName, amount, dueDate, notes, recurrence, currency ->
                showAddInstallmentDialog = false
                viewModel.addInstallment(
                    Installment(
                        clientId = clientId,
                        clientName = clientName,
                        amount = amount,
                        dueDate = dueDate,
                        notes = notes,
                        recurrence = recurrence,
                        currency = currency
                    )
                )
            }
        )
    }
}

@Composable
fun InstallmentCardItem(
    installment: Installment,
    remainingAmount: Double,
    isOverdue: Boolean,
    isDueToday: Boolean,
    overdueDays: Int,
    currency: String,
    onReminderClick: () -> Unit,
    onShareClick: () -> Unit,
    onPayClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onClientClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("installment_card_${installment.id}"),
        colors = CardDefaults.cardColors(
            containerColor = when {
                installment.isPaid -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                isOverdue -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                isDueToday -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                else -> MaterialTheme.colorScheme.surface
            }
        ),
        shape = RoundedCornerShape(10.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Header: Customer name + Status badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onClientClick() }
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = installment.clientName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Badge
                Surface(
                    color = when {
                        installment.isPaid -> Color(0xFF2E7D32)
                        isOverdue -> MaterialTheme.colorScheme.error
                        isDueToday -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.secondary
                    },
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = when {
                            installment.isPaid -> "مدفوع بالكامل"
                            isOverdue -> "متأخر ($overdueDays يوم)"
                            isDueToday -> "مستحق اليوم"
                            else -> "جاري"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Amount breakdown
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "المبلغ المتبقي:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${FormatUtils.formatAmount(remainingAmount)} $currency",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (installment.isPaid) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "المبلغ الإجمالي:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${FormatUtils.formatAmount(installment.amount)} $currency",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Due Date & Recurrence
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.CalendarToday,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "الاستحقاق: ${DateTimeUtils.formatDateOnly(installment.dueDate)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                if (installment.recurrence != "بدون تكرار") {
                    Text(
                        text = "التكرار: ${installment.recurrence}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            if (installment.notes.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "ملاحظات: ${installment.notes}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 10.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            // Action Buttons: 🔔 تنبيه, 📤 مشاركة, 💳 سداد, 🗑️ حذف
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!installment.isPaid) {
                    // 🔔 تنبيه button
                    OutlinedButton(
                        onClick = onReminderClick,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("installment_reminder_btn_${installment.id}"),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("تنبيه", fontSize = 12.sp, maxLines = 1, softWrap = false)
                    }

                    // 📤 مشاركة button
                    OutlinedButton(
                        onClick = onShareClick,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("installment_share_btn_${installment.id}"),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("مشاركة", fontSize = 12.sp, maxLines = 1, softWrap = false)
                    }

                    // 💳 سداد button
                    Button(
                        onClick = onPayClick,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("installment_pay_btn_${installment.id}"),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Payment, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("سداد", fontSize = 12.sp, maxLines = 1, softWrap = false)
                    }
                } else {
                    Spacer(modifier = Modifier.weight(3f))
                }

                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("installment_delete_btn_${installment.id}")
                ) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = "حذف القسط",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddInstallmentDialog(
    clients: List<Client>,
    defaultCurrency: String,
    onDismiss: () -> Unit,
    onSave: (
        clientId: Int,
        clientName: String,
        amount: Double,
        dueDate: Long,
        notes: String,
        recurrence: String,
        currency: String
    ) -> Unit
) {
    val context = LocalContext.current
    var selectedClient by remember { mutableStateOf(clients.firstOrNull()) }
    var amountText by remember { mutableStateOf("") }
    var notesText by remember { mutableStateOf("") }
    var recurrence by remember { mutableStateOf("بدون تكرار") }

    val cal = remember { Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 7) } }
    var dueDate by remember { mutableStateOf(cal.timeInMillis) }

    var clientDropdownExpanded by remember { mutableStateOf(false) }
    var recurrenceDropdownExpanded by remember { mutableStateOf(false) }

    val recurrences = listOf("بدون تكرار", "يومي", "أسبوعي", "شهري", "سنوي")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة قسط جديد", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Client selector
                ExposedDropdownMenuBox(
                    expanded = clientDropdownExpanded,
                    onExpandedChange = { clientDropdownExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedClient?.name ?: "اختر العميل",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("العميل") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = clientDropdownExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = clientDropdownExpanded,
                        onDismissRequest = { clientDropdownExpanded = false }
                    ) {
                        clients.forEach { c ->
                            DropdownMenuItem(
                                text = { Text(c.name) },
                                onClick = {
                                    selectedClient = c
                                    clientDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                // Amount
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = { Text("مبلغ القسط") },
                    singleLine = true,
                    modifier = Modifier.scrollToTopOnFocus().scrollToTopOnFocus().fillMaxWidth()
                )

                // Due Date
                OutlinedCard(
                    onClick = {
                        val dCal = Calendar.getInstance().apply { timeInMillis = dueDate }
                        DatePickerDialog(
                            context,
                            { _, y, m, d ->
                                dCal.set(y, m, d)
                                dueDate = dCal.timeInMillis
                            },
                            dCal.get(Calendar.YEAR),
                            dCal.get(Calendar.MONTH),
                            dCal.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("تاريخ استحقاق القسط", style = MaterialTheme.typography.labelSmall)
                            Text(DateTimeUtils.formatDateOnly(dueDate), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        }
                        Icon(Icons.Default.CalendarToday, contentDescription = null)
                    }
                }

                // Recurrence
                ExposedDropdownMenuBox(
                    expanded = recurrenceDropdownExpanded,
                    onExpandedChange = { recurrenceDropdownExpanded = it }
                ) {
                    OutlinedTextField(
                        value = recurrence,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("التكرار") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = recurrenceDropdownExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = recurrenceDropdownExpanded,
                        onDismissRequest = { recurrenceDropdownExpanded = false }
                    ) {
                        recurrences.forEach { r ->
                            DropdownMenuItem(
                                text = { Text(r) },
                                onClick = {
                                    recurrence = r
                                    recurrenceDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                // Notes
                OutlinedTextField(
                    value = notesText,
                    onValueChange = { notesText = it },
                    label = { Text("ملاحظات (اختياري)") },
                    modifier = Modifier.scrollToTopOnFocus().scrollToTopOnFocus().fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = amountText.toDoubleOrNull() ?: 0.0
                    val client = selectedClient
                    if (client != null && amount > 0.0) {
                        onSave(
                            client.id,
                            client.name,
                            amount,
                            dueDate,
                            notesText,
                            recurrence,
                            defaultCurrency
                        )
                    }
                },
                enabled = selectedClient != null && (amountText.toDoubleOrNull() ?: 0.0) > 0.0
            ) {
                Text("حفظ")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}
