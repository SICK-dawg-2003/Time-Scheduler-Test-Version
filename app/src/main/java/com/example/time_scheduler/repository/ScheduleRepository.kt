package com.example.time_scheduler.repository

import com.example.time_scheduler.data.Category
import com.example.time_scheduler.data.ProfileState
import com.example.time_scheduler.data.TaskItem
import com.example.time_scheduler.domain.ScheduleConflict
import com.example.time_scheduler.domain.ScheduleConflictResolver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.LocalTime

class ScheduleRepository {

    private val conflictResolver = ScheduleConflictResolver()

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    private val _tasks = MutableStateFlow<List<TaskItem>>(getInitialSampleTasks())
    val tasks: StateFlow<List<TaskItem>> = _tasks.asStateFlow()

    private val _conflicts = MutableStateFlow<List<ScheduleConflict>>(emptyList())
    val conflicts: StateFlow<List<ScheduleConflict>> = _conflicts.asStateFlow()

    private val _profileState = MutableStateFlow(ProfileState())
    val profileState: StateFlow<ProfileState> = _profileState.asStateFlow()

    private val _selectedCategoryFilter = MutableStateFlow<Category?>(null)
    val selectedCategoryFilter: StateFlow<Category?> = _selectedCategoryFilter.asStateFlow()

    init {
        recalculateConflicts()
    }

    fun setSelectedDate(date: LocalDate) {
        _selectedDate.value = date
        recalculateConflicts()
    }

    fun setCategoryFilter(category: Category?) {
        _selectedCategoryFilter.value = category
    }

    fun addTask(task: TaskItem) {
        _tasks.update { current ->
            (current + task).sortedBy { it.startTime }
        }
        recalculateConflicts()
        updateActivityStats()
    }

    fun updateTask(task: TaskItem) {
        _tasks.update { current ->
            current.map { if (it.id == task.id) task else it }.sortedBy { it.startTime }
        }
        recalculateConflicts()
        updateActivityStats()
    }

    fun dropTask(taskId: String) {
        _tasks.update { current ->
            current.filterNot { it.id == taskId }
        }
        recalculateConflicts()
        updateActivityStats()
    }

    fun toggleTaskCompletion(taskId: String) {
        _tasks.update { current ->
            current.map {
                if (it.id == taskId) it.copy(isCompleted = !it.isCompleted) else it
            }
        }
        updateActivityStats()
    }

    fun handleDragAndDropReorder(draggedTask: TaskItem, newStartTime: LocalTime) {
        _tasks.update { current ->
            val resolvedList = conflictResolver.autoResolveDiscrepancy(
                draggedTask = draggedTask,
                newStartTime = newStartTime,
                existingTasks = current
            )
            resolvedList
        }
        recalculateConflicts()
    }

    fun importScannedTasks(scannedTasks: List<TaskItem>) {
        _tasks.update { current ->
            (current + scannedTasks).sortedBy { it.startTime }
        }
        recalculateConflicts()
        updateActivityStats()
    }

    fun updateNotificationsSettings(enabled: Boolean, sound: Boolean, quietHours: Boolean, leadTimeMinutes: Int) {
        _profileState.update { current ->
            current.copy(
                notifications = current.notifications.copy(
                    notificationsEnabled = enabled,
                    soundEnabled = sound,
                    quietHoursEnabled = quietHours,
                    leadTimeMinutes = leadTimeMinutes
                )
            )
        }
    }

    fun toggleGoogleSync(enabled: Boolean) {
        _profileState.update { current ->
            current.copy(
                googleSync = current.googleSync.copy(
                    isConnected = enabled,
                    lastSyncTime = if (enabled) "Just now" else "Disconnected"
                )
            )
        }
        if (enabled) {
            triggerGoogleCalendarSync()
        }
    }

    fun toggleOutlookSync(enabled: Boolean) {
        _profileState.update { current ->
            current.copy(
                outlookSync = current.outlookSync.copy(
                    isConnected = enabled,
                    lastSyncTime = if (enabled) "Just now" else "Disconnected"
                )
            )
        }
        if (enabled) {
            triggerOutlookCalendarSync()
        }
    }

    fun triggerGoogleCalendarSync() {
        _profileState.update { current ->
            current.copy(
                googleSync = current.googleSync.copy(
                    isSyncing = true
                )
            )
        }
        // Simulate sync completion
        _tasks.update { list ->
            list.map { it.copy(isSyncedGoogle = true) }
        }
        _profileState.update { current ->
            current.copy(
                googleSync = current.googleSync.copy(
                    isSyncing = false,
                    lastSyncTime = "Just now"
                )
            )
        }
    }

    fun triggerOutlookCalendarSync() {
        _profileState.update { current ->
            current.copy(
                outlookSync = current.outlookSync.copy(
                    isSyncing = true
                )
            )
        }
        _tasks.update { list ->
            list.map { it.copy(isSyncedOutlook = true) }
        }
        _profileState.update { current ->
            current.copy(
                outlookSync = current.outlookSync.copy(
                    isSyncing = false,
                    lastSyncTime = "Just now"
                )
            )
        }
    }

    private fun recalculateConflicts() {
        val dateTasks = _tasks.value.filter { it.date == _selectedDate.value }
        _conflicts.value = conflictResolver.findConflicts(dateTasks)
    }

    private fun updateActivityStats() {
        val todayTasks = _tasks.value.filter { it.date == LocalDate.now() }
        val completed = todayTasks.count { it.isCompleted }
        val total = todayTasks.size

        _profileState.update { current ->
            current.copy(
                activity = current.activity.copy(
                    tasksCompletedToday = completed,
                    totalTasksToday = total
                )
            )
        }
    }

    private fun getInitialSampleTasks(): List<TaskItem> {
        val today = LocalDate.now()
        return listOf(
            TaskItem(
                title = "Team Morning Standup & Sync",
                description = "Review daily sprint goals and blocker updates with engineering team.",
                date = today,
                startTime = LocalTime.of(9, 0),
                endTime = LocalTime.of(9, 45),
                category = Category.WORK,
                isCompleted = true,
                isSyncedGoogle = true,
                isSyncedOutlook = true
            ),
            TaskItem(
                title = "Gym & Morning Workout",
                description = "Cardio session and strength training.",
                date = today,
                startTime = LocalTime.of(10, 0),
                endTime = LocalTime.of(11, 0),
                category = Category.HEALTH,
                isCompleted = true,
                isSyncedGoogle = true
            ),
            TaskItem(
                title = "System Architecture & API Review",
                description = "Finalize data schema and Google/Outlook calendar sync abstractions.",
                date = today,
                startTime = LocalTime.of(11, 30),
                endTime = LocalTime.of(13, 0),
                category = Category.WORK,
                isCompleted = false,
                isSyncedGoogle = true,
                isSyncedOutlook = true
            ),
            TaskItem(
                title = "Lunch & Relaxation Break",
                description = "Healthy lunch and short walk outdoor.",
                date = today,
                startTime = LocalTime.of(13, 0),
                endTime = LocalTime.of(14, 0),
                category = Category.PERSONAL,
                isCompleted = false
            ),
            TaskItem(
                title = "Android Mobile Development Study",
                description = "Explore Jetpack Compose M3 and ML Kit Text Recognition APIs.",
                date = today,
                startTime = LocalTime.of(14, 30),
                endTime = LocalTime.of(16, 0),
                category = Category.STUDY,
                isCompleted = false
            ),
            TaskItem(
                title = "Project Deadline Review (Urgent)",
                description = "Verify schedule discrepancy resolution and drop time collateral features.",
                date = today,
                startTime = LocalTime.of(16, 30),
                endTime = LocalTime.of(17, 30),
                category = Category.URGENT,
                isCompleted = false
            )
        )
    }
}
