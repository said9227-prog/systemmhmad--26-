package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.ExchangeRate
import com.example.data.model.Invoice
import com.example.data.model.InvoiceItem
import com.example.ui.viewmodel.AppViewModel
import com.example.util.FormatUtils
import com.example.util.MonetaryUtils
import com.example.util.ShareManager
import com.example.util.WhatsAppHelper
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceDetailsScreen(
    viewModel: AppViewModel,
    invoiceId: Int,
    onNavigateBack: () -> Unit
) {
    BackHandler {
        onNavigateBack()
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by viewModel.storeSettings.collectAsState()
    val clientsList by viewModel.clients.collectAsState()
    val exchangeRates by viewModel.exchangeRates.collectAsState()

    var invoice by remember { mutableStateOf<Invoice?>(null) }
    var invoiceItems by remember { mutableStateOf<List<InvoiceItem>>(emptyList()) }

    var showSmsConfirmDialog by remember { mutableStateOf(false) }
    var smsClientPhone by remember { mutableStateOf("") }
    var smsClientName by remember { mutableStateOf("") }
    var smsMessageContent by remember { mutableStateOf("") }

    var selectedDisplayCurrency by remember(settings) {
        mutableStateOf(if (settings.enableInvoiceCurrencyConverter) settings.defaultInvoiceDisplayCurrency else "الكل")
    }

    // Fetch invoice on startup
    LaunchedEffect(invoiceId) {
        val inv = viewModel.getInvoiceById(invoiceId)
        if (inv != null) {
            invoice = inv
            viewModel.getInvoiceItemsFlow(invoiceId).collect {
                invoiceItems = it
            }
        }
    }

    if (invoice == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val currentInvoice = invoice!!
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    val formattedDate = dateFormat.format(Date(currentInvoice.date))

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
    ) {
        // Actions Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "رجوع")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                // Unified Share action (PDF + Summary)
                IconButton(
                    onClick = {
                        val client = clientsList.find { it.id == currentInvoice.clientId }
                        val chosenName = when (selectedDisplayCurrency) {
                            "SAR" -> "الريال السعودي (SAR)"
                            "USD" -> "الدولار الأمريكي (USD)"
                            "YER" -> "الريال اليمني (YER)"
                            else -> currentInvoice.currency
                        }
                        Toast.makeText(context, "📄 جاري مشاركة الفاتورة بـ ($chosenName) فقط", Toast.LENGTH_SHORT).show()
                        scope.launch {
                            ShareManager.shareInvoice(
                                context = context,
                                invoice = currentInvoice,
                                items = invoiceItems,
                                settings = settings,
                                client = client,
                                shareAsPdf = true,
                                exchangeRates = exchangeRates,
                                targetCurrency = selectedDisplayCurrency
                            )
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "مشاركة الفاتورة",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                // Share as PDF directly
                IconButton(
                    onClick = {
                        val client = clientsList.find { it.id == currentInvoice.clientId }
                        val chosenName = when (selectedDisplayCurrency) {
                            "SAR" -> "الريال السعودي (SAR)"
                            "USD" -> "الدولار الأمريكي (USD)"
                            "YER" -> "الريال اليمني (YER)"
                            else -> currentInvoice.currency
                        }
                        Toast.makeText(context, "📄 جاري تصدير ومشاركة Invoice.pdf بـ ($chosenName) فقط", Toast.LENGTH_SHORT).show()
                        scope.launch {
                            ShareManager.shareInvoice(
                                context = context,
                                invoice = currentInvoice,
                                items = invoiceItems,
                                settings = settings,
                                client = client,
                                shareAsPdf = true,
                                exchangeRates = exchangeRates,
                                targetCurrency = selectedDisplayCurrency
                            )
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.PictureAsPdf,
                        contentDescription = "مشاركة PDF",
                        tint = Color(0xFFDC2626)
                    )
                }

                // Print action
                IconButton(
                    onClick = {
                        Toast.makeText(context, "جاري إرسال الفاتورة لبروتوكول الطباعة المباشر...", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Icon(Icons.Default.Print, contentDescription = "طباعة الفاتورة")
                }

                // Send WhatsApp action
                IconButton(
                    onClick = {
                        val client = clientsList.find { it.id == currentInvoice.clientId }
                        val waMsg = WhatsAppHelper.formatWhatsAppInvoiceMessage(
                            invoice = currentInvoice,
                            items = invoiceItems,
                            storeName = settings.storeName,
                            storePhone = settings.storePhone,
                            storeAddress = settings.storeAddress,
                            currency = settings.currency,
                            clientBalance = client?.balance,
                            clientCurrency = client?.currency,
                            showVatAndSubtotal = settings.showVatAndSubtotal,
                            rates = exchangeRates,
                            targetCurrency = selectedDisplayCurrency
                        )
                        WhatsAppHelper.sendWhatsAppMessage(context, client?.phone, waMsg)
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Chat,
                        contentDescription = "مشاركة عبر واتساب",
                        tint = Color(0xFF25D366)
                    )
                }

                // Send SMS action
                IconButton(
                    onClick = {
                        val client = clientsList.find { it.id == currentInvoice.clientId }
                        if (client != null) {
                            smsClientPhone = client.phone
                            smsClientName = client.name
                            val invCurr = MonetaryUtils.normalizeCurrency(currentInvoice.currency.ifBlank { settings.currency })
                            val clientCurr = MonetaryUtils.normalizeCurrency(client.currency.ifBlank { settings.currency })

                            val detailsText = if (currentInvoice.isQuickInvoice) {
                                currentInvoice.notes?.ifBlank { "شراء بضاعة" } ?: "شراء بضاعة"
                            } else {
                                val summary = invoiceItems.joinToString(", ") { "${it.itemName} (x${it.quantity})" }
                                if (summary.length > 80) summary.take(77) + "..." else summary
                            }

                            val isSpecific = selectedDisplayCurrency != "الكل" && selectedDisplayCurrency.isNotBlank()
                            val chosenCurr = when (selectedDisplayCurrency) {
                                "SAR" -> MonetaryUtils.CURRENCY_SAR
                                "USD" -> MonetaryUtils.CURRENCY_USD
                                "YER" -> MonetaryUtils.CURRENCY_YER
                                else -> invCurr
                            }
                            val currSym = when (chosenCurr) {
                                MonetaryUtils.CURRENCY_SAR -> "ر.س"
                                MonetaryUtils.CURRENCY_USD -> "$"
                                else -> "ر.ي"
                            }

                            val totChosen = MonetaryUtils.convert(currentInvoice.totalAmount, invCurr, chosenCurr, exchangeRates).convertedAmount
                            val balChosen = MonetaryUtils.convert(kotlin.math.abs(client.balance), clientCurr, chosenCurr, exchangeRates).convertedAmount

                            smsMessageContent = if (isSpecific) {
                                "عميلنا العزيز ${client.name}، تم إصدار فاتورة مبيعات #${currentInvoice.invoiceNumber}\n" +
                                "• الإجمالي: ${FormatUtils.formatAmount(totChosen)} $currSym\n" +
                                "• الأصناف: $detailsText\n" +
                                "• الرصيد المستحق: ${FormatUtils.formatAmount(balChosen)} $currSym\n" +
                                "شكراً لتعاملكم معنا - ${settings.storeName}"
                            } else {
                                val totSar = MonetaryUtils.convert(currentInvoice.totalAmount, invCurr, MonetaryUtils.CURRENCY_SAR, exchangeRates).convertedAmount
                                val totYer = MonetaryUtils.convert(currentInvoice.totalAmount, invCurr, MonetaryUtils.CURRENCY_YER, exchangeRates).convertedAmount
                                val totUsd = MonetaryUtils.convert(currentInvoice.totalAmount, invCurr, MonetaryUtils.CURRENCY_USD, exchangeRates).convertedAmount

                                val balSar = MonetaryUtils.convert(kotlin.math.abs(client.balance), clientCurr, MonetaryUtils.CURRENCY_SAR, exchangeRates).convertedAmount
                                val balYer = MonetaryUtils.convert(kotlin.math.abs(client.balance), clientCurr, MonetaryUtils.CURRENCY_YER, exchangeRates).convertedAmount
                                val balUsd = MonetaryUtils.convert(kotlin.math.abs(client.balance), clientCurr, MonetaryUtils.CURRENCY_USD, exchangeRates).convertedAmount

                                "عميلنا العزيز ${client.name}، تم إصدار فاتورة مبيعات #${currentInvoice.invoiceNumber}\n" +
                                "• الإجمالي: ${FormatUtils.formatAmount(totSar)} ر.س | ${FormatUtils.formatAmount(totYer)} ر.ي | $${FormatUtils.formatAmount(totUsd)}\n" +
                                "• الأصناف: $detailsText\n" +
                                "• الرصيد المستحق: ${FormatUtils.formatAmount(balSar)} ر.س | ${FormatUtils.formatAmount(balYer)} ر.ي | $${FormatUtils.formatAmount(balUsd)}\n" +
                                "شكراً لتعاملكم معنا - ${settings.storeName}"
                            }
                            showSmsConfirmDialog = true
                        } else {
                            Toast.makeText(context, "فشل تحديد العميل للفاتورة!", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Sms,
                        contentDescription = "إرسال كـ SMS",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Printable Bill Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .shadow(2.dp, RoundedCornerShape(10.dp)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(10.dp)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Store/Merchant Header Info
                item {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = settings.storeName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )
                        if (settings.storePhone.isNotEmpty()) {
                            Text(
                                text = "الهاتف: ${settings.storePhone}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (settings.storeAddress.isNotEmpty()) {
                            Text(
                                text = settings.storeAddress,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Divider()
                    }
                }

                // Customer and Date Metadata
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("رقم الفاتورة:", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                            Text(currentInvoice.invoiceNumber, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("العميل:", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                            Text(currentInvoice.clientName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("التاريخ والوقت:", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                            Text(formattedDate, fontSize = 13.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("النوع:", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                            Text(if (currentInvoice.isQuickInvoice) "فاتورة سريعة ⚡" else "فاتورة مفصلة بالأصناف 📦", fontSize = 13.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("الحالة:", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (currentInvoice.isDraft) Color(0xFFFEF3C7) else Color(0xFFECFDF5)
                                ),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = if (currentInvoice.isDraft) "مسودة" else "نهائية مكتملة",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (currentInvoice.isDraft) Color(0xFFD97706) else Color(0xFF059669),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Divider()
                    }
                }

                // Credit Limit Override Notice if applicable
                if (currentInvoice.isCreditOverride) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFCDD2))
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = Color(0xFFD32F2F), modifier = Modifier.size(18.dp))
                                    Text("تم إصدار الفاتورة بتجاوز استثنائي للحد الائتماني", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFFD32F2F))
                                }
                                Text("المسؤول المصرح: ${currentInvoice.overrideAuthorizer ?: "الإدارة"}", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                Text("سبب التجاوز: ${currentInvoice.overrideReason ?: "غير محدد"}", fontSize = 11.sp, color = Color.DarkGray)
                                if (currentInvoice.overrideAmount > 0) {
                                    Text("مقدار التجاوز: +${FormatUtils.formatAmount(currentInvoice.overrideAmount)} ${currentInvoice.currency.ifBlank { settings.currency }}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB71C1C))
                                }
                            }
                        }
                    }
                }

                // Billing Content Table
                if (currentInvoice.isQuickInvoice) {
                    item {
                        Column {
                            Text("وصف الخدمات المذكورة:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(4.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            ) {
                                Text(
                                    text = currentInvoice.description ?: "لا يوجد وصف محدد.",
                                    modifier = Modifier.padding(8.dp),
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                } else {
                    // Currency Converter Selector Banner
                    if (settings.enableInvoiceCurrencyConverter) {
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Icon(Icons.Default.CurrencyExchange, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                            Text(
                                                text = "حاسبة وعرض الفاتورة بالعملة المختارة:",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        val currLabel = when (selectedDisplayCurrency) {
                                            "SAR" -> "الريال السعودي (SAR)"
                                            "USD" -> "الدولار الأمريكي (USD)"
                                            "YER" -> "الريال اليمني (YER)"
                                            else -> "عرض شامل (ثلاثي)"
                                        }
                                        Text("💱 $currLabel", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        val chips = listOf(
                                            "الكل" to "🌐 شامل",
                                            "YER" to "🇾🇪 يمني",
                                            "SAR" to "🇸🇦 سعودي",
                                            "USD" to "🇺🇸 دولار"
                                        )
                                        chips.forEach { (code, label) ->
                                            val isSel = selectedDisplayCurrency == code
                                            FilterChip(
                                                selected = isSel,
                                                onClick = { selectedDisplayCurrency = code },
                                                label = { Text(label, fontSize = 11.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal) },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Itemized Table Header
                    item {
                        val priceHeaderTitle = when (selectedDisplayCurrency) {
                            "SAR" -> "سعر الوحدة (ر.س)"
                            "USD" -> "سعر الوحدة ($)"
                            "YER" -> "سعر الوحدة (ر.ي)"
                            else -> "سعر الوحدة"
                        }
                        val totalHeaderTitle = when (selectedDisplayCurrency) {
                            "SAR" -> "الإجمالي (ر.س)"
                            "USD" -> "الإجمالي ($)"
                            "YER" -> "الإجمالي (ر.ي)"
                            else -> "الإجمالي"
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                                .padding(vertical = 6.dp, horizontal = 4.dp)
                        ) {
                            Text("#", modifier = Modifier.width(28.dp), fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = TextAlign.Center)
                            Text("اسم الصنف والصرف", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            Text("الكمية", modifier = Modifier.width(42.dp), fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = TextAlign.Center)
                            Text(priceHeaderTitle, modifier = Modifier.width(82.dp), fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = TextAlign.End)
                            Text(totalHeaderTitle, modifier = Modifier.width(92.dp), fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = TextAlign.End)
                        }
                    }

                    // Table items with detailed SAR, YER and USD breakdown
                    itemsIndexed(invoiceItems) { index, item ->
                        val rawCurr = item.currency.ifBlank { currentInvoice.currency }
                        val itemCurr = MonetaryUtils.normalizeCurrency(rawCurr)
                        val qty = item.quantity
                        val unitPrice = item.unitPrice
                        val itemTotalPrice = if (item.totalPrice > 0.001) item.totalPrice else unitPrice * qty

                        val pSar = MonetaryUtils.convert(unitPrice, itemCurr, MonetaryUtils.CURRENCY_SAR, exchangeRates).convertedAmount
                        val pYer = MonetaryUtils.convert(unitPrice, itemCurr, MonetaryUtils.CURRENCY_YER, exchangeRates).convertedAmount
                        val pUsd = MonetaryUtils.convert(unitPrice, itemCurr, MonetaryUtils.CURRENCY_USD, exchangeRates).convertedAmount

                        val totSar = MonetaryUtils.convert(itemTotalPrice, itemCurr, MonetaryUtils.CURRENCY_SAR, exchangeRates).convertedAmount
                        val totYer = MonetaryUtils.convert(itemTotalPrice, itemCurr, MonetaryUtils.CURRENCY_YER, exchangeRates).convertedAmount
                        val totUsd = MonetaryUtils.convert(itemTotalPrice, itemCurr, MonetaryUtils.CURRENCY_USD, exchangeRates).convertedAmount

                        val sarToYer = MonetaryUtils.convert(1.0, MonetaryUtils.CURRENCY_SAR, MonetaryUtils.CURRENCY_YER, exchangeRates).convertedAmount
                        val usdToYer = MonetaryUtils.convert(1.0, MonetaryUtils.CURRENCY_USD, MonetaryUtils.CURRENCY_YER, exchangeRates).convertedAmount

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp, horizontal = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("${index + 1}", modifier = Modifier.width(28.dp), fontSize = 12.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.itemName, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    if (!settings.enableUnclutteredInvoiceMode) {
                                        when (selectedDisplayCurrency) {
                                            "SAR" -> Text("💱 1 ر.س = ${FormatUtils.formatAmount(sarToYer)} ر.ي", fontSize = 9.sp, color = Color.Gray)
                                            "USD" -> Text("💱 1 $ = ${FormatUtils.formatAmount(usdToYer)} ر.ي", fontSize = 9.sp, color = Color.Gray)
                                            "YER" -> Text("💱 1 ر.س = ${FormatUtils.formatAmount(sarToYer)} ر.ي", fontSize = 9.sp, color = Color.Gray)
                                            else -> Text("💱 1 ر.س = ${FormatUtils.formatAmount(sarToYer)} ر.ي | 1 $ = ${FormatUtils.formatAmount(usdToYer)} ر.ي", fontSize = 9.sp, color = Color.Gray)
                                        }
                                    }
                                }
                                Text("$qty", modifier = Modifier.width(42.dp), fontSize = 12.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                
                                when (selectedDisplayCurrency) {
                                    "SAR" -> {
                                        Column(modifier = Modifier.width(82.dp), horizontalAlignment = Alignment.End) {
                                            Text("${FormatUtils.formatAmount(pSar)} ر.س", fontSize = 11.sp, color = Color(0xFF047857), fontWeight = FontWeight.Bold)
                                            if (!settings.enableUnclutteredInvoiceMode) {
                                                Text("${FormatUtils.formatAmount(pYer)} ر.ي", fontSize = 9.sp, color = Color.Gray)
                                            }
                                        }
                                        Column(modifier = Modifier.width(92.dp), horizontalAlignment = Alignment.End) {
                                            Text("${FormatUtils.formatAmount(totSar)} ر.س", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                            if (!settings.enableUnclutteredInvoiceMode) {
                                                Text("${FormatUtils.formatAmount(totYer)} ر.ي", fontSize = 9.sp, color = Color.Gray)
                                            }
                                        }
                                    }
                                    "USD" -> {
                                        Column(modifier = Modifier.width(82.dp), horizontalAlignment = Alignment.End) {
                                            Text("$${FormatUtils.formatAmount(pUsd)}", fontSize = 11.sp, color = Color(0xFF047857), fontWeight = FontWeight.Bold)
                                            if (!settings.enableUnclutteredInvoiceMode) {
                                                Text("${FormatUtils.formatAmount(pYer)} ر.ي", fontSize = 9.sp, color = Color.Gray)
                                            }
                                        }
                                        Column(modifier = Modifier.width(92.dp), horizontalAlignment = Alignment.End) {
                                            Text("$${FormatUtils.formatAmount(totUsd)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                            if (!settings.enableUnclutteredInvoiceMode) {
                                                Text("${FormatUtils.formatAmount(totYer)} ر.ي", fontSize = 9.sp, color = Color.Gray)
                                            }
                                        }
                                    }
                                    "YER" -> {
                                        Column(modifier = Modifier.width(82.dp), horizontalAlignment = Alignment.End) {
                                            Text("${FormatUtils.formatAmount(pYer)} ر.ي", fontSize = 11.sp, color = Color(0xFF047857), fontWeight = FontWeight.Bold)
                                            if (!settings.enableUnclutteredInvoiceMode) {
                                                Text("${FormatUtils.formatAmount(pSar)} ر.س", fontSize = 9.sp, color = Color.Gray)
                                            }
                                        }
                                        Column(modifier = Modifier.width(92.dp), horizontalAlignment = Alignment.End) {
                                            Text("${FormatUtils.formatAmount(totYer)} ر.ي", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                            if (!settings.enableUnclutteredInvoiceMode) {
                                                Text("${FormatUtils.formatAmount(totSar)} ر.س", fontSize = 9.sp, color = Color.Gray)
                                            }
                                        }
                                    }
                                    else -> {
                                        Column(modifier = Modifier.width(82.dp), horizontalAlignment = Alignment.End) {
                                            Text("${FormatUtils.formatAmount(pSar)} ر.س", fontSize = 10.sp, color = Color(0xFF047857), fontWeight = FontWeight.Bold)
                                            if (!settings.enableUnclutteredInvoiceMode) {
                                                Text("${FormatUtils.formatAmount(pYer)} ر.ي", fontSize = 9.sp, color = Color.DarkGray)
                                                Text("$${FormatUtils.formatAmount(pUsd)}", fontSize = 9.sp, color = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                        Column(modifier = Modifier.width(92.dp), horizontalAlignment = Alignment.End) {
                                            Text("${FormatUtils.formatAmount(totSar)} ر.س", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                            if (!settings.enableUnclutteredInvoiceMode) {
                                                Text("${FormatUtils.formatAmount(totYer)} ر.ي", fontSize = 9.sp, color = Color.DarkGray)
                                                Text("$${FormatUtils.formatAmount(totUsd)}", fontSize = 9.sp, color = Color(0xFF047857), fontWeight = FontWeight.SemiBold)
                                            }
                                        }
                                    }
                                }
                            }
                            Divider(modifier = Modifier.padding(top = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        }
                    }
                }

                // Billing Calculations Total panel
                item {
                    val invCurr = MonetaryUtils.normalizeCurrency(currentInvoice.currency.ifBlank { settings.currency })
                    val invTotalSar = MonetaryUtils.convert(currentInvoice.totalAmount, invCurr, MonetaryUtils.CURRENCY_SAR, exchangeRates).convertedAmount
                    val invTotalYer = MonetaryUtils.convert(currentInvoice.totalAmount, invCurr, MonetaryUtils.CURRENCY_YER, exchangeRates).convertedAmount
                    val invTotalUsd = MonetaryUtils.convert(currentInvoice.totalAmount, invCurr, MonetaryUtils.CURRENCY_USD, exchangeRates).convertedAmount

                    val paidSar = MonetaryUtils.convert(currentInvoice.paidAmount, invCurr, MonetaryUtils.CURRENCY_SAR, exchangeRates).convertedAmount
                    val paidYer = MonetaryUtils.convert(currentInvoice.paidAmount, invCurr, MonetaryUtils.CURRENCY_YER, exchangeRates).convertedAmount
                    val paidUsd = MonetaryUtils.convert(currentInvoice.paidAmount, invCurr, MonetaryUtils.CURRENCY_USD, exchangeRates).convertedAmount

                    val remSar = (invTotalSar - paidSar).coerceAtLeast(0.0)
                    val remYer = (invTotalYer - paidYer).coerceAtLeast(0.0)
                    val remUsd = (invTotalUsd - paidUsd).coerceAtLeast(0.0)

                    val sarToYer = MonetaryUtils.convert(1.0, MonetaryUtils.CURRENCY_SAR, MonetaryUtils.CURRENCY_YER, exchangeRates).convertedAmount
                    val usdToYer = MonetaryUtils.convert(1.0, MonetaryUtils.CURRENCY_USD, MonetaryUtils.CURRENCY_YER, exchangeRates).convertedAmount

                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        if (!currentInvoice.isQuickInvoice) {
                            val subtotalSar = invoiceItems.sumOf { 
                                val rawC = it.currency.ifBlank { currentInvoice.currency }
                                val tot = if (it.totalPrice > 0.001) it.totalPrice else it.unitPrice * it.quantity
                                MonetaryUtils.convert(tot, MonetaryUtils.normalizeCurrency(rawC), MonetaryUtils.CURRENCY_SAR, exchangeRates).convertedAmount
                            }
                            val subtotalYer = invoiceItems.sumOf { 
                                val rawC = it.currency.ifBlank { currentInvoice.currency }
                                val tot = if (it.totalPrice > 0.001) it.totalPrice else it.unitPrice * it.quantity
                                MonetaryUtils.convert(tot, MonetaryUtils.normalizeCurrency(rawC), MonetaryUtils.CURRENCY_YER, exchangeRates).convertedAmount
                            }
                            val subtotalUsd = invoiceItems.sumOf { 
                                val rawC = it.currency.ifBlank { currentInvoice.currency }
                                val tot = if (it.totalPrice > 0.001) it.totalPrice else it.unitPrice * it.quantity
                                MonetaryUtils.convert(tot, MonetaryUtils.normalizeCurrency(rawC), MonetaryUtils.CURRENCY_USD, exchangeRates).convertedAmount
                            }

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("المجموع الفرعي:", fontSize = 12.sp)
                                when (selectedDisplayCurrency) {
                                    "SAR" -> Text("${FormatUtils.formatAmount(subtotalSar)} ر.س", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    "USD" -> Text("$${FormatUtils.formatAmount(subtotalUsd)}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    "YER" -> Text("${FormatUtils.formatAmount(subtotalYer)} ر.ي", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    else -> Text("${FormatUtils.formatAmount(subtotalSar)} ر.س | ${FormatUtils.formatAmount(subtotalYer)} ر.ي | $${FormatUtils.formatAmount(subtotalUsd)}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            if (currentInvoice.discount > 0) {
                                val discSar = MonetaryUtils.convert(currentInvoice.discount, invCurr, MonetaryUtils.CURRENCY_SAR, exchangeRates).convertedAmount
                                val discYer = MonetaryUtils.convert(currentInvoice.discount, invCurr, MonetaryUtils.CURRENCY_YER, exchangeRates).convertedAmount
                                val discUsd = MonetaryUtils.convert(currentInvoice.discount, invCurr, MonetaryUtils.CURRENCY_USD, exchangeRates).convertedAmount
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("الخصم المالي الممنوح:", fontSize = 12.sp, color = Color(0xFFDC2626))
                                    when (selectedDisplayCurrency) {
                                        "SAR" -> Text("-${FormatUtils.formatAmount(discSar)} ر.س", fontSize = 12.sp, color = Color(0xFFDC2626), fontWeight = FontWeight.Bold)
                                        "USD" -> Text("-$${FormatUtils.formatAmount(discUsd)}", fontSize = 12.sp, color = Color(0xFFDC2626), fontWeight = FontWeight.Bold)
                                        "YER" -> Text("-${FormatUtils.formatAmount(discYer)} ر.ي", fontSize = 12.sp, color = Color(0xFFDC2626), fontWeight = FontWeight.Bold)
                                        else -> Text("-${FormatUtils.formatAmount(discSar)} ر.س | -${FormatUtils.formatAmount(discYer)} ر.ي | -$${FormatUtils.formatAmount(discUsd)}", fontSize = 11.sp, color = Color(0xFFDC2626), fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            Divider(color = Color.LightGray.copy(alpha = 0.5f))
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("الإجمالي الكلي النهائي:", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                            Column(horizontalAlignment = Alignment.End) {
                                when (selectedDisplayCurrency) {
                                    "SAR" -> {
                                        Text("${FormatUtils.formatAmount(invTotalSar)} ريال سعودي (SAR)", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
                                        Text("يعادل: ${FormatUtils.formatAmount(invTotalYer)} ر.ي | $${FormatUtils.formatAmount(invTotalUsd)}", fontSize = 11.sp, color = Color.Gray)
                                    }
                                    "USD" -> {
                                        Text("$${FormatUtils.formatAmount(invTotalUsd)} دولار أمريكي (USD)", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
                                        Text("يعادل: ${FormatUtils.formatAmount(invTotalYer)} ر.ي | ${FormatUtils.formatAmount(invTotalSar)} ر.س", fontSize = 11.sp, color = Color.Gray)
                                    }
                                    "YER" -> {
                                        Text("${FormatUtils.formatAmount(invTotalYer)} ريال يمني (YER)", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
                                        Text("يعادل: ${FormatUtils.formatAmount(invTotalSar)} ر.س | $${FormatUtils.formatAmount(invTotalUsd)}", fontSize = 11.sp, color = Color.Gray)
                                    }
                                    else -> {
                                        Text("${FormatUtils.formatAmount(invTotalSar)} ر.س  |  ${FormatUtils.formatAmount(invTotalYer)} ر.ي", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                                        Text("$${FormatUtils.formatAmount(invTotalUsd)} دولار أمريكي", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Color(0xFF047857))
                                    }
                                }
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("المبلغ المدفوع:", fontSize = 12.sp)
                            when (selectedDisplayCurrency) {
                                "SAR" -> Text("${FormatUtils.formatAmount(paidSar)} ر.س", fontSize = 12.sp, color = Color(0xFF059669), fontWeight = FontWeight.SemiBold)
                                "USD" -> Text("$${FormatUtils.formatAmount(paidUsd)}", fontSize = 12.sp, color = Color(0xFF059669), fontWeight = FontWeight.SemiBold)
                                "YER" -> Text("${FormatUtils.formatAmount(paidYer)} ر.ي", fontSize = 12.sp, color = Color(0xFF059669), fontWeight = FontWeight.SemiBold)
                                else -> Text("${FormatUtils.formatAmount(paidSar)} ر.س | ${FormatUtils.formatAmount(paidYer)} ر.ي | $${FormatUtils.formatAmount(paidUsd)}", fontSize = 11.sp, color = Color(0xFF059669), fontWeight = FontWeight.SemiBold)
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("المبلغ المتبقي:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            val hasRem = remSar > 0.001 || remYer > 0.001 || remUsd > 0.001
                            val remText = if (!hasRem) "خالص بالكامل" else when (selectedDisplayCurrency) {
                                "SAR" -> "${FormatUtils.formatAmount(remSar)} ر.س"
                                "USD" -> "$${FormatUtils.formatAmount(remUsd)}"
                                "YER" -> "${FormatUtils.formatAmount(remYer)} ر.ي"
                                else -> "${FormatUtils.formatAmount(remSar)} ر.س | ${FormatUtils.formatAmount(remYer)} ر.ي | $${FormatUtils.formatAmount(remUsd)}"
                            }
                            Text(
                                text = remText,
                                fontSize = 12.sp,
                                color = if (hasRem) Color(0xFFDC2626) else Color(0xFF059669),
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = "💱 صرف النظام المعتمد: 1 ر.س = ${FormatUtils.formatAmount(sarToYer)} ر.ي | 1 $ = ${FormatUtils.formatAmount(usdToYer)} ر.ي",
                            fontSize = 10.sp,
                            color = Color.Gray
                        )
                    }
                }

                // Invoice notes block
                if (!currentInvoice.notes.isNullOrBlank()) {
                    item {
                        Column {
                            Text("شروط وأحكام الفاتورة / الملاحظات:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(currentInvoice.notes ?: "", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Prominent Unified Share Action Button
        val client = clientsList.find { it.id == currentInvoice.clientId }
        val (shareBtnText, chosenCurrLabel) = when (selectedDisplayCurrency) {
            "SAR" -> "📤 مشاركة الفاتورة PDF (بالريال السعودي فقط)" to "الريال السعودي (SAR)"
            "USD" -> "📤 مشاركة الفاتورة PDF (بالدولار الأمريكي فقط)" to "الدولار الأمريكي (USD)"
            "YER" -> "📤 مشاركة الفاتورة PDF (بالريال اليمني فقط)" to "الريال اليمني (YER)"
            else -> "📤 مشاركة الفاتورة PDF (بالعملة الرسمية فقط)" to currentInvoice.currency
        }
        Button(
            onClick = {
                Toast.makeText(context, "📄 جاري إعداد ومشاركة Invoice.pdf بـ ($chosenCurrLabel) فقط", Toast.LENGTH_SHORT).show()
                scope.launch {
                    ShareManager.shareInvoice(
                        context = context,
                        invoice = currentInvoice,
                        items = invoiceItems,
                        settings = settings,
                        client = client,
                        shareAsPdf = true,
                        exchangeRates = exchangeRates,
                        targetCurrency = selectedDisplayCurrency
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            shape = RoundedCornerShape(10.dp),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "مشاركة",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = shareBtnText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }

    // SMS Confirmation Dialog with Permission request flow
    if (showSmsConfirmDialog) {
        val phoneNumber = smsClientPhone
        val smsMessage = smsMessageContent

        var permissionGranted by remember {
            mutableStateOf(
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.SEND_SMS
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            )
        }

        val smsPermissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            permissionGranted = isGranted
            if (isGranted) {
                val sent = sendDirectSms(context, phoneNumber, smsMessage)
                if (sent) {
                    Toast.makeText(context, "تم إرسال رسالة كشف الفاتورة بنجاح!", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "فشل إرسال الرسالة المباشرة، جاري فتح التطبيق الافتراضي...", Toast.LENGTH_LONG).show()
                    sendSmsViaIntent(context, phoneNumber, smsMessage)
                }
            } else {
                Toast.makeText(context, "تم رفض الصلاحية. جاري فتح تطبيق الرسائل لإرسالها يدوياً...", Toast.LENGTH_LONG).show()
                sendSmsViaIntent(context, phoneNumber, smsMessage)
            }
            showSmsConfirmDialog = false
        }

        AlertDialog(
            onDismissRequest = { showSmsConfirmDialog = false },
            title = { Text("إرسال تفاصيل الفاتورة عبر SMS", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("هل ترغب في إرسال تفاصيل الفاتورة والمال المتبقي للعميل عبر رسالة SMS؟", fontSize = 14.sp)
                    Text("سيتم إرسال الرسالة إلى: ${phoneNumber.ifEmpty { "لا يوجد رقم مسجل!" }}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Box(modifier = Modifier.padding(8.dp)) {
                            Text(smsMessage, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (phoneNumber.isBlank()) {
                            Toast.makeText(context, "لا يمكن الإرسال، رقم هاتف العميل فارغ!", Toast.LENGTH_SHORT).show()
                        } else {
                            sendSmsViaIntent(context, phoneNumber, smsMessage)
                        }
                        showSmsConfirmDialog = false
                    }
                ) {
                    Text("إرسال عبر تطبيق الرسائل", maxLines = 1, softWrap = false)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSmsConfirmDialog = false }) {
                    Text("إلغاء وتخطي", maxLines = 1, softWrap = false)
                }
            }
        )
    }
}

// Build a beautiful share invoice plain text message
fun buildShareTextReceipt(
    invoice: Invoice,
    items: List<InvoiceItem>,
    storeName: String,
    currency: String,
    showVatAndSubtotal: Boolean = false,
    rates: List<ExchangeRate> = emptyList(),
    targetCurrency: String? = null
): String {
    return ShareManager.buildInvoiceSummaryText(
        invoice = invoice,
        items = items,
        storeName = storeName,
        storePhone = "",
        storeAddress = "",
        currency = currency,
        client = null,
        showVatAndSubtotal = showVatAndSubtotal,
        rates = rates,
        itemsMap = emptyMap(),
        targetCurrency = targetCurrency
    )
}
