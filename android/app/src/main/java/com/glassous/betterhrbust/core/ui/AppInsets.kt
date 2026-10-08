package com.glassous.betterhrbust.core.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

/**
 * 顶部安全间距。
 *
 * 页面内容初始应位于该间距之下（避免被状态栏遮挡），
 * 但页面背景与滚动中的内容可以穿透该区域绘制（edge-to-edge）。
 * 当顶部被会话过期横幅等元素占用时，该值会被下调为 0，避免重复让位。
 */
val LocalTopContentInset = staticCompositionLocalOf { 0.dp }

/**
 * 底部安全间距：系统导航栏（小白条）+ 悬浮导航坞（含悬浮间距）的总高度。
 *
 * 页面内容初始/末尾应留出该间距（避免被悬浮胶囊导航坞遮挡），
 * 滚动中的内容可以穿透该区域绘制（edge-to-edge）。
 */
val LocalBottomContentInset = staticCompositionLocalOf { 0.dp }
