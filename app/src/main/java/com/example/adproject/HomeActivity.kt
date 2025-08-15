package com.example.adproject

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class HomeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_home)

        // 未登录直接回登录页
        ensureLoggedInOrGoLogin()

        // Edge-to-edge（若根布局 id 不是 main，可删除这段）
        val mainId = resources.getIdentifier("main", "id", packageName)
        if (mainId != 0) {
            ViewCompat.setOnApplyWindowInsetsListener(findViewById(mainId)) { v, insets ->
                val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                insets
            }
        }

        // 底部导航
        val exerciseButton = findViewById<Button>(R.id.exerciseButton)
        val dashboardButton = findViewById<Button>(R.id.dashboardButton)
        val classButton = findViewById<Button>(R.id.classButton)
        val homeButton = findViewById<Button>(R.id.homeButton)

        // 其他按钮
        val profileButton = findViewById<Button>(R.id.profileButton)
        val settingsButton = findViewById<Button>(R.id.settingsButton)
        val logoutButton = findViewById<Button>(R.id.logoutButton)

        // 默认选中 Home
        setSelectedButton(homeButton)

        // 显示用户名（先用缓存，后台异步拉服务器）
        setUserNameIfPresent()

        // 底部导航点击
        exerciseButton.setOnClickListener {
            setSelectedButton(exerciseButton)
            startActivity(Intent(this, ExerciseActivity::class.java))
        }
        dashboardButton.setOnClickListener {
            setSelectedButton(dashboardButton)
            startActivity(Intent(this, DashboardActivity::class.java))
        }
        classButton.setOnClickListener {
            setSelectedButton(classButton)
            startActivity(Intent(this, ClassActivity::class.java))
        }
        homeButton.setOnClickListener { setSelectedButton(homeButton) }

        // 账户/设置
        profileButton.setOnClickListener {
            Toast.makeText(this, "Go to Account Management", Toast.LENGTH_SHORT).show()
        }
        settingsButton.setOnClickListener {
            Toast.makeText(this, "Go to Settings", Toast.LENGTH_SHORT).show()
        }

        // 退出登录
        logoutButton.setOnClickListener {
            UserSession.clear(this)
            goLoginClearBackStack()
        }
    }

    override fun onResume() {
        super.onResume()
        // 仅保留登录校验；已删除 /student/dashboard 的心跳请求
        ensureLoggedInOrGoLogin()

        // 如果以后想恢复会话心跳，请取消下面注释：
        /*
        lifecycleScope.launch {
            try {
                val r = com.example.adproject.api.ApiClient.api.dashboard()
                if (!r.isSuccessful) {
                    val e = r.errorBody()?.string().orEmpty()
                    if (r.code() == 401 || e.contains("未登录") || e.contains("会话已失效")) {
                        UserSession.clear(this@HomeActivity)
                        goLoginClearBackStack()
                    }
                }
            } catch (_: Exception) {}
        }
        */
    }

    /** 如果未登录，跳到登录页并清空返回栈 */
    private fun ensureLoggedInOrGoLogin() {
        if (!UserSession.isLoggedIn(this)) {
            goLoginClearBackStack()
        }
    }

    private fun goLoginClearBackStack() {
        startActivity(Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
    }

    private fun setSelectedButton(selectedButton: Button) {
        findViewById<Button>(R.id.exerciseButton)?.isSelected = false
        findViewById<Button>(R.id.dashboardButton)?.isSelected = false
        findViewById<Button>(R.id.classButton)?.isSelected = false
        findViewById<Button>(R.id.homeButton)?.isSelected = false
        selectedButton.isSelected = true
    }

    private fun setUserNameIfPresent() {
        val tv = findViewById<TextView>(R.id.userNameText) ?: return

        // 1) 先显示缓存
        UserSession.name(this)?.takeIf { it.isNotBlank() }?.let { tv.text = it }

        // 2) 后台同步（等后端就绪后会更新）
        lifecycleScope.launch {
            val latest = UserSession.syncNameFromServer(this@HomeActivity)
            if (!latest.isNullOrBlank()) {
                tv.text = latest
            }
        }
    }
}
