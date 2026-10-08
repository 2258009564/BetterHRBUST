package com.glassous.betterhrbust.feature.scores

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.glassous.betterhrbust.data.repository.Resource
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ScoresScreen(
    modifier: Modifier = Modifier
) {
    val app = remember { BetterHrbustApp.instance }
    val academicRepo = remember { app.academicRepository }
    val prefsManager = remember { app.preferencesManager }
    val coroutineScope = rememberCoroutineScope()

    val prefs by prefsManager.preferencesFlow.collectAsState(initial = null)
    var isRefreshing by remember { mutableStateOf(false) }
    var scoreResult by remember { mutableStateOf<ScoreResult?>(null) }

    var searchQuery by remember { mutableStateOf("") }
    var selectedProperty by remember { mutableStateOf("全部") } // 全部, 必修, 限选, 任选
    var selectedPassStatus by remember { mutableStateOf("全部") } // 全部, 仅及格, 未通过

    var selectedItem by remember { mutableStateOf<ScoreItem?>(null) }
    val navigator = rememberListDetailPaneScaffoldNavigator<ScoreItem>()

    fun loadData(force: Boolean = false) {
        val currentPrefs = prefs ?: return
        coroutineScope.launch {
            isRefreshing = true
            academicRepo.getScores(currentPrefs.studentId, force).collect { res ->
                if (res is Resource.Success) {
                    scoreResult = res.data
                }
            }
            isRefreshing = false
        }
    }

    LaunchedEffect(prefs?.studentId) {
        if (prefs != null && prefs!!.studentId.isNotEmpty()) {
            loadData(force = false)
        }
    }

    val stats = remember(scoreResult) {
        scoreResult?.scores?.let { AcademicParsers.calculateGpaStats(it) }
    }

    val filteredScores = remember(scoreResult, searchQuery, selectedProperty, selectedPassStatus) {
        val all = scoreResult?.scores ?: emptyList()
        all.filter { item ->
            val matchQuery = searchQuery.isBlank() || item.courseName.contains(searchQuery, ignoreCase = true) || item.courseId.contains(searchQuery, ignoreCase = true)
            val matchProp = selectedProperty == "全部" || item.property.contains(selectedProperty)
            val matchPass = when (selectedPassStatus) {
                "仅及格" -> item.passed
                "未通过" -> !item.passed
                else -> true
            }
            matchQuery && matchProp && matchPass
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
                isRefreshing = isRefreshing,
                onRefresh = { loadData(force = true) },
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
                    // GPA Stats Summary
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
                                    text = "学业分析概览",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(
                                            text = "${stats?.gpa ?: 0.0}",
                                            style = MaterialTheme.typography.headlineMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(text = "累计 GPA (4.0)", style = MaterialTheme.typography.labelSmall)
                                    }
                                    Column {
                                        Text(
                                            text = "${stats?.weightedAvg ?: 0.0}",
                                            style = MaterialTheme.typography.headlineMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                        Text(text = "加权均分", style = MaterialTheme.typography.labelSmall)
                                    }
                                    Column {
                                        Text(
                                            text = "${stats?.earnedCredits ?: 0.0}/${stats?.totalCredits ?: 0.0}",
                                            style = MaterialTheme.typography.headlineMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.tertiary
                                        )
                                        Text(text = "已获/总学分", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
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
                            text = "共找到 ${filteredScores.size} 门课程记录",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }

                    if (filteredScores.isEmpty()) {
                        item {
                            EmptyView(title = "未找到成绩记录", description = "请尝试更换筛选条件或下拉刷新")
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
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "${item.year}学年 • ${item.property} • ${item.credit} 学分",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (item.passed) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
                                    ) {
                                        Text(
                                            text = item.score,
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = if (item.passed) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.error,
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
                            text = "课程代码: ${item.courseId} • 课序号: ${item.courseSeq}",
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
