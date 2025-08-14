// api/PersistentCookieJar.kt
package com.example.adproject.api

import android.content.Context
import android.util.Log
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import org.json.JSONArray
import org.json.JSONObject

/**
 * 简易持久化 CookieJar：把 Cookie 存到 SharedPreferences，App 重启也还在
 * 注意：仅示例用途，生产可换成熟库；这里已处理过期清理/覆盖更新等
 */
class PersistentCookieJar(ctx: Context) : CookieJar {
    private val sp = ctx.getSharedPreferences("cookie_store", Context.MODE_PRIVATE)
    private val KEY = "cookies"

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val all = loadAll().toMutableList()
        // 先删掉同名（domain+path+name 相同）的旧 cookie，再加新
        val toAdd = cookies.filter { it.name.isNotBlank() }
        val filtered = all.filterNot { old ->
            toAdd.any { it.name == old.name && it.domain == old.domain && it.path == old.path }
        }
        val merged = (filtered + toAdd).filter { !itHasExpired(it) }
        saveAll(merged)
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        val list = loadAll().filter { c ->
            !itHasExpired(c) &&
                    HttpUrl.Builder().scheme(url.scheme).host(c.domain.removePrefix("."))
                        .build().host.endsWith(c.domain.removePrefix(".")) &&
                    url.encodedPath.startsWith(c.path)
        }
        // 顺手把过期的清掉
        saveAll(list)
        return list
    }

    fun clear() = saveAll(emptyList())

    private fun itHasExpired(c: Cookie) = c.expiresAt < System.currentTimeMillis()

    private fun saveAll(list: List<Cookie>) {
        val arr = JSONArray()
        list.forEach { c ->
            val o = JSONObject().apply {
                put("name", c.name); put("value", c.value)
                put("expiresAt", c.expiresAt)
                put("domain", c.domain); put("path", c.path)
                put("secure", c.secure); put("httpOnly", c.httpOnly)
                put("hostOnly", c.hostOnly)
            }
            arr.put(o)
        }
        sp.edit().putString(KEY, arr.toString()).apply()
    }

    private fun loadAll(): List<Cookie> {
        val s = sp.getString(KEY, "[]") ?: "[]"
        return try {
            val arr = JSONArray(s)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                val builder = Cookie.Builder()
                    .name(o.getString("name"))
                    .value(o.getString("value"))
                    .path(o.getString("path"))

                val domain = o.getString("domain")
                if (o.optBoolean("hostOnly", false)) builder.hostOnlyDomain(domain) else builder.domain(domain)
                if (o.optBoolean("secure", false)) builder.secure()
                if (o.optBoolean("httpOnly", false)) builder.httpOnly()

                builder.expiresAt(o.getLong("expiresAt")).build()
            }
        } catch (e: Exception) {
            Log.w("PersistentCookieJar", "parse cookies failed: ${e.message}")
            emptyList()
        }
    }
}
