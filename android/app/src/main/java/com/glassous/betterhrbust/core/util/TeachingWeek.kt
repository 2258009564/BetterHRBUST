package com.glassous.betterhrbust.core.util

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** 周一推进教学周；仅依据有日期的学校同步结果，不猜测学期起点。 */
object TeachingWeek {
    fun mondayForWeek(referenceWeek: Int, referenceDate: String, selectedWeek: Int): LocalDate? {
        if (referenceWeek !in 1..26 || selectedWeek !in 1..26) return null
        val reference = runCatching { LocalDate.parse(referenceDate) }.getOrNull() ?: return null
        return reference.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .plusWeeks((selectedWeek - referenceWeek).toLong())
    }

    fun resolve(referenceWeek: Int, referenceDate: String, date: LocalDate): Int? {
        if (referenceWeek !in 1..26) return null
        val reference = runCatching { LocalDate.parse(referenceDate) }.getOrNull() ?: return null
        if (date.isBefore(reference)) return null
        val monday = TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)
        val elapsed = ChronoUnit.WEEKS.between(reference.with(monday), date.with(monday)).toInt()
        return (referenceWeek + elapsed).takeIf { it in 1..26 }
    }
}
