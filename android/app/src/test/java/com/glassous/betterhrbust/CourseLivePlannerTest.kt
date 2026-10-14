package com.glassous.betterhrbust

import com.glassous.betterhrbust.notification.CourseLivePlanner
import com.glassous.betterhrbust.widget.WidgetCourse
import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Test

class CourseLivePlannerTest {
    private val course = WidgetCourse("数据结构", "D510", "10:10–11:50", false)
    private fun at(time: String) = LocalDateTime.parse("2026-10-12T$time")
    @Test fun onlyImminentAndActiveClassesAppear() {
        assertNull(CourseLivePlanner.plan(listOf(course), at("09:54:59")).state)
        val imminent = CourseLivePlanner.plan(listOf(course), at("09:55:00"))
        assertFalse(imminent.state!!.ongoing)
        assertEquals(at("10:10:00"), imminent.nextRefresh)
        assertEquals(15L, imminent.state!!.remainingMinutes(at("09:55:01")))
        val active = CourseLivePlanner.plan(listOf(course), at("10:10:00"))
        assertTrue(active.state!!.ongoing)
        assertEquals(at("11:50:00"), active.nextRefresh)
        assertNull(CourseLivePlanner.plan(listOf(course), at("11:50:00")).state)
    }
    @Test fun ongoingClassWinsAndInvalidSlotsDoNotCrash() {
        val next=WidgetCourse("下一课", "D502", "11:45–13:00", false)
        val broken=WidgetCourse("坏数据", "", "未知", false)
        assertEquals(course, CourseLivePlanner.plan(listOf(next,broken,course),at("11:40:00")).state!!.course)
        assertNull(CourseLivePlanner.plan(listOf(broken),at("11:40:00")).state)
    }
    @Test fun xiaomiProtocolPayloadUsesRequiredFields() {
        val state=CourseLivePlanner.plan(listOf(course),at("10:10:00")).state!!
        val os2=kotlinx.serialization.json.Json.parseToJsonElement(com.glassous.betterhrbust.notification.CourseNotification.xiaomiPayload(state,at("10:10:00"),2)) as kotlinx.serialization.json.JsonObject
        val data=os2["param_v2"] as kotlinx.serialization.json.JsonObject
        assertNotNull(data["baseInfo"])
        assertNull(data["param_island"])
        val os3=kotlinx.serialization.json.Json.parseToJsonElement(com.glassous.betterhrbust.notification.CourseNotification.xiaomiPayload(state,at("10:10:00"),3)) as kotlinx.serialization.json.JsonObject
        val island=(os3["param_v2"] as kotlinx.serialization.json.JsonObject)["param_island"] as kotlinx.serialization.json.JsonObject
        assertNotNull(island["bigIslandArea"]);assertNotNull(island["smallIslandArea"])
    }
    @Test fun emptyDaySchedulesMidnightWithoutPosting() {
        val plan=CourseLivePlanner.plan(emptyList(),at("12:00:00"))
        assertNull(plan.state)
        assertEquals(LocalDateTime.parse("2026-10-13T00:00:00"),plan.nextRefresh)
    }
}
