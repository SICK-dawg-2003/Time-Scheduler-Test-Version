package com.example.time_scheduler.ui.calendar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.unit.dp
import com.example.time_scheduler.data.TaskItem
import com.example.time_scheduler.ui.ScheduleViewModel

@Composable
fun CalendarScreen(
    viewModel: ScheduleViewModel,
    modifier: Modifier = Modifier
) {
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
            FloatingActionButton(
                onClick = { isShowAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Task")
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
                }
            )
        }
    }
}
