package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.model.*
import com.example.data.repository.AppRepository
import com.example.ui.viewmodel.AppViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PosAccountingHardeningTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var repository: AppRepository
    private lateinit var viewModel: AppViewModel

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = AppRepository(db)
        viewModel = AppViewModel(context as android.app.Application, repository)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun test_addPayment_with_missing_rate_triggers_error_and_no_payment_inserted() = runBlocking {
        // Create client with SAR currency
        val client = Client(
            id = 1,
            name = "عميل تجريبي",
            currency = "الريال السعودي",
            balance = 0.0
        )
        db.clientDao().insertClient(client)

        // Empty exchange rates, meaning there is no exchange rate from YER to SAR
        db.exchangeRateDao().deleteAllRates()

        // Try adding a payment in YER
        viewModel.addPayment(
            clientId = 1,
            amount = 1000.0,
            paymentMethod = "نقدي",
            notes = "دفعة بدون سعر صرف",
            currency = "الريال اليمني"
        )

        // Advance Robolectric main looper to execute the coroutine on Dispatchers.Main
        org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        val error = viewModel.paymentError.value
        assertNotNull(error)
        assertTrue(error!!.contains("لم يتم العثور على سعر صرف"))

        // Verify no payment was inserted into database
        val payments = db.paymentDao().getAllPayments()
        assertTrue(payments.isEmpty())
    }

    @Test
    fun test_soft_deleted_client_excluded_from_queries() = runBlocking {
        val clientActive = Client(id = 10, name = "عميل نشط", isDeleted = 0)
        val clientDeleted = Client(id = 11, name = "عميل محذوف", isDeleted = 1)

        db.clientDao().insertClient(clientActive)
        db.clientDao().insertClient(clientDeleted)

        // 1. Get all clients flow
        val flowList = db.clientDao().getAllClientsFlow().first()
        assertEquals(1, flowList.size)
        assertEquals("عميل نشط", flowList.first().name)

        // 2. Get all clients suspend function
        val list = db.clientDao().getAllClients()
        assertEquals(1, list.size)

        // 3. Search clients
        val searchList = db.clientDao().searchClients("%عميل%").first()
        assertEquals(1, searchList.size)
    }

    @Test
    fun test_updateClient_triggers_balance_recalculation() = runBlocking {
        // Pre-fill some default rates so recalculation can run
        db.exchangeRateDao().insertRate(ExchangeRate(fromCurrency = "USD", toCurrency = "YER", rate = 1500.0))

        val client = Client(
            id = 5,
            name = "عميل ميزان",
            currency = "الريال اليمني",
            initialBalance = 10.0,
            initialBalanceCurrency = "الدولار الأمريكي",
            balanceType = "عليه لنا"
        )
        repository.insertClient(client)

        // Verify initial balance got converted and saved during insert recalculation
        val savedClient = db.clientDao().getClientById(5)
        assertNotNull(savedClient)
        assertEquals(15000.0, savedClient!!.balance, 0.01)

        // Now update client initial balance
        val updatedClient = savedClient.copy(initialBalance = 20.0, initialBalanceConvertedAmount = 0.0)
        repository.updateClient(updatedClient)

        // Balance recalculation should run and update balance
        val finalClient = db.clientDao().getClientById(5)
        assertEquals(30000.0, finalClient!!.balance, 0.01)
    }

    @Test
    fun test_saveExchangeRate_rejects_invalid_rates() {
        // Test zero, negative, NaN and Infinite rates
        viewModel.saveExchangeRate("USD", "YER", 0.0)
        assertEquals("سعر الصرف يجب أن يكون رقماً أكبر من صفر", viewModel.paymentError.value)
        viewModel.clearPaymentError()

        viewModel.saveExchangeRate("USD", "YER", -50.0)
        assertEquals("سعر الصرف يجب أن يكون رقماً أكبر من صفر", viewModel.paymentError.value)
        viewModel.clearPaymentError()

        viewModel.saveExchangeRate("USD", "YER", Double.NaN)
        assertEquals("سعر الصرف يجب أن يكون رقماً أكبر من صفر", viewModel.paymentError.value)
        viewModel.clearPaymentError()

        viewModel.saveExchangeRate("USD", "YER", Double.POSITIVE_INFINITY)
        assertEquals("سعر الصرف يجب أن يكون رقماً أكبر من صفر", viewModel.paymentError.value)
    }
}
