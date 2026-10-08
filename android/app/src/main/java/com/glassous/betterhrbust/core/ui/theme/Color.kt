package com.glassous.betterhrbust.core.ui.theme

import androidx.compose.ui.graphics.Color

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

// Course color palettes (12 pastel pairs: light container + dark text)
data class CourseColorToken(
    val backgroundLight: Color,
    val textLight: Color,
    val backgroundDark: Color,
    val textDark: Color
)

val CourseColors = listOf(
    CourseColorToken(Color(0xFFDBEAFE), Color(0xFF1E3A8A), Color(0xFF1E3A8A).copy(alpha = 0.35f), Color(0xFFBFDBFE)), // Blue
    CourseColorToken(Color(0xFFFEF3C7), Color(0xFF78350F), Color(0xFF78350F).copy(alpha = 0.35f), Color(0xFFFDE68A)), // Amber
    CourseColorToken(Color(0xFFEDE9FE), Color(0xFF5B21B6), Color(0xFF5B21B6).copy(alpha = 0.35f), Color(0xFFDDD6FE)), // Violet
    CourseColorToken(Color(0xFFCCFBF1), Color(0xFF115E59), Color(0xFF115E59).copy(alpha = 0.35f), Color(0xFF99F6E4)), // Teal
    CourseColorToken(Color(0xFFFFE4E6), Color(0xFF9F1239), Color(0xFF9F1239).copy(alpha = 0.35f), Color(0xFFFECDD3)), // Rose
    CourseColorToken(Color(0xFFE0F2FE), Color(0xFF075985), Color(0xFF075985).copy(alpha = 0.35f), Color(0xFFBAE6FD)), // Sky
    CourseColorToken(Color(0xFFFFEDD5), Color(0xFF9A3412), Color(0xFF9A3412).copy(alpha = 0.35f), Color(0xFFFED7AA)), // Orange
    CourseColorToken(Color(0xFFE0E7FF), Color(0xFF3730A3), Color(0xFF3730A3).copy(alpha = 0.35f), Color(0xFFC7D2FE)), // Indigo
    CourseColorToken(Color(0xFFFCE7F3), Color(0xFF831843), Color(0xFF831843).copy(alpha = 0.35f), Color(0xFFFBCFE8)), // Pink
    CourseColorToken(Color(0xFFD1FAE5), Color(0xFF065F46), Color(0xFF065F46).copy(alpha = 0.35f), Color(0xFFA7F3D0)), // Emerald
    CourseColorToken(Color(0xFFF3E8FF), Color(0xFF6B21A8), Color(0xFF6B21A8).copy(alpha = 0.35f), Color(0xFFE9D5FF)), // Purple
    CourseColorToken(Color(0xFFF1F5F9), Color(0xFF334155), Color(0xFF334155).copy(alpha = 0.35f), Color(0xFFCBD5E1))  // Slate
)

val CourseMutedColor = CourseColorToken(
    backgroundLight = Color(0xFFF4F4F5),
    textLight = Color(0xFF71717A),
    backgroundDark = Color(0xFF27272A),
    textDark = Color(0xFFA1A1AA)
)
