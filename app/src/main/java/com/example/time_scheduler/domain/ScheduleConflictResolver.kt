package com.example.time_scheduler.domain

import com.example.time_scheduler.data.TaskItem
import java.time.LocalTime
import java.time.Duration

data class ScheduleConflict(
    val taskA: TaskItem,
    val taskB: TaskItem,
    val overlapMinutes: Long,
    val description: String
)

sealed class ResolutionStrategy {
    data class ShiftTask(val taskToShift: TaskItem, val newStartTime: LocalTime, val newEndTime: LocalTime) : ResolutionStrategy()
    data class AdjustBuffer(val taskToAdjust: TaskItem, val newBufferMinutes: Int) : ResolutionStrategy()
    data class DropTask(val taskToDrop: TaskItem) : ResolutionStrategy()
}

class ScheduleConflictResolver {

    /**
     * Checks list of tasks for any time overlaps.
     */
    fun findConflicts(tasks: List<TaskItem>): List<ScheduleConflict> {
        val sortedTasks = tasks.sortedBy { it.startTime }
        val conflicts = mutableListOf<ScheduleConflict>()

        for (i in 0 until sortedTasks.size) {
            for (j in i + 1 until sortedTasks.size) {
                val t1 = sortedTasks[i]
                val t2 = sortedTasks[j]

                // Check overlap
                if (t1.startTime.isBefore(t2.endTime) && t2.startTime.isBefore(t1.endTime)) {
                    val overlapStart = if (t1.startTime.isAfter(t2.startTime)) t1.startTime else t2.startTime
                    val overlapEnd = if (t1.endTime.isBefore(t2.endTime)) t1.endTime else t2.endTime
                    val overlapMinutes = Duration.between(overlapStart, overlapEnd).toMinutes()

                    conflicts.add(
                        ScheduleConflict(
                            taskA = t1,
                            taskB = t2,
                            overlapMinutes = overlapMinutes,
                            description = "Overlapping time discrepancy between '${t1.title}' and '${t2.title}' (${overlapMinutes} min overlap)."
                        )
                    )
                }
            }
        }
        return conflicts
    }

    /**
     * Automatically adjusts time for a dropped or dragged task to avoid discrepancy.
     */
    fun autoResolveDiscrepancy(
        draggedTask: TaskItem,
        newStartTime: LocalTime,
        existingTasks: List<TaskItem>
    ): List<TaskItem> {
        val durationMinutes = draggedTask.durationMinutes
        val newEndTime = newStartTime.plusMinutes(durationMinutes)
        val updatedTask = draggedTask.copy(startTime = newStartTime, endTime = newEndTime)

        val otherTasks = existingTasks.filter { it.id != draggedTask.id }.sortedBy { it.startTime }
        val result = mutableListOf<TaskItem>()
        var currentEndTime = updatedTask.endTime

        result.add(updatedTask)

        for (task in otherTasks) {
            if (task.startTime.isBefore(currentEndTime) && !task.startTime.isBefore(updatedTask.startTime)) {
                // Shift subsequent overlapping task forward
                val shiftDuration = Duration.between(task.startTime, currentEndTime).toMinutes() + task.bufferMinutes
                val shiftedStart = task.startTime.plusMinutes(shiftDuration)
                val shiftedEnd = task.endTime.plusMinutes(shiftDuration)
                val shiftedTask = task.copy(startTime = shiftedStart, endTime = shiftedEnd)
                result.add(shiftedTask)
                currentEndTime = shiftedEnd
            } else {
                result.add(task)
            }
        }

        return result.sortedBy { it.startTime }
    }
}
