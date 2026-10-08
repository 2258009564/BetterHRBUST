package com.glassous.betterhrbust

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.glassous.betterhrbust.core.model.AuthState
import com.glassous.betterhrbust.core.ui.LocalBottomContentInset
import com.glassous.betterhrbust.core.ui.LocalTopContentInset
import com.glassous.betterhrbust.core.ui.components.NavigationDock
import com.glassous.betterhrbust.core.ui.components.NavigationDockDestination
import com.glassous.betterhrbust.core.ui.components.navigationDockInset
import com.glassous.betterhrbust.core.ui.components.rememberNavigationDockCollapseConnection
import com.glassous.betterhrbust.core.ui.components.rememberNavigationDockCollapseState
import com.glassous.betterhrbust.core.ui.theme.BetterHRBUSTTheme
import com.glassous.betterhrbust.feature.auth.AuthScreen
import com.glassous.betterhrbust.feature.auth.ReLoginBottomSheet
import com.glassous.betterhrbust.feature.auth.SessionExpiredBanner
import com.glassous.betterhrbust.feature.classrooms.ClassroomsScreen
import com.glassous.betterhrbust.feature.courses.CoursesScreen
import com.glassous.betterhrbust.feature.dashboard.DashboardScreen
import com.glassous.betterhrbust.feature.exams.ExamsScreen
import com.glassous.betterhrbust.feature.profile.ProfileScreen
import com.glassous.betterhrbust.feature.program.ProgramScreen
import com.glassous.betterhrbust.feature.scores.ScoresScreen
import com.glassous.betterhrbust.feature.settings.NoticesSettingsScreen
import com.glassous.betterhrbust.feature.timetable.TimetableScreen
import com.glassous.betterhrbust.navigation.*
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val app = remember { BetterHrbustApp.instance }
            val prefs by app.preferencesManager.preferencesFlow.collectAsState(initial = null)
            // 会话状态初始为 null（尚未从 DataStore 读出）：
            // 若直接给 Unauthenticated 作为初值，会先渲染登录页再跳到首页，出现"一闪而过的登录页"
            val authState by app.authRepository.authState.collectAsState(initial = null)
            val isSessionExpired by app.authRepository.isSessionExpired.collectAsState()
            val shouldPromptReLogin by app.authRepository.shouldPromptReLogin.collectAsState()
            val sessionPromptDismissed by app.authRepository.sessionPromptDismissed.collectAsState()

            val isSystemDark = isSystemInDarkTheme()
            val useDarkTheme = prefs?.darkTheme ?: isSystemDark

            // 系统栏图标明暗跟随应用内主题（支持与系统主题不一致的手动切换）
            val window = this@MainActivity.window
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !useDarkTheme
                    isAppearanceLightNavigationBars = !useDarkTheme
                }
            }

            // 动态取色固定开启：Android 12+ 一律使用壁纸取色
            BetterHRBUSTTheme(
                darkTheme = useDarkTheme,
                dynamicColor = true
            ) {
                val resolvedAuthState = authState
                if (resolvedAuthState == null) {
                    // 首帧占位：仅渲染与主题一致的底色，等会话状态就绪后再决定落地页
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                    )
                } else {
                    MainAppScaffold(
                        authState = resolvedAuthState,
                        isSessionExpired = isSessionExpired,
                        shouldPromptReLogin = shouldPromptReLogin,
                        sessionPromptDismissed = sessionPromptDismissed
                    )
                }
            }
        }
    }
}

@Composable
fun MainAppScaffold(
    authState: AuthState,
    isSessionExpired: Boolean,
    shouldPromptReLogin: Boolean = isSessionExpired,
    sessionPromptDismissed: Boolean = false
) {
    val authRepo = remember { BetterHrbustApp.instance.authRepository }
    val syncManager = remember { BetterHrbustApp.instance.syncManager }
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    var showReLoginSheet by remember { mutableStateOf(false) }
    var initialized by remember { mutableStateOf(false) }
    var lastAuthenticated by remember { mutableStateOf(false) }

    val isAuthScreen = currentDestination?.hasRoute(AuthRoute::class) == true
    val isMainScreen = currentDestination?.hasRoute(MainRoute::class) == true ||
        currentDestination?.hasRoute(DashboardRoute::class) == true

    val startDestination: Any = if (authState is AuthState.Authenticated) {
        MainRoute
    } else {
        AuthRoute
    }

    // 数据策略：用户主动登录成功 → 立即全量同步；
    // 冷启动已登录 → 仅"每天首次打开"自动同步一次；其余一律只读本地缓存
    LaunchedEffect(authState) {
        val authenticated = authState is AuthState.Authenticated
        if (!initialized) {
            initialized = true
            lastAuthenticated = authenticated
            if (authenticated) {
                syncManager.ensureDailySync()
            }
        } else if (authenticated && !lastAuthenticated) {
            // 未登录 → 已登录：用户主动登录成功
            lastAuthenticated = true
            syncManager.syncAll()
        } else {
            lastAuthenticated = authenticated
        }
    }

    // 会话失效提示按一周节流；仅在应提示且用户未忽略时展示顶部横幅
    val showSessionBanner = !isAuthScreen && shouldPromptReLogin && !sessionPromptDismissed

    // 系统栏安全间距（dp）
    val statusBarInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navigationBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    // 顶部让位：会话过期横幅已占据状态栏区域时不再重复让位
    val topContentInset = if (showSessionBanner) 0.dp else statusBarInset
    // 底部让位：主界面为底部导航坞让位，其它二级页面直接让位给系统导航条
    val bottomContentInset = if (isMainScreen) {
        navigationDockInset()
    } else {
        navigationBarInset
    }

    CompositionLocalProvider(
        LocalTopContentInset provides topContentInset,
        LocalBottomContentInset provides bottomContentInset,
        LocalContentColor provides MaterialTheme.colorScheme.onBackground
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 当会话已过期且在应用内主界面时，在顶部显示重新登录提示条
                if (showSessionBanner) {
                    SessionExpiredBanner(
                        onReLoginClick = { showReLoginSheet = true },
                        onDismiss = { authRepo.dismissSessionPrompt() },
                        modifier = Modifier.statusBarsPadding()
                    )
                }

                NavHost(
                    navController = navController,
                    startDestination = startDestination,
                    enterTransition = { slideInHorizontally(tween(320)) { it } + fadeIn(tween(320)) },
                    exitTransition = { slideOutHorizontally(tween(320)) { -it / 4 } + fadeOut(tween(320)) },
                    popEnterTransition = { slideInHorizontally(tween(320)) { -it / 4 } + fadeIn(tween(320)) },
                    popExitTransition = { slideOutHorizontally(tween(320)) { it } + fadeOut(tween(320)) },
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                ) {
                    composable<AuthRoute> {
                        AuthScreen(
                            onLoginSuccess = {
                                navController.navigate(MainRoute) {
                                    popUpTo(AuthRoute) { inclusive = true }
                                }
                            }
                        )
                    }

                    composable<MainRoute> {
                        MainPagerScreen(
                            navController = navController,
                            onLogout = {
                                navController.navigate(AuthRoute) {
                                    popUpTo(MainRoute) { inclusive = true }
                                }
                            },
                            onReLogin = {
                                navController.navigate(AuthRoute) {
                                    launchSingleTop = true
                                }
                            }
                        )
                    }

                    // 兼容旧的 DashboardRoute 目标
                    composable<DashboardRoute> {
                        MainPagerScreen(
                            navController = navController,
                            onLogout = {
                                navController.navigate(AuthRoute) {
                                    popUpTo(DashboardRoute) { inclusive = true }
                                }
                            },
                            onReLogin = {
                                navController.navigate(AuthRoute) {
                                    launchSingleTop = true
                                }
                            }
                        )
                    }

                    composable<ProgramRoute> {
                        ProgramScreen()
                    }

                    composable<ClassroomsRoute> {
                        ClassroomsScreen()
                    }

                    composable<CoursesRoute> {
                        CoursesScreen()
                    }

                    composable<ProfileRoute> {
                        ProfileScreen()
                    }
                }
            }
        }
    }

    if (showReLoginSheet) {
        ReLoginBottomSheet(
            onDismiss = { showReLoginSheet = false }
        )
    }
}

/**
 * 承载 5 个一级导航 Tab 的主页面容器：
 * - 使用 [HorizontalPager] 承载，提供平滑左右滑动切换动画，[beyondViewportPageCount] 保活所有页面状态；
 * - [userScrollEnabled] 设为 false，禁止手势直接翻页，仅由底部导航坞驱动；
 * - 底部悬浮 [NavigationDock]，并在容器挂载滚动折叠监听。
 */
@Composable
fun MainPagerScreen(
    navController: NavController,
    onLogout: () -> Unit,
    onReLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 5 })
    var tabIndex by rememberSaveable { mutableStateOf(0) }
    val currentTab = tabIndex

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage to pagerState.isScrollInProgress }
            .collect { (page, scrolling) ->
                if (!scrolling && page != tabIndex) tabIndex = page
            }
    }

    val dockCollapseState = rememberNavigationDockCollapseState()
    val dockCollapseConnection = rememberNavigationDockCollapseConnection(dockCollapseState)

    val dockDestinations = remember {
        TopLevelDestination.entries.map {
            NavigationDockDestination(glyph = it.selectedIcon, label = it.label)
        }
    }

    // 非「概览」标签时，系统返回键平滑回到「概览」
    BackHandler(enabled = currentTab != 0) {
        tabIndex = 0
        coroutineScope.launch { pagerState.animateScrollToPage(0) }
    }

    val onDashboardNavigate: (Any) -> Unit = { route ->
        val targetTab = when (route) {
            is DashboardRoute, DashboardRoute -> 0
            is TimetableRoute, TimetableRoute -> 1
            is ScoresRoute, ScoresRoute -> 2
            is ExamsRoute, ExamsRoute -> 3
            is SettingsRoute, SettingsRoute -> 4
            else -> -1
        }
        if (targetTab != -1) {
            tabIndex = targetTab
            coroutineScope.launch { pagerState.animateScrollToPage(targetTab) }
        } else {
            navController.navigate(route)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(dockCollapseConnection)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 4,
            userScrollEnabled = false,
        ) { page ->
            when (page) {
                0 -> DashboardScreen(onNavigate = onDashboardNavigate)
                1 -> TimetableScreen()
                2 -> ScoresScreen()
                3 -> ExamsScreen()
                4 -> NoticesSettingsScreen(
                    onLogout = onLogout,
                    onReLogin = onReLogin
                )
            }
        }

        NavigationDock(
            destinations = dockDestinations,
            selectedIndex = currentTab,
            onSelected = { index ->
                tabIndex = index
                coroutineScope.launch { pagerState.animateScrollToPage(index) }
            },
            collapseState = dockCollapseState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
