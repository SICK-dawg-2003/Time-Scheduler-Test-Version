package com.example.time_scheduler.domain

import com.example.time_scheduler.data.Category
import com.example.time_scheduler.data.TaskItem
import com.google.mlkit.vision.text.Text
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class ScheduleTextParser {

    private val headerNoise = setOf(
        "time", "activity", "note", "notes", "daily schedule",
        "schedule", "timetable", "hours", "subject", "room", "day", "date"
    )

    // Requires at least one side of the range to have :MM or AM/PM so dates (11/24) or room numbers (3-4) are ignored
    private val strictTimeRangeRegex = Regex(
        """(?i)\b(\d{1,2}:\d{2}\s*(?:AM|PM)?|\d{1,2}\s*(?:AM|PM))\s*(?:[-–—~]|to|till|until)\s*(\d{1,2}(?::\d{2})?\s*(?:AM|PM)?)\b"""
    )

    private val strictSingleTimeRegex = Regex(
        """(?i)\b(\d{1,2}:\d{2}\s*(?:AM|PM)?|\d{1,2}\s*(?:AM|PM))\b"""
    )

    /**
     * Time-Anchor Row Span Spatial Parser with OCR confidence and symbol-noise filtering.
     */
    fun parseVisionTextHierarchy(
        visionText: Text,
        targetDate: LocalDate = LocalDate.now()
    ): List<TaskItem> {
        val allLines = visionText.textBlocks.flatMap { it.lines }
        if (allLines.isEmpty()) {
            return parseScannedText(visionText.text, targetDate)
        }

        // 1. Filter out low-confidence OCR artifacts, symbol noise, and table headers
        val validLines = allLines.filter { line ->
            val text = line.text.trim()
            val confidenceOk = (line.confidence ?: 1.0f) >= 0.65f
            val alnumCount = text.count { it.isLetterOrDigit() }
            val cleanWord = text.lowercase(Locale.ROOT).replace(Regex("""[^a-z\s]"""), "").trim()

            confidenceOk &&
                    text.length >= 2 &&
                    alnumCount >= (text.length * 0.5) &&
                    !headerNoise.contains(cleanWord)
        }

        if (validLines.isEmpty()) return emptyList()

        // 2. Find all verified time lines (Row Anchors)
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
            val rangeMatch = strictTimeRangeRegex.find(sanitizedText)
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

        val sortedAnchors = timeAnchors.sortedBy { it.top }
        if (sortedAnchors.isEmpty()) {
            return parseScannedText(
                validLines.joinToString("\n") { it.text },
                targetDate
            )
        }

        // 3. Assign non-time text lines to their vertical row spans in natural reading order
        val parsedTasks = mutableListOf<TaskItem>()
        var lastTrackedTime: LocalTime? = null

        val avgRowHeight = if (sortedAnchors.size > 1) {
            ((sortedAnchors.last().top - sortedAnchors.first().top) / (sortedAnchors.size - 1))
                .coerceAtLeast(40)
        } else {
            100
        }

        val nonTimeLines = validLines.filterNot { line ->
            sortedAnchors.any { it.line == line }
        }

        for (idx in sortedAnchors.indices) {
            val anchor = sortedAnchors[idx]
            val rowTop = anchor.top - (avgRowHeight * 0.35).toInt()
            val rowBottom = if (idx + 1 < sortedAnchors.size) {
                sortedAnchors[idx + 1].top - 2
            } else {
                anchor.bottom + (avgRowHeight * 0.85).toInt()
            }

            // Sort top-to-bottom (bucketed by 20px line height), then left-to-right
            val rowLines = nonTimeLines.filter { line ->
                val box = line.boundingBox ?: return@filter false
                val centerY = (box.top + box.bottom) / 2
                centerY in rowTop..rowBottom
            }.sortedWith(
                compareBy(
                    { (it.boundingBox?.top ?: 0) / 20 },
                    { it.boundingBox?.left ?: 0 }
                )
            )

            var taskTitle = ""
            var description = "Imported from scanned schedule"

            if (rowLines.isNotEmpty()) {
                val minX = rowLines.minOf { it.boundingBox?.left ?: 0 }
                val maxX = rowLines.maxOf { it.boundingBox?.right ?: 0 }
                val columnSpan = maxX - minX

                // Only split into Title vs. Notes columns if text spans wide horizontally (> 220px)
                if (columnSpan > 220) {
                    val midX = minX + (columnSpan * 0.55).toInt()
                    val titleLines = rowLines.filter { (it.boundingBox?.left ?: 0) <= midX }
                    val noteLines = rowLines.filter { (it.boundingBox?.left ?: 0) > midX }

                    taskTitle = cleanExtractedText(titleLines.joinToString(" ") { it.text.trim() })
                    if (noteLines.isNotEmpty()) {
                        description = cleanExtractedText(noteLines.joinToString(" ") { it.text.trim() })
                    } else if (titleLines.size > 1) {
                        taskTitle = cleanExtractedText(titleLines[0].text)
                        description = cleanExtractedText(titleLines.drop(1).joinToString(" ") { it.text.trim() })
                    }
                } else {
                    taskTitle = cleanExtractedText(rowLines[0].text)
                    if (rowLines.size > 1) {
                        description = cleanExtractedText(rowLines.drop(1).joinToString(" ") { it.text.trim() })
                    }
                }
            }

            val startTime = parseTimeWithChronology(anchor.startStr, lastTrackedTime) ?: continue
            val endTime = parseTimeWithChronology(anchor.endStr, startTime) ?: startTime.plusHours(1)
            lastTrackedTime = endTime

            // Skip empty placeholder rows if no readable title was captured
            if (taskTitle.length < 2) continue

            val combinedText = "$taskTitle$description"
            parsedTasks.add(
                TaskItem(
                    title = taskTitle,
                    description = description.ifBlank { "Imported from scanned schedule" },
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
     * Parses raw OCR text lines when spatial bounding boxes are unavailable.
     * Never fabricates tasks if no valid timestamps are found.
     */
    fun parseScannedText(rawText: String, targetDate: LocalDate = LocalDate.now()): List<TaskItem> {
        val lines = rawText.split("\n")
            .map { it.trim() }
            .filter { line ->
                val alnumCount = line.count { it.isLetterOrDigit() }
                val cleanWord = line.lowercase(Locale.ROOT).replace(Regex("""[^a-z\s]"""), "").trim()
                line.length >= 2 &&
                        alnumCount >= (line.length * 0.5) &&
                        !headerNoise.contains(cleanWord)
            }

        if (lines.isEmpty()) return emptyList()

        val parsedTasks = mutableListOf<TaskItem>()
        var lastTrackedTime: LocalTime? = null
        var i = 0

        while (i < lines.size) {
            val line = sanitizeOcrTimeText(lines[i])
            val columns = line.split(Regex("""[|\t]|\s{2,}""")).map { it.trim() }.filter { it.isNotEmpty() }

            if (columns.size >= 2) {
                val timeColMatch = strictTimeRangeRegex.find(columns[0])
                if (timeColMatch != null) {
                    val startStr = timeColMatch.groupValues[1].trim()
                    val endStr = timeColMatch.groupValues[2].trim()
                    val taskTitle = cleanExtractedText(columns[1])
                    val description = if (columns.size >= 3) cleanExtractedText(columns[2]) else "Imported from scanned schedule"

                    val startTime = parseTimeWithChronology(startStr, lastTrackedTime)
                    if (startTime != null && taskTitle.length >= 2) {
                        val endTime = parseTimeWithChronology(endStr, startTime) ?: startTime.plusHours(1)
                        lastTrackedTime = endTime
                        parsedTasks.add(
                            TaskItem(
                                title = taskTitle,
                                description = description,
                                date = targetDate,
                                startTime = startTime,
                                endTime = endTime,
                                category = detectCategory("$taskTitle$description")
                            )
                        )
                    }
                    i++
                    continue
                }
            }

            val rangeMatch = strictTimeRangeRegex.find(line)
            if (rangeMatch != null) {
                val startStr = rangeMatch.groupValues[1].trim()
                val endStr = rangeMatch.groupValues[2].trim()

                var title = cleanExtractedText(line.removeRange(rangeMatch.range))
                var description = "Imported from scanned schedule"

                if (title.isBlank() && i + 1 < lines.size && !strictTimeRangeRegex.containsMatchIn(sanitizeOcrTimeText(lines[i + 1]))) {
                    title = cleanExtractedText(lines[i + 1])
                    if (i + 2 < lines.size && !strictTimeRangeRegex.containsMatchIn(sanitizeOcrTimeText(lines[i + 2]))) {
                        description = cleanExtractedText(lines[i + 2])
                        i += 2
                    } else {
                        i++
                    }
                } else if (i + 1 < lines.size && !strictTimeRangeRegex.containsMatchIn(sanitizeOcrTimeText(lines[i + 1]))) {
                    description = cleanExtractedText(lines[i + 1])
                    i++
                }

                val startTime = parseTimeWithChronology(startStr, lastTrackedTime)
                if (startTime != null && title.length >= 2) {
                    val endTime = parseTimeWithChronology(endStr, startTime) ?: startTime.plusHours(1)
                    lastTrackedTime = endTime
                    parsedTasks.add(
                        TaskItem(
                            title = title,
                            description = description.ifBlank { "Imported from scanned schedule" },
                            date = targetDate,
                            startTime = startTime,
                            endTime = endTime,
                            category = detectCategory("$title$description")
                        )
                    )
                }
            } else {
                val singleMatch = strictSingleTimeRegex.find(line)
                if (singleMatch != null) {
                    val timeStr = singleMatch.groupValues[1].trim()
                    var title = cleanExtractedText(line.removeRange(singleMatch.range))

                    if (title.isBlank() && i + 1 < lines.size && !strictSingleTimeRegex.containsMatchIn(sanitizeOcrTimeText(lines[i + 1]))) {
                        title = cleanExtractedText(lines[i + 1])
                        i++
                    }

                    val startTime = parseTimeWithChronology(timeStr, lastTrackedTime)
                    if (startTime != null && title.length >= 2) {
                        val endTime = startTime.plusHours(1)
                        lastTrackedTime = endTime
                        parsedTasks.add(
                            TaskItem(
                                title = title,
                                description = "Imported from scanned schedule",
                                date = targetDate,
                                startTime = startTime,
                                endTime = endTime,
                                category = detectCategory(title)
                            )
                        )
                    }
                }
            }
            i++
        }

        return parsedTasks.sortedBy { it.startTime }
    }

    private fun cleanExtractedText(raw: String): String {
        return raw
            .replace(Regex("""^[|:\-\s•*._]+"""), "")
            .replace(Regex("""[|]+$"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    /**
     * Targeted OCR Time Sanitizer that fixes common digit misreads without corrupting prose words.
     */
    private fun sanitizeOcrTimeText(rawText: String): String {
        var text = rawText

        // Fix digit misreads only within time-like patterns (e.g., "I0:00" -> "10:00", "7:OO" -> "7:00")
        text = text.replace(Regex("""\b[Il](\d:\d{2})"""), "1$1")
            .replace(Regex("""(\d{1,2}:)[Oo]{2}\b"""), "${1}00")
            .replace(Regex("""(\d{1,2}:)[Oo](\d)\b"""), "${1}0$2")
            .replace(Regex("""(\d{1,2}:\d)[Oo]\b"""), "${1}0")
            .replace(Regex("""\bS:(\d{2})"""), "5:$1")

        // Normalize time separators ("7.00" -> "7:00", "10;00" -> "10:00")
        text = text.replace(Regex("""(\d{1,2})[.;](\d{2})"""), "$1:$2")

        // Format compact AM/PM times ("10am" -> "10:00 AM", "9pm" -> "9:00 PM")
        text = text.replace(Regex("""\b(\d{1,2})\s*(am|AM)\b"""), "$1:00 AM")
            .replace(Regex("""\b(\d{1,2})\s*(pm|PM)\b"""), "$1:00 PM")

        // Normalize range dashes/words without altering slashes in dates
        text = text.replace(Regex("""\s*(?:[-–—~]|(?:\bto\b|\btill\b|\buntil\b))\s*""", RegexOption.IGNORE_CASE), " - ")

        return text.replace(Regex("""\s+"""), " ").trim()
    }

    private fun parseTimeWithChronology(
        timeString: String,
        lastTime: LocalTime?
    ): LocalTime? {
        val clean = timeString.trim().uppercase(Locale.ROOT)

        if (clean.contains("AM") || clean.contains("PM")) {
            return parseTime(clean)
        }

        val parts = clean.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0

        if (hour !in 0..23 || minute !in 0..59) return null

        var inferredHour = hour
        if (lastTime != null) {
            val lastHour = lastTime.hour
            if (hour == 12) {
                inferredHour = 12
            } else if (hour in 1..11 && lastHour >= 11) {
                inferredHour = hour + 12
            }
        } else if (hour in 1..6) {
            inferredHour = hour + 12
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
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun detectCategory(text: String): Category {
        val lower = text.lowercase(Locale.ROOT)
        val scores = mutableMapOf<Category, Int>().withDefault { 0 }

        val workKeywords = listOf("work", "meeting", "sync", "project", "review", "client", "office", "standup", "team", "planning", "sprint")
        val studyKeywords = listOf("study", "read", "books", "class", "exam", "homework", "lecture", "assignment", "research", "quiz", "math", "science", "english", "lessons", "practice")
        val healthKeywords = listOf("gym", "health", "doctor", "run", "workout", "cardio", "yoga", "meditation", "hospital", "fitness", "nap", "sleep", "walk", "cycling", "bath", "rest")
        val urgentKeywords = listOf("urgent", "priority", "deadline", "asap", "important", "critical")
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