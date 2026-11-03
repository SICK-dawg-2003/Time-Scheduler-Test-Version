package com.example.time_scheduler.ui.calendar

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.time_scheduler.data.TaskItem
import com.example.time_scheduler.ui.ScheduleViewModel
import com.example.time_scheduler.util.NotificationHelper

@Composable
fun CalendarScreen(
    viewModel: ScheduleViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val selectedDate by viewModel.selectedDate.collectAsState()
    val allTasks by viewModel.allTasks.collectAsState()
    val conflicts by viewModel.conflicts.collectAsState()
    val categoryFilter by viewModel.selectedCategoryFilter.collectAsState()

    var taskToEdit by remember { mutableStateOf<TaskItem?>(null) }
    var isShowAddDialog by remember { mutableStateOf(false) }

    // Filter tasks by selected date and optional category filter
    val filteredTasks = remember(allTasks, selectedDate, categoryFilter) {
        allTasks.filter { task ->
            task.date == selectedDate && (categoryFilter == null || task.category == categoryFilter)
        }.sortedBy { it.startTime }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Implicit Intent share button
                FloatingActionButton(
                    onClick = {
                        val taskSummary = filteredTasks.joinToString("\n") { "- ${it.title} (${it.startTime} - ${it.endTime})" }
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, "Schedule for $selectedDate:\n$taskSummary")
                            type = "text/plain"
                        }
                        val shareIntent = Intent.createChooser(sendIntent, "Share Schedule")
                        context.startActivity(shareIntent)
                        Toast.makeText(context, "Opening share intent...", Toast.LENGTH_SHORT).show()
                    },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = "Share Schedule (Intent)")
                }

                // Notification test button
                FloatingActionButton(
                    onClick = {
                        NotificationHelper.showReminderNotification(
                            context,
                            "Time Scheduler Reminder",
                            "You have ${filteredTasks.size} tasks scheduled for today."
                        )
                        Toast.makeText(context, "Notification sent!", Toast.LENGTH_SHORT).show()
                    },
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Icon(Icons.Default.Notifications, contentDescription = "Test Notification")
                }

                // Add Task FAB
                FloatingActionButton(
                    onClick = { isShowAddDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Task")
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            CalendarHeader(
                selectedDate = selectedDate,
                onDateSelected = { viewModel.onDateSelected(it) },
                selectedCategoryFilter = categoryFilter,
                onCategoryFilterSelected = { viewModel.onCategoryFilterSelected(it) }
            )

            Box(modifier = Modifier.weight(1f)) {
                DailyTimelineView(
                    tasks = filteredTasks,
                    conflicts = conflicts,
                    onToggleTaskCompletion = { viewModel.toggleTaskCompletion(it) },
                    onDropTask = { viewModel.dropTask(it) },
                    onEditTask = { taskToEdit = it },
                    onTaskDragged = { task, newTime -> viewModel.onTaskDragged(task, newTime) },
                    onAutoResolveConflicts = {
                        conflicts.firstOrNull()?.let { conflict ->
                            viewModel.onTaskDragged(conflict.taskB, conflict.taskA.endTime.plusMinutes(15))
                        }
                    }
                )
            }
        }

        // Add Dialog
        if (isShowAddDialog) {
            TaskDialog(
                selectedDate = selectedDate,
                onDismiss = { isShowAddDialog = false },
                onSaveTask = { newTask ->
                    viewModel.addTask(newTask)
                    isShowAddDialog = false
                    Toast.makeText(context, "Task '${newTask.title}' added successfully!", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // Edit Dialog
        taskToEdit?.let { task ->
            TaskDialog(
                initialTask = task,
                selectedDate = selectedDate,
                onDismiss = { taskToEdit = null },
                onSaveTask = { updatedTask ->
                    viewModel.updateTask(updatedTask)
                    taskToEdit = null
                    Toast.makeText(context, "Task '${updatedTask.title}' updated successfully!", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}
