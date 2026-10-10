package com.glassous.betterhrbust.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.color.ColorProvider as DayNightColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.glassous.betterhrbust.R

/** 小部件根容器圆角（Android 12+ 由 [cornerRadius] 生效，低版本退化为直角）。 */
internal val WidgetRootCorner = 16.dp

/** 小部件内边距。 */
internal val WidgetRootPadding = 10.dp

/** 周视图时间列宽度：仅放大节序号，把宽度让给课程块。 */
internal val WidgetTimeColumnWidth = 16.dp

/** 顶部标题行 / 星期表头行高。 */
internal val WidgetHeaderHeight = 20.dp
internal val WidgetWeekdayHeight = 15.dp

/**
 * 无课大节行的高度上限：保持「无课行收窄」的观感，
 * 把更多可用高度让给有课行（课程名的换行空间因此更充足）。
 */
internal val WidgetMaxEmptyRowHeight = 22.dp

/** 行高低于该值时不渲染大节序号（避免文字被裁切）。 */
internal val WidgetRowLabelMinHeight = 12.dp

/** 时间列在行高达到该值时才追加起止时间（避免高度不足时文字被裁切）。 */
internal val WidgetTimeRangeMinRowHeight = 40.dp

/** 课程名 / 教室的最大行数：块高允许时给足换行空间。 */
private const val MaxNameLines = 4
private const val MaxNameLinesDetailed = 4
private const val MaxLocationLines = 2

/**
 * 课程块内的文字排版：课程名 / 教室各自的行数、是否展示教师。
 *
 * 行数按块高动态计算（而不是写死上限），因此课程名与教室都能获得充足的换行空间，
 * 同时保证文字总高度不超过块高、不会被裁切。
 */
internal data class BlockTextLayout(
    val nameLines: Int,
    val locationLines: Int,
    val showTeacher: Boolean
)

/**
 * 按块高分配课程块的文字行数。
 *
 * 优先级：课程名（最多 [MaxNameLines] / [MaxNameLinesDetailed] 行）→ 教室（最多 [MaxLocationLines] 行）
 * → 教师（仅在高度充裕时）。同一格叠放多门课时（[stacked]）每块只有半高，仅保证课程名一行。
 *
 * @param detailed 宽版排版（课程名 11sp / 次级文字 9sp，行高更大，可容纳更多行）
 */
internal fun blockTextLayout(
    blockHeight: Dp,
    detailed: Boolean,
    stacked: Boolean,
    reserveTagLine: Boolean = false
): BlockTextLayout {
    val nameLineHeight = if (detailed) 14f else 12f
    val subLineHeight = if (detailed) 12f else 11f
    // 减去块内上下内边距（各 2dp）与一点余量；非本周课程再为「非本周」标记预留一行
    val available = (
        blockHeight.value - 5f - if (reserveTagLine) subLineHeight else 0f
        ).coerceAtLeast(0f)

    if (stacked) {
        return BlockTextLayout(
            nameLines = (available / nameLineHeight).toInt().coerceIn(1, 1),
            locationLines = 0,
            showTeacher = false
        )
    }

    // 教师在「课程名 2 行 + 教室 2 行 + 教师 1 行」都放得下时才展示
    val showTeacher = detailed && available >= nameLineHeight * 2 + subLineHeight * 3
    val forNameAndLocation = available - if (showTeacher) subLineHeight else 0f
    val locationLines = when {
        forNameAndLocation < nameLineHeight + subLineHeight -> 0
        forNameAndLocation >= nameLineHeight * 2 + subLineHeight * 2 -> MaxLocationLines
        else -> 1
    }
    val maxNameLines = if (detailed) MaxNameLinesDetailed else MaxNameLines
    val nameLines = ((forNameAndLocation - locationLines * subLineHeight) / nameLineHeight)
        .toInt()
        .coerceIn(1, maxNameLines)
    return BlockTextLayout(
        nameLines = nameLines,
        locationLines = locationLines,
        showTeacher = showTeacher
    )
}

/**
 * 有课行与无课行的基础高度比例，对齐 App 内课表（有课 96dp / 无课 30dp ≈ 3.2 : 1）。
 */
private const val OccupiedRowRatio = 3.2f

/**
 * 把 [totalHeight] 精确分配给「有课行 / 无课行」，使网格铺满小部件可用高度（不留底部空白）。
 *
 * 分配规则：先按 [OccupiedRowRatio] 求出无课行高度（不超过 [WidgetMaxEmptyRowHeight]，
 * 保持 App 内的收窄观感），剩余高度全部由有课行均分，因此总和恰好等于 [totalHeight]。
 */
internal fun allocateRowHeights(
    totalHeight: Dp,
    occupiedCount: Int,
    emptyCount: Int
): Pair<Dp, Dp> {
    if (occupiedCount + emptyCount <= 0) return 0.dp to 0.dp
    val unit = totalHeight.value / (occupiedCount * OccupiedRowRatio + emptyCount)
    val emptyRow = unit.dp.coerceAtMost(WidgetMaxEmptyRowHeight)
    val occupiedRow = if (occupiedCount <= 0) {
        0.dp
    } else {
        ((totalHeight - emptyRow * emptyCount) / occupiedCount).coerceAtLeast(unit.dp)
    }
    return occupiedRow to emptyRow
}

/**
 * 小部件中性色（跟随系统深浅色；不启用动态取色，以保持与课程黄金角配色一致的观感）。
 */
internal object WidgetPalette {
    val Surface: ColorProvider = dayNightColor(Color(0xFFFFFFFF), Color(0xFF1C1B1F))
    val OnSurface: ColorProvider = dayNightColor(Color(0xFF1B1B1F), Color(0xFFE6E1E5))
    val OnSurfaceVariant: ColorProvider = dayNightColor(Color(0xFF5A5D63), Color(0xFFC4C6CF))
    /** 今天 / 当前列高亮：中性蓝，与课程块的金黄色相区分 */
    val Highlight: ColorProvider = dayNightColor(Color(0xFF4A5F82), Color(0xFFAEC6FF))

    /** 今天所在列的淡色底（对齐 App 内课表的 todayTint：浅色 8% / 深色 14%） */
    val TodayTint: ColorProvider =
        dayNightColor(Color(0x144A5F82), Color(0x24AEC6FF))
}

/**
 * 明 / 暗双色 → Glance 颜色提供者。
 *
 * API 31+ 由桌面端在渲染时按深浅色择一（无需重新组合小部件即可跟随系统换色）；
 * 低版本在组合期按系统当前深浅色取值。
 */
internal fun dayNightColor(day: Color, night: Color): ColorProvider =
    DayNightColorProvider(day = day, night = night)

/** 课程块配色 → Glance 颜色提供者。 */
internal fun WidgetCardColors.containerColor(): ColorProvider =
    dayNightColor(containerDay, containerNight)

internal fun WidgetCardColors.contentColor(): ColorProvider =
    dayNightColor(contentDay, contentNight)

internal fun WidgetCardColors.accentColor(): ColorProvider =
    dayNightColor(accentDay, accentNight)

/** 课程块内的次级文字（地点 / 教师）配色。 */
internal fun WidgetCardColors.subContentColor(): ColorProvider =
    dayNightColor(subContentDay, subContentNight)

/** 文本样式快捷构造。 */
internal fun widgetText(
    color: ColorProvider,
    sizeSp: Int,
    weight: FontWeight? = null,
    align: TextAlign? = null
): TextStyle = TextStyle(color = color, fontSize = sizeSp.sp, fontWeight = weight, textAlign = align)

/**
 * 小部件文案：在 [TimetableWidgetLoader] 之后、组合之前一次性解析，
 * 避免 Glance 组合期反复访问 Context。
 */
internal class WidgetStrings(private val context: Context) {
    fun weekTitle(week: Int): String = context.getString(R.string.widget_week_title, week)

    fun dayTitle(dayLabel: String): String = context.getString(R.string.widget_day_title, dayLabel)

    val notCurrentWeek: String = context.getString(R.string.widget_not_current_week)
    val weekEmpty: String = context.getString(R.string.widget_week_empty)
    val dayEmpty: String = context.getString(R.string.widget_day_empty)
    val noSessionTitle: String = context.getString(R.string.widget_no_session_title)
    val noSessionHint: String = context.getString(R.string.widget_no_session_hint)
}

/**
 * 小部件根容器：圆角纸面 + 整块可点击（点击任意位置打开 App 课表页）。
 *
 * 同时标记为小部件「背景视图」（[appWidgetBackground]），供系统做圆角裁剪与过渡优化。
 */
@Composable
internal fun WidgetRoot(
    action: Action,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .cornerRadius(WidgetRootCorner)
            .background(WidgetPalette.Surface)
            .clickable(action)
            .padding(10.dp),
        content = content
    )
}

/**
 * 固定高度的空白间隔。
 *
 * 不使用 Glance 的 `Spacer`：其默认沿主轴扩展，会把后续内容挤出可视区域；
 * 这里以「有明确高度的空 Box」表达间隔，几何尺寸完全确定。
 */
@Composable
internal fun WidgetGap(height: Dp) {
    Box(modifier = GlanceModifier.fillMaxWidth().height(height)) {}
}

/**
 * 空态 / 占位：居中标题 + 可选提示（未登录、无课表缓存、本周无课、今天无课共用）。
 *
 * 居中交给外层 Box（FrameLayout 的 gravity），内层 Column 保持 wrap_content，
 * 避免在 Column 中使用 fillMaxHeight 导致高度占满整块小部件。
 */
@Composable
internal fun WidgetEmptyState(title: String, hint: String = "") {
    Box(
        modifier = GlanceModifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.Horizontal.CenterHorizontally) {
            Text(
                text = title,
                style = widgetText(WidgetPalette.OnSurface, 12, FontWeight.Medium),
                maxLines = 2
            )
            if (hint.isNotEmpty()) {
                WidgetGap(3.dp)
                Text(
                    text = hint,
                    style = widgetText(WidgetPalette.OnSurfaceVariant, 10),
                    maxLines = 2
                )
            }
        }
    }
}

/**
 * 课表色块（周视图与日视图网格共用）：色块 + 课程名 + 上课教室（+ 教师）。
 *
 * 块内文字行数由调用方按块高算好的 [layout] 决定：课程名与教室都会充分换行，
 * 行高不足时才逐级收紧，因此文字不会被裁切。
 * 非本周有效的课程配色已弱化，并追加一行「非本周」。
 *
 * 尺寸完全由调用方以显式 dp 给出，块内不做任何高度填充。
 *
 * @param detailed 宽版排版：课程名 11sp、次级文字 9sp、左右内边距更大
 */
@Composable
internal fun GridCourseBlock(
    cell: WidgetCell,
    strings: WidgetStrings,
    layout: BlockTextLayout,
    detailed: Boolean,
    modifier: GlanceModifier = GlanceModifier
) {
    Column(
        modifier = modifier
            .cornerRadius(6.dp)
            .background(cell.colors.containerColor())
            .padding(
                horizontal = if (detailed) 6.dp else 2.dp,
                vertical = 2.dp
            )
    ) {
        Text(
            text = cell.course.courseName,
            style = widgetText(
                color = cell.colors.contentColor(),
                sizeSp = if (detailed) 11 else 9,
                weight = FontWeight.Medium
            ),
            maxLines = layout.nameLines
        )
        if (layout.locationLines > 0) {
            Text(
                text = cell.course.location.ifEmpty { "待定" },
                style = widgetText(cell.colors.subContentColor(), if (detailed) 9 else 8),
                maxLines = layout.locationLines
            )
        }
        if (layout.showTeacher) {
            Text(
                text = cell.course.teacher.ifEmpty { "—" },
                style = widgetText(cell.colors.subContentColor(), 9),
                maxLines = 1
            )
        }
        if (!cell.activeInWeek) {
            Text(
                text = strings.notCurrentWeek,
                style = widgetText(cell.colors.accentColor(), if (detailed) 8 else 7, FontWeight.Medium),
                maxLines = 1
            )
        }
    }
}


