package com.timetable.wear.engine

import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimetableLogicTest {

    private val dates = TimetableLogic.availableSchoolDates(
        dayRotation = mapOf("2026-09-03" to 1, "2026-09-04" to 2, "2026-09-07" to 3),
        specialDates = mapOf("2026-09-11" to "B")
    )

    @Test
    fun `selects next school day before a term and last school day after it`() {
        assertEquals(LocalDate.of(2026, 9, 3), TimetableLogic.preferredSchoolDate(LocalDate.of(2026, 8, 17), dates))
        assertEquals(LocalDate.of(2026, 9, 11), TimetableLogic.preferredSchoolDate(LocalDate.of(2026, 10, 1), dates))
    }

    @Test
    fun `navigates only to valid school days and stops at range boundaries`() {
        assertEquals(
            LocalDate.of(2026, 9, 7),
            TimetableLogic.adjacentSchoolDate(LocalDate.of(2026, 9, 4), dates, previous = false)
        )
        assertEquals(
            LocalDate.of(2026, 9, 4),
            TimetableLogic.adjacentSchoolDate(LocalDate.of(2026, 9, 7), dates, previous = true)
        )
        assertNull(TimetableLogic.adjacentSchoolDate(LocalDate.of(2026, 9, 3), dates, previous = true))
        assertNull(TimetableLogic.adjacentSchoolDate(LocalDate.of(2026, 9, 11), dates, previous = false))
    }

    @Test
    fun `uses an end-exclusive interval and exact seconds for countdown`() {
        assertFalse(TimetableLogic.isInRange(LocalTime.of(9, 20), "08:40", "09:20"))
        assertTrue(TimetableLogic.isInRange(LocalTime.of(9, 20), "09:20", "10:00"))
        assertEquals(1, TimetableLogic.secondsRemaining(LocalTime.of(9, 19, 59), "09:20"))
        assertEquals(0, TimetableLogic.secondsRemaining(LocalTime.of(9, 20), "09:20"))
    }
}
