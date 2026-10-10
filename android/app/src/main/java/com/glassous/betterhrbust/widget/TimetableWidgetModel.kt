package com.glassous.betterhrbust.widget

import com.glassous.betterhrbust.core.model.TimetableCell
import com.glassous.betterhrbust.core.parser.AcademicParsers
import com.glassous.betterhrbust.feature.timetable.CombineSlots
import java.time.LocalDate
import java.time.LocalTime

data class WidgetCourse(val name: String, val location: String, val time: String, val ongoing: Boolean, val colorKey: String = name)

/** 数据与布局解耦；只读已验证账号的离线课表，不在桌面后台请求教务。 */
data class WidgetDayProgress(val remaining: List<WidgetCourse>,val completed: Int,val total: Int)

object TimetableWidgetModel {
    fun progress(courses: List<WidgetCourse>, now: LocalTime): WidgetDayProgress {
        val remaining=courses.filter { now.isBefore(LocalTime.parse(it.time.substringAfter("–"))) }
        return WidgetDayProgress(remaining,courses.size-remaining.size,courses.size)
    }

    fun teachingWeek(referenceWeek: Int, referenceDate: String, today: LocalDate): Int? =
        com.glassous.betterhrbust.core.util.TeachingWeek.resolve(referenceWeek, referenceDate, today)

    fun dayCourses(cells: List<TimetableCell>, week: Int, today: LocalDate, now: LocalTime? = null): List<WidgetCourse> =
        cells.filter { it.day == today.dayOfWeek.value && AcademicParsers.isCourseActiveInWeek(it.weeks, week) }
            .sortedWith(compareBy<TimetableCell> { it.sectionIndex }.thenBy { it.courseName })
            .mapNotNull { cell ->
                val slot = CombineSlots.getOrNull(cell.sectionIndex - 1) ?: return@mapNotNull null
                WidgetCourse(cell.courseName, cell.location.ifBlank { "地点待公布" },
                    "${slot.start}–${slot.end}", now != null && !now.isBefore(LocalTime.parse(slot.start)) && now.isBefore(LocalTime.parse(slot.end)), com.glassous.betterhrbust.core.ui.theme.CourseColorPalette.keyOf(cell.courseName,cell.courseSeq,cell.courseId,cell.id))
            }
    fun remainingCourses(cells: List<TimetableCell>, week: Int, today: LocalDate, now: LocalTime): List<WidgetCourse> =
        dayCourses(cells, week, today, now).filter { now.isBefore(LocalTime.parse(it.time.substringAfter("–"))) }
}

