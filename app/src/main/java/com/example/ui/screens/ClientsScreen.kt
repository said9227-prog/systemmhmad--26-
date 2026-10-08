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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Client
import com.example.ui.viewmodel.AppViewModel
import com.example.util.FormatUtils
import com.example.util.PaymentLoyaltyUtils
import androidx.paging.compose.collectAsLazyPagingItems
import com.example.util.ShareManager
import kotlin.math.abs

/**
 * دالة مساعدة لتنسيق عدد العملاء وفق قواعد النحو العربي الصحيحة:
 * 0 -> لا يوجد عملاء
 * 1 -> عميل واحد
 * 2 -> عميلان
 * 3-10 -> X عملاء
 * 11+ -> X عميلًا
 */
fun formatArabicCustomerCount(count: Int): String {
    return when {
        count == 0 -> "لا يوجد عملاء"
        count == 1 -> "عميل واحد"
        count == 2 -> "عميلان"
        count in 3..10 -> "$count عملاء"
        else -> "$count عميلًا"
    }
}

/**
 * دالة مساعدة لتنسيق أيام التأخير بالنحو العربي:
 * 1 -> يوم واحد
 * 2 -> يومان
 * 3-10 -> X أيام
 * 11+ -> X يومًا
 */
fun formatArabicOverdueDays(days: Int): String {
    return when {
        days <= 0 -> "اليوم"
        days == 1 -> "يوم واحد"
        days == 2 -> "يومان"
        days in 3..10 -> "$days أيام"
        else -> "$days يومًا"
    }
}

// دالة تطبيع النصوص العربية لضمان دقة البحث الذكي
private fun normalizeArabic(text: String): String {
    return text
        .replace("[أإآا]".toRegex(), "ا")
        .replace("ة", "ه")
        .replace("ى", "ي")
        .replace("[ً-ٟ]".toRegex(), "") // إزالة التشكيل
        .trim()
        .lowercase()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ClientsScreen(
    onNavigateToAddClient: () -> Unit,
    viewModel: AppViewModel,
    initialShowAddDialog: Boolean = false,
    onNavigateToCreateInvoiceForClient: (Int) -> Unit,
    onNavigateToRecordPaymentForClient: (Int) -> Unit,
    onNavigateToClientStatement: (Int) -> Unit,
    onNavigateToClientProfile: ((Int) -> Unit)? = null
) {
    val context = LocalContext.current
    val clientsData by viewModel.clientsScreenData.collectAsState()
    val clientsList = clientsData.clients
    val invoicesList = clientsData.invoices
    val paymentsList = clientsData.payments
    val installmentsList = clientsData.installments
    val settings by viewModel.storeSettings.collectAsState()
    val exchangeRates = clientsData.exchangeRates
    val isDarkMode by viewModel.isDarkMode.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("جميع العملاء") }
    var selectedSort by remember { mutableStateOf("الاسم") }
    var showFilterSheet by remember { mutableStateOf(false) }
    var showTotalsSheet by remember { mutableStateOf(false) }

    val lazyClients = viewModel.clientsPagedFlow.collectAsLazyPagingItems()

    LaunchedEffect(searchQuery) {
        viewModel.updateClientsSearchQuery(searchQuery)
    }

    // Dialog States
    var clientForPayment by remember { mutableStateOf<Client?>(null) }
    var clientToEdit by remember { mutableStateOf<Client?>(null) }
    var clientToDelete by remember { mutableStateOf<Client?>(null) }
    var selectedClientForProfile by remember { mutableStateOf<Client?>(null) }

    // فتح شاشة إضافة عميل تلقائياً إذا طُلب ذلك
    LaunchedEffect(initialShowAddDialog) {
        if (initialShowAddDialog) {
            onNavigateToAddClient()
        }
    }

    // خريطة آخر حركة للعميل (O(N+M) فائق السرعة عبر مسح أحادي بدون حلقات متداخلة)
    val clientActivityMap = remember(invoicesList, paymentsList) {
        val lastDates = HashMap<Int, Long>()
        for (inv in invoicesList) {
            val curr = lastDates[inv.clientId] ?: 0L
            if (inv.date > curr) lastDates[inv.clientId] = inv.date
        }
        for (pay in paymentsList) {
            val curr = lastDates[pay.clientId] ?: 0L
            if (pay.date > curr) lastDates[pay.clientId] = pay.date
        }
        lastDates
    }

    // تجميع الحركات حسب العميل مسبقاً لمنع التكرار التربيعي
    val invoicesByClient = remember(invoicesList) { invoicesList.groupBy { it.clientId } }
    val paymentsByClient = remember(paymentsList) { paymentsList.groupBy { it.clientId } }
    val installmentsByClient = remember(installmentsList) { installmentsList.groupBy { it.clientId } }

    // ملفات تحليل الولاء وسلوك السداد (محسوبة بسرعة O(1) لكل عميل)
    val profilesMap = remember(clientsList, invoicesByClient, paymentsByClient, installmentsByClient, settings) {
        clientsList.associate { client ->
            client.id to PaymentLoyaltyUtils.analyzeClientBehaviorPreFiltered(
                client = client,
                clientInvoices = invoicesByClient[client.id] ?: emptyList(),
                clientPayments = paymentsByClient[client.id] ?: emptyList(),
                clientInstallments = installmentsByClient[client.id] ?: emptyList(),
                settings = settings
            )
        }
    }

    // تصفية وترتيب قائمة العملاء بكفاءة وسرعة
    val filteredAndSortedClients = remember(
        clientsList,
        searchQuery,
        selectedFilter,
        selectedSort,
        profilesMap,
        clientActivityMap
    ) {
        val normalizedQuery = normalizeArabic(searchQuery)

        val filtered = clientsList.filter { client ->
            val matchesSearch = if (normalizedQuery.isBlank()) {
                true
            } else {
                val normName = normalizeArabic(client.name)
                val normPhone = normalizeArabic(client.phone)
                val normAltPhone = normalizeArabic(client.altPhone)
                val normCompany = normalizeArabic(client.companyName)
                val normCustomerId = normalizeArabic(client.customerId)
                val normAddress = normalizeArabic(client.address)
                val formattedId = normalizeArabic("CUS-${client.id.toString().padStart(5, '0')}")

                normName.contains(normalizedQuery) ||
                normPhone.contains(normalizedQuery) ||
                normAltPhone.contains(normalizedQuery) ||
                normCompany.contains(normalizedQuery) ||
                normCustomerId.contains(normalizedQuery) ||
                normAddress.contains(normalizedQuery) ||
                formattedId.contains(normalizedQuery)
            }

            val profile = profilesMap[client.id]
            val matchesFilter = when (selectedFilter) {
                "جميع العملاء" -> true
                "نقدي" -> client.dealType == "نقدي"
                "آجل" -> client.dealType == "آجل"
                "مدين" -> client.balance > 0.001
                "دائن" -> client.balance < -0.001
                "الرصيد المتعادل" -> abs(client.balance) <= 0.001
                "متعثر" -> profile?.isOverdue == true || (client.balance > 0 && client.creditLimit > 0 && client.balance > client.creditLimit)
                "VIP" -> client.classification.equals("VIP", ignoreCase = true) || profile?.isLoyal == true
                else -> true
            }

            matchesSearch && matchesFilter
        }

        filtered.sortedWith(
            compareByDescending<Client> { it.isPinned }.thenComparator { c1, c2 ->
                when (selectedSort) {
                    "الاسم" -> c1.name.compareTo(c2.name)
                    "الأحدث إضافة" -> c2.id.compareTo(c1.id)
                    "الرصيد الأعلى" -> c2.balance.compareTo(c1.balance)
                    "الرصيد الأقل" -> c1.balance.compareTo(c2.balance)
                    "الأكثر مديونية" -> {
                        val d1 = if (c1.balance > 0) c1.balance else 0.0
                        val d2 = if (c2.balance > 0) c2.balance else 0.0
                        d2.compareTo(d1)
                    }
                    "آخر حركة مالية" -> {
                        val t1 = clientActivityMap[c1.id] ?: 0L
                        val t2 = clientActivityMap[c2.id] ?: 0L
                        t2.compareTo(t1)
                    }
                    else -> c1.name.compareTo(c2.name)
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // ─────────────────────────────────────────────────────────────
        // 1. شريط علوي مدمج فائق الكفاءة (بدون Top App Bar ضخم)
        // ─────────────────────────────────────────────────────────────
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            shadowElevation = 0.5.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // السطر الأول: العنوان المدمج + العداد العربي الدقيق + تبديل المظهر
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "العملاء",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                        ) {
                            Text(
                                text = formatArabicCustomerCount(clientsList.size),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    // زر تبديل الوضع الليلي والنهاري المدمج
                    IconButton(
                        onClick = { viewModel.toggleDarkMode() },
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("theme_toggle_btn")
                    ) {
                        Icon(
                            imageVector = if (isDarkMode) Icons.Default.WbSunny else Icons.Default.NightsStay,
                            contentDescription = if (isDarkMode) "تفعيل الوضع النهاري" else "تفعيل الوضع الليلي",
                            tint = if (isDarkMode) Color(0xFFF59E0B) else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // السطر الثاني: حقل البحث المدمج + زر الإجماليات + زر إضافة عميل
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // حقل البحث المدمج
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                text = "ابحث عن عميل...",
                                fontSize = 12.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = "بحث",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.scrollToTopOnFocus().scrollToTopOnFocus().size(18.dp)
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotBlank()) {
                                IconButton(
                                    onClick = { searchQuery = "" },
                                    modifier = Modifier.size(22.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "مسح", modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("search_clients_input"),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        )
                    )

                    // زر مخصص ومستقل للإجماليات 📊
                    FilledTonalButton(
                        onClick = { showTotalsSheet = true },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier
                            .height(44.dp)
                            .testTag("customer_totals_btn"),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Icon(
                            Icons.Default.BarChart,
                            contentDescription = "الإجماليات",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "الإجماليات",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    // زر مدمج لإضافة عميل +
                    Button(
                        onClick = onNavigateToAddClient,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        modifier = Modifier
                            .height(44.dp)
                            .testTag("add_client_btn")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "إضافة عميل", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "إضافة",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                // السطر الثالث: شريط الفلاتر والترتيب السريع فائق الصغر
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AssistChip(
                        onClick = { showFilterSheet = true },
                        label = { Text("ترتيب: $selectedSort", fontSize = 10.5.sp, fontWeight = FontWeight.Bold) },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null, modifier = Modifier.size(13.dp)) },
                        colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                        shape = RoundedCornerShape(7.dp),
                        modifier = Modifier.height(28.dp)
                    )

                    val filterOptions = listOf("جميع العملاء", "مدين", "دائن", "الرصيد المتعادل", "آجل", "نقدي", "متعثر", "VIP")
                    filterOptions.forEach { filter ->
                        FilterChip(
                            selected = selectedFilter == filter,
                            onClick = { selectedFilter = filter },
                            label = { Text(filter, fontSize = 10.5.sp, fontWeight = if (selectedFilter == filter) FontWeight.Bold else FontWeight.Normal) },
                            shape = RoundedCornerShape(7.dp),
                            modifier = Modifier.height(28.dp)
                        )
                    }
                }
            }
        }

        // ─────────────────────────────────────────────────────────────
        // 2. المحتوى الرئيسي: بطاقات العملاء تغطي المساحة الأكبر
        // ─────────────────────────────────────────────────────────────
        if (clientsList.isEmpty()) {
            // الحالة الفارغة لعدم وجود عملاء
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Outlined.People,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                    )
                    Text(
                        text = "لا يوجد عملاء",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "ابدأ بإضافة أول عميل لإدارة حساباته ومتابعة معاملاته.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = onNavigateToAddClient,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("+ إضافة عميل", fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else if (lazyClients.itemCount == 0) {
            // الحالة الفارغة للبحث
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Outlined.SearchOff,
                        contentDescription = null,
                        modifier = Modifier.size(52.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Text(
                        text = "لا توجد نتائج",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "لم يتم العثور على عميل مطابق للبحث.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = {
                            searchQuery = ""
                            selectedFilter = "جميع العملاء"
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("مسح البحث")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().testTag("clients_lazy_column"),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    count = lazyClients.itemCount,
                    key = { index -> lazyClients[index]?.id ?: index }
                ) { index ->
                    val client = lazyClients[index]
                    if (client != null) {
                        val lastDate = clientActivityMap[client.id] ?: 0L
                        val profile = profilesMap[client.id]

                        CustomerAccountingCard(
                            client = client,
                            currency = client.currency.ifBlank { settings.currency.ifBlank { "الريال اليمني" } },
                            lastActivityTimestamp = lastDate,
                            loyaltyProfile = profile,
                            onCardClick = {
                                if (onNavigateToClientProfile != null) {
                                    onNavigateToClientProfile(client.id)
                                } else {
                                    selectedClientForProfile = client
                                }
                            },
                            onRecordPayment = { clientForPayment = client },
                            onOpenStatement = { onNavigateToClientStatement(client.id) },
                            onNewInvoice = { onNavigateToCreateInvoiceForClient(client.id) },
                            onOpenTransactions = { onNavigateToClientStatement(client.id) },
                            onCallClient = {
                                if (client.phone.isNotBlank()) {
                                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${client.phone}"))
                                    context.startActivity(intent)
                                } else {
                                    Toast.makeText(context, "لا يوجد رقم هاتف مسجل للعميل", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onEditClient = { clientToEdit = client },
                            onTogglePin = { viewModel.toggleClientPin(client) },
                            onDeleteClient = { clientToDelete = client }
                        )
                    }
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 3. ورقة الإجماليات المالية المخصصة (Dedicated Totals BottomSheet)
    // ─────────────────────────────────────────────────────────────
    if (showTotalsSheet) {
        CustomerTotalsBottomSheet(
            clientsList = clientsList,
            invoicesList = invoicesList,
            paymentsList = paymentsList,
            profilesMap = profilesMap,
            currency = settings.currency,
            onDismiss = { showTotalsSheet = false }
        )
    }

    // ورقة الترتيب والتصفية
    if (showFilterSheet) {
        ModalBottomSheet(
            onDismissRequest = { showFilterSheet = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "ترتيب وتصفية العملاء",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Text("ترتيب حسب:", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                val sortOptions = listOf("الاسم", "الأحدث إضافة", "الرصيد الأعلى", "الرصيد الأقل", "الأكثر مديونية", "آخر حركة مالية")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    sortOptions.forEach { sortOption ->
                        FilterChip(
                            selected = selectedSort == sortOption,
                            onClick = { selectedSort = sortOption },
                            label = { Text(sortOption) }
                        )
                    }
                }

                HorizontalDivider()

                Text("تصفية الحسابات:", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                val filterOptions = listOf("جميع العملاء", "مدين", "دائن", "الرصيد المتعادل", "آجل", "نقدي", "متعثر", "VIP")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    filterOptions.forEach { filterOption ->
                        FilterChip(
                            selected = selectedFilter == filterOption,
                            onClick = { selectedFilter = filterOption },
                            label = { Text(filterOption) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Button(
                    onClick = { showFilterSheet = false },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("تطبيق الفلاتر", fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Quick Payment Dialog
    clientForPayment?.let { client ->
        val clientCurrency = client.currency.ifBlank { settings.currency.ifBlank { "الريال اليمني" } }
        QuickPaymentDialog(
            client = client,
            currency = clientCurrency,
            rates = exchangeRates,
            isAutoNumberingEnabled = settings.isAutoNumberingEnabled,
            defaultVoucherNumber = if (settings.lastPaymentNumber == 0) "1" else "${settings.lastPaymentNumber + 1}",
            onDismiss = { clientForPayment = null },
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
                clientForPayment = null
                Toast.makeText(context, "تم تسجيل سند القبض بنجاح", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Edit Client Dialog
    clientToEdit?.let { client ->
        EditClientDialog(
            client = client,
            onDismiss = { clientToEdit = null },
            onConfirm = { updatedClient ->
                viewModel.updateClient(updatedClient)
                clientToEdit = null
                Toast.makeText(context, "تم تحديث بيانات العميل بنجاح", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Delete Client Confirmation
    clientToDelete?.let { client ->
        AlertDialog(
            onDismissRequest = { clientToDelete = null },
            title = { Text("حذف العميل", fontWeight = FontWeight.Bold) },
            text = { Text("هل أنت متأكد من حذف '${client.name}'؟ سيتم حذف جميع بياناته.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteClient(client)
                        clientToDelete = null
                        Toast.makeText(context, "تم حذف العميل بنجاح", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("حذف", fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { clientToDelete = null }) {
                    Text("إلغاء", maxLines = 1, softWrap = false)
                }
            }
        )
    }

    // In-Screen Customer Profile
    selectedClientForProfile?.let { client ->
        ModalBottomSheet(
            onDismissRequest = { selectedClientForProfile = null },
            modifier = Modifier.fillMaxHeight(0.92f)
        ) {
            CustomerProfileScreen(
                viewModel = viewModel,
                clientId = client.id,
                onNavigateBack = { selectedClientForProfile = null },
                onNavigateToCreateInvoice = { id ->
                    selectedClientForProfile = null
                    onNavigateToCreateInvoiceForClient(id)
                },
                onNavigateToStatement = { id ->
                    selectedClientForProfile = null
                    onNavigateToClientStatement(id)
                }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
// بطاقة العميل المحاسبية المطورة (واسعة، مريحة، غير متراكمة)
// ─────────────────────────────────────────────────────────────
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CustomerAccountingCard(
    client: Client,
    currency: String,
    lastActivityTimestamp: Long,
    loyaltyProfile: com.example.util.ClientLoyaltyProfile? = null,
    onCardClick: () -> Unit,
    onRecordPayment: () -> Unit,
    onOpenStatement: () -> Unit,
    onNewInvoice: () -> Unit,
    onOpenTransactions: () -> Unit,
    onCallClient: () -> Unit,
    onEditClient: () -> Unit,
    onTogglePin: () -> Unit,
    onDeleteClient: () -> Unit
) {
    val context = LocalContext.current
    var showMoreMenu by remember { mutableStateOf(false) }

    val balance = client.balance
    val isDebit = balance > 0.001
    val isCredit = balance < -0.001
    val isZero = !isDebit && !isCredit

    val customerIdDisplay = if (client.customerId.isNotBlank()) client.customerId else "CUS-${client.id.toString().padStart(5, '0')}"

    // مصطلحات محاسبية صحيحة ورسمية: مدين / دائن / متعادل
    val statusText = when {
        isDebit -> "مدين"
        isCredit -> "دائن"
        else -> "متعادل"
    }

    val statusColor = when {
        isDebit -> MaterialTheme.colorScheme.error
        isCredit -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val statusBg = when {
        isDebit -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
        isCredit -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    val isOverdue = loyaltyProfile?.isOverdue == true

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onCardClick)
            .testTag("customer_card_${client.id}"),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = when {
            client.isPinned -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
            isOverdue -> BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
            else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 👤 السطر الأول: هوية العميل + الرصيد المالي البارز
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // اسم العميل ومعرفه
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = client.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (client.isPinned) {
                            Icon(
                                Icons.Filled.PushPin,
                                contentDescription = "مثبت في الأعلى",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        ) {
                            Text(
                                text = customerIdDisplay,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }

                        if (client.phone.isNotBlank()) {
                            Text(
                                text = "📞 ${client.phone}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    if (client.companyName.isNotBlank()) {
                        Text(
                            text = "🏢 ${client.companyName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 1.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // الرصيد المالي الحالي البارز
                Column(horizontalAlignment = Alignment.End) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = FormatUtils.formatWithCurrency(abs(balance), currency),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            softWrap = false
                        )
                        Surface(
                            shape = RoundedCornerShape(5.dp),
                            color = statusBg
                        ) {
                            Text(
                                text = statusText,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = statusColor,
                                fontSize = 11.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    if (client.dealType.isNotBlank() && client.dealType != "نقدي") {
                        Text(
                            text = "تعامل ${client.dealType}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }

            // ⚠️ شارات التأخير والولاء الذكية
            val hasLoyalBadges = loyaltyProfile != null && (loyaltyProfile.isLoyal || loyaltyProfile.isFastPayer || loyaltyProfile.isRegularPayer || loyaltyProfile.hasFixedPattern)

            if (isOverdue || hasLoyalBadges) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // أولوية التنبيه للتأخير
                    if (isOverdue && loyaltyProfile != null) {
                        Surface(
                            shape = RoundedCornerShape(5.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                            border = BorderStroke(0.7.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = "🔴 متأخر بالسداد — ${formatArabicOverdueDays(loyaltyProfile.overdueDays)}",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 10.5.sp
                            )
                        }
                    }

                    // شارات الولاء
                    if (loyaltyProfile != null) {
                        if (loyaltyProfile.isLoyal) {
                            BadgePill("⭐ عميل وفي", Color(0xFFB45309), Color(0xFFFEF3C7))
                        }
                        if (loyaltyProfile.isFastPayer) {
                            BadgePill("⚡ سريع السداد", Color(0xFF0369A1), Color(0xFFE0F2FE))
                        }
                        if (loyaltyProfile.isRegularPayer) {
                            BadgePill("✓ منتظم بالسداد", Color(0xFF047857), Color(0xFFD1FAE5))
                        }
                        if (loyaltyProfile.hasFixedPattern) {
                            BadgePill("🔄 سداد ثابت", Color(0xFF6D28D9), Color(0xFFEDE9FE))
                        }
                    }
                }
            }

            // ⚡ أزرار الإجراءات السريعة المدمجة (صف واحد أنيق لا يستهلك ارتفاع البطاقة)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // سداد
                CompactActionButton(
                    icon = Icons.Default.CreditCard,
                    label = "سداد",
                    onClick = onRecordPayment,
                    modifier = Modifier.weight(1f),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )

                // كشف الحساب
                CompactActionButton(
                    icon = Icons.Default.Description,
                    label = "كشف الحساب",
                    onClick = onOpenStatement,
                    modifier = Modifier.weight(1.3f),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // فاتورة
                CompactActionButton(
                    icon = Icons.Default.Receipt,
                    label = "فاتورة",
                    onClick = onNewInvoice,
                    modifier = Modifier.weight(1f),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // اتصال
                if (client.phone.isNotBlank()) {
                    Surface(
                        onClick = onCallClient,
                        modifier = Modifier.size(34.dp),
                        shape = RoundedCornerShape(7.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        contentColor = MaterialTheme.colorScheme.primary
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Phone,
                                contentDescription = "اتصال",
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }

                // قائمة المزيد
                Box {
                    Surface(
                        onClick = { showMoreMenu = true },
                        modifier = Modifier.size(34.dp),
                        shape = RoundedCornerShape(7.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "المزيد",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = showMoreMenu,
                        onDismissRequest = { showMoreMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("💰 الحركات المالية") },
                            leadingIcon = { Icon(Icons.Default.Paid, contentDescription = null) },
                            onClick = {
                                showMoreMenu = false
                                onOpenTransactions()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("✏️ تعديل البيانات") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = {
                                showMoreMenu = false
                                onEditClient()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(if (client.isPinned) "📌 إلغاء التثبيت" else "📌 تثبيت في الأعلى") },
                            leadingIcon = { Icon(if (client.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin, contentDescription = null) },
                            onClick = {
                                showMoreMenu = false
                                onTogglePin()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("📋 نسخ رقم العميل") },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                            onClick = {
                                showMoreMenu = false
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Customer ID", customerIdDisplay))
                                Toast.makeText(context, "تم نسخ رقم العميل: $customerIdDisplay", Toast.LENGTH_SHORT).show()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("📤 مشاركة بيانات العميل") },
                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                            onClick = {
                                showMoreMenu = false
                                val shareText = """
                                    اسم العميل: ${client.name}
                                    رقم العميل: $customerIdDisplay
                                    الهاتف: ${client.phone}
                                    الرصيد الحالي: ${FormatUtils.formatWithCurrency(abs(balance), currency)} ($statusText)
                                """.trimIndent()
                                ShareManager.shareText(
                                    context = context,
                                    text = shareText,
                                    chooserTitle = "📤 مشاركة بيانات العميل",
                                    subject = "بيانات العميل - ${client.name}"
                                )
                            }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("🗑️ حذف العميل", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            onClick = {
                                showMoreMenu = false
                                onDeleteClient()
                            }
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// ورقة الإجماليات المالية المنفصلة (CustomerTotalsBottomSheet)
// ─────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerTotalsBottomSheet(
    clientsList: List<Client>,
    invoicesList: List<com.example.data.model.Invoice>,
    paymentsList: List<com.example.data.model.Payment>,
    profilesMap: Map<Int, com.example.util.ClientLoyaltyProfile>,
    currency: String,
    onDismiss: () -> Unit
) {
    val totalDebits = remember(clientsList) {
        clientsList.filter { it.balance > 0 }.sumOf { it.balance }
    }
    val totalCredits = remember(clientsList) {
        clientsList.filter { it.balance < 0 }.sumOf { abs(it.balance) }
    }
    val netReceivables = totalDebits - totalCredits

    val totalSales = remember(invoicesList) {
        invoicesList.sumOf { it.totalAmount }
    }
    val totalPayments = remember(paymentsList) {
        paymentsList.sumOf { it.amount }
    }

    val totalOverdueAmount = remember(profilesMap) {
        profilesMap.values
            .filter { it.isOverdue }
            .sumOf { maxOf(0.0, it.client.balance) }
    }

    val debitCount = remember(clientsList) { clientsList.count { it.balance > 0.001 } }
    val creditCount = remember(clientsList) { clientsList.count { it.balance < -0.001 } }
    val zeroCount = remember(clientsList) { clientsList.count { abs(it.balance) <= 0.001 } }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.fillMaxHeight(0.93f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ترويسة الورقة
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.BarChart,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "الإجماليات المالية للعملاء",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "إغلاق")
                }
            }

            // 💰 1. الأرصدة
            TotalsSectionCard(title = "💰 الأرصدة") {
                TotalsRowItem(
                    label = "إجمالي الأرصدة المدينة (مدين)",
                    amount = FormatUtils.formatWithCurrency(totalDebits, currency),
                    color = MaterialTheme.colorScheme.error
                )
                TotalsRowItem(
                    label = "إجمالي الأرصدة الدائنة (دائن)",
                    amount = FormatUtils.formatWithCurrency(totalCredits, currency),
                    color = MaterialTheme.colorScheme.primary
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                TotalsRowItem(
                    label = "صافي المستحقات",
                    amount = "${FormatUtils.formatWithCurrency(abs(netReceivables), currency)} (${if (netReceivables > 0) "لنا" else if (netReceivables < 0) "علينا" else "متعادل"})",
                    color = if (netReceivables > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    isBold = true
                )
            }

            // 💵 2. المبيعات والمقبوضات
            TotalsSectionCard(title = "🧾 المبيعات والمقبوضات") {
                TotalsRowItem(
                    label = "إجمالي المبيعات",
                    amount = FormatUtils.formatWithCurrency(totalSales, currency),
                    color = MaterialTheme.colorScheme.onSurface
                )
                TotalsRowItem(
                    label = "إجمالي المقبوضات",
                    amount = FormatUtils.formatWithCurrency(totalPayments, currency),
                    color = Color(0xFF059669)
                )
            }

            // 📋 3. المستحقات
            TotalsSectionCard(title = "📋 المستحقات") {
                TotalsRowItem(
                    label = "إجمالي المبالغ المستحقة القائمة",
                    amount = FormatUtils.formatWithCurrency(totalDebits, currency),
                    color = MaterialTheme.colorScheme.error
                )
                TotalsRowItem(
                    label = "المستحقات المتأخرة بالسداد",
                    amount = FormatUtils.formatWithCurrency(totalOverdueAmount, currency),
                    color = MaterialTheme.colorScheme.error,
                    isBold = true
                )
            }

            // 👥 4. إحصائيات العملاء
            TotalsSectionCard(title = "👥 إحصائيات العملاء") {
                TotalsRowItem(
                    label = "إجمالي العملاء",
                    amount = formatArabicCustomerCount(clientsList.size),
                    color = MaterialTheme.colorScheme.primary,
                    isBold = true
                )
                TotalsRowItem(
                    label = "عملاء لديهم رصيد مدين",
                    amount = "$debitCount عملاء",
                    color = MaterialTheme.colorScheme.error
                )
                TotalsRowItem(
                    label = "عملاء لديهم رصيد دائن",
                    amount = "$creditCount عملاء",
                    color = MaterialTheme.colorScheme.primary
                )
                TotalsRowItem(
                    label = "حسابات برصيد متعادل",
                    amount = "$zeroCount عملاء",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("إغلاق", fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun TotalsSectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            content()
        }
    }
}

@Composable
fun TotalsRowItem(
    label: String,
    amount: String,
    color: Color = MaterialTheme.colorScheme.onSurface,
    isBold: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.5.sp,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = amount,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isBold) FontWeight.ExtraBold else FontWeight.Bold,
            color = color,
            fontSize = 13.5.sp,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
fun CompactActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(34.dp),
        shape = RoundedCornerShape(7.dp),
        color = containerColor,
        contentColor = contentColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun BadgePill(text: String, textColor: Color, bgColor: Color) {
    Surface(
        shape = RoundedCornerShape(5.dp),
        color = bgColor.copy(alpha = 0.85f),
        border = BorderStroke(0.6.dp, textColor.copy(alpha = 0.3f))
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = textColor,
            fontSize = 10.sp
        )
    }
}
