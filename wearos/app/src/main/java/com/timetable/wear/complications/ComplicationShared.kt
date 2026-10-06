package com.timetable.wear.complications

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.timetable.wear.data.model.PeriodInfo
import java.util.Locale

fun parseSubject(subject: String): String {
    val trimmed = subject.trim()
    if (trimmed.isEmpty()) return trimmed
    val firstWord = trimmed.split(SUBJECT_SPLIT_REGEX).first()
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

fun getBatteryLevel(context: Context): Int {
    val snapshot = batterySnapshot(context)
    if (snapshot.level < 0 || snapshot.scale < 0) return -1
    return (snapshot.level * 100f / snapshot.scale).toInt().coerceIn(0, 100)
}

fun formatBatteryText(level: Int): String = if (level < 0) "—" else "$level%"

fun isActiveTimedPeriod(period: PeriodInfo): Boolean = period.type in ACTIVE_TIMED_TYPES

private val SUBJECT_SPLIT_REGEX = Regex("\\s+")

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

/**
 * Battery state is read via a sticky broadcast; cache it briefly so the
 * complication/tile providers (which query level, charging and power-save
 * together on every update) issue a single broadcast per window instead of three.
 */
private const val BATTERY_CACHE_MILLIS = 30_000L

private data class BatterySnapshot(
    val level: Int,
    val scale: Int,
    val status: Int,
    val powerSave: Boolean,
    val timestamp: Long
)

@Volatile
private var batterySnapshot: BatterySnapshot? = null

private fun batterySnapshot(context: Context): BatterySnapshot {
    val now = android.os.SystemClock.elapsedRealtime()
    batterySnapshot?.takeIf { now - it.timestamp < BATTERY_CACHE_MILLIS }?.let { return it }
    val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val snapshot = BatterySnapshot(
        level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1,
        scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1,
        status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1,
        powerSave = try {
            context.getSystemService(android.os.PowerManager::class.java)?.isPowerSaveMode == true
        } catch (_: Exception) { false },
        timestamp = now
    )
    batterySnapshot = snapshot
    return snapshot
}

fun isPowerSaveMode(context: Context): Boolean {
    return batterySnapshot(context).powerSave
}

fun isBatteryCharging(context: Context): Boolean {
    val status = batterySnapshot(context).status
    return status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
}

fun getBatteryIconRes(context: Context, level: Int): Int {
    if (isPowerSaveMode(context)) return com.timetable.wear.R.drawable.ic_battery_saver
    if (level >= 100) return com.timetable.wear.R.drawable.ic_battery_full
    if (isBatteryCharging(context)) {
        // Charging levels 20/30/50/80/90/full mapping - use generic charging icon, full handled above
        return com.timetable.wear.R.drawable.ic_battery_charging
    }
    // 0-6 bar mapping: 0-14→0, 15-28→1, 29-42→2, 43-56→3, 57-71→4, 72-85→5, 86-99→6
    val idx = when (level.coerceIn(0, 99)) {
        in 0..14 -> 0
        in 15..28 -> 1
        in 29..42 -> 2
        in 43..56 -> 3
        in 57..71 -> 4
        in 72..85 -> 5
        else -> 6
    }
    return when (idx) {
        0 -> com.timetable.wear.R.drawable.ic_battery_0
        1 -> com.timetable.wear.R.drawable.ic_battery_1
        2 -> com.timetable.wear.R.drawable.ic_battery_2
        3 -> com.timetable.wear.R.drawable.ic_battery_3
        4 -> com.timetable.wear.R.drawable.ic_battery_4
        5 -> com.timetable.wear.R.drawable.ic_battery_5
        else -> com.timetable.wear.R.drawable.ic_battery_6
    }
}

private fun Char.isCjk(): Boolean = code in 0x4E00..0x9FFF
