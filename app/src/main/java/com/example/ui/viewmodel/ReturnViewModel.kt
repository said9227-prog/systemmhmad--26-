package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.*
import com.example.data.repository.AppRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import com.example.util.MonetaryUtils

class ReturnViewModel(
    application: Application,
    private val repository: AppRepository
) : AndroidViewModel(application) {

    val storeSettings = repository.getSettingsFlow().map { it ?: StoreSettings() }.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), StoreSettings()
    )

    val returns = repository.getAllReturnsFlow().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    val exchangeRates = repository.getAllRatesFlow().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    fun getReturnItems(returnId: Int): Flow<List<ProductReturnItem>> {
        return repository.getReturnItemsFlow(returnId)
    }

    suspend fun getReturnById(returnId: Int): ProductReturn? {
        return repository.getReturnById(returnId)
    }

    suspend fun getReturnItemsList(returnId: Int): List<ProductReturnItem> {
        return repository.getReturnItems(returnId)
    }

    fun saveReturn(
        productReturn: ProductReturn,
        items: List<ProductReturnItem>,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                if (items.isEmpty()) {
                    onError("لا يمكن حفظ المرتجع بدون أصناف.")
                    return@launch
                }

                val ratesList = repository.getAllRates()
                val targetBaseCurrency = productReturn.baseCurrency.ifBlank { "الريال اليمني" }

                // Calculate exact conversions for each return line item
                val convertedItems = items.map { item ->
                    val itemCurr = item.currency.ifBlank { "الريال اليمني" }
                    val conv = MonetaryUtils.convert(
                        amount = item.totalPrice,
                        fromCurrency = itemCurr,
                        toCurrency = targetBaseCurrency,
                        rates = ratesList
                    )
                    item.copy(
                        currency = itemCurr,
                        exchangeRate = conv.exchangeRate,
                        convertedTotalPrice = conv.convertedAmount
                    )
                }

                val sumConvertedTotal = convertedItems.sumOf { it.convertedTotalPrice }
                val finalReturn = productReturn.copy(
                    baseCurrency = targetBaseCurrency,
                    convertedTotalAmount = if (sumConvertedTotal > 0) sumConvertedTotal else productReturn.totalAmount
                )
                
                if (finalReturn.type == "CUSTOMER") {
                    repository.saveCustomerReturn(finalReturn, convertedItems)
                    val settings = repository.getSettings() ?: StoreSettings()
                    if (settings.isAutoNumberingEnabled) {
                        val numDigits = finalReturn.returnNumber.filter { it.isDigit() }.toIntOrNull()
                        val nextNum = if (numDigits != null && numDigits > settings.lastSalesReturnNumber) numDigits else settings.lastSalesReturnNumber + 1
                        repository.saveSettings(settings.copy(lastSalesReturnNumber = nextNum))
                    }
                } else {
                    repository.savePurchaseReturn(finalReturn, convertedItems)
                    val settings = repository.getSettings() ?: StoreSettings()
                    if (settings.isAutoNumberingEnabled) {
                        val numDigits = finalReturn.returnNumber.filter { it.isDigit() }.toIntOrNull()
                        val nextNum = if (numDigits != null && numDigits > settings.lastPurchaseReturnNumber) numDigits else settings.lastPurchaseReturnNumber + 1
                        repository.saveSettings(settings.copy(lastPurchaseReturnNumber = nextNum))
                    }
                }
                onSuccess()
            } catch (e: Exception) {
                onError("حدث خطأ أثناء الحفظ: ${e.message}")
            }
        }
    }

    fun deleteReturn(productReturn: ProductReturn) {
        viewModelScope.launch {
            repository.deleteReturn(productReturn)
        }
    }
    
    // Auto-generate Return Number (Starts at 1 when auto-numbering enabled, or empty for manual)
    fun generateReturnNumber(type: String, settings: StoreSettings = storeSettings.value): String {
        if (!settings.isAutoNumberingEnabled) {
            return ""
        }
        return if (type == "CUSTOMER") {
            "${settings.lastSalesReturnNumber + 1}"
        } else {
            "${settings.lastPurchaseReturnNumber + 1}"
        }
    }
}

class ReturnViewModelFactory(
    private val application: Application,
    private val repository: AppRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ReturnViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ReturnViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
