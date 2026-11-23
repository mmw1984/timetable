package com.timetable.shared.engine

import com.timetable.shared.data.model.PeriodInfo
import com.timetable.shared.data.model.ScheduleItem
import com.timetable.shared.data.model.ScheduleItemType

/**
 * Contiguous same-subject 連堂 block containing the current period, for
 * merged countdown/display. Returns null when merging is off, the current
 * period is not a class, or it stands alone.
 *
 * Same rule as the tile grid / home list merging: PERIOD type only, exact
 * same non-blank subject, contiguous end == start, never assembly.
 */
data class MergedBlock(val displayName: String, val start: String, val end: String)

fun mergedPeriodBlock(
    items: List<ScheduleItem>,
    current: PeriodInfo,
    merge: Boolean
): MergedBlock? {
    if (!merge) return null
    if (current.type != PeriodInfo.PeriodType.PERIOD) return null
    val subject = current.subject.trim()
    if (subject.isEmpty() || current.start.isBlank() || current.end.isBlank()) return null
    val periods = items.filter { it.type == ScheduleItemType.PERIOD }
    if (periods.isEmpty()) return null
    val index = periods.indexOfFirst { it.start == current.start && it.end == current.end }
        .takeIf { it >= 0 }
        ?: periods.indexOfFirst {
            it.subject == current.subject &&
                it.start.isNotBlank() && it.end.isNotBlank() &&
                it.start <= current.start && current.end <= it.end
        }.takeIf { it >= 0 } ?: return null
    var first = index
    while (first > 0) {
        val prev = periods[first - 1]
        val cur = periods[first]
        if (prev.subject.trim() != subject || prev.end != cur.start) break
        first--
    }
    var last = index
    while (last < periods.size - 1) {
        val cur = periods[last]
        val next = periods[last + 1]
        if (next.subject.trim() != subject || cur.end != next.start) break
        last++
    }
    if (first == last) return null
    val head = periods[first]
    val tail = periods[last]
    val name = if (head.periodNumber != null && tail.periodNumber != null) {
        "第${head.periodNumber}-${tail.periodNumber}節"
    } else {
        head.displayName
    }
    return MergedBlock(displayName = name, start = head.start, end = tail.end)
}
