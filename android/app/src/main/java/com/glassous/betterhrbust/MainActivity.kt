package com.glassous.betterhrbust

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.glassous.betterhrbust.core.model.AuthState
import com.glassous.betterhrbust.core.ui.theme.BetterHRBUSTTheme
import com.glassous.betterhrbust.feature.auth.AuthScreen
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

            val isSystemDark = isSystemInDarkTheme()
            val useDarkTheme = prefs?.darkTheme ?: isSystemDark
            val useDynamicColor = prefs?.dynamicColor ?: true

            BetterHRBUSTTheme(
                darkTheme = useDarkTheme,
                dynamicColor = useDynamicColor
            ) {
                MainAppScaffold(
                    authState = authState,
                    offlineMode = prefs?.offlineMode ?: false
                )
            }
        }
    }
}

@Composable
fun MainAppScaffold(
    authState: AuthState,
    offlineMode: Boolean
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val isAuthScreen = currentDestination?.hasRoute(AuthRoute::class) == true
    val startDestination = if (authState is AuthState.Authenticated || offlineMode) {
        DashboardRoute
    } else {
        AuthRoute
    }

    val customSuiteType = if (isAuthScreen) {
        NavigationSuiteType.None
    } else {
        NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(
            androidx.compose.material3.adaptive.currentWindowAdaptiveInfo()
        )
    }

    NavigationSuiteScaffold(
        layoutType = customSuiteType,
        navigationSuiteItems = {
            if (!isAuthScreen) {
                TopLevelDestination.entries.forEach { destination ->
                    val isSelected = currentDestination?.hierarchy?.any {
                        it.hasRoute(destination.route::class)
                    } == true

                    item(
                        selected = isSelected,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = if (isSelected) destination.selectedIcon else destination.unselectedIcon,
                                contentDescription = destination.label
                            )
                        },
                        label = { Text(destination.label) }
                    )
                }
            }
        },
        modifier = Modifier.fillMaxSize()
    ) {
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.fillMaxSize()
        ) {
            composable<AuthRoute> {
                AuthScreen(
                    onLoginSuccess = {
                        navController.navigate(DashboardRoute) {
                            popUpTo(AuthRoute) { inclusive = true }
                        }
                    },
                    onOfflineMode = {
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
}
