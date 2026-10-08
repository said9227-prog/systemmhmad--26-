package com.example.util

import com.example.data.model.ExchangeRate
import com.example.domain.currency.Currency
import com.example.domain.currency.CurrencyConversionService
import com.example.domain.currency.Money
import java.math.BigDecimal
import java.math.RoundingMode

data class ConversionResult(
    val originalAmount: Double,
    val fromCurrency: String,
    val toCurrency: String,
    val exchangeRate: Double,
    val convertedAmount: Double,
    val isSameCurrency: Boolean,
    val rateDescription: String,
    val isValid: Boolean = true,
    val errorMessage: String? = null
)

object MonetaryUtils {

    const val CURRENCY_YER = "الريال اليمني"
    const val CURRENCY_USD = "الدولار الأمريكي"
    const val CURRENCY_SAR = "الريال السعودي"

    val SUPPORTED_CURRENCIES = listOf(
        CURRENCY_YER,
        CURRENCY_USD,
        CURRENCY_SAR
    )

    fun roundMoney(amount: Double): Double {
        if (amount.isNaN() || amount.isInfinite()) return 0.0
        return BigDecimal.valueOf(amount)
            .setScale(2, RoundingMode.HALF_UP)
            .toDouble()
    }

    fun roundRate(rate: Double): Double {
        if (rate.isNaN() || rate.isInfinite() || rate <= 0.0) return 1.0
        return BigDecimal.valueOf(rate)
            .setScale(4, RoundingMode.HALF_UP)
            .toDouble()
    }

    fun normalizeCurrency(currency: String?): String {
        return Currency.fromCodeOrName(currency).nameAr
    }

    fun getCurrencyCode(currency: String): String {
        return Currency.fromCodeOrName(currency).code
    }

    fun getCurrencySymbol(currency: String): String {
        return Currency.fromCodeOrName(currency).symbol
    }

    /**
     * Converts an amount from `fromCurrency` to `toCurrency` using the provided exchange rates.
     * Uses CurrencyConversionService as the single authoritative conversion engine.
     */
    fun convert(
        amount: Double,
        fromCurrency: String,
        toCurrency: String,
        rates: List<ExchangeRate>,
        customOverrideRate: Double? = null
    ): ConversionResult {
        val fromEnum = Currency.fromCodeOrName(fromCurrency)
        val toEnum = Currency.fromCodeOrName(toCurrency)

        if (fromEnum == toEnum) {
            val rounded = roundMoney(amount)
            return ConversionResult(
                originalAmount = rounded,
                fromCurrency = fromEnum.nameAr,
                toCurrency = toEnum.nameAr,
                exchangeRate = 1.0,
                convertedAmount = rounded,
                isSameCurrency = true,
                rateDescription = "1 ${fromEnum.code} = 1 ${toEnum.code}",
                isValid = true
            )
        }

        val rateMap = rates.associate {
            (Currency.fromCodeOrName(it.fromCurrency) to Currency.fromCodeOrName(it.toCurrency)) to BigDecimal.valueOf(it.rate)
        }

        val overrideBigDecimal = if (customOverrideRate != null && customOverrideRate > 0.0) {
            BigDecimal.valueOf(customOverrideRate)
        } else null

        val money = Money(amount, fromEnum)

        return try {
            val detail = CurrencyConversionService.convert(money, toEnum, rateMap, overrideBigDecimal)
            ConversionResult(
                originalAmount = detail.originalMoney.toDouble(),
                fromCurrency = detail.originalMoney.currency.nameAr,
                toCurrency = detail.targetCurrency.nameAr,
                exchangeRate = detail.exchangeRate.toDouble(),
                convertedAmount = detail.convertedMoney.toDouble(),
                isSameCurrency = false,
                rateDescription = detail.rateDescription,
                isValid = true
            )
        } catch (e: Exception) {
            ConversionResult(
                originalAmount = roundMoney(amount),
                fromCurrency = fromEnum.nameAr,
                toCurrency = toEnum.nameAr,
                exchangeRate = 0.0,
                convertedAmount = 0.0,
                isSameCurrency = false,
                rateDescription = "غير متوفر",
                isValid = false,
                errorMessage = e.message ?: "لم يتم العثور على سعر صرف بين ${fromEnum.nameAr} و ${toEnum.nameAr}"
            )
        }
    }
}
