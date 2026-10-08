sed -i '/import kotlinx.coroutines.flow.combine/a import com.example.domain.report.*' app/src/main/java/com/example/ui/viewmodel/AppViewModel.kt
sed -i '/val invoices = repository.getAllInvoicesFlow().stateIn(/i \
    private val _selectedReportCurrency = MutableStateFlow("كل العملات")\
    val selectedReportCurrency: StateFlow<String> = _selectedReportCurrency\
\
    private val _selectedDateFilter = MutableStateFlow(DateFilterType.THIS_MONTH)\
    val selectedDateFilter: StateFlow<DateFilterType> = _selectedDateFilter\
\
    private val _customDateRange = MutableStateFlow(ReportDateUtils.getRange(DateFilterType.THIS_MONTH))\
    val customDateRange: StateFlow<DateRange> = _customDateRange\
\
    val reportData: StateFlow<ReportData?> = combine(\
        invoices,\
        payments,\
        clients,\
        items,\
        installments,\
        repository.getAllCompaniesFlow(),\
        _selectedReportCurrency,\
        _selectedDateFilter,\
        _customDateRange\
    ) { invs, pays, cls, itms, insts, suppliers, currency, filterType, customRange ->\
        val dateRange = if (filterType == DateFilterType.CUSTOM) customRange else ReportDateUtils.getRange(filterType)\
        val invoiceItems = repository.getAllInvoiceItems()\
        ReportEngine.generateReport(\
            dateRange = dateRange,\
            selectedCurrency = currency,\
            invoices = invs,\
            invoiceItems = invoiceItems,\
            payments = pays,\
            clients = cls,\
            items = itms,\
            installments = insts,\
            suppliers = suppliers\
        )\
    }.stateIn(\
        scope = viewModelScope,\
        started = SharingStarted.WhileSubscribed(5000),\
        initialValue = null\
    )\
\
    fun setReportCurrency(currency: String) {\
        _selectedReportCurrency.value = currency\
    }\
\
    fun setReportDateFilter(type: DateFilterType) {\
        _selectedDateFilter.value = type\
        if (type != DateFilterType.CUSTOM) {\
             _customDateRange.value = ReportDateUtils.getRange(type)\
        }\
    }\
\
    fun setReportCustomDateRange(start: Long, end: Long) {\
        _customDateRange.value = DateRange(start, end)\
        _selectedDateFilter.value = DateFilterType.CUSTOM\
    }\
' app/src/main/java/com/example/ui/viewmodel/AppViewModel.kt
