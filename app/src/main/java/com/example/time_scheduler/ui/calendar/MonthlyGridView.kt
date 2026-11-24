package com.example.time_scheduler.ui.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.time_scheduler.data.TaskItem
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun MonthlyGridView(
    selectedDate: LocalDate,
    tasks: List<TaskItem>,
    onDateSelected: (LocalDate) -> Unit,
    onEditTask: (TaskItem) -> Unit,
    onOpenDayView: (LocalDate) -> Unit = onDateSelected,
    modifier: Modifier = Modifier
) {
    val yearMonth = remember(selectedDate) { YearMonth.from(selectedDate) }
    val firstDayOfMonth = remember(yearMonth) { yearMonth.atDay(1) }
    val daysInMonth = yearMonth.lengthOfMonth()

    val firstDayOfWeekOffset = firstDayOfMonth.dayOfWeek.value % 7

    val weekRows = remember(firstDayOfMonth, daysInMonth, firstDayOfWeekOffset) {
        val startDate = firstDayOfMonth.minusDays(firstDayOfWeekOffset.toLong())
        (0..41).map { startDate.plusDays(it.toLong()) }.chunked(7)
    }

    val dayOfWeekNames = listOf("SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT")

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        // Day of Week Header Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            dayOfWeekNames.forEach { dayName ->
                Text(
                    text = dayName,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // 6x7 Full-Height Calendar Days Grid
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(top = 6.dp, bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            weekRows.forEach { weekDates ->
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    weekDates.forEach { date ->
                        val isCurrentMonth = date.month == selectedDate.month
                        val isSelected = date == selectedDate
                        val isToday = date == LocalDate.now()
                        val dayTasks = tasks.filter { it.date == date }

                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    onDateSelected(date)
                                    onOpenDayView(date)
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                else if (isToday) MaterialTheme.colorScheme.surfaceVariant
                                else if (isCurrentMonth) MaterialTheme.colorScheme.surface
                                else MaterialTheme.colorScheme.surfaceContainerLowest
                            ),
                            border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
                        ) {
                            BoxWithConstraints(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(4.dp)
                            ) {
                                val maxVisibleTasks = ((maxHeight - 28.dp) / 18.dp).toInt().coerceIn(2, 4)

                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = date.dayOfMonth.toString(),
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                        else if (!isCurrentMonth) MaterialTheme.colorScheme.outline
                                        else if (isToday) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface
                                    )

                                    Spacer(modifier = Modifier.height(2.dp))

                                    // Task Pills (Clicking a pill triggers onEditTask without switching to Day View)
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(2.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        dayTasks.take(maxVisibleTasks).forEach { task ->
                                            Surface(
                                                color = task.category.color.copy(alpha = 0.2f),
                                                shape = RoundedCornerShape(4.dp),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { onEditTask(task) }
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .width(2.5.dp)
                                                            .height(14.dp)
                                                            .background(task.category.color)
                                                    )
                                                    Spacer(modifier = Modifier.width(2.dp))
                                                    Text(
                                                        text = task.title,
                                                        fontSize = 8.sp,
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.padding(vertical = 1.dp)
                                                    )
                                                }
                                            }
                                        }
                                        if (dayTasks.size > maxVisibleTasks) {
                                            Text(
                                                text = "+${dayTasks.size - maxVisibleTasks} more",
                                                fontSize = 7.sp,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.fillMaxWidth()
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