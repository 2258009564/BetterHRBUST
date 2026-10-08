package com.glassous.betterhrbust.data.repository

import android.util.Log
import com.glassous.betterhrbust.core.datastore.UserPreferencesManager
import com.glassous.betterhrbust.core.model.GitHubRelease
import com.glassous.betterhrbust.core.model.UpdateState
import com.glassous.betterhrbust.core.util.VersionComparator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * 版本更新检测仓库（数据源：GitHub Release）
 *
 * 与桌面端 `web/src/services/update.js` 完全同口径：
 * - 请求 `GET https://api.github.com/repos/Glassous/BetterHRBUST/releases/latest`；
 * - 仓库尚无任何 Release 时返回 404，属正常状态：按「暂无更新」处理，不报错；
 * - 版本比较走 [VersionComparator]（剥离 v / android-v 等前缀后按段数值比较）；
 * - 检测结果以 [UpdateState] 暴露，UI 侧只做展示与跳转，不做应用内下载安装。
 *
 * 节流：启动时的静默检测按天节流（见 [checkIfNeeded]），把请求量压到日均 ≤1 次，
 * 远低于 GitHub 未认证 API 的 60 次/小时限流。
 *
 * 使用独立的 OkHttpClient：不携带教务 Cookie / Referer，避免污染教务会话链路。
 */
class UpdateRepository(
    /** 当前应用版本（versionName，由 Application 从 PackageManager 读取） */
    val currentVersion: String,
    private val prefs: UserPreferencesManager
) {
    companion object {
        private const val TAG = "UpdateRepository"
        private const val RELEASE_API =
            "https://api.github.com/repos/Glassous/BetterHRBUST/releases/latest"
        const val RELEASES_PAGE = "https://github.com/Glassous/BetterHRBUST/releases"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state

    /** 检测互斥：并发触发时后到者直接返回，避免重复请求 */
    private val checkMutex = Mutex()

    /** 内部解析结果（UI 只消费 [UpdateState]） */
    private data class ReleasePayload(
        val version: String,
        val notes: String,
        val releaseUrl: String,
        val publishedDate: String
    )

    /**
     * 启动时静默检测：今天已检测过则跳过。
     * 失败或「已是最新」都不改变界面，仅在有新版本时进入 Available 态。
     */
    suspend fun checkIfNeeded() {
        val snapshot = prefs.preferencesFlow.firstOrNull() ?: return
        if (isSameDay(snapshot.lastUpdateCheckAt, System.currentTimeMillis())) return
        check(silent = true)
    }

    /**
     * 执行一次更新检测
     * @param silent 静默检测（启动时）：无更新/失败均不改变界面状态
     */
    suspend fun check(silent: Boolean = false) {
        if (!checkMutex.tryLock()) return
        try {
            _state.value = UpdateState.Checking
            val release = fetchLatestRelease()
            prefs.setLastUpdateCheckAt(System.currentTimeMillis())

            when {
                release != null && VersionComparator.isNewer(release.version, currentVersion) -> {
                    _state.value = UpdateState.Available(
                        currentVersion = currentVersion,
                        latestVersion = release.version,
                        notes = release.notes,
                        releaseUrl = release.releaseUrl,
                        publishedDate = release.publishedDate
                    )
                }
                // 静默检测无更新：保持初始态，避免用户尚未主动查询就看到结果
                silent -> if (_state.value !is UpdateState.Available) {
                    _state.value = UpdateState.Idle
                }
                else -> _state.value = UpdateState.UpToDate(currentVersion)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "更新检测失败", e)
            if (silent) {
                if (_state.value !is UpdateState.Available) _state.value = UpdateState.Idle
            } else {
                _state.value = UpdateState.Error(describeError(e))
            }
        } finally {
            checkMutex.unlock()
        }
    }

    /** 请求 GitHub 最新 Release；仓库暂无 Release（404）或 tag 无法解析出版本号时返回 null */
    private suspend fun fetchLatestRelease(): ReleasePayload? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(RELEASE_API)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "BetterHRBUST-Android/$currentVersion")
            .build()

        client.newCall(request).execute().use { response ->
            if (response.code == 404) return@withContext null
            if (response.code == 403 || response.code == 429) {
                throw IOException("请求过于频繁，请稍后再试")
            }
            if (!response.isSuccessful) {
                throw IOException("GitHub API 返回 ${response.code}")
            }

            val payload = response.body?.string().orEmpty()
            if (payload.isBlank()) return@withContext null

            val release = json.decodeFromString<GitHubRelease>(payload)
            if (release.draft) return@withContext null

            val version = VersionComparator.normalize(
                release.tagName.ifBlank { release.name }
            )
            if (version.isEmpty()) return@withContext null

            ReleasePayload(
                version = version,
                notes = release.body.trim(),
                releaseUrl = release.htmlUrl.ifBlank { RELEASES_PAGE },
                publishedDate = release.publishedAt.take(10)
            )
        }
    }

    private fun describeError(e: Exception): String {
        val raw = e.message.orEmpty()
        return when {
            raw.startsWith("请求过于频繁") || raw.startsWith("GitHub API") -> raw
            e is IOException -> "网络连接失败，请检查网络后重试"
            else -> "检查更新失败，请稍后重试"
        }
    }

    private fun isSameDay(a: Long, b: Long): Boolean {
        if (a <= 0L) return false
        val zone = ZoneId.systemDefault()
        val dayA = Instant.ofEpochMilli(a).atZone(zone).toLocalDate()
        val dayB = Instant.ofEpochMilli(b).atZone(zone).toLocalDate()
        return dayA == dayB
    }
}
