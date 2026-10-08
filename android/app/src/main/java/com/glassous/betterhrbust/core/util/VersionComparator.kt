package com.glassous.betterhrbust.core.util

/**
 * 语义化版本比较（与桌面端 `web/src/services/update.js` 口径完全一致）
 *
 * - tag 形如 `v1.0.0` / `desktop-v1.0.0` / `android-v1.0.0` / `1.0` 均可，
 *   统一剥离非数字前缀后按 `.` 分段做数值比较；
 * - 段数不同按缺位补 0（`1.0` 与 `1.0.0` 视为相同）；
 * - 纯函数，便于单元测试。
 */
object VersionComparator {

    private val VERSION_PATTERN = Regex("""\d+(?:\.\d+)*""")

    /** 剥离 v / desktop-v / android-v 等前缀，仅保留数字版本段；无法识别时返回空串 */
    fun normalize(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return VERSION_PATTERN.find(raw)?.value.orEmpty()
    }

    /** a > b 返回 1，a < b 返回 -1，相等返回 0 */
    fun compare(a: String?, b: String?): Int {
        val left = normalize(a).split('.').map { it.toIntOrNull() ?: 0 }
        val right = normalize(b).split('.').map { it.toIntOrNull() ?: 0 }
        val length = maxOf(left.size, right.size)
        for (i in 0 until length) {
            val l = left.getOrElse(i) { 0 }
            val r = right.getOrElse(i) { 0 }
            if (l != r) return if (l > r) 1 else -1
        }
        return 0
    }

    /**
     * candidate 是否严格新于 current（版本相同不算有更新）。
     *
     * 任一侧无法解析出版本号时返回 false：版本未知宁可不提示，
     * 也不误报「有新版本」诱导用户重复下载。
     */
    fun isNewer(candidate: String?, current: String?): Boolean {
        if (normalize(candidate).isEmpty() || normalize(current).isEmpty()) return false
        return compare(candidate, current) > 0
    }
}
