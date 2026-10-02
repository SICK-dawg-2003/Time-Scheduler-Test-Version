package com.example.time_scheduler.domain

import com.example.time_scheduler.data.Category
import com.example.time_scheduler.data.TaskItem
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class ScheduleTextParser {

    /**
     * Parses raw OCR text lines from camera scan and extracts candidate schedule tasks.
     */
    fun parseScannedText(rawText: String, targetDate: LocalDate = LocalDate.now()): List<TaskItem> {
        val lines = rawText.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        val parsedTasks = mutableListOf<TaskItem>()

        // Patterns to match times like "09:00 - 10:30", "9:00 AM", "14:00", "2:30 PM - 4:00 PM"
        val timeRangeRegex = Regex(
            """(?i)(\d{1,2}:\d{2}\s*(?:AM|PM)?)\s*[-–—to]+\s*(\d{1,2}:\d{2}\s*(?:AM|PM)?)""",
            RegexOption.IGNORE_CASE
        )
        val singleTimeRegex = Regex(
            """(?i)\b(\d{1,2}:\d{2}\s*(?:AM|PM)?)\b""",
            RegexOption.IGNORE_CASE
        )

        for (line in lines) {
            val rangeMatch = timeRangeRegex.find(line)
            if (rangeMatch != null) {
                val startStr = rangeMatch.groupValues[1].trim()
                val endStr = rangeMatch.groupValues[2].trim()
                val title = line.removeRange(rangeMatch.range).trim().replace(Regex("""^[:\-\s]+"""), "")

                val startTime = parseTime(startStr) ?: LocalTime.of(9, 0)
                val endTime = parseTime(endStr) ?: startTime.plusHours(1)
                val taskTitle = if (title.isBlank()) "Scanned Event" else title

                parsedTasks.add(
                    TaskItem(
                        title = taskTitle,
                        description = "Imported via Camera OCR Scan",
                        date = targetDate,
                        startTime = startTime,
                        endTime = endTime,
                        category = detectCategory(taskTitle)
                    )
                )
            } else {
                val singleMatch = singleTimeRegex.find(line)
                if (singleMatch != null) {
                    val timeStr = singleMatch.groupValues[1].trim()
                    val title = line.removeRange(singleMatch.range).trim().replace(Regex("""^[:\-\s]+"""), "")
                    val startTime = parseTime(timeStr) ?: LocalTime.of(10, 0)
                    val endTime = startTime.plusHours(1)
                    val taskTitle = if (title.isBlank()) "Scanned Task" else title

                    parsedTasks.add(
                        TaskItem(
                            title = taskTitle,
                            description = "Imported via Camera OCR Scan",
                            date = targetDate,
                            startTime = startTime,
                            endTime = endTime,
                            category = detectCategory(taskTitle)
                        )
                    )
                }
            }
        }

        // Fallback if no specific times matched but text exists
        if (parsedTasks.isEmpty() && lines.isNotEmpty()) {
            var defaultStart = LocalTime.of(9, 0)
            for ((index, line) in lines.take(4).withIndex()) {
                val title = line.take(40)
                if (title.isNotBlank()) {
                    parsedTasks.add(
                        TaskItem(
                            title = title,
                            description = "Extracted from scanned text block ${index + 1}",
                            date = targetDate,
                            startTime = defaultStart,
                            endTime = defaultStart.plusHours(1),
                            category = detectCategory(title)
                        )
                    )
                    defaultStart = defaultStart.plusHours(1)
                }
            }
        }

        return parsedTasks.sortedBy { it.startTime }
    }

    private fun parseTime(timeString: String): LocalTime? {
        val clean = timeString.trim().uppercase(Locale.ROOT)
        val formatters = listOf(
            DateTimeFormatter.ofPattern("h:mm a"),
            DateTimeFormatter.ofPattern("hh:mm a"),
            DateTimeFormatter.ofPattern("H:mm"),
            DateTimeFormatter.ofPattern("HH:mm")
        )

        for (formatter in formatters) {
            try {
                return LocalTime.parse(clean, formatter)
            } catch (_: Exception) {}
        }
        return null
    }

    private fun detectCategory(title: String): Category {
        val lower = title.lowercase()
        return when {
            lower.contains("work") || lower.contains("meeting") || lower.contains("sync") || lower.contains("project") || lower.contains("review") -> Category.WORK
            lower.contains("study") || lower.contains("read") || lower.contains("class") || lower.contains("exam") -> Category.STUDY
            lower.contains("gym") || lower.contains("health") || lower.contains("doctor") || lower.contains("run") || lower.contains("workout") -> Category.HEALTH
            lower.contains("urgent") || lower.contains("priority") || lower.contains("deadline") || lower.contains("asap") -> Category.URGENT
            lower.contains("lunch") || lower.contains("dinner") || lower.contains("movie") || lower.contains("coffee") || lower.contains("personal") -> Category.PERSONAL
            else -> Category.OTHER
        }
    }
}
