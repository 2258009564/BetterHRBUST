package com.glassous.betterhrbust

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.core.app.ActivityScenario
import androidx.core.app.FrameMetricsAggregator
import com.glassous.betterhrbust.core.database.ProfileEntity
import com.glassous.betterhrbust.core.database.TimetableEntity
import com.glassous.betterhrbust.core.model.*
import com.glassous.betterhrbust.widget.TimetableWidgetProvider
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assume.assumeTrue
import java.time.LocalDate
import android.view.MotionEvent
import android.os.SystemClock
import android.content.Intent
import java.io.File

@RunWith(AndroidJUnit4::class)
class TimetableSwipeTest {
    @Test fun profileSwipeFramesOnAnOfflineFixture() = runBlocking {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        assumeTrue(context.packageName.endsWith(".widgetpreview"))
        val app=context.applicationContext as BetterHrbustApp
        val old=app.preferencesManager.preferencesFlow.first()
        assumeTrue(old.username.isBlank() && old.studentId.isBlank())
        val number="9900000033";val sid="timetable-swipe-fixture"
        var scenario: ActivityScenario<MainActivity>?=null
        try {
            val cells=(1..7).flatMap { day -> (1..5).map { section ->
                TimetableCell("$day-$section","$day-$section",day,section,"第${section}大节","课程${day}-${section}","1","D510","教师","1-18周","")
            } }
            app.database.profileDao().insert(ProfileEntity(number,Json.encodeToString(PersonalInfo(studentNumber=number,realName="Swipe fixture"))))
            app.database.timetableDao().insert(TimetableEntity(sid,Json.encodeToString(TimetableResult(cells))))
            app.preferencesManager.saveAuth(number,sid,"2026","1")
            app.preferencesManager.setCurrentWeek(6)
            app.preferencesManager.setLastFullSyncDate(LocalDate.now().toString())
            scenario=ActivityScenario.launch(Intent(context,MainActivity::class.java).putExtra(TimetableWidgetProvider.OPEN_TIMETABLE,true))
            instrumentation.waitForIdleSync();Thread.sleep(1000)
            repeat(10) {
                val root=instrumentation.uiAutomation.rootInActiveWindow
                if(root?.packageName?.toString()!=context.packageName) {
                    root?.findAccessibilityNodeInfosByText("Wait")?.firstOrNull()?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
                    Thread.sleep(500)
                }
            }
            instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                File(context.getExternalFilesDir(null),"swipe-screen.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
                bitmap.recycle()
            }
            var bounds=android.graphics.Rect()
            scenario.onActivity { it.window.decorView.getGlobalVisibleRect(bounds) }
            val width=bounds.width().toFloat()
            val y=bounds.top+bounds.height()*0.40f
            fun swipe() {
                val start=SystemClock.uptimeMillis()
                for (i in 0..24) {
                    val action=when(i){0->MotionEvent.ACTION_DOWN;24->MotionEvent.ACTION_UP;else->MotionEvent.ACTION_MOVE}
                    val event=MotionEvent.obtain(start,SystemClock.uptimeMillis(),action,bounds.left+width*(0.88f-0.76f*i/24),y,0)
                    instrumentation.uiAutomation.injectInputEvent(event,true);event.recycle();Thread.sleep(12)
                }
                Thread.sleep(250)
            }
            repeat(2){swipe()}
            val aggregator=FrameMetricsAggregator()
            scenario.onActivity { aggregator.add(it) }
            repeat(6){swipe()}
            instrumentation.waitForIdleSync()
            scenario.onActivity { aggregator.remove(it) }
            val histogram=aggregator.metrics?.get(0)
            val values=mutableListOf<Int>()
            if(histogram!=null)for(i in 0 until histogram.size()) repeat(histogram.valueAt(i)){values.add(histogram.keyAt(i))}
            values.sort()
            val report="frames=${values.size} medianMs=${values.getOrNull(values.size/2)} p90Ms=${values.getOrNull((values.size*0.9).toInt())} slow34=${values.count{it>=34}}"
            File(context.getExternalFilesDir(null),"swipe-profile.txt").writeText(report)
            org.junit.Assert.assertTrue("必须采集到真实窗口帧",values.isNotEmpty())
        } finally {
            scenario?.close()
            app.preferencesManager.saveAuth(old.username,"",old.year,old.term,old.realName,old.savedPassword)
            app.preferencesManager.clearSession()
            app.database.profileDao().clearAll()
            app.database.timetableDao().clear(sid)
        }
    }
}

