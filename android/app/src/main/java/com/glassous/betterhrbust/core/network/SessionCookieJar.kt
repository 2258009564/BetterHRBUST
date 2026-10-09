package com.glassous.betterhrbust.core.network

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.util.concurrent.ConcurrentHashMap

class SessionCookieJar : CookieJar {
    private data class Key(val name: String, val domain: String, val path: String)
    private val cookieStore = ConcurrentHashMap<Key, Cookie>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        for (cookie in cookies) {
            val key = Key(cookie.name, cookie.domain, cookie.path)
            if (cookie.expiresAt <= System.currentTimeMillis()) cookieStore.remove(key)
            else cookieStore[key] = cookie
        }
    }

    private fun validCookies(): List<Cookie> {
        val now = System.currentTimeMillis()
        cookieStore.forEach { (key, cookie) ->
            if (cookie.expiresAt <= now) cookieStore.remove(key, cookie)
        }
        return cookieStore.values.filter { it.expiresAt > now }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> =
        validCookies().filter { it.matches(url) }.sortedByDescending { it.path.length }

    fun clear() = cookieStore.clear()

    fun hasSession(): Boolean = getJSessionId() != null

    fun getJSessionId(): String? = validCookies()
        .filter { it.name == "JSESSIONID" }
        .sortedByDescending { it.path.length }
        .firstOrNull()?.value

    fun setJSessionId(host: String, value: String) {
        val cookie = Cookie.Builder().hostOnlyDomain(host).path("/academic")
            .name("JSESSIONID").value(value).build()
        cookieStore[Key(cookie.name, cookie.domain, cookie.path)] = cookie
    }
}
