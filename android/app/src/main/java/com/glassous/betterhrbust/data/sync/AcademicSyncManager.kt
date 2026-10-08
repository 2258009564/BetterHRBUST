package com.glassous.betterhrbust.data.sync

import com.glassous.betterhrbust.core.datastore.UserPreferencesManager
import com.glassous.betterhrbust.data.repository.AcademicRepository
import com.glassous.betterhrbust.data.repository.AuthRepository
import com.glassous.betterhrbust.data.repository.Resource
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.last
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 教务数据全量同步管理器
 *
 * 策略（与用户约定一致）：
 * 1. 登录成功后一次性全量拉取（档案 / 课表 / 成绩 / 考试 / 培养方案 / 公告）并写入 Room 持久化；
 * 2. 之后各页面仅读本地缓存（`cacheOnly = true`），不再自动联网；
 * 3. 仅在【每天首次打开】与【用户手动刷新】两种情况下触网；
 * 4. 手动刷新时若会话已失效，则要求用户重新登录（由 AuthRepository 的节流判定决定是否弹提示）。
 */
class AcademicSyncManager(
    private val academicRepo: AcademicRepository,
    private val prefs: UserPreferencesManager,
    private val authRepo: AuthRepository
) {

    /** 同步结果 */
    data class SyncOutcome(
        val success: Boolean,
        /** 会话失效（需要重新登录） */
        val expired: Boolean,
        val message: String
    )

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing

    private val _lastSyncMessage = MutableStateFlow("")
    val lastSyncMessage: StateFlow<String> = _lastSyncMessage

    private fun todayKey(): String = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

    /** 今天是否尚未做过全量同步 */
    suspend fun needsDailySync(): Boolean {
        val snapshot = prefs.preferencesFlow.firstOrNull() ?: return false
        if (snapshot.studentId.isEmpty()) return false
        return snapshot.lastFullSyncDate != todayKey()
    }

    /**
     * 每天首次打开应用时自动全量同步
     * @return 是否真正执行了同步
     */
    suspend fun ensureDailySync(): Boolean {
        if (!needsDailySync()) return false
        syncAll(manual = false)
        return true
    }

    /**
     * 执行一次全量同步（各端点并发）
     * @param manual 是否由用户手动刷新触发（失效时无条件要求重新登录）
     */
    suspend fun syncAll(manual: Boolean = false): SyncOutcome {
        if (_isSyncing.value) {
            return SyncOutcome(success = false, expired = false, message = "正在同步中，请稍候")
        }

        // 手动刷新：随后的会话失效需要无条件要求重新登录
        if (manual) authRepo.notifyManualRefreshIntent()

        val snapshot = prefs.preferencesFlow.firstOrNull()
            ?: return SyncOutcome(success = false, expired = false, message = "无法读取本地登录信息")

        if (snapshot.studentId.isEmpty()) {
            authRepo.markSessionExpired(true)
            return SyncOutcome(success = false, expired = true, message = "当前未登录，请重新登录后再刷新数据")
        }

        _isSyncing.value = true
        _lastSyncMessage.value = ""

        return try {
            val studentId = snapshot.studentId
            val results = coroutineScope {
                val tasks = mutableListOf(
                    async { academicRepo.getScores(studentId, forceRefresh = true).last() },
                    async { academicRepo.getCurriculumPlan(studentId, forceRefresh = true).last() },
                    async { academicRepo.getExams(studentId, forceRefresh = true).last() },
                    async { academicRepo.getNotices(forceRefresh = true).last() },
                    async { academicRepo.getTeachingWeek().last() }
                )
                if (snapshot.username.isNotEmpty()) {
                    tasks.add(async { academicRepo.getPersonalInfo(snapshot.username, forceRefresh = true).last() })
                }
                if (snapshot.year.isNotEmpty() && snapshot.term.isNotEmpty()) {
                    tasks.add(
                        async {
                            academicRepo.getTimetable(
                                studentId = studentId,
                                year = snapshot.year,
                                term = snapshot.term,
                                forceRefresh = true
                            ).last()
                        }
                    )
                }
                tasks.awaitAll()
            }

            if (authRepo.isSessionExpired.value) {
                SyncOutcome(success = false, expired = true, message = "登录状态已失效，请重新登录后再刷新数据")
            } else {
                val failed = results.filterIsInstance<Resource.Error>()
                prefs.setLastFullSyncDate(todayKey())
                val message = if (failed.isEmpty()) {
                    "数据已全部同步"
                } else {
                    "部分数据同步失败（${failed.size} 项）：${failed.first().message}"
                }
                _lastSyncMessage.value = message
                SyncOutcome(success = failed.isEmpty(), expired = false, message = message)
            }
        } catch (e: Exception) {
            val message = "同步异常：${e.message ?: "未知错误"}"
            _lastSyncMessage.value = message
            SyncOutcome(success = false, expired = authRepo.isSessionExpired.value, message = message)
        } finally {
            _isSyncing.value = false
        }
    }
}
