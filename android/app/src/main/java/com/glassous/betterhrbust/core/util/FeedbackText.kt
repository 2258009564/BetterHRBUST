package com.glassous.betterhrbust.core.util

import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 反馈表单的文本度量工具，与 Web 端 `services/feedback.js` 同口径：
 *
 * - 计数与长度限制按 **Unicode 码点** 计算（emoji 记 1 个字符，而非 UTF-16 的 2 个）；
 * - 截断按码点边界进行，绝不切出半个代理对；
 * - 提交时间为服务端 RFC3339 字符串，展示前统一换算到本地时区。
 */
object FeedbackText {

    private val TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy/M/d HH:mm:ss")

    /** 按 Unicode 码点计数（空串为 0）。 */
    fun codePointLength(value: String): Int = value.codePointCount(0, value.length)

    /**
     * 按码点截断到 [maxCodePoints]。
     * 未超限时原样返回；[maxCodePoints] ≤ 0 时返回空串。
     */
    fun truncate(value: String, maxCodePoints: Int): String {
        if (maxCodePoints <= 0) return ""
        if (value.codePointCount(0, value.length) <= maxCodePoints) return value
        return value.substring(0, value.offsetByCodePoints(0, maxCodePoints))
    }

    /**
     * 提交时间格式化：RFC3339 → `yyyy/M/d HH:mm:ss`（本地时区）。
     * 空值或解析失败（含服务端未返回时区信息）时回退为「刚刚」。
     */
    fun formatSubmittedAt(raw: String, zone: ZoneId = ZoneId.systemDefault()): String {
        if (raw.isBlank()) return "刚刚"
        return runCatching {
            OffsetDateTime.parse(raw)
                .atZoneSameInstant(zone)
                .format(TIME_FORMATTER)
        }.getOrDefault("刚刚")
    }
}
