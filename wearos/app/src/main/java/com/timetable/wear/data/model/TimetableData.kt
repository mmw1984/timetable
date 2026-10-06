package com.timetable.wear.data.model

import kotlinx.serialization.Serializable

object TimetableData {

    val normal = TimetableSchedule(
        preSchoolAssembly = TimePeriod(start = "08:15", end = "08:40"),
        periods = listOf(
            TimePeriod(start = "08:40", end = "09:20"),
            TimePeriod(start = "09:20", end = "10:00"),
            TimePeriod(start = "10:20", end = "11:00"),
            TimePeriod(start = "11:00", end = "11:40"),
            TimePeriod(start = "12:50", end = "13:30"),
            TimePeriod(start = "13:30", end = "14:10"),
            TimePeriod(start = "14:25", end = "15:05"),
            TimePeriod(start = "15:05", end = "15:45")
        ),
        breaks = listOf(
            BreakPeriod(start = "10:00", end = "10:20", name = "小息"),
            BreakPeriod(start = "11:40", end = "12:50", name = "午餐"),
            BreakPeriod(start = "14:10", end = "14:25", name = "小息")
        )
    )

    val specialA = TimetableSchedule(
        preSchoolAssembly = TimePeriod(start = "08:15", end = "08:30"),
        periods = listOf(
            TimePeriod(start = "08:30", end = "09:00"),
            TimePeriod(start = "09:00", end = "09:30"),
            TimePeriod(start = "09:50", end = "10:20"),
            TimePeriod(start = "10:20", end = "10:50"),
            TimePeriod(start = "11:05", end = "11:35"),
            TimePeriod(start = "11:35", end = "12:05"),
            TimePeriod(start = "13:15", end = "13:45"),
            TimePeriod(start = "13:45", end = "14:15")
        ),
        breaks = listOf(
            BreakPeriod(start = "09:30", end = "09:50", name = "小息"),
            BreakPeriod(start = "10:50", end = "11:05", name = "小息"),
            BreakPeriod(start = "12:05", end = "13:10", name = "午餐"),
            BreakPeriod(start = "13:10", end = "13:15", name = "點名")
        )
    )

    val specialB = TimetableSchedule(
        preSchoolAssembly = TimePeriod(start = "08:15", end = "08:30"),
        periods = listOf(
            TimePeriod(start = "08:30", end = "09:05"),
            TimePeriod(start = "09:05", end = "09:40"),
            TimePeriod(start = "10:00", end = "10:35"),
            TimePeriod(start = "10:35", end = "11:10"),
            TimePeriod(start = "11:25", end = "12:00"),
            TimePeriod(start = "12:00", end = "12:35"),
            TimePeriod(start = "13:45", end = "14:20"),
            TimePeriod(start = "14:20", end = "14:55")
        ),
        breaks = listOf(
            BreakPeriod(start = "09:40", end = "10:00", name = "小息"),
            BreakPeriod(start = "11:10", end = "11:25", name = "小息"),
            BreakPeriod(start = "12:35", end = "13:40", name = "午餐"),
            BreakPeriod(start = "13:40", end = "13:45", name = "點名"),
            BreakPeriod(start = "14:55", end = "15:45", name = "集會")
        )
    )

    val specialC = TimetableSchedule(
        preSchoolAssembly = TimePeriod(start = "08:15", end = "08:30"),
        periods = listOf(
            TimePeriod(start = "08:30", end = "09:00"),
            TimePeriod(start = "09:00", end = "09:30"),
            TimePeriod(start = "09:50", end = "10:20"),
            TimePeriod(start = "10:20", end = "10:50"),
            TimePeriod(start = "11:05", end = "11:35"),
            TimePeriod(start = "11:35", end = "12:05"),
            TimePeriod(start = "13:15", end = "13:45"),
            TimePeriod(start = "13:45", end = "14:15")
        ),
        breaks = listOf(
            BreakPeriod(start = "09:30", end = "09:50", name = "小息"),
            BreakPeriod(start = "10:50", end = "11:05", name = "小息"),
            BreakPeriod(start = "12:05", end = "13:10", name = "午餐"),
            BreakPeriod(start = "13:10", end = "13:15", name = "點名"),
            BreakPeriod(start = "14:15", end = "15:45", name = "長集會")
        )
    )

    val specialD = TimetableSchedule(
        preSchoolAssembly = TimePeriod(start = "08:15", end = "08:30"),
        periods = listOf(
            TimePeriod(start = "08:30", end = "09:10"),
            TimePeriod(start = "09:10", end = "09:50"),
            TimePeriod(start = "10:10", end = "10:50"),
            TimePeriod(start = "10:50", end = "11:30"),
            TimePeriod(start = "13:10", end = "13:45"),
            TimePeriod(start = "13:45", end = "14:20"),
            TimePeriod(start = "14:35", end = "15:10"),
            TimePeriod(start = "15:10", end = "15:45")
        ),
        breaks = listOf(
            BreakPeriod(start = "09:50", end = "10:10", name = "小息"),
            BreakPeriod(start = "11:30", end = "13:05", name = "午餐/社際聚會"),
            BreakPeriod(start = "13:05", end = "13:10", name = "點名"),
            BreakPeriod(start = "14:20", end = "14:35", name = "小息")
        )
    )

    val specialE = TimetableSchedule(
        preSchoolAssembly = TimePeriod(start = "08:15", end = "08:30"),
        periods = listOf(
            TimePeriod(start = "08:30", end = "09:00"),
            TimePeriod(start = "09:00", end = "09:30"),
            TimePeriod(start = "09:45", end = "10:15"),
            TimePeriod(start = "10:15", end = "10:45"),
            TimePeriod(start = "10:55", end = "11:25"),
            TimePeriod(start = "11:25", end = "11:55"),
            TimePeriod(start = "12:05", end = "12:35"),
            TimePeriod(start = "12:35", end = "13:05")
        ),
        breaks = listOf(
            BreakPeriod(start = "09:30", end = "09:45", name = "小息"),
            BreakPeriod(start = "10:45", end = "10:55", name = "小息"),
            BreakPeriod(start = "11:55", end = "12:05", name = "小息")
        )
    )

    fun schedule(type: TimetableType): TimetableSchedule? = when (type) {
        TimetableType.NORMAL -> normal
        TimetableType.SPECIAL_A -> specialA
        TimetableType.SPECIAL_B -> specialB
        TimetableType.SPECIAL_C -> specialC
        TimetableType.SPECIAL_D -> specialD
        TimetableType.SPECIAL_E -> specialE
        TimetableType.NONE -> null
    }

    // 2026-2027 Class 5E（X1 = PHY WKW 511、X2 = ICT WKC 316 已展開）
    val subjectSchedule: Map<Int, Map<Int, String>> = mapOf(
        1 to mapOf(
            1 to "ENG KKY 401", 2 to "ENG KKY 401",
            3 to "MACO YPC 401", 4 to "MACO YPC 401",
            5 to "PE LD,WLS G001", 6 to "PE LD,WLS G001",
            7 to "PHY WKW 511", 8 to "PHY WKW 511"
        ),
        2 to mapOf(
            1 to "ICT WKC 316", 2 to "ICT WKC 316",
            3 to "CHIN NKT 401", 4 to "CHIN NKT 401",
            5 to "MACO YPC 401", 6 to "MACO YPC 401",
            7 to "CS LPY 401", 8 to "ENG KKY 401"
        ),
        3 to mapOf(
            1 to "CHIN NKT 401", 2 to "CHIN NKT 401",
            3 to "ICT WKC 316", 4 to "ENG KKY 401",
            5 to "PHY WKW 511", 6 to "PHY WKW 511",
            7 to "MACO YPC 401", 8 to "MACO YPC 401"
        ),
        4 to mapOf(
            1 to "MACO YPC 401", 2 to "MACO YPC 401",
            3 to "CEP LWF 401", 4 to "CHIN NKT 401",
            5 to "PHY WKW 511", 6 to "ENG KKY 401",
            7 to "ICT WKC 316", 8 to "ICT WKC 316"
        ),
        5 to mapOf(
            1 to "PHY WKW 511", 2 to "PHY WKW 511",
            3 to "ENG KKY 401", 4 to "ENG KKY 401",
            5 to "CS LPY 401", 6 to "CS LPY 401",
            7 to "MACO YPC 401", 8 to "CHIN NKT 401"
        ),
        6 to mapOf(
            1 to "MACO YPC 401", 2 to "MACO YPC 401",
            3 to "ICT WKC 316", 4 to "ICT WKC 316",
            5 to "C&L SDF 401", 6 to "ENG KKY 401",
            7 to "CHIN NKT 401", 8 to "CHIN NKT 401"
        )
    )

    val dayRotation: Map<String, Int> = mapOf(
        "2026-09-03" to 1, "2026-09-04" to 2, "2026-09-07" to 3, "2026-09-08" to 4, "2026-09-09" to 5, "2026-09-10" to 6,
        "2026-09-11" to 1, "2026-09-14" to 2, "2026-09-15" to 3, "2026-09-16" to 4, "2026-09-17" to 5, "2026-09-18" to 6,
        "2026-09-21" to 1, "2026-09-22" to 2, "2026-09-23" to 3, "2026-09-24" to 4, "2026-09-25" to 5, "2026-09-28" to 6,
        "2026-09-29" to 1, "2026-09-30" to 2, "2026-10-02" to 3, "2026-10-05" to 4, "2026-10-06" to 5, "2026-10-07" to 6,
        "2026-10-08" to 1, "2026-10-09" to 2, "2026-10-12" to 3, "2026-10-13" to 4, "2026-10-14" to 5, "2026-10-15" to 6,
        "2026-10-16" to 1, "2026-10-20" to 2, "2026-10-21" to 3, "2026-10-22" to 4, "2026-10-23" to 5, "2026-10-26" to 6,
        "2026-10-27" to 1, "2026-10-28" to 2, "2026-10-29" to 3, "2026-10-30" to 4, "2026-11-02" to 5, "2026-11-03" to 6,
        "2026-11-04" to 1, "2026-11-05" to 2, "2026-11-06" to 3, "2026-11-09" to 4, "2026-11-10" to 5, "2026-11-11" to 6,
        "2026-11-12" to 1, "2026-11-13" to 2, "2026-11-16" to 3, "2026-11-17" to 4, "2026-11-18" to 5, "2026-11-19" to 6,
        "2026-11-20" to 1, "2026-11-23" to 2, "2026-11-24" to 3, "2026-11-25" to 4, "2026-11-26" to 5, "2026-11-27" to 6,
        "2026-11-30" to 1, "2026-12-01" to 2, "2026-12-02" to 3, "2026-12-03" to 4, "2026-12-04" to 5, "2026-12-07" to 6,
        "2026-12-08" to 1, "2026-12-09" to 2, "2026-12-10" to 3, "2026-12-11" to 4, "2026-12-14" to 5, "2026-12-15" to 6,
        "2026-12-16" to 1, "2026-12-17" to 2, "2026-12-18" to 3, "2026-12-21" to 4, "2026-12-22" to 5, "2027-01-04" to 6,
        "2027-01-25" to 1, "2027-01-26" to 2, "2027-01-27" to 3, "2027-01-28" to 4, "2027-01-29" to 5, "2027-02-01" to 6,
        "2027-02-12" to 1, "2027-02-15" to 2, "2027-02-16" to 3, "2027-02-17" to 4, "2027-02-18" to 5, "2027-02-19" to 6,
        "2027-02-22" to 1, "2027-02-23" to 2, "2027-02-24" to 3, "2027-02-25" to 4, "2027-02-26" to 5, "2027-03-01" to 6,
        "2027-03-02" to 1, "2027-03-03" to 2, "2027-03-04" to 3, "2027-03-05" to 4, "2027-03-08" to 5, "2027-03-09" to 6,
        "2027-03-10" to 1, "2027-03-11" to 2, "2027-03-12" to 3, "2027-03-15" to 4, "2027-03-16" to 5, "2027-03-17" to 6,
        "2027-03-18" to 1, "2027-03-19" to 2, "2027-03-22" to 3, "2027-03-23" to 4, "2027-03-24" to 5, "2027-04-06" to 6,
        "2027-04-14" to 1, "2027-04-15" to 2, "2027-04-16" to 3, "2027-04-19" to 4, "2027-04-20" to 5, "2027-04-21" to 6,
        "2027-04-22" to 1, "2027-04-23" to 2, "2027-04-26" to 3, "2027-04-27" to 4, "2027-04-28" to 5, "2027-04-29" to 6,
        "2027-04-30" to 1, "2027-05-03" to 2, "2027-05-04" to 3, "2027-05-05" to 4, "2027-05-06" to 5, "2027-05-07" to 6,
        "2027-05-10" to 1, "2027-05-11" to 2, "2027-05-12" to 3, "2027-05-14" to 4, "2027-05-17" to 5, "2027-05-18" to 6,
        "2027-05-19" to 1, "2027-05-20" to 2, "2027-05-21" to 3, "2027-05-24" to 4, "2027-05-25" to 5, "2027-05-26" to 6,
        "2027-05-27" to 1, "2027-05-28" to 2, "2027-05-31" to 3, "2027-06-01" to 4, "2027-06-02" to 5, "2027-06-03" to 6
    )

    val specialDates: Map<String, String> = mapOf(
        "2026-09-03" to "A", "2026-09-04" to "A", "2026-09-07" to "A", "2026-09-08" to "A",
        "2026-09-09" to "A", "2026-09-10" to "A",
        "2026-09-11" to "B",
        "2026-11-13" to "C",
        "2026-11-27" to "E",
        "2026-12-11" to "C",
        "2027-02-01" to "C",
        "2027-02-15" to "B",
        "2027-03-19" to "B",
        "2027-04-06" to "B",
        "2027-04-30" to "C",
        "2027-05-07" to "D",
        "2027-05-14" to "B",
        "2027-05-18" to "B", "2027-05-19" to "B", "2027-05-20" to "B", "2027-05-21" to "B",
        "2027-05-24" to "B", "2027-05-25" to "B", "2027-05-26" to "B", "2027-05-27" to "B",
        "2027-05-28" to "B", "2027-05-31" to "B", "2027-06-01" to "B", "2027-06-02" to "B",
        "2027-06-03" to "B"
    )
}
