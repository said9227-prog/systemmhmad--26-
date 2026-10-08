package com.example.ui.screens.returns

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.util.FormatUtils
import androidx.navigation.NavController
import com.example.data.model.Client
import com.example.data.model.Invoice
import com.example.data.model.InvoiceItem
import com.example.data.model.ProductReturn
import com.example.data.model.ProductReturnItem
import com.example.data.model.SupplierCompany
import com.example.ui.viewmodel.AppViewModel
import com.example.ui.viewmodel.ReturnViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

import com.example.util.MonetaryUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateReturnScreen(
    navController: NavController,
    appViewModel: AppViewModel,
    returnViewModel: ReturnViewModel,
    returnType: String // "CUSTOMER" or "PURCHASE"
) {
    val coroutineScope = rememberCoroutineScope()
    val isCustomer = returnType == "CUSTOMER"

    val clients by appViewModel.clients.collectAsState()
    val suppliers by appViewModel.companies.collectAsState()
    val invoices by appViewModel.invoices.collectAsState()
    val allItems by appViewModel.items.collectAsState()
    val allReturns by returnViewModel.returns.collectAsState()
    val settings by returnViewModel.storeSettings.collectAsState()
    val exchangeRates by returnViewModel.exchangeRates.collectAsState()

    var selectedClient by remember { mutableStateOf<Client?>(null) }
    var selectedSupplier by remember { mutableStateOf<SupplierCompany?>(null) }
    var selectedInvoice by remember { mutableStateOf<Invoice?>(null) }
    
    var clientDropdownExpanded by remember { mutableStateOf(false) }
    var supplierDropdownExpanded by remember { mutableStateOf(false) }
    var invoiceDropdownExpanded by remember { mutableStateOf(false) }

    var reason by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var returnNumber by remember(settings.isAutoNumberingEnabled) {
        mutableStateOf(if (settings.isAutoNumberingEnabled) returnViewModel.generateReturnNumber(returnType, settings) else "")
    }

    val returnItems = remember { mutableStateListOf<ProductReturnItem>() }
    var originalInvoiceItems by remember { mutableStateOf<List<InvoiceItem>>(emptyList()) }
    var previousReturns by remember { mutableStateOf<List<ProductReturnItem>>(emptyList()) }

    var showAddItemDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    var invoiceSearchQuery by remember { mutableStateOf("") }
    var invoiceSearchResult by remember { mutableStateOf<Invoice?>(null) }
    var searchPerformed by remember { mutableStateOf(false) }

    val clientBaseCurrency = remember(selectedClient, isCustomer) {
        if (isCustomer && selectedClient != null) {
            selectedClient!!.currency.ifBlank { "الريال اليمني" }
        } else {
            "الريال اليمني"
        }
    }

    val totalAmount = returnItems.sumOf { it.totalPrice }
    val convertedTotalAmount = returnItems.sumOf { it.convertedTotalPrice }

    // When an invoice is selected, load its items and previous returns
    LaunchedEffect(selectedInvoice) {
        if (selectedInvoice != null) {
            originalInvoiceItems = appViewModel.getInvoiceItems(selectedInvoice!!.id)
            val pastReturns = allReturns.filter { it.invoiceId == selectedInvoice!!.id }
            val pastReturnItems = mutableListOf<ProductReturnItem>()
            for (pr in pastReturns) {
                pastReturnItems.addAll(returnViewModel.getReturnItemsList(pr.id))
            }
            previousReturns = pastReturnItems
            returnItems.clear()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isCustomer) "مرتجع مبيعات جديد" else "مرتجع مشتريات جديد") },
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
        },
        bottomBar = {
            Surface(
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("إجمالي قيمة المرتجع المحولة:", style = MaterialTheme.typography.bodyMedium)
                            Text("عملة الحساب: $clientBaseCurrency", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        }
                        Text(
                            text = "${FormatUtils.formatAmount(convertedTotalAmount)}\u00A0$clientBaseCurrency",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            if (returnNumber.trim().isBlank()) {
                                errorMessage = "يرجى إدخال رقم المرتجع"
                                return@Button
                            }
                            if (isCustomer && selectedClient == null) {
                                errorMessage = "يرجى اختيار العميل"
                                return@Button
                            }
                            if (!isCustomer && selectedSupplier == null) {
                                errorMessage = "يرجى اختيار المورد"
                                return@Button
                            }
                            if (returnItems.isEmpty()) {
                                errorMessage = "لا يمكن حفظ مرتجع بدون أصناف"
                                return@Button
                            }

                            val primaryCurrency = returnItems.firstOrNull()?.currency ?: clientBaseCurrency

                            val newReturn = ProductReturn(
                                returnNumber = returnNumber.trim(),
                                type = returnType,
                                invoiceId = selectedInvoice?.id,
                                invoiceNumber = selectedInvoice?.invoiceNumber ?: "",
                                clientId = selectedClient?.id,
                                clientName = selectedClient?.name ?: "",
                                supplierCompanyId = selectedSupplier?.id,
                                supplierName = selectedSupplier?.name ?: "",
                                supplierType = "شركة",
                                totalAmount = totalAmount,
                                reason = reason,
                                notes = notes,
                                currency = primaryCurrency,
                                baseCurrency = clientBaseCurrency,
                                convertedTotalAmount = convertedTotalAmount
                            )
                            
                            returnViewModel.saveReturn(
                                productReturn = newReturn,
                                items = returnItems.toList(),
                                onSuccess = { navController.popBackStack() },
                                onError = { errorMessage = it }
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text("حفظ المرتجع", modifier = Modifier.padding(vertical = 8.dp))
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                if (errorMessage != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(errorMessage!!, color = MaterialTheme.colorScheme.onErrorContainer)
                            TextButton(onClick = { errorMessage = null }) {
                                Text("إخفاء")
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("البيانات الأساسية", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        
                        OutlinedTextField(
                            value = returnNumber,
                            onValueChange = { returnNumber = it },
                            label = { Text(if (isCustomer) "رقم مرتجع المبيعات" + if (!settings.isAutoNumberingEnabled) " (يدوي) *" else "" else "رقم مرتجع المشتريات" + if (!settings.isAutoNumberingEnabled) " (يدوي) *" else "") },
                            placeholder = { Text("أدخل رقم المرتجع يدوياً...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = true
                        )
                        
                        if (isCustomer) {
                            OutlinedTextField(
                                value = invoiceSearchQuery,
                                onValueChange = { 
                                    invoiceSearchQuery = it
                                    searchPerformed = false
                                },
                                label = { Text("البحث برقم الفاتورة الأصلية") },
                                trailingIcon = {
                                    IconButton(onClick = {
                                        invoiceSearchResult = invoices.find { it.invoiceNumber.equals(invoiceSearchQuery, ignoreCase = true) && !it.isDraft }
                                        searchPerformed = true
                                    }) {
                                        Icon(Icons.Default.Search, contentDescription = "بحث")
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            
                            if (searchPerformed) {
                                if (invoiceSearchResult != null) {
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                selectedInvoice = invoiceSearchResult
                                                selectedClient = clients.find { it.id == invoiceSearchResult!!.clientId }
                                                invoiceSearchQuery = ""
                                                searchPerformed = false
                                            },
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Text("تم العثور على الفاتورة:", style = MaterialTheme.typography.labelMedium)
                                            Text("رقم: ${invoiceSearchResult!!.invoiceNumber}", fontWeight = FontWeight.Bold)
                                            Text("العميل: ${invoiceSearchResult!!.clientName}")
                                            Text("اضغط هنا لاختيار الفاتورة", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                } else {
                                    Text("لم يتم العثور على فاتورة بهذا الرقم", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                        
                        if (isCustomer) {
                            ExposedDropdownMenuBox(
                                expanded = clientDropdownExpanded,
                                onExpandedChange = { clientDropdownExpanded = it },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = selectedClient?.name ?: "",
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("العميل") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = clientDropdownExpanded) },
                                    modifier = Modifier
                                        .menuAnchor()
                                        .fillMaxWidth()
                                )
                                ExposedDropdownMenu(
                                    expanded = clientDropdownExpanded,
                                    onDismissRequest = { clientDropdownExpanded = false }
                                ) {
                                    clients.forEach { client ->
                                        DropdownMenuItem(
                                            text = { Text(client.name) },
                                            onClick = {
                                                selectedClient = client
                                                clientDropdownExpanded = false
                                                selectedInvoice = null
                                                returnItems.clear()
                                            }
                                        )
                                    }
                                }
                            }

                            if (selectedClient != null) {
                                val clientInvoices = invoices.filter { it.clientId == selectedClient!!.id && !it.isDraft }
                                ExposedDropdownMenuBox(
                                    expanded = invoiceDropdownExpanded,
                                    onExpandedChange = { invoiceDropdownExpanded = it },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    OutlinedTextField(
                                        value = selectedInvoice?.invoiceNumber ?: "بدون فاتورة محددة",
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text("الفاتورة الأصلية (اختياري)") },
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = invoiceDropdownExpanded) },
                                        modifier = Modifier
                                            .menuAnchor()
                                            .fillMaxWidth()
                                    )
                                    ExposedDropdownMenu(
                                        expanded = invoiceDropdownExpanded,
                                        onDismissRequest = { invoiceDropdownExpanded = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("بدون فاتورة محددة") },
                                            onClick = {
                                                selectedInvoice = null
                                                invoiceDropdownExpanded = false
                                                returnItems.clear()
                                            }
                                        )
                                        clientInvoices.forEach { inv ->
                                            val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(inv.date))
                                            DropdownMenuItem(
                                                text = { Text("${inv.invoiceNumber} - $dateStr") },
                                                onClick = {
                                                    selectedInvoice = inv
                                                    invoiceDropdownExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            ExposedDropdownMenuBox(
                                expanded = supplierDropdownExpanded,
                                onExpandedChange = { supplierDropdownExpanded = it },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = selectedSupplier?.name ?: "",
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("المورد") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = supplierDropdownExpanded) },
                                    modifier = Modifier
                                        .menuAnchor()
                                        .fillMaxWidth()
                                )
                                ExposedDropdownMenu(
                                    expanded = supplierDropdownExpanded,
                                    onDismissRequest = { supplierDropdownExpanded = false }
                                ) {
                                    suppliers.forEach { supplier ->
                                        DropdownMenuItem(
                                            text = { Text(supplier.name) },
                                            onClick = {
                                                selectedSupplier = supplier
                                                supplierDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        if (isCustomer && selectedClient != null) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                shape = MaterialTheme.shapes.small
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("عملة حساب العميل:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    Text(clientBaseCurrency, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }

                        OutlinedTextField(
                            value = reason,
                            onValueChange = { reason = it },
                            label = { Text("سبب المرتجع") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = notes,
                            onValueChange = { notes = it },
                            label = { Text("ملاحظات") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("الأصناف المرتجعة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (selectedInvoice != null || !isCustomer) {
                        TextButton(onClick = { showAddItemDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("إضافة صنف")
                        }
                    } else if (selectedClient != null) {
                        TextButton(onClick = { errorMessage = "يجب اختيار فاتورة أصلية أولاً للإرجاع الدقيق" }) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("إضافة صنف")
                        }
                    }
                }
            }

            if (returnItems.isEmpty()) {
                item {
                    Box(modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("لم يتم إضافة أصناف بعد", color = Color.Gray)
                    }
                }
            } else {
                items(returnItems) { item ->
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
                                Text("الكمية: ${item.quantity} × السعر: ${FormatUtils.formatAmount(item.unitPrice)}\u00A0${item.currency}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                if (item.currency != clientBaseCurrency) {
                                    Text("المقابل بالحساب: ${FormatUtils.formatAmount(item.convertedTotalPrice)}\u00A0$clientBaseCurrency", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                                }
                                if (isCustomer) {
                                    Text("الحالة: ${item.itemCondition}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    "${FormatUtils.formatAmount(item.totalPrice)}\u00A0${item.currency}",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (item.currency != clientBaseCurrency) {
                                    Text(
                                        "${FormatUtils.formatAmount(item.convertedTotalPrice)}\u00A0$clientBaseCurrency",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.Gray
                                    )
                                }
                            }
                            IconButton(onClick = { returnItems.remove(item) }) {
                                Icon(Icons.Default.Delete, contentDescription = "حذف", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddItemDialog) {
        var selectedInvoiceItem by remember { mutableStateOf<InvoiceItem?>(null) }
        var quantityText by remember { mutableStateOf("") }
        var condition by remember { mutableStateOf("صالح للبيع") }
        var itemSearchQuery by remember { mutableStateOf("") }
        var errorMsg by remember { mutableStateOf<String?>(null) }
        
        val maxAllowed = selectedInvoiceItem?.let { invItem ->
            val prevRet = previousReturns.filter { it.itemId == invItem.itemId }.sumOf { it.quantity }
            invItem.quantity - prevRet
        } ?: 0

        AlertDialog(
            onDismissRequest = { showAddItemDialog = false },
            title = { Text("إضافة صنف للمرتجع") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (errorMsg != null) {
                        Text(errorMsg!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    
                    if (selectedInvoiceItem == null) {
                        OutlinedTextField(
                            value = itemSearchQuery,
                            onValueChange = { itemSearchQuery = it },
                            label = { Text(if (isCustomer) "البحث عن صنف في الفاتورة" else "البحث عن صنف في المخزن") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "بحث") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        
                        Box(modifier = Modifier.heightIn(max = 200.dp)) {
                            LazyColumn {
                                if (isCustomer) {
                                    val filtered = originalInvoiceItems.filter { it.itemName.contains(itemSearchQuery, ignoreCase = true) }
                                    items(filtered) { invItem ->
                                        val prevRet = previousReturns.filter { it.itemId == invItem.itemId }.sumOf { it.quantity }
                                        val available = invItem.quantity - prevRet
                                        if (available > 0) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        selectedInvoiceItem = invItem
                                                        quantityText = available.toString()
                                                        errorMsg = null
                                                    }
                                                    .padding(vertical = 8.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(invItem.itemName)
                                                Text("المتاح: $available", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                            }
                                            Divider()
                                        }
                                    }
                                } else {
                                    val filtered = allItems.filter { it.name.contains(itemSearchQuery, ignoreCase = true) }
                                    items(filtered) { item ->
                                        if (item.quantity > 0) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        val unitPrice = if (isCustomer) item.sellingPrice else item.purchasePrice
                                                        val itemCurrency = if (isCustomer) item.sellingCurrency else item.purchaseCurrency
                                                        selectedInvoiceItem = InvoiceItem(
                                                            invoiceId = 0,
                                                            itemId = item.id,
                                                            itemName = item.name,
                                                            quantity = item.quantity,
                                                            unitPrice = unitPrice,
                                                            totalPrice = unitPrice
                                                        )
                                                        quantityText = "1"
                                                        errorMsg = null
                                                    }
                                                    .padding(vertical = 8.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(item.name)
                                                Text("المخزون: ${item.quantity}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                            }
                                            Divider()
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (selectedInvoiceItem != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(selectedInvoiceItem!!.itemName, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            TextButton(onClick = { selectedInvoiceItem = null; itemSearchQuery = "" }) {
                                Text("تغيير")
                            }
                        }
                        
                        if (isCustomer) {
                            Text("الكمية المتاحة للإرجاع: $maxAllowed", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        } else {
                            Text("رصيد المخزن المتوفر: ${selectedInvoiceItem!!.quantity}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                        
                        OutlinedTextField(
                            value = quantityText,
                            onValueChange = { quantityText = it },
                            label = { Text("الكمية المرتجعة") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (isCustomer) {
                            Text("حالة الصنف المرتجع", style = MaterialTheme.typography.labelMedium)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = condition == "صالح للبيع", onClick = { condition = "صالح للبيع" })
                                Text("صالح للبيع (يعاد للمخزون)", style = MaterialTheme.typography.bodySmall)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = condition == "تالف", onClick = { condition = "تالف" })
                                Text("تالف (لا يعاد للمخزون)", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val qty = quantityText.toIntOrNull() ?: 0
                        if (selectedInvoiceItem == null) {
                            errorMsg = "يرجى اختيار صنف"
                        } else if (qty <= 0) {
                            errorMsg = "الكمية يجب أن تكون أكبر من صفر"
                        } else if (qty > maxAllowed) {
                            errorMsg = "الكمية تتجاوز المتاح ($maxAllowed)"
                        } else {
                            // Check if already in list
                            val existing = returnItems.find { it.itemId == selectedInvoiceItem!!.itemId }
                            if (existing != null) {
                                errorMsg = "الصنف موجود مسبقاً في قائمة المرتجع"
                            } else {
                                val price = selectedInvoiceItem!!.unitPrice
                                val itemCurrency = selectedInvoiceItem!!.currency.ifBlank {
                                    selectedInvoiceItem!!.originalProductCurrency.ifBlank { "الريال اليمني" }
                                }
                                val lineTotal = price * qty
                                val conv = MonetaryUtils.convert(
                                    amount = lineTotal,
                                    fromCurrency = itemCurrency,
                                    toCurrency = clientBaseCurrency,
                                    rates = exchangeRates
                                )
                                returnItems.add(
                                    ProductReturnItem(
                                        returnId = 0,
                                        itemId = selectedInvoiceItem!!.itemId,
                                        itemName = selectedInvoiceItem!!.itemName,
                                        unit = "قطعة",
                                        quantity = qty,
                                        unitPrice = price,
                                        totalPrice = lineTotal,
                                        itemCondition = if (isCustomer) condition else "صالح للبيع",
                                        originalSoldQuantity = selectedInvoiceItem!!.quantity,
                                        currency = itemCurrency,
                                        exchangeRate = conv.exchangeRate,
                                        convertedTotalPrice = conv.convertedAmount,
                                        originalProductPrice = selectedInvoiceItem!!.originalProductPrice.takeIf { it > 0 } ?: price,
                                        originalProductCurrency = selectedInvoiceItem!!.originalProductCurrency.ifBlank { itemCurrency }
                                    )
                                )
                                showAddItemDialog = false
                            }
                        }
                    }
                ) {
                    Text("إضافة")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddItemDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }
}
