package com.glassous.betterhrbust.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.glassous.betterhrbust.BetterHrbustApp
import com.glassous.betterhrbust.core.model.NoticeItem
import com.glassous.betterhrbust.core.ui.LocalBottomContentInset
import com.glassous.betterhrbust.core.ui.LocalTopContentInset
import com.glassous.betterhrbust.core.ui.components.AppPullToRefreshBox
import com.glassous.betterhrbust.core.ui.components.EmptyView
import com.glassous.betterhrbust.core.ui.components.LoadingView
import com.glassous.betterhrbust.core.ui.components.PageHeaderTitle
import com.glassous.betterhrbust.data.repository.Resource
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoticesSettingsScreen(
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val app = remember { BetterHrbustApp.instance }
    val academicRepo = remember { app.academicRepository }
    val authRepo = remember { app.authRepository }
    val prefsManager = remember { app.preferencesManager }
    val database = remember { app.database }
    val syncManager = remember { app.syncManager }
    val coroutineScope = rememberCoroutineScope()

    val prefs by prefsManager.preferencesFlow.collectAsState(initial = null)
    val isSyncing by syncManager.isSyncing.collectAsState()
    var isRefreshing by remember { mutableStateOf(false) }
    var notices by remember { mutableStateOf<List<NoticeItem>>(emptyList()) }
    var selectedNotice by remember { mutableStateOf<NoticeItem?>(null) }
    var syncMessage by remember { mutableStateOf("") }
    var cacheError by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState()

    var showClearCacheDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    /**
     * 读取本地缓存（离线只读，不联网）
     * 教务数据仅在登录成功、每天首次打开与手动刷新三种情况下获取
     */
    fun loadCache() {
        coroutineScope.launch {
            academicRepo.getNotices(forceRefresh = false, cacheOnly = true).collect { res ->
                when (res) {
                    is Resource.Success -> {
                        notices = res.data
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
            syncMessage = outcome.message
            isRefreshing = false
            if (outcome.expired) {
                authRepo.markSessionExpired(true)
            } else {
                loadCache()
            }
        }
    }

    LaunchedEffect(Unit) {
        loadCache()
    }

    Box(modifier = modifier.fillMaxSize()) {
        AppPullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { refreshAll() },
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(
                    top = LocalTopContentInset.current + 8.dp,
                    bottom = LocalBottomContentInset.current + 32.dp
                )
            ) {
                // 第一行：页面标题 + 手动刷新按钮（登录后教务数据不再自动获取）
                item {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            PageHeaderTitle("通知与应用设置", modifier = Modifier.weight(1f))
                            FilledTonalIconButton(
                                onClick = { refreshAll() },
                                enabled = !isSyncing
                            ) {
                                if (isSyncing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "手动刷新教务数据"
                                    )
                                }
                            }
                        }
                        if (syncMessage.isNotEmpty()) {
                            Text(
                                text = syncMessage,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }

                // Section: Academic Notices
                item {
                    Text(
                        text = "教务运行通知 (${notices.size} 条)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (notices.isEmpty() && isRefreshing) {
                    item {
                        LoadingView(message = "正在加载教务通知...")
                    }
                } else if (notices.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = cacheError.ifEmpty { "暂无新的教务公告" },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                } else {
                    items(notices) { notice ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedNotice = notice },
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = notice.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 2
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = notice.date,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                }

                // Section: System & Theme Settings
                item {
                    Text(
                        text = "偏好与系统设置",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            // Dark mode selection
                            Column {
                                Text(text = "深浅色主题模式", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Spacer(modifier = Modifier.height(8.dp))
                                val currentMode = when (prefs?.darkTheme) {
                                    true -> "深色"
                                    false -> "浅色"
                                    null -> "系统"
                                }
                                val modes = listOf("系统", "浅色", "深色")
                                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                    modes.forEachIndexed { index, mode ->
                                        SegmentedButton(
                                            selected = currentMode == mode,
                                            onClick = {
                                                coroutineScope.launch {
                                                    val saveVal = when (mode) {
                                                        "深色" -> "dark"
                                                        "浅色" -> "light"
                                                        else -> "system"
                                                    }
                                                    prefsManager.setDarkTheme(saveVal)
                                                }
                                            },
                                            shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size)
                                        ) {
                                            Text(mode, style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }

                            HorizontalDivider()

                            // Dynamic Color Switch
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(text = "Material You 动态取色", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    Text(text = "基于系统壁纸生成配色主题", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                }
                                Switch(
                                    checked = prefs?.dynamicColor ?: true,
                                    onCheckedChange = { checked ->
                                        coroutineScope.launch { prefsManager.setDynamicColor(checked) }
                                    }
                                )
                            }

                            HorizontalDivider()

                            // Clear Cache
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showClearCacheDialog = true },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(text = "清理离线本地缓存", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    Text(text = "清除本地缓存的课表、成绩与考试数据", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                }
                                Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }

                // Logout Button
                item {
                    Button(
                        onClick = { showLogoutDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.ExitToApp, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("退出教务账号登录", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Modal Bottom Sheet for notice detail
        if (selectedNotice != null) {
            val n = selectedNotice!!
            ModalBottomSheet(
                onDismissRequest = { selectedNotice = null },
                sheetState = sheetState,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                ) {
                    Text(text = n.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "发布日期: ${n.date}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = n.content, style = MaterialTheme.typography.bodyMedium, lineHeight = MaterialTheme.typography.bodyLarge.lineHeight)
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { selectedNotice = null },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("关闭")
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }

        // Clear Cache Confirmation Dialog
        if (showClearCacheDialog) {
            AlertDialog(
                onDismissRequest = { showClearCacheDialog = false },
                title = { Text("确认清理缓存？") },
                text = { Text("清理后离线时将无法查看已保存的数据，直到下次连网刷新。") },
                confirmButton = {
                    TextButton(onClick = {
                        coroutineScope.launch {
                            database.profileDao().clearAll()
                            database.noticeDao().clearAll()
                            showClearCacheDialog = false
                        }
                    }) {
                        Text("确认清理", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearCacheDialog = false }) {
                        Text("取消")
                    }
                }
            )
        }

        // Logout Confirmation Dialog
        if (showLogoutDialog) {
            AlertDialog(
                onDismissRequest = { showLogoutDialog = false },
                title = { Text("退出登录") },
                text = { Text("确定要注销当前教务在线会话并返回登录页面吗？") },
                confirmButton = {
                    TextButton(onClick = {
                        showLogoutDialog = false
                        coroutineScope.launch {
                            authRepo.logout().collect {
                                onLogout()
                            }
                        }
                    }) {
                        Text("确定退出", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showLogoutDialog = false }) {
                        Text("取消")
                    }
                }
            )
        }
    }
}
