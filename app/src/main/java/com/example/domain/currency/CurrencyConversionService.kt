package com.example.domain.currency

import java.math.BigDecimal
import java.math.RoundingMode

data class ConversionDetail(
    val originalMoney: Money,
    val targetCurrency: Currency,
    val exchangeRate: BigDecimal, // 1 unit of originalMoney.currency = exchangeRate units of targetCurrency
    val convertedMoney: Money,
    val rateDescription: String
)

class MissingExchangeRateException(
    val fromCurrency: Currency,
    val toCurrency: Currency
) : IllegalArgumentException("لم يتم العثور على سعر صرف للتحويل بين ${fromCurrency.nameAr} و ${toCurrency.nameAr}. يرجى تحديد سعر الصرف.")

object CurrencyConversionService {

    private const val RATE_SCALE = 8
    private const val DISPLAY_RATE_SCALE = 6

    /**
     * Resolves the exchange rate between fromCurrency and toCurrency given a map of rates or explicit rate.
     * 1 unit of fromCurrency = rate units of toCurrency.
     */
    fun resolveExchangeRate(
        fromCurrency: Currency,
        toCurrency: Currency,
        rateMap: Map<Pair<Currency, Currency>, BigDecimal> = emptyMap(),
        explicitOverrideRate: BigDecimal? = null
    ): BigDecimal {
        if (fromCurrency == toCurrency) {
            return BigDecimal.ONE.setScale(DISPLAY_RATE_SCALE, RoundingMode.HALF_UP)
        }

        if (explicitOverrideRate != null && explicitOverrideRate.compareTo(BigDecimal.ZERO) > 0) {
            return explicitOverrideRate.setScale(DISPLAY_RATE_SCALE, RoundingMode.HALF_UP)
        }

        // 1. Direct pair match (e.g., 1 USD = 1500 YER)
        rateMap[fromCurrency to toCurrency]?.let { rate ->
            if (rate.compareTo(BigDecimal.ZERO) > 0) {
                return rate.setScale(DISPLAY_RATE_SCALE, RoundingMode.HALF_UP)
            }
        }

        // 2. Inverse pair match (e.g., rate stored as USD -> YER, but converting YER -> USD)
        rateMap[toCurrency to fromCurrency]?.let { inverseRate ->
            if (inverseRate.compareTo(BigDecimal.ZERO) > 0) {
                return BigDecimal.ONE.divide(inverseRate, RATE_SCALE, RoundingMode.HALF_UP)
            }
        }

        // 3. Triangular conversion via YER
        val fromToYer = if (fromCurrency == Currency.YER) BigDecimal.ONE else {
            rateMap[fromCurrency to Currency.YER]
                ?: rateMap[Currency.YER to fromCurrency]?.let {
                    if (it.compareTo(BigDecimal.ZERO) > 0) BigDecimal.ONE.divide(it, RATE_SCALE, RoundingMode.HALF_UP) else null
                }
        }

        val toToYer = if (toCurrency == Currency.YER) BigDecimal.ONE else {
            rateMap[toCurrency to Currency.YER]
                ?: rateMap[Currency.YER to toCurrency]?.let {
                    if (it.compareTo(BigDecimal.ZERO) > 0) BigDecimal.ONE.divide(it, RATE_SCALE, RoundingMode.HALF_UP) else null
                }
        }

        if (fromToYer != null && toToYer != null && toToYer.compareTo(BigDecimal.ZERO) > 0) {
            return fromToYer.divide(toToYer, RATE_SCALE, RoundingMode.HALF_UP)
        }

        // No silent fallback rate! Cross-currency conversions require a valid rate.
        throw MissingExchangeRateException(fromCurrency, toCurrency)
    }

    /**
     * Tries to resolve rate safely without throwing exception, returning null if missing.
     */
    fun resolveExchangeRateOrNull(
        fromCurrency: Currency,
        toCurrency: Currency,
        rateMap: Map<Pair<Currency, Currency>, BigDecimal> = emptyMap(),
        explicitOverrideRate: BigDecimal? = null
    ): BigDecimal? {
        return try {
            resolveExchangeRate(fromCurrency, toCurrency, rateMap, explicitOverrideRate)
        } catch (e: MissingExchangeRateException) {
            null
        }
    }

    /**
     * Converts Money from its native currency to targetCurrency using rateMap or explicit rate.
     */
    fun convert(
        money: Money,
        targetCurrency: Currency,
        rateMap: Map<Pair<Currency, Currency>, BigDecimal> = emptyMap(),
        explicitOverrideRate: BigDecimal? = null
    ): ConversionDetail {
        if (money.currency == targetCurrency) {
            val unitRate = BigDecimal.ONE.setScale(DISPLAY_RATE_SCALE, RoundingMode.HALF_UP)
            return ConversionDetail(
                originalMoney = money,
                targetCurrency = targetCurrency,
                exchangeRate = unitRate,
                convertedMoney = money,
                rateDescription = "1 ${money.currency.code} = 1 ${targetCurrency.code}"
            )
        }

        val rate = resolveExchangeRate(money.currency, targetCurrency, rateMap, explicitOverrideRate)
        val convertedAmount = money.amount.multiply(rate).setScale(Money.SCALE, RoundingMode.HALF_UP)
        val convertedMoney = Money(convertedAmount, targetCurrency)
        val desc = "1 ${money.currency.code} = ${rate.setScale(DISPLAY_RATE_SCALE, RoundingMode.HALF_UP)} ${targetCurrency.code}"

        return ConversionDetail(
            originalMoney = money,
            targetCurrency = targetCurrency,
            exchangeRate = rate.setScale(DISPLAY_RATE_SCALE, RoundingMode.HALF_UP),
            convertedMoney = convertedMoney,
            rateDescription = desc
        )
    }

    /**
     * Converts Money using explicit rate, returning null if explicit rate is invalid or missing when required.
     */
    fun convertWithExplicitRate(
        money: Money,
        targetCurrency: Currency,
        explicitRate: BigDecimal
    ): ConversionDetail {
        return convert(money, targetCurrency, emptyMap(), explicitRate)
    }
}
