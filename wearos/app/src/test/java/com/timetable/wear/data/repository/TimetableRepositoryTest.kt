package com.timetable.wear.data.repository

import com.timetable.wear.data.local.CachedTimetableSnapshot
import com.timetable.wear.data.local.TimetableCacheStore
import com.timetable.wear.data.model.TimetableData
import com.timetable.wear.data.remote.SpecialDatesParseResult
import com.timetable.wear.data.remote.TimetableParseResult
import com.timetable.wear.data.remote.TimetableRemoteSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimetableRepositoryTest {

    @Test
    fun `partial remote failure preserves the last known good snapshot`() = runTest {
        val cache = MemoryCache(existingSnapshot())
        val repository = TimetableRepository(
            fetcher = FakeRemoteSource(dayRotation = Result.failure(IllegalStateException("offline"))),
            cache = cache,
            complicationRefreshRequester = NoOpComplicationRefreshRequester
        )

        repository.initialize()
        val before = repository.data.value
        val result = repository.refresh()

        assertTrue(result.isFailure)
        assertEquals(before, repository.data.value)
        assertEquals(0, cache.saveCount)
    }

    @Test
    fun `complete remote update writes one normalized snapshot`() = runTest {
        val cache = MemoryCache()
        val repository = TimetableRepository(
            fetcher = FakeRemoteSource(),
            cache = cache,
            complicationRefreshRequester = NoOpComplicationRefreshRequester
        )

        val result = repository.refresh("https://example.com/timetable")

        assertTrue(result.isSuccess)
        assertEquals(1, cache.saveCount)
        assertEquals("https://example.com/timetable/", cache.snapshot?.urlBase)
        assertTrue(cache.snapshot?.isValid() == true)
        assertEquals("https://example.com/timetable/", repository.urlConfig.value.base)
    }

    @Test
    fun `invalid url is rejected without changing the configured source`() = runTest {
        val cache = MemoryCache(existingSnapshot())
        val repository = TimetableRepository(
            fetcher = FakeRemoteSource(),
            cache = cache,
            complicationRefreshRequester = NoOpComplicationRefreshRequester
        )

        repository.initialize()
        val before = repository.urlConfig.value
        val result = repository.refresh("https://example.com/timetable?bad=true")

        assertTrue(result.isFailure)
        assertEquals(before, repository.urlConfig.value)
        assertEquals(0, cache.saveCount)
    }

    @Test
    fun `unknown special timetable type is rejected`() = runTest {
        val cache = MemoryCache(existingSnapshot())
        val repository = TimetableRepository(
            fetcher = FakeRemoteSource(
                specialDates = Result.success(
                    SpecialDatesParseResult(specialDates = mapOf("2026-09-04" to "Z"))
                )
            ),
            cache = cache,
            complicationRefreshRequester = NoOpComplicationRefreshRequester
        )

        repository.initialize()
        val before = repository.data.value
        val result = repository.refresh()

        assertTrue(result.isFailure)
        assertEquals(before, repository.data.value)
        assertEquals(0, cache.saveCount)
    }

    private class MemoryCache(
        var snapshot: CachedTimetableSnapshot? = null
    ) : TimetableCacheStore {
        var saveCount = 0

        override suspend fun loadSnapshot(): CachedTimetableSnapshot? = snapshot

        override suspend fun saveSnapshot(snapshot: CachedTimetableSnapshot) {
            saveCount += 1
            this.snapshot = snapshot
        }
    }

    private class FakeRemoteSource(
        private val dayRotation: Result<Map<String, Int>> = Result.success(mapOf("2026-09-03" to 1)),
        private val specialDates: Result<SpecialDatesParseResult> = Result.success(SpecialDatesParseResult()),
        private val timetable: Result<TimetableParseResult> = Result.success(
            TimetableParseResult(TimetableData.normal, TimetableData.subjectSchedule)
        )
    ) : TimetableRemoteSource {
        override suspend fun fetchDayRotation(url: String): Result<Map<String, Int>> = dayRotation
        override suspend fun fetchSpecialDates(url: String): Result<SpecialDatesParseResult> = specialDates
        override suspend fun fetchTimetable(url: String): Result<TimetableParseResult> = timetable
    }

    private object NoOpComplicationRefreshRequester : ComplicationRefreshRequester {
        override fun requestAll() = Unit
    }

    @Test
    fun `first launch with empty cache falls back to bundled assets`() = runTest {
        val repository = TimetableRepository(
            fetcher = FakeRemoteSource(),
            cache = MemoryCache(),
            complicationRefreshRequester = NoOpComplicationRefreshRequester,
            bundledSource = com.timetable.wear.data.local.BundledTimetableSource { existingSnapshot() }
        )

        repository.initialize()

        assertEquals(mapOf("2026-09-03" to 1), repository.data.value.dayRotation)
    }

    private fun existingSnapshot(): CachedTimetableSnapshot = CachedTimetableSnapshot.from(
        urlBase = "https://example.com/old/",
        dayRotation = mapOf("2026-09-03" to 1),
        specialDates = emptyMap(),
        subjectSchedule = TimetableData.subjectSchedule,
        timetables = mapOf("normal" to TimetableData.normal)
    )
}
