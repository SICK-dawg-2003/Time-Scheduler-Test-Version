package com.example.time_scheduler.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.time_scheduler.data.TaskItem
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

private const val HOUR_ROW_HEIGHT_DP = 64

@Composable
fun WeeklyGridView(
    selectedDate: LocalDate,
    tasks: List<TaskItem>,
    onDateSelected: (LocalDate) -> Unit,
    onEditTask: (TaskItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val hourFormatter = remember { DateTimeFormatter.ofPattern("h a") }

    // Start week on Sunday or Monday
    val startOfWeek = remember(selectedDate) {
        val daysFromSunday = selectedDate.dayOfWeek.value % 7
        selectedDate.minusDays(daysFromSunday.toLong())
    }
    val weekDates = remember(startOfWeek) {
        (0..6).map { startOfWeek.plusDays(it.toLong()) }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // 7-Day Header Row (Sun to Sat)
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 54.dp, top = 6.dp, bottom = 6.dp, end = 4.dp)
            ) {
                weekDates.forEach { date ->
                    val isSelected = date == selectedDate
                    val isToday = date == LocalDate.now()

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                else if (isToday) MaterialTheme.colorScheme.surfaceVariant
                                else Color.Transparent
                            )
                            .clickable { onDateSelected(date) }
                            .padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).uppercase(),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = date.dayOfMonth.toString(),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                            else if (isToday) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // 7-Day Grid Scroll Area (6:00 AM through 11:00 PM)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(bottom = 80.dp)
        ) {
            // Background Hour Grid Lines & Time Sidebar
            Column(modifier = Modifier.fillMaxSize()) {
                for (hour in 6..23) {
                    val hourLabel = LocalTime.of(hour, 0).format(hourFormatter)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(HOUR_ROW_HEIGHT_DP.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = hourLabel,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .width(50.dp)
                                .padding(start = 6.dp, top = 2.dp)
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(HOUR_ROW_HEIGHT_DP.dp)
                        ) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                thickness = 1.dp,
                                modifier = Modifier.align(Alignment.TopCenter)
                            )
                        }
                    }
                }
            }

            // 7 Columns Grid Content Layer
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 50.dp, end = 4.dp)
            ) {
                weekDates.forEach { colDate ->
                    val dayTasks = tasks.filter { it.date == colDate }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height((18 * HOUR_ROW_HEIGHT_DP).dp)
                            .border(
                                width = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                            )
                    ) {
                        dayTasks.forEach { task ->
                            val startMinuteOffset = (task.startTime.hour - 6) * 60 + task.startTime.minute
                            val durationMinutes = (task.endTime.hour - task.startTime.hour) * 60 + (task.endTime.minute - task.startTime.minute)
                            val topDp = (startMinuteOffset.toFloat() / 60f) * HOUR_ROW_HEIGHT_DP
                            val heightDp = (durationMinutes.toFloat() / 60f) * HOUR_ROW_HEIGHT_DP

                            if (topDp >= 0) {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 1.dp)
                                        .offset { IntOffset(0, topDp.dp.toPx().roundToInt()) }
                                        .height(heightDp.coerceAtLeast(26f).dp)
                                        .clickable { onEditTask(task) },
                                    colors = CardDefaults.cardColors(
                                        containerColor = task.category.color.copy(alpha = 0.2f)
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(1.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Left Vertical Accent Bar (Matching user screenshot!)
                                        Box(
                                            modifier = Modifier
                                                .width(3.dp)
                                                .fillMaxHeight()
                                                .clip(RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp))
                                                .background(task.category.color)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = task.title,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 9.5.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (heightDp >= 38) {
                                                Text(
                                                    text = task.formattedTimeString,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    fontSize = 8.sp,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
