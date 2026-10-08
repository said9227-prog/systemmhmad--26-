package com.example.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object FormatUtils {
    private val decimalSymbols = DecimalFormatSymbols(Locale.US)
    // Plain formatting WITHOUT comma separators (e.g. 1000000000 or 50000000 instead of 1,000,000,000)
    private val cleanFormat = DecimalFormat("0.##", decimalSymbols)
    private val standardFormat = DecimalFormat("0.##", decimalSymbols)

    /**
     * Formats a number cleanly without commas as plain readable numbers.
     * e.g. 1000000000.0 -> "1000000000"
     * e.g. 50000000.0 -> "50000000"
     * e.g. 170000.0 -> "170000"
     * e.g. 12000.50 -> "12000.5"
     * e.g. 0.0 -> "0"
     */
    fun formatAmount(value: Double): String {
        if (value.isNaN() || value.isInfinite()) return "0"
        return cleanFormat.format(value)
    }

    fun formatPlain(value: Double): String {
        if (value.isNaN() || value.isInfinite()) return "0"
        return standardFormat.format(value)
    }

    fun formatAmount(value: Float): String = formatAmount(value.toDouble())
    fun formatAmount(value: Number): String = formatAmount(value.toDouble())
    fun formatAmount(value: Int): String = formatAmount(value.toDouble())
    fun formatAmount(value: Long): String = formatAmount(value.toDouble())

    fun formatWithCurrency(value: Double, currency: String = "﷼"): String {
        return "${formatAmount(value)}\u00A0${formatCurrencyCompact(currency)}"
    }

    fun formatWithCurrency(value: Float, currency: String = "﷼"): String {
        return "${formatAmount(value)}\u00A0${formatCurrencyCompact(currency)}"
    }

    fun formatWithCurrency(value: Number, currency: String = "﷼"): String {
        return "${formatAmount(value)}\u00A0${formatCurrencyCompact(currency)}"
    }

    /**
     * Converts a currency string to its official short symbol:
     * - Saudi: "ر.س"
     * - Yemeni: "ر.ي"
     * - Dollar: "دولار" (or "$")
     * - Euro: "€"
     * This saves critical horizontal space for numbers across the app.
     */
    fun formatCurrencyCompact(currency: String?): String {
        if (currency.isNullOrBlank()) return "ر.ي"
        val trimmed = currency.trim()
        return when {
            trimmed.contains("سعودي", ignoreCase = true) || trimmed.equals("SAR", ignoreCase = true) || trimmed == "ر.س" -> "ر.س"
            trimmed.contains("يمني", ignoreCase = true) || trimmed.equals("YER", ignoreCase = true) || trimmed == "ر.ي" -> "ر.ي"
            trimmed.contains("دولار", ignoreCase = true) || trimmed.equals("USD", ignoreCase = true) || trimmed == "$" -> "دولار"
            trimmed.contains("يورو", ignoreCase = true) || trimmed.equals("EUR", ignoreCase = true) || trimmed == "€" -> "€"
            else -> trimmed
        }
    }

    /**
     * Converts large numbers with multiple zeros into clean exponential notation:
     * e.g., 1000000000 -> "1×10⁹"
     * e.g., 50000000 -> "5×10⁷"
     * e.g., 25000000 -> "25×10⁶"
     * e.g., 1000000 -> "1×10⁶"
     * Returns null if the number doesn't have many trailing zeros (e.g. 1,234,567 or fractional numbers).
     */
    fun toExponentFormat(value: Double): String? {
        if (value.isNaN() || value.isInfinite() || value == 0.0) return null
        val absVal = kotlin.math.abs(value)
        if (absVal < 10000.0) return null // Only for round thousands and above

        val longVal = absVal.toLong()
        if (absVal != longVal.toDouble()) return null // Contains decimal fractions

        var temp = longVal
        var zerosCount = 0
        while (temp > 0L && temp % 10L == 0L) {
            zerosCount++
            temp /= 10L
        }

        // Only use when there are many zeros (>= 4 zeros, e.g. 10000, 100000, 1000000000)
        if (zerosCount < 4) return null

        val sign = if (value < 0) "-" else ""
        val exponentSuperscript = toSuperscript(zerosCount)
        return "${sign}${temp}×10$exponentSuperscript"
    }

    fun toSuperscript(number: Int): String {
        return number.toString().map { char ->
            when (char) {
                '0' -> '⁰'
                '1' -> '¹'
                '2' -> '²'
                '3' -> '³'
                '4' -> '⁴'
                '5' -> '⁵'
                '6' -> '⁶'
                '7' -> '⁷'
                '8' -> '⁸'
                '9' -> '⁹'
                else -> char
            }
        }.joinToString("")
    }

    /**
     * Formats amount smartly:
     * Uses exponential format (e.g. 1×10⁹) if preferCompactIfManyZeros is true and number has many zeros,
     * otherwise returns full comma-separated number (e.g. 1,000,000,000).
     */
    fun formatSmartAmount(value: Double, currency: String? = null, preferCompactIfManyZeros: Boolean = false): String {
        val currSymbol = if (!currency.isNullOrBlank()) " ${formatCurrencyCompact(currency)}" else ""
        if (preferCompactIfManyZeros) {
            val exp = toExponentFormat(value)
            if (exp != null) return "$exp$currSymbol"
        }
        return "${formatAmount(value)}$currSymbol"
    }
}

// Global extension functions for convenient access across Jetpack Compose UI
fun Double.toCleanString(): String = FormatUtils.formatAmount(this)
fun Float.toCleanString(): String = FormatUtils.formatAmount(this)
fun Number.toCleanString(): String = FormatUtils.formatAmount(this)
