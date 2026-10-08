package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Inventory
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Item
import com.example.ui.viewmodel.AppViewModel
import com.example.util.scrollToTopOnFocus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupplierItemsScreen(
    supplierName: String,
    viewModel: AppViewModel,
    onNavigateBack: () -> Unit
) {
    val itemsList by viewModel.items.collectAsState()
    val settings by viewModel.storeSettings.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var showOnlyLowStock by remember { mutableStateOf(false) }
    var itemToEdit by remember { mutableStateOf<Item?>(null) }
    var itemForHistory by remember { mutableStateOf<Item?>(null) }

    // Filter all items for this specific supplier / company
    val supplierItems = remember(itemsList, supplierName) {
        itemsList.filter { item ->
            (item.supplierType == "شركة" && item.supplierCompanyName.trim().equals(supplierName.trim(), ignoreCase = true)) ||
            (item.supplierType == "مورد فردي" && item.individualSupplierName.trim().equals(supplierName.trim(), ignoreCase = true))
        }
    }

    // Determine supplier type (Company vs Individual)
    val isCompany = remember(supplierItems) {
        supplierItems.firstOrNull()?.supplierType == "شركة" || !supplierItems.any { it.supplierType == "مورد فردي" }
    }

    // Statistics for this supplier
    val totalItemsCount = supplierItems.size
    val totalQuantity = supplierItems.sumOf { it.quantity }
    val lowStockCount = remember(supplierItems) {
        supplierItems.count { it.quantity <= it.minQuantityAlert }
    }

    // Filter items based on search and low-stock filter
    val filteredItems = remember(supplierItems, searchQuery, showOnlyLowStock) {
        supplierItems.filter { item ->
            val matchesSearch = item.name.contains(searchQuery, ignoreCase = true) ||
                                item.barcode.contains(searchQuery) ||
                                item.category.contains(searchQuery, ignoreCase = true)
            val matchesLowStock = !showOnlyLowStock || item.quantity <= item.minQuantityAlert
            matchesSearch && matchesLowStock
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(
                                    if (isCompany) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isCompany) Icons.Default.Business else Icons.Default.Person,
                                contentDescription = null,
                                tint = if (isCompany) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = supplierName,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isCompany) "نافذة الشركة الخاصة" else "نافذة المورد الفردي",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 12.dp)
        ) {
            Spacer(modifier = Modifier.height(6.dp))

            // 1. Top Summary Card: Total items and total quantity for this company
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(1.dp, RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "عدد أصناف $supplierName:",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "$totalItemsCount",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "أصناف مسجلة",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Vertical Separator
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(32.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "إجمالي المخزون المتوفر:",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "$totalQuantity",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF059669)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "وحدة بالمستودع",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 2. Low Stock Warning Card with explicit warning text before button click
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showOnlyLowStock = !showOnlyLowStock },
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (showOnlyLowStock) Color(0xFFFEE2E2)
                    else if (lowStockCount > 0) Color(0xFFFFFBEB)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                ),
                border = BorderStroke(
                    width = 1.dp,
                    color = if (showOnlyLowStock) Color(0xFFDC2626)
                    else if (lowStockCount > 0) Color(0xFFF59E0B)
                    else MaterialTheme.colorScheme.outlineVariant
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(
                                    if (lowStockCount > 0) Color(0xFFFEE2E2) else Color(0xFFE0F2FE),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (lowStockCount > 0) Icons.Default.Warning else Icons.Default.Inventory2,
                                contentDescription = "التخزين المنخفض",
                                tint = if (lowStockCount > 0) Color(0xFFDC2626) else Color(0xFF0284C7),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "مستوى التخزين المنخفض",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (lowStockCount > 0) Color(0xFFB45309) else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (lowStockCount > 0) {
                                    "⚠️ يوجد $lowStockCount أصناف على وشك النفاد كتحذير"
                                } else {
                                    "✅ جميع الأصناف متوفرة ولا يوجد نواقص"
                                },
                                fontSize = 11.sp,
                                color = if (lowStockCount > 0) Color(0xFFDC2626) else Color(0xFF059669),
                                fontWeight = if (lowStockCount > 0) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }

                    FilterChip(
                        selected = showOnlyLowStock,
                        onClick = { showOnlyLowStock = !showOnlyLowStock },
                        label = {
                            Text(
                                text = if (showOnlyLowStock) "عرض الكل" else "تصفية النواقص ($lowStockCount)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFDC2626),
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 3. Search Bar for this supplier's window
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("ابحث في أصناف $supplierName...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "مسح")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .scrollToTopOnFocus()
                    .testTag("supplier_search_input"),
                shape = RoundedCornerShape(8.dp),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 4. Items List
            if (filteredItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Outlined.Inventory,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (showOnlyLowStock) "لا توجد أصناف منخفضة المخزون لهذا المورد"
                                   else if (searchQuery.isNotBlank()) "لم يتم العثور على أصناف تطابق البحث"
                                   else "لا توجد أصناف مسجلة لهذا المورد",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredItems, key = { it.id }) { item ->
                        ItemCard(
                            item = item,
                            currency = settings.currency,
                            onEdit = { itemToEdit = item },
                            onDelete = { viewModel.deleteItem(item) },
                            onShowHistory = { itemForHistory = item }
                        )
                    }
                }
            }
        }
    }

    // Edit Item Dialog if triggered
    itemToEdit?.let { item ->
        ItemFormDialog(
            title = "تعديل صنف ($supplierName)",
            item = item,
            onDismiss = { itemToEdit = null },
            onSave = { name, barcode, category, purchasePrice, sellingPrice, quantity, minQty, imageUri, purchaseCurr, sellingCurr ->
                viewModel.updateItem(
                    item.copy(
                        name = name,
                        barcode = barcode,
                        category = category,
                        purchasePrice = purchasePrice,
                        sellingPrice = sellingPrice,
                        quantity = quantity,
                        minQuantityAlert = minQty,
                        imageUri = imageUri,
                        purchaseCurrency = purchaseCurr,
                        sellingCurrency = sellingCurr
                    )
                )
                itemToEdit = null
            }
        )
    }

    // Purchase History Dialog (Lazy loaded on click to eliminate N+1 queries)
    itemForHistory?.let { selectedItem ->
        val history by viewModel.getItemPurchaseHistory(selectedItem.id).collectAsState(initial = emptyList())
        ItemHistoryDialog(
            item = selectedItem,
            history = history,
            currency = settings.currency,
            onDismiss = { itemForHistory = null }
        )
    }
}
