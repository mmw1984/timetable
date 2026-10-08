package com.timetable.wear.engine

import com.timetable.wear.data.model.PeriodInfo
import com.timetable.wear.data.model.ScheduleItem
import com.timetable.wear.data.model.ScheduleItemType
import com.timetable.wear.data.model.TimetableSchedule
import com.timetable.wear.data.model.TimetableType
import com.timetable.wear.data.repository.TimetableRepoData
import com.timetable.wear.data.repository.TimetableRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class HomeScheduleState(
    val currentPeriod: PeriodInfo = PeriodInfo(
        type = PeriodInfo.PeriodType.NONE,
        name = "載入中...",
        subject = "正在載入..."
    ),
    val nextPeriod: PeriodInfo? = null,
    val timetableType: TimetableType = TimetableType.NONE,
    val dayCycle: Int? = null,
    val scheduleItems: List<ScheduleItem> = emptyList(),
    val currentItemId: String? = null,
    val selectedDate: String = "",
    val selectedDateDisplay: String = "",
    val isViewingToday: Boolean = false,
    val followsToday: Boolean = true,
    val canNavigatePrevious: Boolean = false,
    val canNavigateNext: Boolean = false,
    val isLoading: Boolean = true
)

data class CountdownState(
    val countdown: String = "--:--:--",
    val countdownShort: String = "--:--",
    val countdownSeconds: Int = 0,
    val countdownProgress: Float = 0f,
    val countdownLabel: String = ""
)

data class TodaySnapshot(
    val timetableType: TimetableType,
    val dayCycle: Int?,
    val currentPeriod: PeriodInfo,
    val nextPeriod: PeriodInfo?,
    val scheduleItems: List<ScheduleItem>,
    val isNextDay: Boolean = false,
    val dateDisplay: String = ""
)

@Singleton
class TimetableEngine @Inject constructor(
    private val repository: TimetableRepository
) {
    private val _scheduleState = MutableStateFlow(HomeScheduleState())
    val scheduleState: StateFlow<HomeScheduleState> = _scheduleState.asStateFlow()

    private val _countdownState = MutableStateFlow(CountdownState())
    val countdownState: StateFlow<CountdownState> = _countdownState.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val initializationMutex = Mutex()
    private val stateLock = Any()
    private var timerJob: Job? = null
    private var initialized = false

    // Cache of the built timeline: rebuilt only when the underlying schedule data
    // changes, instead of on every 1-second tick.
    private var cachedSlotsSchedule: TimetableSchedule? = null
    private var cachedSlotsDayCycle: Int? = null
    private var cachedSlotsSubjects: Map<Int, Map<Int, String>>? = null
    private var cachedSlots: List<TimelineSlot> = emptyList()
    private var cachedItems: List<ScheduleItem> = emptyList()

    private val displayFormatter = DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.TAIWAN)

    suspend fun start() {
        ensureInitialized()
        startTimerIfNeeded()
    }

    /** Lightweight init for screens/tiles that need data but not the 1s ticker. */
    suspend fun ensureReady() {
        ensureInitialized()
    }

    fun stop() {
        timerJob?.cancel()
        timerJob = null
    }

    suspend fun previousSchoolDay() {
        ensureInitialized()
        navigateToAdjacentSchoolDay(previous = true)
    }

    suspend fun nextSchoolDay() {
        ensureInitialized()
        navigateToAdjacentSchoolDay(previous = false)
    }

    suspend fun refreshFromRemote(urlOverride: String? = null): Result<Unit> {
        ensureInitialized()
        val result = repository.refresh(urlOverride)
        if (result.isSuccess) {
            val current = _scheduleState.value
            val selected = current.selectedDate.takeIf { it.isNotBlank() }
                ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                ?: preferredSchoolDate(LocalDate.now(), repository.data.value)
                ?: LocalDate.now()
            refreshForDate(selected, current.followsToday)
            startTimerIfNeeded()
        }
        return result
    }

    suspend fun getTodaySnapshot(): TodaySnapshot? = runCatching {
        ensureInitialized()
        val now = LocalTime.now()
        val today = LocalDate.now()
        val data = repository.data.value
        val type = timetableTypeForDate(today, data)
        val dayCycle = data.dayRotation[today.toString()]
        val schedule = repository.schedule(type)
        val slots = slotsFor(schedule, dayCycle, data.subjectSchedule)
        TodaySnapshot(
            timetableType = type,
            dayCycle = dayCycle,
            currentPeriod = currentPeriodAt(now, slots),
            nextPeriod = nextPeriodAt(now, slots),
            scheduleItems = itemsFor(schedule, dayCycle, data.subjectSchedule),
            isNextDay = false,
            dateDisplay = formatDateDisplay(today)
        )
    }.getOrNull()

    /** For Tiles/Widgets: if today is not a school day, return the next school day's schedule. */
    suspend fun getTileSnapshot(): TodaySnapshot? = runCatching {
        ensureInitialized()
        val todaySnapshot = getTodaySnapshot()
        if (todaySnapshot != null && todaySnapshot.timetableType != TimetableType.NONE && todaySnapshot.dayCycle != null) {
            return@runCatching todaySnapshot
        }
        val data = repository.data.value
        val today = LocalDate.now()
        val dates = availableSchoolDates(data)
        val nextDate = dates.firstOrNull { it.isAfter(today) } ?: return@runCatching todaySnapshot
        val type = timetableTypeForDate(nextDate, data)
        val dayCycle = data.dayRotation[nextDate.toString()]
        val schedule = repository.schedule(type)
        val items = itemsFor(schedule, dayCycle, data.subjectSchedule)
        val firstPeriod = schedule?.periods?.firstOrNull()
        val firstSubject = dayCycle?.let { data.subjectSchedule[it]?.get(1) } ?: "課程"
        val firstSlot = if (firstPeriod != null) {
            PeriodInfo(
                type = PeriodInfo.PeriodType.FREE,
                name = "下次上課",
                start = firstPeriod.start,
                end = firstPeriod.end,
                subject = "${formatDateDisplay(nextDate)} · Day ${dayCycle ?: "—"} · $firstSubject"
            )
        } else {
            PeriodInfo(
                type = PeriodInfo.PeriodType.FREE,
                name = "下次上課",
                subject = formatDateDisplay(nextDate)
            )
        }
        TodaySnapshot(
            timetableType = type,
            dayCycle = dayCycle,
            currentPeriod = firstSlot,
            nextPeriod = null,
            scheduleItems = items,
            isNextDay = true,
            dateDisplay = formatDateDisplay(nextDate)
        )
    }.getOrNull()

    private suspend fun ensureInitialized() {
        initializationMutex.withLock {
            if (initialized) return
            repository.initialize()
            initialized = true
            val target = preferredSchoolDate(LocalDate.now(), repository.data.value) ?: LocalDate.now()
            refreshForDate(target, followsToday = true)
        }
    }

    private fun navigateToAdjacentSchoolDay(previous: Boolean) {
        val current = _scheduleState.value
        val selected = current.selectedDate.takeIf { it.isNotBlank() }
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: return
        val dates = availableSchoolDates(repository.data.value)
        val target = TimetableLogic.adjacentSchoolDate(selected, dates, previous) ?: return
        refreshForDate(target, followsToday = target == LocalDate.now())
        startTimerIfNeeded()
    }

    private fun refreshForDate(date: LocalDate, followsToday: Boolean) {
        synchronized(stateLock) {
            val data = repository.data.value
            val dateString = date.toString()
            val dates = availableSchoolDates(data)
            val type = timetableTypeForDate(date, data)
            val dayCycle = data.dayRotation[dateString]
            val schedule = repository.schedule(type)
            val isToday = date == LocalDate.now()
            val items = itemsFor(schedule, dayCycle, data.subjectSchedule)
            val slots = slotsFor(schedule, dayCycle, data.subjectSchedule)
            val now = LocalTime.now()
            val current = if (isToday) {
                currentPeriodAt(now, slots)
            } else {
                previewPeriod(schedule, dayCycle)
            }
            val next = if (isToday) nextPeriodAt(now, slots) else slots.firstOrNull()?.toPeriodInfo()
            val currentItemId = if (isToday) findCurrentItemId(now, items) else null

            _scheduleState.value = HomeScheduleState(
                currentPeriod = current,
                nextPeriod = next,
                timetableType = type,
                dayCycle = dayCycle,
                scheduleItems = items,
                currentItemId = currentItemId,
                selectedDate = dateString,
                selectedDateDisplay = formatDateDisplay(date),
                isViewingToday = isToday,
                followsToday = followsToday,
                canNavigatePrevious = dates.any { it < date },
                canNavigateNext = dates.any { it > date },
                isLoading = false
            )
            updateCountdown(now, current)
        }
    }

    private fun startTimerIfNeeded() {
        if (timerJob?.isActive == true || !_scheduleState.value.followsToday) return
        timerJob = scope.launch {
            while (true) {
                val now = LocalTime.now()
                val today = LocalDate.now()
                var state = _scheduleState.value

                if (state.followsToday && state.selectedDate != today.toString()) {
                    val preferred = preferredSchoolDate(today, repository.data.value) ?: today
                    refreshForDate(preferred, followsToday = true)
                    state = _scheduleState.value
                }

                if (state.isViewingToday) {
                    updateLivePeriod(now)
                    // Sleep until the next whole second to avoid drift accumulation
                    // from fixed 1s delays.
                    delay(millisUntilNextSecond())
                } else {
                    // While previewing the next school day, only poll for a date rollover.
                    delay(60_000)
                }
            }
        }
    }

    private fun millisUntilNextSecond(): Long {
        val remainder = System.currentTimeMillis() % 1_000L
        return (1_000L - remainder).coerceAtLeast(200L)
    }

    private fun updateLivePeriod(now: LocalTime) {
        synchronized(stateLock) {
            val state = _scheduleState.value
            if (!state.followsToday || !state.isViewingToday) return
            val schedule = repository.schedule(state.timetableType) ?: return
            val subjects = repository.data.value.subjectSchedule
            val slots = slotsFor(schedule, state.dayCycle, subjects)
            val current = currentPeriodAt(now, slots)
            val next = nextPeriodAt(now, slots)
            val currentItemId = findCurrentItemId(now, state.scheduleItems)

            if (
                current != state.currentPeriod ||
                next != state.nextPeriod ||
                currentItemId != state.currentItemId
            ) {
                _scheduleState.update {
                    it.copy(currentPeriod = current, nextPeriod = next, currentItemId = currentItemId)
                }
            }
            updateCountdown(now, current)
        }
    }

    private fun updateCountdown(now: LocalTime, period: PeriodInfo) {
        if (period.type !in TIMED_PERIOD_TYPES || period.end.isBlank()) {
            _countdownState.value = CountdownState()
            return
        }
        val remaining = TimetableLogic.secondsRemaining(now, period.end)
        val total = period.totalSeconds.coerceAtLeast(1)
        _countdownState.value = CountdownState(
            countdown = formatCountdown(remaining),
            countdownShort = formatCountdownShort(remaining),
            countdownSeconds = remaining,
            countdownProgress = (remaining.toFloat() / total).coerceIn(0f, 1f),
            countdownLabel = when (period.type) {
                PeriodInfo.PeriodType.PERIOD -> "下課倒計時"
                PeriodInfo.PeriodType.BREAK_TIME -> "休息結束倒計時"
                PeriodInfo.PeriodType.ASSEMBLY -> "早會結束倒計時"
                else -> ""
            }
        )
    }

    private fun timetableTypeForDate(date: LocalDate, data: TimetableRepoData): TimetableType {
        data.specialDates[date.toString()]?.let { return TimetableType.fromString(it) }
        if (date.toString() !in data.dayRotation) return TimetableType.NONE
        return if (date.dayOfWeek == java.time.DayOfWeek.FRIDAY) {
            TimetableType.SPECIAL_B
        } else {
            TimetableType.NORMAL
        }
    }

    private fun availableSchoolDates(data: TimetableRepoData): List<LocalDate> =
        TimetableLogic.availableSchoolDates(data.dayRotation, data.specialDates)

    private fun preferredSchoolDate(today: LocalDate, data: TimetableRepoData): LocalDate? {
        val dates = availableSchoolDates(data)
        return TimetableLogic.preferredSchoolDate(today, dates)
    }

    /**
     * Cached timeline accessors. The schedule instance and subject map come from the
     * repository's StateFlow, so referential equality is enough to detect changes;
     * while data is unchanged the 1-second ticker reuses the built lists with zero
     * allocation instead of rebuilding and sorting them every tick.
     */
    private fun slotsFor(
        schedule: TimetableSchedule?,
        dayCycle: Int?,
        subjects: Map<Int, Map<Int, String>>
    ): List<TimelineSlot> {
        if (schedule === cachedSlotsSchedule && dayCycle == cachedSlotsDayCycle && subjects === cachedSlotsSubjects) {
            return cachedSlots
        }
        cachedSlotsSchedule = schedule
        cachedSlotsDayCycle = dayCycle
        cachedSlotsSubjects = subjects
        cachedSlots = schedule?.let { buildSlots(it, dayCycle) }.orEmpty()
        cachedItems = buildScheduleItems(schedule, dayCycle)
        return cachedSlots
    }

    private fun itemsFor(
        schedule: TimetableSchedule?,
        dayCycle: Int?,
        subjects: Map<Int, Map<Int, String>>
    ): List<ScheduleItem> {
        slotsFor(schedule, dayCycle, subjects)
        return cachedItems
    }

    private fun buildSlots(schedule: TimetableSchedule, dayCycle: Int?): List<TimelineSlot> = buildList {
        schedule.preSchoolAssembly?.let {
            add(TimelineSlot(it.start, it.end, PeriodInfo.PeriodType.ASSEMBLY, "早會", "早會"))
        }
        schedule.periods.forEachIndexed { index, period ->
            val subject = repository.data.value.subjectSchedule[dayCycle]?.get(index + 1) ?: "課程"
            add(TimelineSlot(period.start, period.end, PeriodInfo.PeriodType.PERIOD, "第${index + 1}節", subject))
        }
        schedule.breaks.forEach { breakPeriod ->
            add(TimelineSlot(breakPeriod.start, breakPeriod.end, PeriodInfo.PeriodType.BREAK_TIME, breakPeriod.name, breakPeriod.name))
        }
    }.sortedBy { timeToSeconds(it.start) }

    private fun buildScheduleItems(schedule: TimetableSchedule?, dayCycle: Int?): List<ScheduleItem> {
        if (schedule == null) return emptyList()
        return buildList {
            schedule.preSchoolAssembly?.let {
                add(ScheduleItem(ScheduleItemType.ASSEMBLY, displayName = "早會", subject = "早會", start = it.start, end = it.end))
            }
            schedule.periods.forEachIndexed { index, period ->
                val subject = repository.data.value.subjectSchedule[dayCycle]?.get(index + 1) ?: "課程"
                add(ScheduleItem(ScheduleItemType.PERIOD, index + 1, "第${index + 1}節", subject, period.start, period.end))
            }
            schedule.breaks.forEach { breakPeriod ->
                add(ScheduleItem(ScheduleItemType.BREAK_TIME, displayName = breakPeriod.name, subject = breakPeriod.name, start = breakPeriod.start, end = breakPeriod.end))
            }
        }.sortedBy { timeToSeconds(it.start) }
    }

    private fun currentPeriodAt(now: LocalTime, slots: List<TimelineSlot>): PeriodInfo {
        return slots.firstOrNull { slot ->
            TimetableLogic.isInRange(now, slot.start, slot.end)
        }?.toPeriodInfo() ?: PeriodInfo(
            type = if (slots.isEmpty()) PeriodInfo.PeriodType.NONE else PeriodInfo.PeriodType.FREE,
            name = if (slots.isEmpty()) "今日沒有課程" else "空堂時間",
            subject = if (slots.isEmpty()) "非上課日" else "目前沒有課程"
        )
    }

    private fun nextPeriodAt(now: LocalTime, slots: List<TimelineSlot>): PeriodInfo? =
        slots.firstOrNull { timeToSeconds(it.start) > now.toSecondOfDay() }?.toPeriodInfo()

    private fun previewPeriod(schedule: TimetableSchedule?, dayCycle: Int?): PeriodInfo {
        if (schedule == null) {
            return PeriodInfo(PeriodInfo.PeriodType.NONE, "非上課日", subject = "該日沒有課程")
        }
        val firstStart = schedule.periods.firstOrNull()?.start.orEmpty()
        val lastEnd = schedule.periods.lastOrNull()?.end.orEmpty()
        return PeriodInfo(
            type = PeriodInfo.PeriodType.FREE,
            name = "課表預覽",
            subject = if (dayCycle != null) {
                "Day $dayCycle · ${schedule.periods.size}節 · $firstStart–$lastEnd"
            } else {
                "$firstStart–$lastEnd"
            },
            start = firstStart,
            end = lastEnd
        )
    }

    private fun findCurrentItemId(now: LocalTime, items: List<ScheduleItem>): String? {
        return items.firstOrNull { TimetableLogic.isInRange(now, it.start, it.end) }?.stableKey
    }

    private fun formatDateDisplay(date: LocalDate): String = displayFormatter.format(date)

    private fun formatCountdown(seconds: Int): String {
        val hours = seconds / 3_600
        val minutes = (seconds % 3_600) / 60
        val secs = seconds % 60
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, secs)
    }

    private fun formatCountdownShort(seconds: Int): String {
        val safe = seconds.coerceAtLeast(0)
        return String.format(Locale.US, "%02d:%02d", safe / 60, safe % 60)
    }

    private fun timeToSeconds(time: String): Int = TimetableLogic.timeToSeconds(time)

    private data class TimelineSlot(
        val start: String,
        val end: String,
        val type: PeriodInfo.PeriodType,
        val name: String,
        val subject: String
    ) {
        fun toPeriodInfo() = PeriodInfo(type, name, start, end, subject)
    }

    private companion object {
        val TIMED_PERIOD_TYPES = setOf(
            PeriodInfo.PeriodType.PERIOD,
            PeriodInfo.PeriodType.BREAK_TIME,
            PeriodInfo.PeriodType.ASSEMBLY
        )
    }
}
