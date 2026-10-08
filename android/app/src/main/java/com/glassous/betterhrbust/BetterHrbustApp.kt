package com.glassous.betterhrbust

import android.app.Application
import com.glassous.betterhrbust.core.database.AppDatabase
import com.glassous.betterhrbust.core.datastore.UserPreferencesManager
import com.glassous.betterhrbust.core.network.AcademicHttpClient
import com.glassous.betterhrbust.data.repository.AcademicRepository
import com.glassous.betterhrbust.data.repository.AuthRepository
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
    }

    companion object {
        lateinit var instance: BetterHrbustApp
            private set
    }
}
