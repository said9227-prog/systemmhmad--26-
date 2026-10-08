package com.example.ui.screens

import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.util.scrollToTopOnFocus
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Client
import com.example.data.model.Installment
import com.example.data.model.Invoice
import com.example.ui.viewmodel.AppViewModel
import com.example.util.DateTimeUtils
import com.example.util.FormatUtils
import com.example.util.SoundHelper
import java.util.Calendar

/**
 * شاشة إعدادات منبّه الأقساط (Alarm Profile + Reminder Schedule + Persistent Reminder)
 * مصممة وفق أعلى معايير الجودة والإنتاجية:
 * 1. حالة المنبّه
 * 2. موعد المنبّه (تاريخ + وقت + تكرار)
 * 3. نغمة المنبّه (منتقي أندرويد الأصلي + معاينة تشغيل/إيقاف) والاهتزاز
 * 4. نوع التنبيه (عام / عميل محدد مع قراءة مباشرة ودقيقة من قاعدة البيانات)
 * 5. تجربة المنبّه (دون التأثير على بيانات الأقساط)
 * 6. حفظ وجدولة المنبّه (إلغاء القديم -> حفظ الإعدادات -> جدولة الجديد)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstallmentAlarmSettingsScreen(
    viewModel: AppViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val settings by viewModel.storeSettings.collectAsState()
    val clients by viewModel.clients.collectAsState()
    val invoices by viewModel.invoices.collectAsState()
    val installments by viewModel.installments.collectAsState()

    // Clean up ringtone preview when screen is exited
    DisposableEffect(Unit) {
        onDispose {
            SoundHelper.stopPreview()
        }
    }

    // --- State loaded from persistent settings ---
    var isAlarmEnabled by remember(settings.isGeneralInstallmentReminderEnabled, settings.isCustomerInstallmentReminderEnabled) {
        mutableStateOf(settings.isGeneralInstallmentReminderEnabled || settings.isCustomerInstallmentReminderEnabled)
    }

    // Alarm Type: "general" (تنبيه أقساط عام) or "customer" (تنبيه قسط لعميل محدد)
    var alarmType by remember {
        mutableStateOf(if (settings.isCustomerInstallmentReminderEnabled && !settings.isGeneralInstallmentReminderEnabled) "customer" else "general")
    }

    // Date state
    val defaultDateMillis = remember(settings.generalAlarmDate) {
        if (settings.generalAlarmDate > 0) settings.generalAlarmDate else System.currentTimeMillis()
    }
    var alarmDateMillis by remember(defaultDateMillis) { mutableStateOf(defaultDateMillis) }

    // Time state (hour, minute)
    var alarmHour by remember(settings.generalReminderHour) { mutableStateOf(settings.generalReminderHour) }
    var alarmMinute by remember(settings.generalReminderMinute) { mutableStateOf(settings.generalReminderMinute) }

    // Recurrence
    var recurrence by remember(settings.generalAlarmRecurrence) {
        mutableStateOf(settings.generalAlarmRecurrence.ifBlank { "مرة واحدة" })
    }
    var recurrenceMenuExpanded by remember { mutableStateOf(false) }

    // Sound & Vibration Profile
    var soundUri by remember(settings.generalSoundUri) { mutableStateOf(settings.generalSoundUri) }
    var soundTitle by remember(settings.generalSoundTitle) {
        mutableStateOf(settings.generalSoundTitle.ifBlank { "نغمة النظام الافتراضية" })
    }
    var isVibrationEnabled by remember(settings.generalVibrationEnabled) { mutableStateOf(settings.generalVibrationEnabled) }

    // Preview Playing state
    var isPlayingPreview by remember { mutableStateOf(false) }

    // General scope and thresholds
    var generalScope by remember(settings.generalReminderScope) { mutableStateOf(settings.generalReminderScope) }
    var daysBeforeDue by remember(settings.generalReminderDaysBeforeDue) { mutableStateOf(settings.generalReminderDaysBeforeDue.toString()) }
    var daysAfterOverdue by remember(settings.generalReminderDaysAfterOverdue) { mutableStateOf(settings.generalReminderDaysAfterOverdue.toString()) }

    // Customer selection state for "customer" alarm type
    var selectedCustomer by remember { mutableStateOf<Client?>(null) }
    var selectedInvoiceId by remember { mutableStateOf<Int?>(null) }
    var selectedInstallmentId by remember { mutableStateOf<Int?>(null) }
    var customerSearchQuery by remember { mutableStateOf("") }
    var showCustomerPickerSheet by remember { mutableStateOf(false) }

    // Compute dynamic financial remaining amount directly from Room DB state
    val customerUnpaidInvoices = remember(selectedCustomer, invoices) {
        val cid = selectedCustomer?.id ?: return@remember emptyList<Invoice>()
        invoices.filter { it.clientId == cid && !it.isDraft && it.remainingAmount > 0.01 }
    }
    val customerUnpaidInstallments = remember(selectedCustomer, installments) {
        val cid = selectedCustomer?.id ?: return@remember emptyList<Installment>()
        installments.filter { it.clientId == cid && !it.isPaid && (it.amount - it.paidAmount) > 0.01 }
    }

    val dynamicRemainingAmount = remember(selectedCustomer, selectedInvoiceId, selectedInstallmentId, customerUnpaidInvoices, customerUnpaidInstallments) {
        if (selectedCustomer == null) 0.0
        else if (selectedInstallmentId != null && selectedInstallmentId != 0) {
            val inst = customerUnpaidInstallments.firstOrNull { it.id == selectedInstallmentId }
            inst?.let { (it.amount - it.paidAmount).coerceAtLeast(0.0) } ?: selectedCustomer!!.balance.coerceAtLeast(0.0)
        } else if (selectedInvoiceId != null && selectedInvoiceId != 0) {
            val inv = customerUnpaidInvoices.firstOrNull { it.id == selectedInvoiceId }
            inv?.remainingAmount ?: selectedCustomer!!.balance.coerceAtLeast(0.0)
        } else {
            selectedCustomer!!.balance.coerceAtLeast(0.0)
        }
    }

    // General summary count from latest DB state
    val now = System.currentTimeMillis()
    val startOfToday = remember(now) { DateTimeUtils.getStartOfDay(now) }
    val endOfToday = remember(now) { DateTimeUtils.getEndOfDay(now) }
    val activeDueInstallments = remember(installments, startOfToday, endOfToday, generalScope) {
        installments.filter { !it.isPaid }
    }
    val generalSummaryCount = remember(activeDueInstallments) { activeDueInstallments.size }
    val generalSummaryTotal = remember(activeDueInstallments) {
        activeDueInstallments.sumOf { (it.amount - it.paidAmount).coerceAtLeast(0.0) }
    }

    // Native Android Ringtone Picker Launcher
    val ringtonePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }
            val uriString = uri?.toString() ?: ""
            soundUri = uriString
            soundTitle = SoundHelper.getRingtoneTitle(context, uriString)
            SoundHelper.stopPreview()
            isPlayingPreview = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Alarm,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("إعدادات منبّه الأقساط", fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            saveAlarmProfile(
                                viewModel = viewModel,
                                isAlarmEnabled = isAlarmEnabled,
                                alarmType = alarmType,
                                alarmDateMillis = alarmDateMillis,
                                alarmHour = alarmHour,
                                alarmMinute = alarmMinute,
                                recurrence = recurrence,
                                soundUri = soundUri,
                                soundTitle = soundTitle,
                                isVibrationEnabled = isVibrationEnabled,
                                generalScope = generalScope,
                                daysBeforeDue = daysBeforeDue.toIntOrNull() ?: 1,
                                daysAfterOverdue = daysAfterOverdue.toIntOrNull() ?: 3,
                                selectedCustomer = selectedCustomer,
                                selectedInvoiceId = selectedInvoiceId,
                                selectedInstallmentId = selectedInstallmentId,
                                remainingAmount = dynamicRemainingAmount,
                                currency = settings.currency,
                                context = context,
                                onCompleted = onNavigateBack
                            )
                        },
                        modifier = Modifier.testTag("topbar_save_alarm_btn")
                    ) {
                        Text("حفظ", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            // 1. حالة المنبّه
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("alarm_status_card"),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isAlarmEnabled) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        else MaterialTheme.colorScheme.surface
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.5.dp,
                        if (isAlarmEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isAlarmEnabled) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isAlarmEnabled) Icons.Default.AlarmOn else Icons.Default.AlarmOff,
                                    contentDescription = null,
                                    tint = if (isAlarmEnabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "حالة المنبّه",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (isAlarmEnabled) "● مفعّل وجاهز للإطلاق في الموعد" else "○ متوقف حالياً",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isAlarmEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (isAlarmEnabled) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                        Switch(
                            checked = isAlarmEnabled,
                            onCheckedChange = { isAlarmEnabled = it },
                            modifier = Modifier.testTag("alarm_status_switch")
                        )
                    }
                }
            }

            // 2. موعد المنبّه (التاريخ، الوقت، التكرار)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Event, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("موعد المنبّه", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        HorizontalDivider()

                        // 📅 التاريخ
                        OutlinedCard(
                            onClick = {
                                val cal = Calendar.getInstance().apply { timeInMillis = alarmDateMillis }
                                DatePickerDialog(
                                    context,
                                    { _, y, m, d ->
                                        cal.set(y, m, d)
                                        alarmDateMillis = cal.timeInMillis
                                    },
                                    cal.get(Calendar.YEAR),
                                    cal.get(Calendar.MONTH),
                                    cal.get(Calendar.DAY_OF_MONTH)
                                ).show()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("pick_alarm_date_btn"),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Column {
                                        Text("📅 التاريخ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(
                                            text = DateTimeUtils.formatDateOnly(alarmDateMillis),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Icon(Icons.Default.EditCalendar, contentDescription = "تغيير التاريخ", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        // ⏰ الوقت
                        OutlinedCard(
                            onClick = {
                                TimePickerDialog(
                                    context,
                                    { _, h, m ->
                                        alarmHour = h
                                        alarmMinute = m
                                    },
                                    alarmHour,
                                    alarmMinute,
                                    false
                                ).show()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("pick_alarm_time_btn"),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Column {
                                        Text("⏰ الوقت", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        val amPm = if (alarmHour < 12) "صباحًا (ص)" else "مساءً (م)"
                                        val displayHour = if (alarmHour % 12 == 0) 12 else alarmHour % 12
                                        val timeStr = String.format("%02d:%02d %s", displayHour, alarmMinute, amPm)
                                        Text(
                                            text = timeStr,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                Icon(Icons.Default.AccessTime, contentDescription = "تغيير الوقت", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        // 🔁 التكرار
                        val recurrenceOptions = listOf("مرة واحدة", "يومي", "أسبوعي", "شهري")
                        ExposedDropdownMenuBox(
                            expanded = recurrenceMenuExpanded,
                            onExpandedChange = { recurrenceMenuExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = recurrence,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("التكرار") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = recurrenceMenuExpanded) },
                                leadingIcon = { Icon(Icons.Default.Repeat, contentDescription = null) },
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth()
                                    .testTag("alarm_recurrence_dropdown"),
                                shape = RoundedCornerShape(8.dp)
                            )
                            ExposedDropdownMenu(
                                expanded = recurrenceMenuExpanded,
                                onDismissRequest = { recurrenceMenuExpanded = false }
                            ) {
                                recurrenceOptions.forEach { opt ->
                                    DropdownMenuItem(
                                        text = { Text(opt, fontWeight = if (opt == recurrence) FontWeight.Bold else FontWeight.Normal) },
                                        onClick = {
                                            recurrence = opt
                                            recurrenceMenuExpanded = false
                                        },
                                        leadingIcon = {
                                            if (opt == recurrence) {
                                                Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. نغمة واهتزاز المنبّه
            item {
                Card(
                    modifier = Modifier.scrollToTopOnFocus().scrollToTopOnFocus().fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("نغمة واهتزاز المنبّه", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        HorizontalDivider()

                        // 🎵 اختيار النغمة عبر منتقي أندرويد الأصلي
                        OutlinedCard(
                            onClick = {
                                try {
                                    val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                        putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM or RingtoneManager.TYPE_NOTIFICATION)
                                        putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "اختر نغمة منبّه الأقساط")
                                        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                                        if (soundUri.isNotBlank()) {
                                            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(soundUri))
                                        }
                                    }
                                    ringtonePickerLauncher.launch(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "تعذر فتح منتقي النغمات في النظام", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("pick_sound_btn"),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Column {
                                        Text("🎵 نغمة المنبّه", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(
                                            text = soundTitle,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1
                                        )
                                    }
                                }
                                Icon(Icons.Default.ChevronLeft, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        // 🔊 معاينة النغمة
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.VolumeUp, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text("🔊 معاينة النغمة", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            }

                            FilledTonalButton(
                                onClick = {
                                    if (isPlayingPreview) {
                                        SoundHelper.stopPreview()
                                        isPlayingPreview = false
                                    } else {
                                        SoundHelper.playPreview(context, soundUri)
                                        isPlayingPreview = true
                                    }
                                },
                                modifier = Modifier.testTag("toggle_preview_audio_btn")
                            ) {
                                Icon(
                                    imageVector = if (isPlayingPreview) Icons.Default.Stop else Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (isPlayingPreview) "إيقاف" else "تشغيل")
                            }
                        }

                        HorizontalDivider()

                        // 📳 الاهتزاز
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Vibration, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Column {
                                    Text("📳 الاهتزاز", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Text("تفعيل نمط الاهتزاز عند إطلاق التنبيه", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (isVibrationEnabled) {
                                    IconButton(
                                        onClick = { SoundHelper.vibratePreview(context) },
                                        modifier = Modifier.testTag("test_vibration_btn")
                                    ) {
                                        Icon(Icons.Default.Sensors, contentDescription = "تجربة الاهتزاز", tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                Switch(
                                    checked = isVibrationEnabled,
                                    onCheckedChange = { isVibrationEnabled = it },
                                    modifier = Modifier.testTag("vibration_switch")
                                )
                            }
                        }
                    }
                }
            }

            // 4. نوع التنبيه (عام / عميل محدد)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("🔔 نوع التنبيه", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        HorizontalDivider()

                        // الخيار الأول: تنبيه أقساط عام
                        Surface(
                            onClick = { alarmType = "general" },
                            shape = RoundedCornerShape(8.dp),
                            color = if (alarmType == "general") MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.5.dp,
                                if (alarmType == "general") MaterialTheme.colorScheme.primary else Color.Transparent
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("select_general_alarm_type")
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = alarmType == "general",
                                    onClick = { alarmType = "general" }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text("○ تنبيه أقساط عام", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                                    Text("إشعار ملخص شامل بكافة الأقساط المستحقة أو المتأخرة", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }

                        // الخيار الثاني: تنبيه قسط لعميل محدد
                        Surface(
                            onClick = { alarmType = "customer" },
                            shape = RoundedCornerShape(8.dp),
                            color = if (alarmType == "customer") MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.5.dp,
                                if (alarmType == "customer") MaterialTheme.colorScheme.primary else Color.Transparent
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("select_customer_alarm_type")
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = alarmType == "customer",
                                    onClick = { alarmType = "customer" }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text("○ تنبيه قسط لعميل محدد", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                                    Text("منبّه مخصص لعميل وفاتورة مع قراءة المبلغ المتبقي تلقائياً", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }

                        // Configuration when "general" is chosen
                        if (alarmType == "general") {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("نطاق التنبيه العام:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    val scopes = listOf("جميع ما سبق", "أقساط مستحقة اليوم", "أقساط متأخرة", "أقساط ستستحق قريبًا")
                                    scopes.forEach { sc ->
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { generalScope = sc }
                                        ) {
                                            RadioButton(selected = generalScope == sc, onClick = { generalScope = sc })
                                            Text(sc, fontSize = 13.sp)
                                        }
                                    }

                                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("الأقساط المشمولة حالياً:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(
                                            "$generalSummaryCount قسط (${FormatUtils.formatAmount(generalSummaryTotal)} ${settings.currency})",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }

                        // Configuration when "customer" is chosen
                        if (alarmType == "customer") {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                // 👤 العميل
                                OutlinedCard(
                                    onClick = { showCustomerPickerSheet = true },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("choose_customer_btn"),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                            Column {
                                                Text("👤 العميل", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text(
                                                    text = selectedCustomer?.name ?: "[ اضغط لاختيار العميل ]",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = if (selectedCustomer != null) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (selectedCustomer != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                        Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                }

                                if (selectedCustomer != null) {
                                    // 📄 الفاتورة / القسط (إذا وجدت)
                                    if (customerUnpaidInvoices.isNotEmpty() || customerUnpaidInstallments.isNotEmpty()) {
                                        var expandedDocMenu by remember { mutableStateOf(false) }
                                        val docLabel = when {
                                            selectedInstallmentId != null -> {
                                                val inst = customerUnpaidInstallments.firstOrNull { it.id == selectedInstallmentId }
                                                "قسط: ${inst?.notes?.ifBlank { "قسط رقم ${inst.id}" } ?: "قسط"}"
                                            }
                                            selectedInvoiceId != null -> {
                                                val inv = customerUnpaidInvoices.firstOrNull { it.id == selectedInvoiceId }
                                                "فاتورة رقم: ${inv?.invoiceNumber}"
                                            }
                                            else -> "رصيد الحساب الإجمالي المستحق"
                                        }

                                        ExposedDropdownMenuBox(
                                            expanded = expandedDocMenu,
                                            onExpandedChange = { expandedDocMenu = it }
                                        ) {
                                            OutlinedTextField(
                                                value = docLabel,
                                                onValueChange = {},
                                                readOnly = true,
                                                label = { Text("📄 الفاتورة / القسط") },
                                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedDocMenu) },
                                                leadingIcon = { Icon(Icons.Default.Receipt, contentDescription = null) },
                                                modifier = Modifier
                                                    .menuAnchor()
                                                    .fillMaxWidth(),
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            ExposedDropdownMenu(
                                                expanded = expandedDocMenu,
                                                onDismissRequest = { expandedDocMenu = false }
                                            ) {
                                                DropdownMenuItem(
                                                    text = { Text("رصيد الحساب الإجمالي للعميل") },
                                                    onClick = {
                                                        selectedInvoiceId = null
                                                        selectedInstallmentId = null
                                                        expandedDocMenu = false
                                                    }
                                                )
                                                customerUnpaidInvoices.forEach { inv ->
                                                    DropdownMenuItem(
                                                        text = { Text("فاتورة #${inv.invoiceNumber} (متبقي: ${FormatUtils.formatAmount(inv.remainingAmount)} ${inv.currency})") },
                                                        onClick = {
                                                            selectedInvoiceId = inv.id
                                                            selectedInstallmentId = null
                                                            expandedDocMenu = false
                                                        }
                                                    )
                                                }
                                                customerUnpaidInstallments.forEach { inst ->
                                                    DropdownMenuItem(
                                                        text = { Text("قسط #${inst.id} (متبقي: ${FormatUtils.formatAmount(inst.amount - inst.paidAmount)} ${inst.currency})") },
                                                        onClick = {
                                                            selectedInstallmentId = inst.id
                                                            selectedInvoiceId = inst.invoiceId
                                                            expandedDocMenu = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // 💰 المبلغ المتبقي (قراءة تلقائية مباشرة من قاعدة البيانات)
                                    Surface(
                                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.scrollToTopOnFocus().scrollToTopOnFocus().fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text("💰 المبلغ المتبقي (قراءة تلقائية)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                                Text(
                                                    text = "${FormatUtils.formatAmount(dynamicRemainingAmount)} ${settings.currency}",
                                                    style = MaterialTheme.typography.titleLarge,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.secondary
                                                )
                                            }
                                            Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 5. 🧪 تجربة المنبّه
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f)
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Science, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                            Text("🧪 تجربة المنبّه", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        }
                        Text(
                            text = "يُطلق إشعارًا ومنبّهًا تجريبيًا فوريًا للتأكد من عمل الصوت والاهتزاز بشكل مستقل تمامًا دون تسجيل أي قسط مالي في قاعدة البيانات.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Button(
                            onClick = {
                                viewModel.triggerTestAlarm(
                                    soundUri = soundUri,
                                    vibrationEnabled = isVibrationEnabled
                                )
                                Toast.makeText(context, "تم إطلاق تنبيه تجريبي! تحقق من الإشعار والصوت.", Toast.LENGTH_LONG).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("trigger_test_alarm_btn"),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.NotificationsActive, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("🔔 تجربة التنبيه الآن", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // 6. 💾 حفظ الإعدادات وجدولة المنبّه
            item {
                Button(
                    onClick = {
                        saveAlarmProfile(
                            viewModel = viewModel,
                            isAlarmEnabled = isAlarmEnabled,
                            alarmType = alarmType,
                            alarmDateMillis = alarmDateMillis,
                            alarmHour = alarmHour,
                            alarmMinute = alarmMinute,
                            recurrence = recurrence,
                            soundUri = soundUri,
                            soundTitle = soundTitle,
                            isVibrationEnabled = isVibrationEnabled,
                            generalScope = generalScope,
                            daysBeforeDue = daysBeforeDue.toIntOrNull() ?: 1,
                            daysAfterOverdue = daysAfterOverdue.toIntOrNull() ?: 3,
                            selectedCustomer = selectedCustomer,
                            selectedInvoiceId = selectedInvoiceId,
                            selectedInstallmentId = selectedInstallmentId,
                            remainingAmount = dynamicRemainingAmount,
                            currency = settings.currency,
                            context = context,
                            onCompleted = onNavigateBack
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("save_and_schedule_alarm_btn"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("💾 حفظ وجدولة المنبّه", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }

    // Customer Picker Bottom Sheet / Dialog
    if (showCustomerPickerSheet) {
        AlertDialog(
            onDismissRequest = { showCustomerPickerSheet = false },
            title = { Text("اختر العميل للمنبّه", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = customerSearchQuery,
                        onValueChange = { customerSearchQuery = it },
                        placeholder = { Text("بحث بالاسم أو الهاتف...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        modifier = Modifier.scrollToTopOnFocus().scrollToTopOnFocus().fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    val filteredClients = clients.filter {
                        it.name.contains(customerSearchQuery, ignoreCase = true) ||
                                it.phone.contains(customerSearchQuery)
                    }

                    if (filteredClients.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("لا يوجد عملاء يطابقون البحث", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(filteredClients.size) { idx ->
                                val c = filteredClients[idx]
                                Surface(
                                    onClick = {
                                        selectedCustomer = c
                                        selectedInvoiceId = null
                                        selectedInstallmentId = null
                                        showCustomerPickerSheet = false
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (selectedCustomer?.id == c.id) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(c.name, fontWeight = FontWeight.Bold)
                                            if (c.phone.isNotBlank()) {
                                                Text(c.phone, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                        Text(
                                            "${FormatUtils.formatAmount(c.balance.coerceAtLeast(0.0))} ${settings.currency}",
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCustomerPickerSheet = false }) {
                    Text("إغلاق")
                }
            }
        )
    }
}

/**
 * Persists the Alarm Profile into Room and schedules AlarmManager.
 * Strictly follows the required pipeline:
 * Old Alarm -> Cancel -> Save Settings to Room -> Schedule New Alarm
 */
private fun saveAlarmProfile(
    viewModel: AppViewModel,
    isAlarmEnabled: Boolean,
    alarmType: String,
    alarmDateMillis: Long,
    alarmHour: Int,
    alarmMinute: Int,
    recurrence: String,
    soundUri: String,
    soundTitle: String,
    isVibrationEnabled: Boolean,
    generalScope: String,
    daysBeforeDue: Int,
    daysAfterOverdue: Int,
    selectedCustomer: Client?,
    selectedInvoiceId: Int?,
    selectedInstallmentId: Int?,
    remainingAmount: Double,
    currency: String,
    context: android.content.Context,
    onCompleted: () -> Unit
) {
    if (alarmType == "customer" && selectedCustomer == null && isAlarmEnabled) {
        Toast.makeText(context, "الرجاء اختيار العميل أولاً", Toast.LENGTH_SHORT).show()
        return
    }

    if (alarmType == "customer" && isAlarmEnabled && selectedCustomer != null) {
        // Schedule customer reminder with alarm profile
        val cal = Calendar.getInstance().apply {
            timeInMillis = alarmDateMillis
            set(Calendar.HOUR_OF_DAY, alarmHour)
            set(Calendar.MINUTE, alarmMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val targetScheduleTime = if (cal.timeInMillis <= System.currentTimeMillis()) {
            System.currentTimeMillis() + 60_000L // 1 minute from now if selected time passed
        } else cal.timeInMillis

        viewModel.createCustomerReminder(
            customerId = selectedCustomer.id,
            customerName = selectedCustomer.name,
            invoiceId = selectedInvoiceId,
            invoiceNumber = null,
            installmentId = selectedInstallmentId,
            amount = remainingAmount,
            remainingAmount = remainingAmount,
            dueDate = targetScheduleTime,
            scheduledAt = targetScheduleTime,
            currency = currency,
            soundUri = soundUri,
            soundTitle = soundTitle,
            vibrationEnabled = isVibrationEnabled,
            recurrence = recurrence,
            onSuccess = {
                // Also update settings profile
                viewModel.updateInstallmentReminderSettings(
                    isGeneralEnabled = false,
                    generalHour = alarmHour,
                    generalMinute = alarmMinute,
                    daysBefore = daysBeforeDue,
                    daysAfter = daysAfterOverdue,
                    scope = generalScope,
                    isCustomerEnabled = true,
                    allowCustomCustomerReminder = true,
                    generalDate = alarmDateMillis,
                    generalRecurrence = recurrence,
                    generalSoundUri = soundUri,
                    generalSoundTitle = soundTitle,
                    generalVibrationEnabled = isVibrationEnabled,
                    customerDefaultHour = alarmHour,
                    customerDefaultMinute = alarmMinute,
                    customerDefaultRecurrence = recurrence,
                    customerSoundUri = soundUri,
                    customerSoundTitle = soundTitle,
                    customerVibrationEnabled = isVibrationEnabled,
                    onSuccess = {
                        Toast.makeText(context, "تم حفظ وجدولة منبّه القسط للعميل بنجاح!", Toast.LENGTH_LONG).show()
                        onCompleted()
                    }
                )
            }
        )
    } else {
        // General installment reminder profile
        viewModel.updateInstallmentReminderSettings(
            isGeneralEnabled = isAlarmEnabled,
            generalHour = alarmHour,
            generalMinute = alarmMinute,
            daysBefore = daysBeforeDue,
            daysAfter = daysAfterOverdue,
            scope = generalScope,
            isCustomerEnabled = false,
            allowCustomCustomerReminder = true,
            generalDate = alarmDateMillis,
            generalRecurrence = recurrence,
            generalSoundUri = soundUri,
            generalSoundTitle = soundTitle,
            generalVibrationEnabled = isVibrationEnabled,
            customerDefaultHour = alarmHour,
            customerDefaultMinute = alarmMinute,
            customerDefaultRecurrence = recurrence,
            customerSoundUri = soundUri,
            customerSoundTitle = soundTitle,
            customerVibrationEnabled = isVibrationEnabled,
            onSuccess = {
                val msg = if (isAlarmEnabled) "تم حفظ وجدولة منبّه الأقساط العام بنجاح!" else "تم إيقاف المنبّه بنجاح"
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                onCompleted()
            }
        )
    }
}
