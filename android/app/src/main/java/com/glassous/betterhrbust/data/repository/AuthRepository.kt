package com.glassous.betterhrbust.data.repository

import com.glassous.betterhrbust.core.database.AppDatabase
import com.glassous.betterhrbust.core.datastore.UserPreferencesManager
import com.glassous.betterhrbust.core.model.AuthState
import com.glassous.betterhrbust.core.model.StudentContext
import com.glassous.betterhrbust.core.network.AcademicHttpClient
import com.glassous.betterhrbust.core.network.CharsetDecoderHelper
import com.glassous.betterhrbust.core.parser.AcademicParsers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

class AuthRepository(
    private val client: AcademicHttpClient,
    private val prefs: UserPreferencesManager,
    private val database: AppDatabase
) {
    private val _isSessionExpired = kotlinx.coroutines.flow.MutableStateFlow(false)
    val isSessionExpired: kotlinx.coroutines.flow.StateFlow<Boolean> = _isSessionExpired

    val authState: Flow<AuthState> = prefs.preferencesFlow.map { pref ->
        if (pref.username.isNotEmpty() && pref.studentId.isNotEmpty()) {
            AuthState.Authenticated(username = pref.username, studentId = pref.studentId)
        } else {
            AuthState.Unauthenticated
        }
    }

    fun markSessionExpired(expired: Boolean = true) {
        _isSessionExpired.value = expired
    }

    suspend fun getCaptcha(): ByteArray {
        return client.downloadCaptcha()
    }

    suspend fun checkCaptcha(code: String): Boolean {
        return client.checkCaptcha(code)
    }

    fun login(username: String, password: String, captcha: String): Flow<Resource<StudentContext>> = flow {
        emit(Resource.Loading)
        try {
            val responseHtml = client.login(username, password, captcha)
            if (AcademicParsers.isLoginPage(responseHtml)) {
                val errorReason = AcademicParsers.parseLoginFailureReason(responseHtml)
                emit(Resource.Error(errorReason))
                return@flow
            }

            // Successfully logged in! Now fetch student context
            val contextHtml = client.get("student/currcourse/currcourse.jsdo", preferredCharset = CharsetDecoderHelper.GBK)
            if (AcademicParsers.isLoginPage(contextHtml)) {
                emit(Resource.Error("提取学生上下文失败，会话未建立"))
                return@flow
            }

            val studentContext = AcademicParsers.parseStudentContext(contextHtml)
            if (studentContext.studentId.isEmpty()) {
                // If internal studentId wasn't found, try year calculation fallback
                emit(Resource.Error("未获取到内部学生 ID，请重试"))
                return@flow
            }

            // Save to preferences
            prefs.saveAuth(
                username = username,
                studentId = studentContext.studentId,
                year = studentContext.year,
                term = studentContext.term
            )
            _isSessionExpired.value = false

            emit(Resource.Success(studentContext))
        } catch (e: Exception) {
            emit(Resource.Error(e.message ?: "登录请求发生异常", e))
        }
    }

    fun logout(): Flow<Resource<Unit>> = flow {
        emit(Resource.Loading)
        _isSessionExpired.value = false
        try {
            client.logout()
            prefs.clearSession()
            database.profileDao().clearAll()
            database.noticeDao().clearAll()
            emit(Resource.Success(Unit))
        } catch (e: Exception) {
            prefs.clearSession()
            emit(Resource.Success(Unit))
        }
    }
}
