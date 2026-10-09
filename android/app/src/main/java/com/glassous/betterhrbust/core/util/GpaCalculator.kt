package com.glassous.betterhrbust.core.util

import com.glassous.betterhrbust.core.model.CreditCategory
import com.glassous.betterhrbust.core.model.CreditsProgress
import com.glassous.betterhrbust.core.model.CurriculumGroup
import com.glassous.betterhrbust.core.model.DedupedScore
import com.glassous.betterhrbust.core.model.DegreeStats
import com.glassous.betterhrbust.core.model.EarlyGradStats
import com.glassous.betterhrbust.core.model.RecommendStats
import com.glassous.betterhrbust.core.model.RiskStats
import com.glassous.betterhrbust.core.model.ScoreItem
import com.glassous.betterhrbust.core.model.ScoreStats
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 学业统计模块（哈尔滨理工大学官方口径）
 *
 * 数据来源与算法依据：《哈尔滨理工大学学生管理规定》第二十一条
 *  - 学分绩点 = 成绩绩点 × 学分；平均学分绩点 = Σ(成绩绩点 × 学分) / Σ学分
 *  - 百分制：60 分 = 1 绩点，60 分以上每增 1 分 +0.1（即 (成绩-50)/10，100 分 = 5.0）；低于 60 分绩点为 0
 *  - 五级记分制：不及格 0 / 及格 1.5 / 中等 2.5 / 良好 3.5 / 优秀 4.5
 *
 * 相关门槛：
 *  - 学位证：必修课平均学分绩点 ≥ 1.5
 *  - 提前毕业：必修课平均学分绩点 ≥ 4.0
 *  - 学业处理：累计挂科 > 15 学分留降级；> 25 学分退学
 *  - 推免：必修课成绩全部合格，且补考与重修课程累计不超过两门
 *
 * 统计范围：全部学业指标只统计必修课，限选与任选课不参与任何计算。
 *
 * 各学院 / 各年度细则可能存在差异，门槛均为可配置常量，界面须标注"以学校教务处口径为准"。
 *
 * 与 Web 端 web/src/services/academic/stats.js 保持完全一致的算法，便于两端口径统一。
 */
object GpaCalculator {

    /** 学位绩点门槛（必修课平均学分绩点下限，以学校教务处口径为准） */
    const val DEGREE_GPA_THRESHOLD = 1.5

    /** 提前毕业门槛：全部课程平均学分绩点不低于该值 */
    const val EARLY_GRAD_GPA_THRESHOLD = 4.0

    /** 累计挂科学分上限（超过 → 留降级） */
    const val RISK_DOWNGRADE_CREDITS = 15.0

    /** 累计挂科学分上限（超过 → 退学处理） */
    const val RISK_EXPEL_CREDITS = 25.0

    /** 推免：补考 + 重修课程累计门数上限 */
    const val RECOMMEND_RETAKE_LIMIT = 2

    /** 已获得学分是否只统计必修课（限选 / 任选不计） */
    const val EARNED_CREDITS_REQUIRED_ONLY = false

    /** 学分统计口径说明文案（面向用户，保持简短） */
    val EARNED_CREDITS_NOTE: String
        get() = if (EARNED_CREDITS_REQUIRED_ONLY) {
            "仅统计必修课，重修/补考合并计一次"
        } else {
            "按全部课程统计，重修/补考合并计一次"
        }

    /** 学业统计口径说明文案（全部指标仅统计必修课） */
    const val STATS_SCOPE_NOTE = "学位绩点：学业课 + E 类最高 1 门 + 剩余 A–E 类最高 1 门；风险与推免按必修课统计"

    /**
     * 是否参与学业统计的课程。
     * 统计口径：全部指标只统计必修课，选修课（限选 + 任选）不参与任何计算。
     */
    fun isCountedCourse(property: String?): Boolean = isRequired(property)

    /** 五级记分制 → 成绩绩点（官方折算） */
    private val LEVEL_GPA = mapOf(
        "优秀" to 4.5, "优" to 4.5,
        "良好" to 3.5, "良" to 3.5,
        "中等" to 2.5, "中" to 2.5,
        "及格" to 1.5, "合格" to 1.5, "及格线" to 1.5,
        "不及格" to 0.0, "不合格" to 0.0
    )

    /** 五级记分制 → 折算百分制（仅用于加权平均分估算） */
    private val LEVEL_SCORE = mapOf(
        "优秀" to 95.0, "优" to 95.0,
        "良好" to 85.0, "良" to 85.0,
        "中等" to 75.0, "中" to 75.0,
        "及格" to 65.0, "合格" to 65.0, "及格线" to 65.0,
        "不及格" to 0.0, "不合格" to 0.0
    )

    /** 补考 / 重修 关键字（用于推免资格自检的补考重修门数统计） */
    private val RETAKE_KEYWORDS = listOf("补考", "重修", "重考", "清考")

    /** 成绩折算结果 */
    data class ScoreValue(
        val numeric: Double?,
        val gradePoint: Double,
        val estimated: Double?
    )

    private fun round1(value: Double): Double = (value * 10.0).roundToInt() / 10.0

    private fun round2(value: Double): Double = (value * 100.0).roundToInt() / 100.0

    /**
     * 将成绩文本归一化为百分制数值与官方成绩绩点
     */
    fun parseScoreValue(raw: String?): ScoreValue {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return ScoreValue(null, 0.0, null)

        val num = text.toDoubleOrNull()
        if (num != null) {
            // 官方五分制：60 分 = 1 绩点，每增 1 分 +0.1，上限 5.0；低于 60 分为 0
            val gp = if (num >= 60.0) min(5.0, (num - 50.0) / 10.0) else 0.0
            return ScoreValue(num, gp, num)
        }

        val key = text.replace("\\s".toRegex(), "")
        if (LEVEL_GPA.containsKey(key)) {
            return ScoreValue(null, LEVEL_GPA[key] ?: 0.0, LEVEL_SCORE[key])
        }
        return ScoreValue(null, 0.0, null)
    }

    /** 单门课程成绩绩点（0 ~ 5.0） */
    fun gradePoint(raw: String?): Double = parseScoreValue(raw).gradePoint

    /** 选课属性归一化：required / limited / elective / other */
    fun resolveProperty(property: String?): String {
        val p = property?.trim().orEmpty()
        if (p.isEmpty()) return "other"
        return when {
            p.contains("必修") -> "required"
            p.contains("任选") || p.contains("公选") || p.contains("通识") -> "elective"
            p.contains("限选") || p.contains("选") -> "limited"
            else -> "other"
        }
    }

    /** 是否必修课 */
    fun isRequired(property: String?): Boolean = resolveProperty(property) == "required"

    /** 是否任选课 */
    fun isElective(property: String?): Boolean = resolveProperty(property) == "elective"

    /** 是否学位课（必修 + 限选） */
    fun isDegreeCourse(property: String?): Boolean {
        val p = resolveProperty(property)
        return p == "required" || p == "limited"
    }

    private fun courseKey(item: ScoreItem): String =
        item.courseId.trim().ifEmpty { item.courseName }

    private fun isRetakeRecord(item: ScoreItem): Boolean {
        val text = "${item.examType}${item.remark}"
        return RETAKE_KEYWORDS.any { text.contains(it) }
    }

    /**
     * 成绩记录合并去重：同一门课（courseId，空则回退 courseName）只保留一条
     * 规则：优先保留"通过且折算分最高"的记录；若该课从未通过，则保留折算分最高的一条用于挂科统计
     */
    fun dedupeScores(scores: List<ScoreItem>): List<DedupedScore> {
        val order = mutableListOf<String>()
        val groups = LinkedHashMap<String, MutableList<ScoreItem>>()

        scores.forEach { item ->
            val key = courseKey(item)
            if (key.isEmpty()) return@forEach
            if (!groups.containsKey(key)) {
                groups[key] = mutableListOf()
                order.add(key)
            }
            groups[key]?.add(item)
        }

        return order.map { key ->
            val items = groups[key].orEmpty().distinct()
            val best = items.sortedWith(
                compareByDescending<ScoreItem> { it.passed }
                    .thenByDescending { parseScoreValue(it.score).estimated ?: 0.0 }
            ).first()
            val retake = items.any { isRetakeRecord(it) } || items.map { it.year to it.term }.distinct().size > 1
            DedupedScore(
                item = best,
                recordCount = items.size,
                isRetake = retake,
                gradePoint = parseScoreValue(best.score).gradePoint
            )
        }
    }

    fun isLowScore(score: String?): Boolean = parseScoreValue(score).estimated?.let { it < 70 } ?: false

    private fun electiveCategory(item: ScoreItem): Char? {
        val text = "${item.courseGroup} ${item.courseName} ${item.property}".uppercase()
        return Regex("([ABCDE])\\s*类|[（(]([ABCDE])[）)]").find(text)
            ?.groupValues?.drop(1)?.firstOrNull { it.isNotEmpty() }?.first()
    }

    fun degreeCourses(scores: List<ScoreItem>): List<ScoreItem> {
        val courses = dedupeScores(scores).map { it.item }.filter { it.credit > 0 }
        val electives = courses.filter { electiveCategory(it) != null }
        val ranking = compareByDescending<ScoreItem> { parseScoreValue(it.score).estimated ?: Double.NEGATIVE_INFINITY }
            .thenBy { it.courseId }
        val academic = courses.filter { electiveCategory(it) == null && isDegreeCourse(it.property) }
        val firstE = electives.filter { electiveCategory(it) == 'E' }.sortedWith(ranking).firstOrNull()
        val second = if (firstE != null) electives.filter { it !== firstE }.sortedWith(ranking).firstOrNull() else null
        return academic + listOfNotNull(firstE, second)

    }

    private data class WeightedResult(
        val creditSum: Double,
        val gpa: Double,
        val weightedAvg: Double,
        val excCount: Int
    )

    private fun calcWeighted(courses: List<ScoreItem>): WeightedResult {
        var creditSum = 0.0
        var scoreWeight = 0.0
        var gpaWeight = 0.0
        var excCount = 0

        courses.forEach { item ->
            val cr = item.credit
            if (cr <= 0.0) return@forEach
            val value = parseScoreValue(item.score)
            val estimated = value.estimated ?: return@forEach

            creditSum += cr
            scoreWeight += estimated * cr
            gpaWeight += value.gradePoint * cr
            val numeric = value.numeric
            if ((numeric != null && numeric >= 90.0) || (numeric == null && value.gradePoint >= 4.5)) {
                excCount += 1
            }
        }

        return WeightedResult(
            creditSum = creditSum,
            gpa = if (creditSum > 0) round2(gpaWeight / creditSum) else 0.0,
            weightedAvg = if (creditSum > 0) round1(scoreWeight / creditSum) else 0.0,
            excCount = excCount
        )
    }

    /**
     * 学业统计总入口（五分制 GPA + 必修课学分 + 四项特色算法）
     */
    fun buildStats(scores: List<ScoreItem>): ScoreStats {
        val deduped = dedupeScores(scores)
        // 统计口径：仅统计必修课（限选 / 任选均不参与任何计算）
        val requiredRecords = deduped.filter { isRequired(it.item.property) && it.item.credit > 0 }
        val requiredCourses = requiredRecords.map { it.item }

        val totalCredits = round1(requiredCourses.sumOf { it.credit })
        val earnedCredits = round1(requiredCourses.filter { it.passed }.sumOf { it.credit })

        val failedCourses = requiredCourses.filter { !it.passed }
        val failedCredits = round1(failedCourses.sumOf { it.credit })

        // 必修课加权：GPA / 加权平均分 / 优秀率
        val overall = calcWeighted(requiredCourses)

        val selectedDegreeCourses = degreeCourses(scores)
        val degreeWeight = calcWeighted(selectedDegreeCourses)
        val degreeAllPassed = selectedDegreeCourses.isNotEmpty() && selectedDegreeCourses.all { it.passed }
        val degreeCredits = selectedDegreeCourses.filter { it.credit > 0 && parseScoreValue(it.score).estimated != null }.sumOf { it.credit }
        val degreeRawGpa = if (degreeCredits > 0) selectedDegreeCourses.sumOf { it.credit.coerceAtLeast(0.0) * gradePoint(it.score) } / degreeCredits else 0.0
        val degreeQualified = degreeCredits > 0 && degreeRawGpa >= DEGREE_GPA_THRESHOLD

        // ---- 特色算法 ② 推免 / 保研（必修课口径，统计补考 + 重修门数） ----
        val retakeCount = requiredRecords.count { it.isRetake }
        val recommendAllPassed = requiredCourses.isNotEmpty() && failedCourses.isEmpty()
        val recommendRetakeOk = retakeCount <= RECOMMEND_RETAKE_LIMIT
        val recommendQualified = requiredCourses.isNotEmpty() && recommendAllPassed && recommendRetakeOk

        // ---- 特色算法 ③ 学业风险预警 ----
        val riskLevel = when {
            failedCredits > RISK_EXPEL_CREDITS -> "expel"
            failedCredits > RISK_DOWNGRADE_CREDITS -> "downgrade"
            else -> "none"
        }
        val riskLabel = when (riskLevel) {
            "expel" -> "退学风险"
            "downgrade" -> "留降级风险"
            else -> "正常"
        }
        val riskDescription = when (riskLevel) {
            "expel" -> "累计挂科 $failedCredits 学分，已超过 ${RISK_EXPEL_CREDITS.toInt()} 学分退学警戒线"
            "downgrade" -> "累计挂科 $failedCredits 学分，已超过 ${RISK_DOWNGRADE_CREDITS.toInt()} 学分留降级警戒线"
            else -> "累计挂科 $failedCredits 学分，未触及 ${RISK_DOWNGRADE_CREDITS.toInt()} 学分留降级警戒线"
        }

        // ---- 特色算法 ④ 提前毕业 ----
        val earlyQualified = overall.creditSum > 0 && overall.gpa >= EARLY_GRAD_GPA_THRESHOLD

        return ScoreStats(
            gpa = overall.gpa,
            weightedAvg = overall.weightedAvg,
            totalCredits = totalCredits,
            earnedCredits = earnedCredits,
            failedCount = failedCourses.size,
            excellentRate = if (requiredCourses.isNotEmpty()) {
                round1(overall.excCount * 100.0 / requiredCourses.size)
            } else {
                0.0
            },
            failedCredits = failedCredits,
            courseCount = requiredCourses.size,
            rawCourseCount = scores.size,
            dedupedCount = deduped.size,
            retakeCount = deduped.count { it.isRetake && it.item.credit > 0 },
            degree = DegreeStats(
                gpa = degreeWeight.gpa,
                courseCount = selectedDegreeCourses.size,
                requiredCredits = round1(selectedDegreeCourses.sumOf { it.credit }),
                earnedCredits = round1(selectedDegreeCourses.filter { it.passed }.sumOf { it.credit }),
                allPassed = degreeAllPassed,
                threshold = DEGREE_GPA_THRESHOLD,
                qualified = degreeQualified
            ),
            recommend = RecommendStats(
                gpa = overall.gpa,
                courseCount = requiredCourses.size,
                allPassed = recommendAllPassed,
                retakeCount = retakeCount,
                retakeLimit = RECOMMEND_RETAKE_LIMIT,
                qualified = recommendQualified
            ),
            risk = RiskStats(
                failedCredits = failedCredits,
                downgradeLine = RISK_DOWNGRADE_CREDITS,
                expelLine = RISK_EXPEL_CREDITS,
                level = riskLevel,
                label = riskLabel,
                description = riskDescription
            ),
            earlyGraduation = EarlyGradStats(
                gpa = overall.gpa,
                threshold = EARLY_GRAD_GPA_THRESHOLD,
                qualified = earlyQualified
            )
        )
    }

    fun isSoftwareDirectionGroup(group: CurriculumGroup): Boolean {
        val courses = group.courses.distinctBy { it.code.ifBlank { it.name } }
        val optional = resolveProperty(group.property) != "required" || group.name.contains("选修") || group.name.contains("限选")
        val tenCourses = courses.size == 10 && courses.all { it.credit == 2.5 }
        val groupRequirement = group.requiredCourses == 10 && group.requiredCredits == 25.0 &&
            Regex("专业|方向|软件").containsMatchIn(group.name)
        return optional && (tenCourses || groupRequirement)
    }

    fun planGroupRequiredCredits(group: CurriculumGroup): Double =
        if (isSoftwareDirectionGroup(group)) 10.0 else group.requiredCredits

    fun planGroupRequiredCourses(group: CurriculumGroup): Int =
        if (isSoftwareDirectionGroup(group)) 4 else group.requiredCourses

    /**
     * 计算培养方案课组学分完成度（概览页与培养方案页共用，保证两侧口径一致）
     */
    fun computeCreditsProgress(
        scores: List<ScoreItem>,
        groups: List<CurriculumGroup>,
        requiredOnly: Boolean = EARNED_CREDITS_REQUIRED_ONLY,
        planTotalCredits: Double? = null
    ): CreditsProgress {
        val deduped = dedupeScores(scores)
        val eligible = deduped
            .map { it.item }
            .filter { it.passed && (!requiredOnly || isRequired(it.property)) }

        val matched = mutableSetOf<String>()
        val categories = mutableListOf<CreditCategory>()

        if (groups.isNotEmpty()) {
            groups.distinctBy { it.id.ifBlank { it.name } }.forEach { g ->
                var earned = 0.0
                eligible.forEach { s ->
                    val key = courseKey(s)
                    if (matched.contains(key)) return@forEach
                    val inGroup = g.courses.any { it.code == s.courseId || it.name == s.courseName }
                    val groupName = s.courseGroup
                    val nameMatch = groupName.isNotEmpty() && (
                        g.name.contains(groupName) ||
                            groupName.contains(g.name) ||
                            (g.name.length >= 3 && groupName.length >= 3 && g.name.take(3) == groupName.take(3))
                        )
                    if (inGroup || nameMatch) {
                        earned += s.credit
                        matched.add(key)
                    }
                }
                categories.add(
                    CreditCategory(
                        name = g.name,
                        property = g.property,
                        required = round1(planGroupRequiredCredits(g)),
                        earned = round1(earned)
                    )
                )
            }
        } else {
            // 降级：无培养方案数据时，按成绩单中的课组归集（全部标记为已归属，避免重复计入"方案外课程"）
            val groupMap = LinkedHashMap<String, Double>()
            eligible.forEach { s ->
                val grp = s.courseGroup.ifEmpty { "其它" }
                groupMap[grp] = (groupMap[grp] ?: 0.0) + s.credit
                matched.add(courseKey(s))
            }
            groupMap.forEach { (grpName, cr) ->
                categories.add(
                    CreditCategory(
                        name = grpName,
                        property = "必修",
                        required = 0.0,
                        earned = round1(cr)
                    )
                )
            }
        }

        // 汇总未归属到方案课组的学分（方案外课程）
        var leftover = 0.0
        eligible.forEach { s ->
            val key = courseKey(s)
            if (!matched.contains(key)) leftover += s.credit
        }
        if (leftover > 0) {
            categories.add(
                CreditCategory(
                    name = if (groups.isNotEmpty()) "方案外课程（通识选修 / 实践拓展）" else "其它课程",
                    property = "任选",
                    required = 0.0,
                    earned = round1(leftover)
                )
            )
        }

        val earnedTotal = round1(categories.sumOf { it.earned })
        val requiredTotalRaw = round1(categories.sumOf { it.required })
        val summary = groups.firstOrNull { it.name.trim() in listOf("总计", "合计", "全部课程", "毕业要求", "培养方案总计") }
        val requiredTotal = planTotalCredits?.takeIf { it > 0 } ?: summary?.requiredCredits?.takeIf { it > 0 } ?: requiredTotalRaw

        return CreditsProgress(
            categories = categories,
            earnedTotal = earnedTotal,
            requiredTotal = requiredTotal,
            completionPercent = if (requiredTotal > 0) {
                min(100, ((earnedTotal / requiredTotal) * 100).roundToInt())
            } else 0,
            requiredOnly = requiredOnly
        )
    }
}
