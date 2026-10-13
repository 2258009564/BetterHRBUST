package com.glassous.betterhrbust.data.repository

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/** 提交成功回执（对应后端响应信封的 data 字段）。 */
data class FeedbackReceipt(
    val id: String,
    val createdAt: String
)

/**
 * 意见反馈仓库（数据源：BetterHRBUST Go 服务，与 Web 端 `services/feedback.js` 同口径）。
 *
 * 隐私红线：请求体只包含 content / contact / signature / anonymous 四个字段，
 * 绝不携带学号、教务内部 ID、Cookie、成绩、课表或设备信息；
 * 日志只记录 HTTP 状态码，不打印请求体与服务端 message，避免用户文本落盘。
 *
 * 使用独立的 OkHttpClient：不携带教务 Cookie / Referer，避免污染教务会话链路。
 */
class FeedbackRepository(
    /** 后端服务基址（Web/Admin 端 VITE_API_BASE 指向的同一 Go 服务） */
    private val baseUrl: String = BASE_URL
) {
    companion object {
        private const val TAG = "FeedbackRepository"

        /** 后端服务地址（与 Web/Admin 端同源反代到 /api 的 Go 服务）。 */
        const val BASE_URL = "https://bthadmin.vodyanitsa.top"

        private const val SUBMIT_PATH = "/api/feedback"

        /**
         * 反馈正文与附属字段的长度限制，与 Web 端 FEEDBACK_LIMITS
         * 及服务端 service.FeedbackSubmitInput 校验保持一致。
         */
        const val MIN_CONTENT = 5
        const val MAX_CONTENT = 2000
        const val MAX_CONTACT = 128
        const val MAX_SIGNATURE = 64

        /** 提交为幂等的一次性写操作，不做自动重试，避免重复落库。 */
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    /** 请求体：只承载用户主动填写的文本。 */
    @Serializable
    private data class SubmitBody(
        val content: String,
        val contact: String,
        val signature: String,
        val anonymous: Boolean
    )

    /** 服务端统一响应信封：`{ code, message, data }`，code == 0 表示成功。 */
    @Serializable
    private data class Envelope(
        val code: Int,
        val message: String = "",
        val data: Receipt? = null
    )

    @Serializable
    private data class Receipt(
        val id: String = "",
        val createdAt: String = ""
    )

    /**
     * 提交一条反馈。
     *
     * @param content 反馈正文（5 - 2000 字，服务端二次校验）
     * @param contact 选填联系方式（≤128 字）
     * @param signature 实名发布时随本条反馈提交的姓名文本；匿名时传空串
     * @param anonymous 是否匿名提交（实名开关关闭时应为 true）
     * @return 成功时携带 [FeedbackReceipt]；失败时 [Result.exceptionOrNull] 的 message
     *         为可直接展示给用户的文案（服务端 message 或网络错误提示）。
     */
    suspend fun submit(
        content: String,
        contact: String = "",
        signature: String = "",
        anonymous: Boolean = true
    ): Result<FeedbackReceipt> = withContext(Dispatchers.IO) {
        val payload = json.encodeToString(
            SubmitBody(
                content = content,
                contact = contact,
                signature = signature,
                anonymous = anonymous
            )
        )

        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}$SUBMIT_PATH")
            .header("Accept", "application/json")
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                val envelope = runCatching { json.decodeFromString<Envelope>(body) }.getOrNull()

                when {
                    // 非 JSON 响应（网关错误页等）：不透出响应体，只给通用文案
                    envelope == null -> {
                        Log.w(TAG, "反馈提交失败：HTTP ${response.code}，响应无法解析")
                        Result.failure(IOException("提交失败（HTTP ${response.code}），请稍后重试"))
                    }

                    envelope.code != 0 -> {
                        Log.w(TAG, "反馈提交被拒绝：业务码 ${envelope.code}（HTTP ${response.code}）")
                        Result.failure(
                            IOException(envelope.message.ifBlank { "提交失败，请稍后重试" })
                        )
                    }

                    else -> Result.success(
                        FeedbackReceipt(
                            id = envelope.data?.id.orEmpty(),
                            createdAt = envelope.data?.createdAt.orEmpty()
                        )
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Log.w(TAG, "反馈提交网络失败：${e.javaClass.simpleName}")
            Result.failure(IOException("网络不可用，请检查连接后重试"))
        } catch (e: Exception) {
            Log.w(TAG, "反馈提交异常：${e.javaClass.simpleName}")
            Result.failure(IOException("提交失败，请稍后重试"))
        }
    }
}
