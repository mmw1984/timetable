package com.timetable.shared.engine

import com.timetable.shared.data.model.ScheduleItem
import com.timetable.shared.data.model.ScheduleItemType

fun timeToMinutes(time: String): Int {
    if (time.isBlank()) return -1
    val parts = time.split(":")
    if (parts.size < 2) return -1
    val h = parts[0].toIntOrNull() ?: return -1
    val m = parts[1].toIntOrNull() ?: return -1
    return h * 60 + m
}

/** Merge contiguous same-subject periods into one item (連堂). */
fun mergeConsecutiveItems(items: List<ScheduleItem>): List<ScheduleItem> {
    if (items.isEmpty()) return items
    val merged = mutableListOf<ScheduleItem>()
    var current = items[0]
    for (i in 1 until items.size) {
        val next = items[i]
        // Only merge same type + same subject and contiguous time, skip assembly
        val canMerge = current.type == next.type &&
            current.type != ScheduleItemType.ASSEMBLY &&
            current.subject == next.subject &&
            current.subject.isNotBlank() &&
            current.end == next.start
        if (canMerge) {
            val newDisplayName = if (
                current.type == ScheduleItemType.PERIOD &&
                current.periodNumber != null && next.periodNumber != null
            ) {
                // Extract start number from current display (already may be range like "第1-2節")
                val startNum = current.periodNumber
                val endNum = next.periodNumber
                "第${startNum}-${endNum}節"
            } else {
                current.displayName
            }
            current = current.copy(
                displayName = newDisplayName,
                end = next.end
                // Keep start periodNumber for future merging (e.g., 1-2 + 3 -> 1-3)
                // periodNumber stays as start's number
            )
        } else {
            merged.add(current)
            current = next
        }
    }
    merged.add(current)
    return merged
}

fun isCurrentMerged(item: ScheduleItem, state: HomeScheduleState): Boolean {
    // Prefer exact stableKey match for non-merged items
    if (item.stableKey == state.currentItemId) return true
    // For merged ranges, check if current period is inside the range
    val cur = state.currentPeriod
    if (cur.start.isBlank() || cur.end.isBlank() || item.start.isBlank() || item.end.isBlank()) return false
    val curStart = timeToMinutes(cur.start)
    val curEnd = timeToMinutes(cur.end)
    val itemStart = timeToMinutes(item.start)
    val itemEnd = timeToMinutes(item.end)
    if (curStart < 0 || curEnd < 0 || itemStart < 0 || itemEnd < 0) return false
    // Current period fully inside merged item range
    return curStart >= itemStart && curEnd <= itemEnd
}
