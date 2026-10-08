package com.glassous.betterhrbust.core.network

import java.net.URLEncoder
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

object CharsetDecoderHelper {
    val GBK: Charset = Charset.forName("GBK")
    val UTF_8: Charset = StandardCharsets.UTF_8

    fun decode(bytes: ByteArray, contentTypeHeader: String?, preferredCharset: Charset? = null): String {
        if (preferredCharset != null) {
            val primary = String(bytes, preferredCharset)
            if (!hasExcessiveReplacements(primary, bytes.size)) {
                return primary
            }
            val fallback = if (preferredCharset == UTF_8) GBK else UTF_8
            val retry = String(bytes, fallback)
            return if (countReplacements(retry) < countReplacements(primary)) retry else primary
        }

        var detectedCharsetName: String? = null
        if (!contentTypeHeader.isNullOrBlank()) {
            val parts = contentTypeHeader.split(";")
            for (part in parts) {
                val trimmed = part.trim()
                if (trimmed.startsWith("charset=", ignoreCase = true)) {
                    detectedCharsetName = trimmed.substring("charset=".length).trim().trim('"', '\'')
                }
            }
        }

        val headerSlice = String(bytes.take(2048).toByteArray(), StandardCharsets.ISO_8859_1)
        val metaMatch = Regex("""<meta[^>]+charset=["']?([a-zA-Z0-9_\-]+)""", RegexOption.IGNORE_CASE).find(headerSlice)
        val metaCharset = metaMatch?.groupValues?.get(1)

        if (!metaCharset.isNullOrBlank()) {
            if (metaCharset.contains("utf", ignoreCase = true)) {
                detectedCharsetName = metaCharset
            } else if (detectedCharsetName.isNullOrBlank() || detectedCharsetName.equals("ISO-8859-1", ignoreCase = true)) {
                detectedCharsetName = metaCharset
            }
        }

        val primaryCharset = when {
            detectedCharsetName?.contains("utf", ignoreCase = true) == true -> UTF_8
            detectedCharsetName?.contains("gb", ignoreCase = true) == true -> GBK
            else -> GBK
        }

        val decoded = String(bytes, primaryCharset)
        if (hasExcessiveReplacements(decoded, bytes.size)) {
            val fallbackCharset = if (primaryCharset == UTF_8) GBK else UTF_8
            val retry = String(bytes, fallbackCharset)
            if (countReplacements(retry) < countReplacements(decoded)) {
                return retry
            }
        }
        return decoded
    }

    private fun countReplacements(text: String): Int = text.count { it == '\uFFFD' }

    private fun hasExcessiveReplacements(text: String, byteLength: Int): Boolean {
        if (byteLength == 0) return false
        val count = countReplacements(text)
        return count > 5 && (count.toDouble() / byteLength.toDouble()) > 0.005
    }

    fun encodeGbk(value: String): String = URLEncoder.encode(value, "GBK")
}
