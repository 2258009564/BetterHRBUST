package com.glassous.betterhrbust.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 系统广播触发的小部件刷新。
 *
 * 覆盖三类本地缓存「过期」的场景：
 * - `DATE_CHANGED`：跨天后日视图需要展示新的「今天」；
 * - `TIME_SET` / `TIMEZONE_CHANGED`：用户改时间或换时区后「今天」可能变化；
 * - `BOOT_COMPLETED`：开机后立即刷新，避免继续显示开机前的课表。
 *
 * 均为系统受保护广播，声明为 `exported=false` 亦可正常接收。
 * 周期性兜底刷新由 `appwidget-provider` 的 `updatePeriodMillis` 提供。
 */
class TimetableWidgetRefreshReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in RefreshActions) return
        // 保持在 goAsync 窗口内完成刷新，避免进程被提前判定为空闲而回收
        val pending = goAsync()
        TimetableWidgetUpdater.refreshAsync { pending.finish() }
    }

    private companion object {
        val RefreshActions = setOf(
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_BOOT_COMPLETED
        )
    }
}
