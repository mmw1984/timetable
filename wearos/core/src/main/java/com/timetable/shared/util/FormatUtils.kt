package com.timetable.shared.util

import com.timetable.shared.data.model.PeriodInfo
import java.util.Locale

fun parseSubject(subject: String): String {
    val trimmed = subject.trim()
    if (trimmed.isEmpty()) return trimmed
    // First whitespace-separated token without Regex allocs (called on every tile/complication update).
    var end = trimmed.length
    for (i in trimmed.indices) {
        val c = trimmed[i]
        if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
            end = i
            break
        }
    }
    val firstWord = if (end == trimmed.length) trimmed else trimmed.substring(0, end)
    return if (firstWord.any { it.isCjk() }) trimmed else firstWord
}

fun formatCountdown(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    val hours = safe / 3600
    val minutes = (safe % 3600) / 60
    val secs = safe % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, secs)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, secs)
    }
}

/**
 * Kit countdown style ("67:34"): total minutes, zero-padded, no hour field.
 * A merged 80-minute block reads "80:00" and always fits the 74dp slot.
 */
fun formatCountdownMinutes(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    return String.format(Locale.US, "%02d:%02d", safe / 60, safe % 60)
}

fun isActiveTimedPeriod(period: PeriodInfo): Boolean = period.type in ACTIVE_TIMED_TYPES

private val ACTIVE_TIMED_TYPES = setOf(
    PeriodInfo.PeriodType.PERIOD,
    PeriodInfo.PeriodType.BREAK_TIME,
    PeriodInfo.PeriodType.ASSEMBLY
)

/** Memoized "HH:mm" -> seconds; distinct clock times in a timetable are few. */
private val timeSecondsCache = java.util.concurrent.ConcurrentHashMap<String, Int>()

private fun timeToSecondsCached(value: String): Int {
    timeSecondsCache[value]?.let { return it }
    val parts = value.split(":")
    if (parts.size < 2) return 0
    val seconds = (parts[0].toIntOrNull() ?: 0) * 3600 + (parts[1].toIntOrNull() ?: 0) * 60
    if (timeSecondsCache.size < 256) timeSecondsCache[value] = seconds
    return seconds
}

private fun nowSeconds(): Int {
    val now = java.time.LocalTime.now()
    return now.hour * 3600 + now.minute * 60 + now.second
}

fun remainingSecondsFor(end: String): Int {
    return timeToSecondsCached(end) - nowSeconds()
}

fun secondsUntil(start: String): Int {
    return timeToSecondsCached(start) - nowSeconds()
}

fun elapsedSecondsFor(start: String): Int {
    return nowSeconds() - timeToSecondsCached(start)
}

fun formatMinutes(remainingSec: Int): String {
    val mins = (remainingSec.coerceAtLeast(0) + 59) / 60
    return if (mins <= 0) "0m" else "${mins}m"
}

fun formatMmSs(remainingSec: Int): String {
    val safe = remainingSec.coerceAtLeast(0)
    val mins = safe / 60
    val secs = safe % 60
    return String.format(Locale.US, "%02d:%02d", mins, secs)
}

fun formatMmSsAod(remainingSec: Int): String {
    val safe = remainingSec.coerceAtLeast(0)
    val mins = safe / 60
    return String.format(Locale.US, "%02d:--", mins)
}

fun isSchoolHours(now: java.time.LocalTime, lastEnd: String): Boolean {
    val lastSec = timeToSecondsCached(lastEnd) + 15 * 60
    if (lastSec == 15 * 60) return false
    val nowSec = now.hour * 3600 + now.minute * 60 + now.second
    val startSec = 7 * 3600 + 30 * 60
    return nowSec in startSec..lastSec
}

private fun Char.isCjk(): Boolean = code in 0x4E00..0x9FFF
