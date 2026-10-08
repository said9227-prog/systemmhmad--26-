import re

with open("app/src/main/java/com/example/ui/screens/ItemScreen.kt", "r") as f:
    content = f.read()

# We want to replace the `fun ItemCard` entirely.
# Let's find it.
pattern = re.compile(r"@Composable\s*\nfun ItemCard\(.*?\}\n\}\n", re.DOTALL)

new_item_card = """@Composable
fun ItemCard(
    item: Item,
    history: List<ItemPurchaseHistory>,
    currency: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val isLowStock = item.quantity <= item.minQuantityAlert
    var showHistoryDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(1.dp, RoundedCornerShape(10.dp))
            .clickable { showHistoryDialog = true },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Header Row (Item Name, Category, Menu)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                if (isLowStock) Color(0xFFFEF2F2) else MaterialTheme.colorScheme.secondaryContainer,
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Inventory2,
                            contentDescription = null,
                            tint = if (isLowStock) Color(0xFFDC2626) else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (item.category.isNotBlank()) {
                                Text(
                                    text = item.category,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("•", fontSize = 11.sp, color = Color.Gray.copy(alpha = 0.5f))
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            
                            val supplierLabel = if (item.supplierType == "شركة" && item.supplierCompanyName.isNotBlank()) {
                                "🏢 ${item.supplierCompanyName}"
                            } else if (item.supplierType == "مورد فردي" && item.individualSupplierName.isNotBlank()) {
                                "👤 ${item.individualSupplierName}"
                            } else null
                            
                            if (supplierLabel != null) {
                                Text(
                                    text = supplierLabel,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
                
                // Edit/Delete Dropdown
                Row {
                    if (isLowStock) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFEE2E2)),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text(
                                text = "نقص المخزون",
                                color = Color(0xFFDC2626),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    var showItemMenu by remember { mutableStateOf(false) }
                    IconButton(onClick = { showItemMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "خيارات")
                    }
                    DropdownMenu(
                        expanded = showItemMenu,
                        onDismissRequest = { showItemMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("تعديل") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = {
                                showItemMenu = false
                                onEdit()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("حذف") },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red) },
                            onClick = {
                                showItemMenu = false
                                onDelete()
                            }
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(8.dp))

            // Smart Summary Row
            val currentPrice = item.purchasePrice
            val previousPrice = if (history.size > 1) history[1].purchasePrice else null
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Stock Info
                Column {
                    Text("المخزون", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = "${item.quantity} ${item.unit}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isLowStock) Color(0xFFDC2626) else Color(0xFF059669)
                    )
                }

                // Last Purchase
                Column {
                    Text("آخر شراء", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = DateTimeUtils.formatDateOnly(item.purchaseDate),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Current Price
                Column {
                    Text("السعر (${currency})", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = FormatUtils.formatAmount(currentPrice),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Price Change indicator
                Column(horizontalAlignment = Alignment.End) {
                    Text("السعر السابق", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (previousPrice != null) {
                        val change = currentPrice - previousPrice
                        val changePercent = if (previousPrice > 0) (change / previousPrice) * 100 else 0.0
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = FormatUtils.formatAmount(previousPrice),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = androidx.compose.ui.text.TextStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            if (change > 0) {
                                Text("🔴 +${"%.1f".format(changePercent)}%", fontSize = 11.sp, color = Color(0xFFDC2626), fontWeight = FontWeight.Bold)
                            } else if (change < 0) {
                                Text("🟢 -${"%.1f".format(kotlin.math.abs(changePercent))}%", fontSize = 11.sp, color = Color(0xFF059669), fontWeight = FontWeight.Bold)
                            } else {
                                Text("—", fontSize = 11.sp, color = Color.Gray)
                            }
                        }
                    } else {
                        Text("—", fontSize = 11.sp, color = Color.Gray)
                    }
                }
            }
        }
    }

    if (showHistoryDialog) {
        ItemHistoryDialog(
            item = item,
            history = history,
            currency = currency,
            onDismiss = { showHistoryDialog = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemHistoryDialog(
    item: Item,
    history: List<ItemPurchaseHistory>,
    currency: String,
    onDismiss: () -> Unit
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(item.name, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(
                            "المورد الحالي: ${if (item.supplierType == "شركة") item.supplierCompanyName else item.individualSupplierName}",
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                            fontSize = 12.sp
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }

                if (history.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("لا يوجد سجل شراء مسبق لهذا الصنف.", color = Color.Gray)
                    }
                } else {
                    val stats = history.map { it.purchasePrice }
                    val maxPrice = stats.maxOrNull() ?: 0.0
                    val minPrice = stats.minOrNull() ?: 0.0
                    val avgPrice = if (stats.isNotEmpty()) stats.average() else 0.0

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        // Stats Box
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.5f)),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("أعلى سعر", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(FormatUtils.formatAmount(maxPrice), fontWeight = FontWeight.Bold, color = Color(0xFFDC2626))
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("متوسط", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(FormatUtils.formatAmount(avgPrice), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("أقل سعر", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(FormatUtils.formatAmount(minPrice), fontWeight = FontWeight.Bold, color = Color(0xFF059669))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        val historyByCompany = history.groupBy { it.supplierCompanyName }

                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            historyByCompany.forEach { (companyName, companyHistory) ->
                                item {
                                    Text(
                                        text = if (companyName.isNotBlank()) "🏢 سجل الشراء من: $companyName" else "مورد غير معروف",
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                }

                                items(companyHistory) { record ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp, horizontal = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(DateTimeUtils.formatDateOnly(record.purchaseDate), fontSize = 12.sp)
                                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.Gray)
                                        Text("${FormatUtils.formatAmount(record.purchasePrice)} $currency", fontWeight = FontWeight.Bold)
                                        Text("الكمية: ${record.quantity}", fontSize = 12.sp, color = Color.Gray)
                                    }
                                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha=0.5f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
"""

content = re.sub(pattern, new_item_card, content)

with open("app/src/main/java/com/example/ui/screens/ItemScreen.kt", "w") as f:
    f.write(content)

