package com.example

import com.example.ui.screens.formatArabicCustomerCount
import com.example.ui.screens.formatArabicOverdueDays
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun arabicCustomerCount_pluralizationRules() {
        // 0 -> لا يوجد عملاء
        assertEquals("لا يوجد عملاء", formatArabicCustomerCount(0))

        // 1 -> عميل واحد
        assertEquals("عميل واحد", formatArabicCustomerCount(1))

        // 2 -> عميلان
        assertEquals("عميلان", formatArabicCustomerCount(2))

        // 3-10 -> X عملاء
        assertEquals("3 عملاء", formatArabicCustomerCount(3))
        assertEquals("5 عملاء", formatArabicCustomerCount(5))
        assertEquals("10 عملاء", formatArabicCustomerCount(10))

        // 11+ -> X عميلًا
        assertEquals("11 عميلًا", formatArabicCustomerCount(11))
        assertEquals("25 عميلًا", formatArabicCustomerCount(25))
        assertEquals("100 عميلًا", formatArabicCustomerCount(100))
    }

    @Test
    fun arabicOverdueDays_pluralizationRules() {
        assertEquals("اليوم", formatArabicOverdueDays(0))
        assertEquals("يوم واحد", formatArabicOverdueDays(1))
        assertEquals("يومان", formatArabicOverdueDays(2))
        assertEquals("5 أيام", formatArabicOverdueDays(5))
        assertEquals("10 أيام", formatArabicOverdueDays(10))
        assertEquals("15 يومًا", formatArabicOverdueDays(15))
        assertEquals("30 يومًا", formatArabicOverdueDays(30))
    }

    @Test
    fun customerAccountingStatus_terms() {
        val debitBalance = 150000.0
        val creditBalance = -80000.0
        val zeroBalance = 0.0

        fun getStatus(balance: Double): String = when {
            balance > 0.001 -> "مدين"
            balance < -0.001 -> "دائن"
            else -> "متعادل"
        }

        assertEquals("مدين", getStatus(debitBalance))
        assertEquals("دائن", getStatus(creditBalance))
        assertEquals("متعادل", getStatus(zeroBalance))
    }
}

