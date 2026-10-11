package com.glassous.betterhrbust.core.util

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** 由 shared/school-calendar.json 生成；更新校历时运行 tools/generate-school-calendar.mjs。 */
object SchoolCalendar {
    const val SOURCE = "http://jwzx.hrbust.edu.cn/homepage/infoSingleArticle.do?articleId=416"
    data class Semester(val label: String, val start: LocalDate, val teachingEnd: LocalDate, val periodEnd: LocalDate, val weeks: Int)
    private val semesters = listOf(
        Semester("2026 秋", LocalDate.parse("2026-08-31"), LocalDate.parse("2027-01-10"), LocalDate.parse("2027-02-24"), 19),
        Semester("2027 春", LocalDate.parse("2027-03-01"), LocalDate.parse("2027-07-11"), LocalDate.parse("2027-08-25"), 19)
    )
    fun semesterOn(date: LocalDate): Semester? = semesters.firstOrNull { !date.isBefore(it.start) && !date.isAfter(it.periodEnd) }
    fun weekOn(date: LocalDate): Int? {
        val semester = semesterOn(date) ?: return null
        if (date.isAfter(semester.teachingEnd)) return null
        return (ChronoUnit.DAYS.between(semester.start, date) / 7 + 1).toInt()
    }
}
