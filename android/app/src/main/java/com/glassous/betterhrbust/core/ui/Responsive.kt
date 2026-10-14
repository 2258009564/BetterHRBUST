package com.glassous.betterhrbust.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

const val TABLET_MIN_WIDTH_DP = 600

/** 根据当前窗口选择导航；分屏或低矮横屏不沿用设备的平板分类。 */
internal fun usesNavigationRail(widthDp: Int, heightDp: Int): Boolean =
    widthDp >= TABLET_MIN_WIDTH_DP && heightDp >= 480

@Composable
fun isTabletDevice(): Boolean {
    val configuration = LocalConfiguration.current
    return usesNavigationRail(configuration.screenWidthDp, configuration.screenHeightDp)
}
