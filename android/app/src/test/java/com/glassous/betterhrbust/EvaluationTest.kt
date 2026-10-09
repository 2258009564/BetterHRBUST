package com.glassous.betterhrbust

import com.glassous.betterhrbust.core.network.AcademicHttpClient
import com.glassous.betterhrbust.core.parser.AcademicParsers
import com.glassous.betterhrbust.data.repository.EvaluationRepository
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.util.concurrent.atomic.AtomicInteger

class EvaluationTest {
    private val question = """<form method="post" action="save.jsdo?token=a%2Bb%3D">
        <input name="token" type="hidden" value="fresh-token">
        <table><tr><td>教学态度</td><td><label><input type="radio" name="rating" value="4">优秀</label>
        <label><input type="radio" name="rating" value="3">良好</label></td></tr>
        <tr><td>改进建议</td><td><textarea name="comment" maxlength="20"></textarea></td></tr></table></form>"""

    @Test fun formRetainsFreshTokenAndChosenRatingAndGbkComment() {
        val form = EvaluationRepository.parseForm(question, "eva/index/form.jsdo")
        val body = form.body(listOf(1), listOf("教学建议"))
        assertTrue(body.contains("token=fresh-token"))
        assertTrue(body.contains("rating=3"))
        assertEquals("教学建议", URLDecoder.decode(body.substringAfter("comment="), "GBK"))
        assertTrue(form.action.endsWith("token=a%2Bb%3D"))
        assertThrows(IllegalStateException::class.java) { form.body(listOf(-1), listOf("建议")) }
        assertThrows(IllegalArgumentException::class.java) { form.body(listOf(0), listOf("")) }
        assertThrows(IllegalArgumentException::class.java) { form.body(listOf(0), listOf("建议😀")) }
    }

    @Test fun publicScriptCaptchaWordsAreNotLoginForms() {
        assertFalse(AcademicParsers.isLoginPage("<script>var x='getCaptcha.do j_captcha j_acegi_security_check'</script><table>成绩</table>"))
        assertTrue(AcademicParsers.isLoginPage("<form action='j_acegi_security_check'><input name='j_captcha'></form>"))
        assertThrows(IllegalArgumentException::class.java) { EvaluationRepository.evaluationUrl("https://example.com/save", "eva/index/form.jsdo") }
    }

    @Test fun listPreservesPendingAndCompletedState() {
        val tasks = EvaluationRepository.parseTasks("""<table class="infolist_tab"><tr><th>教师</th></tr>
            <tr><td>教师甲</td><td>课程甲</td><td>未评价</td><td><a href="form.jsdo?id=42">评价</a></td></tr>
            <tr><td>教师乙</td><td>课程乙</td><td>已评价</td><td></td></tr></table>""")
        assertEquals(2, tasks.size)
        assertTrue(tasks[0].pending)
        assertTrue(tasks[0].url.startsWith("http://jwzx.hrbust.edu.cn/academic/eva/"))
        assertTrue(tasks[1].completed)
        assertTrue(tasks[1].url.isEmpty())
    }

    @Test fun loginRedirectStaysHttpAndDoesNotReplayPassword() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val posts = AtomicInteger()
        server.createContext("/academic/j_acegi_security_check") { exchange ->
            posts.incrementAndGet(); exchange.requestBody.readBytes()
            exchange.responseHeaders.add("Location", "https://127.0.0.1:${server.address.port}/academic/student/currcourse/currcourse.jsdo")
            exchange.sendResponseHeaders(302, -1); exchange.close()
        }
        server.createContext("/academic/student/currcourse/currcourse.jsdo") { exchange ->
            val response = "<table>authenticated course</table>".toByteArray()
            exchange.sendResponseHeaders(200, response.size.toLong()); exchange.responseBody.use { it.write(response) }
        }
        server.start()
        try {
            val client = AcademicHttpClient(baseUrl = "http://127.0.0.1:${server.address.port}/academic/")
            assertTrue(client.login("test-user", "test-password", "0000").contains("authenticated course"))
            assertEquals(1, posts.get())
        } finally { server.stop(0) }
    }
}
