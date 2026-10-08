package com.glassous.betterhrbust

import com.glassous.betterhrbust.core.model.ScoreItem
import com.glassous.betterhrbust.core.parser.AcademicParsers
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class AcademicParsersTest {

    private fun loadFixture(filename: String): String {
        val stream = javaClass.classLoader?.getResourceAsStream("fixtures/$filename")
            ?: File("src/test/resources/fixtures/$filename").inputStream()
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    @Test
    fun testStripHtmlComments() {
        val rawHtml = "<table><tr><th>课程号</th><!--<th>考试方式</th>--><th>课程名</th></tr><tr><td>U001</td><!--<td></td>--><td>离散数学</td></tr></table>"
        val clean = AcademicParsers.stripHtmlComments(rawHtml)
        assertFalse(clean.contains("考试方式"))
        assertTrue(clean.contains("离散数学"))
        assertTrue(clean.contains("U001"))
    }

    @Test
    fun testDecodeEntities() {
        val encoded = "&amp;lt;&amp;lt;高等数学&amp;gt;&amp;gt;&nbsp;&copy;&nbsp;&#65;&#x42;"
        val decoded = AcademicParsers.decodeEntities(encoded)
        assertTrue(decoded.contains("<<高等数学>>"))
        assertTrue(decoded.contains("©"))
        assertTrue(decoded.contains("AB"))
    }

    @Test
    fun testIsLoginPage() {
        val loginHtml = "<html><form action='j_acegi_security_check' method='post'><input name='j_captcha'></form></html>"
        assertTrue(AcademicParsers.isLoginPage(loginHtml))

        val normalHtml = "<html><head><title>个人信息</title></head><body><h1>学生档案</h1></body></html>"
        assertFalse(AcademicParsers.isLoginPage(normalHtml))
    }

    @Test
    fun testParseLoginFailureReason() {
        assertEquals("验证码错误或已过期，请刷新重试", AcademicParsers.parseLoginFailureReason("验证码错误，请重新输入"))
        assertEquals("学号或密码错误", AcademicParsers.parseLoginFailureReason("Reason: badCredentials"))
        assertEquals("学号或密码错误", AcademicParsers.parseLoginFailureReason("密码输入错误"))
        assertEquals("该学号不存在", AcademicParsers.parseLoginFailureReason("用户不存在或状态异常"))
        assertEquals("账号已被系统锁定，请稍后再试", AcademicParsers.parseLoginFailureReason("账号已被系统锁定"))
        assertEquals("登录失败，请检查学号与密码", AcademicParsers.parseLoginFailureReason("未知系统异常"))
    }

    @Test
    fun testParseStudentContext() {
        val html = loadFixture("0022-student.context.html")
        val context = AcademicParsers.parseStudentContext(html)

        // Year must be 46 in sample
        assertEquals("46", context.year)
        assertEquals("2", context.term)
        assertTrue("Course list should not be empty", context.courses.isNotEmpty())

        val firstCourse = context.courses[0]
        assertTrue(firstCourse.courseId.isNotEmpty())
        assertTrue(firstCourse.courseName.isNotEmpty())
        assertTrue(firstCourse.credit > 0.0)
    }

    @Test
    fun testParsePersonalInfo() {
        val html = loadFixture("0015-student.myInfo.html")
        val info = AcademicParsers.parsePersonalInfo(html)

        assertTrue("Student number should be extracted", info.studentNumber.isNotEmpty())
        assertTrue("College should be extracted", info.college.isNotEmpty())
        assertTrue("Major should be extracted", info.major.isNotEmpty())
        assertEquals("在籍（注册）", info.status)
    }

    @Test
    fun testParseTimetable() {
        val html = loadFixture("0032-timetable.student.html")
        val timetable = AcademicParsers.parseTimetable(html)

        assertTrue("Cells should not be empty", timetable.cells.isNotEmpty())
        for (cell in timetable.cells) {
            assertTrue("Day must be between 1 and 7", cell.day in 1..7)
            assertTrue("Section index must be between 1 and 6", cell.sectionIndex in 1..6)
            assertTrue("Course name must not be blank", cell.courseName.isNotBlank())
        }
    }

    @Test
    fun testIsCourseActiveInWeek() {
        val oddExpr = "1-16周(单)"
        assertTrue(AcademicParsers.isCourseActiveInWeek(oddExpr, 1))
        assertFalse(AcademicParsers.isCourseActiveInWeek(oddExpr, 2))
        assertTrue(AcademicParsers.isCourseActiveInWeek(oddExpr, 3))
        assertFalse(AcademicParsers.isCourseActiveInWeek(oddExpr, 4))

        val evenExpr = "1-16周(双)"
        assertFalse(AcademicParsers.isCourseActiveInWeek(evenExpr, 1))
        assertTrue(AcademicParsers.isCourseActiveInWeek(evenExpr, 2))
        assertFalse(AcademicParsers.isCourseActiveInWeek(evenExpr, 3))
        assertTrue(AcademicParsers.isCourseActiveInWeek(evenExpr, 4))

        val rangeExpr = "4-10周"
        assertFalse(AcademicParsers.isCourseActiveInWeek(rangeExpr, 3))
        assertTrue(AcademicParsers.isCourseActiveInWeek(rangeExpr, 4))
        assertTrue(AcademicParsers.isCourseActiveInWeek(rangeExpr, 7))
        assertTrue(AcademicParsers.isCourseActiveInWeek(rangeExpr, 10))
        assertFalse(AcademicParsers.isCourseActiveInWeek(rangeExpr, 11))
    }

    @Test
    fun testHashCourseColor() {
        val hash1 = AcademicParsers.hashCourseColor("数据结构与算法")
        val hash2 = AcademicParsers.hashCourseColor("数据结构与算法")
        val hash3 = AcademicParsers.hashCourseColor("大学英语")

        assertEquals("Hash must be deterministic", hash1, hash2)
        assertTrue("Hash must map to 0..11 palette", hash1 in 0..11)
        assertTrue("Hash must map to 0..11 palette", hash3 in 0..11)
    }

    @Test
    fun testParseScores() {
        val html = loadFixture("0028-score.all.html")
        val result = AcademicParsers.parseScores(html)

        assertTrue("Scores list should not be empty", result.scores.isNotEmpty())
        for (item in result.scores) {
            assertTrue("Course name must not be empty", item.courseName.isNotEmpty())
            assertTrue("Credit must be >= 0", item.credit >= 0.0)
            assertTrue("Score value must not be empty", item.score.isNotEmpty())
        }
    }

    @Test
    fun testCalculateGpaStats() {
        val sampleScores = listOf(
            ScoreItem(
                year = "2024", term = "秋", courseId = "C01", courseName = "软件工程", courseSeq = "1",
                courseGroup = "必修", score = "90", credit = 3.0, hours = 48, property = "必修",
                remark = "", examType = "正常考试", passMark = "及格", passed = true
            ),
            ScoreItem(
                year = "2024", term = "秋", courseId = "C02", courseName = "操作系统", courseSeq = "2",
                courseGroup = "必修", score = "80", credit = 4.0, hours = 64, property = "必修",
                remark = "", examType = "正常考试", passMark = "及格", passed = true
            ),
            ScoreItem(
                year = "2024", term = "秋", courseId = "C03", courseName = "计算机网络", courseSeq = "3",
                courseGroup = "必修", score = "50", credit = 3.0, hours = 48, property = "必修",
                remark = "", examType = "正常考试", passMark = "不及格", passed = false
            )
        )

        val stats = AcademicParsers.calculateGpaStats(sampleScores)
        // C01: score 90 -> gpa = (90-50)/10 = 4.0, weight = 4.0 * 3.0 = 12.0
        // C02: score 80 -> gpa = (80-50)/10 = 3.0, weight = 3.0 * 4.0 = 12.0
        // C03: score 50 (<60) -> gpa = 0.0, weight = 0.0
        // total credits = 10.0, earned credits = 7.0
        // total gpa weight = 24.0 -> gpa = 24.0 / 10.0 = 2.4
        assertEquals(10.0, stats.totalCredits, 0.01)
        assertEquals(7.0, stats.earnedCredits, 0.01)
        assertEquals(1, stats.failedCount)
        assertEquals(2.4, stats.gpa, 0.01)
    }

    @Test
    fun testParseExams() {
        val html = loadFixture("0038-exam.all.html")
        val exams = AcademicParsers.parseExams(html)

        assertTrue("Exams list should not be empty", exams.isNotEmpty())
        for (exam in exams) {
            assertTrue("Course name must not be empty", exam.courseName.isNotEmpty())
            assertTrue("Exam time must not be empty", exam.time.isNotEmpty())
            assertTrue("Exam location must not be empty", exam.location.isNotEmpty())
        }
    }

    @Test
    fun testParseClassroomQueryOptions() {
        val html = loadFixture("0042-classroom.query.html")
        val options = AcademicParsers.parseClassroomQueryOptions(html)

        assertTrue("Campus areas should be extracted", options.areas.isNotEmpty())
        assertTrue("Buildings should be extracted", options.buildings.isNotEmpty())
    }

    @Test
    fun testParseCalendarInfo() {
        val html = loadFixture("0051-notice.calendarInfo.html")
        val calendar = AcademicParsers.parseCalendarInfo(html)

        assertTrue("Current week must be >= 1", calendar.currentWeek >= 1)
        assertTrue("Semester name should not be blank", calendar.semesterName.isNotBlank())
    }

    @Test
    fun testParseTeachingWeek() {
        val html = loadFixture("0005-auth.listLeft.html")
        val week = AcademicParsers.parseTeachingWeek(html)

        assertTrue("Teaching week must be between 1 and 26", week in 1..26)
    }
}
