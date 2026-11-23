package com.timetable.wear.complications

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

fun getBatteryLevel(context: Context): Int {
    val snapshot = batterySnapshot(context)
    if (snapshot.level < 0 || snapshot.scale < 0) return -1
    return (snapshot.level * 100f / snapshot.scale).toInt().coerceIn(0, 100)
}

fun formatBatteryText(level: Int): String = if (level < 0) "—" else "$level%"

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
