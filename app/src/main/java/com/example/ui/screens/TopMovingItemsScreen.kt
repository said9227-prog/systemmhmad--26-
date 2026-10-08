package com.example.ui.screens

import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.*
import com.example.ui.viewmodel.AppViewModel
import com.example.util.DateTimeUtils
import com.example.util.FormatUtils
import com.example.util.ItemMovementAnalyzer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopMovingItemsScreen(
    viewModel: AppViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToInvoiceDetails: (Int) -> Unit = {},
    onNavigateToClientProfile: (Int) -> Unit = {},
    initialSelectedItemId: Int? = null,
    initialSelectedItemName: String? = null
) {
    val context = LocalContext.current
    val settings by viewModel.storeSettings.collectAsState()

    var selectedPeriod by remember { mutableStateOf(ItemMovementPeriod.THIS_MONTH) }
    var selectedCurrency by remember { mutableStateOf("الكل") }
    var sortBy by remember { mutableStateOf(ItemMovementSort.QUANTITY) }
    var searchQuery by remember { mutableStateOf("") }

    // Dynamic currencies available in the system
    val availableCurrencies = remember(settings) {
        val list = mutableListOf("الكل")
        list.add(settings.currency)
        viewModel.invoices.value.map { it.currency }.filter { it.isNotBlank() }.distinct().forEach {
            if (!list.contains(it)) list.add(it)
        }
        list
    }

    // Run analysis calculation
    val topMovingItemsState by viewModel.topMovingItemsState.collectAsState()
    val (movementList, globalStats) = topMovingItemsState

    // Sync selected filters to AppViewModel in the background
    LaunchedEffect(selectedPeriod, selectedCurrency, searchQuery, sortBy) {
        viewModel.topMovingPeriod.value = selectedPeriod
        viewModel.topMovingCurrency.value = selectedCurrency
        viewModel.topMovingSearchQuery.value = searchQuery
        viewModel.topMovingSortBy.value = sortBy
    }

    // Selected item for deep customer breakdown dialog
    var itemForCustomerDetail by remember {
        mutableStateOf<ItemMovementSummary?>(null)
    }

    // Auto-open requested item if specified via navigation
    LaunchedEffect(movementList, initialSelectedItemId, initialSelectedItemName) {
        if (itemForCustomerDetail == null) {
            if (initialSelectedItemId != null && initialSelectedItemId > 0) {
                movementList.find { it.itemId == initialSelectedItemId }?.let {
                    itemForCustomerDetail = it
                }
            } else if (!initialSelectedItemName.isNullOrBlank()) {
                movementList.find { it.itemName.equals(initialSelectedItemName, ignoreCase = true) }?.let {
                    itemForCustomerDetail = it
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "🔥 الأصناف الأكثر حركة",
                                fontWeight = FontWeight.Bold,
                                fontSize = 19.sp
                            )
                        }
                        Text(
                            text = "تحليل حركة المبيعات وتوزيع السحب حسب العملاء",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("top_moving_items_back_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val shareText = ItemMovementAnalyzer.buildShareReport(
                                items = movementList,
                                stats = globalStats,
                                period = selectedPeriod,
                                currency = if (selectedCurrency == "الكل") settings.currency else selectedCurrency,
                                storeName = settings.storeName
                            )
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, shareText)
                                type = "text/plain"
                            }
                            val shareIntent = Intent.createChooser(sendIntent, "مشاركة تقرير حركة الأصناف")
                            context.startActivity(shareIntent)
                        },
                        modifier = Modifier.testTag("share_movement_report_btn")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "مشاركة التقرير")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Period Selection Chips
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "فترة التحليل:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(ItemMovementPeriod.values()) { period ->
                            val isSelected = selectedPeriod == period
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedPeriod = period },
                                label = { Text(period.labelAr, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                leadingIcon = if (isSelected) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                } else null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }
                }
            }

            // Currency and Sort Filter Row
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Currency selector
                    var currencyExpanded by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedButton(
                            onClick = { currencyExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.AttachMoney, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = if (selectedCurrency == "الكل") "كل العملات" else selectedCurrency,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                        DropdownMenu(
                            expanded = currencyExpanded,
                            onDismissRequest = { currencyExpanded = false }
                        ) {
                            availableCurrencies.forEach { curr ->
                                DropdownMenuItem(
                                    text = { Text(if (curr == "الكل") "جميع العملات" else curr, fontSize = 13.sp) },
                                    onClick = {
                                        selectedCurrency = curr
                                        currencyExpanded = false
                                    },
                                    leadingIcon = if (selectedCurrency == curr) {
                                        { Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                                    } else null
                                )
                            }
                        }
                    }

                    // Sort By selector
                    var sortExpanded by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedButton(
                            onClick = { sortExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Sort, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = sortBy.labelAr,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                        DropdownMenu(
                            expanded = sortExpanded,
                            onDismissRequest = { sortExpanded = false }
                        ) {
                            ItemMovementSort.values().forEach { sort ->
                                DropdownMenuItem(
                                    text = { Text(sort.labelAr, fontSize = 13.sp) },
                                    onClick = {
                                        sortBy = sort
                                        sortExpanded = false
                                    },
                                    leadingIcon = if (sortBy == sort) {
                                        { Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                                    } else null
                                )
                            }
                        }
                    }
                }
            }

            // Search Bar
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .testTag("search_top_items_input"),
                    placeholder = { Text("بحث عن صنف أو اسم عميل...", fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "مسح")
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
            }

            // KPI Overview Banner
            item {
                MovementOverviewBanner(
                    stats = globalStats,
                    periodLabel = selectedPeriod.labelAr,
                    currency = if (selectedCurrency == "الكل") settings.currency else selectedCurrency
                )
            }

            // Section Title & Items Count
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ترتيب الأصناف المتحركة (${movementList.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "اضغط على الصنف لعرض تفاصيل العملاء",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Empty state
            if (movementList.isEmpty()) {
                item {
                    EmptyMovementState(
                        hasFilter = searchQuery.isNotBlank() || selectedCurrency != "الكل" || selectedPeriod != ItemMovementPeriod.ALL_TIME
                    )
                }
            } else {
                // List of top moving items
                itemsIndexed(movementList) { index, itemSummary ->
                    TopMovingItemCard(
                        rank = index + 1,
                        summary = itemSummary,
                        currency = if (selectedCurrency == "الكل") settings.currency else selectedCurrency,
                        onClick = { itemForCustomerDetail = itemSummary },
                        onQuickClientClick = { clientId ->
                            if (clientId != null && clientId > 0) {
                                onNavigateToClientProfile(clientId)
                            }
                        }
                    )
                }
            }
        }
    }

    // Detail Dialog: "من العملاء الذين يسحبون هذا الصنف؟"
    itemForCustomerDetail?.let { selectedItem ->
        ItemCustomersBreakdownDialog(
            itemSummary = selectedItem,
            currency = if (selectedCurrency == "الكل") settings.currency else selectedCurrency,
            periodLabel = selectedPeriod.labelAr,
            onDismiss = { itemForCustomerDetail = null },
            onNavigateToInvoice = { invId ->
                itemForCustomerDetail = null
                onNavigateToInvoiceDetails(invId)
            },
            onNavigateToClient = { cId ->
                itemForCustomerDetail = null
                onNavigateToClientProfile(cId)
            }
        )
    }
}

@Composable
fun MovementOverviewBanner(
    stats: ItemMovementGlobalStats,
    periodLabel: String,
    currency: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFEA580C).copy(alpha = 0.08f),
                            Color(0xFFEA580C).copy(alpha = 0.02f)
                        )
                    )
                )
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(0xFFEA580C), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocalFireDepartment,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "ملخص حركة المبيعات",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "خلال: $periodLabel",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (stats.mostMovedItemName != null) {
                    Surface(
                        color = Color(0xFFFEF3C7),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "👑 المتصدر: ", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB45309))
                            Text(
                                text = stats.mostMovedItemName,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF92400E),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Total Items
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("أصناف بيعت", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = "${stats.totalItemsMoved}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Total Units Net
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("صافي الوحدات", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = "${stats.totalUnitsSoldNet}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFEA580C)
                    )
                    if (stats.totalUnitsReturned > 0) {
                        Text(
                            text = "مرتجع: -${stats.totalUnitsReturned}",
                            fontSize = 9.sp,
                            color = Color(0xFFDC2626)
                        )
                    }
                }

                // Total Net Revenue
                Column(modifier = Modifier.weight(1.2f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("صافي القيمة", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = FormatUtils.formatAmount(stats.totalNetRevenue),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF059669)
                    )
                    Text(currency, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                // Total Clients
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("عملاء ساحبون", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = "${stats.totalUniqueClients}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2563EB)
                    )
                }
            }
        }
    }
}

@Composable
fun TopMovingItemCard(
    rank: Int,
    summary: ItemMovementSummary,
    currency: String,
    onClick: () -> Unit,
    onQuickClientClick: (Int?) -> Unit
) {
    val rankBadgeColor = when (rank) {
        1 -> Color(0xFFF59E0B) // Gold
        2 -> Color(0xFF94A3B8) // Silver
        3 -> Color(0xFFD97706) // Bronze
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    val rankTextColor = when (rank) {
        1, 2, 3 -> Color.White
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable(onClick = onClick)
            .testTag("item_movement_card_${summary.itemId ?: rank}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (rank <= 3) rankBadgeColor.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (rank <= 3) 2.dp else 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header: Rank + Item Name + Category + Total Net Qty
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Rank badge
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(rankBadgeColor, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (rank <= 3) when (rank) {
                                1 -> "🥇"
                                2 -> "🥈"
                                else -> "🥉"
                            } else "#$rank",
                            fontSize = if (rank <= 3) 14.sp else 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = rankTextColor
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = summary.itemName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (summary.category.isNotBlank()) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = summary.category,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(
                                text = "المخزون الحالي: ${summary.currentStock} ${summary.unit}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Total Quantity & Amount Highlight
                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${summary.netQuantity}",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp,
                            color = Color(0xFFEA580C)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = summary.unit,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "${FormatUtils.formatAmount(summary.netAmount)} ${FormatUtils.formatCurrencyCompact(currency)}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF059669)
                    )
                    if (summary.returnedQuantity > 0) {
                        Text(
                            text = "(مباع: ${summary.grossSoldQuantity} | مرتجع: -${summary.returnedQuantity})",
                            fontSize = 9.sp,
                            color = Color(0xFFDC2626)
                        )
                    }
                }
            }

            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            // Top Customer Highlight (العميل الأكثر سحباً)
            if (summary.topCustomer != null && summary.topCustomer.netQuantity > 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFFEF3C7).copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("👑", fontSize = 14.sp)
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "الأكثر سحباً: ",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF92400E)
                        )
                        Text(
                            text = summary.topCustomer.clientName,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF78350F),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${summary.topCustomer.netQuantity} ${summary.unit}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFB45309)
                        )
                        Spacer(Modifier.width(4.dp))
                        Surface(
                            color = Color(0xFFF59E0B),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "%.0f%%".format(summary.topCustomer.percentageOfTotal),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }

            // Customer Distribution Preview Chips
            if (summary.customers.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "سحب العملاء (${summary.customers.size}):",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { onClick() }
                    ) {
                        Text(
                            text = "عرض التفاصيل الكاملة",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Icon(
                            imageVector = Icons.Default.ChevronLeft,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Quick preview of top 3 customers
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    summary.customers.take(3).forEach { cust ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onQuickClientClick(cust.clientId) }
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = cust.clientName,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${cust.netQuantity} ${summary.unit}",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    if (summary.customers.size > 3) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.clickable { onClick() }
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "+${summary.customers.size - 3}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
 * Deep breakdown dialog showing who bought this item, quantities, percentages, and invoices.
 */
@Composable
fun ItemCustomersBreakdownDialog(
    itemSummary: ItemMovementSummary,
    currency: String,
    periodLabel: String,
    onDismiss: () -> Unit,
    onNavigateToInvoice: (Int) -> Unit,
    onNavigateToClient: (Int) -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.88f),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "من العملاء الذين يسحبون هذا الصنف؟",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = itemSummary.itemName,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "خلال: $periodLabel • المخزون الحالي: ${itemSummary.currentStock} ${itemSummary.unit}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق")
                    }
                }

                // Item Total Metric Card
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("إجمالي صافي المبيعات", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = "${itemSummary.netQuantity} ${itemSummary.unit}",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFEA580C)
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("إجمالي القيمة", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = "${FormatUtils.formatAmount(itemSummary.netAmount)} ${FormatUtils.formatCurrencyCompact(currency)}",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF059669)
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("عدد العملاء", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = "${itemSummary.customersCount} عميل",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // Top Customer King Banner
                if (itemSummary.topCustomer != null && itemSummary.topCustomer.netQuantity > 0) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7)),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(Color(0xFFF59E0B), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("👑", fontSize = 18.sp)
                                }
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text("العميل الأكثر سحباً لهذا الصنف", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF92400E))
                                    Text(
                                        text = itemSummary.topCustomer.clientName,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF78350F)
                                    )
                                }
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "${itemSummary.topCustomer.netQuantity} ${itemSummary.unit}",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF92400E)
                                )
                                Text(
                                    text = "يمثل %.1f%% من المسحوب".format(itemSummary.topCustomer.percentageOfTotal),
                                    fontSize = 10.sp,
                                    color = Color(0xFFB45309),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Text(
                    text = "قائمة العملاء مرتبين حسب كمية سحبهم:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Customers Breakdown List
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(itemSummary.customers) { index, customer ->
                        CustomerDetailRow(
                            rank = index + 1,
                            customer = customer,
                            unit = itemSummary.unit,
                            currency = currency,
                            onInvoiceClick = onNavigateToInvoice,
                            onClientClick = { customer.clientId?.let { onNavigateToClient(it) } }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CustomerDetailRow(
    rank: Int,
    customer: CustomerMovementDetail,
    unit: String,
    currency: String,
    onInvoiceClick: (Int) -> Unit,
    onClientClick: () -> Unit
) {
    var expandedInvoices by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Rank circle
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(
                                if (customer.isTopCustomer) Color(0xFFF59E0B) else MaterialTheme.colorScheme.primaryContainer,
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (customer.isTopCustomer) "👑" else "$rank",
                            fontSize = if (customer.isTopCustomer) 12.sp else 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (customer.isTopCustomer) Color.White else MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            text = customer.clientName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            modifier = Modifier.clickable { onClientClick() },
                            color = if (customer.clientId != null && customer.clientId > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                        if (customer.phone.isNotBlank()) {
                            Text(customer.phone, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${customer.netQuantity}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color(0xFFEA580C)
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(unit, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        text = "${FormatUtils.formatAmount(customer.netAmount)} ${FormatUtils.formatCurrencyCompact(currency)}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF059669)
                    )
                }
            }

            // Visual Progress Bar representing client's share
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "حصة السحب: %.1f%%".format(customer.percentageOfTotal),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (customer.returnedQuantity > 0) {
                        Text(
                            text = "مرتجع: -${customer.returnedQuantity} $unit",
                            fontSize = 9.sp,
                            color = Color(0xFFDC2626)
                        )
                    }
                }
                Spacer(Modifier.height(3.dp))
                LinearProgressIndicator(
                    progress = { (customer.percentageOfTotal / 100.0).toFloat().coerceIn(0.01f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = if (customer.isTopCustomer) Color(0xFFF59E0B) else MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }

            // Expandable invoices button
            if (customer.invoices.isNotEmpty() || customer.returns.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expandedInvoices = !expandedInvoices }
                        .padding(top = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "الفواتير والمرتجعات (${customer.invoices.size} فاتورة" +
                                if (customer.returns.isNotEmpty()) " / ${customer.returns.size} مرتجع" else "" + ")",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(
                        imageVector = if (expandedInvoices) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                if (expandedInvoices) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                            .padding(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Invoices
                        customer.invoices.forEach { inv ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onInvoiceClick(inv.invoiceId) }
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Receipt, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = inv.invoiceNumber,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = DateTimeUtils.formatDateOnly(inv.date),
                                        fontSize = 9.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = "+${inv.quantity} $unit (${FormatUtils.formatAmount(inv.totalPrice)} ${FormatUtils.formatCurrencyCompact(inv.currency)})",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF059669)
                                )
                            }
                        }

                        // Returns
                        customer.returns.forEach { ret ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.AssignmentReturn, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color(0xFFDC2626))
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = ret.returnNumber,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFDC2626)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "${DateTimeUtils.formatDateOnly(ret.date)} • ${ret.reason}",
                                        fontSize = 9.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = "-${ret.quantity} $unit (-${FormatUtils.formatAmount(ret.totalPrice)} ${FormatUtils.formatCurrencyCompact(currency)})",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFDC2626)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyMovementState(hasFilter: Boolean) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .background(Color(0xFFFEF3C7), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Inventory2,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = Color(0xFFD97706)
                )
            }
            Text(
                text = if (hasFilter) "لا توجد أصناف تطابق شروط البحث أو التصفية" else "لا توجد حركة مبيعات مسجلة في هذه الفترة",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                textAlign = TextAlign.Center
            )
            Text(
                text = if (hasFilter) "جرب تغيير الفترة الزمنية أو العملة أو عبارة البحث" else "ستظهر الأصناف وتحليلات سحب العملاء فور إصدار فواتير مبيعات",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
