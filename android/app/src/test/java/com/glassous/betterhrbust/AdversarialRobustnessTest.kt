package com.glassous.betterhrbust

import com.glassous.betterhrbust.core.database.*
import com.glassous.betterhrbust.core.model.*
import com.glassous.betterhrbust.core.network.CharsetDecoderHelper
import com.glassous.betterhrbust.core.network.SessionCookieJar
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.charset.Charset
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.xml.parsers.DocumentBuilderFactory

class AdversarialRobustnessTest {

    private val json = Json { ignoreUnknownKeys = true }

    // ==========================================
    // 1. SessionCookieJar Adversarial Tests
    // ==========================================

    @Test
    fun testCookieJar_ConcurrentReadWriteClear() {
        val cookieJar = SessionCookieJar()
        val numThreads = 16
        val operationsPerThread = 500
        val executor = Executors.newFixedThreadPool(numThreads)
        val latch = CountDownLatch(numThreads)
        val errors = AtomicInteger(0)

        for (t in 0 until numThreads) {
            executor.submit {
                try {
                    for (i in 0 until operationsPerThread) {
                        val host = "jwzx.hrbust.edu.cn"
                        val url = "http://$host/academic/item_$i.do".toHttpUrl()

                        when (i % 4) {
                            0 -> {
                                val cookie = Cookie.Builder()
                                    .domain(host)
                                    .path("/academic")
                                    .name("COOKIE_$t")
                                    .value("VAL_${i}_$t")
                                    .build()
                                cookieJar.saveFromResponse(url, listOf(cookie))
                            }
                            1 -> {
                                cookieJar.setJSessionId(host, "SESS_${i}_$t")
                            }
                            2 -> {
                                cookieJar.loadForRequest(url)
                                cookieJar.getJSessionId()
                                cookieJar.hasSession()
                            }
                            3 -> {
                                if (i == operationsPerThread - 1) {
                                    cookieJar.clear()
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    errors.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }

        assertTrue("Concurrent execution timed out", latch.await(10, TimeUnit.SECONDS))
        executor.shutdown()
        assertEquals("Concurrent operations caused exceptions", 0, errors.get())
    }

    @Test
    fun testCookieJar_ExpirationHandling() {
        val cookieJar = SessionCookieJar()
        val host = "jwzx.hrbust.edu.cn"
        val url = "http://$host/academic/test".toHttpUrl()
        val now = System.currentTimeMillis()

        // Expired cookie (10 seconds ago)
        val expiredCookie = Cookie.Builder()
            .domain(host)
            .path("/academic")
            .name("EXPIRED_TAG")
            .value("old_val")
            .expiresAt(now - 10_000)
            .build()

        // Valid cookie (10 minutes in future)
        val activeCookie = Cookie.Builder()
            .domain(host)
            .path("/academic")
            .name("ACTIVE_TAG")
            .value("new_val")
            .expiresAt(now + 600_000)
            .build()

        cookieJar.saveFromResponse(url, listOf(expiredCookie, activeCookie))

        // On load, expired cookie should be dropped, active cookie retained
        val loaded = cookieJar.loadForRequest(url)
        assertEquals(1, loaded.size)
        assertEquals("ACTIVE_TAG", loaded[0].name)
        assertEquals("new_val", loaded[0].value)

        // Ensure clear removes all active cookies
        cookieJar.clear()
        assertTrue(cookieJar.loadForRequest(url).isEmpty())
        assertFalse(cookieJar.hasSession())
    }

    @Test
    fun testCookieJar_PathMatching() {
        val cookieJar = SessionCookieJar()
        val host = "jwzx.hrbust.edu.cn"
        val baseAcademicUrl = "http://$host/academic/login.jsp".toHttpUrl()
        val rootUrl = "http://$host/".toHttpUrl()

        cookieJar.setJSessionId(host, "TEST_JSESSIONID_999")

        // Request inside /academic should match
        val matched = cookieJar.loadForRequest(baseAcademicUrl)
        assertEquals(1, matched.size)
        assertEquals("JSESSIONID", matched[0].name)

        // Request to root / outside /academic should NOT match
        val unmatched = cookieJar.loadForRequest(rootUrl)
        assertTrue("Cookie with /academic path should not match root /", unmatched.isEmpty())
    }

    // ==========================================
    // 2. CharsetDecoderHelper Adversarial Tests
    // ==========================================

    @Test
    fun testCharsetDecoderHelper_EmptyAndMalformedStreams() {
        // 1. Empty byte array
        val emptyResult = CharsetDecoderHelper.decode(ByteArray(0), "text/html; charset=gbk")
        assertEquals("", emptyResult)

        // 2. Truncated GBK lead byte (0x81 alone)
        val truncatedGbk = byteArrayOf(0x81.toByte())
        val decodedTruncated = CharsetDecoderHelper.decode(truncatedGbk, "text/html; charset=gbk")
        assertNotNull(decodedTruncated)

        // 3. Random noise / high range bytes
        val noise = byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x80.toByte(), 0xAA.toByte())
        val decodedNoise = CharsetDecoderHelper.decode(noise, null)
        assertNotNull(decodedNoise)

        // 4. Large stream with mixed UTF-8 and GBK
        val sampleText = "理工教务综合测试 - " + "A".repeat(5000)
        val sampleBytes = sampleText.toByteArray(Charset.forName("GBK"))
        val decodedLarge = CharsetDecoderHelper.decode(sampleBytes, "text/html")
        assertTrue(decodedLarge.startsWith("理工教务综合测试"))
        assertTrue(decodedLarge.endsWith("AAAAA"))
    }

    @Test
    fun testCharsetDecoderHelper_HtmlMetaDetectionAndFallback() {
        // HTML body with meta tag specifying gb2312, no HTTP header charset
        val htmlContent = "<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=gb2312\" /></head><body>哈尔滨理工大学</body></html>"
        val gbkBytes = htmlContent.toByteArray(Charset.forName("GBK"))

        val decodedWithMeta = CharsetDecoderHelper.decode(gbkBytes, "text/html")
        assertTrue("Meta tag charset should be honored", decodedWithMeta.contains("哈尔滨理工大学"))

        // ISO-8859-1 HTTP header override by meta tag
        val decodedIsoOverride = CharsetDecoderHelper.decode(gbkBytes, "text/html; charset=ISO-8859-1")
        assertTrue("ISO-8859-1 should fallback to meta detection", decodedIsoOverride.contains("哈尔滨理工大学"))

        // Preferred charset specified
        val utf8Text = "智能排课与考试日程"
        val utf8Bytes = utf8Text.toByteArray(Charsets.UTF_8)
        val decodedUtf8 = CharsetDecoderHelper.decode(utf8Bytes, null, preferredCharset = Charsets.UTF_8)
        assertEquals(utf8Text, decodedUtf8)
    }

    // ==========================================
    // 3. Room Entities and Serialization Fidelity
    // ==========================================

    @Test
    fun testRoomEntitiesAndJsonFidelity() {
        // Timetable round-trip
        val timetable = TimetableResult(
            cells = listOf(
                TimetableCell(
                    id = "1-1", courseId = "001", day = 1, sectionIndex = 1,
                    sectionLabel = "1-2节", courseName = "软件工程实训", courseSeq = "01",
                    location = "新主楼E202", teacher = "张教授", weeks = "1-16周",
                    hoursType = "讲授", rawLines = listOf("软件工程实训", "新主楼E202")
                )
            ),
            unarranged = emptyList()
        )
        val encodedTimetable = json.encodeToString(timetable)
        val entityTimetable = TimetableEntity(studentId = "20240001", json = encodedTimetable)
        assertEquals("20240001", entityTimetable.studentId)
        val decodedTimetable = json.decodeFromString<TimetableResult>(entityTimetable.json)
        assertEquals(timetable.cells.size, decodedTimetable.cells.size)
        assertEquals("软件工程实训", decodedTimetable.cells[0].courseName)

        // Score round-trip
        val scoreResult = ScoreResult(
            scores = listOf(
                ScoreItem(
                    year = "2024", term = "1", courseId = "C001", courseName = "高等数学",
                    courseSeq = "02", courseGroup = "必修", score = "95", credit = 5.0,
                    hours = 80, property = "必修", remark = "", examType = "正常考试",
                    passMark = "及格", passed = true
                )
            )
        )
        val encodedScores = json.encodeToString(scoreResult)
        val entityScore = ScoreEntity(studentId = "20240001", json = encodedScores)
        val decodedScores = json.decodeFromString<ScoreResult>(entityScore.json)
        assertEquals(1, decodedScores.scores.size)
        assertEquals("95", decodedScores.scores[0].score)

        // Exam round-trip
        val exams = listOf(
            ExamItem(
                courseId = "E01", courseName = "大学物理", time = "2026-11-10 09:00--11:00",
                location = "一教401", property = "考试", countdownDays = 32, isUpcoming = true
            )
        )
        val encodedExams = json.encodeToString(exams)
        val entityExam = ExamEntity(studentId = "20240001", json = encodedExams)
        val decodedExams = json.decodeFromString<List<ExamItem>>(entityExam.json)
        assertEquals(1, decodedExams.size)
        assertEquals("大学物理", decodedExams[0].courseName)

        // Profile entity
        val profile = PersonalInfo(
            studentNumber = "20240001", realName = "张三", college = "计算机科学与技术学院",
            major = "软件工程", grade = "2024", status = "在籍"
        )
        val encodedProfile = json.encodeToString(profile)
        val entityProfile = ProfileEntity(studentNumber = "20240001", json = encodedProfile)
        val decodedProfile = json.decodeFromString<PersonalInfo>(entityProfile.json)
        assertEquals("张三", decodedProfile.realName)
        assertEquals("软件工程", decodedProfile.major)

        // Notice entity
        val noticeEntity = NoticeEntity(
            id = "notice_001",
            title = "关于2026学年秋季学期选课通知",
            content = "选课将于下周一正式开启...",
            date = "2026-10-01",
            isRead = false
        )
        assertEquals("notice_001", noticeEntity.id)
        assertFalse(noticeEntity.isRead)
    }

    // ==========================================
    // 4. AndroidManifest.xml Cleartext Traffic Verification
    // ==========================================

    @Test
    fun testAndroidManifestCleartextTrafficConfig() {
        val manifestFile = File("src/main/AndroidManifest.xml")
        assertTrue("AndroidManifest.xml must exist at ${manifestFile.absolutePath}", manifestFile.exists())

        val dbFactory = DocumentBuilderFactory.newInstance()
        val dBuilder = dbFactory.newDocumentBuilder()
        val doc = dBuilder.parse(manifestFile)
        doc.documentElement.normalize()

        val appNodes = doc.getElementsByTagName("application")
        assertTrue("Application node must exist in manifest", appNodes.length > 0)
        val appElement = appNodes.item(0) as org.w3c.dom.Element

        val usesCleartextTraffic = appElement.getAttribute("android:usesCleartextTraffic")
        assertEquals(
            "android:usesCleartextTraffic must be strictly 'true' for plain HTTP educational administration access",
            "true",
            usesCleartextTraffic
        )

        // Verify Internet permission
        val permissionNodes = doc.getElementsByTagName("uses-permission")
        val permissions = (0 until permissionNodes.length).map {
            (permissionNodes.item(it) as org.w3c.dom.Element).getAttribute("android:name")
        }
        assertTrue("INTERNET permission must be declared", permissions.contains("android.permission.INTERNET"))
        assertTrue("ACCESS_NETWORK_STATE permission must be declared", permissions.contains("android.permission.ACCESS_NETWORK_STATE"))
    }
}
