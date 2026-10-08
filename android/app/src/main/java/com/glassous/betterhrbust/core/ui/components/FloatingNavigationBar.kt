package com.glassous.betterhrbust.core.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.glassous.betterhrbust.navigation.TopLevelDestination

/** 悬浮胶囊导航坞的尺寸常量（页面底部安全距离计算同样依赖这些值）。 */
object FloatingNavigationBarDefaults {
    /** 胶囊总高度 */
    val Height = 64.dp

    /** 胶囊与系统导航栏（小白条区域）之间的悬浮间距 */
    val BottomMargin = 8.dp

    /** 胶囊内单个按钮的宽度 */
    val ItemWidth = 56.dp

    /** 胶囊内按钮之间的间距（紧凑排列） */
    val ItemSpacing = 4.dp

    /** 胶囊自身内边距 */
    val ItemPadding = 6.dp

    /** 胶囊内容区高度 */
    val ContentHeight = Height - ItemPadding * 2
}

/**
 * 悬浮胶囊式底部导航坞。
 *
 * - 胶囊整体悬浮在系统导航栏（小白条）上方，保留 [FloatingNavigationBarDefaults.BottomMargin] 的安全间距；
 * - 内部按钮紧凑排列（间距 [FloatingNavigationBarDefaults.ItemSpacing]），选中项渲染为胶囊高亮块；
 * - 导航坞不参与内容布局，页面内容可穿透其所在区域滚动（由页面自行通过 contentPadding 留出安全距离）。
 */
@Composable
fun FloatingNavigationBar(
    destinations: List<TopLevelDestination>,
    isSelected: (TopLevelDestination) -> Boolean,
    onNavigate: (TopLevelDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(bottom = FloatingNavigationBarDefaults.BottomMargin),
        shape = RoundedCornerShape(percent = 50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .padding(FloatingNavigationBarDefaults.ItemPadding)
                .height(FloatingNavigationBarDefaults.ContentHeight),
            horizontalArrangement = Arrangement.spacedBy(FloatingNavigationBarDefaults.ItemSpacing),
            verticalAlignment = Alignment.CenterVertically
        ) {
            destinations.forEach { destination ->
                FloatingNavigationBarItem(
                    destination = destination,
                    selected = isSelected(destination),
                    onClick = { onNavigate(destination) }
                )
            }
        }
    }
}

@Composable
private fun FloatingNavigationBarItem(
    destination: TopLevelDestination,
    selected: Boolean,
    onClick: () -> Unit
) {
    val containerColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            Color.Transparent
        },
        label = "floatingNavItemContainer"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "floatingNavItemContent"
    )

    Column(
        modifier = Modifier
            .width(FloatingNavigationBarDefaults.ItemWidth)
            .fillMaxHeight()
            .clip(RoundedCornerShape(percent = 50))
            .background(containerColor)
            .selectable(
                selected = selected,
                role = Role.Tab,
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = destination.label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = contentColor,
            maxLines = 1
        )
    }
}
