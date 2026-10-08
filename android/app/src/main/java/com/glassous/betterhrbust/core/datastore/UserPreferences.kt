package com.glassous.betterhrbust.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

data class AppPreferences(
    val username: String = "",
    val studentId: String = "",
    val year: String = "",
    val term: String = "2",
    val currentWeek: Int = 1,
    val selectedWeek: Int = 1,
    val darkTheme: Boolean? = null,
    val dynamicColor: Boolean = true,
    val offlineMode: Boolean = false,
    /** 上次成功登录时间（毫秒时间戳），用于会话失效提示的"一周节流"判定 */
    val lastLoginAt: Long = 0L,
    /** 上次会话失效提示时间（毫秒时间戳） */
    val lastPromptAt: Long = 0L,
    /** 上次全量同步日期（yyyy-MM-dd），用于"每天首次打开自动同步"判定 */
    val lastFullSyncDate: String = "",
    /** 上一轮会话是否已判定失效（持久化，保证冷启动后仍能正确展示提示） */
    val sessionExpired: Boolean = false,
    /** 上一轮会话失效是否已决定要提示（持久化） */
    val promptReLogin: Boolean = false
)

class UserPreferencesManager(private val context: Context) {
    companion object {
        private val KEY_USERNAME = stringPreferencesKey("username")
        private val KEY_STUDENT_ID = stringPreferencesKey("student_id")
        private val KEY_YEAR = stringPreferencesKey("year")
        private val KEY_TERM = stringPreferencesKey("term")
        private val KEY_CURRENT_WEEK = intPreferencesKey("current_week")
        private val KEY_SELECTED_WEEK = intPreferencesKey("selected_week")
        private val KEY_DARK_THEME = stringPreferencesKey("dark_theme") // "system", "dark", "light"
        private val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        private val KEY_OFFLINE_MODE = booleanPreferencesKey("offline_mode")
        private val KEY_LAST_LOGIN_AT = longPreferencesKey("last_login_at")
        private val KEY_LAST_PROMPT_AT = longPreferencesKey("last_prompt_at")
        private val KEY_LAST_FULL_SYNC_DATE = stringPreferencesKey("last_full_sync_date")
        private val KEY_SESSION_EXPIRED = booleanPreferencesKey("session_expired")
        private val KEY_PROMPT_RELOGIN = booleanPreferencesKey("prompt_relogin")
    }

    val preferencesFlow: Flow<AppPreferences> = context.dataStore.data.map { prefs ->
        val darkThemeStr = prefs[KEY_DARK_THEME] ?: "system"
        val darkThemeBool = when (darkThemeStr) {
            "dark" -> true
            "light" -> false
            else -> null
        }
        AppPreferences(
            username = prefs[KEY_USERNAME] ?: "",
            studentId = prefs[KEY_STUDENT_ID] ?: "",
            year = prefs[KEY_YEAR] ?: "",
            term = prefs[KEY_TERM] ?: "2",
            currentWeek = prefs[KEY_CURRENT_WEEK] ?: 1,
            selectedWeek = prefs[KEY_SELECTED_WEEK] ?: 1,
            darkTheme = darkThemeBool,
            dynamicColor = prefs[KEY_DYNAMIC_COLOR] ?: true,
            offlineMode = prefs[KEY_OFFLINE_MODE] ?: false,
            lastLoginAt = prefs[KEY_LAST_LOGIN_AT] ?: 0L,
            lastPromptAt = prefs[KEY_LAST_PROMPT_AT] ?: 0L,
            lastFullSyncDate = prefs[KEY_LAST_FULL_SYNC_DATE] ?: "",
            sessionExpired = prefs[KEY_SESSION_EXPIRED] ?: false,
            promptReLogin = prefs[KEY_PROMPT_RELOGIN] ?: false
        )
    }

    /** 持久化会话失效状态与提示状态 */
    suspend fun setSessionState(expired: Boolean, promptReLogin: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SESSION_EXPIRED] = expired
            prefs[KEY_PROMPT_RELOGIN] = promptReLogin
        }
    }

    suspend fun saveAuth(username: String, studentId: String, year: String, term: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_USERNAME] = username
            prefs[KEY_STUDENT_ID] = studentId
            prefs[KEY_YEAR] = year
            prefs[KEY_TERM] = term
        }
    }

    /** 记录上一次成功登录时间 */
    suspend fun setLastLoginAt(timestamp: Long) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LAST_LOGIN_AT] = timestamp
        }
    }

    /** 记录上一次会话失效提示时间 */
    suspend fun setLastPromptAt(timestamp: Long) {
        context.dataStore.edit { prefs ->
            if (timestamp <= 0L) prefs.remove(KEY_LAST_PROMPT_AT) else prefs[KEY_LAST_PROMPT_AT] = timestamp
        }
    }

    /** 记录上一次全量同步日期（yyyy-MM-dd） */
    suspend fun setLastFullSyncDate(date: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LAST_FULL_SYNC_DATE] = date
        }
    }

    suspend fun setSelectedWeek(week: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SELECTED_WEEK] = week
        }
    }

    suspend fun setCurrentWeek(week: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_CURRENT_WEEK] = week
        }
    }

    suspend fun setDarkTheme(mode: String) { // "system", "dark", "light"
        context.dataStore.edit { prefs ->
            prefs[KEY_DARK_THEME] = mode
        }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_DYNAMIC_COLOR] = enabled
        }
    }

    suspend fun setOfflineMode(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_OFFLINE_MODE] = enabled
        }
    }

    suspend fun clearSession() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_USERNAME)
            prefs.remove(KEY_STUDENT_ID)
            prefs.remove(KEY_YEAR)
            prefs.remove(KEY_TERM)
            prefs.remove(KEY_LAST_LOGIN_AT)
            prefs.remove(KEY_LAST_PROMPT_AT)
            prefs.remove(KEY_LAST_FULL_SYNC_DATE)
            prefs.remove(KEY_SESSION_EXPIRED)
            prefs.remove(KEY_PROMPT_RELOGIN)
        }
    }
}
