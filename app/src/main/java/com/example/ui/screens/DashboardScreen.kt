package com.example.ui.screens

import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.util.scrollToTopOnFocus
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Client
import com.example.data.model.*
import com.example.ui.viewmodel.ActivityItem
import com.example.ui.viewmodel.ActivityType
import com.example.ui.viewmodel.AppViewModel
import com.example.util.ArabicGrammarUtils
import com.example.util.FormatUtils
import com.example.util.ItemMovementAnalyzer
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(
    viewModel: AppViewModel,
    onNavigateToCreateInvoice: () -> Unit,
    onNavigateToAddClient: () -> Unit,
    onNavigateToAddItem: () -> Unit,
    onNavigateToItems: () -> Unit = {},
    onNavigateToInvoices: () -> Unit,
    onNavigateToClients: () -> Unit,
    onNavigateToInstallments: () -> Unit = {},
    onNavigateToClientProfile: (clientId: Int) -> Unit = {},
    onNavigateToCreateReturn: (String) -> Unit = {},
    onNavigateToReturnsList: () -> Unit = {},
    onNavigateToTopMovingItems: (itemId: Int?, itemName: String?) -> Unit = { _, _ -> }
) {
    val dashboardData by viewModel.dashboardScreenData.collectAsState()
    val dashboardState = dashboardData.dashboardState
    val stats = dashboardData.stats
    val settings = dashboardData.settings
    val activeReminders = dashboardData.activeReminders
    
    var showQuickAddMenu by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showEditStoreNameDialog by remember { mutableStateOf(false) }
    var showGlobalPaymentDialog by remember { mutableStateOf(false) }
    val clientsList = dashboardData.clients
    val itemMovementState = dashboardData.itemMovement

    var selectedItemForMovementDialog by remember { mutableStateOf<ItemMovementSummary?>(null) }

    val dashboardTopMovingItems = itemMovementState.first

    if (showAboutDialog) {
        AboutAppDialog(onDismiss = { showAboutDialog = false })
    }

    if (showEditStoreNameDialog) {
        EditStoreNameDialog(
            currentName = settings.storeName,
            onDismiss = { showEditStoreNameDialog = false },
            onSave = { newName ->
                viewModel.updateStoreName(newName)
                showEditStoreNameDialog = false
            }
        )
    }

    if (showGlobalPaymentDialog) {
        GlobalPaymentDialog(
            clients = clientsList,
            defaultCurrency = settings.currency,
            isAutoNumberingEnabled = settings.isAutoNumberingEnabled,
            defaultVoucherNumber = if (settings.lastPaymentNumber == 0) "1" else "${settings.lastPaymentNumber + 1}",
            onDismiss = { showGlobalPaymentDialog = false },
            onSave = { clientId, amount, method, notes, currency, voucherNum ->
                viewModel.addPayment(
                    clientId = clientId,
                    amount = amount,
                    paymentMethod = method,
                    notes = notes,
                    currency = currency,
                    voucherNumber = voucherNum
                )
                showGlobalPaymentDialog = false
            }
        )
    }

    // Dual-Type Installment Reminder System Popup (General & Customer-specific)
    // Strictly backed by Room persistence - One-Time Popup Logic
    if (activeReminders.isNotEmpty()) {
        com.example.ui.components.InstallmentReminderPopupDialog(
            reminders = activeReminders,
            storeName = settings.storeName,
            onDismissReminder = { reminderId ->
                viewModel.markReminderHandled(reminderId)
            },
            onNavigateToInstallments = { reminderId ->
                viewModel.markReminderHandled(reminderId)
                onNavigateToInstallments()
            },
            onNavigateToClient = { clientId, reminderId ->
                viewModel.markReminderHandled(reminderId)
                if (clientId > 0) {
                    onNavigateToClientProfile(clientId)
                } else {
                    onNavigateToClients()
                }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Scrim overlay behind FAB when menu is open
        AnimatedVisibility(
            visible = showQuickAddMenu,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.zIndex(1f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) {
                        showQuickAddMenu = false
                    }
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 90.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Currency & Time Period Filtering Controls
            item {
                CurrencyAndPeriodFilterSection(
                    selectedCurrencyCode = dashboardState.selectedCurrencyCode,
                    availableCurrencies = dashboardState.availableCurrencies,
                    selectedPeriod = dashboardState.selectedPeriod,
                    onSelectCurrency = { viewModel.setDashboardCurrencyFilter(it) },
                    onSelectPeriod = { viewModel.setDashboardPeriodFilter(it) },
                    totalRegisteredClients = dashboardState.totalRegisteredClients,
                    currentSummary = dashboardState.currentSummary
                )
            }

            // 🔥 Top Moving Items Section (Dashboard Widget) - Placed at the top of the dashboard
            item {
                val currentCurr = if (dashboardState.isAllCurrenciesMode) {
                    settings.currency
                } else {
                    dashboardState.currentSummary?.currency?.symbol
                        ?: dashboardState.summariesPerCurrency[dashboardState.selectedCurrencyCode]?.currency?.symbol
                        ?: settings.currency
                }
                DashboardTopMovingItemsSection(
                    items = dashboardTopMovingItems,
                    currency = currentCurr,
                    periodLabel = dashboardState.selectedPeriod.labelAr,
                    onSeeAll = { onNavigateToTopMovingItems(null, null) },
                    onItemClick = { item -> selectedItemForMovementDialog = item }
                )
            }

            // Content Switch: ALL CURRENCIES MODE vs SINGLE CURRENCY SCOPED MODE
            if (dashboardState.isAllCurrenciesMode) {
                // Multi-Currency Accounting Notice
                item {
                    AllCurrenciesExplainingBanner()
                }

                // Dedicated Card for Each Currency (No mixing!)
                items(dashboardState.summariesPerCurrency.values.toList()) { summary ->
                    CurrencyOverviewCard(
                        summary = summary,
                        onSelectCurrency = { viewModel.setDashboardCurrencyFilter(summary.currency.code) },
                        onNavigateToInstallments = onNavigateToInstallments
                    )
                }

                // Global Quick Counts (Invoices, Payments, Items)
                item {
                    QuickStatsRow(
                        invoicesCount = stats.invoicesCount,
                        paymentsCount = stats.paymentsCount,
                        itemsCount = stats.itemsCount,
                        onNavigateToInvoices = onNavigateToInvoices,
                        onNavigateToAddItem = onNavigateToAddItem,
                        onNavigateToItems = onNavigateToItems
                    )
                }

                // General Recent Activities
                item {
                    SectionHeader(title = "آخر العمليات والنشاطات (العامة)", onSeeAll = onNavigateToInvoices)
                }
                if (stats.recentActivities.isEmpty()) {
                    item {
                        EmptyGeneralActivitiesCard()
                    }
                } else {
                    items(stats.recentActivities.take(10)) { activity ->
                        ActivityListItem(activity = activity, currency = settings.currency)
                    }
                }

            } else {
                // SINGLE CURRENCY SCOPED MODE
                val summary = dashboardState.currentSummary
                    ?: dashboardState.summariesPerCurrency[dashboardState.selectedCurrencyCode]
                    ?: CurrencyFinancialSummary(currency = CurrencyMeta.from(dashboardState.selectedCurrencyCode))

                // Currency Scoped Customers Breakdown Card (With Arabic Grammar)
                item {
                    CurrencyCustomerCard(
                        summary = summary,
                        onNavigateToClients = onNavigateToClients
                    )
                }

                // Core 4 Financial Bento Cards Grid
                item {
                    SingleCurrencyFinancialCardsGrid(summary = summary)
                }

                // Financial Analysis & Collection Efficiency Card
                item {
                    CollectionEfficiencyCard(summary = summary)
                }

                // Installments Health in this currency
                if (summary.totalInstallmentsCount > 0 || summary.installmentsDueTodayCount > 0 || summary.installmentsOverdueCount > 0) {
                    item {
                        InstallmentHealthCard(
                            summary = summary,
                            onNavigateToInstallments = onNavigateToInstallments
                        )
                    }
                }

                // Quick Counts (Invoices, Payments, Items)
                item {
                    QuickStatsRow(
                        invoicesCount = summary.invoicesCount,
                        paymentsCount = summary.paymentsCount,
                        itemsCount = stats.itemsCount,
                        onNavigateToInvoices = onNavigateToInvoices,
                        onNavigateToAddItem = onNavigateToAddItem,
                        onNavigateToItems = onNavigateToItems
                    )
                }

                // Top Debtors strictly in this currency
                if (summary.topDebtors.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = "كبار المدينين بال${summary.currency.nameAr}",
                            onSeeAll = onNavigateToClients
                        )
                    }
                    items(summary.topDebtors) { debtorInfo ->
                        CurrencyDebtorListItem(
                            debtorInfo = debtorInfo,
                            onClientClick = { onNavigateToClientProfile(debtorInfo.client.id) }
                        )
                    }
                }

                // Recent Activities strictly for this currency & period
                item {
                    SectionHeader(
                        title = "العمليات بال${summary.currency.nameAr} (${dashboardState.selectedPeriod.labelAr})",
                        onSeeAll = onNavigateToInvoices
                    )
                }
                if (summary.recentActivities.isEmpty()) {
                    item {
                        EmptyCurrencyActivitiesCard(
                            currencyName = summary.currency.nameAr,
                            periodLabel = dashboardState.selectedPeriod.labelAr,
                            onCreateInvoice = onNavigateToCreateInvoice,
                            onAddPayment = { showGlobalPaymentDialog = true }
                        )
                    }
                } else {
                    items(summary.recentActivities) { activity ->
                        ActivityListItem(activity = activity, currency = summary.currency.symbol)
                    }
                }
            }
        }

        // Quick Add Floating Menu
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 20.dp, end = 20.dp)
                .zIndex(10f)
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AnimatedVisibility(
                    visible = showQuickAddMenu,
                    enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut()
                ) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        QuickAddActionItem(
                            text = "سداد دفعة",
                            icon = Icons.Default.Payments,
                            color = Color(0xFF10B981),
                            onClick = {
                                showQuickAddMenu = false
                                showGlobalPaymentDialog = true
                            }
                        )
                        QuickAddActionItem(
                            text = "فاتورة جديدة",
                            icon = Icons.Default.PostAdd,
                            color = Color(0xFF4F46E5),
                            onClick = {
                                showQuickAddMenu = false
                                onNavigateToCreateInvoice()
                            }
                        )
                        QuickAddActionItem(
                            text = "إضافة عميل",
                            icon = Icons.Default.PersonAdd,
                            color = Color(0xFF10B981),
                            onClick = {
                                showQuickAddMenu = false
                                onNavigateToAddClient()
                            }
                        )
                        QuickAddActionItem(
                            text = "إضافة صنف",
                            icon = Icons.Default.AddBox,
                            color = Color(0xFFF59E0B),
                            onClick = {
                                showQuickAddMenu = false
                                onNavigateToAddItem()
                            }
                        )
                        QuickAddActionItem(
                            text = "مرتجع مبيعات",
                            icon = Icons.Default.AssignmentReturn,
                            color = Color(0xFFE63946),
                            onClick = {
                                showQuickAddMenu = false
                                onNavigateToCreateReturn("CUSTOMER")
                            }
                        )
                        QuickAddActionItem(
                            text = "مرتجع مشتريات",
                            icon = Icons.Default.AssignmentReturned,
                            color = Color(0xFF1D3557),
                            onClick = {
                                showQuickAddMenu = false
                                onNavigateToCreateReturn("PURCHASE")
                            }
                        )
                        QuickAddActionItem(
                            text = "الأقساط والتنبيهات",
                            icon = Icons.Default.Alarm,
                            color = Color(0xFFEF4444),
                            onClick = {
                                showQuickAddMenu = false
                                onNavigateToInstallments()
                            }
                        )
                        QuickAddActionItem(
                            text = "سجل المرتجعات",
                            icon = Icons.Default.List,
                            color = Color(0xFF6366F1),
                            onClick = {
                                showQuickAddMenu = false
                                onNavigateToReturnsList()
                            }
                        )
                    }
                }

                FloatingActionButton(
                    onClick = { showQuickAddMenu = !showQuickAddMenu },
                    containerColor = Color(0xFFFFD700),
                    contentColor = Color.Black,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 10.dp),
                    modifier = Modifier.testTag("dashboard_quick_add_fab")
                ) {
                    Icon(
                        imageVector = if (showQuickAddMenu) Icons.Default.Close else Icons.Default.Add,
                        contentDescription = "قائمة الإضافة السريعة"
                    )
                }
            }
        }
    }

    // Customer Breakdown Dialog on click from Dashboard
    selectedItemForMovementDialog?.let { itemSummary ->
        ItemCustomersBreakdownDialog(
            itemSummary = itemSummary,
            currency = if (dashboardState.selectedCurrencyCode == "ALL") settings.currency else dashboardState.selectedCurrencyCode,
            periodLabel = dashboardState.selectedPeriod.labelAr,
            onDismiss = { selectedItemForMovementDialog = null },
            onNavigateToInvoice = { invId ->
                selectedItemForMovementDialog = null
                onNavigateToInvoices()
            },
            onNavigateToClient = { cId ->
                selectedItemForMovementDialog = null
                onNavigateToClientProfile(cId)
            }
        )
    }
}

@Composable
fun StoreHeader(
    storeName: String,
    onEditClick: () -> Unit,
    onAboutClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Storefront,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "مرحباً بك",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = storeName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = onEditClick, modifier = Modifier.size(24.dp).padding(start = 4.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "تعديل", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        IconButton(
            onClick = onAboutClick,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                .size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "حول",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrencyAndPeriodFilterSection(
    selectedCurrencyCode: String,
    availableCurrencies: List<CurrencyMeta>,
    selectedPeriod: DashboardPeriod,
    onSelectCurrency: (String) -> Unit,
    onSelectPeriod: (DashboardPeriod) -> Unit,
    totalRegisteredClients: Int,
    currentSummary: CurrencyFinancialSummary?
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Horizontal scrolling Currency Chips
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                val isSelected = selectedCurrencyCode == "ALL"
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelectCurrency("ALL") },
                    label = { Text("🌎 جميع العملات", fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                    shape = RoundedCornerShape(10.dp)
                )
            }
            items(availableCurrencies) { curr ->
                val isSelected = selectedCurrencyCode == curr.code
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelectCurrency(curr.code) },
                    label = { Text("${curr.flag} ${curr.nameAr}", fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                    shape = RoundedCornerShape(10.dp)
                )
            }
        }

        // Horizontal scrolling Time Period Chips
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(DashboardPeriod.values()) { period ->
                val isSelected = selectedPeriod == period
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelectPeriod(period) },
                    label = { Text(period.labelAr, fontSize = 12.sp) },
                    shape = RoundedCornerShape(10.dp)
                )
            }
        }
    }
}

@Composable
fun AllCurrenciesExplainingBanner() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "فصل الحسابات حسب العملة (عدم خلط الأرصدة)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "لكل عملة بطاقتها المحاسبية الخاصة لتفادي أي خلط رياضي بين العملات المختلفة. انقر على أي بطاقة للانتقال للتحليل التفصيلي.",
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun CurrencyOverviewCard(
    summary: CurrencyFinancialSummary,
    onSelectCurrency: () -> Unit,
    onNavigateToInstallments: () -> Unit
) {
    val curr = summary.currency

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelectCurrency() },
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = curr.flag, fontSize = 24.sp)
                    Text(
                        text = curr.nameAr,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Icon(Icons.Default.ChevronLeft, contentDescription = "عرض التفاصيل", tint = MaterialTheme.colorScheme.primary)
            }

            // Main Metric: Net Balance
            val netColor = when {
                summary.netBalance > 0 -> Color(0xFFEF4444)
                summary.netBalance < 0 -> Color(0xFF10B981)
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Column {
                Text("صافي الرصيد", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = "${com.example.util.FormatUtils.formatAmount(summary.netBalance)}\u00A0${curr.symbol}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = netColor,
                    maxLines = 1,
                    softWrap = false
                )
            }

            // Secondary Metrics Row (Sales, Receipts, Debts)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricItem(label = "مبيعات", amount = summary.totalSales, symbol = curr.symbol, color = Color(0xFF4F46E5))
                MetricItem(label = "مقبوضات", amount = summary.totalReceipts, symbol = curr.symbol, color = Color(0xFF059669))
                MetricItem(label = "ديون", amount = summary.totalDebts, symbol = curr.symbol, color = Color(0xFFDC2626))
            }

            // Installment alerts
            if (summary.installmentsOverdueCount > 0 || summary.installmentsDueTodayCount > 0) {
                Surface(
                    color = Color(0xFFFEF2F2),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().clickable { onNavigateToInstallments() }
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(16.dp))
                        Text(
                            text = "أقساط مستحقة/متأخرة (${summary.installmentsDueTodayCount + summary.installmentsOverdueCount})",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFDC2626),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MetricItem(label: String, amount: Double, symbol: String, color: Color) {
    Column {
        Text(text = label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = "${com.example.util.FormatUtils.formatAmount(amount)}\u00A0$symbol",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
fun CurrencyCustomerCard(
    summary: CurrencyFinancialSummary,
    onNavigateToClients: () -> Unit
) {
    val curr = summary.currency
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
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
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Groups,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "عملاء نطاق العملة (${curr.nameAr})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "حصر العملاء الذين لديهم عمليات أو أرصدة بهذه العملة فقط",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                TextButton(onClick = onNavigateToClients) {
                    Text("سجل العملاء", fontSize = 11.sp)
                }
            }

            // Big customer count with proper Arabic grammar
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "العدد الإجمالي النشط:",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = ArabicGrammarUtils.formatCustomerCount(summary.clientCount),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Breakdown chips
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(
                            color = Color(0xFFEF4444).copy(alpha = 0.12f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = ArabicGrammarUtils.formatDebtorCount(summary.debtorsCount),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFDC2626),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        if (summary.creditorsCount > 0) {
                            Surface(
                                color = Color(0xFF10B981).copy(alpha = 0.12f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = ArabicGrammarUtils.formatCreditorCount(summary.creditorsCount),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF059669),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "${summary.balancedCount} متزن",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SingleCurrencyFinancialCardsGrid(summary: CurrencyFinancialSummary) {
    val curr = summary.currency
    
    // Net Balance Hero
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("صافي الرصيد", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val netColor = when {
                summary.netBalance > 0 -> Color(0xFFEF4444)
                summary.netBalance < 0 -> Color(0xFF10B981)
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Text(
                text = "${com.example.util.FormatUtils.formatAmount(summary.netBalance)}\u00A0${curr.symbol}",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = netColor,
                maxLines = 1,
                softWrap = false
            )
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    // 3 Mini Cards Row
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Sales
        Surface(
            modifier = Modifier.weight(1f),
            color = Color(0xFF6366F1).copy(alpha = 0.1f),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("المبيعات", fontSize = 11.sp, color = Color(0xFF4F46E5))
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = com.example.util.FormatUtils.formatAmount(summary.totalSales),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4F46E5),
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
        
        // Receipts
        Surface(
            modifier = Modifier.weight(1f),
            color = Color(0xFF10B981).copy(alpha = 0.1f),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("المقبوضات", fontSize = 11.sp, color = Color(0xFF059669))
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = com.example.util.FormatUtils.formatAmount(summary.totalReceipts),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF059669),
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
        
        // Debts
        Surface(
            modifier = Modifier.weight(1f),
            color = Color(0xFFEF4444).copy(alpha = 0.1f),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("الديون", fontSize = 11.sp, color = Color(0xFFDC2626))
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = com.example.util.FormatUtils.formatAmount(summary.totalDebts),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFDC2626),
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
fun CollectionEfficiencyCard(summary: CurrencyFinancialSummary) {
    val curr = summary.currency
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant
        )
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
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Percent,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "نسبة التحصيل وكفاءة الفواتير",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                Surface(
                    color = if (summary.collectionRate >= 60) Color(0xFF10B981).copy(alpha = 0.15f) else Color(0xFFF59E0B).copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "${FormatUtils.formatAmount(summary.collectionRate)}%",
                        color = if (summary.collectionRate >= 60) Color(0xFF059669) else Color(0xFFD97706),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            // Progress bar
            LinearProgressIndicator(
                progress = (summary.collectionRate.toFloat() / 100f).coerceIn(0f, 1f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = if (summary.collectionRate >= 60) Color(0xFF10B981) else Color(0xFFF59E0B),
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            // Invoice status breakdown
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Paid
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "مسددة بالكامل",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${summary.paidInvoicesCount}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF10B981)
                    )
                }
                // Partial
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "مسددة جزئياً",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${summary.partialInvoicesCount}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF59E0B)
                    )
                }
                // Unpaid
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "غير مسددة",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${summary.unpaidInvoicesCount}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFEF4444)
                    )
                }
            }
        }
    }
}

@Composable
fun InstallmentHealthCard(
    summary: CurrencyFinancialSummary,
    onNavigateToInstallments: () -> Unit
) {
    val curr = summary.currency
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Alarm,
                        contentDescription = null,
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "أقساط العملة (${curr.nameAr})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                TextButton(onClick = onNavigateToInstallments) {
                    Text("إدارة الأقساط", fontSize = 11.sp)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Overdue
                Surface(
                    modifier = Modifier.weight(1f),
                    color = Color(0xFFEF4444).copy(alpha = 0.1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(text = "متأخرة", fontSize = 10.sp, color = Color(0xFFDC2626))
                        Text(
                            text = "${summary.installmentsOverdueCount} أقساط",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFDC2626)
                        )
                        Text(
                            text = "${FormatUtils.formatAmount(summary.installmentsOverdueAmount)} ${curr.symbol}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFFDC2626)
                        )
                    }
                }

                // Due Today
                Surface(
                    modifier = Modifier.weight(1f),
                    color = Color(0xFFF59E0B).copy(alpha = 0.1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(text = "مستحقة اليوم", fontSize = 10.sp, color = Color(0xFFD97706))
                        Text(
                            text = "${summary.installmentsDueTodayCount} أقساط",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFD97706)
                        )
                        Text(
                            text = "${FormatUtils.formatAmount(summary.installmentsDueTodayAmount)} ${curr.symbol}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFFD97706)
                        )
                    }
                }

                // Total Remaining
                Surface(
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(text = "المتبقي الإجمالي", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = "${summary.totalInstallmentsCount} أقساط",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${FormatUtils.formatAmount(summary.totalInstallmentsRemainingAmount)} ${curr.symbol}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun QuickStatsRow(
    invoicesCount: Int,
    paymentsCount: Int,
    itemsCount: Int,
    onNavigateToInvoices: () -> Unit,
    onNavigateToAddItem: () -> Unit,
    onNavigateToItems: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        MiniStatCard(
            title = "الفواتير",
            value = "$invoicesCount",
            icon = Icons.Default.Receipt,
            color = Color(0xFF6750A4),
            modifier = Modifier.weight(1f),
            onClick = onNavigateToInvoices
        )
        MiniStatCard(
            title = "المدفوعات",
            value = "$paymentsCount",
            icon = Icons.Default.Payments,
            color = Color(0xFF0D9488),
            modifier = Modifier.weight(1f),
            onClick = onNavigateToInvoices
        )
        MiniStatCard(
            title = "الأصناف",
            value = "$itemsCount",
            icon = Icons.Default.Inventory2,
            color = Color(0xFFD97706),
            modifier = Modifier.weight(1f),
            onClick = onNavigateToItems
        )
    }
}

@Composable
fun CurrencyDebtorListItem(
    debtorInfo: CurrencyDebtorInfo,
    onClientClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClientClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color(0xFFFEE2E2), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = debtorInfo.client.name.take(1),
                        color = Color(0xFFDC2626),
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = debtorInfo.client.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "الهاتف: ${debtorInfo.client.phone.ifBlank { "غير مسجل" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${FormatUtils.formatAmount(debtorInfo.debtAmount)} ${debtorInfo.currency.symbol}",
                    color = Color(0xFFEF4444),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp
                )
                Text(
                    text = "${debtorInfo.invoicesCount} فواتير",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun EmptyGeneralActivitiesCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "لا توجد عمليات مسجلة حالياً.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
fun EmptyCurrencyActivitiesCard(
    currencyName: String,
    periodLabel: String,
    onCreateInvoice: () -> Unit,
    onAddPayment: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.EventBusy,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(36.dp)
            )
            Text(
                text = "لا توجد عمليات مسجلة بال$currencyName خلال $periodLabel.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onCreateInvoice,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("فاتورة جديدة", fontSize = 11.sp)
                }
                OutlinedButton(
                    onClick = onAddPayment,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("سداد دفعة", fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
fun FinancialCardsGrid(
    currency: String,
    totalDebt: Double,
    totalReceived: Double,
    netBalance: Double,
    totalClients: Int,
    currencyDebts: Map<String, Double> = emptyMap(),
    currencyPayments: Map<String, Double> = emptyMap()
) {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()

    val debtBg = if (isDark) Color(0xFF450A0A) else Color(0xFFFDF2F2)
    val debtText = if (isDark) Color(0xFFFECACA) else Color(0xFF9B1C1C)
    val debtBorder = if (isDark) Color(0xFF7F1D1D) else Color(0xFFFDE2E2)

    val recBg = if (isDark) Color(0xFF064E3B) else Color(0xFFECFDF5)
    val recText = if (isDark) Color(0xFFA7F3D0) else Color(0xFF047857)
    val recBorder = if (isDark) Color(0xFF065F46) else Color(0xFFD1FAE5)

    val balBg = if (isDark) Color(0xFF1E1B4B) else Color(0xFFEEF2FF)
    val balText = if (isDark) Color(0xFFC7D2FE) else Color(0xFF3730A3)
    val balBorder = if (isDark) Color(0xFF312E81) else Color(0xFFE0E7FF)

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Main Debts Outstanding Card (Large) - Bento Style with Currency Breakdown
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = debtBg),
            border = androidx.compose.foundation.BorderStroke(1.dp, debtBorder)
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "إجمالي ديون العملاء",
                        color = debtText.copy(alpha = 0.8f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(
                        imageVector = Icons.Default.TrendingUp,
                        contentDescription = null,
                        tint = debtText.copy(alpha = 0.8f),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))

                // Breakdown by currencies
                val availableDebts = currencyDebts.filter { it.value > 0 }
                if (availableDebts.isNotEmpty()) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        availableDebts.forEach { (curr, amt) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = curr,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = debtText.copy(alpha = 0.9f)
                                )
                                Surface(
                                    color = debtText.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "${FormatUtils.formatAmount(amt)} ${FormatUtils.formatCurrencyCompact(curr)}",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = debtText,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        text = "${FormatUtils.formatAmount(totalDebt)} ${FormatUtils.formatCurrencyCompact(currency)}",
                        color = debtText,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "مبالغ مستحقة الدفع من $totalClients عملاء",
                    color = debtText.copy(alpha = 0.9f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Half width cards for Received and Net
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Received Card with Currency Details
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = recBg),
                border = androidx.compose.foundation.BorderStroke(1.dp, recBorder)
            ) {
                Column(
                    modifier = Modifier.padding(8.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "إجمالي المقبوضات",
                            color = recText.copy(alpha = 0.8f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            imageVector = Icons.Default.Payments,
                            contentDescription = null,
                            tint = recText.copy(alpha = 0.8f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))

                    val availablePayments = currencyPayments.filter { it.value > 0 }
                    if (availablePayments.isNotEmpty()) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            availablePayments.forEach { (curr, amt) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(curr.take(8), fontSize = 11.sp, color = recText.copy(alpha = 0.8f), fontWeight = FontWeight.Medium)
                                    Text(FormatUtils.formatAmount(amt), fontSize = 12.sp, color = recText, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "${FormatUtils.formatAmount(totalReceived)} ${FormatUtils.formatCurrencyCompact(currency)}",
                            color = recText,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            // Net Balance Card
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = balBg),
                border = androidx.compose.foundation.BorderStroke(1.dp, balBorder)
            ) {
                Column(
                    modifier = Modifier.padding(8.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "الرصيد العام",
                            color = balText.copy(alpha = 0.8f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            imageVector = Icons.Default.AccountBalanceWallet,
                            contentDescription = null,
                            tint = balText.copy(alpha = 0.8f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${FormatUtils.formatAmount(netBalance)} ${FormatUtils.formatCurrencyCompact(currency)}",
                        color = balText,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        softWrap = false
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (netBalance > 0) "صافي ديون نشطة" else "لا توجد ديون معلقة",
                        color = balText.copy(alpha = 0.7f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun DashboardCharts(
    currency: String,
    totalDebt: Double,
    totalReceived: Double,
    currencyDebts: Map<String, Double> = emptyMap(),
    currencyPayments: Map<String, Double> = emptyMap()
) {
    // List of active currencies to show
    val allCurrencies = remember(currencyDebts, currencyPayments) {
        val set = mutableSetOf<String>()
        set.addAll(currencyDebts.keys)
        set.addAll(currencyPayments.keys)
        if (set.isEmpty()) {
            listOf("الكل")
        } else {
            listOf("الكل") + set.toList()
        }
    }

    var selectedCurrencyIndex by remember { mutableStateOf(0) }
    val activeCurrency = allCurrencies.getOrElse(selectedCurrencyIndex) { "الكل" }

    val (currentDebt, currentReceived, displayCurr) = if (activeCurrency == "الكل") {
        Triple(totalDebt, totalReceived, currency)
    } else {
        Triple(currencyDebts[activeCurrency] ?: 0.0, currencyPayments[activeCurrency] ?: 0.0, activeCurrency)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "النسبة والتحليل المالي",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (allCurrencies.size > 1) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        allCurrencies.forEachIndexed { idx, currName ->
                            val isSelected = selectedCurrencyIndex == idx
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.clickable { selectedCurrencyIndex = idx }
                            ) {
                                Text(
                                    text = currName,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            val total = (currentDebt + currentReceived).coerceAtLeast(1.0)
            val debtRatio = (currentDebt / total).toFloat()
            val receivedRatio = (currentReceived / total).toFloat()

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Pie Chart Canvas
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokeWidth = 35f
                        val sizeMin = size.minDimension - strokeWidth
                        
                        // Draw Debt Sweep (Crimson/Coral)
                        drawArc(
                            color = Color(0xFFEF4444),
                            startAngle = -90f,
                            sweepAngle = debtRatio * 360f,
                            useCenter = false,
                            style = Stroke(width = strokeWidth),
                            size = Size(sizeMin, sizeMin),
                            topLeft = Offset(strokeWidth / 2, strokeWidth / 2)
                        )
                        // Draw Received Sweep (Emerald)
                        drawArc(
                            color = Color(0xFF10B981),
                            startAngle = -90f + (debtRatio * 360f),
                            sweepAngle = receivedRatio * 360f,
                            useCenter = false,
                            style = Stroke(width = strokeWidth),
                            size = Size(sizeMin, sizeMin),
                            topLeft = Offset(strokeWidth / 2, strokeWidth / 2)
                        )
                    }
                    Text(
                        text = if (totalReceived + currentDebt > 0) String.format("%.0f%%", receivedRatio * 100) else "0%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF10B981)
                    )
                }

                Spacer(modifier = Modifier.width(24.dp))

                // Chart Legend
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    ChartLegendItem(
                        color = Color(0xFFEF4444),
                        label = "ديون غير محصلة",
                        percentage = String.format(Locale.US, "%.1f%%", debtRatio * 100),
                        value = "${FormatUtils.formatAmount(currentDebt)} $displayCurr"
                    )
                    ChartLegendItem(
                        color = Color(0xFF10B981),
                        label = "مبالغ مستلمة ومسددة",
                        percentage = String.format(Locale.US, "%.1f%%", receivedRatio * 100),
                        value = "${FormatUtils.formatAmount(currentReceived)} $displayCurr"
                    )
                }
            }
        }
    }
}

@Composable
fun ChartLegendItem(color: Color, label: String, percentage: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(color, RoundedCornerShape(3.dp))
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = label,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = percentage,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            }
            Text(
                text = value,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun MiniStatCard(
    title: String,
    value: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.1f)
    ) {
        Column(
            modifier = Modifier.padding(8.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = value,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = color
            )
            Text(
                text = title,
                fontSize = 11.sp,
                color = color.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
fun SectionHeader(title: String, onSeeAll: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        TextButton(onClick = onSeeAll) {
            Text("عرض الكل", fontSize = 12.sp)
        }
    }
}

@Composable
fun DebtorListItem(debtor: Client, currency: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color(0xFFFEE2E2), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = debtor.name.take(1),
                        color = Color(0xFFDC2626),
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = debtor.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "الهاتف: ${debtor.phone}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = "${FormatUtils.formatAmount(debtor.balance)} $currency",
                color = Color(0xFFEF4444),
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }
    }
}

@Composable
fun ActivityListItem(activity: ActivityItem, currency: String) {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    val formattedDate = dateFormat.format(Date(activity.date))

    val color = if (activity.type == ActivityType.INVOICE) Color(0xFF4F46E5) else Color(0xFF10B981)
    val background = if (activity.type == ActivityType.INVOICE) Color(0xFFEEF2F6) else Color(0xFFECFDF5)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(background, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (activity.type == ActivityType.INVOICE) Icons.Default.Receipt else Icons.Default.Payments,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = activity.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = activity.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formattedDate,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
            Text(
                text = "${if (activity.type == ActivityType.INVOICE) "+" else "-"}${FormatUtils.formatAmount(activity.amount)} $currency",
                color = color,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }
    }
}

@Composable
fun QuickAddActionItem(
    text: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
        tonalElevation = 3.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                text = text,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(color.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun AboutAppDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.tertiary
                                )
                            ),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountBalance,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Text(
                    text = "تطبيق إدارة الحسابات والديون",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    textAlign = TextAlign.Center
                )

                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "الإصدار v1.0.0",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // App Purpose
                Text(
                    text = "وظيفة التطبيق الرئيسية:",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "نظام مالي متكامل لإدارة حسابات العملاء، تسجيل الفواتير والمدفوعات، التصدير التلقائي لكشوفات الحسابات PDF، والتقارير المالية مع حماية البيانات والنسخ الاحتياطي.",
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Features List
                Text(
                    text = "أبرز مميزات التطبيق:",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                val features = listOf(
                    "📊 متابعة ديون ومدفوعات العملاء بدقة وكشوفات تفصيلية.",
                    "🧾 إنشاء وتفاصيل الفواتير والطباعة بصيغة PDF منسقة.",
                    "🗓️ اختيار التاريخ بنظامين (جدول تقويم + خانات أرقام).",
                    "📂 النسخ الاحتياطي اليومي لكشوفات العملاء في Downloads.",
                    "🔒 أمان عالي برمز PIN وقفل حماية التطبيق."
                )

                features.forEach { feature ->
                    Text(
                        text = feature,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Design Rights Credit
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "حقوق التصميم والتطوير",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }

                        Text(
                            text = "حقوق التصميم من قبل شعيب العوني",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Text(
                            text = "جميع الحقوق محفوظة © 2026",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("إغلاق", fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
fun EditStoreNameDialog(currentName: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تعديل اسم المتجر", fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("اسم المتجر") },
                singleLine = true,
                modifier = Modifier.scrollToTopOnFocus().scrollToTopOnFocus().fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = { if(text.isNotBlank()) onSave(text) }) {
                Text("حفظ")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

@Composable
fun CurrencyFilterRow(selectedCurrency: String, onCurrencySelected: (String) -> Unit) {
    val filters = listOf(
        "الكل" to "🌎 الكل",
        "الريال اليمني" to "🇾🇪 ريال يمني",
        "الريال السعودي" to "🇸🇦 ريال سعودي",
        "الدولار الأمريكي" to "🇺🇸 دولار أمريكي"
    )
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        items(filters) { filter ->
            val isSelected = selectedCurrency == filter.first
            FilterChip(
                selected = isSelected,
                onClick = { onCurrencySelected(filter.first) },
                label = { Text(filter.second, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                shape = RoundedCornerShape(10.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalPaymentDialog(
    clients: List<com.example.data.model.Client>,
    defaultCurrency: String,
    isAutoNumberingEnabled: Boolean = false,
    defaultVoucherNumber: String = "",
    onDismiss: () -> Unit,
    onSave: (clientId: Int, amount: Double, method: String, notes: String, currency: String, voucherNumber: String?) -> Unit
) {
    val context = LocalContext.current
    var selectedClient by remember { mutableStateOf<com.example.data.model.Client?>(null) }
    var expandedClient by remember { mutableStateOf(false) }
    
    var amountStr by remember { mutableStateOf("") }
    var selectedCurrency by remember { mutableStateOf("الريال اليمني") }
    var expandedCurrency by remember { mutableStateOf(false) }
    
    var exchangeRateStr by remember { mutableStateOf("1.0") }
    var method by remember { mutableStateOf("نقدي") }
    var expandedMethod by remember { mutableStateOf(false) }
    
    var voucherNumber by remember(isAutoNumberingEnabled) {
        mutableStateOf(if (isAutoNumberingEnabled) defaultVoucherNumber else "")
    }
    var collectorName by remember { mutableStateOf("محمد المقطري") }
    var transferNumber by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    val currencies = listOf("الريال اليمني", "الريال السعودي", "الدولار الأمريكي")
    val methods = listOf("نقدي", "إيداع", "تحويل", "شيك")

    val clientPreviousCurrency = selectedClient?.currency?.ifBlank { defaultCurrency.ifBlank { "الريال اليمني" } } ?: "الريال اليمني"
    val showCurrencyNotice = selectedClient != null && clientPreviousCurrency != selectedCurrency

    val convertedAmount = remember(amountStr, exchangeRateStr) {
        val amt = amountStr.toDoubleOrNull() ?: 0.0
        val rate = exchangeRateStr.toDoubleOrNull() ?: 1.0
        amt * rate
    }

    AlertDialog(
        onDismissRequest = onDismiss,
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
                    .padding(top = 4.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Client Dropdown
                ExposedDropdownMenuBox(
                    expanded = expandedClient,
                    onExpandedChange = { expandedClient = !expandedClient }
                ) {
                    OutlinedTextField(
                        value = selectedClient?.name ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("العميل *") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedClient) },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        modifier = Modifier.scrollToTopOnFocus().scrollToTopOnFocus().menuAnchor().fillMaxWidth(),
                        isError = isError && selectedClient == null
                    )
                    ExposedDropdownMenu(
                        expanded = expandedClient,
                        onDismissRequest = { expandedClient = false }
                    ) {
                        clients.forEach { client ->
                            DropdownMenuItem(
                                text = { Text(client.name) },
                                onClick = {
                                    selectedClient = client
                                    expandedClient = false
                                }
                            )
                        }
                    }
                }

                // Currency Alert Notice
                if (showCurrencyNotice) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFB45309), modifier = Modifier.size(18.dp))
                            Text(
                                text = "تنبيه: العميل يتعامل مسبقاً بـ ($clientPreviousCurrency). تم تحديد $selectedCurrency لهذه الدفعة.",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF92400E)
                            )
                        }
                    }
                }

                // Amount and Currency Row
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Amount
                    OutlinedTextField(
                        value = amountStr,
                        onValueChange = { amountStr = it; isError = false },
                        label = { Text("المبلغ المدفوع *") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        modifier = Modifier.scrollToTopOnFocus().scrollToTopOnFocus().weight(1.2f),
                        isError = isError && amountStr.toDoubleOrNull() == null,
                        shape = RoundedCornerShape(10.dp)
                    )

                    // Currency Dropdown
                    ExposedDropdownMenuBox(
                        expanded = expandedCurrency,
                        onExpandedChange = { expandedCurrency = !expandedCurrency },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = selectedCurrency,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("عملة الدفع") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCurrency) },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                            modifier = Modifier.scrollToTopOnFocus().scrollToTopOnFocus().menuAnchor().fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = expandedCurrency,
                            onDismissRequest = { expandedCurrency = false }
                        ) {
                            currencies.forEach { curr ->
                                DropdownMenuItem(
                                    text = { Text(curr) },
                                    onClick = {
                                        selectedCurrency = curr
                                        expandedCurrency = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Currency Conversion Rate Input if currency differs or SAR/USD selected
                if (selectedCurrency != "الريال اليمني" || showCurrencyNotice) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "💱 تحويل سعر الصرف ($selectedCurrency ⬅️ $clientPreviousCurrency):",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = exchangeRateStr,
                                    onValueChange = { exchangeRateStr = it },
                                    label = { Text("سعر الصرف") },
                                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                Column(modifier = Modifier.weight(1.2f)) {
                                    Text("المبلغ الحسابي المعادل:", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        text = "${FormatUtils.formatAmount(convertedAmount)} $clientPreviousCurrency",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }

                // Payment Method & Voucher Row
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Method Dropdown
                    ExposedDropdownMenuBox(
                        expanded = expandedMethod,
                        onExpandedChange = { expandedMethod = !expandedMethod },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = method,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("طريقة الدفع") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedMethod) },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                            modifier = Modifier.scrollToTopOnFocus().scrollToTopOnFocus().menuAnchor().fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = expandedMethod,
                            onDismissRequest = { expandedMethod = false }
                        ) {
                            methods.forEach { met ->
                                DropdownMenuItem(
                                    text = { Text(met) },
                                    onClick = {
                                        method = met
                                        expandedMethod = false
                                    }
                                )
                            }
                        }
                    }

                    // Voucher / Receipt Number
                    OutlinedTextField(
                        value = voucherNumber,
                        onValueChange = { voucherNumber = it },
                        label = { Text("رقم السند") },
                        placeholder = { Text("مثال: 1045", fontSize = 11.sp) },
                        modifier = Modifier.scrollToTopOnFocus().scrollToTopOnFocus().weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )
                }

                // Collector Name (المحصل)
                OutlinedTextField(
                    value = collectorName,
                    onValueChange = { collectorName = it },
                    label = { Text("اسم المحصل المعتمد") },
                    leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    placeholder = { Text("اسم المحصل الذي قام بالتحصيل", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.scrollToTopOnFocus().scrollToTopOnFocus().fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                // Transfer Number (رقم الحوالة)
                OutlinedTextField(
                    value = transferNumber,
                    onValueChange = { transferNumber = it },
                    label = { Text("رقم الحوالة / الإيداع البنكي (اختياري)") },
                    leadingIcon = { Icon(Icons.Default.ConfirmationNumber, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    placeholder = { Text("رقم الحوالة أو الإيداع", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.scrollToTopOnFocus().scrollToTopOnFocus().fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                // Notes
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("ملاحظات (اختياري)") },
                    modifier = Modifier.scrollToTopOnFocus().scrollToTopOnFocus().fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amountStr.toDoubleOrNull()
                    if (amt != null && amt > 0 && selectedClient != null) {
                        val finalNote = buildString {
                            if (collectorName.isNotBlank()) append("المحصل: $collectorName")
                            if (transferNumber.isNotBlank()) {
                                if (isNotEmpty()) append(" | ")
                                append("حوالة #: $transferNumber")
                            }
                            if (notes.isNotBlank()) {
                                if (isNotEmpty()) append(" | ")
                                append(notes)
                            }
                        }
                        onSave(selectedClient!!.id, amt, method, finalNote, selectedCurrency, voucherNumber.trim().ifBlank { null })
                    } else {
                        isError = true
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
            ) {
                Text("حفظ وتسجيل الدفعة", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("إلغاء", color = MaterialTheme.colorScheme.error)
            }
        }
    )
}

@Composable
fun DashboardTopMovingItemsSection(
    items: List<ItemMovementSummary>,
    currency: String,
    periodLabel: String,
    onSeeAll: () -> Unit,
    onItemClick: (ItemMovementSummary) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSeeAll() }
            .testTag("dashboard_top_moving_items_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "📦 الأصناف الأكثر حركة",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (periodLabel.isNotBlank()) {
                        Text(
                            text = " ($periodLabel)",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            if (items.isEmpty()) {
                Text(
                    text = "لا توجد حركة مبيعات مسجلة في هذه الفترة",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items.take(5).forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSeeAll() }
                                .padding(vertical = 3.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = item.itemName,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "—  ${item.netQuantity} ${item.unit}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFEA580C)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(Modifier.height(6.dp))

            // Bottom link: عرض الكل ←
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSeeAll() }
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "عرض الكل",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "عرض الكل",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}
