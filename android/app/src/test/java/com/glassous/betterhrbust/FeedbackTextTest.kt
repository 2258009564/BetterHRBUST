package com.glassous.betterhrbust

import com.glassous.betterhrbust.core.util.FeedbackText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.ZoneId

/**
 * 反馈表单文本度量测试：与 Web 端 `services/feedback.js` 的 textLength / maxlength 同口径，
 * 重点覆盖 emoji（代理对）计数与截断边界，避免出现"半个字符"提交到后端。
 */
class FeedbackTextTest {

    private val shanghai = ZoneId.of("Asia/Shanghai")

    /** 是否存在未配对的代理项（半个 emoji）。 */
    private fun hasUnpairedSurrogate(value: String): Boolean {
        var index = 0
        while (index < value.length) {
            val current = value[index]
            when {
                current.isHighSurrogate() -> {
                    if (index + 1 >= value.length || !value[index + 1].isLowSurrogate()) return true
                    index += 2
                }
                current.isLowSurrogate() -> return true
                else -> index++
            }
        }
        return false
    }

    // ---- 码点计数 ----

    @Test
    fun codePointLengthCountsAsciiAndCjkByCharacter() {
        assertEquals(0, FeedbackText.codePointLength(""))
        assertEquals(3, FeedbackText.codePointLength("abc"))
        assertEquals(7, FeedbackText.codePointLength("哈尔滨理工大学"))
    }

    @Test
    fun codePointLengthCountsEmojiAsSingleCharacter() {
        // 😀 = U+1F600（UTF-16 中占 2 个 char），按码点应记 1 个字符
        assertEquals(3, FeedbackText.codePointLength("你😀好"))
        assertEquals(2, FeedbackText.codePointLength("😀😀"))
    }

    // ---- 码点截断 ----

    @Test
    fun truncateKeepsShortValueUnchanged() {
        assertEquals("求助", FeedbackText.truncate("求助", 64))
        assertEquals("", FeedbackText.truncate("", 5))
    }

    @Test
    fun truncateCutsByCodePointBoundary() {
        val value = "啊😀嗯😀哦"
        val truncated = FeedbackText.truncate(value, 3)
        assertEquals("啊😀嗯", truncated)
        assertEquals(3, FeedbackText.codePointLength(truncated))
        // 不能切出孤立代理项（否则渲染为乱码，也会污染提交内容）
        assertFalse(hasUnpairedSurrogate(truncated))
    }

    @Test
    fun truncateExactlyAtLimitKeepsValue() {
        assertEquals("😀😀", FeedbackText.truncate("😀😀", 2))
    }

    @Test
    fun truncateWithNonPositiveLimitReturnsEmpty() {
        assertEquals("", FeedbackText.truncate("内容", 0))
        assertEquals("", FeedbackText.truncate("内容", -1))
    }

    // ---- 提交时间格式化 ----

    @Test
    fun formatSubmittedAtConvertsUtcToLocalZone() {
        assertEquals(
            "2026/10/13 16:30:00",
            FeedbackText.formatSubmittedAt("2026-10-13T08:30:00Z", shanghai)
        )
    }

    @Test
    fun formatSubmittedAtKeepsExplicitOffset() {
        assertEquals(
            "2026/10/13 08:30:00",
            FeedbackText.formatSubmittedAt("2026-10-13T08:30:00+08:00", shanghai)
        )
    }

    @Test
    fun formatSubmittedAtFallsBackForBlankOrInvalidInput() {
        assertEquals("刚刚", FeedbackText.formatSubmittedAt("", shanghai))
        assertEquals("刚刚", FeedbackText.formatSubmittedAt("   ", shanghai))
        assertEquals("刚刚", FeedbackText.formatSubmittedAt("not-a-time", shanghai))
    }
}
