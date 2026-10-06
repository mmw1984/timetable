package com.timetable.wear.data.model

import kotlinx.serialization.Serializable

@Serializable
data class TimePeriod(
    val start: String,
    val end: String
)

@Serializable
data class BreakPeriod(
    val start: String,
    val end: String,
    val name: String
)

@Serializable
data class TimetableSchedule(
    val preSchoolAssembly: TimePeriod? = null,
    val periods: List<TimePeriod>,
    val breaks: List<BreakPeriod>
)

enum class ScheduleItemType {
    ASSEMBLY,
    PERIOD,
    BREAK_TIME
}

data class ScheduleItem(
    val type: ScheduleItemType,
    val periodNumber: Int? = null,
    val displayName: String,
    val subject: String,
    val start: String,
    val end: String
) {
    val stableKey: String
        get() = "${type.name}-${periodNumber ?: 0}-$start-$end"
}

enum class TimetableType(val displayText: String, val noticeText: String) {
    NORMAL("正常時間表", "正常時間表"),
    SPECIAL_A("特殊時間表A", "特殊時間表A - 學期初安排"),
    SPECIAL_B("特殊時間表B", "特殊時間表B"),
    SPECIAL_C("特殊時間表C", "特殊時間表C"),
    SPECIAL_D("特殊時間表D", "特殊時間表D"),
    SPECIAL_E("特殊時間表E", "特殊時間表E"),
    NONE("非上課日", "今日無課程");

    companion object {
        fun fromString(value: String): TimetableType = when (value) {
            "A" -> SPECIAL_A
            "B" -> SPECIAL_B
            "C" -> SPECIAL_C
            "D" -> SPECIAL_D
            "E" -> SPECIAL_E
            else -> NORMAL
        }
    }
}

data class PeriodInfo(
    val type: PeriodType = PeriodType.NONE,
    val name: String = "",
    val start: String = "",
    val end: String = "",
    val subject: String = ""
) {
    enum class PeriodType {
        PERIOD, BREAK_TIME, ASSEMBLY, FREE, NONE
    }

    val totalSeconds: Int
        get() {
            if (start.isEmpty() || end.isEmpty()) return 0
            return timeToSeconds(end) - timeToSeconds(start)
        }

    private fun timeToSeconds(time: String): Int {
        val parts = time.split(":")
        if (parts.size < 2) return 0
        return (parts[0].toIntOrNull() ?: 0) * 3600 +
                (parts[1].toIntOrNull() ?: 0) * 60 +
                (parts.getOrElse(2) { "0" }.toIntOrNull() ?: 0)
    }
}
