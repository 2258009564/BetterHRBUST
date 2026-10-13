package com.glassous.betterhrbust

import com.glassous.betterhrbust.core.datastore.CredentialObfuscator
import com.glassous.betterhrbust.core.datastore.UserPreferencesManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** 「记住密码」凭证的混淆与解析（与 Web 端 savedCredential.js 同一算法：XOR + Base64） */
class SavedCredentialTest {

    @Test
    fun obfuscationRoundTripsUtf8Passwords() {
        val samples = listOf(
            "123456",
            "p@ss w0rd!",
            "密码测试中文",
            "emoji🔐混合Chinese字符",
            "a".repeat(64)
        )
        samples.forEach { plain ->
            val cipher = CredentialObfuscator.obfuscate(plain)
            assertFalse("密文不应包含明文: $plain", cipher.contains(plain))
            assertEquals(plain, CredentialObfuscator.deobfuscate(cipher))
        }
    }

    @Test
    fun brokenCipherReturnsNull() {
        assertNull(CredentialObfuscator.deobfuscate("not-valid-base64!!!"))
    }

    @Test
    fun resolvesOnlyMatchingUsername() {
        val value = "2401234567\n" + CredentialObfuscator.obfuscate("Secret#1")
        assertEquals("Secret#1", UserPreferencesManager.resolveSavedPassword("2401234567", value))
        assertEquals("Secret#1", UserPreferencesManager.resolveSavedPassword(" 2401234567 ", value))
        assertNull("学号不匹配时不得填入", UserPreferencesManager.resolveSavedPassword("2407654321", value))
        assertNull(UserPreferencesManager.resolveSavedPassword("2401234567", ""))
        assertNull(UserPreferencesManager.resolveSavedPassword("", value))
    }

    @Test
    fun malformedCredentialReturnsNull() {
        assertNull(UserPreferencesManager.resolveSavedPassword("2401234567", "no-separator"))
        assertNull(UserPreferencesManager.resolveSavedPassword("2401234567", "2401234567\nnot-base64!!!"))
    }
}
