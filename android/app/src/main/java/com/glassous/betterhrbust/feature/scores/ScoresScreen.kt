package com.glassous.betterhrbust.feature.scores

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.glassous.betterhrbust.BetterHrbustApp
import com.glassous.betterhrbust.core.model.ScoreItem
import com.glassous.betterhrbust.core.model.ScoreResult
import com.glassous.betterhrbust.core.parser.AcademicParsers
import com.glassous.betterhrbust.core.ui.LocalBottomContentInset
import com.glassous.betterhrbust.core.ui.LocalTopContentInset
import com.glassous.betterhrbust.core.ui.components.AppPullToRefreshBox
import com.glassous.betterhrbust.core.ui.components.EmptyView
import com.glassous.betterhrbust.core.ui.components.LoadingView
import com.glassous.betterhrbust.core.ui.components.ResponsiveStatGrid
import com.glassous.betterhrbust.core.ui.components.StatEntry
import com.glassous.betterhrbust.core.util.GpaCalculator
import com.glassous.betterhrbust.data.repository.Resource
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ScoresScreen(
    modifier: Modifier = Modifier
) {
    val app = remember { BetterHrbustApp.instance }
    val academicRepo = remember { app.academicRepository }
    val authRepo = remember { app.authRepository }
    val prefsManager = remember { app.preferencesManager }
    val syncManager = remember { app.syncManager }
    val coroutineScope = rememberCoroutineScope()

    val prefs by prefsManager.preferencesFlow.collectAsState(initial = null)
    val isSyncing by syncManager.isSyncing.collectAsState()
    var isRefreshing by remember { mutableStateOf(false) }
    var scoreResult by remember { mutableStateOf<ScoreResult?>(null) }
    var cacheError by remember { mutableStateOf("") }

    var searchQuery by remember { mutableStateOf("") }
    var selectedSemester by remember { mutableStateOf("全部") } // 全部 或 "2024 春" 形式的学期标签
    var selectedProperty by remember { mutableStateOf("全部") } // 全部, 必修, 限选, 任选
    var selectedPassStatus by remember { mutableStateOf("全部") } // 全部, 仅及格, 未通过

    var selectedItem by remember { mutableStateOf<ScoreItem?>(null) }
    val navigator = rememberListDetailPaneScaffoldNavigator<ScoreItem>()

    /** 读取本地缓存（离线只读，不联网） */
    fun loadCache() {
        val currentPrefs = prefs ?: return
        coroutineScope.launch {
            academicRepo.getScores(currentPrefs.studentId, cacheOnly = true).collect { res ->
                when (res) {
                    is Resource.Success -> {
                        scoreResult = res.data
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

    val stats = remember(scoreResult) {
        scoreResult?.scores?.let { AcademicParsers.calculateGpaStats(it) }
    }

    // 同一门课的重修/补考记录合并去重后展示
    val dedupedScores = remember(scoreResult) {
        scoreResult?.scores?.let { GpaCalculator.dedupeScores(it) } ?: emptyList()
    }
    // 键规则与 GpaCalculator.courseKey 保持一致（courseId 为空时回退 courseName）
    val recordCountMap = remember(dedupedScores) {
        dedupedScores.associate { it.item.courseId.ifEmpty { it.item.courseName } to it.recordCount }
    }

    // 学期筛选项：各学期按时间先后倒序（最新在前），排序口径与 Web 端 semesterSortKey 一致
    val semesterOptions = remember(dedupedScores) {
        dedupedScores.map { it.item }
            .distinctBy { GpaCalculator.semesterLabel(it.year, it.term) }
            .sortedByDescending { GpaCalculator.semesterSortKey(it.year, it.term) }
            .map { GpaCalculator.semesterLabel(it.year, it.term) }
    }

    // 数据变化（切换账号 / 重新同步）后，若所选学期已不存在则回退到"全部"
    LaunchedEffect(semesterOptions) {
        if (selectedSemester != "全部" && selectedSemester !in semesterOptions) {
            selectedSemester = "全部"
        }
    }

    val filteredScores = remember(dedupedScores, searchQuery, selectedSemester, selectedProperty, selectedPassStatus) {
        val all = dedupedScores.map { it.item }
        all.filter { item ->
            val matchQuery = searchQuery.isBlank() || item.courseName.contains(searchQuery, ignoreCase = true) || item.courseId.contains(searchQuery, ignoreCase = true)
            val matchSemester = selectedSemester == "全部" || GpaCalculator.semesterLabel(item.year, item.term) == selectedSemester
            val matchProp = selectedProperty == "全部" || item.property.contains(selectedProperty)
            val matchPass = when (selectedPassStatus) {
                "仅及格" -> item.passed
                "未通过" -> !item.passed
                else -> true
            }
            matchQuery && matchSemester && matchProp && matchPass
        }
    }

    BackHandler(enabled = navigator.canNavigateBack()) {
        coroutineScope.launch {
            navigator.navigateBack()
        }
    }

    ListDetailPaneScaffold(
        directive = navigator.scaffoldDirective,
        value = navigator.scaffoldValue,
        modifier = modifier.fillMaxSize(),
        listPane = {
            AppPullToRefreshBox(
                isRefreshing = isRefreshing || isSyncing,
                onRefresh = { refreshAll() },
                modifier = Modifier.fillMaxSize()
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(
                        top = LocalTopContentInset.current + 8.dp,
                        bottom = LocalBottomContentInset.current + 16.dp
                    )
                ) {
                    // 学业概览：手机 2×2 / 平板 1×4 自适应
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                Text(
                                    text = "学业概览",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                ResponsiveStatGrid(
                                    entries = listOf(
                                        StatEntry(
                                            label = "GPA",
                                            value = stats?.gpa?.let { String.format("%.2f", it) } ?: "0.00",
                                            valueColor = MaterialTheme.colorScheme.primary
                                        ),
                                        StatEntry(
                                            label = "均分",
                                            value = stats?.weightedAvg?.let { String.format("%.1f", it) } ?: "0.0",
                                            valueColor = MaterialTheme.colorScheme.secondary
                                        ),
                                        StatEntry(
                                            label = "学分",
                                            value = "${stats?.earnedCredits ?: 0.0}/${stats?.totalCredits ?: 0.0}"
                                        ),
                                        StatEntry(
                                            label = "挂科",
                                            value = "${stats?.failedCount ?: 0}",
                                            valueColor = if ((stats?.failedCount ?: 0) > 0) {
                                                MaterialTheme.colorScheme.error
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            }
                                        )
                                    )
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = GpaCalculator.EARNED_CREDITS_NOTE,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }

                    // 特色学业算法：学位证 / 推免 / 学业风险预警
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "特色学业算法",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = GpaCalculator.STATS_SCOPE_NOTE,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                FeatureAlgorithmRow(
                                    title = "① 学位证算法",
                                    value = stats?.degree?.gpa?.let { String.format("%.2f", it) } ?: "--",
                                    qualified = stats?.degree?.qualified == true,
                                    detail = stats?.degree?.let {
                                        "门槛 ${it.threshold} · " +
                                            "E 类最高 1 门 + 剩余 A–E 类最高 1 门"
                                    } ?: "暂无数据"
                                )
                                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                                FeatureAlgorithmRow(
                                    title = "② 推免资格自检",
                                    value = stats?.recommend?.let { "${it.retakeCount}/${it.retakeLimit}" } ?: "--",
                                    qualified = stats?.recommend?.qualified == true,
                                    detail = stats?.recommend?.let {
                                        "补考 + 重修累计，上限 ${it.retakeLimit} 门"
                                    } ?: "暂无数据"
                                )
                                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                                FeatureAlgorithmRow(
                                    title = "③ 学业风险预警",
                                    value = stats?.risk?.label ?: "--",
                                    qualified = stats?.risk?.level == "none",
                                    detail = stats?.risk?.description ?: "暂无数据"
                                )
                                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                            }
                        }
                    }

                    // Search and Filters
                    item {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("搜索课程名或课号...") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Close, contentDescription = null)
                                    }
                                }
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        )
                    }

                    // Semester Filter Chips（最新学期在前）
                    if (semesterOptions.isNotEmpty()) {
                        item {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                item {
                                    FilterChip(
                                        selected = selectedSemester == "全部",
                                        onClick = { selectedSemester = "全部" },
                                        label = { Text("全部学期") }
                                    )
                                }
                                items(semesterOptions) { label ->
                                    FilterChip(
                                        selected = selectedSemester == label,
                                        onClick = { selectedSemester = label },
                                        label = { Text(label) }
                                    )
                                }
                            }
                        }
                    }

                    // Segmented Button Row: Property
                    item {
                        val propOptions = listOf("全部", "必修", "限选", "任选")
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            propOptions.forEachIndexed { index, prop ->
                                SegmentedButton(
                                    selected = selectedProperty == prop,
                                    onClick = { selectedProperty = prop },
                                    shape = SegmentedButtonDefaults.itemShape(index = index, count = propOptions.size)
                                ) {
                                    Text(prop, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }

                    // Segmented Button Row: Pass Status
                    item {
                        val statusOptions = listOf("全部", "仅及格", "未通过")
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            statusOptions.forEachIndexed { index, status ->
                                SegmentedButton(
                                    selected = selectedPassStatus == status,
                                    onClick = { selectedPassStatus = status },
                                    shape = SegmentedButtonDefaults.itemShape(index = index, count = statusOptions.size)
                                ) {
                                    Text(status, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }

                    // Results count
                    item {
                        Text(
                            text = "${filteredScores.size} 门课程",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }

                    if (filteredScores.isEmpty()) {
                        item {
                            EmptyView(
                                title = "未找到成绩记录",
                                // 仅当本地确实没有成绩缓存时才提示刷新引导，避免筛选无结果时误报
                                description = if (dedupedScores.isEmpty() && cacheError.isNotEmpty()) {
                                    cacheError
                                } else {
                                    "请尝试更换筛选条件"
                                }
                            )
                        }
                    } else {
                        items(filteredScores) { item ->
                            val isSelected = selectedItem == item
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedItem = item
                                        coroutineScope.launch {
                                            navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, item)
                                        }
                                    },
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainer
                                    }
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
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = item.courseName,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            if (!item.passed) {
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = MaterialTheme.colorScheme.errorContainer
                                                ) {
                                                    Text(
                                                        text = "未通过",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                            val recordCount =
                                                recordCountMap[item.courseId.ifEmpty { item.courseName }] ?: 1
                                            if (recordCount > 1) {
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = MaterialTheme.colorScheme.secondaryContainer
                                                ) {
                                                    Text(
                                                        text = "补考/重修已合并",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "${GpaCalculator.semesterLabel(item.year, item.term)} • ${item.property} • ${item.credit} 学分",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (!GpaCalculator.isLowScore(item.score)) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
                                    ) {
                                        Text(
                                            text = item.score,
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = if (!GpaCalculator.isLowScore(item.score)) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.error,
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        detailPane = {
            val item = selectedItem ?: navigator.currentDestination?.contentKey
            if (item != null) {
                Card(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            start = 16.dp,
                            end = 16.dp,
                            top = LocalTopContentInset.current + 16.dp,
                            bottom = LocalBottomContentInset.current + 16.dp
                        ),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "课程成绩明细",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            if (navigator.canNavigateBack()) {
                                IconButton(onClick = {
                                    coroutineScope.launch { navigator.navigateBack() }
                                }) {
                                    Icon(Icons.Default.Close, contentDescription = "关闭")
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = item.courseName,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${item.courseId} · ${item.courseSeq}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(text = item.score, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                                    Text(text = "总评分数", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(text = "${item.credit}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                                    Text(text = "学分", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(text = "${item.hours}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                                    Text(text = "学时", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            ScoreDetailItem("修读学年学期", "${item.year} 学年 第 ${item.term} 学期")
                            ScoreDetailItem("选课属性", item.property)
                            ScoreDetailItem("所属课组", item.courseGroup.ifEmpty { "无" })
                            ScoreDetailItem("考试性质", item.examType)
                            ScoreDetailItem("及格状态", if (item.passed) "及格 (${item.passMark})" else "未通过 (${item.passMark})")
                            ScoreDetailItem("备注", item.remark.ifEmpty { "无" })
                        }
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "选择左侧课程查看详情",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    )
}

@Composable
private fun ScoreDetailItem(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

/** 特色算法条目：标题 + 关键指标 + 达标状态 + 说明 */
@Composable
private fun FeatureAlgorithmRow(
    title: String,
    value: String,
    qualified: Boolean,
    detail: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (qualified) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
            Text(
                text = if (qualified) "达标 / 符合" else "未达标 / 需注意",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}
