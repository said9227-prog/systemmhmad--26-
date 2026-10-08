package com.example.ui.screens.returns

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.data.model.ProductReturn
import com.example.data.model.ProductReturnItem
import com.example.ui.viewmodel.ReturnViewModel
import java.text.SimpleDateFormat
import java.util.*
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.MoreVert
import com.example.util.exportReturnToPdf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReturnDetailsScreen(
    navController: NavController,
    viewModel: ReturnViewModel,
    returnId: Int
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var productReturn by remember { mutableStateOf<ProductReturn?>(null) }
    var returnItems by remember { mutableStateOf<List<ProductReturnItem>>(emptyList()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    
    LaunchedEffect(returnId) {
        productReturn = viewModel.getReturnById(returnId)
        returnItems = viewModel.getReturnItemsList(returnId)
    }

    val generateShareText = {
        val pr = productReturn
        if (pr != null) {
            com.example.util.ShareManager.buildReturnSummaryText(pr, returnItems, "الحكيمي للأدوية والمستلزمات الطبية")
        } else ""
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("تفاصيل المرتجع") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                actions = {
                    if (productReturn != null) {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "المزيد")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("مشاركة نصية") },
                                leadingIcon = { Icon(Icons.Default.Message, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, generateShareText())
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, "مشاركة المرتجع عبر"))
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("مشاركة واتساب") },
                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        setPackage("com.whatsapp")
                                        putExtra(Intent.EXTRA_TEXT, generateShareText())
                                    }
                                    try {
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "الواتساب غير مثبت", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("مشاركة أو تصدير PDF") },
                                leadingIcon = { Icon(Icons.Default.PictureAsPdf, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    productReturn?.let { pr ->
                                        coroutineScope.launch {
                                            com.example.util.ShareManager.shareReturn(context, pr, returnItems, "الحكيمي للأدوية والمستلزمات الطبية", shareAsPdf = true)
                                        }
                                    }
                                }
                            )
                        }
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "حذف المرتجع", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        if (productReturn == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val pr = productReturn!!
            val isCustomer = pr.type == "CUSTOMER"
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
            
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(pr.returnNumber, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Surface(
                                    color = if (isCustomer) Color(0xFFE8F5E9) else Color(0xFFFFF3E0),
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Text(
                                        text = if (isCustomer) "مرتجع مبيعات" else "مرتجع مشتريات",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (isCustomer) Color(0xFF2E7D32) else Color(0xFFE65100)
                                    )
                                }
                            }
                            
                            Divider()
                            
                            DetailRow("التاريخ:", dateFormat.format(Date(pr.date)))
                            if (isCustomer) {
                                DetailRow("العميل:", pr.clientName)
                            } else {
                                DetailRow("المورد:", pr.supplierName)
                            }
                            DetailRow("الفاتورة الأصلية:", if (pr.invoiceNumber.isNotEmpty()) pr.invoiceNumber else "غير محددة")
                            DetailRow("عملة الحساب الأساسية:", pr.baseCurrency.ifBlank { "الريال اليمني" })
                            if (pr.reason.isNotBlank()) DetailRow("السبب:", pr.reason)
                            if (pr.notes.isNotBlank()) DetailRow("الملاحظات:", pr.notes)
                            
                            Divider()
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("إجمالي المرتجع المحول:", style = MaterialTheme.typography.titleMedium)
                                val displayTotal = if (pr.convertedTotalAmount > 0) pr.convertedTotalAmount else pr.totalAmount
                                val displayCurr = if (pr.convertedTotalAmount > 0) pr.baseCurrency else pr.currency
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "${com.example.util.FormatUtils.formatAmount(displayTotal)} $displayCurr",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    if (pr.convertedTotalAmount > 0 && pr.baseCurrency != pr.currency && pr.currency.isNotBlank()) {
                                        Text(
                                            text = "(${com.example.util.FormatUtils.formatAmount(pr.totalAmount)} ${pr.currency})",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.Gray
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                
                item {
                    Text("الأصناف المرتجعة (${returnItems.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                
                items(returnItems) { item ->
                    val itemCurr = item.currency.ifBlank { pr.currency.ifBlank { "الريال اليمني" } }
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.itemName, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("الكمية: ${item.quantity} ${item.unit}", style = MaterialTheme.typography.bodySmall)
                                Text("سعر الوحدة: ${com.example.util.FormatUtils.formatAmount(item.unitPrice)} $itemCurr", style = MaterialTheme.typography.bodySmall)
                                if (itemCurr != pr.baseCurrency && item.convertedTotalPrice > 0) {
                                    Text("المقابل بالحساب: ${com.example.util.FormatUtils.formatAmount(item.convertedTotalPrice)} ${pr.baseCurrency}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                                }
                                if (isCustomer) {
                                    Text("الحالة: ${item.itemCondition}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    "${com.example.util.FormatUtils.formatAmount(item.totalPrice)} $itemCurr",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (itemCurr != pr.baseCurrency && item.convertedTotalPrice > 0) {
                                    Text(
                                        "${com.example.util.FormatUtils.formatAmount(item.convertedTotalPrice)} ${pr.baseCurrency}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.Gray
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirm && productReturn != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("تأكيد الحذف") },
            text = { Text("هل أنت متأكد من حذف هذا المرتجع؟ سيتم التراجع عن تأثيره على المخزون وحساب العميل/المورد.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteReturn(productReturn!!)
                        showDeleteConfirm = false
                        navController.popBackStack()
                    }
                ) {
                    Text("حذف", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
        Text(value, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
    }
}
