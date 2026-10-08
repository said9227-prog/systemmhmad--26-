package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Money
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.AppViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddClientScreen(
    viewModel: AppViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // 👤 معلومات العميل
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var altPhone by remember { mutableStateOf("") }
    var companyName by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var nameError by remember { mutableStateOf(false) }

    // 💰 إعداد الحساب
    var dealType by remember { mutableStateOf("نقدي") } // نقدي, آجل
    var initialBalanceStr by remember { mutableStateOf("0") }
    var balanceType by remember { mutableStateOf("عليه لنا") } // عليه لنا, له عندنا

    // 📅 شروط البيع الآجل
    var creditLimitStr by remember { mutableStateOf("") }
    var paymentPeriod by remember { mutableStateOf("عند الطلب") }
    var customPaymentDaysStr by remember { mutableStateOf("") }
    var expandedPaymentPeriod by remember { mutableStateOf(false) }

    val paymentOptions = listOf("عند الطلب", "7 أيام", "15 يومًا", "30 يومًا", "60 يومًا", "مخصص")

    // 🏷️ تصنيف العميل
    var clientType by remember { mutableStateOf("فرد") }
    var expandedClientType by remember { mutableStateOf(false) }
    val clientTypeOptions = listOf("فرد", "تاجر", "شركة", "مؤسسة", "آخر")

    var classification by remember { mutableStateOf("جديد") }
    var expandedClassification by remember { mutableStateOf(false) }
    val classificationOptions = listOf("جديد", "دائم", "VIP", "متعثر")

    // 📝 معلومات إضافية
    var email by remember { mutableStateOf("") }
    var taxNumber by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var isAdditionalInfoExpanded by remember { mutableStateOf(false) }

    // Calculated default due date
    val defaultDueDate = remember(paymentPeriod, customPaymentDaysStr) {
        val days = when (paymentPeriod) {
            "7 أيام" -> 7
            "15 يومًا" -> 15
            "30 يومًا" -> 30
            "60 يومًا" -> 60
            "مخصص" -> customPaymentDaysStr.toIntOrNull() ?: 0
            else -> 0
        }
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, days)
        cal.time
    }
    
    val dateFormatter = SimpleDateFormat("dd MMMM yyyy", Locale("ar"))

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("إضافة عميل", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        bottomBar = {
            Surface(
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                PaddingValues(16.dp).let {
                    Button(
                        onClick = {
                            if (name.isBlank()) {
                                nameError = true
                                Toast.makeText(context, "يرجى إدخال اسم العميل", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            val initialBal = initialBalanceStr.toDoubleOrNull() ?: 0.0
                            val limit = creditLimitStr.toDoubleOrNull() ?: 0.0
                            val days = when (paymentPeriod) {
                                "7 أيام" -> 7
                                "15 يومًا" -> 15
                                "30 يومًا" -> 30
                                "60 يومًا" -> 60
                                "مخصص" -> customPaymentDaysStr.toIntOrNull() ?: 0
                                else -> 0
                            }

                            viewModel.addClient(
                                name = name.trim(),
                                phone = phone.trim(),
                                altPhone = altPhone.trim(),
                                companyName = companyName.trim(),
                                address = address.trim(),
                                city = city.trim(),
                                email = email.trim(),
                                notes = notes.trim(),
                                classification = classification,
                                initialBalance = initialBal,
                                balanceType = balanceType,
                                dealType = dealType,
                                paymentPeriod = paymentPeriod,
                                defaultDueDateDays = days,
                                clientType = clientType,
                                taxNumber = taxNumber.trim(),
                                creditLimit = limit
                            )

                            Toast.makeText(context, "تمت إضافة العميل بنجاح", Toast.LENGTH_SHORT).show()
                            onNavigateBack()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text("حفظ العميل", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(8.dp))
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 👤 معلومات العميل
            SectionCard(title = "👤 معلومات العميل") {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; nameError = false },
                    label = { Text("اسم العميل *") },
                    modifier = Modifier.fillMaxWidth(),
                    isError = nameError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    singleLine = true,
                    supportingText = if (nameError) { { Text("يرجى إدخال اسم العميل") } } else null
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("رقم الهاتف") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = altPhone,
                        onValueChange = { altPhone = it },
                        label = { Text("رقم إضافي") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true
                    )
                }

                OutlinedTextField(
                    value = companyName,
                    onValueChange = { companyName = it },
                    label = { Text("اسم الشركة / المؤسسة") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    singleLine = true
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = city,
                        onValueChange = { city = it },
                        label = { Text("المدينة / المنطقة") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text("العنوان") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                        singleLine = true
                    )
                }
            }

            // 💰 إعداد الحساب
            SectionCard(title = "💰 إعداد الحساب") {
                Text("نوع التعامل", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SelectableChip(
                        selected = dealType == "نقدي",
                        text = "نقدي",
                        icon = Icons.Default.Money,
                        onClick = { dealType = "نقدي" },
                        modifier = Modifier.weight(1f)
                    )
                    SelectableChip(
                        selected = dealType == "آجل",
                        text = "آجل",
                        icon = Icons.Default.CreditCard,
                        onClick = { dealType = "آجل" },
                        modifier = Modifier.weight(1f)
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = initialBalanceStr,
                    onValueChange = { initialBalanceStr = it },
                    label = { Text("الرصيد الافتتاحي (﷼)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )

                if ((initialBalanceStr.toDoubleOrNull() ?: 0.0) > 0) {
                    Text("نوع الرصيد", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SelectableChip(
                            selected = balanceType == "عليه لنا",
                            text = "عليه لنا",
                            onClick = { balanceType = "عليه لنا" },
                            modifier = Modifier.weight(1f),
                            selectedColor = MaterialTheme.colorScheme.errorContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                        SelectableChip(
                            selected = balanceType == "له عندنا",
                            text = "له عندنا",
                            onClick = { balanceType = "له عندنا" },
                            modifier = Modifier.weight(1f),
                            selectedColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // 📅 شروط البيع الآجل
            AnimatedVisibility(visible = dealType == "آجل") {
                SectionCard(title = "📅 شروط البيع الآجل") {
                    OutlinedTextField(
                        value = creditLimitStr,
                        onValueChange = { creditLimitStr = it },
                        label = { Text("حد الائتمان (﷼)") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )

                    ExposedDropdownMenuBox(
                        expanded = expandedPaymentPeriod,
                        onExpandedChange = { expandedPaymentPeriod = !expandedPaymentPeriod }
                    ) {
                        OutlinedTextField(
                            value = paymentPeriod,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("مدة السداد") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedPaymentPeriod) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = expandedPaymentPeriod,
                            onDismissRequest = { expandedPaymentPeriod = false }
                        ) {
                            paymentOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option) },
                                    onClick = {
                                        paymentPeriod = option
                                        expandedPaymentPeriod = false
                                    }
                                )
                            }
                        }
                    }

                    if (paymentPeriod == "مخصص") {
                        OutlinedTextField(
                            value = customPaymentDaysStr,
                            onValueChange = { customPaymentDaysStr = it },
                            label = { Text("عدد الأيام") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                    }

                    if (paymentPeriod != "عند الطلب") {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.CalendarToday, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text("تاريخ الاستحقاق الافتراضي:", style = MaterialTheme.typography.bodySmall)
                                    Text(dateFormatter.format(defaultDueDate), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                }
                            }
                        }
                    }
                }
            }

            // 🏷️ تصنيف العميل
            SectionCard(title = "🏷️ تصنيف العميل") {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExposedDropdownMenuBox(
                        expanded = expandedClientType,
                        onExpandedChange = { expandedClientType = !expandedClientType },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = clientType,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("نوع العميل") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedClientType) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = expandedClientType,
                            onDismissRequest = { expandedClientType = false }
                        ) {
                            clientTypeOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option) },
                                    onClick = {
                                        clientType = option
                                        expandedClientType = false
                                    }
                                )
                            }
                        }
                    }

                    ExposedDropdownMenuBox(
                        expanded = expandedClassification,
                        onExpandedChange = { expandedClassification = !expandedClassification },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = classification,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("تصنيف العميل") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedClassification) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = expandedClassification,
                            onDismissRequest = { expandedClassification = false }
                        ) {
                            classificationOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option) },
                                    onClick = {
                                        classification = option
                                        expandedClassification = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // 📝 معلومات إضافية
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isAdditionalInfoExpanded = !isAdditionalInfoExpanded }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("📝 معلومات إضافية", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
                        Icon(
                            imageVector = if (isAdditionalInfoExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Expand"
                        )
                    }
                    
                    AnimatedVisibility(visible = isAdditionalInfoExpanded) {
                        Column(
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            OutlinedTextField(
                                value = email,
                                onValueChange = { email = it },
                                label = { Text("البريد الإلكتروني") },
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = taxNumber,
                                onValueChange = { taxNumber = it },
                                label = { Text("الرقم الضريبي") },
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = notes,
                                onValueChange = { notes = it },
                                label = { Text("ملاحظات") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 3
                            )
                        }
                    }
                }
            }
            
            // Bottom spacer for FAB/Bottom button
            Spacer(modifier = Modifier.height(64.dp))
        }
    }
}

@Composable
fun SectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
fun SelectableChip(
    selected: Boolean,
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selectedColor: Color = MaterialTheme.colorScheme.primary,
    selectedTextColor: Color = MaterialTheme.colorScheme.onPrimary
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = MaterialTheme.shapes.medium,
        color = if (selected) selectedColor else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (selected) selectedTextColor else MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(text, fontWeight = FontWeight.Bold)
        }
    }
}
