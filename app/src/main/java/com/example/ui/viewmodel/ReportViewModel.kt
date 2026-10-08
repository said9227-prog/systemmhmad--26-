package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.repository.AppRepository
import com.example.domain.report.DateFilterType
import com.example.domain.report.DateRange
import com.example.domain.report.ReportData
import com.example.domain.report.ReportDateUtils
import com.example.domain.report.ReportEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

class ReportViewModel(
    application: Application,
    private val repository: AppRepository
) : AndroidViewModel(application) {

    private val _selectedReportCurrency = MutableStateFlow("كل العملات")
    val selectedReportCurrency: StateFlow<String> = _selectedReportCurrency

    private val _selectedDateFilter = MutableStateFlow(DateFilterType.THIS_MONTH)
    val selectedDateFilter: StateFlow<DateFilterType> = _selectedDateFilter

    private val _customDateRange = MutableStateFlow(ReportDateUtils.getRange(DateFilterType.THIS_MONTH))
    val customDateRange: StateFlow<DateRange> = _customDateRange

    val storeSettings = repository.getSettingsFlow().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val clients = repository.getAllClientsFlow().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val invoices = repository.getAllInvoicesFlow().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val payments = repository.getAllPaymentsFlow().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val returns = repository.getAllReturnsFlow().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val dbFlow1 = combine(
        repository.getAllInvoicesFlow(),
        repository.getAllPaymentsFlow(),
        repository.getAllClientsFlow()
    ) { invs, pays, cls -> Triple(invs, pays, cls) }

    private val dbFlow2 = combine(
        repository.getAllItemsFlow(),
        repository.getAllInstallmentsFlow(),
        repository.getAllCompaniesFlow(),
        repository.getAllReturnsFlow(),
        repository.getAllPurchaseHistoryFlow()
    ) { itms, insts, sups, rets, purchases -> Tuple5(itms, insts, sups, rets, purchases) }

    private data class Tuple5<A, B, C, D, E>(val first: A, val second: B, val third: C, val fourth: D, val fifth: E)

    private val filterFlow = combine(
        _selectedReportCurrency,
        _selectedDateFilter,
        _customDateRange
    ) { c, t, r -> Triple(c, t, r) }

    val reportData: StateFlow<ReportData?> = combine(dbFlow1, dbFlow2, filterFlow) { db1, db2, filters ->
        val invs = db1.first
        val pays = db1.second
        val cls = db1.third
        val itms = db2.first
        val insts = db2.second
        val suppliers = db2.third
        val rets = db2.fourth
        val purchases = db2.fifth
        val currency = filters.first
        val filterType = filters.second
        val customRange = filters.third

        val dateRange = if (filterType == DateFilterType.CUSTOM) customRange else ReportDateUtils.getRange(filterType)
        // Fetch only invoice items for the period's invoices rather than full database table
        val periodInvoiceIds = invs.filter { !it.isDraft && it.date in dateRange.start..dateRange.end }.map { it.id }
        val invoiceItems = repository.getInvoiceItemsForInvoices(periodInvoiceIds)
        
        ReportEngine.generateReport(
            dateRange = dateRange,
            selectedCurrency = currency,
            invoices = invs,
            invoiceItems = invoiceItems,
            payments = pays,
            clients = cls,
            items = itms,
            installments = insts,
            suppliers = suppliers,
            returns = rets,
            purchaseHistory = purchases
        )
    }.flowOn(Dispatchers.Default)
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    fun setReportCurrency(currency: String) {
        _selectedReportCurrency.value = currency
    }

    fun setReportDateFilter(type: DateFilterType) {
        _selectedDateFilter.value = type
        if (type != DateFilterType.CUSTOM) {
             _customDateRange.value = ReportDateUtils.getRange(type)
        }
    }

    fun setReportCustomDateRange(start: Long, end: Long) {
        _customDateRange.value = DateRange(start, end)
        _selectedDateFilter.value = DateFilterType.CUSTOM
    }
}

class ReportViewModelFactory(
    private val application: Application,
    private val repository: AppRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ReportViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ReportViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
