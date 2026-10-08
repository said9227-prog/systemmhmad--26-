package com.example.ui.components

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.Client
import com.example.data.model.Installment
import com.example.data.model.InstallmentReminder
import com.example.data.model.InstallmentReminderType
import com.example.data.model.Invoice
import com.example.util.DateTimeUtils
import com.example.util.FormatUtils
import com.example.util.ShareManager
import java.util.Calendar

/**
 * 1. Home Screen Popup Dialog for new/unhandled reminders.
 * Strictly respects the One-Time Popup Logic backed by Room persistence.
 */
@Composable
fun InstallmentReminderPopupDialog(
    reminders: List<InstallmentReminder>,
    storeName: String,
    onDismissReminder: (reminderId: Int) -> Unit,
    onNavigateToInstallments: (reminderId: Int) -> Unit,
    onNavigateToClient: (clientId: Int, reminderId: Int) -> Unit
) {
    if (reminders.isEmpty()) return

    // Show the highest priority reminder first (Customer-specific or General)
    var currentIndex by remember(reminders.size) { mutableStateOf(0) }
    val safeIndex = currentIndex.coerceIn(0, (reminders.size - 1).coerceAtLeast(0))
    val currentReminder = reminders.getOrNull(safeIndex) ?: return

    val context = LocalContext.current
    val isGeneral = currentReminder.reminderType == InstallmentReminderType.GENERAL_INSTALLMENT

    AlertDialog(
        onDismissRequest = {
            onDismissReminder(currentReminder.id)
        },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        modifier = Modifier.testTag("installment_reminder_popup_dialog"),
        icon = {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (isGeneral) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.errorContainer
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isGeneral) Icons.Default.NotificationsActive else Icons.Default.Person,
                    contentDescription = null,
                    tint = if (isGeneral) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
            }
        },
        title = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (isGeneral) "🔔 تنبيه أقساط عام" else "👤 تنبيه قسط للعميل",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                if (reminders.size > 1) {
                    Text(
                        text = "تنبيه ${safeIndex + 1} من ${reminders.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isGeneral) {
                    // GENERAL REMINDER LAYOUT
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "لديك ${currentReminder.dueCount} أقساط مستحقة.",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "إجمالي المستحق:",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${FormatUtils.formatAmount(currentReminder.amount)} ${currentReminder.currency}",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center
                            )

                            if (currentReminder.overdueCount > 0) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 12.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant
                                )
                                Text(
                                    text = "العملاء المتأخرين بالسداد: ${currentReminder.overdueCount} عميل",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "المبلغ المتأخر: ${FormatUtils.formatAmount(currentReminder.overdueAmount)} ${currentReminder.currency}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                } else {
                    // CUSTOMER-SPECIFIC REMINDER LAYOUT
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = currentReminder.customerName ?: "عميل",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            if (!currentReminder.invoiceNumber.isNullOrBlank()) {
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.padding(top = 6.dp)
                                ) {
                                    Text(
                                        text = "فاتورة: ${currentReminder.invoiceNumber}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "المبلغ المستحق:",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${FormatUtils.formatAmount(currentReminder.remainingAmount)} ${currentReminder.currency}",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center
                            )

                            if (currentReminder.dueDate > 0) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.CalendarMonth,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "تاريخ الاستحقاق: ${DateTimeUtils.formatDateOnly(currentReminder.dueDate)}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (currentReminder.overdueDays > 0) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "متأخر بالسداد: ${currentReminder.overdueDays} أيام",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (isGeneral) {
                Button(
                    onClick = {
                        onNavigateToInstallments(currentReminder.id)
                    },
                    modifier = Modifier.testTag("view_installments_btn")
                ) {
                    Icon(imageVector = Icons.Default.ListAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("عرض العملاء والأقساط")
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Share via standard system Share Sheet
                    OutlinedButton(
                        onClick = {
                            ShareManager.shareCustomerInstallmentReminder(
                                context = context,
                                customerName = currentReminder.customerName ?: "العميل",
                                invoiceNumber = currentReminder.invoiceNumber,
                                remainingAmount = currentReminder.remainingAmount,
                                currency = currentReminder.currency,
                                dueDate = currentReminder.dueDate,
                                overdueDays = currentReminder.overdueDays,
                                storeName = storeName
                            )
                        },
                        modifier = Modifier.testTag("share_reminder_btn")
                    ) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("مشاركة")
                    }

                    Button(
                        onClick = {
                            val cId = currentReminder.customerId ?: 0
                            onNavigateToClient(cId, currentReminder.id)
                        },
                        modifier = Modifier.testTag("view_customer_btn")
                    ) {
                        Icon(imageVector = Icons.Default.Person, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("عرض العميل")
                    }
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    onDismissReminder(currentReminder.id)
                },
                modifier = Modifier.testTag("close_reminder_btn")
            ) {
                Text("إغلاق")
            }
        }
    )
}

/**
 * 2. Dialog to Configure and Create a General Installment Reminder
 */
@Composable
fun CreateGeneralReminderDialog(
    initialScope: String = "جميع ما سبق",
    initialHour: Int = 10,
    initialMinute: Int = 0,
    daysBeforeDue: Int = 1,
    daysAfterOverdue: Int = 3,
    onDismiss: () -> Unit,
    onSave: (scope: String, hour: Int, minute: Int, daysBefore: Int, daysAfter: Int) -> Unit
) {
    val context = LocalContext.current
    var selectedScope by remember { mutableStateOf(initialScope) }
    var hour by remember { mutableStateOf(initialHour) }
    var minute by remember { mutableStateOf(initialMinute) }
    var daysBefore by remember { mutableStateOf(daysBeforeDue.toString()) }
    var daysAfter by remember { mutableStateOf(daysAfterOverdue.toString()) }

    val scopes = listOf(
        "جميع ما سبق",
        "أقساط مستحقة اليوم",
        "أقساط ستستحق قريبًا",
        "أقساط متأخرة"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("create_general_reminder_dialog"),
        icon = {
            Icon(
                imageVector = Icons.Default.NotificationsActive,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Text(
                text = "🔔 تنبيه أقساط عام",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "تنبيه إداري عام يلخص الأقساط المستحقة والمتأخرة لجميع العملاء.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Scope selector
                Text(
                    text = "نوع التنبيه:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    scopes.forEach { scopeOption ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                        ) {
                            RadioButton(
                                selected = selectedScope == scopeOption,
                                onClick = { selectedScope = scopeOption }
                            )
                            Text(
                                text = scopeOption,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = 6.dp)
                            )
                        }
                    }
                }

                // Time picker button
                OutlinedCard(
                    onClick = {
                        TimePickerDialog(
                            context,
                            { _, h, m ->
                                hour = h
                                minute = m
                            },
                            hour,
                            minute,
                            false
                        ).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "وقت التنبيه اليومي",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val amPm = if (hour < 12) "صباحًا" else "مساءً"
                            val displayHour = if (hour % 12 == 0) 12 else hour % 12
                            Text(
                                text = String.format("%02d:%02d %s", displayHour, minute, amPm),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Icon(imageVector = Icons.Default.AccessTime, contentDescription = null)
                    }
                }

                // Days before due
                OutlinedTextField(
                    value = daysBefore,
                    onValueChange = { daysBefore = it.filter { ch -> ch.isDigit() } },
                    label = { Text("عدد الأيام قبل الاستحقاق") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Today, contentDescription = null) }
                )

                // Days after overdue
                OutlinedTextField(
                    value = daysAfter,
                    onValueChange = { daysAfter = it.filter { ch -> ch.isDigit() } },
                    label = { Text("عدد أيام التأخر") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Warning, contentDescription = null) }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val db = daysBefore.toIntOrNull() ?: 1
                    val da = daysAfter.toIntOrNull() ?: 3
                    onSave(selectedScope, hour, minute, db, da)
                },
                modifier = Modifier.testTag("save_general_reminder_btn")
            ) {
                Text("حفظ التنبيه العام")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}

/**
 * 3. Dialog to Create a Customer-Specific Installment Reminder.
 * Automatically loads customer info and unpaid invoices/installments.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateCustomerReminderDialog(
    customer: Client,
    customerInvoices: List<Invoice>,
    customerInstallments: List<Installment>,
    preselectedInstallment: Installment? = null,
    currency: String = "الريال اليمني",
    onDismiss: () -> Unit,
    onSave: (
        customerId: Int,
        customerName: String,
        invoiceId: Int?,
        invoiceNumber: String?,
        installmentId: Int?,
        amount: Double,
        remainingAmount: Double,
        dueDate: Long,
        scheduledAt: Long,
        currency: String
    ) -> Unit
) {
    val context = LocalContext.current

    // Options: individual invoices with remaining balance, or individual installment, or overall balance
    var selectedInvoiceId by remember {
        mutableStateOf(preselectedInstallment?.invoiceId)
    }
    var selectedInstallmentId by remember {
        mutableStateOf(preselectedInstallment?.id)
    }

    val unpaidInvoices = remember(customerInvoices) {
        customerInvoices.filter { !it.isDraft && it.remainingAmount > 0.01 }
    }

    // Default remaining amount
    val remainingAmount = remember(selectedInvoiceId, selectedInstallmentId, unpaidInvoices, customerInstallments, customer) {
        if (selectedInstallmentId != null && selectedInstallmentId != 0) {
            val inst = customerInstallments.firstOrNull { it.id == selectedInstallmentId }
            inst?.let { (it.amount - it.paidAmount).coerceAtLeast(0.0) } ?: customer.balance
        } else if (selectedInvoiceId != null && selectedInvoiceId != 0) {
            val inv = unpaidInvoices.firstOrNull { it.id == selectedInvoiceId }
            inv?.remainingAmount ?: customer.balance
        } else {
            customer.balance.coerceAtLeast(0.0)
        }
    }

    val selectedInvoiceNumber = remember(selectedInvoiceId, unpaidInvoices) {
        unpaidInvoices.firstOrNull { it.id == selectedInvoiceId }?.invoiceNumber
    }

    val defaultDueDate = remember(selectedInvoiceId, selectedInstallmentId, unpaidInvoices, customerInstallments) {
        if (selectedInstallmentId != null && selectedInstallmentId != 0) {
            customerInstallments.firstOrNull { it.id == selectedInstallmentId }?.dueDate ?: System.currentTimeMillis()
        } else if (selectedInvoiceId != null && selectedInvoiceId != 0) {
            unpaidInvoices.firstOrNull { it.id == selectedInvoiceId }?.date ?: System.currentTimeMillis()
        } else {
            System.currentTimeMillis()
        }
    }

    var reminderDueDate by remember { mutableStateOf(defaultDueDate) }

    // Scheduled reminder date/time (default tomorrow 10:00 AM)
    val calendar = remember {
        Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }
    }
    var scheduledAtTime by remember { mutableStateOf(calendar.timeInMillis) }

    var invoiceDropdownExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("create_customer_reminder_dialog"),
        icon = {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Text(
                text = "👤 تنبيه قسط للعميل",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Customer name card
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "العميل:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = customer.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Select Invoice / Installment
                if (unpaidInvoices.isNotEmpty()) {
                    ExposedDropdownMenuBox(
                        expanded = invoiceDropdownExpanded,
                        onExpandedChange = { invoiceDropdownExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = selectedInvoiceNumber ?: "الرصيد الإجمالي المتبقي على العميل",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("اختر الفاتورة / القسط") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = invoiceDropdownExpanded) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = invoiceDropdownExpanded,
                            onDismissRequest = { invoiceDropdownExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("الرصيد الإجمالي (${FormatUtils.formatAmount(customer.balance)} $currency)") },
                                onClick = {
                                    selectedInvoiceId = null
                                    selectedInstallmentId = null
                                    invoiceDropdownExpanded = false
                                }
                            )
                            unpaidInvoices.forEach { inv ->
                                DropdownMenuItem(
                                    text = {
                                        Text("${inv.invoiceNumber} - متبقي: ${FormatUtils.formatAmount(inv.remainingAmount)} ${inv.currency}")
                                    },
                                    onClick = {
                                        selectedInvoiceId = inv.id
                                        selectedInstallmentId = null
                                        reminderDueDate = inv.date
                                        invoiceDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Remaining balance card
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "المبلغ المتبقي الفعلي:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${FormatUtils.formatAmount(remainingAmount)} $currency",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                // Due Date selector
                OutlinedCard(
                    onClick = {
                        val cal = Calendar.getInstance().apply { timeInMillis = reminderDueDate }
                        DatePickerDialog(
                            context,
                            { _, y, m, d ->
                                cal.set(y, m, d)
                                reminderDueDate = cal.timeInMillis
                            },
                            cal.get(Calendar.YEAR),
                            cal.get(Calendar.MONTH),
                            cal.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "تاريخ الاستحقاق",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = DateTimeUtils.formatDateOnly(reminderDueDate),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Icon(imageVector = Icons.Default.CalendarToday, contentDescription = null)
                    }
                }

                // Reminder Trigger Date & Time selector
                OutlinedCard(
                    onClick = {
                        val cal = Calendar.getInstance().apply { timeInMillis = scheduledAtTime }
                        DatePickerDialog(
                            context,
                            { _, y, m, d ->
                                cal.set(y, m, d)
                                TimePickerDialog(
                                    context,
                                    { _, hourOfDay, minute ->
                                        cal.set(Calendar.HOUR_OF_DAY, hourOfDay)
                                        cal.set(Calendar.MINUTE, minute)
                                        scheduledAtTime = cal.timeInMillis
                                    },
                                    cal.get(Calendar.HOUR_OF_DAY),
                                    cal.get(Calendar.MINUTE),
                                    false
                                ).show()
                            },
                            cal.get(Calendar.YEAR),
                            cal.get(Calendar.MONTH),
                            cal.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "موعد إطلاق التنبيه (التاريخ والوقت)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = DateTimeUtils.formatDateTime12h(scheduledAtTime),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Icon(imageVector = Icons.Default.Alarm, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        customer.id,
                        customer.name,
                        selectedInvoiceId,
                        selectedInvoiceNumber,
                        selectedInstallmentId,
                        remainingAmount,
                        remainingAmount,
                        reminderDueDate,
                        scheduledAtTime,
                        currency
                    )
                },
                modifier = Modifier.testTag("save_customer_reminder_btn")
            ) {
                Text("حفظ التنبيه")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}
