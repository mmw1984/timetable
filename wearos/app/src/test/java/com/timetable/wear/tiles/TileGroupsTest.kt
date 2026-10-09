package com.timetable.wear.tiles

import com.timetable.wear.data.local.DenseLayoutMode
import com.timetable.wear.data.local.resolveDenseLayout
import com.timetable.wear.data.model.ScheduleItem
import com.timetable.wear.data.model.ScheduleItemType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TileGroupsTest {

    // Day-2-like periods: ICT,ICT,CHIN,CHIN,MACO,MACO,CS,ENG (contiguous times)
    private fun day2Periods(): List<ScheduleItem> {
        val subjects = listOf("ICT WKC 316", "ICT WKC 316", "CHIN NKT 401", "CHIN NKT 401", "MACO YPC 401", "MACO YPC 401", "CS LPY 401", "ENG KKY 401")
        val starts = listOf("08:30", "09:05", "10:00", "10:35", "11:25", "12:00", "13:45", "14:20")
        val ends = listOf("09:05", "09:40", "10:35", "11:10", "12:00", "12:35", "14:20", "14:55")
        return subjects.mapIndexed { i, subject ->
            ScheduleItem(
                type = ScheduleItemType.PERIOD,
                periodNumber = i + 1,
                displayName = "第${i + 1}節",
                subject = subject,
                start = starts[i],
                end = ends[i]
            )
        }
    }

    @Test
    fun `merged day with doubles collapses to 5 groups`() {
        val groups = tileGroups(day2Periods(), merge = true)
        assertEquals(5, groups.size)
        assertEquals("第1-2節", groups[0].displayName)
        assertEquals("第3-4節", groups[1].displayName)
        assertEquals("第5-6節", groups[2].displayName)
        assertEquals("第7節", groups[3].displayName)
        assertEquals("第8節", groups[4].displayName)
    }

    @Test
    fun `unmerged day keeps all 8 periods`() {
        assertEquals(8, tileGroups(day2Periods(), merge = false).size)
    }

    @Test
    fun `breaks and assembly never become groups`() {
        val items = day2Periods() + ScheduleItem(ScheduleItemType.BREAK_TIME, null, "午餐", "午餐", "12:35", "13:40")
        assertEquals(5, tileGroups(items, merge = true).size)
    }

    @Test
    fun `compact labels read like 1-2 ICT`() {
        val groups = tileGroups(day2Periods(), merge = true)
        assertEquals("1-2 ICT", compactRowLabel(groups[0]))
        assertEquals("7 CS", compactRowLabel(groups[3]))
        assertEquals("8 ENG", compactRowLabel(groups[4]))
    }

    @Test
    fun `odd count 5 leaves a lone orphan group, not a crash`() {
        // 2+2+1 chunking must hold: last row has exactly one item
        val rows = tileGroups(day2Periods(), merge = true).chunked(2)
        assertEquals(listOf(2, 2, 1), rows.map { it.size })
    }

    @Test
    fun `dense mode on forces dense, off forces standard`() {
        assertTrue(resolveDenseLayout(DenseLayoutMode.ON, 999, 999))
        assertFalse(resolveDenseLayout(DenseLayoutMode.OFF, 0, 0))
    }

    @Test
    fun `dense auto follows small screens`() {
        assertTrue(resolveDenseLayout(DenseLayoutMode.AUTO, 192, 200))
        assertTrue(resolveDenseLayout(DenseLayoutMode.AUTO, 225, 200))
        assertFalse(resolveDenseLayout(DenseLayoutMode.AUTO, 225, 260))
    }

    @Test
    fun `compact budget leaves room for the overflow pill`() {
        // small round ~192dp screen: 2 rows (1 group + "+N 更多")
        assertEquals(2, compactRowBudget(192))
        // large round ~227dp screen: 3 rows
        assertEquals(3, compactRowBudget(227))
        // unknown height falls back safely instead of clipping everything
        assertEquals(2, compactRowBudget(0))
    }
}
