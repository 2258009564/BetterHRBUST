package com.glassous.betterhrbust

import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.collect
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import com.glassous.betterhrbust.core.database.AppDatabase
import com.glassous.betterhrbust.core.datastore.UserPreferencesManager
import com.glassous.betterhrbust.core.network.AcademicHttpClient
import com.glassous.betterhrbust.data.repository.AcademicRepository
import com.glassous.betterhrbust.data.repository.AuthRepository
import com.glassous.betterhrbust.data.repository.UpdateRepository
import com.glassous.betterhrbust.data.sync.AcademicSyncManager

class BetterHrbustApp : Application() {
    private val widgetScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)


    lateinit var database: AppDatabase
        private set

    lateinit var preferencesManager: UserPreferencesManager
        private set

    lateinit var httpClient: AcademicHttpClient
        private set

    lateinit var authRepository: AuthRepository
        private set

    lateinit var academicRepository: AcademicRepository
        private set

    lateinit var syncManager: AcademicSyncManager
        private set

    lateinit var updateRepository: UpdateRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = AppDatabase.getInstance(this)
        preferencesManager = UserPreferencesManager(this)
        httpClient = AcademicHttpClient()
        authRepository = AuthRepository(httpClient, preferencesManager, database)
        httpClient.onSessionExpired = {
            authRepository.markSessionExpired(true)
        }
        academicRepository = AcademicRepository(httpClient, database, preferencesManager)
        syncManager = AcademicSyncManager(academicRepository, preferencesManager, authRepository)
        // 登录、退出、同步和教学周变动都刷新桌面；仅观察本地存储，不额外联网。
        widgetScope.launch {
            preferencesManager.preferencesFlow.collectLatest { prefs ->
                kotlinx.coroutines.flow.combine(
                    database.timetableDao().getTimetable(prefs.studentId),
                    database.profileDao().getProfile(prefs.username)
                ) { timetable, profile -> timetable to profile }.collect {
                    refreshWidgets()
                }
            }
        }
        updateRepository = UpdateRepository(
            currentVersion = resolveAppVersion(),
            prefs = preferencesManager
        )
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        // 主进程存活时，系统主题/字号变化立即重算组件，而非复用旧密度布局。
        widgetScope.launch { refreshWidgets() }
    }

    private suspend fun refreshWidgets() {
        try {
            com.glassous.betterhrbust.widget.TimetableWidgetProvider.refreshAll(this)
            com.glassous.betterhrbust.widget.TimetableWidgetUpdater.refreshAll(this)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // 后续本地数据/系统更新会重试，不影响应用启动。
        }
    }

    /** 读取当前应用版本号（versionName）；异常时回退为占位版本 */
    private fun resolveAppVersion(): String = runCatching {
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0)
        }
        info.versionName?.takeIf { it.isNotBlank() } ?: "1.0"
    }.getOrDefault("1.0")

    companion object {
        lateinit var instance: BetterHrbustApp
            private set
    }
}
