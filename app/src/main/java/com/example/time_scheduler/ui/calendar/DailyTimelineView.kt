package com.example.time_scheduler.ui.calendar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.time_scheduler.data.TaskItem
import com.example.time_scheduler.domain.ScheduleConflict
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private const val HOUR_HEIGHT_DP = 72

@Composable
fun DailyTimelineView(
    tasks: List<TaskItem>,
    conflicts: List<ScheduleConflict>,
    onToggleTaskCompletion: (String) -> Unit,
    onDropTask: (String) -> Unit,
    onEditTask: (TaskItem) -> Unit,
    onTaskDragged: (TaskItem, LocalTime) -> Unit,
    onAutoResolveConflicts: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(modifier = modifier.fillMaxSize()) {
        // Discrepancy Conflict Alert Banner
        AnimatedVisibility(visible = conflicts.isNotEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Conflict Warning",
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Schedule Discrepancy Detected!",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = "${conflicts.size} overlapping task slot(s). Click resolve to auto-adjust buffers.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }

                    Button(
                        onClick = onAutoResolveConflicts,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text("Auto-Resolve", fontSize = 12.sp)
                    }
                }
            }
        }

        // Timeline Scroll Area
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(bottom = 80.dp)
        ) {
            // Background Hour Lines & Time Labels
            Column(modifier = Modifier.fillMaxSize()) {
                for (hour in 6..23) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(HOUR_HEIGHT_DP.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = String.format("%02d:00", hour),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .width(54.dp)
                                .padding(start = 12.dp, top = 4.dp)
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(HOUR_HEIGHT_DP.dp)
                        ) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                thickness = 1.dp,
                                modifier = Modifier.align(Alignment.TopCenter)
                            )
                        }
                    }
                }
            }

            // Task Cards Layer placed on timeline with drag-and-drop offset
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 58.dp, end = 12.dp)
            ) {
                tasks.forEach { task ->
                    TimelineTaskCard(
                        task = task,
                        onToggleCompletion = { onToggleTaskCompletion(task.id) },
                        onDropTask = { onDropTask(task.id) },
                        onEditTask = { onEditTask(task) },
                        onDragEnd = { newStart -> onTaskDragged(task, newStart) }
                    )
                }
            }
        }
    }
}

@Composable
fun TimelineTaskCard(
    task: TaskItem,
    onToggleCompletion: () -> Unit,
    onDropTask: () -> Unit,
    onEditTask: () -> Unit,
    onDragEnd: (LocalTime) -> Unit
) {
    var offsetY by remember { mutableFloatStateOf(0f) }
    var isMenuExpanded by remember { mutableStateOf(false) }

    // Calculate Y offset based on start time (from 06:00 baseline)
    val startHourOffset = (task.startTime.hour - 6) + (task.startTime.minute / 60f)
    val topPx = startHourOffset * HOUR_HEIGHT_DP
    val heightDp = ((task.durationMinutes / 60f) * HOUR_HEIGHT_DP).coerceAtLeast(54f)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(heightDp.dp)
            .offset { IntOffset(0, (topPx + offsetY).roundToInt()) }
            .pointerInput(task) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        offsetY += dragAmount.y
                    },
                    onDragEnd = {
                        // Calculate shifted time based on offset delta
                        val hourDelta = offsetY / HOUR_HEIGHT_DP
                        val minutesDelta = (hourDelta * 60).roundToInt()
                        var newTime = task.startTime.plusMinutes(minutesDelta.toLong())

                        // Clamp to valid hours 06:00 to 22:00
                        if (newTime.isBefore(LocalTime.of(6, 0))) {
                            newTime = LocalTime.of(6, 0)
                        }
                        if (newTime.isAfter(LocalTime.of(22, 0))) {
                            newTime = LocalTime.of(22, 0)
                        }

                        offsetY = 0f
                        onDragEnd(newTime)
                    }
                )
            },
        colors = CardDefaults.cardColors(
            containerColor = task.category.color.copy(alpha = 0.18f)
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .border(
                    width = 1.dp,
                    color = task.category.color.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp)
                )
        ) {
            // Left color-coded vertical bar indicator
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .fillMaxSize()
                    .background(task.category.color)
            )

            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Checkbox & Task details
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(
                        onClick = onToggleCompletion,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = if (task.isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                            contentDescription = "Toggle Complete",
                            tint = if (task.isCompleted) MaterialTheme.colorScheme.primary else task.category.color,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = task.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = task.formattedTimeString,
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (task.isSyncedGoogle || task.isSyncedOutlook) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Default.Sync,
                                    contentDescription = "Synced",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }

                        if (task.description.isNotBlank() && heightDp > 60) {
                            Text(
                                text = task.description,
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        }
                    }
                }

                // Category Tag & Action Menu
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(task.category.color.copy(alpha = 0.2f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = task.category.displayName,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = task.category.color
                        )
                    }

                    Box {
                        IconButton(
                            onClick = { isMenuExpanded = true },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More Actions",
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = isMenuExpanded,
                            onDismissRequest = { isMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Edit Task") },
                                onClick = {
                                    isMenuExpanded = false
                                    onEditTask()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Drop Task (Delete)", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Drop",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                onClick = {
                                    isMenuExpanded = false
                                    onDropTask()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
