package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import com.example.data.model.*
import com.example.ui.viewmodel.AppViewModel
import com.example.util.AuditCleanupEngine
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuditLogManagementScreen(
    viewModel: AppViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val activeCount by viewModel.activeAuditLogsCount.collectAsState()
    val archivedCount by viewModel.archivedAuditLogsCount.collectAsState()
    val statsSummary by viewModel.auditStatsSummary.collectAsState()
    val monthlySummaries by viewModel.monthlyAuditSummaries.collectAsState()
    val archiveFiles by viewModel.archiveFilesList.collectAsState()
    val retentionSettings by viewModel.auditRetentionSettings.collectAsState()
    val operationsHistory by viewModel.auditOperationsHistory.collectAsState()

    val isOperationInProgress by viewModel.auditOperationInProgress.collectAsState()
    val progressPercent by viewModel.auditProgressPercent.collectAsState()
    val progressStageText by viewModel.auditProgressStageText.collectAsState()

    val activeSearchResults by viewModel.activeLogsSearchResults.collectAsState()
    val archivedSearchResults by viewModel.archivedLogsSearchResults.collectAsState()
    val searchQuery by viewModel.auditSearchQuery.collectAsState()
    val searchTarget by viewModel.auditSearchTarget.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        "📊 المؤشرات والإحصائيات",
        "📅 الأرشيف والتنظيف الشهري",
        "🗂️ حزم الأرشيف والاستعادة",
        "🔍 استعراض السجلات والعمليات",
        "⚙️ سياسة الاحتفاظ والأمان"
    )

    // Multi-month selection state
    val selectedMonths = remember { mutableStateListOf<MonthAuditSummary>() }

    // Dialog states
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var pendingDeleteAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingDeletePreview by remember { mutableStateOf<AuditCleanupPreview?>(null) }

    var showCustomRangeDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var exportTargetMonth by remember { mutableStateOf<MonthAuditSummary?>(null) }

    // Refresh data on entry
    LaunchedEffect(Unit) {
        viewModel.refreshAuditStatsAndSummaries()
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("screen_audit_log_management"),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "إدارة سجل العمليات والأرشفة",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "حفظ، ترحيل، وتطهير سجلات التدقيق التاريخية بأمان",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("btn_back_audit_management")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "رجوع"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            viewModel.refreshAuditStatsAndSummaries()
                            Toast.makeText(context, "تم تحديث إحصائيات السجلات", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.testTag("btn_refresh_audit_stats")
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "تحديث")
                    }
                    IconButton(
                        onClick = {
                            exportTargetMonth = null
                            showExportDialog = true
                        },
                        modifier = Modifier.testTag("btn_export_all_audit")
                    ) {
                        Icon(imageVector = Icons.Default.FileDownload, contentDescription = "تصدير السجلات")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Safety Disclaimer Banner (Critical Accounting Data Protection)
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "نظام حماية البيانات المحاسبية: العمليات هنا تطبق على سجلات التدقيق (Audit Logs) فقط. لن يتم مس أو تعديل العملاء، الفواتير، المدفوعات، المخزون، أو الأرصدة المالية إطلاقاً.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 16.sp
                    )
                }
            }

            // Scrollable Tab Row
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                edgePadding = 16.dp,
                divider = { HorizontalDivider() },
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                text = title,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 13.sp
                            )
                        },
                        modifier = Modifier.testTag("tab_audit_$index")
                    )
                }
            }

            // Content per tab
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                when (selectedTab) {
                    0 -> OverviewStatsTab(
                        statsSummary = statsSummary,
                        retentionSettings = retentionSettings,
                        onTriggerOldLogsCleanup = {
                            val cutoff = retentionSettings.cutoffTimestampMillis
                            if (cutoff != null) {
                                coroutineScope.launch {
                                    val preview = AuditCleanupEngine.previewOlderThan(
                                        db = viewModel.getDatabaseInstance(),
                                        cutoffTime = cutoff,
                                        title = "تنظيف السجلات الأقدم من ${retentionSettings.retentionPeriodLabelArabic}",
                                        safetyLockDays = retentionSettings.safetyLockDays
                                    )
                                    pendingDeletePreview = preview
                                    pendingDeleteAction = {
                                        viewModel.executeOldLogsCleanup(cutoff) { res ->
                                            if (res.isSuccess) {
                                                Toast.makeText(context, "تم تنظيف ${res.getOrNull()} سجل بنجاح", Toast.LENGTH_LONG).show()
                                            } else {
                                                Toast.makeText(context, "خطأ: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    }
                                    showDeleteConfirmDialog = true
                                }
                            } else {
                                Toast.makeText(context, "سياسة الاحتفاظ الحالية هي الاحتفاظ الدائم (دائمًا)", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onNavigateToMonthlyTab = { selectedTab = 1 },
                        onNavigateToArchivesTab = { selectedTab = 2 },
                        onNavigateToBrowserTab = { selectedTab = 3 }
                    )
                    1 -> MonthlyRetentionTab(
                        monthlySummaries = monthlySummaries,
                        selectedMonths = selectedMonths,
                        safetyLockDays = retentionSettings.safetyLockDays,
                        onArchiveMonth = { summary ->
                            viewModel.executeMonthArchive(summary.year, summary.month) { res ->
                                if (res.isSuccess) {
                                    Toast.makeText(context, "تم أرشفة وحفظ ${res.getOrNull()?.recordCount} سجل بنجاح", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(context, "فشل الأرشفة: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        onDeleteMonth = { summary ->
                            coroutineScope.launch {
                                val preview = AuditCleanupEngine.previewDateRange(
                                    db = viewModel.getDatabaseInstance(),
                                    startTime = summary.minTimestamp,
                                    endTime = summary.maxTimestamp,
                                    title = "حذف سجلات شهر ${summary.displayName}",
                                    safetyLockDays = retentionSettings.safetyLockDays
                                )
                                pendingDeletePreview = preview
                                pendingDeleteAction = {
                                    viewModel.executeMonthCleanup(summary.year, summary.month) { res ->
                                        if (res.isSuccess) {
                                            Toast.makeText(context, "تم حذف ${res.getOrNull()} سجل بنجاح من شهر ${summary.displayName}", Toast.LENGTH_LONG).show()
                                        } else {
                                            Toast.makeText(context, "فشل الحذف: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                                showDeleteConfirmDialog = true
                            }
                        },
                        onDeleteSelectedMonths = {
                            if (selectedMonths.isEmpty()) return@MonthlyRetentionTab
                            val totalRecs = selectedMonths.sumOf { it.count }
                            val monthsCopy = selectedMonths.toList()
                            val now = System.currentTimeMillis()
                            val safetyCutoff = now - (retentionSettings.safetyLockDays * 86400000L)
                            val hasProtected = monthsCopy.any { it.maxTimestamp > safetyCutoff }

                            pendingDeletePreview = AuditCleanupPreview(
                                operationTitle = "حذف سجلات ${monthsCopy.size} أشهر محددة",
                                affectedRecordCount = totalRecs,
                                periodDescription = monthsCopy.joinToString("، ") { it.displayName },
                                startTimestamp = monthsCopy.minOf { it.minTimestamp },
                                endTimestamp = monthsCopy.maxOf { it.maxTimestamp },
                                estimatedSizeBytes = AuditCleanupEngine.estimateAuditLogsSizeBytes(totalRecs),
                                isProtectedBySafetyLock = hasProtected,
                                safetyLockWarning = if (hasProtected) "تتضمن بعض الأشهر المختارة سجلات حديثة تخضع لقفل الأمان (${retentionSettings.safetyLockDays} يومًا)." else null
                            )
                            pendingDeleteAction = {
                                viewModel.executeMultiMonthCleanup(monthsCopy) { res ->
                                    selectedMonths.clear()
                                    if (res.isSuccess) {
                                        Toast.makeText(context, "تم حذف ${res.getOrNull()} سجل بنجاح لعدة أشهر", Toast.LENGTH_LONG).show()
                                    } else {
                                        Toast.makeText(context, "فشل الحذف: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                            showDeleteConfirmDialog = true
                        },
                        onExportMonth = { summary ->
                            exportTargetMonth = summary
                            showExportDialog = true
                        },
                        onOpenCustomRangeDialog = {
                            showCustomRangeDialog = true
                        }
                    )
                    2 -> ArchivePackagesTab(
                        archiveFiles = archiveFiles,
                        onRestoreArchive = { fileInfo ->
                            val file = File(fileInfo.absolutePath)
                            viewModel.restoreArchivePackage(file) { res ->
                                if (res.isSuccess) {
                                    val r = res.getOrThrow()
                                    Toast.makeText(
                                        context,
                                        "تمت استعادة ${r.newlyRestoredCount} سجل جديد بأمان (تم تخطي ${r.skippedDuplicatesCount} مكرر)",
                                        Toast.LENGTH_LONG
                                    ).show()
                                } else {
                                    Toast.makeText(context, "فشل الاستعادة: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        onShareArchive = { fileInfo ->
                            shareFile(context, File(fileInfo.absolutePath), "application/zip")
                        },
                        onDeleteArchive = { fileInfo ->
                            val file = File(fileInfo.absolutePath)
                            if (file.delete()) {
                                viewModel.refreshAuditStatsAndSummaries()
                                Toast.makeText(context, "تم حذف ملف الأرشيف", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                    3 -> AuditBrowserTab(
                        activeResults = activeSearchResults,
                        archivedResults = archivedSearchResults,
                        searchQuery = searchQuery,
                        searchTarget = searchTarget,
                        operationsHistory = operationsHistory,
                        onQueryChange = { q ->
                            viewModel.searchAuditLogs(q, searchTarget)
                        },
                        onTargetChange = { t ->
                            viewModel.searchAuditLogs(searchQuery, t)
                        }
                    )
                    4 -> RetentionSettingsTab(
                        settings = retentionSettings,
                        onUpdatePeriod = { p -> viewModel.updateRetentionPeriod(p) },
                        onUpdateSafetyDays = { d -> viewModel.updateSafetyLockDays(d) },
                        onToggleAutoExport = { e -> viewModel.setAutoExportBeforeDelete(e) }
                    )
                }
            }
        }
    }

    // Modal Progress Dialog when background archiving / deleting is active
    if (isOperationInProgress) {
        Dialog(onDismissRequest = { /* Non-dismissable while processing */ }) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                modifier = Modifier.padding(8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        progress = { (progressPercent / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier.size(56.dp),
                        strokeWidth = 5.dp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "جاري تنفيذ العملية بأمان...",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "$progressPercent%",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 18.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = progressStageText,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "يتم الحذف في دفعات مقسمة (500 سجل) للحفاظ على استقرار قاعدة البيانات وسرعة الهاتف.",
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }

    // Safe Two-Step Delete Confirmation Dialog
    if (showDeleteConfirmDialog && pendingDeletePreview != null) {
        val preview = pendingDeletePreview!!
        var hasAgreedToSafetyWarning by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = {
                showDeleteConfirmDialog = false
                pendingDeletePreview = null
                pendingDeleteAction = null
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.WarningAmber,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = preview.operationTitle,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                text = "⚠️ إشعار عالي الأهمية:",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "هذا الإجراء سيقوم بحذف سجلات العمليات التاريخية فقط.\nلن يتم حذف أو تغيير أي عملاء، فواتير، سندات، أو أرصدة مالية نهائياً.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "• الفترة: ${preview.periodDescription}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "• عدد السجلات المتأثرة: ${preview.affectedRecordCount} سجل",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "• المساحة التقديرية المحررة: ${AuditCleanupEngine.formatFileSize(preview.estimatedSizeBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (preview.isProtectedBySafetyLock && preview.safetyLockWarning != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "🛡️ ${preview.safetyLockWarning}",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { hasAgreedToSafetyWarning = !hasAgreedToSafetyWarning }
                    ) {
                        Checkbox(
                            checked = hasAgreedToSafetyWarning,
                            onCheckedChange = { hasAgreedToSafetyWarning = it }
                        )
                        Text(
                            text = "أؤكد رغبتي في تنظيف سجلات هذه الفترة بعد الاطلاع على التفاصيل",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val action = pendingDeleteAction
                        showDeleteConfirmDialog = false
                        pendingDeletePreview = null
                        pendingDeleteAction = null
                        action?.invoke()
                    },
                    enabled = hasAgreedToSafetyWarning,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("حذف السجلات بأمان")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showDeleteConfirmDialog = false
                        pendingDeletePreview = null
                        pendingDeleteAction = null
                    }
                ) {
                    Text("إلغاء")
                }
            }
        )
    }

    // Custom Date Range Cleanup Dialog
    if (showCustomRangeDialog) {
        CustomRangeCleanupDialog(
            viewModel = viewModel,
            safetyLockDays = retentionSettings.safetyLockDays,
            onDismiss = { showCustomRangeDialog = false },
            onConfirmRange = { start, end ->
                showCustomRangeDialog = false
                coroutineScope.launch {
                    val preview = AuditCleanupEngine.previewDateRange(
                        db = viewModel.getDatabaseInstance(),
                        startTime = start,
                        endTime = end,
                        title = "حذف سجلات فترة مخصصة",
                        safetyLockDays = retentionSettings.safetyLockDays
                    )
                    pendingDeletePreview = preview
                    pendingDeleteAction = {
                        viewModel.executeCustomRangeCleanup(start, end) { res ->
                            if (res.isSuccess) {
                                Toast.makeText(context, "تم حذف ${res.getOrNull()} سجل بنجاح", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "فشل الحذف: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                    showDeleteConfirmDialog = true
                }
            }
        )
    }

    // Export Dialog (CSV / TXT / PDF)
    if (showExportDialog) {
        ExportAuditLogsDialog(
            targetMonth = exportTargetMonth,
            onDismiss = { showExportDialog = false },
            onExport = { format ->
                showExportDialog = false
                val start = exportTargetMonth?.minTimestamp
                val end = exportTargetMonth?.maxTimestamp
                val desc = exportTargetMonth?.displayName ?: "كامل السجلات"
                viewModel.exportAuditLogs(format, start, end, desc) { res ->
                    if (res.isSuccess) {
                        val file = res.getOrThrow()
                        Toast.makeText(context, "تم إنشاء الملف: ${file.name}", Toast.LENGTH_LONG).show()
                        shareFile(context, file, when (format) {
                            "CSV" -> "text/csv"
                            "PDF" -> "application/pdf"
                            else -> "text/plain"
                        })
                    } else {
                        Toast.makeText(context, "فشل التصدير: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }
}

// =========================================================================
// TAB 0: OVERVIEW & STATS
// =========================================================================

@Composable
fun OverviewStatsTab(
    statsSummary: AuditStatsSummary,
    retentionSettings: com.example.data.preferences.AuditRetentionSettings,
    onTriggerOldLogsCleanup: () -> Unit,
    onNavigateToMonthlyTab: () -> Unit,
    onNavigateToArchivesTab: () -> Unit,
    onNavigateToBrowserTab: () -> Unit
) {
    val dateFmt = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // High-level KPI Cards
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onNavigateToBrowserTab() },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Icon(imageVector = Icons.Default.ListAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "السجلات النشطة", style = MaterialTheme.typography.labelMedium)
                        Text(
                            text = "${statsSummary.activeLogsCount}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "المساحة: ${AuditCleanupEngine.formatFileSize(statsSummary.estimatedActiveSizeBytes)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onNavigateToArchivesTab() },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Icon(imageVector = Icons.Default.Archive, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "السجلات المؤرشفة", style = MaterialTheme.typography.labelMedium)
                        Text(
                            text = "${statsSummary.archivedLogsCount}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "حزم ZIP: ${AuditCleanupEngine.formatFileSize(statsSummary.archiveDirectorySizeBytes)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }

        // Storage & Database Footprint
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(
                        text = "💾 استهلاك الذاكرة وتخزين قاعدة البيانات",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "حجم ملف قاعدة البيانات الكامل (SQLite + WAL):", style = MaterialTheme.typography.bodySmall)
                        Text(
                            text = AuditCleanupEngine.formatFileSize(statsSummary.databaseFileSizeBytes),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "الحجم التقديري لسجلات العمليات النشطة:", style = MaterialTheme.typography.bodySmall)
                        Text(
                            text = AuditCleanupEngine.formatFileSize(statsSummary.estimatedActiveSizeBytes),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "مساحة مجلد الأرشيف الخارجي (ZIP):", style = MaterialTheme.typography.bodySmall)
                        Text(
                            text = AuditCleanupEngine.formatFileSize(statsSummary.archiveDirectorySizeBytes),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        // Timeline Span
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(
                        text = "⏳ النطاق الزمني للسجلات الحالية",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "أقدم سجل نشط:", style = MaterialTheme.typography.bodySmall)
                        Text(
                            text = if (statsSummary.oldestLogTimestamp != null) dateFmt.format(Date(statsSummary.oldestLogTimestamp)) else "لا توجد سجلات",
                            fontWeight = FontWeight.Medium,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "أحدث سجل مسجل:", style = MaterialTheme.typography.bodySmall)
                        Text(
                            text = if (statsSummary.newestLogTimestamp != null) dateFmt.format(Date(statsSummary.newestLogTimestamp)) else "لا توجد سجلات",
                            fontWeight = FontWeight.Medium,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "سجلات الشهر الحالي:", style = MaterialTheme.typography.bodySmall)
                        Text(
                            text = "${statsSummary.currentMonthCount} سجل",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        // Quick Retention Policy Action Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Policy, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "سياسة الاحتفاظ الحالية: ${retentionSettings.retentionPeriodLabelArabic}",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (retentionSettings.retentionPeriodKey == "FOREVER") {
                            "تم ضبط السياسة على 'الاحتفاظ الدائم'، مما يعني عدم حذف أي سجلات تلقائياً. يمكنك تغييرها من تبويب الإعدادات."
                        } else {
                            "هناك ${statsSummary.oldLogsEligibleCount} سجل أقدم من تاريخ انتهاء الصلاحية المحدد (${retentionSettings.retentionPeriodLabelArabic})."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    if (retentionSettings.retentionPeriodKey != "FOREVER" && statsSummary.oldLogsEligibleCount > 0) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = onTriggerOldLogsCleanup,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(imageVector = Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("تطهير السجلات المنتهية (${statsSummary.oldLogsEligibleCount} سجل)")
                        }
                    }
                }
            }
        }

        // Shortcut Action Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(
                    onClick = onNavigateToMonthlyTab,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(imageVector = Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("إدارة الأشهر", fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = onNavigateToArchivesTab,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(imageVector = Icons.Default.FolderZip, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("حزم الأرشيف", fontSize = 12.sp)
                }
            }
        }
    }
}

// =========================================================================
// TAB 1: MONTHLY RETENTION & CLEANUP
// =========================================================================

@Composable
fun MonthlyRetentionTab(
    monthlySummaries: List<MonthAuditSummary>,
    selectedMonths: MutableList<MonthAuditSummary>,
    safetyLockDays: Int,
    onArchiveMonth: (MonthAuditSummary) -> Unit,
    onDeleteMonth: (MonthAuditSummary) -> Unit,
    onDeleteSelectedMonths: () -> Unit,
    onExportMonth: (MonthAuditSummary) -> Unit,
    onOpenCustomRangeDialog: () -> Unit
) {
    val now = System.currentTimeMillis()
    val safetyLockCutoff = now - (safetyLockDays * 86400000L)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "سجلات التدقيق مجمعة حسب الأشهر",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
                OutlinedButton(
                    onClick = onOpenCustomRangeDialog,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(imageVector = Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("فترة مخصصة", fontSize = 12.sp)
                }
            }
        }

        // Multi-select bulk bar
        if (selectedMonths.isNotEmpty()) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "تم تحديد: ${selectedMonths.size} أشهر (${selectedMonths.sumOf { it.count }} سجل)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Row {
                            TextButton(onClick = { selectedMonths.clear() }) {
                                Text("إلغاء")
                            }
                            Button(
                                onClick = onDeleteSelectedMonths,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("حذف المحدد", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        if (monthlySummaries.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "لا توجد سجلات عمليات مسجلة حتى الآن.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        } else {
            items(monthlySummaries) { summary ->
                val isProtected = summary.maxTimestamp > safetyLockCutoff
                val isSelected = selectedMonths.contains(summary)

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        else MaterialTheme.colorScheme.surface
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { checked ->
                                        if (checked) selectedMonths.add(summary) else selectedMonths.remove(summary)
                                    }
                                )
                                Column {
                                    Text(
                                        text = summary.displayName,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = "${summary.count} سجل (${AuditCleanupEngine.formatFileSize(AuditCleanupEngine.estimateAuditLogsSizeBytes(summary.count))})",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (isProtected) {
                                Surface(
                                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "قفل حديث",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.error,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Action Buttons per month
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilledTonalButton(
                                onClick = { onArchiveMonth(summary) },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Archive, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("أرشفة ZIP", fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = { onExportMonth(summary) },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                            ) {
                                Icon(imageVector = Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("تصدير", fontSize = 12.sp)
                            }

                            Button(
                                onClick = { onDeleteMonth(summary) },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                            ) {
                                Icon(imageVector = Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("حذف", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// TAB 2: ARCHIVE PACKAGES & SAFE RESTORE
// =========================================================================

@Composable
fun ArchivePackagesTab(
    archiveFiles: List<ArchiveFileInfo>,
    onRestoreArchive: (ArchiveFileInfo) -> Unit,
    onShareArchive: (ArchiveFileInfo) -> Unit,
    onDeleteArchive: (ArchiveFileInfo) -> Unit
) {
    val dateFmt = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                text = "حزم الأرشيف المحفوظة على الذاكرة (${archiveFiles.size} حزم)",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "كل حزمة عبارة عن ملف ZIP مشفر بالهاش يحتوي على السجلات وبيان التحقق Manifest.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }

        if (archiveFiles.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.FolderZip,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(54.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "لا توجد حزم أرشيف مؤرشفة حالياً.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Text(
                            text = "قم بأرشفة أي شهر من تبويب 'الأرشيف والتنظيف الشهري'.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        } else {
            items(archiveFiles) { fileInfo ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = fileInfo.fileName,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = "الفترة: ${fileInfo.periodDescription} | ${fileInfo.recordCount} سجل",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "الحجم: ${AuditCleanupEngine.formatFileSize(fileInfo.fileSizeBytes)} | تاريخ الإنشاء: ${dateFmt.format(Date(fileInfo.lastModified))}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }

                            if (fileInfo.isValid) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "تكامل سليم SHA-256",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            } else {
                                Surface(
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "تالف / غير متطابق",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { onRestoreArchive(fileInfo) },
                                modifier = Modifier.weight(1f),
                                enabled = fileInfo.isValid,
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("استعادة آمنة", fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = { onShareArchive(fileInfo) },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("مشاركة ZIP", fontSize = 12.sp)
                            }

                            IconButton(
                                onClick = { onDeleteArchive(fileInfo) }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "حذف الحزمة",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// TAB 3: AUDIT BROWSER & OPERATIONS LOG
// =========================================================================

@Composable
fun AuditBrowserTab(
    activeResults: List<AuditLog>,
    archivedResults: List<ArchivedAuditLog>,
    searchQuery: String,
    searchTarget: String,
    operationsHistory: List<AuditManagementOperation>,
    onQueryChange: (String) -> Unit,
    onTargetChange: (String) -> Unit
) {
    val dateFmt = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault())
    var subTab by remember { mutableIntStateOf(0) } // 0: Search Logs, 1: Operations History

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
    ) {
        // Sub-tabs
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = subTab == 0,
                onClick = { subTab = 0 },
                label = { Text("سجلات العمليات (${if (searchTarget == "ACTIVE") activeResults.size else archivedResults.size})") }
            )
            FilterChip(
                selected = subTab == 1,
                onClick = { subTab = 1 },
                label = { Text("سجل عمليات الإدارة (${operationsHistory.size})") }
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (subTab == 0) {
            // Search field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onQueryChange,
                modifier = Modifier.scrollToTopOnFocus().scrollToTopOnFocus().fillMaxWidth(),
                placeholder = { Text("ابحث في تفاصيل العمليات، أسماء الجداول، أو نوع العملية...") },
                leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(imageVector = Icons.Default.Clear, contentDescription = null)
                        }
                    }
                },
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Target Toggle (Active vs Archive)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ChoiceChip(
                    text = "السجلات النشطة (Active)",
                    selected = searchTarget == "ACTIVE",
                    onClick = { onTargetChange("ACTIVE") }
                )
                ChoiceChip(
                    text = "جدول الأرشيف (Archived)",
                    selected = searchTarget == "ARCHIVE",
                    onClick = { onTargetChange("ARCHIVE") }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            val displayList = if (searchTarget == "ACTIVE") activeResults else archivedResults

            if (displayList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (searchQuery.isEmpty()) "لا توجد سجلات للعرض." else "لم يتم العثور على نتائج تطابق '$searchQuery'.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (searchTarget == "ACTIVE") {
                        items(activeResults) { log ->
                            AuditLogItemCard(
                                id = log.id,
                                timestamp = log.timestamp,
                                operationType = log.operationType,
                                tableName = log.tableName,
                                details = log.details,
                                dateFmt = dateFmt
                            )
                        }
                    } else {
                        items(archivedResults) { log ->
                            AuditLogItemCard(
                                id = log.originalId,
                                timestamp = log.timestamp,
                                operationType = log.operationType,
                                tableName = log.tableName,
                                details = log.details,
                                dateFmt = dateFmt,
                                badgeText = "حزمة: ${log.archivePackageName}"
                            )
                        }
                    }
                }
            }
        } else {
            // Operations History Tab (Audit the Audit system)
            if (operationsHistory.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "لا توجد عمليات إدارة مسجلة بعد.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(operationsHistory) { op ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = when (op.operationType) {
                                                "ARCHIVE" -> "📦 أرشفة"
                                                "DELETE" -> "🗑️ حذف"
                                                "CLEANUP" -> "🧹 تنظيف"
                                                "RESTORE" -> "🔄 استعادة"
                                                "EXPORT" -> "📤 تصدير"
                                                else -> op.operationType
                                            },
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "(${op.periodDescription})",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Surface(
                                        color = if (op.resultStatus == "ناجحة") MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.errorContainer,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = op.resultStatus,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (op.resultStatus == "ناجحة") MaterialTheme.colorScheme.onPrimaryContainer
                                            else MaterialTheme.colorScheme.onErrorContainer,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "السجلات: ${op.recordCount} | المدة: ${op.durationMs}ms",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                    Text(
                                        text = dateFmt.format(Date(op.timestamp)),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }

                                if (!op.notes.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = op.notes,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
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

@Composable
fun ChoiceChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
fun AuditLogItemCard(
    id: Int,
    timestamp: Long,
    operationType: String,
    tableName: String,
    details: String,
    dateFmt: SimpleDateFormat,
    badgeText: String? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "#$id",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelMedium
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = operationType,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "[$tableName]",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                Text(
                    text = dateFmt.format(Date(timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = details,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (badgeText != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

// =========================================================================
// TAB 4: RETENTION SETTINGS & SAFETY LOCK
// =========================================================================

@Composable
fun RetentionSettingsTab(
    settings: com.example.data.preferences.AuditRetentionSettings,
    onUpdatePeriod: (String) -> Unit,
    onUpdateSafetyDays: (Int) -> Unit,
    onToggleAutoExport: (Boolean) -> Unit
) {
    val periods = listOf(
        "FOREVER" to "الاحتفاظ الدائم (دائمًا - آمن افتراضياً)",
        "FIVE_YEARS" to "الاحتفاظ لمدة 5 سنوات",
        "TWO_YEARS" to "الاحتفاظ لمدة سنتين",
        "ONE_YEAR" to "الاحتفاظ لمدة سنة واحدة",
        "SIX_MONTHS" to "الاحتفاظ لمدة 6 أشهر",
        "THREE_MONTHS" to "الاحتفاظ لمدة 3 أشهر"
    )

    val safetyDays = listOf(30, 60, 90)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(
                        text = "⏳ سياسة الاحتفاظ الافتراضية بسجلات العمليات",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = "تحدد هذه السياسة المدة الزمنية لبقاء سجلات العمليات النشطة في قاعدة البيانات قبل أن تصبح مؤهلة للتطهير أو الأرشفة.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    periods.forEach { (key, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onUpdatePeriod(key) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = settings.retentionPeriodKey == key,
                                onClick = { onUpdatePeriod(key) }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(
                        text = "🔒 قفل حماية السجلات الحديثة (Safety Lock)",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = "يمنع النظام حذف السجلات الحديثة التي لم تتجاوز هذه المدة إلا بعد تأكيد صريح، لتجنب الحذف العرضي أثناء تسوية الحسابات.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        safetyDays.forEach { days ->
                            OutlinedButton(
                                onClick = { onUpdateSafetyDays(days) },
                                modifier = Modifier.weight(1f),
                                colors = if (settings.safetyLockDays == days) {
                                    ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                                } else ButtonDefaults.outlinedButtonColors()
                            ) {
                                Text("$days يومًا")
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "📤 التصدير التلقائي قبل الحذف",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "حفظ نسخة تصدير تلقائية من السجلات المحذوفة قبل تفريغها من قاعدة البيانات.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.autoExportBeforeDelete,
                        onCheckedChange = onToggleAutoExport
                    )
                }
            }
        }
    }
}

// =========================================================================
// CUSTOM RANGE CLEANUP DIALOG
// =========================================================================

@Composable
fun CustomRangeCleanupDialog(
    viewModel: AppViewModel,
    safetyLockDays: Int,
    onDismiss: () -> Unit,
    onConfirmRange: (startTime: Long, endTime: Long) -> Unit
) {
    val calendar = Calendar.getInstance()
    var startYear by remember { mutableIntStateOf(calendar.get(Calendar.YEAR)) }
    var startMonth by remember { mutableIntStateOf(calendar.get(Calendar.MONTH) + 1) }
    var startDay by remember { mutableIntStateOf(1) }

    var endYear by remember { mutableIntStateOf(calendar.get(Calendar.YEAR)) }
    var endMonth by remember { mutableIntStateOf(calendar.get(Calendar.MONTH) + 1) }
    var endDay by remember { mutableIntStateOf(calendar.get(Calendar.DAY_OF_MONTH)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تحديد فترة مخصصة للتنظيف") },
        text = {
            Column {
                Text(
                    text = "حدد تاريخ البداية والنهاية للفترة التي ترغب في تنظيف سجلاتها:",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(12.dp))

                Text("من تاريخ: $startYear/${String.format("%02d", startMonth)}/${String.format("%02d", startDay)}", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            if (startMonth > 1) startMonth-- else { startMonth = 12; startYear-- }
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(2.dp)
                    ) {
                        Text("الشهر السابق", fontSize = 11.sp)
                    }
                    Button(
                        onClick = {
                            if (startMonth < 12) startMonth++ else { startMonth = 1; startYear++ }
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(2.dp)
                    ) {
                        Text("الشهر التالي", fontSize = 11.sp)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text("إلى تاريخ: $endYear/${String.format("%02d", endMonth)}/${String.format("%02d", endDay)}", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            if (endMonth > 1) endMonth-- else { endMonth = 12; endYear-- }
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(2.dp)
                    ) {
                        Text("الشهر السابق", fontSize = 11.sp)
                    }
                    Button(
                        onClick = {
                            if (endMonth < 12) endMonth++ else { endMonth = 1; endYear++ }
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(2.dp)
                    ) {
                        Text("الشهر التالي", fontSize = 11.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val sCal = Calendar.getInstance().apply {
                        set(startYear, startMonth - 1, startDay, 0, 0, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    val eCal = Calendar.getInstance().apply {
                        set(endYear, endMonth - 1, endDay, 23, 59, 59)
                        set(Calendar.MILLISECOND, 999)
                    }
                    onConfirmRange(sCal.timeInMillis, eCal.timeInMillis)
                }
            ) {
                Text("معاينة والتأكيد")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}

// =========================================================================
// EXPORT DIALOG
// =========================================================================

@Composable
fun ExportAuditLogsDialog(
    targetMonth: MonthAuditSummary?,
    onDismiss: () -> Unit,
    onExport: (format: String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (targetMonth != null) "تصدير سجلات ${targetMonth.displayName}" else "تصدير سجل العمليات (Audit Log)",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "اختر الصيغة المناسبة لتصدير تقرير السجلات وحفظه على الهاتف:",
                    style = MaterialTheme.typography.bodySmall
                )

                OutlinedButton(
                    onClick = { onExport("CSV") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Default.TableChart, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("ملف إكسل / جدول (CSV مع دعم كامل للعربية)")
                }

                OutlinedButton(
                    onClick = { onExport("PDF") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Default.PictureAsPdf, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("مستند رسمي متعدد الصفحات (PDF)")
                }

                OutlinedButton(
                    onClick = { onExport("TXT") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Default.Description, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("تقرير نصي مفصل (Text TXT)")
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}

// Helper to share files
fun shareFile(context: Context, file: File, mimeType: String) {
    try {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "مشاركة الملف"))
    } catch (e: Exception) {
        Toast.makeText(context, "فشل فتح مشاركة الملف: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
