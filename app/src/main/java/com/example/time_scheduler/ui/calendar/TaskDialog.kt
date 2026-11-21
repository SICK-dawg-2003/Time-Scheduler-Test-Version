package com.example.time_scheduler.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.time_scheduler.data.Category
import com.example.time_scheduler.data.TaskItem
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun TaskDialog(
    initialTask: TaskItem? = null,
    selectedDate: LocalDate,
    onDismiss: () -> Unit,
    onSaveTask: (TaskItem) -> Unit
) {
    var title by remember { mutableStateOf(initialTask?.title ?: "") }
    var description by remember { mutableStateOf(initialTask?.description ?: "") }
    var startHour by remember { mutableIntStateOf(initialTask?.startTime?.hour ?: 9) }
    var startMinute by remember { mutableIntStateOf(initialTask?.startTime?.minute ?: 0) }
    var endHour by remember { mutableIntStateOf(initialTask?.endTime?.hour ?: 10) }
    var endMinute by remember { mutableIntStateOf(initialTask?.endTime?.minute ?: 0) }
    var selectedCategory by remember { mutableStateOf(initialTask?.category ?: Category.WORK) }
    var bufferMinutes by remember { mutableIntStateOf(initialTask?.bufferMinutes ?: 15) }
    var leadTimeMinutes by remember { mutableIntStateOf(initialTask?.leadTimeMinutes ?: 15) }

    var isCategoryDropdownOpen by remember { mutableStateOf(false) }
    var activeCustomTimeTarget by remember { mutableStateOf<String?>(null) } // "START" or "END"

    val timeFormatter = remember { DateTimeFormatter.ofPattern("h:mm a") }
    val leadTimeOptions = listOf(
        15 to "15m before",
        5 to "5m before",
        30 to "30m before",
        60 to "1h before",
        0 to "At start"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialTask == null) "Schedule New Task" else "Edit Task",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Task Title") },
                    leadingIcon = { Icon(Icons.Default.Title, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description / Notes") },
                    leadingIcon = { Icon(Icons.Default.Description, contentDescription = null) },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                // Category Selector
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { isCategoryDropdownOpen = true }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Category, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(selectedCategory.color)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Category: ${selectedCategory.displayName}",
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    DropdownMenu(
                        expanded = isCategoryDropdownOpen,
                        onDismissRequest = { isCategoryDropdownOpen = false }
                    ) {
                        Category.entries.forEach { category ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(12.dp)
                                                .clip(CircleShape)
                                                .background(category.color)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(category.displayName)
                                    }
                                },
                                onClick = {
                                    selectedCategory = category
                                    isCategoryDropdownOpen = false
                                }
                            )
                        }
                    }
                }

                // Arrow Steppers + Direct Custom Time Input
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Start Time Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AccessTime, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Start Time:", fontWeight = FontWeight.SemiBold)
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Backward Arrow (<)
                            IconButton(
                                onClick = {
                                    val time = LocalTime.of(startHour, startMinute).minusMinutes(15)
                                    startHour = time.hour
                                    startMinute = time.minute
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                    contentDescription = "Decrease Start Time",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }

                            // Tappable Custom Time Pill Display
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .clickable { activeCustomTimeTarget = "START" }
                                    .padding(horizontal = 2.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = LocalTime.of(startHour, startMinute).format(timeFormatter),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit Custom Time",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }

                            // Forward Arrow (>)
                            IconButton(
                                onClick = {
                                    val time = LocalTime.of(startHour, startMinute).plusMinutes(15)
                                    startHour = time.hour
                                    startMinute = time.minute
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = "Increase Start Time",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    // End Time Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AccessTime, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("End Time:", fontWeight = FontWeight.SemiBold)
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Backward Arrow (<)
                            IconButton(
                                onClick = {
                                    val time = LocalTime.of(endHour, endMinute).minusMinutes(15)
                                    endHour = time.hour
                                    endMinute = time.minute
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                    contentDescription = "Decrease End Time",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }

                            // Tappable Custom Time Pill Display
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .clickable { activeCustomTimeTarget = "END" }
                                    .padding(horizontal = 2.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = LocalTime.of(endHour, endMinute).format(timeFormatter),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit Custom Time",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }

                            // Forward Arrow (>)
                            IconButton(
                                onClick = {
                                    val time = LocalTime.of(endHour, endMinute).plusMinutes(15)
                                    endHour = time.hour
                                    endMinute = time.minute
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = "Increase End Time",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                // Alarm Lead Time Selector Row
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Reminder Lead Time:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        leadTimeOptions.forEach { (mins, label) ->
                            val isSelected = leadTimeMinutes == mins
                            FilterChip(
                                selected = isSelected,
                                onClick = { leadTimeMinutes = mins },
                                label = { Text(label, fontSize = 10.sp, fontWeight = FontWeight.Medium) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (title.isNotBlank()) {
                        val startTime = LocalTime.of(startHour, startMinute)
                        val endTime = LocalTime.of(endHour, endMinute)
                        val taskToSave = (initialTask ?: TaskItem(
                            title = title,
                            startTime = startTime,
                            endTime = endTime,
                            date = selectedDate
                        )).copy(
                            title = title,
                            description = description,
                            startTime = startTime,
                            endTime = if (endTime.isBefore(startTime)) startTime.plusHours(1) else endTime,
                            category = selectedCategory,
                            bufferMinutes = bufferMinutes,
                            leadTimeMinutes = leadTimeMinutes,
                            date = selectedDate
                        )
                        onSaveTask(taskToSave)
                    }
                }
            ) {
                Text("Save", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )

    // Custom Direct Time Entry Modal Dialog
    activeCustomTimeTarget?.let { target ->
        val currentHour = if (target == "START") startHour else endHour
        val currentMin = if (target == "START") startMinute else endMinute

        CustomTimePickerDialog(
            initialHour = currentHour,
            initialMinute = currentMin,
            title = if (target == "START") "Set Custom Start Time" else "Set Custom End Time",
            onDismiss = { activeCustomTimeTarget = null },
            onTimeSet = { h, m ->
                if (target == "START") {
                    startHour = h
                    startMinute = m
                } else {
                    endHour = h
                    endMinute = m
                }
            }
        )
    }
}

@Composable
fun CustomTimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    title: String,
    onDismiss: () -> Unit,
    onTimeSet: (Int, Int) -> Unit
) {
    var hourText by remember { mutableStateOf(String.format(Locale.US, "%02d", initialHour)) }
    var minuteText by remember { mutableStateOf(String.format(Locale.US, "%02d", initialMinute)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Type custom exact time (24-hour format):",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = hourText,
                        onValueChange = { if (it.length <= 2) hourText = it.filter { c -> c.isDigit() } },
                        label = { Text("Hour (0-23)") },
                        modifier = Modifier.width(100.dp)
                    )

                    Text(
                        text = ":",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

                    OutlinedTextField(
                        value = minuteText,
                        onValueChange = { if (it.length <= 2) minuteText = it.filter { c -> c.isDigit() } },
                        label = { Text("Min (0-59)") },
                        modifier = Modifier.width(100.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val parsedHour = (hourText.toIntOrNull() ?: initialHour).coerceIn(0, 23)
                    val parsedMinute = (minuteText.toIntOrNull() ?: initialMinute).coerceIn(0, 59)
                    onTimeSet(parsedHour, parsedMinute)
                    onDismiss()
                }
            ) {
                Text("Set Time", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
