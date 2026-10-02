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
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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

    var isCategoryDropdownOpen by remember { mutableStateOf(false) }

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
                        .padding(vertical = 8.dp),
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

                // Time Pickers (Simple hour selection for robust cross-version compatibility)
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AccessTime, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Start Time:")
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { startHour = (startHour + 1) % 24 }) {
                                Text(String.format(Locale.US, "%02d:%02d", startHour, startMinute), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AccessTime, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("End Time:")
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { endHour = (endHour + 1) % 24 }) {
                                Text(String.format(Locale.US, "%02d:%02d", endHour, endMinute), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
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
}
