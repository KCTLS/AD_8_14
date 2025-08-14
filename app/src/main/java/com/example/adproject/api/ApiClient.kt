// api/ApiClient.kt（只展示关键改动）
package com.example.adproject.api

import android.content.Context
import com.example.adproject.BuildConfig
import okhttp3.Interceptor
import okhttp3.OkHttpClient
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
}
