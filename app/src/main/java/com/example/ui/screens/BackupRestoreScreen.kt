package com.example.ui.screens

import android.app.TimePickerDialog
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BackupHistory
import com.example.data.preferences.BackupSettingsState
import com.example.ui.viewmodel.AppViewModel
import com.example.util.BackupEngine
import com.example.util.BackupPreview
import com.example.util.BackupScheduler
import com.example.util.RestoreEngine
import com.example.util.RestoreResult
import com.example.util.SafStorageManager
import com.example.util.ShareManager
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreScreen(
    viewModel: AppViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    var selectedTabIndex by remember { mutableStateOf(0) }

    val tabs = listOf(
        "النسخ والمزامنة" to Icons.Default.Backup,
        "استعادة البيانات" to Icons.Default.Restore,
        "تصدير التقارير" to Icons.Default.FileDownload,
        "سجل العمليات" to Icons.Default.History
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "النسخ الاحتياطي والتصدير",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "حفظ البيانات، المزامنة السحابية (SAF)، والاسترجاع الموثوق",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "الرجوع"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Distinct Operations Header Bar
            OperationsOverviewBar()

            // Navigation Tabs
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                tabs.forEachIndexed { index, (title, icon) ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = {
                            Text(
                                title,
                                fontSize = 12.sp,
                                fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        icon = { Icon(icon, contentDescription = title, modifier = Modifier.size(20.dp)) }
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp)
            ) {
                when (selectedTabIndex) {
                    0 -> BackupTabContent(viewModel = viewModel, onGoToRestore = { selectedTabIndex = 1 })
                    1 -> RestoreTabContent(viewModel = viewModel)
                    2 -> ExportTabContent(viewModel = viewModel)
                    3 -> HistoryTabContent(viewModel = viewModel)
                }
            }
        }
    }
}

/**
 * Visual Banner explaining the 3 distinct operations available in the system
 */
@Composable
private fun OperationsOverviewBar() {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Default.Inventory2, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                Text("أ. نسخ شامل (ZIP)", fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Text("•", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Default.TableChart, contentDescription = null, tint = Color(0xFF059669), modifier = Modifier.size(14.dp))
                Text("ب. جداول (Excel/CSV)", fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Text("•", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(14.dp))
                Text("ج. تقرير رسمي (PDF)", fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// =========================================================================
// TAB 1: BACKUP & SYNC (SAF, Status, Backup Now, WorkManager Settings)
// =========================================================================
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BackupTabContent(
    viewModel: AppViewModel,
    onGoToRestore: () -> Unit
) {
    val context = LocalContext.current

    val settingsState by viewModel.backupSettingsState.collectAsState()
    val backupInProgress by viewModel.backupInProgress.collectAsState()
    val backupProgressPercent by viewModel.backupProgressPercent.collectAsState()
    val backupStageText by viewModel.backupStageText.collectAsState()

    val isDriveSyncEnabled by viewModel.isDriveSyncEnabled.collectAsState()
    val driveAccountEmail by viewModel.driveAccountEmail.collectAsState()
    val driveBackupStatus by viewModel.driveBackupStatus.collectAsState()
    val lastBackupTimeStr by viewModel.lastBackupTimeStr.collectAsState()

    // SAF Directory Picker Launcher
    val safFolderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.setBackupFolder(uri)
            Toast.makeText(context, "تم ربط مجلد النسخ والمزامنة بنجاح!", Toast.LENGTH_SHORT).show()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // --- 1. SYNCHRONIZATION STATUS & LAST BACKUP CARD ---
        item {
            SyncStatusAndLastBackupCard(settingsState = settingsState)
        }

        // --- 1.5. GOOGLE DRIVE CLOUD SYNC CARD ---
        item {
            GoogleDriveSyncCard(
                viewModel = viewModel
            )
        }

        // --- 2. BACKUP FOLDER CONFIGURATION (SAF) ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.FolderSpecial, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column {
                                Text("مجلد النسخ والمزامنة (SAF)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("يدعم مجلدات الهاتف، بطاقة SD، و Google Drive المتاحة في جهازك", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    val folderName = settingsState.backupFolderName.ifBlank { "لم يتم تحديد مجلد بعد" }
                    Surface(
                        color = if (settingsState.backupFolderUri != null) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else Color(0xFFFEF3C7),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                                Icon(
                                    if (settingsState.backupFolderUri != null) Icons.Default.CloudQueue else Icons.Default.WarningAmber,
                                    contentDescription = null,
                                    tint = if (settingsState.backupFolderUri != null) MaterialTheme.colorScheme.primary else Color(0xFFD97706),
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        text = folderName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                    if (settingsState.backupFolderUri == null) {
                                        Text("يرجى تحديد مجلد للنسخ الاحتياطي أولاً للتمكن من النسخ والمزامنة", fontSize = 10.sp, color = Color(0xFFB45309))
                                    }
                                }
                            }

                            Button(
                                onClick = { safFolderPicker.launch(null) },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.DriveFileMove, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    if (settingsState.backupFolderUri == null) "تحديد المجلد" else "تغيير المجلد",
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- 3. LIVE PROGRESS INDICATOR ---
        if (backupInProgress) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("جاري معالجة وتوليد النسخة الاحتياطية...", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("$backupProgressPercent%", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                        }
                        LinearProgressIndicator(
                            progress = { backupProgressPercent / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                        Text(backupStageText, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        // --- 4. PRIMARY ACTIONS: BACKUP NOW & RESTORE SHORTCUT ---
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // BACKUP NOW
                Button(
                    onClick = {
                        if (settingsState.backupFolderUri == null) {
                            Toast.makeText(context, "يرجى تحديد مجلد للنسخ الاحتياطي أولاً عبر زر تحديد المجلد", Toast.LENGTH_LONG).show()
                            safFolderPicker.launch(null)
                            return@Button
                        }
                        viewModel.executeBackupNow { res ->
                            Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
                        }
                    },
                    enabled = !backupInProgress,
                    modifier = Modifier.weight(1.3f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("بدء النسخ الآن (Backup Now)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                // RESTORE SHORTCUT
                OutlinedButton(
                    onClick = onGoToRestore,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Restore, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("استعادة نسخة", fontSize = 12.sp)
                }
            }
        }

        // --- 5. AUTOMATIC BACKUP SETTINGS (WorkManager, Type, Frequency, Retention) ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (settingsState.isAutoBackupEnabled) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, if (settingsState.isAutoBackupEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                            Column {
                                Text("النسخ الاحتياطي التلقائي (WorkManager)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(
                                    if (settingsState.isAutoBackupEnabled) "مفعل في الخلفية 🟢" else "معطل حالياً ⚪",
                                    fontSize = 11.sp,
                                    color = if (settingsState.isAutoBackupEnabled) Color(0xFF059669) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Switch(
                            checked = settingsState.isAutoBackupEnabled,
                            onCheckedChange = { isChecked ->
                                if (isChecked && settingsState.backupFolderUri == null) {
                                    Toast.makeText(context, "يرجى تحديد مجلد للنسخ الاحتياطي أولاً لتفعيل النسخ التلقائي", Toast.LENGTH_LONG).show()
                                    safFolderPicker.launch(null)
                                }
                                viewModel.setAutoBackupEnabled(isChecked)
                            }
                        )
                    }

                    if (settingsState.isAutoBackupEnabled) {
                        HorizontalDivider()

                        // Automatic Backup Type Selection
                        Text("نوع النسخ التلقائي المراد تنفيذه:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            val types = listOf(
                                Triple("FULL_BACKUP", "نسخ شامل كامل (Full Backup)", "أرشيف ZIP منظم وقابل للاسترجاع لاحقاً"),
                                Triple("CSV", "تصدير جداول Excel (CSV)", "ملفات مفصولة بفواصل مع ترميز UTF-8"),
                                Triple("PDF", "تقرير مالي شامل (PDF)", "مستند رسمي منسق للمراجعة والطباعة")
                            )

                            types.forEach { (typeKey, title, subtitle) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { viewModel.setAutoBackupType(typeKey) }
                                        .padding(horizontal = 4.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = settingsState.backupType.equals(typeKey, ignoreCase = true),
                                        onClick = { viewModel.setAutoBackupType(typeKey) }
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column {
                                        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                        Text(subtitle, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }

                        HorizontalDivider()

                        // Frequency Picker
                        Text("تكرار التشغيل التلقائي:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("DAILY" to "يومياً (كل 24 ساعة)", "WEEKLY" to "أسبوعياً (كل 7 أيام)").forEach { (freqKey, label) ->
                                FilterChip(
                                    selected = settingsState.frequency.equals(freqKey, ignoreCase = true),
                                    onClick = { viewModel.setAutoBackupFrequency(freqKey) },
                                    label = { Text(label, fontSize = 11.sp) }
                                )
                            }
                        }

                        HorizontalDivider()

                        // Scheduled Execution Time Picker (طلب المستخدم: إمكانية ضبط وقت عمل الإجراء في الخلفية)
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Default.AccessTime, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                    Text("وقت عمل الإجراء التلقائي بالخلفية:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                }

                                val hour12 = if (settingsState.backupExecutionHour == 0) 12 else if (settingsState.backupExecutionHour > 12) settingsState.backupExecutionHour - 12 else settingsState.backupExecutionHour
                                val amPm = if (settingsState.backupExecutionHour < 12) "ص" else "م"
                                val minFormatted = String.format(Locale.US, "%02d", settingsState.backupExecutionMinute)

                                OutlinedButton(
                                    onClick = {
                                        TimePickerDialog(
                                            context,
                                            { _, selectedHour, selectedMinute ->
                                                viewModel.setAutoBackupExecutionTime(selectedHour, selectedMinute)
                                                Toast.makeText(context, "تم ضبط وقت النسخ التلقائي في الخلفية", Toast.LENGTH_SHORT).show()
                                            },
                                            settingsState.backupExecutionHour,
                                            settingsState.backupExecutionMinute,
                                            false
                                        ).show()
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "الساعة $hour12:$minFormatted $amPm (تعديل ⏱️)",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            // Popular time quick chips
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val popularHours = listOf(
                                    0 to "12:00 ص",
                                    2 to "02:00 ص (موصى به)",
                                    4 to "04:00 ص",
                                    22 to "10:00 م"
                                )
                                popularHours.forEach { (hr, label) ->
                                    val isSelected = settingsState.backupExecutionHour == hr && settingsState.backupExecutionMinute == 0
                                    SuggestionChip(
                                        onClick = {
                                            viewModel.setAutoBackupExecutionTime(hr, 0)
                                        },
                                        label = {
                                            Text(
                                                label,
                                                fontSize = 10.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                        },
                                        border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
                                    )
                                }
                            }

                            val nextBackupInfo = BackupScheduler.getNextBackupTimeString(
                                hour = settingsState.backupExecutionHour,
                                minute = settingsState.backupExecutionMinute,
                                intervalDays = if (settingsState.frequency.equals("WEEKLY", ignoreCase = true)) 7 else 1
                            )
                            Text(
                                text = "⏰ موعد التنفيذ القادم في الخلفية: $nextBackupInfo",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // Retention Policy Slider
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("الاحتفاظ بآخر نسخ (Retention):", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Text("${settingsState.retentionCount} نسخ", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            Slider(
                                value = settingsState.retentionCount.toFloat(),
                                onValueChange = { viewModel.setBackupRetentionCount(it.toInt()) },
                                valueRange = 3f..30f,
                                steps = 26
                            )
                            Text("يتم حذف النسخ القديمة تلقائياً وفقط بعد اكتمال النسخة الجديدة بنجاح", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        // Background Rules Notice (Requirement 10, 11, 12)
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                Text(
                                    text = "ملاحظة موثوقية نظام Android: يعمل النسخ التلقائي بالخلفية عبر WorkManager ويستمر في العمل حتى عند إغلاق التطبيق أو إعادة تشغيل الجهاز. قد تؤخر خوارزميات توفير الطاقة (Doze) الموعد قليلاً لضمان سلامة البطارية.",
                                    fontSize = 10.sp,
                                    lineHeight = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Synchronization Status & Last Backup Info Card (Requirement 19 & 20)
 */
@Composable
private fun SyncStatusAndLastBackupCard(settingsState: BackupSettingsState) {
    val status = settingsState.syncStatus
    val (statusLabel, statusColor, statusIcon) = when (status) {
        "Backup Successful" -> Triple("متزامن بنجاح (Successfully Synchronized)", Color(0xFF059669), Icons.Default.CheckCircle)
        "Backup In Progress" -> Triple("جاري النسخ الاحتياطي...", Color(0xFF2563EB), Icons.Default.Sync)
        "Folder Required" -> Triple("يلزم تحديد مجلد النسخ (Folder Required)", Color(0xFFD97706), Icons.Default.Folder)
        "Ready" -> Triple("جاهز للنسخ (Ready)", Color(0xFF2563EB), Icons.Default.CloudDone)
        "Backup Failed" -> Triple("فشل النسخ الاحتياطي (Failed)", Color(0xFFDC2626), Icons.Default.ErrorOutline)
        "Retry Scheduled" -> Triple("مجدولة إعادة المحاولة (Retry Scheduled)", Color(0xFFD97706), Icons.Default.Update)
        "Folder Unavailable" -> Triple("المجلد غير متاح (Folder Unavailable)", Color(0xFFDC2626), Icons.Default.FolderOff)
        "No Network" -> Triple("لا يوجد اتصال بالإنترنت", Color(0xFFD97706), Icons.Default.WifiOff)
        "Permission Lost" -> Triple("فُقد تصريح المجلد (Permission Lost)", Color(0xFFDC2626), Icons.Default.Lock)
        else -> Triple("غير مفعل (Not Enabled)", Color(0xFF6B7280), Icons.Default.CloudOff)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // Status Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("حالة المزامنة (Sync Status):", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Surface(
                    color = statusColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(statusIcon, contentDescription = null, tint = statusColor, modifier = Modifier.size(14.dp))
                        Text(statusLabel, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = statusColor)
                    }
                }
            }

            HorizontalDivider()

            // Last Successful Backup Section
            Text("آخر نسخة ناجحة (Last Successful Backup):", fontWeight = FontWeight.Bold, fontSize = 12.sp)

            if (settingsState.lastSuccessfulTimestamp > 0L) {
                val dateStr = SimpleDateFormat("dd/MM/yyyy - hh:mm a", Locale.getDefault())
                    .format(Date(settingsState.lastSuccessfulTimestamp))
                val sizeStr = if (settingsState.lastFileSizeBytes > 1024 * 1024) {
                    String.format(Locale.US, "%.2f MB", settingsState.lastFileSizeBytes / (1024.0 * 1024.0))
                } else {
                    String.format(Locale.US, "%.1f KB", settingsState.lastFileSizeBytes / 1024.0)
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("التاريخ والوقت:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(dateStr, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("نوع النسخة:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(settingsState.lastBackupType ?: "-", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("اسم الملف:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(settingsState.lastFileName ?: "-", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("الحجم:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(sizeStr, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            } else {
                Text(
                    "لم يتم إجراء أي نسخة احتياطية ناجحة حتى الآن.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!settingsState.lastErrorMessage.isNullOrBlank()) {
                Surface(
                    color = Color(0xFFFEE2E2),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(16.dp))
                        Text(
                            text = "آخر تنبيه: ${settingsState.lastErrorMessage}",
                            fontSize = 10.sp,
                            color = Color(0xFF991B1B)
                        )
                    }
                }
            }
        }
    }
}

// =========================================================================
// TAB 2: RESTORE CONTENT (Pre-restore Inspection, Preview, Safety Backup)
// =========================================================================
@Composable
private fun RestoreTabContent(viewModel: AppViewModel) {
    val context = LocalContext.current

    val restoreInProgress by viewModel.restoreInProgress.collectAsState()
    val restoreProgressPercent by viewModel.restoreProgressPercent.collectAsState()
    val restoreStageText by viewModel.restoreStageText.collectAsState()

    var localBackups by remember { mutableStateOf(viewModel.getLocalBackupFiles()) }
    var pendingPreview by remember { mutableStateOf<BackupPreview?>(null) }
    var isInspecting by remember { mutableStateOf(false) }
    var restoreSuccessResult by remember { mutableStateOf<RestoreResult?>(null) }
    var showSuccessDialog by remember { mutableStateOf(false) }

    // SAF Document Picker: Allows selecting a ".zip" backup file
    val zipFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            isInspecting = true
            viewModel.inspectBackupUri(uri) { res ->
                isInspecting = false
                if (res.isSuccess) {
                    pendingPreview = res.getOrThrow()
                } else {
                    val err = res.exceptionOrNull()?.localizedMessage ?: "الملف المحدد غير صالح أو تالف"
                    Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Safety Warning Banner
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                border = BorderStroke(1.dp, Color(0xFF93C5FD))
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFF2563EB), modifier = Modifier.size(28.dp))
                    Column {
                        Text("استعادة آمنة ومحمية 100% (Safety Guaranteed)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF1E3A8A))
                        Text(
                            "يقوم النظام تلقائياً بإنشاء نسخة أمان فورية من بياناتك الحالية قبل إجراء أي استعادة، مع فحص التوافق وتدقيق الأرصدة داخل معاملة ذرية متكاملة.",
                            fontSize = 11.sp,
                            color = Color(0xFF1E40AF)
                        )
                    }
                }
            }
        }

        // Action: Pick File from Device Storage (OpenDocument)
        item {
            Button(
                onClick = { zipFilePicker.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.FileOpen, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isInspecting) "جاري فحص وتدقيق الملف..." else "اختيار ملف نسخة احتياطية (.zip)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }

        // Live Restore Progress
        if (restoreInProgress) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("جاري استعادة البيانات...", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        LinearProgressIndicator(
                            progress = { restoreProgressPercent / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(restoreStageText, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                            Text("$restoreProgressPercent%", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // --- GOOGLE DRIVE CLOUD BACKUPS SECTION ---
        item {
            GoogleDriveCloudBackupsSection(viewModel = viewModel)
        }

        // List of Local Backup Files
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("النسخ الاحتياطية المتوفرة على الهاتف:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                IconButton(onClick = { localBackups = viewModel.getLocalBackupFiles() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "تحديث القائمة")
                }
            }
        }

        if (localBackups.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 30.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.FolderOff, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
                        Text("لا توجد نسخ احتياطية محفوظة حالياً في ذاكرة التطبيق", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            items(localBackups) { file ->
                val dateStr = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(Date(file.lastModified()))
                val sizeKb = file.length() / 1024
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFF059669).copy(alpha = 0.15f),
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Archive, contentDescription = null, tint = Color(0xFF059669), modifier = Modifier.size(20.dp))
                                }
                            }
                            Column {
                                Text(file.name, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text("$dateStr • $sizeKb ك.ب", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            // Restore button: Inspects first then triggers preview
                            IconButton(
                                onClick = {
                                    viewModel.inspectBackupFile(file) { res ->
                                        if (res.isSuccess) {
                                            pendingPreview = res.getOrThrow()
                                        } else {
                                            Toast.makeText(context, "الملف غير صالح أو لا يطابق بنية النسخ الاحتياطي", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Restore, contentDescription = "استعادة", tint = Color(0xFF059669))
                            }

                            // Share button
                            IconButton(
                                onClick = {
                                    ShareManager.shareFile(
                                        context = context,
                                        file = file,
                                        mimeType = "application/zip",
                                        chooserTitle = "مشاركة ملف النسخة الاحتياطية"
                                    )
                                }
                            ) {
                                Icon(Icons.Default.Share, contentDescription = "مشاركة", tint = MaterialTheme.colorScheme.primary)
                            }

                            // Delete button
                            IconButton(
                                onClick = {
                                    viewModel.deleteLocalBackupFile(file)
                                    localBackups = viewModel.getLocalBackupFiles()
                                }
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "حذف", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }

    // Pre-Restore Inspection & Confirmation Dialog (Requirement 4.6 & 4.7)
    pendingPreview?.let { preview ->
        AlertDialog(
            onDismissRequest = { pendingPreview = null },
            icon = { Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFF059669)) },
            title = { Text("معاينة وتأكيد الاستعادة", fontWeight = FontWeight.Bold, textAlign = TextAlign.Center) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            val sizeFormatted = if (preview.fileSizeBytes > 1024 * 1024) {
                                String.format(Locale.US, "%.2f MB", preview.fileSizeBytes / (1024.0 * 1024.0))
                            } else {
                                String.format(Locale.US, "%.1f KB", preview.fileSizeBytes / 1024.0)
                            }
                            val dateFormatted = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(preview.timestamp))

                            Text("📁 الملف: ${preview.fileName}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("📦 الحجم: $sizeFormatted", fontSize = 11.sp)
                            Text("📅 تاريخ الإنشاء: $dateFormatted", fontSize = 11.sp)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("إصدار المخطط: ${preview.databaseVersion}", fontSize = 11.sp)
                                Surface(
                                    color = if (preview.isCompatible) Color(0xFFD1FAE5) else Color(0xFFFEE2E2),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        if (preview.isCompatible) "متوافق مع التطبيق ✓" else "تحذير: إصدار غير متطابق ⚠️",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (preview.isCompatible) Color(0xFF065F46) else Color(0xFF991B1B),
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }

                    Text("سجلات البيانات المتضمنة بالنسخة:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("👥 العملاء والحسابات:", fontSize = 11.sp)
                            Text("${preview.recordCounts["clients"] ?: 0} عميل", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("🧾 الفواتير وبنودها:", fontSize = 11.sp)
                            Text("${preview.recordCounts["invoices"] ?: 0} فاتورة", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("💵 سندات القبض والدفعات:", fontSize = 11.sp)
                            Text("${preview.recordCounts["payments"] ?: 0} سند", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("📅 الأقساط والتنبيهات:", fontSize = 11.sp)
                            Text("${preview.recordCounts["installments"] ?: 0} قسط", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("📦 الأصناف والمخزون:", fontSize = 11.sp)
                            Text("${preview.recordCounts["items"] ?: 0} صنف", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }

                    Surface(
                        color = Color(0xFFEFF6FF),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "🛡️ سيتم أخذ نسخة أمان احتياطية تلقائياً من بياناتك الحالية قبل تطبيق أي تعديل، وفي حال حدوث أي خطأ لن تتأثر بياناتك إطلاقاً.",
                            fontSize = 10.sp,
                            lineHeight = 14.sp,
                            color = Color(0xFF1E40AF),
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = pendingPreview
                        pendingPreview = null
                        if (target != null) {
                            viewModel.restoreFromBackupPreview(
                                preview = target,
                                onResult = { res ->
                                    restoreSuccessResult = res
                                    showSuccessDialog = true
                                    localBackups = viewModel.getLocalBackupFiles()
                                }
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669))
                ) {
                    Text("نعم، استعادة الآن")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingPreview = null }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // Success Summary Dialog
    if (showSuccessDialog && restoreSuccessResult != null) {
        val res = restoreSuccessResult!!
        AlertDialog(
            onDismissRequest = { showSuccessDialog = false },
            icon = {
                Icon(
                    if (res.success) Icons.Default.CheckCircle else Icons.Default.Error,
                    contentDescription = null,
                    tint = if (res.success) Color(0xFF059669) else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    if (res.success) "اكتملت الاستعادة بنجاح!" else "فشلت عملية الاستعادة",
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(res.message, fontSize = 12.sp)
                    if (res.success) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("إجمالي السجلات المستعادة: ${res.totalRestored}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                for ((tbl, count) in res.details) {
                                    Text("• $tbl: $count", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showSuccessDialog = false }) {
                    Text("تم")
                }
            }
        )
    }
}

// =========================================================================
// TAB 3: EXPORT CONTENT (CSV for Excel with UTF-8 BOM, PDF for Print)
// =========================================================================
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExportTabContent(viewModel: AppViewModel) {
    val context = LocalContext.current
    val exportInProgress by viewModel.exportInProgress.collectAsState()

    var selectedFormat by remember { mutableStateOf("CSV") }
    var selectedRange by remember { mutableStateOf("الكل") }
    var selectedCategories by remember {
        mutableStateOf(setOf("clients", "invoices", "payments", "installments", "items"))
    }

    val categoryLabels = mapOf(
        "clients" to "كشف العملاء وأرصدتهم",
        "invoices" to "سجل فواتير المبيعات",
        "payments" to "سندات القبض والدفعات",
        "installments" to "جدول الأقساط",
        "items" to "المخزون والأصناف",
        "supplierCompanies" to "دليل الموردين والشركات"
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Format Selection Cards
        item {
            Text("اختر صيغة التصدير المطلوبة:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // 1. CSV
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { selectedFormat = "CSV" },
                    colors = CardDefaults.cardColors(
                        containerColor = if (selectedFormat == "CSV") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(1.5.dp, if (selectedFormat == "CSV") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.TableChart, contentDescription = null, tint = Color(0xFF059669))
                        Text("Excel / CSV", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text("ترميز UTF-8 عربي (BOM)", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // 2. PDF
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { selectedFormat = "PDF" },
                    colors = CardDefaults.cardColors(
                        containerColor = if (selectedFormat == "PDF") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(1.5.dp, if (selectedFormat == "PDF") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = Color(0xFFDC2626))
                        Text("تقرير PDF", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text("منسق وجاهز للطباعة", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // Date Range
        item {
            Text("نطاق التاريخ:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("الكل", "اليوم", "هذا الأسبوع", "هذا الشهر").forEach { range ->
                    FilterChip(
                        selected = selectedRange == range,
                        onClick = { selectedRange = range },
                        label = { Text(range, fontSize = 11.sp) }
                    )
                }
            }
        }

        // Categories Checkboxes
        item {
            Text("البيانات المراد تصديرها:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                categoryLabels.forEach { (catKey, label) ->
                    FilterChip(
                        selected = selectedCategories.contains(catKey),
                        onClick = {
                            selectedCategories = if (selectedCategories.contains(catKey)) {
                                selectedCategories - catKey
                            } else {
                                selectedCategories + catKey
                            }
                        },
                        label = { Text(label, fontSize = 11.sp) }
                    )
                }
            }
        }

        // Export Action Button
        item {
            Spacer(modifier = Modifier.height(6.dp))
            Button(
                onClick = {
                    if (selectedCategories.isEmpty()) {
                        Toast.makeText(context, "يرجى تحديد قسم واحد على الأقل للتصدير", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    val now = System.currentTimeMillis()
                    val (startDate, endDate) = when (selectedRange) {
                        "اليوم" -> Pair(now - 24 * 3600 * 1000L, now)
                        "هذا الأسبوع" -> Pair(now - 7 * 24 * 3600 * 1000L, now)
                        "هذا الشهر" -> Pair(now - 30 * 24 * 3600 * 1000L, now)
                        else -> Pair(null, null)
                    }

                    viewModel.exportData(
                        format = selectedFormat,
                        selectedCategories = selectedCategories,
                        startDate = startDate,
                        endDate = endDate,
                        onSuccess = { file ->
                            val mime = when (selectedFormat) {
                                "CSV" -> "text/comma-separated-values"
                                "PDF" -> "application/pdf"
                                else -> "text/plain"
                            }
                            ShareManager.shareFile(
                                context = context,
                                file = file,
                                mimeType = mime,
                                chooserTitle = "مشاركة الملف المصدر عبر التطبيقات"
                            )
                        },
                        onError = { err ->
                            Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                        }
                    )
                },
                enabled = !exportInProgress,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Share, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (exportInProgress) "جاري التصدير..." else "تصدير ومشاركة الملف الآن",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// =========================================================================
// TAB 4: HISTORY (Audit trail of backups, sizes, dates, and destinations)
// =========================================================================
@Composable
private fun HistoryTabContent(viewModel: AppViewModel) {
    val backupHistoryList by viewModel.backupHistory.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text("سجل عمليات النسخ الاحتياطي والمزامنة:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }

        if (backupHistoryList.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.HistoryToggleOff, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
                        Text("لا يوجد أي عمليات مسجلة حتى الآن", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            items(backupHistoryList) { entry ->
                val dateStr = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(Date(entry.timestamp))
                val sizeKb = entry.fileSizeBytes / 1024

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Surface(
                                shape = CircleShape,
                                color = if (entry.status == "ناجحة") Color(0xFF059669).copy(alpha = 0.15f) else Color(0xFFDC2626).copy(alpha = 0.15f),
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        if (entry.status == "ناجحة") Icons.Default.Check else Icons.Default.Close,
                                        contentDescription = null,
                                        tint = if (entry.status == "ناجحة") Color(0xFF059669) else Color(0xFFDC2626),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Column {
                                Text("${entry.backupType} • ${entry.destination}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text("$dateStr • $sizeKb ك.ب • ${entry.recordCount} سجل", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        Surface(
                            color = if (entry.status == "ناجحة") Color(0xFFD1FAE5) else Color(0xFFFEE2E2),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = entry.status,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (entry.status == "ناجحة") Color(0xFF065F46) else Color(0xFF991B1B),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GoogleDriveSyncCard(
    viewModel: AppViewModel
) {
    val context = LocalContext.current
    val isDriveSyncEnabled by viewModel.isDriveSyncEnabled.collectAsState()
    val driveAccountEmail by viewModel.driveAccountEmail.collectAsState()
    val driveBackupStatus by viewModel.driveBackupStatus.collectAsState()
    val lastBackupTimeStr by viewModel.lastBackupTimeStr.collectAsState()
    val isDriveBackingUp by viewModel.isDriveBackingUp.collectAsState()
    val driveBackupProgress by viewModel.driveBackupProgress.collectAsState()
    val driveBackupStage by viewModel.driveBackupStage.collectAsState()
    val driveAuthIntent by viewModel.driveAuthRecoverableIntent.collectAsState()

    // Google Drive Smart Scheduling & Battery States
    val isDriveScheduleEnabled by viewModel.isDriveScheduleEnabled.collectAsState()
    val driveScheduleFrequency by viewModel.driveScheduleFrequency.collectAsState()
    val driveScheduleHour by viewModel.driveScheduleHour.collectAsState()
    val driveScheduleMinute by viewModel.driveScheduleMinute.collectAsState()
    val driveScheduleDayOfWeek by viewModel.driveScheduleDayOfWeek.collectAsState()
    val driveScheduleDayOfMonth by viewModel.driveScheduleDayOfMonth.collectAsState()
    val isBatteryOptimizationIgnored by viewModel.isBatteryOptimizationIgnored.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.refreshBatteryOptimizationStatus()
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    // Google Sign-In with Drive scopes launcher
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.data != null) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                if (account != null && !account.email.isNullOrBlank()) {
                    viewModel.onGoogleDriveAccountSelected(account)
                } else {
                    Toast.makeText(context, "لم يتم اختيار حساب Google صالح", Toast.LENGTH_SHORT).show()
                }
            } catch (e: ApiException) {
                val errMsg = when (e.statusCode) {
                    12501 -> "تم إلغاء اختيار الحساب"
                    7 -> "لا يوجد اتصال بالإنترنت"
                    else -> "فشل تسجيل الدخول بحساب Google (رمز: ${e.statusCode})"
                }
                Toast.makeText(context, errMsg, Toast.LENGTH_LONG).show()
            }
        }
    }

    // Recoverable Auth Consent launcher
    val recoverableAuthLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.performGoogleDriveBackup()
        }
    }

    LaunchedEffect(driveAuthIntent) {
        driveAuthIntent?.let { intent ->
            recoverableAuthLauncher.launch(intent)
            viewModel.driveAuthRecoverableIntent.value = null
        }
    }

    fun triggerGoogleSignIn() {
        try {
            val client = viewModel.getGoogleSignInClient(context)
            client.signOut().addOnCompleteListener {
                googleSignInLauncher.launch(client.signInIntent)
            }
        } catch (e: Exception) {
            Toast.makeText(context, "تعذر فتح نافذة حساب Google: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, if (isDriveSyncEnabled) Color(0xFF4285F4) else MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF4285F4).copy(alpha = 0.15f),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.CloudSync,
                                contentDescription = null,
                                tint = Color(0xFF4285F4),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Column {
                        Text("مزامنة Google Drive السحابية", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("حفظ تلقائي واسترجاع فوري لقاعدة البيانات والصور سحابياً", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Switch(
                    checked = isDriveSyncEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled) {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            }
                            if (driveAccountEmail.isNullOrBlank()) {
                                triggerGoogleSignIn()
                            } else {
                                viewModel.toggleDriveSync(true, driveAccountEmail)
                            }
                        } else {
                            viewModel.toggleDriveSync(false)
                        }
                    }
                )
            }

            if (isDriveSyncEnabled) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                Surface(
                    color = Color(0xFF4285F4).copy(alpha = 0.08f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFF4285F4).copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.AccountCircle, contentDescription = null, tint = Color(0xFF4285F4), modifier = Modifier.size(24.dp))
                                Column {
                                    Text(
                                        text = driveAccountEmail ?: "لم يتم ربط حساب Google",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = if (driveAccountEmail.isNullOrBlank()) Color(0xFFB45309) else Color(0xFF1E3A8A)
                                    )
                                    Text("المجلد المخصص: الحكيمي للأدوية والمستلزمات الطبية / Backups", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF10B981).copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "متصل",
                                    color = Color(0xFF047857),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedButton(
                                onClick = { triggerGoogleSignIn() },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.ManageAccounts, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("تبديل / اختيار حساب Google للمزامنة", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                // --- 1. Smart Scheduled Auto-Sync Section (Daily, Weekly, Monthly with Catch-Up) ---
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Schedule, contentDescription = null, tint = Color(0xFF2563EB), modifier = Modifier.size(20.dp))
                                Column {
                                    Text("المزامنة التلقائية المجدولة", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Text("تحديد موعد يومي / أسبوعي / شهري للمزامنة", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Switch(
                                checked = isDriveScheduleEnabled,
                                onCheckedChange = { viewModel.setDriveScheduleEnabled(it) }
                            )
                        }

                        if (isDriveScheduleEnabled) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                            // Frequency Chips: DAILY, WEEKLY, MONTHLY
                            Text("تكرار المزامنة:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val frequencies = listOf(
                                    Triple("DAILY", "يوميًا", Icons.Default.Today),
                                    Triple("WEEKLY", "أسبوعيًا", Icons.Default.DateRange),
                                    Triple("MONTHLY", "شهريًا", Icons.Default.CalendarMonth)
                                )
                                frequencies.forEach { (code, label, icon) ->
                                    val isSelected = driveScheduleFrequency.equals(code, ignoreCase = true)
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { viewModel.setDriveScheduleFrequency(code) },
                                        label = { Text(label, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp)) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            // Time Picker Button
                            val isAm = driveScheduleHour < 12
                            val displayHour = when {
                                driveScheduleHour == 0 -> 12
                                driveScheduleHour > 12 -> driveScheduleHour - 12
                                else -> driveScheduleHour
                            }
                            val formattedTimeStr = String.format(Locale.getDefault(), "%02d:%02d %s", displayHour, driveScheduleMinute, if (isAm) "صباحاً" else "مساءً")

                            OutlinedButton(
                                onClick = {
                                    TimePickerDialog(
                                        context,
                                        { _, hour, minute ->
                                            viewModel.setDriveScheduleTime(hour, minute)
                                        },
                                        driveScheduleHour,
                                        driveScheduleMinute,
                                        false
                                    ).show()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("الوقت المحدد: $formattedTimeStr (انقر لتغيير الوقت)", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }

                            // Weekly Day of Week Picker
                            if (driveScheduleFrequency.equals("WEEKLY", ignoreCase = true)) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("يوم المزامنة الأسبوعي:", fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                    val daysOfWeek = listOf(
                                        Pair(Calendar.FRIDAY, "الجمعة"),
                                        Pair(Calendar.SATURDAY, "السبت"),
                                        Pair(Calendar.SUNDAY, "الأحد"),
                                        Pair(Calendar.MONDAY, "الإثنين"),
                                        Pair(Calendar.TUESDAY, "الثلاثاء"),
                                        Pair(Calendar.WEDNESDAY, "الأربعاء"),
                                        Pair(Calendar.THURSDAY, "الخميس")
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        daysOfWeek.take(4).forEach { (calDay, dayName) ->
                                            val isSelected = driveScheduleDayOfWeek == calDay
                                            FilterChip(
                                                selected = isSelected,
                                                onClick = { viewModel.setDriveScheduleDayOfWeek(calDay) },
                                                label = { Text(dayName, fontSize = 9.sp) },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        daysOfWeek.drop(4).forEach { (calDay, dayName) ->
                                            val isSelected = driveScheduleDayOfWeek == calDay
                                            FilterChip(
                                                selected = isSelected,
                                                onClick = { viewModel.setDriveScheduleDayOfWeek(calDay) },
                                                label = { Text(dayName, fontSize = 9.sp) },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                }
                            }

                            // Monthly Day of Month Picker
                            if (driveScheduleFrequency.equals("MONTHLY", ignoreCase = true)) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("يوم المزامنة الشهري (من 1 إلى 28):", fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                    val daysOfMonth = listOf(1, 5, 10, 15, 20, 25, 28)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        daysOfMonth.forEach { day ->
                                            val isSelected = driveScheduleDayOfMonth == day
                                            FilterChip(
                                                selected = isSelected,
                                                onClick = { viewModel.setDriveScheduleDayOfMonth(day) },
                                                label = { Text("يوم $day", fontSize = 9.sp) },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                }
                            }

                            // Smart Catch-up info card
                            Surface(
                                color = Color(0xFFF0FDF4),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, Color(0xFF86EFAC)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.Bolt, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(18.dp))
                                    Column {
                                        Text("ميزة الاستدراك التلقائي الذكي:", fontWeight = FontWeight.Bold, fontSize = 10.sp, color = Color(0xFF15803D))
                                        Text(
                                            "في حال كان الهاتف مغلقاً أو بدون اتصال بالإنترنت وقت الموعد المحدد ($formattedTimeStr)، سيقوم التطبيق فور اتصالك بالإنترنت بعمل المزامنة في الخلفية، ولن يتم تكرار العملية مرتين في نفس الفترة.",
                                            fontSize = 9.sp,
                                            color = Color(0xFF166534),
                                            lineHeight = 13.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // --- 2. Battery Optimization Notice Section ---
                Surface(
                    color = if (isBatteryOptimizationIgnored) Color(0xFFF0FDF4) else Color(0xFFFFFBEB),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, if (isBatteryOptimizationIgnored) Color(0xFF86EFAC) else Color(0xFFFDE68A)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    if (isBatteryOptimizationIgnored) Icons.Default.BatteryChargingFull else Icons.Default.BatteryAlert,
                                    contentDescription = null,
                                    tint = if (isBatteryOptimizationIgnored) Color(0xFF16A34A) else Color(0xFFD97706),
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    if (isBatteryOptimizationIgnored) "وضع البطارية: مسموح بالعمل التلقائي ⚡" else "تنبيه قيود توفير البطارية",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = if (isBatteryOptimizationIgnored) Color(0xFF15803D) else Color(0xFF92400E)
                                )
                            }
                            if (!isBatteryOptimizationIgnored) {
                                TextButton(
                                    onClick = {
                                        viewModel.requestIgnoreBatteryOptimizations()
                                        viewModel.refreshBatteryOptimizationStatus()
                                    },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("إلغاء قيود البطارية", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD97706))
                                }
                            }
                        }
                        if (!isBatteryOptimizationIgnored) {
                            Text(
                                "لضمان تشغيل المزامنة بالموعد المحدد حتى لو كان الهاتف في وضع السكون (Doze Mode)، يُنصح بإلغاء قيود البطارية للتطبيق.",
                                fontSize = 9.sp,
                                color = Color(0xFF78350F),
                                lineHeight = 12.sp
                            )
                        }
                    }
                }

                // In-Progress Indicator
                if (isDriveBackingUp) {
                    Surface(
                        color = Color(0xFFEBF5FF),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFF93C5FD)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = driveBackupStage.ifBlank { "جاري المزامنة مع Google Drive..." },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1E40AF)
                                )
                                Text(
                                    text = "$driveBackupProgress%",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1E40AF)
                                )
                            }
                            LinearProgressIndicator(
                                progress = { driveBackupProgress / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = Color(0xFF2563EB),
                                trackColor = Color(0xFFDBEAFE)
                            )
                        }
                    }
                } else if (!driveBackupStatus.isNullOrBlank()) {
                    val isError = driveBackupStatus?.contains("خطأ", ignoreCase = true) == true || driveBackupStatus?.contains("فشل", ignoreCase = true) == true
                    Surface(
                        color = if (isError) Color(0xFFFEF2F2) else Color(0xFFF0FDF4),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, if (isError) Color(0xFFFCA5A5) else Color(0xFF86EFAC)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                if (isError) Icons.Default.ErrorOutline else Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = if (isError) Color(0xFFDC2626) else Color(0xFF059669),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                driveBackupStatus ?: "",
                                fontSize = 10.sp,
                                color = if (isError) Color(0xFF991B1B) else Color(0xFF065F46),
                                lineHeight = 15.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.performGoogleDriveBackup() },
                        enabled = !isDriveBackingUp,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4285F4)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(vertical = 10.dp)
                    ) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isDriveBackingUp) "جاري المزامنة..." else "مزامنة ورفع الآن مع Google Drive",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            viewModel.checkDriveBackup()
                            viewModel.loadGoogleDriveBackups()
                        },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "تحديث", modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun GoogleDriveCloudBackupsSection(viewModel: AppViewModel) {
    val context = LocalContext.current
    val cloudBackups by viewModel.driveCloudBackups.collectAsState()
    val isCloudLoading by viewModel.isDriveCloudBackupsLoading.collectAsState()
    val isDriveBackingUp by viewModel.isDriveBackingUp.collectAsState()
    val driveAccountEmail by viewModel.driveAccountEmail.collectAsState()

    var fileToRestore by remember { mutableStateOf<com.example.util.GoogleDriveFileMetadata?>(null) }

    LaunchedEffect(driveAccountEmail) {
        if (!driveAccountEmail.isNullOrBlank()) {
            viewModel.loadGoogleDriveBackups()
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, Color(0xFF4285F4).copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null, tint = Color(0xFF4285F4), modifier = Modifier.size(20.dp))
                    Text("النسخ السحابية المحفوظة في Google Drive:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                IconButton(
                    onClick = { viewModel.loadGoogleDriveBackups() },
                    enabled = !isCloudLoading
                ) {
                    if (isCloudLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color(0xFF4285F4))
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = "تحديث النسخ السحابية", tint = Color(0xFF4285F4))
                    }
                }
            }

            if (isCloudLoading) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color(0xFF4285F4))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("جاري استعلام قائمة النسخ الاحتياطية من Google Drive...", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else if (cloudBackups.isEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.CloudOff, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(28.dp))
                        Text(
                            text = "لا توجد نسخ سحابية في Google Drive حتى الآن",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "اضغط 'مزامنة ورفع الآن مع Google Drive' من تبويب النسخ والمزامنة لرفع أول نسخة.",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                cloudBackups.forEach { fileMeta ->
                    val sizeKb = fileMeta.size / 1024
                    val sizeFormatted = if (sizeKb > 1024) "${String.format(Locale.US, "%.1f", sizeKb / 1024.0)} MB" else "$sizeKb KB"
                    val formattedDate = fileMeta.formattedDate

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Default.FolderZip, contentDescription = null, tint = Color(0xFF2563EB), modifier = Modifier.size(16.dp))
                                    Text(fileMeta.name, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                                Text("الحجم: $sizeFormatted • التاريخ: $formattedDate", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("سحابي بـ Google Drive (آمن وشامل لقاعدة البيانات والصور)", fontSize = 9.sp, color = Color(0xFF059669))
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Button(
                                onClick = { fileToRestore = fileMeta },
                                enabled = !isDriveBackingUp,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4285F4)),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("استعادة", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    // Confirmation dialog before restoring from cloud
    fileToRestore?.let { fileMeta ->
        AlertDialog(
            onDismissRequest = { fileToRestore = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.WarningAmber, contentDescription = null, tint = Color(0xFFD97706))
                    Text("تأكيد الاستعادة السحابية", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "هل أنت متأكد من استعادة النسخة السحابية التالية؟",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text("اسم الملف: ${fileMeta.name}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("الحجم: ${fileMeta.size / 1024} KB", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text(
                        text = "⚠️ تنبيه: سيتم تنزيل النسخة واسترجاع وتحديث كافة السجلات المحاسبية وقاعدة البيانات وصور الأصناف من هذه النسخة السحابية.",
                        fontSize = 11.sp,
                        color = Color(0xFFB45309),
                        lineHeight = 15.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = fileMeta
                        fileToRestore = null
                        viewModel.restoreGoogleDriveCloudBackup(
                            fileMeta = target,
                            onSuccess = { msg -> Toast.makeText(context, msg, Toast.LENGTH_LONG).show() },
                            onError = { err -> Toast.makeText(context, err, Toast.LENGTH_LONG).show() }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4285F4))
                ) {
                    Text("نعم، استعادة الآن")
                }
            },
            dismissButton = {
                TextButton(onClick = { fileToRestore = null }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

