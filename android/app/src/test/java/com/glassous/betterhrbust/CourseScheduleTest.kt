package com.glassous.betterhrbust
import com.glassous.betterhrbust.core.util.CourseSchedule
import java.time.LocalTime
import org.junit.Assert.*
import org.junit.Test
class CourseScheduleTest {
    @Test fun courseIsFinishedFromItsEndTime() {
        assertFalse(CourseSchedule.hasEnded(1, LocalTime.parse("09:49")))
        assertTrue(CourseSchedule.hasEnded(1, LocalTime.parse("09:50")))
        assertTrue((1..6).all { CourseSchedule.hasEnded(it, LocalTime.parse("21:30")) })
    }
}
