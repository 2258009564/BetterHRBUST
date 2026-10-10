package com.glassous.betterhrbust.widget

import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import kotlinx.coroutines.runBlocking

/** Android 11 使用系统列表服务；Android 12+ 由内联 RemoteCollectionItems 提供数据。 */
class WidgetListService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = object: RemoteViewsFactory {
        private var rows=emptyList<RemoteViews>()
        override fun onCreate() { onDataSetChanged() }
        override fun onDestroy() { rows=emptyList() }
        override fun onDataSetChanged() = runBlocking {
            rows=emptyList()
            val snapshot=WidgetDataSource.load(this@WidgetListService) ?: return@runBlocking
            if(snapshot.prefs.username!=intent.getStringExtra("owner") || snapshot.prefs.lastLoginAt!=intent.getLongExtra("epoch",-1) || snapshot.message.isNotEmpty())return@runBlocking
            val remaining=TimetableWidgetModel.progress(snapshot.today,snapshot.time).remaining
            val list=when(intent.getIntExtra("column",0)) {
                0->remaining
                2->remaining.ifEmpty { if(snapshot.tomorrowUnknown) emptyList() else snapshot.tomorrow }
                else->snapshot.tomorrow
            }
            val compact=intent.getBooleanExtra("nextOnly",false)
            rows=(if(compact) list.take(1) else list).map { TimetableWidgetProvider.courseRow(this@WidgetListService,it,compact) }
        }
        override fun getCount()=rows.size
        override fun getViewAt(position: Int)=rows.getOrNull(position)
        override fun getLoadingView(): RemoteViews?=null
        override fun getViewTypeCount()=1
        override fun getItemId(position: Int)=position.toLong()
        override fun hasStableIds()=false
    }
}
