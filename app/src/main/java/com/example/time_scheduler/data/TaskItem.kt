package com.example.time_scheduler.data

import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID

data class TaskItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String = "",
    val date: LocalDate = LocalDate.now(),
    val startTime: LocalTime,
    val endTime: LocalTime,
    val category: Category = Category.WORK,
    val isCompleted: Boolean = false,
    val isSyncedGoogle: Boolean = false,
    val isSyncedOutlook: Boolean = false,
    val bufferMinutes: Int = 15
) {
    val formattedTimeString: String
        get() {
            val formatter = DateTimeFormatter.ofPattern("h:mm a")
            return "${startTime.format(formatter)} - ${endTime.format(formatter)}"
        }

    val durationMinutes: Long
        get() = Duration.between(startTime, endTime).toMinutes()
}
