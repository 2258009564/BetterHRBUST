package com.glassous.betterhrbust.feature.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.glassous.betterhrbust.BetterHrbustApp
import com.glassous.betterhrbust.core.model.PersonalInfo
import com.glassous.betterhrbust.core.ui.LocalBottomContentInset
import com.glassous.betterhrbust.core.ui.LocalTopContentInset
import com.glassous.betterhrbust.core.ui.components.AppPullToRefreshBox
import com.glassous.betterhrbust.core.ui.components.LoadingView
import com.glassous.betterhrbust.core.ui.components.PageHeaderTitle
import com.glassous.betterhrbust.data.repository.Resource
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    modifier: Modifier = Modifier
) {
    val app = remember { BetterHrbustApp.instance }
    val academicRepo = remember { app.academicRepository }
    val prefsManager = remember { app.preferencesManager }
    val coroutineScope = rememberCoroutineScope()

    val prefs by prefsManager.preferencesFlow.collectAsState(initial = null)
    var isRefreshing by remember { mutableStateOf(false) }
    var profile by remember { mutableStateOf<PersonalInfo?>(null) }

    fun loadData(force: Boolean = false) {
        val currentPrefs = prefs ?: return
        coroutineScope.launch {
            isRefreshing = true
            academicRepo.getPersonalInfo(currentPrefs.username, force).collect { res ->
                if (res is Resource.Success) {
                    profile = res.data
                }
            }
            isRefreshing = false
        }
    }

    LaunchedEffect(prefs?.username) {
        if (prefs != null && prefs!!.username.isNotEmpty()) {
            loadData(force = false)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AppPullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { loadData(force = true) },
            modifier = Modifier.fillMaxSize()
        ) {
            val p = profile
            if (p == null && isRefreshing) {
                LoadingView(message = "正在加载学籍信息...")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(
                        top = LocalTopContentInset.current + 8.dp,
                        bottom = LocalBottomContentInset.current + 24.dp
                    )
                ) {
                    // 页面标题（作为滚动内容，可穿透状态栏）
                    item { PageHeaderTitle("学籍档案与基本资料") }

                    // Profile Header Card
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(64.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.AccountCircle,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(44.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = p?.realName?.ifEmpty { "哈理工同学" } ?: "哈理工同学",
                                            style = MaterialTheme.typography.headlineSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.primary
                                        ) {
                                            Text(
                                                text = p?.status ?: "在籍",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "学号: ${p?.studentNumber?.ifEmpty { prefs?.username } ?: prefs?.username ?: "未知"}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }
                    }

                    // Academic Info Card
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                        ) {
                            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(text = "学业学籍信息", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                HorizontalDivider()
                                ProfileField("所在院系", p?.college ?: "")
                                ProfileField("专业名称", p?.major ?: "")
                                if (!p?.direction.isNullOrBlank()) {
                                    ProfileField("专业方向", p.direction)
                                }
                                ProfileField("行政班级", p?.className ?: "")
                                ProfileField("入学年级", p?.grade ?: "")
                                ProfileField("学生类别", p?.studentType ?: "本科生")
                            }
                        }
                    }

                    // Contact Info Card
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                        ) {
                            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(text = "个人联络资料", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                HorizontalDivider()
                                ProfileField("联系电话", p?.phone ?: "未填")
                                ProfileField("电子邮箱", p?.email ?: "未填")
                                ProfileField("通讯地址", p?.address ?: "未填")
                                ProfileField("邮政编码", p?.postalCode ?: "未填")
                            }
                        }
                    }

                    // Status changes
                    val changes = p?.changes ?: emptyList()
                    if (changes.isNotEmpty()) {
                        item {
                            Text(text = "学籍异动记录 (${changes.size} 条)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }

                        items(changes) { ch ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(text = ch.type, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                        Text(text = ch.date, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                    }
                                    if (ch.reason.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(text = "原因: ${ch.reason}", style = MaterialTheme.typography.bodySmall)
                                    }
                                    if (ch.remark.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(text = "备注: ${ch.remark}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
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

@Composable
private fun ProfileField(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
        Text(text = value.ifEmpty { "未登记" }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
