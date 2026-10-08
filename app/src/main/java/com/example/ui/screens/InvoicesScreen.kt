package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.util.scrollToTopOnFocus
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.Invoice
import com.example.ui.viewmodel.AppViewModel
import com.example.util.FormatUtils
import com.example.util.ShareManager
import com.example.util.WhatsAppHelper
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoicesScreen(
    viewModel: AppViewModel,
    onNavigateToInvoiceDetails: (Int) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val invoicesData by viewModel.invoicesScreenData.collectAsState()
    val invoicesList = invoicesData.invoices
    val clientsList = invoicesData.clients
    val settings by viewModel.storeSettings.collectAsState()
    val exchangeRates = invoicesData.exchangeRates

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilterTab by remember { mutableStateOf(0) } // 0 = All, 1 = Final, 2 = Draft, 3 = Quick

    val filteredInvoices = remember(invoicesList, searchQuery, selectedFilterTab) {
        invoicesList.filter { inv ->
            val matchesSearch = inv.invoiceNumber.contains(searchQuery, ignoreCase = true) || 
                                inv.clientName.contains(searchQuery, ignoreCase = true)
            
            val matchesTab = when (selectedFilterTab) {
                1 -> !inv.isDraft
                2 -> inv.isDraft
                3 -> inv.isQuickInvoice
                else -> true
            }

            matchesSearch && matchesTab
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Search input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("ابحث برقم الفاتورة أو اسم العميل...") },
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
                    .testTag("invoice_search_input"),
                shape = RoundedCornerShape(8.dp)
            )

            Spacer(modifier = Modifier.scrollToTopOnFocus().scrollToTopOnFocus().height(12.dp))

            // Tab Filters
            TabRow(
                selectedTabIndex = selectedFilterTab,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .shadow(1.dp)
            ) {
                listOf("الكل", "نهائية", "مسودة", "سريعة").forEachIndexed { idx, title ->
                    Tab(
                        selected = selectedFilterTab == idx,
                        onClick = { selectedFilterTab = idx },
                        text = { Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Invoices Listing
            if (filteredInvoices.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Outlined.ReceiptLong,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "لا توجد فواتير مطابقة للبحث",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                var invoiceToDelete by remember { mutableStateOf<Invoice?>(null) }

                if (invoiceToDelete != null) {
                    AlertDialog(
                        onDismissRequest = { invoiceToDelete = null },
                        title = { Text("تأكيد حذف الفاتورة", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error) },
                        text = { Text("هل أنت متأكد من رغبتك في حذف الفاتورة رقم '${invoiceToDelete?.invoiceNumber}'؟ سيتم إعادة خصم المبالغ من سجلات العميل.") },
                        confirmButton = {
                            Button(
                                onClick = {
                                    invoiceToDelete?.let { viewModel.deleteInvoice(it) }
                                    invoiceToDelete = null
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("تأكيد الحذف", maxLines = 1, softWrap = false)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { invoiceToDelete = null }) {
                                Text("إلغاء", maxLines = 1, softWrap = false)
                            }
                        }
                    )
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = 90.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredInvoices, key = { it.id }) { invoice ->
                        InvoiceCard(
                            invoice = invoice,
                            currency = if (invoice.currency.isNotBlank()) invoice.currency else settings.currency,
                            onClick = { onNavigateToInvoiceDetails(invoice.id) },
                            onDelete = { invoiceToDelete = invoice },
                            onShareClick = {
                                val client = clientsList.find { it.id == invoice.clientId }
                                coroutineScope.launch {
                                    val items = viewModel.getInvoiceItems(invoice.id)
                                    ShareManager.shareInvoice(
                                        context = context,
                                        invoice = invoice,
                                        items = items,
                                        settings = settings,
                                        client = client,
                                        shareAsPdf = true,
                                        exchangeRates = exchangeRates
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun InvoiceCard(
    invoice: Invoice,
    currency: String,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onShareClick: () -> Unit = {}
) {
    val formattedDate = com.example.util.DateTimeUtils.formatDateTime12h(invoice.date)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(
                                if (invoice.isDraft) Color(0xFFFEF3C7) else MaterialTheme.colorScheme.secondaryContainer,
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (invoice.isQuickInvoice) Icons.Default.Bolt else Icons.Default.Receipt,
                            contentDescription = null,
                            tint = if (invoice.isDraft) Color(0xFFD97706) else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "فاتورة ${invoice.invoiceNumber}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (invoice.isDraft) Color(0xFFFEF3C7) else Color(0xFFECFDF5)
                                ),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = if (invoice.isDraft) "مسودة" else "نهائية",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (invoice.isDraft) Color(0xFFD97706) else Color(0xFF059669),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Text(
                            text = "العميل: ${invoice.clientName}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Amount
                Text(
                    text = "${FormatUtils.formatAmount(invoice.totalAmount)} ${FormatUtils.formatCurrencyCompact(currency)}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    softWrap = false
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(8.dp))

            // Time & Date, WhatsApp Icon, and Delete shortcut
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    softWrap = false
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    // Unified Share Action Button
                    Surface(
                        onClick = onShareClick,
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "مشاركة الفاتورة",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "مشاركة",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "حذف الفاتورة",
                            tint = Color.Red.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
