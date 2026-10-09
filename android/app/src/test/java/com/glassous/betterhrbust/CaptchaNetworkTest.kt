package com.glassous.betterhrbust

import com.glassous.betterhrbust.core.network.AcademicHttpClient
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger

class CaptchaNetworkTest {
    @Test fun logoutUsesOriginalPortalEndpointWithoutReportingNormalRedirectAsExpiration() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val calls = AtomicInteger()
        val expired = AtomicInteger()
        server.createContext("/academic/logout_security_check") { exchange ->
            calls.incrementAndGet()
            exchange.responseHeaders.add("Location", "/academic/index.jsp")
            exchange.sendResponseHeaders(302, -1); exchange.close()
        }
        server.createContext("/academic/index.jsp") { exchange ->
            val body = "<form action='j_acegi_security_check'><input name='j_captcha'></form>".toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong()); exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            val client = AcademicHttpClient(baseUrl = "http://127.0.0.1:${server.address.port}/academic/")
            client.cookieJar.setJSessionId("127.0.0.1", "old-session")
            client.onSessionExpired = { expired.incrementAndGet() }
            client.logout()
            assertEquals(1, calls.get())
            assertEquals(0, expired.get())
            assertFalse(client.cookieJar.hasSession())
        } finally { server.stop(0) }
    }

    @Test fun captchaRedirectStaysHttpAndSharesSessionCookie() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val port = server.address.port
        val bytes = byteArrayOf(1, 2, 3)
        server.createContext("/academic/getCaptcha.do") { exchange ->
            exchange.responseHeaders.add("Set-Cookie", "JSESSIONID=captcha-session; Path=/academic")
            exchange.responseHeaders.add("Location", "https://127.0.0.1:$port/academic/captcha-image")
            exchange.sendResponseHeaders(302, -1); exchange.close()
        }
        server.createContext("/academic/captcha-image") { exchange ->
            val valid = exchange.requestHeaders.getFirst("Cookie")?.contains("JSESSIONID=captcha-session") == true
            exchange.responseHeaders.add("Content-Type", "image/jpeg")
            exchange.sendResponseHeaders(if (valid) 200 else 403, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            assertArrayEquals(bytes, AcademicHttpClient(baseUrl = "http://127.0.0.1:$port/academic/").downloadCaptcha())
        } finally { server.stop(0) }
    }

    @Test fun gatewayErrorIsActionableAndDoesNotRetry() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val calls = AtomicInteger()
        server.createContext("/academic/getCaptcha.do") { exchange ->
            calls.incrementAndGet(); exchange.sendResponseHeaders(502, -1); exchange.close()
        }
        server.start()
        try {
            try {
                AcademicHttpClient(baseUrl = "http://127.0.0.1:${server.address.port}/academic/").downloadCaptcha()
                fail("应报告 HTTP 502")
            } catch (error: IOException) {
                assertTrue(error.message!!.contains("HTTP 502"))
                assertTrue(error.message!!.contains("切换网络"))
            }
            assertEquals(1, calls.get())
        } finally { server.stop(0) }
    }

    @Test fun captchaRejectsHtmlAndPrecheckFailureDoesNotRejectLogin() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/academic/getCaptcha.do") { exchange ->
            exchange.responseHeaders.add("Content-Type", "text/html")
            val bytes = "<html>gateway</html>".toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong()); exchange.responseBody.use { it.write(bytes) }
        }
        server.createContext("/academic/checkCaptcha.do") { exchange ->
            exchange.sendResponseHeaders(502, -1); exchange.close()
        }
        server.start()
        try {
            val client = AcademicHttpClient(baseUrl = "http://127.0.0.1:${server.address.port}/academic/")
            try { client.downloadCaptcha(); fail("不能把 HTML 当作验证码") }
            catch (error: IOException) { assertTrue(error.message!!.contains("验证码图片")) }
            assertTrue(client.checkCaptcha("1234"))
        } finally { server.stop(0) }
    }
}
