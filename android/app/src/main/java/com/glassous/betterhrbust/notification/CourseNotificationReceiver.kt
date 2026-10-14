package com.glassous.betterhrbust.notification

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.glassous.betterhrbust.BetterHrbustApp
import com.glassous.betterhrbust.widget.WidgetDataSource
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDateTime
import java.time.ZoneId

class CourseNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context,intent: Intent) {
        val pending=goAsync()
        CourseNotificationController.scope.launch {
            try {
                if(intent.action==STOP) (context.applicationContext as BetterHrbustApp).preferencesManager.setCourseNotifications(false)
                CourseNotificationController.refresh(context)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                android.util.Log.w("CourseNotification","本地通知更新失败",error)
            } finally { pending.finish() }
        }
    }
    companion object { const val STOP="com.glassous.betterhrbust.COURSE_NOTIFICATION_STOP" }
}

/** 复用已核验的本地课表，闹钟只唤醒本地更新，不运行常驻服务或网络轮询。 */
object CourseNotificationController {
    internal val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val mutex=Mutex()
    private var timer: Job?=null
    private fun alarmIntent(context: Context)=PendingIntent.getBroadcast(context,CourseNotification.ID,
        Intent(context,CourseNotificationReceiver::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    suspend fun refresh(context: Context): Unit = mutex.withLock {
        val app=context.applicationContext as BetterHrbustApp
        val prefs=app.preferencesManager.preferencesFlow.first()
        val manager=context.getSystemService(NotificationManager::class.java)
        val alarms=context.getSystemService(AlarmManager::class.java)
        timer?.cancel();timer=null
        alarms.cancel(alarmIntent(context))
        if(!prefs.courseNotifications || prefs.studentId.isBlank() || !manager.areNotificationsEnabled()) {
            manager.cancel(CourseNotification.ID);return@withLock
        }
        val snapshot=WidgetDataSource.load(context)
        if(snapshot==null) {
            manager.cancel(CourseNotification.ID);return@withLock
        }
        val now=LocalDateTime.of(snapshot.date,snapshot.time)
        val plan=CourseLivePlanner.plan(if(snapshot.message.isBlank()) snapshot.today else emptyList(),now)
        val current=app.preferencesManager.preferencesFlow.first()
        if(!current.courseNotifications || current.username!=prefs.username || current.studentId!=prefs.studentId || current.lastLoginAt!=prefs.lastLoginAt) {
            manager.cancel(CourseNotification.ID);return@withLock
        }
        val state=plan.state
        CourseNotification.createChannel(context)
        if(state==null) manager.cancel(CourseNotification.ID)
        else try { manager.notify(CourseNotification.ID,CourseNotification.build(context,state,now,CourseNotification.support(context))) }
        catch(_: SecurityException) { manager.cancel(CourseNotification.ID);return@withLock }
        // 小米摘要文本按分钟更新；无活动时只安排下一个开始/结束边界或午夜。
        val next=if(state==null) plan.nextRefresh else minOf(plan.nextRefresh,now.plusMinutes(1))
        val deadline=next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val pending=alarmIntent(context)
        try {
            if(Build.VERSION.SDK_INT<31 || alarms.canScheduleExactAlarms()) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,deadline,pending)
            else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,deadline,pending)
        } catch(_: SecurityException) { alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,deadline,pending) }
        timer=scope.launch {
            delay((deadline-System.currentTimeMillis()).coerceAtLeast(1))
            // 在独立作业中更新，避免刷新时取消当前定时器连带取消整个更新。
            scope.launch {
                try { refresh(context.applicationContext) }
                catch(cancelled: CancellationException) { throw cancelled }
                catch(error: Exception) { android.util.Log.w("CourseNotification","定时更新失败",error) }
            }
        }
    }
}
