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
    val offlineMode: Boolean = false
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
            offlineMode = prefs[KEY_OFFLINE_MODE] ?: false
        )
    }

    suspend fun saveAuth(username: String, studentId: String, year: String, term: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_USERNAME] = username
            prefs[KEY_STUDENT_ID] = studentId
            prefs[KEY_YEAR] = year
            prefs[KEY_TERM] = term
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
        }
    }
}
