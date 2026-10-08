package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.report.CurrencyReport
import com.example.domain.report.DateFilterType
import com.example.domain.report.ReportData
import com.example.domain.report.DateRange
import com.example.ui.viewmodel.AppViewModel
import com.example.util.FormatUtils
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    reportViewModel: com.example.ui.viewmodel.ReportViewModel,
    onNavigateToTopMovingItems: () -> Unit = {}
) {
    val reportData by reportViewModel.reportData.collectAsState()
    val selectedCurrency by reportViewModel.selectedReportCurrency.collectAsState()
    val selectedDateFilter by reportViewModel.selectedDateFilter.collectAsState()
    val customDateRange by reportViewModel.customDateRange.collectAsState()
    val settings by reportViewModel.storeSettings.collectAsState()
    val clientsList by reportViewModel.clients.collectAsState()
    val invoicesList by reportViewModel.invoices.collectAsState()
    val paymentsList by reportViewModel.payments.collectAsState()
    val returnsList by reportViewModel.returns.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    // UI States
    var selectedTab by remember { mutableIntStateOf(0) }
    var showCurrencyDropdown by remember { mutableStateOf(false) }
    var showDateDropdown by remember { mutableStateOf(false) }
    var showCustomDateDialog by remember { mutableStateOf(false) }
    var showMultiClientDialog by remember { mutableStateOf(false) }
    var isGeneratingPdf by remember { mutableStateOf(false) }

    val tabs = listOf("🛒 المبيعات والتحصيل", "📦 المشتريات", "👥 العملاء والمخزون", "📈 تحليل مالي")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("📊 التقارير", fontWeight = FontWeight.Bold, fontSize = 14.sp) },
                modifier = Modifier.height(40.dp),
                windowInsets = WindowInsets(0, 0, 0, 0),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    IconButton(onClick = { showMultiClientDialog = true }) {
                        Icon(Icons.Default.Group, contentDescription = "تقرير عملاء محددين", modifier = Modifier.size(20.dp))
                    }
                    if (isGeneratingPdf) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        IconButton(onClick = {
                            if (reportData != null) {
                                isGeneratingPdf = true
                                coroutineScope.launch {
                                    try {
                                        val file = com.example.util.ReportPdfManager.exportFinancialReportToPdf(
                                            context, reportData!!, settings ?: com.example.data.model.StoreSettings()
                                        )
                                        if (file != null) {
                                            com.example.util.ShareManager.sharePdf(context, file)
                                        } else {
                                            Toast.makeText(context, "تعذر إنشاء التقرير حاليًا. يرجى المحاولة مرة أخرى.", Toast.LENGTH_SHORT).show()
                                        }
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "حدث خطأ أثناء إنشاء التقرير.", Toast.LENGTH_SHORT).show()
                                    } finally {
                                        isGeneratingPdf = false
                                    }
                                }
                            }
                        }) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = "تصدير PDF", modifier = Modifier.size(20.dp))
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Compact Filter Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Date Filter Dropdown
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { showDateDropdown = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = selectedDateFilter.label,
                            fontSize = 11.sp,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    DropdownMenu(
                        expanded = showDateDropdown,
                        onDismissRequest = { showDateDropdown = false }
                    ) {
                        DateFilterType.values().forEach { filter ->
                            DropdownMenuItem(
                                text = { Text(filter.label, fontSize = 12.sp) },
                                onClick = {
                                    showDateDropdown = false
                                    if (filter == DateFilterType.CUSTOM) {
                                        showCustomDateDialog = true
                                    } else {
                                        reportViewModel.setReportDateFilter(filter)
                                    }
                                }
                            )
                        }
                    }
                }

                // Currency Filter Dropdown
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { showCurrencyDropdown = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.AttachMoney, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = selectedCurrency,
                            fontSize = 11.sp,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    DropdownMenu(
                        expanded = showCurrencyDropdown,
                        onDismissRequest = { showCurrencyDropdown = false }
                    ) {
                        val availableCurrencies = listOf("كل العملات", "الريال اليمني", "الريال السعودي", "الدولار الأمريكي")
                        availableCurrencies.forEach { cur ->
                            DropdownMenuItem(
                                text = { Text(cur, fontSize = 12.sp) },
                                onClick = {
                                    showCurrencyDropdown = false
                                    reportViewModel.setReportCurrency(cur)
                                }
                            )
                        }
                    }
                }
            }

            if (selectedDateFilter == DateFilterType.CUSTOM) {
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                Text(
                    text = "من: ${sdf.format(Date(customDateRange.start))} إلى: ${sdf.format(Date(customDateRange.end))}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = 2.dp)
                )
            }

            // Tabs Header
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                edgePadding = 6.dp,
                modifier = Modifier.height(40.dp)
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                title,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 12.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    )
                }
            }

            // Main Reports Viewport Space
            if (reportData == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (reportData!!.currencyReports.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(56.dp), tint = Color.Gray)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("📊 لا توجد بيانات", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("لا توجد معاملات ضمن الفترة والعملة المحددة.", color = Color.Gray, fontSize = 13.sp)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(reportData!!.currencyReports) { currencyReport ->
                        CurrencyReportSection(
                            currencyReport = currencyReport,
                            selectedTab = selectedTab,
                            onOpenMultiClientDialog = { showMultiClientDialog = true }
                        )
                    }
                }
            }
        }
    }

    if (showMultiClientDialog) {
        MultiClientReportDialog(
            clients = clientsList,
            invoices = invoicesList,
            payments = paymentsList,
            returns = returnsList,
            settings = settings ?: com.example.data.model.StoreSettings(),
            onDismiss = { showMultiClientDialog = false },
            onExportPdf = { selectedClients ->
                showMultiClientDialog = false
                isGeneratingPdf = true
                coroutineScope.launch {
                    try {
                        val pdfFile = com.example.util.ReportPdfManager.exportMultiClientReportToPdf(
                            context = context,
                            selectedClients = selectedClients,
                            invoices = invoicesList,
                            payments = paymentsList,
                            returns = returnsList,
                            settings = settings ?: com.example.data.model.StoreSettings()
                        )
                        if (pdfFile != null) {
                            com.example.util.ShareManager.sharePdf(context, pdfFile)
                        } else {
                            Toast.makeText(context, "تعذر إنتاج التقرير. يرجى المحاولة لاحقًا.", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "حدث خطأ أثناء إعداد التقرير.", Toast.LENGTH_SHORT).show()
                    } finally {
                        isGeneratingPdf = false
                    }
                }
            }
        )
    }
}

@Composable
fun CurrencyReportSection(
    currencyReport: CurrencyReport,
    selectedTab: Int,
    onOpenMultiClientDialog: () -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Currency Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(8.dp)
            ) {
                Icon(Icons.Default.AttachMoney, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "العملة: ${currencyReport.currency}",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontSize = 16.sp
                )
            }

            when (selectedTab) {
                0 -> { // Sales & Collection
                    SalesSummarySection(currencyReport)
                    CollectionSection(currencyReport)
                    ReturnsSection(currencyReport)
                }
                1 -> { // Purchases
                    PurchasesSection(currencyReport)
                }
                2 -> { // Customers & Inventory
                    CustomerSection(currencyReport, onOpenMultiClientDialog = onOpenMultiClientDialog)
                    DebtAgingSection(currencyReport)
                    InventorySection(currencyReport)
                }
                3 -> { // Financial Analysis
                    FinancialAnalysisSection(currencyReport)
                }
            }
        }
    }
}

@Composable
fun SalesSummarySection(report: CurrencyReport) {
    ReportBlock(title = "🛒 ملخص المبيعات", icon = Icons.Default.ShoppingCart) {
        ReportRow("إجمالي المبيعات", report.salesReport.totalSales, report.currency, isBold = true)
        ReportRow("مرتجعات المبيعات", report.salesReport.totalReturns, report.currency, color = Color.Red)
        ReportRow("صافي المبيعات", report.salesReport.netSales, report.currency, isBold = true, color = Color(0xFF059669))
        Divider(modifier = Modifier.padding(vertical = 8.dp))
        ReportRowText("عدد فواتير البيع", "${report.salesReport.invoiceCount} فاتورة")
        ReportRow("متوسط قيمة الفاتورة", report.salesReport.averageInvoiceValue, report.currency)
        Divider(modifier = Modifier.padding(vertical = 8.dp))
        ReportRowText("الفواتير المدفوعة", "${report.salesReport.fullyPaidCount}")
        ReportRowText("الفواتير الجزئية", "${report.salesReport.partiallyPaidCount}")
        ReportRowText("الفواتير غير المسددة", "${report.salesReport.unpaidCount}", color = Color.Red)
        Divider(modifier = Modifier.padding(vertical = 8.dp))
        ReportRowText("أعلى عميل شراءً", report.salesReport.topCustomerName)
        ReportRow("قيمة مشترياته", report.salesReport.topCustomerAmount, report.currency)
        ReportRowText("أكثر صنف مبيعاً", report.salesReport.topItemName)
    }
}

@Composable
fun CollectionSection(report: CurrencyReport) {
    ReportBlock(title = "📥 تقرير التحصيل", icon = Icons.Default.Payments) {
        ReportRow("إجمالي المستحقات (فواتير)", report.collectionReport.totalDue, report.currency)
        ReportRow("إجمالي المقبوضات", report.collectionReport.totalCollected, report.currency, color = Color(0xFF059669), isBold = true)
        ReportRow("إجمالي المتبقي", report.collectionReport.totalRemaining, report.currency, color = Color.Red, isBold = true)
        
        val ratioColor = if (report.collectionReport.collectionRatio >= 80) Color(0xFF059669) 
                         else if (report.collectionReport.collectionRatio >= 50) Color(0xFFD97706) 
                         else Color.Red
        ReportRowText("نسبة التحصيل للفترة", "${FormatUtils.formatAmount(report.collectionReport.collectionRatio)}%", color = ratioColor, isBold = true)
        
        Divider(modifier = Modifier.padding(vertical = 8.dp))
        ReportRow("الأقساط المستحقة القادمة", report.collectionReport.dueInstallments, report.currency)
        ReportRow("الأقساط المتأخرة السداد", report.collectionReport.overdueInstallments, report.currency, color = Color.Red)
    }
}

@Composable
fun PurchasesSection(report: CurrencyReport) {
    ReportBlock(title = "تقرير المشتريات والموردين", icon = Icons.Default.Storefront) {
        ReportRow("إجمالي المشتريات", report.purchaseReport.totalPurchases, report.currency, isBold = true)
        ReportRow("مرتجعات المشتريات", report.returnsReport.totalPurchaseReturns, report.currency, color = Color.Red)
        ReportRow("صافي المشتريات", report.purchaseReport.netPurchases, report.currency, isBold = true, color = Color(0xFF059669))
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        ReportRowText("عدد عمليات الشراء", "${report.purchaseReport.invoiceCount} عملية")
        ReportRowText("عدد الموردين النشطين", "${report.supplierReport.supplierCount} مورد")
    }
}

@Composable
fun ReturnsSection(report: CurrencyReport) {
    ReportBlock(title = "تقرير المرتجعات", icon = Icons.Default.AssignmentReturn) {
        ReportRow("مرتجعات المبيعات", report.returnsReport.totalSalesReturns, report.currency)
        ReportRow("مرتجعات المشتريات", report.returnsReport.totalPurchaseReturns, report.currency, color = Color.Red)
    }
}

@Composable
fun CustomerSection(report: CurrencyReport, onOpenMultiClientDialog: () -> Unit = {}) {
    ReportBlock(title = "تقرير حركة العملاء", icon = Icons.Default.People) {
        ReportRowText("إجمالي العملاء المتفاعلين", "${report.customerReport.activeCustomersCount}")
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        Text("حالة ديون العملاء الحالية:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        ReportRowText("العملاء المدينون (عليهم)", "${report.customerReport.debtorsCount}", color = Color.Red)
        ReportRowText("العملاء الدائنون (لهم)", "${report.customerReport.creditorsCount}", color = Color(0xFF059669))
        ReportRowText("العملاء المتعادلون", "${report.customerReport.neutralCount}")
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onOpenMultiClientDialog,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(Icons.Default.Group, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("توليد تقرير تفصيلي لعملاء محددين (PDF)", fontSize = 13.sp)
        }
    }
}

@Composable
fun InventorySection(report: CurrencyReport) {
    ReportBlock(title = "تقرير المخزون", icon = Icons.Default.Inventory) {
        ReportRowText("إجمالي الأصناف المسجلة", "${report.inventoryReport.totalItemsCount}")
        ReportRowText("الأصناف المتوفرة", "${report.inventoryReport.availableItemsCount}", color = Color(0xFF059669))
        ReportRowText("الأصناف منخفضة المخزون", "${report.inventoryReport.lowStockItemsCount}", color = Color(0xFFD97706))
        ReportRowText("الأصناف النافدة", "${report.inventoryReport.outOfStockItemsCount}", color = Color.Red)
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        ReportRowText("إجمالي عدد القطع", "${report.inventoryReport.totalQuantity} قطعة")
        ReportRowText("القطع المباعة خلال الفترة", "${report.inventoryReport.soldQuantityPeriod} قطعة")
    }
}

@Composable
fun DebtAgingSection(report: CurrencyReport) {
    ReportBlock(title = "تحليل أعمار الديون (مستحقات غير مسددة)", icon = Icons.Default.AccountBalanceWallet) {
        ReportRowText("العملاء ذوي ديون غير مسددة", "${report.debtAgingReport.totalCustomers}")
        ReportRowText("فواتير غير مسددة بالكامل", "${report.debtAgingReport.totalInvoices}")
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        ReportRow("مستحق اليوم", report.debtAgingReport.currentDue, report.currency, color = Color(0xFFD97706))
        ReportRow("متأخر 1-7 أيام", report.debtAgingReport.overdue1_7, report.currency, color = Color.Red)
        ReportRow("متأخر 8-30 يومًا", report.debtAgingReport.overdue8_30, report.currency, color = Color.Red)
        ReportRow("متأخر 31-60 يومًا", report.debtAgingReport.overdue31_60, report.currency, color = Color.Red)
        ReportRow("متأخر أكثر من 60 يومًا", report.debtAgingReport.overdue60Plus, report.currency, color = Color.Red, isBold = true)
    }
}

@Composable
fun FinancialAnalysisSection(report: CurrencyReport) {
    ReportBlock(title = "التحليل المالي", icon = Icons.Default.TrendingUp) {
        ReportRow("صافي المبيعات", report.financialAnalysis.netSales, report.currency, color = Color(0xFF059669), isBold = true)
        ReportRow("إجمالي المقبوضات (تدفق نقدي)", report.financialAnalysis.cashFlow, report.currency, color = Color(0xFF059669))
        ReportRow("الذمم المدينة (أموال بالخارج)", report.financialAnalysis.accountsReceivable, report.currency, color = Color.Red, isBold = true)
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        Text(
            "مؤشر مالي متاح:",
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
        Text(
            "الأرقام تعكس حركة الأموال المسجلة والفواتير والمقبوضات خلال الفترة المحددة بدقة.",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
fun ReportBlock(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            content()
        }
    }
}

@Composable
fun ReportRow(label: String, amount: Double, currency: String, isBold: Boolean = false, color: Color = Color.Unspecified) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            fontSize = 13.sp,
            color = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else color,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal
        )
        Text(
            "${FormatUtils.formatAmount(amount)} $currency",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurface else color
        )
    }
}

@Composable
fun ReportRowText(label: String, value: String, isBold: Boolean = false, color: Color = Color.Unspecified) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            fontSize = 13.sp,
            color = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else color,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal
        )
        Text(
            value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurface else color
        )
    }
}

@Composable
fun MultiClientReportDialog(
    clients: List<com.example.data.model.Client>,
    invoices: List<com.example.data.model.Invoice>,
    payments: List<com.example.data.model.Payment>,
    returns: List<com.example.data.model.ProductReturn>,
    settings: com.example.data.model.StoreSettings,
    onDismiss: () -> Unit,
    onExportPdf: (selectedClients: List<com.example.data.model.Client>) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedClientIds by remember { mutableStateOf(setOf<Int>()) }

    val filteredClients = remember(clients, searchQuery) {
        if (searchQuery.isBlank()) clients
        else clients.filter { it.name.contains(searchQuery, ignoreCase = true) || it.phone.contains(searchQuery) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Group, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("تحديد العملاء للتقرير التفصيلي", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("بحث باسم العميل أو رقم الهاتف...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "تم تحديد: ${selectedClientIds.size} من أصل ${clients.size}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row {
                        TextButton(
                            onClick = { selectedClientIds = clients.map { it.id }.toSet() },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("تحديد الكل", fontSize = 11.sp)
                        }
                        TextButton(
                            onClick = { selectedClientIds = emptySet() },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("إلغاء الكل", fontSize = 11.sp, color = Color.Red)
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

                if (filteredClients.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                        Text("لا يوجد عملاء مطابقون للبحث", color = Color.Gray, fontSize = 13.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp)
                    ) {
                        items(filteredClients, key = { it.id }) { client ->
                            val isSelected = selectedClientIds.contains(client.id)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedClientIds = if (isSelected) {
                                            selectedClientIds - client.id
                                        } else {
                                            selectedClientIds + client.id
                                        }
                                    }
                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { checked ->
                                        selectedClientIds = if (checked == true) {
                                            selectedClientIds + client.id
                                        } else {
                                            selectedClientIds - client.id
                                        }
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(client.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    if (client.phone.isNotBlank()) {
                                        Text(client.phone, fontSize = 11.sp, color = Color.Gray)
                                    }
                                }
                                Text(
                                    "${FormatUtils.formatAmount(client.balance)} ${settings.currency}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (client.balance > 0) Color.Red else Color(0xFF059669)
                                )
                            }
                            HorizontalDivider(color = Color.LightGray.copy(alpha = 0.4f))
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val chosen = clients.filter { selectedClientIds.contains(it.id) }
                    if (chosen.isNotEmpty()) {
                        onExportPdf(chosen)
                    }
                },
                enabled = selectedClientIds.isNotEmpty()
            ) {
                Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("تصدير وطباعة PDF (${selectedClientIds.size})")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}
