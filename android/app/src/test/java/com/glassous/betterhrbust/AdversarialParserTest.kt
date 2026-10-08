package com.glassous.betterhrbust

import com.glassous.betterhrbust.core.model.ScoreItem
import com.glassous.betterhrbust.core.parser.AcademicParsers
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * Adversarial test harness for AcademicParsers.
 * Tests edge cases, malicious inputs, boundary conditions, and contract correctness.
 */
class AdversarialParserTest {

    private fun loadFixture(filename: String): String {
        val stream = javaClass.classLoader?.getResourceAsStream("fixtures/$filename")
            ?: File("src/test/resources/fixtures/$filename").inputStream()
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    // =========================================================================
    // 1. Comment Stripping (stripHtmlComments)
    // =========================================================================

    @Test
    fun testStripHtmlComments_NullAndEmpty() {
        assertEquals("", AcademicParsers.stripHtmlComments(null))
        assertEquals("", AcademicParsers.stripHtmlComments(""))
        assertEquals("   ", AcademicParsers.stripHtmlComments("   "))
    }

    @Test
    fun testStripHtmlComments_StandardAndMultiline() {
        val input = "<div><!-- This is a standard comment --><span>Content</span></div>"
        assertEquals("<div><span>Content</span></div>", AcademicParsers.stripHtmlComments(input))

        val multiline = """
            <table>
                <!--
                    Multi-line comment
                    with <th>fake header</th>
                -->
                <tr><td>Actual Data</td></tr>
            </table>
        """.trimIndent()
        val stripped = AcademicParsers.stripHtmlComments(multiline)
        assertFalse("Comment body must be removed", stripped.contains("fake header"))
        assertTrue("Actual data must be retained", stripped.contains("Actual Data"))
    }

    @Test
    fun testStripHtmlComments_SequentialAndHyphenated() {
        val sequential = "A<!-- c1 -->B<!-- c2 -->C<!-- c3 -->D"
        assertEquals("ABCD", AcademicParsers.stripHtmlComments(sequential))

        val dashesInside = "Hello<!-- a - b -- c ---- d -->World"
        assertEquals("HelloWorld", AcademicParsers.stripHtmlComments(dashesInside))
    }

    @Test
    fun testStripHtmlComments_TableColumnIntegrity() {
        val tableHtml = """
            <table class="datalist">
                <tr>
                    <th>学年</th>
                    <th>学期</th>
                    <!-- <th>废弃列</th> -->
                    <th>课程号</th>
                    <!-- <th>测试临时列</th> -->
                    <th>总评</th>
                </tr>
                <tr>
                    <td>2024</td>
                    <td>秋</td>
                    <!-- <td>IGNORE</td> -->
                    <td>CS101</td>
                    <!-- <td>IGNORE</td> -->
                    <td>95</td>
                </tr>
            </table>
        """.trimIndent()
        val clean = AcademicParsers.stripHtmlComments(tableHtml)
        assertFalse(clean.contains("废弃列"))
        assertFalse(clean.contains("测试临时列"))
        assertFalse(clean.contains("IGNORE"))
        assertTrue(clean.contains("CS101"))
        assertTrue(clean.contains("95"))
    }

    @Test
    fun testStripHtmlComments_NestedCommentBehavior() {
        // Standard HTML doesn't support nested comments.
        // A non-greedy regex <!--[\s\S]*?--> terminates at the first `-->`.
        val nested = "<!-- outer <!-- inner --> tail -->"
        val stripped = AcademicParsers.stripHtmlComments(nested)
        // Verify regex non-greedy behavior: first comment closes at first -->, leaving ' tail -->'
        assertEquals(" tail -->", stripped)
    }

    // =========================================================================
    // 2. Piecewise GPA Calculation (calculateGpaStats)
    // Formula: min(4.0, (score - 50) / 10) for score >= 60, and 0.0 otherwise.
    // =========================================================================

    @Test
    fun testGpa_ExactPiecewiseFormula_UpperBounds() {
        // Score = 100 -> (100 - 50) / 10 = 5.0 -> strictly capped to min(4.0, 5.0) = 4.0
        val s100 = listOf(createScoreItem("100", 3.0, true))
        val stats100 = AcademicParsers.calculateGpaStats(s100)
        assertEquals(4.0, stats100.gpa, 0.0001)

        // Score = 95 -> (95 - 50) / 10 = 4.5 -> strictly capped to min(4.0, 4.5) = 4.0
        val s95 = listOf(createScoreItem("95", 3.0, true))
        val stats95 = AcademicParsers.calculateGpaStats(s95)
        assertEquals(4.0, stats95.gpa, 0.0001)

        // Score = 90 -> (90 - 50) / 10 = 4.0 -> exact 4.0
        val s90 = listOf(createScoreItem("90", 3.0, true))
        val stats90 = AcademicParsers.calculateGpaStats(s90)
        assertEquals(4.0, stats90.gpa, 0.0001)
    }

    @Test
    fun testGpa_ExactPiecewiseFormula_IntermediateAndBoundary() {
        // Score = 85 -> (85 - 50) / 10 = 3.5
        val s85 = listOf(createScoreItem("85", 2.0, true))
        assertEquals(3.5, AcademicParsers.calculateGpaStats(s85).gpa, 0.0001)

        // Score = 75 -> (75 - 50) / 10 = 2.5
        val s75 = listOf(createScoreItem("75", 2.0, true))
        assertEquals(2.5, AcademicParsers.calculateGpaStats(s75).gpa, 0.0001)

        // Score = 60 -> (60 - 50) / 10 = 1.0 (lowest passing score)
        val s60 = listOf(createScoreItem("60", 2.0, true))
        assertEquals(1.0, AcademicParsers.calculateGpaStats(s60).gpa, 0.0001)

        // Score = 59.9 -> strictly < 60.0 -> GPA = 0.0
        val s599 = listOf(createScoreItem("59.9", 2.0, false))
        assertEquals(0.0, AcademicParsers.calculateGpaStats(s599).gpa, 0.0001)

        // Score = 59 -> strictly < 60.0 -> GPA = 0.0
        val s59 = listOf(createScoreItem("59", 2.0, false))
        assertEquals(0.0, AcademicParsers.calculateGpaStats(s59).gpa, 0.0001)

        // Score = 0 -> strictly < 60.0 -> GPA = 0.0
        val s0 = listOf(createScoreItem("0", 2.0, false))
        assertEquals(0.0, AcademicParsers.calculateGpaStats(s0).gpa, 0.0001)

        // Negative score -> strictly < 60.0 -> GPA = 0.0
        val sNeg = listOf(createScoreItem("-5", 2.0, false))
        assertEquals(0.0, AcademicParsers.calculateGpaStats(sNeg).gpa, 0.0001)
    }

    @Test
    fun testGpa_LetterGradeMapping() {
        val sExc = listOf(createScoreItem("优秀", 3.0, true))
        assertEquals(4.0, AcademicParsers.calculateGpaStats(sExc).gpa, 0.0001)

        val sGood = listOf(createScoreItem("良好", 3.0, true))
        assertEquals(3.5, AcademicParsers.calculateGpaStats(sGood).gpa, 0.0001)

        val sMid = listOf(createScoreItem("中等", 3.0, true))
        assertEquals(2.5, AcademicParsers.calculateGpaStats(sMid).gpa, 0.0001)

        val sPass = listOf(createScoreItem("及格", 3.0, true))
        assertEquals(1.5, AcademicParsers.calculateGpaStats(sPass).gpa, 0.0001)

        val sFail = listOf(createScoreItem("不及格", 3.0, false))
        assertEquals(0.0, AcademicParsers.calculateGpaStats(sFail).gpa, 0.0001)
    }

    @Test
    fun testGpa_WeightedAverageAndCredits() {
        val scores = listOf(
            createScoreItem("100", 4.0, true), // gp = 4.0, weight = 16.0
            createScoreItem("80", 2.0, true),  // gp = 3.0, weight = 6.0
            createScoreItem("50", 4.0, false)  // gp = 0.0, weight = 0.0
        )
        val stats = AcademicParsers.calculateGpaStats(scores)
        // totalCredits = 10.0, earnedCredits = 6.0, failedCount = 1
        // totalGpaWeight = 16.0 + 6.0 + 0 = 22.0 -> gpa = 22.0 / 10.0 = 2.2
        // totalScoreWeight = 100*4 + 80*2 + 50*4 = 400 + 160 + 200 = 760 -> weightedAvg = 76.0
        // excellentRate = 1 / 3 = 33.3%
        assertEquals(10.0, stats.totalCredits, 0.01)
        assertEquals(6.0, stats.earnedCredits, 0.01)
        assertEquals(1, stats.failedCount)
        assertEquals(2.2, stats.gpa, 0.01)
        assertEquals(76.0, stats.weightedAvg, 0.01)
        assertEquals(33.3, stats.excellentRate, 0.1)
    }

    @Test
    fun testGpa_EmptyAndZeroCreditsEdgeCases() {
        val emptyStats = AcademicParsers.calculateGpaStats(emptyList())
        assertEquals(0.0, emptyStats.gpa, 0.0001)
        assertEquals(0.0, emptyStats.weightedAvg, 0.0001)
        assertEquals(0.0, emptyStats.totalCredits, 0.0001)
        assertEquals(0, emptyStats.failedCount)

        // Zero credit course
        val zeroCreditScores = listOf(createScoreItem("90", 0.0, true))
        val zeroStats = AcademicParsers.calculateGpaStats(zeroCreditScores)
        assertEquals(0.0, zeroStats.gpa, 0.0001)
        assertEquals(0.0, zeroStats.totalCredits, 0.0001)
    }

    // =========================================================================
    // 3. Score Parsing: Total score (总评) vs In-process score (过程性成绩)
    // =========================================================================

    @Test
    fun testScoreParsing_DifferentiatesTotalScoreFromProcessScore() {
        // Construct table with col 6 as "总评" and col 13 as "过程性成绩"
        val html = """
            <html><body>
            <select name="year"><option value="2024">2024-2025</option></select>
            <table class="datalist">
                <tr>
                    <th>学年</th>
                    <th>学期</th>
                    <th>课程号</th>
                    <th>课程名</th>
                    <th>课序号</th>
                    <th>课组</th>
                    <th>总评</th>
                    <th>学分</th>
                    <th>学时</th>
                    <th>选课属性</th>
                    <th>备注</th>
                    <th>考试性质</th>
                    <th>及格标志</th>
                    <th>过程性成绩</th>
                </tr>
                <tr>
                    <td>2024</td>
                    <td>秋</td>
                    <td>CS101</td>
                    <td>计算机体系结构</td>
                    <td>01</td>
                    <td>必修</td>
                    <td>88</td>
                    <td>3.5</td>
                    <td>56</td>
                    <td>必修</td>
                    <td>无</td>
                    <td>期末</td>
                    <td>及格</td>
                    <td>62</td>
                </tr>
                <tr>
                    <td>2024</td>
                    <td>秋</td>
                    <td>CS102</td>
                    <td>编译原理</td>
                    <td>02</td>
                    <td>必修</td>
                    <td>94</td>
                    <td>4.0</td>
                    <td>64</td>
                    <td>必修</td>
                    <td>无</td>
                    <td>期末</td>
                    <td>及格</td>
                    <td>70</td>
                </tr>
            </table>
            </body></html>
        """.trimIndent()

        val result = AcademicParsers.parseScores(html)
        assertEquals(2, result.scores.size)

        val first = result.scores[0]
        assertEquals("计算机体系结构", first.courseName)
        // CRITICAL CHECK: score must be 总评 (88), NOT 过程性成绩 (62)
        assertEquals("88", first.score)
        assertEquals(3.5, first.credit, 0.01)

        val second = result.scores[1]
        assertEquals("编译原理", second.courseName)
        // CRITICAL CHECK: score must be 总评 (94), NOT 过程性成绩 (70)
        assertEquals("94", second.score)
        assertEquals(4.0, second.credit, 0.01)
    }

    @Test
    fun testScoreParsing_ReorderedColumnsRobustness() {
        // Table where "过程性成绩" appears BEFORE "总评"
        val htmlReordered = """
            <html><body>
            <table class="datalist">
                <tr>
                    <th>课程名</th>
                    <th>过程性成绩</th>
                    <th>总评</th>
                    <th>学分</th>
                </tr>
                <tr>
                    <td>高等代数</td>
                    <td>55</td>
                    <td>85</td>
                    <td>4.0</td>
                </tr>
            </table>
            </body></html>
        """.trimIndent()

        val result = AcademicParsers.parseScores(htmlReordered)
        assertEquals(1, result.scores.size)
        // Must still extract 总评 (85), not 过程性成绩 (55)
        assertEquals("85", result.scores[0].score)
        assertEquals(4.0, result.scores[0].credit, 0.01)
    }

    // =========================================================================
    // 4. DJB2 Color Hashing (hashCourseColor)
    // =========================================================================

    @Test
    fun testHashCourseColor_DeterminismAndStability() {
        val courseNames = listOf(
            "操作系统",
            "编译原理",
            "数据结构与算法",
            "计算机组成原理",
            "软件工程导论",
            "马克思主义基本原理",
            "高等数学A(1)",
            "College English IV",
            "Web前端开发技术"
        )

        for (name in courseNames) {
            val hash1 = AcademicParsers.hashCourseColor(name)
            val hash2 = AcademicParsers.hashCourseColor(name)
            val hash3 = AcademicParsers.hashCourseColor(name)
            assertEquals("Hash must be strictly deterministic for '$name'", hash1, hash2)
            assertEquals("Hash must be strictly deterministic across calls", hash2, hash3)
            assertTrue("Hash must be in range 0..11", hash1 in 0..11)
        }
    }

    @Test
    fun testHashCourseColor_WhitespaceInvariance() {
        val hashOriginal = AcademicParsers.hashCourseColor("数据结构与算法")
        val hashTrailing = AcademicParsers.hashCourseColor("数据结构与算法   ")
        val hashLeading = AcademicParsers.hashCourseColor("   数据结构与算法")
        val hashSpaced = AcademicParsers.hashCourseColor("数 据 结 构 与 算 法")

        assertEquals(hashOriginal, hashTrailing)
        assertEquals(hashOriginal, hashLeading)
        assertEquals(hashOriginal, hashSpaced)
    }

    @Test
    fun testHashCourseColor_EdgeCases() {
        // Empty string
        val emptyHash = AcademicParsers.hashCourseColor("")
        assertTrue(emptyHash in 0..11)
        // 5381 % 12 = 5
        assertEquals(5, emptyHash)

        // Only spaces
        val spaceHash = AcademicParsers.hashCourseColor("    \t\n  ")
        assertEquals(emptyHash, spaceHash)

        // Unicode emojis & symbols
        val emojiHash = AcademicParsers.hashCourseColor("💻 Computer Science 🚀")
        assertTrue(emojiHash in 0..11)

        // Very long string
        val longString = "A".repeat(5000)
        val longHash = AcademicParsers.hashCourseColor(longString)
        assertTrue(longHash in 0..11)
    }

    @Test
    fun testHashCourseColor_PaletteDistribution() {
        // Test that 30 distinct course names distribute across multiple color indices
        val courses = listOf(
            "高等数学", "线性代数", "概率论与数理统计", "大学物理", "程序设计基础",
            "面向对象程序设计", "数据结构", "离散数学", "操作系统", "计算机网络",
            "数据库系统原理", "计算机组成原理", "编译原理", "软件工程", "网络安全",
            "人工智能导论", "机器学习", "深度学习", "分布式系统", "云计算技术",
            "计算机图形学", "数字图像处理", "嵌入式系统", "移动应用开发", "信息检索",
            "大学英语", "中国近现代史纲要", "思想道德与法治", "体育", "军事理论"
        )

        val usedBuckets = courses.map { AcademicParsers.hashCourseColor(it) }.toSet()
        // Must occupy at least 6 different buckets out of 12 for good palette variety
        assertTrue("DJB2 must achieve healthy distribution (used ${usedBuckets.size}/12)", usedBuckets.size >= 6)
    }

    // =========================================================================
    // 5. Week Filtering (isCourseActiveInWeek)
    // =========================================================================

    @Test
    fun testIsCourseActiveInWeek_EmptyAndDefault() {
        assertTrue("Null expression means active all weeks", AcademicParsers.isCourseActiveInWeek(null, 5))
        assertTrue("Blank expression means active all weeks", AcademicParsers.isCourseActiveInWeek("", 5))
        assertTrue("Whitespace expression means active all weeks", AcademicParsers.isCourseActiveInWeek("   ", 5))
        assertTrue("Non-numeric expression like 全周 means active", AcademicParsers.isCourseActiveInWeek("全周", 8))
    }

    @Test
    fun testIsCourseActiveInWeek_OddWeeks() {
        val oddExpr = "1-16周(单)"
        for (w in 1..16) {
            val expected = (w % 2 != 0)
            assertEquals("Week $w for '$oddExpr'", expected, AcademicParsers.isCourseActiveInWeek(oddExpr, w))
        }
        // Outside 1-16 range
        assertFalse("Week 17 is outside range", AcademicParsers.isCourseActiveInWeek(oddExpr, 17))
        assertFalse("Week 19 is outside range", AcademicParsers.isCourseActiveInWeek(oddExpr, 19))
    }

    @Test
    fun testIsCourseActiveInWeek_EvenWeeks() {
        val evenExpr = "1-16周(双)"
        for (w in 1..16) {
            val expected = (w % 2 == 0)
            assertEquals("Week $w for '$evenExpr'", expected, AcademicParsers.isCourseActiveInWeek(evenExpr, w))
        }
        // Outside 1-16 range
        assertFalse("Week 18 is outside range", AcademicParsers.isCourseActiveInWeek(evenExpr, 18))
        assertFalse("Week 0 is outside range", AcademicParsers.isCourseActiveInWeek(evenExpr, 0))
    }

    @Test
    fun testIsCourseActiveInWeek_ContinuousAndDisjointRanges() {
        // Continuous range
        val rangeExpr = "4-10周"
        assertFalse(AcademicParsers.isCourseActiveInWeek(rangeExpr, 3))
        assertTrue(AcademicParsers.isCourseActiveInWeek(rangeExpr, 4))
        assertTrue(AcademicParsers.isCourseActiveInWeek(rangeExpr, 7))
        assertTrue(AcademicParsers.isCourseActiveInWeek(rangeExpr, 10))
        assertFalse(AcademicParsers.isCourseActiveInWeek(rangeExpr, 11))

        // Disjoint ranges: 1-4, 8-12, 16周
        val disjointExpr = "1-4周, 8-12周, 16周"
        assertTrue(AcademicParsers.isCourseActiveInWeek(disjointExpr, 1))
        assertTrue(AcademicParsers.isCourseActiveInWeek(disjointExpr, 4))
        assertFalse(AcademicParsers.isCourseActiveInWeek(disjointExpr, 5))
        assertFalse(AcademicParsers.isCourseActiveInWeek(disjointExpr, 7))
        assertTrue(AcademicParsers.isCourseActiveInWeek(disjointExpr, 8))
        assertTrue(AcademicParsers.isCourseActiveInWeek(disjointExpr, 12))
        assertFalse(AcademicParsers.isCourseActiveInWeek(disjointExpr, 13))
        assertFalse(AcademicParsers.isCourseActiveInWeek(disjointExpr, 15))
        assertTrue(AcademicParsers.isCourseActiveInWeek(disjointExpr, 16))
        assertFalse(AcademicParsers.isCourseActiveInWeek(disjointExpr, 17))
    }

    @Test
    fun testIsCourseActiveInWeek_SingleWeek() {
        val singleExpr = "7周"
        assertFalse(AcademicParsers.isCourseActiveInWeek(singleExpr, 6))
        assertTrue(AcademicParsers.isCourseActiveInWeek(singleExpr, 7))
        assertFalse(AcademicParsers.isCourseActiveInWeek(singleExpr, 8))
    }

    @Test
    fun testIsCourseActiveInWeek_BoundaryValues() {
        val standardExpr = "1-16周"
        assertFalse("Week 0 should be inactive", AcademicParsers.isCourseActiveInWeek(standardExpr, 0))
        assertFalse("Negative week should be inactive", AcademicParsers.isCourseActiveInWeek(standardExpr, -5))
        assertFalse("Week 25 should be inactive", AcademicParsers.isCourseActiveInWeek(standardExpr, 25))
    }

    // Helper
    private fun createScoreItem(score: String, credit: Double, passed: Boolean): ScoreItem {
        return ScoreItem(
            year = "2024",
            term = "秋",
            courseId = "CS101",
            courseName = "测试课程",
            courseSeq = "01",
            courseGroup = "必修",
            score = score,
            credit = credit,
            hours = 48,
            property = "必修",
            remark = "",
            examType = "期末考试",
            passMark = if (passed) "及格" else "不及格",
            passed = passed
        )
    }
}
