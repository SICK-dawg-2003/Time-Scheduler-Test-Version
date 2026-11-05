package com.example.time_scheduler.domain

import com.example.time_scheduler.data.Category
import com.example.time_scheduler.data.TaskItem
import com.google.mlkit.vision.text.Text
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

class ScheduleTextParser {

    /**
     * Parses ML Kit Vision Text hierarchy (Blocks -> Lines -> Elements) utilizing spatial bounding box coordinates
     * to accurately group table rows and map out Time, Task Title Name, and Notes/Description columns.
     */
    fun parseVisionTextHierarchy(
        visionText: Text,
        targetDate: LocalDate = LocalDate.now()
    ): List<TaskItem> {
        val allLines = visionText.textBlocks.flatMap { it.lines }
        if (allLines.isEmpty()) {
            return parseScannedText(visionText.text, targetDate)
        }

        // Filter out table header noise lines
        val headerNoise = setOf("time", "activity", "note", "notes", "daily schedule", "schedule", "timetable", "hours")
        val validLines = allLines.filterNot { line ->
            headerNoise.contains(line.text.lowercase().replace(Regex("""[^a-z\s]"""), "").trim())
        }

        // Group lines horizontally into rows based on Y-coordinate bounding box overlap
        val rows = mutableListOf<MutableList<Text.Line>>()
        val sortedLines = validLines.sortedBy { it.boundingBox?.top ?: 0 }

        for (line in sortedLines) {
            val lineTop = line.boundingBox?.top ?: 0
            val lineBottom = line.boundingBox?.bottom ?: 0
            val lineCenterY = (lineTop + lineBottom) / 2

            // Find an existing row where Y-centers are close (within 30 pixels)
            val matchingRow = rows.find { row ->
                val rowCenterY = row.map { (it.boundingBox?.top ?: 0) + (it.boundingBox?.bottom ?: 0) }.average().toInt()
                abs(rowCenterY - lineCenterY) < 30
            }

            if (matchingRow != null) {
                matchingRow.add(line)
            } else {
                rows.add(mutableListOf(line))
            }
        }

        val parsedTasks = mutableListOf<TaskItem>()
        val timeRangeRegex = Regex(
            """(?i)(\b\d{1,2}(?::\d{2})?\s*(?:AM|PM)?)\s*[-–—to]+\s*(\b\d{1,2}(?::\d{2})?\s*(?:AM|PM)?)""",
            RegexOption.IGNORE_CASE
        )

        for (row in rows) {
            // Sort items in this row from left to right by boundingBox.left
            val sortedRowItems = row.sortedBy { it.boundingBox?.left ?: 0 }
            
            // Look for time range match in the row
            val timeLineIndex = sortedRowItems.indexOfFirst { timeRangeRegex.containsMatchIn(it.text) }
            if (timeLineIndex != -1) {
                val timeLine = sortedRowItems[timeLineIndex]
                val rangeMatch = timeRangeRegex.find(timeLine.text)!!
                val startStr = rangeMatch.groupValues[1].trim()
                val endStr = rangeMatch.groupValues[2].trim()

                // Items to the right of time are Title and Description
                val remainingLines = sortedRowItems.filterIndexed { index, _ -> index != timeLineIndex }

                var taskTitle = ""
                var description = "Imported via Document Scan"

                if (remainingLines.isNotEmpty()) {
                    taskTitle = remainingLines[0].text.trim().replace(Regex("""^[:\-\s•*]+"""), "")
                    if (remainingLines.size >= 2) {
                        description = remainingLines.subList(1, remainingLines.size).joinToString(" ") { it.text.trim() }
                    }
                }

                if (taskTitle.isBlank()) {
                    taskTitle = "Scanned Event"
                }

                val startTime = parseTime(startStr) ?: LocalTime.of(9, 0)
                val endTime = parseTime(endStr) ?: startTime.plusHours(1)
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

        // If spatial row extraction didn't yield tasks, fall back to plain text parser
        return if (parsedTasks.isNotEmpty()) {
            parsedTasks.sortedBy { it.startTime }
        } else {
            parseScannedText(visionText.text, targetDate)
        }
    }

    /**
     * Parses raw OCR text lines from camera scan or image upload and extracts candidate schedule tasks.
     * Incorporates Stack Overflow OCR best practices:
     * - Noise/header line filtering (TIME, ACTIVITY, NOTE)
     * - Column & delimiter-aware table row splitting (|, \t, multi-space)
     * - Multi-line sequential block parsing
     * - Weighted keyword confidence scoring for smart Category classification
     */
    fun parseScannedText(rawText: String, targetDate: LocalDate = LocalDate.now()): List<TaskItem> {
        val rawLines = rawText.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        
        // Filter out table header noise like "TIME", "ACTIVITY", "NOTE", "DAILY SCHEDULE"
        val headerNoise = setOf("time", "activity", "note", "notes", "daily schedule", "schedule", "timetable", "hours")
        val lines = rawLines.filterNot { line ->
            headerNoise.contains(line.lowercase().replace(Regex("""[^a-z\s]"""), "").trim())
        }

        val parsedTasks = mutableListOf<TaskItem>()

        // Time range regex matching: "09:00 - 10:30", "9:00AM to 10:00AM", "9am - 10am", "14:00 - 15:30", "7:00 - 8:00"
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

            // Check if line contains column delimiters like '|', '\t', or multi-space separators
            val columns = line.split(Regex("""[|\t]|\s{2,}""")).map { it.trim() }.filter { it.isNotEmpty() }

            if (columns.size >= 2) {
                // Table layout: Column 0 is time, Column 1 is Activity Title, Column 2 is Note/Description
                val timeColMatch = timeRangeRegex.find(columns[0])
                if (timeColMatch != null) {
                    val startStr = timeColMatch.groupValues[1].trim()
                    val endStr = timeColMatch.groupValues[2].trim()
                    val taskTitle = columns[1]
                    val description = if (columns.size >= 3) columns[2] else "Imported from scanned schedule/flyer"

                    val startTime = parseTime(startStr) ?: LocalTime.of(9, 0)
                    val endTime = parseTime(endStr) ?: startTime.plusHours(1)
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
                    i++
                    continue
                }
            }

            // Single line / multi-line fallback matching
            val rangeMatch = timeRangeRegex.find(line)
            if (rangeMatch != null) {
                val startStr = rangeMatch.groupValues[1].trim()
                val endStr = rangeMatch.groupValues[2].trim()
                
                var title = line.removeRange(rangeMatch.range).trim().replace(Regex("""^[:\-\s•*]+"""), "").trim()
                val description: String

                // If title is empty on this line, check the next line for the title
                if (title.isBlank() && i + 1 < lines.size) {
                    title = lines[i + 1].trim().replace(Regex("""^[:\-\s•*]+"""), "").trim()
                    description = if (i + 2 < lines.size && !timeRangeRegex.containsMatchIn(lines[i + 2])) {
                        lines[i + 2].trim()
                    } else {
                        "Imported from scanned schedule/flyer"
                    }
                    i++
                } else if (i + 1 < lines.size && !timeRangeRegex.containsMatchIn(lines[i + 1])) {
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
            for ((index, line) in lines.take(8).withIndex()) {
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

    /**
     * Confidence-scored Category Classifier.
     * Evaluates weighted keyword occurrences across title and notes/description text.
     */
    private fun detectCategory(text: String): Category {
        val lower = text.lowercase(Locale.ROOT)
        val scores = mutableMapOf<Category, Int>().withDefault { 0 }

        val workKeywords = listOf("work", "meeting", "sync", "project", "review", "client", "office", "standup", "team", "planning", "sprint")
        val studyKeywords = listOf("study", "read", "books", "class", "exam", "homework", "lecture", "assignment", "research", "quiz", "math", "science", "english", "lessons", "practice")
        val healthKeywords = listOf("gym", "health", "doctor", "run", "workout", "cardio", "yoga", "meditation", "hospital", "fitness", "nap", "sleep", "walk", "cycling", "bath", "rest")
        val urgentKeywords = listOf("urgent", "priority", "deadline", "asap", "important", "exam", "critical")
        val personalKeywords = listOf("wake up", "play", "snack", "lunch", "dinner", "movie", "coffee", "personal", "family", "birthday", "party", "home", "dad", "mom", "toys", "puzzles", "tv")

        for (kw in workKeywords) if (lower.contains(kw)) scores[Category.WORK] = scores.getValue(Category.WORK) + 1
        for (kw in studyKeywords) if (lower.contains(kw)) scores[Category.STUDY] = scores.getValue(Category.STUDY) + 1
        for (kw in healthKeywords) if (lower.contains(kw)) scores[Category.HEALTH] = scores.getValue(Category.HEALTH) + 1
        for (kw in urgentKeywords) if (lower.contains(kw)) scores[Category.URGENT] = scores.getValue(Category.URGENT) + 1
        for (kw in personalKeywords) if (lower.contains(kw)) scores[Category.PERSONAL] = scores.getValue(Category.PERSONAL) + 1

        val maxEntry = scores.maxByOrNull { it.value }
        return if (maxEntry != null && maxEntry.value > 0) maxEntry.key else Category.OTHER
    }
}
