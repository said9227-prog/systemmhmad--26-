import sys

content = """package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.model.Item
import com.example.data.model.ItemCategory
import com.example.data.model.ItemUnit
import com.example.data.model.SupplierCompany
import com.example.ui.viewmodel.AppViewModel
import com.example.util.DateTimeUtils
import com.example.util.FormatUtils
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddItemScreen(
    viewModel: AppViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val categoriesList by viewModel.categories.collectAsState()
    val unitsList by viewModel.units.collectAsState()
    val companiesList by viewModel.supplierCompanies.collectAsState()

    val itemSuggestions by viewModel.itemSuggestions.collectAsState()

    // Form fields
    var name by remember { mutableStateOf("") }
    var barcode by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(categoriesList.firstOrNull()?.name ?: "عام") }
    var selectedUnit by remember { mutableStateOf(unitsList.firstOrNull()?.name ?: "قطعة") }

    var supplierType by remember { mutableStateOf("شركة") }
    var selectedCompany by remember { mutableStateOf<SupplierCompany?>(companiesList.firstOrNull()) }
    var individualSupplierName by remember { mutableStateOf("") }
    var individualSupplierPhone by remember { mutableStateOf("") }
    var individualSupplierNotes by remember { mutableStateOf("") }

    var purchasePriceStr by remember { mutableStateOf("") }
    var quantityStr by remember { mutableStateOf("") }
    var minQuantityStr by remember { mutableStateOf("3") }
    var purchaseDateMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var showDatePickerDialog by remember { mutableStateOf(false) }

    var sellingPriceStr by remember { mutableStateOf("") }
    
    var imageUriStr by remember { mutableStateOf<String?>(null) }
    var showImagePreviewDialog by remember { mutableStateOf(false) }

    // Errors
    var nameError by remember { mutableStateOf<String?>(null) }
    var supplierCompanyError by remember { mutableStateOf<String?>(null) }
    var individualSupplierError by remember { mutableStateOf<String?>(null) }
    var purchasePriceError by remember { mutableStateOf<String?>(null) }
    var sellingPriceError by remember { mutableStateOf<String?>(null) }
    var quantityError by remember { mutableStateOf<String?>(null) }
    var minQuantityError by remember { mutableStateOf<String?>(null) }

    // Dialogs
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var showAddUnitDialog by remember { mutableStateOf(false) }
    var showAddCompanyDialog by remember { mutableStateOf(false) }
    
    // Duplicate protection
    var existingDuplicateItem by remember { mutableStateOf<Item?>(null) }

    // Focus state for autocomplete
    var isNameFocused by remember { mutableStateOf(false) }

    // Photo picker
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            imageUriStr = uri.toString()
        }
    }

    // Default category/unit selection
    LaunchedEffect(categoriesList) {
        if (categoriesList.isNotEmpty() && !categoriesList.any { it.name == selectedCategory }) {
            selectedCategory = categoriesList.first().name
        }
    }
    LaunchedEffect(unitsList) {
        if (unitsList.isNotEmpty() && !unitsList.any { it.name == selectedUnit }) {
            selectedUnit = unitsList.first().name
        }
    }
    LaunchedEffect(companiesList) {
        if (companiesList.isNotEmpty() && selectedCompany == null) {
            selectedCompany = companiesList.first()
        }
    }

    if (showAddCategoryDialog) {
        AddCategoryDialog(
            existingCategories = categoriesList.map { it.name },
            onDismiss = { showAddCategoryDialog = false },
            onSave = { categoryName, isPermanent ->
                if (isPermanent) {
                    viewModel.addCategory(categoryName, isPermanent = true)
                }
                selectedCategory = categoryName
                showAddCategoryDialog = false
            }
        )
    }

    if (showAddUnitDialog) {
        AddUnitDialog(
            existingUnits = unitsList.map { it.name },
            onDismiss = { showAddUnitDialog = false },
            onSave = { unitName, isPermanent ->
                if (isPermanent) {
                    viewModel.addUnit(unitName, isPermanent = true)
                }
                selectedUnit = unitName
                showAddUnitDialog = false
            }
        )
    }

    if (showAddCompanyDialog) {
        AddCompanyDialog(
            existingCompanies = companiesList.map { it.name },
            onDismiss = { showAddCompanyDialog = false },
            onSave = { companyName, phone, address, notes ->
                viewModel.addCompany(companyName, phone, address, notes) { newId ->
                    selectedCompany = SupplierCompany(
                        id = newId.toInt(),
                        name = companyName,
                        phone = phone,
                        address = address,
                        notes = notes
                    )
                }
                showAddCompanyDialog = false
            }
        )
    }

    if (showDatePickerDialog) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = purchaseDateMillis
        )
        DatePickerDialog(
            onDismissRequest = { showDatePickerDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        purchaseDateMillis = it
                    }
                    showDatePickerDialog = false
                }) {
                    Text("تأكيد", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePickerDialog = false }) {
                    Text("إلغاء")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
    
    if (showImagePreviewDialog && imageUriStr != null) {
        ImagePreviewDialog(
            imageUri = imageUriStr!!,
            onDismiss = { showImagePreviewDialog = false },
            onRemove = { 
                imageUriStr = null
                showImagePreviewDialog = false
            }
        )
    }
    
    if (existingDuplicateItem != null) {
        AlertDialog(
            onDismissRequest = { existingDuplicateItem = null },
            title = { Text("هذا الصنف موجود بالفعل", fontWeight = FontWeight.Bold) },
            text = { Text("يوجد صنف بهذا الاسم بالفعل (${existingDuplicateItem!!.name}). هل تريد فتح الصنف للتعديل بدلاً من إضافة صنف جديد؟") },
            confirmButton = {
                Button(onClick = {
                    // Ideally navigate to edit, but for now we'll just close and let user handle it
                    Toast.makeText(context, "الرجاء تعديل الصنف من قائمة الأصناف", Toast.LENGTH_LONG).show()
                    existingDuplicateItem = null
                    onNavigateBack()
                }) {
                    Text("استخدام الصنف الموجود")
                }
            },
            dismissButton = {
                TextButton(onClick = { existingDuplicateItem = null }) {
                    Text("إلغاء الإضافة")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("إضافة صنف جديد", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("add_item_back_button")) {
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
                shadowElevation = 12.dp,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Button(
                        onClick = {
                            nameError = null
                            supplierCompanyError = null
                            individualSupplierError = null
                            purchasePriceError = null
                            sellingPriceError = null
                            quantityError = null
                            minQuantityError = null

                            var hasError = false
                            if (name.trim().isBlank()) { nameError = "يرجى إدخال اسم الصنف"; hasError = true }
                            
                            if (supplierType == "شركة") {
                                if (selectedCompany == null) { supplierCompanyError = "يرجى اختيار شركة التوريد أو إضافة شركة جديدة"; hasError = true }
                            } else {
                                if (individualSupplierName.trim().isBlank()) { individualSupplierError = "يرجى إدخال اسم المورد الفردي"; hasError = true }
                            }

                            val pPrice = purchasePriceStr.toDoubleOrNull()
                            if (pPrice == null || pPrice < 0) { purchasePriceError = "يرجى إدخال سعر شراء صحيح (صفر أو أكثر)"; hasError = true }
                            
                            val sPrice = sellingPriceStr.toDoubleOrNull()
                            if (sPrice == null || sPrice < 0) { sellingPriceError = "يرجى إدخال سعر بيع صحيح (صفر أو أكثر)"; hasError = true }
                            
                            val qty = quantityStr.toIntOrNull()
                            if (qty == null || qty < 0) { quantityError = "يرجى إدخال كمية صحيحة (صفر أو أكثر)"; hasError = true }
                            
                            val minQty = minQuantityStr.toIntOrNull()
                            if (minQty == null || minQty < 0) { minQuantityError = "يرجى إدخال الحد الأدنى صحيح (صفر أو أكثر)"; hasError = true }

                            if (!hasError) {
                                coroutineScope.launch {
                                    val existing = viewModel.checkItemExists(name)
                                    if (existing != null) {
                                        existingDuplicateItem = existing
                                    } else {
                                        viewModel.addItem(
                                            name = name,
                                            barcode = barcode,
                                            category = selectedCategory,
                                            unit = selectedUnit,
                                            supplierType = supplierType,
                                            supplierCompanyId = if (supplierType == "شركة") selectedCompany?.id else null,
                                            supplierCompanyName = if (supplierType == "شركة") selectedCompany?.name ?: "" else "",
                                            individualSupplierName = if (supplierType == "مورد فردي") individualSupplierName else "",
                                            individualSupplierPhone = if (supplierType == "مورد فردي") individualSupplierPhone else "",
                                            individualSupplierNotes = if (supplierType == "مورد فردي") individualSupplierNotes else "",
                                            purchaseDate = purchaseDateMillis,
                                            purchasePrice = pPrice ?: 0.0,
                                            sellingPrice = sPrice ?: 0.0,
                                            quantity = qty ?: 0,
                                            minQuantityAlert = minQty ?: 0,
                                            imageUri = imageUriStr,
                                            onSuccess = {
                                                Toast.makeText(context, "تم حفظ الصنف بنجاح!", Toast.LENGTH_SHORT).show()
                                                onNavigateBack()
                                            }
                                        )
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("حفظ الصنف", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            
            // Image Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (imageUriStr != null) {
                        Box(
                            modifier = Modifier
                                .size(120.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                                .clickable { showImagePreviewDialog = true },
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = imageUriStr,
                                contentDescription = "صورة الصنف",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(4.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
                                    .clickable { photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                                    .padding(6.dp)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "تغيير", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .clickable { photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddPhotoAlternate,
                                contentDescription = "إضافة صورة",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                        Text("إضافة صورة للصنف", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                    }
                }
            }

            // Info Section
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("معلومات الصنف", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    
                    // SMART AUTOCOMPLETE TEXT FIELD
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { 
                                name = it
                                nameError = null
                                viewModel.searchItemAutocomplete(it)
                            },
                            label = { Text("اسم الصنف *") },
                            placeholder = { Text("مثال: بانادول إكسترا") },
                            isError = nameError != null,
                            supportingText = nameError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                        )
                        
                        // Suggestion Dropdown
                        if (name.isNotEmpty() && itemSuggestions.isNotEmpty()) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 65.dp)
                                    .heightIn(max = 200.dp),
                                shadowElevation = 8.dp,
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface
                            ) {
                                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                                    itemSuggestions.forEach { suggestion ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    name = suggestion.name
                                                    selectedCategory = suggestion.category
                                                    selectedUnit = suggestion.unit
                                                    barcode = suggestion.barcode
                                                    viewModel.clearItemSuggestions()
                                                }
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.Inventory2, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column {
                                                Text(suggestion.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                Text("التصنيف: ${suggestion.category} - الوحدة: ${suggestion.unit}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                        HorizontalDivider()
                                    }
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = barcode,
                        onValueChange = { barcode = it },
                        label = { Text("الباركود (اختياري)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ExposedDropdownMenuBox(
                            expanded = false,
                            onExpandedChange = { },
                            modifier = Modifier.weight(1f)
                        ) {
                            var categoryExpanded by remember { mutableStateOf(false) }
                            OutlinedTextField(
                                value = selectedCategory,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("التصنيف") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                                modifier = Modifier.menuAnchor().clickable { categoryExpanded = true }.fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = categoryExpanded,
                                onDismissRequest = { categoryExpanded = false }
                            ) {
                                categoriesList.forEach { category ->
                                    DropdownMenuItem(
                                        text = { Text(category.name) },
                                        onClick = { selectedCategory = category.name; categoryExpanded = false }
                                    )
                                }
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("إضافة تصنيف جديد...", color = MaterialTheme.colorScheme.primary) },
                                    onClick = { categoryExpanded = false; showAddCategoryDialog = true },
                                    leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                                )
                            }
                        }
                        
                        ExposedDropdownMenuBox(
                            expanded = false,
                            onExpandedChange = { },
                            modifier = Modifier.weight(1f)
                        ) {
                            var unitExpanded by remember { mutableStateOf(false) }
                            OutlinedTextField(
                                value = selectedUnit,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("الوحدة") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = unitExpanded) },
                                modifier = Modifier.menuAnchor().clickable { unitExpanded = true }.fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = unitExpanded,
                                onDismissRequest = { unitExpanded = false }
                            ) {
                                unitsList.forEach { unit ->
                                    DropdownMenuItem(
                                        text = { Text(unit.name) },
                                        onClick = { selectedUnit = unit.name; unitExpanded = false }
                                    )
                                }
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("إضافة وحدة جديدة...", color = MaterialTheme.colorScheme.primary) },
                                    onClick = { unitExpanded = false; showAddUnitDialog = true },
                                    leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                                )
                            }
                        }
                    }
                }
            }

            // Supplier Section
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("المورد", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { supplierType = "شركة" }) {
                            RadioButton(selected = supplierType == "شركة", onClick = { supplierType = "شركة" })
                            Text("شركة")
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { supplierType = "مورد فردي" }) {
                            RadioButton(selected = supplierType == "مورد فردي", onClick = { supplierType = "مورد فردي" })
                            Text("مورد فردي")
                        }
                    }

                    if (supplierType == "شركة") {
                        ExposedDropdownMenuBox(
                            expanded = false,
                            onExpandedChange = { },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            var companyExpanded by remember { mutableStateOf(false) }
                            OutlinedTextField(
                                value = selectedCompany?.name ?: "",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("شركة التوريد *") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = companyExpanded) },
                                isError = supplierCompanyError != null,
                                supportingText = supplierCompanyError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                                modifier = Modifier.menuAnchor().clickable { companyExpanded = true }.fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = companyExpanded,
                                onDismissRequest = { companyExpanded = false }
                            ) {
                                companiesList.forEach { company ->
                                    DropdownMenuItem(
                                        text = { Text(company.name) },
                                        onClick = { selectedCompany = company; companyExpanded = false; supplierCompanyError = null }
                                    )
                                }
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("إضافة شركة جديدة...", color = MaterialTheme.colorScheme.primary) },
                                    onClick = { companyExpanded = false; showAddCompanyDialog = true },
                                    leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                                )
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value = individualSupplierName,
                            onValueChange = { individualSupplierName = it; individualSupplierError = null },
                            label = { Text("اسم المورد *") },
                            isError = individualSupplierError != null,
                            supportingText = individualSupplierError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = individualSupplierPhone,
                            onValueChange = { individualSupplierPhone = it },
                            label = { Text("رقم الهاتف") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                        )
                    }
                }
            }

            // Financial & Stock Section
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("الشراء والبيع والمخزون", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = purchasePriceStr,
                            onValueChange = { purchasePriceStr = it; purchasePriceError = null },
                            label = { Text("سعر الشراء *") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            isError = purchasePriceError != null,
                            supportingText = purchasePriceError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } }
                        )
                        OutlinedTextField(
                            value = sellingPriceStr,
                            onValueChange = { sellingPriceStr = it; sellingPriceError = null },
                            label = { Text("سعر البيع *") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            isError = sellingPriceError != null,
                            supportingText = sellingPriceError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } }
                        )
                    }
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = quantityStr,
                            onValueChange = { quantityStr = it; quantityError = null },
                            label = { Text("الكمية الحالية *") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            isError = quantityError != null,
                            supportingText = quantityError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } }
                        )
                        OutlinedTextField(
                            value = minQuantityStr,
                            onValueChange = { minQuantityStr = it; minQuantityError = null },
                            label = { Text("الحد الأدنى للتنبيه *") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            isError = minQuantityError != null,
                            supportingText = minQuantityError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } }
                        )
                    }
                    
                    OutlinedTextField(
                        value = FormatUtils.formatDateOnly(purchaseDateMillis),
                        onValueChange = { },
                        readOnly = true,
                        label = { Text("تاريخ الشراء") },
                        trailingIcon = { Icon(Icons.Default.DateRange, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth().clickable { showDatePickerDialog = true }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(70.dp))
        }
    }
}

@Composable
fun ImagePreviewDialog(imageUri: String, onDismiss: () -> Unit, onRemove: () -> Unit) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = true)
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            // Close button
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopStart).padding(16.dp).background(Color.Black.copy(alpha=0.5f), CircleShape)
            ) {
                Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color.White)
            }
            
            // Delete button
            IconButton(
                onClick = onRemove,
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).background(Color.Red.copy(alpha=0.8f), CircleShape)
            ) {
                Icon(Icons.Default.Delete, contentDescription = "حذف الصورة", tint = Color.White)
            }
            
            val state = rememberTransformableState { zoomChange, offsetChange, _ ->
                scale = (scale * zoomChange).coerceIn(1f, 5f)
                offset += offsetChange
            }

            AsyncImage(
                model = imageUri,
                contentDescription = "Full Screen Preview",
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    )
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                if (scale > 1f) {
                                    scale = 1f
                                    offset = androidx.compose.ui.geometry.Offset.Zero
                                } else {
                                    scale = 2.5f
                                }
                            }
                        )
                    }
                    .transformable(state = state),
                contentScale = ContentScale.Fit
            )
        }
    }
}

// Dialog Composable placeholders (AddCategoryDialog, AddUnitDialog, AddCompanyDialog)
@Composable
fun AddCategoryDialog(
    existingCategories: List<String>,
    onDismiss: () -> Unit,
    onSave: (name: String, isPermanent: Boolean) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var isPermanent by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة تصنيف جديد", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; errorMessage = null },
                    label = { Text("اسم التصنيف *") },
                    isError = errorMessage != null,
                    supportingText = errorMessage?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                if (name.trim().isBlank()) { errorMessage = "يرجى إدخال اسم التصنيف"; return@Button }
                if (existingCategories.any { it.trim().equals(name.trim(), ignoreCase = true) }) {
                    errorMessage = "هذا التصنيف موجود بالفعل."
                    return@Button
                }
                onSave(name.trim(), isPermanent)
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
fun AddUnitDialog(
    existingUnits: List<String>,
    onDismiss: () -> Unit,
    onSave: (name: String, isPermanent: Boolean) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var isPermanent by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة وحدة جديدة", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; errorMessage = null },
                    label = { Text("اسم الوحدة *") },
                    isError = errorMessage != null,
                    supportingText = errorMessage?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                if (name.trim().isBlank()) { errorMessage = "يرجى إدخال اسم الوحدة"; return@Button }
                if (existingUnits.any { it.trim().equals(name.trim(), ignoreCase = true) }) {
                    errorMessage = "هذه الوحدة موجودة بالفعل."
                    return@Button
                }
                onSave(name.trim(), isPermanent)
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
fun AddCompanyDialog(
    existingCompanies: List<String>,
    onDismiss: () -> Unit,
    onSave: (name: String, phone: String, address: String, notes: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة شركة جديدة", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; errorMessage = null },
                    label = { Text("اسم الشركة *") },
                    isError = errorMessage != null,
                    supportingText = errorMessage?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("الهاتف") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("العنوان") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("ملاحظات") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = {
                if (name.trim().isBlank()) { errorMessage = "يرجى إدخال اسم الشركة"; return@Button }
                if (existingCompanies.any { it.trim().equals(name.trim(), ignoreCase = true) }) {
                    errorMessage = "هذه الشركة موجودة بالفعل."
                    return@Button
                }
                onSave(name.trim(), phone.trim(), address.trim(), notes.trim())
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
"""

with open('app/src/main/java/com/example/ui/screens/AddItemScreen.kt', 'w', encoding='utf-8') as f:
    f.write(content)

print("Generated AddItemScreen.kt")
