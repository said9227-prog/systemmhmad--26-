package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.Client
import com.example.data.model.Invoice
import com.example.data.model.Payment
import com.example.ui.viewmodel.AppViewModel
import com.example.util.CreditUtils
import com.example.util.FormatUtils
import com.example.util.ShareManager
import com.example.util.exportSelectedPaymentsToPdf
import java.text.SimpleDateFormat
import java.util.*

/**
 * Formats timestamp millis to full Arabic day name + date + 12-hour time (ص/م).
 * Example: "الأربعاء 2026-07-22 | 03:26 ص"
 */
fun formatArabicTimestamp(timeMillis: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = timeMillis }
    val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
    val arabicDayName = when (dayOfWeek) {
        Calendar.SUNDAY -> "الأحد"
        Calendar.MONDAY -> "الإثنين"
        Calendar.TUESDAY -> "الثلاثاء"
        Calendar.WEDNESDAY -> "الأربعاء"
        Calendar.THURSDAY -> "الخميس"
        Calendar.FRIDAY -> "الجمعة"
        Calendar.SATURDAY -> "السبت"
        else -> ""
    }
    val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val hour12 = cal.get(Calendar.HOUR).let { if (it == 0) 12 else it }
    val minuteStr = String.format("%02d", cal.get(Calendar.MINUTE))
    val amPmStr = if (cal.get(Calendar.AM_PM) == Calendar.AM) "ص" else "م"

    return "$arabicDayName ${dateFmt.format(cal.time)} | $hour12:$minuteStr $amPmStr"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientStatementScreen(
    viewModel: AppViewModel,
    clientId: Int,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val settings by viewModel.storeSettings.collectAsState()

    BackHandler {
        onNavigateBack()
    }

    var showSmsConfirmDialog by remember { mutableStateOf(false) }
    var smsAmount by remember { mutableStateOf(0.0) }
    var smsCurrency by remember { mutableStateOf("") }

    var client by remember { mutableStateOf<Client?>(null) }
    val invoicesList by viewModel.invoices.collectAsState()
    val paymentsList by viewModel.payments.collectAsState()
    val returnsList by viewModel.allReturns.collectAsState()
    val exchangeRates by viewModel.exchangeRates.collectAsState()

    var showPaymentDialog by remember { mutableStateOf(false) }
    var showPdfDateDialog by remember { mutableStateOf(false) }
    var previewReceiptImageUri by remember { mutableStateOf<String?>(null) }

    // Screen Date Filters
    var filterStartDate by remember { mutableStateOf<Long?>(null) }
    var filterEndDate by remember { mutableStateOf<Long?>(null) }

    var searchQuery by remember { mutableStateOf("") }

    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }

    // Fetch Client Details
    LaunchedEffect(clientId, invoicesList, paymentsList) {
        client = viewModel.clients.value.find { it.id == clientId }
    }

    if (client == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val currentClient = client!!
    val clientInvoices = invoicesList.filter { it.clientId == clientId && !it.isDraft }
    val clientPayments = paymentsList.filter { it.clientId == clientId }
    val clientReturns = returnsList.filter { it.clientId == clientId }

    // Merge and Chronologically Sort Transactions Ledger
    val ledgerItems = remember(currentClient, clientInvoices, clientPayments, clientReturns, exchangeRates, filterStartDate, filterEndDate, searchQuery) {
        val list = mutableListOf<LedgerEntry>()

        val clientBaseCurrency = currentClient.currency.ifBlank { settings.currency.ifBlank { "الريال اليمني" } }
        val rateMap = exchangeRates.associate {
            (com.example.domain.currency.Currency.fromCodeOrName(it.fromCurrency) to com.example.domain.currency.Currency.fromCodeOrName(it.toCurrency)) to java.math.BigDecimal.valueOf(it.rate)
        }

        // 0. Opening Balance Entry if present
        if (kotlin.math.abs(currentClient.initialBalance) > 0.001) {
            val initCurrency = currentClient.initialBalanceCurrency.ifBlank { clientBaseCurrency }
            val isDebit = currentClient.balanceType == "مدين" || currentClient.balanceType == "عليه لنا"
            val initMoney = com.example.domain.currency.Money(kotlin.math.abs(currentClient.initialBalance), com.example.domain.currency.Currency.fromCodeOrName(initCurrency))
            val targetCurrency = com.example.domain.currency.Currency.fromCodeOrName(clientBaseCurrency)
            val conv = try {
                com.example.domain.currency.CurrencyConversionService.convert(initMoney, targetCurrency, rateMap)
            } catch (e: Exception) {
                com.example.domain.currency.ConversionDetail(initMoney, targetCurrency, java.math.BigDecimal.ONE, initMoney, "")
            }
            val convertedVal = conv.convertedMoney.toDouble()
            list.add(
                LedgerEntry(
                    id = -1,
                    date = if (currentClient.createdAt > 0) currentClient.createdAt else 0L,
                    type = "رصيد افتتاحي",
                    label = "رصيد افتتاحي (${currentClient.balanceType}) - ${FormatUtils.formatAmount(kotlin.math.abs(currentClient.initialBalance))} $initCurrency",
                    debit = if (isDebit) convertedVal else 0.0,
                    credit = if (!isDebit) convertedVal else 0.0,
                    paymentMethod = "",
                    currency = initCurrency,
                    originalAmount = kotlin.math.abs(currentClient.initialBalance),
                    exchangeRate = conv.exchangeRate.toDouble(),
                    baseCurrency = clientBaseCurrency
                )
            )
        }

        // 1. Invoices
        clientInvoices.forEach { inv ->
            val invCurr = inv.currency.ifBlank { clientBaseCurrency }
            val invCurrEnum = com.example.domain.currency.Currency.fromCodeOrName(invCurr)
            val targetCurrEnum = com.example.domain.currency.Currency.fromCodeOrName(clientBaseCurrency)
            val convertedVal = if (invCurrEnum == targetCurrEnum) {
                inv.totalAmount
            } else if (inv.convertedTotalAmount > 0) {
                inv.convertedTotalAmount
            } else {
                try {
                    com.example.domain.currency.CurrencyConversionService.convert(
                        com.example.domain.currency.Money(inv.totalAmount, invCurrEnum),
                        targetCurrEnum,
                        rateMap
                    ).convertedMoney.toDouble()
                } catch (e: Exception) {
                    inv.totalAmount
                }
            }

            list.add(
                LedgerEntry(
                    id = inv.id,
                    date = inv.date,
                    type = if (inv.isQuickInvoice) "فاتورة سريعة" else "فاتورة",
                    label = buildString {
                        append("فاتورة رقم: #${inv.invoiceNumber}")
                        if (invCurrEnum != targetCurrEnum) append(" (${FormatUtils.formatAmount(inv.totalAmount)} $invCurr)")
                        if (inv.notes?.isNotBlank() == true) append(" - ${inv.notes}")
                    },
                    debit = convertedVal,
                    credit = 0.0,
                    paymentMethod = "",
                    currency = invCurr,
                    originalAmount = inv.totalAmount,
                    exchangeRate = if (inv.exchangeRate > 0) inv.exchangeRate else 1.0,
                    baseCurrency = clientBaseCurrency,
                    voucherNumber = inv.invoiceNumber
                )
            )
        }

        // 2. Payments
        clientPayments.forEach { pay ->
            val payCurr = pay.currency.ifBlank { clientBaseCurrency }
            val payCurrEnum = com.example.domain.currency.Currency.fromCodeOrName(payCurr)
            val targetCurrEnum = com.example.domain.currency.Currency.fromCodeOrName(clientBaseCurrency)
            val convertedVal = if (payCurrEnum == targetCurrEnum) {
                pay.amount
            } else if (pay.convertedAmount > 0) {
                pay.convertedAmount
            } else {
                try {
                    com.example.domain.currency.CurrencyConversionService.convert(
                        com.example.domain.currency.Money(pay.amount, payCurrEnum),
                        targetCurrEnum,
                        rateMap
                    ).convertedMoney.toDouble()
                } catch (e: Exception) {
                    pay.amount
                }
            }

            list.add(
                LedgerEntry(
                    id = pay.id,
                    date = pay.date,
                    type = "دفعة مستلمة",
                    label = buildString {
                        append("سند قبض: ${pay.paymentMethod}")
                        if (payCurrEnum != targetCurrEnum) append(" (${FormatUtils.formatAmount(pay.amount)} $payCurr)")
                        if (!pay.voucherNumber.isNullOrBlank()) append(" | سند: #${pay.voucherNumber}")
                        if (!pay.collectorName.isNullOrBlank()) append(" | المحصل: ${pay.collectorName}")
                        if (!pay.transferNumber.isNullOrBlank()) append(" | حوالة: #${pay.transferNumber}")
                        if (!pay.notes.isNullOrBlank()) append(" | ${pay.notes}")
                    },
                    debit = 0.0,
                    credit = convertedVal,
                    paymentMethod = pay.paymentMethod,
                    voucherNumber = pay.voucherNumber,
                    collectorName = pay.collectorName,
                    transferNumber = pay.transferNumber,
                    receiptImageUri = pay.receiptImageUri,
                    currency = payCurr,
                    originalAmount = pay.amount,
                    exchangeRate = if (pay.exchangeRate > 0) pay.exchangeRate else 1.0,
                    baseCurrency = clientBaseCurrency
                )
            )
        }

        // 3. Customer Returns
        clientReturns.forEach { ret ->
            val isAccountCredit = ret.settlementType != "استرداد نقدي"
            val retCurr = ret.currency.ifBlank { clientBaseCurrency }
            val retCurrEnum = com.example.domain.currency.Currency.fromCodeOrName(retCurr)
            val targetCurrEnum = com.example.domain.currency.Currency.fromCodeOrName(clientBaseCurrency)
            val convertedVal = if (retCurrEnum == targetCurrEnum) {
                ret.totalAmount
            } else if (ret.convertedTotalAmount > 0) {
                ret.convertedTotalAmount
            } else {
                try {
                    com.example.domain.currency.CurrencyConversionService.convert(
                        com.example.domain.currency.Money(ret.totalAmount, retCurrEnum),
                        targetCurrEnum,
                        rateMap
                    ).convertedMoney.toDouble()
                } catch (e: Exception) {
                    ret.totalAmount
                }
            }

            list.add(
                LedgerEntry(
                    id = ret.id,
                    date = ret.date,
                    type = "مرتجع مبيعات",
                    label = buildString {
                        append("مرتجع #${ret.returnNumber}")
                        if (retCurrEnum != targetCurrEnum) append(" (${FormatUtils.formatAmount(ret.totalAmount)} $retCurr)")
                        if (!ret.invoiceNumber.isNullOrBlank()) append(" (عن فاتورة #${ret.invoiceNumber})")
                        append(" | التسوية: ${ret.settlementType}")
                        if (!ret.notes.isNullOrBlank()) append(" | ${ret.notes}")
                    },
                    debit = 0.0,
                    credit = if (isAccountCredit) convertedVal else 0.0,
                    paymentMethod = ret.settlementType,
                    currency = retCurr,
                    originalAmount = ret.totalAmount,
                    exchangeRate = if (ret.exchangeRate > 0) ret.exchangeRate else 1.0,
                    baseCurrency = clientBaseCurrency,
                    voucherNumber = ret.returnNumber
                )
            )
        }

        // Sort chronologically
        var sorted = list.sortedBy { it.date }

        // Apply date filters if any
        filterStartDate?.let { start -> sorted = sorted.filter { it.date >= start } }
        filterEndDate?.let { end -> sorted = sorted.filter { it.date <= end + 86400000L } } // include end day

        // Apply search query filter
        if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim()
            sorted = sorted.filter {
                it.voucherNumber?.contains(q, ignoreCase = true) == true ||
                it.label.contains(q, ignoreCase = true) ||
                it.type.contains(q, ignoreCase = true)
            }
        }

        // Compute running balance in customer base currency
        var currentRunningBalance = 0.0
        sorted.map { entry ->
            currentRunningBalance += (entry.debit - entry.credit)
            entry.copy(runningBalance = currentRunningBalance)
        }
    }

    val totalDebits = ledgerItems.sumOf { it.debit }
    val totalCredits = ledgerItems.sumOf { it.credit }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // App bar
        Surface(
            shadowElevation = 4.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "رجوع")
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Column {
                        Text(
                            text = "كشف حساب تفصيلي",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = currentClient.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Export Actions
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // CSV Share Button
                    IconButton(
                        onClick = {
                            val csv = viewModel.exportStatementToCSV(currentClient, clientInvoices, clientPayments)
                            ShareManager.shareText(
                                context = context,
                                text = csv,
                                chooserTitle = "📤 مشاركة كشف الحساب CSV",
                                subject = "كشف حساب - ${currentClient.name}"
                            )
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "مشاركة CSV",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // PDF Date Selector Trigger Button
                    FilledTonalIconButton(
                        onClick = { showPdfDateDialog = true },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = Color(0xFFFEE2E2),
                            contentColor = Color(0xFFDC2626)
                        ),
                        modifier = Modifier.testTag("pdf_export_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PictureAsPdf,
                            contentDescription = "مشاركة كشف الحساب PDF"
                        )
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Client Header Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = currentClient.name.take(1),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 20.sp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                Column {
                                    Text(
                                        text = currentClient.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 17.sp
                                    )
                                    if (currentClient.phone.isNotBlank()) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Phone,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = currentClient.phone,
                                                fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }

                            // Quick Call Action
                            if (currentClient.phone.isNotBlank()) {
                                IconButton(
                                    onClick = {
                                        try {
                                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${currentClient.phone}"))
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "تعذر فتح الاتصال", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier
                                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                                        .size(38.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Call,
                                        contentDescription = "اتصال",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        // Current Total Net Balance Badge
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = if (currentClient.balance > 0) Color(0xFFFEF2F2)
                                    else if (currentClient.balance < 0) Color(0xFFECFDF5)
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "الرصيد الكلي المتبقي",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = if (currentClient.balance > 0) "مستحق على العميل (دين)"
                                    else if (currentClient.balance < 0) "رصيد دائن لصالح العميل"
                                    else "الحساب متزن تماماً",
                                    fontSize = 11.sp,
                                    color = if (currentClient.balance > 0) Color(0xFFDC2626) else if (currentClient.balance < 0) Color(0xFF059669) else Color.Gray
                                )
                            }
                            Text(
                                text = "${FormatUtils.formatAmount(currentClient.balance)} ${settings.currency}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                color = if (currentClient.balance > 0) Color(0xFFDC2626) else if (currentClient.balance < 0) Color(0xFF059669) else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Credit Limit System Summary Widget (if configured)
                        if (currentClient.creditLimit > 0) {
                            val creditInfo = CreditUtils.getCreditStatusInfo(currentClient)
                            Card(
                                colors = CardDefaults.cardColors(containerColor = creditInfo.containerColor),
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, creditInfo.statusColor.copy(alpha = 0.3f))
                            ) {
                                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Icon(
                                                Icons.Default.AccountBalanceWallet,
                                                contentDescription = null,
                                                tint = creditInfo.statusColor,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                "نظام الحد الائتماني للعميل",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = creditInfo.statusColor
                                            )
                                        }

                                        Surface(
                                            color = creditInfo.statusColor.copy(alpha = 0.18f),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = creditInfo.title,
                                                color = creditInfo.statusColor,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("الحد الائتماني: ${FormatUtils.formatAmount(currentClient.creditLimit)} ${settings.currency}", fontSize = 11.sp, color = Color.DarkGray)
                                        Text("المتبقي: ${FormatUtils.formatAmount(creditInfo.availableCredit)} ${settings.currency}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (creditInfo.availableCredit > 0) Color(0xFF2E7D32) else Color(0xFFD32F2F))
                                    }

                                    // Progress bar
                                    val progressFraction = (creditInfo.usagePercentage / 100.0).toFloat().coerceIn(0f, 1f)
                                    LinearProgressIndicator(
                                        progress = { progressFraction },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(3.dp)),
                                        color = creditInfo.statusColor,
                                        trackColor = Color.LightGray.copy(alpha = 0.3f)
                                    )
                                    Text(
                                        text = "نسبة الاستهلاك: ${String.format(Locale.US, "%.1f", creditInfo.usagePercentage)}% من الحد المسموح",
                                        fontSize = 10.sp,
                                        color = Color.DarkGray
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Summary Metrics Row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Total Debits Card
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                Icon(Icons.Default.ArrowUpward, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(14.dp))
                                Text("إجمالي الديون", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF991B1B))
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = FormatUtils.formatAmount(totalDebits),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color(0xFFDC2626)
                            )
                        }
                    }

                    // Total Credits Card
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFECFDF5)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                Icon(Icons.Default.ArrowDownward, contentDescription = null, tint = Color(0xFF059669), modifier = Modifier.size(14.dp))
                                Text("إجمالي المسدد", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF065F46))
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = FormatUtils.formatAmount(totalCredits),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color(0xFF059669)
                            )
                        }
                    }

                    // Total Remaining Card (Requirement 4)
                    val remainingVal = currentClient.balance
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(
                            containerColor = if (remainingVal > 0) Color(0xFFFFF7ED) else Color(0xFFF3F4F6)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = if (remainingVal > 0) Color(0xFFD97706) else Color(0xFF4B5563), modifier = Modifier.size(14.dp))
                                Text("إجمالي المتبقي", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (remainingVal > 0) Color(0xFFB45309) else Color(0xFF374151))
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = FormatUtils.formatAmount(remainingVal),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = if (remainingVal > 0) Color(0xFFD97706) else Color(0xFF1F2937)
                            )
                        }
                    }
                }
            }

            // Search Bar (البحث برقم السند أو الفاتورة أو التاريخ)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("البحث برقم فاتورة السداد / السند أو الفاتورة:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        placeholder = { Text("أدخل رقم السداد، رقم الفاتورة، أو المحصل...", fontSize = 12.5.sp) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "عدسة البحث",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "مسح البحث",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        )
                    )
                }
            }

            // Date Range Filter Controls
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("فلترة الفترة الزمنية للكشف:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

                        OutlinedButton(
                            onClick = { showStartDatePicker = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = filterStartDate?.let { dateFmt.format(Date(it)) } ?: "من تاريخ",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        OutlinedButton(
                            onClick = { showEndDatePicker = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = filterEndDate?.let { dateFmt.format(Date(it)) } ?: "إلى تاريخ",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (filterStartDate != null || filterEndDate != null) {
                            IconButton(
                                onClick = {
                                    filterStartDate = null
                                    filterEndDate = null
                                },
                                modifier = Modifier
                                    .background(Color(0xFFFEF2F2), CircleShape)
                                    .size(36.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "مسح الفلتر", tint = Color.Red, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }

            // Ledger Transactions Section Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("سجل المعاملات والحركات (${ledgerItems.size})", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    if (clientPayments.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                coroutineScope.launch {
                                    val pdfPath = exportSelectedPaymentsToPdf(context, currentClient, clientPayments, settings)
                                    if (pdfPath != null) {
                                        ShareManager.shareFile(context, java.io.File(pdfPath), "application/pdf", "مشاركة كافة دفعات العميل")
                                    } else {
                                        Toast.makeText(context, "حدث خطأ أثناء تصدير الدفعات", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("مشاركة كافة الدفعات PDF", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Text("موثقة باليوم والساعة 12h", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Ledger List Items
            if (ledgerItems.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.ReceiptLong,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Text(
                                "لا توجد معاملات مسجلة في هذه الفترة",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(ledgerItems, key = { "${it.type}_${it.id}" }) { entry ->
                    val matchingPayment = remember(entry.id) { clientPayments.find { p -> p.id == entry.id } }
                    LedgerTransactionCard(
                        entry = entry,
                        currency = settings.currency,
                        onSmsClick = {
                            smsAmount = entry.credit
                            smsCurrency = entry.currency.ifBlank { settings.currency }
                            showSmsConfirmDialog = true
                        },
                        onSharePdfClick = if (entry.debit == 0.0 && matchingPayment != null) {
                            {
                                coroutineScope.launch {
                                    val pdfPath = exportSelectedPaymentsToPdf(context, currentClient, listOf(matchingPayment), settings)
                                    if (pdfPath != null) {
                                        ShareManager.shareFile(context, java.io.File(pdfPath), "application/pdf", "مشاركة إيصال الدفعة")
                                    } else {
                                        Toast.makeText(context, "حدث خطأ أثناء تصدير إيصال الدفعة", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        } else null,
                        onImageClick = { uri ->
                            previewReceiptImageUri = uri
                        }
                    )
                }
            }
        }

        // Bottom Action Footer
        Surface(
            shadowElevation = 8.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Button(
                    onClick = { showPaymentDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("record_payment_btn"),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Payments, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "تسجيل دفعة سداد جديدة (+)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }

    // PDF Export Date Range Selection Dialog
    if (showPdfDateDialog) {
        PdfDateRangeSelectionDialog(
            initialStartDate = filterStartDate,
            initialEndDate = filterEndDate,
            onDismiss = { showPdfDateDialog = false },
            onExportAll = {
                showPdfDateDialog = false
                exportClientStatementToPdf(
                    context = context,
                    client = currentClient,
                    startDate = null,
                    endDate = null,
                    ledgerItems = ledgerItems,
                    settings = settings
                )
            },
            onExportFiltered = { startMillis, endMillis ->
                showPdfDateDialog = false
                // Filter items for PDF export
                val filteredForPdf = ledgerItems.filter { it.date in startMillis..endMillis }
                exportClientStatementToPdf(
                    context = context,
                    client = currentClient,
                    startDate = startMillis,
                    endDate = endMillis,
                    ledgerItems = filteredForPdf,
                    settings = settings
                )
            }
        )
    }

    // Start Date Picker dual-mode dialog
    if (showStartDatePicker) {
        com.example.ui.components.UniversalDualDatePickerDialog(
            title = "تحديد تاريخ البداية",
            initialMillis = filterStartDate,
            onDismiss = { showStartDatePicker = false },
            onDateSelected = {
                filterStartDate = it
                showStartDatePicker = false
            }
        )
    }

    // End Date Picker dual-mode dialog
    if (showEndDatePicker) {
        com.example.ui.components.UniversalDualDatePickerDialog(
            title = "تحديد تاريخ النهاية",
            initialMillis = filterEndDate,
            onDismiss = { showEndDatePicker = false },
            onDateSelected = {
                filterEndDate = it
                showEndDatePicker = false
            }
        )
    }

    // Record Payment dialog
    if (showPaymentDialog) {
        val clientBaseCurrency = currentClient.currency.ifBlank { settings.currency.ifBlank { "الريال اليمني" } }
        PaymentRecordDialog(
            clientName = currentClient.name,
            currency = clientBaseCurrency,
            rates = exchangeRates,
            onDismiss = { showPaymentDialog = false },
            onSave = { amount, method, notes, voucherNumber, collectorName, transferNumber, receiptImageUri, paymentCurrency, customRate ->
                viewModel.addPayment(
                    clientId = currentClient.id,
                    amount = amount,
                    paymentMethod = method,
                    notes = notes,
                    currency = paymentCurrency,
                    voucherNumber = voucherNumber,
                    collectorName = collectorName,
                    transferNumber = transferNumber,
                    receiptImageUri = receiptImageUri,
                    customRate = customRate
                )
                showPaymentDialog = false
                smsAmount = amount
                smsCurrency = paymentCurrency
                showSmsConfirmDialog = true
                Toast.makeText(context, "تم تسجيل دفعة السداد بنجاح!", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Receipt Image Preview Dialog
    previewReceiptImageUri?.let { imgUri ->
        ReceiptImageViewerDialog(
            imageUri = imgUri,
            onDismiss = { previewReceiptImageUri = null }
        )
    }

    // SMS Confirmation Dialog with Permission request flow
    if (showSmsConfirmDialog) {
        val phoneNumber = currentClient.phone
        val paidAmount = smsAmount
        val remainingBalance = (currentClient.balance - paidAmount).coerceAtLeast(0.0)
        val storeName = settings.storeName
        val currency = smsCurrency.ifBlank { settings.currency }
        val currentDateTime = SimpleDateFormat("yyyy/MM/dd hh:mm a", Locale("ar")).format(Date())
        val smsMessage = "عميلنا العزيز ${currentClient.name}، تم استلام دفعة بقيمة $paidAmount $currency بتاريخ $currentDateTime. الرصيد المتبقي المستحق هو $remainingBalance $currency. شكراً لتعاملكم معنا - $storeName"

        var permissionGranted by remember {
            mutableStateOf(
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.SEND_SMS
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            )
        }

        val smsPermissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            permissionGranted = isGranted
            if (isGranted) {
                val sent = sendDirectSms(context, phoneNumber, smsMessage)
                if (sent) {
                    Toast.makeText(context, "تم إرسال رسالة كشف السداد بنجاح!", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "فشل إرسال الرسالة المباشرة، جاري فتح التطبيق الافتراضي...", Toast.LENGTH_LONG).show()
                    sendSmsViaIntent(context, phoneNumber, smsMessage)
                }
            } else {
                Toast.makeText(context, "تم رفض الصلاحية. جاري فتح تطبيق الرسائل لإرسالها يدوياً...", Toast.LENGTH_LONG).show()
                sendSmsViaIntent(context, phoneNumber, smsMessage)
            }
            showSmsConfirmDialog = false
        }

        AlertDialog(
            onDismissRequest = { showSmsConfirmDialog = false },
            title = { Text("إرسال كشف السداد عبر SMS", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("هل ترغب في إرسال تفاصيل الدفعة والمال المتبقي للعميل عبر رسالة SMS؟", fontSize = 14.sp)
                    Text("سيتم إرسال الرسالة إلى: ${phoneNumber.ifEmpty { "لا يوجد رقم مسجل!" }}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("نص الرسالة:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(smsMessage, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (phoneNumber.isBlank()) {
                            Toast.makeText(context, "لا يمكن الإرسال، رقم هاتف العميل فارغ!", Toast.LENGTH_SHORT).show()
                        } else {
                            sendSmsViaIntent(context, phoneNumber, smsMessage)
                        }
                        showSmsConfirmDialog = false
                    }
                ) {
                    Text("إرسال عبر تطبيق الرسائل", maxLines = 1, softWrap = false)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSmsConfirmDialog = false }) {
                    Text("إلغاء وتخطي", maxLines = 1, softWrap = false)
                }
            }
        )
    }
}

/**
 * Modern Card item for a single transaction in the ledger.
 * Features full Arabic day of week, 12h time, and clear debit/credit badges.
 */
@Composable
fun LedgerTransactionCard(
    entry: LedgerEntry,
    currency: String,
    onSmsClick: () -> Unit,
    onSharePdfClick: (() -> Unit)? = null,
    onImageClick: (String) -> Unit = {}
) {
    val isInvoice = entry.debit > 0
    val formattedTime = remember(entry.date) { formatArabicTimestamp(entry.date) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isInvoice) Color(0xFFFCA5A5).copy(alpha = 0.5f) else Color(0xFF6EE7B7).copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(
                                color = if (isInvoice) Color(0xFFFEF2F2) else Color(0xFFECFDF5),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isInvoice) Icons.Default.ReceiptLong else Icons.Default.Payments,
                            contentDescription = null,
                            tint = if (isInvoice) Color(0xFFDC2626) else Color(0xFF059669),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = entry.type,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            if (!isInvoice) {
                                Surface(
                                    color = Color(0xFFD1FAE5),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = entry.paymentMethod.ifEmpty { "نقدي" },
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF065F46),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = entry.label,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Amount Badge
                val itemCurrency = entry.currency.ifBlank { currency }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = if (isInvoice) "+${FormatUtils.formatAmount(entry.debit)} $itemCurrency"
                        else "-${FormatUtils.formatAmount(entry.credit)} $itemCurrency",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = if (isInvoice) Color(0xFFDC2626) else Color(0xFF059669),
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            // Payment metadata chips (Voucher, Collector, Transfer, Receipt Image)
            if (!isInvoice && (entry.voucherNumber != null || entry.collectorName != null || entry.transferNumber != null || entry.receiptImageUri != null)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    entry.voucherNumber?.let { vNum ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(Icons.Default.Receipt, contentDescription = null, modifier = Modifier.size(11.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("سند: #$vNum", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    entry.collectorName?.let { cName ->
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(11.dp), tint = MaterialTheme.colorScheme.primary)
                                Text(cName, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }

                    entry.transferNumber?.let { tNum ->
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(Icons.Default.ConfirmationNumber, contentDescription = null, modifier = Modifier.size(11.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                                Text("حوالة: #$tNum", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        }
                    }

                    entry.receiptImageUri?.let { imgUri ->
                        Surface(
                            color = Color(0xFFFEF3C7),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.clickable { onImageClick(imgUri) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(11.dp), tint = Color(0xFFB45309))
                                Text("عرض الإشعار", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB45309))
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            // Footer Timestamp & Running Balance
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Default.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formattedTime,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "الرصيد بعدها: ${FormatUtils.formatAmount(entry.runningBalance)}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (entry.runningBalance > 0) Color(0xFFDC2626) else Color(0xFF059669)
                    )

                    if (!isInvoice) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (onSharePdfClick != null) {
                                IconButton(
                                    onClick = onSharePdfClick,
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PictureAsPdf,
                                        contentDescription = "مشاركة PDF",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            IconButton(
                                onClick = onSmsClick,
                                modifier = Modifier.size(26.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Sms,
                                    contentDescription = "إرسال SMS",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Dialog component for 3 numerical input fields (Day, Month, Year).
 * Allows typing numbers directly or adjusting them.
 */
@Composable
fun DateInputFieldsRow(
    label: String,
    day: String,
    onDayChange: (String) -> Unit,
    month: String,
    onMonthChange: (String) -> Unit,
    year: String,
    onYearChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.primary
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Day Input
            OutlinedTextField(
                value = day,
                onValueChange = { if (it.length <= 2 && it.all { c -> c.isDigit() }) onDayChange(it) },
                label = { Text("اليوم", fontSize = 10.sp) },
                placeholder = { Text("01", fontSize = 10.sp) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.scrollToTopOnFocus().scrollToTopOnFocus().weight(1f),
                textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
            )

            Text("/", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

            // Month Input
            OutlinedTextField(
                value = month,
                onValueChange = { if (it.length <= 2 && it.all { c -> c.isDigit() }) onMonthChange(it) },
                label = { Text("الشهر", fontSize = 10.sp) },
                placeholder = { Text("07", fontSize = 10.sp) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.scrollToTopOnFocus().scrollToTopOnFocus().weight(1f),
                textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
            )

            Text("/", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

            // Year Input
            OutlinedTextField(
                value = year,
                onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) onYearChange(it) },
                label = { Text("السنة", fontSize = 10.sp) },
                placeholder = { Text("2026", fontSize = 10.sp) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.scrollToTopOnFocus().scrollToTopOnFocus().weight(1.3f),
                textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
            )
        }
    }
}

/**
 * PDF Export Date Selector Dialog.
 * Triggered when pressing the PDF button. Defaults to current date with separate Day/Month/Year input fields.
 */
@Composable
fun PdfDateRangeSelectionDialog(
    initialStartDate: Long?,
    initialEndDate: Long?,
    onDismiss: () -> Unit,
    onExportAll: () -> Unit,
    onExportFiltered: (startMillis: Long, endMillis: Long) -> Unit
) {
    val calNow = Calendar.getInstance()
    val currYear = calNow.get(Calendar.YEAR).toString()
    val currMonth = String.format("%02d", calNow.get(Calendar.MONTH) + 1)
    val currDay = String.format("%02d", calNow.get(Calendar.DAY_OF_MONTH))

    // Start Date State (Default: Day 1 of current month)
    var startDay by remember { mutableStateOf("01") }
    var startMonth by remember { mutableStateOf(currMonth) }
    var startYear by remember { mutableStateOf(currYear) }

    // End Date State (Default: Current day)
    var endDay by remember { mutableStateOf(currDay) }
    var endMonth by remember { mutableStateOf(currMonth) }
    var endYear by remember { mutableStateOf(currYear) }

    var errorMessage by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    imageVector = Icons.Default.PictureAsPdf,
                    contentDescription = null,
                    tint = Color(0xFFDC2626)
                )
                Text("تحديد تاريخ تقرير PDF", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "أدخل أرقام اليوم والشهر والسنة للتقرير (تلقائياً تاريخ اليوم، ويمكنك التعديل كتابة):",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Start Date Input Row
                DateInputFieldsRow(
                    label = "من تاريخ (البداية):",
                    day = startDay,
                    onDayChange = { startDay = it; errorMessage = "" },
                    month = startMonth,
                    onMonthChange = { startMonth = it; errorMessage = "" },
                    year = startYear,
                    onYearChange = { startYear = it; errorMessage = "" }
                )

                // End Date Input Row
                DateInputFieldsRow(
                    label = "إلى تاريخ (النهاية):",
                    day = endDay,
                    onDayChange = { endDay = it; errorMessage = "" },
                    month = endMonth,
                    onMonthChange = { endMonth = it; errorMessage = "" },
                    year = endYear,
                    onYearChange = { endYear = it; errorMessage = "" }
                )

                if (errorMessage.isNotEmpty()) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    try {
                        val sDay = startDay.toIntOrNull() ?: throw Exception()
                        val sMonth = startMonth.toIntOrNull() ?: throw Exception()
                        val sYear = startYear.toIntOrNull() ?: throw Exception()

                        val eDay = endDay.toIntOrNull() ?: throw Exception()
                        val eMonth = endMonth.toIntOrNull() ?: throw Exception()
                        val eYear = endYear.toIntOrNull() ?: throw Exception()

                        val startCal = Calendar.getInstance().apply {
                            isLenient = false
                            set(sYear, sMonth - 1, sDay, 0, 0, 0)
                            set(Calendar.MILLISECOND, 0)
                        }

                        val endCal = Calendar.getInstance().apply {
                            isLenient = false
                            set(eYear, eMonth - 1, eDay, 23, 59, 59)
                            set(Calendar.MILLISECOND, 999)
                        }

                        if (startCal.timeInMillis > endCal.timeInMillis) {
                            errorMessage = "تاريخ البداية يجب أن يكون قبل أو يساوي تاريخ النهاية!"
                            return@Button
                        }

                        onExportFiltered(startCal.timeInMillis, endCal.timeInMillis)
                    } catch (e: Exception) {
                        errorMessage = "الرجاء إدخال أرقام صحيحة لليوم (1-31) والشهر (1-12) والسنة."
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
            ) {
                Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("تصدير PDF بالفترة المحددة", fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
            }
        },
        dismissButton = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(
                    onClick = onExportAll,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("تقرير شامل لكل الأوقات", fontSize = 11.sp, maxLines = 1, softWrap = false)
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("إلغاء", maxLines = 1, softWrap = false)
                }
            }
        }
    )
}

/**
 * Manual Date Input Dialog for screen filter buttons.
 */
@Composable
fun ManualDateInputDialog(
    title: String,
    initialMillis: Long?,
    onDismiss: () -> Unit,
    onSelect: (Long) -> Unit
) {
    val cal = Calendar.getInstance().apply {
        if (initialMillis != null) timeInMillis = initialMillis
    }
    var day by remember { mutableStateOf(String.format("%02d", cal.get(Calendar.DAY_OF_MONTH))) }
    var month by remember { mutableStateOf(String.format("%02d", cal.get(Calendar.MONTH) + 1)) }
    var year by remember { mutableStateOf(cal.get(Calendar.YEAR).toString()) }
    var errorMessage by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "أدخل أو عدّل الأرقام يدوياً (اليوم / الشهر / السنة):",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                DateInputFieldsRow(
                    label = "التاريخ المطلوب:",
                    day = day,
                    onDayChange = { day = it; errorMessage = "" },
                    month = month,
                    onMonthChange = { month = it; errorMessage = "" },
                    year = year,
                    onYearChange = { year = it; errorMessage = "" }
                )
                if (errorMessage.isNotEmpty()) {
                    Text(errorMessage, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    try {
                        val d = day.toIntOrNull() ?: throw Exception()
                        val m = month.toIntOrNull() ?: throw Exception()
                        val y = year.toIntOrNull() ?: throw Exception()
                        val selectCal = Calendar.getInstance().apply {
                            isLenient = false
                            set(y, m - 1, d, 12, 0, 0)
                        }
                        onSelect(selectCal.timeInMillis)
                    } catch (e: Exception) {
                        errorMessage = "الرجاء أدخل تاريخ صحيح (اليوم 1-31، الشهر 1-12، السنة)."
                    }
                }
            ) {
                Text("تأكيد التاريخ", maxLines = 1, softWrap = false)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء", maxLines = 1, softWrap = false)
            }
        }
    )
}

fun getSavedCollectors(context: android.content.Context): List<String> {
    val prefs = context.getSharedPreferences("app_collectors_prefs", android.content.Context.MODE_PRIVATE)
    val saved = prefs.getString("collectors_list_csv", null)
    return if (!saved.isNullOrBlank()) {
        saved.split(";;").filter { it.isNotBlank() }
    } else {
        listOf("محمد المقطري", "رعد الحكيمي")
    }
}

fun saveCollector(context: android.content.Context, name: String) {
    val trimmed = name.trim()
    if (trimmed.isBlank()) return
    val current = getSavedCollectors(context).toMutableList()
    if (!current.contains(trimmed)) {
        current.add(trimmed)
        val prefs = context.getSharedPreferences("app_collectors_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().putString("collectors_list_csv", current.joinToString(";;")).apply()
    }
}

@Composable
fun ReceiptImageViewerDialog(
    imageUri: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("إشعار الحوالة / الإيداع", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "إغلاق")
                }
            }
        },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 220.dp, max = 420.dp),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = imageUri,
                    contentDescription = "صورة الإشعار الكاملة",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Fit
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("إغلاق", fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentRecordDialog(
    clientName: String,
    currency: String = "ر.ي",
    rates: List<com.example.data.model.ExchangeRate> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (
        amount: Double,
        method: String,
        notes: String,
        voucherNumber: String?,
        collectorName: String?,
        transferNumber: String?,
        receiptImageUri: String?,
        paymentCurrency: String,
        customRate: Double?
    ) -> Unit
) {
    val context = LocalContext.current
    var amountStr by remember { mutableStateOf("") }
    var method by remember { mutableStateOf("نقدي") } // "نقدي", "إيداع", "تحويل"
    var voucherNumber by remember { mutableStateOf("") }
    var collectorName by remember { mutableStateOf("محمد المقطري") }
    var transferNumber by remember { mutableStateOf("") }
    var receiptImageUri by remember { mutableStateOf<String?>(null) }
    var notes by remember { mutableStateOf("") }
    var selectedPayCurrency by remember { mutableStateOf(currency.ifBlank { "الريال اليمني" }) }
    var customRateStr by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    val collectorsList = remember {
        mutableStateListOf(*getSavedCollectors(context).toTypedArray())
    }
    var showAddCollectorDialog by remember { mutableStateOf(false) }
    var newCollectorInput by remember { mutableStateOf("") }
    var previewLocalImageUri by remember { mutableStateOf<String?>(null) }

    val customRateDouble = customRateStr.toDoubleOrNull()
    val conversionResult = remember(amountStr, selectedPayCurrency, currency, rates, customRateDouble) {
        val amt = amountStr.toDoubleOrNull() ?: 0.0
        if (amt > 0.0 && selectedPayCurrency != currency) {
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

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        receiptImageUri = uri?.toString()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(0.92f).widthIn(max = 520.dp),
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Payments,
                    contentDescription = null,
                    tint = Color(0xFF10B981)
                )
                Text("تسجيل دفعة سداد جديدة", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Client Info Chip
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "العميل: $clientName",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Amount Field and Currency Selection Row
                var expandedPayCurr by remember { mutableStateOf(false) }
                val currenciesList = listOf("الريال اليمني", "الريال السعودي", "الدولار الأمريكي")

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = amountStr,
                        onValueChange = { amountStr = it; isError = false },
                        label = { Text("مبلغ الدفعة *") },
                        isError = isError,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1.3f),
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
                            label = { Text("العملة") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedPayCurr) },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                            modifier = Modifier.scrollToTopOnFocus().scrollToTopOnFocus().menuAnchor().fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = expandedPayCurr,
                            onDismissRequest = { expandedPayCurr = false }
                        ) {
                            currenciesList.forEach { c ->
                                DropdownMenuItem(
                                    text = { Text(c) },
                                    onClick = {
                                        selectedPayCurrency = c
                                        expandedPayCurr = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Live Currency Conversion Section (نظام المصارفة التلقائية واليدوية)
                if (selectedPayCurrency != currency) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.CurrencyExchange, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Text("نظام المصارفة والصرف المالي:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }

                        Text(
                            text = "العملة المستلمة هي ($selectedPayCurrency) بينما عملة الحساب الرئيسية هي ($currency). سيتم تسديد الحساب بالعملة الرئيسية بناءً على سعر الصرف.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Option to type Custom rate manually to override system rate
                        OutlinedTextField(
                            value = customRateStr,
                            onValueChange = { customRateStr = it },
                            label = { Text("سعر الصرف اليدوي (اختياري لتعديل الصرف)") },
                            placeholder = {
                                Text("اتركه فارغاً لاستخدام سعر النظام التلقائي")
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp)
                        )

                        if (conversionResult != null) {
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("سعر الصرف المطبق:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(conversionResult.rateDescription, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    }
                                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text("المبلغ المصارف (المعادل بالعملة الرئيسية):", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                        Text(
                                            text = "${FormatUtils.formatAmount(conversionResult.convertedAmount)} ${FormatUtils.formatCurrencyCompact(currency)}",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color(0xFF10B981)
                                        )
                                    }
                                    Text(
                                        text = "* سيتم خصم وتسديد مبلغ قدره ${FormatUtils.formatAmount(conversionResult.convertedAmount)} ${FormatUtils.formatCurrencyCompact(currency)} من رصيد العميل الرئيسي مباشرة.",
                                        fontSize = 10.sp,
                                        color = Color.Gray
                                    )
                                }
                            }
                        }
                    }
                }

                // Payment Method Selector ("نقدي", "إيداع", "تحويل")
                Text("طريقة السداد / الدفع:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf("نقدي", "إيداع", "تحويل").forEach { opt ->
                        val selected = method == opt
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { method = opt },
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                            ),
                            border = if (selected) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(vertical = 10.dp, horizontal = 4.dp)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = opt,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Voucher Number (رقم السند)
                OutlinedTextField(
                    value = voucherNumber,
                    onValueChange = { voucherNumber = it },
                    label = { Text("رقم السند (سند قبض / استلام)") },
                    leadingIcon = {
                        Icon(Icons.Default.Receipt, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    placeholder = { Text("مثال: REC-1045", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.scrollToTopOnFocus().scrollToTopOnFocus().fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                // Collector Selection (اسم المُحصل)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("اسم المُحصل:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        TextButton(
                            onClick = {
                                newCollectorInput = ""
                                showAddCollectorDialog = true
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("إضافة مُحصل جديد", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Predefined & Added Collectors Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        collectorsList.forEach { name ->
                            val isSelected = collectorName == name
                            Surface(
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(8.dp),
                                border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                                modifier = Modifier.clickable {
                                    collectorName = if (isSelected) "" else name
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Text(
                                        text = name,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // Editable collector field
                    OutlinedTextField(
                        value = collectorName,
                        onValueChange = { collectorName = it },
                        label = { Text("المحصل المعتمد") },
                        leadingIcon = {
                            Icon(Icons.Default.Badge, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        placeholder = { Text("اختر من القائمة أو اكتب الاسم هنا", fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.scrollToTopOnFocus().scrollToTopOnFocus().fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                }

                // Transfer / Remittance Number (رقم الحوالة)
                OutlinedTextField(
                    value = transferNumber,
                    onValueChange = { transferNumber = it },
                    label = { Text("رقم الحوالة / رقم الإيداع البنكي") },
                    leadingIcon = {
                        Icon(Icons.Default.ConfirmationNumber, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    placeholder = { Text("مثال: 948271038", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.scrollToTopOnFocus().scrollToTopOnFocus().fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                // Receipt / Transfer Slip Image (مكان لصورة إشعار الحوالة أو الإيداع)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("صورة إشعار الحوالة أو الإيداع:", fontSize = 12.sp, fontWeight = FontWeight.Bold)

                    if (receiptImageUri == null) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { imagePickerLauncher.launch("image/*") }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.AddPhotoAlternate,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "إرفاق صورة إشعار الحوالة / السند من المعرض",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    } else {
                        Card(
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                AsyncImage(
                                    model = receiptImageUri,
                                    contentDescription = "معاينة إشعار السداد",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(130.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { previewLocalImageUri = receiptImageUri },
                                    contentScale = ContentScale.Crop
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(
                                        onClick = { previewLocalImageUri = receiptImageUri },
                                        contentPadding = PaddingValues(horizontal = 6.dp)
                                    ) {
                                        Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("معاينة كاملة", fontSize = 11.sp)
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        TextButton(
                                            onClick = { imagePickerLauncher.launch("image/*") },
                                            contentPadding = PaddingValues(horizontal = 6.dp)
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("تغيير", fontSize = 11.sp)
                                        }

                                        TextButton(
                                            onClick = { receiptImageUri = null },
                                            colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFDC2626)),
                                            contentPadding = PaddingValues(horizontal = 6.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("حذف", fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Notes Field
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("ملاحظات الدفعة") },
                    modifier = Modifier.scrollToTopOnFocus().scrollToTopOnFocus().fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    maxLines = 3
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amountStr.toDoubleOrNull()
                    if (amt == null || amt <= 0) {
                        isError = true
                    } else {
                        onSave(
                            amt,
                            method,
                            notes,
                            voucherNumber.ifBlank { null },
                            collectorName.ifBlank { null },
                            transferNumber.ifBlank { null },
                            receiptImageUri,
                            selectedPayCurrency,
                            customRateDouble
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
            ) {
                Text("تسجيل السداد", fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء", maxLines = 1, softWrap = false)
            }
        }
    )

    // Dialog for adding a new collector
    if (showAddCollectorDialog) {
        AlertDialog(
            onDismissRequest = { showAddCollectorDialog = false },
            title = { Text("إضافة مُحصل جديد", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newCollectorInput,
                    onValueChange = { newCollectorInput = it },
                    label = { Text("اسم المُحصل الجديد") },
                    placeholder = { Text("مثال: خالد العريقي") },
                    singleLine = true,
                    modifier = Modifier.scrollToTopOnFocus().scrollToTopOnFocus().fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = newCollectorInput.trim()
                        if (trimmed.isNotBlank()) {
                            saveCollector(context, trimmed)
                            if (!collectorsList.contains(trimmed)) {
                                collectorsList.add(trimmed)
                            }
                            collectorName = trimmed
                        }
                        showAddCollectorDialog = false
                    }
                ) {
                    Text("إضافة", maxLines = 1, softWrap = false)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddCollectorDialog = false }) {
                    Text("إلغاء", maxLines = 1, softWrap = false)
                }
            }
        )
    }

    // Full screen preview of local picked image
    previewLocalImageUri?.let { uri ->
        ReceiptImageViewerDialog(
            imageUri = uri,
            onDismiss = { previewLocalImageUri = null }
        )
    }
}

fun sendDirectSms(context: android.content.Context, phoneNumber: String, message: String): Boolean {
    return try {
        val smsManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            context.getSystemService(android.telephony.SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            android.telephony.SmsManager.getDefault()
        }
        smsManager.sendTextMessage(phoneNumber, null, message, null, null)
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

fun sendSmsViaIntent(context: android.content.Context, phoneNumber: String, message: String) {
    try {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("smsto:$phoneNumber")
            putExtra("sms_body", message)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "فشل فتح تطبيق الرسائل: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

data class LedgerEntry(
    val id: Int,
    val date: Long,
    val type: String,
    val label: String,
    val debit: Double, // Converted equivalent debit in customer base currency
    val credit: Double, // Converted equivalent credit in customer base currency
    val paymentMethod: String,
    val runningBalance: Double = 0.0,
    val voucherNumber: String? = null,
    val collectorName: String? = null,
    val transferNumber: String? = null,
    val receiptImageUri: String? = null,
    val currency: String = "الريال اليمني", // Original currency
    val originalAmount: Double = 0.0,
    val exchangeRate: Double = 1.0,
    val baseCurrency: String = "الريال اليمني"
)

fun exportClientStatementToPdf(
    context: android.content.Context,
    client: Client,
    startDate: Long?,
    endDate: Long?,
    ledgerItems: List<LedgerEntry>,
    settings: com.example.data.model.StoreSettings
) {
    val pdfDocument = android.graphics.pdf.PdfDocument()
    val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 Size
    val page = pdfDocument.startPage(pageInfo)
    val canvas = page.canvas

    val titlePaint = android.graphics.Paint().apply {
        textSize = 18f
        isFakeBoldText = true
        color = android.graphics.Color.BLACK
        textAlign = android.graphics.Paint.Align.CENTER
    }

    val textPaint = android.graphics.Paint().apply {
        textSize = 10f
        color = android.graphics.Color.BLACK
        textAlign = android.graphics.Paint.Align.RIGHT
    }

    val labelPaint = android.graphics.Paint().apply {
        textSize = 11f
        isFakeBoldText = true
        color = android.graphics.Color.DKGRAY
        textAlign = android.graphics.Paint.Align.RIGHT
    }

    val linePaint = android.graphics.Paint().apply {
        color = android.graphics.Color.LTGRAY
        strokeWidth = 1f
    }

    val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val periodStr = if (startDate != null && endDate != null) {
        "من تاريخ: ${dateFmt.format(Date(startDate))} إلى تاريخ: ${dateFmt.format(Date(endDate))}"
    } else {
        "تقرير شامل لجميع الأوقات"
    }

    var y = 45f

    canvas.drawText("كشف حساب تفصيلي للعميل", 595f / 2f, y, titlePaint)
    y += 25f

    canvas.drawText("المتجر: ${settings.storeName}", 545f, y, textPaint)
    y += 18f
    canvas.drawText("العميل: ${client.name}", 545f, y, textPaint)
    y += 18f
    if (client.phone.isNotEmpty()) {
        canvas.drawText("الهاتف: ${client.phone}", 545f, y, textPaint)
        y += 18f
    }
    canvas.drawText("الفترة: $periodStr", 545f, y, textPaint)
    y += 18f
    canvas.drawText("تاريخ الإصدار: ${formatArabicTimestamp(System.currentTimeMillis())}", 545f, y, textPaint)
    y += 22f

    canvas.drawLine(50f, y, 545f, y, linePaint)
    y += 20f

    val totalDebit = ledgerItems.sumOf { it.debit }
    val totalCredit = ledgerItems.sumOf { it.credit }
    val currentBalance = client.balance

    val clientMainCurrency = client.currency.ifBlank { settings.currency.ifBlank { "الريال اليمني" } }

    canvas.drawText("ملخص كشف الحساب:", 545f, y, labelPaint)
    y += 20f
    canvas.drawText("إجمالي الديون / الفواتير (+): ${FormatUtils.formatAmount(totalDebit)} $clientMainCurrency", 545f, y, textPaint)
    y += 18f
    canvas.drawText("إجمالي الدفعات / السداد (-): ${FormatUtils.formatAmount(totalCredit)} $clientMainCurrency", 545f, y, textPaint)
    y += 18f
    canvas.drawText("رصيد الحساب المتبقي الكلي: ${FormatUtils.formatAmount(currentBalance)} $clientMainCurrency", 545f, y, textPaint)
    y += 22f

    canvas.drawLine(50f, y, 545f, y, linePaint)
    y += 20f

    canvas.drawText("تفاصيل حركة الحساب بالتوقيت والساعة (12h):", 545f, y, labelPaint)
    y += 20f

    val colDateX = 140f
    val colTypeX = 220f
    val colLabelX = 350f
    val colDebitX = 420f
    val colCreditX = 480f
    val colBalX = 545f

    val headerPaint = android.graphics.Paint().apply {
        textSize = 10f
        isFakeBoldText = true
        color = android.graphics.Color.BLACK
        textAlign = android.graphics.Paint.Align.RIGHT
    }

    canvas.drawText("التاريخ والوقت", colDateX, y, headerPaint)
    canvas.drawText("العملية", colTypeX, y, headerPaint)
    canvas.drawText("البيان", colLabelX, y, headerPaint)
    canvas.drawText("دين (+)", colDebitX, y, headerPaint)
    canvas.drawText("سداد (-)", colCreditX, y, headerPaint)
    canvas.drawText("الرصيد", colBalX, y, headerPaint)

    y += 10f
    canvas.drawLine(50f, y, 545f, y, linePaint)
    y += 18f

    val visibleItems = ledgerItems.take(25)
    visibleItems.forEach { entry ->
        val fullTimeStr = formatArabicTimestamp(entry.date)
        canvas.drawText(fullTimeStr.take(18), colDateX, y, textPaint)
        canvas.drawText(entry.type, colTypeX, y, textPaint)

        val entryCurr = entry.currency.ifBlank { clientMainCurrency }
        val displayLabel = if (entry.label.length > 18) entry.label.take(15) + "..." else entry.label
        canvas.drawText(displayLabel, colLabelX, y, textPaint)

        canvas.drawText(if (entry.debit > 0) "${FormatUtils.formatAmount(entry.debit)} $entryCurr" else "-", colDebitX, y, textPaint)
        canvas.drawText(if (entry.credit > 0) "${FormatUtils.formatAmount(entry.credit)} $entryCurr" else "-", colCreditX, y, textPaint)
        canvas.drawText("${FormatUtils.formatAmount(entry.runningBalance)} $clientMainCurrency", colBalX, y, textPaint)

        y += 18f
    }

    pdfDocument.finishPage(page)

    try {
        val file = java.io.File(context.cacheDir, "statement_${client.name}_${System.currentTimeMillis()}.pdf")
        val outputStream = java.io.FileOutputStream(file)
        pdfDocument.writeTo(outputStream)
        pdfDocument.close()
        outputStream.close()

        ShareManager.sharePdf(
            context = context,
            file = file,
            chooserTitle = "📤 مشاركة كشف الحساب",
            extraText = "مرفق كشف حساب العميل ${client.name} للفترة: $periodStr",
            subject = "كشف حساب - ${client.name}"
        )
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "فشل تصدير كشف الحساب كـ PDF: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
