package com.example.adproject

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.adproject.api.ApiClient
import com.example.adproject.databinding.ActivityLoginBinding
import com.example.adproject.model.LoginRequest
import com.example.adproject.model.LoginResultVO
import kotlinx.coroutines.launch
import retrofit2.Response

class LoginActivity : AppCompatActivity() {

    private lateinit var vb: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 已登录则直达
        if (UserSession.isLoggedIn(this)) {
            goExercise()
            finish()
            return
        }

        vb = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(vb.root)

        vb.linkToRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        vb.btnLogin.setOnClickListener {
            val email = vb.inputEmail.editText?.text?.toString()?.trim().orEmpty()
            val pwd   = vb.inputPassword.editText?.text?.toString()?.trim().orEmpty()
            if (email.isEmpty() || pwd.isEmpty()) {
                Toast.makeText(this, "请输入邮箱和密码", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                val ok = loginAndHandle(email, pwd)
                if (ok) {
                    Toast.makeText(this@LoginActivity, "登录成功", Toast.LENGTH_SHORT).show()
                    goExercise()
                }
            }
        }
    }

    private suspend fun loginAndHandle(email: String, pwd: String): Boolean {
        return try {
            val resp: Response<LoginResultVO> = ApiClient.api.login(LoginRequest(email, pwd))

            if (!resp.isSuccessful) {
                Toast.makeText(
                    this,
                    "登录失败(${resp.code()}): ${resp.errorBody()?.string()?.take(200) ?: "无响应"}",
                    Toast.LENGTH_LONG
                ).show()
                return false
            }

            val data = resp.body()

            // 成功条件：status=ok 且 currentAuthority=student
            val success = data?.status.equals("ok", true)
                    && data?.currentAuthority.equals("student", true)

            if (!success) {
                val tip = data?.message ?: data?.msg ?: "账号或密码错误"
                Toast.makeText(this, tip, Toast.LENGTH_LONG).show()
                return false
            }

            // === 登录成功处理 ===
            val uid = (data?.userId ?: -1).toInt()
            val uname = data?.userName ?: email.substringBefore("@")
            UserSession.save(this, userId = uid, userName = uname, email = email)

            data?.token?.let { token ->
                if (token.isNotBlank()) ApiClient.updateAuthToken(token)
            }

            true
        } catch (e: Exception) {
            Toast.makeText(this, "网络异常：${e.message}", Toast.LENGTH_LONG).show()
            false
        }
    }


    private fun goExercise() {
        startActivity(Intent(this, ExerciseActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
    }
}
