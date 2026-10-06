package com.timetable.wear.engine

import java.time.LocalDate
import java.time.LocalTime

internal object TimetableLogic {
    fun availableSchoolDates(
        dayRotation: Map<String, Int>,
        specialDates: Map<String, String>
    ): List<LocalDate> = (dayRotation.keys + specialDates.keys)
        .mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
        .distinct()
        .sorted()

    fun preferredSchoolDate(today: LocalDate, dates: List<LocalDate>): LocalDate? =
        dates.firstOrNull { it >= today } ?: dates.lastOrNull()

    fun adjacentSchoolDate(
        selectedDate: LocalDate,
        dates: List<LocalDate>,
        previous: Boolean
    ): LocalDate? = if (previous) {
        dates.lastOrNull { it < selectedDate }
    } else {
        dates.firstOrNull { it > selectedDate }
    }

    fun isInRange(now: LocalTime, start: String, end: String): Boolean {
        val second = now.toSecondOfDay()
        return second >= timeToSeconds(start) && second < timeToSeconds(end)
    }

    fun secondsRemaining(now: LocalTime, end: String): Int =
        (timeToSeconds(end) - now.toSecondOfDay()).coerceAtLeast(0)

    fun timeToSeconds(time: String): Int {
        secondsCache[time]?.let { return it }
        val parts = time.split(":")
        if (parts.size < 2) return 0
        val seconds = (parts[0].toIntOrNull() ?: 0) * 3_600 +
            (parts[1].toIntOrNull() ?: 0) * 60 +
            (parts.getOrElse(2) { "0" }.toIntOrNull() ?: 0)
        // Distinct clock times are few; memoize to avoid re-splitting on the 1s ticker.
        if (secondsCache.size < 256) secondsCache[time] = seconds
        return seconds
    }

    private val secondsCache = java.util.concurrent.ConcurrentHashMap<String, Int>()
}
