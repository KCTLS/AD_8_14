package com.example.adproject

import android.content.Context
import com.example.adproject.api.ApiClient

/**
 * 统一的登录态读写工具：
 * - 保存：id / name / email / （可选）token
 * - 判断是否已登录：用布尔标记 + email/token 兜底
 * - 清理：清 SharedPreferences、清 Cookie、清全局 Authorization
 */
object UserSession {
    private const val PREF       = "user_session"
    private const val KEY_ID     = "user_id"
    private const val KEY_NAME   = "user_name"
    private const val KEY_EMAIL  = "user_email"
    private const val KEY_TOKEN  = "auth_token"
    private const val KEY_LOGGED = "logged_in"

    fun save(ctx: Context, userId: Int, userName: String, email: String, token: String? = null) {
        val e = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putInt(KEY_ID, userId)
            .putString(KEY_NAME, userName)
            .putString(KEY_EMAIL, email)
            .putBoolean(KEY_LOGGED, true)
        if (!token.isNullOrBlank()) e.putString(KEY_TOKEN, token)
        e.apply()

        // 如果后端走 JWT，这里把全局 Authorization 带上；不用 JWT 就置空即可
        ApiClient.updateAuthToken(token)
    }

    fun clear(ctx: Context) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().clear().apply()
        // 会话型登录（Cookie）也顺便清掉
        try { ApiClient.clearCookies() } catch (_: Exception) {}
        ApiClient.updateAuthToken(null)
    }

    fun id(ctx: Context): Int? {
        val v = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getInt(KEY_ID, -1)
        return if (v == -1) null else v
    }

    fun name(ctx: Context): String? =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_NAME, null)

    fun email(ctx: Context): String? =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_EMAIL, null)

    fun token(ctx: Context): String? =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_TOKEN, null)

    fun isLoggedIn(ctx: Context): Boolean {
        val sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val flag = sp.getBoolean(KEY_LOGGED, false)
        val hasEmail = !sp.getString(KEY_EMAIL, null).isNullOrBlank()
        val hasToken = !sp.getString(KEY_TOKEN, null).isNullOrBlank()
        return flag || hasEmail || hasToken
    }
}
