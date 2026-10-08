package com.example.domain.currency

import java.math.BigDecimal
import java.math.RoundingMode

data class Money(
    val amount: BigDecimal,
    val currency: Currency
) : Comparable<Money> {

    constructor(amountDouble: Double, currency: Currency) : this(
        BigDecimal.valueOf(if (amountDouble.isNaN() || amountDouble.isInfinite()) 0.0 else amountDouble)
            .setScale(SCALE, RoundingMode.HALF_UP),
        currency
    )

    constructor(amountString: String, currency: Currency) : this(
        try {
            BigDecimal(amountString).setScale(SCALE, RoundingMode.HALF_UP)
        } catch (e: Exception) {
            BigDecimal.ZERO.setScale(SCALE)
        },
        currency
    )

    fun toDouble(): Double = amount.setScale(SCALE, RoundingMode.HALF_UP).toDouble()

    operator fun plus(other: Money): Money {
        require(currency == other.currency) { "Cannot add $currency and ${other.currency} without conversion" }
        return Money(amount.add(other.amount).setScale(SCALE, RoundingMode.HALF_UP), currency)
    }

    operator fun minus(other: Money): Money {
        require(currency == other.currency) { "Cannot subtract $currency and ${other.currency} without conversion" }
        return Money(amount.subtract(other.amount).setScale(SCALE, RoundingMode.HALF_UP), currency)
    }

    operator fun times(multiplier: BigDecimal): Money {
        return Money(amount.multiply(multiplier).setScale(SCALE, RoundingMode.HALF_UP), currency)
    }

    operator fun times(multiplier: Int): Money {
        return times(BigDecimal(multiplier))
    }

    operator fun times(multiplier: Double): Money {
        return times(BigDecimal.valueOf(multiplier))
    }

    fun isZero(): Boolean = amount.compareTo(BigDecimal.ZERO) == 0

    fun isPositive(): Boolean = amount.compareTo(BigDecimal.ZERO) > 0

    fun isNegative(): Boolean = amount.compareTo(BigDecimal.ZERO) < 0

    override fun compareTo(other: Money): Int {
        require(currency == other.currency) { "Cannot compare $currency and ${other.currency}" }
        return amount.compareTo(other.amount)
    }

    fun format(includeCode: Boolean = true): String {
        val formattedNum = com.example.util.FormatUtils.formatPlain(toDouble())
        return if (includeCode) "$formattedNum ${currency.code}" else formattedNum
    }

    companion object {
        const val SCALE = 2

        fun zero(currency: Currency = Currency.YER) = Money(BigDecimal.ZERO.setScale(SCALE), currency)
    }
}
