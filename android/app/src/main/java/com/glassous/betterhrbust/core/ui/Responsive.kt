package com.glassous.betterhrbust.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

/** 平板判定阈值：设备最小宽度 ≥ 600dp（与 Android `sw600dp` 资源限定符一致）。 */
const val TABLET_MIN_WIDTH_DP = 600

/**
 * 当前设备是否为平板。
 *
 * 以「最小宽度」而非当前窗口宽度判定：手机横屏窗口虽宽，但交互习惯仍是底部导航坞；
 * 平板则无论横竖屏都使用左侧竖排导航坞（见 `NavigationDock` 的 `vertical` 参数）。
 */
@Composable
fun isTabletDevice(): Boolean =
    LocalConfiguration.current.smallestScreenWidthDp >= TABLET_MIN_WIDTH_DP
