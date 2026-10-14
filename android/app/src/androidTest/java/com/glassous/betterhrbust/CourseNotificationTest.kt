package com.glassous.betterhrbust

import android.app.Notification
import android.app.NotificationManager
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.glassous.betterhrbust.notification.*
import com.glassous.betterhrbust.widget.WidgetCourse
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime

@RunWith(AndroidJUnit4::class)
class CourseNotificationTest {
    @Test fun standardNotificationIsPromotableAndCancelWorksWithoutXiaomi() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue(context.packageName.endsWith(".widgetpreview"))
        val manager=context.getSystemService(NotificationManager::class.java)
        assumeTrue(manager.areNotificationsEnabled())
        // 独立测试通知 ID，避免应用自身的未登录清理任务撤销构建器测试通知。
        val testId=CourseNotification.ID+1
        val now=LocalDateTime.now()
        val state=CourseLiveState(WidgetCourse("通知测试课程","测试教室","10:10–11:50",true),now.minusMinutes(2),now.plusMinutes(3),true)
        CourseNotification.createChannel(context)
        val notification=CourseNotification.build(context,state,now,LiveNotificationSupport(0,false,Build.VERSION.SDK_INT>=36))
        assertNull(notification.extras.getString("miui.focus.param"))
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertNotNull(notification.contentIntent)
        assertNotNull(notification.deleteIntent)
        assertNull(notification.contentView)
        if(Build.VERSION.SDK_INT>=36) assertTrue(notification.hasPromotableCharacteristics())
        try {
            manager.notify(testId,notification)
            Thread.sleep(500)
            assertTrue(manager.activeNotifications.any { it.id==testId })
        } finally { manager.cancel(testId) }
        Thread.sleep(200)
        assertFalse(manager.activeNotifications.any { it.id==testId })
    }
    @Test fun unverifiedIdentityNeverPostsCourseDetails() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue(context.packageName.endsWith(".widgetpreview"))
        val app=context.applicationContext as BetterHrbustApp
        val old=app.preferencesManager.preferencesFlow.first()
        assumeTrue(old.username.isBlank() && old.studentId.isBlank())
        try {
            app.preferencesManager.setCourseNotifications(true)
            CourseNotificationController.refresh(context)
            assertFalse(context.getSystemService(NotificationManager::class.java).activeNotifications.any { it.id==CourseNotification.ID })
        } finally { app.preferencesManager.setCourseNotifications(old.courseNotifications);CourseNotificationController.refresh(context) }
    }
}
