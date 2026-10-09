package com.glassous.betterhrbust.data.repository

import com.glassous.betterhrbust.core.network.AcademicHttpClient
import com.glassous.betterhrbust.core.network.CharsetDecoderHelper
import org.jsoup.Jsoup
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.Charset

data class EvaluationTask(val key: String, val teacher: String, val course: String, val status: String, val pending: Boolean, val completed: Boolean, val url: String)
data class EvaluationOption(val value: String, val label: String)
data class EvaluationQuestion(val name: String, val title: String, val options: List<EvaluationOption>)
data class EvaluationComment(val name: String, val title: String, val maxLength: Int)
data class EvaluationForm(val url: String, val action: String, val questions: List<EvaluationQuestion>, val comments: List<EvaluationComment>, val fields: List<Pair<String, String>>) {
    fun signature() = questions.map { it.title to it.options.map { option -> option.label } } to comments.map { it.title }
    fun body(ratings: List<Int>, text: List<String>): String {
        require(ratings.size == questions.size && text.size == comments.size) { "配置与问卷项目数量不一致" }
        val fields = fields.toMutableList()
        questions.forEachIndexed { index, question ->
            val option = question.options.getOrNull(ratings[index]) ?: error("请为每项评价选择选项")
            fields.add(question.name to option.value)
        }
        comments.forEachIndexed { index, comment ->
            val value = text[index].trim()
            require(value.isNotEmpty()) { "请填写每项文字评价" }
            require(comment.maxLength < 0 || value.length <= comment.maxLength) { "评语超过问卷长度限制" }
            fields.add(comment.name to value.replace(Regex("\\r?\\n"), "\r\n"))
        }
        val encoder = Charset.forName("GBK").newEncoder()
        return fields.joinToString("&") { (name, value) ->
            require(encoder.canEncode(name) && encoder.canEncode(value)) { "评语含无法按 GBK 编码的字符" }
            "${URLEncoder.encode(name, "GBK")}=${URLEncoder.encode(value, "GBK")}" }
    }
}

class EvaluationRepository(private val client: AcademicHttpClient) {
    companion object {
        private const val BASE = "http://jwzx.hrbust.edu.cn/academic/"
        fun evaluationUrl(value: String, page: String): String {
            val url = URI(BASE).resolve(page).resolve(value)
            require(url.host == "jwzx.hrbust.edu.cn" && url.userInfo == null &&
                url.scheme in listOf("http", "https") && url.port in listOf(-1, 80, 443) &&
                url.path.startsWith("/academic/eva/")) { "评价地址不属于学校教学评价模块" }
            return "http://jwzx.hrbust.edu.cn${url.rawPath}${url.rawQuery?.let { "?$it" }.orEmpty()}"
        }
        fun parseTasks(html: String): List<EvaluationTask> {
            val table = Jsoup.parse(html).selectFirst("table.infolist_tab") ?: error("学校未返回评价列表")
            val occurrences = mutableMapOf<String, Int>()
            return table.select("tr").mapNotNull { row ->
                val cells = row.select("td")
                if (cells.size < 4) return@mapNotNull null
                val teacher = cells[0].text(); val course = cells[1].text(); val status = cells[2].text()
                if (teacher.isBlank() || course.isBlank()) return@mapNotNull null
                val identity = "$teacher|$course"
                val number = occurrences.getOrDefault(identity, 0); occurrences[identity] = number + 1
                val pending = Regex("未评估|未评价|未完成").containsMatchIn(status)
                val href = cells[3].selectFirst("a[href]")?.attr("href").orEmpty()
                EvaluationTask("$identity|$number", teacher, course, status, pending,
                    Regex("已评估|已评价|已完成").containsMatchIn(status),
                    if (pending && href.isNotBlank()) evaluationUrl(href, "eva/index/resultlist.jsdo") else "")
            }
        }
        fun parseForm(html: String, page: String): EvaluationForm {
            val document = Jsoup.parse(html)
            val form = document.select("form").firstOrNull { it.select("input[type=radio]").isNotEmpty() } ?: error("未取得评价问卷")
            require(form.attr("method").equals("post", true)) { "问卷提交方式不受支持" }
            require(form.attr("enctype").ifBlank { "application/x-www-form-urlencoded" } == "application/x-www-form-urlencoded") { "问卷编码不受支持" }
            require(form.select("input[type=file],input[name*=captcha]").isEmpty()) { "问卷含附件或验证码，请在原版页面完成" }
            fun title(element: org.jsoup.nodes.Element, fallback: String): String =
                element.closest("tr")?.select("td,th")?.firstOrNull { it.select("input,textarea,select").isEmpty() &&
                    it.text().isNotBlank() && !it.text().matches(Regex("\\d+[.、]?")) }?.text() ?: fallback
            val questions = form.select("input[type=radio][name]").filter { !it.hasAttr("disabled") }.groupBy { it.attr("name") }.map { (name, inputs) ->
                EvaluationQuestion(name, title(inputs.first(), name), inputs.map { input ->
                    val label = document.select("label[for]").firstOrNull { it.attr("for") == input.id() }?.text()
                        ?: input.closest("label")?.text()
                        ?: input.nextSibling()?.toString()?.let { Jsoup.parse(it).text() }
                        ?: "选项 ${input.attr("value")}" 
                    EvaluationOption(input.attr("value"), label)
                })
            }
            require(questions.isNotEmpty()) { "问卷中没有评价选项" }
            val comments = form.select("textarea[name]").filter { !it.hasAttr("disabled") }.map {
                EvaluationComment(it.attr("name"), title(it, it.attr("name")), it.attr("maxlength").toIntOrNull() ?: -1)
            }
            val excluded = (questions.map { it.name } + comments.map { it.name }).toSet()
            val fields = form.select("input[name],select[name],textarea[name]").filter {
                it.attr("name") !in excluded && !it.hasAttr("disabled") && it.attr("type") !in listOf("submit", "button", "reset", "file") &&
                    (it.attr("type") !in listOf("radio", "checkbox") || it.hasAttr("checked"))
            }.flatMap { element ->
                if (element.tagName() == "select") {
                    val options = element.select("option[selected]").ifEmpty { element.select("option").take(1) }
                    options.map { element.attr("name") to it.attr("value") }
                } else listOf(element.attr("name") to if (element.tagName() == "textarea") element.text() else element.attr("value"))
            }
            return EvaluationForm(evaluationUrl(page, page), evaluationUrl(form.attr("action").ifBlank { page }, page), questions, comments, fields)
        }
    }
    suspend fun tasks(): List<EvaluationTask> = parseTasks(client.get("eva/index/resultlist.jsdo?_t=${System.currentTimeMillis()}", CharsetDecoderHelper.GBK))
    suspend fun form(task: EvaluationTask): EvaluationForm = parseForm(client.get(task.url, CharsetDecoderHelper.GBK), task.url)
    suspend fun submit(tasks: List<EvaluationTask>, template: EvaluationForm, ratings: List<Int>, comments: List<String>, stopped: () -> Boolean, progress: (String) -> Unit): Int {
        template.body(ratings, comments)
        var count = 0
        for (task in tasks) {
            if (stopped()) break
            fun unique(items: List<EvaluationTask>): EvaluationTask? {
                val matches = items.filter { it.teacher == task.teacher && it.course == task.course }
                require(matches.size <= 1) { "同一教师和课程有多条评价，无法可靠核对" }
                return matches.singleOrNull()
            }
            val before = unique(this.tasks()) ?: error("学校评价列表发生变化")
            if (before.completed) { count++; continue }
            require(before.pending && before.url.isNotBlank()) { "课程已不处于待评价状态" }
            val current = form(before)
            require(current.signature() == template.signature()) { "该课程问卷结构不同，请单独配置" }
            if (stopped()) break
            progress("${task.course}：正在提交")
            var failure: Exception? = null
            try { client.postEncoded(current.action, current.body(ratings, comments), current.url) }
            catch (error: kotlinx.coroutines.CancellationException) { throw error }
            catch (error: Exception) { failure = error }
            require(unique(this.tasks())?.completed == true) { failure?.message ?: "学校未确认评价完成，已停止，勿重复提交" }
            count++; progress("${task.course}：已核对完成")
        }
        return count
    }
}
