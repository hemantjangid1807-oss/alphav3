package com.alphasim.brain.data.api

import android.content.Context
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/** Keeps the BRAIN session cookie across app restarts. */
class PersistentCookieJar(context: Context) : CookieJar {
    private val prefs = context.applicationContext.getSharedPreferences("cookies", Context.MODE_PRIVATE)
    private val lock = Any()
    private val cookies = mutableMapOf<String, Cookie>()

    init {
        val base = BrainApi.BASE.toHttpUrl()
        prefs.all.forEach { (k, v) ->
            val c = (v as? String)?.let { Cookie.parse(base, it) }
            if (c != null && c.expiresAt > System.currentTimeMillis()) cookies[k] = c
        }
    }

    private fun keyOf(c: Cookie) = "${c.domain}|${c.path}|${c.name}"

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        synchronized(lock) {
            val edit = prefs.edit()
            for (c in cookies) {
                val key = keyOf(c)
                if (c.expiresAt <= System.currentTimeMillis()) {
                    this.cookies.remove(key)
                    edit.remove(key)
                } else {
                    this.cookies[key] = c
                    if (c.persistent) edit.putString(key, c.toString()) else edit.remove(key)
                }
            }
            edit.apply()
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> = synchronized(lock) {
        val now = System.currentTimeMillis()
        cookies.values.removeAll { it.expiresAt <= now }
        cookies.values.filter { it.matches(url) }
    }

    fun hasSession(): Boolean = synchronized(lock) { cookies.isNotEmpty() }

    fun clear() = synchronized(lock) {
        cookies.clear()
        prefs.edit().clear().apply()
    }
}
