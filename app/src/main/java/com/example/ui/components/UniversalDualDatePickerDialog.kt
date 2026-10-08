package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.*

enum class DatePickerMode {
    GRID_CALENDAR, // جدول التقويم
    MANUAL_FIELDS   // إدخال بالخانات (أرقام)
}

/**
 * Universal Dual-Mode Date Picker Dialog.
 * Supports:
 * 1. Calendar Grid Table (جدول التقويم الشغري المنسق)
 * 2. Manual Digit Input Fields (خانات اليوم/الشهر/السنة)
 * Defaults to current date automatically.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UniversalDualDatePickerDialog(
    title: String = "تحديد التاريخ",
    initialMillis: Long? = null,
    onDismiss: () -> Unit,
    onDateSelected: (selectedMillis: Long) -> Unit
) {
    val cal = remember {
        Calendar.getInstance().apply {
            timeInMillis = initialMillis ?: System.currentTimeMillis()
        }
    }

    var selectedYear by remember { mutableStateOf(cal.get(Calendar.YEAR)) }
    var selectedMonth by remember { mutableStateOf(cal.get(Calendar.MONTH)) } // 0-indexed
    var selectedDay by remember { mutableStateOf(cal.get(Calendar.DAY_OF_MONTH)) }

    // Display mode state
    var currentMode by remember { mutableStateOf(DatePickerMode.GRID_CALENDAR) }

    // Manual digit strings
    var dayText by remember(selectedDay) { mutableStateOf(String.format("%02d", selectedDay)) }
    var monthText by remember(selectedMonth) { mutableStateOf(String.format("%02d", selectedMonth + 1)) }
    var yearText by remember(selectedYear) { mutableStateOf(selectedYear.toString()) }

    var errorMessage by remember { mutableStateOf("") }

    // Helper Arabic Month Names
    val arabicMonths = listOf(
        "يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو",
        "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        imageVector = Icons.Default.CalendarToday,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }

                // Quick Today Button
                TextButton(
                    onClick = {
                        val todayCal = Calendar.getInstance()
                        selectedYear = todayCal.get(Calendar.YEAR)
                        selectedMonth = todayCal.get(Calendar.MONTH)
                        selectedDay = todayCal.get(Calendar.DAY_OF_MONTH)
                        dayText = String.format("%02d", selectedDay)
                        monthText = String.format("%02d", selectedMonth + 1)
                        yearText = selectedYear.toString()
                        errorMessage = ""
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Default.Today, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("اليوم", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Mode Switcher Tabs
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SegmentedButton(
                        selected = currentMode == DatePickerMode.GRID_CALENDAR,
                        onClick = {
                            currentMode = DatePickerMode.GRID_CALENDAR
                            errorMessage = ""
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.GridOn, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("جدول التقويم", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    SegmentedButton(
                        selected = currentMode == DatePickerMode.MANUAL_FIELDS,
                        onClick = {
                            currentMode = DatePickerMode.MANUAL_FIELDS
                            dayText = String.format("%02d", selectedDay)
                            monthText = String.format("%02d", selectedMonth + 1)
                            yearText = selectedYear.toString()
                            errorMessage = ""
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.EditCalendar, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("خانات أرقام", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Current Selected Summary Badge
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("التاريخ المحدد حالياً:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = "${arabicMonths.getOrElse(selectedMonth) { "" }} $selectedDay, $selectedYear",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // MODE 1: GRID CALENDAR
                if (currentMode == DatePickerMode.GRID_CALENDAR) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Month-Year Navigation Bar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    if (selectedMonth == 0) {
                                        selectedMonth = 11
                                        selectedYear -= 1
                                    } else {
                                        selectedMonth -= 1
                                    }
                                }
                            ) {
                                Icon(Icons.Default.ChevronRight, contentDescription = "الشهر السابق")
                            }

                            Text(
                                text = "${arabicMonths.getOrElse(selectedMonth) { "" }} $selectedYear",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )

                            IconButton(
                                onClick = {
                                    if (selectedMonth == 11) {
                                        selectedMonth = 0
                                        selectedYear += 1
                                    } else {
                                        selectedMonth += 1
                                    }
                                }
                            ) {
                                Icon(Icons.Default.ChevronLeft, contentDescription = "الشهر التالي")
                            }
                        }

                        // Days of Week Headers
                        val daysOfWeek = listOf("أحد", "إثنين", "ثلاثاء", "أربعاء", "خميس", "جمعة", "سبت")
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            daysOfWeek.forEach { dayName ->
                                Text(
                                    text = dayName,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        // Calendar Grid calculation
                        val calendarInstance = Calendar.getInstance().apply {
                            set(Calendar.YEAR, selectedYear)
                            set(Calendar.MONTH, selectedMonth)
                            set(Calendar.DAY_OF_MONTH, 1)
                        }
                        val firstDayOfWeek = calendarInstance.get(Calendar.DAY_OF_WEEK) // 1=Sunday, 7=Saturday
                        val maxDaysInMonth = calendarInstance.getActualMaximum(Calendar.DAY_OF_MONTH)

                        val totalGridCells = (firstDayOfWeek - 1) + maxDaysInMonth

                        // 7 Columns Grid Table
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(7),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(totalGridCells) { index ->
                                if (index < (firstDayOfWeek - 1)) {
                                    // Empty cell before day 1
                                    Box(modifier = Modifier.size(36.dp))
                                } else {
                                    val dayNum = index - (firstDayOfWeek - 1) + 1
                                    val isSelected = (dayNum == selectedDay)

                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isSelected) MaterialTheme.colorScheme.primary
                                                else Color.Transparent
                                            )
                                            .border(
                                                width = if (isSelected) 0.dp else 1.dp,
                                                color = if (isSelected) Color.Transparent else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                                shape = CircleShape
                                            )
                                            .clickable {
                                                selectedDay = dayNum
                                                dayText = String.format("%02d", dayNum)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = dayNum.toString(),
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 12.sp,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // MODE 2: MANUAL FIELDS
                if (currentMode == DatePickerMode.MANUAL_FIELDS) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "اكتب أرقام اليوم والشهر والسنة مباشرة (أرقام فقط):",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Day Box
                            OutlinedTextField(
                                value = dayText,
                                onValueChange = { input ->
                                    if (input.length <= 2 && input.all { c -> c.isDigit() }) {
                                        dayText = input
                                        input.toIntOrNull()?.let { d ->
                                            if (d in 1..31) selectedDay = d
                                        }
                                        errorMessage = ""
                                    }
                                },
                                label = { Text("اليوم", fontSize = 10.sp) },
                                placeholder = { Text("01", fontSize = 10.sp) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                            )

                            Text("/", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                            // Month Box
                            OutlinedTextField(
                                value = monthText,
                                onValueChange = { input ->
                                    if (input.length <= 2 && input.all { c -> c.isDigit() }) {
                                        monthText = input
                                        input.toIntOrNull()?.let { m ->
                                            if (m in 1..12) selectedMonth = m - 1
                                        }
                                        errorMessage = ""
                                    }
                                },
                                label = { Text("الشهر", fontSize = 10.sp) },
                                placeholder = { Text("07", fontSize = 10.sp) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                            )

                            Text("/", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                            // Year Box
                            OutlinedTextField(
                                value = yearText,
                                onValueChange = { input ->
                                    if (input.length <= 4 && input.all { c -> c.isDigit() }) {
                                        yearText = input
                                        input.toIntOrNull()?.let { y ->
                                            if (y in 2000..2099) selectedYear = y
                                        }
                                        errorMessage = ""
                                    }
                                },
                                label = { Text("السنة", fontSize = 10.sp) },
                                placeholder = { Text("2026", fontSize = 10.sp) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1.3f),
                                textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }

                if (errorMessage.isNotEmpty()) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    try {
                        val d = dayText.toIntOrNull() ?: selectedDay
                        val m = monthText.toIntOrNull() ?: (selectedMonth + 1)
                        val y = yearText.toIntOrNull() ?: selectedYear

                        if (d !in 1..31) {
                            errorMessage = "رقم اليوم غير صحيح (يجب أن يكون بين 1 و 31)"
                            return@Button
                        }
                        if (m !in 1..12) {
                            errorMessage = "رقم الشهر غير صحيح (يجب أن يكون بين 1 و 12)"
                            return@Button
                        }
                        if (y !in 2000..2099) {
                            errorMessage = "السنة غير صحيحة"
                            return@Button
                        }

                        val resultCal = Calendar.getInstance().apply {
                            set(Calendar.YEAR, y)
                            set(Calendar.MONTH, m - 1)
                            set(Calendar.DAY_OF_MONTH, d)
                            set(Calendar.HOUR_OF_DAY, 0)
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }

                        onDateSelected(resultCal.timeInMillis)
                    } catch (e: Exception) {
                        errorMessage = "الرجاء التأكد من كتابة التاريخ بشكل صحيح!"
                    }
                }
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("تأكيد اختيار التاريخ", fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء", maxLines = 1, softWrap = false)
            }
        }
    )
}
