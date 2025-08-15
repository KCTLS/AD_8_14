package com.example.adproject

import android.content.Context
import com.example.adproject.api.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 统一的登录态读写工具：
 * - 保存：id / name / email / （可选）token
 * - 判断是否已登录：必须 logged_in == true 且（有 token 或有 Cookie）
 * - 清理：清 SharedPreferences、清 Cookie、清全局 Authorization
 * - 新增：setName / setEmail / syncNameFromServer
 */
object UserSession {
    private const val PREF       = "user_session"
    private const val KEY_ID     = "user_id"
    private const val KEY_NAME   = "user_name"
    private const val KEY_EMAIL  = "user_email"
    private const val KEY_TOKEN  = "auth_token"
    private const val KEY_LOGGED = "logged_in"

    // 便捷 SharedPreferences 访问
    private fun sp(ctx: Context) = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    /** 保存登录信息（覆盖式保存） */
    fun save(ctx: Context, userId: Int, userName: String, email: String, token: String? = null) {
        val e = sp(ctx).edit()
            .putInt(KEY_ID, userId)
            .putString(KEY_NAME, userName)
            .putString(KEY_EMAIL, email)
            .putBoolean(KEY_LOGGED, true)
        if (!token.isNullOrBlank()) e.putString(KEY_TOKEN, token)
        e.apply()

        // 如果后端走 JWT，这里把全局 Authorization 带上；不用 JWT 就置空即可
        ApiClient.updateAuthToken(token)
    }

    /** 仅更新本地姓名，其余信息保持不变 */
    fun setName(ctx: Context, newName: String) {
        sp(ctx).edit().putString(KEY_NAME, newName).apply()
    }

    /** 仅更新本地邮箱，其余信息保持不变 */
    fun setEmail(ctx: Context, newEmail: String) {
        sp(ctx).edit().putString(KEY_EMAIL, newEmail).apply()
    }

    /**
     * 从服务端同步姓名并写入本地。
     * 依赖 ApiService.getStudentName(): Response<Result<StudentNameResponse>>
     * 成功返回姓名；失败返回 null（不抛异常，便于页面静默调用）
     */
    suspend fun syncNameFromServer(ctx: Context): String? = withContext(Dispatchers.IO) {
        try {
            val resp = ApiClient.api.getStudentName()
            if (!resp.isSuccessful) return@withContext null

            val body = resp.body() ?: return@withContext null
            val name = body.data?.name?.trim()

            if (body.code == 1 && !name.isNullOrEmpty()) {
                setName(ctx, name)   // 这句返回 Unit，不作为最后表达式
                name                 // 最后返回 String?
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }


    /** 清除本地会话 */
    fun clear(ctx: Context) {
        sp(ctx).edit().clear().apply()
        try { ApiClient.clearCookies() } catch (_: Exception) {}
        ApiClient.updateAuthToken(null)
    }

    /** 读取用户 ID */
    fun id(ctx: Context): Int? {
        val v = sp(ctx).getInt(KEY_ID, -1)
        return if (v == -1) null else v
    }

    /** 读取用户名 */
    fun name(ctx: Context): String? = sp(ctx).getString(KEY_NAME, null)

    /** 读取用户邮箱 */
    fun email(ctx: Context): String? = sp(ctx).getString(KEY_EMAIL, null)

    /** 读取 token */
    fun token(ctx: Context): String? = sp(ctx).getString(KEY_TOKEN, null)

    /**
     * 登录状态判断：
     * 必须 logged_in == true 且（有 token 或有 Cookie）
     */
    fun isLoggedIn(ctx: Context): Boolean {
        val s = sp(ctx)
        val flagged = s.getBoolean(KEY_LOGGED, false)
        val hasToken = !s.getString(KEY_TOKEN, null).isNullOrBlank()
        val hasCookies = ApiClient.hasCookies()
        return flagged && (hasToken || hasCookies)
    }
}
