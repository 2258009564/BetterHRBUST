package com.glassous.betterhrbust.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt

/** 导航坞的一个目的地（图标 + 标签）。 */
data class NavigationDockDestination(
    val glyph: ImageVector,
    val label: String,
)

/**
 * 导航坞尺寸与动效常量。
 */
object NavigationDockDefaults {
    /** 胶囊高度。 */
    val BarHeight = 62.dp

    /** 折叠后的胶囊高度：隐藏标签，仅保留图标。 */
    val CollapsedBarHeight = 42.dp

    /** 垂直滑动完成折叠 / 展开所需的手指行程。 */
    val CollapseTravel = 44.dp

    /** 松手时按速度预判折叠落位的投影时长（秒）。 */
    const val CollapseFlingProjection = 0.06f

    /** 页面下滑浏览多少距离后折叠导航坞（隐藏标签，仅保留图标）。 */
    val CollapseScrollDistance = 24.dp

    /** 页面上滑回看多少距离后展开导航坞（更灵敏，回看即恢复标签）。 */
    val ExpandScrollDistance = 8.dp

    /** 胶囊内边距（四周）。 */
    val BarPadding = 4.dp

    /** 胶囊与屏幕左右边缘的间距。 */
    val EdgeMargin = 21.dp

    /** 胶囊上方为阴影预留的空间。 */
    val ShadowRoom = 8.dp

    /** 图标尺寸。 */
    val IconSize = 24.dp

    /** 图标与标签之间的间距。 */
    val LabelGap = 2.dp

    /** 标签字号。 */
    val LabelSize = 10.sp

    /** 标签因超宽而缩小时的下限字号。 */
    val MinLabelSize = 9.sp

    /** 单个目的地的最大宽度。 */
    val MaxItemExtent = 56.dp

    /** 按压时选中胶囊的膨胀量（半径方向）。 */
    val LensGrowth = 10.dp

    /** 选中胶囊内内容的额外放大系数（× 抬起量）。 */
    const val LensMagnify = 0.12f

    /** 速度归一化基准（用于果冻拉伸）。 */
    const val JellySpeed = 8f

    /** 果冻拉伸的最大比例。 */
    const val JellyStretch = 0.25f

    /** 拖拽越出胶囊时的橡皮筋阻尼上限（单位：目的地个数）。 */
    const val OverDrag = 0.35f

    /** 拖拽越界时整体拉伸的上限比例（× 最短边）。 */
    const val PullLimitRatio = 7f / 32f

    /** 拖拽越界拉伸的强度。 */
    const val PullStretch = 0.5f

    /** 按压时整体膨胀的基础比例。 */
    const val PressGrowth = 1f / 8f

    /** 按压膨胀的绝对上限（像素）。 */
    const val MaxPressGrowth = 16f

    /** 跟随手指（快速、无回弹）。 */
    val TrackSpring = DockSpringSpec(stiffness = 12_000f, dampingRatio = 1f)

    /** 落位（柔和回弹）。 */
    val SettleSpring = DockSpringSpec(stiffness = 420f, dampingRatio = 0.72f)

    /** 按压抬起 / 回弹。 */
    val LiftSpring = DockSpringSpec(stiffness = 1_100f, dampingRatio = 0.82f)
}

/**
 * 导航坞距屏幕底部的间距 = `max(左右边距(21dp), 系统导航栏 inset)`。
 */
@Composable
fun navigationDockBottomMargin(): Dp {
    val navigationBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return maxOf(NavigationDockDefaults.EdgeMargin, navigationBarBottom)
}

/** 导航坞占用的内容底部内边距 = 胶囊高度 + [navigationDockBottomMargin]。 */
@Composable
fun navigationDockInset(): Dp = NavigationDockDefaults.BarHeight + navigationDockBottomMargin()

/**
 * 导航坞折叠状态：`0` = 展开（图标 + 标签），`1` = 折叠（仅图标）。
 */
@Stable
class NavigationDockCollapseState internal constructor(
    internal val progress: ResettableSpring,
) {
    /** 目标态是否已折叠（动画可能仍在进行中）。 */
    val isCollapsed: Boolean get() = progress.target > 0.5f

    /** 折叠为"仅图标"。 */
    fun collapse() {
        progress.springTo(1f, NavigationDockDefaults.SettleSpring)
    }

    /** 展开恢复标签。 */
    fun expand() {
        progress.springTo(0f, NavigationDockDefaults.SettleSpring)
    }

    /**
     * 消费一段页面垂直滚动量（[scrollDeltaY] 与手指位移同向：手指上滑为负、下拖为正）：
     * 页面下滑浏览更后方内容累计超过 [collapseDistancePx] 即折叠；页面上滑回看累计超过 [expandDistancePx] 即展开。
     */
    fun onPageScroll(scrollDeltaY: Float, collapseDistancePx: Float, expandDistancePx: Float) {
        when {
            scrollDeltaY < 0f -> {
                collapseAccum -= scrollDeltaY
                expandAccum = 0f
                if (collapseAccum >= collapseDistancePx) {
                    collapseAccum = 0f
                    if (!isCollapsed) collapse()
                }
            }

            scrollDeltaY > 0f -> {
                expandAccum += scrollDeltaY
                collapseAccum = 0f
                if (expandAccum >= expandDistancePx) {
                    expandAccum = 0f
                    if (isCollapsed) expand()
                }
            }
        }
    }

    private var collapseAccum = 0f
    private var expandAccum = 0f
}

/** 记住一个 [NavigationDockCollapseState]。 */
@Composable
fun rememberNavigationDockCollapseState(): NavigationDockCollapseState {
    val spring = rememberResettableSpring(0f)
    return remember(spring) { NavigationDockCollapseState(spring) }
}

/**
 * 记住一个把页面垂直滚动转发给 [state] 的 [NestedScrollConnection]：
 * 挂到承载页面的容器上（`Modifier.nestedScroll(...)`）即可让导航坞随滚动折叠 / 展开。
 */
@Composable
fun rememberNavigationDockCollapseConnection(
    state: NavigationDockCollapseState,
    collapseDistance: Dp = NavigationDockDefaults.CollapseScrollDistance,
    expandDistance: Dp = NavigationDockDefaults.ExpandScrollDistance,
): NestedScrollConnection {
    val density = LocalDensity.current
    return remember(state, density, collapseDistance, expandDistance) {
        val collapsePx = with(density) { collapseDistance.toPx() }
        val expandPx = with(density) { expandDistance.toPx() }
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                state.onPageScroll(consumed.y, collapsePx, expandPx)
                return Offset.Zero
            }
        }
    }
}

/** 越界位移的橡皮筋阻尼映射。 */
private fun rubberBand(overshoot: Float, limit: Float): Float {
    val pull = 1f - 1f / (abs(overshoot) * 0.55f / limit + 1f)
    return limit * pull * if (overshoot < 0f) -1f else 1f
}

/**
 * 悬浮胶囊底部导航坞（Material3 纯色渲染）。
 *
 * 组成与行为：
 *  - 悬浮胶囊：`surfaceContainer` 表面 + 双层柔和投影，居中、宽度按目的地数量自适应收紧；
 *  - 选中高亮胶囊（lens）：由 [ResettableSpring] 驱动，移动时沿运动方向"果冻"拉伸，按压时在指尖下放大；
 *  - 图标/标签颜色：未被 lens 覆盖处渲染为 `onSurface`；被 lens 覆盖的部分渲染为主色 `primary`，lens 滑过时逐段变色；
 *  - 交互：按住可左右拖动 lens 滑过各目的地，经过时触发触感，松手切到该目的地；越界时整体橡皮筋拉伸；按压时整体膨胀；
 *  - 折叠：胶囊收缩为"仅图标"（标签淡出、高度变矮），展开时恢复图标 + 标签。
 *
 * @param destinations 目的地列表
 * @param selectedIndex 当前选中的索引
 * @param onSelected 选中回调（点击或拖拽释放到该目的地时触发）
 * @param collapseState 外部折叠状态（通常由页面滚动驱动）
 */
@Composable
fun NavigationDock(
    destinations: List<NavigationDockDestination>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    collapseState: NavigationDockCollapseState? = null,
    itemExtent: Dp = NavigationDockDefaults.MaxItemExtent,
) {
    if (destinations.isEmpty()) return

    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val shape = RoundedCornerShape(50)
    val colorScheme = MaterialTheme.colorScheme

    val contentColor = colorScheme.onSurface
    val tintColor = colorScheme.primary
    val lensColor = colorScheme.secondaryContainer
    val lensLiftColor = colorScheme.onSecondaryContainer

    val shadowColor = Color.Black
    val shadowStrength = if (colorScheme.surface.luminance() < 0.5f) 3f else 1f
    val navigationBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomMargin = maxOf(NavigationDockDefaults.EdgeMargin, navigationBarBottom)
    val barPaddingPx = with(density) { NavigationDockDefaults.BarPadding.toPx() }
    val lensGrowthPx = with(density) { NavigationDockDefaults.LensGrowth.toPx() }
    val barHeightPx = with(density) { NavigationDockDefaults.BarHeight.toPx() }
    val collapsedBarHeightPx = with(density) { NavigationDockDefaults.CollapsedBarHeight.toPx() }
    val collapseTravelPx = with(density) { NavigationDockDefaults.CollapseTravel.toPx() }

    val lens = rememberResettableSpring(selectedIndex.toFloat())
    val lift = rememberResettableSpring(0f)
    val swell = rememberResettableSpring(0f)
    val stretch = rememberResettableSpring(0f)

    /** 折叠进度：0 = 展开（图标 + 标签），1 = 折叠（仅图标）。 */
    val localCollapse = rememberResettableSpring(0f)
    val collapse = collapseState?.progress ?: localCollapse
    val dockTopInWindow = remember { floatArrayOf(0f) }

    var pressedIndex by remember { mutableStateOf<Int?>(null) }
    var dragging by remember { mutableStateOf(false) }

    val currentSelectedIndex by rememberUpdatedState(selectedIndex)
    val currentOnSelected by rememberUpdatedState(onSelected)

    // 外部改变选中项时，让 lens 弹向新的选中位置；按压/拖拽期间交由手势接管
    LaunchedEffect(selectedIndex) {
        if (pressedIndex == null && lens.target != selectedIndex.toFloat()) {
            lens.springTo(selectedIndex.toFloat(), NavigationDockDefaults.SettleSpring)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = NavigationDockDefaults.EdgeMargin,
                end = NavigationDockDefaults.EdgeMargin,
                top = NavigationDockDefaults.ShadowRoom,
                bottom = bottomMargin,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .widthIn(
                    max = itemExtent * destinations.size +
                        NavigationDockDefaults.BarPadding * 2
                )
                .dockBarHeight(collapse, barHeightPx, collapsedBarHeightPx)
                .graphicsLayer {
                    if (size.width <= 0f || size.height <= 0f) return@graphicsLayer
                    val liftValue = swell.value.coerceAtLeast(0f)
                    val swellScale = 1f + liftValue * minOf(
                        NavigationDockDefaults.PressGrowth * 2f,
                        NavigationDockDefaults.MaxPressGrowth / size.maxDimension,
                    )
                    val pullX = stretch.value
                    scaleX = swellScale *
                        (1f + abs(pullX) / size.width * NavigationDockDefaults.PullStretch)
                    scaleY = swellScale
                    translationX = pullX
                }
                .shadow(
                    elevation = 8.dp,
                    shape = shape,
                    clip = false,
                    ambientColor = shadowColor.copy(alpha = 0.08f * shadowStrength),
                    spotColor = shadowColor.copy(alpha = 0.08f * shadowStrength),
                )
                .shadow(
                    elevation = 1.dp,
                    shape = shape,
                    clip = false,
                    ambientColor = shadowColor.copy(alpha = 0.04f * shadowStrength),
                    spotColor = shadowColor.copy(alpha = 0.04f * shadowStrength),
                )
                .background(color = colorScheme.surfaceContainer, shape = shape)
                .onGloballyPositioned { coordinates ->
                    dockTopInWindow[0] = coordinates.positionInWindow().y
                }
                .pointerInput(destinations.size) {
                    val count = destinations.size
                    val lastIndex = count - 1

                    fun indexAt(position: Float): Int = position.roundToInt().coerceIn(0, lastIndex)

                    fun positionAt(x: Float, width: Float): Float {
                        val extent = (width - barPaddingPx * 2f) / count
                        val raw = (x - barPaddingPx) / extent - 0.5f
                        return when {
                            raw < 0f -> rubberBand(raw, NavigationDockDefaults.OverDrag)
                            raw > lastIndex ->
                                lastIndex + rubberBand(raw - lastIndex, NavigationDockDefaults.OverDrag)
                            else -> raw
                        }
                    }

                    fun settleReleasedLens(commit: Boolean, wasDragging: Boolean) {
                        val index = pressedIndex ?: return
                        pressedIndex = null
                        dragging = false
                        lift.springTo(0f, NavigationDockDefaults.SettleSpring)
                        swell.springTo(0f, NavigationDockDefaults.SettleSpring)
                        stretch.springTo(0f, NavigationDockDefaults.SettleSpring)
                        if (!commit) {
                            lens.springTo(currentSelectedIndex.toFloat(), NavigationDockDefaults.SettleSpring)
                            return
                        }
                        lens.springTo(index.toFloat(), NavigationDockDefaults.SettleSpring)
                        if (index != currentSelectedIndex) currentOnSelected(index)
                    }

                    awaitPointerEventScope {
                        while (true) {
                            val down: PointerInputChange = awaitFirstDown(requireUnconsumed = false)
                            down.consume()
                            val barWidth = size.width.toFloat()
                            val downX = down.position.x
                            val downWindowY = dockTopInWindow[0] + down.position.y
                            val tracker = VelocityTracker()
                            tracker.addPosition(down.uptimeMillis, down.position)
                            var wasDragging = false
                            var verticalDrag = false
                            val collapseBase = collapse.value

                            val pressIndex = indexAt(positionAt(downX, barWidth))
                            pressedIndex = pressIndex
                            dragging = false
                            lift.springTo(1f, NavigationDockDefaults.LiftSpring)
                            swell.springTo(1f, NavigationDockDefaults.LiftSpring)
                            lens.springTo(pressIndex.toFloat(), NavigationDockDefaults.SettleSpring)

                            fun commitCollapse() {
                                val projected = collapse.value +
                                    collapse.velocity * NavigationDockDefaults.CollapseFlingProjection
                                val target = if (projected > 0.5f) 1f else 0f
                                if ((target > 0.5f) != (collapseBase > 0.5f)) {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                collapse.springTo(target, NavigationDockDefaults.SettleSpring)
                            }

                            var released = false
                            while (!released) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id }
                                if (change == null) {
                                    if (verticalDrag) {
                                        collapse.springTo(collapseBase, NavigationDockDefaults.SettleSpring)
                                    }
                                    settleReleasedLens(commit = false, wasDragging = wasDragging)
                                    break
                                }
                                if (change.changedToUpIgnoreConsumed()) {
                                    tracker.addPosition(change.uptimeMillis, change.position)
                                    if (verticalDrag) {
                                        commitCollapse()
                                        change.consume()
                                        released = true
                                        break
                                    }
                                    if (wasDragging) {
                                        val anchor = pressedIndex ?: pressIndex
                                        val extent = (barWidth - barPaddingPx * 2f) / count
                                        val lead = tracker.calculateVelocity().x / extent * 0.1f
                                        val projected = (lens.target + lead)
                                            .roundToInt()
                                            .coerceIn(anchor - 1, anchor + 1)
                                            .coerceIn(0, lastIndex)
                                        if (projected != pressedIndex) {
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            pressedIndex = projected
                                        }
                                    }
                                    settleReleasedLens(commit = true, wasDragging = wasDragging)
                                    change.consume()
                                    released = true
                                    break
                                }
                                if (!change.pressed) {
                                    if (verticalDrag) {
                                        collapse.springTo(collapseBase, NavigationDockDefaults.SettleSpring)
                                        released = true
                                        break
                                    }
                                    settleReleasedLens(commit = false, wasDragging = wasDragging)
                                    released = true
                                    break
                                }
                                val x = change.position.x
                                val dy = (dockTopInWindow[0] + change.position.y) - downWindowY
                                tracker.addPosition(change.uptimeMillis, change.position)
                                if (!wasDragging && !verticalDrag) {
                                    val slop = viewConfiguration.touchSlop
                                    if (abs(dy) > slop && abs(dy) > abs(x - downX)) {
                                        verticalDrag = true
                                        pressedIndex = null
                                        dragging = false
                                        lift.springTo(0f, NavigationDockDefaults.SettleSpring)
                                        swell.springTo(0f, NavigationDockDefaults.SettleSpring)
                                        stretch.springTo(0f, NavigationDockDefaults.SettleSpring)
                                        lens.springTo(
                                            currentSelectedIndex.toFloat(),
                                            NavigationDockDefaults.SettleSpring,
                                        )
                                    } else if (abs(x - downX) > slop) {
                                        wasDragging = true
                                        dragging = true
                                    }
                                }
                                if (verticalDrag) {
                                    collapse.springTo(
                                        (collapseBase + dy / collapseTravelPx).coerceIn(0f, 1f),
                                        NavigationDockDefaults.TrackSpring,
                                    )
                                    change.consume()
                                    continue
                                }
                                if (wasDragging) {
                                    val overshoot = x - x.coerceIn(0f, barWidth)
                                    stretch.springTo(
                                        rubberBand(
                                            overshoot,
                                            minOf(size.width, size.height) *
                                                NavigationDockDefaults.PullLimitRatio,
                                        ),
                                        NavigationDockDefaults.TrackSpring,
                                    )
                                    val position = positionAt(x, barWidth)
                                    lens.springTo(position, NavigationDockDefaults.TrackSpring)
                                    val index = indexAt(position)
                                    if (index != pressedIndex) {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        pressedIndex = index
                                    }
                                }
                                change.consume()
                            }
                        }
                    }
                }
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(NavigationDockDefaults.BarPadding)) {
                val extent: Dp = maxWidth / destinations.size
                val widestLabelPx = rememberWidestLabelPx(destinations)
                val labelStyle = MaterialTheme.typography.labelSmall.copy(
                    fontSize = NavigationDockDefaults.LabelSize,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.sp,
                )
                val extentPx = with(density) { extent.toPx() }
                val labelInsetPx = with(density) { 2.dp.toPx() }
                val room = extentPx - labelInsetPx * 2f
                val labelScale = if (widestLabelPx <= room) {
                    1f
                } else {
                    maxOf(
                        room / widestLabelPx,
                        with(density) { NavigationDockDefaults.MinLabelSize.toPx() } /
                            with(density) { NavigationDockDefaults.LabelSize.toPx() },
                    )
                }

                // ① 选中胶囊（lens）：绘制在内容之下
                Box(
                    modifier = Modifier
                        .lensRectPlacement(lens, lift, extentPx, lensGrowthPx)
                        .drawBehind {
                            drawRoundRect(
                                color = lerp(
                                    lensColor,
                                    lensLiftColor,
                                    0.08f * lift.value.coerceIn(0f, 1f),
                                ),
                                cornerRadius = CornerRadius(size.height / 2f),
                            )
                        }
                )

                // ② 各目的地（图标 + 标签），被 lens 覆盖的部分渲染为主色
                Row(modifier = Modifier.fillMaxSize()) {
                    destinations.forEachIndexed { index, destination ->
                        DockItem(
                            destination = destination,
                            index = index,
                            lens = lens,
                            lift = lift,
                            collapse = collapse,
                            lensGrowthPx = lensGrowthPx,
                            contentColor = contentColor,
                            tintColor = tintColor,
                            labelStyle = labelStyle.copy(fontSize = NavigationDockDefaults.LabelSize * labelScale),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

/** 量出最宽标签的像素宽度（用于标签超宽时自动缩字）。 */
@Composable
private fun rememberWidestLabelPx(destinations: List<NavigationDockDestination>): Float {
    val textMeasurer = rememberTextMeasurer()
    val style = TextStyle(
        fontSize = NavigationDockDefaults.LabelSize,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.sp,
    )
    return remember(destinations, style, textMeasurer) {
        destinations.maxOfOrNull { textMeasurer.measure(it.label, style).size.width }?.toFloat() ?: 0f
    }
}

/**
 * 单个目的地：图标 + 标签。
 *
 * 着色实现：内容按 lens 形状分区绘制两次 —— lens 之外用原色、
 * lens 之内用主色 SrcIn 滤镜，lens 滑过时图标/文字便逐段变色。
 */
@Composable
private fun DockItem(
    destination: NavigationDockDestination,
    index: Int,
    lens: ResettableSpring,
    lift: ResettableSpring,
    collapse: ResettableSpring,
    lensGrowthPx: Float,
    contentColor: Color,
    tintColor: Color,
    labelStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    val tintPaint = remember { Paint() }
    val tintFilter = remember(tintColor) { ColorFilter.tint(tintColor, BlendMode.SrcIn) }
    val lensPath = remember { Path() }
    val outsidePath = remember { Path() }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .drawWithContent {
                val width = size.width
                val height = size.height
                if (width <= 0f || height <= 0f) {
                    drawContent()
                    return@drawWithContent
                }
                val position = lens.value - index
                val rect = lensRect(
                    position = position,
                    velocity = lens.velocity,
                    liftValue = lift.value,
                    extentPx = width,
                    heightPx = height,
                    lensGrowthPx = lensGrowthPx,
                )

                // 快速路径：lens 与本目的地不相交时按原样绘制一次
                if (rect.left >= width || rect.left + rect.width <= 0f) {
                    drawContent()
                    return@drawWithContent
                }

                val liftValue = lift.value.coerceIn(0f, 1f)
                lensPath.reset()
                lensPath.addRoundRect(
                    RoundRect(
                        rect = Rect(
                            rect.left,
                            rect.top,
                            rect.left + rect.width,
                            rect.top + rect.height,
                        ),
                        cornerRadius = CornerRadius(rect.height / 2f),
                    )
                )

                val emphasis = (1f - abs(position)).coerceIn(0f, 1f)
                val scale = 1f + NavigationDockDefaults.LensMagnify * emphasis * liftValue

                // ① lens 之外：原色绘制
                outsidePath.reset()
                outsidePath.fillType = PathFillType.EvenOdd
                outsidePath.addRect(Rect(Offset.Zero, size))
                outsidePath.addPath(lensPath)
                clipPath(outsidePath) {
                    withTransform({
                        if (scale != 1f) scale(scale, scale, center)
                    }) {
                        this@drawWithContent.drawContent()
                    }
                }

                // ② lens 之内：按 lens 形状剪裁，再用主色 SrcIn 滤镜重绘一次
                tintPaint.colorFilter = tintFilter
                val canvas = drawContext.canvas
                canvas.saveLayer(Rect(Offset.Zero, size), tintPaint)
                clipPath(lensPath) {
                    withTransform({
                        if (scale != 1f) scale(scale, scale, center)
                    }) {
                        this@drawWithContent.drawContent()
                    }
                }
                canvas.restore()
            },
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = destination.glyph,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(NavigationDockDefaults.IconSize),
                )
                Spacer(Modifier.height(NavigationDockDefaults.LabelGap))
                Text(
                    text = destination.label,
                    color = contentColor,
                    style = labelStyle,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.dockLabelCollapse(collapse),
                )
            }
        }
    }
}

private class LensRect(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
)

/** 由位置、速度（果冻拉伸）与抬起量（按压膨胀）推导 lens 的实时矩形。 */
private fun lensRect(
    position: Float,
    velocity: Float,
    liftValue: Float,
    extentPx: Float,
    heightPx: Float,
    lensGrowthPx: Float,
): LensRect {
    val jelly = (abs(velocity) / NavigationDockDefaults.JellySpeed)
        .coerceAtMost(1f) * NavigationDockDefaults.JellyStretch
    val growth = lensGrowthPx * 2f * liftValue.coerceIn(0f, 1f)
    val width = (extentPx + growth) * (1f + jelly)
    val height = (heightPx + growth) * (1f - jelly / 2f)
    return LensRect(
        left = (position + 0.5f) * extentPx - width / 2f,
        top = (heightPx - height) / 2f,
        width = width,
        height = height,
    )
}

/** 选中胶囊高亮块的布局摆位。 */
private fun Modifier.lensRectPlacement(
    lens: ResettableSpring,
    lift: ResettableSpring,
    extentPx: Float,
    lensGrowthPx: Float,
): Modifier = layout { measurable, constraints ->
    val rect = lensRect(
        position = lens.value,
        velocity = lens.velocity,
        liftValue = lift.value,
        extentPx = extentPx,
        heightPx = constraints.maxHeight.toFloat(),
        lensGrowthPx = lensGrowthPx,
    )
    val placeable = measurable.measure(
        Constraints.fixed(
            width = rect.width.roundToInt().coerceAtLeast(0),
            height = rect.height.roundToInt().coerceAtLeast(0),
        )
    )
    layout(constraints.maxWidth, constraints.maxHeight) {
        placeable.placeRelative(rect.left.roundToInt(), rect.top.roundToInt())
    }
}

/** 折叠态的胶囊高度：测量期读取折叠弹簧，在展开与折叠高度间插值。 */
private fun Modifier.dockBarHeight(
    collapse: ResettableSpring,
    expandedPx: Float,
    collapsedPx: Float,
): Modifier = layout { measurable, constraints ->
    val t = collapse.value.coerceIn(0f, 1f)
    val height = (expandedPx + (collapsedPx - expandedPx) * t)
        .roundToInt()
        .coerceIn(constraints.minHeight, constraints.maxHeight)
    val placeable = measurable.measure(constraints.copy(minHeight = height, maxHeight = height))
    layout(placeable.width, height) { placeable.placeRelative(0, 0) }
}

/** 折叠态的标签：透明度淡出并收拢高度。 */
private fun Modifier.dockLabelCollapse(collapse: ResettableSpring): Modifier = this
    .graphicsLayer {
        alpha = (1f - collapse.value.coerceIn(0f, 1f) * 2f).coerceIn(0f, 1f)
    }
    .layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val t = collapse.value.coerceIn(0f, 1f)
        val height = (placeable.height * (1f - t)).roundToInt()
        layout(placeable.width, height) { placeable.placeRelative(0, 0) }
    }
