package com.timetable.wear.data.local

import android.content.Context
import com.timetable.wear.data.remote.TimetableFetcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads the timetable files bundled in `assets/` (synced from the repo at build time)
 * and parses them into a [CachedTimetableSnapshot].
 *
 * Used as an offline fallback on first launch when no DataStore snapshot exists yet,
 * so the app shows the latest packaged data instead of the stale compiled-in defaults.
 */
fun interface BundledTimetableSource {
    suspend fun loadBundled(): CachedTimetableSnapshot?
}

@Singleton
class AssetBundledTimetableSource @Inject constructor(
    @ApplicationContext private val context: Context
) : BundledTimetableSource {

    override suspend fun loadBundled(): CachedTimetableSnapshot? = withContext(Dispatchers.IO) {
        runCatching {
            val fetcher = TimetableFetcher()
            val dayRotation = fetcher.parseDayRotation(readAsset("days.txt"))
            val special = fetcher.parseSpecialDates(readAsset("special-date.txt"))
            val timetable = fetcher.parseTimetable(readAsset("timetable.txt"))
            val schedules = buildMap {
                timetable.normalSchedule?.let { put("normal", it) }
                putAll(special.timetables)
            }
            CachedTimetableSnapshot.from(
                urlBase = "",
                dayRotation = dayRotation,
                specialDates = special.specialDates,
                subjectSchedule = timetable.subjectSchedule,
                timetables = schedules
            ).takeIf { it.isValid() }
        }.getOrNull()
    }

    private fun readAsset(name: String): String =
        context.assets.open(name).bufferedReader().use { it.readText() }
}
