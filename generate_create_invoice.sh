#!/bin/bash
cat << 'INNER_EOF' > app/src/main/java/com/example/ui/screens/CreateInvoiceScreen.kt
package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Client
import com.example.data.model.Item
import com.example.ui.viewmodel.AppViewModel
import com.example.util.FormatUtils
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateInvoiceScreen(
    viewModel: AppViewModel,
    initialClientId: Int? = null,
    onNavigateBack: () -> Unit,
    onNavigateToInvoiceDetails: (Int) -> Unit
) {
    BackHandler {
        onNavigateBack()
    }
    
    val context = LocalContext.current
    val clientsList by viewModel.clients.collectAsState()
    val itemsList by viewModel.items.collectAsState()
    val settings by viewModel.storeSettings.collectAsState()
    val cart by viewModel.invoiceCart.collectAsState()
    
    var selectedCurrency by remember { mutableStateOf(settings.currency.ifBlank { "الريال اليمني" }) }
    var selectedClient by remember { mutableStateOf<Client?>(null) }
    
    // Auto populate client if initialClientId provided
    LaunchedEffect(initialClientId, clientsList) {
        if (initialClientId != null && selectedClient == null) {
            selectedClient = clientsList.find { it.id == initialClientId }
        }
    }

    var clientSearchQuery by remember { mutableStateOf("") }
    var itemSearchQuery by remember { mutableStateOf("") }
    
    // Payment States
    val subtotal = cart.sumOf { it.customPrice * it.quantity }
    var paidAmountStr by remember { mutableStateOf("") }
    val paidAmount = paidAmountStr.toDoubleOrNull() ?: 0.0
    val remainingAmount = (subtotal - paidAmount).coerceAtLeast(0.0)
    
    // Auto generated Date and Time
    val sdfDate = SimpleDateFormat("dd MMMM yyyy", Locale("ar"))
    val sdfTime = SimpleDateFormat("hh:mm a", Locale("ar"))
    val currentDate = sdfDate.format(Date())
    val currentTime = sdfTime.format(Date())
    val nextInvoiceNum = "${settings.invoicePrefix}${settings.lastInvoiceNumber + 1}"

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Header
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Receipt, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("فاتورة جديدة", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Divider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("رقم الفاتورة: #$nextInvoiceNum", fontWeight = FontWeight.Bold)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("التاريخ: $currentDate", fontSize = 14.sp)
                        Text("الوقت: $currentTime", fontSize = 14.sp)
                    }
                }
            }
        }
        
        // 2. Currency Selection
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("اختر عملة الفاتورة:", fontWeight = FontWeight.Bold)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("الريال اليمني" to "🇾🇪 ريال يمني", "الريال السعودي" to "🇸🇦 ريال سعودي", "الدولار الأمريكي" to "🇺🇸 دولار أمريكي").forEach { (curr, label) ->
                        FilterChip(
                            selected = selectedCurrency == curr,
                            onClick = { selectedCurrency = curr },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                }
            }
        }
        
        // 3. Client Selection
        item {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("العميل", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                    
                    if (selectedClient == null) {
                        OutlinedTextField(
                            value = clientSearchQuery,
                            onValueChange = { clientSearchQuery = it },
                            placeholder = { Text("ابحث عن عميل...") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        
                        if (clientSearchQuery.length >= 2) {
                            val q = clientSearchQuery.lowercase()
                            val results = clientsList.filter { 
                                it.name.lowercase().startsWith(q) || it.name.lowercase().contains(q) || it.phone.contains(q)
                            }.sortedByDescending { it.name.lowercase().startsWith(q) }.take(5)
                            
                            if (results.isNotEmpty()) {
                                Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))) {
                                    results.forEach { client ->
                                        Text(
                                            text = client.name,
                                            modifier = Modifier.fillMaxWidth().clickable {
                                                selectedClient = client
                                                clientSearchQuery = ""
                                            }.padding(12.dp),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // Show selected client info
                        Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)).padding(12.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(selectedClient!!.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                IconButton(onClick = { selectedClient = null; viewModel.clearInvoiceDraft() }) {
                                    Icon(Icons.Default.Close, contentDescription = "Remove Client", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                            
                            val balance = selectedClient!!.balance
                            if (balance > 0) {
                                Text("🔴 عليه: ${FormatUtils.formatAmount(balance)} $selectedCurrency", fontWeight = FontWeight.Bold, color = Color(0xFFD32F2F))
                            } else if (balance < 0) {
                                Text("🟢 له: ${FormatUtils.formatAmount(-balance)} $selectedCurrency", fontWeight = FontWeight.Bold, color = Color(0xFF388E3C))
                            } else {
                                Text("الرصيد: 0 $selectedCurrency")
                            }
                        }
                    }
                }
            }
        }
        
        // 4. Items Selection & Cart
        if (selectedClient != null) {
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Inventory2, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("الأصناف", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                        
                        OutlinedTextField(
                            value = itemSearchQuery,
                            onValueChange = { itemSearchQuery = it },
                            placeholder = { Text("ابحث عن صنف...") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        
                        if (itemSearchQuery.length >= 2) {
                            val q = itemSearchQuery.lowercase()
                            val results = itemsList.filter { 
                                it.name.lowercase().startsWith(q) || it.name.lowercase().contains(q)
                            }.sortedByDescending { it.name.lowercase().startsWith(q) }.take(5)
                            
                            if (results.isNotEmpty()) {
                                Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))) {
                                    results.forEach { item ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth().clickable {
                                                viewModel.addCartItem(item, 1)
                                                itemSearchQuery = ""
                                            }.padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column {
                                                Text(item.name, fontWeight = FontWeight.Bold)
                                                Text("💰 ${FormatUtils.formatAmount(item.sellingPrice)} $selectedCurrency", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                            }
                                            Text("المخزون: ${item.quantity}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                        
                        // Show Cart Items
                        if (cart.isNotEmpty()) {
                            Divider(modifier = Modifier.padding(vertical = 8.dp))
                            cart.forEach { cartItem ->
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(cartItem.item.name, fontWeight = FontWeight.Bold)
                                        Text("السعر: ${FormatUtils.formatAmount(cartItem.customPrice)} $selectedCurrency", fontSize = 12.sp)
                                        Text("الإجمالي: ${FormatUtils.formatAmount(cartItem.customPrice * cartItem.quantity)} $selectedCurrency", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    }
                                    
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(onClick = { viewModel.updateCartItemQuantity(cartItem, cartItem.quantity - 1) }) {
                                            Icon(Icons.Default.RemoveCircleOutline, contentDescription = "Decrease", tint = MaterialTheme.colorScheme.primary)
                                        }
                                        Text("${cartItem.quantity}", fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp))
                                        IconButton(onClick = { viewModel.updateCartItemQuantity(cartItem, cartItem.quantity + 1) }) {
                                            Icon(Icons.Default.AddCircleOutline, contentDescription = "Increase", tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            // 5. Summary & Payment
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("إجمالي الفاتورة", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text("${FormatUtils.formatAmount(subtotal)} $selectedCurrency", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                        }
                        
                        Divider()
                        
                        Text("المبلغ المدفوع الآن:", fontWeight = FontWeight.Bold)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { paidAmountStr = subtotal.toString() }, modifier = Modifier.weight(1f)) {
                                Text("دفع كامل")
                            }
                            OutlinedButton(onClick = { paidAmountStr = (subtotal / 2).toString() }, modifier = Modifier.weight(1f)) {
                                Text("دفع جزئي")
                            }
                            OutlinedButton(onClick = { paidAmountStr = "0" }, modifier = Modifier.weight(1f)) {
                                Text("بدون دفع")
                            }
                        }
                        
                        OutlinedTextField(
                            value = paidAmountStr,
                            onValueChange = { paidAmountStr = it },
                            label = { Text("المبلغ المدفوع ($selectedCurrency)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        
                        val oldBalance = selectedClient!!.balance
                        val newBalance = oldBalance + remainingAmount - (if (paidAmount > subtotal) (paidAmount - subtotal) else 0.0)
                        
                        Column(modifier = Modifier.fillMaxWidth().background(if (remainingAmount > 0) Color(0xFFFFF3E0) else Color(0xFFE8F5E9), RoundedCornerShape(8.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("الرصيد السابق:")
                                Text("${FormatUtils.formatAmount(oldBalance)} $selectedCurrency", fontWeight = FontWeight.Bold)
                            }
                            if (remainingAmount > 0) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("المتبقي من الفاتورة:")
                                    Text("+${FormatUtils.formatAmount(remainingAmount)} $selectedCurrency", fontWeight = FontWeight.Bold, color = Color(0xFFD32F2F))
                                }
                            } else if (paidAmount > subtotal) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("فائض المدفوع:")
                                    Text("+${FormatUtils.formatAmount(paidAmount - subtotal)} $selectedCurrency", fontWeight = FontWeight.Bold, color = Color(0xFF388E3C))
                                }
                            }
                            Divider()
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("الرصيد الجديد للعميل:")
                                Text("${FormatUtils.formatAmount(newBalance)} $selectedCurrency", fontWeight = FontWeight.ExtraBold)
                            }
                        }
                        
                        Button(
                            onClick = {
                                if (cart.isNotEmpty() && selectedClient != null) {
                                    viewModel.setSelectedClient(selectedClient!!)
                                    viewModel.saveDetailedInvoice(
                                        paidAmount = paidAmount,
                                        currency = selectedCurrency,
                                        onSuccess = { invoiceId ->
                                            Toast.makeText(context, "تم حفظ الفاتورة بنجاح", Toast.LENGTH_SHORT).show()
                                            onNavigateToInvoiceDetails(invoiceId)
                                        }
                                    )
                                } else {
                                    Toast.makeText(context, "الرجاء اختيار عميل وإضافة أصناف", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("حفظ الفاتورة", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
INNER_EOF
