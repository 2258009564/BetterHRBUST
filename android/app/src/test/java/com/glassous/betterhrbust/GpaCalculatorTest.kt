package com.glassous.betterhrbust

import com.glassous.betterhrbust.core.model.CurriculumGroup
import com.glassous.betterhrbust.core.model.PlanCourseDetail
import com.glassous.betterhrbust.core.model.ScoreItem
import com.glassous.betterhrbust.core.util.GpaCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 学业统计模块（五分制 + 四项特色算法）单元测试
 *
 * 算法依据《哈尔滨理工大学学生管理规定》第二十一条：
 * 60 分 = 1 绩点，60 分以上每增 1 分 +0.1（90 分 = 4.0，100 分 = 5.0），低于 60 分为 0；
 * 五级记分制：不及格 0 / 及格 1.5 / 中等 2.5 / 良好 3.5 / 优秀 4.5。
 */
class GpaCalculatorTest {

    private fun item(
        courseId: String,
        courseName: String = "课程$courseId",
        score: String,
        credit: Double,
        property: String = "必修",
        courseGroup: String = "",
        examType: String = "正常考试",
        remark: String = "",
        passed: Boolean = score.toDoubleOrNull()?.let { it >= 60.0 } ?: (score != "不及格"),
        year: String = "2024",
        term: String = "秋"
    ) = ScoreItem(
        year = year,
        term = term,
        courseId = courseId,
        courseName = courseName,
        courseSeq = "1",
        courseGroup = courseGroup,
        score = score,
        credit = credit,
        hours = (credit * 16).toInt(),
        property = property,
        remark = remark,
        examType = examType,
        passMark = if (passed) "及格" else "不及格",
        passed = passed
    )

    // ---------------- 五分制绩点换算 ----------------

    @Test
    fun testGradePointFivePointScale() {
        assertEquals(1.0, GpaCalculator.gradePoint("60"), 0.001)
        assertEquals(2.0, GpaCalculator.gradePoint("70"), 0.001)
        assertEquals(4.0, GpaCalculator.gradePoint("90"), 0.001)
        assertEquals(5.0, GpaCalculator.gradePoint("100"), 0.001)
        // 90 分以上不再封顶 4.0
        assertEquals(4.5, GpaCalculator.gradePoint("95"), 0.001)
        // 低于 60 分为 0
        assertEquals(0.0, GpaCalculator.gradePoint("59"), 0.001)
        assertEquals(0.0, GpaCalculator.gradePoint("0"), 0.001)
    }

    @Test
    fun testGradePointLevelScale() {
        assertEquals(4.5, GpaCalculator.gradePoint("优秀"), 0.001)
        assertEquals(3.5, GpaCalculator.gradePoint("良好"), 0.001)
        assertEquals(2.5, GpaCalculator.gradePoint("中等"), 0.001)
        assertEquals(1.5, GpaCalculator.gradePoint("及格"), 0.001)
        assertEquals(0.0, GpaCalculator.gradePoint("不及格"), 0.001)
        // 非成绩文本（缓考 / 免修）不折算
        assertEquals(0.0, GpaCalculator.gradePoint("缓考"), 0.001)
    }

    // ---------------- 去重与学分口径 ----------------

    @Test
    fun testDedupeKeepsHighestPassedRecord() {
        val scores = listOf(
            item("C01", score = "45", credit = 3.0, passed = false),
            item("C01", score = "88", credit = 3.0, property = "必修", examType = "补考", passed = true),
            item("C01", score = "92", credit = 3.0, property = "必修", examType = "重修", passed = true)
        )
        val deduped = GpaCalculator.dedupeScores(scores)
        assertEquals(1, deduped.size)
        assertEquals("92", deduped[0].item.score)
        assertEquals(3, deduped[0].recordCount)
        assertTrue(deduped[0].isRetake)
        assertEquals(4.2, deduped[0].gradePoint, 0.001)
    }

    @Test
    fun testCreditsOnlyCountRequiredAndDeduplicated() {
        val scores = listOf(
            item("C01", score = "90", credit = 3.0, property = "必修", passed = true),
            // 同一门必修课的重修记录不得重复累加学分
            item("C01", score = "75", credit = 3.0, property = "必修", examType = "重修", passed = true),
            item("C02", score = "80", credit = 4.0, property = "必修", passed = true),
            item("C03", score = "50", credit = 3.0, property = "必修", passed = false),
            // 限选与任选不参与任何统计
            item("E01", score = "95", credit = 2.0, property = "限选", passed = true),
            item("E02", score = "95", credit = 1.0, property = "任选", passed = true)
        )

        val stats = GpaCalculator.buildStats(scores)
        // 必修去重后：3 + 4 + 3 = 10 学分；通过：3 + 4 = 7 学分
        assertEquals(10.0, stats.totalCredits, 0.001)
        assertEquals(7.0, stats.earnedCredits, 0.001)
        assertEquals(1, stats.failedCount)
        assertEquals(3.0, stats.failedCredits, 0.001)
        // 参与统计的必修课 3 门；去重后课程总门数（含选修）5 门
        assertEquals(3, stats.courseCount)
        assertEquals(5, stats.dedupedCount)
    }

    @Test
    fun testElectivesExcludedFromAllStatistics() {
        val scores = listOf(
            item("C01", score = "90", credit = 3.0, property = "必修", passed = true),
            // 选修课挂科 / 低分不得影响 GPA、挂科门数与学业预警
            item("E01", score = "30", credit = 6.0, property = "限选", passed = false),
            item("E02", score = "20", credit = 8.0, property = "任选", passed = false),
            item("E03", score = "60", credit = 5.0, property = "任选", passed = true)
        )

        val stats = GpaCalculator.buildStats(scores)
        assertEquals(4.0, stats.gpa, 0.001)              // 仅 C01：(90-50)/10
        assertEquals(90.0, stats.weightedAvg, 0.001)
        assertEquals(0, stats.failedCount)               // 选修挂科不计入门数
        assertEquals(0.0, stats.failedCredits, 0.001)    // 选修挂科不触发学业预警
        assertEquals("none", stats.risk.level)
        assertEquals(3.0, stats.totalCredits, 0.001)
        assertEquals(3.0, stats.earnedCredits, 0.001)
        assertFalse(stats.degree.qualified)
        assertTrue(stats.recommend.qualified)
        assertTrue(stats.earlyGraduation.qualified)
    }

    @Test
    fun degreeUsesHighestEThenHighestRemainingIncludingEAndIgnoresZeroCredits() {
        val scores = listOf(
            item("REQ", score = "65", credit = 3.0),
            item("ZERO", score = "0", credit = 0.0),
            item("E1", score = "95", credit = 2.0, property = "任选", courseGroup = "E类"),
            item("E2", score = "90", credit = 2.0, property = "任选", courseGroup = "E类"),
            item("A1", score = "89", credit = 2.0, property = "任选", courseGroup = "A类"),
            item("D1", score = "60", credit = 2.0, property = "任选", courseGroup = "D类")
        )
        assertEquals(listOf("REQ", "E1", "E2"), GpaCalculator.degreeCourses(scores).map { it.courseId })
        val degree = GpaCalculator.buildStats(scores).degree
        assertEquals(7.0, degree.requiredCredits, 0.001)
        assertEquals(3.07, degree.gpa, 0.001)
        assertTrue(degree.qualified)
        assertEquals(listOf("REQ"), GpaCalculator.degreeCourses(scores.filter { it.courseGroup != "E类" }).map { it.courseId })
    }

    @Test
    fun academicCoursesWithLetterNamesAndProfessionalElectivesAreRetained() {
        val courses = listOf(item("MATH", courseName = "高等数学(A)", score = "85", credit = 3.0),
            item("MAJOR", score = "90", credit = 2.5, property = "任选", courseGroup = "专业选修"))
        assertEquals(listOf("MATH", "MAJOR"), GpaCalculator.degreeCourses(courses).map { it.courseId })
    }

    @Test
    fun duplicateNormalRecordIsNotRetakeAndTrueRetakeCountsOnce() {
        val normal = item("C01", score = "90", credit = 3.0)
        assertEquals(0, GpaCalculator.buildStats(listOf(normal, normal)).retakeCount)
        val retake = normal.copy(examType = "补考", score = "80")
        assertEquals(1, GpaCalculator.buildStats(listOf(normal, retake, retake)).retakeCount)
    }

    @Test
    fun professionalElectivesUseSchoolTenCreditRequirementAndChooseFour() {
        val group = CurriculumGroup("direction", "专业限选", "限选", 10.0, 4,
            courses = (1..10).map { PlanCourseDetail("DIR$it", "方向课程$it", 2.5, 40, "限选") })
        assertEquals(10.0, GpaCalculator.computeCreditsProgress(emptyList(), listOf(group)).requiredTotal, 0.001)
        assertEquals(10.0, GpaCalculator.planGroupRequiredCredits(group), 0.001)
        assertEquals(4, GpaCalculator.planGroupRequiredCourses(group))
        assertEquals(20.0, GpaCalculator.planGroupRequiredCredits(group.copy(requiredCredits = 20.0)), 0.001)
    }

    @Test
    fun explicitPlanTotalWinsAndDuplicateGroupsAreNotSummedTwice() {
        val group = CurriculumGroup("1", "基础课程", "必修", 20.0, 4)
        assertEquals(20.0, GpaCalculator.computeCreditsProgress(emptyList(), listOf(group, group)).requiredTotal, 0.001)
        assertEquals(158.5, GpaCalculator.computeCreditsProgress(emptyList(), listOf(group), planTotalCredits = 158.5).requiredTotal, 0.001)
        assertTrue(GpaCalculator.isLowScore("69"))
        assertFalse(GpaCalculator.isLowScore("70"))
    }

    // ---------------- 特色算法 ① 学位证 ----------------

    @Test
    fun testDegreeAlgorithm() {
        val qualified = GpaCalculator.buildStats(
            listOf(
                item("C01", score = "85", credit = 4.0, property = "必修", passed = true),
                // 选修课不计入学位绩点
                item("E01", score = "80", credit = 2.0, property = "限选", passed = true),
                item("E02", score = "60", credit = 1.0, property = "任选", passed = true)
            )
        )
        assertEquals(2, qualified.degree.courseCount)
        assertEquals(3.33, qualified.degree.gpa, 0.01)
        assertEquals(6.0, qualified.degree.requiredCredits, 0.001)
        assertTrue(qualified.degree.allPassed)
        assertTrue(qualified.degree.qualified)
        assertEquals(GpaCalculator.DEGREE_GPA_THRESHOLD, qualified.degree.threshold, 0.001)

        // 必修课挂科 → 不达标
        val failed = GpaCalculator.buildStats(
            listOf(
                item("C01", score = "85", credit = 4.0, property = "必修", passed = true),
                item("C02", score = "30", credit = 2.0, property = "必修", passed = false)
            )
        )
        assertFalse(failed.degree.allPassed)
        assertTrue(failed.degree.qualified)
    }

    @Test
    fun testDegreeThresholdIsOnePointFive() {
        // 学位证绩点门槛：必修课平均学分绩点 ≥ 1.5
        assertEquals(1.5, GpaCalculator.DEGREE_GPA_THRESHOLD, 0.001)

        // 绩点 1.8（≥ 1.5，但 < 2.0）→ 达标
        val pass = GpaCalculator.buildStats(
            listOf(item("C01", score = "68", credit = 3.0, property = "必修", passed = true))
        )
        assertEquals(1.8, pass.degree.gpa, 0.001)
        assertEquals(1.5, pass.degree.threshold, 0.001)
        assertTrue(pass.degree.qualified)

        // 绩点 1.0（< 1.5）→ 未达标
        val fail = GpaCalculator.buildStats(
            listOf(item("C01", score = "60", credit = 3.0, property = "必修", passed = true))
        )
        assertEquals(1.0, fail.degree.gpa, 0.001)
        assertFalse(fail.degree.qualified)
    }

    // ---------------- 特色算法 ② 推免 ----------------

    @Test
    fun testRecommendAlgorithm() {
        val scores = listOf(
            item("C01", score = "90", credit = 3.0, property = "必修", passed = true),
            item("C02", score = "88", credit = 3.0, property = "必修", examType = "补考", passed = true),
            item("C03", score = "85", credit = 3.0, property = "必修", examType = "重修", passed = true),
            item("C04", score = "75", credit = 2.0, property = "必修", examType = "补考", passed = true),
            item("E01", score = "55", credit = 1.0, property = "任选", passed = false)
        )
        val stats = GpaCalculator.buildStats(scores)
        // 3 门补考/重修 > 上限 2 → 不符合推免资格
        assertEquals(3, stats.recommend.retakeCount)
        assertEquals(2, stats.recommend.retakeLimit)
        assertFalse(stats.recommend.qualified)
        // 选修课完全不参与统计，只有必修课计入
        assertTrue(stats.recommend.allPassed)
        assertEquals(4, stats.recommend.courseCount)
    }

    @Test
    fun testRecommendQualifiedWhenWithinLimit() {
        val scores = listOf(
            item("C01", score = "90", credit = 3.0, property = "必修", passed = true),
            item("C02", score = "70", credit = 3.0, property = "必修", examType = "补考", passed = true),
            item("E01", score = "60", credit = 1.0, property = "任选", passed = true)
        )
        val stats = GpaCalculator.buildStats(scores)
        assertEquals(1, stats.recommend.retakeCount)
        assertTrue(stats.recommend.qualified)
    }

    // ---------------- 特色算法 ③ 学业风险预警 ----------------

    @Test
    fun testRiskAlertThresholds() {
        val normal = GpaCalculator.buildStats(
            listOf(item("C01", score = "50", credit = 10.0, passed = false))
        )
        assertEquals("none", normal.risk.level)
        assertEquals("正常", normal.risk.label)

        val downgrade = GpaCalculator.buildStats(
            listOf(item("C01", score = "50", credit = 16.0, passed = false))
        )
        assertEquals("downgrade", downgrade.risk.level)
        assertEquals("留降级风险", downgrade.risk.label)

        val expel = GpaCalculator.buildStats(
            listOf(item("C01", score = "50", credit = 26.0, passed = false))
        )
        assertEquals("expel", expel.risk.level)
        assertEquals("退学风险", expel.risk.label)
    }

    // ---------------- 特色算法 ④ 提前毕业 ----------------

    @Test
    fun testEarlyGraduation() {
        val qualified = GpaCalculator.buildStats(
            listOf(
                item("C01", score = "95", credit = 3.0, passed = true),
                item("C02", score = "95", credit = 3.0, passed = true)
            )
        )
        assertTrue(qualified.earlyGraduation.qualified)
        assertEquals(GpaCalculator.EARLY_GRAD_GPA_THRESHOLD, qualified.earlyGraduation.threshold, 0.001)

        val notQualified = GpaCalculator.buildStats(
            listOf(
                item("C01", score = "88", credit = 3.0, passed = true),
                item("C02", score = "90", credit = 3.0, passed = true)
            )
        )
        assertFalse(notQualified.earlyGraduation.qualified)
    }

    // ---------------- 培养方案学分完成度（与概览页同口径） ----------------

    @Test
    fun testCreditsProgressAlignsWithEarnedCredits() {
        val scores = listOf(
            item("C01", score = "90", credit = 3.0, property = "必修", passed = true),
            item("C02", score = "80", credit = 4.0, property = "必修", passed = true),
            item("C03", score = "50", credit = 3.0, property = "必修", passed = false),
            item("E01", score = "95", credit = 5.0, property = "限选", passed = true)
        )
        val groups = listOf(
            CurriculumGroup(
                id = "1",
                name = "学科基础课程",
                property = "必修",
                requiredCredits = 20.0,
                requiredCourses = 8,
                courses = listOf(
                    PlanCourseDetail("C01", "课程C01", 3.0, 48, "必修"),
                    PlanCourseDetail("C02", "课程C02", 4.0, 64, "必修"),
                    PlanCourseDetail("C03", "课程C03", 3.0, 48, "必修")
                )
            ),
            CurriculumGroup(
                id = "2",
                name = "专业限选课程",
                property = "限选",
                requiredCredits = 10.0,
                requiredCourses = 4,
                courses = listOf(PlanCourseDetail("E01", "课程E01", 5.0, 80, "限选"))
            )
        )

        val progress = GpaCalculator.computeCreditsProgress(scores, groups)
        val stats = GpaCalculator.buildStats(scores)

        // 已获总学分与概览页统计严格一致（只计必修并通过的课程）
        assertEquals(12.0, progress.earnedTotal, 0.001)
        assertEquals(12.0, progress.earnedTotal, 0.001)
        assertEquals(30.0, progress.requiredTotal, 0.001)
        // 限选课组不计入已获得学分
        val limited = progress.categories.first { it.name == "专业限选课程" }
        assertEquals(5.0, limited.earned, 0.001)
        assertEquals(10.0, limited.required, 0.001)
    }

    @Test
    fun testCreditsProgressFallbackWithoutPlan() {
        val scores = listOf(
            item("C01", score = "90", credit = 3.0, property = "必修", courseGroup = "通识必修", passed = true),
            item("C02", score = "80", credit = 2.0, property = "必修", courseGroup = "专业必修", passed = true)
        )
        val progress = GpaCalculator.computeCreditsProgress(scores, emptyList())
        assertEquals(5.0, progress.earnedTotal, 0.001)
        assertEquals(2, progress.categories.size)
        // required = 3×1.2 + 2×1.2 = 6.0 → 5 / 6 ≈ 83%
        assertEquals(0, progress.completionPercent)
        assertEquals(0.0, progress.requiredTotal, 0.001)
    }
}
