package com.example.time_scheduler.ui.calendar

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.time_scheduler.data.TaskItem
import com.example.time_scheduler.ui.ScheduleViewModel
import com.example.time_scheduler.util.NotificationHelper
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun CalendarScreen(
    viewModel: ScheduleViewModel,
    currentViewMode: CalendarViewMode = CalendarViewMode.DAY,
    isDarkMode: Boolean = false,
    onToggleDarkMode: () -> Unit = {},
    onOpenDrawer: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val selectedDate by viewModel.selectedDate.collectAsState()
    val currentSelectedDate by rememberUpdatedState(selectedDate)
    val allTasks by viewModel.allTasks.collectAsState()
    val conflicts by viewModel.conflicts.collectAsState()
    val categoryFilter by viewModel.selectedCategoryFilter.collectAsState()

    var taskToEdit by remember { mutableStateOf<TaskItem?>(null) }
    var isShowAddDialog by remember { mutableStateOf(false) }
    var isNotificationSheetOpen by remember { mutableStateOf(false) }
    var isDayMonthPickerOpen by remember { mutableStateOf(false) }

    val mainScrollState = rememberScrollState()

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(context, "Notification permission granted!", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    val filteredTasks = remember(allTasks, selectedDate, categoryFilter) {
        allTasks.filter { task ->
            task.date == selectedDate && (categoryFilter == null || task.category == categoryFilter)
        }.sortedBy { it.startTime }
    }

    val categoryFilteredAllTasks = remember(allTasks, categoryFilter) {
        if (categoryFilter == null) allTasks else allTasks.filter { it.category == categoryFilter }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
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
            if (currentViewMode == CalendarViewMode.DAY) {
                // Day View Top Bar:
                // [☰] >> [Month Year ⌄] >> [📅] >> [🌙/☀️] >> [Day / (Date)]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. Hamburger Icon
                    IconButton(
                        onClick = onOpenDrawer,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Open Side Panel",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // 2. Month & Year Display with Downward Arrow
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { isDayMonthPickerOpen = true }
                            .padding(horizontal = 4.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = selectedDate.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            softWrap = false
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Select Month and Year",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // 3. Calendar Icon (Jump to Today)
                    IconButton(
                        onClick = { viewModel.onDateSelected(LocalDate.now()) },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = "Jump to Today",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // 4. Light/Dark Mode Toggle
                    IconButton(
                        onClick = onToggleDarkMode,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Toggle Dark Mode",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // 5. Animated Day Label on Top + Circled Date Number Below (e.g., Tue / (24))
                    AnimatedContent(
                        targetState = selectedDate,
                        transitionSpec = {
                            val forward = targetState.isAfter(initialState)
                            val slideIn = slideInHorizontally(
                                animationSpec = tween(260, easing = FastOutSlowInEasing),
                                initialOffsetX = { fullWidth -> if (forward) fullWidth else -fullWidth }
                            ) + fadeIn(animationSpec = tween(220))
                            val slideOut = slideOutHorizontally(
                                animationSpec = tween(260, easing = FastOutSlowInEasing),
                                targetOffsetX = { fullWidth -> if (forward) -fullWidth else fullWidth }
                            ) + fadeOut(animationSpec = tween(180))
                            slideIn.togetherWith(slideOut)
                        },
                        label = "DayBadgeSlideAnimation"
                    ) { animatedDate ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Text(
                                text = animatedDate.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                            ) {
                                Text(
                                    text = animatedDate.dayOfMonth.toString(),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }
            } else {
                // Full CalendarHeader for Agenda, Week, and Month views
                CalendarHeader(
                    selectedDate = selectedDate,
                    onDateSelected = { viewModel.onDateSelected(it) },
                    selectedCategoryFilter = categoryFilter,
                    onCategoryFilterSelected = { viewModel.onCategoryFilterSelected(it) },
                    viewMode = currentViewMode,
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
                    },
                    onOpenDrawer = onOpenDrawer
                )

                Spacer(modifier = Modifier.height(4.dp))
            }

            // Main Content Area
            Box(
                modifier = if (isLandscape) Modifier.height(550.dp) else Modifier.weight(1f)
            ) {
                when (currentViewMode) {
                    CalendarViewMode.DAY -> {
                        var totalHorizontalDrag by remember { mutableFloatStateOf(0f) }

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    detectHorizontalDragGestures(
                                        onDragStart = { totalHorizontalDrag = 0f },
                                        onHorizontalDrag = { change, dragAmount ->
                                            change.consume()
                                            totalHorizontalDrag += dragAmount
                                        },
                                        onDragEnd = {
                                            val swipeThresholdPx = 75f
                                            if (totalHorizontalDrag < -swipeThresholdPx) {
                                                // Finger slid left -> advance date forward
                                                viewModel.onDateSelected(currentSelectedDate.plusDays(1))
                                            } else if (totalHorizontalDrag > swipeThresholdPx) {
                                                // Finger slid right -> go back to previous date
                                                viewModel.onDateSelected(currentSelectedDate.minusDays(1))
                                            }
                                            totalHorizontalDrag = 0f
                                        },
                                        onDragCancel = { totalHorizontalDrag = 0f }
                                    )
                                }
                        ) {
                            AnimatedContent(
                                targetState = selectedDate,
                                transitionSpec = {
                                    val forward = targetState.isAfter(initialState)
                                    val slideIn = slideInHorizontally(
                                        animationSpec = tween(280, easing = FastOutSlowInEasing),
                                        initialOffsetX = { fullWidth -> if (forward) fullWidth else -fullWidth }
                                    ) + fadeIn(animationSpec = tween(240))
                                    val slideOut = slideOutHorizontally(
                                        animationSpec = tween(280, easing = FastOutSlowInEasing),
                                        targetOffsetX = { fullWidth -> if (forward) -fullWidth else fullWidth }
                                    ) + fadeOut(animationSpec = tween(200))
                                    slideIn.togetherWith(slideOut)
                                },
                                label = "DailyTimelineSlideAnimation"
                            ) { animatedDate ->
                                val dayTasks = remember(allTasks, animatedDate, categoryFilter) {
                                    allTasks.filter { task ->
                                        task.date == animatedDate && (categoryFilter == null || task.category == categoryFilter)
                                    }.sortedBy { it.startTime }
                                }

                                DailyTimelineView(
                                    tasks = dayTasks,
                                    conflicts = if (animatedDate == selectedDate) conflicts else emptyList(),
                                    onToggleTaskCompletion = { viewModel.toggleTaskCompletion(it) },
                                    onDropTask = {
                                        NotificationHelper.cancelTaskAlarms(context, it)
                                        viewModel.dropTask(it)
                                    },
                                    onEditTask = { taskToEdit = it },
                                    onTaskDragged = { task, newTime ->
                                        val updated = task.copy(startTime = newTime)
                                        viewModel.onTaskDragged(task, newTime)
                                        NotificationHelper.scheduleTaskDualAlarms(context, updated)
                                    },
                                    onAutoResolveConflicts = {
                                        conflicts.firstOrNull()?.let { conflict ->
                                            val updated = conflict.taskB.copy(startTime = conflict.taskA.endTime.plusMinutes(15))
                                            viewModel.onTaskDragged(conflict.taskB, conflict.taskA.endTime.plusMinutes(15))
                                            NotificationHelper.scheduleTaskDualAlarms(context, updated)
                                        }
                                    }
                                )
                            }
                        }
                    }

                    CalendarViewMode.WEEK -> {
                        WeeklyGridView(
                            selectedDate = selectedDate,
                            tasks = categoryFilteredAllTasks,
                            onDateSelected = { viewModel.onDateSelected(it) },
                            onEditTask = { taskToEdit = it },
                            onTaskReassigned = { updatedTask ->
                                viewModel.updateTask(updatedTask)
                                NotificationHelper.scheduleTaskDualAlarms(context, updatedTask)
                            }
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
                        if (filteredTasks.isEmpty()) {
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
                            }
                        } else {
                            TabularTableView(
                                tasks = filteredTasks,
                                onEditTask = { taskToEdit = it }
                            )
                        }
                    }
                }
            }
        }
    }

    // Month & Year Picker Dialog for Day View's Month Display
    if (isDayMonthPickerOpen) {
        MonthYearPickerDialog(
            selectedDate = selectedDate,
            onDismiss = { isDayMonthPickerOpen = false },
            onDateSelected = { newDate ->
                viewModel.onDateSelected(newDate)
            }
        )
    }

    if (isShowAddDialog) {
        TaskDialog(
            selectedDate = selectedDate,
            onDismiss = { isShowAddDialog = false },
            onSaveTask = { newTask ->
                viewModel.addTask(newTask)
                val count = NotificationHelper.scheduleTaskDualAlarms(context, newTask)
                isShowAddDialog = false
                Toast.makeText(context, "Task '${newTask.title}' scheduled ($count alarms set)!", Toast.LENGTH_SHORT).show()
            }
        )
    }

    taskToEdit?.let { task ->
        TaskDialog(
            initialTask = task,
            selectedDate = selectedDate,
            onDismiss = { taskToEdit = null },
            onSaveTask = { updatedTask ->
                viewModel.updateTask(updatedTask)
                val count = NotificationHelper.scheduleTaskDualAlarms(context, updatedTask)
                taskToEdit = null
                Toast.makeText(context, "Task '${updatedTask.title}' updated ($count alarms set)!", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (isNotificationSheetOpen) {
        NotificationSettingsSheet(
            tasks = filteredTasks,
            onDismiss = { isNotificationSheetOpen = false }
        )
    }
}