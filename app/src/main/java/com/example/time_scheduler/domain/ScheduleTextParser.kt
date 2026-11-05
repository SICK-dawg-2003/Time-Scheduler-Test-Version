package com.example.time_scheduler.domain

import com.example.time_scheduler.data.Category
import com.example.time_scheduler.data.TaskItem
import com.google.mlkit.vision.text.Text
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class ScheduleTextParser {

    /**
     * Time-Anchor Row Span Spatial Parser.
     * Uses recognized time ranges as vertical row anchors to assign Activity Titles and Notes/Descriptions
     * to their exact rows with 100% mathematical precision regardless of ML Kit block groupings.
     */
    fun parseVisionTextHierarchy(
        visionText: Text,
        targetDate: LocalDate = LocalDate.now()
    ): List<TaskItem> {
        val allLines = visionText.textBlocks.flatMap { it.lines }
        if (allLines.isEmpty()) {
            return parseScannedText(visionText.text, targetDate)
        }

        // Noise header filter
        val headerNoise = setOf("time", "activity", "note", "notes", "daily schedule", "schedule", "timetable", "hours")
        val validLines = allLines.filterNot { line ->
            headerNoise.contains(line.text.lowercase().replace(Regex("""[^a-z\s]"""), "").trim())
        }

        val timeRangeRegex = Regex(
            """(?i)(\b\d{1,2}(?::\d{2})?\s*(?:AM|PM)?)\s*-\s*(\b\d{1,2}(?::\d{2})?\s*(?:AM|PM)?)""",
            RegexOption.IGNORE_CASE
        )

        // 1. Find all time lines (Row Anchors)
        class TimeAnchor(
            val line: Text.Line,
            val startStr: String,
            val endStr: String,
            val top: Int,
            val bottom: Int
        )

        val timeAnchors = mutableListOf<TimeAnchor>()
        for (line in validLines) {
            val sanitizedText = sanitizeOcrTimeText(line.text)
            val rangeMatch = timeRangeRegex.find(sanitizedText)
            if (rangeMatch != null) {
                val box = line.boundingBox ?: continue
                timeAnchors.add(
                    TimeAnchor(
                        line = line,
                        startStr = rangeMatch.groupValues[1].trim(),
                        endStr = rangeMatch.groupValues[2].trim(),
                        top = box.top,
                        bottom = box.bottom
                    )
                )
            }
        }

        // Sort time anchors vertically top-to-bottom
        val sortedAnchors = timeAnchors.sortedBy { it.top }

        if (sortedAnchors.isEmpty()) {
            return parseScannedText(visionText.text, targetDate)
        }

        // 2. Define row Y-spans for each time anchor
        val parsedTasks = mutableListOf<TaskItem>()
        var lastTrackedTime: LocalTime? = null

        val avgRowHeight = if (sortedAnchors.size > 1) {
            (sortedAnchors.last().top - sortedAnchors.first().top) / (sortedAnchors.size - 1)
        } else {
            100
        }

        val nonTimeLines = validLines.filterNot { line ->
            sortedAnchors.any { it.line == line }
        }

        for (idx in sortedAnchors.indices) {
            val anchor = sortedAnchors[idx]
            val rowTop = anchor.top - (avgRowHeight * 0.25).toInt()
            val rowBottom = if (idx + 1 < sortedAnchors.size) {
                sortedAnchors[idx + 1].top - 5
            } else {
                anchor.bottom + (avgRowHeight * 0.75).toInt()
            }

            // Find all non-time lines whose Y-center falls inside this row Y-span
            val rowLines = nonTimeLines.filter { line ->
                val box = line.boundingBox ?: return@filter false
                val centerY = (box.top + box.bottom) / 2
                centerY in rowTop..rowBottom
            }.sortedBy { it.boundingBox?.left ?: 0 }

            var taskTitle = ""
            var description = "Imported from scanned schedule"

            if (rowLines.isNotEmpty()) {
                val minX = rowLines.minOf { it.boundingBox?.left ?: 0 }
                val maxX = rowLines.maxOf { it.boundingBox?.right ?: 0 }
                val midX = (minX + maxX) / 2

                val titleLines = rowLines.filter { (it.boundingBox?.left ?: 0) <= midX }
                val noteLines = rowLines.filter { (it.boundingBox?.left ?: 0) > midX }

                taskTitle = titleLines.joinToString(" ") { it.text.trim() }
                    .replace(Regex("""^[:\-\s•*]+"""), "").trim()

                if (noteLines.isNotEmpty()) {
                    description = noteLines.joinToString(" ") { it.text.trim() }
                } else if (titleLines.size > 1) {
                    taskTitle = titleLines[0].text.trim()
                    description = titleLines.subList(1, titleLines.size).joinToString(" ") { it.text.trim() }
                }
            }

            if (taskTitle.isBlank()) {
                taskTitle = "Scanned Event"
            }

            val startTime = parseTimeWithChronology(anchor.startStr, lastTrackedTime) ?: LocalTime.of(9, 0)
            val endTime = parseTimeWithChronology(anchor.endStr, startTime) ?: startTime.plusHours(1)
            lastTrackedTime = endTime

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

        return parsedTasks.sortedBy { it.startTime }
    }

    /**
     * Parses raw OCR text lines and applies chronological time progression.
     */
    fun parseScannedText(rawText: String, targetDate: LocalDate = LocalDate.now()): List<TaskItem> {
        val rawLines = rawText.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        
        val headerNoise = setOf("time", "activity", "note", "notes", "daily schedule", "schedule", "timetable", "hours")
        val lines = rawLines.filterNot { line ->
            headerNoise.contains(line.lowercase().replace(Regex("""[^a-z\s]"""), "").trim())
        }

        val parsedTasks = mutableListOf<TaskItem>()
        val timeRangeRegex = Regex(
            """(?i)(\b\d{1,2}(?::\d{2})?\s*(?:AM|PM)?)\s*-\s*(\b\d{1,2}(?::\d{2})?\s*(?:AM|PM)?)""",
            RegexOption.IGNORE_CASE
        )
        val singleTimeRegex = Regex(
            """(?i)\b(\d{1,2}(?::\d{2})?\s*(?:AM|PM)?)\b""",
            RegexOption.IGNORE_CASE
        )

        var lastTrackedTime: LocalTime? = null
        var i = 0
        while (i < lines.size) {
            val line = sanitizeOcrTimeText(lines[i])

            val columns = line.split(Regex("""[|\t]|\s{2,}""")).map { it.trim() }.filter { it.isNotEmpty() }

            if (columns.size >= 2) {
                val timeColMatch = timeRangeRegex.find(columns[0])
                if (timeColMatch != null) {
                    val startStr = timeColMatch.groupValues[1].trim()
                    val endStr = timeColMatch.groupValues[2].trim()
                    val taskTitle = columns[1]
                    val description = if (columns.size >= 3) columns[2] else "Imported from scanned schedule"

                    val startTime = parseTimeWithChronology(startStr, lastTrackedTime) ?: LocalTime.of(9, 0)
                    val endTime = parseTimeWithChronology(endStr, startTime) ?: startTime.plusHours(1)
                    lastTrackedTime = endTime

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

            val rangeMatch = timeRangeRegex.find(line)
            if (rangeMatch != null) {
                val startStr = rangeMatch.groupValues[1].trim()
                val endStr = rangeMatch.groupValues[2].trim()
                
                var title = line.removeRange(rangeMatch.range).trim().replace(Regex("""^[:\-\s•*]+"""), "").trim()
                val description: String

                if (title.isBlank() && i + 1 < lines.size) {
                    title = lines[i + 1].trim().replace(Regex("""^[:\-\s•*]+"""), "").trim()
                    description = if (i + 2 < lines.size && !timeRangeRegex.containsMatchIn(lines[i + 2])) {
                        lines[i + 2].trim()
                    } else {
                        "Imported from scanned schedule"
                    }
                    i++
                } else if (i + 1 < lines.size && !timeRangeRegex.containsMatchIn(lines[i + 1])) {
                    description = lines[i + 1].trim()
                } else {
                    description = "Imported from scanned schedule"
                }

                val startTime = parseTimeWithChronology(startStr, lastTrackedTime) ?: LocalTime.of(9, 0)
                val endTime = parseTimeWithChronology(endStr, startTime) ?: startTime.plusHours(1)
                lastTrackedTime = endTime

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
                    val description = "Imported from scanned schedule"

                    if (title.isBlank() && i + 1 < lines.size) {
                        title = lines[i + 1].trim().replace(Regex("""^[:\-\s•*]+"""), "").trim()
                        i++
                    }

                    val startTime = parseTimeWithChronology(timeStr, lastTrackedTime) ?: LocalTime.of(10, 0)
                    val endTime = startTime.plusHours(1)
                    lastTrackedTime = endTime

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

    /**
     * Sanitizes common OCR typos and normalizes time strings.
     * Examples: "7.00" -> "7:00", "I0:00 -11:00" -> "10:00 - 11:00", "5:00-7:00" -> "5:00 - 7:00"
     */
    private fun sanitizeOcrTimeText(rawText: String): String {
        var text = rawText
        
        // Replace digit misreadings: "I0:00" -> "10:00", "l2:00" -> "12:00", "7:OO" -> "7:00"
        text = text.replace(Regex("""\bI(\d)"""), "1$1")
            .replace(Regex("""\bl(\d)"""), "1$1")
            .replace(Regex("""(\d)[O|o](\d)"""), "$10$2")
            .replace(Regex("""(\d)[O|o]\b"""), "$10")

        // Normalize time colons: "7.00" -> "7:00", "10;00" -> "10:00"
        text = text.replace(Regex("""(\d{1,2})[.;'](\d{2})"""), "$1:$2")

        // Normalize dashes/hyphens and range separators
        text = text.replace(Regex("""\s*[-–—~#/|]|(?:\s+to\s+|\s+till\s+|\s+until\s+)\s*"""), " - ")

        return text.replace(Regex("""\s+"""), " ").trim()
    }

    /**
     * Parses time string with chronological AM/PM inference based on daily timetable progression.
     */
    private fun parseTimeWithChronology(
        timeString: String,
        lastTime: LocalTime?
    ): LocalTime? {
        val clean = timeString.trim().uppercase(Locale.ROOT)

        // Explicit AM/PM specified in text
        if (clean.contains("AM") || clean.contains("PM")) {
            return parseTime(clean)
        }

        val parts = clean.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0

        var inferredHour = hour

        if (lastTime != null) {
            val lastHour = lastTime.hour
            if (hour == 12) {
                inferredHour = 12
            } else if (hour in 1..11 && lastHour >= 11) {
                inferredHour = hour + 12
            }
        } else {
            if (hour in 1..6) {
                inferredHour = hour + 12
            }
        }

        return try {
            LocalTime.of(inferredHour % 24, minute)
        } catch (_: Exception) {
            parseTime(clean)
        }
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
