// App.kt
package com.example.adproject

import android.app.Application
import com.example.adproject.api.ApiClient

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // 从本地恢复 token（如果你用 JWT），并初始化持久化 Cookie
        ApiClient.init(this, UserSession.token(this))
    }
}
