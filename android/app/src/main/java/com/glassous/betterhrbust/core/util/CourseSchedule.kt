package com.glassous.betterhrbust.core.util

import java.time.LocalTime

object CourseSchedule {
    private val ends = listOf("09:50", "11:50", "15:10", "17:10", "19:40", "21:30").map(LocalTime::parse)
    fun hasEnded(section: Int, now: LocalTime): Boolean =
        ends.getOrNull(section - 1)?.let { !now.isBefore(it) } ?: false
}
