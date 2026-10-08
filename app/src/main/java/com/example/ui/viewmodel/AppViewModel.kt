package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import com.example.data.database.AppDatabase
import com.example.data.model.*
import com.example.data.repository.AppRepository
import com.example.data.model.ItemMovementPeriod
import com.example.data.model.ItemMovementSort
import com.example.data.model.ItemMovementSummary
import com.example.data.model.ItemMovementGlobalStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import android.os.Environment
import com.example.util.FinancialCalculator
import com.example.util.MonetaryUtils
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

enum class SecurityProtectionMode(val label: String) {
    NONE("بدون قفل (معطل)"),
    BIOMETRIC_ONLY("البصمة فقط"),
    PIN_ONLY("رقم سري فقط"),
    BIOMETRIC_AND_PIN("بصمة ورقم سري")
}

class AppViewModel(
    private val app: Application,
    private val repository: AppRepository
) : AndroidViewModel(app) {

    private val _paymentError = MutableStateFlow<String?>(null)
    val paymentError = _paymentError.asStateFlow()

    fun clearPaymentError() {
        _paymentError.value = null
    }

    val financialExceptionHandler = kotlinx.coroutines.CoroutineExceptionHandler { _, throwable ->
        android.util.Log.e("FinancialWriteOp", "Error in financial write operation", throwable)
        _paymentError.value = throwable.localizedMessage ?: "حدث خطأ غير متوقع أثناء العملية المالية"
    }

    // Paging 3 Client Stream
    private val _clientsSearchQuery = MutableStateFlow("")
    val clientsSearchQuery = _clientsSearchQuery.asStateFlow()

    fun updateClientsSearchQuery(query: String) {
        _clientsSearchQuery.value = query
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val clientsPagedFlow = _clientsSearchQuery
        .debounce(300)
        .flatMapLatest { query ->
            if (query.isBlank()) {
                repository.getAllClientsPaged(50)
            } else {
                repository.searchClientsPaged(query, 50)
            }
        }
        .cachedIn(viewModelScope)

    // Paging 3 Item Stream
    private val _itemsSearchQuery = MutableStateFlow("")
    val itemsSearchQuery = _itemsSearchQuery.asStateFlow()

    fun updateItemsSearchQuery(query: String) {
        _itemsSearchQuery.value = query
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val itemsPagedFlow = _itemsSearchQuery
        .debounce(300)
        .flatMapLatest { query ->
            if (query.isBlank()) {
                repository.getAllItemsPaged(50)
            } else {
                repository.searchItemsPaged(query, 50)
            }
        }
        .cachedIn(viewModelScope)

    // --- State Holders ---
    val clients = repository.getAllClientsFlow().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    val items = repository.getAllItemsFlow().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    val invoices = repository.getAllInvoicesFlow().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )
    val allInvoiceItems = repository.getAllInvoiceItemsFlow().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )
    val allReturns = repository.getAllReturnsFlow().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )
    val allReturnItems = repository.getAllReturnItemsFlow().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )
    val payments = repository.getAllPaymentsFlow().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    val auditLogs = repository.getAllLogsFlow().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    val storeSettings = repository.getSettingsFlow().map { it ?: StoreSettings() }.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), StoreSettings()
    )

    val installments = repository.getAllInstallmentsFlow().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    val categories = repository.getAllCategoriesFlow().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    val units = repository.getAllUnitsFlow().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    val companies = repository.getAllCompaniesFlow().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )
    
    private val _itemSuggestions = MutableStateFlow<List<Item>>(emptyList())
    val itemSuggestions = _itemSuggestions.asStateFlow()

    private var searchJob: kotlinx.coroutines.Job? = null

    fun searchItemAutocomplete(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            _itemSuggestions.value = emptyList()
            return
        }
        searchJob = viewModelScope.launch {
            kotlinx.coroutines.delay(300)
            val results = repository.autocompleteItems(query)
            _itemSuggestions.value = results.sortedWith { a, b ->
                val aExact = a.name.equals(query, ignoreCase = true)
                val bExact = b.name.equals(query, ignoreCase = true)
                if (aExact && !bExact) return@sortedWith -1
                if (!aExact && bExact) return@sortedWith 1
                val aStarts = a.name.startsWith(query, ignoreCase = true)
                val bStarts = b.name.startsWith(query, ignoreCase = true)
                if (aStarts && !bStarts) return@sortedWith -1
                if (!aStarts && bStarts) return@sortedWith 1
                a.name.compareTo(b.name)
            }.take(10)
        }
    }

    fun clearItemSuggestions() {
        searchJob?.cancel()
        _itemSuggestions.value = emptyList()
    }

    suspend fun checkItemExists(name: String): Item? {
        return repository.getItemByName(name.trim())
    }

    val backupHistory = repository.getAllBackupHistoryFlow().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    // --- Modern Backup & Restore Engine States ---
    val backupInProgress = MutableStateFlow(false)
    val backupProgressPercent = MutableStateFlow(0)
    val backupStageText = MutableStateFlow("")

    val restoreInProgress = MutableStateFlow(false)
    val restoreProgressPercent = MutableStateFlow(0)
    val restoreStageText = MutableStateFlow("")

    val exportInProgress = MutableStateFlow(false)

    // Auto-Backup Scheduling Preferences State
    val backupPreferences = com.example.data.preferences.BackupPreferences(app)
    val backupSettingsState = backupPreferences.backupSettingsFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), com.example.data.preferences.BackupSettingsState()
    )

    val isAutoBackupEnabled = MutableStateFlow(false)
    val backupFrequency = MutableStateFlow("يوميًا") // يوميًا, أسبوعيًا, كل أسبوعين, شهريًا, مخصص
    val customBackupIntervalDays = MutableStateFlow(1)
    val backupScheduleHour = MutableStateFlow(2)
    val backupScheduleMinute = MutableStateFlow(0)
    val backupDestination = MutableStateFlow("الهاتف") // الهاتف, مجلد خارجي, Google Drive
    val externalBackupFolderUri = MutableStateFlow<String?>(null)
    val backupRetentionCount = MutableStateFlow(7)

    // --- Audit Log Advanced Management & Retention States ---
    val auditRetentionPreferences = com.example.data.preferences.AuditRetentionPreferences(app)
    val auditRetentionSettings = auditRetentionPreferences.retentionSettingsFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), com.example.data.preferences.AuditRetentionSettings()
    )

    val activeAuditLogsCount = repository.getActiveLogsCountFlow().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), 0
    )

    val archivedAuditLogsCount = repository.getArchivedLogsCountFlow().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), 0
    )

    val auditOperationsHistory = repository.getAllAuditOperationsFlow().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    val monthlyAuditSummaries = MutableStateFlow<List<MonthAuditSummary>>(emptyList())
    val auditStatsSummary = MutableStateFlow(AuditStatsSummary())
    val archiveFilesList = MutableStateFlow<List<ArchiveFileInfo>>(emptyList())

    val auditOperationInProgress = MutableStateFlow(false)
    val auditProgressPercent = MutableStateFlow(0)
    val auditProgressStageText = MutableStateFlow("")

    val auditSearchQuery = MutableStateFlow("")
    val auditSearchTarget = MutableStateFlow("ACTIVE") // ACTIVE, ARCHIVE, ALL
    val activeLogsSearchResults = MutableStateFlow<List<AuditLog>>(emptyList())
    val archivedLogsSearchResults = MutableStateFlow<List<ArchivedAuditLog>>(emptyList())

    // --- Installment Reminders State (Dual Type: General & Customer-Specific) ---
    val installmentReminders = repository.getAllRemindersFlow().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    val unhandledReminders = repository.getUnhandledRemindersFlow().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    // Real-time calculation reflecting partial payments and auto-invalidating fully paid invoices/installments
    val activeReminders = combine(unhandledReminders, invoices, clients, installments) { reminders, invList, clientList, instList ->
        val invMap = invList.associateBy { it.id }
        val clientMap = clientList.associateBy { it.id }
        val instMap = instList.associateBy { it.id }
        val now = System.currentTimeMillis()

        reminders.mapNotNull { reminder ->
            if (reminder.reminderType == InstallmentReminderType.CUSTOMER_INSTALLMENT) {
                var realRemaining = reminder.remainingAmount
                var isFullyPaid = false

                if (reminder.invoiceId != null && reminder.invoiceId > 0) {
                    val inv = invMap[reminder.invoiceId]
                    if (inv == null || inv.remainingAmount <= 0.01) {
                        isFullyPaid = true
                    } else {
                        realRemaining = inv.remainingAmount
                    }
                } else if (reminder.installmentId != null && reminder.installmentId > 0) {
                    val inst = instMap[reminder.installmentId]
                    if (inst == null || inst.isPaid || (inst.amount - inst.paidAmount) <= 0.01) {
                        isFullyPaid = true
                    } else {
                        realRemaining = (inst.amount - inst.paidAmount).coerceAtLeast(0.0)
                    }
                } else if (reminder.customerId != null && reminder.customerId > 0) {
                    val cl = clientMap[reminder.customerId]
                    if (cl == null || cl.balance <= 0.01) {
                        isFullyPaid = true
                    } else {
                        realRemaining = cl.balance
                    }
                }

                if (isFullyPaid || realRemaining <= 0.01) {
                    viewModelScope.launch {
                        repository.markReminderHandled(reminder.id)
                    }
                    null
                } else {
                    val overdueDays = if (reminder.dueDate > 0 && now > reminder.dueDate) {
                        ((now - reminder.dueDate) / (1000 * 60 * 60 * 24)).toInt().coerceAtLeast(0)
                    } else 0
                    reminder.copy(remainingAmount = realRemaining, overdueDays = overdueDays)
                }
            } else {
                reminder
            }
        }
    }.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    // Legacy support for overdueInstallments: combines installments with active reminders
    val overdueInstallments = combine(installments, activeReminders) { list, _ ->
        val now = System.currentTimeMillis()
        list.filter { !it.isPaid && it.dueDate < now }
    }.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    fun dismissOverdueInstallments(ids: List<Int>) {
        viewModelScope.launch {
            activeReminders.value.forEach { rem ->
                repository.markReminderHandled(rem.id)
            }
        }
    }


    private val backupManager = com.example.util.GoogleDriveBackupManager(app)
    val hasDriveBackup = MutableStateFlow(false)
    val driveBackupStatus = MutableStateFlow<String?>(null)
    val isDriveSyncEnabled = MutableStateFlow(false)
    val driveAccountEmail = MutableStateFlow<String?>(null)
    val lastBackupTimeStr = MutableStateFlow("لم يتم إنشاء نسخة بعد")
    val availableBackupFiles = MutableStateFlow<List<com.example.util.BackupFileInfo>>(emptyList())

    // Enhanced Google Drive Sync Observables
    val isDriveBackingUp = MutableStateFlow(false)
    val driveBackupProgress = MutableStateFlow(0)
    val driveBackupStage = MutableStateFlow("")
    val driveCloudBackups = MutableStateFlow<List<com.example.util.GoogleDriveFileMetadata>>(emptyList())
    val isDriveCloudBackupsLoading = MutableStateFlow(false)
    val driveAuthRecoverableIntent = MutableStateFlow<android.content.Intent?>(null)

    // Google Drive Smart Scheduling & Battery Observables
    val isDriveScheduleEnabled = MutableStateFlow(true)
    val driveScheduleFrequency = MutableStateFlow("DAILY") // DAILY, WEEKLY, MONTHLY
    val driveScheduleHour = MutableStateFlow(7)
    val driveScheduleMinute = MutableStateFlow(30)
    val driveScheduleDayOfWeek = MutableStateFlow(java.util.Calendar.FRIDAY)
    val driveScheduleDayOfMonth = MutableStateFlow(1)
    val isBatteryOptimizationIgnored = MutableStateFlow(false)

    val exchangeRates = repository.getAllRatesFlow().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    fun saveExchangeRate(fromCurrency: String, toCurrency: String, rate: Double) {
        if (rate.isNaN() || rate.isInfinite() || rate <= 0.0) {
            _paymentError.value = "سعر الصرف يجب أن يكون رقماً أكبر من صفر"
            return
        }
        val normFrom = com.example.domain.currency.Currency.fromCodeOrName(fromCurrency).nameAr
        val normTo = com.example.domain.currency.Currency.fromCodeOrName(toCurrency).nameAr

        viewModelScope.launch(financialExceptionHandler) {
            // Delete inverse pair B -> A if it exists to prevent duplicates
            val inverseExisting = repository.getRateBetween(normTo, normFrom)
            if (inverseExisting != null) {
                repository.deleteRate(inverseExisting)
            }

            val existing = repository.getRateBetween(normFrom, normTo)
            if (existing != null) {
                repository.updateRate(existing.copy(rate = rate, lastUpdated = System.currentTimeMillis()))
            } else {
                repository.insertRate(
                    com.example.data.model.ExchangeRate(
                        fromCurrency = normFrom,
                        toCurrency = normTo,
                        rate = rate,
                        lastUpdated = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    fun deleteExchangeRate(rate: com.example.data.model.ExchangeRate) {
        viewModelScope.launch {
            repository.deleteRate(rate)
        }
    }

    fun getGoogleSignInClient(activityContext: Context) = backupManager.getGoogleSignInClient(activityContext)
    fun getLastSignedInGoogleAccount() = backupManager.getLastSignedInAccount()

    init {
        val drivePrefs = app.getSharedPreferences("drive_sync_prefs", Context.MODE_PRIVATE)
        isDriveSyncEnabled.value = drivePrefs.getBoolean("is_sync_enabled", false)
        driveAccountEmail.value = drivePrefs.getString("drive_account_email", null)

        isDriveScheduleEnabled.value = com.example.util.GoogleDriveSyncScheduler.isScheduleEnabled(app)
        driveScheduleFrequency.value = com.example.util.GoogleDriveSyncScheduler.getFrequency(app)
        driveScheduleHour.value = com.example.util.GoogleDriveSyncScheduler.getScheduleHour(app)
        driveScheduleMinute.value = com.example.util.GoogleDriveSyncScheduler.getScheduleMinute(app)
        driveScheduleDayOfWeek.value = com.example.util.GoogleDriveSyncScheduler.getScheduleDayOfWeek(app)
        driveScheduleDayOfMonth.value = com.example.util.GoogleDriveSyncScheduler.getScheduleDayOfMonth(app)
        isBatteryOptimizationIgnored.value = com.example.util.GoogleDriveSyncScheduler.isIgnoringBatteryOptimizations(app)

        if (isDriveSyncEnabled.value && isDriveScheduleEnabled.value && !driveAccountEmail.value.isNullOrBlank()) {
            com.example.util.GoogleDriveSyncScheduler.scheduleNextAlarm(app)
            com.example.util.GoogleDriveSyncScheduler.checkAndTriggerCatchUpIfNeeded(app)
        }

        val backupPrefs = app.getSharedPreferences("backup_scheduler_prefs", Context.MODE_PRIVATE)
        isAutoBackupEnabled.value = backupPrefs.getBoolean("auto_backup_enabled", false)
        backupFrequency.value = backupPrefs.getString("backup_frequency", "يوميًا") ?: "يوميًا"
        customBackupIntervalDays.value = backupPrefs.getInt("custom_interval_days", 1)
        backupScheduleHour.value = backupPrefs.getInt("backup_schedule_hour", 2)
        backupScheduleMinute.value = backupPrefs.getInt("backup_schedule_minute", 0)
        backupDestination.value = backupPrefs.getString("backup_destination", "الهاتف") ?: "الهاتف"
        externalBackupFolderUri.value = backupPrefs.getString("external_folder_uri", null)
        backupRetentionCount.value = backupPrefs.getInt("backup_retention_count", 7)

        checkDriveBackup()
        seedDefaultsIfEmpty()
        syncTodayGeneralReminderIfDue()
        refreshAuditStatsAndSummaries()
    }

    private fun seedDefaultsIfEmpty() {
        viewModelScope.launch {
            val existingCats = repository.getAllCategories()
            val now = System.currentTimeMillis()
            if (existingCats.isEmpty()) {
                val defaultCategories = listOf("كواشف", "أدوية", "مستلزمات طبية", "أدوات", "أخرى")
                defaultCategories.forEach { cat ->
                    repository.insertCategory(ItemCategory(name = cat, isPermanent = true, createdAt = now))
                }
            }
            val existingUnits = repository.getAllUnits()
            if (existingUnits.isEmpty()) {
                val defaultUnits = listOf("قطعة", "علبة", "كرتون", "زجاجة", "شريط", "كيس", "صندوق")
                defaultUnits.forEach { u ->
                    repository.insertUnit(ItemUnit(name = u, isPermanent = true, createdAt = now))
                }
            }
            val existingCompanies = repository.getAllCompanies()
            if (existingCompanies.isEmpty()) {
                val defaultCompanies = listOf(
                    Triple("شركة الجبل", "770000001", "صنعاء"),
                    Triple("شركة العابد", "770000002", "تعز")
                )
                defaultCompanies.forEach { (compName, compPhone, compAddr) ->
                    repository.insertCompany(SupplierCompany(name = compName, phone = compPhone, address = compAddr, createdAt = now))
                }
            }
        }
    }

    fun checkDriveBackup() {
        viewModelScope.launch {
            hasDriveBackup.value = backupManager.checkForAvailableBackup()
            lastBackupTimeStr.value = backupManager.getLastBackupTimeFormatted()
            loadAvailableBackupFiles()
        }
    }

    fun loadAvailableBackupFiles() {
        viewModelScope.launch {
            availableBackupFiles.value = backupManager.getAvailableBackupFiles()
        }
    }

    fun setDriveAccountEmail(email: String) {
        val trimmed = email.trim()
        if (trimmed.isNotBlank()) {
            driveAccountEmail.value = trimmed
            isDriveSyncEnabled.value = true
            val drivePrefs = app.getSharedPreferences("drive_sync_prefs", Context.MODE_PRIVATE)
            drivePrefs.edit()
                .putBoolean("is_sync_enabled", true)
                .putString("drive_account_email", trimmed)
                .apply()
            driveBackupStatus.value = "تم تعيين البريد: $trimmed بنجاح"
            Toast.makeText(app, "تم تعيين البريد الإلكتروني: $trimmed", Toast.LENGTH_SHORT).show()
        }
    }

    fun onGoogleDriveAccountSelected(account: com.google.android.gms.auth.api.signin.GoogleSignInAccount) {
        val email = account.email ?: return
        isDriveSyncEnabled.value = true
        driveAccountEmail.value = email
        backupManager.setSyncEnabled(true, email)
        performGoogleDriveBackup(email)
    }

    fun toggleDriveSync(enabled: Boolean, email: String? = null) {
        isDriveSyncEnabled.value = enabled
        val targetEmail = email ?: driveAccountEmail.value
        driveAccountEmail.value = if (enabled) targetEmail else null
        backupManager.setSyncEnabled(enabled, targetEmail)

        if (enabled && !targetEmail.isNullOrBlank()) {
            com.example.util.GoogleDriveSyncScheduler.scheduleNextAlarm(app)
            com.example.util.GoogleDriveSyncScheduler.checkAndTriggerCatchUpIfNeeded(app)
            com.example.util.GoogleDriveSyncWorker.enqueuePeriodicSync(app)
            performGoogleDriveBackup(targetEmail)
        } else if (!enabled) {
            com.example.util.GoogleDriveSyncScheduler.cancelSchedule(app)
            com.example.util.GoogleDriveSyncWorker.cancelSync(app)
            driveBackupStatus.value = "تم إيقاف المزامنة التلقائية مع Google Drive"
        }
    }

    fun setDriveScheduleEnabled(enabled: Boolean) {
        isDriveScheduleEnabled.value = enabled
        com.example.util.GoogleDriveSyncScheduler.setScheduleEnabled(app, enabled)
    }

    fun setDriveScheduleFrequency(frequency: String) {
        driveScheduleFrequency.value = frequency
        com.example.util.GoogleDriveSyncScheduler.setFrequency(app, frequency)
    }

    fun setDriveScheduleTime(hour: Int, minute: Int) {
        driveScheduleHour.value = hour
        driveScheduleMinute.value = minute
        com.example.util.GoogleDriveSyncScheduler.setScheduleTime(app, hour, minute)
    }

    fun setDriveScheduleDayOfWeek(dayOfWeek: Int) {
        driveScheduleDayOfWeek.value = dayOfWeek
        com.example.util.GoogleDriveSyncScheduler.setScheduleDayOfWeek(app, dayOfWeek)
    }

    fun setDriveScheduleDayOfMonth(dayOfMonth: Int) {
        driveScheduleDayOfMonth.value = dayOfMonth
        com.example.util.GoogleDriveSyncScheduler.setScheduleDayOfMonth(app, dayOfMonth)
    }

    fun refreshBatteryOptimizationStatus() {
        isBatteryOptimizationIgnored.value = com.example.util.GoogleDriveSyncScheduler.isIgnoringBatteryOptimizations(app)
    }

    fun requestIgnoreBatteryOptimizations() {
        com.example.util.GoogleDriveSyncScheduler.requestIgnoreBatteryOptimizations(app)
    }

    fun performGoogleDriveBackup(customEmail: String? = null) {
        if (isDriveBackingUp.value) return
        val targetEmail = customEmail ?: driveAccountEmail.value ?: backupManager.getSavedAccountEmail()
        if (targetEmail.isNullOrBlank()) {
            driveBackupStatus.value = "يرجى اختيار حساب Google لبدء المزامنة"
            return
        }

        viewModelScope.launch {
            isDriveBackingUp.value = true
            driveBackupProgress.value = 5
            driveBackupStage.value = "بدء الاتصال بحساب Google..."
            driveBackupStatus.value = "جاري التحقق من صلاحيات Google Drive..."

            val result = backupManager.createAndUploadBackup(
                db = AppDatabase.getDatabase(app),
                accountEmail = targetEmail,
                onProgress = { percent, stage ->
                    driveBackupProgress.value = percent
                    driveBackupStage.value = stage
                    driveBackupStatus.value = "$stage ($percent%)"
                }
            )

            isDriveBackingUp.value = false
            result.onSuccess { msg ->
                com.example.util.GoogleDriveSyncScheduler.recordSuccessForCurrentPeriod(app)
                driveBackupStatus.value = msg
                hasDriveBackup.value = true
                lastBackupTimeStr.value = backupManager.getLastBackupTimeFormatted()
                loadAvailableBackupFiles()
                loadGoogleDriveBackups(targetEmail)
                Toast.makeText(app, "تمت مزامنة ورفع النسخة الاحتياطية إلى Google Drive بنجاح!", Toast.LENGTH_LONG).show()
            }.onFailure { err ->
                if (err is com.google.android.gms.auth.UserRecoverableAuthException) {
                    driveAuthRecoverableIntent.value = err.intent
                    driveBackupStatus.value = "يتطلب حساب Google إذن المزامنة. يرجى منح الإذن عند المطالبة."
                } else {
                    val msg = err.localizedMessage ?: "فشلت المزامنة مع Google Drive"
                    driveBackupStatus.value = "خطأ في المزامنة: $msg"
                    Toast.makeText(app, msg, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun loadGoogleDriveBackups(accountEmail: String? = null) {
        val targetEmail = accountEmail ?: driveAccountEmail.value ?: backupManager.getSavedAccountEmail()
        if (targetEmail.isNullOrBlank()) return

        viewModelScope.launch {
            isDriveCloudBackupsLoading.value = true
            val result = backupManager.listBackupsFromDrive(targetEmail)
            isDriveCloudBackupsLoading.value = false
            result.onSuccess { list ->
                driveCloudBackups.value = list
            }.onFailure { err ->
                if (err is com.google.android.gms.auth.UserRecoverableAuthException) {
                    driveAuthRecoverableIntent.value = err.intent
                }
            }
        }
    }

    fun restoreGoogleDriveCloudBackup(
        fileMeta: com.example.util.GoogleDriveFileMetadata,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val targetEmail = driveAccountEmail.value ?: backupManager.getSavedAccountEmail()
        viewModelScope.launch {
            isDriveBackingUp.value = true
            driveBackupProgress.value = 10
            driveBackupStage.value = "جاري تنزيل النسخة (${fileMeta.name}) من Google Drive..."
            driveBackupStatus.value = driveBackupStage.value

            val result = backupManager.restoreFromDriveFile(
                db = AppDatabase.getDatabase(app),
                fileId = fileMeta.id,
                fileName = fileMeta.name,
                accountEmail = targetEmail,
                onProgress = { p, stage ->
                    driveBackupProgress.value = p
                    driveBackupStage.value = stage
                    driveBackupStatus.value = stage
                }
            )

            isDriveBackingUp.value = false
            result.onSuccess { msg ->
                driveBackupStatus.value = msg
                hasDriveBackup.value = true
                lastBackupTimeStr.value = backupManager.getLastBackupTimeFormatted()
                refreshAuditStatsAndSummaries()
                loadAvailableBackupFiles()
                onSuccess(msg)
            }.onFailure { err ->
                val errorMsg = err.localizedMessage ?: "فشلت الاستعادة من Google Drive"
                driveBackupStatus.value = "خطأ في الاستعادة: $errorMsg"
                onError(errorMsg)
            }
        }
    }

    fun restoreBackupFromFile(fileInfo: com.example.util.BackupFileInfo, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            driveBackupStatus.value = "جاري قراءة ملف (${fileInfo.fileName}) واستعادة البيانات..."
            val result = backupManager.restoreFromSpecificFile(AppDatabase.getDatabase(app), fileInfo.file)
            result.onSuccess { msg ->
                driveBackupStatus.value = msg
                hasDriveBackup.value = true
                lastBackupTimeStr.value = backupManager.getLastBackupTimeFormatted()
                refreshAuditStatsAndSummaries()
                onSuccess(msg)
            }.onFailure { err ->
                driveBackupStatus.value = "خطأ في الاستعادة: ${err.message}"
                onError(err.message ?: "فشلت الاستعادة")
            }
        }
    }

    fun restoreGoogleDriveBackup(onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            driveBackupStatus.value = "جاري استعادة أحدث نسخة احتياطية متوفرة..."
            val result = backupManager.restoreLatestBackup(AppDatabase.getDatabase(app))
            result.onSuccess { msg ->
                driveBackupStatus.value = msg
                hasDriveBackup.value = true
                lastBackupTimeStr.value = backupManager.getLastBackupTimeFormatted()
                refreshAuditStatsAndSummaries()
                onSuccess(msg)
            }.onFailure { err ->
                driveBackupStatus.value = "خطأ في الاستعادة: ${err.message}"
                onError(err.message ?: "فشلت الاستعادة")
            }
        }
    }

    private fun triggerAutoDriveBackup() {
        if (!isDriveSyncEnabled.value) return
        try {
            com.example.util.GoogleDriveSyncWorker.enqueueInstantSync(app)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        viewModelScope.launch {
            try {
                lastBackupTimeStr.value = backupManager.getLastBackupTimeFormatted()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // --- Multi-Currency Financial Dashboard Intelligence ---
    val selectedDashboardCurrency = MutableStateFlow("YER") 
    val selectedDashboardPeriod = MutableStateFlow(DashboardPeriod.THIS_MONTH)

    fun setDashboardCurrencyFilter(currencyCode: String) {
        selectedDashboardCurrency.value = currencyCode
    }

    fun setDashboardPeriodFilter(period: DashboardPeriod) {
        selectedDashboardPeriod.value = period
    }

    private data class FiveFlows(
        val clList: List<Client>,
        val invList: List<Invoice>,
        val payList: List<Payment>,
        val instList: List<Installment>,
        val settings: StoreSettings
    )

    val financialDashboardState: StateFlow<FinancialDashboardUiState> = combine(
        combine(clients, invoices, payments, installments, storeSettings) { clList, invList, payList, instList, settings ->
            FiveFlows(clList, invList, payList, instList, settings)
        },
        selectedDashboardCurrency,
        selectedDashboardPeriod
    ) { five, curr, period ->
        FinancialCalculator.computeDashboardState(
            clientsList = five.clList,
            invoicesList = five.invList,
            paymentsList = five.payList,
            installmentsList = five.instList,
            storeSettings = five.settings,
            selectedCurrencyCode = curr,
            selectedPeriod = period
        )
    }.flowOn(kotlinx.coroutines.Dispatchers.Default).stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        FinancialDashboardUiState(isLoading = true)
    )

    val topMovingPeriod = MutableStateFlow(ItemMovementPeriod.THIS_MONTH)
    val topMovingCurrency = MutableStateFlow("الكل")
    val topMovingSearchQuery = MutableStateFlow("")
    val topMovingSortBy = MutableStateFlow(ItemMovementSort.QUANTITY)

    val topMovingItemsState: StateFlow<Pair<List<ItemMovementSummary>, ItemMovementGlobalStats>> = combine(
        combine(invoices, allInvoiceItems, allReturns, allReturnItems) { invs, invItems, rets, retItems ->
            Pair(invs, invItems) to Pair(rets, retItems)
        },
        combine(items, clients, topMovingPeriod, topMovingCurrency) { its, cls, period, currency ->
            Pair(its, cls) to Pair(period, currency)
        },
        combine(topMovingSearchQuery, topMovingSortBy) { query, sortBy ->
            Pair(query, sortBy)
        }
    ) { group1, group2, group3 ->
        val (invs, invItems) = group1.first
        val (rets, retItems) = group1.second
        val (its, cls) = group2.first
        val (period, currency) = group2.second
        val (searchQuery, sortBy) = group3

        com.example.util.ItemMovementAnalyzer.analyze(
            invoices = invs,
            invoiceItems = invItems,
            returns = rets,
            returnItems = retItems,
            itemsCatalog = its,
            clients = cls,
            period = period,
            currencyFilter = currency,
            searchQuery = searchQuery,
            sortBy = sortBy
        )
    }
    .flowOn(kotlinx.coroutines.Dispatchers.Default)
    .distinctUntilChanged()
    .stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        Pair(emptyList(), ItemMovementGlobalStats(0, 0, 0, 0.0, 0, null, 0, null, "الريال اليمني"))
    )

    val itemMovementState: StateFlow<Pair<List<ItemMovementSummary>, ItemMovementGlobalStats>> = combine(
        combine(invoices, allInvoiceItems, allReturns, allReturnItems) { invs, invItems, rets, retItems ->
            Pair(invs, invItems) to Pair(rets, retItems)
        },
        combine(items, clients, selectedDashboardPeriod, selectedDashboardCurrency) { its, cls, period, currency ->
            Pair(its, cls) to Pair(period, currency)
        }
    ) { group1, group2 ->
        val (invs, invItems) = group1.first
        val (rets, retItems) = group1.second
        val (its, cls) = group2.first
        val (period, currency) = group2.second

        val movementPeriod = when (period) {
            DashboardPeriod.TODAY -> ItemMovementPeriod.TODAY
            DashboardPeriod.THIS_WEEK -> ItemMovementPeriod.THIS_WEEK
            DashboardPeriod.THIS_MONTH -> ItemMovementPeriod.THIS_MONTH
            DashboardPeriod.THIS_YEAR -> ItemMovementPeriod.THIS_YEAR
            DashboardPeriod.ALL -> ItemMovementPeriod.ALL_TIME
        }
        val currencyFilter = if (currency == "ALL") "الكل" else currency

        com.example.util.ItemMovementAnalyzer.analyze(
            invoices = invs,
            invoiceItems = invItems,
            returns = rets,
            returnItems = retItems,
            itemsCatalog = its,
            clients = cls,
            period = movementPeriod,
            currencyFilter = currencyFilter,
            sortBy = ItemMovementSort.QUANTITY
        )
    }
    .flowOn(kotlinx.coroutines.Dispatchers.Default)
    .distinctUntilChanged()
    .stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        Pair(emptyList(), ItemMovementGlobalStats(0, 0, 0, 0.0, 0, null, 0, null, "الريال اليمني"))
    )

    // --- Combined Screen States ---
    data class ClientsScreenData(
        val clients: List<com.example.data.model.Client>,
        val invoices: List<com.example.data.model.Invoice>,
        val payments: List<com.example.data.model.Payment>,
        val installments: List<com.example.data.model.Installment>,
        val exchangeRates: List<com.example.data.model.ExchangeRate>
    )

    val clientsScreenData: StateFlow<ClientsScreenData> = combine(
        clients, invoices, payments, installments, exchangeRates
    ) { cls, invs, pays, insts, rates ->
        ClientsScreenData(cls, invs, pays, insts, rates)
    }
    .flowOn(kotlinx.coroutines.Dispatchers.Default)
    .stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        ClientsScreenData(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
    )

    data class InvoicesScreenData(
        val invoices: List<com.example.data.model.Invoice>,
        val clients: List<com.example.data.model.Client>,
        val exchangeRates: List<com.example.data.model.ExchangeRate>
    )

    val invoicesScreenData: StateFlow<InvoicesScreenData> = combine(
        invoices, clients, exchangeRates
    ) { invs, cls, rates ->
        InvoicesScreenData(invs, cls, rates)
    }
    .flowOn(kotlinx.coroutines.Dispatchers.Default)
    .stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        InvoicesScreenData(emptyList(), emptyList(), emptyList())
    )

    data class InstallmentsScreenData(
        val installments: List<com.example.data.model.Installment>,
        val clients: List<com.example.data.model.Client>,
        val invoices: List<com.example.data.model.Invoice>
    )

    val installmentsScreenData: StateFlow<InstallmentsScreenData> = combine(
        installments, clients, invoices
    ) { insts, cls, invs ->
        InstallmentsScreenData(insts, cls, invs)
    }
    .flowOn(kotlinx.coroutines.Dispatchers.Default)
    .stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        InstallmentsScreenData(emptyList(), emptyList(), emptyList())
    )

    // Use a trigger flow to refresh dashboard stats when underlying data changes or filters change
    private val dashboardTrigger = combine(
        selectedDashboardCurrency,
        selectedDashboardPeriod,
        repository.getAllInvoicesFlow().map { it.size }, 
        repository.getAllPaymentsFlow().map { it.size },
        repository.getAllClientsFlow().map { it.size }
    ) { curr, period, _, _, _ -> Pair(curr, period) }

    private fun getPeriodStart(period: DashboardPeriod, now: Long): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        return when (period) {
            DashboardPeriod.TODAY -> cal.apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }.timeInMillis
            DashboardPeriod.THIS_WEEK -> cal.apply { set(Calendar.DAY_OF_WEEK, firstDayOfWeek); set(Calendar.HOUR_OF_DAY, 0) }.timeInMillis
            DashboardPeriod.THIS_MONTH -> cal.apply { set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0) }.timeInMillis
            DashboardPeriod.THIS_YEAR -> cal.apply { set(Calendar.DAY_OF_YEAR, 1); set(Calendar.HOUR_OF_DAY, 0) }.timeInMillis
            DashboardPeriod.ALL -> 0L
        }
    }

    // --- Dashboard Statistics State ---
    @OptIn(ExperimentalCoroutinesApi::class)
    val dashboardStats = dashboardTrigger.transformLatest { _ ->
        emit(DashboardStats()) // Optional: initial empty or loading state
        
        val totalClients = repository.getClientsCount()
        val totalItems = repository.getItemsCount()
        
        // Multi-currency stats from SQL
        // We focus on the selected dashboard currency for now, 
        // but we could also get totals for all currencies if needed.
        val currCode = selectedDashboardCurrency.value
        val now = System.currentTimeMillis()
        val start = 0L // All time for overall stats
        val end = Long.MAX_VALUE
        
        val (totalSales, totalReceived, invCount) = repository.getDashboardTotals(currCode, start, end)
        val payCount = repository.getPaymentsCount(currCode, start, end)
        
        val recentActivities = repository.getRecentActivities(20)
        
        // For top debtors and active clients, we might still need some limited SQL or in-memory for small sets.
        // But for 100k records, we definitely want SQL.
        val topDebtors = repository.getTopDebtors(5)
        
        emit(DashboardStats(
            totalClients = totalClients,
            totalDebt = totalSales - totalReceived, // Simplified
            totalReceived = totalReceived,
            netBalance = totalSales - totalReceived,
            invoicesCount = invCount,
            paymentsCount = payCount,
            itemsCount = totalItems,
            recentActivities = recentActivities,
            topDebtors = topDebtors,
            currencyDebts = mapOf(currCode to (totalSales - totalReceived)),
            currencyPayments = mapOf(currCode to totalReceived)
        ))
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        DashboardStats()
    )

    data class DashboardScreenData(
        val dashboardState: FinancialDashboardUiState,
        val stats: DashboardStats,
        val settings: StoreSettings,
        val activeReminders: List<InstallmentReminder>,
        val clients: List<Client>,
        val itemMovement: Pair<List<ItemMovementSummary>, ItemMovementGlobalStats>
    )

    val dashboardScreenData: StateFlow<DashboardScreenData> = combine(
        financialDashboardState,
        dashboardStats,
        storeSettings,
        activeReminders,
        clients,
        itemMovementState
    ) { array ->
        @Suppress("UNCHECKED_CAST")
        DashboardScreenData(
            dashboardState = array[0] as FinancialDashboardUiState,
            stats = array[1] as DashboardStats,
            settings = array[2] as StoreSettings,
            activeReminders = array[3] as List<InstallmentReminder>,
            clients = array[4] as List<Client>,
            itemMovement = array[5] as Pair<List<ItemMovementSummary>, ItemMovementGlobalStats>
        )
    }
    .flowOn(kotlinx.coroutines.Dispatchers.Default)
    .stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        DashboardScreenData(
            FinancialDashboardUiState(isLoading = true),
            DashboardStats(),
            StoreSettings(),
            emptyList(),
            emptyList(),
            Pair(emptyList(), ItemMovementGlobalStats(0, 0, 0, 0.0, 0, null, 0, null, "الريال اليمني"))
        )
    )

    // --- Search Queries & Filtering State ---
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    // Smart Search Results
    @OptIn(ExperimentalCoroutinesApi::class)
    val smartSearchResults = searchQuery.debounce(300).transformLatest { query ->
        if (query.isBlank()) {
            emit(SmartSearchResult())
            return@transformLatest
        }
        
        val q = query.trim()
        val clientsResult = repository.searchClients(q).firstOrNull() ?: emptyList()
        val itemsResult = repository.searchItems(q).firstOrNull() ?: emptyList()
        val invoicesResult = repository.searchInvoices(q)
        
        emit(SmartSearchResult(
            clients = clientsResult.take(10),
            items = itemsResult.take(10),
            invoices = invoicesResult.take(10)
        ))
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        SmartSearchResult()
    )

    // --- Invoice Creation Draft State ---
    private val _selectedClient = MutableStateFlow<Client?>(null)
    val selectedClient = _selectedClient.asStateFlow()

    private val _invoiceCart = MutableStateFlow<List<CartItem>>(emptyList())
    val invoiceCart = _invoiceCart.asStateFlow()

    private val _discount = MutableStateFlow(0.0)
    val discount = _discount.asStateFlow()

    private val _taxRate = MutableStateFlow(15.0) // 15% default tax
    val taxRate = _taxRate.asStateFlow()

    private val _invoiceNotes = MutableStateFlow("")
    val invoiceNotes = _invoiceNotes.asStateFlow()

    fun setInvoiceClient(client: Client?) {
        _selectedClient.value = client
    }

    fun addItemToCart(item: Item, qty: Int = 1) {
        val current = _invoiceCart.value.toMutableList()
        val index = current.indexOfFirst { it.item.id == item.id }
        if (index != -1) {
            val updated = current[index].copy(
                quantity = current[index].quantity + qty,
                quantityInput = (current[index].quantity + qty).toString()
            )
            current[index] = updated
        } else {
            // By default, start with empty string for quantity and default currency to "الريال اليمني"
            val defaultCurr = "الريال اليمني"
            val itemOrigCurr = item.sellingCurrency.ifBlank { "الريال اليمني" }
            val initPrice = if (itemOrigCurr == defaultCurr) {
                item.sellingPrice
            } else {
                val conv = MonetaryUtils.convert(
                    amount = item.sellingPrice,
                    fromCurrency = itemOrigCurr,
                    toCurrency = defaultCurr,
                    rates = exchangeRates.value
                )
                if (!conv.isValid) {
                    val fromEnum = com.example.domain.currency.Currency.fromCodeOrName(itemOrigCurr)
                    val toEnum = com.example.domain.currency.Currency.fromCodeOrName(defaultCurr)
                    _paymentError.value = "لم يتم العثور على سعر صرف بين ${fromEnum.nameAr} و ${toEnum.nameAr}. يرجى إضافة سعر الصرف أولاً."
                    return
                }
                conv.convertedAmount
            }
            current.add(CartItem(
                item = item,
                quantity = 0,
                customPrice = initPrice,
                customCurrency = defaultCurr,
                quantityInput = ""
            ))
        }
        _invoiceCart.value = current
    }

    fun removeCartItem(cartItem: CartItem) {
        _invoiceCart.value = _invoiceCart.value.filter { it.item.id != cartItem.item.id }
    }

    fun updateCartItemQuantity(cartItem: CartItem, newQtyInput: String) {
        val qty = newQtyInput.filter { it.isDigit() }.toIntOrNull() ?: 0
        _invoiceCart.value = _invoiceCart.value.map {
            if (it.item.id == cartItem.item.id) {
                it.copy(quantity = qty, quantityInput = newQtyInput)
            } else it
        }
    }

    fun updateCartItemPrice(cartItem: CartItem, newPrice: Double) {
        _invoiceCart.value = _invoiceCart.value.map {
            if (it.item.id == cartItem.item.id) it.copy(customPrice = newPrice) else it
        }
    }

    fun updateCartItemCurrency(cartItem: CartItem, newCurrency: String) {
        val rates = exchangeRates.value
        val oldCurrency = cartItem.customCurrency.ifBlank { "الريال اليمني" }
        val newPrice = if (oldCurrency == newCurrency) {
            cartItem.customPrice
        } else {
            val conv = MonetaryUtils.convert(
                amount = cartItem.customPrice,
                fromCurrency = oldCurrency,
                toCurrency = newCurrency,
                rates = rates
            )
            conv.convertedAmount
        }
        _invoiceCart.value = _invoiceCart.value.map {
            if (it.item.id == cartItem.item.id) it.copy(
                customCurrency = newCurrency,
                customPrice = newPrice
            ) else it
        }
    }

    fun setDiscount(amount: Double) {
        _discount.value = amount
    }

    fun setTaxRate(rate: Double) {
        _taxRate.value = rate
    }

    fun setInvoiceNotes(notes: String) {
        _invoiceNotes.value = notes
    }

    fun clearInvoiceDraft() {
        _selectedClient.value = null
        _invoiceCart.value = emptyList()
        _discount.value = 0.0
        _taxRate.value = 15.0
        _invoiceNotes.value = ""
    }

    // Save Final Detailed Invoice
    fun saveDetailedInvoice(
        paidAmount: Double = 0.0,
        paymentMethod: String = "نقدي",
        isDraft: Boolean = false,
        currency: String = "الريال اليمني",
        isCreditOverride: Boolean = false,
        overrideAuthorizer: String? = null,
        overrideReason: String? = null,
        overrideAmount: Double = 0.0,
        customInvoiceNumber: String? = null,
        customDateMillis: Long? = null,
        paymentVoucherNumber: String? = null,
        onSuccess: (Int) -> Unit
    ) {
        val client = _selectedClient.value ?: return
        val cart = _invoiceCart.value
        if (cart.isEmpty() && !isDraft) return

        // Stock validation guard: منع سحب كميات تفوق رصيد المخزن
        if (!isDraft) {
            val overStock = cart.firstOrNull { it.quantity > it.item.quantity }
            if (overStock != null) {
                return
            }
        }

        viewModelScope.launch(financialExceptionHandler) {
            val settings = storeSettings.value
            val ratesList = repository.getAllRates()
            val rateMap = ratesList.associate {
                (com.example.domain.currency.Currency.fromCodeOrName(it.fromCurrency) to com.example.domain.currency.Currency.fromCodeOrName(it.toCurrency)) to java.math.BigDecimal.valueOf(it.rate)
            }

            val invoiceCurrency = com.example.domain.currency.Currency.fromCodeOrName(currency)
            val clientAccountCurr = com.example.domain.currency.Currency.fromCodeOrName(client.currency)

            var subtotalInInvoiceCurrency = java.math.BigDecimal.ZERO
            var subtotalInBaseCurrency = java.math.BigDecimal.ZERO

            val invoiceItems = cart.map { cartItem ->
                val lineCurrency = com.example.domain.currency.Currency.fromCodeOrName(cartItem.customCurrency)
                val unitPriceMoney = com.example.domain.currency.Money(cartItem.customPrice, lineCurrency)
                val lineTotalMoney = unitPriceMoney * cartItem.quantity

                // Convert line total to Invoice Currency
                val convToInvoice = com.example.domain.currency.CurrencyConversionService.convert(lineTotalMoney, invoiceCurrency, rateMap)
                subtotalInInvoiceCurrency = subtotalInInvoiceCurrency.add(convToInvoice.convertedMoney.amount)

                // Convert line total to Customer Account Currency (baseCurrency)
                val convToBase = com.example.domain.currency.CurrencyConversionService.convert(lineTotalMoney, clientAccountCurr, rateMap)
                subtotalInBaseCurrency = subtotalInBaseCurrency.add(convToBase.convertedMoney.amount)

                InvoiceItem(
                    invoiceId = 0, // will be overwritten in transaction
                    itemId = cartItem.item.id,
                    itemName = cartItem.item.name,
                    quantity = cartItem.quantity,
                    unitPrice = cartItem.customPrice,
                    totalPrice = lineTotalMoney.toDouble(),
                    currency = lineCurrency.nameAr,
                    exchangeRate = convToInvoice.exchangeRate.toDouble(),
                    convertedTotalPrice = convToInvoice.convertedMoney.toDouble(),
                    originalProductPrice = cartItem.item.sellingPrice,
                    originalProductCurrency = cartItem.item.sellingCurrency
                )
            }

            val discountMoneyInInvoice = com.example.domain.currency.Money(_discount.value, invoiceCurrency)
            val taxAmountInInvoice = if (settings.showVatAndSubtotal) {
                (subtotalInInvoiceCurrency.subtract(discountMoneyInInvoice.amount)).multiply(java.math.BigDecimal.valueOf(_taxRate.value / 100.0))
            } else java.math.BigDecimal.ZERO

            val totalAmountInInvoiceCurrency = subtotalInInvoiceCurrency.subtract(discountMoneyInInvoice.amount).add(taxAmountInInvoice).coerceAtLeast(java.math.BigDecimal.ZERO)

            // Convert total invoice amount to Customer Account Currency
            val convInvoiceToBase = com.example.domain.currency.CurrencyConversionService.convert(
                com.example.domain.currency.Money(totalAmountInInvoiceCurrency, invoiceCurrency),
                clientAccountCurr,
                rateMap
            )

            val invoiceNumber = if (!customInvoiceNumber.isNullOrBlank()) {
                customInvoiceNumber.trim()
            } else if (settings.isAutoNumberingEnabled) {
                val nextNum = settings.lastInvoiceNumber + 1
                if (settings.invoicePrefix.isNotBlank()) "${settings.invoicePrefix}$nextNum" else "$nextNum"
            } else {
                "${settings.lastInvoiceNumber + 1}"
            }

            val invoiceDate = customDateMillis ?: System.currentTimeMillis()
            val totalAmtDouble = totalAmountInInvoiceCurrency.setScale(2, java.math.RoundingMode.HALF_UP).toDouble()

            val invoice = Invoice(
                invoiceNumber = invoiceNumber,
                date = invoiceDate,
                clientId = client.id,
                clientName = client.name,
                isQuickInvoice = false,
                discount = _discount.value,
                taxRate = if (settings.showVatAndSubtotal) _taxRate.value else 0.0,
                notes = _invoiceNotes.value,
                isDraft = isDraft,
                totalAmount = totalAmtDouble,
                paidAmount = paidAmount,
                remainingAmount = (totalAmtDouble - paidAmount).coerceAtLeast(0.0),
                currency = invoiceCurrency.nameAr,
                exchangeRate = convInvoiceToBase.exchangeRate.toDouble(),
                baseCurrency = clientAccountCurr.nameAr,
                convertedTotalAmount = convInvoiceToBase.convertedMoney.toDouble(),
                isCreditOverride = isCreditOverride,
                overrideAuthorizer = overrideAuthorizer,
                overrideReason = overrideReason,
                overrideAmount = overrideAmount
            )

            val payment = if (paidAmount > 0) {
                val convPaymentToBase = com.example.domain.currency.CurrencyConversionService.convert(
                    com.example.domain.currency.Money(paidAmount, invoiceCurrency),
                    clientAccountCurr,
                    rateMap
                )
                Payment(
                    clientId = client.id,
                    invoiceId = 0, // Assigned in repository transaction
                    amount = paidAmount,
                    paymentMethod = paymentMethod,
                    notes = "دفعة مع فاتورة $invoiceNumber",
                    currency = invoiceCurrency.nameAr,
                    exchangeRate = convPaymentToBase.exchangeRate.toDouble(),
                    baseCurrency = clientAccountCurr.nameAr,
                    convertedAmount = convPaymentToBase.convertedMoney.toDouble(),
                    voucherNumber = paymentVoucherNumber
                )
            } else null

            val updatedSettings = if (settings.isAutoNumberingEnabled) {
                val numDigits = invoiceNumber.filter { it.isDigit() }.toIntOrNull()
                val nextNum = if (numDigits != null && numDigits > settings.lastInvoiceNumber) numDigits else settings.lastInvoiceNumber + 1
                settings.copy(lastInvoiceNumber = nextNum)
            } else null

            val id = repository.insertDetailedInvoiceWithPaymentAndSettings(
                invoice = invoice,
                items = invoiceItems,
                payment = payment,
                settingsToUpdate = updatedSettings
            )

            clearInvoiceDraft()
            triggerAutoDriveBackup()
            onSuccess(id.toInt())
        }
    }

    // Save Quick Invoice
    fun saveQuickInvoice(
        client: Client,
        description: String,
        amount: Double,
        currency: String = "الريال اليمني",
        isCreditOverride: Boolean = false,
        overrideAuthorizer: String? = null,
        overrideReason: String? = null,
        overrideAmount: Double = 0.0,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch(financialExceptionHandler) {
            val settings = storeSettings.value
            val nextNum = settings.lastInvoiceNumber + 1
            val invoiceNumber = "${settings.invoicePrefix}$nextNum"

            val clientAccountCurr = com.example.domain.currency.Currency.fromCodeOrName(client.currency)
            val invoiceCurr = com.example.domain.currency.Currency.fromCodeOrName(currency)

            val ratesList = repository.getAllRates()
            val rateMap = ratesList.associate {
                (com.example.domain.currency.Currency.fromCodeOrName(it.fromCurrency) to com.example.domain.currency.Currency.fromCodeOrName(it.toCurrency)) to java.math.BigDecimal.valueOf(it.rate)
            }

            val convInvoiceToBase = com.example.domain.currency.CurrencyConversionService.convert(
                com.example.domain.currency.Money(amount, invoiceCurr),
                clientAccountCurr,
                rateMap
            )

            val invoice = Invoice(
                invoiceNumber = invoiceNumber,
                clientId = client.id,
                clientName = client.name,
                isQuickInvoice = true,
                description = description,
                discount = 0.0,
                taxRate = 0.0,
                isDraft = false,
                totalAmount = amount,
                paidAmount = 0.0,
                remainingAmount = amount,
                currency = invoiceCurr.nameAr,
                exchangeRate = convInvoiceToBase.exchangeRate.toDouble(),
                baseCurrency = clientAccountCurr.nameAr,
                convertedTotalAmount = convInvoiceToBase.convertedMoney.toDouble(),
                isCreditOverride = isCreditOverride,
                overrideAuthorizer = overrideAuthorizer,
                overrideReason = overrideReason,
                overrideAmount = overrideAmount
            )

            val updatedSettings = settings.copy(lastInvoiceNumber = nextNum)
            repository.insertInvoiceWithSettings(invoice, updatedSettings)
            triggerAutoDriveBackup()
            onSuccess()
        }
    }

    // --- Client Operations ---
    fun addClient(
        name: String,
        phone: String = "",
        altPhone: String = "",
        companyName: String = "",
        address: String = "",
        city: String = "",
        email: String = "",
        notes: String = "",
        classification: String = "جديد",
        initialBalance: Double = 0.0,
        balanceType: String = "مدين",
        dealType: String = "نقدي",
        paymentPeriod: String = "عند الطلب",
        defaultDueDateDays: Int = 0,
        clientType: String = "فرد",
        taxNumber: String = "",
        imageUri: String? = null,
        creditLimit: Double = 0.0,
        creditWarningThreshold: Double = 80.0,
        initialBalanceCurrency: String = "الريال اليمني",
        currency: String = "الريال اليمني"
    ) {
        viewModelScope.launch {
            val count = repository.getAllClients().size + 1
            val customerId = "CUS-" + count.toString().padStart(5, '0')
            val calculatedInitialBalance = if (balanceType == "مدين" || balanceType == "عليه لنا") initialBalance else -initialBalance
            
            val client = Client(
                customerId = customerId,
                name = name,
                phone = phone,
                altPhone = altPhone,
                companyName = companyName,
                address = address,
                city = city,
                email = email,
                notes = notes,
                classification = classification,
                dealType = dealType,
                initialBalance = initialBalance,
                balanceType = balanceType,
                balance = calculatedInitialBalance,
                paymentPeriod = paymentPeriod,
                defaultDueDateDays = defaultDueDateDays,
                clientType = clientType,
                taxNumber = taxNumber,
                imageUri = imageUri,
                creditLimit = creditLimit,
                creditWarningThreshold = creditWarningThreshold,
                initialBalanceCurrency = initialBalanceCurrency,
                currency = currency
            )
            repository.insertClient(client)
            triggerAutoDriveBackup()
        }
    }

    fun updateClient(client: Client) {
        viewModelScope.launch {
            repository.updateClient(client)
            repository.recalculateClientBalance(client.id)
            triggerAutoDriveBackup()
        }
    }

    fun toggleClientPin(client: Client) {
        viewModelScope.launch {
            repository.updateClient(client.copy(isPinned = !client.isPinned))
            triggerAutoDriveBackup()
        }
    }

    fun deleteClient(client: Client) {
        viewModelScope.launch {
            repository.deleteClient(client)
            triggerAutoDriveBackup()
        }
    }


    fun getItemPurchaseHistory(itemId: Int): kotlinx.coroutines.flow.Flow<List<ItemPurchaseHistory>> {
        return repository.getPurchaseHistoryForItem(itemId)
    }

    // --- Item, Category, Unit, and Company Operations ---
    fun addCategory(name: String, isPermanent: Boolean = true, onComplete: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = repository.insertCategory(ItemCategory(name = name.trim(), isPermanent = isPermanent, createdAt = System.currentTimeMillis()))
            onComplete(id)
        }
    }

    fun addUnit(name: String, isPermanent: Boolean = true, onComplete: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = repository.insertUnit(ItemUnit(name = name.trim(), isPermanent = isPermanent, createdAt = System.currentTimeMillis()))
            onComplete(id)
        }
    }

    fun addCompany(name: String, phone: String = "", address: String = "", notes: String = "", onComplete: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = repository.insertCompany(SupplierCompany(name = name.trim(), phone = phone.trim(), address = address.trim(), notes = notes.trim(), createdAt = System.currentTimeMillis()))
            onComplete(id)
        }
    }

    fun addOrAccumulateItem(
        name: String,
        barcode: String = "",
        category: String,
        unit: String = "قطعة",
        supplierType: String = "شركة",
        supplierCompanyId: Int? = null,
        supplierCompanyName: String = "",
        companyPhone: String = "",
        individualSupplierName: String = "",
        individualSupplierPhone: String = "",
        individualSupplierNotes: String = "",
        purchaseDate: Long = System.currentTimeMillis(),
        purchasePrice: Double = 0.0,
        sellingPrice: Double = 0.0,
        quantity: Int = 0,
        minQty: Int = 3,
        imageUri: String? = null,
        purchaseCurrency: String = "الريال اليمني",
        sellingCurrency: String = "الريال اليمني",
        onSuccess: (isAccumulated: Boolean, totalQty: Int) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            val trimmedCategory = category.trim().ifBlank { "عام" }
            val trimmedUnit = unit.trim().ifBlank { "قطعة" }

            // Ensure category and unit are permanently saved in DB if not already present
            val allCats = repository.getAllCategories()
            if (allCats.none { it.name.equals(trimmedCategory, ignoreCase = true) }) {
                repository.insertCategory(ItemCategory(name = trimmedCategory, isPermanent = true))
            }

            val allUnits = repository.getAllUnits()
            if (allUnits.none { it.name.equals(trimmedUnit, ignoreCase = true) }) {
                repository.insertUnit(ItemUnit(name = trimmedUnit, isPermanent = true))
            }

            // Ensure company is saved if it doesn't exist
            var finalCompanyId = supplierCompanyId
            var finalCompanyName = supplierCompanyName.trim()
            if (supplierType == "شركة" && finalCompanyName.isNotBlank()) {
                val companies = repository.getAllCompanies()
                val existingComp = companies.find { it.name.equals(finalCompanyName, ignoreCase = true) }
                if (existingComp != null) {
                    finalCompanyId = existingComp.id
                    finalCompanyName = existingComp.name
                    if (companyPhone.isNotBlank() && existingComp.phone != companyPhone.trim()) {
                        repository.updateCompany(existingComp.copy(phone = companyPhone.trim()))
                    }
                } else {
                    finalCompanyId = repository.insertCompany(SupplierCompany(name = finalCompanyName, phone = companyPhone.trim())).toInt()
                }
            }

            // Check if item already exists: STRICT MATCH by Name, Category, Unit, and Supplier
            val allItems = repository.getAllItems()
            val existing = allItems.find { 
                it.name.trim().equals(name.trim(), ignoreCase = true) &&
                it.category.trim().equals(trimmedCategory, ignoreCase = true) &&
                it.unit.trim().equals(trimmedUnit, ignoreCase = true) &&
                it.supplierType == supplierType &&
                if (supplierType == "شركة") {
                    it.supplierCompanyName.trim().equals(finalCompanyName, ignoreCase = true)
                } else {
                    it.individualSupplierName.trim().equals(individualSupplierName.trim(), ignoreCase = true)
                }
            }

            if (existing != null) {
                val newTotalQty = existing.quantity + quantity
                val updatedItem = existing.copy(
                    quantity = newTotalQty,
                    purchasePrice = if (purchasePrice > 0) purchasePrice else existing.purchasePrice,
                    sellingPrice = if (sellingPrice > 0) sellingPrice else existing.sellingPrice,
                    purchaseDate = purchaseDate,
                    barcode = if (barcode.isNotBlank()) barcode.trim() else existing.barcode,
                    category = trimmedCategory,
                    unit = trimmedUnit,
                    imageUri = imageUri ?: existing.imageUri,
                    supplierType = supplierType,
                    supplierCompanyId = if (supplierType == "شركة") finalCompanyId ?: existing.supplierCompanyId else null,
                    supplierCompanyName = if (supplierType == "شركة") finalCompanyName.ifBlank { existing.supplierCompanyName } else "",
                    individualSupplierName = if (supplierType == "مورد فردي") individualSupplierName.trim().ifBlank { existing.individualSupplierName } else "",
                    individualSupplierPhone = if (supplierType == "مورد فردي") individualSupplierPhone.trim().ifBlank { existing.individualSupplierPhone } else "",
                    individualSupplierNotes = if (supplierType == "مورد فردي") individualSupplierNotes.trim().ifBlank { existing.individualSupplierNotes } else "",
                    minQuantityAlert = if (minQty > 0) minQty else existing.minQuantityAlert,
                    purchaseCurrency = if (purchaseCurrency.isNotBlank()) purchaseCurrency else existing.purchaseCurrency,
                    sellingCurrency = if (sellingCurrency.isNotBlank()) sellingCurrency else existing.sellingCurrency
                )
                repository.updateItem(updatedItem)
                
                // Record purchase history
                if (quantity > 0 || purchasePrice > 0) {
                    val finalSupplierName = if (supplierType == "شركة") finalCompanyName else individualSupplierName.trim()
                    val pCurr = if (purchaseCurrency.isNotBlank()) purchaseCurrency else existing.purchaseCurrency
                    repository.insertItemPurchaseHistory(ItemPurchaseHistory(
                        itemId = existing.id,
                        supplierCompanyId = finalCompanyId,
                        supplierCompanyName = finalSupplierName,
                        supplierType = supplierType,
                        purchaseDate = purchaseDate,
                        purchasePrice = purchasePrice,
                        quantity = quantity,
                        purchaseCurrency = pCurr
                    ))
                }
                
                repository.insertLog("تحديث صنف", "المخزون", "تمت إضافة كمية ($quantity) للصنف ${existing.name}. الكمية الإجمالية الجديدة: $newTotalQty")
                triggerAutoDriveBackup()
                onSuccess(true, newTotalQty)
            } else {
                val item = Item(
                    name = name.trim(),
                    barcode = barcode.trim(),
                    category = trimmedCategory,
                    unit = trimmedUnit,
                    supplierType = supplierType,
                    supplierCompanyId = finalCompanyId,
                    supplierCompanyName = finalCompanyName,
                    individualSupplierName = individualSupplierName.trim(),
                    individualSupplierPhone = individualSupplierPhone.trim(),
                    individualSupplierNotes = individualSupplierNotes.trim(),
                    purchaseDate = purchaseDate,
                    purchasePrice = purchasePrice,
                    sellingPrice = sellingPrice,
                    quantity = quantity,
                    minQuantityAlert = minQty,
                    imageUri = imageUri,
                    createdAt = System.currentTimeMillis(),
                    purchaseCurrency = purchaseCurrency,
                    sellingCurrency = sellingCurrency
                )
                val newId = repository.insertItem(item).toInt()
                if (quantity > 0 || purchasePrice > 0) {
                    val finalSupplierName = if (supplierType == "شركة") finalCompanyName else individualSupplierName.trim()
                    repository.insertItemPurchaseHistory(ItemPurchaseHistory(
                        itemId = newId,
                        supplierCompanyId = finalCompanyId,
                        supplierCompanyName = finalSupplierName,
                        supplierType = supplierType,
                        purchaseDate = purchaseDate,
                        purchasePrice = purchasePrice,
                        quantity = quantity,
                        purchaseCurrency = purchaseCurrency
                    ))
                }
                triggerAutoDriveBackup()
                onSuccess(false, quantity)
            }
        }
    }

    fun addItem(
        name: String,
        category: String,
        unit: String = "قطعة",
        supplierType: String = "شركة",
        supplierCompanyId: Int? = null,
        supplierCompanyName: String = "",
        individualSupplierName: String = "",
        individualSupplierPhone: String = "",
        individualSupplierNotes: String = "",
        purchaseDate: Long = System.currentTimeMillis(),
        purchasePrice: Double = 0.0,
        sellingPrice: Double = 0.0,
        quantity: Int = 0,
        minQty: Int = 3,
        imageUri: String? = null,
        barcode: String = "",
        purchaseCurrency: String = "الريال اليمني",
        sellingCurrency: String = "الريال اليمني",
        onSuccess: () -> Unit = {}
    ) {
        addOrAccumulateItem(
            name = name,
            barcode = barcode,
            category = category,
            unit = unit,
            supplierType = supplierType,
            supplierCompanyId = supplierCompanyId,
            supplierCompanyName = supplierCompanyName,
            companyPhone = "",
            individualSupplierName = individualSupplierName,
            individualSupplierPhone = individualSupplierPhone,
            individualSupplierNotes = individualSupplierNotes,
            purchaseDate = purchaseDate,
            purchasePrice = purchasePrice,
            sellingPrice = sellingPrice,
            quantity = quantity,
            minQty = minQty,
            imageUri = imageUri,
            purchaseCurrency = purchaseCurrency,
            sellingCurrency = sellingCurrency,
            onSuccess = { _, _ -> onSuccess() }
        )
    }

    // Overload for backward compatibility with dialogs
    fun addItem(
        name: String, 
        barcode: String, 
        category: String, 
        purchasePrice: Double, 
        sellingPrice: Double, 
        quantity: Int, 
        minQty: Int, 
        imageUri: String? = null,
        purchaseCurrency: String = "الريال اليمني",
        sellingCurrency: String = "الريال اليمني"
    ) {
        addItem(
            name = name,
            category = category,
            unit = "قطعة",
            supplierType = "شركة",
            purchasePrice = purchasePrice,
            sellingPrice = sellingPrice,
            quantity = quantity,
            minQty = minQty,
            imageUri = imageUri,
            barcode = barcode,
            purchaseCurrency = purchaseCurrency,
            sellingCurrency = sellingCurrency
        )
    }

    fun updateItem(item: Item) {
        viewModelScope.launch {
            repository.updateItem(item)
        }
    }

    fun deleteItem(item: Item) {
        viewModelScope.launch {
            repository.deleteItem(item)
            if (!item.imageUri.isNullOrBlank()) {
                com.example.util.ImageUtils.deleteImageFile(app, item.imageUri)
            }
        }
    }

    // --- Payment Operations ---
    fun addPayment(
        clientId: Int,
        amount: Double,
        paymentMethod: String,
        notes: String,
        invoiceId: Int? = null,
        currency: String? = null,
        voucherNumber: String? = null,
        collectorName: String? = null,
        transferNumber: String? = null,
        receiptImageUri: String? = null,
        customRate: Double? = null
    ) {
        viewModelScope.launch(financialExceptionHandler) {
            val settings = storeSettings.value
            val client = repository.getClientById(clientId)
            val clientAccountCurr = com.example.domain.currency.Currency.fromCodeOrName(client?.currency ?: settings.currency)
            val payCurr = com.example.domain.currency.Currency.fromCodeOrName(currency ?: settings.currency)

            val ratesList = repository.getAllRates()
            val rateMap = ratesList.associate {
                (com.example.domain.currency.Currency.fromCodeOrName(it.fromCurrency) to com.example.domain.currency.Currency.fromCodeOrName(it.toCurrency)) to java.math.BigDecimal.valueOf(it.rate)
            }

            val conversionResult = runCatching {
                if (customRate != null && customRate > 0.0) {
                    com.example.domain.currency.CurrencyConversionService.convertWithExplicitRate(
                        com.example.domain.currency.Money(amount, payCurr),
                        clientAccountCurr,
                        java.math.BigDecimal.valueOf(customRate)
                    )
                } else {
                    com.example.domain.currency.CurrencyConversionService.convert(
                        com.example.domain.currency.Money(amount, payCurr),
                        clientAccountCurr,
                        rateMap
                    )
                }
            }

            conversionResult.onSuccess { convPay ->
                val finalVoucherNumber = if (!voucherNumber.isNullOrBlank()) {
                    voucherNumber.trim()
                } else if (settings.isAutoNumberingEnabled) {
                    "${settings.lastPaymentNumber + 1}"
                } else {
                    null
                }

                val payment = Payment(
                    clientId = clientId,
                    invoiceId = invoiceId,
                    amount = amount,
                    paymentMethod = paymentMethod,
                    notes = notes,
                    currency = payCurr.nameAr,
                    exchangeRate = convPay.exchangeRate.toDouble(),
                    baseCurrency = clientAccountCurr.nameAr,
                    convertedAmount = convPay.convertedMoney.toDouble(),
                    voucherNumber = finalVoucherNumber,
                    collectorName = collectorName?.ifBlank { null },
                    transferNumber = transferNumber?.ifBlank { null },
                    receiptImageUri = receiptImageUri?.ifBlank { null }
                )

                val updatedSettings = if (settings.isAutoNumberingEnabled && finalVoucherNumber != null) {
                    val numDigits = finalVoucherNumber.filter { it.isDigit() }.toIntOrNull()
                    val nextNum = if (numDigits != null && numDigits > settings.lastPaymentNumber) numDigits else settings.lastPaymentNumber + 1
                    settings.copy(lastPaymentNumber = nextNum)
                } else {
                    null
                }

                repository.insertPaymentWithSettings(payment, updatedSettings)
                triggerAutoDriveBackup()
            }.onFailure { throwable ->
                if (throwable is com.example.domain.currency.MissingExchangeRateException) {
                    _paymentError.value = "لم يتم العثور على سعر صرف بين ${payCurr.nameAr} و ${clientAccountCurr.nameAr}. يرجى إضافة سعر الصرف أولاً."
                } else {
                    _paymentError.value = throwable.localizedMessage ?: "حدث خطأ غير متوقع أثناء معالجة الدفع"
                }
            }
        }
    }

    fun deletePayment(payment: Payment) {
        viewModelScope.launch {
            repository.deletePayment(payment)
            triggerAutoDriveBackup()
        }
    }

    // --- Settings Operations ---
    fun updateStoreSettings(settings: StoreSettings) {
        viewModelScope.launch {
            repository.saveSettings(settings)
        }
    }

    fun updateStoreName(newName: String) {
        viewModelScope.launch {
            val current = repository.getSettings() ?: StoreSettings()
            repository.saveSettings(current.copy(storeName = newName))
        }
    }

    // --- All Clients PDF Backup Export ---
    suspend fun generateAndSaveAllClientsPdfNow(): File? {
        val currentSettings = storeSettings.value
        val file = com.example.util.exportAllClientsStatementToPdf(
            context = app,
            clientsList = clients.value,
            invoicesList = invoices.value,
            paymentsList = payments.value,
            settings = currentSettings
        )
        if (file != null) {
            repository.insertLog("تصدير PDF", "العملاء", "تم حفظ كشوفات العملاء في مجلد Downloads: ${file.name}")
        }
        return file
    }

    // --- Invoice Delete ---
    fun deleteInvoice(invoice: Invoice) {
        viewModelScope.launch {
            repository.deleteInvoice(invoice)
        }
    }

    fun getInvoiceItemsFlow(invoiceId: Int): Flow<List<InvoiceItem>> {
        return repository.getInvoiceItemsFlow(invoiceId)
    }

    suspend fun getInvoiceItems(invoiceId: Int): List<InvoiceItem> {
        return repository.getInvoiceItems(invoiceId)
    }

    suspend fun getInvoiceById(invoiceId: Int): Invoice? {
        return repository.getInvoiceById(invoiceId)
    }

    // --- App Security PIN & Biometric State ---
    private val _appLocked = MutableStateFlow(false)
    val appLocked = _appLocked.asStateFlow()

    private val _securityPin = MutableStateFlow<String?>(null)
    val securityPin = _securityPin.asStateFlow()

    private val _isBiometricEnabled = MutableStateFlow(false)
    val isBiometricEnabled = _isBiometricEnabled.asStateFlow()

    private val _biometricUserName = MutableStateFlow("")
    val biometricUserName = _biometricUserName.asStateFlow()

    private val _securityProtectionMode = MutableStateFlow(SecurityProtectionMode.NONE)
    val securityProtectionMode = _securityProtectionMode.asStateFlow()

    private val _isDarkMode = MutableStateFlow(false)
    val isDarkMode = _isDarkMode.asStateFlow()

    init {
        // Read lock PIN and Biometrics from SharedPrefs on startup
        val prefs = app.getSharedPreferences("security_prefs", Context.MODE_PRIVATE)
        val savedPin = prefs.getString("pin_code", null)
        val savedBiometricEnabled = prefs.getBoolean("biometric_enabled", false)
        val savedBiometricUser = prefs.getString("biometric_user_name", "") ?: ""
        val savedModeStr = prefs.getString("security_protection_mode", null)

        val resolvedMode = if (savedModeStr != null) {
            try {
                SecurityProtectionMode.valueOf(savedModeStr)
            } catch (e: Exception) {
                SecurityProtectionMode.NONE
            }
        } else {
            when {
                savedBiometricEnabled && !savedPin.isNullOrBlank() -> SecurityProtectionMode.BIOMETRIC_AND_PIN
                savedBiometricEnabled -> SecurityProtectionMode.BIOMETRIC_ONLY
                !savedPin.isNullOrBlank() -> SecurityProtectionMode.PIN_ONLY
                else -> SecurityProtectionMode.NONE
            }
        }

        _securityPin.value = savedPin
        _isBiometricEnabled.value = savedBiometricEnabled
        _biometricUserName.value = savedBiometricUser
        _securityProtectionMode.value = resolvedMode

        if (resolvedMode != SecurityProtectionMode.NONE) {
            _appLocked.value = true
        }

        // Read theme mode preference (default false = Light Mode / النهار)
        val themePrefs = app.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
        _isDarkMode.value = themePrefs.getBoolean("is_dark_mode", false)
    }

    fun setSecurityProtectionMode(
        mode: SecurityProtectionMode,
        pin: String? = null,
        userName: String = ""
    ) {
        val prefs = app.getSharedPreferences("security_prefs", Context.MODE_PRIVATE)
        val editor = prefs.edit().putString("security_protection_mode", mode.name)

        when (mode) {
            SecurityProtectionMode.NONE -> {
                editor.remove("pin_code").putBoolean("biometric_enabled", false)
                _securityPin.value = null
                _isBiometricEnabled.value = false
                _appLocked.value = false
            }
            SecurityProtectionMode.BIOMETRIC_ONLY -> {
                editor.remove("pin_code").putBoolean("biometric_enabled", true)
                if (userName.isNotBlank()) {
                    editor.putString("biometric_user_name", userName)
                    _biometricUserName.value = userName
                }
                _securityPin.value = null
                _isBiometricEnabled.value = true
                _appLocked.value = true
            }
            SecurityProtectionMode.PIN_ONLY -> {
                editor.putBoolean("biometric_enabled", false)
                if (!pin.isNullOrBlank()) {
                    editor.putString("pin_code", pin)
                    _securityPin.value = pin
                }
                _isBiometricEnabled.value = false
                _appLocked.value = true
            }
            SecurityProtectionMode.BIOMETRIC_AND_PIN -> {
                editor.putBoolean("biometric_enabled", true)
                if (!pin.isNullOrBlank()) {
                    editor.putString("pin_code", pin)
                    _securityPin.value = pin
                }
                if (userName.isNotBlank()) {
                    editor.putString("biometric_user_name", userName)
                    _biometricUserName.value = userName
                }
                _isBiometricEnabled.value = true
                _appLocked.value = true
            }
        }
        editor.apply()
        _securityProtectionMode.value = mode
        viewModelScope.launch {
            repository.insertLog("أمان", "الإعدادات", "تم ضبط نمط الحماية إلى: ${mode.label}")
        }
    }

    fun toggleDarkMode() {
        val newMode = !_isDarkMode.value
        _isDarkMode.value = newMode
        val themePrefs = app.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
        themePrefs.edit().putBoolean("is_dark_mode", newMode).apply()
    }

    fun setSecurityPin(pin: String?) {
        val prefs = app.getSharedPreferences("security_prefs", Context.MODE_PRIVATE)
        if (pin.isNullOrBlank()) {
            prefs.edit().remove("pin_code").apply()
            _securityPin.value = null
            if (!_isBiometricEnabled.value) {
                _appLocked.value = false
            }
        } else {
            prefs.edit().putString("pin_code", pin).apply()
            _securityPin.value = pin
            _appLocked.value = true
        }
        viewModelScope.launch {
            repository.insertLog("أمان", "الإعدادات", if (pin.isNullOrBlank()) "تم إيقاف قفل الحماية PIN" else "تم تفعيل قفل الحماية برقم PIN")
        }
    }

    fun setBiometricSettings(enabled: Boolean, userName: String) {
        val cleanName = userName.trim()
        val prefs = app.getSharedPreferences("security_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("biometric_enabled", enabled)
            .putString("biometric_user_name", cleanName)
            .apply()

        _isBiometricEnabled.value = enabled
        if (cleanName.isNotBlank()) {
            _biometricUserName.value = cleanName
        }

        if (enabled) {
            _appLocked.value = true
        } else if (_securityPin.value.isNullOrBlank()) {
            _appLocked.value = false
        }

        viewModelScope.launch {
            repository.insertLog(
                "أمان",
                "الإعدادات",
                if (enabled) "تم تفعيل قفل البصمة للمستخدم: $cleanName" else "تم إيقاف قفل البصمة"
            )
        }
    }

    fun updateBiometricUserName(userName: String) {
        val cleanName = userName.trim()
        val prefs = app.getSharedPreferences("security_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("biometric_user_name", cleanName).apply()
        _biometricUserName.value = cleanName
        viewModelScope.launch {
            repository.insertLog("أمان", "الإعدادات", "تم تحديث اسم مستخدم البصمة إلى: $cleanName")
        }
    }

    fun unlockApp(pin: String): Boolean {
        if (_securityPin.value == pin) {
            _appLocked.value = false
            viewModelScope.launch {
                repository.insertLog("أمان", "الدخول", "تم إلغاء قفل التطبيق عبر رمز PIN")
            }
            return true
        }
        return false
    }

    fun unlockAppByBiometric(): Boolean {
        _appLocked.value = false
        viewModelScope.launch {
            val user = _biometricUserName.value.ifBlank { "المستخدم المسجل" }
            repository.insertLog("أمان", "الدخول", "تم إلغاء قفل التطبيق بواسطة البصمة للمستخدم: $user")
        }
        return true
    }

    fun lockApp() {
        if (!_securityPin.value.isNullOrBlank() || _isBiometricEnabled.value) {
            _appLocked.value = true
        }
    }

    // --- EXPORT AND BACKUP OPERATIONS (Real database files operations & CSV Generation) ---
    fun exportDatabase(onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dbFile = app.getDatabasePath("client_accounts_pro_db")
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val exportDir = File(downloadsDir, "backup")
                if (!exportDir.exists()) exportDir.mkdirs()

                val format = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ENGLISH)
                val timestampStr = format.format(Date())
                val backupDbFile = File(exportDir, "backup_accounts_pro_$timestampStr.db")
                val backupJsonFile = File(exportDir, "backup_accounts_pro_$timestampStr.json")

                if (dbFile.exists()) {
                    dbFile.copyTo(backupDbFile, overwrite = true)
                }

                try {
                    backupManager.createAndUploadBackup(AppDatabase.getDatabase(app))
                    if (backupManager.latestBackupJsonFile.exists()) {
                        backupManager.latestBackupJsonFile.copyTo(backupJsonFile, overwrite = true)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                withContext(Dispatchers.Main) {
                    repository.insertLog("نسخ احتياطي", "النظام", "تصدير قاعدة البيانات بنجاح إلى مجلد Download/backup")
                    onSuccess("تم التصدير بنجاح إلى ذاكرة الهاتف بداخل مجلد Download/backup!\n(تم حفظ الملفين: ${backupDbFile.name} و ${backupJsonFile.name})")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError("فشل التصدير: ${e.localizedMessage}")
                }
            }
        }
    }

    fun importDatabase(backupFilePath: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val backupFile = File(backupFilePath)
                if (backupFile.exists()) {
                    // Close db before overwrite
                    AppDatabase.getDatabase(app).close()
                    val dbFile = app.getDatabasePath("client_accounts_pro_db")
                    backupFile.copyTo(dbFile, overwrite = true)
                    
                    withContext(Dispatchers.Main) {
                        Toast.makeText(app, "تم استعادة قاعدة البيانات بنجاح! الرجاء إعادة تشغيل التطبيق.", Toast.LENGTH_LONG).show()
                        onSuccess()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        onError("ملف النسخة الاحتياطية غير موجود!")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError("فشل الاستيراد: ${e.localizedMessage}")
                }
            }
        }
    }

    // =========================================================================
    // MODERN BACKUP ENGINE INTEGRATION (Full Zip Archive, Verification, History)
    // =========================================================================

    fun performManualBackup(
        destination: String = backupDestination.value,
        externalFolderUri: String? = externalBackupFolderUri.value,
        selectedCategories: Set<String> = com.example.util.BackupEngine.ALL_CATEGORIES.toSet(),
        retentionCount: Int = backupRetentionCount.value,
        onSuccess: (File) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (backupInProgress.value) return
        backupInProgress.value = true
        backupProgressPercent.value = 5
        backupStageText.value = "بدء تجهيز النسخة الاحتياطية..."

        viewModelScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(app)
            val result = com.example.util.BackupEngine.createBackup(
                context = app,
                db = db,
                selectedCategories = selectedCategories,
                destination = destination,
                externalFolderUri = externalFolderUri,
                retentionCount = retentionCount,
                onProgress = { progress, stage ->
                    backupProgressPercent.value = progress
                    backupStageText.value = stage
                    com.example.util.BackupNotificationManager.showProgress(app, stage, progress)
                }
            )

            withContext(Dispatchers.Main) {
                backupInProgress.value = false
                if (result.isSuccess) {
                    val file = result.getOrThrow()
                    val sizeKb = file.length() / 1024
                    com.example.util.BackupNotificationManager.showSuccess(
                        app,
                        title = "✓ تم إنشاء النسخة بنجاح",
                        details = "تم حفظ ${file.name} ($sizeKb ك.ب) بنجاح"
                    )
                    onSuccess(file)
                } else {
                    val errMsg = result.exceptionOrNull()?.localizedMessage ?: "حدث خطأ غير معروف"
                    com.example.util.BackupNotificationManager.showError(
                        app,
                        title = "⚠️ تعذر إنشاء النسخة الاحتياطية",
                        errorMessage = errMsg
                    )
                    onError(errMsg)
                }
            }
        }
    }

    // =========================================================================
    // MODERN RESTORE ENGINE INTEGRATION (Safety Backup, Atomic Tx, Balance Audit)
    // =========================================================================

    fun performRestoreFromFile(
        backupFile: File,
        onSuccess: (com.example.util.RestoreResult) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (restoreInProgress.value) return
        restoreInProgress.value = true
        restoreProgressPercent.value = 5
        restoreStageText.value = "بدء تجهيز عملية الاستعادة..."

        viewModelScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(app)
            val result = com.example.util.RestoreEngine.restoreFromFile(
                context = app,
                db = db,
                backupFile = backupFile,
                onProgress = { progress, stage ->
                    restoreProgressPercent.value = progress
                    restoreStageText.value = stage
                }
            )

            withContext(Dispatchers.Main) {
                restoreInProgress.value = false
                if (result.success) {
                    repository.insertLog("استعادة", "النظام", "تمت استعادة ${result.totalRestored} سجلاً من النسخة الاحتياطية: ${backupFile.name}")
                    onSuccess(result)
                } else {
                    onError(result.message)
                }
            }
        }
    }

    fun performRestoreFromUri(
        uri: Uri,
        onSuccess: (com.example.util.RestoreResult) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val tempFile = File(app.cacheDir, "temp_restore_${System.currentTimeMillis()}.zip")
                app.contentResolver.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                withContext(Dispatchers.Main) {
                    performRestoreFromFile(tempFile, onSuccess = { res ->
                        tempFile.delete()
                        onSuccess(res)
                    }, onError = { err ->
                        tempFile.delete()
                        onError(err)
                    })
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError("تعذر قراءة الملف المحدد: ${e.localizedMessage}")
                }
            }
        }
    }

    // =========================================================================
    // BACKUP SCHEDULING SETTINGS & PERSISTENCE
    // =========================================================================

    fun saveBackupSchedule(
        enabled: Boolean,
        frequency: String,
        customDays: Int,
        hour: Int,
        minute: Int,
        destination: String,
        externalUri: String?,
        retentionCount: Int
    ) {
        isAutoBackupEnabled.value = enabled
        backupFrequency.value = frequency
        customBackupIntervalDays.value = customDays
        backupScheduleHour.value = hour
        backupScheduleMinute.value = minute
        backupDestination.value = destination
        externalBackupFolderUri.value = externalUri
        backupRetentionCount.value = retentionCount

        val prefs = app.getSharedPreferences("backup_scheduler_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("auto_backup_enabled", enabled)
            .putString("backup_frequency", frequency)
            .putInt("custom_interval_days", customDays)
            .putInt("backup_schedule_hour", hour)
            .putInt("backup_schedule_minute", minute)
            .putString("backup_destination", destination)
            .putString("external_folder_uri", externalUri)
            .putInt("backup_retention_count", retentionCount)
            .apply()

        val intervalDays = when (frequency) {
            "أسبوعيًا" -> 7
            "كل أسبوعين" -> 14
            "شهريًا" -> 30
            "مخصص" -> customDays.coerceAtLeast(1)
            else -> 1 // "يوميًا"
        }

        com.example.util.BackupScheduler.scheduleAutomaticBackup(
            context = app,
            enabled = enabled,
            intervalDays = intervalDays,
            hour = hour,
            minute = minute
        )

        viewModelScope.launch {
            repository.insertLog(
                "جدولة النسخ",
                "الإعدادات",
                if (enabled) "تم تفعيل النسخ التلقائي ($frequency الساعة $hour:$minute إلى $destination)" else "تم إيقاف النسخ التلقائي"
            )
        }
    }

    // =========================================================================
    // PROFESSIONAL BACKUP & EXPORT ENGINE (SAF, WORKMANAGER, RESTORE PREVIEW)
    // =========================================================================

    fun setAutoBackupEnabled(enabled: Boolean) {
        viewModelScope.launch {
            backupPreferences.updateAutoBackupEnabled(enabled)
            val settings = backupSettingsState.value
            com.example.util.BackupScheduler.scheduleAutomaticBackup(
                context = app,
                enabled = enabled,
                frequency = settings.frequency,
                hour = settings.backupExecutionHour,
                minute = settings.backupExecutionMinute
            )
        }
    }

    fun setAutoBackupType(type: String) {
        viewModelScope.launch {
            backupPreferences.updateBackupType(type)
        }
    }

    fun setAutoBackupFrequency(frequency: String) {
        viewModelScope.launch {
            backupPreferences.updateFrequency(frequency)
            val settings = backupSettingsState.value
            if (settings.isAutoBackupEnabled) {
                com.example.util.BackupScheduler.scheduleAutomaticBackup(
                    context = app,
                    enabled = true,
                    frequency = frequency,
                    hour = settings.backupExecutionHour,
                    minute = settings.backupExecutionMinute
                )
            }
        }
    }

    fun setAutoBackupExecutionTime(hour: Int, minute: Int) {
        viewModelScope.launch {
            backupPreferences.updateBackupExecutionTime(hour, minute)
            val settings = backupSettingsState.value
            if (settings.isAutoBackupEnabled) {
                com.example.util.BackupScheduler.scheduleAutomaticBackup(
                    context = app,
                    enabled = true,
                    frequency = settings.frequency,
                    hour = hour,
                    minute = minute
                )
            }
        }
    }

    fun scheduleClientStatementExport(
        enabled: Boolean,
        frequency: String,
        hour: Int,
        minute: Int,
        specificDate: String = "",
        dayOfWeek: Int = java.util.Calendar.THURSDAY,
        dayOfMonth: Int = 1
    ) {
        val prefs = app.getSharedPreferences("pdf_backup_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("is_auto_pdf_backup_enabled", enabled)
            .putString("pdf_schedule_frequency", frequency)
            .putInt("pdf_schedule_hour", hour)
            .putInt("pdf_schedule_minute", minute)
            .putString("pdf_schedule_specific_date", specificDate)
            .putInt("pdf_schedule_day_of_week", dayOfWeek)
            .putInt("pdf_schedule_day_of_month", dayOfMonth)
            .apply()

        com.example.util.ClientStatementExportScheduler.scheduleExport(
            context = app,
            enabled = enabled,
            frequency = frequency,
            hour = hour,
            minute = minute,
            specificDate = specificDate,
            dayOfWeek = dayOfWeek,
            dayOfMonth = dayOfMonth
        )

        viewModelScope.launch {
            repository.insertLog(
                "تصدير كشوفات العملاء",
                "الإعدادات",
                if (enabled) "تم تفعيل التصدير التلقائي لكشوفات العملاء ($frequency)" else "تم إيقاف التصدير التلقائي لكشوفات العملاء"
            )
        }
    }

    fun setBackupRetentionCount(count: Int) {
        viewModelScope.launch {
            backupPreferences.updateRetentionCount(count)
        }
    }

    fun setBackupFolder(uri: Uri) {
        viewModelScope.launch {
            com.example.util.SafStorageManager.takePersistablePermissions(app, uri)
            val folderName = com.example.util.SafStorageManager.getFolderDisplayName(app, uri)
            backupPreferences.saveBackupFolder(uri.toString(), folderName)
            externalBackupFolderUri.value = uri.toString()
            val settings = backupSettingsState.value
            if (settings.isAutoBackupEnabled) {
                com.example.util.BackupScheduler.scheduleAutomaticBackup(
                    context = app,
                    enabled = true,
                    frequency = settings.frequency
                )
            }
        }
    }

    fun executeBackupNow(
        overrideType: String? = null,
        onResult: (com.example.util.BackupExecutionResult) -> Unit = {}
    ) {
        if (backupInProgress.value) return
        backupInProgress.value = true
        backupProgressPercent.value = 10
        backupStageText.value = "بدء تجهيز وحفظ النسخة الاحتياطية..."

        viewModelScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(app)
            val result = com.example.util.BackupExecutionCoordinator.executeBackup(
                context = app,
                db = db,
                overrideType = overrideType,
                isTriggeredByWorkManager = false,
                onProgress = { progress, stage ->
                    backupProgressPercent.value = progress
                    backupStageText.value = stage
                }
            )

            withContext(Dispatchers.Main) {
                backupInProgress.value = false
                onResult(result)
            }
        }
    }

    fun inspectBackupUri(
        uri: Uri,
        onResult: (Result<com.example.util.BackupPreview>) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val preview = com.example.util.RestoreEngine.inspectBackupUri(app, uri)
            withContext(Dispatchers.Main) {
                onResult(preview)
            }
        }
    }

    fun inspectBackupFile(
        file: File,
        onResult: (Result<com.example.util.BackupPreview>) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val preview = com.example.util.RestoreEngine.inspectBackupFile(file)
            withContext(Dispatchers.Main) {
                onResult(preview)
            }
        }
    }

    fun restoreFromBackupPreview(
        preview: com.example.util.BackupPreview,
        onResult: (com.example.util.RestoreResult) -> Unit
    ) {
        if (restoreInProgress.value) return
        restoreInProgress.value = true
        restoreProgressPercent.value = 5
        restoreStageText.value = "جاري التحضير لبدء الاستعادة الآمنة..."

        viewModelScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(app)
            val result = com.example.util.RestoreEngine.restoreFromFile(
                context = app,
                db = db,
                backupFile = preview.tempFile,
                onProgress = { progress, stage ->
                    restoreProgressPercent.value = progress
                    restoreStageText.value = stage
                }
            )

            withContext(Dispatchers.Main) {
                restoreInProgress.value = false
                if (result.success) {
                    repository.insertLog("استعادة", "النظام", "تمت استعادة ${result.totalRestored} سجلاً من النسخة: ${preview.fileName}")
                }
                onResult(result)
            }
        }
    }

    fun getLocalBackupFiles(): List<File> {
        val dir = com.example.util.BackupEngine.getBackupDirectory(app)
        return dir.listFiles { f -> f.extension.equals("zip", ignoreCase = true) || f.extension.equals("json", ignoreCase = true) }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    fun deleteLocalBackupFile(file: File): Boolean {
        return try {
            val deleted = file.delete()
            if (deleted) {
                viewModelScope.launch {
                    repository.insertLog("حذف نسخة", "النظام", "تم حذف ملف النسخة: ${file.name}")
                }
            }
            deleted
        } catch (e: Exception) {
            false
        }
    }

    // =========================================================================
    // DEDICATED EXPORT ENGINE INTEGRATION (CSV with UTF-8 BOM, PDF, TXT)
    // =========================================================================

    fun exportData(
        format: String, // "CSV", "PDF", "TXT"
        selectedCategories: Set<String>,
        startDate: Long? = null,
        endDate: Long? = null,
        onSuccess: (File) -> Unit,
        onError: (String) -> Unit
    ) {
        if (exportInProgress.value) return
        exportInProgress.value = true

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val db = AppDatabase.getDatabase(app)
                val settings = db.storeSettingsDao().getSettings() ?: StoreSettings()

                val file = when (format.uppercase(Locale.US)) {
                    "CSV" -> com.example.util.ExportEngine.exportToCsv(
                        context = app,
                        db = db,
                        selectedCategories = selectedCategories,
                        startDate = startDate,
                        endDate = endDate,
                        settings = settings
                    )
                    "PDF" -> com.example.util.ExportEngine.exportToPdf(
                        context = app,
                        db = db,
                        selectedCategories = selectedCategories,
                        startDate = startDate,
                        endDate = endDate,
                        settings = settings
                    )
                    else -> com.example.util.ExportEngine.exportToTxt(
                        context = app,
                        db = db,
                        selectedCategories = selectedCategories,
                        startDate = startDate,
                        endDate = endDate,
                        settings = settings
                    )
                }

                withContext(Dispatchers.Main) {
                    exportInProgress.value = false
                    repository.insertLog("تصدير بيانات", "النظام", "تم تصدير ملف $format: ${file.name}")
                    onSuccess(file)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    exportInProgress.value = false
                    onError("فشل تصدير البيانات: ${e.localizedMessage}")
                }
            }
        }
    }

    // Real CSV/Excel Export for Clients and Statements
    fun exportClientsToCSV(): String {
        val sBuilder = java.lang.StringBuilder()
        sBuilder.append("ID,الاسم,الهاتف,العنوان,البريد الالكتروني,التصنيف,الرصيد\n")
        clients.value.forEach { c ->
            sBuilder.append("${c.id},\"${c.name}\",\"${c.phone}\",\"${c.address}\",\"${c.email}\",\"${c.classification}\",${c.balance}\n")
        }
        return sBuilder.toString()
    }

    fun exportStatementToCSV(client: Client, invoices: List<Invoice>, payments: List<Payment>): String {
        val sBuilder = java.lang.StringBuilder()
        sBuilder.append("كشف حساب العميل: ${client.name}\n")
        sBuilder.append("الهاتف: ${client.phone}\n\n")
        sBuilder.append("التاريخ,العملية,الوصف,المدين,الدائن,الرصيد\n")

        // Chronological sort
        val transactions = mutableListOf<LedgerTransaction>()
        invoices.filter { !it.isDraft }.forEach {
            transactions.add(LedgerTransaction(it.date, "فاتورة", "فاتورة رقم ${it.invoiceNumber}", it.totalAmount, 0.0))
        }
        payments.forEach {
            transactions.add(LedgerTransaction(it.date, "دفعة مستلمة", "طريقة الدفع: ${it.paymentMethod} ${it.notes ?: ""}", 0.0, it.amount))
        }
        transactions.sortBy { it.date }

        val format = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        var runningBalance = 0.0
        transactions.forEach { t ->
            runningBalance += (t.debit - t.credit)
            val dateStr = format.format(Date(t.date))
            sBuilder.append("$dateStr,\"${t.type}\",\"${t.details}\",${t.debit},${t.credit},$runningBalance\n")
        }
        return sBuilder.toString()
    }

    // --- Installment Operations ---
    fun addInstallment(installment: Installment) {
        viewModelScope.launch {
            val id = repository.insertInstallment(installment)
            val insertedInst = installment.copy(id = id.toInt())
            com.example.util.InstallmentManager.scheduleExactAlarm(app, insertedInst)
            triggerAutoDriveBackup()
        }
    }

    fun updateInstallment(installment: Installment) {
        viewModelScope.launch {
            repository.updateInstallment(installment)
            if (installment.isPaid) {
                com.example.util.InstallmentManager.cancelAlarm(app, installment.id)
            } else {
                com.example.util.InstallmentManager.scheduleExactAlarm(app, installment)
            }
            triggerAutoDriveBackup()
        }
    }

    fun deleteInstallment(installment: Installment) {
        viewModelScope.launch {
            repository.deleteInstallment(installment)
            com.example.util.InstallmentManager.cancelAlarm(app, installment.id)
            repository.cancelRemindersForInstallment(installment.id)
            triggerAutoDriveBackup()
        }
    }

    fun markInstallmentPaid(installment: Installment, paidAmount: Double = installment.amount) {
        viewModelScope.launch {
            val updated = installment.copy(isPaid = true, paidAmount = paidAmount)
            repository.updateInstallment(updated)
            com.example.util.InstallmentManager.cancelAlarm(app, installment.id)
            repository.cancelRemindersForInstallment(installment.id)

            // Check recurring installment
            val nextDueDate = com.example.util.InstallmentManager.calculateNextDueDate(installment.dueDate, installment.recurrence)
            if (nextDueDate != null) {
                val nextInstallment = installment.copy(
                    id = 0,
                    dueDate = nextDueDate,
                    isPaid = false,
                    paidAmount = 0.0
                )
                val newId = repository.insertInstallment(nextInstallment)
                com.example.util.InstallmentManager.scheduleExactAlarm(app, nextInstallment.copy(id = newId.toInt()))
            }

            // Also record a payment for the client
            repository.insertPayment(
                Payment(
                    clientId = installment.clientId,
                    amount = paidAmount,
                    date = System.currentTimeMillis(),
                    paymentMethod = "نقدي",
                    notes = "سداد قسط للعميل ${installment.clientName}",
                    currency = installment.currency
                )
            )

            triggerAutoDriveBackup()
        }
    }

    // --- Installment Reminder Actions ---

    fun markReminderHandled(reminderId: Int) {
        viewModelScope.launch {
            repository.markReminderHandled(reminderId, System.currentTimeMillis())
            com.example.util.InstallmentManager.cancelCustomerReminder(app, reminderId)
        }
    }

    fun deleteReminder(reminder: InstallmentReminder) {
        viewModelScope.launch {
            repository.deleteReminder(reminder)
            com.example.util.InstallmentManager.cancelCustomerReminder(app, reminder.id)
        }
    }

    fun createCustomerReminder(
        customerId: Int,
        customerName: String,
        invoiceId: Int?,
        invoiceNumber: String?,
        installmentId: Int?,
        amount: Double,
        remainingAmount: Double,
        dueDate: Long,
        scheduledAt: Long,
        currency: String = "الريال اليمني",
        soundUri: String = "",
        soundTitle: String = "نغمة النظام الافتراضية",
        vibrationEnabled: Boolean = true,
        recurrence: String = "مرة واحدة",
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val overdueDays = if (dueDate > 0 && now > dueDate) {
                ((now - dueDate) / (1000 * 60 * 60 * 24)).toInt().coerceAtLeast(0)
            } else 0

            val reminder = InstallmentReminder(
                reminderType = InstallmentReminderType.CUSTOMER_INSTALLMENT,
                customerId = customerId,
                customerName = customerName,
                invoiceId = invoiceId,
                invoiceNumber = invoiceNumber,
                installmentId = installmentId,
                amount = amount,
                remainingAmount = remainingAmount,
                dueDate = dueDate,
                overdueDays = overdueDays,
                createdAt = now,
                scheduledAt = scheduledAt,
                status = InstallmentReminderStatus.NEW,
                title = "تنبيه قسط للعميل",
                message = "العميل: $customerName\nالمبلغ المستحق: ${com.example.util.FormatUtils.formatAmount(remainingAmount)} $currency",
                currency = currency,
                soundUri = soundUri,
                soundTitle = soundTitle,
                vibrationEnabled = vibrationEnabled,
                recurrence = recurrence
            )
            val newId = repository.insertReminder(reminder)
            val saved = reminder.copy(id = newId.toInt())
            com.example.util.InstallmentManager.scheduleCustomerReminderAlarm(app, saved)
            onSuccess()
        }
    }

    fun createGeneralReminder(
        scope: String = "جميع ما سبق",
        scheduledAt: Long = System.currentTimeMillis(),
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val allInstallments = repository.getAllInstallments()
            val settings = repository.getSettings() ?: StoreSettings()

            val startOfToday = com.example.util.DateTimeUtils.getStartOfDay(now)
            val endOfToday = com.example.util.DateTimeUtils.getEndOfDay(now)
            val upcomingLimit = endOfToday + (settings.generalReminderDaysBeforeDue.coerceAtLeast(1) * 86_400_000L)

            val dueTodayList = allInstallments.filter { !it.isPaid && it.dueDate in startOfToday..endOfToday }
            val upcomingList = allInstallments.filter { !it.isPaid && it.dueDate in (endOfToday + 1)..upcomingLimit }
            val overdueList = allInstallments.filter { !it.isPaid && it.dueDate < startOfToday }

            val activeList = when (scope) {
                "أقساط مستحقة اليوم" -> dueTodayList
                "أقساط ستستحق قريبًا" -> upcomingList
                "أقساط متأخرة" -> overdueList
                else -> (dueTodayList + upcomingList + overdueList).distinctBy { it.id }
            }

            val dueCount = activeList.size
            val totalDue = activeList.sumOf { (it.amount - it.paidAmount).coerceAtLeast(0.0) }
            val overdueCount = overdueList.map { it.clientId }.distinct().size
            val overdueAmount = overdueList.sumOf { (it.amount - it.paidAmount).coerceAtLeast(0.0) }

            val reminder = InstallmentReminder(
                reminderType = InstallmentReminderType.GENERAL_INSTALLMENT,
                amount = totalDue,
                remainingAmount = totalDue,
                dueDate = endOfToday,
                createdAt = now,
                scheduledAt = scheduledAt,
                status = InstallmentReminderStatus.NEW,
                title = "تنبيه أقساط عام",
                message = "لديك $dueCount أقساط مستحقة. إجمالي المستحق: ${com.example.util.FormatUtils.formatAmount(totalDue)} ${settings.currency}",
                dueCount = dueCount,
                overdueCount = overdueCount,
                overdueAmount = overdueAmount,
                currency = settings.currency,
                generalScope = scope
            )
            repository.insertReminder(reminder)
            onSuccess()
        }
    }

    fun syncTodayGeneralReminderIfDue() {
        viewModelScope.launch {
            val settings = repository.getSettings() ?: StoreSettings()
            if (!settings.isGeneralInstallmentReminderEnabled) return@launch

            val now = System.currentTimeMillis()
            val startOfToday = com.example.util.DateTimeUtils.getStartOfDay(now)
            val endOfToday = com.example.util.DateTimeUtils.getEndOfDay(now)

            // Check if unhandled general reminder already exists
            val existing = repository.getUnhandledReminders().firstOrNull {
                it.reminderType == InstallmentReminderType.GENERAL_INSTALLMENT
            }
            if (existing != null) return@launch

            val allInstallments = repository.getAllInstallments()
            val dueTodayList = allInstallments.filter { !it.isPaid && it.dueDate in startOfToday..endOfToday }
            val overdueList = allInstallments.filter { !it.isPaid && it.dueDate < startOfToday }

            if (dueTodayList.isNotEmpty() || overdueList.isNotEmpty()) {
                val dueCount = dueTodayList.size
                val totalDue = dueTodayList.sumOf { (it.amount - it.paidAmount).coerceAtLeast(0.0) }
                val overdueCount = overdueList.map { it.clientId }.distinct().size
                val overdueAmount = overdueList.sumOf { (it.amount - it.paidAmount).coerceAtLeast(0.0) }

                val reminder = InstallmentReminder(
                    reminderType = InstallmentReminderType.GENERAL_INSTALLMENT,
                    amount = totalDue,
                    remainingAmount = totalDue,
                    dueDate = endOfToday,
                    createdAt = now,
                    scheduledAt = now,
                    status = InstallmentReminderStatus.NEW,
                    title = "تنبيه أقساط عام",
                    message = "لديك $dueCount أقساط مستحقة اليوم. إجمالي المستحق: ${com.example.util.FormatUtils.formatAmount(totalDue)} ${settings.currency}",
                    dueCount = dueCount,
                    overdueCount = overdueCount,
                    overdueAmount = overdueAmount,
                    currency = settings.currency,
                    generalScope = settings.generalReminderScope
                )
                repository.insertReminder(reminder)
            }
        }
    }

    fun updateInstallmentReminderSettings(
        isGeneralEnabled: Boolean,
        generalHour: Int,
        generalMinute: Int,
        daysBefore: Int,
        daysAfter: Int,
        scope: String,
        isCustomerEnabled: Boolean,
        allowCustomCustomerReminder: Boolean,
        generalDate: Long = 0L,
        generalRecurrence: String = "يومي",
        generalSoundUri: String = "",
        generalSoundTitle: String = "نغمة النظام الافتراضية",
        generalVibrationEnabled: Boolean = true,
        customerDefaultHour: Int = 10,
        customerDefaultMinute: Int = 0,
        customerDefaultRecurrence: String = "مرة واحدة",
        customerSoundUri: String = "",
        customerSoundTitle: String = "نغمة النظام الافتراضية",
        customerVibrationEnabled: Boolean = true,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val current = repository.getSettings() ?: StoreSettings()
            val updated = current.copy(
                isGeneralInstallmentReminderEnabled = isGeneralEnabled,
                generalReminderHour = generalHour,
                generalReminderMinute = generalMinute,
                generalReminderDaysBeforeDue = daysBefore,
                generalReminderDaysAfterOverdue = daysAfter,
                generalReminderScope = scope,
                isCustomerInstallmentReminderEnabled = isCustomerEnabled,
                allowCustomCustomerReminder = allowCustomCustomerReminder,
                generalAlarmDate = generalDate,
                generalAlarmRecurrence = generalRecurrence,
                generalSoundUri = generalSoundUri,
                generalSoundTitle = generalSoundTitle,
                generalVibrationEnabled = generalVibrationEnabled,
                customerDefaultHour = customerDefaultHour,
                customerDefaultMinute = customerDefaultMinute,
                customerDefaultRecurrence = customerDefaultRecurrence,
                customerSoundUri = customerSoundUri,
                customerSoundTitle = customerSoundTitle,
                customerVibrationEnabled = customerVibrationEnabled
            )
            repository.saveSettings(updated)

            if (isGeneralEnabled) {
                com.example.util.InstallmentManager.scheduleGeneralReminderAlarm(
                    context = app,
                    hour = generalHour,
                    minute = generalMinute,
                    daysBefore = daysBefore,
                    daysAfter = daysAfter,
                    scope = scope,
                    specificDate = generalDate,
                    recurrence = generalRecurrence,
                    soundUri = generalSoundUri,
                    soundTitle = generalSoundTitle,
                    vibrationEnabled = generalVibrationEnabled
                )
            } else {
                com.example.util.InstallmentManager.cancelGeneralReminderAlarm(app)
            }
            onSuccess()
        }
    }

    fun triggerTestAlarm(soundUri: String = "", vibrationEnabled: Boolean = true) {
        com.example.util.SoundHelper.triggerTestNotification(
            context = app,
            title = "🔔 تجربة منبّه الأقساط",
            message = "تم إطلاق تنبيه تجريبي لمعاينة الصوت والاهتزاز بشكل مستقل دون التأثير على بيانات الأقساط المالية.",
            soundUriString = soundUri,
            vibrationEnabled = vibrationEnabled
        )
    }

    // =========================================================================
    // --- AUDIT LOG MANAGEMENT, RETENTION, ARCHIVING & CLEANUP OPERATIONS ---
    // =========================================================================

    fun getDatabaseInstance(): AppDatabase = repository.getDatabase()

    fun refreshAuditStatsAndSummaries() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val db = repository.getDatabase()
                val activeCount = repository.getActiveLogsCount()
                val archivedCount = repository.getArchivedLogsCount()
                val oldestTs = repository.getOldestLogTimestamp()
                val newestTs = repository.getNewestLogTimestamp()
                val summaries = repository.getMonthlyAuditSummaries()
                monthlyAuditSummaries.value = summaries

                val cal = Calendar.getInstance()
                val curYear = cal.get(Calendar.YEAR)
                val curMonth = cal.get(Calendar.MONTH) + 1
                val curMonthCount = summaries.find { it.year == curYear && it.month == curMonth }?.count ?: 0

                val cutoff = auditRetentionSettings.value.cutoffTimestampMillis
                val oldEligible = if (cutoff != null) {
                    db.auditLogDao().getCountOlderThan(cutoff)
                } else 0

                val dbSizeBytes = com.example.util.AuditCleanupEngine.getDatabaseFileSizeBytes(app)
                val archiveSizeBytes = com.example.util.AuditArchiveEngine.getArchivesTotalSizeBytes(app)
                val activeEstimatedSizeBytes = com.example.util.AuditCleanupEngine.estimateAuditLogsSizeBytes(activeCount)

                auditStatsSummary.value = AuditStatsSummary(
                    activeLogsCount = activeCount,
                    archivedLogsCount = archivedCount,
                    oldestLogTimestamp = oldestTs,
                    newestLogTimestamp = newestTs,
                    currentMonthCount = curMonthCount,
                    oldLogsEligibleCount = oldEligible,
                    estimatedActiveSizeBytes = activeEstimatedSizeBytes,
                    archiveDirectorySizeBytes = archiveSizeBytes,
                    databaseFileSizeBytes = dbSizeBytes
                )

                archiveFilesList.value = com.example.util.AuditArchiveEngine.listArchiveFiles(app)

                // Populate initial search results
                searchAuditLogs(auditSearchQuery.value, auditSearchTarget.value)
            } catch (e: Exception) {
                // Non-fatal stats refresh
            }
        }
    }

    fun searchAuditLogs(query: String, target: String = "ACTIVE") {
        auditSearchQuery.value = query
        auditSearchTarget.value = target
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (target == "ACTIVE" || target == "ALL") {
                    activeLogsSearchResults.value = repository.searchActiveLogs(query.trim(), limit = 100, offset = 0)
                } else {
                    activeLogsSearchResults.value = emptyList()
                }

                if (target == "ARCHIVE" || target == "ALL") {
                    archivedLogsSearchResults.value = repository.searchArchivedLogs(query.trim(), limit = 100, offset = 0)
                } else {
                    archivedLogsSearchResults.value = emptyList()
                }
            } catch (e: Exception) {
                // Non-fatal
            }
        }
    }

    fun executeMonthArchive(
        year: Int,
        month: Int,
        onComplete: (Result<ArchiveFileInfo>) -> Unit
    ) {
        viewModelScope.launch {
            auditOperationInProgress.value = true
            auditProgressPercent.value = 5
            auditProgressStageText.value = "بدء عملية الأرشفة..."

            val correlationId = UUID.randomUUID().toString()
            val startCal = Calendar.getInstance().apply {
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month - 1)
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val startTime = startCal.timeInMillis

            val endCal = Calendar.getInstance().apply {
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month - 1)
                set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }
            val endTime = endCal.timeInMillis

            val monthSummary = MonthAuditSummary(year, month, 0, startTime, endTime)
            val periodDesc = monthSummary.displayName
            val packageSlug = String.format("%04d-%02d", year, month)
            val startTimeOp = System.currentTimeMillis()

            val opRecord = AuditManagementOperation(
                operationType = "ARCHIVE",
                stage = "STARTED",
                correlationId = correlationId,
                periodDescription = periodDesc,
                recordCount = 0,
                resultStatus = "قيد المعالجة"
            )
            val opId = repository.insertAuditOperation(opRecord)

            val result = com.example.util.AuditArchiveEngine.archiveRange(
                context = app,
                db = repository.getDatabase(),
                startTime = startTime,
                endTime = endTime,
                periodDescription = periodDesc,
                packageSlug = packageSlug,
                storeName = storeSettings.value.storeName,
                userName = "المسؤول",
                correlationId = correlationId,
                onProgress = { percent, stage ->
                    auditProgressPercent.value = percent
                    auditProgressStageText.value = stage
                }
            )

            val durationMs = System.currentTimeMillis() - startTimeOp
            if (result.isSuccess) {
                val archiveInfo = result.getOrThrow()
                repository.updateAuditOperation(
                    opRecord.copy(
                        id = opId.toInt(),
                        stage = "COMPLETED",
                        recordCount = archiveInfo.recordCount,
                        resultStatus = "ناجحة",
                        durationMs = durationMs,
                        notes = "تم حفظ الأرشيف في: ${archiveInfo.fileName}"
                    )
                )
                repository.insertLog("أرشفة", "سجل العمليات", "تمت أرشفة سجلات شهر $periodDesc (${archiveInfo.recordCount} سجل)")
                auditRetentionPreferences.recordArchiveTimestamp()
            } else {
                repository.updateAuditOperation(
                    opRecord.copy(
                        id = opId.toInt(),
                        stage = "FAILED",
                        resultStatus = "فشلت",
                        durationMs = durationMs,
                        notes = result.exceptionOrNull()?.message
                    )
                )
            }

            refreshAuditStatsAndSummaries()
            auditOperationInProgress.value = false
            onComplete(result)
        }
    }

    fun executeMonthCleanup(
        year: Int,
        month: Int,
        onComplete: (Result<Int>) -> Unit
    ) {
        viewModelScope.launch {
            auditOperationInProgress.value = true
            auditProgressPercent.value = 5
            auditProgressStageText.value = "بدء حذف سجلات الشهر..."

            val correlationId = UUID.randomUUID().toString()
            val startCal = Calendar.getInstance().apply {
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month - 1)
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val startTime = startCal.timeInMillis

            val endCal = Calendar.getInstance().apply {
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month - 1)
                set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }
            val endTime = endCal.timeInMillis

            val monthSummary = MonthAuditSummary(year, month, 0, startTime, endTime)
            val periodDesc = monthSummary.displayName
            val startTimeOp = System.currentTimeMillis()

            val opRecord = AuditManagementOperation(
                operationType = "DELETE",
                stage = "STARTED",
                correlationId = correlationId,
                periodDescription = periodDesc,
                recordCount = 0,
                resultStatus = "قيد المعالجة"
            )
            val opId = repository.insertAuditOperation(opRecord)

            try {
                val deletedCount = com.example.util.AuditCleanupEngine.executeBatchDeleteRange(
                    context = app,
                    db = repository.getDatabase(),
                    startTime = startTime,
                    endTime = endTime,
                    batchSize = 500,
                    onProgress = { deleted, total, percent ->
                        auditProgressPercent.value = percent
                        auditProgressStageText.value = "جارٍ تنظيف السجلات... تمت معالجة: $deleted / $total ($percent%)"
                    }
                )

                val durationMs = System.currentTimeMillis() - startTimeOp
                repository.updateAuditOperation(
                    opRecord.copy(
                        id = opId.toInt(),
                        stage = "COMPLETED",
                        recordCount = deletedCount,
                        resultStatus = "ناجحة",
                        durationMs = durationMs
                    )
                )
                repository.insertLog("حذف", "سجل العمليات", "تم حذف سجلات شهر $periodDesc ($deletedCount سجل)")
                auditRetentionPreferences.recordCleanupTimestamp()
                refreshAuditStatsAndSummaries()
                auditOperationInProgress.value = false
                onComplete(Result.success(deletedCount))
            } catch (e: Exception) {
                val durationMs = System.currentTimeMillis() - startTimeOp
                repository.updateAuditOperation(
                    opRecord.copy(
                        id = opId.toInt(),
                        stage = "FAILED",
                        resultStatus = "فشلت",
                        durationMs = durationMs,
                        notes = e.message
                    )
                )
                auditOperationInProgress.value = false
                onComplete(Result.failure(e))
            }
        }
    }

    fun executeMultiMonthCleanup(
        months: List<MonthAuditSummary>,
        onComplete: (Result<Int>) -> Unit
    ) {
        viewModelScope.launch {
            if (months.isEmpty()) {
                onComplete(Result.success(0))
                return@launch
            }

            auditOperationInProgress.value = true
            auditProgressPercent.value = 5
            auditProgressStageText.value = "بدء حذف السجلات للأشهر المحددة (${months.size} أشهر)..."

            val correlationId = UUID.randomUUID().toString()
            val periodDesc = months.joinToString("، ") { it.displayName }
            val startTimeOp = System.currentTimeMillis()

            val opRecord = AuditManagementOperation(
                operationType = "DELETE",
                stage = "STARTED",
                correlationId = correlationId,
                periodDescription = "حذف ${months.size} أشهر: $periodDesc",
                recordCount = 0,
                resultStatus = "قيد المعالجة"
            )
            val opId = repository.insertAuditOperation(opRecord)

            var grandTotalDeleted = 0

            try {
                for ((idx, m) in months.withIndex()) {
                    val startCal = Calendar.getInstance().apply {
                        set(Calendar.YEAR, m.year)
                        set(Calendar.MONTH, m.month - 1)
                        set(Calendar.DAY_OF_MONTH, 1)
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    val startTime = startCal.timeInMillis

                    val endCal = Calendar.getInstance().apply {
                        set(Calendar.YEAR, m.year)
                        set(Calendar.MONTH, m.month - 1)
                        set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
                        set(Calendar.HOUR_OF_DAY, 23)
                        set(Calendar.MINUTE, 59)
                        set(Calendar.SECOND, 59)
                        set(Calendar.MILLISECOND, 999)
                    }
                    val endTime = endCal.timeInMillis

                    val deletedForMonth = com.example.util.AuditCleanupEngine.executeBatchDeleteRange(
                        context = app,
                        db = repository.getDatabase(),
                        startTime = startTime,
                        endTime = endTime,
                        batchSize = 500,
                        onProgress = { deleted, total, _ ->
                            val overallPercent = (((idx.toDouble() + (deleted.toDouble() / total.coerceAtLeast(1))) / months.size) * 100).toInt().coerceIn(0, 100)
                            auditProgressPercent.value = overallPercent
                            auditProgressStageText.value = "معالجة ${m.displayName}: $deleted / $total ($overallPercent%)"
                        }
                    )
                    grandTotalDeleted += deletedForMonth
                }

                val durationMs = System.currentTimeMillis() - startTimeOp
                repository.updateAuditOperation(
                    opRecord.copy(
                        id = opId.toInt(),
                        stage = "COMPLETED",
                        recordCount = grandTotalDeleted,
                        resultStatus = "ناجحة",
                        durationMs = durationMs
                    )
                )
                repository.insertLog("حذف", "سجل العمليات", "تم حذف سجلات عدة أشهر (${months.size} أشهر، $grandTotalDeleted سجل)")
                auditRetentionPreferences.recordCleanupTimestamp()
                refreshAuditStatsAndSummaries()
                auditOperationInProgress.value = false
                onComplete(Result.success(grandTotalDeleted))
            } catch (e: Exception) {
                val durationMs = System.currentTimeMillis() - startTimeOp
                repository.updateAuditOperation(
                    opRecord.copy(
                        id = opId.toInt(),
                        stage = "FAILED",
                        resultStatus = "فشلت",
                        durationMs = durationMs,
                        notes = e.message
                    )
                )
                auditOperationInProgress.value = false
                onComplete(Result.failure(e))
            }
        }
    }

    fun executeCustomRangeCleanup(
        startTime: Long,
        endTime: Long,
        onComplete: (Result<Int>) -> Unit
    ) {
        viewModelScope.launch {
            auditOperationInProgress.value = true
            auditProgressPercent.value = 5
            auditProgressStageText.value = "بدء حذف سجلات الفترة المخصصة..."

            val correlationId = UUID.randomUUID().toString()
            val dateFmt = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
            val periodDesc = "${dateFmt.format(Date(startTime))} ← ${dateFmt.format(Date(endTime))}"
            val startTimeOp = System.currentTimeMillis()

            val opRecord = AuditManagementOperation(
                operationType = "DELETE",
                stage = "STARTED",
                correlationId = correlationId,
                periodDescription = periodDesc,
                recordCount = 0,
                resultStatus = "قيد المعالجة"
            )
            val opId = repository.insertAuditOperation(opRecord)

            try {
                val deletedCount = com.example.util.AuditCleanupEngine.executeBatchDeleteRange(
                    context = app,
                    db = repository.getDatabase(),
                    startTime = startTime,
                    endTime = endTime,
                    batchSize = 500,
                    onProgress = { deleted, total, percent ->
                        auditProgressPercent.value = percent
                        auditProgressStageText.value = "جارٍ تنظيف السجلات... تمت معالجة: $deleted / $total ($percent%)"
                    }
                )

                val durationMs = System.currentTimeMillis() - startTimeOp
                repository.updateAuditOperation(
                    opRecord.copy(
                        id = opId.toInt(),
                        stage = "COMPLETED",
                        recordCount = deletedCount,
                        resultStatus = "ناجحة",
                        durationMs = durationMs
                    )
                )
                repository.insertLog("حذف", "سجل العمليات", "تم حذف سجلات فترة مخصصة $periodDesc ($deletedCount سجل)")
                auditRetentionPreferences.recordCleanupTimestamp()
                refreshAuditStatsAndSummaries()
                auditOperationInProgress.value = false
                onComplete(Result.success(deletedCount))
            } catch (e: Exception) {
                val durationMs = System.currentTimeMillis() - startTimeOp
                repository.updateAuditOperation(
                    opRecord.copy(
                        id = opId.toInt(),
                        stage = "FAILED",
                        resultStatus = "فشلت",
                        durationMs = durationMs,
                        notes = e.message
                    )
                )
                auditOperationInProgress.value = false
                onComplete(Result.failure(e))
            }
        }
    }

    fun executeOldLogsCleanup(
        cutoffTime: Long,
        onComplete: (Result<Int>) -> Unit
    ) {
        viewModelScope.launch {
            auditOperationInProgress.value = true
            auditProgressPercent.value = 5
            auditProgressStageText.value = "بدء تنظيف السجلات القديمة..."

            val correlationId = UUID.randomUUID().toString()
            val dateFmt = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
            val periodDesc = "السجلات الأقدم من ${dateFmt.format(Date(cutoffTime))}"
            val startTimeOp = System.currentTimeMillis()

            val opRecord = AuditManagementOperation(
                operationType = "CLEANUP",
                stage = "STARTED",
                correlationId = correlationId,
                periodDescription = periodDesc,
                recordCount = 0,
                resultStatus = "قيد المعالجة"
            )
            val opId = repository.insertAuditOperation(opRecord)

            try {
                val deletedCount = com.example.util.AuditCleanupEngine.executeBatchDeleteOlderThan(
                    context = app,
                    db = repository.getDatabase(),
                    cutoffTime = cutoffTime,
                    batchSize = 500,
                    onProgress = { deleted, total, percent ->
                        auditProgressPercent.value = percent
                        auditProgressStageText.value = "جارٍ تنظيف السجلات القديمة... تمت معالجة: $deleted / $total ($percent%)"
                    }
                )

                val durationMs = System.currentTimeMillis() - startTimeOp
                repository.updateAuditOperation(
                    opRecord.copy(
                        id = opId.toInt(),
                        stage = "COMPLETED",
                        recordCount = deletedCount,
                        resultStatus = "ناجحة",
                        durationMs = durationMs
                    )
                )
                repository.insertLog("تنظيف", "سجل العمليات", "تم تنظيف السجلات القديمة الأقدم من $periodDesc ($deletedCount سجل)")
                auditRetentionPreferences.recordCleanupTimestamp()
                refreshAuditStatsAndSummaries()
                auditOperationInProgress.value = false
                onComplete(Result.success(deletedCount))
            } catch (e: Exception) {
                val durationMs = System.currentTimeMillis() - startTimeOp
                repository.updateAuditOperation(
                    opRecord.copy(
                        id = opId.toInt(),
                        stage = "FAILED",
                        resultStatus = "فشلت",
                        durationMs = durationMs,
                        notes = e.message
                    )
                )
                auditOperationInProgress.value = false
                onComplete(Result.failure(e))
            }
        }
    }

    fun restoreArchivePackage(
        file: File,
        onComplete: (Result<AuditRestoreResult>) -> Unit
    ) {
        viewModelScope.launch {
            auditOperationInProgress.value = true
            auditProgressPercent.value = 5
            auditProgressStageText.value = "بدء استعادة الأرشيف (${file.name})..."

            val correlationId = UUID.randomUUID().toString()
            val startTimeOp = System.currentTimeMillis()

            val opRecord = AuditManagementOperation(
                operationType = "RESTORE",
                stage = "STARTED",
                correlationId = correlationId,
                periodDescription = file.name,
                recordCount = 0,
                resultStatus = "قيد المعالجة"
            )
            val opId = repository.insertAuditOperation(opRecord)

            val result = com.example.util.AuditArchiveEngine.restoreArchive(
                context = app,
                db = repository.getDatabase(),
                archiveFile = file,
                onProgress = { percent, stage ->
                    auditProgressPercent.value = percent
                    auditProgressStageText.value = stage
                }
            )

            val durationMs = System.currentTimeMillis() - startTimeOp
            if (result.isSuccess) {
                val res = result.getOrThrow()
                repository.updateAuditOperation(
                    opRecord.copy(
                        id = opId.toInt(),
                        stage = "COMPLETED",
                        recordCount = res.newlyRestoredCount,
                        resultStatus = "ناجحة",
                        durationMs = durationMs,
                        notes = "تمت استعادة: ${res.newlyRestoredCount} سجل، تم تجاهل: ${res.skippedDuplicatesCount} مكرر"
                    )
                )
                repository.insertLog("استعادة", "سجل العمليات", "تمت استعادة أرشيف ${file.name} (استعادة ${res.newlyRestoredCount} وتجاهل ${res.skippedDuplicatesCount})")
            } else {
                repository.updateAuditOperation(
                    opRecord.copy(
                        id = opId.toInt(),
                        stage = "FAILED",
                        resultStatus = "فشلت",
                        durationMs = durationMs,
                        notes = result.exceptionOrNull()?.message
                    )
                )
            }

            refreshAuditStatsAndSummaries()
            auditOperationInProgress.value = false
            onComplete(result)
        }
    }

    fun exportAuditLogs(
        format: String, // CSV, TXT, PDF
        startTime: Long? = null,
        endTime: Long? = null,
        periodDesc: String,
        onComplete: (Result<File>) -> Unit
    ) {
        viewModelScope.launch {
            val correlationId = UUID.randomUUID().toString()
            val startTimeOp = System.currentTimeMillis()

            val opRecord = AuditManagementOperation(
                operationType = "EXPORT",
                stage = "STARTED",
                correlationId = correlationId,
                periodDescription = "$format: $periodDesc",
                recordCount = 0,
                resultStatus = "قيد المعالجة"
            )
            val opId = repository.insertAuditOperation(opRecord)

            val result = when (format.uppercase()) {
                "CSV" -> com.example.util.AuditExportManager.exportToCsv(
                    context = app,
                    db = repository.getDatabase(),
                    startTime = startTime,
                    endTime = endTime,
                    storeName = storeSettings.value.storeName,
                    periodDescription = periodDesc
                )
                "PDF" -> com.example.util.AuditExportManager.exportToPdf(
                    context = app,
                    db = repository.getDatabase(),
                    startTime = startTime,
                    endTime = endTime,
                    storeName = storeSettings.value.storeName,
                    periodDescription = periodDesc
                )
                else -> com.example.util.AuditExportManager.exportToTxt(
                    context = app,
                    db = repository.getDatabase(),
                    startTime = startTime,
                    endTime = endTime,
                    storeName = storeSettings.value.storeName,
                    periodDescription = periodDesc
                )
            }

            val durationMs = System.currentTimeMillis() - startTimeOp
            if (result.isSuccess) {
                val exportedFile = result.getOrThrow()
                repository.updateAuditOperation(
                    opRecord.copy(
                        id = opId.toInt(),
                        stage = "COMPLETED",
                        resultStatus = "ناجحة",
                        durationMs = durationMs,
                        notes = "تم إنشاء ملف: ${exportedFile.name} (${com.example.util.AuditCleanupEngine.formatFileSize(exportedFile.length())})"
                    )
                )
            } else {
                repository.updateAuditOperation(
                    opRecord.copy(
                        id = opId.toInt(),
                        stage = "FAILED",
                        resultStatus = "فشلت",
                        durationMs = durationMs,
                        notes = result.exceptionOrNull()?.message
                    )
                )
            }

            onComplete(result)
        }
    }

    fun updateRetentionPeriod(periodKey: String) {
        viewModelScope.launch {
            auditRetentionPreferences.updateRetentionPeriod(periodKey)
            refreshAuditStatsAndSummaries()
        }
    }

    fun updateSafetyLockDays(days: Int) {
        viewModelScope.launch {
            auditRetentionPreferences.updateSafetyLockDays(days)
            refreshAuditStatsAndSummaries()
        }
    }

    fun setAutoExportBeforeDelete(enabled: Boolean) {
        viewModelScope.launch {
            auditRetentionPreferences.setAutoExportBeforeDelete(enabled)
        }
    }
}


// --- Data classes representing various UI States ---

enum class ActivityType { INVOICE, PAYMENT }

data class ActivityItem(
    val id: Int,
    val type: ActivityType,
    val title: String,
    val subtitle: String,
    val amount: Double,
    val date: Long,
    val isDraft: Boolean,
    val referenceId: Int
)

data class DashboardStats(
    val totalClients: Int = 0,
    val totalDebt: Double = 0.0,
    val totalReceived: Double = 0.0,
    val netBalance: Double = 0.0,
    val invoicesCount: Int = 0,
    val paymentsCount: Int = 0,
    val itemsCount: Int = 0,
    val recentActivities: List<ActivityItem> = emptyList(),
    val topDebtors: List<Client> = emptyList(),
    val topActiveClients: List<Client> = emptyList(),
    val currencyDebts: Map<String, Double> = emptyMap(),
    val currencyPayments: Map<String, Double> = emptyMap()
)

data class SmartSearchResult(
    val clients: List<Client> = emptyList(),
    val items: List<Item> = emptyList(),
    val invoices: List<Invoice> = emptyList()
)

data class CartItem(
    val item: Item,
    val quantity: Int,
    val customPrice: Double,
    val customCurrency: String = "الريال اليمني",
    val quantityInput: String = quantity.toString()
)

data class LedgerTransaction(
    val date: Long,
    val type: String,
    val details: String,
    val debit: Double, // client owes us
    val credit: Double // client paid us
)

// Factory for ViewModel
class AppViewModelFactory(
    private val app: Application,
    private val repository: AppRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AppViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AppViewModel(app, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

