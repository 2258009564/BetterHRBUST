package com.glassous.betterhrbust

import androidx.compose.ui.graphics.toArgb

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.glassous.betterhrbust.core.database.ProfileEntity
import com.glassous.betterhrbust.core.database.TimetableEntity
import com.glassous.betterhrbust.core.model.PersonalInfo
import com.glassous.betterhrbust.core.model.TimetableCell
import com.glassous.betterhrbust.core.model.TimetableResult
import com.glassous.betterhrbust.widget.TimetableWidgetProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class WidgetDataUpdateTest {
    /** 仅在未使用过账号的隔离测试包运行；不在正式包覆盖用户数据。 */
    @Test fun roomUpdatesSwitchingAccountsAndLogoutReachTheActualHostWithoutNetwork() = runBlocking {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        assumeTrue(context.packageName.endsWith(".widgetpreview"))
        val app=context.applicationContext as BetterHrbustApp
        val previous=app.preferencesManager.preferencesFlow.first()
        assumeTrue(previous.username.isBlank() && previous.studentId.isBlank())
        val manager=AppWidgetManager.getInstance(context)
        val component=ComponentName(context,TimetableWidgetProvider::class.java)
        val info=manager.installedProviders.single { it.provider==component }
        val host=AppWidgetHost(context,20261011)
        val id=host.allocateAppWidgetId()
        var hostView: AppWidgetHostView?=null
        var activity: androidx.test.core.app.ActivityScenario<com.glassous.betterhrbust.widget.WidgetPreviewHostActivity>?=null
        val a="9901234567"; val b="9907654321"
        val aid="widget-host-test-A"; val bid="widget-host-test-B"
        val oldProfiles=listOf(a,b).associateWith { app.database.profileDao().getProfile(it).first() }
        val oldTables=listOf(aid,bid).associateWith { app.database.timetableDao().getTimetable(it).first() }
        fun text(view: View?): String {
            if (view==null || view.visibility!=View.VISIBLE) return ""
            if (view is TextView) return view.text.toString()
            if (view is ViewGroup) return (0 until view.childCount).joinToString(" ") { text(view.getChildAt(it)) }
            return ""
        }
        fun find(view: View?, id: Int): View? {
            if(view==null)return null
            if(view.id==id)return view
            if(view is ViewGroup)for(i in 0 until view.childCount) {
                val found=find(view.getChildAt(i),id)
                if(found!=null)return found
            }
            return null
        }
        suspend fun waitFor(expected: String, forbidden: String?=null) {
            val deadline=SystemClock.elapsedRealtime()+10000
            var last=""
            while (SystemClock.elapsedRealtime()<deadline) {
                instrumentation.runOnMainSync {
                    hostView?.let { view ->
                        val density=context.resources.displayMetrics.density
                        val w=(360*density).toInt();val h=(400*density).toInt()
                        view.forceLayout()
                        view.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY))
                        view.layout(0,0,w,h)
                    }
                    last=text(hostView)
                }
                if (expected in last && (forbidden==null || forbidden !in last)) return
                delay(50)
            }
            fail("组件未自动达到期望状态 expected=$expected forbidden=$forbidden actual=$last")
        }
        suspend fun cache(number: String, internalId: String, course: String) {
            app.database.profileDao().insert(ProfileEntity(number,Json.encodeToString(PersonalInfo(studentNumber=number,realName="Widget test"))))
            val cell=TimetableCell(course,course,LocalDate.now().plusDays(1).dayOfWeek.value,1,"第一大节",course,"1","D510","Test teacher","1-18周","")
            app.database.timetableDao().insert(TimetableEntity(internalId,Json.encodeToString(TimetableResult(cells=listOf(cell)))))
        }
        try {
            val options=Bundle().apply {
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,360)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,400)
            }
            assertTrue(manager.bindAppWidgetIdIfAllowed(id,component,options))
            activity=androidx.test.core.app.ActivityScenario.launch(android.content.Intent(context,com.glassous.betterhrbust.widget.WidgetPreviewHostActivity::class.java))
            activity.onActivity { screen ->
                host.startListening();hostView=host.createView(screen,id,info)
                screen.setContentView(hostView)
            }
            waitFor("打开应用登录")
            cache(a,aid,"测试课程A")
            app.preferencesManager.saveAuth(a,aid,"2026","1")
            app.preferencesManager.setCurrentWeek(6)
            waitFor("测试课程A")
            cache(a,aid,"A课程已更新")
            waitFor("A课程已更新","测试课程A")
            // 仅在隔离模拟器切换系统主题，验证已绑定的组件会更新；恢复原系统模式。
            if (android.os.Build.HARDWARE in listOf("ranchu","goldfish")) {
                val uiMode=context.getSystemService(android.app.UiModeManager::class.java)
                val originalMode=uiMode.nightMode
                val modeNames=mapOf(0 to "auto",1 to "no",2 to "yes")
                fun shell(command: String) {
                    instrumentation.uiAutomation.executeShellCommand(command).use { descriptor ->
                        java.io.FileInputStream(descriptor.fileDescriptor).use { it.readBytes() }
                    }
                }
                if (originalMode in modeNames) try {
                    for ((mode,flag) in listOf("yes" to android.content.res.Configuration.UI_MODE_NIGHT_YES,"no" to android.content.res.Configuration.UI_MODE_NIGHT_NO)) {
                        shell("cmd uimode night $mode")
                        val deadline=SystemClock.elapsedRealtime()+10000
                        var changed=false
                        var themeState=""
                        while (!changed && SystemClock.elapsedRealtime()<deadline) {
                            instrumentation.runOnMainSync {
                                hostView?.let { view ->
                                    val density=context.resources.displayMetrics.density
                                    val w=(360*density).toInt();val h=(400*density).toInt()
                                    view.forceLayout()
                        view.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY))
                                    view.layout(0,0,w,h)
                                }
                                val applied=app.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
                                val ink=(find(hostView,R.id.widget_course_detail) as? TextView)?.currentTextColor
                                val panel=(hostView?.findViewById<View>(R.id.widget_today_panel)?.background as? android.graphics.drawable.GradientDrawable)?.color?.defaultColor
                                themeState="applied=$applied flag=$flag ink=$ink panel=$panel count=${hostView?.findViewById<android.widget.ListView>(R.id.widget_tomorrow_courses)?.count} children=${hostView?.findViewById<android.widget.ListView>(R.id.widget_tomorrow_courses)?.childCount}"
                                changed=applied==flag && ink==com.glassous.betterhrbust.core.ui.theme.CourseColorPalette.cardColors("A课程已更新",flag==android.content.res.Configuration.UI_MODE_NIGHT_YES).content.toArgb() && panel==app.getColor(R.color.widget_today_surface)
                            }
                            if (!changed) delay(50)
                        }
                        assertTrue("已绑定组件必须随系统主题更新 $themeState",changed)
                    }
                } finally { shell("cmd uimode night ${modeNames.getValue(originalMode)}") }
            }
            cache(b,bid,"测试课程B")
            app.preferencesManager.saveAuth(b,bid,"2026","1")
            app.preferencesManager.setCurrentWeek(6)
            waitFor("测试课程B","A课程已更新")
            app.database.profileDao().insert(ProfileEntity(b,Json.encodeToString(PersonalInfo(studentNumber=a))))
            waitFor("打开应用核验账号","测试课程B")
            app.preferencesManager.clearSession()
            waitFor("打开应用登录","测试课程B")
        } finally {
            instrumentation.runOnMainSync { host.deleteAppWidgetId(id);host.stopListening() }
            activity?.close()
            app.preferencesManager.saveAuth(previous.username,"",previous.year,previous.term,previous.realName,previous.savedPassword)
            app.preferencesManager.clearSession()
            for ((key,entity) in oldProfiles) {
                if (entity==null) app.database.profileDao().clear(key) else app.database.profileDao().insert(entity)
            }
            for ((key,entity) in oldTables) {
                if (entity==null) app.database.timetableDao().clear(key) else app.database.timetableDao().insert(entity)
            }
        }
    }
}
