package com.glassous.betterhrbust.feature.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.glassous.betterhrbust.BetterHrbustApp
import com.glassous.betterhrbust.core.model.*
import com.glassous.betterhrbust.core.parser.AcademicParsers
import com.glassous.betterhrbust.core.ui.LocalBottomContentInset
import com.glassous.betterhrbust.core.ui.LocalTopContentInset
import com.glassous.betterhrbust.core.ui.components.AppPullToRefreshBox
import com.glassous.betterhrbust.core.ui.components.LoadingView
import com.glassous.betterhrbust.data.repository.Resource
import kotlinx.coroutines.launch
import java.time.LocalDate

data class QuickNavGridItem(
    val title: String,
    val icon: ImageVector,
    val route: Any
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigate: (Any) -> Unit,
    modifier: Modifier = Modifier
) {
    val app = remember { BetterHrbustApp.instance }
    val academicRepo = remember { app.academicRepository }
    val prefsManager = remember { app.preferencesManager }
    val coroutineScope = rememberCoroutineScope()

    val prefs by prefsManager.preferencesFlow.collectAsState(initial = null)
    var isRefreshing by remember { mutableStateOf(false) }

    var profile by remember { mutableStateOf<PersonalInfo?>(null) }
    var timetable by remember { mutableStateOf<TimetableResult?>(null) }
    var scoreResult by remember { mutableStateOf<ScoreResult?>(null) }
    var exams by remember { mutableStateOf<List<ExamItem>>(emptyList()) }

    fun refreshData(force: Boolean = false) {
        val currentPrefs = prefs ?: return
        coroutineScope.launch {
            isRefreshing = true
            // Load Profile
            if (currentPrefs.username.isNotEmpty()) {
                academicRepo.getPersonalInfo(currentPrefs.username, force).collect { res ->
                    if (res is Resource.Success) profile = res.data
                }
            }
            // Load Timetable
            if (currentPrefs.studentId.isNotEmpty()) {
                academicRepo.getTimetable(currentPrefs.studentId, currentPrefs.year, currentPrefs.term, force).collect { res ->
                    if (res is Resource.Success) timetable = res.data
                }
            }
            // Load Scores
            if (currentPrefs.studentId.isNotEmpty()) {
                academicRepo.getScores(currentPrefs.studentId, force).collect { res ->
                    if (res is Resource.Success) scoreResult = res.data
                }
            }
            // Load Exams
            if (currentPrefs.studentId.isNotEmpty()) {
                academicRepo.getExams(currentPrefs.studentId, force).collect { res ->
                    if (res is Resource.Success) exams = res.data
                }
            }
            // Load teaching week
            academicRepo.getTeachingWeek().collect {}

            isRefreshing = false
        }
    }

    LaunchedEffect(prefs?.studentId) {
        if (prefs != null) {
            refreshData(force = false)
        }
    }

    val stats = remember(scoreResult) {
        scoreResult?.scores?.let { AcademicParsers.calculateGpaStats(it) }
    }

    // Today's courses
    val today = remember { LocalDate.now() }
    val todayDayOfWeek = remember(today) { today.dayOfWeek.value } // 1=Monday .. 7=Sunday
    val currentWeek = prefs?.currentWeek ?: 1

    val todayCourses = remember(timetable, todayDayOfWeek, currentWeek) {
        timetable?.cells?.filter { cell ->
            cell.day == todayDayOfWeek && AcademicParsers.isCourseActiveInWeek(cell.weeks, currentWeek)
        }?.sortedBy { it.sectionIndex } ?: emptyList()
    }

    val upcomingExam = remember(exams) {
        exams.firstOrNull { it.isUpcoming && (it.countdownDays ?: 0) >= 0 }
    }

    val quickNavItems = listOf(
        QuickNavGridItem("课程表", Icons.Default.CalendarMonth, com.glassous.betterhrbust.navigation.TimetableRoute),
        QuickNavGridItem("成绩单", Icons.Default.Assessment, com.glassous.betterhrbust.navigation.ScoresRoute),
        QuickNavGridItem("考试日程", Icons.Default.DateRange, com.glassous.betterhrbust.navigation.ExamsRoute),
        QuickNavGridItem("培养方案", Icons.Default.Bookmark, com.glassous.betterhrbust.navigation.ProgramRoute),
        QuickNavGridItem("空教室", Icons.Default.MeetingRoom, com.glassous.betterhrbust.navigation.ClassroomsRoute),
        QuickNavGridItem("全校课程", Icons.Default.Search, com.glassous.betterhrbust.navigation.CoursesRoute),
        QuickNavGridItem("学籍档案", Icons.Default.Badge, com.glassous.betterhrbust.navigation.ProfileRoute),
        QuickNavGridItem("通知与设置", Icons.Default.Settings, com.glassous.betterhrbust.navigation.SettingsRoute)
    )

    Box(modifier = modifier.fillMaxSize()) {
        AppPullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { refreshData(force = true) },
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(
                    top = LocalTopContentInset.current + 8.dp,
                    bottom = LocalBottomContentInset.current + 24.dp
                )
            ) {
            // Student Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                Text(
                                    text = profile?.realName?.ifEmpty { "哈理工同学" } ?: "哈理工同学",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "学号: ${profile?.studentNumber?.ifEmpty { prefs?.username } ?: prefs?.username ?: "未知"}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primary
                            ) {
                                Text(
                                    text = "第 $currentWeek 周",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                        if (!profile?.college.isNullOrBlank() || !profile?.major.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "${profile?.college ?: ""} ${profile?.major ?: ""}".trim(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }

            // Key Stats Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = stats?.gpa?.toString() ?: "--",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(text = "累计 GPA", style = MaterialTheme.typography.labelSmall)
                        }
                        VerticalDivider(modifier = Modifier.height(40.dp))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = stats?.earnedCredits?.toString() ?: "--",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Text(text = "已获学分", style = MaterialTheme.typography.labelSmall)
                        }
                        VerticalDivider(modifier = Modifier.height(40.dp))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${stats?.failedCount ?: 0}",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = if ((stats?.failedCount ?: 0) > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
                            )
                            Text(text = "未通过门数", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            // Upcoming Exam Card (if any)
            if (upcomingExam != null) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigate(com.glassous.betterhrbust.navigation.ExamsRoute) },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "近期考试：${upcomingExam.courseName}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${upcomingExam.time} | ${upcomingExam.location}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.tertiary
                            ) {
                                Text(
                                    text = "还有 ${upcomingExam.countdownDays} 天",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onTertiary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Today's Courses Section
            item {
                Text(
                    text = "今日课程",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (todayCourses.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(imageVector = Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "今天没有安排课程，好好休息吧！", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            } else {
                items(todayCourses.size) { index ->
                    val course = todayCourses[index]
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigate(com.glassous.betterhrbust.navigation.TimetableRoute) },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "${course.sectionIndex}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = course.courseName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${course.location} • ${course.teacher}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = course.sectionLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }

            // Quick Nav Grid Section
            item {
                Text(
                    text = "功能导航",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        for (row in quickNavItems.chunked(4)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                for (item in row) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { onNavigate(item.route) }
                                            .padding(vertical = 4.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                            modifier = Modifier.size(44.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = item.icon,
                                                    contentDescription = item.title,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = item.title,
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1
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
}

