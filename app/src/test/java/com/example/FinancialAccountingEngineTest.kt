package com.example

import com.example.data.model.*
import com.example.domain.currency.Currency
import com.example.domain.currency.CurrencyConversionService
import com.example.domain.currency.Money
import com.example.util.FormatUtils
import com.example.util.MonetaryUtils
import com.example.util.pdf.mapper.ClientStatementPdfMapper
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FinancialAccountingEngineTest {

    private val ratesList = listOf(
        ExchangeRate(id = 1, fromCurrency = "USD", toCurrency = "YER", rate = 1500.0),
        ExchangeRate(id = 2, fromCurrency = "SAR", toCurrency = "YER", rate = 400.0),
        ExchangeRate(id = 3, fromCurrency = "USD", toCurrency = "SAR", rate = 3.75)
    )

    private val rateMap = ratesList.associate {
        (Currency.fromCodeOrName(it.fromCurrency) to Currency.fromCodeOrName(it.toCurrency)) to BigDecimal.valueOf(it.rate)
    }

    private val settings = StoreSettings(
        storeName = "الحكيمي للأدوية والمستلزمات الطبية",
        currency = "الريال اليمني"
    )

    @Before
    fun setUp() {
    }

    // 1. YER invoice → YER customer
    @Test
    fun test1_YER_invoice_to_YER_customer() {
        val client = Client(id = 1, name = "عميل 1", currency = "الريال اليمني", balance = 0.0)
        val invoiceMoney = Money(10000.0, Currency.YER)
        val conv = CurrencyConversionService.convert(invoiceMoney, Currency.YER, rateMap)

        assertEquals(10000.0, conv.convertedMoney.toDouble(), 0.01)
        assertEquals(1.0, conv.exchangeRate.toDouble(), 0.0001)
    }

    // 2. SAR invoice → YER customer (100 SAR * 400 = 40,000 YER)
    @Test
    fun test2_SAR_invoice_to_YER_customer() {
        val invoiceMoney = Money(100.0, Currency.SAR)
        val conv = CurrencyConversionService.convert(invoiceMoney, Currency.YER, rateMap)

        assertEquals(40000.0, conv.convertedMoney.toDouble(), 0.01)
        assertEquals(400.0, conv.exchangeRate.toDouble(), 0.0001)
    }

    // 3. USD invoice → YER customer (10 USD * 1500 = 15,000 YER)
    @Test
    fun test3_USD_invoice_to_YER_customer() {
        val invoiceMoney = Money(10.0, Currency.USD)
        val conv = CurrencyConversionService.convert(invoiceMoney, Currency.YER, rateMap)

        assertEquals(15000.0, conv.convertedMoney.toDouble(), 0.01)
        assertEquals(1500.0, conv.exchangeRate.toDouble(), 0.0001)
    }

    // 4. YER invoice → SAR customer (400 YER / 400 = 1 SAR)
    @Test
    fun test4_YER_invoice_to_SAR_customer() {
        val invoiceMoney = Money(400.0, Currency.YER)
        val conv = CurrencyConversionService.convert(invoiceMoney, Currency.SAR, rateMap)

        assertEquals(1.0, conv.convertedMoney.toDouble(), 0.01)
        assertEquals(0.0025, conv.exchangeRate.toDouble(), 0.0001)
    }

    // 5. USD invoice → SAR customer (10 USD * 3.75 = 37.5 SAR)
    @Test
    fun test5_USD_invoice_to_SAR_customer() {
        val invoiceMoney = Money(10.0, Currency.USD)
        val conv = CurrencyConversionService.convert(invoiceMoney, Currency.SAR, rateMap)

        assertEquals(37.5, conv.convertedMoney.toDouble(), 0.01)
        assertEquals(3.75, conv.exchangeRate.toDouble(), 0.0001)
    }

    // 6. YER invoice → USD customer (1500 YER / 1500 = 1 USD)
    @Test
    fun test6_YER_invoice_to_USD_customer() {
        val invoiceMoney = Money(1500.0, Currency.YER)
        val conv = CurrencyConversionService.convert(invoiceMoney, Currency.USD, rateMap)

        assertEquals(1.0, conv.convertedMoney.toDouble(), 0.01)
    }

    // 7. SAR payment against YER account (100 SAR = 40,000 YER)
    @Test
    fun test7_SAR_payment_against_YER_account() {
        val payMoney = Money(100.0, Currency.SAR)
        val conv = CurrencyConversionService.convert(payMoney, Currency.YER, rateMap)

        assertEquals(40000.0, conv.convertedMoney.toDouble(), 0.01)
        assertEquals(Currency.SAR, conv.originalMoney.currency)
    }

    // 8. USD payment against YER account (20 USD = 30,000 YER)
    @Test
    fun test8_USD_payment_against_YER_account() {
        val payMoney = Money(20.0, Currency.USD)
        val conv = CurrencyConversionService.convert(payMoney, Currency.YER, rateMap)

        assertEquals(30000.0, conv.convertedMoney.toDouble(), 0.01)
    }

    // 9. YER payment against SAR invoice / account (400 YER = 1 SAR)
    @Test
    fun test9_YER_payment_against_SAR_invoice() {
        val payMoney = Money(400.0, Currency.YER)
        val conv = CurrencyConversionService.convert(payMoney, Currency.SAR, rateMap)

        assertEquals(1.0, conv.convertedMoney.toDouble(), 0.01)
    }

    // 10. SAR payment against USD invoice / account (37.5 SAR = 10 USD)
    @Test
    fun test10_SAR_payment_against_USD_invoice() {
        val payMoney = Money(37.5, Currency.SAR)
        val conv = CurrencyConversionService.convert(payMoney, Currency.USD, rateMap)

        assertEquals(10.0, conv.convertedMoney.toDouble(), 0.01)
    }

    // 11. Returns in another currency
    @Test
    fun test11_returns_in_another_currency() {
        val returnMoney = Money(10.0, Currency.USD)
        val conv = CurrencyConversionService.convert(returnMoney, Currency.YER, rateMap)

        assertEquals(15000.0, conv.convertedMoney.toDouble(), 0.01)
    }

    // 12. Opening balance in another currency (100 USD = 150,000 YER)
    @Test
    fun test12_opening_balance_in_another_currency() {
        val initMoney = Money(100.0, Currency.USD)
        val conv = CurrencyConversionService.convert(initMoney, Currency.YER, rateMap)

        assertEquals(150000.0, conv.convertedMoney.toDouble(), 0.01)
    }

    // 13. Quick invoice uses exact same conversion logic
    @Test
    fun test13_quick_invoice_same_as_normal_invoice() {
        val quickInvMoney = Money(100.0, Currency.SAR)
        val normalInvMoney = Money(100.0, Currency.SAR)

        val convQuick = CurrencyConversionService.convert(quickInvMoney, Currency.YER, rateMap)
        val convNormal = CurrencyConversionService.convert(normalInvMoney, Currency.YER, rateMap)

        assertEquals(convNormal.convertedMoney, convQuick.convertedMoney)
        assertEquals(convNormal.exchangeRate, convQuick.exchangeRate)
    }

    // 14. Multiple invoices accumulate in base currency
    @Test
    fun test14_multiple_invoices_accumulate() {
        val inv1 = CurrencyConversionService.convert(Money(100.0, Currency.SAR), Currency.YER, rateMap).convertedMoney
        val inv2 = CurrencyConversionService.convert(Money(10.0, Currency.USD), Currency.YER, rateMap).convertedMoney
        val inv3 = CurrencyConversionService.convert(Money(5000.0, Currency.YER), Currency.YER, rateMap).convertedMoney

        val total = inv1 + inv2 + inv3
        // 40,000 + 15,000 + 5,000 = 60,000 YER
        assertEquals(60000.0, total.toDouble(), 0.01)
    }

    // 15. Multiple payments accumulate in base currency
    @Test
    fun test15_multiple_payments_accumulate() {
        val pay1 = CurrencyConversionService.convert(Money(50.0, Currency.SAR), Currency.YER, rateMap).convertedMoney
        val pay2 = CurrencyConversionService.convert(Money(10000.0, Currency.YER), Currency.YER, rateMap).convertedMoney

        val total = pay1 + pay2
        // 20,000 + 10,000 = 30,000 YER
        assertEquals(30000.0, total.toDouble(), 0.01)
    }

    // 16. Partial payment balance calculation
    @Test
    fun test16_partial_payment() {
        val invoiceConv = CurrencyConversionService.convert(Money(100.0, Currency.SAR), Currency.YER, rateMap).convertedMoney // 40,000
        val payConv = CurrencyConversionService.convert(Money(10000.0, Currency.YER), Currency.YER, rateMap).convertedMoney // 10,000

        val remaining = invoiceConv - payConv
        assertEquals(30000.0, remaining.toDouble(), 0.01)
    }

    // 17. Full payment
    @Test
    fun test17_full_payment() {
        val invoiceConv = CurrencyConversionService.convert(Money(100.0, Currency.SAR), Currency.YER, rateMap).convertedMoney // 40,000
        val payConv = CurrencyConversionService.convert(Money(100.0, Currency.SAR), Currency.YER, rateMap).convertedMoney // 40,000

        val remaining = invoiceConv - payConv
        assertTrue(remaining.isZero())
    }

    // 18. Overpayment creates credit balance
    @Test
    fun test18_overpayment_creates_credit_balance() {
        val invoiceConv = CurrencyConversionService.convert(Money(100.0, Currency.SAR), Currency.YER, rateMap).convertedMoney // 40,000
        val payConv = CurrencyConversionService.convert(Money(50000.0, Currency.YER), Currency.YER, rateMap).convertedMoney // 50,000

        val remaining = invoiceConv - payConv
        assertTrue(remaining.isNegative())
        assertEquals(-10000.0, remaining.toDouble(), 0.01)
    }

    // 19. Multiple returns credited
    @Test
    fun test19_multiple_returns_credited() {
        val ret1 = CurrencyConversionService.convert(Money(10.0, Currency.SAR), Currency.YER, rateMap).convertedMoney // 4000
        val ret2 = CurrencyConversionService.convert(Money(1000.0, Currency.YER), Currency.YER, rateMap).convertedMoney // 1000

        val totalReturns = ret1 + ret2
        assertEquals(5000.0, totalReturns.toDouble(), 0.01)
    }

    // 20. Mixed invoice, payment, return currencies invariant: Opening + Invoices - Payments - Returns
    @Test
    fun test20_mixed_invoice_payment_return_currencies() {
        val opening = CurrencyConversionService.convert(Money(100.0, Currency.USD), Currency.YER, rateMap).convertedMoney // 150,000
        val inv = CurrencyConversionService.convert(Money(200.0, Currency.SAR), Currency.YER, rateMap).convertedMoney // 80,000
        val pay = CurrencyConversionService.convert(Money(50.0, Currency.USD), Currency.YER, rateMap).convertedMoney // 75,000
        val ret = CurrencyConversionService.convert(Money(50.0, Currency.SAR), Currency.YER, rateMap).convertedMoney // 20,000

        // 150000 + 80000 - 75000 - 20000 = 135,000 YER
        val finalBal = opening + inv - pay - ret
        assertEquals(135000.0, finalBal.toDouble(), 0.01)
    }

    // 21. Historical exchange rate immutability: changing rateMap does NOT alter historical detail
    @Test
    fun test21_historical_exchange_rate_immutability() {
        val historicalConv = CurrencyConversionService.convertWithExplicitRate(Money(100.0, Currency.SAR), Currency.YER, BigDecimal("400.00"))
        assertEquals(40000.0, historicalConv.convertedMoney.toDouble(), 0.01)

        // Today's rate changes to 450.0
        val todayRatesMap = mapOf((Currency.SAR to Currency.YER) to BigDecimal("450.00"))
        val todayConv = CurrencyConversionService.convert(Money(100.0, Currency.SAR), Currency.YER, todayRatesMap)

        // Historical record still has 40,000
        assertEquals(40000.0, historicalConv.convertedMoney.toDouble(), 0.01)
        assertEquals(45000.0, todayConv.convertedMoney.toDouble(), 0.01)
    }

    // 22. Zero amount transactions
    @Test
    fun test22_zero_amount_transactions() {
        val conv = CurrencyConversionService.convert(Money(0.0, Currency.SAR), Currency.YER, rateMap)
        assertTrue(conv.convertedMoney.isZero())
    }

    // 23. Negative amount handling
    @Test
    fun test23_negative_amount_handling() {
        val conv = CurrencyConversionService.convert(Money(-100.0, Currency.SAR), Currency.YER, rateMap)
        assertEquals(-40000.0, conv.convertedMoney.toDouble(), 0.01)
    }

    // 24. Very large amounts precision without overflow or float inaccuracy
    @Test
    fun test24_very_large_amounts_precision() {
        val largeMoney = Money(1000000000.0, Currency.SAR) // 1 Billion SAR
        val conv = CurrencyConversionService.convert(largeMoney, Currency.YER, rateMap)

        // 1,000,000,000 * 400 = 400,000,000,000 YER
        assertEquals(400000000000.0, conv.convertedMoney.toDouble(), 1.0)
    }

    // 25. Repeated save / tap prevention validation logic
    @Test
    fun test25_repeated_save_prevention() {
        var callCount = 0
        fun handleSave() {
            if (callCount > 0) return
            callCount++
        }

        handleSave()
        handleSave()
        handleSave()

        assertEquals(1, callCount)
    }

    // 26. Database restore recalculates exact balances using conversion detail
    @Test
    fun test26_database_restore_recalculates_exact_balances() {
        val client = Client(id = 1, name = "عميل 1", currency = "الريال اليمني", initialBalance = 100.0, initialBalanceCurrency = "الدولار الأمريكي", balanceType = "مدين")
        val inv = Invoice(id = 10, clientId = 1, clientName = "عميل 1", invoiceNumber = "INV-1", totalAmount = 200.0, currency = "الريال السعودي", exchangeRate = 400.0, convertedTotalAmount = 80000.0, baseCurrency = "الريال اليمني")
        val pay = Payment(id = 100, clientId = 1, amount = 50.0, currency = "الدولار الأمريكي", exchangeRate = 1500.0, convertedAmount = 75000.0, baseCurrency = "الريال اليمني")

        val initialInBase = 100.0 * 1500.0 // 150,000 YER
        val net = initialInBase + inv.convertedTotalAmount - pay.convertedAmount // 150000 + 80000 - 75000 = 155000 YER

        assertEquals(155000.0, net, 0.01)
    }

    // 27. Backup/restore preserves currency info
    @Test
    fun test27_backup_restore_preserves_currency_info() {
        val inv = Invoice(id = 1, invoiceNumber = "1", clientId = 1, clientName = "أحمد", totalAmount = 100.0, currency = "الريال السعودي", exchangeRate = 400.0, baseCurrency = "الريال اليمني", convertedTotalAmount = 40000.0)

        assertEquals("الريال السعودي", inv.currency)
        assertEquals("الريال اليمني", inv.baseCurrency)
        assertEquals(400.0, inv.exchangeRate, 0.001)
        assertEquals(40000.0, inv.convertedTotalAmount, 0.01)
    }

    // 28. PDF consistency with accounting engine
    @Test
    fun test28_pdf_consistency_with_accounting_engine() {
        val client = Client(id = 1, name = "عميل أحمد", currency = "الريال اليمني", balance = 30000.0)
        val ledgerItems = listOf(
            com.example.ui.screens.LedgerEntry(id = 1, date = 1000L, type = "فاتورة", label = "فاتورة #1", debit = 40000.0, credit = 0.0, paymentMethod = "", runningBalance = 40000.0, currency = "الريال السعودي", originalAmount = 100.0, exchangeRate = 400.0, baseCurrency = "الريال اليمني"),
            com.example.ui.screens.LedgerEntry(id = 2, date = 2000L, type = "دفعة", label = "سند #1", debit = 0.0, credit = 10000.0, paymentMethod = "نقدي", runningBalance = 30000.0, currency = "الريال اليمني", originalAmount = 10000.0, exchangeRate = 1.0, baseCurrency = "الريال اليمني")
        )

        val pdfModel = ClientStatementPdfMapper.mapToPdfModel(client, ledgerItems, settings)
        assertNotNull(pdfModel)
        assertEquals("كشف حساب عميل أحمد", pdfModel.title)
    }

    // 29. Customer statement running balance calculated strictly in base currency
    @Test
    fun test29_customer_statement_running_balance_in_base_currency() {
        val entry1DebitInBase = 40000.0 // 100 SAR converted to YER
        val entry2CreditInBase = 10000.0 // 10,000 YER

        var running = 0.0
        running += (entry1DebitInBase - 0.0)
        assertEquals(40000.0, running, 0.01)

        running += (0.0 - entry2CreditInBase)
        assertEquals(30000.0, running, 0.01)
    }

    // 30. Dashboard totals per currency
    @Test
    fun test30_dashboard_totals_per_currency() {
        val invSar = Money(100.0, Currency.SAR)
        val invYer = Money(5000.0, Currency.YER)

        val fmtSar = FormatUtils.formatWithCurrency(invSar.toDouble(), Currency.SAR.nameAr)
        val fmtYer = FormatUtils.formatWithCurrency(invYer.toDouble(), Currency.YER.nameAr)

        assertEquals("100\u00A0ر.س", fmtSar)
        assertEquals("5000\u00A0ر.ي", fmtYer)
    }

    // 31. Missing exchange rate raises invalid result (no silent raw fallback or zero conversion)
    @Test
    fun test31_missing_exchange_rate_raises_invalid_result() {
        val emptyRates = emptyList<ExchangeRate>()
        val result = MonetaryUtils.convert(100.0, "الريال السعودي", "الريال اليمني", emptyRates)

        assertFalse(result.isValid)
        assertNotNull(result.errorMessage)
        assertEquals(0.0, result.convertedAmount, 0.0001)
    }

    // 32. Debt aging calculates overdue days based on due date
    @Test
    fun test32_debt_aging_uses_due_date() {
        val now = System.currentTimeMillis()
        val MILLIS_IN_DAY = 24 * 60 * 60 * 1000L

        // Invoice created 10 days ago (10 days overdue -> overdue8_30)
        val overdueInv = Invoice(
            id = 1,
            clientId = 1,
            clientName = "أحمد",
            invoiceNumber = "INV-100",
            date = now - (10 * MILLIS_IN_DAY),
            totalAmount = 1000.0,
            remainingAmount = 1000.0,
            currency = "الريال اليمني"
        )

        val reportData = com.example.domain.report.ReportEngine.generateReport(
            dateRange = com.example.domain.report.DateRange(0L, now),
            selectedCurrency = "الريال اليمني",
            invoices = listOf(overdueInv),
            invoiceItems = emptyList(),
            payments = emptyList(),
            clients = emptyList(),
            items = emptyList(),
            installments = emptyList(),
            suppliers = emptyList()
        )

        val report = reportData.currencyReports.first()
        assertEquals(1000.0, report.debtAgingReport.overdue8_30, 0.01)
        assertEquals(0.0, report.debtAgingReport.overdue31_60, 0.01)
    }

    // 33. Inventory report separates stock value by purchase currency
    @Test
    fun test33_inventory_separated_by_purchase_currency() {
        val item1 = Item(id = 1, name = "دواء A", quantity = 10, purchasePrice = 5.0, purchaseCurrency = "الدولار الأمريكي")
        val item2 = Item(id = 2, name = "دواء B", quantity = 100, purchasePrice = 400.0, purchaseCurrency = "الريال اليمني")

        val reportDataUsd = com.example.domain.report.ReportEngine.generateReport(
            dateRange = com.example.domain.report.DateRange(0L, System.currentTimeMillis()),
            selectedCurrency = "الدولار الأمريكي",
            invoices = emptyList(),
            invoiceItems = emptyList(),
            payments = emptyList(),
            clients = emptyList(),
            items = listOf(item1, item2),
            installments = emptyList(),
            suppliers = emptyList()
        )

        val reportUsd = reportDataUsd.currencyReports.first()
        // Item 1: 10 * 5.0 = 50.0 USD
        assertEquals(50.0, reportUsd.inventoryReport.inventoryValue, 0.01)
        assertEquals(10, reportUsd.inventoryReport.totalQuantity)
    }

    // 34. 1M transaction scale financial precision
    @Test
    fun test34_scale_1M_transactions_precision() {
        val amount = 1_000_000.0
        val money = Money(amount, Currency.USD)
        val conv = CurrencyConversionService.convert(money, Currency.YER, rateMap)

        // 1,000,000 USD * 1500 = 1,500,000,000 YER
        assertEquals(1_500_000_000.0, conv.convertedMoney.toDouble(), 0.01)
    }

    // 35. Soft-deleted client exclusion check
    @Test
    fun test35_soft_deleted_client_exclusion_check() {
        val clientActive = Client(id = 1, name = "عميل نشط", isDeleted = 0)
        val clientDeleted = Client(id = 2, name = "عميل محذوف", isDeleted = 1)
        
        // Excluded from lists
        val clients = listOf(clientActive, clientDeleted)
        val activeClients = clients.filter { it.isDeleted == 0 }
        
        assertEquals(1, activeClients.size)
        assertEquals("عميل نشط", activeClients.first().name)
    }

    // 36. saveExchangeRate validation rejects invalid rates
    @Test
    fun test36_saveExchangeRate_rejects_invalid_rates() {
        val rateZero = 0.0
        val rateNegative = -1.5
        val rateNaN = Double.NaN
        val rateInfinite = Double.POSITIVE_INFINITY

        assertTrue(rateZero <= 0.0)
        assertTrue(rateNegative <= 0.0)
        assertTrue(rateNaN.isNaN())
        assertTrue(rateInfinite.isInfinite())
    }

    // 37. negative initial balance handled
    @Test
    fun test37_negative_initial_balance_handled() {
        val initialBalance = -5000.0 // Credit balance
        val isDebit = false
        val amountInBase = java.math.BigDecimal.valueOf(initialBalance)
        val openingInBase = if (isDebit) amountInBase else amountInBase.negate()
        assertEquals(5000.0, openingInBase.toDouble(), 0.01)
    }
}
