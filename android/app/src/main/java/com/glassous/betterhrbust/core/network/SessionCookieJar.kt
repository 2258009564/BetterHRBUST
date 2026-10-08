package com.glassous.betterhrbust.core.network

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.util.concurrent.ConcurrentHashMap

class SessionCookieJar : CookieJar {
    private val cookieStore = ConcurrentHashMap<String, MutableMap<String, Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val host = url.host
        val hostCookies = cookieStore.computeIfAbsent(host) { ConcurrentHashMap() }
        for (cookie in cookies) {
            hostCookies[cookie.name] = cookie
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val host = url.host
        val hostCookies = cookieStore[host] ?: return emptyList()
        val validCookies = mutableListOf<Cookie>()
        val currentTime = System.currentTimeMillis()

        val iterator = hostCookies.values.iterator()
        while (iterator.hasNext()) {
            val cookie = iterator.next()
            if (cookie.expiresAt < currentTime) {
                iterator.remove()
            } else if (cookie.matches(url)) {
                validCookies.add(cookie)
            }
        }
        return validCookies
    }

    fun clear() {
        cookieStore.clear()
    }

    fun hasSession(): Boolean {
        for (map in cookieStore.values) {
            if (map.containsKey("JSESSIONID")) return true
        }
        return false
    }

    fun getJSessionId(): String? {
        for (map in cookieStore.values) {
            val cookie = map["JSESSIONID"]
            if (cookie != null) return cookie.value
        }
        return null
    }

    fun setJSessionId(host: String, value: String) {
        val cookie = Cookie.Builder()
            .domain(host)
            .path("/academic")
            .name("JSESSIONID")
            .value(value)
            .build()
        val hostCookies = cookieStore.computeIfAbsent(host) { ConcurrentHashMap() }
        hostCookies[cookie.name] = cookie
    }
}
