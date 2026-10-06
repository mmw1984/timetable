package com.timetable.wear.data.remote

import com.timetable.wear.data.model.BreakPeriod
import com.timetable.wear.data.model.TimePeriod
import com.timetable.wear.data.model.TimetableSchedule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

data class SpecialDatesParseResult(
    val specialDates: Map<String, String> = emptyMap(),
    val timetables: Map<String, TimetableSchedule> = emptyMap()
)

data class TimetableParseResult(
    val normalSchedule: TimetableSchedule? = null,
    val subjectSchedule: Map<Int, Map<Int, String>> = emptyMap()
)

interface TimetableRemoteSource {
    suspend fun fetchDayRotation(url: String): Result<Map<String, Int>>
    suspend fun fetchSpecialDates(url: String): Result<SpecialDatesParseResult>
    suspend fun fetchTimetable(url: String): Result<TimetableParseResult>
}

class TimetableFetcher : TimetableRemoteSource {

    override suspend fun fetchDayRotation(url: String): Result<Map<String, Int>> = withContext(Dispatchers.IO) {
        runCatching { parseDayRotation(readText(url, "days.txt")) }
    }

    override suspend fun fetchSpecialDates(url: String): Result<SpecialDatesParseResult> = withContext(Dispatchers.IO) {
        runCatching { parseSpecialDates(readText(url, "special-date.txt")) }
    }

    override suspend fun fetchTimetable(url: String): Result<TimetableParseResult> = withContext(Dispatchers.IO) {
        runCatching { parseTimetable(readText(url, "timetable.txt")) }
    }

    private fun readText(baseUrl: String, filename: String): String {
        val connection = (URL("$baseUrl$filename").openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            requestMethod = "GET"
        }
        return try {
            check(connection.responseCode in 200..299) {
                "Failed to download $filename: HTTP ${connection.responseCode}"
            }
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    internal fun parseDayRotation(text: String): Map<String, Int> {
        val result = mutableMapOf<String, Int>()
        val regex = Regex("""([A-Za-z]+ \d{1,2}, \d{4}) \([A-Za-z]+\): Day (\d+)""")
        for (line in text.lines()) {
            val match = regex.find(line) ?: continue
            val date = try {
                LocalDate.parse(match.groupValues[1], dayFormatter)
            } catch (_: DateTimeParseException) {
                continue
            }
            match.groupValues[2].toIntOrNull()?.takeIf { it in 1..6 }?.let { result[date.toString()] = it }
        }
        return result
    }

    internal fun parseSpecialDates(text: String): SpecialDatesParseResult {
        val specialDates = mutableMapOf<String, String>()
        val timetables = mutableMapOf<String, TimetableSchedule>()
        var currentKey: String? = null
        var periods = mutableListOf<TimePeriod>()
        var breaks = mutableListOf<BreakPeriod>()
        var inDatesSection = false

        fun saveTimetable() {
            val key = currentKey ?: return
            if (periods.isNotEmpty()) {
                timetables[key] = TimetableSchedule(null, periods.toList(), breaks.toList())
            }
        }

        val headerRegex = Regex("""### Special Timetable ([A-E])""")
        for (line in text.lines()) {
            val trimmed = line.trim()
            val header = headerRegex.find(trimmed)
            if (header != null) {
                saveTimetable()
                currentKey = "special${header.groupValues[1]}"
                periods = mutableListOf()
                breaks = mutableListOf()
                inDatesSection = false
                continue
            }
            if (trimmed.startsWith("### Dates")) {
                saveTimetable()
                currentKey = null
                inDatesSection = true
                continue
            }
            if (!trimmed.contains("|")) continue
            val cells = trimmed.split("|").map { it.trim() }.filter { it.isNotEmpty() }
            if (cells.size < 3 || cells.any { it.contains("---") }) continue

            if (inDatesSection) {
                if (cells[0].lowercase(Locale.ROOT).contains("dates")) continue
                val type = cells.last()
                if (type != "Normal") {
                    expandDateRange(cells[0]).forEach { specialDates[it] = type }
                }
            } else if (currentKey != null && !cells[0].lowercase(Locale.ROOT).contains("period")) {
                val start = normalizeTime(cells[1])
                val end = normalizeTime(cells[2])
                if (start.isEmpty() || end.isEmpty()) continue
                val name = cells[0]
                if (isBreakRow(name)) {
                    breaks.add(BreakPeriod(start, end, mapBreakName(name)))
                } else {
                    periods.add(TimePeriod(start, end))
                }
            }
        }
        saveTimetable()
        return SpecialDatesParseResult(specialDates, timetables)
    }

    internal fun parseTimetable(text: String): TimetableParseResult {
        val periods = mutableListOf<TimePeriod>()
        val breaks = mutableListOf<BreakPeriod>()
        var assembly: TimePeriod? = null
        val subjectSchedule = (1..6).associateWith { mutableMapOf<Int, String>() }.toMutableMap()
        var periodIndex = 0

        for (line in text.lines()) {
            if (!line.contains("|")) continue
            val cells = line.split("|").map { it.trim().replace("**", "") }.filter { it.isNotEmpty() }
            if (cells.size < 2 || cells[0].lowercase(Locale.ROOT).startsWith("period")) continue
            val timeMatch = TIME_RANGE_REGEX.find(cells[0]) ?: continue
            val start = normalizeTime(timeMatch.groupValues[1])
            val end = normalizeTime(timeMatch.groupValues[2])
            if (start.isEmpty() || end.isEmpty()) continue

            when {
                cells[1].contains("pre-school assembly", ignoreCase = true) -> assembly = TimePeriod(start, end)
                cells[1].contains("recess", ignoreCase = true) -> breaks.add(BreakPeriod(start, end, "小息"))
                cells[1].contains("lunch", ignoreCase = true) -> breaks.add(BreakPeriod(start, end, "午餐"))
                cells[1].contains("roll", ignoreCase = true) -> breaks.add(BreakPeriod(start, end, "點名"))
                else -> {
                    periodIndex += 1
                    periods.add(TimePeriod(start, end))
                    for (day in 1..6) {
                        cells.getOrNull(day)?.let { subjectSchedule[day]?.put(periodIndex, it) }
                    }
                }
            }
        }

        val schedule = periods.takeIf { it.isNotEmpty() }?.let { TimetableSchedule(assembly, it, breaks) }
        return TimetableParseResult(schedule, subjectSchedule)
    }

    private fun normalizeTime(raw: String): String {
        val parts = raw.trim().split(":")
        if (parts.size != 2) return ""
        val hour = parts[0].toIntOrNull() ?: return ""
        val minute = parts[1].toIntOrNull() ?: return ""
        if (hour !in 1..23 || minute !in 0..59) return ""
        val normalizedHour = if (hour in 1..7) hour + 12 else hour
        return String.format(Locale.US, "%02d:%02d", normalizedHour, minute)
    }

    private fun isBreakRow(name: String): Boolean {
        val lower = name.lowercase(Locale.ROOT)
        return lower.contains("recess") || lower.contains("lunch") || lower.contains("roll") ||
            lower.contains("house meeting") || (lower.contains("assembly") && !lower.contains("pre-school"))
    }

    private fun mapBreakName(name: String): String = when {
        name.contains("lunch", true) && name.contains("house", true) -> "午餐/社際聚會"
        name.contains("lunch", true) -> "午餐"
        name.contains("recess", true) -> "小息"
        name.contains("roll", true) -> "點名"
        name.contains("long assembly", true) -> "長集會"
        name.contains("assembly", true) -> "集會"
        name.contains("house meeting", true) -> "午餐/社際聚會"
        else -> name
    }

    private fun expandDateRange(raw: String): List<String> {
        val rangeMatch = DATE_RANGE_REGEX.find(raw.trim())
        if (rangeMatch != null) {
            val end = parseDate(rangeMatch.groupValues[2]) ?: return emptyList()
            val left = rangeMatch.groupValues[1].trim()
            val start = when {
                left.contains("/") && left.count { it == '/' } == 1 -> parseDate("$left/${end.year}")
                left.contains("/") -> parseDate(left)
                else -> parseDate("${left.padStart(2, '0')}/${end.monthValue.toString().padStart(2, '0')}/${end.year}")
            } ?: return emptyList()
            return generateSequence(start) { current -> current.plusDays(1).takeIf { !it.isAfter(end) } }
                .filter { it.dayOfWeek != DayOfWeek.SATURDAY && it.dayOfWeek != DayOfWeek.SUNDAY }
                .map { it.toString() }
                .toList()
        }
        return parseDate(raw)
            ?.takeUnless { it.dayOfWeek == DayOfWeek.SATURDAY || it.dayOfWeek == DayOfWeek.SUNDAY }
            ?.let { listOf(it.toString()) }
            .orEmpty()
    }

    private fun parseDate(raw: String): LocalDate? = runCatching {
        LocalDate.parse(raw.trim(), dateFormatter)
    }.getOrNull()

    private companion object {
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 20_000
        val dayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM dd, yyyy", Locale.US)
        val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
        val TIME_RANGE_REGEX = Regex("""(\d{1,2}:\d{2})\s*-\s*(\d{1,2}:\d{2})""")
        val DATE_RANGE_REGEX = Regex("""^(.+?)\s*-\s*(.+)$""")
    }
}
