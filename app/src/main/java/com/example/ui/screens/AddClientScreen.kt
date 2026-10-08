package com.example.ui.screens

import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Money
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.util.FormatUtils
import com.example.util.rememberFormNavigator
import com.example.util.scrollToTopOnFocus
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
    val formNavigator = rememberFormNavigator()

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
    var balanceType by remember { mutableStateOf("مدين") } // مدين, دائن
    val currencyOptions = listOf("الريال اليمني", "ريال سعودي", "دولار أمريكي")
    var initialBalanceCurrency by remember { mutableStateOf("الريال اليمني") }

    // 📱 Contact Picker
    val contactPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickContact()
    ) { uri: Uri? ->
        uri?.let { contactUri ->
            try {
                val cursor = context.contentResolver.query(contactUri, null, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val idIndex = it.getColumnIndex(ContactsContract.Contacts._ID)
                        val id = if (idIndex != -1) it.getString(idIndex) else null
                        val nameIndex = it.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                        if (nameIndex != -1 && name.isBlank()) {
                            name = it.getString(nameIndex) ?: ""
                        }
                        val hasPhoneIndex = it.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)
                        val hasPhone = if (hasPhoneIndex != -1) it.getInt(hasPhoneIndex) else 0
                        if (hasPhone > 0 && id != null) {
                            val phones = context.contentResolver.query(
                                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                                null,
                                ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                                arrayOf(id),
                                null
                            )
                            phones?.use { pCursor ->
                                if (pCursor.moveToFirst()) {
                                    val numberIndex = pCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                                    if (numberIndex != -1) {
                                        phone = pCursor.getString(numberIndex) ?: ""
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "تعذر قراءة جهة الاتصال: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val contactPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            try {
                contactPickerLauncher.launch(null)
            } catch (e: Exception) {
                Toast.makeText(context, "تعذر فتح جهات الاتصال", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "تم رفض صلاحية الوصول لجهات الاتصال", Toast.LENGTH_SHORT).show()
        }
    }

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
                                creditLimit = limit,
                                initialBalanceCurrency = initialBalanceCurrency,
                                currency = initialBalanceCurrency
                            )

                            Toast.makeText(context, "تمت إضافة العميل بنجاح", Toast.LENGTH_SHORT).show()
                            onNavigateBack()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
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
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 👤 معلومات العميل
            SectionCard(title = "👤 معلومات العميل") {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; nameError = false },
                    label = { Text("اسم العميل *") },
                    modifier = formNavigator.fieldModifier("client_name", Modifier.fillMaxWidth()),
                    isError = nameError,
                    keyboardOptions = formNavigator.keyboardOptions("client_name", KeyboardType.Text),
                    keyboardActions = formNavigator.keyboardActions("client_name"),
                    singleLine = true,
                    supportingText = if (nameError) { { Text("يرجى إدخال اسم العميل") } } else null
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { 
                            val old = phone
                            phone = it
                            formNavigator.checkPhoneAutoAdvance("client_phone", old, it)
                        },
                        label = { Text("رقم الهاتف") },
                        trailingIcon = {
                            IconButton(onClick = {
                                contactPermissionLauncher.launch(android.Manifest.permission.READ_CONTACTS)
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Contacts,
                                    contentDescription = "اختيار من جهات الاتصال",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        modifier = formNavigator.fieldModifier("client_phone", Modifier.weight(1f)),
                        keyboardOptions = formNavigator.keyboardOptions("client_phone", KeyboardType.Phone),
                        keyboardActions = formNavigator.keyboardActions("client_phone"),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = altPhone,
                        onValueChange = { 
                            val old = altPhone
                            altPhone = it
                            formNavigator.checkPhoneAutoAdvance("client_alt_phone", old, it)
                        },
                        label = { Text("رقم إضافي") },
                        modifier = formNavigator.fieldModifier("client_alt_phone", Modifier.weight(1f)),
                        keyboardOptions = formNavigator.keyboardOptions("client_alt_phone", KeyboardType.Phone),
                        keyboardActions = formNavigator.keyboardActions("client_alt_phone"),
                        singleLine = true
                    )
                }

                OutlinedTextField(
                    value = companyName,
                    onValueChange = { companyName = it },
                    label = { Text("اسم الشركة / المؤسسة") },
                    modifier = formNavigator.fieldModifier("client_company", Modifier.fillMaxWidth()),
                    keyboardOptions = formNavigator.keyboardOptions("client_company", KeyboardType.Text),
                    keyboardActions = formNavigator.keyboardActions("client_company"),
                    singleLine = true
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = city,
                        onValueChange = { city = it },
                        label = { Text("المدينة / المنطقة") },
                        modifier = formNavigator.fieldModifier("client_city", Modifier.weight(1f)),
                        keyboardOptions = formNavigator.keyboardOptions("client_city", KeyboardType.Text),
                        keyboardActions = formNavigator.keyboardActions("client_city"),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text("العنوان") },
                        modifier = formNavigator.fieldModifier("client_address", Modifier.weight(1f)),
                        keyboardOptions = formNavigator.keyboardOptions("client_address", KeyboardType.Text),
                        keyboardActions = formNavigator.keyboardActions("client_address"),
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

                // Currency selector for initial balance
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("عملة الحساب الافتتاحي:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        currencyOptions.forEach { curr ->
                            FilterChip(
                                selected = initialBalanceCurrency == curr,
                                onClick = { initialBalanceCurrency = curr },
                                label = { Text(curr, fontSize = 11.sp, fontWeight = if (initialBalanceCurrency == curr) FontWeight.Bold else FontWeight.Normal) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                OutlinedTextField(
                    value = initialBalanceStr,
                    onValueChange = { initialBalanceStr = it },
                    label = { Text("الرصيد الافتتاحي ($initialBalanceCurrency)") },
                    modifier = formNavigator.fieldModifier("client_initial_balance", Modifier.fillMaxWidth()),
                    keyboardOptions = formNavigator.keyboardOptions("client_initial_balance", KeyboardType.Number),
                    keyboardActions = formNavigator.keyboardActions("client_initial_balance"),
                    singleLine = true,
                    maxLines = 1,
                    supportingText = {
                        val initBal = initialBalanceStr.toDoubleOrNull() ?: 0.0
                        if (initBal > 0) {
                            Text(
                                text = "المبلغ: ${FormatUtils.formatAmount(initBal)}\u00A0$initialBalanceCurrency",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                )

                if ((initialBalanceStr.toDoubleOrNull() ?: 0.0) > 0) {
                    Text("نوع الرصيد", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SelectableChip(
                            selected = balanceType == "مدين",
                            text = "مدين",
                            onClick = { balanceType = "مدين" },
                            modifier = Modifier.weight(1f),
                            selectedColor = MaterialTheme.colorScheme.errorContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                        SelectableChip(
                            selected = balanceType == "دائن",
                            text = "دائن",
                            onClick = { balanceType = "دائن" },
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
                        modifier = formNavigator.fieldModifier("client_credit_limit", Modifier.fillMaxWidth()),
                        keyboardOptions = formNavigator.keyboardOptions("client_credit_limit", KeyboardType.Number),
                        keyboardActions = formNavigator.keyboardActions("client_credit_limit"),
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
                            modifier = Modifier.scrollToTopOnFocus().scrollToTopOnFocus().menuAnchor().fillMaxWidth()
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
                            modifier = formNavigator.fieldModifier("client_custom_days", Modifier.fillMaxWidth()),
                            keyboardOptions = formNavigator.keyboardOptions("client_custom_days", KeyboardType.Number),
                            keyboardActions = formNavigator.keyboardActions("client_custom_days"),
                            singleLine = true
                        )
                    }

                    if (paymentPeriod != "عند الطلب") {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
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
                            modifier = Modifier.scrollToTopOnFocus().scrollToTopOnFocus().menuAnchor().fillMaxWidth()
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
                            modifier = Modifier.scrollToTopOnFocus().scrollToTopOnFocus().menuAnchor().fillMaxWidth()
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
                            .padding(8.dp),
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
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = email,
                                onValueChange = { email = it },
                                label = { Text("البريد الإلكتروني") },
                                modifier = formNavigator.fieldModifier("client_email", Modifier.fillMaxWidth()),
                                keyboardOptions = formNavigator.keyboardOptions("client_email", KeyboardType.Email),
                                keyboardActions = formNavigator.keyboardActions("client_email"),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = taxNumber,
                                onValueChange = { taxNumber = it },
                                label = { Text("الرقم الضريبي") },
                                modifier = formNavigator.fieldModifier("client_tax_number", Modifier.fillMaxWidth()),
                                keyboardOptions = formNavigator.keyboardOptions("client_tax_number", KeyboardType.Number),
                                keyboardActions = formNavigator.keyboardActions("client_tax_number"),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = notes,
                                onValueChange = { notes = it },
                                label = { Text("ملاحظات") },
                                modifier = formNavigator.fieldModifier("client_notes", Modifier.fillMaxWidth()),
                                keyboardOptions = formNavigator.keyboardOptions("client_notes", KeyboardType.Text),
                                keyboardActions = formNavigator.keyboardActions("client_notes"),
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
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
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
            modifier = Modifier.padding(horizontal = 12.dp),
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
