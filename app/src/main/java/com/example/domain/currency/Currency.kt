package com.example.domain.currency

enum class Currency(
    val code: String,
    val nameAr: String,
    val symbol: String,
    val flag: String
) {
    YER("YER", "الريال اليمني", "ر.ي", "🇾🇪"),
    SAR("SAR", "الريال السعودي", "ر.س", "🇸🇦"),
    USD("USD", "الدولار الأمريكي", "$", "🇺🇸");

    val displayName: String
        get() = "$flag $nameAr ($code)"

    val chipLabel: String
        get() = "$flag $nameAr"

    companion object {
        val DEFAULT = YER

        fun fromCodeOrName(input: String?): Currency {
            if (input.isNullOrBlank()) return DEFAULT
            val trimmed = input.trim()
            return when {
                trimmed.equals("YER", ignoreCase = true) || trimmed.contains("يمني") || trimmed.contains("اليمني") -> YER
                trimmed.equals("SAR", ignoreCase = true) || trimmed.contains("سعودي") || trimmed.contains("السعودي") -> SAR
                trimmed.equals("USD", ignoreCase = true) || trimmed.contains("دولار") || trimmed.contains("امريكي") || trimmed.contains("أمريكي") || trimmed == "$" -> USD
                else -> DEFAULT
            }
        }
    }
}
