package com.example.ui.screens

import android.Manifest
import android.net.Uri
import android.provider.ContactsContract
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.zIndex
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
import coil.request.ImageRequest
import com.example.ui.components.ImageViewerDialog
import com.example.util.ImageUtils
import com.example.data.model.Item
import com.example.data.model.ItemCategory
import com.example.data.model.ItemUnit
import com.example.data.model.SupplierCompany
import com.example.ui.viewmodel.AppViewModel
import com.example.util.DateTimeUtils
import com.example.util.FormatUtils
import com.example.util.rememberFormNavigator
import com.example.util.scrollToTopOnFocus
import androidx.compose.foundation.ScrollState
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
    val companiesList by viewModel.companies.collectAsState()
    val allItemsList by viewModel.items.collectAsState()
    val localAddedIndividualSuppliers = remember { mutableStateListOf<String>() }
    var pendingCompanyName by remember { mutableStateOf("") }
    val individualSuppliersList = remember(allItemsList, localAddedIndividualSuppliers) {
        val fromItems = allItemsList.map { it.individualSupplierName }.filter { it.isNotBlank() }
        (fromItems + localAddedIndividualSuppliers).distinct()
    }

    // Compute recently added categories (newest first)
    val recentCategories = remember(categoriesList, allItemsList) {
        val categoryTimestampMap = mutableMapOf<String, Long>()
        allItemsList.forEach { item ->
            val trimmed = item.category.trim()
            if (trimmed.isNotBlank()) {
                val prev = categoryTimestampMap[trimmed] ?: 0L
                categoryTimestampMap[trimmed] = maxOf(prev, item.createdAt)
            }
        }
        categoriesList.forEach { cat ->
            val trimmed = cat.name.trim()
            if (trimmed.isNotBlank()) {
                val prev = categoryTimestampMap[trimmed] ?: 0L
                val time = if (cat.createdAt > 0L) cat.createdAt else (cat.id * 1000L)
                categoryTimestampMap[trimmed] = maxOf(prev, time)
            }
        }
        val sorted = categoryTimestampMap.entries.sortedByDescending { it.value }.map { it.key }
        if (sorted.isEmpty()) listOf("عام") else sorted
    }

    // Compute recently added units (newest first)
    val recentUnits = remember(unitsList, allItemsList) {
        val unitTimestampMap = mutableMapOf<String, Long>()
        allItemsList.forEach { item ->
            val trimmed = item.unit.trim()
            if (trimmed.isNotBlank()) {
                val prev = unitTimestampMap[trimmed] ?: 0L
                unitTimestampMap[trimmed] = maxOf(prev, item.createdAt)
            }
        }
        unitsList.forEach { u ->
            val trimmed = u.name.trim()
            if (trimmed.isNotBlank()) {
                val prev = unitTimestampMap[trimmed] ?: 0L
                val time = if (u.createdAt > 0L) u.createdAt else (u.id * 1000L)
                unitTimestampMap[trimmed] = maxOf(prev, time)
            }
        }
        val sorted = unitTimestampMap.entries.sortedByDescending { it.value }.map { it.key }
        if (sorted.isEmpty()) listOf("قطعة") else sorted
    }

    val itemSuggestions by viewModel.itemSuggestions.collectAsState()

    // Form fields
    var name by remember { mutableStateOf("") }
    var barcode by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(recentCategories.firstOrNull() ?: "عام") }
    var selectedUnit by remember { mutableStateOf(recentUnits.firstOrNull() ?: "قطعة") }

    var supplierType by remember { mutableStateOf("شركة") }
    var selectedCompany by remember { mutableStateOf<SupplierCompany?>(null) }
    var supplierCompanyNameInput by remember { mutableStateOf("") }
    var companyPhone by remember { mutableStateOf("") }
    var individualSupplierName by remember { mutableStateOf("") }
    var individualSupplierPhone by remember { mutableStateOf("") }
    var individualSupplierNotes by remember { mutableStateOf("") }

    var pendingCategoryName by remember { mutableStateOf("") }
    var pendingUnitName by remember { mutableStateOf("") }

    val contactPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickContact()
    ) { uri: Uri? ->
        uri?.let { contactUri ->
            val cursor = context.contentResolver.query(contactUri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val idIndex = it.getColumnIndex(ContactsContract.Contacts._ID)
                    val id = if (idIndex != -1) it.getString(idIndex) else null
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
                                    val phoneNum = pCursor.getString(numberIndex)
                                    if (supplierType == "شركة") {
                                        companyPhone = phoneNum
                                    } else {
                                        individualSupplierPhone = phoneNum
                                    }
                                }
                            }
                        }
                    }
                }
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

    LaunchedEffect(selectedCompany) {
        if (selectedCompany != null) {
            supplierCompanyNameInput = selectedCompany?.name ?: ""
        }
    }

    val existingMatchedItem = remember(
        name,
        selectedCategory,
        selectedUnit,
        supplierType,
        selectedCompany,
        supplierCompanyNameInput,
        individualSupplierName,
        allItemsList
    ) {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) null
        else {
            val effCompanyName = if (supplierType == "شركة") {
                selectedCompany?.name?.trim() ?: supplierCompanyNameInput.trim()
            } else ""
            allItemsList.find {
                it.name.trim().equals(trimmedName, ignoreCase = true) &&
                it.category.trim().equals(selectedCategory.trim(), ignoreCase = true) &&
                it.unit.trim().equals(selectedUnit.trim(), ignoreCase = true) &&
                it.supplierType == supplierType &&
                if (supplierType == "شركة") {
                    it.supplierCompanyName.trim().equals(effCompanyName, ignoreCase = true)
                } else {
                    it.individualSupplierName.trim().equals(individualSupplierName.trim(), ignoreCase = true)
                }
            }
        }
    }

    var purchasePriceStr by remember { mutableStateOf("") }
    var quantityStr by remember { mutableStateOf("") }
    var minQuantityStr by remember { mutableStateOf("100") }
    var purchaseDateMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var showDatePickerDialog by remember { mutableStateOf(false) }

    var sellingPriceStr by remember { mutableStateOf("") }
    
    val currencyOptions = listOf("الريال اليمني", "ريال سعودي", "دولار أمريكي")
    var purchaseCurrency by remember { mutableStateOf("الريال اليمني") }
    var sellingCurrency by remember { mutableStateOf("الريال اليمني") }
    
    val formNavigator = rememberFormNavigator(
        listOf(
            "item_name",
            "item_barcode",
            "item_category",
            "item_unit",
            "item_supplier_name",
            "item_supplier_phone",
            "item_purchase_price",
            "item_selling_price",
            "item_quantity",
            "item_min_quantity"
        )
    )
    
    var imageUriStr by remember { mutableStateOf<String?>(null) }
    var isProcessingImage by remember { mutableStateOf(false) }
    var showImagePreviewDialog by remember { mutableStateOf(false) }
    var showDeleteImageWarning by remember { mutableStateOf(false) }

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
    var showSkipSellingPriceDialog by remember { mutableStateOf(false) }

    // Focus state for autocomplete
    var isNameFocused by remember { mutableStateOf(false) }

    // Photo picker
    val scrollState = rememberScrollState()
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                isProcessingImage = true
                val savedUri = ImageUtils.saveImageSafely(context, uri)
                if (savedUri != null) {
                    if (imageUriStr != null && imageUriStr != savedUri) {
                        ImageUtils.deleteImageFile(context, imageUriStr)
                    }
                    imageUriStr = savedUri
                } else {
                    Toast.makeText(context, "تعذر معالجة الصورة، يرجى اختيار صورة أخرى", Toast.LENGTH_SHORT).show()
                }
                isProcessingImage = false
            }
        }
    }

    // Default category/unit selection
    LaunchedEffect(recentCategories) {
        if (selectedCategory.isBlank() && recentCategories.isNotEmpty()) {
            selectedCategory = recentCategories.first()
        }
    }
    LaunchedEffect(recentUnits) {
        if (selectedUnit.isBlank() && recentUnits.isNotEmpty()) {
            selectedUnit = recentUnits.first()
        }
    }
    LaunchedEffect(companiesList) {
        if (companiesList.isNotEmpty() && selectedCompany == null && supplierCompanyNameInput.isEmpty()) {
            selectedCompany = companiesList.first()
            supplierCompanyNameInput = selectedCompany?.name ?: ""
        }
    }

    if (showAddCategoryDialog) {
        AddCategoryDialog(
            existingCategories = categoriesList.map { it.name },
            initialName = pendingCategoryName,
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
            initialName = pendingUnitName,
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
            initialName = pendingCompanyName,
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
                    supplierCompanyNameInput = companyName
                    companyPhone = phone
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
    
    if (showDeleteImageWarning) {
        AlertDialog(
            onDismissRequest = { showDeleteImageWarning = false },
            modifier = Modifier.fillMaxWidth(0.92f).widthIn(max = 520.dp),
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
            icon = { Icon(Icons.Default.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("تحذير: حذف الصورة", fontWeight = FontWeight.Bold) },
            text = { Text("هل أنت متأكد من رغبتك في حذف صورة هذا الصنف؟ لا يمكن التراجع عن هذه الخطوة.") },
            confirmButton = {
                Button(
                    onClick = {
                        ImageUtils.deleteImageFile(context, imageUriStr)
                        imageUriStr = null
                        showDeleteImageWarning = false
                        showImagePreviewDialog = false
                        Toast.makeText(context, "تم حذف الصورة", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text("نعم، حذف الصورة", fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteImageWarning = false },
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text("إلغاء", maxLines = 1, softWrap = false)
                }
            }
        )
    }

    if (showImagePreviewDialog && imageUriStr != null) {
        ImageViewerDialog(
            imageUri = imageUriStr!!,
            title = if (name.isNotBlank()) name else "معاينة صورة الصنف",
            onDismiss = { showImagePreviewDialog = false },
            onRequestRemove = {
                showDeleteImageWarning = true
            },
            onRequestReplace = {
                showImagePreviewDialog = false
                photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
        )
    }

    var isSaveAndNewRequested by remember { mutableStateOf(false) }

    val performSave: (Double) -> Unit = { finalSellingPrice ->
        val pPrice = purchasePriceStr.toDoubleOrNull() ?: 0.0
        val qty = quantityStr.toIntOrNull() ?: 0
        val minQty = minQuantityStr.toIntOrNull() ?: 3

        viewModel.addOrAccumulateItem(
            name = name,
            barcode = barcode,
            category = selectedCategory,
            unit = selectedUnit,
            supplierType = supplierType,
            supplierCompanyId = if (supplierType == "شركة") selectedCompany?.id else null,
            supplierCompanyName = if (supplierType == "شركة") supplierCompanyNameInput else "",
            companyPhone = if (supplierType == "شركة") companyPhone else "",
            individualSupplierName = if (supplierType == "مورد فردي") individualSupplierName else "",
            individualSupplierPhone = if (supplierType == "مورد فردي") individualSupplierPhone else "",
            individualSupplierNotes = if (supplierType == "مورد فردي") individualSupplierNotes else "",
            purchaseDate = purchaseDateMillis,
            purchasePrice = pPrice,
            sellingPrice = finalSellingPrice,
            quantity = qty,
            minQty = minQty,
            imageUri = imageUriStr,
            purchaseCurrency = purchaseCurrency,
            sellingCurrency = sellingCurrency,
            onSuccess = { isAccumulated, totalQty ->
                if (isAccumulated) {
                    Toast.makeText(context, "تم تحديث الصنف ($name) وجمع الكمية السابقة والجديدة بنجاح! الكمية الحالية: $totalQty", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "تم حفظ الصنف بنجاح! الكمية: $totalQty", Toast.LENGTH_SHORT).show()
                }
                
                if (isSaveAndNewRequested) {
                    // Reset item fields
                    name = ""
                    barcode = ""
                    purchasePriceStr = ""
                    sellingPriceStr = ""
                    quantityStr = ""
                    imageUriStr = null
                    supplierCompanyNameInput = ""
                    selectedCompany = null
                    companyPhone = ""
                    individualSupplierName = ""
                    individualSupplierPhone = ""
                    individualSupplierNotes = ""
                    
                    // Reset errors
                    nameError = null
                    supplierCompanyError = null
                    individualSupplierError = null
                    purchasePriceError = null
                    sellingPriceError = null
                    quantityError = null
                    minQuantityError = null
                } else {
                    onNavigateBack()
                }
            }
        )
    }

    val validateAndSave: (Boolean) -> Unit = { saveAndNew ->
        isSaveAndNewRequested = saveAndNew
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
            if (supplierCompanyNameInput.trim().isBlank()) { supplierCompanyError = "يرجى إدخال اسم شركة التوريد"; hasError = true }
        } else {
            if (individualSupplierName.trim().isBlank()) { individualSupplierError = "يرجى إدخال اسم المورد الفردي"; hasError = true }
        }

        val pPrice = purchasePriceStr.toDoubleOrNull()
        if (pPrice == null || pPrice < 0) { purchasePriceError = "يرجى إدخال سعر شراء صحيح (صفر أو أكثر)"; hasError = true }
        
        val sPrice = sellingPriceStr.toDoubleOrNull()
        if (sellingPriceStr.isNotBlank() && (sPrice == null || sPrice < 0)) {
            sellingPriceError = "يرجى إدخال سعر بيع صحيح أو ترك الخانة فارغة"
            hasError = true
        }
        
        val qty = quantityStr.toIntOrNull()
        if (qty == null || qty < 0) { quantityError = "يرجى إدخال كمية صحيحة (صفر أو أكثر)"; hasError = true }
        
        val minQty = minQuantityStr.toIntOrNull()
        if (minQty == null || minQty < 0) { minQuantityError = "يرجى إدخال الحد الأدنى صحيح (صفر أو أكثر)"; hasError = true }

        if (hasError) {
            Toast.makeText(context, "يجب تعبئة البيانات الإجبارية", Toast.LENGTH_SHORT).show()
        } else {
            if (sellingPriceStr.trim().isBlank()) {
                showSkipSellingPriceDialog = true
            } else {
                performSave(sPrice ?: 0.0)
            }
        }
    }

    if (showSkipSellingPriceDialog) {
        AlertDialog(
            onDismissRequest = { showSkipSellingPriceDialog = false },
            modifier = Modifier.fillMaxWidth(0.92f).widthIn(max = 520.dp),
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
            icon = { Icon(Icons.Default.HelpOutline, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("تأكيد تخطي سعر البيع", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "لم تقم بتحديد سعر بيع لهذا الصنف (${name.ifBlank { "الصنف" }}).\n\nهل تريد المتابعة وحفظ الصنف بدون سعر بيع؟ (يمكنك تحديده لاحقاً)",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSkipSellingPriceDialog = false
                        performSave(0.0)
                    },
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text("نعم، حفظ بدون سعر بيع", fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showSkipSellingPriceDialog = false },
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text("إلغاء لتحديد السعر", fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false)
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("إضافة صنف جديد", fontWeight = FontWeight.Bold) },
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
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { validateAndSave(true) },
                        modifier = Modifier.weight(1f).height(50.dp),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("حفظ وإضافة آخر", fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, softWrap = false)
                    }
                    Button(
                        onClick = { validateAndSave(false) },
                        modifier = Modifier.weight(1f).height(50.dp),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("حفظ وخروج", fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, softWrap = false)
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            
            // Image Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (isProcessingImage) {
                        Box(
                            modifier = Modifier
                                .size(130.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(8.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(32.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("جاري معالجة وضغط الصورة...", fontSize = 11.sp, textAlign = TextAlign.Center)
                            }
                        }
                    } else if (imageUriStr != null) {
                        Box(
                            modifier = Modifier
                                .size(130.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                                .clickable { showImagePreviewDialog = true },
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(imageUriStr)
                                    .size(300)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "صورة الصنف",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            // Zoom button overlay
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.65f))
                                    .clickable { showImagePreviewDialog = true }
                                    .padding(5.dp)
                            ) {
                                Icon(Icons.Default.ZoomIn, contentDescription = "معاينة وتكبير", modifier = Modifier.size(16.dp), tint = Color.White)
                            }
                            // Edit photo button
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(4.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                                    .clickable { photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                                    .padding(6.dp)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "تغيير الصورة", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                            // Delete photo button (triggers warning)
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(4.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f))
                                    .clickable { showDeleteImageWarning = true }
                                    .padding(6.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "حذف الصورة", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                            }
                        }
                        Text("انقر على الصورة لمعاينتها وتكبيرها بالتفصيل (Zoom)", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
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
                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                            modifier = formNavigator.fieldModifier("item_name", Modifier.fillMaxWidth()),
                            singleLine = true,
                            keyboardOptions = formNavigator.keyboardOptions("item_name", KeyboardType.Text),
                            keyboardActions = formNavigator.keyboardActions("item_name")
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
                                Column(modifier = Modifier.verticalScroll(scrollState)) {
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
                                                    formNavigator.next("item_name")
                                                }
                                                .padding(8.dp),
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
                        modifier = formNavigator.fieldModifier("item_barcode", Modifier.fillMaxWidth()),
                        singleLine = true,
                        keyboardOptions = formNavigator.keyboardOptions("item_barcode", KeyboardType.Text),
                        keyboardActions = formNavigator.keyboardActions("item_barcode")
                    )
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SearchableStringField(
                            value = selectedCategory,
                            onValueChange = { selectedCategory = it },
                            options = recentCategories,
                            label = "التصنيف *",
                            icon = Icons.Default.Category,
                            onAddNew = { typedName ->
                                pendingCategoryName = typedName
                                showAddCategoryDialog = true
                            },
                            addNewLabel = { "إضافة \"$it\" كتصنيف جديد" },
                            keyboardOptions = formNavigator.keyboardOptions("item_category", KeyboardType.Text),
                            keyboardActions = formNavigator.keyboardActions("item_category"),
                            onSelected = { formNavigator.next("item_category") },
                            modifier = formNavigator.fieldModifier("item_category", Modifier.weight(1f))
                        )
                        
                        SearchableStringField(
                            value = selectedUnit,
                            onValueChange = { selectedUnit = it },
                            options = recentUnits,
                            label = "الوحدة *",
                            icon = Icons.Default.Inventory2,
                            onAddNew = { typedName ->
                                pendingUnitName = typedName
                                showAddUnitDialog = true
                            },
                            addNewLabel = { "إضافة \"$it\" كوحدة جديدة" },
                            keyboardOptions = formNavigator.keyboardOptions("item_unit", KeyboardType.Text),
                            keyboardActions = formNavigator.keyboardActions("item_unit"),
                            onSelected = { formNavigator.next("item_unit") },
                            modifier = formNavigator.fieldModifier("item_unit", Modifier.weight(1f))
                        )
                    }
                }
            }

            if (existingMatchedItem != null) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Column {
                            Text(
                                text = "تنبيه: صنف مطابق مسجل مسبقاً بنفس الاسم والتصنيف والوحدة والمورد",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "الرصيد الحالي بالمخزن: ${existingMatchedItem.quantity} ${existingMatchedItem.unit} • سيتم دمج وتحديث الكمية الجديدة تلقائياً عند الحفظ.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            // Supplier Section
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("المورد", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                        SearchableSupplierField(
                            value = supplierCompanyNameInput,
                            onValueChange = { input -> 
                                supplierCompanyNameInput = input
                                supplierCompanyError = null
                                val matched = companiesList.find { it.name.equals(input, ignoreCase = true) }
                                if (matched != null) {
                                    selectedCompany = matched
                                } else {
                                    selectedCompany = null
                                }
                            },
                            options = companiesList.map { it.name },
                            label = "اسم الشركة *",
                            isError = supplierCompanyError != null,
                            errorMessage = supplierCompanyError,
                            keyboardOptions = formNavigator.keyboardOptions("item_supplier_name", KeyboardType.Text),
                            keyboardActions = formNavigator.keyboardActions("item_supplier_name"),
                            onSelected = { formNavigator.next("item_supplier_name") },
                            onAddNew = { typedName ->
                                pendingCompanyName = typedName
                                showAddCompanyDialog = true
                            },
                            modifier = formNavigator.fieldModifier("item_supplier_name", Modifier.fillMaxWidth())
                        )

                        // Company Phone field
                        OutlinedTextField(
                            value = companyPhone,
                            onValueChange = { 
                                val old = companyPhone
                                companyPhone = it
                                formNavigator.checkPhoneAutoAdvance("item_supplier_phone", old, it)
                            },
                            label = { Text("رقم الشركة") },
                            placeholder = { Text("رقم هاتف الشركة / التوريد") },
                            leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            trailingIcon = {
                                IconButton(onClick = {
                                    contactPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                                }) {
                                    Icon(Icons.Default.Contacts, contentDescription = "اختيار من جهات الاتصال", tint = MaterialTheme.colorScheme.primary)
                                }
                            },
                            modifier = formNavigator.fieldModifier("item_supplier_phone", Modifier.fillMaxWidth()),
                            singleLine = true,
                            keyboardOptions = formNavigator.keyboardOptions("item_supplier_phone", KeyboardType.Phone),
                            keyboardActions = formNavigator.keyboardActions("item_supplier_phone")
                        )
                    } else {
                        SearchableSupplierField(
                            value = individualSupplierName,
                            onValueChange = { 
                                individualSupplierName = it
                                individualSupplierError = null
                                val matchedItem = allItemsList.find { item -> item.individualSupplierName.equals(it, ignoreCase = true) && item.individualSupplierPhone.isNotBlank() }
                                if (matchedItem != null) {
                                    individualSupplierPhone = matchedItem.individualSupplierPhone
                                }
                            },
                            options = individualSuppliersList,
                            label = "اسم المورد *",
                            isError = individualSupplierError != null,
                            errorMessage = individualSupplierError,
                            keyboardOptions = formNavigator.keyboardOptions("item_supplier_name", KeyboardType.Text),
                            keyboardActions = formNavigator.keyboardActions("item_supplier_name"),
                            onSelected = { formNavigator.next("item_supplier_name") },
                            onAddNew = { typedName ->
                                individualSupplierName = typedName
                                if (!localAddedIndividualSuppliers.contains(typedName)) {
                                    localAddedIndividualSuppliers.add(typedName)
                                }
                                individualSupplierError = null
                            },
                            modifier = formNavigator.fieldModifier("item_supplier_name", Modifier.fillMaxWidth())
                        )
                        OutlinedTextField(
                            value = individualSupplierPhone,
                            onValueChange = { 
                                val old = individualSupplierPhone
                                individualSupplierPhone = it
                                formNavigator.checkPhoneAutoAdvance("item_supplier_phone", old, it)
                            },
                            label = { Text("رقم الهاتف") },
                            trailingIcon = {
                                IconButton(onClick = {
                                    contactPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                                }) {
                                    Icon(Icons.Default.Contacts, contentDescription = "اختيار من جهات الاتصال", tint = MaterialTheme.colorScheme.primary)
                                }
                            },
                            modifier = formNavigator.fieldModifier("item_supplier_phone", Modifier.fillMaxWidth()),
                            singleLine = true,
                            keyboardOptions = formNavigator.keyboardOptions("item_supplier_phone", KeyboardType.Phone),
                            keyboardActions = formNavigator.keyboardActions("item_supplier_phone")
                        )
                    }
                }
            }

            // Financial & Stock Section
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("الشراء والبيع والمخزون والعملات", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

                    // عملة الشراء
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("عملة الشراء:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            currencyOptions.forEach { curr ->
                                val compactLabel = FormatUtils.formatCurrencyCompact(curr)
                                FilterChip(
                                    selected = purchaseCurrency == curr,
                                    onClick = { 
                                        purchaseCurrency = curr
                                    },
                                    label = { 
                                        Text(
                                            compactLabel, 
                                            fontSize = 11.sp, 
                                            fontWeight = if (purchaseCurrency == curr) FontWeight.Bold else FontWeight.Normal,
                                            maxLines = 1,
                                            softWrap = false
                                        ) 
                                    }
                                )
                            }
                        }
                    }

                    // عملة البيع
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("عملة البيع:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            currencyOptions.forEach { curr ->
                                val compactLabel = FormatUtils.formatCurrencyCompact(curr)
                                FilterChip(
                                    selected = sellingCurrency == curr,
                                    onClick = { sellingCurrency = curr },
                                    label = { 
                                        Text(
                                            compactLabel, 
                                            fontSize = 11.sp, 
                                            fontWeight = if (sellingCurrency == curr) FontWeight.Bold else FontWeight.Normal,
                                            maxLines = 1,
                                            softWrap = false
                                        ) 
                                    }
                                )
                            }
                        }
                    }
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = purchasePriceStr,
                            onValueChange = { purchasePriceStr = it; purchasePriceError = null },
                            label = { Text("سعر الشراء (${FormatUtils.formatCurrencyCompact(purchaseCurrency)}) *") },
                            modifier = formNavigator.fieldModifier("item_purchase_price", Modifier.weight(1f)),
                            keyboardOptions = formNavigator.keyboardOptions("item_purchase_price", KeyboardType.Decimal),
                            keyboardActions = formNavigator.keyboardActions("item_purchase_price"),
                            isError = purchasePriceError != null,
                            supportingText = purchasePriceError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } }
                        )
                        OutlinedTextField(
                            value = sellingPriceStr,
                            onValueChange = { sellingPriceStr = it; sellingPriceError = null },
                            label = { Text("سعر البيع (${FormatUtils.formatCurrencyCompact(sellingCurrency)})") },
                            placeholder = { Text("0.0") },
                            modifier = formNavigator.fieldModifier("item_selling_price", Modifier.weight(1f)),
                            keyboardOptions = formNavigator.keyboardOptions("item_selling_price", KeyboardType.Decimal),
                            keyboardActions = formNavigator.keyboardActions("item_selling_price"),
                            isError = sellingPriceError != null,
                            supportingText = sellingPriceError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } }
                        )
                    }
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = quantityStr,
                            onValueChange = { quantityStr = it; quantityError = null },
                            label = { Text("الكمية الحالية *") },
                            modifier = formNavigator.fieldModifier("item_quantity", Modifier.weight(1f)),
                            keyboardOptions = formNavigator.keyboardOptions("item_quantity", KeyboardType.Number),
                            keyboardActions = formNavigator.keyboardActions("item_quantity"),
                            isError = quantityError != null,
                            supportingText = quantityError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } }
                        )
                        OutlinedTextField(
                            value = minQuantityStr,
                            onValueChange = { minQuantityStr = it; minQuantityError = null },
                            label = { Text("الحد الأدنى للتنبيه *") },
                            modifier = formNavigator.fieldModifier("item_min_quantity", Modifier.weight(1f)),
                            keyboardOptions = formNavigator.keyboardOptions("item_min_quantity", KeyboardType.Number),
                            keyboardActions = formNavigator.keyboardActions("item_min_quantity", onDone = { validateAndSave(false) }),
                            isError = minQuantityError != null,
                            supportingText = minQuantityError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } }
                        )
                    }

                    // Purchase/Sale Totals Display
                    val parsedQty = quantityStr.toIntOrNull() ?: 0
                    val parsedPurchase = purchasePriceStr.toDoubleOrNull() ?: 0.0
                    val parsedSale = sellingPriceStr.toDoubleOrNull() ?: 0.0
                    if (parsedQty > 0) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (parsedPurchase > 0) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("إجمالي تكلفة الشراء:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("${FormatUtils.formatAmount(parsedPurchase * parsedQty)}\u00A0$purchaseCurrency", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                                    }
                                }
                                if (parsedSale > 0) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("إجمالي قيمة البيع المتوقعة:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("${FormatUtils.formatAmount(parsedSale * parsedQty)}\u00A0$sellingCurrency", fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                                    }
                                }
                            }
                        }
                    }
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDatePickerDialog = true }
                    ) {
                        OutlinedTextField(
                            value = SimpleDateFormat("yyyy-MM-dd", Locale("ar")).format(Date(purchaseDateMillis)),
                            onValueChange = { },
                            readOnly = true,
                            enabled = false,
                            label = { Text("تاريخ الشراء") },
                            trailingIcon = { Icon(Icons.Default.DateRange, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            colors = OutlinedTextFieldDefaults.colors(
                                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                disabledBorderColor = MaterialTheme.colorScheme.outline,
                                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                disabledTrailingIconColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(70.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchableStringField(
    value: String,
    onValueChange: (String) -> Unit,
    options: List<String>,
    label: String,
    icon: ImageVector,
    isError: Boolean = false,
    errorMessage: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: androidx.compose.foundation.text.KeyboardActions = androidx.compose.foundation.text.KeyboardActions.Default,
    onSelected: (() -> Unit)? = null,
    onAddNew: (String) -> Unit,
    addNewLabel: (String) -> String,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    // If empty or already selected an existing option, show all options so the user can easily scroll and switch.
    // If typing a custom query, filter options.
    val filteredOptions = remember(value, options) {
        if (value.isBlank() || options.any { it.equals(value.trim(), ignoreCase = true) }) {
            options
        } else {
            val matched = options.filter { it.contains(value.trim(), ignoreCase = true) }
            if (matched.isNotEmpty()) matched else options
        }
    }

    val exactMatchExists = remember(value, options) {
        options.any { it.trim().equals(value.trim(), ignoreCase = true) }
    }

    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            if (interaction is androidx.compose.foundation.interaction.PressInteraction.Release) {
                expanded = true
            }
        }
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = { 
                onValueChange(it)
                expanded = true
            },
            label = { Text(label) },
            leadingIcon = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (value.isNotEmpty()) {
                        IconButton(onClick = { 
                            onValueChange("")
                            expanded = true
                        }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Clear, contentDescription = "مسح", modifier = Modifier.size(16.dp))
                        }
                    }
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                }
            },
            isError = isError,
            supportingText = errorMessage?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
            interactionSource = interactionSource,
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
                .scrollToTopOnFocus()
                .onFocusChanged { focusState ->
                    if (focusState.isFocused) {
                        expanded = true
                    }
                },
            singleLine = true,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .heightIn(max = 240.dp)
                .verticalScroll(rememberScrollState())
        ) {
            filteredOptions.forEach { option ->
                val isSelected = option.equals(value.trim(), ignoreCase = true)
                DropdownMenuItem(
                    text = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = option,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                fontSize = 14.sp
                            )
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    },
                    onClick = {
                        onValueChange(option)
                        expanded = false
                        onSelected?.invoke()
                    }
                )
            }

            if (!exactMatchExists && value.isNotBlank()) {
                HorizontalDivider()
                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = addNewLabel(value.trim()),
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    },
                    onClick = {
                        expanded = false
                        onAddNew(value.trim())
                    }
                )
            }
        }
    }
}

@Composable
fun SearchableSupplierField(
    value: String,
    onValueChange: (String) -> Unit,
    options: List<String>,
    label: String,
    isError: Boolean = false,
    errorMessage: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: androidx.compose.foundation.text.KeyboardActions = androidx.compose.foundation.text.KeyboardActions.Default,
    onSelected: (() -> Unit)? = null,
    onAddNew: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    
    val filteredOptions = remember(value, options) {
        if (value.isBlank()) {
            options
        } else {
            options.filter { it.contains(value, ignoreCase = true) }
        }
    }

    val exactMatchExists = remember(value, options) {
        options.any { it.trim().equals(value.trim(), ignoreCase = true) }
    }

    val showSuggestions = isFocused && value.isNotBlank()

    Box(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = { 
                onValueChange(it)
            },
            label = { Text(label) },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (value.isNotEmpty()) {
                        IconButton(onClick = { onValueChange("") }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Clear, contentDescription = "مسح", modifier = Modifier.size(16.dp))
                        }
                    }
                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp))
                }
            },
            isError = isError,
            supportingText = errorMessage?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
            modifier = Modifier
                .fillMaxWidth()
                .scrollToTopOnFocus()
                .onFocusChanged { focusState ->
                    isFocused = focusState.isFocused
                },
            singleLine = true,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions
        )

        // Dropdown suggestions list
        if (showSuggestions) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 65.dp)
                    .heightIn(max = 240.dp)
                    .zIndex(10f),
                shadowElevation = 8.dp,
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 4.dp)
                ) {
                    if (filteredOptions.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "لا توجد نتائج مطابقة",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        HorizontalDivider()
                    } else {
                        filteredOptions.forEach { option ->
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = option,
                                            fontWeight = if (option.equals(value, ignoreCase = true)) FontWeight.Bold else FontWeight.Normal,
                                            color = if (option.equals(value, ignoreCase = true)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                            fontSize = 14.sp
                                        )
                                        if (option.equals(value, ignoreCase = true)) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    onValueChange(option)
                                    isFocused = false
                                    onSelected?.invoke()
                                }
                            )
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))
                        }
                    }

                    // Add new option if not exact match already
                    if (!exactMatchExists && value.isNotBlank()) {
                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AddCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "إضافة \"$value\" كمورد جديد",
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                            },
                            onClick = {
                                isFocused = false
                                onAddNew(value.trim())
                            }
                        )
                    }
                }
            }
        }
    }
}

// Dialog Composable placeholders (AddCategoryDialog, AddUnitDialog, AddCompanyDialog)
@Composable
fun AddCategoryDialog(
    existingCategories: List<String>,
    initialName: String = "",
    onDismiss: () -> Unit,
    onSave: (name: String, isPermanent: Boolean) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var isPermanent by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(0.92f).widthIn(max = 520.dp),
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        title = { Text("إضافة تصنيف جديد", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; errorMessage = null },
                    label = { Text("اسم التصنيف *") },
                    isError = errorMessage != null,
                    supportingText = errorMessage?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    modifier = Modifier.scrollToTopOnFocus().scrollToTopOnFocus().scrollToTopOnFocus().fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.trim().isBlank()) { errorMessage = "يرجى إدخال اسم التصنيف"; return@Button }
                    if (existingCategories.any { it.trim().equals(name.trim(), ignoreCase = true) }) {
                        errorMessage = "هذا التصنيف موجود بالفعل."
                        return@Button
                    }
                    onSave(name.trim(), isPermanent)
                },
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) { Text("حفظ", fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false) }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) { Text("إلغاء", maxLines = 1, softWrap = false) }
        }
    )
}

@Composable
fun AddUnitDialog(
    existingUnits: List<String>,
    initialName: String = "",
    onDismiss: () -> Unit,
    onSave: (name: String, isPermanent: Boolean) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var isPermanent by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(0.92f).widthIn(max = 520.dp),
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        title = { Text("إضافة وحدة جديدة", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; errorMessage = null },
                    label = { Text("اسم الوحدة *") },
                    isError = errorMessage != null,
                    supportingText = errorMessage?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    modifier = Modifier.scrollToTopOnFocus().scrollToTopOnFocus().scrollToTopOnFocus().fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.trim().isBlank()) { errorMessage = "يرجى إدخال اسم الوحدة"; return@Button }
                    if (existingUnits.any { it.trim().equals(name.trim(), ignoreCase = true) }) {
                        errorMessage = "هذه الوحدة موجودة بالفعل."
                        return@Button
                    }
                    onSave(name.trim(), isPermanent)
                },
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) { Text("حفظ", fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false) }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) { Text("إلغاء", maxLines = 1, softWrap = false) }
        }
    )
}

@Composable
fun AddCompanyDialog(
    existingCompanies: List<String>,
    initialName: String = "",
    onDismiss: () -> Unit,
    onSave: (name: String, phone: String, address: String, notes: String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(0.92f).widthIn(max = 520.dp),
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        title = { Text("إضافة شركة جديدة", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; errorMessage = null },
                    label = { Text("اسم الشركة *") },
                    isError = errorMessage != null,
                    supportingText = errorMessage?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    modifier = Modifier.scrollToTopOnFocus().scrollToTopOnFocus().scrollToTopOnFocus().fillMaxWidth()
                )
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("الهاتف") }, modifier = Modifier.scrollToTopOnFocus().scrollToTopOnFocus().scrollToTopOnFocus().fillMaxWidth())
                OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("العنوان") }, modifier = Modifier.scrollToTopOnFocus().scrollToTopOnFocus().scrollToTopOnFocus().fillMaxWidth())
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("ملاحظات") }, modifier = Modifier.scrollToTopOnFocus().scrollToTopOnFocus().scrollToTopOnFocus().fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.trim().isBlank()) { errorMessage = "يرجى إدخال اسم الشركة"; return@Button }
                    if (existingCompanies.any { it.trim().equals(name.trim(), ignoreCase = true) }) {
                        errorMessage = "هذه الشركة موجودة بالفعل."
                        return@Button
                    }
                    onSave(name.trim(), phone.trim(), address.trim(), notes.trim())
                },
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) { Text("حفظ", fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false) }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) { Text("إلغاء", maxLines = 1, softWrap = false) }
        }
    )
}
