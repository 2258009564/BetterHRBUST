package com.glassous.betterhrbust.feature.settings

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.glassous.betterhrbust.BetterHrbustApp
import com.glassous.betterhrbust.notification.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CourseNotificationSettings(enabled: Boolean) {
    val context=LocalContext.current
    val app=context.applicationContext as BetterHrbustApp
    val scope=rememberCoroutineScope()
    var support by remember { mutableStateOf<LiveNotificationSupport?>(null) }
    var permission by remember { mutableStateOf(context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()) }
    fun setEnabled(value: Boolean) { scope.launch { app.preferencesManager.setCourseNotifications(value) } }
    val permissionRequest=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permission=granted
        if(granted)setEnabled(true)
    }
    val lifecycle=androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer=androidx.lifecycle.LifecycleEventObserver { _,event ->
            if(event==androidx.lifecycle.Lifecycle.Event.ON_RESUME) scope.launch {
                permission=context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
                support=withContext(Dispatchers.IO) { CourseNotification.support(context) }
                withContext(Dispatchers.IO) { CourseNotificationController.refresh(context) }
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) { support=withContext(Dispatchers.IO) { CourseNotification.support(context) } }
    Card(modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp),
        colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text("课表实时通知",modifier=Modifier.weight(1f),style=MaterialTheme.typography.titleSmall)
                Switch(checked=enabled,onCheckedChange={ value ->
                    if(!value)setEnabled(false)
                    else if(Build.VERSION.SDK_INT>=33 && !permission) permissionRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else setEnabled(true)
                })
            }
            Text("上课前15分钟显示课程、教室和倒计时，下课后自动结束。点击通知进入课表，可随时停止跟踪。",
                style=MaterialTheme.typography.bodySmall)
            val status=when {
                !permission -> "系统通知权限未开启"
                support==null -> "正在检查系统支持"
                support!!.xiaomiAllowed && support!!.xiaomiIsland -> "系统已开放小米超级岛通知"
                support!!.xiaomiAllowed -> "系统已开放小米焦点通知"
                support!!.xiaomiProtocol>=2 -> "小米未开放本应用焦点通知权限，当前使用标准通知"
                support!!.promotedAllowed -> "系统已允许实时通知提升，具体显示方式由系统决定"
                else -> "当前使用标准课表通知"
            }
            Text(status,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text("小米超级岛需应用获准接入；后台更新可能受省电限制，无精确闹钟权限时提醒可能延迟。",
                style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick={
                val intent=Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName)
                runCatching { context.startActivity(intent) }
            }) { Text("系统通知设置") }
        }
    }
}
