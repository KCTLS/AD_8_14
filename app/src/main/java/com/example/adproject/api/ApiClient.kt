// api/ApiClient.kt
package com.example.adproject.api

import android.content.Context
import com.example.adproject.BuildConfig
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    private const val DEFAULT_BASE_URL = "http://10.0.2.2:8080/student/"
    @Volatile private var baseUrl: String = BuildConfig.BASE_URL.ifBlank { DEFAULT_BASE_URL }

    // 用持久化 Cookie
    @Volatile private lateinit var cookieJar: PersistentCookieJar

    @Volatile private var authToken: String? = null
    fun updateAuthToken(token: String?) { authToken = token }

    private val logging = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.ENABLE_HTTP_LOG) HttpLoggingInterceptor.Level.BODY
        else HttpLoggingInterceptor.Level.NONE
    }

    private val headerInterceptor = Interceptor { chain ->
        val b = chain.request().newBuilder()
            .header("Accept", "application/json")
            .header("User-Agent", "ADProject/1.0 (Android)")
        authToken?.takeIf { it.isNotBlank() }?.let { b.header("Authorization", "Bearer $it") }
        chain.proceed(b.build())
    }

    @Volatile private lateinit var okHttp: OkHttpClient
    @Volatile private lateinit var retrofit: Retrofit

    @Volatile lateinit var api: ApiService
        private set

    /** 在 Application.onCreate() 里先调用一次 */
    fun init(context: Context, restoredToken: String?) {
        cookieJar = PersistentCookieJar(context.applicationContext)
        authToken = restoredToken
        rebuild() // 构建 OkHttp & Retrofit & api
    }

    private fun buildClient(): OkHttpClient =
        OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .addInterceptor(headerInterceptor)
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()

    private fun buildRetrofit(client: OkHttpClient, baseUrl: String): Retrofit =
        Retrofit.Builder()
            .baseUrl(if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/")
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()

    @Synchronized fun rebuild() {
        okHttp = buildClient()
        retrofit = buildRetrofit(okHttp, baseUrl)
        api = retrofit.create(ApiService::class.java)
    }

    fun clearCookies() { try { cookieJar.clear() } catch (_: Exception) {} }

    @Synchronized
    fun switchBaseUrl(newBaseUrl: String) {
        baseUrl = if (newBaseUrl.endsWith("/")) newBaseUrl else "$newBaseUrl/"
        rebuild()
    }

    // ---------- ↓↓↓ 新增：给 UserSession 用的小工具 ↓↓↓ ----------

    /** 暴露给 UserSession：当前是否还持有任何 Cookie */
    fun hasCookies(): Boolean = cookieJar.hasCookies()

    /**
     * 轻量会话自检：利用你已有的 /student/viewQuestion 接口来判断是否仍登录
     * 返回 true 表示仍然有效；false 表示应当回到登录页
     */
    suspend fun isSessionAlive(): Boolean {
        // 直接用 OkHttp 做一个最小 GET，避免依赖业务层
        val url = (if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/") +
                // baseUrl 已是 .../student/，这里用相对路径即可
                "viewQuestion?keyword=&questionName=&grade=&subject=&topic=&category=&page=1&questionIndex=-1"

        val req: Request = Request.Builder().url(url).get().build()

        return try {
            okHttp.newCall(req).execute().use { resp ->
                if (resp.code == 401) return false
                val body = resp.body?.string().orEmpty()
                // 你日志里未登录时返回：{"code":0,"msg":"未登录或会话已失效"}
                !body.contains("未登录或会话已失效") && !body.contains("\"code\":0")
            }
        } catch (_: Exception) {
            // 网络异常时，保守起见视为无效（避免误放行）
            false
        }
    }
}
