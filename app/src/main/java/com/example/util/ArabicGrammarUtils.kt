package com.example.util

/**
 * Utility functions for Arabic grammar and pluralization rules.
 * Adheres strictly to the Arabic pluralization standards:
 * - 0: لا يوجد
 * - 1: واحد (مفرد)
 * - 2: اثنان (مثنى)
 * - 3..10: جمع تكسير / جمع قلة
 * - 11+: تمييز منصوب بالفتحة (مفرد منصوب)
 */
object ArabicGrammarUtils {

    /**
     * Format customer count with correct Arabic grammar:
     * 0: لا يوجد عملاء
     * 1: عميل واحد
     * 2: عميلان
     * 3..10: X عملاء
     * 11+: X عميلًا
     */
    fun formatCustomerCount(count: Int): String {
        return when {
            count <= 0 -> "لا يوجد عملاء"
            count == 1 -> "عميل واحد"
            count == 2 -> "عميلان"
            count in 3..10 -> "$count عملاء"
            else -> "$count عميلًا"
        }
    }

    /**
     * Format debtor count:
     * 0: لا يوجد مدينون
     * 1: مدين واحد
     * 2: مدينان
     * 3..10: X مدينين
     * 11+: X مدينًا
     */
    fun formatDebtorCount(count: Int): String {
        return when {
            count <= 0 -> "لا يوجد مدينون"
            count == 1 -> "مدين واحد"
            count == 2 -> "مدينان"
            count in 3..10 -> "$count مدينين"
            else -> "$count مدينًا"
        }
    }

    /**
     * Format creditor count:
     * 0: لا يوجد دائنون
     * 1: دائن واحد
     * 2: دائنان
     * 3..10: X دائنين
     * 11+: X دائنًا
     */
    fun formatCreditorCount(count: Int): String {
        return when {
            count <= 0 -> "لا يوجد دائنون"
            count == 1 -> "دائن واحد"
            count == 2 -> "دائنان"
            count in 3..10 -> "$count دائنين"
            else -> "$count دائنًا"
        }
    }

    /**
     * Format invoice count:
     * 0: لا توجد فواتير
     * 1: فاتورة واحدة
     * 2: فاتورتان
     * 3..10: X فواتير
     * 11+: X فاتورة
     */
    fun formatInvoiceCount(count: Int): String {
        return when {
            count <= 0 -> "لا توجد فواتير"
            count == 1 -> "فاتورة واحدة"
            count == 2 -> "فاتورتان"
            count in 3..10 -> "$count فواتير"
            else -> "$count فاتورة"
        }
    }

    /**
     * Format installment count:
     * 0: لا توجد أقساط
     * 1: قسط واحد
     * 2: قسطان
     * 3..10: X أقساط
     * 11+: X قسطًا
     */
    fun formatInstallmentCount(count: Int): String {
        return when {
            count <= 0 -> "لا توجد أقساط"
            count == 1 -> "قسط واحد"
            count == 2 -> "قسطان"
            count in 3..10 -> "$count أقساط"
            else -> "$count قسطًا"
        }
    }

    /**
     * Format payment count:
     * 0: لا توجد دفعات
     * 1: دفعة واحدة
     * 2: دفعتان
     * 3..10: X دفعات
     * 11+: X دفعة
     */
    fun formatPaymentCount(count: Int): String {
        return when {
            count <= 0 -> "لا توجد دفعات"
            count == 1 -> "دفعة واحدة"
            count == 2 -> "دفعتان"
            count in 3..10 -> "$count دفعات"
            else -> "$count دفعة"
        }
    }

    /**
     * Format days count:
     */
    fun formatDaysCount(days: Int): String {
        return when {
            days <= 0 -> "0 يوم"
            days == 1 -> "يوم واحد"
            days == 2 -> "يومان"
            days in 3..10 -> "$days أيام"
            else -> "$days يومًا"
        }
    }
}
