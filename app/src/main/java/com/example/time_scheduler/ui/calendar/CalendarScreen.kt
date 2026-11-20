package com.example.time_scheduler.ui.calendar

import android.content.Intent
import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.time_scheduler.data.TaskItem
import com.example.time_scheduler.ui.ScheduleViewModel
import com.example.time_scheduler.util.NotificationHelper
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun CalendarScreen(
    viewModel: ScheduleViewModel,
    isDarkMode: Boolean = false,
    onToggleDarkMode: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val selectedDate by viewModel.selectedDate.collectAsState()
    val allTasks by viewModel.allTasks.collectAsState()
    val conflicts by viewModel.conflicts.collectAsState()
    val categoryFilter by viewModel.selectedCategoryFilter.collectAsState()

    var currentViewMode by remember { mutableStateOf(CalendarViewMode.DAY) }
    var taskToEdit by remember { mutableStateOf<TaskItem?>(null) }
    var isShowAddDialog by remember { mutableStateOf(false) }
    var isNotificationSheetOpen by remember { mutableStateOf(false) }

    val mainScrollState = rememberScrollState()

    // Filter tasks by selected date and optional category filter for single day
    val filteredTasks = remember(allTasks, selectedDate, categoryFilter) {
        allTasks.filter { task ->
            task.date == selectedDate && (categoryFilter == null || task.category == categoryFilter)
        }.sortedBy { it.startTime }
    }

    // Filter all tasks by category filter for multi-day/week/month views
    val categoryFilteredAllTasks = remember(allTasks, categoryFilter) {
        if (categoryFilter == null) allTasks else allTasks.filter { it.category == categoryFilter }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            // Clean Single Add Task FAB
            FloatingActionButton(
                onClick = { isShowAddDialog = true },
                shape = RoundedCornerShape(18.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .padding(end = 12.dp, bottom = 12.dp)
                    .size(56.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Task", modifier = Modifier.size(28.dp))
            }
        }
    ) { innerPadding ->
        Column(
            modifier = if (isLandscape) {
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(mainScrollState)
            } else {
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            }
        ) {
            // Header Section with Share, Notification, and Theme Toggle Icons on Top Right
            CalendarHeader(
                selectedDate = selectedDate,
                onDateSelected = { viewModel.onDateSelected(it) },
                selectedCategoryFilter = categoryFilter,
                onCategoryFilterSelected = { viewModel.onCategoryFilterSelected(it) },
                viewMode = currentViewMode,
                onViewModeSelected = { currentViewMode = it },
                isDarkMode = isDarkMode,
                onToggleDarkMode = onToggleDarkMode,
                onNotificationClick = { isNotificationSheetOpen = true },
                onShareClick = {
                    val taskSummary = filteredTasks.joinToString("\n") { "- ${it.title} (${it.startTime} - ${it.endTime})" }
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, "Schedule for $selectedDate:\n$taskSummary")
                        type = "text/plain"
                    }
                    val shareIntent = Intent.createChooser(sendIntent, "Share Schedule")
                    context.startActivity(shareIntent)
                    Toast.makeText(context, "Opening share intent...", Toast.LENGTH_SHORT).show()
                }
            )

            // Date Subheader Row ("Thursday, November 19" & "Today" Badge)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = 16.dp,
                        end = if (isLandscape) 36.dp else 16.dp,
                        top = 6.dp,
                        bottom = 6.dp
                    ),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = selectedDate.format(DateTimeFormatter.ofPattern("EEEE, MMMM d")),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.clickable { viewModel.onDateSelected(LocalDate.now()) }
                ) {
                    Text(
                        text = "Today",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            // Main Content Area (Timeline, Grid, or Empty State Card)
            Box(
                modifier = if (isLandscape) Modifier.height(550.dp) else Modifier.weight(1f)
            ) {
                if (filteredTasks.isEmpty() && (currentViewMode == CalendarViewMode.DAY || currentViewMode == CalendarViewMode.AGENDA)) {
                    // Empty State Card ("No scheduled activities")
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                // Left Purple Vertical Accent Bar
                                Box(
                                    modifier = Modifier
                                        .width(4.dp)
                                        .height(48.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "No scheduled activities",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Your day is currently clear.",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Add a task to start organizing your day.",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Render Daily Timeline below empty state indicator
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
                } else {
                    when (currentViewMode) {
                        CalendarViewMode.DAY -> {
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

                        CalendarViewMode.WEEK -> {
                            WeeklyGridView(
                                selectedDate = selectedDate,
                                tasks = categoryFilteredAllTasks,
                                onDateSelected = { viewModel.onDateSelected(it) },
                                onEditTask = { taskToEdit = it }
                            )
                        }

                        CalendarViewMode.MONTH -> {
                            MonthlyGridView(
                                selectedDate = selectedDate,
                                tasks = categoryFilteredAllTasks,
                                onDateSelected = { viewModel.onDateSelected(it) },
                                onEditTask = { taskToEdit = it }
                            )
                        }

                        CalendarViewMode.AGENDA -> {
                            TabularTableView(
                                tasks = filteredTasks,
                                onEditTask = { taskToEdit = it }
                            )
                        }
                    }
                }
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

        // Notification Settings Bottom Sheet
        if (isNotificationSheetOpen) {
            NotificationSettingsSheet(
                tasks = filteredTasks,
                onDismiss = { isNotificationSheetOpen = false }
            )
        }
    }
}
