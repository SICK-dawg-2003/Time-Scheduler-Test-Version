package com.example.time_scheduler.ui.calendar

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.time_scheduler.data.Category
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun CalendarHeader(
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    selectedCategoryFilter: Category?,
    onCategoryFilterSelected: (Category?) -> Unit,
    viewMode: CalendarViewMode,
    onViewModeSelected: (CalendarViewMode) -> Unit,
    isDarkMode: Boolean = false,
    onToggleDarkMode: () -> Unit = {},
    onNotificationClick: () -> Unit = {},
    onShareClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    var isMonthPickerOpen by remember { mutableStateOf(false) }

    // Generate current week dates centered around selected date
    val startOfWeek = selectedDate.minusDays((selectedDate.dayOfWeek.value % 7).toLong())
    val weekDates = (0..6).map { startOfWeek.plusDays(it.toLong()) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = if (isLandscape) 12.dp else 8.dp,
                vertical = if (isLandscape) 4.dp else 6.dp
            ),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 3.dp,
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(vertical = if (isLandscape) 6.dp else 10.dp)
        ) {
            // 1. Top Bar: Calendar Title & Action Icons (Portrait Mode Only)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = 16.dp,
                        end = if (isLandscape) 36.dp else 16.dp
                    ),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Calendar",
                    fontSize = if (isLandscape) 18.sp else 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Render Action Icons on Top Right in Portrait Mode ONLY
                if (!isLandscape) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(onClick = onShareClick, modifier = Modifier.size(32.dp)) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share Schedule",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(onClick = onNotificationClick, modifier = Modifier.size(32.dp)) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = "Notifications",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(onClick = onToggleDarkMode, modifier = Modifier.size(32.dp)) {
                            Icon(
                                imageVector = if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = "Toggle Dark Mode",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(if (isLandscape) 4.dp else 10.dp))

            // 2. Month Navigation Row: Separate Month Title Picker & Calendar Today Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = 16.dp,
                        end = if (isLandscape) 36.dp else 16.dp
                    ),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Month Header (Opens Month & Year Picker Dialog) + Separate Calendar Icon (Jumps to Today)
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = selectedDate.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                        fontSize = if (isLandscape) 18.sp else 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { isMonthPickerOpen = true }
                            .padding(end = 4.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = { onDateSelected(LocalDate.now()) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = "Jump to Today",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Month-mode navigation arrows (if Weekday strip is hidden in MONTH view)
                if (viewMode == CalendarViewMode.MONTH) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { onDateSelected(selectedDate.minusMonths(1)) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                contentDescription = "Previous Month"
                            )
                        }

                        IconButton(
                            onClick = { onDateSelected(selectedDate.plusMonths(1)) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "Next Month"
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(if (isLandscape) 6.dp else 16.dp))

            // 3. View Mode Segmented Bar: Day | Week | Month | Agenda
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CalendarViewMode.entries.forEach { mode ->
                    val isSelected = viewMode == mode
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 3.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else Color.Transparent
                            )
                            .clickable { onViewModeSelected(mode) }
                            .padding(vertical = if (isLandscape) 6.dp else 10.dp)
                    ) {
                        Text(
                            text = mode.label,
                            fontSize = if (isLandscape) 11.sp else 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(if (isLandscape) 6.dp else 16.dp))

            // 4. Week Day Selector Strip with Left (<) and Right (>) Navigation Arrows on both sides
            if (viewMode == CalendarViewMode.DAY || viewMode == CalendarViewMode.AGENDA) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Arrow (<)
                    IconButton(
                        onClick = {
                            val prev = when (viewMode) {
                                CalendarViewMode.DAY, CalendarViewMode.AGENDA -> selectedDate.minusDays(1)
                                CalendarViewMode.WEEK -> selectedDate.minusWeeks(1)
                                else -> selectedDate.minusDays(1)
                            }
                            onDateSelected(prev)
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Previous Date"
                        )
                    }

                    // 7 Weekday Date Chips Row
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        weekDates.forEach { date ->
                            val isSelected = date == selectedDate
                            val isToday = date == LocalDate.now()

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                        else Color.Transparent
                                    )
                                    .clickable { onDateSelected(date) }
                                    .padding(horizontal = 10.dp, vertical = if (isLandscape) 6.dp else 10.dp)
                            ) {
                                Text(
                                    text = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).uppercase(),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = date.dayOfMonth.toString(),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary
                                    else if (isToday) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface
                                )
                                if (isSelected) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(4.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary)
                                    )
                                }
                            }
                        }
                    }

                    // Right Arrow (>)
                    IconButton(
                        onClick = {
                            val next = when (viewMode) {
                                CalendarViewMode.DAY, CalendarViewMode.AGENDA -> selectedDate.plusDays(1)
                                CalendarViewMode.WEEK -> selectedDate.plusWeeks(1)
                                else -> selectedDate.plusDays(1)
                            }
                            onDateSelected(next)
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Next Date"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(if (isLandscape) 6.dp else 16.dp))
            }

            // 5. Categories Section & Filter Pills (With Action Icons beside "Other" in Landscape Mode)
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Categories",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                )

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    item {
                        FilterChip(
                            selected = selectedCategoryFilter == null,
                            onClick = { onCategoryFilterSelected(null) },
                            label = { Text("All", fontWeight = FontWeight.SemiBold, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        )
                    }

                    items(Category.entries.toTypedArray()) { category ->
                        val isSelected = selectedCategoryFilter == category
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                onCategoryFilterSelected(if (isSelected) null else category)
                            },
                            label = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(category.color)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(category.displayName, fontWeight = FontWeight.Medium, fontSize = 11.sp)
                                }
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = category.color.copy(alpha = 0.25f),
                                containerColor = category.color.copy(alpha = 0.12f)
                            )
                        )
                    }

                    // In Landscape Mode ONLY: Action Icons placed right beside "Other" category pill!
                    if (isLandscape) {
                        item {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(start = 12.dp)
                            ) {
                                IconButton(onClick = onShareClick, modifier = Modifier.size(32.dp)) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = "Share Schedule",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(onClick = onNotificationClick, modifier = Modifier.size(32.dp)) {
                                    Icon(
                                        imageVector = Icons.Default.Notifications,
                                        contentDescription = "Notifications",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(onClick = onToggleDarkMode, modifier = Modifier.size(32.dp)) {
                                    Icon(
                                        imageVector = if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                                        contentDescription = "Toggle Dark Mode",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Month & Year Fast Picker Navigation Dialog
    if (isMonthPickerOpen) {
        MonthYearPickerDialog(
            selectedDate = selectedDate,
            onDismiss = { isMonthPickerOpen = false },
            onDateSelected = { newDate ->
                onDateSelected(newDate)
            }
        )
    }
}

@Composable
fun MonthYearPickerDialog(
    selectedDate: LocalDate,
    onDismiss: () -> Unit,
    onDateSelected: (LocalDate) -> Unit
) {
    var pickerYear by remember { mutableIntStateOf(selectedDate.year) }
    var pickerMonth by remember { mutableIntStateOf(selectedDate.monthValue) }

    val monthNames = listOf("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Navigate Month & Year",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Year Stepper Row (< 2026 >)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    IconButton(
                        onClick = { pickerYear-- },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Previous Year",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    Text(
                        text = pickerYear.toString(),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    IconButton(
                        onClick = { pickerYear++ },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Next Year",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // 12 Months Grid (JAN - DEC)
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    for (rowIdx in 0..2) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            for (colIdx in 0..3) {
                                val monthNumber = rowIdx * 4 + colIdx + 1
                                val isSelectedMonth = pickerMonth == monthNumber

                                Surface(
                                    color = if (isSelectedMonth) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { pickerMonth = monthNumber }
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.padding(vertical = 10.dp)
                                    ) {
                                        Text(
                                            text = monthNames[monthNumber - 1],
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelectedMonth) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelectedMonth) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val maxDays = YearMonth.of(pickerYear, pickerMonth).lengthOfMonth()
                    val validDay = selectedDate.dayOfMonth.coerceIn(1, maxDays)
                    val newDate = LocalDate.of(pickerYear, pickerMonth, validDay)
                    onDateSelected(newDate)
                    onDismiss()
                }
            ) {
                Text("Jump to Date", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
