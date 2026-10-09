package com.glassous.betterhrbust.core.ui.theme

import androidx.compose.ui.graphics.Color

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

/**
 * 中性灰课程配色：非当前教学周的课程统一走该语义色（对齐 Web 的 COURSE_MUTED）。
 * 当前教学周的课程配色由 [CourseColorPalette] 的黄金角 HSL 算法动态生成。
 */
val CourseMutedColor = CourseMutedColors(
    backgroundLight = Color(0xFFF4F4F5),
    textLight = Color(0xFF71717A),
    backgroundDark = Color(0xFF27272A),
    textDark = Color(0xFFA1A1AA)
)

data class CourseMutedColors(
    val backgroundLight: Color,
    val textLight: Color,
    val backgroundDark: Color,
    val textDark: Color
)
