package com.glassous.betterhrbust.feature.courses

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.glassous.betterhrbust.BetterHrbustApp
import com.glassous.betterhrbust.core.model.CourseSearchItem
import com.glassous.betterhrbust.core.ui.LocalBottomContentInset
import com.glassous.betterhrbust.core.ui.LocalTopContentInset
import com.glassous.betterhrbust.core.ui.components.EmptyView
import com.glassous.betterhrbust.core.ui.components.LoadingView
import com.glassous.betterhrbust.core.ui.components.PageHeaderTitle
import com.glassous.betterhrbust.data.repository.Resource
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoursesScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val app = remember { BetterHrbustApp.instance }
    val academicRepo = remember { app.academicRepository }
    val coroutineScope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<CourseSearchItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    fun doSearch(keyword: String) {
        if (keyword.isBlank()) return
        coroutineScope.launch {
            isLoading = true
            academicRepo.searchCourses(keyword.trim()).collect { res ->
                if (res is Resource.Success) {
                    searchResults = res.data
                }
                isLoading = false
            }
        }
    }

    // 整页为单一滚动列表：顶部内容（标题/检索框）随滚动穿透状态栏，
    // 初始与末尾内容由 contentPadding 保证不被系统栏与悬浮导航坞遮挡。
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(
            top = LocalTopContentInset.current + 8.dp,
            bottom = LocalBottomContentInset.current + 24.dp
        )
    ) {
        // 页面标题（作为滚动内容，可穿透状态栏）
        item { PageHeaderTitle("我的课程名录", onBack = onBack) }

        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = {
                    searchQuery = it
                    if (it.length >= 2) doSearch(it)
                },
                placeholder = { Text("输入课程名、课号或教师检索...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            )
        }

        if (isLoading) {
            item {
                LoadingView(
                    message = "正在检索课程目录...",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                )
            }
        } else if (searchResults.isEmpty()) {
            item {
                EmptyView(
                    title = "未检索到课程",
                    description = "请在上方输入课程关键词或代码查询",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                )
            }
        } else {
            items(searchResults) { course ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = course.courseName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "${course.credit} 学分",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "${course.courseId} · ${course.department.ifEmpty { "哈尔滨理工大学" }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (course.hours > 0 || course.examWay.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${course.hours} 学时 · ${course.examWay.ifEmpty { "考试" }}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }
        }
    }
}
