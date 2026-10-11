package com.glassous.betterhrbust

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.core.app.ActivityScenario
import com.glassous.betterhrbust.core.database.ProfileEntity
import com.glassous.betterhrbust.core.database.TimetableEntity
import com.glassous.betterhrbust.core.model.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assume.assumeTrue
import org.junit.Assert.*
import java.time.LocalDate
import android.view.MotionEvent
import android.os.SystemClock
import android.content.Intent
import java.io.File

@RunWith(AndroidJUnit4::class)
class NativePreviewTest {
    @Test fun captureActualNativeTimetableAndVerifyCourseTapReturnsToItsColor() = runBlocking {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        assumeTrue(context.packageName.endsWith(".widgetpreview"))
        val app=context.applicationContext as BetterHrbustApp
        val old=app.preferencesManager.preferencesFlow.first()
        assumeTrue(old.username.isBlank() && old.studentId.isBlank())
        val number="9901234567";val sid="native-preview-only"
        val directory=File(context.getExternalFilesDir(null),"native-previews").apply { mkdirs() }
        val names=listOf("数据结构","计算机网络","软件工程","数据库系统")
        fun tap(x: Float,y: Float) {
            val now=SystemClock.uptimeMillis()
            for(action in listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP)) {
                val event=MotionEvent.obtain(now,SystemClock.uptimeMillis(),action,x,y,0)
                instrumentation.uiAutomation.injectInputEvent(event,true);event.recycle()
                Thread.sleep(70)
            }
        }
        fun screenshot(name: String): android.graphics.Bitmap {
            val bitmap=instrumentation.uiAutomation.takeScreenshot() ?: error("无法截取真实界面")
            File(directory,name).outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
            return bitmap
        }
        fun findText(node: android.view.accessibility.AccessibilityNodeInfo?, query: String): android.view.accessibility.AccessibilityNodeInfo? {
            if(node==null)return null
            if(node.text?.toString()?.contains(query)==true || node.contentDescription?.toString()?.contains(query)==true)return node
            for(index in 0 until node.childCount) {
                val found=findText(node.getChild(index),query)
                if(found!=null)return found
            }
            return null
        }
        try {
            val cells=(1..7).flatMap { day->listOf(1,3).mapIndexed { index,section->
                TimetableCell("$day-$section","COURSE$index",day,section,"第${section}大节",names[(day+index)%names.size],"1","西-新D510","林老师","1-18周","")
            } }
            app.database.profileDao().insert(ProfileEntity(number,Json.encodeToString(PersonalInfo(studentNumber=number,realName="林同学"))))
            app.database.timetableDao().insert(TimetableEntity(sid,Json.encodeToString(TimetableResult(cells))))
            app.preferencesManager.saveAuth(number,sid,"2026","1")
            app.preferencesManager.setCurrentWeek(6,teachingWeeks=20)
            app.preferencesManager.setLastFullSyncDate(LocalDate.now().toString())
            for(mode in listOf("light","dark")) {
                app.preferencesManager.setDarkTheme(mode)
                val intent=Intent(context,MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_TAB,MainActivity.TAB_TIMETABLE)
                ActivityScenario.launch<MainActivity>(intent).use { scenario ->
                    instrumentation.waitForIdleSync();Thread.sleep(800)
                    var root=instrumentation.uiAutomation.rootInActiveWindow
                    val deadline=SystemClock.elapsedRealtime()+30000
                    while((root?.packageName?.toString()!=context.packageName || findText(root,"第 6 周")==null) && SystemClock.elapsedRealtime()<deadline) {
                        root?.findAccessibilityNodeInfosByText("Wait")?.firstOrNull()?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
                        Thread.sleep(400)
                        root=instrumentation.uiAutomation.rootInActiveWindow
                    }
                    assertEquals(context.packageName,root?.packageName?.toString())
                    fun texts(node: android.view.accessibility.AccessibilityNodeInfo?): String {
                        if(node==null)return ""
                        return listOfNotNull(node.text?.toString(),node.contentDescription?.toString()).joinToString(" ")+"\n"+(0 until node.childCount).joinToString("\n"){texts(node.getChild(it))}
                    }
                    File(directory,"native-ui-$mode.txt").writeText(texts(root))
                    screenshot("android-$mode.png").recycle()
                    assertTrue("组件跳转应定位第6周: ${texts(root)}",findText(root,"第 6 周")!=null)
                    val node=names.asSequence().mapNotNull { findText(root,it) }.firstOrNull() ?: error("课表课程未显示")
                    val bounds=android.graphics.Rect();node.getBoundsInScreen(bounds)
                    val before=screenshot("android-$mode.png")
                    val x=(bounds.right-6).coerceIn(0,before.width-1);val y=bounds.centerY().coerceIn(0,before.height-1)
                    val baseline=before.getPixel(x,y);before.recycle()
                    tap(bounds.exactCenterX(),bounds.exactCenterY());Thread.sleep(350)
                    // 点遮罩关闭详情，然后再次打开/关闭，验证按压反馈不会残留蓝色。
                    repeat(2) {
                        var screen=android.graphics.Rect();scenario.onActivity { it.window.decorView.getGlobalVisibleRect(screen) }
                        tap(screen.left+12f,screen.exactCenterY());Thread.sleep(400)
                        val settled=SystemClock.elapsedRealtime()+3000
                        var distance=Int.MAX_VALUE
                        do {
                            val image=screenshot("after-close-$mode.png")
                            val actual=image.getPixel(x,y);image.recycle()
                            distance=listOf(android.graphics.Color.red(actual)-android.graphics.Color.red(baseline),
                                android.graphics.Color.green(actual)-android.graphics.Color.green(baseline),
                                android.graphics.Color.blue(actual)-android.graphics.Color.blue(baseline)).maxOf { kotlin.math.abs(it) }
                            if(distance>2)Thread.sleep(150)
                        } while(distance>2 && SystemClock.elapsedRealtime()<settled)
                        // GPU 混合可有1级色差；持续蓝色反馈会远超此容差。
                        assertTrue("详情关闭后应恢复原课程配色，最大通道差=$distance",distance<=2)
                        if(it==0){tap(bounds.exactCenterX(),bounds.exactCenterY());Thread.sleep(350)}
                    }
                    var live=instrumentation.uiAutomation.rootInActiveWindow
                    val next=findText(live,"下一周") ?: error("下一周按钮未显示")
                    val nextBounds=android.graphics.Rect();next.getBoundsInScreen(nextBounds)
                    tap(nextBounds.exactCenterX(),nextBounds.exactCenterY())
                    Thread.sleep(500)
                    live=instrumentation.uiAutomation.rootInActiveWindow
                    assertNotNull("应能浏览第7周",findText(live,"第 7 周"))
                    scenario.onActivity { it.startActivity(Intent(it,MainActivity::class.java)
                        .putExtra(MainActivity.EXTRA_OPEN_TAB,MainActivity.TAB_TIMETABLE)
                        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)) }
                    val returnDeadline=SystemClock.elapsedRealtime()+4000
                    do {
                        Thread.sleep(150)
                        live=instrumentation.uiAutomation.rootInActiveWindow
                    } while(findText(live,"第 6 周")==null && SystemClock.elapsedRealtime()<returnDeadline)
                    assertNotNull("已有实例再次从组件进入也必须恢复第6周",findText(live,"第 6 周"))
                    val week=findText(live,"第 6 周")!!
                    val weekBounds=android.graphics.Rect();week.getBoundsInScreen(weekBounds)
                    tap(weekBounds.exactCenterX(),weekBounds.exactCenterY());Thread.sleep(500)
                    assertNotNull("周次选择应按官方校历列19周",findText(instrumentation.uiAutomation.rootInActiveWindow,"共 19 周"))
                }
            }
        } finally {
            app.preferencesManager.saveAuth(old.username,"",old.year,old.term,old.realName,old.savedPassword)
            app.preferencesManager.clearSession();app.preferencesManager.setDarkTheme(when(old.darkTheme){true->"dark";false->"light";null->"system"})
            app.database.profileDao().clear(number);app.database.timetableDao().clear(sid)
        }
    }
}
