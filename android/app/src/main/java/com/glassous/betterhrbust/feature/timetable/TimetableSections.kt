package com.glassous.betterhrbust.feature.timetable

/**
 * 课表节次模式：大节（6 节）/ 小节（12 节），与 Web 端 COMBINE / BASE 模式对齐。
 */
enum class SectionMode(val label: String) {
    COMBINE("大节"),
    BASE("小节")
}

/** 单一节次的起止时间。 */
data class TimeSlot(
    val period: Int,
    val start: String,
    val end: String
)

/** 大节起止时间（对齐 Web `COMBINE_SLOT_TIMES` / 学校官方作息表）。 */
val CombineSlots = listOf(
    TimeSlot(1, "08:10", "09:50"),
    TimeSlot(2, "10:10", "11:50"),
    TimeSlot(3, "13:30", "15:10"),
    TimeSlot(4, "15:30", "17:10"),
    TimeSlot(5, "18:00", "19:40"),
    TimeSlot(6, "19:50", "21:30")
)

/** 小节起止时间（对齐 Web `BASE_SLOT_TIMES` / 学校官方作息表）。 */
val BaseSlots = listOf(
    TimeSlot(1, "08:10", "08:55"),
    TimeSlot(2, "09:05", "09:50"),
    TimeSlot(3, "10:10", "10:55"),
    TimeSlot(4, "11:05", "11:50"),
    TimeSlot(5, "13:30", "14:15"),
    TimeSlot(6, "14:25", "15:10"),
    TimeSlot(7, "15:30", "16:15"),
    TimeSlot(8, "16:25", "17:10"),
    TimeSlot(9, "18:00", "18:45"),
    TimeSlot(10, "18:55", "19:40"),
    TimeSlot(11, "19:50", "20:35"),
    TimeSlot(12, "20:45", "21:30")
)

/** 一学期最大教学周数（与 Web 周选择保持一致）。 */
const val MaxTeachingWeek = 26

val DayNames = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

/** 星期单选用的紧凑文案（周一 → 一）。 */
val DayShortNames = listOf("一", "二", "三", "四", "五", "六", "日")

fun slotsOf(mode: SectionMode): List<TimeSlot> = when (mode) {
    SectionMode.COMBINE -> CombineSlots
    SectionMode.BASE -> BaseSlots
}

/**
 * 解析持久化的节次模式（[SectionMode.name]，如 `COMBINE` / `BASE`）。
 *
 * 大小写不敏感；缺失或未知值回落为大节，保证旧数据 / 手工写入的异常值能够优雅降级。
 */
fun sectionModeOfStored(value: String?): SectionMode =
    SectionMode.entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
        ?: SectionMode.COMBINE

/**
 * 大节 → 覆盖的小节行区间（1-based，闭区间）。
 *
 * 教务数据只到大节粒度（`TimetableCell.sectionIndex` 1..6），小节模式下次序为 s 的大节
 * 占第 2s-1、2s 两行，课程块跨两行显示。
 */
fun baseRowRangeOf(sectionIndex: Int): IntRange {
    val start = (sectionIndex.coerceIn(1, CombineSlots.size) - 1) * 2 + 1
    return start..(start + 1)
}

/** 小节序号（1..12）所属的大节序号（1..6）。 */
fun sectionOfBasePeriod(period: Int): Int = (period.coerceIn(1, BaseSlots.size) + 1) / 2

/** 节次时间展示文案，如 `13:30 - 15:10`。 */
fun slotRangeText(slot: TimeSlot): String = "${slot.start} - ${slot.end}"
