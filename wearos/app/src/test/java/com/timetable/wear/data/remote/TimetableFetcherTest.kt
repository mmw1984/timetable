package com.timetable.wear.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

class TimetableFetcherTest {

    @Test
    fun `parses normal timetable subjects and afternoon times`() {
        val result = TimetableFetcher().parseTimetable(
            """
            | Period | Day 1 | Day 2 |
            | **8:15 - 8:40** | **Pre-School Assembly** |
            | **8:40 - 9:20** | MATH 101 | ENG 102 |
            | **11:40 - 12:50** | **Lunch** |
            | **1:15 - 1:45** | ICT 201 | CS 202 |
            """.trimIndent()
        )

        assertNotNull(result.normalSchedule)
        assertEquals("13:15", result.normalSchedule?.periods?.get(1)?.start)
        assertEquals("MATH 101", result.subjectSchedule[1]?.get(1))
        assertEquals("CS 202", result.subjectSchedule[2]?.get(2))
    }

    @Test
    fun `parses special timetable ranges and skips weekend dates`() {
        val result = TimetableFetcher().parseSpecialDates(
            """
            ### Special Timetable A
            | Period / Time | Time start | Time end | Duration (min) |
            | --- | --- | --- | --- |
            | 1st | 8:30 | 9:00 | 30 |
            | Recess | 9:00 | 9:15 | 15 |
            ### Dates and Events
            | Dates | Events | Special Timetable |
            | --- | --- | --- |
            | 03 - 10/09/2026 | Orientation | A |
            | 12/09/2026 | Weekend | A |
            """.trimIndent()
        )

        assertEquals("A", result.specialDates["2026-09-03"])
        assertEquals("A", result.specialDates["2026-09-10"])
        assertFalse(result.specialDates.containsKey("2026-09-05"))
        assertFalse(result.specialDates.containsKey("2026-09-12"))
        assertEquals("08:30", result.timetables["specialA"]?.periods?.single()?.start)
    }
}
