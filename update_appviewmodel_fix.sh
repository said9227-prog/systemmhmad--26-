sed -i '40,64d' app/src/main/java/com/example/ui/viewmodel/AppViewModel.kt

sed -i '/import kotlinx.coroutines.flow.combine/a import kotlinx.coroutines.flow.combineTransform' app/src/main/java/com/example/ui/viewmodel/AppViewModel.kt

cat << 'INNER_EOF' >> app/src/main/java/com/example/ui/viewmodel/AppViewModel.kt

    private val dbFlow1 = combine(
        repository.getAllInvoicesFlow(),
        repository.getAllPaymentsFlow(),
        repository.getAllClientsFlow()
    ) { invs, pays, cls -> Triple(invs, pays, cls) }

    private val dbFlow2 = combine(
        repository.getAllItemsFlow(),
        repository.getAllInstallmentsFlow(),
        repository.getAllCompaniesFlow()
    ) { itms, insts, sups -> Triple(itms, insts, sups) }

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
        val currency = filters.first
        val filterType = filters.second
        val customRange = filters.third

        val dateRange = if (filterType == DateFilterType.CUSTOM) customRange else ReportDateUtils.getRange(filterType)
        val invoiceItems = repository.getAllInvoiceItems()
        ReportEngine.generateReport(
            dateRange = dateRange,
            selectedCurrency = currency,
            invoices = invs,
            invoiceItems = invoiceItems,
            payments = pays,
            clients = cls,
            items = itms,
            installments = insts,
            suppliers = suppliers
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )
}
INNER_EOF
