package com.glassous.betterhrbust.notification

import com.glassous.betterhrbust.widget.WidgetCourse
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime

/** 通知只跟踪临近或正在进行的课；结束即撤销，不将整天课表常驻上岛。 */
data class CourseLiveState(
    val course: WidgetCourse,
    val startsAt: LocalDateTime,
    val endsAt: LocalDateTime,
    val ongoing: Boolean
) {
    val boundary: LocalDateTime get() = if (ongoing) endsAt else startsAt
    fun remainingMinutes(now: LocalDateTime): Long =
        ((Duration.between(now, boundary).seconds.coerceAtLeast(0) + 59) / 60)
    val title: String get() = if (ongoing) "正在上课 · ${course.name}" else "即将上课 · ${course.name}"
}

data class CourseNotificationPlan(val state: CourseLiveState?, val nextRefresh: LocalDateTime)

object CourseLivePlanner {
    const val REMINDER_MINUTES = 15L

    fun plan(courses: List<WidgetCourse>, now: LocalDateTime): CourseNotificationPlan {
        val candidates = courses.mapNotNull { course ->
            runCatching {
                val parts = course.time.split("–")
                val start = now.toLocalDate().atTime(LocalTime.parse(parts[0]))
                val end = now.toLocalDate().atTime(LocalTime.parse(parts[1]))
                if (!end.isAfter(start) || !now.isBefore(end)) null else
                    CourseLiveState(course, start, end, !now.isBefore(start))
            }.getOrNull()
        }.sortedWith(compareByDescending<CourseLiveState> { it.ongoing }.thenBy { it.startsAt }.thenBy { it.course.name })
        val state = candidates.firstOrNull { !now.isBefore(it.startsAt.minusMinutes(REMINDER_MINUTES)) }
        val boundaries = candidates.flatMap { listOf(it.startsAt.minusMinutes(REMINDER_MINUTES), it.startsAt, it.endsAt) }
            .filter { it.isAfter(now) }
        val next = boundaries.minOrNull() ?: now.toLocalDate().plusDays(1).atStartOfDay()
        return CourseNotificationPlan(state, next)
    }
}
