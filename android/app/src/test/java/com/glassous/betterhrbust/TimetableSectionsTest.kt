package com.glassous.betterhrbust

import com.glassous.betterhrbust.core.ui.theme.CourseColorPalette
import com.glassous.betterhrbust.feature.timetable.BaseSlots
import com.glassous.betterhrbust.feature.timetable.CombineSlots
import com.glassous.betterhrbust.feature.timetable.SectionMode
import com.glassous.betterhrbust.feature.timetable.baseRowRangeOf
import com.glassous.betterhrbust.feature.timetable.sectionModeOfStored
import com.glassous.betterhrbust.feature.timetable.sectionOfBasePeriod
import com.glassous.betterhrbust.feature.timetable.slotRangeText
import com.glassous.betterhrbust.feature.timetable.slotsOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TimetableSectionsTest {

    @Test
    fun combineSlotsMatchOfficialTimetable() {
        assertEquals(6, CombineSlots.size)
        assertEquals("08:10", CombineSlots.first().start)
        assertEquals("21:30", CombineSlots.last().end)
        assertEquals("13:30 - 15:10", slotRangeText(CombineSlots[2]))
        assertSame(CombineSlots, slotsOf(SectionMode.COMBINE))
    }

    @Test
    fun baseSlotsMatchOfficialTimetable() {
        assertEquals(12, BaseSlots.size)
        assertEquals("08:10", BaseSlots.first().start)
        assertEquals("21:30", BaseSlots.last().end)
        // 第 1、2 节的课间衔接（09:05 为第 2 节开始）
        assertEquals("09:05", BaseSlots[1].start)
        assertSame(BaseSlots, slotsOf(SectionMode.BASE))
    }

    @Test
    fun baseRowsOfSectionAreContiguousPairs() {
        val covered = mutableListOf<Int>()
        for (section in 1..6) {
            val range = baseRowRangeOf(section)
            assertEquals("大节 $section 应覆盖两个小节", 2, range.count())
            covered += range.toList()
        }
        assertEquals((1..12).toList(), covered)
        assertEquals(1..2, baseRowRangeOf(1))
        assertEquals(11..12, baseRowRangeOf(6))
        // 越界输入收敛到合法区间，避免索引异常
        assertEquals(1..2, baseRowRangeOf(0))
        assertEquals(11..12, baseRowRangeOf(99))
    }

    @Test
    fun basePeriodMapsBackToItsSection() {
        for (period in 1..12) {
            val section = sectionOfBasePeriod(period)
            assertTrue("小节 $period 应落在其大节区间内", period in baseRowRangeOf(section))
        }
        assertEquals(1, sectionOfBasePeriod(1))
        assertEquals(1, sectionOfBasePeriod(2))
        assertEquals(6, sectionOfBasePeriod(11))
        assertEquals(6, sectionOfBasePeriod(12))
    }

    @Test
    fun sectionModeRestoresFromStoredValueWithFallback() {
        // 切换后写回的标识（SectionMode.name）能原样还原，且大小写不敏感
        assertEquals(SectionMode.BASE, sectionModeOfStored(SectionMode.BASE.name))
        assertEquals(SectionMode.BASE, sectionModeOfStored("base"))
        assertEquals(SectionMode.COMBINE, sectionModeOfStored("COMBINE"))
        // 首次使用（键缺失）或写入异常值：回落为大节，保证课表仍可正常渲染
        assertEquals(SectionMode.COMBINE, sectionModeOfStored(null))
        assertEquals(SectionMode.COMBINE, sectionModeOfStored("unknown"))
    }

    @Test
    fun courseHuesFollowGoldenAngleAndStayStable() {
        val first = CourseColorPalette.hueOf("数据结构与算法")

        // 重复注册不得改变已分配颜色
        CourseColorPalette.register(listOf("数据结构与算法", "高等数学", "大学物理"))
        CourseColorPalette.register(listOf("数据结构与算法", "离散数学"))
        assertEquals(first, CourseColorPalette.hueOf("数据结构与算法"))

        // 黄金角色相展开：不同课程颜色互不相同，且全部落在合法色相区间
        val hues = listOf("数据结构与算法", "高等数学", "大学物理", "离散数学")
            .map { CourseColorPalette.hueOf(it) }
        assertEquals(hues.size, hues.toSet().size)
        assertTrue(hues.all { it in 0..359 })

        // 主键为空时使用 Web 同款兜底色相
        assertEquals(220, CourseColorPalette.hueOf(""))
    }

    @Test
    fun courseKeyPrefersNameAndIgnoresWhitespace() {
        assertEquals("数据结构", CourseColorPalette.keyOf(" 数据 结构 ", courseSeq = "001"))
        assertEquals("001", CourseColorPalette.keyOf("", courseSeq = "001", id = "9"))
        assertEquals("9", CourseColorPalette.keyOf(null, null, null, "9"))
        assertEquals("", CourseColorPalette.keyOf(null, null, null, null))
        // 名称与课序号中的空白不影响取色主键，保证同名课程颜色一致
        assertEquals(
            CourseColorPalette.keyOf("数据结构与算法"),
            CourseColorPalette.keyOf("数 据结构 与算法")
        )
    }
}
