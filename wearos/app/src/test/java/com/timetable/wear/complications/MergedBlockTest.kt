package com.timetable.wear.complications

import com.timetable.wear.data.model.PeriodInfo
import com.timetable.wear.data.model.ScheduleItem
import com.timetable.wear.data.model.ScheduleItemType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MergedBlockTest {

    private fun period(
        number: Int,
        subject: String,
        start: String,
        end: String
    ) = ScheduleItem(
        type = ScheduleItemType.PERIOD,
        periodNumber = number,
        displayName = "第${number}節",
        subject = subject,
        start = start,
        end = end
    )

    private fun current(
        subject: String,
        start: String,
        end: String,
        type: PeriodInfo.PeriodType = PeriodInfo.PeriodType.PERIOD
    ) = PeriodInfo(type = type, name = "", start = start, end = end, subject = subject)

    // ICT,ICT,CHIN,MACO,MACO,CS,ENG with contiguous times.
    private fun dayItems(): List<ScheduleItem> = listOf(
        period(1, "ICT WKC 316", "08:30", "09:05"),
        period(2, "ICT WKC 316", "09:05", "09:40"),
        period(3, "CHIN NKT 401", "10:00", "10:35"),
        period(4, "MACO YPC 401", "11:25", "12:00"),
        period(5, "MACO YPC 401", "12:00", "12:35"),
        period(6, "CS LPY 401", "13:45", "14:20"),
        period(7, "ENG KKY 401", "14:20", "14:55")
    )

    @Test
    fun `first of double extends to block end`() {
        val block = mergedPeriodBlock(dayItems(), current("ICT WKC 316", "08:30", "09:05"), merge = true)
        assertEquals(MergedBlock("第1-2節", "08:30", "09:40"), block)
    }

    @Test
    fun `second of double extends to same block`() {
        val block = mergedPeriodBlock(dayItems(), current("ICT WKC 316", "09:05", "09:40"), merge = true)
        assertEquals(MergedBlock("第1-2節", "08:30", "09:40"), block)
    }

    @Test
    fun `lone period returns null`() {
        assertNull(mergedPeriodBlock(dayItems(), current("CHIN NKT 401", "10:00", "10:35"), merge = true))
    }

    @Test
    fun `merge off returns null`() {
        assertNull(mergedPeriodBlock(dayItems(), current("ICT WKC 316", "08:30", "09:05"), merge = false))
    }

    @Test
    fun `break and free never merge`() {
        assertNull(
            mergedPeriodBlock(
                dayItems(), current("小息", "09:40", "10:00", PeriodInfo.PeriodType.BREAK_TIME), merge = true
            )
        )
        assertNull(
            mergedPeriodBlock(
                dayItems(), current("", "", "", PeriodInfo.PeriodType.FREE), merge = true
            )
        )
    }

    @Test
    fun `different subjects do not merge across the boundary`() {
        // 2nd ICT ends 09:40, 3rd CHIN starts 10:00: gap + different subject.
        val block = mergedPeriodBlock(dayItems(), current("ICT WKC 316", "09:05", "09:40"), merge = true)
        assertEquals("09:40", block?.end)
    }
}
