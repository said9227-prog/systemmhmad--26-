package com.example.ui.screens.returns

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.data.model.ProductReturn
import com.example.ui.viewmodel.ReturnViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReturnListScreen(
    navController: NavController,
    viewModel: ReturnViewModel
) {
    val returns by viewModel.returns.collectAsState()
    var selectedType by remember { mutableStateOf("ALL") } // ALL, CUSTOMER, PURCHASE
    var searchQuery by remember { mutableStateOf("") }
    
    val filteredReturns = returns.filter {
        val matchesType = selectedType == "ALL" || it.type == selectedType
        val matchesSearch = searchQuery.isBlank() || 
                it.clientName.contains(searchQuery, ignoreCase = true) ||
                it.supplierName.contains(searchQuery, ignoreCase = true) ||
                it.invoiceNumber.contains(searchQuery, ignoreCase = true) ||
                it.returnNumber.contains(searchQuery, ignoreCase = true)
        matchesType && matchesSearch
    }.sortedByDescending { it.date }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("سجل المرتجعات") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("ابحث برقم الفاتورة، اسم العميل، رقم المرتجع...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "بحث") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "مسح البحث")
                        }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.medium
            )
            
            // Filter Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedType == "ALL",
                    onClick = { selectedType = "ALL" },
                    label = { Text("الكل") }
                )
                FilterChip(
                    selected = selectedType == "CUSTOMER",
                    onClick = { selectedType = "CUSTOMER" },
                    label = { Text("مرتجع عملاء") }
                )
                FilterChip(
                    selected = selectedType == "PURCHASE",
                    onClick = { selectedType = "PURCHASE" },
                    label = { Text("مرتجع مشتريات") }
                )
            }

            if (filteredReturns.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("لا توجد سجلات", style = MaterialTheme.typography.bodyLarge, color = Color.Gray)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredReturns, key = { it.id }) { productReturn ->
                        ReturnItemCard(
                            productReturn = productReturn,
                            onClick = { navController.navigate("return_details/${productReturn.id}") }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ReturnItemCard(
    productReturn: ProductReturn,
    onClick: () -> Unit
) {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
    val isCustomer = productReturn.type == "CUSTOMER"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = productReturn.returnNumber,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    color = if (isCustomer) Color(0xFFE8F5E9) else Color(0xFFFFF3E0),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = if (isCustomer) "مرتجع عميل" else "مرتجع مورد",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isCustomer) Color(0xFF2E7D32) else Color(0xFFE65100)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Gray)
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isCustomer) productReturn.clientName else productReturn.supplierName,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Gray)
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = dateFormat.format(Date(productReturn.date)),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }

            if (productReturn.invoiceNumber.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Receipt, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Gray)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "الفاتورة: ${productReturn.invoiceNumber}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }
            
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "إجمالي قيمة المرتجع:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
                Column(horizontalAlignment = Alignment.End) {
                    val displayTotal = if (productReturn.convertedTotalAmount > 0) productReturn.convertedTotalAmount else productReturn.totalAmount
                    val displayCurr = if (productReturn.convertedTotalAmount > 0) productReturn.baseCurrency else productReturn.currency
                    Text(
                        text = "${com.example.util.FormatUtils.formatAmount(displayTotal)}\u00A0$displayCurr",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        softWrap = false
                    )
                    if (productReturn.convertedTotalAmount > 0 && productReturn.baseCurrency != productReturn.currency && productReturn.currency.isNotBlank()) {
                        Text(
                            text = "(${com.example.util.FormatUtils.formatAmount(productReturn.totalAmount)}\u00A0${productReturn.currency})",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray
                        )
                    }
                }
            }
        }
    }
}
