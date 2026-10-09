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
    /** 课表视图形态的持久化标识（`TimetableViewMode.name`：WEEK / DAY） */
    val timetableViewMode: String = "WEEK",
    /** 课表节次粒度的持久化标识（`SectionMode.name`：COMBINE / BASE） */
    val timetableSectionMode: String = "COMBINE",
    val darkTheme: Boolean? = null,
    val offlineMode: Boolean = false,
    /** 上次成功登录时间（毫秒时间戳），用于会话失效提示的"一周节流"判定 */
    val lastLoginAt: Long = 0L,
    /** 上次会话失效提示时间（毫秒时间戳） */
    val lastPromptAt: Long = 0L,
    /** 上次全量同步日期（yyyy-MM-dd），用于"每天首次打开自动同步"判定 */
    val lastFullSyncDate: String = "",
    /**
     * 上一轮会话是否已判定失效。
     * 持久化以保证冷启动后登录页仍能提示"登录状态已失效"；
     * 而"是否弹出提示"不做持久化——用户看过一次后，下次启动不再重复弹出。
     */
    val sessionExpired: Boolean = false,
    /** 真实姓名（供概览页在档案缓存缺失时兜底展示，避免回退为占位文案） */
    val realName: String = "",
    /** 记住的登录密码（仅本地 DataStore，用于免重复输入） */
    val savedPassword: String = "",
    /** 上次版本更新检测时间（毫秒时间戳），用于启动静默检测的"按天节流" */
    val lastUpdateCheckAt: Long = 0L,
    /** 导航坞折叠态（用户手动在坞内展开 / 折叠后持久化，冷启动沿用） */
    val navigationDockCollapsed: Boolean = false,
)

class UserPreferencesManager(private val context: Context) {
    companion object {
        private val KEY_USERNAME = stringPreferencesKey("username")
        private val KEY_STUDENT_ID = stringPreferencesKey("student_id")
        private val KEY_YEAR = stringPreferencesKey("year")
        private val KEY_TERM = stringPreferencesKey("term")
        private val KEY_CURRENT_WEEK = intPreferencesKey("current_week")
        private val KEY_SELECTED_WEEK = intPreferencesKey("selected_week")
        private val KEY_TIMETABLE_VIEW_MODE = stringPreferencesKey("timetable_view_mode")
        private val KEY_TIMETABLE_SECTION_MODE = stringPreferencesKey("timetable_section_mode")
        private val KEY_DARK_THEME = stringPreferencesKey("dark_theme") // "system", "dark", "light"
        private val KEY_OFFLINE_MODE = booleanPreferencesKey("offline_mode")
        private val KEY_LAST_LOGIN_AT = longPreferencesKey("last_login_at")
        private val KEY_LAST_PROMPT_AT = longPreferencesKey("last_prompt_at")
        private val KEY_LAST_FULL_SYNC_DATE = stringPreferencesKey("last_full_sync_date")
        private val KEY_SESSION_EXPIRED = booleanPreferencesKey("session_expired")
        private val KEY_REAL_NAME = stringPreferencesKey("real_name")
        private val KEY_SAVED_PASSWORD = stringPreferencesKey("saved_password")
        private val KEY_LAST_UPDATE_CHECK_AT = longPreferencesKey("last_update_check_at")
        private val KEY_NAVIGATION_DOCK_COLLAPSED = booleanPreferencesKey("navigation_dock_collapsed")
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
            timetableViewMode = prefs[KEY_TIMETABLE_VIEW_MODE] ?: "WEEK",
            timetableSectionMode = prefs[KEY_TIMETABLE_SECTION_MODE] ?: "COMBINE",
            darkTheme = darkThemeBool,
            offlineMode = prefs[KEY_OFFLINE_MODE] ?: false,
            lastLoginAt = prefs[KEY_LAST_LOGIN_AT] ?: 0L,
            lastPromptAt = prefs[KEY_LAST_PROMPT_AT] ?: 0L,
            lastFullSyncDate = prefs[KEY_LAST_FULL_SYNC_DATE] ?: "",
            sessionExpired = prefs[KEY_SESSION_EXPIRED] ?: false,
            realName = prefs[KEY_REAL_NAME] ?: "",
            savedPassword = prefs[KEY_SAVED_PASSWORD] ?: "",
            lastUpdateCheckAt = prefs[KEY_LAST_UPDATE_CHECK_AT] ?: 0L,
            navigationDockCollapsed = prefs[KEY_NAVIGATION_DOCK_COLLAPSED] ?: false
        )
    }

    /** 记录真实姓名（用于概览页展示） */
    suspend fun setRealName(name: String) {
        context.dataStore.edit { prefs ->
            if (name.isBlank()) prefs.remove(KEY_REAL_NAME) else prefs[KEY_REAL_NAME] = name
        }
    }

    /** 记住登录密码（仅存本地，便于免重复输入） */
    suspend fun setSavedPassword(password: String) {
        context.dataStore.edit { prefs ->
            if (password.isEmpty()) prefs.remove(KEY_SAVED_PASSWORD) else prefs[KEY_SAVED_PASSWORD] = password
        }
    }

    /**
     * 持久化会话失效状态。
     *
     * 只持久化「是否失效」，「是否弹提示」仅在本次运行期间有效：
     * 用户看过一次提示（或点了忽略）后，再次启动应用不再重复弹出，
     * 而是继续按一周节流来决定下一次提示时机。
     */
    suspend fun setSessionState(expired: Boolean) {
        context.dataStore.edit { prefs ->
            if (expired) prefs[KEY_SESSION_EXPIRED] = true else prefs.remove(KEY_SESSION_EXPIRED)
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

    /** 记录上一次版本更新检测时间（毫秒时间戳，用于启动静默检测的按天节流） */
    suspend fun setLastUpdateCheckAt(timestamp: Long) {
        context.dataStore.edit { prefs ->
            if (timestamp <= 0L) prefs.remove(KEY_LAST_UPDATE_CHECK_AT)
            else prefs[KEY_LAST_UPDATE_CHECK_AT] = timestamp
        }
    }

    /** 记忆导航坞折叠态（用户手动在坞内展开 / 折叠后调用，冷启动沿用） */
    suspend fun setNavigationDockCollapsed(collapsed: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_NAVIGATION_DOCK_COLLAPSED] = collapsed
        }
    }

    suspend fun setSelectedWeek(week: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SELECTED_WEEK] = week
        }
    }

    /** 记忆课表视图形态（周 / 日），下次进入课表页时沿用 */
    suspend fun setTimetableViewMode(mode: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_TIMETABLE_VIEW_MODE] = mode
        }
    }

    /** 记忆课表节次粒度（大节 / 小节），下次进入课表页时沿用 */
    suspend fun setTimetableSectionMode(mode: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_TIMETABLE_SECTION_MODE] = mode
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

    suspend fun setOfflineMode(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_OFFLINE_MODE] = enabled
        }
    }

    /**
     * 退出登录时清理会话数据。
     * 注意：保留「账号」与「记住的密码」，以便下次登录免重复输入（需求：登录页持久保存用户名与密码）。
     */
    suspend fun clearSession() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_STUDENT_ID)
            prefs.remove(KEY_YEAR)
            prefs.remove(KEY_TERM)
            prefs.remove(KEY_LAST_LOGIN_AT)
            prefs.remove(KEY_LAST_PROMPT_AT)
            prefs.remove(KEY_LAST_FULL_SYNC_DATE)
            prefs.remove(KEY_SESSION_EXPIRED)
            prefs.remove(KEY_REAL_NAME)
        }
    }
}
