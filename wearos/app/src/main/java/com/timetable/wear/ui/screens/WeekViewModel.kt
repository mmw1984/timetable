package com.timetable.wear.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.timetable.wear.complications.parseSubject
import com.timetable.wear.data.model.TimetableType
import com.timetable.wear.data.repository.TimetableRepository
import com.timetable.wear.engine.TimetableEngine
import com.timetable.wear.engine.TimetableLogic
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class WeekDayItem(
    val date: LocalDate,
    val dateDisplay: String,
    val dayLabel: String,
    val typeLabel: String,
    val preview: String,
    val isToday: Boolean
)

@HiltViewModel
class WeekViewModel @Inject constructor(
    private val repository: TimetableRepository,
    private val engine: TimetableEngine
) : ViewModel() {

    private val _items = MutableStateFlow<List<WeekDayItem>>(emptyList())
    val items: StateFlow<List<WeekDayItem>> = _items.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val displayFormatter = DateTimeFormatter.ofPattern("M月d日 EEE", Locale.TAIWAN)
    private val shortFormatter = DateTimeFormatter.ofPattern("M/d", Locale.TAIWAN)

    fun load() {
        viewModelScope.launch {
            _isLoading.value = true
            // Ensure engine/repository initialized
            try { engine.start() } catch (_: Exception) {}
            val data = repository.data.value
            val allDates = TimetableLogic.availableSchoolDates(data.dayRotation, data.specialDates)
            val today = LocalDate.now()
            val upcoming = allDates.filter { it >= today }.take(7)
                .ifEmpty { allDates.takeLast(7) }
            val result = upcoming.map { date ->
                val dateStr = date.toString()
                val dayCycle = data.dayRotation[dateStr]
                val type = timetableTypeForDate(date, data)
                val preview = buildPreview(dayCycle, data.subjectSchedule)
                WeekDayItem(
                    date = date,
                    dateDisplay = displayFormatter.format(date),
                    dayLabel = if (dayCycle != null) "Day $dayCycle" else "—",
                    typeLabel = typeLabel(type),
                    preview = preview,
                    isToday = date == today
                )
            }
            _items.value = result
            _isLoading.value = false
        }
    }

    private fun timetableTypeForDate(date: LocalDate, data: com.timetable.wear.data.repository.TimetableRepoData): TimetableType {
        data.specialDates[date.toString()]?.let { return TimetableType.fromString(it) }
        if (date.toString() !in data.dayRotation) return TimetableType.NONE
        return if (date.dayOfWeek == java.time.DayOfWeek.FRIDAY) TimetableType.SPECIAL_B else TimetableType.NORMAL
    }

    private fun typeLabel(type: TimetableType): String = when (type) {
        TimetableType.NORMAL -> "正常"
        TimetableType.SPECIAL_A -> "特A"
        TimetableType.SPECIAL_B -> "特B"
        TimetableType.SPECIAL_C -> "特C"
        TimetableType.SPECIAL_D -> "特D"
        TimetableType.SPECIAL_E -> "特E"
        TimetableType.NONE -> "非上課日"
    }

    private fun buildPreview(
        dayCycle: Int?,
        schedule: Map<Int, Map<Int, String>>
    ): String {
        if (dayCycle == null) return "—"
        val map = schedule[dayCycle] ?: return "—"
        return (1..4).mapNotNull { map[it]?.let { parseSubject(it) } }.joinToString(" · ").ifBlank { "—" }
    }
}
