package com.example.time_scheduler

import com.example.time_scheduler.data.Category
import com.example.time_scheduler.data.TaskItem
import com.example.time_scheduler.domain.ScheduleConflictResolver
import com.example.time_scheduler.domain.ScheduleTextParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class ScheduleDomainTest {

    @Test
    fun testScheduleTextParser_ExtractsTasksFromOCRText() {
        val parser = ScheduleTextParser()
        val rawOcrText = """
            09:00 AM - 10:30 AM Work Team Planning
            02:00 PM - 03:00 PM Gym Session
        """.trimIndent()

        val tasks = parser.parseScannedText(rawOcrText, LocalDate.now())

        assertEquals(2, tasks.size)
        assertEquals("Work Team Planning", tasks[0].title)
        assertEquals(Category.WORK, tasks[0].category)
        assertEquals(LocalTime.of(9, 0), tasks[0].startTime)
        assertEquals(LocalTime.of(10, 30), tasks[0].endTime)

        assertEquals("Gym Session", tasks[1].title)
        assertEquals(Category.HEALTH, tasks[1].category)
    }

    @Test
    fun testScheduleConflictResolver_DetectsOverlaps() {
        val resolver = ScheduleConflictResolver()
        val today = LocalDate.now()

        val task1 = TaskItem(
            title = "Task 1",
            date = today,
            startTime = LocalTime.of(9, 0),
            endTime = LocalTime.of(10, 30),
            category = Category.WORK
        )

        val task2 = TaskItem(
            title = "Task 2",
            date = today,
            startTime = LocalTime.of(10, 0),
            endTime = LocalTime.of(11, 0),
            category = Category.STUDY
        )

        val conflicts = resolver.findConflicts(listOf(task1, task2))

        assertEquals(1, conflicts.size)
        assertEquals(30L, conflicts[0].overlapMinutes)
        assertTrue(conflicts[0].description.contains("Overlapping time discrepancy"))
    }
}
