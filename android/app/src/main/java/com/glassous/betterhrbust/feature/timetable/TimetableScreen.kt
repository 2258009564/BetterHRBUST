package com.glassous.betterhrbust.feature.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.glassous.betterhrbust.BetterHrbustApp
import com.glassous.betterhrbust.core.model.TimetableCell
import com.glassous.betterhrbust.core.model.TimetableResult
import com.glassous.betterhrbust.core.parser.AcademicParsers
import com.glassous.betterhrbust.core.ui.LocalBottomContentInset
import com.glassous.betterhrbust.core.ui.LocalTopContentInset
import com.glassous.betterhrbust.core.ui.components.AppPullToRefreshBox
import com.glassous.betterhrbust.core.ui.components.EmptyView
import com.glassous.betterhrbust.core.ui.components.LoadingView
import com.glassous.betterhrbust.core.ui.theme.CourseColors
import com.glassous.betterhrbust.core.ui.theme.CourseMutedColor
import com.glassous.betterhrbust.core.ui.theme.LocalDarkTheme
import com.glassous.betterhrbust.data.repository.Resource
import kotlinx.coroutines.launch
import java.time.LocalDate

val SectionTimes = listOf(
    "08:10 - 09:50",
    "10:10 - 11:50",
    "13:30 - 15:10",
    "15:30 - 17:10",
    "18:00 - 19:40",
    "19:50 - 21:30"
)

val DayNames = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimetableScreen(
    modifier: Modifier = Modifier
) {
    val app = remember { BetterHrbustApp.instance }
    val academicRepo = remember { app.academicRepository }
    val authRepo = remember { app.authRepository }
    val prefsManager = remember { app.preferencesManager }
    val syncManager = remember { app.syncManager }
    val coroutineScope = rememberCoroutineScope()
    // 使用应用内主题的明暗状态（而非系统主题），保证手动切换主题时课程配色同步
    val isDark = LocalDarkTheme.current

    val prefs by prefsManager.preferencesFlow.collectAsState(initial = null)
    val isSyncing by syncManager.isSyncing.collectAsState()
    var isRefreshing by remember { mutableStateOf(false) }
    var timetableResult by remember { mutableStateOf<TimetableResult?>(null) }
    var cacheError by remember { mutableStateOf("") }
    var selectedWeek by remember { mutableStateOf(1) }

    val today = remember { LocalDate.now() }
    val todayDayOfWeek = remember(today) { today.dayOfWeek.value } // 1..7
    var selectedDay by remember { mutableStateOf(todayDayOfWeek) }

    var selectedCourseDetail by remember { mutableStateOf<TimetableCell?>(null) }
    val sheetState = rememberModalBottomSheetState()

    LaunchedEffect(prefs?.currentWeek) {
        if (prefs != null) {
            selectedWeek = prefs!!.currentWeek
        }
    }

    /** 读取本地缓存（离线只读，不联网） */
    fun loadCache() {
        val currentPrefs = prefs ?: return
        coroutineScope.launch {
            academicRepo.getTimetable(
                currentPrefs.studentId,
                currentPrefs.year,
                currentPrefs.term,
                cacheOnly = true
            ).collect { res ->
                when (res) {
                    is Resource.Success -> {
                        timetableResult = res.data
                        cacheError = ""
                    }
                    is Resource.Error -> cacheError = res.message
                    else -> Unit
                }
            }
        }
    }

    /** 手动全量刷新：会话失效时要求重新登录 */
    fun refreshAll() {
        coroutineScope.launch {
            isRefreshing = true
            val outcome = syncManager.syncAll(manual = true)
            isRefreshing = false
            if (outcome.expired) {
                authRepo.markSessionExpired(true)
            } else {
                loadCache()
            }
        }
    }

    LaunchedEffect(prefs?.studentId, prefs?.lastFullSyncDate) {
        if (prefs != null && prefs!!.studentId.isNotEmpty()) {
            loadCache()
        }
    }

    // 全屏穿透：顶部控制区吸顶于状态栏下方（初始安全间距），列表内容可滚动穿透状态栏；
    // 底部由 contentPadding 预留小白条 + 悬浮导航坞的安全距离。
    AppPullToRefreshBox(
        isRefreshing = isRefreshing || isSyncing,
        onRefresh = { refreshAll() },
        modifier = modifier.fillMaxSize()
    ) {
        if (timetableResult == null && (isRefreshing || isSyncing)) {
            LoadingView(message = "正在加载课表...")
        } else if (timetableResult == null) {
            EmptyView(
                title = "暂无课表数据",
                description = cacheError.ifEmpty { "请下拉或使用「更多」页首行按钮手动刷新数据" }
            )
        } else {
            val dayCells = remember(timetableResult, selectedDay) {
                timetableResult?.cells?.filter { it.day == selectedDay } ?: emptyList()
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(
                    bottom = LocalBottomContentInset.current + 16.dp
                )
            ) {
                // 周次与星期选择：吸顶控制区，初始位于状态栏下方，滚动时内容穿透状态栏
                stickyHeader(key = "timetable_header") {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 1.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = LocalTopContentInset.current)
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            // Week Selector Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { if (selectedWeek > 1) selectedWeek-- }) {
                                        Icon(Icons.Default.ChevronLeft, contentDescription = "上一周")
                                    }
                                    Text(
                                        text = "第 $selectedWeek 周",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    IconButton(onClick = { if (selectedWeek < 26) selectedWeek++ }) {
                                        Icon(Icons.Default.ChevronRight, contentDescription = "下一周")
                                    }
                                }

                                val currentWeek = prefs?.currentWeek ?: 1
                                if (selectedWeek != currentWeek) {
                                    TextButton(onClick = { selectedWeek = currentWeek }) {
                                        Icon(Icons.Default.Today, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("回到本周")
                                    }
                                } else {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Text(
                                            text = "当前周",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }

                            // Day of Week Selector
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceAround
                            ) {
                                for (dayIdx in 1..7) {
                                    val isSelected = selectedDay == dayIdx
                                    val isToday = todayDayOfWeek == dayIdx
                                    val dayLabel = DayNames[dayIdx - 1]

                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable { selectedDay = dayIdx }
                                            .background(
                                                when {
                                                    isSelected -> MaterialTheme.colorScheme.primary
                                                    isToday -> MaterialTheme.colorScheme.primaryContainer
                                                    else -> MaterialTheme.colorScheme.surface
                                                }
                                            )
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = dayLabel,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                                                color = when {
                                                    isSelected -> MaterialTheme.colorScheme.onPrimary
                                                    isToday -> MaterialTheme.colorScheme.onPrimaryContainer
                                                    else -> MaterialTheme.colorScheme.onSurface
                                                }
                                            )
                                            if (isToday && !isSelected) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(4.dp)
                                                        .clip(RoundedCornerShape(2.dp))
                                                        .background(MaterialTheme.colorScheme.primary)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                for (section in 1..6) {
                    val activeInSlot = dayCells.filter { it.sectionIndex == section }

                    item(key = "section_$section") {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer
                            )
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "第 $section 大节",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = SectionTimes.getOrElse(section - 1) { "" },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                if (activeInSlot.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "无课程安排",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        for (cell in activeInSlot) {
                                            val isActiveThisWeek = AcademicParsers.isCourseActiveInWeek(cell.weeks, selectedWeek)
                                            val colorIdx = AcademicParsers.hashCourseColor(cell.courseName)
                                            val colorToken = if (isActiveThisWeek) CourseColors[colorIdx] else CourseMutedColor
                                            val bgColor = if (isDark) colorToken.backgroundDark else colorToken.backgroundLight
                                            val textColor = if (isDark) colorToken.textDark else colorToken.textLight

                                            Card(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { selectedCourseDetail = cell },
                                                shape = RoundedCornerShape(12.dp),
                                                colors = CardDefaults.cardColors(containerColor = bgColor)
                                            ) {
                                                Column(modifier = Modifier.padding(12.dp)) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = cell.courseName,
                                                            style = MaterialTheme.typography.titleSmall,
                                                            fontWeight = FontWeight.Bold,
                                                            color = textColor,
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                        if (!isActiveThisWeek) {
                                                            Surface(
                                                                shape = RoundedCornerShape(6.dp),
                                                                color = MaterialTheme.colorScheme.surfaceVariant
                                                            ) {
                                                                Text(
                                                                    text = "非本周",
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                                )
                                                            }
                                                        }
                                                    }

                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Text(
                                                            text = "${cell.location} • ${cell.teacher}",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = textColor.copy(alpha = 0.9f)
                                                        )
                                                        Text(
                                                            text = cell.weeks,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = textColor.copy(alpha = 0.8f)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Unarranged courses
                val unarranged = timetableResult?.unarranged ?: emptyList()
                if (unarranged.isNotEmpty()) {
                    item {
                        Text(
                            text = "未排课（${unarranged.size}）",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .padding(horizontal = 16.dp)
                        )
                    }

                    items(unarranged) { un ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = un.courseName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${un.teacher} · ${un.mergeClass}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal Bottom Sheet for Course Detail
    if (selectedCourseDetail != null) {
        val cell = selectedCourseDetail!!
        ModalBottomSheet(
            onDismissRequest = { selectedCourseDetail = null },
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Text(
                    text = cell.courseName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                if (cell.courseSeq.isNotEmpty()) {
                    Text(
                        text = "课序号: ${cell.courseSeq}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                DetailRow("上课地点", cell.location, Icons.Default.Place)
                DetailRow("任课教师", cell.teacher, Icons.Default.Person)
                DetailRow("上课周次", cell.weeks, Icons.Default.CalendarToday)
                DetailRow("节次安排", "${cell.sectionLabel} (${SectionTimes.getOrElse(cell.sectionIndex - 1) { "" }})", Icons.Default.Schedule)
                if (cell.hoursType.isNotEmpty()) {
                    DetailRow("学时类型", cell.hoursType, Icons.Default.Info)
                }

                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { selectedCourseDetail = null },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("关闭")
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            Text(text = value.ifEmpty { "无" }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
    }
}
