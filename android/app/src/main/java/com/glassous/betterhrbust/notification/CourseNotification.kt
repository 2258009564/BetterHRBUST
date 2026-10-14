package com.glassous.betterhrbust.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import com.glassous.betterhrbust.MainActivity
import com.glassous.betterhrbust.R
import kotlinx.serialization.json.*
import java.time.LocalDateTime
import java.time.ZoneId

/** 使用厂商公开能力查询，查询失败时正常退回系统通知，不伪装媒体/通话通知。 */
data class LiveNotificationSupport(val xiaomiProtocol: Int, val xiaomiAllowed: Boolean, val promotedAllowed: Boolean, val xiaomiIsland: Boolean = false)

object CourseNotification {
    const val CHANNEL = "course_live"
    const val ID = 20261012
    fun support(context: Context): LiveNotificationSupport {
        val protocol = runCatching { Settings.System.getInt(context.contentResolver,"notification_focus_protocol",0) }.getOrDefault(0)
        val registered = runCatching {
            @Suppress("DEPRECATION")
            val metadata = context.packageManager.getApplicationInfo(context.packageName,android.content.pm.PackageManager.GET_META_DATA).metaData
            @Suppress("DEPRECATION")
            metadata?.get("com.xiaomi.xms.APP_ID")?.toString()?.isNotBlank() == true
        }.getOrDefault(false)
        val island = protocol >= 3 && runCatching {
            val properties=Class.forName("android.os.SystemProperties")
            properties.getDeclaredMethod("getBoolean",String::class.java,Boolean::class.javaPrimitiveType)
                .invoke(null,"persist.sys.feature.island",false) == true
        }.getOrDefault(false)
        val allowed = if(protocol >= 2 && registered) runCatching {
            context.contentResolver.call(Uri.parse("content://miui.statusbar.notification.public"),"canShowFocus",null,
                Bundle().apply { putString("package",context.packageName) })?.getBoolean("canShowFocus",false) == true
        }.getOrDefault(false) else false
        val manager=context.getSystemService(NotificationManager::class.java)
        return LiveNotificationSupport(protocol,allowed,Build.VERSION.SDK_INT >= 36 && manager.canPostPromotedNotifications(),island)
    }
    fun createChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL,"课表实时通知",NotificationManager.IMPORTANCE_LOW).apply {
                description="临近上课和正在上课的课程状态";setSound(null,null);enableVibration(false)
            })
    }
    fun build(context: Context,state: CourseLiveState,now: LocalDateTime,support: LiveNotificationSupport): Notification {
        val content="${state.course.location} · ${state.course.time}"
        val open=PendingIntent.getActivity(context,ID,Intent(context,MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_TAB,MainActivity.TAB_TIMETABLE)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop=PendingIntent.getBroadcast(context,ID,Intent(context,CourseNotificationReceiver::class.java)
            .setAction(CourseNotificationReceiver.STOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder=Notification.Builder(context,CHANNEL).setSmallIcon(R.drawable.ic_course_notification)
            .setContentTitle(state.title).setContentText(content).setContentIntent(open)
            .setOngoing(true).setOnlyAlertOnce(true).setVisibility(Notification.VISIBILITY_PRIVATE)
            .setCategory(Notification.CATEGORY_EVENT)
            .setWhen(state.boundary.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
            .setUsesChronometer(true).setChronometerCountDown(true)
            .setTimeoutAfter(java.time.Duration.between(now,state.endsAt).toMillis().coerceAtLeast(1))
            .setDeleteIntent(stop)
            .addAction(Notification.Action.Builder(Icon.createWithResource(context,R.drawable.ic_course_notification),"停止跟踪",stop).build())
        // Android 16 首版以 colorized 判断资格；36.1 改为显式请求且禁止 colorized。
        // 按完整 SDK 版本分支，不能只检查大版本 36。
        if(Build.VERSION.SDK_INT == 36 && Build.VERSION.SDK_INT_FULL < Build.VERSION_CODES_FULL.BAKLAVA_1) {
            builder.setColorized(true)
        }
        val notification=builder.build()
        if(Build.VERSION.SDK_INT >= 36) notification.extras.putBoolean(androidx.core.app.NotificationCompat.EXTRA_REQUEST_PROMOTED_ONGOING,true)
        if(support.xiaomiProtocol >= 2 && support.xiaomiAllowed) {
            notification.extras.putString("miui.focus.param",xiaomiPayload(state,now,if(support.xiaomiIsland) support.xiaomiProtocol else 2))
            notification.extras.putBundle("miui.focus.pics",Bundle().apply {
                putParcelable("miui.focus.pic_course",Icon.createWithResource(context,R.drawable.ic_course_notification))
            })
        }
        return notification
    }
    fun xiaomiPayload(state: CourseLiveState,now: LocalDateTime,protocol: Int): String = buildJsonObject {
        putJsonObject("param_v2") {
            put("protocol",1);put("business","course");put("updatable",true);put("reopen","reopen")
            put("islandFirstFloat",false);put("enableFloat",false);put("filterWhenNoPermission",false)
            put("ticker",if(state.ongoing) "上课中" else "即将上课")
            put("aodTitle",state.course.name)
            put("timeout",((java.time.Duration.between(now,state.endsAt).seconds+59)/60).coerceAtLeast(1))
            putJsonObject("baseInfo") {
                put("type",2);put("title",state.course.name)
                put("content","${state.course.location} · ${state.course.time}")
                put("extraTitle",if(state.ongoing) "上课中" else "即将上课")
            }
            if(protocol >= 3) putJsonObject("param_island") {
                put("islandProperty",1)
                put("islandTimeout",java.time.Duration.between(now,state.endsAt).seconds.coerceAtLeast(1))
                putJsonObject("bigIslandArea") {
                    putJsonObject("imageTextInfoLeft") {
                        put("type",1)
                        putJsonObject("picInfo") { put("type",1);put("pic","miui.focus.pic_course") }
                        putJsonObject("textInfo") {
                            put("title",if(state.ongoing) "上课中" else "即将上课")
                            put("content","${state.remainingMinutes(now)}分钟")
                        }
                    }
                    putJsonObject("picInfo") { put("type",1);put("pic","miui.focus.pic_course") }
                }
                putJsonObject("smallIslandArea") {
                    putJsonObject("picInfo") { put("type",1);put("pic","miui.focus.pic_course") }
                }
            }
        }
    }.toString()
}
