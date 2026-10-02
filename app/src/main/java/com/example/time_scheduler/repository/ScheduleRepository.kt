package com.example.time_scheduler.repository

import android.content.Context
import com.example.time_scheduler.data.Category
import com.example.time_scheduler.data.TaskItem
import com.example.time_scheduler.domain.ScheduleConflict
import com.example.time_scheduler.domain.ScheduleConflictResolver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.LocalTime

class ScheduleRepository(context: Context? = null) {

    private val taskStorage = context?.let { TaskStorage(it.applicationContext) }
    private val conflictResolver = ScheduleConflictResolver()

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    private val _tasks = MutableStateFlow<List<TaskItem>>(taskStorage?.loadTasks() ?: emptyList())
    val tasks: StateFlow<List<TaskItem>> = _tasks.asStateFlow()

    private val _conflicts = MutableStateFlow<List<ScheduleConflict>>(emptyList())
    val conflicts: StateFlow<List<ScheduleConflict>> = _conflicts.asStateFlow()

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
        saveTasksToStorage()
    }

    fun updateTask(task: TaskItem) {
        _tasks.update { current ->
            current.map { if (it.id == task.id) task else it }.sortedBy { it.startTime }
        }
        recalculateConflicts()
        saveTasksToStorage()
    }

    fun dropTask(taskId: String) {
        _tasks.update { current ->
            current.filterNot { it.id == taskId }
        }
        recalculateConflicts()
        saveTasksToStorage()
    }

    fun toggleTaskCompletion(taskId: String) {
        _tasks.update { current ->
            current.map {
                if (it.id == taskId) it.copy(isCompleted = !it.isCompleted) else it
            }
        }
        saveTasksToStorage()
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
        saveTasksToStorage()
    }

    fun importScannedTasks(scannedTasks: List<TaskItem>) {
        _tasks.update { current ->
            (current + scannedTasks).sortedBy { it.startTime }
        }
        recalculateConflicts()
        saveTasksToStorage()
    }

    private fun saveTasksToStorage() {
        taskStorage?.saveTasks(_tasks.value)
    }

    private fun recalculateConflicts() {
        val dateTasks = _tasks.value.filter { it.date == _selectedDate.value }
        _conflicts.value = conflictResolver.findConflicts(dateTasks)
    }
}
