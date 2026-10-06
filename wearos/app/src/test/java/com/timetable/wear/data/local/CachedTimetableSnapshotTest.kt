package com.timetable.wear.data.local

import com.timetable.wear.data.model.TimetableData
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CachedTimetableSnapshotTest {

    @Test
    fun `snapshot round trips through one serialized payload`() {
        val snapshot = CachedTimetableSnapshot.from(
            urlBase = "https://example.com/",
            dayRotation = mapOf("2026-09-03" to 1),
            specialDates = emptyMap(),
            subjectSchedule = TimetableData.subjectSchedule,
            timetables = mapOf("normal" to TimetableData.normal)
        )
        val json = Json { ignoreUnknownKeys = true }

        val restored = json.decodeFromString<CachedTimetableSnapshot>(json.encodeToString(snapshot))

        assertEquals(snapshot, restored)
        assertTrue(restored.isValid())
        assertEquals(TimetableData.subjectSchedule, restored.subjectScheduleAsInts())
    }

    @Test
    fun `incomplete snapshot is never considered valid`() {
        val incomplete = CachedTimetableSnapshot(
            urlBase = "https://example.com/",
            dayRotation = emptyMap(),
            specialDates = emptyMap(),
            subjectSchedule = emptyMap(),
            timetables = emptyMap()
        )

        assertFalse(incomplete.isValid())
    }
}
