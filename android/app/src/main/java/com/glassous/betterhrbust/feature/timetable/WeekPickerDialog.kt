package com.glassous.betterhrbust.feature.timetable

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 每行展示的周次数量。 */
private const val WeeksPerRow = 6

/**
 * 教学周选择弹窗（对齐 Web 的周选择卡片）：
 * 1..[MaxTeachingWeek] 网格排列，当前教学周以「本周」标注，选中周高亮。
 */
@Composable
internal fun WeekPickerSheet(
    selectedWeek: Int,
    currentWeek: Int,
    maxTeachingWeek: Int,
    calendarKnown: Boolean,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = "选择教学周",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if(calendarKnown) "共 $maxTeachingWeek 周" else "校历待确认 · 暂列 $maxTeachingWeek 周",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(14.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..maxTeachingWeek).chunked(WeeksPerRow).forEach { rowWeeks ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowWeeks.forEach { week ->
                            WeekChip(
                                week = week,
                                isSelected = week == selectedWeek,
                                isCurrent = week == currentWeek,
                                onClick = { onSelect(week) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        // 末行不足时补占位，保持与上一行等宽对齐
                        repeat(WeeksPerRow - rowWeeks.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekChip(
    week: Int,
    isSelected: Boolean,
    isCurrent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val container by animateColorAsState(
        targetValue = when {
            isSelected -> MaterialTheme.colorScheme.primary
            isCurrent -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surfaceContainerHigh
        },
        label = "weekChipContainer"
    )
    val content = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary
        isCurrent -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(container)
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "$week",
            fontSize = 15.sp,
            fontWeight = if (isSelected || isCurrent) FontWeight.Bold else FontWeight.Medium,
            color = content
        )
        Text(
            text = if (isCurrent) "本周" else "",
            fontSize = 9.sp,
            lineHeight = 11.sp,
            color = if (isCurrent) content else Color.Transparent
        )
    }
}
