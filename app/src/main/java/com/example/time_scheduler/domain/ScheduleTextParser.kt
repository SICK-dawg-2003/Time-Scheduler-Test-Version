package com.example.time_scheduler.domain

import com.example.time_scheduler.data.Category
import com.example.time_scheduler.data.TaskItem
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class ScheduleTextParser {

    /**
     * Parses raw OCR text lines from camera scan or image upload and extracts candidate schedule tasks.
     * Enhanced to support multi-line text blocks, robust time parsing (12h/24h/am-pm/9am), rich title/description extraction, and smart category detection.
     */
    fun parseScannedText(rawText: String, targetDate: LocalDate = LocalDate.now()): List<TaskItem> {
        val lines = rawText.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        val parsedTasks = mutableListOf<TaskItem>()

        // Flexible time range regex matching: "09:00 - 10:30", "9:00AM to 10:00AM", "9am - 10am", "14:00 - 15:30"
        val timeRangeRegex = Regex(
            """(?i)(\b\d{1,2}(?::\d{2})?\s*(?:AM|PM)?)\s*[-–—to]+\s*(\b\d{1,2}(?::\d{2})?\s*(?:AM|PM)?)""",
            RegexOption.IGNORE_CASE
        )
        val singleTimeRegex = Regex(
            """(?i)\b(\d{1,2}(?::\d{2})?\s*(?:AM|PM)?)\b""",
            RegexOption.IGNORE_CASE
        )

        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val rangeMatch = timeRangeRegex.find(line)

            if (rangeMatch != null) {
                val startStr = rangeMatch.groupValues[1].trim()
                val endStr = rangeMatch.groupValues[2].trim()
                
                // Extract title from same line or next line if empty
                var title = line.removeRange(rangeMatch.range).trim().replace(Regex("""^[:\-\s•*]+"""), "").trim()
                val description: String

                // If title is empty on this line, check the next line for the title
                if (title.isBlank() && i + 1 < lines.size) {
                    title = lines[i + 1].trim().replace(Regex("""^[:\-\s•*]+"""), "").trim()
                    description = "Imported from scanned schedule/flyer"
                    i++ // consume next line as title
                } else if (i + 1 < lines.size && !timeRangeRegex.containsMatchIn(lines[i + 1])) {
                    // Next line might be a description or notes
                    description = lines[i + 1].trim()
                } else {
                    description = "Imported from scanned schedule/flyer"
                }

                val startTime = parseTime(startStr) ?: LocalTime.of(9, 0)
                val endTime = parseTime(endStr) ?: startTime.plusHours(1)
                val taskTitle = if (title.isBlank()) "Scanned Event" else title
                val combinedText = "$taskTitle $description"

                parsedTasks.add(
                    TaskItem(
                        title = taskTitle,
                        description = description,
                        date = targetDate,
                        startTime = startTime,
                        endTime = endTime,
                        category = detectCategory(combinedText)
                    )
                )
            } else {
                val singleMatch = singleTimeRegex.find(line)
                if (singleMatch != null) {
                    val timeStr = singleMatch.groupValues[1].trim()
                    var title = line.removeRange(singleMatch.range).trim().replace(Regex("""^[:\-\s•*]+"""), "").trim()
                    val description = "Imported from scanned schedule/flyer"

                    if (title.isBlank() && i + 1 < lines.size) {
                        title = lines[i + 1].trim().replace(Regex("""^[:\-\s•*]+"""), "").trim()
                        i++
                    }

                    val startTime = parseTime(timeStr) ?: LocalTime.of(10, 0)
                    val endTime = startTime.plusHours(1)
                    val taskTitle = if (title.isBlank()) "Scanned Task" else title
                    val combinedText = "$taskTitle $description"

                    parsedTasks.add(
                        TaskItem(
                            title = taskTitle,
                            description = description,
                            date = targetDate,
                            startTime = startTime,
                            endTime = endTime,
                            category = detectCategory(combinedText)
                        )
                    )
                }
            }
            i++
        }

        // Fallback: If no explicit times matched, treat text blocks as sequential tasks
        if (parsedTasks.isEmpty() && lines.isNotEmpty()) {
            var defaultStart = LocalTime.of(9, 0)
            for ((index, line) in lines.take(6).withIndex()) {
                val cleanLine = line.replace(Regex("""^[:\-\s•*]+"""), "").trim()
                if (cleanLine.isNotBlank() && cleanLine.length > 2) {
                    val parts = cleanLine.split(":", limit = 2)
                    val title = parts[0].trim()
                    val desc = if (parts.size > 1) parts[1].trim() else "Extracted from scanned text block ${index + 1}"
                    
                    parsedTasks.add(
                        TaskItem(
                            title = if (title.length > 35) title.take(35) else title,
                            description = desc,
                            date = targetDate,
                            startTime = defaultStart,
                            endTime = defaultStart.plusHours(1),
                            category = detectCategory("$title $desc")
                        )
                    )
                    defaultStart = defaultStart.plusHours(1)
                }
            }
        }

        return parsedTasks.sortedBy { it.startTime }
    }

    private fun parseTime(timeString: String): LocalTime? {
        var clean = timeString.trim().uppercase(Locale.ROOT)
        if (clean.matches(Regex("""\d{1,2}(?:AM|PM)"""))) {
            clean = clean.replace(Regex("""(AM|PM)"""), ":00 $1")
        } else if (clean.matches(Regex("""\d{1,2}:\d{2}(?:AM|PM)"""))) {
            clean = clean.replace(Regex("""(AM|PM)"""), " $1")
        }

        val formatters = listOf(
            DateTimeFormatter.ofPattern("h:mm a"),
            DateTimeFormatter.ofPattern("hh:mm a"),
            DateTimeFormatter.ofPattern("H:mm"),
            DateTimeFormatter.ofPattern("HH:mm"),
            DateTimeFormatter.ofPattern("h a"),
            DateTimeFormatter.ofPattern("hh a")
        )

        for (formatter in formatters) {
            try {
                return LocalTime.parse(clean, formatter)
            } catch (_: Exception) {}
        }
        return null
    }

    private fun detectCategory(text: String): Category {
        val lower = text.lowercase()
        return when {
            lower.contains("work") || lower.contains("meeting") || lower.contains("sync") || lower.contains("project") || lower.contains("review") || lower.contains("client") || lower.contains("office") || lower.contains("standup") || lower.contains("team") -> Category.WORK
            lower.contains("study") || lower.contains("read") || lower.contains("class") || lower.contains("exam") || lower.contains("homework") || lower.contains("lecture") || lower.contains("assignment") || lower.contains("research") || lower.contains("quiz") -> Category.STUDY
            lower.contains("gym") || lower.contains("health") || lower.contains("doctor") || lower.contains("run") || lower.contains("workout") || lower.contains("cardio") || lower.contains("yoga") || lower.contains("meditation") || lower.contains("hospital") || lower.contains("fitness") -> Category.HEALTH
            lower.contains("urgent") || lower.contains("priority") || lower.contains("deadline") || lower.contains("asap") || lower.contains("important") || lower.contains("exam") -> Category.URGENT
            lower.contains("lunch") || lower.contains("dinner") || lower.contains("movie") || lower.contains("coffee") || lower.contains("personal") || lower.contains("family") || lower.contains("birthday") || lower.contains("party") || lower.contains("home") -> Category.PERSONAL
            else -> Category.OTHER
        }
    }
}
