package com.glassous.betterhrbust.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * GitHub Release 模型（仅声明更新检测所需字段，其余字段由 ignoreUnknownKeys 忽略）
 */
@Serializable
data class GitHubRelease(
    @SerialName("tag_name") val tagName: String = "",
    val name: String = "",
    val body: String = "",
    @SerialName("html_url") val htmlUrl: String = "",
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    @SerialName("published_at") val publishedAt: String = "",
    val assets: List<GitHubAsset> = emptyList()
)

/** Release 附件（更新检测当前只跳转 Release 页，保留资源信息供后续按平台跳直链） */
@Serializable
data class GitHubAsset(
    val name: String = "",
    @SerialName("browser_download_url") val browserDownloadUrl: String = ""
)

/**
 * 更新检测状态（「更多」页更新模块的渲染依据）
 *
 * 与桌面端 web/src/composables/useUpdate.js 的状态口径保持一致：
 * Idle / Checking / UpToDate / Available / Error。
 */
sealed interface UpdateState {
    /** 尚未检测（启动静默检测之前的初始态，或静默检测无结果） */
    data object Idle : UpdateState

    data object Checking : UpdateState

    data class UpToDate(val currentVersion: String) : UpdateState

    data class Available(
        val currentVersion: String,
        val latestVersion: String,
        val notes: String,
        val releaseUrl: String,
        /** 发布日期（yyyy-MM-dd，取自 Release 的 published_at） */
        val publishedDate: String = ""
    ) : UpdateState

    data class Error(val message: String) : UpdateState
}
