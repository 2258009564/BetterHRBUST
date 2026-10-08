package com.glassous.betterhrbust.core.parser

import com.glassous.betterhrbust.core.model.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.min

object AcademicParsers {

    private val NAMED_ENTITIES = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ",
        "copy" to "©", "reg" to "®", "trade" to "™", "hellip" to "…",
        "mdash" to "—", "ndash" to "–", "middot" to "·", "deg" to "°",
        "laquo" to "«", "raquo" to "»",
        "ldquo" to "“", "rdquo" to "”", "lsquo" to "‘", "rsquo" to "’"
    )

    fun stripHtmlComments(html: String?): String {
        if (html.isNullOrEmpty()) return ""
        return html.replace(Regex("<!--[\\s\\S]*?-->"), "")
    }

    fun decodeEntities(text: String?): String {
        if (text == null || !text.contains('&')) return text ?: ""
        val pattern = Regex("&(?:#x([0-9a-fA-F]+)|#([0-9]+)|([a-zA-Z][a-zA-Z0-9]*));")
        var out: String = text
        for (i in 0 until 3) {
            var replaced = false
            val next = pattern.replace(out) { matchResult ->
                replaced = true
                val hex = matchResult.groups[1]?.value
                val dec = matchResult.groups[2]?.value
                val name = matchResult.groups[3]?.value
                when {
                    hex != null -> {
                        val code = hex.toIntOrNull(16)
                        if (code != null && code in 1..0x10FFFF) String(Character.toChars(code)) else matchResult.value
                    }
                    dec != null -> {
                        val code = dec.toIntOrNull(10)
                        if (code != null && code in 1..0x10FFFF) String(Character.toChars(code)) else matchResult.value
                    }
                    name != null -> NAMED_ENTITIES[name.lowercase()] ?: matchResult.value
                    else -> matchResult.value
                }
            }
            if (!replaced || next == out) break
            out = next
        }
        return out
    }

    fun isLoginPage(html: String?): Boolean {
        if (html.isNullOrEmpty()) return false
        val markers = listOf("j_acegi_security_check", "getCaptcha.do", "j_captcha")
        return markers.any { html.contains(it) }
    }

    fun parseLoginFailureReason(html: String?): String {
        if (html.isNullOrEmpty()) return "登录失败，请检查学号与密码"
        return when {
            html.contains("验证码") -> "验证码错误或已过期，请刷新重试"
            html.contains("密码") || html.contains("badCredentials", ignoreCase = true) -> "学号或密码错误"
            html.contains("用户名") || html.contains("用户不存在") -> "该学号不存在"
            html.contains("锁定") -> "账号已被系统锁定，请稍后再试"
            else -> "登录失败，请检查学号与密码"
        }
    }

    fun parseStudentContext(html: String): StudentContext {
        val doc: Document = Jsoup.parse(html)

        var studentId = ""
        var year = ""
        val ctrtMatch = Regex("""studentid=["']?(\d+)["']?[\s\S]*?year=["']?(\d+)["']?""", RegexOption.IGNORE_CASE).find(html)
        if (ctrtMatch != null) {
            studentId = ctrtMatch.groupValues[1]
            year = ctrtMatch.groupValues[2]
        } else {
            val sidM = Regex("""studentid\s*=\s*["']?(\d+)["']?""", RegexOption.IGNORE_CASE).find(html)
            val yrM = Regex("""year\s*=\s*["']?(\d+)["']?""", RegexOption.IGNORE_CASE).find(html)
            if (sidM != null) studentId = sidM.groupValues[1]
            if (yrM != null) year = yrM.groupValues[1]
        }

        var term = "2"
        val termSelect = doc.selectFirst("select[name=term]")
        if (termSelect != null) {
            val selectedOption = termSelect.selectFirst("option[selected]") ?: termSelect.selectFirst("option")
            if (selectedOption != null) {
                term = selectedOption.`val`()
            }
        }

        val courses = mutableListOf<CurrentCourseItem>()
        val table = doc.selectFirst("table.infolist_tab")
        if (table != null) {
            val rows = table.select("tr.infolist_common")
            for (tr in rows) {
                val tds = tr.select("td")
                if (tds.size >= 6) {
                    courses.add(
                        CurrentCourseItem(
                            courseId = tds.getOrNull(0)?.text()?.trim() ?: "",
                            courseSeq = tds.getOrNull(1)?.text()?.trim() ?: "",
                            courseName = tds.getOrNull(2)?.text()?.trim() ?: "",
                            teacher = tds.getOrNull(3)?.text()?.trim() ?: "",
                            credit = tds.getOrNull(4)?.text()?.trim()?.toDoubleOrNull() ?: 0.0,
                            property = tds.getOrNull(5)?.text()?.trim() ?: "",
                            examWay = tds.getOrNull(6)?.text()?.trim() ?: "",
                            examType = tds.getOrNull(7)?.text()?.trim() ?: "",
                            timeAndPlace = tds.getOrNull(9)?.text()?.trim() ?: ""
                        )
                    )
                }
            }
        }

        return StudentContext(studentId = studentId, year = year, term = term, courses = courses)
    }

    fun parsePersonalInfo(html: String): PersonalInfo {
        val doc: Document = Jsoup.parse(html)
        var studentNumber = ""
        var realName = ""
        var college = ""
        var major = ""
        var direction = ""
        var studentType = ""
        var grade = ""
        var className = ""
        var idCard = ""
        var email = ""
        var phone = ""
        var address = ""
        var postalCode = ""
        var photoUrl = ""
        val changes = mutableListOf<StatusChange>()

        val photoImg = doc.selectFirst("img[src*=loadphoto_added.jsdo], img[src*=showStudentImage.jsp]")
        if (photoImg != null) {
            photoUrl = photoImg.attr("src")
        }

        val formTable = doc.selectFirst("table.form")
        if (formTable != null) {
            val rows = formTable.select("tr")
            for (row in rows) {
                val ths = row.select("th")
                val tds = row.select("td")
                for (i in 0 until min(ths.size, tds.size)) {
                    val key = ths[i].text().trim()
                    val value = tds[i].text().replace("\u00A0", "").trim()
                    when {
                        key.contains("用户名") -> studentNumber = value
                        key.contains("真实姓名") -> realName = value
                        key.contains("所在院系") -> college = value
                        key.contains("专业") && !key.contains("方向") -> major = value
                        key.contains("方向") -> direction = value
                        key.contains("学生类别") -> studentType = value
                        key.contains("年级") -> grade = value
                        key.contains("班级") -> className = value
                        key.contains("证件号码") -> idCard = value
                        key.contains("电子邮箱") -> email = value
                        key.contains("联系电话") -> phone = value
                        key.contains("通讯地址") -> address = value
                        key.contains("邮政编码") -> postalCode = value
                    }
                }
            }
        }

        val changesTable = doc.selectFirst("table.datalist")
        if (changesTable != null) {
            val rows = changesTable.select("tr.infolist_hr_common, tr.infolist_common")
            for (tr in rows) {
                val tds = tr.select("td")
                if (tds.size >= 4) {
                    changes.add(
                        StatusChange(
                            type = tds[0].text().trim(),
                            date = tds[1].text().trim(),
                            reason = tds[2].text().trim(),
                            remark = tds[3].text().trim()
                        )
                    )
                }
            }
        }

        return PersonalInfo(
            studentNumber = studentNumber,
            realName = realName,
            college = college,
            major = major,
            direction = direction,
            studentType = studentType,
            grade = grade,
            className = className,
            idCard = idCard,
            email = email,
            phone = phone,
            address = address,
            postalCode = postalCode,
            status = "在籍（注册）",
            photoUrl = photoUrl,
            changes = changes
        )
    }

    fun parseTimetable(html: String): TimetableResult {
        val doc: Document = Jsoup.parse(html)
        val cells = mutableListOf<TimetableCell>()
        val unarranged = mutableListOf<UnarrangedCourse>()

        val table = doc.selectFirst("table#timetable")
        if (table != null) {
            val rows = table.select("tr.infolist_hr_common")
            for ((rowIndex, tr) in rows.withIndex()) {
                val th = tr.selectFirst("th")
                val sectionLabel = if (th != null) {
                    decodeEntities(th.text().replace(Regex("<br\\s*/?>"), "").trim())
                } else {
                    "第${rowIndex + 1}大节"
                }

                val tds = tr.select("td")
                for ((dayIndex, td) in tds.withIndex()) {
                    val id = td.attr("id")
                    val rawHtml = td.html().trim()
                    if (rawHtml.isEmpty() || rawHtml == "&nbsp;" || td.text().trim().isEmpty()) {
                        continue
                    }

                    val lines = rawHtml
                        .split(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE))
                        .map { decodeEntities(it.replace(Regex("<[^>]+>"), "")).trim() }
                        .filter { it.isNotEmpty() }
                    if (lines.isEmpty()) continue

                    var courseName = lines[0]
                    var courseSeq = ""
                    val nameMatch = Regex("""<<?(.*?)(?:>>|;)?(?:\s*;\s*(\d+))?$""").find(courseName)
                    if (nameMatch != null) {
                        courseName = nameMatch.groups[1]?.value ?: courseName
                        courseSeq = nameMatch.groups[2]?.value ?: ""
                    }
                    courseName = courseName.replace(Regex("""^[<《]+|[>》]+$"""), "").trim()

                    val location = lines.getOrNull(1) ?: ""
                    val teacher = lines.getOrNull(2) ?: ""
                    val weeks = lines.getOrNull(3) ?: ""
                    val hoursType = lines.getOrNull(4) ?: ""

                    var day = dayIndex + 1
                    var courseId = ""
                    if (id.isNotEmpty()) {
                        val parts = id.split("-")
                        val parsedDay = parts[0].toIntOrNull()
                        if (parsedDay != null && parsedDay in 1..7) day = parsedDay
                        if (parts.size > 1 && parts[1].matches(Regex("""\d+"""))) courseId = parts[1]
                    }

                    cells.add(
                        TimetableCell(
                            id = id,
                            courseId = courseId,
                            day = day,
                            sectionIndex = rowIndex + 1,
                            sectionLabel = sectionLabel,
                            courseName = courseName,
                            courseSeq = courseSeq,
                            location = location,
                            teacher = teacher,
                            weeks = weeks,
                            hoursType = hoursType,
                            rawLines = lines
                        )
                    )
                }
            }
        }

        val noArrTable = doc.selectFirst("table#noArrangement")
        if (noArrTable != null) {
            val rows = noArrTable.select("tr")
            for (i in 1 until rows.size) {
                val tds = rows[i].select("td")
                if (tds.size >= 8) {
                    unarranged.add(
                        UnarrangedCourse(
                            courseId = decodeEntities(tds[0].text().trim()),
                            courseName = decodeEntities(tds[1].text().trim()),
                            courseSeq = decodeEntities(tds[2].text().trim()),
                            teacher = decodeEntities(tds[3].text().trim()),
                            mergeClass = decodeEntities(tds[4].text().trim()),
                            weeks = decodeEntities(tds[5].text().trim()),
                            day = decodeEntities(tds[6].text().trim()),
                            location = decodeEntities(tds[7].text().trim())
                        )
                    )
                }
            }
        }

        return TimetableResult(cells = cells, unarranged = unarranged)
    }

    fun isCourseActiveInWeek(weeksExpr: String?, targetWeek: Int): Boolean {
        if (weeksExpr.isNullOrBlank()) return true
        val isOdd = weeksExpr.contains("单")
        val isEven = weeksExpr.contains("双")
        if (isOdd && targetWeek % 2 == 0) return false
        if (isEven && targetWeek % 2 != 0) return false

        val ranges = Regex("""\d+(?:-\d+)?""").findAll(weeksExpr).toList()
        if (ranges.isEmpty()) return true

        for (match in ranges) {
            val parts = match.value.split("-").mapNotNull { it.toIntOrNull() }
            if (parts.isNotEmpty()) {
                val start = parts[0]
                val end = if (parts.size > 1) parts[1] else start
                if (targetWeek in start..end) return true
            }
        }
        return false
    }

    fun hashCourseColor(key: String): Int {
        var hash = 5381L
        for (ch in key.replace(Regex("""\s+"""), "")) {
            hash = (((hash shl 5) + hash) + ch.code) and 0xFFFFFFFFL
        }
        return (hash % 12).toInt()
    }

    fun parseScores(html: String): ScoreResult {
        val doc: Document = Jsoup.parse(html)
        val yearOptions = mutableListOf<YearOption>()
        val yearSelect = doc.selectFirst("select[name=year]")
        if (yearSelect != null) {
            val options = yearSelect.select("option")
            for (opt in options) {
                val value = opt.`val`().trim()
                val label = opt.text().trim()
                if (value.isNotEmpty()) {
                    yearOptions.add(YearOption(value = value, label = label))
                }
            }
        }

        val scores = mutableListOf<ScoreItem>()
        val table = doc.selectFirst("table.datalist")
        if (table != null) {
            val trList = table.select("tr")
            if (trList.size > 1) {
                val ths = trList[0].select("th")
                val headers = ths.map { it.text().trim() }

                val colYear = headers.indexOfFirst { it == "学年" }
                val colTerm = headers.indexOfFirst { it == "学期" }
                val colCode = headers.indexOfFirst { it == "课程号" }
                val colName = headers.indexOfFirst { it == "课程名" }
                val colSeq = headers.indexOfFirst { it == "课序号" }
                val colGroup = headers.indexOfFirst { it == "课组" }
                val colTotal = headers.indexOfFirst { it == "总评" } // 精确匹配总评
                val colCredit = headers.indexOfFirst { it == "学分" }
                val colHours = headers.indexOfFirst { it == "学时" }
                val colProp = headers.indexOfFirst { it == "选课属性" }
                val colRemark = headers.indexOfFirst { it == "备注" }
                val colExamType = headers.indexOfFirst { it == "考试性质" }
                val colPassMark = headers.indexOfFirst { it == "及格标志" }

                for (i in 1 until trList.size) {
                    val tds = trList[i].select("td")
                    if (tds.isEmpty()) continue

                    fun getTd(index: Int): String = if (index >= 0 && index < tds.size) tds[index].text().trim() else ""

                    val year = getTd(colYear)
                    val term = getTd(colTerm)
                    val courseId = getTd(colCode)
                    val courseName = getTd(colName)
                    val courseSeq = getTd(colSeq)
                    val courseGroup = getTd(colGroup)
                    val rawScore = getTd(colTotal)
                    val credit = getTd(colCredit).toDoubleOrNull() ?: 0.0
                    val hours = getTd(colHours).toIntOrNull() ?: 0
                    val property = getTd(colProp)
                    val remark = getTd(colRemark)
                    val examType = getTd(colExamType)
                    val passMark = getTd(colPassMark)

                    val numScore = rawScore.toDoubleOrNull()
                    val passed = when {
                        numScore != null -> numScore >= 60.0
                        passMark.isNotEmpty() -> !passMark.contains("不") && passMark != "不及格"
                        rawScore.isNotEmpty() -> rawScore != "不及格" && rawScore != "不合格"
                        else -> true
                    }

                    scores.add(
                        ScoreItem(
                            year = year,
                            term = term,
                            courseId = courseId,
                            courseName = courseName,
                            courseSeq = courseSeq,
                            courseGroup = courseGroup,
                            score = rawScore,
                            credit = credit,
                            hours = hours,
                            property = property,
                            remark = remark,
                            examType = examType,
                            passMark = passMark,
                            passed = passed
                        )
                    )
                }
            }
        }

        return ScoreResult(scores = scores, yearOptions = yearOptions)
    }

    fun calculateGpaStats(scores: List<ScoreItem>): ScoreStats {
        var totalCredits = 0.0
        var earnedCredits = 0.0
        var totalScoreWeight = 0.0
        var totalGpaWeight = 0.0
        var failedCount = 0
        var excCount = 0

        for (item in scores) {
            val cr = item.credit
            totalCredits += cr
            if (item.passed) {
                earnedCredits += cr
            } else {
                failedCount++
            }

            val num = item.score.toDoubleOrNull()
            if (num != null) {
                totalScoreWeight += num * cr
                if (num >= 90.0) excCount++
                val gp = if (num >= 60.0) min(4.0, (num - 50.0) / 10.0) else 0.0
                totalGpaWeight += gp * cr
            } else {
                // 等级制折算
                val gp = when (item.score) {
                    "优秀", "优" -> 4.0
                    "良好", "良" -> 3.5
                    "中等", "中" -> 2.5
                    "及格" -> 1.5
                    else -> 0.0
                }
                totalGpaWeight += gp * cr
                val estimatedScore = when (item.score) {
                    "优秀", "优" -> 95.0
                    "良好", "良" -> 85.0
                    "中等", "中" -> 75.0
                    "及格" -> 65.0
                    else -> 0.0
                }
                if (estimatedScore > 0) {
                    totalScoreWeight += estimatedScore * cr
                    if (estimatedScore >= 90.0) excCount++
                }
            }
        }

        val gpa = if (totalCredits > 0) totalGpaWeight / totalCredits else 0.0
        val weightedAvg = if (totalCredits > 0) totalScoreWeight / totalCredits else 0.0
        val excellentRate = if (scores.isNotEmpty()) (excCount.toDouble() / scores.size.toDouble()) * 100.0 else 0.0

        return ScoreStats(
            gpa = Math.round(gpa * 100.0) / 100.0,
            weightedAvg = Math.round(weightedAvg * 10.0) / 10.0,
            totalCredits = totalCredits,
            earnedCredits = earnedCredits,
            failedCount = failedCount,
            excellentRate = Math.round(excellentRate * 10.0) / 10.0
        )
    }

    fun parseExams(html: String): List<ExamItem> {
        val cleanHtml = stripHtmlComments(html)
        val doc: Document = Jsoup.parse(cleanHtml)
        val exams = mutableListOf<ExamItem>()

        val table = doc.selectFirst("table.datalist") ?: doc.selectFirst("table.infolist_tab")
        if (table != null) {
            val trList = table.select("tr")
            if (trList.size > 1) {
                val ths = trList[0].select("th")
                val headers = ths.map { it.text().trim() }

                val colCode = headers.indexOfFirst { it.contains("课程号") }
                val colName = headers.indexOfFirst { it.contains("课程名称") }
                val colTime = headers.indexOfFirst { it.contains("考试时间") }
                val colLoc = headers.indexOfFirst { it.contains("考试地点") }
                val colProp = headers.indexOfFirst { it.contains("考试性质") }

                val today = LocalDate.now()
                val dateRegex = Regex("""(\d{4}-\d{2}-\d{2})""")

                for (i in 1 until trList.size) {
                    val tds = trList[i].select("td")
                    if (tds.size < 4) continue

                    val courseId = (if (colCode >= 0 && colCode < tds.size) tds[colCode].text() else tds[0].text()).trim()
                    val courseName = (if (colName >= 0 && colName < tds.size) tds[colName].text() else tds[1].text()).trim()
                    val time = (if (colTime >= 0 && colTime < tds.size) tds[colTime].text() else tds[2].text()).trim()
                    val location = (if (colLoc >= 0 && colLoc < tds.size) tds[colLoc].text() else tds[3].text()).trim()
                    val property = (if (colProp >= 0 && colProp < tds.size) tds[colProp].text() else if (tds.size > 4) tds[4].text() else "正常考试").trim()

                    if (courseName.isEmpty() && courseId.isEmpty()) continue

                    var countdownDays: Int? = null
                    var isUpcoming = true
                    val dateMatch = dateRegex.find(time)
                    if (dateMatch != null) {
                        try {
                            val examDate = LocalDate.parse(dateMatch.value, DateTimeFormatter.ISO_LOCAL_DATE)
                            val diff = ChronoUnit.DAYS.between(today, examDate).toInt()
                            countdownDays = diff
                            isUpcoming = diff >= 0
                        } catch (_: Exception) {}
                    }

                    exams.add(
                        ExamItem(
                            courseId = courseId,
                            courseName = courseName,
                            time = time,
                            location = location,
                            property = property,
                            countdownDays = countdownDays,
                            isUpcoming = isUpcoming
                        )
                    )
                }
            }
        }

        return exams
    }

    fun parseCurriculumPlan(html: String): CurriculumPlanResult {
        val doc: Document = Jsoup.parse(html)
        val groups = mutableListOf<CurriculumGroup>()

        val select = doc.selectFirst("select#syt12") ?: doc.selectFirst("select[name=syt]")
        if (select != null) {
            val options = select.select("option")
            for (opt in options) {
                val text = opt.text().trim()
                val m = Regex("""^(.*?)\s+选课属性：(.*?)?\s*学分要求=([\d.]+)\s*门数要求=(\d+)""").find(text)
                if (m != null) {
                    groups.add(
                        CurriculumGroup(
                            id = opt.`val`(),
                            name = m.groupValues[1].trim(),
                            property = m.groupValues[2].trim().ifEmpty { "必修" },
                            requiredCredits = m.groupValues[3].toDoubleOrNull() ?: 0.0,
                            requiredCourses = m.groupValues[4].toIntOrNull() ?: 0,
                            courses = mutableListOf()
                        )
                    )
                }
            }
        }

        val detailTable = doc.selectFirst("table#output_ctx") ?: doc.selectFirst("table.output_ctx")
        if (detailTable != null) {
            val rows = detailTable.select("tr")
            var currentGroupName = ""

            for (tr in rows) {
                val th = tr.selectFirst("th")
                if (th != null && th.text().contains("课组")) {
                    currentGroupName = th.text().replace(Regex(".*课组[：:]\\s*"), "").trim()
                }

                val tds = tr.select("td")
                if (tds.size >= 7) {
                    val cCode = tds[1].text().trim()
                    val cName = tds[2].text().trim()
                    val cCredit = tds[3].text().trim().toDoubleOrNull() ?: 0.0
                    val cHours = tds[4].text().trim().toIntOrNull() ?: 0
                    val cProp = tds[5].text().trim()

                    if (cCode.isNotEmpty() && cName.isNotEmpty()) {
                        val group = groups.find { it.name == currentGroupName } ?: groups.firstOrNull()
                        if (group != null) {
                            (group.courses as? MutableList<PlanCourseDetail>)?.add(
                                PlanCourseDetail(
                                    code = cCode,
                                    name = cName,
                                    credit = cCredit,
                                    hours = cHours,
                                    property = cProp
                                )
                            )
                        }
                    }
                }
            }
        }

        return CurriculumPlanResult(groups = groups)
    }

    fun parseClassroomQueryOptions(html: String): ClassroomQueryOptions {
        val doc: Document = Jsoup.parse(html)
        val areas = mutableListOf<NamedOption>()
        val buildings = mutableListOf<NamedOption>()
        val rooms = mutableListOf<NamedOption>()

        doc.select("select[name=aid] option").forEach { opt ->
            val v = opt.`val`()
            if (v.isNotEmpty() && v != "-1") areas.add(NamedOption(v, opt.text().trim()))
        }
        doc.select("select[name=buildingid] option").forEach { opt ->
            val v = opt.`val`()
            if (v.isNotEmpty() && v != "-1") buildings.add(NamedOption(v, opt.text().trim()))
        }
        doc.select("select[name=room] option").forEach { opt ->
            val v = opt.`val`()
            if (v.isNotEmpty() && v != "-1") rooms.add(NamedOption(v, opt.text().trim()))
        }

        return ClassroomQueryOptions(areas = areas, buildings = buildings, rooms = rooms)
    }

    fun parseCourseList(html: String): List<CourseSearchItem> {
        val doc: Document = Jsoup.parse(html)
        val list = mutableListOf<CourseSearchItem>()

        val table = doc.selectFirst("table.datalist") ?: doc.selectFirst("table.infolist_tab")
        if (table != null) {
            val rows = table.select("tr")
            for (i in 1 until rows.size) {
                val tds = rows[i].select("td")
                if (tds.size >= 4) {
                    list.add(
                        CourseSearchItem(
                            courseId = tds[0].text().trim(),
                            courseName = tds[1].text().trim(),
                            credit = tds.getOrNull(2)?.text()?.trim()?.toDoubleOrNull() ?: 0.0,
                            hours = tds.getOrNull(3)?.text()?.trim()?.toIntOrNull() ?: 0,
                            department = tds.getOrNull(4)?.text()?.trim() ?: "",
                            examWay = tds.getOrNull(5)?.text()?.trim() ?: ""
                        )
                    )
                }
            }
        }
        return list
    }

    fun parseCalendarInfo(html: String): CalendarInfo {
        val doc: Document = Jsoup.parse(html)
        var currentWeek = 1
        val curTd = doc.selectFirst(".week td.cur span") ?: doc.selectFirst(".curweek strong")
        if (curTd != null) {
            val num = curTd.text().trim().toIntOrNull()
            if (num != null) currentWeek = num
        }

        var semesterName = ""
        val curWeekDiv = doc.selectFirst(".curweek")
        if (curWeekDiv != null) {
            semesterName = curWeekDiv.text().replace(Regex("""\s+"""), " ").replace(Regex("第.*周"), "").trim()
        }

        val notices = mutableListOf<NoticeItem>()
        val textWrapper = doc.selectFirst("#textwrapper")
        if (textWrapper != null) {
            val items = textWrapper.select("p, div, li")
            for ((idx, el) in items.withIndex()) {
                val text = el.text().trim()
                if (text.isNotEmpty()) {
                    notices.add(
                        NoticeItem(
                            id = "notice_$idx",
                            title = if (text.length > 50) text.substring(0, 50) else text,
                            content = text,
                            date = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
                        )
                    )
                }
            }
        }

        return CalendarInfo(
            currentWeek = currentWeek,
            semesterName = semesterName,
            notices = notices
        )
    }

    fun parseTeachingWeek(html: String): Int {
        val match = Regex("""第\s*(\d+)\s*周""").find(html)
        if (match != null) {
            return match.groupValues[1].toIntOrNull() ?: 1
        }
        val doc = Jsoup.parse(html)
        val curTd = doc.selectFirst(".week td.cur span") ?: doc.selectFirst("#date p span")
        return curTd?.text()?.trim()?.toIntOrNull() ?: 1
    }
}
