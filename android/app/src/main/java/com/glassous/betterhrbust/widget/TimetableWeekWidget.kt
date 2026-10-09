package com.glassous.betterhrbust.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import com.glassous.betterhrbust.feature.timetable.DayNames
import com.glassous.betterhrbust.feature.timetable.DayShortNames

/** 标题行右侧「今天 · 周X」占宽。 */
private val WeekTitleTailWidth = 76.dp

/** 标题行与星期表头、星期表头与课程行之间的间隔。 */
private val WeekHeaderGap = 4.dp
private val WeekBodyGap = 2.dp

/** 课程格四周留白（避免相邻色块粘连）。 */
private val WeekCellInset = 1.dp

/** 同格多门课时的块间距。 */
private val WeekStackGap = 2.dp

/**
 * 周视图课表小部件（桌面占位 4 列 × 4 行）。
 *
 * 固定展示当前教学周，与应用内课表一致：
 * - 7 列（周一至周日）× 6 个大节全部保行，本周无课的大节收窄显示，保留节次位置；
 * - 今天所在列带淡色底、星期标签加粗高亮；
 * - 非本周有效的课程以中性灰弱化并标注「非本周」。
 *
 * 布局说明：小部件内所有元素都使用显式 dp 尺寸（依据 [LocalSize] 计算），
 * 不使用 `fillMaxHeight` / `Spacer` —— 在 RemoteViews 的嵌套 LinearLayout 中，
 * MATCH_PARENT 高度的子视图会取满整块高度、并把后续内容挤出可视区域；
 * 横向列宽则统一交给 `defaultWeight` 由宿主按实际宽度精确分配，避免按尺寸推算导致的溢出。
 */
class TimetableWeekWidget : GlanceAppWidget() {

    /** 固定尺寸小部件：按宿主给出的精确尺寸组合，行高据此计算。 */
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = TimetableWidgetLoader.load()
        val strings = WidgetStrings(context)
        val action = openTimetableAction(context)
        provideContent { WeekWidgetContent(data = data, strings = strings, action = action) }
    }
}

/** 周视图小部件的系统入口（Manifest 中声明）。 */
class TimetableWeekWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TimetableWeekWidget()
}

@Composable
private fun WeekWidgetContent(data: TimetableWidgetData, strings: WidgetStrings, action: Action) {
    // Glance 在组合期始终提供小部件实际尺寸（见 GlanceRemoteViews / SizeBox）
    val size = LocalSize.current
    WidgetRoot(action = action) {
        when {
            // 未登录 / 本地无课表：提示打开应用同步（与「本周无课」区分开）
            !data.hasSession || !data.hasTimetable ->
                WidgetEmptyState(strings.noSessionTitle, strings.noSessionHint)
            !data.hasWeekCourses -> WidgetEmptyState(strings.weekEmpty)
            else -> WeekGrid(data = data, strings = strings, size = size)
        }
    }
}

/**
 * 周网格：标题 + 星期表头 + 6 个大节行。
 *
 * 列宽全部由 `defaultWeight` 分配（时间列固定窄宽），因此无论宿主机型 / 密度如何，
 * 表格宽度都恰好等于容器宽度，不会右溢出。
 */
@Composable
private fun WeekGrid(data: TimetableWidgetData, strings: WidgetStrings, size: DpSize) {
    val contentHeight = (size.height - WidgetRootPadding * 2).coerceAtLeast(40.dp)
    val gridHeight = (contentHeight - WidgetHeaderHeight - WeekHeaderGap - WidgetWeekdayHeight - WeekBodyGap)
        .coerceAtLeast(20.dp)

    // 有课行 / 无课行分别计算行高：无课行收窄，剩余高度全部给有课行，精确铺满小部件高度
    val emptyRowCount = data.weekRows.count { !it.occupied }
    val occupiedRowCount = data.weekRows.size - emptyRowCount
    val (occupiedRowHeight, emptyRowHeight) = allocateRowHeights(gridHeight, occupiedRowCount, emptyRowCount)

    // 标题：教学周 + 今天
    Row(
        modifier = GlanceModifier.fillMaxWidth().height(WidgetHeaderHeight),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = strings.weekTitle(data.currentWeek),
            modifier = GlanceModifier.defaultWeight(),
            style = widgetText(WidgetPalette.OnSurface, 12, FontWeight.Bold),
            maxLines = 1
        )
        Text(
            text = strings.dayTitle(DayNames[data.todayDay - 1]),
            modifier = GlanceModifier.width(WeekTitleTailWidth),
            style = widgetText(WidgetPalette.OnSurfaceVariant, 9, align = TextAlign.End),
            maxLines = 1
        )
    }

    WidgetGap(WeekHeaderGap)

    // 星期表头：今天所在列带淡色底 + 加粗高亮
    Row(modifier = GlanceModifier.fillMaxWidth().height(WidgetWeekdayHeight)) {
        Box(modifier = GlanceModifier.width(WidgetTimeColumnWidth).height(WidgetWeekdayHeight)) {}
        for (day in 1..7) {
            val isToday = day == data.todayDay
            Box(
                modifier = GlanceModifier
                    .defaultWeight()
                    .height(WidgetWeekdayHeight)
                    .then(if (isToday) GlanceModifier.background(WidgetPalette.TodayTint) else GlanceModifier),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = DayShortNames[day - 1],
                    style = widgetText(
                        color = if (isToday) WidgetPalette.Highlight else WidgetPalette.OnSurfaceVariant,
                        sizeSp = 9,
                        weight = if (isToday) FontWeight.Bold else FontWeight.Normal
                    ),
                    maxLines = 1
                )
            }
        }
    }

    WidgetGap(WeekBodyGap)

    // 课程行：6 个大节全部保行；无课行收窄，同一格可能排多门课（不同周次），纵向等分展示前两门
    data.weekRows.forEach { row ->
        val rowHeight = if (row.occupied) occupiedRowHeight else emptyRowHeight
        Row(modifier = GlanceModifier.fillMaxWidth().height(rowHeight)) {
            Box(
                modifier = GlanceModifier.width(WidgetTimeColumnWidth).height(rowHeight),
                contentAlignment = Alignment.Center
            ) {
                if (rowHeight >= WidgetRowLabelMinHeight) {
                    Text(
                        text = "${row.section}",
                        style = widgetText(WidgetPalette.OnSurface, 9, FontWeight.Bold),
                        maxLines = 1
                    )
                }
            }
            for (day in 1..7) {
                val isToday = day == data.todayDay
                Box(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .height(rowHeight)
                        .then(if (isToday) GlanceModifier.background(WidgetPalette.TodayTint) else GlanceModifier)
                        .padding(WeekCellInset)
                ) {
                    val cells = row.cellsByDay[day].orEmpty()
                    if (cells.isNotEmpty()) {
                        val shown = cells.take(2)
                        val blockHeight = if (shown.size > 1) {
                            ((rowHeight - WeekStackGap) / 2).coerceAtLeast(8.dp)
                        } else {
                            rowHeight
                        }
                        Column(modifier = GlanceModifier.fillMaxWidth()) {
                            shown.forEachIndexed { index, cell ->
                                if (index > 0) WidgetGap(WeekStackGap)
                                GridCourseBlock(
                                    cell = cell,
                                    strings = strings,
                                    layout = blockTextLayout(
                                        blockHeight = blockHeight,
                                        detailed = false,
                                        stacked = shown.size > 1,
                                        reserveTagLine = !cell.activeInWeek
                                    ),
                                    detailed = false,
                                    modifier = GlanceModifier.fillMaxWidth().height(blockHeight)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
