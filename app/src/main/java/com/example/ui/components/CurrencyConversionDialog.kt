package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.ExchangeRate
import com.example.domain.currency.Currency
import com.example.domain.currency.CurrencyConversionService
import com.example.domain.currency.Money
import com.example.util.FormatUtils
import com.example.util.MonetaryUtils
import java.math.BigDecimal
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrencyConversionDialog(
    rates: List<ExchangeRate>,
    initialAmount: Double = 100.0,
    initialFromCurrency: String = "الريال اليمني",
    initialToCurrency: String = "الريال السعودي",
    onNavigateToSettings: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    var amountStr by remember { mutableStateOf(if (initialAmount > 0) initialAmount.toString() else "100") }
    var fromCurrency by remember { mutableStateOf(Currency.fromCodeOrName(initialFromCurrency)) }
    var toCurrency by remember { mutableStateOf(Currency.fromCodeOrName(initialToCurrency)) }
    var expandedFrom by remember { mutableStateOf(false) }
    var expandedTo by remember { mutableStateOf(false) }

    val rateMap = remember(rates) {
        rates.associate {
            (Currency.fromCodeOrName(it.fromCurrency) to Currency.fromCodeOrName(it.toCurrency)) to BigDecimal.valueOf(it.rate)
        }
    }

    val amountDouble = amountStr.toDoubleOrNull() ?: 0.0
    val money = Money(amountDouble, fromCurrency)
    val conversion = CurrencyConversionService.convert(money, toCurrency, rateMap)

    // Pre-calculate 1 unit exchange rates between all pairs
    val pairRates = remember(rates) {
        listOf(
            Triple(Currency.USD, Currency.YER, "الدولار الأمريكي مقابل الريال اليمني"),
            Triple(Currency.SAR, Currency.YER, "الريال السعودي مقابل الريال اليمني"),
            Triple(Currency.USD, Currency.SAR, "الدولار الأمريكي مقابل الريال السعودي")
        ).map { (from, to, title) ->
            val direct = CurrencyConversionService.convert(Money(1.0, from), to, rateMap)
            val inverse = CurrencyConversionService.convert(Money(1.0, to), from, rateMap)
            RatePairInfo(
                title = title,
                from = from,
                to = to,
                directRate = direct.convertedMoney.toDouble(),
                inverseRate = inverse.convertedMoney.toDouble()
            )
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CurrencyExchange,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Column {
                        Text(
                            text = "مصارفة وتحويل العملات",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "أسعار التحويل والعكس وحساب الفواتير",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Divider()

                // Section 1: Standard 1-to-1 Exchange Table (التحويل المتبادل بين العملات والعكس)
                Text(
                    text = "📊 أسعار الصرف الحالية (مقابل كل 1 وحدة والعكس):",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    pairRates.forEach { pair ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "1 ${pair.from.code} = ${FormatUtils.formatAmount(pair.directRate)} ${pair.to.code}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${pair.from.flag} ➔ ${pair.to.flag}",
                                        fontSize = 12.sp
                                    )
                                }
                                Text(
                                    text = "العكس: 1 ${pair.to.code} ≈ ${String.format(Locale.US, "%.5f", pair.inverseRate)} ${pair.from.code}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Divider()

                // Section 2: Interactive Converter
                Text(
                    text = "🧮 حاسبة التحويل السريع:",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                // Amount Field
                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it },
                    label = { Text("المبلغ المراد تحويله") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                // Currency Choice Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // From Currency Dropdown
                    ExposedDropdownMenuBox(
                        expanded = expandedFrom,
                        onExpandedChange = { expandedFrom = !expandedFrom },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = fromCurrency.displayName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("من") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedFrom) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = expandedFrom,
                            onDismissRequest = { expandedFrom = false }
                        ) {
                            Currency.values().forEach { curr ->
                                DropdownMenuItem(
                                    text = { Text(curr.displayName) },
                                    onClick = {
                                        fromCurrency = curr
                                        expandedFrom = false
                                    }
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = {
                            val temp = fromCurrency
                            fromCurrency = toCurrency
                            toCurrency = temp
                        },
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SwapHoriz,
                            contentDescription = "تبديل",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // To Currency Dropdown
                    ExposedDropdownMenuBox(
                        expanded = expandedTo,
                        onExpandedChange = { expandedTo = !expandedTo },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = toCurrency.displayName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("إلى") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedTo) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = expandedTo,
                            onDismissRequest = { expandedTo = false }
                        ) {
                            Currency.values().forEach { curr ->
                                DropdownMenuItem(
                                    text = { Text(curr.displayName) },
                                    onClick = {
                                        toCurrency = curr
                                        expandedTo = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Result Box
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "المبلغ المحسوب:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                        Text(
                            text = "${FormatUtils.formatAmount(conversion.convertedMoney.toDouble())} ${toCurrency.code}",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = conversion.rateDescription,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                // Navigate to Settings button
                if (onNavigateToSettings != null) {
                    Button(
                        onClick = {
                            onDismiss()
                            onNavigateToSettings()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("⚙️ الانتقال لصفحة الإعدادات لتعديل أسعار الصرف", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text("إغلاق", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private data class RatePairInfo(
    val title: String,
    val from: Currency,
    val to: Currency,
    val directRate: Double,
    val inverseRate: Double
)
