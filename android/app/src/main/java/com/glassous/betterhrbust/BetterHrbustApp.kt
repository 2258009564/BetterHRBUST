package com.glassous.betterhrbust

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
        updateRepository = UpdateRepository(
            currentVersion = resolveAppVersion(),
            prefs = preferencesManager
        )
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
