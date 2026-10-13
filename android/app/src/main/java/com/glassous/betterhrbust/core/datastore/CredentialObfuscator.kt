package com.glassous.betterhrbust.core.datastore

import java.util.Base64

/**
 * 「记住密码」本地凭证的轻量混淆，算法与密钥同 Web 端
 * （web/src/services/savedCredential.js）：XOR + Base64。
 *
 * 只用于避免密码在 DataStore 文件中以明文裸露，不承担真实加密职责 ——
 * 密钥内置在代码里，能读取应用私有目录的设备（如 root）仍可还原。
 * 取消登录页的「记住密码」勾选即可立即清除。
 */
internal object CredentialObfuscator {
    private const val MASK = "better-hrbust-local-credential-v1"

    fun obfuscate(text: String): String =
        Base64.getEncoder().encodeToString(mask(text.toByteArray(Charsets.UTF_8)))

    /** 解密失败（数据损坏 / 非法 Base64）时返回 null，不影响登录 */
    fun deobfuscate(cipher: String): String? = try {
        mask(Base64.getDecoder().decode(cipher)).toString(Charsets.UTF_8)
    } catch (e: IllegalArgumentException) {
        null
    }

    /** 对称异或：加密与解密共用同一实现 */
    private fun mask(bytes: ByteArray): ByteArray =
        ByteArray(bytes.size) { index -> (bytes[index].toInt() xor MASK[index % MASK.length].code).toByte() }
}
