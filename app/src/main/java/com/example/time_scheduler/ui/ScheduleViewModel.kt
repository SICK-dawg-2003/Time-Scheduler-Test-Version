package com.example.time_scheduler.ui

import androidx.lifecycle.ViewModel
import com.example.time_scheduler.data.Category
import com.example.time_scheduler.data.ProfileState
import com.example.time_scheduler.data.TaskItem
import com.example.time_scheduler.domain.ScheduleConflict
import com.example.time_scheduler.domain.ScheduleTextParser
import com.example.time_scheduler.repository.ScheduleRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.time.LocalTime

class ScheduleViewModel(
    private val repository: ScheduleRepository = ScheduleRepository()
) : ViewModel() {

    private val textParser = ScheduleTextParser()

    val selectedDate: StateFlow<LocalDate> = repository.selectedDate
    val allTasks: StateFlow<List<TaskItem>> = repository.tasks
    val conflicts: StateFlow<List<ScheduleConflict>> = repository.conflicts
    val profileState: StateFlow<ProfileState> = repository.profileState
    val selectedCategoryFilter: StateFlow<Category?> = repository.selectedCategoryFilter

    private val _scannedCandidateTasks = MutableStateFlow<List<TaskItem>>(emptyList())
    val scannedCandidateTasks: StateFlow<List<TaskItem>> = _scannedCandidateTasks.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    fun onDateSelected(date: LocalDate) {
        repository.setSelectedDate(date)
    }

    fun onCategoryFilterSelected(category: Category?) {
        repository.setCategoryFilter(category)
    }

    fun addTask(task: TaskItem) {
        repository.addTask(task)
    }

    fun updateTask(task: TaskItem) {
        repository.updateTask(task)
    }

    fun dropTask(taskId: String) {
        repository.dropTask(taskId)
    }

    fun toggleTaskCompletion(taskId: String) {
        repository.toggleTaskCompletion(taskId)
    }

    fun onTaskDragged(draggedTask: TaskItem, newStartTime: LocalTime) {
        repository.handleDragAndDropReorder(draggedTask, newStartTime)
    }

    fun processScannedImageText(rawText: String) {
        _isScanning.value = true
        val candidates = textParser.parseScannedText(rawText, selectedDate.value)
        _scannedCandidateTasks.value = candidates
        _isScanning.value = false
    }

    fun confirmImportScannedTasks(tasksToImport: List<TaskItem>) {
        repository.importScannedTasks(tasksToImport)
        _scannedCandidateTasks.value = emptyList()
    }

    fun clearScannedCandidates() {
        _scannedCandidateTasks.value = emptyList()
    }

    fun updateNotificationsSettings(enabled: Boolean, sound: Boolean, quietHours: Boolean, leadTimeMinutes: Int) {
        repository.updateNotificationsSettings(enabled, sound, quietHours, leadTimeMinutes)
    }

    fun toggleGoogleSync(enabled: Boolean) {
        repository.toggleGoogleSync(enabled)
    }

    fun toggleOutlookSync(enabled: Boolean) {
        repository.toggleOutlookSync(enabled)
    }

    fun triggerGoogleSyncNow() {
        repository.triggerGoogleCalendarSync()
    }

    fun triggerOutlookSyncNow() {
        repository.triggerOutlookCalendarSync()
    }
}
