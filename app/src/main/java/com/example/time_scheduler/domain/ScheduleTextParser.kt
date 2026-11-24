package com.example.time_scheduler.domain

import com.example.time_scheduler.data.Category
import com.example.time_scheduler.data.TaskItem
import com.google.mlkit.vision.text.Text
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

class ScheduleTextParser {

    private val headerNoise = setOf(
        "time", "start", "end", "start time", "end time",
        "activity", "task", "task title", "title",
        "note", "notes", "description", "category",
        "daily schedule", "schedule", "timetable", "hours", "subject", "room", "day", "date"
    )

    private val categoryColumnLabels = mapOf(
        "work" to Category.WORK,
        "study" to Category.STUDY,
        "health" to Category.HEALTH,
        "urgent" to Category.URGENT,
        "personal" to Category.PERSONAL,
        "other" to Category.OTHER
    )

    private val strictTimeRangeRegex = Regex(
        """(?i)\b(\d{1,2}:\d{2}\s*(?:AM|PM)?|\d{1,2}\s*(?:AM|PM))\s*(?:[-–—~]|to|till|until)\s*(\d{1,2}(?::\d{2})?\s*(?:AM|PM)?)\b"""
    )

    private val strictSingleTimeRegex = Regex(
        """(?i)\b(\d{1,2}:\d{2}\s*(?:AM|PM)?|\d{1,2}\s*(?:AM|PM))\b"""
    )

    private data class SpatialBox(
        val text: String,
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int
    ) {
        val centerY: Int get() = (top + bottom) / 2
        val height: Int get() = max(1, bottom - top)
    }

    /**
     * Row-First Spatial Table & Schedule Parser.
     * Groups ML Kit bounding boxes into horizontal visual rows so both:
     *  - Single-column time ranges ("06:30 AM - 07:15 AM")
     *  - Dual-column start/end times ("06:30 AM" | "07:15 AM")
     *  - Multi-line wrapped titles and descriptions
     * are extracted accurately regardless of image resolution.
     */
    fun parseVisionTextHierarchy(
        visionText: Text,
        targetDate: LocalDate = LocalDate.now()
    ): List<TaskItem> {
        val allLines = visionText.textBlocks.flatMap { it.lines }
        if (allLines.isEmpty()) {
            return parseScannedText(visionText.text, targetDate)
        }

        // 1. Filter low-confidence noise, symbol-only lines, and table headers
        val validBoxes = allLines.mapNotNull { line ->
            val raw = line.text.trim()
            val box = line.boundingBox ?: return@mapNotNull null
            val confidenceOk = (line.confidence ?: 1.0f) >= 0.45f
            val alnumCount = raw.count { it.isLetterOrDigit() }
            val normalizedHeader = raw.lowercase(Locale.ROOT)
                .replace(Regex("""[^a-z\s]"""), "")
                .replace(Regex("""\s+"""), " ")
                .trim()

            if (confidenceOk &&
                raw.length >= 2 &&
                alnumCount >= (raw.length * 0.45) &&
                !headerNoise.contains(normalizedHeader)
            ) {
                SpatialBox(raw, box.left, box.top, box.right, box.bottom)
            } else {
                null
            }
        }

        if (validBoxes.isEmpty()) return emptyList()

        // 2. Cluster boxes into horizontal visual rows using median text height
        val sortedHeights = validBoxes.map { it.height }.sorted()
        val medianHeight = sortedHeights[sortedHeights.size / 2].coerceAtLeast(16)
        val rowYTolerance = (medianHeight * 0.65).toInt().coerceAtLeast(12)

        val sortedByY = validBoxes.sortedBy { it.centerY }
        val visualRows = mutableListOf<MutableList<SpatialBox>>()

        for (box in sortedByY) {
            val matchingRow = visualRows.lastOrNull { row ->
                val rowAvgY = row.map { it.centerY }.average()
                abs(box.centerY - rowAvgY) <= rowYTolerance
            }
            if (matchingRow != null) {
                matchingRow.add(box)
            } else {
                visualRows.add(mutableListOf(box))
            }
        }

        // Sort boxes left-to-right within each row and merge closely adjacent fragments in the same cell
        val mergedRows: List<List<SpatialBox>> = visualRows.map { row ->
            val leftToRight = row.sortedBy { it.left }
            val cells = mutableListOf<SpatialBox>()
            for (box in leftToRight) {
                val prev = cells.lastOrNull()
                val gap = if (prev != null) box.left - prev.right else Int.MAX_VALUE
                // Merge only if gap is smaller than ~1.1x line height (same table cell)
                if (prev != null && gap < (medianHeight * 1.1).toInt()) {
                    cells[cells.lastIndex] = SpatialBox(
                        text = "${prev.text} ${box.text}",
                        left = prev.left,
                        top = minOf(prev.top, box.top),
                        right = maxOf(prev.right, box.right),
                        bottom = maxOf(prev.bottom, box.bottom)
                    )
                } else {
                    cells.add(box)
                }
            }
            cells
        }

        // 3. Parse each visual row into tasks (attaching wrapped continuation rows to the active task)
        data class MutableTaskDraft(
            var startTime: LocalTime,
            var endTime: LocalTime,
            var title: String,
            var description: String,
            var explicitCategory: Category?,
            var titleLeftX: Int
        )

        val drafts = mutableListOf<MutableTaskDraft>()
        var lastTrackedTime: LocalTime? = null

        for (rowCells in mergedRows) {
            var rowStartStr: String? = null
            var rowEndStr: String? = null
            val singleTimesInRow = mutableListOf<String>()
            val textCells = mutableListOf<SpatialBox>()

            for (cell in rowCells) {
                val sanitized = sanitizeOcrTimeText(cell.text)
                val rangeMatch = strictTimeRangeRegex.find(sanitized)

                if (rangeMatch != null && rowStartStr == null) {
                    rowStartStr = rangeMatch.groupValues[1].trim()
                    rowEndStr = rangeMatch.groupValues[2].trim()

                    val leftover = cleanExtractedText(sanitized.removeRange(rangeMatch.range))
                    if (leftover.length >= 2) {
                        textCells.add(cell.copy(text = leftover))
                    }
                } else {
                    val singleMatches = strictSingleTimeRegex.findAll(sanitized).toList()
                    // Treat cell as a pure timestamp cell if the time token covers most of the cell text
                    if (singleMatches.isNotEmpty() && sanitized.length <= 14) {
                        singleMatches.forEach { singleTimesInRow.add(it.groupValues[1].trim()) }
                    } else if (singleMatches.isNotEmpty() && textCells.isEmpty() && rowStartStr == null && singleTimesInRow.isEmpty()) {
                        // Leading inline time + title in a single cell (e.g., "08:00 AM Team Sync")
                        val firstMatch = singleMatches.first()
                        singleTimesInRow.add(firstMatch.groupValues[1].trim())
                        val leftover = cleanExtractedText(sanitized.removeRange(firstMatch.range))
                        if (leftover.length >= 2) {
                            textCells.add(cell.copy(text = leftover))
                        }
                    } else {
                        val cleaned = cleanExtractedText(cell.text)
                        if (cleaned.length >= 2) {
                            textCells.add(cell.copy(text = cleaned))
                        }
                    }
                }
            }

            // Resolve Start & End time for this row if any timestamp was detected
            val hasRowTimestamp = rowStartStr != null || singleTimesInRow.isNotEmpty()

            if (hasRowTimestamp) {
                val resolvedStartStr = rowStartStr ?: singleTimesInRow[0]
                val resolvedEndStr = rowEndStr ?: singleTimesInRow.getOrNull(1)

                val startTime = parseTimeWithChronology(resolvedStartStr, lastTrackedTime)
                if (startTime != null) {
                    val endTime = if (resolvedEndStr != null) {
                        parseTimeWithChronology(resolvedEndStr, startTime) ?: startTime.plusHours(1)
                    } else {
                        startTime.plusHours(1)
                    }
                    lastTrackedTime = endTime

                    // Check if the rightmost cell is an explicit Category column ("Work", "Study", etc.)
                    var explicitCat: Category? = null
                    val contentCells = textCells.toMutableList()
                    if (contentCells.size >= 2) {
                        val lastKey = contentCells.last().text.lowercase(Locale.ROOT)
                        if (categoryColumnLabels.containsKey(lastKey)) {
                            explicitCat = categoryColumnLabels[lastKey]
                            contentCells.removeAt(contentCells.lastIndex)
                        }
                    }

                    val titleCell = contentCells.firstOrNull()
                    val title = titleCell?.text ?: ""
                    val description = if (contentCells.size > 1) {
                        contentCells.drop(1).joinToString(" ") { it.text }
                    } else {
                        ""
                    }

                    drafts.add(
                        MutableTaskDraft(
                            startTime = startTime,
                            endTime = endTime,
                            title = title,
                            description = description,
                            explicitCategory = explicitCat,
                            titleLeftX = titleCell?.left ?: 0
                        )
                    )
                }
            } else if (drafts.isNotEmpty() && textCells.isNotEmpty()) {
                // Continuation row (wrapped title or description lines directly below the previous task row)
                val currentDraft = drafts.last()
                val contentCells = textCells.toMutableList()

                if (contentCells.size >= 2) {
                    val lastKey = contentCells.last().text.lowercase(Locale.ROOT)
                    if (categoryColumnLabels.containsKey(lastKey) && currentDraft.explicitCategory == null) {
                        currentDraft.explicitCategory = categoryColumnLabels[lastKey]
                        contentCells.removeAt(contentCells.lastIndex)
                    }
                }

                if (currentDraft.title.isBlank()) {
                    currentDraft.title = contentCells[0].text
                    currentDraft.titleLeftX = contentCells[0].left
                    if (contentCells.size > 1) {
                        val extraDesc = contentCells.drop(1).joinToString(" ") { it.text }
                        currentDraft.description = listOf(currentDraft.description, extraDesc)
                            .filter { it.isNotBlank() }
                            .joinToString(" ")
                    }
                } else if (contentCells.size >= 2) {
                    currentDraft.title = "${currentDraft.title} ${contentCells[0].text}".trim()
                    val extraDesc = contentCells.drop(1).joinToString(" ") { it.text }
                    currentDraft.description = listOf(currentDraft.description, extraDesc)
                        .filter { it.isNotBlank() }
                        .joinToString(" ")
                } else {
                    val cell = contentCells[0]
                    // If aligned near the title column and description hasn't started, append to title; else description
                    if (abs(cell.left - currentDraft.titleLeftX) <= medianHeight * 2 && currentDraft.description.isBlank()) {
                        currentDraft.title = "${currentDraft.title} ${cell.text}".trim()
                    } else {
                        currentDraft.description = listOf(currentDraft.description, cell.text)
                            .filter { it.isNotBlank() }
                            .joinToString(" ")
                    }
                }
            }
        }

        // If spatial row clustering found nothing (e.g., plain single-column text), fall back to line parser
        if (drafts.isEmpty()) {
            return parseScannedText(validBoxes.joinToString("\n") { it.text }, targetDate)
        }

        return drafts
            .filter { it.title.length >= 2 }
            .map { draft ->
                val finalDesc = draft.description.ifBlank { "Imported from scanned schedule" }
                val finalCategory = draft.explicitCategory ?: detectCategory("${draft.title} $finalDesc")
                TaskItem(
                    title = draft.title,
                    description = finalDesc,
                    date = targetDate,
                    startTime = draft.startTime,
                    endTime = draft.endTime,
                    category = finalCategory
                )
            }
            .sortedBy { it.startTime }
    }

    /**
     * Parses raw text lines (used by "Simulate Sample" and non-spatial fallback).
     */
    fun parseScannedText(rawText: String, targetDate: LocalDate = LocalDate.now()): List<TaskItem> {
        val lines = rawText.split("\n")
            .map { it.trim() }
            .filter { line ->
                val alnumCount = line.count { it.isLetterOrDigit() }
                val cleanWord = line.lowercase(Locale.ROOT).replace(Regex("""[^a-z\s]"""), "").trim()
                line.length >= 2 &&
                        alnumCount >= (line.length * 0.45) &&
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
                                category = detectCategory("$taskTitle $description")
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
                            category = detectCategory("$title $description")
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

    private fun sanitizeOcrTimeText(rawText: String): String {
        var text = rawText

        // Fix digit misreads only within time-like patterns
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