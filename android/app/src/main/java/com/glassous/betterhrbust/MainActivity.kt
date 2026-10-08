package com.glassous.betterhrbust

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.glassous.betterhrbust.core.model.AuthState
import com.glassous.betterhrbust.core.ui.LocalBottomContentInset
import com.glassous.betterhrbust.core.ui.LocalTopContentInset
import com.glassous.betterhrbust.core.ui.components.FloatingNavigationBar
import com.glassous.betterhrbust.core.ui.components.FloatingNavigationBarDefaults
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val app = remember { BetterHrbustApp.instance }
            val prefs by app.preferencesManager.preferencesFlow.collectAsState(initial = null)
            val authState by app.authRepository.authState.collectAsState(initial = AuthState.Unauthenticated)
            val isSessionExpired by app.authRepository.isSessionExpired.collectAsState()
            val shouldPromptReLogin by app.authRepository.shouldPromptReLogin.collectAsState()

            val isSystemDark = isSystemInDarkTheme()
            val useDarkTheme = prefs?.darkTheme ?: isSystemDark
            val useDynamicColor = prefs?.dynamicColor ?: true

            // 系统栏图标明暗跟随应用内主题（支持与系统主题不一致的手动切换）
            val window = this@MainActivity.window
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !useDarkTheme
                    isAppearanceLightNavigationBars = !useDarkTheme
                }
            }

            BetterHRBUSTTheme(
                darkTheme = useDarkTheme,
                dynamicColor = useDynamicColor
            ) {
                MainAppScaffold(
                    authState = authState,
                    isSessionExpired = isSessionExpired,
                    shouldPromptReLogin = shouldPromptReLogin
                )
            }
        }
    }
}

@Composable
fun MainAppScaffold(
    authState: AuthState,
    isSessionExpired: Boolean,
    shouldPromptReLogin: Boolean = isSessionExpired
) {
    val syncManager = remember { BetterHrbustApp.instance.syncManager }
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    var showReLoginSheet by remember { mutableStateOf(false) }
    var initialized by remember { mutableStateOf(false) }
    var lastAuthenticated by remember { mutableStateOf(false) }

    val isAuthScreen = currentDestination?.hasRoute(AuthRoute::class) == true
    val startDestination = if (authState is AuthState.Authenticated) {
        DashboardRoute
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

    // 会话失效提示按一周节流；仅在应提示时展示顶部横幅
    val showSessionBanner = !isAuthScreen && shouldPromptReLogin
    val showBottomNav = !isAuthScreen

    // 系统栏安全间距（dp）
    val statusBarInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navigationBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    // 顶部让位：会话过期横幅已占据状态栏区域时不再重复让位
    val topContentInset = if (showSessionBanner) 0.dp else statusBarInset
    // 底部让位：系统导航栏（小白条）+ 悬浮导航坞整体（高度 + 悬浮间距）
    val bottomContentInset = if (showBottomNav) {
        navigationBarInset +
            FloatingNavigationBarDefaults.BottomMargin +
            FloatingNavigationBarDefaults.Height
    } else {
        navigationBarInset
    }

    CompositionLocalProvider(
        LocalTopContentInset provides topContentInset,
        LocalBottomContentInset provides bottomContentInset,
        // 原 Scaffold 会依据背景色提供内容色；改为全屏穿透布局后需在此显式提供，
        // 否则未指定颜色的文本/图标会回退到默认黑色，在深色模式下显示异常
        LocalContentColor provides MaterialTheme.colorScheme.onBackground
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 当会话已过期且在应用内主界面时，在顶部显示重新登录提示条（类似 Web 端 AppHeader）
                if (showSessionBanner) {
                    SessionExpiredBanner(
                        onReLoginClick = { showReLoginSheet = true },
                        modifier = Modifier.statusBarsPadding()
                    )
                }

                NavHost(
                    navController = navController,
                    startDestination = startDestination,
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                ) {
                    composable<AuthRoute> {
                        AuthScreen(
                            onLoginSuccess = {
                                navController.navigate(DashboardRoute) {
                                    popUpTo(AuthRoute) { inclusive = true }
                                }
                            }
                        )
                    }

                    composable<DashboardRoute> {
                        DashboardScreen(
                            onNavigate = { route ->
                                navController.navigate(route)
                            }
                        )
                    }

                    composable<TimetableRoute> {
                        TimetableScreen()
                    }

                    composable<ScoresRoute> {
                        ScoresScreen()
                    }

                    composable<ExamsRoute> {
                        ExamsScreen()
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

                    composable<SettingsRoute> {
                        NoticesSettingsScreen(
                            onLogout = {
                                navController.navigate(AuthRoute) {
                                    popUpTo(DashboardRoute) { inclusive = true }
                                }
                            }
                        )
                    }
                }
            }

            // 悬浮胶囊导航坞：内容全屏穿透，导航坞悬浮于内容之上
            if (showBottomNav) {
                FloatingNavigationBar(
                    destinations = TopLevelDestination.entries,
                    isSelected = { destination ->
                        currentDestination?.hierarchy?.any {
                            it.hasRoute(destination.route::class)
                        } == true
                    },
                    onNavigate = { destination ->
                        navController.navigate(destination.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }

    if (showReLoginSheet) {
        ReLoginBottomSheet(
            onDismiss = { showReLoginSheet = false }
        )
    }
}
