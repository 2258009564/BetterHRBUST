package com.glassous.betterhrbust

import com.glassous.betterhrbust.core.network.CharsetDecoderHelper
import com.glassous.betterhrbust.core.network.SessionCookieJar
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test
import java.nio.charset.Charset

class NetworkAndSessionTest {

    @Test
    fun testSessionCookieJarStorageAndRetrieval() {
        val cookieJar = SessionCookieJar()
        val url = "http://jwzx.hrbust.edu.cn/academic/login.jsp".toHttpUrl()

        assertFalse(cookieJar.hasSession())

        val cookie = Cookie.Builder()
            .name("JSESSIONID")
            .value("ABC123456789")
            .domain("jwzx.hrbust.edu.cn")
            .path("/academic")
            .build()

        cookieJar.saveFromResponse(url, listOf(cookie))

        assertTrue(cookieJar.hasSession())
        assertEquals("ABC123456789", cookieJar.getJSessionId())

        val loadedCookies = cookieJar.loadForRequest(url)
        assertEquals(1, loadedCookies.size)
        assertEquals("JSESSIONID", loadedCookies[0].name)
        assertEquals("ABC123456789", loadedCookies[0].value)

        cookieJar.clear()
        assertFalse(cookieJar.hasSession())
        assertTrue(cookieJar.loadForRequest(url).isEmpty())
    }

    @Test
    fun testCharsetDecoderHelperGbkAndUtf8() {
        val originalText = "哈尔滨理工大学 综合教务在线 2026"

        // GBK encoded bytes
        val gbkBytes = originalText.toByteArray(Charset.forName("GBK"))
        val decodedFromGbk = CharsetDecoderHelper.decode(gbkBytes, "text/html; charset=gbk")
        assertEquals(originalText, decodedFromGbk)

        // UTF-8 encoded bytes
        val utf8Bytes = originalText.toByteArray(Charsets.UTF_8)
        val decodedFromUtf8 = CharsetDecoderHelper.decode(utf8Bytes, "text/html; charset=utf-8")
        assertEquals(originalText, decodedFromUtf8)

        // URL encoding with GBK
        val encodedParam = CharsetDecoderHelper.encodeGbk("高等数学")
        assertTrue(encodedParam.contains("%"))
    }
}
