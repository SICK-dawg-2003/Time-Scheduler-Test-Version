package com.example.time_scheduler.domain

import com.example.time_scheduler.data.Category
import com.example.time_scheduler.data.TaskItem
import com.google.mlkit.vision.text.Text
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

class ScheduleTextParser {

    private val headerNoise = setOf(
        "time", "start", "end", "start time", "end time", "period", "slot", "duration",
        "activity", "task", "task title", "title", "course", "subject", "code",
        "note", "notes", "description", "category", "room", "instructor",
        "daily schedule", "schedule", "timetable", "class schedule", "hours"
    )

    private val dayHeaderMap = mapOf(
        "mon" to DayOfWeek.MONDAY, "monday" to DayOfWeek.MONDAY,
        "tue" to DayOfWeek.TUESDAY, "tues" to DayOfWeek.TUESDAY, "tuesday" to DayOfWeek.TUESDAY,
        "wed" to DayOfWeek.WEDNESDAY, "wednesday" to DayOfWeek.WEDNESDAY,
        "thu" to DayOfWeek.THURSDAY, "thur" to DayOfWeek.THURSDAY, "thurs" to DayOfWeek.THURSDAY, "thursday" to DayOfWeek.THURSDAY,
        "fri" to DayOfWeek.FRIDAY, "friday" to DayOfWeek.FRIDAY,
        "sat" to DayOfWeek.SATURDAY, "saturday" to DayOfWeek.SATURDAY,
        "sun" to DayOfWeek.SUNDAY, "sunday" to DayOfWeek.SUNDAY
    )

    private val categoryColumnLabels = mapOf(
        "work" to Category.WORK,
        "study" to Category.STUDY,
        "health" to Category.HEALTH,
        "urgent" to Category.URGENT,
        "personal" to Category.PERSONAL,
        "other" to Category.OTHER
    )

    // Matches individual timestamps: "06:30 AM", "7:30", "14:00", "8 AM", "0730" (when paired in time cells)
    private val singleTimeTokenRegex = Regex(
        """(?i)\b(\d{1,2}:\d{2}\s*(?:AM|PM)?|\d{1,2}\s*(?:AM|PM))\b"""
    )

    // Matches explicit ranges like "7:30 - 8:30", "06:30 AM to 07:15 AM", "0730-0830"
    private val rangeTimeRegex = Regex(
        """(?i)\b(\d{1,2}:\d{2}\s*(?:AM|PM)?|\d{1,2}\s*(?:AM|PM)|\d{4})\s*(?:[-–—~_=]|to|till|until)\s*(\d{1,2}(?::\d{2})?\s*(?:AM|PM)?|\d{4})\b"""
    )

    private data class SpatialBox(
        val text: String,
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int
    ) {
        val centerX: Int get() = (left + right) / 2
        val centerY: Int get() = (top + bottom) / 2
        val height: Int get() = max(1, bottom - top)
    }

    private data class DayColumnAnchor(
        val dayOfWeek: DayOfWeek,
        val centerX: Int,
        val left: Int,
        val right: Int
    )

    private data class ExtractedTimeSpan(
        val startStr: String,
        val endStr: String?,
        val leftoverText: String,
        val cellLeftX: Int
    )

    fun parseVisionTextHierarchy(
        visionText: Text,
        targetDate: LocalDate = LocalDate.now()
    ): List<TaskItem> {
        val allLines = visionText.textBlocks.flatMap { it.lines }
        if (allLines.isEmpty()) {
            return parseScannedText(visionText.text, targetDate)
        }

        // 1. Clean vertical table-border artifacts ("|", "I", "l") and filter low-confidence noise
        val rawBoxes = allLines.mapNotNull { line ->
            val box = line.boundingBox ?: return@mapNotNull null
            val stripped = line.text
                .replace(Regex("""^[|lI\[\]]+(?=\s)"""), "")
                .replace(Regex("""(?<=\s)[|lI\[\]]+$"""), "")
                .trim()

            val confidenceOk = (line.confidence ?: 1.0f) >= 0.35f
            val alnumCount = stripped.count { it.isLetterOrDigit() }

            if (confidenceOk && stripped.length >= 2 && alnumCount >= (stripped.length * 0.4)) {
                SpatialBox(stripped, box.left, box.top, box.right, box.bottom)
            } else {
                null
            }
        }

        if (rawBoxes.isEmpty()) return emptyList()

        // 2. Detect if top headers represent a 2D Weekly Timetable (Mon, Tue, Wed...)
        val dayColumns = detectDayHeaderColumns(rawBoxes)

        // Filter out table header labels
        val validBoxes = rawBoxes.filterNot { box ->
            val normalized = box.text.lowercase(Locale.ROOT)
                .replace(Regex("""[^a-z\s]"""), "")
                .replace(Regex("""\s+"""), " ")
                .trim()
            headerNoise.contains(normalized) || dayHeaderMap.containsKey(normalized)
        }

        if (validBoxes.isEmpty()) return emptyList()

        // 3. Cluster boxes into horizontal visual rows
        val sortedHeights = validBoxes.map { it.height }.sorted()
        val medianHeight = sortedHeights[sortedHeights.size / 2].coerceAtLeast(16)
        val rowYTolerance = (medianHeight * 0.65).toInt().coerceAtLeast(12)

        val visualRows = mutableListOf<MutableList<SpatialBox>>()
        for (box in validBoxes.sortedBy { it.centerY }) {
            val matchingRow = visualRows.lastOrNull { row ->
                abs(box.centerY - row.map { it.centerY }.average()) <= rowYTolerance
            }
            if (matchingRow != null) {
                matchingRow.add(box)
            } else {
                visualRows.add(mutableListOf(box))
            }
        }

        // Sort left-to-right and merge closely adjacent fragments within the same cell
        val mergedRows: List<List<SpatialBox>> = visualRows.map { row ->
            val leftToRight = row.sortedBy { it.left }
            val cells = mutableListOf<SpatialBox>()
            for (box in leftToRight) {
                val prev = cells.lastOrNull()
                val gap = if (prev != null) box.left - prev.right else Int.MAX_VALUE
                if (prev != null && gap < (medianHeight * 0.9).toInt()) {
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

        if (dayColumns.size >= 2) {
            val matrixTasks = parseWeeklyMatrixRows(mergedRows, dayColumns, targetDate, medianHeight)
            if (matrixTasks.isNotEmpty()) {
                return matrixTasks
            }
        }

        return parseStandardTableRows(mergedRows, targetDate, medianHeight)
    }

    /**
     * Extracts Start Time, optional End Time, and remaining non-time text from a single cell.
     * Handles:
     *  - "06:30 AM - 07:15 AM"
     *  - "06:30 AM 07:15 AM" (when dash is missing)
     *  - "06:30 AM" (single time cell)
     *  - "08:00 AM Team Meeting" (inline time + title)
     */
    private fun extractTimesFromCell(cell: SpatialBox): ExtractedTimeSpan? {
        val sanitized = sanitizeOcrTimeText(cell.text)

        // 1. Check explicit range ("start - end")
        val rangeMatch = rangeTimeRegex.find(sanitized)
        if (rangeMatch != null) {
            val sStr = normalizeCompactMilitary(rangeMatch.groupValues[1].trim())
            val eStr = normalizeCompactMilitary(rangeMatch.groupValues[2].trim())
            val leftover = cleanExtractedText(sanitized.removeRange(rangeMatch.range))
            return ExtractedTimeSpan(sStr, eStr, leftover, cell.left)
        }

        // 2. Check all individual time tokens in the cell
        val matches = singleTimeTokenRegex.findAll(sanitized).toList()
        if (matches.isEmpty()) return null

        val first = matches[0]
        val second = matches.getOrNull(1)

        // Remove matched time ranges from text to see if non-time title text remains
        val withoutFirst = sanitized.removeRange(first.range)
        val remainingRaw = if (second != null) {
            // Re-find second token on string with first removed
            val secondAfter = singleTimeTokenRegex.find(withoutFirst)
            if (secondAfter != null) withoutFirst.removeRange(secondAfter.range) else withoutFirst
        } else {
            withoutFirst
        }

        val leftover = cleanExtractedText(remainingRaw)

        // Accept if the cell is primarily timestamps OR starts with a timestamp at the beginning of the cell
        val startsWithTime = first.range.first <= 2
        if (leftover.length <= 2 || startsWithTime) {
            return ExtractedTimeSpan(
                startStr = first.groupValues[1].trim(),
                endStr = second?.groupValues?.get(1)?.trim(),
                leftoverText = leftover,
                cellLeftX = cell.left
            )
        }

        return null
    }

    private fun normalizeCompactMilitary(token: String): String {
        if (token.matches(Regex("""\d{4}"""))) {
            return "${token.substring(0, 2)}:${token.substring(2, 4)}"
        }
        return token
    }

    private fun detectDayHeaderColumns(boxes: List<SpatialBox>): List<DayColumnAnchor> {
        val dayBoxes = boxes.mapNotNull { box ->
            val key = box.text.lowercase(Locale.ROOT).replace(Regex("""[^a-z]"""), "")
            val dow = dayHeaderMap[key] ?: return@mapNotNull null
            Pair(dow, box)
        }
        if (dayBoxes.size < 2) return emptyList()

        val bestBand = dayBoxes.map { candidate ->
            dayBoxes.filter { abs(it.second.centerY - candidate.second.centerY) <= candidate.second.height * 1.5 }
        }.maxByOrNull { it.size } ?: return emptyList()

        if (bestBand.size < 2) return emptyList()

        return bestBand
            .distinctBy { it.first }
            .sortedBy { it.second.centerX }
            .map { (dow, box) ->
                DayColumnAnchor(
                    dayOfWeek = dow,
                    centerX = box.centerX,
                    left = box.left,
                    right = box.right
                )
            }
    }

    private fun parseWeeklyMatrixRows(
        mergedRows: List<List<SpatialBox>>,
        dayColumns: List<DayColumnAnchor>,
        targetDate: LocalDate,
        medianHeight: Int
    ): List<TaskItem> {
        val startOfWeek = targetDate.minusDays((targetDate.dayOfWeek.value % 7).toLong())

        fun dateForDayOfWeek(dow: DayOfWeek): LocalDate {
            val offset = dow.value % 7
            return startOfWeek.plusDays(offset.toLong())
        }

        data class MatrixSlotDraft(
            val date: LocalDate,
            var startTime: LocalTime,
            var endTime: LocalTime,
            val lines: MutableList<String>
        )

        val activeRowSlots = mutableMapOf<DayOfWeek, MatrixSlotDraft>()
        val completedDrafts = mutableListOf<MatrixSlotDraft>()
        var currentStartTime: LocalTime? = null
        var currentEndTime: LocalTime? = null
        var awaitingStackedEndTime = false
        var lastTrackedTime: LocalTime? = null

        val firstDayLeftEdge = dayColumns.first().left - (medianHeight * 2)

        for (rowCells in mergedRows) {
            val timeColumnCells = rowCells.filter { it.left < firstDayLeftEdge }
            val subjectCells = rowCells.filter { it.left >= firstDayLeftEdge }
                .mapNotNull { cell ->
                    val cleaned = cleanExtractedText(cell.text)
                    if (cleaned.length >= 2) cell.copy(text = cleaned) else null
                }

            val timeExtractions = timeColumnCells.mapNotNull { extractTimesFromCell(it) }

            if (timeExtractions.isNotEmpty()) {
                val allTokens = timeExtractions.flatMap { listOfNotNull(it.startStr, it.endStr) }

                // Check if this row is just the second line of a stacked time cell (e.g., "- 09:30 AM")
                if (awaitingStackedEndTime && allTokens.size == 1 && subjectCells.isEmpty() && currentStartTime != null) {
                    val resolvedEnd = parseTimeWithChronology(allTokens[0], currentStartTime)
                    if (resolvedEnd != null && resolvedEnd.isAfter(currentStartTime)) {
                        currentEndTime = resolvedEnd
                        lastTrackedTime = resolvedEnd
                        activeRowSlots.values.forEach { it.endTime = resolvedEnd }
                        awaitingStackedEndTime = false
                        continue
                    }
                }

                val startStr = allTokens[0]
                val endStr = allTokens.getOrNull(1)
                val (sTime, eTime) = resolveStartAndEndTimes(startStr, endStr, lastTrackedTime)

                if (sTime != null) {
                    completedDrafts.addAll(activeRowSlots.values)
                    activeRowSlots.clear()

                    currentStartTime = sTime
                    currentEndTime = eTime
                    lastTrackedTime = eTime
                    awaitingStackedEndTime = (endStr == null)
                }
            } else if (subjectCells.isNotEmpty()) {
                awaitingStackedEndTime = false
            }

            val slotStart = currentStartTime ?: continue
            val slotEnd = currentEndTime ?: slotStart.plusHours(1)

            for (cell in subjectCells) {
                val nearestDay = dayColumns.minByOrNull { abs(cell.centerX - it.centerX) } ?: continue
                val existing = activeRowSlots[nearestDay.dayOfWeek]
                if (existing != null) {
                    existing.lines.add(cell.text)
                } else {
                    activeRowSlots[nearestDay.dayOfWeek] = MatrixSlotDraft(
                        date = dateForDayOfWeek(nearestDay.dayOfWeek),
                        startTime = slotStart,
                        endTime = slotEnd,
                        lines = mutableListOf(cell.text)
                    )
                }
            }
        }

        completedDrafts.addAll(activeRowSlots.values)

        return completedDrafts
            .filter { it.lines.isNotEmpty() }
            .map { draft ->
                val title = draft.lines.first()
                val description = if (draft.lines.size > 1) {
                    draft.lines.drop(1).joinToString(" • ")
                } else {
                    "Imported from timetable"
                }
                TaskItem(
                    title = title,
                    description = description,
                    date = draft.date,
                    startTime = draft.startTime,
                    endTime = draft.endTime,
                    category = detectCategory("$title $description")
                )
            }
            .sortedWith(compareBy({ it.date }, { it.startTime }))
    }

    private fun parseStandardTableRows(
        mergedRows: List<List<SpatialBox>>,
        targetDate: LocalDate,
        medianHeight: Int
    ): List<TaskItem> {
        data class RowDraft(
            var startTime: LocalTime,
            var endTime: LocalTime,
            var hasExplicitEndTime: Boolean,
            var timeColLeftX: Int,
            var title: String,
            var description: String,
            var explicitCategory: Category?,
            var titleCenterX: Int,
            var descCenterX: Int?
        )

        val drafts = mutableListOf<RowDraft>()
        var lastTrackedTime: LocalTime? = null

        for (rowCells in mergedRows) {
            val timeExtractions = mutableListOf<ExtractedTimeSpan>()
            val textCells = mutableListOf<SpatialBox>()

            for (cell in rowCells) {
                val extracted = extractTimesFromCell(cell)
                if (extracted != null) {
                    timeExtractions.add(extracted)
                    if (extracted.leftoverText.length >= 2) {
                        textCells.add(cell.copy(text = extracted.leftoverText))
                    }
                } else {
                    val cleaned = cleanExtractedText(cell.text)
                    if (cleaned.length >= 2) {
                        textCells.add(cell.copy(text = cleaned))
                    }
                }
            }

            if (timeExtractions.isNotEmpty()) {
                val allTokens = timeExtractions.flatMap { listOfNotNull(it.startStr, it.endStr) }
                val firstTimeCellLeft = timeExtractions.first().cellLeftX

                // Check if this row is the second line of a stacked Start/End time cell
                // (e.g., Row 1 had "08:00 AM -" + Title, Row 2 has only "09:30 AM" in the same time column)
                val prevDraft = drafts.lastOrNull()
                if (prevDraft != null &&
                    !prevDraft.hasExplicitEndTime &&
                    allTokens.size == 1 &&
                    textCells.isEmpty() &&
                    abs(firstTimeCellLeft - prevDraft.timeColLeftX) <= medianHeight * 3
                ) {
                    val (_, resolvedEnd) = resolveStartAndEndTimes(
                        startStr = prevDraft.startTime.format(DateTimeFormatter.ofPattern("HH:mm")),
                        endStr = allTokens[0],
                        lastTime = prevDraft.startTime
                    )
                    if (resolvedEnd.isAfter(prevDraft.startTime)) {
                        prevDraft.endTime = resolvedEnd
                        prevDraft.hasExplicitEndTime = true
                        lastTrackedTime = resolvedEnd
                        continue
                    }
                }

                val startStr = allTokens[0]
                val endStr = allTokens.getOrNull(1)
                val (startTime, endTime) = resolveStartAndEndTimes(startStr, endStr, lastTrackedTime)

                if (startTime != null) {
                    lastTrackedTime = endTime

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
                    val descCells = contentCells.drop(1)

                    drafts.add(
                        RowDraft(
                            startTime = startTime,
                            endTime = endTime,
                            hasExplicitEndTime = (endStr != null),
                            timeColLeftX = firstTimeCellLeft,
                            title = titleCell?.text ?: "",
                            description = descCells.joinToString(" ") { it.text },
                            explicitCategory = explicitCat,
                            titleCenterX = titleCell?.centerX ?: 0,
                            descCenterX = descCells.firstOrNull()?.centerX
                        )
                    )
                }
            } else if (drafts.isNotEmpty() && textCells.isNotEmpty()) {
                val current = drafts.last()
                for (cell in textCells) {
                    val catMatch = categoryColumnLabels[cell.text.lowercase(Locale.ROOT)]
                    if (catMatch != null && current.explicitCategory == null) {
                        current.explicitCategory = catMatch
                        continue
                    }

                    if (current.title.isBlank()) {
                        current.title = cell.text
                        current.titleCenterX = cell.centerX
                    } else {
                        val distToTitle = abs(cell.centerX - current.titleCenterX)
                        val distToDesc = current.descCenterX?.let { abs(cell.centerX - it) } ?: Int.MAX_VALUE

                        if (distToTitle < distToDesc && distToTitle <= medianHeight * 4) {
                            current.title = "${current.title} ${cell.text}".trim()
                        } else {
                            if (current.descCenterX == null) current.descCenterX = cell.centerX
                            current.description = listOf(current.description, cell.text)
                                .filter { it.isNotBlank() }
                                .joinToString(" ")
                        }
                    }
                }
            }
        }

        if (drafts.isEmpty()) {
            return parseScannedText(mergedRows.flatten().joinToString("\n") { it.text }, targetDate)
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
     * Resolves Start and End LocalTimes together, sharing AM/PM context across both endpoints
     * (e.g., "1:00 - 2:30 PM" -> 13:00 to 14:30; "11:00 - 1:00 PM" -> 11:00 to 13:00).
     */
    private fun resolveStartAndEndTimes(
        startStr: String,
        endStr: String?,
        lastTime: LocalTime?
    ): Pair<LocalTime?, LocalTime> {
        val startUpper = startStr.trim().uppercase(Locale.ROOT)
        val endUpper = endStr?.trim()?.uppercase(Locale.ROOT)

        var startTime = parseTimeWithChronology(startUpper, lastTime) ?: return Pair(null, LocalTime.of(10, 0))

        if (endUpper == null) {
            return Pair(startTime, startTime.plusHours(1))
        }

        var endTime = parseTimeWithChronology(endUpper, startTime) ?: startTime.plusHours(1)

        // If end time explicitly says PM, start time has no AM/PM, and start is 1..11:
        // e.g., "1:00 - 2:30 PM" -> start should be 13:00 (1:00 PM), whereas "11:00 - 1:00 PM" stays 11:00 AM
        val startHasMeridiem = startUpper.contains("AM") || startUpper.contains("PM")
        val endHasPm = endUpper.contains("PM")
        if (!startHasMeridiem && endHasPm && startTime.hour in 1..11) {
            val candidatePmStart = startTime.plusHours(12)
            if (!candidatePmStart.isAfter(endTime)) {
                startTime = candidatePmStart
                endTime = parseTimeWithChronology(endUpper, startTime) ?: endTime
            }
        }

        return Pair(startTime, endTime)
    }

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
                val timeColMatch = rangeTimeRegex.find(columns[0])
                if (timeColMatch != null) {
                    val startStr = normalizeCompactMilitary(timeColMatch.groupValues[1].trim())
                    val endStr = normalizeCompactMilitary(timeColMatch.groupValues[2].trim())
                    val taskTitle = cleanExtractedText(columns[1])
                    val description = if (columns.size >= 3) cleanExtractedText(columns[2]) else "Imported from scanned schedule"

                    val (startTime, endTime) = resolveStartAndEndTimes(startStr, endStr, lastTrackedTime)
                    if (startTime != null && taskTitle.length >= 2) {
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

            val rangeMatch = rangeTimeRegex.find(line)
            if (rangeMatch != null) {
                val startStr = normalizeCompactMilitary(rangeMatch.groupValues[1].trim())
                val endStr = normalizeCompactMilitary(rangeMatch.groupValues[2].trim())

                var title = cleanExtractedText(line.removeRange(rangeMatch.range))
                var description = "Imported from scanned schedule"

                if (title.isBlank() && i + 1 < lines.size && !rangeTimeRegex.containsMatchIn(sanitizeOcrTimeText(lines[i + 1]))) {
                    title = cleanExtractedText(lines[i + 1])
                    if (i + 2 < lines.size && !rangeTimeRegex.containsMatchIn(sanitizeOcrTimeText(lines[i + 2]))) {
                        description = cleanExtractedText(lines[i + 2])
                        i += 2
                    } else {
                        i++
                    }
                } else if (i + 1 < lines.size && !rangeTimeRegex.containsMatchIn(sanitizeOcrTimeText(lines[i + 1]))) {
                    description = cleanExtractedText(lines[i + 1])
                    i++
                }

                val (startTime, endTime) = resolveStartAndEndTimes(startStr, endStr, lastTrackedTime)
                if (startTime != null && title.length >= 2) {
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
                val singleMatch = singleTimeTokenRegex.find(line)
                if (singleMatch != null) {
                    val timeStr = singleMatch.groupValues[1].trim()
                    var title = cleanExtractedText(line.removeRange(singleMatch.range))

                    if (title.isBlank() && i + 1 < lines.size && !singleTimeTokenRegex.containsMatchIn(sanitizeOcrTimeText(lines[i + 1]))) {
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
     * Fixes OCR digit and separator corruptions inside timestamp patterns:
     * - "I0:00" / "l0:00" -> "10:00"
     * - "O8:30" / "8:OO" -> "08:30" / "8:00"
     * - "B:00" -> "8:00", "S:30" -> "5:30"
     * - "7.00" / "7;00" / "7,00" -> "7:00"
     */
    private fun sanitizeOcrTimeText(rawText: String): String {
        var text = rawText

        text = text
            .replace(Regex("""\b[Il|](\d:\d{2})"""), "1$1")
            .replace(Regex("""\b[Il|]:(\d{2})"""), "1:$1")
            .replace(Regex("""\b[Oo](\d:\d{2})"""), "0$1")
            .replace(Regex("""\bB:(\d{2})"""), "8:$1")
            .replace(Regex("""\bS:(\d{2})"""), "5:$1")
            .replace(Regex("""(\d{1,2}:)[Oo]{2}\b"""), "${1}00")
            .replace(Regex("""(\d{1,2}:)[Oo](\d)\b"""), "${1}0$2")
            .replace(Regex("""(\d{1,2}:\d)[Oo]\b"""), "${1}0")

        // Normalize decimal/semicolon/comma time separators ("7.00" -> "7:00")
        text = text.replace(Regex("""\b(\d{1,2})[.;,](\d{2})\b"""), "$1:$2")

        // Format compact AM/PM times ("10am" -> "10:00 AM", "9pm" -> "9:00 PM")
        text = text.replace(Regex("""\b(\d{1,2})\s*(am|AM)\b"""), "$1:00 AM")
            .replace(Regex("""\b(\d{1,2})\s*(pm|PM)\b"""), "$1:00 PM")

        // Normalize range separators
        text = text.replace(Regex("""\s*(?:[-–—~_=]|(?:\bto\b|\btill\b|\buntil\b))\s*""", RegexOption.IGNORE_CASE), " - ")

        return text.replace(Regex("""\s+"""), " ").trim()
    }

    private fun parseTimeWithChronology(
        timeString: String,
        lastTime: LocalTime?
    ): LocalTime? {
        val clean = normalizeCompactMilitary(timeString.trim().uppercase(Locale.ROOT))

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
        val studyKeywords = listOf("study", "read", "books", "class", "exam", "homework", "lecture", "assignment", "research", "quiz", "math", "science", "english", "lessons", "practice", "lab", "it", "cs")
        val healthKeywords = listOf("gym", "health", "doctor", "run", "workout", "cardio", "yoga", "meditation", "hospital", "fitness", "nap", "sleep", "walk", "cycling", "bath", "rest", "pe")
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