package com.glassous.betterhrbust

import com.glassous.betterhrbust.core.network.AcademicHttpClient
import com.glassous.betterhrbust.core.network.SessionExpiredException
import com.glassous.betterhrbust.core.parser.AcademicParsers
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger

/**
 * 会话失效识别回归测试。
 *
 * 业务请求（非登录 / 登出流程）落到登录页、或返回 401 时，必须抛出
 * [SessionExpiredException] 并触发 onSessionExpired 回调 —— 数据层据此保留
 * 本地缓存并提示"重新登录（可忽略）"，而不是把登录页解析成空数据后覆盖旧数据。
 *
 * 登录流程内的登录页跳转（密码错误回到登录页）则不能被误判为会话失效。
 */
class SessionLossNetworkTest {

    private val loginPageHtml = """
        <html><body>
        <form action="j_acegi_security_check" method="post">
          <input name="j_username">
          <input name="j_password">
          <input name="j_captcha">
        </form>
        </body></html>
    """.trimIndent()

    private fun serveLoginPage(server: HttpServer, hits: AtomicInteger) {
        server.createContext("/academic/common/security/login.jsp") { exchange ->
            hits.incrementAndGet()
            val bytes = loginPageHtml.toByteArray()
            exchange.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
    }

    @Test
    fun businessRequestRedirectedToLoginPageReportsSessionLoss() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val port = server.address.port
        val loginPageHits = AtomicInteger()
        server.createContext("/academic/showPersonalInfo.do") { exchange ->
            exchange.responseHeaders.add("Location", "/academic/common/security/login.jsp")
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }
        serveLoginPage(server, loginPageHits)
        server.start()
        try {
            val client = AcademicHttpClient(baseUrl = "http://127.0.0.1:$port/academic/")
            val expiredCallbacks = AtomicInteger()
            client.onSessionExpired = { expiredCallbacks.incrementAndGet() }

            try {
                client.get("showPersonalInfo.do")
                fail("业务请求被跳转到登录页必须视为会话失效")
            } catch (error: SessionExpiredException) {
                assertTrue(error.message!!.contains("重新登录"))
            }
            assertEquals("会话失效必须通知上层（用于提示重新登录）", 1, expiredCallbacks.get())
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun businessRequestReturningLoginPageWithoutRedirectReportsSessionLoss() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val port = server.address.port
        server.createContext("/academic/listLeft.do") { exchange ->
            val bytes = loginPageHtml.toByteArray()
            exchange.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val client = AcademicHttpClient(baseUrl = "http://127.0.0.1:$port/academic/")
            val expiredCallbacks = AtomicInteger()
            client.onSessionExpired = { expiredCallbacks.incrementAndGet() }

            try {
                client.get("listLeft.do")
                fail("HTTP 200 但内容为登录页同样视为会话失效")
            } catch (error: SessionExpiredException) {
                assertTrue(error.message!!.contains("重新登录"))
            }
            assertEquals(1, expiredCallbacks.get())
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun businessRequestUnauthorizedReportsSessionLoss() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val port = server.address.port
        server.createContext("/academic/manager/score/studentOwnScore.do") { exchange ->
            exchange.requestBody.readBytes()
            exchange.sendResponseHeaders(401, -1)
            exchange.close()
        }
        server.start()
        try {
            val client = AcademicHttpClient(baseUrl = "http://127.0.0.1:$port/academic/")
            val expiredCallbacks = AtomicInteger()
            client.onSessionExpired = { expiredCallbacks.incrementAndGet() }

            try {
                client.post("manager/score/studentOwnScore.do", mapOf("para" to "0"))
                fail("业务请求返回 401 必须视为会话失效")
            } catch (error: SessionExpiredException) {
                assertTrue(error.message!!.contains("重新登录"))
            }
            assertEquals(1, expiredCallbacks.get())
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun loginFlowLandingOnLoginPageStillReturnsHtml() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val port = server.address.port
        val loginPageHits = AtomicInteger()
        server.createContext("/academic/j_acegi_security_check") { exchange ->
            exchange.requestBody.readBytes()
            exchange.responseHeaders.add("Location", "/academic/common/security/login.jsp")
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }
        serveLoginPage(server, loginPageHits)
        server.start()
        try {
            val client = AcademicHttpClient(baseUrl = "http://127.0.0.1:$port/academic/")
            val expiredCallbacks = AtomicInteger()
            client.onSessionExpired = { expiredCallbacks.incrementAndGet() }

            val html = client.login("2021001", "wrong-password", "1234")
            assertTrue("登录失败跳回登录页属于流程内预期", AcademicParsers.isLoginPage(html))
            assertEquals("登录流程内的登录页不能误判为会话失效", 0, expiredCallbacks.get())
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun logoutRequestIsNotTreatedAsSessionLoss() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val port = server.address.port
        val loginPageHits = AtomicInteger()
        server.createContext("/academic/j_acegi_logout") { exchange ->
            exchange.responseHeaders.add("Location", "/academic/common/security/login.jsp")
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }
        serveLoginPage(server, loginPageHits)
        server.start()
        try {
            val client = AcademicHttpClient(baseUrl = "http://127.0.0.1:$port/academic/")
            val expiredCallbacks = AtomicInteger()
            client.onSessionExpired = { expiredCallbacks.incrementAndGet() }

            client.logout()
            assertEquals("主动登出的登录页跳转不应触发失效提示", 0, expiredCallbacks.get())
        } finally {
            server.stop(0)
        }
    }
}
