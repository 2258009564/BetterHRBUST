package com.glassous.betterhrbust.core.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

class AcademicHttpClient(
    val cookieJar: SessionCookieJar = SessionCookieJar(),
    private val baseUrl: String = BASE_URL
) {
    companion object {
        const val BASE_URL = "http://jwzx.hrbust.edu.cn/academic/"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .cookieJar(cookieJar)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .addInterceptor(Interceptor { chain ->
            val original = chain.request()
            val requestBuilder = original.newBuilder()
                .header("User-Agent", USER_AGENT)
                .header("Referer", original.header("Referer") ?: baseUrl)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                .header("Accept-Language", "zh-CN,zh;q=0.9")
            chain.proceed(requestBuilder.build())
        })
        .build()

    private fun resolveUrl(path: String): String {
        val base = baseUrl.toHttpUrl()
        val url = if (path.startsWith("http://") || path.startsWith("https://")) path.toHttpUrl()
            else base.resolve(path.removePrefix("/academic/")) ?: throw IOException("教务地址无效")
        if (url.host != base.host || url.username.isNotEmpty() || url.password.isNotEmpty() ||
            !url.encodedPath.startsWith(base.encodedPath)) throw IOException("拒绝请求非同源教务地址")
        return url.newBuilder().scheme(base.scheme).port(base.port).build().toString()
    }

    private fun executeAcademic(request: Request, preferredCharset: Charset?): String {
        var current = request
        repeat(6) {
            val response = client.newCall(current).execute()
            try {
                if (response.code in listOf(301, 302, 303, 307, 308)) {
                    if (current.method == "POST" && response.code in listOf(307, 308)) {
                        throw IOException("教务要求重新提交表单，已停止以避免重复提交")
                    }
                    if (current.method == "POST" && current.url.encodedPath.startsWith("/academic/eva/")) {
                        return "" // 评价提交后由调用方重新读取列表核对，302 不代表完成。
                    }
                    val location = response.header("Location") ?: throw IOException("教务跳转缺少目标地址")
                    val target = current.url.resolve(location) ?: throw IOException("教务跳转地址无效")
                    current = current.newBuilder().url(resolveUrl(target.toString())).get().build()
                } else {
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code}: ${response.message}")
                    val bytes = response.body?.bytes() ?: throw IOException("教务响应为空")
                    val html = CharsetDecoderHelper.decode(bytes, response.header("Content-Type"), preferredCharset)
                    checkSessionExpiration(current.url.toString(), html)
                    return html
                }
            } finally {
                response.close()
            }
        }
        throw IOException("教务重定向次数过多")
    }

    var onSessionExpired: (() -> Unit)? = null

    private fun checkSessionExpiration(url: String, html: String) {
        val isAuthEndpoint = url.contains("login") || url.contains("getCaptcha") || url.contains("j_acegi_security_check")
        if (!isAuthEndpoint) {
            if (com.glassous.betterhrbust.core.parser.AcademicParsers.isLoginPage(html)) {
                onSessionExpired?.invoke()
                throw SessionExpiredException("会话已过期，请重新登录")
            }
        }
    }

    suspend fun get(
        path: String,
        preferredCharset: Charset? = null,
        headers: Map<String, String> = emptyMap()
    ): String = withContext(Dispatchers.IO) {
        val fullUrl = resolveUrl(path)
        val requestBuilder = Request.Builder().url(fullUrl)
        for ((k, v) in headers) {
            requestBuilder.header(k, v)
        }
        val request = requestBuilder.build()
        executeAcademic(request, preferredCharset)
    }

    suspend fun post(
        path: String,
        formBody: Map<String, String>,
        preferredCharset: Charset? = null,
        encodeFormWithGbk: Boolean = false,
        headers: Map<String, String> = emptyMap()
    ): String = withContext(Dispatchers.IO) {
        val fullUrl = resolveUrl(path)
        val requestBuilder = Request.Builder().url(fullUrl)
        for ((k, v) in headers) {
            requestBuilder.header(k, v)
        }

        val body = if (encodeFormWithGbk) {
            val encodedQuery = formBody.entries.joinToString("&") { (key, value) ->
                "${URLEncoder.encode(key, "GBK")}=${URLEncoder.encode(value, "GBK")}"
            }
            encodedQuery.toRequestBody("application/x-www-form-urlencoded; charset=GBK".toMediaType())
        } else {
            val formBuilder = FormBody.Builder()
            for ((key, value) in formBody) {
                formBuilder.add(key, value)
            }
            formBuilder.build()
        }

        requestBuilder.post(body)
        executeAcademic(requestBuilder.build(), preferredCharset)
    }

    suspend fun postEncoded(path: String, encodedBody: String, referrer: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(resolveUrl(path))
            .header("Referer", resolveUrl(referrer))
            .post(encodedBody.toRequestBody("application/x-www-form-urlencoded; charset=GBK".toMediaType()))
            .build()
        executeAcademic(request, CharsetDecoderHelper.GBK)
    }

    suspend fun downloadCaptcha(): ByteArray = withContext(Dispatchers.IO) {
        val url = resolveUrl("getCaptcha.do?_t=${System.currentTimeMillis()}")
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw IOException("获取验证码失败: HTTP ${response.code}")
        }
        response.body?.bytes() ?: throw IOException("验证码返回为空")
    }

    suspend fun checkCaptcha(code: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = resolveUrl("checkCaptcha.do?captchaCode=$code")
            val request = Request.Builder().url(url).post(FormBody.Builder().build()).build()
            val response = client.newCall(request).execute()
            val text = response.body?.string()?.trim()?.lowercase()
            text == "true"
        } catch (_: Exception) {
            true // 预校验网络异常时不阻塞主登录
        }
    }

    suspend fun login(username: String, password: String, captcha: String): String = withContext(Dispatchers.IO) {
        val form = mapOf(
            "j_username" to username,
            "j_password" to password,
            "j_captcha" to captcha
        )
        // Submit login to Acegi security check
        val html = post("j_acegi_security_check", form, preferredCharset = CharsetDecoderHelper.GBK)
        html
    }

    suspend fun logout() = withContext(Dispatchers.IO) {
        try {
            get("j_acegi_logout")
        } catch (_: Exception) {
            // ignore network failure on logout
        } finally {
            cookieJar.clear()
        }
    }
}
