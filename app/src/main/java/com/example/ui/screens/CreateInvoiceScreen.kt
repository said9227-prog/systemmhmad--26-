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
import com.example.util.scrollToTopOnFocus
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Client
import com.example.data.model.Item
import com.example.ui.components.CurrencyConversionDialog
import com.example.ui.viewmodel.AppViewModel
import com.example.util.FormatUtils
import com.example.util.MonetaryUtils
import com.example.util.ShareManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import androidx.compose.ui.window.Dialog
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateInvoiceScreen(
    viewModel: AppViewModel,
    initialClientId: Int? = null,
    onNavigateBack: () -> Unit,
    onNavigateToInvoiceDetails: (Int) -> Unit,
    onNavigateToSettings: (() -> Unit)? = null
) {
    BackHandler {
        onNavigateBack()
    }
    
    val context = LocalContext.current
    val clientsList by viewModel.clients.collectAsState()
    val itemsList by viewModel.items.collectAsState()
    val settings by viewModel.storeSettings.collectAsState()
    val cart by viewModel.invoiceCart.collectAsState()
    val exchangeRates by viewModel.exchangeRates.collectAsState()
    
    var selectedClient by remember { mutableStateOf<Client?>(null) }
    val clientBaseCurrency = selectedClient?.currency?.ifBlank { "الريال اليمني" } ?: "الريال اليمني"
    var selectedCurrency by remember { mutableStateOf(clientBaseCurrency) }
    var showCurrencyConversionDialog by remember { mutableStateOf(false) }
    
    // Auto populate client if initialClientId provided and update invoice currency to match client's account currency
    LaunchedEffect(initialClientId, clientsList) {
        if (initialClientId != null && selectedClient == null) {
            val client = clientsList.find { it.id == initialClientId }
            if (client != null) {
                selectedClient = client
                selectedCurrency = client.currency.ifBlank { "الريال اليمني" }
            }
        }
    }

    // Always update invoice currency whenever selectedClient changes
    LaunchedEffect(selectedClient) {
        selectedClient?.let { client ->
            selectedCurrency = client.currency.ifBlank { "الريال اليمني" }
        }
    }

    var clientSearchQuery by remember { mutableStateOf("") }
    var itemSearchQuery by remember { mutableStateOf("") }
    
    // Payment States - exact calculation in client's base account currency
    val subtotalInClientCurrency = remember(cart, clientBaseCurrency, exchangeRates) {
        var sum = 0.0
        cart.forEach { cItem ->
            val total = cItem.customPrice * cItem.quantity
            val itemCurr = cItem.customCurrency.ifBlank { "الريال اليمني" }
            val conv = MonetaryUtils.convert(
                amount = total,
                fromCurrency = itemCurr,
                toCurrency = clientBaseCurrency,
                rates = exchangeRates
            )
            sum += conv.convertedAmount
        }
        MonetaryUtils.roundMoney(sum)
    }

    var paidAmountStr by remember { mutableStateOf("") }
    val paidAmount = paidAmountStr.toDoubleOrNull() ?: 0.0
    val remainingAmount = (subtotalInClientCurrency - paidAmount).coerceAtLeast(0.0)
    
    // Invoice Number State (empty by default for manual entry, auto-numbered if enabled in settings)
    val defaultAutoInvoiceNum = if (settings.invoicePrefix.isNotBlank()) "${settings.invoicePrefix}${if (settings.lastInvoiceNumber == 0) 1 else settings.lastInvoiceNumber + 1}" else "${if (settings.lastInvoiceNumber == 0) 1 else settings.lastInvoiceNumber + 1}"
    var invoiceNumber by remember(settings.isAutoNumberingEnabled, settings.lastInvoiceNumber) {
        mutableStateOf(if (settings.isAutoNumberingEnabled) defaultAutoInvoiceNum else "")
    }
    var invoiceNumberError by remember { mutableStateOf<String?>(null) }
    var voucherNumberStr by remember { mutableStateOf("") }

    // Date and Time customization
    var selectedDateTimeMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    val datePickerDialog = remember(context, selectedDateTimeMillis) {
        val cal = Calendar.getInstance().apply { timeInMillis = selectedDateTimeMillis }
        android.app.DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val updatedCal = Calendar.getInstance().apply {
                    timeInMillis = selectedDateTimeMillis
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                }
                selectedDateTimeMillis = updatedCal.timeInMillis
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    val timePickerDialog = remember(context, selectedDateTimeMillis) {
        val cal = Calendar.getInstance().apply { timeInMillis = selectedDateTimeMillis }
        android.app.TimePickerDialog(
            context,
            { _, hourOfDay, minute ->
                val updatedCal = Calendar.getInstance().apply {
                    timeInMillis = selectedDateTimeMillis
                    set(Calendar.HOUR_OF_DAY, hourOfDay)
                    set(Calendar.MINUTE, minute)
                }
                selectedDateTimeMillis = updatedCal.timeInMillis
            },
            cal.get(Calendar.HOUR_OF_DAY),
            cal.get(Calendar.MINUTE),
            false
        )
    }

    val sdfDate = remember { SimpleDateFormat("yyyy/MM/dd", Locale("ar")) }
    val sdfTime = remember { SimpleDateFormat("hh:mm a", Locale("ar")) }
    val formattedDate = sdfDate.format(Date(selectedDateTimeMillis))
    val formattedTime = sdfTime.format(Date(selectedDateTimeMillis))

    val scope = rememberCoroutineScope()
    var showSuccessDialog by remember { mutableStateOf(false) }
    var showMultiSelectItemsDialog by remember { mutableStateOf(false) }
    var savedInvoiceId by remember { mutableStateOf(0) }
    var showStockWarningDialog by remember { mutableStateOf(false) }
    var stockWarningDialogMessage by remember { mutableStateOf("") }

    if (showStockWarningDialog) {
        AlertDialog(
            onDismissRequest = { showStockWarningDialog = false },
            icon = {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    "تحذير: لا يمكن إتمام عملية السحب",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            },
            text = {
                Text(
                    stockWarningDialogMessage,
                    fontSize = 13.sp,
                    lineHeight = 22.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            confirmButton = {
                Button(
                    onClick = { showStockWarningDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("فهمت، سأعدل الكمية", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (showCurrencyConversionDialog) {
        CurrencyConversionDialog(
            rates = exchangeRates,
            initialAmount = 100.0,
            initialFromCurrency = "الريال السعودي",
            initialToCurrency = clientBaseCurrency,
            onNavigateToSettings = onNavigateToSettings,
            onDismiss = { showCurrencyConversionDialog = false }
        )
    }

    if (showMultiSelectItemsDialog) {
        MultiSelectItemDialog(
            itemsList = itemsList,
            currency = selectedCurrency,
            cart = cart,
            onDismiss = { showMultiSelectItemsDialog = false },
            onItemAdd = { item -> viewModel.addItemToCart(item, 1) },
            onItemRemove = { item -> cart.find { it.item.id == item.id }?.let { viewModel.removeCartItem(it) } }
        )
    }

    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = { 
                showSuccessDialog = false 
                onNavigateToInvoiceDetails(savedInvoiceId) 
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981))
                    Text("تم الحفظ بنجاح", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("تم حفظ الفاتورة بنجاح. يمكنك مشاركتها الآن عبر النظام أو عرض التفاصيل:")
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                        IconButton(onClick = { 
                            showSuccessDialog = false
                            scope.launch {
                                val inv = viewModel.getInvoiceById(savedInvoiceId)
                                if (inv != null) {
                                    val items = viewModel.getInvoiceItems(savedInvoiceId)
                                    ShareManager.shareInvoice(
                                        context = context,
                                        invoice = inv,
                                        items = items,
                                        settings = settings,
                                        client = selectedClient,
                                        shareAsPdf = true,
                                        exchangeRates = exchangeRates
                                    )
                                }
                                onNavigateToInvoiceDetails(savedInvoiceId)
                            }
                        }) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Share, contentDescription = "مشاركة", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                                Text("📤 مشاركة", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                            }
                        }
                        IconButton(onClick = { 
                            showSuccessDialog = false
                            scope.launch {
                                val inv = viewModel.getInvoiceById(savedInvoiceId)
                                if (inv != null) {
                                    val items = viewModel.getInvoiceItems(savedInvoiceId)
                                    ShareManager.shareInvoice(
                                        context = context,
                                        invoice = inv,
                                        items = items,
                                        settings = settings,
                                        client = selectedClient,
                                        shareAsPdf = true,
                                        exchangeRates = exchangeRates
                                    )
                                }
                                onNavigateToInvoiceDetails(savedInvoiceId)
                            }
                        }) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = "PDF", tint = Color(0xFFD32F2F), modifier = Modifier.size(32.dp))
                                Text("PDF", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                            }
                        }
                        IconButton(onClick = { 
                            Toast.makeText(context, "جاري إرسال الفاتورة لبروتوكول الطباعة...", Toast.LENGTH_SHORT).show()
                            showSuccessDialog = false
                            onNavigateToInvoiceDetails(savedInvoiceId)
                        }) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Print, contentDescription = "طباعة", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                                Text("طباعة", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { 
                    showSuccessDialog = false
                    onNavigateToInvoiceDetails(savedInvoiceId)
                }) {
                    Text("عرض الفاتورة", maxLines = 1, softWrap = false)
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 1. Header
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Receipt, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("فاتورة جديدة", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Divider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))
                    
                    // Editable / Manual Invoice Number Field
                    OutlinedTextField(
                        value = invoiceNumber,
                        onValueChange = {
                            invoiceNumber = it
                            if (invoiceNumberError != null) invoiceNumberError = null
                        },
                        label = { Text("رقم الفاتورة" + if (!settings.isAutoNumberingEnabled) " (تسجيل يدوي) *" else "") },
                        placeholder = { Text("أدخل رقم الفاتورة يدوياً...") },
                        isError = invoiceNumberError != null,
                        supportingText = invoiceNumberError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        )
                    )

                    // Date & Time pickers
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedCard(
                            onClick = { datePickerDialog.show() },
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.CalendarToday, contentDescription = "تعديل التاريخ", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                Column {
                                    Text("التاريخ (انقر للتعديل)", fontSize = 10.sp, color = Color.Gray)
                                    Text(formattedDate, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        OutlinedCard(
                            onClick = { timePickerDialog.show() },
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.AccessTime, contentDescription = "تعديل الوقت", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                Column {
                                    Text("الوقت (انقر للتعديل)", fontSize = 10.sp, color = Color.Gray)
                                    Text(formattedTime, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
        
        // 2. Client Account Currency & Exchange Conversion Shortcut
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(
                                Icons.Default.AccountBalanceWallet,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text("عملة حساب العميل:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = clientBaseCurrency,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = "يتم احتساب وخصم الصافي تلقائياً من رصيد العميل بهذه العملة",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    OutlinedButton(
                        onClick = { showCurrencyConversionDialog = true },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.CurrencyExchange, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("💱 أسعار الصرف", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        
        // 3. Client Selection
        item {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                            modifier = Modifier.scrollToTopOnFocus().scrollToTopOnFocus().fillMaxWidth(),
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
                                                if (client.currency.isNotBlank()) {
                                                    selectedCurrency = client.currency
                                                }
                                                clientSearchQuery = ""
                                            }.padding(8.dp),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // Show selected client info
                        Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)).padding(8.dp)) {
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
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
                    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.Inventory2, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text("الأصناف", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            }
                            TextButton(onClick = { showMultiSelectItemsDialog = true }) {
                                Icon(Icons.Default.List, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("تصفح وإضافة متعددة")
                            }
                        }
                        
                        OutlinedTextField(
                            value = itemSearchQuery,
                            onValueChange = { itemSearchQuery = it },
                            placeholder = { Text("ابحث عن صنف...") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            modifier = Modifier.scrollToTopOnFocus().scrollToTopOnFocus().fillMaxWidth(),
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
                                        val isOutOfStock = item.quantity <= 0
                                        Row(
                                            modifier = Modifier.fillMaxWidth().clickable {
                                                if (isOutOfStock) {
                                                    Toast.makeText(context, "الصنف '${item.name}' غير متوفر بالمخزن (الرصيد 0)", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    val existingInCart = cart.find { it.item.id == item.id }
                                                    if (existingInCart != null && existingInCart.quantity >= item.quantity) {
                                                        Toast.makeText(context, "الكمية في السلة وصلت للحد الأقصى في المخزن (${item.quantity} ${item.unit})", Toast.LENGTH_LONG).show()
                                                    } else {
                                                        viewModel.addItemToCart(item, 1)
                                                        itemSearchQuery = ""
                                                    }
                                                }
                                            }.padding(8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(item.name, fontWeight = FontWeight.Bold)
                                                Text("💰 ${FormatUtils.formatAmount(item.sellingPrice)} $selectedCurrency", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                            }
                                            if (isOutOfStock) {
                                                Text(
                                                    "نفد المخزون (0)",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.error
                                                )
                                            } else {
                                                Text("المخزون: ${item.quantity} ${item.unit}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        
                        // Show Cart Items
                        if (cart.isNotEmpty()) {
                            Divider(modifier = Modifier.padding(vertical = 8.dp))
                            cart.forEach { cartItem ->
                                val isOverStock = cartItem.quantity > cartItem.item.quantity
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(cartItem.item.name, fontWeight = FontWeight.Bold)
                                            val itemTotal = cartItem.customPrice * cartItem.quantity
                                            val itemCurr = cartItem.customCurrency.ifBlank { "الريال اليمني" }
                                            
                                            // Currency selection chips for this item
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text("عملة الشراء:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                listOf("الريال اليمني" to "🇾🇪 يمني", "الريال السعودي" to "🇸🇦 سعودي", "الدولار الأمريكي" to "🇺🇸 دولار").forEach { (currCode, currLabel) ->
                                                    FilterChip(
                                                        selected = itemCurr == currCode,
                                                        onClick = { viewModel.updateCartItemCurrency(cartItem, currCode) },
                                                        label = { Text(currLabel, fontSize = 10.sp) },
                                                        modifier = Modifier.height(28.dp),
                                                        colors = FilterChipDefaults.filterChipColors(
                                                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                                        )
                                                    )
                                                }
                                            }

                                            Text(
                                                "السعر: ${FormatUtils.formatAmount(cartItem.customPrice)} $itemCurr | المتوفر: ${cartItem.item.quantity} ${cartItem.item.unit}",
                                                fontSize = 12.sp,
                                                color = if (isOverStock) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                "الإجمالي: ${FormatUtils.formatAmount(itemTotal)} $itemCurr",
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )

                                            // If item currency differs from client's account currency, show converted amount
                                            if (itemCurr != clientBaseCurrency) {
                                                val conv = remember(itemCurr, clientBaseCurrency, itemTotal, exchangeRates) {
                                                    MonetaryUtils.convert(
                                                        amount = itemTotal,
                                                        fromCurrency = itemCurr,
                                                        toCurrency = clientBaseCurrency,
                                                        rates = exchangeRates
                                                    )
                                                }
                                                Text(
                                                    "المكافئ لحساب العميل: ${FormatUtils.formatAmount(conv.convertedAmount)} $clientBaseCurrency (سعر الصرف: ${conv.exchangeRate})",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF2E7D32)
                                                )
                                            }
                                        }
                                        
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            OutlinedTextField(
                                                value = cartItem.quantityInput,
                                                onValueChange = { newValue ->
                                                    viewModel.updateCartItemQuantity(cartItem, newValue)
                                                },
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                modifier = Modifier.scrollToTopOnFocus().scrollToTopOnFocus().width(76.dp),
                                                singleLine = true,
                                                isError = isOverStock,
                                                textStyle = LocalTextStyle.current.copy(
                                                    textAlign = TextAlign.Center,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isOverStock) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                                )
                                            )

                                            IconButton(
                                                onClick = { viewModel.removeCartItem(cartItem) },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.DeleteOutline,
                                                    contentDescription = "حذف من السلة",
                                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                                                )
                                            }
                                        }
                                    }

                                    // تحذير مرئي فوري إذا تجاوزت الكمية رصيد المخزن
                                    if (isOverStock) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f),
                                            shape = RoundedCornerShape(6.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 4.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    Icons.Default.Warning,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "⚠️ تحذير: الكمية المطلوبة (${cartItem.quantity}) أكبر من المتوفر بالمخزن (${cartItem.item.quantity} ${cartItem.item.unit})! يمنع السحب.",
                                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
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
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
                    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text("إجمالي الفاتورة", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                Text("بحساب العميل ($clientBaseCurrency)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(
                                "${FormatUtils.formatAmount(subtotalInClientCurrency)} $clientBaseCurrency",
                                fontSize = 19.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        
                        Divider()
                        
                        Text("المبلغ المدفوع الآن:", fontWeight = FontWeight.Bold)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { paidAmountStr = subtotalInClientCurrency.toString() },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                            ) {
                                Text("دفع كامل", maxLines = 1, softWrap = false, fontSize = 12.sp)
                            }
                            OutlinedButton(
                                onClick = { paidAmountStr = (subtotalInClientCurrency / 2).toString() },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                            ) {
                                Text("دفع جزئي", maxLines = 1, softWrap = false, fontSize = 12.sp)
                            }
                            OutlinedButton(
                                onClick = { paidAmountStr = "0" },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                            ) {
                                Text("بدون دفع", maxLines = 1, softWrap = false, fontSize = 12.sp)
                            }
                        }
                        
                        OutlinedTextField(
                            value = paidAmountStr,
                            onValueChange = { paidAmountStr = it },
                            label = { Text("المبلغ المدفوع ($clientBaseCurrency)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.scrollToTopOnFocus().scrollToTopOnFocus().fillMaxWidth(),
                            singleLine = true,
                            maxLines = 1,
                            supportingText = {
                                if (paidAmount > 0) {
                                    Text(
                                        text = "المبلغ: ${FormatUtils.formatAmount(paidAmount)}\u00A0$clientBaseCurrency",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        )

                        if (paidAmount > 0) {
                            OutlinedTextField(
                                value = voucherNumberStr,
                                onValueChange = { voucherNumberStr = it },
                                label = { Text("رقم سند السداد/القبض (يدوي)") },
                                placeholder = { Text("أدخل رقم السند يدوياً...") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                maxLines = 1
                            )
                        }
                        
                        val oldBalance = selectedClient!!.balance
                        val remainingInClientCurr = (subtotalInClientCurrency - paidAmount).coerceAtLeast(0.0)
                        val newBalance = oldBalance + remainingInClientCurr - (if (paidAmount > subtotalInClientCurrency) (paidAmount - subtotalInClientCurrency) else 0.0)
                        
                        Column(modifier = Modifier.fillMaxWidth().background(if (remainingInClientCurr > 0) Color(0xFFFFF3E0) else Color(0xFFE8F5E9), RoundedCornerShape(8.dp)).padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("الرصيد السابق:", maxLines = 1, softWrap = false)
                                Text("${FormatUtils.formatAmount(oldBalance)}\u00A0$clientBaseCurrency", fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                            }
                            if (remainingInClientCurr > 0) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("المتبقي من الفاتورة:", maxLines = 1, softWrap = false)
                                    Text("+${FormatUtils.formatAmount(remainingInClientCurr)}\u00A0$clientBaseCurrency", fontWeight = FontWeight.Bold, color = Color(0xFFD32F2F), maxLines = 1, softWrap = false)
                                }
                            } else if (paidAmount > subtotalInClientCurrency) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("فائض المدفوع:", maxLines = 1, softWrap = false)
                                    Text("+${FormatUtils.formatAmount(paidAmount - subtotalInClientCurrency)}\u00A0$clientBaseCurrency", fontWeight = FontWeight.Bold, color = Color(0xFF388E3C), maxLines = 1, softWrap = false)
                                }
                            }
                            Divider()
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("الرصيد الجديد للعميل:", maxLines = 1, softWrap = false)
                                Text("${FormatUtils.formatAmount(newBalance)}\u00A0$clientBaseCurrency", fontWeight = FontWeight.ExtraBold, maxLines = 1, softWrap = false)
                            }
                        }
                        
                        Button(
                            onClick = {
                                if (cart.isNotEmpty() && selectedClient != null) {
                                    // 1. فحص توفر المخزون ومنع السحب إذا كانت الكمية المطلوبة تفوق المتوفر
                                    val overStockItem = cart.firstOrNull { it.quantity > it.item.quantity }
                                    if (overStockItem != null) {
                                        stockWarningDialogMessage = "لا يمكن إتمام عملية السحب وحفظ الفاتورة!\n\n" +
                                            "الصنف: ${overStockItem.item.name}\n" +
                                            "الكمية المطلوبة: ${overStockItem.quantity} ${overStockItem.item.unit}\n" +
                                            "الكمية المتوفرة في المخزن: ${overStockItem.item.quantity} ${overStockItem.item.unit}\n\n" +
                                            "السبب: الكمية المطلوبة مرتفعة لهذا الصنف وتتجاوز رصيد المخزن الحالي (${overStockItem.item.quantity} ${overStockItem.item.unit}). يرجى تعديل الكمية للسماح بالسحب."
                                        showStockWarningDialog = true
                                        return@Button
                                    }

                                    // 2. فحص أن الكمية ليست صفر
                                    val zeroQtyItem = cart.firstOrNull { it.quantity <= 0 }
                                    if (zeroQtyItem != null) {
                                        Toast.makeText(context, "يرجى تحديد كمية صحيحة أكبر من الصفر للصنف '${zeroQtyItem.item.name}'", Toast.LENGTH_LONG).show()
                                        return@Button
                                    }

                                    if (!settings.isAutoNumberingEnabled && invoiceNumber.trim().isBlank()) {
                                        invoiceNumberError = "يرجى إدخال رقم الفاتورة يدوياً"
                                        Toast.makeText(context, "يرجى إدخال رقم الفاتورة يدوياً", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    viewModel.setInvoiceClient(selectedClient!!)
                                    viewModel.saveDetailedInvoice(
                                        paidAmount = paidAmount,
                                        currency = clientBaseCurrency,
                                        customInvoiceNumber = invoiceNumber.trim().ifBlank { null },
                                        customDateMillis = selectedDateTimeMillis,
                                        paymentVoucherNumber = voucherNumberStr.trim().ifBlank { null },
                                        onSuccess = { invoiceId ->
                                            savedInvoiceId = invoiceId
                                            showSuccessDialog = true
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
                            Text("حفظ الفاتورة", fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiSelectItemDialog(
    itemsList: List<Item>,
    currency: String,
    cart: List<com.example.ui.viewmodel.CartItem>,
    onDismiss: () -> Unit,
    onItemAdd: (Item) -> Unit,
    onItemRemove: (Item) -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    
    val filteredItems = itemsList.filter {
        searchQuery.isBlank() || it.name.lowercase().contains(searchQuery.lowercase()) || it.barcode.contains(searchQuery)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text("إضافة أصناف للفاتورة") },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "إغلاق")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    placeholder = { Text("ابحث عن صنف بالاسم أو الباركود...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                if (cart.isNotEmpty()) {
                    Text(
                        text = "الأصناف المضافة (${cart.size})",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    // Show a quick summary of added items
                    androidx.compose.foundation.lazy.LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(cart) { cartItem ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(cartItem.item.name, color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 12.sp)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        Icons.Default.Close, 
                                        contentDescription = "إزالة", 
                                        modifier = Modifier.size(16.dp).clickable { onItemRemove(cartItem.item) },
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Divider()
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize().weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredItems, key = { it.id }) { item ->
                        val isAdded = cart.any { it.item.id == item.id }
                        val isOutOfStock = item.quantity <= 0
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable {
                                if (isAdded) {
                                    onItemRemove(item)
                                } else if (isOutOfStock) {
                                    Toast.makeText(context, "الصنف '${item.name}' غير متوفر بالمخزن (الرصيد 0)", Toast.LENGTH_SHORT).show()
                                } else {
                                    onItemAdd(item)
                                }
                            },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isAdded) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.name, fontWeight = FontWeight.Bold)
                                    val itemCurr = FormatUtils.formatCurrencyCompact(item.sellingCurrency.ifBlank { "الريال اليمني" })
                                    if (isOutOfStock) {
                                        Text(
                                            "نفد المخزون (0) | السعر: ${FormatUtils.formatAmount(item.sellingPrice)} $itemCurr",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    } else {
                                        Text("المخزون: ${item.quantity} ${item.unit} | السعر: ${FormatUtils.formatAmount(item.sellingPrice)} $itemCurr", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                if (isAdded) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = "مضاف", tint = MaterialTheme.colorScheme.primary)
                                } else if (isOutOfStock) {
                                    Text(
                                        "غير متوفر",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                } else {
                                    Icon(Icons.Default.AddCircleOutline, contentDescription = "إضافة", tint = Color.Gray)
                                }
                            }
                        }
                    }
                }

                // Bottom bar
                Surface(
                    shadowElevation = 8.dp,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth().padding(16.dp).height(50.dp)
                    ) {
                        Text("تم (${cart.size} أصناف)", fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, softWrap = false)
                    }
                }
            }
        }
    }
}
