package com.timetable.wear.data.local

import com.timetable.wear.data.remote.TimetableFetcher
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the offline fallback: the data files bundled in `assets/` (synced
 * from the repo at build time) must parse into a valid snapshot, otherwise a
 * first launch with no cache and no network shows stale compiled-in defaults.
 */
class BundledAssetsTest {

    @Test
    fun `bundled assets parse into a valid snapshot`() {
        val fetcher = TimetableFetcher()
        val dayRotation = fetcher.parseDayRotation(readAsset("days.txt"))
        val special = fetcher.parseSpecialDates(readAsset("special-date.txt"))
        val timetable = fetcher.parseTimetable(readAsset("timetable.txt"))

        val snapshot = CachedTimetableSnapshot.from(
            urlBase = "",
            dayRotation = dayRotation,
            specialDates = special.specialDates,
            subjectSchedule = timetable.subjectSchedule,
            timetables = buildMap {
                timetable.normalSchedule?.let { put("normal", it) }
                putAll(special.timetables)
            }
        )

        assertTrue(
            "bundled days.txt/special-date.txt/timetable.txt must form a valid snapshot",
            snapshot.isValid()
        )
    }

    private fun readAsset(name: String): String {
        // Unit tests run with the module directory as working dir.
        val file = File("src/main/assets/$name")
        assertTrue("missing bundled asset: ${file.path}", file.isFile)
        return file.readText()
    }
}
