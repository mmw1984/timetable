package com.timetable.wear.data.repository

import com.timetable.wear.data.local.BundledTimetableSource
import com.timetable.wear.data.local.CachedTimetableSnapshot
import com.timetable.wear.data.local.TimetableCacheStore
import com.timetable.wear.data.model.TimetableData
import com.timetable.wear.data.model.TimetableSchedule
import com.timetable.wear.data.model.TimetableType
import com.timetable.wear.data.remote.TimetableRemoteSource
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.URI
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

data class UrlConfig(
    val base: String = DEFAULT_URL_BASE
) {
    companion object {
        const val DEFAULT_URL_BASE = "https://raw.githubusercontent.com/mmw1984/timetable/refs/heads/main/"
    }
}

data class TimetableRepoData(
    val dayRotation: Map<String, Int> = TimetableData.dayRotation,
    val specialDates: Map<String, String> = TimetableData.specialDates,
    val subjectSchedule: Map<Int, Map<Int, String>> = TimetableData.subjectSchedule,
    val normalSchedule: TimetableSchedule? = null,
    val customTimetables: Map<String, TimetableSchedule> = emptyMap(),
    val isRemote: Boolean = false
)

@Singleton
class TimetableRepository @Inject constructor(
    private val fetcher: TimetableRemoteSource,
    private val cache: TimetableCacheStore,
    private val complicationRefreshRequester: ComplicationRefreshRequester,
    private val bundledSource: BundledTimetableSource? = null,
) {
    private val _data = MutableStateFlow(TimetableRepoData())
    val data: StateFlow<TimetableRepoData> = _data.asStateFlow()

    private val _urlConfig = MutableStateFlow(UrlConfig())
    val urlConfig: StateFlow<UrlConfig> = _urlConfig.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val initializationMutex = Mutex()
    private val refreshMutex = Mutex()
    private var initialized = false
    private val timeSecondsCache = java.util.concurrent.ConcurrentHashMap<String, Int>()

    suspend fun initialize() {
        initializationMutex.withLock {
            if (initialized) return
            val restoredCache = cache.loadSnapshot()?.also { snapshot ->
                _urlConfig.value = UrlConfig(snapshot.urlBase.ifBlank { UrlConfig.DEFAULT_URL_BASE })
                _data.value = TimetableRepoData(
                    dayRotation = snapshot.dayRotation,
                    specialDates = snapshot.specialDates,
                    subjectSchedule = snapshot.subjectScheduleAsInts(),
                    normalSchedule = snapshot.timetables["normal"],
                    customTimetables = snapshot.timetables - "normal",
                    isRemote = true
                )
            }
            // First launch with no cache: fall back to the data files bundled in
            // assets/ (synced from the repo at build time) instead of the stale
            // compiled-in defaults, so the app is usable offline immediately.
            val bundled = if (restoredCache == null) bundledSource?.loadBundled() else null
            if (bundled != null) {
                _data.value = TimetableRepoData(
                    dayRotation = bundled.dayRotation,
                    specialDates = bundled.specialDates,
                    subjectSchedule = bundled.subjectScheduleAsInts(),
                    normalSchedule = bundled.timetables["normal"],
                    customTimetables = bundled.timetables - "normal",
                    isRemote = false
                )
            }
            initialized = true
            // A complication provider may start before the UI. Refresh bound slots once its
            // direct-boot-safe cache has been restored, rather than waiting for a remote update.
            if (restoredCache != null) complicationRefreshRequester.requestAll()
        }
    }

    suspend fun refresh(urlOverride: String? = null): Result<Unit> = refreshMutex.withLock {
        _isRefreshing.value = true
        try {
            initialize()
            val base = normalizeBaseUrl(urlOverride ?: _urlConfig.value.base)
            val payload = coroutineScope {
                val dayRotation = async { fetcher.fetchDayRotation(base) }
                val specialDates = async { fetcher.fetchSpecialDates(base) }
                val timetable = async { fetcher.fetchTimetable(base) }
                RemotePayload(
                    dayRotation = dayRotation.await().getOrElse { throw IllegalStateException("無法下載上課日資料", it) },
                    specialDates = specialDates.await().getOrElse { throw IllegalStateException("無法下載特殊時間表", it) },
                    timetable = timetable.await().getOrElse { throw IllegalStateException("無法下載課表資料", it) }
                )
            }

            validate(payload)
            val schedules = buildMap {
                put("normal", checkNotNull(payload.timetable.normalSchedule))
                putAll(payload.specialDates.timetables)
            }
            val snapshot = CachedTimetableSnapshot.from(
                urlBase = base,
                dayRotation = payload.dayRotation,
                specialDates = payload.specialDates.specialDates,
                subjectSchedule = payload.timetable.subjectSchedule,
                timetables = schedules
            )
            check(snapshot.isValid()) { "遠端資料不完整，未套用更新" }

            // Disk is committed before memory, so a process death cannot leave a mixed version behind.
            cache.saveSnapshot(snapshot)
            _urlConfig.value = UrlConfig(base)
            _data.value = TimetableRepoData(
                dayRotation = payload.dayRotation,
                specialDates = payload.specialDates.specialDates,
                subjectSchedule = payload.timetable.subjectSchedule,
                normalSchedule = payload.timetable.normalSchedule,
                customTimetables = payload.specialDates.timetables,
                isRemote = true
            )
            complicationRefreshRequester.requestAll()
            Result.success(Unit)
        } catch (error: Exception) {
            Result.failure(error)
        } finally {
            _isRefreshing.value = false
        }
    }

    fun schedule(type: TimetableType): TimetableSchedule? {
        val current = _data.value
        return when (type) {
            TimetableType.NORMAL -> current.normalSchedule ?: TimetableData.normal
            TimetableType.SPECIAL_A -> current.customTimetables["specialA"] ?: TimetableData.specialA
            TimetableType.SPECIAL_B -> current.customTimetables["specialB"] ?: TimetableData.specialB
            TimetableType.SPECIAL_C -> current.customTimetables["specialC"] ?: TimetableData.specialC
            TimetableType.SPECIAL_D -> current.customTimetables["specialD"] ?: TimetableData.specialD
            TimetableType.SPECIAL_E -> current.customTimetables["specialE"] ?: TimetableData.specialE
            TimetableType.NONE -> null
        }
    }

    private fun validate(payload: RemotePayload) {
        check(payload.dayRotation.isNotEmpty()) { "上課日資料為空" }
        check(payload.dayRotation.values.all { it in 1..6 }) { "上課日資料包含無效 Day 編號" }
        check(payload.dayRotation.keys.all(::isIsoDate)) { "上課日資料包含無效日期" }
        check(payload.specialDates.specialDates.all { (date, type) ->
            isIsoDate(date) && (type == "Normal" || type in SPECIAL_TYPES)
        }) { "特殊日期資料包含無效日期或時間表類型" }
        check(isValidSchedule(payload.timetable.normalSchedule)) { "正常時間表缺失或無效" }
        check(payload.specialDates.timetables.values.all(::isValidSchedule)) { "特殊時間表包含無效資料" }
        check(payload.timetable.subjectSchedule.values.any { it.isNotEmpty() }) { "科目時間表缺失" }
    }

    private fun normalizeBaseUrl(raw: String): String {
        val trimmed = raw.trim().trimEnd('/')
        val uri = runCatching { URI(trimmed) }.getOrElse { throw IllegalArgumentException("資料來源 URL 格式無效") }
        require(
            uri.scheme == "https" &&
                !uri.host.isNullOrBlank() &&
                uri.query == null &&
                uri.fragment == null
        ) { "資料來源必須是沒有查詢參數的 HTTPS URL" }
        return "$trimmed/"
    }

    private fun isIsoDate(value: String): Boolean = runCatching {
        LocalDate.parse(value)
    }.isSuccess

    private fun isValidSchedule(schedule: TimetableSchedule?): Boolean {
        if (schedule == null || schedule.periods.isEmpty()) return false
        val assemblyValid = schedule.preSchoolAssembly?.let { isValidTimeRange(it.start, it.end) } ?: true
        return assemblyValid &&
            schedule.periods.all { isValidTimeRange(it.start, it.end) } &&
            schedule.breaks.all { isValidTimeRange(it.start, it.end) }
    }

    private fun isValidTimeRange(start: String, end: String): Boolean =
        start.isNotBlank() && end.isNotBlank() && timeToSeconds(start) < timeToSeconds(end)

    private fun timeToSeconds(value: String): Int {
        timeSecondsCache[value]?.let { return it }
        val parts = value.split(":")
        if (parts.size < 2) return -1
        val hour = parts[0].toIntOrNull() ?: return -1
        val minute = parts[1].toIntOrNull() ?: return -1
        val second = parts.getOrElse(2) { "0" }.toIntOrNull() ?: return -1
        if (hour !in 0..23 || minute !in 0..59 || second !in 0..59) return -1
        return (hour * 3_600 + minute * 60 + second).also {
            // Distinct clock times are few; memoize to avoid re-splitting on every check.
            if (timeSecondsCache.size < 256) timeSecondsCache[value] = it
        }
    }

    private data class RemotePayload(
        val dayRotation: Map<String, Int>,
        val specialDates: com.timetable.wear.data.remote.SpecialDatesParseResult,
        val timetable: com.timetable.wear.data.remote.TimetableParseResult
    )

    private companion object {
        val SPECIAL_TYPES = setOf("A", "B", "C", "D", "E")
    }
}
